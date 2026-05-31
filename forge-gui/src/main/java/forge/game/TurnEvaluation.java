package forge.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computed state evaluation for one turn's snapshot.
 * All 10 dimension scores are normalized to [-1, +1] where
 * positive = advantage for the "reference" (human) player.
 *
 * Based on the MTG State Evaluation Spec (mtg-state-evaluation-spec.md).
 */
public class TurnEvaluation {

    /** Human-readable labels for the 10 evaluation dimensions. */
    public static final String[] DIMENSION_NAMES = {
        "Resources", "Board", "Tempo", "Card Adv.", "Life Pressure",
        "Inevitability", "Flexibility", "Risk/Info", "Synergy", "Explosiveness"
    };

    public final int turnNumber;
    public final String humanPlayerId;

    // --- 10 dimension scores normalized to [-1, +1] ---
    // Positive = advantage for humanPlayer; negative = opponent advantage.
    public final float resources;       // 6.1 Mana production & fixing
    public final float boardPresence;   // 6.2 Battlefield strength
    public final float tempo;           // 6.3 Initiative & mana efficiency
    public final float cardAdvantage;   // 6.4 Net hand / engine draw
    public final float lifePressure;    // 6.5 Effective clocks
    public final float inevitability;   // 6.6 Long-term advantage
    public final float flexibility;     // 6.7 Available options
    public final float riskInformation; // 6.8 Exposure & info asymmetry
    public final float synergy;         // 6.9 Strategy cohesion (N/A Phase 1)
    public final float explosiveness;   // 6.10 Burst potential (N/A Phase 1)

    // --- Per-player helper stats for this turn ---
    /** Number of lands played this turn per player ID. */
    public final Map<String, Integer> landDrops;
    /** Number of spells cast this turn per player ID. */
    public final Map<String, Integer> spellsCast;
    /** Number of cards drawn this turn per player ID. */
    public final Map<String, Integer> cardsDrawn;
    /** Land count at START of this turn per player ID. */
    public final Map<String, Integer> landCount;
    /** Creature count at START of this turn per player ID. */
    public final Map<String, Integer> creatureCount;
    /** Board value score (raw, pre-normalization) per player ID. */
    public final Map<String, Float> boardScore;

    /**
     * How significant is this turn?
     * Higher = more important (used for critical turn detection).
     * Combines life swing + board dominance change.
     */
    public final int criticalScore;

    public TurnEvaluation(int turnNumber, String humanPlayerId,
                          float resources, float boardPresence, float tempo,
                          float cardAdvantage, float lifePressure, float inevitability,
                          float flexibility, float riskInformation,
                          float synergy, float explosiveness,
                          Map<String, Integer> landDrops,
                          Map<String, Integer> spellsCast,
                          Map<String, Integer> cardsDrawn,
                          Map<String, Integer> landCount,
                          Map<String, Integer> creatureCount,
                          Map<String, Float> boardScore,
                          int criticalScore) {
        this.turnNumber = turnNumber;
        this.humanPlayerId = humanPlayerId;
        this.resources = resources;
        this.boardPresence = boardPresence;
        this.tempo = tempo;
        this.cardAdvantage = cardAdvantage;
        this.lifePressure = lifePressure;
        this.inevitability = inevitability;
        this.flexibility = flexibility;
        this.riskInformation = riskInformation;
        this.synergy = synergy;
        this.explosiveness = explosiveness;
        this.landDrops    = Collections.unmodifiableMap(new LinkedHashMap<>(landDrops));
        this.spellsCast   = Collections.unmodifiableMap(new LinkedHashMap<>(spellsCast));
        this.cardsDrawn   = Collections.unmodifiableMap(new LinkedHashMap<>(cardsDrawn));
        this.landCount    = Collections.unmodifiableMap(new LinkedHashMap<>(landCount));
        this.creatureCount = Collections.unmodifiableMap(new LinkedHashMap<>(creatureCount));
        this.boardScore   = Collections.unmodifiableMap(new LinkedHashMap<>(boardScore));
        this.criticalScore = criticalScore;
    }

    /** Returns all 10 dimension scores in spec order. */
    public float[] getDimensions() {
        return new float[]{
            resources, boardPresence, tempo, cardAdvantage, lifePressure,
            inevitability, flexibility, riskInformation, synergy, explosiveness
        };
    }
}

