package forge.deck;

import org.testng.annotations.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link DeckRulesConfig} — model + inline hint parsing.
 */
public class DeckRulesConfigTest {

    @Test
    public void testEmpty() {
        DeckRulesConfig config = DeckRulesConfig.empty();
        assertNotNull(config);
        assertTrue(config.isEmpty());
        assertFalse(config.hasMulligan());
        assertFalse(config.hasCombos());
        assertFalse(config.hasDontCombos());
    }

    @Test
    public void testMulliganConfigDefaults() {
        DeckRulesConfig.MulliganConfig mc = DeckRulesConfig.MulliganConfig.createDefault();
        assertNotNull(mc);
        assertEquals(mc.getThresholds().size(), 4);
        assertEquals(mc.getThresholds().get(0).getRound(), 0);
        assertEquals(mc.getThresholds().get(0).getMinValue(), 3.5);
        assertEquals(mc.getThresholds().get(3).getRound(), 3);
        assertEquals(mc.getThresholds().get(3).getMinValue(), 2.0);
    }

    @Test
    public void testCardValuesDefaults() {
        DeckRulesConfig.MulliganConfig.CardValues cv = new DeckRulesConfig.MulliganConfig.CardValues();
        assertEquals(cv.getLand(), 1.0);
        assertEquals(cv.getCmc0To2(), 0.8);
        assertEquals(cv.getCmc3(), 0.5);
        assertEquals(cv.getOther(), 0.3);
    }

    @Test
    public void testFromInlineHints_MulliganThresholds() {
        Set<String> hints = new LinkedHashSet<>();
        hints.add("MulliganThreshold$0:4.0;1:3.5;2:2.5");

        DeckRulesConfig config = DeckRulesConfig.fromInlineHints(hints);
        assertNotNull(config);
        assertTrue(config.hasMulligan());
        assertEquals(config.getMulligan().getThresholds().size(), 3);

        DeckRulesConfig.MulliganConfig.Threshold t0 = config.getMulligan().getThresholds().get(0);
        assertEquals(t0.getRound(), 0);
        assertEquals(t0.getMinValue(), 4.0);
        assertEquals(t0.getHandSize(), 7); // 7 - round
    }

    @Test
    public void testFromInlineHints_MulliganOverrides() {
        Set<String> hints = new LinkedHashSet<>();
        hints.add("MulliganOverride$Sol Ring:1.2;Doubling Season:0.6");

        DeckRulesConfig config = DeckRulesConfig.fromInlineHints(hints);
        assertNotNull(config);
        assertTrue(config.hasMulligan());
        assertEquals(config.getMulligan().getCardOverrides().size(), 2);
        assertEquals(config.getMulligan().getCardOverrides().get(0).getName(), "Sol Ring");
        assertEquals(config.getMulligan().getCardOverrides().get(0).getValue(), 1.2);
    }

    @Test
    public void testFromInlineHints_Combo() {
        Set<String> hints = new LinkedHashSet<>();
        hints.add("Combo$combo1:Doubling Season,Atraxa, Praetors' Voice");

        DeckRulesConfig config = DeckRulesConfig.fromInlineHints(hints);
        assertNotNull(config);
        assertTrue(config.hasCombos());
        assertEquals(config.getCombos().size(), 1);
        assertEquals(config.getCombos().get(0).getId(), "combo1");
        assertEquals(config.getCombos().get(0).getPieces().size(), 2);
    }

    @Test
    public void testFromInlineHints_DontCombo() {
        Set<String> hints = new LinkedHashSet<>();
        hints.add("DontCombo$dc1:Rule of Law,Thousand-Year Storm:critical");

        DeckRulesConfig config = DeckRulesConfig.fromInlineHints(hints);
        assertNotNull(config);
        assertTrue(config.hasDontCombos());
        assertEquals(config.getDontCombos().size(), 1);
        assertEquals(config.getDontCombos().get(0).getSeverity(), DeckRulesConfig.Severity.CRITICAL);
    }

    @Test
    public void testFromInlineHints_null() {
        DeckRulesConfig config = DeckRulesConfig.fromInlineHints(null);
        assertNull(config);
    }

    @Test
    public void testFromInlineHints_empty() {
        DeckRulesConfig config = DeckRulesConfig.fromInlineHints(new LinkedHashSet<>());
        assertNull(config);
    }

    @Test
    public void testFromInlineHints_noRelevantHints() {
        Set<String> hints = new LinkedHashSet<>();
        hints.add("RemAIDeck$true");
        hints.add("SideboardingPlan$something");

        DeckRulesConfig config = DeckRulesConfig.fromInlineHints(hints);
        assertNull(config);
    }

    @Test
    public void testSeverityFromString() {
        assertEquals(DeckRulesConfig.Severity.fromString(null), DeckRulesConfig.Severity.MAJOR);
        assertEquals(DeckRulesConfig.Severity.fromString("minor"), DeckRulesConfig.Severity.MINOR);
        assertEquals(DeckRulesConfig.Severity.fromString("critical"), DeckRulesConfig.Severity.CRITICAL);
        assertEquals(DeckRulesConfig.Severity.fromString("MAJOR"), DeckRulesConfig.Severity.MAJOR);
        assertEquals(DeckRulesConfig.Severity.fromString("unknown"), DeckRulesConfig.Severity.MAJOR);
    }

    @Test
    public void testComboIsWinCondition() {
        DeckRulesConfig.ComboDeclaration combo = new DeckRulesConfig.ComboDeclaration();
        assertFalse(combo.isWinCondition());

        java.util.List<String> tags = new java.util.ArrayList<>();
        tags.add("counters");
        tags.add("win-condition");
        combo.setTags(tags);
        assertTrue(combo.isWinCondition());
    }
}

