# Feature: Game Replay

Forge supports two ways to replay a previously recorded game:

| Mode | Audience | Entry Point |
|------|----------|-------------|
| **CLI Simulation** (`sim -r`) | Developers / batch analysis | `SimulateMatch` CLI flag |
| **Interactive GUI Replay** | Players | Home → *Replay Mode* → *Replay Game* |

Both modes share the same library-reordering infrastructure (`ReplayLibraryReorderer`)
so the opening-hand draw sequence matches the original game exactly.

---

## 1. Interactive GUI Replay Mode

### Overview

Allows a human player to select any previously saved game log and replay it interactively —
playing the same decks, in the same library order, against an AI opponent.

### User Flow

1. Main menu → **Replay Mode** group → **Replay Game**
2. A list of available replay logs (newest first) is shown.
3. Select a log — the info panel shows players, decks, turns, winner.
4. Click **Start** — the game begins with:
   - **You** (Human) as P1
   - All other players replaced by AI
   - Library order identical to the original game
5. After starting, the log is **flagged as replayed** (`meta.replayed_at`) and
   disappears from the list so it cannot be accidentally replayed twice.

### Direct CLI Launch

You can also open a specific replay directly from the command line without navigating
the home screen first:

```bash
# Windows (from forge-gui-desktop directory)
java -jar target\forge-gui-desktop-*.jar replay "C:\Users\You\AppData\Roaming\Forge\games\gamelogs\replay_Commander_2026-03-29_07-05-10.json"

# Short form with relative path
java -jar forge-gui-desktop-*.jar replay path/to/replay.json
```

Forge opens normally, loads all data, and **automatically starts the game** as soon as
the home screen is ready. If the file has a `replayed_at` flag (already been replayed),
a confirmation dialog asks whether to replay it again.

| Argument | Description |
|----------|-------------|
| `replay` | Mode name (first argument after the JAR) |
| `<path>` | Absolute or relative path to the replay JSON file |

### Architecture

```
VSubmenuReplay  ──(Start button)──►  CSubmenuReplay.startReplayGame()
                                             │
                                     ReplayLogParser.parse()
                                             │  reads meta, players,
                                             │  reconstructs Deck objects
                                             │  from initial_state.objects
                                             ▼
                                     GameRules.replayLogPath = file path
                                             │
                                     HostedMatch.startMatch()
                                             │
                                     GameAction.startGame()
                                             │
                              ReplayLibraryReorderer.reorderLibraries()
                                             │  (runs after shuffle, before drawCards)
                                             ▼
                                     Interactive game — human plays
```

### Involved Files

| File | Module | Role |
|------|--------|------|
| `ReplayLogParser.java` | `forge-gui` | Parses replay JSON, reconstructs `Deck` objects, manages `replayed_at` flag |
| `VSubmenuReplay.java` | `forge-gui-desktop` | Swing view: replay list, info panel, Start button |
| `CSubmenuReplay.java` | `forge-gui-desktop` | Controller: scans log dir, builds players, launches match; holds `pendingReplayPath` for CLI mode |
| `Main.java` | `forge-gui-desktop` | `replay` CLI mode: sets `pendingReplayPath`, boots GUI normally |
| `EMenuGroup.java` | `forge-gui-desktop` | `REPLAY` menu group entry |
| `EDocID.java` | `forge-gui-desktop` | `HOME_REPLAY` doc ID |
| `VHomeUI.java` | `forge-gui-desktop` | Registers `VSubmenuReplay` in the home navigation |
| `ForgePreferences.java` | `forge-gui` | `SUBMENU_REPLAY` pref (expanded/collapsed state) |
| `ReplayLibraryReorderer.java` | `forge-game` | Core library reorder (shared with CLI mode) |
| `GameRules.java` | `forge-game` | `replayLogPath` field consumed by `GameAction` |

### Deck Reconstruction

`ReplayLogParser` rebuilds each player's deck from the JSON file's `initial_state.objects`:

1. Each object has `cardRef` (card name), `owner` (P1/P2/…) and `zone` (library/hand/command).
2. All `library` and `hand` objects → `DeckSection.Main`
3. All `command` zone objects → `DeckSection.Commander` (for Commander/Oathbreaker)
4. Card names are resolved via `StaticData.instance().getCommonCards().getCard(name)`
5. Fallback for older files without `initial_state.objects`: reconstructs from `card_index` + DRAW events

### Replayed-Flag

To prevent a log from being replayed more than once, `ReplayLogParser.markAsReplayed()`
writes a `"replayed_at"` timestamp into the JSON file's `meta` section:

```json
{
  "format": "mtg-replay",
  "meta": {
    "game_id": "...",
    "replayed_at": "2026-03-29T07:45:00Z"
  }
}
```

On the next `CSubmenuReplay.updateData()` scan, files with `replayed_at` set are silently
skipped. The flag is written **before** the match starts so a crash does not re-expose
the entry.

### Game Type Detection

`CSubmenuReplay` maps `meta.game_type` to a `GameType` enum:

| `game_type` contains | `GameType` used |
|----------------------|-----------------|
| `commander` | `Commander` (+40 life, commander zone) |
| `oathbreaker` | `Oathbreaker` |
| `tiny` | `TinyLeaders` |
| `brawl` | `Brawl` |
| `sealed` | `Sealed` |
| `draft` | `Draft` |
| *(anything else)* | `Constructed` |

---

## 2. CLI Simulation Replay (`sim -r`)

### Overview

Deterministic headless simulation: the library is reordered to reproduce the exact
same draw sequence as a recorded game. Used for automated testing, debugging, and
batch analysis.

### CLI Usage

```bash
# Replay a previous game (single game, libraries reordered to match draws)
java -jar forge-gui-desktop-*.jar sim \
    -d deck1.dck -d deck2.dck \
    -n 1 \
    -r path/to/replay_log.json

# Commander format + quiet mode
java -jar forge-gui-desktop-*.jar sim \
    -d commander_deck1.dck -d commander_deck2.dck \
    -n 1 -f commander -q \
    -r path/to/replay_log.json
```

| Flag | Description |
|------|-------------|
| `-r <path>` | Path to the MTG Replay Notation JSON file from a previous game |

### Architecture

```
SimulateMatch  ──(-r flag)──►  GameRules.replayLogPath
                                       │
Match.startGame()                      │
  └─ prepareAllZones()                 │  (initial shuffle happens here)
       └─ player.shuffle(null)         │
                                       ▼
GameAction.startGame()
  ├─ runPreOpeningHandActions()
  ├─ ReplayLibraryReorderer.reorderLibraries(game, replayLogPath)   ◄── reorder AFTER shuffle
  ├─ drawCards() / drawStartingHand()                                ◄── draws match replay
  └─ MulliganService.perform()
```

---

## 3. Core: Library Reordering (`ReplayLibraryReorderer`)

Shared by both GUI and CLI modes.

### How It Works

1. **Parse** `parseDrawOrder()` — reads all `DRAW` events from `"events"` (or legacy
   `"log_l1"`) and builds an ordered list of card names per player ID (`P1`, `P2`, …).

2. **Match players** — `P1` = `game.getPlayers().get(0)`, `P2` = index 1, etc.
   (matches `ReplayNotationExporter` convention).

3. **Reorder** `reorderLibrary()` per player:
   - For each card name in draw order, find the first matching card in the library
   - Move matched cards to the front in sequence
   - Append all remaining (unmatched) cards after

4. **Draw** — `drawCards()` pulls from position 0 (top), reproducing the original hand.

### Replay JSON — Relevant Fields

```json
{
  "events": [
    {
      "type": "DRAW",
      "data": {
        "card_name": "Sol Ring",
        "from": "P1:library",
        "to": "P1:hand"
      }
    }
  ]
}
```

| Field | Purpose |
|-------|---------|
| `type` | Must equal `"DRAW"` |
| `data.card_name` | Exact card name matched against the library |
| `data.from` | Player ID prefix (e.g. `"P1:library"` → player `"P1"`) |

### Edge Cases

| Scenario | Behaviour |
|----------|-----------|
| Duplicate names (e.g. 4× Lightning Bolt) | Each draw consumes the first remaining copy — order preserved |
| Card already in another zone | Skipped with `DEBUG` log — does not cause errors |
| Player has no DRAW events | Library left in shuffled order |
| Mid-game shuffle (fetch, tutor) | Predetermined order is lost from that point — only initial draws are guaranteed |

---

## 4. Replay JSON Log Files

### Location

Game logs are saved automatically after each game to:

```
%APPDATA%\Forge\games\gamelogs\
```

File naming: `replay_<GameType>_<YYYY-MM-DD>_<HH-mm-ss>.json`

### `replayed_at` Flag (added by GUI replay)

```json
{
  "meta": {
    "replayed_at": "2026-03-29T07:45:00Z"
  }
}
```

Files with this field are excluded from the GUI replay list. The flag is permanent
(written back to the source file) and survives Forge restarts.

---

## 5. Logging

All replay operations use SLF4J:

| Logger | Level | Message |
|--------|-------|---------|
| `ReplayLibraryReorderer` | `INFO` | Draw order parsed (player count, total draws) |
| `ReplayLibraryReorderer` | `INFO` | Library reorder complete per player |
| `ReplayLibraryReorderer` | `WARN` | No events array / no draw order found |
| `ReplayLibraryReorderer` | `DEBUG` | Card not found in library (skipped) |
| `ReplayLibraryReorderer` | `ERROR` | IOException loading replay file |
| `ReplayLogParser` | `INFO` | Replay parsed (player count, game type, turns, winner) |
| `ReplayLogParser` | `INFO` | Deck reconstructed per player (card count) |
| `ReplayLogParser` | `WARN` | Card name not found in database |
| `ReplayLogParser` | `INFO` | `markAsReplayed()` — file flagged with timestamp |
| `CSubmenuReplay` | `INFO` | Replay files found in log directory |



