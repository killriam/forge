# Commander Simulation: Gemessene Metriken

**Version:** 1.0.0  
**Datum:** 2026-04-07  
**Zweck:** Vollständige Dokumentation aller extrahierten Statistiken aus Commander AI-Simulationen

---

## 1. Übersicht

Commander AI-Simulationen generieren zwei Arten von Statistiken:

1. **Game Summary** (`game_summary`): Aggregierte Werte für das gesamte Spiel
2. **Turn Summary** (`per_turn_summary`): Granulare Werte pro Turn

Alle Metriken werden aus den MTG Replay Notation JSON-Logs extrahiert, die automatisch nach jedem Spiel gespeichert werden.

---

## 2. Game Summary Metriken

### 2.1 Spiel-Metadaten

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `total_turns` | integer | `game_summary.total_turns` | Gesamtanzahl der Turns (gezählt ab Starting Player) |
| `duration_seconds` | integer | `game_summary.duration_seconds` | Spieldauer in Sekunden (Echtzeit) |
| `winner` | string | `game_summary.winner` | Player-ID des Gewinners (z.B. `"P1"`, `"P2"`) oder `null` bei Draw |
| `win_condition` | string | `game_summary.win_condition` | Wie das Spiel gewonnen wurde (z.B. `"damage"`, `"mill"`, `"combo"`) |

### 2.2 Card Flow Metriken

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `total_cards_drawn` | integer | `players[P].total_cards_drawn` | Summe aller gezogenen Karten (inkl. Starthand) |
| `card_draw_rate` | float | `players[P].card_draw_rate` | Durchschnittliche Karten pro Turn (`total_cards_drawn / total_turns`) |
| `total_spells_cast` | integer | `players[P].total_spells_cast` | Summe aller gespielten Spells (inkl. Kreaturen) |
| `spell_velocity` | float | `players[P].spell_velocity` | Durchschnittliche Spells pro Turn (`total_spells_cast / total_turns`) |
| `total_abilities_activated` | integer | `players[P].total_abilities_activated` | Summe aller aktivierten Abilities |

### 2.3 Mana Metriken

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `missed_land_drops` | integer | `players[P].missed_land_drops` | Turns ohne Land-Drop (ab Turn 2, exkl. Turn 0/1) |
| `total_lands_played` | integer | `players[P].total_lands_played` | Summe aller gespielten Lands |
| `peak_mana` | integer | `players[P].peak_mana` | Höchste verfügbare Mana-Menge in einem Turn |

**Interpretation:**
- **Niedrige `missed_land_drops`** = konsistente Mana-Kurve
- **Hohe `peak_mana`** = Ramp-Strategie erfolgreich
- **Verhältnis `total_lands_played / total_turns`** = Land-Drop-Konsistenz

### 2.4 Combat Metriken

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `total_damage_dealt` | integer | `players[P].total_damage_dealt` | Summe allen zugefügten Schadens (Combat + Non-Combat) |
| `total_damage_received` | integer | `players[P].total_damage_received` | Summe allen erhaltenen Schadens |
| `total_creatures_played` | integer | `players[P].total_creatures_played` | Summe aller gespielten Kreaturen |

**Interpretation:**
- **`total_damage_dealt > total_damage_received`** = Aggressive/proaktive Strategie
- **`total_creatures_played` vs. `spell_velocity`** = Creature-Density im Deck

### 2.5 Life Total Metriken

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `starting_life` | integer | `players[P].starting_life` | Startlife (Commander: 40) |
| `ending_life` | integer | `players[P].ending_life` | Life am Spielende |
| `life_delta` | integer | `players[P].life_delta` | Differenz (`ending_life - starting_life`) |

**Interpretation:**
- **Positiver `life_delta`** = Lifegain-Strategie oder niedrige Bedrohung durch Gegner
- **Negativer `life_delta`** = Aggressiver Gegner oder Life-als-Ressource-Strategie

### 2.6 Tempo Metriken

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `total_counters_placed` | integer | `players[P].total_counters_placed` | Summe aller platzierten Counter (z.B. +1/+1) |

---

## 3. Turn Summary Metriken (Per-Turn)

Jedes Spiel enthält eine `per_turn_summary[]` Array mit Statistiken für jeden Turn.

### 3.1 Turn-Metadaten

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `turn` | integer | `per_turn_summary[].turn` | Turn-Nummer (ab 1) |
| `active_player` | string | `per_turn_summary[].active_player` | Aktiver Spieler in diesem Turn (z.B. `"P1"`) |

