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
 * @param cardName     the card the decision is about (the card that was blocked, or the chosen/
 *                     vetoed removal target — see {@code decisionType})
 * @param decisionType one of {@code "deployment_guard_blocked"}, {@code "target_selected"},
 *                     {@code "target_all_vetoed"}, {@code "target_fallback"}
 * @param ruleId       human-facing label for which rule fired — the deployment-guard's role name,
 *                     or the matched {@code evaluation_ladder} step's {@code description}; may be
 *                     {@code null}
 * @param scoreDelta   the matched ladder step's {@code score}, if this was a
 *                     {@code target_selected} decision via the ladder; {@code null} otherwise
 * @param reason       free-text explanation (a deployment constraint's or veto's own
 *                     {@code reason} field, when the authored policy provided one); may be
 *                     {@code null}
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
                .append(' ').append(decisionType).append(' ').append(cardName);
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
