package forge.game.scenario;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import forge.game.ReplayLogParser;
import forge.game.ReplayLogParser.ScenarioInfo;
import org.testng.annotations.Test;

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
}
