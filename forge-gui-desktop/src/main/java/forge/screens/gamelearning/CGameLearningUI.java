package forge.screens.gamelearning;

import forge.game.GameEvaluationReport;
import forge.game.OriginalGameSummary;
import forge.game.ReplayLogParser;
import forge.game.ReplayStateReconstructor;
import forge.game.ReplayStateReconstructor.BattlefieldCardInfo;
import forge.game.ReplayStateReconstructor.TurnSnapshot;
import forge.gui.framework.ICDoc;
import forge.screens.home.replay.CSubmenuReplay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-level controller for the Game Learning Viewer screen.
 * Manages turn-by-turn navigation, populates the MTG board panel,
 * the event timeline, and the evaluation tabs.
 */
public enum CGameLearningUI implements ICDoc {
    SINGLETON_INSTANCE;

    private static final Logger LOG = LoggerFactory.getLogger(CGameLearningUI.class);

    /** Set before switching to this screen to load a specific replay. */
    private static ReplayLogParser pendingParser = null;

    public static void setPendingParser(ReplayLogParser parser) {
        pendingParser = parser;
    }

    private final VGameLearningUI view = VGameLearningUI.SINGLETON_INSTANCE;

    private ReplayStateReconstructor reconstructor = null;
    private ReplayLogParser currentParser = null;
    private List<TurnSnapshot> turns = new ArrayList<>();
    private int currentTurnIndex = 0;

    /** Cached evaluation report; null until background computation finishes. */
    private GameEvaluationReport evaluationReport = null;

    /** ID of the "human" (first) player — shown in the bottom half of the board. */
    private String humanPlayerId = null;
    /** IDs of all opponents, in order — the first active one shown in the top half. */
    private List<String> opponentIds = new ArrayList<>();

    private boolean initialized = false;

    @Override
    public void register() {
    }

    @Override
    public void initialize() {
        if (!initialized) {
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
            initialized = true;
        }

        if (pendingParser != null) {
            load(pendingParser);
            pendingParser = null;
        }
    }

    @Override
    public void update() {
    }

    // -----------------------------------------------------------------------
    // Loading
    // -----------------------------------------------------------------------

