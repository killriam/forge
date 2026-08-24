package forge.ai.guidance;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * One entry from {@code ai_guidance.play_preferences.tactical_sequences[]}
 * (mtg-replay-notation/spec/ai-play-guidance-spec.md §6.2) — a scripted multi-priority tactical
 * line (e.g. "bait with an enabler, then commit the engine core unless the coast isn't clear").
 *
 * <p><b>Deliberately not built on {@code GameRules.forcedPlaySequence}</b> — see
 * forge-integration-guide.md §12.5.6/§12.9.1. That mechanism is a real, already-shipped, and
 * historically delicate exact-card-name short-circuit (its own design doc,
 * {@code plan-deckRulesAiIntegration.prompt.md}, scoped it deliberately to soft
 * retry-or-fall-through enforcement with no mid-sequence condition checking, and this project's
 * own history shows it needed more than one follow-up fix after shipping). A tactical sequence
 * needs per-stage {@code abort_if} re-evaluated every priority and a "prefer this role, not this
 * exact card" match — different enough in kind, not just degree, that extending the existing
 * mechanism risked the exact regression its own soft-enforcement design was built to avoid.
 * {@link TacticalSequenceTracker} is a new, independent, additive mechanism instead.</p>
 */
public final class TacticalSequence {

    /** One {@code stage_N} entry. {@code abortIf}/{@code fallback} may be {@code null} — only ai-play-guidance-spec.md §6.2's stage_2 example has them. */
    public record Stage(String targetRole, JsonObject abortIf, String fallback) { }

    private final String id;
    private final JsonObject trigger;
    private final List<Stage> stages;
    private final String reason;

    TacticalSequence(String id, JsonObject trigger, List<Stage> stages, String reason) {
        this.id = id;
        this.trigger = trigger;
        this.stages = stages;
        this.reason = reason;
    }

    public String getId() {
        return id;
    }

    public JsonObject getTrigger() {
        return trigger;
    }

    public List<Stage> getStages() {
        return stages;
    }

    public String getReason() {
        return reason;
    }
}
