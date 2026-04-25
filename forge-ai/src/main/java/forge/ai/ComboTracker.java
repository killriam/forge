package forge.ai;

import forge.deck.DeckRulesConfig;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runtime tracker that monitors combo and anti-synergy status for an AI player.
 *
 * Initialized from the deck's {@link DeckRulesConfig} at game start.
 * Provides queries to determine:
 * <ul>
 *   <li>Which combos are close to assembly (for tutor/sequencing priority)</li>
 *   <li>Which anti-synergies would be triggered by playing a given card</li>
 *   <li>Which anti-synergies are currently active on the battlefield</li>
 * </ul>
 */
public class ComboTracker {

    private static final Logger LOG = LoggerFactory.getLogger(ComboTracker.class);

    private final List<DeckRulesConfig.ComboDeclaration> combos;
    private final List<DeckRulesConfig.AntiSynergy> antiSynergies;

    public ComboTracker(DeckRulesConfig config) {
        this.combos = config != null && config.hasCombos()
                ? config.getCombos() : Collections.emptyList();
        this.antiSynergies = config != null && config.hasDontCombos()
                ? config.getDontCombos() : Collections.emptyList();
    }

    /** @return true if there is at least one combo or anti-synergy to track. */
    public boolean isActive() {
        return !combos.isEmpty() || !antiSynergies.isEmpty();
    }

    // ========================================================================
    // Combo Readiness
    // ========================================================================

    /**
     * Describes the status of a single combo for the current game state.
     */
    public static class ComboStatus {
        public final DeckRulesConfig.ComboDeclaration combo;
        /** Pieces already on the battlefield or in the command zone. */
        public final Set<String> piecesOnBoard;
        /** Pieces in the player's hand. */
        public final Set<String> piecesInHand;
        /** Pieces still missing (not on board and not in hand). */
        public final Set<String> piecesMissing;
        /** 0.0 = nothing, 1.0 = all pieces assembled on board. */
        public final double readiness;

        public ComboStatus(DeckRulesConfig.ComboDeclaration combo,
                           Set<String> onBoard, Set<String> inHand, Set<String> missing) {
            this.combo = combo;
            this.piecesOnBoard = onBoard;
            this.piecesInHand = inHand;
            this.piecesMissing = missing;

            int total = combo.getPieces().size();
            if (total == 0) {
                this.readiness = 0.0;
            } else {
                // Board pieces count full, hand pieces count half
                this.readiness = Math.min(1.0,
                        (onBoard.size() + inHand.size() * 0.5) / total);
            }
        }

        public boolean isAssembled() { return piecesMissing.isEmpty() && piecesInHand.isEmpty(); }
        public boolean isOneAway() { return piecesMissing.isEmpty() && piecesInHand.size() == 1; }
    }

    /**
     * Evaluate the status of all declared combos for the given AI player.
     */
    public List<ComboStatus> evaluateCombos(Player ai) {
        if (combos.isEmpty()) return Collections.emptyList();

        Set<String> boardNames = getCardNames(ai, ZoneType.Battlefield);
        boardNames.addAll(getCardNames(ai, ZoneType.Command));
        Set<String> handNames = getCardNames(ai, ZoneType.Hand);

        List<ComboStatus> results = new ArrayList<>();
        for (DeckRulesConfig.ComboDeclaration combo : combos) {
            Set<String> onBoard = new HashSet<>();
            Set<String> inHand = new HashSet<>();
            Set<String> missing = new HashSet<>();

            for (String piece : combo.getPieces()) {
                if (boardNames.contains(piece)) {
                    onBoard.add(piece);
                } else if (handNames.contains(piece)) {
                    inHand.add(piece);
                } else {
                    missing.add(piece);
                }
            }
            results.add(new ComboStatus(combo, onBoard, inHand, missing));
        }
        return results;
    }

    /**
     * Get the names of combo pieces that are currently missing from the board and hand.
     * Useful for prioritizing tutor targets.
     */
    public Set<String> getMissingComboPieces(Player ai) {
        Set<String> missing = new HashSet<>();
        for (ComboStatus status : evaluateCombos(ai)) {
            missing.addAll(status.piecesMissing);
        }
        return missing;
    }

