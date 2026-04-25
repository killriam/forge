package forge.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Detects notable moments and blunders from a list of turn snapshots.
 *
 * Phase 1 detectors (event-log based, no card DB required):
 *  - Heavy damage taken in a single turn
 *  - Significant board-presence swing against the human player
 *  - Possible missed lethal (creature count × 2 ≥ opponent life)
 *
 * More precise detectors (unfavorable blocks, spell sequencing, etc.) are planned
 * for Phase 3 when full P/T and mana-cost data are available.
 */
public final class BlunderDetector {

    private BlunderDetector() { }

    /**
     * Analyse all turns and return a list of detected blunders / notable events.
     *
     * @param turns        full list of turn snapshots
     * @param evaluations  corresponding evaluation list (same indices)
     * @param humanId      the player ID of the "human" player
     * @param playerNames  player ID → display name map
     */
    public static List<BlunderEntry> detect(
            List<ReplayStateReconstructor.TurnSnapshot> turns,
            List<TurnEvaluation> evaluations,
            String humanId,
            Map<String, String> playerNames) {

        List<BlunderEntry> result = new ArrayList<>();
        if (turns.isEmpty()) return result;

        String humanName = playerNames.getOrDefault(humanId, humanId);
        List<String> allPlayers = new ArrayList<>(turns.get(0).lifeTotals.keySet());
        List<String> opponents = new ArrayList<>(allPlayers);
        opponents.remove(humanId);

        for (int i = 1; i < turns.size(); i++) {
            ReplayStateReconstructor.TurnSnapshot curr = turns.get(i);
            ReplayStateReconstructor.TurnSnapshot prev = turns.get(i - 1);
            TurnEvaluation currEval = i < evaluations.size() ? evaluations.get(i) : null;
            TurnEvaluation prevEval = i - 1 < evaluations.size() ? evaluations.get(i - 1) : null;

            int turn = curr.turnNumber;

            // ---- Heavy damage: human took >= 8 damage in one turn ----
            int myLifePrev = prev.lifeTotals.getOrDefault(humanId, 20);
            int myLifeCurr = curr.lifeTotals.getOrDefault(humanId, 20);
            int damageIn   = myLifePrev - myLifeCurr;
            if (damageIn >= 8) {
                BlunderEntry.Severity sev = damageIn >= 15
                        ? BlunderEntry.Severity.CRITICAL : BlunderEntry.Severity.WARNING;
                result.add(new BlunderEntry(turn, BlunderEntry.Type.HEAVY_DAMAGE, sev,
                        humanName + " took " + damageIn + " damage  ("
                                + myLifePrev + " \u2192 " + myLifeCurr + " life)",
                        humanName));
            }

            // ---- Board swing: board-presence delta dropped significantly ----
            if (currEval != null && prevEval != null) {
                float boardDelta = currEval.boardPresence - prevEval.boardPresence;
                if (boardDelta < -0.35f) {
                    result.add(new BlunderEntry(turn, BlunderEntry.Type.BOARD_SWING,
                            BlunderEntry.Severity.WARNING,
                            "Board presence dropped significantly for " + humanName
                                    + " (change: " + String.format("%+.2f", boardDelta) + ")",
                            humanName));
                }
            }

            // ---- Possible missed lethal (on human's PREVIOUS active turn) ----
            // Check: were we the active player LAST turn, and could we have killed an opponent?
            if (prev.activePlayerId != null && prev.activePlayerId.equals(humanId)) {
                for (String opp : opponents) {
                    int oppLifePrev  = prev.lifeTotals.getOrDefault(opp, 20);
                    int myCreatures  = prev.battlefieldCounts.getOrDefault(humanId, 0);
                    // Rough estimate: 2 power per creature average
                    int roughDamage  = myCreatures * 2;
                    // Only flag if opponent survived (life > 0 now but rough damage was enough)
                    int oppLifeCurr  = curr.lifeTotals.getOrDefault(opp, 20);
                    if (roughDamage >= oppLifePrev && oppLifePrev > 0 && oppLifeCurr > 0) {
                        String oppName = playerNames.getOrDefault(opp, opp);
                        result.add(new BlunderEntry(prev.turnNumber,
                                BlunderEntry.Type.POSSIBLE_MISSED_LETHAL,
                                BlunderEntry.Severity.CRITICAL,
                                oppName + " was at " + oppLifePrev + " life. You had "
                                        + myCreatures + " creatures (est. "
                                        + roughDamage + " damage). Did you miss lethal?",
                                humanName));
                    }
                }
            }

            // ---- Card disadvantage: fell 3+ cards behind ----
            int myHandCurr  = curr.handSizes.getOrDefault(humanId, 0);
            float avgOppHand = 0f;
            if (!opponents.isEmpty()) {
                float sum = 0f;
                for (String opp : opponents) sum += curr.handSizes.getOrDefault(opp, 0);
                avgOppHand = sum / opponents.size();
            }
            if ((avgOppHand - myHandCurr) >= 3) {
                result.add(new BlunderEntry(turn, BlunderEntry.Type.BEHIND_ON_CARDS,
                        BlunderEntry.Severity.INFO,
                        humanName + " is " + Math.round(avgOppHand - myHandCurr)
                                + " cards behind opponent(s) (hand: "
                                + myHandCurr + " vs " + String.format("%.1f", avgOppHand) + ")",
                        humanName));
            }
        }

        // Deduplicate consecutive blunders of same type (keep only highest severity)
        return deduplicate(result);
    }

    /** Remove duplicate blunders of the same type on consecutive turns, keeping highest severity. */
    private static List<BlunderEntry> deduplicate(List<BlunderEntry> raw) {
        List<BlunderEntry> out = new ArrayList<>();
        BlunderEntry last = null;
        for (BlunderEntry b : raw) {
            if (last != null && last.type == b.type && b.turnNumber - last.turnNumber <= 1) {
                // Replace with the more severe entry
                if (b.severity.ordinal() > last.severity.ordinal()) {
                    out.remove(out.size() - 1);
                    out.add(b);
                    last = b;
                }
                // else: skip duplicate, keep existing
            } else {
                out.add(b);
                last = b;
            }
        }
        return Collections.unmodifiableList(out);
    }
}


