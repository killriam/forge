# Feature: Decklist Mulligan Evaluator

Spec-driven mulligan evaluation based on the **Commander Decklist Notation §6.1**
([`commander-decklist-spec.md`](../mtg-replay-notation/spec/commander-decklist-spec.md#61-mulligan-rule)).
Replaces the default AI hand-scoring heuristic with a configurable, JSON-driven
decision procedure when a decklist config file is provided.

## Motivation

The default AI mulligan logic in `ComputerUtil.scoreHand()` uses hard-coded heuristics
(land count ratios, castable spells, etc.). For tuned Commander decks, a deck-author
can define precise mulligan rules — card-type-based values, per-card overrides, and
round-specific thresholds — that better reflect the deck's game plan.

## Spec Summary (§6.1)

### §6.1.1 Card Values

*(Updated for spec v1.4.0 — a full per-mana-value curve, not the old 4-bucket CMC shape.)*
Each card in the opening hand is assigned a value based on its type and exact mana value:

| Key | Default | Applies To |
|-----|---------|------------|
| `land` | `1.0` | Any land card (× a 1.0–1.4 multiplier if it produces 2+ colors — §6.1.1b) |
| `mv0` | `0.85` | Non-land, mana value 0 |
| `mv1` | `0.8` | Non-land, mana value 1 |
| `mv2` | `0.75` | Non-land, mana value 2 |
| `mv3` | `0.6` | Non-land, mana value 3 |
| `mv4`–`mv7Plus` | `0.2` | Non-land, mana value 4+ (flat, since v1.4.0) |

A non-land card with an `{X}` in its cost scores as if its mana value were 2 higher, for curve
lookup only (§6.1.1a) — e.g. Fireball (`{X}{R}`, real mv 1) scores as `mv3`.

**Example hand score:**
3 lands (1.0 each, no multicolor bonus) + 2 mana rocks mv2 (0.75 each) + 2 spells mv5 (0.2 each)
= 3.0 + 1.5 + 0.4 = **4.9**

### §6.1.2 Mulligan Thresholds

| Round | Hand Size | Min Value | Description |
|-------|-----------|-----------|-------------|
| 0 | 7 | 3.5 | Initial hand |
| 1 | 6 | 3.0 | After first mulligan |
| 2 | 5 | 2.5 | After second mulligan |
| 3 | 4 | 2.0 | After third mulligan |

**Decision**: If `total_value >= min_value` → **keep**. Otherwise → **mulligan**.

Beyond round 3, no threshold is defined → the hand is always kept.

### §6.1.3 Per-Card Overrides

Individual cards can override the generic type-based value:

```json
{
    "name": "Sol Ring",
    "value": 1.2,
    "reason": "Best turn-1 play in Commander"
}
```

Override priority: **per-card override → land (× multicolor bonus) → mana-value curve bucket**.

## Architecture

```
SimulateMatch  ──(-l flag)──►  RegisteredPlayer.decklistConfigPath
                                          │
                                          ▼
ComputerUtil.wantMulligan(ai, cardsToReturn)
  ├─ ai.getRegisteredPlayer().getDecklistConfigPath()
  ├─ DecklistMulliganEvaluator.fromJsonFile(configPath)   ◄── cached per path
  │     └─ Parses JSON → DecklistMulliganConfig
  ├─ evaluator.shouldKeep(hand, cardsToReturn)
  │     ├─ evaluateHand() → sum of scoreCard() per card
  │     └─ getMinValueForRound(round) → threshold lookup
  └─ Fallback: scoreHand() (default heuristic if no config or on error)
```

`DecklistMulliganEvaluator` also exposes `evaluateManaBase()`/`isPlayable()`/`isGoodAiHand()`
(§6.1.5 Mana Base Band) and `startingHandQuality()`/`manaCurveBonusAchieved()` (§6.1.6's
Forge-representable Mana Curve bonus) — see "Mana Base Band & Starting Hand Quality" below.

## Involved Files

| File | Module | Role |
|------|--------|------|
| `ManaProductionUtil.java` | `forge-core` (`forge.deck.mulligan`) | `CardRules`-only helpers: land/mana-value detection, X-cost, produced colors, §6.1.1b coverage multiplier |
| `HandQualityTarget.java` | `forge-core` (`forge.deck.mulligan`) | `NONE`/`Q1`-`Q4`/`BEST` per-seat target enum |
| `DeckRulesConfig.java` | `forge-core` | Data model for `deck_rules` incl. `MulliganConfig` (9-key curve + Mana Base Band) |
| `DecklistMulliganConfig.java` | `forge-game` (`forge.game.mulligan`) | Gson-shaped mirror of `MulliganConfig`, used for JSON parsing |
| `DecklistMulliganEvaluator.java` | `forge-game` (`forge.game.mulligan`) | Scoring engine: `scoreCard()`, `evaluateHand()`, `shouldKeep()`, `evaluateManaBase()`, `startingHandQuality()` |
| `HandQualitySampler.java` | `forge-game` (`forge.game.mulligan`) | Monte Carlo percentile sampler over a deck's own main-deck pool, backs the Target Hand Quality picker |
| `ComputerUtil.java` | `forge-ai` | Integration point in `wantMulligan()` — checks for config before default scoring |
| `PlayerControllerAi.java` | `forge-ai` | Calls `ComputerUtil.wantMulligan()` from `mulliganKeepHand()` |
| `RegisteredPlayer.java` | `forge-game` | Stores `decklistConfigPath` and `startingHandQualityTarget` per player |
| `Player.ensureStartingHandQualityTarget()` | `forge-game` | Reorders the opening hand (bounded reshuffle attempts) to meet the seat's chosen target |
| `PlayerPanel.java` | `forge-gui-desktop` | Lobby UI: Mana Base Band display + Target Hand Quality picker, next to "Guarantee Cards Under Test" |
| `InputConfirmMulligan.java` | `forge-gui` | Human mulligan dialog: per-card value breakdown + Mulligan Value/Mana Base/Starting Hand Quality |
| `SimulateMatch.java` | `forge-gui-desktop` | CLI flag `-l` sets decklist config paths |

*(`DecklistMulliganConfig`/`DecklistMulliganEvaluator`/`HandQualitySampler` moved from
`forge-ai` to `forge-game` so `Player.ensureStartingHandQualityTarget()` — needed at game setup
alongside `ensureCardsUnderTestInTop10()` — can use them without forge-game depending on
forge-ai, which would be circular since forge-ai already depends on forge-game.)*

## CLI Usage

```bash
# Both players use the same mulligan config
java -jar forge-gui-desktop-*.jar sim \
    -d deck1.dck -d deck2.dck \
    -n 10 \
    -l mulligan_config.json

# Each player uses a different config
java -jar forge-gui-desktop-*.jar sim \
    -d deck1.dck -d deck2.dck \
    -n 10 \
    -l config_player1.json config_player2.json

# Combined with replay mode and Commander format
java -jar forge-gui-desktop-*.jar sim \
    -d cmd_deck1.dck -d cmd_deck2.dck \
    -n 1 -f commander -q \
    -r replay.json \
    -l decklist1.json decklist2.json
```

| Flag | Description |
|------|-------------|
| `-l <path1> [path2] …` | One JSON config file per player (Commander Decklist Notation format) |

## JSON Config Format

The mulligan config is embedded in a Commander Decklist Notation JSON file under
`deck_rules.mulligan`:

```json
{
    "deck_rules": {
        "mulligan": {
            "card_values": {
                "land": 1.0,
                "mv0": 0.85,
                "mv1": 0.8,
                "mv2": 0.75,
                "mv3": 0.6,
                "mv4": 0.2,
                "mv5": 0.2,
                "mv6": 0.2,
                "mv7Plus": 0.2
            },
            "card_overrides": [
                {
                    "name": "Sol Ring",
                    "value": 1.2,
                    "reason": "Best turn-1 play in Commander"
                },
                {
                    "name": "Doubling Season",
                    "value": 0.6,
                    "reason": "High mana value but crucial for combo"
                }
            ],
            "thresholds": [
                { "round": 0, "hand_size": 7, "min_value": 3.5,
                  "description": "Keep 7-card hand if total value >= 3.5" },
                { "round": 1, "hand_size": 6, "min_value": 3.0,
                  "description": "Keep 6-card hand if total value >= 3.0" },
                { "round": 2, "hand_size": 5, "min_value": 2.5,
                  "description": "Keep 5-card hand if total value >= 2.5" },
                { "round": 3, "hand_size": 4, "min_value": 2.0,
                  "description": "Keep 4-card hand if total value >= 2.0" }
            ],
            "mana_base_min": 3,
            "mana_base_max": 4
        }
    }
}
```

### Config Fields

#### `card_values` (object)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `land` | number | `1.0` | Value for any land card (× a 1.0–1.4 coverage multiplier if it produces 2+ colors, §6.1.1b) |
| `mv0` | number | `0.85` | Non-land, mana value 0 |
| `mv1` | number | `0.8` | Non-land, mana value 1 |
| `mv2` | number | `0.75` | Non-land, mana value 2 |
| `mv3` | number | `0.6` | Non-land, mana value 3 |
| `mv4`–`mv7Plus` | number | `0.2` | Non-land, mana value 4 or higher |

#### `mana_base_min` / `mana_base_max` (§6.1.5 Mana Base Band)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `mana_base_min` | number | `3` | Below this Mana Base score, a hand is not **Playable** |
| `mana_base_max` | number | `4` | Above this, a hand is Playable but not a **Good AI Hand** |

#### `card_overrides` (array of objects)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | **Yes** | Exact card name |
| `value` | number | **Yes** | Override value for this card |
| `reason` | string | No | Human-readable rationale |

#### `thresholds` (array of objects)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `round` | integer | **Yes** | Mulligan round (0 = initial 7-card hand) |
| `hand_size` | integer | No | Expected hand size (informational) |
| `min_value` | number | **Yes** | Minimum total hand value to keep |
| `description` | string | No | Human-readable description |

## Scoring Algorithm

```
for each card in hand:
    if card.name in card_overrides:
        value = override.value
    else if card.isLand():
        value = card_values.land * coverageMultiplier(card, deckPipWeights)  // 1.0 if <2 colors
    else:
        mv = card.manaValue + (2 if card has an {X} cost else 0)   // §6.1.1a
        value = card_values[bucket(mv)]                            // land/mv0../mv7Plus

    total += value

threshold = thresholds[current_round].min_value
decision  = total >= threshold ? KEEP : MULLIGAN
```

## Mana Base Band & Starting Hand Quality (§6.1.5 / §6.1.6)

Alongside the total-value keep/mulligan decision above, `DecklistMulliganEvaluator` also
computes two additional, independent numbers per hand:

- **Mana Base score** (`evaluateManaBase()`) — sums each card's Mana Base value (basic land
  `1.0`, non-mana utility land `0.0`, single-color land `0.8`/`1.0` tapped-vs-not, multicolor
  land via the same coverage formula as `card_values.land`, mana-producing non-lands `1.0`/`0.9`/
  `0.6` at mv 0/1/2, everything else `0.0`). `isPlayable()` checks `>= mana_base_min`;
  `isGoodAiHand()` additionally requires `<= mana_base_max`.
- **Starting Hand Quality** (`startingHandQuality()`) — the total mulligan value above, plus a
  flat `+0.5` (`manaCurveBonusAchieved()`) if the hand has a non-land permanent at mana value
  exactly 1, 2, 3, and 4. This is the only one of the spec's three Starting Hand Quality
  components representable from this notation alone — per-card Mechanic Graph tiers and
  deckmechanic synergy chains need MaMo-internal data this notation doesn't model (see spec
  §6.1.6).

