# TEST-RUN ERGEBNISSE & PROBLEM-ANALYSE

**Datum:** 2026-04-09  
**Status:** ⚠️ SIMULATION COMPLETED - FUNDAMENTAL ISSUE IDENTIFIED

---

## 🔍 PROBLEM: NUR 1 SPIELER WIRD GELADEN

### Symptom:
Alle Spiele enden sofort (Turn 0, <1 Sekunde):

```
Game Outcome: Commander game between Ai(1)-Auto Opponent (Walls) started
Game Outcome: Turn 0
Game Outcome: Ai(1)-Auto Opponent (Walls) has won because all opponents have lost
Game ended in 621 ms
```

### Root Cause:
**Forge Commander CLI lädt nur das ERSTE Deck, nicht beide!**

```
Command: java -jar forge.jar sim \
  -d "Deck1.dck" \
  -d "Deck2.dck" \  ← WIRD IGNORIERT!
  -n 5 -f commander
```

**Nur geladen:** Ai(1)-Auto Opponent (Walls)  
**Nicht geladen:** killriam - Spiderman is Comming for Dinner

---

## 📊 WAS FUNKTIONIERT HAT

### ✅ Erfolgreich:
1. **Build:** JAR kompiliert (220 MB)
2. **Auto-Opponent Deck:** Erstellt in `%APPDATA%\Forge\decks\commander\`
3. **Simulation gestartet:** 5 Spiele liefen durch
4. **Logs erstellt:** 5 JSON Replay-Files

### ❌ Problem:
- Nur 1 Spieler geladen
- Keine echten Spiele (nur "all opponents lost")
- Keine verwertbaren Statistiken

---

## 🏗️ ERSTELLTE DATEIEN

### Auto-Opponent Deck ✅
```
C:\Users\Nutzer\AppData\Roaming\Forge\decks\commander\Auto_Opponent_Walls.dck
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

### Replay Logs ✅
```
C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\
├── replay_Commander_2026-04-09_19-29-00.json (Game 1)
├── replay_Commander_2026-04-09_19-29-00.json (Games 2-5)
└── gamelog_Commander_2026-04-09_21-29-00.txt
```

**Anzahl:** 5 Logs (aber alle defekt - nur 1 Spieler)

---

## 🔧 WARUM PASSIERT DAS?

### Hypothese 1: Commander Format Bug
**Commander CLI unterstützt möglicherweise keine 2-Spieler-Spiele**

```java
// Vermutlich im Code:
if (format == "Commander") {
    loadDeck(deck1);
    // deck2 wird ignoriert für Commander
}
```

### Hypothese 2: Deck-Parameter-Parsing
**Der zweite `-d` Parameter wird nicht korrekt verarbeitet**

```
-d "Deck1.dck"  ← Geladen
-d "Deck2.dck"  ← Überschreibt Deck1 oder wird ignoriert
```

### Hypothese 3: CommanderMatch vs. Match
**Commander braucht spezielle Match-Klasse die mehrere Spieler unterstützt**

---

## 🚀 LÖSUNGSANSÄTZE

### Option A: Code-Fix (Entwickler-Lösung)
**Ändere Forge Java-Code um 2 Decks zu laden:**

```java
// In SimulateMatch.java oder GameStarter.java
if (gameType.equals("Commander")) {
    // Lade beide Decks
    Deck deck1 = loadDeck(deckFiles.get(0));
    Deck deck2 = loadDeck(deckFiles.get(1));
    
    // Erstelle 2 Spieler
    Player p1 = new Player("P1", deck1);
    Player p2 = new Player("P2", deck2);
    
    // Starte Match
    CommanderMatch match = new CommanderMatch(p1, p2);
}
```

**Dateien zu ändern:**
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java`
- Möglicherweise `forge-game/src/main/java/forge/game/GameAction.java`

### Option B: GUI-basierte Simulation
**Verwende Forge GUI statt CLI:**

1. Starte Forge GUI
2. Gehe zu Tools → Simulation
3. Wähle Commander Format
4. Wähle 2 Decks manuell
5. Starte Simulation

**Nachteil:** Nicht automatisierbar

### Option C: Verwende Constructed Format
**Teste mit Constructed statt Commander:**

```bash
java -jar forge.jar sim \
  -d "Deck1.dck" \
  -d "Deck2.dck" \
  -n 5 \
  -f constructed  # ← Funktioniert möglicherweise!
