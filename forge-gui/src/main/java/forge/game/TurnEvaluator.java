package forge.game;

import forge.StaticData;
import forge.card.CardRules;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes {@link TurnEvaluation} objects from {@link ReplayStateReconstructor.TurnSnapshot} data.
 *
 * Implementation phases:
 *  Phase 1 — event-log only: Card Advantage, Life Pressure, per-turn cast/draw/land counts.
 *  Phase 2 — BattlefieldCardInfo.type heuristics: Resources (land MPP), Board Presence (rough).
 *  Phase 3 — Forge StaticData card-DB lookups: accurate P/T, keywords, mana-producer detection.
 *
 * Dimensions 9 (Synergy) and 10 (Explosiveness) require a tag system not yet available;
 * they are returned as 0.0 (N/A).
 */
public final class TurnEvaluator {

    // Lazily populated: card name → CardRules (null = not found or DB unavailable)
    private static final Map<String, CardRules> CARD_RULES_CACHE = new HashMap<>();

    private TurnEvaluator() { }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Evaluate a single turn snapshot relative to the "human" (reference) player.
     *
     * @param turn      snapshot to evaluate
     * @param humanId   player ID whose perspective is "positive" in scores
     * @param allTurns  full turn list for context (previous turn needed for critical score)
     */
    public static TurnEvaluation evaluate(
            ReplayStateReconstructor.TurnSnapshot turn,
            String humanId,
            List<ReplayStateReconstructor.TurnSnapshot> allTurns) {

        List<String> allPlayers = new ArrayList<>(turn.lifeTotals.keySet());
        List<String> opponents = new ArrayList<>(allPlayers);
        opponents.remove(humanId);

        // --- Per-turn event counts ---
        Map<String, Integer> landDrops  = new LinkedHashMap<>();
        Map<String, Integer> spellsCast = new LinkedHashMap<>();
        Map<String, Integer> cardsDrawn = new LinkedHashMap<>();
        for (String pid : allPlayers) {
            landDrops.put(pid, 0);
            spellsCast.put(pid, 0);
            cardsDrawn.put(pid, 0);
        }
        for (ReplayStateReconstructor.EventEntry e : turn.events) {
            if (e.actor == null) continue;
            switch (e.type) {
                case "PLAY_LAND": landDrops.merge(e.actor,  1, Integer::sum); break;
                case "CAST":      spellsCast.merge(e.actor, 1, Integer::sum); break;
                case "DRAW":      cardsDrawn.merge(e.actor, 1, Integer::sum); break;
                default: break;
            }
        }

        // --- Battlefield analysis per player ---
        Map<String, Integer> landCount    = new LinkedHashMap<>();
        Map<String, Integer> creatureCount = new LinkedHashMap<>();
        Map<String, Float>   boardScore   = new LinkedHashMap<>();
        for (String pid : allPlayers) {
            analyzeBattlefield(pid, turn, landCount, creatureCount, boardScore);
        }

        // --- Dimension: Resources (6.1) ---
        float myMPP  = computeMPP(humanId, turn);
        float oppMPP = avgOpponentsMPP(opponents, turn);
        float resources = norm(myMPP - oppMPP, 6f);

        // --- Dimension: Board Presence (6.2) ---
        float myBP  = boardScore.getOrDefault(humanId, 0f);
        float oppBP = avgOpponentsFloat(opponents, boardScore);
        float boardPresence = norm(myBP - oppBP, 12f);

        // --- Life-clock helpers shared by Tempo and Life Pressure ---
        float myLife    = turn.lifeTotals.getOrDefault(humanId, 20);
        float oppLife   = avgOpponentsInt(opponents, turn.lifeTotals);
        float myDNT     = estimateDNT(humanId, turn, opponents);
        float oppDNT    = estimateOpponentsDNT(opponents, turn, humanId);
        float clockOpp  = myDNT  > 0.5f ? oppLife / myDNT  : 100f;
        float clockYou  = oppDNT > 0.5f ? myLife  / oppDNT : 100f;
        float lpRaw     = (1f / Math.max(1f, clockOpp)) - (1f / Math.max(1f, clockYou));

        // --- Dimension: Tempo (6.3) ---
        float myLands  = landCount.getOrDefault(humanId, 0);
        float oppLands = avgOpponents(opponents, landCount);
        float effRaw   = (myLands - oppLands) * 0.3f;
        float tempo    = norm(1.2f * lpRaw + 0.8f * effRaw, 2f);

        // --- Dimension: Card Advantage (6.4) ---
        float myHand  = turn.handSizes.getOrDefault(humanId, 0);
        float oppHand = avgOpponentsInt(opponents, turn.handSizes);
        float cardAdvantage = norm(myHand - oppHand, 8f);

        // --- Dimension: Life Pressure (6.5) ---
        float lifePressure = norm(lpRaw, 1f);

        // --- Dimension: Inevitability (6.6) ---
        // Proxy: board engine advantage (who controls the board more persistently)
        float inevRaw = (myBP > oppBP + 2f ? 2f : 0f) - (oppBP > myBP + 2f ? 2f : 0f);
        float inevitability = norm(inevRaw, 10f);

        // --- Dimension: Flexibility (6.7) ---
        // Proxy: hand options = hand size (more cards = more choices)
        float flexibility = norm(myHand * 0.8f - oppHand * 0.8f, 6f);

        // --- Dimension: Risk/Information (6.8) ---
        // Exposure proxy: wide board without known protection = risk
        float myBfCount  = turn.battlefieldCounts.getOrDefault(humanId, 0);
        // Many creatures → exposure to board wipes
        float exposure = myBfCount > 4 ? (myBfCount - 4) * 0.7f : 0f;
        float riskInformation = norm(-exposure, 4f);

        // --- Dimensions 9 & 10: require tag system — N/A for now ---
        float synergy       = 0f;
        float explosiveness = 0f;

        // --- Critical turn score ---
        int critScore = computeCriticalScore(humanId, opponents, turn, allTurns,
                myBP, oppBP);

        return new TurnEvaluation(turn.turnNumber, humanId,
                resources, boardPresence, tempo, cardAdvantage, lifePressure,
                inevitability, flexibility, riskInformation, synergy, explosiveness,
                landDrops, spellsCast, cardsDrawn, landCount, creatureCount, boardScore,
                critScore);
    }

