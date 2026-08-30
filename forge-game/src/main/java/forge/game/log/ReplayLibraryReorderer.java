package forge.game.log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reorders players' libraries to match the draw order recorded in a replay JSON log.
 *
 * This enables deterministic game replay: after shuffling, the library is reordered so
 * that cards will be drawn in exactly the same sequence as in the recorded game.
 *
 * The replay JSON format uses L1 events with type "DRAW", where data contains:
 *   - "card_name": the name of the drawn card
 *   - "from": "P1:library" (identifies the player)
 */
public class ReplayLibraryReorderer {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayLibraryReorderer.class);

    /**
     * Parse draw order from a replay JSON file.
     *
     * @param replayJsonPath Path to the replay JSON file
     * @return Map of player ID ("P1", "P2", ...) to ordered list of card names drawn
     */
    public static Map<String, List<String>> parseDrawOrder(String replayJsonPath) throws IOException {
        Map<String, List<String>> drawOrder = new LinkedHashMap<>();

        try (Reader reader = new FileReader(replayJsonPath)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOG.warn("Replay file is not a JSON object: {}", replayJsonPath);
                return drawOrder;
            }

            JsonObject rootObj = root.getAsJsonObject();

            // Events are stored under "events" key (spec v1.5.0+)
            JsonArray events = null;
            if (rootObj.has("events") && rootObj.get("events").isJsonArray()) {
                events = rootObj.getAsJsonArray("events");
            } else if (rootObj.has("log_l1") && rootObj.get("log_l1").isJsonArray()) {
                // Fallback for older format
                events = rootObj.getAsJsonArray("log_l1");
            }

            if (events == null) {
                LOG.warn("No events array found in replay file: {}", replayJsonPath);
                return drawOrder;
            }

            for (JsonElement eventElem : events) {
                if (!eventElem.isJsonObject()) {
                    continue;
                }
                JsonObject event = eventElem.getAsJsonObject();

                // Filter for DRAW events
                String type = event.has("type") ? event.get("type").getAsString() : null;
                if (!"DRAW".equals(type)) {
                    continue;
                }

                JsonObject data = event.has("data") && event.get("data").isJsonObject()
                        ? event.getAsJsonObject("data") : null;
                if (data == null) {
                    continue;
                }

                // Extract player ID from "from" field (e.g., "P1:library")
                String from = data.has("from") ? data.get("from").getAsString() : null;
                String playerId = null;
                if (from != null && from.contains(":")) {
                    playerId = from.substring(0, from.indexOf(':'));
                }

                // Extract card name
                String cardName = data.has("card_name") ? data.get("card_name").getAsString() : null;

                if (playerId != null && cardName != null) {
                    drawOrder.computeIfAbsent(playerId, k -> new ArrayList<>()).add(cardName);
                }
            }
        }

        LOG.info("Parsed draw order from {}: {} players, total draws: {}",
                replayJsonPath,
                drawOrder.size(),
                drawOrder.values().stream().mapToInt(List::size).sum());

        return drawOrder;
    }

    /**
     * Reorder a player's library so that cards matching the draw order appear on top
     * in the correct sequence.
     *
     * @param player The player whose library to reorder
     * @param drawOrderCardNames Ordered list of card names as they should be drawn
     */
    public static void reorderLibrary(Player player, List<String> drawOrderCardNames) {
        Zone library = player.getZone(ZoneType.Library);
        List<Card> currentCards = new ArrayList<>();
        for (Card c : library.getCards().threadSafeIterable()) {
            currentCards.add(c);
        }

        if (currentCards.isEmpty() || drawOrderCardNames.isEmpty()) {
            return;
        }

        List<Card> reordered = new ArrayList<>();
        List<Card> remaining = new ArrayList<>(currentCards);

        // Place cards matching draw order at the top, in sequence
        for (String cardName : drawOrderCardNames) {
            Card matched = null;
            for (Card c : remaining) {
                if (c.getName().equals(cardName)) {
                    matched = c;
                    break;
                }
            }
            if (matched != null) {
                reordered.add(matched);
                remaining.remove(matched);
            } else {
                LOG.debug("Card '{}' from draw order not found in {}'s library (may be in opening hand or elsewhere)",
                        cardName, player.getName());
            }
        }

        // Append all non-drawn cards after the draw-order cards
        reordered.addAll(remaining);

        // Apply the new order
        library.setCards(reordered);
        LOG.info("Reordered {}'s library: {} cards matched from draw order, {} remaining",
                player.getName(), reordered.size() - remaining.size(), remaining.size());
    }

    /**
     * Reorder all players' libraries in the game based on a replay log.
     *
     * Player ID mapping follows ReplayNotationExporter convention:
     * P1 = first player in game.getPlayers(), P2 = second, etc.
     *
     * @param game The game whose players' libraries to reorder
     * @param replayJsonPath Path to the replay JSON file
     */
    public static void reorderLibraries(Game game, String replayJsonPath) {
        try {
            Map<String, List<String>> drawOrder = parseDrawOrder(replayJsonPath);
            if (drawOrder.isEmpty()) {
                LOG.warn("No draw order found in replay log: {}", replayJsonPath);
                return;
            }

            boolean isShuffleReplay = game.getRules() != null && game.getRules().isShuffleReplay();
            List<Player> players = game.getPlayers();
            for (int i = 0; i < players.size(); i++) {
                Player p = players.get(i);
                // In Shuffle Replay mode, the human player plays with a normally shuffled deck
                if (isShuffleReplay && (i == 0 || !p.isAI())) {
                    LOG.info("Shuffle Replay: keeping {}'s library shuffled normally", p.getName());
                    continue;
                }
                String playerId = "P" + (i + 1);
                List<String> playerDrawOrder = drawOrder.get(playerId);
                if (playerDrawOrder != null && !playerDrawOrder.isEmpty()) {
                    reorderLibrary(p, playerDrawOrder);
                } else {
                    LOG.debug("No draw order for {} in replay log", playerId);
                }
            }

            LOG.info("Library reorder complete for {} players from replay: {} (shuffleReplay={})",
                    players.size(), replayJsonPath, isShuffleReplay);
        } catch (IOException e) {
            LOG.error("Failed to load replay log for library reordering: {}", replayJsonPath, e);
        }
    }
}


