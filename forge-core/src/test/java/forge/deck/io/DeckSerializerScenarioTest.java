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

    @Test
    public void testCardsUnderTest_roundTripsThroughFile() throws IOException {
        Deck d = new Deck("Cards Under Test Deck");
        d.addCardUnderTest("Cyclonic Rift");
        d.addCardUnderTest("Rhystic Study");
        d.addCardUnderTest("Sol Ring");

        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);
        Deck reloaded = DeckSerializer.fromFile(f);

        assertNotNull(reloaded);
        assertEquals(3, reloaded.getCardsUnderTest().size());
        assertTrue(reloaded.isCardUnderTest("Cyclonic Rift"));
        assertTrue(reloaded.isCardUnderTest("Rhystic Study"));
        assertTrue(reloaded.isCardUnderTest("Sol Ring"));
        assertFalse(reloaded.isCardUnderTest("Black Lotus"));
    }

    @Test
    public void testNewCardsSinceLastRevision_roundTripsThroughFile() throws IOException {
        Deck d = new Deck("New Cards Deck");
        d.addNewCardSinceLastRevision("Arcane Signet");
        d.addNewCardSinceLastRevision("Mana Drain");

        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);
        Deck reloaded = DeckSerializer.fromFile(f);

        assertNotNull(reloaded);
        assertEquals(2, reloaded.getNewCardsSinceLastRevision().size());
        assertTrue(reloaded.isNewCardSinceLastRevision("Arcane Signet"));
        assertTrue(reloaded.isNewCardSinceLastRevision("Mana Drain"));
        assertFalse(reloaded.isNewCardSinceLastRevision("Sol Ring"));
    }

    @Test
    public void testDeckFileHeader_parsesCardsUnderTestAndNewCardsDirectly() {
        FileSectionManual kvPairs = new FileSectionManual();
        kvPairs.put(DeckFileHeader.NAME, "killriam - Atraxa Superfriends (2026-09-08)");
        kvPairs.put(DeckFileHeader.CARDS_UNDER_TEST, "Cyclonic Rift; Rhystic Study; Sol Ring");
        kvPairs.put(DeckFileHeader.NEW_CARDS_SINCE_LAST_REVISION, "Arcane Signet; Mana Drain");
        DeckFileHeader dh = new DeckFileHeader(kvPairs);

        assertEquals(3, dh.getCardsUnderTest().size());
        assertEquals("Cyclonic Rift", dh.getCardsUnderTest().get(0));
        assertEquals("Rhystic Study", dh.getCardsUnderTest().get(1));
        assertEquals("Sol Ring", dh.getCardsUnderTest().get(2));

        assertEquals(2, dh.getNewCardsSinceLastRevision().size());
        assertEquals("Arcane Signet", dh.getNewCardsSinceLastRevision().get(0));
        assertEquals("Mana Drain", dh.getNewCardsSinceLastRevision().get(1));
    }

    @Test
    public void testCopyTo_preservesCardsUnderTestAndScenarioIds() {
        Deck d = new Deck("Original");
        d.setScenarioIds("scen-1");
        d.addCardUnderTest("Sol Ring");
        d.addNewCardSinceLastRevision("Mana Drain");

        Deck copy = (Deck) d.copyTo("Cloned");
        assertEquals("scen-1", copy.getScenarioIds());
        assertEquals(1, copy.getCardsUnderTest().size());
        assertTrue(copy.isCardUnderTest("Sol Ring"));
        assertEquals(1, copy.getNewCardsSinceLastRevision().size());
        assertTrue(copy.isNewCardSinceLastRevision("Mana Drain"));
    }
}
