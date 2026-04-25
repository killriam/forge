# Commander AI Simulation: Komplette Anleitung

**Version:** 2.0.0  
**Datum:** 2026-04-07  
**Status:** Ready for Production

---

## 📚 Einleitung

Diese Anleitung zeigt den **kompletten Workflow** für Commander Deck-Testing mit 100+ AI-Simulationen und detaillierter Statistik-Analyse. Das System ist in drei Phasen aufgeteilt:

### Was Sie tun werden:

1. **Phase 1: Vorbereitung** - Erstellen Sie Ihr Commander Deck als JSON-Datei
2. **Phase 2: Simulation** - Führen Sie 100 automatisierte Spiele aus
3. **Phase 3: Analyse** - Analysieren Sie die Ergebnisse und optimieren Sie Ihr Deck

**Gesamtdauer:** ~2-3 Stunden für 100 Spiele (abhängig von Hardware)

---

## 🎮 Phase 1: Deck-Vorbereitung

### Schritt 1.0: Deck-Dateien vorbereiten

**Voraussetzung:** `.dck` Dateien müssen bereits in Forge-Format vorliegen.

Diese werden erstellt durch:
- ✅ Externes Tool (z.B. `convert_decklist_to_dck.py`)
- ✅ Manuelle Deck-Erstellung in Forge GUI
- ✅ Import aus anderen Tools

**Speicherort:**
```
%APPDATA%\Forge\decks\commander\*.dck
```

**Beispiel:**
```
C:\Users\Username\AppData\Roaming\Forge\decks\commander\
├── Krenko_Mob_Boss.dck
├── Atraxa_Superfriends.dck
└── My_Custom_Deck.dck
```

### Schritt 1.1: Commander Decklist JSON erstellen (Optional)

**Speicherort:**
```
D:\Daten\SoftwareProjekte\Forge\forge\my_decks\my_deck.json
```

**Minimal-Beispiel** (`my_deck.json`):

```json
{
  "format": "mtg-commander-decklist",
  "version": "1.0.0",
  "meta": {
    "deck_id": "krenko-mob-boss-v1",
    "deck_name": "Krenko Mob Boss",
    "format": "Commander",
    "colors": ["R"],
    "created": "2026-04-07",
    "author": "Your Name",
    "description": "Token-based Commander deck"
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
    },
    {
      "quantity": 1,
      "name": "Lightning Bolt",
      "edition": "A25",
      "collector_number": "141",
      "primary_mechanic": "removal"
    }
    // ... weitere 97 Karten
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
        {
          "round": 0,
          "hand_size": 7,
          "min_value": 3.5
        },
        {
          "round": 1,
          "hand_size": 6,
          "min_value": 3.0
        }
      ]
    }
  }
}
```

**Anforderungen:**
- ✅ Exakt 1 Commander
- ✅ Exakt 99 Main-Karten
- ✅ Jede Karte braucht: `name`, `edition`, `collector_number`, `primary_mechanic`
- ✅ Optional aber empfohlen: `meta.deck_id`, `deck_rules.mulligan`

---

### Schritt 1.2: Bereit für Simulation

Nach der Vorbereitung (extern) sind die `.dck` Dateien bereit für die Simulation.

**Zu überprüfen:**
- ✅ `.dck` Dateien existieren in `%APPDATA%\Forge\decks\commander\`
- ✅ Dateinamen sind identisch zu denen, die in Phase 2 verwendet werden

---

## 🎯 Phase 2: AI-Simulation ausführen

### Schritt 2.1: Simulation starten

Führen Sie das Simulations-Skript aus:

**Befehl (Mirror Match - Deck vs. sich selbst):**
```powershell
cd D:\Daten\SoftwareProjekte\Forge\forge
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Games 100
```

**Befehl (Matchup Test - 2 verschiedene Decks):**
```powershell
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Deck2 "Atraxa_Superfriends" -Games 100
```

**Parameter:**

| Parameter | Default | Beschreibung |
|-----------|---------|--------------|
| `-Deck1` | (required) | Primäres Deck (ohne `.dck`) |
| `-Deck2` | Mirror | Gegner-Deck (default = Deck1) |
| `-Games` | `100` | Anzahl zu spielender Spiele |
| `-Timeout` | `180` | Timeout pro Spiel (Sekunden) |
| `-Quiet` | (flag) | Minimale Konsolen-Ausgabe |

**Beispiele:**

```powershell
# Schnelltest: 10 Spiele
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Games 10

# Mit Timeout erhöhen (für komplexe Decks)
.\run_commander_simulation.ps1 -Deck1 "Atraxa_Superfriends" -Games 100 -Timeout 300

