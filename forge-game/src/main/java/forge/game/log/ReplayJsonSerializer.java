package forge.game.log;

import forge.game.log.model.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Simple JSON serializer for replay logs.
 * This is a lightweight alternative to GSON for basic JSON export.
 */
public class ReplayJsonSerializer {

    /**
     * Serialize a ReplayLog to JSON string.
     */
    public static String toJson(ReplayLog log) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        // Format
        json.append("  \"format\": \"").append(escape(log.getFormat())).append("\",\n");
        json.append("  \"version\": \"").append(escape(log.getVersion())).append("\",\n");

        // Meta
        json.append("  \"meta\": ");
        appendMeta(json, log.getMeta());
        json.append(",\n");

        // Seed
        json.append("  \"seed\": ").append(log.getSeed()).append(",\n");

        // Game Start Info
        json.append("  \"game_start\": ");
        appendGameStartInfo(json, log.getGameStart());
        json.append(",\n");

        // Card Index
        json.append("  \"card_index\": ");
        appendCardIndex(json, log.getCardIndex());
        json.append(",\n");

        // Initial State
        json.append("  \"initial_state\": ");
        appendGameState(json, log.getInitialState());
        json.append(",\n");

        // L1 Events
        json.append("  \"log_l1\": ");
        appendL1Events(json, log.getLogL1());
        json.append(",\n");

        // L2 Units
        json.append("  \"views_l2\": ");
        appendL2Units(json, log.getViewsL2());
        json.append(",\n");

        // Learning Markers (spec v1.3.0)
        json.append("  \"learning_markers\": ");
        appendLearningMarkers(json, log.getLearningMarkers());
        json.append("\n");

