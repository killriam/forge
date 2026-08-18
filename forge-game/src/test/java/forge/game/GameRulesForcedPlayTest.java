package forge.game;

import forge.util.Localizer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.*;

/**
 * Tests for {@link GameRules#popForcedPlayIfMatches(String, String)}, the hook that keeps a
 * human seat's "what's next" scripted-sequence hint (see {@code CPrompt}) in sync with what the
 * player has actually played.
 *
 * <p>Doesn't need a running game/FModel, but {@link GameRules}'s constructor takes a
 * {@link GameType}, and every {@code GameType} enum constant's constructor calls
 * {@link Localizer#getMessage}, which throws NPE until a resource bundle is loaded - hence the
 * one-time {@link #initLocalizer()} bootstrap below, pointed at forge-gui's real language files
 * (forge-game has no localized resources of its own to fall back on).</p>
 */
public class GameRulesForcedPlayTest {

    @BeforeClass
    public static void initLocalizer() {
        File dir = new File("").getAbsoluteFile();
        File languagesDir = null;
        for (int i = 0; i < 5 && dir != null; i++) {
            File candidate = new File(dir, "forge-gui/res/languages");
            if (candidate.isDirectory()) {
                languagesDir = candidate;
                break;
            }
            dir = dir.getParentFile();
        }
        assertNotNull("Could not locate forge-gui/res/languages from " + new File("").getAbsolutePath(), languagesDir);
        Localizer.getInstance().initialize("en-US", languagesDir.getPath());
    }

    private static GameRules rulesWithSequence(Map<String, List<String>> seq) {
        GameRules rules = new GameRules(GameType.Constructed);
        rules.setForcedPlaySequence(seq);
        return rules;
    }

    @Test
    public void testPopForcedPlayIfMatches_popsHeadOnMatch() {
        Map<String, List<String>> seq = new LinkedHashMap<>();
        seq.put("killriam", new ArrayList<>(List.of("Forest", "Sol Ring")));
        GameRules rules = rulesWithSequence(seq);

        rules.popForcedPlayIfMatches("killriam", "Forest");

        assertEquals(List.of("Sol Ring"), rules.getForcedPlaySequence().get("killriam"));
    }

    @Test
    public void testPopForcedPlayIfMatches_noopOnMismatch() {
        Map<String, List<String>> seq = new LinkedHashMap<>();
        seq.put("killriam", new ArrayList<>(List.of("Forest", "Sol Ring")));
        GameRules rules = rulesWithSequence(seq);

        rules.popForcedPlayIfMatches("killriam", "Mountain");

        assertEquals("Non-matching card name should not advance the queue",
                List.of("Forest", "Sol Ring"), rules.getForcedPlaySequence().get("killriam"));
    }

    @Test
    public void testPopForcedPlayIfMatches_noopWhenNoSequenceForLobbyName() {
        Map<String, List<String>> seq = new LinkedHashMap<>();
        seq.put("killriam", new ArrayList<>(List.of("Forest")));
        GameRules rules = rulesWithSequence(seq);

        // Should not throw for an unrelated seat.
        rules.popForcedPlayIfMatches("AI 1", "Forest");

        assertEquals(List.of("Forest"), rules.getForcedPlaySequence().get("killriam"));
        assertNull(rules.getForcedPlaySequence().get("AI 1"));
    }

    @Test
    public void testPopForcedPlayIfMatches_noopWhenSequenceExhausted() {
        Map<String, List<String>> seq = new LinkedHashMap<>();
        seq.put("killriam", new ArrayList<>());
        GameRules rules = rulesWithSequence(seq);

        rules.popForcedPlayIfMatches("killriam", "Forest");

        assertTrue(rules.getForcedPlaySequence().get("killriam").isEmpty());
    }

    @Test
    public void testPopForcedPlayIfMatches_noopWhenForcedSequenceNull() {
        GameRules rules = new GameRules(GameType.Constructed);
        assertNull(rules.getForcedPlaySequence());

        // Should not throw when no scenario is attached at all.
        rules.popForcedPlayIfMatches("killriam", "Forest");

        assertNull(rules.getForcedPlaySequence());
    }

    @Test
    public void testPopForcedPlayIfMatches_repeatedNameOnlyPopsOncePerCall() {
        // Regression test for the AI-double-pop scenario described in the popForcedPlayIfMatches
        // javadoc: a script with two consecutive "Swamp" entries must only ever lose one entry
        // per call, matching AiController's own single pre-play pop for the same physical play.
        Map<String, List<String>> seq = new LinkedHashMap<>();
        seq.put("AI 1", new ArrayList<>(List.of("Swamp", "Swamp", "Mountain")));
        GameRules rules = rulesWithSequence(seq);

        rules.popForcedPlayIfMatches("AI 1", "Swamp");
        assertEquals(List.of("Swamp", "Mountain"), rules.getForcedPlaySequence().get("AI 1"));

        rules.popForcedPlayIfMatches("AI 1", "Swamp");
        assertEquals(List.of("Mountain"), rules.getForcedPlaySequence().get("AI 1"));
    }

    @Test
    public void testPopForcedPlayIfMatches_multiSeatIsolation() {
        Map<String, List<String>> seq = new LinkedHashMap<>();
        seq.put("Player 1", new ArrayList<>(List.of("Forest", "Sol Ring")));
        seq.put("Player 2", new ArrayList<>(List.of("Island", "Counterspell")));
        seq.put("Player 3", new ArrayList<>(List.of("Mountain", "Lightning Bolt")));
        GameRules rules = rulesWithSequence(seq);

        // Popping for Player 2 only modifies Player 2's sequence
        rules.popForcedPlayIfMatches("Player 2", "Island");
        assertEquals(List.of("Forest", "Sol Ring"), rules.getForcedPlaySequence().get("Player 1"));
        assertEquals(List.of("Counterspell"), rules.getForcedPlaySequence().get("Player 2"));
        assertEquals(List.of("Mountain", "Lightning Bolt"), rules.getForcedPlaySequence().get("Player 3"));

        // Non-matching card for Player 1 leaves it untouched
        rules.popForcedPlayIfMatches("Player 1", "Swamp");
        assertEquals(List.of("Forest", "Sol Ring"), rules.getForcedPlaySequence().get("Player 1"));

        // Matching card for Player 1 pops it
        rules.popForcedPlayIfMatches("Player 1", "Forest");
        assertEquals(List.of("Sol Ring"), rules.getForcedPlaySequence().get("Player 1"));
    }

    @Test
    public void testPopForcedPlayIfMatches_fullSequenceDrain() {
        Map<String, List<String>> seq = new LinkedHashMap<>();
        seq.put("P1", new ArrayList<>(List.of("Land A", "Spell B", "Spell C")));
        GameRules rules = rulesWithSequence(seq);

        rules.popForcedPlayIfMatches("P1", "Land A");
        assertEquals(List.of("Spell B", "Spell C"), rules.getForcedPlaySequence().get("P1"));

        rules.popForcedPlayIfMatches("P1", "Spell B");
        assertEquals(List.of("Spell C"), rules.getForcedPlaySequence().get("P1"));

        rules.popForcedPlayIfMatches("P1", "Spell C");
        assertTrue(rules.getForcedPlaySequence().get("P1").isEmpty());

        // Further pops on empty list do not crash
        rules.popForcedPlayIfMatches("P1", "Spell D");
        assertTrue(rules.getForcedPlaySequence().get("P1").isEmpty());
    }
}
