import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Collections;
import java.util.Locale;

/** Colors, fonts and small components shared by all screens. */
public final class Theme {

    public static final Color RED       = new Color(0xD8, 0x4E, 0x55);
    public static final Color RED_DARK  = new Color(0xC0, 0x39, 0x41);
    public static final Color RED_SOFT  = new Color(0xFD, 0xE4, 0xE4);
    public static final Color TEXT      = new Color(0x22, 0x22, 0x22);
    public static final Color MUTED     = new Color(0x6B, 0x6B, 0x6B);
    public static final Color BORDER    = new Color(0xCF, 0xCF, 0xCF);
    public static final Color ROW_ALT   = new Color(0xFA, 0xFA, 0xFA);

    private Theme() {}

    public static Font plain(float size) { return new Font("SansSerif", Font.PLAIN, 1).deriveFont(size); }
    public static Font bold(float size)  { return new Font("SansSerif", Font.BOLD, 1).deriveFont(size); }

    /** Call once from Main so every dialog/component uses the same font. */
    public static void install() {
        FontUIResource f = new FontUIResource(plain(14));
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (key instanceof String && ((String) key).endsWith(".font")) {
                UIManager.put(key, f);
            }
        }
    }

    public static String money(double value) {
        return String.format(Locale.US, "Rs %,.0f", value);
    }

    // ------------------------------------------------------------------ buttons

    public static class RoundButton extends JButton {
        private Color bg, hover, border;

        public RoundButton(String text, Color bg, Color hover, Color fg) {
            super(text);
            setColors(bg, hover, fg);
            setFont(bold(14));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        }

        public void setColors(Color bg, Color hover, Color fg) {
            this.bg = bg;
            this.hover = hover;
            setForeground(fg);
            repaint();
        }

        public void setBorderColor(Color c) { this.border = c; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = getModel().isPressed() ? hover.darker()
                    : getModel().isRollover() ? hover : bg;
            int w = getWidth(), h = getHeight();
            g2.setColor(c);
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Large red button (Login, Search Buses, Book Ticket). */
    public static RoundButton primary(String text) {
        return new RoundButton(text, RED, RED_DARK, Color.WHITE);
    }

    /** Pink pill button (Today / Tomorrow). */
    public static RoundButton pill(String text) {
        RoundButton b = new RoundButton(text, RED_SOFT, new Color(0xF8, 0xCD, 0xCD), RED_DARK);
        b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        return b;
    }

    /** White button with a red outline (Logout, Cancel Ticket). */
    public static RoundButton outline(String text) {
        RoundButton b = new RoundButton(text, Color.WHITE, RED_SOFT, RED_DARK);
        b.setBorderColor(RED);
        return b;
    }

    /** Button that looks like a text link. */
    public static JButton link(String text) {
        JButton b = new JButton(text);
        b.setFont(bold(14));
        b.setForeground(RED);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ------------------------------------------------------------------ inputs

    /** Input box with a small caption on top (like the From / To fields in the reference). */
    public static JPanel box(String caption, JComponent field) {
        JLabel cap = new JLabel(caption);
        cap.setFont(plain(12));
        cap.setForeground(MUTED);

        if (field instanceof JTextField) {
            field.setBorder(null);
            field.setOpaque(false);
            field.setFont(plain(16));
            field.setForeground(TEXT);
        }

        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBackground(Color.WHITE);
        p.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)));
        p.add(cap, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    /** Text field that shows a gray hint while empty. */
    public static class HintField extends JTextField {
        private final String hint;

        public HintField(String hint) { this.hint = hint; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(getFont());
                g2.setColor(new Color(0x9A, 0x9A, 0x9A));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, getInsets().left,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }
}