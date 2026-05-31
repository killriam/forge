package forge.game.scenario;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.log.ScenarioLibrarySetup;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Integration tests for the scenario starting-hand mechanism.
 *
 * <p>These tests verify that {@link ScenarioLibrarySetup} correctly reorders
 * a player's library so that named cards appear at the front (and are drawn as
 * the opening hand), and that the full game engine picks these up via
 * {@code GameRules.setScenarioStartingHands()} → {@code GameAction.startGame()}.</p>
 *
 * <h2>Coverage</h2>
 * <ol>
 *   <li>{@link #testReorderLibraryPlacesStartingHandFirst()} — unit-level: verifies
 *       the library order after calling {@code reorderLibrary()} directly.</li>
 *   <li>{@link #testReorderLibraryWithFirstDraws()} — verifies that first-draw cards
 *       appear immediately after the starting hand in the library.</li>
 *   <li>{@link #testHandIsDrawnAfterReorder()} — verifies that drawing 7 cards after
 *       reorder yields exactly the specified starting hand.</li>
 *   <li>{@link #testMissingCardSkippedWithWarn()} — verifies graceful handling when a
 *       named card is not present in the library.</li>
 *   <li>{@link #testGameRulesScenarioHandEndToEnd()} — full integration: creates a
 *       {@code Match} with scenario rules, starts the game with a hook to capture hands,
 *       and asserts the captured hand matches the scenario spec.</li>
 * </ol>
 *
 * <p><strong>Note:</strong> Tests 1–4 manipulate a pre-created game's zones directly
 * (no deck files needed). Test 5 builds minimal decks programmatically.</p>
 */
public class ScenarioStartingHandTest extends AITest {

    // -------------------------------------------------------------------------
    // 1. Basic reorder — starting hand first
    // -------------------------------------------------------------------------

    /**
     * After calling {@code ScenarioLibrarySetup.reorderLibrary()}, the first N cards
     * in the library must be the starting-hand cards in the given order, followed by
     * the remaining cards.
     */
    @Test
    public void testReorderLibraryPlacesStartingHandFirst() {
        Game game = initAndCreateGame();
        Player p0 = game.getPlayers().get(0);

        // Populate library with 15 basic lands (5 of each)
        populateLibraryWithBasicLands(p0, 5, 5, 5);

        List<String> startingHand = Arrays.asList(
                "Mountain", "Mountain", "Forest", "Forest", "Island", "Mountain", "Forest");

        ScenarioLibrarySetup.reorderLibrary(p0, startingHand, Collections.emptyList());

        List<Card> library = new ArrayList<>(p0.getZone(ZoneType.Library).getCards());
        assertEquals("Library size must be unchanged after reorder", 15, library.size());

        // First 7 cards must match the starting hand (in order)
        assertEquals("Library[0] should be Mountain", "Mountain", library.get(0).getName());
        assertEquals("Library[1] should be Mountain", "Mountain", library.get(1).getName());
        assertEquals("Library[2] should be Forest",   "Forest",   library.get(2).getName());
        assertEquals("Library[3] should be Forest",   "Forest",   library.get(3).getName());
        assertEquals("Library[4] should be Island",   "Island",   library.get(4).getName());
        assertEquals("Library[5] should be Mountain", "Mountain", library.get(5).getName());
        assertEquals("Library[6] should be Forest",   "Forest",   library.get(6).getName());

        System.out.println("[PASS] testReorderLibraryPlacesStartingHandFirst");
    }

    // -------------------------------------------------------------------------
    // 2. Reorder with first draws
    // -------------------------------------------------------------------------

    /**
     * After calling {@code reorderLibrary()} with both startingHand and firstDraws,
     * the library order must be: [startingHand cards] → [firstDraws cards] → [rest].
     */
    @Test
    public void testReorderLibraryWithFirstDraws() {
        Game game = initAndCreateGame();
        Player p0 = game.getPlayers().get(0);

        populateLibraryWithBasicLands(p0, 5, 5, 5);

        List<String> startingHand = Arrays.asList(
                "Mountain", "Mountain", "Forest", "Forest", "Island", "Mountain", "Forest");
        List<String> firstDraws   = Arrays.asList("Island", "Island");

        ScenarioLibrarySetup.reorderLibrary(p0, startingHand, firstDraws);

        List<Card> library = new ArrayList<>(p0.getZone(ZoneType.Library).getCards());
        assertEquals("Library size must be unchanged", 15, library.size());

        // Positions 7 and 8 should be the first-draw cards
        assertEquals("Library[7] should be Island (first draw)", "Island", library.get(7).getName());
        assertEquals("Library[8] should be Island (first draw)", "Island", library.get(8).getName());

        System.out.println("[PASS] testReorderLibraryWithFirstDraws");
    }

    // -------------------------------------------------------------------------
    // 3. Drawing the starting hand
    // -------------------------------------------------------------------------

    /**
     * After reordering the library, drawing 7 cards must yield exactly the named
     * starting-hand cards (multiset comparison — order in hand may differ).
     */
    @Test
    public void testHandIsDrawnAfterReorder() {
        Game game = initAndCreateGame();
        Player p0 = game.getPlayers().get(0);

        populateLibraryWithBasicLands(p0, 5, 5, 5);

        List<String> startingHand = Arrays.asList(
                "Mountain", "Mountain", "Mountain", "Forest", "Forest", "Island", "Island");

        ScenarioLibrarySetup.reorderLibrary(p0, startingHand, Collections.emptyList());

        // Draw the opening hand
        p0.drawCards(7);

        CardCollectionView hand = p0.getCardsIn(ZoneType.Hand);
        assertEquals("Hand should have exactly 7 cards", 7, hand.size());

        Map<String, Long> handCounts = countByName(hand);
        assertEquals("Hand should contain 3 Mountains", 3L, (long) handCounts.getOrDefault("Mountain", 0L));
        assertEquals("Hand should contain 2 Forests",   2L, (long) handCounts.getOrDefault("Forest",   0L));
        assertEquals("Hand should contain 2 Islands",   2L, (long) handCounts.getOrDefault("Island",   0L));

        System.out.println("[PASS] testHandIsDrawnAfterReorder — hand: " + handToString(hand));
    }

    // -------------------------------------------------------------------------
    // 4. Missing card is skipped gracefully
    // -------------------------------------------------------------------------

    /**
     * If a named card is not present in the library, {@code reorderLibrary()} should
     * skip it silently (WARN-level log) and still correctly place all found cards.
     */
    @Test
    public void testMissingCardSkippedWithWarn() {
        Game game = initAndCreateGame();
        Player p0 = game.getPlayers().get(0);

        // Only add Mountains and Forests — no Island
        for (int i = 0; i < 8; i++) addCardToZone("Mountain", p0, ZoneType.Library);
        for (int i = 0; i < 7; i++) addCardToZone("Forest",   p0, ZoneType.Library);

        // Request Island in starting hand — it's not in the library
        List<String> startingHand = Arrays.asList(
                "Mountain", "Forest", "Island" /* missing! */, "Mountain", "Forest", "Mountain", "Forest");

        ScenarioLibrarySetup.reorderLibrary(p0, startingHand, Collections.emptyList());

        List<Card> library = new ArrayList<>(p0.getZone(ZoneType.Library).getCards());
        // All 15 original cards should still be there (Island was skipped, not added)
        assertEquals("Library should still have 15 cards (Island was skipped)", 15, library.size());

        // The 6 found cards must be at the front
        assertEquals("Library[0] should be Mountain", "Mountain", library.get(0).getName());
        assertEquals("Library[1] should be Forest",   "Forest",   library.get(1).getName());
        // Library[2] = Mountain (Island was skipped)
        assertEquals("Library[2] should be Mountain", "Mountain", library.get(2).getName());

        System.out.println("[PASS] testMissingCardSkippedWithWarn — Island skipped, 6/7 placed");
    }

    // -------------------------------------------------------------------------
    // 5. End-to-end via GameRules.setScenarioStartingHands() → Match.startGame()
    // -------------------------------------------------------------------------

    /**
     * Full integration test:
     * <ol>
     *   <li>Build two minimal Constructed decks (60 basic lands each).</li>
     *   <li>Configure {@link GameRules} with a scenario starting hand for P1.</li>
     *   <li>Start via {@link Match#startGame(Game, Runnable)} with a hook that
     *       captures the actual opening hands.</li>
     *   <li>Assert the captured P1 hand matches the scenario spec.</li>
     * </ol>
     *
     * <p>This verifies the full chain:
     * {@code GameRules} → {@code GameAction.startGame()} →
     * {@code ScenarioLibrarySetup.reorderLibraries()} → {@code drawCards()} →
     * {@code MulliganService} (AI keeps with ScenarioKeepMulligan) → hook captures hand.</p>
     */
    @Test
    public void testGameRulesScenarioHandEndToEnd() {
        initAndCreateGame(); // ensure FModel is initialized

        // Build decks with enough basic lands for the scenario to work
        Deck deck1 = buildBasicLandDeck("ScenarioTestDeck-P1",
                12, "Mountain", 12, "Forest", 12, "Island", 12, "Plains", 12, "Swamp");
        Deck deck2 = buildBasicLandDeck("ScenarioTestDeck-P2",
                60, "Mountain");

        List<RegisteredPlayer> players = new ArrayList<>();
        RegisteredPlayer rp1 = new RegisteredPlayer(deck1).setPlayer(new LobbyPlayerAi("TestP1-Scenario", null));
        RegisteredPlayer rp2 = new RegisteredPlayer(deck2).setPlayer(new LobbyPlayerAi("TestP2-Scenario", null));
        players.add(rp1);
        players.add(rp2);

        // Configure GameRules with scenario starting hand for P1
        GameRules rules = new GameRules(GameType.Constructed);
        rules.setAppliedVariants(EnumSet.of(GameType.Constructed));
        rules.setSimulationMode(true); // headless — no GUI, no replay file

        // P1 starting hand: 3 Mountains, 2 Forests, 1 Island, 1 Plains
        Map<String, List<String>> startingHands = new LinkedHashMap<>();
        List<String> p1Hand = Arrays.asList("Mountain", "Mountain", "Mountain", "Forest", "Forest", "Island", "Plains");
        startingHands.put("P1", p1Hand);
        rules.setScenarioStartingHands(startingHands);
        rules.setScenarioSkipMulligan(true); // AI keeps hand without mulligan

        Map<String, List<String>> firstDraws = new LinkedHashMap<>();
        firstDraws.put("P1", Arrays.asList("Swamp", "Mountain"));
        rules.setScenarioFirstDraws(firstDraws);

        Match match = new Match(rules, players, "ScenarioStartingHandTest");
        Game game = match.createGame();

        // Capture actual hands via the startGameHook (fires just before T1)
        Map<String, List<String>> capturedHands = new LinkedHashMap<>();

        try {
            match.startGame(game, () -> {
                List<Player> gamePlayers = game.getPlayers();
                for (int i = 0; i < gamePlayers.size(); i++) {
                    Player p = gamePlayers.get(i);
                    String pid = "P" + (i + 1);
                    List<String> hand = new ArrayList<>();
                    for (Card c : p.getCardsIn(ZoneType.Hand)) {
                        hand.add(c.getName());
                    }
                    capturedHands.put(pid, hand);
                    System.out.println("  Captured " + pid + " hand (" + hand.size() + "): " + hand);
                }
            });
        } catch (Exception e) {
            // Game may complete normally or be cut short — hand capture is what matters
            System.err.println("Game completed with exception (non-fatal for this test): " + e.getMessage());
        }

        // ── Assert P1 opening hand matches scenario spec ──────────────────────
        assertTrue("P1 hand should have been captured by hook", capturedHands.containsKey("P1"));
        List<String> actualP1Hand = capturedHands.get("P1");
        assertNotNull("P1 hand must not be null", actualP1Hand);
        assertEquals("P1 hand should have 7 cards", 7, actualP1Hand.size());

        Map<String, Long> actualCounts = actualP1Hand.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        Map<String, Long> expectedCounts = p1Hand.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        assertEquals("P1 hand card counts must match scenario spec", expectedCounts, actualCounts);

        System.out.println("[PASS] testGameRulesScenarioHandEndToEnd");
        System.out.println("  Expected: " + p1Hand);
        System.out.println("  Actual:   " + actualP1Hand);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Add {@code count} copies of each of Mountain, Forest, Island to the library. */
    private void populateLibraryWithBasicLands(Player p, int mountains, int forests, int islands) {
        for (int i = 0; i < mountains; i++) addCardToZone("Mountain", p, ZoneType.Library);
        for (int i = 0; i < forests;   i++) addCardToZone("Forest",   p, ZoneType.Library);
        for (int i = 0; i < islands;   i++) addCardToZone("Island",   p, ZoneType.Library);
    }

    /** Count cards in a collection by name. */
    private Map<String, Long> countByName(CardCollectionView cards) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Card c : cards) {
            counts.merge(c.getName(), 1L, Long::sum);
        }
        return counts;
    }

    /** One-line hand description for logging. */
    private String handToString(CardCollectionView hand) {
        List<String> names = new ArrayList<>();
        for (Card c : hand) names.add(c.getName());
        return names.toString();
    }

    /**
     * Build a minimal Constructed deck with the given basic-land distribution.
     * Arguments are alternating (count, name) pairs, e.g. {@code (12, "Mountain", 12, "Forest")}.
     */
    private static Deck buildBasicLandDeck(String name, Object... countNamePairs) {
        Deck deck = new Deck(name);
        for (int i = 0; i < countNamePairs.length; i += 2) {
            int count    = (Integer) countNamePairs[i];
            String cname = (String)  countNamePairs[i + 1];
            deck.getMain().add(cname, count);
        }
        return deck;
    }
}





