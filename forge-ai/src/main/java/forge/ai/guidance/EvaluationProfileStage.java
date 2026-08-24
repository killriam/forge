package forge.ai.guidance;

import java.util.Map;

/**
 * One entry from {@code ai_guidance.evaluation_profile.stages.<name>}
 * (mtg-replay-notation/spec/ai-play-guidance-spec.md §7.2) — a named game-stage
 * ({@code "early"}/{@code "mid"}/{@code "late"} in the spec's own example, but any name is
 * accepted) with a turn range and a weight per evaluation dimension.
 *
 * <p>Implements forge-integration-guide.md §12.11.1/§12.12's "stage modifier overlay" decision:
 * a narrow extension of the existing overlay architecture (scaling {@link TargetRankingRule
 * .LadderStep} scores that opt in via {@code dimension}), not a from-scratch parallel 10-dimension
 * evaluator. Only the dimensions an authored policy actually references need to appear in
 * {@code weights} — unreferenced dimensions/stages are simply never looked up.</p>
 */
public record EvaluationProfileStage(String name, int turnMin, int turnMax, Map<String, Double> weights) {

    /** Weight for {@code dimension} in this stage, or {@code 0.0} if this stage doesn't declare one — a neutral (no-op) scaling factor, not an error. */
    public double weightFor(String dimension) {
        Double w = weights.get(dimension);
        return w != null ? w : 0.0;
    }
}
