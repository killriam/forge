# ANALYSIS Log Level Implementation

## Overview
Added a new "ANALYSIS" log level that extends PHASE logging with detailed zone change tracking and board state delta summaries.

## Implementation Date
December 14, 2025 (Initial)
December 19, 2025 (Enhanced spell resolution logging)

## What Was Added

### 1. New Log Level - ANALYSIS
**File**: `forge-game/src/main/java/forge/game/GameLogEntryType.java`
- Added `ANALYSIS("Analysis")` as the most verbose log level
- Position: After PHASE, before the enum end
- This level includes everything from PHASE plus additional analysis features

### 2. Zone Change Tracking
**File**: `forge-game/src/main/java/forge/game/GameLogFormatter.java`

Added handler for `GameEventCardChangeZone`:
- Logs every card movement between zones with full details
- Format: `<Owner>: <CardName> moved from <FromZone> to <ToZone>`
- Examples:
  - `Player1: Lightning Bolt moved from Hand to Stack`
  - `Player2: Grizzly Bears moved from Battlefield to Graveyard`
  - `Player1: Island moved from Library to Hand`

Enhanced `GameEventSpellResolved` handler:
- Logs when spells are resolving for better ANALYSIS-level tracking
- Format: `Resolving: <CardName>`
- This appears in the log right before the zone change (Stack ÔåÆ Graveyard)
- Helps distinguish between spell resolution and other zone changes

### 3. Board State Delta Tracking
**Features**:
- Captures complete board state at the start of each turn
- Tracks changes to key zones for each player:
  - Battlefield
  - Hand
  - Graveyard
  - Library
  - Exile

**At Turn End**:
- Generates a comprehensive summary showing:
  - All zone changes that occurred during the turn
  - Delta calculations for each zone (starting count ÔåÆ ending count)
  - Only displays zones that changed

### 4. Turn Summary Format

```
=== Turn Summary - Board State Changes ===
Zone Changes:
  - Player1: Mountain moved from Library to Hand
  - Player1: Lightning Bolt moved from Hand to Stack
  - Player1: Lightning Bolt moved from Stack to Graveyard
  - Player2: Grizzly Bears moved from Battlefield to Graveyard

Board State Delta:
Player1:
  Battlefield: 3 -> 3 (+0)
  Hand: 7 -> 6 (-1)
  Graveyard: 1 -> 2 (+1)
  Library: 52 -> 51 (-1)
Player2:
  Battlefield: 5 -> 4 (-1)
  Hand: 6 -> 6 (+0)
  Graveyard: 2 -> 3 (+1)
```

## Log Level Hierarchy

From least to most verbose:

1. **GAME_OUTCOME** - Game results only
2. **MATCH_RESULTS** - Match summary
3. **TURN** - Turn changes
4. **MULLIGAN** - Mulligan events
5. **ANTE** - Ante cards
6. **DRAFT** - Draft picks
7. **ZONE_CHANGE** - Zone changes (less detailed)
8. **PLAYER_CONTROL** - Control changes
9. **COMBAT** - Combat events
10. **DISCARD** - Discard events
11. **INFORMATION** - General info
12. **EFFECT_REPLACED** - Replacement effects
13. **LAND** - Land plays
14. **STACK_RESOLVE** - Stack resolution
15. **STACK_ADD** - Add to stack
16. **DAMAGE** - Damage events
17. **MANA** - Mana abilities
18. **PHASE** - Phase changes
19. **ANALYSIS** - Ô¡É NEW: Zone changes + board state deltas

## How to Use

### Set Log Level to ANALYSIS

In game preferences or settings:
```java
GameLogEntryType logLevel = GameLogEntryType.ANALYSIS;
List<GameLogEntry> analysisLog = game.getGameLog().getLogEntries(logLevel);
```

### What You'll See

With ANALYSIS level enabled, you get:
- Ô£à Everything from PHASE level (all phase transitions)
- Ô£à Detailed zone changes with card names
- Ô£à Turn-by-turn board state summaries
- Ô£à Delta calculations showing net changes

### Example ANALYSIS Log Output

