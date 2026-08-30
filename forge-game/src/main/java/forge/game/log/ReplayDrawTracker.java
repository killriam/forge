package forge.game.log;

import forge.game.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateful tracker for replay-mode draw ordering across mulligan rounds.
 *
 * <p>The {@link ReplayLibraryReorderer} sets up the library order before the
 * initial draw, but {@code AbstractMulligan.mulligan()} shuffles the library
 * randomly after returning the hand.  This class remembers how many draw
 * events have already been consumed (per player) and re-applies the correct
 * library order after each mulligan shuffle so that every new hand exactly
 * matches the recorded game.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   // In GameAction.startGame(), after ReplayLibraryReorderer.reorderLibraries():
 *   Map&lt;String, List&lt;String&gt;&gt; drawOrder = ReplayLibraryReorderer.parseDrawOrder(path);
 *   ReplayDrawTracker tracker = new ReplayDrawTracker(drawOrder);
 *   game.setReplayDrawTracker(tracker);
 *
 *   // In AbstractMulligan.mulligan(), after player.shuffle(null):
 *   ReplayDrawTracker tracker = player.getGame().getReplayDrawTracker();
 *   if (tracker != null) {
 *       tracker.onMulliganShuffle(player, toMulligan.size());
 *   }
 * </pre>
 */
public class ReplayDrawTracker {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayDrawTracker.class);

    /** Full ordered draw sequence per player ID ("P1", "P2", …). */
    private final Map<String, List<String>> drawOrder;

    /**
     * Number of draw-log entries already consumed per player.
     * Advances by {@code toMulligan.size()} on each mulligan round.
     */
    private final Map<String, Integer> consumedCounts;

    /**
     * @param drawOrder the full parsed draw order from the replay JSON,
     *                  keyed by player ID ("P1", "P2", …)
     */
    public ReplayDrawTracker(Map<String, List<String>> drawOrder) {
        this.drawOrder = drawOrder != null ? drawOrder : new HashMap<>();
        this.consumedCounts = new HashMap<>();
        for (String playerId : this.drawOrder.keySet()) {
            consumedCounts.put(playerId, 0);
        }
    }

    /**
     * Called from {@code AbstractMulligan.mulligan()} right after
     * {@code player.shuffle(null)}.
     *
     * <p>Advances the consumed-draw pointer by {@code cardsReturned} (the
     * number of cards that were just put back into the library) and then
     * re-orders the library so that the correct next hand will be drawn.</p>
     *
     * @param player       the player who is taking the mulligan
     * @param cardsReturned number of cards returned to the library
     *                      (equals the hand size before this mulligan)
     */
    public void onMulliganShuffle(Player player, int cardsReturned) {
        if (player.getGame().getRules() != null && player.getGame().getRules().isShuffleReplay()
                && (player.getGame().getPlayers().indexOf(player) == 0 || !player.isAI())) {
            return;
        }
        String playerId = getPlayerId(player);
        List<String> fullOrder = drawOrder.get(playerId);
        if (fullOrder == null || fullOrder.isEmpty()) {
            return;
        }

        int current = consumedCounts.getOrDefault(playerId, 0);
        int next = current + cardsReturned;
        consumedCounts.put(playerId, next);

        if (next >= fullOrder.size()) {
            LOG.debug("ReplayDrawTracker: pointer {} >= draw order size {} for {} — no reorder",
                    next, fullOrder.size(), playerId);
            return;
        }

        List<String> remaining = fullOrder.subList(next, fullOrder.size());
        LOG.debug("ReplayDrawTracker: reordering library for {} from draw index {} (remaining draws: {})",
                playerId, next, remaining.size());

        ReplayLibraryReorderer.reorderLibrary(player, remaining);
    }

    // -------------------------------------------------------------------------

    private static String getPlayerId(Player player) {
        return "P" + (player.getGame().getPlayers().indexOf(player) + 1);
    }
}

