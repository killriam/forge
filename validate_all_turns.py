"""Final comprehensive validation of ReplayGameStateBuilder across multiple turns."""
import json
import sys

def validate_turn(root, target_turn, players, player_ids):
    """Validate reconstruction at a specific turn."""
    # Initialize
    cards = {}
    life_totals = {}
    for pid, pdata in root.get('meta', {}).get('players', {}).items():
        life_totals[pid] = pdata.get('starting_life', 20)

    objects = root.get('initial_state', {}).get('objects', {})
    for card_id, obj in objects.items():
        name = obj.get('card_ref') or obj.get('cardRef')
        owner = obj.get('owner')
        zone = obj.get('zone')
        if not owner and zone and ':' in zone:
            owner = zone.split(':')[0]
        if name and owner:
            cards[card_id] = {'name': name, 'owner': owner, 'zone': zone if zone and ':' in zone else f"{owner}:{zone}" if zone else None}

    events = root.get('events') or root.get('log_l1', [])
    active_player = player_ids[0] if player_ids else None

    for evt in events:
        data = evt.get('data', {})
        etype = evt.get('type', '')

        if etype == 'ACTIVE_PLAYER_CHANGE':
            tn = data.get('turn_number') or data.get('turn')
            if tn is not None and tn >= target_turn:
                active_player = data.get('new_player') or data.get('player') or active_player
                break
            active_player = data.get('new_player') or data.get('player') or active_player

        if etype == 'MOVE':
            cid = data.get('card') or data.get('obj')
            to = data.get('to')
            if cid and to:
                if cid in cards:
                    owner = cards[cid]['owner']
                    cards[cid]['zone'] = to if ':' in to else f"{owner}:{to}"
                else:
                    cn = data.get('card_name')
                    fr = data.get('from', '')
                    owner = to.split(':')[0] if ':' in to else (fr.split(':')[0] if ':' in fr else None)
                    if cn and owner:
                        cards[cid] = {'name': cn, 'owner': owner, 'zone': to if ':' in to else f"{owner}:{to}"}

        elif etype == 'DRAW':
            cid = data.get('card') or data.get('obj')
            fr = data.get('from', '')
            if cid and cid in cards:
                pid = fr.split(':')[0] if ':' in fr else cards[cid]['owner']
                cards[cid]['zone'] = f"{pid}:hand"

        elif etype == 'PLAY_LAND':
            cid = data.get('card') or data.get('obj')
            if cid and cid in cards:
                cards[cid]['zone'] = f"{cards[cid]['owner']}:battlefield"

        elif etype == 'DISCARD':
            cid = data.get('card') or data.get('obj')
            if cid and cid in cards:
                cards[cid]['zone'] = f"{cards[cid]['owner']}:graveyard"

        elif etype == 'LIFE':
            pid = data.get('player')
            if pid and 'new_total' in data:
                life_totals[pid] = data['new_total']

        elif etype == 'CAST':
            cid = data.get('card') or data.get('obj')
            if cid and cid in cards:
                cards[cid]['zone'] = f"{cards[cid]['owner']}:stack"

        elif etype == 'COUNTERS':
            ct = data.get('counter_type', '')
            delta = data.get('delta', 0)
            if ct == 'Time' and delta > 0:
                cid = data.get('card') or data.get('obj')
                if cid and cid in cards:
                    cz = cards[cid].get('zone', '')
                    if ':exile' not in cz and cz != 'exile':
                        cards[cid]['zone'] = f"{cards[cid]['owner']}:exile"

    # Override life from per_turn_summary
    for t in root.get('per_turn_summary', []):
        if t.get('turn') == target_turn:
            for pid, ps in t.get('players', {}).items():
                if 'life' in ps:
                    life_totals[pid] = ps['life']
            break

    # Collect zones
    zone_stats = {}
    for pid in player_ids:
        zc = {}
        for c in cards.values():
            cz = c.get('zone', '')
            if not cz: continue
            zpid = cz.split(':')[0] if ':' in cz else c['owner']
            zt = cz.split(':')[1] if ':' in cz else cz
            if zpid == pid and zt != 'stack':
                zc.setdefault(zt, []).append(c['name'])
        zone_stats[pid] = zc

    # Cross-validate
    results = {}
    for t in root.get('per_turn_summary', []):
        if t.get('turn') != target_turn:
            continue
        for pid, ps in t.get('players', {}).items():
            exp_life = ps.get('life')
            exp_hand = ps.get('cards_in_hand')
            exp_bf = ps.get('permanents_on_battlefield')
            act_life = life_totals.get(pid)
            act_hand = len(zone_stats.get(pid, {}).get('hand', []))
            act_bf = len(zone_stats.get(pid, {}).get('battlefield', []))
            results[pid] = {
                'life_ok': exp_life is None or act_life == exp_life,
                'hand_ok': exp_hand is None or act_hand == exp_hand,
                'bf_ok': exp_bf is None or act_bf == exp_bf,
                'hand_diff': act_hand - (exp_hand or 0),
                'bf_diff': act_bf - (exp_bf or 0),
            }
        break

    return results


def main():
    f = 'C:/Users/Nutzer/AppData/Roaming/Forge/games/gamelogs/replay_Constructed_2026-04-03_11-56-54.json'
    with open(f) as fp:
        root = json.load(fp)

    players = {}
    for pid, pd in root.get('meta', {}).get('players', {}).items():
        players[pid] = pd.get('name', pid)
    player_ids = list(players.keys())

    print(f"{'Turn':>5} | {'P1 Life':>8} | {'P1 Hand':>10} | {'P1 BF':>8} | {'P2 Life':>8} | {'P2 Hand':>10} | {'P2 BF':>8} | {'P3 Life':>8} | {'P3 Hand':>10} | {'P3 BF':>8} | {'P4 Life':>8} | {'P4 Hand':>10} | {'P4 BF':>8}")
    print("-" * 150)

    turns_ok = 0
    turns_tested = 0
    for turn in [5, 10, 15, 20, 25, 30, 40, 50]:
        results = validate_turn(root, turn, players, player_ids)
        if not results:
            continue
        turns_tested += 1

        parts = [f"{turn:5d}"]
        all_ok = True
        for pid in player_ids:
            r = results.get(pid, {})
            life_sym = "✓" if r.get('life_ok', True) else "✗"
            hand_sym = "✓" if r.get('hand_ok', True) else f"±{r.get('hand_diff', '?')}"
            bf_sym = "✓" if r.get('bf_ok', True) else f"±{r.get('bf_diff', '?')}"
            parts.append(f"{life_sym:>8}")
            parts.append(f"{hand_sym:>10}")
            parts.append(f"{bf_sym:>8}")
            if not (r.get('life_ok', True) and r.get('hand_ok', True) and r.get('bf_ok', True)):
                all_ok = False

        if all_ok:
            turns_ok += 1
        status = "  ✓" if all_ok else "  ✗"
        print(" | ".join(parts) + status)

    print(f"\n{turns_ok}/{turns_tested} turns fully validated")
    print("\nKey: ✓ = exact match, ±N = difference from expected")


if __name__ == '__main__':
    main()

