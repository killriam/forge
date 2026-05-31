# Start Upstream Forge
Write-Host "`n=== UPSTREAM FORGE STARTER ===" -ForegroundColor Cyan

# Suche Java 17
Write-Host "Suche Java 17..." -ForegroundColor Yellow
$java17 = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
    Where-Object {$_.Name -match "jdk-?17"} |
    Select-Object -First 1

if ($java17) {
    $javaCmd = Join-Path $java17.FullName "bin\java.exe"
    Write-Host "✅ Java 17 gefunden: $javaCmd" -ForegroundColor Green
    & $javaCmd -version
} else {
    $javaCmd = "java"
    Write-Host "⚠ Java 17 nicht gefunden, verwende Standard-Java" -ForegroundColor Yellow
    java -version
}

# Sichere Fork-Präferenzen
Write-Host "`nSichere Fork-Präferenzen..." -ForegroundColor Yellow
$forkPrefs = "$env:APPDATA\Forge\preferences\forge.preferences"
if (Test-Path $forkPrefs) {
    Move-Item $forkPrefs "$forkPrefs.FORK" -Force
    Write-Host "✅ Fork-Präferenzen gesichert" -ForegroundColor Green
} else {
    Write-Host "ℹ Bereits gesichert oder nicht vorhanden" -ForegroundColor Gray
}

# Wechsle zu upstream
Write-Host "`nWechsle zu upstream target..." -ForegroundColor Yellow
Set-Location "D:\Daten\SoftwareProjekte\Forge\forge-upstream\forge-gui-desktop\target"

# Zeige JAR-Info
$jar = Get-Item "forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar"
Write-Host "JAR: $($jar.Name)" -ForegroundColor Gray
Write-Host "Größe: $([math]::Round($jar.Length/1MB,2)) MB" -ForegroundColor Gray
Write-Host "Datum: $($jar.LastWriteTime)" -ForegroundColor Gray

# Starte Forge
Write-Host "`n🚀 STARTE UPSTREAM FORGE GUI..." -ForegroundColor Green -BackgroundColor DarkGreen
& $javaCmd -jar forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar


