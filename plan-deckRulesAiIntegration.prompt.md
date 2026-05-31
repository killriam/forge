# Plan: Case 1 — Replay Instructions for the AI (`forcedPlaySequence`)

## Goal

Replay a recorded game **deterministically**: the AI follows the exact play sequence
captured in a replay JSON (CAST / ACTIVATE events in order), without breaking game rules.

Library order is already handled by `ReplayLibraryReorderer`.  
This feature adds forced **play decisions** on top — making AI turns mirror the original human choices.

---

## Background

### What already exists

| Infrastructure | File | What it does |
|---------------|------|-------------|
| `replayLogPath` | `GameRules` | Path to replay JSON consumed by `GameAction` |
| `replayMode` | `GameRules` | When `true`: forces library order from replay |
| `forcedLibraryOrder` | `GameRules` | `playerLobbyName → ordered card names` for draw sequence |
| `ReplayLibraryReorderer` | `forge-game` | Parses DRAW events, reorders libraries after shuffle |
| `ReplayLogParser` | `forge-gui` | Parses replay JSON, reconstructs `Deck` objects |
| `SimulateMatch` (CLI `-r`) | `forge-gui-desktop` | Sets `replayLogPath`; runs headless AI game with same library order |

### What is missing

The AI still makes **independent decisions** after library order is fixed.  
Cards may be drawn in the right order but cast in a completely different order —
so the replay drifts from the original game within 1–2 turns.

### Replay JSON event format (L1Event)

Relevant fields for CAST/ACTIVATE events:

```json
{
  "i": 42,
  "t": "T3.MP1:2",
  "a": "P1",
  "type": "CAST",
  "data": {
    "card": "c17",
    "card_name": "Lightning Bolt",
    "targets": ["P2"]
  }
}
```

- `a` — actor player ID (`P1`, `P2`, …)
- `type` — `"CAST"` or `"ACTIVATE"`
- `data.card` — card object ID (`c<n>`)
- `data.card_name` — human-readable card name (preferred for matching)
- `data.targets` — optional list of target object IDs / player IDs

Player ID → lobby name mapping comes from `meta.players.<id>.name`.

---

## Design

### New field: `GameRules.forcedPlaySequence`

```java
// forge-game/src/main/java/.../game/GameRules.java
private Map<String, List<String>> forcedPlaySequence; // lobbyName → ordered card names
```

- Plain `Map<String, List<String>>` — no forge-ai types, no circular deps.
- Populated at match setup time (before `startGame()`).
- Consumed by the AI controller at decision time (mutable — entries are popped).

### Enforcement Model: Soft (Try-Best-Effort)

The forced sequence is a **hint**, not a hard constraint:

1. When `chooseSpellAbilityToPlay()` is called, check if the next card in the sequence is in hand **and** castable right now.
2. If yes → cast it (pop from sequence).
3. If no → **fall through to normal AI** (do not block the game).

This ensures the game never stalls because of a race condition or minor state divergence.

---

## Implementation Steps

### Step 1 — `GameRules.java` (forge-game)

Add field + getter/setter:

```java
// after existing forcedLibraryOrder field
private Map<String, List<String>> forcedPlaySequence;

public Map<String, List<String>> getForcedPlaySequence() {
    return forcedPlaySequence;
}
public void setForcedPlaySequence(Map<String, List<String>> seq) {
    this.forcedPlaySequence = seq;
}
```

No schema impact. The field is `null` by default — no existing code path is affected.

---

### Step 2 — `ReplayPlaySequenceParser` (new class, forge-gui)

New utility class that reads CAST/ACTIVATE events from a replay JSON file and builds
the `Map<String, List<String>>` needed by `GameRules`.

**Location:** `forge-gui/src/main/java/forge/game/ReplayPlaySequenceParser.java`

