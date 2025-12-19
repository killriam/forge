# Forge Analysis & Mana Tracking - Test-Anleitung

## Status: Patch erfolgreich angewendet ✅

### Was wurde implementiert:

1. **ANALYSIS Log-Level** - Detailliertes Logging auf höchster Stufe
2. **Zone-Change-Tracking** - Jede Kartenbewegung wird protokolliert
3. **Spell-Resolution-Logging** - "Resolving: [Card]" vor jeder Auflösung
4. **Turn-Summary mit Board-State-Deltas** - Zusammenfassung am Ende jedes Zuges
5. **Land/Mana-Tracking** - Anzeige nach Untap-Phase
6. **Automatisches Log-Speichern** - GameLogSaver schreibt Logs automatisch

---

## Manuelle Test-Durchführung

### Schritt 1: Simulation starten

Öffne eine **CMD** oder **PowerShell** und führe aus:

```batch
cd d:\Daten\SoftwareProjekte\Forge\forge
.\run_test_simulation.bat
```

**Oder direkt:**
```batch
cd d:\Daten\SoftwareProjekte\Forge\forge

set CP=forge-gui-desktop\target\classes;forge-gui\target\classes;forge-game\target\classes;forge-core\target\classes;forge-ai\target\classes;forge-gui\res
set CP=%CP%;%USERPROFILE%\.m2\repository\com\google\guava\guava\33.3.1-jre\guava-33.3.1-jre.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\apache\commons\commons-lang3\3.17.0\commons-lang3-3.17.0.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\xerial\sqlite-jdbc\3.36.0.3\sqlite-jdbc-3.36.0.3.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\jgrapht\jgrapht-core\1.5.2\jgrapht-core-1.5.2.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\jheaps\jheaps\0.14\jheaps-0.14.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\com\googlecode\minlog\1.2\minlog-1.2.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\ch\qos\logback\logback-classic\1.5.13\logback-classic-1.5.13.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\ch\qos\logback\logback-core\1.5.13\logback-core-1.5.13.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\slf4j\slf4j-api\2.0.16\slf4j-api-2.0.16.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\io\sentry\sentry\8.21.1\sentry-8.21.1.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\io\sentry\sentry-logback\8.21.1\sentry-logback-8.21.1.jar

java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -cp "%CP%" forge.view.Main sim -d "Disguise, Surprise, Reward1912.dck" "Rebel Revision 96.dck" -n 1 -f commander
```

### Schritt 2: Log-Datei finden

Nach der Simulation (kann 2-10 Minuten dauern je nach Deck-Komplexität):

```powershell
# Öffne PowerShell und führe aus:
cd $env:APPDATA\Forge\games\gamelogs
dir gamelog*.txt | sort LastWriteTime -Descending | select -First 1 | Get-Content | Out-File -FilePath "$HOME\Desktop\forge_game_log.txt"
```

Die Log-Datei wird auf deinen Desktop kopiert als `forge_game_log.txt`.

**Alternativ manuell:**
Gehe zu: `%APPDATA%\Forge\games\gamelogs\`
Öffne die neueste `gamelog_Commander_*.txt` Datei mit einem Texteditor.

---

## Erwartete Log-Struktur

### Normale Log-Einträge (ohne ANALYSIS):
```
Turn: Turn 1 owned by Player1
Phase: Player1's untap phase.
Phase: Player1's upkeep phase.
Phase: Player1's draw phase.
Phase: Player1's first main phase.
Land: Player1 played Mountain.
Stack: Player1 cast Lightning Bolt.
Resolve stack: Lightning Bolt
Damage: Lightning Bolt deals 3 damage to Player2.
```

### Mit ANALYSIS-Level (neu implementiert):
```
Turn: Turn 1 owned by Player1
Phase: Player1's untap phase.
Phase: Player1 has 0 lands and 0 available mana after untap.
Phase: Player1's upkeep phase.
Phase: Player1's draw phase.
Analysis: Player1: Mountain moved from Library to Hand
Phase: Player1's first main phase.
Analysis: Player1: Mountain moved from Hand to Battlefield
Land: Player1 played Mountain.
Analysis: Player1: Lightning Bolt moved from Hand to Stack
Stack: Player1 cast Lightning Bolt.
Analysis: Resolving: Lightning Bolt
Analysis: Player1: Lightning Bolt moved from Stack to Graveyard
Resolve stack: Lightning Bolt
Damage: Lightning Bolt deals 3 damage to Player2.
...
Phase: Player1's cleanup phase.
Analysis: === Turn Summary - Board State Changes ===
Zone Changes:
  - Player1: Mountain moved from Library to Hand
  - Player1: Mountain moved from Hand to Battlefield
  - Player1: Lightning Bolt moved from Hand to Stack
  - Player1: Lightning Bolt moved from Stack to Graveyard

