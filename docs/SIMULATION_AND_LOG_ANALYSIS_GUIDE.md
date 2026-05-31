# Simulation & Log-Analyse Guide — Forge

**Version:** 1.0.0  
**Datum:** 2026-05-03  
**Status:** Ready for Use

---

## 📚 Übersicht

Diese Anleitung zeigt den **kompletten Workflow** vom Erstellen einer Decklist über das Starten einer Simulation bis zur Analyse der Game-Logs.

### Was Sie lernen werden:

1. **Decklist-Vorbereitung** — Verschiedene Deck-Formate und wie man sie erstellt
2. **Simulation starten** — Verschiedene CLI-Modi (sim, replay, scenario)
3. **Game-Logs finden** — Wo Forge die Logs speichert
4. **Log-Analyse** — Tools und Skripte zur Auswertung
5. **Troubleshooting** — Häufige Probleme und Lösungen

---

## 🎯 Phase 1: Decklist-Vorbereitung

### 1.1 Deck-Formate

Forge unterstützt mehrere Deck-Formate:

| Format | Dateityp | Verwendung | Speicherort |
|--------|----------|------------|-------------|
| **Forge Deck File** | `.dck` | ✅ Native Forge-Decks (direkt verwendbar) | `%APPDATA%\Forge\decks\<format>\` |
| **JSON Decklist** | `.json` | Source-Datei mit Metadata (muss konvertiert werden) | Projektverzeichnis (`my_decks/`) |
| **Text Decklist** | `.txt` | Import-Format | Beliebig |

### 1.2 Forge Deck File (.dck) — Standard-Format

Dies ist das **bevorzugte Format** für Simulationen.

**Speicherort-Beispiel:**
```
C:\Users\Username\AppData\Roaming\Forge\decks\
├── constructed\
│   ├── Aggro_Red.dck
│   └── Control_Blue.dck
├── commander\
│   ├── Krenko_Mob_Boss.dck
│   └── Atraxa_Superfriends.dck
└── limited\
    └── Draft_Deck_1.dck
```

**Format-Beispiel** (`Aggro_Red.dck`):
```
[metadata]
Name=Aggro Red
Deck Type=constructed

[Main]
4 Lightning Bolt
4 Goblin Guide
4 Monastery Swiftspear
20 Mountain
4 Eidolon of the Great Revel
4 Lava Spike
4 Rift Bolt
4 Skullcrack
4 Boros Charm
4 Searing Blaze
4 Soul-Scar Mage
```

**Commander-Format** (`Krenko_Mob_Boss.dck`):
```
[metadata]
Name=Krenko Mob Boss
Deck Type=Commander

[Commander]
1 Krenko, Mob Boss

[Main]
1 Sol Ring
1 Lightning Bolt
1 Command Tower
1 Blood Crypt
// ... 95 weitere Karten (gesamt 99)
```

**Anforderungen:**
- ✅ Constructed: Mindestens 60 Karten
- ✅ Commander: 1 Commander + genau 99 Main-Karten
- ✅ Korrekte `Deck Type` (constructed, Commander, limited)

### 1.3 JSON Decklist — Erweitert mit Metadata

**Verwendung:** Commander-Decks mit zusätzlichen Informationen (Mulligan-Regeln, Mechaniken, etc.)

**❗ Wichtig:** JSON-Decklists sind **Source-Dateien** für erweiterte Features. Sie müssen zu `.dck` konvertiert werden (siehe 1.4), bevor Forge sie verwenden kann.

**Speicherort (Projektverzeichnis):**
```
<forge-projekt>\my_decks\my_deck.json