**Deck-select UI**: the game lobby's per-seat panel (`PlayerPanel`, next to "Guarantee Cards
Under Test") shows the selected deck's configured Mana Base Band read-only, plus a **Target
Hand Quality** picker (`Off`/`Q1`–`Q4`/`Best hand found`). The four quartile levels and the
"best" level are thresholds on the *deck's own* Starting Hand Quality distribution — sampled by
`HandQualitySampler` (3000 random 7-card draws from the deck's main pool) rather than a fixed
universal scale, since achievable quality varies enormously deck to deck. Picking a level other
than `Off` makes `Player.ensureStartingHandQualityTarget()` reshuffle the library at game setup
(bounded to 200 attempts, keeping the best hand found if none clears the threshold) so the
opening hand meets or exceeds that percentile.

**Mulligan dialog**: `InputConfirmMulligan` (the human "Keep or Mulligan?" prompt) appends a
per-card mulligan-value breakdown plus the hand's total Mulligan Value (with the current round's
threshold and spec verdict), Mana Base score (with the Playable/Good AI Hand verdicts), and
Starting Hand Quality, whenever the player's deck declares mulligan rules. Purely informational —
the human still chooses Keep/Mulligan themselves.

## Fallback Behaviour

The evaluator integrates **non-invasively** into the existing mulligan flow:

1. If `decklistConfigPath` is `null` → default `scoreHand()` heuristic is used.
2. If the JSON file cannot be parsed → warning logged, falls back to default.
3. If `deck_rules.mulligan` section is missing → returns `null`, falls back to default.
4. If a threshold for the current round is missing → returns `min_value = 0.0` (always keep).

Any exception during evaluation is caught and the default heuristic takes over silently.

## Caching

`DecklistMulliganEvaluator` instances are cached per file path in a `ConcurrentHashMap`.
This avoids re-parsing the JSON for each mulligan call during a multi-game match.
Call `DecklistMulliganEvaluator.clearCache()` between matches if configs change.

## Round Mapping

In the London Mulligan (default), `cardsToReturn` passed to `wantMulligan()` maps
directly to the mulligan round in 2-player games:

| `cardsToReturn` | Mulligan Round | Hand Size (London) | Threshold |
|-----------------|---------------|---------------------|-----------|
| 0 | 0 | 7 | 3.5 |
| 1 | 1 | 7 → put 1 back → 6 | 3.0 |
| 2 | 2 | 7 → put 2 back → 5 | 2.5 |
| 3 | 3 | 7 → put 3 back → 4 | 2.0 |

> **Note**: In multiplayer (3+ players), the first mulligan is free (`firstMulliganFree = true`),
> which shifts `cardsToReturn` by one. The thresholds still work reasonably because they
> correlate with effective hand size rather than strict round numbers.

