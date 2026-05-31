# ========================================
#   Forge - Debug Mode with JSON Logging
# ========================================

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Forge - Debug Mode" -ForegroundColor Cyan
Write-Host "  JSON Replay Notation: ENABLED" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Set the EXE path
$ForgeExe = "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge.exe"
$GameLogDir = "$env:APPDATA\Forge\games\gamelogs"

# Check if EXE exists
if (-not (Test-Path $ForgeExe)) {
    Write-Host "[ERROR] forge.exe not found at:" -ForegroundColor Red
    Write-Host "  $ForgeExe" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please compile first with:" -ForegroundColor Yellow
    Write-Host "  mvn clean package -pl forge-gui-desktop -am '-Dmaven.test.skip=true'" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "[DEBUG] Starting Forge..." -ForegroundColor Yellow
Write-Host "[DEBUG] EXE: $ForgeExe" -ForegroundColor Gray
Write-Host "[DEBUG] Game logs directory:" -ForegroundColor Gray
Write-Host "        $GameLogDir" -ForegroundColor Gray
Write-Host ""
Write-Host "[INFO] After your game, JSON files will be in:" -ForegroundColor Cyan
Write-Host "       $GameLogDir\" -ForegroundColor White
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting Forge GUI now..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Start Forge
$process = Start-Process -FilePath $ForgeExe -PassThru
Write-Host "[INFO] Forge started (PID: $($process.Id))" -ForegroundColor Green
Write-Host ""
Write-Host "Instructions:" -ForegroundColor Yellow
Write-Host "  1. Play your game normally" -ForegroundColor White
Write-Host "  2. When game ends, SAVE THE GAME LOG (important!)" -ForegroundColor White
Write-Host "  3. Return to this console" -ForegroundColor White
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Count existing JSON files before
$jsonCountBefore = @(Get-ChildItem -Path $GameLogDir -Filter "replay_*.json" -ErrorAction SilentlyContinue).Count
Write-Host "[BEFORE] Current JSON files in gamelogs: $jsonCountBefore" -ForegroundColor Gray
Write-Host ""

Write-Host "Waiting for you to finish your game..." -ForegroundColor Yellow
Write-Host "Press any key when you're done and have saved the game log..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Checking for new JSON logs..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Count JSON files after
$jsonCountAfter = @(Get-ChildItem -Path $GameLogDir -Filter "replay_*.json" -ErrorAction SilentlyContinue).Count
Write-Host "[AFTER] Current JSON files in gamelogs: $jsonCountAfter" -ForegroundColor Gray
Write-Host ""

if ($jsonCountAfter -gt $jsonCountBefore) {
    Write-Host "[SUCCESS] New JSON file(s) created!" -ForegroundColor Green
    Write-Host ""

    # Show latest JSON file
    $latestJson = Get-ChildItem -Path $GameLogDir -Filter "replay_*.json" |
                  Sort-Object LastWriteTime -Descending |
                  Select-Object -First 1

    if ($latestJson) {
        Write-Host "Latest JSON file:" -ForegroundColor Cyan
        Write-Host "  Name: $($latestJson.Name)" -ForegroundColor White
        Write-Host "  Size: $($latestJson.Length) bytes" -ForegroundColor White
        Write-Host "  Time: $($latestJson.LastWriteTime)" -ForegroundColor White
        Write-Host ""

        # Show first 30 lines
        Write-Host "First 30 lines of JSON:" -ForegroundColor Cyan
        Write-Host "----------------------------------------" -ForegroundColor Gray
        Get-Content $latestJson.FullName | Select-Object -First 30
        Write-Host "----------------------------------------" -ForegroundColor Gray
        Write-Host ""

        # Validate JSON
        Write-Host "Validating JSON..." -ForegroundColor Yellow
        try {
            $json = Get-Content $latestJson.FullName | ConvertFrom-Json
            Write-Host "[SUCCESS] JSON is valid!" -ForegroundColor Green
            Write-Host ""
            Write-Host "JSON Summary:" -ForegroundColor Cyan
            Write-Host "  Format:      $($json.format)" -ForegroundColor White
            Write-Host "  Version:     $($json.version)" -ForegroundColor White
            Write-Host "  Game ID:     $($json.meta.game_id)" -ForegroundColor White
            Write-Host "  Game Type:   $($json.meta.game_type)" -ForegroundColor White
            if ($json.meta.winner) {
                Write-Host "  Winner:      $($json.meta.winner)" -ForegroundColor White
            }
            if ($json.meta.turns) {
                Write-Host "  Turns:       $($json.meta.turns)" -ForegroundColor White
            }
            Write-Host "  L1 Events:   $($json.log_l1.Count)" -ForegroundColor White
            if ($json.views_l2) {
                Write-Host "  L2 Units:    $($json.views_l2.Count)" -ForegroundColor White
            }
            Write-Host ""
        } catch {
            Write-Host "[ERROR] JSON validation failed!" -ForegroundColor Red
            Write-Host "  $_" -ForegroundColor Red
            Write-Host ""
        }

        Write-Host "Full path to JSON:" -ForegroundColor Cyan
        Write-Host "  $($latestJson.FullName)" -ForegroundColor White
        Write-Host ""
    }

} elseif ($jsonCountAfter -eq $jsonCountBefore -and $jsonCountAfter -gt 0) {
    Write-Host "[WARNING] No NEW JSON files created, but existing ones found." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Possible reasons:" -ForegroundColor Yellow
    Write-Host "  1. You didn't save the game log after the match" -ForegroundColor White
    Write-Host "  2. Using old Forge version (not newly compiled)" -ForegroundColor White
    Write-Host ""

    # Show existing files
    Write-Host "Existing JSON files:" -ForegroundColor Cyan
    Get-ChildItem -Path $GameLogDir -Filter "replay_*.json" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 5 |
        ForEach-Object {
            Write-Host "  - $($_.Name) ($($_.LastWriteTime))" -ForegroundColor Gray
        }
    Write-Host ""

} else {
    Write-Host "[WARNING] No JSON files found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Possible reasons:" -ForegroundColor Yellow
    Write-Host "  1. Game log was not saved after the match" -ForegroundColor White
    Write-Host "  2. Using old Forge version (not the newly compiled one)" -ForegroundColor White
    Write-Host "  3. Auto-enable reflection failed" -ForegroundColor White
    Write-Host ""

    # Check if text logs exist
    Write-Host "[DEBUG] Checking for text game logs..." -ForegroundColor Yellow
    $textLogs = Get-ChildItem -Path $GameLogDir -Filter "gamelog_*.txt" -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 3

    if ($textLogs) {
        Write-Host ""
        Write-Host "Recent text logs found:" -ForegroundColor Cyan
        $textLogs | ForEach-Object {
            Write-Host "  - $($_.Name) ($($_.LastWriteTime))" -ForegroundColor Gray
        }
        Write-Host ""
        Write-Host "Text logs exist, but no JSON. This means:" -ForegroundColor Yellow
        Write-Host "  -> JSON logging was NOT active during the game" -ForegroundColor Red
        Write-Host "  -> You need to use the NEWLY COMPILED forge.exe" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Solution:" -ForegroundColor Cyan
        Write-Host "  1. Make sure you're using: $ForgeExe" -ForegroundColor White
        Write-Host "  2. NOT an installed version from somewhere else" -ForegroundColor White
        Write-Host ""
    } else {
        Write-Host ""
        Write-Host "No game logs found at all (text or JSON)." -ForegroundColor Red
        Write-Host "Did you save the game log after the match?" -ForegroundColor Yellow
        Write-Host ""
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Debug session complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Read-Host "Press Enter to exit"

