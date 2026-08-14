package forge.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs turn-by-turn game state from an L1 event stream in a replay JSON file.
 *
 * Takes a parsed {@link ReplayLogParser} and applies events incrementally to build
 * a list of {@link TurnSnapshot} objects — one per turn — each containing the game
 * state at the start of that turn plus all events that occurred during it.
 *
 * This is a pure data transformer: it does not interact with the game engine.
 */
public class ReplayStateReconstructor {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayStateReconstructor.class);

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

    /**
     * Minimal info about a single permanent on the battlefield.
     * {@code type} is the card's type line (e.g. "Creature — Goblin") or empty string.
     */
    public static class BattlefieldCardInfo {
        public final String name;
        public final String type;

        public BattlefieldCardInfo(String name, String type) {
            this.name = name != null ? name : "?";
            this.type = type != null ? type : "";
        }
    }

    /** Snapshot of game state at the START of a turn, plus all events during that turn. */
    public static class TurnSnapshot {
        public final int turnNumber;
        public final String activePlayerId;
        public final Map<String, Integer> lifeTotals;
        public final Map<String, Integer> handSizes;
        public final Map<String, Integer> librarySizes;
        public final Map<String, Integer> graveyardSizes;
        public final Map<String, Integer> exileSizes;
        public final Map<String, Integer> battlefieldCounts;
        /** Per-player list of permanents on the battlefield at the START of this turn. */
        public final Map<String, List<BattlefieldCardInfo>> battlefieldCards;
        public final List<EventEntry> events;

        /**
         * True for the very first synthetic entry representing game initialisation
         * (before any {@code ACTIVE_PLAYER_CHANGE} events).
         */
        public boolean isPreGame = false;

        /**
         * True for the synthetic terminal entry representing the end-of-game summary.
         */
        public boolean isGameOver = false;

        /**
         * True for the synthetic first entry showing the overall game overview
         * (players, decks, winner, game length). Displayed when first opening a replay.
         */
        public boolean isGameOverview = false;

        /**
         * Set to {@code true} when at least one {@code LEARNING_MARKER} event occurs
         * during this turn.
         */
        public boolean hasLearningMarker = false;

        public TurnSnapshot(int turnNumber, String activePlayerId,
                     Map<String, Integer> lifeTotals,
                     Map<String, Integer> handSizes,
                     Map<String, Integer> librarySizes,
                     Map<String, Integer> graveyardSizes,
                     Map<String, Integer> exileSizes,
                     Map<String, Integer> battlefieldCounts,
                     Map<String, List<BattlefieldCardInfo>> battlefieldCards) {
            this.turnNumber = turnNumber;
            this.activePlayerId = activePlayerId;
            this.lifeTotals = new LinkedHashMap<>(lifeTotals);
            this.handSizes = new LinkedHashMap<>(handSizes);
            this.librarySizes = new LinkedHashMap<>(librarySizes);
            this.graveyardSizes = new LinkedHashMap<>(graveyardSizes);
            this.exileSizes = new LinkedHashMap<>(exileSizes);
            this.battlefieldCounts = new LinkedHashMap<>(battlefieldCounts);
            // Deep-copy the battlefield card lists
            Map<String, List<BattlefieldCardInfo>> bfCopy = new LinkedHashMap<>();
            for (Map.Entry<String, List<BattlefieldCardInfo>> e : battlefieldCards.entrySet()) {
                bfCopy.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            this.battlefieldCards = Collections.unmodifiableMap(bfCopy);
            this.events = new ArrayList<>();
        }

        /** Returns a one-line summary of the state at the start of this turn. */
        public String getSummary(Map<String, String> playerNames) {
            StringBuilder sb = new StringBuilder();
            sb.append("Turn ").append(turnNumber);
            if (activePlayerId != null) {
                String name = playerNames.getOrDefault(activePlayerId, activePlayerId);
                sb.append(" (").append(name).append(")");
            }
            sb.append(" | ");
            boolean first = true;
            for (Map.Entry<String, Integer> e : lifeTotals.entrySet()) {
                if (!first) sb.append(", ");
                String name = playerNames.getOrDefault(e.getKey(), e.getKey());
                sb.append(name).append(": ").append(e.getValue()).append(" hp");
                first = false;
            }
            return sb.toString();
        }
    }

    /** A single L1 event with a human-readable description. */
    public static class EventEntry {
        public final int index;
        public final String timeMarker;
        public final String actor;
        public final String type;
        public final String description;

        EventEntry(int index, String timeMarker, String actor, String type, String description) {
            this.index = index;
            this.timeMarker = timeMarker;
            this.actor = actor;
            this.type = type;
            this.description = description;
        }

        @Override
        public String toString() {
            String t = timeMarker != null ? "[" + timeMarker + "] " : "";
            return t + description;
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final List<TurnSnapshot> turns = new ArrayList<>();
    private final Map<String, String> playerNames;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ReplayStateReconstructor(ReplayLogParser parser) {
        this.playerNames = new LinkedHashMap<>();
        Map<String, Integer> startingLife = new LinkedHashMap<>();

        for (Map.Entry<String, ReplayLogParser.PlayerInfo> entry : parser.getPlayers().entrySet()) {
            String pid = entry.getKey();
            ReplayLogParser.PlayerInfo info = entry.getValue();
            playerNames.put(pid, info.name != null ? info.name : pid);
            startingLife.put(pid, info.startingLife);
        }

        JsonObject root = parser.getRoot();
        if (root == null) {
            LOG.warn("ReplayStateReconstructor: parser has no root JSON (not parsed?)");
            return;
        }

        // Build card-name → type map from the card_index section
        Map<String, String> nameToType = buildNameToTypeMap(root);

        JsonArray events = null;
        if (root.has("events") && root.get("events").isJsonArray()) {
            events = root.getAsJsonArray("events");
        } else if (root.has("log_l1") && root.get("log_l1").isJsonArray()) {
            events = root.getAsJsonArray("log_l1");
        }

        if (events == null || events.size() == 0) {
            LOG.info("ReplayStateReconstructor: no events found in replay");
            Map<String, List<BattlefieldCardInfo>> emptyBf = new LinkedHashMap<>();
            for (String pid : startingLife.keySet()) emptyBf.put(pid, new ArrayList<>());
            TurnSnapshot snap = new TurnSnapshot(0, null, startingLife,
                    new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                    emptyBf);
            snap.isPreGame = true;
            turns.add(snap);
            TurnSnapshot goSnap = new TurnSnapshot(0, null, startingLife,
                    new LinkedHashMap<>(), new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                    emptyBf);
            goSnap.isGameOver = true;
            turns.add(goSnap);
            return;
        }

        // Parse initial_state.objects for any cards pre-placed on battlefield
        Map<String, List<String>> initialBattlefield = parseInitialBattlefield(root);

        reconstruct(events, startingLife, nameToType, initialBattlefield);
    }

    /** Build cardName → typeString from card_index (handles both name-keyed and ID-keyed indexes). */
    private static Map<String, String> buildNameToTypeMap(JsonObject root) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!root.has("card_index") || !root.get("card_index").isJsonObject()) return result;
        JsonObject index = root.getAsJsonObject("card_index");
        for (Map.Entry<String, JsonElement> entry : index.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject def = entry.getValue().getAsJsonObject();
            String name = getStr(def, "name");
            String type = getStr(def, "type");
            if (name == null) name = entry.getKey(); // key might be the name itself
            if (name != null && type != null) {
                result.put(name, type);
            }
        }
        return result;
    }

    /** Read initial_state.objects to get any cards starting on the battlefield. */
    private static Map<String, List<String>> parseInitialBattlefield(JsonObject root) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (!root.has("initial_state") || !root.get("initial_state").isJsonObject()) return result;
        JsonObject initState = root.getAsJsonObject("initial_state");
        if (!initState.has("objects") || !initState.get("objects").isJsonObject()) return result;
        JsonObject objects = initState.getAsJsonObject("objects");
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject obj = entry.getValue().getAsJsonObject();
            String zone = getStr(obj, "zone");
            String cardRef = getStr(obj, "card_ref"); // set to card.getName() in real game logs
            if (zone != null && zone.endsWith(":battlefield") && cardRef != null) {
                String pid = zone.split(":")[0];
                result.computeIfAbsent(pid, k -> new ArrayList<>()).add(cardRef);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Reconstruction logic
    // -------------------------------------------------------------------------

    private void reconstruct(JsonArray events, Map<String, Integer> startingLife,
                             Map<String, String> nameToType,
                             Map<String, List<String>> initialBattlefield) {
        Map<String, Integer> life = new LinkedHashMap<>(startingLife);
        Map<String, Integer> handSize = new LinkedHashMap<>();
        Map<String, Integer> libSize = new LinkedHashMap<>();
        Map<String, Integer> graveyardSize = new LinkedHashMap<>();
        Map<String, Integer> exileSize = new LinkedHashMap<>();
        Map<String, Integer> battlefieldCount = new LinkedHashMap<>();
        // Per-player mutable list of card names currently on the battlefield
        Map<String, List<String>> currentBattlefield = new LinkedHashMap<>();

        for (String pid : life.keySet()) {
            handSize.put(pid, 7);
            libSize.put(pid, 60);
            graveyardSize.put(pid, 0);
            exileSize.put(pid, 0);
            List<String> initBf = initialBattlefield.get(pid);
            if (initBf != null && !initBf.isEmpty()) {
                battlefieldCount.put(pid, initBf.size());
                currentBattlefield.put(pid, new ArrayList<>(initBf));
            } else {
                battlefieldCount.put(pid, 0);
                currentBattlefield.put(pid, new ArrayList<>());
            }
        }

        int currentTurn = 0; // 0 = pre-game init
        String currentActivePlayer = life.isEmpty() ? null : life.keySet().iterator().next();
        TurnSnapshot currentSnapshot = new TurnSnapshot(currentTurn, currentActivePlayer,
                life, handSize, libSize, graveyardSize, exileSize, battlefieldCount,
                toBattlefieldCardInfoMap(currentBattlefield, nameToType));
        currentSnapshot.isPreGame = true;
        turns.add(currentSnapshot);

        for (JsonElement evtEl : events) {
            if (!evtEl.isJsonObject()) continue;
            JsonObject evt = evtEl.getAsJsonObject();

            int idx = evt.has("i") ? evt.get("i").getAsInt() : 0;
            String timeMarker = getStr(evt, "t");
            String actor = getStr(evt, "a");
            String type = getStr(evt, "type");
            if (type == null) continue;

            JsonObject data = evt.has("data") && evt.get("data").isJsonObject()
                    ? evt.getAsJsonObject("data") : null;

            // Track battlefield card movements BEFORE buildDescription updates zone counts
            if ("MOVE".equals(type) && data != null) {
                updateBattlefieldCards(data, actor, currentBattlefield);
            } else if ("PLAY_LAND".equals(type) && data != null) {
                // Lands never go through a MOVE event at all (confirmed against real replay
                // logs: PLAY_LAND has no from/to fields), so without this they never appear
                // on the battlefield despite being the single most common permanent type.
                String cardName = getCardDisplayName(data);
                String pid = getStr(data, "player");
                if (pid == null) pid = actor;
                if (cardName != null && pid != null) {
                    currentBattlefield.computeIfAbsent(pid, k -> new ArrayList<>()).add(cardName);
                    battlefieldCount.merge(pid, 1, Integer::sum);
                }
            }

            if ("ACTIVE_PLAYER_CHANGE".equals(type)) {
                if (data != null && data.has("turn_number")) {
                    currentTurn = data.get("turn_number").getAsInt();
                } else {
                    currentTurn = Math.max(1, currentTurn + 1);
                }
                String newActivePlayer = data != null ? getStr(data, "new_player") : null;
                if (newActivePlayer == null && data != null) newActivePlayer = getStr(data, "player");
                if (newActivePlayer == null) newActivePlayer = actor;

                currentActivePlayer = newActivePlayer;
                currentSnapshot = new TurnSnapshot(currentTurn, currentActivePlayer,
                        life, handSize, libSize, graveyardSize, exileSize, battlefieldCount,
                        toBattlefieldCardInfoMap(currentBattlefield, nameToType));
                turns.add(currentSnapshot);

                String name = playerNames.getOrDefault(currentActivePlayer, currentActivePlayer);
                currentSnapshot.events.add(new EventEntry(idx, timeMarker, actor, type,
                        "Turn " + currentTurn + " begins \u2014 " + name + "'s turn"));
                continue;
            }

            // Flag the current snapshot when a learning marker is encountered
            if ("LEARNING_MARKER".equals(type)) {
                currentSnapshot.hasLearningMarker = true;
            }

            String description = buildDescription(type, actor, data, life, handSize, libSize,
                    graveyardSize, exileSize, battlefieldCount);
            currentSnapshot.events.add(new EventEntry(idx, timeMarker, actor, type, description));
        }

        // Add terminal Game Over snapshot using the final state
        TurnSnapshot gameOverSnap = new TurnSnapshot(currentTurn, null,
                life, handSize, libSize, graveyardSize, exileSize, battlefieldCount,
                toBattlefieldCardInfoMap(currentBattlefield, nameToType));
        gameOverSnap.isGameOver = true;
        turns.add(gameOverSnap);
    }

    /** Convert the mutable battlefield tracking map to an immutable BattlefieldCardInfo map. */
    private static Map<String, List<BattlefieldCardInfo>> toBattlefieldCardInfoMap(
            Map<String, List<String>> current, Map<String, String> nameToType) {
        Map<String, List<BattlefieldCardInfo>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : current.entrySet()) {
            List<BattlefieldCardInfo> cards = new ArrayList<>();
            for (String name : entry.getValue()) {
                cards.add(new BattlefieldCardInfo(name, nameToType.get(name)));
            }
            result.put(entry.getKey(), cards);
        }
        return result;
    }

    /** Update the live battlefield card lists when a MOVE event is processed. */
    private static void updateBattlefieldCards(JsonObject data, String actor,
                                                Map<String, List<String>> currentBattlefield) {
        String cardName = getCardDisplayName(data);
        if (cardName == null) return;
        String from = getStr(data, "from");
        String to = getStr(data, "to");

        // Remove from source battlefield if applicable
        if (from != null && from.contains(":battlefield")) {
            String pid = from.split(":")[0];
            List<String> list = currentBattlefield.get(pid);
            if (list != null) list.remove(cardName);
        }
        // Add to destination battlefield if applicable
        if (to != null && to.contains("battlefield")) {
            String pid = extractPlayerFromZone(to, from, actor, data);
            if (pid != null) {
                currentBattlefield.computeIfAbsent(pid, k -> new ArrayList<>()).add(cardName);
            }
        }
    }

    /**
     * Determine the player ID for a zone reference that may lack a player prefix.
     * A cast permanent's stack-to-battlefield MOVE has bare "to":"battlefield" with no
     * player prefix at all (confirmed against real replay logs) - the event's own
     * controller/owner field is the authoritative source there, checked before falling back
     * to the source zone's player or the acting player.
     */
    private static String extractPlayerFromZone(String zone, String fromZone, String actor, JsonObject data) {
        if (zone == null) return null;
        int colon = zone.indexOf(':');
        if (colon > 0) {
            String pid = zone.substring(0, colon);
            if (!pid.equalsIgnoreCase("shared") && !pid.equalsIgnoreCase("stack")) {
                return pid;
            }
        }
        // No player prefix in "to" zone (e.g. plain "battlefield") — the event's own
        // controller/owner is authoritative for whose battlefield this is.
        if (data != null) {
            String controller = getStr(data, "controller");
            if (controller != null) return controller;
            String owner = getStr(data, "owner");
            if (owner != null) return owner;
        }
        // No usable player in "to" zone — try to derive from "from" zone
        if (fromZone != null) {
            int fc = fromZone.indexOf(':');
            if (fc > 0) {
                String pid = fromZone.substring(0, fc);
                if (!pid.equalsIgnoreCase("shared") && !pid.equalsIgnoreCase("stack")) {
                    return pid;
                }
            }
        }
        // Final fallback: actor (card owner in MOVE events)
        if (actor != null && !actor.equalsIgnoreCase("SYS")) {
            return actor;
        }
        return null;
    }

    private String buildDescription(String type, String actor, JsonObject data,
                                     Map<String, Integer> life,
                                     Map<String, Integer> handSize,
                                     Map<String, Integer> libSize,
                                     Map<String, Integer> graveyardSize,
                                     Map<String, Integer> exileSize,
                                     Map<String, Integer> battlefieldCount) {
        String actorName = actor != null ? playerNames.getOrDefault(actor, actor) : "";

        switch (type) {
            case "PHASE_CHANGE": {
                String phase = data != null ? getStr(data, "phase") : null;
                return phase != null ? "\u2192 " + phase : "\u2192 phase change";
            }
            case "DRAW": {
                String pid = null;
                if (data != null) {
                    String from = getStr(data, "from");
                    if (from != null && from.contains(":")) pid = from.split(":")[0];
                }
                if (pid == null) pid = actor;
                adjustZone(libSize, pid, -1);
                adjustZone(handSize, pid, +1);
                String pName = playerNames.getOrDefault(pid, pid);
                return pName + " draws a card";
            }
            case "MULLIGAN": {
                String pid = actor;
                if (data != null) {
                    String p = getStr(data, "player");
                    if (p != null) pid = p;
                }
                int keepCount = 7;
                if (data != null && data.has("hand_size_after")) {
                    keepCount = data.get("hand_size_after").getAsInt();
                } else if (data != null && data.has("keep_count")) {
                    keepCount = data.get("keep_count").getAsInt();
                }
                handSize.put(pid, keepCount);
                libSize.put(pid, Math.max(0, libSize.getOrDefault(pid, 60) + (7 - keepCount)));
                String mulliganName = playerNames.getOrDefault(pid, pid);
                return mulliganName + " mulligans to " + keepCount;
            }
            case "CAST": {
                String cardName = data != null ? getCardDisplayName(data) : null;
                String cost = data != null ? formatCostElement(data.has("cost") ? data.get("cost") : null) : null;
                StringBuilder sb = new StringBuilder(actorName).append(" casts ");
                sb.append(cardName != null ? cardName : "a spell");
                if (cost != null) sb.append(" (").append(cost).append(")");
                return sb.toString();
            }
            case "PLAY_LAND": {
                String cardName = data != null ? getCardDisplayName(data) : null;
                return actorName + " plays " + (cardName != null ? cardName : "a land");
            }
            case "RESOLVE": {
                String cardName = data != null ? getCardDisplayName(data) : null;
                boolean fizzled = data != null && data.has("fizzled") && data.get("fizzled").getAsBoolean();
                String suffix = fizzled ? " (fizzled)" : "";
                return (cardName != null ? cardName : "Spell") + " resolves" + suffix;
            }
            case "MOVE": {
                String cardName = data != null ? getCardDisplayName(data) : null;
                String from = data != null ? getStr(data, "from") : null;
                String to = data != null ? getStr(data, "to") : null;
                updateZoneSizes(from, to, data, handSize, libSize, graveyardSize, exileSize, battlefieldCount);
                String fromStr = from != null ? formatZone(from) : "?";
                String toStr = to != null ? formatZone(to) : "?";
                if (cardName != null) {
                    return cardName + ": " + fromStr + " \u2192 " + toStr;
                }
                return fromStr + " \u2192 " + toStr;
            }
            case "DAMAGE": {
                String target = data != null ? getStr(data, "target") : null;
                String targetName = data != null ? getStr(data, "target_name") : null;
                if (targetName == null && target != null) targetName = playerNames.getOrDefault(target, target);
                int amount = data != null && data.has("amount") ? data.get("amount").getAsInt() : 0;
                String sourceName = data != null ? getStr(data, "source_name") : null;
                if (target != null && life.containsKey(target)) {
                    life.merge(target, -amount, Integer::sum);
                }
                String displayTarget = targetName != null ? targetName : (target != null ? target : "target");
                String suffix = sourceName != null ? " from " + sourceName : "";
                return amount + " damage to " + displayTarget + suffix;
            }
            case "LIFE": {
                String pid = data != null ? getStr(data, "player") : actor;
                if (pid == null) pid = actor;
                if (data != null) {
                    if (data.has("new_total")) {
                        int newLife = data.get("new_total").getAsInt();
                        life.put(pid, newLife);
                        String name = playerNames.getOrDefault(pid, pid);
                        return name + "'s life: " + newLife;
                    } else if (data.has("life")) {
                        int newLife = data.get("life").getAsInt();
                        life.put(pid, newLife);
                        String name = playerNames.getOrDefault(pid, pid);
                        return name + "'s life: " + newLife;
                    }
                }
                return actorName + " life changed";
            }
            case "DECLARE_ATTACKERS": {
                int count = data != null && data.has("attackers") && data.get("attackers").isJsonObject()
                        ? data.getAsJsonObject("attackers").size() : 0;
                return actorName + " declares " + (count > 0 ? count + " attacker" + (count != 1 ? "s" : "") : "attackers");
            }
            case "DECLARE_BLOCKERS": {
                int count = data != null && data.has("blockers") && data.get("blockers").isJsonObject()
                        ? data.getAsJsonObject("blockers").size() : 0;
                return actorName + " declares " + (count > 0 ? count + " blocker" + (count != 1 ? "s" : "") : "blockers");
            }
            case "DISCARD": {
                String cardName = data != null ? getCardDisplayName(data) : null;
                adjustZone(handSize, actor, -1);
                return actorName + " discards" + (cardName != null ? " " + cardName : " a card");
            }
            case "COUNTER": {
                String cardName = data != null ? getCardDisplayName(data) : null;
                return (cardName != null ? cardName : "Spell") + " is countered";
            }
            case "TRIGGER": {
                String sourceName = data != null ? getStr(data, "source_name") : null;
                if (sourceName == null) sourceName = data != null ? getStr(data, "source") : null;
                return "Triggered: " + (sourceName != null ? sourceName : "ability");
            }
            case "ACTIVATE": {
                String sourceName = data != null ? getStr(data, "source_name") : null;
                if (sourceName == null) sourceName = data != null ? getCardDisplayName(data) : null;
                return actorName + " activates" + (sourceName != null ? " " + sourceName : "");
            }
            case "GAME_START": {
                return "Game started";
            }
            case "LEARNING_MARKER": {
                String label = data != null ? getStr(data, "label") : null;
                return "[Marker] " + (label != null ? label : "");
            }
            case "RESOURCES": {
                String pid = data != null ? getStr(data, "player") : actor;
                String name = playerNames.getOrDefault(pid, pid);
                int lands = data != null && data.has("land_count") ? data.get("land_count").getAsInt() : 0;
                return name + ": " + lands + " land" + (lands != 1 ? "s" : "");
            }
            case "TAP": {
                String cardName = data != null ? getStr(data, "card_name") : null;
                boolean tapped = data == null || !data.has("tapped") || data.get("tapped").getAsBoolean();
                return (cardName != null ? cardName : "Card") + (tapped ? " tapped" : " untapped");
            }
            case "TURN_START": {
                String pid = data != null ? getStr(data, "player") : null;
                if (pid == null) pid = actor;
                String name = playerNames.getOrDefault(pid, pid);
                int turn = data != null && data.has("turn") ? data.get("turn").getAsInt() : 0;
                return "Turn " + (turn > 0 ? turn + " \u2014 " : "") + name + "'s turn starts";
            }
            default:
                return type.toLowerCase().replace("_", " ");
        }
    }

    private static void adjustZone(Map<String, Integer> map, String pid, int delta) {
        if (pid != null) {
            map.merge(pid, delta, (a, b) -> Math.max(0, a + b));
        }
    }

    private static void updateZoneSizes(String from, String to, JsonObject data,
                                         Map<String, Integer> handSize,
                                         Map<String, Integer> libSize,
                                         Map<String, Integer> graveyardSize,
                                         Map<String, Integer> exileSize,
                                         Map<String, Integer> battlefieldCount) {
        applyZoneDelta(from, data, handSize, libSize, graveyardSize, exileSize, battlefieldCount, -1);
        applyZoneDelta(to, data, handSize, libSize, graveyardSize, exileSize, battlefieldCount, +1);
    }

    private static void applyZoneDelta(String zoneRef, JsonObject data,
                                        Map<String, Integer> handSize,
                                        Map<String, Integer> libSize,
                                        Map<String, Integer> graveyardSize,
                                        Map<String, Integer> exileSize,
                                        Map<String, Integer> battlefieldCount,
                                        int delta) {
        if (zoneRef == null) return;
        String pid;
        String zoneName;
        int colon = zoneRef.indexOf(':');
        if (colon > 0) {
            pid = zoneRef.substring(0, colon);
            zoneName = zoneRef.substring(colon + 1);
        } else {
            // No player prefix (e.g. a cast permanent's plain "battlefield" or "stack") - the
            // event's own controller/owner field is the authoritative source. Same class of
            // gap as extractPlayerFromZone() above; a bare zone with no resolvable player
            // (e.g. "stack" on the "from" side, or "shared") correctly falls through to a no-op.
            pid = data != null ? getStr(data, "controller") : null;
            if (pid == null && data != null) pid = getStr(data, "owner");
            zoneName = zoneRef;
        }
        if (pid == null) return;
        switch (zoneName) {
            case "hand": adjustZone(handSize, pid, delta); break;
            case "library": adjustZone(libSize, pid, delta); break;
            case "graveyard": adjustZone(graveyardSize, pid, delta); break;
            case "exile": adjustZone(exileSize, pid, delta); break;
            case "battlefield": adjustZone(battlefieldCount, pid, delta); break;
            default: break;
        }
    }

    private static String formatZone(String zone) {
        if (zone == null) return "?";
        int colon = zone.indexOf(':');
        return colon >= 0 ? zone.substring(colon + 1) : zone;
    }

    /**
     * Safely get a string value from a JsonObject key.
     * Returns null if the key is absent, null, or not a string primitive
     * (e.g. when "cost" is a nested JsonObject).
     */
    static String getStr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            return el.getAsString();
        }
        // Not a primitive (e.g. nested JsonObject for "cost") — return null safely
        return null;
    }

    private static String getCardDisplayName(JsonObject data) {
        String name = getStr(data, "card_name");
        if (name != null) return name;
        name = getStr(data, "card");
        if (name != null) return name;
        return getStr(data, "obj");
    }

    private static String formatCostElement(JsonElement costEl) {
        if (costEl == null || costEl.isJsonNull()) return null;
        if (costEl.isJsonPrimitive()) {
            return costEl.getAsString();
        }
        if (costEl.isJsonObject()) {
            JsonObject cost = costEl.getAsJsonObject();
            if (cost.has("mana") && cost.get("mana").isJsonArray()) {
                JsonArray mana = cost.getAsJsonArray("mana");
                StringBuilder sb = new StringBuilder();
                for (JsonElement e : mana) {
                    if (e.isJsonPrimitive()) sb.append(e.getAsString());
                }
                String s = sb.toString().trim();
                if (!s.isEmpty()) return s;
            }
            if (cost.has("total_mana_value") && cost.get("total_mana_value").isJsonPrimitive()) {
                int mv = cost.get("total_mana_value").getAsInt();
                return mv > 0 ? mv + " mana" : "free";
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<TurnSnapshot> getTurns() {
        return Collections.unmodifiableList(turns);
    }

    public Map<String, String> getPlayerNames() {
        return Collections.unmodifiableMap(playerNames);
    }

    public TurnSnapshot getTurn(int index) {
        if (index < 0 || index >= turns.size()) return null;
        return turns.get(index);
    }
}

