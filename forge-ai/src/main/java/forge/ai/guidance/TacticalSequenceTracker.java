package forge.ai.guidance;

import forge.game.Game;
import forge.game.event.GameEventAiGuidanceDecision;
import forge.game.player.Player;

/**
 * Runtime progress tracker for a single {@link TacticalSequence} in flight — one instance held
 * per {@link forge.ai.AiController}, mirroring how that class already holds a {@code ComboTracker}
 * alongside its (immutable) {@link AiGuidanceProfile}. Where {@code ComboTracker} recomputes its
 * answers fresh from game state on every query, this class needs actual persisted state (which
 * sequence is active, which stage it's on) across multiple priority windows — a tactical sequence
 * spans turns, {@code ComboTracker}-style stateless re-derivation doesn't fit it. See
 * forge-integration-guide.md §12.9 for the full design and why this is a new mechanism rather
 * than an extension of {@code GameRules.forcedPlaySequence}.
 */
public final class TacticalSequenceTracker {

    private TacticalSequence activeSequence;
    private int activeStageIndex;

    /**
     * Call once per priority window (not per candidate) when {@code guidanceProfile != null}. If
     * no sequence is active, scans for one whose {@code trigger} now evaluates true and activates
     * it. If one is active, re-checks the current stage's {@code abort_if} (re-evaluated every
     * call, per ai-play-guidance-spec.md §6.2's own "abortable priority states" framing) and
     * deactivates if it now fires.
     *
     * @return the {@code target_role} the AI should currently prefer, or {@code null} if no
     *         sequence is active/relevant this priority
     */
    public String desiredRoleFor(AiGuidanceProfile profile, Player aiPlayer, Game game) {
        if (activeSequence == null) {
            for (TacticalSequence candidate : profile.getTacticalSequences()) {
                if (PredicateEvaluator.evaluate(candidate.getTrigger(), profile, aiPlayer, game, null)) {
                    activeSequence = candidate;
                    activeStageIndex = 0;
                    game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), null,
                            "tactical_sequence_started", activeSequence.getId(), null, activeSequence.getReason()));
                    break;
                }
            }
            if (activeSequence == null) {
                return null;
            }
        }

        TacticalSequence.Stage stage = activeSequence.getStages().get(activeStageIndex);
        if (stage.abortIf() != null && PredicateEvaluator.evaluate(stage.abortIf(), profile, aiPlayer, game, null)) {
            String sequenceId = activeSequence.getId();
            String fallbackReason = stage.fallback() != null
                    ? "abort_if matched; fallback=" + stage.fallback() : "abort_if matched";
            game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), null,
                    "tactical_sequence_aborted", sequenceId, null, fallbackReason));
            deactivate();
            return null;
        }
        return stage.targetRole();
    }

    /**
     * Call whenever any card is cast by {@code aiPlayer} (see {@code AiController}'s subscription
     * to {@code GameEventSpellAbilityCast} — mirrors how Slice 3's decision logging reacts to game
     * events rather than being polled). Advances to the next stage if {@code cardName}'s declared
     * role matches the currently active stage's {@code target_role}; completes (and deactivates)
     * the sequence if that was the last stage. A no-op if no sequence is active or the cast card
     * doesn't match — most casts have nothing to do with an in-flight sequence.
     */
    public void onCardCast(String cardName, AiGuidanceProfile profile, Player aiPlayer, Game game) {
        if (activeSequence == null) {
            return;
        }
        TacticalSequence.Stage stage = activeSequence.getStages().get(activeStageIndex);
        String castRole = profile.roleOf(cardName);
        if (castRole == null || !castRole.equals(stage.targetRole())) {
            return;
        }

        activeStageIndex++;
        if (activeStageIndex >= activeSequence.getStages().size()) {
            game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), cardName,
                    "tactical_sequence_completed", activeSequence.getId(), null, null));
            deactivate();
        } else {
            TacticalSequence.Stage nextStage = activeSequence.getStages().get(activeStageIndex);
            game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), cardName,
                    "tactical_sequence_stage_advanced", activeSequence.getId(), null,
                    "now on stage " + (activeStageIndex + 1) + ", targeting role \"" + nextStage.targetRole() + "\""));
        }
    }

    private void deactivate() {
        activeSequence = null;
        activeStageIndex = 0;
    }

    /** For tests/diagnostics only — the id of the in-flight sequence, or {@code null}. */
    public String getActiveSequenceId() {
        return activeSequence != null ? activeSequence.getId() : null;
    }
}