## Logging

All evaluator operations are logged via SLF4J:

| Level | Message |
|-------|---------|
| `INFO` | Config loaded (override count, threshold count) |
| `DEBUG` | Per-hand evaluation (hand value, threshold, KEEP/MULLIGAN decision) |
| `WARN` | Failed to load config / missing `deck_rules.mulligan` section |

## Example: Tuning a Ramp Deck

For a deck that relies on ramping into expensive spells, raise the value of high-CMC
cards and add key overrides:

```json
{
    "deck_rules": {
        "mulligan": {
            "card_values": {
                "land": 1.0,
                "mv0": 0.9,
                "mv1": 0.9,
                "mv2": 0.85,
                "mv3": 0.6,
                "mv4": 0.4,
                "mv5": 0.4,
                "mv6": 0.4,
                "mv7Plus": 0.4
            },
            "card_overrides": [
                { "name": "Sol Ring", "value": 1.2 },
                { "name": "Mana Crypt", "value": 1.2 },
                { "name": "Cultivate", "value": 0.9 },
                { "name": "Kodama's Reach", "value": 0.9 }
            ],
            "thresholds": [
                { "round": 0, "hand_size": 7, "min_value": 3.8 },
                { "round": 1, "hand_size": 6, "min_value": 3.2 },
                { "round": 2, "hand_size": 5, "min_value": 2.5 },
                { "round": 3, "hand_size": 4, "min_value": 2.0 }
            ]
        }
    }
}
```

