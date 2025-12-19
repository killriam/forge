package forge.view;

import java.io.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import forge.util.SQLiteConnection;
import forge.util.TextUtil;
import forge.util.WordUtil;
import forge.util.storage.IStorage;

import static forge.localinstance.properties.ForgeConstants.DECK_COMMANDER_DIR;

public class SimulateMatch {
    public static void simulate(String[] args) {
        FModel.initialize(null, null);

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

        GameType type = GameType.Constructed;
        if (params.containsKey("f")) {
            type = GameType.valueOf(WordUtil.capitalize(params.get("f").get(0)));
        }

        GameRules rules = new GameRules(type);
        rules.setAppliedVariants(EnumSet.of(type));

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

        List<RegisteredPlayer> pp = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

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

        sb.append(" - ").append(Lang.nounWithNumeral(nGames, "game")).append(" of ").append(type);

        System.out.println(sb.toString());

        Match mc = new Match(rules, pp, "Test");

        if (matchSize != 0) {
            int iGame = 0;
            while (!mc.isMatchOver()) {
                // play games until the match ends
                simulateSingleMatch(mc, iGame, outputGamelog);
                iGame++;
            }
        } else {
            for (int iGame = 0; iGame < nGames; iGame++) {
                simulateSingleMatch(mc, iGame, outputGamelog);
            }
        }

        System.out.flush();
    }

    private static void argumentHelp() {
        System.out.println("Syntax: forge.exe sim -d <deck1[.dck]> ... <deckX[.dck]> -D [D] -n [N] -m [M] -t [T] -p [P] -f [F] -q");
        System.out.println("\tsim - stands for simulation mode");
        System.out.println("\tdeck1 (or deck2,...,X) - constructed deck name or filename (has to be quoted when contains multiple words)");
        System.out.println("\tdeck is treated as file if it ends with a dot followed by three numbers or letters");
        System.out.println("\tD - absolute directory to load decks from");
        System.out.println("\tN - number of games, defaults to 1 (Ignores match setting)");
        System.out.println("\tM - Play full match of X games, typically 1,3,5 games. (Optional, overrides N)");
        System.out.println("\tT - Type of tournament to run with all provided decks (Bracket, RoundRobin, Swiss)");
        System.out.println("\tP - Amount of players per match (used only with Tournaments, defaults to 2)");
        System.out.println("\tF - format of games, defaults to constructed");
        System.out.println("\tc - Clock flag. Set the maximum time in seconds before calling the match a draw, defaults to 120.");
        System.out.println("\tq - Quiet flag. Output just the game result, not the entire game log.");
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
            if (!g1.isGameOver()) {
                g1.setGameOver(GameEndReason.Draw);
            }
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

 }
