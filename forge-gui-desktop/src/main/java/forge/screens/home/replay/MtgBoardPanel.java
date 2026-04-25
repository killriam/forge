package forge.screens.home.replay;

import forge.game.ReplayStateReconstructor.BattlefieldCardInfo;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * MTG-style battlefield panel for the Game Learning Viewer.
 *
 * Layout (top → bottom):
 *   ┌─────────────────────────────────────────┐
 *   │  [Opponent info bar: name ❤ zones]      │
 *   │  [Opponent's permanents — scrollable]   │
 *   ├═════════ midfield line ═════════════════╡
 *   │  [Human's permanents — scrollable]      │
 *   │  [Human info bar: name ❤ zones]         │
 *   └─────────────────────────────────────────┘
 *
 * Cards are displayed using {@link ReplayPlayArea} (via {@link BattlefieldCardsPanel})
 * which groups permanents by type (Lands / Creatures / Others) in rows with
 * adaptive card sizing — matching the layout style of the real game's PlayArea.
 */
public class MtgBoardPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // --- Info bar ---
    private static final Color CLR_LIFE_HIGH = new Color(80, 200, 80);
    private static final Color CLR_LIFE_MED  = new Color(220, 180, 40);
    private static final Color CLR_LIFE_LOW  = new Color(220, 60, 60);
    private static final Color CLR_ACTIVE    = new Color(120, 200, 255);
    private static final Color CLR_NORMAL    = new Color(190, 190, 190);

    // --- Opponent section ---
    private final InfoBar oppBar;
    private final BattlefieldCardsPanel oppBf;

    // --- Human section ---
    private final BattlefieldCardsPanel humanBf;
    private final InfoBar humanBar;

    public MtgBoardPanel() {
        setLayout(new MigLayout("insets 0, gap 0, wrap 1, fill, flowy"));
        setOpaque(false);

        oppBar  = new InfoBar(false);
        oppBf   = new BattlefieldCardsPanel(true);   // mirror: lands top, creatures bottom
        oppBf.setOpaque(false);

        humanBf  = new BattlefieldCardsPanel(false);  // normal: creatures top, lands bottom
        humanBf.setOpaque(false);
        humanBar = new InfoBar(true);

        // Opponent area (top half) — darker blue-grey tint
        JPanel oppArea = buildArea(true, oppBar, oppBf, false);
        add(oppArea, "growx, growy 50, h 50%!");

        // Human area (bottom half) — green felt tint
        JPanel humanArea = buildArea(false, humanBar, humanBf, true);
        add(humanArea, "growx, growy 50, h 50%!");
    }

    /**
     * Update the opponent section.
     */
    public void setOpponentState(String name, int life, int hand, int library,
                                  int graveyard, int exile, int bf, boolean active,
                                  List<BattlefieldCardInfo> cards) {
        oppBar.update(name, life, hand, library, graveyard, exile, bf, active);
        oppBf.setCards(cards);
    }

    /**
     * Update the human player section.
     */
    public void setHumanState(String name, int life, int hand, int library,
                               int graveyard, int exile, int bf, boolean active,
                               List<BattlefieldCardInfo> cards) {
        humanBar.update(name, life, hand, library, graveyard, exile, bf, active);
        humanBf.setCards(cards);
    }

    // -----------------------------------------------------------------------
    // Helper: build a gradient area containing info bar + battlefield panel
    // -----------------------------------------------------------------------

    private static JPanel buildArea(boolean isOpponent,
                                     InfoBar bar,
                                     BattlefieldCardsPanel bf,
                                     boolean infoBarAtBottom) {
        JPanel area = new JPanel(new MigLayout("insets 4 6 4 6, gap 4, wrap 1, fill")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int w = getWidth();
                int h = getHeight();
                // Gradient background: opponent = blue-grey, human = green
                Color topColor, bottomColor;
                if (isOpponent) {
                    topColor = new Color(18, 30, 45);
                    bottomColor = new Color(22, 38, 50);
                } else {
                    topColor = new Color(14, 42, 28);
                    bottomColor = new Color(18, 50, 34);
                }
                GradientPaint gp = new GradientPaint(0, 0, topColor, 0, h, bottomColor);
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        area.setOpaque(false);
        area.setBorder(new MidlineBorder(infoBarAtBottom));

        if (infoBarAtBottom) {
            area.add(bf,  "grow, push");
            area.add(bar, "growx, h 36!");
        } else {
            area.add(bar, "growx, h 36!");
            area.add(bf,  "grow, push");
        }
        return area;
    }

    // -----------------------------------------------------------------------
    // Inner: border that draws the midfield line at one side
    // -----------------------------------------------------------------------

    private static class MidlineBorder implements javax.swing.border.Border {
        private static final Color LINE_COLOR = new Color(80, 200, 120, 120);
        private final boolean bottom;

        MidlineBorder(boolean drawOnBottom) {
            this.bottom = drawOnBottom;
        }

        @Override
        public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(LINE_COLOR);
            g2.setStroke(new BasicStroke(2f));
            if (bottom) {
                g2.drawLine(x + 10, y + h - 1, x + w - 10, y + h - 1);
            } else {
                g2.drawLine(x + 10, y, x + w - 10, y);
            }
            g2.dispose();
        }

        @Override public java.awt.Insets getBorderInsets(java.awt.Component c) { return new java.awt.Insets(2, 0, 2, 0); }
        @Override public boolean isBorderOpaque() { return false; }
    }

    // -----------------------------------------------------------------------
    // Inner: compact horizontal info bar for one player
    // -----------------------------------------------------------------------

    private static class InfoBar extends JPanel {
        private static final long serialVersionUID = 1L;

        private final JLabel lblName;
        private final JLabel lblLife;
        private final JLabel lblZones;
        private final boolean isHuman;

        InfoBar(boolean isHuman) {
            this.isHuman = isHuman;
            setLayout(new MigLayout("insets 2 8 2 8, gap 8, fill, nogrid"));
            setOpaque(false);

            lblName = new JLabel(isHuman ? "You" : "Opponent");
            lblName.setFont(lblName.getFont().deriveFont(Font.BOLD, 13f));
            lblName.setForeground(Color.WHITE);

            lblLife = new JLabel("20");
            lblLife.setFont(lblLife.getFont().deriveFont(Font.BOLD, 22f));
            lblLife.setForeground(CLR_LIFE_HIGH);
            lblLife.setHorizontalAlignment(SwingConstants.CENTER);

            lblZones = new JLabel("");
            lblZones.setFont(lblZones.getFont().deriveFont(Font.PLAIN, 11f));
            lblZones.setForeground(CLR_NORMAL);

            if (isHuman) {
                add(lblLife,  "w 50!, gapright 6");
                add(lblName,  "growx, pushx");
                add(lblZones, "gapleft 8");
            } else {
                add(lblZones, "gapright 8");
                add(lblName,  "growx, pushx");
                add(lblLife,  "w 50!, gapleft 6");
            }

            setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        protected void paintComponent(Graphics g) {
            // Semi-transparent bar background
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }

        void update(String name, int life, int hand, int library,
                    int graveyard, int exile, int bf, boolean active) {
            lblName.setText(name != null ? name : "?");
            lblName.setForeground(active ? CLR_ACTIVE : Color.WHITE);

            // Life with heart emoji prefix
            lblLife.setText("\u2764 " + life);
            if (life <= 5)       lblLife.setForeground(CLR_LIFE_LOW);
            else if (life <= 15) lblLife.setForeground(CLR_LIFE_MED);
            else                 lblLife.setForeground(CLR_LIFE_HIGH);

            // Zone indicators with icons
            String zoneText = "<html>"
                    + "<span style='color:#aabbcc'>\u270B</span>" + hand
                    + " &nbsp; <span style='color:#8899aa'>\uD83D\uDCDA</span>" + library
                    + " &nbsp; <span style='color:#997766'>\u26B0</span>" + graveyard
                    + " &nbsp; <span style='color:#9999aa'>\u2734</span>" + exile
                    + " &nbsp; <span style='color:#88aa88'>\u2694</span>" + bf
                    + "</html>";
            lblZones.setText(zoneText);
            lblZones.setForeground(active ? new Color(170, 200, 255) : CLR_NORMAL);

            String tooltip = "Hand: " + hand + "  Library: " + library
                    + "  Grave: " + graveyard + "  Exile: " + exile
                    + "  Battlefield: " + bf;
            lblZones.setToolTipText(tooltip);
        }
    }
}
