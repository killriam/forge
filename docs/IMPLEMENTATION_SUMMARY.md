# Simulation Statistics Implementation Summary

**Status:** ✅ Phase 1 Complete - Ready for Integration Testing  
**Date:** 2026-04-07  
**Version:** 2.0.0

---

## What Was Implemented

### Java Components (forge-game)

1. **`SimulationStats.java`** - Data model for JSON export
   - Nested classes: `MetaData`, `GameOutcomeData`, `TimelineData`
   - Format: `forge-simulation-stats` v2.0.0

2. **`PlayerStats.java`** - Per-player statistics model
   - Nested classes: `CardsData`, `SpellsData`, `ManaData`, `CombatData`, `BoardData`, `TempoData`

3. **`SimulationMetricsCollector.java`** - Core metrics collection logic
   - Tracks metrics during game execution
   - Called at turn end and game end
   - Exports to `SimulationStats` object

4. **`SimulationStatsExporter.java`** - JSON export utility
   - Uses Gson for pretty-printing
   - Exports to `simulation_stats_<timestamp>.json`

5. **`Game.java`** (modified)
   - Added field: `private SimulationMetricsCollector simulationMetricsCollector`
   - Added getters/setters for metrics collector

---

## File Sizes (Estimated)

| Format | Per Game | 100 Games | Reduction |
|--------|----------|-----------|-----------|
| **Full Replay** (current) | 200-800 KB | 20-80 MB | - |
| **Simulation Stats** (new) | 5-10 KB | 500 KB - 1 MB | **95-98%** |

---

## Integration Steps

### Step 1: Wire Up SimulateMatch.java

**File:** `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`

```java
public static void simulateSingleMatch(final Match mc, int iGame, boolean outputGamelog) {
    // ...existing code...
    
    final Game g1 = mc.createGame();
    
    // NEW: Initialize simulation metrics collector
    boolean trackTimeline = false; // Set to true for turn-by-turn data
    SimulationMetricsCollector metricsCollector = 
        new SimulationMetricsCollector(g1, trackTimeline);
    g1.setSimulationMetricsCollector(metricsCollector);
    
    // Run game
    try {
        TimeLimitedCodeBlock.runWithTimeout(() -> {
            mc.startGame(g1);
            sw.stop();
        }, mc.getRules().getSimTimeout(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        System.out.println("Stopping slow match as draw");
    } finally {
        // ...existing cleanup...
    }
    
    // NEW: Export simulation stats
    File statsDir = new File(ForgeConstants.SIMULATION_STATS_DIR);
    statsDir.mkdirs();
    
    File statsFile = SimulationStatsExporter.exportToJson(g1, statsDir);
    if (statsFile != null) {
        System.out.println("Simulation stats: " + statsFile.getAbsolutePath());
    }
    
    // Optional: Still save full replay if -r flag set
    if (params.containsKey("r")) {
        File replayFile = GameLogSaver.saveGameLogReplayNotation(g1, true);
        System.out.println("Full replay: " + replayFile.getAbsolutePath());
    }
    
    // ...existing code (outcome logging)...
}
```

### Step 2: Add Constants to ForgeConstants.java

**File:** `forge-gui/src/main/java/forge/localinstance/properties/ForgeConstants.java`

```java
public static final String SIMULATION_STATS_DIR = USER_DIR + "games/simulation_stats/";
```

### Step 3: Hook Turn End Events

**Option A: PhaseHandler (recommended)**

**File:** `forge-game/src/main/java/forge/game/phase/PhaseHandler.java`

Find the `advanceToNextPhase()` method and add:

```java
public void advanceToNextPhase() {
    // ...existing code...
    
    // NEW: Track metrics at end of turn
    if (phase == PhaseType.CLEANUP && game.getSimulationMetricsCollector() != null) {
        game.getSimulationMetricsCollector().onTurnEnd(turn);
    }
    
    // ...rest of method...
}
```

