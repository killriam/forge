package forge.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.testng.AssertJUnit.*;

/**
 * Covers {@link DemoPlaySequenceExtractor#updateScenarioEvents}, used when the user confirms
 * (via the "Demo Play Complete" dialog in {@code CSubmenuScenario}) that a recorded demo-play
 * line should be written directly into the scenario file's "events" field.
 */
public class DemoPlaySequenceExtractorTest {

    private static File writeScenario(File dir, String contents) throws IOException {
        File f = new File(dir, "scenario-under-test.json");
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(contents);
        }
        return f;
    }

    private static JsonArray oneEvent() {
        JsonArray events = new JsonArray();
        JsonObject e = new JsonObject();
        e.addProperty("i", 1);
        e.addProperty("t", "T1.MP1:1");
        e.addProperty("a", "P1");
        e.addProperty("type", "PLAY_LAND");
        JsonObject data = new JsonObject();
        data.addProperty("card_name", "Command Tower");
        e.add("data", data);
        events.add(e);
        return events;
    }

    @Test
    public void testUpdateScenarioEvents_addsEventsAndPreservesOtherFields() throws IOException {
        File dir = Files.createTempDirectory("demoplay-test").toFile();
        File scenarioFile = writeScenario(dir,
                "{\"format\":\"mtg-replay\",\"mode\":\"scenario\","
                        + "\"scenario\":{\"id\":\"abc\",\"name\":\"Test\",\"type\":\"opening_hand_test\"}}");

        DemoPlaySequenceExtractor.updateScenarioEvents(scenarioFile, oneEvent());

        JsonObject root = JsonParser.parseReader(new java.io.FileReader(scenarioFile)).getAsJsonObject();
        assertTrue("events field should be present", root.has("events"));
        assertEquals(1, root.getAsJsonArray("events").size());
        assertEquals("Command Tower",
                root.getAsJsonArray("events").get(0).getAsJsonObject()
                        .getAsJsonObject("data").get("card_name").getAsString());
        // Untouched fields must survive the round-trip
        assertEquals("abc", root.getAsJsonObject("scenario").get("id").getAsString());
        assertEquals("Test", root.getAsJsonObject("scenario").get("name").getAsString());
        assertEquals("mtg-replay", root.get("format").getAsString());
    }

    @Test
    public void testUpdateScenarioEvents_overwritesExistingEvents() throws IOException {
        File dir = Files.createTempDirectory("demoplay-test").toFile();
        File scenarioFile = writeScenario(dir,
                "{\"mode\":\"scenario\",\"scenario\":{\"id\":\"abc\"},"
                        + "\"events\":[{\"i\":1,\"a\":\"P1\",\"type\":\"CAST\",\"data\":{\"card_name\":\"Old Card\"}}]}");

        DemoPlaySequenceExtractor.updateScenarioEvents(scenarioFile, oneEvent());

        JsonObject root = JsonParser.parseReader(new java.io.FileReader(scenarioFile)).getAsJsonObject();
        assertEquals(1, root.getAsJsonArray("events").size());
        assertEquals("Command Tower",
                root.getAsJsonArray("events").get(0).getAsJsonObject()
                        .getAsJsonObject("data").get("card_name").getAsString());
    }

    @Test
    public void testUpdateScenarioEvents_writesBackupBeforeOverwriting() throws IOException {
        File dir = Files.createTempDirectory("demoplay-test").toFile();
        String original = "{\"mode\":\"scenario\",\"scenario\":{\"id\":\"abc\"}}";
        File scenarioFile = writeScenario(dir, original);

        DemoPlaySequenceExtractor.updateScenarioEvents(scenarioFile, oneEvent());

        File backup = new File(dir, "scenario-under-test.json.bak");
        assertTrue("backup file should have been created", backup.exists());
        String backupContents = new String(Files.readAllBytes(backup.toPath()));
        assertEquals("backup should contain the pre-update contents", original, backupContents);
    }

    /**
     * Mirrors the exact shape {@link forge.game.log.ReplayEventLogger} now produces for a
     * Metamorphosis-style cast (sacrifice a creature as an additional cost, X mana chosen) - the
     * concrete case that motivated capturing cost/x/choices on CAST events at all. Verifies
     * {@link DemoPlaySequenceExtractor#extractPlayerEvents} resolves both the target and the
     * sacrificed card from card_index into names, and passes the X value through.
     */
    @Test
    public void testExtractPlayerEvents_resolvesTargetsXAndSacrificeFromRealLogShape() throws IOException {
        File dir = Files.createTempDirectory("demoplay-test").toFile();
        File replayFile = new File(dir, "recording.json");
        String raw = "{"
                + "\"card_index\": {"
                + "  \"c10\": {\"name\": \"Metamorphosis\"},"
                + "  \"c5\": {\"name\": \"The Pride of Hull Clade\"},"
                + "  \"c11\": {\"name\": \"Arbor Adherent\"}"
                + "},"
                + "\"events\": ["
                + "  {\"i\": 59, \"t\": \"T3.MP1:1\", \"a\": \"P1\", \"type\": \"CAST\", \"data\": {"
                + "      \"card\": \"c10\", \"card_name\": \"Metamorphosis\","
                + "      \"targets\": [\"c11\"],"
                + "      \"cost\": {\"mana\": \"0\", \"additional\": [\"X=4\"], \"alternative\": null},"
                + "      \"x\": 4,"
                + "      \"choices\": {\"sacrifice\": [\"c5\"]}"
                + "  }}"
                + "]"
                + "}";
        try (FileWriter fw = new FileWriter(replayFile)) {
            fw.write(raw);
        }

        JsonArray events = DemoPlaySequenceExtractor.extractPlayerEvents(replayFile, "P1");

        assertEquals(1, events.size());
        JsonObject data = events.get(0).getAsJsonObject().getAsJsonObject("data");
        assertEquals("Metamorphosis", data.get("card_name").getAsString());
        assertEquals(4, data.get("x").getAsInt());
        assertEquals("Arbor Adherent", data.getAsJsonArray("targets").get(0).getAsString());
        assertEquals("The Pride of Hull Clade", data.getAsJsonArray("sacrifice").get(0).getAsString());
    }
}
