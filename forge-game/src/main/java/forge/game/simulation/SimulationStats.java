package forge.game.simulation;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for simulation statistics exported to JSON.
 * Format: forge-simulation-stats v2.0.0
 */
public class SimulationStats {
    private String format = "forge-simulation-stats";
    private String version = "2.0.0";
    private MetaData meta;
    private GameOutcomeData outcome;
    private Map<String, PlayerStats> players;
    private TimelineData timeline;

    public SimulationStats() {
        this.meta = new MetaData();
        this.outcome = new GameOutcomeData();
        this.players = new HashMap<>();
        this.timeline = null; // Optional
    }

    // Getters and Setters
    public String getFormat() { return format; }
    public String getVersion() { return version; }
    public MetaData getMeta() { return meta; }
    public void setMeta(MetaData meta) { this.meta = meta; }
    public GameOutcomeData getOutcome() { return outcome; }
    public void setOutcome(GameOutcomeData outcome) { this.outcome = outcome; }
    public Map<String, PlayerStats> getPlayers() { return players; }
    public void setPlayers(Map<String, PlayerStats> players) { this.players = players; }
    public TimelineData getTimeline() { return timeline; }
    public void setTimeline(TimelineData timeline) { this.timeline = timeline; }

    public static class MetaData {
        private String timestamp;
        private String simulationId;
        private String gameType;
        private String deck1Name;
        private String deck2Name;
        private String deck1Hash;
        private String deck2Hash;

        // Getters and Setters
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getSimulationId() { return simulationId; }
        public void setSimulationId(String simulationId) { this.simulationId = simulationId; }
        public String getGameType() { return gameType; }
        public void setGameType(String gameType) { this.gameType = gameType; }
        public String getDeck1Name() { return deck1Name; }
        public void setDeck1Name(String deck1Name) { this.deck1Name = deck1Name; }
        public String getDeck2Name() { return deck2Name; }
        public void setDeck2Name(String deck2Name) { this.deck2Name = deck2Name; }
        public String getDeck1Hash() { return deck1Hash; }
        public void setDeck1Hash(String deck1Hash) { this.deck1Hash = deck1Hash; }
        public String getDeck2Hash() { return deck2Hash; }
        public void setDeck2Hash(String deck2Hash) { this.deck2Hash = deck2Hash; }
    }

    public static class GameOutcomeData {
        private String winner;
        private String winCondition;
        private int totalTurns;
        private long durationMs;
        private String gameEndedReason;

        // Getters and Setters
        public String getWinner() { return winner; }
        public void setWinner(String winner) { this.winner = winner; }
        public String getWinCondition() { return winCondition; }
        public void setWinCondition(String winCondition) { this.winCondition = winCondition; }
        public int getTotalTurns() { return totalTurns; }
        public void setTotalTurns(int totalTurns) { this.totalTurns = totalTurns; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getGameEndedReason() { return gameEndedReason; }
        public void setGameEndedReason(String gameEndedReason) { this.gameEndedReason = gameEndedReason; }
    }

    public static class TimelineData {
        private int[] turnCount;
        private int[] p1Life;
        private int[] p2Life;
        private int[] p1Creatures;
        private int[] p2Creatures;

        // Getters and Setters
        public int[] getTurnCount() { return turnCount; }
        public void setTurnCount(int[] turnCount) { this.turnCount = turnCount; }
        public int[] getP1Life() { return p1Life; }
        public void setP1Life(int[] p1Life) { this.p1Life = p1Life; }
        public int[] getP2Life() { return p2Life; }
        public void setP2Life(int[] p2Life) { this.p2Life = p2Life; }
        public int[] getP1Creatures() { return p1Creatures; }
        public void setP1Creatures(int[] p1Creatures) { this.p1Creatures = p1Creatures; }
        public int[] getP2Creatures() { return p2Creatures; }
        public void setP2Creatures(int[] p2Creatures) { this.p2Creatures = p2Creatures; }
    }
}

