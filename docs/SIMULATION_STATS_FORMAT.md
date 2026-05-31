# Forge Simulation Stats JSON Schema

**Version:** 2.0.0  
**Format:** `forge-simulation-stats`  
**Purpose:** Lightweight statistics format for AI simulation batch analysis

---

## JSON Schema Definition

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "https://forge.com/schemas/simulation-stats-2.0.0.json",
  "title": "Forge Simulation Stats",
  "description": "Reduced statistics format for batch AI simulations",
  "type": "object",
  "required": ["format", "version", "meta", "outcome", "players"],
  "properties": {
    "format": {
      "type": "string",
      "const": "forge-simulation-stats",
      "description": "Format identifier"
    },
    "version": {
      "type": "string",
      "pattern": "^2\\.\\d+\\.\\d+$",
      "description": "Schema version (semver)"
    },
    "meta": {
      "type": "object",
      "required": ["timestamp", "game_type"],
      "properties": {
        "timestamp": {
          "type": "string",
          "format": "date-time",
          "description": "ISO 8601 timestamp"
        },
        "simulation_id": {
          "type": "string",
          "description": "Unique simulation identifier"
        },
        "game_type": {
          "type": "string",
          "enum": ["Constructed", "Commander", "Oathbreaker", "Brawl", "TinyLeaders"],
          "description": "Game format"
        },
        "deck1_name": {
          "type": "string",
          "description": "Player 1 deck name"
        },
        "deck2_name": {
          "type": "string",
          "description": "Player 2 deck name"
        },
        "deck1_hash": {
          "type": "string",
          "pattern": "^[a-f0-9]{16}$",
          "description": "Deck hash (16 hex chars)"
        },
        "deck2_hash": {
          "type": "string",
          "pattern": "^[a-f0-9]{16}$",
          "description": "Deck hash (16 hex chars)"
        }
      }
    },
    "outcome": {
      "type": "object",
      "required": ["winner", "total_turns", "duration_ms"],
      "properties": {
        "winner": {
          "type": ["string", "null"],
          "description": "Player ID (P1, P2) or null for draw"
        },
        "win_condition": {
          "type": "string",
          "enum": ["damage", "mill", "combo", "concede", "timeout", "draw"],
          "description": "How the game was won"
        },
        "total_turns": {
          "type": "integer",
          "minimum": 0,
          "description": "Total number of turns"
        },
        "duration_ms": {
          "type": "integer",
          "minimum": 0,
          "description": "Game duration in milliseconds"
        },
        "game_ended_reason": {
          "type": "string",
          "description": "Technical reason (PLAYER_LOST_GAME, DRAW, etc.)"
        }
      }
    },
    "players": {
      "type": "object",
      "patternProperties": {
        "^P\\d+$": {
          "$ref": "#/definitions/PlayerStats"
        }
      },
      "description": "Per-player statistics"
    },
    "timeline": {
      "type": "object",
      "description": "Optional turn-by-turn timeline data",
      "properties": {
        "turn_count": {
          "type": "array",
          "items": {"type": "integer"},
          "description": "Turn numbers [1,2,3,...]"
        },
        "P1_life": {
          "type": "array",
          "items": {"type": "integer"},
          "description": "P1 life total at end of each turn"
        },
        "P2_life": {
          "type": "array",
          "items": {"type": "integer"},
          "description": "P2 life total at end of each turn"
        },
        "P1_creatures": {
          "type": "array",
          "items": {"type": "integer"},
          "description": "P1 creature count at end of each turn"
        },
        "P2_creatures": {
          "type": "array",
          "items": {"type": "integer"},
          "description": "P2 creature count at end of each turn"
        }
      }
    }
  },
  "definitions": {
    "PlayerStats": {
      "type": "object",
      "required": ["deck_name", "final_life", "cards", "spells", "mana", "combat", "board"],
      "properties": {
        "deck_name": {
          "type": "string",
          "description": "Deck name for this player"
        },
        "final_life": {
          "type": "integer",
          "description": "Life total at game end"
        },
        "life_delta": {
          "type": "integer",
          "description": "Change from starting life (final - starting)"
        },
        "cards": {
          "type": "object",
          "required": ["drawn"],
          "properties": {
            "drawn": {
              "type": "integer",
              "minimum": 0,
              "description": "Total cards drawn (including starting hand)"
            },
            "mulligans": {
              "type": "integer",
              "minimum": 0,
              "description": "Number of mulligans taken"
            },
            "starting_hand_size": {
              "type": "integer",
              "minimum": 0,
              "description": "Cards in hand after mulligans"
            }
          }
        },
        "spells": {
          "type": "object",
          "required": ["total_cast"],
          "properties": {
            "total_cast": {
              "type": "integer",
              "minimum": 0,
              "description": "Total spells cast (including creatures)"
            },
            "creatures": {
              "type": "integer",
              "minimum": 0,
              "description": "Creature spells cast"
            },
            "noncreatures": {
              "type": "integer",
              "minimum": 0,
              "description": "Non-creature spells cast"
            },
            "avg_cmc": {
              "type": "number",
              "minimum": 0,
              "description": "Average CMC of spells cast"
            }
          }
        },
        "mana": {
          "type": "object",
          "required": ["lands_played"],
          "properties": {
            "lands_played": {
              "type": "integer",
              "minimum": 0,
              "description": "Total lands played"
            },
            "missed_drops": {
              "type": "integer",
              "minimum": 0,
              "description": "Turns without land drop (when able)"
            },
            "peak_available": {
              "type": "integer",
              "minimum": 0,
              "description": "Highest available mana in any turn"
            },
            "total_produced": {
              "type": "integer",
              "minimum": 0,
              "description": "Total mana produced throughout game"
            },
            "total_spent": {
              "type": "integer",
              "minimum": 0,
              "description": "Total mana spent on spells"
            }
          }
        },
        "combat": {
          "type": "object",
          "required": ["damage_dealt", "damage_taken"],
          "properties": {
            "damage_dealt": {
              "type": "integer",
              "minimum": 0,
              "description": "Total damage dealt (combat + non-combat)"
            },
            "damage_taken": {
              "type": "integer",
              "minimum": 0,
              "description": "Total damage taken"
            },
            "attacks_declared": {
              "type": "integer",
              "minimum": 0,
              "description": "Number of attack declarations"
            },
            "blocks_declared": {
              "type": "integer",
              "minimum": 0,
              "description": "Number of block declarations"
            }
          }
        },
        "board": {
          "type": "object",
          "required": ["final_creatures", "final_lands"],
          "properties": {
            "final_creatures": {
              "type": "integer",
              "minimum": 0,
              "description": "Creatures on battlefield at game end"
            },
            "final_lands": {
              "type": "integer",
              "minimum": 0,
              "description": "Lands on battlefield at game end"
            },
            "final_other": {
              "type": "integer",
              "minimum": 0,
              "description": "Other permanents at game end"
            },
            "peak_creatures": {
              "type": "integer",
              "minimum": 0,
              "description": "Highest creature count during game"
            }
          }
        },
        "tempo": {
          "type": "object",
          "properties": {
            "abilities_activated": {
              "type": "integer",
              "minimum": 0,
              "description": "Total abilities activated"
            },
            "counters_placed": {
              "type": "integer",
              "minimum": 0,
              "description": "Total counters placed (e.g., +1/+1)"
            },
            "turns_with_action": {
              "type": "integer",
              "minimum": 0,
              "description": "Turns where player cast spell or activated ability"
            }
          }
        }
      }
    }
  }
}
```

---

## Field Descriptions

### Meta Section

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `timestamp` | ISO 8601 | ✅ | When simulation was run |
| `simulation_id` | string | ❌ | Unique ID for tracking |
| `game_type` | enum | ✅ | Format (Commander, Constructed, etc.) |
| `deck1_name` | string | ❌ | Player 1 deck name |
| `deck2_name` | string | ❌ | Player 2 deck name |
| `deck1_hash` | hex16 | ❌ | SHA-256 deck hash (first 16 chars) |
| `deck2_hash` | hex16 | ❌ | SHA-256 deck hash (first 16 chars) |

### Outcome Section

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `winner` | P1/P2/null | ✅ | Winner or null for draw |
| `win_condition` | enum | ❌ | damage/mill/combo/concede/timeout/draw |
| `total_turns` | integer | ✅ | Total game turns |
| `duration_ms` | integer | ✅ | Real-time duration in milliseconds |
| `game_ended_reason` | string | ❌ | Technical reason (from GameEndReason) |

### Player Stats

#### Cards

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `drawn` | integer | ✅ | Total cards drawn (incl. starting hand) |
| `mulligans` | integer | ❌ | Number of mulligans |
| `starting_hand_size` | integer | ❌ | Hand size after mulligans |

#### Spells

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `total_cast` | integer | ✅ | All spells cast |
| `creatures` | integer | ❌ | Creature spells cast |
| `noncreatures` | integer | ❌ | Non-creature spells cast |
| `avg_cmc` | float | ❌ | Average CMC of cast spells |

#### Mana

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `lands_played` | integer | ✅ | Total lands played |
| `missed_drops` | integer | ❌ | Turns without land drop (when possible) |
| `peak_available` | integer | ❌ | Max mana available in any turn |
| `total_produced` | integer | ❌ | Total mana produced |
| `total_spent` | integer | ❌ | Total mana spent on spells |

#### Combat

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `damage_dealt` | integer | ✅ | All damage dealt |
| `damage_taken` | integer | ✅ | All damage taken |
| `attacks_declared` | integer | ❌ | Number of attacks |
| `blocks_declared` | integer | ❌ | Number of blocks |

#### Board

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `final_creatures` | integer | ✅ | Creatures at game end |
| `final_lands` | integer | ✅ | Lands at game end |
| `final_other` | integer | ❌ | Other permanents at game end |
| `peak_creatures` | integer | ❌ | Max creatures during game |

#### Tempo

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `abilities_activated` | integer | ❌ | Total abilities activated |
| `counters_placed` | integer | ❌ | Total counters placed |
| `turns_with_action` | integer | ❌ | Turns with any action |

### Timeline (Optional)

Arrays of values tracked per turn. All arrays must have same length = `total_turns`.

| Field | Type | Description |
|-------|------|-------------|
| `turn_count` | int[] | Turn numbers [1,2,3,...] |
| `P1_life` | int[] | P1 life at end of each turn |
| `P2_life` | int[] | P2 life at end of each turn |
| `P1_creatures` | int[] | P1 creature count per turn |
| `P2_creatures` | int[] | P2 creature count per turn |

**Note:** Timeline is optional because it adds ~2-4 KB per game. Include only when turn-by-turn analysis is needed.

---

## Example File

```json
{
  "format": "forge-simulation-stats",
  "version": "2.0.0",
  "meta": {
    "timestamp": "2026-04-07T14:30:22Z",
    "simulation_id": "sim_001",
    "game_type": "Commander",
    "deck1_name": "Krenko Mob Boss",
    "deck2_name": "Atraxa Superfriends",
    "deck1_hash": "a3f8c2d1e9b7f604",
    "deck2_hash": "f7a2b9c3d5e8a104"
  },
  "outcome": {
    "winner": "P1",
    "win_condition": "damage",
    "total_turns": 12,
    "duration_ms": 45320,
    "game_ended_reason": "PLAYER_LOST_GAME"
  },
  "players": {
    "P1": {
      "deck_name": "Krenko Mob Boss",
      "final_life": 8,
      "life_delta": -32,
      "cards": {
        "drawn": 18,
        "mulligans": 0,
        "starting_hand_size": 7
      },
      "spells": {
        "total_cast": 22,
        "creatures": 14,
        "noncreatures": 8,
        "avg_cmc": 2.8
      },
      "mana": {
        "lands_played": 8,
        "missed_drops": 1,
        "peak_available": 9,
        "total_produced": 156,
        "total_spent": 142
      },
      "combat": {
        "damage_dealt": 160,
        "damage_taken": 32,
        "attacks_declared": 8,
        "blocks_declared": 2
      },
      "board": {
        "final_creatures": 12,
        "final_lands": 8,
        "final_other": 3,
        "peak_creatures": 15
      },
      "tempo": {
        "abilities_activated": 5,
        "counters_placed": 0,
        "turns_with_action": 11
      }
    },
    "P2": {
      "deck_name": "Atraxa Superfriends",
      "final_life": 0,
      "life_delta": -40,
      "cards": {
        "drawn": 16,
        "mulligans": 1,
        "starting_hand_size": 6
      },
      "spells": {
        "total_cast": 15,
        "creatures": 3,
        "noncreatures": 12,
        "avg_cmc": 3.5
      },
      "mana": {
        "lands_played": 9,
        "missed_drops": 0,
        "peak_available": 10,
        "total_produced": 172,
        "total_spent": 165
      },
      "combat": {
        "damage_dealt": 32,
        "damage_taken": 160,
        "attacks_declared": 3,
        "blocks_declared": 5
      },
      "board": {
        "final_creatures": 0,
        "final_lands": 9,
        "final_other": 0,
        "peak_creatures": 6
      },
      "tempo": {
        "abilities_activated": 12,
        "counters_placed": 8,
        "turns_with_action": 10
      }
    }
  },
  "timeline": {
    "turn_count": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
    "P1_life": [40, 40, 40, 38, 35, 30, 22, 18, 15, 12, 10, 8],
    "P2_life": [40, 39, 37, 35, 30, 22, 15, 8, 0, 0, 0, 0],
    "P1_creatures": [0, 1, 2, 3, 5, 7, 10, 12, 14, 15, 14, 12],
    "P2_creatures": [0, 0, 1, 2, 3, 4, 5, 6, 5, 4, 0, 0]
  }
}
```

---

## Derived Metrics (Calculated in Analytics)

These metrics are **NOT** stored in the simulation stats file but are calculated by the analytics engine:

### Per-Game Derived Metrics

```python
# From simulation stats
stats = load_simulation_stats("sim_001.json")
player = stats['players']['P1']
outcome = stats['outcome']