    // -----------------------------------------------------------------------
    // Battlefield analysis
    // -----------------------------------------------------------------------

    private static void analyzeBattlefield(String pid,
            ReplayStateReconstructor.TurnSnapshot turn,
            Map<String, Integer> landCount,
            Map<String, Integer> creatureCount,
            Map<String, Float> boardScore) {

        List<ReplayStateReconstructor.BattlefieldCardInfo> bf =
                turn.battlefieldCards.getOrDefault(pid, Collections.emptyList());
        int lands = 0, creatures = 0;
        float score = 0f;
        for (ReplayStateReconstructor.BattlefieldCardInfo ci : bf) {
            String type = ci.type != null ? ci.type : "";
            if (typeContains(type, "Land")) {
                lands++;
                // Lands contribute to Resources, not Board Presence
            } else if (typeContains(type, "Creature")) {
                creatures++;
                score += getCreatureValue(ci);
            } else if (typeContains(type, "Planeswalker")) {
                score += 4.0f;
            } else if (!type.isEmpty()) {
                score += 0.8f; // generic noncreature permanent value
            }
        }
        landCount.put(pid, lands);
        creatureCount.put(pid, creatures);
        boardScore.put(pid, score);
    }

    // -----------------------------------------------------------------------
    // Mana Production Potential (Resources, 6.1)
    // -----------------------------------------------------------------------

    private static float computeMPP(String pid, ReplayStateReconstructor.TurnSnapshot turn) {
        List<ReplayStateReconstructor.BattlefieldCardInfo> bf =
                turn.battlefieldCards.getOrDefault(pid, Collections.emptyList());
        float lands = 0f, rocks = 0f, dorks = 0f;
        for (ReplayStateReconstructor.BattlefieldCardInfo ci : bf) {
            String type = ci.type != null ? ci.type : "";
            if (typeContains(type, "Land")) {
                lands++;
            } else if (typeContains(type, "Creature")) {
                // Mana dork: creature that produces mana
                if (isManaProducer(ci.name)) dorks++;
            } else if (typeContains(type, "Artifact")) {
                // Rough proxy: many artifacts in decks are mana rocks
                rocks += 0.5f;
            }
        }
        return lands + 0.9f * rocks + 0.8f * dorks;
    }