        json.append("}");
        return json.toString();
    }

    /**
     * Append game start info section.
     */
    private static void appendGameStartInfo(StringBuilder json, GameStartInfo gameStart) {
        json.append("{\n");

        json.append("    \"toss_winner\": ");
        if (gameStart.getTossWinner() != null) {
            json.append("\"").append(escape(gameStart.getTossWinner())).append("\"");
        } else {
            json.append("null");
        }
        json.append(",\n");

        json.append("    \"play_draw_choice\": ");
        if (gameStart.getPlayDrawChoice() != null) {
            json.append("\"").append(escape(gameStart.getPlayDrawChoice())).append("\"");
        } else {
            json.append("null");
        }
        json.append(",\n");

        json.append("    \"starting_player\": ");
        if (gameStart.getStartingPlayer() != null) {
            json.append("\"").append(escape(gameStart.getStartingPlayer())).append("\"");
        } else {
            json.append("null");
        }
        json.append(",\n");

        json.append("    \"mulligans\": [\n");
        List<GameStartInfo.MulliganInfo> mulligans = gameStart.getMulligans();
        for (int i = 0; i < mulligans.size(); i++) {
            GameStartInfo.MulliganInfo m = mulligans.get(i);
            json.append("      {");
            json.append("\"player\": \"").append(escape(m.getPlayer())).append("\", ");
            json.append("\"starting_hand_size\": ").append(m.getStartingHandSize()).append(", ");
            json.append("\"mulligans_taken\": ").append(m.getMulligansTaken()).append(", ");
            json.append("\"final_hand_size\": ").append(m.getFinalHandSize()).append(", ");
            json.append("\"cards_to_bottom\": ").append(m.getCardsToBottom());
            json.append("}");
            if (i < mulligans.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("    ]\n");

        json.append("  }");
    }

    /**
     * Write ReplayLog to file.
     */
    public static void writeToFile(ReplayLog log, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(toJson(log));
        }
    }

    private static void appendMeta(StringBuilder json, ReplayMeta meta) {
        json.append("{\n");
        json.append("    \"game_id\": \"").append(escape(meta.getGameId())).append("\",\n");
        json.append("    \"timestamp\": \"").append(escape(meta.getTimestamp())).append("\",\n");
        json.append("    \"game_type\": \"").append(escape(meta.getGameType())).append("\",\n");
        json.append("    \"players\": {");

        boolean first = true;
        for (Map.Entry<String, ReplayMeta.PlayerMeta> entry : meta.getPlayers().entrySet()) {
            if (!first) json.append(",");
            json.append("\n      \"").append(escape(entry.getKey())).append("\": {");
            json.append("\"name\": \"").append(escape(entry.getValue().getName())).append("\"");
            if (entry.getValue().getDeckName() != null) {
                json.append(", \"deck_name\": \"").append(escape(entry.getValue().getDeckName())).append("\"");
            }
            if (entry.getValue().getDeckHash() != null) {
                json.append(", \"deck_hash\": \"").append(escape(entry.getValue().getDeckHash())).append("\"");
            }
            json.append("}");
            first = false;
        }
        json.append("\n    },\n");

        json.append("    \"winner\": ");
        if (meta.getWinner() != null) {
            json.append("\"").append(escape(meta.getWinner())).append("\"");
        } else {
            json.append("null");
        }
        json.append(",\n");

        json.append("    \"win_condition\": ");
        if (meta.getWinCondition() != null) {
            json.append("\"").append(escape(meta.getWinCondition())).append("\"");
        } else {
            json.append("null");
        }
        json.append(",\n");

        json.append("    \"conceded\": ").append(meta.isConceded()).append(",\n");

        json.append("    \"turns\": ").append(meta.getTurns() != null ? meta.getTurns() : "null").append(",\n");
        json.append("    \"duration_seconds\": ").append(meta.getDurationSeconds() != null ? meta.getDurationSeconds() : "null").append("\n");
        json.append("  }");
    }

    private static void appendCardIndex(StringBuilder json, Map<String, CardDefinition> cardIndex) {
        json.append("{\n");
        boolean first = true;
        for (Map.Entry<String, CardDefinition> entry : cardIndex.entrySet()) {
            if (!first) json.append(",\n");
            json.append("    \"").append(escape(entry.getKey())).append("\": {");
            CardDefinition card = entry.getValue();
            json.append("\"name\": \"").append(escape(card.getName())).append("\"");
            if (card.getCost() != null && !card.getCost().isEmpty()) {
                json.append(", \"cost\": \"").append(escape(card.getCost())).append("\"");
            }
            if (card.getType() != null && !card.getType().isEmpty()) {
                json.append(", \"type\": \"").append(escape(card.getType())).append("\"");
            }
            json.append("}");
            first = false;
        }
        json.append("\n  }");
    }

    private static void appendGameState(StringBuilder json, GameState state) {
        json.append("{\n");
        json.append("    \"turn\": ").append(state.getTurn()).append(",\n");
        json.append("    \"phase\": ").append(quote(state.getPhase())).append(",\n");
        json.append("    \"step\": ").append(quote(state.getStep())).append(",\n");
        json.append("    \"priority\": ").append(quote(state.getPriority())).append(",\n");
        json.append("    \"active_player\": ").append(quote(state.getActivePlayer())).append(",\n");

        // Players
        json.append("    \"players\": ");
        appendPlayerStates(json, state.getPlayers());
        json.append(",\n");

        // Zones
        json.append("    \"zones\": ");
        appendZones(json, state.getZones());
        json.append(",\n");

        // Objects - the critical mapping of object IDs to card names
        json.append("    \"objects\": ");
        appendObjects(json, state.getObjects());
        json.append("\n");

        json.append("  }");
    }

    private static void appendPlayerStates(StringBuilder json, Map<String, GameState.PlayerState> players) {
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, GameState.PlayerState> entry : players.entrySet()) {
            if (!first) json.append(", ");
            json.append("\n      \"").append(escape(entry.getKey())).append("\": {");
            GameState.PlayerState ps = entry.getValue();
            json.append("\"life\": ").append(ps.getLife());
            json.append(", \"max_hand_size\": ").append(ps.getMaxHandSize());
            json.append(", \"lands_played\": ").append(ps.getLandsPlayedThisTurn());
            if (ps.getManaPool() != null) {
                json.append(", \"mana_pool\": ");
                appendValue(json, ps.getManaPool());
            }
            if (ps.getCounters() != null && !ps.getCounters().isEmpty()) {
                json.append(", \"counters\": ");
                appendIntegerMap(json, ps.getCounters());
            }
            json.append("}");
            first = false;
        }
        json.append("\n    }");
    }

    private static void appendZones(StringBuilder json, Map<String, Object> zones) {
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : zones.entrySet()) {
            if (!first) json.append(", ");
            json.append("\n      \"").append(escape(entry.getKey())).append("\": ");
            appendValue(json, entry.getValue());
            first = false;
        }
        json.append("\n    }");
    }

    private static void appendObjects(StringBuilder json, Map<String, GameState.ObjectState> objects) {
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, GameState.ObjectState> entry : objects.entrySet()) {
            if (!first) json.append(",");
            json.append("\n      \"").append(escape(entry.getKey())).append("\": ");
            appendObjectState(json, entry.getValue());
            first = false;
        }
        json.append("\n    }");
    }

    private static void appendObjectState(StringBuilder json, GameState.ObjectState obj) {
        json.append("{");
        json.append("\"card_ref\": ").append(quote(obj.getCardRef()));
        json.append(", \"owner\": ").append(quote(obj.getOwner()));
        json.append(", \"controller\": ").append(quote(obj.getController()));
        json.append(", \"zone\": ").append(quote(obj.getZone()));

        if (obj.isTapped()) json.append(", \"tapped\": true");
        if (obj.isFaceDown()) json.append(", \"face_down\": true");
        if (obj.isFlipped()) json.append(", \"flipped\": true");
        if (obj.getDamageMarked() > 0) json.append(", \"damage_marked\": ").append(obj.getDamageMarked());

        if (obj.getCounters() != null && !obj.getCounters().isEmpty()) {
            json.append(", \"counters\": ");
            appendIntegerMap(json, obj.getCounters());
        }

        if (obj.getAttachedTo() != null) {
            json.append(", \"attached_to\": ").append(quote(obj.getAttachedTo()));
        }

        if (obj.getNotes() != null && !obj.getNotes().isEmpty()) {
            json.append(", \"notes\": ");
            appendMap(json, obj.getNotes());
        }

        json.append("}");
    }

    private static void appendL1Events(StringBuilder json, List<L1Event> events) {
        json.append("[\n");
        for (int i = 0; i < events.size(); i++) {
            L1Event event = events.get(i);
            json.append("    {");
            json.append("\"i\": ").append(event.getI()).append(", ");
            json.append("\"t\": ").append(quote(event.getT())).append(", ");
            json.append("\"a\": ").append(quote(event.getA())).append(", ");
            json.append("\"type\": ").append(quote(event.getType())).append(", ");
            json.append("\"data\": ");
            appendMap(json, event.getData());
            json.append("}");
            if (i < events.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]");
    }

    private static void appendL2Units(StringBuilder json, List<L2Unit> units) {
        json.append("[]");  // Simplified for now
    }

    /**
     * Serialize the top-level learning_markers array (spec v1.3.0).
     */
    private static void appendLearningMarkers(StringBuilder json, List<ReplayLog.LearningMarker> markers) {
        if (markers == null || markers.isEmpty()) {
            json.append("[]");
            return;
        }
        json.append("[\n");
        for (int i = 0; i < markers.size(); i++) {
            ReplayLog.LearningMarker m = markers.get(i);
            json.append("    {\n");
            json.append("      \"marker_id\": ").append(quote(m.getMarkerId())).append(",\n");
            json.append("      \"event_index\": ").append(m.getEventIndex()).append(",\n");
            json.append("      \"t\": ").append(quote(m.getT())).append(",\n");
            json.append("      \"player\": ").append(quote(m.getPlayer())).append(",\n");
            json.append("      \"label\": ").append(quote(m.getLabel())).append(",\n");
            json.append("      \"category\": ").append(quote(m.getCategory())).append(",\n");
            json.append("      \"created_at\": ").append(quote(m.getCreatedAt())).append(",\n");
            json.append("      \"notes\": ").append(quote(m.getNotes() != null ? m.getNotes() : "")).append(",\n");
            // snapshot
            ReplayLog.LearningMarker.Snapshot s = m.getSnapshot();
            json.append("      \"snapshot\": {\n");
            json.append("        \"turn\": ").append(s.getTurn()).append(",\n");
            json.append("        \"phase\": ").append(quote(s.getPhase())).append(",\n");
            json.append("        \"active_player\": ").append(quote(s.getActivePlayer())).append(",\n");
            json.append("        \"life_totals\": ");
            appendIntegerMap(json, s.getLifeTotals());
            json.append(",\n");
            json.append("        \"cards_in_hand\": ");
            appendIntegerMap(json, s.getCardsInHand());
            json.append(",\n");
            json.append("        \"battlefield_count\": ");
            appendIntegerMap(json, s.getBattlefieldCount());
            json.append(",\n");
            json.append("        \"stack_empty\": ").append(s.isStackEmpty()).append("\n");
            json.append("      }\n");
            json.append("    }");
            if (i < markers.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]");
    }

    private static void appendMap(StringBuilder json, Map<String, Object> map) {
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(", ");
            json.append("\"").append(escape(entry.getKey())).append("\": ");
            appendValue(json, entry.getValue());
            first = false;
        }
        json.append("}");
    }

    private static void appendIntegerMap(StringBuilder json, Map<String, Integer> map) {
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (!first) json.append(", ");
            json.append("\"").append(escape(entry.getKey())).append("\": ").append(entry.getValue());
            first = false;
        }
        json.append("}");
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof String) {
            json.append("\"").append(escape((String) value)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value.toString());
        } else if (value instanceof List) {
            json.append("[");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                appendValue(json, list.get(i));
                if (i < list.size() - 1) json.append(", ");
            }
            json.append("]");
        } else if (value instanceof Map) {
            appendMap(json, (Map<String, Object>) value);
        } else {
            json.append("\"").append(escape(value.toString())).append("\"");
        }
    }

    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}