# Calculate derived metrics
derived = {
    "card_draw_rate": player['cards']['drawn'] / outcome['total_turns'],
    "spell_velocity": player['spells']['total_cast'] / outcome['total_turns'],
    "mana_efficiency": player['mana']['total_spent'] / player['mana']['total_produced'],
    "damage_ratio": player['combat']['damage_dealt'] / max(1, player['combat']['damage_taken']),
    "creature_density": player['spells']['creatures'] / player['spells']['total_cast']
}
```

### Aggregate Metrics (Over Multiple Games)

```python
# Load 100 games
all_stats = load_batch("simulation_stats_*.json", limit=100)

# Calculate aggregates
aggregate = {
    "total_games": len(all_stats),
    "wins": count_wins(all_stats, "P1"),
    "win_rate": count_wins(all_stats, "P1") / len(all_stats),
    
    "avg_turns": mean([s['outcome']['total_turns'] for s in all_stats]),
    "median_turns": median([s['outcome']['total_turns'] for s in all_stats]),
    "stdev_turns": stdev([s['outcome']['total_turns'] for s in all_stats]),
    
    "avg_damage_dealt": mean([s['players']['P1']['combat']['damage_dealt'] for s in all_stats]),
    "avg_spell_velocity": mean([derive_spell_velocity(s) for s in all_stats])
}
```

---

## Validation

Use JSON Schema validator:

```bash
# Install validator
pip install jsonschema

# Validate file
jsonschema -i simulation_stats_001.json simulation-stats-schema.json
```

Python validation:

```python
import json
import jsonschema

# Load schema
with open('simulation-stats-schema.json', 'r') as f:
    schema = json.load(f)

# Load stats file
with open('simulation_stats_001.json', 'r') as f:
    stats = json.load(f)

# Validate
try:
    jsonschema.validate(instance=stats, schema=schema)
    print("✅ Valid simulation stats file")
except jsonschema.ValidationError as e:
    print(f"❌ Validation error: {e.message}")
```

---

**Version:** 2.0.0  
**Last Updated:** 2026-04-07

