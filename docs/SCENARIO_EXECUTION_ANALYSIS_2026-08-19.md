# Scenario Execution Analysis & Problem Solving Log (2026-08-19)

This document provides a continuous tracking log and technical analysis of scenario playback, forced play execution, and mana engine interactions in Forge.

---

## 1. Executive Summary & Progression of Fixes

| Stage | Issue Identified | Root Cause | Solution Implemented | Status |
| :--- | :--- | :--- | :--- | :---: |
| **Stage 1** | Scenarios stalling on Turns 5 & 7 | `ReplayEventLogger` recorded automatic triggered abilities (*Evolve*, *Historic*, *ETB*) as `ACTIVATE` events. | Fixed `ReplayEventLogger` to emit `TRIGGER`, and updated extractor to filter them out. | **Resolved** |
| **Stage 2** | Premature play skip at start of turns in 4-player Commander | Give-up condition evaluated `currentTurn > firstSeenTurn + 2`, which falsely fired during Upkeep in +4 turn Commander rotations. | Gated give-up strictly to `isTurnEnding` (`END_OF_TURN` / `CLEANUP`). | **Resolved** |
| **Stage 3** | Script cascade / dumping on Turn 1 | `AiController` called `seq.remove(0)` before checking whether mana could actually be paid. | Added `ComputerUtilMana.canPayManaCost(...)` check before popping the forced queue. | **Resolved** |
| **Stage 4** | Scenario missing in Constructed/Commander dropdown | `PlayerPanel.java` only inspected `.dck` metadata, ignoring `scenario.deck_id` references on disk. | Added two-way discovery in `PlayerPanel.java` with deduplication and fuzzy name matching. | **Resolved** |
| **Stage 5** | *Rush of Knowledge* failing on Turn 5 | `Arbor Adherent` has two mana abilities. The 1-mana tap ability was selected over the 15-mana ($X = \text{toughness}$) ability. | Prioritize higher mana-yield abilities on the same permanent during mana payment. | **In Progress** |

---

## 2. Detailed Breakdown of Latest Game Log (`gamelog_Constructed_2026-08-19_17-49-00.txt`)

### 2.1 Timeline of Executed Plays

```
[Turn 1]
  1. Command Tower (PLAY_LAND)  -> SUCCESS
  2. Shield Sphere (CAST)        -> SUCCESS (0 mana)
  3. Ornithopter (CAST)          -> SUCCESS (0 mana)
  4. Phyrexian Walker (CAST)     -> SUCCESS (0 mana)
  5. The Pride of Hull Clade (CAST) -> SUCCESS (Cast from Command Zone, {G} from Command Tower)

[Turn 3]
  6. Metamorphosis (CAST)        -> SUCCESS (Sacrificed Commander, generated 12 green mana)
  7. Arbor Adherent (CAST)       -> SUCCESS (Cast from Metamorphosis mana)
  8. Psychosis Crawler (CAST)    -> SUCCESS (Cast from Metamorphosis mana)
  9. Jamie McCrimmon (CAST)      -> SUCCESS (Cast from Metamorphosis mana)

[Turn 5]
 10. The Pride of Hull Clade (CAST) -> SUCCESS (Recast from Command Zone; Jamie McCrimmon pumped +11/+11)
 11. Rush of Knowledge (CAST)    -> FAILED MANA PAYMENT (Arbor Adherent tapped for 1 {U} instead of 15 {U})
 12. City of Shadows (PLAY_LAND) -> SUCCESS

[Turn 7]
 13. Walking Bulwark (CAST)      -> SUCCESS (Cast & resolved)

[Turn 9]
 14. Duelist of the Mind (CAST)  -> SKIPPED (Never reached hand because Rush of Knowledge did not draw)
 15. Empyrial Plate (CAST)       -> SUCCESS (Cast from hand)

[Turn 11]
 16. Empyrial Plate (ACTIVATE)   -> SUCCESS (Equipped to Arbor Adherent)
```

---

## 3. Mana Ability Analysis: *Arbor Adherent* & *Rush of Knowledge*

### 3.1 Card Rules & Mana Costs

1. **`Rush of Knowledge`**:
   - **Mana Cost:** `{4}{U}` (CMC = 5, exactly **1 Blue mana** required).
   - **Effect:** Draw cards equal to the highest mana value among permanents you control (draws 11 cards with *The Pride of Hull Clade*).

2. **`Arbor Adherent`**:
   - **Types:** Creature — Dog Druid (2/4)
   - **Ability 1:** `{T}: Add one mana of any color.` (Yield: **1 mana**)
   - **Ability 2:** `{T}: Add X mana of any one color, where X is the greatest toughness among other creatures you control.` (Yield: **$X = 15$ mana** with *The Pride of Hull Clade*).

### 3.2 Root Cause of the Mana Payment Failure

In [`ComputerUtilMana.java`](../forge-ai/src/main/java/forge/ai/ComputerUtilMana.java):
* When estimating available mana in `getAvailableManaEstimate`, Forge calculated $X = 15$, returning 16 total available mana.
* However, when resolving `payManaCost` to actually tap permanents on the battlefield:
  1. Multiple mana abilities on the **same card** were compared at line 218 via `ability1.compareTo(ability2)`.
  2. In `SpellAbility.compareTo()`, abilities are compared alphabetically by description:
     - Ability 1: `"Add one mana of any color."`
     - Ability 2: `"Add X mana of any one color, where X is..."`
  3. Because `"Add one"` sorts alphabetically before `"Add X"`, Forge selected and tapped **Ability 1** (producing only **1 {U}**).
  4. Tapping Ability 1 exhausted the tap cost `{T}`, leaving *Arbor Adherent* tapped.
  5. Forge was left with 1 mana toward a 5-mana cost (`{4}{U}`), causing `payManaCost` to fail and return *Rush of Knowledge* to hand.

---

## 4. Resolution Plan

1. **Same-Card Mana Ability Comparator:**
   Update `ComputerUtilMana.java` when comparing multiple mana abilities on the same card so that abilities producing **greater total mana amount** ($X > 1$) are sorted before simple 1-mana abilities.
2. **Resulting Execution:**
   - On Turn 5, *Arbor Adherent* activates Ability 2 for **15 {U}**.
   - 5 mana pays for *Rush of Knowledge* (`{4}{U}`).
   - 10 {U} remains in the mana pool.
   - *Rush of Knowledge* draws 11 cards, putting *Duelist of the Mind*, *Crashing Drawbridge*, and subsequent combo pieces directly into hand.
   - The entire 17-action sequence completes cleanly.
