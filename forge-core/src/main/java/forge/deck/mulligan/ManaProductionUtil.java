package forge.deck.mulligan;

import forge.card.CardRules;
import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, {@link CardRules}-only helpers shared by every consumer of the Commander Decklist
 * Notation mulligan rules (spec §6.1) that needs to reason about a card's mana production
 * without a live game in progress - e.g. deck-select-time hand sampling as well as in-game
 * scoring (both {@code forge.game.card.Card} and {@code forge.item.PaperCard} expose
 * {@code getRules()}, so this class never depends on either).
 *
 * <p>Color production is detected via a regex over the card's Oracle text rather than the
 * runtime mana-ability graph, since that graph only exists for a live {@code Card}. This is
 * the same "match the rules text" approach the spec itself uses for enters-tapped detection
 * (§6.1.5); it correctly handles basic lands and any land whose ability prints explicit mana
 * symbols (duals, shocks, guildgates, ...). Lands worded as "any color" (Command Tower, City
 * of Brass-style triomes with color-identity text, etc.) fall back to the supplied commander
 * color identity mask, or all five colors if none is known - a disclosed approximation, not a
 * schema ambiguity, matching this spec's own precedent (§6.1.5 "Known limitation").
 */
public final class ManaProductionUtil {

    private ManaProductionUtil() { }

    private static final Pattern MANA_SYMBOL = Pattern.compile("\\{([WUBRG])\\}");
    private static final Pattern ANY_COLOR = Pattern.compile("(?i)any color");
    private static final Pattern PRODUCES_MANA = Pattern.compile("(?i)Add \\{|Add one mana|Add mana|Add an amount of");
    private static final Pattern ENTERS_TAPPED = Pattern.compile(
            "(?i)enters tapped unless|unless you|you may pay.*\\{|enters tapped|enters the battlefield tapped");

    public static boolean isLand(CardRules rules) {
        return rules != null && rules.getType().isLand();
    }

    /** Non-land permanent (creature/artifact/enchantment/planeswalker) - used by §6.1.6's Mana Curve bonus. */
    public static boolean isNonLandPermanent(CardRules rules) {
        return rules != null && rules.getType().isPermanent() && !rules.getType().isLand();
    }

    /** Real mana value (X = 0), matching the spec's "real mana value anywhere else" convention. */
    public static int manaValue(CardRules rules) {
        if (rules == null) return 0;
        ManaCost cost = rules.getManaCost();
        return cost == null ? 0 : cost.getCMC();
    }

    /** §6.1.1a - true iff the cost contains one or more {@code {X}} symbols. */
    public static boolean hasXCost(CardRules rules) {
        if (rules == null) return false;
        ManaCost cost = rules.getManaCost();
        return cost != null && cost.countX() > 0;
    }

    /** Mana value adjusted per §6.1.1a (X-cost cards score as if X=2), for curve lookup only. */
    public static int effectiveCurveManaValue(CardRules rules) {
        int mv = manaValue(rules);
        return hasXCost(rules) ? mv + 2 : mv;
    }

    private static String oracleText(CardRules rules) {
        String text = rules == null ? null : rules.getOracleText();
        return text == null ? "" : text;
    }

    /** True if this card's rules text indicates it produces mana at all (land or not). */
    public static boolean producesMana(CardRules rules) {
        String text = oracleText(rules);
        return PRODUCES_MANA.matcher(text).find();
    }

    /**
     * §6.1.5's "enters tapped or under a condition" test: matches
     * {@code enters tapped unless}, {@code unless you}, {@code you may pay...{}, {@code enters tapped},
     * or {@code enters the battlefield tapped}.
     */
    public static boolean entersTappedOrConditional(CardRules rules) {
        return ENTERS_TAPPED.matcher(oracleText(rules)).find();
    }

    /**
     * Colors this land can produce, as a {@link MagicColor} bitmask. Reads explicit mana
     * symbols off the Oracle text; "any color" text (Command Tower and the like) falls back to
     * {@code commanderIdentityMask}, or all five colors when that is {@code 0} (colorless/unknown).
     */
    public static byte producedColorMask(CardRules rules, byte commanderIdentityMask) {
        String text = oracleText(rules);
        if (text.isEmpty()) return 0;
        if (ANY_COLOR.matcher(text).find()) {
            return commanderIdentityMask != 0 ? commanderIdentityMask : MagicColor.ALL_COLORS;
        }
        byte mask = 0;
        Matcher m = MANA_SYMBOL.matcher(text);
        while (m.find()) {
            mask |= MagicColor.fromName(m.group(1).charAt(0));
        }
        return mask;
    }

    public static int countColors(byte colorMask) {
        return Integer.bitCount(colorMask & 0xFF);
    }

    /**
     * §6.1.1b step 1 - per-color pip weight across a (already quantity-flattened) list of
     * non-land cards' rules. Colors with no pips at all get weight 0.0.
     */
    public static Map<Byte, Double> deckPipWeights(Iterable<CardRules> nonLandCardRules) {
        final byte[] colors = {MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED, MagicColor.GREEN};
        double[] totals = new double[5];
        double grandTotal = 0.0;
        for (CardRules rules : nonLandCardRules) {
            ManaCost cost = rules == null ? null : rules.getManaCost();
            if (cost == null) continue;
            for (ManaCostShard shard : cost) {
                byte shardMask = shard.getColorMask();
                int n = countColors(shardMask);
                if (n == 0) continue; // generic/colorless shard
                double share = 1.0 / n;
                for (int i = 0; i < colors.length; i++) {
                    if ((shardMask & colors[i]) != 0) {
                        totals[i] += share;
                        grandTotal += share;
                    }
                }
            }
        }
        java.util.Map<Byte, Double> weights = new java.util.HashMap<>();
        for (int i = 0; i < colors.length; i++) {
            weights.put(colors[i], grandTotal > 0 ? totals[i] / grandTotal : 0.0);
        }
        return weights;
    }

    /**
     * §6.1.1b step 2 - the {@code 1.0}-{@code 1.4} multiplier for a land producing {@code colorMask}.
     * Mono-color/colorless lands (fewer than 2 colors) are unaffected ({@code 1.0}).
     */
    public static double coverageMultiplier(byte colorMask, Map<Byte, Double> deckPipWeights) {
        if (countColors(colorMask) < 2) return 1.0;
        double coverage = 0.0;
        for (Map.Entry<Byte, Double> e : deckPipWeights.entrySet()) {
            if ((colorMask & e.getKey()) != 0) {
                coverage += e.getValue();
            }
        }
        coverage = Math.min(coverage, 1.0);
        return 1.0 + 0.4 * coverage;
    }
}
