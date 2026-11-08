# Concept: Deck-Specific Influence on AI Target Selection

**Status:** concept / analysis (2026-10-18). Based on reading the code; no game was run to measure behavior.
**Goal:** let a deck author influence *which target* the Forge AI picks when it casts a targeted spell, using annotations that travel with the deck.

---

## 1. Current behavior (vanilla flow)

| Step | Where | What happens |
|------|-------|--------------|
| 1. Ordering | `AiController` ~L1819, `ComputerUtilAbility.saEvaluator` | All playable spells/abilities are sorted, best first. |
| 2. Deployment guard | `AiController.java:1922` | **Deck hook 1.** A card whose role has a failing constraint is skipped this priority. |
| 3. Cast decision | `canPlayAndPayFor` → handler `checkApiLogic` (e.g. `DestroyAi.java:107`) | Candidate list is built and filtered (see below). |
| 4. Target pick | `ComputerUtilCard.getBestRemovalTargetAI` (L600) | **Deck hook 2** if a `target_rankings` rule exists for the source card; otherwise `evaluateRemovalTargetPriority`. |
| 5. Hold gate | `DestroyAi.java:241`, `ComputerUtilCard.useRemovalNow` | Single-target spells can still be held after a target was chosen. Guidance cannot override this. |

**Candidate filtering in step 3 (before any ranking):** opponents' permanents only; drops indestructible, regenerating/shielded, sacrifice-in-response (`SacMe`), undying, "dies this turn anyway"; applies `MustTarget`.

**Vanilla target score (step 4):** creature → `evaluateCreature`; land → land-removal priority; other → `50 + 30*CMC` (+10 per loyalty); +30 token; + board-threat/4 for opponent permanents.

## 2. Existing deck-level mechanism (already in the fork)

- Deck `[metadata]`: `AiHints=DecklistSpec$<path to json>` → `Deck.getDecklistSpecPath()`.
- `DeckRulesLoader.loadAiGuidanceIfNeeded` parses `deck_rules.ai_guidance` → `AiGuidanceProfile`, held by `AiController` (`initGuidanceProfile`, called from `PlayerControllerAi.java:1422`).
- Blocks: `role_bindings` (card → `primary_role`, deployment constraints), `target_rankings` (per `source_card`: `vetoes[]`, `evaluation_ladder[]` with scores), `canonical_threat_catalog` (tier lists), `tactical_sequences`, `evaluation_profile` (stage weights scale ladder steps that declare a `dimension`).
- Condition fields: `target.has_indestructible` (also true for hexproof), `target.canonical_threat_tier`, `target.role`, `target_spell.*` (counterspells), `state.game_stage`, `battlefield.*`, `hand.roles`, `opponent_open_mana`, `resources.available_mana`, `state.self_board_presence_ahead`, `active_engine_core_count`. Combinators: `all_of`/`any_of`/`none_of`.
- Decisions are logged as `GameEventAiGuidanceDecision` (`target_selected`, `target_fallback`, `target_all_vetoed`, `deployment_guard_blocked`).
- Spec: `mtg-replay-notation/spec/ai-play-guidance-spec.md`, `forge-integration-guide.md` §12.

Example (derived from `src/test/resources/ai_guidance/swords_target_ranking.json`):

```json
{"deck_rules":{"ai_guidance":{
  "canonical_threat_catalog":{"tier_2_engine":["Korvold, Fae-Cursed King"]},
  "role_bindings":{"cards":{"Ashnod's Altar":{"primary_role":"engine_core"}}},
  "target_rankings":[{"source_card":"Swords to Plowshares",
    "vetoes":[{"condition":{"field":"target.has_indestructible","op":"==","value":true}}],
    "evaluation_ladder":[
      {"condition":{"field":"target.canonical_threat_tier","op":"==","value":"tier_2_engine"},"score":70},
      {"condition":{"field":"target.role","op":"==","value":"engine_core"},"score":60}]}]}}}
```

## 3. Semantics and limits

- **Coverage:** only `DestroyAi`, `DamageDealAi`, `ChangeZoneAi` (exile/bounce) and `CounterAi` consult the profile. No hook found for auras/equipment, pump, tap, gain-control, fight, counters, animate (from call-site grep; not run-tested).
- **Pool is fixed first:** guidance re-ranks only what survives step 3 filtering.
- **Veto-all = hold:** a rule exists and every candidate is vetoed → no target → spell not cast. Intentional (`hasTargetRankingRule` vs `chooseGuidedRemovalTarget` null split).
- **Scores are local:** they rank targets of one spell only; they do not affect spell ordering (step 1).
- **Ladder:** first matching step per candidate wins; highest score across candidates wins.
- **Matching:** by `source_card` name only; the categorical `applies_to` form from the spec is skipped silently.
- **Unknown fields fail open** (condition treated as true, WARN logged) — a typo silently disables a veto.
- **Unverified:** `DestroyAi.java:116` returns early when an `AILogic` param already chose enough targets, which may bypass guidance for such cards.

## 4. Suspected defect

`AiGuidanceProfile.chooseGuidedRemovalTarget` (L428): when no ladder step matches any surviving candidate it falls back to `ComputerUtilCard.getWorstAI(survivors)`. Vanilla removal picks the *highest*-priority target. A sparse ladder therefore makes the AI aim removal at the weakest permanent whenever none of the ladder steps match. Comments/tests call this the "vanilla fallback", which it is not. Not yet demonstrated by a test.

## 5. Extension options

| # | Option | Effort | Notes |
|---|--------|--------|-------|
| A | Use the existing JSON via `DecklistSpec$` | none | Path resolves from working dir (`forge-gui/`) unless absolute. |
| B | Compact inline token, e.g. `AiHints=TargetPriority$Swords to Plowshares:Korvold>Ashnod's Altar` | small | Same pattern as `MulliganOverride$` (`DeckRulesConfig.fromInlineHints`); no companion file. |
| C | Fix the fallback to vanilla best-target scoring | trivial | Add a test with a ladder that matches none of the candidates. |
| D | Route aura/pump/tap/gain-control/fight handlers through one shared target helper that consults the profile | medium | Needs per-handler review; their ranking differs from removal. |
| E | Categorical `applies_to` matching | medium | Could derive from `ApiType` + target zone/type instead of mechanic-group metadata. |

**Recommended order:** C → B → D.

## 6. Verification approach

- Unit level: call the target-selection method directly with a real `Player`/`Game`/`SpellAbility` (as `AiGuidanceTargetRankingTest` does); do not assert on post-turn board state, because the independent "cast now?" decision is non-deterministic (see integration guide §12.7.4).
- Behavioral: run `sim -n <N>` with and without the ruleset and compare the logged `target_*` events and win rates.
- Any GUI-side authoring change needs the live in-process GUI check (see project notes).
