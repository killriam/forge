# Replay-Feature: Technische Details & Entwicklerhinweise

## Überblick

Das CLI-Replay-Feature ermöglicht das erneute Abspielen eines Spiels anhand eines Replay-Logs. Die Bibliotheksreihenfolge wird exakt wiederhergestellt, sofern keine Shuffle-Events auftreten. Bereits abgespielte Replays werden markiert und nicht erneut gelistet.

## Technische Umsetzung

- **Replay-Log Parsing:**
  - Implementiert in `ReplayLogParser.java`
  - Liest und validiert das JSON-Format (`format: mtg-replay`)
  - Extrahiert Metadaten, Spielerinformationen, Decklisten und Kartenreihenfolge
  - Prüft und setzt das Feld `replayed_at` (Zeitstempel)

- **Deck-Rekonstruktion:**
  - Haupt- und Commander-Karten werden pro Spieler aus `initial_state.objects` und `card_index` rekonstruiert
  - Die Reihenfolge der Bibliothek entspricht der im Replay-Log dokumentierten Zieh-Reihenfolge
  - Bei fehlender `initial_state`-Struktur Fallback auf Events (ältere Logs)

- **Replay-Start:**
  - Beim Start mit `replay <logfile>` wird nach dem Home-Screen automatisch ein Spiel mit den rekonstruierten Decks und Bibliotheken gestartet
  - Bereits markierte Replays (`replayed_at` gesetzt) lösen einen Bestätigungsdialog aus
  - Fehlerhafte oder nicht gefundene Dateien führen zu einem Fehlerdialog

- **Replay-Log-Flag:**
  - Nach erneutem Abspielen wird das Feld `replayed_at` im Logfile aktualisiert
  - Solche Logs werden in der Replay-Auswahl nicht mehr angezeigt

## Wichtige Dateien

- `forge-gui/src/main/java/forge/game/ReplayLogParser.java` (Parsing, Markierung, Deckbau)
- `forge-gui-desktop` (UI-Integration, CLI-Startlogik)
- `docs/CLI-REPLAY.md` (Nutzer-Dokumentation)

## Hinweise für Entwickler

- Das Feature ist kompatibel mit allen Spielmodi und Decktypen
- Die Replay-Logik ist robust gegenüber älteren und neuen Logformaten
- Erweiterungen (z.B. Mulligan-Regeln aus dem Log) können über die ReplayLogParser-API erfolgen

---

**Siehe auch:**
- `AGENTS.md` (Architektur und Entwicklerhinweise)
- `ReplayLogParser.java` (Code-Referenz)

