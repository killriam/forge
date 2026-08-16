package forge.deck.io;

import forge.deck.Deck;
import forge.util.FileSectionManual;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.testng.AssertJUnit.*;

/**
 * Round-trip tests for the {@code Scenario=} deck metadata key (attaches a scenario JSON file,
 * by id or filename, to a real Constructed/Commander deck) - mirrors the pre-existing
 * {@code EvalScenario=} key's write/read pattern in {@link DeckSerializer}.
 */
public class DeckSerializerScenarioTest {

    private static File tempDeckFile() throws IOException {
        File f = File.createTempFile("scenario-deck-test", ".dck");
        f.deleteOnExit();
        return f;
    }

    @Test
    public void testScenarioIds_roundTripsThroughFile() throws IOException {
        Deck d = new Deck("Scenario Round-Trip Deck");
        d.setScenarioIds("perfect-draw-1");

        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);
        Deck reloaded = DeckSerializer.fromFile(f);

        assertNotNull(reloaded);
        assertEquals("perfect-draw-1", reloaded.getScenarioIds());
    }

    @Test
    public void testScenarioIds_supportsCommaSeparatedList() throws IOException {
        Deck d = new Deck("Multi-Scenario Deck");
        d.setScenarioIds("perfect-draw-1,best-hand-2");

        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);
        Deck reloaded = DeckSerializer.fromFile(f);

        assertEquals("perfect-draw-1,best-hand-2", reloaded.getScenarioIds());
    }

    @Test
    public void testScenarioIds_absentWhenNeverSet() throws IOException {
        Deck d = new Deck("Plain Deck");

        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);
        Deck reloaded = DeckSerializer.fromFile(f);

        assertNull(reloaded.getScenarioIds());
    }

    @Test
    public void testScenarioIds_independentFromEvalScenarioIds() throws IOException {
        // Scenario= (this feature) and EvalScenario= (the pre-existing eval_sequence mechanism)
        // are sibling keys for two different scenario types - they must not collide or overwrite
        // each other when both happen to be set on the same deck.
        Deck d = new Deck("Both Keys Deck");
        d.setScenarioIds("perfect-draw-1");
        d.setEvalScenarioIds("eval-seq-1");

        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);
        Deck reloaded = DeckSerializer.fromFile(f);

        assertEquals("perfect-draw-1", reloaded.getScenarioIds());
        assertEquals("eval-seq-1", reloaded.getEvalScenarioIds());
    }

    @Test
    public void testDeckFileHeader_parsesScenarioKeyDirectly() {
        FileSectionManual kvPairs = new FileSectionManual();
        kvPairs.put(DeckFileHeader.NAME, "x");
        kvPairs.put(DeckFileHeader.SCENARIO, "perfect-draw-1");
        DeckFileHeader dh = new DeckFileHeader(kvPairs);

        assertEquals("perfect-draw-1", dh.getScenario());
    }
}
