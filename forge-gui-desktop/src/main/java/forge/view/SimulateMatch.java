package forge.view;

import java.io.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.time.StopWatch;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckImportController;
import forge.deck.DeckRecognizer;
import forge.deck.io.DeckSerializer;
import forge.game.*;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.log.GameReplaySimulation;
import forge.game.player.DeckStats;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.tournament.system.AbstractTournament;
import forge.gamemodes.tournament.system.TournamentBracket;
import forge.gamemodes.tournament.system.TournamentPairing;
import forge.gamemodes.tournament.system.TournamentPlayer;
import forge.gamemodes.tournament.system.TournamentRoundRobin;
import forge.gamemodes.tournament.system.TournamentSwiss;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.Lang;
import forge.util.MyRandom;
import forge.util.SQLiteConnection;
import forge.util.TextUtil;
import forge.util.WordUtil;
import forge.util.storage.IStorage;

import static forge.localinstance.properties.ForgeConstants.DECK_COMMANDER_DIR;

public class SimulateMatch {
    public static void simulate(String[] args) {
        try {
            FModel.initialize(null, null);
        } catch (ExceptionInInitializerError | Exception e) {
            System.err.println("=== INITIALIZATION FAILED ===");
            e.printStackTrace(System.err);
            if (e.getCause() != null) {
                System.err.println("=== ROOT CAUSE ===");
                e.getCause().printStackTrace(System.err);
            }
            return;
        }

        // Check for replay simulation mode (standalone test)
        if (args.length >= 2 && "-replay".equals(args[1])) {
            String outputDir = args.length >= 3 ? args[2] : ".";
            runReplaySimulation(outputDir);
            return;
        }

        System.out.println("Simulation mode");
        if (args.length < 4) {
            argumentHelp();
            return;
        }

        final Map<String, List<String>> params = new HashMap<>();
        List<String> options = null;

        for (int i = 1; i < args.length; i++) {
            // "sim" is in the 0th slot
            final String a = args[i];

            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    System.err.println("Error at argument " + a);
                    argumentHelp();
                    return;
                }

                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                System.err.println("Illegal parameter usage");
                return;
            }
        }

        int nGames = 1;
        if (params.containsKey("n")) {
            // Number of games should only be a single string
            nGames = Integer.parseInt(params.get("n").get(0));
        }

        int matchSize = 0;
        if (params.containsKey("m")) {
            // Match size ("best of X games")
            matchSize = Integer.parseInt(params.get("m").get(0));
        }

        boolean outputGamelog = !params.containsKey("q");

        Long seed = null;
        if (params.containsKey("s")) {
            seed = Long.parseLong(params.get("s").get(0));
            MyRandom.setRandom(new Random(seed));
        }

        GameType type = GameType.Constructed;
        if (params.containsKey("f")) {
            type = GameType.valueOf(WordUtil.capitalize(params.get("f").get(0)));
        }

        GameRules rules = new GameRules(type);
        rules.setAppliedVariants(EnumSet.of(type));
        // Mark as simulation so replay JSON files get "sim_" prefix (not "replay_")
        rules.setSimulationMode(true);

        if (matchSize != 0) {
            rules.setGamesPerMatch(matchSize);
        }

        if (params.containsKey("t")) {
            simulateTournament(params, rules, outputGamelog);
            System.out.flush();
            return;
        }

        // Extended deck testing mode
        if (params.containsKey("xd")) {
            try {
                simulationSeries(params, rules, nGames, type);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            System.out.flush();
            return;
        }

        // Extended deck testing mode
        if (params.containsKey("xd")) {
            try {
                simulationSeries(params, rules, nGames, type);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            System.out.flush();
            return;
        }

        List<RegisteredPlayer> pp = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        // Seat order == "P1", "P2", … order in a scenario's events/players blocks.
        List<String> seatLobbyNames = new ArrayList<>();

        int i = 1;

        if (params.containsKey("d")) {
            for (String deck : params.get("d")) {
                Deck d = deckFromCommandLineParameter(deck, type);
                if (d == null) {
                    System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck, ", match cannot start"));
                    return;
                }
                if (i > 1) {
                    sb.append(" vs ");
                }
                String name = TextUtil.concatNoSpace("Ai(", String.valueOf(i), ")-", d.getName());
                sb.append(name);
                seatLobbyNames.add(name);

                RegisteredPlayer rp;

                if (type.equals(GameType.Commander)) {
                    rp = RegisteredPlayer.forCommander(d);
                } else {
                    rp = new RegisteredPlayer(d);
                }
                rp.setPlayer(GamePlayerUtil.createAiPlayer(name, i - 1));
                pp.add(rp);
                i++;
            }
        }

        if (params.containsKey("c")) {
            rules.setSimTimeout(Integer.parseInt(params.get("c").get(0)));
        }

        // Replay mode: reorder libraries from a replay JSON log
        if (params.containsKey("r")) {
            String replayPath = params.get("r").get(0);
            rules.setReplayLogPath(replayPath);
            System.out.println("Replay mode enabled: " + replayPath);

            // Also extract forced play sequence from CAST/ACTIVATE events
            Map<String, List<String>> playSeq =
                    forge.game.ReplayPlaySequenceParser.parse(new File(replayPath));
            if (!playSeq.isEmpty()) {
                rules.setForcedPlaySequence(playSeq);
                if (outputGamelog) {
                    System.out.println("[replay] Loaded forced play sequence for "
                            + playSeq.size() + " player(s)");
                }
            }
        }

        // Scenario mode: load starting hands and first draws from a scenario JSON file.
        // Deliberately NOT "-s" - that key collides with the RNG seed flag above
        // (params.containsKey("s") near the top of this method), which would try to
        // Long.parseLong() the scenario file path and crash before ever reaching here.
        Map<String, List<String>> scenarioExpectedHands = new LinkedHashMap<>();
        if (params.containsKey("scenario")) {
            String scenarioPath = params.get("scenario").get(0);
            loadScenarioIntoRules(scenarioPath, rules, scenarioExpectedHands, seatLobbyNames);
        }

        // Decklist-based mulligan config: -l <config1.json> [config2.json] ...
        if (params.containsKey("l")) {
            List<String> configPaths = params.get("l");
            for (int idx = 0; idx < configPaths.size() && idx < pp.size(); idx++) {
                pp.get(idx).setDecklistConfigPath(configPaths.get(idx));
                System.out.println("Decklist mulligan config for player " + (idx + 1) + ": " + configPaths.get(idx));
            }
        }

        sb.append(" - ").append(Lang.nounWithNumeral(nGames, "game")).append(" of ").append(type);
        if (seed != null) {
            sb.append(" seed ").append(seed);
        }

        System.out.println(sb.toString());

        Match mc = new Match(rules, pp, "Test");

        if (matchSize != 0) {
            int iGame = 0;
            while (!mc.isMatchOver()) {
                // play games until the match ends
                if (!scenarioExpectedHands.isEmpty()) {
                    runScenarioVerification(mc, iGame, outputGamelog, scenarioExpectedHands);
                } else {
                    simulateSingleMatch(mc, iGame, outputGamelog);
                }
                iGame++;
            }
        } else {
            for (int iGame = 0; iGame < nGames; iGame++) {
                if (!scenarioExpectedHands.isEmpty()) {
                    runScenarioVerification(mc, iGame, outputGamelog, scenarioExpectedHands);
                } else {
                    simulateSingleMatch(mc, iGame, outputGamelog);
                }
            }
        }

        System.out.flush();
    }

    private static void argumentHelp() {
        System.out.println("Syntax: forge.exe sim -d <deck1[.dck]> ... <deckX[.dck]> -D [D] -n [N] -m [M] -t [T] -p [P] -f [F] -q -r [R] -s [S] -scenario [SC] -l [L1] [L2]");
        System.out.println("\tsim - stands for simulation mode");
        System.out.println("\tdeck1 (or deck2,...,X) - constructed deck name or filename (has to be quoted when contains multiple words)");
        System.out.println("\tdeck is treated as file if it ends with a dot followed by three numbers or letters");
        System.out.println("\tD - absolute directory to load decks from");
        System.out.println("\tN - number of games, defaults to 1 (Ignores match setting)");
        System.out.println("\tM - Play full match of X games, typically 1,3,5 games. (Optional, overrides N)");
        System.out.println("\tT - Type of tournament to run with all provided decks (Bracket, RoundRobin, Swiss)");
        System.out.println("\tP - Amount of players per match (used only with Tournaments, defaults to 2)");
        System.out.println("\tF - format of games, defaults to constructed");
        System.out.println("\tS - RNG seed for simulation");
        System.out.println("\tc - Clock flag. Set the maximum time in seconds before calling the match a draw, defaults to 120.");
        System.out.println("\tq - Quiet flag. Output just the game result, not the entire game log.");
        System.out.println("\tr - Replay mode. Path to a replay JSON log; reorders libraries to match recorded draw order.");
        System.out.println("\tscenario - Scenario mode. Path to a scenario JSON (mtg-replay format, mode=scenario).");
        System.out.println("\t    Loads starting hands + first draws into GameRules and verifies them after game start.");
        System.out.println("\t    (Not \"-s\" - that's taken by the RNG seed flag above.)");
        System.out.println("\tl - Decklist mulligan config. One JSON path per player (Commander Decklist Notation format).");
        System.out.println();
        System.out.println("Alternative: forge.exe sim -replay [output_dir]");
        System.out.println("\t-replay - Run standalone replay notation test (generates JSON log)");
        System.out.println("\toutput_dir - Directory for JSON output (defaults to current directory)");
    }

    /**
     * Parses a scenario JSON file (mtg-replay format, mode=scenario) and
     * loads the per-player starting hands and first draws into {@code rules}.
     *
     * <p>The {@code expectedHands} map is also populated so that the calling
     * code can verify actual hands against the expected spec after game start.</p>
     *
     * @param path            path to the scenario JSON file
     * @param rules           GameRules to configure
     * @param expectedHands   map to fill with expected hands per player id ("P1", "P2", …)
     * @param seatLobbyNames  the lobby name assigned to each {@code -d} seat, in order
     *                        (index 0 = "P1", index 1 = "P2", …) — used to translate a
     *                        forced play sequence's player-id keys into the runtime names
     *                        AiController actually looks up.
     */
    private static void loadScenarioIntoRules(String path, GameRules rules,
                                               Map<String, List<String>> expectedHands,
                                               List<String> seatLobbyNames) {
        try (java.io.FileReader reader = new java.io.FileReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (!root.has("scenario")) {
                System.err.println("Scenario file has no 'scenario' object: " + path);
                return;
            }

            JsonObject scenario = root.getAsJsonObject("scenario");
            String scenarioType = scenario.has("type") ? scenario.get("type").getAsString() : "";
            String title = scenario.has("title") ? scenario.get("title").getAsString() : "(untitled)";

            System.out.println("Scenario: [" + scenarioType + "] " + title);

            if (!scenario.has("players")) {
                System.out.println("Scenario has no 'players' block — no hand constraints.");
                return;
            }

            Map<String, List<String>> startingHands = new LinkedHashMap<>();
            Map<String, List<String>> firstDraws = new LinkedHashMap<>();

            JsonObject players = scenario.getAsJsonObject("players");
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                String playerId = entry.getKey(); // "P1", "P2", …
                JsonObject playerObj = entry.getValue().getAsJsonObject();

                if (playerObj.has("starting_hand")) {
                    JsonArray arr = playerObj.getAsJsonArray("starting_hand");
                    List<String> hand = new ArrayList<>();
                    for (JsonElement e : arr) { hand.add(e.getAsString()); }
                    if (!hand.isEmpty()) {
                        startingHands.put(playerId, hand);
                        expectedHands.put(playerId, hand);
                    }
                }

                if (playerObj.has("first_draws")) {
                    JsonArray arr = playerObj.getAsJsonArray("first_draws");
                    List<String> draws = new ArrayList<>();
                    for (JsonElement e : arr) { draws.add(e.getAsString()); }
                    if (!draws.isEmpty()) firstDraws.put(playerId, draws);
                }
            }

            if (!startingHands.isEmpty()) {
                rules.setScenarioStartingHands(startingHands);
                System.out.println("Scenario: Starting hands loaded for " + startingHands.keySet());
            }
            if (!firstDraws.isEmpty()) {
                rules.setScenarioFirstDraws(firstDraws);
                System.out.println("Scenario: First draws loaded for " + firstDraws.keySet());
            }
            if ("opening_hand_test".equals(scenarioType)) {
                rules.setScenarioSkipMulligan(true);
                System.out.println("Scenario type: opening_hand_test — AI mulligans skipped");
                System.out.println("[DEBUG] ScenarioSkipMulligan flag set to: " + rules.isScenarioSkipMulligan());
            } else {
                System.out.println("[DEBUG] Scenario type '" + scenarioType + "' does not auto-skip mulligans");
            }

            // Forced play sequence: parse events array if present. Keys come back as raw
            // player ids ("P1", "P2", …) — translate to the actual per-seat lobby names
            // (Ai(N)-<deckName>) before handing to GameRules, since that's what
            // AiController looks up by at decision time. An id with no matching seat
            // (out-of-range index) is dropped rather than guessed.
            if (root.has("events") && root.get("events").isJsonArray()) {
                Map<String, List<String>> byId =
                        forge.game.ReplayLogParser.parseForcedSequenceEvents(root.getAsJsonArray("events"));
                Map<String, List<String>> sacById =
                        forge.game.ReplayLogParser.parseForcedSequenceSacrifice(root.getAsJsonArray("events"));
                Map<String, List<String>> playSeq = new LinkedHashMap<>();
                Map<String, List<String>> sacSeq = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> e : byId.entrySet()) {
                    int seatIdx = playerIdToSeatIndex(e.getKey());
                    if (seatIdx < 0 || seatIdx >= seatLobbyNames.size() || e.getValue().isEmpty()) continue;
                    playSeq.put(seatLobbyNames.get(seatIdx), e.getValue());
                    List<String> sac = sacById.get(e.getKey());
                    if (sac != null && !sac.isEmpty()) {
                        sacSeq.put(seatLobbyNames.get(seatIdx), sac);
                    }
                }
                if (!playSeq.isEmpty()) {
                    rules.setForcedPlaySequence(playSeq);
                    int total = playSeq.values().stream().mapToInt(List::size).sum();
                    System.out.println("Scenario: Loaded forced play sequence — " + total + " event(s) for " + playSeq.size() + " player(s)");
                    if (!sacSeq.isEmpty()) {
                        rules.setForcedPlaySequenceSacrifice(sacSeq);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to parse scenario file '" + path + "': " + e.getMessage());
        }
    }

    /** Maps a scenario player id ("P1", "P2", …) to a zero-based seat index, or -1 if unparseable. */
    private static int playerIdToSeatIndex(String playerId) {
        if (playerId == null || playerId.length() < 2 || playerId.charAt(0) != 'P') return -1;
        try {
            return Integer.parseInt(playerId.substring(1)) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Runs a single game in scenario mode.
     *
     * <p>Like {@link #simulateSingleMatch} but uses a {@code startGameHook} to
     * capture each player's opening hand immediately before the first turn.
     * After the game, prints a SCENARIO STARTING HAND VERIFICATION section
     * that compares expected vs. actual hands and emits PASS/FAIL per player.</p>
     *
     * @param mc              the Match to run
     * @param iGame           zero-based game index (for result line formatting)
     * @param outputGamelog   if true, print full game log entries
     * @param expectedHands   per-player expected card names (key = "P1", "P2", …)
     */
    private static void runScenarioVerification(final Match mc, int iGame, boolean outputGamelog,
                                                 Map<String, List<String>> expectedHands) {
        final StopWatch sw = new StopWatch();
        sw.start();

        final Game g1 = mc.createGame();
        // Without this, GameLogSaver.saveGameLog() below only writes the .txt narrative
        // log — gameLog.getReplayExporter() stays null, so no mtg-replay JSON is produced
        // and there's nothing for CSubmenuScenario/ReplayLogParser (or an external
        // validator) to load back and inspect.
        forge.game.GameLogSaver.enableReplayNotation(g1);

        // Captured hands — populated by the startGameHook (before T1 phase loop)
        final Map<String, List<String>> capturedHands = new LinkedHashMap<>();

        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> {
                mc.startGame(g1, () -> {
                    // Hook fires just before the first-turn phase loop.
                    // All mulligans are done; captured hands are the final opening hands.
                    List<forge.game.player.Player> gamePlayers = g1.getPlayers();
                    for (int pi = 0; pi < gamePlayers.size(); pi++) {
                        forge.game.player.Player p = gamePlayers.get(pi);
                        String pid = "P" + (pi + 1);
                        List<String> hand = new ArrayList<>();
                        for (forge.game.card.Card c : p.getCardsIn(forge.game.zone.ZoneType.Hand)) {
                            hand.add(c.getName());
                        }
                        capturedHands.put(pid, hand);
                    }
                });
                sw.stop();
            }, mc.getRules().getSimTimeout(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("Stopping slow match as draw");
        } catch (Exception | StackOverflowError e) {
            e.printStackTrace();
        } finally {
            if (sw.isStarted()) sw.stop();
            if (!g1.isGameOver()) g1.setGameOver(GameEndReason.Draw);
        }

        // Print game log
        List<GameLogEntry> log;
        if (outputGamelog) {
            log = g1.getGameLog().getLogEntries(null);
        } else {
            log = g1.getGameLog().getLogEntries(GameLogEntryType.MATCH_RESULTS);
        }
        Collections.reverse(log);
        for (GameLogEntry l : log) {
            System.out.println(l);
        }

        // Save game log to file
        try {
            File logFile = forge.game.GameLogSaver.saveGameLog(g1);
            if (logFile != null) {
                System.out.println("\nGame log saved to: " + logFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to save game log: " + e.getMessage());
        }

        // ── Scenario Starting Hand Verification ──────────────────────────────────
        System.out.println("\n===== SCENARIO STARTING HAND VERIFICATION =====");
        boolean allPassed = true;
        int constrainedPlayers = 0;

        for (Map.Entry<String, List<String>> exp : expectedHands.entrySet()) {
            String playerId = exp.getKey();
            List<String> expectedHand = exp.getValue();
            List<String> actualHand = capturedHands.getOrDefault(playerId, Collections.emptyList());

            if (expectedHand.isEmpty()) {
                System.out.println(playerId + ": SKIP (no starting hand constraint)");
                continue;
            }

            constrainedPlayers++;
            System.out.println(playerId + " Expected (" + expectedHand.size() + "): " + expectedHand);
            System.out.println(playerId + " Actual   (" + actualHand.size() + "): " + actualHand);

            // Compare multisets (order-insensitive — mulligans may rearrange nothing but be safe)
            Map<String, Long> expCounts = expectedHand.stream()
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            Map<String, Long> actCounts = actualHand.stream()
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            boolean passed = expCounts.equals(actCounts);

            System.out.println(playerId + ": " + (passed ? "PASS \u2713" : "FAIL \u2717"));
            if (!passed) {
                allPassed = false;
                // Show diff
                for (Map.Entry<String, Long> e : expCounts.entrySet()) {
                    long act = actCounts.getOrDefault(e.getKey(), 0L);
                    if (!e.getValue().equals(act)) {
                        System.out.println("  " + e.getKey() + ": expected " + e.getValue() + ", got " + act);
                    }
                }
            }
        }

        if (constrainedPlayers == 0) {
            System.out.println("No starting hand constraints defined — nothing to verify.");
        } else {
            System.out.println("SCENARIO RESULT: " + (allPassed ? "PASS \u2713" : "FAIL \u2717"));
        }
        System.out.println("================================================\n");

        // Game result line
        if (g1.getOutcome().isDraw()) {
            System.out.printf("\nGame Result: Game %d ended in a Draw! Took %d ms.%n", 1 + iGame, sw.getTime());
        } else {
            System.out.printf("\nGame Result: Game %d ended in %d ms. %s has won!\n%n",
                    1 + iGame, sw.getTime(), g1.getOutcome().getWinningLobbyPlayer().getName());
        }
    }

    public static void simulateSingleMatch(final Match mc, int iGame, boolean outputGamelog) {
        final StopWatch sw = new StopWatch();
        sw.start();

        final Game g1 = mc.createGame();
        // will run match in the same thread
        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> {
                mc.startGame(g1);
                sw.stop();
            }, mc.getRules().getSimTimeout(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("Stopping slow match as draw");
        } catch (Exception | StackOverflowError e) {
            e.printStackTrace();
        } finally {
            if (sw.isStarted()) {
                sw.stop();
            }
            g1.setGameOver(GameEndReason.Draw);
        }

        List<GameLogEntry> log;
        if (outputGamelog) {
            log = g1.getGameLog().getLogEntries(null);
        } else {
            log = g1.getGameLog().getLogEntries(GameLogEntryType.MATCH_RESULTS);
        }
        Collections.reverse(log);
        for (GameLogEntry l : log) {
            System.out.println(l);
        }

        // Save game log to file
        try {
            File logFile = forge.game.GameLogSaver.saveGameLog(g1);
            if (logFile != null) {
                System.out.println("\nGame log saved to: " + logFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to save game log: " + e.getMessage());
        }

        // If both players life totals to 0 in a single turn, the game should end in a draw
        if (g1.getOutcome().isDraw()) {
            System.out.printf("\nGame Result: Game %d ended in a Draw! Took %d ms.%n", 1 + iGame, sw.getTime());
        } else {
            System.out.printf("\nGame Result: Game %d ended in %d ms. %s has won!\n%n", 1 + iGame, sw.getTime(), g1.getOutcome().getWinningLobbyPlayer().getName());
        }
    }

    private static void simulateTournament(Map<String, List<String>> params, GameRules rules, boolean outputGamelog) {
        String tournament = params.get("t").get(0);
        AbstractTournament tourney = null;
        int matchPlayers = params.containsKey("p") ? Integer.parseInt(params.get("p").get(0)) : 2;

        DeckGroup deckGroup = new DeckGroup("SimulatedTournament");
        List<TournamentPlayer> players = new ArrayList<>();
        int numPlayers = 0;
        if (params.containsKey("d")) {
            for (String deck : params.get("d")) {
                Deck d = deckFromCommandLineParameter(deck, rules.getGameType());
                if (d == null) {
                    System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck, ", match cannot start"));
                    return;
                }

                deckGroup.addAiDeck(d);
                players.add(new TournamentPlayer(GamePlayerUtil.createAiPlayer(d.getName(), 0), numPlayers));
                numPlayers++;
            }
        }

        if (params.containsKey("D")) {
            // Direc
            String foldName = params.get("D").get(0);
            File folder = new File(foldName);
            if (!folder.isDirectory()) {
                System.out.println("Directory not found - " + foldName);
            } else {
                for (File deck : folder.listFiles((dir, name) -> name.endsWith(".dck"))) {
                    Deck d = DeckSerializer.fromFile(deck);
                    if (d == null) {
                        System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck.getName(), ", match cannot start"));
                        return;
                    }
                    deckGroup.addAiDeck(d);
                    players.add(new TournamentPlayer(GamePlayerUtil.createAiPlayer(d.getName(), 0), numPlayers));
                    numPlayers++;
                }
            }
        }

        if (numPlayers == 0) {
            System.out.println("No decks/Players found. Please try again.");
        }

        if ("bracket".equalsIgnoreCase(tournament)) {
            tourney = new TournamentBracket(players, matchPlayers);
        } else if ("roundrobin".equalsIgnoreCase(tournament)) {
            tourney = new TournamentRoundRobin(players, matchPlayers);
        } else if ("swiss".equalsIgnoreCase(tournament)) {
            tourney = new TournamentSwiss(players, matchPlayers);
        }
        if (tourney == null) {
            System.out.println("Failed to initialize tournament, bailing out");
            return;
        }

        tourney.initializeTournament();

        String lastWinner = "";
        int curRound = 0;
        System.out.println(TextUtil.concatNoSpace("Starting a ", tournament, " tournament with ",
                String.valueOf(numPlayers), " players over ",
                String.valueOf(tourney.getTotalRounds()), " rounds"));
        while (!tourney.isTournamentOver()) {
            if (tourney.getActiveRound() != curRound) {
                if (curRound != 0) {
                    System.out.println(TextUtil.concatNoSpace("End Round - ", String.valueOf(curRound)));
                }
                curRound = tourney.getActiveRound();
                System.out.println();
                System.out.println(TextUtil.concatNoSpace("Round ", String.valueOf(curRound), " Pairings:"));

                for (TournamentPairing pairing : tourney.getActivePairings()) {
                    System.out.println(pairing.outputHeader());
                }
                System.out.println();
            }

            TournamentPairing pairing = tourney.getNextPairing();
            List<RegisteredPlayer> regPlayers = AbstractTournament.registerTournamentPlayers(pairing, deckGroup);

            StringBuilder sb = new StringBuilder();
            sb.append("Round ").append(tourney.getActiveRound()).append(" - ");
            sb.append(pairing.outputHeader());
            System.out.println(sb.toString());

            if (!pairing.isBye()) {
                Match mc = new Match(rules, regPlayers, "TourneyMatch");

                int exceptions = 0;
                int iGame = 0;
                while (!mc.isMatchOver()) {
                    // play games until the match ends
                    try {
                        simulateSingleMatch(mc, iGame, outputGamelog);
                        iGame++;
                    } catch (Exception e) {
                        exceptions++;
                        System.out.println(e.toString());
                        if (exceptions > 5) {
                            System.out.println("Exceeded number of exceptions thrown. Abandoning match...");
                            break;
                        } else {
                            System.out.println("Game threw exception. Abandoning game and continuing...");
                        }
                    }

                }
                LobbyPlayer winner = mc.getWinner().getPlayer();
                for (TournamentPlayer tp : pairing.getPairedPlayers()) {
                    if (winner.equals(tp.getPlayer())) {
                        pairing.setWinner(tp);
                        lastWinner = winner.getName();
                        System.out.println(TextUtil.concatNoSpace("Match Winner - ", lastWinner, "!"));
                        System.out.println();
                        break;
                    }
                }
            }

            tourney.reportMatchCompletion(pairing);
        }
        tourney.outputTournamentResults();
    }

    public static Match simulateOffthreadGame(List<Deck> decks, GameType format, int games) {
        return null;
    }

    private static Deck deckFromCommandLineParameter(String deckname, GameType type) {
        int dotpos = deckname.lastIndexOf('.');
        if (dotpos > 0 && dotpos == deckname.length() - 4) {
            String baseDir = type.equals(GameType.Commander) ?
                    ForgeConstants.DECK_COMMANDER_DIR : ForgeConstants.DECK_CONSTRUCTED_DIR;

            File f = new File(baseDir + deckname);
            if (!f.exists()) {
                System.out.println("No deck found in " + baseDir);
            }

            return DeckSerializer.fromFile(f);
        }

        IStorage<Deck> deckStore = null;

        // Add other game types here...
        if (type.equals(GameType.Commander)) {
            deckStore = FModel.getDecks().getCommander();
        } else {
            deckStore = FModel.getDecks().getConstructed();
        }

        return deckStore.get(deckname);
    }

    // Extended deck testing simulation series
    private static void simulationSeries(Map<String, List<String>> params, GameRules rules,
                                         int nGames, GameType type) throws SQLException {
        String deckIdeaPath = params.get("xd").get(1);
        int mode = Integer.parseInt(params.get("xd").get(0));

        System.out.println("Extended deck testing mode: " + mode);
        System.out.println("Deck idea file: " + deckIdeaPath);

        // Read deck from file
        List<String> deckLines = readAndSplitFile(deckIdeaPath);
        if (deckLines == null || deckLines.isEmpty()) {
            System.out.println("Could not read deck file: " + deckIdeaPath);
            return;
        }

        // Parse the deck
        DeckRecognizer recognizer = new DeckRecognizer();
        List<DeckRecognizer.Token> tokens = recognizer.parseCardList(deckLines.toArray(new String[0]));
        Deck testDeck = DeckImportController.createDeckOutof(tokens, false);

        if (testDeck == null) {
            System.out.println("Could not parse deck from file");
            return;
        }

        // Find sparring deck
        Deck sparringDeck = findDeckIn(DECK_COMMANDER_DIR, "DO Nothing DECK.dck");
        if (sparringDeck == null) {
            System.out.println("Could not find sparring deck");
            return;
        }

        // Initialize database
        SQLiteConnection.createNewTable();
        int setOfGamesID = getFirstInsertOfGameset(testDeck.getName());

        // Initialize deck stats
        Map<String, DeckStats> deckStatsMap = initDeckstats(testDeck, sparringDeck, setOfGamesID);

        StopWatch totalTime = new StopWatch();
        totalTime.start();

        // Run games
        for (int iGame = 0; iGame < nGames; iGame++) {
            List<RegisteredPlayer> players = new ArrayList<>();

            RegisteredPlayer rp1;
            RegisteredPlayer rp2;

            if (type.equals(GameType.Commander)) {
                rp1 = RegisteredPlayer.forCommander(testDeck);
                rp2 = RegisteredPlayer.forCommander(sparringDeck);
            } else {
                rp1 = new RegisteredPlayer(testDeck);
                rp2 = new RegisteredPlayer(sparringDeck);
            }

            rp1.setPlayer(GamePlayerUtil.createAiPlayer("TestDeck-" + testDeck.getName(), 0));
            rp2.setPlayer(GamePlayerUtil.createAiPlayer("Sparring-" + sparringDeck.getName(), 1));

            players.add(rp1);
            players.add(rp2);

            Match mc = new Match(rules, players, "DeckTest");

            GameAnalysis analysis = simulateSingleMatchWithAnalysis(mc, iGame, false);

            if (analysis != null) {
                String winnerName = analysis.getWinningPlayer();
                DeckStats winnerStats = deckStatsMap.get(winnerName);
                if (winnerStats != null) {
                    winnerStats.addVictoryStats(analysis.getLastTurnNumber(), analysis.getLifeDelta());
                }

                // Record starting hand stats for test deck
                DeckStats testDeckStats = deckStatsMap.get("TestDeck-" + testDeck.getName());
                if (testDeckStats != null && analysis.getCardsinStartingHand() != null) {
                    InsertStartingHandStats(testDeckStats.getId(), analysis.getCardsinStartingHand(),
                            analysis.getLifeDelta() + analysis.getLastTurnNumber() * 10);
                }
            }

            if ((iGame + 1) % 100 == 0) {
                System.out.println("Completed " + (iGame + 1) + " games");
            }
        }

        totalTime.stop();

        // Update database with final stats
        for (DeckStats stats : deckStatsMap.values()) {
            SQLiteConnection.updateDeckStatsBy(stats);
        }

        SQLiteConnection.insertGameAnalysis(setOfGamesID, nGames, (int)(totalTime.getTime() / 60000));

        // Display results
        displayDeckStats(deckStatsMap, nGames);
    }

    private static List<String> readAndSplitFile(String filePath) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return null;
        }
        return lines;
    }

    private static Deck findDeckIn(String directory, String deckName) {
        File f = new File(directory + deckName);
        if (f.exists()) {
            return DeckSerializer.fromFile(f);
        }
        return null;
    }

    private static Map<String, DeckStats> initDeckstats(Deck testDeck, Deck sparringDeck,
                                                         int setOfGamesID) {
        Map<String, DeckStats> statsMap = new HashMap<>();

        String testName = "TestDeck-" + testDeck.getName();
        String sparringName = "Sparring-" + sparringDeck.getName();

        DeckStats testStats = new DeckStats(testName);
        testStats.setId(SQLiteConnection.insertDeckStats(setOfGamesID, testName));
        statsMap.put(testName, testStats);

        DeckStats sparringStats = new DeckStats(sparringName);
        sparringStats.setId(SQLiteConnection.insertDeckStats(setOfGamesID, sparringName));
        statsMap.put(sparringName, sparringStats);

        return statsMap;
    }

    private static int getFirstInsertOfGameset(String deckName) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String dateStr = now.format(formatter);
        return SQLiteConnection.insertSetOfGames(deckName, dateStr);
    }

    private static void InsertStartingHandStats(int deckStatsId, CardCollectionView cards, int score) {
        StringBuilder combinedHand = new StringBuilder();
        for (Card c : cards) {
            SQLiteConnection.insertorUpdateCardOccurence(deckStatsId, c.getName(), score);
            if (combinedHand.length() > 0) {
                combinedHand.append("|");
            }
            combinedHand.append(c.getName());
        }
        SQLiteConnection.insertorUpdateCardInHandsOccurence(deckStatsId, combinedHand.toString(), score);
    }

    private static void displayDeckStats(Map<String, DeckStats> statsMap, int totalGames) {
        System.out.println("\n========== Deck Statistics ==========");
        System.out.println("Total games played: " + totalGames);
        for (DeckStats stats : statsMap.values()) {
            System.out.println(stats.toString());
            if (stats.getWinCount() > 0) {
                double winRate = (double) stats.getWinCount() / totalGames * 100;
                System.out.printf("  Win rate: %.2f%%%n", winRate);
            }
        }
        System.out.println("======================================\n");
    }

    private static GameAnalysis simulateSingleMatchWithAnalysis(final Match mc, int iGame,
                                                                 boolean outputGamelog) {
        final StopWatch sw = new StopWatch();
        sw.start();

        final Game g1 = mc.createGame();

        // Store starting hands
        for (forge.game.player.Player p : g1.getPlayers()) {
            p.setCardsInStartingHand(p.getCardsIn(forge.game.zone.ZoneType.Hand));
        }

        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> {
                mc.startGame(g1);
                sw.stop();
            }, mc.getRules().getSimTimeout(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("Stopping slow match as draw");
        } catch (Exception | StackOverflowError e) {
            e.printStackTrace();
        } finally {
            if (sw.isStarted()) {
                sw.stop();
            }
            if (!g1.isGameOver()) {
                g1.setGameOver(GameEndReason.Draw);
            }
        }

        // Create game analysis
        GameAnalysis analysis = null;
        if (!g1.getOutcome().isDraw()) {
            String winnerName = g1.getOutcome().getWinningLobbyPlayer().getName();
            int lastTurn = g1.getPhaseHandler().getTurn();

            // Calculate life delta
            int lifeDelta = 0;
            forge.game.player.Player winner = null;
            for (forge.game.player.Player p : g1.getPlayers()) {
                if (p.getName().equals(winnerName)) {
                    winner = p;
                    lifeDelta = p.getLife();
                    break;
                }
            }

            CardCollectionView startingHand = winner != null ? winner.getCardsInStartingHand() : null;
            int manaScore = winner != null ? winner.countManaLandAndRampsInStartingHand() : 0;

            analysis = new GameAnalysis(winnerName, lastTurn, lifeDelta, startingHand, manaScore);
        }

        if (outputGamelog) {
            List<GameLogEntry> log = g1.getGameLog().getLogEntries(null);
            Collections.reverse(log);
            for (GameLogEntry l : log) {
                System.out.println(l);
            }
        }

        // Save game log to file
        try {
            File logFile = forge.game.GameLogSaver.saveGameLog(g1);
            if (logFile != null && outputGamelog) {
                System.out.println("\nGame log saved to: " + logFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Failed to save game log: " + e.getMessage());
        }

        return analysis;
    }

    /**
     * Run the replay notation simulation (standalone test).
     * This generates a simulated game and exports it in JSON Replay Notation format.
     */
    private static void runReplaySimulation(String outputDir) {
        System.out.println("MTG Replay Notation - Standalone Simulation");
        System.out.println("============================================\n");

        try {
            File outputDirectory = new File(outputDir);
            GameReplaySimulation simulation = new GameReplaySimulation();
            File jsonFile = simulation.simulateGame(outputDirectory);

            System.out.println("\n✅ Simulation complete!");
            System.out.println("📄 JSON file: " + jsonFile.getAbsolutePath());
            System.out.println("\nYou can now:");
            System.out.println("  - View the JSON file");
            System.out.println("  - Validate it");
            System.out.println("  - Use it for replay or analysis");

        } catch (Exception e) {
            System.err.println("\n❌ Simulation failed:");
            e.printStackTrace();
            System.exit(1);
        }
    }

}
