package forge.game;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Iterables;
import com.google.common.eventbus.Subscribe;

import forge.LobbyPlayer;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.event.*;
import forge.game.event.GameEventCardDamaged.DamageType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.TargetChoices;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.util.*;
import forge.util.maps.MapOfLists;

public class GameLogFormatter extends IGameEventVisitor.Base<GameLogEntry> {
    private final Localizer localizer = Localizer.getInstance();
    private final GameLog log;

    // Board state tracking for ANALYSIS level
    private final Map<Player, Map<ZoneType, Integer>> turnStartBoardState = new HashMap<>();
    private final List<String> turnZoneChanges = new ArrayList<>();

    public GameLogFormatter(GameLog gameLog) {
        log = gameLog;
    }

    @Override
    public GameLogEntry visit(GameEventGameOutcome ev) {
        // Turn number counted from the starting player
        int lastTurn = (int)Math.ceil((float)ev.result().getLastTurnNumber() / 2.0);
        log.add(GameLogEntryType.GAME_OUTCOME, localizer.getMessage("lblTurn") + " " + lastTurn);

        for (String outcome : ev.result().getOutcomeStrings()) {
            log.add(GameLogEntryType.GAME_OUTCOME, outcome);
        }
        return generateSummary(ev.history());
    }

    @Override
    public GameLogEntry visit(GameEventScry ev) {
        String scryOutcome = "";

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
        String surveilOutcome = "";

        if (ev.toLibrary() > 0 && ev.toGraveyard() > 0) {
            surveilOutcome = localizer.getMessage("lblLogSurveiledToLibraryGraveyard", ev.player().toString(), String.valueOf(ev.toLibrary()), String.valueOf(ev.toGraveyard()));
        } else if (ev.toGraveyard() == 0) {
            surveilOutcome = localizer.getMessage("lblLogSurveiledToLibrary", ev.player().toString(), String.valueOf(ev.toLibrary()));
        } else {
            surveilOutcome = localizer.getMessage("lblLogSurveiledToGraveyard", ev.player().toString(), String.valueOf(ev.toGraveyard()));
        }

        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, surveilOutcome);
    }

    @Override
    public GameLogEntry visit(GameEventSpellResolved ev) {
        String messageForLog = ev.hasFizzled() ? localizer.getMessage("lblLogCardAbilityFizzles", ev.spell().getHostCard().toString()) : ev.spell().getStackDescription();

        // For ANALYSIS level, also log that the spell is resolving
        if (ev.spell().isSpell() && !ev.hasFizzled()) {
            String analysisMsg = String.format("Resolving: %s", ev.spell().getHostCard().getName());
            log.add(GameLogEntryType.ANALYSIS, analysisMsg);
        }

        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, messageForLog);
    }

    @Override
    public GameLogEntry visit(GameEventSpellAbilityCast event) {
        String player = event.sa().getActivatingPlayer().getName();
        String action = event.sa().isSpell() ? localizer.getMessage("lblCast")
                : event.sa().isTrigger() ? localizer.getMessage("lblTriggered")
                        : localizer.getMessage("lblActivated");
        String object = event.si().getStackDescription().startsWith("Morph ")
                ? localizer.getMessage("lblMorph")
                : event.sa().getHostCard().toString();

        String messageForLog = "";

        if (event.sa().getTargetRestrictions() != null) {
            StringBuilder sb = new StringBuilder();

            for (TargetChoices ch : event.sa().getAllTargetChoices()) {
                if (null != ch) {
                    sb.append(ch);
                }
            }
            messageForLog = localizer.getMessage("lblLogPlayerActionObjectWitchTarget", player, action, object, sb.toString());
        } else {
            messageForLog = localizer.getMessage("lblLogPlayerActionObject", player, action, object);
        }

        return new GameLogEntry(GameLogEntryType.STACK_ADD, messageForLog);
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

    private static GameLogEntry generateSummary(final Collection<GameOutcome> gamesPlayed) {
        final GameOutcome outcome1 = Iterables.getFirst(gamesPlayed, null);
        final HashMap<RegisteredPlayer, String> players = outcome1.getPlayerNames();
        final HashMap<RegisteredPlayer, Integer> winCount = new HashMap<>();

        // Calculate total games each player has won.
        for (final GameOutcome game : gamesPlayed) {
            RegisteredPlayer player = game.getWinningPlayer();

            int amount = winCount.getOrDefault(player, 0);
            winCount.put(player, amount + 1);
        }

        final StringBuilder sb = new StringBuilder();
        for (Entry<RegisteredPlayer, String> entry : players.entrySet()) {
            int amount = winCount.getOrDefault(entry.getKey(), 0);

            sb.append(entry.getValue()).append(": ").append(amount).append(" ");
        }

        return new GameLogEntry(GameLogEntryType.MATCH_RESULTS, sb.toString());
    }

    @Override
    public GameLogEntry visit(final GameEventPlayerControl event) {
        final LobbyPlayer newLobbyPlayer = event.newLobbyPlayer();
        final Player p = event.player();

        final String message;
        if (newLobbyPlayer == null) {
            message = localizer.getMessage("lblLogPlayerHasRestoredControlThemself", p.getName());
        } else {
            if (newLobbyPlayer.getName().equals(p.getName())) return null;
            message = localizer.getMessage("lblLogPlayerControlledTargetPlayer", p.getName(), newLobbyPlayer.getName());
        }
        return new GameLogEntry(GameLogEntryType.PLAYER_CONTROL, message);
    }

    @Override
    public GameLogEntry visit(GameEventTurnPhase ev) {
        Player p = ev.playerTurn();
        String phaseMessage = ev.phaseDesc() + Lang.getInstance().getPossessedObject(p.getName(), ev.phase().nameForUi);

        // After untap phase, log land count and available mana
        if (ev.phase() == forge.game.phase.PhaseType.UNTAP && !ev.phaseDesc().equals("Repeat")) {
            int landCount = countLands(p);
            int availableMana = calculateAvailableMana(p);

            String resourceMessage = String.format("%s has %d land%s and %d available mana after untap.",
                p.getName(), landCount, landCount == 1 ? "" : "s", availableMana);

            log.add(GameLogEntryType.PHASE, resourceMessage);
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
            additionalLog = localizer.getMessage("lblRemovingNLoyaltyCounter", String.valueOf(event.amount()));
        }
        String message = localizer.getMessage("lblSourceDealsNDamageToDest", event.source().toString(), String.valueOf(event.amount()), additionalLog.isEmpty() ? "" : " (" + additionalLog + ")", event.card().toString());
        return new GameLogEntry(GameLogEntryType.DAMAGE, message);
    }

    /* (non-Javadoc)
     * @see forge.game.event.IGameEventVisitor.Base#visit(forge.game.event.GameEventLandPlayed)
     */
    @Override
    public GameLogEntry visit(GameEventLandPlayed ev) {
        String message = localizer.getMessage("lblLogPlayerPlayedLand", ev.player().toString(), ev.land().toString());
        return new GameLogEntry(GameLogEntryType.LAND, message);
    }

    @Override
    public GameLogEntry visit(GameEventTurnBegan event) {
        // Capture board state at turn start for delta calculation
        Player turnPlayer = event.turnOwner();
        if (turnPlayer != null && turnPlayer.getGame() != null) {
            captureBoardState(turnPlayer.getGame());
            turnZoneChanges.clear(); // Clear zone changes from previous turn
        }

        String message = localizer.getMessage("lblLogTurnNOwnerByPlayer", String.valueOf(event.turnNumber()), event.turnOwner().toString());
        return new GameLogEntry(GameLogEntryType.TURN, message);
    }

    @Override
    public GameLogEntry visit(GameEventPlayerDamaged ev) {
        String extra = ev.infect() ? localizer.getMessage("lblLogAsPoisonCounters") : "";
        String damageType = ev.combat() ? localizer.getMessage("lblCombat") : localizer.getMessage("lblNonCombat");
        String message = localizer.getMessage("lblLogSourceDealsNDamageOfTypeToDest", ev.source().toString(),
                            String.valueOf(ev.amount()), damageType, ev.target().toString(), extra);
        return new GameLogEntry(GameLogEntryType.DAMAGE, message);
    }

    @Override
    public GameLogEntry visit(GameEventPlayerPoisoned ev) {
        String message = localizer.getMessage("lblLogPlayerReceivesNPosionCounterFrom",
                            ev.receiver().toString(), String.valueOf(ev.amount()), ev.source().toString());
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
        for (GameEntity k : ev.attackersMap().keySet()) {
            Collection<Card> attackers = ev.attackersMap().get(k);
            if (attackers == null || attackers.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(localizer.getMessage("lblLogPlayerAssignedAttackerToAttackTarget", ev.player(), Lang.joinHomogenous(attackers), k));
        }
        if (sb.length() == 0) return null;
        return new GameLogEntry(GameLogEntryType.COMBAT, sb.toString());
    }

    @Override
    public GameLogEntry visit(final GameEventBlockersDeclared ev) {
        final StringBuilder sb = new StringBuilder();

        // Loop through Defenders
        // Append Defending Player/Planeswalker

        Collection<Card> blockers = null;

        for (Entry<GameEntity, MapOfLists<Card, Card>> kv : ev.blockers().entrySet()) {
            GameEntity defender = kv.getKey();
            MapOfLists<Card, Card> attackers = kv.getValue();
            if (attackers == null || attackers.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }

            String controllerName;
            if (defender instanceof Card c) {
                controllerName = c.isBattle() ? c.getProtectingPlayer().getName() : c.getController().getName();
            } else {
                controllerName = defender.getName();
            }

            boolean firstAttacker = true;
            for (final Entry<Card, Collection<Card>> att : attackers.entrySet()) {
                if (!firstAttacker) sb.append("\n");

                blockers = att.getValue();
                if (blockers.isEmpty()) {
                    sb.append(localizer.getMessage("lblLogPlayerDidntBlockAttacker", controllerName, att.getKey()));
                } else {
                    sb.append(localizer.getMessage("lblLogPlayerAssignedBlockerToBlockAttacker", controllerName, Lang.joinHomogenous(blockers), att.getKey()));
                }
                firstAttacker = false;
            }
        }

        return new GameLogEntry(GameLogEntryType.COMBAT, sb.toString());
    }

    @Override
    public GameLogEntry visit(GameEventMulligan ev) {
        String message = localizer.getMessage("lblPlayerHasMulliganedDownToNCards").replace("%d", String.valueOf(ev.player().getZone(ZoneType.Hand).size())).replace("%s", ev.player().toString());
        return new GameLogEntry(GameLogEntryType.MULLIGAN, message);
    }

    @Override
    public GameLogEntry visit(GameEventCardForetold ev) {
        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, ev.toString());
    }

    @Override
    public GameLogEntry visit(GameEventCardPlotted ev) {
        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, ev.toString());
    }

    @Override
    public GameLogEntry visit(GameEventDoorChanged ev) {
        return new GameLogEntry(GameLogEntryType.STACK_RESOLVE, ev.toString());
    }

    @Override
    public GameLogEntry visit(GameEventCardChangeZone ev) {
        Card card = ev.card();
        Zone from = ev.from();
        Zone to = ev.to();

        if (card == null || from == null || to == null) {
            return null;
        }

        ZoneType fromZone = from.getZoneType();
        ZoneType toZone = to.getZoneType();

        // Build detailed zone change message for ANALYSIS level
        String cardName = card.getName();
        String ownerName = card.getOwner() != null ? card.getOwner().getName() : "Unknown";
        String message = String.format("%s: %s moved from %s to %s",
            ownerName, cardName, fromZone.toString(), toZone.toString());

        // Track zone changes for turn summary
        turnZoneChanges.add(message);

        return new GameLogEntry(GameLogEntryType.ANALYSIS, message);
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
                for (forge.game.spellability.SpellAbility ma : card.getManaAbilities()) {
                    if (ma.canPlay()) {
                        // Count the number of mana symbols this ability produces
                        String produced = ma.getParamOrDefault("Produced", "");
                        if (!produced.isEmpty()) {
                            // Split by space to count individual mana symbols
                            String[] manaSymbols = produced.split(" ");
                            int producedAmount = ma.hasParam("Amount")
                                ? forge.game.ability.AbilityUtils.calculateAmount(card, ma.getParam("Amount"), ma)
                                : 1;
                            availableMana += manaSymbols.length * producedAmount;
                            break; // Only count one ability per card (the best one)
                        }
                    }
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