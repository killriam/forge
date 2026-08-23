package forge.ai.guidance;

import forge.ai.AITest;
import forge.ai.ComputerUtilCard;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * End-to-end proof that the {@code target_rankings} veto/ladder hook wired into
 * {@code ComputerUtilCard.getBestRemovalTargetAI(Player, Iterable, SpellAbility)} actually
 * changes which target the AI picks. Covers the two source-spec human checkpoints (§11.5
 * "Checkpoint 3: Threat Triage Decision" / {@code UNI_THREAT_TRIAGE} and "Checkpoint 4:
 * Indestructible Veto" / {@code UNI_VETO_INDESTRUCTIBLE}) that forge-integration-guide.md
 * §12.6.5 flagged as blocked until this shipped.
 *
 * <p>Calls {@code ComputerUtilCard.getBestRemovalTargetAI(...)} — the real, shared production
 * method all three removal handlers (DestroyAi, DamageDealAi, and ChangeZoneAi, which owns
 * Swords to Plowshares) call — directly, rather than driving a full priority-loop turn and
 * checking what ended up on the battlefield. This is a deliberate design choice, not a shortcut:
 * whether the AI's <i>outer</i> "is casting this spell right now worthwhile" decision fires at
 * all turned out to be genuinely non-deterministic in this exact test harness — reproducible
 * flakiness present even with no {@code ai_guidance} involved (confirmed by running the
 * no-guidance case repeatedly and seeing it flip between {@code WillPlay} and
 * {@code TargetingFailed} with no code change in between) — so it is an unrelated, pre-existing
 * source of noise, not something this test needs to fight. Testing the target-selection method
 * directly is both more precise (it isolates exactly the boundary this slice changed) and
 * reliable (deterministic given fixed inputs). See forge-integration-guide.md §12.7 for the full
 * account.</p>
 *
 * <p>The fixture ({@code ai_guidance/swords_target_ranking.json}) is close to
 * forge-integration-guide.md §12.3's own worked Swords-to-Plowshares example, minus the fields
 * this slice doesn't implement (commander/power-based scoring, evasion-based scoring).</p>
 */
public class AiGuidanceTargetRankingTest extends AITest {

    private static File fixture(String name) {
        URL url = AiGuidanceTargetRankingTest.class.getClassLoader().getResource("ai_guidance/" + name);
        AssertJUnit.assertNotNull("Test fixture ai_guidance/" + name + " not found on test classpath", url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void attachGuidance(Player ai, String fixtureName) {
        Deck deck = new Deck();
        deck.setDecklistSpecPath(fixture(fixtureName).getAbsolutePath());
        ((forge.ai.PlayerControllerAi) ai.getController()).getAi().initGuidanceProfile(deck);
    }

    /** A real, activatable Swords to Plowshares SpellAbility, hosted by {@code ai}. */
    private SpellAbility swordsToPlowshares(Player ai) {
        Card card = createCard("Swords to Plowshares", ai);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(ai);
        return sa;
    }

    @Test
    public void targetsTheCanonicalThreatTierEngineHubOverAVanillaBeater() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attachGuidance(ai, "swords_target_ranking.json");

        Card korvold = addCard("Korvold, Fae-Cursed King", opponent); // tier_2_engine in the fixture
        Card ghalta = addCard("Ghalta, Primal Hunger", opponent);     // no tier/role tag
        CardCollection candidates = new CardCollection();
        candidates.add(korvold);
        candidates.add(ghalta);

        Card chosen = ComputerUtilCard.getBestRemovalTargetAI(ai, candidates, swordsToPlowshares(ai));

        AssertJUnit.assertEquals("Korvold (tier_2_engine, +70 ladder score) should be chosen over untagged Ghalta",
                korvold, chosen);
    }

    @Test
    public void vetoesTheIndestructibleTargetAndTakesTheOtherOne() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attachGuidance(ai, "swords_target_ranking.json");

        Card colossus = addCard("Darksteel Colossus", opponent);      // real printed Indestructible - vetoed
        Card korvold = addCard("Korvold, Fae-Cursed King", opponent); // not indestructible - no veto applies
        CardCollection candidates = new CardCollection();
        candidates.add(colossus);
        candidates.add(korvold);

        Card chosen = ComputerUtilCard.getBestRemovalTargetAI(ai, candidates, swordsToPlowshares(ai));

        AssertJUnit.assertEquals("Indestructible Darksteel Colossus must never be the chosen target",
                korvold, chosen);
    }

    @Test
    public void everyCandidateVetoedYieldsNoTarget() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        attachGuidance(ai, "swords_target_ranking.json");

        Card colossus = addCard("Darksteel Colossus", opponent);
        CardCollection candidates = new CardCollection();
        candidates.add(colossus);

        Card chosen = ComputerUtilCard.getBestRemovalTargetAI(ai, candidates, swordsToPlowshares(ai));

        AssertJUnit.assertNull("With the only candidate vetoed, guidance must not fall back to picking it anyway",
                chosen);
    }

    @Test
    public void withoutAGuidanceProfileTargetSelectionIsUnchanged() {
        // Backward compatibility: no ai_guidance -> hasTargetRankingRule() is false for every
        // card -> getBestRemovalTargetAI(ai, list, sa) falls straight through to the vanilla
        // 2-arg evaluateRemovalTargetPriority path, same as if sa had never been passed at all.
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        // deliberately no attachGuidance(...) call here

        Card colossus = addCard("Darksteel Colossus", opponent);
        Card bear = addCard("Runeclaw Bear", opponent);
        CardCollection candidates = new CardCollection();
        candidates.add(colossus);
        candidates.add(bear);

        Card guided = ComputerUtilCard.getBestRemovalTargetAI(ai, candidates, swordsToPlowshares(ai));
        Card vanilla = ComputerUtilCard.getBestRemovalTargetAI(ai, candidates);

        AssertJUnit.assertEquals(
                "With no ai_guidance profile attached, passing sa must select the exact same target as the vanilla 2-arg call",
                vanilla, guided);
    }
}
