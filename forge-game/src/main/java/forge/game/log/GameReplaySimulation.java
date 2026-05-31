package forge.game.log;

import forge.game.log.model.*;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Simple command-line simulation to test the MTG Replay Notation system.
 * Creates a simulated game and exports it to JSON format.
 *
 * Usage:
 *   java -cp forge.jar forge.game.log.GameReplaySimulation [output_dir]
 *
 * This will create a JSON file in the specified directory (or current directory if not specified).
 */
public class GameReplaySimulation {

    public static void main(String[] args) {
        System.out.println("════════════════════════════════════════════════════");
        System.out.println("  MTG Replay Notation - Game Simulation");
        System.out.println("════════════════════════════════════════════════════\n");

        // Parse output directory
        String outputPath = args.length > 0 ? args[0] : ".";
        File outputDir = new File(outputPath);

        try {
            // Run simulation
            GameReplaySimulation sim = new GameReplaySimulation();
            File jsonFile = sim.simulateGame(outputDir);

            System.out.println("\n════════════════════════════════════════════════════");
            System.out.println("  ✅ Simulation Complete!");
            System.out.println("  📄 JSON File: " + jsonFile.getAbsolutePath());
            System.out.println("════════════════════════════════════════════════════");

        } catch (Exception e) {
            System.err.println("\n════════════════════════════════════════════════════");
            System.err.println("  ❌ Simulation Failed");
            System.err.println("════════════════════════════════════════════════════");
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Simulate a complete game and export to JSON.
     */
    public File simulateGame(File outputDir) throws IOException {
        System.out.println("Step 1: Creating simulated game...");
        ReplayLog log = createGameLog();

        System.out.println("  ✓ Game created");
        System.out.println("    - Players: " + log.getMeta().getPlayers().size());
        System.out.println("    - Cards: " + log.getCardIndex().size());
        System.out.println("    - Events: " + log.getLogL1().size());

        System.out.println("\nStep 2: Validating replay log...");
        ReplayNotationValidator validator = new ReplayNotationValidator(log);
        boolean isValid = validator.validate();

        System.out.println("  ✓ Validation: " + (isValid ? "PASSED" : "FAILED"));
        System.out.println("    - Errors: " + validator.getErrors().size());
        System.out.println("    - Warnings: " + validator.getWarnings().size());

        if (!validator.getErrors().isEmpty()) {
            System.out.println("\n  Validation Errors:");
            for (String error : validator.getErrors()) {
                System.out.println("    ✗ " + error);
            }
        }

        System.out.println("\nStep 3: Generating L2 Units...");
        ReplayL2Generator l2gen = new ReplayL2Generator(log);
        l2gen.generateL2Units();

        System.out.println("  ✓ L2 Units generated: " + log.getViewsL2().size());

        System.out.println("\nStep 4: Exporting to JSON...");
        File jsonFile = exportToJson(log, outputDir);

        System.out.println("  ✓ JSON exported");
        System.out.println("    - File: " + jsonFile.getName());
        System.out.println("    - Size: " + jsonFile.length() + " bytes");

        System.out.println("\nStep 5: Summary");
        printGameSummary(log);

        return jsonFile;
    }

    /**
     * Create a simulated game log representing a simple MTG game.
     */
    private ReplayLog createGameLog() {
        ReplayLog log = new ReplayLog();

        // Setup game metadata
        ReplayMeta meta = log.getMeta();
        meta.setGameId("sim-" + System.currentTimeMillis());
        meta.setTimestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
        meta.setGameType("Simulated_Constructed");
        meta.setWinner("P1");
        meta.setTurns(5);
        meta.setDurationSeconds(180);

        // Add players
        ReplayMeta.PlayerMeta alice = new ReplayMeta.PlayerMeta();
        alice.setName("Alice");
        alice.setDeckHash("red_aggro_deck");
        meta.getPlayers().put("P1", alice);

        ReplayMeta.PlayerMeta bob = new ReplayMeta.PlayerMeta();
        bob.setName("Bob");
        bob.setDeckHash("blue_control_deck");
        meta.getPlayers().put("P2", bob);

        // Set random seed
        log.setSeed(123456789L);

        // Add cards to index
        addCardToIndex(log, "Mountain", "", "Basic Land — Mountain");
        addCardToIndex(log, "Island", "", "Basic Land — Island");
        addCardToIndex(log, "Lightning Bolt", "{R}", "Instant");
        addCardToIndex(log, "Counterspell", "{U}{U}", "Instant");
        addCardToIndex(log, "Grizzly Bears", "{1}{G}", "Creature — Bear 2/2");
        addCardToIndex(log, "Goblin Guide", "{R}", "Creature — Goblin Scout 2/2");

        // Setup initial state
        GameState initialState = new GameState();
        initialState.setTurn(0);
        initialState.setPhase("PREGAME");
        initialState.setPriority("P1");
        initialState.setActivePlayer("P1");
        log.setInitialState(initialState);

        // Simulate game events
        simulateGameEvents(log);

        return log;
    }

    /**
     * Add a card to the card index.
     */
    private void addCardToIndex(ReplayLog log, String name, String cost, String type) {
        CardDefinition card = new CardDefinition();
        card.setName(name);
        card.setCost(cost);
        card.setType(type);
        card.setOracleId("oracle-" + name.toLowerCase().replace(" ", "-"));
        log.getCardIndex().put(name, card);
    }

    /**
     * Simulate a realistic game sequence.
     */
    private void simulateGameEvents(ReplayLog log) {
        int eventIndex = 0;

        // ═══════════════════════════════════════════════════
        // PREGAME: Shuffle and Draw Starting Hands
        // ═══════════════════════════════════════════════════
        eventIndex = addEvent(log, eventIndex, "T0.PREGAME", "SYS", "RANDOM",
            "kind", "shuffle", "zone", "P1:library", "seed", 123456789);

        eventIndex = addEvent(log, eventIndex, "T0.PREGAME", "SYS", "RANDOM",
            "kind", "shuffle", "zone", "P2:library", "seed", 987654321);

        // Draw opening hands (7 cards each)
        for (int i = 1; i <= 7; i++) {
            eventIndex = addEvent(log, eventIndex, "T0.PREGAME", "SYS", "DRAW",
                "player", "P1", "card", "c" + i);
        }
        for (int i = 8; i <= 14; i++) {
            eventIndex = addEvent(log, eventIndex, "T0.PREGAME", "SYS", "DRAW",
                "player", "P2", "card", "c" + i);
        }

        // ═══════════════════════════════════════════════════
        // TURN 1 (P1)
        // ═══════════════════════════════════════════════════
        eventIndex = addEvent(log, eventIndex, "T1.UP", "SYS", "TURN_START",
            "player", "P1", "turn", 1);

        eventIndex = addEvent(log, eventIndex, "T1.DRAW", "SYS", "DRAW",
            "player", "P1", "card", "c15");

        // Play Mountain
        eventIndex = addEvent(log, eventIndex, "T1.MP1:0", "P1", "PLAY_LAND",
            "card", "c1");

        eventIndex = addEvent(log, eventIndex, "T1.MP1:0", "SYS", "MOVE",
            "obj", "c1", "from", "P1:hand", "to", "battlefield", "pos", "top", "visibility", "public");

        // Pass turn
        eventIndex = addEvent(log, eventIndex, "T1.MP1:0", "P1", "PASS_PRIORITY",
            "stack_size", 0);

        eventIndex = addEvent(log, eventIndex, "T1.END", "SYS", "PHASE_CHANGE",
            "phase", "END", "step", "END", "active_player", "P1");

        // ═══════════════════════════════════════════════════
        // TURN 2 (P2)
        // ═══════════════════════════════════════════════════
        eventIndex = addEvent(log, eventIndex, "T2.UP", "SYS", "TURN_START",
            "player", "P2", "turn", 2);

        eventIndex = addEvent(log, eventIndex, "T2.DRAW", "SYS", "DRAW",
            "player", "P2", "card", "c16");

        // Play Island
        eventIndex = addEvent(log, eventIndex, "T2.MP1:0", "P2", "PLAY_LAND",
            "card", "c8");

        eventIndex = addEvent(log, eventIndex, "T2.MP1:0", "SYS", "MOVE",
            "obj", "c8", "from", "P2:hand", "to", "battlefield", "pos", "top", "visibility", "public");

        eventIndex = addEvent(log, eventIndex, "T2.MP1:0", "P2", "PASS_PRIORITY",
            "stack_size", 0);

        // ═══════════════════════════════════════════════════
        // TURN 3 (P1) - Cast Goblin Guide
        // ═══════════════════════════════════════════════════
        eventIndex = addEvent(log, eventIndex, "T3.UP", "SYS", "TURN_START",
            "player", "P1", "turn", 3);

        eventIndex = addEvent(log, eventIndex, "T3.DRAW", "SYS", "DRAW",
            "player", "P1", "card", "c17");

        // Play second Mountain
        eventIndex = addEvent(log, eventIndex, "T3.MP1:0", "P1", "PLAY_LAND",
            "card", "c2");

        eventIndex = addEvent(log, eventIndex, "T3.MP1:0", "SYS", "MOVE",
            "obj", "c2", "from", "P1:hand", "to", "battlefield", "pos", "top", "visibility", "public");

        // Cast Goblin Guide
        eventIndex = addEvent(log, eventIndex, "T3.MP1:1", "P1", "CAST",
            "card", "c20", "cost", createManaCost("{R}"), "targets", new ArrayList<>(), "choices", new HashMap<>());

        eventIndex = addEvent(log, eventIndex, "T3.MP1:1", "SYS", "PUT_ON_STACK",
            "stack", "s1", "kind", "SPELL", "source", "c20", "controller", "P1", "card", "c20");

        eventIndex = addEvent(log, eventIndex, "T3.MP1:2", "P2", "PASS_PRIORITY",
            "stack_size", 1);

        eventIndex = addEvent(log, eventIndex, "T3.MP1:3", "SYS", "RESOLVE",
            "stack", "s1");

        eventIndex = addEvent(log, eventIndex, "T3.MP1:3", "SYS", "MOVE",
            "obj", "c20", "from", "stack", "to", "battlefield", "pos", "top", "visibility", "public");

        // Attack with Goblin Guide
        eventIndex = addEvent(log, eventIndex, "T3.COMBAT.DECLARE_ATTACKERS:0", "P1", "DECLARE_ATTACKERS",
            "attackers", createAttackers("c20", "P2"));

        // No blockers
        eventIndex = addEvent(log, eventIndex, "T3.COMBAT.DECLARE_BLOCKERS:0", "P2", "DECLARE_BLOCKERS",
            "blocks", new ArrayList<>());

        // Combat damage
        eventIndex = addEvent(log, eventIndex, "T3.COMBAT.COMBAT_DAMAGE", "SYS", "DAMAGE",
            "source", "c20", "target", "P2", "amount", 2, "type", "combat", "prevented", 0);

        eventIndex = addEvent(log, eventIndex, "T3.COMBAT.COMBAT_DAMAGE", "SYS", "LIFE",
            "player", "P2", "delta", -2, "new_total", 18, "cause", "combat_damage");

        // ═══════════════════════════════════════════════════
        // TURN 4 (P2) - Do nothing
        // ═══════════════════════════════════════════════════
        eventIndex = addEvent(log, eventIndex, "T4.UP", "SYS", "TURN_START",
            "player", "P2", "turn", 4);

        eventIndex = addEvent(log, eventIndex, "T4.DRAW", "SYS", "DRAW",
            "player", "P2", "card", "c18");

        eventIndex = addEvent(log, eventIndex, "T4.MP1:0", "P2", "PLAY_LAND",
            "card", "c9");

        eventIndex = addEvent(log, eventIndex, "T4.MP1:0", "SYS", "MOVE",
            "obj", "c9", "from", "P2:hand", "to", "battlefield", "pos", "top", "visibility", "public");

        eventIndex = addEvent(log, eventIndex, "T4.MP1:0", "P2", "PASS_PRIORITY",
            "stack_size", 0);

        // ═══════════════════════════════════════════════════
        // TURN 5 (P1) - Cast Lightning Bolt for the win
        // ═══════════════════════════════════════════════════
        eventIndex = addEvent(log, eventIndex, "T5.UP", "SYS", "TURN_START",
            "player", "P1", "turn", 5);

        eventIndex = addEvent(log, eventIndex, "T5.DRAW", "SYS", "DRAW",
            "player", "P1", "card", "c19");

        // Cast Lightning Bolt targeting opponent
        eventIndex = addEvent(log, eventIndex, "T5.MP1:0", "P1", "CAST",
            "card", "c19", "cost", createManaCost("{R}"),
            "targets", createTargets("any", "P2"), "choices", new HashMap<>());

        eventIndex = addEvent(log, eventIndex, "T5.MP1:0", "SYS", "PUT_ON_STACK",
            "stack", "s2", "kind", "SPELL", "source", "c19", "controller", "P1", "card", "c19");

        eventIndex = addEvent(log, eventIndex, "T5.MP1:1", "P2", "PASS_PRIORITY",
            "stack_size", 1);

        eventIndex = addEvent(log, eventIndex, "T5.MP1:2", "SYS", "RESOLVE",
            "stack", "s2");

        eventIndex = addEvent(log, eventIndex, "T5.MP1:2", "SYS", "DAMAGE",
            "source", "c19", "target", "P2", "amount", 3, "type", "spell", "prevented", 0);

        eventIndex = addEvent(log, eventIndex, "T5.MP1:2", "SYS", "LIFE",
            "player", "P2", "delta", -3, "new_total", 15, "cause", "spell_damage");

        eventIndex = addEvent(log, eventIndex, "T5.MP1:2", "SYS", "MOVE",
            "obj", "c19", "from", "stack", "to", "P1:graveyard", "pos", "top", "visibility", "public");

        // Attack for lethal
        eventIndex = addEvent(log, eventIndex, "T5.COMBAT.DECLARE_ATTACKERS:0", "P1", "DECLARE_ATTACKERS",
            "attackers", createAttackers("c20", "P2"));

        eventIndex = addEvent(log, eventIndex, "T5.COMBAT.DECLARE_BLOCKERS:0", "P2", "DECLARE_BLOCKERS",
            "blocks", new ArrayList<>());

        eventIndex = addEvent(log, eventIndex, "T5.COMBAT.COMBAT_DAMAGE", "SYS", "DAMAGE",
            "source", "c20", "target", "P2", "amount", 2, "type", "combat", "prevented", 0);

        eventIndex = addEvent(log, eventIndex, "T5.COMBAT.COMBAT_DAMAGE", "SYS", "LIFE",
            "player", "P2", "delta", -2, "new_total", 13, "cause", "combat_damage");
    }

    /**
     * Add an event to the log.
     */
    private int addEvent(ReplayLog log, int index, String time, String actor, String type, Object... kvPairs) {
        L1Event event = new L1Event(index, time, actor, type);
        for (int i = 0; i < kvPairs.length; i += 2) {
            event.addData((String) kvPairs[i], kvPairs[i + 1]);
        }
        log.addL1Event(event);
        return index + 1;
    }

    /**
     * Create mana cost object.
     */
    private Map<String, Object> createManaCost(String manaString) {
        Map<String, Object> cost = new HashMap<>();
        cost.put("mana", Arrays.asList(manaString));
        cost.put("additional", new ArrayList<>());
        cost.put("alternative", null);
        return cost;
    }

    /**
     * Create targets list.
     */
    private List<Map<String, String>> createTargets(String slot, String obj) {
        List<Map<String, String>> targets = new ArrayList<>();
        Map<String, String> target = new HashMap<>();
        target.put("slot", slot);
        target.put("obj", obj);
        targets.add(target);
        return targets;
    }

    /**
     * Create attackers list.
     */
    private List<Map<String, String>> createAttackers(String creature, String defending) {
        List<Map<String, String>> attackers = new ArrayList<>();
        Map<String, String> attacker = new HashMap<>();
        attacker.put("creature", creature);
        attacker.put("defending", defending);
        attackers.add(attacker);
        return attackers;
    }

    /**
     * Export replay log to JSON file.
     */
    private File exportToJson(ReplayLog log, File outputDir) throws IOException {
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDir);
            }
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String filename = "replay_simulation_" + timestamp + ".json";
        File jsonFile = new File(outputDir, filename);

        ReplayJsonSerializer.writeToFile(log, jsonFile);

        return jsonFile;
    }

