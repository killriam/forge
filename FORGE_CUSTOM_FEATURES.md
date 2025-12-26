# Forge - Custom Features Overview

This document provides a consolidated overview of all custom modifications and features added to the Forge codebase.

---

## 🎯 Key Features

### 1. Game Analytics & Database
**Purpose:** Track game results, deck statistics, and starting hand analysis

**Key Files:**
- `forge-game/src/main/java/forge/util/SQLiteConnection.java` - Database operations
- `forge-game/src/main/java/forge/game/GameAnalysis.java` - Game analysis results
- `forge-game/src/main/java/forge/game/player/DeckStats.java` - Deck statistics tracking

**Capabilities:**
- SQLite database for persistent storage
- Win rates, life delta, turn count per deck
- Mana curve analysis (available mana per turn)
- Starting hand statistics

---

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

**Key Methods:**
- `updateCustom()` - Now uses `FThreads.invokeInBackgroundThread()`
- `refreshDecksList()` - Triggers async loading

---

## 📁 New Files Added

### Core Classes
| File | Purpose |
|------|---------|
| `forge-core/.../CardForFitting.java` | Card fitting data class |
| `forge-core/.../DeckIdea.java` | Deck idea/template class |
| `forge-core/.../FittingSection.java` | Deck section fitting |

### Game Analysis
| File | Purpose |
|------|---------|
| `forge-game/.../GameAnalysis.java` | Game analysis results |
| `forge-game/.../DeckStats.java` | Deck statistics |
| `forge-game/.../startingHandStats.java` | Starting hand stats |
| `forge-game/.../SQLiteConnection.java` | SQLite operations |

### Replay Notation (11 files)
| File | Purpose |
|------|---------|
| `forge-game/.../model/ReplayLog.java` | Root replay object |
| `forge-game/.../model/ReplayMeta.java` | Game metadata |
| `forge-game/.../model/CardDefinition.java` | Card index |
| `forge-game/.../model/GameState.java` | State snapshot |
| `forge-game/.../model/L1Event.java` | Event log entry |
| `forge-game/.../model/L2Unit.java` | Learning unit |
| `forge-game/.../ReplayNotationExporter.java` | Export engine |
| `forge-game/.../ReplayL2Generator.java` | L2 generator |
| `forge-game/.../ReplayNotationValidator.java` | Validator |
| `forge-game/.../ReplayJsonSerializer.java` | JSON writer |

---

## 🔧 Dependencies Added

### Root `pom.xml`
```xml
<dependency>
    <groupId>org.xerial</groupId>
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

## 📚 Related Documentation

All detailed documentation has been moved to `temp-docs/` folder:
- Replay notation specifications
- Implementation details
- Debug guides
- Test results

---

## 🚀 Quick Start

### Enable Game Logging
```java
game.getLog().setLogLevel(GameLogEntryType.ANALYSIS);
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
java -jar forge.jar -simulate -deck1 "MyDeck" -deck2 "OpponentDeck" -games 100
```

---

*Last updated: December 2025*

