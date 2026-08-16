package forge.gamemodes.match;

import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.player.RegisteredPlayer;
import forge.player.LobbyPlayerHuman;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.AssertJUnit.*;

/**
 * Regression test for design decision 2 of the scenario-attach feature (see
 * {@code docs/SCENARIO_STARTING_HAND_FORMAT.md} and the "Attach a Scenario" implementation
 * plan): {@link GameLobby#buildScenarioGameRules} computes positional "P1"/"P2" scenario keys
 * using {@link HostedMatch#sortPlayersHumanFirst}, and that ordering must exactly match what
 * {@link HostedMatch#startMatch} itself will use when it builds the {@code Game} - otherwise a
 * scenario attached to a human seat sitting in a raw slot index after an AI seat would silently
 * apply to the wrong player.
 */
public class HostedMatchSortTest {

    private static RegisteredPlayer human(String name) {
        return new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerHuman(name));
    }

    private static RegisteredPlayer ai(String name) {
        return new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi(name, Set.of()));
    }

    @Test
    public void testSortPlayersHumanFirst_movesHumanFromSlot1ToIndex0() {
        RegisteredPlayer aiSlot0 = ai("AI 1");
        RegisteredPlayer humanSlot1 = human("killriam");

        List<RegisteredPlayer> sorted = HostedMatch.sortPlayersHumanFirst(List.of(aiSlot0, humanSlot1));

        assertSame("Human should be moved to index 0 regardless of original slot order",
                humanSlot1, sorted.get(0));
        assertSame(aiSlot0, sorted.get(1));
    }

    @Test
    public void testSortPlayersHumanFirst_humanAlreadyFirstStaysFirst() {
        RegisteredPlayer humanSlot0 = human("killriam");
        RegisteredPlayer aiSlot1 = ai("AI 1");

        List<RegisteredPlayer> sorted = HostedMatch.sortPlayersHumanFirst(List.of(humanSlot0, aiSlot1));

        assertSame(humanSlot0, sorted.get(0));
        assertSame(aiSlot1, sorted.get(1));
    }

    @Test
    public void testSortPlayersHumanFirst_doesNotMutateInputList() {
        RegisteredPlayer aiSlot0 = ai("AI 1");
        RegisteredPlayer humanSlot1 = human("killriam");
        List<RegisteredPlayer> original = List.of(aiSlot0, humanSlot1); // immutable - would throw if sorted in place

        List<RegisteredPlayer> sorted = HostedMatch.sortPlayersHumanFirst(original);

        assertSame("Original list order must be untouched", aiSlot0, original.get(0));
        assertSame(humanSlot1, sorted.get(0));
    }

    @Test
    public void testSortPlayersHumanFirst_allAiOrderIsStable() {
        RegisteredPlayer ai1 = ai("AI 1");
        RegisteredPlayer ai2 = ai("AI 2");

        List<RegisteredPlayer> sorted = HostedMatch.sortPlayersHumanFirst(List.of(ai1, ai2));

        assertSame("No human present - relative AI order should be preserved", ai1, sorted.get(0));
        assertSame(ai2, sorted.get(1));
    }
}
