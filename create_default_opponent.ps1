# Auto-Opponent Deck Generator for Forge Commander Simulations
# Creates a default opponent deck if none is specified

param(
    [Parameter(Mandatory=$false)]
    [string]$OutputPath = "$env:APPDATA\Forge\decks\commander"
)

$deckName = "Auto_Opponent_Walls"
$deckFile = Join-Path $OutputPath "$deckName.dck"

Write-Host "Creating Default Opponent Deck..." -ForegroundColor Cyan
Write-Host "   Commander: Ramos, Dragon Engine" -ForegroundColor Gray
Write-Host "   Main: 99x Wastes`n" -ForegroundColor Gray

# Forge .dck format for Commander
$deckContent = @"
[metadata]
Name=Auto Opponent (Basic)
[Commander]
1 Ramos, Dragon Engine
[Main]
99 Wastes
[Sideboard]
"@

# Ensure directory exists
if (-not (Test-Path $OutputPath)) {
    New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null
}

# Write deck file without BOM (Forge cannot parse UTF-8 BOM)
[System.IO.File]::WriteAllText($deckFile, $deckContent, [System.Text.UTF8Encoding]::new($false))

if (Test-Path $deckFile) {
    Write-Host "OK Default opponent deck created!" -ForegroundColor Green
    Write-Host "   Location: $deckFile`n" -ForegroundColor Gray
    return $deckName
} else {
    Write-Host "ERROR Failed to create opponent deck!" -ForegroundColor Red
    return $null
}