```
Turn: Turn 1 owned by Player1
Phase: Player1's untap phase.
Phase: Player1's upkeep phase.
Phase: Player1's draw phase.
Analysis: Player1: Mountain moved from Library to Hand
Phase: Player1's first main phase.
Analysis: Player1: Mountain moved from Hand to Battlefield
Land: Player1 played Mountain.
Analysis: Player1: Lightning Bolt moved from Hand to Stack
Stack: Player1 cast Lightning Bolt.
Analysis: Resolving: Lightning Bolt
Analysis: Player1: Lightning Bolt moved from Stack to Graveyard
Resolve stack: Lightning Bolt
Damage: Lightning Bolt deals 3 damage to Player2.
Phase: Player1's begin combat phase.
Phase: Player1's declare attackers phase.
Phase: Player1's declare blockers phase.
Phase: Player1's combat damage phase.
Phase: Player1's end combat phase.
Phase: Player1's second main phase.
Phase: Player1's end step.
Phase: Player1's cleanup phase.

=== Turn Summary - Board State Changes ===
Zone Changes:
  - Player1: Mountain moved from Library to Hand
  - Player1: Mountain moved from Hand to Battlefield
  - Player1: Lightning Bolt moved from Hand to Stack
  - Player1: Lightning Bolt moved from Stack to Graveyard

Board State Delta:
Player1:
  Battlefield: 0 -> 1 (+1)
  Hand: 7 -> 6 (-1)
  Graveyard: 0 -> 1 (+1)
  Library: 53 -> 52 (-1)
```

## Use Cases

### 1. Game Analysis
- Track resource flow (cards drawn, played, discarded)
- See exactly when cards moved between zones
- Analyze tempo and card advantage

### 2. Strategy Review
- Review how your board state changed each turn
- Identify key moments (large zone shifts)
- Understand resource management

### 3. Debugging
- Verify correct game state transitions
- Identify unexpected zone changes
- Track card movements for rules verification

### 4. Learning
- Study opponent's plays
- Understand typical game flow patterns
- Learn from mistakes by reviewing zone changes

### 5. Content Creation
- Generate detailed game reports
- Create play-by-play commentary
- Document interesting games

## Technical Details

### Board State Tracking
- `Map<Player, Map<ZoneType, Integer>>` stores state at turn start
- Cleared and recaptured each turn
- Lightweight - only stores counts, not card objects

### Zone Change Tracking
- `List<String>` accumulates zone changes during the turn
- Cleared at turn start
- Includes all zone transitions with full context

### Performance Impact
- Minimal: Only string building and map operations
- No game logic impact
- Log entries created only when ANALYSIS level is active

## Future Enhancements (Optional)

Could be extended with:
- Permanent type breakdown (creatures vs. artifacts vs. enchantments)
- Mana base analysis (land counts by type)
- Life total changes per turn
- Card advantage calculations
- Tempo metrics
- Spell velocity (spells cast per turn)

## Benefits

1. **Complete Transparency**: See every card movement
2. **Turn Summaries**: Quick overview of what happened
3. **Delta Calculations**: Understand net changes
4. **Strategic Insight**: Better game analysis
5. **Educational**: Learn from detailed game flow
6. **Debugging Aid**: Verify correct game state

## Compatibility

- Ô£à Works with all game modes
- Ô£à Compatible with existing log levels
- Ô£à No breaking changes to existing code
- Ô£à Opt-in: Only active when ANALYSIS level selected
- Ô£à No performance impact on other log levels

## Files Modified

1. `GameLogEntryType.java` - Added ANALYSIS enum value
2. `GameLogFormatter.java` - Added zone change and board state tracking
3. `GameLog.java` - Updated documentation

## Testing

To test the ANALYSIS level:
1. Set log verbosity to `GameLogEntryType.ANALYSIS`
2. Play a game
3. Check the game log for:
   - Zone change entries for each card movement
   - Turn summary at end of each turn
   - Board state deltas showing changes

The log will be automatically saved to file (from previous feature) in:
`<user_dir>/games/gamelogs/gamelog_<GameType>_<timestamp>.txt`

