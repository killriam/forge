
*Last updated: December 2025*
---
All detailed documentation has been moved to `temp-docs/` folder:
> **Fork Point:** `a6c5e79f24` (upstream/master)  
> **Branch:** `apply-analysis-patch` (9 commits)  
> **Total:** 81 files changed, ~22 000 lines added  
> **See also:** [FORK_CHANGES_SUMMARY.md](./FORK_CHANGES_SUMMARY.md) for the full reimplementation guide

- Replay notation specifications
- Implementation details
- Debug guides
- Test results
    <groupId>io.sentry</groupId>
    <artifactId>sentry-logback</artifactId>
    <version>7.14.0</version>
    <version>3.46.1</version>
### Root `pom.xml`
| `forge-game/.../ReplayJsonSerializer.java` | JSON writer |
| `forge-game/.../ReplayNotationExporter.java` | Export engine |
| `forge-game/.../model/ReplayLog.java` | Root replay object |
| `forge-game/.../model/ReplayMeta.java` | Game metadata |
| `forge-game/.../model/CardDefinition.java` | Card index |
### Replay Notation (11 files)
- **Level 1 (L1):** Chronological event log (lossless, authoritative)
- **Level 2 (L2):** Decision-focused snapshots for ML training
- `forge-game/src/main/java/forge/game/log/model/` - Data model classes
- `forge-game/src/main/java/forge/game/log/ReplayNotationExporter.java` - Export engine
### 2. MTG Replay Notation (JSON Format)
### 2. MTG Replay Notation (JSON Format) — v1.5.0

This document provides a consolidated overview of all custom modifications and features added to the Forge codebase.

- `forge-game/src/main/java/forge/game/log/model/` - Data model classes (9 model files)
- `forge-game/src/main/java/forge/game/log/ReplayNotationExporter.java` - Export engine (1634 lines)
- `forge-game/src/main/java/forge/game/log/ReplayJsonSerializer.java` - JSON serializer (723 lines)

- `mtg-replay-notation/` - Git submodule with canonical spec, JSON schema, and examples
### 1. Game Analytics & Database
**Purpose:** Track game results, deck statistics, and starting hand analysis
- **Level 1 (L1):** Chronological event log (lossless, authoritative) — key `"events"`
- **Level 2 (L2):** Decision-focused snapshots for ML training — key `"views_l2"`

**Format Version History:**

| Version | Key Additions |
|---------|---------------|
| 1.0.0 | Initial L1/L2 format |
| 1.1.0 | Object-to-card mapping, card_name in events, win_condition, deck identification |
| 1.2.0 | New events: GAME_START, PLAY_LAND, DRAW, DISCARD, MULLIGAN |
| 1.3.0 | `game_start` section (toss, mulligans), ACTIVE_PLAYER_CHANGE event |
| 1.4.0 | LEARNING_MARKER event, `learning_markers` array, `deck_link` in player meta |
| 1.5.0 | `events` key (was `log_l1`), `spec_version`, new events (ACTIVATE, TRIGGER, RESOLVE, DECLARE_ATTACKERS, DECLARE_BLOCKERS, COUNTERS), enriched `card_index`, `per_turn_summary`, `game_summary` |

**JSON Top-Level Structure (v1.5.0):**
```json
{
  "format": "mtg-replay",
  "version": "1.5.0",
  "spec_version": "1.5.0",
  "meta": { "game_id", "players": { "P1": { "name", "deck_name", "deck_hash", "is_ai", "starting_life" } }, "winner", "win_condition" },
  "seed": 1234567890,
  "game_start": { "toss_winner", "play_draw_choice", "starting_player", "mulligans": [] },
  "card_index": { "c1": { "name", "cost", "type", "oracle_id", "oracle_text", "power", "toughness", "subtypes" } },
  "initial_state": { "turn", "phase", "players", "zones", "objects" },
  "events": [ /* L1 event log */ ],
  "views_l2": [ /* L2 learning units */ ],
  "learning_markers": [ /* quick-nav index */ ],
  "per_turn_summary": [ /* per-turn KPIs per player */ ],
  "game_summary": { /* aggregated game-wide stats */ }
}
```
- `forge-game/src/main/java/forge/util/SQLiteConnection.java` - Database operations
- `forge-game/src/main/java/forge/game/GameAnalysis.java` - Game analysis results
- `forge-game/src/main/java/forge/game/player/DeckStats.java` - Deck statistics tracking

**Capabilities:**
- SQLite database for persistent storage
- Win rates, life delta, turn count per deck
- Mana curve analysis (available mana per turn)
- Starting hand statistics

---

**Event Types (20):**
- Player decisions: `CAST`, `ACTIVATE`, `PLAY_LAND`, `DRAW`, `DISCARD`, `MULLIGAN`
- Combat: `DECLARE_ATTACKERS`, `DECLARE_BLOCKERS`
- System: `MOVE`, `DAMAGE`, `LIFE`, `RESOURCES`, `COUNTERS`, `TRIGGER`, `RESOLVE`
- Meta: `GAME_START`, `PHASE_CHANGE`, `ACTIVE_PLAYER_CHANGE`, `LEARNING_MARKER`

