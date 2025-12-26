package forge.game.log.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Complete game state snapshot.
 * Used for initial_state and L2 Unit before/after snapshots.
 */
public class GameState {
    private int turn;
    private String phase;
    private String step;
    private String priority;
    private String activePlayer;
    private Map<String, PlayerState> players;
    private Map<String, Object> zones;
    private Map<String, ObjectState> objects;

    public GameState() {
        this.players = new HashMap<>();
        this.zones = new HashMap<>();
        this.objects = new HashMap<>();
    }

    // Getters and Setters
    public int getTurn() { return turn; }
    public void setTurn(int turn) { this.turn = turn; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getActivePlayer() { return activePlayer; }
    public void setActivePlayer(String activePlayer) { this.activePlayer = activePlayer; }

    public Map<String, PlayerState> getPlayers() { return players; }
    public void setPlayers(Map<String, PlayerState> players) { this.players = players; }

    public Map<String, Object> getZones() { return zones; }
    public void setZones(Map<String, Object> zones) { this.zones = zones; }

    public Map<String, ObjectState> getObjects() { return objects; }
    public void setObjects(Map<String, ObjectState> objects) { this.objects = objects; }

    /**
     * Player state within a game state snapshot.
     */
    public static class PlayerState {
        private int life;
        private Object manaPool; // Can be array or simple representation
        private Map<String, Integer> counters;
        private int landsPlayedThisTurn;
        private int maxHandSize;

        public PlayerState() {
            this.counters = new HashMap<>();
        }

        public int getLife() { return life; }
        public void setLife(int life) { this.life = life; }

        public Object getManaPool() { return manaPool; }
        public void setManaPool(Object manaPool) { this.manaPool = manaPool; }

        public Map<String, Integer> getCounters() { return counters; }
        public void setCounters(Map<String, Integer> counters) { this.counters = counters; }

        public int getLandsPlayedThisTurn() { return landsPlayedThisTurn; }
        public void setLandsPlayedThisTurn(int landsPlayedThisTurn) { this.landsPlayedThisTurn = landsPlayedThisTurn; }

        public int getMaxHandSize() { return maxHandSize; }
        public void setMaxHandSize(int maxHandSize) { this.maxHandSize = maxHandSize; }
    }

    /**
     * Object (card/permanent/token) state within a game state snapshot.
     */
    public static class ObjectState {
        private String cardRef;
        private String controller;
        private String owner;
        private String zone;
        private boolean tapped;
        private boolean flipped;
        private boolean faceDown;
        private Map<String, Integer> counters;
        private int damageMarked;
        private String attachedTo;
        private Map<String, Object> notes;

        public ObjectState() {
            this.counters = new HashMap<>();
            this.notes = new HashMap<>();
        }

        public String getCardRef() { return cardRef; }
        public void setCardRef(String cardRef) { this.cardRef = cardRef; }

        public String getController() { return controller; }
        public void setController(String controller) { this.controller = controller; }

        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }

        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }

        public boolean isTapped() { return tapped; }
        public void setTapped(boolean tapped) { this.tapped = tapped; }

        public boolean isFlipped() { return flipped; }
        public void setFlipped(boolean flipped) { this.flipped = flipped; }

        public boolean isFaceDown() { return faceDown; }
        public void setFaceDown(boolean faceDown) { this.faceDown = faceDown; }

        public Map<String, Integer> getCounters() { return counters; }
        public void setCounters(Map<String, Integer> counters) { this.counters = counters; }

        public int getDamageMarked() { return damageMarked; }
        public void setDamageMarked(int damageMarked) { this.damageMarked = damageMarked; }

        public String getAttachedTo() { return attachedTo; }
        public void setAttachedTo(String attachedTo) { this.attachedTo = attachedTo; }

        public Map<String, Object> getNotes() { return notes; }
        public void setNotes(Map<String, Object> notes) { this.notes = notes; }
    }
}

