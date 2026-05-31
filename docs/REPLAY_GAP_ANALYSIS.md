# MTG Replay Log Gap Analysis

**Document Version:** 1.0  
**Date:** 2026-04-06  
**Scope:** Analysis of gaps between current Forge replay logs and requirements for accurate board state representation, game state evaluation, and learning helper statistics

---

## Executive Summary

This document analyzes the gap between Forge's current MTG Replay Notation implementation and the requirements defined in the [MTG State Evaluation Specification](../mtg-replay-notation/spec/mtg-state-evaluation-spec.md). The analysis is structured around three key objectives:

**A) Board State Accuracy** — Capturing sufficient detail to reconstruct game states  
**B) Evaluation Framework Support** — Enabling 10-dimensional state evaluation  
**C) Learning Helper Statistics** — Providing actionable feedback for player improvement

### Key Findings

| Category | Current Coverage | Gap Severity | Priority |
|----------|------------------|--------------|----------|
| **Basic Zone Tracking** | ✅ Complete | None | — |
| **Card Attributes** | 🟡 Partial (40%) | High | P0 |
| **Mana System Detail** | 🟡 Partial (30%) | Critical | P0 |
| **Combat Metadata** | ❌ Missing | High | P1 |
| **Card Tags/Categories** | ❌ Missing | Critical | P0 |
| **Formation/Synergy Data** | ❌ Missing | Medium | P2 |
| **Learning Statistics** | 🟡 Partial (50%) | High | P1 |
| **Blunder Detection Data** | ❌ Missing | Medium | P2 |

---

## Table of Contents

