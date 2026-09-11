package forge.ai.mulligan;

import forge.deck.DeckRulesConfig;
import forge.game.card.CardCollection;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link DecklistMulliganEvaluator} — specifically the per-round threshold
 * fallback used when a deck's mulligan config doesn't list every round.
 */
public class DecklistMulliganEvaluatorTest {

    @Test
    public void testUnlistedRound_fallsBackToStandardDefault_notAlwaysKeep() {
        // Deck config only specifies a threshold for round 0 - as would happen with a .dck
        // file's "MulliganThreshold$0:3.5" alone.
        DeckRulesConfig.MulliganConfig mc = new DeckRulesConfig.MulliganConfig();
        List<DeckRulesConfig.MulliganConfig.Threshold> thresholds = new ArrayList<>();
        thresholds.add(new DeckRulesConfig.MulliganConfig.Threshold(0, 7, 3.5, null));
        mc.setThresholds(thresholds);

        DecklistMulliganEvaluator evaluator = DecklistMulliganEvaluator.fromDeckRules(mc);
        assertNotNull(evaluator);

        CardCollection emptyHand = new CardCollection(); // hand value = 0.0

        // Rounds 1-3 have no explicit threshold - must fall back to the spec's standard
        // defaults (3.0/2.5/2.0), not silently always-keep (0.0), so a worthless hand mulligans.
        assertFalse(evaluator.shouldKeep(emptyHand, 1), "Round 1 should fall back to default threshold 3.0");
        assertFalse(evaluator.shouldKeep(emptyHand, 2), "Round 2 should fall back to default threshold 2.5");
        assertFalse(evaluator.shouldKeep(emptyHand, 3), "Round 3 should fall back to default threshold 2.0");

        // Round 4+ has no spec default either - correctly stays "always keep".
        assertTrue(evaluator.shouldKeep(emptyHand, 4), "Round 4+ has no default and should always keep");
    }

    @Test
    public void testExplicitThreshold_stillOverridesStandardDefault() {
        DeckRulesConfig.MulliganConfig mc = new DeckRulesConfig.MulliganConfig();
        List<DeckRulesConfig.MulliganConfig.Threshold> thresholds = new ArrayList<>();
        thresholds.add(new DeckRulesConfig.MulliganConfig.Threshold(1, 6, 0.0, null));
        mc.setThresholds(thresholds);

        DecklistMulliganEvaluator evaluator = DecklistMulliganEvaluator.fromDeckRules(mc);
        CardCollection emptyHand = new CardCollection();

        // An explicit 0.0 threshold for round 1 means always keep, even though the spec
        // default for round 1 is 3.0 - an explicit entry always wins over the fallback.
        assertTrue(evaluator.shouldKeep(emptyHand, 1));
    }
}
