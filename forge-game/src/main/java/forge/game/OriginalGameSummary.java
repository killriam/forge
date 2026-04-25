package forge.game;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight DTO capturing the turn-by-turn summary of an original game.
 * Built by the Game Learning Viewer from {@code ReplayStateReconstructor} data
 * and passed through {@link GameRules} so that {@code ViewWinLose} can compare
 * the original game with a replay.
 */
public class OriginalGameSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<TurnData> turns;
    private final String winnerName;
    private final int totalTurns;

    public OriginalGameSummary(List<TurnData> turns, String winnerName, int totalTurns) {
        this.turns = turns != null ? new ArrayList<>(turns) : new ArrayList<>();
        this.winnerName = winnerName;
        this.totalTurns = totalTurns;
    }

    public List<TurnData> getTurns() {
        return turns;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public int getTotalTurns() {
        return totalTurns;
    }

    /**
     * Find the first turn where original and replay game states diverge.
     * Compares life totals as the primary divergence indicator.
     *
     * @param replayTurns turn-by-turn data from the replay game
     * @return the 1-based turn number of first divergence, or -1 if identical
     */
    public int findDivergenceTurn(List<TurnData> replayTurns) {
        int limit = Math.min(turns.size(), replayTurns.size());
        for (int i = 0; i < limit; i++) {
            TurnData orig = turns.get(i);
            TurnData replay = replayTurns.get(i);
            if (!orig.lifeTotals.equals(replay.lifeTotals)) {
                return orig.turnNumber;
            }
        }
        return -1;
    }

    /**
     * Per-turn snapshot of key game metrics.
     */
    public static class TurnData implements Serializable {
        private static final long serialVersionUID = 1L;

        public final int turnNumber;
        /** Player ID → life total at start of this turn. */
        public final Map<String, Integer> lifeTotals;
        /** Player ID → hand size at start of this turn. */
        public final Map<String, Integer> handSizes;
        /** Player ID → library size at start of this turn. */
        public final Map<String, Integer> librarySizes;
        /** Number of events that occurred during this turn. */
        public final int eventCount;

        public TurnData(int turnNumber,
                        Map<String, Integer> lifeTotals,
                        Map<String, Integer> handSizes,
                        Map<String, Integer> librarySizes,
                        int eventCount) {
            this.turnNumber = turnNumber;
            this.lifeTotals = new LinkedHashMap<>(lifeTotals);
            this.handSizes = new LinkedHashMap<>(handSizes);
            this.librarySizes = new LinkedHashMap<>(librarySizes);
            this.eventCount = eventCount;
        }
    }
}

