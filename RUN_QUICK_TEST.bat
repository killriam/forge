@echo off
setlocal enabledelayedexpansion

echo ================================================
echo Forge Simulation - Quick Test
echo ================================================
echo.
echo Deck 1: Disguise, Surprise, Reward1912.dck
echo Deck 2: Rebel Revision 96.dck
echo Format: Commander
echo Games: 1
echo.

cd /d "d:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target"

set M2=%USERPROFILE%\.m2\repository

set CP=classes
set CP=%CP%;..\..\forge-gui\target\classes
set CP=%CP%;..\..\forge-game\target\classes
set CP=%CP%;..\..\forge-core\target\classes
set CP=%CP%;..\..\forge-ai\target\classes
set CP=%CP%;..\..\forge-gui\res
set CP=%CP%;%M2%\com\google\guava\guava\33.3.1-jre\guava-33.3.1-jre.jar
set CP=%CP%;%M2%\org\apache\commons\commons-lang3\3.17.0\commons-lang3-3.17.0.jar
set CP=%CP%;%M2%\org\apache\commons\commons-text\1.12.0\commons-text-1.12.0.jar
set CP=%CP%;%M2%\org\xerial\sqlite-jdbc\3.36.0.3\sqlite-jdbc-3.36.0.3.jar
set CP=%CP%;%M2%\org\jgrapht\jgrapht-core\1.5.2\jgrapht-core-1.5.2.jar
set CP=%CP%;%M2%\org\jheaps\jheaps\0.14\jheaps-0.14.jar
set CP=%CP%;%M2%\com\googlecode\minlog\1.2\minlog-1.2.jar
set CP=%CP%;%M2%\ch\qos\logback\logback-classic\1.5.13\logback-classic-1.5.13.jar
set CP=%CP%;%M2%\ch\qos\logback\logback-core\1.5.13\logback-core-1.5.13.jar
set CP=%CP%;%M2%\org\slf4j\slf4j-api\2.0.16\slf4j-api-2.0.16.jar
set CP=%CP%;%M2%\io\sentry\sentry\8.21.1\sentry-8.21.1.jar
set CP=%CP%;%M2%\io\sentry\sentry-logback\8.21.1\sentry-logback-8.21.1.jar

echo Running simulation (this may take 2-10 minutes)...
echo.

java --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -Dsentry.dsn="" -cp "%CP%" forge.view.Main sim -d "Disguise, Surprise, Reward1912.dck" "Rebel Revision 96.dck" -n 1 -f commander

echo.
echo ================================================
echo Simulation Complete
echo ================================================
echo.

REM Check for log file
set LOGDIR=%APPDATA%\Forge\games\gamelogs
if exist "%LOGDIR%\" (
    echo Checking for game log...
    for /f "delims=" %%F in ('dir /b /o-d "%LOGDIR%\gamelog*.txt" 2^>nul') do (
        set LOGFILE=%%F
        goto :foundlog
    )
    echo No log files found in %LOGDIR%
    goto :end

    :foundlog
    echo.
    echo Latest log file: !LOGFILE!
    echo Location: %LOGDIR%\!LOGFILE!
    echo.
    echo Opening log file...
    notepad "%LOGDIR%\!LOGFILE!"
) else (
    echo Log directory not found: %LOGDIR%
)

:end
echo.
pause

