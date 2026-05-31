# Commander AI Simulation & Statistik-Analyse

Vollständiger Workflow für Commander Deck-Testing mit 100+ AI-Simulationen und detaillierter Statistik-Auswertung.

**📖 Architecture:** [Simulation Analytics Architecture](docs/SIMULATION_ANALYTICS_ARCHITECTURE.md) - Separated logging & analytics design (recommended for v2.0)

---

## Quick Start

### Local (From Forge Directory)

```powershell
# 1. Erstelle Commander Decklist JSON
# Siehe: docs/COMMANDER_DECK_REQUIREMENTS.md

# 2. Konvertiere JSON zu .dck
python convert_decklist_to_dck.py my_deck.json

# 3. Führe 100 Simulationen aus
.\run_commander_simulation.ps1 -Deck1 "my_deck" -Games 100

# 4. Analysiere Statistiken
python analyze_commander_stats.py

# 5. Öffne Report
# commander_simulation_report.json
```

### External (From Anywhere)

```powershell
# Full absolute paths - use from any directory

# 1. Convert JSON to .dck (from anywhere)
python "D:\Daten\SoftwareProjekte\Forge\forge\convert_decklist_to_dck.py" "C:\my_decks\krenko.json"

# 2. Run simulation (full JAR path)
$jar = "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
java -jar "$jar" sim -d "my_deck.dck" "opponent.dck" -n 100 -f commander -c 180

# 3. Analyze stats (from anywhere)
python "D:\Daten\SoftwareProjekte\Forge\forge\analyze_commander_stats.py"
```

📍 **See:** [External Command-Line Calls](#external-commandline-calls) for complete documentation

---

## External Command-Line Calls

Run Forge simulations from **anywhere on your system** with absolute paths.

### 📍 Key Paths

| Component | Absolute Path |
|-----------|---------------|
| **Forge JAR** | `D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar` |
| **Deck Directory** | `C:\Users\Nutzer\AppData\Roaming\Forge\decks\commander\` |
| **Replay Logs** | `C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\` |
| **Convert Script** | `D:\Daten\SoftwareProjekte\Forge\forge\convert_decklist_to_dck.py` |
| **Analyze Script** | `D:\Daten\SoftwareProjekte\Forge\forge\analyze_commander_stats.py` |
| **Simulation Script** | `D:\Daten\SoftwareProjekte\Forge\forge\run_commander_simulation.ps1` |

---

### 🎯 Direct JAR Call (No Scripts)

**Run simulation directly without PowerShell script:**

```bash
java -jar "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar" sim -d "deck1.dck" "deck2.dck" -n 100 -f commander -c 180
```

**Important:** Deck names must be found in `%APPDATA%\Forge\decks\commander\`

**Parameters:**
- `-d <deck1> <deck2>` - Both decks after SINGLE `-d` flag
- `-n <games>` - Number of games
- `-f <format>` - Format (commander, constructed, etc.)
- `-c <seconds>` - Timeout per game
- `-q` - Quiet mode

---

### 💻 PowerShell (Full Pipeline)

```powershell
# Store JAR path
$jar = "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
$deck1 = "my_deck"
$deck2 = "opponent_deck"
$games = 100
$timeout = 180
$format = "commander"

# Run simulation
java -jar "$jar" sim -d "$deck1.dck" "$deck2.dck" -n $games -f $format -c $timeout

# Wait for completion and analyze
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Simulation complete!"
    python "D:\Daten\SoftwareProjekte\Forge\forge\analyze_commander_stats.py"
}
```

---

### 🖥️ Command Prompt (Batch Script)

**`run_sim.bat`:**

```batch
@echo off
setlocal enabledelayedexpansion

set JAR=D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar
set DECK1=%1
set DECK2=%2
set GAMES=%3
set TIMEOUT=%4

if "%DECK1%"=="" (
    echo Usage: run_sim.bat deck1 deck2 games timeout
    echo Example: run_sim.bat my_deck opponent_deck 100 180
    exit /b 1
)

if "%DECK2%"=="" set DECK2=%DECK1%
if "%GAMES%"=="" set GAMES=100
if "%TIMEOUT%"=="" set TIMEOUT=180

