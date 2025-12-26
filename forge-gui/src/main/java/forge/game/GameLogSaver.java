package forge.game;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import forge.game.log.ReplayNotationExporter;
import forge.game.log.ReplayL2Generator;
import forge.game.log.ReplayNotationValidator;
import forge.game.log.model.ReplayLog;
import forge.localinstance.properties.ForgeConstants;

/**
 * Utility class for saving game logs to files.
 */
public class GameLogSaver {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    /**
     * Enable replay notation logging for a game.
     * This should be called at game start to capture all events.
     *
     * @param game The game to enable replay notation for
     */
    public static void enableReplayNotation(Game game) {
        if (game == null) {
            System.err.println("[Replay Notation] ❌ enableReplayNotation: game is NULL!");
            return;
        }

        GameLog gameLog = game.getGameLog();
        if (gameLog == null) {
            System.err.println("[Replay Notation] ❌ enableReplayNotation: gameLog is NULL!");
            return;
        }

        // Check if already enabled
        if (gameLog.getReplayExporter() != null) {
            System.out.println("[Replay Notation] ⚠️ Already enabled for game " + game.getId());
            return; // Already enabled
        }

        // Create and register the replay notation exporter
        System.out.println("[Replay Notation] DEBUG: Creating ReplayNotationExporter...");
        ReplayNotationExporter exporter = new ReplayNotationExporter(game);
        System.out.println("[Replay Notation] DEBUG: Calling gameLog.enableReplayNotation()...");
        gameLog.enableReplayNotation(exporter);
        System.out.println("[Replay Notation] DEBUG: Verification - exporter is now: " + (gameLog.getReplayExporter() != null ? "SET ✅" : "STILL NULL ❌"));
    }

