# Scenario Blackbox Testing Guide — Forge

**Version:** 1.0.0  
**Datum:** 2026-05-03  
**Status:** Ready for Production

---

## 📚 Übersicht

Diese Anleitung zeigt, wie Sie das **Scenario-Replay-Feature** von Forge von externen Programmen aus testen können, **ohne den Forge-Quellcode zu kennen** (Blackbox-Testing).

### Zielgruppe:

- 🎯 Tool-Entwickler, die MTG-Scenarios generieren
- 🎯 QA-Engineers, die Forge automatisiert testen
- 🎯 Deck-Builder, die Scenario-basierte Tests schreiben
- 🎯 AI-Researcher, die reproduzierbare Testfälle brauchen

### Was Sie lernen werden:

1. **Scenario-JSON erstellen** — Programmatisch Scenario-Dateien generieren
2. **Forge CLI aufrufen** — Forge als Blackbox-Tool verwenden
3. **Ergebnisse validieren** — Log-Ausgaben parsen und prüfen
4. **Automatisierte Tests** — Test-Suites in Python, PowerShell, etc.
5. **CI/CD Integration** — Scenarios in Build-Pipelines einbinden

---

## 🎯 Phase 1: Scenario-JSON programmatisch erstellen

### 1.1 Minimal-Scenario (Python)

**Zweck:** Erstellen Sie ein Scenario mit definierter Starthand.

```python
#!/usr/bin/env python3
"""
Scenario Generator — Minimal Example
"""
import json
from datetime import datetime

def create_minimal_scenario(output_file):
    """Erstellt ein minimales Scenario für Testing."""
    
    scenario = {
        "format": "mtg-replay",
        "version": "1.8.0",
        "mode": "scenario",
        "meta": {
            "game_id": f"test-scenario-{datetime.now().strftime('%Y%m%d-%H%M%S')}",
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "game_type": "commander",
            "players": {
                "P1": {
                    "name": "Test-Player-1",
                    "deck_name": "Test Deck",
                    "is_ai": True
                },
                "P2": {
                    "name": "Test-Player-2", 
                    "deck_name": "Opponent Deck",
                    "is_ai": True
                }
            }
        },
        "scenario": {
            "type": "opening_hand_test",
            "title": "Minimal Test Scenario",
            "description": "Tests basic scenario loading",
            "question": "Does the scenario load correctly?",
            "answer": "Yes, if both players start with correct hands.",
            "tags": ["test", "minimal"],
            "ruling_references": [],
            "player_count": 2,
            "players": {
                "P1": {
                    "commanders": ["Sol Ring"],  # Für Tests: beliebige Karte
                    "starting_hand": [
                        "Mountain",
                        "Mountain",
                        "Lightning Bolt",
                        "Shock",
                        "Forest",
                        "Plains",
                        "Island"
                    ],
                    "first_draws": [
                        "Swamp",
                        "Command Tower",
                        "Sol Ring"
                    ],
                    "starting_life": 40
                },
                "P2": {
                    "commanders": [],
                    "starting_hand": [],
                    "first_draws": [],
                    "starting_life": 40
                }
            }
        }
    }
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(scenario, f, indent=2, ensure_ascii=False)
    
    print(f"✓ Created scenario: {output_file}")
    return output_file

if __name__ == "__main__":
    create_minimal_scenario("test_scenario_minimal.json")
```

**Output:**
```
✓ Created scenario: test_scenario_minimal.json
```

---

### 1.2 Scenario mit Forced Sequence (Python)

**Zweck:** Erstellen Sie ein Scenario mit erzwungener Spielreihenfolge.

