package forge.screens.home.replay;

import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;

import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.screens.home.EMenuGroup;
import forge.screens.home.IVSubmenu;
import forge.screens.home.StartButton;
import forge.screens.home.VHomeUI;
import forge.toolbox.FButton;
import forge.toolbox.FList;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * View for the Replay Game submenu.
 * Displays available replay log files and allows the user to start
 * an interactive game using the same deck/library order.
 */
public enum VSubmenuReplay implements IVSubmenu<CSubmenuReplay> {
    SINGLETON_INSTANCE;

    private final FList<String> replayList;
    private final FScrollPane replayListPane;
    private final JTextArea replayInfo;
    private final FScrollPane replayInfoPane;
    /** Shows total-game and replayed counts next to the title. */
    private final JLabel lblCount = new JLabel();

    /** Indeterminate progress bar shown while replay files are being loaded. */
    private final JProgressBar progressBar;

    final DefaultListModel<String> model = new DefaultListModel<>();

    private final StartButton btnStart = new StartButton();
    private final FButton btnView = new FButton();

    /** Days-filter combo: 0=all, 1,2,3,5,7,14,30 */
    private final JComboBox<String> cmbDays = new JComboBox<>(
            new String[]{"0", "1", "2", "3", "5", "7", "14", "30"});

    private DragCell parentCell;
    final Localizer localizer = Localizer.getInstance();
    private final DragTab tab = new DragTab(localizer.getMessage("lblReplayMode"));

    VSubmenuReplay() {
        replayList = new FList<>();
        replayListPane = new FScrollPane(this.replayList, true);
        replayInfo = new JTextArea();
        replayInfo.setEditable(false);
        replayInfo.setLineWrap(true);
        replayInfo.setWrapStyleWord(true);
        replayInfo.setOpaque(false);
        replayInfoPane = new FScrollPane(replayInfo, true);
        btnView.setText(localizer.getMessage("lblViewReplay"));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("Loading replays…");
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
    }

    @Override
    public EMenuGroup getGroupEnum() {
        return EMenuGroup.REPLAY;
    }

    @Override
    public String getMenuTitle() {
        return localizer.getMessage("lblReplayGame");
    }

    @Override
    public EDocID getItemEnum() {
        return EDocID.HOME_REPLAY;
    }

    @Override
    public EDocID getDocumentID() {
        return EDocID.HOME_REPLAY;
    }

    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    @Override
    public CSubmenuReplay getLayoutControl() {
        return CSubmenuReplay.SINGLETON_INSTANCE;
    }

    @Override
    public void setParentCell(DragCell cell0) {
        this.parentCell = cell0;
    }

    @Override
    public DragCell getParentCell() {
        return this.parentCell;
    }

    public JList<String> getList() {
        return replayList;
    }

    public DefaultListModel<String> getModel() {
        return model;
    }

    public JTextArea getReplayInfo() {
        return replayInfo;
    }

    public StartButton getBtnStart() {
        return btnStart;
    }

    public FButton getBtnView() {
        return btnView;
    }

    public JComboBox<String> getCmbDays() {
        return cmbDays;
    }

    public JLabel getLblCount() {
        return lblCount;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    @Override
    public void populate() {
        final JPanel container = VHomeUI.SINGLETON_INSTANCE.getPnlDisplay();

        container.removeAll();
        container.setLayout(new MigLayout("insets 10, gap 10, wrap 1"));

        final JPanel titlePanel = new JPanel(new MigLayout("insets 0, gap 8"));
        titlePanel.setOpaque(false);
        javax.swing.JLabel lblTitle = new javax.swing.JLabel(localizer.getMessage("lblReplayModeTitle"));
        lblTitle.setFont(lblTitle.getFont().deriveFont(18f));
        titlePanel.add(lblTitle, "pushx, growx");

        // Days filter inline
        JLabel lblDays = new JLabel(localizer.getMessage("lblShowLastDays"));
        titlePanel.add(lblDays, "gapleft 16");
        titlePanel.add(cmbDays, "w 60!, h 24!");
        titlePanel.add(lblCount, "gapleft 20, pushx");

        container.add(titlePanel, "w 96%!, gap 2% 2% 5px 5px");
        container.add(progressBar, "w 96%!, h 6!, gap 2% 2% 0 0");
        replayList.setModel(model);
        container.add(replayListPane, "w 96%!, h 45%, gap 2% 2% 0 0");
        container.add(replayInfoPane, "w 96%!, h 25%, gap 2% 2% 0 0");

        // Buttons row: [Replay] [View]
        final JPanel btnPanel = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, gap 5"));
        btnPanel.setOpaque(false);
        btnPanel.add(btnStart, "w 200px!, h 30px!");
        btnPanel.add(btnView, "w 120px!, h 30px!");
        container.add(btnPanel, "gap 2% 2% 10px 10px");

        if (container.isShowing()) {
            container.validate();
            container.repaint();
        }
    }
}
