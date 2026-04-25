package forge.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a GameState-compatible key-value representation from a replay JSON file
 * at a specific turn. Used to start a game mid-way through a replay by reconstructing
 * the exact zone contents (which cards are in hand, library, battlefield, etc.)
 * from the initial_state + events.
 *
 * <p>Produces lines in the Puzzle/GameState format (e.g. {@code p0life=20},
 * {@code p0hand=Lightning Bolt;Mountain}) that can be parsed by
 * {@code forge.ai.GameState.parse(lines)} and applied via
 * {@code GameState.applyToGame(game)}.</p>
 */
public class ReplayGameStateBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayGameStateBuilder.class);

    /** A card tracked through zone changes. */
    private static class TrackedCard {
        final String cardId;
        final String cardName;
        final String owner;  // "P1", "P2", etc.
        String zone;          // current zone, e.g. "P1:hand", "P1:battlefield", or "battlefield"

        TrackedCard(String cardId, String cardName, String owner, String zone) {
            this.cardId = cardId;
            this.cardName = cardName;
            this.owner = owner;
            this.zone = zone;
        }
    }

    private final ReplayLogParser parser;
    private final Map<String, TrackedCard> cards = new LinkedHashMap<>();
    private final Map<String, Integer> lifeTotals = new LinkedHashMap<>();
    private final List<String> playerIds = new ArrayList<>();
    private int currentTurn = 0;
    private String activePlayerId = null;

    public ReplayGameStateBuilder(ReplayLogParser parser) {
        this.parser = parser;
    }

    /**
     * Reconstruct the game state at the START of the given turn number.
     * Returns a list of GameState key-value lines ready for {@code GameState.parse()}.
     *
     * @param targetTurn the turn number to reconstruct (e.g. 5)
     * @return list of key-value lines, or empty list if reconstruction failed
     */
    public List<String> buildStateAtTurn(int targetTurn) {
        JsonObject root = parser.getRoot();
        if (root == null) {
            LOG.warn("ReplayGameStateBuilder: no root JSON");
            return new ArrayList<>();
        }

        // Initialize player data
        for (Map.Entry<String, ReplayLogParser.PlayerInfo> entry : parser.getPlayers().entrySet()) {
            String pid = entry.getKey();
            playerIds.add(pid);
            lifeTotals.put(pid, entry.getValue().startingLife);
        }

        // Initialize card tracking from initial_state.objects
        initializeCards(root);

        // Get events (try both "events" and "log_l1" keys)
        JsonArray events = null;
        if (root.has("events") && root.get("events").isJsonArray()) {
            events = root.getAsJsonArray("events");
        } else if (root.has("log_l1") && root.get("log_l1").isJsonArray()) {
            events = root.getAsJsonArray("log_l1");
        }

        if (events == null) {
            LOG.warn("ReplayGameStateBuilder: no events found (checked 'events' and 'log_l1')");
            return new ArrayList<>();
        }

        LOG.info("ReplayGameStateBuilder: replaying {} events to reach turn {}", events.size(), targetTurn);

        // Replay events up to the target turn
        replayEventsToTurn(events, targetTurn);

        // Override life totals from per_turn_summary if available (more accurate)
        overrideLifeFromTurnSummary(root, targetTurn);

        // Build GameState lines
        return generateStateLines(targetTurn);
    }

    private void initializeCards(JsonObject root) {
        if (!root.has("initial_state") || !root.get("initial_state").isJsonObject()) return;
        JsonObject initialState = root.getAsJsonObject("initial_state");

        if (!initialState.has("objects") || !initialState.get("objects").isJsonObject()) return;
        JsonObject objects = initialState.getAsJsonObject("objects");

        // Build card_index lookup for name resolution
        Map<String, String> cardIndex = new LinkedHashMap<>();
        if (root.has("card_index") && root.get("card_index").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("card_index").entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    String name = getStr(entry.getValue().getAsJsonObject(), "name");
                    if (name != null) cardIndex.put(entry.getKey(), name);
                }
            }
        }

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject obj = entry.getValue().getAsJsonObject();
            String cardId = entry.getKey();

            String cardName = getStr(obj, "card_ref");
            if (cardName == null) cardName = getStr(obj, "cardRef");
            if (cardName == null) cardName = cardIndex.get(cardId);
            if (cardName == null) continue;

            String owner = getStr(obj, "owner");
            String zone = getStr(obj, "zone");
            if (owner == null && zone != null && zone.contains(":")) {
                owner = zone.substring(0, zone.indexOf(':'));
            }
            if (owner == null) continue;

            cards.put(cardId, new TrackedCard(cardId, cardName, owner, zone));
        }

        LOG.info("ReplayGameStateBuilder: initialized {} cards from initial_state.objects", cards.size());
    }

    private void replayEventsToTurn(JsonArray events, int targetTurn) {
        currentTurn = 1;
        activePlayerId = playerIds.isEmpty() ? null : playerIds.get(0);

        for (JsonElement elem : events) {
            if (!elem.isJsonObject()) continue;
            JsonObject evt = elem.getAsJsonObject();

            String type = getStr(evt, "type");
            if (type == null) continue;

            JsonObject data = evt.has("data") && evt.get("data").isJsonObject()
                    ? evt.getAsJsonObject("data") : null;

            // Check for turn boundary via ACTIVE_PLAYER_CHANGE
            if ("ACTIVE_PLAYER_CHANGE".equals(type)) {
                int newTurn = -1;
                if (data != null && data.has("turn_number")) {
                    newTurn = data.get("turn_number").getAsInt();
                } else if (data != null && data.has("turn")) {
                    newTurn = data.get("turn").getAsInt();
                }
                if (newTurn < 0) newTurn = currentTurn + 1;

                // Stop BEFORE the target turn starts — we want the state at the start of that turn
                if (newTurn >= targetTurn) {
                    String newPlayer = data != null ? getStr(data, "new_player") : null;
                    if (newPlayer == null && data != null) newPlayer = getStr(data, "player");
                    if (newPlayer != null) activePlayerId = newPlayer;
                    currentTurn = newTurn;
                    LOG.info("ReplayGameStateBuilder: reached target turn {} (active={}), stopping", targetTurn, activePlayerId);
                    return;
                }

                currentTurn = newTurn;
                String newPlayer = data != null ? getStr(data, "new_player") : null;
                if (newPlayer == null && data != null) newPlayer = getStr(data, "player");
                if (newPlayer != null) activePlayerId = newPlayer;
            }

            // Also handle TURN_START (legacy format)
            if ("TURN_START".equals(type)) {
                int newTurn = data != null && data.has("turn") ? data.get("turn").getAsInt() : currentTurn + 1;
                if (newTurn >= targetTurn) {
                    String newPlayer = data != null ? getStr(data, "player") : null;
                    if (newPlayer != null) activePlayerId = newPlayer;
                    currentTurn = newTurn;
                    return;
                }
                currentTurn = newTurn;
            }

            // Apply zone changes and life updates
            applyEvent(type, data);
        }

        LOG.info("ReplayGameStateBuilder: processed all {} events, current turn = {}", events.size(), currentTurn);
    }

    private void applyEvent(String type, JsonObject data) {
        if (data == null) return;

        switch (type) {
            case "MOVE": {
                // Handles both "card" and "obj" field names
                String cardId = getStr(data, "card");
                if (cardId == null) cardId = getStr(data, "obj");
                String toZone = getStr(data, "to");
                if (cardId != null && toZone != null) {
                    TrackedCard tc = cards.get(cardId);
                    if (tc != null) {
                        tc.zone = normalizeZone(toZone, tc.owner);
                    } else {
                        // Card not in initial state (token or dynamically created) — start tracking it
                        String cardName = getStr(data, "card_name");
                        String from = getStr(data, "from");
                        String owner = extractPlayer(toZone);
                        if (owner == null) owner = extractPlayer(from);
                        if (cardName != null && owner != null) {
                            TrackedCard newTc = new TrackedCard(cardId, cardName, owner,
                                    normalizeZone(toZone, owner));
                            cards.put(cardId, newTc);
                        }
                    }
                }
                break;
            }
            case "DRAW": {
                // Draw moves card from library to hand (standalone — no MOVE event follows)
                String cardId = getStr(data, "card");
                if (cardId == null) cardId = getStr(data, "obj");
                String from = getStr(data, "from");
                if (cardId != null) {
                    TrackedCard tc = cards.get(cardId);
                    if (tc != null) {
                        String pid = extractPlayer(from);
                        if (pid == null) pid = tc.owner;
                        tc.zone = pid + ":hand";
                    }
                }
                break;
            }
            case "PLAY_LAND": {
                // Land play moves card from hand to battlefield (no MOVE event follows)
                String cardId = getStr(data, "card");
                if (cardId == null) cardId = getStr(data, "obj");
                if (cardId != null) {
                    TrackedCard tc = cards.get(cardId);
                    if (tc != null) {
                        tc.zone = tc.owner + ":battlefield";
                    }
                }
                break;
            }
            case "DISCARD": {
                // Discard moves card from hand to graveyard
                String cardId = getStr(data, "card");
                if (cardId == null) cardId = getStr(data, "obj");
                if (cardId != null) {
                    TrackedCard tc = cards.get(cardId);
                    if (tc != null) {
                        tc.zone = tc.owner + ":graveyard";
                    }
                }
                break;
            }
            case "LIFE": {
                String pid = getStr(data, "player");
                if (pid != null && data.has("new_total")) {
                    lifeTotals.put(pid, data.get("new_total").getAsInt());
                } else if (pid != null && data.has("life")) {
                    lifeTotals.put(pid, data.get("life").getAsInt());
                }
                break;
            }
            case "DAMAGE": {
                // LIFE events handle life tracking authoritatively
                break;
            }
            case "CAST": {
                // CAST moves a card from its current zone to "stack"
                // (No separate MOVE event is logged for hand → stack)
                String cardId = getStr(data, "card");
                if (cardId == null) cardId = getStr(data, "obj");
                if (cardId != null) {
                    TrackedCard tc = cards.get(cardId);
                    if (tc != null) {
                        tc.zone = tc.owner + ":stack";
                    }
                }
                break;
            }
            case "COUNTERS": {
                // Heuristic: when a card receives Time counters, it was exiled (suspended).
                // The replay logger doesn't always capture the zone change to exile as a MOVE event
                // (e.g. The Eleventh Doctor's suspend trigger). Detect this and move to exile.
                String counterType = getStr(data, "counter_type");
                if ("Time".equals(counterType)) {
                    String cardId = getStr(data, "card");
                    if (cardId == null) cardId = getStr(data, "obj");
                    int delta = data.has("delta") ? data.get("delta").getAsInt() : 0;
                    if (cardId != null && delta > 0) {
                        // Card received time counters = was exiled with suspend
                        TrackedCard tc = cards.get(cardId);
                        if (tc != null && !tc.zone.endsWith(":exile") && !"exile".equals(tc.zone)) {
                            LOG.debug("ReplayGameStateBuilder: {} ({}) received Time counters — assuming exile (suspend)",
                                    tc.cardName, cardId);
                            tc.zone = tc.owner + ":exile";
                        }
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * Override life totals from per_turn_summary, which is the most accurate source
     * since it records snapshots directly from the game engine at each turn start.
     */
    private void overrideLifeFromTurnSummary(JsonObject root, int targetTurn) {
        if (!root.has("per_turn_summary") || !root.get("per_turn_summary").isJsonArray()) return;
        JsonArray summaries = root.getAsJsonArray("per_turn_summary");

        // Find the summary for the target turn
        JsonObject targetSummary = null;
        for (JsonElement elem : summaries) {
            if (!elem.isJsonObject()) continue;
            JsonObject summary = elem.getAsJsonObject();
            int turn = summary.has("turn") && !summary.get("turn").isJsonNull()
                    ? summary.get("turn").getAsInt() : -1;
            if (turn == targetTurn) {
                targetSummary = summary;
                break;
            }
        }

        if (targetSummary == null) {
            LOG.debug("ReplayGameStateBuilder: no per_turn_summary for turn {}, using event-based life totals", targetTurn);
            return;
        }

        if (!targetSummary.has("players") || !targetSummary.get("players").isJsonObject()) return;
        JsonObject players = targetSummary.getAsJsonObject("players");

        for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
            String pid = entry.getKey();
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject pStats = entry.getValue().getAsJsonObject();
            if (pStats.has("life")) {
                int life = pStats.get("life").getAsInt();
                lifeTotals.put(pid, life);
                LOG.debug("ReplayGameStateBuilder: {} life from per_turn_summary = {}", pid, life);
            }
        }
    }

    private List<String> generateStateLines(int targetTurn) {
        List<String> lines = new ArrayList<>();

        // Collect cards by player and zone
        Map<Integer, Map<String, List<String>>> playerZones = new LinkedHashMap<>();
        for (int i = 0; i < playerIds.size(); i++) {
            playerZones.put(i, new LinkedHashMap<>());
        }

        int trackedTotal = 0;
        for (TrackedCard tc : cards.values()) {
            if (tc.zone == null) continue;

            // Parse zone "P1:hand" -> player="P1", zone="hand"
            String zonePid;
            String zoneType;
            if (tc.zone.contains(":")) {
                zonePid = tc.zone.substring(0, tc.zone.indexOf(':'));
                zoneType = tc.zone.substring(tc.zone.indexOf(':') + 1);
            } else {
                // Bare zone name like "battlefield" — use owner
                zonePid = tc.owner;
                zoneType = tc.zone;
            }

            // Skip stack/removed zones
            if ("stack".equals(zoneType)) continue;

            // Skip system/virtual cards that aren't real MTG cards
            if (isSystemCard(tc.cardName)) continue;

            int playerIdx = playerIds.indexOf(zonePid);
            if (playerIdx < 0) continue;

            playerZones.get(playerIdx)
                    .computeIfAbsent(zoneType, k -> new ArrayList<>())
                    .add(tc.cardName);
            trackedTotal++;
        }

        // Turn + phase
        lines.add("Turn=" + targetTurn);
        lines.add("RemoveSummoningSickness=true");

        // Active player
        int activeIdx = playerIds.indexOf(activePlayerId);
        if (activeIdx >= 0) {
            lines.add("ActivePlayer=p" + activeIdx);
            lines.add("ActivePhase=MAIN1");
        }

        // Per-player state
        for (int i = 0; i < playerIds.size(); i++) {
            String prefix = "p" + i;
            String pid = playerIds.get(i);

            // Life
            int life = lifeTotals.getOrDefault(pid, 20);
            lines.add(prefix + "Life=" + life);

            // Zones
            Map<String, List<String>> zones = playerZones.get(i);
            addZoneLine(lines, prefix, "Hand", zones.getOrDefault("hand", new ArrayList<>()));
            addZoneLine(lines, prefix, "Library", zones.getOrDefault("library", new ArrayList<>()));
            addZoneLine(lines, prefix, "Battlefield", zones.getOrDefault("battlefield", new ArrayList<>()));
            addZoneLine(lines, prefix, "Graveyard", zones.getOrDefault("graveyard", new ArrayList<>()));
            addZoneLine(lines, prefix, "Exile", zones.getOrDefault("exile", new ArrayList<>()));
            addZoneLine(lines, prefix, "Command", zones.getOrDefault("command", new ArrayList<>()));
        }

        LOG.info("ReplayGameStateBuilder: generated {} lines for turn {} ({} cards tracked)",
                lines.size(), targetTurn, trackedTotal);
        for (String line : lines) {
            LOG.info("  [GameState] {}", line);
        }

        return lines;
    }

    /**
     * Normalize a zone string. If it has no player prefix (e.g. "battlefield"),
     * add the owner prefix (e.g. "P1:battlefield").
     */
    private static String normalizeZone(String zone, String owner) {
        if (zone == null) return null;
        if (zone.contains(":")) return zone;
        return owner + ":" + zone;
    }

    /** Extract the player ID from a zone string like "P1:hand". Returns null if none. */
    private static String extractPlayer(String zone) {
        if (zone != null && zone.contains(":")) {
            return zone.substring(0, zone.indexOf(':'));
        }
        return null;
    }

    /**
     * Check if a card name is a system/virtual card that shouldn't be included in GameState.
     * These are created by the game engine and don't exist in the card database.
     */
    private static boolean isSystemCard(String cardName) {
        if (cardName == null) return true;
        return cardName.equals("Commander Effect")
                || cardName.endsWith("'s Companion Effect")
                || cardName.equals("Puzzle Goal")
                || cardName.startsWith("Emblem -");
    }

    private static void addZoneLine(List<String> lines, String prefix, String zone, List<String> cardNames) {
        if (!cardNames.isEmpty()) {
            lines.add(prefix + zone + "=" + String.join(";", cardNames));
        }
    }

    private static String getStr(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive()) return el.getAsString();
        }
        return null;
    }
}





