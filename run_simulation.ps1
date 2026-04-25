# MTG Replay Notation - Game Simulation Runner
# Führt die Spielsimulation über forge.jar aus

Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  MTG Replay Notation - Run Simulation via JAR" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════`n" -ForegroundColor Cyan

# Pfade
$projectRoot = $PSScriptRoot
$outputDir = Join-Path $projectRoot "simulation_output"

# Suche nach forge.jar
$forgeJarLocations = @(
    (Join-Path $projectRoot "forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar"),
    (Join-Path $projectRoot "forge-gui-desktop\target\forge.jar"),
    (Join-Path $projectRoot "target\forge.jar"),
    (Join-Path $projectRoot "forge.jar")
)

$forgeJar = $null
foreach ($location in $forgeJarLocations) {
    $matches = Get-ChildItem -Path $location -ErrorAction SilentlyContinue
    if ($matches) {
        $forgeJar = $matches[0].FullName
        break
    }
}

# Erstelle Output-Verzeichnis
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
    Write-Host "✓ Created output directory: $outputDir`n" -ForegroundColor Green
}

Write-Host "Step 1: Locating forge.jar..." -ForegroundColor Yellow

if (-not $forgeJar -or -not (Test-Path $forgeJar)) {
    Write-Host "✗ forge.jar not found!" -ForegroundColor Red
    Write-Host "`nSearched in:" -ForegroundColor Gray
    foreach ($loc in $forgeJarLocations) {
        Write-Host "  - $loc" -ForegroundColor Gray
    }
    Write-Host "`nPlease build the project first with:" -ForegroundColor Yellow
    Write-Host "  mvn clean package -DskipTests`n" -ForegroundColor White
    exit 1
}

Write-Host "✓ Found forge.jar: $forgeJar" -ForegroundColor Green
$jarInfo = Get-Item $forgeJar
Write-Host "  Size: $([math]::Round($jarInfo.Length / 1MB, 2)) MB" -ForegroundColor Gray
Write-Host "  Modified: $($jarInfo.LastWriteTime)" -ForegroundColor Gray

Write-Host "`nStep 2: Running simulation..." -ForegroundColor Yellow
Write-Host "  Command: java -jar forge.jar sim -replay $outputDir" -ForegroundColor Gray
Write-Host "────────────────────────────────────────────────────`n" -ForegroundColor Gray

# Führe Simulation über JAR aus
Push-Location $projectRoot
java -jar "$forgeJar" sim -replay "$outputDir"
$exitCode = $LASTEXITCODE
Pop-Location

if ($exitCode -ne 0) {
    Write-Host "`n✗ Simulation failed with exit code: $exitCode" -ForegroundColor Red
    exit $exitCode
}

Write-Host "`n────────────────────────────────────────────────────" -ForegroundColor Gray
Write-Host "`nStep 4: Checking output..." -ForegroundColor Yellow

# Finde generierte JSON-Datei
$jsonFiles = Get-ChildItem -Path $outputDir -Filter "replay_simulation_*.json" | Sort-Object LastWriteTime -Descending

if ($jsonFiles.Count -eq 0) {
    Write-Host "✗ No JSON file found in output directory!" -ForegroundColor Red
    exit 1
}

$latestJson = $jsonFiles[0]
Write-Host "✓ Found JSON file: $($latestJson.Name)" -ForegroundColor Green
Write-Host "  Size: $($latestJson.Length) bytes" -ForegroundColor Gray
Write-Host "  Path: $($latestJson.FullName)" -ForegroundColor Gray

Write-Host "`nStep 5: Validating JSON format..." -ForegroundColor Yellow

# Prüfe JSON-Inhalt
$jsonContent = Get-Content $latestJson.FullName -Raw

$checks = @(
    @{Pattern = '"format": "mtg-replay"'; Name = "Format field"},
    @{Pattern = '"version": "1.0.0"'; Name = "Version field"},
    @{Pattern = '"game_id"'; Name = "Game ID"},
    @{Pattern = '"players"'; Name = "Players section"},
    @{Pattern = '"card_index"'; Name = "Card index"},
    @{Pattern = '"log_l1"'; Name = "L1 events"},
    @{Pattern = '"views_l2"'; Name = "L2 units"},
    @{Pattern = '"CAST"'; Name = "CAST event"},
    @{Pattern = '"DAMAGE"'; Name = "DAMAGE event"},
    @{Pattern = '"MOVE"'; Name = "MOVE event"}
)

$allPassed = $true
foreach ($check in $checks) {
    if ($jsonContent -match $check.Pattern) {
        Write-Host "  ✓ $($check.Name)" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $($check.Name) - NOT FOUND" -ForegroundColor Red
        $allPassed = $false
    }
}

if (-not $allPassed) {
    Write-Host "`n✗ JSON validation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "`n════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ✅ SIMULATION SUCCESSFUL!" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "`n📄 JSON Output: $($latestJson.FullName)" -ForegroundColor White
Write-Host "`n💡 You can now:" -ForegroundColor Yellow
Write-Host "   - View the JSON file in any text editor" -ForegroundColor Gray
Write-Host "   - Validate it with a JSON validator" -ForegroundColor Gray
Write-Host "   - Use it for replay or analysis" -ForegroundColor Gray
Write-Host ""

