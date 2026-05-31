package forge.game.log.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Aggregated game-wide statistics computed at game end.
 * Provides high-level KPIs for post-game analysis.
 * Spec v1.5.0, Section 16.
 */
public class GameSummary {
    private int totalTurns;
    private int durationSeconds;
    private String winner;
    private String winCondition;
    private Map<String, PlayerGameStats> players;

    public GameSummary() {
        this.players = new HashMap<>();
    }

    public int getTotalTurns() { return totalTurns; }
    public void setTotalTurns(int totalTurns) { this.totalTurns = totalTurns; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }

    public String getWinCondition() { return winCondition; }
    public void setWinCondition(String winCondition) { this.winCondition = winCondition; }

    public Map<String, PlayerGameStats> getPlayers() { return players; }

    /**
     * Aggregated per-player statistics for the entire game.
     */
    public static class PlayerGameStats {
        // Card flow
        private int totalCardsDrawn;
        private double cardDrawRate;          // cards drawn per turn
        private int totalSpellsCast;
        private double spellVelocity;         // spells cast per turn
        private int totalAbilitiesActivated;

        // Mana
        private int missedLandDrops;          // turns with 0 lands played (excluding turn 0)
        private int totalLandsPlayed;
        private int peakMana;                 // highest available mana reached

        // Combat
        private int totalDamageDealt;
        private int totalDamageReceived;
        private int totalCreaturesPlayed;

        // Life
        private int startingLife;
        private int endingLife;
        private int lifeDelta;                // ending - starting

        // Tempo
        private int totalCountersPlaced;      // from COUNTERS events

        // Getters and Setters
        public int getTotalCardsDrawn() { return totalCardsDrawn; }
        public void setTotalCardsDrawn(int v) { this.totalCardsDrawn = v; }

        public double getCardDrawRate() { return cardDrawRate; }
        public void setCardDrawRate(double v) { this.cardDrawRate = v; }

        public int getTotalSpellsCast() { return totalSpellsCast; }
        public void setTotalSpellsCast(int v) { this.totalSpellsCast = v; }

        public double getSpellVelocity() { return spellVelocity; }
        public void setSpellVelocity(double v) { this.spellVelocity = v; }

        public int getTotalAbilitiesActivated() { return totalAbilitiesActivated; }
        public void setTotalAbilitiesActivated(int v) { this.totalAbilitiesActivated = v; }

        public int getMissedLandDrops() { return missedLandDrops; }
        public void setMissedLandDrops(int v) { this.missedLandDrops = v; }

        public int getTotalLandsPlayed() { return totalLandsPlayed; }
        public void setTotalLandsPlayed(int v) { this.totalLandsPlayed = v; }

        public int getPeakMana() { return peakMana; }
        public void setPeakMana(int v) { this.peakMana = v; }

        public int getTotalDamageDealt() { return totalDamageDealt; }
        public void setTotalDamageDealt(int v) { this.totalDamageDealt = v; }

        public int getTotalDamageReceived() { return totalDamageReceived; }
        public void setTotalDamageReceived(int v) { this.totalDamageReceived = v; }

        public int getTotalCreaturesPlayed() { return totalCreaturesPlayed; }
        public void setTotalCreaturesPlayed(int v) { this.totalCreaturesPlayed = v; }

        public int getStartingLife() { return startingLife; }
        public void setStartingLife(int v) { this.startingLife = v; }

        public int getEndingLife() { return endingLife; }
        public void setEndingLife(int v) { this.endingLife = v; }

        public int getLifeDelta() { return lifeDelta; }
        public void setLifeDelta(int v) { this.lifeDelta = v; }

        public int getTotalCountersPlaced() { return totalCountersPlaced; }
        public void setTotalCountersPlaced(int v) { this.totalCountersPlaced = v; }
    }
}

