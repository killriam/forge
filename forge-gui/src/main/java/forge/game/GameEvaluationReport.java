package forge.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the complete evaluation of a single game:
 * <ul>
 *   <li>Per-turn {@link TurnEvaluation} objects (one per turn snapshot)</li>
 *   <li>Game-level aggregate statistics (spell velocity, draw rate, land drop rate)</li>
 *   <li>The detected critical turn number</li>
 *   <li>{@link BlunderEntry} list of notable moments / mistakes</li>
 * </ul>
 *
 * Constructed via {@link #build(List, Map, String)}.
 */
public class GameEvaluationReport {

    public final List<TurnEvaluation> turnEvaluations;
    public final List<BlunderEntry> blunders;

    /** Average spells cast per turn, per player ID. */
    public final Map<String, Float> spellVelocity;
    /** Average cards drawn per turn (excluding opening draw), per player ID. */
    public final Map<String, Float> cardDrawEfficiency;
    /** Fraction of turns where the player played a land, per player ID. */
    public final Map<String, Float> landDropRate;

    /** 1-based turn number of the most critical turn (highest life swing + board change). 0 = none. */
    public final int criticalTurn;
    /** The player ID treated as "human" / reference player. */
    public final String humanPlayerId;

    // Constructor (package-private; use build())
    GameEvaluationReport(List<TurnEvaluation> turnEvaluations,
                         List<BlunderEntry> blunders,
                         Map<String, Float> spellVelocity,
                         Map<String, Float> cardDrawEfficiency,
                         Map<String, Float> landDropRate,
                         int criticalTurn,
                         String humanPlayerId) {
        this.turnEvaluations    = Collections.unmodifiableList(new ArrayList<>(turnEvaluations));
        this.blunders           = Collections.unmodifiableList(new ArrayList<>(blunders));
        this.spellVelocity      = Collections.unmodifiableMap(new LinkedHashMap<>(spellVelocity));
        this.cardDrawEfficiency = Collections.unmodifiableMap(new LinkedHashMap<>(cardDrawEfficiency));
        this.landDropRate       = Collections.unmodifiableMap(new LinkedHashMap<>(landDropRate));
        this.criticalTurn       = criticalTurn;
        this.humanPlayerId      = humanPlayerId;
    }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Build a complete evaluation report from a list of turn snapshots.
     * This method may take a moment (card DB lookups are cached after first call).
     */
    public static GameEvaluationReport build(
            List<ReplayStateReconstructor.TurnSnapshot> turns,
            Map<String, String> playerNames,
            String humanPlayerId) {

        if (turns == null || turns.isEmpty()) {
            return new GameEvaluationReport(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    0, humanPlayerId);
        }

        // --- Per-turn evaluations ---
        List<TurnEvaluation> evaluations = new ArrayList<>();
        for (ReplayStateReconstructor.TurnSnapshot turn : turns) {
            evaluations.add(TurnEvaluator.evaluate(turn, humanPlayerId, turns));
        }

        // --- Aggregate game-level stats ---
        Map<String, Integer> totalCasts         = new LinkedHashMap<>();
        Map<String, Integer> totalDraws         = new LinkedHashMap<>();
        Map<String, Integer> turnsWithLandDrop  = new LinkedHashMap<>();

        for (String pid : turns.get(0).lifeTotals.keySet()) {
            totalCasts.put(pid, 0);
            totalDraws.put(pid, 0);
            turnsWithLandDrop.put(pid, 0);
        }
        for (TurnEvaluation ev : evaluations) {
            for (Map.Entry<String, Integer> e : ev.spellsCast.entrySet()) {
                totalCasts.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            for (Map.Entry<String, Integer> e : ev.cardsDrawn.entrySet()) {
                totalDraws.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            for (Map.Entry<String, Integer> e : ev.landDrops.entrySet()) {
                if (e.getValue() > 0) {
                    turnsWithLandDrop.merge(e.getKey(), 1, Integer::sum);
                }
            }
        }

        int totalTurns = turns.size();
        Map<String, Float> spellVelocity      = new LinkedHashMap<>();
        Map<String, Float> cardDrawEfficiency  = new LinkedHashMap<>();
        Map<String, Float> landDropRate        = new LinkedHashMap<>();
        for (String pid : totalCasts.keySet()) {
            spellVelocity.put(pid, totalTurns > 0
                    ? (float) totalCasts.get(pid) / totalTurns : 0f);
            cardDrawEfficiency.put(pid, totalTurns > 0
                    ? (float) totalDraws.get(pid) / totalTurns : 0f);
            landDropRate.put(pid, totalTurns > 0
                    ? (float) turnsWithLandDrop.get(pid) / totalTurns : 0f);
        }

        // --- Critical turn: highest criticalScore ---
        int critTurn = 0;
        int maxCrit  = 0;
        for (TurnEvaluation ev : evaluations) {
            if (ev.criticalScore > maxCrit) {
                maxCrit  = ev.criticalScore;
                critTurn = ev.turnNumber;
            }
        }

        // --- Blunder detection ---
        List<BlunderEntry> blunders = BlunderDetector.detect(
                turns, evaluations, humanPlayerId, playerNames);

        return new GameEvaluationReport(evaluations, blunders,
                spellVelocity, cardDrawEfficiency, landDropRate,
                critTurn, humanPlayerId);
    }

    /** Convenience: get the TurnEvaluation for the given 0-based index; null if out of range. */
    public TurnEvaluation getEvaluationAt(int index) {
        if (index < 0 || index >= turnEvaluations.size()) return null;
        return turnEvaluations.get(index);
    }
}

