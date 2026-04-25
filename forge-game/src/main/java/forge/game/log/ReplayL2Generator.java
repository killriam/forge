package forge.game.log;

import forge.game.log.model.GameState;
import forge.game.log.model.L1Event;
import forge.game.log.model.L2Unit;
import forge.game.log.model.ReplayLog;

import java.util.*;

/**
 * Generates Level 2 (Learning View) Units from Level 1 event log.
 * Implements the L2 generation logic from MTG_REPLAY_NOTATION.md.
 */
public class ReplayL2Generator {

    private final ReplayLog replayLog;
    private int unitIndex = 0;

    // Player decision event types (evaluatable)
    private static final Set<String> DECISION_EVENTS = new HashSet<>(Arrays.asList(
        "CAST", "ACTIVATE", "CHOOSE", "DECLARE_ATTACKERS", "DECLARE_BLOCKERS",
        "PASS_PRIORITY", "MULLIGAN", "PLAY_LAND"
    ));

    public ReplayL2Generator(ReplayLog replayLog) {
        this.replayLog = replayLog;
    }

    /**
     * Generate all L2 units from the L1 event log.
     */
    public void generateL2Units() {
        List<L1Event> events = replayLog.getLogL1();
        if (events.isEmpty()) {
            return;
        }

        int i = 0;
        while (i < events.size()) {
            L2Unit unit = tryCreateUnit(events, i);
            if (unit != null) {
                replayLog.addL2Unit(unit);
                i = unit.getL1Range()[1] + 1; // Continue after this unit
            } else {
                i++; // Move to next event
            }
        }
    }

    /**
     * Try to create a unit starting at the given event index.
     * Returns null if no unit can be created.
     */
    private L2Unit tryCreateUnit(List<L1Event> events, int startIndex) {
        if (startIndex >= events.size()) {
            return null;
        }

        L1Event startEvent = events.get(startIndex);

        // Unit boundaries:
        // 1. Decision events (player actions)
        // 2. Phase transitions
        // 3. Priority passes that resolve stack

        if (!shouldStartUnit(startEvent)) {
            return null;
        }

        L2Unit unit = new L2Unit();
        unit.setU(unitIndex++);
        unit.setTStart(startEvent.getT());

        // Find decision events and end of unit
        List<Integer> decisionEvents = new ArrayList<>();
        int endIndex = startIndex;

        for (int i = startIndex; i < events.size(); i++) {
            L1Event event = events.get(i);

            if (isDecisionEvent(event)) {
                decisionEvents.add(event.getI());
            }

            // Check if this is a stable boundary
            if (i > startIndex && isStableBoundary(events, i)) {
                endIndex = i;
                break;
            }

            endIndex = i;
        }

        unit.setTEnd(events.get(endIndex).getT());
        unit.setL1Range(new int[]{startIndex, endIndex});
        unit.setDecisionEvents(decisionEvents);

        // Capture before state (simplified - would need full state tracking)
        unit.setBefore(createStateSnapshot(startEvent));

        // Build stack items
        buildStackItems(unit, events, startIndex, endIndex);

        // Capture after state
        unit.setAfter(createStateSnapshot(events.get(endIndex)));

        return unit;
    }

    /**
     * Check if this event should start a new unit.
     */
    private boolean shouldStartUnit(L1Event event) {
        // Start unit on:
        // - Decision events
        // - Phase changes
        // - Turn starts
        return isDecisionEvent(event) ||
               "PHASE_CHANGE".equals(event.getType()) ||
               "TURN_START".equals(event.getType());
    }

    /**
     * Check if this is a decision event.
     */
    private boolean isDecisionEvent(L1Event event) {
        return DECISION_EVENTS.contains(event.getType());
    }

