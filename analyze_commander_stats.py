#!/usr/bin/env python3
"""
Commander Simulation Statistics Analyzer

Extrahiert und aggregiert Statistiken aus Commander Replay-Logs
und generiert einen detaillierten JSON-Report.

Usage:
    python analyze_commander_stats.py [log_directory] [output_file]
"""

import json
import sys
import os
from pathlib import Path
from typing import Dict, List, Optional
from datetime import datetime
from collections import defaultdict
import statistics


class GameStats:
    """Statistiken für ein einzelnes Spiel."""

    def __init__(self, log_data: Dict):
        self.data = log_data
        self.meta = log_data.get('meta', {})
        self.game_summary = log_data.get('game_summary', {})
        self.per_turn_summary = log_data.get('per_turn_summary', [])

    def get_game_id(self) -> str:
        return self.meta.get('game_id', 'unknown')

    def get_winner(self) -> Optional[str]:
        return self.game_summary.get('winner')

    def get_total_turns(self) -> int:
        return self.game_summary.get('total_turns', 0)

    def get_duration_seconds(self) -> int:
        return self.game_summary.get('duration_seconds', 0)

    def get_player_stats(self, player_id: str) -> Dict:
        players = self.game_summary.get('players', {})
        return players.get(player_id, {})


class StatsAggregator:
    """Aggregiert Statistiken über mehrere Spiele."""

    def __init__(self):
        self.games: List[GameStats] = []
        self.players = set()

    def add_game(self, game: GameStats):
        self.games.append(game)
        players_in_game = game.game_summary.get('players', {}).keys()
        self.players.update(players_in_game)

    def get_player_metrics(self, player_id: str) -> Dict:
        """Berechne aggregierte Metriken für einen Spieler."""

        # Sammel alle Werte
        wins = 0
        total_games = 0
        turns_list = []
        damage_dealt_list = []
        damage_received_list = []
        cards_drawn_list = []
        spells_cast_list = []
        spell_velocity_list = []
        missed_land_drops_list = []
        peak_mana_list = []
        lands_played_list = []
        creatures_played_list = []
        life_delta_list = []

        for game in self.games:
            stats = game.get_player_stats(player_id)
            if not stats:
                continue

            total_games += 1

            if game.get_winner() == player_id:
                wins += 1

            turns_list.append(game.get_total_turns())

            # Game-wide stats
            damage_dealt_list.append(stats.get('total_damage_dealt', 0))
            damage_received_list.append(stats.get('total_damage_received', 0))
            cards_drawn_list.append(stats.get('total_cards_drawn', 0))
            spells_cast_list.append(stats.get('total_spells_cast', 0))
            spell_velocity_list.append(stats.get('spell_velocity', 0.0))
            missed_land_drops_list.append(stats.get('missed_land_drops', 0))
            peak_mana_list.append(stats.get('peak_mana', 0))
            lands_played_list.append(stats.get('total_lands_played', 0))
            creatures_played_list.append(stats.get('total_creatures_played', 0))
            life_delta_list.append(stats.get('life_delta', 0))

        if total_games == 0:
            return {}

        # Berechne Aggregationen
        def safe_mean(lst): return statistics.mean(lst) if lst else 0
        def safe_median(lst): return statistics.median(lst) if lst else 0
        def safe_stdev(lst): return statistics.stdev(lst) if len(lst) > 1 else 0

        return {
            "total_games": total_games,
            "wins": wins,
            "losses": total_games - wins,
            "win_rate": wins / total_games if total_games > 0 else 0,

            "avg_turns": safe_mean(turns_list),
            "median_turns": safe_median(turns_list),
            "min_turns": min(turns_list) if turns_list else 0,
            "max_turns": max(turns_list) if turns_list else 0,
            "stdev_turns": safe_stdev(turns_list),

            "avg_damage_dealt": safe_mean(damage_dealt_list),
            "avg_damage_received": safe_mean(damage_received_list),
            "avg_cards_drawn": safe_mean(cards_drawn_list),
            "avg_spells_cast": safe_mean(spells_cast_list),

            "avg_spell_velocity": safe_mean(spell_velocity_list),
            "avg_missed_land_drops": safe_mean(missed_land_drops_list),
            "median_peak_mana": safe_median(peak_mana_list),
            "avg_lands_played": safe_mean(lands_played_list),
            "avg_creatures_played": safe_mean(creatures_played_list),
            "avg_life_delta": safe_mean(life_delta_list),

            # Standard deviations (für Konsistenz-Analyse)
            "stdev_damage_dealt": safe_stdev(damage_dealt_list),
            "stdev_spell_velocity": safe_stdev(spell_velocity_list),
            "stdev_missed_land_drops": safe_stdev(missed_land_drops_list)
        }

    def generate_report(self) -> Dict:
        """Generiere vollständigen JSON-Report."""

        report = {
            "format": "commander-simulation-report",
            "version": "1.0.0",
            "meta": {
                "generated_at": datetime.now().isoformat(),
                "total_games": len(self.games),
                "players": list(self.players)
            },
            "aggregate_stats": {},
            "per_game_details": []
        }

        # Aggregierte Statistiken pro Spieler
        for player_id in self.players:
            report["aggregate_stats"][player_id] = self.get_player_metrics(player_id)

        # Per-Game Details
        for game in self.games:
            game_detail = {
                "game_id": game.get_game_id(),
                "winner": game.get_winner(),
                "total_turns": game.get_total_turns(),
                "duration_seconds": game.get_duration_seconds(),
                "players": {}
            }

            for player_id in self.players:
                stats = game.get_player_stats(player_id)
                if stats:
                    game_detail["players"][player_id] = {
                        "total_damage_dealt": stats.get('total_damage_dealt', 0),
                        "total_damage_received": stats.get('total_damage_received', 0),
                        "total_spells_cast": stats.get('total_spells_cast', 0),
                        "spell_velocity": stats.get('spell_velocity', 0.0),
                        "missed_land_drops": stats.get('missed_land_drops', 0),
                        "peak_mana": stats.get('peak_mana', 0),
                        "life_delta": stats.get('life_delta', 0)
                    }

            report["per_game_details"].append(game_detail)

        return report


