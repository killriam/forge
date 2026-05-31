# Simulation Analytics Architecture

**Version:** 2.0.0  
**Datum:** 2026-04-07  
**Status:** Proposed Design

---

## 1. Problem Statement

**Aktuell:** AI-Simulationen generieren vollständige MTG Replay Notation JSON-Logs (~200-800 KB pro Spiel), die alle L1-Events, L2-Views und detaillierte Turn-Summaries enthalten. Bei 100 Spielen = 20-80 MB Logs.

**Problem:**
- ❌ Redundante Daten (viele Metriken könnten bereits während Simulation berechnet werden)
- ❌ Analyse-Skript muss alle Logs neu laden und aggregieren (langsam bei 100+ Spielen)
- ❌ Simulation-Logs vermischen Replay-Daten mit Statistik-Daten
- ❌ Keine inkrementelle Statistik-Aggregation möglich

**Lösung:** Zwei-Ebenen-Architektur:
1. **Simulation Logs** (schlank): Nur essential metrics pro Spiel + Rohdaten für Ableitungen
2. **Analytics Engine** (separat): Aggregiert Simulation Logs → detaillierte Reports

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│ External Tool (Deck Creation)                               │
│ • Converts JSON to .dck format                              │
│ • Or: Manual deck creation in Forge                         │
├─────────────────────────────────────────────────────────────┤
│ %APPDATA%\Forge\decks\commander\*.dck                       │
│ (Pre-existing deck files)                                   │
└─────────────────────┬───────────────────────────────────────┘
                      │ Reads: .dck files
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ Forge AI Simulation (SimulateMatch.java)                    │
├─────────────────────────────────────────────────────────────┤
│ • Loads .dck deck files                                     │
│ • Führt Spiel aus (GameAction.startGame)                    │
│ • Sammelt Metriken während des Spiels (SimulationMetrics)   │
│ • Schreibt schlanke JSON (simulation_stats_*.json)          │
└─────────────────────┬───────────────────────────────────────┘
                      │ Writes: simulation_stats_*.json (5-10 KB)
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ %APPDATA%\Forge\games\simulation_stats\                     │
│ ├─ simulation_stats_20260407_143022.json                    │
│ ├─ simulation_stats_20260407_143045.json                    │
│ └─ ... (100 files @ ~5-10 KB each = 500 KB - 1 MB)          │
└─────────────────────┬───────────────────────────────────────┘
                      │ Reads batch of files
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ Analytics Engine (analyze_commander_stats.py)               │
├─────────────────────────────────────────────────────────────┤
│ • Lädt alle simulation_stats_*.json                         │
│ • Aggregiert Metriken (avg, median, stdev)                  │
│ • Berechnet abgeleitete Metriken (z.B. win_rate)            │
│ • Generiert commander_simulation_report.json                │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      │ Writes: commander_simulation_report.json
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ Final Report (JSON/CSV/HTML)                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Simulation Stats Format (Reduced)

### 3.1 File Format: `simulation_stats_<timestamp>.json`

```json
{
  "format": "forge-simulation-stats",
  "version": "2.0.0",
  "meta": {
    "timestamp": "2026-04-07T14:30:22Z",
    "simulation_id": "sim_001",
    "game_type": "Commander",
    "deck1_name": "Krenko Mob Boss",
    "deck2_name": "Atraxa Superfriends",
    "deck1_hash": "a3f8c2d1e9b7f604",
    "deck2_hash": "f7a2b9c3d5e8a104"
  },
  "outcome": {
    "winner": "P1",
    "win_condition": "damage",
    "total_turns": 12,
    "duration_ms": 45320,
    "game_ended_reason": "PLAYER_LOST_GAME"
  },
  "players": {
    "P1": {
      "deck_name": "Krenko Mob Boss",
      "final_life": 8,
      "life_delta": -32,
      
      "cards": {
        "drawn": 18,
        "mulligans": 0,
        "starting_hand_size": 7
      },
      
      "spells": {
        "total_cast": 22,
        "creatures": 14,
        "noncreatures": 8,
        "avg_cmc": 2.8
      },
      
      "mana": {
        "lands_played": 8,
        "missed_drops": 1,
        "peak_available": 9,
        "total_produced": 156,
        "total_spent": 142
      },
      
      "combat": {
        "damage_dealt": 160,
        "damage_taken": 32,
        "attacks_declared": 8,
        "blocks_declared": 2
      },
      
      "board": {
        "final_creatures": 12,
        "final_lands": 8,
        "final_other": 3,
        "peak_creatures": 15
      },
      
      "tempo": {
        "abilities_activated": 5,
        "counters_placed": 0,
        "turns_with_action": 11
      }
    },
    "P2": { /* ... same structure ... */ }
  },
  
  "timeline": {
    "turn_count": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
    "P1_life": [40, 40, 40, 38, 35, 30, 22, 18, 15, 12, 10, 8],
    "P2_life": [40, 39, 37, 35, 30, 22, 15, 8, 0, 0, 0, 0],
    "P1_creatures": [0, 1, 2, 3, 5, 7, 10, 12, 14, 15, 14, 12],
    "P2_creatures": [0, 0, 1, 2, 3, 4, 5, 6, 5, 4, 0, 0]
  }
}
```

