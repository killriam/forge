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

            // Build start-game hook: apply game state, then show description dialog
            final List<String> gameStateLines = si != null ? new ArrayList<>(si.gameState) : new ArrayList<>();
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
            human.setStartingHand(0);
            players.add(human);

            // AI players (indices 1..playerCount-1)
            for (int i = 1; i < playerCount; i++) {
                final RegisteredPlayer ai = new RegisteredPlayer(new Deck())
                        .setPlayer(GamePlayerUtil.createAiPlayer("AI " + i));
                ai.setStartingHand(0);
                players.add(ai);
            }

            GameRules rules = new GameRules(GameType.Puzzle);
            rules.setGamesPerMatch(1);
            rules.setScenarioMode(true);  // disables achievement tracking and game log saving
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