# Silent Mode (keine Ausgabe)
.\run_commander_simulation.ps1 -Deck1 "Krenko_Mob_Boss" -Games 100 -Quiet
```

### Schritt 2.2: Simulation-Fortschritt überwachen

**Console-Ausgabe:**
```
════════════════════════════════════════════════════
  Commander AI Batch Simulation
════════════════════════════════════════════════════

✓ Found forge.jar: D:\Daten\SoftwareProjekte\Forge\forge\...
✓ Mirror match mode: Krenko_Mob_Boss vs. Krenko_Mob_Boss
✓ Deck 1 found: C:\Users\...\AppData\Roaming\Forge\decks\commander\Krenko_Mob_Boss.dck
✓ Deck 2 found: C:\Users\...\AppData\Roaming\Forge\decks\commander\Krenko_Mob_Boss.dck

════════════════════════════════════════════════════
Simulation Configuration:
  Games:    100
  Timeout:  180 seconds
  Format:   Commander
  Quiet:    False
════════════════════════════════════════════════════

Starting simulation...
This may take several minutes for 100 games.

[Simulation läuft...]

════════════════════════════════════════════════════
✅ Simulation Complete!
════════════════════════════════════════════════════

📊 Statistics:
   Duration:     18.5 minutes
   Games:        100
   New logs:     100 JSON files
   Log dir:      C:\Users\...\AppData\Roaming\Forge\games\simulation_stats\
```

### Schritt 2.3: Output-Dateien überprüfen

**Speicherort der Simulation Stats:**
```
%APPDATA%\Forge\games\simulation_stats\
```

**Beispiel-Dateien:**
```
simulation_stats_20260407_143000.json
simulation_stats_20260407_143045.json
simulation_stats_20260407_143089.json
... (100 Dateien)
```

**Jede Datei:** ~5-10 KB (vs. 200-800 KB für Full Replay)

---

## 📊 Phase 3: Statistik-Analyse

### Schritt 3.1: Statistiken aggregieren

Führen Sie das Analyse-Skript aus:

**Befehl:**
```powershell
cd D:\Daten\SoftwareProjekte\Forge\forge
python analyze_commander_stats.py
```

**Interaktive Eingabe:**
```
📂 Log directory: C:\Users\...\AppData\Roaming\Forge\games\simulation_stats\
✓ Found 103 replay log(s)

💡 Process how many recent logs? (1-103, default: 100):
[User drückt Enter oder gibt Zahl ein]

✓ Processing 100 log(s)...
  Processed 100/100 logs...
✓ Loaded 100 game(s)
✓ Players detected: P1, P2

Generating report...
```

**Custom Options:**

```powershell
# Custom Log-Verzeichnis
python analyze_commander_stats.py D:\my_logs

# Custom Output-Datei
python analyze_commander_stats.py D:\my_logs my_analysis.json

# Beide kombiniert
python analyze_commander_stats.py D:\my_logs D:\my_reports\analysis_20260407.json
```

### Schritt 3.2: Report-Ausgabe anschauen

**Console-Output:**
```
════════════════════════════════════════════════════════════════════════
Commander Simulation Statistics Analyzer
════════════════════════════════════════════════════════════════════════

📂 Log directory: C:\Users\...\AppData\Roaming\Forge\games\simulation_stats\
✓ Found 100 replay log(s)

════════════════════════════════════════════════════════════════════════
📊 Summary Statistics
════════════════════════════════════════════════════════════════════════

🎮 Player: P1
   Win Rate:          58.0% (58W/42L)
   Avg Turns:         12.4 (±3.1)
   Avg Damage Dealt:  145.2 (±35.7)
   Avg Spell Velocity:1.85 spells/turn
   Avg Missed Lands:  1.20 (±0.80)
   Median Peak Mana:  7

🎮 Player: P2
   Win Rate:          42.0% (42W/58L)
   Avg Turns:         12.4 (±3.1)
   Avg Damage Dealt:  109.8 (±28.4)
   Avg Spell Velocity:1.62 spells/turn
   Avg Missed Lands:  1.35 (±0.95)
   Median Peak Mana:  6

════════════════════════════════════════════════════════════════════════
✅ Analysis complete!
════════════════════════════════════════════════════════════════════════

