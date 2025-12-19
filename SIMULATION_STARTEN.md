# Forge Simulation über Kommandozeile starten

## ⚠️ WICHTIG: Sentry deaktivieren

Wenn du den Fehler `DSN is required` siehst, füge `-Dsentry.dsn=""` hinzu:

```batch
java -Dsentry.dsn="" -jar forge-gui-desktop.jar sim -d "Deck1.dck" "Deck2.dck" -n 1 -f commander
```

---

## Schnellstart (Kopiere und füge ein)

### Empfohlen: Test-Skript verwenden

```batch
cd /d d:\Daten\SoftwareProjekte\Forge\forge
RUN_QUICK_TEST.bat
```
*Das Skript hat bereits den Sentry-Fix integriert!*

---

### Mit fertiger forge.jar (Einfachste Methode)

```batch
java -Dsentry.dsn="" -jar forge-gui-desktop.jar sim -d "Disguise, Surprise, Reward1912.dck" "Rebel Revision 96.dck" -n 1 -f commander
```

**Oder mit vollständigem Pfad:**

```batch
cd /d "C:\Program Files\Forge"
java -jar forge-gui-desktop.jar sim -d "Disguise, Surprise, Reward1912.dck" "Rebel Revision 96.dck" -n 1 -f commander
```

**Oder aus dem Build-Verzeichnis:**

```batch
cd /d d:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target
java -jar forge-gui-desktop-2.0.08-SNAPSHOT.jar sim -d "Disguise, Surprise, Reward1912.dck" "Rebel Revision 96.dck" -n 1 -f commander
```

---

### Alternative: Mit run_test_simulation.bat

```batch
cd /d d:\Daten\SoftwareProjekte\Forge\forge
run_test_simulation.bat
```

---

## Befehl anpassen

### Andere Decks verwenden:

```batch
java -jar forge-gui-desktop.jar sim -d "Mein-Deck.dck" "Gegner-Deck.dck" -n 1 -f commander
```

### Mehr Spiele simulieren:

```batch
# 10 Spiele statt 1:
java -jar forge-gui-desktop.jar sim -d "Deck1.dck" "Deck2.dck" -n 10 -f commander
```

### Anderes Format:

```batch
# Constructed (Standard/Modern/Legacy):
java -jar forge-gui-desktop.jar sim -d "Deck1.dck" "Deck2.dck" -n 1 -f constructed

# Commander:
java -jar forge-gui-desktop.jar sim -d "Deck1.dck" "Deck2.dck" -n 1 -f commander

# Sealed:
java -jar forge-gui-desktop.jar sim -d "Deck1.dck" "Deck2.dck" -n 1 -f sealed
```

### Ohne Ausgabe (nur Log-Datei):

```batch
java -jar forge-gui-desktop.jar sim -d "Deck1.dck" "Deck2.dck" -n 1 -f commander -q
```
**(-q = quiet mode)**

---

## Nach der Simulation

Die Log-Datei findest du hier:

```
%APPDATA%\Forge\games\gamelogs\gamelog_Commander_*.txt
```

**Schnell öffnen (PowerShell):**
```powershell
notepad "$env:APPDATA\Forge\games\gamelogs\$(Get-ChildItem $env:APPDATA\Forge\games\gamelogs\gamelog*.txt | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | Select-Object -ExpandProperty Name)"
```

---

## Beispiel-Ausgabe während der Simulation

```
Simulation mode
Loading decks...
Starting game 1 of 1...
Turn 1: Player1
Turn 2: Player2
...
Game 1 ended in X turns
Winner: Player1
Game log saved to: C:\Users\...\Forge\games\gamelogs\gamelog_Commander_2025-12-19_15-30-45.txt
```

---

## Troubleshooting

**Fehler: "Could not find deck"**
→ Prüfe ob Decks existieren:
```batch
dir %APPDATA%\Forge\decks\commander\*.dck
```

**Fehler: "NoClassDefFoundError"**
→ Maven-Build wiederholen:
```batch
cd d:\Daten\SoftwareProjekte\Forge\forge
mvn clean install -DskipTests
```

**Simulation hängt**
→ Warte 2-10 Minuten (Commander-Spiele dauern länger)
→ Prüfe ob Log-Datei bereits erstellt wurde

---

## Empfehlung

**Für einmalige Tests:** Nutze Option 1 (`run_test_simulation.bat`)

**Für wiederholte Tests mit anderen Decks:** Nutze Option 2 oder 3 und passe die Deck-Namen an

