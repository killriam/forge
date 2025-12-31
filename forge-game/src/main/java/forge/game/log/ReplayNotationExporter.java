package forge.game.log;

import forge.game.Game;
import forge.game.GameOutcome;
import forge.game.card.Card;
import forge.game.log.model.*;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exports game logs in MTG Replay Notation format (JSON).
 * Implements the specification from MTG_REPLAY_NOTATION.md.
 */
public class ReplayNotationExporter {

    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final Game game;
    private final ReplayLog replayLog;
    private int eventIndex = 0;
    private final Map<Card, String> cardIdMap = new HashMap<>();
    private int cardIdCounter = 0;
    private int stackIdCounter = 0;
    private long gameStartTime = System.currentTimeMillis();

    // Phase-Event Buffering: Skip empty phases
    private Map<String, Object> pendingPhaseEvent = null;
    private String currentPhase = null;
    private long phaseStartTime = 0;
    private static final Set<String> ALWAYS_EMPTY_PHASES = new HashSet<>(Arrays.asList(
        "PREGAME", "UNTAP", "DRAW", "CLEANUP"
    ));

    public ReplayNotationExporter(Game game) {
        this.game = game;
        this.replayLog = new ReplayLog();
        initializeReplayLog();
    }

    /**
     * Initialize the replay log with metadata and initial state.
     */
    private void initializeReplayLog() {
        // Set metadata
        ReplayMeta meta = replayLog.getMeta();
        meta.setGameId("game-" + UUID.randomUUID().toString());
        meta.setTimestamp(ISO_FORMAT.format(new Date()));

        if (game.getRules() != null && game.getRules().getGameType() != null) {
            meta.setGameType(game.getRules().getGameType().toString());
        } else {
            meta.setGameType("Unknown");
        }

        // Add player metadata
        for (Player player : game.getPlayers()) {
            ReplayMeta.PlayerMeta playerMeta = new ReplayMeta.PlayerMeta();
            playerMeta.setName(player.getName());
            meta.getPlayers().put(getPlayerId(player), playerMeta);
        }

        // Set seed (use game's random seed if available)
        replayLog.setSeed(System.currentTimeMillis());

        // Initialize initial state
        captureInitialState();
    }

    /**
     * Capture the initial game state.
     */
    private void captureInitialState() {
        GameState state = new GameState();
        state.setTurn(0);
        state.setPhase("PREGAME");
        state.setStep("PREGAME");

        // Initialize player states
        for (Player player : game.getPlayers()) {
            GameState.PlayerState playerState = new GameState.PlayerState();
            playerState.setLife(player.getLife());
            playerState.setMaxHandSize(7);
            state.getPlayers().put(getPlayerId(player), playerState);
        }

        // Initialize zones
        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);
            Map<String, Object> libraryInfo = new HashMap<>();
            libraryInfo.put("count", player.getZone(ZoneType.Library).size());
            state.getZones().put(playerId + ":library", libraryInfo);

