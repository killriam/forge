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
        json.append("\n");

        json.append("}");
        return json.toString();
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
        json.append("    \"players\": {},\n");
        json.append("    \"zones\": {},\n");
        json.append("    \"objects\": {}\n");
        json.append("  }");
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

