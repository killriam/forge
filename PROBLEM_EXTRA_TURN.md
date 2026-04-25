# Problem: Incorrect Handling of Extra Turns (e.g., Lighthouse Chronologist)

## Problem Description

- In multiplayer games, the ability to grant extra turns was not implemented correctly.
- The method `getCurrentTurnPosition()` was used in the code, but it does not exist and is not suitable for managing turn order.
- The insertion and management of extra turns did not occur at the correct position in the turn order.

## Analysis

- Turn order is managed via the order of players in the `PlayerCollection` (`ingamePlayers`) and a stack of `ExtraTurn` objects in the `PhaseHandler`.
- The position of an extra turn in the turn order can be determined by the size of the stack (`extraTurns.size()`).
- The incorrect use of `getCurrentTurnPosition()` led to compilation errors and faulty logic.

## Solution/Approach (as of 2026-04-01)

1. **Code Correction**
   - The erroneous call to `getCurrentTurnPosition()` in `AddTurnEffect` was removed.
   - The turn order position of an `ExtraTurn` is now set directly in `PhaseHandler.addExtraTurn()` based on the stack size.
   - The method `setTurnOrderPosition()` is still used, but is no longer set with a non-existent method.
   - The tests were updated so they no longer access the non-existent method, but instead check that `setTurnOrderPosition()` is called when adding an `ExtraTurn`.

2. **Test Adjustment**
   - The tests now check that the method `setTurnOrderPosition()` is called when adding an `ExtraTurn`.
   - It is still verified that extra turns are only added for active players.

3. **Build and Test Process**
   - It is essential to first run a full build with `mvn clean install` so that all dependencies are resolved correctly.
   - Afterwards, tests in the `forge-game` module can be run specifically.

## Result

- The handling of extra turns is now robust and compatible with multiplayer scenarios.
- The tests are updated and correctly verify the desired behavior.
- Compilation errors due to the non-existent method are resolved.
- The changes were made and tested on 2026-04-01.
