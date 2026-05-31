package forge.game.scenario;

import forge.card.CardDb;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.net.HeadlessGuiDesktop;
import forge.net.TestUtils;
import forge.player.GamePlayerUtil;
import forge.view.TimeLimitedCodeBlock;

import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Integration test for {@link forge.game.log.ScenarioLibrarySetup}.
 *
 * <p>Verifies that the scenario starting-hand system correctly places the
 * predefined cards at the front of the library so that, after normal
 * {@code drawCards()}, the opening hand exactly matches the configured
 * {@code starting_hand} list.</p>
 *
 * <p>This test runs a full headless game in the same way the CLI
 * {@code sim -s scenario.json} command does — via {@link Match#startGame(Game, Runnable)}.
 * It is tagged as a stress test because it requires FModel (card data) to be
 * loaded, which takes several seconds.</p>
 *
 * <p>Run with:</p>
 * <pre>
 * mvn -pl forge-gui-desktop -am verify \
 *   -Dtest="ScenarioStartingHandIntegrationTest" \
 *   -Dsurefire.failIfNoSpecifiedTests=false \
 *   -Drun.stress.tests=true
 * </pre>
 */
public class ScenarioStartingHandIntegrationTest {

    private static final int GAME_TIMEOUT_SECONDS = 120;

    // ── Opening hand we want to verify ───────────────────────────────────────
    // 7 Mountains → all drawn as the opening hand (library positions 0–6)
    private static final List<String> EXPECTED_HAND = Collections.unmodifiableList(Arrays.asList(
            "Mountain", "Mountain", "Mountain", "Mountain",
            "Mountain", "Mountain", "Mountain"
    ));

    // 3 Islands → drawn as turns 1, 2, 3 after the opening hand (library positions 7–9)
    private static final List<String> EXPECTED_FIRST_DRAWS = Collections.unmodifiableList(Arrays.asList(
            "Island", "Island", "Island"
    ));

    // ── One-time FModel setup ─────────────────────────────────────────────────

    @BeforeClass
    public static void setUp() {
        TestUtils.ensureFModelInitialized();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test: opening hand matches ScenarioLibrarySetup reorder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full-game integration test: verify that P1's opening hand after game start
     * exactly matches the {@code EXPECTED_HAND} list configured via
     * {@link GameRules#setScenarioStartingHands(Map)}.
     *
     * <p>Requires card data → tagged as stress test.</p>
     */
    @Test
    public void testOpeningHandMatchesScenarioConfig() {
        if (!"true".equalsIgnoreCase(System.getProperty("run.stress.tests"))) {
            throw new SkipException("Stress tests skipped. Use -Drun.stress.tests=true to run.");
        }

        // ── 1. Build decks ────────────────────────────────────────────────────
        // P1: 20 Mountain + 20 Island + 20 Forest → 60 total
        //     Scenario will place 7 Mountains first, then 3 Islands on top of the rest.
        Deck p1Deck = buildMixedBasicsDeck("P1-ScenarioTest", 20, 20, 20);

        // P2: simple opponent deck, no scenario constraints
        Deck p2Deck = buildMixedBasicsDeck("P2-Opponent", 20, 20, 20);

        // ── 2. Set up GameRules with scenario config ──────────────────────────
        GameRules rules = new GameRules(GameType.Constructed);
        rules.setAppliedVariants(EnumSet.of(GameType.Constructed));
        rules.setSimulationMode(true);
        rules.setSimTimeout(GAME_TIMEOUT_SECONDS);

        // P1 gets a defined starting hand + first draws
        Map<String, List<String>> startingHands = new LinkedHashMap<>();
        startingHands.put("P1", EXPECTED_HAND);

        Map<String, List<String>> firstDraws = new LinkedHashMap<>();
        firstDraws.put("P1", EXPECTED_FIRST_DRAWS);

        rules.setScenarioStartingHands(startingHands);
        rules.setScenarioFirstDraws(firstDraws);
        // Skip AI mulligan so the reordered hand is kept intact
        rules.setScenarioSkipMulligan(true);

        // ── 3. Register players ───────────────────────────────────────────────
        RegisteredPlayer rp1 = new RegisteredPlayer(p1Deck);
        rp1.setPlayer(GamePlayerUtil.createAiPlayer("P1-ScenarioTest", 0));

        RegisteredPlayer rp2 = new RegisteredPlayer(p2Deck);
        rp2.setPlayer(GamePlayerUtil.createAiPlayer("P2-Opponent", 1));

        List<RegisteredPlayer> registeredPlayers = Arrays.asList(rp1, rp2);

        // ── 4. Run the game, capture the opening hand via startGameHook ───────
        Match mc = new Match(rules, registeredPlayers, "ScenarioHandTest");
        final Game g1 = mc.createGame();

        // Holds captured hand names; populated inside the start-game hook
        final List<String> capturedP1Hand = new java.util.ArrayList<>();

        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> {
                mc.startGame(g1, () -> {
                    // Hook fires just before the first-turn phase loop (mulligans done).
                    // Capture every card currently in P1's hand.
                    Player p1 = g1.getPlayers().get(0);
                    for (Card c : p1.getCardsIn(ZoneType.Hand)) {
                        capturedP1Hand.add(c.getName());
                    }
                    System.out.println("[ScenarioTest] P1 opening hand: " + capturedP1Hand);
                });
            }, GAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("[ScenarioTest] Game timed out (treat as draw) — hand still captured");
        } catch (Exception | StackOverflowError e) {
            Assert.fail("Game threw an unexpected exception: " + e.getMessage(), e instanceof Exception ? (Exception) e : null);
        } finally {
            if (!g1.isGameOver()) {
                g1.setGameOver(GameEndReason.Draw);
            }
        }

        // ── 5. Assert opening hand ─────────────────────────────────────────────
        Assert.assertFalse(capturedP1Hand.isEmpty(),
                "Start-game hook must have fired — capturedP1Hand must not be empty");

        Assert.assertEquals(capturedP1Hand.size(), EXPECTED_HAND.size(),
                "P1 opening hand size does not match. Actual hand: " + capturedP1Hand);

        // Multiset comparison (order-insensitive — ScenarioLibrarySetup guarantees card
        // membership but drawCards ordering may be reversed vs library index 0)
        Map<String, Long> expectedCounts = EXPECTED_HAND.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        Map<String, Long> actualCounts = capturedP1Hand.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        Assert.assertEquals(actualCounts, expectedCounts,
                "P1 opening hand card composition mismatch.\n"
                        + "  Expected: " + EXPECTED_HAND + "\n"
                        + "  Actual:   " + capturedP1Hand);

        System.out.println("[ScenarioTest] PASS ✓ Opening hand matches scenario config.");
    }

    /**
     * Verifies that the first {@value #FIRST_DRAW_CHECK_COUNT} cards drawn from
     * the library (the turn-by-turn draws) match {@code EXPECTED_FIRST_DRAWS}.
     *
     * <p>We verify this by inspecting the library order directly BEFORE the game
     * starts (after {@link forge.game.log.ScenarioLibrarySetup#reorderLibraries}
     * has run but before any draws).</p>
     *
     * <p>Requires card data → tagged as stress test.</p>
     */
    private static final int FIRST_DRAW_CHECK_COUNT = 3;

    @Test
    public void testFirstDrawsAreAtCorrectLibraryPosition() {
        if (!"true".equalsIgnoreCase(System.getProperty("run.stress.tests"))) {
            throw new SkipException("Stress tests skipped. Use -Drun.stress.tests=true to run.");
        }

        Deck p1Deck = buildMixedBasicsDeck("P1-DrawOrderTest", 20, 20, 20);
        Deck p2Deck = buildMixedBasicsDeck("P2-Opponent2", 20, 20, 20);

        GameRules rules = new GameRules(GameType.Constructed);
        rules.setAppliedVariants(EnumSet.of(GameType.Constructed));
        rules.setSimulationMode(true);
        rules.setSimTimeout(GAME_TIMEOUT_SECONDS);

        Map<String, List<String>> startingHands = new LinkedHashMap<>();
        startingHands.put("P1", EXPECTED_HAND);

        Map<String, List<String>> firstDraws = new LinkedHashMap<>();
        firstDraws.put("P1", EXPECTED_FIRST_DRAWS);

        rules.setScenarioStartingHands(startingHands);
        rules.setScenarioFirstDraws(firstDraws);
        rules.setScenarioSkipMulligan(true);

        RegisteredPlayer rp1 = new RegisteredPlayer(p1Deck);
        rp1.setPlayer(GamePlayerUtil.createAiPlayer("P1-DrawOrderTest", 0));
        RegisteredPlayer rp2 = new RegisteredPlayer(p2Deck);
        rp2.setPlayer(GamePlayerUtil.createAiPlayer("P2-Opponent2", 1));

        Match mc = new Match(rules, Arrays.asList(rp1, rp2), "DrawOrderTest");
        final Game g1 = mc.createGame();

        // Capture the library-top cards AFTER reorder, BEFORE hand draw.
        // We do this in a startGameHook — at this point the opening hand has been
        // drawn already. Instead we inspect the remaining library top to see if
        // EXPECTED_FIRST_DRAWS appear in order.
        final List<String> capturedLibraryTop = new java.util.ArrayList<>();

        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> {
                mc.startGame(g1, () -> {
                    Player p1 = g1.getPlayers().get(0);
                    // Library top cards are at index 0 (drawn next turn)
                    List<Card> lib = p1.getZone(ZoneType.Library).getCards().threadSafeIterable()
                            .stream().collect(Collectors.toList());
                    int checkCount = Math.min(FIRST_DRAW_CHECK_COUNT, lib.size());
                    for (int i = 0; i < checkCount; i++) {
                        capturedLibraryTop.add(lib.get(i).getName());
                    }
                    System.out.println("[ScenarioTest] P1 library top " + checkCount + ": " + capturedLibraryTop);
                });
            }, GAME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("[ScenarioTest] Game timed out — library snapshot still captured");
        } catch (Exception | StackOverflowError e) {
            Assert.fail("Game threw an unexpected exception: " + e.getMessage());
        } finally {
            if (!g1.isGameOver()) {
                g1.setGameOver(GameEndReason.Draw);
            }
        }

        Assert.assertFalse(capturedLibraryTop.isEmpty(),
                "Library top capture must not be empty — start-game hook must have fired");

        Assert.assertEquals(capturedLibraryTop.size(), FIRST_DRAW_CHECK_COUNT,
                "Library snapshot should contain " + FIRST_DRAW_CHECK_COUNT + " cards");

        for (int i = 0; i < FIRST_DRAW_CHECK_COUNT; i++) {
            String expected = EXPECTED_FIRST_DRAWS.get(i);
            String actual = capturedLibraryTop.get(i);
            Assert.assertEquals(actual, expected,
                    "Library position " + i + " after opening hand draw: "
                            + "expected '" + expected + "', got '" + actual + "'.\n"
                            + "Full library top: " + capturedLibraryTop);
        }

        System.out.println("[ScenarioTest] PASS ✓ First draws are at correct library positions.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: build a 60-card constructed deck with mixed basic lands
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build a legal 60-card constructed deck with {@code mountains} Mountains,
     * {@code islands} Islands, and {@code forests} Forests.
     */
    private static Deck buildMixedBasicsDeck(String name, int mountains, int islands, int forests) {
        CardDb cardDb = FModel.getMagicDb().getCommonCards();

        PaperCard mountain = cardDb.getCard("Mountain");
        PaperCard island = cardDb.getCard("Island");
        PaperCard forest = cardDb.getCard("Forest");

        Assert.assertNotNull(mountain, "Mountain must exist in card database");
        Assert.assertNotNull(island, "Island must exist in card database");
        Assert.assertNotNull(forest, "Forest must exist in card database");

        Deck deck = new Deck(name);
        for (int i = 0; i < mountains; i++) deck.getMain().add(mountain);
        for (int i = 0; i < islands; i++) deck.getMain().add(island);
        for (int i = 0; i < forests; i++) deck.getMain().add(forest);

        return deck;
    }
}

