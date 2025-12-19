# Game Log Auto-Save Feature

## Overview
This feature automatically saves game logs to text files after each game ends.

## Implementation Date
December 14, 2025

## What Was Added

### 1. New Directory Constant
**File**: `forge-gui/src/main/java/forge/localinstance/properties/ForgeConstants.java`
- Added `GAME_LOG_DIR` constant pointing to `USER_GAMES_DIR/gamelogs/`
- Game logs will be saved in: `<user_dir>/games/gamelogs/`

### 2. GameLogSaver Utility Class
**File**: `forge-gui/src/main/java/forge/game/GameLogSaver.java`
- New utility class for saving game logs to files
- **Methods**:
  - `saveGameLog(GameView)` - Saves log and returns File object
  - `saveGameLogAndGetPath(GameView)` - Saves log and returns file path string

**Features**:
- Creates `gamelogs` directory automatically if it doesn't exist
- Generates timestamped filenames: `gamelog_<GameType>_<YYYY-MM-DD_HH-mm-ss>.txt`
- Includes header with game type and date/time
- Replaces `[COMPUTER]` with `[AI]` for readability
- Handles errors gracefully

### 3. Desktop Integration
**File**: `forge-gui-desktop/src/main/java/forge/screens/match/ViewWinLose.java`
- Auto-saves game log when win/lose screen is shown
- Prints save location to console: `"Game log saved to: <path>"`
- Integrated into `show()` method

### 4. Mobile Integration
**File**: `forge-gui-mobile/src/forge/screens/match/winlose/ViewWinLose.java`
- Auto-saves game log when win/lose screen is displayed
- Prints save location to console
- Integrated into constructor

## Log File Format

```
======================================
Forge Game Log
Game Type: <GameType>
Date: YYYY-MM-DD HH:mm:ss
======================================

<Log entries in chronological order>
Turn: 1: Turn 1 owned by Player1
Phase: Player1's untap phase.
Phase: Player1's upkeep phase.
...
```

## Log File Locations

### Windows
`C:\Users\<username>\AppData\Roaming\Forge\games\gamelogs\`

### Linux/Mac
`~/.forge/games/gamelogs/`

## Example Filenames
- `gamelog_Constructed_2025-12-14_15-30-45.txt`
- `gamelog_Draft_2025-12-14_16-22-10.txt`
- `gamelog_Quest_2025-12-14_17-05-33.txt`
- `gamelog_Commander_2025-12-14_18-44-21.txt`

## Benefits
1. **Automatic**: No user action required
2. **Persistent**: Logs survive application restart
3. **Organized**: Timestamped and game-type labeled
4. **Accessible**: Plain text format, easy to read and search
5. **Shareable**: Can be shared for bug reports or strategy analysis
6. **Compatible**: Works for all game modes (Constructed, Draft, Sealed, Quest, Gauntlet, Commander, etc.)

## Use Cases
- **Bug reporting**: Attach log to bug reports for developers
- **Strategy analysis**: Review decisions made during games
- **Learning**: Study opponent's plays and game flow
- **Record keeping**: Maintain history of games played
- **Debugging**: Troubleshoot game rules or AI behavior

## Technical Details
- Logs are saved when the win/lose screen appears (after game ends)
- One file per game
- File I/O is synchronous but fast (typically < 100ms)
- No performance impact on gameplay
- No file size limits (grows with game length)
- Old logs are never deleted automatically (manual cleanup if needed)

## Future Enhancements (Optional)
- Add preference to enable/disable auto-save
- Add "Open Log Folder" button in UI
- Option to save logs in JSON format
- Automatic cleanup of old logs (configurable retention period)
- Include match result summary at top of log
- Compress old logs automatically

## Notes
- Existing "Copy to Clipboard" functionality remains unchanged
- This feature complements manual clipboard copying
- Console output confirms successful saves for debugging

