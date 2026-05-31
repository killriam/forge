# Simple Upstream Forge Starter with Java 17 Download
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  UPSTREAM FORGE - Java 17 Setup" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

$forgeDir = "D:\Daten\SoftwareProjekte\Forge\forge-upstream"
$java17Dir = "$forgeDir\java17-portable"

# Check if Java 17 already exists
if (Test-Path "$java17Dir\bin\java.exe") {
    Write-Host "Java 17 portable already installed" -ForegroundColor Green
} else {
    Write-Host "Downloading Java 17..." -ForegroundColor Yellow

    $downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.10%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.10_7.zip"
    $zipFile = "$env:TEMP\openjdk17.zip"

    try {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $zipFile -UseBasicParsing
        Write-Host "Download complete" -ForegroundColor Green

        Write-Host "Extracting Java 17..." -ForegroundColor Yellow
        Expand-Archive -Path $zipFile -DestinationPath $forgeDir -Force

        # Rename
        $extractedFolder = Get-ChildItem $forgeDir -Directory | Where-Object {$_.Name -like "jdk-17*"} | Select-Object -First 1
        if ($extractedFolder) {
            Move-Item $extractedFolder.FullName $java17Dir -Force
            Write-Host "Java 17 installed to: $java17Dir" -ForegroundColor Green
        }

        Remove-Item $zipFile -Force -ErrorAction SilentlyContinue
    } catch {
        Write-Host "Download failed: $_" -ForegroundColor Red
        Write-Host "Please install Java 17 manually from:" -ForegroundColor Yellow
        Write-Host "https://adoptium.net/de/temurin/releases/?version=17" -ForegroundColor Cyan
        pause
        exit
    }
}

# Backup fork preferences
$forkPrefs = "$env:APPDATA\Forge\preferences\forge.preferences"
if (Test-Path $forkPrefs) {
    Move-Item $forkPrefs "$forkPrefs.FORK" -Force -ErrorAction SilentlyContinue
    Write-Host "Fork preferences backed up" -ForegroundColor Green
}

# Start Upstream Forge
Write-Host "Starting Upstream Forge with Java 17..." -ForegroundColor Cyan
Set-Location "$forgeDir\forge-gui-desktop\target"

$javaExe = "$java17Dir\bin\java.exe"
Write-Host "Java: $javaExe" -ForegroundColor Gray
& $javaExe -version

Write-Host "Starting GUI..." -ForegroundColor Yellow
& $javaExe -jar forge-gui-desktop-2.0.13-SNAPSHOT-jar-with-dependencies.jar

