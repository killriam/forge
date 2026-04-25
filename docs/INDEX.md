# Commander AI Simulation: Dokumentations-Index

**Zentrale Referenz für alle Dokumentationen und Dateien**

---

## 📖 Dokumentationen

### 🚀 Einstieg

| Datei | Pfad | Zielgruppe | Inhalt |
|-------|------|-----------|--------|
| **COMMANDER_SIMULATION_COMPLETE_GUIDE.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_SIMULATION_COMPLETE_GUIDE.md` | Anfänger | Kompletter Workflow von A-Z mit Einleitungstext |
| **QUICK_REFERENCE.md** | `D:\Daten\SoftwareProjekte\Forge\forge\QUICK_REFERENCE.md` | Erfahrene | Schnelle Übersicht, Befehle, Tabellen |
| **COMMANDER_SIMULATION_README.md** | `D:\Daten\SoftwareProjekte\Forge\forge\COMMANDER_SIMULATION_README.md` | Alle | Übersicht mit Links |

### 📋 Anforderungen & Spezifikationen

| Datei | Pfad | Inhalt |
|-------|------|--------|
| **COMMANDER_DECK_REQUIREMENTS.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_DECK_REQUIREMENTS.md` | Pflichtfelder, Best Practices, Set-Code Referenz |
| **commander-decklist-spec.md** | `D:\Daten\SoftwareProjekte\Forge\forge\mtg-replay-notation\spec\commander-decklist-spec.md` | Offizielle Spezifikation v1.0.0 |
| **SIMULATION_STATS_FORMAT.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\SIMULATION_STATS_FORMAT.md` | JSON Schema, Field Definitions, Validation |

### 📊 Metriken & Analyse

| Datei | Pfad | Inhalt |
|-------|------|--------|
| **COMMANDER_METRICS_DOCUMENTATION.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_METRICS_DOCUMENTATION.md` | Game Summary, Turn Summary, Aggregate Metrics |

### 🏗️ Architektur & Implementierung

| Datei | Pfad | Zielgruppe | Inhalt |
|-------|------|-----------|--------|
| **SIMULATION_ANALYTICS_ARCHITECTURE.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\SIMULATION_ANALYTICS_ARCHITECTURE.md` | Entwickler | 2-Ebenen-Design, Separated Concerns |
| **IMPLEMENTATION_SUMMARY.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\IMPLEMENTATION_SUMMARY.md` | Entwickler | Phase 1 Details, Integration Steps |

---

## 💾 Dateien & Tools

### Tools

| Datei | Pfad | Typ | Zweck |
|-------|------|-----|-------|
| **convert_decklist_to_dck.py** | `D:\Daten\SoftwareProjekte\Forge\forge\convert_decklist_to_dck.py` | Python | JSON → Forge .dck Format |
| **run_commander_simulation.ps1** | `D:\Daten\SoftwareProjekte\Forge\forge\run_commander_simulation.ps1` | PowerShell | Starten von 100+ Spielen |
| **analyze_commander_stats.py** | `D:\Daten\SoftwareProjekte\Forge\forge\analyze_commander_stats.py` | Python | Aggregieren & Analysieren |

### Input/Output

| Typ | Pfad | Beschreibung |
|-----|------|-------------|
| **Deck Input** | `D:\Daten\SoftwareProjekte\Forge\forge\my_decks\*.json` | Ihre Commander Decklist JSONs |
| **Deck Output** | `%APPDATA%\Forge\decks\commander\*.dck` | Konvertierte Decks (Forge-Format) |
| **Simulation Output** | `%APPDATA%\Forge\games\simulation_stats\*.json` | 100 Spiel-Statistik-Dateien |
| **Report Output** | `D:\Daten\SoftwareProjekte\Forge\forge\commander_simulation_report.json` | Finale aggregierte Statistiken |

### Java Source (Implementation)

| Klasse | Pfad | Inhalt |
|--------|------|--------|
| **SimulationStats.java** | `forge-game\src\main\java\forge\game\simulation\SimulationStats.java` | Data model für JSON |
| **PlayerStats.java** | `forge-game\src\main\java\forge\game\simulation\PlayerStats.java` | Per-Player Statistiken |
| **SimulationMetricsCollector.java** | `forge-game\src\main\java\forge\game\simulation\SimulationMetricsCollector.java` | Kern-Logik für Metrik-Erfassung |
| **SimulationStatsExporter.java** | `forge-game\src\main\java\forge\game\simulation\SimulationStatsExporter.java` | JSON-Export Utility |
| **Game.java** (modified) | `forge-game\src\main\java\forge\game\Game.java` | Collector Field + Getter/Setter |

---

## 🎯 Workflow-Schritte & Dokumentationen

### Schritt 0: Deck vorbereiten (Extern)
**Voraussetzung:** `.dck` Dateien müssen externe erstellt werden  
**Tools:** Beliebige Tool (z.B. `convert_decklist_to_dck.py`) oder Forge GUI  
**Output:** `%APPDATA%\Forge\decks\commander\*.dck`

### Schritt 1: Simulation ausführen
**Dokument:** [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) (Kapitel "Phase 2")  
**Quick Ref:** [QUICK_REFERENCE.md](QUICK_REFERENCE.md) (Punkt "Workflow in 4 Schritten")  
**Tool:** `run_commander_simulation.ps1`

### Schritt 2: Statistiken analysieren
**Dokument:** [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) (Kapitel "Phase 3")  
**Metriken:** [COMMANDER_METRICS_DOCUMENTATION.md](docs/COMMANDER_METRICS_DOCUMENTATION.md)  
**Quick Ref:** [QUICK_REFERENCE.md](QUICK_REFERENCE.md) (Punkt "Wichtigste Metriken")  
**Tool:** `analyze_commander_stats.py`

