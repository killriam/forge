package forge.game.log;

import forge.game.log.model.L1Event;
import forge.game.log.model.L2Unit;
import forge.game.log.model.ReplayLog;

import java.util.*;

/**
 * Validates replay logs according to MTG Replay Notation specification.
 * Ensures completeness, consistency, and correctness of replay data.
 */
public class ReplayNotationValidator {

    private final ReplayLog replayLog;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public ReplayNotationValidator(ReplayLog replayLog) {
        this.replayLog = replayLog;
    }

    /**
     * Validate the entire replay log.
     * Returns true if valid, false otherwise.
     */
    public boolean validate() {
        errors.clear();
        warnings.clear();

        validateFormat();
        validateMeta();
        validateL1Events();
        validateL2Derivability();
        validateTimeMarkers();
        validateObjectIds();

        return errors.isEmpty();
    }

    /**
     * Validate basic format fields.
     */
    private void validateFormat() {
        if (replayLog.getFormat() == null || !replayLog.getFormat().equals("mtg-replay")) {
            errors.add("Invalid format field, expected 'mtg-replay'");
        }

        if (replayLog.getVersion() == null || replayLog.getVersion().isEmpty()) {
            errors.add("Missing version field");
        }
    }

    /**
     * Validate metadata.
     */
    private void validateMeta() {
        if (replayLog.getMeta() == null) {
            errors.add("Missing meta section");
            return;
        }

        if (replayLog.getMeta().getGameId() == null || replayLog.getMeta().getGameId().isEmpty()) {
            warnings.add("Missing game_id in meta");
        }

        if (replayLog.getMeta().getTimestamp() == null || replayLog.getMeta().getTimestamp().isEmpty()) {
            warnings.add("Missing timestamp in meta");
        }

        if (replayLog.getMeta().getPlayers() == null || replayLog.getMeta().getPlayers().isEmpty()) {
            errors.add("No players in meta");
        }
    }

    /**
     * Validate L1 event log.
     */
    private void validateL1Events() {
        List<L1Event> events = replayLog.getLogL1();

        if (events == null || events.isEmpty()) {
            warnings.add("No L1 events in log");
            return;
        }

        Set<Integer> seenIndices = new HashSet<>();

        for (int i = 0; i < events.size(); i++) {
            L1Event event = events.get(i);

            // Check event index monotonicity
            if (event.getI() != i) {
                errors.add("Event " + i + " has incorrect index: " + event.getI());
            }

            if (seenIndices.contains(event.getI())) {
                errors.add("Duplicate event index: " + event.getI());
            }
            seenIndices.add(event.getI());

            // Check required fields
            if (event.getT() == null || event.getT().isEmpty()) {
                errors.add("Event " + i + " missing time marker");
            }

            if (event.getA() == null || event.getA().isEmpty()) {
                errors.add("Event " + i + " missing actor");
            } else {
                String actor = event.getA();
                if (!actor.equals("SYS") && !actor.startsWith("P")) {
                    errors.add("Event " + i + " has invalid actor: " + actor);
                }
            }

            if (event.getType() == null || event.getType().isEmpty()) {
                errors.add("Event " + i + " missing event type");
            }

            if (event.getData() == null) {
                errors.add("Event " + i + " missing data");
            }

            // Validate specific event types
            validateEventType(event, i);
        }
    }

    /**
     * Validate specific event type requirements.
     */
    private void validateEventType(L1Event event, int index) {
        String type = event.getType();
        Map<String, Object> data = event.getData();

        switch (type) {
            case "MOVE":
                if (!data.containsKey("obj")) {
                    errors.add("Event " + index + " (MOVE) missing 'obj' field");
                }
                if (!data.containsKey("from")) {
                    errors.add("Event " + index + " (MOVE) missing 'from' field");
                }
                if (!data.containsKey("to")) {
                    errors.add("Event " + index + " (MOVE) missing 'to' field");
                }
                break;

            case "CAST":
                if (!data.containsKey("card")) {
                    errors.add("Event " + index + " (CAST) missing 'card' field");
                }
                if (!data.containsKey("cost")) {
                    errors.add("Event " + index + " (CAST) missing 'cost' field");
                }
                break;

            case "PUT_ON_STACK":
                if (!data.containsKey("stack")) {
                    errors.add("Event " + index + " (PUT_ON_STACK) missing 'stack' field");
                }
                if (!data.containsKey("kind")) {
                    errors.add("Event " + index + " (PUT_ON_STACK) missing 'kind' field");
                }
                break;

            case "RESOLVE":
                if (!data.containsKey("stack")) {
                    errors.add("Event " + index + " (RESOLVE) missing 'stack' field");
                }
                break;

            case "DAMAGE":
                if (!data.containsKey("source")) {
                    errors.add("Event " + index + " (DAMAGE) missing 'source' field");
                }
                if (!data.containsKey("target")) {
                    errors.add("Event " + index + " (DAMAGE) missing 'target' field");
                }
                if (!data.containsKey("amount")) {
                    errors.add("Event " + index + " (DAMAGE) missing 'amount' field");
                }
                break;

            case "LIFE":
                if (!data.containsKey("player")) {
                    errors.add("Event " + index + " (LIFE) missing 'player' field");
                }
                if (!data.containsKey("delta")) {
                    errors.add("Event " + index + " (LIFE) missing 'delta' field");
                }
                if (!data.containsKey("new_total")) {
                    errors.add("Event " + index + " (LIFE) missing 'new_total' field");
                }
                break;

            case "RANDOM":
                if (!data.containsKey("kind")) {
                    errors.add("Event " + index + " (RANDOM) missing 'kind' field");
                }
                if (!data.containsKey("seed")) {
                    errors.add("Event " + index + " (RANDOM) missing 'seed' field");
                }
                break;
        }
    }