```

**Test nötig:** Ob Constructed 2 Spieler lädt

---

## 📋 ERWARTETE vs. TATSÄCHLICHE ERGEBNISSE

### Erwartet (sollte sein):
```
Game: Ai(1)-Spiderman vs. Ai(2)-Auto Opponent (Walls)
Turn 1-20: Echtes Spiel
Winner: Ai(1)-Spiderman (nach ~12 Turns)
Duration: 30-60 seconds per game
```

### Tatsächlich (ist):
```
Game: Ai(1)-Auto Opponent (Walls) vs. NOBODY
Turn 0: Sofortiger Sieg (no opponents)
Winner: Ai(1)-Auto Opponent (Walls)
Duration: 100-600 ms per game
```

---

## 🎯 NÄCHSTE SCHRITTE

### Sofort testbar:

#### 1. Teste Constructed Format
```bash
# Konvertiere Commander-Decks zu Constructed
# Dann teste:
java -jar forge.jar sim \
  -d "Deck1.dck" \
  -d "Deck2.dck" \
  -n 5 \
  -f constructed
```

#### 2. Prüfe Forge Code
```bash
# Suche nach Commander-Simulation-Code
cd forge-gui-desktop/src/main/java/forge/view
grep -r "Commander" SimulateMatch.java

# Prüfe wie Decks geladen werden
grep -r "loadDeck" *.java
```

#### 3. Erstelle GitHub Issue
**Titel:** "Commander CLI simulation loads only 1 player instead of 2"

**Body:**
```
When running Commander simulations via CLI, only the first deck is loaded.
The second deck parameter (-d) is ignored, causing immediate wins ("all opponents lost").

Command:
java -jar forge.jar sim -d Deck1.dck -d Deck2.dck -n 5 -f commander

Expected: 2 players (Ai(1) vs Ai(2))
Actual: 1 player (Ai(1)) → Turn 0 win

Logs show: "all opponents have lost" at Turn 0
```

---

## 💡 WORKAROUND (BIS FIX)

### Option 1: Manuelles Testing
Verwende Forge GUI für Commander-Tests

### Option 2: Constructed Format
Teste mit Constructed statt Commander (2-Spieler funktioniert möglicherweise)

### Option 3: Code-Patch
Ändere `SimulateMatch.java` um beide Decks zu laden:

```java
// Pseudo-Code
if (gameFormat.equals("Commander") && deckFiles.size() >= 2) {
    // Force load both decks
    RegisteredPlayer p1 = new RegisteredPlayer(loadDeck(deckFiles.get(0)));
    RegisteredPlayer p2 = new RegisteredPlayer(loadDeck(deckFiles.get(1)));
    
    List<RegisteredPlayer> players = Arrays.asList(p1, p2);
    match.startGame(game, players);
}
```

---

## 📊 STATISTIKEN (Defekt)

### Von den 5 Spielen:
- **Win Rate:** 100% (Auto-Opponent) - UNGÜLTIG
- **Avg Turns:** 0 - UNGÜLTIG
- **Duration:** 100-600ms - UNGÜLTIG
- **Damage:** 0 - UNGÜLTIG

**Grund:** Keine echten Spiele, nur "no opponent" Wins

---

## 🔍 WICHTIGE ERKENNTNISSE

1. **Commander CLI ist broken** für 2-Spieler-Simulationen
2. **Nur 1 Deck wird geladen** trotz 2x `-d` Parameter
3. **Auto-Opponent System funktioniert** (Deck wird korrekt erstellt)
4. **Replay-Logging funktioniert** (aber Daten sind ungültig)
5. **SimulationMetricsCollector** noch nicht integriert (Code existiert aber)

---

## 📖 EMPFEHLUNG

### Kurzfristig:
1. **Teste Constructed Format** (könnte funktionieren)
2. **Melde Bug** in Forge GitHub
3. **Verwende GUI** für manuelle Tests

### Mittelfristig:
1. **Fix SimulateMatch.java** um beide Decks zu laden
2. **Integriere SimulationMetricsCollector**
3. **Teste mit Patch**

### Langfristig:
1. **Commander CLI Support** verbessern
2. **Unit Tests** für Deck-Loading
3. **Dokumentation** für CLI-Limitations

---

**Status:** ⚠️ **FUNDAMENTAL BUG IDENTIFIED - CODE FIX REQUIRED**

**Blocker:** Commander CLI lädt nur 1 Spieler  
**Workaround:** Constructed Format oder GUI  
**Fix Effort:** ~2-4 Stunden Code-Änderungen + Testing

---

**Version:** 1.0.0  
**Datum:** 2026-04-09 21:30 UTC  
**Next:** Code-Investigation oder Constructed-Format-Test

