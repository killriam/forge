package forge.deck;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration model for deck-level AI behavioral rules.
 * Maps the "deck_rules" section of the Commander Decklist Notation spec (§6).
 *
 * This model lives in forge-core so that {@link Deck} can hold a reference.
 * Parsing from JSON is done in forge-ai via {@code DeckRulesLoader}.
 *
 * Three subsections are supported:
 * <ul>
 *   <li><b>Mulligan</b> — card values, per-card overrides, round thresholds (§6.1)</li>
 *   <li><b>Combos</b> — declared synergistic card combinations (§6.2)</li>
 *   <li><b>Don't-Combos</b> — declared anti-synergies (§6.3)</li>
 * </ul>
 */
public class DeckRulesConfig {

    private MulliganConfig mulligan;
    private List<ComboDeclaration> combos;
    private List<AntiSynergy> dontCombos;

    public DeckRulesConfig() {
        this.mulligan = null;
        this.combos = new ArrayList<>();
        this.dontCombos = new ArrayList<>();
    }

    // ---- Accessors ----

    public MulliganConfig getMulligan() { return mulligan; }
    public void setMulligan(MulliganConfig mulligan) { this.mulligan = mulligan; }

    public List<ComboDeclaration> getCombos() { return combos; }
    public void setCombos(List<ComboDeclaration> combos) { this.combos = combos != null ? combos : new ArrayList<>(); }

    public List<AntiSynergy> getDontCombos() { return dontCombos; }
    public void setDontCombos(List<AntiSynergy> dontCombos) { this.dontCombos = dontCombos != null ? dontCombos : new ArrayList<>(); }

    public boolean hasMulligan() { return mulligan != null; }
    public boolean hasCombos() { return combos != null && !combos.isEmpty(); }
    public boolean hasDontCombos() { return dontCombos != null && !dontCombos.isEmpty(); }

    /** @return true if any section is populated. */
    public boolean isEmpty() { return !hasMulligan() && !hasCombos() && !hasDontCombos(); }

    // ========================================================================
    // Mulligan Config  (§6.1)
    // ========================================================================

    public static class MulliganConfig {
        private CardValues cardValues;
        private List<CardOverride> cardOverrides;
        private List<Threshold> thresholds;

        public MulliganConfig() {
            this.cardValues = new CardValues();
            this.cardOverrides = new ArrayList<>();
            this.thresholds = new ArrayList<>();
        }

        public CardValues getCardValues() { return cardValues; }
        public void setCardValues(CardValues cardValues) { this.cardValues = cardValues; }

        public List<CardOverride> getCardOverrides() { return cardOverrides; }
        public void setCardOverrides(List<CardOverride> v) { this.cardOverrides = v != null ? v : new ArrayList<>(); }

        public List<Threshold> getThresholds() { return thresholds; }
        public void setThresholds(List<Threshold> v) { this.thresholds = v != null ? v : new ArrayList<>(); }

        /** Default card values per Commander Decklist Notation spec §6.1.1. */
        public static class CardValues {
            private double land = 1.0;
            private double cmc0To2 = 0.8;
            private double cmc3 = 0.5;
            private double other = 0.3;

            public double getLand() { return land; }
            public void setLand(double v) { this.land = v; }

            public double getCmc0To2() { return cmc0To2; }
            public void setCmc0To2(double v) { this.cmc0To2 = v; }

            public double getCmc3() { return cmc3; }
            public void setCmc3(double v) { this.cmc3 = v; }

            public double getOther() { return other; }
            public void setOther(double v) { this.other = v; }
        }

        /** Per-card value override per spec §6.1.3. */
        public static class CardOverride {
            private String name;
            private double value;
            private String reason;

            public CardOverride() {}
            public CardOverride(String name, double value, String reason) {
                this.name = name;
                this.value = value;
                this.reason = reason;
            }

            public String getName() { return name; }
            public void setName(String v) { this.name = v; }

            public double getValue() { return value; }
            public void setValue(double v) { this.value = v; }

            public String getReason() { return reason; }
            public void setReason(String v) { this.reason = v; }
        }

