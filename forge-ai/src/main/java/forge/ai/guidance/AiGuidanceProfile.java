package forge.ai.guidance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed {@code ai_guidance} policy
 * (mtg-replay-notation/spec/ai-play-guidance-spec.md, §3–§4).
 *
 * <p>Lives entirely in {@code forge-ai} and is never attached to {@link forge.deck.Deck} or
 * {@link forge.deck.DeckRulesConfig} (forge-core), which stay Gson-free by design — the same
 * layering constraint that already keeps {@code DeckRulesConfig} JSON-parsing logic out of
 * forge-core and inside {@code forge.ai.DeckRulesLoader}. An {@link forge.ai.AiController}
 * instance holds its own {@code AiGuidanceProfile} exactly the way it already holds a
 * {@code ComboTracker} — see {@code AiController.initGuidanceProfile(Deck)}. Details and the
 * gaps this slice deliberately leaves open: forge-integration-guide.md §12.6.</p>
 *
 * <p><b>Schema note:</b> the spec's own worked examples disagree about where a deployment guard
 * attaches — forge-integration-guide.md §12.3 embeds it per-card
 * ({@code tactical_roles.<card>.deployment_guard}), while ai-play-guidance-spec.md §4.3 declares
 * it per-<i>role</i> ({@code role_bindings.deployment_constraints[].applies_to_role}). This class
 * implements the latter (it generalizes — one constraint covers every card sharing that role
 * instead of duplicating the same guard JSON onto each one) and does not read the former shape at
 * all. See forge-integration-guide.md §12.6.1.</p>
 */
public final class AiGuidanceProfile {

    private final Map<String, CardRoleBinding> cardRoles = new LinkedHashMap<>();
    private final Map<String, JsonObject> deploymentConstraintsByRole = new LinkedHashMap<>();

    private AiGuidanceProfile() { }

    public static AiGuidanceProfile parse(JsonObject aiGuidanceRoot) {
        AiGuidanceProfile profile = new AiGuidanceProfile();
        if (aiGuidanceRoot == null) {
            return profile;
        }
        if (!aiGuidanceRoot.has("role_bindings") || !aiGuidanceRoot.get("role_bindings").isJsonObject()) {
            return profile;
        }
        JsonObject roleBindings = aiGuidanceRoot.getAsJsonObject("role_bindings");

        if (roleBindings.has("cards") && roleBindings.get("cards").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : roleBindings.getAsJsonObject("cards").entrySet()) {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject cardObj = e.getValue().getAsJsonObject();
                String primaryRole = cardObj.has("primary_role") ? cardObj.get("primary_role").getAsString() : null;
                profile.cardRoles.put(e.getKey(), new CardRoleBinding(primaryRole));
            }
        }

        if (roleBindings.has("deployment_constraints") && roleBindings.get("deployment_constraints").isJsonArray()) {
            for (JsonElement el : roleBindings.getAsJsonArray("deployment_constraints")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject dc = el.getAsJsonObject();
                if (!dc.has("applies_to_role") || !dc.has("condition") || !dc.get("condition").isJsonObject()) {
                    continue;
                }
                // "on_fail":"hold" is the only documented behavior (spec §4.3) - there is nothing
                // else to branch on today, so it is read but intentionally not stored.
                profile.deploymentConstraintsByRole.put(
                        dc.get("applies_to_role").getAsString(),
                        dc.getAsJsonObject("condition"));
            }
        }
        return profile;
    }

    public CardRoleBinding getRoleBinding(String cardName) {
        return cardRoles.get(cardName);
    }

    /** Declared {@code primary_role} for a card name, or {@code null} if it has none. Package-visible: only {@link PredicateEvaluator} needs this to resolve {@code battlefield.roles}/{@code hand.roles}/{@code active_engine_core_count}. */
    String roleOf(String cardName) {
        CardRoleBinding binding = cardRoles.get(cardName);
        return binding == null ? null : binding.getPrimaryRole();
    }

    /**
     * True if {@code hostCard}'s declared role (if any) has no deployment constraint, or has one
     * that currently evaluates to true. False only when a constraint is declared for this card's
     * role and it currently fails — i.e. this priority is not a legal moment to deploy it.
     */
    public boolean passesDeploymentGuard(Card hostCard, Player aiPlayer, Game game) {
        if (hostCard == null) {
            return true;
        }
        String role = roleOf(hostCard.getName());
        if (role == null) {
            return true;
        }
        JsonObject guard = deploymentConstraintsByRole.get(role);
        if (guard == null) {
            return true;
        }
        return PredicateEvaluator.evaluate(guard, this, aiPlayer, game, null);
    }

    public boolean isEmpty() {
        return cardRoles.isEmpty() && deploymentConstraintsByRole.isEmpty();
    }

    public int cardRoleCount() {
        return cardRoles.size();
    }

    public int deploymentConstraintCount() {
        return deploymentConstraintsByRole.size();
    }
}