Beispiel:
D:\Daten\SoftwareProjekte\Forge\forge\my_decks\my_deck.json
```

**Format-Beispiel:**
```json
{
  "format": "mtg-commander-decklist",
  "version": "1.0.0",
  "meta": {
    "deck_id": "krenko-mob-boss-v1",
    "deck_name": "Krenko Mob Boss",
    "format": "Commander",
    "colors": ["R"],
    "created": "2026-05-03",
    "author": "Your Name"
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
  ],
  "deck_rules": {
    "mulligan": {
      "card_values": {
        "land": 1.0,
        "cmc_0_to_2": 0.8,
        "cmc_3": 0.5,
        "other": 0.3
      },
      "thresholds": [
        {"round": 0, "hand_size": 7, "min_value": 3.5},
        {"round": 1, "hand_size": 6, "min_value": 3.0}
      ]
    }
  }
}
```

**📖 Siehe auch:** 
- `docs/COMMANDER_DECK_REQUIREMENTS.md` — Vollständige JSON-Spezifikation
- `mtg-replay-notation/spec/commander-decklist-spec.md` — Offizielle Spec

### 1.4 Decklist konvertieren (JSON → .dck)

Falls Sie eine JSON-Decklist haben, konvertieren Sie sie zu `.dck`:

```powershell
cd D:\Daten\SoftwareProjekte\Forge\forge
python convert_decklist_to_dck.py my_decks/my_deck.json
```

**Output:**
```
✓ Converted: C:\Users\...\AppData\Roaming\Forge\decks\commander\Krenko_Mob_Boss.dck
```

---

## 🎮 Phase 2: Simulation starten

Forge unterstützt verschiedene CLI-Modi für Simulationen.

### 2.1 Desktop JAR finden

**Nach dem Build (Maven):**
```
forge-gui-desktop\target\forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar
```

**Oder nach Release:**
```
forge-gui-desktop\target\forge-gui-desktop-2.0.12-jar-with-dependencies.jar
```

**Shortcut-Variable (PowerShell):**
```powershell
$jar = "forge-gui-desktop\target\forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar"
```

### 2.2 Simulation-Modi Übersicht

| Modus | Verwendung | Befehl |
|-------|------------|--------|
| **sim** | Headless AI-Simulation | `java -jar $jar sim ...` |
| **replay** | Interaktives Replay (GUI) | `java -jar $jar replay <json>` |
| **scenario** | Scenario-Test (GUI) | `java -jar $jar scenario <json>` |
| **parse** | Card-Validierung | `java -jar $jar parse` |

---

### 2.3 Modus: `sim` — Headless AI-Simulation

**Zweck:** Automatisierte Spiele zwischen zwei AI-Decks (headless, kein GUI).

#### Basis-Syntax

```powershell
java -jar $jar sim -d deck1.dck deck2.dck -n <games> -f <format> [options]
```

#### Parameter

| Parameter | Pflicht | Default | Beschreibung |
|-----------|---------|---------|--------------|
| `-d` | ✅ | — | Zwei Deck-Dateien (`.dck`) |
| `-n` | ❌ | 1 | Anzahl der Spiele |
| `-m` | ❌ | 1 | Match-Größe (Best-of-N) |
| `-f` | ❌ | constructed | Format (`constructed`, `commander`, `limited`) |
| `-c` | ❌ | 180 | Timeout pro Spiel (Sekunden) |
| `-q` | ❌ | false | Quiet-Mode (minimale Ausgabe) |
| `-r` | ❌ | — | Replay-Log für deterministische Simulation |

#### Beispiele

**✅ Einfaches Spiel (1 Game):**
```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar sim `
  -d "$env:APPDATA\Forge\decks\constructed\Aggro_Red.dck" `
     "$env:APPDATA\Forge\decks\constructed\Control_Blue.dck" `
  -n 1 -f constructed
```

**✅ Commander Simulation (100 Games, Quiet):**
```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar sim `
  -d "$env:APPDATA\Forge\decks\commander\Krenko_Mob_Boss.dck" `
     "$env:APPDATA\Forge\decks\commander\Atraxa_Superfriends.dck" `
  -n 100 -f commander -q
```

**✅ Mit Timeout erhöhen (für komplexe Decks):**
```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar sim `
  -d deck1.dck deck2.dck `
  -n 10 -c 300
