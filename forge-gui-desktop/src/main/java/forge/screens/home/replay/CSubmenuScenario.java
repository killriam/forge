package forge.screens.home.replay;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JMenu;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.ReplayLogParser;
import forge.game.ReplayLogParser.ScenarioInfo;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.gui.SOverlayUtils;
import forge.gui.framework.ICDoc;
import forge.gui.util.SOptionPane;
import forge.item.IPaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.skin.FSkinProp;
import forge.menus.IMenuProvider;
import forge.menus.MenuUtil;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.util.Localizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        updateData();
    }

    private void updateData() {
        scenarioParsers.clear();
        view.getModel().clear();

        File logDir = new File(ForgeConstants.GAME_LOG_DIR);
        if (!logDir.exists() || !logDir.isDirectory()) {
            return;
        }

        File[] jsonFiles = logDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            return;
        }

        Arrays.sort(jsonFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        for (File jsonFile : jsonFiles) {
            ReplayLogParser parser = new ReplayLogParser(jsonFile);
            if (parser.parse() && parser.isScenario()) {
                String display = buildDisplayName(parser);
                scenarioParsers.put(display, parser);
                view.getModel().addElement(display);
            }
        }

        LOG.info("Found {} scenario files", scenarioParsers.size());
    }

    private String buildDisplayName(ReplayLogParser parser) {
        ScenarioInfo si = parser.getScenarioInfo();
        if (si != null && si.title != null) {
            String prefix = si.type != null ? "[" + si.type + "] " : "";
            return prefix + si.title;
        }
        return parser.getReplayFile().getName();
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

        return launchScenario(parser);
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
    private boolean launchScenario(ReplayLogParser parser) {
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

            hostedMatch.setStartGameHook(() -> {
                if (!gameStateLines.isEmpty()) {
                    ScenarioGameState gs = new ScenarioGameState();
                    gs.parse(gameStateLines);
                    gs.applyToGame(hostedMatch.getGame());
                }
                if (!dialogText.isEmpty()) {
                    SOptionPane.showMessageDialog(dialogText, dialogTitle, SOptionPane.INFORMATION_ICON);
                }
            });

            // Human player (index 0)
            final List<RegisteredPlayer> players = new ArrayList<>();
            final RegisteredPlayer human = new RegisteredPlayer(new Deck())
                    .setPlayer(GamePlayerUtil.getGuiPlayer());
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
                final RegisteredPlayer ai = new RegisteredPlayer(new Deck())
                        .setPlayer(GamePlayerUtil.createAiPlayer("AI " + i));
                String aiPlayerId = "P" + (i + 1);
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
    }

    @Override
    public List<JMenu> getMenus() {
        return new ArrayList<>();
    }

    /**
     * Minimal concrete GameState that resolves card names against the main Forge card database.
     * Used to parse and apply puzzle-format game state lines from scenario JSON files.
     */
    private static class ScenarioGameState extends forge.ai.GameState {
        @Override
        public IPaperCard getPaperCard(String cardName, String setCode, int artID) {
            return FModel.getMagicDb().getCommonCards().getCard(cardName, setCode, artID);
        }
    }
}