    private void load(ReplayLogParser parser) {
        this.currentParser = parser;
        this.reconstructor = new ReplayStateReconstructor(parser);
        this.turns = new ArrayList<>(reconstructor.getTurns());
        this.currentTurnIndex = 0;
        this.evaluationReport = null;

        Map<String, String> names = reconstructor.getPlayerNames();
        List<String> allIds = new ArrayList<>(names.keySet());

        // First player = "human" bottom panel; rest = opponents (top panel)
        humanPlayerId = allIds.isEmpty() ? null : allIds.get(0);
        opponentIds = allIds.size() > 1 ? allIds.subList(1, allIds.size()) : Collections.emptyList();

        view.setPlayerNames(names);

        // Clear markers while loading
        view.setBlunderTurns(Collections.emptySet());
        view.setLearningTurns(Collections.emptySet());

        // Build deck info from parser for the Game Overview
        Map<String, String> deckNames = new java.util.LinkedHashMap<>();
        if (parser.getPlayers() != null) {
            for (Map.Entry<String, ReplayLogParser.PlayerInfo> entry : parser.getPlayers().entrySet()) {
                ReplayLogParser.PlayerInfo pi = entry.getValue();
                if (pi.deckName != null && !pi.deckName.isEmpty()) {
                    deckNames.put(entry.getKey(), pi.deckName);
                }
            }
        }

        // --- Insert synthetic Game Overview entry at position 0 ---
        // Use initial life/hand/library from the first existing snapshot
        Map<String, Integer> initLife = new java.util.LinkedHashMap<>();
        Map<String, Integer> initHand = new java.util.LinkedHashMap<>();
        Map<String, Integer> initLib = new java.util.LinkedHashMap<>();
        Map<String, Integer> emptyZone = new java.util.LinkedHashMap<>();
        Map<String, Integer> emptyBf = new java.util.LinkedHashMap<>();
        Map<String, List<BattlefieldCardInfo>> emptyBfCards = new java.util.LinkedHashMap<>();
        for (String id : allIds) {
            initLife.put(id, 20);
            initHand.put(id, 0);
            initLib.put(id, 0);
            emptyZone.put(id, 0);
            emptyBf.put(id, 0);
            emptyBfCards.put(id, Collections.emptyList());
        }
        if (!turns.isEmpty()) {
            TurnSnapshot first = turns.get(0);
            initLife.putAll(first.lifeTotals);
            initHand.putAll(first.handSizes);
            initLib.putAll(first.librarySizes);
        }
        TurnSnapshot overviewSnap = new TurnSnapshot(0, null,
                initLife, initHand, initLib, emptyZone, emptyZone, emptyBf, emptyBfCards);
        overviewSnap.isGameOverview = true;
        turns.add(0, overviewSnap);

        // Populate turn list — collect learning-marker turns while iterating
        view.getTurnModel().clear();
        Set<Integer> learningTurnNumbers = new HashSet<>();
        for (TurnSnapshot turn : turns) {
            view.getTurnModel().addElement(turn);
            if (turn.hasLearningMarker) {
                learningTurnNumbers.add(turn.turnNumber);
            }
        }
        view.setLearningTurns(learningTurnNumbers);

        // Populate Game Overview panel
        String winnerId = parser.getWinner();
        String winnerName = null;
        if (winnerId != null && names.containsKey(winnerId)) {
            winnerName = names.get(winnerId);
        }
        int totalTurns = 0;
        for (int i = turns.size() - 1; i >= 0; i--) {
            TurnSnapshot s = turns.get(i);
            if (!s.isPreGame && !s.isGameOver && !s.isGameOverview) { totalTurns = s.turnNumber; break; }
        }
        view.getGameOverviewPanel().setOverview(names, deckNames, winnerName, totalTurns);

        if (!turns.isEmpty()) {
            view.getTurnList().setSelectedIndex(0);
            showTurn(0);
        } else {
            view.getLblTurnInfo().setText("No turns found in this replay.");
            view.getEventTimeline().setEvents(null);
            view.getEvalPanel().setEvaluation(null);
        }

        updateNavButtons();

        // --- Background: compute full evaluation report ---
        final List<TurnSnapshot> realSnapshots = new ArrayList<>();
        for (TurnSnapshot s : this.turns) {
            if (!s.isPreGame && !s.isGameOver && !s.isGameOverview) realSnapshots.add(s);
        }
        final Map<String, String> playerNames = names;
        final String hid = humanPlayerId;

        new SwingWorker<GameEvaluationReport, Void>() {
            @Override
            protected GameEvaluationReport doInBackground() {
                return GameEvaluationReport.build(realSnapshots, playerNames, hid);
            }

            @Override
            protected void done() {
                try {
                    GameEvaluationReport report = get();
                    evaluationReport = report;

                    // Populate game report panel
                    view.getGameReportPanel().setData(report, realSnapshots, playerNames);

                    // Mark blunder turns in cell renderer
                    Set<Integer> blunderTurnNums = new HashSet<>();
                    for (forge.game.BlunderEntry bl : report.blunders) {
                        blunderTurnNums.add(bl.turnNumber);
                    }
                    view.setBlunderTurns(blunderTurnNums);

                    // Refresh current turn evaluation
                    showTurn(currentTurnIndex);

                } catch (Exception e) {
                    LOG.warn("Failed to compute game evaluation report: {}", e.getMessage());
                }
            }
        }.execute();
    }

    // -----------------------------------------------------------------------
    // Turn display
    // -----------------------------------------------------------------------

