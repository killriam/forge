package forge.deck.mulligan;

import forge.card.MagicColor;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Unit tests for the pure-math parts of {@link ManaProductionUtil} - §6.1.1b's coverage
 * multiplier formula - using hand-built pip-weight maps so no real card data needs to load.
 */
public class ManaProductionUtilTest {

    @Test
    public void testCoverageMultiplier_specExample_perfectCoverage() {
        // Spec §6.1.1b example: deck pips are 60% U / 40% B, dual U/B land -> coverage 1.0 -> 1.4x
        Map<Byte, Double> weights = new HashMap<>();
        weights.put(MagicColor.BLUE, 0.6);
        weights.put(MagicColor.BLACK, 0.4);

        byte ub = (byte) (MagicColor.BLUE | MagicColor.BLACK);
        assertEquals(ManaProductionUtil.coverageMultiplier(ub, weights), 1.4, 0.0001);
    }

    @Test
    public void testCoverageMultiplier_specExample_partialCoverage() {
        // Same deck, U/R land: coverage = 0.6 + 0(R unused) = 0.6 -> 1.24x
        Map<Byte, Double> weights = new HashMap<>();
        weights.put(MagicColor.BLUE, 0.6);
        weights.put(MagicColor.BLACK, 0.4);

        byte ur = (byte) (MagicColor.BLUE | MagicColor.RED);
        assertEquals(ManaProductionUtil.coverageMultiplier(ur, weights), 1.24, 0.0001);
    }

    @Test
    public void testCoverageMultiplier_monoColorLand_noBonus() {
        Map<Byte, Double> weights = new HashMap<>();
        weights.put(MagicColor.BLUE, 1.0);
        assertEquals(ManaProductionUtil.coverageMultiplier(MagicColor.BLUE, weights), 1.0, 0.0001);
    }

    @Test
    public void testCoverageMultiplier_colorlessLand_noBonus() {
        assertEquals(ManaProductionUtil.coverageMultiplier((byte) 0, new HashMap<>()), 1.0, 0.0001);
    }

    @Test
    public void testCoverageMultiplier_noDeckPips_noBonusEvenForMulticolorLand() {
        // "If the deck has no colored pips at all, every weight is 0 and no land ever receives a bonus."
        byte wubrg = MagicColor.ALL_COLORS;
        assertEquals(ManaProductionUtil.coverageMultiplier(wubrg, new HashMap<>()), 1.0, 0.0001);
    }

    @Test
    public void testCoverageMultiplier_capsAtOneHundredPercentCoverage() {
        // A 5-color land in a mono-color-weighted deck can't exceed coverage 1.0 -> max 1.4x
        Map<Byte, Double> weights = new HashMap<>();
        weights.put(MagicColor.WHITE, 1.0);
        assertEquals(ManaProductionUtil.coverageMultiplier(MagicColor.ALL_COLORS, weights), 1.4, 0.0001);
    }

    @Test
    public void testCountColors() {
        assertEquals(ManaProductionUtil.countColors((byte) 0), 0);
        assertEquals(ManaProductionUtil.countColors(MagicColor.BLUE), 1);
        assertEquals(ManaProductionUtil.countColors((byte) (MagicColor.BLUE | MagicColor.BLACK)), 2);
        assertEquals(ManaProductionUtil.countColors(MagicColor.ALL_COLORS), 5);
    }
}
