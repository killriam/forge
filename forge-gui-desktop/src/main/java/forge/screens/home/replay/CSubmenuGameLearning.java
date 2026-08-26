package forge.screens.home.replay;

import forge.game.ReplayLogParser;
import forge.game.ReplayStateReconstructor;
import forge.game.ReplayStateReconstructor.TurnSnapshot;
import forge.gui.framework.EDocID;
import forge.gui.framework.ICDoc;
import forge.menus.IMenuProvider;
import forge.menus.MenuUtil;
import forge.screens.home.CHomeUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Game Learning Viewer submenu (embedded in home navigation).
 *
 * NOTE: The full-featured Game Learning Viewer is now opened as a top-level tab
 * via CGameLearningUI / FScreen.GAME_LEARNING_SCREEN. This class acts as a
 * simple stub that redirects to the top-level screen when a parser is pending.
 */
public enum CSubmenuGameLearning implements ICDoc, IMenuProvider {
    SINGLETON_INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(CSubmenuGameLearning.class);

    private static ReplayLogParser pendingParser = null;

    /** Called by CSubmenuReplay when the user clicks "View". */
    public static void openForReplay(ReplayLogParser parser) {
        pendingParser = parser;
        SwingUtilities.invokeLater(() ->
                CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.HOME_GAME_LEARNING));
    }

    private final VSubmenuGameLearning view = VSubmenuGameLearning.SINGLETON_INSTANCE;

    private ReplayStateReconstructor reconstructor = null;
    private ReplayLogParser currentParser = null;
    private List<TurnSnapshot> turns = new ArrayList<>();
    private int currentTurnIndex = 0;

    @Override
    public void register() {
    }

    @Override
    public void initialize() {
        view.getTurnList().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = view.getTurnList().getSelectedIndex();
                if (idx >= 0) {
                    currentTurnIndex = idx;
                    showTurn(idx);
                }
            }
        });

        view.getBtnPrev().addActionListener(e -> navigateTurn(-1));
        view.getBtnNext().addActionListener(e -> navigateTurn(+1));
        view.getBtnReplay().addActionListener(e -> launchReplay());
    }

    @Override
    public void update() {
        MenuUtil.setMenuProvider(null);

        // Consume pending parser if set
        if (pendingParser != null) {
            load(pendingParser);
            pendingParser = null;
        }
    }

    /** Load and reconstruct a replay for viewing. */
    private void load(ReplayLogParser parser) {
        this.currentParser = parser;
        this.reconstructor = new ReplayStateReconstructor(parser);
        this.turns = new ArrayList<>(reconstructor.getTurns());
        this.currentTurnIndex = 0;

        Map<String, String> names = reconstructor.getPlayerNames();

        // Populate turn list
        view.getTurnModel().clear();
        for (TurnSnapshot turn : turns) {
            view.getTurnModel().addElement(turn.getSummary(names));
        }

        if (!turns.isEmpty()) {
            view.getTurnList().setSelectedIndex(0);
            showTurn(0);
        } else {
            view.getStateArea().setText("No turns found in this replay.");
        }

        updateNavButtons();
    }

    private void showTurn(int idx) {
        if (idx < 0 || idx >= turns.size()) return;
        TurnSnapshot turn = turns.get(idx);
        Map<String, String> names = reconstructor.getPlayerNames();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Turn ").append(turn.turnNumber);
        if (turn.activePlayerId != null) {
            String name = names.getOrDefault(turn.activePlayerId, turn.activePlayerId);
            sb.append(" \u2014 ").append(name).append("'s Turn");
        }
        sb.append(" ===\n\n");

        // Player states
        sb.append("Player States:\n");
        for (Map.Entry<String, Integer> entry : turn.lifeTotals.entrySet()) {
            String pid = entry.getKey();
            String name = names.getOrDefault(pid, pid);
            int life = entry.getValue();
            int hand = turn.handSizes.getOrDefault(pid, 0);
            int lib = turn.librarySizes.getOrDefault(pid, 0);
            sb.append("  ").append(name);
            if (currentParser != null && currentParser.getPlayers().containsKey(pid)) {
                Integer t = currentParser.getPlayers().get(pid).team;
                if (t != null) {
                    sb.append(" [Team ").append(t).append("]");
                }
            }
            sb.append(": ")
              .append(life).append(" life | ")
              .append("Hand: ").append(hand).append(" | ")
              .append("Library: ").append(lib).append("\n");
        }

        // Events
        sb.append("\nEvents this turn:\n");
        if (turn.events.isEmpty()) {
            sb.append("  (no events recorded)\n");
        } else {
            for (ReplayStateReconstructor.EventEntry event : turn.events) {
                sb.append("  ").append(event).append("\n");
            }
        }

        view.getStateArea().setText(sb.toString());
        view.getStateArea().setCaretPosition(0);
        currentTurnIndex = idx;
        updateNavButtons();
    }

    private void navigateTurn(int delta) {
        int newIdx = currentTurnIndex + delta;
        if (newIdx >= 0 && newIdx < turns.size()) {
            currentTurnIndex = newIdx;
            view.getTurnList().setSelectedIndex(currentTurnIndex);
            showTurn(currentTurnIndex);
        }
    }

    private void updateNavButtons() {
        view.getBtnPrev().setEnabled(currentTurnIndex > 0);
        view.getBtnNext().setEnabled(currentTurnIndex < turns.size() - 1);
        view.getBtnReplay().setEnabled(currentParser != null);
    }

    /**
     * Show the replay confirmation popup and launch the replay if the user confirms.
     */
    private void launchReplay() {
        if (currentParser == null) return;

        int turnNumber = 1;
        if (currentTurnIndex >= 0 && currentTurnIndex < turns.size()) {
            turnNumber = turns.get(currentTurnIndex).turnNumber;
        }

        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        JLabel lblDesc = new JLabel("<html><body style='width:340px'>"
                + "Start a new game from <b>Turn " + turnNumber + "</b> with the same decks. "
                + "Try different decisions and see if you reach a better outcome."
                + "</body></html>");
        lblDesc.setFont(lblDesc.getFont().deriveFont(Font.PLAIN, 13f));
        lblDesc.setAlignmentX(0f);
        panel.add(lblDesc);
        panel.add(javax.swing.Box.createVerticalStrut(10));

        JCheckBox chkEnforce = new JCheckBox("Enforce Cards drawn order", true);
        chkEnforce.setAlignmentX(0f);
        panel.add(chkEnforce);

        String title = "Replay from Turn " + turnNumber;
        int result = JOptionPane.showConfirmDialog(
                null, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        boolean enforceDrawOrder = chkEnforce.isSelected();
        CSubmenuReplay.SINGLETON_INSTANCE.startReplayFromPath(
                currentParser.getReplayFile().getAbsolutePath(),
                enforceDrawOrder,
                turnNumber,
                null);
    }

    @Override
    public List<JMenu> getMenus() {
        return new ArrayList<>();
    }
}