**Model Classes:**
| Class | Purpose |
|-------|---------|
| `ReplayLog` | Root object + `LearningMarker` inner class |
| `ReplayMeta` | Game metadata + `PlayerMeta` inner class |
| `GameStartInfo` | Pre-game decisions (toss, mulligans) + `MulliganInfo` inner class |
| `CardDefinition` | Enriched card index (oracle text, P/T, subtypes) |
| `GameState` | Board state snapshot |
| `L1Event` | Chronological event |
| `L2Unit` | ML training unit |
| `GameSummary` | Aggregated game-wide stats + `PlayerGameStats` inner class |
| `TurnSummary` | Per-turn statistics + `PlayerTurnStats` inner class |

### 2. MTG Replay Notation (JSON Format)
**Purpose:** Machine-readable game replay format for AI training and analysis

**Key Files:**
- `forge-game/src/main/java/forge/game/log/model/` - Data model classes
- `forge-game/src/main/java/forge/game/log/ReplayNotationExporter.java` - Export engine
- `forge-game/src/main/java/forge/game/log/ReplayL2Generator.java` - Learning view generator

**Architecture:**
- **Level 1 (L1):** Chronological event log (lossless, authoritative)
- **Level 2 (L2):** Decision-focused snapshots for ML training

**Object ID Format:**
| Type | Format | Example |
|------|--------|---------|
| Card/Permanent | `c` + number | `c42` |
| Token | `t` + number | `t7` |
| Stack Object | `s` + number | `s1` |
| Player | `P` + number | `P1` |

**Time Marker Format:** `T<turn>.<phase>[:<priority_pass>]`
- Example: `T3.MP1:2` = Turn 3, Main Phase 1, Priority Pass 2

---

### 3. Text-Based Game Logging
**Purpose:** Human-readable game logs for debugging and analysis

**Key Files:**
- Auto-saves game logs after each simulated game
- `forge-gui/src/main/java/forge/game/GameLogSaver.java` - Log saving utilities

**Usage:**
```java
// Enable analysis logging
game.getLog().setLogLevel(GameLogEntryType.ANALYSIS);

// Save both formats
File[] logs = GameLogSaver.saveGameLogBothFormats(game);
```

---

### 4. Simulation Enhancements
**Purpose:** Extended simulation mode for deck testing

**Key Files:**
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java` - Simulation runner
### 6. ConcurrentModificationException Fixes
**Purpose:** Prevent crashes during concurrent game state access (simulation, AI)

**Key File:** `forge-game/src/main/java/forge/game/card/Card.java`

**Changes:** 4 defensive `ImmutableList.copyOf()` wraps in methods that iterate mutable collections:
- `getName(CardState)` — `changedCardNames.values()`
- `hasNonLegendaryCreatureNames()` — `changedCardNames.values()`
- `getManaCost()` — `changedCardManaCost.values()`
- `updateChangedText()` — `changedCardTraitsByText.values()`

---

### 7. AI Decision Logging
**Purpose:** Log AI decisions with alternatives for analysis

**Key File:** `forge-ai/src/main/java/forge/ai/AiDecisionLogger.java`

**Logs:** Spell choices, combat declarations, counterspell decisions

---


**Features:**
- Deck testing against sparring decks
- Automated game analysis
- Statistics collection

---

### 5. Commander Performance Optimization
**Purpose:** Fix GUI freeze when loading Commander decks

**Problem:** Loading Commander decks blocked the EDT (Event Dispatch Thread) for minutes.

**Solution:** Asynchronous deck loading in background thread.

**Key File:** `forge-gui-desktop/src/main/java/forge/deckchooser/FDeckChooser.java`

### Replay Notation (16 files)
- `updateCustom()` - Now uses `FThreads.invokeInBackgroundThread()`
- `refreshDecksList()` - Triggers async loading
| `forge-game/.../model/ReplayLog.java` | Root replay object (195 lines, incl. LearningMarker) |
| `forge-game/.../model/ReplayMeta.java` | Game metadata + PlayerMeta (93 lines) |
| `forge-game/.../model/CardDefinition.java` | Enriched card index (42 lines) |
## 📁 New Files Added

### Core Classes
| `forge-game/.../model/GameStartInfo.java` | **NEW** Pre-game decisions (91 lines) |
| `forge-game/.../model/GameSummary.java` | **NEW** Aggregated game stats (112 lines) |
| `forge-game/.../model/TurnSummary.java` | **NEW** Per-turn stats (93 lines) |
| `forge-game/.../ReplayNotationExporter.java` | Export engine (1634 lines) |
| `forge-game/.../ReplayJsonSerializer.java` | JSON writer (723 lines) |
|------|---------|
| `forge-core/.../CardForFitting.java` | Card fitting data class |
| `forge-game/.../ReplayNotationSimulation.java` | Simulation test (468 lines) |
| `forge-game/.../GameReplaySimulation.java` | Simulated game for testing |
| `forge-game/src/test/.../ReplayNotationSimulationTest.java` | Unit tests (404 lines) |
| `forge-core/.../FittingSection.java` | Deck section fitting |

### Game Analysis
| File | Purpose |
|------|---------|
### Root `pom.xml` — `<dependencyManagement>`
| `forge-game/.../DeckStats.java` | Deck statistics |
<!-- JSON serialization for replay notation -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>

<!-- SQLite for game analytics -->
| `forge-game/.../startingHandStats.java` | Starting hand stats |
| `forge-game/.../SQLiteConnection.java` | SQLite operations |

    <version>3.36.0.3</version>
| File | Purpose |
|------|---------|
| `forge-game/.../model/ReplayLog.java` | Root replay object |
| `forge-game/.../model/ReplayMeta.java` | Game metadata |
```xml
<!-- Unit testing for Replay Notation -->
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>
</dependency>