💡 View full report: D:\Daten\SoftwareProjekte\Forge\forge\commander_simulation_report.json
```

### Schritt 3.3: JSON-Report öffnen

**Dateipfad:**
```
D:\Daten\SoftwareProjekte\Forge\forge\commander_simulation_report.json
```

**Öffnen Sie die Datei mit:**
- Visual Studio Code
- Notepad++
- Ihrem favorisierten JSON-Editor

**Report-Struktur:**
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
      "total_games": 100,
      "wins": 58,
      "win_rate": 0.58,
      "avg_turns": 12.4,
      "avg_damage_dealt": 145.2,
      "avg_spell_velocity": 1.85,
      // ... weitere Metriken
    },
    "P2": { /* ... */ }
  },
  "per_game_details": [
    {
      "game_id": "game_001",
      "winner": "P1",
      "total_turns": 10,
      "players": {
        "P1": { /* Statistiken */ },
        "P2": { /* Statistiken */ }
      }
    }
    // ... 100 Spiele
  ]
}
```

---

## 🔍 Interpretation der Metriken

### Win-Rate

```
Win Rate: 58.0% (58W/42L)
```
- **Bedeutung:** Ihr Deck gewinnt 58% der Spiele gegen sich selbst
- **Gut:** > 50% (besser als Zufallschancen)
- **Sehr gut:** > 60% (starkes Deck)
- **Exzellent:** > 70% (sehr konsistent)

### Average Turns

```
Avg Turns: 12.4 (±3.1)
```
- **Bedeutung:** Spiele enden durchschnittlich nach 12.4 Turns
- **Standard Deviation (±3.1):** Variabilität zwischen Spielen
- **Aggressiv:** < 10 Turns (schnelle Win-Strategie)
- **Control:** > 15 Turns (grindige Strategie)

### Average Damage Dealt

```
Avg Damage Dealt: 145.2 (±35.7)
```
- **Bedeutung:** Durchschnittlicher Schaden pro Spiel
- **Interpretation:** Bei 40 Startlife brauchen Sie ~3-4 Spiele zum KO
- **Mit Effizienzbeiträgen:** Aggressive Decks > 150 Damage

### Average Spell Velocity

```
Avg Spell Velocity: 1.85 spells/turn
```
- **Bedeutung:** Im Durchschnitt 1.85 Spells pro Turn
- **Niedrig:** < 1.0 (wenige Plays pro Turn → zu viel Mana-Verschwendung)
- **Normal:** 1.5-2.5 (gute Balance)
- **Hoch:** > 2.5 (aggressive Spielweise, hohe Spell-Density)

### Average Missed Lands

```
Avg Missed Lands: 1.20 (±0.80)
```
- **Bedeutung:** Im Durchschnitt verpasst das Deck 1.2 Land-Drops pro Spiel
- **Gut:** < 1.0 (konsistente Mana-Base)
- **Problematisch:** > 2.0 (zu wenig Lands oder schlechte Mulligan-Strategie)

### Median Peak Mana

```
Median Peak Mana: 7
```
- **Bedeutung:** Die höchste Mana-Verfügbarkeit liegt bei ~7 Mana
- **Entspricht:** Etwa 7 Lands + Mana-Rocks ohne Duplikate
- **Für Commander:** 7-10 ist normal (Commander braucht viel Mana)

---

## 🎯 Deck-Optimierungsanleitung

Basierend auf Ihren Statistiken können Sie Ihr Deck optimieren:

### Szenario 1: Zu hohe Missed Land Drops (> 2.0)

**Ursache:** Nicht genug Lands oder schlechte Mulligan-Regeln

**Lösung:**
1. Erhöhen Sie Land-Count um 2-3 Karten
2. Fügen Sie Mana-Ramp hinzu (z.B. `Cultivate`, `Kodama's Reach`)
3. Passen Sie Mulligan-Thresholds an:
   ```json
   "thresholds": [
     {"round": 0, "hand_size": 7, "min_value": 3.8}  // ← höher
   ]
   ```

### Szenario 2: Zu niedrige Spell Velocity (< 1.2)

**Ursache:** Zu viele hochkosten Spells oder zu wenig Card Draw

**Lösung:**
1. Fügen Sie Low-CMC Cards hinzu (< 3 Mana)
2. Ergänzen Sie Card Draw (z.B. `Rhystic Study`, `Mystic Remora`)
3. Reduzieren Sie High-CMC Cards (> 6 Mana)

### Szenario 3: Niedriges Win Rate (< 50%)

**Ursache:** Deck ist nicht schnell/stark genug

**Lösung:**
1. Analysieren Sie Win-Conditions
2. Erhöhen Sie Removal und Interaction
3. Testen Sie gegen verschiedene Archetypes

---

## 📁 Dateistruktur & Pfade

