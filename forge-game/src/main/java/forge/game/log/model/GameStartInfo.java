package forge.game.log.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures pre-game decisions including toss result and mulligan information.
 */
public class GameStartInfo {
    private String tossWinner;           // Player who won the die roll/coin toss
    private String playDrawChoice;       // "play" or "draw"
    private String startingPlayer;       // Player who takes the first turn
    private List<MulliganInfo> mulligans; // Mulligan decisions per player

    public GameStartInfo() {
        this.mulligans = new ArrayList<>();
    }

    // Getters and Setters
    public String getTossWinner() { return tossWinner; }
    public void setTossWinner(String tossWinner) { this.tossWinner = tossWinner; }

    public String getPlayDrawChoice() { return playDrawChoice; }
    public void setPlayDrawChoice(String playDrawChoice) { this.playDrawChoice = playDrawChoice; }

    public String getStartingPlayer() { return startingPlayer; }
    public void setStartingPlayer(String startingPlayer) { this.startingPlayer = startingPlayer; }

    public List<MulliganInfo> getMulligans() { return mulligans; }
    public void setMulligans(List<MulliganInfo> mulligans) { this.mulligans = mulligans; }

    public void addMulligan(MulliganInfo mulligan) {
        this.mulligans.add(mulligan);
    }

    /**
     * Mulligan information for a single player.
     */
    public static class MulliganInfo {
        private String player;
        private int startingHandSize;
        private int mulligansTaken;
        private int finalHandSize;
        private int cardsToBottom;

        public MulliganInfo() {
            this.startingHandSize = 7;
        }

        public MulliganInfo(String player) {
            this.player = player;
            this.startingHandSize = 7;
            this.mulligansTaken = 0;
            this.finalHandSize = 7;
            this.cardsToBottom = 0;
        }

        // Getters and Setters
        public String getPlayer() { return player; }
        public void setPlayer(String player) { this.player = player; }

        public int getStartingHandSize() { return startingHandSize; }
        public void setStartingHandSize(int startingHandSize) { this.startingHandSize = startingHandSize; }

        public int getMulligansTaken() { return mulligansTaken; }
        public void setMulligansTaken(int mulligansTaken) { this.mulligansTaken = mulligansTaken; }

        public int getFinalHandSize() { return finalHandSize; }
        public void setFinalHandSize(int finalHandSize) { this.finalHandSize = finalHandSize; }

        public int getCardsToBottom() { return cardsToBottom; }
        public void setCardsToBottom(int cardsToBottom) { this.cardsToBottom = cardsToBottom; }

        /**
         * Record a mulligan decision.
         */
        public void recordMulligan() {
            this.mulligansTaken++;
            this.finalHandSize = Math.max(0, this.startingHandSize - this.mulligansTaken);
        }

        /**
         * Record the final keep decision with cards put to bottom.
         */
        public void recordKeep(int cardsToBottom) {
            this.cardsToBottom = cardsToBottom;
            this.finalHandSize = Math.max(0, this.startingHandSize - this.cardsToBottom);
        }
    }
}

