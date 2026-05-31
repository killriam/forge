package forge.game;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import forge.game.log.ReplayNotationExporter;
import forge.game.log.ReplayL2Generator;
import forge.game.log.ReplayNotationValidator;
import forge.game.log.model.ReplayLog;
import forge.localinstance.properties.ForgeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for saving game logs to files.
 */
public class GameLogSaver {

    private static final Logger LOG = LoggerFactory.getLogger(GameLogSaver.class);
    private static final DateTimeFormatter FILENAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HEADER_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    /**
     * Enable replay notation logging for a game.
     * This should be called at game start to capture all events.
     *
     * @param game The game to enable replay notation for
     */
    public static void enableReplayNotation(Game game) {
        if (game == null) {
            LOG.warn("enableReplayNotation: game is null");
            return;
        }

        GameLog gameLog = game.getGameLog();
        if (gameLog == null) {
            LOG.warn("enableReplayNotation: gameLog is null for game {}", game.getId());
            return;
        }

        if (gameLog.getReplayExporter() != null) {
            LOG.debug("Replay notation already enabled for game {}", game.getId());
            return;
        }

        ReplayNotationExporter exporter = new ReplayNotationExporter(game);
        gameLog.enableReplayNotation(exporter);
        LOG.debug("Replay notation enabled for game {}", game.getId());
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

        File logDir = new File(ForgeConstants.GAME_LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        String timestamp = FILENAME_FORMAT.format(Instant.now());
        String gameType = game.getRules() != null && game.getRules().getGameType() != null ?
                          game.getRules().getGameType().toString() : "Game";
        String filename = String.format("gamelog_%s_%s.txt", gameType, timestamp);
        File logFile = new File(logDir, filename);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile))) {
            writer.write("Game Type: " + gameType);
            writer.newLine();
            writer.write("Date: " + HEADER_FORMAT.format(Instant.now()));
            writer.newLine();
            writer.write("======================================");
            writer.newLine();
            writer.newLine();

            List<GameLogEntry> entries = gameLog.getLogEntries(null);
            for (int i = entries.size() - 1; i >= 0; i--) {
                GameLogEntry entry = entries.get(i);
                writer.write(entry.toString().replace("[COMPUTER]", "[AI]"));
                writer.newLine();
            }

            writer.flush();

            ReplayNotationExporter replayExporter = gameLog.getReplayExporter();
            if (replayExporter != null) {
                try {
                    File jsonFile = replayExporter.exportToFile(logDir, timestamp);
                    if (jsonFile != null) {
                        LOG.info("JSON replay saved: {}", jsonFile.getAbsolutePath());
                    } else {
                        LOG.warn("exportToFile() returned null for game {}", game.getId());
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to save JSON replay notation: {}", e.getMessage());
                }
            } else {
                LOG.debug("No replay exporter for game {} — JSON not saved", game.getId());
            }

            return logFile;
        } catch (IOException e) {
            LOG.error("Failed to save game log: {}", e.getMessage());
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

        File logDir = new File(ForgeConstants.GAME_LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        String timestamp = FILENAME_FORMAT.format(Instant.now());
        String gameType = gameView.getGameType() != null ? gameView.getGameType().toString() : "Game";
        String filename = String.format("gamelog_%s_%s.txt", gameType, timestamp);
        File logFile = new File(logDir, filename);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile))) {
            writer.write("======================================");
            writer.newLine();
            writer.write("Forge Game Log");
            writer.newLine();
            writer.write("Game Type: " + gameType);
            writer.newLine();
            writer.write("Date: " + HEADER_FORMAT.format(Instant.now()));
            writer.newLine();
            writer.write("======================================");
            writer.newLine();
            writer.newLine();

            List<GameLogEntry> entries = gameLog.getLogEntries(null);
            for (int i = entries.size() - 1; i >= 0; i--) {
                GameLogEntry entry = entries.get(i);
                writer.write(entry.toString().replace("[COMPUTER]", "[AI]"));
                writer.newLine();
            }

            writer.flush();

            ReplayNotationExporter replayExporter = gameLog.getReplayExporter();
            if (replayExporter != null) {
                try {
                    File jsonFile = replayExporter.exportToFile(logDir, timestamp);
                    if (jsonFile != null) {
                        LOG.info("JSON replay saved: {}", jsonFile.getAbsolutePath());
                    } else {
                        LOG.warn("exportToFile() returned null");
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to save JSON replay notation: {}", e.getMessage());
                }
            } else {
                LOG.debug("No replay exporter — JSON not saved");
            }

            return logFile;
        } catch (IOException e) {
            LOG.error("Failed to save game log: {}", e.getMessage());
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
            ReplayNotationExporter exporter = new ReplayNotationExporter(game);

            if (includeL2) {
                ReplayLog replayLog = exporter.getReplayLog();
                ReplayL2Generator l2Generator = new ReplayL2Generator(replayLog);
                l2Generator.generateL2Units();

                ReplayNotationValidator validator = new ReplayNotationValidator(replayLog);
                if (!validator.validate()) {
                    LOG.warn("Replay log validation warnings:\n{}", validator.getReport());
                }
            }

            File logDir = new File(ForgeConstants.GAME_LOG_DIR);
            File jsonFile = exporter.exportToFile(logDir);

            if (jsonFile != null) {
                LOG.info("Replay notation saved: {}", jsonFile.getAbsolutePath());
            }

            return jsonFile;

        } catch (Exception e) {
            LOG.error("Failed to save replay notation log: {}", e.getMessage());
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
