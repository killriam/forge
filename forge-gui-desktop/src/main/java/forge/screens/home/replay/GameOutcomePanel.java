package forge.screens.home.replay;

import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;

/**
 * Panel showing game outcome details:
 * - Winner and game length
 * - Turn when each player lost
 * - Win condition / loss reason
 */
public class GameOutcomePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JLabel lblTitle;
    private final JTextArea txtOutcome;

    public GameOutcomePanel() {
        setLayout(new MigLayout("insets 10, gap 8, fill, wrap 1"));
        setOpaque(true);
        setBackground(new Color(25, 30, 35));

        lblTitle = new JLabel("🏆 Game Outcome");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 16f));
        lblTitle.setForeground(new Color(200, 220, 240));

        txtOutcome = new JTextArea();
        txtOutcome.setEditable(false);
        txtOutcome.setOpaque(true);
        txtOutcome.setBackground(new Color(35, 40, 45));
        txtOutcome.setForeground(new Color(220, 220, 220));
        txtOutcome.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtOutcome.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        txtOutcome.setLineWrap(true);
        txtOutcome.setWrapStyleWord(true);

        add(lblTitle, "growx");
        add(txtOutcome, "grow, push");
    }

    /**
     * Display game outcome information.
     *
     * @param playerNames map of player IDs to names
     * @param winnerId ID of the winning player (null if draw/unknown)
     * @param gameEndTurn turn number when the game ended
     * @param playerLossTurns map of player ID to turn they lost (optional)
     * @param winCondition description of how the game was won
     */
    public void setOutcome(Map<String, String> playerNames,
                           String winnerId,
                           int gameEndTurn,
                           Map<String, Integer> playerLossTurns,
                           String winCondition) {
        StringBuilder sb = new StringBuilder();

        sb.append("═══ WINNER ═══\n");
        if (winnerId != null && !winnerId.isEmpty()) {
            String winnerName = playerNames.getOrDefault(winnerId, winnerId);
            sb.append("🏆 ").append(winnerName).append("\n");
        } else {
            sb.append("— Draw or unknown outcome —\n");
        }

        sb.append("\n═══ GAME LENGTH ═══\n");
        sb.append("Game ended on Turn ").append(gameEndTurn).append("\n");

        if (playerLossTurns != null && !playerLossTurns.isEmpty()) {
            sb.append("\n═══ PLAYER ELIMINATIONS ═══\n");
            for (Map.Entry<String, Integer> entry : playerLossTurns.entrySet()) {
                String pid = entry.getKey();
                int turn = entry.getValue();
                String name = playerNames.getOrDefault(pid, pid);
                sb.append("☠ ").append(name).append(" lost on Turn ").append(turn);
                if (pid.equals(winnerId)) {
                    sb.append(" (but still won?)"); // shouldn't happen
                }
                sb.append("\n");
            }
        }

        if (winCondition != null && !winCondition.isEmpty()) {
            sb.append("\n═══ WIN CONDITION ═══\n");
            sb.append(winCondition).append("\n");
        }

        txtOutcome.setText(sb.toString());
        txtOutcome.setCaretPosition(0);
    }

    /** Clear all information. */
    public void clear() {
        txtOutcome.setText("No game outcome data available.");
    }
}

