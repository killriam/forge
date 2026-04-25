# AUTO-OPPONENT SYSTEM - DOCUMENTATION

**Version:** 2.1.0  
**Datum:** 2026-04-09  
**Feature:** Automatische Gegner-Deck-Erstellung

---

## 🎯 ÜBERSICHT

Das System erstellt automatisch ein Standard-Gegner-Deck wenn kein zweites Deck angegeben wird.

### Standard-Gegner-Deck:
- **Commander:** The Walls of Ba Sing Se
- **Main:** 99x Wastes
- **Deck-Name:** `Auto_Opponent_Walls.dck`

---

## 📋 NUTZUNG

### Option 1: Mit eigenem Gegner-Deck

```powershell
.\run_commander_simulation.ps1 -Deck1 "MyDeck" -Deck2 "killriam - dummy defender (2026-04-09)" -Games 10
```

**Verwendet:**
- Deck 1: MyDeck.dck
- Deck 2: killriam - dummy defender (2026-04-09).dck

### Option 2: Automatischer Gegner (NEU!)

```powershell
.\run_commander_simulation.ps1 -Deck1 "MyDeck" -Games 10
```

**System erstellt automatisch:**
- Deck 1: MyDeck.dck
- Deck 2: Auto_Opponent_Walls.dck (auto-generiert)

**Commander:** The Walls of Ba Sing Se  
**Main:** 99x Wastes (Basicland, farbloses Mana)

---

## 🔧 FUNKTIONSWEISE

### Workflow:

1. **Prüfe Deck2-Parameter:**
   ```
   if (-not $Deck2 -or $Deck2 -eq ""):
       → Kein Gegner angegeben
   ```

2. **Erstelle Auto-Opponent:**
   ```
   create_default_opponent.ps1
   → Erstellt: Auto_Opponent_Walls.dck
   ```

3. **Verwende Auto-Opponent:**
   ```
   Deck2 = "Auto_Opponent_Walls"
   ```

4. **Fallback bei Fehler:**
   ```
   if (creation failed):
       Deck2 = Deck1  # Mirror Match
   ```

---

## 📁 DATEIEN

### Erstellte Skripte:

| Datei | Zweck |
|-------|-------|
| `create_default_opponent.ps1` | PowerShell Opponent Generator |
| `create_default_opponent.bat` | Batch Opponent Generator (Fallback) |
| `run_commander_simulation.ps1` | Haupt-Simulations-Skript (Updated) |

### Erstellt-Deck:

```
%APPDATA%\Forge\decks\commander\Auto_Opponent_Walls.dck
```

**Inhalt:**
```
[metadata]
Name=Auto Opponent (Walls)
[Commander]
1 The Walls of Ba Sing Se
[Main]
99 Wastes
[Sideboard]
```

---

## 🎮 BEISPIELE

### Beispiel 1: Test gegen Auto-Opponent
```powershell
.\run_commander_simulation.ps1 -Deck1 "killriam - Spiderman is Comming for Dinner (2026-04-06)" -Games 10
```

**Output:**
```
⚙️  No opponent specified - creating default opponent...
✓ Using: Auto_Opponent_Walls (The Walls of Ba Sing Se + 99x Wastes)
✓ Deck 1: killriam - Spiderman is Comming for Dinner (2026-04-06).dck
✓ Deck 2: Auto_Opponent_Walls.dck
```

### Beispiel 2: Custom Gegner
```powershell
.\run_commander_simulation.ps1 `
    -Deck1 "killriam - Spiderman is Comming for Dinner (2026-04-06)" `
    -Deck2 "killriam - dummy defender (2026-04-09)" `
    -Games 10