echo Starting simulation...
java -jar "%JAR%" sim -d "%DECK1%.dck" "%DECK2%.dck" -n %GAMES% -f commander -c %TIMEOUT%

echo Simulation complete!
```

**Usage from anywhere:**
```batch
D:\Daten\SoftwareProjekte\Forge\forge\run_sim.bat my_deck opponent_deck 100 180
```

---

### 🐍 Python Integration

**`run_forge_sim.py`:**

```python
import subprocess
import json
from pathlib import Path

class ForgeSimulator:
    """Run Forge simulations from external code"""
    
    JAR_PATH = r"D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
    DECK_DIR = Path(r"C:\Users\Nutzer\AppData\Roaming\Forge\decks\commander")
    LOGS_DIR = Path(r"C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs")
    
    def run_simulation(self, deck1, deck2, games=10, timeout=180):
        """
        Run Commander simulation
        
        Args:
            deck1: First deck name (without .dck)
            deck2: Second deck name (without .dck)
            games: Number of games to simulate
            timeout: Timeout per game in seconds
        
        Returns:
            Subprocess return code
        """
        cmd = [
            "java",
            "-jar", self.JAR_PATH,
            "sim",
            "-d", f"{deck1}.dck", f"{deck2}.dck",
            "-n", str(games),
            "-f", "commander",
            "-c", str(timeout)
        ]
        
        print(f"🎮 Running simulation: {deck1} vs {deck2} ({games} games)")
        print(f"   Command: {' '.join(cmd)}")
        
        result = subprocess.run(cmd, cwd=self.DECK_DIR.parent)
        return result.returncode
    
    def get_latest_logs(self, n=5):
        """Get latest N replay logs"""
        logs = sorted(
            self.LOGS_DIR.glob("replay_Commander_*.json"),
            key=lambda x: x.stat().st_mtime,
            reverse=True
        )[:n]
        return logs

# Example usage
if __name__ == "__main__":
    sim = ForgeSimulator()
    
    # Run simulation
    ret = sim.run_simulation("Krenko_Mob_Boss", "Atraxa_Superfriends", games=5)
    
    if ret == 0:
        print("✅ Simulation successful!")
        latest = sim.get_latest_logs(3)
        for log in latest:
            print(f"   - {log.name}")
```

**Usage:**
```python
python run_forge_sim.py
```

---

### 🐳 Docker (Optional)

If running Forge in a container:

```dockerfile
FROM openjdk:22-jdk-slim

# Install Python for analysis scripts
RUN apt-get update && apt-get install -y python3 python3-pip

# Copy Forge
COPY forge/ /forge

WORKDIR /forge

# Run simulation
CMD ["java", "-jar", "forge-gui-desktop/target/forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar", "sim", "-d", "deck1.dck", "deck2.dck", "-n", "100", "-f", "commander", "-c", "180"]
```

---

### 📊 Full External Workflow

**Script: `full_analysis.ps1`**

```powershell
param(
    [string]$Deck1 = "my_deck",
    [string]$Deck2 = "opponent_deck",
    [int]$Games = 100,
    [int]$Timeout = 180
)

$jarPath = "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
$scriptDir = "D:\Daten\SoftwareProjekte\Forge\forge"
$deckDir = "$env:APPDATA\Forge\decks\commander"

Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "🎮 Forge Commander Simulation" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan

Write-Host "`n📊 Configuration:" -ForegroundColor Yellow
Write-Host "   Deck 1: $Deck1"
Write-Host "   Deck 2: $Deck2"
Write-Host "   Games: $Games"
Write-Host "   Timeout: $Timeout seconds"

