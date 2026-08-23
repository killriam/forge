package forge.ai.guidance;

import com.google.common.eventbus.Subscribe;
import forge.ai.AITest;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.event.GameEventAiGuidanceDecision;
import forge.game.log.ReplayNotationExporter;
import forge.game.log.model.L2Unit;
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
 * End-to-end proof that a guidance decision (a deployment guard blocking a card, or a
 * {@code target_rankings} veto/selection) actually reaches the replay JSON's L2
 * ({@code views_l2[].annotations.guidanceDecisions}) — the "Coaching Pipeline" §12.2's 4th hook
 * and ai-play-guidance-spec.md's Stage 5 describe, and forge-integration-guide.md §12.6.3/§12.7.3
 * both listed as deferred until now. See forge-integration-guide.md §12.8 for the design.
 *
 * <p>Two ways of observing the same fired {@link GameEventAiGuidanceDecision}, both exercised
 * here: (1) a plain Guava {@code EventBus} subscriber registered directly on the game, which
 * proves the event carries the right structured fields regardless of anything downstream; (2)
 * wiring a real {@link ReplayNotationExporter} into the game's already-registered
 * {@code GameLogFormatter} (the exact production wiring path — {@code Game}'s own constructor
 * auto-registers {@code gameLog.getEventVisitor()}, confirmed by reading {@code Game.java}, not
 * assumed) and checking the resulting {@link L2Unit}, driven via {@code onTurnBegin()}'s real
 * turn-flush logic rather than a full simulated game loop — deliberately, following the same
 * "call the changed production method directly" lesson from §12.7.4's flakiness finding.</p>
 */
public class AiGuidanceDecisionLoggingTest extends AITest {

    /** Captures every {@link GameEventAiGuidanceDecision} posted to the game's event bus. */
    private static final class Capture {
        final List<GameEventAiGuidanceDecision> events = new ArrayList<>();

        @Subscribe
        public void on(GameEventAiGuidanceDecision event) {
            events.add(event);
        }
    }

    private static File fixture(String name) {
        URL url = AiGuidanceDecisionLoggingTest.class.getClassLoader().getResource("ai_guidance/" + name);
        AssertJUnit.assertNotNull("Test fixture ai_guidance/" + name + " not found on test classpath", url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private AiGuidanceProfile loadProfile(String fixtureName) {
        Deck deck = new Deck();
        deck.setDecklistSpecPath(fixture(fixtureName).getAbsolutePath());
        return forge.ai.DeckRulesLoader.loadAiGuidanceIfNeeded(deck);
    }

    @Test
    public void deploymentGuardBlockFiresAStructuredEvent() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);

        AiGuidanceProfile profile = loadProfile("multiplier_guard.json");
        // Tagged "multiplier" in this fixture (renamed from Sol Ring during Slice 1's debugging,
        // §12.6.4 - a vanilla creature has no "why cast it" ambiguity for the AI to second-guess,
        // though that's irrelevant here since passesDeploymentGuard() is called directly and
        // doesn't touch that decision layer at all).
        Card runeclawBear = createCard("Runeclaw Bear", ai); // not on the battlefield - guard just needs a Card + empty board context

        boolean passes = profile.passesDeploymentGuard(runeclawBear, ai, game);

        AssertJUnit.assertFalse("Empty board should fail the multiplier_requires_board guard", passes);
        AssertJUnit.assertEquals("Exactly one guidance event should have fired", 1, capture.events.size());
        GameEventAiGuidanceDecision fired = capture.events.get(0);
        AssertJUnit.assertEquals("deployment_guard_blocked", fired.decisionType());
        AssertJUnit.assertEquals("Runeclaw Bear", fired.cardName());
        AssertJUnit.assertEquals(ai.getName(), fired.playerName());
        AssertJUnit.assertEquals("multiplier", fired.ruleId());
        AssertJUnit.assertEquals("Avoid deploying a multiplier onto an empty, non-functioning board.", fired.reason());
    }