    /**
     * Validate that L2 is derivable from L1.
     */
    private void validateL2Derivability() {
        List<L2Unit> units = replayLog.getViewsL2();
        List<L1Event> events = replayLog.getLogL1();

        if (units == null || units.isEmpty()) {
            warnings.add("No L2 units generated");
            return;
        }

        for (int i = 0; i < units.size(); i++) {
            L2Unit unit = units.get(i);
            int[] range = unit.getL1Range();

            if (range == null || range.length != 2) {
                errors.add("Unit " + i + " has invalid L1 range");
                continue;
            }

            int start = range[0];
            int end = range[1];

            if (start < 0 || start >= events.size()) {
                errors.add("Unit " + i + " L1 range start out of bounds: " + start);
            }

            if (end < 0 || end >= events.size()) {
                errors.add("Unit " + i + " L1 range end out of bounds: " + end);
            }

            if (start > end) {
                errors.add("Unit " + i + " L1 range invalid: start > end");
            }

            // Validate decision event references
            if (unit.getDecisionEvents() != null) {
                for (Integer eventIndex : unit.getDecisionEvents()) {
                    if (eventIndex < start || eventIndex > end) {
                        errors.add("Unit " + i + " references decision event " + eventIndex +
                                 " outside its L1 range [" + start + ", " + end + "]");
                    }
                }
            }
        }
    }

    /**
     * Validate time marker ordering and format.
     */
    private void validateTimeMarkers() {
        List<L1Event> events = replayLog.getLogL1();

        for (int i = 0; i < events.size(); i++) {
            L1Event event = events.get(i);
            String timeMarker = event.getT();

            if (timeMarker == null || timeMarker.isEmpty()) {
                continue; // Already reported
            }

            // Validate format: T<turn>.<phase>[:<priority>]
            if (!timeMarker.matches("^T\\d+\\.[A-Z_]+(:?\\d*)$")) {
                warnings.add("Event " + i + " has invalid time marker format: " + timeMarker);
            }
        }
    }

    /**
     * Validate object ID uniqueness and format.
     */
    private void validateObjectIds() {
        Set<String> seenCardIds = new HashSet<>();
        Set<String> seenStackIds = new HashSet<>();

        List<L1Event> events = replayLog.getLogL1();

        for (int i = 0; i < events.size(); i++) {
            L1Event event = events.get(i);
            Map<String, Object> data = event.getData();

            // Check card IDs
            if (data.containsKey("obj")) {
                String id = data.get("obj").toString();
                if (id.startsWith("c") && !seenCardIds.contains(id)) {
                    seenCardIds.add(id);
                }
                validateObjectIdFormat(id, "obj", i);
            }

            if (data.containsKey("card")) {
                String id = data.get("card").toString();
                if (id.startsWith("c") && !seenCardIds.contains(id)) {
                    seenCardIds.add(id);
                }
                validateObjectIdFormat(id, "card", i);
            }

            // Check stack IDs
            if (data.containsKey("stack")) {
                String id = data.get("stack").toString();
                if (id.startsWith("s") && !seenStackIds.contains(id)) {
                    seenStackIds.add(id);
                }
                validateObjectIdFormat(id, "stack", i);
            }
        }
    }

    /**
     * Validate object ID format.
     */
    private void validateObjectIdFormat(String id, String fieldName, int eventIndex) {
        if (id == null || id.isEmpty()) {
            return;
        }

        // IDs should be: c123, t45, s7, P1, P2
        if (!id.matches("^[ctsPT]\\d+$")) {
            warnings.add("Event " + eventIndex + " field '" + fieldName +
                       "' has invalid ID format: " + id);
        }
    }

    /**
     * Get validation errors.
     */
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Get validation warnings.
     */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    /**
     * Get validation report as string.
     */
    public String getReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Replay Validation Report ===\n\n");

        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("✓ Validation passed with no errors or warnings\n");
        } else {
            if (!errors.isEmpty()) {
                sb.append("ERRORS (").append(errors.size()).append("):\n");
                for (String error : errors) {
                    sb.append("  ✗ ").append(error).append("\n");
                }
                sb.append("\n");
            }

            if (!warnings.isEmpty()) {
                sb.append("WARNINGS (").append(warnings.size()).append("):\n");
                for (String warning : warnings) {
                    sb.append("  ⚠ ").append(warning).append("\n");
                }
            }
        }

        return sb.toString();
    }
}