# 1. Run Simulation
Write-Host "`n🎬 Starting simulation..." -ForegroundColor Green
$startTime = Get-Date
java -jar "$jarPath" sim -d "$Deck1.dck" "$Deck2.dck" -n $Games -f commander -c $Timeout
$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ Simulation completed in $([math]::Round($duration, 1))s" -ForegroundColor Green
    
    # 2. Analyze Statistics
    Write-Host "`n📈 Analyzing statistics..." -ForegroundColor Green
    Push-Location $scriptDir
    python analyze_commander_stats.py
    Pop-Location
    
    # 3. Show Report Location
    $reportPath = Join-Path $scriptDir "commander_simulation_report.json"
    Write-Host "`n📄 Report saved to: $reportPath" -ForegroundColor Cyan
    
} else {
    Write-Host "`n❌ Simulation failed with exit code: $LASTEXITCODE" -ForegroundColor Red
}
```

**Usage from anywhere:**
```powershell
D:\Daten\SoftwareProjekte\Forge\forge\full_analysis.ps1 -Deck1 "Krenko_Mob_Boss" -Deck2 "Atraxa_Superfriends" -Games 50
```

---

### ⚙️ Environment Variables (Optional)

Set these for easier access:

```powershell
# Add to $PROFILE
$env:FORGE_HOME = "D:\Daten\SoftwareProjekte\Forge\forge"
$env:FORGE_JAR = "$env:FORGE_HOME\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
$env:FORGE_DECKS = "$env:APPDATA\Forge\decks\commander"

# Now use:
java -jar $env:FORGE_JAR sim -d deck1.dck deck2.dck -n 100 -f commander
```

---

### 🔗 CI/CD Integration

**GitHub Actions Example:**

```yaml
name: Forge Simulation

on: [push]

jobs:
  simulate:
    runs-on: windows-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v3
      
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '22'
      
      - name: Run Simulation
        run: |
          $jar = "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
          java -jar "$jar" sim -d "deck1.dck" "deck2.dck" -n 100 -f commander -c 180
      
      - name: Upload Results
        uses: actions/upload-artifact@v3
        with:
          name: simulation-results
          path: '${{ env:APPDATA }}\Forge\games\gamelogs\'
```

---



### 1. Commander Decklist JSON erstellen

Erstellen Sie eine JSON-Datei nach der [Commander Decklist Specification v1.0.0](mtg-replay-notation/spec/commander-decklist-spec.md).

**Minimal-Beispiel** (`krenko.json`):

```json
{
  "format": "mtg-commander-decklist",
  "version": "1.0.0",
  "meta": {
    "deck_name": "Krenko Mob Boss",
    "format": "Commander",
    "colors": ["R"]
  },
  "commander": [
    {
      "quantity": 1,
      "name": "Krenko, Mob Boss",
      "edition": "M13",
      "collector_number": "138",
      "primary_mechanic": "token"
    }
  ],
  "main": [
    {
      "quantity": 1,
      "name": "Sol Ring",
      "edition": "C21",
      "collector_number": "263",
      "primary_mechanic": "ramp"
    }
    // ... 98 weitere Karten
  ]
}
```

**Anforderungen:**
- ✅ `commander`: 1 Karte (oder 2 bei Partner)
- ✅ `main`: 99 Karten (oder 98 bei Companion)
- ✅ Jede Karte: `name`, `edition`, `collector_number`, `primary_mechanic`
- ✅ Optional aber empfohlen: `deck_rules.mulligan`, `deck_rules.combos`

📖 **Vollständige Dokumentation:** [docs/COMMANDER_DECK_REQUIREMENTS.md](docs/COMMANDER_DECK_REQUIREMENTS.md)

---

### 2. Decklist zu .dck konvertieren

```powershell
python convert_decklist_to_dck.py krenko.json
```

**Output:**
- `.dck`-Datei wird erstellt in: `%APPDATA%\Forge\decks\commander\Krenko_Mob_Boss.dck`
- Deck-Hash wird berechnet (für Replay-Referenzierung)
- Validierung gegen Commander-Regeln (99+1 Karten, etc.)

**Optionen:**
```powershell
# Custom Output-Verzeichnis
python convert_decklist_to_dck.py krenko.json D:\my_decks
```

---

### 3. Simulation ausführen

```powershell
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Games 100
```

**Parameter:**

| Parameter | Default | Beschreibung |
|-----------|---------|--------------|
| `-Deck1` | (required) | Ihr Deck (ohne `.dck`-Extension) |
| `-Deck2` | (Mirror) | Gegner-Deck (Default: Mirror Match) |
| `-Games` | `100` | Anzahl Spiele |
| `-Timeout` | `180` | Timeout pro Spiel in Sekunden |
| `-Quiet` | (flag) | Minimale Konsolen-Ausgabe |

**Beispiele:**

```powershell
# Mirror Match (Deck vs. sich selbst)
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Games 100

# Matchup-Test (2 verschiedene Decks)
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Deck2 "Atraxa_Superfriends" -Games 100

