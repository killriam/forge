package forge.game.log.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root object for MTG Replay & Learning Notation format.
 * Represents a complete game log with L1 (full detail) and L2 (learning) views.
 *
 * Version history:
 * - 1.0.0: Initial format
 * - 1.1.0: Added initial_state.objects mapping, card_name in events, win_condition, deck_name, deck_hash
 * - 1.2.0: New event types GAME_START, PLAY_LAND, DRAW, DISCARD, MULLIGAN with proper actor attribution
 * - 1.3.0: Added game_start section with toss_winner, play_draw_choice, mulligans detail
 * - 1.3.1 (spec 1.2.1): Added ACTIVE_PLAYER_CHANGE event for turn transitions
 * - 1.4.0 (spec 1.3.0): Added LEARNING_MARKER event and learning_markers top-level array
 * - 1.4.0 (spec 1.4.0): Added deck_link field in player metadata
 */
public class ReplayLog {
    private String format = "mtg-replay";
    private String version = "1.4.0";
    private ReplayMeta meta;
    private long seed;
    private GameStartInfo gameStart;
    private Map<String, CardDefinition> cardIndex;
    private GameState initialState;
    private List<L1Event> logL1;
    private List<L2Unit> viewsL2;
    /** v1.4.0 (spec 1.3.0): Top-level index of all LEARNING_MARKER events for quick navigation. */
    private List<LearningMarker> learningMarkers;

    public ReplayLog() {
        this.meta = new ReplayMeta();
        this.gameStart = new GameStartInfo();
        this.cardIndex = new HashMap<>();
        this.initialState = new GameState();
        this.logL1 = new ArrayList<>();
        this.viewsL2 = new ArrayList<>();
        this.learningMarkers = new ArrayList<>();
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

    public GameStartInfo getGameStart() { return gameStart; }
    public void setGameStart(GameStartInfo gameStart) { this.gameStart = gameStart; }

    public Map<String, CardDefinition> getCardIndex() { return cardIndex; }
    public void setCardIndex(Map<String, CardDefinition> cardIndex) { this.cardIndex = cardIndex; }

    public GameState getInitialState() { return initialState; }
    public void setInitialState(GameState initialState) { this.initialState = initialState; }

    public List<L1Event> getLogL1() { return logL1; }
    public void setLogL1(List<L1Event> logL1) { this.logL1 = logL1; }

    public List<L2Unit> getViewsL2() { return viewsL2; }
    public void setViewsL2(List<L2Unit> viewsL2) { this.viewsL2 = viewsL2; }

    public List<LearningMarker> getLearningMarkers() { return learningMarkers; }
    public void setLearningMarkers(List<LearningMarker> learningMarkers) { this.learningMarkers = learningMarkers; }

    public void addL1Event(L1Event event) {
        this.logL1.add(event);
    }

    public void addL2Unit(L2Unit unit) {
        this.viewsL2.add(unit);
    }

    public void addLearningMarker(LearningMarker marker) {
        this.learningMarkers.add(marker);
    }

    // -------------------------------------------------------------------------
    // Inner class: LearningMarker (spec 1.3.0)
    // -------------------------------------------------------------------------

    /**
     * Top-level summary entry for a LEARNING_MARKER event.
     * Stored in the top-level learning_markers array for quick navigation.
     * Spec section 8.6.
     */
    public static class LearningMarker {
        private String markerId;       // e.g. "lm-1"
        private int eventIndex;        // L1 event index
        private String t;              // time marker of the bookmarked moment
        private String player;         // player who placed the marker
        private String label;          // short description / question
        private String category;       // decision_review | mistake | turning_point | interesting_interaction | sideboard_note | general
        private String createdAt;      // ISO 8601
        private Snapshot snapshot;     // lightweight game state at marker moment
        private String notes;          // extended free-form notes

        public LearningMarker() {
            this.snapshot = new Snapshot();
        }

        public String getMarkerId() { return markerId; }
        public void setMarkerId(String markerId) { this.markerId = markerId; }

        public int getEventIndex() { return eventIndex; }
        public void setEventIndex(int eventIndex) { this.eventIndex = eventIndex; }

        public String getT() { return t; }
        public void setT(String t) { this.t = t; }

        public String getPlayer() { return player; }
        public void setPlayer(String player) { this.player = player; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public Snapshot getSnapshot() { return snapshot; }
        public void setSnapshot(Snapshot snapshot) { this.snapshot = snapshot; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        /** Lightweight game-state snapshot stored inside each learning marker. */
        public static class Snapshot {
            private int turn;
            private String phase;
            private String activePlayer;
            private Map<String, Integer> lifeTotals;
            private Map<String, Integer> cardsInHand;
            private Map<String, Integer> battlefieldCount;
            private boolean stackEmpty;

            public Snapshot() {
                this.lifeTotals = new HashMap<>();
                this.cardsInHand = new HashMap<>();
                this.battlefieldCount = new HashMap<>();
            }

            public int getTurn() { return turn; }
            public void setTurn(int turn) { this.turn = turn; }

            public String getPhase() { return phase; }
            public void setPhase(String phase) { this.phase = phase; }

            public String getActivePlayer() { return activePlayer; }
            public void setActivePlayer(String activePlayer) { this.activePlayer = activePlayer; }

            public Map<String, Integer> getLifeTotals() { return lifeTotals; }
            public void setLifeTotals(Map<String, Integer> lifeTotals) { this.lifeTotals = lifeTotals; }

            public Map<String, Integer> getCardsInHand() { return cardsInHand; }
            public void setCardsInHand(Map<String, Integer> cardsInHand) { this.cardsInHand = cardsInHand; }

            public Map<String, Integer> getBattlefieldCount() { return battlefieldCount; }
            public void setBattlefieldCount(Map<String, Integer> battlefieldCount) { this.battlefieldCount = battlefieldCount; }

            public boolean isStackEmpty() { return stackEmpty; }
            public void setStackEmpty(boolean stackEmpty) { this.stackEmpty = stackEmpty; }
        }
    }
}
