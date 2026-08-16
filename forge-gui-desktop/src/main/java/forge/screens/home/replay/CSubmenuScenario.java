package forge.screens.home.replay;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JMenu;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

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
import forge.util.Localizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Controller for the Replay Scenario submenu.
 * Scans the game log directory for scenario JSON files and lets the user
 * play them interactively, similar to puzzle mode.
 *
 * The scenario JSON's "scenario" object may include:
 *   "player_count": 2..N   (number of players to create, default 2)
 *   "game_state": ["key=value", ...]  (puzzle-format key=value lines)
 */
public enum CSubmenuScenario implements ICDoc, IMenuProvider {
    SINGLETON_INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(CSubmenuScenario.class);

    private final VSubmenuScenario view = VSubmenuScenario.SINGLETON_INSTANCE;
    private final Map<String, ReplayLogParser> scenarioParsers = new LinkedHashMap<>();

    /** Background worker for async file scanning - cancelled if the screen is re-entered. */
    private SwingWorker<List<Map.Entry<String, ReplayLogParser>>, Void> loadWorker;

    @Override
    public void register() {
    }

    @Override
    public void initialize() {
        view.getList().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        view.getList().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateScenarioInfo();
            }
        });
        view.getBtnStart().addActionListener(e -> startScenario());
        view.getBtnDemoPlay().addActionListener(e -> startScenarioDemoPlay());
        // Do NOT call updateData() here - every home-screen submenu's initialize() runs
        // unconditionally, synchronously, on the EDT at app startup (FView.initialize()), well
        // before the user has navigated here. ReplayLogParser.listScenarioFiles() parses every
        // *.json in the game-log directory - including full deck reconstruction (real
        // card-database lookups) - to find the handful that are actually scenarios, which with
        // a large gamelogs folder blocked the UI thread for seconds on every launch regardless
        // of whether this screen was ever opened. Lazy-load only when the screen is shown
        // instead, same fix already applied to CSubmenuReplay for the same reason.
        updateBtnEnablement();
    }

    /**
     * Scan the game log directory asynchronously and populate the scenario list. Only runs when
     * the screen is actually visible (called from update()), not at app startup.
     */
    private void updateData() {
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }

        scenarioParsers.clear();
        view.getModel().clear();
        updateBtnEnablement();

        loadWorker = new SwingWorker<>() {
            @Override
            protected List<Map.Entry<String, ReplayLogParser>> doInBackground() {
                List<Map.Entry<String, ReplayLogParser>> result = new ArrayList<>();
                Map<String, String> scenarioToDeck = buildScenarioToDeckIndex();
                // scenarioParsers is keyed by this display string (below, in done()) and the
                // JList itself only ever hands back a String on selection - two files that
                // happen to produce the same display name (e.g. both titled "Perfect Game",
                // whether coincidentally or because one is a literal duplicate of the other)
                // would otherwise silently collide: every visible row with that text would
                // resolve to whichever parser was put() last, so selecting what looks like your
                // scenario can silently open a different, unrelated one. Disambiguate by
                // filename the moment a repeat is seen.
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (ReplayLogParser parser : ReplayLogParser.listScenarioFiles()) {
                    if (isCancelled()) break;
                    String display = buildDisplayName(parser, scenarioToDeck);
                    if (!seen.add(display)) {
                        String fileToken = parser.getReplayFile().getName().replace(".json", "");
                        display = display + " — " + fileToken;
                        seen.add(display);
                        LOG.warn("Scenario display name collision - disambiguated as '{}'", display);
                    }
                    result.add(new AbstractMap.SimpleEntry<>(display, parser));
                }
                return result;
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    List<Map.Entry<String, ReplayLogParser>> result = get();
                    for (Map.Entry<String, ReplayLogParser> entry : result) {
                        scenarioParsers.put(entry.getKey(), entry.getValue());
                        view.getModel().addElement(entry.getKey());
                    }
                    LOG.info("Found {} scenario files", scenarioParsers.size());
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
        boolean hasItems = view.getModel().getSize() > 0;
        view.getBtnStart().setEnabled(hasItems);
        view.getBtnDemoPlay().setEnabled(hasItems);
    }

    /**
     * Builds a reverse index from every scenario id/filename token referenced by any deck's
     * {@code Scenario=} metadata to that deck's name - lets {@link #buildDisplayName} show a
     * deck name even for scenario files with no {@code meta.players.P1.deck_name} of their own
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
     * Builds the list entry text - always shows the deck the scenario is for when it's knowable
     * at all, so entries are identifiable by deck rather than a generic title or raw filename.
     * Deck name comes first from {@code meta.players.PX.deck_name} (authoritative - reflects
     * what was actually played), falling back to {@code scenarioToDeck} (reverse-lookup from any
     * deck's own {@code Scenario=} reference) when the file itself carries no player metadata.
     */
    private String buildDisplayName(ReplayLogParser parser, Map<String, String> scenarioToDeck) {
        ScenarioInfo si = parser.getScenarioInfo();
        String deckName = getPlayerDeckName(parser, "P1");
        String oppDeckName = getPlayerDeckName(parser, "P2");

        if (deckName == null) {
            deckName = lookupDeckName(parser, si, scenarioToDeck);
        }

        if (si != null && si.title != null) {
            String prefix = si.type != null ? "[" + si.type + "] " : "";
            String title = prefix + si.title;
            if (deckName != null) {
                title += " (" + deckName + ")";
            }
            return title;
        }

        if (deckName != null) {
            return oppDeckName != null && !oppDeckName.equals(deckName)
                    ? deckName + " vs " + oppDeckName
                    : deckName;
        }

        return parser.getReplayFile().getName();
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

    private static String getPlayerDeckName(ReplayLogParser parser, String playerId) {
        ReplayLogParser.PlayerInfo info = parser.getPlayers().get(playerId);
        return info != null ? info.deckName : null;
    }

    private void updateScenarioInfo() {
        String selected = view.getList().getSelectedValue();
        if (selected == null) {
            view.getScenarioInfo().setText("");
            return;
        }

        ReplayLogParser parser = scenarioParsers.get(selected);
        if (parser == null) {
            view.getScenarioInfo().setText("");
            return;
        }

        ScenarioInfo si = parser.getScenarioInfo();
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(parser.getReplayFile().getName()).append("\n\n");

        if (si != null) {
            if (si.type != null)  sb.append("Type:  ").append(si.type).append("\n");
            if (si.title != null) sb.append("Title: ").append(si.title).append("\n");
            sb.append("Players: ").append(si.playerCount).append("\n");
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
        String selected = view.getList().getSelectedValue();
        if (selected == null) {
            SOptionPane.showMessageDialog(
                    localizer.getMessage("lblPleaseSelectScenario"),
                    localizer.getMessage("lblNoSelectedScenario"),
                    FSkinProp.ICO_ERROR);
            return false;
        }

        ReplayLogParser parser = scenarioParsers.get(selected);
        if (parser == null) {
            return false;
        }

        return launchScenario(parser, demoPlay);
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

        SwingUtilities.invokeLater(() -> {
            SOverlayUtils.startGameOverlay();
            SOverlayUtils.showOverlay();
        });

        try {
            final HostedMatch hostedMatch = GuiBase.getInterface().hostMatch();

            // Merge explicit game_state lines with auto-generated lines from player setup
            final List<String> gameStateLines = si != null ? new ArrayList<>(si.gameState) : new ArrayList<>();
            if (si != null && si.hasPlayerSetup()) {
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

            final String dialogTitle = si != null && si.title != null ? si.title : "Scenario";
            final String dialogText = buildGameStartDialog(si);

            // Demo Play: record the full playthrough in detail so it can be converted into
            // events[] data afterward (see DemoPlaySequenceExtractor). Base name derived from
            // the scenario's own id/filename so the recording is easy to associate with it.
            final String demoBaseName = demoPlay
                    ? "demo-play_" + safeFileToken(si != null && si.id != null ? si.id : parser.getReplayFile().getName())
                            + "_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date())
                    : null;
            final File demoRecordingFile = demoPlay
                    ? new File(ForgeConstants.GAME_LOG_DIR, demoBaseName + ".json") : null;
            final File demoSnippetFile = demoPlay
                    ? new File(ForgeConstants.GAME_LOG_DIR, demoBaseName + "_events_snippet.json") : null;

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
                        DemoPlaySequenceExtractor.writeSnippet(demoRecordingFile, "P1", demoSnippetFile);
                        SwingUtilities.invokeLater(() -> SOptionPane.showMessageDialog(
                                "Demo play recorded. P1's actions were extracted to an events[] snippet:\n\n"
                                        + demoSnippetFile.getPath()
                                        + "\n\nPaste its contents into this scenario's \"events\" field to encode this line.",
                                "Demo Play Complete", SOptionPane.INFORMATION_ICON));
                    } catch (Exception e) {
                        LOG.error("Failed to extract demo-play events snippet from {}", demoRecordingFile, e);
                    }
                });
            }

            // Human player (index 0)
            final List<RegisteredPlayer> players = new ArrayList<>();
            final RegisteredPlayer human = new RegisteredPlayer(new Deck())
                    .setPlayer(GamePlayerUtil.getGuiPlayer());
            // Tracks the actual lobby name assigned to each seat this run, so a forced
            // play sequence (keyed by "P1"/"P2" in the JSON) can be translated to the
            // runtime name GameRules.setForcedPlaySequence()/AiController expect.
            final Map<String, String> idToLobbyName = new LinkedHashMap<>();
            idToLobbyName.put("P1", GamePlayerUtil.getGuiPlayer().getName());
            // Apply commander from player setup if present (for Commander game type support)
            if (si != null && si.playerCommanders.containsKey("P1")) {
                for (String cmdName : si.playerCommanders.get("P1")) {
                    forge.item.PaperCard cmdCard = FModel.getMagicDb().getCommonCards().getCard(cmdName);
                    if (cmdCard != null) {
                        human.getCommanders().add(cmdCard);
                    }
                }
            }
            players.add(human);

            // AI players (indices 1..playerCount-1)
            for (int i = 1; i < playerCount; i++) {
                final String aiName = "AI " + i;
                final RegisteredPlayer ai = new RegisteredPlayer(new Deck())
                        .setPlayer(GamePlayerUtil.createAiPlayer(aiName));
                String aiPlayerId = "P" + (i + 1);
                idToLobbyName.put(aiPlayerId, aiName);
                if (si != null && si.playerCommanders.containsKey(aiPlayerId)) {
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
