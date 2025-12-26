package forge.game.log.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root object for MTG Replay & Learning Notation format.
 * Represents a complete game log with L1 (full detail) and L2 (learning) views.
 */
public class ReplayLog {
    private String format = "mtg-replay";
    private String version = "1.0.0";
    private ReplayMeta meta;
    private long seed;
    private Map<String, CardDefinition> cardIndex;
    private GameState initialState;
    private List<L1Event> logL1;
    private List<L2Unit> viewsL2;

    public ReplayLog() {
        this.meta = new ReplayMeta();
        this.cardIndex = new HashMap<>();
        this.initialState = new GameState();
        this.logL1 = new ArrayList<>();
        this.viewsL2 = new ArrayList<>();
    }

    // Getters and Setters
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public ReplayMeta getMeta() { return meta; }
    public void setMeta(ReplayMeta meta) { this.meta = meta; }

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    public Map<String, CardDefinition> getCardIndex() { return cardIndex; }
    public void setCardIndex(Map<String, CardDefinition> cardIndex) { this.cardIndex = cardIndex; }

    public GameState getInitialState() { return initialState; }
    public void setInitialState(GameState initialState) { this.initialState = initialState; }

    public List<L1Event> getLogL1() { return logL1; }
    public void setLogL1(List<L1Event> logL1) { this.logL1 = logL1; }

    public List<L2Unit> getViewsL2() { return viewsL2; }
    public void setViewsL2(List<L2Unit> viewsL2) { this.viewsL2 = viewsL2; }

    public void addL1Event(L1Event event) {
        this.logL1.add(event);
    }

    public void addL2Unit(L2Unit unit) {
        this.viewsL2.add(unit);
    }
}

