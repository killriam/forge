# Scenario Starting Hand Format

Scenarios mit definierter Starthand und First-Draws erlauben es, einen exakten Spielstand
für Tests, Puzzles oder Lernmaterial zu spezifizieren.  
Der AI-Gegner behält seine vordefinierte Hand automatisch; der menschliche Spieler
(P1) kann das Mulligan-Dialog normal nutzen.

---

## Überblick: Was ein Scenario-JSON steuert

| JSON-Feld | Wer bekommt es | Was passiert |
|-----------|----------------|-------------|
| `scenario.players.PX.starting_hand` | P1 (Mensch) + P2 (AI) | Karten werden an den Anfang der Library gestellt → als Starthand gezogen |
| `scenario.players.PX.first_draws` | P1 (Mensch) + P2 (AI) | Karten werden direkt nach der Starthand in die Library gestellt → in Zug 1–N gezogen |
| `scenario.players.PX.commanders` | P1 (Mensch) + P2 (AI) | Commandanten werden in die Command Zone gelegt |
| `scenario.players.PX.battlefield` | P1 (Mensch) + P2 (AI) | Karten starten auf dem Schlachtfeld (GameState-Integration) |
| `scenario.players.PX.starting_life` | P1 (Mensch) + P2 (AI) | Life-Total zu Spielbeginn |
| `scenario.type = "opening_hand_test"` | AI-Spieler | AI überspringt Mulligan automatisch (hält Starthand) |
| `events` (top-level) | P1 (Mensch) + P2 (AI) | **Erzwungene Spielreihenfolge** — Karten/Fähigkeiten werden in dieser Reihenfolge gespielt (siehe unten) |

---

## ⚡ NEU: Erzwungene Spielreihenfolge (Forced Play Sequence)

Zusätzlich zur Starthand können Sie auch **exakt vorgeben, welche Karten in welcher Reihenfolge gespielt werden**.  
Die AI folgt dieser Sequenz automatisch — perfekt für reproduzierbare Tests und Lernszenarien.

### JSON-Struktur

Auf **Top-Level** (neben `scenario`) ein `events`-Array mit CAST/ACTIVATE/PLAY_LAND-Events:

```json
{
  "format": "mtg-replay",
  "mode": "scenario",
  "scenario": { /* ... */ },
  "events": [
    {
      "i": 1,
      "t": "T1.MP1:1",
      "a": "P1",
      "type": "PLAY_LAND",
      "data": { "card_name": "Command Tower" }
    },
    {
      "i": 2,
      "t": "T2.MP1:1",
      "a": "P1",
      "type": "CAST",
      "data": { "card_name": "Energy Tap" }
    },
    {
      "i": 3,
      "t": "T3.MP1:2",
      "a": "P1",
      "type": "CAST",
      "data": {
        "card_name": "The Pride of Hull Clade",
        "targets": []
      }
    }
  ]
}
```

### Event-Typen

| `type` | Beschreibung |
|--------|-------------|
| `"PLAY_LAND"` | Land spielen |
| `"CAST"` | Zauberspruch casten (Creature, Instant, Sorcery, Artifact, ...) |
| `"ACTIVATE"` | Aktivierte Fähigkeit nutzen (z.B. Planeswalker-Ability) |

### Event-Felder

| Feld | Typ | Beschreibung |
|------|-----|-------------|
| `i` | `int` | Event-Index (fortlaufend, z.B. 1, 2, 3, ...) |
| `t` | `string` | Zeitstempel (z.B. `"T1.MP1:1"` = Turn 1, Main Phase 1, Priority 1) |
| `a` | `string` | Actor — welcher Spieler (`"P1"`, `"P2"`, ...) |
| `type` | `string` | Event-Typ (`"CAST"`, `"ACTIVATE"`, `"PLAY_LAND"`) |
| `data.card_name` | `string` | Karten-Name |
| `data.targets` | `[string]` | Optional: Ziele der Fähigkeit (für Phase 2 — noch nicht implementiert) |

### Funktionsweise