```

**Output:**
```
✓ Using opponent: killriam - dummy defender (2026-04-09)
✓ Deck 1: killriam - Spiderman is Comming for Dinner (2026-04-06).dck
✓ Deck 2: killriam - dummy defender (2026-04-09).dck
```

---

## 🔍 WARUM "THE WALLS OF BA SING SE"?

### Eigenschaften:
- **Defender:** Kann nicht angreifen
- **High Toughness:** Schwer zu entfernen
- **Passiv:** Macht nichts aktives
- **Ideal für Testing:**
  - Gegner spielt keine Spells
  - Fokus liegt auf Deck 1's Performance
  - Konsistente Baseline für Metriken

### Warum 99x Wastes?
- **Basicland:** Kann immer gespielt werden
- **Farbloses Mana:** Funktioniert mit jedem Commander
- **Keine Strategie:** Gegner macht nichts außer Lands spielen
- **Maximale Konsistenz:** Jedes Spiel identisch

---

## ⚙️ MANUELLE ERSTELLUNG

Falls das Skript nicht funktioniert:

### PowerShell:
```powershell
$deckPath = "$env:APPDATA\Forge\decks\commander\Auto_Opponent_Walls.dck"
@"
[metadata]
Name=Auto Opponent (Walls)
[Commander]
1 The Walls of Ba Sing Se
[Main]
99 Wastes
[Sideboard]
"@ | Out-File -FilePath $deckPath -Encoding UTF8 -Force
```

### Batch:
```bat
cd /d "%APPDATA%\Forge\decks\commander"
echo [metadata] > Auto_Opponent_Walls.dck
echo Name=Auto Opponent (Walls) >> Auto_Opponent_Walls.dck
echo [Commander] >> Auto_Opponent_Walls.dck
echo 1 The Walls of Ba Sing Se >> Auto_Opponent_Walls.dck
echo [Main] >> Auto_Opponent_Walls.dck
echo 99 Wastes >> Auto_Opponent_Walls.dck
echo [Sideboard] >> Auto_Opponent_Walls.dck
```

### Manuell (Text-Editor):
1. Öffne: `%APPDATA%\Forge\decks\commander\Auto_Opponent_Walls.dck`
2. Füge ein:
   ```
   [metadata]
   Name=Auto Opponent (Walls)
   [Commander]
   1 The Walls of Ba Sing Se
   [Main]
   99 Wastes
   [Sideboard]
   ```
3. Speichere mit UTF-8 Encoding

---

## 🎯 VERWENDUNGSZWECKE

### 1. Deck-Performance-Testing
**Ziel:** Wie performt mein Deck gegen passiven Gegner?

```powershell
.\run_commander_simulation.ps1 -Deck1 "MyDeck" -Games 100
```

**Metriken:**
- Win-Rate (sollte ~100% sein)
- Average Turns to Win
- Damage Output
- Spell Velocity
- Mana Curve Efficiency

### 2. Goldfish Testing
**Ziel:** Deck spielt gegen sich selbst (Speed-Test)

```powershell
.\run_commander_simulation.ps1 -Deck1 "MyComboD eck" -Games 50
```

**Analyse:**
- Wie schnell gewinnt das Deck?
- Konsistenz der Win-Condition
- Mulligan-Performance

### 3. Mulligan-Strategie-Testing
**Ziel:** Teste Mulligan-Entscheidungen

```powershell
.\run_commander_simulation.ps1 -Deck1 "MyDeck" -Games 100
```

**Vergleiche:**
- Mulligan-Rate
- Win-Rate mit/ohne Mulligan
- Optimale Hand-Evaluation

---

## 📊 ERWARTETE ERGEBNISSE

### Gegen Auto-Opponent (Walls):

**Typische Metriken:**
- **Win Rate:** 95-100% (wenn Deck funktioniert)
- **Avg Turns:** 8-15 (abhängig vom Deck)
- **Damage Dealt:** 40+ (Commander = 40 Life)
- **Spell Velocity:** 1.5-2.5 spells/turn

### Flags für Probleme:
- ⚠️ **Win Rate < 90%:** Deck hat Probleme
- ⚠️ **Avg Turns > 20:** Deck ist zu langsam
- ⚠️ **Missed Land Drops > 2:** Mana-Base Problem

---

## 🔧 TROUBLESHOOTING

### Problem: "Deck not found: Auto_Opponent_Walls.dck"

**Lösung:**
```powershell
# Erstelle manuell
cd "D:\Daten\SoftwareProjekte\Forge\forge"
.\create_default_opponent.bat
```

### Problem: "The Walls of Ba Sing Se" ist unbekannt

**Ursache:** Karte existiert nicht in Forge

**Alternative Commander:**
```
1 Wall of Denial      # U/W Defender, Shroud
1 Wall of Omens       # W Defender, Draw
1 Perimeter Captain   # W Defender, Lifegain
```

**Edit Deck:**
```
%APPDATA%\Forge\decks\commander\Auto_Opponent_Walls.dck
```

Ändere:
```
[Commander]
1 Wall of Denial
```

---

## 📝 NÄCHSTE SCHRITTE

1. **Teste Auto-Opponent:**
   ```powershell
   .\run_commander_simulation.ps1 -Deck1 "YourDeck" -Games 10
   ```

2. **Prüfe Logs:**
   ```
   %APPDATA%\Forge\games\simulation_stats\
   ```

3. **Analysiere:**
   ```powershell
   python analyze_commander_stats.py
   ```

4. **Optimiere Deck** basierend auf Metriken

---

**Version:** 2.1.0  
**Status:** ✅ IMPLEMENTED  
**Last Updated:** 2026-04-09

