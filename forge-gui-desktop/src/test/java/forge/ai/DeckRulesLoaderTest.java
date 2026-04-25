package forge.ai;

import forge.deck.DeckRulesConfig;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link DeckRulesLoader} — JSON parsing of Commander Decklist Notation.
 */
public class DeckRulesLoaderTest {

    private static final String EXAMPLE_JSON = "mtg-replay-notation/examples/commander-decklist.json";

    @Test
    public void testLoadFromExampleFile() throws IOException {
        // Resolve relative to project root
        File projectRoot = findProjectRoot();
        if (projectRoot == null) {
            // Skip if running from unexpected directory
            return;
        }

        File jsonFile = new File(projectRoot, EXAMPLE_JSON);
        if (!jsonFile.exists()) {
            // Skip gracefully if submodule not initialized
            System.out.println("Skipping: " + jsonFile + " not found (submodule may not be initialized)");
            return;
        }

        DeckRulesConfig config = DeckRulesLoader.loadFromFile(jsonFile);
        assertNotNull(config, "Config should not be null");

        // Mulligan
        assertTrue(config.hasMulligan(), "Should have mulligan config");
        DeckRulesConfig.MulliganConfig mc = config.getMulligan();
        assertNotNull(mc.getCardValues());
        assertEquals(mc.getCardValues().getLand(), 1.0);
        assertEquals(mc.getCardValues().getCmc0To2(), 0.8);
        assertFalse(mc.getThresholds().isEmpty(), "Should have thresholds");
        assertFalse(mc.getCardOverrides().isEmpty(), "Should have card overrides");

        // Verify Sol Ring override
        boolean foundSolRing = false;
        for (DeckRulesConfig.MulliganConfig.CardOverride co : mc.getCardOverrides()) {
            if ("Sol Ring".equals(co.getName())) {
                assertEquals(co.getValue(), 1.2);
                foundSolRing = true;
            }
        }
        assertTrue(foundSolRing, "Should have Sol Ring override");

        // Combos
        assertTrue(config.hasCombos(), "Should have combos");
        assertFalse(config.getCombos().isEmpty());
        DeckRulesConfig.ComboDeclaration firstCombo = config.getCombos().get(0);
        assertNotNull(firstCombo.getId());
        assertFalse(firstCombo.getPieces().isEmpty());

        // Anti-synergies
        assertTrue(config.hasDontCombos(), "Should have dont_combos");
        assertFalse(config.getDontCombos().isEmpty());
    }

    /**
     * Walk up from CWD to find a directory containing pom.xml (project root).
     */
    private File findProjectRoot() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 5 && dir != null; i++) {
            if (new File(dir, "pom.xml").exists() && new File(dir, "forge-ai").isDirectory()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        return null;
    }
}

