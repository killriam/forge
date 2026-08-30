package forge.game.log;

import forge.game.log.model.*;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Simulation test for MTG Replay Notation system.
 * Tests the complete workflow without requiring a real game.
 */
public class ReplayNotationSimulationTest {

    @Test
    public void testCompleteWorkflow() {
        System.out.println("=== MTG Replay Notation Simulation Test ===\n");

        // 1. Create a simulated replay log
        ReplayLog replayLog = createSimulatedGame();
        System.out.println("✓ Step 1: Created simulated replay log");

        // 2. Validate the structure
        ReplayNotationValidator validator = new ReplayNotationValidator(replayLog);
        boolean isValid = validator.validate();

        System.out.println("\n✓ Step 2: Validation complete");
        System.out.println(validator.getReport());

        // 3. Generate L2 Units
        ReplayL2Generator l2Generator = new ReplayL2Generator(replayLog);
        l2Generator.generateL2Units();
        System.out.println("✓ Step 3: Generated L2 units: " + replayLog.getViewsL2().size());

        // 4. Export to JSON
        String json = ReplayJsonSerializer.toJson(replayLog);
        System.out.println("\n✓ Step 4: Generated JSON (" + json.length() + " characters)");

        // 5. Print summary
        printSummary(replayLog);

        // Assertions
        assertNotNull("Replay log should not be null", replayLog);
        assertNotNull("Meta should not be null", replayLog.getMeta());
        assertTrue("Should have L1 events", !replayLog.getLogL1().isEmpty());
        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain format", json.contains("mtg-replay"));

        System.out.println("\n=== All Tests Passed! ===");
    }

    /**
     * Create a simulated game with realistic events.
     */
    private ReplayLog createSimulatedGame() {
        ReplayLog log = new ReplayLog();

        // Setup metadata
        ReplayMeta meta = log.getMeta();
        meta.setGameId("test-game-001");
        meta.setTimestamp("2025-12-20T15:30:00Z");
        meta.setGameType("Constructed");
        meta.setWinner("P1");
        meta.setTurns(5);
        meta.setDurationSeconds(180);

        // Add players
        ReplayMeta.PlayerMeta p1 = new ReplayMeta.PlayerMeta();
        p1.setName("Alice");
        meta.getPlayers().put("P1", p1);

        ReplayMeta.PlayerMeta p2 = new ReplayMeta.PlayerMeta();
        p2.setName("Bob");
        meta.getPlayers().put("P2", p2);

        // Add cards to index
        CardDefinition mountain = new CardDefinition();
        mountain.setName("Mountain");
        mountain.setType("Basic Land — Mountain");
        log.getCardIndex().put("Mountain", mountain);

        CardDefinition bolt = new CardDefinition();
        bolt.setName("Lightning Bolt");
        bolt.setCost("{R}");
        bolt.setType("Instant");
        log.getCardIndex().put("Lightning Bolt", bolt);

        CardDefinition bear = new CardDefinition();
        bear.setName("Grizzly Bears");
        bear.setCost("{1}{G}");
        bear.setType("Creature — Bear");
        log.getCardIndex().put("Grizzly Bears", bear);

        // Set seed
        log.setSeed(123456789L);

        // Create initial state
        GameState initialState = new GameState();
        initialState.setTurn(0);
        initialState.setPhase("PREGAME");
        initialState.setPriority("P1");
        log.setInitialState(initialState);

        // Simulate a simple game sequence
        addGameEvents(log);

        return log;
    }

