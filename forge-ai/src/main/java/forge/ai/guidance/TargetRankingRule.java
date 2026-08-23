package forge.ai.guidance;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * One entry from {@code ai_guidance.target_rankings[]}
 * (mtg-replay-notation/spec/ai-play-guidance-spec.md §5.2).
 *
 * <p>Keyed by {@code source_card} (forge-integration-guide.md §12.3's shape) rather than the
 * categorical {@code applies_to: {primary_mechanic, target_zone, target_type}} shape
 * ai-play-guidance-spec.md §5.2's own schema declares — the same kind of two-shapes-for-one-
 * concept disagreement Slice 1 hit for deployment guards (forge-integration-guide.md §12.6.1/2).
 * {@code source_card} was picked because it resolves against real Forge card names with no extra
 * mechanic-group metadata Forge doesn't have; see forge-integration-guide.md §12.7 for the
 * follow-up this leaves open.</p>
 */
public final class TargetRankingRule {

    /** One step of {@code evaluation_ladder[]}: first matching condition (in list order) wins. */
    public record LadderStep(JsonObject condition, int score) { }

    private final String sourceCard;
    private final List<JsonObject> vetoConditions;
    private final List<LadderStep> ladder;

    TargetRankingRule(String sourceCard, List<JsonObject> vetoConditions, List<LadderStep> ladder) {
        this.sourceCard = sourceCard;
        this.vetoConditions = vetoConditions;
        this.ladder = ladder;
    }

    public String getSourceCard() {
        return sourceCard;
    }

    public List<JsonObject> getVetoConditions() {
        return vetoConditions;
    }

    public List<LadderStep> getLadder() {
        return ladder;
    }
}
