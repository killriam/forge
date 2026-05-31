# 📖 Commander AI Simulation: Dokumentation & Dateien

**Alle Pfade, Dokumentationen und Tools auf einen Blick**

---

## 🎯 START HIER

### Für den ersten Start:
👉 **[COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md)**
- Kompletter Workflow von A bis Z
- Mit Einleitungstext und Beispielen
- Pfad: `D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_SIMULATION_COMPLETE_GUIDE.md`

### Für schnelle Referenz:
👉 **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)**
- Befehle, Parameter, Tabellen
- Häufige Probleme & Lösungen
- Pfad: `D:\Daten\SoftwareProjekte\Forge\forge\QUICK_REFERENCE.md`

### Für kompletten Überblick:
👉 **[INDEX.md](docs/INDEX.md)**
- Alle Dokumente sortiert
- Nach Zielgruppe & Workflow
- Pfad: `D:\Daten\SoftwareProjekte\Forge\forge\docs\INDEX.md`

---

## 📚 Dokumentationen (Absolute Pfade)

### Benutzer-Handbücher

| Datei | Absoluter Pfad | Für wen? |
|-------|---|---|
| **COMMANDER_SIMULATION_COMPLETE_GUIDE.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_SIMULATION_COMPLETE_GUIDE.md` | Anfänger & alle |
| **QUICK_REFERENCE.md** | `D:\Daten\SoftwareProjekte\Forge\forge\QUICK_REFERENCE.md` | Erfahrene Nutzer |
| **COMMANDER_SIMULATION_README.md** | `D:\Daten\SoftwareProjekte\Forge\forge\COMMANDER_SIMULATION_README.md` | Übersicht |

### Anforderungen & Spezifikationen

| Datei | Absoluter Pfad | Inhalt |
|-------|---|---|
| **COMMANDER_DECK_REQUIREMENTS.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_DECK_REQUIREMENTS.md` | Deck-Format & Best Practices |
| **commander-decklist-spec.md** | `D:\Daten\SoftwareProjekte\Forge\forge\mtg-replay-notation\spec\commander-decklist-spec.md` | Offizielle v1.0.0 Spec |

### Metriken & Datenformat

| Datei | Absoluter Pfad | Inhalt |
|-------|---|---|
| **COMMANDER_METRICS_DOCUMENTATION.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_METRICS_DOCUMENTATION.md` | Alle Metriken erklärt |
| **SIMULATION_STATS_FORMAT.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\SIMULATION_STATS_FORMAT.md` | JSON Schema & Validierung |

### Technische Dokumentation

| Datei | Absoluter Pfad | Für wen? |
|-------|---|---|
| **SIMULATION_ANALYTICS_ARCHITECTURE.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\SIMULATION_ANALYTICS_ARCHITECTURE.md` | Entwickler |
| **IMPLEMENTATION_SUMMARY.md** | `D:\Daten\SoftwareProjekte\Forge\forge\docs\IMPLEMENTATION_SUMMARY.md` | Entwickler |

---

## 🔧 Tools (Absolute Pfade)

### Python Scripts

```powershell
# Deck Konvertierung
D:\Daten\SoftwareProjekte\Forge\forge\convert_decklist_to_dck.py

# Statistik Analyse
D:\Daten\SoftwareProjekte\Forge\forge\analyze_commander_stats.py
```

### PowerShell Script

```powershell
# Batch Simulation
D:\Daten\SoftwareProjekte\Forge\forge\run_commander_simulation.ps1
```

---

## 💾 Dateistruktur

### Input (Ihre Daten)

```
D:\Daten\SoftwareProjekte\Forge\forge\my_decks\
├── my_deck.json              ← Ihre Commander Decklist
├── atraxa_superfriends.json
└── krenko_mob_boss.json
```

### Output (Generierte Dateien)

```
%APPDATA%\Forge\decks\commander\
├── My_Deck.dck               ← Konvertierte Decks
├── Atraxa_Superfriends.dck
└── Krenko_Mob_Boss.dck

%APPDATA%\Forge\games\simulation_stats\
├── simulation_stats_20260407_143000.json
├── simulation_stats_20260407_143045.json
└── ... (100 Dateien für 100 Spiele)