**Option B: Game.onCleanupPhase()**

Add to end of `Game.onCleanupPhase()`:

```java
public void onCleanupPhase() {
    // ...existing cleanup code...
    
    // Track simulation metrics
    if (simulationMetricsCollector != null) {
        simulationMetricsCollector.onTurnEnd(getPhaseHandler().getTurn());
    }
}
```

---

## Testing

### Unit Test

```java
@Test
public void testSimulationMetricsCollector() {
    // Create mock game
    Game game = createTestGame();
    
    // Initialize collector
    SimulationMetricsCollector collector = 
        new SimulationMetricsCollector(game, true);
    game.setSimulationMetricsCollector(collector);
    
    // Simulate events
    Player p1 = game.getPlayers().get(0);
    collector.onCardDrawn(p1);
    collector.onLandPlayed(p1);
    collector.onTurnEnd(1);
    
    // Export and validate
    SimulationStats stats = collector.exportStats();
    Assert.assertNotNull(stats);
    Assert.assertEquals("forge-simulation-stats", stats.getFormat());
    Assert.assertEquals("2.0.0", stats.getVersion());
}
```

### Integration Test

```bash
# Build project
mvn clean package -DskipTests -pl forge-gui-desktop -am

# Run 1 test game
java -jar forge-gui-desktop/target/forge-gui-desktop-*-jar-with-dependencies.jar \
     sim -d deck1.dck deck2.dck -n 1 -f commander -q

# Check output
ls %APPDATA%\Forge\games\simulation_stats\
# Should see: simulation_stats_<timestamp>.json

# Validate JSON
python -c "import json; print(json.load(open('simulation_stats_*.json'))['format'])"
# Should print: forge-simulation-stats
```

---

## Python Analytics Update

**File:** `analyze_commander_stats.py`

Already implemented to handle both formats:

```python
def detect_log_format(file_path: Path) -> str:
    with open(file_path, 'r') as f:
        data = json.load(f)
    
    if data.get('format') == 'forge-simulation-stats':
        return 'simulation_stats'
    elif data.get('format') == 'mtg-replay':
        return 'full_replay'
    else:
        return 'unknown'

# Load based on format (backward compatible)
if format == 'simulation_stats':
    stats = load_simulation_stats(file_path)
else:
    stats = load_full_replay(file_path)  # Legacy
```

---

## Migration Strategy

### Phase 1 (Current): Dual Output

```java
// Write BOTH formats during transition period
File statsFile = SimulationStatsExporter.exportToJson(g1, statsDir);
File replayFile = GameLogSaver.saveGameLogReplayNotation(g1, false);
```

### Phase 2: Flag-Based

```bash
# Simulation stats only (default)
java -jar forge.jar sim -d deck1 deck2 -n 100 -f commander

# Full replay for debugging (optional)
java -jar forge.jar sim -d deck1 deck2 -n 1 -f commander --full-replay
```

### Phase 3: Stats Only

Remove full replay export from simulation mode entirely. Use separate recording mode:

```bash
# Record replay for viewer
java -jar forge.jar replay -d deck1 deck2 -f commander
```

---

## Event Tracking (Future Enhancement)

Currently, metrics are updated via `onTurnEnd()`. For more accurate tracking, add hooks to game events:

### Example: Track Spell Cast

**File:** `forge-game/src/main/java/forge/game/spellability/SpellAbility.java`

```java
public void resolve() {
    // ...existing code...
    
    // Track metrics
    if (game.getSimulationMetricsCollector() != null) {
        game.getSimulationMetricsCollector().onSpellCast(
            getActivatingPlayer(), 
            getHostCard()
        );
    }
    
    // ...rest of method...
}
```

### Example: Track Damage

**File:** `forge-game/src/main/java/forge/game/GameEntity.java`

