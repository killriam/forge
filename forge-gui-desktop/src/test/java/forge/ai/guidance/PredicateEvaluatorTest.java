package forge.ai.guidance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.ai.AITest;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests for {@link PredicateEvaluator}. AST-combinator and unsupported-field-fallback tests are
 * pure data-level (no game engine needed, mirrors {@code ScenarioForcedPlaySequenceTest}'s
 * style). Field-resolution tests ({@code battlefield.creatures.count}, {@code battlefield.roles},
 * {@code active_engine_core_count}) need a real {@link Player}/{@link Game} — Forge's own AI test
 * suite has no lightweight fake for {@code Player} (it's stateful and game-registered), so this
 * extends {@link AITest} and uses its real headless-game construction, the same way every
 * existing {@code forge.ai.ability.*AiTest} class does. Runs via plain {@code mvn test}.
 */
public class PredicateEvaluatorTest extends AITest {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // -------------------------------------------------------------------------
    // Pure AST logic — no Player/Game needed (all fields below evaluate before
    // ever reaching evaluateLeaf, or use only null-safe fallback paths).
    // -------------------------------------------------------------------------

    @Test
    public void testNullAstIsAlwaysSatisfied() {
        assertTrue(PredicateEvaluator.evaluate(null, AiGuidanceProfile.parse(null), null, null, null));
    }

    @Test
    public void testUnsupportedFieldFailsOpenNotClosed() {
        // Documented, deliberate default matching both spec documents' own reference pseudocode
        // (forge-integration-guide.md §12.2, ai-play-guidance-spec.md §11.3) — logs a warning
        // instead of silently doing so; see this class's own javadoc for why.
        JsonObject ast = obj("{\"field\":\"totally_made_up_field\",\"op\":\"==\",\"value\":\"x\"}");
        assertTrue(PredicateEvaluator.evaluate(ast, AiGuidanceProfile.parse(null), null, null, null));
    }

    @Test
    public void testAnyOfIsSatisfiedByAnUnsupportedFieldFailingOpen() {
        JsonObject ast = obj("{\"any_of\":[{\"field\":\"unsupported_field_x\",\"op\":\"==\",\"value\":1}]}");
        assertTrue(PredicateEvaluator.evaluate(ast, AiGuidanceProfile.parse(null), null, null, null));
    }

    @Test
    public void testNoneOfInvertsAnUnsupportedFieldFailingOpen() {
        JsonObject ast = obj("{\"none_of\":[{\"field\":\"unsupported_field_x\",\"op\":\"==\",\"value\":1}]}");
        // the one branch fails open (true) -> none_of is violated -> false
        assertFalse(PredicateEvaluator.evaluate(ast, AiGuidanceProfile.parse(null), null, null, null));
    }

    // -------------------------------------------------------------------------
    // Field resolution against a real headless game
    // -------------------------------------------------------------------------

    @Test
    public void testAllOfRequiresEveryBranchNumericField() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Runeclaw Bear", ai);
        addCard("Runeclaw Bear", ai);

        AiGuidanceProfile profile = AiGuidanceProfile.parse(null);
        JsonObject bothTrue = obj("{\"all_of\":["
                + "{\"field\":\"battlefield.creatures.count\",\"op\":\">=\",\"value\":2},"
                + "{\"field\":\"battlefield.creatures.count\",\"op\":\"<=\",\"value\":2}"
                + "]}");
        assertTrue(PredicateEvaluator.evaluate(bothTrue, profile, ai, game, null));

        JsonObject oneFalse = obj("{\"all_of\":["
                + "{\"field\":\"battlefield.creatures.count\",\"op\":\">=\",\"value\":2},"
                + "{\"field\":\"battlefield.creatures.count\",\"op\":\">\",\"value\":2}"
                + "]}");
        assertFalse(PredicateEvaluator.evaluate(oneFalse, profile, ai, game, null));
    }

    @Test
    public void testNumericOpsCoverFullSet() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Runeclaw Bear", ai);
        addCard("Runeclaw Bear", ai);
        addCard("Runeclaw Bear", ai); // exactly 3 creatures on the battlefield
        AiGuidanceProfile profile = AiGuidanceProfile.parse(null);

        assertTrue(numeric(profile, ai, game, "==", 3));
        assertFalse(numeric(profile, ai, game, "==", 4));
        assertTrue(numeric(profile, ai, game, "!=", 4));
        assertTrue(numeric(profile, ai, game, ">", 2));
        assertTrue(numeric(profile, ai, game, ">=", 3));
        assertTrue(numeric(profile, ai, game, "<", 4));
        assertTrue(numeric(profile, ai, game, "<=", 3));
        assertFalse(numeric(profile, ai, game, "<=", 2));
    }

    private boolean numeric(AiGuidanceProfile profile, Player ai, Game game, String op, int expected) {
        JsonObject ast = obj("{\"field\":\"battlefield.creatures.count\",\"op\":\"" + op + "\",\"value\":" + expected + "}");
        return PredicateEvaluator.evaluate(ast, profile, ai, game, null);
    }

    @Test
    public void testActiveEngineCoreCountResolvesDeclaredRoles() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Ashnod's Altar", ai);
        addCard("Sol Ring", ai); // no declared role -> must not count

        JsonObject cardsJson = obj("{\"role_bindings\":{\"cards\":{"
                + "\"Ashnod's Altar\":{\"primary_role\":\"engine_core\"}"
                + "}}}");
        AiGuidanceProfile profile = AiGuidanceProfile.parse(cardsJson);

        JsonObject ast = obj("{\"field\":\"active_engine_core_count\",\"op\":\"==\",\"value\":1}");
        assertTrue(PredicateEvaluator.evaluate(ast, profile, ai, game, null));
    }

    @Test
    public void testBattlefieldRolesContainsAny() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCard("Ashnod's Altar", ai);

        JsonObject cardsJson = obj("{\"role_bindings\":{\"cards\":{"
                + "\"Ashnod's Altar\":{\"primary_role\":\"engine_core\"}"
                + "}}}");
        AiGuidanceProfile profile = AiGuidanceProfile.parse(cardsJson);

        JsonObject matches = obj("{\"field\":\"battlefield.roles\",\"op\":\"contains_any\","
                + "\"value\":[\"engine_core\",\"enabler\"]}");
        assertTrue(PredicateEvaluator.evaluate(matches, profile, ai, game, null));

        JsonObject noMatch = obj("{\"field\":\"battlefield.roles\",\"op\":\"contains_any\","
                + "\"value\":[\"payoff\",\"protection\"]}");
        assertFalse(PredicateEvaluator.evaluate(noMatch, profile, ai, game, null));
    }

    @Test
    public void testHandRolesLacks() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCardToZone("Heroic Intervention", ai, ZoneType.Hand);

        AiGuidanceProfile emptyProfile = AiGuidanceProfile.parse(null);
        // no role declared for Heroic Intervention in this profile -> hand.roles is empty -> lacks "protection" is true
        JsonObject ast = obj("{\"field\":\"hand.roles\",\"op\":\"lacks\",\"value\":\"protection\"}");
        assertTrue(PredicateEvaluator.evaluate(ast, emptyProfile, ai, game, null));

        JsonObject cardsJson = obj("{\"role_bindings\":{\"cards\":{"
                + "\"Heroic Intervention\":{\"primary_role\":\"protection\"}"
                + "}}}");
        AiGuidanceProfile withRole = AiGuidanceProfile.parse(cardsJson);
        assertFalse(PredicateEvaluator.evaluate(ast, withRole, ai, game, null));
    }

    @Test
    public void testTargetHasIndestructible() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        // Darksteel Colossus has the printed Indestructible keyword itself. (Avacyn, Angel of
        // Hope was the first pick here, but she only grants Indestructible to *other* permanents
        // she controls - she does not have the keyword on her own card - so she would have made
        // this test silently assert the wrong thing.)
        forge.game.card.Card colossus = addCard("Darksteel Colossus", ai);
        forge.game.card.Card bear = addCard("Runeclaw Bear", ai);

        AiGuidanceProfile profile = AiGuidanceProfile.parse(null);
        JsonObject ast = obj("{\"field\":\"target.has_indestructible\",\"op\":\"==\",\"value\":true}");
        assertTrue(PredicateEvaluator.evaluate(ast, profile, ai, game, colossus));
        assertFalse(PredicateEvaluator.evaluate(ast, profile, ai, game, bear));
    }

    @Test
    public void testHandHasRolesAllIsTheSameSetAsHandRoles() {
        // ai-play-guidance-spec.md §6.2's bait_countermagic_sequence example uses the field name
        // "hand.has_roles_all" (op contains_all) for exactly what §4.3's own "hand.roles" field
        // already computes - one more field-naming disagreement between the spec's own worked
        // examples, not two different features. See PredicateEvaluator's own class javadoc.
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCardToZone("Ashnod's Altar", ai, ZoneType.Hand);
        addCardToZone("Heroic Intervention", ai, ZoneType.Hand);

        JsonObject cardsJson = obj("{\"role_bindings\":{\"cards\":{"
                + "\"Ashnod's Altar\":{\"primary_role\":\"engine_core\"},"
                + "\"Heroic Intervention\":{\"primary_role\":\"protection\"}"
                + "}}}");
        AiGuidanceProfile profile = AiGuidanceProfile.parse(cardsJson);

        JsonObject bothPresent = obj("{\"field\":\"hand.has_roles_all\",\"op\":\"contains_all\","
                + "\"value\":[\"engine_core\",\"protection\"]}");
        assertTrue(PredicateEvaluator.evaluate(bothPresent, profile, ai, game, null));

        JsonObject missingOne = obj("{\"field\":\"hand.has_roles_all\",\"op\":\"contains_all\","
                + "\"value\":[\"engine_core\",\"enabler\"]}");
        assertFalse(PredicateEvaluator.evaluate(missingOne, profile, ai, game, null));
    }

    @Test
    public void testResourcesAvailableMana() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCards("Forest", 3, ai);
        AiGuidanceProfile profile = AiGuidanceProfile.parse(null);

        assertTrue(PredicateEvaluator.evaluate(
                obj("{\"field\":\"resources.available_mana\",\"op\":\">=\",\"value\":3}"), profile, ai, game, null));
        assertFalse(PredicateEvaluator.evaluate(
                obj("{\"field\":\"resources.available_mana\",\"op\":\">=\",\"value\":4}"), profile, ai, game, null));
    }

    @Test
    public void testTargetSpellTargetsOurRole() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        forge.game.card.Card altar = addCard("Ashnod's Altar", ai);

        JsonObject cardsJson = obj("{\"role_bindings\":{\"cards\":{"
                + "\"Ashnod's Altar\":{\"primary_role\":\"engine_core\"}"
                + "}}}");
        AiGuidanceProfile profile = AiGuidanceProfile.parse(cardsJson);

        // A real targeted removal spell, its target set the same way DestroyAi itself does
        // (sa.getTargets().add(card)) - reads the target off the real, resolved TargetChoices,
        // not a guess at what the spell "would" target.
        forge.game.spellability.SpellAbility shatter = createCard("Vandalblast", opponent).getSpellAbilities().get(0);
        shatter.setActivatingPlayer(opponent);
        shatter.resetTargets();
        shatter.getTargets().add(altar);

        JsonObject ast = obj("{\"field\":\"target_spell.targets_our_role\",\"op\":\"==\",\"value\":\"engine_core\"}");
        assertTrue(PredicateEvaluator.evaluate(ast, profile, ai, game, shatter));

        JsonObject wrongRole = obj("{\"field\":\"target_spell.targets_our_role\",\"op\":\"==\",\"value\":\"payoff\"}");
        assertFalse(PredicateEvaluator.evaluate(wrongRole, profile, ai, game, shatter));
    }

    @Test
    public void testStateSelfBoardPresenceAhead() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        AiGuidanceProfile profile = AiGuidanceProfile.parse(null);
        JsonObject ast = obj("{\"field\":\"state.self_board_presence_ahead\",\"op\":\"==\",\"value\":true}");

        // Empty board on both sides: not strictly ahead
        assertFalse(PredicateEvaluator.evaluate(ast, profile, ai, game, null));

        addCard("Grave Titan", ai);
        addCard("Runeclaw Bear", opponent);
        assertTrue(PredicateEvaluator.evaluate(ast, profile, ai, game, null));
    }

    @Test
    public void testTargetSpellEffectTypes() {
        Game game = initAndCreateGame();
        Player opponent = game.getPlayers().get(0);
        AiGuidanceProfile profile = AiGuidanceProfile.parse(null);

        forge.game.spellability.SpellAbility destroy = createCard("Doom Blade", opponent).getSpellAbilities().get(0);
        assertTrue(PredicateEvaluator.evaluate(
                obj("{\"field\":\"target_spell.effect_types\",\"op\":\"contains\",\"value\":\"destroy\"}"),
                profile, opponent, game, destroy));

        forge.game.spellability.SpellAbility exile = createCard("Swords to Plowshares", opponent).getSpellAbilities().get(0);
        assertTrue(PredicateEvaluator.evaluate(
                obj("{\"field\":\"target_spell.effect_types\",\"op\":\"contains\",\"value\":\"exile\"}"),
                profile, opponent, game, exile));

        forge.game.spellability.SpellAbility counter = createCard("Counterspell", opponent).getSpellAbilities().get(0);
        assertTrue(PredicateEvaluator.evaluate(
                obj("{\"field\":\"target_spell.effect_types\",\"op\":\"contains\",\"value\":\"counter\"}"),
                profile, opponent, game, counter));

        // A vanilla creature spell has no destroy/exile/bounce/counter/mass_removal/minus_x_minus_x
        // effect at all - effectTypesOf() should return an empty set, not guess.
        forge.game.spellability.SpellAbility bear = createCard("Runeclaw Bear", opponent).getSpellAbilities().get(0);
        assertFalse(PredicateEvaluator.evaluate(
                obj("{\"field\":\"target_spell.effect_types\",\"op\":\"contains\",\"value\":\"destroy\"}"),
                profile, opponent, game, bear));
        assertTrue(PredicateEvaluator.evaluate(
                obj("{\"field\":\"target_spell.effect_types\",\"op\":\"excludes_all\",\"value\":[\"exile\",\"bounce\",\"minus_x_minus_x\"]}"),
                profile, opponent, game, destroy));
    }

    @Test
    public void testStateGameStage() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);

        JsonObject stagesJson = obj("{\"evaluation_profile\":{\"stages\":{"
                + "\"early\":{\"turns\":[1,3],\"weights\":{}},"
                + "\"late\":{\"turns\":[8,99],\"weights\":{}}"
                + "}}}");
        AiGuidanceProfile profile = AiGuidanceProfile.parse(stagesJson);

        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN1, ai, 2);
        assertTrue(PredicateEvaluator.evaluate(
                obj("{\"field\":\"state.game_stage\",\"op\":\"==\",\"value\":\"early\"}"), profile, ai, game, null));

        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN1, ai, 10);
        assertTrue(PredicateEvaluator.evaluate(
                obj("{\"field\":\"state.game_stage\",\"op\":\"==\",\"value\":\"late\"}"), profile, ai, game, null));

        // Turn 5 falls in the gap between the two declared stages - no stage matches.
        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN1, ai, 5);
        assertFalse(PredicateEvaluator.evaluate(
                obj("{\"field\":\"state.game_stage\",\"op\":\"==\",\"value\":\"early\"}"), profile, ai, game, null));
        assertFalse(PredicateEvaluator.evaluate(
                obj("{\"field\":\"state.game_stage\",\"op\":\"==\",\"value\":\"late\"}"), profile, ai, game, null));
    }
}
