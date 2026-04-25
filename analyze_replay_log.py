#!/usr/bin/env python3
"""
Analysiere MTG Replay Logs für Optimierungspotential
"""

import json
import sys
from collections import Counter, defaultdict

def analyze_log(json_file):
    """Analysiere ein Replay-Log auf leere Phasen und Optimierungspotential."""

    with open(json_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    events = data.get('log_l1', [])

    print("=" * 60)
    print("MTG Replay Log - Optimierungs-Analyse")
    print("=" * 60)
    print(f"\nDatei: {json_file}")
    print(f"Total Events: {len(events)}")
    print()

    # Event-Typen zählen
    print("=" * 60)
    print("Event-Typen (häufigste zuerst):")
    print("=" * 60)
    event_types = Counter(e.get('type') for e in events)
    for event_type, count in event_types.most_common():
        pct = (count / len(events)) * 100
        print(f"  {event_type:25s} {count:6d} ({pct:5.1f}%)")

    # Phasen-Analyse
    print("\n" + "=" * 60)
    print("Phasen-Analyse (welche Phasen sind oft leer?):")
    print("=" * 60)

    phases_with_actions = defaultdict(int)
    phases_total = defaultdict(int)
    current_phase = None
    events_in_phase = 0

    for event in events:
        time = event.get('t', '')
        etype = event.get('type', '')

        # Extrahiere Phase (z.B. "T1.UP", "T1.MP1:1" -> "T1.UP", "T1.MP1")
        if '.' in time:
            parts = time.split('.')
            if len(parts) >= 2:
                turn = parts[0]
                phase = parts[1].split(':')[0]  # Remove priority counter
                phase_key = f"{turn}.{phase}"

                # Neue Phase?
                if phase_key != current_phase:
                    if current_phase and events_in_phase > 0:
                        phases_with_actions[current_phase] += 1
                    if current_phase:
                        phases_total[current_phase] += 1

                    current_phase = phase_key
                    events_in_phase = 0

                # Zähle nur "wichtige" Events (keine reinen System-Events)
                important_events = {
                    'CAST', 'ACTIVATE', 'DECLARE_ATTACKERS', 'DECLARE_BLOCKERS',
                    'PLAY_LAND', 'DRAW', 'DAMAGE'
                }
                if etype in important_events:
                    events_in_phase += 1

    # Letzte Phase abschließen
    if current_phase:
        if events_in_phase > 0:
            phases_with_actions[current_phase] += 1
        phases_total[current_phase] += 1

    # Zeige Phasen mit geringer Aktivität
    print("\nPhasen-Typ Aktivität:")
    phase_type_activity = defaultdict(lambda: {'total': 0, 'with_action': 0})

    for phase_key in phases_total:
        # Extrahiere nur Phase-Typ (ohne Turn-Nummer)
        if '.' in phase_key:
            phase_type = phase_key.split('.')[1]
            phase_type_activity[phase_type]['total'] += 1
            if phase_key in phases_with_actions:
                phase_type_activity[phase_type]['with_action'] += 1

    for phase_type in sorted(phase_type_activity.keys()):
        stats = phase_type_activity[phase_type]
        total = stats['total']
        with_action = stats['with_action']
        empty = total - with_action
        empty_pct = (empty / total * 100) if total > 0 else 0

        status = "✅" if empty_pct < 20 else "⚠️" if empty_pct < 50 else "❌"
        print(f"  {status} {phase_type:20s} Total: {total:4d}  Aktiv: {with_action:4d}  Leer: {empty:4d} ({empty_pct:5.1f}%)")

    # Optimierungs-Empfehlungen
    print("\n" + "=" * 60)
    print("🎯 OPTIMIERUNGS-EMPFEHLUNGEN:")
    print("=" * 60)

    recommendations = []

    for phase_type, stats in phase_type_activity.items():
        empty_pct = ((stats['total'] - stats['with_action']) / stats['total'] * 100) if stats['total'] > 0 else 0
        if empty_pct > 70:
            recommendations.append(f"❌ {phase_type:15s} ist {empty_pct:.0f}% der Zeit leer → Kann übersprungen werden!")
        elif empty_pct > 50:
            recommendations.append(f"⚠️  {phase_type:15s} ist {empty_pct:.0f}% der Zeit leer → Optional überspringen")

    if recommendations:
        for rec in recommendations:
            print(f"  {rec}")
    else:
        print("  ✅ Keine offensichtlichen leeren Phasen gefunden.")

    # Event-Sequenz-Muster
    print("\n" + "=" * 60)
    print("🔍 WIEDERHOLENDE MUSTER:")
    print("=" * 60)

    # Finde aufeinanderfolgende gleiche Events
    consecutive_same = []
    prev_type = None
    count = 0

    for event in events:
        etype = event.get('type')
        if etype == prev_type:
            count += 1
        else:
            if count >= 3:
                consecutive_same.append((prev_type, count))
            prev_type = etype
            count = 1

    if consecutive_same:
        print("\n  Aufeinanderfolgende gleiche Events (≥3):")
        for etype, count in sorted(set(consecutive_same), key=lambda x: x[1], reverse=True)[:10]:
            print(f"    {etype:25s} {count}x hintereinander")
        print("\n  💡 Diese könnten zu einem Event zusammengefasst werden!")

    print("\n" + "=" * 60)
    print("📊 ZUSAMMENFASSUNG:")
    print("=" * 60)

    important_event_count = sum(1 for e in events if e.get('type') in {
        'CAST', 'ACTIVATE', 'DECLARE_ATTACKERS', 'DECLARE_BLOCKERS',
        'PLAY_LAND', 'DRAW', 'DAMAGE'
    })

    compression_potential = len(events) - important_event_count
    compression_pct = (compression_potential / len(events) * 100) if len(events) > 0 else 0

    print(f"  Total Events:              {len(events):6d}")
    print(f"  Wichtige Events:           {important_event_count:6d} ({important_event_count/len(events)*100:.1f}%)")
    print(f"  System/Phase Events:       {compression_potential:6d} ({compression_pct:.1f}%)")
    print(f"  Kompressionspotential:     {compression_pct:.0f}%")
    print()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python analyze_replay_log.py <json_file>")
        sys.exit(1)

    analyze_log(sys.argv[1])

