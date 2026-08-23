package forge.ai.guidance;

/**
 * One entry from {@code ai_guidance.role_bindings.cards.<name>}
 * (mtg-replay-notation/spec/ai-play-guidance-spec.md §4.3).
 *
 * <p>Ability-level role granularity ({@code abilities.<n>.role}, spec §4.2/§4.3) is not
 * represented here yet — only {@code primary_role} is consumed by the slice-1 deployment-guard
 * hook. See forge-integration-guide.md §12.6 for what's deferred.</p>
 */
public final class CardRoleBinding {

    private final String primaryRole;

    public CardRoleBinding(String primaryRole) {
        this.primaryRole = primaryRole;
    }

    public String getPrimaryRole() {
        return primaryRole;
    }
}