        /** Mulligan threshold per round per spec §6.1.2. */
        public static class Threshold {
            private int round;
            private int handSize;
            private double minValue;
            private String description;

            public Threshold() {}
            public Threshold(int round, int handSize, double minValue, String description) {
                this.round = round;
                this.handSize = handSize;
                this.minValue = minValue;
                this.description = description;
            }

            public int getRound() { return round; }
            public void setRound(int v) { this.round = v; }

            public int getHandSize() { return handSize; }
            public void setHandSize(int v) { this.handSize = v; }

            public double getMinValue() { return minValue; }
            public void setMinValue(double v) { this.minValue = v; }

            public String getDescription() { return description; }
            public void setDescription(String v) { this.description = v; }
        }

        /** Create a default config with spec default values. */
        public static MulliganConfig createDefault() {
            MulliganConfig config = new MulliganConfig();
            config.getThresholds().add(new Threshold(0, 7, 3.5, "Keep 7-card hand if total value >= 3.5"));
            config.getThresholds().add(new Threshold(1, 6, 3.0, "Keep 6-card hand if total value >= 3.0"));
            config.getThresholds().add(new Threshold(2, 5, 2.5, "Keep 5-card hand if total value >= 2.5"));
            config.getThresholds().add(new Threshold(3, 4, 2.0, "Keep 4-card hand if total value >= 2.0"));
            return config;
        }
    }

    // ========================================================================
    // Combo Declaration  (§6.2)
    // ========================================================================

    public static class ComboDeclaration {
        private String id;
        private String name;
        private List<String> pieces;
        private String result;
        private List<String> tags;

        public ComboDeclaration() {
            this.pieces = new ArrayList<>();
            this.tags = new ArrayList<>();
        }

        public String getId() { return id; }
        public void setId(String v) { this.id = v; }

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }

        public List<String> getPieces() { return pieces; }
        public void setPieces(List<String> v) { this.pieces = v != null ? v : new ArrayList<>(); }

        public String getResult() { return result; }
        public void setResult(String v) { this.result = v; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> v) { this.tags = v != null ? v : new ArrayList<>(); }

        public boolean isWinCondition() {
            return tags != null && tags.contains("win-condition");
        }

