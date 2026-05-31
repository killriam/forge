# Findings Monitor — PowerShell Version
# Überwacht externe Testing-Findings und aktualisiert README

$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Forge Findings Monitor (PowerShell)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Prüfe Python
Write-Host "Checking Python..." -ForegroundColor Yellow
try {
    $pythonVersion = python --version 2>&1
    Write-Host "✓ Found: $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Python not found!" -ForegroundColor Red
    Write-Host "   Please install Python 3.7+ and add to PATH" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "Starting monitor..." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop" -ForegroundColor Yellow
Write-Host ""

# Wechsel ins Script-Verzeichnis
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# Starte Monitor
python monitor_findings.py

Write-Host ""
Write-Host "Monitor stopped." -ForegroundColor Yellow
Read-Host "Press Enter to exit"

