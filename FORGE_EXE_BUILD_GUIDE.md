# Forge Executable — Build & Launch Guide

**Date:** 2026-05-10  
**Issue Resolved:** ✅ forge.exe not found → Now built successfully

---

## ✅ Problem Gelöst

**Original Error:**
```
❌ Forge executable not found at: 
D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe
```

**Solution Applied:**
```bash
mvn clean package -pl forge-gui-desktop -am -DskipTests
```

**Result:**
```
✅ BUILD SUCCESS
✅ forge.exe created: 294 KB
✅ All artifacts generated
```

---

## 📦 Build Artifacts

Nach dem erfolgreichen Build sind folgende Dateien verfügbar:

| File | Size | LastWriteTime | Purpose |
|------|------|---------------|---------|
| **forge.exe** | 294 KB | 2026-05-10 16:26:57 | Windows Executable |
| **forge-gui-desktop-*-jar-with-dependencies.jar** | 238 MB | 2026-05-10 16:27:25 | Standalone JAR |
| forge-gui-desktop-*.jar | 2 MB | 2026-05-10 16:26:56 | Main JAR |
| forge.cmd | 476 B | 2026-05-10 16:26:57 | Windows Batch |
| forge.sh | 184 B | 2026-05-10 16:26:57 | Linux Shell |
| forge.command | 179 B | 2026-05-10 16:26:57 | macOS Launcher |

---

## 🚀 Wie starten

### **Methode 1: PowerShell-Launcher (Empfohlen)**

```powershell
.\start-forge.ps1
```

Dieser Launcher:
- ✅ Findet forge.exe automatisch
- ✅ Zeigt Build-Informationen
- ✅ Startet Forge sofort

---

### **Methode 2: Direkt über Executable**

```cmd
D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe
```

Oder im Explorer:
```
forge-gui-desktop\target\forge.exe (Doppelklick)
```

---

### **Methode 3: Via JAR (plattformunabhängig)**

```bash
java -jar D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar
```

---

## 🔨 Build-Prozess (falls neu bauen nötig)

### Vollständiger Build (mit .exe)

```powershell
cd D:\Daten\SoftwareProjekte\Forge\forge
mvn clean package -pl forge-gui-desktop -am -DskipTests
```

**Dauer:** ~4 Minuten  
**Ausgabe:** Alle Artifacts inklusive forge.exe

---

### Schneller Build (nur Compile, keine .exe)

```powershell
mvn clean compile -pl forge-gui-desktop -am -DskipTests
```

**Dauer:** ~2.5 Minuten  
**Ausgabe:** Nur .class Dateien, keine Executables

**⚠️ Warnung:** Dieser Befehl erstellt KEINE forge.exe!

---

## ✅ Verification

### Prüfe ob forge.exe existiert

```powershell
Test-Path "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe"
```

**Erwartete Ausgabe:** `True`

---

### Zeige alle Build-Artifacts

```powershell
Get-ChildItem "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target" -Filter "forge*" | 
    Select-Object Name, Length, LastWriteTime | 
    Format-Table -AutoSize
```

---

## 🎮 Verwendung

### GUI starten

```powershell
# Via Launcher (empfohlen)
.\start-forge.ps1

# Oder direkt
.\forge-gui-desktop\target\forge.exe
```

---

### Simulation ausführen

```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar sim `
  -d "Deck1.dck" "Deck2.dck" `
  -n 10 `
  -f commander
```

---

### Scenario ausführen

```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar scenario `
  "scenario.json"
```

---

### Replay abspielen

```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar replay `
  "replay_log.json"
```

---

## 🛠️ Unterschied: compile vs. package

| Befehl | Dauer | Erzeugt forge.exe? | Use Case |
|--------|-------|-------------------|----------|
| `mvn compile` | ~2.5 min | ❌ NEIN | Schnelles Testen von Code-Änderungen |
| `mvn package` | ~4 min | ✅ JA | Release-Build, Distribution |

**Regel:** Verwende `package` wenn du Forge starten willst!

---

## 📚 Dokumentation

- **`BUILD_STATUS_2026-05-10.md`** — Vollständiger Build-Status
- **`TEAM_PERSISTENCE_SUMMARY.md`** — Team-Feature (v1.9.0)
- **`SCENARIO_SIM_FIX_COMPLETE_TIMELINE.md`** — Scenario-Fixes

---

## ✨ Was ist neu in diesem Build?

### 1. Team Persistence (v1.9.0)

Teams werden jetzt in Replays gespeichert:

```json
{
  "meta": {
    "players": {
      "P1": {"team": 0},
      "P2": {"team": 0},
      "P3": {"team": 1}
    }
  }
}
```

---

### 2. Scenario Mode Fixes

- ✅ Mulligan skip funktioniert
- ✅ Replay mode = "scenario"
- ✅ Starthand-Größe korrekt
- ✅ Library-Reordering funktioniert

---

## 🔗 Schnellzugriff

**Forge starten:**
```powershell
.\start-forge.ps1
```

**Build neu erstellen:**
```powershell
mvn clean package -pl forge-gui-desktop -am -DskipTests
```

**Logs öffnen:**
```powershell
explorer "$env:APPDATA\Forge\games\gamelogs"
```

---

**Build Status:** ✅ SUCCESS  
**forge.exe Status:** ✅ AVAILABLE  
**Ready to Use:** ✅ YES

**Build Date:** 2026-05-10 16:27:27

