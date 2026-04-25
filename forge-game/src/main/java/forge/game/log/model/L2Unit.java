package forge.game.log.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Level 2 Unit - Learning View.
 * Represents one complete decision context with before/after snapshots.
 */
public class L2Unit {
    private int u; // unit index
    private String tStart; // start time marker
    private String tEnd; // end time marker
    private int[] l1Range; // [start_index, end_index] in L1 log
    private List<Integer> decisionEvents; // indices of decision events
    private GameState before; // state before
    private List<StackItem> stack; // stack contents during this unit
    private GameState after; // state after
    private Annotations annotations; // learning annotations

    public L2Unit() {
        this.decisionEvents = new ArrayList<>();
        this.stack = new ArrayList<>();
        this.annotations = new Annotations();
    }

    // Getters and Setters
    public int getU() { return u; }
    public void setU(int u) { this.u = u; }

    public String getTStart() { return tStart; }
    public void setTStart(String tStart) { this.tStart = tStart; }

    public String getTEnd() { return tEnd; }
    public void setTEnd(String tEnd) { this.tEnd = tEnd; }

    public int[] getL1Range() { return l1Range; }
    public void setL1Range(int[] l1Range) { this.l1Range = l1Range; }

    public List<Integer> getDecisionEvents() { return decisionEvents; }
    public void setDecisionEvents(List<Integer> decisionEvents) { this.decisionEvents = decisionEvents; }

    public GameState getBefore() { return before; }
    public void setBefore(GameState before) { this.before = before; }

    public List<StackItem> getStack() { return stack; }
    public void setStack(List<StackItem> stack) { this.stack = stack; }

    public GameState getAfter() { return after; }
    public void setAfter(GameState after) { this.after = after; }

    public Annotations getAnnotations() { return annotations; }
    public void setAnnotations(Annotations annotations) { this.annotations = annotations; }

    /**
     * Stack item in L2 view.
     */
    public static class StackItem {
        private String stack; // stack ID
        private String kind; // SPELL, ABILITY, TRIGGER
        private String controller;
        private String source;
        private String card;
        private String cardName;
        private List<Target> targets;
        private Map<String, Object> choices;
        private int linkedDecisionEvent;
        private List<String> manaPaid;
        private String outcome; // resolved, countered, fizzled, exiled

        public StackItem() {
            this.targets = new ArrayList<>();
            this.choices = new HashMap<>();
            this.manaPaid = new ArrayList<>();
        }

        public String getStack() { return stack; }
        public void setStack(String stack) { this.stack = stack; }

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }

        public String getController() { return controller; }
        public void setController(String controller) { this.controller = controller; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getCard() { return card; }
        public void setCard(String card) { this.card = card; }

        public String getCardName() { return cardName; }
        public void setCardName(String cardName) { this.cardName = cardName; }

        public List<Target> getTargets() { return targets; }
        public void setTargets(List<Target> targets) { this.targets = targets; }

        public Map<String, Object> getChoices() { return choices; }
        public void setChoices(Map<String, Object> choices) { this.choices = choices; }

        public int getLinkedDecisionEvent() { return linkedDecisionEvent; }
        public void setLinkedDecisionEvent(int linkedDecisionEvent) { this.linkedDecisionEvent = linkedDecisionEvent; }

        public List<String> getManaPaid() { return manaPaid; }
        public void setManaPaid(List<String> manaPaid) { this.manaPaid = manaPaid; }

        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }

        /**
         * Target information.
         */
        public static class Target {
            private String slot;
            private String obj;
            private String name;
            private boolean valid;

            public String getSlot() { return slot; }
            public void setSlot(String slot) { this.slot = slot; }

            public String getObj() { return obj; }
            public void setObj(String obj) { this.obj = obj; }

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            public boolean isValid() { return valid; }
            public void setValid(boolean valid) { this.valid = valid; }
        }
    }

    /**
     * Annotations for learning and analysis.
     */
    public static class Annotations {
        private Object decisionQuality; // can be null or a scoring object
        private List<String> alternativeLines;
        private boolean keyMoment;
        private String teachingNotes;

        public Annotations() {
            this.alternativeLines = new ArrayList<>();
        }

        public Object getDecisionQuality() { return decisionQuality; }
        public void setDecisionQuality(Object decisionQuality) { this.decisionQuality = decisionQuality; }

        public List<String> getAlternativeLines() { return alternativeLines; }
        public void setAlternativeLines(List<String> alternativeLines) { this.alternativeLines = alternativeLines; }

        public boolean isKeyMoment() { return keyMoment; }
        public void setKeyMoment(boolean keyMoment) { this.keyMoment = keyMoment; }

        public String getTeachingNotes() { return teachingNotes; }
        public void setTeachingNotes(String teachingNotes) { this.teachingNotes = teachingNotes; }
    }
}

