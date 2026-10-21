import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

/**
 * Panel with a background image that covers the whole area (no stretching),
 * plus a translucent dark overlay so white text stays readable.
 */
public class BackgroundPanel extends JPanel {

    private final BufferedImage image;
    private final int overlayAlpha;      // 0 = no dark overlay, 255 = solid black
    private BufferedImage cache;

    public BackgroundPanel(String fileName) {
        this(fileName, 0);
    }

    public BackgroundPanel(String fileName, int overlayAlpha) {
        this.image = load(fileName);
        this.overlayAlpha = overlayAlpha;
        setLayout(new BorderLayout());
    }

    private static BufferedImage load(String name) {
        try {
            URL url = BackgroundPanel.class.getResource("/" + name);
            if (url != null) return ImageIO.read(url);

            String[] folders = {"", "src/", "images/", "resources/", "src/main/resources/"};
            for (String folder : folders) {
                File f = new File(folder + name);
                if (f.isFile()) return ImageIO.read(f);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.err.println("Image '" + name + "' not found. "
                + "Put it in the project folder (next to the src folder).");
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        if (image != null) {
            if (cache == null || cache.getWidth() != w || cache.getHeight() != h) {
                cache = scaleToCover(w, h);
            }
            g2.drawImage(cache, 0, 0, null);
        } else {
            g2.setPaint(new GradientPaint(0, 0, new Color(0x2B, 0x3A, 0x67),
                    0, h, new Color(0xD8, 0x4E, 0x55)));
            g2.fillRect(0, 0, w, h);
        }
        if (overlayAlpha > 0) {
            g2.setColor(new Color(0, 0, 0, overlayAlpha));
            g2.fillRect(0, 0, w, h);
        }
        g2.dispose();
    }

    private BufferedImage scaleToCover(int w, int h) {
        double s = Math.max((double) w / image.getWidth(), (double) h / image.getHeight());
        int dw = (int) Math.ceil(image.getWidth() * s);
        int dh = (int) Math.ceil(image.getHeight() * s);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(image, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        g2.dispose();
        return out;
    }
}