package forge.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

/**
 * "Demo Play" conversion step: reads a replay JSON produced by {@link
 * forge.game.log.ReplayEventLogger} for one player (typically the human seat in a demo-play
 * scenario launch, "P1") and re-numbers their CAST/ACTIVATE/PLAY_LAND events into a ready-to-paste
 * scenario {@code events[]} array (see {@code docs/SCENARIO_STARTING_HAND_FORMAT.md}).
 *
 * <p>Demo play applies a scenario's forced draw order to a seat but deliberately does NOT apply
 * its forced play-sequence, so a human can play the guaranteed hand out for real and discover a
 * good line - this class turns that discovered line into the {@code events[]} data to encode
 * back into the scenario file, closing the authoring loop.</p>
 *
 * @see ReplayPlaySequenceParser the analogous extractor for the "-r" full-game-replay path
 *      (produces a flat {@code lobbyName -> card names} map instead of {@code events[]} JSON,
 *      and covers every player instead of one)
 */
public final class DemoPlaySequenceExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(DemoPlaySequenceExtractor.class);

    private static final Set<String> PLAY_EVENT_TYPES =
            new HashSet<>(java.util.Arrays.asList("CAST", "ACTIVATE", "PLAY_LAND"));

    private DemoPlaySequenceExtractor() { /* utility class */ }

    /**
     * Extracts {@code playerId}'s (e.g. {@code "P1"}) CAST/ACTIVATE/PLAY_LAND events from
     * {@code replayFile}'s {@code log_l1} array (falls back to legacy {@code events}), re-indexed
     * from 1, in the exact shape scenario JSON's {@code events[]} field expects.
     *
     * @return a JsonArray (never null; empty when the file is missing/unreadable or the player
     *         has no matching events)
     */
    public static JsonArray extractPlayerEvents(final File replayFile, final String playerId) {
        final JsonArray result = new JsonArray();
        if (replayFile == null || !replayFile.exists() || playerId == null) {
            return result;
        }

        try (Reader reader = new FileReader(replayFile)) {
            final JsonElement rootElem = JsonParser.parseReader(reader);
            if (!rootElem.isJsonObject()) {
                return result;
            }
            final JsonObject root = rootElem.getAsJsonObject();
            final String eventsKey = root.has("log_l1") ? "log_l1" : "events";
            if (!root.has(eventsKey) || !root.get(eventsKey).isJsonArray()) {
                return result;
            }
            final java.util.Map<String, String> cardNamesById = buildCardIndex(root);

            int index = 1;
            for (final JsonElement el : root.getAsJsonArray(eventsKey)) {
                if (!el.isJsonObject()) continue;
                final JsonObject ev = el.getAsJsonObject();

                final String type = getString(ev, "type");
                final String actor = getString(ev, "a");
                if (type == null || actor == null) continue;
                if (!PLAY_EVENT_TYPES.contains(type) || !playerId.equals(actor)) continue;

                final String cardName = resolveCardName(ev);
                if (cardName == null) continue;
                // Raw log nests everything under "data" (ev.data.targets, ev.data.x, ...) -
                // resolveCardName() already knows this; the fields below need the same object.
                final JsonObject rawData = ev.getAsJsonObject("data");

                final JsonObject out = new JsonObject();
                out.addProperty("i", index++);
                final String t = getString(ev, "t");
                out.addProperty("t", t != null ? t : "");
                out.addProperty("a", actor);
                out.addProperty("type", type);
                final JsonObject data = new JsonObject();
                data.addProperty("card_name", cardName);
                // Targets, X value, and choices made to pay additional costs (e.g. Metamorphosis'
                // "sacrifice a creature") are recorded by card id in the raw log - resolve them to
                // names here so the events[] snippet is self-contained and human-authorable
                // without cross-referencing the recording. Not yet consumed on replay (see
                // docs/SCENARIO_STARTING_HAND_FORMAT.md, "Phase 2") - recorded for now so a
                // scenario author can encode them by hand.
                addResolvedNames(data, "targets", rawData.get("targets"), cardNamesById);
                if (rawData.has("x") && !rawData.get("x").isJsonNull()) {
                    data.add("x", rawData.get("x"));
                }
                if (rawData.has("choices") && rawData.get("choices").isJsonObject()) {
                    final JsonObject choices = rawData.getAsJsonObject("choices");
                    addResolvedNames(data, "sacrifice", choices.get("sacrifice"), cardNamesById);
                }
                out.add("data", data);
                result.add(out);
            }
        } catch (final IOException e) {
            LOG.error("Failed to read replay file for demo-play extraction: {}", replayFile, e);
        } catch (final RuntimeException e) {
            LOG.error("Unexpected error extracting demo-play events from: {}", replayFile, e);
        }
        return result;
    }

    /**
     * Extracts {@code playerId}'s events (see {@link #extractPlayerEvents}) and writes them,
     * pretty-printed, to {@code outFile} - ready to open and paste into a scenario JSON's
     * top-level {@code "events"} field.
     */
    public static void writeSnippet(final File replayFile, final String playerId, final File outFile) throws IOException {
        final JsonArray events = extractPlayerEvents(replayFile, playerId);
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (FileWriter fw = new FileWriter(outFile)) {
            gson.toJson(events, fw);
        }
        LOG.info("Demo-play events snippet ({} event(s)) written to {}", events.size(), outFile);
    }

    /**
     * Overwrites {@code scenarioFile}'s top-level {@code "events"} field with {@code events},
     * preserving everything else in the file - used when the user confirms they want their
     * demo-play line encoded directly into the scenario instead of manually pasting the snippet
     * from {@link #writeSnippet}. Copies the file to {@code <name>.bak} first (overwriting any
     * previous backup) so an unwanted update is trivially reversible.
     */
    public static void updateScenarioEvents(final File scenarioFile, final JsonArray events) throws IOException {
        final JsonObject root;
        try (Reader reader = new FileReader(scenarioFile)) {
            final JsonElement rootElem = JsonParser.parseReader(reader);
            if (!rootElem.isJsonObject()) {
                throw new IOException("Scenario file is not a JSON object: " + scenarioFile);
            }
            root = rootElem.getAsJsonObject();
        }

        final File backup = new File(scenarioFile.getParentFile(), scenarioFile.getName() + ".bak");
        java.nio.file.Files.copy(scenarioFile.toPath(), backup.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        root.add("events", events);
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter fw = new FileWriter(scenarioFile)) {
            gson.toJson(root, fw);
        }
        LOG.info("Scenario file updated with {} event(s) (backup at {}): {}", events.size(), backup, scenarioFile);
    }

    /** Builds an id ("c3", "t1", a player id like "P2") -> name lookup from the replay's
     *  top-level card_index. Player ids are left to resolve as themselves (targets can be
     *  players, e.g. a burn spell aimed at an opponent). */
    private static java.util.Map<String, String> buildCardIndex(final JsonObject root) {
        final java.util.Map<String, String> byId = new java.util.HashMap<>();
        if (root.has("card_index") && root.get("card_index").isJsonObject()) {
            for (final java.util.Map.Entry<String, JsonElement> e : root.getAsJsonObject("card_index").entrySet()) {
                if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("name")) {
                    byId.put(e.getKey(), e.getValue().getAsJsonObject().get("name").getAsString());
                }
            }
        }
        return byId;
    }

    /** If {@code idsElement} is a non-empty JSON array of ids, resolves each to a name (falling
     *  back to the raw id, e.g. a player id, when it's not in the card index) and adds the
     *  result to {@code data} under {@code key}. No-op if the array is absent or empty. */
    private static void addResolvedNames(final JsonObject data, final String key,
            final JsonElement idsElement, final java.util.Map<String, String> cardNamesById) {
        if (idsElement == null || !idsElement.isJsonArray() || idsElement.getAsJsonArray().size() == 0) {
            return;
        }
        final JsonArray names = new JsonArray();
        for (final JsonElement idEl : idsElement.getAsJsonArray()) {
            final String id = idEl.getAsString();
            names.add(cardNamesById.getOrDefault(id, id));
        }
        data.add(key, names);
    }

    private static String resolveCardName(final JsonObject ev) {
        if (!ev.has("data") || !ev.get("data").isJsonObject()) return null;
        final JsonObject data = ev.getAsJsonObject("data");
        if (data.has("card_name") && !data.get("card_name").isJsonNull()) {
            return data.get("card_name").getAsString();
        }
        if (data.has("card") && !data.get("card").isJsonNull()) {
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
