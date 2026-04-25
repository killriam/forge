package forge.screens.home.replay;

import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Map;

/**
 * Game Overview panel — shown when selecting the first "📊 Game Overview"
 * entry in the turn sidebar.  Displays a compact summary:
 *   - Players with deck names
 *   - Winner & game length
 *   - Quick stats
 */
public class GameOverviewPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final Color BG = new Color(22, 26, 32);
    private static final Color CARD_BG = new Color(30, 36, 44);
    private static final Color ACCENT_GOLD = new Color(220, 190, 60);
    private static final Color ACCENT_GREEN = new Color(80, 200, 80);
    private static final Color TEXT_PRIMARY = new Color(220, 230, 240);
    private static final Color TEXT_SECONDARY = new Color(150, 165, 180);

    private final JLabel lblTitle;
    private final JPanel playersPanel;
    private final JLabel lblWinnerTitle;
    private final JLabel lblWinner;
    private final JLabel lblGameLength;

    public GameOverviewPanel() {
        setLayout(new MigLayout("insets 16, gap 10, fill, wrap 1"));
        setOpaque(true);
        setBackground(BG);

        // Title
        lblTitle = new JLabel("\uD83D\uDCCA Game Overview");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 20f));
        lblTitle.setForeground(TEXT_PRIMARY);
        add(lblTitle, "growx, gapbottom 8");

        // Separator
        add(createSeparator(), "growx, h 2!, gapbottom 8");

        // Players section
        playersPanel = new JPanel(new MigLayout("insets 0, gap 8, fill, wrap 1"));
        playersPanel.setOpaque(false);
        add(playersPanel, "growx, gapbottom 12");

        // Winner section
        JPanel winnerCard = createCard();
        lblWinnerTitle = new JLabel("\uD83C\uDFC6 WINNER");
        lblWinnerTitle.setFont(lblWinnerTitle.getFont().deriveFont(Font.BOLD, 11f));
        lblWinnerTitle.setForeground(ACCENT_GOLD);
        winnerCard.add(lblWinnerTitle, "growx");

        lblWinner = new JLabel("—");
        lblWinner.setFont(lblWinner.getFont().deriveFont(Font.BOLD, 16f));
        lblWinner.setForeground(TEXT_PRIMARY);
        winnerCard.add(lblWinner, "growx");

        lblGameLength = new JLabel("");
        lblGameLength.setFont(lblGameLength.getFont().deriveFont(Font.PLAIN, 12f));
        lblGameLength.setForeground(TEXT_SECONDARY);
        winnerCard.add(lblGameLength, "growx");

        add(winnerCard, "growx, gapbottom 8");

        // Spacer push
        add(new JLabel(""), "grow, push");
    }

    /**
     * Populate the overview with game data.
     *
     * @param playerNames  player ID → display name
     * @param deckNames    player ID → deck name (may be empty)
     * @param winnerName   name of the winning player, or null
     * @param totalTurns   total number of turns in the game
     */
    public void setOverview(Map<String, String> playerNames,
                            Map<String, String> deckNames,
                            String winnerName,
                            int totalTurns) {
        // --- Players ---
        playersPanel.removeAll();
        JLabel lblPlayersTitle = new JLabel("\uD83C\uDFAE PLAYERS");
        lblPlayersTitle.setFont(lblPlayersTitle.getFont().deriveFont(Font.BOLD, 11f));
        lblPlayersTitle.setForeground(new Color(100, 180, 220));
        playersPanel.add(lblPlayersTitle, "growx, gapbottom 4");

        if (playerNames != null) {
            for (Map.Entry<String, String> entry : playerNames.entrySet()) {
                String pid = entry.getKey();
                String name = entry.getValue();
                String deck = deckNames != null ? deckNames.get(pid) : null;

                JPanel playerCard = createCard();

                // Player name with winner indicator
                boolean isWinner = winnerName != null && winnerName.equals(name);
                String nameText = isWinner ? "\uD83C\uDFC6 " + name : name;
                JLabel lblName = new JLabel(nameText);
                lblName.setFont(lblName.getFont().deriveFont(Font.BOLD, 14f));
                lblName.setForeground(isWinner ? ACCENT_GOLD : TEXT_PRIMARY);
                playerCard.add(lblName, "growx");

                // Deck name
                if (deck != null && !deck.isEmpty()) {
                    JLabel lblDeck = new JLabel("\uD83C\uDCCF " + deck);
                    lblDeck.setFont(lblDeck.getFont().deriveFont(Font.PLAIN, 11f));
                    lblDeck.setForeground(TEXT_SECONDARY);
                    playerCard.add(lblDeck, "growx");
                }

                // Result badge
                if (isWinner) {
                    JLabel lblResult = new JLabel("WON");
                    lblResult.setFont(lblResult.getFont().deriveFont(Font.BOLD, 10f));
                    lblResult.setForeground(ACCENT_GREEN);
                    lblResult.setHorizontalAlignment(SwingConstants.LEFT);
                    playerCard.add(lblResult, "growx");
                } else if (winnerName != null) {
                    JLabel lblResult = new JLabel("LOST");
                    lblResult.setFont(lblResult.getFont().deriveFont(Font.BOLD, 10f));
                    lblResult.setForeground(new Color(220, 80, 80));
                    lblResult.setHorizontalAlignment(SwingConstants.LEFT);
                    playerCard.add(lblResult, "growx");
                }

                playersPanel.add(playerCard, "growx");
            }
        }

        // --- Winner ---
        if (winnerName != null) {
            lblWinner.setText(winnerName);
            lblWinner.setForeground(ACCENT_GOLD);
        } else {
            lblWinner.setText("Unknown / Draw");
            lblWinner.setForeground(TEXT_SECONDARY);
        }

        // --- Game length ---
        lblGameLength.setText("Game lasted " + totalTurns + " turns");

        playersPanel.revalidate();
        playersPanel.repaint();
        revalidate();
        repaint();
    }

    // --- Helpers ---

    private static JPanel createCard() {
        JPanel card = new JPanel(new MigLayout("insets 8 10 8 10, gap 3, fill, wrap 1"));
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 60, 70), 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        return card;
    }

    private static JPanel createSeparator() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(60, 70, 85));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
    }
}

