# How to Use the ANALYSIS Log Level

## Quick Start Guide

### Step 1: Enable ANALYSIS Logging

**Desktop Version:**
1. Launch Forge Desktop
2. Go to **Preferences** ÔåÆ **Developer Settings**
3. Find **"Game Log Entry Type"**
4. Select **"ANALYSIS"** from the dropdown
5. Click **Save**

**Alternative (All Versions):**
The default log level is DAMAGE. To see ANALYSIS logs, you need to change the preference.

### Step 2: Play a Game

1. Start any game mode (Quest, Gauntlet, Standard, Commander, etc.)
2. Play through at least one turn
3. The ANALYSIS log will capture:
   - All phase transitions (same as PHASE level)
   - Every zone change with card names
   - Spell resolutions
   - Turn-end summaries with board state deltas

### Step 3: View the Log

**During the Game:**
- The game log panel shows all log entries in real-time
- Scroll to see the full log

**After the Game:**
- The Win/Lose screen displays the complete game log
- Click **"Copy to Clipboard"** to export the log
- The log is automatically saved to:
  ```
  <forge_user_dir>/games/gamelogs/gamelog_<GameType>_<timestamp>.txt
  ```

### Example: What You'll See

Here's a sample of ANALYSIS log output from a single turn:

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
Stack: Player1 cast Lightning Bolt targeting Player2.
Analysis: Resolving: Lightning Bolt
Analysis: Player1: Lightning Bolt moved from Stack to Graveyard
Resolve stack: Lightning Bolt
Damage: Lightning Bolt deals 3 damage to Player2.
Phase: Player1's begin combat phase.
Phase: Player1's declare attackers phase.
Player1 didn't attack this turn.
Phase: Player1's declare blockers phase.
Phase: Player1's combat damage phase.
Phase: Player1's end combat phase.
Phase: Player1's second main phase.
Phase: Player1's end step.
Phase: Player1's cleanup phase.

Analysis: === Turn Summary - Board State Changes ===
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

## What Gets Logged at ANALYSIS Level

### 1. Phase Information (Inherited from PHASE level)
- Every phase transition (untap, upkeep, draw, main, combat phases, etc.)
- Format: `Phase: <Player>'s <phase name>.`

### 2. Zone Changes
- **Every card movement** between zones
- Includes card name, owner, source zone, and destination zone
- Examples:
  - Drawing: `Player1: Island moved from Library to Hand`
  - Playing lands: `Player1: Forest moved from Hand to Battlefield`
  - Casting spells: `Player2: Counterspell moved from Hand to Stack`
  - Creatures dying: `Player1: Grizzly Bears moved from Battlefield to Graveyard`
  - Exiling: `Player2: Swords to Plowshares moved from Stack to Exile`
  - Discarding: `Player1: Plains moved from Hand to Graveyard`

### 3. Spell Resolution Markers
- When a spell finishes resolving
- Format: `Analysis: Resolving: <CardName>`
- Helps identify exactly when effects happen

### 4. Turn-End Summaries
Generated at the end of each turn, showing:

**Zone Changes Section:**
- Complete list of all card movements during the turn
- Chronological order
- Full context for each move

**Board State Delta Section:**
- Shows net changes for each zone
- Format: `<Zone>: <start> -> <end> (┬▒delta)`
- Only displays zones that changed
- Tracked zones:
  - Battlefield
  - Hand
  - Graveyard
  - Library
  - Exile

## Use Cases

### 1. Game Analysis
Use ANALYSIS logs to:
- Track card advantage changes turn by turn
- Identify key decision points
- Understand resource flow
- Analyze tempo plays

### 2. Learning & Improvement
- Review your games to see what happened
- Study opponent strategies
- Understand complex game states
- Learn from mistakes

### 3. Bug Reporting
- Provide detailed logs when reporting issues
- Show exact sequence of zone changes
- Verify game state transitions
- Help developers reproduce problems

### 4. Content Creation
- Generate detailed game reports
- Create play-by-play commentary
- Document interesting games
- Share strategic insights

### 5. Rules Learning
- See exactly when cards change zones
- Understand spell resolution timing
- Track state-based actions
- Learn triggered abilities

