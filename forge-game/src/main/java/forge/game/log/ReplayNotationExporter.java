package forge.game.log;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.log.model.*;
import forge.game.player.Player;
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

        // Initialize zones
        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);
            Map<String, Object> libraryInfo = new HashMap<>();
            libraryInfo.put("count", player.getZone(ZoneType.Library).size());
            state.getZones().put(playerId + ":library", libraryInfo);

            state.getZones().put(playerId + ":hand", new ArrayList<>());
            state.getZones().put(playerId + ":graveyard", new ArrayList<>());
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
     * Get or create a stable ID for a card.
     */
    private String getCardId(Card card) {
        return cardIdMap.computeIfAbsent(card, c -> {
            cardIdCounter++;

            // Add to card index
            CardDefinition def = new CardDefinition();
            def.setName(c.getName());
            def.setCost(c.getManaCost() != null ? c.getManaCost().toString() : "");
            def.setType(c.getType().toString());
            replayLog.getCardIndex().put(c.getName(), def);

            return "c" + cardIdCounter;
        });
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
     * Log a zone change event.
     */
    public void logZoneChange(Card card, ZoneType from, ZoneType to, String timeMarker, Player owner) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("obj", getCardId(card));
        data.put("from", formatZone(from, owner));
        data.put("to", formatZone(to, owner));
        data.put("pos", "top");
        data.put("visibility", isPublicZone(to) ? "public" : "private");

        addEvent(timeMarker, "SYS", "MOVE", data);
    }

    /**
     * Log a spell being cast.
     */
    public void logCast(Card card, Player caster, String timeMarker) {
        flushPendingPhase(); // Something happened! Log the phase.

        Map<String, Object> data = new HashMap<>();
        data.put("card", getCardId(card));

        Map<String, Object> cost = new HashMap<>();
        cost.put("mana", Arrays.asList(card.getManaCost() != null ? card.getManaCost().toString() : "0"));
        cost.put("additional", new ArrayList<>());
        cost.put("alternative", null);
        data.put("cost", cost);

        data.put("modes", new ArrayList<>());
        data.put("x", null);
        data.put("targets", new ArrayList<>());
        data.put("choices", new HashMap<>());

        addEvent(timeMarker, getPlayerId(caster), "CAST", data);
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
        data.put("controller", getPlayerId(controller));
        data.put("card", getCardId(card));
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

        if (target instanceof Card) {
            data.put("target", getCardId((Card) target));
        } else if (target instanceof Player) {
            data.put("target", getPlayerId((Player) target));
        }

        data.put("amount", amount);
        data.put("type", damageType);
        data.put("prevented", 0);

        addEvent(timeMarker, "SYS", "DAMAGE", data);
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