```
D:\Daten\SoftwareProjekte\Forge\forge\
├── convert_decklist_to_dck.py                    ← Konverter
├── run_commander_simulation.ps1                   ← Simulator
├── analyze_commander_stats.py                     ← Analyzer
├── commander_simulation_report.json               ← Report (Output)
├── my_decks/
│   ├── my_deck.json                              ← Ihre Decklist
│   └── atraxa_superfriends.json                  ← Weitere Decks
├── docs/
│   ├── COMMANDER_DECK_REQUIREMENTS.md             ← Deck-Anforderungen
│   ├── COMMANDER_METRICS_DOCUMENTATION.md        ← Alle Metriken erklärt
│   ├── SIMULATION_ANALYTICS_ARCHITECTURE.md      ← Technisches Design
│   ├── SIMULATION_STATS_FORMAT.md                ← JSON Schema
│   ├── IMPLEMENTATION_SUMMARY.md                 ← Implementierungsdetails
│   └── COMMANDER_SIMULATION_COMPLETE_GUIDE.md   ← Diese Datei
│
└── %APPDATA%\Forge\
    ├── decks/commander/
    │   ├── Krenko_Mob_Boss.dck                   ← Konvertiertes Deck
    │   └── Atraxa_Superfriends.dck
    └── games/simulation_stats/
        ├── simulation_stats_20260407_143000.json ← Stats Spiel 1
        ├── simulation_stats_20260407_143045.json ← Stats Spiel 2
        └── ... (100 Dateien)
```

---

## 🚀 Quick Start (TL;DR)

```powershell
# 1. Stelle sicher, dass .dck Dateien bereit sind
# → %APPDATA%\Forge\decks\commander\Deck_Name.dck

# 2. Simulieren
.\run_commander_simulation.ps1 -Deck1 "Deck_Name" -Games 100

# 3. Analysieren
python analyze_commander_stats.py

# 4. Report öffnen
notepad commander_simulation_report.json
```

---

## 🆘 Troubleshooting

### Problem: "Could not load deck"

**Ursache:** `.dck`-Datei nicht gefunden oder defekt

**Lösung:**
```powershell
# Überprüfen Sie das Verzeichnis
ls $env:APPDATA\Forge\decks\commander\

# Konvertieren Sie erneut
python convert_decklist_to_dck.py my_decks/my_deck.json
```

### Problem: "No simulation logs found"

**Ursache:** Spiele wurden nicht ausgeführt oder Logs wurden nicht gespeichert

**Lösung:**
```powershell
# Überprüfen Sie das Logs-Verzeichnis
ls $env:APPDATA\Forge\games\simulation_stats\

# Versuchen Sie einen Test mit 1 Spiel
.\run_commander_simulation.ps1 -Deck1 "Deck_Name" -Games 1

# Überprüfen Sie Forge-Logs
cat $env:APPDATA\Forge\forge.log
```

### Problem: "Simulation hängt"

**Ursache:** Komplexe Board-States oder Infinite Loops

**Lösung:**
```powershell
# Erhöhen Sie den Timeout
.\run_commander_simulation.ps1 -Deck1 "Deck_Name" -Games 100 -Timeout 300
```

---

## 📚 Weitere Ressourcen

- **[COMMANDER_DECK_REQUIREMENTS.md](COMMANDER_DECK_REQUIREMENTS.md)** - Deck-Format & Best Practices
- **[COMMANDER_METRICS_DOCUMENTATION.md](COMMANDER_METRICS_DOCUMENTATION.md)** - Detaillierte Metrik-Erklärungen
- **[SIMULATION_ANALYTICS_ARCHITECTURE.md](SIMULATION_ANALYTICS_ARCHITECTURE.md)** - Technisches Design
- **[SIMULATION_STATS_FORMAT.md](SIMULATION_STATS_FORMAT.md)** - JSON Schema & Validierung
- **[commander-decklist-spec.md](../mtg-replay-notation/spec/commander-decklist-spec.md)** - Offizielle Spezifikation

---

## 💡 Best Practices

1. **Starten Sie mit 10 Spielen** - Schnelle Validierung
2. **Überprüfen Sie Win-Rate** - Sollte > 50% gegen sich selbst sein
3. **Optimieren Sie Mulligan-Regeln** - Reduziert Missed Lands
4. **Testen Sie gegen verschiedene Decks** - Nicht nur Mirror Match
5. **Vergleichen Sie Versionen** - Run vorher/nachher für Verbesserungen

---

**Viel Spaß beim Deck-Testing! 🎮**

**Version:** 2.0.0 | **Letztes Update:** 2026-04-07




