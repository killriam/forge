# Download Official Forge Release (Pre-Built)
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Official Forge 2.0.12 Download" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

$downloadDir = "D:\Daten\SoftwareProjekte\Forge\forge-official-2.0.12"
$zipFile = "$env:TEMP\forge-2.0.12.zip"

if (!(Test-Path $downloadDir)) {
    New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null
}

Write-Host "`nDownloading Official Forge 2.0.12..." -ForegroundColor Yellow
$url = "https://github.com/Card-Forge/forge/releases/download/forge-2.0.12/forge-gui-desktop-2.0.12.tar.bz2"

try {
    Invoke-WebRequest-Uri $url -OutFile $zipFile -UseBasicParsing
    Write-Host "Download complete!" -ForegroundColor Green

    Write-Host "Extracting..." -ForegroundColor Yellow
    # Note: .tar.bz2 requires special handling in PowerShell
    tar -xjf $zipFile -C $downloadDir

    Write-Host "Official Forge extracted to: $downloadDir" -ForegroundColor Green
    Write-Host "`nStarting Forge..." -ForegroundColor Cyan

    Set-Location $downloadDir
    & "D:\Daten\SoftwareProjekte\Forge\forge-upstream\java17-portable\bin\java.exe" -jar forge-gui-desktop-2.0.12.jar

} catch {
    Write-Host "Download failed: $_" -ForegroundColor Red
    Write-Host "`nAlternative: Lade manuell herunter von:" -ForegroundColor Yellow
    Write-Host "https://github.com/Card-Forge/forge/releases/tag/forge-2.0.12" -ForegroundColor Cyan
    pause
}

