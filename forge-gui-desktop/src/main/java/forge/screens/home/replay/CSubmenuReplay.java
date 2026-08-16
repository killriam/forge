package forge.screens.home.replay;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JMenu;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.OriginalGameSummary;
import forge.game.ReplayGameStateBuilder;
import forge.game.ReplayLogParser;
import forge.game.ReplayLogParser.PlayerInfo;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.gui.SOverlayUtils;
import forge.gui.framework.FScreen;
import forge.gui.framework.ICDoc;
import forge.gui.util.SOptionPane;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.localinstance.skin.FSkinProp;
import forge.menus.IMenuProvider;
import forge.menus.MenuUtil;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.screens.gamelearning.CGameLearningUI;
import forge.Singletons;
import forge.util.Localizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Replay Game submenu.
 * Scans the game log directory for replay JSON files and lets the user
 * start an interactive game with the same decks and library order.
 */
public enum CSubmenuReplay implements ICDoc, IMenuProvider {
    SINGLETON_INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(CSubmenuReplay.class);

    /** Set before Forge GUI starts to auto-launch a replay on first home screen show. */
    private static String pendingReplayPath = null;

    /**
     * Called from Main.java (CLI mode "replay") before Forge initializes the GUI.
     * The replay will be started automatically once the home screen is first shown.
     */
    public static void setPendingReplayPath(String path) {
        pendingReplayPath = path;
    }

    private final VSubmenuReplay view = VSubmenuReplay.SINGLETON_INSTANCE;
    private final Map<String, ReplayLogParser> replayParsers = new LinkedHashMap<>();

    /** Background worker for async file scanning – cancelled if the screen is re-entered. */
    private SwingWorker<List<Map.Entry<String, ReplayLogParser>>, Void> loadWorker;

    @Override
    public void register() {
    }

