#!/usr/bin/env python3
"""
Forge Scenario Runner — Blackbox Testing Wrapper

Führt Forge Scenario-Tests aus ohne den Forge-Quellcode zu kennen.

Usage:
    runner = ForgeScenarioRunner("path/to/forge.jar")
    result = runner.run_scenario("test_scenario.json")

    if result["success"]:
        print("✅ Scenario passed")
    else:
        print("❌ Scenario failed")
"""
import subprocess
import sys
import os
from pathlib import Path
from typing import Dict

class ForgeScenarioRunner:
    """Führt Forge Scenario-Tests aus (Blackbox)."""

    def __init__(self, forge_jar_path: str):
        """
        Args:
            forge_jar_path: Pfad zur forge-gui-desktop JAR-Datei
        """
        self.forge_jar = Path(forge_jar_path)
        if not self.forge_jar.exists():
            raise FileNotFoundError(f"Forge JAR not found: {self.forge_jar}")

    def run_scenario(self, scenario_json: str, timeout: int = 300, verbose: bool = True) -> Dict:
        """
        Führt ein Scenario aus.

        Args:
            scenario_json: Pfad zur Scenario-JSON-Datei
            timeout: Timeout in Sekunden (default: 300)
            verbose: Ausgabe auf Console (default: True)

        Returns:
            dict mit: {"returncode": int, "stdout": str, "stderr": str, "success": bool}
        """
        scenario_path = Path(scenario_json)
        if not scenario_path.exists():
            raise FileNotFoundError(f"Scenario file not found: {scenario_path}")

        cmd = [
            "java",
            "-jar",
            str(self.forge_jar),
            "scenario",
            str(scenario_path.absolute())
        ]

        if verbose:
            print(f"🚀 Running scenario: {scenario_path.name}")
            print(f"   Command: {' '.join(cmd)}")

        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=timeout
            )

            success = result.returncode == 0

            if verbose:
                if success:
                    print(f"✅ Scenario completed successfully")
                else:
                    print(f"❌ Scenario failed (exit code: {result.returncode})")

            return {
                "returncode": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "success": success
            }

        except subprocess.TimeoutExpired:
            if verbose:
                print(f"❌ Timeout after {timeout}s")
            return {
                "returncode": -1,
                "stdout": "",
                "stderr": f"Timeout after {timeout}s",
                "success": False
            }
        except Exception as e:
            if verbose:
                print(f"❌ Error: {e}")
            return {
                "returncode": -1,
                "stdout": "",
                "stderr": str(e),
                "success": False
            }

# Beispiel-Verwendung
if __name__ == "__main__":
    import sys

    if len(sys.argv) < 3:
        print("Usage: python forge_scenario_runner.py <forge.jar> <scenario.json>")
        sys.exit(1)

    forge_jar = sys.argv[1]
    scenario_file = sys.argv[2]

    runner = ForgeScenarioRunner(forge_jar)
    result = runner.run_scenario(scenario_file)

    print("\n" + "="*60)
    if result["success"]:
        print("✅ Scenario PASSED")
    else:
        print("❌ Scenario FAILED")
    print("="*60)

    print(f"\nReturn Code: {result['returncode']}")

    if result["stdout"]:
        print("\n--- STDOUT ---")
        print(result["stdout"][:500])  # Erste 500 Zeichen

    if result["stderr"]:
        print("\n--- STDERR ---")
        print(result["stderr"][:500])

    sys.exit(0 if result["success"] else 1)