    // -------------------------------------------------------------------------
    // Eval Scenario helpers (commander-decklist-spec v1.2.0)
    // -------------------------------------------------------------------------

    /**
     * Parses the EvalScenario IDs from a deck's metadata and returns them as
     * a list. Returns an empty list when no eval scenarios are configured.
     *
     * @param deck the Forge deck whose metadata to inspect
     * @return list of eval_sequence scenario IDs (may be empty, never null)
     */
    public static List<String> getEvalScenarioIds(forge.deck.Deck deck) {
        String raw = deck.getEvalScenarioIds();
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (String id : raw.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    /**
     * Applies a forced-mode eval scenario's draw sequence to the given
     * GameRules object.  The scenario defines an opening hand (N cards) and
     * optional per-turn draws; together they determine the forced library order
     * that Forge will use for the player.
     *
     * <p>For group-based card references ({@code {"group": "ramp"}}) the caller
     * must resolve them to concrete card names before passing here; this method
     * only accepts concrete ordered card-name lists.</p>
     *
     * <p>Usage pattern:</p>
     * <pre>
     *   List&lt;String&gt; order = resolveScenario(deck, scenarioJson); // caller resolves
     *   applyForcedLibraryOrder(rules, playerName, order);
     * </pre>
     *
     * @param rules      GameRules instance to configure
     * @param playerName lobby name of the player whose library to force
     * @param cardOrder  concrete card names in the desired draw order
     *                   (index 0 = top of library = first drawn)
     */
    public static void applyForcedLibraryOrder(
            forge.game.GameRules rules,
            String playerName,
            List<String> cardOrder) {
        if (rules == null || playerName == null || cardOrder == null || cardOrder.isEmpty()) {
            return;
        }
        rules.setReplayMode(true);
        java.util.Map<String, List<String>> order = rules.getForcedLibraryOrder();
        if (order == null) {
            order = new java.util.HashMap<>();
            rules.setForcedLibraryOrder(order);
        }
        order.put(playerName, cardOrder);
    }

    /**
     * Print game summary.
     */
    private void printGameSummary(ReplayLog log) {
        System.out.println("  ┌──────────────────────────────────────────────┐");
        System.out.println("  │ Game Summary                                 │");
        System.out.println("  └──────────────────────────────────────────────┘");
        System.out.println("    Format:      " + log.getFormat());
        System.out.println("    Version:     " + log.getVersion());
        System.out.println("    Game ID:     " + log.getMeta().getGameId());
        System.out.println("    Winner:      " + log.getMeta().getWinner());
        System.out.println("    Turns:       " + log.getMeta().getTurns());
        System.out.println("    Duration:    " + log.getMeta().getDurationSeconds() + "s");
        System.out.println("    Players:     " + log.getMeta().getPlayers().size());
        System.out.println("    Cards:       " + log.getCardIndex().size());
        System.out.println("    L1 Events:   " + log.getLogL1().size());
        System.out.println("    L2 Units:    " + log.getViewsL2().size());

        // Event type breakdown
        Map<String, Integer> eventCounts = new HashMap<>();
        for (L1Event event : log.getLogL1()) {
            eventCounts.put(event.getType(), eventCounts.getOrDefault(event.getType(), 0) + 1);
        }

        System.out.println("\n  Event Type Breakdown:");
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(eventCounts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (Map.Entry<String, Integer> entry : sorted) {
            System.out.println("    " + String.format("%-20s", entry.getKey()) + ": " + entry.getValue());
        }
    }
}

