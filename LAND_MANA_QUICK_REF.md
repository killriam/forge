# Land and Mana Logging - Quick Reference

## What It Does
Automatically logs land count and available mana after each player's untap step.

## Log Format
```
<PlayerName> has <N> land(s) and <M> available mana after untap.
```

## Example
```
Turn: Turn 3 owned by Player1
Phase: Player1's untap phase.
Player1 has 2 lands and 2 available mana after untap.
Phase: Player1's upkeep phase.
Phase: Player1's draw phase.
[...]
```

## When It Appears
- Ô£à After every untap step
- Ô£à For each player in turn order
- Ô£à At PHASE log level or higher

## What It Shows

### Land Count
- All lands on the battlefield
- Includes basic and non-basic lands
- Counts any card with Land type

### Available Mana
- Mana in the pool (floating)
- Mana from untapped sources
- Estimate based on mana abilities

## How to See It

### Enable Feature
**Already enabled!** Just set log level to PHASE or higher.

**Desktop**:
- Preferences ÔåÆ Developer ÔåÆ Game Log Entry Type ÔåÆ Select "PHASE" (or higher)

**Mobile**:
- Settings ÔåÆ Game Log Entry Type ÔåÆ Select "PHASE" (or higher)

### View Logs
- **During game**: In the game log panel
- **After game**: Win/Lose screen
- **Saved**: `<user_dir>/games/gamelogs/gamelog_*.txt`

## Log Levels

| Level | Shows Land/Mana Info? |
|-------|----------------------|
| TURN | ÔØî No |
| MULLIGAN | ÔØî No |
| PHASE | Ô£à Yes |
| DAMAGE | Ô£à Yes |
| MANA | Ô£à Yes |
| ANALYSIS | Ô£à Yes |

## Use Cases

### ­ƒÄ» Track Mana Development
See how mana grows turn by turn

### ­ƒôè Compare Players
See who has mana advantage

### ­ƒôÜ Learn Optimal Curves
Understand good mana progression

### ­ƒöì Analyze Games
Review mana decisions

### ­ƒÉø Debug Issues
Verify mana abilities work

## Examples

### Early Game
```
Turn 1:
Player1 has 0 lands and 0 available mana after untap.

Turn 2:
Player2 has 1 land and 1 available mana after untap.

Turn 3:
Player1 has 2 lands and 2 available mana after untap.
```

### With Mana Rocks
```
Turn 4:
Player2 has 3 lands and 5 available mana after untap.
# Player2 has Sol Ring (+2 mana)
```

### Ramp Deck
```
Turn 5:
Player1 has 7 lands and 7 available mana after untap.
# Player1 ramped with cultivate/rampant growth
```

## Accuracy

### Land Count
Ô£à **100% Accurate** - Counts all lands on battlefield

### Available Mana
ÔÜá´©Å **Estimate** - Useful approximation, but:
- Doesn't account for color restrictions
- Doesn't consider conditional abilities
- Uses best ability per source
- Good enough for tracking and analysis

## Quick Tips

### Understand Mana Advantage
```
Player1 has 5 lands and 7 available mana after untap.
Player2 has 5 lands and 5 available mana after untap.
# Player1 has +2 mana advantage (probably has rocks)
```

### Spot Mana Issues
```
Turn 6:
Player1 has 3 lands and 3 available mana after untap.
# Player1 might be mana screwed
```

### Track Ramp Success
```
Turn 4:
Player1 has 6 lands and 6 available mana after untap.
# Successful ramp strategy
```

## Implementation Details

### What's Counted
- **Lands**: All cards with Land type on battlefield
- **Mana Sources**: Untapped permanents with mana abilities
- **Mana Pool**: Any floating mana from previous phases

### What's NOT Counted
- Lands in hand (not played yet)
- Tapped mana sources (can't be used now)
- Conditional mana (can't determine if condition is met)

### When It Runs
- After untap step completes
- Before upkeep phase begins
- Once per player per turn

## Troubleshooting

### Not Seeing Logs?
1. Ô£à Check log level is PHASE or higher
2. Ô£à Verify you're in a game (not deck building)
3. Ô£à Ensure at least one turn has passed

### Numbers Seem Wrong?
- Land count should always be accurate
- Mana is an estimate (may differ from exact)
- Check for tapped lands (won't count towards mana)

### Want More Detail?
- Use ANALYSIS level for more verbose logs
- Check turn summaries for zone changes
- Review full game log after match

## Related Features

- **ANALYSIS Log Level**: Shows all zone changes and board state
- **Game Log Auto-Save**: Saves logs to file automatically
- **Phase Logging**: Shows all phase transitions

## Benefits Summary

Ô£à **Automatic** - No manual tracking needed
Ô£à **Consistent** - Same format every turn
Ô£à **Informative** - Clear resource status
Ô£à **Lightweight** - Minimal performance impact
Ô£à **Always On** - Works at PHASE level by default

## Quick Start

1. Set log level to **PHASE** or higher
2. Play a game
3. Look for land/mana info after untap steps

**That's it!** The feature works automatically.

---

**See `LAND_MANA_LOGGING_FEATURE.md` for complete documentation.**