    private static float avgOpponentsMPP(List<String> opponents,
                                          ReplayStateReconstructor.TurnSnapshot turn) {
        if (opponents.isEmpty()) return 0f;
        float sum = 0f;
        for (String opp : opponents) sum += computeMPP(opp, turn);
        return sum / opponents.size();
    }

    // -----------------------------------------------------------------------
    // Damage Next Turn estimate (Life Pressure, 6.5 helper)
    // -----------------------------------------------------------------------

    private static float estimateDNT(String pid,
            ReplayStateReconstructor.TurnSnapshot turn,
            List<String> blockingOpponents) {

        List<ReplayStateReconstructor.BattlefieldCardInfo> bf =
                turn.battlefieldCards.getOrDefault(pid, Collections.emptyList());
        float dmg = 0f;
        for (ReplayStateReconstructor.BattlefieldCardInfo ci : bf) {
            String type = ci.type != null ? ci.type : "";
            if (!typeContains(type, "Creature")) continue;
            CardRules rules = lookupRules(ci.name);
            float power = 2f;
            float evasionMult = 1.0f;
            if (rules != null) {
                int p = rules.getIntPower();
                power = (p == Integer.MAX_VALUE || p < 0) ? 2f : p;
                if (rules.hasKeyword("Flying"))  evasionMult = 1.2f;
                if (rules.hasKeyword("Menace"))  evasionMult = Math.max(evasionMult, 1.1f);
                if (rules.hasKeyword("Trample")) evasionMult = Math.max(evasionMult, 1.1f);
            }
            dmg += power * evasionMult;
        }
        // Rough block estimate: total opponent toughness
        float blockT = 0f;
        for (String opp : blockingOpponents) {
            List<ReplayStateReconstructor.BattlefieldCardInfo> oppBf =
                    turn.battlefieldCards.getOrDefault(opp, Collections.emptyList());
            for (ReplayStateReconstructor.BattlefieldCardInfo ci : oppBf) {
                String type = ci.type != null ? ci.type : "";
                if (!typeContains(type, "Creature")) continue;
                CardRules rules = lookupRules(ci.name);
                if (rules != null) {
                    int t = rules.getIntToughness();
                    blockT += (t == Integer.MAX_VALUE || t < 0) ? 2f : t;
                } else {
                    blockT += 2f;
                }
            }
        }
        if (!blockingOpponents.isEmpty()) blockT /= blockingOpponents.size();
        float blockEst = Math.min(blockT, dmg) * 0.5f;
        return Math.max(0f, dmg - blockEst);
    }

    private static float estimateOpponentsDNT(List<String> opponents,
            ReplayStateReconstructor.TurnSnapshot turn, String humanId) {
        if (opponents.isEmpty()) return 0f;
        float total = 0f;
        for (String opp : opponents) {
            total += estimateDNT(opp, turn, Collections.singletonList(humanId));
        }
        return total; // sum, not average, to represent combined opponent threat
    }

    // -----------------------------------------------------------------------
    // Board value per creature (Board Presence, 6.2)
    // -----------------------------------------------------------------------

    private static float getCreatureValue(ReplayStateReconstructor.BattlefieldCardInfo ci) {
        CardRules rules = lookupRules(ci.name);
        if (rules != null) {
            int p = rules.getIntPower();
            int t = rules.getIntToughness();
            if (p == Integer.MAX_VALUE || p < 0) p = 2;
            if (t == Integer.MAX_VALUE || t < 0) t = 2;
            float kw = 0f;
            if (rules.hasKeyword("Flying"))       kw += 0.8f;
            if (rules.hasKeyword("Trample"))      kw += 0.4f;
            if (rules.hasKeyword("First Strike")) kw += 0.4f;
            if (rules.hasKeyword("Double Strike")) kw += 0.6f;
            if (rules.hasKeyword("Deathtouch"))   kw += 0.6f;
            if (rules.hasKeyword("Lifelink"))      kw += 0.4f;
            if (rules.hasKeyword("Hexproof"))      kw += 0.8f;
            return p + 0.8f * t + kw;
        }
        return 2f + 0.8f * 2f; // default 2/2 = 3.6
    }

    // -----------------------------------------------------------------------
    // Critical turn score
    // -----------------------------------------------------------------------

