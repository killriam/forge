package forge.game;

/**
 * Represents a single detected blunder or notable moment in a game.
 * Blunders are flagged by BlunderDetector from the event stream
 * and presented in the Game Report panel of the Learning Viewer.
 */
public class BlunderEntry {

    /** Classification of severity. */
    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    /** Type/category of blunder or notable event. */
    public enum Type {
        HEAVY_DAMAGE("⚡ Heavy Damage"),
        BOARD_SWING("⚔ Board Swing"),
        POSSIBLE_MISSED_LETHAL("\uD83D\uDEA8 Possible Missed Lethal"),
        BEHIND_ON_CARDS("\uD83C\uDCCF Card Disadvantage"),
        BEHIND_ON_BOARD("\uD83D\uDCC9 Behind on Board");

        public final String label;

        Type(String label) { this.label = label; }
    }

    public final int turnNumber;
    public final Type type;
    public final Severity severity;
    public final String explanation;
    /** The player this blunder is attributed to (display name). */
    public final String playerName;

    public BlunderEntry(int turnNumber, Type type, Severity severity,
                        String explanation, String playerName) {
        this.turnNumber = turnNumber;
        this.type = type;
        this.severity = severity;
        this.explanation = explanation;
        this.playerName = playerName;
    }
}


