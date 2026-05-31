# Test Simulation and Log Analysis Script
# Tests the replay log writer fixes (views_l2, controller, owner fields)

param(
    [string]$Deck1 = "forge-gui/res/quest/world/Zendikar_Standard/duels/6.dck",
    [string]$Deck2 = "forge-gui/res/quest/world/Zendikar_Standard/duels/7.dck",
    [int]$NumGames = 1
)

Write-Host "=== Forge Replay Log Test ===" -ForegroundColor Cyan
Write-Host ""

# Find JAR file
$jarFile = Get-ChildItem "forge-gui-desktop/target/*-jar-with-dependencies.jar" | Select-Object -First 1
if (-not $jarFile) {
    Write-Host "ERROR: JAR file not found. Run 'mvn clean package -pl forge-gui-desktop -am' first." -ForegroundColor Red
    exit 1
}

Write-Host "Using JAR: $($jarFile.Name)" -ForegroundColor Green
Write-Host ""

# Run simulation
Write-Host "Running simulation..." -ForegroundColor Yellow
Write-Host "  Deck 1: $Deck1"
Write-Host "  Deck 2: $Deck2"
Write-Host "  Games: $NumGames"
Write-Host ""

$output = java -jar $jarFile.FullName sim -D (Get-Location).Path -d $Deck1 $Deck2 -n $NumGames 2>&1 | Out-String

# Check if simulation ran
if ($output -match "Error|Could not load") {
    Write-Host "SIMULATION FAILED:" -ForegroundColor Red
    Write-Host $output
    exit 1
}

Write-Host "Simulation completed!" -ForegroundColor Green
Write-Host ""

# Find generated replay logs
$replayLogs = Get-ChildItem "forge-gui-desktop/sim_*.json" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending

if ($replayLogs.Count -eq 0) {
    Write-Host "WARNING: No replay logs found in forge-gui-desktop/" -ForegroundColor Yellow
    Write-Host "Checking %APPDATA%\Forge\games\gamelogs..." -ForegroundColor Yellow
    
    $appDataPath = "$env:APPDATA\Forge\games\gamelogs"
    if (Test-Path $appDataPath) {
        $replayLogs = Get-ChildItem "$appDataPath\sim_*.json" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
    }
}

if ($replayLogs.Count -eq 0) {
    Write-Host "ERROR: No replay logs found!" -ForegroundColor Red
    exit 1
}

$latestLog = $replayLogs[0]
Write-Host "Found replay log: $($latestLog.Name)" -ForegroundColor Green
Write-Host "  Path: $($latestLog.FullName)"
Write-Host "  Size: $([math]::Round($latestLog.Length / 1KB, 2)) KB"
Write-Host ""

# Analyze log content
Write-Host "=== Analyzing Log Content ===" -ForegroundColor Cyan
Write-Host ""

