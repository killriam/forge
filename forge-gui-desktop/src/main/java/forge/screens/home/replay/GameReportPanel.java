package forge.screens.home.replay;

import forge.game.BlunderEntry;
import forge.game.GameEvaluationReport;
import forge.game.ReplayStateReconstructor.TurnSnapshot;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shows game-level analytics:
 * <ul>
 *   <li>Life-total timeline (line chart across all turns)</li>
 *   <li>Summary stat cards (Spell Velocity, Card Draw Efficiency, Land Drop Rate, Critical Turn)</li>
 *   <li>Scrollable blunder / notable-moment table</li>
 * </ul>
 */
public class GameReportPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color BG      = new Color(28, 28, 35);
    private static final Color TEXT    = new Color(180, 195, 210);
    private static final Color DIVIDER = new Color(50, 50, 65);

    // Per-player line colours (up to 4 players)
    static final Color[] PLAYER_COLORS = {
        new Color(100, 180, 255),
        new Color(255, 130, 80),
        new Color(100, 220, 120),
        new Color(220, 100, 220)
    };

    private final LifeChartPanel    lifeChart;
    private final JPanel            statsRow;
    private final JTable            blunderTable;
    private final DefaultTableModel blunderModel;

    public GameReportPanel() {
        setOpaque(true);
        setBackground(BG);
        setLayout(new MigLayout("insets 4, gap 4, fill, wrap 1"));

        // Life chart
        lifeChart = new LifeChartPanel();
        add(lifeChart, "growx, h 100!, shrink 0, gapbottom 4");

        // Stat cards row
        statsRow = new JPanel(new MigLayout("insets 0, gap 6, fill"));
        statsRow.setOpaque(false);
        add(statsRow, "growx, h 60!, shrink 0");

        // Blunder section
        JLabel lblBlunders = new JLabel("Notable Moments & Blunders");
        lblBlunders.setFont(lblBlunders.getFont().deriveFont(Font.BOLD, 11f));
        lblBlunders.setForeground(TEXT);
        add(lblBlunders, "growx, h 18!, gaptop 2");

        blunderModel = new DefaultTableModel(
                new String[]{"Turn", "Type", "Severity", "Details"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        blunderTable = new JTable(blunderModel);
        blunderTable.setBackground(new Color(32, 32, 42));
        blunderTable.setForeground(TEXT);
        blunderTable.setGridColor(DIVIDER);
        blunderTable.setRowHeight(22);
        blunderTable.setFont(blunderTable.getFont().deriveFont(Font.PLAIN, 11f));
        blunderTable.getTableHeader().setBackground(new Color(22, 22, 30));
        blunderTable.getTableHeader().setForeground(TEXT);
        blunderTable.getColumnModel().getColumn(0).setMaxWidth(45);
        blunderTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        blunderTable.getColumnModel().getColumn(2).setMaxWidth(75);
        blunderTable.getColumnModel().getColumn(3).setPreferredWidth(350);

        // Colour severity cells
        blunderTable.getColumnModel().getColumn(2).setCellRenderer(new SeverityCellRenderer());

        JScrollPane blunderScroll = new JScrollPane(blunderTable);
        blunderScroll.setOpaque(false);
        blunderScroll.getViewport().setBackground(new Color(32, 32, 42));
        blunderScroll.setBorder(BorderFactory.createLineBorder(DIVIDER));
        add(blunderScroll, "grow, push");
    }

    /**
     * Populate the panel with a completed evaluation report.
     *
     * @param report      computed evaluation (may be null while loading)
     * @param turns       turn snapshots (for the life chart)
     * @param playerNames player ID → display name
     */
    public void setData(GameEvaluationReport report,
                        List<TurnSnapshot> turns,
                        Map<String, String> playerNames) {

        // Life chart — always update from turn snapshots
        lifeChart.setTurns(turns, playerNames);

        if (report == null) return;

        // --- Stat cards ---
        statsRow.removeAll();
        String hid = report.humanPlayerId;

        addStatCard("Spell Velocity", formatFloat(report.spellVelocity.get(hid), "spells/turn"),
                report.spellVelocity, playerNames);
        addStatCard("Draw Rate", formatFloat(report.cardDrawEfficiency.get(hid), "cards/turn"),
                report.cardDrawEfficiency, playerNames);
        addStatCard("Land Drop Rate", formatPct(report.landDropRate.get(hid)),
                report.landDropRate, playerNames);
        if (report.criticalTurn > 0) {
            addSimpleCard("Critical Turn", "Turn " + report.criticalTurn);
        }

        statsRow.revalidate();
        statsRow.repaint();

        // --- Blunder table ---
        blunderModel.setRowCount(0);
        if (report.blunders.isEmpty()) {
            blunderModel.addRow(new Object[]{"—", "No blunders detected", "", ""});
        } else {
            for (BlunderEntry bl : report.blunders) {
                blunderModel.addRow(new Object[]{
                        bl.turnNumber,
                        bl.type.label,
                        bl.severity.name(),
                        bl.explanation
                });
            }
        }

        revalidate();
        repaint();
    }

    // -----------------------------------------------------------------------
    // Stat card helpers
    // -----------------------------------------------------------------------

    private void addStatCard(String title, String primaryValue,
                             Map<String, Float> allValues,
                             Map<String, String> names) {
        JPanel card = makeCardPanel();
        JLabel lbl1 = makeSmallLabel(title);
        JLabel lbl2 = makeBigLabel(primaryValue);

        // Build tooltip: all players
        StringBuilder tt = new StringBuilder("<html>");
        int idx = 0;
        for (Map.Entry<String, Float> e : allValues.entrySet()) {
            Color c = PLAYER_COLORS[idx % PLAYER_COLORS.length];
            tt.append("<font color='#").append(Integer.toHexString(c.getRGB() & 0xFFFFFF))
              .append("'>").append(names.getOrDefault(e.getKey(), e.getKey()))
              .append("</font>: ").append(String.format("%.2f", e.getValue()))
              .append("<br>");
            idx++;
        }
        tt.append("</html>");
        card.setToolTipText(tt.toString());

        card.add(lbl1, "growx");
        card.add(lbl2, "growx");
        statsRow.add(card, "w 115!, h 50!, growx");
    }

    private void addSimpleCard(String title, String value) {
        JPanel card = makeCardPanel();
        card.add(makeSmallLabel(title), "growx");
        card.add(makeBigLabel(value),   "growx");
        statsRow.add(card, "w 115!, h 50!, growx");
    }

    private static JPanel makeCardPanel() {
        JPanel p = new JPanel(new MigLayout("insets 5, wrap 1, fill"));
        p.setOpaque(true);
        p.setBackground(new Color(38, 38, 52));
        p.setBorder(BorderFactory.createLineBorder(new Color(58, 58, 78)));
        return p;
    }

    private static JLabel makeSmallLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 9f));
        l.setForeground(new Color(135, 145, 160));
        return l;
    }

    private static JLabel makeBigLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        l.setForeground(new Color(210, 225, 245));
        return l;
    }

    private static String formatFloat(Float v, String unit) {
        return v != null ? String.format("%.2f %s", v, unit) : "N/A";
    }

    private static String formatPct(Float v) {
        return v != null ? String.format("%.0f%%", v * 100) : "N/A";
    }

    // -----------------------------------------------------------------------
    // Severity cell renderer
    // -----------------------------------------------------------------------

    private static class SeverityCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String s = value != null ? value.toString() : "";
            switch (s) {
                case "CRITICAL": setForeground(new Color(240, 80, 80));  break;
                case "WARNING":  setForeground(new Color(230, 175, 50)); break;
                default:         setForeground(new Color(120, 180, 120)); break;
            }
            return this;
        }
    }

    // -----------------------------------------------------------------------
    // Life Chart
    // -----------------------------------------------------------------------

    /**
     * Simple line chart showing life totals across all turns for each player.
     */
    static class LifeChartPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private List<TurnSnapshot> turns;
        private List<String>       orderedIds;
        private Map<String, String> names;

        LifeChartPanel() {
            setOpaque(true);
            setBackground(BG);
            setPreferredSize(new Dimension(300, 100));
        }

        void setTurns(List<TurnSnapshot> turns, Map<String, String> playerNames) {
            this.turns = turns;
            this.names = playerNames;
            this.orderedIds = turns != null && !turns.isEmpty()
                    ? new ArrayList<>(turns.get(0).lifeTotals.keySet())
                    : new ArrayList<>();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (turns == null || turns.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w   = getWidth();
            int h   = getHeight();
            int padL = 30, padR = 8, padT = 8, padB = 18;
            int cW  = w - padL - padR;
            int cH  = h - padT - padB;

            // Max life for Y scale
            int maxLife = 20;
            for (TurnSnapshot t : turns) {
                for (int life : t.lifeTotals.values()) {
                    if (life > maxLife) maxLife = life;
                }
            }

            // Draw grid
            g2.setColor(new Color(45, 45, 55));
            for (int life = 0; life <= maxLife; life += 10) {
                int gy = padT + cH - (int)((float) life / maxLife * cH);
                g2.drawLine(padL, gy, padL + cW, gy);
            }
            // Y-axis labels
            Font axisFont = getFont().deriveFont(Font.PLAIN, 8f);
            g2.setFont(axisFont);
            g2.setColor(new Color(110, 110, 125));
            FontMetrics fm = g2.getFontMetrics();
            for (int life = 0; life <= maxLife; life += 10) {
                int gy = padT + cH - (int)((float) life / maxLife * cH);
                String lbl = String.valueOf(life);
                g2.drawString(lbl, padL - fm.stringWidth(lbl) - 2, gy + fm.getAscent() / 2);
            }

            // Draw life lines per player
            int numTurns = turns.size();
            g2.setStroke(new BasicStroke(1.8f));
            for (int p = 0; p < orderedIds.size(); p++) {
                String pid  = orderedIds.get(p);
                Color col   = PLAYER_COLORS[p % PLAYER_COLORS.length];
                g2.setColor(col);

                int prevX = -1, prevY = -1;
                for (int t = 0; t < numTurns; t++) {
                    int life = turns.get(t).lifeTotals.getOrDefault(pid, 0);
                    int x    = padL + (numTurns > 1 ? (int)((float) t / (numTurns - 1) * cW) : cW / 2);
                    int y    = padT + cH - (int)((float) Math.max(0, life) / maxLife * cH);
                    if (prevX >= 0) {
                        g2.drawLine(prevX, prevY, x, y);
                    }
                    prevX = x; prevY = y;
                }

                // Legend dot + name at the right edge
                String name = names != null ? names.getOrDefault(pid, pid) : pid;
                int ly = padT + 8 + p * 12;
                g2.fillOval(padL + cW - 50, ly - 5, 7, 7);
                g2.setFont(axisFont);
                g2.setColor(col);
                g2.drawString(name, padL + cW - 40, ly);
            }

            // X-axis label
            g2.setColor(new Color(110, 110, 125));
            g2.setFont(axisFont);
            g2.drawString("Turn", padL, h - 3);

            g2.dispose();
        }
    }
}




