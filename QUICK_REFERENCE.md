# Forge Simulation - Schnellreferenz

## ✅ Problem behoben: Sentry DSN-Fehler

Der Fehler `IllegalArgumentException: DSN is required` ist jetzt behoben!

---

## Simulation starten (3 Methoden)

### 🎯 Methode 1: Test-Skript (EMPFOHLEN)

```batch
cd d:\Daten\SoftwareProjekte\Forge\forge
RUN_QUICK_TEST.bat
```

**Vorteile:**
- ✅ Sentry-Fix bereits integriert
- ✅ Öffnet Log-Datei automatisch
- ✅ Zeigt Statistiken an

---

### 📝 Methode 2: Direkter Befehl (Eine Zeile)

```batch
cd d:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target && java -Dsentry.dsn="" --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp "classes;..\..\forge-gui\target\classes;..\..\forge-game\target\classes;..\..\forge-core\target\classes;..\..\forge-ai\target\classes;..\..\forge-gui\res;%USERPROFILE%\.m2\repository\com\google\guava\guava\33.3.1-jre\guava-33.3.1-jre.jar;%USERPROFILE%\.m2\repository\org\apache\commons\commons-lang3\3.17.0\commons-lang3-3.17.0.jar;%USERPROFILE%\.m2\repository\org\xerial\sqlite-jdbc\3.36.0.3\sqlite-jdbc-3.36.0.3.jar;%USERPROFILE%\.m2\repository\org\jgrapht\jgrapht-core\1.5.2\jgrapht-core-1.5.2.jar;%USERPROFILE%\.m2\repository\com\googlecode\minlog\1.2\minlog-1.2.jar" forge.view.Main sim -d "Disguise, Surprise, Reward1912.dck" "Rebel Revision 96.dck" -n 1 -f commander
```

---

### 🔧 Methode 3: Für andere Decks/Formate

**Commander:**
```batch
java -Dsentry.dsn="" -cp [CLASSPATH] forge.view.Main sim -d "Deck1.dck" "Deck2.dck" -n 1 -f commander
```

**Constructed (Standard/Modern):**
```batch
java -Dsentry.dsn="" -cp [CLASSPATH] forge.view.Main sim -d "Deck1.dck" "Deck2.dck" -n 1 -f constructed
```

**Mehrere Spiele (z.B. 10):**
```batch
java -Dsentry.dsn="" -cp [CLASSPATH] forge.view.Main sim -d "Deck1.dck" "Deck2.dck" -n 10 -f commander
```

---

## Log-Datei finden

### Speicherort:
```
%APPDATA%\Forge\games\gamelogs\gamelog_Commander_[Timestamp].txt
```

### Schnell öffnen:
```batch
explorer %APPDATA%\Forge\games\gamelogs
```

### In PowerShell analysieren:
```powershell
$log = Get-ChildItem "$env:APPDATA\Forge\games\gamelogs\gamelog*.txt" | 
       Sort-Object LastWriteTime -Descending | 
       Select-Object -First 1

# Zeige Statistiken
$content = Get-Content $log.FullName -Raw
Write-Host "ANALYSIS-Einträge: $(([regex]::Matches($content, 'Analysis:')).Count)"
Write-Host "Zone-Changes: $(([regex]::Matches($content, 'moved from .+ to .+')).Count)"
Write-Host "Spell-Resolutions: $(([regex]::Matches($content, 'Resolving:')).Count)"
Write-Host "Turn-Summaries: $(([regex]::Matches($content, 'Turn Summary')).Count)"

# Öffne Log
notepad $log.FullName
```

---

## Erwartete Log-Ausgabe

### ✅ ANALYSIS-Features (durch Patch implementiert):

```
Analysis: Player1: Sol Ring moved from Hand to Battlefield
Analysis: Player1: Lightning Bolt moved from Hand to Stack
Analysis: Resolving: Lightning Bolt
Analysis: Player1: Lightning Bolt moved from Stack to Graveyard

Analysis: === Turn Summary - Board State Changes ===
Zone Changes:
  - Player1: Sol Ring moved from Hand to Battlefield
  - Player1: Lightning Bolt moved from Hand to Stack
  - Player1: Lightning Bolt moved from Stack to Graveyard

Board State Delta:
Player1:
  Hand: 7 -> 5 (-2)
  Battlefield: 0 -> 1 (+1)
  Graveyard: 0 -> 1 (+1)
```

---

## Prüf-Checkliste

Nach der Simulation solltest du sehen:

- [ ] Log-Datei erstellt in `%APPDATA%\Forge\games\gamelogs\`
- [ ] `Analysis:` Einträge für jeden Zone-Change
- [ ] `Resolving: [Card]` vor Spell-Auflösungen
- [ ] `=== Turn Summary - Board State Changes ===` am Zugende
- [ ] Board-State-Deltas mit `+` und `-` Änderungen

---

## Fehlerbehebung

### Problem: "DSN is required"
**Lösung:** Füge `-Dsentry.dsn=""` zum Java-Befehl hinzu (bereits in RUN_QUICK_TEST.bat)

### Problem: "Could not find deck"
**Lösung:** Prüfe, ob Decks existieren:
```batch
dir %APPDATA%\Forge\decks\commander\*.dck
```

### Problem: "NoClassDefFoundError"
**Lösung:** Maven neu bauen:
```batch
cd d:\Daten\SoftwareProjekte\Forge\forge
mvn clean install -DskipTests
```

### Problem: Simulation dauert ewig
**Normal:** Commander-Spiele benötigen 2-10 Minuten
**Tipp:** Prüfe, ob Log-Datei bereits wächst während Simulation läuft

---

## Weitere Dokumentation

- **ABSCHLUSSBERICHT.md** - Vollständiger Implementierungsbericht
- **SIMULATION_STARTEN.md** - Detaillierte Befehlsreferenz
- **TEST_ANLEITUNG.md** - Schritt-für-Schritt-Anleitung

---

## Status

✅ **Patch angewendet**
✅ **Build erfolgreich**
✅ **Sentry-Fix integriert**
✅ **Bereit für Tests**

**Nächster Schritt:** Führe `RUN_QUICK_TEST.bat` aus!