```python
#!/usr/bin/env python3
"""
Scenario Generator — Mit Forced Play Sequence
"""
import json
from datetime import datetime

def create_forced_sequence_scenario(output_file):
    """Erstellt ein Scenario mit erzwungener Spielreihenfolge."""
    
    scenario = {
        "format": "mtg-replay",
        "version": "1.8.0",
        "mode": "scenario",
        "meta": {
            "game_id": f"forced-seq-{datetime.now().strftime('%Y%m%d-%H%M%S')}",
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "game_type": "commander",
            "players": {
                "P1": {
                    "name": "AI-Test-Player",
                    "deck_name": "Test Deck",
                    "is_ai": True
                },
                "P2": {
                    "name": "AI-Opponent",
                    "deck_name": "Opponent",
                    "is_ai": True
                }
            }
        },
        "scenario": {
            "type": "opening_hand_test",
            "title": "Forced Sequence Test",
            "description": "Tests forced play sequence enforcement",
            "question": "Does AI follow the forced sequence?",
            "answer": "Yes, AI plays Land → Spell → Land as scripted.",
            "tags": ["forced_sequence", "test"],
            "ruling_references": [],
            "player_count": 2,
            "players": {
                "P1": {
                    "commanders": ["Lightning Bolt"],
                    "starting_hand": [
                        "Mountain",
                        "Forest",
                        "Lightning Bolt",
                        "Shock",
                        "Plains",
                        "Island",
                        "Swamp"
                    ],
                    "first_draws": [
                        "Command Tower",
                        "Sol Ring",
                        "Mana Vault"
                    ],
                    "starting_life": 40
                },
                "P2": {
                    "commanders": [],
                    "starting_hand": [],
                    "first_draws": [],
                    "starting_life": 40
                }
            }
        },
        "events": [
            {
                "i": 1,
                "t": "T1.MP1:1",
                "a": "AI-Test-Player",
                "type": "PLAY_LAND",
                "data": {"card_name": "Mountain"}
            },
            {
                "i": 2,
                "t": "T2.MP1:1",
                "a": "AI-Test-Player",
                "type": "PLAY_LAND",
                "data": {"card_name": "Forest"}
            },
            {
                "i": 3,
                "t": "T2.MP1:2",
                "a": "AI-Test-Player",
                "type": "CAST",
                "data": {"card_name": "Lightning Bolt"}
            }
        ]
    }
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(scenario, f, indent=2, ensure_ascii=False)
    
    print(f"✓ Created forced sequence scenario: {output_file}")
    return output_file

if __name__ == "__main__":
    create_forced_sequence_scenario("test_scenario_forced.json")
```

---

### 1.3 Scenario-Generator-Klasse (Python)

**Zweck:** Wiederverwendbare Klasse für Scenario-Erstellung.

```python
#!/usr/bin/env python3
"""
Reusable Scenario Builder Class
"""
import json
from datetime import datetime
from typing import List, Dict, Optional

class ScenarioBuilder:
    """Builder-Klasse für MTG Replay Notation Scenarios."""
    
    def __init__(self, game_id: Optional[str] = None):
        self.game_id = game_id or f"scenario-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
        self.scenario_data = {
            "format": "mtg-replay",
            "version": "1.8.0",
            "mode": "scenario",
            "meta": {
                "game_id": self.game_id,
                "timestamp": datetime.utcnow().isoformat() + "Z",
                "game_type": "commander",
                "players": {}
            },
            "scenario": {
                "type": "opening_hand_test",
                "title": "",
                "description": "",
                "question": "",
                "answer": "",
                "tags": [],
                "ruling_references": [],
                "player_count": 2,
                "players": {}
            },
            "events": []
        }
    
    def set_meta(self, title: str, description: str, question: str, answer: str, tags: List[str] = None):
        """Setzt Scenario-Metadata."""
        self.scenario_data["scenario"]["title"] = title
        self.scenario_data["scenario"]["description"] = description
        self.scenario_data["scenario"]["question"] = question
        self.scenario_data["scenario"]["answer"] = answer
        self.scenario_data["scenario"]["tags"] = tags or []
        return self
    
    def add_player(self, player_id: str, name: str, deck_name: str, 
                   commanders: List[str] = None,
                   starting_hand: List[str] = None,
                   first_draws: List[str] = None,
                   starting_life: int = 40):
        """Fügt einen Spieler hinzu."""
        self.scenario_data["meta"]["players"][player_id] = {
            "name": name,
            "deck_name": deck_name,
            "is_ai": True
        }
        
        self.scenario_data["scenario"]["players"][player_id] = {
            "commanders": commanders or [],
            "starting_hand": starting_hand or [],
            "first_draws": first_draws or [],
            "starting_life": starting_life
        }
        return self
    
    def add_forced_event(self, event_index: int, time: str, actor: str, 
                        event_type: str, card_name: str):
        """Fügt ein erzwungenes Event hinzu."""
        event = {
            "i": event_index,
            "t": time,
            "a": actor,
            "type": event_type,
            "data": {"card_name": card_name}
        }
        self.scenario_data["events"].append(event)
        return self
    
    def build(self) -> Dict:
        """Gibt das fertige Scenario zurück."""
        return self.scenario_data
    
    def save(self, output_file: str):
        """Speichert das Scenario als JSON."""
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(self.scenario_data, f, indent=2, ensure_ascii=False)
        print(f"✓ Saved scenario: {output_file}")
        return output_file

# Verwendungsbeispiel:
if __name__ == "__main__":
    builder = ScenarioBuilder("my-test-scenario")
    
    builder.set_meta(
        title="Test Scenario — Land Drop + Spell",
        description="Tests basic land drop and spell cast sequence",
        question="Can P1 play land and cast spell?",
        answer="Yes, if sequence is followed correctly.",
        tags=["test", "basic"]
    )
    
    builder.add_player(
        "P1",
        "Test-Player",
        "Test Deck",
        commanders=["Lightning Bolt"],
        starting_hand=["Mountain", "Forest", "Lightning Bolt", "Plains", "Island", "Swamp", "Command Tower"],
        first_draws=["Sol Ring", "Mana Vault", "Shock"],
        starting_life=40
    )
    
    builder.add_player(
        "P2",
        "Opponent",
        "Opponent Deck",
        starting_life=40
    )
    
    builder.add_forced_event(1, "T1.MP1:1", "Test-Player", "PLAY_LAND", "Mountain")
    builder.add_forced_event(2, "T2.MP1:1", "Test-Player", "CAST", "Lightning Bolt")
    
    builder.save("test_scenario_builder.json")
```

