package forge.game.mulligan;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.deck.DeckRulesConfig;
import forge.deck.mulligan.ManaProductionUtil;
import forge.game.card.Card;
import forge.item.PaperCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates opening hands using the Commander Decklist Notation mulligan rules (§6.1),
 * including the §6.1.5 Mana Base Band and the (Forge-representable subset of the) §6.1.6
 * Starting Hand Quality score.
 *
 * Decision procedure (§6.1.1/§6.1.2, the model real Forge games use):
 * 1. For each card in hand, compute its value (override → land [× §6.1.1b multicolor bonus] →
 *    mana-value curve bucket, adjusted per §6.1.1a for {X} costs).
 * 2. Sum all card values to get total hand value.
 * 3. Look up the threshold for the current mulligan round.
 * 4. Keep if total_value >= min_value; otherwise, mulligan.
 *
 * Card scoring is expressed against {@link CardRules} so the same logic works both for a live
 * in-game {@link Card} and a deck-select-time {@link PaperCard} (used by
 * {@code forge.deck.mulligan.HandQualitySampler} to sample hands before a game exists).
 */
public class DecklistMulliganEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(DecklistMulliganEvaluator.class);

    /** Cache: file path → evaluator instance (avoids re-parsing per mulligan call). */
    private static final Map<String, DecklistMulliganEvaluator> CACHE = new ConcurrentHashMap<>();

    /** Spec §6.1.2 standard per-round thresholds, used to fill in any round a deck's config
     *  doesn't explicitly list (e.g. a deck that only sets round 0 still gets sane thresholds
     *  for rounds 1-3 instead of those rounds silently always-keeping). Rounds beyond 3 have no
     *  spec default and correctly fall through to "always keep" - see getMinValueForRound(). */
    private static final Map<Integer, Double> STANDARD_DEFAULT_THRESHOLDS = Map.of(
            0, 3.5, 1, 3.0, 2, 2.5, 3, 2.0
    );

    private final DecklistMulliganConfig config;
    /** Precomputed lookup: card name → override value. */
    private final Map<String, Double> overrideMap;
    /** §6.1.1b deck-wide colored-pip weights; empty means "no deck context" -> no multicolor land bonus. */
    private final Map<Byte, Double> pipWeights;
    /** Color identity of the deck's commander(s), for "any color" lands (Command Tower, ...); 0 = unknown. */
    private final byte commanderColorMask;

    public DecklistMulliganEvaluator(DecklistMulliganConfig config) {
        this(config, Collections.emptyMap(), (byte) 0);
    }

    public DecklistMulliganEvaluator(DecklistMulliganConfig config, Map<Byte, Double> pipWeights, byte commanderColorMask) {
        this.config = config;
        this.pipWeights = pipWeights != null ? pipWeights : Collections.emptyMap();
        this.commanderColorMask = commanderColorMask;
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
     * Create a DecklistMulliganEvaluator from a {@link DeckRulesConfig.MulliganConfig}, with no
     * deck context - the §6.1.1b multicolor land bonus and "any color" land resolution are
     * skipped (equivalent to a deck with no colored pips at all). Prefer
     * {@link #fromDeckRules(DeckRulesConfig.MulliganConfig, Deck)} when a {@link Deck} is available.
     */
    public static DecklistMulliganEvaluator fromDeckRules(DeckRulesConfig.MulliganConfig mulliganConfig) {
        return fromDeckRules(mulliganConfig, null);
    }

    /**
     * Create a DecklistMulliganEvaluator from a {@link DeckRulesConfig.MulliganConfig}, deriving
     * §6.1.1b's colored-pip weights and the commander color identity (for "any color" lands) from
     * {@code deck} when supplied.
     */
    public static DecklistMulliganEvaluator fromDeckRules(DeckRulesConfig.MulliganConfig mulliganConfig, Deck deck) {
        if (mulliganConfig == null) return null;

        DecklistMulliganConfig cfg = new DecklistMulliganConfig();

        DeckRulesConfig.MulliganConfig.CardValues src = mulliganConfig.getCardValues();
        if (src != null) {
            DecklistMulliganConfig.CardValues cv = new DecklistMulliganConfig.CardValues();
            cv.setLand(src.getLand());
            cv.setMv0(src.getMv0());
            cv.setMv1(src.getMv1());
            cv.setMv2(src.getMv2());
            cv.setMv3(src.getMv3());
            cv.setMv4(src.getMv4());
            cv.setMv5(src.getMv5());
            cv.setMv6(src.getMv6());
            cv.setMv7Plus(src.getMv7Plus());
            cfg.setCardValues(cv);
        }
        cfg.setManaBaseMin(mulliganConfig.getManaBaseMin());
        cfg.setManaBaseMax(mulliganConfig.getManaBaseMax());

        if (mulliganConfig.getCardOverrides() != null) {
            List<DecklistMulliganConfig.CardOverride> overrides = new ArrayList<>();
            for (DeckRulesConfig.MulliganConfig.CardOverride srcOv : mulliganConfig.getCardOverrides()) {
                DecklistMulliganConfig.CardOverride co = new DecklistMulliganConfig.CardOverride();
                co.setName(srcOv.getName());
                co.setValue(srcOv.getValue());
                co.setReason(srcOv.getReason());
                overrides.add(co);
            }
            cfg.setCardOverrides(overrides);
        }

        if (mulliganConfig.getThresholds() != null) {
            List<DecklistMulliganConfig.Threshold> thresholds = new ArrayList<>();
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

        Map<Byte, Double> pipWeights = Collections.emptyMap();
        byte commanderMask = 0;
        if (deck != null) {
            List<CardRules> nonLandRules = new ArrayList<>();
            for (PaperCard pc : deck.getMain().toFlatList()) {
                CardRules rules = pc.getRules();
                if (!ManaProductionUtil.isLand(rules)) {
                    nonLandRules.add(rules);
                }
            }
            pipWeights = ManaProductionUtil.deckPipWeights(nonLandRules);
            for (PaperCard cmd : deck.getCommanders()) {
                commanderMask |= cmd.getRules().getColorIdentity().getColor();
            }
        }

        return new DecklistMulliganEvaluator(cfg, pipWeights, commanderMask);
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

    // ========================================================================
    // §6.1.1 - Mulligan card value
    // ========================================================================

    private double scoreByRules(String name, boolean isLand, CardRules rules) {
        Double override = overrideMap.get(name);
        if (override != null) {
            return override;
        }
        if (isLand) {
            double base = config.getCardValues().getLand();
            byte colorMask = ManaProductionUtil.producedColorMask(rules, commanderColorMask);
            double multiplier = ManaProductionUtil.coverageMultiplier(colorMask, pipWeights);
            return base * multiplier;
        }
        int mv = ManaProductionUtil.effectiveCurveManaValue(rules);
        return config.getCardValues().forManaValue(mv);
    }

    /** Score a single card in an in-game hand. */
    public double scoreCard(Card card) {
        return scoreByRules(card.getName(), card.isLand(), card.getRules());
    }

    /** Score a single card by its paper printing (used for deck-select-time hand sampling). */
    public double scoreCard(PaperCard card) {
        CardRules rules = card.getRules();
        return scoreByRules(card.getName(), ManaProductionUtil.isLand(rules), rules);
    }

    /** Compute the total mulligan value of an in-game hand. */
    public double evaluateHand(Collection<Card> hand) {
        double total = 0.0;
        for (Card card : hand) {
            total += scoreCard(card);
        }
        return total;
    }

    /** Compute the total mulligan value of a hand of paper cards. */
    public double evaluateHandPaper(Collection<PaperCard> hand) {
        double total = 0.0;
        for (PaperCard card : hand) {
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
    public boolean shouldKeep(Collection<Card> hand, int mulliganRound) {
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
     * A round the deck's config doesn't explicitly list falls back to the spec's standard
     * per-round default (rounds 0-3); beyond that, no default is defined so the hand is
     * always kept.
     */
    public double getMinValueForRound(int round) {
        if (config.getThresholds() != null) {
            for (DecklistMulliganConfig.Threshold t : config.getThresholds()) {
                if (t.getRound() == round) {
                    return t.getMinValue();
                }
            }
        }
        return STANDARD_DEFAULT_THRESHOLDS.getOrDefault(round, 0.0);
    }

    // ========================================================================
    // §6.1.5 - Mana Base Band
    // ========================================================================

    private double manaBaseByRules(boolean isLand, CardRules rules) {
        if (isLand) {
            if (!ManaProductionUtil.producesMana(rules)) {
                return 0.0; // true non-mana utility land (e.g. Maze of Ith)
            }
            byte colorMask = ManaProductionUtil.producedColorMask(rules, commanderColorMask);
            if (ManaProductionUtil.countColors(colorMask) >= 2) {
                return ManaProductionUtil.coverageMultiplier(colorMask, pipWeights);
            }
            return ManaProductionUtil.entersTappedOrConditional(rules) ? 0.8 : 1.0;
        }
        if (!ManaProductionUtil.producesMana(rules)) {
            return 0.0;
        }
        int mv = ManaProductionUtil.manaValue(rules);
        switch (mv) {
            case 0: return 1.0;
            case 1: return 0.9;
            case 2: return 0.6;
            default: return 0.0;
        }
    }

    public double manaBaseScore(Card card) {
        return manaBaseByRules(card.isLand(), card.getRules());
    }

    public double manaBaseScore(PaperCard card) {
        CardRules rules = card.getRules();
        return manaBaseByRules(ManaProductionUtil.isLand(rules), rules);
    }

    public double evaluateManaBase(Collection<Card> hand) {
        double total = 0.0;
        for (Card card : hand) {
            total += manaBaseScore(card);
        }
        return total;
    }

    public double evaluateManaBasePaper(Collection<PaperCard> hand) {
        double total = 0.0;
        for (PaperCard card : hand) {
            total += manaBaseScore(card);
        }
        return total;
    }

    /** Human-facing "can this hand function at all" check - does not penalize flooded hands. */
    public boolean isPlayable(double manaBaseScore) {
        return manaBaseScore >= config.getManaBaseMin();
    }

    /** Stricter, double-sided check for automated hand selection - neither screwed nor flooded. */
    public boolean isGoodAiHand(double manaBaseScore) {
        return manaBaseScore >= config.getManaBaseMin() && manaBaseScore <= config.getManaBaseMax();
    }

    public double getManaBaseMin() { return config.getManaBaseMin(); }
    public double getManaBaseMax() { return config.getManaBaseMax(); }

    // ========================================================================
    // §6.1.6 - Starting Hand Quality (Forge-representable subset only)
    // ========================================================================

    /**
     * §6.1.6's Mana Curve bonus component - the only one of the three components the spec
     * documents as representable from this notation alone (tier ranking and deckmechanic
     * synergy both need MaMo-internal data with no equivalent field here). Flat {@code true} if
     * the hand has a non-land permanent at mana value exactly 1, another at 2, another at 3, and
     * another at 4.
     */
    public boolean manaCurveBonusAchieved(Collection<Card> hand) {
        boolean has1 = false, has2 = false, has3 = false, has4 = false;
        for (Card card : hand) {
            if (!ManaProductionUtil.isNonLandPermanent(card.getRules())) continue;
            switch (card.getCMC()) {
                case 1: has1 = true; break;
                case 2: has2 = true; break;
                case 3: has3 = true; break;
                case 4: has4 = true; break;
                default: break;
            }
        }
        return has1 && has2 && has3 && has4;
    }

    public boolean manaCurveBonusAchievedPaper(Collection<PaperCard> hand) {
        boolean has1 = false, has2 = false, has3 = false, has4 = false;
        for (PaperCard card : hand) {
            CardRules rules = card.getRules();
            if (!ManaProductionUtil.isNonLandPermanent(rules)) continue;
            switch (ManaProductionUtil.manaValue(rules)) {
                case 1: has1 = true; break;
                case 2: has2 = true; break;
                case 3: has3 = true; break;
                case 4: has4 = true; break;
                default: break;
            }
        }
        return has1 && has2 && has3 && has4;
    }

    /**
     * Forge's Starting Hand Quality score: the §6.1.1 total mulligan value plus §6.1.6's
     * representable Mana Curve bonus (+0.5 flat). Combining the two (rather than exposing the
     * +0.5 alone) keeps the score continuous enough to be useful for e.g. quartile-based hand
     * targets - a +/-0.5 flag by itself barely varies across hands.
     */
    public double startingHandQuality(Collection<Card> hand) {
        return evaluateHand(hand) + (manaCurveBonusAchieved(hand) ? 0.5 : 0.0);
    }

    public double startingHandQualityPaper(Collection<PaperCard> hand) {
        return evaluateHandPaper(hand) + (manaCurveBonusAchievedPaper(hand) ? 0.5 : 0.0);
    }

    // ========================================================================
    // Per-card breakdown (mulligan dialog display)
    // ========================================================================

    /** A single hand card's name and its §6.1.1 mulligan value, for UI display. */
    public static final class CardScore {
        public final String name;
        public final double value;
        public CardScore(String name, double value) {
            this.name = name;
            this.value = value;
        }
    }

    /** Per-card mulligan-value breakdown of an in-game hand, highest value first. */
    public List<CardScore> breakdown(Collection<Card> hand) {
        List<CardScore> list = new ArrayList<>();
        for (Card card : hand) {
            list.add(new CardScore(card.getName(), scoreCard(card)));
        }
        list.sort((a, b) -> Double.compare(b.value, a.value));
        return list;
    }

    /**
     * Get the underlying config (for testing/introspection).
     */
    public DecklistMulliganConfig getConfig() {
        return config;
    }
}
