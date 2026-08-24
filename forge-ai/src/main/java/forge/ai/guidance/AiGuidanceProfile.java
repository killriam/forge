package forge.ai.guidance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.ai.ComputerUtilCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.event.GameEventAiGuidanceDecision;
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

    /** {@code condition} + the policy author's own {@code reason} text, surfaced in L2 decision logging (§12.8). */
    private record DeploymentConstraint(JsonObject condition, String reason) { }

    private final Map<String, CardRoleBinding> cardRoles = new LinkedHashMap<>();
    private final Map<String, DeploymentConstraint> deploymentConstraintsByRole = new LinkedHashMap<>();
    private final Map<String, TargetRankingRule> targetRankingsBySourceCard = new LinkedHashMap<>();
    private final Set<String> tier1Combo = new HashSet<>();
    private final Set<String> tier2Engine = new HashSet<>();
    private final Set<String> tier3Stax = new HashSet<>();
    private final List<TacticalSequence> tacticalSequences = new ArrayList<>();
    private final List<EvaluationProfileStage> evaluationStages = new ArrayList<>();

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
        if (aiGuidanceRoot.has("play_preferences") && aiGuidanceRoot.get("play_preferences").isJsonObject()) {
            JsonObject playPreferences = aiGuidanceRoot.getAsJsonObject("play_preferences");
            if (playPreferences.has("tactical_sequences") && playPreferences.get("tactical_sequences").isJsonArray()) {
                parseTacticalSequences(playPreferences.getAsJsonArray("tactical_sequences"), profile);
            }
        }
        if (aiGuidanceRoot.has("evaluation_profile") && aiGuidanceRoot.get("evaluation_profile").isJsonObject()) {
            JsonObject evaluationProfile = aiGuidanceRoot.getAsJsonObject("evaluation_profile");
            if (evaluationProfile.has("stages") && evaluationProfile.get("stages").isJsonObject()) {
                parseEvaluationStages(evaluationProfile.getAsJsonObject("stages"), profile);
            }
        }
        return profile;
    }

    private static void parseEvaluationStages(JsonObject stages, AiGuidanceProfile profile) {
        for (Map.Entry<String, JsonElement> e : stages.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject stageObj = e.getValue().getAsJsonObject();
            if (!stageObj.has("turns") || !stageObj.get("turns").isJsonArray()
                    || !stageObj.has("weights") || !stageObj.get("weights").isJsonObject()) {
                continue;
            }
            JsonArray turns = stageObj.getAsJsonArray("turns");
            if (turns.size() != 2) {
                continue;
            }
            int turnMin = turns.get(0).getAsInt();
            int turnMax = turns.get(1).getAsInt();

            Map<String, Double> weights = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> w : stageObj.getAsJsonObject("weights").entrySet()) {
                if (w.getValue().isJsonPrimitive()) {
                    weights.put(w.getKey(), w.getValue().getAsDouble());
                }
            }
            profile.evaluationStages.add(new EvaluationProfileStage(e.getKey(), turnMin, turnMax, weights));
        }
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
                String reason = dc.has("reason") ? dc.get("reason").getAsString() : null;
                profile.deploymentConstraintsByRole.put(
                        dc.get("applies_to_role").getAsString(),
                        new DeploymentConstraint(dc.getAsJsonObject("condition"), reason));
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

            List<TargetRankingRule.Veto> vetoes = new ArrayList<>();
            if (rule.has("vetoes") && rule.get("vetoes").isJsonArray()) {
                for (JsonElement v : rule.getAsJsonArray("vetoes")) {
                    if (!v.isJsonObject()) {
                        continue;
                    }
                    JsonObject vObj = v.getAsJsonObject();
                    if (!vObj.has("condition") || !vObj.get("condition").isJsonObject()) {
                        continue;
                    }
                    String reason = vObj.has("reason") ? vObj.get("reason").getAsString() : null;
                    vetoes.add(new TargetRankingRule.Veto(vObj.getAsJsonObject("condition"), reason));
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
                    String description = step.has("description") ? step.get("description").getAsString() : null;
                    String dimension = step.has("dimension") ? step.get("dimension").getAsString() : null;
                    ladder.add(new TargetRankingRule.LadderStep(
                            step.getAsJsonObject("condition"), step.get("score").getAsInt(), description, dimension));
                }
            }

            profile.targetRankingsBySourceCard.put(sourceCard,
                    new TargetRankingRule(sourceCard, vetoes, ladder));
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

    private static void parseTacticalSequences(JsonArray sequences, AiGuidanceProfile profile) {
        for (JsonElement el : sequences) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject seq = el.getAsJsonObject();
            if (!seq.has("id") || !seq.has("trigger") || !seq.get("trigger").isJsonObject()) {
                continue;
            }
            String id = seq.get("id").getAsString();
            JsonObject trigger = seq.getAsJsonObject("trigger");
            String reason = seq.has("reason") ? seq.get("reason").getAsString() : null;

            // Stages are named stage_1, stage_2, ... (ai-play-guidance-spec.md §6.2's own worked
            // example) rather than a JSON array - scan sequentially, stop at the first gap.
            List<TacticalSequence.Stage> stages = new ArrayList<>();
            for (int i = 1; seq.has("stage_" + i); i++) {
                JsonObject stageObj = seq.getAsJsonObject("stage_" + i);
                if (!stageObj.has("target_role")) {
                    break;
                }
                String targetRole = stageObj.get("target_role").getAsString();
                JsonObject abortIf = stageObj.has("abort_if") && stageObj.get("abort_if").isJsonObject()
                        ? stageObj.getAsJsonObject("abort_if") : null;
                String fallback = stageObj.has("fallback") ? stageObj.get("fallback").getAsString() : null;
                stages.add(new TacticalSequence.Stage(targetRole, abortIf, fallback));
            }
            if (stages.isEmpty()) {
                continue;
            }

            profile.tacticalSequences.add(new TacticalSequence(id, trigger, stages, reason));
        }
    }

    public List<TacticalSequence> getTacticalSequences() {
        return tacticalSequences;
    }

    public CardRoleBinding getRoleBinding(String cardName) {
        return cardRoles.get(cardName);
    }

    /** Declared {@code primary_role} for a card name, or {@code null} if it has none. Package-visible: only {@link PredicateEvaluator} needs this to resolve {@code battlefield.roles}/{@code hand.roles}/{@code active_engine_core_count}. */
    String roleOf(String cardName) {
        CardRoleBinding binding = cardRoles.get(cardName);
        return binding == null ? null : binding.getPrimaryRole();
    }

    /** True if {@code cardName}'s declared {@code primary_role} equals {@code role}. Convenience over {@link #roleOf} for callers (e.g. {@code AiController}'s tactical-sequence hook) that just need a yes/no match. */
    public boolean cardHasRole(String cardName, String role) {
        return role != null && role.equals(roleOf(cardName));
    }

    /**
     * True if {@code hostCard}'s declared role (if any) has no deployment constraint, or has one
     * that currently evaluates to true. False only when a constraint is declared for this card's
     * role and it currently fails — i.e. this priority is not a legal moment to deploy it. Fires a
     * {@link GameEventAiGuidanceDecision} (type {@code "deployment_guard_blocked"}) whenever it
     * returns {@code false}, for L2 decision logging — see forge-integration-guide.md §12.8.
     */
    public boolean passesDeploymentGuard(Card hostCard, Player aiPlayer, Game game) {
        if (hostCard == null) {
            return true;
        }
        String role = roleOf(hostCard.getName());
        if (role == null) {
            return true;
        }
        DeploymentConstraint constraint = deploymentConstraintsByRole.get(role);
        if (constraint == null) {
            return true;
        }
        boolean passes = PredicateEvaluator.evaluate(constraint.condition(), this, aiPlayer, game, null);
        if (!passes) {
            game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), hostCard.getName(),
                    "deployment_guard_blocked", role, null, constraint.reason()));
        }
        return passes;
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

    /**
     * The declared {@code evaluation_profile.stages} entry whose {@code turns} range contains the
     * game's current turn, or {@code null} if there's no {@code evaluation_profile} at all, or the
     * current turn falls in a gap between declared stages. Package-visible: only
     * {@link PredicateEvaluator} needs this directly (to resolve {@code state.game_stage});
     * {@link #stageWeightFor} is the entry point everything else should use.
     */
    EvaluationProfileStage currentStageEntry(Game game) {
        int turn = game.getPhaseHandler().getTurn();
        for (EvaluationProfileStage stage : evaluationStages) {
            if (turn >= stage.turnMin() && turn <= stage.turnMax()) {
                return stage;
            }
        }
        return null;
    }

    /** The name of the current {@code evaluation_profile} stage (e.g. {@code "early"}/{@code "mid"}/{@code "late"}), or {@code null} — see {@link #currentStageEntry}. */
    String currentStage(Game game) {
        EvaluationProfileStage stage = currentStageEntry(game);
        return stage != null ? stage.name() : null;
    }

    /**
     * The current {@code evaluation_profile} stage's weight for {@code dimension}, or {@code 0.0}
     * (a neutral, no-op scaling factor) if there's no {@code evaluation_profile}, the current turn
     * matches no declared stage, or the matched stage doesn't declare a weight for this dimension.
     * Used by {@link #chooseGuidedRemovalTarget}/{@link #chooseGuidedCounterTarget} to scale a
     * {@link TargetRankingRule.LadderStep} that opted in via {@code dimension} — see
     * forge-integration-guide.md §12.11.1/§12.12's "stage modifier overlay" decision.
     */
    double stageWeightFor(Game game, String dimension) {
        EvaluationProfileStage stage = currentStageEntry(game);
        return stage != null ? stage.weightFor(dimension) : 0.0;
    }

    /**
     * {@code step}'s score, scaled by the current {@code evaluation_profile} stage's weight for
     * its declared {@code dimension} (formula: {@code score * (1 + weight)}) — or the unscaled
     * {@code score} unchanged if the step has no {@code dimension} (opt-in, not automatic; see
     * {@link TargetRankingRule.LadderStep}'s own javadoc).
     */
    private int scaledScore(TargetRankingRule.LadderStep step, Game game) {
        if (step.dimension() == null) {
            return step.score();
        }
        return (int) Math.round(step.score() * (1.0 + stageWeightFor(game, step.dimension())));
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
        String sourceCardName = sa.getHostCard().getName();
        TargetRankingRule rule = targetRankingsBySourceCard.get(sourceCardName);

        CardCollection survivors = new CardCollection();
        for (Card c : candidates) {
            TargetRankingRule.Veto matchedVeto = null;
            for (TargetRankingRule.Veto veto : rule.getVetoes()) {
                if (PredicateEvaluator.evaluate(veto.condition(), this, aiPlayer, game, c)) {
                    matchedVeto = veto;
                    break;
                }
            }
            if (matchedVeto == null) {
                survivors.add(c);
            }
        }
        if (survivors.isEmpty()) {
            game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), sourceCardName,
                    "target_all_vetoed", null, null,
                    "every candidate target matched a veto condition"));
            return null;
        }

        Card best = null;
        int bestScore = Integer.MIN_VALUE;
        String bestDescription = null;
        for (Card c : survivors) {
            for (TargetRankingRule.LadderStep step : rule.getLadder()) {
                if (PredicateEvaluator.evaluate(step.condition(), this, aiPlayer, game, c)) {
                    int score = scaledScore(step, game);
                    if (score > bestScore) {
                        bestScore = score;
                        best = c;
                        bestDescription = step.description();
                    }
                    break; // first-matching-step-wins per candidate, spec §5.2
                }
            }
        }
        if (best != null) {
            game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), best.getName(),
                    "target_selected", bestDescription, bestScore, null));
            return best;
        }

        Card fallback = ComputerUtilCard.getWorstAI(survivors);
        game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), fallback.getName(),
                "target_fallback", null, null,
                "no evaluation_ladder step matched any non-vetoed candidate; chose by vanilla evaluation"));
        return fallback;
    }

    /**
     * As {@link #chooseGuidedRemovalTarget}, but for choosing which spell on the stack to counter
     * — a {@code target_rankings} rule for a counterspell's own {@code source_card} written with
     * {@code target_spell.*} condition fields (ai-play-guidance-spec.md §5.2's
     * {@code counterspell_priority} example) instead of {@code target.*}. Reuses the same
     * {@code targetRankingsBySourceCard} map — nothing about the rule storage or lookup needs to
     * know in advance whether a card's rule will be applied to {@code Card} or
     * {@code SpellAbility} candidates, only the rule's own authored condition fields need to match
     * what's actually passed to {@link PredicateEvaluator#evaluate}. See
     * forge-integration-guide.md §12.10.
     *
     * <p>Falls back to the first non-vetoed survivor (in iteration order) when no ladder step
     * matches any of them, rather than a vanilla-evaluation call like
     * {@link #chooseGuidedRemovalTarget} does — {@code CounterAi.chooseTargetSpellAbility()}'s own
     * vanilla "best option" comparison is an unfinished stub (a hardcoded
     * {@code betterThanBest = false}, confirmed by reading the source — first legal candidate
     * found always wins today), so falling back to it would not actually add anything a plain
     * first-survivor fallback doesn't already give.</p>
     *
     * @return the guided choice, or {@code null} if every candidate was vetoed
     */
    public SpellAbility chooseGuidedCounterTarget(SpellAbility sa, Player aiPlayer, Game game, Iterable<SpellAbility> candidates) {
        String sourceCardName = sa.getHostCard().getName();
        TargetRankingRule rule = targetRankingsBySourceCard.get(sourceCardName);

        List<SpellAbility> survivors = new ArrayList<>();
        for (SpellAbility candidate : candidates) {
            boolean vetoed = false;
            for (TargetRankingRule.Veto veto : rule.getVetoes()) {
                if (PredicateEvaluator.evaluate(veto.condition(), this, aiPlayer, game, candidate)) {
                    vetoed = true;
                    break;
                }
            }
            if (!vetoed) {
                survivors.add(candidate);
            }
        }
        if (survivors.isEmpty()) {
            game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(), sourceCardName,
                    "target_all_vetoed", null, null,
                    "every candidate spell matched a veto condition"));
            return null;
        }

        SpellAbility best = null;
        int bestScore = Integer.MIN_VALUE;
        String bestDescription = null;
        for (SpellAbility candidate : survivors) {
            for (TargetRankingRule.LadderStep step : rule.getLadder()) {
                if (PredicateEvaluator.evaluate(step.condition(), this, aiPlayer, game, candidate)) {
                    int score = scaledScore(step, game);
                    if (score > bestScore) {
                        bestScore = score;
                        best = candidate;
                        bestDescription = step.description();
                    }
                    break; // first-matching-step-wins per candidate, spec §5.2
                }
            }
        }
        if (best != null) {
            game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(),
                    best.getHostCard() != null ? best.getHostCard().getName() : null,
                    "target_selected", bestDescription, bestScore, null));
            return best;
        }

        SpellAbility fallback = survivors.get(0);
        game.fireEvent(new GameEventAiGuidanceDecision(aiPlayer.getName(),
                fallback.getHostCard() != null ? fallback.getHostCard().getName() : null,
                "target_fallback", null, null,
                "no evaluation_ladder step matched any non-vetoed candidate; chose the first survivor"));
        return fallback;
    }

    public boolean isEmpty() {
        return cardRoles.isEmpty() && deploymentConstraintsByRole.isEmpty() && targetRankingsBySourceCard.isEmpty()
                && tacticalSequences.isEmpty() && evaluationStages.isEmpty();
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