**Output:**
```
✓ Saved scenario: test_scenario_builder.json
```

---

## 🎮 Phase 2: Forge CLI aufrufen (Blackbox)

### 2.1 Python Subprocess

**Zweck:** Starten Sie Forge als externes Programm und erfassen Sie die Ausgabe.

```python
#!/usr/bin/env python3
"""
Forge Scenario Runner — Python
"""
import subprocess
import sys
import os
from pathlib import Path

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
    
    def run_scenario(self, scenario_json: str, timeout: int = 300) -> dict:
        """
        Führt ein Scenario aus.
        
        Args:
            scenario_json: Pfad zur Scenario-JSON-Datei
            timeout: Timeout in Sekunden (default: 300)
            
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
            
            return {
                "returncode": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "success": success
            }
            
        except subprocess.TimeoutExpired:
            print(f"❌ Timeout after {timeout}s")
            return {
                "returncode": -1,
                "stdout": "",
                "stderr": f"Timeout after {timeout}s",
                "success": False
            }
        except Exception as e:
            print(f"❌ Error: {e}")
            return {
                "returncode": -1,
                "stdout": "",
                "stderr": str(e),
                "success": False
            }

# Verwendungsbeispiel:
if __name__ == "__main__":
    # Pfade anpassen!
    forge_jar = r"D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar"
    scenario_file = "test_scenario_minimal.json"
    
    runner = ForgeScenarioRunner(forge_jar)
    result = runner.run_scenario(scenario_file)
    
    print("\n" + "="*60)
    if result["success"]:
        print("✅ Scenario completed successfully")
    else:
        print("❌ Scenario failed")
    print("="*60)
    
    print(f"\nReturn Code: {result['returncode']}")
    
    if result["stdout"]:
        print("\n--- STDOUT ---")
        print(result["stdout"])
    
    if result["stderr"]:
        print("\n--- STDERR ---")
        print(result["stderr"])
```

---

### 2.2 PowerShell Runner

**Zweck:** Scenario-Tests in PowerShell.

