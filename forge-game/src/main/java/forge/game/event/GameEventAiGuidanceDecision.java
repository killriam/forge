package forge.game.event;

/**
 * Fired when a declarative {@code ai_guidance} policy (mtg-replay-notation/spec/
 * ai-play-guidance-spec.md) changes an AI decision — a deployment guard held a card back, or a
 * {@code target_rankings} rule vetoed/selected a removal target. Deliberately primitive-typed
 * only (no {@code forge.ai.guidance} types) so this class can live in forge-game, which must stay
 * free of forge-ai types — the same layering discipline documented on
 * {@code forge.ai.guidance.AiGuidanceProfile}'s own class javadoc and enforced throughout
 * forge-integration-guide.md §12.6/§12.7 (e.g. why {@code GameRules.forcedPlaySequence} is a bare
 * {@code Map<String, List<String>>} rather than a richer forge-ai-shaped object).
 *
 * @param playerName   the AI seat this decision belongs to
 * @param cardName     the card the decision is about (the card that was blocked, the chosen/
 *                     vetoed removal target, or the card whose cast advanced a tactical sequence
 *                     — see {@code decisionType}); {@code null} for the two
 *                     {@code tactical_sequence_*} types that aren't about one specific card
 *                     ({@code started}, {@code aborted})
 * @param decisionType one of {@code "deployment_guard_blocked"}, {@code "target_selected"},
 *                     {@code "target_all_vetoed"}, {@code "target_fallback"},
 *                     {@code "tactical_sequence_started"}, {@code "tactical_sequence_aborted"},
 *                     {@code "tactical_sequence_stage_advanced"}, {@code "tactical_sequence_completed"}
 * @param ruleId       human-facing label for which rule fired — the deployment-guard's role name,
 *                     the matched {@code evaluation_ladder} step's {@code description}, or the
 *                     {@code tactical_sequences[].id} for the sequence types; may be {@code null}
 * @param scoreDelta   the matched ladder step's {@code score}, if this was a
 *                     {@code target_selected} decision via the ladder; {@code null} otherwise
 * @param reason       free-text explanation (a deployment constraint's, veto's, or sequence's own
 *                     {@code reason} field, when the authored policy provided one, or a synthesized
 *                     explanation for the {@code tactical_sequence_*} types); may be {@code null}
 */
public record GameEventAiGuidanceDecision(String playerName, String cardName, String decisionType,
        String ruleId, Integer scoreDelta, String reason) implements GameEvent {

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[ai_guidance] ").append(playerName)
                .append(' ').append(decisionType);
        if (cardName != null) {
            sb.append(' ').append(cardName);
        }
        if (ruleId != null) {
            sb.append(" (").append(ruleId);
            if (scoreDelta != null) {
                sb.append(" +").append(scoreDelta);
            }
            sb.append(')');
        }
        if (reason != null) {
            sb.append(" - ").append(reason);
        }
        return sb.toString();
    }
}
