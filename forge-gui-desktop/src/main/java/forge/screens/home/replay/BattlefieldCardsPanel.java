package forge.screens.home.replay;

import forge.game.ReplayStateReconstructor.BattlefieldCardInfo;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin.SkinnedPanel;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;

/**
 * Scrollable panel showing all cards on a player's battlefield.
 * <p>
 * Cards are displayed using {@link ReplayPlayArea} which groups them by type
 * (Lands / Creatures / Others) and arranges them in rows with adaptive card
 * sizing — matching the layout style of the real game's {@code PlayArea}.
 * Falls back to colour-coded chips when card images are unavailable.
 */
public class BattlefieldCardsPanel extends SkinnedPanel {
    private static final long serialVersionUID = 1L;

    private final ReplayPlayArea playArea;
    private final FScrollPane scrollPane;
    private final JLabel emptyLabel;

    /** Construct with default settings. */
    public BattlefieldCardsPanel() {
        this(false);
    }

    /**
     * Construct a battlefield panel.
     *
     * @param mirror if true, lands are shown at the top (opponent style);
     *               if false, creatures on top (human style).
     */
    public BattlefieldCardsPanel(boolean mirror) {
        setLayout(new BorderLayout());
        setOpaque(false);

        playArea = new ReplayPlayArea(mirror);
        scrollPane = new FScrollPane(playArea, true);
        add(scrollPane, BorderLayout.CENTER);

        emptyLabel = new JLabel("No permanents");
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 11f));
        emptyLabel.setForeground(new Color(100, 100, 100));
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setVerticalAlignment(SwingConstants.CENTER);

        showEmpty();
    }

    /**
     * Backwards-compatible constructor — accepts cardW/cardH but ignores them
     * (card size is now adaptive). The mirror flag defaults to false.
     */
    public BattlefieldCardsPanel(int cardW, int cardH) {
        this(false);
    }

    /** Update the displayed cards for this player's battlefield. */
    public void setCards(List<BattlefieldCardInfo> cards) {
        if (cards == null || cards.isEmpty()) {
            showEmpty();
        } else {
            showPlayArea();
            playArea.setCards(cards);
        }
    }

    private void showEmpty() {
        remove(scrollPane);
        add(emptyLabel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void showPlayArea() {
        remove(emptyLabel);
        add(scrollPane, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    // --- Static colour utilities (kept for external use) ---

    /** Sort order: Land=0, Creature=1, Artifact=2, Enchantment=3, Planeswalker=4, Other=5 */
    static int typeOrder(String type) {
        if (type == null) return 5;
        String t = type.toLowerCase();
        if (t.contains("land"))         return 0;
        if (t.contains("creature"))     return 1;
        if (t.contains("artifact"))     return 2;
        if (t.contains("enchantment"))  return 3;
        if (t.contains("planeswalker")) return 4;
        return 5;
    }

    /** Colour for each card type category. Used by fallback rendering. */
    public static Color getTypeColor(String type) {
        if (type == null) return new Color(60, 60, 70);
        String t = type.toLowerCase();
        if (t.contains("land"))         return new Color(120, 85, 35);
        if (t.contains("creature"))     return new Color(35, 120, 55);
        if (t.contains("artifact"))     return new Color(90, 100, 110);
        if (t.contains("enchantment"))  return new Color(70, 50, 140);
        if (t.contains("planeswalker")) return new Color(180, 110, 25);
        return new Color(50, 75, 140);
    }
}
