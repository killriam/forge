package forge.deck.io;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.util.FileSection;
import forge.util.FileSectionManual;
import forge.util.FileUtil;
import forge.util.TextUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class DeckSerializer {

    /** Name of the optional section holding the deck's ai_guidance JSON (matched case-insensitively on read). */
    public static final String AI_GUIDANCE_SECTION = "AiGuidance";

    public static void writeDeck(final Deck d, final File f) {
        FileUtil.writeFile(f, serializeDeck(d));
    }

    static DeckFileHeader readDeckMetadata(final Map<String, List<String>> map) {
        if (map == null) {
            return null;
        }
        final List<String> metadata = map.get("metadata");
        if (metadata != null) {
            return new DeckFileHeader(FileSection.parse(metadata, FileSection.EQUALS_KV_SEPARATOR));
        }
        final List<String> general = map.get("general");
        if (general != null) {
            final FileSectionManual fs = new FileSectionManual();
            fs.put(DeckFileHeader.NAME, StringUtils.join(map.get(""), " "));
            fs.put(DeckFileHeader.DECK_TYPE, StringUtils.join(general, " "));
            return new DeckFileHeader(fs);
        }

        return null;
    }

    private static List<String> serializeDeck(Deck d) {
        final List<String> out = new ArrayList<>();
        out.add(TextUtil.enclosedBracket("metadata"));
    
        out.add(TextUtil.concatNoSpace(DeckFileHeader.NAME,"=", d.getName().replaceAll("\n", "")));
        if (d.getDeckFormat() != null) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.DECK_TYPE, "=", d.getDeckFormat().name()));
        }
        if (d.getSourceUrl() != null) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.SOURCE_URL, "=", d.getSourceUrl().replaceAll("\n", "")));
        }
        // these are optional
        if (d.getComment() != null) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.COMMENT,"=", d.getComment().replaceAll("\n", "")));
        }
        if (!d.getTags().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.TAGS,"=", StringUtils.join(d.getTags(), DeckFileHeader.TAGS_SEPARATOR)));
        }
        if (!d.getAiHints().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.AI_HINTS, "=", StringUtils.join(d.getAiHints(), " | ")));
        }
        if (!d.getDraftNotes().isEmpty()) {
            String sb = serializeDraftNotes(d.getDraftNotes());
            out.add(TextUtil.concatNoSpace(DeckFileHeader.DRAFT_NOTES, "=", sb));
        }
        if (!d.getKeyCards().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.KEY_CARDS, "=", StringUtils.join(d.getKeyCards(), ";")));
        }
        if (d.getDeckUrl() != null && !d.getDeckUrl().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.DECK_URL, "=", d.getDeckUrl()));
        }
        if (d.getEvalScenarioIds() != null && !d.getEvalScenarioIds().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.EVAL_SCENARIO, "=", d.getEvalScenarioIds()));
        }
        if (d.getScenarioIds() != null && !d.getScenarioIds().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.SCENARIO, "=", d.getScenarioIds()));
        }
        if (!d.getCardsUnderTest().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.CARDS_UNDER_TEST, "=", StringUtils.join(d.getCardsUnderTest(), "; ")));
        }
        if (!d.getNewCardsSinceLastRevision().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.NEW_CARDS_SINCE_LAST_REVISION, "=", StringUtils.join(d.getNewCardsSinceLastRevision(), "; ")));
        }
        if (!d.getSleeveArtKey().isEmpty()) {
            out.add(TextUtil.concatNoSpace(DeckFileHeader.SLEEVE_ART, "=", d.getSleeveArtKey()));
            if (d.getSleeveArtOffset() != Deck.DEFAULT_SLEEVE_OFFSET) {
                out.add(TextUtil.concatNoSpace(DeckFileHeader.SLEEVE_OFFSET, "=", String.valueOf(d.getSleeveArtOffset())));
            }
        }

        for (Entry<DeckSection, CardPool> s : d) {
            if (s.getValue().isEmpty())
                continue;
            out.add(TextUtil.enclosedBracket(s.getKey().toString()));
            out.add(s.getValue().toCardList(System.lineSeparator()));
        }
        // Last, one line, unchanged: the raw JSON is owned by whoever wrote it (MaMo), not rebuilt here.
        if (d.getAiGuidanceJson() != null && !d.getAiGuidanceJson().trim().isEmpty()) {
            out.add(TextUtil.enclosedBracket(AI_GUIDANCE_SECTION));
            out.add(d.getAiGuidanceJson());
        }
        return out;
    }

    public static String serializeDraftNotes(final Map<String, String> draftNotes) {
        StringBuilder sb = new StringBuilder();
        for (String key : draftNotes.keySet()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }

            sb.append(key).append(":").append(draftNotes.get(key));
        }
        return sb.toString();
    }

    public static Deck fromFile(final File deckFile) {
        return fromSections(FileSection.parseSections(FileUtil.readFile(deckFile)));
    }

    public static Deck fromSections(final Map<String, List<String>> sections) {
        if (sections == null || sections.isEmpty()) {
            return null;
        }
    
        final DeckFileHeader dh = readDeckMetadata(sections);
        if (dh == null) {
            return null;
        }

        Deck d = new Deck(dh.getName());
        d.setComment(dh.getComment());
        d.setDeckFormat(dh.getDeckType());
        d.setSourceUrl(dh.getSourceUrl());
        d.setAiHints(dh.getAiHints());
        d.getTags().addAll(dh.getTags());
        d.setDraftNotes(dh.getDraftNotes());
        for (String keyCard : dh.getKeyCards()) {
            d.addKeyCard(keyCard);
        }
        for (String c : dh.getCardsUnderTest()) {
            d.addCardUnderTest(c);
        }
        for (String c : dh.getNewCardsSinceLastRevision()) {
            d.addNewCardSinceLastRevision(c);
        }
        if (dh.getDeckUrl() != null)       d.setDeckUrl(dh.getDeckUrl());
        if (dh.getEvalScenario() != null)  d.setEvalScenarioIds(dh.getEvalScenario());
        if (dh.getScenario() != null)      d.setScenarioIds(dh.getScenario());
        d.setSleeveArtKey(dh.getSleeveArtKey());
        d.setSleeveArtOffset(dh.getSleeveArtOffset());
        for (Map.Entry<String, List<String>> section : sections.entrySet()) {
            if (AI_GUIDANCE_SECTION.equalsIgnoreCase(section.getKey())) {
                // Non-empty lines only (the section parser already drops blank ones). More than one line is kept
                // joined by a newline so the consumer can reject it loudly; it is never silently truncated.
                d.setAiGuidanceJson(StringUtils.join(section.getValue(), "\n"));
                break;
            }
        }
        d.setDeferredSections(sections);
        return d;
    }
}
