# Forced Play Sequence & AI Controller Architecture

This document provides a comprehensive technical guide to Forge's **Forced Play Sequence** mechanism, detailing how recorded game events and scenario scripts are parsed, translated to runtime seats, executed by `AiController`, and coordinated with the rules engine.

---

## 1. Architectural Overview

The **Forced Play Sequence** system allows Forge to deterministically guide player and AI actions according to a pre-recorded sequence (from MTG Replay Notation JSON or Scenario JSON). 

```
┌─────────────────────────────────┐
│  MTG Replay / Scenario JSON     │
│  (events[] / log_l1[])          │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│  DemoPlaySequenceExtractor /    │  Filters CAST, ACTIVATE, PLAY_LAND
│  ReplayLogParser                │  Resolves Card IDs -> Names & Sacrifices
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│  Seat Translation               │  Translates P1/P2 -> Runtime Lobby Names
│  (idToLobbyName)                │  (e.g., "Ai(1)-killriam", "Player 1")
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│  GameRules                      │  Stores forcedPlaySequence &
│  (Game state rules object)      │  forcedPlaySequenceSacrifice maps
└────────────────┬────────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────────┐
│  AiController.chooseSpellAbilityToPlay() (Case 1)      │
│  - Land Play Execution                                 │
│  - Zone Search (Hand, Command, Board, Grave, Exile)    │
│  - Mandatory Sacrifice Cost Target Injection           │
│  - Phase-Aware Soft Enforcement & Give-Up              │
│  - Heuristic Fallback (Case 2+)                        │
└────────────────────────────────────────────────────────┘
```

---

## 2. Event Recording & Extraction Pipeline

### 2.1 Event Classification in `ReplayEventLogger`

During gameplay, [`ReplayEventLogger`](../forge-game/src/main/java/forge/game/log/ReplayEventLogger.java) intercepts `GameEventSpellAbilityCast` events and classifies them into standard replay types:

| Ability Characteristic | Emitted Event Type | Role in Scenarios |
| :--- | :--- | :--- |
| `sa.isSpell()` | `CAST` | Forced player spell cast |
| `sa.isLandAbility()` | `PLAY_LAND` | Forced player land drop |
| `sa.isTrigger()` | `TRIGGER` | **Automatic engine trigger** (excluded from forced scripts) |
| `sa.isActivatedAbility()` | `ACTIVATE` | Forced player activated ability (e.g. equipment, board abilities) |

> **Critical Distinction:** Automatic triggers (such as *Evolve*, *ETB triggers*, *Historic cast triggers*, and *Exploit*) fire automatically through rules engine state transitions. They are recorded as `TRIGGER` events and must never be extracted into scenario `events[]` as player-initiated actions.

### 2.2 Sequence Extraction in `DemoPlaySequenceExtractor`

[`DemoPlaySequenceExtractor`](../forge-gui/src/main/java/forge/game/DemoPlaySequenceExtractor.java) extracts a player's recorded actions:
1. Filters the event stream for `CAST`, `ACTIVATE`, and `PLAY_LAND` where `actor == playerId`.
2. Resolves object IDs (e.g. `c10`) to canonical card names using `card_index`.
3. Extracts additional cost data, such as `data.choices.sacrifice` (e.g. sacrificing *The Pride of Hull Clade* for *Metamorphosis*).
4. Generates the scenario `events[]` array.

---

## 3. Runtime Initialization & Seat Translation

### 3.1 Translating Scenario Player IDs to Lobby Names

In scenarios and full replay simulations, players are identified abstractly (e.g. `P1`, `P2`), but Forge's runtime engine keys seats by their lobby names (e.g. `Ai(1)-killriam - Horror: Dead is not an end`, `Human Player`).

1. [`ReplayLogParser.ScenarioInfo.buildForcedPlaySequenceForLobbyNames`](../forge-gui/src/main/java/forge/game/ReplayLogParser.java):
   Translates `{ "P1": ["Command Tower", "Shield Sphere"] }` to `{ "Ai(1)-killriam...": ["Command Tower", "Shield Sphere"] }`.
2. [`ReplayLogParser.ScenarioInfo.buildForcedPlaySequenceSacrificeForLobbyNames`](../forge-gui/src/main/java/forge/game/ReplayLogParser.java):
   Translates corresponding sacrifice targets `{ "P1": [null, "The Pride of Hull Clade"] }`.
3. Attached to the active match via:
   ```java
   game.getRules().setForcedPlaySequence(forcedSeqMap);
   game.getRules().setForcedPlaySequenceSacrifice(forcedSacMap);
   ```

---

## 4. How `AiController` Executes Forced Plays (Case 1)

During priority cycles, Forge calls [`AiController.chooseSpellAbilityToPlay()`](../forge-ai/src/main/java/forge/ai/AiController.java). Before running heuristic AI evaluations, the engine checks **Case 1 (Forced Play Sequence)**:

```java
final Map<String, List<String>> forcedSeq = game.getRules().getForcedPlaySequence();
final List<String> seq = forcedSeq.get(player.getLobbyPlayer().getName());
```

### 4.1 Step-by-Step Priority Execution