1. Scenario-JSON wird geladen → `starting_hand` + `first_draws` + `events`
2. `ReplayPlaySequenceParser` liest das `events`-Array
3. `GameRules.forcedPlaySequence` wird mit der Karten-Reihenfolge befüllt
4. Während des Spiels: AI prüft bei jeder Priorität die Queue
   - Nächste Karte castbar? → Cast + aus Queue entfernen
   - Nicht castbar? → in Queue lassen, normal AI-Entscheidung
5. Mensch (P1): kann die Sequenz befolgen oder ignorieren

---

## Minimal-Beispiel: Starthand testen

```json
{
  "format": "mtg-replay",
  "version": "1.8.0",
  "mode": "scenario",
  "scenario": {
    "type": "opening_hand_test",
    "title": "Mein Deck — Starthand Test",
    "description": "Testet ob diese Eröffnungshand funktioniert.",
    "question": "Welche Linie ist optimal mit dieser Hand?",
    "player_count": 2,
    "players": {
      "P1": {
        "starting_hand": [
          "Sol Ring",
          "Command Tower",
          "Mana Crypt",
          "Arcane Signet",
          "Swamp",
          "Forest",
          "Island"
        ],
        "first_draws": [
          "Demonic Tutor",
          "Brainstorm",
          "Counterspell",
          "Swamp",
          "Forest"
        ],
        "starting_life": 40
      },
      "P2": {
        "starting_hand": [],
        "first_draws": [],
        "starting_life": 40
      }
    }
  },
  "meta": {
    "game_type": "commander"
  }
}
```

## ⚡ Vollständiges Beispiel: Scenario + Erzwungene Spielreihenfolge

```json
{
  "format": "mtg-replay",
  "version": "1.8.0",
  "mode": "scenario",
  "meta": {
    "game_id": "horror-forced-sequence-001",
    "timestamp": "2026-05-02T21:30:00Z",
    "game_type": "commander"
  },
  "scenario": {
    "type": "opening_hand_test",
    "title": "Horror — Erzwungene T1-T3 Sequenz",
    "description": "Testet exakte Spielreihenfolge: T1 Land, T2 Land + Spell, T3 Commander.",
    "question": "Befolge die vorgegebene Spielreihenfolge — fuehrt sie zum Erfolg?",
    "answer": "Ja: T1 Command Tower, T2 Breeding Pool + Energy Tap, T3 Commander cast.",
    "tags": ["forced_sequence", "commander", "turn3"],
    "player_count": 2,
    "players": {
      "P1": {
        "commanders": ["The Pride of Hull Clade"],
        "starting_hand": [
          "Command Tower",
          "Breeding Pool",
          "Energy Tap",
          "Beast Within",
          "Tropical Island",
          "City of Brass",
          "Crashing Drawbridge"
        ],
        "first_draws": [
          "Cactus Preserve",
          "Cyclonic Rift",
          "Ancient Adamantoise"
        ],
        "starting_life": 40
      },
      "P2": {
        "starting_hand": [],
        "first_draws": [],
        "starting_life": 40
      }
    }
  },
  "events": [
    {
      "i": 1,
      "t": "T1.MP1:1",
      "a": "Ai(1)-killriam - Horror: Dead is not an end (2026-04-21)",
      "type": "PLAY_LAND",
      "data": { "card_name": "Command Tower" }
    },
    {
      "i": 2,
      "t": "T2.MP1:1",
      "a": "Ai(1)-killriam - Horror: Dead is not an end (2026-04-21)",
      "type": "PLAY_LAND",
      "data": { "card_name": "Breeding Pool" }
    },
    {
      "i": 3,
      "t": "T2.MP1:2",
      "a": "Ai(1)-killriam - Horror: Dead is not an end (2026-04-21)",
      "type": "CAST",
      "data": { "card_name": "Energy Tap" }
    }
  ]
}
```

> ✅ **Getestet** — `hand: 7/7, draws: 3/3, 3 event(s) for 1 player(s)` — AI spielt exakt: T2 Command Tower, T2 Breeding Pool, T2 Energy Tap (Sequence befolgt)

**Wichtig:** Der `"a"`-Wert (Actor) im `events`-Array muss mit dem **tatsächlichen Lobby-Namen** des Spielers übereinstimmen.  
Bei CLI-Sim mit `-d <deck.dck>` ist der Name: `Ai(1)-<Username> - <DeckName> (<Date>)`.

---

## Vollständiges Beispiel: Commander-Szenario

```json
{
  "format": "mtg-replay",
  "version": "1.8.0",
  "mode": "scenario",
  "meta": {
    "game_id": "scenario-example-001",
    "timestamp": "2026-05-02T18:00:00Z",
    "game_type": "commander",
    "players": {
      "P1": {
        "name": "Spieler",
        "deck_name": "Horror: Dead is not an end",
        "is_ai": false
      },
      "P2": {
        "name": "AI",
        "deck_name": "Aggro",
        "is_ai": true
      }
    }
  },
  "scenario": {
    "type": "opening_hand_test",
    "title": "Horror Deck — T3 Combo Setup",
    "description": "Testet ob diese Starthand einen konsistenten Turn-3-Start ermöglicht.",
    "question": "Welche Karten sollten in diesem Turn 2 gespielt werden?",
    "answer": "Arcane Signet und Sol Ring, um Turn 3 The Pride of Hull Clade casten zu können.",
    "tags": ["ramp", "commander", "turn3"],
    "ruling_references": [],
    "player_count": 2,
    "players": {
      "P1": {
        "commanders": ["The Pride of Hull Clade"],
        "starting_hand": [
          "Sol Ring",
          "Arcane Signet",
          "Command Tower",
          "Breeding Pool",
          "Forest",
          "Island",
          "Counterspell"
        ],
        "first_draws": [
          "Beast Within",
          "Force of Vigor",
          "Brainstorm",
          "Swamp",
          "Ancient Tomb"
        ],
        "starting_life": 40
      },
      "P2": {
        "commanders": [],
        "starting_hand": [],
        "first_draws": [],
        "starting_life": 40
      }
    }
  }
}
```

---

## Feldbeschreibung

### Top-Level

| Feld | Typ | Req. | Beschreibung |
|------|-----|------|-------------|
| `format` | `string` | ✅ | Immer `"mtg-replay"` |
| `version` | `string` | ✅ | `"1.8.0"` für Scenario-Starthand-Support |
| `mode` | `string` | ✅ | Muss `"scenario"` sein — sonst wird die Datei als Replay behandelt |
| `meta` | `object` | — | Optionale Metadaten (game_id, timestamp, players, game_type) |
| `scenario` | `object` | ✅ | Das eigentliche Szenario-Objekt (siehe unten) |

### `scenario`-Objekt

| Feld | Typ | Req. | Beschreibung |
|------|-----|------|-------------|
| `type` | `string` | — | `"opening_hand_test"` → AI überspringt Mulligan.<br>`"puzzle"` → AI mulligant normal. Fehlt das Feld: normales Verhalten. |
| `title` | `string` | ✅ | Anzeige-Name in der Szenario-Liste der GUI |
| `description` | `string` | — | Erklärungstext, wird vor dem Spielstart als Dialog gezeigt |
| `question` | `string` | — | Lernfrage, wird im Dialog angezeigt |
| `answer` | `string` | — | Antwort/Lösungshinweis, wird im Dialog angezeigt |
| `tags` | `[string]` | — | Schlagwörter für Filterung (z.B. `["ramp", "combo"]`) |
| `ruling_references` | `[string]` | — | Verweise auf Regelreferenzen |
| `player_count` | `int` | — | Anzahl der Spieler (Standard: `2`) |
| `players` | `object` | — | Per-Spieler-Setup (Schlüssel: `"P1"`, `"P2"`, …) |
| `game_state` | `[string]` | — | Puzzle-Format Key=Value-Zeilen für direkten Spielzustand (siehe unten) |

### `scenario.players.PX`-Objekt

`PX` = `"P1"` (Mensch), `"P2"` (AI), `"P3"`, … für Multi-Player.

| Feld | Typ | Req. | Beschreibung |
|------|-----|------|-------------|
| `commanders` | `[string]` | — | Karten-Namen der Commandanten (gehen in die Command Zone). Leer `[]` = kein Commander. |
| `starting_hand` | `[string]` | — | **Geordnete** Karten, die als Starthand gezogen werden. Leeres Array `[]` = normale zufällige Hand. |
| `first_draws` | `[string]` | — | Karten, die nach der Starthand **oben in der Library** liegen. In dieser Reihenfolge in Zug 1, 2, 3, … gezogen. |
| `battlefield` | `[string]` | — | Karten, die zu Spielbeginn auf dem Schlachtfeld sind (GameState-Zeilen). |
| `starting_life` | `int` | — | Life Total zu Spielbeginn (Standard: 20; Commander üblich: 40). |