        @Override
        public String toString() {
            return "Combo[" + id + ": " + String.join(" + ", pieces) + "]";
        }
    }

    // ========================================================================
    // Anti-Synergy  (§6.3)
    // ========================================================================

    public enum Severity {
        MINOR, MAJOR, CRITICAL;

        public static Severity fromString(String s) {
            if (s == null) return MAJOR;
            switch (s.toLowerCase()) {
                case "minor": return MINOR;
                case "critical": return CRITICAL;
                default: return MAJOR;
            }
        }
    }

    public static class AntiSynergy {
        private String id;
        private String name;
        private List<String> pieces;
        private String reason;
        private Severity severity;

        public AntiSynergy() {
            this.pieces = new ArrayList<>();
            this.severity = Severity.MAJOR;
        }

        public String getId() { return id; }
        public void setId(String v) { this.id = v; }

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }

        public List<String> getPieces() { return pieces; }
        public void setPieces(List<String> v) { this.pieces = v != null ? v : new ArrayList<>(); }

        public String getReason() { return reason; }
        public void setReason(String v) { this.reason = v; }

        public Severity getSeverity() { return severity; }
        public void setSeverity(Severity v) { this.severity = v; }

        @Override
        public String toString() {
            return "AntiSynergy[" + id + ": " + String.join(" vs ", pieces) + " (" + severity + ")]";
        }
    }

    // ========================================================================
    // Factory: build from inline AiHints (compact format in .dck header)
    // ========================================================================

    /**
     * Parse inline AiHints from the deck file header and produce a DeckRulesConfig.
     * Supported inline hint keys:
     * <ul>
     *   <li>{@code MulliganThreshold$<round>:<min_value>[;<round>:<min_value>...]}
     *   <li>{@code MulliganOverride$<CardName>:<value>[;<CardName>:<value>...]}
     *   <li>{@code Combo$<id>:<Card1>,<Card2>[,<Card3>...]}
     *   <li>{@code DontCombo$<id>:<Card1>,<Card2>[:<severity>]}
     * </ul>
     */
    public static DeckRulesConfig fromInlineHints(java.util.Set<String> aiHints) {
        if (aiHints == null || aiHints.isEmpty()) {
            return null;
        }

        DeckRulesConfig config = new DeckRulesConfig();
        boolean found = false;

        for (String hint : aiHints) {
            if (hint.toLowerCase().startsWith("mulliganthreshold$")) {
                found = true;
                parseMulliganThresholds(config, hint.substring(hint.indexOf('$') + 1));
            } else if (hint.toLowerCase().startsWith("mulliganoverride$")) {
                found = true;
                parseMulliganOverrides(config, hint.substring(hint.indexOf('$') + 1));
            } else if (hint.toLowerCase().startsWith("combo$")) {
                found = true;
                parseCombo(config, hint.substring(hint.indexOf('$') + 1));
            } else if (hint.toLowerCase().startsWith("dontcombo$")) {
                found = true;
                parseDontCombo(config, hint.substring(hint.indexOf('$') + 1));
            }
        }

        return found ? config : null;
    }

    private static void parseMulliganThresholds(DeckRulesConfig config, String value) {
        if (config.mulligan == null) {
            config.mulligan = new MulliganConfig();
        }
        // Format: 0:3.5;1:3.0;2:2.5;3:2.0
        String[] pairs = value.split(";");
        for (String pair : pairs) {
            String[] parts = pair.trim().split(":");
            if (parts.length == 2) {
                try {
                    int round = Integer.parseInt(parts[0].trim());
                    double minVal = Double.parseDouble(parts[1].trim());
                    int handSize = 7 - round;
                    config.mulligan.getThresholds().add(new MulliganConfig.Threshold(round, handSize, minVal, null));
                } catch (NumberFormatException ignored) {
                    // skip malformed entries
                }
            }
        }
    }

    private static void parseMulliganOverrides(DeckRulesConfig config, String value) {
        if (config.mulligan == null) {
            config.mulligan = new MulliganConfig();
        }
        // Format: Sol Ring:1.2;Doubling Season:0.6
        String[] pairs = value.split(";");
        for (String pair : pairs) {
            int lastColon = pair.lastIndexOf(':');
            if (lastColon > 0) {
                String cardName = pair.substring(0, lastColon).trim();
                try {
                    double val = Double.parseDouble(pair.substring(lastColon + 1).trim());
                    config.mulligan.getCardOverrides().add(new MulliganConfig.CardOverride(cardName, val, null));
                } catch (NumberFormatException ignored) {
                    // skip malformed entries
                }
            }
        }
    }

    private static void parseCombo(DeckRulesConfig config, String value) {
        // Format: combo_id:Card1,Card2,Card3
        int colonPos = value.indexOf(':');
        if (colonPos <= 0) return;
        String id = value.substring(0, colonPos).trim();
        String[] pieces = value.substring(colonPos + 1).split(",");
        if (pieces.length < 1) return;

        ComboDeclaration combo = new ComboDeclaration();
        combo.setId(id);
        combo.setName(id);
        List<String> pieceList = new ArrayList<>();
        for (String p : pieces) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) pieceList.add(trimmed);
        }
        combo.setPieces(pieceList);
        combo.setResult("");
        config.combos.add(combo);
    }

    private static void parseDontCombo(DeckRulesConfig config, String value) {
        // Format: dc_id:Card1,Card2[:severity]
        String[] segments = value.split(":");
        if (segments.length < 2) return;

        String id = segments[0].trim();
        String[] pieces = segments[1].split(",");

        AntiSynergy as = new AntiSynergy();
        as.setId(id);
        as.setName(id);
        List<String> pieceList = new ArrayList<>();
        for (String p : pieces) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) pieceList.add(trimmed);
        }
        as.setPieces(pieceList);
        as.setReason("");
        if (segments.length >= 3) {
            as.setSeverity(Severity.fromString(segments[2].trim()));
        }
        config.dontCombos.add(as);
    }

    /** @return an empty, non-null config for decks without rules. */
    public static DeckRulesConfig empty() {
        return new DeckRulesConfig();
    }
}


