package forge.game.log;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameOutcome;
import forge.game.card.Card;
import forge.game.log.model.*;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;

import com.google.common.collect.Multiset;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exports game logs in MTG Replay Notation format (JSON).
 * Implements the specification from MTG_REPLAY_NOTATION.md.
 */
public class ReplayNotationExporter {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private final Game game;
    private final ReplayLog replayLog;
    private int eventIndex = 0;
    private final Map<Card, String> cardIdMap = new HashMap<>();
    private int cardIdCounter = 0;
    private int stackIdCounter = 0;
    private int learningMarkerCounter = 0;
    private final long gameStartTime = System.currentTimeMillis();
    private final Map<Card, String> cardToStackId = new HashMap<>();

    // Stable player ID cache: populated at construction, survives player elimination
    private final Map<Player, String> playerIdCache = new LinkedHashMap<>();
    // Ordered list of all players (including future eliminated ones) for buildGameSummary
    private final List<Player> allPlayersOrdered = new ArrayList<>();

    // Phase-Event Buffering: Skip empty phases
    private Map<String, Object> pendingPhaseEvent = null;
    private String currentPhase = null;
    private long phaseStartTime = 0;
    private static final Set<String> ALWAYS_EMPTY_PHASES = new HashSet<>(Arrays.asList(
        "PREGAME", "UNTAP", "DRAW", "CLEANUP"
    ));

    // ---- Turn summary tracking ----
    private int trackingTurn = 0;
    private String trackingActivePlayer = null;
    // FIX P1: Track turn-start snapshots for L2 generation
    private GameState turnStartSnapshot = null;
    // Per-player counters reset each turn: playerId → counter
    private final Map<String, Integer> turnLandsPlayed = new HashMap<>();
    private final Map<String, Integer> turnCardsDrawn = new HashMap<>();
    private final Map<String, Integer> turnSpellsCast = new HashMap<>();
    private final Map<String, Integer> turnAbilitiesActivated = new HashMap<>();
    private final Map<String, Integer> turnDamageDealt = new HashMap<>();
    private final Map<String, Integer> turnDamageReceived = new HashMap<>();
    // Game-wide accumulators
    private final Map<String, Integer> gameTotalCardsDrawn = new HashMap<>();
    private final Map<String, Integer> gameTotalSpellsCast = new HashMap<>();
    private final Map<String, Integer> gameTotalAbilitiesActivated = new HashMap<>();
    private final Map<String, Integer> gameTotalLandsPlayed = new HashMap<>();
    private final Map<String, Integer> gameMissedLandDrops = new HashMap<>();
    private final Map<String, Integer> gameTotalDamageDealt = new HashMap<>();
    private final Map<String, Integer> gameTotalDamageReceived = new HashMap<>();
    private final Map<String, Integer> gameTotalCreaturesPlayed = new HashMap<>();
    private final Map<String, Integer> gameTotalCountersPlaced = new HashMap<>();
    private final Map<String, Integer> gamePeakMana = new HashMap<>();

    public ReplayNotationExporter(Game game) {
        this.game = game;
        this.replayLog = new ReplayLog();
        initializeReplayLog();
    }

    /**
     * Initialize the replay log with metadata and initial state.
     */
    private void initializeReplayLog() {
        // Set metadata
        ReplayMeta meta = replayLog.getMeta();
        meta.setGameId("game-" + UUID.randomUUID());
        meta.setTimestamp(ISO_FORMAT.format(Instant.now()));

        if (game.getRules() != null && game.getRules().getGameType() != null) {
            meta.setGameType(game.getRules().getGameType().toString());
        } else {
            meta.setGameType("Unknown");
        }
        
        // FIX: Set mode="scenario" if scenario settings are present
        if (game.getRules() != null && game.getRules().getScenarioStartingHands() != null 
                && !game.getRules().getScenarioStartingHands().isEmpty()) {
            replayLog.setMode("scenario");
            System.out.println("[ReplayNotationExporter] Setting replay mode to 'scenario'");
        } else {
            System.out.println("[ReplayNotationExporter] Replay mode remains 'full_game'");
        }

        // Build stable player ID map from initial game.getPlayers() order.
        // Must happen before any player can be eliminated (moved to lostPlayers).
        List<Player> players = game.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            playerIdCache.put(p, "P" + (i + 1));
            allPlayersOrdered.add(p);
        }

        // Add player metadata
        for (Player player : game.getPlayers()) {
            ReplayMeta.PlayerMeta playerMeta = new ReplayMeta.PlayerMeta();
            playerMeta.setName(player.getName());

            // Player type and AI status
            playerMeta.setAi(player.isAI());
            playerMeta.setPlayerType(player.isAI() ? "AI" : "Human");
            playerMeta.setStartingLife(player.getStartingLife());

            // Team information for multiplayer team games
            int teamNumber = player.getTeam();
            if (teamNumber >= 0) {
                playerMeta.setTeam(teamNumber);
            }
            // Note: team remains null for non-team games (1v1, free-for-all)

            // Add deck name and hash if available
            if (player.getRegisteredPlayer() != null &&
                player.getRegisteredPlayer().getDeck() != null) {
                forge.deck.Deck deck = player.getRegisteredPlayer().getDeck();
                playerMeta.setDeckName(deck.getName());
                String deckHash = calculateDeckHash(deck);
                playerMeta.setDeckHash(deckHash);
                // deck_link (spec v1.4.0): populated from DeckURL metadata field when present.
                playerMeta.setDeckLink(deck.getDeckUrl());
            } else {
                playerMeta.setDeckName("unknown");
                playerMeta.setDeckHash(null);
                playerMeta.setDeckLink(null);
            }

            meta.getPlayers().put(getPlayerId(player), playerMeta);
        }

        // Set seed (cryptographically random — not the game RNG seed, but unique per game)
        replayLog.setSeed(new java.security.SecureRandom().nextLong());