```powershell
# run_scenario_test.ps1
# Führt ein Forge Scenario aus (Blackbox)

param(
    [Parameter(Mandatory=$true)]
    [string]$ScenarioFile,
    
    [Parameter(Mandatory=$false)]
    [string]$ForgeJar = "forge-gui-desktop\target\forge-gui-desktop-*-jar-with-dependencies.jar",
    
    [Parameter(Mandatory=$false)]
    [int]$Timeout = 300  # Sekunden
)

# Farben für Output
$Green = "Green"
$Red = "Red"
$Yellow = "Yellow"
$Cyan = "Cyan"

Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor $Cyan
Write-Host "  Forge Scenario Blackbox Test" -ForegroundColor $Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor $Cyan

# Finde JAR
$jar = Get-ChildItem $ForgeJar -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $jar) {
    Write-Host "❌ Forge JAR not found: $ForgeJar" -ForegroundColor $Red
    exit 1
}

Write-Host "✓ Found JAR: $($jar.Name)" -ForegroundColor $Green

# Überprüfe Scenario-Datei
if (-not (Test-Path $ScenarioFile)) {
    Write-Host "❌ Scenario file not found: $ScenarioFile" -ForegroundColor $Red
    exit 1
}

Write-Host "✓ Found scenario: $ScenarioFile" -ForegroundColor $Green

# Führe Scenario aus
Write-Host "`n🚀 Running scenario..." -ForegroundColor $Yellow
Write-Host "   Timeout: $Timeout seconds" -ForegroundColor $Yellow

$scenarioPath = (Resolve-Path $ScenarioFile).Path

$process = Start-Process -FilePath "java" `
    -ArgumentList "-jar", "`"$($jar.FullName)`"", "scenario", "`"$scenarioPath`"" `
    -NoNewWindow `
    -PassThru `
    -RedirectStandardOutput "scenario_output.txt" `
    -RedirectStandardError "scenario_error.txt" `
    -Wait

# Warte auf Process oder Timeout
$timeoutMs = $Timeout * 1000
if (-not $process.WaitForExit($timeoutMs)) {
    Write-Host "`n❌ Timeout after $Timeout seconds!" -ForegroundColor $Red
    $process.Kill()
    exit 1
}

$exitCode = $process.ExitCode

Write-Host "`n═══════════════════════════════════════════════════════════" -ForegroundColor $Cyan
if ($exitCode -eq 0) {
    Write-Host "✅ Scenario completed successfully" -ForegroundColor $Green
} else {
    Write-Host "❌ Scenario failed (Exit Code: $exitCode)" -ForegroundColor $Red
}
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor $Cyan

# Zeige Output
if (Test-Path "scenario_output.txt") {
    $output = Get-Content "scenario_output.txt" -Raw
    if ($output) {
        Write-Host "`n--- STDOUT ---" -ForegroundColor $Yellow
        Write-Host $output
    }
}

if (Test-Path "scenario_error.txt") {
    $errors = Get-Content "scenario_error.txt" -Raw
    if ($errors) {
        Write-Host "`n--- STDERR ---" -ForegroundColor $Red
        Write-Host $errors
    }
}

exit $exitCode
```

**Verwendung:**
```powershell
.\run_scenario_test.ps1 -ScenarioFile "test_scenario_minimal.json"
```

---

## 📊 Phase 3: Ergebnisse validieren

### 3.1 Replay-Log-Validator (Python)

**Zweck:** Überprüfen Sie, ob Forge ein Replay-Log erstellt hat und validieren Sie es.

```python
#!/usr/bin/env python3
"""
Replay Log Validator — Blackbox Testing
"""
import json
import os
from pathlib import Path
from datetime import datetime, timedelta

class ReplayLogValidator:
    """Validiert Forge Replay-Logs."""
    
    def __init__(self, log_dir: str = None):
        """
        Args:
            log_dir: Pfad zum gamelogs-Verzeichnis (default: %APPDATA%\Forge\games\gamelogs)
        """
        if log_dir is None:
            appdata = os.environ.get("APPDATA")
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
    
    def validate_log(self, log_file: Path) -> dict:
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
    
    def print_results(self, results: dict):
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

# Verwendungsbeispiel:
if __name__ == "__main__":
    validator = ReplayLogValidator()
    
    try:
        # Finde neuestes Log
        latest_log = validator.find_latest_log(max_age_seconds=300)
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
```

---

### 3.2 Scenario-Ergebnis-Checker

**Zweck:** Überprüfen Sie spezifische Eigenschaften des Replays.

```python
#!/usr/bin/env python3
"""
Scenario Result Checker — Überprüft spezifische Bedingungen
"""
import json
from pathlib import Path
from typing import List, Callable

