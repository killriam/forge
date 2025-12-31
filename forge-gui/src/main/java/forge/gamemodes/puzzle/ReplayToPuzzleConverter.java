package forge.gamemodes.puzzle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts MTG Replay Notation (JSON) to Forge Puzzle Format (.pzl).
 *
 * Usage:
 *   ReplayToPuzzleConverter converter = new ReplayToPuzzleConverter();
 *   String puzzleContent = converter.convert(replayJson, targetTurn, targetPhase);
 *
 * Or from file:
 *   converter.convertFile("game_replay.json", "output.pzl", 3, "Main1");
 */
public class ReplayToPuzzleConverter {

    private static final Map<String, String> PHASE_MAPPING = new HashMap<>();
    static {
        PHASE_MAPPING.put("UPKEEP", "Upkeep");
        PHASE_MAPPING.put("UP", "Upkeep");
        PHASE_MAPPING.put("DRAW", "Draw");
        PHASE_MAPPING.put("MAIN1", "Main1");
        PHASE_MAPPING.put("MP1", "Main1");
        PHASE_MAPPING.put("COMBAT", "BeginCombat");
        PHASE_MAPPING.put("COMBAT_DECLARE_ATTACKERS", "DeclareAttackers");
        PHASE_MAPPING.put("COMBAT_DECLARE_BLOCKERS", "DeclareBlockers");
        PHASE_MAPPING.put("COMBAT_DAMAGE", "CombatDamage");
        PHASE_MAPPING.put("MAIN2", "Main2");
        PHASE_MAPPING.put("MP2", "Main2");
        PHASE_MAPPING.put("END", "EndOfTurn");
        PHASE_MAPPING.put("CLEANUP", "Cleanup");
    }

    /**
     * Convert a replay JSON string to puzzle format.
     *
     * @param replayJson The replay notation JSON string
     * @param targetTurn The turn number to extract (1-based)
     * @param targetPhase The phase to start at (e.g., "Main1", "Combat")
     * @return Puzzle format string (.pzl content)
     */
    public String convert(String replayJson, int targetTurn, String targetPhase) {
        JsonObject replay = JsonParser.parseString(replayJson).getAsJsonObject();
        return convertFromJson(replay, targetTurn, targetPhase, "Replay Scenario", 99);
    }

    /**
     * Convert with custom puzzle metadata.
     */
    public String convert(String replayJson, int targetTurn, String targetPhase,
                         String puzzleName, int turnLimit) {
        JsonObject replay = JsonParser.parseString(replayJson).getAsJsonObject();
        return convertFromJson(replay, targetTurn, targetPhase, puzzleName, turnLimit);
    }

    /**
     * Convert a replay file to a puzzle file.
     */
    public void convertFile(String inputPath, String outputPath, int targetTurn, String targetPhase)
            throws IOException {
        String json = readFile(inputPath);
        String puzzle = convert(json, targetTurn, targetPhase);
        writeFile(outputPath, puzzle);
    }

