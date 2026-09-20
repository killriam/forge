package forge.game.mulligan;

import forge.ai.AITest;
import forge.deck.DeckRulesConfig;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Behavioral tests for {@link DecklistMulliganEvaluator} against the Commander Decklist
 * Notation spec §6.1 (card values curve, X-cost adjustment, Mana Base Band, Starting Hand
 * Quality's representable Mana Curve bonus). Uses real card data via {@link AITest} since the
 * evaluator scores off each card's actual {@code CardRules} (type line, mana cost, Oracle text).
 */
public class DecklistMulliganEvaluatorSpecTest extends AITest {

    private DecklistMulliganEvaluator defaultEvaluator() {
        return DecklistMulliganEvaluator.fromDeckRules(DeckRulesConfig.MulliganConfig.createDefault());
    }

    @Test
    public void testLandValue_default() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card plains = createCard("Plains", p);

        assertEquals(defaultEvaluator().scoreCard(plains), 1.0, 0.0001);
    }

    @Test
    public void testNonLandCurve_byManaValue() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        DecklistMulliganEvaluator ev = defaultEvaluator();

        assertEquals(ev.scoreCard(createCard("Sol Ring", p)), 0.8, 0.0001);      // mv1 -> mv1 bucket
        assertEquals(ev.scoreCard(createCard("Grizzly Bears", p)), 0.75, 0.0001); // mv2 -> mv2 bucket
        assertEquals(ev.scoreCard(createCard("Gray Ogre", p)), 0.6, 0.0001);      // mv3 -> mv3 bucket
        assertEquals(ev.scoreCard(createCard("Giant Spider", p)), 0.2, 0.0001);   // mv4 -> mv4 bucket (flat 0.2, v1.4.0)
    }

    @Test
    public void testXCostAdjustment_scoresAsEffectiveMv3NotRealMv1() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Card fireball = createCard("Fireball", p); // ManaCost "X R" -> real mv1, effective mv3 per §6.1.1a

        assertEquals(fireball.getCMC(), 1, "Fireball's real mana value is 1 by convention (X=0)");
        assertEquals(defaultEvaluator().scoreCard(fireball), 0.6, 0.0001, "Should score as mv3 (0.6), not mv1 (0.8)");
    }

    @Test
    public void testCardOverride_winsOverCurve() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        DeckRulesConfig.MulliganConfig mc = DeckRulesConfig.MulliganConfig.createDefault();
        mc.getCardOverrides().add(new DeckRulesConfig.MulliganConfig.CardOverride("Sol Ring", 1.2, "best t1 play"));
        DecklistMulliganEvaluator ev = DecklistMulliganEvaluator.fromDeckRules(mc);

        assertEquals(ev.scoreCard(createCard("Sol Ring", p)), 1.2, 0.0001);
    }

    @Test
    public void testManaBaseScore_categories() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        DecklistMulliganEvaluator ev = defaultEvaluator();

        assertEquals(ev.manaBaseScore(createCard("Plains", p)), 1.0, 0.0001, "Basic land");
        assertEquals(ev.manaBaseScore(createCard("Sol Ring", p)), 0.9, 0.0001, "mv1 mana rock");
        assertEquals(ev.manaBaseScore(createCard("Maze of Ith", p)), 0.0, 0.0001, "True non-mana utility land");
        assertEquals(ev.manaBaseScore(createCard("Giant Spider", p)), 0.0, 0.0001, "mv4+ non-mana-producer");
    }

    @Test
    public void testPlayableAndGoodAiHand_defaultBand() {
        DecklistMulliganEvaluator ev = defaultEvaluator();
        assertEquals(ev.getManaBaseMin(), 3.0, 0.0001);
        assertEquals(ev.getManaBaseMax(), 4.0, 0.0001);

        assertTrue(ev.isPlayable(3.0));
        assertFalse(ev.isPlayable(2.9));
        assertTrue(ev.isGoodAiHand(3.5));
        assertTrue(ev.isPlayable(4.5), "Flooded hand is still playable");
        assertFalse(ev.isGoodAiHand(4.5), "...but not a Good AI hand");
    }

    @Test
    public void testManaCurveBonus_requiresOnePermanentAtEachOfMv1Through4() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        DecklistMulliganEvaluator ev = defaultEvaluator();

        List<Card> fullCurve = Arrays.asList(
                createCard("Elvish Mystic", p),  // mv1
                createCard("Grizzly Bears", p),  // mv2
                createCard("Gray Ogre", p),      // mv3
                createCard("Giant Spider", p));  // mv4
        assertTrue(ev.manaCurveBonusAchieved(fullCurve));
        assertEquals(ev.startingHandQuality(fullCurve), ev.evaluateHand(fullCurve) + 0.5, 0.0001);

        List<Card> missingMv3 = Arrays.asList(
                createCard("Elvish Mystic", p),
                createCard("Grizzly Bears", p),
                createCard("Giant Spider", p));
        assertFalse(ev.manaCurveBonusAchieved(missingMv3));
        assertEquals(ev.startingHandQuality(missingMv3), ev.evaluateHand(missingMv3), 0.0001);
    }

    @Test
    public void testBreakdown_sortedHighestValueFirst() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        DecklistMulliganEvaluator ev = defaultEvaluator();

        List<Card> hand = Arrays.asList(createCard("Giant Spider", p), createCard("Plains", p));
        List<DecklistMulliganEvaluator.CardScore> breakdown = ev.breakdown(hand);

        assertEquals(breakdown.size(), 2);
        assertEquals(breakdown.get(0).name, "Plains", "Land (1.0) should sort above Giant Spider (0.2)");
        assertEquals(breakdown.get(1).name, "Giant Spider");
    }
}
