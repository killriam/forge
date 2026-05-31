# Plan: Definierte Starthand + First-5-Draws als Szenario

## Kontext

Das Datenmodell (`Scenario.PlayerSetup`, `ScenarioInfo`) und das JSON-Parsing sind bereits
vollständig implementiert. Das **kritische Problem** liegt in
`ReplayLogParser.ScenarioInfo.buildGameStateFromPlayerSetup()`: es schreibt
`humanlibrary=CardA;CardB;...` mit ausschließlich den `first_draws`-Karten — das
überschreibt das komplette Deck (z.B. 93 Karten bei Commander) mit nur 5 Karten.
Die Lösung folgt dem Muster von `ReplayLibraryReorderer`.

---

## Betroffene Dateien

| Datei | Modul | Rolle |
|-------|-------|-------|
| `forge-game/.../log/ScenarioLibrarySetup.java` | `forge-game` | **NEU** — reordert Library nach Shuffle |
| `forge-game/.../GameRules.java` | `forge-game` | Neue Felder: `scenarioStartingHands`, `scenarioFirstDraws`, `scenarioSkipMulligan` |
| `forge-game/.../GameAction.java` | `forge-game` | Aufruf von `ScenarioLibrarySetup` + Mulligan-Skip |
| `forge-gui/.../game/ReplayLogParser.java` | `forge-gui` | Fix: `library=`-Zeilen aus `buildGameStateFromPlayerSetup()` entfernen |
| `forge-gui-desktop/.../replay/CSubmenuScenario.java` | `forge-gui-desktop` | Szenario-Setup an `GameRules` übergeben; `setStartingHand(0)` ggf. entfernen |

---

## Schritt-für-Schritt-Implementierung

### Schritt 1 — Neue Klasse `ScenarioLibrarySetup` (forge-game)

Datei: `forge-game/src/main/java/forge/game/log/ScenarioLibrarySetup.java`

Modelliert nach `ReplayLibraryReorderer`. Methode:

```java
public static void reorderLibraries(
        Game game,
        Map<String, List<String>> startingHands,
        Map<String, List<String>> firstDraws)
```

Algorithmus pro Spieler (`P1` → index 0, `P2` → index 1, …):

1. Aktuellen Library-Inhalt als veränderliche Liste laden.
2. Alle Karten aus `startingHands.get("PX")` (in Reihenfolge) an den Anfang verschieben.
3. Direkt dahinter alle Karten aus `firstDraws.get("PX")` (in Reihenfolge) einfügen.
4. Alle verbleibenden Karten (nicht in Schritt 2/3 verwendet) in ihrer aktuellen (zufälligen) Reihenfolge anhängen.
5. `player.getZone(ZoneType.Library).setCards(reorderedList)` aufrufen.

**Vorbedingung**: `ScenarioLibrarySetup` wird **nur aufgerufen wenn `startingHands` befüllt ist**.
Ohne `starting_hand` macht das Szenario keinen Sinn. Ein befülltes `firstDraws` allein reicht nicht.

Commander-Karte ist bereits in der Command Zone — sie taucht **nicht** in der Library auf
und wird vom Reorder nicht berührt. Bei einem 1-Commander-Deck (Standard):
`[7 starting_hand] + [5 first_draws] + [87 shuffled]` = 99 Library-Karten.

Fehlerbehandlung:
- Fehlende Karte → `WARN`-Log, überspringen (wie `ReplayLibraryReorderer`).
- Duplikat-Namen (z.B. 4× Lightning Bolt): ersten nicht-verwendeten Treffer nehmen.

---

### Schritt 2 — `GameRules` erweitern (forge-game)

Datei: `forge-game/src/main/java/forge/game/GameRules.java`

Neue Felder und Getter/Setter:

```java
/** Per-player starting hand cards for scenario mode. Key = "P1", "P2", … */
private Map<String, List<String>> scenarioStartingHands = null;

/** Per-player first-N draw cards (top of library) for scenario mode. Key = "P1", "P2", … */
private Map<String, List<String>> scenarioFirstDraws = null;

/**
 * When true: skip the MulliganService for this game (used for opening_hand_test scenarios).
 * Ensures the starting hand is deterministic.
 */
private boolean scenarioSkipMulligan = false;
```

---

### Schritt 3 — `GameAction.startGame()` anpassen (forge-game)

Datei: `forge-game/src/main/java/forge/game/GameAction.java`