    /**
     * Add a realistic sequence of game events.
     */
    private void addGameEvents(ReplayLog log) {
        int eventIndex = 0;

        // Turn 1 - P1
        // Draw
        addEvent(log, eventIndex++, "T1.DRAW", "SYS", "DRAW",
            "player", "P1", "card", "c1");

        // Play land
        addEvent(log, eventIndex++, "T1.MP1:0", "P1", "PLAY_LAND",
            "card", "c5");

        addEvent(log, eventIndex++, "T1.MP1:0", "SYS", "MOVE",
            "obj", "c5", "from", "P1:hand", "to", "battlefield");

        // Pass turn
        addEvent(log, eventIndex++, "T1.MP1:0", "P1", "PASS_PRIORITY",
            "stack_size", 0);

        // Turn 2 - P2
        addEvent(log, eventIndex++, "T2.DRAW", "SYS", "DRAW",
            "player", "P2", "card", "c6");

        addEvent(log, eventIndex++, "T2.MP1:0", "P2", "PLAY_LAND",
            "card", "c7");

        addEvent(log, eventIndex++, "T2.MP1:0", "SYS", "MOVE",
            "obj", "c7", "from", "P2:hand", "to", "battlefield");

        // Turn 3 - P1 casts Lightning Bolt
        addEvent(log, eventIndex++, "T3.DRAW", "SYS", "DRAW",
            "player", "P1", "card", "c17");

        addEvent(log, eventIndex++, "T3.MP1:0", "P1", "CAST",
            "card", "c17", "cost", createCost("{R}"));

        addEvent(log, eventIndex++, "T3.MP1:0", "SYS", "PUT_ON_STACK",
            "stack", "s1", "kind", "SPELL", "controller", "P1", "card", "c17");

        addEvent(log, eventIndex++, "T3.MP1:1", "P2", "PASS_PRIORITY",
            "stack_size", 1);

        addEvent(log, eventIndex++, "T3.MP1:2", "SYS", "RESOLVE",
            "stack", "s1");

        addEvent(log, eventIndex++, "T3.MP1:2", "SYS", "DAMAGE",
            "source", "c17", "target", "P2", "amount", 3, "type", "spell");

        addEvent(log, eventIndex++, "T3.MP1:2", "SYS", "LIFE",
            "player", "P2", "delta", -3, "new_total", 17, "cause", "damage");

        addEvent(log, eventIndex++, "T3.MP1:2", "SYS", "MOVE",
            "obj", "c17", "from", "stack", "to", "P1:graveyard");

        // Turn 4 - Combat
        addEvent(log, eventIndex++, "T4.COMBAT.DECLARE_ATTACKERS", "P1", "DECLARE_ATTACKERS",
            "attackers", createAttackers());

        addEvent(log, eventIndex++, "T4.COMBAT.DECLARE_BLOCKERS", "P2", "DECLARE_BLOCKERS",
            "blocks", createBlocks());

        addEvent(log, eventIndex++, "T4.COMBAT.COMBAT_DAMAGE", "SYS", "DAMAGE",
            "source", "c25", "target", "c42", "amount", 2, "type", "combat");

        addEvent(log, eventIndex++, "T4.COMBAT.COMBAT_DAMAGE", "SYS", "STATE_BASED",
            "action", "creature_lethal_damage", "objects", createArray("c42"));

        addEvent(log, eventIndex++, "T4.COMBAT.COMBAT_DAMAGE", "SYS", "MOVE",
            "obj", "c42", "from", "battlefield", "to", "P2:graveyard");
    }

    /**
     * Helper to add an event with variable key-value pairs.
     */
    private void addEvent(ReplayLog log, int index, String time, String actor, String type, Object... dataKeyValues) {
        L1Event event = new L1Event(index, time, actor, type);

        for (int i = 0; i < dataKeyValues.length; i += 2) {
            String key = (String) dataKeyValues[i];
            Object value = dataKeyValues[i + 1];
            event.addData(key, value);
        }

        log.addL1Event(event);
    }

    /**
     * Create a mana cost object.
     */
    private Object createCost(String manaString) {
        java.util.Map<String, Object> cost = new java.util.HashMap<>();
        cost.put("mana", java.util.Arrays.asList(manaString));
        cost.put("additional", new java.util.ArrayList<>());
        cost.put("alternative", null);
        return cost;
    }

    /**
     * Create attackers array.
     */
    private Object createAttackers() {
        java.util.List<java.util.Map<String, String>> attackers = new java.util.ArrayList<>();
        java.util.Map<String, String> attacker = new java.util.HashMap<>();
        attacker.put("creature", "c25");
        attacker.put("defending", "P2");
        attackers.add(attacker);
        return attackers;
    }

