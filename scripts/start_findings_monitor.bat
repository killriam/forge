@echo off
REM Findings Monitor — Startet das automatische Findings-Monitoring
REM Überwacht D:\Daten\SoftwareProjekte\MaMo\FORGE_SCENARIO_SIM_FINDING.md
REM und aktualisiert README_BLACKBOX_TESTING.md bei Änderungen

echo ============================================================
echo Forge Findings Monitor
echo ============================================================
echo.
echo Checking Python installation...

python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python not found!
    echo Please install Python 3.7+ and add it to PATH
    pause
    exit /b 1
)

echo Python found!
echo.
echo Starting monitor...
echo Press Ctrl+C to stop
echo.

REM Starte Monitor im aktuellen Verzeichnis
cd /d "%~dp0"
python monitor_findings.py

pause

