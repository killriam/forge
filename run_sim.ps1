# Forge Simulation Runner Script
$ErrorActionPreference = "Continue"

$env:JAVA_HOME = "C:\Users\killr\.jdks\openjdk-23.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$baseDir = "C:\Users\killr\source\repos\forge2"
$m2Repo = "C:\Users\killr\.m2\repository"
$workDir = "$baseDir\forge-gui-desktop"

# Build classpath from compiled classes
$classpathItems = @(
    "$baseDir\forge-gui-desktop\target\classes",
    "$baseDir\forge-gui\target\classes",
    "$baseDir\forge-game\target\classes",
    "$baseDir\forge-core\target\classes",
    "$baseDir\forge-ai\target\classes",
    "$baseDir\forge-gui\res"
)

# Add all jars from Maven repository
$jars = Get-ChildItem "$m2Repo" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notlike "*sources*" -and $_.FullName -notlike "*javadoc*" }

foreach ($jar in $jars) {
    $classpathItems += $jar.FullName
}

$classpath = $classpathItems -join ";"

Write-Host "Classpath has $($classpathItems.Count) entries"
Write-Host "Working directory: $workDir"
Write-Host ""
Write-Host "Running simulation..."
Write-Host ""

$vmArgs = @(
    "-Xms768m",
    "-XX:+UseParallelGC",
    "--add-opens", "java.base/java.util=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens", "java.base/java.text=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED",
    "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
    "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens", "java.base/java.nio=ALL-UNNAMED",
    "--add-opens", "java.base/java.math=ALL-UNNAMED",
    "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens", "java.base/java.net=ALL-UNNAMED",
    "--add-opens", "java.desktop/javax.swing=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.beans=ALL-UNNAMED",
    "--add-opens", "java.desktop/javax.swing.border=ALL-UNNAMED",
    "-Dio.netty.tryReflectionSetAccessible=true",
    "-cp", $classpath,
    "forge.view.Main",
    "sim",
    "-d", "ashes 31102024", "Disguise, Surprise, Reward1512",
    "-n", "1",
    "-f", "commander"
)

Set-Location $workDir
& "$env:JAVA_HOME\bin\java.exe" $vmArgs 2>&1

Write-Host ""
Write-Host "Simulation completed."

# Check for log files
$logDir = "$env:APPDATA\Forge\games\gamelogs"
if (Test-Path $logDir) {
    Write-Host ""
    Write-Host "Log files in $logDir :"
    Get-ChildItem $logDir -Filter "*.txt" | Sort-Object LastWriteTime -Descending | Select-Object -First 5 Name, LastWriteTime
}

