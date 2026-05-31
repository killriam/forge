#!/usr/bin/env pwsh
# Test script for deck preselection

Set-Location "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target"

Write-Host "Testing deck preselection..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Starting Forge with:" -ForegroundColor Yellow
Write-Host "  Format: commander" -ForegroundColor Green
Write-Host "  Deck 1 (Player): Auto Opponent (Basic)" -ForegroundColor Green
Write-Host ""

java -jar forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar gui --format commander --deck "Auto Opponent (Basic)"

Write-Host ""
Write-Host "Forge exited." -ForegroundColor Cyan