---

## Funktionsweise intern

```
Szenario-JSON geladen
     │
     ▼
ReplayLogParser.parse()
  → liest scenario.players.PX.starting_hand
  → liest scenario.players.PX.first_draws
  → liest scenario.players.PX.commanders
     │
     ▼
CSubmenuScenario.launchScenario()
  → rules.setScenarioStartingHands(map)   [P-ID → Karten]
  → rules.setScenarioFirstDraws(map)
  → rules.setScenarioSkipMulligan(true)   [nur bei type=opening_hand_test]
  → RegisteredPlayer mit Commandanten
     │
     ▼
GameAction.startGame()
  → Karten werden normal gemischt (Shuffle)
  → ScenarioLibrarySetup.reorderLibraries()
      ├── starting_hand-Karten → Anfang der Library
      ├── first_draws-Karten → direkt danach
      └── Rest der Library in zufälliger Reihenfolge
     │
     ▼
MulliganService
  → AI-Spieler mit scenarioSkipMulligan=true → Hand behalten
  → Mensch (P1) → normaler Mulligan-Dialog
     │
     ▼
Spiel startet — Spieler zieht seine Starthand aus der vorbereiteten Library
```

---

## Regeln und Einschränkungen

### Kartenamen
- Exakter Name wie in der Forge-Datenbank: `"Lightning Bolt"`, `"The Pride of Hull Clade"`
- **Groß-/Kleinschreibung** beachten (Forge sucht case-insensitiv, aber exakte Schreibweise ist sicherer)
- Englische Namen verwenden (Forge arbeitet intern mit englischen Karten-Namen)
- Karte nicht gefunden → `WARN`-Eintrag im Log, Karte wird übersprungen

### `starting_hand`-Länge
- Typischerweise 7 Karten (Standard-Starthand)
- Kann kürzer sein (z.B. nach Mulligan-Simulation) — Forge zieht die angegebene Anzahl
- Kann länger sein — alle Karten werden trotzdem an den Anfang der Library gestellt
- Leeres Array `[]` → keine Starthand-Kontrolle für diesen Spieler

### `first_draws`-Länge  
- Beliebig lang — alle Karten werden in Reihenfolge nach der Starthand in die Library gelegt
- Leer `[]` → keine Draws-Kontrolle

### Commander-Decks
- Commander-Karte gehört in das `commanders`-Array, **nicht** in `starting_hand` oder `first_draws`
- Der Commander ist zu Spielbeginn in der Command Zone und erscheint nicht in der Library
- Library-Struktur nach Reorder:  
  `[starting_hand (7)] + [first_draws (N)] + [verbleibende Library (zufällig)]`
- Bei 99-Karten Commander-Deck (1 Commander in Command Zone):  
  `7 + 5 + 87 = 99` Library-Karten

### `starting_hand` ohne `first_draws`
- Erlaubt — `first_draws` kann weggelassen oder leer gelassen werden
- Nur `first_draws` ohne `starting_hand` → **nicht unterstützt**, wird ignoriert

### Multi-Player
- `"P1"` = menschlicher Spieler (Index 0)
- `"P2"` = AI 1 (Index 1)
- `"P3"` = AI 2 (Index 2), usw.
- `player_count` muss entsprechend gesetzt werden (z.B. `3` für 3 Spieler)

---

## Szenario-Typen (`type`-Feld)

| Wert | Mulligan AI | Verwendung |
|------|-------------|-----------|
| `"opening_hand_test"` | AI-Spieler überspringen Mulligan | Definierte Starthand testen |
| `"puzzle"` | AI mulligant normal | Komplexe Board-State-Puzzles |
| `"rules_test"` | AI mulligant normal | Regelinteraktion nachspielen |
| (nicht gesetzt) | Normal | Generisches Szenario |

---

## Szenario laden und spielen

