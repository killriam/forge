@echo off
set JAR=D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar
set DECK1=killriam - Spiderman is Comming for Dinner (2026-04-06)
set DECK2=SimpleOpponent

echo Testing: %DECK1% vs %DECK2%
java -jar "%JAR%" sim -d "%DECK1%.dck" "%DECK2%.dck" -n 1 -f commander -c 180

echo Exit code: %ERRORLEVEL%