```

**✅ Mit Replay (deterministisch):**
```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar sim `
  -d deck1.dck deck2.dck `
  -n 1 -r "C:\path\to\replay_log.json"
```

#### 💡 Batch-Simulation (PowerShell-Skript)

Für Commander: Verwenden Sie das fertige Skript:

```powershell
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Deck2 "Atraxa_Superfriends" -Games 100
```

**Siehe:** `COMMANDER_SIMULATION_COMPLETE_GUIDE.md` für Details.

---

### 2.4 Modus: `replay` — Interaktives Replay

**Zweck:** Spielt ein aufgezeichnetes Spiel interaktiv nach (mit GUI).

#### Syntax

```powershell
java -jar $jar replay <replay_log.json>
```

#### Beispiel

```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar replay `
  "C:\Users\...\AppData\Roaming\Forge\games\gamelogs\replay_Commander_2026-05-03_14-30-00.json"
```

#### Features

- ✅ Exakte Wiederherstellung: Library-Reihenfolge wird aus Replay-Log rekonstruiert
- ✅ No Mulligan: AI überspringt Mulligan (Hand ist aus Replay bekannt)
- ✅ GUI: Vollständige grafische Oberfläche
- ✅ Re-Replay-Check: Warnung bei bereits abgespielten Replays

**📖 Siehe:** `CLI-REPLAY.md` für Details.

---

### 2.5 Modus: `scenario` — Scenario-Test

**Zweck:** Testet spezifische Board-States oder Szenarien (z.B. Puzzles).

#### Syntax

```powershell
java -jar $jar scenario <scenario.json>
```

#### Beispiel

```powershell
java -jar forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar scenario `
  "D:\Daten\SoftwareProjekte\Forge\forge\docs\example_scenario_forced_sequence.json"
```

#### Features

- ✅ Definierte Starthand (`starting_hand`)
- ✅ Definierte First Draws (`first_draws`)
- ✅ Erzwungene Spielreihenfolge (`events` array)
- ✅ Perfekt für reproduzierbare Tests

**📖 Siehe:** `SCENARIO_STARTING_HAND_FORMAT.md` für Format-Details.

---

## 📂 Phase 3: Game-Logs finden

Nach der Simulation werden mehrere Log-Typen erstellt.

### 3.1 Log-Verzeichnisse

| Log-Typ | Speicherort | Dateiformat |
|---------|-------------|-------------|
| **Replay Logs** | `%APPDATA%\Forge\games\gamelogs\` | `replay_*.json` |
| **Simulation Stats** | `%APPDATA%\Forge\games\simulation_stats\` | `simulation_stats_*.json` |
| **System Logs** | `%APPDATA%\Forge\` | `forge.log` |

**Windows-Pfade:**
```
C:\Users\Username\AppData\Roaming\Forge\
├── games\
│   ├── gamelogs\
│   │   ├── replay_Commander_2026-05-03_14-30-00.json
│   │   ├── replay_Commander_2026-05-03_14-35-12.json
│   │   └── ...
│   └── simulation_stats\
│       ├── simulation_stats_20260503_143000.json
│       ├── simulation_stats_20260503_143045.json
│       └── ...
└── forge.log
```

### 3.2 Replay Logs — Vollständige Spielaufzeichnung

**Format:** MTG Replay Notation (JSON)  
**Größe:** 200-800 KB pro Spiel  
**Verwendung:** Vollständige Replay-Fähigkeit, detaillierte Analyse

**Beispiel-Inhalt:**
```json
{
  "format": "mtg-replay",
  "version": "1.8.0",
  "mode": "game",
  "meta": {
    "game_id": "game_20260503_143000",
    "timestamp": "2026-05-03T14:30:00Z",
    "game_type": "commander",
    "players": {
      "P1": {"name": "Ai(1)-Krenko", "is_ai": true},
      "P2": {"name": "Ai(2)-Atraxa", "is_ai": true}
    }
  },
  "log_l1": [
    {"i": 1, "t": "T1.UP:1", "a": "P1", "type": "DRAW", "data": {"card_id": "c42"}},
    {"i": 2, "t": "T1.MP1:1", "a": "P1", "type": "PLAY_LAND", "data": {"card_id": "c15"}}
    // ... 
  ],
  "game_summary": {
    "winner": "P1",
    "total_turns": 12,
    "duration_seconds": 145
  }
}
```

**📖 Siehe:** `mtg-replay-notation/spec/MTG-REPLAY-NOTATION.md` für vollständige Spec.

### 3.3 Simulation Stats — Kompakte Statistik

**Format:** JSON  
**Größe:** 5-10 KB pro Spiel  
**Verwendung:** Aggregate Statistiken, schnelle Analyse

**Beispiel-Inhalt:**
```json
{
  "format": "simulation-stats",
  "version": "1.0.0",
  "meta": {
    "game_id": "sim_20260503_143000",
    "timestamp": "2026-05-03T14:30:00Z"
  },
  "players": {
    "P1": {
      "deck_name": "Krenko_Mob_Boss",
      "win": true,
      "turns_played": 12,
      "damage_dealt": 158,
      "spell_velocity": 1.92,
      "missed_land_drops": 1,
      "peak_mana": 8
    },
    "P2": { /* ... */ }
  }
}
```

**📖 Siehe:** `SIMULATION_STATS_FORMAT.md` für Schema-Details.

### 3.4 Logs schnell öffnen (PowerShell)

```powershell
# Replay Logs öffnen
explorer "$env:APPDATA\Forge\games\gamelogs"

