package forge.screens.gamelearning;

import forge.Singletons;
import forge.game.ReplayStateReconstructor.TurnSnapshot;
import forge.gui.framework.FScreen;
import forge.gui.framework.IVTopLevelUI;
import forge.screens.home.replay.EvaluationDimensionPanel;
import forge.screens.home.replay.EventTimelinePanel;
import forge.screens.home.replay.GameInitPanel;
import forge.screens.home.replay.GameOutcomePanel;
import forge.screens.home.replay.GameOverviewPanel;
import forge.screens.home.replay.GameReportPanel;
import forge.screens.home.replay.LearningStatisticsPanel;
import forge.screens.home.replay.MtgBoardPanel;
import forge.toolbox.FButton;
import forge.toolbox.FScrollPane;
import forge.util.Localizer;
import forge.view.FView;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Top-level view for the Game Learning Viewer screen.
 *
 * Layout:
 *   Left  : turn list sidebar (with per-player life indicators)
 *   Right : [lblTurnInfo]
 *           [MtgBoardPanel — MTG-style battlefield: opponent top, human bottom]
 *           [JTabbedPane: Events | Statistics | Game Report]
 *           [Nav buttons]
 */
public enum VGameLearningUI implements IVTopLevelUI {
    SINGLETON_INSTANCE;

    // --- Turn list (left panel) ---
    private final DefaultListModel<TurnSnapshot> turnModel = new DefaultListModel<>();
    private final JList<TurnSnapshot> turnList;
    private final FScrollPane turnListPane;
    /** Player names used by the turn list cell renderer (set via setPlayerNames). */
    private Map<String, String> playerNames;

    // --- MTG board panel (replaces old human/opponent split) ---
    private final MtgBoardPanel mtgBoardPanel = new MtgBoardPanel();

    // --- Special views ---
    private final GameOverviewPanel gameOverviewPanel;
    private final GameInitPanel gameInitPanel;
    private final GameOutcomePanel gameOutcomePanel;

    // --- Evaluation panel (shown inline above board for regular turns) ---
    private final EvaluationDimensionPanel evalPanel;

    // --- Analysis tab pane (bottom-right) ---
    private final JTabbedPane  analysisTabs;
    private final EventTimelinePanel eventTimeline;
    private final LearningStatisticsPanel statisticsPanel;
    private final GameReportPanel gameReportPanel;

    // --- Navigation buttons (bottom) ---
    private final FButton btnPrev;
    private final FButton btnNext;
    private final FButton btnReplay;

    // --- Turn info label ---
    private final JLabel lblTurnInfo;

    /** Turn numbers that have a detected blunder — orange ⚠ marker. */
    private Set<Integer> blunderTurns = new HashSet<>();
    /** Turn numbers that contain a LEARNING_MARKER event — cyan 🔖 marker. */
    private Set<Integer> learningTurns = new HashSet<>();

    final Localizer localizer = Localizer.getInstance();

    VGameLearningUI() {
        turnList = new JList<>(turnModel);
        turnList.setCellRenderer(new TurnCellRenderer());
        turnList.setFixedCellHeight(54);
        turnList.setOpaque(false);
        turnList.setBackground(new Color(0, 0, 0, 0));
        turnListPane = new FScrollPane(turnList, true);

        gameOverviewPanel = new GameOverviewPanel();
        gameInitPanel = new GameInitPanel();
        gameOutcomePanel = new GameOutcomePanel();
        evalPanel = new EvaluationDimensionPanel();
        eventTimeline   = new EventTimelinePanel();
        statisticsPanel = new LearningStatisticsPanel();
        gameReportPanel = new GameReportPanel();

        // Analysis tabs: Events | Statistics | Game Report
        analysisTabs = new JTabbedPane(JTabbedPane.TOP);
        analysisTabs.setOpaque(false);
        analysisTabs.addTab("Events", eventTimeline);
        analysisTabs.addTab("Statistics", statisticsPanel);
        analysisTabs.addTab("Game Report", gameReportPanel);

        lblTurnInfo = new JLabel("Select a turn to view");
        lblTurnInfo.setFont(lblTurnInfo.getFont().deriveFont(Font.BOLD, 14f));
        lblTurnInfo.setForeground(new Color(180, 200, 220));

        btnPrev   = new FButton(localizer.getMessage("lblPrevTurn"));
        btnNext   = new FButton(localizer.getMessage("lblNextTurn"));
        btnReplay = new FButton(localizer.getMessage("lblReplayFromHere"));
    }

