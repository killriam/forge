@echo off
setlocal enabledelayedexpansion

set JAR=D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar
set DECK1=killriam - Spiderman is Comming for Dinner (2026-04-06)
set DECK2=Auto_Opponent_Walls
set LOGFILE=D:\Daten\SoftwareProjekte\Forge\forge\test_sim_final.txt

echo Starting simulation... > "%LOGFILE%"
echo Deck1: %DECK1% >> "%LOGFILE%"
echo Deck2: %DECK2% >> "%LOGFILE%"
echo. >> "%LOGFILE%"

java -jar "%JAR%" sim -d "%DECK1%.dck" "%DECK2%.dck" -n 1 -f commander -c 180 >> "%LOGFILE%" 2>&1

echo. >> "%LOGFILE%"
echo Exit code: %ERRORLEVEL% >> "%LOGFILE%"

type "%LOGFILE%"
exit /b %ERRORLEVEL%