    private void showTurn(int idx) {
        if (idx < 0 || idx >= turns.size()) return;
        TurnSnapshot turn = turns.get(idx);
        Map<String, String> names = reconstructor.getPlayerNames();

        // --- Special: Game Overview (synthetic first entry) ---
        if (turn.isGameOverview) {
            view.showView("OVERVIEW");
            view.getLblTurnInfo().setText("\uD83D\uDCCA Game Overview");
            view.getEventTimeline().setEvents(null);
            view.getEvalPanel().setEvaluation(null);
            currentTurnIndex = idx;
            updateNavButtons();
            return;
        }

        // --- Special: Game Init (pre-game) ---
        if (turn.isPreGame) {
            view.showView("INIT");
            populateGameInitPanel(turn, names);
            view.getLblTurnInfo().setText("\uD83C\uDFAE Game Initialisation");
            showPreGameInfo(turn, names);
            view.getEventTimeline().setEvents(turn.events);
            view.getEvalPanel().setEvaluation(null);
            currentTurnIndex = idx;
            updateNavButtons();
            return;
        }

        // --- Special: Game Over ---
        if (turn.isGameOver) {
            view.showView("GAMEOVER");
            populateGameOverPanel(turn, names);
            view.getLblTurnInfo().setText("\uD83C\uDFC6 Game Over");
            showGameOverInfo(turn, names);
            view.getEventTimeline().setEvents(turn.events);
            view.getEvalPanel().setEvaluation(null);
            currentTurnIndex = idx;
            updateNavButtons();
            return;
        }

        // --- Normal turn: switch to TURN view ---
        view.showView("TURN");

        TurnSnapshot prevTurn = null;
        for (int i = idx - 1; i >= 0; i--) {
            if (!turns.get(i).isPreGame) { prevTurn = turns.get(i); break; }
        }

        // Turn info header
        StringBuilder header = new StringBuilder("Turn ").append(turn.turnNumber);
        if (turn.activePlayerId != null) {
            String name = names.getOrDefault(turn.activePlayerId, turn.activePlayerId);
            header.append(" \u2014 ").append(name).append("'s Turn");
        }
        if (turn.hasLearningMarker) header.append("  \uD83D\uDD16");
        view.getLblTurnInfo().setText(header.toString());

        // Determine which opponent to show in the top half
        String shownOpponentId = opponentIds.isEmpty() ? null : opponentIds.get(0);
        for (String pid : opponentIds) {
            if (pid.equals(turn.activePlayerId)) { shownOpponentId = pid; break; }
        }

        // Update board — human (bottom) and opponent (top)
        if (humanPlayerId != null) {
            updateBoardSection(humanPlayerId, turn, prevTurn, names, false, shownOpponentId);
        }

        // Statistics tab
        Map<String, Integer> prevLifeTotals = prevTurn != null ? prevTurn.lifeTotals : null;
        view.getStatisticsPanel().setTurnStatistics(turn, prevTurn, prevLifeTotals, names, humanPlayerId);

        // Events tab
        view.getEventTimeline().setEvents(turn.events);

        // Evaluation tab
        if (evaluationReport != null) {
            int realIdx = 0;
            for (int i = 0; i <= idx; i++) {
                if (!turns.get(i).isPreGame && !turns.get(i).isGameOver && !turns.get(i).isGameOverview) realIdx++;
            }
            view.getEvalPanel().setEvaluation(evaluationReport.getEvaluationAt(realIdx - 1));
        } else {
            view.getEvalPanel().setEvaluation(null);
        }

        currentTurnIndex = idx;
        updateNavButtons();
    }

    /** Update the MTG board panel for a regular game turn. */
    private void updateBoardSection(String humanId, TurnSnapshot turn, TurnSnapshot prevTurn,
                                     Map<String, String> names,
                                     boolean ignored, String opponentId) {
        // Human side
        String humanName = names.getOrDefault(humanId, humanId);
        int humanLife   = turn.lifeTotals.getOrDefault(humanId, 0);
        int humanHand   = turn.handSizes.getOrDefault(humanId, 0);
        int humanLib    = turn.librarySizes.getOrDefault(humanId, 0);
        int humanGrave  = turn.graveyardSizes.getOrDefault(humanId, 0);
        int humanExile  = turn.exileSizes.getOrDefault(humanId, 0);
        int humanBf     = turn.battlefieldCounts.getOrDefault(humanId, 0);
        boolean humanActive = humanId.equals(turn.activePlayerId);
        List<BattlefieldCardInfo> humanCards = turn.battlefieldCards.getOrDefault(humanId, Collections.emptyList());

        view.getMtgBoardPanel().setHumanState(
                humanName, humanLife, humanHand, humanLib,
                humanGrave, humanExile, humanBf, humanActive, humanCards);

        // Opponent side
        if (opponentId != null) {
            String oppName  = names.getOrDefault(opponentId, opponentId);
            int oppLife     = turn.lifeTotals.getOrDefault(opponentId, 0);
            int oppHand     = turn.handSizes.getOrDefault(opponentId, 0);
            int oppLib      = turn.librarySizes.getOrDefault(opponentId, 0);
            int oppGrave    = turn.graveyardSizes.getOrDefault(opponentId, 0);
            int oppExile    = turn.exileSizes.getOrDefault(opponentId, 0);
            int oppBf       = turn.battlefieldCounts.getOrDefault(opponentId, 0);
            boolean oppActive = opponentId.equals(turn.activePlayerId);
            List<BattlefieldCardInfo> oppCards = turn.battlefieldCards.getOrDefault(opponentId, Collections.emptyList());

            view.getMtgBoardPanel().setOpponentState(
                    oppName, oppLife, oppHand, oppLib,
                    oppGrave, oppExile, oppBf, oppActive, oppCards);
        } else {
            view.getMtgBoardPanel().setOpponentState(
                    "Opponent", 20, 0, 0, 0, 0, 0, false, Collections.emptyList());
        }
    }