### 3.2 Per-Turn Actions

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `lands_played` | integer | `players[P].lands_played` | Lands in diesem Turn gespielt |
| `land_drop_rating` | string | `players[P].land_drop_rating` | Bewertung: `"bad"`, `"good"`, `"super"` (heuristisch) |
| `cards_drawn` | integer | `players[P].cards_drawn` | Karten in diesem Turn gezogen |
| `spells_cast` | integer | `players[P].spells_cast` | Spells in diesem Turn gespielt |
| `abilities_activated` | integer | `players[P].abilities_activated` | Abilities in diesem Turn aktiviert |

**Interpretation:**
- **`land_drop_rating`** (Heuristik):
  - `"bad"`: 0 Lands gespielt, >3 Lands in Hand
  - `"good"`: 1 Land gespielt
  - `"super"`: >1 Land gespielt (Ramp-Effect)

### 3.3 Per-Turn Board State

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `land_count` | integer | `players[P].land_count` | Lands auf dem Battlefield am Turn-Ende |
| `available_mana` | integer | `players[P].available_mana` | Geschätzte verfügbare Mana (nicht nach Farbe) |
| `creatures_on_battlefield` | integer | `players[P].creatures_on_battlefield` | Kreaturen auf dem Battlefield am Turn-Ende |
| `permanents_on_battlefield` | integer | `players[P].permanents_on_battlefield` | Alle Permanents auf dem Battlefield am Turn-Ende |

**Interpretation:**
- **`available_mana` steigt nicht** = Mana-Problem oder keine weiteren Land-Drops
- **`creatures_on_battlefield` vs. `permanents_on_battlefield`** = Creature-Density auf dem Board

### 3.4 Per-Turn Resources

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `life` | integer | `players[P].life` | Life Total am Turn-Ende |
| `cards_in_hand` | integer | `players[P].cards_in_hand` | Karten in Hand am Turn-Ende |

### 3.5 Per-Turn Combat

| Metrik | Typ | Quelle | Beschreibung |
|--------|-----|--------|--------------|
| `damage_dealt` | integer | `players[P].damage_dealt` | Schaden in diesem Turn zugefügt |
| `damage_taken` | integer | `players[P].damage_taken` | Schaden in diesem Turn erhalten |

---

## 4. Aggregierte Report-Metriken

Das `analyze_commander_stats.py`-Skript berechnet zusätzliche statistische Metriken über mehrere Spiele:

### 4.1 Win-Rate Metriken

| Metrik | Berechnung | Beschreibung |
|--------|------------|--------------|
| `total_games` | Count | Anzahl analysierter Spiele |
| `wins` | Count | Anzahl gewonnener Spiele |
| `losses` | Count | Anzahl verlorener Spiele |
| `win_rate` | `wins / total_games` | Win-Rate (0.0 - 1.0) |

### 4.2 Turn-Count Statistiken

| Metrik | Berechnung | Beschreibung |
|--------|------------|--------------|
| `avg_turns` | `mean(total_turns)` | Durchschnittliche Spieldauer in Turns |
| `median_turns` | `median(total_turns)` | Median Spieldauer |
| `min_turns` | `min(total_turns)` | Schnellstes Spiel |
| `max_turns` | `max(total_turns)` | Längstes Spiel |
| `stdev_turns` | `stdev(total_turns)` | Standardabweichung (Konsistenz-Indikator) |

**Interpretation:**
- **Niedrige `avg_turns`** = Aggressives Deck oder schnelle Combos
- **Niedrige `stdev_turns`** = Konsistente Deck-Performance

### 4.3 Durchschnitts-Metriken

| Metrik | Berechnung | Beschreibung |
|--------|------------|--------------|
| `avg_damage_dealt` | `mean(total_damage_dealt)` | Durchschnittlicher Schaden pro Spiel |
| `avg_damage_received` | `mean(total_damage_received)` | Durchschnittlich erhaltener Schaden |
| `avg_cards_drawn` | `mean(total_cards_drawn)` | Durchschnittliche Card Draw |
| `avg_spells_cast` | `mean(total_spells_cast)` | Durchschnittliche Spells pro Spiel |
| `avg_spell_velocity` | `mean(spell_velocity)` | Durchschnittliche Spells/Turn |
| `avg_missed_land_drops` | `mean(missed_land_drops)` | Durchschnittlich verpasste Land-Drops |
| `median_peak_mana` | `median(peak_mana)` | Median der Peak-Mana-Werte |
| `avg_lands_played` | `mean(total_lands_played)` | Durchschnittliche Land-Drops pro Spiel |
| `avg_creatures_played` | `mean(total_creatures_played)` | Durchschnittliche Creatures pro Spiel |
| `avg_life_delta` | `mean(life_delta)` | Durchschnittliche Life-Änderung |

