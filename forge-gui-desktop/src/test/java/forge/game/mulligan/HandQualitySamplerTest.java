package forge.game.mulligan;

import forge.ai.AITest;
import forge.deck.Deck;
import forge.deck.DeckRulesConfig;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Sanity tests for {@link HandQualitySampler} - the deck-select "Target Hand Quality" picker's
 * percentile engine (§6.1.6, sampled since exact enumeration is combinatorially infeasible).
 */
public class HandQualitySamplerTest extends AITest {

    @Test
    public void testPercentiles_areMonotonicAndBestIsMax() {
        initAndCreateGame();

        Deck deck = new Deck("Sampler Test Deck");
        addBasics(deck, "Plains", 20);
        addBasics(deck, "Forest", 20);
        addBasics(deck, "Sol Ring", 10);
        addBasics(deck, "Giant Spider", 10);
        addBasics(deck, "Gray Ogre", 10);
        addBasics(deck, "Grizzly Bears", 10);
        addBasics(deck, "Elvish Mystic", 10);

        DecklistMulliganEvaluator evaluator = DecklistMulliganEvaluator.fromDeckRules(
                DeckRulesConfig.MulliganConfig.createDefault(), deck);

        HandQualitySampler.Percentiles p = HandQualitySampler.compute(deck, evaluator);

        assertTrue(p.q1 <= p.q2, "q1 <= q2");
        assertTrue(p.q2 <= p.q3, "q2 <= q3");
        assertTrue(p.q3 <= p.q4, "q3 <= q4");
        assertTrue(p.q4 <= p.best, "q4 <= best");
        assertTrue(p.best > 0, "A deck full of lands and cheap spells should score above zero");
    }

    private void addBasics(Deck deck, String name, int qty) {
        PaperCard pc = forge.StaticData.instance().getCommonCards().getCard(name);
        deck.getMain().add(pc, qty);
    }
}