```java
package forge.game;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Parses a replay JSON file and extracts the sequence of CAST/ACTIVATE events
 * per player, for use with {@link GameRules#setForcedPlaySequence(Map)}.
 *
 * <p>Player IDs (P1, P2, …) are mapped to lobby names via {@code meta.players}.
 *
 * @see ReplayLogParser
 * @see GameRules
 */
public class ReplayPlaySequenceParser {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayPlaySequenceParser.class);
    private static final Set<String> PLAY_EVENT_TYPES =
            new HashSet<>(Arrays.asList("CAST", "ACTIVATE", "PLAY_LAND"));

    /**
     * Parse {@code replayFile} and return a map of {@code lobbyName → ordered card names}.
     *
     * @param replayFile path to the replay JSON
     * @return map, never null; empty if file cannot be read or has no events
     */
    public static Map<String, List<String>> parse(File replayFile) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (replayFile == null || !replayFile.exists()) return result;

        try (Reader reader = new FileReader(replayFile)) {
            JsonElement rootElem = JsonParser.parseReader(reader);
            if (!rootElem.isJsonObject()) return result;
            JsonObject root = rootElem.getAsJsonObject();

            // Build playerId → lobbyName map from meta.players
            Map<String, String> idToName = new LinkedHashMap<>();
            if (root.has("meta") && root.get("meta").isJsonObject()) {
                JsonObject meta = root.getAsJsonObject("meta");
                if (meta.has("players") && meta.get("players").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : meta.getAsJsonObject("players").entrySet()) {
                        if (e.getValue().isJsonObject()) {
                            String name = e.getValue().getAsJsonObject().has("name")
                                    ? e.getValue().getAsJsonObject().get("name").getAsString()
                                    : e.getKey();
                            idToName.put(e.getKey(), name);
                        }
                    }
                }
            }

            // Scan log_l1 for CAST/ACTIVATE events in order
            String eventsKey = root.has("log_l1") ? "log_l1" : "events";
            if (!root.has(eventsKey) || !root.get(eventsKey).isJsonArray()) {
                LOG.warn("No event array ('{}') found in replay file: {}", eventsKey, replayFile);
                return result;
            }

            for (JsonElement el : root.getAsJsonArray(eventsKey)) {
                if (!el.isJsonObject()) continue;
                JsonObject ev = el.getAsJsonObject();

                String type = ev.has("type") ? ev.get("type").getAsString() : null;
                if (type == null || !PLAY_EVENT_TYPES.contains(type)) continue;

                String actor = ev.has("a") ? ev.get("a").getAsString() : null;
                if (actor == null) continue;

                String lobbyName = idToName.getOrDefault(actor, actor);

                // card_name preferred; fall back to card object id
                String cardName = null;
                if (ev.has("data") && ev.get("data").isJsonObject()) {
                    JsonObject data = ev.getAsJsonObject("data");
                    if (data.has("card_name") && !data.get("card_name").isJsonNull()) {
                        cardName = data.get("card_name").getAsString();
                    } else if (data.has("card") && !data.get("card").isJsonNull()) {
                        cardName = data.get("card").getAsString(); // object ID fallback
                    }
                }
                if (cardName == null) continue;

                result.computeIfAbsent(lobbyName, k -> new ArrayList<>()).add(cardName);
            }

            int total = result.values().stream().mapToInt(List::size).sum();
            LOG.info("Parsed play sequence: {} players, {} total plays", result.size(), total);
        } catch (IOException e) {
            LOG.error("Failed to parse play sequence from: {}", replayFile, e);
        }
        return result;
    }
}
```

---

### Step 3 — `SimulateMatch.java` (forge-gui-desktop)

When `-r <path>` is set, additionally parse the play sequence and store it in `GameRules`:

```java
// After: rules.setReplayLogPath(replayPath);
Map<String, List<String>> playSeq =
    ReplayPlaySequenceParser.parse(new java.io.File(replayPath));
if (!playSeq.isEmpty()) {
    rules.setForcedPlaySequence(playSeq);
    if (!quiet) {
        System.out.println("[replay] Loaded forced play sequence for "
            + playSeq.size() + " player(s)");
    }
}
```

