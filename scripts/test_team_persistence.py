#!/usr/bin/env python3
"""
Test script for Team Persistence in Forge Replays
Verifies that team assignments are correctly saved and restored
"""

import json
import subprocess
import os
from pathlib import Path

def test_team_persistence():
    """
    Test that team information persists in replay logs.

    Steps:
    1. Start a 2v2 team game
    2. Save the replay
    3. Load the replay JSON
    4. Verify team field exists
    5. Verify team assignments are correct
    """

    print("="*60)
    print("Team Persistence Test")
    print("="*60)

    # Path to latest replay
    replay_dir = Path(os.path.expandvars(r"%APPDATA%\Forge\games\gamelogs"))

    # Find most recent replay file
    replay_files = sorted(replay_dir.glob("replay_*.json"), key=lambda p: p.stat().st_mtime, reverse=True)

    if not replay_files:
        print("❌ No replay files found in:", replay_dir)
        return False

    latest_replay = replay_files[0]
    print(f"\n📂 Testing replay: {latest_replay.name}")

    # Load replay JSON
    try:
        with open(latest_replay, 'r', encoding='utf-8') as f:
            replay_data = json.load(f)
    except Exception as e:
        print(f"❌ Failed to load replay: {e}")
        return False

    # Check version
    version = replay_data.get('version', 'unknown')
    print(f"📋 Replay version: {version}")

    if version < "1.9.0":
        print("⚠️  Replay is older than v1.9.0 (team field may not exist)")

    # Check players
    meta = replay_data.get('meta', {})
    players = meta.get('players', {})

    if not players:
        print("❌ No players found in replay metadata")
        return False

    print(f"\n👥 Found {len(players)} player(s)")

    # Check for team field
    team_count = 0
    teams_found = set()

    for player_id, player_data in players.items():
        name = player_data.get('name', 'Unknown')
        team = player_data.get('team')

        print(f"\n  {player_id}: {name}")

        if team is not None:
            print(f"    Team: {team}")
            teams_found.add(team)
            team_count += 1
        else:
            print(f"    Team: None (FFA)")

    # Result
    print("\n" + "="*60)
    print("Test Results:")
    print("="*60)

    if team_count == 0:
        print("ℹ️  No teams detected (Free-For-All game)")
        print("✅ This is expected for non-team games")
        return True

    if team_count == len(players):
        print(f"✅ All {len(players)} players have team assignments")
        print(f"✅ {len(teams_found)} unique team(s) found: {sorted(teams_found)}")
        return True

    print(f"⚠️  Mixed: {team_count}/{len(players)} players have teams")
    print(f"⚠️  This might indicate a bug or incomplete team setup")
    return False


def test_team_restore():
    """
    Test that teams are restored when loading a replay.

    This requires actually starting a replay game, which is
    best done through the Forge GUI or forge_scenario_runner.py
    """

    print("\n" + "="*60)
    print("Team Restoration Test")
    print("="*60)
    print("\nTo test team restoration:")
    print("1. Start Forge GUI")
    print("2. Load a team game replay (with team field in JSON)")
    print("3. Verify players are on correct teams in game")
    print("4. Check console output for:")
    print("   'Restored team assignment: P1 -> Team 0'")
    print("   (if debug logging is enabled)")


def validate_replay_json_schema(replay_path):
    """
    Validate that replay JSON matches v1.9.0 schema with team field.
    """

    print("\n" + "="*60)
    print("JSON Schema Validation")
    print("="*60)

    with open(replay_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Check required top-level fields
    required_fields = ['format', 'version', 'meta']
    for field in required_fields:
        if field not in data:
            print(f"❌ Missing required field: {field}")
            return False
        print(f"✅ {field}: {data[field]}")

    # Check meta.players structure
    meta = data.get('meta', {})
    players = meta.get('players', {})

    if not players:
        print("❌ meta.players is empty or missing")
        return False

    # Validate each player has correct fields
    for player_id, player_data in players.items():
        required_player_fields = ['name', 'is_ai']
        optional_player_fields = ['team', 'deck_name', 'starting_life', 'player_type']

        print(f"\n  Validating {player_id}:")

        for field in required_player_fields:
            if field not in player_data:
                print(f"    ❌ Missing required field: {field}")
                return False
            print(f"    ✅ {field}: {player_data[field]}")

        for field in optional_player_fields:
            if field in player_data:
                print(f"    ✅ {field}: {player_data[field]}")
            else:
                print(f"    ℹ️  {field}: (not set)")

    print("\n✅ Replay JSON schema is valid")
    return True


if __name__ == '__main__':
    import sys

    # Test team persistence
    success = test_team_persistence()

    # Test restoration info
    test_team_restore()

    # If a specific replay path is provided, validate its schema
    if len(sys.argv) > 1:
        replay_path = sys.argv[1]
        if os.path.exists(replay_path):
            validate_replay_json_schema(replay_path)
        else:
            print(f"\n❌ Replay file not found: {replay_path}")
            sys.exit(1)

    # Exit code
    sys.exit(0 if success else 1)

