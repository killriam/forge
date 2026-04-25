#!/usr/bin/env python3
"""
Test script for ReplayGameStateBuilder validation.
Simulates the zone tracking logic from the Java implementation
against the example replay JSON to verify correctness.
"""
import json
import sys
import os

def test_replay_state_builder(json_path, target_turn):
    """Simulate the ReplayGameStateBuilder logic in Python for validation."""

    with open(json_path, 'r', encoding='utf-8') as f:
        root = json.load(f)

    # Extract player info
    players = {}
    life_totals = {}
    if 'meta' in root and 'players' in root['meta']:
        for pid, pdata in root['meta']['players'].items():
            players[pid] = pdata.get('name', pid)
            life_totals[pid] = pdata.get('starting_life', 20)

    player_ids = list(players.keys())
    print(f"Players: {players}")
    print(f"Starting life: {life_totals}")

    # Initialize cards from initial_state.objects
    cards = {}  # cardId -> {name, owner, zone}
    card_index = root.get('card_index', {})

    initial_state = root.get('initial_state', {})
    objects = initial_state.get('objects', {})

    for card_id, obj in objects.items():
        name = obj.get('card_ref') or obj.get('cardRef') or card_index.get(card_id, {}).get('name')
        owner = obj.get('owner')
        zone = obj.get('zone')
        if not owner and zone and ':' in zone:
            owner = zone.split(':')[0]
        if name and owner:
            cards[card_id] = {'name': name, 'owner': owner, 'zone': zone}

    print(f"\nInitialized {len(cards)} cards from initial_state.objects")

    # Get events
    events = root.get('events') or root.get('log_l1', [])
    print(f"Total events: {len(events)}")

    # Replay events up to target turn
    current_turn = 1
    active_player = player_ids[0] if player_ids else None

    for evt in events:
        evt_type = evt.get('type', '')
        data = evt.get('data', {})

        # Check for turn boundary
        if evt_type == 'ACTIVE_PLAYER_CHANGE':
            new_turn = data.get('turn_number') or data.get('turn', current_turn + 1)
            if new_turn >= target_turn:
                active_player = data.get('new_player') or data.get('player') or active_player
                current_turn = new_turn
                print(f"\n>>> Reached target turn {target_turn}, stopping event replay")
                break
            current_turn = new_turn
            active_player = data.get('new_player') or data.get('player') or active_player

        # Apply zone changes
        if evt_type == 'MOVE':
            card_id = data.get('card') or data.get('obj')
            to_zone = data.get('to')
            card_name = data.get('card_name')
            if card_id and to_zone:
                if card_id in cards:
                    old_zone = cards[card_id]['zone']
                    cards[card_id]['zone'] = to_zone
                    # print(f"  MOVE {card_name or card_id}: {old_zone} -> {to_zone}")
                elif card_name:
                    from_zone = data.get('from', '')
                    owner = to_zone.split(':')[0] if ':' in to_zone else (from_zone.split(':')[0] if ':' in from_zone else None)
                    if owner:
                        cards[card_id] = {'name': card_name, 'owner': owner, 'zone': to_zone}

        elif evt_type == 'DRAW':
            card_id = data.get('card') or data.get('obj')
            from_zone = data.get('from', '')
            if card_id and card_id in cards:
                pid = from_zone.split(':')[0] if ':' in from_zone else cards[card_id]['owner']
                cards[card_id]['zone'] = f"{pid}:hand"

        elif evt_type == 'DISCARD':
            card_id = data.get('card') or data.get('obj')
            if card_id and card_id in cards:
                cards[card_id]['zone'] = f"{cards[card_id]['owner']}:graveyard"

        elif evt_type == 'DAMAGE':
            target = data.get('target')
            amount = data.get('amount', 0)
            if target and target in life_totals:
                life_totals[target] -= amount

        elif evt_type == 'LIFE':
            pid = data.get('player')
            if pid:
                if 'new_total' in data:
                    life_totals[pid] = data['new_total']
                elif 'life' in data:
                    life_totals[pid] = data['life']

    # Collect cards by player and zone
    print(f"\n=== Game State at Turn {target_turn} ===")
    print(f"Active Player: {active_player} ({players.get(active_player, '?')})")
    print(f"Life Totals: {life_totals}")

    # Group by player index + zone type
    for i, pid in enumerate(player_ids):
        prefix = f"p{i}"
        print(f"\n--- {prefix} ({players[pid]}) ---")
        print(f"  Life: {life_totals.get(pid, 20)}")

        zone_cards = {}
        for card in cards.values():
            card_zone = card.get('zone', '')
            card_owner = card_zone.split(':')[0] if ':' in card_zone else card.get('owner')
            zone_type = card_zone.split(':')[1] if ':' in card_zone else card_zone

            if card_owner == pid:
                zone_cards.setdefault(zone_type, []).append(card['name'])

        for zone_type in ['hand', 'library', 'battlefield', 'graveyard', 'exile', 'command']:
            if zone_type in zone_cards:
                card_list = zone_cards[zone_type]
                print(f"  {zone_type}: {len(card_list)} cards — {'; '.join(card_list[:10])}")
                if len(card_list) > 10:
                    print(f"    ... and {len(card_list) - 10} more")

    # Generate GameState lines
    print(f"\n=== Generated GameState Lines ===")
    lines = []
    lines.append(f"Turn={target_turn}")
    lines.append("RemoveSummoningSickness=true")

    active_idx = player_ids.index(active_player) if active_player in player_ids else 0
    lines.append(f"ActivePlayer=p{active_idx}")
    lines.append("ActivePhase=MAIN1")

    for i, pid in enumerate(player_ids):
        prefix = f"p{i}"
        lines.append(f"{prefix}Life={life_totals.get(pid, 20)}")

        zone_cards = {}
        for card in cards.values():
            card_zone = card.get('zone', '')
            card_owner = card_zone.split(':')[0] if ':' in card_zone else card.get('owner')
            zone_type = card_zone.split(':')[1] if ':' in card_zone else card_zone
            if card_owner == pid:
                zone_cards.setdefault(zone_type, []).append(card['name'])

        for zone, gstate_zone in [('hand', 'Hand'), ('library', 'Library'), ('battlefield', 'Battlefield'),
                                    ('graveyard', 'Graveyard'), ('exile', 'Exile'), ('command', 'Command')]:
            if zone in zone_cards:
                lines.append(f"{prefix}{gstate_zone}={';'.join(zone_cards[zone])}")

    for line in lines:
        print(f"  {line}")

    print(f"\nTotal GameState lines: {len(lines)}")
    return lines


if __name__ == '__main__':
    # Test with the example replay file
    example_path = os.path.join(os.path.dirname(__file__),
                                'mtg-replay-notation', 'examples', 'simple-game.json')

    if len(sys.argv) >= 3:
        test_replay_state_builder(sys.argv[1], int(sys.argv[2]))
    elif os.path.exists(example_path):
        print("Testing with simple-game.json at Turn 3...\n")
        test_replay_state_builder(example_path, 3)
        print("\n" + "="*60 + "\n")
        print("Testing with simple-game.json at Turn 1...\n")
        test_replay_state_builder(example_path, 1)
    else:
        print(f"Example file not found at: {example_path}")
        print("Usage: python test_replay_state_builder.py <replay.json> <turnNumber>")

