package forge.game.scenario;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import forge.game.ReplayLogParser;
import forge.game.ReplayLogParser.ScenarioInfo;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.*;

/**
 * Tests for the forced-play-sequence ({@code events} array) parsing and player-id-to-
 * lobby-name translation shared by {@code CSubmenuScenario} (GUI Scenario Viewer) and
 * {@code SimulateMatch}'s {@code -s} CLI scenario flag.
 *
 * <p>Pure data-level tests — no FModel/game engine required. For full-game integration
 * coverage of scenario starting hands, see {@link ScenarioStartingHandTest}.</p>
 */
public class ScenarioForcedPlaySequenceTest {

    private static JsonArray events(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    // -------------------------------------------------------------------------
    // ReplayLogParser.parseForcedSequenceEvents
    // -------------------------------------------------------------------------

    @Test
    public void testParseForcedSequenceEvents_ordersCardsPerActor() {
        JsonArray arr = events("["
                + "{\"i\":1,\"t\":\"T1.MP1:1\",\"a\":\"P1\",\"type\":\"PLAY_LAND\",\"data\":{\"card_name\":\"Command Tower\"}},"
                + "{\"i\":2,\"t\":\"T2.MP1:1\",\"a\":\"P1\",\"type\":\"CAST\",\"data\":{\"card_name\":\"Energy Tap\"}}"
                + "]");

        Map<String, List<String>> result = ReplayLogParser.parseForcedSequenceEvents(arr);

        assertEquals("Only P1 should appear", 1, result.size());
        assertEquals("P1's sequence should be in event order",
                List.of("Command Tower", "Energy Tap"), result.get("P1"));
    }

    @Test
    public void testParseForcedSequenceEvents_keepsSeparatePlayersSeparate() {
        JsonArray arr = events("["
                + "{\"i\":1,\"a\":\"P1\",\"type\":\"PLAY_LAND\",\"data\":{\"card_name\":\"Forest\"}},"
                + "{\"i\":2,\"a\":\"P2\",\"type\":\"PLAY_LAND\",\"data\":{\"card_name\":\"Mountain\"}}"
                + "]");

        Map<String, List<String>> result = ReplayLogParser.parseForcedSequenceEvents(arr);

        assertEquals(List.of("Forest"), result.get("P1"));
        assertEquals(List.of("Mountain"), result.get("P2"));
    }

    @Test
    public void testParseForcedSequenceEvents_ignoresNonPlayEventTypes() {
        JsonArray arr = events("["
                + "{\"i\":1,\"a\":\"P1\",\"type\":\"DRAW\",\"data\":{\"card_name\":\"Sol Ring\"}},"
                + "{\"i\":2,\"a\":\"P1\",\"type\":\"CAST\",\"data\":{\"card_name\":\"Sol Ring\"}}"
                + "]");

        Map<String, List<String>> result = ReplayLogParser.parseForcedSequenceEvents(arr);

        assertEquals("Only the CAST event should be captured", List.of("Sol Ring"), result.get("P1"));
    }

    @Test
    public void testParseForcedSequenceEvents_skipsEventsMissingCardName() {
        JsonArray arr = events("[{\"i\":1,\"a\":\"P1\",\"type\":\"CAST\",\"data\":{}}]");

        Map<String, List<String>> result = ReplayLogParser.parseForcedSequenceEvents(arr);

        assertTrue("Event with no resolvable card name should be dropped", result.isEmpty());
    }

    @Test
    public void testParseForcedSequenceEvents_skipsEventsMissingActor() {
        JsonArray arr = events("[{\"i\":1,\"type\":\"CAST\",\"data\":{\"card_name\":\"Sol Ring\"}}]");

        Map<String, List<String>> result = ReplayLogParser.parseForcedSequenceEvents(arr);

        assertTrue("Event with no actor should be dropped", result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // ScenarioInfo.buildForcedPlaySequenceForLobbyNames
    // -------------------------------------------------------------------------

    @Test
    public void testBuildForcedPlaySequenceForLobbyNames_translatesKnownIds() {
        ScenarioInfo si = new ScenarioInfo();
        si.playerForcedSequence.put("P1", List.of("Command Tower", "Energy Tap"));
        si.playerForcedSequence.put("P2", List.of("Lightning Bolt"));

        Map<String, String> idToLobbyName = new LinkedHashMap<>();
        idToLobbyName.put("P1", "killriam");
        idToLobbyName.put("P2", "AI 1");

        Map<String, List<String>> result = si.buildForcedPlaySequenceForLobbyNames(idToLobbyName);

        assertEquals(List.of("Command Tower", "Energy Tap"), result.get("killriam"));
        assertEquals(List.of("Lightning Bolt"), result.get("AI 1"));
    }

    @Test
    public void testBuildForcedPlaySequenceForLobbyNames_dropsUnmatchedIds() {
        ScenarioInfo si = new ScenarioInfo();
        si.playerForcedSequence.put("P1", List.of("Command Tower"));
        si.playerForcedSequence.put("P3", List.of("Lightning Bolt")); // no P3 seat this run

        Map<String, String> idToLobbyName = new LinkedHashMap<>();
        idToLobbyName.put("P1", "killriam");
        idToLobbyName.put("P2", "AI 1");

        Map<String, List<String>> result = si.buildForcedPlaySequenceForLobbyNames(idToLobbyName);

        assertEquals("Only the matched seat should be present", 1, result.size());
        assertTrue(result.containsKey("killriam"));
    }

    @Test
    public void testScenarioInfo_hasForcedPlaySequence() {
        ScenarioInfo si = new ScenarioInfo();
        assertFalse(si.hasForcedPlaySequence());
        si.playerForcedSequence.put("P1", List.of("Sol Ring"));
        assertTrue(si.hasForcedPlaySequence());
    }

    // -------------------------------------------------------------------------
    // ReplayLogParser.resolveScenarioByIdOrFilename(String, List<ReplayLogParser>)
    //
    // Exercised against an in-memory candidate list (built from real temp scenario files, so
    // parse()/ScenarioInfo wiring is genuinely tested) rather than the real GAME_LOG_DIR - the
    // public no-arg overload just forwards to listScenarioFiles() and isn't independently
    // testable without either touching the user's real game-log directory or refactoring the
    // directory scan itself, neither of which this fix needs.
    // -------------------------------------------------------------------------

    private static File tempScenarioFile(String fileNameNoExt, String scenarioJsonBody) throws IOException {
        File f = File.createTempFile(fileNameNoExt + "-", ".json");
        f.deleteOnExit();
        try (FileWriter w = new FileWriter(f)) {
            w.write("{\"format\":\"mtg-replay\",\"mode\":\"scenario\",\"scenario\":" + scenarioJsonBody + "}");
        }
        return f;
    }

    private static ReplayLogParser parsedScenario(String fileNameNoExt, String idOrNull) throws IOException {
        String body = idOrNull == null ? "{}" : "{\"id\":\"" + idOrNull + "\"}";
        File f = tempScenarioFile(fileNameNoExt, body);
        ReplayLogParser parser = new ReplayLogParser(f);
        assertTrue("Fixture scenario file should parse successfully", parser.parse());
        assertTrue("Fixture file should be recognized as a scenario", parser.isScenario());
        return parser;
    }

    @Test
    public void testResolveScenarioByIdOrFilename_matchesById() throws IOException {
        List<ReplayLogParser> candidates = new ArrayList<>();
        candidates.add(parsedScenario("scenario-a", "perfect-draw-1"));
        candidates.add(parsedScenario("scenario-b", "best-hand-2"));

        ReplayLogParser result = ReplayLogParser.resolveScenarioByIdOrFilename("best-hand-2", candidates);

        assertNotNull(result);
        assertEquals("best-hand-2", result.getScenarioInfo().id);
    }

    @Test
    public void testResolveScenarioByIdOrFilename_fallsBackToFilenameWhenNoIdMatches() throws IOException {
        ReplayLogParser onlyCandidate = parsedScenario("my-scenario-file", null); // no scenario.id set
        List<ReplayLogParser> candidates = List.of(onlyCandidate);
        String fileNameNoExt = onlyCandidate.getReplayFile().getName().replace(".json", "");

        ReplayLogParser result = ReplayLogParser.resolveScenarioByIdOrFilename(fileNameNoExt, candidates);

        assertNotNull(result);
        assertSame(onlyCandidate, result);
    }

    @Test
    public void testResolveScenarioByIdOrFilename_fallsBackToFilenameWithJsonExtension() throws IOException {
        ReplayLogParser onlyCandidate = parsedScenario("my-scenario-file2", null);
        List<ReplayLogParser> candidates = List.of(onlyCandidate);
        String fullFileName = onlyCandidate.getReplayFile().getName(); // includes ".json"

        ReplayLogParser result = ReplayLogParser.resolveScenarioByIdOrFilename(fullFileName, candidates);

        assertSame(onlyCandidate, result);
    }

    @Test
    public void testResolveScenarioByIdOrFilename_idTakesPrecedenceOverFilename() throws IOException {
        // A file whose id happens to equal another file's filename must still resolve by id
        // first - id match is checked in a full first pass before any filename fallback pass.
        ReplayLogParser byId = parsedScenario("unrelated-name", "shared-token");
        ReplayLogParser byName = parsedScenario("shared-token", null);
        List<ReplayLogParser> candidates = List.of(byId, byName);

        ReplayLogParser result = ReplayLogParser.resolveScenarioByIdOrFilename("shared-token", candidates);

        assertSame(byId, result);
    }

    @Test
    public void testResolveScenarioByIdOrFilename_returnsNullWhenUnresolvable() throws IOException {
        List<ReplayLogParser> candidates = List.of(parsedScenario("scenario-a", "perfect-draw-1"));

        assertNull(ReplayLogParser.resolveScenarioByIdOrFilename("no-such-scenario", candidates));
    }

    @Test
    public void testResolveScenarioByIdOrFilename_returnsNullForNullOrEmptyToken() throws IOException {
        List<ReplayLogParser> candidates = List.of(parsedScenario("scenario-a", "perfect-draw-1"));

        assertNull(ReplayLogParser.resolveScenarioByIdOrFilename(null, candidates));
        assertNull(ReplayLogParser.resolveScenarioByIdOrFilename("", candidates));
    }
}