# Schnelltest (10 Spiele)
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Games 10
```

**Output:**
- JSON Replay-Logs: `%APPDATA%\Forge\games\gamelogs\replay_Commander_*.json`
- Jedes Spiel = 1 JSON-Datei mit vollständigen `game_summary` und `per_turn_summary` Statistiken

---

### 4. Statistiken analysieren

```powershell
python analyze_commander_stats.py
```

Das Skript:
1. Findet alle Commander Replay-Logs in `%APPDATA%\Forge\games\gamelogs\`
2. Fragt nach Anzahl zu verarbeitender Logs (Default: 100)
3. Lädt und aggregiert alle `game_summary` Metriken
4. Berechnet Durchschnitt, Median, Standardabweichung
5. Exportiert `commander_simulation_report.json`

**Optionen:**

```powershell
# Custom Log-Verzeichnis
python analyze_commander_stats.py D:\my_logs

# Custom Output-Datei
python analyze_commander_stats.py D:\my_logs my_report.json
```

**Output-Beispiel** (Konsole):

```
📊 Summary Statistics
══════════════════════════════════════════════════════════════════════

🎮 Player: P1
   Win Rate:          58.0% (58W/42L)
   Avg Turns:         12.4 (±3.1)
   Avg Damage Dealt:  145.2 (±35.7)
   Avg Spell Velocity:1.85 spells/turn
   Avg Missed Lands:  1.20 (±0.80)
   Median Peak Mana:  7
```

---

### 5. Report analysieren

Öffnen Sie `commander_simulation_report.json` in einem JSON-Viewer oder Code-Editor.

**Struktur:**

```json
{
  "format": "commander-simulation-report",
  "version": "1.0.0",
  "meta": {
    "generated_at": "2026-04-07T14:30:00Z",
    "total_games": 100,
    "players": ["P1", "P2"]
  },
  "aggregate_stats": {
    "P1": {
      "win_rate": 0.58,
      "avg_turns": 12.4,
      "avg_damage_dealt": 145.2,
      "avg_spell_velocity": 1.85,
      // ... alle Metriken
    }
  },
  "per_game_details": [
    {
      "game_id": "...",
      "winner": "P1",
      "total_turns": 10,
      "players": { /* ... */ }
    }
  ]
}
```

📖 **Alle Metriken erklärt:** [docs/COMMANDER_METRICS_DOCUMENTATION.md](docs/COMMANDER_METRICS_DOCUMENTATION.md)

---

## Gemessene Metriken (Überblick)

### Game-Wide Statistiken

- **Win-Rate:** `wins / total_games`
- **Turn-Count:** `avg_turns`, `median_turns`, `stdev_turns`
- **Damage:** `avg_damage_dealt`, `avg_damage_received`
- **Card Flow:** `avg_cards_drawn`, `card_draw_rate`, `total_spells_cast`, `spell_velocity`
- **Mana:** `missed_land_drops`, `peak_mana`, `total_lands_played`
- **Creatures:** `total_creatures_played`
- **Life:** `starting_life`, `ending_life`, `life_delta`

### Per-Turn Statistiken

- **Actions:** `lands_played`, `spells_cast`, `abilities_activated`, `cards_drawn`
- **Board State:** `land_count`, `available_mana`, `creatures_on_battlefield`, `permanents_on_battlefield`
- **Resources:** `life`, `cards_in_hand`
- **Combat:** `damage_dealt`, `damage_taken`
- **Ratings:** `land_drop_rating` ("bad", "good", "super")

### Konsistenz-Metriken

- **Standardabweichungen:** `stdev_turns`, `stdev_damage_dealt`, `stdev_spell_velocity`, `stdev_missed_land_drops`

---

## Optimierungsziele

### Maximize (höher = besser)

✅ `win_rate`  
✅ `avg_damage_dealt`  
✅ `avg_spell_velocity`  
✅ `median_peak_mana`  
✅ `avg_cards_drawn`

### Minimize (niedriger = besser)

❌ `avg_missed_land_drops`  
❌ `avg_damage_received`  
❌ `stdev_turns` (für Konsistenz)  
❌ `stdev_missed_land_drops` (für Mana-Konsistenz)

---

## Troubleshooting

### ✅ Verify External Setup

Before running external commands, verify paths exist:

```powershell
# Check JAR
Test-Path "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"