**A) Library-Reorder nach dem bestehenden `replayLogPath`-Block:**

```java
// Scenario mode: reorder library to enforce starting hand + first draws
// Only active when starting hands are explicitly defined (first_draws alone is not supported)
Map<String, List<String>> scenStartingHands = game.getRules().getScenarioStartingHands();
if (scenStartingHands != null && !scenStartingHands.isEmpty()) {
    Map<String, List<String>> scenFirstDraws = game.getRules().getScenarioFirstDraws();
    forge.game.log.ScenarioLibrarySetup.reorderLibraries(
            game,
            scenStartingHands,
            scenFirstDraws != null ? scenFirstDraws : Collections.emptyMap());
}
```

**B) Mulligan-Skip für AI-Spieler** — der menschliche Spieler mulligant normal.
`MulliganService.perform()` bleibt erhalten; die Änderung greift **innerhalb**
von `MulliganService`: AI-Spieler mit `scenarioSkipMulligan=true` behalten ihre
Hand automatisch (kein Mulligan-Dialog, kein Scry).

Alternativ: `MulliganService` prüft pro Spieler:
```java
if (player.isAI() && game.getRules().isScenarioSkipMulligan()) {
    // keep hand, skip mulligan entirely for this AI player
    continue;
}
```

---

### Schritt 4 — `buildGameStateFromPlayerSetup()` fixen (forge-gui)

Datei: `forge-gui/src/main/java/forge/game/ReplayLogParser.java`

In `ScenarioInfo.buildGameStateFromPlayerSetup()`:

- **Entfernen**: den `for`-Block, der `prefix + "library=" + ...` erzeugt.
- **Entfernen**: den `for`-Block, der `prefix + "hand=" + ...` erzeugt.
  (Starthand kommt jetzt aus Library-Reorder + normalem Draw, nicht aus GameState.)
- **Behalten**: `command=`-Zeilen **nur wenn KEINE** `RegisteredPlayer`-Commander gesetzt
  wurden — d.h. wenn `playerCommanders.isEmpty()`. Dopplung vermeiden.
- **Behalten**: `battlefield=`- und `life=`-Zeilen unverändert.

---

### Schritt 5 — `CSubmenuScenario.launchScenario()` anpassen (forge-gui-desktop)

Datei: `forge-gui-desktop/src/main/java/forge/screens/home/replay/CSubmenuScenario.java`

**A) Szenario-Setup an `GameRules` übergeben:**

```java
GameRules rules = new GameRules(hasCommanders ? GameType.Commander : GameType.Puzzle);
rules.setGamesPerMatch(1);
rules.setScenarioMode(true);

// Scenario library setup — only when starting hands are defined
if (si != null && !si.playerStartingHands.isEmpty()) {
    rules.setScenarioStartingHands(si.playerStartingHands);
    if (!si.playerFirstDraws.isEmpty()) {
        rules.setScenarioFirstDraws(si.playerFirstDraws);
    }
}
// Skip AI mulligan for opening_hand_test type (human may still mulligan freely)
if (si != null && "opening_hand_test".equals(si.type)) {
    rules.setScenarioSkipMulligan(true);
}
```

**B) `setStartingHand(0)` entfernen** (Ansatz B: Library-Reorder liefert die Starthand):

- `human.setStartingHand(0)` und `ai.setStartingHand(0)` **entfernen**.
- Alle Spieler ziehen ihre Starthand normal aus der reorderter Library.
- `buildGameStateFromPlayerSetup()` erzeugt keine `hand=`-Zeilen mehr.

**C) Mulligan-Scope**: `scenarioSkipMulligan=true` nur für `opening_hand_test` setzen.
Gilt in `MulliganService` nur für AI-Spieler — der menschliche Spieler mulligant frei.

---

## JSON-Szenario-Format (Beispiel)

```json
{
  "format": "mtg-replay",
  "version": "1.8.0",
  "mode": "scenario",
  "scenario": {
    "type": "opening_hand_test",
    "title": "Fast Combo T3 Win — Test",
    "description": "Verify that the specific opening hand enables a Turn-3 kill.",
    "question": "What is the optimal line of play with this hand?",
    "player_count": 2,
    "players": {
      "P1": {
        "starting_hand": [
          "Sol Ring",
          "Command Tower",
          "Mana Crypt",
          "Dark Ritual",
          "Demonic Tutor",
          "Swamp",
          "Necropotence"
        ],
        "first_draws": [
          "Ad Nauseam",
          "Mox Diamond",
          "Lion's Eye Diamond",
          "Dark Ritual",
          "Swamp"
        ],
        "commanders": ["Urza, Lord High Artificer"],
        "starting_life": 40
      },
      "P2": {
        "starting_hand": [],
        "first_draws": [],
        "starting_life": 40
      }
    }
  }
}
```

