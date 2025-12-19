# Test simulation for two Commander decks
$ErrorActionPreference = "Continue"

Write-Host "=== Forge Commander Simulation Test ===" -ForegroundColor Green
Write-Host "Deck 1: Disguise, Surprise, Reward1912.dck"
Write-Host "Deck 2: Rebel Revision 96.dck"
Write-Host ""

# Build classpath
$baseDir = $PWD
$m2 = "$env:USERPROFILE\.m2\repository"

$cp = @(
    "$baseDir\forge-gui-desktop\target\classes",
    "$baseDir\forge-gui\target\classes",
    "$baseDir\forge-game\target\classes",
    "$baseDir\forge-core\target\classes",
    "$baseDir\forge-ai\target\classes",
    "$baseDir\forge-gui\res",
    "$m2\com\google\guava\guava\33.3.1-jre\guava-33.3.1-jre.jar",
    "$m2\org\apache\commons\commons-lang3\3.17.0\commons-lang3-3.17.0.jar",
    "$m2\org\apache\commons\commons-text\1.12.0\commons-text-1.12.0.jar",
    "$m2\commons-cli\commons-cli\1.9.0\commons-cli-1.9.0.jar",
    "$m2\com\googlecode\minlog\1.2\minlog-1.2.jar",
    "$m2\org\jgrapht\jgrapht-core\1.5.2\jgrapht-core-1.5.2.jar",
    "$m2\org\jheaps\jheaps\0.14\jheaps-0.14.jar",
    "$m2\com\google\code\findbugs\jsr305\3.0.2\jsr305-3.0.2.jar",
    "$m2\org\xerial\sqlite-jdbc\3.36.0.3\sqlite-jdbc-3.36.0.3.jar",
    "$m2\io\sentry\sentry-logback\8.21.1\sentry-logback-8.21.1.jar",
    "$m2\io\sentry\sentry\8.21.1\sentry-8.21.1.jar",
    "$m2\ch\qos\logback\logback-classic\1.5.13\logback-classic-1.5.13.jar",
    "$m2\ch\qos\logback\logback-core\1.5.13\logback-core-1.5.13.jar",
    "$m2\org\slf4j\slf4j-api\2.0.16\slf4j-api-2.0.16.jar"
) -join ";"

Write-Host "Classpath configured with $($cp.Split(';').Count) entries"
Write-Host "Starting simulation..." -ForegroundColor Yellow
Write-Host ""

# Run simulation
$vmArgs = @(
    "--add-opens", "java.base/java.util=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens", "java.base/java.text=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED",
    "-cp", $cp,
    "forge.view.Main",
    "sim",
    "-d", "Disguise, Surprise, Reward1912.dck", "Rebel Revision 96.dck",
    "-n", "1",
    "-f", "commander"
)

& java $vmArgs

Write-Host ""
Write-Host "=== Simulation Complete ===" -ForegroundColor Green
Write-Host ""

# Check for log files
$logDir = "$env:APPDATA\Forge\games\gamelogs"
if (Test-Path $logDir) {
    Write-Host "Checking for game logs in: $logDir" -ForegroundColor Cyan
    $latestLog = Get-ChildItem $logDir -Filter "*.txt" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

    if ($latestLog) {
        Write-Host "Latest log file: $($latestLog.Name)" -ForegroundColor Green
        Write-Host "Created: $($latestLog.LastWriteTime)"
        Write-Host "Size: $($latestLog.Length) bytes"
        Write-Host ""
        Write-Host "=== LOG CONTENT (First 200 lines) ===" -ForegroundColor Cyan
        Write-Host ""
        Get-Content $latestLog.FullName -TotalCount 200
    } else {
        Write-Host "No log files found in $logDir" -ForegroundColor Red
    }
} else {
    Write-Host "Log directory not found: $logDir" -ForegroundColor Red
}

