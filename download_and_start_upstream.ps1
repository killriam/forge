# Download und Start von Upstream Forge mit Java 17
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  UPSTREAM FORGE - Java 17 Setup" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Cyan

$forgeDir = "D:\Daten\SoftwareProjekte\Forge\forge-upstream"
$java17Dir = "$forgeDir\java17-portable"

# Prüfe ob Java 17 bereits vorhanden
if (Test-Path "$java17Dir\bin\java.exe") {
    Write-Host "✅ Java 17 (portable) bereits vorhanden" -ForegroundColor Green
} else {
    Write-Host "⬇ Lade Java 17 herunter (portable)..." -ForegroundColor Yellow

    $downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.10%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.10_7.zip"
    $zipFile = "$env:TEMP\openjdk17.zip"

    try {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $zipFile -UseBasicParsing
        Write-Host "✅ Download abgeschlossen" -ForegroundColor Green

        Write-Host "📦 Entpacke Java 17..." -ForegroundColor Yellow
        Expand-Archive -Path $zipFile -DestinationPath $forgeDir -Force

        # Umbenennen
        $extractedFolder = Get-ChildItem $forgeDir -Directory | Where-Object {$_.Name -like "jdk-17*"} | Select-Object -First 1
        if ($extractedFolder) {
            Move-Item $extractedFolder.FullName $java17Dir -Force
            Write-Host "✅ Java 17 installiert nach: $java17Dir" -ForegroundColor Green
        }

        Remove-Item $zipFile -Force -ErrorAction SilentlyContinue
    } catch {
        Write-Host "❌ Download fehlgeschlagen: $_" -ForegroundColor Red
        Write-Host "`nAlternative: Installieren Sie Java 17 manuell:" -ForegroundColor Yellow
        Write-Host "https://adoptium.net/de/temurin/releases/?version=17" -ForegroundColor Cyan
        pause
        exit
    }
}

# Sichere Fork-Präferenzen
$forkPrefs = "$env:APPDATA\Forge\preferences\forge.preferences"
if (Test-Path $forkPrefs) {
    Move-Item $forkPrefs "$forkPrefs.FORK" -Force -ErrorAction SilentlyContinue
    Write-Host "`n✅ Fork-Präferenzen gesichert" -ForegroundColor Green
}

# Starte Upstream Forge
Write-Host "`n🚀 Starte Upstream Forge mit Java 17..." -ForegroundColor Cyan
Set-Location "$forgeDir\forge-gui-desktop\target"

$javaExe = "$java17Dir\bin\java.exe"
Write-Host "Java: $javaExe" -ForegroundColor Gray
& $javaExe -version

Write-Host "Starte GUI..." -ForegroundColor Yellow
& $javaExe -jar forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar


