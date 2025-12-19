package forge.game;

import java.io.File;

/**
 * Simple test to verify GameLogSaver functionality.
 * This is a manual test - run after building the project.
 */
public class GameLogSaverTest {

    public static void main(String[] args) {
        System.out.println("GameLogSaver Test");
        System.out.println("=================\n");

        // Test 1: Null safety
        System.out.println("Test 1: Null GameView handling");
        File result1 = GameLogSaver.saveGameLog((GameView) null);
        System.out.println("Result: " + (result1 == null ? "PASS - Returned null as expected" : "FAIL"));
        System.out.println();

        // Test 1b: Null Game handling
        System.out.println("Test 1b: Null Game handling");
        File result1b = GameLogSaver.saveGameLog((Game) null);
        System.out.println("Result: " + (result1b == null ? "PASS - Returned null as expected" : "FAIL"));
        System.out.println();

        // Test 2: Path generation
        System.out.println("Test 2: Check expected log directory path");
        System.out.println("Expected directory: " + forge.localinstance.properties.ForgeConstants.GAME_LOG_DIR);
        File logDir = new File(forge.localinstance.properties.ForgeConstants.GAME_LOG_DIR);
        System.out.println("Directory exists: " + logDir.exists());
        System.out.println();

        // Note: Full integration test requires a running game with GameView
        System.out.println("Note: Full integration test requires running an actual game.");
        System.out.println("After playing a game, check the following directory for log files:");
        System.out.println("  " + forge.localinstance.properties.ForgeConstants.GAME_LOG_DIR);
        System.out.println("\nExpected filename format: gamelog_<GameType>_YYYY-MM-DD_HH-mm-ss.txt");
    }
}