    private String convertFromJson(JsonObject replay, int targetTurn, String targetPhase,
                                   String puzzleName, int turnLimit) {
        StringBuilder sb = new StringBuilder();

        // Get card index for name lookups
        JsonObject cardIndex = replay.has("card_index") ?
            replay.getAsJsonObject("card_index") : new JsonObject();

        // Get initial state
        JsonObject initialState = replay.has("initial_state") ?
            replay.getAsJsonObject("initial_state") : new JsonObject();

        // Get L1 events to track state changes
        JsonArray l1Events = replay.has("log_l1") ?
            replay.getAsJsonArray("log_l1") : new JsonArray();

        // Compute game state at target turn
        GameStateSnapshot state = computeStateAtTurn(initialState, l1Events, cardIndex, targetTurn, targetPhase);

        // Get future draws for library
        List<String> futureDraws = getFutureDraws(l1Events, cardIndex, targetTurn);

        // Build puzzle metadata
        sb.append("[metadata]\n");
        sb.append("Name:").append(puzzleName).append("\n");
        sb.append("URL:Generated from Replay Notation\n");
        sb.append("Goal:Win\n");
        sb.append("Turns:").append(turnLimit).append("\n");
        sb.append("Difficulty:Medium\n");
        sb.append("Description:Replay scenario from turn ").append(targetTurn).append(".\n");
        sb.append("\n");

        // Build puzzle state
        sb.append("[state]\n");
        sb.append("turn=").append(targetTurn).append("\n");
        sb.append("ActivePlayer=").append(state.activePlayerIsHuman ? "Human" : "AI").append("\n");
        sb.append("ActivePhase=").append(normalizePhaseName(targetPhase)).append("\n");

        // Human (P1) state
        sb.append("HumanLife=").append(state.humanLife).append("\n");
        if (!state.humanBattlefield.isEmpty()) {
            sb.append("HumanPlay=").append(formatCardList(state.humanBattlefield)).append("\n");
        }
        if (!state.humanHand.isEmpty()) {
            sb.append("HumanHand=").append(formatCardListSimple(state.humanHand)).append("\n");
        }
        if (!state.humanGraveyard.isEmpty()) {
            sb.append("HumanGraveyard=").append(formatCardListSimple(state.humanGraveyard)).append("\n");
        }

        // Library: combine remaining library + future draws
        List<String> humanLibrary = new ArrayList<>(state.humanLibrary);
        if (!futureDraws.isEmpty()) {
            // Put future draws at the top of library
            List<String> orderedLibrary = new ArrayList<>(futureDraws);
            orderedLibrary.addAll(humanLibrary);
            humanLibrary = orderedLibrary;
        }
        if (!humanLibrary.isEmpty()) {
            sb.append("HumanLibrary=").append(formatCardListSimple(humanLibrary)).append("\n");
        }

        if (!state.humanExile.isEmpty()) {
            sb.append("HumanExile=").append(formatCardListSimple(state.humanExile)).append("\n");
        }

        // AI (P2) state
        sb.append("AILife=").append(state.aiLife).append("\n");
        if (!state.aiBattlefield.isEmpty()) {
            sb.append("AIPlay=").append(formatCardList(state.aiBattlefield)).append("\n");
        }
        if (!state.aiHand.isEmpty()) {
            sb.append("AIHand=").append(formatCardListSimple(state.aiHand)).append("\n");
        }
        if (!state.aiGraveyard.isEmpty()) {
            sb.append("AIGraveyard=").append(formatCardListSimple(state.aiGraveyard)).append("\n");
        }
        if (!state.aiLibrary.isEmpty()) {
            sb.append("AILibrary=").append(formatCardListSimple(state.aiLibrary)).append("\n");
        }
        if (!state.aiExile.isEmpty()) {
            sb.append("AIExile=").append(formatCardListSimple(state.aiExile)).append("\n");
        }

        // Add mana pool if present
        if (!state.humanManaPool.isEmpty()) {
            sb.append("p0manapool=").append(state.humanManaPool).append("\n");
        }
        if (!state.aiManaPool.isEmpty()) {
            sb.append("p1manapool=").append(state.aiManaPool).append("\n");
        }

        return sb.toString();
    }

    /**
     * Compute the game state at a specific turn by processing L1 events.
     */
    private GameStateSnapshot computeStateAtTurn(JsonObject initialState, JsonArray l1Events,
                                                  JsonObject cardIndex, int targetTurn, String targetPhase) {
        GameStateSnapshot state = new GameStateSnapshot();

        // Initialize from initial state
        initializeFromInitialState(state, initialState, cardIndex);

        // Process events up to target turn/phase
        for (int i = 0; i < l1Events.size(); i++) {
            JsonObject event = l1Events.get(i).getAsJsonObject();
            String timeMarker = event.has("t") ? event.get("t").getAsString() : "";

            // Parse time marker: T1.MP1:0
            int eventTurn = parseEventTurn(timeMarker);
            String eventPhase = parseEventPhase(timeMarker);

            // Stop if we've passed the target
            if (eventTurn > targetTurn) {
                break;
            }
            if (eventTurn == targetTurn && isPhaseAfter(eventPhase, targetPhase)) {
                break;
            }

            // Apply event to state
            applyEvent(state, event, cardIndex);
        }

        return state;
    }

