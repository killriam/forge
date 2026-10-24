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

/** "Guaranteed Land Drop": AI seat, own turns 2-5, no land in hand -> the draw becomes a random mana land. */
public class GuaranteedLandDropTest extends AITest {

    private Game createGame(boolean enabled) {
        initAndCreateGame(); // initializes FModel if needed

        List<RegisteredPlayer> players = new ArrayList<>();
        RegisteredPlayer rp0 = new RegisteredPlayer(new Deck("D0")).setPlayer(new LobbyPlayerAi("p0", null));
        rp0.setGuaranteedLandDrop(enabled);
        players.add(rp0);
        players.add(new RegisteredPlayer(new Deck("D1")).setPlayer(new LobbyPlayerAi("p1", null)));

        GameRules rules = new GameRules(GameType.Constructed);
        Game game = new Game(players, rules, new Match(rules, players, "GuaranteedLandDropTest"));
        Player p0 = game.getPlayers().get(0);
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p0);
        game.getPhaseHandler().onStackResolved();
        return game;
    }

    private Player prepared(Game game, int turn, boolean landInHand) {
        Player p0 = game.getPlayers().get(0);
        while (p0.getTurn() < turn) {
            p0.incrementTurn();
        }
        for (int i = 0; i < 20; i++) {
            addCardToZone("Grizzly Bears", p0, ZoneType.Library);
        }
        addCardToZone("Forest", p0, ZoneType.Library);
        addCardToZone("Mountain", p0, ZoneType.Library);
        addCardToZone(landInHand ? "Island" : "Grizzly Bears", p0, ZoneType.Hand);
        return p0;
    }

    private static Card top(Player p) {
        return p.getZone(ZoneType.Library).get(0);
    }

    @Test
    public void swapsInAManaLandWhenHandHasNone() {
        for (int turn = 2; turn <= 5; turn++) {
            Game game = createGame(true);
            Player p0 = prepared(game, turn, false);
            assertTrue("turn " + turn, p0.ensureGuaranteedLandDrop());
            assertTrue("turn " + turn + " top is a land", top(p0).isLand());
            assertEquals(22, p0.getZone(ZoneType.Library).size());
        }
    }

    @Test
    public void doesNothingOutsideTurnsTwoToFive() {
        for (int turn : new int[] {1, 6, 9}) {
            Game game = createGame(true);
            Player p0 = prepared(game, turn, false);
            assertFalse("turn " + turn, p0.ensureGuaranteedLandDrop());
            assertFalse(top(p0).isLand());
        }
    }

    @Test
    public void doesNothingWhenHandAlreadyHasALand() {
        Game game = createGame(true);
        Player p0 = prepared(game, 3, true);
        assertFalse(p0.ensureGuaranteedLandDrop());
        assertFalse(top(p0).isLand());
    }

    @Test
    public void doesNothingWhenDisabled() {
        Game game = createGame(false);
        Player p0 = prepared(game, 3, false);
        assertFalse(p0.ensureGuaranteedLandDrop());
        assertFalse(top(p0).isLand());
    }

    @Test
    public void skipsLandsWithoutAManaAbilityAndSurvivesNoCandidate() {
        Game game = createGame(true);
        Player p0 = game.getPlayers().get(0);
        while (p0.getTurn() < 3) {
            p0.incrementTurn();
        }
        for (int i = 0; i < 10; i++) {
            addCardToZone("Grizzly Bears", p0, ZoneType.Library);
        }
        addCardToZone("Maze of Ith", p0, ZoneType.Library); // a land with no mana ability
        assertFalse("no mana land in library -> nothing changes", p0.ensureGuaranteedLandDrop());
        assertFalse(top(p0).isLand());
    }
}
