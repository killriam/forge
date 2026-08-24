package forge.ai.guidance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.ai.ComputerUtilCard;
import forge.ai.ComputerUtilMana;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Evaluates the {@code ai_guidance} Structured Predicate AST
 * ({@code all_of}/{@code any_of}/{@code none_of} logical combinators, {@code field}/{@code op}/
 * {@code value} leaves) against live game state.
 * See mtg-replay-notation/spec/ai-play-guidance-spec.md §4.3/§10.2.
 *
 * <p>Deviates from both spec documents' own reference pseudocode in three ways, documented in
 * forge-integration-guide.md §12.6/§12.10:</p>
 * <ol>
 *   <li>Takes an {@link AiGuidanceProfile} parameter (not just Player/Game/Card). Several
 *       documented leaf fields — {@code active_engine_core_count}, {@code battlefield.roles},
 *       {@code hand.roles} — resolve a card name to its declared {@code role_bindings} role,
 *       which only the profile knows; a pure {@code (Player, Game, Card)} function cannot
 *       implement them at all.</li>
 *   <li>Implements the set operators ({@code contains}, {@code contains_any}, {@code
 *       contains_all}, {@code excludes_all}, {@code lacks}) that ai-play-guidance-spec.md §10.2's
 *       own {@code PredicateOperator} TypeScript union declares, and that the spec's own worked
 *       {@code multiplier_requires_board}/{@code engine_core_hold_against_countermagic} examples
 *       (§4.3) use — but that neither document's Java {@code PredicateEvaluator} reference
 *       implementation actually has a case for (their {@code compareInt}-only leaf evaluator
 *       would throw trying to read an array {@code value} as an int).</li>
 *   <li>The last parameter is {@code Object target}, not {@code Card target} — {@code target.*}
 *       fields (removal targeting) need a {@link Card}; {@code target_spell.*} fields
 *       (counterspell targeting, §12.10) need a {@link SpellAbility} on the stack instead. One
 *       evaluator handles both rather than duplicating the {@code all_of}/{@code any_of}/
 *       {@code none_of} traversal for a second, near-identical spell-targeted variant.</li>
 * </ol>
 */
