# Forge Fork – Complete Change Summary & Reimplementation Guide

> **Purpose:** This document catalogs every change made since forking from the upstream Forge repository.  
> It is intended as a blueprint to reproduce all custom features on a fresh upstream checkout.  
> **Fork Point:** Commit `a6c5e79f24` (upstream/master — "Update captain_rex_nebula.txt")  
> **Branch:** `apply-analysis-patch`  
> **Total:** 71 files changed, ~18 650 lines added, ~55 lines removed

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)  
2. [Complete File Inventory](#2-complete-file-inventory)  
3. [Feature Details](#3-feature-details)  
   - 3.1 [Game Analytics & SQLite Database](#31-game-analytics--sqlite-database)  
   - 3.2 [MTG Replay Notation (JSON)](#32-mtg-replay-notation-json)  
   - 3.3 [ANALYSIS Log Level & Game Logging](#33-analysis-log-level--game-logging)  
   - 3.4 [AI Decision Logging](#34-ai-decision-logging)  
   - 3.5 [Simulation Enhancements](#35-simulation-enhancements)  
   - 3.6 [Commander / Lobby UX Improvements](#36-commander--lobby-ux-improvements)  
   - 3.7 [Deck Infrastructure Additions](#37-deck-infrastructure-additions)  
   - 3.8 [Minor Bug Fixes & Tweaks](#38-minor-bug-fixes--tweaks)  
   - 3.9 [Dependency Changes (POM)](#39-dependency-changes-pom)  
   - 3.10 [Utility Scripts & Tooling](#310-utility-scripts--tooling)  
   - 3.11 [Documentation & Metadata](#311-documentation--metadata)  
4. [Dependency Map Between Features](#4-dependency-map-between-features)  
5. [Reimplementation Strategy](#5-reimplementation-strategy)  

---

## 1. Executive Summary

This fork adds seven main feature areas to Forge:

| # | Feature | New Files | Modified Files | Complexity |
|---|---------|-----------|----------------|------------|
| 1 | Game Analytics & SQLite DB | 5 | 3 | Medium |
| 2 | MTG Replay Notation (JSON) | 13 | 4 | High |
| 3 | ANALYSIS Log Level & Text Logging | 2 | 5 | Medium |
| 4 | AI Decision Logging | 1 | 2 | Low |
| 5 | Simulation Enhancements | 0 | 1 | Medium |
| 6 | Commander / Lobby UX | 0 | 8 | Medium |
| 7 | Deck Infrastructure | 4 | 3 | Low |

Plus: 2 dependency additions (SQLite-JDBC, Gson), 8 utility scripts, bug fixes, and documentation.

---

## 2. Complete File Inventory

### 2.1 New Files (Added)

| File | Module | Feature | Lines |
|------|--------|---------|-------|
| `forge-game/.../util/SQLiteConnection.java` | forge-game | Analytics DB | 283 |
| `forge-game/.../game/GameAnalysis.java` | forge-game | Analytics | 66 |
| `forge-game/.../player/DeckStats.java` | forge-game | Analytics | 47 |
| `forge-game/.../player/DeckWins.java` | forge-game | Analytics | 6 |
| `forge-game/.../game/startingHandStats.java` | forge-game | Analytics | 43 |
| `forge-game/.../log/ReplayNotationExporter.java` | forge-game | Replay JSON | 687 |
| `forge-game/.../log/ReplayL2Generator.java` | forge-game | Replay JSON | 305 |
| `forge-game/.../log/ReplayNotationValidator.java` | forge-game | Replay JSON | 377 |
| `forge-game/.../log/ReplayJsonSerializer.java` | forge-game | Replay JSON | 207 |
| `forge-game/.../log/ReplayNotationSimulation.java` | forge-game | Replay JSON | 411 |
| `forge-game/.../log/GameReplaySimulation.java` | forge-game | Replay JSON | 435 |
| `forge-game/.../log/model/ReplayLog.java` | forge-game | Replay JSON | 63 |
| `forge-game/.../log/model/ReplayMeta.java` | forge-game | Replay JSON | 58 |
| `forge-game/.../log/model/CardDefinition.java` | forge-game | Replay JSON | 25 |
| `forge-game/.../log/model/GameState.java` | forge-game | Replay JSON | 136 |
| `forge-game/.../log/model/L1Event.java` | forge-game | Replay JSON | 48 |
| `forge-game/.../log/model/L2Unit.java` | forge-game | Replay JSON | 161 |
| `forge-game/src/test/.../ReplayNotationSimulationTest.java` | forge-game | Replay JSON (Test) | 404 |
| `forge-ai/.../ai/AiDecisionLogger.java` | forge-ai | AI Logging | 345 |
| `forge-gui/.../game/GameLogSaver.java` | forge-gui | Text Logging | 297 |
| `forge-gui/.../game/GameLogSaverTest.java` | forge-gui | Text Logging (Test) | 40 |
| `forge-gui/.../puzzle/ReplayToPuzzleConverter.java` | forge-gui | Replay→Puzzle | 561 |
| `forge-gui/.../puzzle/ReplayToPuzzleLauncher.java` | forge-gui | Replay→Puzzle | 0 |
| `forge-gui/res/puzzle/REPLAY_TEST.pzl` | forge-gui | Replay→Puzzle | 20 |
| `forge-gui/res/puzzle/example_replay.json` | forge-gui | Replay→Puzzle | 71 |
| `forge-core/.../deck/CardForFitting.java` | forge-core | Deck Infra | 14 |
| `forge-core/.../deck/DeckIdea.java` | forge-core | Deck Infra | 13 |
| `forge-core/.../deck/FittingSection.java` | forge-core | Deck Infra | 17 |
| `AI_FEATURES_ANALYSIS.md` | root | Documentation | 1389 |
| `FORGE_CUSTOM_FEATURES.md` | root | Documentation | 184 |
| `START_FORGE_DEBUG.bat` | root | Script | 95 |
| `START_FORGE_DEBUG.ps1` | root | Script | 192 |
| `analyze_replay_log.py` | root | Script | 172 |
| `monitor_logs.ps1` | root | Script | 64 |
| `run_simulation.ps1` | root | Script | 126 |
| `test_commander_debug.ps1` | root | Script | 68 |
| `replay_to_puzzle.bat` | root | Script | 0 |
| `replay_to_puzzle.ps1` | root | Script | 0 |
| `forge-analysis-and-mana-tracking.patch` | root | Patch backup | binary |
| `forge-analysis-and-mana-tracking.patch.utf8` | root | Patch backup | 4935 |
| `forge-analysis-and-mana-tracking.utf8.patch` | root | Patch backup | 4935 |

### 2.2 Modified Files

| File | Module | Feature | +/- Lines |
|------|--------|---------|-----------|
| `.gitignore` | root | Metadata | +3 |
| `pom.xml` | root | Dependencies | +10 |
| `forge-game/pom.xml` | forge-game | Dependencies | +5 |
| `forge-gui/pom.xml` | forge-gui | Dependencies | +4 |
| `forge-game/.../GameLogEntryType.java` | forge-game | Log Level | +4/-2 |
| `forge-game/.../GameLog.java` | forge-game | Replay integration | +18 |
| `forge-game/.../GameLogFormatter.java` | forge-game | Replay + Analysis | +306/-2 |
| `forge-game/.../GameSnapshot.java` | forge-game | Bugfix | +31/-1 |
| `forge-game/.../Match.java` | forge-game | Replay auto-enable | +25/-1 |
| `forge-game/.../phase/PhaseHandler.java` | forge-game | Mana tracking | +14 |
| `forge-game/.../player/Player.java` | forge-game | Analytics | +77 |
| `forge-ai/.../ai/AiAttackController.java` | forge-ai | AI Logging | +23 |
| `forge-ai/.../ai/AiController.java` | forge-ai | AI Logging | +36/-1 |
| `forge-gui-desktop/.../FDeckChooser.java` | forge-gui-desktop | Commander perf | +355/-82 |
| `forge-gui-desktop/.../CLobby.java` | forge-gui-desktop | Variant saving | +13 |
| `forge-gui-desktop/.../VLobby.java` | forge-gui-desktop | Variant saving | +28 |
| `forge-gui-desktop/.../TargetingOverlay.java` | forge-gui-desktop | Whitespace fix | +1/-1 |
| `forge-gui-desktop/.../ViewWinLose.java` | forge-gui-desktop | Auto-save log | +7 |
| `forge-gui-desktop/.../CDock.java` | forge-gui-desktop | Arc default | +2/-1 |
| `forge-gui-desktop/.../SimulateMatch.java` | forge-gui-desktop | Simulation ext. | +341/-4 |
| `forge-gui-mobile/.../FDeckChooser.java` | forge-gui-mobile | Deck state fix | +17/-13 |
| `forge-gui-mobile/.../LobbyScreen.java` | forge-gui-mobile | Commander default | +5 |
| `forge-gui-mobile/.../PlayerPanel.java` | forge-gui-mobile | Save deck state | +2 |
| `forge-gui-mobile/.../ViewWinLose.java` | forge-gui-mobile | Auto-save log | +7 |
| `forge-gui/.../ColorDeckGenerator.java` | forge-gui | Color fix | +22 |
| `forge-gui/.../CommanderDeckGenerator.java` | forge-gui | Color fix | +7 |
| `forge-gui/.../DeckImportController.java` | forge-gui | Util method | +35 |
| `forge-gui/.../ColumnDef.java` | forge-gui | NullPointer fix | +4/-1 |
| `forge-gui/.../ItemManagerModel.java` | forge-gui | Whitespace | +1 |
| `forge-gui/.../ForgeConstants.java` | forge-gui | Log dir constant | +1 |

---

## 3. Feature Details

### 3.1 Game Analytics & SQLite Database

**Purpose:** Track game results, deck win rates, mana curves, and starting hand quality in a persistent SQLite database.

**New Files:**
- `forge-game/.../util/SQLiteConnection.java` — JDBC wrapper: create tables, insert/update deck stats, card occurrences, game sets
- `forge-game/.../game/GameAnalysis.java` — Value object holding winner name, turn count, life delta, starting hand, mana score
- `forge-game/.../player/DeckStats.java` — Per-deck statistics (wins, average turn, average life delta)
- `forge-game/.../player/DeckWins.java` — Simple win record DTO
- `forge-game/.../game/startingHandStats.java` — Starting hand composition statistics

**Modified Files:**
- `forge-game/.../player/Player.java` — Added fields: `cardsInStartingHand`, `manacurveData[8]`; methods: `setCardsInStartingHand()`, `getCardsInStartingHand()`, `countManaLandRampsIn()`, `getCardswithManaAbilities()`, `listManaCreatableIn()`, `getManacurveData()`, `setManacurveData()`
- `forge-game/.../phase/PhaseHandler.java` — At end-of-turn, counts lands/mana producers and calls `player.setManacurveData()`
- `forge-gui-desktop/.../SimulateMatch.java` — `simulationSeries()`, `simulateSingleMatchWithAnalysis()`, DB insert methods

**Integration Points:**
- `Player.java` (around line 172 and 4083+) — new fields and methods at end of class
- `PhaseHandler.java` (CLEANUP phase case, around line 368) — 14 lines added after `game.getEndOfTurn().executeAt()`

---

### 3.2 MTG Replay Notation (JSON)

**Purpose:** Machine-readable game replay format for AI training and analysis. Two-level architecture: L1 (chronological events) and L2 (decision-focused snapshots).

**New Files (13):**
- `forge-game/.../log/model/ReplayLog.java` — Root JSON object
- `forge-game/.../log/model/ReplayMeta.java` — Game metadata (players, decks, format)
- `forge-game/.../log/model/CardDefinition.java` — Card index entry
- `forge-game/.../log/model/GameState.java` — Board state snapshot
- `forge-game/.../log/model/L1Event.java` — Chronological event entry
- `forge-game/.../log/model/L2Unit.java` — ML training unit
- `forge-game/.../log/ReplayNotationExporter.java` — Main export engine (687 lines) — hooks into GameLogFormatter events
- `forge-game/.../log/ReplayL2Generator.java` — Generates L2 decision-focused data
- `forge-game/.../log/ReplayNotationValidator.java` — Validates JSON output
- `forge-game/.../log/ReplayJsonSerializer.java` — JSON writer using Gson
- `forge-game/.../log/ReplayNotationSimulation.java` — Standalone simulation test
- `forge-game/.../log/GameReplaySimulation.java` — Simulated game for testing
- `forge-game/src/test/.../ReplayNotationSimulationTest.java` — Unit tests

**Modified Files:**
- `forge-game/.../GameLog.java` — Added `enableReplayNotation(exporter)` and `getReplayExporter()`
- `forge-game/.../GameLogFormatter.java` — Major changes: tracks board state, zone changes, phase/turn/priority for time markers; delegates events to `ReplayNotationExporter` (logCast, logPhaseChange, logDamage, logZoneChange); new visitors for `GameEventCardChangeZone`, `GameEventTurnEnded`; mana calculation
- `forge-game/.../Match.java` — `createGame()` auto-enables ReplayNotation via reflection call to `GameLogSaver.enableReplayNotation(game)`

**Object ID Format:** `c<n>` (cards), `t<n>` (tokens), `s<n>` (stack), `P<n>` (players)  
**Time Marker:** `T<turn>.<phase>[:<priority>]` e.g. `T3.MP1:2`

---

### 3.3 ANALYSIS Log Level & Game Logging

**Purpose:** New `ANALYSIS` log level for detailed zone changes, board state deltas, and land/mana tracking. Auto-save game logs to files.

**New Files:**
- `forge-gui/.../game/GameLogSaver.java` — Static utility: `saveGameLog()`, `saveGameLogBothFormats()`, `saveGameLogReplayNotation()`, `saveGameLogAndGetPath()`, `enableReplayNotation()`
- `forge-gui/.../game/GameLogSaverTest.java` — Basic test

**Modified Files:**
- `forge-game/.../GameLogEntryType.java` — Added `ANALYSIS("Analysis")` and `AI_DECISION("AI Decision")` enum values
- `forge-game/.../GameLogFormatter.java` — Zone change tracking (`visit(GameEventCardChangeZone)`), turn summary (`visit(GameEventTurnEnded)`), board state delta, land count, available mana calculation
- `forge-gui/.../ForgeConstants.java` — Added `GAME_LOG_DIR = USER_GAMES_DIR + "gamelogs" + PATH_SEPARATOR`
- `forge-gui-desktop/.../ViewWinLose.java` — Auto-save on game end: `GameLogSaver.saveGameLogAndGetPath(game)`
- `forge-gui-mobile/.../ViewWinLose.java` — Same auto-save on mobile

---

### 3.4 AI Decision Logging

**Purpose:** Log AI decisions (spell choices, combat, counterspells) with alternatives for analysis.

**New Files:**
- `forge-ai/.../AiDecisionLogger.java` — Static logger: `logDecision()`, `logDecisionWithAlternatives()`, `logCombatDecision()`

**Modified Files:**
- `forge-ai/.../AiController.java` — In `chooseCounterSpell()`: logs chosen counterspell. In main spell picker loop: collects up to 4 playable alternatives before returning the best one, logs via `AiDecisionLogger.logDecisionWithAlternatives()`
- `forge-ai/.../AiAttackController.java` — After combat declaration: logs attacking creatures, targets, and aggression level

---

### 3.5 Simulation Enhancements

**Purpose:** Extended deck testing mode (`-xd` flag) with automated game series, statistics collection, and DB storage.

**Modified Files:**
- `forge-gui-desktop/.../SimulateMatch.java` — Major additions:
  - `-replay` mode for standalone replay notation test
  - `-xd` mode for extended deck testing series
  - `simulationSeries()` — reads deck file, parses via `DeckImportController`, runs N games against sparring deck
  - `simulateSingleMatchWithAnalysis()` — runs game, captures `GameAnalysis` with starting hand
  - `InsertStartingHandStats()` — records card occurrences in DB
  - `displayDeckStats()` — prints win rates
  - Auto-saves game logs after each simulated game

---

### 3.6 Commander / Lobby UX Improvements

**Purpose:** Fix EDT freeze when loading Commander decks; persist game variant selection; default to Commander.

**Modified Files:**

#### Desktop:
- `forge-gui-desktop/.../FDeckChooser.java` — **Major rewrite of `updateCustom()`**: deck loading moved to `FThreads.invokeInBackgroundThread()`, UI update via `FThreads.invokeInEdtLater()`. Added `AtomicBoolean loadingDecks` guard against concurrent loads. Added extensive debug logging. `saveState()` returns silently if not initialized (was `NullPointerException`). `restoreSavedState()` handles partial name matching.
- `forge-gui-desktop/.../CLobby.java` — On init: loads saved game variants from preferences; defaults to Commander if none saved
- `forge-gui-desktop/.../VLobby.java` — Added `applyVariant()`, `saveVariants()` methods; checkbox listener now calls `saveVariants()`
- `forge-gui-desktop/.../CDock.java` — Targeting overlay arc default changed from `0` (OFF) to `2` (ON)

#### Mobile:
- `forge-gui-mobile/.../FDeckChooser.java` — `saveState()` returns silently instead of throwing NPE. `getState()` null-safe. `restoreSavedState()` selects index 0 instead of re-refreshing
- `forge-gui-mobile/.../LobbyScreen.java` — Defaults to Commander if no saved variants
- `forge-gui-mobile/.../PlayerPanel.java` — Calls `deckChooser.saveState()` on deck selection change

---

### 3.7 Deck Infrastructure Additions

**Purpose:** Supporting classes for future deck fitting/idea features and utility methods.

**New Files:**
- `forge-core/.../deck/CardForFitting.java` — Card DTO for fitting algorithm (14 lines)
- `forge-core/.../deck/DeckIdea.java` — Deck template/idea class (13 lines)
- `forge-core/.../deck/FittingSection.java` — Deck section fitting (17 lines)

**Modified Files:**
- `forge-gui/.../DeckImportController.java` — Added static `createDeckOutof(tokens, includeBnR)` method for programmatic deck creation
- `forge-gui/.../ColorDeckGenerator.java` — Added `getColor()` override returning `ColorSet` based on deck name
- `forge-gui/.../CommanderDeckGenerator.java` — Added `getColor()` override returning commander's color identity

---

### 3.8 Minor Bug Fixes & Tweaks

| File | Fix |
|------|-----|
| `forge-game/.../GameSnapshot.java` | Sorted cards by zone position before restoration to prevent `IndexOutOfBoundsException`. Clamped zone position to valid range. |
| `forge-gui/.../ColumnDef.java` | Null check on `toDeckColor()` return → returns `0` weight instead of NPE |
| `forge-gui-desktop/.../TargetingOverlay.java` | Whitespace fix (indentation correction) |
| `forge-gui/.../ItemManagerModel.java` | Blank line added to file header |

---

### 3.9 Dependency Changes (POM)

#### Root `pom.xml` — `<dependencyManagement>`
```xml
<!-- NEW: JSON serialization for replay notation -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>

<!-- NEW: SQLite for game analytics -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.36.0.3</version>
</dependency>
```

#### `forge-game/pom.xml` — `<dependencies>`
```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.36.0.3</version>
</dependency>
```

#### `forge-gui/pom.xml` — `<dependencies>`
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
</dependency>
```

---

### 3.10 Utility Scripts & Tooling

| File | Purpose |
|------|---------|
| `START_FORGE_DEBUG.ps1` / `.bat` | Launch Forge with debug JVM options |
| `run_simulation.ps1` | Run simulation series from command line |
| `test_commander_debug.ps1` | Commander deck loading test |
| `monitor_logs.ps1` | Tail game log files |
| `analyze_replay_log.py` | Python script to parse/analyze JSON replay logs |
| `replay_to_puzzle.ps1` / `.bat` | Convert replay JSON to puzzle format |

---

### 3.11 Documentation & Metadata

| File | Purpose |
|------|---------|
| `AI_FEATURES_ANALYSIS.md` | Deep analysis of Forge AI architecture (1389 lines) |
| `FORGE_CUSTOM_FEATURES.md` | Custom features overview |
| `temp-docs/` (59 files) | Detailed implementation notes, debug guides, test results |
| `.gitignore` | Added `temp-docs/` |
| `forge-analysis-and-mana-tracking.patch*` | Backup of original patch files |
| `forge-gui/res/puzzle/REPLAY_TEST.pzl` | Test puzzle for replay notation |
| `forge-gui/res/puzzle/example_replay.json` | Example replay JSON |

---

## 4. Dependency Map Between Features

```
┌─────────────────────────────────────────────────────────────┐
│                    POM Dependencies                          │
│              (Gson 2.10.1, SQLite-JDBC 3.36)                │
└──────────┬──────────────────────────────────┬────────────────┘
           │                                  │
           ▼                                  ▼
┌──────────────────────┐           ┌──────────────────────────┐
│ GameLogEntryType     │           │ SQLiteConnection.java    │
│ (ANALYSIS, AI_DEC.)  │           │ (standalone)             │
└──────────┬───────────┘           └──────────┬───────────────┘
           │                                  │
           ▼                                  ▼
┌──────────────────────┐           ┌──────────────────────────┐
│ GameLogFormatter     │           │ GameAnalysis, DeckStats   │
│ (board state, zone   │           │ startingHandStats         │
│  changes, mana calc) │           └──────────┬───────────────┘
└──────────┬───────────┘                      │
           │                                  │
     ┌─────┴──────┐                           │
     ▼            ▼                           ▼
┌──────────┐ ┌──────────────────┐  ┌──────────────────────────┐
│ GameLog  │ │ ReplayNotation   │  │ SimulateMatch.java       │
│ Saver    │ │ Exporter+Models  │  │ (extended deck testing)  │
└──────────┘ └──────────────────┘  └──────────────────────────┘
     │            │
     ▼            ▼
┌──────────────────────────────────────┐
│ Match.java (auto-enable via reflect) │
│ ViewWinLose (auto-save)              │
└──────────────────────────────────────┘

┌──────────────────────────┐    ┌─────────────────────────────┐
│ AiDecisionLogger (new)   │    │ Commander/Lobby UX (indep.) │
│ AiController (modified)  │    │ FDeckChooser, CLobby,       │
│ AiAttackController (mod) │    │ VLobby, LobbyScreen, etc.   │
└──────────────────────────┘    └─────────────────────────────┘

┌──────────────────────────┐    ┌─────────────────────────────┐
│ Player.java (analytics)  │    │ Deck Infrastructure (indep.)│
│ PhaseHandler (mana track)│    │ CardForFitting, DeckIdea,   │
│                          │    │ FittingSection              │
└──────────────────────────┘    └─────────────────────────────┘
```

**Key Dependencies:**
- Replay Notation requires: Gson dependency, GameLogEntryType changes, GameLogFormatter changes, GameLog changes
- Game Analytics requires: SQLite-JDBC dependency, Player.java changes, PhaseHandler changes
- SimulateMatch extended mode requires: both Analytics + Replay + DeckImportController
- GameLogSaver requires: ForgeConstants.GAME_LOG_DIR, Replay models
- Match.java auto-enable requires: GameLogSaver (loaded via reflection)
- Commander/Lobby UX is **independent** — can be applied separately
- Deck Infrastructure is **independent**
- AI Decision Logging is **independent** (only depends on GameLogEntryType.AI_DECISION)
- Bug fixes (GameSnapshot, ColumnDef) are **independent**

---

## 5. Reimplementation Strategy

### 5.1 Recommended Order

Apply changes in this order to resolve dependencies correctly:

| Step | Feature | Risk of Merge Conflict | Method |
|------|---------|----------------------|--------|
| **1** | POM Dependencies | 🟢 Low | Manual edit — add Gson + SQLite to `<dependencyManagement>` and module POMs |
| **2** | `.gitignore` + `ForgeConstants.java` | 🟢 Low | Manual edit — 1 line each |
| **3** | Bug Fixes (GameSnapshot, ColumnDef) | 🟢 Low | Cherry-pick or manual — isolated changes |
| **4** | `GameLogEntryType.java` | 🟢 Low | Manual — add 2 enum values before semicolon |
| **5** | Deck Infrastructure (CardForFitting, DeckIdea, FittingSection) | 🟢 Low | Copy new files — no dependencies |
| **6** | Player.java analytics fields | 🟡 Medium | Manual patch — append fields + methods at end of class |
| **7** | PhaseHandler.java mana tracking | 🟡 Medium | Manual — 14 lines after `game.getEndOfTurn().executeAt()` in CLEANUP case |
| **8** | GameLogFormatter.java (ANALYSIS + Replay) | 🔴 High | Manual — heavily modified file, likely upstream changes |
| **9** | GameLog.java (replay methods) | 🟢 Low | Manual — append 2 methods at end |
| **10** | Replay Notation models + engine (13 new files) | 🟢 Low | Copy entire `forge-game/.../log/` package |
| **11** | GameLogSaver + ForgeConstants | 🟢 Low | Copy new file + 1-line edit |
| **12** | Match.java auto-enable | 🟡 Medium | Manual — wrap `new Game()` in `createGame()` |
| **13** | AI Decision Logger | 🟢 Low | Copy AiDecisionLogger, then patch AiController + AiAttackController |
| **14** | SQLiteConnection + Analytics classes | 🟢 Low | Copy 5 new files |
| **15** | SimulateMatch.java extensions | 🔴 High | Manual — large diff, interleaved with existing code |
| **16** | Commander/Lobby UX (Desktop) | 🔴 High | FDeckChooser heavily rewritten — may need fresh implementation |
| **17** | Commander/Lobby UX (Mobile) | 🟡 Medium | Smaller changes, but mobile code may have diverged |
| **18** | ViewWinLose auto-save (Desktop + Mobile) | 🟢 Low | 6 lines each |
| **19** | Deck generators (ColorDeckGenerator, CommanderDeckGenerator) | 🟢 Low | Add `getColor()` methods |
| **20** | DeckImportController utility method | 🟢 Low | Append static method |
| **21** | CDock arc default | 🟢 Low | Change one `0` to `2` |
| **22** | Scripts & Documentation | 🟢 Low | Copy files |

### 5.2 Per-Feature Reimplementation Notes

#### 5.2.1 POM Dependencies (Step 1)
**Method:** Manual edit  
**What to do:**
1. Open root `pom.xml`, find `<dependencyManagement><dependencies>`, add Gson `2.10.1` and SQLite-JDBC `3.36.0.3`
2. Open `forge-game/pom.xml`, add SQLite-JDBC `3.36.0.3` to `<dependencies>`
3. Open `forge-gui/pom.xml`, add Gson (version-managed) to `<dependencies>`

#### 5.2.2 GameLogEntryType (Step 4)
**Method:** Manual edit  
**What to do:** In enum, change `PHASE("Phase");` to `PHASE("Phase"), ANALYSIS("Analysis"), AI_DECISION("AI Decision");`

#### 5.2.3 GameLogFormatter (Step 8) ⚠️ HIGH RISK
**Method:** Manual patch — this is the most complex single file change  
**What to do:**
1. Add imports: `Map`, `ArrayList`, `List`, `ReplayNotationExporter`, `Zone`
2. Add fields: `turnStartBoardState`, `turnZoneChanges`, `replayExporter`, `currentTurn`, `currentPhase`, `priorityCounter`
3. Add methods: `setReplayExporter()`, `getReplayExporter()`, `generateTimeMarker()`
4. Modify `visit(GameEventGameOutcome)` — add replay exporter call
5. Modify `visit(GameEventSpellResolved)` — add ANALYSIS log for resolving spells
6. Modify `visit(GameEventSpellAbilityCast)` — add replay JSON logging
7. Modify `visit(GameEventTurnPhase)` — add turn/phase tracking, mana/land logging after untap
8. Modify `visit(GameEventCardDamaged)` — add replay JSON logging
9. Modify `visit(GameEventTurnBegan)` — capture board state
10. Modify `visit(GameEventPlayerDamaged)` — add replay JSON logging
11. Add new visitors: `visit(GameEventCardChangeZone)`, `visit(GameEventTurnEnded)`
12. Add helper methods: `captureBoardState()`, `generateBoardStateDelta()`, `countLands()`, `calculateAvailableMana()`

**Conflict Risk:** This file is core game infrastructure. Upstream likely modifies it regularly. Plan for manual merge.

#### 5.2.4 FDeckChooser Desktop (Step 16) ⚠️ HIGH RISK
**Method:** Fresh implementation recommended  
**Core change:** Move `updateCustom()` body into `FThreads.invokeInBackgroundThread()`, update UI via `FThreads.invokeInEdtLater()`. Add `AtomicBoolean loadingDecks` guard. Consider **removing** all the `System.out.println` debug logging for production.

**Key pattern:**
```java
private final AtomicBoolean loadingDecks = new AtomicBoolean(false);

private void updateCustom() {
    if (!loadingDecks.compareAndSet(false, true)) return;
    DeckFormat deckFormat = lstDecks.getGameType().getDeckFormat();
    FThreads.invokeInBackgroundThread(() -> {
        try {
            // load decks based on format (same switch as original)
            final Iterable<DeckProxy> decks = ...;
            final ItemManagerConfig config = ...;
            FThreads.invokeInEdtLater(() -> {
                updateDecks(decks, config);
                loadingDecks.set(false);
            });
        } catch (Exception e) {
            FThreads.invokeInEdtLater(() -> loadingDecks.set(false));
        }
    });
}
```

#### 5.2.5 SimulateMatch (Step 15) ⚠️ HIGH RISK
**Method:** Manual patch  
**Key additions:**
- `-replay` argument handling at start of `simulate()`
- `-xd` argument handling with `simulationSeries()` and related private methods
- Auto-save game log after each game in `simulateSingleMatch()`

#### 5.2.6 Player.java Analytics (Step 6)
**Method:** Manual — append at end of class  
**What to add:**
- 3 new fields near line 172: `cardsInStartingHand`, `manacurveDataturnCount`, `manacurveData`
- ~72 lines of methods at end of class (getters/setters for starting hand, mana counting, mana listing)

### 5.3 Clean-Up Recommendations for Reimplementation

When reimplementing, consider these improvements:

1. **Remove debug logging from FDeckChooser** — The `System.out.println("[DECK LOADING DEBUG]...")` statements were for debugging during development. Remove them for production.

2. **Consolidate patch files** — The `forge-analysis-and-mana-tracking.patch*` files in root are obsolete backup artifacts. Don't copy them.

3. **Review Match.java reflection** — The reflection-based auto-enable in `Match.createGame()` is fragile. Consider a proper module dependency or service-loader pattern.

4. **Consider SLF4J for AI logging** — `AiDecisionLogger` uses direct `System.out.println`. Consider using Forge's logging framework.

5. **Consolidate temp-docs** — The 59 files in `temp-docs/` are development artifacts. Keep only this summary and `FORGE_CUSTOM_FEATURES.md`.

6. **Duplicate code in SimulateMatch** — The `-xd` handler is duplicated (appears twice). Remove the duplicate.

### 5.4 Testing Checklist

After reimplementation, verify:

- [ ] Forge starts normally (desktop + mobile)
- [ ] Commander deck loading doesn't freeze the GUI
- [ ] Game logs are saved to `<userdir>/games/gamelogs/`
- [ ] JSON replay files are generated alongside text logs
- [ ] ANALYSIS log entries appear in game log (zone changes, mana info)
- [ ] AI decision log entries appear
- [ ] Simulation mode works: `forge.exe sim -d deck1 deck2 -n 10`
- [ ] Extended simulation: `forge.exe sim -xd 1 deckfile.txt -n 100`
- [ ] Replay simulation: `forge.exe sim -replay output_dir`
- [ ] SQLite database created and populated after simulation
- [ ] Lobby defaults to Commander if no saved variant
- [ ] Variant selection is persisted across restarts
- [ ] Targeting overlay arcs default to ON
- [ ] No NullPointerException in ColumnDef deck color sorting
- [ ] No IndexOutOfBoundsException in GameSnapshot zone restoration

---

*Generated: February 2026*  
*Fork base: Card-Forge/forge @ a6c5e79f24*  
*Branch: apply-analysis-patch (6 commits)*