### 3.2 Design-Entscheidungen

#### Was wird DIREKT berechnet (während Simulation):

✅ **Essential Metrics** (bereits verfügbar in `GameOutcome`, `PlayerStatistics`):
- `winner`, `total_turns`, `duration_ms`
- `final_life`, `life_delta`
- `cards.drawn`, `spells.total_cast`, `creatures`, `noncreatures`
- `mana.lands_played`, `combat.damage_dealt`, `combat.damage_taken`

✅ **Einfach zu tracken** (während Spiel-Loop):
- `mana.missed_drops` (counter bei jedem Turn)
- `mana.peak_available` (max tracking)
- `board.peak_creatures` (max tracking)
- `timeline.*` (arrays, die pro Turn aktualisiert werden)

#### Was wird SPÄTER abgeleitet (in Analytics):

🔄 **Aggregate Statistics** (über mehrere Spiele):
- `win_rate = wins / total_games`
- `avg_turns = mean(outcome.total_turns)`
- `median_peak_mana = median(mana.peak_available)`
- `stdev_*` (Standardabweichungen)

🔄 **Derived Metrics** (aus vorhandenen Daten):
- `card_draw_rate = cards.drawn / total_turns`
- `spell_velocity = spells.total_cast / total_turns`
- `mana_efficiency = mana.total_spent / mana.total_produced`
- `damage_ratio = combat.damage_dealt / combat.damage_taken`

---

## 4. Data Flow

### 4.0 Prerequisite: Deck Files

**Assumption:** `.dck` files are created externally by another tool and already present in:
```
%APPDATA%\Forge\decks\commander\*.dck
```

The simulation system reads these pre-existing deck files. No deck conversion is handled by this architecture.

### 4.1 During Simulation (Java)

```java
// SimulateMatch.java (neu: SimulationMetricsCollector)

public class SimulationMetricsCollector {
    private Game game;
    private Map<String, PlayerMetrics> playerMetrics;
    private List<Integer> turnCountTimeline;
    private Map<String, List<Integer>> lifeTimeline;
    
    public SimulationMetricsCollector(Game game) {
        this.game = game;
        this.playerMetrics = new HashMap<>();
        this.turnCountTimeline = new ArrayList<>();
        this.lifeTimeline = new HashMap<>();
        
        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);
            playerMetrics.put(playerId, new PlayerMetrics());
            lifeTimeline.put(playerId, new ArrayList<>());
        }
    }
    
    // Aufgerufen nach jedem Turn
    public void onTurnEnd(int turnNumber) {
        turnCountTimeline.add(turnNumber);
        
        for (Player player : game.getPlayers()) {
            String playerId = getPlayerId(player);
            PlayerMetrics metrics = playerMetrics.get(playerId);
            
            // Track timeline
            lifeTimeline.get(playerId).add(player.getLife());
            
            // Update peak values
            int availableMana = calculateAvailableMana(player);
            metrics.updatePeakMana(availableMana);
            
            int creaturesOnBoard = countCreatures(player);
            metrics.updatePeakCreatures(creaturesOnBoard);
            
            // Track missed land drops
            if (turnNumber > 1 && metrics.getLandsPlayedThisTurn() == 0 
                && player.getLandsInHand() > 0) {
                metrics.incrementMissedDrops();
            }
        }
    }
    
    public SimulationStats exportStats() {
        SimulationStats stats = new SimulationStats();
        stats.setMeta(/* ... */);
        stats.setOutcome(/* from game.getOutcome() */);
        stats.setPlayers(playerMetrics);
        stats.setTimeline(buildTimeline());
        return stats;
    }
}
```

### 4.2 Export to JSON (Java)