## Tips for Using ANALYSIS Logs

### Performance
- ANALYSIS is the most verbose log level
- Generates more data than other levels
- Minimal performance impact (just string operations)
- Log files will be larger

### Filtering
If you want to see specific events:
```java
// Get only zone changes
List<GameLogEntry> zoneChanges = 
    game.getGameLog().getLogEntriesExact(GameLogEntryType.ANALYSIS);

// Get everything up to ANALYSIS level (includes all lower levels)
List<GameLogEntry> fullLog = 
    game.getGameLog().getLogEntries(GameLogEntryType.ANALYSIS);
```

### Searching Logs
When reviewing saved log files, search for:
- `"=== Turn Summary"` - Find turn-end summaries
- `"moved from"` - Find all zone changes
- `"Resolving:"` - Find spell resolutions
- Player names - Track specific player actions
- Card names - Track specific cards

### Combining with Other Tools
- Export logs to spreadsheet for analysis
- Use text editor to search/filter
- Combine with replay feature
- Compare logs from different games

## Log Levels Comparison

| Level | What You See |
|-------|-------------|
| TURN | Turn changes only |
| PHASE | Turn changes + all phases |
| DAMAGE | PHASE + damage events |
| MANA | DAMAGE + mana abilities |
| **ANALYSIS** | **Everything + zone changes + turn summaries** |

## Troubleshooting

### Not Seeing ANALYSIS Entries?
1. Verify log level is set to ANALYSIS in preferences
2. Check that you're playing a game (not just deck building)
3. Ensure at least one turn has completed
4. Try restarting Forge after changing preferences

### Log File Not Created?
- Check the user directory: `<forge_user_dir>/games/gamelogs/`
- Verify write permissions
- Look for error messages in console
- Log is created when game ends (Win/Lose screen)

### Too Much Information?
If ANALYSIS is too verbose, try:
- PHASE level - Shows phases without zone details
- DAMAGE level - Shows combat and damage
- TURN level - Just turn changes

## Advanced: Programmatic Access

### Reading ANALYSIS Logs in Code

```java
// Get the game log
GameLog log = game.getGameLog();

// Get all ANALYSIS-level entries
List<GameLogEntry> analysisEntries = 
    log.getLogEntriesExact(GameLogEntryType.ANALYSIS);

// Process each entry
for (GameLogEntry entry : analysisEntries) {
    String message = entry.message;
    GameLogEntryType type = entry.type;
    
    if (message.contains("=== Turn Summary")) {
        // This is a turn summary
        System.out.println("Turn Summary Found:");
        System.out.println(message);
    } else if (message.contains("moved from")) {
        // This is a zone change
        System.out.println("Zone Change: " + message);
    } else if (message.startsWith("Resolving:")) {
        // This is a spell resolution
        System.out.println("Spell Resolved: " + message);
    }
}
```

### Saving Custom Log Format

```java
// Get the game
GameView game = ...;

// Create custom log output
StringBuilder customLog = new StringBuilder();
List<GameLogEntry> entries = game.getGameLog().getLogEntries(GameLogEntryType.ANALYSIS);

for (GameLogEntry entry : entries) {
    // Add timestamp or custom formatting
    customLog.append("[").append(entry.type).append("] ");
    customLog.append(entry.message).append("\n");
}

// Save to custom location
Files.write(Paths.get("my_custom_log.txt"), customLog.toString().getBytes());
```

## Related Documentation

- `ANALYSIS_LOG_LEVEL.md` - Complete technical documentation
- `ANALYSIS_QUICK_REF.md` - Quick reference guide
- `ANALYSIS_IMPLEMENTATION_SUMMARY.md` - Implementation details
- `GAME_LOG_FEATURE.md` - Game log auto-save feature

## Questions?

The ANALYSIS log level is designed to give you complete visibility into the game state. If you need different information or have suggestions for improvements, please provide feedback!

---

**Remember:** ANALYSIS logs everything that happens in a game. It's perfect for detailed analysis, learning, and debugging, but if you just want basic game flow, try PHASE or DAMAGE levels instead.