D:\Daten\SoftwareProjekte\Forge\forge\
└── commander_simulation_report.json    ← Finale Statistiken
```

**Hinweis:** `%APPDATA%` = `C:\Users\<YourUsername>\AppData\Roaming`

---

## 🚀 3-Befehle Quick Start

```powershell
# 1. Stelle sicher, dass .dck Dateien bereit sind
# → %APPDATA%\Forge\decks\commander\My_Deck.dck
# (Externe Tools oder Forge GUI)

# 2. Simulieren (100 Spiele)
cd D:\Daten\SoftwareProjekte\Forge\forge
.\run_commander_simulation.ps1 -Deck1 "My_Deck" -Games 100

# 3. Analysieren
python analyze_commander_stats.py
```

**Danach:** Öffnen Sie `commander_simulation_report.json` im JSON-Editor

---

## 📖 Dokumentations-Struktur

### Nach Anwendungsfall

#### "Ich möchte mein Deck testen"
1. Lese: [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md)
2. Siehe: [QUICK_REFERENCE.md](QUICK_REFERENCE.md) für Befehle
3. Nutze: `convert_decklist_to_dck.py` + `run_commander_simulation.ps1` + `analyze_commander_stats.py`

#### "Ich verstehe die Metriken nicht"
1. Lese: [COMMANDER_METRICS_DOCUMENTATION.md](docs/COMMANDER_METRICS_DOCUMENTATION.md)
2. Siehe: [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) Kapitel "Metrik-Interpretation"

#### "Ich möchte mein Deck optimieren"
1. Lese: [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) Kapitel "Deck-Optimierung"
2. Siehe: [QUICK_REFERENCE.md](QUICK_REFERENCE.md) Kapitel "Optimierungstipps"

#### "Ich möchte das System verstehen" (Entwickler)
1. Lese: [SIMULATION_ANALYTICS_ARCHITECTURE.md](docs/SIMULATION_ANALYTICS_ARCHITECTURE.md)
2. Studiere: [SIMULATION_STATS_FORMAT.md](docs/SIMULATION_STATS_FORMAT.md)
3. Implementiere: [IMPLEMENTATION_SUMMARY.md](docs/IMPLEMENTATION_SUMMARY.md)

---

## 🎯 Workflow mit Pfaden

### Schritt 1: Deck erstellen
**Dokument:** [COMMANDER_DECK_REQUIREMENTS.md](docs/COMMANDER_DECK_REQUIREMENTS.md)  
**Datei-Pfad:** `D:\Daten\SoftwareProjekte\Forge\forge\my_decks\my_deck.json`

### Schritt 2: Konvertieren
**Tool:** `D:\Daten\SoftwareProjekte\Forge\forge\convert_decklist_to_dck.py`  
**Befehl:**
```powershell
python convert_decklist_to_dck.py my_decks/my_deck.json
```
**Output:** `%APPDATA%\Forge\decks\commander\My_Deck.dck`

### Schritt 3: Simulieren
**Tool:** `D:\Daten\SoftwareProjekte\Forge\forge\run_commander_simulation.ps1`  
**Befehl:**
```powershell
.\run_commander_simulation.ps1 -Deck1 "My_Deck" -Games 100
```
**Output:** `%APPDATA%\Forge\games\simulation_stats\simulation_stats_*.json` (100 Dateien)

### Schritt 4: Analysieren
**Tool:** `D:\Daten\SoftwareProjekte\Forge\forge\analyze_commander_stats.py`  
**Befehl:**
```powershell
python analyze_commander_stats.py
```
**Output:** `D:\Daten\SoftwareProjekte\Forge\forge\commander_simulation_report.json`

### Schritt 5: Report anschauen
**Datei:** `D:\Daten\SoftwareProjekte\Forge\forge\commander_simulation_report.json`  
**Mit:** VS Code, Notepad++, oder beliebigen JSON-Editor öffnen

---

## 📋 Dokumentations-Checklist

- [ ] **Anfänger?** → Lese [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md)
- [ ] **Schnelle Referenz?** → Nutze [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
- [ ] **Deck-Format?** → Siehe [COMMANDER_DECK_REQUIREMENTS.md](docs/COMMANDER_DECK_REQUIREMENTS.md)
- [ ] **Metriken?** → Lese [COMMANDER_METRICS_DOCUMENTATION.md](docs/COMMANDER_METRICS_DOCUMENTATION.md)
- [ ] **JSON-Schema?** → Studiere [SIMULATION_STATS_FORMAT.md](docs/SIMULATION_STATS_FORMAT.md)
- [ ] **Architektur?** → Siehe [SIMULATION_ANALYTICS_ARCHITECTURE.md](docs/SIMULATION_ANALYTICS_ARCHITECTURE.md)
- [ ] **Alle Pfade?** → Öffne [INDEX.md](docs/INDEX.md)

---

## 🔗 Alle Dateien auf einen Blick

### Dokumentationen
```
D:\Daten\SoftwareProjekte\Forge\forge\
├── QUICK_REFERENCE.md
├── COMMANDER_SIMULATION_README.md
├── docs\
│   ├── INDEX.md
│   ├── COMMANDER_SIMULATION_COMPLETE_GUIDE.md
│   ├── COMMANDER_DECK_REQUIREMENTS.md
│   ├── COMMANDER_METRICS_DOCUMENTATION.md
│   ├── SIMULATION_ANALYTICS_ARCHITECTURE.md
│   ├── SIMULATION_STATS_FORMAT.md
│   └── IMPLEMENTATION_SUMMARY.md
└── mtg-replay-notation\spec\
    └── commander-decklist-spec.md
