package forge.screens.home.replay;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import forge.game.ReplayStateReconstructor.EventEntry;
import forge.toolbox.FScrollPane;
import net.miginfocom.swing.MigLayout;

/**
 * Visual timeline panel that displays L1 events for a turn with colour-coded type badges.
 * Used by the Game Learning Viewer to show what happened during a turn.
 */
public class EventTimelinePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final DefaultListModel<EventEntry> model = new DefaultListModel<>();
    private final JList<EventEntry> eventList;
    private final FScrollPane scrollPane;

    public EventTimelinePanel() {
        setLayout(new MigLayout("insets 0, gap 0, fill"));
        setOpaque(false);

        eventList = new JList<>(model);
        eventList.setCellRenderer(new EventCellRenderer());
        eventList.setOpaque(false);
        eventList.setBackground(new Color(0, 0, 0, 0));
        eventList.setFixedCellHeight(22);

        scrollPane = new FScrollPane(eventList, true);
        add(scrollPane, "grow, push");
    }

    /**
     * Update the event list with new events.
     */
    public void setEvents(List<EventEntry> events) {
        model.clear();
        if (events != null) {
            for (EventEntry e : events) {
                model.addElement(e);
            }
        }
        if (!model.isEmpty()) {
            eventList.ensureIndexIsVisible(0);
        }
    }

    /**
     * Custom cell renderer that shows a colour-coded type badge + event description.
     */
    private static class EventCellRenderer extends JPanel implements ListCellRenderer<EventEntry> {
        private static final long serialVersionUID = 1L;

        private final JLabel lblBadge = new JLabel();
        private final JLabel lblDesc  = new JLabel();

        EventCellRenderer() {
            setLayout(new MigLayout("insets 1 4 1 4, gap 6, fill"));
            setOpaque(true);

            lblBadge.setFont(lblBadge.getFont().deriveFont(Font.BOLD, 9f));
            lblBadge.setForeground(Color.WHITE);
            lblBadge.setHorizontalAlignment(JLabel.CENTER);
            lblBadge.setOpaque(true);
            lblBadge.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

            lblDesc.setFont(lblDesc.getFont().deriveFont(Font.PLAIN, 12f));
            lblDesc.setForeground(new Color(210, 210, 210));

            add(lblBadge, "w 80!, h 16!");
            add(lblDesc, "growx, pushx");
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends EventEntry> list,
                                                       EventEntry value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            if (value == null) {
                lblBadge.setText("");
                lblDesc.setText("");
                setBackground(new Color(20, 20, 20));
                return this;
            }

            String type = value.type != null ? value.type : "";
            lblBadge.setText(formatBadgeText(type));
            lblBadge.setBackground(getBadgeColor(type));

            String timeStr = value.timeMarker != null ? "[" + value.timeMarker + "] " : "";
            lblDesc.setText(timeStr + value.description);

            // Alternating row backgrounds
            if (index % 2 == 0) {
                setBackground(new Color(25, 25, 30));
            } else {
                setBackground(new Color(35, 35, 40));
            }

            if (isSelected) {
                setBackground(new Color(50, 60, 80));
            }

            return this;
        }

        private static String formatBadgeText(String type) {
            if (type.length() > 10) {
                // Abbreviate long type names
                return type.substring(0, 8).toUpperCase() + "…";
            }
            return type.toUpperCase();
        }

        private static Color getBadgeColor(String type) {
            switch (type) {
                case "CAST":              return new Color(60, 100, 180);
                case "RESOLVE":           return new Color(40, 120, 60);
                case "DAMAGE":            return new Color(180, 50, 50);
                case "LIFE":              return new Color(180, 50, 50);
                case "DRAW":              return new Color(50, 140, 170);
                case "MOVE":              return new Color(100, 100, 100);
                case "PLAY_LAND":         return new Color(120, 90, 40);
                case "PHASE_CHANGE":      return new Color(160, 140, 40);
                case "ACTIVE_PLAYER_CHANGE": return new Color(160, 140, 40);
                case "DECLARE_ATTACKERS": return new Color(160, 60, 60);
                case "DECLARE_BLOCKERS":  return new Color(60, 60, 160);
                case "DISCARD":           return new Color(140, 80, 140);
                case "COUNTER":           return new Color(160, 50, 50);
                case "TRIGGER":           return new Color(140, 100, 50);
                case "ACTIVATE":          return new Color(50, 120, 140);
                case "TAP":               return new Color(80, 80, 80);
                case "GAME_START":        return new Color(40, 120, 60);
                case "COMBAT":            return new Color(160, 60, 60);
                default:                  return new Color(70, 70, 70);
            }
        }
    }
}