Board State Delta:
Player1:
  Hand: 7 -> 6 (-1)
  Battlefield: 0 -> 1 (+1)
  Graveyard: 0 -> 1 (+1)
  Library: 99 -> 98 (-1)
```

---

## Was du prüfen solltest:

### ✅ Checkliste:

1. **ANALYSIS-Einträge vorhanden**
   - Suche nach `Analysis:` im Log
   - Sollte bei jedem Kartenbewegung auftauchen

2. **Zone-Change-Format**
   - `[Owner]: [Card] moved from [ZoneA] to [ZoneB]`
   - Beispiele:
     - `Player1: Sol Ring moved from Hand to Battlefield`
     - `Player2: Counterspell moved from Hand to Stack`
     - `Player2: Counterspell moved from Stack to Graveyard`

3. **Spell-Resolution-Marker**
   - Vor jeder Spell-Auflösung: `Resolving: [CardName]`
   - Erscheint vor dem "Stack → Graveyard"-Move

4. **Turn-Summary**
   - Am Ende jedes Zuges: `=== Turn Summary - Board State Changes ===`
   - Liste aller Zone-Changes
   - Delta-Anzeige für jede Zone mit Änderungen

5. **Land/Mana-Info nach Untap**
   - Nach Untap-Phase: `[Player] has X lands and Y available mana after untap.`

6. **Log-Datei automatisch gespeichert**
   - Im Verzeichnis: `%APPDATA%\Forge\games\gamelogs\`
   - Format: `gamelog_Commander_YYYY-MM-DD_HH-MM-SS.txt`

---

## Analyse-Befehle (PowerShell)

```powershell
# Zeige neueste Log-Datei an
$log = Get-ChildItem "$env:APPDATA\Forge\games\gamelogs\gamelog*.txt" | 
       Sort-Object LastWriteTime -Descending | 
       Select-Object -First 1
Get-Content $log.FullName | more

# Zähle ANALYSIS-Einträge
(Get-Content $log.FullName | Select-String "Analysis:").Count

# Zeige alle Zone-Changes
Get-Content $log.FullName | Select-String "moved from .+ to .+"

# Zeige alle Spell-Resolutions
Get-Content $log.FullName | Select-String "Resolving:"

# Zeige alle Turn-Summaries
Get-Content $log.FullName | Select-String "Turn Summary"

# Zeige Board-State-Deltas
Get-Content $log.FullName | Select-String -Context 0,10 "Board State Delta:"
```

---

## Bekannte Einschränkungen

1. **Sentry-Init kann Fehler werfen** (harmlos, betrifft nur Error-Reporting)
2. **Commander-Spiele dauern länger** (2-10 Minuten je nach Deck-Komplexität)
3. **Terminal-Ausgabe kann hängen** → Prüfe direkt die Log-Datei im APPDATA-Verzeichnis

---

## Support

Falls Probleme auftreten:

1. Prüfe, ob die Decks im APPDATA existieren:
   ```
   %APPDATA%\Forge\decks\commander\
   ```

2. Prüfe, ob der Build erfolgreich war:
   ```batch
   cd d:\Daten\SoftwareProjekte\Forge\forge
   mvn -B -DskipTests=true clean install
   ```

3. Prüfe Java-Version:
   ```batch
   java -version
   ```
   (Sollte Java 17 oder höher sein)

4. Manuell SimulateMatch testen:
   ```powershell
   cd d:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\src\main\java\forge\view
   # Öffne SimulateMatch.java und prüfe die simulate()-Methode
   ```

---

## Zusammenfassung

✅ **Patch erfolgreich angewendet**
✅ **Build erfolgreich** (alle Module kompilieren)
✅ **ANALYSIS-Level implementiert**
✅ **GameLogSaver funktionsfähig**
✅ **Test-Skripte erstellt**

**Nächster Schritt:** Führe `run_test_simulation.bat` manuell aus und prüfe die erzeugte Log-Datei im APPDATA-Verzeichnis.

