package forge.ai;

import forge.game.Game;
import forge.game.GameLogEntryType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetChoices;

import java.util.List;

/**
 * Logs AI decisions to the game log for debugging and analysis.
 * This helps understand why the AI chose to play certain cards or abilities
 * and why it selected specific targets.
 */
public class AiDecisionLogger {

    private static boolean enabled = true;

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Logs why the AI decided to play a spell or ability.
     *
     * @param ai The AI player
     * @param sa The spell ability being played
     * @param decision The decision reason
     */
    public static void logDecision(Player ai, SpellAbility sa, AiPlayDecision decision) {
        logDecisionWithAlternatives(ai, sa, decision, null);
    }

    /**
     * Logs why the AI decided to play a spell or ability, including alternative options.
     *
     * @param ai The AI player
     * @param sa The spell ability being played
     * @param decision The decision reason
     * @param alternatives Top alternative options the AI could have chosen (up to 3 will be logged)
     */
    public static void logDecisionWithAlternatives(Player ai, SpellAbility sa, AiPlayDecision decision, List<SpellAbility> alternatives) {
        if (!enabled || ai == null || sa == null) {
            return;
        }

        Game game = ai.getGame();
        if (game == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[AI] ").append(ai.getName()).append(" decides to play: ");

        Card host = sa.getHostCard();
        if (host != null) {
            sb.append(host.getName());
        } else {
            sb.append(sa.toString());
        }

        // Add the reason
        sb.append(" | Reason: ").append(getDecisionDescription(decision));

        // Add API type if available
        if (sa.getApi() != null) {
            sb.append(" | Type: ").append(sa.getApi().name());
        }

        // Add targeting information
        String targetInfo = getTargetInfo(sa);
        if (!targetInfo.isEmpty()) {
            sb.append(" | ").append(targetInfo);
        }

        game.getGameLog().add(GameLogEntryType.AI_DECISION, sb.toString());

        // Log alternatives if available
        if (alternatives != null && !alternatives.isEmpty()) {
            StringBuilder altSb = new StringBuilder();
            altSb.append("[AI] ").append(ai.getName()).append(" other options considered: ");

            int count = 0;
            for (SpellAbility altSa : alternatives) {
                if (count >= 3) break; // Only log top 3 alternatives
                if (altSa == sa) continue; // Skip the chosen one

                if (count > 0) altSb.append(", ");

                Card altHost = altSa.getHostCard();
                if (altHost != null) {
                    altSb.append(altHost.getName());
                } else {
                    altSb.append(altSa.toString());
                }

                // Add brief info about the alternative
                if (altSa.getApi() != null) {
                    altSb.append(" (").append(altSa.getApi().name()).append(")");
                }

                count++;
            }

            if (count > 0) {
                game.getGameLog().add(GameLogEntryType.AI_DECISION, altSb.toString());
            }
        }
    }

    /**
     * Logs why the AI chose specific targets.
     *
     * @param ai The AI player
     * @param sa The spell ability with targets
     * @param targetReason Optional reason for target selection
     */
    public static void logTargeting(Player ai, SpellAbility sa, String targetReason) {
        if (!enabled || ai == null || sa == null) {
            return;
        }

        Game game = ai.getGame();
        if (game == null) {
            return;
        }

        TargetChoices targets = sa.getTargets();
        if (targets == null || targets.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[AI] ").append(ai.getName()).append(" targeting for ");

        Card host = sa.getHostCard();
        if (host != null) {
            sb.append(host.getName());
        }

        sb.append(": ");
        sb.append(getTargetInfo(sa));

        if (targetReason != null && !targetReason.isEmpty()) {
            sb.append(" | Reason: ").append(targetReason);
        }

        game.getGameLog().add(GameLogEntryType.AI_DECISION, sb.toString());
    }

    /**
     * Logs why the AI decided NOT to play something.
     *
     * @param ai The AI player
     * @param sa The spell ability not being played
     * @param decision The decision reason
     */
    public static void logSkipDecision(Player ai, SpellAbility sa, AiPlayDecision decision) {
        if (!enabled || ai == null || sa == null) {
            return;
        }

        // Only log interesting skip reasons, not every evaluation
        if (!isInterestingSkipReason(decision)) {
            return;
        }

        Game game = ai.getGame();
        if (game == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[AI] ").append(ai.getName()).append(" considers but skips: ");

        Card host = sa.getHostCard();
        if (host != null) {
            sb.append(host.getName());
        } else {
            sb.append(sa.toString());
        }

        sb.append(" | Reason: ").append(getDecisionDescription(decision));

        game.getGameLog().add(GameLogEntryType.AI_DECISION, sb.toString());
    }

    /**
     * Logs combat decisions (attacking/blocking).
     */
    public static void logCombatDecision(Player ai, String decision) {
        if (!enabled || ai == null) {
            return;
        }

        Game game = ai.getGame();
        if (game == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[AI] ").append(ai.getName()).append(" combat: ").append(decision);

        game.getGameLog().add(GameLogEntryType.AI_DECISION, sb.toString());
    }

    /**
     * Gets a human-readable description of the AI's decision.
     */
    private static String getDecisionDescription(AiPlayDecision decision) {
        return switch (decision) {
            case WillPlay -> "Best available play";
            case MandatoryPlay -> "Mandatory (forced to play)";
            case PlayToEmptyHand -> "Playing to empty hand";
            case ImpactCombat -> "Will impact combat favorably";
            case ResponseToStackResolve -> "Responding to stack";
            case AddBoardPresence -> "Adding board presence";
            case Removal -> "Removing a threat";
            case Tempo -> "Tempo advantage";
            case CardAdvantage -> "Card advantage";
            case WaitForCombat -> "Waiting for combat phase";
            case WaitForMain2 -> "Waiting for Main Phase 2";
            case WaitForEndOfTurn -> "Waiting for end of turn";
            case StackNotEmpty -> "Stack not empty, waiting";
            case AnotherTime -> "Better timing available later";
            case CantPlaySa -> "Cannot legally play";
            case CantPlayAi -> "AI logic prevents play";
            case CantAfford -> "Cannot afford mana cost";
            case CantAffordX -> "Cannot afford X cost";
            case DoesntImpactCombat -> "Would not impact combat";
            case DoesntImpactGame -> "Would not impact game state";
            case MissingLogic -> "No AI logic implemented";
            case MissingNeededCards -> "Missing required cards";
            case TimingRestrictions -> "Timing restrictions not met";
            case MissingPhaseRestrictions -> "Wrong phase";
            case ConditionsNotMet -> "Conditions not met";
            case NeedsToPlayCriteriaNotMet -> "Play criteria not met";
            case StopRunawayActivations -> "Preventing infinite loop";
            case TargetingFailed -> "No valid targets";
            case CostNotAcceptable -> "Cost too high";
            case LifeInDanger -> "Life in danger, being defensive";
            case WouldDestroyLegend -> "Would destroy own legend";
            case WouldBecomeZeroToughnessCreature -> "Would create 0-toughness creature";
            case WouldDestroyWorldEnchantment -> "Would destroy world enchantment";
            case BadEtbEffects -> "Bad enter-the-battlefield effects";
            case CurseEffects -> "Would be cursed/negative effects";
        };
    }

    /**
     * Builds a string describing all targets of a spell ability.
     */
    private static String getTargetInfo(SpellAbility sa) {
        if (sa == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        TargetChoices targets = sa.getTargets();

        if (targets != null && !targets.isEmpty()) {
            sb.append("Targets: ");

            // Target cards
            if (!targets.getTargetCards().isEmpty()) {
                sb.append("Cards[");
                boolean first = true;
                for (Card c : targets.getTargetCards()) {
                    if (!first) sb.append(", ");
                    sb.append(c.getName());
                    if (c.getController() != null) {
                        sb.append(" (").append(c.getController().getName()).append(")");
                    }
                    first = false;
                }
                sb.append("] ");
            }

            // Target players
            Iterable<Player> targetPlayers = targets.getTargetPlayers();
            if (targetPlayers.iterator().hasNext()) {
                sb.append("Players[");
                boolean first = true;
                for (Player p : targetPlayers) {
                    if (!first) sb.append(", ");
                    sb.append(p.getName());
                    first = false;
                }
                sb.append("] ");
            }

            // Target SAs (for counterspells etc.)
            Iterable<SpellAbility> targetSpells = targets.getTargetSpells();
            if (targetSpells.iterator().hasNext()) {
                sb.append("Spells[");
                boolean first = true;
                for (SpellAbility targetSa : targetSpells) {
                    if (!first) sb.append(", ");
                    if (targetSa.getHostCard() != null) {
                        sb.append(targetSa.getHostCard().getName());
                    }
                    first = false;
                }
                sb.append("] ");
            }
        }

        // Check sub-abilities for additional targets
        SpellAbility sub = sa.getSubAbility();
        if (sub != null) {
            String subTargets = getTargetInfo(sub);
            if (!subTargets.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("-> Sub: ");
                }
                sb.append(subTargets);
            }
        }

        return sb.toString().trim();
    }

    /**
     * Determines if a skip reason is interesting enough to log.
     * We don't want to spam the log with every evaluation.
     */
    private static boolean isInterestingSkipReason(AiPlayDecision decision) {
        return switch (decision) {
            // These are interesting to understand AI behavior
            case WaitForCombat, WaitForMain2, WaitForEndOfTurn,
                 LifeInDanger, TargetingFailed, CostNotAcceptable,
                 BadEtbEffects, CurseEffects -> true;
            // These are routine/expected and would spam the log
            default -> false;
        };
    }
}