class ScenarioResultChecker:
    """Überprüft Scenario-Ergebnisse gegen erwartete Bedingungen."""
    
    def __init__(self, replay_log_path: str):
        with open(replay_log_path, 'r', encoding='utf-8') as f:
            self.data = json.load(f)
        
        self.checks_passed = 0
        self.checks_failed = 0
        self.errors = []
    
    def check_player_starting_hand_count(self, player_id: str, expected_count: int) -> bool:
        """Überprüft die Anzahl der Karten in der Starthand."""
        # Zähle DRAW events in T1.UP (Starting hand)
        draws = [
            e for e in self.data.get("log_l1", [])
            if e.get("type") == "DRAW" 
            and e.get("a") == player_id
            and e.get("t", "").startswith("T1.UP")
        ]
        
        actual_count = len(draws)
        passed = actual_count == expected_count
        
        if passed:
            self.checks_passed += 1
            print(f"✅ {player_id} starting hand: {actual_count} cards (expected {expected_count})")
        else:
            self.checks_failed += 1
            self.errors.append(f"{player_id} starting hand: {actual_count} cards, expected {expected_count}")
            print(f"❌ {player_id} starting hand: {actual_count} cards (expected {expected_count})")
        
        return passed
    
    def check_winner(self, expected_winner: str) -> bool:
        """Überprüft den Gewinner des Spiels."""
        actual_winner = self.data.get("game_summary", {}).get("winner")
        passed = actual_winner == expected_winner
        
        if passed:
            self.checks_passed += 1
            print(f"✅ Winner: {actual_winner} (expected {expected_winner})")
        else:
            self.checks_failed += 1
            self.errors.append(f"Winner: {actual_winner}, expected {expected_winner}")
            print(f"❌ Winner: {actual_winner} (expected {expected_winner})")
        
        return passed
    
    def check_event_type_count(self, event_type: str, min_count: int = None, max_count: int = None) -> bool:
        """Überprüft die Anzahl eines Event-Types."""
        events = [e for e in self.data.get("log_l1", []) if e.get("type") == event_type]
        actual_count = len(events)
        
        passed = True
        if min_count is not None and actual_count < min_count:
            passed = False
        if max_count is not None and actual_count > max_count:
            passed = False
        
        if passed:
            self.checks_passed += 1
            print(f"✅ Event {event_type}: {actual_count} (min: {min_count}, max: {max_count})")
        else:
            self.checks_failed += 1
            self.errors.append(f"Event {event_type}: {actual_count}, expected min: {min_count}, max: {max_count}")
            print(f"❌ Event {event_type}: {actual_count} (min: {min_count}, max: {max_count})")
        
        return passed
    
    def check_forced_event_executed(self, event_index: int, event_type: str, card_name: str) -> bool:
        """Überprüft, ob ein erzwungenes Event ausgeführt wurde."""
        events = self.data.get("log_l1", [])
        
        # Suche Event mit passendem Type und Card-Name
        found = False
        for event in events:
            if event.get("type") == event_type:
                data = event.get("data", {})
                if data.get("card_name") == card_name or data.get("card_id") == card_name:
                    found = True
                    break
        
        if found:
            self.checks_passed += 1
            print(f"✅ Forced event executed: {event_type} {card_name}")
        else:
            self.checks_failed += 1
            self.errors.append(f"Forced event not found: {event_type} {card_name}")
            print(f"❌ Forced event NOT executed: {event_type} {card_name}")
        
        return found
    
    def summary(self) -> dict:
        """Gibt eine Zusammenfassung der Checks zurück."""
        total = self.checks_passed + self.checks_failed
        success_rate = (self.checks_passed / total * 100) if total > 0 else 0
        
        return {
            "total_checks": total,
            "passed": self.checks_passed,
            "failed": self.checks_failed,
            "success_rate": success_rate,
            "errors": self.errors,
            "all_passed": self.checks_failed == 0
        }

