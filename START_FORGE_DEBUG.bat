@echo off
REM ========================================
REM Forge - Debug Mode with JSON Logging
REM ========================================

echo.
echo ========================================
echo   Forge - Debug Mode
echo   JSON Replay Notation: ENABLED
echo ========================================
echo.

REM Set the JAR path
set FORGE_JAR=D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.08-SNAPSHOT-jar-with-dependencies.jar

REM Check if JAR exists
if not exist "%FORGE_JAR%" (
    echo ERROR: forge.jar not found at:
    echo %FORGE_JAR%
    echo.
    echo Please compile first with:
    echo   mvn clean package -pl forge-gui-desktop -am '-Dmaven.test.skip=true'
    pause
    exit /b 1
)

echo [DEBUG] Starting Forge...
echo [DEBUG] JAR: %FORGE_JAR%
echo [DEBUG] Game logs will be saved to:
echo         C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\
echo.
echo [INFO] After your game, check for JSON files with:
echo        dir "C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\*.json"
echo.
echo ========================================
echo   Starting Forge GUI now...
echo ========================================
echo.

REM Start Forge with the forge.exe (includes all resources)
start "" "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe"

echo.
echo [INFO] Forge is starting in a new window...
echo [INFO] This console will stay open for debugging.
echo.
echo [WAIT] Play your game normally...
echo [WAIT] When game ends, save the game log as usual.
echo.
echo Press any key to check for JSON logs after your game...
pause >nul

echo.
echo ========================================
echo   Checking for JSON logs...
echo ========================================
echo.

REM Check for JSON files
dir "C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\*.json" /O-D 2>nul

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [SUCCESS] JSON files found!
    echo.
    echo Latest JSON file:
    for /f "delims=" %%f in ('dir "C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\replay_*.json" /B /O-D 2^>nul') do (
        echo   - %%f
        goto :found
    )
    :found
    echo.
    echo [INFO] View the JSON with:
    echo   Get-Content "C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\replay_*.json" ^| Select-Object -First 50
    echo.
) else (
    echo.
    echo [WARNING] No JSON files found yet.
    echo.
    echo Possible reasons:
    echo   1. Game log was not saved after the match
    echo   2. Using old Forge version (not the newly compiled one)
    echo   3. Auto-enable reflection failed
    echo.
    echo [DEBUG] Checking if any game logs exist:
    dir "C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\*.txt" /O-D 2>nul | findstr /C:"gamelog_"
    echo.
)

echo.
echo ========================================
echo   Debug session complete
echo ========================================
pause

