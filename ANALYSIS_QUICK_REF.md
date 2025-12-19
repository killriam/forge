# ANALYSIS Log Level - Quick Reference

## What It Does
New log level that shows:
1. Ô£à All PHASE information (phase transitions)
2. Ô£à Detailed zone changes (card name + owner + zones)
3. Ô£à Turn-end summaries with board state deltas

## Example Output

```
Phase: Player1's draw phase.
Analysis: Player1: Mountain moved from Library to Hand
Phase: Player1's first main phase.
Analysis: Player1: Lightning Bolt moved from Hand to Stack
Stack: Player1 cast Lightning Bolt.
Analysis: Resolving: Lightning Bolt
Analysis: Player1: Lightning Bolt moved from Stack to Graveyard
Resolve stack: Lightning Bolt
Damage: Lightning Bolt deals 3 damage to Player2.
...

Analysis: === Turn Summary - Board State Changes ===
Zone Changes:
  - Player1: Mountain moved from Library to Hand
  - Player1: Lightning Bolt moved from Hand to Stack
  - Player1: Lightning Bolt moved from Stack to Graveyard

Board State Delta:
Player1:
  Hand: 7 -> 6 (-1)
  Graveyard: 0 -> 1 (+1)
  Library: 52 -> 51 (-1)
```

## Key Features
- **Zone Changes**: Every card movement logged with full details
- **Turn Summaries**: Complete list of what happened each turn
- **Deltas**: Shows net changes (e.g., +1 battlefield, -2 hand)
- **Smart**: Only shows zones that changed

## How to Enable
Set log level to `GameLogEntryType.ANALYSIS` in game preferences.

## Files Changed
1. `GameLogEntryType.java` - Added ANALYSIS enum
2. `GameLogFormatter.java` - Added tracking logic
3. `GameLog.java` - Updated documentation

## Log Level Order (Most Verbose Last)
PHASE ÔåÆ **ANALYSIS** (new!)

ANALYSIS includes everything PHASE has, plus zone tracking and summaries.

## Perfect For
- ­ƒôè Strategy analysis
- ­ƒÄô Learning from games  
- ­ƒÉø Debugging issues
- ­ƒôØ Game documentation
- ­ƒÄ« Competitive review

## Integration
Works automatically with the game log auto-save feature!
Logs saved to: `<user_dir>/games/gamelogs/`

## Ready to Use Ô£à
No configuration needed - just select ANALYSIS log level and play!

