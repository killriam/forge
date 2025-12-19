@echo off
echo === Forge Simulation Test ===
echo.
echo Deck 1: Disguise, Surprise, Reward1912.dck
echo Deck 2: Rebel Revision 96.dck
echo.

cd /d "d:\Daten\SoftwareProjekte\Forge\forge"

set CP=forge-gui-desktop\target\classes
set CP=%CP%;forge-gui\target\classes
set CP=%CP%;forge-game\target\classes
set CP=%CP%;forge-core\target\classes
set CP=%CP%;forge-ai\target\classes
set CP=%CP%;forge-gui\res
set CP=%CP%;%USERPROFILE%\.m2\repository\com\google\guava\guava\33.3.1-jre\guava-33.3.1-jre.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\apache\commons\commons-lang3\3.17.0\commons-lang3-3.17.0.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\xerial\sqlite-jdbc\3.36.0.3\sqlite-jdbc-3.36.0.3.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\jgrapht\jgrapht-core\1.5.2\jgrapht-core-1.5.2.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\jheaps\jheaps\0.14\jheaps-0.14.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\com\googlecode\minlog\1.2\minlog-1.2.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\ch\qos\logback\logback-classic\1.5.13\logback-classic-1.5.13.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\ch\qos\logback\logback-core\1.5.13\logback-core-1.5.13.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\slf4j\slf4j-api\2.0.16\slf4j-api-2.0.16.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\io\sentry\sentry\8.21.1\sentry-8.21.1.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\io\sentry\sentry-logback\8.21.1\sentry-logback-8.21.1.jar
set CP=%CP%;%USERPROFILE%\.m2\repository\org\apache\commons\commons-text\1.12.0\commons-text-1.12.0.jar

echo Running simulation...
echo.

java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -cp "%CP%" forge.view.Main sim -d "Disguise, Surprise, Reward1912.dck" "Rebel Revision 96.dck" -n 1 -f commander

echo.
echo === Simulation Complete ===
echo.
echo Checking for log files...
if exist "%APPDATA%\Forge\games\gamelogs\" (
    dir /b /o-d "%APPDATA%\Forge\games\gamelogs\gamelog*.txt" 2>nul | findstr /r "." >nul
    if errorlevel 1 (
        echo No log files found
    ) else (
        echo Latest log files:
        dir /b /o-d "%APPDATA%\Forge\games\gamelogs\gamelog*.txt" | findstr /n "^" | findstr "^[1-3]:"
    )
) else (
    echo Log directory does not exist
)

pause

