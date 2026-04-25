package forge.screens.home.replay;

import forge.ImageCache;
import forge.StaticData;
import forge.game.ReplayStateReconstructor.BattlefieldCardInfo;
import forge.item.PaperCard;

import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Lightweight card panel for the Replay / Game Learning viewer.
 * <p>
 * Loads and renders the card image with rounded corners and a black border.
 * Falls back to an MTG-frame-styled card with coloured border and name
 * when no image is available.
 */
public class ReplayCardPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Standard MTG card aspect ratio (3.5 / 2.5). */
    public static final float ASPECT_RATIO = 3.5f / 2.5f;

    private static final float ROUNDED_CORNER_SIZE = 0.1f;
    private static final float BLACK_BORDER_SIZE = 0.03f;

    private final String cardName;
    private final String cardType;
    private final Color frameColor;

    private volatile BufferedImage image;

    // Dimensions set by the layout manager (ReplayPlayArea)
    private int cardW, cardH;

    public ReplayCardPanel(BattlefieldCardInfo card) {
        this.cardName = card.name;
        this.cardType = card.type;
        this.frameColor = getFrameColor(card.type);
        setOpaque(false);
        setToolTipText(card.name + (card.type.isEmpty() ? "" : " \u2014 " + card.type));
    }

    /** Returns the card name this panel displays. */
    public String getCardName() {
        return cardName;
    }

    /** Returns the card type string. */
    public String getCardType() {
        return cardType;
    }

    /**
     * Set the card bounds within the parent container.
     * Called by {@link ReplayPlayArea} during layout.
     */
    public void setCardBounds(int x, int y, int w, int h) {
        this.cardW = w;
        this.cardH = h;
        setBounds(x, y, w, h);
        setPreferredSize(new Dimension(w, h));
        loadImageIfNeeded();
    }

    private boolean imageLoadStarted = false;

    private void loadImageIfNeeded() {
        if (imageLoadStarted || cardW <= 0 || cardH <= 0) return;
        imageLoadStarted = true;
        final int tw = cardW;
        final int th = cardH;
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                try {
                    PaperCard pc = null;

                    if (StaticData.instance() != null && StaticData.instance().getCommonCards() != null) {
                        pc = StaticData.instance().getCommonCards().getCard(cardName);
                    }

                    if (pc != null) {
                        BufferedImage img = ImageCache.getImage(pc, tw, th);
                        if (img != null) {
                            return img;
                        }
                        // Try original (unscaled) image as fallback
                        img = ImageCache.getOriginalImage(pc.getImageKey(false), false, null);
                        if (img != null) {
                            return img;
                        }
                    }
                } catch (Exception e) {
                    // Silently fall back to frame rendering
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    BufferedImage img = get();
                    if (img != null) {
                        image = img;
                        repaint();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) { g2.dispose(); return; }

        int cornerSize = Math.max(4, Math.round(w * ROUNDED_CORNER_SIZE));
        int borderSize = Math.max(1, Math.round(w * BLACK_BORDER_SIZE));

        if (image != null) {
            // Black border background
            g2.setColor(Color.BLACK);
            g2.fillRoundRect(0, 0, w, h, cornerSize, cornerSize);

            // Draw card image clipped to rounded rect (inside the border)
            int imgX = borderSize;
            int imgY = borderSize;
            int imgW = w - borderSize * 2;
            int imgH = h - borderSize * 2;
            int imgCorner = Math.max(2, cornerSize - borderSize);

            g2.setClip(new RoundRectangle2D.Float(imgX, imgY, imgW, imgH, imgCorner, imgCorner));
            g2.drawImage(image, imgX, imgY, imgW, imgH, this);
            g2.setClip(null);

            // Subtle outer border line
            g2.setColor(new Color(0, 0, 0, 100));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, cornerSize, cornerSize);
        } else {
            // --- Fallback: MTG-frame-styled card ---
            paintCardFrame(g2, w, h, cornerSize, borderSize);
        }
        g2.dispose();
    }

    /** Paint an MTG-style card frame when no image is available. */
    private void paintCardFrame(Graphics2D g2, int w, int h, int cornerSize, int borderSize) {
        // Outer black border
        g2.setColor(Color.BLACK);
        g2.fillRoundRect(0, 0, w, h, cornerSize, cornerSize);

        // Frame colour border (thicker)
        int frameBorder = Math.max(2, w / 12);
        g2.setColor(frameColor);
        g2.fillRoundRect(borderSize, borderSize,
                w - borderSize * 2, h - borderSize * 2,
                cornerSize - 1, cornerSize - 1);

        // Inner card area with gradient
        int inner = borderSize + frameBorder;
        int innerW = w - inner * 2;
        int innerH = h - inner * 2;
        if (innerW > 0 && innerH > 0) {
            Color bgTop = darken(frameColor, 0.35f);
            Color bgBot = darken(frameColor, 0.55f);
            GradientPaint gp = new GradientPaint(0, inner, bgTop, 0, h - inner, bgBot);
            g2.setPaint(gp);
            g2.fillRoundRect(inner, inner, innerW, innerH,
                    Math.max(2, cornerSize / 2), Math.max(2, cornerSize / 2));
        }

        // --- Name banner at top ---
        int bannerH = Math.max(10, h / 5);
        int bannerY = inner;
        if (innerW > 0 && bannerH > 0) {
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRect(inner, bannerY, innerW, bannerH);

            // Card name text
            g2.setColor(Color.WHITE);
            float fontSize = Math.max(7f, Math.min(11f, w * 0.15f));
            Font f = getFont().deriveFont(Font.BOLD, fontSize);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics(f);
            drawWrappedName(g2, fm, cardName, inner + 3, bannerY + fm.getAscent() + 1, innerW - 6);
        }

        // --- Type line at bottom ---
        if (cardType != null && !cardType.isEmpty() && innerW > 0) {
            int typeH = Math.max(8, h / 8);
            int typeY = h - inner - typeH;
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRect(inner, typeY, innerW, typeH);

            g2.setColor(new Color(200, 200, 200));
            float typeFontSize = Math.max(6f, Math.min(9f, w * 0.11f));
            Font tf = getFont().deriveFont(Font.PLAIN, typeFontSize);
            g2.setFont(tf);
            FontMetrics tfm = g2.getFontMetrics(tf);

            // Truncate type line if too long
            String displayType = cardType;
            if (tfm.stringWidth(displayType) > innerW - 4) {
                while (displayType.length() > 3 && tfm.stringWidth(displayType + "...") > innerW - 4) {
                    displayType = displayType.substring(0, displayType.length() - 1);
                }
                displayType += "...";
            }
            g2.drawString(displayType, inner + 2, typeY + tfm.getAscent() + 1);
        }

        // Subtle outer highlight
        g2.setColor(new Color(255, 255, 255, 20));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(1, 1, w - 3, h - 3, cornerSize - 1, cornerSize - 1);
    }

    /** Darken a color by a factor (0.0 = unchanged, 1.0 = black). */
    private static Color darken(Color c, float factor) {
        float inv = 1f - factor;
        return new Color(
                Math.max(0, Math.round(c.getRed() * inv)),
                Math.max(0, Math.round(c.getGreen() * inv)),
                Math.max(0, Math.round(c.getBlue() * inv)));
    }

    /** Get the MTG frame colour based on card type. */
    private static Color getFrameColor(String type) {
        if (type == null) return new Color(90, 90, 100);
        String t = type.toLowerCase();
        if (t.contains("land"))         return new Color(170, 130, 60);  // brown/gold
        if (t.contains("creature"))     return new Color(60, 160, 80);  // green
        if (t.contains("artifact"))     return new Color(160, 170, 180); // silver
        if (t.contains("enchantment"))  return new Color(120, 80, 200); // purple
        if (t.contains("planeswalker")) return new Color(220, 150, 40); // gold
        if (t.contains("instant"))      return new Color(60, 120, 200); // blue
        if (t.contains("sorcery"))      return new Color(180, 50, 50);  // red
        return new Color(100, 120, 160);
    }

    /** Draw card name wrapped to fit within {@code maxW} pixels, anchored at top-left. */
    private static void drawWrappedName(Graphics2D g2, FontMetrics fm,
                                         String name, int x, int yTop, int maxW) {
        if (name == null || name.isEmpty()) return;
        String[] words = name.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String test = current.length() == 0 ? word : current + " " + word;
            if (fm.stringWidth(test) <= maxW) {
                if (current.length() > 0) current.append(' ');
                current.append(word);
            } else {
                if (current.length() > 0) lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        if (lines.size() > 3) lines = lines.subList(0, 3);
        int lineH = fm.getHeight();
        int y = yTop;
        for (String line : lines) {
            g2.drawString(line, x, y);
            y += lineH;
        }
    }

    // --- Type classification (used by ReplayPlayArea for row grouping) ---

    public boolean isLand() {
        return cardType != null && cardType.toLowerCase().contains("land")
                && !isCreature(); // Creature Lands go in creature row
    }

    public boolean isCreature() {
        return cardType != null && cardType.toLowerCase().contains("creature");
    }
}