    /**
     * Saves the game log to a file in the game logs directory.
     *
     * @param game The game containing the log to save
     * @return The file that was created, or null if save failed
     */
    public static File saveGameLog(Game game) {
        if (game == null) {
            return null;
        }

        GameLog gameLog = game.getGameLog();
        if (gameLog == null) {
            return null;
        }

        // Create game logs directory if it doesn't exist
        File logDir = new File(ForgeConstants.GAME_LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        // Generate filename with timestamp
        String timestamp = DATE_FORMAT.format(new Date());
        String gameType = game.getRules() != null && game.getRules().getGameType() != null ?
                          game.getRules().getGameType().toString() : "Game";
        String filename = String.format("gamelog_%s_%s.txt", gameType, timestamp);
        File logFile = new File(logDir, filename);

        // Write log to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile))) {
            // Write header
            writer.write("Game Type: " + gameType);
            writer.newLine();
            writer.write("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.newLine();
            writer.write("======================================");
            writer.newLine();
            writer.newLine();

            // Write log entries
            List<GameLogEntry> entries = gameLog.getLogEntries(null);
            for (int i = entries.size() - 1; i >= 0; i--) {
                GameLogEntry entry = entries.get(i);
                String message = entry.toString().replace("[COMPUTER]", "[AI]");
                writer.write(message);
                writer.newLine();
            }

            writer.flush();

            // Also save JSON replay notation if enabled
            ReplayNotationExporter replayExporter = gameLog.getReplayExporter();
            System.out.println("[Replay Notation DEBUG] replayExporter: " + (replayExporter != null ? "NOT NULL ✅" : "NULL ❌"));
            if (replayExporter != null) {
                try {
                    System.out.println("[Replay Notation DEBUG] Calling exportToFile()...");
                    File jsonFile = replayExporter.exportToFile(logDir);
                    if (jsonFile != null) {
                        System.out.println("[Replay Notation] ✅ JSON replay notation saved to: " + jsonFile.getAbsolutePath());
                    } else {
                        System.err.println("[Replay Notation] ❌ exportToFile() returned null!");
                    }
                } catch (Exception e) {
                    System.err.println("[Replay Notation] ❌ Failed to save JSON replay notation: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("[Replay Notation] ❌ replayExporter is NULL - Auto-enable may have failed or wrong Game object!");
                System.err.println("[Replay Notation] DEBUG: GameLog class: " + gameLog.getClass().getName());
                System.err.println("[Replay Notation] DEBUG: Game ID: " + game.getId());
            }

            return logFile;
        } catch (IOException e) {
            System.err.println("Failed to save game log: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Saves the game log to a file in the game logs directory.
     *
     * @param gameView The game view containing the log to save
     * @return The file that was created, or null if save failed
     */
    public static File saveGameLog(GameView gameView) {
        if (gameView == null) {
            return null;
        }

        GameLog gameLog = gameView.getGameLog();
        if (gameLog == null) {
            return null;
        }

        // Create game logs directory if it doesn't exist
        File logDir = new File(ForgeConstants.GAME_LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        // Generate filename with timestamp
        String timestamp = DATE_FORMAT.format(new Date());
        String gameType = gameView.getGameType() != null ? gameView.getGameType().toString() : "Game";
        String filename = String.format("gamelog_%s_%s.txt", gameType, timestamp);
        File logFile = new File(logDir, filename);

        // Write log to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile))) {
            // Write header
            writer.write("======================================");
            writer.newLine();
            writer.write("Forge Game Log");
            writer.newLine();
            writer.write("Game Type: " + gameType);
            writer.newLine();
            writer.write("Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.newLine();
            writer.write("======================================");
            writer.newLine();
            writer.newLine();

            // Write log entries
            List<GameLogEntry> entries = gameLog.getLogEntries(null);
            for (int i = entries.size() - 1; i >= 0; i--) {
                GameLogEntry entry = entries.get(i);
                String message = entry.toString().replace("[COMPUTER]", "[AI]");
                writer.write(message);
                writer.newLine();
            }

            writer.flush();

            // Also save JSON replay notation if enabled
            System.out.println("[Replay Notation DEBUG] saveGameLog(GameView) called");
            ReplayNotationExporter replayExporter = gameLog.getReplayExporter();
            System.out.println("[Replay Notation DEBUG] replayExporter: " + (replayExporter != null ? "NOT NULL ✅" : "NULL ❌"));
            if (replayExporter != null) {
                try {
                    System.out.println("[Replay Notation DEBUG] Calling exportToFile()...");
                    File jsonFile = replayExporter.exportToFile(logDir);
                    if (jsonFile != null) {
                        System.out.println("[Replay Notation] ✅ JSON replay notation saved to: " + jsonFile.getAbsolutePath());
                    } else {
                        System.err.println("[Replay Notation] ❌ exportToFile() returned null!");
                    }
                } catch (Exception e) {
                    System.err.println("[Replay Notation] ❌ Failed to save JSON replay notation: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("[Replay Notation] ❌ replayExporter is NULL in GameView saveGameLog!");
                System.err.println("[Replay Notation] DEBUG: GameLog class: " + gameLog.getClass().getName());
            }

            return logFile;
        } catch (IOException e) {
            System.err.println("Failed to save game log: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Saves the game log and returns the file path as a string.
     *
     * @param gameView The game view containing the log to save
     * @return The path to the saved file, or null if save failed
     */
    public static String saveGameLogAndGetPath(GameView gameView) {
        File logFile = saveGameLog(gameView);
        return logFile != null ? logFile.getAbsolutePath() : null;
    }

    /**
     * Saves the game log in JSON Replay Notation format.
     * This is the new format specified in MTG_REPLAY_NOTATION.md.
     *
     * @param game The game containing the log to save
     * @param includeL2 Whether to generate Level 2 (Learning View) units
     * @return The file that was created, or null if save failed
     */
    public static File saveGameLogReplayNotation(Game game, boolean includeL2) {
        if (game == null) {
            return null;
        }

        try {
            // Create exporter
            ReplayNotationExporter exporter = new ReplayNotationExporter(game);

            // Note: The exporter needs to be integrated with the game event system
            // to capture events during gameplay. This is a placeholder structure.
            // See MTG_REPLAY_NOTATION.md section 14 for integration details.

            // Generate L2 units if requested
            if (includeL2) {
                ReplayLog replayLog = exporter.getReplayLog();
                ReplayL2Generator l2Generator = new ReplayL2Generator(replayLog);
                l2Generator.generateL2Units();

                // Optionally validate
                ReplayNotationValidator validator = new ReplayNotationValidator(replayLog);
                if (!validator.validate()) {
                    System.err.println("Replay log validation warnings:");
                    System.err.println(validator.getReport());
                }
            }

            // Export to file
            File logDir = new File(ForgeConstants.GAME_LOG_DIR);
            File jsonFile = exporter.exportToFile(logDir);

            if (jsonFile != null) {
                System.out.println("Replay notation log saved to: " + jsonFile.getAbsolutePath());
            }

            return jsonFile;

        } catch (Exception e) {
            System.err.println("Failed to save replay notation log: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Saves both text and JSON replay notation logs.
     *
     * @param game The game containing the log to save
     * @return Array with [textFile, jsonFile], or nulls if save failed
     */
    public static File[] saveGameLogBothFormats(Game game) {
        File textFile = saveGameLog(game);
        File jsonFile = saveGameLogReplayNotation(game, true);
        return new File[] { textFile, jsonFile };
    }
}