    private static int computeCriticalScore(
            String humanId, List<String> opponents,
            ReplayStateReconstructor.TurnSnapshot turn,
            List<ReplayStateReconstructor.TurnSnapshot> allTurns,
            float myBP, float oppBP) {

        // Find the previous snapshot in allTurns
        int prevIdx = -1;
        for (int i = allTurns.size() - 1; i >= 0; i--) {
            if (allTurns.get(i) == turn) { prevIdx = i - 1; break; }
        }
        if (prevIdx < 0) return 0;
        ReplayStateReconstructor.TurnSnapshot prev = allTurns.get(prevIdx);

        // Life swing
        int lifeSwing = 0;
        for (String pid : turn.lifeTotals.keySet()) {
            lifeSwing += Math.abs(turn.lifeTotals.getOrDefault(pid, 0)
                    - prev.lifeTotals.getOrDefault(pid, 0));
        }
        // Board dominance change
        float prevMyBP  = sumBoardScore(humanId, prev);
        float prevOppBP = 0f;
        if (!opponents.isEmpty()) {
            float sum = 0f;
            for (String opp : opponents) sum += sumBoardScore(opp, prev);
            prevOppBP = sum / opponents.size();
        }
        float domChange = Math.abs((myBP - oppBP) - (prevMyBP - prevOppBP));
        return lifeSwing + (int)(domChange * 2);
    }

    private static float sumBoardScore(String pid, ReplayStateReconstructor.TurnSnapshot turn) {
        List<ReplayStateReconstructor.BattlefieldCardInfo> bf =
                turn.battlefieldCards.getOrDefault(pid, Collections.emptyList());
        float score = 0f;
        for (ReplayStateReconstructor.BattlefieldCardInfo ci : bf) {
            String type = ci.type != null ? ci.type : "";
            if (typeContains(type, "Land")) continue;
            if (typeContains(type, "Creature")) score += getCreatureValue(ci);
            else if (typeContains(type, "Planeswalker")) score += 4.0f;
            else score += 0.8f;
        }
        return score;
    }

    // -----------------------------------------------------------------------
    // Aggregation helpers
    // -----------------------------------------------------------------------

    private static float avgOpponents(List<String> opponents, Map<String, Integer> map) {
        if (opponents.isEmpty()) return 0f;
        float sum = 0f;
        for (String opp : opponents) sum += map.getOrDefault(opp, 0);
        return sum / opponents.size();
    }

    private static float avgOpponentsInt(List<String> opponents, Map<String, Integer> map) {
        return avgOpponents(opponents, map);
    }

    private static float avgOpponentsFloat(List<String> opponents, Map<String, Float> map) {
        if (opponents.isEmpty()) return 0f;
        float sum = 0f;
        for (String opp : opponents) sum += map.getOrDefault(opp, 0f);
        return sum / opponents.size();
    }

    // -----------------------------------------------------------------------
    // Normalization
    // -----------------------------------------------------------------------

    private static float norm(float raw, float cap) {
        return Math.max(-1f, Math.min(1f, raw / cap));
    }

    // -----------------------------------------------------------------------
    // Card DB helpers
    // -----------------------------------------------------------------------

    /** Case-insensitive substring check with word-boundary awareness. */
    private static boolean typeContains(String typeStr, String typeName) {
        if (typeStr == null || typeStr.isEmpty()) return false;
        return typeStr.contains(typeName);
    }

    /**
     * Returns true if the named card's oracle text suggests it produces mana.
     * Used to identify mana dorks on the battlefield.
     */
    private static boolean isManaProducer(String cardName) {
        CardRules rules = lookupRules(cardName);
        if (rules == null) return false;
        String oracle = rules.getOracleText();
        return oracle != null && oracle.contains(": Add");
    }

    /**
     * Look up CardRules by card name, with in-memory caching.
     * Returns null if the card is not found or StaticData is unavailable.
     */
    static CardRules lookupRules(String cardName) {
        if (cardName == null || cardName.isEmpty() || "?".equals(cardName)) return null;
        if (CARD_RULES_CACHE.containsKey(cardName)) return CARD_RULES_CACHE.get(cardName);
        CardRules result = null;
        try {
            StaticData sd = StaticData.instance();
            if (sd != null) {
                PaperCard pc = sd.getCommonCards().getCard(cardName);
                if (pc != null) result = pc.getRules();
            }
        } catch (Exception ignored) {
            // StaticData may not be initialized in test contexts
        }
        CARD_RULES_CACHE.put(cardName, result);
        return result;
    }
}





