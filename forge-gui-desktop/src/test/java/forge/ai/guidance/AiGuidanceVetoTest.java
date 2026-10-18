package forge.ai.guidance;

import forge.ai.AITest;
import forge.ai.ComputerUtilCard;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Veto-only target guidance: {@code target.is_commander}, {@code target.controller} (incl.
 * {@code leading_opponent}), {@code target.name}, fail-closed vetoes, and the pick made among the
 * survivors when a rule has no ladder (or its ladder matches none of them).
 *
 * <p>Same approach as {@link AiGuidanceTargetRankingTest}: call
 * {@link ComputerUtilCard#getBestRemovalTargetAI(Player, Iterable, SpellAbility)} directly with a
 * real Swords to Plowshares {@link SpellAbility}; never assert on the post-turn board.</p>
 *
 * <p>Ghalta (12/12) always outranks Runeclaw Bear (2/2) by vanilla removal priority, so "the
 * vanilla pick" is Ghalta and "the weakest" is the Bear.</p>
 */
public class AiGuidanceVetoTest extends AITest {

    private static final String SWORDS = "Swords to Plowshares";

    /** Writes a decklist spec whose only rule is {@code source_card: Swords to Plowshares}. */
    private static File spec(String vetoesJson, String ladderJson) {
        String json = "{\"deck_rules\":{\"ai_guidance\":{"
                + "\"canonical_threat_catalog\":{\"tier_2_engine\":[\"Korvold, Fae-Cursed King\"]},"
                + "\"target_rankings\":[{\"source_card\":\"" + SWORDS + "\","
                + "\"vetoes\":[" + vetoesJson + "],"
                + "\"evaluation_ladder\":[" + ladderJson + "]}]}}}";
        try {
            File f = File.createTempFile("veto_spec", ".json");
            f.deleteOnExit();
            Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
            return f;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String veto(String field, String op, String valueJson) {
        return "{\"condition\":{\"field\":\"" + field + "\",\"op\":\"" + op + "\",\"value\":" + valueJson + "}}";
    }

    private void attach(Player ai, File spec) {
        Deck deck = new Deck();
        deck.setDecklistSpecPath(spec.getAbsolutePath());
        ((forge.ai.PlayerControllerAi) ai.getController()).getAi().initGuidanceProfile(deck);
    }

    private SpellAbility swords(Player ai) {
        Card card = createCard(SWORDS, ai);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        return sa;
    }

    private static CardCollection list(Card... cards) {
        CardCollection c = new CardCollection();
        for (Card card : cards) {
            c.add(card);
        }
        return c;
    }

    @Test
    public void vetoOnCommanderRemovesTheOpponentCommander() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attach(ai, spec(veto("target.is_commander", "==", "true"), ""));

        Card ghalta = addCard("Ghalta, Primal Hunger", opponent);
        ghalta.setCommander(true);
        Card bear = addCard("Runeclaw Bear", opponent);
        CardCollection candidates = list(ghalta, bear);

        AssertJUnit.assertEquals("sanity: without guidance the vanilla pick is the big commander",
                ghalta, ComputerUtilCard.getBestRemovalTargetAI(ai, candidates));
        AssertJUnit.assertEquals("the commander is vetoed, the Bear is all that is left",
                bear, ComputerUtilCard.getBestRemovalTargetAI(ai, candidates, swords(ai)));
    }

    @Test
    public void vetoOnCommanderAloneYieldsNoTarget() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attach(ai, spec(veto("target.is_commander", "==", "true"), ""));

        Card ghalta = addCard("Ghalta, Primal Hunger", opponent);
        ghalta.setCommander(true);

        AssertJUnit.assertNull(ComputerUtilCard.getBestRemovalTargetAI(ai, list(ghalta), swords(ai)));
    }

    @Test
    public void vetoOnOpponentControllerLeavesOwnPermanents() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attach(ai, spec(veto("target.controller", "==", "\"opponent\""), ""));

        Card oppGhalta = addCard("Ghalta, Primal Hunger", opponent);
        Card ownBear = addCard("Runeclaw Bear", ai);

        AssertJUnit.assertEquals("the opponent's permanent is vetoed; the AI's own is not",
                ownBear, ComputerUtilCard.getBestRemovalTargetAI(ai, list(oppGhalta, ownBear), swords(ai)));
    }

    @Test
    public void vetoOnOwnControllerLeavesOpponentPermanents() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attach(ai, spec(veto("target.controller", "==", "\"own\""), ""));

        Card ownGhalta = addCard("Ghalta, Primal Hunger", ai);
        Card oppBear = addCard("Runeclaw Bear", opponent);

        AssertJUnit.assertEquals(oppBear,
                ComputerUtilCard.getBestRemovalTargetAI(ai, list(ownGhalta, oppBear), swords(ai)));
    }

    @Test
    public void vetoOnLeadingOpponentUsesTheStrongestBoardInAThreePlayerGame() {
        Game game = initAndCreateThreePlayerGame();
        Player first = game.getPlayers().get(0);
        Player ai = game.getPlayers().get(1);
        Player third = game.getPlayers().get(2);
        attach(ai, spec(veto("target.controller", "==", "\"leading_opponent\""), ""));

        Card weakOpponentCard = addCard("Runeclaw Bear", first);
        Card leaderCard = addCard("Ghalta, Primal Hunger", third); // third has the stronger board

        AssertJUnit.assertEquals("the leading opponent's permanent is vetoed",
                weakOpponentCard,
                ComputerUtilCard.getBestRemovalTargetAI(ai, list(weakOpponentCard, leaderCard), swords(ai)));
    }

    @Test
    public void vetoOnNamedCard() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attach(ai, spec(veto("target.name", "==", "\"Ghalta, Primal Hunger\""), ""));

        Card ghalta = addCard("Ghalta, Primal Hunger", opponent);
        Card bear = addCard("Runeclaw Bear", opponent);

        AssertJUnit.assertEquals(bear, ComputerUtilCard.getBestRemovalTargetAI(ai, list(ghalta, bear), swords(ai)));
    }

    @Test
    public void vetoOnlyRuleFallsBackToTheVanillaBestSurvivorNotTheWeakest() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        // The veto matches nothing in play, so both candidates survive and there is no ladder.
        attach(ai, spec(veto("target.name", "==", "\"Nothing In Play\""), ""));

        Card ghalta = addCard("Ghalta, Primal Hunger", opponent);
        Card bear = addCard("Runeclaw Bear", opponent);
        CardCollection candidates = list(ghalta, bear);

        Card guided = ComputerUtilCard.getBestRemovalTargetAI(ai, candidates, swords(ai));

        AssertJUnit.assertEquals("a veto-only rule must pick like vanilla removal (highest priority)",
                ComputerUtilCard.getBestRemovalTargetAI(ai, candidates), guided);
        AssertJUnit.assertEquals(ghalta, guided);
    }

    @Test
    public void ladderMatchingNoSurvivorAlsoFallsBackToTheVanillaBest() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        String ladder = "{\"condition\":{\"field\":\"target.role\",\"op\":\"==\",\"value\":\"engine_core\"},\"score\":60}";
        attach(ai, spec("", ladder)); // no card has the role, so no step matches

        Card ghalta = addCard("Ghalta, Primal Hunger", opponent);
        Card bear = addCard("Runeclaw Bear", opponent);

        AssertJUnit.assertEquals(ghalta, ComputerUtilCard.getBestRemovalTargetAI(ai, list(ghalta, bear), swords(ai)));
    }

    @Test
    public void vetoWithAnUnknownFieldFailsClosed() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attach(ai, spec(veto("target.is_comander", "==", "true"), "")); // typo

        Card ghalta = addCard("Ghalta, Primal Hunger", opponent);
        Card bear = addCard("Runeclaw Bear", opponent);

        AssertJUnit.assertNull("a broken veto keeps protecting: every candidate is vetoed, the spell is held",
                ComputerUtilCard.getBestRemovalTargetAI(ai, list(ghalta, bear), swords(ai)));
    }

    @Test
    public void vetoWithAnInvalidControllerValueFailsClosed() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attach(ai, spec(veto("target.controller", "==", "\"opponnet\""), "")); // typo

        Card bear = addCard("Runeclaw Bear", opponent);

        AssertJUnit.assertNull(ComputerUtilCard.getBestRemovalTargetAI(ai, list(bear), swords(ai)));
    }

    @Test
    public void unknownFieldInsideNoneOfInAVetoStillFailsClosed() {
        // none_of would turn a leaf "false" into "true"; validation must look inside combinators.
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        String vetoJson = "{\"condition\":{\"none_of\":[{\"field\":\"target.bogus\",\"op\":\"==\",\"value\":true}]}}";
        attach(ai, spec(vetoJson, ""));

        Card bear = addCard("Runeclaw Bear", opponent);

        AssertJUnit.assertNull(ComputerUtilCard.getBestRemovalTargetAI(ai, list(bear), swords(ai)));
    }

    @Test
    public void validateReportsOnlyRealProblems() {
        com.google.gson.JsonObject ok = com.google.gson.JsonParser.parseString(
                "{\"all_of\":[{\"field\":\"target.is_commander\",\"op\":\"==\",\"value\":true},"
                + "{\"field\":\"target.controller\",\"op\":\"!=\",\"value\":\"own\"}]}").getAsJsonObject();
        AssertJUnit.assertTrue(PredicateEvaluator.validate(ok).isEmpty());

        com.google.gson.JsonObject bad = com.google.gson.JsonParser.parseString(
                "{\"any_of\":[{\"field\":\"nope\",\"op\":\"==\",\"value\":1}]}").getAsJsonObject();
        AssertJUnit.assertEquals(1, PredicateEvaluator.validate(bad).size());
    }
}
