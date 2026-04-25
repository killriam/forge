package forge.screens.home.replay;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import forge.toolbox.FSkin.SkinnedPanel;
import net.miginfocom.swing.MigLayout;

/**
 * Visual box displaying a single player's game state in the Game Learning Viewer.
 * Shows name, life total (colour-coded), and zone counts (hand, library, graveyard,
 * exile, battlefield) in a compact card-style layout.
 */
public class PlayerStateBox extends SkinnedPanel {
    private static final long serialVersionUID = 1L;

    private static final Color CLR_LIFE_HIGH  = new Color(80, 200, 80);   // green
    private static final Color CLR_LIFE_MED   = new Color(220, 180, 40);  // yellow
    private static final Color CLR_LIFE_LOW   = new Color(220, 60, 60);   // red
    private static final Color CLR_ACTIVE_BG  = new Color(50, 80, 120, 120);
    private static final Color CLR_INACTIVE_BG = new Color(30, 30, 30, 80);
    private static final Color CLR_BORDER_ACTIVE   = new Color(100, 160, 220);
    private static final Color CLR_BORDER_INACTIVE = new Color(80, 80, 80);

    private final JLabel lblName;
    private final JLabel lblLife;
    private final JLabel lblLifeDelta;
    private final JLabel lblHand;
    private final JLabel lblLibrary;
    private final JLabel lblGraveyard;
    private final JLabel lblExile;
    private final JLabel lblBattlefield;

    private boolean isActive = false;

    public PlayerStateBox() {
        setLayout(new MigLayout("insets 8 10 8 10, gap 4, wrap 1, fill"));
        setOpaque(false);

        // Player name
        lblName = new JLabel();
        lblName.setFont(lblName.getFont().deriveFont(Font.BOLD, 15f));
        lblName.setForeground(Color.WHITE);
        lblName.setHorizontalAlignment(SwingConstants.CENTER);
        add(lblName, "growx, pushx, align center");

        // Life total (large)
        lblLife = new JLabel();
        lblLife.setFont(lblLife.getFont().deriveFont(Font.BOLD, 32f));
        lblLife.setForeground(CLR_LIFE_HIGH);
        lblLife.setHorizontalAlignment(SwingConstants.CENTER);
        add(lblLife, "growx, pushx, align center, gaptop 2");

        // Life delta (e.g. "-3" from previous turn)
        lblLifeDelta = new JLabel();
        lblLifeDelta.setFont(lblLifeDelta.getFont().deriveFont(Font.ITALIC, 11f));
        lblLifeDelta.setForeground(new Color(180, 180, 180));
        lblLifeDelta.setHorizontalAlignment(SwingConstants.CENTER);
        add(lblLifeDelta, "growx, pushx, align center, h 16!");

        // Zone counts in a grid row
        lblHand        = createZoneLabel("\u270B");  // ✋ hand
        lblLibrary     = createZoneLabel("\uD83D\uDCDA"); // 📚 library
        lblGraveyard   = createZoneLabel("\u26B0");  // ⚰ graveyard
        lblExile       = createZoneLabel("\u2734");   // ✴ exile
        lblBattlefield = createZoneLabel("\u2694");   // ⚔ battlefield

        // Zone row panel
        SkinnedPanel zoneRow = new SkinnedPanel(new MigLayout("insets 0, gap 6, align center"));
        zoneRow.setOpaque(false);
        zoneRow.add(lblHand);
        zoneRow.add(lblLibrary);
        zoneRow.add(lblGraveyard);
        zoneRow.add(lblExile);
        zoneRow.add(lblBattlefield);
        add(zoneRow, "growx, pushx, align center, gaptop 4");

        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    private JLabel createZoneLabel(String icon) {
        JLabel lbl = new JLabel(icon + " 0");
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
        lbl.setForeground(new Color(190, 190, 190));
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setToolTipText("");
        return lbl;
    }

    /**
     * Update the box with current turn data.
     *
     * @param name         player display name
     * @param life         current life total
     * @param lifeDelta    life change since last turn (0 if first turn)
     * @param hand         cards in hand
     * @param library      cards in library
     * @param graveyard    cards in graveyard
     * @param exile        cards in exile
     * @param battlefield  permanents on battlefield
     * @param active       true if this player is the active player this turn
     */
    public void update(String name, int life, int lifeDelta,
                       int hand, int library, int graveyard,
                       int exile, int battlefield, boolean active) {
        this.isActive = active;

        lblName.setText(name != null ? name : "?");
        lblLife.setText(String.valueOf(life));

        // Colour-code life
        if (life <= 5) {
            lblLife.setForeground(CLR_LIFE_LOW);
        } else if (life <= 15) {
            lblLife.setForeground(CLR_LIFE_MED);
        } else {
            lblLife.setForeground(CLR_LIFE_HIGH);
        }

        // Life delta
        if (lifeDelta != 0) {
            String deltaStr = (lifeDelta > 0 ? "+" : "") + lifeDelta;
            lblLifeDelta.setText(deltaStr);
            lblLifeDelta.setForeground(lifeDelta > 0 ? CLR_LIFE_HIGH : CLR_LIFE_LOW);
        } else {
            lblLifeDelta.setText("");
        }

        lblHand.setText("\u270B " + hand);
        lblHand.setToolTipText("Hand: " + hand + " cards");
        lblLibrary.setText("\uD83D\uDCDA " + library);
        lblLibrary.setToolTipText("Library: " + library + " cards");
        lblGraveyard.setText("\u26B0 " + graveyard);
        lblGraveyard.setToolTipText("Graveyard: " + graveyard + " cards");
        lblExile.setText("\u2734 " + exile);
        lblExile.setToolTipText("Exile: " + exile + " cards");
        lblBattlefield.setText("\u2694 " + battlefield);
        lblBattlefield.setToolTipText("Battlefield: " + battlefield + " permanents");

        // Active player gets highlighted border
        if (active) {
            lblName.setForeground(new Color(140, 200, 255));
        } else {
            lblName.setForeground(Color.WHITE);
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 10;

        // Background
        g2.setColor(isActive ? CLR_ACTIVE_BG : CLR_INACTIVE_BG);
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

        // Border
        g2.setColor(isActive ? CLR_BORDER_ACTIVE : CLR_BORDER_INACTIVE);
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        g2.dispose();
        super.paintComponent(g);
    }
}


