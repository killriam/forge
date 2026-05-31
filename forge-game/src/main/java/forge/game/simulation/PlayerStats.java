package forge.game.simulation;

/**
 * Per-player statistics for simulation export.
 */
public class PlayerStats {
    private String deckName;
    private int finalLife;
    private int lifeDelta;

    private CardsData cards;
    private SpellsData spells;
    private ManaData mana;
    private CombatData combat;
    private BoardData board;
    private TempoData tempo;

    public PlayerStats() {
        this.cards = new CardsData();
        this.spells = new SpellsData();
        this.mana = new ManaData();
        this.combat = new CombatData();
        this.board = new BoardData();
        this.tempo = new TempoData();
    }

    // Getters and Setters
    public String getDeckName() { return deckName; }
    public void setDeckName(String deckName) { this.deckName = deckName; }
    public int getFinalLife() { return finalLife; }
    public void setFinalLife(int finalLife) { this.finalLife = finalLife; }
    public int getLifeDelta() { return lifeDelta; }
    public void setLifeDelta(int lifeDelta) { this.lifeDelta = lifeDelta; }

    public CardsData getCards() { return cards; }
    public SpellsData getSpells() { return spells; }
    public ManaData getMana() { return mana; }
    public CombatData getCombat() { return combat; }
    public BoardData getBoard() { return board; }
    public TempoData getTempo() { return tempo; }

    public static class CardsData {
        private int drawn;
        private int mulligans;
        private int startingHandSize;

        public int getDrawn() { return drawn; }
        public void setDrawn(int drawn) { this.drawn = drawn; }
        public int getMulligans() { return mulligans; }
        public void setMulligans(int mulligans) { this.mulligans = mulligans; }
        public int getStartingHandSize() { return startingHandSize; }
        public void setStartingHandSize(int startingHandSize) { this.startingHandSize = startingHandSize; }
    }

    public static class SpellsData {
        private int totalCast;
        private int creatures;
        private int noncreatures;
        private double avgCmc;

        public int getTotalCast() { return totalCast; }
        public void setTotalCast(int totalCast) { this.totalCast = totalCast; }
        public int getCreatures() { return creatures; }
        public void setCreatures(int creatures) { this.creatures = creatures; }
        public int getNoncreatures() { return noncreatures; }
        public void setNoncreatures(int noncreatures) { this.noncreatures = noncreatures; }
        public double getAvgCmc() { return avgCmc; }
        public void setAvgCmc(double avgCmc) { this.avgCmc = avgCmc; }
    }

    public static class ManaData {
        private int landsPlayed;
        private int missedDrops;
        private int peakAvailable;
        private int totalProduced;
        private int totalSpent;

        public int getLandsPlayed() { return landsPlayed; }
        public void setLandsPlayed(int landsPlayed) { this.landsPlayed = landsPlayed; }
        public int getMissedDrops() { return missedDrops; }
        public void setMissedDrops(int missedDrops) { this.missedDrops = missedDrops; }
        public int getPeakAvailable() { return peakAvailable; }
        public void setPeakAvailable(int peakAvailable) { this.peakAvailable = peakAvailable; }
        public int getTotalProduced() { return totalProduced; }
        public void setTotalProduced(int totalProduced) { this.totalProduced = totalProduced; }
        public int getTotalSpent() { return totalSpent; }
        public void setTotalSpent(int totalSpent) { this.totalSpent = totalSpent; }
    }

    public static class CombatData {
        private int damageDealt;
        private int damageTaken;
        private int attacksDeclared;
        private int blocksDeclared;

        public int getDamageDealt() { return damageDealt; }
        public void setDamageDealt(int damageDealt) { this.damageDealt = damageDealt; }
        public int getDamageTaken() { return damageTaken; }
        public void setDamageTaken(int damageTaken) { this.damageTaken = damageTaken; }
        public int getAttacksDeclared() { return attacksDeclared; }
        public void setAttacksDeclared(int attacksDeclared) { this.attacksDeclared = attacksDeclared; }
        public int getBlocksDeclared() { return blocksDeclared; }
        public void setBlocksDeclared(int blocksDeclared) { this.blocksDeclared = blocksDeclared; }
    }

    public static class BoardData {
        private int finalCreatures;
        private int finalLands;
        private int finalOther;
        private int peakCreatures;

        public int getFinalCreatures() { return finalCreatures; }
        public void setFinalCreatures(int finalCreatures) { this.finalCreatures = finalCreatures; }
        public int getFinalLands() { return finalLands; }
        public void setFinalLands(int finalLands) { this.finalLands = finalLands; }
        public int getFinalOther() { return finalOther; }
        public void setFinalOther(int finalOther) { this.finalOther = finalOther; }
        public int getPeakCreatures() { return peakCreatures; }
        public void setPeakCreatures(int peakCreatures) { this.peakCreatures = peakCreatures; }
    }

    public static class TempoData {
        private int abilitiesActivated;
        private int countersPlaced;
        private int turnsWithAction;

        public int getAbilitiesActivated() { return abilitiesActivated; }
        public void setAbilitiesActivated(int abilitiesActivated) { this.abilitiesActivated = abilitiesActivated; }
        public int getCountersPlaced() { return countersPlaced; }
        public void setCountersPlaced(int countersPlaced) { this.countersPlaced = countersPlaced; }
        public int getTurnsWithAction() { return turnsWithAction; }
        public void setTurnsWithAction(int turnsWithAction) { this.turnsWithAction = turnsWithAction; }
    }
}