**JSON-Konventionen** (aus `mtg-replay-notation` Spec):
- `snake_case` Keys
- `starting_hand`: exakte Karten für die Eröffnungshand (Mulligan wird übersprungen)
- `first_draws`: geordnete Karten oben in der Library (werden in dieser Reihenfolge gezogen)
- Leere Arrays = kein Override (normales Verhalten)
- `commanders`: Kommandant(en) in der Command Zone

---

## Entscheidungen (festgelegt)

1. **Mulligan-Scope**: Mulligan **nur für AI-Spieler** überspringen; der menschliche Spieler
   (P1) darf normal mulliganen — er hat seine Starthand bereits explizit definiert und
   entscheidet selbst, ob er sie behalten möchte.
   Umsetzung: `scenarioSkipMulligan` gilt nur für `player.isAI()` in `MulliganService`
   bzw. in `GameAction`-Logik.

2. **Ansatz B (Clean)**: `setStartingHand(0)` entfernen, Library-Reorder für
   `starting_hand` + `first_draws` in einem Schritt, normaler Draw danach.
   `buildGameStateFromPlayerSetup()` erzeugt **keine** `hand=`- oder `library=`-Zeilen mehr.

3. **Deck-Vollständigkeit**: Fehlende Karte → `WARN`-Log + überspringen
   (wie `ReplayLibraryReorderer`). Kein harter Fehler.

4. **Commander-Deck-Struktur**: 1 Commander-Karte in der Command Zone + 99 Library-Karten
   (Standardformat). Reorder-Ergebnis:
   `[7 starting_hand] + [5 first_draws] + [87 shuffled]`.
   Der Commander ist **nicht** in der Library und wird vom Reorder nicht berührt.

5. **Nur `first_draws` ohne `starting_hand`**: Nicht unterstützt — macht keinen Sinn als
   Szenario-Typ. Wenn `playerStartingHands` leer ist aber `playerFirstDraws` befüllt,
   wird `ScenarioLibrarySetup` gar nicht aufgerufen.

---

## Implementierungsstatus ✅ ABGESCHLOSSEN

| Komponente | Status |
|------------|--------|
| `Scenario.PlayerSetup` Modell | ✅ vorhanden |
| `ReplayLogParser` JSON-Parsing | ✅ vorhanden |
| `ScenarioInfo.playerStartingHands` / `playerFirstDraws` | ✅ vorhanden |
| `buildGameStateFromPlayerSetup()` — `hand=`-Zeilen | ✅ entfernt (Ansatz B) |
| `buildGameStateFromPlayerSetup()` — `library=`-Zeilen | ✅ entfernt (Bug behoben) |
| `buildGameStateFromPlayerSetup()` — `command=`-Zeilen | ✅ entfernt (kein Duplikat mehr) |
| `CSubmenuScenario` Commander-Handling | ✅ vorhanden (RegisteredPlayer) |
| `CSubmenuScenario` `setStartingHand(0)` | ✅ entfernt (Ansatz B) |
| `CSubmenuScenario` GameRules-Szenario-Setup | ✅ implementiert |
| `ScenarioLibrarySetup` Klasse | ✅ neu erstellt (`forge-game/.../log/`) |
| `ScenarioKeepMulligan` Helferklasse | ✅ neu erstellt (`forge-game/.../mulligan/`) |
| `GameRules` Szenario-Felder | ✅ hinzugefügt (`scenarioStartingHands`, `scenarioFirstDraws`, `scenarioSkipMulligan`) |
| `GameAction` Szenario-Reorder-Aufruf | ✅ implementiert (nach replayLogPath-Block) |
| `MulliganService` AI-Mulligan-Skip | ✅ implementiert (nur AI-Spieler, Mensch mulligant frei) |
| `Match.java` pre-existierender Fehler (`chooseCardsForAnte`) | ✅ nebenher behoben |
| Maven-Build `forge-game` | ✅ kompiliert fehlerfrei |
| Maven-Build `forge-gui` + `forge-gui-desktop` | ✅ kompiliert fehlerfrei |