1. [Current Implementation Overview](#1-current-implementation-overview)
2. [Gap Analysis: Board State Accuracy](#2-gap-analysis-board-state-accuracy)
3. [Gap Analysis: Evaluation Framework](#3-gap-analysis-evaluation-framework)
4. [Gap Analysis: Learning Helper Statistics](#4-gap-analysis-learning-helper-statistics)
5. [Proposed Additional Statistics](#5-proposed-additional-statistics)
6. [Implementation Roadmap](#6-implementation-roadmap)
7. [Data Model Extensions](#7-data-model-extensions)

---

## 1. Current Implementation Overview

### 1.1 Existing Data Structures

#### ReplayLog (Top-Level Container)
```java
class ReplayLog {
    String format, version, specVersion;
    ReplayMeta meta;
    long seed;
    GameStartInfo gameStart;
    Map<String, CardDefinition> cardIndex;      // ✅ Basic card metadata
    GameState initialState;                     // ✅ Snapshot at game start
    List<L1Event> logL1;                        // ✅ Full event stream
    List<L2Unit> viewsL2;                       // 🟡 Partially implemented
    List<LearningMarker> learningMarkers;       // ✅ User bookmarks
    List<TurnSummary> perTurnSummary;           // 🟡 Basic stats only
    GameSummary gameSummary;                    // 🟡 Basic aggregates
}
```

#### GameState (Snapshot)
```java
class GameState {
    int turn;
    String phase, step, priority, activePlayer;
    Map<String, PlayerState> players;           // ✅ Life, mana pool, counters
    Map<String, Object> zones;                  // ✅ Zone card lists
    Map<String, ObjectState> objects;           // 🟡 See below
}

class ObjectState {
    String cardRef, controller, owner, zone;
    boolean tapped, flipped, faceDown;
    Map<String, Integer> counters;              // ✅ +1/+1, loyalty, etc.
    int damageMarked;
    String attachedTo;
    Map<String, Object> notes;                  // 🟡 Generic escape hatch
}
```

**Missing from ObjectState:**
- Power/Toughness (current values)
- Base P/T (for delta calculation)
- Keyword abilities (flying, trample, etc.)
- Summoning sickness flag
- Ability descriptors (activated, triggered)
- Mana production capabilities

#### TurnSummary.PlayerTurnStats
```java
class PlayerTurnStats {
    // ✅ Basic Counts
    int landsPlayed, cardsDrawn, spellsCast, abilitiesActivated;
    int landCount, life, cardsInHand;
    int creaturesOnBattlefield, permanentsOnBattlefield;
    int damageDealt, damageTaken;
    
    // 🟡 Estimated Values
    String landDropRating;      // "bad", "good", "super" — heuristic only
    int availableMana;          // Estimation; no color breakdown
}
```

**Missing:**
- Detailed mana breakdown (colored vs. colorless, untapped sources)
- Cast options in hand (requires card DB + mana analysis)
- Mana color coverage metrics
- Combat threat metrics (attackers, blockers, evasion)

#### CardDefinition (card_index)
```java
class CardDefinition {
    String name, type, manaCost, oracleId;
    // Basic identifying info only
}
```

**Missing:**
- Power/Toughness
- Keywords
- Ability text (structured)
- Tags (mana_producer, engine_draw, etc.)
- Color identity
- Mana production capabilities

### 1.2 Event Coverage

Current L1 event types capture:
- ✅ CAST, MOVE, RESOLVE, TRIGGER, ACTIVATE, COUNTERS
- ✅ DAMAGE, DRAW, DISCARD, MULLIGAN
- ✅ DECLARE_ATTACKERS, DECLARE_BLOCKERS
- ✅ PHASE, ACTIVE_PLAYER_CHANGE

**Good:** Event stream is comprehensive for deterministic replay.

**Gap:** Events lack semantic annotations needed for evaluation (e.g., "this MOVE was a BOUNCE effect", "this CAST was a RAMP spell").

---

## 2. Gap Analysis: Board State Accuracy

### 2.1 Objective: Accurate Visual Reconstruction

**Goal:** Viewer should display battlefield identically to in-game state.

#### Current Capabilities ✅
- Zone membership (which cards are where)
- Tapped/untapped status
- Counters (all types)
- Attachments (Equipment, Auras)
- Face-down status
- Controller/owner tracking

#### Critical Gaps 🔴

| Missing Data | Impact | Use Cases |
|--------------|--------|-----------|
| **Power/Toughness** | Cannot display creature stats | Board evaluation, combat math |
| **Keywords** | Cannot show flying/trample icons | Evasion analysis, combat decisions |
| **Summoning Sickness** | Cannot indicate creatures with haste | Attack phase accuracy |
| **Activated Abilities** | Cannot show "tap abilities" | Forgotten ability detection |
| **Color Identity** | Cannot color-code permanents | Mana fixing analysis |

#### Recommended Additions

##### ObjectState Extensions
```java
class ObjectState {
    // ...existing fields...
    
    // Creature stats
    Integer power, toughness;           // Current values (null for non-creatures)
    Integer basePower, baseToughness;   // Original printed values
    
    // Status flags
    boolean summoningSick;              // Has haste or entered this turn
    boolean canAttack, canBlock;        // Computed from game state
    
    // Keywords (boolean flags for common ones)
    Set<String> keywords;               // "flying", "trample", "first_strike", etc.
    
    // Abilities (structured)
    List<AbilityDescriptor> abilities;  // Activated, triggered, static
    
    // Color identity (for mana analysis)
    Set<String> colorIdentity;          // ["W", "U", "B", "R", "G"]
    
    // Mana production (for lands/rocks/dorks)
    ManaProductionCapability manaAbility;
}

class AbilityDescriptor {
    String type;            // "activated", "triggered", "static"
    String cost;            // "{T}: Add {G}" or "Whenever...", etc.
    String effect;          // Human-readable
    boolean hasTapSymbol;   // For "forgotten tap ability" detection
}

class ManaProductionCapability {
    List<String> producesColors;    // ["W", "U"], ["any"], etc.
    boolean requiresTap;
    int producesAmount;             // 1 for lands, 2+ for rituals/doublers
    boolean conditional;            // e.g., "if you control an Island"
}
```

### 2.2 Objective: State-Based Action Verification

**Goal:** Viewer should highlight illegal states (for bug detection).

#### Current Capabilities ✅
- Basic consistency (cards in one zone only)
- Life total tracking
- Counter tracking

#### Critical Gaps 🔴
- Cannot verify creature death (toughness ≤ 0)
- Cannot verify planeswalker death (loyalty ≤ 0)
- Cannot verify legend rule violations
- Cannot verify summoning sickness enforcement

**Fix:** Requires P/T and loyalty tracking in ObjectState.

---

## 3. Gap Analysis: Evaluation Framework

The evaluation spec defines 10 dimensions. Below is dimension-by-dimension gap analysis.

### 3.1 Dimension 1: Resources

**Formula (Spec):**
```
MPP(you) = lands + 0.9 × rocks + 0.8 × dorks_active
MPPΔ = MPP(you) - MPP(opp)
Fix(you) = min(1, colors_accessible / colors_needed)
Resources = norm(1.0 × MPPΔ + 0.8 × FixΔ, C=6)
```

#### Current Data ✅
- `TurnSummary.landCount` — total lands on battlefield
- Zone contents — can count all permanents

#### Missing Data 🔴

| Requirement | Current | Gap | Impact |
|-------------|---------|-----|--------|
| Differentiate lands/rocks/dorks | Type heuristics only | Need tags: `mana_rock`, `mana_dork` | Cannot compute MPP accurately |
| Mana color production | Not captured | Need `ManaProductionCapability` per permanent | Cannot compute fixing |
| Colors accessible | Not computed | Need untapped source analysis | Cannot compute Fix() |
| Colors needed (deck/commander) | Not in meta | Need deck color identity | Cannot compute Fix() |

#### Implementation Requirements

1. **Card Tagging System:**
   ```json
   {
     "card_index": {
       "c42": {
         "name": "Sol Ring",
         "tags": ["mana_rock", "fast_mana"],
         "mana_ability": {
           "produces": ["colorless", "colorless"],
           "requires_tap": true
         }
       },
       "c43": {
         "name": "Birds of Paradise",
         "tags": ["mana_dork", "fixing"],
         "mana_ability": {
           "produces": ["any"],
           "requires_tap": true
         }
       }
     }
   }
   ```

2. **Per-Turn Mana Summary:**
   ```java
   class PlayerTurnStats {
       // ...existing...
       int manaProducingLands;
       int manaRocks;
       int manaDorks;            // Count only untapped, non-sick
       float manaPotential;      // MPP formula result
       Map<String, Integer> colorsAccessible;  // {W:2, U:3, ...}
       int fixingSources;        // Duals, any-color
   }
   ```

### 3.2 Dimension 2: Board Presence

**Formula (Spec):**
```
V_creature = (P + 0.8×T + keywords) × (0.85 if sick) × (0.90 if tapped)
V_noncreature = lookup table (engine/lock/pw)
BoardPresence = norm(ΣV_you - ΣV_opp, C=12)
```

#### Current Data ✅
- `creaturesOnBattlefield` count
- `permanentsOnBattlefield` count

#### Missing Data 🔴

| Requirement | Current | Gap | Impact |
|-------------|---------|-----|--------|
| Power/Toughness | Not in GameState | Need P/T per creature | Cannot compute creature value |
| Keywords | Not captured | Need keyword set per creature | Cannot compute keyword bonuses |
| Summoning sickness | Not tracked | Need flag per creature | Cannot apply 0.85 multiplier |
| Tapped status | ✅ Available | — | Can apply 0.90 multiplier |
| Noncreature tags | Not in card_index | Need `engine_draw`, `lock_piece`, etc. | Cannot value planeswalkers/enchantments |

#### Implementation Requirements

Extend `ObjectState` with P/T and keywords (see §2.1), then:

```java
class PlayerTurnStats {
    // ...existing...
    float boardPresenceScore;       // Computed from evaluation formula
    int flyingCreatures;            // Evasion threat count
    int protectedPermanents;        // Hexproof/ward count
    float planeswalkerLoyalty;      // Sum of loyalty counters
}
```

### 3.3 Dimension 3: Tempo

**Formula (Spec):**
```
Init_raw = (1 / ClockOpp) - (1 / ClockYou)
Eff_raw = (untapped_you - untapped_opp) × 0.3
Tempo = norm(1.2 × Init_raw + 0.8 × Eff_raw, C=2)
```

#### Current Data ✅
- Life totals per turn
- `damageDealt` per turn

#### Missing Data 🔴

| Requirement | Current | Gap | Impact |
|-------------|---------|-----|--------|
| Untapped mana sources | Not counted separately | Need per-turn untapped land/rock count | Cannot compute efficiency proxy |
| Damage Next Turn (DNT) | Not estimated | Need attacker analysis + evasion | Cannot compute clocks |
| Block estimate | Not computed | Need blocker P/T + combat math | Cannot compute DNT accurately |

#### Implementation Requirements

```java
class PlayerTurnStats {
    // ...existing...
    int untappedLands;
    int untappedManaRocks;
    float estimatedDNT;             // Damage Next Turn
    float clockTurns;               // Life / DNT
}
```

### 3.4 Dimension 4: Card Advantage

**Formula (Spec):**
```
CA_raw = 1.0 × (hand_you - hand_opp) 
       + 0.8 × (3×engine_draw_you - 3×engine_draw_opp)
       + 0.5 × (recastable_you - recastable_opp)
CardAdvantage = norm(CA_raw, C=8)
```

#### Current Data ✅
- `cardsInHand` per player
- `cardsDrawn` per turn

#### Missing Data 🔴

| Requirement | Current | Gap | Impact |
|-------------|---------|-----|--------|
| Engine draw permanents | Not tagged | Need `engine_draw` tag | Cannot compute repeatable CA |
| Recastable resources | Not tracked | Need flashback/escape/jump-start tags | Cannot value graveyard |
| Tutor engines | Not tagged | Need `tutor` tag | Undervalues card selection |

#### Implementation Requirements

Add tags to card_index, then track in turn summary:

```java
class PlayerTurnStats {
    // ...existing...
    int drawEnginesOnBattlefield;
    int tutorEnginesOnBattlefield;
    int recastableCardsInGraveyard;
    float cardAdvantageScore;       // Formula result
}
```

### 3.5 Dimension 5: Life Pressure

**Status:** 🟡 Partially computable from existing data (life totals, damage events), but missing DNT/clock calculations.

See §3.3 Tempo for DNT gaps — same data needed.

### 3.6 Dimension 6: Inevitability

**Formula (Spec):**
```
ESI(you) = 2.0×draw_engines + 1.2×token_engines + 1.3×mana_engines 
         + 1.5×pw_engines + 2.2×locks
Res(you) = 0.5×protected_engines + 0.3×recursion_sources
Inevitability = norm((ESI + Res)_you - (ESI + Res)_opp, C=10)
```

#### Missing Data 🔴

| Requirement | Gap |
|-------------|-----|
| Engine types | Need tags: `engine_draw`, `engine_tokens`, `engine_mana`, `lock_piece` |
| Protection | Need `hexproof`/`ward` keyword tracking |
| Recursion | Need tags: `recursion`, `reanimator` |

### 3.7 Dimension 7: Flexibility

**Formula (Spec):**
```
OC(you) = playable + 0.6×instant + 0.3×modal + 0.4×selection_sources
Flexibility = norm(OC_you - OC_opp, C=6)
```

#### Missing Data 🔴

| Requirement | Current | Gap |
|-------------|---------|-----|
| Playable spells in hand | Not computed | Need mana analysis + hand visibility + card DB lookup |
| Instant-speed interaction | Not tagged | Need `instant` + `removal`/`counterspell` tags |
| Modal spells | Not tagged | Need `modal` tag (or parse "Choose one" in oracle text) |
| Card selection engines | Not tagged | Need `card_selection` tag (scry, surveil, rummage) |

**Complexity:** High — requires full card database with structured abilities.

### 3.8 Dimension 8: Risk / Information

**Formula (Spec):**
Hybrid metric involving hand knowledge, hidden zones, and variance.

#### Current Data ✅
- Hand sizes
- Library sizes (for topdeck risk)

#### Missing Data 🔴
- Revealed cards tracking
- Hidden information flags (opponent hand unknown vs. revealed)
- Variance tags (cards with random effects)

**Note:** Spec section is less detailed; may defer to P3.

### 3.9 Dimension 9: Synergy / Gameplan / Reach

**Formula (Spec):**
Formation-based graph analysis (see spec §7).

#### Missing Data 🔴
- Card formation graphs (entirely unimplemented)
- Combo piece tags
- Payoff/enabler relationships

**Status:** Not feasible without tagging system and formation templates. Priority P2.

### 3.10 Dimension 10: Explosiveness

**Formula (Spec):**
Requires tags for rituals, explosive combos, haste enablers.

#### Missing Data 🔴
- All tags needed (spec suggests 20+ categories)

**Status:** Blocked on tagging system. Priority P2.

---

## 4. Gap Analysis: Learning Helper Statistics

The spec defines four key learning statistics (§8):

### 4.1 Land Drop Rating

**Spec Requirement:** Classify each turn's land drop as "bad", "good", or "super" based on curve analysis.

**Current Implementation:** 🟡 Present in `TurnSummary.PlayerTurnStats.landDropRating` but uses simplistic heuristic.

**Gap:**
- No integration with hand analysis (did player have other lands?)
- No curve analysis (does player need lands now or later?)
- No archetype context (aggro vs. control land needs differ)

**Recommended Enhancement:**
```java
class LandDropAnalysis {
    String rating;                  // "missed", "bad", "good", "super"
    String reason;                  // "On curve", "Behind on mana", "Flood"
    int landsInHand;               // Context for missed drops
    int castableSpellsNextTurn;    // Impact of land drop
    boolean optimalDrop;           // vs. holding for later turn
}
```

### 4.2 Available Mana

**Spec Requirement:** Detailed mana calculation with color breakdown, untapped sources, and floating mana.

**Current Implementation:** 🟡 Present as `availableMana` (int) — no color breakdown.

**Gap:**
- No color-specific counts ({W}, {U}, {B}, {R}, {G}, colorless)
- No untapped vs. total distinction
- No conditional mana (e.g., "if you control a Forest")

**Recommended Enhancement:**
```java
class ManaAvailability {
    Map<String, Integer> untappedSources;   // {W:2, U:1, any:1}
    Map<String, Integer> totalSources;      // Include tapped
    int genericProduction;                  // Colorless mana
    int totalAvailable;                     // Sum
    List<String> conditionalSources;        // "Cavern (if casting Goblins)"
}
```

### 4.3 Cast Options in Hand

**Spec Requirement:** Count of spells in hand that are castable with current/next-turn mana.

**Current Implementation:** ❌ Missing entirely.

**Gap:**
- Requires full hand visibility (OK in training mode, issue in spectator mode)
- Requires card database with mana costs
- Requires mana availability calculation (§4.2)

**Recommended Enhancement:**
```java
class CastOptionsAnalysis {
    int castableNow;                    // With current mana
    int castableNextTurn;               // With +1 land drop
    List<String> castableSpells;        // Card names (for tooltip)
    List<String> unblockedSpells;       // Missing mana colors
}
```

### 4.4 Mana Color Coverage

**Spec Requirement:** Compare colors produced vs. colors needed by deck/hand.

**Current Implementation:** ❌ Missing entirely.

**Gap:**
- Requires deck color identity (not in meta currently)
- Requires per-source color tracking
- Requires hand analysis (colors needed now)

**Recommended Enhancement:**
```java
class ManaColorCoverage {
    Set<String> colorsNeeded;           // Deck requirement
    Set<String> colorsAccessible;       // Current sources
    Set<String> colorsMissing;          // Gap
    float coverageRatio;                // accessible / needed
    boolean fullyFixed;                 // All colors accessible
}
```

---

## 5. Proposed Additional Statistics

Beyond the spec, the following statistics would enhance learning:

### 5.1 Combat Mathematics

```java
class CombatAnalysis {
    int attackers, blockers;
    int evasiveAttackers;               // Flying/unblockable
    int potentialDamage;                // If all attack
    int likelyDamage;                   // After blocks
    int crackbackDamage;                // Opp's lethal next turn
    boolean attackFavorable;            // Recommendation
}
```

**Use Cases:**
- "Attacking into unfavorable blocks" blunder detection
- Combat trick value calculation
- Race analysis

### 5.2 Mana Efficiency

```java
class ManaEfficiency {
    int manaProduced;                   // Total available this turn
    int manaSpent;                      // On spells cast
    int manaWasted;                     // Unspent
    float efficiencyRatio;              // spent / produced
    int landDrops;                      // For curve analysis
}
```

**Use Cases:**
- Identify turns with wasted mana
- Curve optimization feedback
- Spell sequencing improvements

### 5.3 Card Quality Metrics

```java
class CardQualityAnalysis {
    int deadCardsInHand;                // Uncastable now or soon
    int situationalCards;               // Conditional usefulness
    int immediateThreats;               // Can impact board now
    float handQualityScore;             // Weighted sum
}
```

**Use Cases:**
- Mulligan decision training
- Sideboard guidance
- Deck construction feedback

### 5.4 Threat Density

```java
class ThreatDensity {
    int immediateThreats;               // On board now
    int threatsToDraw;                  // In library
    float threatDensity;                // threats / library size
    int answersInHand;                  // Removal/counters
    float threatToAnswerRatio;
}
```

**Use Cases:**
- Control vs. aggro archetype detection
- "Do I race or control?" decision support
- Sideboarding recommendations

### 5.5 Interaction Timing

```java
class InteractionAnalysis {
    int instantSpeedInteraction;        // In hand
    int sorcerySpeedRemoval;            // In hand
    boolean holdingUpMana;              // Untapped at EOT
    List<String> suspectedOpponentCards; // From revealed info
}
```

**Use Cases:**
- "Incorrect spell sequencing" blunder detection
- Bluff detection
- Information asymmetry tracking

### 5.6 Win Condition Tracking

```java
class WinConAnalysis {
    List<String> primaryWinCons;        // From deck/board
    Map<String, Float> winConProgress;  // % toward each wincon
    String activeWinCon;                // Current plan
    int turnsToWin;                     // Estimate
    boolean switchedPlans;              // Flag for turn
}
```

**Use Cases:**
- "Missing lethal" detection
- Inevitability vs. tempo tradeoff guidance
- Post-game plan coherence review

---

## 6. Implementation Roadmap

### Phase 0: Data Model Foundation (P0, 3-4 weeks)

**Goal:** Extend data structures to support evaluation.

**Tasks:**
1. Extend `ObjectState` with P/T, keywords, summoning sickness
2. Add `ManaProductionCapability` descriptor
3. Add `AbilityDescriptor` for activated/triggered abilities
4. Extend `CardDefinition` with structured attributes
5. Create `CardTags` enum and tagging infrastructure

**Deliverable:** Updated JSON schema v1.6.0 with new fields.

### Phase 1: Card Database & Tagging (P0, 6-8 weeks)

**Goal:** Populate card_index with evaluation-ready metadata.

**Tasks:**
1. Import card data from Scryfall API (P/T, keywords, types)
2. Implement rule-based tagging for common categories:
   - Mana producers (lands, rocks, dorks)
   - Removal (destroy, exile, bounce)
   - Card draw engines
   - Combat tricks
3. Manual tagging for edge cases (combo pieces, lock pieces)
4. Validation suite to ensure consistency

**Deliverable:** `CardTagGenerator` utility + 95% coverage on Modern-legal cards.

### Phase 2: Enhanced Turn Statistics (P1, 4-5 weeks)

**Goal:** Compute detailed per-turn metrics.

**Tasks:**
1. Implement mana availability calculator with color breakdown
2. Implement cast options analyzer (requires Phase 1)
3. Implement land drop rating v2 (curve-aware)
4. Implement combat analysis (attackers/blockers/evasion)
5. Add statistics to `TurnSummary`

**Deliverable:** Updated `ReplayNotationExporter` generating full stats.

### Phase 3: Evaluation Engine (P1, 6-8 weeks)

**Goal:** Implement 10-dimension evaluation model.

**Tasks:**
1. Implement dimensions 1-5 (Resources, Board Presence, Tempo, Card Advantage, Life Pressure)
2. Implement dimensions 6-8 (Inevitability, Flexibility, Risk)
3. Defer dimensions 9-10 to Phase 4 (formation-based)
4. Unit tests for each dimension
5. Integration with `TurnEvaluator`

**Deliverable:** `EvaluationEngine` class producing dimension vectors per turn.

### Phase 4: Formation & Synergy (P2, 8-12 weeks)

**Goal:** Implement card formation system (spec §7).

**Tasks:**
1. Design formation graph data structure
2. Implement formation template library (combo patterns, synergy pairs)
3. Implement formation detection from board state
4. Implement dimensions 9-10 (Synergy, Explosiveness)
5. Integration with evaluation engine

**Deliverable:** `FormationAnalyzer` + template library.

### Phase 5: Blunder Detection (P2, 4-6 weeks)

**Goal:** Implement spec §9 blunder detectors.

**Tasks:**
1. Attacking into unfavorable blocks
2. Forgotten activated abilities
3. Incorrect spell sequencing
4. Missing lethal
5. Missed triggers
6. Inefficient mana tapping

**Deliverable:** `BlunderDetector` + integration with `CGameLearningUI`.

### Phase 6: Learning Helper UI (P1, 3-4 weeks)

**Goal:** Display new statistics in Game Learning Viewer.

**Tasks:**
1. Update evaluation panel with 10 dimensions
2. Add mana breakdown widget
3. Add cast options tooltip
4. Add blunder highlights in event list
5. Add combat math helper panel

**Deliverable:** Enhanced `VGameLearningUI` with full stat display.

---

## 7. Data Model Extensions

### 7.1 Proposed JSON Schema v1.6.0

**New Top-Level Sections:**

```json
{
  "format": "mtg-replay",
  "version": "1.6.0",
  "spec_version": "1.6.0",
  
  "card_index": {
    "c1": {
      "name": "Lightning Bolt",
      "type": "Instant",
      "mana_cost": "{R}",
      "oracle_id": "...",
      
      // NEW: Structured attributes
      "power": null,
      "toughness": null,
      "keywords": [],
      "color_identity": ["R"],
      "tags": ["removal", "reach_burn", "instant_speed"],
      
      // NEW: Abilities
      "abilities": [
        {
          "type": "spell_effect",
          "cost": "{R}",
          "effect": "Deal 3 damage to any target"
        }
      ]
    },
    "c2": {
      "name": "Birds of Paradise",
      "type": "Creature — Bird",
      "mana_cost": "{G}",
      "power": 0,
      "toughness": 1,
      "keywords": ["flying"],
      "color_identity": ["G"],
      "tags": ["mana_dork", "fixing"],
      
      "mana_ability": {
        "produces": ["any"],
        "requires_tap": true,
        "amount": 1
      }
    }
  },
  
  "initial_state": {
    "objects": {
      "c42": {
        "card_ref": "Birds of Paradise",
        "zone": "P1:battlefield",
        "controller": "P1",
        "owner": "P1",
        "tapped": false,
        
        // NEW: Combat/status flags
        "summoning_sick": true,
        "can_attack": false,
        "can_block": true,
        
        // NEW: Current stats (for modifications)
        "power": 0,
        "toughness": 1,
        "keywords": ["flying"],
        "counters": {}
      }
    }
  },
  
  // NEW: Per-turn detailed statistics
  "per_turn_summary": [
    {
      "turn": 1,
      "active_player": "P1",
      "players": {
        "P1": {
          // ...existing fields...
          
          // NEW: Mana detail
          "mana_detail": {
            "untapped_sources": {"G": 1},
            "total_sources": {"G": 1},
            "generic_production": 0,
            "total_available": 1
          },
          
          // NEW: Cast options
          "cast_options": {
            "castable_now": 1,
            "castable_next_turn": 3,
            "castable_spells": ["Birds of Paradise"]
          },
          
          // NEW: Board analysis
          "board_detail": {
            "mana_producing_lands": 1,
            "mana_rocks": 0,
            "mana_dorks": 0,
            "draw_engines": 0,
            "flying_creatures": 0,
            "board_presence_score": 0.0
          },
          
          // NEW: Combat metrics
          "combat": {
            "attackers": 0,
            "blockers": 0,
            "evasive_attackers": 0,
            "estimated_dnt": 0,
            "clock_turns": 99
          }
        }
      }
    }
  ],
  
  // NEW: Evaluation vectors
  "per_turn_evaluation": [
    {
      "turn": 1,
      "human_player": "P1",
      "dimensions": {
        "resources": 0.0,
        "board_presence": 0.0,
        "tempo": 0.0,
        "card_advantage": 0.0,
        "life_pressure": 0.0,
        "inevitability": 0.0,
        "flexibility": 0.0,
        "risk_information": 0.0,
        "synergy_gameplan": 0.0,
        "explosiveness": 0.0
      },
      "critical_score": 0.0
    }
  ],
  
  // NEW: Blunder reports
  "blunders": [
    {
      "turn": 5,
      "player": "P1",
      "type": "missed_lethal",
      "severity": "critical",
      "event_index": 142,
      "description": "Had exactly 12 damage available, opponent at 12 life",
      "alternative": "Attack with all creatures + cast both burn spells",
      "impact": -100.0
    }
  ]
}
```

### 7.2 Java Model Classes (Additions)

**New file:** `forge-game/src/main/java/forge/game/log/model/TurnEvaluation.java`

```java
package forge.game.log.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 10-dimensional state evaluation for a single turn.
 * Spec: mtg-state-evaluation-spec.md §5.2
 */
public class TurnEvaluation {
    private int turn;
    private String evaluatedPlayer;
    private Map<String, Float> dimensions;
    private float criticalScore;    // Aggregate importance
    
    public TurnEvaluation() {
        this.dimensions = new HashMap<>();
    }
    
    public void setDimension(String name, float value) {
        this.dimensions.put(name, value);
    }
    
    public float getDimension(String name) {
        return dimensions.getOrDefault(name, 0f);
    }
    
    // Getters/setters omitted for brevity
}
```

**New file:** `forge-game/src/main/java/forge/game/log/model/BlunderReport.java`

```java
package forge.game.log.model;

/**
 * Record of a detected mistake or suboptimal play.
 * Spec: mtg-state-evaluation-spec.md §9
 */
public class BlunderReport {
    private int turn;
    private String player;
    private String type;            // "missed_lethal", "inefficient_tapping", etc.
    private String severity;        // "critical", "high", "medium", "low"
    private int eventIndex;
    private String description;
    private String alternative;
    private float impact;           // Negative value (delta in evaluation)
    
    // Getters/setters omitted
}
```

**Extend:** `forge-game/src/main/java/forge/game/log/model/TurnSummary.java`

```java
// Add to TurnSummary.PlayerTurnStats
public static class PlayerTurnStats {
    // ...existing fields...
    
    // NEW: Detailed mana
    private ManaDetail manaDetail;
    
    // NEW: Cast options
    private CastOptions castOptions;
    
    // NEW: Board detail
    private BoardDetail boardDetail;
    
    // NEW: Combat metrics
    private CombatMetrics combat;
}

public static class ManaDetail {
    private Map<String, Integer> untappedSources;
    private Map<String, Integer> totalSources;
    private int genericProduction;
    private int totalAvailable;
}

public static class CastOptions {
    private int castableNow;
    private int castableNextTurn;
    private List<String> castableSpells;
}

public static class BoardDetail {
    private int manaProducingLands;
    private int manaRocks;
    private int manaDorks;
    private int drawEngines;
    private int flyingCreatures;
    private float boardPresenceScore;
}

public static class CombatMetrics {
    private int attackers;
    private int blockers;
    private int evasiveAttackers;
    private float estimatedDNT;
    private float clockTurns;
}
```

---

## 8. Conclusion

### Summary of Priorities

| Priority | Category | Effort | Impact | Start After |
|----------|----------|--------|--------|-------------|
| **P0** | Data model extensions | 3-4 weeks | Foundational | Immediate |
| **P0** | Card tagging system | 6-8 weeks | Critical for all evaluation | Phase 0 |
| **P1** | Enhanced turn statistics | 4-5 weeks | High learning value | Phase 1 |
| **P1** | Evaluation engine (dims 1-8) | 6-8 weeks | Core feature | Phase 1 |
| **P1** | Learning helper UI | 3-4 weeks | User-facing polish | Phase 3 |
| **P2** | Formation system | 8-12 weeks | Advanced analysis | Phase 3 |
| **P2** | Blunder detection | 4-6 weeks | Quality-of-life | Phase 3 |

### Total Estimated Effort

**Core Path (P0-P1):** ~22-30 weeks (5-7 months)  
**Full Implementation (P0-P2):** ~34-50 weeks (8-12 months)

### Quick Wins (Next Sprint)

1. **Add P/T to ObjectState** — 2 days, enables combat math
2. **Add tapped/untapped mana count to TurnSummary** — 1 day, improves tempo analysis
3. **Import Scryfall card data** — 3 days, enables tag population
4. **Implement basic tagging for lands/creatures** — 5 days, unblocks Resources dimension

### Long-Term Vision

With full implementation, Forge will have:
- ✅ Industry-leading game replay accuracy
- ✅ Explainable AI evaluation comparable to chess engines
- ✅ Automated coaching for player improvement
- ✅ Dataset generation for ML research
- ✅ Puzzle/scenario creation from game positions

This positions Forge as the definitive platform for competitive MTG learning and analysis.

---

**Document End**  
For questions or clarifications, contact the Forge development team or open an issue on GitHub.

