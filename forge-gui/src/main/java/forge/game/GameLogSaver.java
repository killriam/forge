package forge.game;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import forge.localinstance.properties.ForgeConstants;

/**
 * Utility class for saving game logs to files.
 */
public class GameLogSaver {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

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
}

