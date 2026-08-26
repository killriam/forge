package forge.screens.home.replay;

import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;

/**
 * Panel showing game initialization details:
 * - Player names and deck names
 * - Starting hands (card counts)
 * - Play order and first player decision
 */
public class GameInitPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JLabel lblTitle;
    private final JTextArea txtInfo;

    public GameInitPanel() {
        setLayout(new MigLayout("insets 10, gap 8, fill, wrap 1"));
        setOpaque(true);
        setBackground(new Color(25, 30, 35));

        lblTitle = new JLabel("🎮 Game Initialization");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 16f));
        lblTitle.setForeground(new Color(200, 220, 240));

        txtInfo = new JTextArea();
        txtInfo.setEditable(false);
        txtInfo.setOpaque(true);
        txtInfo.setBackground(new Color(35, 40, 45));
        txtInfo.setForeground(new Color(220, 220, 220));
        txtInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtInfo.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        txtInfo.setLineWrap(true);
        txtInfo.setWrapStyleWord(true);

        add(lblTitle, "growx");
        add(txtInfo, "grow, push");
    }

    /**
     * Display game initialization information.
     *
     * @param playerNames map of player IDs to names
     * @param deckNames map of player IDs to deck names (optional, can be null)
     * @param startingHands map of player IDs to starting hand size
     * @param playOrder list of player IDs in play order
     * @param firstPlayer ID of the player who goes first
     * @param firstPlayerDecision optional decision made by first player (e.g., "play" or "draw")
     */
    public void setGameInitInfo(Map<String, String> playerNames,
                                 Map<String, String> deckNames,
                                 Map<String, Integer> startingHands,
                                 List<String> playOrder,
                                 String firstPlayer,
                                 String firstPlayerDecision) {
        setGameInitInfo(playerNames, deckNames, null, startingHands, playOrder, firstPlayer, firstPlayerDecision);
    }

    /**
     * Display game initialization information with team assignments.
     */
    public void setGameInitInfo(Map<String, String> playerNames,
                                 Map<String, String> deckNames,
                                 Map<String, Integer> playerTeams,
                                 Map<String, Integer> startingHands,
                                 List<String> playOrder,
                                 String firstPlayer,
                                 String firstPlayerDecision) {
        StringBuilder sb = new StringBuilder();

        sb.append("═══ PLAYERS ═══\n");
        for (Map.Entry<String, String> entry : playerNames.entrySet()) {
            String pid = entry.getKey();
            String name = entry.getValue();
            String deckName = deckNames != null ? deckNames.get(pid) : null;
            Integer team = playerTeams != null ? playerTeams.get(pid) : null;
            int handSize = startingHands != null ? startingHands.getOrDefault(pid, 7) : 7;

            sb.append("\n").append(name);
            if (team != null) {
                sb.append(" [Team ").append(team).append("]");
            }
            if (deckName != null && !deckName.isEmpty()) {
                sb.append(" — ").append(deckName);
            }
            if (team != null) {
                sb.append("\n  Team: Team ").append(team);
            }
            sb.append("\n  Starting hand: ").append(handSize).append(" cards");
            if (pid.equals(firstPlayer)) {
                sb.append("  ⭐ FIRST PLAYER");
            }
            sb.append("\n");
        }

        sb.append("\n═══ PLAY ORDER ═══\n");
        for (int i = 0; i < playOrder.size(); i++) {
            String pid = playOrder.get(i);
            String name = playerNames.getOrDefault(pid, pid);
            sb.append((i + 1)).append(". ").append(name);
            if (pid.equals(firstPlayer)) {
                sb.append(" (goes first)");
            }
            sb.append("\n");
        }

        if (firstPlayerDecision != null && !firstPlayerDecision.isEmpty()) {
            sb.append("\n═══ FIRST PLAYER DECISION ═══\n");
            String fpName = playerNames.getOrDefault(firstPlayer, firstPlayer);
            sb.append(fpName).append(" chose: ").append(firstPlayerDecision).append("\n");
        }

        txtInfo.setText(sb.toString());
        txtInfo.setCaretPosition(0);
    }

    /** Clear all information. */
    public void clear() {
        txtInfo.setText("No game initialization data available.");
    }
}

