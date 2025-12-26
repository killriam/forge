package forge.game.log.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Metadata about the game.
 */
public class ReplayMeta {
    private String gameId;
    private String timestamp;
    private String gameType;
    private Map<String, PlayerMeta> players;
    private String winner;
    private Integer turns;
    private Integer durationSeconds;

    public ReplayMeta() {
        this.players = new HashMap<>();
    }

    // Getters and Setters
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }

    public Map<String, PlayerMeta> getPlayers() { return players; }
    public void setPlayers(Map<String, PlayerMeta> players) { this.players = players; }

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }

    public Integer getTurns() { return turns; }
    public void setTurns(Integer turns) { this.turns = turns; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    /**
     * Player metadata.
     */
    public static class PlayerMeta {
        private String name;
        private String deckHash;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDeckHash() { return deckHash; }
        public void setDeckHash(String deckHash) { this.deckHash = deckHash; }
    }
}

