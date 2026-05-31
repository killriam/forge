@echo off
REM Create Default Opponent Deck for Commander Simulations

set DECK_DIR=%APPDATA%\Forge\decks\commander
set DECK_FILE=%DECK_DIR%\Auto_Opponent_Walls.dck

echo Creating default opponent deck...
echo Commander: The Walls of Ba Sing Se
echo Main: 99x Wastes
echo.

if not exist "%DECK_DIR%" mkdir "%DECK_DIR%"

(
echo [metadata]
echo Name=Auto Opponent ^(Walls^)
echo [Commander]
echo 1 The Walls of Ba Sing Se
echo [Main]
echo 99 Wastes
echo [Sideboard]
) > "%DECK_FILE%"

if exist "%DECK_FILE%" (
    echo ✓ Deck created: %DECK_FILE%
    exit /b 0
) else (
    echo ✗ Failed to create deck!
    exit /b 1
)

