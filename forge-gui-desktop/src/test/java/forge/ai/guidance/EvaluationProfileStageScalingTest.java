package forge.ai.guidance;

import com.google.common.eventbus.Subscribe;
import forge.ai.AITest;
import forge.ai.ComputerUtilCard;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.event.GameEventAiGuidanceDecision;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end proof of forge-integration-guide.md §12.11.1/§12.12's "stage modifier overlay"
 * decision: a {@code target_rankings} {@code evaluation_ladder} step that opts in via
 * {@code dimension} gets its {@code score} scaled by the current {@code evaluation_profile}
 * stage's weight for that dimension (formula: {@code score * (1 + weight)}), read off the fired
 * {@link GameEventAiGuidanceDecision}'s {@code scoreDelta} — the same event Slice 3 already wired
 * up, reused here rather than adding new plumbing to observe a score.
 *
 * <p>Fixture: {@code early} weight {@code -0.5} (100 → 50), {@code late} weight {@code 1.0}
 * (100 → 200), turn 5 in the gap between the two declared stages (100 → 100, unscaled — {@code
 * stageWeightFor} returns {@code 0.0} when no stage matches, per its own contract).</p>
 */
public class EvaluationProfileStageScalingTest extends AITest {

    private static final class Capture {
        final List<GameEventAiGuidanceDecision> events = new ArrayList<>();

        @Subscribe
        public void on(GameEventAiGuidanceDecision event) {
            events.add(event);
        }
    }

    private static File fixture(String name) {
        URL url = EvaluationProfileStageScalingTest.class.getClassLoader().getResource("ai_guidance/" + name);
        AssertJUnit.assertNotNull("Test fixture ai_guidance/" + name + " not found on test classpath", url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void attachGuidance(Player ai) {
        Deck deck = new Deck();
        deck.setDecklistSpecPath(fixture("evaluation_profile_scaling.json").getAbsolutePath());
        ((forge.ai.PlayerControllerAi) ai.getController()).getAi().initGuidanceProfile(deck);
    }

    private int guidedScoreAtTurn(int turn) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, turn);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);
        attachGuidance(ai); // real production path: getBestRemovalTargetAI() reads the profile off ai's own AiController, not a standalone object

        Card korvold = addCard("Korvold, Fae-Cursed King", opponent); // tier_2_engine
        CardCollection candidates = new CardCollection();
        candidates.add(korvold);

        Card swords = createCard("Swords to Plowshares", ai);
        SpellAbility sa = swords.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);

        Card chosen = ComputerUtilCard.getBestRemovalTargetAI(ai, candidates, sa);
        AssertJUnit.assertEquals(korvold, chosen);

        return capture.events.stream()
                .filter(e -> "target_selected".equals(e.decisionType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No target_selected event fired"))
                .scoreDelta();
    }

    @Test
    public void earlyStageScalesTheLadderScoreDown() {
        AssertJUnit.assertEquals("score 100 * (1 + -0.5) = 50", 50, guidedScoreAtTurn(2));
    }

    @Test
    public void lateStageScalesTheLadderScoreUp() {
        AssertJUnit.assertEquals("score 100 * (1 + 1.0) = 200", 200, guidedScoreAtTurn(10));
    }

    @Test
    public void turnOutsideAnyDeclaredStageLeavesScoreUnscaled() {
        AssertJUnit.assertEquals("turn 5 falls in the gap between 'early' [1,3] and 'late' [8,99] - "
                + "stageWeightFor() returns 0.0, so score is unscaled", 100, guidedScoreAtTurn(5));
    }
}
