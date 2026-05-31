# Replay Log Writer Fixes - Implementation Summary

**Date:** 2026-05-15  
**Issue:** MTG Replay Notation frontend requires proper `views_l2` snapshots and event metadata

---

## Problems Fixed

### ✅ Priority 1 (CRITICAL): Empty `views_l2` Array

**Problem:**  
- `views_l2` was always `[]` in exported JSON  
- Frontend relies on L2 views for hand/board state display  
- Without L2 views, frontend must simulate from events (error-prone)

**Solution Implemented:**

1. **Added `captureFullGameState()` method** (`ReplayNotationExporter.java:1537-1668`)
   - Captures complete game state including ALL zones
   - **Critically includes `P1:hand`, `P2:hand`, `P3:hand`, etc.**
   - Captures battlefield, library, graveyard, exile, command zones
   - Creates `ObjectState` for every card with full metadata

2. **Added turn-start snapshot tracking** (`ReplayNotationExporter.java:1673`)
   - Captures `before` state at turn begin
   - Stores in `turnStartSnapshot` field

3. **Added `generateL2UnitForTurn()` method** (`ReplayNotationExporter.java:1748-1810`)
   - Called at end-of-turn (during `flushTurnSummary()`)
   - Creates one L2 Unit per turn with:
     - `t_start`: `T{turn}.DRAW:1`
     - `t_end`: `T{turn}.CLEANUP:last`
     - `before`: Full game state at turn start
     - `after`: Full game state at turn end (end of CLEANUP)
     - `l1_range`: Event indices for this turn
     - `decision_events`: All player decision events in turn
     - `stack`: Empty for now (can be populated later)

**Result:**  
- Each turn now generates one L2 Unit
- Frontend can display hand + board state without event simulation
- All zones (especially hands) are fully populated

---

### ✅ Priority 2 (IMPORTANT): Missing `controller` Field in MOVE/DRAW Events

**Problem:**  
- MOVE events only had `card_name`, not `controller`
- Tokens created mid-game showed in wrong player's board
- Frontend fallback assigned "P1" to all unknown objects

**Solution Implemented:**

**ReplayNotationExporter.java:**
- `logZoneChange()` (line 757-759): Added `controller` and `owner` to MOVE event data
- `logDraw()` (line 793-799): Added `controller` and `owner` to DRAW event data

**ReplayEventLogger.java:**
- `visit(GameEventCardChangeZone)` (line 330-336): Added `controller` and `owner` to MOVE events

**Result:**  
- MOVE events now include: `{"obj": "t1", "card_name": "Soldier Token", "controller": "P2", "owner": "P2"}`
- Tokens and stolen permanents appear in correct player's board column

---

### ✅ Priority 3 (NICE-TO-HAVE): Missing `owner` for New Objects

**Problem:**  
- New objects (tokens, copies) only appeared via MOVE events
- No `owner` field → simulation couldn't reconstruct ownership

**Solution Implemented:**  
- Same as Priority 2 — `owner` field now included in all MOVE/DRAW events

**Result:**  
- Event simulation can correctly assign ownership even for mid-game tokens

---

## Files Modified

| File | Lines Changed | Changes |
|------|---------------|---------|
| `ReplayNotationExporter.java` | ~273 new lines | Added L2 generation logic, controller/owner fields |
| `ReplayEventLogger.java` | ~6 new lines | Added controller/owner fields to MOVE events |

---

## Technical Details

### L2 Unit Structure (per turn)

```json
{
  "u": 1,
  "t_start": "T1.DRAW:1",
  "t_end": "T1.CLEANUP:last",
  "l1_range": [22, 45],
  "decision_events": [25, 31],
  "before": {
    "turn": 1,
    "phase": "DRAW",
    "players": { "P1": {...}, "P2": {...} },
    "zones": {
      "P1:hand": ["c1", "c2", "c3"],
      "P2:hand": ["c10", "c11", "c12"],
      "battlefield": [],
      "P1:library": { "count": 53, "cards": [...] },
      "P2:library": { "count": 53, "cards": [...] }
    },
    "objects": {
      "c1": { "card_ref": "Lightning Bolt", "controller": "P1", "owner": "P1", "zone": "P1:hand", ... },
      "c2": { ... }
    }
  },
  "after": { /* Full GameState at end of CLEANUP */ },
  "stack": [],
  "annotations": {}
}
```

### Event Data Structure (MOVE/DRAW)

**Before (missing controller):**
```json
{"type": "MOVE", "data": {"obj": "t1", "card_name": "Soldier Token", "from": "battlefield", "to": "P2:graveyard"}}
```

**After (with controller + owner):**
```json
{"type": "MOVE", "data": {"obj": "t1", "card_name": "Soldier Token", "controller": "P2", "owner": "P2", "from": "battlefield", "to": "P2:graveyard"}}
```

---

## Testing Recommendations

1. **Build & Compile:**
   ```powershell
   mvn clean compile -pl forge-game -am
   ```

2. **Run a Test Game:**
   ```powershell
   java -jar forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar sim -d my_decks/deck1.dck my_decks/deck2.dck -n 1
   ```

3. **Validate Replay Log:**
   - Check output replay JSON in `forge-gui-desktop/` (filename: `replay_*.json` or `sim_*.json`)
   - Verify `views_l2` is no longer empty
   - Verify each L2 unit has:
     - `before.zones.P1:hand` with card IDs
     - `before.zones.P2:hand` with card IDs
     - `before.objects` with full card metadata
   - Verify MOVE events have `controller` and `owner` fields

4. **Frontend Test:**
   - Load replay in frontend viewer
   - Verify hand cards display correctly for all players
   - Verify tokens appear in correct player's board

---

## Spec Compliance

### MTG Replay Notation v1.1.0+

- ✅ `views_l2`: Populated with per-turn snapshots
- ✅ `views_l2[].before.zones.P*:hand`: All player hands included
- ✅ `views_l2[].after.zones.P*:hand`: All player hands included
- ✅ MOVE event `card_name` (v1.1.0): Already implemented
- ✅ MOVE event `controller` (new): Now implemented
- ✅ MOVE event `owner` (new): Now implemented

---

## Impact

### Critical Fix (views_l2 population)
- **Without this fix:** Frontend must simulate from events → edge cases, missing data
- **With this fix:** Frontend uses authoritative snapshots → accurate display

### Important Fix (controller field)
- **Without this fix:** Tokens show in wrong player's board
- **With this fix:** All permanents show in correct player's column

---

## Notes

- L2 generation happens **once per turn** at end-of-turn (CLEANUP phase)
- Snapshots are captured **before** and **after** the turn
- Stack items are currently empty (can be enhanced later to track cast spells)
- Decision events are automatically detected (CAST, ACTIVATE, DECLARE_ATTACKERS, etc.)

---

## Next Steps (Optional Enhancements)

1. **Populate stack items in L2 Units** — track spells/abilities on stack during turn
2. **Add annotations** — AI evaluation, blunder detection, teaching notes
3. **Optimize snapshot size** — only include changed zones/objects (delta compression)
4. **Add priority tracking** — track priority passes during turn for fine-grained L2 units

---

**Status:** ✅ All three priorities implemented and ready for testing
