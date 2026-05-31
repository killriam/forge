# Forge Command Line Interface (CLI)

Forge unterstützt mehrere Kommandozeilenmodi für verschiedene Aufgaben ohne GUI.

---

## Übersicht der CLI-Modi

| Modus | Beschreibung |
|-------|-------------|
| `sim` | Headless AI-Simulation von Spielen |
| `gui` | Startet das normale Forge-Fenster mit optional vorbelegter Deckauswahl |
| `replay` | Interaktives Replay einer Spiel-Log-Datei |
| `parse` | Validierung von Kartendefinitionen |

---

## 1. Simulation Mode (`sim`)

Führt AI-vs-AI Spiele ohne GUI aus. Nützlich für Deck-Testing, Turniere oder Performance-Analysen.

### Syntax

```bash
java -jar forge-gui-desktop-*-jar-with-dependencies.jar sim [OPTIONS]
```

### Optionen

| Option | Parameter | Beschreibung | Standard |
|--------|-----------|--------------|----------|
| `-d` | `<deck1> ... <deckX>` | Liste von Deck-Dateien (`.dck`) oder Meta-Deck-Namen | **Pflicht** |
| `-D` | `[path]` | Absoluter Pfad zum Deck-Verzeichnis (überschreibt `-d` Pfad) | - |
| `-n` | `[N]` | Anzahl der Spiele | `1` |
| `-m` | `[M]` | Best-of-M Matches (überschreibt `-n`) | `1` |
| `-f` | `[F]` | Spielformat | `constructed` |
| `-t` | `[T]` | Turniermodus (Bracket, RoundRobin, Swiss) | - |
| `-p` | `[P]` | Anzahl Spieler pro Partie (nur Turnier) | `2` |
| `-q` | - | Quiet Mode (nur Ergebnis, keine Debug-Ausgaben) | - |
| `-c` | `[S]` | Timeout in Sekunden (Draw bei Überschreitung) | `120` |
| `-r` | `[replay.json]` | Replay-Datei für deterministische Simulation | - |

### Unterstützte Formate (`-f`)

- `constructed` (Standard)
- `Commander`
- `Oathbreaker`
- `TinyLeaders`
- `Brawl`
- `MomirBasic`
- `Vanguard`
- `MoJhoSto`

### Beispiele

