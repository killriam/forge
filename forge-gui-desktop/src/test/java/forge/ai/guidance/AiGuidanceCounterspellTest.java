package forge.ai.guidance;

import forge.ai.AITest;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end proof that {@code target_rankings} works for counterspell targeting
 * (ai-play-guidance-spec.md §5.2's {@code counterspell_priority} example: {@code target_spell.*}
 * condition fields against a spell on the stack, not {@code target.*} against a {@code Card}).
 * Closes the "counterspell target rankings" gap this repo's own §12.9.3/§12.7.3 notes flagged as
 * unimplemented. See forge-integration-guide.md §12.10 for the full design, including why the
 * fallback here (first non-vetoed survivor) differs from removal targeting's vanilla-evaluation
 * fallback: {@code CounterAi.chooseTargetSpellAbility()}'s own "best option" comparison is an
 * unfinished stub in vanilla Forge (a hardcoded {@code betterThanBest = false} — confirmed by
 * reading the source, not assumed), so there is no richer vanilla behavior to fall back to.
 *
 * <p>Calls {@link AiGuidanceProfile#chooseGuidedCounterTarget} directly with manually-constructed
 * candidate {@code SpellAbility}s (not spells actually resolved onto the game stack) — the same
 * "test the changed method directly" pattern §12.7.4 established, and unnecessary here anyway
 * since nothing this method does depends on stack position, only on each candidate
 * {@code SpellAbility}'s own host card.</p>
 */
public class AiGuidanceCounterspellTest extends AITest {

    private static File fixture(String name) {
        URL url = AiGuidanceCounterspellTest.class.getClassLoader().getResource("ai_guidance/" + name);
        AssertJUnit.assertNotNull("Test fixture ai_guidance/" + name + " not found on test classpath", url);
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private AiGuidanceProfile loadProfile() {
        Deck deck = new Deck();
        deck.setDecklistSpecPath(fixture("counterspell_priority.json").getAbsolutePath());
        return forge.ai.DeckRulesLoader.loadAiGuidanceIfNeeded(deck);
    }

    private SpellAbility spellOn(Player controller, String cardName) {
        Card card = createCard(cardName, controller);
        SpellAbility sa = card.getSpellAbilities().get(0);
        sa.setActivatingPlayer(controller);
        return sa;
    }

    @Test
    public void picksTheCanonicalThreatTierComboPieceOverAnUntaggedSpell() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        AiGuidanceProfile profile = loadProfile();

        SpellAbility oracle = spellOn(opponent, "Thassa's Oracle"); // tier_1_combo, CMC 2 - not vetoed
        SpellAbility divination = spellOn(opponent, "Divination");  // untagged, CMC 3 - not vetoed either
        List<SpellAbility> candidates = new ArrayList<>();
        candidates.add(divination);
        candidates.add(oracle);

        SpellAbility chosen = profile.chooseGuidedCounterTarget(
                spellOn(ai, "Counterspell"), ai, game, candidates);

        AssertJUnit.assertEquals("Thassa's Oracle (tier_1_combo, +100) should be chosen over the untagged Divination",
                oracle, chosen);
    }

    @Test
    public void vetoesTheLowCmcSpellAndTakesTheOtherOne() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        AiGuidanceProfile profile = loadProfile();

        SpellAbility opt = spellOn(opponent, "Opt");                // CMC 1 - vetoed
        SpellAbility divination = spellOn(opponent, "Divination");  // CMC 3 - not vetoed
        List<SpellAbility> candidates = new ArrayList<>();
        candidates.add(opt);
        candidates.add(divination);

        SpellAbility chosen = profile.chooseGuidedCounterTarget(
                spellOn(ai, "Counterspell"), ai, game, candidates);

        AssertJUnit.assertEquals("The CMC-1 Opt must never be the chosen target", divination, chosen);
    }

    @Test
    public void everyCandidateVetoedYieldsNoTarget() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        AiGuidanceProfile profile = loadProfile();

        List<SpellAbility> candidates = new ArrayList<>();
        candidates.add(spellOn(opponent, "Opt")); // CMC 1 - the only candidate, and it's vetoed

        SpellAbility chosen = profile.chooseGuidedCounterTarget(
                spellOn(ai, "Counterspell"), ai, game, candidates);

        AssertJUnit.assertNull("With the only candidate vetoed, guidance must not fall back to picking it anyway",
                chosen);
    }
}
