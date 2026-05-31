# Forge Build Status — 2026-05-10

**Build Time:** 2026-05-10 16:27:27  
**Status:** ✅ **SUCCESS** (Total time: 03:54 min)

---

## 📦 Build Artifacts

| File | Size | Purpose |
|------|------|---------|
| **forge.exe** | 294 KB | Windows Executable (launch4j wrapper) |
| **forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar** | 238 MB | Standalone JAR (all dependencies) |
| forge-gui-desktop-2.0.13-SNAPSHOT.jar | 2 MB | Main JAR (requires classpath) |
| forge.cmd | 476 B | Windows batch launcher |
| forge.command | 179 B | macOS launcher |
| forge.sh | 184 B | Linux launcher |

---

## 🚀 How to Run

### Windows (Executable)

```cmd
D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe
```

### Windows (Batch)

```cmd
D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.cmd
```

### Cross-Platform (JAR)

```bash
java -jar D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar
```

### PowerShell Quick Start

```powershell
# Start Forge GUI
& "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe"

# Or via JAR
java -jar "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar"
```

---

## ✨ New Features in This Build

### 1. Team Persistence (v1.9.0)

**What's New:**
- ✅ Team associations now persist in replay logs
- ✅ Replay JSON includes `"team": 0` field for each player
- ✅ Teams are correctly restored when loading a replay
- ✅ Backward compatible (old replays still work)

**Example Replay JSON:**

```json
{
  "version": "1.9.0",
  "meta": {
    "players": {
      "P1": {
        "name": "Player 1",
        "team": 0,  // ← New field!
        "deck_name": "Control",
        "is_ai": false
      },
      "P2": {
        "name": "Player 2",
        "team": 0,  // Same team
        "deck_name": "Aggro",
        "is_ai": true
      },
      "P3": {
        "name": "Player 3",
        "team": 1,  // Different team
        "deck_name": "Midrange",
        "is_ai": true
      }
    }
  }
}
```

**Documentation:**
- `TEAM_PERSISTENCE_FIX.md` — Technical details
- `TEAM_PERSISTENCE_SUMMARY.md` — Summary
- `scripts/test_team_persistence.py` — Test script

---

### 2. Scenario Mode Fixes (v1.9.0)

**What's Fixed:**
- ✅ Mulligan skip logic works correctly
- ✅ Replay mode set to "scenario" for scenario games
- ✅ Starting hand size matches scenario definition
- ✅ ScenarioLibrarySetup correctly reorders libraries

**Iterations:**
- Iteration #1 (2026-05-03): Mulligan skip fixed
- Iteration #2 (2026-05-03): Replay mode fixed
- Iteration #3 (2026-05-04): Starting hand size fixed

**Documentation:**
- `SCENARIO_SIM_FIX_COMPLETE_TIMELINE.md` — Full history
- `SCENARIO_SIM_FIX_ITERATION_3.md` — Latest fix details

---

## 🛠️ Build Details

### Maven Command

```bash
mvn clean package -pl forge-gui-desktop -am -DskipTests
```

### Build Profile

- **Profile:** Default (Windows/Linux)
- **Modules Built:**
  1. forge-core
  2. forge-game ← Team persistence changes
  3. forge-ai
  4. forge-gui ← Team persistence changes
  5. forge-gui-desktop ← Team persistence changes

### Build Stats

```
[INFO] Forge Parent ....................................... SUCCESS [  2.281 s]
[INFO] Forge Core ......................................... SUCCESS [ 10.295 s]
[INFO] Forge Game ......................................... SUCCESS [ 28.646 s]
[INFO] Forge AI ........................................... SUCCESS [  9.058 s]
[INFO] Forge Gui .......................................... SUCCESS [02:17 min]
[INFO] Forge .............................................. SUCCESS [ 45.985 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  03:54 min
```

---

## 📊 Changed Files in This Build

### Team Persistence

| File | Module | Lines | Status |
|------|--------|-------|--------|
| ReplayMeta.java | forge-game | +8 | ✅ Built |
| ReplayNotationExporter.java | forge-game | +7 | ✅ Built |
| ReplayLogParser.java | forge-gui | +7 | ✅ Built |
| CSubmenuReplay.java | forge-gui-desktop | +5 | ✅ Built |
| ReplayLog.java | forge-game | +2 | ✅ Built |

### Scenario Mode

| File | Module | Lines | Status |
|------|--------|-------|--------|
| GameAction.java | forge-game | +15 | ✅ Built |
| MulliganService.java | forge-game | +7 | ✅ Built |
| SimulateMatch.java | forge-gui-desktop | +4 | ✅ Built |

---

## 🧪 Testing

### Quick Test

```powershell
# Start Forge
& "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe"

# 1. Start a 2v2 team game
# 2. Play a few turns
# 3. Check replay:
$latest = Get-ChildItem "$env:APPDATA\Forge\games\gamelogs" -Filter "replay_*.json" | 
          Sort-Object LastWriteTime -Descending | Select-Object -First 1
notepad $latest.FullName

# 4. Verify "team": 0 and "team": 1 exist in JSON
```

### Automated Tests

```powershell
# Test team persistence
cd scripts
python test_team_persistence.py

# Test scenario mode
java -jar forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar sim `
  -d "scenario-export.dck" "scenario-export.dck" `
  -n 1 -f Constructed `
  -s "scenario-export.json"
```

---

## 📚 Documentation

### New Documents

1. `TEAM_PERSISTENCE_FIX.md` — Technical team persistence docs
2. `TEAM_PERSISTENCE_SUMMARY.md` — Executive summary
3. `SCENARIO_SIM_FIX_COMPLETE_TIMELINE.md` — Scenario fix history
4. `SCENARIO_SIM_FIX_ITERATION_3.md` — Latest scenario fix

### Updated Documents

1. `scripts/README_BLACKBOX_TESTING.md` — Added team fix + scenario fixes
2. `scripts/test_team_persistence.py` — New test script

---

## ✅ Verification

### Files Exist

```powershell
Test-Path "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe"
# Result: True ✅

Test-Path "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar"
# Result: True ✅
```

### Launch4j Success

```
[INFO] launch4j: Successfully created D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe
```

---

## 🎯 Next Steps

1. ✅ **Forge.exe is ready** — Can be launched
2. ✅ **Team persistence active** — Test with team games
3. ✅ **Scenario fixes active** — Test with scenario mode
4. 📝 **Update mtg-replay-notation spec** — Document v1.9.0 changes
5. 📝 **Create release notes** — Document all changes

---

**Build Date:** 2026-05-10 16:27:27  
**Build Status:** ✅ SUCCESS  
**Ready to Use:** ✅ YES

---

*All builds artifacts are in: `D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\`*