---

### Step 4 — `AiController.chooseSpellAbilityToPlay()` (forge-ai)

Inject the forced-sequence check **before** the normal AI path.  
The sequence lives in `GameRules` (forge-game, accessible from forge-ai via `player.getGame().getRules()`).

**Location:** `AiController.java`, at the top of `chooseSpellAbilityToPlay()`,
before the `useSimulation` check:

```java
public List<SpellAbility> chooseSpellAbilityToPlay() {
    AiCache.clear();
    predictedCombat = null;
    predictedCombatNextTurn = null;
    memory.clearMemorySet(AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL);

    // ── Case 1: forced play sequence from replay ─────────────────────────
    Map<String, List<String>> forcedSeq = game.getRules().getForcedPlaySequence();
    if (forcedSeq != null) {
        String lobbyName = player.getLobbyPlayer().getName();
        List<String> seq = forcedSeq.get(lobbyName);
        if (seq != null && !seq.isEmpty()) {
            String nextCardName = seq.get(0);
            // Collect all currently available spell abilities
            List<SpellAbility> all = ComputerUtilAbility.getSpellAbilities(
                    player.getCardsIn(ZoneType.Hand), player);
            for (SpellAbility sa : all) {
                if (sa.getHostCard().getName().equals(nextCardName) && sa.canPlay()) {
                    seq.remove(0);
                    LOG.debug("Forced play: {} for {}", nextCardName, lobbyName);
                    return singleSpellAbilityList(sa);
                }
            }
            // nextCardName not castable right now: leave it in the queue,
            // fall through to normal AI (soft enforcement)
            LOG.debug("Forced play skipped (not castable): {} for {}", nextCardName, lobbyName);
        }
    }
    // ─────────────────────────────────────────────────────────────────────

    if (useSimulation) {
        return singleSpellAbilityList(simPicker.chooseSpellAbilityToPlay(null));
    }
    // ... rest unchanged
}
```

> **Why not in `PlayerControllerAi`?**  
> `PlayerControllerAi.chooseSpellAbilityToPlay()` simply delegates to `brains.chooseSpellAbilityToPlay()`.
> The actual early-return logic belongs in `AiController` where the full candidate list is assembled.

---

### Step 5 — (Optional) Forced Target Selection

For targeted spells (e.g. a removal spell that targets a specific creature), also parse `data.targets`
and attempt to match targets in the current game state:

- `targets` entries are object IDs like `"c23"` or player IDs like `"P2"`.
- During the forced play, attempt to resolve targets by matching the card name in the
  current game state (via `GameObjectUtils.getObjectById()` if available, else skip).
- If matching fails → proceed with normal AI target selection.

This is **Phase 2** — the forced card choice (Step 4) already gives ~70% replay fidelity
without target tracking.

---

## Data Flow Summary

```
replay.json (CAST events)
        │
        ▼
ReplayPlaySequenceParser.parse()          [forge-gui, parse time]
        │ Map<lobbyName, List<cardName>>
        ▼
GameRules.setForcedPlaySequence()         [forge-game, match setup]
        │
        ▼
AiController.chooseSpellAbilityToPlay()   [forge-ai, each AI priority window]
        │ pop nextCardName if castable
        ▼
SpellAbility returned → game engine resolves
```

---

## Sequence Lifecycle Per Game

```
match setup    → forcedPlaySequence populated from replay JSON
               │
turn N, MP1   → AiController checks queue[player] → pops "Sol Ring" → plays it
turn N, MP2   → AiController checks queue[player] → pops "Arcane Signet" → plays it
turn N+1      → pops "Counterspell" → NOT castable (wrong phase) → fall through to normal AI
               │  ← queue entry stays; next iteration may succeed
turn N+2      → "Counterspell" now castable → popped and played
               ...
queue empty   → pure normal AI for all remaining turns
```

---

## Edge Cases & Decisions

