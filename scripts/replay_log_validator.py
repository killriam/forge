#!/usr/bin/env python3
"""
Replay Log Validator — Blackbox Testing

Validiert Forge Replay-Logs programmatisch.

Usage:
    validator = ReplayLogValidator()
    latest_log = validator.find_latest_log()
    results = validator.validate_log(latest_log)

    if results["valid"]:
        print("✅ Log is valid")
    else:
        print("❌ Log has errors")
"""
import json
import os
from pathlib import Path
from datetime import datetime
from typing import Dict

class ReplayLogValidator:
    """Validiert Forge Replay-Logs."""

    def __init__(self, log_dir: str = None):
        """
        Args:
            log_dir: Pfad zum gamelogs-Verzeichnis (default: %APPDATA%\Forge\games\gamelogs)
        """
        if log_dir is None:
            appdata = os.environ.get("APPDATA")
            if not appdata:
                raise EnvironmentError("APPDATA environment variable not set")
            log_dir = Path(appdata) / "Forge" / "games" / "gamelogs"

        self.log_dir = Path(log_dir)
        if not self.log_dir.exists():
            raise FileNotFoundError(f"Log directory not found: {self.log_dir}")

    def find_latest_log(self, max_age_seconds: int = 300) -> Path:
        """
        Findet das neueste Replay-Log.

        Args:
            max_age_seconds: Maximales Alter in Sekunden (default: 300 = 5 Minuten)

        Returns:
            Path zum neuesten Log
        """
        logs = sorted(
            self.log_dir.glob("replay_*.json"),
            key=lambda p: p.stat().st_mtime,
            reverse=True
        )

        if not logs:
            raise FileNotFoundError(f"No replay logs found in {self.log_dir}")

        latest = logs[0]
        age = datetime.now().timestamp() - latest.stat().st_mtime

        if age > max_age_seconds:
            raise ValueError(f"Latest log is too old ({age:.0f}s > {max_age_seconds}s)")

        return latest

    def validate_log(self, log_file: Path) -> Dict:
        """
        Validiert ein Replay-Log.

        Returns:
            dict mit Validierungsergebnissen
        """
        print(f"📋 Validating: {log_file.name}")

        results = {
            "valid": False,
            "errors": [],
            "warnings": [],
            "stats": {}
        }

        try:
            with open(log_file, 'r', encoding='utf-8') as f:
                data = json.load(f)

            # 1. Format-Check
            if data.get("format") != "mtg-replay":
                results["errors"].append("Invalid format field")

            # 2. Version-Check
            if "version" not in data:
                results["errors"].append("Missing version field")

            # 3. Meta-Check
            if "meta" not in data:
                results["errors"].append("Missing meta section")
            else:
                if "game_id" not in data["meta"]:
                    results["errors"].append("Missing game_id in meta")
                if "players" not in data["meta"]:
                    results["errors"].append("Missing players in meta")

            # 4. Events-Check (L1 Log)
            if "log_l1" in data:
                events = data["log_l1"]
                results["stats"]["total_events"] = len(events)

                # Event-Types zählen
                event_types = {}
                for event in events:
                    etype = event.get("type", "UNKNOWN")
                    event_types[etype] = event_types.get(etype, 0) + 1

                results["stats"]["event_types"] = event_types
            else:
                results["warnings"].append("No log_l1 events found")

            # 5. Game Summary Check
            if "game_summary" in data:
                summary = data["game_summary"]
                results["stats"]["winner"] = summary.get("winner")
                results["stats"]["total_turns"] = summary.get("total_turns")
                results["stats"]["duration_seconds"] = summary.get("duration_seconds")
            else:
                results["warnings"].append("No game_summary found")

            # Wenn keine Errors: valid
            results["valid"] = len(results["errors"]) == 0

        except json.JSONDecodeError as e:
            results["errors"].append(f"Invalid JSON: {e}")
        except Exception as e:
            results["errors"].append(f"Validation error: {e}")

        return results

    def print_results(self, results: Dict):
        """Gibt Validierungsergebnisse aus."""
        print("\n" + "="*60)
        if results["valid"]:
            print("✅ Replay log is VALID")
        else:
            print("❌ Replay log is INVALID")
        print("="*60)

        if results["errors"]:
            print("\n❌ ERRORS:")
            for err in results["errors"]:
                print(f"   - {err}")

        if results["warnings"]:
            print("\n⚠️  WARNINGS:")
            for warn in results["warnings"]:
                print(f"   - {warn}")

        if results["stats"]:
            print("\n📊 STATISTICS:")
            for key, value in results["stats"].items():
                if isinstance(value, dict):
                    print(f"   {key}:")
                    for k, v in value.items():
                        print(f"      {k}: {v}")
                else:
                    print(f"   {key}: {value}")

# Beispiel-Verwendung
if __name__ == "__main__":
    validator = ReplayLogValidator()

    try:
        # Finde neuestes Log
        latest_log = validator.find_latest_log(max_age_seconds=600)
        print(f"✓ Found latest log: {latest_log.name}")

        # Validiere
        results = validator.validate_log(latest_log)

        # Zeige Ergebnisse
        validator.print_results(results)

        # Exit Code basierend auf Validierung
        exit(0 if results["valid"] else 1)

    except Exception as e:
        print(f"❌ Error: {e}")
        exit(1)