def find_replay_logs(directory: Path) -> List[Path]:
    """Finde alle Commander Replay-Logs im Verzeichnis."""
    pattern = "replay_Commander_*.json"
    logs = list(directory.glob(pattern))
    return sorted(logs, key=lambda p: p.stat().st_mtime, reverse=True)


def get_gamelog_dir() -> Path:
    """Ermittle Forge Gamelog-Verzeichnis."""
    appdata = os.getenv('APPDATA')
    if appdata:
        return Path(appdata) / 'Forge' / 'games' / 'gamelogs'
    else:
        # Fallback
        home = Path.home()
        return home / '.forge' / 'games' / 'gamelogs'


def main():
    # Parse --limit N before positional args (used by non-interactive callers)
    limit_override: Optional[int] = None
    clean_argv = [sys.argv[0]]
    i = 1
    while i < len(sys.argv):
        if sys.argv[i] == '--limit' and i + 1 < len(sys.argv):
            try:
                limit_override = int(sys.argv[i + 1])
            except ValueError:
                pass
            i += 2
        else:
            clean_argv.append(sys.argv[i])
            i += 1
    sys.argv = clean_argv

    print("=" * 70)
    print("Commander Simulation Statistics Analyzer")
    print("=" * 70)
    print()

    # Log-Verzeichnis
    if len(sys.argv) >= 2:
        log_dir = Path(sys.argv[1])
    else:
        log_dir = get_gamelog_dir()

    if not log_dir.exists():
        print(f"❌ Error: Log directory not found: {log_dir}")
        sys.exit(1)

    print(f"📂 Log directory: {log_dir}")

    # Finde Logs
    logs = find_replay_logs(log_dir)
    if not logs:
        print(f"⚠️  No Commander replay logs found in {log_dir}")
        print(f"   Pattern: replay_Commander_*.json")
        sys.exit(0)

    print(f"✓ Found {len(logs)} replay log(s)")

    # Determine how many recent logs to process
    max_logs = len(logs)
    if limit_override is not None:
        limit = min(limit_override, max_logs)
    elif sys.stdin.isatty():
        print(f"\n💡 Process how many recent logs? (1-{max_logs}, default: 100): ", end="")
        try:
            user_input = input().strip()
            if user_input:
                limit = int(user_input)
                limit = min(limit, max_logs)
            else:
                limit = min(100, max_logs)
        except (ValueError, KeyboardInterrupt):
            limit = min(100, max_logs)
            print()
    else:
        limit = min(100, max_logs)

    logs = logs[:limit]
    print(f"✓ Processing {len(logs)} log(s)...\n")

    # Lade und analysiere Logs
    aggregator = StatsAggregator()
    errors = 0

    for idx, log_path in enumerate(logs, 1):
        try:
            with open(log_path, 'r', encoding='utf-8') as f:
                data = json.load(f)

            game = GameStats(data)
            aggregator.add_game(game)

            if idx % 10 == 0 or idx == len(logs):
                print(f"  Processed {idx}/{len(logs)} logs...", end="\r")

        except Exception as e:
            errors += 1
            if errors <= 5:  # Zeige nur erste 5 Fehler
                print(f"\n⚠️  Error loading {log_path.name}: {e}")

    print()  # Neue Zeile nach Progress

    if errors > 0:
        print(f"\n⚠️  {errors} log(s) could not be loaded (errors shown above)")

    if not aggregator.games:
        print("❌ No valid games loaded. Cannot generate report.")
        sys.exit(1)

    # Generiere Report
    print(f"\n✓ Loaded {len(aggregator.games)} game(s)")
    print(f"✓ Players detected: {', '.join(aggregator.players)}")
    print("\nGenerating report...")

    report = aggregator.generate_report()

    # Output-Datei
    if len(sys.argv) >= 3:
        output_file = Path(sys.argv[2])
    else:
        output_file = Path("commander_simulation_report.json")

    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(f"✅ Report saved: {output_file}")

    # Zusammenfassung anzeigen
    print("\n" + "=" * 70)
    print("📊 Summary Statistics")
    print("=" * 70)

    for player_id in aggregator.players:
        stats = report["aggregate_stats"][player_id]
        print(f"\n🎮 Player: {player_id}")
        print(f"   Win Rate:          {stats['win_rate']:.1%} ({stats['wins']}W/{stats['losses']}L)")
        print(f"   Avg Turns:         {stats['avg_turns']:.1f} (±{stats['stdev_turns']:.1f})")
        print(f"   Avg Damage Dealt:  {stats['avg_damage_dealt']:.1f} (±{stats['stdev_damage_dealt']:.1f})")
        print(f"   Avg Spell Velocity:{stats['avg_spell_velocity']:.2f} spells/turn")
        print(f"   Avg Missed Lands:  {stats['avg_missed_land_drops']:.2f} (±{stats['stdev_missed_land_drops']:.2f})")
        print(f"   Median Peak Mana:  {stats['median_peak_mana']:.0f}")

    print("\n" + "=" * 70)
    print("✅ Analysis complete!")
    print("=" * 70)
    print(f"\n💡 View full report: {output_file}")
    print()


if __name__ == "__main__":
    main()

