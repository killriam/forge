package forge.game.log;

import com.google.common.collect.Multimap;
import com.google.common.eventbus.Subscribe;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.event.*;
import forge.game.log.model.*;
import forge.game.phase.PhaseHandler;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;
import forge.game.zone.ZoneView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live L1 event capture — subscribes to game events and builds a ReplayLog in memory,
 * then flushes JSON to disk on game end.
 *
 * Attach during game setup via:
 * {@code game.subscribeToEvents(new ReplayEventLogger(game, outputPath))}
 *
 * Phase 1 implementation (plan section 3.1):
 * - Tracks card IDs (c#, t#) via CardView.getId(), player IDs (P#) by index
 * - Captures initial_state with library in draw order at game start
 * - Visits: PHASE_CHANGE, MOVE, CAST, ACTIVATE, RESOLVE, RANDOM (shuffle),
 *           DECLARE_ATTACKERS, DECLARE_BLOCKERS, ACTIVE_PLAYER_CHANGE, GAME_END
 * - Builds per-turn TurnSummary and final GameSummary at game end
 */
public class ReplayEventLogger extends IGameEventVisitor.Base<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ReplayEventLogger.class);
    private static final String FORMAT_VERSION = "1.8.0";

    // -------------------------------------------------------------------
    //  Fields
    // -------------------------------------------------------------------
    private final Game game;
    private final String outputPath;
    private final ReplayLog replayLog;

    /** Monotonically increasing event index. */
    private final AtomicInteger eventCounter = new AtomicInteger(0);
    /** Wall-clock start time in millis. */
    private final long startTimeMillis;

    /**
     * CardView.getId() → stable "c{n}" or "t{n}" ID string for this session.
     * Populated lazily on first encounter of any card in an event.
     */
    private final Map<Integer, String> cardIdMap = new HashMap<>();
    private int nextCardSeq = 1;
    private int nextTokenSeq = 1;

    /**
     * PlayerView.getId() → "P1", "P2", …
     * Populated at construction time from game.getPlayers().
     */
    private final Map<Integer, String> playerViewIdMap = new LinkedHashMap<>();

    // Per-turn accumulation
    private int currentTurn = 0;
    private TurnSummary pendingTurnSummary = null;
    private final Map<String, TurnSummary.PlayerTurnStats> pendingPlayerStats = new LinkedHashMap<>();

    // Game-level aggregated stats per player
    // [0]=draws, [1]=spells, [2]=lands, [3]=unused, [4]=unused, [5]=creatures
    private final Map<String, int[]> gameStats = new LinkedHashMap<>();

    /**
     * Cards sacrificed since the last CAST/ACTIVATE event, not yet attached to one. Additional
     * costs like "sacrifice a creature" (e.g. Metamorphosis) are paid before the spell/ability is
     * finalized onto the stack, so GameEventCardSacrificed fires first - buffering it here and
     * attaching + clearing on the next CAST/ACTIVATE is a reasonable approximation of "this is
     * what was sacrificed to pay for it" for the common case of one action at a time.
     */
    private final List<String> pendingSacrificedIds = new ArrayList<>();

    // -------------------------------------------------------------------
    //  Constructor
    // -------------------------------------------------------------------

    public ReplayEventLogger(Game game, String outputPath) {
        this.game = game;
        this.outputPath = outputPath;
        this.startTimeMillis = System.currentTimeMillis();

        replayLog = new ReplayLog();
        replayLog.setVersion(FORMAT_VERSION);
        replayLog.setSpecVersion(FORMAT_VERSION);

        // Build player ID maps from actual Player objects (stable after game creation)
        List<Player> players = game.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            String pid = "P" + (i + 1);
            playerViewIdMap.put(p.getView().getId(), pid);
        }

        // Fill meta
        String gameId = UUID.randomUUID().toString();
        String now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        ReplayMeta meta = replayLog.getMeta();
        meta.setGameId(gameId);
        meta.setTimestamp(now);
        meta.setGameType(game.getRules().getGameType().name());

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            String pid = "P" + (i + 1);
            ReplayMeta.PlayerMeta pm = new ReplayMeta.PlayerMeta();
            pm.setName(p.getName());
            pm.setAi(p.getController().isAI());
            pm.setStartingLife(p.getStartingLife());
            pm.setPlayerType(pm.isAi() ? "AI" : "Human");
            meta.getPlayers().put(pid, pm);
            gameStats.put(pid, new int[6]);
        }
    }

    // -------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------

    /** Return (lazily assign) a stable "c{n}" / "t{n}" ID for a CardView. */
    private String cardId(CardView card) {
        if (card == null) return "?";
        return cardIdMap.computeIfAbsent(card.getId(), id ->
                card.isToken() ? "t" + nextTokenSeq++ : "c" + nextCardSeq++);
    }

    /** Return the "P{n}" ID for a PlayerView, or "SYS" if not found. */
    private String playerStr(PlayerView player) {
        if (player == null) return "SYS";
        String pid = playerViewIdMap.get(player.getId());
        return pid != null ? pid : "SYS";
    }

    /** Build a zone-label string such as "P1:library" or "shared:stack". */
    private String zoneLabel(ZoneView zone) {
        if (zone == null) return "unknown";
        ZoneType zt = zone.zoneType();
        PlayerView owner = zone.player();
        if (owner != null) {
            return playerStr(owner) + ":" + zt.name().toLowerCase();
        }
        return "shared:" + zt.name().toLowerCase();
    }

    /** Current time-marker string derived from PhaseHandler, e.g. "T3.MAIN1". */
    private String timeMarker() {
        PhaseHandler ph = game.getPhaseHandler();
        if (ph == null) return "T0";
        String phase = ph.getPhase() != null ? ph.getPhase().nameForUi : "?";
        return "T" + ph.getTurn() + "." + phase;
    }

    private L1Event makeEvent(String actor, String type) {
        return new L1Event(eventCounter.getAndIncrement(), timeMarker(), actor, type);
    }

    /** Ensure a Card (real object) appears in card_index. */
    private void registerCardReal(Card card) {
        if (card == null) return;
        String cid = cardIdMap.computeIfAbsent(card.getView().getId(), id ->
                card.isToken() ? "t" + nextTokenSeq++ : "c" + nextCardSeq++);
        if (!replayLog.getCardIndex().containsKey(cid)) {
            CardDefinition def = new CardDefinition();
            def.setName(card.getName());
            def.setCost(card.getManaCost() != null ? card.getManaCost().toString() : "");
            def.setType(card.getType() != null ? card.getType().toString() : "");
            if (card.isCreature()) {
                def.setPower(String.valueOf(card.getBasePower()));
                def.setToughness(String.valueOf(card.getBaseToughness()));
            }
            replayLog.getCardIndex().put(cid, def);
        }
    }

    /** Ensure a CardView appears in card_index (lighter fallback for view-only context). */
    private void registerCardView(CardView card) {
        if (card == null) return;
        String cid = cardId(card);
        if (!replayLog.getCardIndex().containsKey(cid)) {
            CardDefinition def = new CardDefinition();
            def.setName(card.getName());
            replayLog.getCardIndex().put(cid, def);
        }
    }

    /** Capture initial_state from all player zones immediately after game starts. */
    private void captureInitialState() {
        GameState gs = replayLog.getInitialState();
        gs.setTurn(0);
        gs.setPhase("UNTAP");

        List<Player> players = game.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            String pid = "P" + (i + 1);

            GameState.PlayerState ps = new GameState.PlayerState();
            ps.setLife(p.getLife());
            ps.setMaxHandSize(p.getMaxHandSize());
            gs.getPlayers().put(pid, ps);

            captureZoneReal(p, pid, ZoneType.Library);
            captureZoneReal(p, pid, ZoneType.Hand);
            captureZoneReal(p, pid, ZoneType.Graveyard);
            captureZoneReal(p, pid, ZoneType.Exile);
            captureZoneReal(p, pid, ZoneType.Battlefield);
        }
    }

    private void captureZoneReal(Player player, String playerId, ZoneType zt) {
        forge.game.zone.Zone zone = player.getZone(zt);
        if (zone == null) return;
        int position = 0;
        for (Card card : zone.getCards().threadSafeIterable()) {
            registerCardReal(card);
            String cid = cardIdMap.getOrDefault(card.getView().getId(), "c?");
            GameState.ObjectState obj = new GameState.ObjectState();
            obj.setCardRef(card.getName());
            obj.setController(playerStr(card.getController().getView()));
            obj.setOwner(playerStr(card.getOwner().getView()));
            obj.setZone(playerId + ":" + zt.name().toLowerCase());
            obj.setTapped(card.isTapped());
            Map<String, Object> notes = new HashMap<>();
            notes.put("position", position);
            notes.put("card_id", cid);
            obj.setNotes(notes);
            replayLog.getInitialState().getObjects().put(cid + "_" + zt.name().toLowerCase() + "_" + position, obj);
            position++;
        }
    }

    /** Flush the pending TurnSummary to the log. */
    private void finalizePendingTurn() {
        if (pendingTurnSummary != null) {
            pendingPlayerStats.forEach((pid, stats) -> pendingTurnSummary.getPlayers().put(pid, stats));
            replayLog.addTurnSummary(pendingTurnSummary);
        }
        pendingTurnSummary = null;
        pendingPlayerStats.clear();
    }

    /** Get or create PlayerTurnStats accumulator for the current pending turn. */
    private TurnSummary.PlayerTurnStats currentPlayerStats(String pid) {
        return pendingPlayerStats.computeIfAbsent(pid, k -> new TurnSummary.PlayerTurnStats());
    }

    // -------------------------------------------------------------------
    //  Event Visitors
    // -------------------------------------------------------------------

    @Override
    public Void visit(GameEventGameStarted ev) {
        captureInitialState();
        String firstPid = playerStr(ev.firstTurn());
        replayLog.getGameStart().setStartingPlayer(firstPid);

        L1Event l1 = makeEvent("SYS", "GAME_START");
        l1.addData("starting_player", firstPid);
        l1.addData("game_type", ev.gameType() != null ? ev.gameType().name() : "unknown");
        replayLog.addL1Event(l1);
        return null;
    }

    @Override
    public Void visit(GameEventTurnBegan ev) {
        finalizePendingTurn();
        currentTurn = ev.turnNumber();
        String activePlayer = playerStr(ev.turnOwner());
        pendingTurnSummary = new TurnSummary(currentTurn, activePlayer);

        // Snapshot live stats into each player's TurnStats
        for (Player p : game.getPlayers()) {
            String pid = playerStr(p.getView());
            TurnSummary.PlayerTurnStats stats = currentPlayerStats(pid);
            stats.setLife(p.getLife());
            stats.setCardsInHand(p.getCardsIn(ZoneType.Hand).size());
            stats.setLandCount((int) p.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(Card::isLand).count());
            stats.setPermanentsOnBattlefield(p.getCardsIn(ZoneType.Battlefield).size());
            stats.setCreaturesOnBattlefield(p.getCreaturesInPlay().size());
        }

        L1Event l1 = makeEvent(activePlayer, "ACTIVE_PLAYER_CHANGE");
        l1.addData("turn", currentTurn);
        l1.addData("player", activePlayer);
        replayLog.addL1Event(l1);
        return null;
    }

    @Override
    public Void visit(GameEventTurnPhase ev) {
        // Bounds the "attach pending sacrifices to the next cast" window to within the same
        // phase, so a sacrifice that was never followed by a cast (e.g. a sac outlet used for
        // its own sake) doesn't get misattributed to some unrelated cast several phases later.
        pendingSacrificedIds.clear();

        String actor = playerStr(ev.playerTurn());
        L1Event l1 = makeEvent(actor, "PHASE_CHANGE");
        l1.addData("turn", currentTurn);
        l1.addData("phase", ev.phase() != null ? ev.phase().nameForUi : "?");
        l1.addData("player", actor);
        replayLog.addL1Event(l1);
        return null;
    }

    @Override
    public Void visit(GameEventCardChangeZone ev) {
        CardView card = ev.card();
        if (card == null) return null;
        registerCardView(card);

        String from = zoneLabel(ev.from());
        String to = zoneLabel(ev.to());
        String owner = card.getOwner() != null ? playerStr(card.getOwner()) : "SYS";

        L1Event l1 = makeEvent(owner, "MOVE");
        l1.addData("card", cardId(card));
        l1.addData("card_name", card.getName());
        l1.addData("from", from);
        l1.addData("to", to);
        
        // FIX P2: Add controller to MOVE events
        if (card.getController() != null) {
            l1.addData("controller", playerStr(card.getController()));
        }
        
        // FIX P3: Add owner to MOVE events  
        if (card.getOwner() != null) {
            l1.addData("owner", playerStr(card.getOwner()));
        }
        
        replayLog.addL1Event(l1);

        // Accumulate draw / land stats
        ZoneView toZone = ev.to();
        if (toZone != null) {
            ZoneType toType = toZone.zoneType();
            PlayerView toOwner = toZone.player();
            if (toType == ZoneType.Hand && toOwner != null) {
                String pid = playerStr(toOwner);
                int[] gs = gameStats.get(pid);
                if (gs != null) gs[0]++;
                currentPlayerStats(pid).setCardsDrawn(currentPlayerStats(pid).getCardsDrawn() + 1);
            }
            if (toType == ZoneType.Battlefield && toOwner != null
                    && card.getCurrentState() != null && card.getCurrentState().isLand()) {
                String pid = playerStr(toOwner);
                currentPlayerStats(pid).setLandsPlayed(currentPlayerStats(pid).getLandsPlayed() + 1);
                int[] gs = gameStats.get(pid);
                if (gs != null) gs[2]++;
            }
        }
        return null;
    }

    @Override
    public Void visit(GameEventCardSacrificed ev) {
        CardView card = ev.card();
        if (card == null) return null;
        registerCardView(card);
        pendingSacrificedIds.add(cardId(card));
        return null;
    }

    @Override
    public Void visit(GameEventSpellAbilityCast ev) {
        StackItemView si = ev.si();
        String actor = si != null && si.getActivatingPlayer() != null
                ? playerStr(si.getActivatingPlayer()) : "SYS";
        CardView hostCard = ev.sa() != null ? ev.sa().getHostCard() : null;
        if (hostCard != null) registerCardView(hostCard);

        boolean isSpell = ev.sa() != null && ev.sa().isSpell();
        boolean isAbility = si != null && si.isAbility();
        String eventType = isSpell ? "CAST" : "ACTIVATE";

        L1Event l1 = makeEvent(actor, eventType);
        if (hostCard != null) {
            l1.addData("card", cardId(hostCard));
            l1.addData("card_name", hostCard.getName());
        }
        l1.addData("stack_index", ev.stackIndex());
        if (ev.targetDescription() != null) {
            l1.addData("target_desc", ev.targetDescription());
        }

        // Targets from StackItemView
        if (si != null) {
            List<String> targets = new ArrayList<>();
            if (si.getTargetCards() != null) {
                for (CardView tc : si.getTargetCards()) {
                    targets.add(cardId(tc));
                }
            }
            if (si.getTargetPlayers() != null) {
                for (PlayerView tp : si.getTargetPlayers()) {
                    targets.add(playerStr(tp));
                }
            }
            if (!targets.isEmpty()) {
                l1.addData("targets", targets);
            }
        }

        // Cost/X/choices, per mtg-replay-notation's CAST event schema (spec/MTG-REPLAY-NOTATION.md
        // §CAST Event: cost.mana/additional/alternative, x, choices). ev.realSa() is the actual
        // SpellAbility (not just its View), so this reads real paid-cost data rather than
        // guessing from the View, which doesn't carry it.
        forge.game.spellability.SpellAbility realSa = ev.realSa();
        if (realSa != null) {
            Map<String, Object> cost = new LinkedHashMap<>();
            cost.put("mana", ReplayNotationExporter.getManaPaid(realSa));
            cost.put("additional", ReplayNotationExporter.getAdditionalCosts(realSa));
            cost.put("alternative", ReplayNotationExporter.getAlternativeCostType(realSa));
            l1.addData("cost", cost);
            if (realSa.costHasManaX()) {
                Integer xPaid = realSa.getXManaCostPaid();
                if (xPaid != null) {
                    l1.addData("x", xPaid);
                }
            }
        }
        if (!pendingSacrificedIds.isEmpty()) {
            Map<String, Object> choices = new LinkedHashMap<>();
            choices.put("sacrifice", new ArrayList<>(pendingSacrificedIds));
            l1.addData("choices", choices);
            pendingSacrificedIds.clear();
        }
        replayLog.addL1Event(l1);

        // Stats
        if (isSpell) {
            int[] gs = gameStats.get(actor);
            if (gs != null) gs[1]++;
            currentPlayerStats(actor).setSpellsCast(currentPlayerStats(actor).getSpellsCast() + 1);
        } else if (isAbility) {
            currentPlayerStats(actor).setAbilitiesActivated(
                    currentPlayerStats(actor).getAbilitiesActivated() + 1);
        }
        return null;
    }

    /**
     * Dedicated PLAY_LAND event, separate from the generic MOVE that {@link
     * #visit(GameEventCardChangeZone)} also emits for the same physical zone change - land plays
     * need their own explicit event type because {@link ReplayPlaySequenceParser} (the "-r"
     * full-game-replay consumer) and the scenario {@code events[]} format both only recognize
     * {@code type in {CAST, ACTIVATE, PLAY_LAND}}, never inferring PLAY_LAND from a generic MOVE.
     */
    @Override
    public Void visit(GameEventLandPlayed ev) {
        CardView land = ev.land();
        if (land == null) return null;
        registerCardView(land);
        String actor = playerStr(ev.player());

        L1Event l1 = makeEvent(actor, "PLAY_LAND");
        l1.addData("card", cardId(land));
        l1.addData("card_name", land.getName());
        replayLog.addL1Event(l1);
        return null;
    }

    @Override
    public Void visit(GameEventSpellResolved ev) {
        if (ev.spell() == null) return null;
        CardView hostCard = ev.spell().getHostCard();
        String actor = "SYS";
        if (hostCard != null && hostCard.getController() != null) {
            actor = playerStr(hostCard.getController());
        }

        L1Event l1 = makeEvent(actor, "RESOLVE");
        if (hostCard != null) {
            l1.addData("card", cardId(hostCard));
            l1.addData("card_name", hostCard.getName());
        }
        if (ev.stackDescription() != null) {
            l1.addData("description", ev.stackDescription());
        }
        if (ev.hasFizzled()) {
            l1.addData("fizzled", true);
        }
        replayLog.addL1Event(l1);
        return null;
    }

    @Override
    public Void visit(GameEventShuffle ev) {
        String pid = playerStr(ev.player());
        L1Event l1 = makeEvent(pid, "RANDOM");
        l1.addData("sub_type", "shuffle");
        l1.addData("player", pid);
        replayLog.addL1Event(l1);
        return null;
    }

    @Override
    public Void visit(GameEventAttackersDeclared ev) {
        String actor = playerStr(ev.player());
        L1Event l1 = makeEvent(actor, "DECLARE_ATTACKERS");
        List<String> attackerIds = new ArrayList<>();
        for (CardView c : ev.attackersMap().values()) {
            registerCardView(c);
            attackerIds.add(cardId(c));
        }
        l1.addData("attackers", attackerIds);
        replayLog.addL1Event(l1);
        return null;
    }

    @Override
    public Void visit(GameEventBlockersDeclared ev) {
        String actor = playerStr(ev.defendingPlayer());
        L1Event l1 = makeEvent(actor, "DECLARE_BLOCKERS");
        List<String> blockerIds = new ArrayList<>();
        for (Multimap<CardView, CardView> kv : ev.blockers().values()) {
            for (CardView b : kv.values()) {
                registerCardView(b);
                blockerIds.add(cardId(b));
            }
        }
        l1.addData("blockers", blockerIds);
        replayLog.addL1Event(l1);
        return null;
    }

    @Override
    public Void visit(GameEventGameOutcome ev) {
        finalizePendingTurn();

        int totalTurns = game.getPhaseHandler() != null ? game.getPhaseHandler().getTurn() : currentTurn;
        long durationSec = (System.currentTimeMillis() - startTimeMillis) / 1000L;

        ReplayMeta meta = replayLog.getMeta();
        meta.setTurns(totalTurns);
        meta.setDurationSeconds((int) durationSec);
        if (ev.winningPlayerName() != null) {
            for (Player p : game.getPlayers()) {
                if (ev.winningPlayerName().equals(p.getName())) {
                    meta.setWinner(playerStr(p.getView()));
                    break;
                }
            }
        }

        // Build GameSummary
        GameSummary summary = new GameSummary();
        summary.setTotalTurns(totalTurns);
        summary.setDurationSeconds((int) durationSec);
        summary.setWinner(meta.getWinner());
        if (ev.outcomeStrings() != null && !ev.outcomeStrings().isEmpty()) {
            summary.setWinCondition(ev.outcomeStrings().get(0));
        }
        for (Player p : game.getPlayers()) {
            String pid = playerStr(p.getView());
            int[] gs = gameStats.getOrDefault(pid, new int[6]);
            GameSummary.PlayerGameStats pgs = new GameSummary.PlayerGameStats();
            pgs.setTotalCardsDrawn(gs[0]);
            pgs.setTotalSpellsCast(gs[1]);
            pgs.setTotalLandsPlayed(gs[2]);
            pgs.setTotalCreaturesPlayed(gs[5]);
            pgs.setStartingLife(p.getStartingLife());
            pgs.setEndingLife(p.getLife());
            pgs.setLifeDelta(p.getLife() - p.getStartingLife());
            if (totalTurns > 0) {
                pgs.setCardDrawRate((double) gs[0] / totalTurns);
                pgs.setSpellVelocity((double) gs[1] / totalTurns);
            }
            summary.getPlayers().put(pid, pgs);
        }
        replayLog.setGameSummary(summary);

        L1Event l1 = makeEvent("SYS", "GAME_END");
        l1.addData("turns", totalTurns);
        l1.addData("winner", meta.getWinner());
        l1.addData("duration_seconds", durationSec);
        replayLog.addL1Event(l1);

        flushToDisk();
        return null;
    }

    // -------------------------------------------------------------------
    //  Disk flush
    // -------------------------------------------------------------------

    private void flushToDisk() {
        try {
            File outFile = new File(outputPath);
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            String json = ReplayJsonSerializer.toJson(replayLog);
            try (FileWriter fw = new FileWriter(outFile)) {
                fw.write(json);
            }
            LOG.info("Replay log saved: {}", outputPath);
        } catch (IOException ex) {
            LOG.error("Failed to write replay log to {}: {}", outputPath, ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------
    //  Guava EventBus dispatch entry point
    // -------------------------------------------------------------------

    @Subscribe
    public void receiveGameEvent(final GameEvent ev) {
        ev.visit(this);
    }
}


