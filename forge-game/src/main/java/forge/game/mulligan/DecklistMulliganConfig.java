package forge.game.mulligan;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration model for decklist-based mulligan evaluation.
 * Maps the "mulligan" section of the Commander Decklist Notation spec (§6.1).
 *
 * JSON structure:
 * <pre>
 * {
 *     "deck_rules": {
 *         "mulligan": {
 *             "card_values": { "land": 1.0, "mv0": 0.85, "mv1": 0.8, "mv2": 0.75, "mv3": 0.6,
 *                              "mv4": 0.2, "mv5": 0.2, "mv6": 0.2, "mv7Plus": 0.2 },
 *             "card_overrides": [ { "name": "Sol Ring", "value": 1.2, "reason": "..." } ],
 *             "thresholds": [ { "round": 0, "hand_size": 7, "min_value": 3.5 }, ... ]
 *         }
 *     }
 * }
 * </pre>
 */
public class DecklistMulliganConfig {

    private CardValues cardValues;
    private List<CardOverride> cardOverrides;
    private List<Threshold> thresholds;
    /** §6.1.5 Mana Base Band - defaults per spec. */
    private double mana_base_min = 3.0;
    private double mana_base_max = 4.0;

    public DecklistMulliganConfig() {
        this.cardValues = new CardValues();
        this.cardOverrides = new ArrayList<>();
        this.thresholds = new ArrayList<>();
    }

    public CardValues getCardValues() { return cardValues; }
    public void setCardValues(CardValues cardValues) { this.cardValues = cardValues; }

    public List<CardOverride> getCardOverrides() { return cardOverrides; }
    public void setCardOverrides(List<CardOverride> cardOverrides) { this.cardOverrides = cardOverrides; }

    public List<Threshold> getThresholds() { return thresholds; }
    public void setThresholds(List<Threshold> thresholds) { this.thresholds = thresholds; }

    public double getManaBaseMin() { return mana_base_min; }
    public void setManaBaseMin(double v) { this.mana_base_min = v; }

    public double getManaBaseMax() { return mana_base_max; }
    public void setManaBaseMax(double v) { this.mana_base_max = v; }

    /**
     * Default card values per the Commander Decklist Notation spec §6.1.1 (v1.4.0 curve).
     */
    public static class CardValues {
        private double land = 1.0;
        private double mv0 = 0.85;
        private double mv1 = 0.8;
        private double mv2 = 0.75;
        private double mv3 = 0.6;
        private double mv4 = 0.2;
        private double mv5 = 0.2;
        private double mv6 = 0.2;
        private double mv7Plus = 0.2;

        public double getLand() { return land; }
        public void setLand(double v) { this.land = v; }

        public double getMv0() { return mv0; }
        public void setMv0(double v) { this.mv0 = v; }

        public double getMv1() { return mv1; }
        public void setMv1(double v) { this.mv1 = v; }

        public double getMv2() { return mv2; }
        public void setMv2(double v) { this.mv2 = v; }

        public double getMv3() { return mv3; }
        public void setMv3(double v) { this.mv3 = v; }

        public double getMv4() { return mv4; }
        public void setMv4(double v) { this.mv4 = v; }

        public double getMv5() { return mv5; }
        public void setMv5(double v) { this.mv5 = v; }

        public double getMv6() { return mv6; }
        public void setMv6(double v) { this.mv6 = v; }

        public double getMv7Plus() { return mv7Plus; }
        public void setMv7Plus(double v) { this.mv7Plus = v; }

        /** Bucket lookup for a (curve-adjusted, per §6.1.1a) mana value, clamped to [0,7]. */
        public double forManaValue(int mv) {
            int clamped = Math.max(0, Math.min(mv, 7));
            switch (clamped) {
                case 0: return mv0;
                case 1: return mv1;
                case 2: return mv2;
                case 3: return mv3;
                case 4: return mv4;
                case 5: return mv5;
                case 6: return mv6;
                default: return mv7Plus;
            }
        }
    }

    /**
     * Per-card value override per spec §6.1.3.
     */
    public static class CardOverride {
        private String name;
        private double value;
        private String reason;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * Mulligan threshold per round per spec §6.1.2.
     */
    public static class Threshold {
        private int round;
        private int hand_size;
        private double min_value;
        private String description;

        public int getRound() { return round; }
        public void setRound(int round) { this.round = round; }

        public int getHandSize() { return hand_size; }
        public void setHandSize(int handSize) { this.hand_size = handSize; }

        public double getMinValue() { return min_value; }
        public void setMinValue(double minValue) { this.min_value = minValue; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * Create a default config with spec default values and thresholds.
     */
    public static DecklistMulliganConfig createDefault() {
        DecklistMulliganConfig config = new DecklistMulliganConfig();

        // Default thresholds from spec §6.1.2
        Threshold t0 = new Threshold();
        t0.setRound(0); t0.setHandSize(7); t0.setMinValue(3.5);
        t0.setDescription("Keep 7-card hand if total value is at least 3.5");

        Threshold t1 = new Threshold();
        t1.setRound(1); t1.setHandSize(6); t1.setMinValue(3.0);
        t1.setDescription("Keep 6-card hand if total value is at least 3.0");

        Threshold t2 = new Threshold();
        t2.setRound(2); t2.setHandSize(5); t2.setMinValue(2.5);
        t2.setDescription("Keep 5-card hand if total value is at least 2.5");

        Threshold t3 = new Threshold();
        t3.setRound(3); t3.setHandSize(4); t3.setMinValue(2.0);
        t3.setDescription("Keep 4-card hand if total value is at least 2.0");

        config.getThresholds().add(t0);
        config.getThresholds().add(t1);
        config.getThresholds().add(t2);
        config.getThresholds().add(t3);

        return config;
    }
}

