package forge.game;

import forge.game.card.CardCollectionView;

public class GameAnalysis {

    private String winningPlayer;
    private int lastTurnNumber;
    private int lifeDelta;
    private CardCollectionView cardsinStartingHand;
    private final int manascoreOfStartingHand;
    private Manastats ManaStatsPlayer1;
    private Manastats ManaStatsPlayer2;

    // Constructor
    public GameAnalysis(String winningPlayer, int lastTurnNumber, int lifeDelta,
                       CardCollectionView cardsinStartingHand, int manascoreOfStartingHand) {
        this.winningPlayer = winningPlayer;
        this.lastTurnNumber = lastTurnNumber;
        this.lifeDelta = lifeDelta;
        this.cardsinStartingHand = cardsinStartingHand;
        this.manascoreOfStartingHand = manascoreOfStartingHand;
    }

    // Getters
    public String getWinningPlayer() { return winningPlayer; }
    public int getLastTurnNumber() { return lastTurnNumber; }
    public int getLifeDelta() { return lifeDelta; }
    public CardCollectionView getCardsinStartingHand() { return cardsinStartingHand; }
    public int getManascoreOfStartingHand() { return manascoreOfStartingHand; }
    public Manastats getManaStatsPlayer1() { return ManaStatsPlayer1; }
    public Manastats getManaStatsPlayer2() { return ManaStatsPlayer2; }

    // Setters
    public void setWinningPlayer(String winningPlayer) { this.winningPlayer = winningPlayer; }
    public void setLastTurnNumber(int lastTurnNumber) { this.lastTurnNumber = lastTurnNumber; }
    public void setLifeDelta(int lifeDelta) { this.lifeDelta = lifeDelta; }
    public void setCardsinStartingHand(CardCollectionView cardsinStartingHand) {
        this.cardsinStartingHand = cardsinStartingHand;
    }
    public void setManaStatsPlayer1(Manastats manaStatsPlayer1) { ManaStatsPlayer1 = manaStatsPlayer1; }
    public void setManaStatsPlayer2(Manastats manaStatsPlayer2) { ManaStatsPlayer2 = manaStatsPlayer2; }

    @Override
    public String toString() {
        return "GameAnalysis{winningPlayer=" + winningPlayer +
                ", lastTurnNumber=" + lastTurnNumber +
                ", lifeDelta=" + lifeDelta + "}";
    }

    public static class Manastats {
        private final String playerName;
        private final int manaScoreStartHand;
        private final String[] availablemana;
        public String PlayerName;
        public int ManaScoreStartHand;
        public String[] availablemanaPerRound;

        public Manastats(String playerName, int manaScoreStartHand, String[] availablemana) {
            this.playerName = playerName;
            this.manaScoreStartHand = manaScoreStartHand;
            this.availablemana = availablemana;
        }
    }
}

