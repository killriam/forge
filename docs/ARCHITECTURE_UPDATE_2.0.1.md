# ARCHITEKTUR-UPDATE: Externe Deck-Erstellung

**Datum:** 2026-04-07  
**Version:** 2.0.1  
**Status:** Architektur angepasst

---

## 📋 Zusammenfassung der Änderungen

### ✅ Was sich geändert hat

Die Architektur wurde angepasst mit der Annahme, dass `.dck` Dateien **extern erstellt werden**.

Das System konzentriert sich auf die **zwei Kernaufgaben:**
1. **Simulation:** Lädt `.dck` Dateien und führt 100+ Spiele durch
2. **Analytics:** Aggregiert Statistiken aus den Simulation-Logs

### ❌ Was entfernt wurde

- ~~Deck Konvertierungs-Dokumentation~~ (jetzt optional)
- ~~Phase 1.2 Schritt (Konvertierung)~~ entfernt
- ~~`convert_decklist_to_dck.py` aus Workflow~~ entfernt

### ✅ Was hinzugefügt wurde

- Schritt 0: "Deck vorbereiten (Extern)" - macht Voraussetzung explizit
- Hinweis auf externe Tools
- Flexibilität für verschiedene Deck-Quellen

---

## 🏗️ Neue Architektur (Visual)

```
┌─────────────────────────────────────────┐
│ External Tool (Deck Creation)          │
│ • Converts JSON to .dck                │
│ • Manual creation in Forge GUI          │
│ • Any other tool                        │
└──────────────┬──────────────────────────┘
               │
               ↓
        %.dck files ready
               │
               ↓
┌─────────────────────────────────────────┐
│ Forge AI Simulation                     │
│ • Reads .dck files                      │
│ • Runs 100+ games                       │
│ • Collects metrics (SimulationMetrics)  │
│ • Outputs: simulation_stats_*.json      │
└──────────────┬──────────────────────────┘
               │ 100 × ~5-10 KB
               ↓
        simulation_stats/
               │
               ↓
┌─────────────────────────────────────────┐
│ Analytics Engine (Python)               │
│ • Loads simulation_stats_*.json          │
│ • Aggregates metrics                    │
│ • Outputs: final report                 │
└──────────────┬──────────────────────────┘
               │
               ↓
   commander_simulation_report.json
```

---

## 📝 Aktualisierte Dokumentationen

| Datei | Änderungen |
|-------|-----------|
| **SIMULATION_ANALYTICS_ARCHITECTURE.md** | Data Flow angepasst, Diagram aktualisiert, Phase-Labels angepasst |
| **COMMANDER_SIMULATION_COMPLETE_GUIDE.md** | Phase 1 Schritt 1.2 entfernt, Schritt 1.0 hinzugefügt |
| **QUICK_REFERENCE.md** | 4-Schritt Workflow → 3-Schritt Workflow |
| **DOCUMENTATION_GUIDE.md** | Quick Start angepasst |
| **INDEX.md** | Workflow-Schritte umbenannt |

---

## 🔄 Neuer Workflow

### Vorher (mit Konvertierung)
```
JSON → Konvertierung → .dck → Simulation → Stats → Report
```

### Nachher (extern erstellte .dck)
```
.dck (extern) → Simulation → Stats → Report
```

**Vereinfachung:** 1 Schritt weniger im Workflow!

---

## 💡 Vorteile dieser Änderung

1. **Flexibilität:** Jedes Tool kann .dck Dateien erstellen
2. **Fokus:** System konzentriert sich auf Simulation + Analytics
3. **Wartbarkeit:** Weniger Code im Simulation-System
4. **Unabhängigkeit:** Kein binärer Dependency auf JSON-Format

---

## 🎯 Praktische Auswirkungen

### Für Nutzer
- **Vorher:** JSON → Konvertierung → Simulation (3 Befehle)
- **Nachher:** .dck Ready → Simulation (1 Befehl)
- ✅ Einfacher!

### Für Entwickler
- **Simulation** & **Analytics** sind Kernkomponenten
- Deck-Management bleibt extern
- Klare Trennung der Concerns

---

## 📋 Checkliste: Was müssen Sie wissen?

- [ ] `.dck` Dateien müssen in `%APPDATA%\Forge\decks\commander\` liegen
- [ ] Sie können von beliebigen Tools erstellt werden
- [ ] Das Simulation-System arbeitet mit bereits erstellten `.dck` Dateien
- [ ] Die drei Kernschritte sind: Sim → Analyze → Report

---

## 🔗 Aktuelle Dokumentations-Struktur

```
START HIER:
D:\Daten\SoftwareProjekte\Forge\forge\DOCUMENTATION_GUIDE.md

DANN:
- D:\Daten\SoftwareProjekte\Forge\forge\QUICK_REFERENCE.md (3-Schritt)
- D:\Daten\SoftwareProjekte\Forge\forge\docs\COMMANDER_SIMULATION_COMPLETE_GUIDE.md (Detail)

ARCHITEKTUR:
- D:\Daten\SoftwareProjekte\Forge\forge\docs\SIMULATION_ANALYTICS_ARCHITECTURE.md (Updated)
```

---

## ✨ Nächste Schritte

1. ✅ Architektur-Dokumente aktualisiert
2. ✅ Benutzer-Dokumentationen angepasst
3. ⏳ Integration in SimulateMatch.java vorbereiten
4. ⏳ Test-Run durchführen

---

**Alle Dokumentationen sind konsistent aktualisiert!** ✅

**Version:** 2.0.1 | **Datum:** 2026-04-07

