package forge.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.StaticData;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a replay JSON log file and extracts metadata, deck lists, and player info.
 * Used by the Replay Game UI to reconstruct decks and start interactive games
 * with the same library order as the original game.
 */
public class ReplayLogParser {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayLogParser.class);

    private final File replayFile;
    private JsonObject root;

    // Parsed metadata
    private String gameId;
    private String timestamp;
    private String gameType;
    private String winner;
    private Integer turns;
    private Integer durationSeconds;
    private boolean replayed = false;
    private String replayedAt = null;
    private String replayedWinner = null;
    private String replayedOutcome = null;
    private Integer replayedTurns = null;
    private String replayedMode = null;
    private final Map<String, PlayerInfo> players = new LinkedHashMap<>();
    /** v1.3.0: player ID who takes the first turn (from game_start.starting_player). */
    private String startingPlayer = null;
    // v1.7.0: scenario mode
    private String mode = "full_game";
    private ScenarioInfo scenarioInfo;
    private boolean decksReconstructed = false;

    public ReplayLogParser(File replayFile) {
        this.replayFile = replayFile;
    }

    /**
     * Parse the replay JSON file and extract all metadata.
     * @return true if parsing succeeded
     */
    public boolean parse() {
        LOG.debug("Parsing replay file: {}", replayFile);
        try (Reader reader = new FileReader(replayFile)) {
            JsonElement rootElem = JsonParser.parseReader(reader);
            if (!rootElem.isJsonObject()) {
                LOG.warn("Replay file is not a JSON object: {}", replayFile);
                return false;
            }
            root = rootElem.getAsJsonObject();

            // Validate format
            String format = getStringField(root, "format");
            LOG.debug("Replay format: {}", format);
            if (!"mtg-replay".equals(format)) {
                LOG.warn("Not a valid mtg-replay file (format='{}'): {}", format, replayFile);
                return false;
            }

            // Parse metadata
            if (root.has("meta") && root.get("meta").isJsonObject()) {
                JsonObject meta = root.getAsJsonObject("meta");
                gameId = getStringField(meta, "game_id");
                timestamp = getStringField(meta, "timestamp");
                gameType = getStringField(meta, "game_type");
                winner = getStringField(meta, "winner");
                startingPlayer = getStringField(meta, "starting_player");

                if (meta.has("turns") && !meta.get("turns").isJsonNull()) {
                    turns = meta.get("turns").getAsInt();
                }
                if (meta.has("duration_seconds") && !meta.get("duration_seconds").isJsonNull()) {
                    durationSeconds = meta.get("duration_seconds").getAsInt();
                }

                // Check if this replay has already been used to start a game
                if (meta.has("replayed_at") && !meta.get("replayed_at").isJsonNull()) {
                    replayed = true;
                    replayedAt = getStringField(meta, "replayed_at");
                }
                if (meta.has("replayed_winner") && !meta.get("replayed_winner").isJsonNull()) {
                    replayedWinner = getStringField(meta, "replayed_winner");
                }
                if (meta.has("replayed_outcome") && !meta.get("replayed_outcome").isJsonNull()) {
                    replayedOutcome = getStringField(meta, "replayed_outcome");
                }
                if (meta.has("replayed_turns") && !meta.get("replayed_turns").isJsonNull()) {
                    replayedTurns = meta.get("replayed_turns").getAsInt();
                }
                if (meta.has("replayed_mode") && !meta.get("replayed_mode").isJsonNull()) {
                    replayedMode = getStringField(meta, "replayed_mode");
                }

                // Parse player metadata
                if (meta.has("players") && meta.get("players").isJsonObject()) {
                    JsonObject playersObj = meta.getAsJsonObject("players");
                    for (Map.Entry<String, JsonElement> entry : playersObj.entrySet()) {
                        String playerId = entry.getKey();
                        if (entry.getValue().isJsonObject()) {
                            JsonObject pObj = entry.getValue().getAsJsonObject();
                            PlayerInfo info = new PlayerInfo();
                            info.playerId = playerId;
                            info.name = getStringField(pObj, "name");
                            info.deckName = getStringField(pObj, "deck_name");
                            info.deckHash = getStringField(pObj, "deck_hash");
                            info.isAi = pObj.has("is_ai") && !pObj.get("is_ai").isJsonNull()
                                    && pObj.get("is_ai").getAsBoolean();
                            info.startingLife = pObj.has("starting_life") && !pObj.get("starting_life").isJsonNull()
                                    ? pObj.get("starting_life").getAsInt() : 20;
                            info.playerType = getStringField(pObj, "player_type");
                            // v1.9.0: parse team number for team games
                            if (pObj.has("team") && !pObj.get("team").isJsonNull()) {
                                info.team = pObj.get("team").getAsInt();
                            }
                            players.put(playerId, info);
                        }
                    }
                }
            }

            // v1.7.0: read mode field (defaults to "full_game" when absent)
            String parsedMode = getStringField(root, "mode");
            if (parsedMode != null) {
                this.mode = parsedMode;
            }

            // v1.7.0: parse scenario block if present
            if ("scenario".equals(this.mode) && root.has("scenario") && root.get("scenario").isJsonObject()) {
                JsonObject sc = root.getAsJsonObject("scenario");
                ScenarioInfo si = new ScenarioInfo();
                si.id          = getStringField(sc, "id");
                si.type        = getStringField(sc, "type");
                // "name" is the field mtg-replay-notation's DecklistScenario spec (§6.4) uses;
                // Forge's own scenario files predate that and use "title" - accept either,
                // preferring "name" when a file somehow has both. si.title is kept in sync as a
                // read-only alias for any code that still reads it directly.
                si.name        = getStringField(sc, "name");
                if (si.name == null) si.name = getStringField(sc, "title");
                si.title       = si.name;
                si.deckId      = getStringField(sc, "deck_id");
                si.description = getStringField(sc, "description");
                si.question    = getStringField(sc, "question");
                si.answer      = getStringField(sc, "answer");
                if (sc.has("ruling_references") && sc.get("ruling_references").isJsonArray()) {
                    for (JsonElement el : sc.getAsJsonArray("ruling_references")) {
                        si.rulingReferences.add(el.getAsString());
                    }
                }
                if (sc.has("tags") && sc.get("tags").isJsonArray()) {
                    for (JsonElement el : sc.getAsJsonArray("tags")) {
                        si.tags.add(el.getAsString());
                    }
                }
                if (sc.has("player_count") && !sc.get("player_count").isJsonNull()) {
                    si.playerCount = sc.get("player_count").getAsInt();
                }
                if (sc.has("game_state") && sc.get("game_state").isJsonArray()) {
                    for (JsonElement el : sc.getAsJsonArray("game_state")) {
                        si.gameState.add(el.getAsString());
                    }
                }
                // v1.8.0 (fork): parse "players" block for structured starting hand / draws / commanders
                if (sc.has("players") && sc.get("players").isJsonObject()) {
                    JsonObject playersObj = sc.getAsJsonObject("players");
                    for (Map.Entry<String, JsonElement> pe : playersObj.entrySet()) {
                        String playerId = pe.getKey(); // "P1", "P2", …
                        if (!pe.getValue().isJsonObject()) continue;
                        JsonObject pp = pe.getValue().getAsJsonObject();

                        if (pp.has("starting_hand") && pp.get("starting_hand").isJsonArray()) {
                            List<String> hand = new ArrayList<>();
                            for (JsonElement el : pp.getAsJsonArray("starting_hand")) {
                                hand.add(el.getAsString());
                            }
                            si.playerStartingHands.put(playerId, hand);
                        }
                        if (pp.has("first_draws") && pp.get("first_draws").isJsonArray()) {
                            List<String> draws = new ArrayList<>();
                            for (JsonElement el : pp.getAsJsonArray("first_draws")) {
                                draws.add(el.getAsString());
                            }
                            si.playerFirstDraws.put(playerId, draws);
                        }
                        if (pp.has("commanders") && pp.get("commanders").isJsonArray()) {
                            List<String> cmds = new ArrayList<>();
                            for (JsonElement el : pp.getAsJsonArray("commanders")) {
                                cmds.add(el.getAsString());
                            }
                            si.playerCommanders.put(playerId, cmds);
                        }
                        if (pp.has("battlefield") && pp.get("battlefield").isJsonArray()) {
                            List<String> bf = new ArrayList<>();
                            for (JsonElement el : pp.getAsJsonArray("battlefield")) {
                                bf.add(el.getAsString());
                            }
                            si.playerBattlefield.put(playerId, bf);
                        }
                        if (pp.has("starting_life") && !pp.get("starting_life").isJsonNull()) {
                            si.playerStartingLife.put(playerId, pp.get("starting_life").getAsInt());
                        }
                    }
                }
                // v1.8.0 (fork): top-level "events" — forced play sequence, keyed by raw
                // player id ("P1", "P2", …) as written by mamo-Connector. Callers translate
                // these ids to the actual runtime lobby name they assigned to that seat
                // (see ScenarioInfo#buildForcedPlaySequenceForLobbyNames) — the JSON itself
                // never carries a lobby-name string, avoiding the whole class of
                // filename/metadata/date-convention mismatches that string would be prone to.
                if (root.has("events") && root.get("events").isJsonArray()) {
                    si.playerForcedSequence.putAll(parseForcedSequenceEvents(root.getAsJsonArray("events")));
                    si.playerForcedSacrifice.putAll(parseForcedSequenceSacrifice(root.getAsJsonArray("events")));
                }
                this.scenarioInfo = si;
            }

            // Deck reconstruction (real card-database lookups per card, per player) is deferred
            // until something actually needs a Deck object - see ensureDecksReconstructed().
            // Building a list entry (Game Recap, Investigate Scenarios) only ever needs the
            // metadata already parsed above (deckName, timestamp, turns, winner, ...); with a
            // large gamelogs folder, reconstructing every file's decks just to list them was a
            // multi-second startup/scan cost for work nobody was about to look at.

            LOG.info("Parsed replay: {} players, game_type={}, turns={}, winner={}",
                    players.size(), gameType, turns, winner);
            LOG.debug("Parse complete");
            return true;
        } catch (IOException e) {
            LOG.error("Failed to parse replay file: {}", replayFile, e);
            return false;
        } catch (Exception e) {
            LOG.error("Error parsing replay file: {}", replayFile, e);
            return false;
        }
    }

    /**
     * Reconstructs each player's {@link Deck} (real card-database lookup per card) if it hasn't
     * been done yet for this parser instance. No-op on repeat calls. {@link #parse()} no longer
     * does this automatically - call this explicitly before reading {@link PlayerInfo#deck},
     * typically right before actually launching a replay/scenario, or when a user has selected
     * one specific file to preview. Building a list of many files should never need this.
     */
    public synchronized void ensureDecksReconstructed() {
        if (decksReconstructed) return;
        if (root == null) return;
        LOG.debug("Starting deck reconstruction");
        reconstructDecks();
        decksReconstructed = true;
    }

    /**
     * Reconstructs deck lists from initial_state.objects and card_index.
     * Each object has an owner (P1, P2, ...), a zone (PX:library, PX:hand, PX:command),
     * and a cardRef (card name).
     */
    private void reconstructDecks() {
        if (root == null) {
            LOG.warn("reconstructDecks called but root is null");
            return;
        }

        // Build card_index lookup: cardId -> cardName
        Map<String, String> cardIndex = new LinkedHashMap<>();
        if (root.has("card_index") && root.get("card_index").isJsonObject()) {
            JsonObject ci = root.getAsJsonObject("card_index");
            for (Map.Entry<String, JsonElement> entry : ci.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject cardObj = entry.getValue().getAsJsonObject();
                    String name = getStringField(cardObj, "name");
                    if (name != null) {
                        cardIndex.put(entry.getKey(), name);
                    }
                }
            }
        }

        // Parse initial_state.objects for card ownership and zones
        if (!root.has("initial_state") || !root.get("initial_state").isJsonObject()) {
            LOG.debug("No initial_state found — falling back to card_index + events");
            reconstructDecksFromEvents(cardIndex);
            return;
        }

        JsonObject initialState = root.getAsJsonObject("initial_state");
        if (!initialState.has("objects") || !initialState.get("objects").isJsonObject()) {
            LOG.debug("No objects in initial_state — falling back to card_index + events");
            reconstructDecksFromEvents(cardIndex);
            return;
        }

        JsonObject objects = initialState.getAsJsonObject("objects");

        // Per player: collect card names for main deck and command zone
        Map<String, List<String>> playerMainCards = new LinkedHashMap<>();
        Map<String, List<String>> playerCommandCards = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject obj = entry.getValue().getAsJsonObject();

            String cardRef = getStringField(obj, "cardRef");
            if (cardRef == null) {
                // Try card_index fallback
                cardRef = cardIndex.get(entry.getKey());
            }
            if (cardRef == null) continue;

            String owner = getStringField(obj, "owner");
            String zone = getStringField(obj, "zone");
            if (owner == null && zone != null && zone.contains(":")) {
                owner = zone.substring(0, zone.indexOf(':'));
            }
            if (owner == null) continue;

            if (zone != null && zone.endsWith(":command")) {
                playerCommandCards.computeIfAbsent(owner, k -> new ArrayList<>()).add(cardRef);
            } else {
                // library, hand — all are part of the main deck
                playerMainCards.computeIfAbsent(owner, k -> new ArrayList<>()).add(cardRef);
            }
        }

        // Build decks for each player
        for (Map.Entry<String, PlayerInfo> entry : players.entrySet()) {
            String playerId = entry.getKey();
            PlayerInfo info = entry.getValue();

            List<String> mainCardNames = playerMainCards.getOrDefault(playerId, new ArrayList<>());
            List<String> commandCardNames = playerCommandCards.getOrDefault(playerId, new ArrayList<>());

            Deck deck = buildDeck(info.deckName != null ? info.deckName : "Replay Deck",
                    mainCardNames, commandCardNames);
            info.deck = deck;

            LOG.info("Reconstructed deck for {} ({}): {} main, {} command cards",
                    playerId, info.name, mainCardNames.size(), commandCardNames.size());
        }
    }

    /**
     * Fallback: reconstruct decks from card_index and DRAW events only.
     * Used when initial_state has no objects (older format).
     */
    private void reconstructDecksFromEvents(Map<String, String> cardIndex) {
        // Collect all card names per player from DRAW events and card_index zones
        Map<String, List<String>> playerCards = new LinkedHashMap<>();

        JsonArray events = null;
        if (root.has("events") && root.get("events").isJsonArray()) {
            events = root.getAsJsonArray("events");
        } else if (root.has("log_l1") && root.get("log_l1").isJsonArray()) {
            events = root.getAsJsonArray("log_l1");
        }

        if (events != null) {
            for (JsonElement eventElem : events) {
                if (!eventElem.isJsonObject()) continue;
                JsonObject event = eventElem.getAsJsonObject();
                String type = getStringField(event, "type");
                if (!"DRAW".equals(type)) continue;

                JsonObject data = event.has("data") && event.get("data").isJsonObject()
                        ? event.getAsJsonObject("data") : null;
                if (data == null) continue;

                String cardName = getStringField(data, "card_name");
                String from = getStringField(data, "from");
                String playerId = null;

                if (from != null && from.contains(":")) {
                    playerId = from.substring(0, from.indexOf(':'));
                }
                // Alternative: "player" field
                if (playerId == null) {
                    playerId = getStringField(data, "player");
                }

                if (playerId != null && cardName != null) {
                    playerCards.computeIfAbsent(playerId, k -> new ArrayList<>()).add(cardName);
                }
            }
        }

        // Also include cards from card_index that we can map to players via initial_state zones
        if (root.has("initial_state") && root.get("initial_state").isJsonObject()) {
            JsonObject initialState = root.getAsJsonObject("initial_state");
            if (initialState.has("zones") && initialState.get("zones").isJsonObject()) {
                JsonObject zones = initialState.getAsJsonObject("zones");
                for (Map.Entry<String, JsonElement> zoneEntry : zones.entrySet()) {
                    String zoneName = zoneEntry.getKey();
                    if (!zoneName.contains(":")) continue;
                    String playerId = zoneName.substring(0, zoneName.indexOf(':'));
                    String zoneType = zoneName.substring(zoneName.indexOf(':') + 1);

                    if (!"library".equals(zoneType) && !"hand".equals(zoneType)) continue;

                    if (zoneEntry.getValue().isJsonArray()) {
                        for (JsonElement cardElem : zoneEntry.getValue().getAsJsonArray()) {
                            String cardId = cardElem.getAsString();
                            String cardName = cardIndex.get(cardId);
                            if (cardName != null) {
                                playerCards.computeIfAbsent(playerId, k -> new ArrayList<>()).add(cardName);
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, PlayerInfo> entry : players.entrySet()) {
            String playerId = entry.getKey();
            PlayerInfo info = entry.getValue();
            List<String> cardNames = playerCards.getOrDefault(playerId, new ArrayList<>());
            info.deck = buildDeck(info.deckName != null ? info.deckName : "Replay Deck",
                    cardNames, new ArrayList<>());
        }
    }

    /**
     * Build a Deck object from card names.
     */
    private Deck buildDeck(String deckName, List<String> mainCardNames, List<String> commandCardNames) {
        Deck deck = new Deck(deckName);
        CardPool mainPool = deck.getOrCreate(DeckSection.Main);

        for (String cardName : mainCardNames) {
                if (isSystemCard(cardName)) {
                    LOG.debug("Skipping system/internal card in main deck: '{}'", cardName);
                    continue;
                }
                PaperCard pc = findCard(cardName);
                if (pc != null) {
                    mainPool.add(pc);
                } else {
                    LOG.warn("Card not found in database: '{}' — skipping", cardName);
                }
            }

        if (!commandCardNames.isEmpty()) {
            CardPool commandPool = deck.getOrCreate(DeckSection.Commander);
            for (String cardName : commandCardNames) {
                if (isSystemCard(cardName)) {
                    LOG.debug("Skipping system/internal card in command zone: '{}'", cardName);
                    continue;
                }
                PaperCard pc = findCard(cardName);
                if (pc != null) {
                    commandPool.add(pc);
                } else {
                    LOG.warn("Commander card not found in database: '{}' — skipping", cardName);
                }
            }
        }

        return deck;
    }

    /**
     * Returns true for internal game-engine cards that don't exist in the card database
     * (e.g. Commander Effect, Emblem tokens, Companion Effect placeholders).
     */
    private static boolean isSystemCard(String cardName) {
        if (cardName == null) return true;
        return cardName.equals("Commander Effect")
                || cardName.endsWith("'s Companion Effect")
                || cardName.equals("Puzzle Goal")
                || cardName.startsWith("Emblem -");
    }

    /**
     * Find a PaperCard by name from the card database.
     */
    private PaperCard findCard(String cardName) {
        if (cardName == null || cardName.isEmpty()) return null;
        try {
            return StaticData.instance().getCommonCards().getCard(cardName);
        } catch (Exception e) {
            LOG.debug("Failed to find card '{}': {}", cardName, e.getMessage());
            return null;
        }
    }

    /**
     * Parses a scenario's top-level {@code events} array into a forced play sequence,
     * keyed by the raw actor id exactly as written in the JSON (e.g. {@code "P1"}).
     *
     * <p>Only {@code CAST}/{@code ACTIVATE}/{@code PLAY_LAND} events with a resolvable
     * card name are included, in array order. Callers must translate the returned keys
     * to actual runtime lobby names (via {@link ScenarioInfo#buildForcedPlaySequenceForLobbyNames})
     * before handing the result to {@link forge.game.GameRules#setForcedPlaySequence(Map)} —
     * {@link forge.ai.AiController} looks the sequence up by {@code player.getLobbyPlayer().getName()},
     * not by the JSON's player id.
     */
    public static Map<String, List<String>> parseForcedSequenceEvents(JsonArray events) {
        return parseForcedSequence(events).cardNames;
    }

    /**
     * Parses the same top-level {@code events} array as {@link #parseForcedSequenceEvents}, but
     * returns each entry's recorded {@code data.choices.sacrifice} target instead of its card
     * name — one list per actor id, index-aligned 1:1 with {@link #parseForcedSequenceEvents}'s
     * result for the same actor (an entry with no recorded sacrifice choice is {@code null}, not
     * omitted, so the two lists stay the same length and can be popped in lockstep).
     *
     * <p>Only the first name in a {@code choices.sacrifice} array is used — recording more than
     * one sacrificed card per event isn't modeled here, matching the existing soft-enforcement,
     * best-effort scope of the rest of this mechanism.
     */
    public static Map<String, List<String>> parseForcedSequenceSacrifice(JsonArray events) {
        return parseForcedSequence(events).sacrificeTargets;
    }

    private static ParsedForcedSequence parseForcedSequence(JsonArray events) {
        final Map<String, List<String>> cardNames = new LinkedHashMap<>();
        final Map<String, List<String>> sacrificeTargets = new LinkedHashMap<>();
        for (JsonElement el : events) {
            if (!el.isJsonObject()) continue;
            JsonObject ev = el.getAsJsonObject();

            String type = ev.has("type") && !ev.get("type").isJsonNull() ? ev.get("type").getAsString() : null;
            if (type == null || !("CAST".equals(type) || "ACTIVATE".equals(type) || "PLAY_LAND".equals(type))) {
                continue;
            }

            String actor = ev.has("a") && !ev.get("a").isJsonNull() ? ev.get("a").getAsString() : null;
            if (actor == null) continue;

            String cardName = null;
            String sacrificeName = null;
            if (ev.has("data") && ev.get("data").isJsonObject()) {
                JsonObject data = ev.getAsJsonObject("data");
                if (data.has("card_name") && !data.get("card_name").isJsonNull()) {
                    cardName = data.get("card_name").getAsString();
                } else if (data.has("card") && !data.get("card").isJsonNull()) {
                    cardName = data.get("card").getAsString();
                }
                // Documented scenario-file shape (docs/SCENARIO_STARTING_HAND_FORMAT.md): a flat
                // data.sacrifice array, as written by DemoPlaySequenceExtractor's flattened
                // snippet export. Also accepts the nested data.choices.sacrifice shape used by
                // ReplayEventLogger's full internal recording (mtg-replay-notation CAST schema),
                // in case a file is ever fed through unflattened.
                if (data.has("sacrifice") && data.get("sacrifice").isJsonArray()
                        && data.getAsJsonArray("sacrifice").size() > 0) {
                    JsonElement first = data.getAsJsonArray("sacrifice").get(0);
                    if (!first.isJsonNull()) sacrificeName = first.getAsString();
                } else if (data.has("choices") && data.get("choices").isJsonObject()) {
                    JsonObject choices = data.getAsJsonObject("choices");
                    if (choices.has("sacrifice") && choices.get("sacrifice").isJsonArray()
                            && choices.getAsJsonArray("sacrifice").size() > 0) {
                        JsonElement first = choices.getAsJsonArray("sacrifice").get(0);
                        if (!first.isJsonNull()) sacrificeName = first.getAsString();
                    }
                }
            }
            if (cardName == null) continue;

            cardNames.computeIfAbsent(actor, k -> new ArrayList<>()).add(cardName);
            sacrificeTargets.computeIfAbsent(actor, k -> new ArrayList<>()).add(sacrificeName);
        }
        return new ParsedForcedSequence(cardNames, sacrificeTargets);
    }

    private static final class ParsedForcedSequence {
        final Map<String, List<String>> cardNames;
        final Map<String, List<String>> sacrificeTargets;
        ParsedForcedSequence(Map<String, List<String>> cardNames, Map<String, List<String>> sacrificeTargets) {
            this.cardNames = cardNames;
            this.sacrificeTargets = sacrificeTargets;
        }
    }

    private String getStringField(JsonObject obj, String field) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return null;
    }

    // --- Public Accessors ---

    public String getGameId() { return gameId; }
    public String getTimestamp() { return timestamp; }
    public String getGameType() { return gameType; }
    public String getWinner() { return winner; }
    public Integer getTurns() { return turns; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public boolean isReplayed() { return replayed; }
    public String getReplayedAt() { return replayedAt; }
    public String getReplayedWinner() { return replayedWinner; }
    public String getReplayedOutcome() { return replayedOutcome; }
    public Integer getReplayedTurns() { return replayedTurns; }
    public String getReplayedMode() { return replayedMode; }
    /** Returns true if this game was replayed with a freshly shuffled deck (Shuffle Replay). */
    public boolean isShuffleReplay() { return "shuffle".equalsIgnoreCase(replayedMode); }

    /** Returns true if the human player (P1) lost in the original game. */
    public boolean isOriginalLoss() {
        if (winner == null || players.isEmpty()) return false;
        String firstPlayerId = players.keySet().iterator().next();
        if (winner.equals(firstPlayerId)) return false;
        PlayerInfo human = players.get(firstPlayerId);
        PlayerInfo winP = players.get(winner);
        if (human != null && winP != null && human.team != null && human.team.equals(winP.team)) {
            return false;
        }
        return true;
    }

    /** Returns true if the human player (P1) won the replayed match. */
    public boolean isReplayWon() {
        if ("win".equalsIgnoreCase(replayedOutcome)) return true;
        if (replayedWinner != null && !players.isEmpty()) {
            String firstPlayerId = players.keySet().iterator().next();
            if (replayedWinner.equals(firstPlayerId)) return true;
            PlayerInfo human = players.get(firstPlayerId);
            PlayerInfo winP = players.get(replayedWinner);
            if (human != null && winP != null && human.team != null && human.team.equals(winP.team)) {
                return true;
            }
        }
        return false;
    }
    public Map<String, PlayerInfo> getPlayers() { return players; }
    public File getReplayFile() { return replayFile; }
    /** Returns the parsed JSON root object, or null if {@link #parse()} has not been called. */
    public JsonObject getRoot() { return root; }
    /** Returns the player ID who takes the first turn, or null if not recorded. */
    public String getStartingPlayer() { return startingPlayer; }
    /** v1.7.0: Returns the replay mode ("full_game" or "scenario"). */
    public String getMode() { return mode; }
    /** v1.7.0: Returns true when this file is a scenario (read-only, cannot be launched). */
    public boolean isScenario() { return "scenario".equals(mode); }
    /** v1.7.0: Returns the parsed scenario metadata, or null for full_game files. */
    public ScenarioInfo getScenarioInfo() { return scenarioInfo; }

    /**
     * Returns true if this replay represents a team game.
     */
    public boolean isTeamGame() {
        Set<Integer> teams = new HashSet<>();
        int countWithTeam = 0;
        for (PlayerInfo p : players.values()) {
            if (p.team != null) {
                teams.add(p.team);
                countWithTeam++;
            }
        }
        return countWithTeam > 0 && (teams.size() < players.size() || "Two-Headed Giant".equalsIgnoreCase(gameType));
    }

    /**
     * Scans {@code ForgeConstants.SCENARIO_DIR} for {@code *.json} files, parses each, and
     * returns only the ones that are scenarios ({@link #isScenario()}), newest first. Demo-play
     * recordings also live in this folder but are plain {@code mode: full_game} replays, so they
     * pass through {@code isScenario()} and are excluded here automatically.
     *
     * <p>Shared by {@code CSubmenuScenario} and the {@code Scenario=} .dck-metadata resolution
     * path ({@link #resolveScenarioByIdOrFilename(String)}) — previously this directory scan was
     * duplicated inline in each GUI caller.</p>
     */
    public static List<ReplayLogParser> listScenarioFiles() {
        List<ReplayLogParser> result = new ArrayList<>();
        File logDir = new File(forge.localinstance.properties.ForgeConstants.SCENARIO_DIR);
        if (!logDir.exists() || !logDir.isDirectory()) {
            return result;
        }
        File[] jsonFiles = logDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            return result;
        }
        Arrays.sort(jsonFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File jsonFile : jsonFiles) {
            ReplayLogParser parser = new ReplayLogParser(jsonFile);
            if (parser.parse() && parser.isScenario()) {
                result.add(parser);
            }
        }
        return result;
    }

    /**
     * Resolves a single id/filename token (as found in a .dck's comma-separated {@code Scenario=}
     * metadata value) to the scenario file it refers to. Matches {@code scenario.id} first (if
     * the file declares one), then falls back to the filename with its {@code .json} extension
     * stripped — existing scenario files predate the optional {@code id} field, so filename
     * matching keeps them referenceable without edits.
     *
     * @return the matching parser, or {@code null} if no scenario file resolves this token.
     */
    public static ReplayLogParser resolveScenarioByIdOrFilename(String idOrFilename) {
        return resolveScenarioByIdOrFilename(idOrFilename, listScenarioFiles());
    }

    /**
     * Matching core of {@link #resolveScenarioByIdOrFilename(String)}, split out so it can be
     * unit-tested against an in-memory candidate list instead of a real {@code GAME_LOG_DIR}
     * directory scan.
     */
    public static ReplayLogParser resolveScenarioByIdOrFilename(String idOrFilename, List<ReplayLogParser> candidates) {
        if (idOrFilename == null || idOrFilename.isEmpty()) return null;
        for (ReplayLogParser parser : candidates) {
            ScenarioInfo si = parser.getScenarioInfo();
            if (si != null && idOrFilename.equals(si.id)) {
                return parser;
            }
        }
        for (ReplayLogParser parser : candidates) {
            String fileName = parser.getReplayFile().getName();
            String withoutExt = fileName.endsWith(".json")
                    ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
            if (idOrFilename.equals(fileName) || idOrFilename.equals(withoutExt)) {
                return parser;
            }
        }
        return null;
    }

    /**
     * Writes a "replayed_at" timestamp into the JSON file's meta section.
     * After this, isReplayed() will return true and the file will be hidden
     * from the replay selection list.
     */
    public void markAsReplayed() {
        markAsReplayed("deterministic");
    }

    public void markAsReplayed(String mode) {
        if (root == null) return;
        try {
            // Add replayed_at to meta
            JsonObject meta = root.has("meta") && root.get("meta").isJsonObject()
                    ? root.getAsJsonObject("meta") : new JsonObject();
            String now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());
            meta.addProperty("replayed_at", now);
            if (mode != null) {
                meta.addProperty("replayed_mode", mode);
            }
            root.add("meta", meta);

            // Write the modified JSON back to the file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter fw = new FileWriter(replayFile)) {
                gson.toJson(root, fw);
            }
            replayed = true;
            replayedAt = now;
            replayedMode = mode;
            LOG.info("Marked replay as used: {} (replayed_at={}, mode={})", replayFile.getName(), now, mode);
        } catch (IOException e) {
            LOG.warn("Could not mark replay as used: {}", replayFile, e);
        }
    }

    /**
     * Records the outcome of a replayed match into the JSON file's meta section.
     */
    public void recordReplayResult(String replayedWinner, String replayedOutcome, int replayedTurns) {
        recordReplayResult(replayedWinner, replayedOutcome, replayedTurns, "deterministic");
    }

    public void recordReplayResult(String replayedWinner, String replayedOutcome, int replayedTurns, String mode) {
        if (root == null) return;
        try {
            JsonObject meta = root.has("meta") && root.get("meta").isJsonObject()
                    ? root.getAsJsonObject("meta") : new JsonObject();
            String now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());
            meta.addProperty("replayed_at", now);
            if (replayedWinner != null) {
                meta.addProperty("replayed_winner", replayedWinner);
            }
            if (replayedOutcome != null) {
                meta.addProperty("replayed_outcome", replayedOutcome);
            }
            meta.addProperty("replayed_turns", replayedTurns);
            if (mode != null) {
                meta.addProperty("replayed_mode", mode);
            }
            root.add("meta", meta);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter fw = new FileWriter(replayFile)) {
                gson.toJson(root, fw);
            }
            this.replayed = true;
            this.replayedAt = now;
            this.replayedWinner = replayedWinner;
            this.replayedOutcome = replayedOutcome;
            this.replayedTurns = replayedTurns;
            this.replayedMode = mode;
            LOG.info("Recorded replay outcome for {}: outcome={}, winner={}, turns={}, mode={}",
                    replayFile.getName(), replayedOutcome, replayedWinner, replayedTurns, mode);
        } catch (IOException e) {
            LOG.warn("Could not record replay outcome for {}: {}", replayFile, e.getMessage());
        }
    }

    /**
     * Extract the initial library draw order from {@code initial_state.objects}.
     *
     * Returns a map of player ID (e.g. "P1") to an ordered list of card names,
     * where index 0 is the top of the library (next draw).
     *
     * Uses the {@code notes.position} field written by the exporter to sort cards
     * within each player's library zone.  Falls back to JSON insertion order when
     * position data is absent.
     *
     * Call {@link #parse()} before this method.
     */
    public Map<String, List<String>> getInitialLibraryOrder() {
        if (root == null) return new LinkedHashMap<>();

        JsonObject initialState = root.has("initial_state") && root.get("initial_state").isJsonObject()
                ? root.getAsJsonObject("initial_state") : null;
        if (initialState == null) return new LinkedHashMap<>();

        JsonObject objects = initialState.has("objects") && initialState.get("objects").isJsonObject()
                ? initialState.getAsJsonObject("objects") : null;
        if (objects == null) return new LinkedHashMap<>();

        // Collect per-player library cards with their positions
        // Map: playerId -> TreeMap<position, cardName>
        Map<String, java.util.TreeMap<Integer, String>> byPlayer = new LinkedHashMap<>();

        int fallbackPos = 0;
        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject obj = entry.getValue().getAsJsonObject();

            String zone = getStringField(obj, "zone");
            if (zone == null || !zone.endsWith(":library")) continue;

            String playerId = zone.substring(0, zone.indexOf(':'));
            String cardRef = getStringField(obj, "card_ref");
            if (cardRef == null) cardRef = getStringField(obj, "cardRef");
            if (cardRef == null) continue;

            // Extract position from notes.position (set by ReplayNotationExporter)
            int position = fallbackPos++;
            if (obj.has("notes") && obj.get("notes").isJsonObject()) {
                JsonObject notes = obj.getAsJsonObject("notes");
                if (notes.has("position") && !notes.get("position").isJsonNull()) {
                    try { position = notes.get("position").getAsInt(); } catch (Exception ignored) {}
                }
            }

            byPlayer.computeIfAbsent(playerId, k -> new java.util.TreeMap<>())
                    .put(position, cardRef);
        }

        // Convert sorted maps to ordered lists
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.TreeMap<Integer, String>> e : byPlayer.entrySet()) {
            result.put(e.getKey(), new ArrayList<>(e.getValue().values()));
        }
        return result;
    }

    /**
     * Get the display summary for this replay (used in the UI list).
     * Scenario files are prefixed with "[SCENARIO]" for visual distinction.
     */
    public String getDisplaySummary() {
        // v1.7.0: scenario files get a distinct prefix
        if (isScenario() && scenarioInfo != null && scenarioInfo.title != null) {
            return "[SCENARIO] " + scenarioInfo.title;
        }

        StringBuilder sb = new StringBuilder();
        if (timestamp != null) {
            sb.append(timestamp.replace("T", " ").replace("Z", ""));
        }
        sb.append(" — ");
        boolean first = true;
        for (PlayerInfo p : players.values()) {
            if (!first) sb.append(" vs ");
            sb.append(p.name != null ? p.name : "?");
            first = false;
        }
        if (turns != null) {
            sb.append(" (").append(turns).append(" turns)");
        }
        if (winner != null) {
            PlayerInfo winnerInfo = players.get(winner);
            if (winnerInfo != null) {
                sb.append(" — Winner: ").append(winnerInfo.name);
            }
        }
        return sb.toString();
    }

    /**
     * Scenario metadata extracted from a scenario replay file (spec v1.7.0+).
     * Only populated when {@code mode == "scenario"}.
     */
    public static class ScenarioInfo {
        /** Optional stable identifier for referencing this scenario from a .dck's Scenario= key.
         *  Falls back to the replay file's own filename (sans extension) when absent. */
        public String id;
        public String type;
        /** Preferred field going forward (matches mtg-replay-notation's DecklistScenario.name).
         *  Populated from JSON "name", falling back to the legacy "title" key. */
        public String name;
        /** @deprecated kept in sync with {@link #name} for old external readers; use {@link #name}. */
        @Deprecated
        public String title;
        /** Owning deck's identifier (mtg-replay-notation DecklistScenario.deck_id) - optional;
         *  authoritative when present, otherwise deck association falls back to a reverse
         *  lookup across decks' own Scenario= references (see CSubmenuScenario). */
        public String deckId;
        public String description;
        public String question;
        public String answer;
        public final List<String> rulingReferences = new ArrayList<>();
        public final List<String> tags = new ArrayList<>();
        /** Number of players to create for the game (default 2). */
        public int playerCount = 2;
        /** Puzzle-format key=value lines to set up the initial game state. */
        public final List<String> gameState = new ArrayList<>();

        // ---------------------------------------------------------------
        // v1.8.0 (fork): per-player structured starting configuration
        // ---------------------------------------------------------------

        /** Per-player starting hand cards (ordered). Key = "P1", "P2", … */
        public final Map<String, List<String>> playerStartingHands = new LinkedHashMap<>();
        /** Per-player first-N draw cards (ordered, top of library). Key = "P1", "P2", … */
        public final Map<String, List<String>> playerFirstDraws = new LinkedHashMap<>();
        /** Per-player commander names. Key = "P1", "P2", … */
        public final Map<String, List<String>> playerCommanders = new LinkedHashMap<>();
        /** Per-player battlefield cards (names). Key = "P1", "P2", … */
        public final Map<String, List<String>> playerBattlefield = new LinkedHashMap<>();
        /** Per-player starting life override. Key = "P1", "P2", … */
        public final Map<String, Integer> playerStartingLife = new LinkedHashMap<>();
        /**
         * Forced play sequence parsed from the top-level {@code events} array, keyed by
         * the raw JSON actor id ("P1", "P2", …) — NOT yet a runtime lobby name.
         * Use {@link #buildForcedPlaySequenceForLobbyNames(Map)} before handing to GameRules.
         */
        public final Map<String, List<String>> playerForcedSequence = new LinkedHashMap<>();
        /**
         * Recorded sacrifice-cost target for each {@link #playerForcedSequence} entry, index-aligned
         * 1:1 per actor id ({@code null} where that entry recorded no sacrifice choice). Lets
         * {@code AiController} force its own sacrifice-cost decision to match what was originally
         * recorded, instead of falling back to its usual cost-benefit heuristic.
         */
        public final Map<String, List<String>> playerForcedSacrifice = new LinkedHashMap<>();

        /**
         * Returns true when at least one player in this scenario has a structured
         * starting configuration (hand or draws).
         */
        public boolean hasPlayerSetup() {
            return !playerStartingHands.isEmpty() || !playerFirstDraws.isEmpty();
        }

        /** Returns true when this scenario defines a forced play sequence via {@code events}. */
        public boolean hasForcedPlaySequence() {
            return !playerForcedSequence.isEmpty();
        }

        /**
         * Translates {@link #playerForcedSequence}'s player-id keys ("P1", "P2", …) into the
         * lobby-name keys {@code GameRules.setForcedPlaySequence()} expects, using the actual
         * names the launcher assigned to each seat this run (e.g. the human's configured
         * player name, or whatever name an AI seat was created with).
         *
         * <p>An id with no corresponding entry in {@code idToLobbyName} is dropped rather than
         * guessed — a stale/renamed seat should not silently match the wrong player.</p>
         */
        public Map<String, List<String>> buildForcedPlaySequenceForLobbyNames(Map<String, String> idToLobbyName) {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : playerForcedSequence.entrySet()) {
                String lobbyName = idToLobbyName.get(e.getKey());
                if (lobbyName == null || e.getValue().isEmpty()) continue;
                result.put(lobbyName, new ArrayList<>(e.getValue()));
            }
            return result;
        }

        /**
         * Same translation as {@link #buildForcedPlaySequenceForLobbyNames}, for
         * {@link #playerForcedSacrifice} instead. Must be applied to the same id-set the caller
         * used for the play sequence itself, so the two lists stay index-aligned per lobby name.
         */
        public Map<String, List<String>> buildForcedSacrificeForLobbyNames(Map<String, String> idToLobbyName) {
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : playerForcedSacrifice.entrySet()) {
                String lobbyName = idToLobbyName.get(e.getKey());
                if (lobbyName == null || e.getValue().isEmpty()) continue;
                result.put(lobbyName, new ArrayList<>(e.getValue()));
            }
            return result;
        }

        /**
         * Builds puzzle-format game_state lines from the structured player setup
         * fields.  Returned lines are merged with existing {@link #gameState} in
         * {@code CSubmenuScenario}.
         *
         * <p>Player index mapping: P1 = human (index 0), P2 = AI-1 (index 1), …</p>
         */
        public List<String> buildGameStateFromPlayerSetup() {
            List<String> lines = new ArrayList<>();

            // Helper: "human" for P1, "ai" for P2, "p2" for P3, etc.
            // The GameState engine recognises "human"/"ai" for two-player, and "pN" for N-player.
            for (Map.Entry<String, List<String>> e : playerStartingHands.entrySet()) {
                String prefix = playerIdToPrefix(e.getKey(), playerCount);
                if (!e.getValue().isEmpty()) {
                    lines.add(prefix + "hand=" + String.join(";", e.getValue()));
                }
            }
            for (Map.Entry<String, List<String>> e : playerFirstDraws.entrySet()) {
                String prefix = playerIdToPrefix(e.getKey(), playerCount);
                if (!e.getValue().isEmpty()) {
                    lines.add(prefix + "library=" + String.join(";", e.getValue()));
                }
            }
            for (Map.Entry<String, List<String>> e : playerCommanders.entrySet()) {
                String prefix = playerIdToPrefix(e.getKey(), playerCount);
                if (!e.getValue().isEmpty()) {
                    // The |IsCommander flag makes GameState call player.addCommander()
                    List<String> flagged = new ArrayList<>();
                    for (String name : e.getValue()) {
                        flagged.add(name + "|IsCommander");
                    }
                    lines.add(prefix + "command=" + String.join(";", flagged));
                }
            }
            for (Map.Entry<String, List<String>> e : playerBattlefield.entrySet()) {
                String prefix = playerIdToPrefix(e.getKey(), playerCount);
                if (!e.getValue().isEmpty()) {
                    lines.add(prefix + "battlefield=" + String.join(";", e.getValue()));
                }
            }
            for (Map.Entry<String, Integer> e : playerStartingLife.entrySet()) {
                String prefix = playerIdToPrefix(e.getKey(), playerCount);
                lines.add(prefix + "life=" + e.getValue());
            }
            return lines;
        }

        /** Map player ID ("P1", "P2", …) to puzzle-format prefix ("human", "ai", "p2", …). */
        private static String playerIdToPrefix(String playerId, int playerCount) {
            if ("P1".equals(playerId)) return "human";
            if ("P2".equals(playerId)) return playerCount == 2 ? "ai" : "p1";
            // P3 → "p2", P4 → "p3", …
            try {
                int idx = Integer.parseInt(playerId.substring(1));
                return "p" + (idx - 1);
            } catch (NumberFormatException e) {
                return playerId.toLowerCase();
            }
        }
    }

    /**
     * Information about a player extracted from the replay log.
     */
    public static class PlayerInfo {
        public String playerId;
        public String name;
        public String deckName;
        public String deckHash;
        public boolean isAi;
        public int startingLife = 20;
        public String playerType;
        public Deck deck;
        /** v1.9.0: Team number for multiplayer team games. Null for non-team games. */
        public Integer team;

        /**
         * Get 0-indexed Forge team number for RegisteredPlayer.
         * Maps 1-indexed team (1 -> 0, 2 -> 1) or 0-indexed team (0 -> 0).
         * Returns -1 if team is null.
         */
        public int getForgeTeam() {
            if (team == null) {
                return -1;
            }
            return team > 0 ? team - 1 : 0;
        }

        @Override
        public String toString() {
            return name + (deckName != null ? " (" + deckName + ")" : "");
        }
    }
}









