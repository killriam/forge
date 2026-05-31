### Custom commits:
- df9f24924c Update documentation and schema for MTG Replay Notation: enhance player metadata, add new event types, and improve learning marker specifications
- be1c50e592 Add game start information and enhance replay logging: implement GameStartInfo model, update ReplayLog structure, and improve JSON serialization for game events
- 84229f7209 Add game start information and enhance replay logging: implement GameStartInfo model, update ReplayLog structure, and improve JSON serialization for game events
- 84b4a46bf0 Add MTG Replay Notation support: implement JSON logging, enhance game state tracking, and introduce analysis tools1
> **Branch:** `apply-analysis-patch` (9 commits, Dec 2025 – Feb 2026)  
> **Total:** 81 files changed, ~21 997 lines added, ~65 lines removed  
> **Last Updated:** March 2026
- 20b163e511 Apply analysis and mana tracking patch (applied, converted encoding); remove .rej artifacts
- 7115b2bea0 Apply analysis and mana tracking patch (partial auto-apply; converted patch encoding)
- 3384aa5f9e Apply analysis and mana tracking patch: add ANALYSIS log level, game log improvements and simulation helpers

### File change stats (fork vs upstream):
```
.gitignore                                         |    3 +
 .gitmodules                                        |    3 +
 AI_FEATURES_ANALYSIS.md                            | 1389 ++++++
 FORGE_CUSTOM_FEATURES.md                           |  184 +
 FORK_CHANGES_SUMMARY.md                            |  548 +++
 START_FORGE_DEBUG.bat                              |   95 +
 START_FORGE_DEBUG.ps1                              |  192 +
 analyze_replay_log.py                              |  172 +
 .../REPLAY_FORMAT_ENHANCEMENT_IMPLEMENTATION.md    |  579 +++
 .../src/main/java/forge/ai/AiAttackController.java |   23 +
 forge-ai/src/main/java/forge/ai/AiController.java  |   36 +-
 .../src/main/java/forge/ai/AiDecisionLogger.java   |  345 ++
 forge-analysis-and-mana-tracking.patch             |  Bin 0 -> 346142 bytes
 forge-analysis-and-mana-tracking.patch.utf8        | 4935 ++++++++++++++++++++
 forge-analysis-and-mana-tracking.utf8.patch        | 4935 ++++++++++++++++++++
 .../src/main/java/forge/deck/CardForFitting.java   |   14 +
 forge-core/src/main/java/forge/deck/DeckIdea.java  |   13 +
 .../src/main/java/forge/deck/FittingSection.java   |   17 +
 forge-game/pom.xml                                 |   11 +
 .../src/main/java/forge/game/GameAnalysis.java     |   66 +
 forge-game/src/main/java/forge/game/GameLog.java   |   18 +
 .../src/main/java/forge/game/GameLogEntryType.java |    6 +-
 .../src/main/java/forge/game/GameLogFormatter.java |  462 +-
 .../src/main/java/forge/game/GameSnapshot.java     |   31 +-
 forge-game/src/main/java/forge/game/Match.java     |   25 +-
 forge-game/src/main/java/forge/game/card/Card.java |   12 +-
 .../java/forge/game/log/GameReplaySimulation.java  |  435 ++
 .../java/forge/game/log/ReplayJsonSerializer.java  |  723 +++
 .../java/forge/game/log/ReplayL2Generator.java     |  305 ++
 .../forge/game/log/ReplayNotationExporter.java     | 1634 +++++++
 .../forge/game/log/ReplayNotationSimulation.java   |  468 ++
 .../forge/game/log/ReplayNotationValidator.java    |  377 ++
 .../java/forge/game/log/model/CardDefinition.java  |   42 +
 .../java/forge/game/log/model/GameStartInfo.java   |   91 +
 .../main/java/forge/game/log/model/GameState.java  |  136 +
 .../java/forge/game/log/model/GameSummary.java     |  112 +
 .../main/java/forge/game/log/model/L1Event.java    |   48 +
 .../src/main/java/forge/game/log/model/L2Unit.java |  161 +
 .../main/java/forge/game/log/model/ReplayLog.java  |  195 +
 .../main/java/forge/game/log/model/ReplayMeta.java |   93 +
 .../java/forge/game/log/model/TurnSummary.java     |   93 +
 .../main/java/forge/game/phase/PhaseHandler.java   |   14 +
 .../src/main/java/forge/game/player/DeckStats.java |   47 +
 .../src/main/java/forge/game/player/DeckWins.java  |    6 +
 .../src/main/java/forge/game/player/Player.java    |   77 +
 .../main/java/forge/game/startingHandStats.java    |   43 +
 .../src/main/java/forge/util/SQLiteConnection.java |  283 ++
 .../game/log/ReplayNotationSimulationTest.java     |  404 ++
 .../main/java/forge/deckchooser/FDeckChooser.java  |  355 +-
 .../main/java/forge/itemmanager/ItemManager.java   |   16 +
 .../java/forge/itemmanager/views/ItemView.java     |   10 +
 .../src/main/java/forge/screens/home/CLobby.java   |   13 +
 .../src/main/java/forge/screens/home/VLobby.java   |   28 +
 .../java/forge/screens/match/TargetingOverlay.java |    2 +-
 .../main/java/forge/screens/match/ViewWinLose.java |    7 +
 .../forge/screens/match/controllers/CDock.java     |    3 +-
 .../src/main/java/forge/view/SimulateMatch.java    |  341 +-
 forge-gui-mobile/src/forge/deck/FDeckChooser.java  |   17 +-
 .../src/forge/screens/constructed/LobbyScreen.java |    5 +
 .../src/forge/screens/constructed/PlayerPanel.java |    2 +
 .../forge/screens/match/winlose/ViewWinLose.java   |    7 +
 forge-gui/pom.xml                                  |    4 +
 forge-gui/res/puzzle/REPLAY_TEST.pzl               |   20 +
 forge-gui/res/puzzle/example_replay.json           |   71 +
 .../main/java/forge/deck/ColorDeckGenerator.java   |   22 +
 .../java/forge/deck/CommanderDeckGenerator.java    |    7 +
 .../main/java/forge/deck/DeckImportController.java |   35 +
 .../src/main/java/forge/game/GameLogSaver.java     |  297 ++
 .../src/main/java/forge/game/GameLogSaverTest.java |   40 +
 .../gamemodes/puzzle/ReplayToPuzzleConverter.java  |  561 +++
 .../gamemodes/puzzle/ReplayToPuzzleLauncher.java   |    0
 .../src/main/java/forge/itemmanager/ColumnDef.java |    7 +-
 .../java/forge/itemmanager/ItemManagerModel.java   |   21 +-
 .../localinstance/properties/ForgeConstants.java   |    1 +
 monitor_logs.ps1                                   |   64 +
 mtg-replay-notation                                |    1 +
 pom.xml                                            |   10 +
 replay_to_puzzle.bat                               |    0
 replay_to_puzzle.ps1                               |    0
 run_simulation.ps1                                 |  126 +
 test_commander_debug.ps1                           |   68 +
 81 files changed, 21997 insertions(+), 65 deletions(-)

---
## Decklist-Aware AI — Mulligan, Combos, Anti-Synergy (April 2026)

Implements Commander Decklist Notation `deck_rules` consumption in the AI:

### New Files
| File | Module | LOC | Purpose |
|------|--------|-----|---------|
| `DeckRulesConfig.java` | forge-core | ~310 | Data model for deck_rules (mulligan, combos, anti-synergies) + inline hint parser |
| `DeckRulesLoader.java` | forge-ai | ~230 | JSON parser: Commander Decklist Notation → DeckRulesConfig |
| `ComboTracker.java` | forge-ai | ~250 | Runtime combo readiness + anti-synergy penalty tracker |
| `DeckRulesConfigTest.java` | forge-gui-desktop (test) | ~130 | Unit tests for model + inline parsing |
| `DeckRulesLoaderTest.java` | forge-gui-desktop (test) | ~80 | Unit tests for JSON loading |

### Modified Files
| File | Module | Change |
|------|--------|--------|
| `Deck.java` | forge-core | +`deckRulesConfig`, `decklistSpecPath` fields, enhanced `setAiHints` |
| `AiController.java` | forge-ai | +`ComboTracker` field, `initComboTracker()` |
| `PlayerControllerAi.java` | forge-ai | Calls `initComboTracker()` at game start |
| `ComputerUtil.java` | forge-ai | `wantMulligan()` uses deck-rules evaluator when available |
| `DecklistMulliganEvaluator.java` | forge-ai | +`fromDeckRules()` factory method |
| `docs/FEATURE_DECKLIST_MULLIGAN.md` | docs | Updated with Phase 2 combo/anti-synergy docs |
