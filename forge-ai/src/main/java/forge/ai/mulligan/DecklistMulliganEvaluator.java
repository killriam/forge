package forge.ai.mulligan;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.deck.DeckRulesConfig;
import forge.game.card.Card;
import forge.util.collect.FCollectionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates opening hands using the Commander Decklist Notation mulligan rules (§6.1).
 *
 * Decision procedure:
 * 1. For each card in hand, compute its value (override → land → CMC bucket).
 * 2. Sum all card values to get total hand value.
 * 3. Look up the threshold for the current mulligan round.
 * 4. Keep if total_value >= min_value; otherwise, mulligan.
 */
public class DecklistMulliganEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(DecklistMulliganEvaluator.class);

    /** Cache: file path → evaluator instance (avoids re-parsing per mulligan call). */
    private static final Map<String, DecklistMulliganEvaluator> CACHE = new ConcurrentHashMap<>();

    private final DecklistMulliganConfig config;
    /** Precomputed lookup: card name → override value. */
    private final Map<String, Double> overrideMap;

    public DecklistMulliganEvaluator(DecklistMulliganConfig config) {
        this.config = config;
        this.overrideMap = new HashMap<>();
        if (config.getCardOverrides() != null) {
            for (DecklistMulliganConfig.CardOverride co : config.getCardOverrides()) {
                overrideMap.put(co.getName(), co.getValue());
            }
        }
    }

    /**
     * Load a DecklistMulliganEvaluator from a Commander Decklist Notation JSON file.
     * The file must contain a "deck_rules.mulligan" section.
     * Results are cached per file path.
     *
     * @param jsonPath Path to the decklist JSON file
     * @return The evaluator, or null if loading fails
     */
    public static DecklistMulliganEvaluator fromJsonFile(String jsonPath) {
        if (jsonPath == null || jsonPath.isEmpty()) {
            return null;
        }

        return CACHE.computeIfAbsent(jsonPath, path -> {
            try {
                return loadFromFile(path);
            } catch (Exception e) {
                LOG.warn("Failed to load decklist mulligan config from {}: {}", path, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Clear the evaluator cache (e.g., between matches).
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Create a DecklistMulliganEvaluator from a {@link DeckRulesConfig.MulliganConfig}.
     * This bridges the new deck-level rules (from forge-core) to the existing evaluator.
     *
     * @param mulliganConfig mulligan section from the deck rules
     * @return evaluator, or null if config is null
     */
    public static DecklistMulliganEvaluator fromDeckRules(DeckRulesConfig.MulliganConfig mulliganConfig) {
        if (mulliganConfig == null) return null;

        // Convert DeckRulesConfig.MulliganConfig → DecklistMulliganConfig
        DecklistMulliganConfig cfg = new DecklistMulliganConfig();

        // Card values
        DeckRulesConfig.MulliganConfig.CardValues src = mulliganConfig.getCardValues();
        if (src != null) {
            DecklistMulliganConfig.CardValues cv = new DecklistMulliganConfig.CardValues();
            cv.setLand(src.getLand());
            cv.setCmc0To2(src.getCmc0To2());
            cv.setCmc3(src.getCmc3());
            cv.setOther(src.getOther());
            cfg.setCardValues(cv);
        }

        // Card overrides
        if (mulliganConfig.getCardOverrides() != null) {
            java.util.List<DecklistMulliganConfig.CardOverride> overrides = new java.util.ArrayList<>();
            for (DeckRulesConfig.MulliganConfig.CardOverride srcOv : mulliganConfig.getCardOverrides()) {
                DecklistMulliganConfig.CardOverride co = new DecklistMulliganConfig.CardOverride();
                co.setName(srcOv.getName());
                co.setValue(srcOv.getValue());
                co.setReason(srcOv.getReason());
                overrides.add(co);
            }
            cfg.setCardOverrides(overrides);
        }

        // Thresholds
        if (mulliganConfig.getThresholds() != null) {
            java.util.List<DecklistMulliganConfig.Threshold> thresholds = new java.util.ArrayList<>();
            for (DeckRulesConfig.MulliganConfig.Threshold srcTh : mulliganConfig.getThresholds()) {
                DecklistMulliganConfig.Threshold th = new DecklistMulliganConfig.Threshold();
                th.setRound(srcTh.getRound());
                th.setHandSize(srcTh.getHandSize());
                th.setMinValue(srcTh.getMinValue());
                th.setDescription(srcTh.getDescription());
                thresholds.add(th);
            }
            cfg.setThresholds(thresholds);
        }

        return new DecklistMulliganEvaluator(cfg);
    }

    private static DecklistMulliganEvaluator loadFromFile(String path) throws IOException {
        Gson gson = new Gson();
        try (Reader reader = new FileReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOG.warn("Decklist config is not a JSON object: {}", path);
                return null;
            }
            JsonObject rootObj = root.getAsJsonObject();

            // Navigate: deck_rules → mulligan
            JsonObject mulliganObj = null;
            if (rootObj.has("deck_rules") && rootObj.get("deck_rules").isJsonObject()) {
                JsonObject deckRules = rootObj.getAsJsonObject("deck_rules");
                if (deckRules.has("mulligan") && deckRules.get("mulligan").isJsonObject()) {
                    mulliganObj = deckRules.getAsJsonObject("mulligan");
                }
            }

            if (mulliganObj == null) {
                LOG.warn("No deck_rules.mulligan section found in {}", path);
                return null;
            }

            DecklistMulliganConfig config = gson.fromJson(mulliganObj, DecklistMulliganConfig.class);
            if (config == null) {
                config = DecklistMulliganConfig.createDefault();
            }

            // Ensure defaults where missing
            if (config.getCardValues() == null) {
                config.setCardValues(new DecklistMulliganConfig.CardValues());
            }
            if (config.getThresholds() == null || config.getThresholds().isEmpty()) {
                DecklistMulliganConfig defaultConfig = DecklistMulliganConfig.createDefault();
                config.setThresholds(defaultConfig.getThresholds());
            }

            LOG.info("Loaded decklist mulligan config from {} ({} overrides, {} thresholds)",
                    path,
                    config.getCardOverrides() != null ? config.getCardOverrides().size() : 0,
                    config.getThresholds().size());

            return new DecklistMulliganEvaluator(config);
        }
    }

    /**
     * Score a single card according to the mulligan evaluation rules.
     *
     * Priority:
     * 1. Per-card override (by exact name match)
     * 2. Land → card_values.land
     * 3. Non-land, CMC 0-2 → card_values.cmc_0_to_2
     * 4. Non-land, CMC 3 → card_values.cmc_3
     * 5. Non-land, CMC 4+ → card_values.other
     */
    public double scoreCard(Card card) {
        // 1. Check per-card overrides
        String cardName = card.getName();
        Double override = overrideMap.get(cardName);
        if (override != null) {
            return override;
        }

        // 2. Land check
        if (card.isLand()) {
            return config.getCardValues().getLand();
        }

        // 3. CMC-based classification
        int cmc = card.getCMC();
        if (cmc <= 2) {
            return config.getCardValues().getCmc0To2();
        } else if (cmc == 3) {
            return config.getCardValues().getCmc3();
        } else {
            return config.getCardValues().getOther();
        }
    }

    /**
     * Compute the total value of a hand.
     */
    public double evaluateHand(FCollectionView<Card> hand) {
        double total = 0.0;
        for (Card card : hand) {
            total += scoreCard(card);
        }
        return total;
    }

    /**
     * Determine whether to keep the hand for the given mulligan round.
     *
     * @param hand The current hand cards
     * @param mulliganRound 0 = initial 7-card hand, 1 = first mulligan, etc.
     * @return true if the hand should be kept, false to mulligan
     */
    public boolean shouldKeep(FCollectionView<Card> hand, int mulliganRound) {
        double totalValue = evaluateHand(hand);
        double minValue = getMinValueForRound(mulliganRound);

        boolean keep = totalValue >= minValue;
        LOG.debug("Decklist mulligan round {}: hand value={}, threshold={}, decision={}",
                mulliganRound, String.format("%.1f", totalValue), String.format("%.1f", minValue),
                keep ? "KEEP" : "MULLIGAN");

        return keep;
    }

    /**
     * Get the minimum hand value required for a given mulligan round.
     * If no threshold is configured for the round, returns 0 (always keep).
     */
    private double getMinValueForRound(int round) {
        if (config.getThresholds() == null) {
            return 0.0;
        }
        for (DecklistMulliganConfig.Threshold t : config.getThresholds()) {
            if (t.getRound() == round) {
                return t.getMinValue();
            }
        }
        // No threshold configured for this round — always keep (too many mulligans)
        return 0.0;
    }

    /**
     * Get the underlying config (for testing/introspection).
     */
    public DecklistMulliganConfig getConfig() {
        return config;
    }
}