    // ---- Accessors for controller ----

    public JList<TurnSnapshot> getTurnList()                  { return turnList; }
    public DefaultListModel<TurnSnapshot> getTurnModel()      { return turnModel; }
    public MtgBoardPanel getMtgBoardPanel()                   { return mtgBoardPanel; }
    public GameOverviewPanel getGameOverviewPanel()           { return gameOverviewPanel; }
    public GameInitPanel getGameInitPanel()                   { return gameInitPanel; }
    public GameOutcomePanel getGameOutcomePanel()             { return gameOutcomePanel; }
    public EventTimelinePanel getEventTimeline()              { return eventTimeline; }
    public LearningStatisticsPanel getStatisticsPanel()       { return statisticsPanel; }
    public EvaluationDimensionPanel getEvalPanel()            { return evalPanel; }
    public GameReportPanel getGameReportPanel()               { return gameReportPanel; }
    public JTabbedPane getAnalysisTabs()                      { return analysisTabs; }
    public JLabel getLblTurnInfo()                            { return lblTurnInfo; }
    public FButton getBtnPrev()                               { return btnPrev; }
    public FButton getBtnNext()                               { return btnNext; }
    public FButton getBtnReplay()                             { return btnReplay; }

    /**
     * Mark which turn numbers contain blunders so the cell renderer can show a warning icon.
     * Pass an empty set to clear all markers.
     */
    public void setBlunderTurns(Set<Integer> turns) {
        this.blunderTurns = turns != null ? new HashSet<>(turns) : new HashSet<>();
        turnList.repaint();
    }

    /**
     * Mark which turn numbers contain LEARNING_MARKER events (cyan bookmark icon).
     * Pass an empty set to clear all markers.
     */
    public void setLearningTurns(Set<Integer> turns) {
        this.learningTurns = turns != null ? new HashSet<>(turns) : new HashSet<>();
        turnList.repaint();
    }

    /**
     * Supply the player-name map used by the turn list renderer.
     * Must be called before adding turns to the model.
     */
    public void setPlayerNames(Map<String, String> names) {
        this.playerNames = names;
    }

    // ---- IVTopLevelUI ----

    @Override
    public void instantiate() {
    }

