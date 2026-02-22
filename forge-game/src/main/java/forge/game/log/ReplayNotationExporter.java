package forge.game.log;

import forge.game.Game;
import forge.game.GameOutcome;
import forge.game.card.Card;
import forge.game.log.model.*;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Exports game logs in MTG Replay Notation format (JSON).
 * Implements the specification from MTG_REPLAY_NOTATION.md.
 */
public class ReplayNotationExporter {

    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final Game game;
    private final ReplayLog replayLog;
    private int eventIndex = 0;
    private final Map<Card, String> cardIdMap = new HashMap<>();
    private int cardIdCounter = 0;
    private int stackIdCounter = 0;
    private long gameStartTime = System.currentTimeMillis();

    // Phase-Event Buffering: Skip empty phases
    private Map<String, Object> pendingPhaseEvent = null;
    private String currentPhase = null;
    private long phaseStartTime = 0;
    private static final Set<String> ALWAYS_EMPTY_PHASES = new HashSet<>(Arrays.asList(
        "PREGAME", "UNTAP", "DRAW", "CLEANUP"
    ));

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
        meta.setGameId("game-" + UUID.randomUUID().toString());
        meta.setTimestamp(ISO_FORMAT.format(new Date()));

        if (game.getRules() != null && game.getRules().getGameType() != null) {
            meta.setGameType(game.getRules().getGameType().toString());
        } else {
            meta.setGameType("Unknown");
        }

        // Add player metadata
        for (Player player : game.getPlayers()) {
            ReplayMeta.PlayerMeta playerMeta = new ReplayMeta.PlayerMeta();
            playerMeta.setName(player.getName());

            // Add deck name and hash if available
            if (player.getRegisteredPlayer() != null &&
                player.getRegisteredPlayer().getDeck() != null) {
                forge.deck.Deck deck = player.getRegisteredPlayer().getDeck();
                playerMeta.setDeckName(deck.getName());
                String deckHash = calculateDeckHash(deck);
                playerMeta.setDeckHash(deckHash);
                // deck_link (spec v1.4.0): currently null – no mamo.games integration.
                // Future: build URL as https://mamo.games/deck/<uuid>#<DDMMYYYY>_<deck_hash>
                playerMeta.setDeckLink(null);
            }

            meta.getPlayers().put(getPlayerId(player), playerMeta);
        }

        // Set seed (use game's random seed if available)
        replayLog.setSeed(System.currentTimeMillis());

        // Initialize initial state
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
        int index = game.getPlayers().indexOf(player);
        return "P" + (index + 1);
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

        // Counters
        if (card.getCounters() != null && !card.getCounters().isEmpty()) {
            for (Map.Entry<forge.game.card.CounterType, Integer> counter : card.getCounters().entrySet()) {
                objState.getCounters().put(counter.getKey().getName(), counter.getValue());
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
                switch (reason) {
                    case LifeReachedZero:
                        return "life_zero";
                    case CommanderDamage:
                        return "commander_damage";
                    case Poisoned:
                        return "poison";
                    case Milled:
                        return "decked";
                    case Conceded:
                        return "concession";
                    case SpellEffect:
                    case OpponentWon:
                        return "alternate_win";
                    default:
                        return "unknown";
                }
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
     * Handles face-down cards by using the actual card name for the index.
     */
    private String getCardId(Card card) {
        return cardIdMap.computeIfAbsent(card, c -> {
            cardIdCounter++;
            String cardId = "c" + cardIdCounter;

            // Get the actual card name (even for face-down cards)
            String actualName = getActualCardName(c);

            // Add to card index with actual name as key
            // Use unique key to avoid collisions (cardId + name)
            String indexKey = actualName.isEmpty() ? cardId : actualName;

            // Only add if not already present with this name
            if (!replayLog.getCardIndex().containsKey(indexKey)) {
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

                replayLog.getCardIndex().put(indexKey, def);
            }

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

        addEvent(timeMarker, getPlayerId(player), "PLAY_LAND", data);
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

        // Draw is a system action, but we record which player drew
        addEvent(timeMarker, "SYS", "DRAW", data);
    }

    /**
     * Log a mulligan decision by a player.
     * This is a player decision event.
     */
    public void logMulligan(Player player, int cardsKept, boolean keepHand, String timeMarker) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", getPlayerId(player));
        data.put("cards_kept", cardsKept);
        data.put("decision", keepHand ? "keep" : "mulligan");

        addEvent(timeMarker, getPlayerId(player), "MULLIGAN", data);
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

        return manaStr.length() > 0 ? manaStr.toString() : "0";
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

        Map<String, Object> data = new HashMap<>();
        data.put("stack", generateStackId());
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
        switch (zone) {
            case Battlefield:
                return "battlefield";
            case Stack:
                return "stack";
            case Exile:
                return "exile";
            case Hand:
                return getPlayerId(player) + ":hand";
            case Library:
                return getPlayerId(player) + ":library";
            case Graveyard:
                return getPlayerId(player) + ":graveyard";
            case Command:
                return getPlayerId(player) + ":command";
            default:
                return zone.name().toLowerCase();
        }
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

    /**
     * Export the replay log to a JSON file.
     */
    public File exportToFile(File outputDir) throws IOException {
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDir);
            }
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String filename = String.format("replay_%s_%s.json",
            replayLog.getMeta().getGameType(), timestamp);
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