public final class PredicateEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(PredicateEvaluator.class);

    private PredicateEvaluator() { }

    public static boolean evaluate(JsonObject ast, AiGuidanceProfile profile, Player aiPlayer, Game game, Object target) {
        if (ast == null || ast.isJsonNull()) {
            return true;
        }
        if (ast.has("all_of")) {
            for (JsonElement el : ast.getAsJsonArray("all_of")) {
                if (!evaluate(el.getAsJsonObject(), profile, aiPlayer, game, target)) {
                    return false;
                }
            }
            return true;
        }
        if (ast.has("any_of")) {
            for (JsonElement el : ast.getAsJsonArray("any_of")) {
                if (evaluate(el.getAsJsonObject(), profile, aiPlayer, game, target)) {
                    return true;
                }
            }
            return false;
        }
        if (ast.has("none_of")) {
            for (JsonElement el : ast.getAsJsonArray("none_of")) {
                if (evaluate(el.getAsJsonObject(), profile, aiPlayer, game, target)) {
                    return false;
                }
            }
            return true;
        }
        if (!ast.has("field") || !ast.has("op")) {
            return true;
        }
        String field = ast.get("field").getAsString();
        String op = ast.get("op").getAsString();
        JsonElement val = ast.has("value") ? ast.get("value") : null;
        return evaluateLeaf(field, op, val, profile, aiPlayer, game, target);
    }

    private static boolean evaluateLeaf(String field, String op, JsonElement val, AiGuidanceProfile profile,
            Player aiPlayer, Game game, Object target) {
        Card targetCard = target instanceof Card c ? c : null;
        SpellAbility targetSpell = target instanceof SpellAbility sa ? sa : null;

        switch (field) {
            case "active_engine_core_count":
                return compareInt(countByRole(profile, aiPlayer, "engine_core"), op, val);
            case "battlefield.creatures.count":
                return compareInt(aiPlayer.getCreaturesInPlay().size(), op, val);
            case "battlefield.roles":
                return compareSet(rolesIn(profile, aiPlayer, ZoneType.Battlefield), op, val);
            case "hand.roles":
            case "hand.has_roles_all":
                // Same underlying set - "hand.has_roles_all" is ai-play-guidance-spec.md §6.2's
                // own bait_countermagic_sequence example's field name for exactly this, used
                // there with op "contains_all"; "hand.roles" (§4.3's own naming) already supports
                // that op via compareSet(). One more field-naming disagreement between the spec's
                // own worked examples, not a new field to build - see PredicateEvaluatorTest.
                return compareSet(rolesIn(profile, aiPlayer, ZoneType.Hand), op, val);
            case "opponent_open_mana":
                return compareInt(maxOpponentUntappedLands(aiPlayer), op, val);
            case "resources.available_mana":
                // Count of available mana *sources*, not total mana yield - undercounts sources
                // producing >1 (Sol Ring, etc.), same class of simplification as
                // opponent_open_mana below. ai-play-guidance-spec.md §6.2's own example compares
                // this against a "{sum_cmc}" dynamic placeholder (the summed CMC of the stage's
                // own target cards) - that expression-evaluation capability is NOT implemented;
                // only literal numeric values work against this field today. See
                // forge-integration-guide.md §12.10.
                return compareInt(ComputerUtilMana.getAvailableManaSources(aiPlayer, true).size(), op, val);
            case "state.self_board_presence_ahead":
                // Interpretation choice, not in either spec document: "ahead" means strictly
                // ahead of *every* opponent (conservative for multiplayer) on total creature
                // value (ComputerUtilCard.evaluateCreatureList(), the same evaluator
                // getBestCreatureAI/getWorstCreatureAI already use). Deliberately not
                // ComputerUtil.evaluateBoardPosition() - that method computes threat *to* its
                // first argument *from* its second (hand size, predicted combat life loss, etc.),
                // not a board-strength comparison - evaluateBoardPosition(ai, opp) is "how
                // dangerous is opp's board to ai", so a naive ">0" reading of it is backwards for
                // this field's actual question, confirmed by reading the method body rather than
                // assumed from its name.
                boolean aheadOfAll = true;
                int selfCreatureValue = ComputerUtilCard.evaluateCreatureList(aiPlayer.getCreaturesInPlay());
                for (Player opp : aiPlayer.getOpponents()) {
                    if (selfCreatureValue <= ComputerUtilCard.evaluateCreatureList(opp.getCreaturesInPlay())) {
                        aheadOfAll = false;
                        break;
                    }
                }
                boolean expectedAhead = val == null || val.getAsBoolean();
                return aheadOfAll == expectedAhead;
            case "target.has_indestructible":
                boolean expectedIndestructible = val == null || val.getAsBoolean();
                boolean actualIndestructible = targetCard != null
                        && (targetCard.hasKeyword("Indestructible") || targetCard.hasKeyword("Hexproof"));
                return actualIndestructible == expectedIndestructible;
            case "target.canonical_threat_tier":
                return targetCard != null && val != null
                        && val.getAsString().equals(profile.canonicalThreatTierOf(targetCard.getName()));
            case "target.role":
                return targetCard != null && val != null
                        && val.getAsString().equals(profile.roleOf(targetCard.getName()));
            case "target_spell.canonical_threat_tier":
                return targetSpell != null && targetSpell.getHostCard() != null && val != null
                        && val.getAsString().equals(profile.canonicalThreatTierOf(targetSpell.getHostCard().getName()));
            case "target_spell.cmc":
                return targetSpell != null && targetSpell.getHostCard() != null
                        && compareInt(targetSpell.getHostCard().getCMC(), op, val);
            case "target_spell.types":
                if (targetSpell == null || targetSpell.getHostCard() == null || val == null || !val.isJsonPrimitive()) {
                    return false;
                }
                return "contains".equals(op) && targetSpell.getHostCard().getType().hasStringType(val.getAsString());
            case "target_spell.targets_our_role":
                // Does the spell being considered for a counter itself target one of aiPlayer's
                // own permanents with this declared role? Reads the real chosen targets off the
                // stack item (already resolved by cast time), not a guess.
                if (targetSpell == null || val == null) {
                    return false;
                }
                String wantedRole = val.getAsString();
                if (targetSpell.getTargets() == null) {
                    return false;
                }
                for (Card c : targetSpell.getTargets().getTargetCards()) {
                    if (aiPlayer.equals(c.getController()) && profile.cardHasRole(c.getName(), wantedRole)) {
                        return true;
                    }
                }
                return false;
            default:
                // Fail OPEN (condition "satisfied") rather than reject the whole ai_guidance
                // profile on one unrecognized field - matches both spec documents' documented
                // default, but unlike them this logs, so an unsupported field silently
                // no-op'ing a deployment guard is visible in testing instead of hidden.
                LOG.warn("ai_guidance predicate references unsupported field '{}' - treating as " +
                        "satisfied; see forge-integration-guide.md §12.6/§12.10 for the supported field list", field);
                return true;
        }
    }

    private static int countByRole(AiGuidanceProfile profile, Player aiPlayer, String role) {
        int count = 0;
        for (Card c : aiPlayer.getCardsIn(ZoneType.Battlefield)) {
            if (role.equals(profile.roleOf(c.getName()))) {
                count++;
            }
        }
        return count;
    }

    private static Set<String> rolesIn(AiGuidanceProfile profile, Player aiPlayer, ZoneType zone) {
        Set<String> roles = new HashSet<>();
        for (Card c : aiPlayer.getCardsIn(zone)) {
            String role = profile.roleOf(c.getName());
            if (role != null) {
                roles.add(role);
            }
        }
        return roles;
    }

    private static int maxOpponentUntappedLands(Player aiPlayer) {
        // Simplification, inherited from both spec documents: counts untapped lands, not actual
        // castable mana (ignores rocks/dorks and color requirements). See forge-integration-guide
        // .md §12.5.2 for why a richer model (ComputerUtilMana) isn't wired in here in slice 1.
        int maxUntapped = 0;
        for (Player opp : aiPlayer.getOpponents()) {
            int lands = 0;
            for (Card c : opp.getCardsIn(ZoneType.Battlefield)) {
                if (c.isLand() && c.isUntapped()) {
                    lands++;
                }
            }
            if (lands > maxUntapped) {
                maxUntapped = lands;
            }
        }
        return maxUntapped;
    }

    private static boolean compareInt(int actualValue, String op, JsonElement val) {
        if (val == null || !val.isJsonPrimitive()) {
            LOG.warn("ai_guidance numeric comparison (op '{}') has a missing/non-scalar value", op);
            return false;
        }
        int expected = val.getAsInt();
        switch (op) {
            case "==": return actualValue == expected;
            case "!=": return actualValue != expected;
            case ">":  return actualValue > expected;
            case ">=": return actualValue >= expected;
            case "<":  return actualValue < expected;
            case "<=": return actualValue <= expected;
            default:
                LOG.warn("ai_guidance predicate uses '{}' as a numeric op - not one of ==,!=,>,>=,<,<=", op);
                return false;
        }
    }

    private static boolean compareSet(Set<String> actualValues, String op, JsonElement val) {
        switch (op) {
            case "contains":
                return val != null && val.isJsonPrimitive() && actualValues.contains(val.getAsString());
            case "contains_any":
                if (val == null || !val.isJsonArray()) return false;
                for (JsonElement e : val.getAsJsonArray()) {
                    if (actualValues.contains(e.getAsString())) return true;
                }
                return false;
            case "contains_all":
                if (val == null || !val.isJsonArray()) return false;
                for (JsonElement e : val.getAsJsonArray()) {
                    if (!actualValues.contains(e.getAsString())) return false;
                }
                return true;
            case "excludes_all":
                if (val == null || !val.isJsonArray()) return true;
                for (JsonElement e : val.getAsJsonArray()) {
                    if (actualValues.contains(e.getAsString())) return false;
                }
                return true;
            case "lacks":
                return val == null || !val.isJsonPrimitive() || !actualValues.contains(val.getAsString());
            default:
                LOG.warn("ai_guidance predicate uses '{}' as a set op - not one of contains,contains_any," +
                        "contains_all,excludes_all,lacks", op);
                return false;
        }
    }
}
