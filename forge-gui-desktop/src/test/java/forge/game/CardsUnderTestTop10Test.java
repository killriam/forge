package forge.game;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.AssertJUnit.*;

/**
 * Tests for guaranteeing CardsUnderTest in the top 10 library positions.
 */
public class CardsUnderTestTop10Test extends AITest {

    private Game createGameWithCardsUnderTest(Deck deck, boolean guaranteeTop10) {
        initAndCreateGame(); // initializes FModel if needed

        List<RegisteredPlayer> players = new ArrayList<>();
        RegisteredPlayer rp0 = new RegisteredPlayer(deck).setPlayer(new LobbyPlayerAi("p0", null));
        rp0.setGuaranteeCardsUnderTestTop10(guaranteeTop10);
        players.add(rp0);

        RegisteredPlayer rp1 = new RegisteredPlayer(new Deck("OpponentDeck")).setPlayer(new LobbyPlayerAi("p1", null));
        players.add(rp1);

        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, players, "CardsUnderTestTop10Test");
        Game game = new Game(players, rules, match);

        Player p0 = game.getPlayers().get(0);
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p0);
        game.getPhaseHandler().onStackResolved();
        return game;
    }

    @Test
    public void testEnsureCardsUnderTestInTop10_movesCardsFromBottomToTop10() {
        Deck deck = new Deck("CutDeck");
        deck.addCardUnderTest("Sol Ring");
        deck.addCardUnderTest("Rhystic Study");

        Game game = createGameWithCardsUnderTest(deck, true);
        Player p0 = game.getPlayers().get(0);

        // Populate library with 50 Plains
        for (int i = 0; i < 50; i++) {
            addCardToZone("Plains", p0, ZoneType.Library);
        }
        // Add the two cards under test at the bottom of the library (indices 50 and 51)
        Card solRing = addCardToZone("Sol Ring", p0, ZoneType.Library);
        Card rhystic = addCardToZone("Rhystic Study", p0, ZoneType.Library);

        List<Card> libBefore = new ArrayList<>(p0.getZone(ZoneType.Library).getCards());
        assertEquals(52, libBefore.size());
        assertTrue("Sol Ring should start at index >= 10", libBefore.indexOf(solRing) >= 10);
        assertTrue("Rhystic Study should start at index >= 10", libBefore.indexOf(rhystic) >= 10);

        // Run ensureCardsUnderTestInTop10
        p0.ensureCardsUnderTestInTop10();

        List<Card> libAfter = new ArrayList<>(p0.getZone(ZoneType.Library).getCards());
        assertEquals(52, libAfter.size());

        int solRingIdx = libAfter.indexOf(solRing);
        int rhysticIdx = libAfter.indexOf(rhystic);

        assertTrue("Sol Ring should now be in top 10 (idx " + solRingIdx + ")", solRingIdx >= 0 && solRingIdx < 10);
        assertTrue("Rhystic Study should now be in top 10 (idx " + rhysticIdx + ")", rhysticIdx >= 0 && rhysticIdx < 10);
        assertFalse("Indices should be distinct", solRingIdx == rhysticIdx);
    }

    @Test
    public void testShuffle_preservesCardsUnderTestInTop10() {
        Deck deck = new Deck("CutDeck");
        deck.addCardUnderTest("Sol Ring");
        deck.addCardUnderTest("Rhystic Study");

        Game game = createGameWithCardsUnderTest(deck, true);
        Player p0 = game.getPlayers().get(0);

        for (int i = 0; i < 50; i++) {
            addCardToZone("Plains", p0, ZoneType.Library);
        }
        Card solRing = addCardToZone("Sol Ring", p0, ZoneType.Library);
        Card rhystic = addCardToZone("Rhystic Study", p0, ZoneType.Library);

        // Test multiple shuffles
        for (int s = 0; s < 10; s++) {
            p0.shuffle(null);

            List<Card> lib = new ArrayList<>(p0.getZone(ZoneType.Library).getCards());
            assertEquals(52, lib.size());

            int solRingIdx = lib.indexOf(solRing);
            int rhysticIdx = lib.indexOf(rhystic);

            assertTrue("Shuffle #" + s + ": Sol Ring should be in top 10 (idx " + solRingIdx + ")",
                    solRingIdx >= 0 && solRingIdx < 10);
            assertTrue("Shuffle #" + s + ": Rhystic Study should be in top 10 (idx " + rhysticIdx + ")",
                    rhysticIdx >= 0 && rhysticIdx < 10);
        }
    }

    @Test
    public void testEnsureCardsUnderTestInTop10_disabledDoesNothing() {
        Deck deck = new Deck("CutDeck");
        deck.addCardUnderTest("Sol Ring");

        // guaranteeTop10 = false
        Game game = createGameWithCardsUnderTest(deck, false);
        Player p0 = game.getPlayers().get(0);

        for (int i = 0; i < 50; i++) {
            addCardToZone("Plains", p0, ZoneType.Library);
        }
        Card solRing = addCardToZone("Sol Ring", p0, ZoneType.Library);

        p0.ensureCardsUnderTestInTop10();

        List<Card> lib = new ArrayList<>(p0.getZone(ZoneType.Library).getCards());
        assertEquals(51, lib.size());
        assertEquals("Sol Ring should remain at bottom when disabled", 50, lib.indexOf(solRing));
    }
}
