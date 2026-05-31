#!/usr/bin/env pwsh
# Forge Quick Launcher
# Startet Forge mit den neuesten Build-Artifacts

$ErrorActionPreference = "Stop"

Write-Host "="*60 -ForegroundColor Cyan
Write-Host "🎮 Forge Quick Launcher" -ForegroundColor Green
Write-Host "="*60 -ForegroundColor Cyan

$forgeRoot = "D:\Daten\SoftwareProjekte\Forge\forge"
$targetDir = "$forgeRoot\forge-gui-desktop\target"

# Check for forge.exe
$forgeExe = "$targetDir\forge.exe"
$forgeJar = "$targetDir\forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar"

if (Test-Path $forgeExe) {
    Write-Host "✅ Found forge.exe" -ForegroundColor Green
    Write-Host "📂 Location: $forgeExe" -ForegroundColor Gray

    # Get file info
    $fileInfo = Get-Item $forgeExe
    Write-Host "📅 Built: $($fileInfo.LastWriteTime)" -ForegroundColor Gray
    Write-Host "📦 Size: $([math]::Round($fileInfo.Length / 1024, 2)) KB" -ForegroundColor Gray

    Write-Host "`n🚀 Starting Forge..." -ForegroundColor Yellow
    & $forgeExe

} elseif (Test-Path $forgeJar) {
    Write-Host "⚠️  forge.exe not found, using JAR..." -ForegroundColor Yellow
    Write-Host "📂 Location: $forgeJar" -ForegroundColor Gray

    Write-Host "`n🚀 Starting Forge via Java..." -ForegroundColor Yellow
    java -jar $forgeJar

} else {
    Write-Host "❌ Forge not found! Building..." -ForegroundColor Red
    Write-Host "`n🔨 Running Maven build..." -ForegroundColor Yellow

    Push-Location $forgeRoot
    try {
        mvn clean package -pl forge-gui-desktop -am -DskipTests

        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Build successful!" -ForegroundColor Green

            if (Test-Path $forgeExe) {
                Write-Host "`n🚀 Starting Forge..." -ForegroundColor Yellow
                & $forgeExe
            } else {
                Write-Host "❌ Build completed but forge.exe not found!" -ForegroundColor Red
            }
        } else {
            Write-Host "❌ Build failed with exit code: $LASTEXITCODE" -ForegroundColor Red
        }
    } finally {
        Pop-Location
    }
}

