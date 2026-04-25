@echo off
REM Commander AI Batch Simulation Runner v2.1
REM With Auto-Opponent Generation

setlocal enabledelayedexpansion

if "%1"=="" (
    echo Error: Deck1 parameter required!
    echo Usage: run_commander_simulation.bat "Deck1" ["Deck2"] [Games] [Timeout]
    echo Example: run_commander_simulation.bat "MyDeck" "" 10 180
    exit /b 1
)

set DECK1=%~1
set DECK2=%~2
set GAMES=%3
set TIMEOUT=%4

if "%GAMES%"=="" set GAMES=100
if "%TIMEOUT%"=="" set TIMEOUT=180

echo ====================================================
echo   Commander AI Batch Simulation v2.1
echo ====================================================
echo.

REM Find JAR
cd /d "%~dp0"
for %%F in (forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar) do set JAR=%%F

if not exist "%JAR%" (
    echo Error: forge.jar not found!
    echo Build first: mvn clean package -pl forge-gui-desktop -am -DskipTests
    exit /b 1
)

echo Found JAR: %JAR%

REM Auto-create opponent if not specified
if "%DECK2%"=="" (
    echo No opponent specified - creating auto-opponent...
    call "%~dp0create_default_opponent.bat"
    set DECK2=Auto_Opponent_Walls
    echo Using: Auto_Opponent_Walls ^(The Walls of Ba Sing Se + 99x Wastes^)
) else (
    echo Using opponent: %DECK2%
)

REM Validate decks
set DECK_DIR=%APPDATA%\Forge\decks\commander

if not exist "%DECK_DIR%\%DECK1%.dck" (
    if not exist "%DECK_DIR%\%DECK1%" (
        echo Error: Deck 1 not found: %DECK1%
        exit /b 1
    )
)

if not exist "%DECK_DIR%\%DECK2%.dck" (
    if not exist "%DECK_DIR%\%DECK2%" (
        echo Error: Deck 2 not found: %DECK2%
        exit /b 1
    )
)

echo Deck 1: %DECK1%
echo Deck 2: %DECK2%
echo.

echo ====================================================
echo Configuration:
echo   Games:    %GAMES%
echo   Timeout:  %TIMEOUT% seconds per game
echo   Format:   Commander
echo ====================================================
echo.

echo Starting simulation...
echo.

REM Ensure log directory
if not exist "%APPDATA%\Forge\games\simulation_stats" mkdir "%APPDATA%\Forge\games\simulation_stats"

REM Run simulation — IMPORTANT: both decks must follow a SINGLE -d flag!
REM Using two -d flags causes the second to overwrite the first (HashMap key collision).
java -jar "%JAR%" sim -d "%DECK1%.dck" "%DECK2%.dck" -n %GAMES% -f commander -c %TIMEOUT%

set EXIT_CODE=%ERRORLEVEL%

echo.
echo ====================================================
if %EXIT_CODE%==0 (
    echo Simulation Complete!
) else (
    echo Simulation ended with exit code: %EXIT_CODE%
)
echo ====================================================
echo.

echo Next steps:
echo   python analyze_commander_stats.py
echo   notepad commander_simulation_report.json
echo.

exit /b %EXIT_CODE%


