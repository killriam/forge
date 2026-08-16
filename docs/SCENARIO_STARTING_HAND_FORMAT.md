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

> **Actor identity (`"a"`):** always a plain player id — `"P1"`, `"P2"`, … — matching the
> `scenario.players` keys elsewhere in the same file. It is **not** the in-game lobby name.
> `CSubmenuScenario`/`SimulateMatch` translate this id to whatever name they actually assign
> that seat at launch time before handing the sequence to `GameRules.setForcedPlaySequence()` —
> callers never need to predict or reconstruct Forge's internal naming conventions
> (`Ai(N)-<deckName>`, profile names, etc.). This replaced an earlier, broken design where the
> exporter had to guess the exact runtime lobby-name string; see Troubleshooting below.

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
| `data.targets` | `[string]` | Optional: Ziele der Fähigkeit, per Name (Kartenname oder Spieler-ID). Wird beim Demo-Play-Export (`DemoPlaySequenceExtractor`) automatisch aus der Aufzeichnung aufgelöst und eingetragen — beim Abspielen einer Sequenz aber noch **nicht ausgewertet** (die AI wählt ihre eigenen Ziele normal; siehe "Getestet, aber noch nicht konsumiert" unten). |
| `data.sacrifice` | `[string]` | Optional: als Zusatzkosten geopferte Karte(n) (z.B. bei Metamorphosis), per Name. Gleiche Einschränkung wie `data.targets` — wird aufgezeichnet, aber beim Abspielen noch nicht ausgewertet. |

### Funktionsweise

1. Scenario-JSON wird geladen → `starting_hand` + `first_draws` + `events`
2. `ReplayLogParser.parseForcedSequenceEvents()` liest das `events`-Array → Map
   `Spieler-ID ("P1"/"P2"/…) → Karten-Reihenfolge`
3. Der Launcher (`CSubmenuScenario` für die GUI, `SimulateMatch` für `-s`) übersetzt jede
   Spieler-ID in den tatsächlich vergebenen Lobby-Namen dieses Laufs (Mensch: konfigurierter
   Spielername; AI: der Name, mit dem der Sitz erzeugt wurde) und befüllt
   `GameRules.forcedPlaySequence` mit dem übersetzten Ergebnis — **derselbe Mechanismus**, den
   der `-r`-Replay-Modus für echte abgeschlossene Spiele nutzt (siehe `AiController`,
   "Forced play sequence from replay").