# Simulation Stats öffnen
explorer "$env:APPDATA\Forge\games\simulation_stats"

# Neueste Replay-Log öffnen
$newest = Get-ChildItem "$env:APPDATA\Forge\games\gamelogs" -Filter "replay_*.json" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
notepad $newest.FullName

# System-Log anzeigen
notepad "$env:APPDATA\Forge\forge.log"
```

---

## 📊 Phase 4: Log-Analyse

Forge bietet mehrere Analyse-Tools.

### 4.1 Replay-Log Analyse — `analyze_replay_log.py`

**Verwendung:** Analysiert ein einzelnes Replay-Log auf Optimierungspotential.

**Befehl:**
```powershell
cd D:\Daten\SoftwareProjekte\Forge\forge
python analyze_replay_log.py "C:\Users\...\AppData\Roaming\Forge\games\gamelogs\replay_*.json"
```

**Output-Beispiel:**
```
============================================================
MTG Replay Log - Optimierungs-Analyse
============================================================

Datei: replay_Commander_2026-05-03_14-30-00.json
Total Events: 1247

============================================================
Event-Typen (häufigste zuerst):
============================================================
  PASS_PRIORITY           423 ( 33.9%)
  DRAW                    156 ( 12.5%)
  PHASE_START             124 (  9.9%)
  PLAY_LAND                45 (  3.6%)
  CAST                     89 (  7.1%)
  RESOLVE                  76 (  6.1%)
  ACTIVATE                 34 (  2.7%)
  ...

============================================================
Phasen-Analyse (welche Phasen sind oft leer?):
============================================================
  UP   : 243 total, 198 with actions (81.5%)
  DR   :  24 total,  24 with actions (100.0%)
  MP1  :  24 total,  24 with actions (100.0%)
  COMBAT_BEGIN: 24 total, 18 with actions (75.0%)
  DECLARE_ATTACKERS: 24 total, 12 with actions (50.0%)
  DECLARE_BLOCKERS: 24 total, 8 with actions (33.3%)
  ...

============================================================
Optimierungspotential:
============================================================
  ✓ DECLARE_BLOCKERS Phase hat in 16 Turns keine Actions
  ✓ END_COMBAT Phase hat in 20 Turns keine Actions
  ⚠ PASS_PRIORITY macht 33.9% aller Events aus
    → Potential für Priority-Auto-Pass bei leeren Phasen

