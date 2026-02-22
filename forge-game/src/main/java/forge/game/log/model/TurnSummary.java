package forge.game.log.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-turn summary of key game statistics for each player.
 * Provides at-a-glance indicators without requiring consumers to parse all events.
 * Spec v1.5.0, Section 16.
 */
public class TurnSummary {
    private int turn;
    private String activePlayer;
    private Map<String, PlayerTurnStats> players;

    public TurnSummary() {
        this.players = new HashMap<>();
    }

    public TurnSummary(int turn, String activePlayer) {
        this();
        this.turn = turn;
        this.activePlayer = activePlayer;
    }

    public int getTurn() { return turn; }
    public void setTurn(int turn) { this.turn = turn; }

    public String getActivePlayer() { return activePlayer; }
    public void setActivePlayer(String activePlayer) { this.activePlayer = activePlayer; }

    public Map<String, PlayerTurnStats> getPlayers() { return players; }

    /**
     * Per-player statistics for a single turn.
     */
    public static class PlayerTurnStats {
        private int landsPlayed;
        private String landDropRating;    // "bad", "good", "super"
        private int cardsDrawn;
        private int spellsCast;
        private int abilitiesActivated;
        private int landCount;            // total lands on battlefield
        private int availableMana;        // estimated total mana available
        private int life;
        private int cardsInHand;
        private int creaturesOnBattlefield;
        private int permanentsOnBattlefield;
        private int damageDealt;          // damage dealt this turn
        private int damageTaken;          // damage received this turn

        // Getters and Setters
        public int getLandsPlayed() { return landsPlayed; }
        public void setLandsPlayed(int landsPlayed) { this.landsPlayed = landsPlayed; }

        public String getLandDropRating() { return landDropRating; }
        public void setLandDropRating(String landDropRating) { this.landDropRating = landDropRating; }

        public int getCardsDrawn() { return cardsDrawn; }
        public void setCardsDrawn(int cardsDrawn) { this.cardsDrawn = cardsDrawn; }

        public int getSpellsCast() { return spellsCast; }
        public void setSpellsCast(int spellsCast) { this.spellsCast = spellsCast; }

        public int getAbilitiesActivated() { return abilitiesActivated; }
        public void setAbilitiesActivated(int abilitiesActivated) { this.abilitiesActivated = abilitiesActivated; }

        public int getLandCount() { return landCount; }
        public void setLandCount(int landCount) { this.landCount = landCount; }

        public int getAvailableMana() { return availableMana; }
        public void setAvailableMana(int availableMana) { this.availableMana = availableMana; }

        public int getLife() { return life; }
        public void setLife(int life) { this.life = life; }

        public int getCardsInHand() { return cardsInHand; }
        public void setCardsInHand(int cardsInHand) { this.cardsInHand = cardsInHand; }

        public int getCreaturesOnBattlefield() { return creaturesOnBattlefield; }
        public void setCreaturesOnBattlefield(int creaturesOnBattlefield) { this.creaturesOnBattlefield = creaturesOnBattlefield; }

        public int getPermanentsOnBattlefield() { return permanentsOnBattlefield; }
        public void setPermanentsOnBattlefield(int permanentsOnBattlefield) { this.permanentsOnBattlefield = permanentsOnBattlefield; }

        public int getDamageDealt() { return damageDealt; }
        public void setDamageDealt(int damageDealt) { this.damageDealt = damageDealt; }

        public int getDamageTaken() { return damageTaken; }
        public void setDamageTaken(int damageTaken) { this.damageTaken = damageTaken; }
    }
}

