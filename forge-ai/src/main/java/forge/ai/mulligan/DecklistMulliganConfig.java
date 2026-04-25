package forge.ai.mulligan;

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
 *             "card_values": { "land": 1.0, "cmc_0_to_2": 0.8, "cmc_3": 0.5, "other": 0.3 },
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

    /**
     * Default card values per the Commander Decklist Notation spec §6.1.1.
     */
    public static class CardValues {
        private double land = 1.0;
        private double cmc_0_to_2 = 0.8;
        private double cmc_3 = 0.5;
        private double other = 0.3;

        public double getLand() { return land; }
        public void setLand(double land) { this.land = land; }

        public double getCmc0To2() { return cmc_0_to_2; }
        public void setCmc0To2(double value) { this.cmc_0_to_2 = value; }

        public double getCmc3() { return cmc_3; }
        public void setCmc3(double value) { this.cmc_3 = value; }

        public double getOther() { return other; }
        public void setOther(double other) { this.other = other; }
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

