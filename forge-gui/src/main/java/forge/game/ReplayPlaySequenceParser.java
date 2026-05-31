package forge.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a replay JSON file and extracts the ordered sequence of CAST / ACTIVATE / PLAY_LAND
 * events per player, for use with {@link GameRules#setForcedPlaySequence(Map)}.
 *
 * <p>Player IDs ({@code P1}, {@code P2}, …) are mapped to lobby names via
 * {@code meta.players.<id>.name}.  If a name entry is absent the raw player ID is used as
 * the key so callers can always do a look-up by whatever name they have available.
 *
 * <p>Usage:
 * <pre>
 *   Map&lt;String, List&lt;String&gt;&gt; seq = ReplayPlaySequenceParser.parse(replayFile);
 *   rules.setForcedPlaySequence(seq);
 * </pre>
 *
 * @see ReplayLogParser
 * @see GameRules#setForcedPlaySequence(Map)
 */
public final class ReplayPlaySequenceParser {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayPlaySequenceParser.class);

    private static final Set<String> PLAY_EVENT_TYPES =
            new HashSet<>(Arrays.asList("CAST", "ACTIVATE", "PLAY_LAND"));

    private ReplayPlaySequenceParser() { /* utility class */ }

    /**
     * Parse {@code replayFile} and return a map of {@code lobbyName → ordered card names}.
     *
     * <p>The returned map is mutable so the AI can remove entries as it plays them.
     *
     * @param replayFile the replay JSON file to parse
     * @return map (never null); empty when the file cannot be read or contains no play events
     */
    public static Map<String, List<String>> parse(final File replayFile) {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        if (replayFile == null || !replayFile.exists()) {
            return result;
        }

        try (Reader reader = new FileReader(replayFile)) {
            final JsonElement rootElem = JsonParser.parseReader(reader);
            if (!rootElem.isJsonObject()) {
                LOG.warn("Replay file is not a JSON object: {}", replayFile);
                return result;
            }
            final JsonObject root = rootElem.getAsJsonObject();

            // Build playerId → lobbyName map from meta.players
            final Map<String, String> idToName = buildIdToNameMap(root);

            // Locate the event array: prefer "log_l1", fall back to legacy "events"
            final String eventsKey = root.has("log_l1") ? "log_l1" : "events";
            if (!root.has(eventsKey) || !root.get(eventsKey).isJsonArray()) {
                LOG.warn("No event array ('{}') found in replay file: {}", eventsKey, replayFile);
                return result;
            }

            final JsonArray events = root.getAsJsonArray(eventsKey);
            for (final JsonElement el : events) {
                if (!el.isJsonObject()) {
                    continue;
                }
                final JsonObject ev = el.getAsJsonObject();
                processEvent(ev, idToName, result);
            }

            final int total = result.values().stream().mapToInt(List::size).sum();
            LOG.info("Parsed play sequence: {} player(s), {} total play event(s) from {}",
                    result.size(), total, replayFile.getName());

        } catch (final IOException e) {
            LOG.error("Failed to read replay file for play-sequence extraction: {}", replayFile, e);
        } catch (final Exception e) {
            LOG.error("Unexpected error parsing play sequence from: {}", replayFile, e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static Map<String, String> buildIdToNameMap(final JsonObject root) {
        final Map<String, String> idToName = new LinkedHashMap<>();
        if (!root.has("meta") || !root.get("meta").isJsonObject()) {
            return idToName;
        }
        final JsonObject meta = root.getAsJsonObject("meta");
        if (!meta.has("players") || !meta.get("players").isJsonObject()) {
            return idToName;
        }
        for (final Map.Entry<String, JsonElement> entry : meta.getAsJsonObject("players").entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            final JsonObject pObj = entry.getValue().getAsJsonObject();
            final String name = pObj.has("name") && !pObj.get("name").isJsonNull()
                    ? pObj.get("name").getAsString()
                    : entry.getKey(); // fall back to player ID
            idToName.put(entry.getKey(), name);
        }
        return idToName;
    }

    private static void processEvent(final JsonObject ev,
                                     final Map<String, String> idToName,
                                     final Map<String, List<String>> result) {
        final String type = getString(ev, "type");
        if (type == null || !PLAY_EVENT_TYPES.contains(type)) {
            return;
        }

        final String actor = getString(ev, "a");
        if (actor == null) {
            return;
        }

        final String lobbyName = idToName.getOrDefault(actor, actor);

        // Resolve card name: prefer explicit "card_name", fall back to object-id "card"
        final String cardName = resolveCardName(ev);
        if (cardName == null) {
            return;
        }

        result.computeIfAbsent(lobbyName, k -> new ArrayList<>()).add(cardName);
    }

    private static String resolveCardName(final JsonObject ev) {
        if (!ev.has("data") || !ev.get("data").isJsonObject()) {
            return null;
        }
        final JsonObject data = ev.getAsJsonObject("data");
        if (data.has("card_name") && !data.get("card_name").isJsonNull()) {
            return data.get("card_name").getAsString();
        }
        if (data.has("card") && !data.get("card").isJsonNull()) {
            // Object-ID fallback (e.g. "c17") — usable as a best-effort key
            return data.get("card").getAsString();
        }
        return null;
    }

    private static String getString(final JsonObject obj, final String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}

