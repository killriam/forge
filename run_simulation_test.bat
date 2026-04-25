@echo off
echo ========================================
echo Commander AI Simulation Test
echo ========================================
echo.

cd /d "D:\Daten\SoftwareProjekte\Forge\forge"

set JAR=forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar

if not exist "%JAR%" (
    echo ERROR: JAR not found!
    pause
    exit /b 1
)

echo Found JAR: %JAR%
echo.
echo Starting 10 games simulation...
echo Deck: killriam - Spiderman is Comming for Dinner
echo.

java -jar "%JAR%" sim -d "killriam - Spiderman is Comming for Dinner (2026-04-06).dck" "killriam - Spiderman is Comming for Dinner (2026-04-06).dck" -n 10 -f commander -c 180

echo.
echo ========================================
echo Simulation Complete!
echo ========================================
echo.
echo Check logs in: %APPDATA%\Forge\games\simulation_stats\
echo.
pause