    /**
     * Get cards drawn in future turns (for library ordering).
     */
    private List<String> getFutureDraws(JsonArray l1Events, JsonObject cardIndex, int startTurn) {
        List<String> draws = new ArrayList<>();

        for (int i = 0; i < l1Events.size(); i++) {
            JsonObject event = l1Events.get(i).getAsJsonObject();
            String timeMarker = event.has("t") ? event.get("t").getAsString() : "";
            String eventType = event.has("type") ? event.get("type").getAsString() : "";

            int eventTurn = parseEventTurn(timeMarker);

            // Only look at future turns
            if (eventTurn <= startTurn) continue;

            // Look for draw events
            if ("DRAW".equals(eventType) || "MOVE".equals(eventType)) {
                JsonObject data = event.has("data") ? event.getAsJsonObject("data") : null;
                if (data != null) {
                    String destination = data.has("to") ? data.get("to").getAsString() : "";
                    if (destination.contains("hand")) {
                        String cardId = data.has("card") ? data.get("card").getAsString() : "";
                        String cardName = getCardName(cardIndex, cardId);
                        if (!cardName.isEmpty()) {
                            draws.add(cardName);
                        }
                    }
                }
            }
        }

        return draws;
    }

    private void initializeFromInitialState(GameStateSnapshot state, JsonObject initialState,
                                            JsonObject cardIndex) {
        // Check for players object
        if (initialState.has("players")) {
            JsonObject players = initialState.getAsJsonObject("players");

            // P1 = Human
            if (players.has("P1")) {
                JsonObject p1 = players.getAsJsonObject("P1");
                state.humanLife = p1.has("life") ? p1.get("life").getAsInt() : 20;
            }

            // P2 = AI
            if (players.has("P2")) {
                JsonObject p2 = players.getAsJsonObject("P2");
                state.aiLife = p2.has("life") ? p2.get("life").getAsInt() : 20;
            }
        }

        // Check for objects (cards in zones)
        if (initialState.has("objects")) {
            JsonObject objects = initialState.getAsJsonObject("objects");
            for (String objId : objects.keySet()) {
                JsonObject obj = objects.getAsJsonObject(objId);
                String zone = obj.has("zone") ? obj.get("zone").getAsString() : "";
                String controller = obj.has("controller") ? obj.get("controller").getAsString() : "P1";
                String cardRef = obj.has("cardRef") ? obj.get("cardRef").getAsString() : objId;
                String cardName = getCardName(cardIndex, cardRef);

                if (cardName.isEmpty()) continue;

                CardState card = new CardState(cardName);
                card.tapped = obj.has("tapped") && obj.get("tapped").getAsBoolean();

                if (obj.has("counters")) {
                    JsonObject counters = obj.getAsJsonObject("counters");
                    for (String counterType : counters.keySet()) {
                        card.counters.put(counterType, counters.get(counterType).getAsInt());
                    }
                }

                // Add to appropriate zone
                boolean isHuman = "P1".equals(controller);
                addCardToZone(state, card, zone, isHuman);
            }
        }

        // Check for zone arrays (alternative format)
        if (initialState.has("zones")) {
            JsonObject zones = initialState.getAsJsonObject("zones");
            processZoneData(state, zones, cardIndex);
        }
    }

    private void processZoneData(GameStateSnapshot state, JsonObject zones, JsonObject cardIndex) {
        for (String zoneName : zones.keySet()) {
            JsonElement zoneData = zones.get(zoneName);
            if (!zoneData.isJsonArray()) continue;

            JsonArray cards = zoneData.getAsJsonArray();
            boolean isHuman = zoneName.startsWith("P1");
            String zoneType = zoneName.contains(":") ? zoneName.split(":")[1] : zoneName;

            for (int i = 0; i < cards.size(); i++) {
                String cardId = cards.get(i).getAsString();
                String cardName = getCardName(cardIndex, cardId);
                if (!cardName.isEmpty()) {
                    addCardToZone(state, new CardState(cardName), zoneType, isHuman);
                }
            }
        }
    }

