#!/usr/bin/env python3
"""
Scenario Builder — Reusable Class for Blackbox Testing

Usage:
    from scenario_builder import ScenarioBuilder

    builder = ScenarioBuilder("my-test")
    builder.set_meta("Title", "Description", "Question?", "Answer!")
    builder.add_player("P1", "Player1", "Deck1", starting_hand=["Mountain"]*7)
    builder.save("test_scenario.json")
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

# Beispiel-Verwendung
if __name__ == "__main__":
    builder = ScenarioBuilder("example-test")

    builder.set_meta(
        title="Example Scenario — Basic Test",
        description="Tests basic scenario loading and execution",
        question="Does the scenario load correctly?",
        answer="Yes, if both players start with defined hands.",
        tags=["example", "test", "basic"]
    )

    builder.add_player(
        "P1",
        "Test-Player-1",
        "Test Deck",
        commanders=["Sol Ring"],
        starting_hand=["Mountain", "Forest", "Plains", "Island", "Swamp", "Lightning Bolt", "Shock"],
        first_draws=["Command Tower", "Sol Ring", "Mana Vault"],
        starting_life=40
    )

    builder.add_player(
        "P2",
        "Test-Player-2",
        "Opponent Deck",
        starting_life=40
    )

    builder.add_forced_event(1, "T1.MP1:1", "Test-Player-1", "PLAY_LAND", "Mountain")
    builder.add_forced_event(2, "T2.MP1:1", "Test-Player-1", "CAST", "Lightning Bolt")

    builder.save("example_scenario_generated.json")

