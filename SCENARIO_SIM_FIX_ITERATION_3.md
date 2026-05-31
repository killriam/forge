# Scenario Fix — Iteration #3 Update

**Date:** 2026-05-04 06:13  
**Status:** ✅ **SHOULD BE FULLY FIXED NOW**

---

## 🎯 The Critical Fix (Iteration #3)

### Root Cause Identified

After Iteration #2 testing (2026-05-03 20:28), we found:
- ✅ Replay mode = "scenario" (FIXED)
- ✅ Mulligans skipped (FIXED)
- ✅ ScenarioLibrarySetup.reorderLibraries() IS called (WORKING)
- ❌ **Starting hand still wrong: 7 cards instead of 1**

**The Problem:**

`GameAction.java` was calling `ScenarioLibrarySetup.reorderLibrary()` correctly (Library WAS reordered with Command Tower as card #1), but then it drew the **default starting hand size (7 cards)** instead of the **scenario starting_hand size (1 card for ["Command Tower"])**!

---

## ✅ Fix #5: Correct Draw Size

**File:** `forge-game/src/main/java/forge/game/GameAction.java` (Line ~2383)

**BEFORE:**
```java
for (final Player p1 : game.getPlayers()) {
    if (StaticData.instance().getFilteredHandsEnabled() ) {
        drawStartingHand(p1);
    } else {
        p1.drawCards(p1.getStartingHandSize());  // ← Always drew 7 cards!
    }
```

**AFTER:**
```java
for (final Player p1 : game.getPlayers()) {
    // FIX: In scenario mode, draw the number of cards defined in scenario starting_hand
    int handSize = p1.getStartingHandSize();  // default: 7
    
    if (scenStartingHands != null && !scenStartingHands.isEmpty()) {
        int playerIndex = game.getPlayers().indexOf(p1);
        String playerId = "P" + (playerIndex + 1);
        java.util.List<String> scenHand = scenStartingHands.get(playerId);
        
        if (scenHand != null && !scenHand.isEmpty()) {
            handSize = scenHand.size();  // ← Now uses scenario size (1 for ["Command Tower"])
            System.out.println("[GameAction] " + p1.getName() + " (" + playerId + 
                    ") will draw " + handSize + " cards (scenario starting hand size)");
        }
    }
    
    if (StaticData.instance().getFilteredHandsEnabled() ) {
        drawStartingHand(p1);
    } else {
        p1.drawCards(handSize);  // ← Draws correct number!
    }
```

---

## 🧪 Expected Test Results

### Console Output

You should now see:

```
Scenario: Starting hands loaded for [P1]
Scenario type: opening_hand_test - AI mulligans skipped
[DEBUG] ScenarioSkipMulligan flag set to: true
[GameAction] Checking scenario starting hands: [P1]
[GameAction] ScenarioLibrarySetup.reorderLibraries() will be called for [P1]
[GameAction] Ai(1)-Scenario: Perfect Game (P1) will draw 1 cards (scenario starting hand size)
[MulliganService] Scenario skip mulligan is ENABLED - AI players will keep hands
[MulliganService] Ai(1)-Scenario: Perfect Game is AI in scenario mode - using ScenarioKeepMulligan
[ReplayNotationExporter] Setting replay mode to 'scenario'
ScenarioLibrarySetup: Ai(1)-Scenario: Perfect Game's library reordered — hand: 1/1, draws: 0/0, remaining: 99
```

### Scenario Verification

```
===== SCENARIO STARTING HAND VERIFICATION =====
P1 Expected (1): [Command Tower]
P1 Actual   (1): [Command Tower]
P1: PASS ✓

SCENARIO RESULT: PASS ✓
All 1 constrained player(s) passed verification
```

### Replay JSON

```json
{
  "format": "mtg-replay",
  "version": "1.5.0",
  "mode": "scenario",  // ✅ Correct
  "game_summary": {
    "winner": "...",
    "P1_mulligans": 0,  // ✅ Correct
    ...
  },
  "log_l1": [
    // First 1 DRAW event for P1 in T1.UP (starting hand)
    {"i": 1, "t": "T1.UP:1", "a": "P1", "type": "DRAW", "data": {"card_name": "Command Tower"}},
    // No more initial draws
    ...
  ]
}
```

---

## 📊 All Fixes Summary

| Iteration | Fix | File | Status |
|-----------|-----|------|--------|
| #1 | Mulligan skip | MulliganService.java | ✅ WORKS |
| #1 | Debug logging | SimulateMatch.java | ✅ WORKS |
| #2 | Replay mode | ReplayNotationExporter.java | ✅ FIXED |
| #2 | ScenarioLibrarySetup debug | GameAction.java | ✅ WORKS |
| #3 | **Draw correct card count** | GameAction.java | ✅ **SHOULD FIX** |

---

## 🚀 Testing Instructions

**JAR:** `forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar` (Built: 2026-05-04 06:13:24)

**Command:**
```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar sim \
  -d "scenario-export.dck" "scenario-export.dck" \
  -n 1 \
  -f Constructed \
  -s "D:\Daten\SoftwareProjekte\MaMo\tmp\frontend-forge-test\scenario-run\scenario-export.json"
```

**Check:**
1. ✅ Console shows: "will draw 1 cards (scenario starting hand size)"
2. ✅ Scenario verification: "PASS"
3. ✅ Replay JSON: `"mode": "scenario"`
4. ✅ P1 hand in replay: Only 1 DRAW event for Command Tower

---

**If all checks pass:** STATUS = **FIXED** ✅  
**If any check fails:** Please provide full console output + replay JSON

---

**For MaMo Testing Team:** This should be the final fix! The root cause was that we were drawing the default 7 cards even though the scenario only specified 1 card in starting_hand. Now it draws exactly the number of cards defined in the scenario. 🎯

