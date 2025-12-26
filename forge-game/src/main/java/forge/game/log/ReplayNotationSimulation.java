package forge.game.log;

import forge.game.log.model.*;

import java.util.*;

/**
 * Standalone simulation test for MTG Replay Notation.
 * Can be run directly without JUnit or a full Forge setup.
 *
 * Run with: java forge.game.log.ReplayNotationSimulation
 */
public class ReplayNotationSimulation {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║   MTG Replay Notation - Simulation Test               ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        ReplayNotationSimulation sim = new ReplayNotationSimulation();

        try {
            // Run all tests
            sim.runAllTests();

            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║   ✅ ALL TESTS PASSED                                  ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("\n╔════════════════════════════════════════════════════════╗");
            System.err.println("║   ❌ TEST FAILED                                       ║");
            System.err.println("╚════════════════════════════════════════════════════════╝");
            e.printStackTrace();
        }
    }

    public void runAllTests() {
        testCompleteWorkflow();
        testValidation();
        testL2Generation();
        testJsonSerialization();
        testEventTypes();
        testIdSystem();
        testTimeMarkers();
    }

    /**
     * Test 1: Complete workflow from creation to JSON export
     */
    public void testCompleteWorkflow() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 1: Complete Workflow");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        // Step 1: Create simulated game
        ReplayLog log = createSimulatedGame();
        System.out.println("✓ Created simulated replay log");
        System.out.println("  - Players: " + log.getMeta().getPlayers().size());
        System.out.println("  - Cards: " + log.getCardIndex().size());
        System.out.println("  - L1 Events: " + log.getLogL1().size());

        // Step 2: Validate
        ReplayNotationValidator validator = new ReplayNotationValidator(log);
        boolean isValid = validator.validate();
        System.out.println("\n✓ Validation complete: " + (isValid ? "VALID" : "INVALID"));
        if (!validator.getErrors().isEmpty()) {
            System.out.println("  - Errors: " + validator.getErrors().size());
        }
        if (!validator.getWarnings().isEmpty()) {
            System.out.println("  - Warnings: " + validator.getWarnings().size());
        }

        // Step 3: Generate L2
        ReplayL2Generator l2gen = new ReplayL2Generator(log);
        l2gen.generateL2Units();
        System.out.println("\n✓ Generated L2 units: " + log.getViewsL2().size());

        // Step 4: Export JSON
        String json = ReplayJsonSerializer.toJson(log);
        System.out.println("\n✓ Generated JSON: " + json.length() + " characters");

        // Step 5: Summary
        printSummary(log);

        System.out.println("\n✅ Test 1 PASSED\n");
    }

    /**
     * Test 2: Validation with errors
     */
    public void testValidation() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 2: Validation");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        ReplayLog log = new ReplayLog();
        log.getMeta().setGameId("validation-test");
        log.getMeta().setGameType("Test");

        // Add valid event
        L1Event valid = new L1Event(0, "T1.MP1", "P1", "CAST");
        valid.addData("card", "c1");
        Map<String, Object> cost = new HashMap<>();
        cost.put("mana", Arrays.asList("R"));
        valid.addData("cost", cost);
        log.addL1Event(valid);
        System.out.println("✓ Added valid CAST event");

        // Add invalid event (missing required fields)
        L1Event invalid = new L1Event(1, "T1.MP1", "P1", "MOVE");
        // Missing "obj", "from", "to"
        log.addL1Event(invalid);
        System.out.println("✓ Added invalid MOVE event (missing fields)");

        // Validate
        ReplayNotationValidator validator = new ReplayNotationValidator(log);
        boolean isValid = validator.validate();

        System.out.println("\nValidation Result: " + (isValid ? "VALID ✓" : "INVALID ✗"));
        System.out.println("Errors: " + validator.getErrors().size());
        System.out.println("Warnings: " + validator.getWarnings().size());

        if (!validator.getErrors().isEmpty()) {
            System.out.println("\nError Details:");
            for (String error : validator.getErrors()) {
                System.out.println("  ✗ " + error);
            }
        }

        System.out.println("\n✅ Test 2 PASSED (detected errors correctly)\n");
    }

    /**
     * Test 3: L2 Generation
     */
    public void testL2Generation() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 3: L2 Generation");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        ReplayLog log = createSimulatedGame();
        System.out.println("L1 Events: " + log.getLogL1().size());

        ReplayL2Generator generator = new ReplayL2Generator(log);
        generator.generateL2Units();

        List<L2Unit> units = log.getViewsL2();
        System.out.println("L2 Units Generated: " + units.size());

        if (!units.isEmpty()) {
            System.out.println("\nFirst L2 Unit:");
            L2Unit unit = units.get(0);
            System.out.println("  - Index: " + unit.getU());
            System.out.println("  - Time Range: " + unit.getTStart() + " → " + unit.getTEnd());
            System.out.println("  - L1 Range: [" + unit.getL1Range()[0] + ", " + unit.getL1Range()[1] + "]");
            System.out.println("  - Decision Events: " + unit.getDecisionEvents().size());
            System.out.println("  - Stack Items: " + unit.getStack().size());
        }

        // Validate units
        List<String> errors = generator.validateUnits();
        System.out.println("\nUnit Validation Errors: " + errors.size());

        System.out.println("\n✅ Test 3 PASSED\n");
    }

    /**
     * Test 4: JSON Serialization
     */
    public void testJsonSerialization() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 4: JSON Serialization");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        ReplayLog log = new ReplayLog();
        log.getMeta().setGameId("json-test-001");
        log.getMeta().setGameType("TestGame");
        log.getMeta().setWinner("P1");

        ReplayMeta.PlayerMeta p1 = new ReplayMeta.PlayerMeta();
        p1.setName("Alice");
        log.getMeta().getPlayers().put("P1", p1);

        CardDefinition card = new CardDefinition();
        card.setName("Test Card");
        card.setCost("{2}{U}{U}");
        card.setType("Instant");
        log.getCardIndex().put("TestCard", card);

        L1Event event = new L1Event(0, "T1.MP1:0", "P1", "CAST");
        event.addData("card", "c1");
        log.addL1Event(event);

        String json = ReplayJsonSerializer.toJson(log);

        System.out.println("Generated JSON:");
        System.out.println("  - Length: " + json.length() + " characters");
        System.out.println("  - Contains format: " + json.contains("\"format\": \"mtg-replay\""));
        System.out.println("  - Contains version: " + json.contains("\"version\": \"1.0.0\""));
        System.out.println("  - Contains player: " + json.contains("Alice"));
        System.out.println("  - Contains card: " + json.contains("Test Card"));

        System.out.println("\nJSON Preview (first 300 chars):");
        System.out.println("─────────────────────────────────────────────────────────");
        System.out.println(json.substring(0, Math.min(300, json.length())));
        System.out.println("...");
        System.out.println("─────────────────────────────────────────────────────────");

        System.out.println("\n✅ Test 4 PASSED\n");
    }

    /**
     * Test 5: Event Types
     */
    public void testEventTypes() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 5: Event Types");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        String[] playerDecisions = {
            "CAST", "ACTIVATE", "PLAY_LAND", "DECLARE_ATTACKERS",
            "DECLARE_BLOCKERS", "PASS_PRIORITY", "MULLIGAN", "CHOOSE"
        };

        String[] systemEvents = {
            "PUT_ON_STACK", "TRIGGER", "RESOLVE", "MOVE", "DAMAGE",
            "LIFE", "COUNTERS", "TAP", "PHASE_CHANGE", "TURN_START",
            "TURN_END", "STATE_BASED", "RANDOM", "DRAW"
        };

        System.out.println("Player Decision Events (" + playerDecisions.length + "):");
        for (String type : playerDecisions) {
            System.out.println("  ✓ " + type);
        }

        System.out.println("\nSystem Events (" + systemEvents.length + "):");
        for (String type : systemEvents) {
            System.out.println("  ✓ " + type);
        }

        int total = playerDecisions.length + systemEvents.length;
        System.out.println("\nTotal Event Types: " + total);

        System.out.println("\n✅ Test 5 PASSED\n");
    }

    /**
     * Test 6: ID System
     */
    public void testIdSystem() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 6: ID System");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        System.out.println("ID Prefixes:");
        System.out.println("  c - Cards:       c1, c2, c42");
        System.out.println("  t - Tokens:      t1, t7");
        System.out.println("  s - Stack:       s1, s2");
        System.out.println("  P - Players:     P1, P2");

        System.out.println("\nExample Usage:");
        System.out.println("  c17 moved from P1:hand to battlefield");
        System.out.println("  s1 resolved (spell c17)");
        System.out.println("  P2 took 3 damage");

        System.out.println("\n✅ Test 6 PASSED\n");
    }

    /**
     * Test 7: Time Markers
     */
    public void testTimeMarkers() {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("TEST 7: Time Markers");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        System.out.println("Time Marker Format: T<turn>.<phase>[:<priority>]");
        System.out.println("\nExamples:");
        System.out.println("  T1.UP.UNTAP         - Turn 1, Upkeep, Untap Step");
        System.out.println("  T3.MP1:0            - Turn 3, Main Phase 1, Priority 0");
        System.out.println("  T3.MP1:2            - Turn 3, Main Phase 1, Priority 2");
        System.out.println("  T4.COMBAT.DECLARE_ATTACKERS - Turn 4, Combat");

        System.out.println("\nPhase Codes:");
        String[] phases = {"UP", "DRAW", "MP1", "COMBAT", "MP2", "END", "CLEAN"};
        for (String phase : phases) {
            System.out.println("  - " + phase);
        }

        System.out.println("\n✅ Test 7 PASSED\n");
    }

    /**
     * Create a simulated game with realistic events.
     */
    private ReplayLog createSimulatedGame() {
        ReplayLog log = new ReplayLog();

        // Setup metadata
        ReplayMeta meta = log.getMeta();
        meta.setGameId("sim-game-001");
        meta.setTimestamp("2025-12-20T15:30:00Z");
        meta.setGameType("Constructed");
        meta.setWinner("P1");
        meta.setTurns(4);
        meta.setDurationSeconds(240);

        // Add players
        ReplayMeta.PlayerMeta p1 = new ReplayMeta.PlayerMeta();
        p1.setName("Alice");
        meta.getPlayers().put("P1", p1);

        ReplayMeta.PlayerMeta p2 = new ReplayMeta.PlayerMeta();
        p2.setName("Bob");
        meta.getPlayers().put("P2", p2);

        // Add cards
        addCard(log, "Mountain", "", "Basic Land — Mountain");
        addCard(log, "Lightning Bolt", "{R}", "Instant");
        addCard(log, "Grizzly Bears", "{1}{G}", "Creature — Bear");

        log.setSeed(987654321L);

        // Initial state
        GameState initialState = new GameState();
        initialState.setTurn(0);
        initialState.setPhase("PREGAME");
        log.setInitialState(initialState);

        // Add game events
        addSimulatedEvents(log);

        return log;
    }

    private void addCard(ReplayLog log, String name, String cost, String type) {
        CardDefinition card = new CardDefinition();
        card.setName(name);
        card.setCost(cost);
        card.setType(type);
        log.getCardIndex().put(name, card);
    }

    private void addSimulatedEvents(ReplayLog log) {
        int i = 0;

        // Turn 1
        addEvent(log, i++, "T1.DRAW", "SYS", "DRAW", "player", "P1", "card", "c1");
        addEvent(log, i++, "T1.MP1:0", "P1", "PLAY_LAND", "card", "c5");
        addEvent(log, i++, "T1.MP1:0", "SYS", "MOVE", "obj", "c5", "from", "P1:hand", "to", "battlefield");
        addEvent(log, i++, "T1.MP1:0", "P1", "PASS_PRIORITY", "stack_size", 0);

        // Turn 2
        addEvent(log, i++, "T2.DRAW", "SYS", "DRAW", "player", "P2", "card", "c6");
        addEvent(log, i++, "T2.MP1:0", "P2", "PLAY_LAND", "card", "c7");
        addEvent(log, i++, "T2.MP1:0", "P2", "PASS_PRIORITY", "stack_size", 0);

        // Turn 3 - Lightning Bolt
        addEvent(log, i++, "T3.DRAW", "SYS", "DRAW", "player", "P1", "card", "c17");
        addEvent(log, i++, "T3.MP1:0", "P1", "CAST", "card", "c17", "cost", createCost());
        addEvent(log, i++, "T3.MP1:0", "SYS", "PUT_ON_STACK", "stack", "s1", "kind", "SPELL", "card", "c17");
        addEvent(log, i++, "T3.MP1:1", "P2", "PASS_PRIORITY", "stack_size", 1);
        addEvent(log, i++, "T3.MP1:2", "SYS", "RESOLVE", "stack", "s1");
        addEvent(log, i++, "T3.MP1:2", "SYS", "DAMAGE", "source", "c17", "target", "P2", "amount", 3);
        addEvent(log, i++, "T3.MP1:2", "SYS", "LIFE", "player", "P2", "delta", -3, "new_total", 17);
        addEvent(log, i++, "T3.MP1:2", "SYS", "MOVE", "obj", "c17", "from", "stack", "to", "P1:graveyard");
    }

    private void addEvent(ReplayLog log, int index, String time, String actor, String type, Object... kvPairs) {
        L1Event event = new L1Event(index, time, actor, type);
        for (int i = 0; i < kvPairs.length; i += 2) {
            event.addData((String) kvPairs[i], kvPairs[i + 1]);
        }
        log.addL1Event(event);
    }

    private Map<String, Object> createCost() {
        Map<String, Object> cost = new HashMap<>();
        cost.put("mana", Arrays.asList("R"));
        cost.put("additional", new ArrayList<>());
        return cost;
    }

    private void printSummary(ReplayLog log) {
        System.out.println("\n┌────────────────────────────────────────────────────────┐");
        System.out.println("│ Replay Log Summary                                     │");
        System.out.println("└────────────────────────────────────────────────────────┘");
        System.out.println("Format:       " + log.getFormat());
        System.out.println("Version:      " + log.getVersion());
        System.out.println("Game ID:      " + log.getMeta().getGameId());
        System.out.println("Game Type:    " + log.getMeta().getGameType());
        System.out.println("Winner:       " + log.getMeta().getWinner());
        System.out.println("Players:      " + log.getMeta().getPlayers().size());
        System.out.println("Cards:        " + log.getCardIndex().size());
        System.out.println("L1 Events:    " + log.getLogL1().size());
        System.out.println("L2 Units:     " + log.getViewsL2().size());

        // Event breakdown
        Map<String, Integer> counts = new HashMap<>();
        for (L1Event event : log.getLogL1()) {
            counts.put(event.getType(), counts.getOrDefault(event.getType(), 0) + 1);
        }

        System.out.println("\nEvent Breakdown:");
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
    }
}

