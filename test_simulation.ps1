# Simple test script
param(
    [Parameter(Mandatory=$true)]
    [string]$Deck1,

    [Parameter(Mandatory=$false)]
    [string]$Deck2 = "Auto_Opponent_Walls",

    [Parameter(Mandatory=$false)]
    [int]$Games = 1
)

Write-Host "Test Simulation Script" -ForegroundColor Green
Write-Host "Deck1: $Deck1"
Write-Host "Deck2: $Deck2"
Write-Host "Games: $Games"

$jar = Get-ChildItem "D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar" | Select-Object -First 1

if (-not $jar) {
    Write-Host "ERROR: JAR not found" -ForegroundColor Red
    exit 1
}

Write-Host "JAR: $($jar.Name)" -ForegroundColor Green

# Add .dck if needed
if (-not $Deck1.EndsWith(".dck")) { $Deck1 = "$Deck1.dck" }
if (-not $Deck2.EndsWith(".dck")) { $Deck2 = "$Deck2.dck" }

Write-Host "Running: java -jar `"$($jar.FullName)`" sim -d `"$Deck1`" `"$Deck2`" -n $Games -f commander -c 180"

java -jar "$($jar.FullName)" sim -d "$Deck1" "$Deck2" -n $Games -f commander -c 180

Write-Host "Exit code: $LASTEXITCODE" -ForegroundColor $(if ($LASTEXITCODE -eq 0) { "Green" } else { "Red" })
exit $LASTEXITCODE