    @Test
    public void targetVetoFiresAStructuredEventWithTheAuthoredReason() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);

        AiGuidanceProfile profile = loadProfile("swords_target_ranking.json");
        Card colossus = addCard("Darksteel Colossus", opponent);
        Card korvold = addCard("Korvold, Fae-Cursed King", opponent);
        CardCollection candidates = new CardCollection();
        candidates.add(colossus);
        candidates.add(korvold);

        Card card = createCard("Swords to Plowshares", ai);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);

        Card chosen = profile.chooseGuidedRemovalTarget(sa, ai, game, candidates);

        AssertJUnit.assertEquals(korvold, chosen);
        AssertJUnit.assertEquals("Exactly one guidance event should have fired (the final selection)",
                1, capture.events.size());
        GameEventAiGuidanceDecision fired = capture.events.get(0);
        AssertJUnit.assertEquals("target_selected", fired.decisionType());
        AssertJUnit.assertEquals("Korvold, Fae-Cursed King", fired.cardName());
        AssertJUnit.assertEquals(Integer.valueOf(70), fired.scoreDelta());
        AssertJUnit.assertEquals("Opponent engine hubs that generate compounding value", fired.ruleId());
    }

    @Test
    public void guidanceDecisionReachesTheL2ReplayAnnotations() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);

        // Wire a real ReplayNotationExporter into the exact production path: Game's own
        // constructor already registered gameLog.getEventVisitor() (a GameLogFormatter) as an
        // event subscriber (confirmed in Game.java, not assumed) - this just gives that already-
        // live formatter somewhere to forward structured decisions to, the same call
        // mamo-Connector/SimulateMatch's real replay-recording path makes.
        ReplayNotationExporter exporter = new ReplayNotationExporter(game);
        game.getGameLog().getEventVisitor().setReplayExporter(exporter);

        AiGuidanceProfile profile = loadProfile("multiplier_guard.json");
        Card runeclawBear = createCard("Runeclaw Bear", ai); // tagged "multiplier" in this fixture

        exporter.onTurnBegin(1, ai);
        profile.passesDeploymentGuard(runeclawBear, ai, game); // fires the event -> GameLogFormatter -> exporter.logGuidanceDecision()
        exporter.onTurnBegin(2, ai); // flushes turn 1: generates its L2Unit

        List<L2Unit> units = exporter.getReplayLog().getViewsL2();
        AssertJUnit.assertEquals("Exactly one L2 unit (turn 1) should have been generated", 1, units.size());

        List<L2Unit.Annotations.GuidanceDecision> decisions = units.get(0).getAnnotations().getGuidanceDecisions();
        AssertJUnit.assertEquals("Turn 1's L2 unit should carry exactly the one guidance decision made during it",
                1, decisions.size());
        L2Unit.Annotations.GuidanceDecision decision = decisions.get(0);
        AssertJUnit.assertEquals("deployment_guard_blocked", decision.getDecisionType());
        AssertJUnit.assertEquals("Runeclaw Bear", decision.getCardName());
        AssertJUnit.assertEquals(ai.getName(), decision.getPlayer());
    }

    @Test
    public void withoutAGuidanceProfileNoEventsFireAtAll() {
        // Backward compatibility: hasTargetRankingRule()/passesDeploymentGuard() are only ever
        // called when guidanceProfile != null (see the null-guard in AiController's hook and
        // ComputerUtilCard.getBestRemovalTargetAI's guidance branch) - this test exists to pin
        // down that AiGuidanceProfile itself never fires an event when nothing in the profile
        // actually applies to the card in question, not just that the caller happens not to ask.
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);

        AiGuidanceProfile empty = AiGuidanceProfile.parse(null);
        Card runeclawBear = createCard("Runeclaw Bear", ai); // no role_bindings entry in an empty profile

        boolean passes = empty.passesDeploymentGuard(runeclawBear, ai, game);

        AssertJUnit.assertTrue("A card with no declared role should always pass the guard", passes);
        AssertJUnit.assertTrue("No guidance decision applied - no event should have fired",
                capture.events.isEmpty());
    }
}
