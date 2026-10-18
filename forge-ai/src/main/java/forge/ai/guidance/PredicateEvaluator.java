package forge.ai.guidance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import forge.ai.ComputerUtilCard;
import forge.ai.ComputerUtilMana;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
            case "target.is_commander":
                // Card.isCommander(): true for a commander in any zone (incl. melded/merged), own or opposing.
                // A non-Card target (counterspell candidates) cannot be evaluated, so neither == nor != matches.
                if (targetCard == null) {
                    return false;
                }
                boolean wantCommander = val == null || val.getAsBoolean();
                return "!=".equals(op) ? targetCard.isCommander() != wantCommander
                        : targetCard.isCommander() == wantCommander;
            case "target.name":
                // Exact card name (the same key role_bindings and the threat catalog use).
                if (targetCard == null || val == null || !val.isJsonPrimitive()) {
                    return false;
                }
                boolean nameMatches = val.getAsString().equals(targetCard.getName());
                return "!=".equals(op) ? !nameMatches : nameMatches;
            case "target.controller":
                if (targetCard == null || val == null || !val.isJsonPrimitive()) {
                    return false;
                }
                boolean controllerMatches = controllerMatches(aiPlayer, targetCard, val.getAsString());
                return "!=".equals(op) ? !controllerMatches : controllerMatches;
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
            case "target_spell.effect_types":
                // Resolved per §12.11.4's decision: infer from the real, active SpellAbility's
                // ApiType at cast/targeting time rather than static MaMo-side card metadata - the
                // one already on the stack correctly reflects which mode a modal spell (Cryptic
                // Command, etc.) actually chose, where static per-card metadata could not. A
                // best-effort classification, not exhaustive: covers the effect types the spec's
                // own worked examples reference (destroy/exile/bounce/counter/mass_removal/
                // minus_x_minus_x) and nothing beyond them - see forge-integration-guide.md §12.12.
                return targetSpell != null && compareSet(effectTypesOf(targetSpell), op, val);
            case "state.game_stage":
                // §12.11.1's "stage modifier overlay" decision, generic half: any existing
                // condition (trigger, veto, ladder step, deployment guard) can gate on the current
                // evaluation_profile stage directly, rather than this evaluator silently applying
                // an implicit multiplier authors can't see or reason about. The multiplier half
                // (evaluation_profile.stages.<stage>.weights scaling target_rankings ladder
                // scores) lives in AiGuidanceProfile.chooseGuidedRemovalTarget/
                // chooseGuidedCounterTarget instead, where a numeric score actually exists to
                // scale - see forge-integration-guide.md §12.12.
                String stage = profile.currentStage(game);
                return stage != null && val != null && val.getAsString().equals(stage);
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

    /** Values accepted by {@code target.controller}. */
    static final Set<String> CONTROLLER_VALUES = Set.of("own", "opponent", "leading_opponent");

    /**
     * Every leaf field {@link #evaluateLeaf} has a case for. Keep in sync with its switch: a field
     * missing here is reported by {@link #validate} and makes a veto fail closed.
     */
    static final Set<String> KNOWN_FIELDS = Set.of(
            "active_engine_core_count", "battlefield.creatures.count", "battlefield.roles",
            "hand.roles", "hand.has_roles_all", "opponent_open_mana", "resources.available_mana",
            "state.self_board_presence_ahead", "state.game_stage",
            "target.has_indestructible", "target.is_commander", "target.controller", "target.name",
            "target.canonical_threat_tier", "target.role",
            "target_spell.canonical_threat_tier", "target_spell.cmc", "target_spell.types",
            "target_spell.targets_our_role", "target_spell.effect_types");

    /**
     * Problems that would make {@code ast} unevaluable: unknown fields and values {@code
     * target.controller} does not accept. Empty when the condition is fully understood. Used to
     * fail vetoes closed, see {@link AiGuidanceProfile#vetoApplies}.
     */
    public static List<String> validate(JsonObject ast) {
        List<String> problems = new ArrayList<>();
        collectProblems(ast, problems);
        return problems;
    }

    private static void collectProblems(JsonElement node, List<String> problems) {
        if (node == null || !node.isJsonObject()) {
            return;
        }
        JsonObject ast = node.getAsJsonObject();
        for (String combinator : new String[] {"all_of", "any_of", "none_of"}) {
            if (ast.has(combinator) && ast.get(combinator).isJsonArray()) {
                for (JsonElement child : ast.getAsJsonArray(combinator)) {
                    collectProblems(child, problems);
                }
            }
        }
        if (!ast.has("field")) {
            return;
        }
        String field = ast.get("field").getAsString();
        if (!KNOWN_FIELDS.contains(field)) {
            problems.add("unknown field '" + field + "'");
        } else if ("target.controller".equals(field)) {
            JsonElement v = ast.get("value");
            String op = ast.has("op") ? ast.get("op").getAsString() : null;
            if (v == null || !v.isJsonPrimitive() || !CONTROLLER_VALUES.contains(v.getAsString())) {
                problems.add("target.controller value must be one of " + CONTROLLER_VALUES);
            }
            if (!"==".equals(op) && !"!=".equals(op)) {
                problems.add("target.controller supports only '==' and '!=' (got '" + op + "')");
            }
        }
    }

    private static boolean controllerMatches(Player aiPlayer, Card target, String which) {
        Player controller = target.getController();
        switch (which) {
            case "own":
                return controller.equals(aiPlayer);
            case "opponent":
                return aiPlayer.isOpponentOf(controller);
            case "leading_opponent":
                return controller.equals(leadingOpponent(aiPlayer));
            default:
                return false;
        }
    }

    /**
     * The "leading" opponent: the one with the highest total creature value
     * ({@link ComputerUtilCard#evaluateCreatureList}, the same measure {@code
     * state.self_board_presence_ahead} uses), ties broken by higher life, then turn order. Not
     * {@code Player.getStrongestOpponent()}, which is highest life only and ignores the board.
     * {@code null} if there are no opponents.
     */
    static Player leadingOpponent(Player aiPlayer) {
        Player leader = null;
        int leaderValue = Integer.MIN_VALUE;
        for (Player opp : aiPlayer.getOpponents()) {
            int value = ComputerUtilCard.evaluateCreatureList(opp.getCreaturesInPlay());
            if (leader == null || value > leaderValue || (value == leaderValue && opp.getLife() > leader.getLife())) {
                leader = opp;
                leaderValue = value;
            }
        }
        return leader;
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

    /**
     * Best-effort classification of {@code sa}'s effect(s) into the vocabulary
     * ai-play-guidance-spec.md's own worked examples use (§5.1's veto example:
     * {@code ["exile", "bounce", "minus_x_minus_x"]}). A single {@code SpellAbility} can map to
     * more than one type (e.g. {@code Pump} with a negative toughness modifier is both
     * {@code "minus_x_minus_x"} and, loosely, a debuff) - returns every type that applies, not
     * just one.
     */
    private static Set<String> effectTypesOf(SpellAbility sa) {
        Set<String> types = new HashSet<>();
        ApiType api = sa.getApi();
        if (api == null) {
            return types;
        }
        switch (api) {
            case Destroy:
                types.add("destroy");
                break;
            case DestroyAll:
                types.add("destroy");
                types.add("mass_removal");
                break;
            case SacrificeAll:
                types.add("mass_removal");
                break;
            case DamageAll:
                // Board-wide damage is only "mass_removal" in intent, not guaranteed lethal to
                // everything - a simplification, not a lethality check (would need per-creature
                // toughness comparison against the damage amount, which the field name alone
                // doesn't ask for).
                types.add("mass_removal");
                break;
            case Counter:
                types.add("counter");
                break;
            case ChangeZone:
                String destination = sa.getParam("Destination");
                if ("Exile".equals(destination)) {
                    types.add("exile");
                } else if ("Hand".equals(destination)) {
                    types.add("bounce");
                }
                break;
            case Pump:
            case PumpAll:
                // "-X/-X" detection is a string heuristic (does NumDef start with "-"?), not a
                // real expression evaluation - covers the common literal/"-X" cases the spec's own
                // examples use, not every possible SVar-driven toughness formula.
                String numDef = sa.getParam("NumDef");
                if (numDef != null && numDef.trim().startsWith("-")) {
                    types.add("minus_x_minus_x");
                }
                break;
            default:
                break;
        }
        return types;
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
