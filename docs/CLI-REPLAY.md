# CLI-Replay Feature für Forge

## Verwendung

**Beispielaufruf (aus dem Verzeichnis `forge-gui-desktop`):**

```powershell
java -jar target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar replay "C:\Users\...\AppData\Roaming\Forge\games\gamelogs\replay_Commander_2026-03-29_07-05-10.json"
```

**Oder mit relativem Pfad:**

```powershell
java -jar forge-gui-desktop-*.jar replay path\to\replay.json
```

## Ablauf

- Forge startet wie gewohnt (Splash-Screen, Skin laden, etc.)
- Nach dem Laden des Home-Screens wird das Spiel automatisch mit den Daten aus der angegebenen Replay-Logdatei gestartet
- Falls die Replay-Datei bereits ein `replayed_at`-Feld enthält, erscheint ein Bestätigungsdialog: "Nochmal spielen?"
- Falls die Datei nicht gefunden wird oder kein gültiges JSON enthält, erscheint ein Fehlerdialog

## Hinweise

- Die Bibliotheksreihenfolge (Library) aller Spieler wird exakt wie im Replay wiederhergestellt, sofern keine Shuffle-Events im Log auftreten
- Replays, die bereits ein `replayed_at`-Feld besitzen, werden nicht erneut in der Replay-Auswahl gelistet
- Nach erneutem Abspielen wird das Feld `replayed_at` mit Zeitstempel aktualisiert
- Das Feature funktioniert für alle unterstützten Spielmodi und Decktypen

---

**Siehe auch:**
- `ReplayLogParser.java` (Log-Parsing und Deck-Rekonstruktion)
- `forge-gui-desktop` (Startlogik und UI-Integration)
- `AGENTS.md` (Architektur und Entwicklerhinweise)