```
                       Priority Check Triggered
                                  │
                                  ▼
                    Is forcedSeq queue non-empty?
                                  │
                       Yes ───────┴─────── No ──► Normal AI Heuristics
                        │
                        ▼
                 Peek Queue Head (nextCardName)
                        │
                        ├───────────────────────────────────────┐
                        ▼                                       ▼
             Is nextCardName a Land?               Is nextCardName Castable/Activatable?
            (Check available land drops)            (Hand, Command, Battlefield, Grave, Exile)
                        │                                       │
                   Yes ─┴─ No                              Yes ─┴─ No
                    │       │                               │       │
                    │       └───────────────┬───────────────┘       │
                    ▼                       │                       ▼
          Execute Land Ability              ▼             Is Turn Ending & Expired?
          & Pop Queue Head             Execute SA         (isMyTurn && isTurnEnding
                    │               & Pop Queue Head       && turn > firstSeenTurn)
                    │                       │                       │
                    │                       │                  Yes ─┴─ No
                    ▼                       ▼                   │       │
             Return Ability          Return Ability             ▼       ▼
                                                          Skip Head   Defer to Next
                                                          & Pop Seq   Priority Window
```

#### Step 1: Land Drop Execution
* Uses `ComputerUtilAbility.getAvailableLandsToPlay(game, player)` to test if a land drop is legally available.
* If `land.getName().equals(nextCardName)`:
  1. Retrieves land ability via `land.getAllPossibleAbilities(player, true)`.
  2. Pops the head from `seq` and `sacSeq`.
  3. Returns the land ability to execute immediately.

#### Step 2: Cross-Zone Card & Ability Discovery
To support spells, commanders, battlefield activations, and graveyard mechanics, `AiController` searches across all relevant game zones:
```java
final CardCollection castableZoneCards = new CardCollection(player.getCardsIn(ZoneType.Hand));
castableZoneCards.addAll(player.getCardsIn(ZoneType.Command));     // Commanders
castableZoneCards.addAll(player.getCardsIn(ZoneType.Battlefield)); // Activated abilities, Equip
castableZoneCards.addAll(player.getCardsIn(ZoneType.Graveyard));   // Flashback, Escape, Scavenge
castableZoneCards.addAll(player.getCardsIn(ZoneType.Exile));       // Adventure, Foretell
```
For each card matching `nextCardName`, the AI evaluates `card.getAllPossibleAbilities(player, false)`. If `sa.canPlay()` is true:
1. Pops the head from `seq` and `sacSeq`.
2. Arms any recorded sacrifice cost target (`pendingForcedSacrificeCardName` / `pendingForcedSacrificeTargetName`).
3. Returns `singleSpellAbilityList(sa)` for immediate resolution.

#### Step 3: Mandatory Additional Cost Targeting
When casting spells with mandatory additional costs (e.g. *Metamorphosis*: *"As an additional cost to cast this spell, sacrifice a creature"*):
1. `AiController` arms `pendingForcedSacrificeTargetName`.
2. When the cast pipeline calls [`AiCostDecision.visit(CostSacrifice)`](../forge-ai/src/main/java/forge/ai/AiCostDecision.java), `AiController.chooseSacrificeType` matches the pending sacrifice name and selects the recorded target (e.g. *The Pride of Hull Clade*) instead of guessing.

#### Step 4: Phase-Aware Soft Enforcement & Give-Up
If the head card is currently uncastable (e.g. sorcery-speed card checked during Upkeep/Combat, or insufficient mana):
* **Soft Enforcement (Deferred):** The card remains at the head of the queue and is retried on subsequent priority windows.
* **Give-Up Condition:** To avoid infinite stalling while allowing full opportunity across phases and turns:
  ```java
  final boolean isTurnEnding = currentPhase == PhaseType.END_OF_TURN || currentPhase == PhaseType.CLEANUP;
  if (isMyTurn && currentTurn > forcedSeqHeadFirstSeenTurn && isTurnEnding) {
      seq.remove(0);
      if (sacSeq != null && !sacSeq.isEmpty()) sacSeq.remove(0);
      forcedSeqHeadCardName = null;
      LOG.info("Scripted play skipped for {}: '{}' was never castable during turn {} - moving on.",
              lobbyName, nextCardName, forcedSeqHeadFirstSeenTurn);
  }
  ```
  This ensures:
  1. Entries are **never discarded in Upkeep or Draw** before Main Phase 1 is reached.
  2. The entry gets every priority window of its initial turn PLUS a full subsequent own-turn across Main 1 and Main 2 before being retired.
  3. Multi-player / 4-player Commander turn cycles (where turn counters increment by `+4`) are handled properly without false early timeouts.

---

## 5. Human Seat Integration

For human players playing through a scripted scenario:
1. [`CPrompt.getScriptedSequenceHint()`](../forge-gui-desktop/src/main/java/forge/screens/match/controllers/CPrompt.java) displays the next forced play as an informational hint in the UI prompt bar.
2. The human player retains full freedom to play or deviate.
3. When the human plays the matching card, [`GameRules.popForcedPlayIfMatches(lobbyName, cardName)`](../forge-game/src/main/java/forge/game/GameRules.java) advances the queue in sync with their actions.

---

## 6. Summary of Key Classes & Interfaces

| Class | Package | Responsibility |
| :--- | :--- | :--- |
| `ReplayEventLogger` | `forge.game.log` | Records game events, distinguishes `CAST`, `ACTIVATE`, `PLAY_LAND`, and `TRIGGER`. |
| `DemoPlaySequenceExtractor` | `forge.game` | Extracts player-driven actions from replay JSON into scenario `events[]`. |
| `ReplayLogParser` | `forge.game` | Parses scenario files and builds seat-mapped forced play queues. |
| `GameRules` | `forge.game` | Holds the active `forcedPlaySequence` and `forcedPlaySequenceSacrifice` maps. |
| `AiController` | `forge.ai` | Consumes forced sequences in Case 1, manages cross-zone discovery, sacrifice targeting, and give-up timing. |
| `CPrompt` | `forge.screens.match.controllers` | Surfaces scripted sequence hints to human players in the GUI. |
