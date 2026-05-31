# Commander AI Batch Simulation Runner
# Version 2.1.0 - With Auto-Opponent Generation
param(
    [Parameter(Mandatory=$true)]
    [string]$Deck1,

    [Parameter(Mandatory=$false)]
    [string]$Deck2 = "",

    [Parameter(Mandatory=$false)]
    [int]$Games = 100,

    [Parameter(Mandatory=$false)]
    [int]$Timeout = 180,

    [Parameter(Mandatory=$false)]
    [switch]$Quiet
)

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "  Commander AI Batch Simulation v2.1" -ForegroundColor Cyan
Write-Host "===============================================`n" -ForegroundColor Cyan

$projectRoot = $PSScriptRoot
$forgeJar = Get-ChildItem "$projectRoot\forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar" -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $forgeJar) {
    Write-Host "ERROR: forge.jar not found!" -ForegroundColor Red
    Write-Host "  Build first: mvn clean package -pl forge-gui-desktop -am -DskipTests`n" -ForegroundColor Yellow
    exit 1
}

Write-Host "OK Found forge.jar: $($forgeJar.Name)" -ForegroundColor Green

# Auto-create opponent deck if not specified
if (-not $Deck2 -or $Deck2 -eq "") {
    Write-Host "Setting up default opponent..." -ForegroundColor Yellow

    # Run the opponent generator script
    $opponentScript = Join-Path $projectRoot "create_default_opponent.ps1"
    if (Test-Path $opponentScript) {
        $defaultOpponent = & $opponentScript

        if ($defaultOpponent) {
            $Deck2 = $defaultOpponent
            Write-Host "OK Using: $Deck2 (The Walls of Ba Sing Se)" -ForegroundColor Green
        } else {
            Write-Host "WARN Auto-generation failed - using Deck1 copy" -ForegroundColor Yellow
            $Deck2 = $Deck1
        }
    } else {
        Write-Host "WARN Auto-opponent script not found - using Deck1" -ForegroundColor Yellow
        $Deck2 = $Deck1
    }
} else {
    Write-Host "OK Using opponent: $Deck2" -ForegroundColor Green
}

$appdata = $env:APPDATA
$deckDir = "$appdata\Forge\decks\commander"

# Handle .dck extension
if (-not $Deck1.EndsWith(".dck")) {
    $deck1Name = "$Deck1.dck"
} else {
    $deck1Name = $Deck1
}

if (-not $Deck2.EndsWith(".dck")) {
    $deck2Name = "$Deck2.dck"
} else {
    $deck2Name = $Deck2
}

$deck1Path = Join-Path $deckDir $deck1Name
$deck2Path = Join-Path $deckDir $deck2Name

# Validate decks
if (-not (Test-Path $deck1Path)) {
    Write-Host "ERROR: Deck 1 not found: $deck1Path" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $deck2Path)) {
    Write-Host "ERROR: Deck 2 not found: $deck2Path" -ForegroundColor Red
    Write-Host "  Available decks:" -ForegroundColor Yellow
    Get-ChildItem $deckDir -Filter "*.dck" | Select-Object -First 5 | ForEach-Object { Write-Host "    - $($_.Name)" -ForegroundColor Gray }
    exit 1
}

Write-Host "OK Deck 1: $deck1Name" -ForegroundColor Green
Write-Host "OK Deck 2: $deck2Name" -ForegroundColor Green

# Ensure log directory exists
$gamelogDir = "$appdata\Forge\games\simulation_stats"
New-Item -ItemType Directory -Path $gamelogDir -Force -ErrorAction SilentlyContinue | Out-Null

Write-Host "`n===============================================" -ForegroundColor Gray
Write-Host "Simulation Configuration:" -ForegroundColor Yellow
Write-Host "  Games:    $Games" -ForegroundColor White
Write-Host "  Timeout:  $Timeout seconds per game" -ForegroundColor White
Write-Host "  Format:   Commander" -ForegroundColor White
Write-Host "===============================================`n" -ForegroundColor Gray

Write-Host "Starting simulation...`n" -ForegroundColor Cyan

# Remove .dck extension for command
$deck1Base = $deck1Name -replace '\.dck$', ''
$deck2Base = $deck2Name -replace '\.dck$', ''

# Build Java command - IMPORTANT: both decks must follow a SINGLE -d flag!
# Using two separate -d flags causes the second to overwrite the first.
$javaArgs = @(
    "-jar", $forgeJar.FullName,
    "sim",
    "-d", "$deck1Base.dck", "$deck2Base.dck",
    "-n", $Games,
    "-f", "commander",
    "-c", $Timeout
)

if ($Quiet) {
    $javaArgs += "-q"
}

# Run simulation
Push-Location $projectRoot
$startTime = Get-Date
java @javaArgs
$exitCode = $LASTEXITCODE
$endTime = Get-Date
Pop-Location

$duration = $endTime - $startTime

# Count generated logs
$newLogs = @(Get-ChildItem "$gamelogDir\simulation_stats_*.json" -ErrorAction SilentlyContinue).Count

Write-Host "`n===============================================" -ForegroundColor Cyan
if ($exitCode -eq 0) {
    Write-Host "OK Simulation Complete!" -ForegroundColor Green
} else {
    Write-Host "WARN Simulation ended with exit code: $exitCode" -ForegroundColor Yellow
}
Write-Host "===============================================" -ForegroundColor Cyan

Write-Host "`nStatistics:" -ForegroundColor Yellow
Write-Host "   Duration:   $($duration.TotalMinutes.ToString('0.0')) minutes" -ForegroundColor White
Write-Host "   Games:      $Games requested" -ForegroundColor White
Write-Host "   Logs:       $newLogs JSON files" -ForegroundColor White
Write-Host "   Log dir:    $gamelogDir" -ForegroundColor Gray

Write-Host "`nNext steps:" -ForegroundColor Yellow
Write-Host "   Analyze: python analyze_commander_stats.py" -ForegroundColor Gray
Write-Host "   Report:  notepad commander_simulation_report.json`n" -ForegroundColor Gray

exit $exitCode