try {
    $logContent = Get-Content $latestLog.FullName -Raw | ConvertFrom-Json
    
    # Check 1: views_l2 populated
    Write-Host "[CHECK 1] views_l2 populated:" -ForegroundColor Yellow
    if ($logContent.views_l2 -and $logContent.views_l2.Count -gt 0) {
        Write-Host "  ✅ YES - Found $($logContent.views_l2.Count) L2 units" -ForegroundColor Green
        
        # Check first L2 unit structure
        $firstL2 = $logContent.views_l2[0]
        Write-Host "  - First L2 unit:" -ForegroundColor Gray
        Write-Host "    Time: $($firstL2.t_start) → $($firstL2.t_end)" -ForegroundColor Gray
        Write-Host "    L1 range: [$($firstL2.l1_range[0]), $($firstL2.l1_range[1])]" -ForegroundColor Gray
        Write-Host "    Decision events: $($firstL2.decision_events.Count)" -ForegroundColor Gray
        
        # Check for hand zones in before state
        if ($firstL2.before -and $firstL2.before.zones) {
            $handZones = $firstL2.before.zones.PSObject.Properties | Where-Object { $_.Name -match ":hand$" }
            Write-Host "    Hand zones in 'before': $($handZones.Count)" -ForegroundColor Gray
            
            if ($handZones.Count -gt 0) {
                Write-Host "      ✅ Hand zones present: $($handZones.Name -join ', ')" -ForegroundColor Green
            } else {
                Write-Host "      ❌ NO HAND ZONES FOUND!" -ForegroundColor Red
            }
        }
        
        # Check for hand zones in after state
        if ($firstL2.after -and $firstL2.after.zones) {
            $handZones = $firstL2.after.zones.PSObject.Properties | Where-Object { $_.Name -match ":hand$" }
            Write-Host "    Hand zones in 'after': $($handZones.Count)" -ForegroundColor Gray
        }
    } else {
        Write-Host "  ❌ NO - views_l2 is empty!" -ForegroundColor Red
    }
    Write-Host ""
    
    # Check 2: MOVE events have controller + owner
    Write-Host "[CHECK 2] MOVE events have controller + owner:" -ForegroundColor Yellow
    $moveEvents = $logContent.events | Where-Object { $_.type -eq "MOVE" }
    if ($moveEvents) {
        $withController = ($moveEvents | Where-Object { $_.data.controller }).Count
        $withOwner = ($moveEvents | Where-Object { $_.data.owner }).Count
        
        Write-Host "  Total MOVE events: $($moveEvents.Count)" -ForegroundColor Gray
        Write-Host "  With 'controller': $withController ($([math]::Round(100*$withController/$moveEvents.Count, 1))%)" -ForegroundColor Gray
        Write-Host "  With 'owner': $withOwner ($([math]::Round(100*$withOwner/$moveEvents.Count, 1))%)" -ForegroundColor Gray
        
        if ($withController -gt 0) {
            Write-Host "  ✅ controller field present" -ForegroundColor Green
        } else {
            Write-Host "  ❌ controller field MISSING!" -ForegroundColor Red
        }
        
        if ($withOwner -gt 0) {
            Write-Host "  ✅ owner field present" -ForegroundColor Green
        } else {
            Write-Host "  ❌ owner field MISSING!" -ForegroundColor Red
        }
    } else {
        Write-Host "  ⚠️  No MOVE events found" -ForegroundColor Yellow
    }
    Write-Host ""
    
    # Check 3: DRAW events have controller + owner
    Write-Host "[CHECK 3] DRAW events have controller + owner:" -ForegroundColor Yellow
    $drawEvents = $logContent.events | Where-Object { $_.type -eq "DRAW" }
    if ($drawEvents) {
        $withController = ($drawEvents | Where-Object { $_.data.controller }).Count
        $withOwner = ($drawEvents | Where-Object { $_.data.owner }).Count
        
        Write-Host "  Total DRAW events: $($drawEvents.Count)" -ForegroundColor Gray
        Write-Host "  With 'controller': $withController ($([math]::Round(100*$withController/$drawEvents.Count, 1))%)" -ForegroundColor Gray
        Write-Host "  With 'owner': $withOwner ($([math]::Round(100*$withOwner/$drawEvents.Count, 1))%)" -ForegroundColor Gray
        
        if ($withController -gt 0) {
            Write-Host "  ✅ controller field present" -ForegroundColor Green
        } else {
            Write-Host "  ❌ controller field MISSING!" -ForegroundColor Red
        }
        
        if ($withOwner -gt 0) {
            Write-Host "  ✅ owner field present" -ForegroundColor Green
        } else {
            Write-Host "  ❌ owner field MISSING!" -ForegroundColor Red
        }
    } else {
        Write-Host "  ⚠️  No DRAW events found" -ForegroundColor Yellow
    }
    Write-Host ""
    
    # Summary
    Write-Host "=== Summary ===" -ForegroundColor Cyan
    Write-Host "Log file: $($latestLog.Name)" -ForegroundColor Gray
    Write-Host "Format version: $($logContent.version)" -ForegroundColor Gray
    Write-Host "Spec version: $($logContent.spec_version)" -ForegroundColor Gray
    Write-Host "Game type: $($logContent.meta.game_type)" -ForegroundColor Gray
    Write-Host "Total turns: $($logContent.meta.turns)" -ForegroundColor Gray
    Write-Host "Winner: $($logContent.meta.winner)" -ForegroundColor Gray
    Write-Host "Total events: $($logContent.events.Count)" -ForegroundColor Gray
    Write-Host "L2 units: $($logContent.views_l2.Count)" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "✅ All checks completed!" -ForegroundColor Green
    
} catch {
    Write-Host "ERROR parsing JSON: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Log file location: $($latestLog.FullName)" -ForegroundColor Cyan
