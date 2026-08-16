package forge.screens.home.replay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;

import forge.deck.Deck;
import forge.game.DemoPlaySequenceExtractor;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.ReplayLogParser;
import forge.game.ReplayLogParser.ScenarioInfo;
import forge.game.log.ReplayEventLogger;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.gui.SOverlayUtils;
import forge.gui.framework.ICDoc;
import forge.gui.util.SOptionPane;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.skin.FSkinProp;
import forge.menus.IMenuProvider;
import forge.menus.MenuUtil;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.screens.home.replay.VSubmenuScenario.ScenarioRow;
import forge.util.Localizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Controller for the Replay Scenario submenu.
 * Scans the scenario directory for scenario JSON files and lets the user play them
 * interactively, similar to puzzle mode, via a sortable table (Type / Name / Deck / Demoed).
 *
 * The scenario JSON's "scenario" object may include:
 *   "player_count": 2..N   (number of players to create, default 2)
 *   "game_state": ["key=value", ...]  (puzzle-format key=value lines)
 */
public enum CSubmenuScenario implements ICDoc, IMenuProvider {
    SINGLETON_INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(CSubmenuScenario.class);
    private static final String DEMO_PLAY_LABEL_DEFAULT = "lblScenarioDemoPlay";
    private static final String DEMO_PLAY_LABEL_REDO = "lblScenarioRedoDemo";

    private final VSubmenuScenario view = VSubmenuScenario.SINGLETON_INSTANCE;

    /** Background worker for async file scanning - cancelled if the screen is re-entered. */
    private SwingWorker<List<ScenarioRow>, Void> loadWorker;

    @Override
    public void register() {
    }

    @Override
    public void initialize() {
        view.getTable().getSelectionModel().addListSelectionListener(this::onSelectionChanged);
        view.getBtnStart().addActionListener(e -> startScenario());
        view.getBtnDemoPlay().addActionListener(e -> startScenarioDemoPlay());
        // Do NOT call updateData() here - every home-screen submenu's initialize() runs
        // unconditionally, synchronously, on the EDT at app startup (FView.initialize()), well
        // before the user has navigated here. ReplayLogParser.listScenarioFiles() parses every
        // *.json in the scenario directory - including full deck reconstruction (real
        // card-database lookups) for demo-play recordings sharing that folder - which with a
        // large enough folder blocked the UI thread for seconds on every launch regardless of
        // whether this screen was ever opened. Lazy-load only when the screen is shown instead,
        // same fix already applied to CSubmenuReplay for the same reason.
        updateBtnEnablement();
    }