    /**
     * Get the best tutor target from declared combos.
     * Prefers pieces from combos that are closest to assembly.
     *
     * @return card name to tutor for, or null if no combo guidance available
     */
    public String getBestTutorTarget(Player ai) {
        List<ComboStatus> statuses = evaluateCombos(ai);
        if (statuses.isEmpty()) return null;

        // Sort by readiness descending — prefer combos closest to completion
        statuses.sort((a, b) -> Double.compare(b.readiness, a.readiness));

        for (ComboStatus status : statuses) {
            // Prefer combos that are one piece away (in hand or missing exactly 1)
            if (status.piecesMissing.size() == 1) {
                return status.piecesMissing.iterator().next();
            }
        }

        // Fallback: return any missing piece from the best combo
        for (ComboStatus status : statuses) {
            if (!status.piecesMissing.isEmpty()) {
                return status.piecesMissing.iterator().next();
            }
        }

        return null;
    }

    // ========================================================================
    // Anti-Synergy Detection
    // ========================================================================

    /**
     * Check which anti-synergies would be activated if the given card is played.
     *
     * @param ai       the AI player
     * @param cardName the name of the card about to be played
     * @return list of anti-synergies that would become active
     */
    public List<DeckRulesConfig.AntiSynergy> checkAntiSynergies(Player ai, String cardName) {
        if (antiSynergies.isEmpty()) return Collections.emptyList();

        Set<String> boardNames = getCardNames(ai, ZoneType.Battlefield);

        List<DeckRulesConfig.AntiSynergy> triggered = new ArrayList<>();
        for (DeckRulesConfig.AntiSynergy as : antiSynergies) {
            if (!as.getPieces().contains(cardName)) continue;

            // Check if all OTHER pieces are already on board
            boolean allOthersPresent = true;
            for (String piece : as.getPieces()) {
                if (piece.equals(cardName)) continue;
                if (!boardNames.contains(piece)) {
                    allOthersPresent = false;
                    break;
                }
            }
            if (allOthersPresent) {
                triggered.add(as);
            }
        }
        return triggered;
    }

    /**
     * Get all anti-synergies that are currently active (all pieces on board).
     */
    public List<DeckRulesConfig.AntiSynergy> getActiveAntiSynergies(Player ai) {
        if (antiSynergies.isEmpty()) return Collections.emptyList();

        Set<String> boardNames = getCardNames(ai, ZoneType.Battlefield);

        List<DeckRulesConfig.AntiSynergy> active = new ArrayList<>();
        for (DeckRulesConfig.AntiSynergy as : antiSynergies) {
            boolean allPresent = true;
            for (String piece : as.getPieces()) {
                if (!boardNames.contains(piece)) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                active.add(as);
            }
        }
        return active;
    }

    /**
     * Compute a penalty score for playing a card based on anti-synergies it would activate.
     * Returns 0 if no anti-synergy would be triggered.
     *
     * @return negative penalty (0, -5, -15, or -30 based on severity)
     */
    public int getAntiSynergyPenalty(Player ai, String cardName) {
        List<DeckRulesConfig.AntiSynergy> triggered = checkAntiSynergies(ai, cardName);
        if (triggered.isEmpty()) return 0;

        int penalty = 0;
        for (DeckRulesConfig.AntiSynergy as : triggered) {
            switch (as.getSeverity()) {
                case MINOR:
                    penalty -= 5;
                    break;
                case MAJOR:
                    penalty -= 15;
                    break;
                case CRITICAL:
                    penalty -= 30;
                    break;
            }
        }
        LOG.debug("Anti-synergy penalty for {}: {} (triggered {} anti-synergies)",
                cardName, penalty, triggered.size());
        return penalty;
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Set<String> getCardNames(Player player, ZoneType zone) {
        Set<String> names = new HashSet<>();
        for (Card c : player.getCardsIn(zone)) {
            names.add(c.getName());
        }
        return names;
    }
}