### Schritt 3: Deck optimieren
**Dokument:** [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) (Kapitel "Deck-Optimierungsanleitung")  
**Quick Ref:** [QUICK_REFERENCE.md](QUICK_REFERENCE.md) (Punkt "Optimierungstipps")

---

## 📚 Nach Zielgruppe

### Für Spieler/Deckbuilder
1. **Start:** [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md)
2. **Nachschlagen:** [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
3. **Deck-Anforderungen:** [COMMANDER_DECK_REQUIREMENTS.md](docs/COMMANDER_DECK_REQUIREMENTS.md)
4. **Metrik-Bedeutung:** [COMMANDER_METRICS_DOCUMENTATION.md](docs/COMMANDER_METRICS_DOCUMENTATION.md)

### Für Entwickler
1. **Architektur:** [SIMULATION_ANALYTICS_ARCHITECTURE.md](docs/SIMULATION_ANALYTICS_ARCHITECTURE.md)
2. **Format:** [SIMULATION_STATS_FORMAT.md](docs/SIMULATION_STATS_FORMAT.md)
3. **Implementation:** [IMPLEMENTATION_SUMMARY.md](docs/IMPLEMENTATION_SUMMARY.md)
4. **Source Code:** `forge-game/src/main/java/forge/game/simulation/`

---

## 🔗 Absolute Pfade (Windows)

```powershell
# Dokumentationen
D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_SIMULATION_COMPLETE_GUIDE.md
D:\Daten\SoftwareProjekte\Forge\forge\QUICK_REFERENCE.md
D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_DECK_REQUIREMENTS.md
D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_METRICS_DOCUMENTATION.md
D:\Daten\SoftwareProjekte\Forge\forge\docs\SIMULATION_ANALYTICS_ARCHITECTURE.md
D:\Daten\SoftwareProjekte\Forge\forge\docs\SIMULATION_STATS_FORMAT.md
D:\Daten\SoftwareProjekte\Forge\forge\docs\IMPLEMENTATION_SUMMARY.md

# Tools
D:\Daten\SoftwareProjekte\Forge\forge\convert_decklist_to_dck.py
D:\Daten\SoftwareProjekte\Forge\forge\run_commander_simulation.ps1
D:\Daten\SoftwareProjekte\Forge\forge\analyze_commander_stats.py

# Benutzerdirectories (Eingabe/Ausgabe)
%APPDATA%\Forge\decks\commander\  # Konvertierte DCK-Dateien
%APPDATA%\Forge\games\simulation_stats\  # Spiel-Statistiken
```

**Hinweis:** `%APPDATA%` auf Windows = `C:\Users\<YourUsername>\AppData\Roaming`

---

## 🎓 Learning Path

### Anfänger (0-1 Stunde)
- [ ] Lese [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md)
- [ ] Erstelle ein Test-Deck
- [ ] Führe 10 Spiele aus
- [ ] Schaue dir Report an

### Fortgeschrittener (1-3 Stunden)
- [ ] Lies [COMMANDER_DECK_REQUIREMENTS.md](docs/COMMANDER_DECK_REQUIREMENTS.md)
- [ ] Optimiere Deck basierend auf Metriken
- [ ] Vergleiche mehrere Deck-Versionen
- [ ] Teste gegen verschiedene Gegner

### Experte (3+ Stunden)
- [ ] Studiere [SIMULATION_ANALYTICS_ARCHITECTURE.md](docs/SIMULATION_ANALYTICS_ARCHITECTURE.md)
- [ ] Verstehe [SIMULATION_STATS_FORMAT.md](docs/SIMULATION_STATS_FORMAT.md)
- [ ] Kontribuiere zu [IMPLEMENTATION_SUMMARY.md](docs/IMPLEMENTATION_SUMMARY.md)

---

## ❓ FAQ-Referenzen

| Frage | Siehe |
|-------|------|
| Wie erstelle ich ein Deck? | [COMMANDER_DECK_REQUIREMENTS.md](docs/COMMANDER_DECK_REQUIREMENTS.md) Kapitel 2 |
| Wie konvertiere ich? | [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) Kapitel "Phase 1.2" |
| Welche Parameter gibt es? | [QUICK_REFERENCE.md](QUICK_REFERENCE.md) Kapitel "Parameter" |
| Was bedeutet Win Rate? | [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) Kapitel "Metriken interpretieren" |
| Wie optimiere ich? | [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) Kapitel "Optimierungsanleitung" |
| Was ist Simulation Stats Format? | [SIMULATION_STATS_FORMAT.md](docs/SIMULATION_STATS_FORMAT.md) |
| Wie ist die Architektur? | [SIMULATION_ANALYTICS_ARCHITECTURE.md](docs/SIMULATION_ANALYTICS_ARCHITECTURE.md) |

---

## 🔄 Version History

| Version | Datum | Änderungen |
|---------|-------|-----------|
| **2.0.0** | 2026-04-07 | Separated Logging & Analytics, schlanke Stats-Logs |
| **1.0.0** | (geplant) | Initial Release |

---

## 📞 Support

- **Dokumentation:** Siehe oben
- **Technische Fragen:** [IMPLEMENTATION_SUMMARY.md](docs/IMPLEMENTATION_SUMMARY.md)
- **Architektur-Fragen:** [SIMULATION_ANALYTICS_ARCHITECTURE.md](docs/SIMULATION_ANALYTICS_ARCHITECTURE.md)
- **Bugs:** Siehe [QUICK_REFERENCE.md](QUICK_REFERENCE.md) Kapitel "Schnelle Fixes"

---

**Zuletzt aktualisiert:** 2026-04-07  
**Version:** 2.0.0




