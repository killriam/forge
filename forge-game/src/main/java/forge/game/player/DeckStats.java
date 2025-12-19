package forge.game.player;

public class DeckStats {

    private String name;
    private int id;
    private int wincount;
    private int lifescore;
    private int turnCount;

    public DeckStats(String name) {
        this.name = name;
        wincount = 0;
        lifescore = 0;
        turnCount = 0;
    }

    public int getTurnCount() { return turnCount; }
    public int getWinCount() { return wincount; }
    public int getLifescore() { return lifescore; }
    public int getId() { return id; }
    public String getName() { return name; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }

    public void incrementWinCount() { this.wincount++; }
    public void addToLifeScore(int score) { this.lifescore += score; }
    public void addToturncount(int turncount) { this.turnCount += turncount; }

    public void addVictoryStats(int lastTurnNumber, int lifeDelta) {
        incrementWinCount();
        addToturncount(lastTurnNumber);
        addToLifeScore(lifeDelta);
    }

    @Override
    public String toString() {
        if (wincount == 0) {
            return "Name" + name + " Count: 0, Score: N/A, Turncount: N/A";
        } else {
            return "Name" + name + " Count: " + wincount + ", Score: " + lifescore / wincount +
                   ", Turncount: " + turnCount / wincount;
        }
    }
}