            state.getZones().put(playerId + ":hand", new ArrayList<>());
            state.getZones().put(playerId + ":graveyard", new ArrayList<>());
        }

        state.getZones().put("battlefield", new ArrayList<>());
        state.getZones().put("stack", new ArrayList<>());
        state.getZones().put("exile", new ArrayList<>());

        replayLog.setInitialState(state);
    }

    /**
     * Get or create a stable ID for a player.
     */
    private String getPlayerId(Player player) {
        int index = game.getPlayers().indexOf(player);
        return "P" + (index + 1);
    }

    /**
     * Set the game outcome information (winner, turns, duration).
     * Should be called when the game ends.
     */
    public void setGameOutcome(GameOutcome outcome) {
        if (outcome == null) {
            return;
        }

        ReplayMeta meta = replayLog.getMeta();

        // Set winner
        RegisteredPlayer winningPlayer = outcome.getWinningPlayer();
        if (winningPlayer != null) {
            // Find the player index
            for (int i = 0; i < game.getPlayers().size(); i++) {
                Player p = game.getPlayers().get(i);
                if (p.getRegisteredPlayer() == winningPlayer) {
                    meta.setWinner("P" + (i + 1));
                    break;
                }
            }
        }

        // Set turns
        meta.setTurns(outcome.getLastTurnNumber());

        // Set duration
        long durationMs = System.currentTimeMillis() - gameStartTime;
        meta.setDurationSeconds((int) (durationMs / 1000));
    }

    /**
     * Get or create a stable ID for a card.
     * Handles face-down cards by using the actual card name for the index.
     */
    private String getCardId(Card card) {
        return cardIdMap.computeIfAbsent(card, c -> {
            cardIdCounter++;
            String cardId = "c" + cardIdCounter;

            // Get the actual card name (even for face-down cards)
            String actualName = getActualCardName(c);

            // Add to card index with actual name as key
            // Use unique key to avoid collisions (cardId + name)
            String indexKey = actualName.isEmpty() ? cardId : actualName;

            // Only add if not already present with this name
            if (!replayLog.getCardIndex().containsKey(indexKey)) {
                CardDefinition def = new CardDefinition();
                def.setName(actualName);

                // Get mana cost from actual card state if available
                if (c.getManaCost() != null && !c.getManaCost().isNoCost()) {
                    def.setCost(c.getManaCost().toString());
                } else if (c.getCurrentState() != null && c.getCurrentState().getManaCost() != null) {
                    def.setCost(c.getCurrentState().getManaCost().toString());
                } else {
                    def.setCost("no cost");
                }

                // Get type
                def.setType(c.getType() != null ? c.getType().toString() : "Unknown");

                replayLog.getCardIndex().put(indexKey, def);
            }

            return cardId;
        });
    }

    /**
     * Get the actual card name, even for face-down cards.
     */
    private String getActualCardName(Card card) {
        // First check if card has a name
        String name = card.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // For face-down cards, try to get the original paper card name
        if (card.getPaperCard() != null) {
            return card.getPaperCard().getName();
        }

        // Try to get from current state
        if (card.getCurrentState() != null && card.getCurrentState().getName() != null) {
            return card.getCurrentState().getName();
        }

        // For tokens or completely unknown cards, generate a descriptive name
        if (card.isToken()) {
            return "Token (" + (card.getType() != null ? card.getType().getCreatureTypes().toString() : "Unknown") + ")";
        }

        // Last resort: use the card ID as a placeholder
        return "Unknown Card #" + card.getId();
    }

    /**
     * Generate a new stack ID.
     */
    private String generateStackId() {
        stackIdCounter++;
        return "s" + stackIdCounter;
    }

    /**
     * Add a Level 1 event to the log.
     */
    public void addEvent(String timeMarker, String actor, String eventType, Map<String, Object> data) {
        L1Event event = new L1Event(eventIndex++, timeMarker, actor, eventType);
        event.setData(data);
        replayLog.addL1Event(event);
    }

    /**
     * Log a zone change event.
     */
    public void logZoneChange(Card card, ZoneType from, ZoneType to, String timeMarker, Player owner) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("obj", getCardId(card));
        data.put("from", formatZone(from, owner));
        data.put("to", formatZone(to, owner));
        data.put("pos", "top");
        data.put("visibility", isPublicZone(to) ? "public" : "private");

        addEvent(timeMarker, "SYS", "MOVE", data);
    }

    /**
     * Log a spell being cast with full details including play mode and total cost.
     */
    public void logCast(Card card, Player caster, String timeMarker, forge.game.spellability.SpellAbility sa) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("card", getCardId(card));

        // Determine play mode
        String playMode = determinePlayMode(sa);
        data.put("play_mode", playMode);

        Map<String, Object> cost = new HashMap<>();

        // Get the mana cost that was actually paid
        String manaPaid = getManaPaid(sa);
        cost.put("mana", Arrays.asList(manaPaid));

        // Calculate total CMC paid
        int totalCostPaid = calculateTotalCostPaid(sa);
        cost.put("total_mana_value", totalCostPaid);

        // List additional costs
        List<String> additionalCosts = getAdditionalCosts(sa);
        cost.put("additional", additionalCosts);

        // Alternative cost type if used
        String alternativeCostType = getAlternativeCostType(sa);
        cost.put("alternative", alternativeCostType);

        data.put("cost", cost);

        data.put("modes", new ArrayList<>());
        data.put("x", sa != null ? sa.getXManaCostPaid() : null);
        data.put("targets", new ArrayList<>());
        data.put("choices", new HashMap<>());

        addEvent(timeMarker, getPlayerId(caster), "CAST", data);
    }

    /**
     * Legacy method for backwards compatibility.
     */
    public void logCast(Card card, Player caster, String timeMarker) {
        logCast(card, caster, timeMarker, null);
    }

    /**
     * Determine the play mode of a spell/ability.
     */
    private String determinePlayMode(forge.game.spellability.SpellAbility sa) {
        if (sa == null) {
            return "normal";
        }

        // Check for face-down casting
        if (sa.isCastFaceDown()) {
            if (sa.hasParam("MorphCost")) {
                return "morph";
            }
            if (sa.hasParam("DisguiseCost")) {
                return "disguise";
            }
            return "face_down";
        }

        // Check alternative costs
        forge.game.spellability.AlternativeCost altCost = sa.getAlternativeCost();
        if (altCost != null) {
            return switch (altCost) {
                case Flashback -> "flashback";
                case Foretold -> "foretold";
                case Escape -> "escape";
                case Madness -> "madness";
                case Evoke -> "evoke";
                case Dash -> "dash";
                case Surge -> "surge";
                case Emerge -> "emerge";
                case Disturb -> "disturb";
                case Spectacle -> "spectacle";
                case Blitz -> "blitz";
                case Warp -> "warp";
                case Harmonize -> "harmonize";
                case Overload -> "overload";
                case Bestow -> "bestow";
                case Awaken -> "awaken";
                case Mutate -> "mutate";
                case Prowl -> "prowl";
                case Plotted -> "plotted";
                default -> "alternative:" + altCost.name().toLowerCase();
            };
        }

        // Check for special casting methods
        if (sa.isAftermath()) {
            return "aftermath";
        }
        if (sa.isKicked()) {
            return "kicked";
        }
        if (sa.isBuyback()) {
            return "buyback";
        }

        return "normal";
    }

    /**
     * Get the mana that was actually paid for this spell.
     */
    private String getManaPaid(forge.game.spellability.SpellAbility sa) {
        if (sa == null) {
            return "0";
        }

        List<forge.game.mana.Mana> payingMana = sa.getPayingMana();
        if (payingMana == null || payingMana.isEmpty()) {
            // Fall back to the card's mana cost
            Card card = sa.getHostCard();
            if (card != null && card.getManaCost() != null) {
                return card.getManaCost().toString();
            }
            return "0";
        }

        // Build the mana string from what was actually paid
        StringBuilder manaStr = new StringBuilder();
        Map<String, Integer> colorCounts = new LinkedHashMap<>();
        int colorless = 0;

        for (forge.game.mana.Mana m : payingMana) {
            if (m.isColorless()) {
                colorless++;
            } else {
                String color = manaColorToString(m.getColor());
                colorCounts.merge(color, 1, Integer::sum);
            }
        }

        // Format: {C}{W}{W}{U} etc.
        if (colorless > 0) {
            manaStr.append("{").append(colorless).append("}");
        }
        for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                manaStr.append("{").append(entry.getKey()).append("}");
            }
        }

        return manaStr.length() > 0 ? manaStr.toString() : "0";
    }

    /**
     * Convert mana color byte to string representation.
     */
    private String manaColorToString(byte color) {
        if ((color & forge.card.mana.ManaAtom.WHITE) != 0) return "W";
        if ((color & forge.card.mana.ManaAtom.BLUE) != 0) return "U";
        if ((color & forge.card.mana.ManaAtom.BLACK) != 0) return "B";
        if ((color & forge.card.mana.ManaAtom.RED) != 0) return "R";
        if ((color & forge.card.mana.ManaAtom.GREEN) != 0) return "G";
        if ((color & forge.card.mana.ManaAtom.COLORLESS) != 0) return "C";
        return "1"; // Generic/Unknown
    }

    /**
     * Calculate the total mana value paid for this spell.
     */
    private int calculateTotalCostPaid(forge.game.spellability.SpellAbility sa) {
        if (sa == null) {
            return 0;
        }

        List<forge.game.mana.Mana> payingMana = sa.getPayingMana();
        if (payingMana != null) {
            return payingMana.size();
        }

        // Fall back to card's CMC
        Card card = sa.getHostCard();
        if (card != null) {
            return card.getCMC();
        }
        return 0;
    }

    /**
     * Get additional costs that were paid.
     */
    private List<String> getAdditionalCosts(forge.game.spellability.SpellAbility sa) {
        List<String> additionalCosts = new ArrayList<>();

        if (sa == null) {
            return additionalCosts;
        }

        // Check for common additional costs
        if (sa.isKicked()) {
            additionalCosts.add("kicker");
        }
        if (sa.isBuyback()) {
            additionalCosts.add("buyback");
        }
        if (sa.isEntwine()) {
            additionalCosts.add("entwine");
        }
        if (sa.isOptionalCostPaid(forge.game.spellability.OptionalCost.Retrace)) {
            additionalCosts.add("retrace");
        }
        if (sa.isOptionalCostPaid(forge.game.spellability.OptionalCost.Jumpstart)) {
            additionalCosts.add("jump-start");
        }
        if (sa.isOptionalCostPaid(forge.game.spellability.OptionalCost.Bargain)) {
            additionalCosts.add("bargain");
        }
        if (sa.costHasManaX()) {
            Integer xPaid = sa.getXManaCostPaid();
            if (xPaid != null && xPaid > 0) {
                additionalCosts.add("X=" + xPaid);
            }
        }

        return additionalCosts;
    }

    /**
     * Get the alternative cost type if one was used.
     */
    private String getAlternativeCostType(forge.game.spellability.SpellAbility sa) {
        if (sa == null) {
            return null;
        }

        forge.game.spellability.AlternativeCost altCost = sa.getAlternativeCost();
        if (altCost != null) {
            return altCost.name().toLowerCase();
        }
        return null;
    }

    /**
     * Log a spell being put on the stack.
     */
    public void logPutOnStack(Card card, Player controller, String timeMarker) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("stack", generateStackId());
        data.put("kind", "SPELL");
        data.put("source", getCardId(card));
        data.put("controller", getPlayerId(controller));
        data.put("card", getCardId(card));
        data.put("targets", new ArrayList<>());
        data.put("choices", new HashMap<>());

        addEvent(timeMarker, "SYS", "PUT_ON_STACK", data);
    }

    /**
     * Log damage dealt.
     */
    public void logDamage(Card source, Object target, int amount, String damageType, String timeMarker) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("source", source != null ? getCardId(source) : "unknown");

        if (target instanceof Card) {
            data.put("target", getCardId((Card) target));
        } else if (target instanceof Player) {
            data.put("target", getPlayerId((Player) target));
        }

        data.put("amount", amount);
        data.put("type", damageType);
        data.put("prevented", 0);

        addEvent(timeMarker, "SYS", "DAMAGE", data);
    }

    /**
     * Log life total change.
     */
    public void logLifeChange(Player player, int delta, int newTotal, String cause, String timeMarker) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("player", getPlayerId(player));
        data.put("delta", delta);
        data.put("new_total", newTotal);
        data.put("cause", cause);

        addEvent(timeMarker, "SYS", "LIFE", data);
    }

    /**
     * Log a phase change.
     * OPTIMIZATION: Phase events are buffered and only logged if something happens in that phase.
     * Empty phases (especially UNTAP, DRAW, CLEANUP, UPKEEP, END_OF_TURN) are skipped.
     */
    public void logPhaseChange(String phase, String step, Player activePlayer, String timeMarker) {
        // Flush previous pending phase event if we're entering a new phase
        if (pendingPhaseEvent != null && !isSamePhase(phase, currentPhase)) {
            // Previous phase had no events - skip it!
            long phaseDuration = System.currentTimeMillis() - phaseStartTime;
            if (phaseDuration > 5000) { // Log if phase took >5 seconds (player was thinking)
                System.out.println("[Replay Optimization] Skipped empty phase: " + currentPhase +
                                   " (duration: " + phaseDuration + "ms)");
            }
            pendingPhaseEvent = null;
        }

        // Check if this is an "always empty" phase
        if (ALWAYS_EMPTY_PHASES.contains(phase)) {
            // Skip these phases entirely - they never have meaningful events
            currentPhase = phase;
            phaseStartTime = System.currentTimeMillis();
            return;
        }

        // Create phase event but don't add it yet - wait to see if something happens
        Map<String, Object> data = new HashMap<>();
        data.put("phase", phase);
        data.put("step", step);
        data.put("active_player", getPlayerId(activePlayer));

        // Store the pending event data
        pendingPhaseEvent = new HashMap<>();
        pendingPhaseEvent.put("timeMarker", timeMarker);
        pendingPhaseEvent.put("data", data);

        currentPhase = phase;
        phaseStartTime = System.currentTimeMillis();
    }

    /**
     * Check if two phases are the same (ignoring step differences).
     */
    private boolean isSamePhase(String phase1, String phase2) {
        if (phase1 == null || phase2 == null) return false;
        return phase1.equals(phase2);
    }

    /**
     * Flush pending phase event before adding any important event.
     * This ensures the phase is logged if something actually happens.
     */
    private void flushPendingPhase() {
        if (pendingPhaseEvent != null) {
            // Something happened! Log the phase event
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) pendingPhaseEvent.get("data");
            String timeMarker = (String) pendingPhaseEvent.get("timeMarker");
            addEvent(timeMarker, "SYS", "PHASE_CHANGE", data);
            pendingPhaseEvent = null;
        }
    }

    /**
     * Format a zone for the log.
     */
    private String formatZone(ZoneType zone, Player player) {
        switch (zone) {
            case Battlefield:
                return "battlefield";
            case Stack:
                return "stack";
            case Exile:
                return "exile";
            case Hand:
                return getPlayerId(player) + ":hand";
            case Library:
                return getPlayerId(player) + ":library";
            case Graveyard:
                return getPlayerId(player) + ":graveyard";
            case Command:
                return getPlayerId(player) + ":command";
            default:
                return zone.name().toLowerCase();
        }
    }

    /**
     * Check if a zone is public.
     */
    private boolean isPublicZone(ZoneType zone) {
        return zone == ZoneType.Battlefield ||
               zone == ZoneType.Stack ||
               zone == ZoneType.Graveyard ||
               zone == ZoneType.Exile ||
               zone == ZoneType.Command;
    }

    /**
     * Generate time marker from turn and phase.
     */
    public String generateTimeMarker(int turn, String phase) {
        return String.format("T%d.%s", turn, phase);
    }

    /**
     * Export the replay log to a JSON file.
     */
    public File exportToFile(File outputDir) throws IOException {
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDir);
            }
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String filename = String.format("replay_%s_%s.json",
            replayLog.getMeta().getGameType(), timestamp);
        File outputFile = new File(outputDir, filename);

        ReplayJsonSerializer.writeToFile(replayLog, outputFile);

        return outputFile;
    }

    /**
     * Get the replay log object.
     */
    public ReplayLog getReplayLog() {
        return replayLog;
    }

    /**
     * Export to JSON string.
     */
    public String toJson() {
        return ReplayJsonSerializer.toJson(replayLog);
    }
}

