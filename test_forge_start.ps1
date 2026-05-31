Set-Location "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target"
$logFile = "D:\Daten\SoftwareProjekte\Forge\forge\forge_start.log"
Write-Host "Starting Forge with deck preselection..." -ForegroundColor Green
Write-Host "Log: $logFile" -ForegroundColor Yellow
java -jar forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar gui --format commander --deck "killriam - Horror: Dead is not an end (2026-05-18)" 2>&1 | Tee-Object $logFile
Write-Host "Forge exited. Check log at: $logFile" -ForegroundColor Cyan