<!-- SQLite for game analytics -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.36.0.3</version>
</dependency>
```

### `forge-gui/pom.xml`
| `forge-game/.../model/CardDefinition.java` | Card index |
| `forge-game/.../model/GameState.java` | State snapshot |
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
| `forge-game/.../ReplayL2Generator.java` | L2 generator |
| `forge-game/.../ReplayNotationValidator.java` | Validator |
| `forge-game/.../ReplayJsonSerializer.java` | JSON writer |
### Git Submodule
```ini
[submodule "mtg-replay-notation"]
    path = mtg-replay-notation
    url = <repository-url>
```


---

## 🔧 Dependencies Added
| Document | Location | Purpose |
|----------|----------|---------|
| Full Change Summary | `FORK_CHANGES_SUMMARY.md` | Complete file inventory, dependency map, reimplementation guide |
| AI Architecture Analysis | `AI_FEATURES_ANALYSIS.md` | Deep analysis of Forge AI (1389 lines) |
| Replay Format Enhancement Plan | `docs/Development/REPLAY_FORMAT_ENHANCEMENT_IMPLEMENTATION.md` | Implementation plan for format limitations |
| Replay Notation Specification | `mtg-replay-notation/spec/` | Canonical format spec + JSON schema |
| Development Notes | `temp-docs/` (59 files) | Implementation notes, debug guides, test results |
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.46.1</version>
</dependency>
```

### `forge-game/pom.xml`
```xml
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-logback</artifactId>
    <version>7.14.0</version>
</dependency>
```

---

## 8. Interactive GUI Replay Mode *(March 2026)*

Allows a human player to replay any previously saved game from the Home screen.

**Entry point:** Home → **Replay Mode** → **Replay Game**

| What it does | How |
|---|---|
| Lists available replays | Scans `%APPDATA%\Forge\games\gamelogs\*.json` |
| Shows replay details | Players, decks, turns, winner in info panel |
| Starts interactive game | You play as P1, others become AI; same library order |
| Prevents double-replay | Writes `meta.replayed_at` to the JSON file; entry disappears |

**New files:**
- `forge-gui/.../game/ReplayLogParser.java` — JSON parser + deck reconstructor
- `forge-gui-desktop/.../home/replay/VSubmenuReplay.java` — View
- `forge-gui-desktop/.../home/replay/CSubmenuReplay.java` — Controller

**Modified files:** `EMenuGroup`, `EDocID`, `VHomeUI`, `ForgePreferences`, `en-US.properties`

**Full docs:** [`docs/FEATURE_GAME_REPLAY.md`](docs/FEATURE_GAME_REPLAY.md)

---

## 📚 Related Documentation

- [`docs/FEATURE_GAME_REPLAY.md`](docs/FEATURE_GAME_REPLAY.md) — Full Game Replay architecture (GUI + CLI)
- [`FORK_CHANGES_SUMMARY.md`](FORK_CHANGES_SUMMARY.md) — Complete file inventory and reimplementation guide
- [`AI_FEATURES_ANALYSIS.md`](AI_FEATURES_ANALYSIS.md) — Forge AI architecture analysis
- `mtg-replay-notation/` — Git submodule: canonical replay format spec and JSON schema

### Analyze Replay
```bash
python analyze_replay_log.py path/to/replay.json
```

### Save Game Replay
```java
// Both text and JSON formats
File[] logs = GameLogSaver.saveGameLogBothFormats(game);

// JSON only
File jsonLog = GameLogSaver.saveGameLogReplayNotation(game, true);
```

### Run Simulation
```bash
java -jar forge-gui-desktop-*.jar sim -d deck1.dck deck2.dck -n 100
```

### Replay a previous game (CLI)
```bash
java -jar forge-gui-desktop-*.jar sim -d deck1.dck deck2.dck -n 1 -r path/to/replay.json
```

---

*Last updated: March 2026*


