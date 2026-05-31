package forge.game.log;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Reorders players' libraries for scenario mode so that a predefined starting hand
 * and first-N draw cards appear at the front of the library in the correct sequence.
 *
 * <p>Used for {@code opening_hand_test} scenario type where both the opening hand and
 * the first draws after it are deterministically defined in the scenario JSON.</p>
 *
 * <p>Algorithm per player:
 * <ol>
 *   <li>Starting-hand cards (in order) → positions 0..6 (drawn as opening hand)</li>
 *   <li>First-draw cards (in order) → positions 7..N (drawn in turns 1..N)</li>
 *   <li>All remaining library cards in their current shuffled order</li>
 * </ol>
 * </p>
 *
 * <p>The commander card is already in the command zone and is never part of the library;
 * it is not affected by this reorder.</p>
 *
 * <p>Missing cards (not found in the library) are logged at WARN level and skipped,
 * following the same convention as {@link ReplayLibraryReorderer}.</p>
 */
public class ScenarioLibrarySetup {

    private static final Logger LOG = LoggerFactory.getLogger(ScenarioLibrarySetup.class);

    /**
     * Reorder all players' libraries based on scenario starting hands and first draws.
     *
     * <p>Player ID mapping: "P1" → {@code game.getPlayers().get(0)},
     * "P2" → index 1, etc. (matches {@code ReplayNotationExporter} convention).</p>
     *
     * @param game          the game whose libraries to reorder
     * @param startingHands per-player ordered card names for the opening hand (key = "P1", "P2", …)
     * @param firstDraws    per-player ordered card names to place on top after the hand (key = "P1", …)
     */
    public static void reorderLibraries(
            Game game,
            Map<String, List<String>> startingHands,
            Map<String, List<String>> firstDraws) {

        List<Player> players = game.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            String playerId = "P" + (i + 1);
            List<String> hand  = startingHands.getOrDefault(playerId, Collections.emptyList());
            List<String> draws = firstDraws.getOrDefault(playerId, Collections.emptyList());

            if (hand.isEmpty() && draws.isEmpty()) {
                LOG.debug("No scenario setup for {} — library left as-is", playerId);
                continue;
            }

            reorderLibrary(players.get(i), hand, draws);
        }

        LOG.info("ScenarioLibrarySetup: reorder complete for {} players", players.size());
    }

    /**
     * Reorder a single player's library.
     *
     * @param player       the player whose library to reorder
     * @param startingHand ordered card names that should form the opening hand (drawn first)
     * @param firstDraws   ordered card names to place immediately after the hand
     */
    public static void reorderLibrary(Player player, List<String> startingHand, List<String> firstDraws) {
        Zone library = player.getZone(ZoneType.Library);
        List<Card> remaining = new ArrayList<>();
        for (Card c : library.getCards().threadSafeIterable()) {
            remaining.add(c);
        }

        if (remaining.isEmpty()) {
            LOG.warn("ScenarioLibrarySetup: {}'s library is empty — cannot reorder", player.getName());
            return;
        }

        List<Card> reordered = new ArrayList<>();

        // Phase 1: starting hand cards → front of library (will be drawn as opening hand)
        int handMatched = placeCards(startingHand, remaining, reordered, player.getName(), "starting_hand");

        // Phase 2: first draw cards → immediately after hand
        int drawMatched = placeCards(firstDraws, remaining, reordered, player.getName(), "first_draws");

        // Phase 3: all remaining cards in their current (shuffled) order
        reordered.addAll(remaining);

        library.setCards(reordered);

        LOG.info("ScenarioLibrarySetup: {}'s library reordered — hand: {}/{}, draws: {}/{}, remaining: {}",
                player.getName(),
                handMatched, startingHand.size(),
                drawMatched, firstDraws.size(),
                remaining.size());
    }

    /**
     * For each name in {@code names}, find the first matching card in {@code pool},
     * move it to {@code target}, and remove it from {@code pool}.
     *
     * @return number of cards successfully matched
     */
    private static int placeCards(List<String> names, List<Card> pool,
                                   List<Card> target, String playerName, String section) {
        int matched = 0;
        for (String name : names) {
            Card found = null;
            for (Card c : pool) {
                if (c.getName().equals(name)) {
                    found = c;
                    break;
                }
            }
            if (found != null) {
                target.add(found);
                pool.remove(found);
                matched++;
            } else {
                LOG.warn("ScenarioLibrarySetup: card '{}' (section={}) not found in {}'s library — skipped",
                        name, section, playerName);
            }
        }
        return matched;
    }
}

