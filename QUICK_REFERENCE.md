# Commander AI Simulation: Quick Reference

**Schnelle Übersicht für erfahrene Nutzer**

---

## 🎯 Workflow in 4 Schritten

### 0. DECK VORBEREITEN (extern)
```
%APPDATA%\Forge\decks\commander\Deck_Name.dck
→ Wird von externem Tool erstellt
```

### 1. SIMULATION AUSFÜHREN
```powershell
.\run_commander_simulation.ps1 -Deck1 "Deck_Name" -Games 100
```
**Output:** `%APPDATA%\Forge\games\simulation_stats\simulation_stats_*.json` (100 Dateien)

### 2. STATISTIKEN ANALYSIEREN
```powershell
python analyze_commander_stats.py
```
**Output:** `commander_simulation_report.json`

### 3. REPORT ANSEHEN
```powershell
notepad commander_simulation_report.json
```

---

## 📊 Wichtigste Metriken

| Metrik | Gut | Sehr Gut | Exzellent |
|--------|-----|----------|-----------|
| **Win Rate** | >50% | >60% | >70% |
| **Avg Turns** | 8-15 | Format abhängig | Format abhängig |
| **Spell Velocity** | >1.2 | >1.5 | >1.8 |
| **Missed Lands** | <2.0 | <1.0 | <0.5 |
| **Peak Mana** | 5-8 | 7-10 | 8-12 |

---

## 🛠️ Parameter

### `run_commander_simulation.ps1`
```
-Deck1 <string>              [REQUIRED] Primäres Deck
-Deck2 <string>              [Optional] Gegner (default=Deck1)
-Games <int>                 [Default 100] Anzahl Spiele
-Timeout <int>               [Default 180] Timeout pro Spiel (sec)
-Quiet                       [Flag] Minimale Ausgabe
```

### `analyze_commander_stats.py`
```
<log_directory>              [Optional] Stats-Pfad
<output_file>                [Optional] Report-Datei
```

---

## 📁 Wichtige Pfade

| Zweck | Pfad |
|-------|------|
| **Deine Decks** | `D:\Daten\SoftwareProjekte\Forge\forge\my_decks\*.json` |
| **Konvertierte DCKs** | `%APPDATA%\Forge\decks\commander\*.dck` |
| **Simulation Stats** | `%APPDATA%\Forge\games\simulation_stats\*.json` |
| **Final Report** | `D:\Daten\SoftwareProjekte\Forge\forge\commander_simulation_report.json` |

**Windows Umgebungsvariable:** 
- `%APPDATA%` = `C:\Users\<YourUsername>\AppData\Roaming`

---

## ⚡ Häufige Befehle

```powershell
# Mirror Match (100 Spiele)
.\run_commander_simulation.ps1 -Deck1 "MyDeck" -Games 100

# Schnelltest (10 Spiele)
.\run_commander_simulation.ps1 -Deck1 "MyDeck" -Games 10

# Matchup Test
.\run_commander_simulation.ps1 -Deck1 "Deck1" -Deck2 "Deck2" -Games 100

# Mit erhöhtem Timeout (komplexe Decks)
.\run_commander_simulation.ps1 -Deck1 "ComplexDeck" -Games 50 -Timeout 300

# Silent Mode
.\run_commander_simulation.ps1 -Deck1 "MyDeck" -Games 100 -Quiet

# Custom Output-Verzeichnis
python analyze_commander_stats.py D:\my_logs D:\my_reports\analysis.json
```

---

## 🐛 Schnelle Fixes

| Problem | Lösung |
|---------|--------|
| `.dck` nicht gefunden | `python convert_decklist_to_dck.py my_decks/deck.json` erneut |
| Keine Logs vorhanden | Prüfe `%APPDATA%\Forge\games\simulation_stats\` |
| Simulation hängt | Timeout erhöhen: `-Timeout 300` |
| JSON-Fehler | Deck-JSON mit Online-Validator prüfen |

---

## 📈 Optimierungstipps

**Zu viele Missed Lands?**
- Lands erhöhen (+2-3)
- Mana-Ramp hinzufügen (Cultivate, Kodama's Reach)
- Mulligan-Min-Value erhöhen

**Zu niedrige Spell Velocity?**
- Low-CMC Cards hinzufügen
- Card Draw verbessern (Rhystic Study)
- High-CMC Cards reduzieren

**Niedriges Win Rate?**
- Win-Conditions überprüfen
- Removal/Interaction erhöhen
- Gegen verschiedene Decks testen

---

## 📚 Vollständige Dokumentation

- `COMMANDER_SIMULATION_COMPLETE_GUIDE.md` - Detaillierte Anleitung
- `COMMANDER_DECK_REQUIREMENTS.md` - Deck-Format
- `COMMANDER_METRICS_DOCUMENTATION.md` - Metrik-Erklärungen
- `SIMULATION_ANALYTICS_ARCHITECTURE.md` - Technisches Design
- `SIMULATION_STATS_FORMAT.md` - JSON Schema

---

**Version:** 2.0.0 | **Datum:** 2026-04-07