    /**
     * Check if this is a stable boundary (end of unit).
     */
    private boolean isStableBoundary(List<L1Event> events, int index) {
        L1Event event = events.get(index);

        // Stable after:
        // - All stack resolution complete
        // - Phase transitions
        // - Priority passes with empty stack

        if ("PHASE_CHANGE".equals(event.getType())) {
            return true;
        }

        if ("PASS_PRIORITY".equals(event.getType())) {
            Object stackSize = event.getData().get("stack_size");
            return stackSize != null && (int) stackSize == 0;
        }

        // Check if next event starts a new context
        if (index + 1 < events.size()) {
            L1Event nextEvent = events.get(index + 1);
            if (shouldStartUnit(nextEvent)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Build stack items for this unit.
     */
    private void buildStackItems(L2Unit unit, List<L1Event> events, int start, int end) {
        Map<String, L2Unit.StackItem> stackItems = new HashMap<>();

        for (int i = start; i <= end; i++) {
            L1Event event = events.get(i);

            if ("PUT_ON_STACK".equals(event.getType())) {
                L2Unit.StackItem item = new L2Unit.StackItem();

                Object stackId = event.getData().get("stack");
                item.setStack(stackId != null ? stackId.toString() : "");

                Object kind = event.getData().get("kind");
                item.setKind(kind != null ? kind.toString() : "");

                Object controller = event.getData().get("controller");
                item.setController(controller != null ? controller.toString() : "");

                Object source = event.getData().get("source");
                item.setSource(source != null ? source.toString() : "");

                Object card = event.getData().get("card");
                item.setCard(card != null ? card.toString() : "");

                item.setLinkedDecisionEvent(event.getI());
                item.setOutcome("resolved"); // Default, can be updated

                stackItems.put(item.getStack(), item);
            } else if ("RESOLVE".equals(event.getType())) {
                Object stackId = event.getData().get("stack");
                if (stackId != null) {
                    L2Unit.StackItem item = stackItems.get(stackId.toString());
                    if (item != null) {
                        item.setOutcome("resolved");
                    }
                }
            }
        }

        unit.setStack(new ArrayList<>(stackItems.values()));
    }

    /**
     * Create a game state snapshot at this event.
     * Simplified version - full implementation would track complete state.
     */
    private GameState createStateSnapshot(L1Event event) {
        GameState state = new GameState();

        // Parse time marker to get turn/phase
        String timeMarker = event.getT();
        if (timeMarker != null && timeMarker.startsWith("T")) {
            try {
                int dotIndex = timeMarker.indexOf('.');
                if (dotIndex > 0) {
                    String turnStr = timeMarker.substring(1, dotIndex);
                    state.setTurn(Integer.parseInt(turnStr));

                    String phaseInfo = timeMarker.substring(dotIndex + 1);
                    int colonIndex = phaseInfo.indexOf(':');
                    if (colonIndex > 0) {
                        state.setPhase(phaseInfo.substring(0, colonIndex));
                    } else {
                        state.setPhase(phaseInfo);
                    }
                }
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        return state;
    }

    /**
     * Calculate deltas between two states.
     */
    public Map<String, Map<String, Object>> calculateDeltas(GameState before, GameState after) {
        Map<String, Map<String, Object>> deltas = new HashMap<>();

        // Compare player states
        for (String playerId : before.getPlayers().keySet()) {
            GameState.PlayerState beforePlayer = before.getPlayers().get(playerId);
            GameState.PlayerState afterPlayer = after.getPlayers().get(playerId);

            if (beforePlayer != null && afterPlayer != null) {
                Map<String, Object> playerDelta = new HashMap<>();
                playerDelta.put("life", afterPlayer.getLife() - beforePlayer.getLife());

                // Add more delta calculations as needed

                deltas.put(playerId, playerDelta);
            }
        }

        return deltas;
    }

    /**
     * Validate that all L2 units are properly formed.
     */
    public List<String> validateUnits() {
        List<String> errors = new ArrayList<>();
        List<L2Unit> units = replayLog.getViewsL2();

        for (int i = 0; i < units.size(); i++) {
            L2Unit unit = units.get(i);

            // Check unit index
            if (unit.getU() != i) {
                errors.add("Unit " + i + " has incorrect index: " + unit.getU());
            }

            // Check L1 range
            int[] range = unit.getL1Range();
            if (range == null || range.length != 2) {
                errors.add("Unit " + i + " has invalid L1 range");
            } else if (range[0] > range[1]) {
                errors.add("Unit " + i + " has invalid L1 range: start > end");
            }

            // Check time markers
            if (unit.getTStart() == null || unit.getTStart().isEmpty()) {
                errors.add("Unit " + i + " missing start time marker");
            }
            if (unit.getTEnd() == null || unit.getTEnd().isEmpty()) {
                errors.add("Unit " + i + " missing end time marker");
            }

            // Check states
            if (unit.getBefore() == null) {
                errors.add("Unit " + i + " missing 'before' state");
            }
            if (unit.getAfter() == null) {
                errors.add("Unit " + i + " missing 'after' state");
            }
        }

        return errors;
    }
}