    private void addCardToZone(GameStateSnapshot state, CardState card, String zone, boolean isHuman) {
        zone = zone.toLowerCase();

        if (zone.contains("battlefield") || zone.contains("play")) {
            if (isHuman) state.humanBattlefield.add(card);
            else state.aiBattlefield.add(card);
        } else if (zone.contains("hand")) {
            if (isHuman) state.humanHand.add(card.name);
            else state.aiHand.add(card.name);
        } else if (zone.contains("graveyard") || zone.contains("grave")) {
            if (isHuman) state.humanGraveyard.add(card.name);
            else state.aiGraveyard.add(card.name);
        } else if (zone.contains("library") || zone.contains("deck")) {
            if (isHuman) state.humanLibrary.add(card.name);
            else state.aiLibrary.add(card.name);
        } else if (zone.contains("exile")) {
            if (isHuman) state.humanExile.add(card.name);
            else state.aiExile.add(card.name);
        }
    }

    private void applyEvent(GameStateSnapshot state, JsonObject event, JsonObject cardIndex) {
        String eventType = event.has("type") ? event.get("type").getAsString() : "";
        String actor = event.has("a") ? event.get("a").getAsString() : "";
        JsonObject data = event.has("data") ? event.getAsJsonObject("data") : new JsonObject();

        boolean isHuman = "P1".equals(actor);

        switch (eventType) {
            case "LIFE_CHANGE":
                int delta = data.has("delta") ? data.get("delta").getAsInt() : 0;
                int newLife = data.has("new") ? data.get("new").getAsInt() : -1;
                if (newLife >= 0) {
                    if (isHuman) state.humanLife = newLife;
                    else state.aiLife = newLife;
                } else {
                    if (isHuman) state.humanLife += delta;
                    else state.aiLife += delta;
                }
                break;

            case "MOVE":
            case "ZONE_CHANGE":
                String cardId = data.has("card") ? data.get("card").getAsString() : "";
                String from = data.has("from") ? data.get("from").getAsString() : "";
                String to = data.has("to") ? data.get("to").getAsString() : "";
                String cardName = getCardName(cardIndex, cardId);

                if (!cardName.isEmpty()) {
                    // Remove from old zone
                    removeCardFromZone(state, cardName, from, isHuman);
                    // Add to new zone
                    addCardToZone(state, new CardState(cardName), to, isHuman);
                }
                break;

            case "TAP":
            case "UNTAP":
                // Would need to track specific card states
                break;

            case "DAMAGE":
                String target = data.has("target") ? data.get("target").getAsString() : "";
                int damage = data.has("amount") ? data.get("amount").getAsInt() : 0;
                if ("P1".equals(target)) state.humanLife -= damage;
                else if ("P2".equals(target)) state.aiLife -= damage;
                break;
        }
    }

    private void removeCardFromZone(GameStateSnapshot state, String cardName, String zone, boolean isHuman) {
        zone = zone.toLowerCase();

        if (zone.contains("battlefield") || zone.contains("play")) {
            List<CardState> list = isHuman ? state.humanBattlefield : state.aiBattlefield;
            list.removeIf(c -> c.name.equals(cardName));
        } else if (zone.contains("hand")) {
            List<String> list = isHuman ? state.humanHand : state.aiHand;
            list.remove(cardName);
        } else if (zone.contains("graveyard")) {
            List<String> list = isHuman ? state.humanGraveyard : state.aiGraveyard;
            list.remove(cardName);
        } else if (zone.contains("library")) {
            List<String> list = isHuman ? state.humanLibrary : state.aiLibrary;
            list.remove(cardName);
        } else if (zone.contains("exile")) {
            List<String> list = isHuman ? state.humanExile : state.aiExile;
            list.remove(cardName);
        }
    }

    private String getCardName(JsonObject cardIndex, String cardId) {
        if (cardIndex.has(cardId)) {
            JsonObject card = cardIndex.getAsJsonObject(cardId);
            return card.has("name") ? card.get("name").getAsString() : "";
        }
        // Maybe cardId is already a name
        return cardId.startsWith("c") || cardId.startsWith("t") ? "" : cardId;
    }

