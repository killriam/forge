"""
Full validation of ReplayGameStateBuilder logic against a real Forge replay.
Simulates the exact Java logic and cross-validates against per_turn_summary.
"""
import json
import sys

def validate_replay_state(json_path, target_turn):
    """Full validation: reconstruct state at target_turn, then cross-check against per_turn_summary."""

    with open(json_path, 'r', encoding='utf-8') as f:
        root = json.load(f)

    meta = root.get('meta', {})
    game_id = meta.get('game_id', '?')
    print(f"{'='*60}")
    print(f"REPLAY STATE VALIDATION")
    print(f"{'='*60}")
    print(f"Game: {game_id}")
    print(f"Target Turn: {target_turn}")

    # Players
    players = {}
    life_totals = {}
    if 'meta' in root and 'players' in root['meta']:
        for pid, pdata in root['meta']['players'].items():
            players[pid] = pdata.get('name', pid)
            life_totals[pid] = pdata.get('starting_life', 20)

    player_ids = list(players.keys())
    print(f"Players: {len(player_ids)} ({', '.join(f'{k}={v}' for k,v in players.items())})")

    # Initialize cards
    cards = {}
    card_index = root.get('card_index', {})
    objects = root.get('initial_state', {}).get('objects', {})

    for card_id, obj in objects.items():
        name = obj.get('card_ref') or obj.get('cardRef') or card_index.get(card_id, {}).get('name')
        owner = obj.get('owner')
        zone = obj.get('zone')
        if not owner and zone and ':' in zone:
            owner = zone.split(':')[0]
        if name and owner:
            # Normalize zone
            if zone and ':' not in zone:
                zone = f"{owner}:{zone}"
            cards[card_id] = {'name': name, 'owner': owner, 'zone': zone}

    print(f"Initial cards: {len(cards)}")

    # Get events
    events = root.get('events') or root.get('log_l1', [])
    print(f"Total events: {len(events)}")

    # Replay events
    current_turn = 1
    active_player = player_ids[0] if player_ids else None

    for evt in events:
        evt_type = evt.get('type', '')
        data = evt.get('data', {})

        if evt_type == 'ACTIVE_PLAYER_CHANGE':
            new_turn = data.get('turn_number') or data.get('turn')
            if new_turn is None:
                new_turn = current_turn + 1
            if new_turn >= target_turn:
                active_player = data.get('new_player') or data.get('player') or active_player
                current_turn = new_turn
                break
            current_turn = new_turn
            active_player = data.get('new_player') or data.get('player') or active_player

        # Apply zone changes
        if evt_type == 'MOVE':
            card_id = data.get('card') or data.get('obj')
            to_zone = data.get('to')
            if card_id and to_zone:
                if card_id in cards:
                    owner = cards[card_id]['owner']
                    cards[card_id]['zone'] = to_zone if ':' in to_zone else f"{owner}:{to_zone}"
                else:
                    card_name = data.get('card_name')
                    from_zone = data.get('from', '')
                    owner = to_zone.split(':')[0] if ':' in to_zone else (from_zone.split(':')[0] if ':' in from_zone else None)
                    if card_name and owner:
                        nz = to_zone if ':' in to_zone else f"{owner}:{to_zone}"
                        cards[card_id] = {'name': card_name, 'owner': owner, 'zone': nz}

        elif evt_type == 'DRAW':
            card_id = data.get('card') or data.get('obj')
            from_zone = data.get('from', '')
            if card_id and card_id in cards:
                pid = from_zone.split(':')[0] if ':' in from_zone else cards[card_id]['owner']
                cards[card_id]['zone'] = f"{pid}:hand"

        elif evt_type == 'PLAY_LAND':
            card_id = data.get('card') or data.get('obj')
            if card_id and card_id in cards:
                cards[card_id]['zone'] = f"{cards[card_id]['owner']}:battlefield"

        elif evt_type == 'DISCARD':
            card_id = data.get('card') or data.get('obj')
            if card_id and card_id in cards:
                cards[card_id]['zone'] = f"{cards[card_id]['owner']}:graveyard"

        elif evt_type == 'LIFE':
            pid = data.get('player')
            if pid and 'new_total' in data:
                life_totals[pid] = data['new_total']

        elif evt_type == 'CAST':
            # CAST moves card from current zone to stack (no separate MOVE event)
            card_id = data.get('card') or data.get('obj')
            if card_id and card_id in cards:
                cards[card_id]['zone'] = f"{cards[card_id]['owner']}:stack"

        elif evt_type == 'COUNTERS':
            # Heuristic: Time counters = card was exiled with suspend
            counter_type = data.get('counter_type', '')
            delta = data.get('delta', 0)
            if counter_type == 'Time' and delta > 0:
                card_id = data.get('card') or data.get('obj')
                if card_id and card_id in cards:
                    cur_zone = cards[card_id].get('zone', '')
                    if ':exile' not in cur_zone and cur_zone != 'exile':
                        cards[card_id]['zone'] = f"{cards[card_id]['owner']}:exile"

    # Override life from per_turn_summary
    pts = root.get('per_turn_summary', [])
    pts_life = {}
    for t in pts:
        if t.get('turn') == target_turn:
            for pid, pstats in t.get('players', {}).items():
                if 'life' in pstats:
                    pts_life[pid] = pstats['life']
                    life_totals[pid] = pstats['life']
            break

    print(f"\n{'='*60}")
    print(f"RECONSTRUCTED STATE AT TURN {target_turn}")
    print(f"{'='*60}")
    print(f"Active Player: {active_player} ({players.get(active_player, '?')})")

    # Collect zones
    zone_stats = {}
    for i, pid in enumerate(player_ids):
        zone_cards = {}
        for card in cards.values():
            cz = card.get('zone', '')
            if not cz:
                continue
            zpid = cz.split(':')[0] if ':' in cz else card['owner']
            zt = cz.split(':')[1] if ':' in cz else cz
            if zpid == pid and zt != 'stack':
                zone_cards.setdefault(zt, []).append(card['name'])

        zone_stats[pid] = zone_cards
        print(f"\n  p{i} ({players[pid]}): life={life_totals.get(pid, '?')}")
        for zone in ['hand', 'library', 'battlefield', 'graveyard', 'exile', 'command']:
            if zone in zone_cards:
                count = len(zone_cards[zone])
                preview = '; '.join(zone_cards[zone][:5])
                if count > 5:
                    preview += f'; ... ({count} total)'
                print(f"    {zone}: {count} — {preview}")

    # Cross-validate against per_turn_summary
    print(f"\n{'='*60}")
    print(f"CROSS-VALIDATION (per_turn_summary)")
    print(f"{'='*60}")

    all_ok = True
    for t in pts:
        if t.get('turn') != target_turn:
            continue

        for pid, pstats in t.get('players', {}).items():
            expected_life = pstats.get('life')
            expected_hand = pstats.get('cards_in_hand')
            expected_bf = pstats.get('permanents_on_battlefield')

            actual_life = life_totals.get(pid)
            actual_hand = len(zone_stats.get(pid, {}).get('hand', []))
            actual_bf = len(zone_stats.get(pid, {}).get('battlefield', []))

            life_ok = expected_life is None or actual_life == expected_life
            hand_ok = expected_hand is None or actual_hand == expected_hand
            bf_ok = expected_bf is None or actual_bf == expected_bf

            status = "OK" if (life_ok and hand_ok and bf_ok) else "MISMATCH"
            if not (life_ok and hand_ok and bf_ok):
                all_ok = False

            print(f"  {pid} ({players.get(pid, '?')}): [{status}]")
            print(f"    Life:        actual={actual_life}, expected={expected_life} {'✓' if life_ok else '✗ MISMATCH!'}")
            print(f"    Hand:        actual={actual_hand}, expected={expected_hand} {'✓' if hand_ok else '✗ MISMATCH!'}")
            print(f"    Battlefield: actual={actual_bf}, expected={expected_bf} {'✓' if bf_ok else '✗ MISMATCH!'}")
        break

    print(f"\n{'='*60}")
    if all_ok:
        print("RESULT: ALL VALIDATIONS PASSED ✓")
    else:
        print("RESULT: SOME VALIDATIONS FAILED ✗")
    print(f"{'='*60}")

    return all_ok


if __name__ == '__main__':
    if len(sys.argv) >= 3:
        f = sys.argv[1]
        t = int(sys.argv[2])
    else:
        f = 'C:/Users/Nutzer/AppData/Roaming/Forge/games/gamelogs/replay_Constructed_2026-04-03_11-56-54.json'
        t = 20

    ok = validate_replay_state(f, t)
    sys.exit(0 if ok else 1)



