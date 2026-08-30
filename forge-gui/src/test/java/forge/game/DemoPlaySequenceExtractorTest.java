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

    @Test
    public void testExtractPlayerEvents_ignoresTriggersAndOpponentEvents() throws IOException {
        File dir = Files.createTempDirectory("demoplay-test").toFile();
        File replayFile = new File(dir, "recording-triggers.json");
        String raw = "{"
                + "\"card_index\": {"
                + "  \"c1\": {\"name\": \"Command Tower\"},"
                + "  \"c2\": {\"name\": \"Gyre Sage\"},"
                + "  \"c3\": {\"name\": \"Walking Bulwark\"},"
                + "  \"c4\": {\"name\": \"Lightning Bolt\"}"
                + "},"
                + "\"events\": ["
                + "  {\"i\": 1, \"t\": \"T1.MP1:1\", \"a\": \"P1\", \"type\": \"PLAY_LAND\", \"data\": {\"card\": \"c1\", \"card_name\": \"Command Tower\"}},"
                + "  {\"i\": 2, \"t\": \"T1.MP1:2\", \"a\": \"P1\", \"type\": \"TRIGGER\", \"data\": {\"card\": \"c2\", \"card_name\": \"Gyre Sage\"}},"
                + "  {\"i\": 3, \"t\": \"T1.MP1:3\", \"a\": \"SYS\", \"type\": \"MOVE\", \"data\": {\"card\": \"c1\"}},"
                + "  {\"i\": 4, \"t\": \"T1.MP1:4\", \"a\": \"P2\", \"type\": \"CAST\", \"data\": {\"card\": \"c4\", \"card_name\": \"Lightning Bolt\"}},"
                + "  {\"i\": 5, \"t\": \"T1.MP1:5\", \"a\": \"P1\", \"type\": \"ACTIVATE\", \"data\": {\"card\": \"c3\", \"card_name\": \"Walking Bulwark\"}}"
                + "]"
                + "}";
        try (FileWriter fw = new FileWriter(replayFile)) {
            fw.write(raw);
        }

        JsonArray events = DemoPlaySequenceExtractor.extractPlayerEvents(replayFile, "P1");

        // Should only extract P1's PLAY_LAND (Command Tower) and ACTIVATE (Walking Bulwark)
        // TRIGGER on Gyre Sage, MOVE on SYS, and CAST on P2 should all be ignored.
        assertEquals(2, events.size());
        assertEquals("PLAY_LAND", events.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("Command Tower", events.get(0).getAsJsonObject().getAsJsonObject("data").get("card_name").getAsString());
        assertEquals("ACTIVATE", events.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("Walking Bulwark", events.get(1).getAsJsonObject().getAsJsonObject("data").get("card_name").getAsString());
    }

    @Test
    public void testParseTeams() throws IOException {
        File tempDir = Files.createTempDirectory("replay-team-test").toFile();
        File jsonFile = new File(tempDir, "replay_team.json");
        String content = "{\n" +
                "  \"format\": \"mtg-replay\",\n" +
                "  \"version\": \"1.9.0\",\n" +
                "  \"meta\": {\n" +
                "    \"game_id\": \"game-123\",\n" +
                "    \"timestamp\": \"2026-08-24T18:19:31Z\",\n" +
                "    \"game_type\": \"Constructed\",\n" +
                "    \"players\": {\n" +
                "      \"P1\": {\"name\": \"Eli\", \"team\": 1, \"is_ai\": false, \"player_type\": \"Human\", \"starting_life\": 40},\n" +
                "      \"P2\": {\"name\": \"Erikarn\", \"team\": 2, \"is_ai\": true, \"player_type\": \"AI\", \"starting_life\": 40},\n" +
                "      \"P3\": {\"name\": \"Ryodan\", \"team\": 2, \"is_ai\": true, \"player_type\": \"AI\", \"starting_life\": 40}\n" +
                "    },\n" +
                "    \"winner\": \"P1\",\n" +
                "    \"turns\": 24\n" +
                "  }\n" +
                "}";

        try (FileWriter fw = new FileWriter(jsonFile)) {
            fw.write(content);
        }

        ReplayLogParser parser = new ReplayLogParser(jsonFile);
        assertTrue(parser.parse());
        assertTrue(parser.isTeamGame());

        ReplayLogParser.PlayerInfo p1 = parser.getPlayers().get("P1");
        assertNotNull(p1);
        assertEquals(Integer.valueOf(1), p1.team);
        assertEquals(0, p1.getForgeTeam());

        ReplayLogParser.PlayerInfo p2 = parser.getPlayers().get("P2");
        assertNotNull(p2);
        assertEquals(Integer.valueOf(2), p2.team);
        assertEquals(1, p2.getForgeTeam());

        ReplayLogParser.PlayerInfo p3 = parser.getPlayers().get("P3");
        assertNotNull(p3);
        assertEquals(Integer.valueOf(2), p3.team);
        assertEquals(1, p3.getForgeTeam());
    }

    @Test
    public void testNonTeamGame() throws IOException {
        File tempDir = Files.createTempDirectory("replay-ffa-test").toFile();
        File jsonFile = new File(tempDir, "replay_ffa.json");
        String content = "{\n" +
                "  \"format\": \"mtg-replay\",\n" +
                "  \"version\": \"1.9.0\",\n" +
                "  \"meta\": {\n" +
                "    \"game_id\": \"game-456\",\n" +
                "    \"timestamp\": \"2026-08-24T18:19:31Z\",\n" +
                "    \"game_type\": \"Constructed\",\n" +
                "    \"players\": {\n" +
                "      \"P1\": {\"name\": \"Alice\", \"is_ai\": false, \"player_type\": \"Human\", \"starting_life\": 20},\n" +
                "      \"P2\": {\"name\": \"Bob\", \"is_ai\": true, \"player_type\": \"AI\", \"starting_life\": 20}\n" +
                "    },\n" +
                "    \"winner\": \"P1\",\n" +
                "    \"turns\": 10\n" +
                "  }\n" +
                "}";

        try (FileWriter fw = new FileWriter(jsonFile)) {
            fw.write(content);
        }

        ReplayLogParser parser = new ReplayLogParser(jsonFile);
        assertTrue(parser.parse());
        assertFalse(parser.isTeamGame());

        ReplayLogParser.PlayerInfo p1 = parser.getPlayers().get("P1");
        assertNotNull(p1);
        assertNull(p1.team);
        assertEquals(-1, p1.getForgeTeam());
    }

    @Test
    public void testReplayOutcomeFields() throws IOException {
        File tempDir = Files.createTempDirectory("replay-outcome-test").toFile();
        File jsonFile = new File(tempDir, "replay_outcome.json");
        String content = "{\n" +
                "  \"format\": \"mtg-replay\",\n" +
                "  \"version\": \"1.9.0\",\n" +
                "  \"meta\": {\n" +
                "    \"game_id\": \"game-789\",\n" +
                "    \"timestamp\": \"2026-08-24T18:19:31Z\",\n" +
                "    \"game_type\": \"Constructed\",\n" +
                "    \"players\": {\n" +
                "      \"P1\": {\"name\": \"Alice\", \"is_ai\": false, \"player_type\": \"Human\", \"starting_life\": 20},\n" +
                "      \"P2\": {\"name\": \"Bob\", \"is_ai\": true, \"player_type\": \"AI\", \"starting_life\": 20}\n" +
                "    },\n" +
                "    \"winner\": \"P2\",\n" +
                "    \"turns\": 24,\n" +
                "    \"replayed_at\": \"2026-08-30T14:15:00Z\",\n" +
                "    \"replayed_winner\": \"P1\",\n" +
                "    \"replayed_outcome\": \"win\",\n" +
                "    \"replayed_turns\": 18\n" +
                "  }\n" +
                "}";

        try (FileWriter fw = new FileWriter(jsonFile)) {
            fw.write(content);
        }

        ReplayLogParser parser = new ReplayLogParser(jsonFile);
        assertTrue(parser.parse());
        assertTrue(parser.isReplayed());
        assertTrue(parser.isOriginalLoss());
        assertTrue(parser.isReplayWon());
        assertEquals("2026-08-30T14:15:00Z", parser.getReplayedAt());
        assertEquals("P1", parser.getReplayedWinner());
        assertEquals("win", parser.getReplayedOutcome());
        assertEquals(Integer.valueOf(18), parser.getReplayedTurns());
        assertEquals(Integer.valueOf(24), parser.getTurns());
    }

    @Test
    public void testRecordReplayResult() throws IOException {
        File tempDir = Files.createTempDirectory("replay-record-test").toFile();
        File jsonFile = new File(tempDir, "replay_record.json");
        String content = "{\n" +
                "  \"format\": \"mtg-replay\",\n" +
                "  \"version\": \"1.9.0\",\n" +
                "  \"meta\": {\n" +
                "    \"game_id\": \"game-999\",\n" +
                "    \"timestamp\": \"2026-08-24T18:19:31Z\",\n" +
                "    \"game_type\": \"Constructed\",\n" +
                "    \"players\": {\n" +
                "      \"P1\": {\"name\": \"Alice\", \"is_ai\": false, \"player_type\": \"Human\", \"starting_life\": 20},\n" +
                "      \"P2\": {\"name\": \"Bob\", \"is_ai\": true, \"player_type\": \"AI\", \"starting_life\": 20}\n" +
                "    },\n" +
                "    \"winner\": \"P2\",\n" +
                "    \"turns\": 20\n" +
                "  }\n" +
                "}";

        try (FileWriter fw = new FileWriter(jsonFile)) {
            fw.write(content);
        }

        ReplayLogParser parser = new ReplayLogParser(jsonFile);
        assertTrue(parser.parse());
        assertFalse(parser.isReplayed());
        assertTrue(parser.isOriginalLoss());

        // Record a replay outcome where player survived 28 turns but still lost
        parser.recordReplayResult("P2", "loss", 28);
        assertTrue(parser.isReplayed());
        assertFalse(parser.isReplayWon());
        assertEquals("loss", parser.getReplayedOutcome());
        assertEquals("P2", parser.getReplayedWinner());
        assertEquals(Integer.valueOf(28), parser.getReplayedTurns());

        // Reload fresh from file to verify persistence
        ReplayLogParser reloaded = new ReplayLogParser(jsonFile);
        assertTrue(reloaded.parse());
        assertTrue(reloaded.isReplayed());
        assertEquals("loss", reloaded.getReplayedOutcome());
        assertEquals("P2", reloaded.getReplayedWinner());
        assertEquals(Integer.valueOf(28), reloaded.getReplayedTurns());
    }
}
