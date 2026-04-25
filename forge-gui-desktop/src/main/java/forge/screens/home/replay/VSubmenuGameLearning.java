package forge.screens.home.replay;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.screens.home.EMenuGroup;
import forge.screens.home.IVSubmenu;
import forge.screens.home.VHomeUI;
import forge.toolbox.FButton;
import forge.toolbox.FList;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * View for the Game Learning Viewer submenu.
 * Displays a recorded game turn-by-turn, showing game state and events.
 * Includes a button to launch a Replay of the recorded game.
 */
public enum VSubmenuGameLearning implements IVSubmenu<CSubmenuGameLearning> {
    SINGLETON_INSTANCE;

    private final FList<String> turnList;
    private final FScrollPane turnListPane;
    private final JTextArea stateArea;
    private final FScrollPane statePane;
    private final FButton btnPrev;
    private final FButton btnNext;
    private final FButton btnReplay;

    final DefaultListModel<String> turnModel = new DefaultListModel<>();

    private DragCell parentCell;
    final Localizer localizer = Localizer.getInstance();
    private final DragTab tab = new DragTab(localizer.getMessage("lblGameLearningViewer"));

    VSubmenuGameLearning() {
        turnList = new FList<>();
        turnListPane = new FScrollPane(turnList, true);

        stateArea = new JTextArea();
        stateArea.setEditable(false);
        stateArea.setLineWrap(true);
        stateArea.setWrapStyleWord(true);
        stateArea.setOpaque(false);
        stateArea.setFont(stateArea.getFont().deriveFont(12f));
        statePane = new FScrollPane(stateArea, true);

        btnPrev = new FButton(localizer.getMessage("lblPrevTurn"));
        btnNext = new FButton(localizer.getMessage("lblNextTurn"));
        btnReplay = new FButton(localizer.getMessage("lblReplayGame"));
    }

    @Override
    public EMenuGroup getGroupEnum() {
        return EMenuGroup.REPLAY;
    }

    @Override
    public String getMenuTitle() {
        return localizer.getMessage("lblGameLearningViewer");
    }

    @Override
    public EDocID getItemEnum() {
        return EDocID.HOME_GAME_LEARNING;
    }

    @Override
    public EDocID getDocumentID() {
        return EDocID.HOME_GAME_LEARNING;
    }

    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    @Override
    public CSubmenuGameLearning getLayoutControl() {
        return CSubmenuGameLearning.SINGLETON_INSTANCE;
    }

    @Override
    public void setParentCell(DragCell cell0) {
        this.parentCell = cell0;
    }

    @Override
    public DragCell getParentCell() {
        return this.parentCell;
    }

    public JList<String> getTurnList() {
        return turnList;
    }

    public DefaultListModel<String> getTurnModel() {
        return turnModel;
    }

    public JTextArea getStateArea() {
        return stateArea;
    }

    public FButton getBtnPrev() {
        return btnPrev;
    }

    public FButton getBtnNext() {
        return btnNext;
    }

    public FButton getBtnReplay() {
        return btnReplay;
    }

    @Override
    public void populate() {
        final JPanel container = VHomeUI.SINGLETON_INSTANCE.getPnlDisplay();

        container.removeAll();
        container.setLayout(new MigLayout("insets 10, gap 10, wrap 1"));

        // Title
        final JPanel titlePanel = new JPanel(new MigLayout("insets 0, gap 0"));
        titlePanel.setOpaque(false);
        javax.swing.JLabel lblTitle = new javax.swing.JLabel(localizer.getMessage("lblGameLearningViewerTitle"));
        lblTitle.setFont(lblTitle.getFont().deriveFont(18f));
        titlePanel.add(lblTitle, "pushx, growx");
        container.add(titlePanel, "w 96%!, gap 2% 2% 5px 5px");

        // Main split: left turn list, right state/events
        final JPanel mainPanel = new JPanel(new MigLayout("insets 0, gap 10"));
        mainPanel.setOpaque(false);
        turnList.setModel(turnModel);
        mainPanel.add(turnListPane, "w 30%!, h 100%!, growy");
        mainPanel.add(statePane, "w 68%!, h 100%!, growy, pushx, growx");
        container.add(mainPanel, "w 96%!, h 60%, gap 2% 2% 0 0, growy, pushy");

        // Navigation buttons
        final JPanel navPanel = new JPanel(new MigLayout("insets 0, gap 5"));
        navPanel.setOpaque(false);
        navPanel.add(btnPrev, "w 120px!, h 28px!");
        navPanel.add(btnNext, "w 120px!, h 28px!");
        navPanel.add(btnReplay, "w 160px!, h 28px!, gapleft 30px");
        container.add(navPanel, "w 96%!, gap 2% 2% 5px 5px");

        if (container.isShowing()) {
            container.validate();
            container.repaint();
        }
    }
}