```

### Tools
```
D:\Daten\SoftwareProjekte\Forge\forge\
├── convert_decklist_to_dck.py
├── run_commander_simulation.ps1
└── analyze_commander_stats.py
```

### Java Source Code
```
D:\Daten\SoftwareProjekte\Forge\forge\forge-game\src\main\java\forge\game\simulation\
├── SimulationStats.java
├── PlayerStats.java
├── SimulationMetricsCollector.java
└── SimulationStatsExporter.java
```

---

## 💡 Pro-Tipps

1. **Speichern Sie diesen Link:**
   ```
   D:\Daten\SoftwareProjekte\Forge\forge\QUICK_REFERENCE.md
   ```
   Schnelle Befehle, wenn Sie vergessen haben

2. **Nutzen Sie INDEX.md für Navigation:**
   ```
   D:\Daten\SoftwareProjekte\Forge\forge\docs\INDEX.md
   ```
   Findet alles schnell

3. **Ausdrucken oder speichern Sie:**
   ```
   D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_SIMULATION_COMPLETE_GUIDE.md
   ```
   Die komplette Anleitung offline

---

## 🎯 Schneller Start nach Befähigung

### Ich bin Anfänger
1. Öffne: `D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_SIMULATION_COMPLETE_GUIDE.md`
2. Folge den 5 Phasen
3. Geschafft! 🎉

### Ich bin erfahren
1. Nutze: `D:\Daten\SoftwareProjekte\Forge\forge\QUICK_REFERENCE.md`
2. Führe die 4 Befehle aus
3. Analysiere die Metriken

### Ich bin Entwickler
1. Lese: `D:\Daten\SoftwareProjekte\Forge\forge\docs\SIMULATION_ANALYTICS_ARCHITECTURE.md`
2. Studiere: `D:\Daten\SoftwareProjekte\Forge\forge\forge-game\src\main\java\forge\game\simulation\`
3. Implementiere Integration in SimulateMatch.java

---

## 📞 Support

- **Anfrage:** Welchen Befehl soll ich ausführen?
  - 👉 [QUICK_REFERENCE.md](QUICK_REFERENCE.md) Kapitel "Häufige Befehle"

- **Anfrage:** Mein Deck-JSON ist fehlerhaft
  - 👉 [COMMANDER_DECK_REQUIREMENTS.md](docs/COMMANDER_DECK_REQUIREMENTS.md) Kapitel "Pflichtfelder"

- **Anfrage:** Simulation hängt
  - 👉 [QUICK_REFERENCE.md](QUICK_REFERENCE.md) Kapitel "Schnelle Fixes"

- **Anfrage:** Was bedeutet Win Rate?
  - 👉 [COMMANDER_SIMULATION_COMPLETE_GUIDE.md](docs/COMMANDER_SIMULATION_COMPLETE_GUIDE.md) Kapitel "Metrik-Interpretation"

---

**Version:** 2.0.0 | **Datum:** 2026-04-07  
**Alle absoluten Pfade für Windows (D:\Daten\SoftwareProjekte\Forge\forge)**