    private int parseEventTurn(String timeMarker) {
        // Format: T1.MP1:0
        if (timeMarker.startsWith("T")) {
            int dotIndex = timeMarker.indexOf('.');
            if (dotIndex > 1) {
                try {
                    return Integer.parseInt(timeMarker.substring(1, dotIndex));
                } catch (NumberFormatException e) {
                    return 1;
                }
            }
        }
        return 1;
    }

    private String parseEventPhase(String timeMarker) {
        // Format: T1.MP1:0
        int dotIndex = timeMarker.indexOf('.');
        if (dotIndex >= 0) {
            int colonIndex = timeMarker.indexOf(':', dotIndex);
            if (colonIndex > dotIndex) {
                return timeMarker.substring(dotIndex + 1, colonIndex);
            }
            return timeMarker.substring(dotIndex + 1);
        }
        return "MP1";
    }

    private boolean isPhaseAfter(String phase1, String phase2) {
        List<String> phaseOrder = Arrays.asList(
            "UP", "UPKEEP", "DRAW", "MP1", "MAIN1",
            "COMBAT", "COMBAT_DECLARE_ATTACKERS", "COMBAT_DECLARE_BLOCKERS", "COMBAT_DAMAGE",
            "MP2", "MAIN2", "END", "CLEANUP"
        );
        int idx1 = phaseOrder.indexOf(phase1.toUpperCase());
        int idx2 = phaseOrder.indexOf(phase2.toUpperCase());
        return idx1 > idx2;
    }

    private String normalizePhaseName(String phase) {
        String normalized = PHASE_MAPPING.get(phase.toUpperCase());
        return normalized != null ? normalized : phase;
    }

    private String formatCardList(List<CardState> cards) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append("; ");
            CardState card = cards.get(i);
            sb.append(card.name);

            // Add modifiers
            List<String> modifiers = new ArrayList<>();
            if (card.tapped) modifiers.add("Tapped");
            for (Map.Entry<String, Integer> counter : card.counters.entrySet()) {
                modifiers.add("Counters:" + counter.getKey() + "=" + counter.getValue());
            }

            if (!modifiers.isEmpty()) {
                sb.append("|").append(String.join(",", modifiers));
            }
        }
        return sb.toString();
    }

    private String formatCardListSimple(List<String> cards) {
        return String.join("; ", cards);
    }

    private String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private void writeFile(String path, String content) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            writer.print(content);
        }
    }

    // ========== Inner Classes ==========

    private static class GameStateSnapshot {
        int humanLife = 20;
        int aiLife = 20;
        boolean activePlayerIsHuman = true;

        List<CardState> humanBattlefield = new ArrayList<>();
        List<String> humanHand = new ArrayList<>();
        List<String> humanGraveyard = new ArrayList<>();
        List<String> humanLibrary = new ArrayList<>();
        List<String> humanExile = new ArrayList<>();
        String humanManaPool = "";

        List<CardState> aiBattlefield = new ArrayList<>();
        List<String> aiHand = new ArrayList<>();
        List<String> aiGraveyard = new ArrayList<>();
        List<String> aiLibrary = new ArrayList<>();
        List<String> aiExile = new ArrayList<>();
        String aiManaPool = "";
    }

    private static class CardState {
        String name;
        boolean tapped = false;
        Map<String, Integer> counters = new HashMap<>();
        String attachedTo = null;

        CardState(String name) {
            this.name = name;
        }
    }

    // ========== Main for testing ==========

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: ReplayToPuzzleConverter <input.json> <output.pzl> [turn] [phase]");
            System.out.println("  turn: Target turn number (default: 1)");
            System.out.println("  phase: Target phase (default: Main1)");
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];
        int targetTurn = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        String targetPhase = args.length > 3 ? args[3] : "Main1";

        try {
            ReplayToPuzzleConverter converter = new ReplayToPuzzleConverter();
            converter.convertFile(inputPath, outputPath, targetTurn, targetPhase);
            System.out.println("Puzzle created: " + outputPath);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

