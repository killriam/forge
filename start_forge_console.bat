@echo off
REM Setze das Arbeitsverzeichnis auf forge-gui-desktop, damit ../forge-gui/ korrekt aufgeloest wird
cd /d "%~dp0forge-gui-desktop"

REM Suche das gebaute jar
for /f "delims=" %%i in ('dir /b "target\forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar" 2^>nul') do set JAR=%%i

if "%JAR%"=="" (
    echo FEHLER: Kein gebautes jar in forge-gui-desktop\target\ gefunden.
    echo Bitte zuerst 'mvn clean install' ausfuehren.
    pause
    exit /b 1
)

java -jar "target\%JAR%"