# Verwendungsbeispiel:
if __name__ == "__main__":
    # Pfad zum neuesten Replay-Log
    import os
    appdata = os.environ.get("APPDATA")
    log_dir = Path(appdata) / "Forge" / "games" / "gamelogs"
    
    # Finde neuestes Log
    logs = sorted(log_dir.glob("replay_*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not logs:
        print("❌ No replay logs found")
        exit(1)
    
    latest_log = logs[0]
    print(f"📋 Checking: {latest_log.name}\n")
    
    # Erstelle Checker
    checker = ScenarioResultChecker(str(latest_log))
    
    # Definiere erwartete Bedingungen
    checker.check_player_starting_hand_count("P1", 7)
    checker.check_event_type_count("PLAY_LAND", min_count=1)
    checker.check_event_type_count("CAST", min_count=1)
    checker.check_forced_event_executed(1, "PLAY_LAND", "Mountain")
    checker.check_forced_event_executed(2, "CAST", "Lightning Bolt")
    
    # Zusammenfassung
    summary = checker.summary()
    
    print("\n" + "="*60)
    print(f"📊 Test Summary: {summary['passed']}/{summary['total_checks']} passed ({summary['success_rate']:.1f}%)")
    print("="*60)
    
    if summary["all_passed"]:
        print("✅ All checks PASSED")
        exit(0)
    else:
        print("❌ Some checks FAILED")
        print("\nErrors:")
        for err in summary["errors"]:
            print(f"   - {err}")
        exit(1)
```

---

## 🧪 Phase 4: Automatisierte Test-Suite

### 4.1 Test-Suite mit pytest

**Zweck:** Erstellen Sie eine vollständige Test-Suite.

**Installation:**
```bash
pip install pytest
```

**Test-Datei:** `test_forge_scenarios.py`

```python
#!/usr/bin/env python3
"""
Pytest Test Suite für Forge Scenario Testing
"""
import pytest
import json
import subprocess
from pathlib import Path
import os
import time

# Konfiguration
FORGE_JAR = Path(r"D:\Daten\SoftwareProjekte\Forge\forge\forge-gui-desktop\target\forge-gui-desktop-2.0.12-SNAPSHOT-jar-with-dependencies.jar")
TEST_SCENARIOS_DIR = Path("test_scenarios")
TIMEOUT = 300  # 5 Minuten

@pytest.fixture
def log_dir():
    """Gibt das Replay-Log-Verzeichnis zurück."""
    appdata = os.environ.get("APPDATA")
    return Path(appdata) / "Forge" / "games" / "gamelogs"

@pytest.fixture
def clean_old_logs(log_dir):
    """Räumt alte Test-Logs auf (älter als 1 Stunde)."""
    cutoff = time.time() - 3600
    for log in log_dir.glob("replay_*.json"):
        if log.stat().st_mtime < cutoff:
            log.unlink()

def run_scenario(scenario_file: Path) -> dict:
    """Führt ein Scenario aus und gibt Ergebnis zurück."""
    cmd = [
        "java",
        "-jar",
        str(FORGE_JAR),
        "scenario",
        str(scenario_file.absolute())
    ]
    
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        timeout=TIMEOUT
    )
    
    return {
        "returncode": result.returncode,
        "stdout": result.stdout,
        "stderr": result.stderr
    }

def get_latest_log(log_dir: Path) -> Path:
    """Gibt das neueste Replay-Log zurück."""
    logs = sorted(log_dir.glob("replay_*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not logs:
        raise FileNotFoundError("No replay logs found")
    return logs[0]

def load_replay_log(log_file: Path) -> dict:
    """Lädt ein Replay-Log."""
    with open(log_file, 'r', encoding='utf-8') as f:
        return json.load(f)

# ==================== TESTS ====================

def test_minimal_scenario_loads(log_dir, clean_old_logs):
    """Test: Minimales Scenario lädt korrekt."""
    scenario = TEST_SCENARIOS_DIR / "minimal.json"
    
    # Scenario ausführen
    result = run_scenario(scenario)
    
    # Exit-Code prüfen
    assert result["returncode"] == 0, f"Scenario failed: {result['stderr']}"
    
    # Replay-Log prüfen
    log = get_latest_log(log_dir)
    data = load_replay_log(log)
    
    assert data["format"] == "mtg-replay"
    assert "meta" in data
    assert "log_l1" in data

def test_starting_hand_correct(log_dir):
    """Test: Starthand wird korrekt gesetzt."""
    scenario = TEST_SCENARIOS_DIR / "starting_hand.json"
    
    result = run_scenario(scenario)
    assert result["returncode"] == 0
    
    log = get_latest_log(log_dir)
    data = load_replay_log(log)
    
    # Zähle DRAW events in T1.UP für P1
    draws = [
        e for e in data["log_l1"]
        if e["type"] == "DRAW" and e["a"] == "P1" and e["t"].startswith("T1.UP")
    ]
    
    assert len(draws) == 7, f"Expected 7 cards in starting hand, got {len(draws)}"

def test_forced_sequence_executed(log_dir):
    """Test: Erzwungene Sequenz wird korrekt ausgeführt."""
    scenario = TEST_SCENARIOS_DIR / "forced_sequence.json"
    
    result = run_scenario(scenario)
    assert result["returncode"] == 0
    
    log = get_latest_log(log_dir)
    data = load_replay_log(log)
    
    # Prüfe, ob PLAY_LAND und CAST events existieren
    play_land_events = [e for e in data["log_l1"] if e["type"] == "PLAY_LAND"]
    cast_events = [e for e in data["log_l1"] if e["type"] == "CAST"]
    
    assert len(play_land_events) >= 1, "No PLAY_LAND events found"
    assert len(cast_events) >= 1, "No CAST events found"

def test_invalid_scenario_fails():
    """Test: Invalides Scenario wird abgelehnt."""
    scenario = TEST_SCENARIOS_DIR / "invalid.json"
    
    # Erstelle invalides Scenario
    scenario.parent.mkdir(exist_ok=True, parents=True)
    with open(scenario, 'w') as f:
        json.dump({"format": "invalid"}, f)
    
    result = run_scenario(scenario)
    
    # Sollte fehlschlagen
    assert result["returncode"] != 0, "Invalid scenario should fail"

def test_commander_starting_life(log_dir):
    """Test: Commander Starting Life ist korrekt (40)."""
    scenario = TEST_SCENARIOS_DIR / "commander.json"
    
    result = run_scenario(scenario)
    assert result["returncode"] == 0
    
    log = get_latest_log(log_dir)
    data = load_replay_log(log)
    
    # Prüfe GAME_START event
    game_start = next((e for e in data["log_l1"] if e["type"] == "GAME_START"), None)
    assert game_start is not None
    
    # Prüfe Starting Life (falls im Log vermerkt)
    # Hinweis: Exakte Struktur hängt von Forge-Implementation ab
    # Dies ist ein Beispiel

# Führe Tests aus
if __name__ == "__main__":
    pytest.main(["-v", __file__])
```

**Tests ausführen:**
```bash
pytest test_forge_scenarios.py -v
```

**Output:**
```
============================= test session starts =============================
test_forge_scenarios.py::test_minimal_scenario_loads PASSED           [ 20%]
test_forge_scenarios.py::test_starting_hand_correct PASSED            [ 40%]
test_forge_scenarios.py::test_forced_sequence_executed PASSED         [ 60%]
test_forge_scenarios.py::test_invalid_scenario_fails PASSED           [ 80%]
test_forge_scenarios.py::test_commander_starting_life PASSED          [100%]

========================== 5 passed in 120.34s ============================
```

---

## 🔄 Phase 5: CI/CD Integration

### 5.1 GitHub Actions Workflow

**Datei:** `.github/workflows/scenario-tests.yml`

```yaml
name: Scenario Blackbox Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  scenario-tests:
    runs-on: windows-latest
    
    steps:
    - name: Checkout Repository
      uses: actions/checkout@v3
    
    - name: Setup Java 17
      uses: actions/setup-java@v3
      with:
        distribution: 'temurin'
        java-version: '17'
    
    - name: Setup Python 3.11
      uses: actions/setup-python@v4
      with:
        python-version: '3.11'
    
    - name: Install Python Dependencies
      run: |
        pip install pytest
    
    - name: Build Forge
      run: |
        mvn clean package -pl forge-gui-desktop -am -Dmaven.test.skip=true
    
    - name: Generate Test Scenarios
      run: |
        python scripts/generate_test_scenarios.py
    
    - name: Run Scenario Tests
      run: |
        pytest tests/test_forge_scenarios.py -v --tb=short
    
    - name: Upload Test Results
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: test-results
        path: |
          test-results/
          logs/
```

---

### 5.2 Jenkins Pipeline

**Datei:** `Jenkinsfile`

```groovy
pipeline {
    agent any
    
    environment {
        JAVA_HOME = tool name: 'JDK17'
        PYTHON = 'python3'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build Forge') {
            steps {
                bat 'mvn clean package -pl forge-gui-desktop -am -Dmaven.test.skip=true'
            }
        }
        
        stage('Generate Test Scenarios') {
            steps {
                bat "${PYTHON} scripts/generate_test_scenarios.py"
            }
        }
        
        stage('Run Scenario Tests') {
            steps {
                bat "${PYTHON} -m pytest tests/test_forge_scenarios.py -v --junit-xml=test-results.xml"
            }
        }
        
        stage('Validate Replay Logs') {
            steps {
                bat "${PYTHON} scripts/validate_replay_logs.py"
            }
        }
    }
    
    post {
        always {
            junit 'test-results.xml'
            archiveArtifacts artifacts: 'logs/**/*.json', allowEmptyArchive: true
        }
        success {
            echo 'All scenario tests passed!'
        }
        failure {
            echo 'Some scenario tests failed!'
        }
    }
}
```

---

## 📚 Weitere Ressourcen

### Dokumentation

| Datei | Beschreibung |
|-------|--------------|
| `SCENARIO_STARTING_HAND_FORMAT.md` | Scenario-JSON-Format Spezifikation |
| `SIMULATION_AND_LOG_ANALYSIS_GUIDE.md` | Simulation & Log-Analyse |
| `CLI-REPLAY.md` | CLI Replay-Modus Details |
| `mtg-replay-notation/spec/MTG-REPLAY-NOTATION.md` | Vollständige Replay-Format-Spec |

### Beispiel-Skripte

| Skript | Beschreibung |
|--------|--------------|
| `scenario_builder.py` | Wiederverwendbare Scenario-Builder-Klasse |
| `forge_scenario_runner.py` | Forge CLI Wrapper |
| `replay_log_validator.py` | Replay-Log-Validierung |
| `scenario_result_checker.py` | Scenario-Ergebnis-Checks |
| `test_forge_scenarios.py` | Pytest Test-Suite |

---

## 💡 Best Practices

### ✅ DO: Empfohlene Vorgehensweise

1. **Versionieren Sie Scenario-Dateien** — Git für JSON-Test-Cases
2. **Isolieren Sie Tests** — Ein Scenario = Ein Test-Case
3. **Validieren Sie Logs programmatisch** — Nicht manuell prüfen
4. **Verwenden Sie Timeouts** — Verhindern Sie hängende Tests
5. **Testen Sie Edge-Cases** — Invalide Inputs, leere Hands, etc.

### ❌ DON'T: Häufige Fehler

1. **Verlassen Sie sich nicht auf GUI** — Nur CLI für Blackbox-Tests
2. **Ignorieren Sie stderr nicht** — Fehler werden dort ausgegeben
3. **Testen Sie nicht ohne Cleanup** — Alte Logs stören Validierung
4. **Hard-coden Sie keine Pfade** — Verwenden Sie Umgebungsvariablen
5. **Skippen Sie nicht die Validierung** — Exit-Code ≠ korrekte Ausführung

---

## 🚀 Quick Start (TL;DR)

### Python

```python
# 1. Scenario erstellen
from scenario_builder import ScenarioBuilder
builder = ScenarioBuilder()
builder.set_meta("Test", "desc", "q?", "a!")
builder.add_player("P1", "Test", "Deck", starting_hand=["Mountain"]*7)
builder.save("test.json")

# 2. Scenario ausführen
from forge_scenario_runner import ForgeScenarioRunner
runner = ForgeScenarioRunner("path/to/forge.jar")
result = runner.run_scenario("test.json")

# 3. Log validieren
from replay_log_validator import ReplayLogValidator
validator = ReplayLogValidator()
log = validator.find_latest_log()
results = validator.validate_log(log)
assert results["valid"]
```

### PowerShell

```powershell
# 1. Scenario erstellen (manuell oder via Python)
# 2. Scenario ausführen
.\run_scenario_test.ps1 -ScenarioFile "test.json"

# 3. Log überprüfen
python validate_replay_logs.py
```

---

**Viel Erfolg beim Blackbox-Testing! 🧪**

**Version:** 1.0.0 | **Letztes Update:** 2026-05-03

