# Monitor Forge game logs
$logDir = "$env:APPDATA\Forge\games\gamelogs"

Write-Host "=== Forge Game Log Monitor ===" -ForegroundColor Green
Write-Host "Monitoring directory: $logDir"
Write-Host "Waiting for new log files (checking every 5 seconds)..."
Write-Host ""

$startTime = Get-Date
$timeout = 300 # 5 minutes

while (((Get-Date) - $startTime).TotalSeconds -lt $timeout) {
    if (Test-Path $logDir) {
        $logs = Get-ChildItem $logDir -Filter "gamelog*.txt" -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending

        if ($logs) {
            $newest = $logs[0]
            $age = ((Get-Date) - $newest.LastWriteTime).TotalSeconds

            if ($age -lt 120) { # File modified in last 2 minutes
                Write-Host "`n=== NEW LOG FILE FOUND ===" -ForegroundColor Green
                Write-Host "File: $($newest.Name)"
                Write-Host "Modified: $($newest.LastWriteTime)"
                Write-Host "Size: $($newest.Length) bytes"
                Write-Host ""
                Write-Host "=== LOG CONTENT ===" -ForegroundColor Cyan
                Write-Host ""

                Get-Content $newest.FullName

                Write-Host ""
                Write-Host "=== ANALYSIS ===" -ForegroundColor Yellow
                $content = Get-Content $newest.FullName -Raw

                # Count ANALYSIS entries
                $analysisCount = ([regex]::Matches($content, "Analysis:")).Count
                Write-Host "Total 'Analysis:' entries: $analysisCount"

                # Check for Zone Changes
                $zoneChanges = ([regex]::Matches($content, "moved from .+ to .+")).Count
                Write-Host "Zone change entries: $zoneChanges"

                # Check for Resolving entries
                $resolvingCount = ([regex]::Matches($content, "Resolving:")).Count
                Write-Host "Spell resolution entries: $resolvingCount"

                # Check for Turn Summary
                $turnSummaries = ([regex]::Matches($content, "Turn Summary - Board State Changes")).Count
                Write-Host "Turn summaries: $turnSummaries"

                break
            }
        }
    }

    Write-Host "." -NoNewline
    Start-Sleep -Seconds 5
}

if (((Get-Date) - $startTime).TotalSeconds -ge $timeout) {
    Write-Host "`n`nTimeout reached. No new log files detected."
}

