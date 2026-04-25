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
        json.append("  \"spec_version\": \"").append(escape(log.getSpecVersion())).append("\",\n");
        // mode (v1.7.0) — always written; defaults to "full_game"
        json.append("  \"mode\": \"").append(escape(log.getMode())).append("\",\n");

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

        // L1 Events (spec key: "events")
        json.append("  \"events\": ");
        appendL1Events(json, log.getLogL1());
        json.append(",\n");

        // L2 Units
        json.append("  \"views_l2\": ");
        appendL2Units(json, log.getViewsL2());
        json.append(",\n");

        // Learning Markers (spec v1.3.0)
        json.append("  \"learning_markers\": ");
        appendLearningMarkers(json, log.getLearningMarkers());
        json.append(",\n");

        // Per-Turn Summary (spec v1.5.0)
        json.append("  \"per_turn_summary\": ");
        appendPerTurnSummary(json, log.getPerTurnSummary());
        json.append(",\n");

        // Game Summary (spec v1.5.0)
        json.append("  \"game_summary\": ");
        appendGameSummary(json, log.getGameSummary());
        json.append(",\n");

        // Scenario (spec v1.7.0) — null when mode == "full_game"
        json.append("  \"scenario\": ");
        if (log.getScenario() != null) {
            appendScenario(json, log.getScenario());
        } else {
            json.append("null");
        }
        json.append("\n");

        json.append("}");
        return json.toString();
    }

    /**
     * Serialize a Scenario object (spec v1.7.0).
     */
    private static void appendScenario(StringBuilder json, forge.game.log.model.Scenario s) {
        json.append("{\n");
        json.append("    \"type\": ").append(quote(s.getType() != null ? s.getType() : "")).append(",\n");
        json.append("    \"title\": ").append(quote(s.getTitle() != null ? s.getTitle() : "")).append(",\n");
        json.append("    \"description\": ").append(quote(s.getDescription() != null ? s.getDescription() : "")).append(",\n");
        json.append("    \"question\": ").append(quote(s.getQuestion() != null ? s.getQuestion() : "")).append(",\n");
        json.append("    \"answer\": ").append(quote(s.getAnswer() != null ? s.getAnswer() : "")).append(",\n");

        // ruling_references
        json.append("    \"ruling_references\": [");
        List<String> refs = s.getRulingReferences();
        if (refs != null && !refs.isEmpty()) {
            for (int i = 0; i < refs.size(); i++) {
                json.append(quote(refs.get(i)));
                if (i < refs.size() - 1) json.append(", ");
            }
        }
        json.append("],\n");

        // tags
        json.append("    \"tags\": [");
        List<String> tags = s.getTags();
        if (tags != null && !tags.isEmpty()) {
            for (int i = 0; i < tags.size(); i++) {
                json.append(quote(tags.get(i)));
                if (i < tags.size() - 1) json.append(", ");
            }
        }
        json.append("]\n");

        json.append("  }");
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
            ReplayMeta.PlayerMeta pm = entry.getValue();
            json.append("\n      \"").append(escape(entry.getKey())).append("\": {");
            json.append("\"name\": \"").append(escape(pm.getName())).append("\"");

            // Always output deck_name (placeholder if missing)
            json.append(", \"deck_name\": ");
            if (pm.getDeckName() != null) {
                json.append("\"").append(escape(pm.getDeckName())).append("\"");
            } else {
                json.append("\"unknown\"");
            }

            // Always output deck_hash (null if missing)
            json.append(", \"deck_hash\": ");
            if (pm.getDeckHash() != null) {
                json.append("\"").append(escape(pm.getDeckHash())).append("\"");
            } else {
                json.append("null");
            }

            // deck_link: always output per spec v1.4.0 (null for AI players)
            json.append(", \"deck_link\": ");
            if (pm.getDeckLink() != null) {
                json.append("\"").append(escape(pm.getDeckLink())).append("\"");
            } else {
                json.append("null");
            }

            // Player type info
            json.append(", \"is_ai\": ").append(pm.isAi());
            json.append(", \"player_type\": \"").append(escape(pm.getPlayerType() != null ? pm.getPlayerType() : "unknown")).append("\"");
            json.append(", \"starting_life\": ").append(pm.getStartingLife());

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
            if (card.getOracleId() != null && !card.getOracleId().isEmpty()) {
                json.append(", \"oracle_id\": \"").append(escape(card.getOracleId())).append("\"");
            }
            if (card.getOracleText() != null && !card.getOracleText().isEmpty()) {
                json.append(", \"oracle_text\": \"").append(escape(card.getOracleText())).append("\"");
            }
            if (card.getPower() != null) {
                json.append(", \"power\": \"").append(escape(card.getPower())).append("\"");
            }
            if (card.getToughness() != null) {
                json.append(", \"toughness\": \"").append(escape(card.getToughness())).append("\"");
            }
            if (card.getSubtypes() != null && !card.getSubtypes().isEmpty()) {
                json.append(", \"subtypes\": [");
                for (int i = 0; i < card.getSubtypes().size(); i++) {
                    if (i > 0) json.append(", ");
                    json.append("\"").append(escape(card.getSubtypes().get(i))).append("\"");
                }
                json.append("]");
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
            json.append(", \"lands_played_this_turn\": ").append(ps.getLandsPlayedThisTurn());
            // Spec: always output mana_pool (empty [] when none)
            json.append(", \"mana_pool\": ");
            if (ps.getManaPool() != null) {
                appendValue(json, ps.getManaPool());
            } else {
                json.append("[]");
            }
            // Spec: always output counters (empty {} when none)
            json.append(", \"counters\": ");
            if (ps.getCounters() != null && !ps.getCounters().isEmpty()) {
                appendIntegerMap(json, ps.getCounters());
            } else {
                json.append("{}");
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
        // Spec section 8.2: always output state flags
        json.append(", \"tapped\": ").append(obj.isTapped());
        json.append(", \"flipped\": ").append(obj.isFlipped());
        json.append(", \"face_down\": ").append(obj.isFaceDown());
        json.append(", \"damage_marked\": ").append(obj.getDamageMarked());

        // Spec: always output counters (empty {} when none)
        json.append(", \"counters\": ");
        if (obj.getCounters() != null && !obj.getCounters().isEmpty()) {
            appendIntegerMap(json, obj.getCounters());
        } else {
            json.append("{}");
        }

        // Spec: always output attached_to (null when not attached)
        json.append(", \"attached_to\": ");
        if (obj.getAttachedTo() != null) {
            json.append(quote(obj.getAttachedTo()));
        } else {
            json.append("null");
        }

        // Spec: always output notes (empty {} when none)
        json.append(", \"notes\": ");
        if (obj.getNotes() != null && !obj.getNotes().isEmpty()) {
            appendMap(json, obj.getNotes());
        } else {
            json.append("{}");
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
        if (units == null || units.isEmpty()) {
            json.append("[]");
            return;
        }
        json.append("[\n");
        for (int i = 0; i < units.size(); i++) {
            L2Unit unit = units.get(i);
            json.append("    {\n");
            json.append("      \"u\": ").append(unit.getU()).append(",\n");
            json.append("      \"t_start\": ").append(quote(unit.getTStart())).append(",\n");
            json.append("      \"t_end\": ").append(quote(unit.getTEnd())).append(",\n");

            // l1_range
            json.append("      \"l1_range\": ");
            if (unit.getL1Range() != null && unit.getL1Range().length == 2) {
                json.append("[").append(unit.getL1Range()[0]).append(", ").append(unit.getL1Range()[1]).append("]");
            } else {
                json.append("null");
            }
            json.append(",\n");

            // decision_events
            json.append("      \"decision_events\": [");
            List<Integer> de = unit.getDecisionEvents();
            for (int j = 0; j < de.size(); j++) {
                json.append(de.get(j));
                if (j < de.size() - 1) json.append(", ");
            }
            json.append("],\n");

            // before state
            json.append("      \"before\": ");
            if (unit.getBefore() != null) {
                appendGameState(json, unit.getBefore());
            } else {
                json.append("null");
            }
            json.append(",\n");

            // stack
            json.append("      \"stack\": ");
            appendStackItems(json, unit.getStack());
            json.append(",\n");

            // after state
            json.append("      \"after\": ");
            if (unit.getAfter() != null) {
                appendGameState(json, unit.getAfter());
            } else {
                json.append("null");
            }
            json.append(",\n");

            // annotations
            json.append("      \"annotations\": ");
            appendAnnotations(json, unit.getAnnotations());
            json.append("\n");

            json.append("    }");
            if (i < units.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]");
    }

    private static void appendStackItems(StringBuilder json, List<L2Unit.StackItem> items) {
        if (items == null || items.isEmpty()) {
            json.append("[]");
            return;
        }
        json.append("[\n");
        for (int i = 0; i < items.size(); i++) {
            L2Unit.StackItem si = items.get(i);
            json.append("        {");
            json.append("\"stack\": ").append(quote(si.getStack()));
            json.append(", \"kind\": ").append(quote(si.getKind()));
            json.append(", \"controller\": ").append(quote(si.getController()));
            json.append(", \"source\": ").append(quote(si.getSource()));
            json.append(", \"card\": ").append(quote(si.getCard()));
            json.append(", \"card_name\": ").append(quote(si.getCardName()));

            // targets
            json.append(", \"targets\": [");
            List<L2Unit.StackItem.Target> targets = si.getTargets();
            for (int j = 0; j < targets.size(); j++) {
                L2Unit.StackItem.Target t = targets.get(j);
                json.append("{\"slot\": ").append(quote(t.getSlot()));
                json.append(", \"obj\": ").append(quote(t.getObj()));
                json.append(", \"name\": ").append(quote(t.getName()));
                json.append(", \"valid\": ").append(t.isValid()).append("}");
                if (j < targets.size() - 1) json.append(", ");
            }
            json.append("]");

            json.append(", \"choices\": ");
            appendMap(json, si.getChoices());

            json.append(", \"linked_decision_event\": ").append(si.getLinkedDecisionEvent());

            json.append(", \"mana_paid\": [");
            List<String> mp = si.getManaPaid();
            for (int j = 0; j < mp.size(); j++) {
                json.append(quote(mp.get(j)));
                if (j < mp.size() - 1) json.append(", ");
            }
            json.append("]");

            json.append(", \"outcome\": ").append(quote(si.getOutcome()));
            json.append("}");
            if (i < items.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("      ]");
    }

    private static void appendAnnotations(StringBuilder json, L2Unit.Annotations ann) {
        if (ann == null) {
            json.append("{\"decision_quality\": null, \"alternative_lines\": [], \"key_moment\": false, \"teaching_notes\": \"\"}");
            return;
        }
        json.append("{");
        json.append("\"decision_quality\": ");
        if (ann.getDecisionQuality() != null) {
            appendValue(json, ann.getDecisionQuality());
        } else {
            json.append("null");
        }
        json.append(", \"alternative_lines\": [");
        List<String> lines = ann.getAlternativeLines();
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                json.append(quote(lines.get(i)));
                if (i < lines.size() - 1) json.append(", ");
            }
        }
        json.append("]");
        json.append(", \"key_moment\": ").append(ann.isKeyMoment());
        json.append(", \"teaching_notes\": ").append(quote(ann.getTeachingNotes() != null ? ann.getTeachingNotes() : ""));
        json.append("}");
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

    // =========================================================================
    //  Per-Turn Summary serialization (spec v1.5.0)
    // =========================================================================

    private static void appendPerTurnSummary(StringBuilder json, List<TurnSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            json.append("[]");
            return;
        }
        json.append("[\n");
        for (int i = 0; i < summaries.size(); i++) {
            TurnSummary ts = summaries.get(i);
            json.append("    {\n");
            json.append("      \"turn\": ").append(ts.getTurn()).append(",\n");
            json.append("      \"active_player\": \"").append(escape(ts.getActivePlayer())).append("\",\n");
            json.append("      \"players\": {\n");

            boolean firstPlayer = true;
            for (Map.Entry<String, TurnSummary.PlayerTurnStats> entry : ts.getPlayers().entrySet()) {
                if (!firstPlayer) json.append(",\n");
                TurnSummary.PlayerTurnStats s = entry.getValue();
                json.append("        \"").append(escape(entry.getKey())).append("\": {");
                json.append("\"lands_played\": ").append(s.getLandsPlayed());
                json.append(", \"land_drop_rating\": \"").append(escape(s.getLandDropRating())).append("\"");
                json.append(", \"cards_drawn\": ").append(s.getCardsDrawn());
                json.append(", \"spells_cast\": ").append(s.getSpellsCast());
                json.append(", \"abilities_activated\": ").append(s.getAbilitiesActivated());
                json.append(", \"land_count\": ").append(s.getLandCount());
                json.append(", \"available_mana\": ").append(s.getAvailableMana());
                json.append(", \"life\": ").append(s.getLife());
                json.append(", \"cards_in_hand\": ").append(s.getCardsInHand());
                json.append(", \"creatures_on_battlefield\": ").append(s.getCreaturesOnBattlefield());
                json.append(", \"permanents_on_battlefield\": ").append(s.getPermanentsOnBattlefield());
                json.append(", \"damage_dealt\": ").append(s.getDamageDealt());
                json.append(", \"damage_taken\": ").append(s.getDamageTaken());
                json.append("}");
                firstPlayer = false;
            }
            json.append("\n      }\n");
            json.append("    }");
            if (i < summaries.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]");
    }

    // =========================================================================
    //  Game Summary serialization (spec v1.5.0)
    // =========================================================================

    private static void appendGameSummary(StringBuilder json, GameSummary gs) {
        if (gs == null) {
            json.append("null");
            return;
        }
        json.append("{\n");
        json.append("    \"total_turns\": ").append(gs.getTotalTurns()).append(",\n");
        json.append("    \"duration_seconds\": ").append(gs.getDurationSeconds()).append(",\n");
        json.append("    \"winner\": ").append(gs.getWinner() != null ? "\"" + escape(gs.getWinner()) + "\"" : "null").append(",\n");
        json.append("    \"win_condition\": ").append(gs.getWinCondition() != null ? "\"" + escape(gs.getWinCondition()) + "\"" : "null").append(",\n");
        json.append("    \"players\": {\n");

        boolean first = true;
        for (Map.Entry<String, GameSummary.PlayerGameStats> entry : gs.getPlayers().entrySet()) {
            if (!first) json.append(",\n");
            GameSummary.PlayerGameStats p = entry.getValue();
            json.append("      \"").append(escape(entry.getKey())).append("\": {\n");
            json.append("        \"total_cards_drawn\": ").append(p.getTotalCardsDrawn()).append(",\n");
            json.append("        \"card_draw_rate\": ").append(p.getCardDrawRate()).append(",\n");
            json.append("        \"total_spells_cast\": ").append(p.getTotalSpellsCast()).append(",\n");
            json.append("        \"spell_velocity\": ").append(p.getSpellVelocity()).append(",\n");
            json.append("        \"total_abilities_activated\": ").append(p.getTotalAbilitiesActivated()).append(",\n");
            json.append("        \"missed_land_drops\": ").append(p.getMissedLandDrops()).append(",\n");
            json.append("        \"total_lands_played\": ").append(p.getTotalLandsPlayed()).append(",\n");
            json.append("        \"peak_mana\": ").append(p.getPeakMana()).append(",\n");
            json.append("        \"total_damage_dealt\": ").append(p.getTotalDamageDealt()).append(",\n");
            json.append("        \"total_damage_received\": ").append(p.getTotalDamageReceived()).append(",\n");
            json.append("        \"total_creatures_played\": ").append(p.getTotalCreaturesPlayed()).append(",\n");
            json.append("        \"starting_life\": ").append(p.getStartingLife()).append(",\n");
            json.append("        \"ending_life\": ").append(p.getEndingLife()).append(",\n");
            json.append("        \"life_delta\": ").append(p.getLifeDelta()).append(",\n");
            json.append("        \"total_counters_placed\": ").append(p.getTotalCountersPlaced()).append("\n");
            json.append("      }");
            first = false;
        }
        json.append("\n    }\n");
        json.append("  }");
    }
}

