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
    /** v1.8.0: ISO 8601 timestamp when this file was replayed. Set by replay launcher. */
    private String replayedAt;
    /** v1.9.0: Winning player ID or 'draw' in the replayed game. */
    private String replayedWinner;
    /** v1.9.0: Outcome for the human player ("win", "loss", "draw") in the replayed game. */
    private String replayedOutcome;
    /** v1.9.0: Turn count in the replayed game. */
    private Integer replayedTurns;
    /** v1.9.0: Replay mode: "deterministic" or "shuffle" (human with shuffled deck). */
    private String replayedMode;

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

    public String getReplayedAt() { return replayedAt; }
    public void setReplayedAt(String replayedAt) { this.replayedAt = replayedAt; }

    public String getReplayedWinner() { return replayedWinner; }
    public void setReplayedWinner(String replayedWinner) { this.replayedWinner = replayedWinner; }

    public String getReplayedOutcome() { return replayedOutcome; }
    public void setReplayedOutcome(String replayedOutcome) { this.replayedOutcome = replayedOutcome; }

    public Integer getReplayedTurns() { return replayedTurns; }
    public void setReplayedTurns(Integer replayedTurns) { this.replayedTurns = replayedTurns; }

    public String getReplayedMode() { return replayedMode; }
    public void setReplayedMode(String replayedMode) { this.replayedMode = replayedMode; }

    public boolean isShuffleReplay() { return "shuffle".equalsIgnoreCase(replayedMode); }

    // -------------------------------------------------------------------------
    // Inner class: PlayerMeta
    // -------------------------------------------------------------------------

    /** Metadata about a single player in the game. */
    public static class PlayerMeta {
        private String name;
        private String deckName;
        private String deckHash;
        /**
         * v1.4.0: URL linking to the exact deck revision used in this game.
         * Null for AI players or when no deck link is available.
         */
        private String deckLink;
        /** Whether this player is controlled by AI. */
        private boolean isAi;
        /** Starting life total for this player. */
        private int startingLife;
        /** Player type description, e.g. "Human", "AI". */
        private String playerType;
        /**
         * v1.9.0: Team number for multiplayer team games.
         * Null for non-team games (e.g., 1v1, free-for-all).
         * Team numbers start at 0.
         */
        private Integer team;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDeckName() { return deckName; }
        public void setDeckName(String deckName) { this.deckName = deckName; }

        public String getDeckHash() { return deckHash; }
        public void setDeckHash(String deckHash) { this.deckHash = deckHash; }

        public String getDeckLink() { return deckLink; }
        public void setDeckLink(String deckLink) { this.deckLink = deckLink; }

        public boolean isAi() { return isAi; }
        public void setAi(boolean ai) { this.isAi = ai; }

        public int getStartingLife() { return startingLife; }
        public void setStartingLife(int startingLife) { this.startingLife = startingLife; }

        public String getPlayerType() { return playerType; }
        public void setPlayerType(String playerType) { this.playerType = playerType; }

        public Integer getTeam() { return team; }
        public void setTeam(Integer team) { this.team = team; }
    }
}