        // Initialize initial state
        captureInitialState();
    }

    /**
     * Re-capture the initial game state after opening hands are drawn and
     * mulligan decisions are finalized (P1.1 fix).
     * Called once from GameLogFormatter when pregame ends (first Turn 1 upkeep).
     * Replaces the empty initial_state that was captured in the constructor
     * before zones were populated.
     */
    public void recaptureInitialState() {
        captureInitialState();
    }

    /**
     * Capture the initial game state.
     */
    private void captureInitialState() {
        GameState state = new GameState();
        state.setTurn(0);
        state.setPhase("PREGAME");
        state.setStep("PREGAME");

        // Initialize player states
        for (Player player : game.getPlayers()) {
            GameState.PlayerState playerState = new GameState.PlayerState();
            playerState.setLife(player.getLife());
            playerState.setManaPool(new ArrayList<>()); // Spec: always []
            playerState.setMaxHandSize(7);
            state.getPlayers().put(getPlayerId(player), playerState);
        }

        // Initialize zones and capture all game objects
        List<String> handCards = new ArrayList<>();
        List<String> libraryCards = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);

            // Capture library cards with position
            libraryCards.clear();
            int position = 0;
            for (Card card : player.getZone(ZoneType.Library)) {
                String cardId = getCardId(card);
                libraryCards.add(cardId);

                // Create object state for this card
                GameState.ObjectState objState = createObjectState(card, playerId + ":library", position++);
                state.getObjects().put(cardId, objState);
            }
            Map<String, Object> libraryInfo = new HashMap<>();
            libraryInfo.put("count", libraryCards.size());
            libraryInfo.put("cards", new ArrayList<>(libraryCards));
            state.getZones().put(playerId + ":library", libraryInfo);

            // Capture hand cards
            handCards.clear();
            for (Card card : player.getZone(ZoneType.Hand)) {
                String cardId = getCardId(card);
                handCards.add(cardId);

                GameState.ObjectState objState = createObjectState(card, playerId + ":hand", -1);
                state.getObjects().put(cardId, objState);
            }
            state.getZones().put(playerId + ":hand", new ArrayList<>(handCards));

            // Capture graveyard (usually empty at start)
            state.getZones().put(playerId + ":graveyard", new ArrayList<>());

            // Capture command zone (for Commander format)
            List<String> commandCards = new ArrayList<>();
            for (Card card : player.getZone(ZoneType.Command)) {
                String cardId = getCardId(card);
                commandCards.add(cardId);

                GameState.ObjectState objState = createObjectState(card, playerId + ":command", -1);
                state.getObjects().put(cardId, objState);
            }
            if (!commandCards.isEmpty()) {
                state.getZones().put(playerId + ":command", commandCards);
            }
        }

        state.getZones().put("battlefield", new ArrayList<>());
        state.getZones().put("stack", new ArrayList<>());
        state.getZones().put("exile", new ArrayList<>());

        replayLog.setInitialState(state);
    }

    /**
     * Get or create a stable ID for a player.
     */
    private String getPlayerId(Player player) {
        // Use stable cache so eliminated players (moved to lostPlayers) keep their IDs
        String cached = playerIdCache.get(player);
        if (cached != null) return cached;
        // Fallback: assign by current position (should not normally happen)
        int index = game.getPlayers().indexOf(player);
        return "P" + (index + 1);
    }

    /**
     * Resolve a PlayerView back to its underlying Player using the game reference.
     */
    public Player resolvePlayer(forge.game.player.PlayerView pv) {
        if (pv == null) return null;
        for (Player p : game.getPlayers()) {
            if (p.getView() == pv) return p;
        }
        // Fallback: match by name
        for (Player p : game.getPlayers()) {
            if (p.getName().equals(pv.getName())) return p;
        }
        return null;
    }

    /**
     * Resolve a CardView back to its underlying Card by matching IDs across all player zones.
     * Returns null if the card cannot be found (e.g. already moved to an inaccessible zone).
     */
    public forge.game.card.Card resolveCard(forge.game.card.CardView cv) {
        if (cv == null) return null;
        int id = cv.getId();
        for (Player p : game.getPlayers()) {
            for (ZoneType zone : new ZoneType[]{ZoneType.Battlefield, ZoneType.Hand,
                    ZoneType.Graveyard, ZoneType.Library, ZoneType.Exile, ZoneType.Command}) {
                for (forge.game.card.Card c : p.getZone(zone)) {
                    if (c.getId() == id) return c;
                }
            }
        }
        // Also try stack zone
        for (forge.game.card.Card c : game.getCardsIn(ZoneType.Stack)) {
            if (c.getId() == id) return c;
        }
        return null;
    }

    /**
     * Create an ObjectState for a card at a given zone and position.
     * @param card The card to create state for
     * @param zone The zone name (e.g., "P1:library", "battlefield")
     * @param position The position in the zone (-1 if not applicable)
     * @return The created ObjectState
     */
    private GameState.ObjectState createObjectState(Card card, String zone, int position) {
        GameState.ObjectState objState = new GameState.ObjectState();

        // Card reference (name) for easy lookup
        objState.setCardRef(getActualCardName(card));

        // Owner and controller
        if (card.getOwner() != null) {
            objState.setOwner(getPlayerId(card.getOwner()));
        }
        if (card.getController() != null) {
            objState.setController(getPlayerId(card.getController()));
        } else {
            objState.setController(objState.getOwner());
        }

        // Zone
        objState.setZone(zone);

        // State flags
        objState.setTapped(card.isTapped());
        objState.setFaceDown(card.isFaceDown());
        objState.setFlipped(card.isFlipped());
        
        // v1.6.0: Summoning sickness
        // A creature has summoning sickness if it entered the battlefield this turn AND lacks haste
        if (card.isCreature()) {
            boolean enteredThisTurn = card.getTurnInZone() == game.getPhaseHandler().getTurn();
            boolean hasHaste = card.hasKeyword("Haste");
            objState.setSummoningSick(enteredThisTurn && !hasHaste);
        } else {
            objState.setSummoningSick(false);
        }

        // Counters
        if (card.getCounters() != null && !card.getCounters().isEmpty()) {
            for (Multiset.Entry<forge.game.card.CounterType> counter : card.getCounters().entrySet()) {
                objState.getCounters().put(counter.getElement().getName(), counter.getCount());
            }
        }

        // Damage marked
        objState.setDamageMarked(card.getDamage());

        // Attached to
        if (card.getAttachedTo() != null) {
            objState.setAttachedTo(getCardId(card.getAttachedTo()));
        }

        // Position in zone (for library ordering)
        if (position >= 0) {
            objState.getNotes().put("position", position);
        }

        return objState;
    }

    /**
     * Set the game outcome information (winner, turns, duration).
     * Should be called when the game ends.
     */
    public void setGameOutcome(GameOutcome outcome) {
        if (outcome == null) {
            return;
        }

        ReplayMeta meta = replayLog.getMeta();

        // Set winner
        RegisteredPlayer winningPlayer = outcome.getWinningPlayer();
        if (winningPlayer != null) {
            // Find the player index
            for (int i = 0; i < game.getPlayers().size(); i++) {
                Player p = game.getPlayers().get(i);
                if (p.getRegisteredPlayer() == winningPlayer) {
                    meta.setWinner("P" + (i + 1));
                    break;
                }
            }
        } else if (outcome.isDraw()) {
            meta.setWinner("draw");
        }

        // Set win condition
        meta.setWinCondition(determineWinCondition(outcome));

        // Set conceded flag
        boolean anyoneConceded = false;
        for (Player p : game.getPlayers()) {
            if (p.getOutcome() != null &&
                p.getOutcome().lossState == forge.game.player.GameLossReason.Conceded) {
                anyoneConceded = true;
                break;
            }
        }
        meta.setConceded(anyoneConceded);

        // Set turns
        meta.setTurns(outcome.getLastTurnNumber());

        // Set duration
        long durationMs = System.currentTimeMillis() - gameStartTime;
        meta.setDurationSeconds((int) (durationMs / 1000));
    }

    /**
     * Set outcome data from a GameEventGameOutcome (used by GameLogFormatter which has no
     * direct GameOutcome reference). Resolves winner by name against allPlayersOrdered.
     */
    public void setOutcomeFromEvent(String winningPlayerName, int lastTurnNumber, List<String> outcomeStrings) {
        ReplayMeta meta = replayLog.getMeta();
        meta.setTurns(lastTurnNumber);
        if (winningPlayerName != null) {
            for (Player p : allPlayersOrdered) {
                if (winningPlayerName.equals(p.getName())) {
                    meta.setWinner(getPlayerId(p));
                    break;
                }
            }
        }
        if (outcomeStrings != null && !outcomeStrings.isEmpty()) {
            meta.setWinCondition(outcomeStrings.get(0));
        }
        long durationMs = System.currentTimeMillis() - gameStartTime;
        meta.setDurationSeconds((int) (durationMs / 1000));
    }

    /**
     * Determine the win condition string based on how players lost.
     */
    private String determineWinCondition(GameOutcome outcome) {
        if (outcome.isDraw()) {
            return "draw";
        }

        // Check each losing player's reason
        for (Player p : game.getPlayers()) {
            if (p.getOutcome() == null || p.getOutcome().hasWon()) {
                continue;
            }

            forge.game.player.GameLossReason reason = p.getOutcome().lossState;
            if (reason != null) {
                return switch (reason) {
                    case LifeReachedZero -> "life_zero";
                    case CommanderDamage -> "commander_damage";
                    case Poisoned -> "poison";
                    case Milled -> "decked";
                    case Conceded -> "concession";
                    case SpellEffect, OpponentWon -> "alternate_win";
                    default -> "unknown";
                };
            }

            // Check for alternate win
            if (p.getOutcome().altWinSourceName != null) {
                return "alternate_win";
            }
        }

        return "unknown";
    }

    /**
     * Calculate a stable hash for a deck based on its card contents.
     * The hash is independent of deck name and based only on:
     * - Card names (sorted alphabetically)
     * - Card quantities
     * - Main deck and Commander sections only (Sideboard excluded)
     *
     * This allows identifying the same deck even if renamed.
     * Sideboard is excluded as it may vary between games.
     *
     * @param deck The deck to hash
     * @return A hex string representing the deck hash (first 16 chars of SHA-256)
     */
    private String calculateDeckHash(forge.deck.Deck deck) {
        if (deck == null) {
            return null;
        }

        try {
            // Build a canonical string representation of the deck
            StringBuilder deckContent = new StringBuilder();

            // Only process Main and Commander sections (exclude Sideboard, etc.)
            forge.deck.DeckSection[] sectionsToHash = {
                forge.deck.DeckSection.Main,
                forge.deck.DeckSection.Commander
            };

            for (forge.deck.DeckSection section : sectionsToHash) {
                forge.deck.CardPool pool = deck.get(section);
                if (pool == null || pool.isEmpty()) {
                    continue;
                }

                deckContent.append("[").append(section.name()).append("]");

                // Get all cards, sort by name for consistency
                List<String> cardEntries = new ArrayList<>();
                for (Map.Entry<forge.item.PaperCard, Integer> entry : pool) {
                    // Format: "CardName:Quantity"
                    cardEntries.add(entry.getKey().getName() + ":" + entry.getValue());
                }
                Collections.sort(cardEntries);

                for (String entry : cardEntries) {
                    deckContent.append(entry).append(";");
                }
            }

            // Calculate SHA-256 hash
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(deckContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Convert to hex string (first 16 characters = 64 bits, enough for uniqueness)
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) { // 8 bytes = 16 hex chars
                String hex = Integer.toHexString(0xff & hashBytes[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 should always be available, but fallback to simple hash
            return Integer.toHexString(deck.toString().hashCode());
        }
    }

    /**
     * Get or create a stable ID for a card.
     * Each card instance gets its own entry in card_index keyed by its card ID (e.g. "c43").
     */
    private String getCardId(Card card) {
        return cardIdMap.computeIfAbsent(card, c -> {
            cardIdCounter++;
            String cardId = "c" + cardIdCounter;

            // Get the actual card name (even for face-down cards)
            String actualName = getActualCardName(c);

            // Create card definition for this specific card instance
            CardDefinition def = new CardDefinition();
            def.setName(actualName);

            // Get mana cost from actual card state if available
            if (c.getManaCost() != null && !c.getManaCost().isNoCost()) {
                def.setCost(c.getManaCost().toString());
            } else if (c.getCurrentState() != null && c.getCurrentState().getManaCost() != null) {
                def.setCost(c.getCurrentState().getManaCost().toString());
            } else {
                def.setCost("no cost");
            }

            // Get type
            def.setType(c.getType() != null ? c.getType().toString() : "Unknown");

            // P1.2: Set oracle_id from PaperCard if available
            if (c.getPaperCard() != null) {
                String surrogateId = c.getPaperCard().getName();
                if (c.getPaperCard().getEdition() != null) {
                    surrogateId += "|" + c.getPaperCard().getEdition();
                }
                if (c.getPaperCard().getCollectorNumber() != null) {
                    surrogateId += "|" + c.getPaperCard().getCollectorNumber();
                }
                def.setOracleId(surrogateId);
            }

            // P12: Enrich card_index with oracle_text, power, toughness, subtypes
            try {
                String oracleText = c.getOracleText();
                if (oracleText != null && !oracleText.isEmpty()) {
                    def.setOracleText(oracleText);
                }
            } catch (Exception ignored) { /* oracle text not available */ }

            try {
                if (c.isCreature()) {
                    def.setPower(c.getBasePowerString());
                    def.setToughness(c.getBaseToughnessString());
                }
            } catch (Exception ignored) { /* P/T not available */ }

            try {
                if (c.getType() != null) {
                    Iterable<String> subs = c.getType().getSubtypes();
                    if (subs != null) {
                        List<String> subtypeList = new ArrayList<>();
                        for (String s : subs) {
                            subtypeList.add(s);
                        }
                        if (!subtypeList.isEmpty()) {
                            def.setSubtypes(subtypeList);
                        }
                    }
                }
            } catch (Exception ignored) { /* subtypes not available */ }

            // Key is the card ID itself (e.g. "c43") for direct lookup from events
            replayLog.getCardIndex().put(cardId, def);

            return cardId;
        });
    }

    /**
     * Get the actual card name, even for face-down cards.
     */
    private String getActualCardName(Card card) {
        // First check if card has a name
        String name = card.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // For face-down cards, try to get the original paper card name
        if (card.getPaperCard() != null) {
            return card.getPaperCard().getName();
        }

        // Try to get from current state
        if (card.getCurrentState() != null && card.getCurrentState().getName() != null) {
            return card.getCurrentState().getName();
        }

        // For tokens or completely unknown cards, generate a descriptive name
        if (card.isToken()) {
            return "Token (" + (card.getType() != null ? card.getType().getCreatureTypes().toString() : "Unknown") + ")";
        }

        // Last resort: use the card ID as a placeholder
        return "Unknown Card #" + card.getId();
    }

    /**
     * Generate a new stack ID.
     */
    private String generateStackId() {
        stackIdCounter++;
        return "s" + stackIdCounter;
    }

    /**
     * Add a Level 1 event to the log.
     */
    public void addEvent(String timeMarker, String actor, String eventType, Map<String, Object> data) {
        L1Event event = new L1Event(eventIndex++, timeMarker, actor, eventType);
        event.setData(data);
        replayLog.addL1Event(event);
    }

    /**
     * Log game start event.
     * This is the first event in any game log.
     */
    public void logGameStart(String gameType, Player firstPlayer, Iterable<Player> allPlayers, String timeMarker) {
        Map<String, Object> data = new HashMap<>();
        data.put("game_type", gameType);
        data.put("first_player", getPlayerId(firstPlayer));

        List<String> playerIds = new ArrayList<>();
        for (Player p : allPlayers) {
            playerIds.add(getPlayerId(p));
        }
        data.put("players", playerIds);

        // Also populate the game_start section
        GameStartInfo gameStart = replayLog.getGameStart();
        gameStart.setStartingPlayer(getPlayerId(firstPlayer));

        // Initialize mulligan info for all players
        for (Player p : allPlayers) {
            GameStartInfo.MulliganInfo mulliganInfo = new GameStartInfo.MulliganInfo(getPlayerId(p));
            gameStart.addMulligan(mulliganInfo);
        }

        addEvent(timeMarker, "SYS", "GAME_START", data);
    }

    /**
     * Set the toss winner and play/draw choice.
     * Call this when the starting player is determined.
     */
    public void setTossWinner(Player tossWinner, boolean choseToPlay) {
        GameStartInfo gameStart = replayLog.getGameStart();
        gameStart.setTossWinner(getPlayerId(tossWinner));
        gameStart.setPlayDrawChoice(choseToPlay ? "play" : "draw");
    }

    /**
     * Record a mulligan decision for a player.
     * Call this each time a player mulligans.
     */
    public void recordMulliganTaken(Player player) {
        GameStartInfo gameStart = replayLog.getGameStart();
        for (GameStartInfo.MulliganInfo info : gameStart.getMulligans()) {
            if (info.getPlayer().equals(getPlayerId(player))) {
                info.recordMulligan();
                break;
            }
        }
    }

    /**
     * Record the final keep decision with cards put to bottom (London mulligan).
     */
    public void recordKeepHand(Player player, int cardsToBottom) {
        GameStartInfo gameStart = replayLog.getGameStart();
        for (GameStartInfo.MulliganInfo info : gameStart.getMulligans()) {
            if (info.getPlayer().equals(getPlayerId(player))) {
                info.setFinalHandSize(player.getCardsIn(ZoneType.Hand).size());
                info.setCardsToBottom(cardsToBottom);
                break;
            }
        }
    }

    /**
     * Log a zone change event.
     * The actor is determined by the context:
     * - For player-initiated moves (hand to battlefield, etc.): the card's controller
     * - For system moves (library to hand during draw, etc.): "SYS"
     */
    public void logZoneChange(Card card, ZoneType from, ZoneType to, String timeMarker, Player owner) {
        logZoneChange(card, from, to, timeMarker, owner, false);
    }

    /**
     * Log a zone change event with explicit actor control.
     * @param isPlayerAction true if this is a deliberate player action (play land, etc.)
     */
    public void logZoneChange(Card card, ZoneType from, ZoneType to, String timeMarker, Player owner, boolean isPlayerAction) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("obj", getCardId(card));
        data.put("card_name", getActualCardName(card));
        data.put("from", formatZone(from, owner));
        data.put("to", formatZone(to, owner));
        data.put("pos", "top");
        data.put("visibility", isPublicZone(to) ? "public" : "private");
        
        // FIX P2 & P3: Add controller and owner to MOVE events
        if (card.getController() != null) {
            data.put("controller", getPlayerId(card.getController()));
        }
        if (card.getOwner() != null) {
            data.put("owner", getPlayerId(card.getOwner()));
        }

        // Determine actor: player action or system action
        String actor = "SYS";
        if (isPlayerAction && card.getController() != null) {
            actor = getPlayerId(card.getController());
        } else if (isPlayerAction && owner != null) {
            actor = getPlayerId(owner);
        }

        addEvent(timeMarker, actor, "MOVE", data);
    }

    /**
     * Log a land being played by a player.
     * This is always a player action, so the actor is the player who played the land.
     */
    public void logPlayLand(Card land, Player player, String timeMarker) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("card", getCardId(land));
        data.put("card_name", getActualCardName(land));
        data.put("player", getPlayerId(player));

        addEvent(timeMarker, getPlayerId(player), "PLAY_LAND", data);
        trackLandPlayed(player);
    }

    /**
     * Log a card being drawn by a player.
     * Drawing is a system action that happens to a player.
     */
    public void logDraw(Card card, Player player, String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        data.put("obj", getCardId(card));
        data.put("card_name", getActualCardName(card));
        data.put("from", getPlayerId(player) + ":library");
        data.put("to", getPlayerId(player) + ":hand");
        data.put("pos", "top");
        data.put("visibility", "private");

        // FIX P2: Add controller to DRAW events
        if (card.getController() != null) {
            data.put("controller", getPlayerId(card.getController()));
        }

        // FIX P3: Add owner to DRAW events
        if (card.getOwner() != null) {
            data.put("owner", getPlayerId(card.getOwner()));
        }

        // Draw is a system action, but we record which player drew
        addEvent(timeMarker, "SYS", "DRAW", data);
        trackCardDrawn(player);
    }

    /**
     * Log a mulligan decision by a player.
     * This is a player decision event.
     * Spec-compliant: includes hand_size_before, hand_size_after, mulligan_count,
     * cards_seen, cards_to_bottom, cards_to_bottom_names.
     */
    public void logMulligan(Player player, int handSizeBefore, int handSizeAfter,
                            int mulliganCount, boolean keepHand,
                            List<Card> cardsSeen, List<Card> cardsToBottom,
                            String timeMarker) {
        Map<String, Object> data = new HashMap<>();
        data.put("decision", keepHand ? "keep" : "mulligan");
        data.put("hand_size_before", handSizeBefore);
        data.put("hand_size_after", handSizeAfter);
        data.put("mulligan_count", mulliganCount);

        // Cards seen (optional, card IDs in hand when decision made)
        if (cardsSeen != null && !cardsSeen.isEmpty()) {
            List<String> seenIds = new ArrayList<>();
            for (Card c : cardsSeen) {
                seenIds.add(getCardId(c));
            }
            data.put("cards_seen", seenIds);
        }

        // Cards put to bottom (London mulligan, only on keep)
        if (cardsToBottom != null && !cardsToBottom.isEmpty()) {
            List<String> bottomIds = new ArrayList<>();
            List<String> bottomNames = new ArrayList<>();
            for (Card c : cardsToBottom) {
                bottomIds.add(getCardId(c));
                bottomNames.add(getActualCardName(c));
            }
            data.put("cards_to_bottom", bottomIds);
            data.put("cards_to_bottom_names", bottomNames);
        }

        addEvent(timeMarker, getPlayerId(player), "MULLIGAN", data);
    }

    /**
     * Legacy method for backwards compatibility.
     * @deprecated Use {@link #logMulligan(Player, int, int, int, boolean, List, List, String)} instead.
     */
    @Deprecated
    public void logMulligan(Player player, int cardsKept, boolean keepHand, String timeMarker) {
        logMulligan(player, keepHand ? cardsKept : cardsKept + 1, cardsKept,
                    keepHand ? 0 : 1, keepHand, null, null, timeMarker);
    }

    /**
     * Log a card being discarded by a player.
     * This can be either a player action (choosing to discard) or forced.
     */
    public void logDiscard(Card card, Player player, boolean isChoice, String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        data.put("obj", getCardId(card));
        data.put("card_name", getActualCardName(card));
        data.put("from", getPlayerId(player) + ":hand");
        data.put("to", getPlayerId(player) + ":graveyard");
        data.put("forced", !isChoice);

        // If player chose which card to discard, they are the actor
        String actor = isChoice ? getPlayerId(player) : "SYS";
        addEvent(timeMarker, actor, "DISCARD", data);
    }

    /**
     * Log a spell being cast with full details including play mode and total cost.
     */
    public void logCast(Card card, Player caster, String timeMarker, forge.game.spellability.SpellAbility sa) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("card", getCardId(card));
        data.put("card_name", getActualCardName(card));

        // Determine play mode
        String playMode = determinePlayMode(sa);
        data.put("play_mode", playMode);

        Map<String, Object> cost = new HashMap<>();

        // Get the mana cost that was actually paid
        String manaPaid = getManaPaid(sa);
        cost.put("mana", Arrays.asList(manaPaid));

        // Calculate total CMC paid
        int totalCostPaid = calculateTotalCostPaid(sa);
        cost.put("total_mana_value", totalCostPaid);

        // List additional costs
        List<String> additionalCosts = getAdditionalCosts(sa);
        cost.put("additional", additionalCosts);

        // Alternative cost type if used
        String alternativeCostType = getAlternativeCostType(sa);
        cost.put("alternative", alternativeCostType);

        data.put("cost", cost);

        data.put("modes", new ArrayList<>());
        data.put("x", sa != null ? sa.getXManaCostPaid() : null);
        data.put("targets", new ArrayList<>());
        data.put("choices", new HashMap<>());

        addEvent(timeMarker, getPlayerId(caster), "CAST", data);
        trackSpellCast(caster);
    }

    /**
     * Legacy method for backwards compatibility.
     */
    public void logCast(Card card, Player caster, String timeMarker) {
        logCast(card, caster, timeMarker, null);
    }

    /**
     * Determine the play mode of a spell/ability.
     */
    private String determinePlayMode(forge.game.spellability.SpellAbility sa) {
        if (sa == null) {
            return "normal";
        }

        // Check for face-down casting
        if (sa.isCastFaceDown()) {
            if (sa.hasParam("MorphCost")) {
                return "morph";
            }
            if (sa.hasParam("DisguiseCost")) {
                return "disguise";
            }
            return "face_down";
        }

        // Check alternative costs
        forge.game.spellability.AlternativeCost altCost = sa.getAlternativeCost();
        if (altCost != null) {
            return switch (altCost) {
                case Flashback -> "flashback";
                case Foretold -> "foretold";
                case Escape -> "escape";
                case Madness -> "madness";
                case Evoke -> "evoke";
                case Dash -> "dash";
                case Surge -> "surge";
                case Emerge -> "emerge";
                case Disturb -> "disturb";
                case Spectacle -> "spectacle";
                case Blitz -> "blitz";
                case Warp -> "warp";
                case Harmonize -> "harmonize";
                case Overload -> "overload";
                case Bestow -> "bestow";
                case Awaken -> "awaken";
                case Mutate -> "mutate";
                case Prowl -> "prowl";
                case Plotted -> "plotted";
                default -> "alternative:" + altCost.name().toLowerCase();
            };
        }

        // Check for special casting methods
        if (sa.isAftermath()) {
            return "aftermath";
        }
        if (sa.isKicked()) {
            return "kicked";
        }
        if (sa.isBuyback()) {
            return "buyback";
        }

        return "normal";
    }

    /**
     * Get the mana that was actually paid for this spell.
     */
    private String getManaPaid(forge.game.spellability.SpellAbility sa) {
        if (sa == null) {
            return "0";
        }

        List<forge.game.mana.Mana> payingMana = sa.getPayingMana();
        if (payingMana == null || payingMana.isEmpty()) {
            // Fall back to the card's mana cost
            Card card = sa.getHostCard();
            if (card != null && card.getManaCost() != null) {
                return card.getManaCost().toString();
            }
            return "0";
        }

        // Build the mana string from what was actually paid
        StringBuilder manaStr = new StringBuilder();
        Map<String, Integer> colorCounts = new LinkedHashMap<>();
        int colorless = 0;

        for (forge.game.mana.Mana m : payingMana) {
            if (m.isColorless()) {
                colorless++;
            } else {
                String color = manaColorToString(m.getColor());
                colorCounts.merge(color, 1, Integer::sum);
            }
        }

        // Format: {C}{W}{W}{U} etc.
        if (colorless > 0) {
            manaStr.append("{").append(colorless).append("}");
        }
        for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                manaStr.append("{").append(entry.getKey()).append("}");
            }
        }

        return !manaStr.isEmpty() ? manaStr.toString() : "0";
    }

    /**
     * Convert mana color byte to string representation.
     */
    private String manaColorToString(byte color) {
        if ((color & forge.card.mana.ManaAtom.WHITE) != 0) return "W";
        if ((color & forge.card.mana.ManaAtom.BLUE) != 0) return "U";
        if ((color & forge.card.mana.ManaAtom.BLACK) != 0) return "B";
        if ((color & forge.card.mana.ManaAtom.RED) != 0) return "R";
        if ((color & forge.card.mana.ManaAtom.GREEN) != 0) return "G";
        if ((color & forge.card.mana.ManaAtom.COLORLESS) != 0) return "C";
        return "1"; // Generic/Unknown
    }

    /**
     * Calculate the total mana value paid for this spell.
     */
    private int calculateTotalCostPaid(forge.game.spellability.SpellAbility sa) {
        if (sa == null) {
            return 0;
        }

        List<forge.game.mana.Mana> payingMana = sa.getPayingMana();
        if (payingMana != null) {
            return payingMana.size();
        }

        // Fall back to card's CMC
        Card card = sa.getHostCard();
        if (card != null) {
            return card.getCMC();
        }
        return 0;
    }

    /**
     * Get additional costs that were paid.
     */
    private List<String> getAdditionalCosts(forge.game.spellability.SpellAbility sa) {
        List<String> additionalCosts = new ArrayList<>();

        if (sa == null) {
            return additionalCosts;
        }

        // Check for common additional costs
        if (sa.isKicked()) {
            additionalCosts.add("kicker");
        }
        if (sa.isBuyback()) {
            additionalCosts.add("buyback");
        }
        if (sa.isEntwine()) {
            additionalCosts.add("entwine");
        }
        if (sa.isOptionalCostPaid(forge.game.spellability.OptionalCost.Retrace)) {
            additionalCosts.add("retrace");
        }
        if (sa.isOptionalCostPaid(forge.game.spellability.OptionalCost.Jumpstart)) {
            additionalCosts.add("jump-start");
        }
        if (sa.isOptionalCostPaid(forge.game.spellability.OptionalCost.Bargain)) {
            additionalCosts.add("bargain");
        }
        if (sa.costHasManaX()) {
            Integer xPaid = sa.getXManaCostPaid();
            if (xPaid != null && xPaid > 0) {
                additionalCosts.add("X=" + xPaid);
            }
        }

        return additionalCosts;
    }

    /**
     * Get the alternative cost type if one was used.
     */
    private String getAlternativeCostType(forge.game.spellability.SpellAbility sa) {
        if (sa == null) {
            return null;
        }

        forge.game.spellability.AlternativeCost altCost = sa.getAlternativeCost();
        if (altCost != null) {
            return altCost.name().toLowerCase();
        }
        return null;
    }

    /**
     * Log a spell being put on the stack.
     */
    public void logPutOnStack(Card card, Player controller, String timeMarker) {
        flushPendingPhase(); // Something happened! Log the phase.

        String stackId = generateStackId();
        // Track card→stackId for RESOLVE events
        cardToStackId.put(card, stackId);

        Map<String, Object> data = new HashMap<>();
        data.put("stack", stackId);
        data.put("kind", "SPELL");
        data.put("source", getCardId(card));
        data.put("source_name", getActualCardName(card));
        data.put("controller", getPlayerId(controller));
        data.put("card", getCardId(card));
        data.put("card_name", getActualCardName(card));
        data.put("targets", new ArrayList<>());
        data.put("choices", new HashMap<>());

        addEvent(timeMarker, "SYS", "PUT_ON_STACK", data);
    }

    /**
     * Log an activated ability (spec §2.1).
     * Emits ACTIVATE event for non-spell, non-trigger abilities.
     */
    public void logActivate(Card card, Player activator, forge.game.spellability.SpellAbility sa, String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        data.put("card", getCardId(card));
        data.put("card_name", getActualCardName(card));
        data.put("ability", sa != null ? sa.getStackDescription() : "unknown ability");
        data.put("controller", getPlayerId(activator));
        data.put("targets", new ArrayList<>());
        data.put("choices", new HashMap<>());

        addEvent(timeMarker, getPlayerId(activator), "ACTIVATE", data);
        trackAbilityActivated(activator);
    }

    /**
     * Log a triggered ability (spec §2.2).
     * Emits TRIGGER event when an ability triggers.
     */
    public void logTrigger(Card source, Player controller, forge.game.spellability.SpellAbility sa, String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        data.put("source", getCardId(source));
        data.put("source_name", getActualCardName(source));
        data.put("trigger", sa != null ? sa.getStackDescription() : "unknown trigger");
        data.put("controller", getPlayerId(controller));

        addEvent(timeMarker, "SYS", "TRIGGER", data);
    }

    /**
     * Log a spell/ability resolving (spec §2.3).
     * Emits RESOLVE event when a spell or ability resolves from the stack.
     */
    public void logResolve(Card card, boolean fizzled, String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        // Look up the stack ID assigned when the card was put on the stack
        String stackId = cardToStackId.remove(card);
        data.put("stack", stackId != null ? stackId : "unknown");
        data.put("card", getCardId(card));
        data.put("card_name", getActualCardName(card));
        data.put("fizzled", fizzled);

        addEvent(timeMarker, "SYS", "RESOLVE", data);
    }

    /**
     * Log attackers being declared (spec §2.5).
     * Emits DECLARE_ATTACKERS event with attacker→defender mapping.
     */
    public void logDeclareAttackers(Player attackingPlayer,
                                     com.google.common.collect.Multimap<GameEntity, Card> attackersMap,
                                     String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        Map<String, Object> attackers = new HashMap<>();

        for (Map.Entry<GameEntity, java.util.Collection<Card>> entry : attackersMap.asMap().entrySet()) {
            GameEntity defender = entry.getKey();
            String defenderId;
            if (defender instanceof Player) {
                defenderId = getPlayerId((Player) defender);
            } else if (defender instanceof Card) {
                defenderId = getCardId((Card) defender);
            } else {
                defenderId = defender.toString();
            }

            for (Card attacker : entry.getValue()) {
                attackers.put(getCardId(attacker), defenderId);
            }
        }

        data.put("attackers", attackers);
        data.put("attacking_player", getPlayerId(attackingPlayer));

        addEvent(timeMarker, getPlayerId(attackingPlayer), "DECLARE_ATTACKERS", data);
    }

    /**
     * Log blockers being declared (spec §2.6).
     * Note: Simplified to accept generic map due to view-type refactoring.
     */
    public void logDeclareBlockers(Player defendingPlayer,
                                    com.google.common.collect.Multimap<Card, Card> blockersMap,
                                    String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        Map<String, Object> blockers = new HashMap<>();

        for (Map.Entry<Card, java.util.Collection<Card>> att : blockersMap.asMap().entrySet()) {
            Card attacker = att.getKey();
            List<String> blockerIds = new ArrayList<>();
            for (Card blocker : att.getValue()) {
                blockerIds.add(getCardId(blocker));
            }
            if (!blockerIds.isEmpty()) {
                blockers.put(getCardId(attacker), blockerIds);
            }
        }

        data.put("blockers", blockers);

        String actor = getPlayerId(defendingPlayer);
        addEvent(timeMarker, actor, "DECLARE_BLOCKERS", data);
    }

    /**
     * Log counter change on a card (spec §2.8).
     * Emits COUNTERS event for +1/+1, loyalty, and other counter changes.
     */
    public void logCounters(Card card, String counterType, int oldValue, int newValue, String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        data.put("obj", getCardId(card));
        data.put("card_name", getActualCardName(card));
        data.put("counter_type", counterType);
        data.put("delta", newValue - oldValue);
        data.put("new_total", newValue);

        addEvent(timeMarker, "SYS", "COUNTERS", data);
        trackCounterPlaced(newValue - oldValue);
    }

    /**
     * Log damage dealt.
     */
    public void logDamage(Card source, Object target, int amount, String damageType, String timeMarker) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("source", source != null ? getCardId(source) : "unknown");
        data.put("source_name", source != null ? getActualCardName(source) : "unknown");

        if (target instanceof Card) {
            data.put("target", getCardId((Card) target));
            data.put("target_name", getActualCardName((Card) target));
        } else if (target instanceof Player) {
            data.put("target", getPlayerId((Player) target));
            data.put("target_name", ((Player) target).getName());
        }

        data.put("amount", amount);
        data.put("type", damageType);
        data.put("prevented", 0);

        addEvent(timeMarker, "SYS", "DAMAGE", data);

        // Track damage for summaries
        Player sourceController = (source != null && source.getController() != null) ? source.getController() : null;
        Player targetPlayer = (target instanceof Player) ? (Player) target : null;
        trackDamageDealt(sourceController, targetPlayer, amount);
    }

    /**
     * Log player resources (land count and available mana) at the start of upkeep.
     * This provides valuable information for AI analysis and replay.
     */
    public void logResources(Player player, int landCount, int availableMana, String timeMarker) {
        // Don't flush pending phase - this is part of the phase transition, not a game action

        Map<String, Object> data = new HashMap<>();
        data.put("player", getPlayerId(player));
        data.put("land_count", landCount);
        data.put("available_mana", availableMana);

        addEvent(timeMarker, "SYS", "RESOURCES", data);
    }

    /**
     * Log life total change.
     */
    public void logLifeChange(Player player, int delta, int newTotal, String cause, String timeMarker) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("player", getPlayerId(player));
        data.put("delta", delta);
        data.put("new_total", newTotal);
        data.put("cause", cause);

        addEvent(timeMarker, "SYS", "LIFE", data);
    }

    /**
     * Log an active player change (turn transition).
     * Spec P2.10: Emits ACTIVE_PLAYER_CHANGE so consumers can detect turn boundaries.
     */
    public void logActivePlayerChange(Player previousPlayer, Player newPlayer, int turnNumber, String timeMarker) {
        Map<String, Object> data = new HashMap<>();
        data.put("previous_player", previousPlayer != null ? getPlayerId(previousPlayer) : null);
        data.put("new_player", getPlayerId(newPlayer));
        data.put("turn_number", turnNumber);

        addEvent(timeMarker, "SYS", "ACTIVE_PLAYER_CHANGE", data);
    }

    /**
     * Log a card being tapped or untapped.
     * Spec P2.7: Emits TAP event for tap/untap state changes.
     */
    public void logTap(Card card, boolean tapped, String timeMarker) {
        flushPendingPhase();

        Map<String, Object> data = new HashMap<>();
        data.put("obj", getCardId(card));
        data.put("card_name", getActualCardName(card));
        data.put("tapped", tapped);

        addEvent(timeMarker, "SYS", "TAP", data);
    }

    /**
     * Build a time marker string from the current game phase/turn state.
     * Used when triggering a learning marker from outside the event-listener chain.
     *
     * @return time marker in the format {@code T<turn>.<phase>}
     */
    public String buildCurrentTimeMarker() {
        if (game.getPhaseHandler() == null) {
            return "T0.UNKNOWN";
        }
        int turn = game.getPhaseHandler().getTurn();
        String phase = game.getPhaseHandler().getPhase() != null
                ? game.getPhaseHandler().getPhase().toString()
                : "UNKNOWN";
        return "T" + turn + "." + phase;
    }

    /**
     * Log a learning marker using the current game state as the time reference.
     * Convenience overload — the time marker is generated automatically.
     *
     * @param player   The player placing the marker
     * @param label    Short description or question about this moment
     * @param category One of: decision_review, mistake, turning_point,
     *                 interesting_interaction, sideboard_note, general
     */
    public void logLearningMarker(Player player, String label, String category) {
        logLearningMarker(player, label, category, buildCurrentTimeMarker());
    }

    /**
     * Log a learning marker (player bookmark) at the current game state.
     * Spec 1.3.0: Emits LEARNING_MARKER L1 event and adds a top-level summary entry.
     * @param player The player placing the marker
     * @param label Short description or question about this moment
     * @param category One of: decision_review, mistake, turning_point, interesting_interaction, sideboard_note, general
     * @param timeMarker Current time marker
     */
    public void logLearningMarker(Player player, String label, String category, String timeMarker) {
        learningMarkerCounter++;
        String markerId = "lm-" + learningMarkerCounter;
        String createdAt = ISO_FORMAT.format(Instant.now());

        // Emit L1 event
        Map<String, Object> data = new HashMap<>();
        data.put("marker_id", markerId);
        data.put("label", label);
        data.put("category", category != null ? category : "general");
        data.put("created_at", createdAt);

        int currentEventIndex = eventIndex; // capture before addEvent increments it
        addEvent(timeMarker, getPlayerId(player), "LEARNING_MARKER", data);

        // Create top-level summary entry with snapshot
        ReplayLog.LearningMarker marker = new ReplayLog.LearningMarker();
        marker.setMarkerId(markerId);
        marker.setEventIndex(currentEventIndex);
        marker.setT(timeMarker);
        marker.setPlayer(getPlayerId(player));
        marker.setLabel(label);
        marker.setCategory(category != null ? category : "general");
        marker.setCreatedAt(createdAt);
        marker.setNotes("");

        // Build lightweight snapshot from current game state
        ReplayLog.LearningMarker.Snapshot snapshot = new ReplayLog.LearningMarker.Snapshot();
        if (game.getPhaseHandler() != null) {
            snapshot.setTurn(game.getPhaseHandler().getTurn());
            snapshot.setPhase(game.getPhaseHandler().getPhase() != null ?
                    game.getPhaseHandler().getPhase().toString() : "UNKNOWN");
            snapshot.setActivePlayer(game.getPhaseHandler().getPlayerTurn() != null ?
                    getPlayerId(game.getPhaseHandler().getPlayerTurn()) : getPlayerId(player));
        }

        for (Player p : game.getPlayers()) {
            String pId = getPlayerId(p);
            snapshot.getLifeTotals().put(pId, p.getLife());
            snapshot.getCardsInHand().put(pId, p.getCardsIn(ZoneType.Hand).size());
            snapshot.getBattlefieldCount().put(pId, p.getCardsIn(ZoneType.Battlefield).size());
        }
        snapshot.setStackEmpty(game.getStackZone().isEmpty());

        marker.setSnapshot(snapshot);
        replayLog.addLearningMarker(marker);
    }

    /**
     * Log a phase change.
     * OPTIMIZATION: Phase events are buffered and only logged if something happens in that phase.
     * Empty phases (especially UNTAP, DRAW, CLEANUP, UPKEEP, END_OF_TURN) are skipped.
     */
    public void logPhaseChange(String phase, String step, Player activePlayer, String timeMarker) {
        // Flush previous pending phase event if we're entering a new phase
        if (pendingPhaseEvent != null && !isSamePhase(phase, currentPhase)) {
            // Previous phase had no events - skip it!
            long phaseDuration = System.currentTimeMillis() - phaseStartTime;
            if (phaseDuration > 5000) { // Log if phase took >5 seconds (player was thinking)
                System.out.println("[Replay Optimization] Skipped empty phase: " + currentPhase +
                                   " (duration: " + phaseDuration + "ms)");
            }
            pendingPhaseEvent = null;
        }

        // Check if this is an "always empty" phase
        if (ALWAYS_EMPTY_PHASES.contains(phase)) {
            // Skip these phases entirely - they never have meaningful events
            currentPhase = phase;
            phaseStartTime = System.currentTimeMillis();
            return;
        }

        // Create phase event but don't add it yet - wait to see if something happens
        Map<String, Object> data = new HashMap<>();
        data.put("phase", phase);
        data.put("step", step);
        data.put("active_player", getPlayerId(activePlayer));

        // Store the pending event data
        pendingPhaseEvent = new HashMap<>();
        pendingPhaseEvent.put("timeMarker", timeMarker);
        pendingPhaseEvent.put("data", data);

        currentPhase = phase;
        phaseStartTime = System.currentTimeMillis();
    }

    /**
     * Check if two phases are the same (ignoring step differences).
     */
    private boolean isSamePhase(String phase1, String phase2) {
        if (phase1 == null || phase2 == null) return false;
        return phase1.equals(phase2);
    }

    /**
     * Flush pending phase event before adding any important event.
     * This ensures the phase is logged if something actually happens.
     */
    private void flushPendingPhase() {
        if (pendingPhaseEvent != null) {
            // Something happened! Log the phase event
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) pendingPhaseEvent.get("data");
            String timeMarker = (String) pendingPhaseEvent.get("timeMarker");
            addEvent(timeMarker, "SYS", "PHASE_CHANGE", data);
            pendingPhaseEvent = null;
        }
    }

    /**
     * Format a zone for the log.
     */
    private String formatZone(ZoneType zone, Player player) {
        return switch (zone) {
            case Battlefield -> "battlefield";
            case Stack -> "stack";
            case Exile -> "exile";
            case Hand -> getPlayerId(player) + ":hand";
            case Library -> getPlayerId(player) + ":library";
            case Graveyard -> getPlayerId(player) + ":graveyard";
            case Command -> getPlayerId(player) + ":command";
            default -> zone.name().toLowerCase();
        };
    }

    /**
     * Check if a zone is public.
     */
    private boolean isPublicZone(ZoneType zone) {
        return zone == ZoneType.Battlefield ||
               zone == ZoneType.Stack ||
               zone == ZoneType.Graveyard ||
               zone == ZoneType.Exile ||
               zone == ZoneType.Command;
    }

    /**
     * Generate time marker from turn and phase.
     */
    public String generateTimeMarker(int turn, String phase) {
        return String.format("T%d.%s", turn, phase);
    }

    // =========================================================================
    //  Turn Summary & Game Summary tracking
    // =========================================================================

    private void incrementCounter(Map<String, Integer> map, String key, int delta) {
        map.merge(key, delta, Integer::sum);
    }

    private int getCounter(Map<String, Integer> map, String key) {
        return map.getOrDefault(key, 0);
    }

    /**
     * FIX P1: Capture a full GameState snapshot from the current game state.
     * This includes all zones (critically P1:hand, P2:hand, etc.) and all objects.
     */
    private GameState captureFullGameState() {
        GameState state = new GameState();
        
        // Capture turn/phase info
        if (game.getPhaseHandler() != null) {
            state.setTurn(game.getPhaseHandler().getTurn());
            state.setPhase(game.getPhaseHandler().getPhase() != null ? 
                game.getPhaseHandler().getPhase().toString() : "UNKNOWN");
            state.setStep(game.getPhaseHandler().getPhase() != null ? 
                game.getPhaseHandler().getPhase().toString() : "UNKNOWN");
            state.setPriority(null); // Not tracked
            Player activePlayer = game.getPhaseHandler().getPlayerTurn();
            if (activePlayer != null) {
                state.setActivePlayer(getPlayerId(activePlayer));
            }
        }

        // Capture player states
        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);
            GameState.PlayerState playerState = new GameState.PlayerState();
            playerState.setLife(player.getLife());
            playerState.setManaPool(new ArrayList<>()); // Spec: always []
            playerState.setMaxHandSize(player.getMaxHandSize());
            playerState.setLandsPlayedThisTurn(player.getLandsPlayedThisTurn());
            state.getPlayers().put(playerId, playerState);
        }

        // Capture all zones
        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);

            // Capture hand (CRITICAL for frontend display)
            List<String> handCards = new ArrayList<>();
            for (Card card : player.getZone(ZoneType.Hand)) {
                String cardId = getCardId(card);
                handCards.add(cardId);
                // Ensure object state exists
                GameState.ObjectState objState = createObjectState(card, playerId + ":hand", -1);
                state.getObjects().put(cardId, objState);
            }
            state.getZones().put(playerId + ":hand", handCards);

            // Capture library
            List<String> libraryCards = new ArrayList<>();
            int position = 0;
            for (Card card : player.getZone(ZoneType.Library)) {
                String cardId = getCardId(card);
                libraryCards.add(cardId);
                GameState.ObjectState objState = createObjectState(card, playerId + ":library", position++);
                state.getObjects().put(cardId, objState);
            }
            Map<String, Object> libraryInfo = new HashMap<>();
            libraryInfo.put("count", libraryCards.size());
            libraryInfo.put("cards", libraryCards);
            state.getZones().put(playerId + ":library", libraryInfo);

            // Capture graveyard
            List<String> graveyardCards = new ArrayList<>();
            for (Card card : player.getZone(ZoneType.Graveyard)) {
                String cardId = getCardId(card);
                graveyardCards.add(cardId);
                GameState.ObjectState objState = createObjectState(card, playerId + ":graveyard", -1);
                state.getObjects().put(cardId, objState);
            }
            state.getZones().put(playerId + ":graveyard", graveyardCards);

            // Capture exile
            List<String> exileCards = new ArrayList<>();
            for (Card card : player.getZone(ZoneType.Exile)) {
                String cardId = getCardId(card);
                exileCards.add(cardId);
                GameState.ObjectState objState = createObjectState(card, playerId + ":exile", -1);
                state.getObjects().put(cardId, objState);
            }
            state.getZones().put(playerId + ":exile", exileCards);

            // Capture command zone (for Commander)
            List<String> commandCards = new ArrayList<>();
            for (Card card : player.getZone(ZoneType.Command)) {
                String cardId = getCardId(card);
                commandCards.add(cardId);
                GameState.ObjectState objState = createObjectState(card, playerId + ":command", -1);
                state.getObjects().put(cardId, objState);
            }
            if (!commandCards.isEmpty()) {
                state.getZones().put(playerId + ":command", commandCards);
            }
        }

        // Capture battlefield
        List<String> battlefieldCards = new ArrayList<>();
        for (Card card : game.getCardsIn(ZoneType.Battlefield)) {
            String cardId = getCardId(card);
            battlefieldCards.add(cardId);
            GameState.ObjectState objState = createObjectState(card, "battlefield", -1);
            state.getObjects().put(cardId, objState);
        }
        state.getZones().put("battlefield", battlefieldCards);

        // Capture stack
        List<String> stackCards = new ArrayList<>();
        for (Card card : game.getCardsIn(ZoneType.Stack)) {
            String cardId = getCardId(card);
            stackCards.add(cardId);
            GameState.ObjectState objState = createObjectState(card, "stack", -1);
            state.getObjects().put(cardId, objState);
        }
        state.getZones().put("stack", stackCards);

        // Capture shared exile zone (if not already captured per-player)
        state.getZones().putIfAbsent("exile", new ArrayList<>());

        return state;
    }

    /**
     * Called when a new turn begins. Flushes the previous turn's summary and resets per-turn counters.
     */
    public void onTurnBegin(int turnNumber, Player activePlayer) {
        // Flush the previous turn's summary (skip turn 0 / pregame)
        if (trackingTurn > 0) {
            flushTurnSummary();
        }

        trackingTurn = turnNumber;
        trackingActivePlayer = getPlayerId(activePlayer);

        // FIX P1: Capture turn-start snapshot for L2 generation
        turnStartSnapshot = captureFullGameState();

        // Reset per-turn counters
        turnLandsPlayed.clear();
        turnCardsDrawn.clear();
        turnSpellsCast.clear();
        turnAbilitiesActivated.clear();
        turnDamageDealt.clear();
        turnDamageReceived.clear();
    }

    /**
     * Capture the current turn's stats and add a TurnSummary to the replay log.
     */
    private void flushTurnSummary() {
        TurnSummary summary = new TurnSummary(trackingTurn, trackingActivePlayer);

        for (Player player : game.getPlayers()) {
            String pid = getPlayerId(player);
            TurnSummary.PlayerTurnStats stats = new TurnSummary.PlayerTurnStats();

            int landsPlayed = getCounter(turnLandsPlayed, pid);
            stats.setLandsPlayed(landsPlayed);
            stats.setLandDropRating(landsPlayed == 0 ? "bad" : landsPlayed == 1 ? "good" : "super");
            stats.setCardsDrawn(getCounter(turnCardsDrawn, pid));
            stats.setSpellsCast(getCounter(turnSpellsCast, pid));
            stats.setAbilitiesActivated(getCounter(turnAbilitiesActivated, pid));
            stats.setDamageDealt(getCounter(turnDamageDealt, pid));
            stats.setDamageTaken(getCounter(turnDamageReceived, pid));

            // Snapshot current game state
            stats.setLife(player.getLife());
            stats.setCardsInHand(player.getCardsIn(ZoneType.Hand).size());

            int landCount = 0;
            int creatureCount = 0;
            int permanentCount = 0;
            for (Card c : player.getCardsIn(ZoneType.Battlefield)) {
                permanentCount++;
                if (c.isLand()) landCount++;
                if (c.isCreature()) creatureCount++;
            }
            stats.setLandCount(landCount);
            stats.setCreaturesOnBattlefield(creatureCount);
            stats.setPermanentsOnBattlefield(permanentCount);

            // Estimate available mana from untapped sources
            int mana = 0;
            for (Card c : player.getCardsIn(ZoneType.Battlefield)) {
                if (c.isUntapped() && !c.getManaAbilities().isEmpty()) {
                    mana++;
                }
            }
            stats.setAvailableMana(mana);

            // Track peak mana for game summary
            int currentPeak = getCounter(gamePeakMana, pid);
            if (mana > currentPeak) {
                gamePeakMana.put(pid, mana);
            }

            // Track missed land drops (active player only, not turn 0)
            if (pid.equals(trackingActivePlayer) && landsPlayed == 0 && trackingTurn > 1) {
                incrementCounter(gameMissedLandDrops, pid, 1);
            }

            summary.getPlayers().put(pid, stats);
        }

        replayLog.addTurnSummary(summary);

        // FIX P1: Generate L2 Unit for this turn at end of CLEANUP
        generateL2UnitForTurn();
    }

    /**
     * FIX P1: Generate and add an L2 Unit for the completed turn.
     * Creates a snapshot with before (turn start) and after (turn end) states.
     */
    private void generateL2UnitForTurn() {
        if (turnStartSnapshot == null) {
            return; // No turn start captured yet
        }

        // Capture turn-end snapshot
        GameState turnEndSnapshot = captureFullGameState();

        // Create L2 Unit
        L2Unit unit = new L2Unit();
        unit.setU(replayLog.getViewsL2().size()); // Sequential index

        // Time markers: T<turn>.DRAW:1 to T<turn>.CLEANUP:last
        String tStart = "T" + trackingTurn + ".DRAW:1";
        String tEnd = "T" + trackingTurn + ".CLEANUP:last";
        unit.setTStart(tStart);
        unit.setTEnd(tEnd);

        // L1 range: find all events in this turn
        List<L1Event> events = replayLog.getLogL1();
        int startIdx = -1;
        int endIdx = -1;
        for (int i = 0; i < events.size(); i++) {
            L1Event evt = events.get(i);
            String timeMarker = evt.getT();
            if (timeMarker != null && timeMarker.startsWith("T" + trackingTurn + ".")) {
                if (startIdx == -1) {
                    startIdx = i;
                }
                endIdx = i;
            }
        }
        if (startIdx >= 0) {
            unit.setL1Range(new int[]{startIdx, endIdx});
        } else {
            unit.setL1Range(new int[]{0, 0});
        }

        // Decision events: find all player decision events in this turn
        List<Integer> decisionEvents = new ArrayList<>();
        for (int i = startIdx; i >= 0 && i <= endIdx; i++) {
            L1Event evt = events.get(i);
            String type = evt.getType();
            if (type != null && (type.equals("CAST") || type.equals("ACTIVATE") ||
                type.equals("DECLARE_ATTACKERS") || type.equals("DECLARE_BLOCKERS") ||
                type.equals("MULLIGAN") || type.equals("PLAY_LAND") || type.equals("CHOOSE"))) {
                decisionEvents.add(evt.getI());
            }
        }
        unit.setDecisionEvents(decisionEvents);

        // Set before and after snapshots
        unit.setBefore(turnStartSnapshot);
        unit.setAfter(turnEndSnapshot);

        // Stack: empty for now (could be populated from events)
        unit.setStack(new ArrayList<>());

        // Annotations: empty for now
        unit.setAnnotations(new L2Unit.Annotations());

        // Add to replay log
        replayLog.addL2Unit(unit);
    }

    // --- Tracking hooks called from log* methods ---

    /** Track a land play for turn/game summary. */
    public void trackLandPlayed(Player player) {
        String pid = getPlayerId(player);
        incrementCounter(turnLandsPlayed, pid, 1);
        incrementCounter(gameTotalLandsPlayed, pid, 1);
    }

    /** Track a card draw for turn/game summary. */
    public void trackCardDrawn(Player player) {
        String pid = getPlayerId(player);
        incrementCounter(turnCardsDrawn, pid, 1);
        incrementCounter(gameTotalCardsDrawn, pid, 1);
    }

    /** Track a spell cast for turn/game summary. */
    public void trackSpellCast(Player player) {
        String pid = getPlayerId(player);
        incrementCounter(turnSpellsCast, pid, 1);
        incrementCounter(gameTotalSpellsCast, pid, 1);
    }

    /** Track an activated ability for turn/game summary. */
    public void trackAbilityActivated(Player player) {
        String pid = getPlayerId(player);
        incrementCounter(turnAbilitiesActivated, pid, 1);
        incrementCounter(gameTotalAbilitiesActivated, pid, 1);
    }

    /** Track damage dealt for turn/game summary. */
    public void trackDamageDealt(Player sourceController, Player target, int amount) {
        if (sourceController != null) {
            incrementCounter(turnDamageDealt, getPlayerId(sourceController), amount);
            incrementCounter(gameTotalDamageDealt, getPlayerId(sourceController), amount);
        }
        if (target != null) {
            incrementCounter(turnDamageReceived, getPlayerId(target), amount);
            incrementCounter(gameTotalDamageReceived, getPlayerId(target), amount);
        }
    }

    /** Track a creature entering the battlefield. */
    public void trackCreaturePlayed(Player player) {
        incrementCounter(gameTotalCreaturesPlayed, getPlayerId(player), 1);
    }

    /** Track counter changes for game summary. */
    public void trackCounterPlaced(int delta) {
        if (delta > 0) {
            // We don't know the player, so track globally
            incrementCounter(gameTotalCountersPlaced, "GLOBAL", delta);
        }
    }

    /**
     * Build the game_summary at end of game. Call this after setGameOutcome().
     */
    public void buildGameSummary() {
        // Flush the last turn
        if (trackingTurn > 0) {
            flushTurnSummary();
        }

        GameSummary gs = new GameSummary();
        ReplayMeta meta = replayLog.getMeta();
        gs.setTotalTurns(meta.getTurns() != null ? meta.getTurns() : trackingTurn);
        gs.setWinner(meta.getWinner());
        gs.setWinCondition(meta.getWinCondition());
        long durationMs = System.currentTimeMillis() - gameStartTime;
        gs.setDurationSeconds((int) (durationMs / 1000));

        int totalTurns = Math.max(1, gs.getTotalTurns());

        // Use allPlayersOrdered (stable, includes eliminated players) instead of
        // game.getPlayers() which only returns still-alive players at game end.
        for (Player player : allPlayersOrdered) {
            String pid = getPlayerId(player);
            GameSummary.PlayerGameStats pgs = new GameSummary.PlayerGameStats();

            int drawn = getCounter(gameTotalCardsDrawn, pid);
            int cast = getCounter(gameTotalSpellsCast, pid);

            pgs.setTotalCardsDrawn(drawn);
            pgs.setCardDrawRate(Math.round(((double) drawn / totalTurns) * 100.0) / 100.0);
            pgs.setTotalSpellsCast(cast);
            pgs.setSpellVelocity(Math.round(((double) cast / totalTurns) * 100.0) / 100.0);
            pgs.setTotalAbilitiesActivated(getCounter(gameTotalAbilitiesActivated, pid));
            pgs.setMissedLandDrops(getCounter(gameMissedLandDrops, pid));
            pgs.setTotalLandsPlayed(getCounter(gameTotalLandsPlayed, pid));
            pgs.setPeakMana(getCounter(gamePeakMana, pid));
            pgs.setTotalDamageDealt(getCounter(gameTotalDamageDealt, pid));
            pgs.setTotalDamageReceived(getCounter(gameTotalDamageReceived, pid));
            pgs.setTotalCreaturesPlayed(getCounter(gameTotalCreaturesPlayed, pid));
            pgs.setStartingLife(player.getStartingLife());
            pgs.setEndingLife(player.getLife());
            pgs.setLifeDelta(player.getLife() - player.getStartingLife());
            pgs.setTotalCountersPlaced(getCounter(gameTotalCountersPlaced, pid));

            gs.getPlayers().put(pid, pgs);
        }

        replayLog.setGameSummary(gs);
    }

    /**
     * Export the replay log to a JSON file.
     *
     * <p>The filename prefix depends on whether the game was a headless AI simulation
     * (started via the CLI {@code sim} command) or a human-played game:</p>
     * <ul>
     *   <li>{@code sim_<GameType>_<timestamp>.json} — simulation/AI-only game</li>
     *   <li>{@code replay_<GameType>_<timestamp>.json} — human-played game</li>
     * </ul>
     */
    public File exportToFile(File outputDir) throws IOException {
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        return exportToFile(outputDir, timestamp);
    }

    public File exportToFile(File outputDir, String timestamp) throws IOException {
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDir);
            }
        }

        boolean isSimulation = game.getRules() != null && game.getRules().isSimulationMode();
        String prefix = isSimulation ? "sim_" : "replay_";

        String filename = String.format("%s%s_%s.json",
            prefix, replayLog.getMeta().getGameType(), timestamp);
        File outputFile = new File(outputDir, filename);

        ReplayJsonSerializer.writeToFile(replayLog, outputFile);

        return outputFile;
    }

    /**
     * Get the replay log object.
     */
    public ReplayLog getReplayLog() {
        return replayLog;
    }

    /**
     * Export to JSON string.
     */
    public String toJson() {
        return ReplayJsonSerializer.toJson(replayLog);
    }
}

