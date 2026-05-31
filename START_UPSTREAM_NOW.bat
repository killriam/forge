@echo off
echo.
echo ========================================
echo   UPSTREAM FORGE STARTER
echo ========================================
echo.

cd /d "D:\Daten\SoftwareProjekte\Forge\forge-upstream\forge-gui-desktop\target"

echo Starte Upstream Forge...
echo JAR: forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar
echo.

start "" java -jar forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar

echo.
echo Upstream Forge wurde gestartet!
echo.
pause

