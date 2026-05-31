#!/usr/bin/env python3
"""
Findings Monitor — Überwacht externe Testing-Team-Findings

Prüft alle 5 Minuten ob sich die Findings-Datei geändert hat und
aktualisiert automatisch die README_BLACKBOX_TESTING.md

Usage:
    python monitor_findings.py

    # Mit Custom-Pfaden:
    python monitor_findings.py \
      --findings "D:\path\to\findings.md" \
      --readme "D:\path\to\README.md"
"""

import os
import sys
import time
import hashlib
import json
from pathlib import Path
from datetime import datetime
from typing import Optional, Dict

# Default-Pfade
DEFAULT_FINDINGS = r"D:\Daten\SoftwareProjekte\MaMo\FORGE_SCENARIO_SIM_FINDING.md"
DEFAULT_README = r"D:\Daten\SoftwareProjekte\Forge\forge\scripts\README_BLACKBOX_TESTING.md"

# Check-Intervall (Sekunden)
CHECK_INTERVAL = 300  # 5 Minuten

class FindingsMonitor:
    """Überwacht Findings-Datei und aktualisiert README bei Änderungen."""

    def __init__(self, findings_path: str, readme_path: str):
        self.findings_path = Path(findings_path)
        self.readme_path = Path(readme_path)
        self.last_hash = None
        self.findings_count = 0

        # Prüfe ob Dateien existieren
        if not self.findings_path.exists():
            raise FileNotFoundError(f"Findings file not found: {self.findings_path}")
        if not self.readme_path.exists():
            raise FileNotFoundError(f"README file not found: {self.readme_path}")

    def get_file_hash(self, file_path: Path) -> str:
        """Berechnet SHA256-Hash einer Datei."""
        sha256 = hashlib.sha256()
        with open(file_path, 'rb') as f:
            for chunk in iter(lambda: f.read(4096), b''):
                sha256.update(chunk)
        return sha256.hexdigest()

    def parse_findings(self) -> Dict:
        """Parst die Findings-Datei und extrahiert wichtige Informationen."""
        with open(self.findings_path, 'r', encoding='utf-8') as f:
            content = f.read()

        findings = {
            "timestamp": datetime.now().isoformat(),
            "file_path": str(self.findings_path),
            "issues": [],
            "summary": "",
            "questions": [],
            "status": "OPEN"
        }

        # Extrahiere Summary
        if "## Summary" in content:
            summary_start = content.find("## Summary") + len("## Summary")
            summary_end = content.find("\n## ", summary_start)
            if summary_end == -1:
                summary_end = len(content)
            findings["summary"] = content[summary_start:summary_end].strip()

        # Extrahiere Questions
        if "## Questions for Forge team" in content:
            questions_start = content.find("## Questions for Forge team")
            questions_end = content.find("\n## ", questions_start + 1)
            if questions_end == -1:
                questions_end = len(content)
            questions_section = content[questions_start:questions_end]

            # Parse numbered questions
            for line in questions_section.split('\n'):
                if line.strip().startswith(('1.', '2.', '3.', '4.', '5.', '6.', '7.', '8.', '9.')):
                    findings["questions"].append(line.strip())

        # Extrahiere Issues/Beobachtungen
        if "Observed mismatches:" in content:
            issues_start = content.find("Observed mismatches:")
            issues_end = content.find("\n## ", issues_start)
            if issues_end == -1:
                issues_end = content.find("\nValidated outcome:", issues_start)
            issues_section = content[issues_start:issues_end]

            for line in issues_section.split('\n'):
                if line.strip().startswith('-'):
                    findings["issues"].append(line.strip()[2:])  # Remove '- '

        return findings

    def update_readme(self, findings: Dict):
        """Aktualisiert die README mit neuen Findings."""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M")

        # Lese aktuelle README
        with open(self.readme_path, 'r', encoding='utf-8') as f:
            readme_content = f.read()

        # Suche nach Agent Updates Section
        agent_section_start = readme_content.find("### Agent Updates")

        if agent_section_start == -1:
            print("[!] Warning: Agent Updates section not found in README")
            return

        # Finde Ende der Agent Updates Section (nächste ### oder End of File)
        agent_section_end = readme_content.find("\n###", agent_section_start + 1)
        if agent_section_end == -1:
            agent_section_end = len(readme_content)

        # Erstelle neuen Entry
        new_entry = f"\n- {timestamp}  Findings updated. Status: {findings['status']}. Issues: {len(findings['issues'])}. Questions: {len(findings['questions'])}."

        # Füge Entry hinzu (vor dem Ende der Section)
        updated_content = (
            readme_content[:agent_section_end] +
            new_entry +
            readme_content[agent_section_end:]
        )

        # Schreibe zurück
        with open(self.readme_path, 'w', encoding='utf-8') as f:
            f.write(updated_content)

        print(f"[+] README updated with new findings entry")

    def check_for_changes(self) -> bool:
        """Prüft ob sich die Findings-Datei geändert hat."""
        current_hash = self.get_file_hash(self.findings_path)

        if self.last_hash is None:
            # Erste Prüfung
            self.last_hash = current_hash
            print(f"[i] Initial hash: {current_hash[:8]}...")
            return False

        if current_hash != self.last_hash:
            print(f"[~] File changed! Old: {self.last_hash[:8]}... New: {current_hash[:8]}...")
            self.last_hash = current_hash
            return True

        return False

    def monitor(self, check_interval: int = CHECK_INTERVAL):
        """Hauptschleife: Überwacht Findings-Datei kontinuierlich."""
        print("="*60)
        print("[*] Forge Findings Monitor")
        print("="*60)
        print(f"Findings: {self.findings_path}")
        print(f"README:   {self.readme_path}")
        print(f"Interval: {check_interval}s ({check_interval/60:.1f} minutes)")
        print("="*60)
        print("\n[>] Starting monitor... (Press Ctrl+C to stop)")

        try:
            iteration = 0
            while True:
                iteration += 1
                timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

                print(f"\n[{timestamp}] Check #{iteration}")

                # Prüfe auf Änderungen
                if self.check_for_changes():
                    print("[!] Change detected! Processing...")

                    # Parse Findings
                    findings = self.parse_findings()
                    self.findings_count += 1

                    print(f"   Issues found: {len(findings['issues'])}")
                    print(f"   Questions found: {len(findings['questions'])}")

                    # Update README
                    self.update_readme(findings)

                    # Zeige Summary
                    if findings['summary']:
                        print(f"\n   Summary: {findings['summary'][:100]}...")

                    print(f"\n[+] Update #{self.findings_count} completed")
                else:
                    print("   No changes detected")

                # Warte bis zum nächsten Check
                print(f"   Next check in {check_interval}s...")
                time.sleep(check_interval)

        except KeyboardInterrupt:
            print("\n\n[X] Monitor stopped by user")
            print(f"   Total updates: {self.findings_count}")
        except Exception as e:
            print(f"\n[ERROR] Error: {e}")
            raise

def main():
    """Main entry point."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Monitor external findings file and update README"
    )
    parser.add_argument(
        '--findings',
        default=DEFAULT_FINDINGS,
        help=f"Path to findings file (default: {DEFAULT_FINDINGS})"
    )
    parser.add_argument(
        '--readme',
        default=DEFAULT_README,
        help=f"Path to README file (default: {DEFAULT_README})"
    )
    parser.add_argument(
        '--interval',
        type=int,
        default=CHECK_INTERVAL,
        help=f"Check interval in seconds (default: {CHECK_INTERVAL})"
    )

    args = parser.parse_args()

    try:
        monitor = FindingsMonitor(args.findings, args.readme)
        monitor.monitor(check_interval=args.interval)
    except FileNotFoundError as e:
        print(f"[ERROR] Error: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"[ERROR] Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()


