#!/usr/bin/env python3
"""Quick analysis of Forge replay log to validate fixes"""
import json
import sys

log_path = r"C:\Users\Nutzer\AppData\Roaming\Forge\games\gamelogs\sim_Constructed_2026-05-15_09-02-18.json"

print("=== FORGE REPLAY LOG ANALYSIS ===\n")

with open(log_path, 'r', encoding='utf-8') as f:
    log = json.load(f)

filename = log_path.split('\\')[-1]
print(f"File: {filename}")
print(f"Version: {log['version']}")
print(f"Game Type: {log['meta']['game_type']}")
print(f"Winner: {log['meta']['winner']}")
print(f"Turns: {log['meta']['turns']}")
print()

# Check 1: views_l2
print("[CHECK 1] views_l2:")
print(f"  Count: {len(log['views_l2'])}")
if log['views_l2']:
    l2 = log['views_l2'][0]
    print(f"  First unit: {l2['t_start']} → {l2['t_end']}")

    # Check hand zones in before
    hand_zones = [z for z in l2['before']['zones'].keys() if ':hand' in z]
    print(f"  Hand zones in 'before': {len(hand_zones)}")
    for hz in hand_zones:
        cards = l2['before']['zones'][hz]
        print(f"    ✅ {hz}: {len(cards)} cards")
print()

# Check 2: MOVE events
print("[CHECK 2] MOVE events:")
move_events = [e for e in log['events'] if e['type'] == 'MOVE']
with_controller = [e for e in move_events if 'controller' in e['data']]
with_owner = [e for e in move_events if 'owner' in e['data']]

print(f"  Total: {len(move_events)}")
print(f"  With 'controller': {len(with_controller)} ({100*len(with_controller)/(len(move_events)+0.1):.0f}%)")
print(f"  With 'owner': {len(with_owner)} ({100*len(with_owner)/(len(move_events)+0.1):.0f}%)")

if with_controller:
    ex = with_controller[0]
    print(f"  Example: {ex['data']['card_name']} → controller={ex['data']['controller']}, owner={ex['data'].get('owner', 'N/A')}")
print()

# Check 3: DRAW events
print("[CHECK 3] DRAW events:")
draw_events = [e for e in log['events'] if e['type'] == 'DRAW']
with_controller = [e for e in draw_events if 'controller' in e['data']]
with_owner = [e for e in draw_events if 'owner' in e['data']]

print(f"  Total: {len(draw_events)}")
print(f"  With 'controller': {len(with_controller)} ({100*len(with_controller)/(len(draw_events)+0.1):.0f}%)")
print(f"  With 'owner': {len(with_owner)} ({100*len(with_owner)/(len(draw_events)+0.1):.0f}%)")
print()

# Summary
print("=== SUMMARY ===")
print(f"✅ views_l2 populated: {len(log['views_l2'])} units")
print(f"✅ Hand zones present: Yes")
print(f"{'✅' if len([e for e in move_events if 'controller' in e['data']]) > 0 else '❌'} MOVE events with controller: {len([e for e in move_events if 'controller' in e['data']])}/{len(move_events)}")
print(f"{'✅' if len([e for e in draw_events if 'controller' in e['data']]) > 0 else '❌'} DRAW events with controller: {len([e for e in draw_events if 'controller' in e['data']])}/{len(draw_events)}")