```java
public final int addDamageAfterPrevention(int damage, Card source, boolean isCombat, Map<AbilityKey, Object> params) {
    // ...existing code...
    
    // Track metrics
    if (game.getSimulationMetricsCollector() != null) {
        if (source != null && source.getController() != null) {
            game.getSimulationMetricsCollector().onDamageDealt(
                source.getController(), 
                damage
            );
        }
        if (this instanceof Player) {
            game.getSimulationMetricsCollector().onDamageTaken(
                (Player) this, 
                damage
            );
        }
    }
    
    // ...rest of method...
}
```

---

## Validation

### JSON Schema Validation

```bash
# Install validator
pip install jsonschema

# Create schema file
cat > simulation-stats-schema.json << 'EOF'
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["format", "version", "meta", "outcome", "players"],
  "properties": {
    "format": {"const": "forge-simulation-stats"},
    "version": {"pattern": "^2\\.\\d+\\.\\d+$"},
    "meta": {"type": "object"},
    "outcome": {"type": "object"},
    "players": {"type": "object"}
  }
}
EOF

# Validate
jsonschema -i simulation_stats_*.json simulation-stats-schema.json
```

### Manual Validation

```python
import json

with open('simulation_stats_20260407_143000.json', 'r') as f:
    data = json.load(f)

# Check required fields
assert data['format'] == 'forge-simulation-stats'
assert data['version'] == '2.0.0'
assert 'meta' in data
assert 'outcome' in data
assert 'players' in data

# Check player stats structure
for player_id, stats in data['players'].items():
    assert 'cards' in stats
    assert 'spells' in stats
    assert 'mana' in stats
    assert 'combat' in stats
    assert 'board' in stats

print("✅ Validation passed!")
```

---

## Known Limitations (Phase 1)

1. **No event hooks yet** - Metrics are estimated from turn-end state, not tracked in real-time
2. **Mana tracking simplified** - Uses untapped lands count, not full mana pool analysis
3. **Timeline optional** - Disabled by default to save space (can enable with constructor arg)
4. **No mulligan tracking** - Mulligans not yet counted (requires hook in MulliganService)
5. **CMC average estimation** - Calculated only from cast spells, not from deck composition

---

## Next Steps

1. **Test Integration** - Add hooks to `SimulateMatch.java`
2. **Event Tracking** - Add real-time event hooks (damage, spells, etc.)
3. **Python Analytics** - Verify `analyze_commander_stats.py` works with new format
4. **Documentation** - Update user-facing docs with new workflow
5. **Performance Test** - Run 1000 games, measure file sizes and load times

---

## Files Modified/Created

### Created (Java)
- `forge-game/src/main/java/forge/game/simulation/SimulationStats.java`
- `forge-game/src/main/java/forge/game/simulation/PlayerStats.java`
- `forge-game/src/main/java/forge/game/simulation/SimulationMetricsCollector.java`
- `forge-game/src/main/java/forge/game/simulation/SimulationStatsExporter.java`

### Modified (Java)
- `forge-game/src/main/java/forge/game/Game.java` (added field + getters/setters)

### To Modify (Integration)
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java` (add exporter calls)
- `forge-gui/src/main/java/forge/localinstance/properties/ForgeConstants.java` (add constant)
- `forge-game/src/main/java/forge/game/phase/PhaseHandler.java` (add turn-end hook) OR
- `forge-game/src/main/java/forge/game/Game.java` (add to onCleanupPhase)

### Created (Documentation)
- `docs/SIMULATION_ANALYTICS_ARCHITECTURE.md`
- `docs/SIMULATION_STATS_FORMAT.md`
- `docs/IMPLEMENTATION_SUMMARY.md` (this file)

### Updated (Documentation)
- `COMMANDER_SIMULATION_README.md` (added links to new docs)

---

**Status:** ✅ Ready for integration testing  
**Effort:** ~6 hours (actual implementation)  
**Next:** Wire up SimulateMatch.java + test with real games


