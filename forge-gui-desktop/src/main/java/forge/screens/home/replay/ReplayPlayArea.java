package forge.screens.home.replay;

import forge.game.ReplayStateReconstructor.BattlefieldCardInfo;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Simplified version of {@code forge.view.arcane.PlayArea} for the Replay viewer.
 * <p>
 * Arranges {@link ReplayCardPanel} instances in rows, grouped by card type:
 * <ul>
 *   <li>Row 1: Lands (stacked by same name, max 5 per stack)</li>
 *   <li>Row 2: Creatures (unstacked, one per slot)</li>
 *   <li>Row 3: Others (Artifacts, Enchantments, Planeswalkers, etc.)</li>
 * </ul>
 * <p>
 * Supports {@code mirror} mode: when true (opponent), lands are at the top
 * and creatures at the bottom. When false (human), creatures on top, lands at bottom.
 * <p>
 * Adaptive card width: binary-searches for the largest card width (between min and max)
 * that allows all cards to fit within the available panel area.
 * <p>
 * No CMatchUI dependency, no interaction, no animation.
 */
public class ReplayPlayArea extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final int GUTTER_X = 5;
    private static final int GUTTER_Y = 4;
    private static final float EXTRA_CARD_SPACING_X = 0.04f;
    private static final float CARD_SPACING_Y = 0.06f;
    private static final float STACK_SPACING_X = 0.12f;
    private static final float STACK_SPACING_Y = 0.12f;
    private static final int STACK_MAX_LANDS = 5;
    private static final int STACK_MAX_OTHERS = 4;

    private static final int CARD_WIDTH_MIN = 36;
    private static final int CARD_WIDTH_MAX = 120;

    private final boolean mirror;
    private final List<ReplayCardPanel> cardPanels = new ArrayList<>();

    // Computed during layout
    private int cardWidth, cardHeight;
    private int extraCardSpacingX, cardSpacingX, cardSpacingY;
    private int stackSpacingX, stackSpacingY;

    public ReplayPlayArea(boolean mirror) {
        this.mirror = mirror;
        setLayout(null); // absolute positioning
        setOpaque(false);
    }

    /**
     * Replace all displayed cards with the given list.
     */
    public void setCards(List<BattlefieldCardInfo> cards) {
        removeAll();
        cardPanels.clear();

        if (cards != null) {
            for (BattlefieldCardInfo card : cards) {
                ReplayCardPanel panel = new ReplayCardPanel(card);
                cardPanels.add(panel);
                add(panel);
            }
        }

        revalidate();
        repaint();
    }

    @Override
    public void doLayout() {
        if (cardPanels.isEmpty()) {
            setPreferredSize(new Dimension(10, 10));
            return;
        }

        Rectangle visible = getBounds();
        int areaWidth = visible.width > 0 ? visible.width : (getParent() != null ? getParent().getWidth() : 400);
        int areaHeight = visible.height > 0 ? visible.height : (getParent() != null ? getParent().getHeight() : 200);

        // Collect panels into type groups
        List<ReplayCardPanel> remaining = new ArrayList<>(cardPanels);

        // Binary search for best card width
        int minW = CARD_WIDTH_MIN;
        int maxW = Math.min(CARD_WIDTH_MAX, areaWidth / 3);
        if (maxW < minW) maxW = minW;

        int lastGoodWidth = minW;
        List<Row> lastGoodTemplate = null;

        setCardWidth(minW);
        List<Row> minTemplate = tryArrange(remaining, areaWidth, areaHeight);
        if (minTemplate != null) {
            lastGoodTemplate = minTemplate;
        }

        int lo = minW;
        int hi = maxW;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            setCardWidth(mid);
            List<Row> template = tryArrange(remaining, areaWidth, areaHeight);
            if (template != null) {
                lastGoodWidth = mid;
                lastGoodTemplate = template;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }

        setCardWidth(lastGoodWidth);
        if (lastGoodTemplate == null) {
            lastGoodTemplate = tryArrange(remaining, areaWidth, areaHeight);
        }
        if (lastGoodTemplate == null) {
            // Absolute fallback: one row with everything
            lastGoodTemplate = new ArrayList<>();
            Row fallback = new Row();
            for (ReplayCardPanel p : remaining) {
                CardStack s = new CardStack();
                s.panels.add(p);
                fallback.stacks.add(s);
            }
            lastGoodTemplate.add(fallback);
        }

        positionCards(lastGoodTemplate);
    }

    // --- Card width / derived metrics ---

    private void setCardWidth(int w) {
        this.cardWidth = w;
        this.cardHeight = Math.round(w * ReplayCardPanel.ASPECT_RATIO);
        this.extraCardSpacingX = Math.round(w * EXTRA_CARD_SPACING_X);
        this.cardSpacingX = (cardHeight - cardWidth) + extraCardSpacingX;
        this.cardSpacingY = Math.round(cardHeight * CARD_SPACING_Y);
        this.stackSpacingX = Math.round(cardWidth * STACK_SPACING_X);
        this.stackSpacingY = Math.round(cardHeight * STACK_SPACING_Y);
    }

    // --- Arrangement ---

    private List<Row> tryArrange(List<ReplayCardPanel> all, int areaW, int areaH) {
        List<ReplayCardPanel> remaining = new ArrayList<>(all);

        // Collect by type
        List<CardStack> landStacks = collectLands(remaining);
        List<CardStack> creatureStacks = collectUnstacked(remaining, true);
        List<CardStack> otherStacks = collectOthers(remaining);

        // Build rows
        List<Row> template = new ArrayList<>();
        boolean allFit;
        if (mirror) {
            // Opponent: lands top, creatures bottom
            allFit = planRow(landStacks, template, areaW, areaH);
            allFit &= planRow(otherStacks, template, areaW, areaH);
            allFit &= planRow(creatureStacks, template, areaW, areaH);
        } else {
            // Human: creatures top, lands bottom
            allFit = planRow(creatureStacks, template, areaW, areaH);
            allFit &= planRow(otherStacks, template, areaW, areaH);
            allFit &= planRow(landStacks, template, areaW, areaH);
        }

        if (!allFit && cardWidth > CARD_WIDTH_MIN) {
            return null;
        }
        return template;
    }

    /** Collect lands, stacking same-named ones (max STACK_MAX_LANDS). */
    private List<CardStack> collectLands(List<ReplayCardPanel> remaining) {
        List<CardStack> stacks = new ArrayList<>();
        for (Iterator<ReplayCardPanel> it = remaining.iterator(); it.hasNext(); ) {
            ReplayCardPanel panel = it.next();
            if (!panel.isLand()) continue;
            it.remove();

            // Try to find existing stack with same name
            boolean added = false;
            for (CardStack stack : stacks) {
                if (stack.panels.get(0).getCardName().equals(panel.getCardName())
                        && stack.panels.size() < STACK_MAX_LANDS) {
                    stack.panels.add(panel);
                    added = true;
                    break;
                }
            }
            if (!added) {
                CardStack s = new CardStack();
                s.panels.add(panel);
                stacks.add(s);
            }
        }
        return stacks;
    }

    /** Collect creatures (or non-creatures if isCreature=false), one per stack. */
    private List<CardStack> collectUnstacked(List<ReplayCardPanel> remaining, boolean isCreature) {
        List<CardStack> stacks = new ArrayList<>();
        for (Iterator<ReplayCardPanel> it = remaining.iterator(); it.hasNext(); ) {
            ReplayCardPanel panel = it.next();
            if (isCreature != panel.isCreature()) continue;
            it.remove();
            CardStack s = new CardStack();
            s.panels.add(panel);
            stacks.add(s);
        }
        return stacks;
    }

    /** Collect remaining (others), stacking same-named ones (max STACK_MAX_OTHERS). */
    private List<CardStack> collectOthers(List<ReplayCardPanel> remaining) {
        List<CardStack> stacks = new ArrayList<>();
        for (ReplayCardPanel panel : remaining) {
            boolean added = false;
            for (CardStack stack : stacks) {
                if (stack.panels.get(0).getCardName().equals(panel.getCardName())
                        && stack.panels.size() < STACK_MAX_OTHERS) {
                    stack.panels.add(panel);
                    added = true;
                    break;
                }
            }
            if (!added) {
                CardStack s = new CardStack();
                s.panels.add(panel);
                stacks.add(s);
            }
        }
        remaining.clear();
        return stacks;
    }

    /** Try to place all stacks from source into rows. Returns false if they don't fit. */
    private boolean planRow(List<CardStack> source, List<Row> template, int areaW, int areaH) {
        if (source.isEmpty()) return true;

        boolean isMinimal = (cardWidth == CARD_WIDTH_MIN);
        Row currentRow = new Row();

        for (CardStack stack : source) {
            int rowWidth = currentRow.getWidth();
            int stackWidth = stack.getWidth();

            if (rowWidth + stackWidth > areaW && !currentRow.stacks.isEmpty()) {
                // Check if adding another row exceeds height
                if (!isMinimal && getTemplateHeight(template) + currentRow.getHeight() > areaH) {
                    return false;
                }
                template.add(currentRow);
                currentRow = new Row();
            }
            currentRow.stacks.add(stack);
        }

        if (!currentRow.stacks.isEmpty()) {
            if (!isMinimal) {
                int totalH = getTemplateHeight(template) + currentRow.getHeight();
                if (totalH > areaH) return false;
            }
            template.add(currentRow);
        }
        return true;
    }

    private int getTemplateHeight(List<Row> template) {
        int h = 0;
        for (Row row : template) {
            h += row.getHeight();
        }
        return h + GUTTER_Y * 2;
    }

    // --- Positioning ---

    private void positionCards(List<Row> template) {
        int y = GUTTER_Y;
        int maxRowWidth = 0;

        for (Row row : template) {
            int x = GUTTER_X;
            int rowBottom = y;
            for (CardStack stack : row.stacks) {
                for (int i = 0; i < stack.panels.size(); i++) {
                    ReplayCardPanel panel = stack.panels.get(i);
                    int stackOffset = (stack.panels.size() - 1 - i);
                    int px = x + stackOffset * stackSpacingX;
                    int py = y + stackOffset * stackSpacingY;
                    panel.setCardBounds(px, py, cardWidth, cardHeight);
                    setComponentZOrder(panel, i);
                }
                rowBottom = Math.max(rowBottom, y + stack.getHeight());
                x += stack.getWidth();
            }
            maxRowWidth = Math.max(maxRowWidth, x);
            y = rowBottom;
        }

        setPreferredSize(new Dimension(
                Math.max(10, maxRowWidth),
                Math.max(10, y + GUTTER_Y)));
    }

    // --- Inner: row and stack structures ---

    private class Row {
        final List<CardStack> stacks = new ArrayList<>();

        int getWidth() {
            if (stacks.isEmpty()) return 0;
            int w = 0;
            for (CardStack s : stacks) w += s.getWidth();
            return w + GUTTER_X * 2 - extraCardSpacingX;
        }

        int getHeight() {
            if (stacks.isEmpty()) return 0;
            int h = 0;
            for (CardStack s : stacks) h = Math.max(h, s.getHeight());
            return h;
        }
    }

    private class CardStack {
        final List<ReplayCardPanel> panels = new ArrayList<>();

        int getWidth() {
            return cardWidth + (panels.size() - 1) * stackSpacingX + cardSpacingX;
        }

        int getHeight() {
            return cardHeight + (panels.size() - 1) * stackSpacingY + cardSpacingY;
        }
    }
}