#### Basis: 3 Spiele zwischen zwei Decks
```bash
java -jar forge-gui-desktop-*.jar sim -d deck1 deck2 -n 3
```
Decks werden in `%APPDATA%\Forge\decks\constructed\` gesucht.

#### Commander: Einzelnes 3-Spieler-Spiel
```bash
java -jar forge-gui-desktop-*.jar sim -d DeckA DeckB DeckC -f Commander
```
Decks werden in `%APPDATA%\Forge\decks\commander\` gesucht.

#### Turnier: Swiss-System, Best-of-3, alle Decks aus Ordner
```bash
java -jar forge-gui-desktop-*.jar sim -D "C:\MyDecks\" -m 3 -t Swiss -p 3
```

#### Mit Quiet Mode (weniger Output)
```bash
java -jar forge-gui-desktop-*.jar sim -d aggro_red control_blue -n 10 -q
```

#### Mit explizitem Deck-Pfad und Dateinamen
```bash
java -jar forge-gui-desktop-*.jar sim -D "D:\Decks\" -d myDeck.dck opponent.dck -n 5
```

#### Deterministische Replay-Simulation
```bash
java -jar forge-gui-desktop-*.jar sim -d deck1 deck2 -r replay_log.json -n 1
```
Verwendet die Draw-Order aus der Replay-Datei für deterministische Wiederholung.

### Ausgabe

- Jedes Spiel endet mit Gewinner-Ansage
- Bei Turnieren: Zusammenfassung aller Ergebnisse
- Mit `-q`: Nur Endergebnis
- Ohne `-q`: Vollständige Spiel-Logs
- Replay-JSON wird automatisch gespeichert in:
  - Simulation: `sim_<Format>_<Datum>.json`
  - Normal: `replay_<Format>_<Datum>.json`

### Hinweise

- **Windows EXE**: Ausgabe nur in Forge-Log-Datei, nicht in Console
- **Performance**: Komplexe Boardstates können langsam werden
- **AI-Limitierungen**: Siehe [AI.md](AI.md) für Details
- **Deck-Namen mit Leerzeichen**: In Anführungszeichen setzen: `"My Deck"`

---

## 2. Replay Mode (`replay`)

Startet Forge im GUI-Modus und lädt ein gespeichertes Spiel zur interaktiven Wiederholung.

### Syntax

```bash
java -jar forge-gui-desktop-*-jar-with-dependencies.jar replay <replay_log.json>
```

### Parameter

| Parameter | Beschreibung |
|-----------|--------------|
| `<replay_log.json>` | Pfad zur Replay-JSON-Datei |

### Beispiele

#### Mit absolutem Pfad
```powershell
java -jar forge-gui-desktop-*.jar replay "C:\Users\Name\AppData\Roaming\Forge\games\gamelogs\replay_Commander_2026-03-29_07-05-10.json"
```

#### Mit relativem Pfad
```bash
java -jar forge-gui-desktop-*.jar replay path/to/replay.json
```

### Ablauf

1. Forge startet normal (Splash-Screen, UI-Laden)
2. Home-Screen wird geladen
3. Spiel startet automatisch mit Replay-Daten:
   - Decks werden rekonstruiert
   - Bibliotheksreihenfolge wird wiederhergestellt
   - Mulligan-Entscheidungen werden übernommen

### Hinweise

- **Bibliotheksreihenfolge**: Wird exakt wiederhergestellt (sofern kein Shuffle)
- **`replayed_at` Feld**: Bestätigungsdialog bei erneutem Replay
- **Fehlerbehandlung**: Dialog bei ungültiger/fehlender Datei

**Details:** Siehe [CLI-REPLAY.md](CLI-REPLAY.md)

---

## 3. GUI Mode (`gui`)

Startet Forge im normalen GUI-Modus und kann optional die Deckauswahl in der Lobby vorausfüllen.

### Syntax

```powershell
java -jar forge-gui-desktop-*-jar-with-dependencies.jar gui [OPTIONS]
```

Alternativ ohne explizites `gui`:

```powershell
java -jar forge-gui-desktop-*-jar-with-dependencies.jar --deck "Mein Deck"
```

### Optionen

| Option | Parameter | Beschreibung | Standard |
|--------|-----------|--------------|----------|
| `--deck` / `--deck1` | `<Deckname>` | Setzt Lobby-Deck für Spieler 1 | - |
| `--deck2` | `<Deckname>` | Setzt Lobby-Deck für Spieler 2 | - |
| `--format` | `commander\|oathbreaker\|tinyleaders\|brawl\|constructed` | Wählt Deck-State/Variante für die Vorauswahl | `commander` |

### Beispiele

```powershell
java -jar forge-gui-desktop-*.jar gui --format commander --deck "Atraxa Poison" --deck2 "Mono Red"
java -jar forge-gui-desktop-*.jar --deck "My Control Deck"
```

### Hinweise

- Diese Optionen schreiben die gewählte Lobby-Deckauswahl in die Forge-Preferences.
- Decknamen müssen so geschrieben sein wie sie in der Deckliste erscheinen.

---

## 4. Parse Mode (`parse`)

Validiert Kartendefinitionen aus `res/cardsfolder/`.

### Syntax

```bash
java -jar forge-gui-desktop-*-jar-with-dependencies.jar parse
```

### Funktion

- Lädt alle Karten-Scripts aus `res/cardsfolder/`
- Validiert Syntax und Referenzen
- Gibt Fehler/Warnungen aus

### Ausgabe

- Liste aller Parser-Fehler
- Ungültige Ability-Referenzen
- Fehlende Oracle-Texte

**Hinweis:** Nützlich bei Entwicklung neuer Kartenskripte.

---

## Allgemeine Hinweise

### Pfade zu Ressourcen

| Ressource | Windows | Linux/macOS |
|-----------|---------|-------------|
| Decks | `%APPDATA%\Forge\decks\` | `~/.forge/decks/` |
| Replays | `%APPDATA%\Forge\games\gamelogs\` | `~/.forge/games/gamelogs/` |
| Logs | `%APPDATA%\Forge\` | `~/.forge/` |

### JAR-Datei finden

Nach `mvn clean package -pl forge-gui-desktop -am`:

```bash
# Windows
forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar

# Linux/macOS
forge-gui-desktop/target/forge-gui-desktop-*-jar-with-dependencies.jar
```

### Debugging

Für detaillierte Logs ohne GUI:

```bash
java -jar forge-gui-desktop-*.jar sim -d deck1 deck2 -n 1 2>&1 | tee simulation.log
```

### Performance-Tipps

- **Große Turniere**: `-q` Mode verwenden
- **Timeout setzen**: `-c 300` für komplexe Decks
- **Parallele Runs**: Mehrere Forge-Instanzen mit unterschiedlichen Decks

---

## Siehe auch

- [AI.md](AI.md) - Details zur AI-Implementierung
- [CLI-REPLAY.md](CLI-REPLAY.md) - Replay-System Details
- [AGENTS.md](../AGENTS.md) - Architektur-Dokumentation
- [FEATURE_GAME_REPLAY.md](FEATURE_GAME_REPLAY.md) - Replay-Format-Spezifikation

---

**Letzte Aktualisierung:** 2026-05-18
