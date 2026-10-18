package forge.deck.io;

import forge.deck.Deck;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.testng.AssertJUnit.*;

/** The optional {@code [AiGuidance]} deck-file section: a raw one-line JSON string carried on {@link Deck}. */
public class DeckSerializerAiGuidanceTest {

    private static final String JSON = "{\"deck_rules\":{\"ai_guidance\":{\"target_rankings\":[{\"source_card\":"
            + "\"Swords to Plowshares\",\"vetoes\":[{\"condition\":{\"field\":\"target.is_commander\","
            + "\"op\":\"==\",\"value\":true}}]}]}}}";

    private static File tempDeckFile() throws IOException {
        File f = File.createTempFile("ai-guidance-deck-test", ".dck");
        f.deleteOnExit();
        return f;
    }

    private static Deck read(String fileText) throws IOException {
        File f = tempDeckFile();
        Files.write(f.toPath(), fileText.getBytes(StandardCharsets.UTF_8));
        return DeckSerializer.fromFile(f);
    }

    @Test
    public void roundTripsUnchangedAndIsWrittenLast() throws IOException {
        Deck d = new Deck("Guided Deck");
        d.setAiGuidanceJson(JSON);

        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

        assertTrue("section is the last thing in the file", text.trim().endsWith(JSON));
        assertTrue(text.contains("[AiGuidance]"));
        assertEquals(JSON, DeckSerializer.fromFile(f).getAiGuidanceJson());
    }

    @Test
    public void absentSectionMeansNull() throws IOException {
        Deck d = read("[metadata]\nName=Plain\n");
        assertNotNull(d);
        assertNull(d.getAiGuidanceJson());
    }

    @Test
    public void sectionIsFoundAnywhereAndCaseInsensitively() throws IOException {
        Deck d = read("[aiguidance]\n" + JSON + "\n[metadata]\nName=Out Of Order\n");
        assertNotNull(d);
        assertEquals("Out Of Order", d.getName());
        assertEquals(JSON, d.getAiGuidanceJson());
    }

    @Test
    public void unknownSectionsAreStillIgnoredAndOtherMetadataIsUnaffected() throws IOException {
        Deck d = read("[metadata]\nName=Mixed\nAiHints=DecklistSpec$some/path.json\n[Whatever]\nfoo\n[AiGuidance]\n"
                + JSON + "\n");
        assertEquals("Mixed", d.getName());
        assertEquals("some/path.json", d.getDecklistSpecPath());
        assertEquals(JSON, d.getAiGuidanceJson());
    }

    @Test
    public void emptySectionIsKeptAsEmptyStringSoTheConsumerCanComplain() throws IOException {
        Deck d = read("[metadata]\nName=Empty\n[AiGuidance]\n");
        assertEquals("", d.getAiGuidanceJson());

        // (A deck read from a file needs the card database to be re-written, so use a fresh one here.)
        Deck fresh = new Deck("Empty");
        fresh.setAiGuidanceJson(d.getAiGuidanceJson());
        File f = tempDeckFile();
        DeckSerializer.writeDeck(fresh, f);
        assertFalse("an empty section is not written back",
                new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).contains("[AiGuidance]"));
    }

    @Test
    public void severalLinesAreKeptJoinedNotTruncated() throws IOException {
        Deck d = read("[metadata]\nName=Multi\n[AiGuidance]\n{\"a\":1,\n\"b\":2}\n");
        assertEquals("{\"a\":1,\n\"b\":2}", d.getAiGuidanceJson());
    }

    @Test
    public void aFiftyKilobyteLineIsAcceptedUntouched() throws IOException {
        StringBuilder sb = new StringBuilder("{\"deck_rules\":{\"pad\":\"");
        for (int i = 0; i < 60_000; i++) {
            sb.append('x');
        }
        String big = sb.append("\"}}").toString();
        Deck d = new Deck("Big");
        d.setAiGuidanceJson(big);

        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);

        assertEquals(big, DeckSerializer.fromFile(f).getAiGuidanceJson());
    }

    @Test
    public void escapedNonAsciiSurvivesTheWriterEncoding() throws IOException {
        // The writer uses the platform charset, so MaMo must send \\uXXXX escapes; ASCII text round-trips on any charset.
        String ascii = "{\"deck_rules\":{\"n\":\"Lim-D\\u00fbl\"}}";
        Deck d = new Deck("Escaped");
        d.setAiGuidanceJson(ascii);
        File f = tempDeckFile();
        DeckSerializer.writeDeck(d, f);
        assertEquals(ascii, DeckSerializer.fromFile(f).getAiGuidanceJson());
    }

    @Test
    public void copyKeepsTheSection() {
        Deck d = new Deck("Original");
        d.setAiGuidanceJson(JSON);
        Deck copy = (Deck) d.copyTo("Renamed");
        assertEquals(JSON, copy.getAiGuidanceJson());
    }
}
