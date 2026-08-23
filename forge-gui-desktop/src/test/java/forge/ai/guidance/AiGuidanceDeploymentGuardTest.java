package forge.ai.guidance;

import forge.ai.AITest;
import forge.ai.PlayerControllerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * End-to-end proof that the ai_guidance deployment-guard hook wired into
 * {@code AiController.chooseSpellAbilityToPlayFromList()} actually changes AI behavior in a
 * real, headless game — not just that {@link PredicateEvaluator}/{@link AiGuidanceProfile} parse
 * correctly in isolation (see {@link PredicateEvaluatorTest} for that layer). Exercises the real
 * production path end to end: JSON file → {@code DeckRulesLoader.loadAiGuidanceIfNeeded()} →
 * {@code AiController.initGuidanceProfile()} → the veto check in
 * {@code chooseSpellAbilityToPlayFromList()}.
 *
 * <p>Runs via plain {@code mvn test}; no GUI, no manual scenario click-through. Both spec
 * documents describe this exact scenario as a manual GUI checkpoint (§11.5 "Checkpoint 2:
 * Multiplier Guard Sanity" / scenario {@code UNI_MULTIPLIER_NO_ENABLER}) — this automates the
 * part of it that doesn't require a human. See forge-integration-guide.md §12.6 for what's still
 * genuinely GUI/human-only after this.</p>
 *
 * <p>Uses {@link #moveToMain2} before letting the AI act. Vanilla Forge AI deliberately
 * prefers Main2 for a plain non-urgent permanent — {@code PermanentAi.checkPhaseRestrictions()}
 * (forge-ai/src/main/java/forge/ai/ability/PermanentAi.java:38, "Wait for Main2 if possible")
 * declines to cast a summoning-sick creature with no Main1-specific reason to come down early,
 * to avoid revealing information before combat (matches the "bluff potential" rationale in
 * {@code docs/AI_DECISION_MAKING_CONCEPT.md} §6.1). An earlier version of this test drove the
 * game only from Main1 to {@code COMBAT_BEGIN} via {@code gameLoopUntilNextPhase} and saw the
 * creature never get cast in <i>any</i> variant, including the one with no ai_guidance involved
 * at all — that was this Main2 timing preference, not a bug in the guard, so debugged and fixed
 * rather than worked around; see forge-integration-guide.md §12.6.2 for the full trail.</p>
 *
 * <p>The fixture ({@code ai_guidance/multiplier_guard.json}) is the {@code
 * multiplier_requires_board} deployment constraint from ai-play-guidance-spec.md §4.3, verbatim
 * — bound to a plain vanilla creature (<b>Runeclaw Bear</b>) rather than the spec's own example
 * card (Doubling Season), which (like Sol Ring, tried first) turned out to be a worse fixture:
 * vanilla Forge AI already declines both of those in an empty-board test for its own unrelated
 * reasons (nothing to double / nothing to spend the ramp on), which would have made "the guard
 * blocked this" indistinguishable from "the AI didn't want it anyway." A vanilla 2/2 has neither
 * ambiguity. The role label is a test fixture, not a real recommendation to tag a vanilla bear as
 * a "multiplier" in an actual policy.</p>
 */
public class AiGuidanceDeploymentGuardTest extends AITest {

    private static File fixture(String name) {
        URL url = AiGuidanceDeploymentGuardTest.class.getClassLoader().getResource("ai_guidance/" + name);
        AssertJUnit.assertNotNull("Test fixture ai_guidance/" + name + " not found on test classpath", url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static void attachGuidance(Player ai, String fixtureName) {
        Deck deck = new Deck();
        deck.setDecklistSpecPath(fixture(fixtureName).getAbsolutePath());
        ((PlayerControllerAi) ai.getController()).getAi().initGuidanceProfile(deck);
    }

    @Test
    public void doesNotDeployMultiplierOnEmptyBoard() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        attachGuidance(ai, "multiplier_guard.json");

        addCardToZone("Runeclaw Bear", ai, ZoneType.Hand);
        addCards("Forest", 2, ai); // battlefield, untapped — {1}{G} is easily affordable

        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);

        AssertJUnit.assertEquals(
                "Guard should have held Runeclaw Bear back off an empty/non-qualifying board",
                0, countCardsWithName(game, "Runeclaw Bear", ZoneType.Battlefield));
        AssertJUnit.assertEquals(
                "Runeclaw Bear should still be sitting in hand, not the battlefield",
                1, countCardsWithName(game, "Runeclaw Bear", ZoneType.Hand));
    }

    @Test
    public void deploysMultiplierOnceAnEngineCoreIsOnline() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        attachGuidance(ai, "multiplier_guard.json");

        addCardToZone("Runeclaw Bear", ai, ZoneType.Hand);
        addCard("Ashnod's Altar", ai); // battlefield permanent with role_bindings primary_role=engine_core
        addCards("Forest", 2, ai);

        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);

        AssertJUnit.assertEquals(
                "Guard should allow Runeclaw Bear once an engine_core-role permanent is already online",
                1, countCardsWithName(game, "Runeclaw Bear", ZoneType.Battlefield));
    }

    @Test
    public void withoutAGuidanceProfileBehaviorIsUnchanged() {
        // Backward-compatibility guarantee (forge-integration-guide.md §12.4): no ai_guidance
        // attached at all -> guidanceProfile stays null -> the hook no-ops -> the AI plays
        // Runeclaw Bear exactly as vanilla Forge always has, even on an empty board.
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        // deliberately no attachGuidance(...) call here

        addCardToZone("Runeclaw Bear", ai, ZoneType.Hand);
        addCards("Forest", 2, ai);

        moveToMain2(game, ai);
        gameLoopUntilNextPhase(game);

        AssertJUnit.assertEquals(
                "With no ai_guidance profile attached, vanilla AI behavior must be unchanged",
                1, countCardsWithName(game, "Runeclaw Bear", ZoneType.Battlefield));
    }
}
