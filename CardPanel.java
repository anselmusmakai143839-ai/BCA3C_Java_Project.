import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** White panel with rounded corners and a soft shadow (the search card in the reference). */
public class CardPanel extends JPanel {

    private final int arc;
    private final Color fill;

    public CardPanel(LayoutManager layout) {
        this(layout, 24, Color.WHITE);
    }

    public CardPanel(LayoutManager layout, int arc, Color fill) {
        super(layout);
        this.arc = arc;
        this.fill = fill;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 20, 20));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        // shadow
        for (int i = 4; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 12));
            g2.fill(new RoundRectangle2D.Float(4 - i, 5 - i, w - 8 + 2 * i, h - 8 + 2 * i, arc + i, arc + i));
        }
        // card
        g2.setColor(fill);
        g2.fill(new RoundRectangle2D.Float(4, 2, w - 8, h - 8, arc, arc));
        g2.dispose();
        super.paintComponent(g);
    }
}