package forge.ai.guidance;

import forge.ai.AITest;
import forge.ai.ComputerUtilCard;
import forge.ai.PlayerControllerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Guidance loaded from a deck's {@code [AiGuidance]} section ({@link Deck#getAiGuidanceJson()}). */
public class AiGuidanceSectionTest extends AITest {

    private static final String COMMANDER_VETO = "{\"deck_rules\":{\"ai_guidance\":{\"target_rankings\":["
            + "{\"source_card\":\"Swords to Plowshares\",\"vetoes\":[{\"condition\":"
            + "{\"field\":\"target.is_commander\",\"op\":\"==\",\"value\":true}}]}]}}}";

    private static AiGuidanceProfile init(Player ai, Deck deck) {
        PlayerControllerAi pc = (PlayerControllerAi) ai.getController();
        pc.getAi().initGuidanceProfile(deck);
        return pc.getAi().getGuidanceProfile();
    }

    private SpellAbility swords(Player ai) {
        Card card = createCard("Swords to Plowshares", ai);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        return sa;
    }

    @Test
    public void sectionProfileBehavesLikeTheFileRoute() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Deck deck = new Deck("Sectioned");
        deck.setAiGuidanceJson(COMMANDER_VETO);

        AssertJUnit.assertNotNull(init(ai, deck));

        Card commander = addCard("Ghalta, Primal Hunger", opponent);
        commander.setCommander(true);
        Card bear = addCard("Runeclaw Bear", opponent);
        CardCollection both = new CardCollection();
        both.add(commander);
        both.add(bear);
        AssertJUnit.assertEquals(bear, ComputerUtilCard.getBestRemovalTargetAI(ai, both, swords(ai)));

        CardCollection onlyCommander = new CardCollection();
        onlyCommander.add(commander);
        AssertJUnit.assertNull("veto-all is still a hold", ComputerUtilCard.getBestRemovalTargetAI(ai, onlyCommander, swords(ai)));
    }

    @Test
    public void sectionWinsOverDecklistSpec() throws IOException {
        File other = File.createTempFile("other_spec", ".json");
        other.deleteOnExit();
        Files.write(other.toPath(), ("{\"deck_rules\":{\"ai_guidance\":{\"target_rankings\":[{\"source_card\":"
                + "\"Some Other Card\",\"vetoes\":[]}]}}}").getBytes(StandardCharsets.UTF_8));

        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Deck deck = new Deck("Both");
        deck.setDecklistSpecPath(other.getAbsolutePath());
        deck.setAiGuidanceJson(COMMANDER_VETO);

        AiGuidanceProfile profile = init(ai, deck);

        AssertJUnit.assertTrue(profile.hasTargetRankingRule("Swords to Plowshares"));
        AssertJUnit.assertFalse(profile.hasTargetRankingRule("Some Other Card"));
    }

    @Test
    public void unusableSectionLeavesGuidanceOffAndDoesNotFallBackToTheSpecFile() throws IOException {
        File spec = File.createTempFile("fallback_spec", ".json");
        spec.deleteOnExit();
        Files.write(spec.toPath(), COMMANDER_VETO.getBytes(StandardCharsets.UTF_8));

        String[] bad = {
                "",                                   // empty section
                "{\"a\":1,\n\"b\":2}",                // more than one line
                "{not json",                          // invalid JSON
                "[1,2,3]",                            // not an object
                "{\"deck_rules\":{}}",                // no ai_guidance
        };
        for (String body : bad) {
            Game game = initAndCreateGame();
            Player ai = game.getPlayers().get(1);
            Deck deck = new Deck("Broken");
            deck.setDecklistSpecPath(spec.getAbsolutePath());
            deck.setAiGuidanceJson(body);
            AssertJUnit.assertNull("guidance must be off for section body: " + body, init(ai, deck));
        }
    }

    @Test
    public void deckWithoutASectionStillUsesDecklistSpec() throws IOException {
        File spec = File.createTempFile("route_spec", ".json");
        spec.deleteOnExit();
        Files.write(spec.toPath(), COMMANDER_VETO.getBytes(StandardCharsets.UTF_8));

        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Deck deck = new Deck("SpecOnly");
        deck.setDecklistSpecPath(spec.getAbsolutePath());

        AssertJUnit.assertTrue(init(ai, deck).hasTargetRankingRule("Swords to Plowshares"));
    }
}
