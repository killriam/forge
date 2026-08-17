package forge.game;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.Subscribe;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.card.CounterEnumType;
import forge.game.event.*;
import forge.game.event.GameEventCardDamaged.DamageType;
import forge.game.log.ReplayNotationExporter;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.util.*;

public class GameLogFormatter extends IGameEventVisitor.Base<GameLogEntry> {
    private final Localizer localizer = Localizer.getInstance();
    private final GameLog log;

    // Board state tracking for ANALYSIS level
    private final Map<Player, Map<ZoneType, Integer>> turnStartBoardState = new HashMap<>();
    private final List<String> turnZoneChanges = new ArrayList<>();

    // Replay Notation Integration (optional)
    private ReplayNotationExporter replayExporter;
    private int currentTurn = 0;
    private String currentPhase = "PREGAME";
    private int priorityCounter = 0;
    private boolean initialStateCaptured = false; // P1.1: Track if initial state has been captured
    private PlayerView previousTurnPlayer = null; // P2.10: Track for ACTIVE_PLAYER_CHANGE
    private PlayerView currentActivePlayer = null; // Track whose turn it is for time markers
    // P4.3: Track lands played via PLAY_LAND to suppress duplicate MOVE events
    private final Set<CardView> recentlyPlayedLands = new HashSet<>();
    // P15: Track drawn/discarded cards to suppress duplicate MOVE events
    private final Set<CardView> recentlyDrawnCards = new HashSet<>();
    private final Set<CardView> recentlyDiscardedCards = new HashSet<>();

    public GameLogFormatter(GameLog gameLog) {
        log = gameLog;
    }

    /**
     * Enable JSON Replay Notation logging alongside text logging.
     * @param exporter The replay notation exporter to use
     */
    public void setReplayExporter(ReplayNotationExporter exporter) {
        this.replayExporter = exporter;
    }

    /**
     * Get the replay notation exporter if enabled.
     * @return The exporter, or null if not enabled
     */
    public ReplayNotationExporter getReplayExporter() {
        return replayExporter;
    }

    /**
     * Generate time marker for current game state.
     * @return Time marker string in format T<turn>.<phase>[:<priority>]
     */
    private String generateTimeMarker() {
        StringBuilder marker = new StringBuilder();
        marker.append("T").append(currentTurn);
        marker.append(".").append(currentPhase);
        if (priorityCounter > 0) {
            marker.append(":").append(priorityCounter);
        }
        return marker.toString();
    }

    @Override
    public GameLogEntry visit(GameEventGameOutcome ev) {
        // Turn number counted from the starting player
        int lastTurn = (int)Math.ceil((float)ev.lastTurnNumber() / 2.0);
        log.add(GameLogEntryType.GAME_OUTCOME, localizer.getMessage("lblTurn") + " " + lastTurn);

        for (String outcome : ev.outcomeStrings()) {
            log.add(GameLogEntryType.GAME_OUTCOME, outcome);
        }
        // Update replay notation with game outcome
        if (replayExporter != null) {
            replayExporter.setOutcomeFromEvent(ev.winningPlayerName(), ev.lastTurnNumber(), ev.outcomeStrings());
            replayExporter.buildGameSummary();
        }

        return new GameLogEntry(GameLogEntryType.MATCH_RESULTS, ev.matchSummary());
    }

    @Override
    public GameLogEntry visit(GameEventGameStarted ev) {
        String message = ev.toString();

        // Log to JSON if enabled - GAME_START is the first event
        if (replayExporter != null) {
            forge.game.player.Player firstPlayer = replayExporter.resolvePlayer(ev.firstTurn());
            java.util.List<forge.game.player.Player> allPlayers = new java.util.ArrayList<>();
            for (PlayerView pv : ev.players()) {
                forge.game.player.Player p = replayExporter.resolvePlayer(pv);
                if (p != null) allPlayers.add(p);
            }
            replayExporter.logGameStart(
                ev.gameType().toString(),
                firstPlayer,
                allPlayers,
                generateTimeMarker()
            );
            if (firstPlayer != null) {
                replayExporter.setTossWinner(firstPlayer, true);
            }
        }

        return new GameLogEntry(GameLogEntryType.GAME_OUTCOME, message);
    }

