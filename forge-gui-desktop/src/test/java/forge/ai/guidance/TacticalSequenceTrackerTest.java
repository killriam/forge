package forge.ai.guidance;

import com.google.common.eventbus.Subscribe;
import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.ai.PlayerControllerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.event.GameEventAiGuidanceDecision;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link TacticalSequenceTracker} — the {@code ai_guidance play_preferences
 * .tactical_sequences[]} state machine (mtg-replay-notation/spec/ai-play-guidance-spec.md §6.2).
 * See forge-integration-guide.md §12.9 for the full design, including why this is a new,
 * independent mechanism rather than an extension of {@code GameRules.forcedPlaySequence}.
 *
 * <p>Most tests call {@link TacticalSequenceTracker#desiredRoleFor}/{@link
 * TacticalSequenceTracker#onCardCast} directly against a real {@code Player}/{@code Game} (via
 * {@link AITest}) — the state-machine logic itself, deterministic and fast, following the
 * §12.7.4 lesson of testing the changed unit directly rather than a full priority-loop turn. One
 * test ({@link #realCastAdvancesTheControllersOwnTracker}) exercises the actual production event
 * wiring end to end by really casting a card through {@link ComputerUtil#handlePlayingSpellAbility}
 * (real stack push, real {@code GameEventSpellAbilityCast}), rather than the AI's own "should I
 * cast this" decision layer — sidestepping that same layer's independent, unrelated flakiness
 * (§12.7.4) while still proving the real {@code AiController.onGuidanceRelevantCast} subscription
 * actually receives and acts on a genuine engine event.</p>
 */
public class TacticalSequenceTrackerTest extends AITest {

    private static final class Capture {
        final List<GameEventAiGuidanceDecision> events = new ArrayList<>();

        @Subscribe
        public void on(GameEventAiGuidanceDecision event) {
            events.add(event);
        }
    }

    private static File fixture(String name) {
        URL url = TacticalSequenceTrackerTest.class.getClassLoader().getResource("ai_guidance/" + name);
        AssertJUnit.assertNotNull("Test fixture ai_guidance/" + name + " not found on test classpath", url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private AiGuidanceProfile loadProfile() {
        Deck deck = new Deck();
        deck.setDecklistSpecPath(fixture("tactical_sequence.json").getAbsolutePath());
        return forge.ai.DeckRulesLoader.loadAiGuidanceIfNeeded(deck);
    }

    @Test
    public void triggerActivatesAndReturnsStageOneRole() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);
        AiGuidanceProfile profile = loadProfile();
        // Empty battlefield -> battlefield.creatures.count == 0 -> trigger fires

        TacticalSequenceTracker tracker = new TacticalSequenceTracker();
        String desired = tracker.desiredRoleFor(profile, ai, game);

        AssertJUnit.assertEquals("enabler", desired);
        AssertJUnit.assertEquals("bait_then_commit", tracker.getActiveSequenceId());
        AssertJUnit.assertEquals(1, capture.events.size());
        AssertJUnit.assertEquals("tactical_sequence_started", capture.events.get(0).decisionType());
        AssertJUnit.assertEquals("bait_then_commit", capture.events.get(0).ruleId());
    }

    @Test
    public void noTriggerMeansNoDesiredRoleAndNoEvent() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);
        AiGuidanceProfile profile = loadProfile();
        addCard("Runeclaw Bear", ai); // battlefield.creatures.count == 1 -> trigger (== 0) is false

        TacticalSequenceTracker tracker = new TacticalSequenceTracker();
        String desired = tracker.desiredRoleFor(profile, ai, game);

        AssertJUnit.assertNull(desired);
        AssertJUnit.assertNull(tracker.getActiveSequenceId());
        AssertJUnit.assertTrue(capture.events.isEmpty());
    }

    @Test
    public void onCardCastAdvancesToStageTwo() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);
        AiGuidanceProfile profile = loadProfile();
        TacticalSequenceTracker tracker = new TacticalSequenceTracker();

        tracker.desiredRoleFor(profile, ai, game); // activates, stage 1 ("enabler")
        tracker.onCardCast("Elvish Visionary", profile, ai, game); // matches stage 1's role -> advances

        String desired = tracker.desiredRoleFor(profile, ai, game); // no abort_if triggered (no opponent mana out)
        AssertJUnit.assertEquals("engine_core", desired);
        AssertJUnit.assertEquals("bait_then_commit", tracker.getActiveSequenceId());
        AssertJUnit.assertTrue("A stage_advanced event should have fired",
                capture.events.stream().anyMatch(e -> "tactical_sequence_stage_advanced".equals(e.decisionType())));
    }

    @Test
    public void unrelatedCastDoesNotAdvanceTheSequence() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        AiGuidanceProfile profile = loadProfile();
        TacticalSequenceTracker tracker = new TacticalSequenceTracker();

        tracker.desiredRoleFor(profile, ai, game); // activates, stage 1 ("enabler")
        tracker.onCardCast("Sol Ring", profile, ai, game); // no declared role in this fixture - irrelevant

        AssertJUnit.assertEquals("Casting something unrelated to the active stage must not advance it",
                "enabler", tracker.desiredRoleFor(profile, ai, game));
    }

    @Test
    public void abortIfDeactivatesTheSequence() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);
        AiGuidanceProfile profile = loadProfile();
        TacticalSequenceTracker tracker = new TacticalSequenceTracker();

        tracker.desiredRoleFor(profile, ai, game);
        tracker.onCardCast("Elvish Visionary", profile, ai, game); // now on stage 2

        addCards("Forest", 4, opponent); // opponent_open_mana == 4 -> stage 2's abort_if fires
        String desired = tracker.desiredRoleFor(profile, ai, game);

        AssertJUnit.assertNull("abort_if firing should clear the desired role", desired);
        AssertJUnit.assertNull("abort_if firing should deactivate the sequence entirely",
                tracker.getActiveSequenceId());
        AssertJUnit.assertTrue(capture.events.stream().anyMatch(e -> "tactical_sequence_aborted".equals(e.decisionType())));
    }

    @Test
    public void completingTheLastStageDeactivates() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);
        AiGuidanceProfile profile = loadProfile();
        TacticalSequenceTracker tracker = new TacticalSequenceTracker();

        tracker.desiredRoleFor(profile, ai, game);              // activate, stage 1
        tracker.onCardCast("Elvish Visionary", profile, ai, game); // -> stage 2
        tracker.onCardCast("Ashnod's Altar", profile, ai, game);   // stage 2's role -> completes

        AssertJUnit.assertNull("Completing the last stage should deactivate the sequence",
                tracker.getActiveSequenceId());
        AssertJUnit.assertTrue(capture.events.stream().anyMatch(e -> "tactical_sequence_completed".equals(e.decisionType())));
    }

    @Test
    public void stageThatNeverAdvancesGetsGivenUpOnAfterEnoughTurns() {
        // Without this, a stage whose target_role never gets cast (card never drawn, say) would
        // starve every other candidate role indefinitely for the rest of the game - see
        // forge-integration-guide.md §12.9.3's own "sequences that never resolve" caveat, closed
        // by this give-up timeout.
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Capture capture = new Capture();
        game.subscribeToEvents(capture);
        AiGuidanceProfile profile = loadProfile();
        TacticalSequenceTracker tracker = new TacticalSequenceTracker();

        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN1, ai, 1);
        AssertJUnit.assertEquals("enabler", tracker.desiredRoleFor(profile, ai, game)); // activates on turn 1, never advances

        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN1, ai, 1 + TacticalSequenceTracker.GIVE_UP_AFTER_OWN_TURNS - 1);
        AssertJUnit.assertEquals("Still within the give-up window", "enabler", tracker.desiredRoleFor(profile, ai, game));

        game.getPhaseHandler().devModeSet(forge.game.phase.PhaseType.MAIN1, ai, 1 + TacticalSequenceTracker.GIVE_UP_AFTER_OWN_TURNS);
        String desired = tracker.desiredRoleFor(profile, ai, game);

        AssertJUnit.assertNull("Should have given up by now", desired);
        AssertJUnit.assertNull(tracker.getActiveSequenceId());
        AssertJUnit.assertTrue(capture.events.stream().anyMatch(e -> "tactical_sequence_gave_up".equals(e.decisionType())));
    }

    @Test
    public void realCastAdvancesTheControllersOwnTracker() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        PlayerControllerAi controllerAi = (PlayerControllerAi) ai.getController();

        Deck deck = new Deck();
        deck.setDecklistSpecPath(fixture("tactical_sequence.json").getAbsolutePath());
        controllerAi.getAi().initGuidanceProfile(deck); // real init path: also subscribes AiController to game events

        addCards("Forest", 2, ai);
        Card visionary = addCardToZone("Elvish Visionary", ai, ZoneType.Hand);
        SpellAbility sa = visionary.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);

        AssertJUnit.assertEquals("enabler",
                controllerAi.getAi().getTacticalSequenceTracker().desiredRoleFor(
                        controllerAi.getAi().getGuidanceProfile(), ai, game));

        boolean cast = ComputerUtil.handlePlayingSpellAbility(ai, sa, null);

        AssertJUnit.assertTrue("Elvish Visionary should have been successfully cast", cast);
        AssertJUnit.assertEquals("engine_core",
                controllerAi.getAi().getTacticalSequenceTracker().desiredRoleFor(
                        controllerAi.getAi().getGuidanceProfile(), ai, game));
    }
}