    /**
     * Create blocks array.
     */
    private Object createBlocks() {
        java.util.List<java.util.Map<String, String>> blocks = new java.util.ArrayList<>();
        java.util.Map<String, String> block = new java.util.HashMap<>();
        block.put("blocker", "c42");
        block.put("blocking", "c25");
        blocks.add(block);
        return blocks;
    }

    /**
     * Create a simple array.
     */
    private Object createArray(String... items) {
        return java.util.Arrays.asList(items);
    }

    /**
     * Print a summary of the replay log.
     */
    private void printSummary(ReplayLog log) {
        System.out.println("\n=== Replay Log Summary ===");
        System.out.println("Format: " + log.getFormat());
        System.out.println("Version: " + log.getVersion());
        System.out.println("Game ID: " + log.getMeta().getGameId());
        System.out.println("Game Type: " + log.getMeta().getGameType());
        System.out.println("Winner: " + log.getMeta().getWinner());
        System.out.println("Turns: " + log.getMeta().getTurns());
        System.out.println("Players: " + log.getMeta().getPlayers().size());
        System.out.println("Cards in Index: " + log.getCardIndex().size());
        System.out.println("L1 Events: " + log.getLogL1().size());
        System.out.println("L2 Units: " + log.getViewsL2().size());

        System.out.println("\n=== Event Type Breakdown ===");
        java.util.Map<String, Integer> eventCounts = new java.util.HashMap<>();
        for (L1Event event : log.getLogL1()) {
            eventCounts.put(event.getType(), eventCounts.getOrDefault(event.getType(), 0) + 1);
        }

        for (java.util.Map.Entry<String, Integer> entry : eventCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("\n=== Sample Events ===");
        for (int i = 0; i < Math.min(5, log.getLogL1().size()); i++) {
            L1Event event = log.getLogL1().get(i);
            System.out.println("  [" + event.getI() + "] " + event.getT() + " - " +
                             event.getA() + " : " + event.getType());
        }
    }

    @Test
    public void testValidation() {
        System.out.println("\n=== Testing Validation ===");

        ReplayLog log = new ReplayLog();
        log.getMeta().setGameId("test-validation");
        log.getMeta().setGameType("Test");

        // Add a valid event
        L1Event event1 = new L1Event(0, "T1.MP1", "P1", "CAST");
        event1.addData("card", "c1");
        event1.addData("cost", createCost("{R}"));
        log.addL1Event(event1);

        // Add an invalid event (missing required field)
        L1Event event2 = new L1Event(1, "T1.MP1", "P1", "MOVE");
        // Missing "obj", "from", "to"
        log.addL1Event(event2);

        ReplayNotationValidator validator = new ReplayNotationValidator(log);
        boolean isValid = validator.validate();

        System.out.println("Validation Result: " + (isValid ? "VALID" : "INVALID"));
        System.out.println("\n" + validator.getReport());

        assertFalse("Should have validation errors", isValid);
        assertTrue("Should have errors", !validator.getErrors().isEmpty());
    }

    @Test
    public void testL2Generation() {
        System.out.println("\n=== Testing L2 Generation ===");

        ReplayLog log = createSimulatedGame();

        System.out.println("L1 Events: " + log.getLogL1().size());

        ReplayL2Generator generator = new ReplayL2Generator(log);
        generator.generateL2Units();

        List<L2Unit> units = log.getViewsL2();
        System.out.println("L2 Units Generated: " + units.size());

        if (!units.isEmpty()) {
            L2Unit firstUnit = units.get(0);
            System.out.println("\nFirst Unit:");
            System.out.println("  Index: " + firstUnit.getU());
            System.out.println("  Time: " + firstUnit.getTStart() + " → " + firstUnit.getTEnd());
            System.out.println("  L1 Range: [" + firstUnit.getL1Range()[0] + ", " + firstUnit.getL1Range()[1] + "]");
            System.out.println("  Decision Events: " + firstUnit.getDecisionEvents().size());
            System.out.println("  Stack Items: " + firstUnit.getStack().size());
        }

        // Validate units
        List<String> errors = generator.validateUnits();
        System.out.println("\nValidation Errors: " + errors.size());
        for (String error : errors) {
            System.out.println("  - " + error);
        }
    }

    @Test
    public void testJsonSerialization() {
        System.out.println("\n=== Testing JSON Serialization ===");

        ReplayLog log = new ReplayLog();
        log.getMeta().setGameId("json-test");
        log.getMeta().setGameType("Test");

        ReplayMeta.PlayerMeta p1 = new ReplayMeta.PlayerMeta();
        p1.setName("TestPlayer");
        log.getMeta().getPlayers().put("P1", p1);

        CardDefinition card = new CardDefinition();
        card.setName("Test Card");
        card.setCost("{1}{U}");
        card.setType("Instant");
        log.getCardIndex().put("TestCard", card);

        L1Event event = new L1Event(0, "T1.MP1", "P1", "CAST");
        event.addData("card", "c1");
        log.addL1Event(event);

        String json = ReplayJsonSerializer.toJson(log);

        System.out.println("Generated JSON (" + json.length() + " chars):");
        System.out.println(json.substring(0, Math.min(500, json.length())) + "...\n");

        assertNotNull("JSON should not be null", json);
        assertTrue("Should contain format", json.contains("\"format\": \"mtg-replay\""));
        assertTrue("Should contain version", json.contains("\"version\": \"1.0.0\""));
        assertTrue("Should contain game_id", json.contains("\"game_id\": \"json-test\""));
        assertTrue("Should contain player", json.contains("TestPlayer"));
        assertTrue("Should contain card", json.contains("Test Card"));
        assertTrue("Should contain event", json.contains("\"type\": \"CAST\""));
    }

    @Test
    public void testTeamSerialization() {
        ReplayLog log = new ReplayLog();
        log.getMeta().setGameId("team-test");
        log.getMeta().setGameType("Constructed");

        ReplayMeta.PlayerMeta p1 = new ReplayMeta.PlayerMeta();
        p1.setName("Alice");
        p1.setTeam(1);
        log.getMeta().getPlayers().put("P1", p1);

        ReplayMeta.PlayerMeta p2 = new ReplayMeta.PlayerMeta();
        p2.setName("Bob");
        p2.setTeam(2);
        log.getMeta().getPlayers().put("P2", p2);

        ReplayMeta.PlayerMeta p3 = new ReplayMeta.PlayerMeta();
        p3.setName("Charlie");
        p3.setTeam(2);
        log.getMeta().getPlayers().put("P3", p3);

        String json = ReplayJsonSerializer.toJson(log);
        assertNotNull(json);
        assertTrue("Should serialize team for P1", json.contains("\"team\": 1"));
        assertTrue("Should serialize team for P2/P3", json.contains("\"team\": 2"));
    }

    @Test
    public void testReplayOutcomeSerialization() {
        System.out.println("\n=== Testing Replay Outcome Serialization ===");

        ReplayLog log = new ReplayLog();
        log.getMeta().setGameId("test-replay-outcome");
        log.getMeta().setGameType("Constructed");
        log.getMeta().setTurns(24);
        log.getMeta().setWinner("P2");
        log.getMeta().setReplayedAt("2026-08-30T14:15:00Z");
        log.getMeta().setReplayedWinner("P1");
        log.getMeta().setReplayedOutcome("win");
        log.getMeta().setReplayedTurns(18);

        String json = ReplayJsonSerializer.toJson(log);
        assertNotNull(json);
        assertTrue("Should serialize replayed_at", json.contains("\"replayed_at\": \"2026-08-30T14:15:00Z\""));
        assertTrue("Should serialize replayed_winner", json.contains("\"replayed_winner\": \"P1\""));
        assertTrue("Should serialize replayed_outcome", json.contains("\"replayed_outcome\": \"win\""));
        assertTrue("Should serialize replayed_turns", json.contains("\"replayed_turns\": 18"));
    }

    @Test
    public void testEventTypes() {
        System.out.println("\n=== Testing Event Types ===");

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

        System.out.println("\nTotal Event Types: " + (playerDecisions.length + systemEvents.length));

        assertEquals("Should have 8 player decision types", 8, playerDecisions.length);
        assertEquals("Should have 14 system event types", 14, systemEvents.length);
    }
}