    @Override
    public GameLogEntry visit(GameEventScry ev) {
        String scryOutcome;
        if (ev.toTop() > 0 && ev.toBottom() > 0) {
            scryOutcome = localizer.getMessage("lblLogScryTopBottomLibrary").replace("%s", ev.player().toString()).replace("%top", String.valueOf(ev.toTop())).replace("%bottom", String.valueOf(ev.toBottom()));
        } else if (ev.toBottom() == 0) {
            scryOutcome = localizer.getMessage("lblLogScryTopLibrary").replace("%s", ev.player().toString()).replace("%top", String.valueOf(ev.toTop()));
        } else {
            scryOutcome = localizer.getMessage("lblLogScryBottomLibrary").replace("%s", ev.player().toString()).replace("%bottom", String.valueOf(ev.toBottom()));
        }

        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, scryOutcome);
    }

    @Override
    public GameLogEntry visit(GameEventSurveil ev) {
        String surveilOutcome;
        if (ev.toLibrary() > 0 && ev.toGraveyard() > 0) {
            surveilOutcome = localizer.getMessage("lblLogSurveiledToLibraryGraveyard", ev.player(), ev.toLibrary(), ev.toGraveyard());
        } else if (ev.toGraveyard() == 0) {
            surveilOutcome = localizer.getMessage("lblLogSurveiledToLibrary", ev.player(), ev.toLibrary());
        } else {
            surveilOutcome = localizer.getMessage("lblLogSurveiledToGraveyard", ev.player(), ev.toGraveyard());
        }

        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, surveilOutcome);
    }

    @Override
    public GameLogEntry visit(GameEventSpellResolved ev) {
        String messageForLog = ev.hasFizzled() ? localizer.getMessage("lblLogCardAbilityFizzles", ev.spell().getHostCard().getName()) : ev.stackDescription();

        // For ANALYSIS level, also log that the spell is resolving
        if (ev.spell().isSpell() && !ev.hasFizzled()) {
            String analysisMsg = String.format("Resolving: %s", ev.spell().getHostCard().getName());
            log.add(GameLogEntryType.ANALYSIS, analysisMsg);
        }

        // Log to JSON if enabled - RESOLVE event
        if (replayExporter != null) {
            Card resolved = replayExporter.resolveCard(ev.spell().getHostCard());
            if (resolved != null) {
                replayExporter.logResolve(resolved, ev.hasFizzled(), generateTimeMarker());
            }
        }

        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, messageForLog, ev.spell().getHostCard());
    }

    @Override
    public GameLogEntry visit(GameEventSpellAbilityCast event) {
        String player = event.si().getActivatingPlayer().getName();
        String action = event.sa().isSpell() ? localizer.getMessage("lblCast")
                : event.si().isTrigger() ? localizer.getMessage("lblTriggered")
                        : localizer.getMessage("lblActivated");
        String siText = event.si() != null ? event.si().getText() : "";
        String object = siText != null && siText.startsWith("Morph ")
                ? localizer.getMessage("lblMorph")
                : event.sa().getHostCard().getName();

        String messageForLog;

        if (event.targetDescription() != null) {
            messageForLog = localizer.getMessage("lblLogPlayerActionObjectWitchTarget", player, action, object, event.targetDescription());
        } else {
            messageForLog = localizer.getMessage("lblLogPlayerActionObject", player, action, object);
        }

        // Log to JSON if enabled
        if (replayExporter != null) {
            priorityCounter++; // Increment priority when spell/ability is cast
            Card hostCard = replayExporter.resolveCard(event.sa().getHostCard());
            forge.game.player.Player activatingPlayer = replayExporter.resolvePlayer(event.si().getActivatingPlayer());
            if (hostCard != null && activatingPlayer != null) {
                // event.realSa() is the actual SpellAbility (not just its View) - carried on the
                // event since GameEventSpellAbilityCast gained the field, so cost/X/alternative-
                // cost details are available here instead of always passing null.
                if (event.sa().isSpell()) {
                    replayExporter.logCast(hostCard, activatingPlayer,
                                         generateTimeMarker(), event.realSa());
                } else if (event.si().isTrigger()) {
                    replayExporter.logTrigger(hostCard, activatingPlayer,
                                             event.realSa(), generateTimeMarker());
                } else {
                    // Activated ability (non-spell, non-trigger)
                    replayExporter.logActivate(hostCard, activatingPlayer,
                                              event.realSa(), generateTimeMarker());
                }
            }
        }

        return new GameLogEntry(GameLogEntryType.STACK_ADD, messageForLog, event.sa().getHostCard());
    }

    @Override
    public GameLogEntry visit(GameEventCardModeChosen ev) {
        if (!ev.log()) {
            return null;
        }

        String modeChoiceOutcome;
        if (ev.random()) {
            modeChoiceOutcome = localizer.getMessage("lblLogRandomMode", ev.cardName(), ev.mode());
        } else {
            modeChoiceOutcome = localizer.getMessage("lblLogPlayerChosenModeForCard",
                    ev.player().toString(), ev.mode(), ev.cardName());
        }
        String name = CardTranslation.getTranslatedName(ev.cardName());
        modeChoiceOutcome = TextUtil.fastReplace(modeChoiceOutcome, "CARDNAME", name);
        modeChoiceOutcome = TextUtil.fastReplace(modeChoiceOutcome, "NICKNAME",
                Lang.getInstance().getNickName(name));
        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, modeChoiceOutcome);
    }

    @Override
    public GameLogEntry visit(GameEventRandomLog ev) {
        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, ev.message());
    }

    @Override
    public GameLogEntry visit(final GameEventPlayerControl event) {
        final String newLobbyPlayerName = event.newLobbyPlayerName();
        final PlayerView p = event.player();

        final String message;
        if (newLobbyPlayerName == null) {
            message = localizer.getMessage("lblLogPlayerHasRestoredControlThemself", p.getName());
        } else {
            if (newLobbyPlayerName.equals(p.getName())) return null;
            message = localizer.getMessage("lblLogPlayerControlledTargetPlayer", p.getName(), newLobbyPlayerName);
        }
        return new GameLogEntry(GameLogEntryType.PLAYER_CONTROL, message);
    }

    @Override
    public GameLogEntry visit(GameEventTurnPhase ev) {
        PlayerView p = ev.playerTurn();
        // Note: currentActivePlayer tracked in visit(GameEventTurnBegan) where Player type is available
        String phaseMessage = ev.phaseDesc() + Lang.getInstance().getPossessedObject(p.getName(), ev.phase().nameForUi);

        // Update time tracking for replay notation
        if (ev.phase() == forge.game.phase.PhaseType.UPKEEP && !ev.phaseDesc().equals("Repeat")) {
            currentTurn++;
            priorityCounter = 0;

            // P1.1: Recapture initial_state once at Turn 1 upkeep,
            // after opening hands are drawn and mulligans are finalized.
            if (!initialStateCaptured && replayExporter != null) {
                replayExporter.recaptureInitialState();
                initialStateCaptured = true;
            }
        }

        // Map phase to short name for time marker
        String phaseName = ev.phase().name();
        if (phaseName.equals("MAIN1")) {
            currentPhase = "MP1";
        } else if (phaseName.equals("MAIN2")) {
            currentPhase = "MP2";
        } else if (phaseName.equals("COMBAT_BEGIN") || phaseName.equals("COMBAT_DECLARE_ATTACKERS")
                || phaseName.equals("COMBAT_DECLARE_BLOCKERS") || phaseName.equals("COMBAT_FIRST_STRIKE_DAMAGE")
                || phaseName.equals("COMBAT_DAMAGE") || phaseName.equals("COMBAT_END")) {
            currentPhase = "COMBAT";
        } else {
            currentPhase = phaseName;
        }

        // Log to JSON if enabled
        if (replayExporter != null && !ev.phaseDesc().equals("Repeat")) {
            replayExporter.logPhaseChange(currentPhase, phaseName, replayExporter.resolvePlayer(p), generateTimeMarker());
        }

        return new GameLogEntry(GameLogEntryType.PHASE, phaseMessage);
    }

    @Override
    public GameLogEntry visit(GameEventCardDamaged event) {
        String additionalLog = "";
        if (event.type() == DamageType.Deathtouch) {
            additionalLog = localizer.getMessage("lblDeathtouch");
        }
        if (event.type() == DamageType.M1M1Counters) {
            additionalLog = localizer.getMessage("lblAsM1M1Counters");
        }
        if (event.type() == DamageType.LoyaltyLoss) {
            additionalLog = localizer.getMessage("lblRemovingNLoyaltyCounter", event.amount());
        }
        String message = localizer.getMessage("lblSourceDealsNDamageToDest", event.source(), event.amount(), additionalLog.isEmpty() ? "" : " (" + additionalLog + ")", event.card().toString());

        // Log to JSON if enabled
        if (replayExporter != null) {
            String damageType = "non-combat";
            Card srcCard = replayExporter.resolveCard(event.source());
            Card tgtCard = replayExporter.resolveCard(event.card());
            if (srcCard != null && tgtCard != null) {
                replayExporter.logDamage(srcCard, tgtCard, event.amount(), damageType, generateTimeMarker());
            }
        }

        return new GameLogEntry(GameLogEntryType.DAMAGE, message, event.source());
    }

    /* (non-Javadoc)
     * @see forge.game.event.IGameEventVisitor.Base#visit(forge.game.event.GameEventLandPlayed)
     */
    @Override
    public GameLogEntry visit(GameEventLandPlayed ev) {
        String message = localizer.getMessage("lblLogPlayerPlayedLand", ev.player(), ev.land());

        // Log to JSON if enabled - use PLAY_LAND event type with player as actor
        if (replayExporter != null) {
            Card landCard = replayExporter.resolveCard(ev.land());
            forge.game.player.Player landPlayer = replayExporter.resolvePlayer(ev.player());
            if (landCard != null && landPlayer != null) {
                replayExporter.logPlayLand(landCard, landPlayer, generateTimeMarker());
            }
            // P4.3: Track this land to suppress the duplicate MOVE event
            recentlyPlayedLands.add(ev.land());
        }

        return new GameLogEntry(GameLogEntryType.LAND, message, ev.land());
    }

    @Override
    public GameLogEntry visit(GameEventTurnBegan event) {
        // event.turnOwner() is now a PlayerView in updated upstream
        PlayerView turnPlayer = event.turnOwner();
        turnZoneChanges.clear(); // Clear zone changes from previous turn
        currentActivePlayer = turnPlayer; // Track active player

        // P2.10: Emit ACTIVE_PLAYER_CHANGE for turn transitions
        if (replayExporter != null) {
            forge.game.player.Player resolvedPrev = replayExporter.resolvePlayer(previousTurnPlayer);
            forge.game.player.Player resolvedCurr = replayExporter.resolvePlayer(turnPlayer);
            replayExporter.logActivePlayerChange(resolvedPrev, resolvedCurr,
                                                  event.turnNumber(), generateTimeMarker());
            replayExporter.onTurnBegin(event.turnNumber(), resolvedCurr);
            previousTurnPlayer = turnPlayer;
        }

        String message = localizer.getMessage("lblLogTurnNOwnerByPlayer", event.turnNumber(), event.turnOwner());
        return new GameLogEntry(GameLogEntryType.TURN, message);
    }

    @Override
    public GameLogEntry visit(GameEventPlayerDamaged ev) {
        String extra = ev.infect() ? localizer.getMessage("lblLogAsPoisonCounters") : "";
        String damageType = ev.combat() ? localizer.getMessage("lblCombat") : localizer.getMessage("lblNonCombat");
        String message = localizer.getMessage("lblLogSourceDealsNDamageOfTypeToDest", ev.source(),
                            ev.amount(), damageType, ev.target(), extra);

        // Log to JSON if enabled
        if (replayExporter != null) {
            String type = ev.combat() ? "combat" : "spell";
            Card srcCard = replayExporter.resolveCard(ev.source());
            forge.game.player.Player tgtPlayer = replayExporter.resolvePlayer(ev.target());
            if (srcCard != null && tgtPlayer != null) {
                replayExporter.logDamage(srcCard, tgtPlayer, ev.amount(), type, generateTimeMarker());
            }
        }

        return new GameLogEntry(GameLogEntryType.DAMAGE, message, ev.source());
    }

    @Override
    public GameLogEntry visit(GameEventPlayerLivesChanged ev) {
        String message = localizer.getMessage("lblLogPlayerLifeChange", ev.player(), ev.oldLives(), ev.newLives());
        // P2.4: Wire existing logLifeChange() — emit LIFE event to JSON
        if (replayExporter != null) {
            int delta = ev.newLives() - ev.oldLives();
            String cause = delta < 0 ? "damage_or_loss" : "gain";
            replayExporter.logLifeChange(replayExporter.resolvePlayer(ev.player()), delta, ev.newLives(), cause, generateTimeMarker());
        }
        return new GameLogEntry(GameLogEntryType.LIFE, message);
    }

    @Override
    public GameLogEntry visit(GameEventCardTapped ev) {
        // P2.7: Emit TAP event for tap/untap state changes to JSON
        if (replayExporter != null) {
            Card tappedCard = replayExporter.resolveCard(ev.card());
            if (tappedCard != null) {
                replayExporter.logTap(tappedCard, ev.tapped(), generateTimeMarker());
            }
        }
        // No text log entry — tap/untap is handled by other UI components
        return null;
    }

    @Override
    public GameLogEntry visit(GameEventCardCounters ev) {
        // P2.8: Emit COUNTERS event for counter changes to JSON
        if (replayExporter != null) {
            String counterType = ev.type() != null ? ev.type().getName() : "unknown";
            Card counterCard = replayExporter.resolveCard(ev.card());
            if (counterCard != null) {
                replayExporter.logCounters(counterCard, counterType, ev.oldValue(), ev.newValue(), generateTimeMarker());
            }
        }
        // No text log entry — counter changes show via other mechanisms
        return null;
    }

    @Override
    public GameLogEntry visit(GameEventPlayerPoisoned ev) {
        String message = localizer.getMessage("lblLogPlayerReceivesNPosionCounterFrom",
                            ev.receiver(), ev.amount(), ev.source());
        return new GameLogEntry(GameLogEntryType.DAMAGE, message);
    }

    @Override
    public GameLogEntry visit(GameEventPlayerRadiation ev) {
        String message;
        final int change = ev.change();
        String radCtr = CounterEnumType.RAD.getName().toLowerCase() + " " +
                Localizer.getInstance().getMessage("lblCounter").toLowerCase();
        if (change >= 0) message = localizer.getMessage("lblLogPlayerRadiation",
                ev.receiver().toString(), Lang.nounWithNumeralExceptOne(String.valueOf(change), radCtr),
                ev.source().toString());
        else message = localizer.getMessage("lblLogPlayerRadRemove",
                ev.receiver().toString(), Lang.nounWithNumeralExceptOne(String.valueOf(Math.abs(change)), radCtr));
        return new GameLogEntry(GameLogEntryType.DAMAGE, message);
    }

    @Override
    public GameLogEntry visit(final GameEventAttackersDeclared ev) {
        final StringBuilder sb = new StringBuilder();

        // Loop through Defenders
        // Append Defending Player/Planeswalker

        // Not a big fan of the triple nested loop here
        for (GameEntityView k : ev.attackersMap().keySet()) {
            Collection<CardView> attackers = ev.attackersMap().get(k);
            if (attackers == null || attackers.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(localizer.getMessage("lblLogPlayerAssignedAttackerToAttackTarget", ev.player(), Lang.joinHomogenous(attackers), k));
        }
        // Log to JSON if enabled - DECLARE_ATTACKERS
        // Note: ev.attackersMap() uses View types (CardView/GameEntityView) which cannot be
        // directly passed to logDeclareAttackers; skipping detailed export for now.
        if (sb.length() == 0) return null;

        return new GameLogEntry(GameLogEntryType.COMBAT, sb.toString());
    }

    @Override
    public GameLogEntry visit(final GameEventBlockersDeclared ev) {
        final StringBuilder sb = new StringBuilder();

        // Loop through Defenders
        // Append Defending Player/Planeswalker

        for (Entry<GameEntityView, Multimap<CardView, CardView>> kv : ev.blockers().entrySet()) {
            GameEntityView defender = kv.getKey();
            Multimap<CardView, CardView> attackers = kv.getValue();
            if (attackers == null || attackers.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }

            String controllerName;
            if (defender instanceof CardView c && c.getController() != null) {
                controllerName = c.getCurrentState().isBattle() ? c.getProtectingPlayer().getName() : c.getController().getName();
            } else {
                controllerName = defender.getName();
            }

            boolean firstAttacker = true;
            for (final Entry<CardView, Collection<CardView>> att : attackers.asMap().entrySet()) {
                if (!firstAttacker) sb.append("\n");

                Collection<CardView> blockers = att.getValue();
                if (blockers.isEmpty() || Iterables.get(blockers, 0) == att.getKey()) {
                    sb.append(localizer.getMessage("lblLogPlayerDidntBlockAttacker", controllerName, att.getKey()));
                } else {
                    sb.append(localizer.getMessage("lblLogPlayerAssignedBlockerToBlockAttacker", controllerName, Lang.joinHomogenous(blockers), att.getKey()));
                }
                firstAttacker = false;
            }
        }

        // Log to JSON if enabled - DECLARE_BLOCKERS
        // Note: ev.blockers() uses View types (CardView) which cannot be directly passed
        // to logDeclareBlockers; skipping detailed export for now.

        return new GameLogEntry(GameLogEntryType.COMBAT, sb.toString());
    }

    @Override
    public GameLogEntry visit(GameEventMulligan ev) {
        forge.game.player.Player mulliganPlayer = replayExporter != null
                ? replayExporter.resolvePlayer(ev.player()) : null;
        int cardsKept = mulliganPlayer != null
                ? mulliganPlayer.getZone(ZoneType.Hand).size() : 0;
        String message = localizer.getMessage("lblPlayerHasMulliganedDownToNCards").replace("%d", String.valueOf(cardsKept)).replace("%s", ev.player().toString());

        // Log to JSON if enabled - MULLIGAN is a player decision event
        if (mulliganPlayer != null) {
            replayExporter.recordMulliganTaken(mulliganPlayer);
            replayExporter.logMulligan(mulliganPlayer, cardsKept, false, generateTimeMarker());
        }

        return new GameLogEntry(GameLogEntryType.MULLIGAN, message);
    }

    @Override
    public GameLogEntry visit(GameEventCardForetold ev) {
        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, ev.toString());
    }

    @Override
    public GameLogEntry visit(GameEventCardPlotted ev) {
        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, ev.toString(), ev.card());
    }

    @Override
    public GameLogEntry visit(GameEventDoorChanged ev) {
        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, ev.toString());
    }

    @Override
    public GameLogEntry visit(GameEventCardChangeZone ev) {
        CardView card = ev.card();
        forge.game.zone.ZoneView from = ev.from();
        forge.game.zone.ZoneView to = ev.to();

        if (card == null || from == null || to == null) {
            return null;
        }

        ZoneType fromZone = from.zoneType();
        ZoneType toZone = to.zoneType();

        // Log mid-game ante additions (e.g. Contract from Below, Demonic Attorney)
        if (toZone == ZoneType.Ante && fromZone != ZoneType.Ante) {
            return new GameLogEntry(GameLogEntryType.ANTE,
                    (card != null ? card.getOwner() + " anted " + card : "a card was anted"));
        }

        // Build detailed zone change message for ANALYSIS level
        String cardName = card.getName();
        if (cardName == null || cardName.isEmpty()) {
            cardName = card.toString();
            if (cardName == null || cardName.isEmpty()) {
                cardName = "[Unknown Card]";
            }
        }

        String ownerName = (from.player() != null) ? from.player().getName() : "Unknown";

        // Add type prefix for battlefield entries using CardView's current state
        String typePrefix = "";
        if (toZone == ZoneType.Battlefield) {
            if (card.getCurrentState().isLand()) {
                typePrefix = "[LAND] ";
            } else if (card.getCurrentState().isCreature()) {
                typePrefix = "[CREATURE] ";
            }
        }

        String message = String.format("%s: %s%s moved from %s to %s",
            ownerName, typePrefix, cardName, fromZone.toString(), toZone.toString());

        // Track zone changes for turn summary
        turnZoneChanges.add(message);

        // Log to JSON if enabled - use specific event types based on zone transition
        if (replayExporter != null) {
            Card underlying = replayExporter.resolveCard(card);
            forge.game.player.Player owner = replayExporter.resolvePlayer(from.player());

            // P4.3: Skip MOVE if this card was just played as a land (already emitted PLAY_LAND)
            if (recentlyPlayedLands.remove(card)) {
                // Already logged as PLAY_LAND — suppress duplicate MOVE
            } else if (recentlyDrawnCards.remove(card)) {
                // P15: Already logged as DRAW — suppress duplicate MOVE
            } else if (recentlyDiscardedCards.remove(card)) {
                // P15: Already logged as DISCARD — suppress duplicate MOVE
            } else if (underlying != null) {
                if (fromZone == ZoneType.Library && toZone == ZoneType.Hand) {
                    // Drawing a card
                    replayExporter.logDraw(underlying, owner, generateTimeMarker());
                    recentlyDrawnCards.add(card);
                } else if (fromZone == ZoneType.Hand && toZone == ZoneType.Graveyard) {
                    // Discarding a card - assume player choice for now
                    replayExporter.logDiscard(underlying, owner, true, generateTimeMarker());
                    recentlyDiscardedCards.add(card);
                } else {
                    // Generic zone change - use MOVE event
                    replayExporter.logZoneChange(underlying, fromZone, toZone, generateTimeMarker(), owner);
                }

                // Track creatures entering battlefield for game summary
                if (toZone == ZoneType.Battlefield && underlying.isCreature() && underlying.getController() != null) {
                    replayExporter.trackCreaturePlayed(underlying.getController());
                }
            }
        }

        // Special ANALYSIS log for lands entering the battlefield
        if (toZone == ZoneType.Battlefield && card.getCurrentState().isLand()) {
            String landMessage = String.format("Land added to battlefield: %s (%s) from %s",
                cardName, ownerName, fromZone.toString());
            log.add(GameLogEntryType.ANALYSIS, landMessage);
        }

        return new GameLogEntry(GameLogEntryType.ANALYSIS, message);
    }

    @Override
    public GameLogEntry visit(GameEventAddLog ev) {
        return new GameLogEntry(ev.type(), ev.message(), ev.sourceCard());
    }

    @Override
    public GameLogEntry visit(GameEventTurnEnded ev) {
        // Generate board state delta summary at end of turn
        return generateBoardStateDelta();
    }

    /**
     * Captures the current board state for all players at turn start.
     */
    private void captureBoardState(Game game) {
        turnStartBoardState.clear();

        for (Player player : game.getPlayers()) {
            Map<ZoneType, Integer> playerZones = new HashMap<>();

            // Track key zones
            playerZones.put(ZoneType.Battlefield, player.getZone(ZoneType.Battlefield).size());
            playerZones.put(ZoneType.Hand, player.getZone(ZoneType.Hand).size());
            playerZones.put(ZoneType.Graveyard, player.getZone(ZoneType.Graveyard).size());
            playerZones.put(ZoneType.Library, player.getZone(ZoneType.Library).size());
            playerZones.put(ZoneType.Exile, player.getZone(ZoneType.Exile).size());

            turnStartBoardState.put(player, playerZones);
        }
    }

    /**
     * Generates a board state delta summary for ANALYSIS level logging.
     */
    private GameLogEntry generateBoardStateDelta() {
        if (turnStartBoardState.isEmpty()) {
            return null; // No initial state captured
        }

        StringBuilder summary = new StringBuilder();
        summary.append("=== Turn Summary - Board State Changes ===\n");

        // Add zone change details
        if (!turnZoneChanges.isEmpty()) {
            summary.append("Zone Changes:\n");
            for (String change : turnZoneChanges) {
                summary.append("  - ").append(change).append("\n");
            }
        }

        // Calculate deltas
        summary.append("\nBoard State Delta:\n");
        for (Map.Entry<Player, Map<ZoneType, Integer>> entry : turnStartBoardState.entrySet()) {
            Player player = entry.getKey();
            Map<ZoneType, Integer> startState = entry.getValue();

            summary.append(player.getName()).append(":\n");

            for (Map.Entry<ZoneType, Integer> zoneEntry : startState.entrySet()) {
                ZoneType zone = zoneEntry.getKey();
                int startCount = zoneEntry.getValue();
                int currentCount = player.getZone(zone).size();
                int delta = currentCount - startCount;

                if (delta != 0) {
                    String deltaStr = delta > 0 ? "+" + delta : String.valueOf(delta);
                    summary.append(String.format("  %s: %d -> %d (%s)\n",
                        zone.toString(), startCount, currentCount, deltaStr));
                }
            }
        }

        return new GameLogEntry(GameLogEntryType.ANALYSIS, summary.toString());
    }

    /**
     * Counts the number of lands a player has on the battlefield.
     */
    private int countLands(Player player) {
        int count = 0;
        for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
            if (card.isLand()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Calculates the available mana a player can produce from untapped sources.
     * This is an estimate based on mana abilities of permanents on the battlefield.
     */
    private int calculateAvailableMana(Player player) {
        int availableMana = 0;

        // Add mana already in the pool
        availableMana += player.getManaPool().totalMana();

        // Calculate potential mana from untapped sources
        for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
            if (card.isUntapped() && !card.getManaAbilities().isEmpty()) {
                // Estimate the maximum mana this source can produce
                boolean counted = false;
                for (forge.game.spellability.SpellAbility ma : card.getManaAbilities()) {
                    // Count the number of mana symbols this ability produces
                    String produced = ma.getParamOrDefault("Produced", "");
                    if (!produced.isEmpty()) {
                        // Split by space to count individual mana symbols
                        String[] manaSymbols = produced.split(" ");
                        int producedAmount = ma.hasParam("Amount")
                            ? forge.game.ability.AbilityUtils.calculateAmount(card, ma.getParam("Amount"), ma)
                            : 1;
                        availableMana += manaSymbols.length * producedAmount;
                        counted = true;
                        break; // Only count one ability per card (the best one)
                    }
                }

                // If no "Produced" param found but has mana abilities, assume 1 mana (for basic lands)
                if (!counted && card.isLand()) {
                    availableMana += 1;
                }
            }
        }

        return availableMana;
    }

    @Subscribe
    public void recieve(GameEvent ev) {
        GameLogEntry le = ev.visit(this);
        if (le != null) {
            log.add(le);
        }
    }
}
