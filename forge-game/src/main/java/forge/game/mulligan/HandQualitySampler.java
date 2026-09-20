package forge.game.mulligan;

import forge.deck.Deck;
import forge.deck.mulligan.HandQualityTarget;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Monte Carlo sampler over a deck's own main-deck pool, used to give the deck-select "Target
 * Hand Quality" picker deck-specific thresholds instead of an arbitrary fixed scale (a 99-card
 * Commander deck's achievable §6.1.6 Starting Hand Quality range depends entirely on its own
 * curve and colors). Exact enumeration of all C(deckSize, 7) opening hands is combinatorially
 * infeasible, so this samples a large number of random 7-card hands and reports percentiles of
 * the resulting score distribution - "Best" is the maximum observed across the sample, a
 * disclosed approximation of the true optimum rather than an exhaustive search.
 */
public final class HandQualitySampler {

    private HandQualitySampler() { }

    private static final int SAMPLE_SIZE = 3000;
    private static final int HAND_SIZE = 7;

    /** Percentile thresholds of a deck's Starting Hand Quality (§6.1.6) distribution. */
    public static final class Percentiles {
        /** 0th percentile (minimum observed) - "keep almost anything". */
        public final double q1;
        /** 25th percentile - "better than the bottom quartile". */
        public final double q2;
        /** 50th percentile (median) - "an above-average hand". */
        public final double q3;
        /** 75th percentile - "a top-quartile hand". */
        public final double q4;
        /** Maximum observed across the sample - "the best hand seen". */
        public final double best;

        Percentiles(double q1, double q2, double q3, double q4, double best) {
            this.q1 = q1;
            this.q2 = q2;
            this.q3 = q3;
            this.q4 = q4;
            this.best = best;
        }

        public double forTarget(HandQualityTarget t) {
            if (t == null) return Double.NEGATIVE_INFINITY;
            switch (t) {
                case Q1: return q1;
                case Q2: return q2;
                case Q3: return q3;
                case Q4: return q4;
                case BEST: return best;
                default: return Double.NEGATIVE_INFINITY;
            }
        }
    }

    /**
     * Sample {@code deck}'s main-deck pool and return percentile thresholds of the resulting
     * Starting Hand Quality distribution, scored via {@code evaluator}. Cheap enough (a few
     * thousand 7-card draws, summing already-resolved per-card values) to call on demand rather
     * than caching - a few milliseconds for a typical 99-card Commander deck.
     */
    public static Percentiles compute(Deck deck, DecklistMulliganEvaluator evaluator) {
        List<PaperCard> pool = deck.getMain().toFlatList();
        if (pool.size() < HAND_SIZE) {
            return new Percentiles(0, 0, 0, 0, 0);
        }

        Random rnd = new Random();
        double[] scores = new double[SAMPLE_SIZE];
        List<PaperCard> working = new ArrayList<>(pool);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            Collections.shuffle(working, rnd);
            scores[i] = evaluator.startingHandQualityPaper(working.subList(0, HAND_SIZE));
        }
        Arrays.sort(scores);

        return new Percentiles(
                percentile(scores, 0.00),
                percentile(scores, 0.25),
                percentile(scores, 0.50),
                percentile(scores, 0.75),
                scores[scores.length - 1]
        );
    }

    private static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) return 0.0;
        int idx = (int) Math.floor(p * (sorted.length - 1));
        idx = Math.max(0, Math.min(idx, sorted.length - 1));
        return sorted[idx];
    }
}