    /** Show pre-game info in the board area (initial life, starting hands, decks). */
    private void showPreGameInfo(TurnSnapshot turn, Map<String, String> names) {
        String oppId = opponentIds.isEmpty() ? null : opponentIds.get(0);

        if (humanPlayerId != null) {
            String humanName = names.getOrDefault(humanPlayerId, humanPlayerId);
            int life   = turn.lifeTotals.getOrDefault(humanPlayerId, 20);
            int hand   = turn.handSizes.getOrDefault(humanPlayerId, 7);
            int lib    = turn.librarySizes.getOrDefault(humanPlayerId, 60);
            view.getMtgBoardPanel().setHumanState(
                    humanName, life, hand, lib, 0, 0, 0, false, Collections.emptyList());
        }
        if (oppId != null) {
            String oppName = names.getOrDefault(oppId, oppId);
            int life   = turn.lifeTotals.getOrDefault(oppId, 20);
            int hand   = turn.handSizes.getOrDefault(oppId, 7);
            int lib    = turn.librarySizes.getOrDefault(oppId, 60);
            view.getMtgBoardPanel().setOpponentState(
                    oppName, life, hand, lib, 0, 0, 0, false, Collections.emptyList());
        }
    }

    /** Populate the GameInitPanel with pre-game details. */
    private void populateGameInitPanel(TurnSnapshot turn, Map<String, String> names) {
        List<String> playOrder = new ArrayList<>();
        if (humanPlayerId != null) playOrder.add(humanPlayerId);
        playOrder.addAll(opponentIds);

        String firstPlayer = playOrder.isEmpty() ? null : playOrder.get(0);
        if (turn.activePlayerId != null) {
            firstPlayer = turn.activePlayerId;
        }

        // Build deck names from parser
        Map<String, String> deckNames = null;
        if (currentParser != null && currentParser.getPlayers() != null) {
            deckNames = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, ReplayLogParser.PlayerInfo> entry : currentParser.getPlayers().entrySet()) {
                ReplayLogParser.PlayerInfo pi = entry.getValue();
                if (pi.deckName != null && !pi.deckName.isEmpty()) {
                    deckNames.put(entry.getKey(), pi.deckName);
                }
            }
        }

