# Scenario Sim Fix — Complete Timeline

**Project:** Forge MTG Scenario Enforcement  
**Issue:** `sim -s` not enforcing scenario constraints  
**Status:** ✅ **SHOULD BE FULLY FIXED** (Iteration #3)

---

## 📅 Timeline

### 2026-05-03 Initial Report
- **Reporter:** MaMo Testing Team
- **Issue:** Scenario starting hand not enforced
  - Replay mode: `"full_game"` (should be `"scenario"`)
  - Player mulligans despite scenario (should skip)
  - Hand: 5 cards after mulligan (should be 1 card from scenario)

---

### 2026-05-03 19:11 — Iteration #1

**Fixes:**
1. ✅ Mulligan skip logic (MulliganService.java)
2. ❌ Replay mode (didn't apply)
3. ❌ Starting hand (ScenarioLibrarySetup not called)

**Result:** Partial fix — mulligan worked, rest failed

---

### 2026-05-03 20:07 — Iteration #1 Test Results

**Testing Team Report:**
- ✅ Mulligans now skipped
- ❌ Replay mode still `"full_game"`
- ❌ Hand: 7 random cards (not 1 from scenario)

**Diagnosis:** ScenarioLibrarySetup not being invoked

---

### 2026-05-03 20:25 — Iteration #2

**Fixes:**
1. ✅ Replay mode (re-implemented with debug logging)
2. ✅ ScenarioLibrarySetup debug logging (to trace calls)

**Result:** Additional fixes with diagnostics

---

### 2026-05-03 20:28 — Iteration #2 Test Results

**Testing Team Report:**
- ✅ Replay mode now `"scenario"` ✨
- ✅ Mulligans still skipped ✨
- ✅ ScenarioLibrarySetup IS CALLED ✨
- ❌ Hand: Still 7 cards (should be 1)

**Diagnosis:** 
- Library WAS reordered correctly (Command Tower is card #1)
- BUT: GameAction drew 7 cards (default) instead of 1 (scenario size)

---

### 2026-05-04 06:13 — Iteration #3 🎯

**Root Cause Identified:**

```java
// PROBLEM: Always drew default starting hand size
p1.drawCards(p1.getStartingHandSize());  // ← 7 cards

// SOLUTION: Draw scenario starting_hand size
int handSize = scenHand.size();  // ← 1 card for ["Command Tower"]
p1.drawCards(handSize);
```

**Critical Fix:**
- GameAction.java now draws CORRECT number of cards based on scenario

**Expected Result:**
```
✅ Replay mode: "scenario"
✅ Mulligans: 0
✅ Hand: ["Command Tower"] (exactly 1 card)
✅ Scenario verification: PASS
```

---

## 🔧 All Code Changes

| File | Lines Changed | Purpose |
|------|---------------|---------|
| `MulliganService.java` | +7 | Skip mulligan for scenario AI |
| `SimulateMatch.java` | +4 | Debug logging |
| `ReplayNotationExporter.java` | +8 | Set mode="scenario" |
| `GameAction.java` | +20 | Debug logging + **draw correct card count** |

**Total:** 4 files, 39 lines added

---

## 🧪 Final Test Instructions

**JAR:** `forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar`  
**Build Time:** 2026-05-04 06:13:24  
**Location:** `forge-gui-desktop/target/`

**Test Command:**
```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar sim \
  -d "scenario-export.dck" "scenario-export.dck" \
  -n 1 \
  -f Constructed \
  -s "D:\Daten\SoftwareProjekte\MaMo\tmp\frontend-forge-test\scenario-run\scenario-export.json"
```

---

## ✅ Success Criteria

### Console Output Must Show:

```
Scenario: Starting hands loaded for [P1]
[DEBUG] ScenarioSkipMulligan flag set to: true
[GameAction] ScenarioLibrarySetup.reorderLibraries() will be called for [P1]
[GameAction] Ai(1)-... (P1) will draw 1 cards (scenario starting hand size)  ← KEY!
[MulliganService] Scenario skip mulligan is ENABLED
[ReplayNotationExporter] Setting replay mode to 'scenario'

===== SCENARIO STARTING HAND VERIFICATION =====
P1 Expected (1): [Command Tower]
P1 Actual   (1): [Command Tower]  ← KEY: Must be 1 card!
P1: PASS ✓

SCENARIO RESULT: PASS ✓
```

### Replay JSON Must Have:

```json
{
  "mode": "scenario",  // ✅ Must be "scenario"
  "game_summary": {
    "P1_mulligans": 0  // ✅ Must be 0
  },
  "log_l1": [
    // ✅ Only 1 DRAW event for P1 in T1.UP
    {"type": "DRAW", "a": "P1", "data": {"card_name": "Command Tower"}},
    // No more starting hand draws
    ...
  ]
}
```

---

## 📊 Expected vs. Actual (All Iterations)

| Criterion | Iter #1 | Iter #2 | Iter #3 (Expected) |
|-----------|---------|---------|-------------------|
| Replay mode | ❌ full_game | ✅ scenario | ✅ scenario |
| Mulligans | ✅ 0 | ✅ 0 | ✅ 0 |
| Hand size | ❌ 7 | ❌ 7 | ✅ 1 |
| Hand cards | ❌ random | ❌ random | ✅ Command Tower |
| Verification | ❌ FAIL | ❌ FAIL | ✅ PASS |

---

## 🎯 If All Tests Pass

**Please report back:**

```
✅ Console shows "will draw 1 cards (scenario starting hand size)"
✅ Scenario verification PASS
✅ Replay JSON mode: "scenario"
✅ Replay JSON P1_mulligans: 0
✅ Replay log: 1 DRAW event for Command Tower

STATUS: FIXED ✅
```

**Then we can mark this finding as:** `FIXED` ✅

---

## 📝 If Any Test Fails

Please provide:
1. Full console output (all debug messages)
2. Complete replay JSON file
3. Text gamelog file
4. Exact command used

---

## 🙏 Thank You

**MaMo Testing Team:** Your detailed findings and patient re-testing through 3 iterations were instrumental in solving this! The issue turned out to be a subtle logic error where the library was reordered correctly, but we were drawing the wrong number of cards. This would have been nearly impossible to diagnose without your excellent bug reports. 🎯

**Forge Development:** All source changes documented in:
- `scripts/README_BLACKBOX_TESTING.md` (Agent Updates section)
- `SCENARIO_SIM_FIX_ITERATION_3.md` (This file)
- `SCENARIO_SIM_FIX_SUMMARY.md` (Overview)

---

**Final Status:** ⏳ **AWAITING FINAL VALIDATION**  
**Confidence Level:** 🎯 **Very High** (root cause identified and fixed)

---

*Document created: 2026-05-04 06:15*  
*For questions, check: `scripts/README_BLACKBOX_TESTING.md`*