    @Override
    public void initialize() {
        view.getList().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        view.getList().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateReplayInfo();
            }
        });
        view.getBtnStart().addActionListener(e -> startReplayGame());
        view.getBtnView().addActionListener(e -> openGameLearningViewer());
        view.getBtnView().setEnabled(false);

        // Sync days-filter combo with stored preference
        String storedDays = "2";
        try {
            storedDays = FModel.getPreferences().getPref(FPref.GAME_RECAP_DAYS);
        } catch (Exception ignored) { }
        view.getCmbDays().setSelectedItem(storedDays);

        // Re-load list whenever the days filter changes
        view.getCmbDays().addActionListener(e -> {
            String sel = (String) view.getCmbDays().getSelectedItem();
            if (sel != null) {
                FModel.getPreferences().setPref(FPref.GAME_RECAP_DAYS, sel);
                FModel.getPreferences().save();
                updateData();
                view.getBtnView().setEnabled(false);
            }
        });

        // Do NOT call updateData() here — lazy-load only when the screen is opened.
    }

    /**
     * Scan the game log directory asynchronously and populate the replay list.
     * Shows an indeterminate progress bar while loading; only runs when the screen is visible.
     */
    private void updateData() {
        // Cancel any in-progress load so we don't double-populate
        if (loadWorker != null && !loadWorker.isDone()) {
            loadWorker.cancel(true);
        }

        replayParsers.clear();
        view.getModel().clear();
        view.getLblCount().setText("Loading…");
        view.getBtnStart().setEnabled(false);
        view.getBtnView().setEnabled(false);
        view.getProgressBar().setVisible(true);

        // Read configurable recap days filter (0 = show all) once before background thread
        final int recapDays;
        int tmpDays = 2;
        try {
            tmpDays = Integer.parseInt(FModel.getPreferences().getPref(FPref.GAME_RECAP_DAYS));
        } catch (NumberFormatException ignored) { }
        recapDays = tmpDays;
        final long cutoffMs = recapDays > 0
                ? System.currentTimeMillis() - (recapDays * 86_400_000L)
                : 0;

        loadWorker = new SwingWorker<>() {
            @Override
            protected List<Map.Entry<String, ReplayLogParser>> doInBackground() {
                List<Map.Entry<String, ReplayLogParser>> result = new ArrayList<>();

                File logDir = new File(ForgeConstants.GAME_LOG_DIR);
                if (!logDir.exists() || !logDir.isDirectory()) {
                    LOG.info("Game log directory does not exist: {}", ForgeConstants.GAME_LOG_DIR);
                    return result;
                }

                File[] jsonFiles = logDir.listFiles((dir, name) -> name.endsWith(".json"));
                if (jsonFiles == null || jsonFiles.length == 0) {
                    LOG.info("No replay JSON files found in: {}", ForgeConstants.GAME_LOG_DIR);
                    return result;
                }

                // Sort by modification time (newest first)
                Arrays.sort(jsonFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                for (File jsonFile : jsonFiles) {
                    if (isCancelled()) break;

                    // Apply time filter
                    if (cutoffMs > 0 && jsonFile.lastModified() < cutoffMs) {
                        continue;
                    }
                    // Simulation files (sim_*.json) are AI-only runs — exclude from Game Recap
                    if (jsonFile.getName().startsWith("sim_")) {
                        continue;
                    }

                    ReplayLogParser parser = new ReplayLogParser(jsonFile);
                    if (parser.parse() && !parser.isScenario()) {
                        String display = buildRecapDisplayString(parser);
                        result.add(new AbstractMap.SimpleEntry<>(display, parser));
                    }
                }
                return result;
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    view.getProgressBar().setVisible(false);
                    return;
                }
                try {
                    List<Map.Entry<String, ReplayLogParser>> result = get();
                    for (Map.Entry<String, ReplayLogParser> entry : result) {
                        replayParsers.put(entry.getKey(), entry.getValue());
                        view.getModel().addElement(entry.getKey());
                    }

                    // Count replayed games and update the counter label
                    int replayedCount = 0;
                    for (ReplayLogParser p : replayParsers.values()) {
                        if (p.isReplayed()) replayedCount++;
                    }
                    int total = replayParsers.size();
                    view.getLblCount().setText(total + " game" + (total != 1 ? "s" : "")
                            + "  |  " + replayedCount + " replayed");

                    LOG.info("Found {} valid replay files ({} replayed, recap days: {})", total, replayedCount, recapDays);
                } catch (Exception e) {
                    LOG.error("Failed to load replay files", e);
                    view.getLblCount().setText("Error loading replays");
                } finally {
                    view.getProgressBar().setVisible(false);
                }
            }
        };
        loadWorker.execute();
    }

    /**
     * Build the Game Recap display string:
     * "2026-04-03 14:22 | Atraxa Praetors, Elves Tribal | 12 turns | Win"
     */
    private String buildRecapDisplayString(ReplayLogParser parser) {
        StringBuilder sb = new StringBuilder();

        // Date and time
        if (parser.getTimestamp() != null) {
            String ts = parser.getTimestamp().replace("T", " ");
            // Strip trailing Z and fractional seconds for clean display
            int dotIdx = ts.indexOf('.');
            if (dotIdx > 0) {
                ts = ts.substring(0, dotIdx);
            }
            ts = ts.replace("Z", "");
            // Trim to minute precision (yyyy-MM-dd HH:mm)
            if (ts.length() > 16) {
                ts = ts.substring(0, 16);
            }
            sb.append(ts);
        }

        // Deck names (comma-separated)
        sb.append(" | ");
        boolean first = true;
        for (PlayerInfo p : parser.getPlayers().values()) {
            if (!first) sb.append(", ");
            sb.append(p.deckName != null ? p.deckName : "Unknown Deck");
            first = false;
        }

        // Turn count
        if (parser.getTurns() != null) {
            sb.append(" | ").append(parser.getTurns()).append(" turns");
        }

        // Outcome for the user (first player = P1)
        sb.append(" | ");
        String winnerId = parser.getWinner();
        if (winnerId == null) {
            sb.append("Unknown");
        } else {
            // The first player key is the human player
            String firstPlayerId = parser.getPlayers().keySet().iterator().next();
            if (winnerId.equals(firstPlayerId)) {
                sb.append("Win");
            } else {
                sb.append("Loss");
            }
        }

        // Mark games that have already been replayed
        if (parser.isReplayed()) {
            sb.append(" [Replayed]");
        }

        return sb.toString();
    }

    /**
     * Update the info panel when a replay is selected.
     */
    private void updateReplayInfo() {
        String selected = view.getList().getSelectedValue();
        if (selected == null) {
            view.getReplayInfo().setText("");
            view.getBtnView().setEnabled(false);
            return;
        }

        ReplayLogParser parser = replayParsers.get(selected);
        if (parser == null) {
            view.getReplayInfo().setText("");
            view.getBtnView().setEnabled(false);
            return;
        }
        // Only reconstructs for this one selected file, not the whole list - see
        // ReplayLogParser.ensureDecksReconstructed().
        parser.ensureDecksReconstructed();

        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(parser.getReplayFile().getName()).append("\n");

        view.getBtnStart().setEnabled(true);
        view.getBtnView().setEnabled(true);
        sb.append("\n=== Replay Info ===\n\n");
        if (parser.getTimestamp() != null) {
            sb.append("Date: ").append(parser.getTimestamp().replace("T", " ").replace("Z", "")).append("\n");
        }
        if (parser.getGameType() != null) {
            sb.append("Game Type: ").append(parser.getGameType()).append("\n");
        }
        if (parser.getTurns() != null) {
            sb.append("Turns: ").append(parser.getTurns()).append("\n");
        }
        if (parser.getDurationSeconds() != null) {
            sb.append("Duration: ").append(parser.getDurationSeconds()).append("s\n");
        }
        sb.append("\n--- Players ---\n");

        for (Map.Entry<String, PlayerInfo> entry : parser.getPlayers().entrySet()) {
            PlayerInfo p = entry.getValue();
            sb.append("\n").append(entry.getKey()).append(": ").append(p.name);
            if (p.deckName != null) {
                sb.append("\n  Deck: ").append(p.deckName);
            }
            if (p.deck != null && p.deck.getMain() != null) {
                sb.append("\n  Main Deck: ").append(p.deck.getMain().countAll()).append(" cards");
            }
            if (p.deck != null && p.deck.has(DeckSection.Commander)) {
                sb.append("\n  Commander: ").append(p.deck.get(DeckSection.Commander).countAll()).append(" cards");
            }
            sb.append("\n  Starting Life: ").append(p.startingLife);
            sb.append("\n  Type: ").append(p.isAi ? "AI" : "Human");
            if (parser.getWinner() != null && parser.getWinner().equals(entry.getKey())) {
                sb.append(" [WINNER]");
            }
            sb.append("\n");
        }

        sb.append("\n--- Replay Mode ---\n");
        sb.append("You will play as the first human player.\n");
        sb.append("The library order will match the original game.\n");
        sb.append("AI opponents keep the same decks.\n");

        view.getReplayInfo().setText(sb.toString());
        view.getReplayInfo().setCaretPosition(0);
    }

    /**
     * Start a replay game directly from a file path (used by CLI "replay" mode).
     * Does not require the file to be in the game log directory.
     */
    public void startReplayFromPath(String replayFilePath) {
        File file = new File(replayFilePath);
        if (!file.exists() || !file.isFile()) {
            SOptionPane.showMessageDialog(
                    "Replay file not found:\n" + replayFilePath,
                    "File Not Found", FSkinProp.ICO_ERROR);
            return;
        }

        ReplayLogParser parser = new ReplayLogParser(file);
        if (!parser.parse()) {
            SOptionPane.showMessageDialog(
                    "Could not parse replay file:\n" + file.getName() + "\n\nFile must be a valid mtg-replay JSON.",
                    "Invalid Replay File", FSkinProp.ICO_ERROR);
            return;
        }

        if (parser.isReplayed()) {
            boolean confirmed = SOptionPane.showConfirmDialog(
                    "This replay has already been played before.\nStart it again anyway?",
                    "Already Replayed");
            if (!confirmed) {
                return;
            }
        }

        launchReplay(parser);
    }

    /**
     * Start a replay with explicit draw-order enforcement and original game summary.
     * Called by CSubmenuGameLearning after the user confirms the replay popup.
     *
     * @param replayFilePath   path to the replay JSON file
     * @param enforceDrawOrder if true, force the original library order; if false, shuffle normally
     * @param branchTurn       turn number the user was viewing when clicking "Replay"
     * @param originalSummary  pre-computed original game summary for post-replay comparison (may be null)
     */
    public void startReplayFromPath(String replayFilePath, boolean enforceDrawOrder,
                                     int branchTurn, OriginalGameSummary originalSummary) {
        File file = new File(replayFilePath);
        if (!file.exists() || !file.isFile()) {
            SOptionPane.showMessageDialog(
                    "Replay file not found:\n" + replayFilePath,
                    "File Not Found", FSkinProp.ICO_ERROR);
            return;
        }

        ReplayLogParser parser = new ReplayLogParser(file);
        if (!parser.parse()) {
            SOptionPane.showMessageDialog(
                    "Could not parse replay file:\n" + file.getName() + "\n\nFile must be a valid mtg-replay JSON.",
                    "Invalid Replay File", FSkinProp.ICO_ERROR);
            return;
        }

        launchReplay(parser, enforceDrawOrder, branchTurn, originalSummary);
    }

    /**
     * Start an interactive game using the selected replay.
     */
    private boolean startReplayGame() {
        String selected = view.getList().getSelectedValue();
        if (selected == null) {
            final Localizer localizer = Localizer.getInstance();
            SOptionPane.showMessageDialog(
                    localizer.getMessage("lblPleaseSelectReplay"),
                    localizer.getMessage("lblNoSelectedReplay"),
                    FSkinProp.ICO_ERROR);
            return false;
        }

        ReplayLogParser parser = replayParsers.get(selected);
        if (parser == null) {
            return false;
        }

        boolean started = launchReplay(parser);
        if (started) {
            // Remove from current UI list immediately
            view.getModel().removeElement(selected);
            replayParsers.remove(selected);
        }
        return started;
    }

    /**
     * Open the selected replay in the Game Learning Viewer (opens as a new top-level tab).
     */
    private void openGameLearningViewer() {
        String selected = view.getList().getSelectedValue();
        if (selected == null) return;
        ReplayLogParser parser = replayParsers.get(selected);
        if (parser == null) return;

        CGameLearningUI.setPendingParser(parser);
        Singletons.getControl().setCurrentScreen(FScreen.GAME_LEARNING_SCREEN);
    }

    /**
     * Core launch logic shared by GUI selection and CLI path mode.
     */
    private boolean launchReplay(ReplayLogParser parser) {
        return launchReplay(parser, true, 1, null);
    }

    /**
     * Core launch logic with explicit draw-order enforcement and comparison data.
     *
     * @param parser           parsed replay file
     * @param enforceDrawOrder whether to force original library order
     * @param branchTurn       the turn from which the user branched (for comparison)
     * @param originalSummary  pre-computed original game summary (may be null)
     */
    private boolean launchReplay(ReplayLogParser parser, boolean enforceDrawOrder,
                                  int branchTurn, OriginalGameSummary originalSummary) {
        // Covers all three launch paths (list Start, CLI direct path, Game Learning Viewer's
        // "Replay from here") - no-op if updateReplayInfo() already reconstructed this parser.
        parser.ensureDecksReconstructed();

        // Validate that we have at least 2 players with decks
        if (parser.getPlayers().size() < 2) {
            SOptionPane.showMessageDialog(
                    "This replay file does not contain enough player data.",
                    "Invalid Replay", FSkinProp.ICO_ERROR);
            return false;
        }

        // Check that at least one player has a non-empty deck
        boolean hasCards = false;
        for (PlayerInfo p : parser.getPlayers().values()) {
            if (p.deck != null && p.deck.getMain() != null && p.deck.getMain().countAll() > 0) {
                hasCards = true;
                break;
            }
        }
        if (!hasCards) {
            SOptionPane.showMessageDialog(
                    "Could not reconstruct any decks from this replay file.\n" +
                    "The replay may be in an older format without card data.",
                    "No Deck Data", FSkinProp.ICO_WARNING);
            return false;
        }

        SwingUtilities.invokeLater(() -> {
            SOverlayUtils.startGameOverlay();
            SOverlayUtils.showOverlay();
        });

        try {
            // Determine game type
            GameType resolvedGameType = resolveGameType(parser.getGameType());
            boolean isCommander = resolvedGameType == GameType.Commander
                    || resolvedGameType == GameType.Oathbreaker
                    || resolvedGameType == GameType.TinyLeaders
                    || resolvedGameType == GameType.Brawl;

            // Fallback: if game_type metadata says "Constructed" but the reconstructed decks
            // contain a Commander section, treat this as a Commander game.  This handles games
            // that were logged with an incorrect game_type (e.g. forge logging Commander as
            // "Constructed") so that the Commander zone is properly set up.
            if (!isCommander) {
                for (PlayerInfo pInfo : parser.getPlayers().values()) {
                    if (pInfo.deck != null && pInfo.deck.has(DeckSection.Commander)
                            && pInfo.deck.get(DeckSection.Commander).countAll() > 0) {
                        isCommander = true;
                        resolvedGameType = GameType.Commander;
                        LOG.info("Commander section detected in deck — upgrading game type to Commander");
                        break;
                    }
                }
            }

            // Build registered players
            List<RegisteredPlayer> registeredPlayers = new ArrayList<>();
            RegisteredPlayer humanRegistered = null;

            int playerIndex = 0;
            for (Map.Entry<String, PlayerInfo> entry : parser.getPlayers().entrySet()) {
                PlayerInfo pInfo = entry.getValue();
                Deck deck = pInfo.deck;
                if (deck == null) {
                    deck = new Deck("Empty");
                }

                RegisteredPlayer rp;
                if (isCommander && deck.has(DeckSection.Commander)) {
                    rp = RegisteredPlayer.forCommander(deck);
                } else {
                    rp = new RegisteredPlayer(deck);
                }

                rp.setStartingLife(pInfo.startingLife);

                // First player slot: human (user plays), rest: AI
                if (playerIndex == 0) {
                    rp.setPlayer(GamePlayerUtil.getGuiPlayer());
                    humanRegistered = rp;
                } else {
                    String aiName = pInfo.name != null ? pInfo.name : "AI";
                    rp.setPlayer(GamePlayerUtil.createAiPlayer(aiName));
                }

                registeredPlayers.add(rp);
                playerIndex++;
            }

            // Setup game rules with replay log path for library reordering
            GameRules rules = new GameRules(resolvedGameType);
            rules.setGamesPerMatch(1);
            rules.setReplayLogPath(parser.getReplayFile().getAbsolutePath());

            // Replay Mode: force original library order based on user choice
            if (enforceDrawOrder) {
                rules.setReplayMode(true);
                Map<String, List<String>> libOrder = parser.getInitialLibraryOrder();
                if (!libOrder.isEmpty()) {
                    rules.setForcedLibraryOrder(libOrder);
                }
                rules.setShuffleRestore("always");
            } else {
                rules.setReplayMode(true);
                rules.setForcedLibraryOrder(null);
                rules.setShuffleRestore("never");
            }
            // Don't auto-save a new replay file when replaying an existing one
            rules.setAutoSaveReplay(false);

            // Determine who goes first from the original game's starting_player
            String startingPlayerId = parser.getStartingPlayer();
            if (startingPlayerId != null) {
                int spIdx = 0;
                for (String pid : parser.getPlayers().keySet()) {
                    if (pid.equals(startingPlayerId)) {
                        rules.setReplayStartingPlayerIndex(spIdx);
                        break;
                    }
                    spIdx++;
                }
            }

            // Set comparison data for ViewWinLose post-game screen
            rules.setOriginalReplayFile(parser.getReplayFile().getAbsolutePath());
            rules.setReplayBranchTurn(branchTurn);
            if (originalSummary != null) {
                rules.setOriginalGameSummary(originalSummary);
            }

            // Apply variants if needed
            Set<GameType> appliedVariants = null;
            if (isCommander) {
                appliedVariants = EnumSet.of(resolvedGameType);
            }

            // Mark the source log as replayed
            parser.markAsReplayed();

            // Start match — use different setup depending on whether we branch mid-game
            final HostedMatch hostedMatch = GuiBase.getInterface().hostMatch();

            if (branchTurn > 1) {
                // ---- Mid-game replay: reconstruct game state at the target turn ----
                LOG.info("Mid-game replay: reconstructing state at turn {}", branchTurn);

                // Build GameState lines from the replay JSON
                ReplayGameStateBuilder stateBuilder = new ReplayGameStateBuilder(parser);
                final List<String> stateLines = stateBuilder.buildStateAtTurn(branchTurn);

                if (stateLines.isEmpty()) {
                    LOG.warn("Failed to build game state at turn {} — falling back to Turn 1 replay", branchTurn);
                    hostedMatch.startMatch(rules, appliedVariants, registeredPlayers,
                            humanRegistered, GuiBase.getInterface().getNewGuiGame());
                } else {
                    for (String line : stateLines) {
                        LOG.debug("GameState line: {}", line);
                    }

                    // Use Puzzle pattern: empty decks + startGameHook
                    List<RegisteredPlayer> puzzlePlayers = new ArrayList<>();
                    RegisteredPlayer puzzleHuman = null;
                    int pIdx = 0;
                    for (Map.Entry<String, PlayerInfo> entry : parser.getPlayers().entrySet()) {
                        PlayerInfo pInfo = entry.getValue();
                        RegisteredPlayer rp = new RegisteredPlayer(new Deck("Replay"));
                        rp.setStartingHand(0);
                        rp.setStartingLife(pInfo.startingLife);
                        if (pIdx == 0) {
                            rp.setPlayer(GamePlayerUtil.getGuiPlayer());
                            puzzleHuman = rp;
                        } else {
                            String aiName = pInfo.name != null ? pInfo.name : "AI";
                            rp.setPlayer(GamePlayerUtil.createAiPlayer(aiName));
                        }
                        puzzlePlayers.add(rp);
                        pIdx++;
                    }

                    // Set the hook that applies the game state before the game starts
                    hostedMatch.setStartGameHook(() -> {
                        try {
                            LOG.info("Applying mid-game state: {} lines for turn {}", stateLines.size(), branchTurn);
                            forge.game.GameState gameState = new forge.game.GameState();
                            gameState.parse(stateLines);
                            gameState.applyToGame(hostedMatch.getGame());
                            LOG.info("Mid-game state applied successfully at turn {}", branchTurn);
                        } catch (Exception ex) {
                            LOG.error("Failed to apply mid-game state at turn " + branchTurn, ex);
                        }
                    });

                    hostedMatch.startMatch(rules, appliedVariants, puzzlePlayers,
                            puzzleHuman, GuiBase.getInterface().getNewGuiGame());
                }
            } else {
                // ---- Turn 1 replay: use full decks with library reorder ----
                hostedMatch.startMatch(rules, appliedVariants, registeredPlayers,
                        humanRegistered, GuiBase.getInterface().getNewGuiGame());
            }

            SwingUtilities.invokeLater(SOverlayUtils::hideOverlay);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to start replay game", e);
            SwingUtilities.invokeLater(SOverlayUtils::hideOverlay);
            SOptionPane.showMessageDialog(
                    "Failed to start replay game: " + e.getMessage(),
                    "Error", FSkinProp.ICO_ERROR);
            return false;
        }
    }

    /**
     * Resolve a game type string from the replay to a GameType enum.
     */
    private GameType resolveGameType(String gameTypeStr) {
        if (gameTypeStr == null) return GameType.Constructed;

        String lower = gameTypeStr.toLowerCase();
        if (lower.contains("commander")) return GameType.Commander;
        if (lower.contains("oathbreaker")) return GameType.Oathbreaker;
        if (lower.contains("tiny")) return GameType.TinyLeaders;
        if (lower.contains("brawl")) return GameType.Brawl;
        if (lower.contains("sealed")) return GameType.Sealed;
        if (lower.contains("draft")) return GameType.Draft;
        // Default to Constructed
        return GameType.Constructed;
    }

    @Override
    public void update() {
        MenuUtil.setMenuProvider(this);

        // Sync days-filter combo with stored preference (in case changed in Settings)
        try {
            String storedDays = FModel.getPreferences().getPref(FPref.GAME_RECAP_DAYS);
            view.getCmbDays().setSelectedItem(storedDays);
        } catch (Exception ignored) { }

        // Refresh list each time the screen is shown so that replayed files are excluded
        updateData();
        view.getBtnView().setEnabled(false);

        // Auto-launch replay if a path was passed on the command line
        if (pendingReplayPath != null) {
            final String path = pendingReplayPath;
            pendingReplayPath = null; // consume immediately so it doesn't trigger again
            SwingUtilities.invokeLater(() -> startReplayFromPath(path));
        }
    }

    @Override
    public List<JMenu> getMenus() {
        return new ArrayList<>();
    }
}