This configuration keeps more aggressively (higher thresholds), but compensates by
valuing ramp spells and mana rocks almost as highly as lands.

---

## Phase 2: Combo Tracking & Anti-Synergy Avoidance

In addition to mulligan evaluation, the `deck_rules` section now drives runtime AI
awareness of combos and anti-synergies.

### Architecture

```
Commander Decklist Notation (.json)
    └── deck_rules
        ├── mulligan     → DecklistMulliganEvaluator
        ├── combos       → ComboTracker.evaluateCombos()
        └── dont_combos  → ComboTracker.checkAntiSynergies()
```

### New Core Files

| File | Module | Purpose |
|------|--------|---------|
| `DeckRulesConfig.java` | forge-core | Data model for all deck_rules (mulligan, combos, anti-synergies) |
| `DeckRulesLoader.java` | forge-ai | JSON parser: Commander Decklist Notation → DeckRulesConfig |
| `ComboTracker.java` | forge-ai | Runtime combo/anti-synergy tracker for AI player |

### Modified Files

| File | Module | Change |
|------|--------|--------|
| `Deck.java` | forge-core | Added `deckRulesConfig`, `decklistSpecPath` fields; `setAiHints` extracts `DecklistSpec$` |
| `AiController.java` | forge-ai | Added `ComboTracker` field + `initComboTracker()` |
| `PlayerControllerAi.java` | forge-ai | Calls `initComboTracker()` at game start |
| `ComputerUtil.java` | forge-ai | `wantMulligan()` uses deck-rules evaluator when available |
| `DecklistMulliganEvaluator.java` | forge-game | Added `fromDeckRules()` factory bridging DeckRulesConfig → evaluator (later moved here from forge-ai — see "Involved Files" above) |

### Combo Awareness API

```java
ComboTracker tracker = aiController.getComboTracker();

// Evaluate all combos for the current game state
List<ComboStatus> statuses = tracker.evaluateCombos(player);
for (ComboStatus s : statuses) {
    // s.readiness  — 0.0 to 1.0 (how close to assembly)
    // s.piecesOnBoard, s.piecesInHand, s.piecesMissing
}

// Get best tutor target (closest-to-complete combo piece)
String target = tracker.getBestTutorTarget(player);

// Get missing pieces across all combos (for search prioritization)
Set<String> missing = tracker.getMissingComboPieces(player);
```

### Anti-Synergy Penalty API

```java
// Check what anti-synergies would trigger if we play "Rule of Law"
List<AntiSynergy> triggered = tracker.checkAntiSynergies(player, "Rule of Law");

// Get a numeric penalty (0, -5, -15, or -30 based on severity)
int penalty = tracker.getAntiSynergyPenalty(player, "Rule of Law");

// Check currently active anti-synergies on the battlefield
List<AntiSynergy> active = tracker.getActiveAntiSynergies(player);
```

### Loading Deck Rules

#### Option A: External JSON file (recommended)

Reference a Commander Decklist Notation JSON from the deck header:
```
[AiHints]
DecklistSpec$path/to/my-deck.json
```

#### Option B: Inline AiHints (compact format)

```
[AiHints]
MulliganThreshold$0:3.5;1:3.0;2:2.5;3:2.0
MulliganOverride$Sol Ring:1.2;Doubling Season:0.6
Combo$combo1:Doubling Season,Atraxa, Praetors' Voice
DontCombo$dc1:Rule of Law,Thousand-Year Storm:critical
```

#### Option C: Programmatic

```java
Deck deck = ...;
DeckRulesConfig config = new DeckRulesConfig();
config.setMulligan(DeckRulesConfig.MulliganConfig.createDefault());
deck.setDeckRulesConfig(config);
```
