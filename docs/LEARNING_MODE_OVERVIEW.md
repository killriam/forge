# Learning Mode — Concept Overview

The Home screen sidebar has a **Learning Mode** group (`localizer` key `lblReplayMode`) with
three entries. There is no design doc for this anywhere else in the codebase — this page exists
to fill that gap. It was reverse-engineered from the actual controller/view classes, not from
any existing spec.

```
Learning Mode
├── Game Recap            (browse YOUR real past games)
├── Investigate Scenarios (browse curated teaching scenarios)
└── Game Learning Viewer  (per-turn analysis surface — see the "two implementations" note below)
```

The three items answer two different questions:

- **Game Recap** and **Investigate Scenarios** are both *pickers* — they answer "which recorded
  game do I want to look at?", scanning the same log directory but filtering for two different
  *kinds* of source material.
- **Game Learning Viewer** is the *analysis/study surface* both pickers feed into — it answers
  "what happened turn-by-turn in the game I picked?".

## Game Recap

- Menu label: `lblReplayGame` = "Game Recap"; screen title `lblReplayModeTitle` = "Game Recap:
  Recent Games".
- Backing classes: `CSubmenuReplay` / `VSubmenuReplay`
  (`forge-gui-desktop/src/main/java/forge/screens/home/replay/`).
- Scans `ForgeConstants.GAME_LOG_DIR` for `.json` replay files, explicitly **excluding**
  `sim_*.json` (AI-only headless runs) and anything where `ReplayLogParser.isScenario()` is
  true. What's left is real games *you* actually played (human in one seat).
- Two actions per entry:
  - **Start** — replays the game interactively from the beginning (`launchReplay()`), optionally
    enforcing the original library/draw order via a checkbox. This starts a **live, playable**
    game reconstructed from the recorded starting conditions — not read-only.
  - **View** — opens the same game in the **Game Learning Viewer** (see below) as a new
    top-level tab, via `CGameLearningUI.setPendingParser(parser)` +
    `Singletons.getControl().setCurrentScreen(FScreen.GAME_LEARNING_SCREEN)`.
- Also reachable via the CLI: `replay <path-to-replay.json>` (see `FEATURE_GAME_REPLAY.md`).

## Investigate Scenarios

- Menu label: `lblReplayScenario` = "Investigate Scenarios"; screen title
  `lblReplayScenarioTitle` = "Investigate Scenarios: Teaching Scenarios".
- Backing classes: `CSubmenuScenario` / `VSubmenuScenario` (same package as above).
- Scans the **same** log directory, but filters the opposite way: only files where
  `parser.isScenario()` is true. These are curated, scripted setups — not organic games you
  played — built for a specific rules question or teaching point: a forced starting hand,
  forced draws, an optional forced play sequence (`events[]`), and free-text
  description/question/answer/ruling-reference fields (see `SCENARIO_STARTING_HAND_FORMAT.md`).
- Single action: **Start Scenario** — `launchScenario()` builds a puzzle-mode (or Commander-mode,
  if commanders are defined) game from the scenario's structured setup fields and starts it
  live, showing the description/question/answer as a dialog once the game state is applied.
- There is no "View in Game Learning Viewer" action for scenarios — they're launch-only.

## Game Learning Viewer — two separate implementations behind one label

This is the part most likely to cause confusion, including for future contributors: **the
sidebar "Game Learning Viewer" entry and the screen Game Recap's "View" button opens are two
different, independently-implemented UIs**, not one screen reached two ways.

### 1. The sidebar-embedded legacy stub

- Backing classes: `CSubmenuGameLearning` / `VSubmenuGameLearning`.
- `EDocID.HOME_GAME_LEARNING`, embedded inside the Home tab like Game Recap/Investigate
  Scenarios are — it is **not** a separate top-level tab.
- Its own class-level Javadoc says: *"The full-featured Game Learning Viewer is now opened as a
  top-level tab via CGameLearningUI / FScreen.GAME_LEARNING_SCREEN. This class acts as a simple
  stub that redirects to the top-level screen when a parser is pending."* — but as of this
  writing that redirect only actually happens via `openForReplay()`, called from elsewhere; the
  class's own `initialize()`/`update()`/`load()`/`showTurn()` methods are a **complete, separate,
  parallel implementation** that renders turn state as plain text into a `JTextArea`
  (`view.getStateArea()`), with a plain `FList<String>` turn list (no life-dot icons, no board
  graphics). It is reached whenever this is the user's last-selected Home submenu (persisted via
  `FPref.SUBMENU_CURRENTMENU`) — including on a completely fresh app launch, since
  `CHomeUI.selectPrevious()` re-selects whatever was last active.
- **This is effectively dead/superseded code that was never deleted.** Worth consolidating or
  removing in a future pass — not done as part of this doc.

### 2. The polished top-level-tab viewer (what "Game Learning Viewer" usually means in practice)

- Backing classes: `CGameLearningUI` / `VGameLearningUI`
  (`forge-gui-desktop/src/main/java/forge/screens/gamelearning/`).
- Opened as a genuinely new top-level tab (alongside "Home" and "Deck Editor" in the window's own
  tab strip), via `CGameLearningUI.setPendingParser(parser)` +
  `Singletons.getControl().setCurrentScreen(FScreen.GAME_LEARNING_SCREEN)` — the call Game
  Recap's "View" button makes.
- Read-only, turn-by-turn analysis, not interactive play:
  - Left: turn list with per-player life dots, ⚠ blunder markers, 🔖 learning markers.
  - Right, per turn: `MtgBoardPanel` (opponent top / human bottom, real card names + zone
    counts), an evaluation-dimension chart bar, and tabs for Events / Statistics / Game Report.
  - Bottom: Prev Turn / Next Turn / **Replay from here**.
- **Replay from here** is how you branch into an interactive game from a specific turn: it opens
  a confirm dialog ("Replay from Turn N", with an "enforce draw order" checkbox) and, on
  confirm, calls `CSubmenuReplay.startReplayFromPath(path, enforceDrawOrder, turnNumber,
  summary)` — `ReplayGameStateBuilder`/`ReplayStateReconstructor` rebuild the exact board state
  at that turn (life, battlefield, hands, etc.), skip mulligan/coin toss, and drop you into a
  live game from that point onward.
- A turn's `TurnSnapshot` captures state **at the start of that turn**, before that turn's own
  events are applied — a card played mid-turn N first appears in the board panel on turn N+1's
  snapshot, not turn N's. This is by design, not a bug; don't "fix" it if it looks surprising in
  a screenshot.

## Notes for future maintenance

- If extending or debugging the Game Learning Viewer's board/nav-button rendering: `MtgBoardPanel`
  and the nav-button `FButton` instances in `VGameLearningUI` are **singleton fields** reused
  across the four `CardLayout` cards (Overview / Init / Turn / GameOver). A Swing component can
  only have one parent — adding the same instance to two different cards silently steals it from
  whichever card built first, leaving that card's slot rendering nothing (correct layout size,
  just never actually `isShowing()`). Give each card its own instance (as `VGameLearningUI`
  already does for `mtgBoardPanelGameOver`), or hoist the shared component outside the
  `CardLayout` entirely (as done for the nav panel) if it's meant to be visible on every card.
- Battlefield-card and zone-count tracking (`ReplayStateReconstructor`) only understands specific
  event `type` values for entering/leaving a zone. If a new action type is added to the replay
  event schema that moves a card onto the battlefield, `updateBattlefieldCards()` and
  `applyZoneDelta()` need to know about it explicitly — there's no generic fallback.