4. Während des Spiels: AI prüft bei jeder Priorität die Queue
   - Nächste Karte castbar? → Cast + aus Queue entfernen
   - Nicht castbar? → in Queue lassen, normal AI-Entscheidung, **innerhalb desselben Zugs**
     erneut versuchen
   - **Backup-Plan:** ist die Karte am Ende des Zugs, in dem sie zuerst an der Reihe war,
     immer noch nicht spielbar gewesen, gibt die AI diesen einen Eintrag auf — kein
     unbegrenztes Retry mehr. Der Eintrag wird aus der Queue entfernt (nicht die restliche
     Sequenz), ein `AI_DECISION`-Eintrag landet im sichtbaren Spiel-Log ("Scripted play
     skipped for `<Name>`: '`<Karte>`' was never castable during turn `<N>` - moving on."),
     und die AI macht mit dem nächsten Queue-Eintrag weiter. Siehe
     `AiController.chooseSpellAbilityToPlay()`, Felder `forcedSeqHeadCardName`/
     `forcedSeqHeadFirstSeenTurn`.
5. Mensch (P1): kann die Sequenz befolgen oder ignorieren — für ihn gibt es keine
   Retry-Begrenzung, da nichts automatisch für ihn gespielt wird; der Hinweis
   (`CPrompt`, "💡 Scripted line suggests: …") zeigt einfach weiter den aktuellen Queue-Kopf,
   bis er selbst die passende Karte spielt (siehe `GameRules.popForcedPlayIfMatches`).

**Beide Einstiegspunkte unterstützt seit Fork-Version, in der dieser Absatz aktualisiert
wurde:** die GUI **Replay Scenario**-Submenu (`CSubmenuScenario`) und der CLI `-s`-Flag
(`SimulateMatch`). Zuvor wertete nur `-s` das `events`-Array überhaupt aus — und selbst dort
wurde die Spieler-ID (`"P1"`) fälschlich direkt als Lobby-Name verwendet, wodurch die
Sequenz nie zum tatsächlichen `player.getLobbyPlayer().getName()` passte und niemals feuerte.

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
      "a": "P1",
      "type": "PLAY_LAND",
      "data": { "card_name": "Command Tower" }
    },
    {
      "i": 2,
      "t": "T2.MP1:1",
      "a": "P1",
      "type": "PLAY_LAND",
      "data": { "card_name": "Breeding Pool" }
    },
    {
      "i": 3,
      "t": "T2.MP1:2",
      "a": "P1",
      "type": "CAST",
      "data": { "card_name": "Energy Tap" }
    }
  ]
}
```

> ✅ **Getestet** — `hand: 7/7, draws: 3/3, 3 event(s) for 1 player(s)` — AI spielt exakt: T2 Command Tower, T2 Breeding Pool, T2 Energy Tap (Sequence befolgt)

**Wichtig:** Der `"a"`-Wert (Actor) im `events`-Array ist eine **Spieler-ID** (`"P1"`, `"P2"`, …) —
dieselbe, die auch unter `scenario.players` verwendet wird. Er muss **nicht** mit dem
tatsächlichen Lobby-Namen übereinstimmen; `CSubmenuScenario`/`SimulateMatch` übernehmen die
Übersetzung selbst, da nur der Launcher zum Startzeitpunkt weiß, welchen Namen ein Sitz
bekommt. (Frühere Fork-Versionen verlangten hier fälschlich einen vorkonstruierten
Lobby-Namen-String wie `Ai(1)-<Username> - <DeckName> (<Date>)` — das führte dazu, dass die
Sequenz nie feuerte, siehe Troubleshooting unten.)

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
| `id` | `string` | — | Optionale stabile Kennung, um dieses Szenario aus einem Deck-File zu referenzieren (siehe "Von einem Deck referenzieren" unten). Fehlt sie, wird die Datei über ihren Dateinamen (ohne `.json`) referenziert. |
| `type` | `string` | — | `"opening_hand_test"` → AI überspringt Mulligan.<br>`"puzzle"` → AI mulligant normal. Fehlt das Feld: normales Verhalten. |
| `name` | `string` | ✅ | Anzeige-Name in der Szenario-Tabelle der GUI (Spalte "Name"). Bevorzugtes Feld — entspricht `DecklistScenario.name` in mtg-replay-notation §6.4. |
| `title` | `string` | — | **Deprecated**, Alias für `name`. Ältere Dateien, die nur `title` setzen, funktionieren weiterhin (`name` fällt automatisch auf `title` zurück); neue Dateien sollten `name` verwenden. |
| `deck_id` | `string` | — | Kennung des Decks, zu dem dieses Szenario gehört (entspricht `DecklistScenario.deck_id`). Wenn gesetzt, wird sie in der Szenario-Tabelle als Spalte "Deck" angezeigt (authoritative) — sonst fällt die Anzeige zurück auf `meta.players.P1.deck_name`, dann auf eine Rückwärtssuche über die `Scenario=`-Metadaten aller Decks. |
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

1. Scenario-JSON in `%AppData%\Forge\games\scenarios\` ablegen (Dateiname beliebig, endet auf `.json`) — **nicht** mehr in `gamelogs\`, das ist seit der Einführung eines eigenen Scenario-Ordners nur noch für echte gespielte Partien (Game Recap) gedacht.
2. Forge starten → **Replay Mode** → **Scenario Viewer**
3. Szenario aus der Liste wählen
4. **Start** klicken

> **Hinweis:** Szenario-Dateien erscheinen in der Liste wenn `"mode": "scenario"` gesetzt ist.  
> Normale Replays (`"mode": "full_game"`) erscheinen nur in der Replay-Liste.

### Via CLI (`-scenario` Flag)

```bash
# Scenario mit Starthand + First-Draws (ohne Forced Sequence):
java -jar forge-gui-desktop-*.jar sim \
  -d "Horror__Dead_is_not_an_end.dck" "Aggro.dck" \
  -n 1 -f Commander \
  -scenario path/to/scenario.json