    @Override
    public void populate() {
        JPanel container = FView.SINGLETON_INSTANCE.getPnlInsets();
        container.removeAll();
        container.setLayout(new MigLayout("insets 0, gap 0, fill"));

        // ---- Title bar ----
        JPanel titleBar = new JPanel(new MigLayout("insets 6 12 6 12, gap 8"));
        titleBar.setOpaque(false);
        JLabel lblTitle = new JLabel(localizer.getMessage("lblGameLearningViewerTitle"));
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 16f));
        lblTitle.setForeground(new Color(200, 220, 240));
        titleBar.add(lblTitle, "pushx, growx");
        container.add(titleBar, "dock north, h 36!");

        // ---- Left panel: turn list ----
        JPanel leftPanel = new JPanel(new MigLayout("insets 0, gap 0, fill"));
        leftPanel.setOpaque(false);
        leftPanel.add(turnListPane, "grow, push");

        // ---- Right panel (uses CardLayout to switch between different views) ----
        rightPanelContainer = new JPanel(new java.awt.CardLayout());
        rightPanelContainer.setOpaque(false);

        // --- View 0: Game Overview (first entry in sidebar) ---
        JPanel overviewView = buildOverviewView();
        rightPanelContainer.add(overviewView, "OVERVIEW");

        // --- View 1: Game Init ---
        JPanel initView = buildInitView();
        rightPanelContainer.add(initView, "INIT");

        // --- View 2: Regular turn (board + eval + tabs) ---
        JPanel turnView = buildTurnView();
        rightPanelContainer.add(turnView, "TURN");

        // --- View 3: Game Over ---
        JPanel gameOverView = buildGameOverView();
        rightPanelContainer.add(gameOverView, "GAMEOVER");

        // ---- Outer split ----
        javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanelContainer);
        splitPane.setDividerLocation(160);
        splitPane.setResizeWeight(0.12);
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(5);

        container.add(splitPane, "grow, push");

        // Default to overview
        showView("OVERVIEW");

        if (container.isShowing()) {
            container.validate();
            container.repaint();
        }
    }

    // --- Card layout panels for different views ---
    private JPanel rightPanelContainer;

    /** Switch between OVERVIEW, INIT, TURN, or GAMEOVER view. */
    public void showView(String viewName) {
        if (rightPanelContainer != null) {
            java.awt.CardLayout cl = (java.awt.CardLayout) rightPanelContainer.getLayout();
            cl.show(rightPanelContainer, viewName);
        }
    }

    private JPanel buildOverviewView() {
        JPanel panel = new JPanel(new MigLayout("insets 6, gap 6, fill, wrap 1"));
        panel.setOpaque(false);
        panel.add(gameOverviewPanel, "grow, push");
        panel.add(buildNavPanel(), "growx, h 38!, dock south");
        return panel;
    }

    private JPanel buildInitView() {
        JPanel panel = new JPanel(new MigLayout("insets 6, gap 6, fill, wrap 1"));
        panel.setOpaque(false);
        panel.add(new JLabel("Game Initialization"), "growx, h 24!");
        panel.add(gameInitPanel, "grow, push");
        panel.add(buildNavPanel(), "growx, h 38!, dock south");
        return panel;
    }

    private JPanel buildTurnView() {
        JPanel panel = new JPanel(new MigLayout("insets 4, gap 4, fill, wrap 1"));
        panel.setOpaque(false);

        // Turn info header
        panel.add(lblTurnInfo, "growx, h 22!");

        // MTG board panel — opponent on top, human on bottom (gets MOST of the space)
        mtgBoardPanel.setBorder(BorderFactory.createLineBorder(new Color(40, 70, 50), 1));
        panel.add(mtgBoardPanel, "grow, push, h 300::");

        // Evaluation panel (compact inline bar)
        evalPanel.setBorder(BorderFactory.createLineBorder(new Color(60, 80, 100), 1));
        FScrollPane evalScroll = new FScrollPane(evalPanel, false);
        panel.add(evalScroll, "growx, h 50:70:100");

        // Analysis tabs — Events | Statistics | Game Report (compact)
        panel.add(analysisTabs, "growx, h 80:120:160");

        // Navigation buttons
        panel.add(buildNavPanel(), "growx, h 38!, dock south");

        return panel;
    }

    private JPanel buildGameOverView() {
        JPanel panel = new JPanel(new MigLayout("insets 6, gap 6, fill, wrap 1"));
        panel.setOpaque(false);
        JLabel lblGameOver = new JLabel("\uD83C\uDFC6 Game Over");
        lblGameOver.setFont(lblGameOver.getFont().deriveFont(Font.BOLD, 16f));
        lblGameOver.setForeground(new Color(220, 190, 60));
        panel.add(lblGameOver, "growx, h 30!");
        panel.add(gameOutcomePanel, "growx, h 160:200:240");
        panel.add(mtgBoardPanel, "grow, push");
        panel.add(buildNavPanel(), "growx, h 38!, dock south");
        return panel;
    }

    private JPanel buildNavPanel() {
        JPanel navPanel = new JPanel(new MigLayout("insets 4, gap 6"));
        navPanel.setOpaque(false);
        navPanel.add(btnPrev,   "w 110!, h 28!");
        navPanel.add(btnNext,   "w 110!, h 28!");
        navPanel.add(btnReplay, "w 200!, h 28!, gapleft 20");
        return navPanel;
    }

    @Override
    public boolean onSwitching(FScreen fromScreen, FScreen toScreen) {
        return true;
    }

    @Override
    public boolean onClosing(FScreen screen) {
        Singletons.getControl().setCurrentScreen(FScreen.HOME_SCREEN);
        return true;
    }

    // ---- Turn list cell renderer ----

    private class TurnCellRenderer extends JPanel implements ListCellRenderer<TurnSnapshot> {
        private static final long serialVersionUID = 1L;

        private final JLabel lblTurn   = new JLabel();
        private final JLabel lblPlayer = new JLabel();
        /** Cached life data for painting. */
        private Map<String, Integer> lifeTotals;
        private String activeId;
        private boolean hasBlunder;
        private boolean hasLearning;
        private boolean isPreGame;
        private boolean isGameOver;
        private boolean isGameOverview;

        TurnCellRenderer() {
            setLayout(new MigLayout("insets 3 10 3 6, gap 3, fill, wrap 1"));
            setOpaque(true);

            lblTurn.setFont(lblTurn.getFont().deriveFont(Font.BOLD, 12f));
            lblTurn.setForeground(new Color(180, 200, 220));

            lblPlayer.setFont(lblPlayer.getFont().deriveFont(Font.PLAIN, 10f));
            lblPlayer.setForeground(new Color(140, 155, 170));
            lblPlayer.setHorizontalAlignment(SwingConstants.LEFT);

            add(lblTurn,  "growx, pushx");
            add(lblPlayer,"growx, pushx");
            setPreferredSize(new java.awt.Dimension(150, 54));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends TurnSnapshot> list,
                                                       TurnSnapshot snap, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            if (snap == null) {
                lblTurn.setText("");
                lblPlayer.setText("");
                lifeTotals = null;
                activeId   = null;
                hasBlunder  = false;
                hasLearning = false;
                isPreGame   = false;
                isGameOver  = false;
                isGameOverview = false;
            } else {
                lifeTotals  = snap.lifeTotals;
                activeId    = snap.activePlayerId;
                hasBlunder  = blunderTurns.contains(snap.turnNumber);
                hasLearning = snap.hasLearningMarker || learningTurns.contains(snap.turnNumber);
                isPreGame   = snap.isPreGame;
                isGameOver  = snap.isGameOver;
                isGameOverview = snap.isGameOverview;

                // Build turn label with appropriate icon prefix
                String turnLabel;
                Color turnColor;
                if (isGameOverview) {
                    turnLabel = "<html><b>\uD83D\uDCCA Game</b></html>";
                    turnColor = new Color(140, 190, 220);           // light blue
                } else if (isGameOver) {
                    turnLabel = "<html><b>\uD83C\uDFC6 Game Over</b></html>";
                    turnColor = new Color(220, 190, 60);            // gold
                } else if (isPreGame) {
                    turnLabel = "<html><b>\uD83C\uDFAE Game Init</b></html>";
                    turnColor = new Color(140, 160, 180);           // muted blue-grey
                } else if (hasBlunder && hasLearning) {
                    turnLabel = "<html><b>\u26A0\uD83D\uDD16 T" + snap.turnNumber + "</b></html>";
                    turnColor = new Color(230, 175, 50);
                } else if (hasBlunder) {
                    turnLabel = "<html><b>\u26A0 Turn " + snap.turnNumber + "</b></html>";
                    turnColor = new Color(230, 175, 50);
                } else if (hasLearning) {
                    turnLabel = "<html><b>\uD83D\uDD16 Turn " + snap.turnNumber + "</b></html>";
                    turnColor = new Color(60, 210, 200);             // teal/cyan
                } else {
                    turnLabel = "<html><b>Turn " + snap.turnNumber + "</b></html>";
                    turnColor = new Color(180, 200, 220);
                }
                lblTurn.setText(turnLabel);
                lblTurn.setForeground(turnColor);

                // Sub-label
                String subLabel;
                if (isGameOverview) {
                    subLabel = "<html><i>decks & result</i></html>";
                } else if (isGameOver) {
                    subLabel = "<html><i>final state</i></html>";
                } else if (isPreGame) {
                    subLabel = "<html><i>setup & mulligan</i></html>";
                } else if (snap.activePlayerId != null && playerNames != null) {
                    String name = playerNames.getOrDefault(snap.activePlayerId, snap.activePlayerId);
                    subLabel = "<html>" + name + "</html>";
                } else {
                    subLabel = "";
                }
                lblPlayer.setText(subLabel);
            }

            if (isSelected) {
                setBackground(new Color(50, 70, 100));
                lblTurn.setForeground(new Color(220, 235, 255));
            } else if (isGameOverview) {
                setBackground(new Color(25, 35, 48));
            } else if (isGameOver) {
                setBackground(new Color(50, 45, 20));
            } else if (isPreGame) {
                setBackground(new Color(28, 34, 42));
            } else if (hasLearning) {
                // Learning turns get a distinctive tinted background
                setBackground(new Color(25, 45, 45));
            } else if (index % 2 == 0) {
                setBackground(new Color(30, 30, 35));
            } else {
                setBackground(new Color(38, 38, 44));
            }

            return this;
        }

        /** Paint small life-dot indicators and left accent bar. */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // --- Left accent bar for learning/blunder markers ---
            if (hasLearning && !isPreGame && !isGameOver) {
                g2.setColor(new Color(60, 210, 200, 180));   // teal/cyan
                g2.fillRect(0, 0, 4, getHeight());
            }
            if (hasBlunder && !isPreGame && !isGameOver) {
                g2.setColor(new Color(230, 175, 50, 200));   // orange
                // If both learning and blunder, split the bar
                if (hasLearning) {
                    g2.fillRect(0, 0, 4, getHeight() / 2);
                } else {
                    g2.fillRect(0, 0, 4, getHeight());
                }
            }

            // --- Pre-game / Game Over / Game Overview separator line ---
            if (isPreGame || isGameOver || isGameOverview) {
                Color lineColor = isGameOver ? new Color(180, 150, 40, 80)
                        : isGameOverview ? new Color(100, 150, 200, 60)
                        : new Color(80, 100, 140, 60);
                g2.setColor(lineColor);
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(4, getHeight() - 2, getWidth() - 4, getHeight() - 2);
                g2.dispose();
                return;
            }

            // --- Life dot indicators ---
            if (lifeTotals == null || lifeTotals.isEmpty()) {
                g2.dispose();
                return;
            }

            int x = 10;
            int y = getHeight() - 10;
            Font f = g.getFont().deriveFont(Font.BOLD, 9f);
            g2.setFont(f);
            java.awt.FontMetrics fm = g2.getFontMetrics(f);

            for (Map.Entry<String, Integer> e : lifeTotals.entrySet()) {
                int life = e.getValue();
                boolean isActive = e.getKey().equals(activeId);

                // Life dot colour
                Color dotColor;
                if (life <= 5)       dotColor = new Color(220, 60, 60);
                else if (life <= 15) dotColor = new Color(220, 180, 40);
                else                 dotColor = new Color(80, 200, 80);

                // Draw dot (filled circle)
                g2.setColor(dotColor);
                g2.fillOval(x, y - 8, 8, 8);
                if (isActive) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawOval(x - 1, y - 9, 10, 10);
                }

                // Draw life number
                g2.setColor(isActive ? Color.WHITE : new Color(180, 180, 180));
                String lifeStr = String.valueOf(life);
                g2.drawString(lifeStr, x + 11, y);

                x += 11 + fm.stringWidth(lifeStr) + 8;
                if (x > getWidth() - 20) break;
            }
            g2.dispose();
        }
    }
}

