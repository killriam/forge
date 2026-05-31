package forge.screens.home.replay;

import forge.game.ReplayStateReconstructor.TurnSnapshot;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JLabel;
import javax.swing.BorderFactory;
import net.miginfocom.swing.MigLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;

/**
 * Panel displaying Learning Helper Statistics (Chapter 8 of MTG State Evaluation Spec).
 * Shows per-turn metrics: Land Drop Rating, Available Mana, Cast Options, Color Coverage, etc.
 */
public class LearningStatisticsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JLabel lblHeader;
    private final JTextArea txtStatistics;

    public LearningStatisticsPanel() {
        setLayout(new MigLayout("insets 4, gap 4, fill, wrap 1"));
        setOpaque(false);

        lblHeader = new JLabel("Learning Helper Statistics (Chapter 8)");
        lblHeader.setFont(lblHeader.getFont().deriveFont(Font.BOLD, 13f));
        lblHeader.setForeground(new Color(180, 200, 220));

        txtStatistics = new JTextArea();
        txtStatistics.setEditable(false);
        txtStatistics.setLineWrap(true);
        txtStatistics.setWrapStyleWord(true);
        txtStatistics.setOpaque(false);
        txtStatistics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtStatistics.setForeground(new Color(200, 210, 220));
        txtStatistics.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        add(lblHeader, "growx");
        add(txtStatistics, "grow, push");
    }

    /**
     * Display statistics for the given turn snapshot.
     * @param turn the turn snapshot containing game state
     * @param prevTurn the previous turn snapshot (for delta calculations), or null
     * @param playerNames map of player IDs to names
     * @param focusPlayerId the player to focus statistics on (typically the human player)
     */
    public void setTurnStatistics(TurnSnapshot turn, TurnSnapshot prevTurn,
                                   Map<String, Integer> prevLifeTotals,
                                   Map<String, String> playerNames,
                                   String focusPlayerId) {
        if (turn == null || focusPlayerId == null) {
            txtStatistics.setText("(no statistics available)");
            return;
        }

        String playerName = playerNames != null
                ? playerNames.getOrDefault(focusPlayerId, focusPlayerId)
                : focusPlayerId;

        StringBuilder sb = new StringBuilder();
        sb.append("=== Turn ").append(turn.turnNumber).append(" Statistics for ")
          .append(playerName).append(" ===\n\n");

        // --- 8.2 Land Drop Rating ---
        sb.append("🌍 Land Drop Rating\n");
        int landsPlayedThisTurn = calculateLandsPlayedThisTurn(turn, prevTurn, focusPlayerId);
        String landRating;
        String landSymbol;
        if (landsPlayedThisTurn == 0) {
            landRating = "Bad (missed land drop)";
            landSymbol = "🔴";
        } else if (landsPlayedThisTurn == 1) {
            landRating = "Good (on curve)";
            landSymbol = "🟢";
        } else {
            landRating = "Super (accelerated)";
            landSymbol = "🌟";
        }
        sb.append("   ").append(landSymbol).append(" ").append(landRating)
          .append(" — ").append(landsPlayedThisTurn).append(" land(s) played this turn\n\n");

        // --- 8.3 Available Mana ---
        sb.append("💎 Available Mana\n");
        int totalLands = countLandsOnBattlefield(turn, focusPlayerId);
        sb.append("   Total lands on battlefield: ").append(totalLands).append("\n");
        sb.append("   (detailed mana pool tracking not yet implemented)\n\n");

        // --- Player State Summary ---
        sb.append("📊 Player State\n");
        int life = turn.lifeTotals.getOrDefault(focusPlayerId, 0);
        int hand = turn.handSizes.getOrDefault(focusPlayerId, 0);
        int library = turn.librarySizes.getOrDefault(focusPlayerId, 0);
        int graveyard = turn.graveyardSizes.getOrDefault(focusPlayerId, 0);
        int exile = turn.exileSizes.getOrDefault(focusPlayerId, 0);
        int battlefield = turn.battlefieldCounts.getOrDefault(focusPlayerId, 0);

        sb.append("   Life: ").append(life);
        if (prevLifeTotals != null) {
            int prevLife = prevLifeTotals.getOrDefault(focusPlayerId, life);
            int lifeDelta = life - prevLife;
            if (lifeDelta != 0) {
                sb.append(" (").append(lifeDelta > 0 ? "+" : "").append(lifeDelta).append(")");
            }
        }
        sb.append("\n");
        sb.append("   Hand: ").append(hand).append(" cards\n");
        sb.append("   Library: ").append(library).append(" cards\n");
        sb.append("   Graveyard: ").append(graveyard).append(" cards\n");
        sb.append("   Exile: ").append(exile).append(" cards\n");
        sb.append("   Battlefield: ").append(battlefield).append(" permanents\n\n");

        // --- 8.4 Cast Options in Hand ---
        sb.append("🃏 Cast Options in Hand\n");
        sb.append("   (requires card database integration)\n");
        sb.append("   Hand size: ").append(hand).append(" cards\n\n");

        // --- 8.5 Mana Color Coverage ---
        sb.append("🎨 Mana Color Coverage\n");
        sb.append("   (requires mana source analysis)\n\n");

        // --- Event Summary ---
        sb.append("📝 Events This Turn\n");
        if (turn.events == null || turn.events.isEmpty()) {
            sb.append("   (no events recorded)\n");
        } else {
            sb.append("   Total events: ").append(turn.events.size()).append("\n");
            // Show first few events
            int maxShow = Math.min(5, turn.events.size());
            for (int i = 0; i < maxShow; i++) {
                String event = turn.events.get(i).toString();
                // Truncate long events
                if (event.length() > 60) {
                    event = event.substring(0, 57) + "...";
                }
                sb.append("   • ").append(event).append("\n");
            }
            if (turn.events.size() > maxShow) {
                sb.append("   ... and ").append(turn.events.size() - maxShow)
                  .append(" more (see Events tab)\n");
            }
        }

        txtStatistics.setText(sb.toString());
        txtStatistics.setCaretPosition(0);
    }

    /**
     * Calculate how many lands were played this turn by comparing battlefield state.
     */
    private int calculateLandsPlayedThisTurn(TurnSnapshot turn, TurnSnapshot prevTurn, String playerId) {
        if (prevTurn == null) {
            // First turn: count lands on battlefield
            return countLandsOnBattlefield(turn, playerId);
        }

        int currentLands = countLandsOnBattlefield(turn, playerId);
        int prevLands = countLandsOnBattlefield(prevTurn, playerId);
        int delta = currentLands - prevLands;

        // Clamp to reasonable range (can't play negative lands, and >2 is rare but possible)
        return Math.max(0, Math.min(delta, 5));
    }

    /**
     * Count lands on the battlefield by examining card list.
     */
    private int countLandsOnBattlefield(TurnSnapshot turn, String playerId) {
        if (turn.battlefieldCards == null) return 0;
        var cards = turn.battlefieldCards.get(playerId);
        if (cards == null || cards.isEmpty()) return 0;

        int count = 0;
        for (var card : cards) {
            if (card.type != null && card.type.toLowerCase().contains("land")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Clear the statistics display.
     */
    public void clear() {
        txtStatistics.setText("");
    }
}