### Via GUI

1. Scenario-JSON in `%AppData%\Forge\games\gamelogs\` ablegen (Dateiname beliebig, endet auf `.json`)
2. Forge starten → **Replay Mode** → **Scenario Viewer**
3. Szenario aus der Liste wählen
4. **Start** klicken

> **Hinweis:** Szenario-Dateien erscheinen in der Liste wenn `"mode": "scenario"` gesetzt ist.  
> Normale Replays (`"mode": "full_game"`) erscheinen nur in der Replay-Liste.

### Via CLI (`-s` Flag)

```bash
# Scenario mit Starthand + First-Draws (ohne Forced Sequence):
java -jar forge-gui-desktop-*.jar sim \
  -d "Horror__Dead_is_not_an_end.dck" "Aggro.dck" \
  -n 1 -f Commander \
  -s path/to/scenario.json

# Scenario MIT Forced Play Sequence (events-Array im JSON):
java -jar forge-gui-desktop-*.jar sim \
  -d "Horror__Dead_is_not_an_end.dck" "Aggro.dck" \
  -n 1 -f Commander \
  -s path/to/scenario_with_forced_sequence.json
# → Startet Spiel mit definierter Hand UND erzwungener Spielreihenfolge
```

---

## Troubleshooting

### Problem: Karte wird nicht in die Starthand gelegt

**Ursache:** `starting_hand`-Karte stimmt nicht mit dem Namen in der Deck-Datei überein.

**Lösung:** Namen prüfen — Forge loggt nicht gefundene Karten als `WARN`:
```
WARN  ScenarioLibrarySetup: Card 'Sol Rng' not found in library for P1 — skipping
```

### Problem: Commander erscheint in der Library

**Ursache:** Commander ist in `starting_hand` oder `first_draws` statt in `commanders` aufgeführt.

**Lösung:** Commander nur in `scenario.players.P1.commanders` eintragen.

### Problem: Szenario erscheint nicht im Scenario Viewer

**Ursache:** `"mode": "scenario"` fehlt im JSON oder wurde falsch geschrieben.

**Lösung:** Sicherstellen dass `"mode": "scenario"` direkt auf Top-Level-Ebene steht.

### Problem: AI mulligant trotz `opening_hand_test`

**Ursache:** `"type": "opening_hand_test"` fehlt im `scenario`-Objekt.

**Lösung:** `"type": "opening_hand_test"` im `scenario`-Block setzen.

### Problem: `starting_hand` hat 7 Karten aber Spieler erhält nur 5

**Ursache:** Karten nicht im Deck vorhanden — Forge überspringt nicht gefundene Karten.

**Lösung:** Sicherstellen, dass alle `starting_hand`-Karten im Deck der Deck-Datei (`.dck`) vorhanden sind.  
Das Szenario-JSON beschreibt nur die *Reihenfolge* — die Karten müssen im Deck enthalten sein.

### Problem: Forced Play Sequence wird nicht befolgt

**Ursache:** Der `"a"`-Wert (Actor) im `events`-Array stimmt nicht mit dem Lobby-Namen des Spielers überein.

**Lösung:** Bei CLI-Sim mit `-d <deck.dck>` ist der Lobby-Name:  
`Ai(1)-<Username> - <DeckName> (<Date>)`  
Beispiel: `"Ai(1)-killriam - Horror: Dead is not an end (2026-04-21)"`

**Tipp:** Ein normales Replay-Spiel laufen lassen, dann aus dem Replay-JSON die `meta.players.<id>.name`-Werte kopieren.

### Problem: Karte aus Forced Sequence wird übersprungen

**Ursache:** Karte ist zum Zeitpunkt des Events nicht castbar (z.B. keine gültigen Targets, nicht genug Mana).

**Lösung:** Das ist **Soft Enforcement** — die AI versucht die Karte zu spielen, fällt aber auf normale AI-Logik zurück wenn nicht möglich.  
Die Karte bleibt in der Queue und wird bei der nächsten Priorität erneut versucht.

---

## Vollständige Feld-Referenz (JSON-Schema)

```json
{
  "format": "mtg-replay",          // required: "mtg-replay"
  "version": "1.8.0",              // required: minimum "1.8.0"
  "mode": "scenario",              // required: "scenario"
  "meta": {                        // optional
    "game_id": "string",
    "timestamp": "2026-05-02T18:00:00Z",
    "game_type": "commander"       // "commander"|"constructed"|...
  },
  "scenario": {                    // required
    "type": "opening_hand_test",   // optional: "opening_hand_test"|"puzzle"|"rules_test"
    "title": "string",             // required for GUI display
    "description": "string",       // optional: shown as dialog before game
    "question": "string",          // optional: shown in dialog
    "answer": "string",            // optional: shown in dialog
    "tags": ["string"],            // optional
    "ruling_references": ["string"],// optional
    "player_count": 2,             // optional, default: 2
    "players": {                   // optional: per-player structured setup
      "P1": {                      // P1=human, P2=AI-1, P3=AI-2, ...
        "commanders": ["string"],  // optional: cards placed in command zone
        "starting_hand": ["string"],// optional: ordered 7 cards for opening hand
        "first_draws": ["string"], // optional: ordered cards after opening hand
        "battlefield": ["string"], // optional: cards placed on battlefield
        "starting_life": 40        // optional, default: 20
      }
    },
    "game_state": ["string"]       // optional: puzzle-format key=value lines
  },
  "events": [                      // optional: forced play sequence (top-level!)
    {
      "i": 1,                      // event index
      "t": "T1.MP1:1",             // timestamp: Turn.Phase:Priority
      "a": "Ai(1)-PlayerName",     // actor: lobby name of player
      "type": "PLAY_LAND",         // PLAY_LAND | CAST | ACTIVATE
      "data": {
        "card_name": "string",     // card name
        "targets": ["string"]      // optional: target IDs (Phase 2)
      }
    }
  ]
}
```

---

## Beispiel-Datei für Horror: Dead is not an end

Speicherort: `%AppData%\Forge\games\gamelogs\scenario_horror_t3_test.json`

> ✅ **Getestet** — alle 7 Startkarten und alle 5 First-Draws wurden korrekt geladen (`hand: 7/7, draws: 5/5`).

```json
{
  "format": "mtg-replay",
  "version": "1.8.0",
  "mode": "scenario",
  "meta": {
    "game_id": "horror-scenario-t3-001",
    "timestamp": "2026-05-02T18:00:00Z",
    "game_type": "commander"
  },
  "scenario": {
    "type": "opening_hand_test",
    "title": "Horror: Dead is not an end — Turn 3 Setup",
    "description": "Starthand mit optimalem Mana-Setup fuer fruehen The Pride of Hull Clade.",
    "question": "Wie spielst du Turn 1 und Turn 2, um Turn 3 den Commander zu casten?",
    "answer": "T1: Command Tower + Energy Tap. T2: Breeding Pool. T3: The Pride of Hull Clade.",
    "tags": ["commander", "ramp", "turn3", "opening_hand"],
    "player_count": 2,
    "players": {
      "P1": {
        "commanders": ["The Pride of Hull Clade"],
        "starting_hand": [
          "Command Tower",
          "Breeding Pool",
          "Tropical Island",
          "Energy Tap",
          "Beast Within",
          "Crashing Drawbridge",
          "City of Brass"
        ],
        "first_draws": [
          "Cactus Preserve",
          "Dreamroot Cascade",
          "Cyclonic Rift",
          "Ancient Adamantoise",
          "Arixmethes, Slumbering Isle"
        ],
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

---

**Siehe auch:**
- `docs/FEATURE_GAME_REPLAY.md` — Replay-System Architektur
- `docs/REPLAY-TECHNIK.md` — Technische Dev-Notizen
- `docs/example_scenario_forced_sequence.json` — Vollständiges Beispiel-Szenario mit Forced Play Sequence
- `plan-scenarioStartingHand.prompt.md` — Implementierungs-Entscheidungen
- `forge-gui/src/main/java/forge/game/ReplayLogParser.java` — JSON-Parser
- `forge-game/src/main/java/forge/game/log/ScenarioLibrarySetup.java` — Library-Reorder-Engine
- `forge-gui-desktop/src/main/java/forge/view/SimulateMatch.java` — CLI-Scenario-Integration mit Forced Play Sequence