============================================================
Empfehlungen:
============================================================
  1. Überspringen Sie DECLARE_BLOCKERS, wenn keine Blocker vorhanden
  2. Überspringen Sie END_COMBAT, wenn kein Stack/Trigger
  3. Implementieren Sie Priority-Auto-Pass für leere Phasen
```

**Features:**
- ✅ Event-Type Distribution
- ✅ Phasen-Leerstand-Analyse
- ✅ Optimierungsvorschläge
- ✅ Statistik-Zusammenfassung

### 4.2 Commander Batch-Analyse — `analyze_commander_stats.py`

**Verwendung:** Analysiert mehrere Simulation-Stats-Logs und erstellt aggregierte Statistiken.

**Befehl:**
```powershell
cd D:\Daten\SoftwareProjekte\Forge\forge
python analyze_commander_stats.py
```

**Interaktiver Dialog:**
```
📂 Log directory: C:\Users\...\AppData\Roaming\Forge\games\simulation_stats\
✓ Found 103 replay log(s)

💡 Process how many recent logs? (1-103, default: 100):
[User drückt Enter]

✓ Processing 100 log(s)...
  Processed 100/100 logs...
✓ Loaded 100 game(s)

Generating report...
```

**Output:**
```
============================================================
Commander Simulation Statistics Analyzer
============================================================

📂 Log directory: C:\Users\...\AppData\Roaming\Forge\games\simulation_stats\
✓ Found 100 replay log(s)

============================================================
📊 Summary Statistics
============================================================

🎮 Player: P1 (Krenko_Mob_Boss)
   Win Rate:          58.0% (58W/42L)
   Avg Turns:         12.4 (±3.1)
   Avg Damage Dealt:  145.2 (±35.7)
   Avg Spell Velocity:1.85 spells/turn
   Avg Missed Lands:  1.20 (±0.80)
   Median Peak Mana:  7

🎮 Player: P2 (Atraxa_Superfriends)
   Win Rate:          42.0% (42W/58L)
   Avg Turns:         12.4 (±3.1)
   Avg Damage Dealt:  109.8 (±28.4)
   Avg Spell Velocity:1.62 spells/turn
   Avg Missed Lands:  1.35 (±0.95)
   Median Peak Mana:  6

============================================================
✅ Analysis complete!
============================================================

💡 View full report: D:\Daten\...\commander_simulation_report.json
```

**Output-Datei:** `commander_simulation_report.json` (detailliert, per-game breakdown)

**📖 Siehe:** 
- `COMMANDER_METRICS_DOCUMENTATION.md` — Alle Metriken erklärt
- `COMMANDER_SIMULATION_COMPLETE_GUIDE.md` — Vollständiger Workflow

### 4.3 Log-Monitoring — `monitor_logs.ps1`

**Verwendung:** Überwacht das Log-Verzeichnis auf neue Dateien (Echtzeit).

**Befehl:**
```powershell
cd D:\Daten\SoftwareProjekte\Forge\forge
.\monitor_logs.ps1
```

**Output:**
```
=== Forge Game Log Monitor ===
Monitoring directory: C:\Users\...\AppData\Roaming\Forge\games\gamelogs
Waiting for new log files (checking every 5 seconds)...

......

=== NEW LOG FILE FOUND ===
File: gamelog_20260503_143000.txt
Modified: 05/03/2026 14:30:15
Size: 12458 bytes

=== LOG CONTENT ===
[Dateiinhalt wird angezeigt]

