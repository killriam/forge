package forge.screens.home.replay;

import forge.game.TurnEvaluation;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Renders a horizontal bar chart for the 10 state-evaluation dimensions
 * defined in the MTG State Evaluation Spec (Sections 6.1–6.10).
 *
 * Each bar extends from the centre:
 *   - RIGHT (green) = human player advantage
 *   - LEFT  (red)   = opponent advantage
 *
 * Bars are normalized to [-1, +1]; the width of the full half is (chartWidth / 2).
 */
public class EvaluationDimensionPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color BG_EVEN    = new Color(30, 30, 38);
    private static final Color BG_ODD     = new Color(34, 34, 44);
    private static final Color BAR_POS    = new Color(55, 165, 75);
    private static final Color BAR_NEG    = new Color(185, 60, 55);
    private static final Color CENTRE_LINE = new Color(65, 65, 80);
    private static final Color TEXT_DIM   = new Color(155, 165, 180);
    private static final Color TEXT_VAL   = new Color(215, 230, 248);
    private static final Color TEXT_NA    = new Color(90, 90, 105);
    private static final Color HIGHLIGHT  = new Color(100, 180, 255, 60); // active-turn glow

    private static final int ROW_H    = 22;
    private static final int LABEL_W  = 102;
    private static final int VAL_W    = 38;
    private static final int PAD      = 4;

    private TurnEvaluation evaluation;
    private boolean         showHighlight;

    public EvaluationDimensionPanel() {
        setOpaque(true);
        setBackground(BG_EVEN);
        // Fixed preferred height = ROW_H * 10 rows + 2 header rows
        setPreferredSize(new Dimension(280, ROW_H * 12));
    }

    /** Assign the evaluation to render.  Pass {@code null} to show empty state. */
    public void setEvaluation(TurnEvaluation eval) {
        this.evaluation    = eval;
        this.showHighlight = (eval != null);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();

        // --- Header ---
        g2.setColor(new Color(22, 22, 30));
        g2.fillRect(0, 0, w, ROW_H * 2);

        Font headerFont = getFont().deriveFont(Font.BOLD, 10f);
        g2.setFont(headerFont);
        g2.setColor(TEXT_DIM);

        int barZone  = w - LABEL_W - VAL_W - PAD * 2;
        int midX     = LABEL_W + barZone / 2;

        g2.drawString("Dimension", PAD, ROW_H - 6);
        g2.drawString("Opp \u25C4", midX - 30, ROW_H - 6);
        g2.drawString("\u25BA You", midX + 6, ROW_H - 6);
        g2.drawString("Score", w - VAL_W - PAD, ROW_H - 6);

        // Thin separator
        g2.setColor(CENTRE_LINE);
        g2.drawLine(0, ROW_H * 2 - 1, w, ROW_H * 2 - 1);

        // --- Rows ---
        float[] dims = evaluation != null ? evaluation.getDimensions()
                : new float[TurnEvaluation.DIMENSION_NAMES.length];

        Font labelFont = getFont().deriveFont(Font.PLAIN, 10f);
        Font valFont   = getFont().deriveFont(Font.BOLD, 9f);
        FontMetrics valFm = g2.getFontMetrics(valFont);

        for (int i = 0; i < TurnEvaluation.DIMENSION_NAMES.length; i++) {
            int rowY = ROW_H * 2 + i * ROW_H;

            // Row background
            g2.setColor(i % 2 == 0 ? BG_EVEN : BG_ODD);
            g2.fillRect(0, rowY, w, ROW_H);

            // Active-turn highlight glow on first row if evaluation is set
            if (showHighlight && i == 0) {
                g2.setColor(HIGHLIGHT);
                g2.fillRect(0, rowY, w, ROW_H);
            }

            // Label
            g2.setFont(labelFont);
            g2.setColor(TEXT_DIM);
            g2.drawString(TurnEvaluation.DIMENSION_NAMES[i], PAD, rowY + ROW_H - 7);

            // Centre line
            g2.setColor(CENTRE_LINE);
            g2.drawLine(midX, rowY + 3, midX, rowY + ROW_H - 3);

            float val = dims[i];

            if (evaluation == null) {
                // No data — draw a faint dash
                g2.setFont(valFont);
                g2.setColor(TEXT_NA);
                g2.drawString("—", midX - 5, rowY + ROW_H - 7);
            } else {
                // Bar
                int halfBarMax = barZone / 2 - 2;
                int barW = Math.max(2, (int)(Math.abs(val) * halfBarMax));
                int barH = ROW_H - 8;
                int barY = rowY + 4;

                Color base = val >= 0 ? BAR_POS : BAR_NEG;
                int barX  = val >= 0 ? midX : midX - barW;
                GradientPaint gp = new GradientPaint(
                        barX, barY, base.brighter(),
                        barX + barW, barY, base.darker());
                g2.setPaint(gp);
                g2.fillRoundRect(barX, barY, barW, barH, 3, 3);

                // Value label
                g2.setFont(valFont);
                g2.setColor(TEXT_VAL);
                String valStr = String.format("%+.2f", val);
                int txtW = valFm.stringWidth(valStr);
                g2.drawString(valStr, w - txtW - PAD, rowY + ROW_H - 7);
            }
        }

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(280, ROW_H * (TurnEvaluation.DIMENSION_NAMES.length + 2));
    }
}

