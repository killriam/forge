- Desktop `Main.main()` supports CLI modes: `sim` (headless match), `parse` (card validation).
**Fork additions:** Game analytics (SQLite via `forge.util.SQLiteConnection`), MTG Replay Notation JSON logging (`forge.game.log.*`), AI decision logger (`AiDecisionLogger`). See `FORK_CHANGES_SUMMARY.md`.
# AGENTS.md — Forge (MTG Rules Engine)

## Architecture Overview

Forge is a Java 17 / Maven multi-module project implementing the full Magic: The Gathering rules engine.

| Module | Role |
|--------|------|
| `forge-core` | Card definitions, rules primitives, mana system (`CardRules`, `ManaCostParser`) |
| `forge-game` | Game loop, state machine, phases, zones, combat, stack resolution (`Game`, `Match`, `PhaseHandler`, `GameAction`) |
| `forge-ai` | Heuristic AI opponent (`AiController`, `ComputerUtil*`, `SpellAbilityAi`, per-ability handlers in `ability/`) |
| `forge-gui` | Shared UI logic + **all card script resources** under `res/` |
| `forge-gui-desktop` | Swing desktop client; entry point: `forge.view.Main` |
| `forge-gui-mobile` | libGDX mobile UI; entry point: `forge.Forge` (ApplicationListener) |
| `forge-lda` | LDA topic modeling for card analysis |
| `adventure-editor` | Adventure mode map/scenario editor |
| `forge-installer` | Installer packaging |
| `forge-gui-mobile-dev` | Desktop runner for mobile UI via LWJGL3 |
**Fork additions:** Game analytics (SQLite via `forge.util.SQLiteConnection`), MTG Replay Notation JSON logging (`forge.game.log.*`), AI decision logger (`AiDecisionLogger`), interactive GUI & CLI game replay, game learning viewer with turn evaluation & blunder detection, scenario viewer. See `FORK_CHANGES_SUMMARY.md`.
**Fork additions:** Game analytics (SQLite via `forge.util.SQLiteConnection`), MTG Replay Notation JSON logging (`forge.game.log.*`), AI decision logger (`AiDecisionLogger`). See `FORK_CHANGES_SUMMARY.md`.

## Build & Run

```bash
# Full build (Windows/Linux profile):
mvn -U -B clean -P windows-linux install
# Desktop package build (after successful changes — creates executable JAR):
mvn clean package -pl forge-gui-desktop -am -Dmaven.test.skip=true


# Run desktop client:
java -jar forge-gui-desktop/target/forge-gui-desktop-*-SNAPSHOT-jar-with-dependencies.jar

# Run headless AI simulation (from desktop jar):
java -jar <jar> sim -d deck1.dck deck2.dck -n 10 -f constructed -q
#   -n = games, -m = match size (best-of), -f = format, -q = quiet, -c = timeout secs
# Replay a recorded game interactively (opens GUI, reorders library to match draws):
java -jar <jar> replay path/to/replay_log.json

# CLI replay simulation (deterministic, headless — reorders library from replay):
java -jar <jar> sim -d deck1.dck deck2.dck -n 1 -r path/to/replay_log.json

# Standalone replay notation test:
java -jar <jar> sim -replay [output_dir]


# Checkstyle runs at validate phase — only checks RedundantImport and UnusedImports (see checkstyle.xml)
```

## Card Scripting (Critical Path)

Cards are defined as `.txt` files in `forge-gui/res/cardsfolder/<first-letter>/`. File name: lowercase, underscores for spaces, no special chars.

```text
Name:Abzan Falconer
ManaCost:2 W
Types:Creature Human Soldier
PT:2/3
K:Outlast:W
S:Mode$ Continuous | Affected$ Creature.YouCtrl+counters_GE1_P1P1 | AddKeyword$ Flying | ...
Oracle:Outlast {W} ...
```

Key properties: `Name`, `ManaCost`, `Types`, `PT`, `K` (keywords), `A` (abilities via AbilityFactory — `AB$`/`SP$`/`DB$`/`ST$`), `S` (static abilities), `T` (triggers), `R` (replacements), `Oracle`, `AI:RemoveDeck`, `DeckHints`, `DeckNeeds`, `SVar`. Dual-face cards use `ALTERNATE` separator. Full API: `docs/Card-scripting-API/`.