        view.getGameInitPanel().setGameInitInfo(
                names,
                deckNames,
                turn.handSizes,
                playOrder,
                firstPlayer,
                null
        );
    }

    /** Populate the GameOutcomePanel with end-of-game details. */
    private void populateGameOverPanel(TurnSnapshot turn, Map<String, String> names) {
        String winnerId = currentParser != null ? currentParser.getWinner() : null;
        int gameEndTurn = turn.turnNumber;

        Map<String, Integer> playerLossTurns = null;

        String winCondition = winnerId != null
                ? names.getOrDefault(winnerId, winnerId) + " won the game."
                : "Game ended.";

        view.getGameOutcomePanel().setOutcome(
                names,
                winnerId,
                gameEndTurn,
                playerLossTurns,
                winCondition
        );
    }

    /** Show game-over summary in the board area (final life totals, winner). */
    private void showGameOverInfo(TurnSnapshot turn, Map<String, String> names) {
        String oppId = opponentIds.isEmpty() ? null : opponentIds.get(0);
        String winnerId = currentParser != null ? currentParser.getWinner() : null;

        if (humanPlayerId != null) {
            String humanName = names.getOrDefault(humanPlayerId, humanPlayerId);
            int life   = turn.lifeTotals.getOrDefault(humanPlayerId, 0);
            int hand   = turn.handSizes.getOrDefault(humanPlayerId, 0);
            int lib    = turn.librarySizes.getOrDefault(humanPlayerId, 0);
            int grave  = turn.graveyardSizes.getOrDefault(humanPlayerId, 0);
            int exile  = turn.exileSizes.getOrDefault(humanPlayerId, 0);
            int bf     = turn.battlefieldCounts.getOrDefault(humanPlayerId, 0);
            boolean won = humanPlayerId.equals(winnerId);
            String displayName = won ? "\uD83C\uDFC6 " + humanName : humanName;
            List<BattlefieldCardInfo> cards = turn.battlefieldCards.getOrDefault(humanPlayerId, Collections.emptyList());
            view.getMtgBoardPanelGameOver().setHumanState(displayName, life, hand, lib, grave, exile, bf, won, cards);
        }
        if (oppId != null) {
            String oppName = names.getOrDefault(oppId, oppId);
            int life   = turn.lifeTotals.getOrDefault(oppId, 0);
            int hand   = turn.handSizes.getOrDefault(oppId, 0);
            int lib    = turn.librarySizes.getOrDefault(oppId, 0);
            int grave  = turn.graveyardSizes.getOrDefault(oppId, 0);
            int exile  = turn.exileSizes.getOrDefault(oppId, 0);
            int bf     = turn.battlefieldCounts.getOrDefault(oppId, 0);
            boolean won = oppId.equals(winnerId);
            String displayName = won ? "\uD83C\uDFC6 " + oppName : oppName;
            List<BattlefieldCardInfo> cards = turn.battlefieldCards.getOrDefault(oppId, Collections.emptyList());
            view.getMtgBoardPanelGameOver().setOpponentState(displayName, life, hand, lib, grave, exile, bf, won, cards);
        }
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Replay launch
    // -----------------------------------------------------------------------

    private void launchReplay() {
        if (currentParser == null) return;

        int turnNumber = 1;
        if (currentTurnIndex >= 0 && currentTurnIndex < turns.size()) {
            TurnSnapshot snap = turns.get(currentTurnIndex);
            if (snap.isPreGame || snap.isGameOver || snap.isGameOverview) {
                for (TurnSnapshot s : turns) {
                    if (!s.isPreGame && !s.isGameOver && !s.isGameOverview) { turnNumber = s.turnNumber; break; }
                }
            } else {
                turnNumber = snap.turnNumber;
            }
        }

        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        JLabel lblDesc = new JLabel("<html><body style='width:340px'>"
                + "Start a new game from <b>Turn " + turnNumber + "</b> with the same decks. "
                + "The game state at that turn is reconstructed from the replay log. "
                + "Mulligan and coin toss are skipped. "
                + "Try different decisions and see if you can reach a better outcome."
                + "</body></html>");
        lblDesc.setFont(lblDesc.getFont().deriveFont(Font.PLAIN, 13f));
        lblDesc.setAlignmentX(0f);
        panel.add(lblDesc);
        panel.add(javax.swing.Box.createVerticalStrut(12));

        JCheckBox chkEnforce = new JCheckBox("Enforce Cards drawn order", true);
        chkEnforce.setFont(chkEnforce.getFont().deriveFont(Font.BOLD, 13f));
        chkEnforce.setAlignmentX(0f);
        panel.add(chkEnforce);
        panel.add(javax.swing.Box.createVerticalStrut(4));

        JLabel lblHint = new JLabel("<html><body style='width:340px; color:#888888; font-size:11px'>"
                + "When enabled, the library order matches the original game — "
                + "you will draw the same cards in the same order.</body></html>");
        lblHint.setAlignmentX(0f);
        panel.add(lblHint);

        String title = "Replay from Turn " + turnNumber;
        int result = JOptionPane.showConfirmDialog(
                null, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        boolean enforceDrawOrder = chkEnforce.isSelected();
        OriginalGameSummary summary = buildOriginalGameSummary();

        CSubmenuReplay.SINGLETON_INSTANCE.startReplayFromPath(
                currentParser.getReplayFile().getAbsolutePath(),
                enforceDrawOrder,
                turnNumber,
                summary);
    }

    private OriginalGameSummary buildOriginalGameSummary() {
        if (reconstructor == null || turns.isEmpty()) return null;

        List<OriginalGameSummary.TurnData> turnDataList = new ArrayList<>();
        for (TurnSnapshot snap : turns) {
            if (snap.isPreGame || snap.isGameOver || snap.isGameOverview) continue;
            turnDataList.add(new OriginalGameSummary.TurnData(
                    snap.turnNumber,
                    snap.lifeTotals,
                    snap.handSizes,
                    snap.librarySizes,
                    snap.events.size()));
        }

        String winnerName = null;
        if (currentParser != null) {
            String winnerId = currentParser.getWinner();
            if (winnerId != null) {
                Map<String, ReplayLogParser.PlayerInfo> players = currentParser.getPlayers();
                ReplayLogParser.PlayerInfo info = players.get(winnerId);
                if (info != null) winnerName = info.name;
            }
        }

        int totalTurns = 0;
        for (int i = turns.size() - 1; i >= 0; i--) {
            TurnSnapshot s = turns.get(i);
            if (!s.isPreGame && !s.isGameOver && !s.isGameOverview) { totalTurns = s.turnNumber; break; }
        }
        return new OriginalGameSummary(turnDataList, winnerName, totalTurns);
    }
}