### 4.4 Konsistenz-Metriken (Standardabweichung)

| Metrik | Berechnung | Beschreibung |
|--------|------------|--------------|
| `stdev_damage_dealt` | `stdev(total_damage_dealt)` | Damage-Variance (Konsistenz der Aggression) |
| `stdev_spell_velocity` | `stdev(spell_velocity)` | Spell-Velocity-Variance (Konsistenz der Plays) |
| `stdev_missed_land_drops` | `stdev(missed_land_drops)` | Mana-Konsistenz |

**Interpretation:**
- **Niedrige Standardabweichungen** = Konsistentes Deck
- **Hohe Standardabweichungen** = Variable Performance (mögliche Mulligan- oder Mana-Probleme)

---

## 5. Optimierungsziele

### 5.1 Maximize (Höher = Besser)

- `win_rate`
- `avg_damage_dealt`
- `avg_spell_velocity`
- `median_peak_mana`
- `avg_cards_drawn`

### 5.2 Minimize (Niedriger = Besser)

- `avg_missed_land_drops`
- `avg_damage_received`
- `stdev_turns` (für Konsistenz)
- `stdev_missed_land_drops` (für Mana-Konsistenz)

### 5.3 Balance (Je nach Deck-Strategie)

- `avg_turns`: Aggro will niedrig, Control will höher
- `avg_creatures_played`: Creature-heavy vs. Spell-heavy
- `life_delta`: Aggressiv (negativ OK) vs. Defensive (positiv bevorzugt)

---

## 6. Export-Format: JSON-Report

Das `analyze_commander_stats.py`-Skript generiert einen `commander_simulation_report.json` mit folgender Struktur:

```json
{
  "format": "commander-simulation-report",
  "version": "1.0.0",
  "meta": {
    "generated_at": "2026-04-07T14:30:00Z",
    "total_games": 100,
    "players": ["P1", "P2"]
  },
  "aggregate_stats": {
    "P1": {
      "total_games": 100,
      "wins": 58,
      "losses": 42,
      "win_rate": 0.58,
      "avg_turns": 12.4,
      "median_turns": 11.0,
      "avg_damage_dealt": 145.2,
      "avg_spell_velocity": 1.85,
      "avg_missed_land_drops": 1.2,
      "stdev_turns": 3.1,
      "stdev_missed_land_drops": 0.8
    },
    "P2": { /* ... */ }
  },
  "per_game_details": [
    {
      "game_id": "game_001",
      "winner": "P1",
      "total_turns": 10,
      "duration_seconds": 342,
      "players": {
        "P1": {
          "total_damage_dealt": 160,
          "total_spells_cast": 18,
          "spell_velocity": 1.8,
          "missed_land_drops": 0,
          "peak_mana": 7
        },
        "P2": { /* ... */ }
      }
    }
  ]
}
```

---

## 7. Verwendung der Metriken

### 7.1 Deck-Optimierung

1. **Mana-Base-Tuning:**
   - `avg_missed_land_drops > 2` → Mehr Lands oder Ramp hinzufügen
   - `median_peak_mana < 5` → Ramp-Package verstärken

2. **Mulligan-Tuning:**
   - `win_rate` niedrig + `avg_missed_land_drops` hoch → Mulligan-Thresholds anpassen
   - Analyse: Spiele mit `missed_land_drops == 0` vs. `> 2` vergleichen

3. **Tempo-Optimierung:**
   - `avg_spell_velocity < 1.5` → Mehr Low-CMC Spells oder Card Draw
   - `avg_turns > 15` → Win-Conditions beschleunigen

### 7.2 Matchup-Analyse

- **Mirror Match** (Deck vs. sich selbst): Testet Deck-Konsistenz
- **Fixed Opponent**: `win_rate P1 vs P2` zeigt Matchup-Balance
- **Damage-Asymmetrie**: `P1.avg_damage_dealt >> P2.avg_damage_dealt` = P1 ist aggressiver

### 7.3 Combo-Tracking (zukünftig)

Durch Kombination mit `deck_rules.combos` aus der Decklist JSON:
- Zähle Spiele, in denen Combo-Pieces gezogen wurden
- Messe Turn-Nummer bis Combo assembled
- Berechne Win-Rate mit vs. ohne Combo

---

**Letzte Aktualisierung:** 2026-04-07

