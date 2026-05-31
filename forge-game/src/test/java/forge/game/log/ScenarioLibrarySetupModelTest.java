package forge.game.log;

import forge.game.log.model.Scenario;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Lightweight data-model tests for the scenario starting-hand system.
 *
 * <p>These tests do NOT require a full Forge game engine (no FModel) —
 * they verify the {@link Scenario} and {@link Scenario.PlayerSetup} data
 * models and confirm that configuration round-trips correctly.</p>
 *
 * <p>For integration-level tests that run an actual game, see
 * {@code forge-gui-desktop} →
 * {@code forge.game.scenario.ScenarioStartingHandTest}.</p>
 */
public class ScenarioLibrarySetupModelTest {

    // -------------------------------------------------------------------------
    // Scenario.PlayerSetup model tests
    // -------------------------------------------------------------------------

    @Test
    public void testPlayerSetup_hasStartingConfig_falseWhenEmpty() {
        Scenario.PlayerSetup setup = new Scenario.PlayerSetup();
        assertFalse("Empty setup should NOT have starting config", setup.hasStartingConfig());
    }

    @Test
    public void testPlayerSetup_hasStartingConfig_trueWhenHandDefined() {
        Scenario.PlayerSetup setup = new Scenario.PlayerSetup();
        setup.setStartingHand(Arrays.asList("Mountain", "Forest", "Island",
                "Plains", "Swamp", "Mountain", "Forest"));
        assertTrue("Setup WITH starting hand SHOULD have starting config", setup.hasStartingConfig());
    }

    @Test
    public void testPlayerSetup_hasStartingConfig_trueWhenFirstDrawsDefined() {
        Scenario.PlayerSetup setup = new Scenario.PlayerSetup();
        setup.setFirstDraws(Arrays.asList("Sol Ring", "Command Tower"));
        assertTrue("Setup WITH first draws SHOULD have starting config", setup.hasStartingConfig());
    }

    @Test
    public void testPlayerSetup_defaultStartingLife() {
        Scenario.PlayerSetup setup = new Scenario.PlayerSetup();
        assertEquals("Default starting life should be 20", 20, setup.getStartingLife());
    }

    @Test
    public void testPlayerSetup_settersAndGetters() {
        Scenario.PlayerSetup setup = new Scenario.PlayerSetup();
        List<String> hand   = Arrays.asList("Mountain", "Forest", "Island", "Plains", "Swamp", "Mountain", "Forest");
        List<String> draws  = Arrays.asList("Island", "Mountain");
        List<String> cmds   = Collections.singletonList("Krenko, Mob Boss");
        List<String> btf    = Arrays.asList("Sol Ring", "Command Tower");

        setup.setStartingHand(hand);
        setup.setFirstDraws(draws);
        setup.setCommanders(cmds);
        setup.setBattlefield(btf);
        setup.setStartingLife(40);

        assertEquals(hand,  setup.getStartingHand());
        assertEquals(draws, setup.getFirstDraws());
        assertEquals(cmds,  setup.getCommanders());
        assertEquals(btf,   setup.getBattlefield());
        assertEquals(40,    setup.getStartingLife());
    }

    // -------------------------------------------------------------------------
    // Scenario model tests
    // -------------------------------------------------------------------------

    @Test
    public void testScenario_typeConstants() {
        assertEquals("opening_hand_test",   Scenario.TYPE_OPENING_HAND_TEST);
        assertEquals("interaction_check",   Scenario.TYPE_INTERACTION_CHECK);
        assertEquals("rules_clarification", Scenario.TYPE_RULES_CLARIFICATION);
        assertEquals("combo_outcome",       Scenario.TYPE_COMBO_OUTCOME);
    }

    @Test
    public void testScenario_hasPlayerSetup_falseWhenNoPlayers() {
        Scenario scenario = new Scenario();
        assertFalse("Scenario with no players should NOT have player setup",
                scenario.hasPlayerSetup());
    }

    @Test
    public void testScenario_hasPlayerSetup_trueWhenPlayerHasHand() {
        Scenario scenario = new Scenario();
        Scenario.PlayerSetup setup = scenario.getOrCreatePlayerSetup("P1");
        setup.setStartingHand(Arrays.asList("Mountain", "Forest", "Island",
                "Plains", "Swamp", "Mountain", "Forest"));
        assertTrue("Scenario should have player setup when P1 has a starting hand",
                scenario.hasPlayerSetup());
    }

    @Test
    public void testScenario_getOrCreatePlayerSetup_createsThenReuses() {
        Scenario scenario = new Scenario();
        Scenario.PlayerSetup s1 = scenario.getOrCreatePlayerSetup("P1");
        Scenario.PlayerSetup s2 = scenario.getOrCreatePlayerSetup("P1");
        assertSame("getOrCreate should return the same instance on second call", s1, s2);
    }

    // -------------------------------------------------------------------------
    // Opening-hand-test scenario config round-trip
    // -------------------------------------------------------------------------

    /**
     * Simulate what CSubmenuScenario / SimulateMatch do: build a Scenario, configure
     * player setups, extract the data map form expected by GameRules, and verify it
     * round-trips correctly.
     */
    @Test
    public void testOpeningHandTestConfig_roundTrip() {
        List<String> p1ExpectedHand  = Arrays.asList("Mountain", "Mountain", "Mountain",
                "Forest", "Forest", "Island", "Plains");
        List<String> p1ExpectedDraws = Arrays.asList("Swamp", "Mountain", "Forest");

        // Build scenario
        Scenario scenario = new Scenario();
        scenario.setType(Scenario.TYPE_OPENING_HAND_TEST);
        scenario.setTitle("Test Round-Trip");
        Scenario.PlayerSetup p1Setup = scenario.getOrCreatePlayerSetup("P1");
        p1Setup.setStartingHand(p1ExpectedHand);
        p1Setup.setFirstDraws(p1ExpectedDraws);
        p1Setup.setStartingLife(40);

        // Verify scenario flags
        assertTrue("opening_hand_test scenario should have player setup",
                scenario.hasPlayerSetup());

        // Extract into maps (as SimulateMatch/CSubmenuScenario would)
        Map<String, List<String>> startingHandsMap = new LinkedHashMap<>();
        Map<String, List<String>> firstDrawsMap    = new LinkedHashMap<>();

        for (Map.Entry<String, Scenario.PlayerSetup> entry : scenario.getPlayers().entrySet()) {
            String pid = entry.getKey();
            Scenario.PlayerSetup setup = entry.getValue();
            if (!setup.getStartingHand().isEmpty()) startingHandsMap.put(pid, setup.getStartingHand());
            if (!setup.getFirstDraws().isEmpty())   firstDrawsMap.put(pid,    setup.getFirstDraws());
        }

        // Verify extracted maps
        assertTrue ("P1 should be in startingHandsMap", startingHandsMap.containsKey("P1"));
        assertFalse("P2 should NOT be in startingHandsMap", startingHandsMap.containsKey("P2"));

        assertEquals("P1 starting hand must match", p1ExpectedHand,  startingHandsMap.get("P1"));
        assertEquals("P1 first draws must match",   p1ExpectedDraws, firstDrawsMap.get("P1"));

        // Life total preserved
        assertEquals(40, scenario.getOrCreatePlayerSetup("P1").getStartingLife());

        System.out.println("[PASS] testOpeningHandTestConfig_roundTrip");
    }
}