## AI System

The AI is **heuristic-based** (no ML). Core flow: `PlayerControllerAi` → `AiController` → `ComputerUtil*` helpers.

- Per-ability AI logic: `forge-ai/src/main/java/forge/ai/ability/` (one class per API effect)
- Card-specific overrides: `SpecialCardAi.java`, `SpecialAiLogic.java`
- AI personality profiles: `forge-gui/res/ai/*.ai` (key-value config — aggression, mulligan, trade thresholds)
- Simulation-based picks: `forge-ai/src/main/java/forge/ai/simulation/`

## Resource Directories (`forge-gui/res/`)

| Path | Content |
|------|---------|
| `cardsfolder/` | ~30k card script `.txt` files (a-z subdirs) |
| `editions/` | Set definitions |
| `ai/` | AI profile `.ai` files |
| `formats/` | Format legality definitions |
| `puzzle/` | Puzzle scenario `.pzl` files |
| `quest/`, `adventure/` | Single-player mode data |
| `tokenscripts/` | Token card definitions |

## Conventions & Patterns

- **Java 17**, no newer API calls (Android compat — avoid e.g. `StringBuilder.isEmpty()`).
- Checkstyle enforces: no redundant imports, no unused imports. Build fails on violation.
- GUI naming: `V` prefix = View, `C` prefix = Controller (e.g. `VStatistics` / `CStatistics`).
- Card scripts use `CARDNAME` as self-reference placeholder in ability text.
- `Valid` filter syntax is pervasive for targeting/selection (e.g. `Creature.YouCtrl+counters_GE1_P1P1`).
- Desktop `Main.main()` supports CLI modes: `sim` (headless match), `parse` (card validation), `replay` (interactive game replay).

## Replay & Analysis System (Fork)

The replay system captures and replays games in the MTG Replay Notation JSON format.

**Data flow (capture):** `GameLogFormatter` → `ReplayNotationExporter` / `ReplayEventLogger` → JSON file via `GameLogSaver`

**Data flow (replay):** `ReplayLogParser` → deck reconstruction + `ReplayLibraryReorderer` (deterministic draw order) → `GameAction.startGame()`

### Key Packages

| Path | Content |
|------|---------|
| `forge-game/.../log/` | Replay engine: `ReplayNotationExporter` (1634 LOC), `ReplayEventLogger`, `ReplayJsonSerializer`, `ReplayLibraryReorderer`, `ReplayL2Generator`, `ReplayNotationValidator` |
| `forge-game/.../log/model/` | Data models: `ReplayLog`, `ReplayMeta`, `L1Event`, `L2Unit`, `GameState`, `CardDefinition`, `GameStartInfo`, `GameSummary`, `TurnSummary`, `Scenario` |
| `forge-gui/.../game/` | `ReplayLogParser`, `GameLogSaver`, `TurnEvaluator`, `BlunderDetector`, `ReplayStateReconstructor`, `ReplayGameStateBuilder` |
| `forge-gui-desktop/.../replay/` | GUI: `VSubmenuReplay`/`CSubmenuReplay` (replay mode), `VSubmenuGameLearning`/`CSubmenuGameLearning` (learning viewer), `VSubmenuScenario`/`CSubmenuScenario` (scenario viewer), board visualization panels |

### mtg-replay-notation Submodule

`mtg-replay-notation/` is a Git submodule containing the canonical replay format specification, JSON Schema, and examples. Key conventions:

- **`snake_case` JSON keys** — never camelCase
- **Object IDs:** `c<n>` = card, `t<n>` = token, `s<n>` = stack item, `P<n>` = player
- **Time markers:** `T<turn>.<phase>[:<priority>]` (e.g. `T3.MP1:2`)
- Spec docs: `mtg-replay-notation/spec/MTG-REPLAY-NOTATION.md`, schema: `mtg-replay-notation/schema/replay-schema.json`
- Changes affect 4+ consumers — prefer optional fields and minor version bumps
- Desktop `Main.main()` supports CLI modes: `sim` (headless match), `parse` (card validation).