# Scenario MIT Forced Play Sequence (events-Array im JSON):
java -jar forge-gui-desktop-*.jar sim \
  -d "Horror__Dead_is_not_an_end.dck" "Aggro.dck" \
  -n 1 -f Commander \
  -scenario path/to/scenario_with_forced_sequence.json
# → Startet Spiel mit definierter Hand UND erzwungener Spielreihenfolge
```

> **Achtung, Flag umbenannt:** Das Scenario-Flag heißt `-scenario`, **nicht** `-s` — `-s` ist
> bereits durch den RNG-Seed-Parameter belegt (`Long.parseLong()` auf einen Dateipfad crasht
> sofort). Beide Flags teilten sich früher denselben Schlüssel; das war ein Bug, kein
> Konfigurationsfehler des Aufrufers, und wurde beim Fix der Forced-Play-Sequence-Übersetzung
> (siehe oben) korrigiert.

### Von einem Deck referenzieren (Constructed/Commander-Match, nicht Puzzle-Modus)

Bisher war ein Szenario nur über den separaten, isolierten **Scenario Viewer** (Puzzle-Modus mit
leerem Deck) spielbar. Ein `.dck`-Deck-File kann jetzt zusätzlich per Metadaten-Schlüssel auf
ein oder mehrere Szenario-Dateien verweisen, damit dasselbe Szenario auch in einem echten
Constructed/Commander-Match mit zwei realen Decks angehängt werden kann (pro Sitzplatz optional,
in der Lobby wählbar):

```ini
[metadata]
Name=Horror: Dead is not an end
Scenario=perfect_game_horror,scenario_horror_t3_test
```

- **Werte:** kommagetrennte Liste aus `scenario.id`-Werten (bevorzugt) oder Dateinamen ohne
  `.json` (Fallback für Szenario-Dateien ohne `id`-Feld) — Auflösung übernimmt
  `ReplayLogParser.resolveScenarioByIdOrFilename()`.
- **Effekt:** In der Constructed-Lobby wird pro Sitzplatz, dessen aktuell gewähltes Deck einen
  `Scenario=`-Schlüssel hat, ein Dropdown mit den referenzierten Szenarien angeboten. Wird eines
  gewählt, gilt für diesen Sitzplatz — **egal ob Mensch oder AI** — dieselbe erzwungene
  Zugreihenfolge (`starting_hand`/`first_draws`) wie im Scenario Viewer. Ein eventuelles
  `events`-Array (erzwungene Spielreihenfolge) wird nur für AI-Sitzplätze tatsächlich ausgeführt;
  für einen menschlichen Sitzplatz erscheint stattdessen ein Hinweis ("Scripted line suggests: …"),
  der nichts blockiert oder erzwingt.
- **Kompatibilitätsprüfung:** der Deck-Verweis selbst entscheidet, welche Szenarien überhaupt zur
  Auswahl stehen — es wird nicht mehr per Karten-Namens-Abgleich "erraten". Fehlen im aktuell
  gewählten Deck einzelne `starting_hand`/`first_draws`-Karten des referenzierten Szenarios, bleibt
  es trotzdem wählbar, wird aber mit einem Warnhinweis markiert (dieselbe "Karte übersprungen"-
  Toleranz wie beim regulären `ScenarioLibrarySetup`-Reorder gilt weiterhin beim tatsächlichen
  Spielstart).
- **Konvention bei mehreren Spielern in derselben Szenario-Datei:** wird eine Szenario-Datei einem
  Sitzplatz zugewiesen, wird ausschließlich ihr eigener `players.P1`-Eintrag (und `events` mit
  `"a": "P1"`) gelesen — unabhängig davon, welcher tatsächliche Sitzplatz (P1 oder P2 des Matches)
  sie referenziert. Enthält die Datei zusätzlich nicht-leere `P2+`-Daten, werden diese ignoriert
  (mit Log-Warnung) statt versehentlich auf den anderen Sitzplatz durchzuschlagen.

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

**Ursache (aktuell):** `"events"` ist nicht auf **Top-Level** (neben `scenario`), oder der `type`
ist keiner von `"CAST"`/`"ACTIVATE"`/`"PLAY_LAND"`, oder `data.card_name` fehlt. Prüfen, ob
`Scenario: forced play sequence set — N event(s) for M player(s)` (GUI-Log) bzw.
`Scenario: Loaded forced play sequence — N event(s) for M player(s)` (CLI-Log) überhaupt
erscheint — wenn nicht, wurde das Array gar nicht geparst.

Falls das Log erscheint, aber die AI die Sequenz trotzdem nicht befolgt: die angegebene Karte
war zu diesem Zeitpunkt nicht castbar (**Soft Enforcement**, siehe unten) — kein Bug, sondern
erwartetes Verhalten.

**Ursache (behoben, historisch):** Vor dieser Korrektur musste `"a"` exakt dem tatsächlichen
Lobby-Namen des Spielers entsprechen (`Ai(1)-<Username> - <DeckName> (<Date>)`), den aber
weder GUI- noch CLI-Pfad zuverlässig reproduzierten — der CLI-Pfad verwendete sogar
fälschlich die rohe Spieler-ID (`"P1"`) direkt als Lobby-Namen. `"a"` ist jetzt immer eine
Spieler-ID (`"P1"`, `"P2"`, …); die Übersetzung zum tatsächlichen Lobby-Namen übernimmt der
Launcher selbst. Falls du eine alte Szenario-Datei mit einem vorkonstruierten Lobby-Namen-String
in `"a"` hast, ersetze ihn durch die passende Spieler-ID.

### Problem: Karte aus Forced Sequence wird übersprungen

**Ursache:** Karte ist zum Zeitpunkt des Events nicht castbar (z.B. keine gültigen Targets, nicht genug Mana).

**Lösung:** Das ist **Soft Enforcement** — die AI versucht die Karte zu spielen, fällt aber auf normale AI-Logik zurück wenn nicht möglich.
Die Karte bleibt in der Queue und wird bei jeder weiteren Priorität **innerhalb desselben Zugs** erneut versucht. Ist sie am Ende dieses Zugs immer noch nicht spielbar gewesen, gibt die AI diesen einen Eintrag auf, loggt das (`AI_DECISION`-Eintrag im Spiel-Log, sichtbar im Game Log Panel) und macht mit dem nächsten Queue-Eintrag weiter — die restliche Sequenz läuft also nicht unbegrenzt fest, falls ein einzelner Schritt aus irgendeinem Grund nie klappt.

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
    "id": "string",                // optional: stable id for deck Scenario= references
    "type": "opening_hand_test",   // optional: "opening_hand_test"|"puzzle"|"rules_test"
    "name": "string",              // required for GUI display (preferred; "title" is a deprecated alias)
    "deck_id": "string",           // optional: owning deck's identifier, shown as the "Deck" column
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
      "a": "P1",                   // actor: player id, matches scenario.players keys
      "type": "PLAY_LAND",         // PLAY_LAND | CAST | ACTIVATE
      "data": {
        "card_name": "string",     // card name
        "targets": ["string"],     // optional: target names, auto-filled on Demo Play export - recorded, not yet replayed
        "sacrifice": ["string"]    // optional: card(s) sacrificed as an additional cost, e.g. Metamorphosis - recorded, not yet replayed
      }
    }
  ]
}
```

---

## Beispiel-Datei für Horror: Dead is not an end

Speicherort: `%AppData%\Forge\games\scenarios\scenario_horror_t3_test.json`

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









