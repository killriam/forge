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

    /** One entry of {@code vetoes[]}: {@code reason} is the authored policy's own explanation, surfaced in L2 decision logging (forge-integration-guide.md §12.8). May be {@code null} if the author didn't write one. */
    public record Veto(JsonObject condition, String reason) { }

    /**
     * One step of {@code evaluation_ladder[]}: first matching condition (in list order) wins.
     * {@code description} is surfaced in L2 decision logging the same way {@link Veto#reason} is.
     * {@code dimension} is optional (may be {@code null}) — when present, it names one of
     * ai-play-guidance-spec.md §7.1's 10 evaluation dimensions, and {@code score} is scaled by the
     * current {@code evaluation_profile} stage's weight for that dimension before comparison
     * (forge-integration-guide.md §12.11.1/§12.12's "stage modifier overlay" decision). A step
     * with no {@code dimension} is unaffected by stage — the field is opt-in per step, not
     * automatic for every ladder.
     */
    public record LadderStep(JsonObject condition, int score, String description, String dimension) { }

    private final String sourceCard;
    private final List<Veto> vetoes;
    private final List<LadderStep> ladder;

    TargetRankingRule(String sourceCard, List<Veto> vetoes, List<LadderStep> ladder) {
        this.sourceCard = sourceCard;
        this.vetoes = vetoes;
        this.ladder = ladder;
    }

    public String getSourceCard() {
        return sourceCard;
    }

    public List<Veto> getVetoes() {
        return vetoes;
    }

    public List<LadderStep> getLadder() {
        return ladder;
    }
}