```java
// SimulationStatsExporter.java (neu)

public class SimulationStatsExporter {
    
    public static File exportToJson(Game game, File outputDir) {
        SimulationMetricsCollector collector = 
            game.getSimulationMetricsCollector();
        
        if (collector == null) {
            return null; // Metrics not collected
        }
        
        SimulationStats stats = collector.exportStats();
        
        String filename = String.format(
            "simulation_stats_%s.json",
            new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
        );
        
        File outputFile = new File(outputDir, filename);
        
        try (FileWriter writer = new FileWriter(outputFile)) {
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
            gson.toJson(stats, writer);
            return outputFile;
        } catch (IOException e) {
            LOG.error("Failed to export simulation stats", e);
            return null;
        }
    }
}
```

### 4.3 Analytics (Python)

```python
# analyze_commander_stats.py (vereinfacht)

class SimulationStatsLoader:
    """Lädt schlanke simulation_stats_*.json files."""
    
    def load_batch(self, directory: Path, limit: int = 100) -> List[Dict]:
        pattern = "simulation_stats_*.json"
        files = sorted(directory.glob(pattern), 
                      key=lambda p: p.stat().st_mtime, 
                      reverse=True)[:limit]
        
        stats = []
        for file in files:
            with open(file, 'r', encoding='utf-8') as f:
                stats.append(json.load(f))
        
        return stats


class MetricsAggregator:
    """Aggregiert Metriken aus mehreren Spielen."""
    
    def aggregate(self, stats_list: List[Dict]) -> Dict:
        # Sammle Werte
        P1_turns = [s['outcome']['total_turns'] for s in stats_list]
        P1_damage = [s['players']['P1']['combat']['damage_dealt'] 
                     for s in stats_list]
        
        # Berechne Aggregationen
        return {
            "avg_turns": statistics.mean(P1_turns),
            "median_turns": statistics.median(P1_turns),
            "stdev_turns": statistics.stdev(P1_turns),
            "avg_damage_dealt": statistics.mean(P1_damage),
            # ... etc
        }


class DerivedMetricsCalculator:
    """Berechnet abgeleitete Metriken."""
    
    def calculate(self, stats: Dict) -> Dict:
        outcome = stats['outcome']
        player = stats['players']['P1']
        
        return {
            "card_draw_rate": player['cards']['drawn'] / outcome['total_turns'],
            "spell_velocity": player['spells']['total_cast'] / outcome['total_turns'],
            "mana_efficiency": player['mana']['total_spent'] / player['mana']['total_produced'],
            "damage_ratio": player['combat']['damage_dealt'] / max(1, player['combat']['damage_taken'])
        }
```

---

## 5. File Size Comparison

### 5.1 Full Replay Log (aktuell)

```json
{
  "format": "mtg-replay",
  "version": "1.5.0",
  "meta": { /* 500 bytes */ },
  "card_index": { /* 20-50 KB */ },
  "initial_state": { /* 5-10 KB */ },
  "log_l1": [ /* 100-500 KB */ ],
  "views_l2": [ /* 50-200 KB */ ],
  "per_turn_summary": [ /* 10-30 KB */ ],
  "game_summary": { /* 2 KB */ }
}
```

**Total:** ~200-800 KB pro Spiel × 100 Spiele = **20-80 MB**

### 5.2 Simulation Stats (proposed)

```json
{
  "format": "forge-simulation-stats",
  "version": "2.0.0",
  "meta": { /* 300 bytes */ },
  "outcome": { /* 200 bytes */ },
  "players": {
    "P1": { /* 1.5 KB */ },
    "P2": { /* 1.5 KB */ }
  },
  "timeline": { /* 2-4 KB */ }
}
```

**Total:** ~5-10 KB pro Spiel × 100 Spiele = **500 KB - 1 MB**

**Einsparung:** 95-98% weniger Speicherplatz! 🎉

---

## 6. Implementation Plan

### Phase 1: Core Metrics Collection (Java) ✅ DONE

**Status:** Already implemented
- `SimulationMetricsCollector` ready
- `SimulationStats` model ready
- `SimulationStatsExporter` ready

### Phase 2: Integration with SimulateMatch (Java) - TODO

**Files to modify:**
1. `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`
   - Deck files are already loaded from `%APPDATA%\Forge\decks\commander\*.dck`
   - Just attach `SimulationMetricsCollector` to game
   - Export stats at game end

2. `forge-gui/src/main/java/forge/localinstance/properties/ForgeConstants.java`
   - Add `SIMULATION_STATS_DIR` constant

3. `forge-game/src/main/java/forge/game/Game.java` ✅ DONE
   - Field + getter/setter already added

### Phase 3: Stats Export (Java) ✅ DONE

**Status:** Already implemented
- `SimulationStatsExporter` ready to use

### Phase 4: Analytics Update (Python) ✅ DONE