=== ANALYSIS ===
Total 'Analysis:' entries: 45
Zone change entries: 123
Spell resolution entries: 78
Turn summaries: 12
```

**Features:**
- ✅ Echtzeit-Überwachung (5-Sekunden-Intervall)
- ✅ Automatische Analyse (Counts, Zone Changes, etc.)
- ✅ Timeout nach 5 Minuten

### 4.4 Replay-Validierung — `validate_replay_state.py`

**Verwendung:** Validiert ein Replay-Log gegen erwartete Game-States.

**Befehl:**
```powershell
python validate_replay_state.py "C:\path\to\replay_log.json"
```

**Output:**
```
✓ Replay log valid
✓ All turns have consistent state transitions
✓ All card references resolved
✓ No orphaned events detected
```

---

## 🆘 Phase 5: Troubleshooting

### Problem 1: "Could not load deck"

**Symptom:**
```
ERROR: Could not load deck: Krenko_Mob_Boss.dck
```

**Ursache:** Deck-Datei nicht gefunden oder defekt.

**Lösung:**

```powershell
# 1. Überprüfen Sie das Verzeichnis
ls "$env:APPDATA\Forge\decks\commander\"

# 2. Überprüfen Sie den exakten Dateinamen
Get-ChildItem "$env:APPDATA\Forge\decks\commander\" -Filter "*.dck"

# 3. Konvertieren Sie erneut (falls JSON vorhanden)
python convert_decklist_to_dck.py my_decks/my_deck.json

# 4. Verwenden Sie den vollständigen Pfad
java -jar $jar sim -d "$env:APPDATA\Forge\decks\commander\Krenko_Mob_Boss.dck" ...
```

---

### Problem 2: "No simulation logs found"

**Symptom:**
```
✗ No replay logs found in C:\Users\...\AppData\Roaming\Forge\games\simulation_stats\
```

**Ursache:** Spiele wurden nicht ausgeführt oder Logging ist deaktiviert.

**Lösung:**

```powershell
# 1. Überprüfen Sie das Logs-Verzeichnis
ls "$env:APPDATA\Forge\games\simulation_stats\"

# 2. Führen Sie einen Test mit 1 Spiel aus
java -jar $jar sim -d deck1.dck deck2.dck -n 1 -f commander

# 3. Überprüfen Sie System-Logs
notepad "$env:APPDATA\Forge\forge.log"

# 4. Stellen Sie sicher, dass Logging aktiviert ist
# → Überprüfen Sie forge.properties (falls vorhanden)
```

---

### Problem 3: "Simulation hängt"

**Symptom:** Simulation stoppt ohne Fortschritt.

**Ursache:** Komplexe Board-States, Infinite Loops, oder zu niedriger Timeout.

**Lösung:**

```powershell
# 1. Erhöhen Sie den Timeout
java -jar $jar sim -d deck1.dck deck2.dck -n 10 -c 300  # 300 Sekunden

# 2. Verwenden Sie Quiet-Mode (weniger Console-Output)
java -jar $jar sim -d deck1.dck deck2.dck -n 10 -q

# 3. Überprüfen Sie System-Logs auf Errors
Get-Content "$env:APPDATA\Forge\forge.log" -Tail 50
```

---

### Problem 4: "Card not recognized"

**Symptom:**
```
WARNING: Card not found: 'Krenko, Mob Boss'
```

**Ursache:** Card-Name ist falsch geschrieben oder Karte existiert nicht in Forge.

**Lösung:**

```powershell
# 1. Überprüfen Sie die exakte Schreibweise
# → Verwenden Sie https://scryfall.com für korrekte Namen

# 2. Überprüfen Sie, ob die Karte in Forge existiert
# → Suchen Sie in forge-gui/res/cardsfolder/

# 3. Verwenden Sie Edition + Collector Number (JSON-Format)
# → Statt nur Name: {"name": "Krenko, Mob Boss", "edition": "M13", "collector_number": "138"}
```

---

### Problem 5: "Out of Memory Error"

**Symptom:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Ursache:** Zu viele Spiele oder komplexe Decks → Java braucht mehr RAM.

**Lösung:**

```powershell
# Erhöhen Sie Java Heap Size
java -Xmx4G -jar $jar sim -d deck1.dck deck2.dck -n 100
#     ^^^^
#     4 GB RAM (default ist ~1 GB)

