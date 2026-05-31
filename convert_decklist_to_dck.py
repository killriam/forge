#!/usr/bin/env python3
"""
Commander Decklist JSON to Forge .dck Converter

Konvertiert Commander Decklist JSON (v1.0.0) zu Forge's .dck-Format
für AI-Simulationen.

Usage:
    python convert_decklist_to_dck.py input.json [output_dir]
"""

import json
import sys
import os
from pathlib import Path
from typing import Dict, List, Optional
import hashlib


def load_decklist(json_path: str) -> Dict:
    """Lade Commander Decklist JSON."""
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Validierung
    if data.get('format') != 'mtg-commander-decklist':
        raise ValueError(f"Invalid format: {data.get('format')} (expected: mtg-commander-decklist)")

    if data.get('version') != '1.0.0':
        print(f"⚠️  Warning: Decklist version {data.get('version')} may not be fully supported")

    return data


def validate_decklist(data: Dict) -> None:
    """Validiere Commander Decklist gegen Requirements."""
    errors = []

    # Meta validation
    meta = data.get('meta', {})
    if not meta.get('deck_name'):
        errors.append("Missing required field: meta.deck_name")
    if meta.get('format') != 'Commander':
        errors.append(f"Invalid format: {meta.get('format')} (must be 'Commander')")

    # Commander validation
    commander = data.get('commander', [])
    if not commander:
        errors.append("Commander section is empty (must contain at least 1 card)")

    # Main deck count
    main = data.get('main', [])
    total_main = sum(card.get('quantity', 0) for card in main)

    # Check for companion in commander section
    has_companion = any('companion' in card.get('additional_mechanics', [])
                       for card in commander if len(commander) > 1)

    expected_main_count = 98 if has_companion else 99
    if total_main != expected_main_count:
        errors.append(f"Main deck has {total_main} cards (expected {expected_main_count})")

    # Card validation
    for section_name, cards in [('commander', commander), ('main', main)]:
        for idx, card in enumerate(cards):
            prefix = f"{section_name}[{idx}]"
            if not card.get('name'):
                errors.append(f"{prefix}: Missing 'name'")
            if not card.get('edition'):
                errors.append(f"{prefix}: Missing 'edition' for '{card.get('name')}'")
            if not card.get('collector_number'):
                errors.append(f"{prefix}: Missing 'collector_number' for '{card.get('name')}'")
            if not card.get('primary_mechanic'):
                errors.append(f"{prefix}: Missing 'primary_mechanic' for '{card.get('name')}'")

    if errors:
        print("❌ Validation Errors:")
        for error in errors:
            print(f"   • {error}")
        raise ValueError(f"Decklist validation failed with {len(errors)} error(s)")

    print("✅ Decklist validation passed")


def compute_deck_hash(commander: List[Dict], main: List[Dict]) -> str:
    """
    Berechne Deck-Hash nach Commander Decklist Spec §7.
    SHA-256 der sortierten "CardName:Quantity" Strings.
    """
    lines = []

    for card in commander:
        lines.append(f"{card['name']}:{card.get('quantity', 1)}")

    for card in main:
        lines.append(f"{card['name']}:{card.get('quantity', 1)}")

    lines.sort()
    content = '\n'.join(lines)
    hash_full = hashlib.sha256(content.encode('utf-8')).hexdigest()
    return hash_full[:16]  # Erste 16 Zeichen


def convert_to_dck(data: Dict, output_path: str) -> None:
    """
    Konvertiere Commander Decklist JSON zu Forge .dck-Format.

    Forge .dck Format:
    [metadata]
    [Commander]
    1 Card Name|SET
    [Main]
    1 Card Name|SET
    ...
    """
    meta = data.get('meta', {})
    commander = data.get('commander', [])
    main = data.get('main', [])

    lines = []

    # Metadata section
    lines.append("[metadata]")
    lines.append(f"Name={meta.get('deck_name', 'Unnamed Deck')}")

    if meta.get('deck_id'):
        lines.append(f"DeckID={meta['deck_id']}")

    # Deck hash
    deck_hash = compute_deck_hash(commander, main)
    lines.append(f"DeckHash={deck_hash}")

    if meta.get('description'):
        lines.append(f"Description={meta['description']}")

    lines.append("")

    # Commander section
    lines.append("[Commander]")
    for card in commander:
        quantity = card.get('quantity', 1)
        name = card['name']
        edition = card['edition'].upper()
        lines.append(f"{quantity} {name}|{edition}")

    lines.append("")

    # Main deck section
    lines.append("[Main]")
    for card in main:
        quantity = card.get('quantity', 1)
        name = card['name']
        edition = card['edition'].upper()
        lines.append(f"{quantity} {name}|{edition}")

    lines.append("")

    # Sideboard (optional, meist leer in Commander)
    if data.get('sideboard'):
        lines.append("[Sideboard]")
        for card in data['sideboard']:
            quantity = card.get('quantity', 1)
            name = card['name']
            edition = card['edition'].upper()
            lines.append(f"{quantity} {name}|{edition}")
        lines.append("")

    # Schreibe .dck-Datei
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))

    print(f"✅ .dck file created: {output_path}")
    print(f"   Deck Hash: {deck_hash}")
    print(f"   Commander: {len(commander)} card(s)")
    print(f"   Main: {sum(c.get('quantity', 1) for c in main)} card(s)")


def get_forge_commander_deck_dir() -> Path:
    """Ermittle Forge Commander Deck-Verzeichnis."""
    appdata = os.getenv('APPDATA')
    if appdata:
        return Path(appdata) / 'Forge' / 'decks' / 'commander'
    else:
        # Fallback für Unix-Systeme
        home = Path.home()
        return home / '.forge' / 'decks' / 'commander'


def main():
    if len(sys.argv) < 2:
        print("Usage: python convert_decklist_to_dck.py <input.json> [output_dir]")
        print()
        print("Example:")
        print("  python convert_decklist_to_dck.py my_deck.json")
        print("  python convert_decklist_to_dck.py my_deck.json D:/output")
        sys.exit(1)

    input_file = sys.argv[1]

    if not os.path.exists(input_file):
        print(f"❌ Error: Input file not found: {input_file}")
        sys.exit(1)

    print("=" * 60)
    print("Commander Decklist JSON → Forge .dck Converter")
    print("=" * 60)
    print(f"\n📂 Input: {input_file}")

    # Lade und validiere Decklist
    try:
        data = load_decklist(input_file)
        validate_decklist(data)
    except Exception as e:
        print(f"\n❌ Error: {e}")
        sys.exit(1)

    # Output-Verzeichnis bestimmen
    if len(sys.argv) >= 3:
        output_dir = Path(sys.argv[2])
    else:
        output_dir = get_forge_commander_deck_dir()

    output_dir.mkdir(parents=True, exist_ok=True)

    # Output-Dateiname
    deck_name = data['meta']['deck_name']
    safe_name = "".join(c if c.isalnum() or c in (' ', '_', '-') else '_' for c in deck_name)
    output_file = output_dir / f"{safe_name}.dck"

    print(f"📂 Output: {output_file}")
    print()

    # Konvertierung
    try:
        convert_to_dck(data, str(output_file))
    except Exception as e:
        print(f"\n❌ Conversion failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

    print("\n" + "=" * 60)
    print("✅ Conversion successful!")
    print("=" * 60)
    print(f"\n💡 Next step: Run simulation with this deck:")
    print(f"   java -jar forge.jar sim -d \"{safe_name}.dck\" -d \"opponent.dck\" -n 100 -f commander -q")
    print()


if __name__ == "__main__":
    main()

