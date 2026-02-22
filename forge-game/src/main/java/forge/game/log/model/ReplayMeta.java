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
    private String winCondition;  // life_zero, commander_damage, poison, decked, concession, alternate_win
    private boolean conceded;
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

    public String getWinCondition() { return winCondition; }
    public void setWinCondition(String winCondition) { this.winCondition = winCondition; }

    public boolean isConceded() { return conceded; }
    public void setConceded(boolean conceded) { this.conceded = conceded; }

    public Integer getTurns() { return turns; }
    public void setTurns(Integer turns) { this.turns = turns; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    /**
     * Player metadata.
     */
    public static class PlayerMeta {
        private String name;
        private String deckName;
        private String deckHash;
        /**
         * v1.4.0: URL linking to the exact deck revision used in this game.
         * Format: https://mamo.games/deck/<uuid>#<DDMMYYYY>_<deck_hash>
         * Null for AI players or when no deck link is available.
         */
        private String deckLink;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDeckName() { return deckName; }
        public void setDeckName(String deckName) { this.deckName = deckName; }

        public String getDeckHash() { return deckHash; }
        public void setDeckHash(String deckHash) { this.deckHash = deckHash; }

        public String getDeckLink() { return deckLink; }
        public void setDeckLink(String deckLink) { this.deckLink = deckLink; }
    }
}