# Für sehr große Simulationen (500+ Games):
java -Xmx8G -jar $jar sim -d deck1.dck deck2.dck -n 500
```

---

## 📚 Weitere Ressourcen

### Dokumentation

| Datei | Beschreibung |
|-------|--------------|
| `COMMANDER_SIMULATION_COMPLETE_GUIDE.md` | Vollständiger Commander-Workflow |
| `COMMANDER_DECK_REQUIREMENTS.md` | Deck-Format-Anforderungen |
| `COMMANDER_METRICS_DOCUMENTATION.md` | Alle Metriken erklärt |
| `SIMULATION_STATS_FORMAT.md` | JSON-Schema für Stats |
| `CLI-REPLAY.md` | Replay-Modus Details |
| `SCENARIO_STARTING_HAND_FORMAT.md` | Scenario-Format |
| `AGENTS.md` | Architektur-Übersicht |

### Spezifikationen

| Datei | Beschreibung |
|-------|--------------|
| `mtg-replay-notation/spec/MTG-REPLAY-NOTATION.md` | Replay-Format Spec |
| `mtg-replay-notation/spec/commander-decklist-spec.md` | Decklist-Format Spec |
| `mtg-replay-notation/schema/replay-schema.json` | JSON-Schema |

### Skripte & Tools

| Datei | Beschreibung |
|-------|--------------|
| `convert_decklist_to_dck.py` | JSON → .dck Konverter |
| `run_commander_simulation.ps1` | Batch-Simulation Runner |
| `analyze_commander_stats.py` | Stats-Aggregator |
| `analyze_replay_log.py` | Replay-Optimierungs-Analyse |
| `monitor_logs.ps1` | Echtzeit Log-Monitor |
| `validate_replay_state.py` | Replay-Validierung |

---

## 💡 Best Practices

### ✅ DO: Empfohlene Vorgehensweise

1. **Starten Sie mit kleinen Tests** — 1-10 Spiele bevor Sie 100+ laufen lassen
2. **Verwenden Sie Quiet-Mode für Batch** — Spart Console-Output bei großen Runs
3. **Analysieren Sie regelmäßig** — Schauen Sie sich Logs nach jedem größeren Run an
4. **Versionieren Sie Ihre Decks** — Nutzen Sie Git für Deck-JSON-Dateien
5. **Dokumentieren Sie Änderungen** — Notieren Sie, warum Sie Karten austauschen

### ❌ DON'T: Häufige Fehler

1. **Verwenden Sie keine veralteten Card-Names** — Scryfall ist immer aktuell
2. **Mischen Sie keine Formate** — Commander-Decks brauchen `-f commander`
3. **Ignorieren Sie keine Warnings** — System-Logs zeigen oft wichtige Hinweise
4. **Überladen Sie nicht das System** — 500+ Games auf einmal kann zu Memory-Errors führen
5. **Verlassen Sie sich nicht nur auf Win-Rate** — Andere Metriken sind ebenso wichtig

---

## 🚀 Quick Start (TL;DR)

```powershell
# 1. Stelle sicher, dass .dck Dateien bereit sind
# → %APPDATA%\Forge\decks\<format>\Deck_Name.dck

# 2. Simulation starten (1 Test-Spiel)
java -jar forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar sim `
  -d "$env:APPDATA\Forge\decks\commander\Deck1.dck" `
     "$env:APPDATA\Forge\decks\commander\Deck2.dck" `
  -n 1 -f commander

# 3. Logs öffnen
explorer "$env:APPDATA\Forge\games\gamelogs"

# 4. Replay-Log analysieren
python analyze_replay_log.py "$env:APPDATA\Forge\games\gamelogs\replay_*.json"

# 5. Für Batch (100 Games):
.\run_commander_simulation.ps1 -Deck1 "Deck1" -Deck2 "Deck2" -Games 100
python analyze_commander_stats.py
```

---

**Viel Spaß beim Testing! 🎮**

**Version:** 1.0.0 | **Letztes Update:** 2026-05-03



