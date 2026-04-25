#!/usr/bin/env pwsh
# Quick Test Script für Commander Loading Debug

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  FORGE COMMANDER DEBUG TEST" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Prüfe ob JAR existiert
$jarPath = "forge-gui-desktop\target\forge-gui-desktop-2.0.08-SNAPSHOT-jar-with-dependencies.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "JAR nicht gefunden! Baue zuerst..." -ForegroundColor Yellow
    Write-Host "Führe aus: mvn package -DskipTests" -ForegroundColor Yellow
    Write-Host ""

    $build = Read-Host "Jetzt bauen? (j/n)"
    if ($build -eq "j" -or $build -eq "J") {
        Write-Host "Baue Projekt..." -ForegroundColor Cyan
        mvn package -DskipTests

        if ($LASTEXITCODE -ne 0) {
            Write-Host "Build fehlgeschlagen!" -ForegroundColor Red
            exit 1
        }
        Write-Host "Build erfolgreich!" -ForegroundColor Green
    } else {
        Write-Host "Abgebrochen." -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  WICHTIGE HINWEISE:" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1. Nach dem Start: Constructed → Commander wählen" -ForegroundColor White
Write-Host "2. Console-Ausgaben beobachten!" -ForegroundColor White
Write-Host "3. Suche nach: [DECK LOADING DEBUG]" -ForegroundColor White
Write-Host "4. Notiere die Zeiten:" -ForegroundColor White
Write-Host "   - Commander decks loaded in: XXXms" -ForegroundColor Gray
Write-Host "   - Total decks loaded: XXX" -ForegroundColor Gray
Write-Host "   - setup: XXXms" -ForegroundColor Gray
Write-Host "   - Total time: XXXms" -ForegroundColor Gray
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Optional: Log in Datei speichern
$saveLog = Read-Host "Console-Output in Datei speichern? (j/n)"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"

if ($saveLog -eq "j" -or $saveLog -eq "J") {
    $logFile = "commander_debug_$timestamp.log"
    Write-Host "Log wird gespeichert in: $logFile" -ForegroundColor Green
    Write-Host ""
    Write-Host "Starte Forge..." -ForegroundColor Cyan
    java -jar $jarPath 2>&1 | Tee-Object -FilePath $logFile
} else {
    Write-Host "Starte Forge..." -ForegroundColor Cyan
    java -jar $jarPath
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Forge wurde beendet." -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan

