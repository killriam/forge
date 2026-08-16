package forge.screens.home.replay;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ListSelectionModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;

import forge.game.ReplayLogParser;
import forge.gui.framework.DragCell;
import forge.gui.framework.DragTab;
import forge.gui.framework.EDocID;
import forge.screens.home.EMenuGroup;
import forge.screens.home.IVSubmenu;
import forge.screens.home.StartButton;
import forge.screens.home.VHomeUI;
import forge.toolbox.FButton;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin.SkinnedTable;
import forge.util.Localizer;
import net.miginfocom.swing.MigLayout;

/**
 * View for the Replay Scenario submenu.
 * Displays available scenario JSON files in a sortable table and lets the user play them
 * interactively, similar to puzzle mode.
 */
public enum VSubmenuScenario implements IVSubmenu<CSubmenuScenario> {
    SINGLETON_INSTANCE;

    /** One row of the scenario table - also carries the parser needed to actually launch it. */
    public static final class ScenarioRow {
        public final String type;
        public final String name;
        public final String deck;
        public final boolean demoed;
        public final ReplayLogParser parser;

        public ScenarioRow(String type, String name, String deck, boolean demoed, ReplayLogParser parser) {
            this.type = type;
            this.name = name;
            this.deck = deck;
            this.demoed = demoed;
            this.parser = parser;
        }
    }

    /** Backs the scenario table - four sortable columns, one row per scenario file. */
    public static final class ScenarioTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Type", "Name", "Deck", "Demoed"};
        private List<ScenarioRow> rows = new ArrayList<>();

        public void setRows(List<ScenarioRow> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        public ScenarioRow getRow(int modelRow) {
            return (modelRow >= 0 && modelRow < rows.size()) ? rows.get(modelRow) : null;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            ScenarioRow r = rows.get(row);
            return switch (col) {
                case 0 -> r.type == null ? "" : r.type;
                case 1 -> r.name;
                case 2 -> r.deck == null ? "" : r.deck;
                case 3 -> r.demoed ? "✓" : "";
                default -> "";
            };
        }
    }

    private final ScenarioTableModel tableModel = new ScenarioTableModel();
    private final SkinnedTable scenarioTable;
    private final FScrollPane scenarioTablePane;
    private final JTextArea scenarioInfo;
    private final FScrollPane scenarioInfoPane;

    private final StartButton btnStart = new StartButton();
    /** Scenario-authoring aid, not a permanent player-facing mode - see CSubmenuScenario.
     *  Label toggles between "Demo Play" and "Redo Demo" based on the selected row. */
    private final FButton btnDemoPlay = new FButton();

    private DragCell parentCell;
    final Localizer localizer = Localizer.getInstance();
    private final DragTab tab = new DragTab(localizer.getMessage("lblReplayScenario"));

    VSubmenuScenario() {
        scenarioTable = new SkinnedTable();
        scenarioTable.setModel(tableModel);
        scenarioTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scenarioTable.setRowHeight(22);
        scenarioTable.setFillsViewportHeight(true);
        scenarioTable.setAutoCreateRowSorter(true);
        scenarioTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        scenarioTable.getColumnModel().getColumn(1).setPreferredWidth(420);
        scenarioTable.getColumnModel().getColumn(2).setPreferredWidth(260);
        scenarioTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        scenarioTable.getColumnModel().getColumn(3).setMaxWidth(90);
        scenarioTablePane = new FScrollPane(scenarioTable, true);

        scenarioInfo = new JTextArea();
        scenarioInfo.setEditable(false);
        scenarioInfo.setLineWrap(true);
        scenarioInfo.setWrapStyleWord(true);
        scenarioInfo.setOpaque(false);
        scenarioInfoPane = new FScrollPane(scenarioInfo, true);
        btnDemoPlay.setText(localizer.getMessageorUseDefault("lblScenarioDemoPlay", "Demo Play (record actions)"));
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

    public SkinnedTable getTable() {
        return scenarioTable;
    }

    public ScenarioTableModel getTableModel() {
        return tableModel;
    }

    /** Maps the table's currently-selected VIEW row to its data, accounting for sort order. */
    public ScenarioRow getSelectedRow() {
        int viewRow = scenarioTable.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = scenarioTable.convertRowIndexToModel(viewRow);
        return tableModel.getRow(modelRow);
    }

    public JTextArea getScenarioInfo() {
        return scenarioInfo;
    }

    public StartButton getBtnStart() {
        return btnStart;
    }

    public FButton getBtnDemoPlay() {
        return btnDemoPlay;
    }

    @Override
    public void populate() {
        final JPanel container = VHomeUI.SINGLETON_INSTANCE.getPnlDisplay();

        container.removeAll();
        container.setLayout(new MigLayout("insets 10, gap 10, wrap 1"));

        final JPanel titlePanel = new JPanel(new MigLayout("insets 0, gap 0"));
        titlePanel.setOpaque(false);
        JLabel lblTitle = new JLabel(localizer.getMessage("lblReplayScenarioTitle"));
        lblTitle.setFont(lblTitle.getFont().deriveFont(18f));
        titlePanel.add(lblTitle, "pushx, growx");

        container.add(titlePanel, "w 96%!, gap 2% 2% 5px 5px");
        container.add(scenarioTablePane, "w 96%!, h 45%, gap 2% 2% 0 0");
        container.add(scenarioInfoPane, "w 96%!, h 25%, gap 2% 2% 0 0");
        container.add(btnStart, "w 96%!, h 30px!, gap 2% 2% 10px 5px");
        container.add(btnDemoPlay, "w 96%!, h 30px!, gap 2% 2% 0 10px");

        if (container.isShowing()) {
            container.validate();
            container.repaint();
        }
    }
}
