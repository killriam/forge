package forge.ai.guidance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.ai.ComputerUtilCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p><b>Schema note (deployment guards):</b> the spec's own worked examples disagree about where
 * a deployment guard attaches — forge-integration-guide.md §12.3 embeds it per-card
 * ({@code tactical_roles.<card>.deployment_guard}), while ai-play-guidance-spec.md §4.3 declares
 * it per-<i>role</i> ({@code role_bindings.deployment_constraints[].applies_to_role}). This class
 * implements the latter (it generalizes — one constraint covers every card sharing that role
 * instead of duplicating the same guard JSON onto each one) and does not read the former shape at
 * all. See forge-integration-guide.md §12.6.1.</p>
 *
 * <p><b>Schema note (target rankings):</b> same kind of disagreement again for {@code
 * target_rankings[]} — ai-play-guidance-spec.md §5.2 matches rules categorically via {@code
 * applies_to: {primary_mechanic, target_zone, target_type}}, which needs mechanic-group metadata
 * Forge's card database doesn't have. This class instead keys rules by {@code source_card}
 * (forge-integration-guide.md §12.3's shape), matched against the card actually casting the
 * removal spell. See forge-integration-guide.md §12.7.</p>
 */
public final class AiGuidanceProfile {

    private final Map<String, CardRoleBinding> cardRoles = new LinkedHashMap<>();
    private final Map<String, JsonObject> deploymentConstraintsByRole = new LinkedHashMap<>();
    private final Map<String, TargetRankingRule> targetRankingsBySourceCard = new LinkedHashMap<>();
    private final Set<String> tier1Combo = new HashSet<>();
    private final Set<String> tier2Engine = new HashSet<>();
    private final Set<String> tier3Stax = new HashSet<>();

    private AiGuidanceProfile() { }

    public static AiGuidanceProfile parse(JsonObject aiGuidanceRoot) {
        AiGuidanceProfile profile = new AiGuidanceProfile();
        if (aiGuidanceRoot == null) {
            return profile;
        }

        if (aiGuidanceRoot.has("role_bindings") && aiGuidanceRoot.get("role_bindings").isJsonObject()) {
            parseRoleBindings(aiGuidanceRoot.getAsJsonObject("role_bindings"), profile);
        }
        if (aiGuidanceRoot.has("target_rankings") && aiGuidanceRoot.get("target_rankings").isJsonArray()) {
            parseTargetRankings(aiGuidanceRoot.getAsJsonArray("target_rankings"), profile);
        }
        if (aiGuidanceRoot.has("canonical_threat_catalog") && aiGuidanceRoot.get("canonical_threat_catalog").isJsonObject()) {
            parseThreatCatalog(aiGuidanceRoot.getAsJsonObject("canonical_threat_catalog"), profile);
        }
        return profile;
    }

    private static void parseRoleBindings(JsonObject roleBindings, AiGuidanceProfile profile) {
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
    }

    private static void parseTargetRankings(JsonArray targetRankings, AiGuidanceProfile profile) {
        for (JsonElement el : targetRankings) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject rule = el.getAsJsonObject();
            if (!rule.has("source_card")) {
                // The applies_to-categorical shape (ai-play-guidance-spec.md §5.2) has no
                // source_card - not matchable without mechanic-group data Forge doesn't have.
                // Skipped, not an error: see this class's own "Schema note (target rankings)".
                continue;
            }
            String sourceCard = rule.get("source_card").getAsString();

            List<JsonObject> vetoConditions = new ArrayList<>();
            if (rule.has("vetoes") && rule.get("vetoes").isJsonArray()) {
                for (JsonElement v : rule.getAsJsonArray("vetoes")) {
                    if (v.isJsonObject() && v.getAsJsonObject().has("condition")
                            && v.getAsJsonObject().get("condition").isJsonObject()) {
                        vetoConditions.add(v.getAsJsonObject().getAsJsonObject("condition"));
                    }
                }
            }

            List<TargetRankingRule.LadderStep> ladder = new ArrayList<>();
            if (rule.has("evaluation_ladder") && rule.get("evaluation_ladder").isJsonArray()) {
                for (JsonElement s : rule.getAsJsonArray("evaluation_ladder")) {
                    if (!s.isJsonObject()) {
                        continue;
                    }
                    JsonObject step = s.getAsJsonObject();
                    if (!step.has("condition") || !step.get("condition").isJsonObject() || !step.has("score")) {
                        continue;
                    }
                    ladder.add(new TargetRankingRule.LadderStep(
                            step.getAsJsonObject("condition"), step.get("score").getAsInt()));
                }
            }

            profile.targetRankingsBySourceCard.put(sourceCard,
                    new TargetRankingRule(sourceCard, vetoConditions, ladder));
        }
    }

    private static void parseThreatCatalog(JsonObject catalog, AiGuidanceProfile profile) {
        addAllNames(catalog, "tier_1_combo", profile.tier1Combo);
        addAllNames(catalog, "tier_2_engine", profile.tier2Engine);
        addAllNames(catalog, "tier_3_stax", profile.tier3Stax);
    }

    private static void addAllNames(JsonObject catalog, String key, Set<String> into) {
        if (!catalog.has(key) || !catalog.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement e : catalog.getAsJsonArray(key)) {
            into.add(e.getAsString());
        }
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

    /**
     * Canonical Threat Catalog tier for a card name (ai-play-guidance-spec.md §9.2), or
     * {@code null} if it's in none of the three declared tiers. Uses the spec's own
     * {@code tier_1_combo}/{@code tier_2_engine}/{@code tier_3_stax} naming — note
     * forge-integration-guide.md §12.3's worked example instead writes {@code "Tier1_Combo"}/
     * {@code "Tier2_EngineHub"}; the two spec documents don't agree on this naming either. Package-
     * visible: only {@link PredicateEvaluator} calls this, to resolve {@code
     * target.canonical_threat_tier}.
     */
    String canonicalThreatTierOf(String cardName) {
        if (tier1Combo.contains(cardName)) return "tier_1_combo";
        if (tier2Engine.contains(cardName)) return "tier_2_engine";
        if (tier3Stax.contains(cardName)) return "tier_3_stax";
        return null;
    }

    public boolean hasTargetRankingRule(String sourceCardName) {
        return sourceCardName != null && targetRankingsBySourceCard.containsKey(sourceCardName);
    }

    /**
     * Applies this profile's {@code target_rankings} rule for {@code sa}'s host card (if any) to
     * {@code candidates}: filters out anything matching a veto condition, then picks the
     * candidate whose first-matching {@code evaluation_ladder} step scores highest. Falls back to
     * {@link ComputerUtilCard#getWorstAI} (vanilla evaluation) among the post-veto survivors if no
     * ladder step matches any of them — vetoes always apply even when the ladder has no opinion.
     *
     * <p>Only call this after confirming {@link #hasTargetRankingRule} — the two null cases this
     * method can return (no rule vs. every candidate vetoed) are otherwise indistinguishable to a
     * caller, and those two cases need opposite fallback behavior (try vanilla selection vs. treat
     * as "no legal target," per forge-integration-guide.md §12.7).</p>
     *
     * @return the guided choice, or {@code null} if every candidate was vetoed
     */
    public Card chooseGuidedRemovalTarget(SpellAbility sa, Player aiPlayer, Game game, Iterable<Card> candidates) {
        TargetRankingRule rule = targetRankingsBySourceCard.get(sa.getHostCard().getName());

        CardCollection survivors = new CardCollection();
        for (Card c : candidates) {
            boolean vetoed = false;
            for (JsonObject vetoCondition : rule.getVetoConditions()) {
                if (PredicateEvaluator.evaluate(vetoCondition, this, aiPlayer, game, c)) {
                    vetoed = true;
                    break;
                }
            }
            if (!vetoed) {
                survivors.add(c);
            }
        }
        if (survivors.isEmpty()) {
            return null;
        }

        Card best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Card c : survivors) {
            for (TargetRankingRule.LadderStep step : rule.getLadder()) {
                if (PredicateEvaluator.evaluate(step.condition(), this, aiPlayer, game, c)) {
                    if (step.score() > bestScore) {
                        bestScore = step.score();
                        best = c;
                    }
                    break; // first-matching-step-wins per candidate, spec §5.2
                }
            }
        }
        return best != null ? best : ComputerUtilCard.getWorstAI(survivors);
    }

    public boolean isEmpty() {
        return cardRoles.isEmpty() && deploymentConstraintsByRole.isEmpty() && targetRankingsBySourceCard.isEmpty();
    }

    public int cardRoleCount() {
        return cardRoles.size();
    }

    public int deploymentConstraintCount() {
        return deploymentConstraintsByRole.size();
    }

    public int targetRankingRuleCount() {
        return targetRankingsBySourceCard.size();
    }
}