    private void onSelectionChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            updateScenarioInfo();
            updateDemoPlayButtonLabel();
        }
    }

    /**
     * Scan the scenario directory asynchronously and populate the table. Only runs when the
     * screen is actually visible (called from update()), not at app startup.
     */
    private void updateData() {
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }

        view.getTableModel().setRows(new ArrayList<>());
        updateBtnEnablement();

        loadWorker = new SwingWorker<>() {
            @Override
            protected List<ScenarioRow> doInBackground() {
                List<ScenarioRow> result = new ArrayList<>();
                Map<String, String> scenarioToDeck = buildScenarioToDeckIndex();
                // Two files that happen to produce the same Name text (e.g. both titled "Perfect
                // Game", whether coincidentally or because one is a literal duplicate of the
                // other) used to collide in a Map<displayString, parser> lookup - selecting what
                // looked like your scenario could silently open a different, unrelated one. The
                // table sidesteps that entirely: each row's identity is its position, resolved
                // back to a parser via ScenarioRow, never by re-parsing displayed text - so
                // disambiguation here is purely cosmetic (avoid confusing duplicate Name cells),
                // not required for correctness.
                java.util.Set<String> seenNames = new java.util.HashSet<>();
                for (ReplayLogParser parser : ReplayLogParser.listScenarioFiles()) {
                    if (isCancelled()) break;
                    ScenarioInfo si = parser.getScenarioInfo();
                    String name = si != null && si.name != null ? si.name : parser.getReplayFile().getName();
                    if (!seenNames.add(name)) {
                        String fileToken = parser.getReplayFile().getName().replace(".json", "");
                        name = name + " — " + fileToken;
                    }
                    String type = si != null ? si.type : null;
                    String deck = resolveDeckName(parser, si, scenarioToDeck);
                    boolean demoed = hasDemoRecording(parser, si);
                    result.add(new ScenarioRow(type, name, deck, demoed, parser));
                }
                return result;
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    List<ScenarioRow> result = get();
                    view.getTableModel().setRows(result);
                    LOG.info("Found {} scenario files", result.size());
                } catch (Exception e) {
                    LOG.error("Failed to load scenario files", e);
                } finally {
                    updateBtnEnablement();
                }
            }
        };
        loadWorker.execute();
    }

    private void updateBtnEnablement() {
        boolean hasItems = view.getTableModel().getRowCount() > 0;
        view.getBtnStart().setEnabled(hasItems);
        view.getBtnDemoPlay().setEnabled(hasItems);
    }

    /**
     * Checks whether any demo-play recording already exists for this scenario, by looking for
     * {@code demo-play_<token>_*.json} files in the scenario directory - same token
     * (id, or filename as fallback) used when {@link #launchScenario} writes one.
     */
    private static boolean hasDemoRecording(ReplayLogParser parser, ScenarioInfo si) {
        File dir = new File(ForgeConstants.SCENARIO_DIR);
        if (!dir.isDirectory()) return false;
        String token = safeFileToken(si != null && si.id != null ? si.id : parser.getReplayFile().getName());
        String prefix = "demo-play_" + token + "_";
        File[] matches = dir.listFiles((d, n) -> n.startsWith(prefix) && n.endsWith(".json"));
        return matches != null && matches.length > 0;
    }

    /**
     * Builds a reverse index from every scenario id/filename token referenced by any deck's
     * {@code Scenario=} metadata to that deck's name - lets {@link #resolveDeckName} show a
     * deck even for scenario files with no {@code meta.players.P1.deck_name} of their own
     * (e.g. hand-authored files, or ones an external tool didn't stamp with player meta), by
     * finding whichever real deck actually references that scenario.
     */
    private static Map<String, String> buildScenarioToDeckIndex() {
        Map<String, String> index = new LinkedHashMap<>();
        for (Deck deck : com.google.common.collect.Iterables.concat(
                FModel.getDecks().getConstructed(), FModel.getDecks().getCommander())) {
            String ids = deck.getScenarioIds();
            if (ids == null || ids.isEmpty()) continue;
            for (String token : ids.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    index.putIfAbsent(trimmed, deck.getName());
                }
            }
        }
        return index;
    }

    /**
     * Resolves the deck this scenario is for, always showing one when it's knowable at all.
     * Priority: explicit {@code scenario.deck_id} (authoritative, mtg-replay-notation spec) ->
     * {@code meta.players.P1.deck_name} (reflects what was actually played) -> reverse lookup
     * across decks' own {@code Scenario=} references (inferred).
     */
    private String resolveDeckName(ReplayLogParser parser, ScenarioInfo si, Map<String, String> scenarioToDeck) {
        if (si != null && si.deckId != null && !si.deckId.isEmpty()) {
            return si.deckId;
        }
        String deckName = getPlayerDeckName(parser, "P1");
        if (deckName != null) return deckName;
        return lookupDeckName(parser, si, scenarioToDeck);
    }

    private String lookupDeckName(ReplayLogParser parser, ScenarioInfo si, Map<String, String> scenarioToDeck) {
        if (si != null && si.id != null) {
            String byId = scenarioToDeck.get(si.id);
            if (byId != null) return byId;
        }
        String fileName = parser.getReplayFile().getName();
        String withoutExt = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
        return scenarioToDeck.get(withoutExt);
    }

    /**
     * Resolves the real {@link Deck} a scenario with structured starting-hand data (opening_hand_test
     * / "Perfect Game" / "Best Starting Hand") is meant to be played with - such scenarios exist to
     * validate a specific deck's opening, so the hand needs to come from that deck's actual library,
     * not an empty placeholder. Same priority chain as {@link #resolveDeckName}, but resolves to the
     * Deck object itself for launching rather than just a display name. Returns null if unresolvable.
     */
    private static Deck resolveScenarioDeck(ReplayLogParser parser, ScenarioInfo si) {
        if (si != null && si.deckId != null && !si.deckId.isEmpty()) {
            Deck byId = findDeckByName(si.deckId);
            if (byId != null) return byId;
        }
        Map<String, String> scenarioToDeck = buildScenarioToDeckIndex();
        String deckName = null;
        if (si != null && si.id != null) {
            deckName = scenarioToDeck.get(si.id);
        }
        if (deckName == null) {
            String fileName = parser.getReplayFile().getName();
            String withoutExt = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
            deckName = scenarioToDeck.get(withoutExt);
        }
        return deckName != null ? findDeckByName(deckName) : null;
    }

    private static Deck findDeckByName(String name) {
        Deck d = FModel.getDecks().getCommander().get(name);
        if (d != null) return d;
        return FModel.getDecks().getConstructed().get(name);
    }

    /** A minimal, inert opponent deck (basic lands only) for scenario launches where a real deck
     *  was resolved for the human seat. These scenarios exist to validate the human's line, not
     *  the AI's - the opponent just needs to be alive and harmless, not a functioning deck. */
    private static Deck buildDummyLandsDeck() {
        Deck deck = new Deck("Scenario Dummy (Lands Only)");
        forge.deck.CardPool lands = deck.getOrCreate(forge.deck.DeckSection.Main);
        lands.add("Plains", 12);
        lands.add("Island", 12);
        lands.add("Swamp", 12);
        lands.add("Mountain", 12);
        lands.add("Forest", 12);
        return deck;
    }

    private static String getPlayerDeckName(ReplayLogParser parser, String playerId) {
        ReplayLogParser.PlayerInfo info = parser.getPlayers().get(playerId);
        return info != null ? info.deckName : null;
    }

    private void updateScenarioInfo() {
        ScenarioRow row = view.getSelectedRow();
        if (row == null) {
            view.getScenarioInfo().setText("");
            return;
        }

        ReplayLogParser parser = row.parser;
        ScenarioInfo si = parser.getScenarioInfo();
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(parser.getReplayFile().getName()).append("\n\n");

        if (si != null) {
            if (si.type != null) sb.append("Type:  ").append(si.type).append("\n");
            if (si.name != null) sb.append("Name:  ").append(si.name).append("\n");
            sb.append("Players: ").append(si.playerCount).append("\n");
            if (row.demoed) sb.append("Demo already recorded for this scenario.\n");
            if (si.description != null && !si.description.isEmpty()) {
                sb.append("\n").append(si.description).append("\n");
            }
            if (si.question != null && !si.question.isEmpty()) {
                sb.append("\nQuestion:\n").append(si.question).append("\n");
            }
            if (si.answer != null && !si.answer.isEmpty()) {
                sb.append("\nAnswer:\n").append(si.answer).append("\n");
            }
            if (!si.rulingReferences.isEmpty()) {
                sb.append("\nRuling References:\n");
                for (String ref : si.rulingReferences) {
                    sb.append("  - ").append(ref).append("\n");
                }
            }
            if (!si.tags.isEmpty()) {
                sb.append("\nTags: ").append(String.join(", ", si.tags)).append("\n");
            }
        }

        view.getScenarioInfo().setText(sb.toString());
        view.getScenarioInfo().setCaretPosition(0);
    }

    /** Switches the Demo Play button's label to "Redo Demo" once the selected scenario already
     *  has a recording, so it's clear a fresh one will replace/add to it rather than start blind. */
    private void updateDemoPlayButtonLabel() {
        ScenarioRow row = view.getSelectedRow();
        boolean redo = row != null && row.demoed;
        view.getBtnDemoPlay().setText(Localizer.getInstance().getMessageorUseDefault(
                redo ? DEMO_PLAY_LABEL_REDO : DEMO_PLAY_LABEL_DEFAULT,
                redo ? "Redo Demo" : "Demo Play (record actions)"));
    }

    private boolean startScenario() {
        return startScenario(false);
    }

    /**
     * "Demo Play": applies the scenario's draw order only (no forced play-sequence, even if the
     * file has one) and records the whole playthrough in detail, so a human can discover a good
     * line with the guaranteed hand and use the recording to write/improve that scenario's
     * {@code events[]} block - a scenario-authoring aid, not a player-facing mode.
     */
    private boolean startScenarioDemoPlay() {
        return startScenario(true);
    }

    private boolean startScenario(boolean demoPlay) {
        final Localizer localizer = Localizer.getInstance();
        ScenarioRow row = view.getSelectedRow();
        if (row == null) {
            SOptionPane.showMessageDialog(
                    localizer.getMessage("lblPleaseSelectScenario"),
                    localizer.getMessage("lblNoSelectedScenario"),
                    FSkinProp.ICO_ERROR);
            return false;
        }

        return launchScenario(row.parser, demoPlay);
    }

    /**
     * Launch a scenario as a puzzle-mode game.
     *
     * Players are created based on the scenario's player_count field.
     * The game state (cards on battlefield, life totals, active player/phase)
     * is applied via the startGameHook using the puzzle key=value format
     * stored in the scenario's game_state array.
     *
     * When the scenario's ScenarioInfo contains structured player setup data
     * (starting_hand / first_draws / commanders), those are auto-converted to
     * puzzle-format game_state lines and merged with any explicit game_state entries.
     *
     * Scenarios with commanders use GameType.Commander rules instead of Puzzle
     * so that command-zone casting and commander tax function correctly.
     */
    private boolean launchScenario(ReplayLogParser parser, boolean demoPlay) {
        final ScenarioInfo si = parser.getScenarioInfo();
        final int playerCount = (si != null && si.playerCount >= 2) ? si.playerCount : 2;

        // Scenarios with a structured starting hand (opening_hand_test / "Perfect Game" /
        // "Best Starting Hand") exist to validate a specific deck's opening - drawing that hand
        // from an empty or unrelated deck isn't meaningful, so these require a resolvable deck
        // reference (scenario.deck_id, or a deck's own Scenario= metadata pointing at it) and
        // refuse to launch without one, rather than silently falling back to a placeholder.
        final boolean needsRealDeck = si != null && si.hasPlayerSetup();
        final Deck resolvedDeck = needsRealDeck ? resolveScenarioDeck(parser, si) : null;
        if (needsRealDeck && resolvedDeck == null) {
            SOptionPane.showMessageDialog(
                    "This scenario has a starting hand to validate, but no deck references it "
                            + "(no scenario.deck_id, and no deck's Scenario= metadata points at it). "
                            + "Attach it to a deck first — see docs/SCENARIO_STARTING_HAND_FORMAT.md, "
                            + "\"Von einem Deck referenzieren\".",
                    "Cannot Launch Scenario", FSkinProp.ICO_ERROR);
            return false;
        }

        SwingUtilities.invokeLater(() -> {
            SOverlayUtils.startGameOverlay();
            SOverlayUtils.showOverlay();
        });

        try {
            final HostedMatch hostedMatch = GuiBase.getInterface().hostMatch();

            // Merge explicit game_state lines with auto-generated lines from player setup. When
            // a real deck was resolved, the auto-generated structured lines are skipped
            // entirely: GameState.applyToGame unconditionally clears EVERY zone for a player
            // before reapplying only what's in the lines it's given (see
            // GameState.setupPlayerState) - safe for puzzle-style empty-deck scenarios, where
            // that's the whole board being authored, but it would wipe a real deck's
            // already-populated library and hand (from ScenarioLibrarySetup /
            // RegisteredPlayer.forCommander below) down to nothing the moment this hook fires.
            // starting_hand/first_draws come from ScenarioLibrarySetup, commanders from the
            // deck's own Commander section, and starting life is set directly on the
            // RegisteredPlayer objects below instead - none of that needs this hook.
            final List<String> gameStateLines = si != null ? new ArrayList<>(si.gameState) : new ArrayList<>();
            if (si != null && si.hasPlayerSetup() && resolvedDeck == null) {
                // Prepend structured lines so explicit game_state overrides them if needed
                List<String> structuredLines = si.buildGameStateFromPlayerSetup();
                structuredLines.addAll(gameStateLines);
                gameStateLines.clear();
                gameStateLines.addAll(structuredLines);
                LOG.info("Scenario: merged {} structured player-setup lines with {} explicit game_state lines",
                        structuredLines.size() - si.gameState.size(), si.gameState.size());
            }

            // Detect Commander scenarios (any player has commanders defined)
            final boolean hasCommanders = si != null && !si.playerCommanders.isEmpty();

            final String dialogTitle = si != null && si.name != null ? si.name : "Scenario";
            final String dialogText = buildGameStartDialog(si);

            // Demo Play: record the full playthrough in detail so it can be converted into
            // events[] data afterward (see DemoPlaySequenceExtractor). Base name derived from
            // the scenario's own id/filename so the recording is easy to associate with it, and
            // written into SCENARIO_DIR (not GAME_LOG_DIR) so it never shows up in Game Recap.
            final String demoBaseName = demoPlay
                    ? "demo-play_" + safeFileToken(si != null && si.id != null ? si.id : parser.getReplayFile().getName())
                            + "_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date())
                    : null;
            final File demoRecordingFile = demoPlay
                    ? new File(ForgeConstants.SCENARIO_DIR, demoBaseName + ".json") : null;
            final File demoSnippetFile = demoPlay
                    ? new File(ForgeConstants.SCENARIO_DIR, demoBaseName + "_events_snippet.json") : null;

            hostedMatch.setStartGameHook(() -> {
                if (!gameStateLines.isEmpty()) {
                    forge.game.GameState gs = new forge.game.GameState();
                    gs.parse(gameStateLines);
                    gs.applyToGame(hostedMatch.getGame());
                }
                if (demoRecordingFile != null) {
                    hostedMatch.getGame().subscribeToEvents(
                            new ReplayEventLogger(hostedMatch.getGame(), demoRecordingFile.getPath()));
                    LOG.info("Demo Play: recording this playthrough to {}", demoRecordingFile);
                }
                if (!dialogText.isEmpty()) {
                    SOptionPane.showMessageDialog(dialogText, dialogTitle, SOptionPane.INFORMATION_ICON);
                }
            });

            if (demoPlay) {
                hostedMatch.setOnMatchOver(() -> {
                    try {
                        final com.google.gson.JsonArray events =
                                DemoPlaySequenceExtractor.extractPlayerEvents(demoRecordingFile, "P1");
                        DemoPlaySequenceExtractor.writeSnippet(demoRecordingFile, "P1", demoSnippetFile);
                        SwingUtilities.invokeLater(() -> {
                            if (events.size() == 0) {
                                SOptionPane.showMessageDialog(
                                        "Demo play recorded, but no CAST/ACTIVATE/PLAY_LAND actions were found "
                                                + "for P1 - nothing to encode.",
                                        "Demo Play Complete", SOptionPane.INFORMATION_ICON);
                                updateData();
                                return;
                            }
                            boolean update = SOptionPane.showConfirmDialog(
                                    "Demo play recorded " + events.size() + " action(s) for P1.\n\n"
                                            + "Update this scenario's \"events\" field with the recorded line now?\n"
                                            + "(A copy is also saved separately at:\n" + demoSnippetFile.getPath() + ")",
                                    "Demo Play Complete");
                            if (update) {
                                try {
                                    DemoPlaySequenceExtractor.updateScenarioEvents(parser.getReplayFile(), events);
                                    SOptionPane.showMessageDialog(
                                            "Scenario file updated:\n\n" + parser.getReplayFile().getPath(),
                                            "Scenario Updated", SOptionPane.INFORMATION_ICON);
                                } catch (Exception e) {
                                    LOG.error("Failed to update scenario file with demo-play events", e);
                                    SOptionPane.showMessageDialog(
                                            "Failed to update the scenario file: " + e.getMessage()
                                                    + "\n\nThe recorded events are still available at:\n"
                                                    + demoSnippetFile.getPath(),
                                            "Update Failed", FSkinProp.ICO_ERROR);
                                }
                            }
                            updateData(); // refresh so the Demoed column/button reflect the new recording
                        });
                    } catch (Exception e) {
                        LOG.error("Failed to extract demo-play events snippet from {}", demoRecordingFile, e);
                    }
                });
            }

            // Human player (index 0). A resolved deck gets used as-is (with its own commander,
            // via RegisteredPlayer.forCommander when this is a Commander scenario) instead of an
            // empty placeholder, so ScenarioLibrarySetup below can reorder that deck's actual
            // library rather than finding nothing to reorder.
            final List<RegisteredPlayer> players = new ArrayList<>();
            final RegisteredPlayer human;
            if (resolvedDeck != null) {
                human = (hasCommanders ? RegisteredPlayer.forCommander(resolvedDeck) : new RegisteredPlayer(resolvedDeck))
                        .setPlayer(GamePlayerUtil.getGuiPlayer());
            } else {
                human = new RegisteredPlayer(new Deck()).setPlayer(GamePlayerUtil.getGuiPlayer());
            }
            // Tracks the actual lobby name assigned to each seat this run, so a forced
            // play sequence (keyed by "P1"/"P2" in the JSON) can be translated to the
            // runtime name GameRules.setForcedPlaySequence()/AiController expect.
            final Map<String, String> idToLobbyName = new LinkedHashMap<>();
            idToLobbyName.put("P1", GamePlayerUtil.getGuiPlayer().getName());
            // Apply commander from player setup if present (placeholder-deck path only - a
            // resolved deck's own Commander section is already picked up above).
            if (resolvedDeck == null && si != null && si.playerCommanders.containsKey("P1")) {
                for (String cmdName : si.playerCommanders.get("P1")) {
                    forge.item.PaperCard cmdCard = FModel.getMagicDb().getCommonCards().getCard(cmdName);
                    if (cmdCard != null) {
                        human.getCommanders().add(cmdCard);
                    }
                }
            }
            players.add(human);

            // AI players (indices 1..playerCount-1). When a real deck was resolved for the human
            // seat, the AI opponent gets an inert lands-only "dummy" deck instead of an empty one
            // - these scenarios validate the human's line, not the AI's, and an empty deck here
            // would hit the same empty-library crash the human's used to (see the two fixes this
            // supersedes the root cause of).
            for (int i = 1; i < playerCount; i++) {
                final String aiName = "AI " + i;
                final RegisteredPlayer ai = new RegisteredPlayer(resolvedDeck != null ? buildDummyLandsDeck() : new Deck())
                        .setPlayer(GamePlayerUtil.createAiPlayer(aiName));
                if (resolvedDeck != null && hasCommanders) {
                    // No forCommander() here (the dummy deck has no commander to pull from) -
                    // Commander rule 903.7 still gives every player 40 life regardless.
                    ai.setStartingLife(40);
                }
                String aiPlayerId = "P" + (i + 1);
                idToLobbyName.put(aiPlayerId, aiName);
                if (resolvedDeck == null && si != null && si.playerCommanders.containsKey(aiPlayerId)) {
                    for (String cmdName : si.playerCommanders.get(aiPlayerId)) {
                        forge.item.PaperCard cmdCard = FModel.getMagicDb().getCommonCards().getCard(cmdName);
                        if (cmdCard != null) {
                            ai.getCommanders().add(cmdCard);
                        }
                    }
                }
                players.add(ai);
            }

            // Use Commander game type when commanders are present, otherwise Puzzle
            GameRules rules = new GameRules(hasCommanders ? GameType.Commander : GameType.Puzzle);
            rules.setGamesPerMatch(1);
            rules.setScenarioMode(true);  // disables achievement tracking and game log saving

            // Scenario library setup: pass defined starting hand + first draws to GameRules.
            // ScenarioLibrarySetup (called from GameAction) will reorder each player's library
            // so that the named cards appear at the front and are drawn normally.
            if (si != null && !si.playerStartingHands.isEmpty()) {
                rules.setScenarioStartingHands(si.playerStartingHands);
                if (!si.playerFirstDraws.isEmpty()) {
                    rules.setScenarioFirstDraws(si.playerFirstDraws);
                }
            }
            // For opening_hand_test: AI keeps its predefined hand — no mulligan dialog.
            // The human player may still mulligan freely.
            if (si != null && "opening_hand_test".equals(si.type)) {
                rules.setScenarioSkipMulligan(true);
            }

            // Forced play sequence (events array): reuses the same GameRules field and
            // AiController "Case 1" consumption logic that drives full-game Replay mode
            // (soft enforcement — an uncastable next card is left in the queue and retried
            // next priority instead of blocking normal play). Translate P1/P2 ids to the
            // lobby names actually assigned above so the AI's name lookup can find them.
            // Demo Play deliberately skips this even if the file has one - the whole point is
            // to play the guaranteed hand out fresh and discover a line, not replay an existing
            // script (see DemoPlaySequenceExtractor / startScenarioDemoPlay).
            if (!demoPlay && si != null && si.hasForcedPlaySequence()) {
                Map<String, List<String>> forcedSeq = si.buildForcedPlaySequenceForLobbyNames(idToLobbyName);
                if (!forcedSeq.isEmpty()) {
                    rules.setForcedPlaySequence(forcedSeq);
                    int total = forcedSeq.values().stream().mapToInt(List::size).sum();
                    LOG.info("Scenario: forced play sequence set — {} event(s) for {} player(s)",
                            total, forcedSeq.size());
                }
            }

            hostedMatch.startMatch(rules, null, players, human, GuiBase.getInterface().getNewGuiGame());

            SwingUtilities.invokeLater(SOverlayUtils::hideOverlay);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to start scenario", e);
            SwingUtilities.invokeLater(SOverlayUtils::hideOverlay);
            SOptionPane.showMessageDialog(
                    "Failed to start scenario: " + e.getMessage(),
                    "Error", FSkinProp.ICO_ERROR);
            return false;
        }
    }

    /** Sanitizes a scenario id/filename into a safe token for use inside a generated filename. */
    private static String safeFileToken(String s) {
        String token = s.endsWith(".json") ? s.substring(0, s.length() - ".json".length()) : s;
        return token.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String buildGameStartDialog(ScenarioInfo si) {
        if (si == null) return "";
        StringBuilder sb = new StringBuilder();
        if (si.description != null && !si.description.isEmpty()) {
            sb.append(si.description).append("\n\n");
        }
        if (si.question != null && !si.question.isEmpty()) {
            sb.append("Question:\n").append(si.question).append("\n\n");
        }
        if (si.answer != null && !si.answer.isEmpty()) {
            sb.append("Answer:\n").append(si.answer);
        }
        return sb.toString().trim();
    }

    @Override
    public void update() {
        MenuUtil.setMenuProvider(this);
        updateData();
    }

    @Override
    public List<JMenu> getMenus() {
        return new ArrayList<>();
    }
}