**Status:** Already implemented
- `analyze_commander_stats.py` ready to process `simulation_stats_*.json`
- Auto-detects format
- Backward compatible

---

## 7. Migration Strategy

### 7.1 Backward Compatibility

**Option A: Dual Mode (recommended for transition)**
```bash
# Old mode: Full replay logs (for replay viewer)
java -jar forge.jar sim -d deck1 deck2 -n 100 -f commander --full-replay

# New mode: Stats-only (for batch analysis)
java -jar forge.jar sim -d deck1 deck2 -n 100 -f commander --stats-only

# Default: Stats + minimal replay reference
java -jar forge.jar sim -d deck1 deck2 -n 100 -f commander
```

**Option B: Separate Tools**
```bash
# Simulation: Always writes stats
java -jar forge.jar sim -d deck1 deck2 -n 100 -f commander

# Replay recording: Separate flag
java -jar forge.jar sim -d deck1 deck2 -n 1 -f commander --record-replay
```

### 7.2 Analytics Tool Compatibility

```python
# analyze_commander_stats.py (auto-detect format)

def detect_log_format(file_path: Path) -> str:
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
        
    if data.get('format') == 'forge-simulation-stats':
        return 'simulation_stats'
    elif data.get('format') == 'mtg-replay':
        return 'full_replay'
    else:
        return 'unknown'

# Load based on format
if format == 'simulation_stats':
    stats = SimulationStatsLoader().load_batch(directory)
elif format == 'full_replay':
    stats = FullReplayLoader().load_batch(directory)  # Legacy
```

---

## 8. Benefits Summary

| Aspect | Before | After | Improvement |
|--------|--------|-------|-------------|
| **File size** | 200-800 KB | 5-10 KB | 95-98% smaller |
| **100 games** | 20-80 MB | 500 KB - 1 MB | 40-160× reduction |
| **Load time** | ~5-10 sec | <1 sec | 5-10× faster |
| **Analytics** | Re-parse all events | Direct field access | 10-20× faster |
| **Storage** | Mixed (replay + stats) | Separated | Better organization |
| **Extensibility** | Hard (embedded in replay) | Easy (add fields) | Modular |

---

## 9. Future Extensions

### 9.1 Incremental Aggregation

```python
# aggregate_incremental.py

class IncrementalAggregator:
    """Aggregiert Statistiken inkrementell (ohne alle Logs neu zu laden)."""
    
    def __init__(self, state_file: Path):
        self.state_file = state_file
        self.state = self.load_state()
    
    def add_game(self, stats: Dict):
        # Update running totals
        self.state['total_games'] += 1
        self.state['sum_turns'] += stats['outcome']['total_turns']
        self.state['sum_damage'] += stats['players']['P1']['combat']['damage_dealt']
        # ... etc
        
        self.save_state()
    
    def get_current_stats(self) -> Dict:
        return {
            "avg_turns": self.state['sum_turns'] / self.state['total_games'],
            "avg_damage": self.state['sum_damage'] / self.state['total_games']
        }
```

### 9.2 Real-Time Dashboard

```python
# watch_simulations.py

import time
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

class SimulationWatcher(FileSystemEventHandler):
    def on_created(self, event):
        if event.src_path.endswith('simulation_stats_*.json'):
            stats = load_stats(event.src_path)
            aggregator.add_game(stats)
            dashboard.update(aggregator.get_current_stats())

# Live-Update während Simulation läuft
observer = Observer()
observer.schedule(SimulationWatcher(), stats_dir, recursive=False)
observer.start()
```

### 9.3 Database Storage

```sql
-- simulation_games table
CREATE TABLE simulation_games (
    id INTEGER PRIMARY KEY,
    timestamp DATETIME,
    deck1_name TEXT,
    deck2_name TEXT,
    winner TEXT,
    total_turns INTEGER,
    duration_ms INTEGER
);

-- player_stats table
CREATE TABLE player_stats (
    game_id INTEGER,
    player_id TEXT,
    final_life INTEGER,
    damage_dealt INTEGER,
    spells_cast INTEGER,
    -- ... all metrics
    FOREIGN KEY (game_id) REFERENCES simulation_games(id)
);
```

---

## 10. Recommendation

**Implement Phase 1 + 2 immediately:**
1. Create `SimulationMetricsCollector` in Java
2. Export `simulation_stats_*.json` from `SimulateMatch`
3. Update `analyze_commander_stats.py` to read new format

**Benefits:**
- 95%+ file size reduction
- 10× faster analytics
- Cleaner separation of concerns
- Extensible for future features

**Effort:** ~2-3 days development + 1 day testing

---

**Last Updated:** 2026-04-07