| Situation | Behaviour |
|-----------|-----------|
| Forced card not in hand (was discarded / countered) | Entry stays in queue; falls through every turn until game ends |
| Forced card castable on opponent's turn (flash) | Soft enforcement: only played if AI would also prioritize responding |
| Simulation mode (`useSimulation = true`) | Forced sequence check runs before simPicker — sim path unaffected |
| Multiple copies of same card name | First matching SA wins |
| ACTIVATE events (activated abilities) | Included in parse step; matched by `sa.getHostCard().getName()` |
| PLAY_LAND events | Included; matched via `sa.isLandAbility()` |
| Empty sequence after all plays consumed | Normal AI for remaining turns |

---

## Module Dependency Check

| Module | Change | New deps |
|--------|--------|----------|
| `forge-game` | `GameRules` + getter/setter | none |
| `forge-gui` | `ReplayPlaySequenceParser` (new class) | `forge-game` (already on classpath) |
| `forge-gui-desktop` | `SimulateMatch` call | `forge-gui` (already on classpath) |
| `forge-ai` | `AiController` early-return | reads `GameRules` via `player.getGame()` — already possible |

No new module dependencies introduced.

---

## Open Questions

1. **Skip vs. retain:** When forced card not castable, should the queue entry be skipped permanently or retried each turn?  
   → Recommended: **retry** (leave entry, try again next decision point). This handles phase timing naturally.

2. **Scope per player:** Should forced sequence apply to *all* AI players, or only the one who matches the original human player?  
   → Recommended: **all players in the map**. Parser only adds entries for players present in `meta.players`.

3. **Simulation-mode AI:** Should `SpellAbilityPicker` also respect the forced sequence, or only the heuristic AI path?  
   → Phase 1: heuristic AI only. Phase 2: extend `SpellAbilityPicker`.

4. **CLI flag:** Should a new flag `--force-play-sequence` disable the auto-parsing from `-r`, or should it always be on when `-r` is set?  
   → Recommended: **always on when `-r` is set** (opt-out flag `--no-play-sequence` if needed later).

---

---

# Documentation Guide: How to Document This Feature

Follow the existing documentation conventions in `docs/` (see `FEATURE_GAME_REPLAY.md`,
`CLI-REPLAY.md`, `REPLAY-TECHNIK.md` as models).

## Files to Create / Update

### 1. New file: `docs/FEATURE_REPLAY_FORCED_PLAY.md`

Primary reference document. Structure:

```markdown
# Feature: Forced Play Sequence (Replay AI Mode)

## 1. Overview
   - What the feature does (one paragraph)
   - Table: mode, audience, entry point

## 2. User Guide
   - How to use: CLI example with -r flag
   - Expected behaviour description

## 3. Architecture
   - Data flow diagram (ASCII art like in FEATURE_GAME_REPLAY.md §1)
   - Involved Files table (module | file | role)

## 4. JSON Source Format
   - Minimal CAST event example
   - Which fields are read (type, a, data.card_name, data.targets)
   - Fallback behaviour for missing fields

## 5. Enforcement Model
   - Soft vs. hard enforcement decision
   - Lifecycle table (what happens when card not castable)

## 6. Edge Cases
   - Table: situation | behaviour

## 7. Logging
   - Logger | Level | Message table (follow FEATURE_GAME_REPLAY.md §5 format)
   | ReplayPlaySequenceParser | INFO  | "Parsed play sequence: N players, M total plays"
   | AiController             | DEBUG | "Forced play: <card> for <player>"
   | AiController             | DEBUG | "Forced play skipped (not castable): <card> for <player>"

## 8. Testing
   - How to verify: run a known replay with -r, compare play order to original
   - Expected log output snippet
```

### 2. Update: `docs/FEATURE_GAME_REPLAY.md`

Add a new row in the opening table:

```markdown
| **Forced Play Sequence** | Developers / batch analysis | `sim -r` (auto-enabled) |
```

Add a new section **§3. Forced Play Sequence** after the CLI Simulation Replay section:

```markdown
## 3. Forced Play Sequence

When `-r` is used in CLI simulation mode, Forge also parses the CAST/ACTIVATE
events from the replay JSON and instructs the AI to attempt those plays in order.

See [FEATURE_REPLAY_FORCED_PLAY.md](FEATURE_REPLAY_FORCED_PLAY.md) for full details.
```

### 3. Update: `docs/CLI-REPLAY.md`

Add a **"Forced Play Sequence"** subsection under the existing CLI options table:

```markdown
### Forced Play Sequence

When a replay file is passed with `-r`, the AI automatically follows the original play
sequence extracted from the log's CAST/ACTIVATE events. This produces a much closer
re-enactment than library order alone.

**Limitations:**
- Soft enforcement only — if a card cannot be cast at that point, the AI falls
  back to its normal heuristic.
- Target selection is not yet forced (Phase 2).
- Only the heuristic AI path is affected; simulation-mode AI is unchanged.
```

### 4. Update: `AGENTS.md`

In the **Replay & Analysis System** section, update the data-flow line:

```markdown
**Data flow (replay):**
`ReplayLogParser` → deck reconstruction +
`ReplayLibraryReorderer` (draw order) +
`ReplayPlaySequenceParser` (play sequence) →
`GameRules.forcedPlaySequence` →
`AiController.chooseSpellAbilityToPlay()` (soft enforcement)
```

### 5. Update: `docs/REPLAY-TECHNIK.md`

Add a "Forced Play Sequence" bullet in **Technische Umsetzung**:

```markdown
- **Forced Play Sequence (Replay AI):**
  - Implementiert in `ReplayPlaySequenceParser.java` (forge-gui)
  - Liest `log_l1`-Events mit `type = CAST|ACTIVATE` aus dem Replay-JSON
  - Baut Map `lobbyName → List<cardName>` für `GameRules.forcedPlaySequence`
  - `AiController.chooseSpellAbilityToPlay()` prüft die Queue vor jeder KI-Entscheidung
  - Soft Enforcement: nicht spielbare Karte bleibt in der Queue, KI entscheidet normal
```

---

## Documentation Style Rules (Project Conventions)

| Rule | Example |
|------|---------|
| Language: English for user/API docs | `docs/FEATURE_*.md`, `AGENTS.md` |
| Language: German allowed for dev notes | `docs/REPLAY-TECHNIK.md` |
| No YAML frontmatter | start file directly with `# Title` |
| Cross-reference with relative links | `[CLI-REPLAY.md](CLI-REPLAY.md)` |
| ASCII data-flow diagrams | Use `──►` `│` `▼` arrows as in FEATURE_GAME_REPLAY.md |
| Involved Files tables | columns: File \| Module \| Role |
| Logging tables | columns: Logger \| Level \| Message |
| Code blocks: label language | ` ```java ` / ` ```json ` / ` ```bash ` |
| One blank line before every `##` heading | |

---

## Checklist: Definition of Done

- [ ] `GameRules.forcedPlaySequence` field added + getter/setter
- [ ] `ReplayPlaySequenceParser` created, parses CAST/ACTIVATE/PLAY_LAND events
- [ ] `SimulateMatch` calls parser when `-r` flag is set
- [ ] `AiController.chooseSpellAbilityToPlay()` checks sequence before normal AI
- [ ] SLF4J log messages at correct levels (INFO for parse, DEBUG for per-play decisions)
- [ ] `docs/FEATURE_REPLAY_FORCED_PLAY.md` created
- [ ] `docs/FEATURE_GAME_REPLAY.md` updated (new row + §3 reference)
- [ ] `docs/CLI-REPLAY.md` updated (forced play sequence paragraph)
- [ ] `AGENTS.md` data-flow line updated
- [ ] `docs/REPLAY-TECHNIK.md` updated (German dev notes)
- [ ] No new module dependencies (verified by `mvn compile -pl forge-game,forge-gui,forge-ai`)
- [ ] Checkstyle passes (no unused/redundant imports)
