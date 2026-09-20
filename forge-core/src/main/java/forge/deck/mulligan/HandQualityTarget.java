package forge.deck.mulligan;

/**
 * A per-seat "keep redrawing the opening hand until it's at least this good" target, expressed
 * as a percentile of the deck's own achievable §6.1.6 Starting Hand Quality distribution (see
 * {@code forge.ai.mulligan.HandQualitySampler}) rather than a fixed universal score - a
 * deck-specific range is what makes the four quartile levels meaningful across very different
 * decks. Lives in forge-core (not forge-ai, where the sampler itself lives) so that
 * {@code forge.game.player.RegisteredPlayer} can hold it without forge-game depending on forge-ai.
 */
public enum HandQualityTarget {
    /** No target - the dealt opening hand is used as-is (subject only to the normal mulligan rule). */
    NONE,
    /** 0th percentile - practically never triggers a redraw. */
    Q1,
    /** 25th percentile - redraw only a bottom-quartile hand. */
    Q2,
    /** 50th percentile (median) - aim for an above-average hand. */
    Q3,
    /** 75th percentile - aim for a top-quartile hand. */
    Q4,
    /** Maximum observed in the deck's sampled distribution - aim for the best hand found. */
    BEST;

    public static HandQualityTarget fromString(String s) {
        if (s == null || s.isEmpty()) return NONE;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /** Human-readable label for UI display - {@link #name()} stays stable for serialization. */
    @Override
    public String toString() {
        switch (this) {
            case NONE: return "Off";
            case Q1: return "Q1 - Any hand";
            case Q2: return "Q2 - Above bottom quartile";
            case Q3: return "Q3 - Above average";
            case Q4: return "Q4 - Top quartile";
            case BEST: return "Best hand found";
            default: return name();
        }
    }
}
