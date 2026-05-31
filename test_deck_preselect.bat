@echo off
cd /d D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target
java -jar forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar --format commander --deck "killriam - Horror: Dead is not an end (2026-05-18)" 2>&1 | findstr /C:"DECK-PRESELECT"
pause