# Check Deck Directory
Test-Path "$env:APPDATA\Forge\decks\commander"

# List decks
Get-ChildItem "$env:APPDATA\Forge\decks\commander\*.dck" | Select-Object Name
```

---

### Problem: "Could not load deck"

**Ursache:** `.dck`-Datei nicht in `%APPDATA%\Forge\decks\commander\`

**Lösung:**
```powershell
# Prüfe Verzeichnis
ls $env:APPDATA\Forge\decks\commander\

# Konvertiere JSON erneut
python "D:\Daten\SoftwareProjekte\Forge\forge\convert_decklist_to_dck.py" my_deck.json
```

### Problem: JAR-Pfad nicht gefunden (External Call)

**Ursache:** Absoluter Pfad falsch oder JAR nicht gebaut

**Lösung:**
```powershell
# Stelle sicher, dass JAR existiert
$jar = "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
Test-Path $jar

# Falls nicht vorhanden, baue Forge
Push-Location "D:\Daten\SoftwareProjekte\Forge\forge"
mvn clean package -pl forge-gui-desktop -am -DskipTests
Pop-Location
```

### Problem: Simulation hängt

**Ursache:** Komplexe Board-States oder Infinite-Loops

**Lösung:**
```powershell
# Erhöhe Timeout auf 5 Minuten
$jar = "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
java -jar "$jar" sim -d "deck1.dck" "deck2.dck" -n 5 -f commander -c 300
```

### Problem: "No replay logs found"

**Ursache:** Logs wurden nicht gespeichert (möglicherweise Fehler beim Spiel)

**Lösung:**
1. Prüfe `%APPDATA%\Forge\games\gamelogs\` manuell
2. Führe Test-Simulation mit 1 Spiel aus: `-n 1`
3. Prüfe Forge-Log für Fehler: `%APPDATA%\Forge\forge.log`

### Problem: "Access denied" auf Python-Skripte

**Ursache:** Skript-Ausführung blockiert durch ExecutionPolicy

**Lösung (PowerShell):**
```powershell
# Python-Skript direkt aufrufen (nicht als .ps1)
python "D:\Daten\SoftwareProjekte\Forge\forge\analyze_commander_stats.py"

# Oder ExecutionPolicy ändern (einmalig)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

---

## Datei-Übersicht

| Datei | Zweck |
|-------|-------|
| `convert_decklist_to_dck.py` | JSON → .dck Konverter |
| `run_commander_simulation.ps1` | Batch-Simulation Runner |
| `analyze_commander_stats.py` | Statistik-Extraktor & Report-Generator |
| `docs/COMMANDER_DECK_REQUIREMENTS.md` | Deck-Anforderungen & Best Practices |
| `docs/COMMANDER_METRICS_DOCUMENTATION.md` | Vollständige Metrik-Dokumentation |
| `docs/SIMULATION_ANALYTICS_ARCHITECTURE.md` | **NEW:** Architektur für v2.0 (reduced logs, separated analytics) |
| `docs/SIMULATION_STATS_FORMAT.md` | **NEW:** JSON Schema für `simulation_stats_*.json` Format |
| `commander_simulation_report.json` | Output: Aggregierte Statistiken |

---

## Nächste Schritte

### A. Deck-Optimierung

1. Analysiere `avg_missed_land_drops` → Mana-Base anpassen
2. Analysiere `avg_spell_velocity` → CMC-Kurve optimieren
3. Analysiere `stdev_*` Metriken → Konsistenz verbessern
4. Passe `deck_rules.mulligan` an → Bessere Opening Hands

### B. Matchup-Analyse

1. Führe Simulationen gegen verschiedene Decks aus
2. Vergleiche `win_rate` pro Matchup
3. Identifiziere problematische Matchups
4. Sideboard-Karten testen (optional)

### C. Fortgeschrittene Analyse

1. Extrahiere `per_turn_summary` für Turn-by-Turn-Analyse
2. Korreliere Combo-Assembling mit Win-Rate (zukünftig)
3. Mana-Curve-Optimierung basierend auf `available_mana` Progression

---

**Version:** 1.0.0  
**Datum:** 2026-04-07  
**Lizenz:** MIT







