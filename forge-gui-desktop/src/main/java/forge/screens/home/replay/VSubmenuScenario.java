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
import forge.screens.home.StartButton;
import forge.screens.home.VHomeUI;
import forge.toolbox.FList;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * View for the Replay Scenario submenu.
 * Displays available scenario JSON files and allows the user to play them
 * interactively, similar to puzzle mode.
 */
public enum VSubmenuScenario implements IVSubmenu<CSubmenuScenario> {
    SINGLETON_INSTANCE;

    private final FList<String> scenarioList;
    private final FScrollPane scenarioListPane;
    private final JTextArea scenarioInfo;
    private final FScrollPane scenarioInfoPane;

    final DefaultListModel<String> model = new DefaultListModel<>();

    private final StartButton btnStart = new StartButton();

    private DragCell parentCell;
    final Localizer localizer = Localizer.getInstance();
    private final DragTab tab = new DragTab(localizer.getMessage("lblReplayScenario"));

    VSubmenuScenario() {
        scenarioList = new FList<>();
        scenarioListPane = new FScrollPane(this.scenarioList, true);
        scenarioInfo = new JTextArea();
        scenarioInfo.setEditable(false);
        scenarioInfo.setLineWrap(true);
        scenarioInfo.setWrapStyleWord(true);
        scenarioInfo.setOpaque(false);
        scenarioInfoPane = new FScrollPane(scenarioInfo, true);
    }

    @Override
    public EMenuGroup getGroupEnum() {
        return EMenuGroup.REPLAY;
    }

    @Override
    public String getMenuTitle() {
        return localizer.getMessage("lblReplayScenario");
    }

    @Override
    public EDocID getItemEnum() {
        return EDocID.HOME_SCENARIO;
    }

    @Override
    public EDocID getDocumentID() {
        return EDocID.HOME_SCENARIO;
    }

    @Override
    public DragTab getTabLabel() {
        return tab;
    }

    @Override
    public CSubmenuScenario getLayoutControl() {
        return CSubmenuScenario.SINGLETON_INSTANCE;
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
        return scenarioList;
    }

    public DefaultListModel<String> getModel() {
        return model;
    }

    public JTextArea getScenarioInfo() {
        return scenarioInfo;
    }

    public StartButton getBtnStart() {
        return btnStart;
    }

    @Override
    public void populate() {
        final JPanel container = VHomeUI.SINGLETON_INSTANCE.getPnlDisplay();

        container.removeAll();
        container.setLayout(new MigLayout("insets 10, gap 10, wrap 1"));

        final JPanel titlePanel = new JPanel(new MigLayout("insets 0, gap 0"));
        titlePanel.setOpaque(false);
        javax.swing.JLabel lblTitle = new javax.swing.JLabel(localizer.getMessage("lblReplayScenarioTitle"));
        lblTitle.setFont(lblTitle.getFont().deriveFont(18f));
        titlePanel.add(lblTitle, "pushx, growx");

        container.add(titlePanel, "w 96%!, gap 2% 2% 5px 5px");
        scenarioList.setModel(model);
        container.add(scenarioListPane, "w 96%!, h 45%, gap 2% 2% 0 0");
        container.add(scenarioInfoPane, "w 96%!, h 25%, gap 2% 2% 0 0");
        container.add(btnStart, "w 96%!, h 30px!, gap 2% 2% 10px 10px");

        if (container.isShowing()) {
            container.validate();
            container.repaint();
        }
    }
}
