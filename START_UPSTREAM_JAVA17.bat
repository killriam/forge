@echo off
echo.
echo ========================================
echo   UPSTREAM FORGE mit Java 17
echo ========================================
echo.

cd /d "D:\Daten\SoftwareProjekte\Forge\forge-upstream\forge-gui-desktop\target"

REM Suche Java 17
set "JAVA17_HOME="
if exist "C:\Program Files\Java\jdk-17" set "JAVA17_HOME=C:\Program Files\Java\jdk-17"
if exist "C:\Program Files\Java\jdk17" set "JAVA17_HOME=C:\Program Files\Java\jdk17"
if exist "C:\Program Files\Eclipse Adoptium\jdk-17" set "JAVA17_HOME=C:\Program Files\Eclipse Adoptium\jdk-17"
if exist "C:\Program Files\Microsoft\jdk-17" set "JAVA17_HOME=C:\Program Files\Microsoft\jdk-17"

if defined JAVA17_HOME (
    echo Java 17 gefunden: %JAVA17_HOME%
    echo.
    "%JAVA17_HOME%\bin\java.exe" -jar forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar
) else (
    echo FEHLER: Java 17 nicht gefunden!
    echo.
    echo Upstream Forge benoetigt Java 17 (nicht Java 22^)
    echo.
    echo Bitte installieren Sie Java 17:
    echo https://adoptium.net/de/temurin/releases/?version=17
    echo.
    echo Oder verwenden Sie die Fork-Version (funktioniert mit Java 22^):
    echo   cd D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target
    echo   java -jar forge-gui-desktop-*-jar-with-dependencies.jar
    echo.
    pause
)

