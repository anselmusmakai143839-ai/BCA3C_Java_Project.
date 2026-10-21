import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.awt.print.Printable;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class DashboardFrame extends JFrame {

    // {boarding point name, city (display), city (search keyword)}
    private static final String[][] BOARDING_POINTS = {
            {"Shastri Maidan",     "Rajkot (Gujarat)",       "Rajkot"},
            {"Green Land Chokdi",  "Rajkot (Gujarat)",       "Rajkot"},
            {"Bigbajar",           "Rajkot (Gujarat)",       "Rajkot"},
            {"Limda Chowk",        "Rajkot (Gujarat)",       "Rajkot"},
            {"Paldi Bus Stop",     "Ahmedabad (Gujarat)",    "Ahmedabad"},
            {"Kamrej Cross Road",  "Surat (Gujarat)",        "Surat"},
            {"Dadar",              "Mumbai (Maharashtra)",   "Mumbai"}
    };

    // {destination name, description, search keyword}
    private static final String[][] DESTINATIONS = {
            {"Ahmedabad", "Ahmedabad (Gujarat)",    "Ahmedabad"},
            {"Mumbai",    "Mumbai (Maharashtra)",   "Mumbai"},
            {"Surat",     "Surat (Gujarat)",        "Surat"},
            {"Pune",      "Pune (Maharashtra)",     "Pune"},
            {"Rajkot",    "Rajkot (Gujarat)",       "Rajkot"},
            {"Vadodara",  "Vadodara (Gujarat)",     "Vadodara"},
            {"Jamnagar",  "Jamnagar (Gujarat)",     "Jamnagar"}
    };

    private static final Color SEG_BASE  = new Color(0xF3, 0xF4, 0xF4);
    private static final Color SEG_HOVER = new Color(0xE6, 0xE8, 0xE8);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final int userId;
    private final String userName;
    private final String displayName;   // name shown on screen, e.g. "Ansel Makai"

    // ---- profile photo (circle image) and the navbar button that shows it
    private BufferedImage avatarImage;
    private AccountButton accountButton;

    // ---- search bar
    private final Theme.HintField fromField = new Theme.HintField("Leaving from");
    private final Theme.HintField toField = new Theme.HintField("Going to");
    private LocalDate travelDate = LocalDate.now();
    private final JLabel dateLabel = new JLabel();
    private final JLabel todayLabel = new JLabel("Today", JLabel.CENTER);
    private final JLabel tomorrowLabel = new JLabel("Tomorrow", JLabel.CENTER);
    private Segment todaySeg, tomorrowSeg;
    private final JLabel searchError = new JLabel(" ");

    // ---- results and filters
    private final CardLayout bodyCards = new CardLayout();
    private final JPanel body = new JPanel(bodyCards);
    private boolean resultsShown = false;
    private final java.util.List<Object[]> allBuses = new ArrayList<>();
    private final JLabel resultLabel = new JLabel(" ");
    private final JSpinner seatSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final java.util.List<ToggleChip> typeChips = new ArrayList<>();   // AC, Non AC, Sleeper, Seater
    private final java.util.List<ToggleChip> timeChips = new ArrayList<>();   // 4 departure time ranges
    private final RangeSlider priceSlider = new RangeSlider(() -> onFilterChanged());
    private final JLabel priceLowLabel = new JLabel(" ");
    private final JLabel priceHighLabel = new JLabel(" ");
    private final JComboBox<String> partnerBox = new JComboBox<>();
    private final JComboBox<String> boardingBox = new JComboBox<>();
    private boolean updatingFilters = false;

    // model columns 0-7 are the original ones; Type and Boarding Point are added at the end
    private final DefaultTableModel busModel = new DefaultTableModel(
            new String[]{"ID", "Bus Name", "From", "To", "Date", "Departure", "Price", "Seats Left",
                    "Type", "Boarding Point"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    private final DefaultTableModel bookingModel = new DefaultTableModel(
            new String[]{"Ticket No.", "Bus", "Route", "Date", "Time", "Seats", "Total", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    private final JTable busTable = new JTable(busModel);
    private final JTable bookingTable = new JTable(bookingModel);

    private final CardLayout pages = new CardLayout();
    private final JPanel pageHolder = new JPanel(pages);
    private final TabButton searchTab = new TabButton("Bus Tickets");
    private final TabButton ticketsTab = new TabButton("My Tickets");
    private final StatChip totalChip = new StatChip("Total");
    private final StatChip bookedChip = new StatChip("Booked");
    private final StatChip cancelledChip = new StatChip("Cancelled");
    private final java.util.List<JPopupMenu> popups = new ArrayList<>();

    public DashboardFrame(int userId, String userName) {
        this.userId = userId;
        this.userName = userName;
        this.displayName = toTitleCase(userName);
        loadAvatar();

        setTitle("Bus Ticket");
        setSize(1180, 780);
        setMinimumSize(new Dimension(1000, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        BackgroundPanel background = new BackgroundPanel("bus-station.png", 110);
        background.setLayout(new BorderLayout());

        pageHolder.setOpaque(false);
        pageHolder.add(buildSearchPage(), "search");
        pageHolder.add(buildBookingsPage(), "tickets");
        background.add(pageHolder, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildNavBar(userName), BorderLayout.NORTH);
        root.add(background, BorderLayout.CENTER);
        setContentPane(root);

        showPage("search");
        syncPills();
    }

    // =================================================================== NAVBAR

    private JComponent buildNavBar(String userName) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setPreferredSize(new Dimension(10, 64));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE5E5E5)),
                BorderFactory.createEmptyBorder(0, 28, 0, 28)));

        JLabel brand = new JLabel("BusTiket");
        brand.setFont(Theme.bold(24));
        brand.setForeground(Theme.RED);

        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.fill = GridBagConstraints.VERTICAL;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 22);
        left.add(brand, gc);
        left.add(searchTab, gc);
        left.add(ticketsTab, gc);

        JLabel hello = new JLabel("Hello, " + displayName);
        hello.setFont(Theme.plain(14));
        hello.setForeground(Theme.TEXT);

        // "My Account" button: click it and a menu opens with My Account on top and Logout below it
        accountButton = new AccountButton();
        JButton account = accountButton;
        installAccountMenu(account);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        GridBagConstraints rc = new GridBagConstraints();
        rc.insets = new Insets(0, 0, 0, 14);
        right.add(hello, rc);
        rc.insets = new Insets(0, 0, 0, 0);
        right.add(account, rc);

        searchTab.addActionListener(e -> {
            if (searchTab.active) resetSearch();   // clicking the tab again = back to the first screen
            showPage("search");
        });
        ticketsTab.addActionListener(e -> showPage("tickets"));

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ---- account dropdown (My Account + Logout)
    private void installAccountMenu(JComponent anchor) {
        JPopupMenu menu = new JPopupMenu();
        popups.add(menu);
        menu.setBorder(BorderFactory.createLineBorder(new Color(0xDDDDDD)));
        menu.setBackground(Color.WHITE);

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        box.add(menuRow("My Account", Theme.TEXT, menu, this::showMyAccount));

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(0xEEEEEE));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        box.add(sep);

        box.add(menuRow("Logout", Theme.RED, menu, () -> {
            new LoginFrame().setVisible(true);
            dispose();
        }));
        menu.add(box);

        // clicking the button while the menu is open should close it (not reopen it)
        long[] lastHidden = {0};
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                lastHidden[0] = System.currentTimeMillis();
            }
            @Override public void popupMenuCanceled(PopupMenuEvent e) {
                lastHidden[0] = System.currentTimeMillis();
            }
        });

        ((AbstractButton) anchor).addActionListener(e -> {
            if (System.currentTimeMillis() - lastHidden[0] < 250) return;
            int w = Math.max(anchor.getWidth(), 190);
            menu.setPopupSize(w, menu.getPreferredSize().height);
            menu.show(anchor, anchor.getWidth() - w, anchor.getHeight() + 6);   // right edges aligned
        });
    }

    private JComponent menuRow(String text, Color fg, JPopupMenu menu, Runnable action) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createEmptyBorder(11, 18, 11, 18));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel(text);
        label.setFont(Theme.plain(15));
        label.setForeground(fg);
        row.add(label, BorderLayout.CENTER);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(Theme.RED_SOFT); }
            @Override public void mouseExited(MouseEvent e)  { row.setBackground(Color.WHITE); }
            @Override public void mouseReleased(MouseEvent e) {
                if (row.contains(e.getPoint())) {
                    menu.setVisible(false);
                    action.run();
                }
            }
        });
        return row;
    }

    /** "My Account" window: only the profile photo, the name below it, and a three-dots menu (Change Photo / Delete Photo). */
    private void showMyAccount() {
        JDialog dlg = new JDialog(this, "My Account", true);

        // big round profile photo (or first-name letter)
        JComponent avatarView = new JComponent() {
            { setPreferredSize(new Dimension(124, 124)); }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                paintAvatar(g2, 2, 2, 120, avatarImage, userName);
                g2.dispose();
            }
        };
        avatarView.setAlignmentX(Component.CENTER_ALIGNMENT);

        // name under the photo
        JLabel nameLabel = new JLabel(displayName, JLabel.CENTER);
        nameLabel.setFont(Theme.bold(20));
        nameLabel.setForeground(Theme.TEXT);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // three dots under the name
        JButton dots = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(0xEE, 0xEE, 0xEE));
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                }
                g2.setColor(Theme.TEXT);
                int cy = getHeight() / 2, cx = getWidth() / 2;
                for (int dx = -10; dx <= 10; dx += 10) g2.fillOval(cx + dx - 2, cy - 2, 5, 5);
                g2.dispose();
            }
        };
        dots.setPreferredSize(new Dimension(54, 28));
        dots.setMaximumSize(new Dimension(54, 28));
        dots.setContentAreaFilled(false);
        dots.setBorderPainted(false);
        dots.setFocusPainted(false);
        dots.setRolloverEnabled(true);
        dots.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dots.setAlignmentX(Component.CENTER_ALIGNMENT);

        // menu inside the three dots
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(new Color(0xDDDDDD)));
        menu.setBackground(Color.WHITE);
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        box.add(menuRow("Change Photo", Theme.TEXT, menu, () -> {
            chooseProfilePhoto();
            avatarView.repaint();
        }));
        box.add(menuRow("Delete Photo", Theme.RED, menu, () -> {
            removeProfilePhoto();
            avatarView.repaint();
        }));
        menu.add(box);

        dots.addActionListener(e -> {
            int w = 180;
            menu.setPopupSize(w, menu.getPreferredSize().height);
            menu.show(dots, (dots.getWidth() - w) / 2, dots.getHeight() + 4);
        });

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(30, 70, 24, 70));
        root.add(avatarView);
        root.add(Box.createVerticalStrut(12));
        root.add(nameLabel);
        root.add(Box.createVerticalStrut(6));
        root.add(dots);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ============================================================ PROFILE PHOTO

    /** "ansel makai" / "ANSEL MAKAI" -> "Ansel Makai" (first letter of every word big, the rest small). */
    private static String toTitleCase(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static final Color[] AVATAR_COLORS = {
            new Color(0xD8, 0x4E, 0x55), new Color(0x3F, 0x72, 0xAF), new Color(0x2E, 0x8B, 0x57),
            new Color(0xE0, 0x8A, 0x1E), new Color(0x7B, 0x5E, 0xA7), new Color(0x1F, 0x9E, 0x9E)
    };

    /** Every user has a photo file here (if they chose one): ~/.bustiket/profile_<userId>.png */
    private File photoFile() {
        return new File(System.getProperty("user.home") + File.separator + ".bustiket",
                "profile_" + userId + ".png");
    }

    /** Reads the saved photo. No photo means avatarImage stays null and the first-name letter is shown. */
    private void loadAvatar() {
        avatarImage = null;
        File f = photoFile();
        if (!f.exists()) return;
        try {
            avatarImage = ImageIO.read(f);
        } catch (IOException ignored) {
            avatarImage = null;
        }
    }

    private void chooseProfilePhoto() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose your profile photo");
        fc.setFileFilter(new FileNameExtensionFilter("Images (jpg, png, gif)", "jpg", "jpeg", "png", "gif"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            BufferedImage src = ImageIO.read(fc.getSelectedFile());
            if (src == null) {
                JOptionPane.showMessageDialog(this, "That file is not a valid image.");
                return;
            }
            BufferedImage circle = toCircle(src);
            File f = photoFile();
            f.getParentFile().mkdirs();
            ImageIO.write(circle, "png", f);
            avatarImage = circle;
            if (accountButton != null) accountButton.repaint();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save photo: " + ex.getMessage());
        }
    }

    private void removeProfilePhoto() {
        photoFile().delete();
        avatarImage = null;
        if (accountButton != null) accountButton.repaint();
    }

    /** Crops the image to a centered square, scales it to 256 px and cuts it into a circle. */
    private static BufferedImage toCircle(BufferedImage src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int sx = (src.getWidth() - side) / 2, sy = (src.getHeight() - side) / 2;
        int s = 256;
        BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.fill(new Ellipse2D.Float(0, 0, s, s));
        g.setComposite(AlphaComposite.SrcIn);
        g.drawImage(src, 0, 0, s, s, sx, sy, sx + side, sy + side, null);
        g.dispose();
        return out;
    }

    /** Draws the round avatar: the photo if there is one, otherwise a colored circle with the first-name letter A-Z. */
    private static void paintAvatar(Graphics2D g2, int x, int y, int size, BufferedImage photo, String name) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        if (photo != null) {
            g2.drawImage(photo, x, y, size, size, null);
        } else {
            String letter = (name == null || name.trim().isEmpty())
                    ? "?" : name.trim().substring(0, 1).toUpperCase();
            int idx = Math.abs(letter.charAt(0)) % AVATAR_COLORS.length;
            g2.setColor(AVATAR_COLORS[idx]);
            g2.fillOval(x, y, size, size);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.bold((int) (size * 0.48)));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(letter, x + (size - fm.stringWidth(letter)) / 2,
                    y + (size - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.setColor(new Color(0, 0, 0, 35));
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(x, y, size - 1, size - 1);
    }

    /** Navbar button: round profile photo (or letter) + "My Account" + small arrow. */
    private class AccountButton extends JButton {
        AccountButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(185, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            g2.setColor(getModel().isRollover() ? Theme.RED_SOFT : Color.WHITE);
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.setColor(Theme.RED);
            g2.setStroke(new BasicStroke(1.3f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);

            int pad = 5, size = h - 2 * pad;
            paintAvatar(g2, pad, pad, size, avatarImage, userName);

            g2.setFont(Theme.bold(14));
            g2.setColor(Theme.RED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("My Account", pad + size + 10, (h - fm.getHeight()) / 2 + fm.getAscent());

            // small down arrow
            int ax = w - 26, ay = h / 2 - 2;
            g2.fillPolygon(new int[]{ax, ax + 10, ax + 5}, new int[]{ay, ay, ay + 5}, 3);
            g2.dispose();
        }
    }

    private void showPage(String name) {
        for (JPopupMenu p : popups) p.setVisible(false);
        pages.show(pageHolder, name);
        searchTab.setActive(name.equals("search"));
        ticketsTab.setActive(name.equals("tickets"));
        if (name.equals("tickets")) loadMyBookings();
    }

    // ============================================================ SEARCH PAGE

    private JComponent buildSearchPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(buildSearchStrip(), BorderLayout.NORTH);

        body.setOpaque(false);
        body.add(buildHero(), "hero");
        body.add(buildResultsView(), "results");
        bodyCards.show(body, "hero");
        page.add(body, BorderLayout.CENTER);
        return page;
    }

    /** First screen: only the search bar and the big photo. Nothing else shows until Search is pressed. */
    private JComponent buildHero() {
        JPanel hero = new JPanel();
        hero.setOpaque(false);
        return hero;
    }

    // ---- the search bar (one row, all cells have the same height)
    private JComponent buildSearchStrip() {
        Segment fromSeg = fieldSegment("From", fromField);
        Segment toSeg = fieldSegment("To", toField);

        // swap button between From and To
        Segment swapSeg = new Segment(new GridBagLayout());
        SwapButton swap = new SwapButton();
        swap.addActionListener(e -> {
            String t = fromField.getText();
            fromField.setText(toField.getText());
            toField.setText(t);
        });
        swapSeg.add(swap);

        // date cell (click = calendar)
        Segment dateSeg = new Segment(new BorderLayout(0, 2));
        dateSeg.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        dateLabel.setFont(Theme.plain(17));
        dateLabel.setForeground(Theme.TEXT);
        dateSeg.add(caption("Departure"), BorderLayout.NORTH);
        dateSeg.add(dateLabel, BorderLayout.CENTER);
        dateSeg.onClick(() -> showCalendar(dateSeg));
        updateDateLabel();

        todaySeg = chipSegment(todayLabel);
        tomorrowSeg = chipSegment(tomorrowLabel);
        todaySeg.onClick(() -> { setDate(LocalDate.now()); refreshIfShown(); });
        tomorrowSeg.onClick(() -> { setDate(LocalDate.now().plusDays(1)); refreshIfShown(); });

        SearchSegment searchSeg = new SearchSegment("Search");
        searchSeg.onClick(() -> doSearch(true));

        fromSeg.setPreferredSize(new Dimension(250, 66));
        swapSeg.setPreferredSize(new Dimension(52, 66));
        toSeg.setPreferredSize(new Dimension(250, 66));
        dateSeg.setPreferredSize(new Dimension(180, 66));
        todaySeg.setPreferredSize(new Dimension(105, 66));
        tomorrowSeg.setPreferredSize(new Dimension(105, 66));
        searchSeg.setPreferredSize(new Dimension(150, 66));

        SegmentBar bar = new SegmentBar(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;
        g.gridy = 0;
        g.insets = new Insets(0, 0, 0, 3);
        g.gridx = 0; g.weightx = 1.2; bar.add(fromSeg, g);
        g.gridx = 1; g.weightx = 0;   bar.add(swapSeg, g);
        g.gridx = 2; g.weightx = 1.2; bar.add(toSeg, g);
        g.gridx = 3; g.weightx = 0.8; bar.add(dateSeg, g);
        g.gridx = 4; g.weightx = 0.3; bar.add(todaySeg, g);
        g.gridx = 5; g.weightx = 0.3; bar.add(tomorrowSeg, g);
        g.gridx = 6; g.weightx = 0.4; g.insets = new Insets(0, 0, 0, 0); bar.add(searchSeg, g);

        searchError.setFont(Theme.plain(13));
        searchError.setForeground(new Color(0xD3, 0x2F, 0x2F));
        searchError.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        searchError.setPreferredSize(new Dimension(10, 18));

        JPanel strip = new JPanel(new BorderLayout(0, 4));
        strip.setBackground(Color.WHITE);
        strip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE5E5E5)),
                BorderFactory.createEmptyBorder(14, 28, 6, 28)));
        bar.setPreferredSize(new Dimension(10, 66));
        strip.add(bar, BorderLayout.NORTH);
        strip.add(searchError, BorderLayout.SOUTH);

        // typing clears the red message; Enter = Search
        KeyAdapter clearError = new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) { searchError.setText(" "); }
        };
        fromField.addKeyListener(clearError);
        toField.addKeyListener(clearError);
        fromField.addActionListener(e -> doSearch(true));
        toField.addActionListener(e -> doSearch(true));

        installPlacePopup(fromField, fromSeg, "Popular boarding points near you", BOARDING_POINTS, "Board at",
                () -> toField.requestFocusInWindow());
        installPlacePopup(toField, toSeg, "Popular destinations", DESTINATIONS, "Go to",
                () -> searchError.setText(" "));
        return strip;
    }

    private JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.plain(12));
        l.setForeground(Theme.MUTED);
        return l;
    }

    private Segment fieldSegment(String captionText, Theme.HintField field) {
        Segment seg = new Segment(new BorderLayout(0, 2));
        seg.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        field.setBorder(null);
        field.setOpaque(false);
        field.setFont(Theme.plain(17));
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.TEXT);
        seg.add(caption(captionText), BorderLayout.NORTH);
        seg.add(field, BorderLayout.CENTER);
        seg.onClick(() -> field.requestFocusInWindow());
        return seg;
    }

    private Segment chipSegment(JLabel label) {
        Segment seg = new Segment(new BorderLayout());
        label.setFont(Theme.plain(15));
        label.setForeground(Theme.TEXT);
        seg.add(label, BorderLayout.CENTER);
        return seg;
    }

    // ---- date handling
    private void setDate(LocalDate d) {
        travelDate = d;
        updateDateLabel();
        syncPills();
    }

    private void updateDateLabel() {
        dateLabel.setText(DATE_FMT.format(travelDate));
    }

    private void syncPills() {
        if (todaySeg == null) return;
        setChipActive(todaySeg, todayLabel, travelDate.equals(LocalDate.now()));
        setChipActive(tomorrowSeg, tomorrowLabel, travelDate.equals(LocalDate.now().plusDays(1)));
    }

    private void setChipActive(Segment seg, JLabel label, boolean active) {
        seg.setColors(active ? Theme.RED_SOFT : SEG_BASE, active ? new Color(0xF8, 0xCD, 0xCD) : SEG_HOVER);
        label.setForeground(active ? Theme.RED_DARK : Theme.TEXT);
        label.setFont(active ? Theme.bold(15) : Theme.plain(15));
    }

    private void showCalendar(JComponent anchor) {
        JPopupMenu pop = new JPopupMenu();
        pop.setBorder(BorderFactory.createLineBorder(new Color(0xDDDDDD)));

        JPanel box = new JPanel(new BorderLayout(0, 8));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        box.setPreferredSize(new Dimension(300, 290));

        YearMonth[] shown = {YearMonth.from(travelDate)};
        JLabel title = new JLabel("", JLabel.CENTER);
        title.setFont(Theme.bold(15));
        JButton prev = Theme.link("<");
        JButton next = Theme.link(">");
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(prev, BorderLayout.WEST);
        head.add(title, BorderLayout.CENTER);
        head.add(next, BorderLayout.EAST);
        JPanel grid = new JPanel(new GridLayout(0, 7, 3, 3));
        grid.setOpaque(false);
        box.add(head, BorderLayout.NORTH);
        box.add(grid, BorderLayout.CENTER);

        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            grid.removeAll();
            YearMonth ym = shown[0];
            title.setText(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear());
            for (String d : new String[]{"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"}) {
                JLabel l = new JLabel(d, JLabel.CENTER);
                l.setFont(Theme.plain(12));
                l.setForeground(Theme.MUTED);
                grid.add(l);
            }
            int offset = ym.atDay(1).getDayOfWeek().getValue() - 1;
            for (int i = 0; i < offset; i++) grid.add(new JLabel(""));
            for (int day = 1; day <= ym.lengthOfMonth(); day++) {
                LocalDate date = ym.atDay(day);
                boolean past = date.isBefore(LocalDate.now());
                boolean selected = date.equals(travelDate);
                Theme.RoundButton b = selected
                        ? new Theme.RoundButton(String.valueOf(day), Theme.RED, Theme.RED_DARK, Color.WHITE)
                        : new Theme.RoundButton(String.valueOf(day), Color.WHITE, Theme.RED_SOFT,
                        past ? new Color(0xBBBBBB) : Theme.TEXT);
                b.setFont(Theme.plain(13));
                b.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
                b.setEnabled(!past);
                b.addActionListener(e -> { setDate(date); pop.setVisible(false); });
                grid.add(b);
            }
            grid.revalidate();
            grid.repaint();
        };
        prev.addActionListener(e -> { shown[0] = shown[0].minusMonths(1); fill[0].run(); });
        next.addActionListener(e -> { shown[0] = shown[0].plusMonths(1); fill[0].run(); });
        fill[0].run();

        pop.add(box);
        pop.show(anchor, 0, anchor.getHeight() + 6);
    }

    // ---- place list popup (used by the From and To fields)
    private void installPlacePopup(JTextField field, JComponent anchor, String titleText,
                                   String[][] places, String rightText, Runnable afterPick) {
        JPopupMenu popup = new JPopupMenu();
        popups.add(popup);
        popup.setFocusable(false);
        popup.setBorder(BorderFactory.createLineBorder(new Color(0xDDDDDD)));
        popup.setBackground(Color.WHITE);

        JLabel title = new JLabel(titleText);
        title.setFont(Theme.bold(16));
        title.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        popup.add(title);

        // all places go into a list (it scrolls by itself if there are more than 7)
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Color.WHITE);
        for (String[] p : places) {
            list.add(placeRow(p, rightText, field, popup, afterPick));
        }
        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setPreferredSize(new Dimension(447, Math.min(places.length, 7) * 58));
        popup.add(scroll);

        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { showPlaces(popup, field, anchor); }
            @Override public void focusLost(FocusEvent e)   { popup.setVisible(false); }
        });
        field.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { showPlaces(popup, field, anchor); }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) { popup.setVisible(false); }
        });
    }

    private void showPlaces(JPopupMenu popup, JTextField field, JComponent anchor) {
        if (field.getText().isEmpty() && anchor.isShowing() && !popup.isVisible()) {
            popup.show(anchor, 0, anchor.getHeight() + 6);
        }
    }

    private JComponent placeRow(String[] p, String rightText, JTextField field,
                                JPopupMenu popup, Runnable afterPick) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        row.setPreferredSize(new Dimension(430, 58));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel name = new JLabel(p[0]);
        name.setFont(Theme.plain(16));
        JLabel city = new JLabel(p[1]);
        city.setFont(Theme.plain(13));
        city.setForeground(Theme.MUTED);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(name);
        text.add(city);

        JLabel right = new JLabel(rightText);
        right.setFont(Theme.plain(13));
        right.setForeground(Theme.MUTED);

        row.add(text, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { row.setBackground(Theme.RED_SOFT); }
            @Override public void mouseExited(MouseEvent e)  { row.setBackground(Color.WHITE); }
            @Override public void mouseReleased(MouseEvent e) {
                field.setText(p[2]);
                popup.setVisible(false);
                afterPick.run();
            }
        });
        return row;
    }

    // ---- results view: filters on the left, buses on the right
    private JComponent buildResultsView() {
        JPanel view = new JPanel(new BorderLayout(18, 0));
        view.setOpaque(false);
        view.setBorder(BorderFactory.createEmptyBorder(20, 28, 22, 28));
        CardPanel filters = buildFilterCard();
        filters.setPreferredSize(new Dimension(310, 10));
        view.add(filters, BorderLayout.WEST);
        view.add(buildResultCard(), BorderLayout.CENTER);
        return view;
    }

    private CardPanel buildFilterCard() {
        CardPanel card = new CardPanel(new BorderLayout(0, 6));
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 8));

        JLabel title = new JLabel("Filters");
        title.setFont(Theme.bold(20));
        JButton clear = Theme.link("Clear all");
        clear.addActionListener(e -> { resetFilters(); applyFilters(); });
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        header.add(title, BorderLayout.WEST);
        header.add(clear, BorderLayout.EAST);

        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);

        col.add(sectionTitle("Bus Type"));
        col.add(left(chipGrid(typeChips, "AC", "Non AC", "Sleeper", "Seater")));

        col.add(sectionTitle("Price Range"));
        priceSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(priceSlider);
        priceLowLabel.setFont(Theme.plain(13));
        priceHighLabel.setFont(Theme.plain(13));
        JPanel priceRow = new JPanel(new BorderLayout());
        priceRow.setOpaque(false);
        priceRow.add(priceLowLabel, BorderLayout.WEST);
        priceRow.add(priceHighLabel, BorderLayout.EAST);
        col.add(left(priceRow));

        col.add(sectionTitle("Departure Time"));
        col.add(left(chipGrid(timeChips, "Before 10 AM", "10 AM - 5 PM", "5 PM - 11 PM", "After 11 PM")));

        col.add(sectionTitle("Bus Partner"));
        col.add(left(styledCombo(partnerBox)));
        col.add(sectionTitle("Boarding Point"));
        col.add(left(styledCombo(boardingBox)));

        partnerBox.addActionListener(e -> onFilterChanged());
        boardingBox.addActionListener(e -> onFilterChanged());

        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(col, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);

        card.add(header, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(16));
        l.setBorder(BorderFactory.createEmptyBorder(12, 0, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private JPanel chipGrid(java.util.List<ToggleChip> store, String... labels) {
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
        grid.setOpaque(false);
        for (String text : labels) {
            ToggleChip chip = new ToggleChip(text);
            chip.addItemListener(e -> onFilterChanged());
            store.add(chip);
            grid.add(chip);
        }
        return grid;
    }

    private JComboBox<String> styledCombo(JComboBox<String> box) {
        box.setFont(Theme.plain(14));
        box.setBackground(Color.WHITE);
        box.setPreferredSize(new Dimension(200, 36));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return box;
    }

    private CardPanel buildResultCard() {
        styleTable(busTable);
        busTable.getColumn("Price").setCellRenderer(new RowRenderer(true));
        // hide technical / repeated columns (they stay in the model), then put the rest in a nice order
        busTable.removeColumn(busTable.getColumn("ID"));
        busTable.removeColumn(busTable.getColumn("From"));
        busTable.removeColumn(busTable.getColumn("To"));
        busTable.removeColumn(busTable.getColumn("Date"));
        busTable.moveColumn(4, 1);   // Type
        busTable.moveColumn(5, 2);   // Boarding Point
        setWidths(busTable, 160, 140, 160, 100, 80, 100);

        resultLabel.setFont(Theme.bold(16));
        resultLabel.setForeground(Theme.TEXT);

        Theme.RoundButton backBtn = Theme.outline("Back to Search");
        backBtn.addActionListener(e -> resetSearch());
        JPanel resultHeader = new JPanel(new BorderLayout());
        resultHeader.setOpaque(false);
        resultHeader.add(resultLabel, BorderLayout.WEST);
        resultHeader.add(backBtn, BorderLayout.EAST);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bottom.setOpaque(false);
        JLabel seatLabel = new JLabel("Number of seats:");
        seatLabel.setFont(Theme.plain(14));
        Theme.RoundButton bookBtn = Theme.primary("Book Ticket");
        bookBtn.addActionListener(e -> bookTicket());
        bottom.add(seatLabel);
        bottom.add(seatSpinner);
        bottom.add(bookBtn);

        CardPanel card = new CardPanel(new BorderLayout(0, 10));
        card.add(resultHeader, BorderLayout.NORTH);
        card.add(tableScroll(busTable, "No buses match your filters"), BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    // ---- searching, filtering
    private void doSearch(boolean fresh) {
        for (JPopupMenu p : popups) p.setVisible(false);
        String from = fromField.getText().trim();
        String to = toField.getText().trim();
        if (from.isEmpty()) { searchError.setText("Please enter your Origin City"); return; }
        if (to.isEmpty())   { searchError.setText("Please enter your Destination City"); return; }
        if (from.equalsIgnoreCase(to)) {
            searchError.setText("Origin and Destination cannot be the same");
            return;
        }
        searchError.setText(" ");

        if (!loadBuses(from, to)) return;
        if (fresh) resetFilters();
        applyFilters();
        resultsShown = true;
        bodyCards.show(body, "results");
    }

    private boolean loadBuses(String from, String to) {
        String sql = "SELECT id, bus_name, source, destination, travel_date, departure_time, "
                + "price, available_seats, bus_type, boarding_point FROM buses "
                + "WHERE source LIKE ? AND destination LIKE ? AND travel_date = ? "
                + "ORDER BY departure_time";
        allBuses.clear();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + from + "%");
            ps.setString(2, "%" + to + "%");
            ps.setString(3, travelDate.toString());      // yyyy-MM-dd
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    allBuses.add(new Object[]{
                            rs.getInt("id"),
                            rs.getString("bus_name"),
                            rs.getString("source"),
                            rs.getString("destination"),
                            rs.getDate("travel_date"),
                            rs.getString("departure_time"),
                            rs.getDouble("price"),
                            rs.getInt("available_seats"),
                            rs.getString("bus_type"),
                            rs.getString("boarding_point")
                    });
                }
            }
            return true;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1054) {   // unknown column
                JOptionPane.showMessageDialog(this,
                        "Your database needs one update: the table 'buses' has no bus_type / boarding_point "
                                + "column yet.\nPlease run upgrade_buses.sql in MySQL Workbench, then try again.");
            } else {
                JOptionPane.showMessageDialog(this, "Failed to search buses: " + ex.getMessage());
            }
            return false;
        }
    }

    /** Clears every filter and fits the price slider and the dropdowns to the buses that were found. */
    private void resetFilters() {
        updatingFilters = true;
        for (ToggleChip c : typeChips) c.setSelected(false);
        for (ToggleChip c : timeChips) c.setSelected(false);

        int lo = 0, hi = 0;
        LinkedHashSet<String> partners = new LinkedHashSet<>();
        LinkedHashSet<String> boardings = new LinkedHashSet<>();
        if (!allBuses.isEmpty()) {
            double min = Double.MAX_VALUE, max = 0;
            for (Object[] r : allBuses) {
                double p = ((Number) r[6]).doubleValue();
                min = Math.min(min, p);
                max = Math.max(max, p);
                partners.add(String.valueOf(r[1]));
                boardings.add(String.valueOf(r[9]));
            }
            lo = (int) Math.floor(min);
            hi = (int) Math.ceil(max);
        }
        priceSlider.setRange(lo, hi);

        partnerBox.removeAllItems();
        partnerBox.addItem("All partners");
        for (String p : partners) partnerBox.addItem(p);
        boardingBox.removeAllItems();
        boardingBox.addItem("All boarding points");
        for (String b : boardings) boardingBox.addItem(b);

        updatingFilters = false;
        updatePriceLabels();
    }

    private void onFilterChanged() {
        if (updatingFilters) return;
        updatePriceLabels();
        applyFilters();
    }

    private void updatePriceLabels() {
        priceLowLabel.setText(Theme.money(priceSlider.getLow()));
        priceHighLabel.setText(Theme.money(priceSlider.getHigh()));
    }

    private void applyFilters() {
        busModel.setRowCount(0);
        boolean acSel = typeChips.get(0).isSelected(), nonAcSel = typeChips.get(1).isSelected();
        boolean sleeperSel = typeChips.get(2).isSelected(), seaterSel = typeChips.get(3).isSelected();
        int low = priceSlider.getLow(), high = priceSlider.getHigh();
        String partner = partnerBox.getSelectedIndex() > 0 ? (String) partnerBox.getSelectedItem() : null;
        String boarding = boardingBox.getSelectedIndex() > 0 ? (String) boardingBox.getSelectedItem() : null;

        for (Object[] r : allBuses) {
            String type = String.valueOf(r[8]).toLowerCase();
            boolean nonAc = type.startsWith("non ac");
            boolean ac = !nonAc && type.startsWith("ac");
            boolean sleeper = type.contains("sleeper");
            boolean seater = type.contains("seater");

            if ((acSel || nonAcSel) && !((acSel && ac) || (nonAcSel && nonAc))) continue;
            if ((sleeperSel || seaterSel) && !((sleeperSel && sleeper) || (seaterSel && seater))) continue;
            double price = ((Number) r[6]).doubleValue();
            if (price < low || price > high) continue;
            if (!timeMatches(String.valueOf(r[5]))) continue;
            if (partner != null && !partner.equals(r[1])) continue;
            if (boarding != null && !boarding.equals(r[9])) continue;
            busModel.addRow(r);
        }

        int shown = busModel.getRowCount(), total = allBuses.size();
        resultLabel.setText(total == 0
                ? "No buses found for this route and date"
                : (shown == total ? shown + " buses available" : shown + " of " + total + " buses match"));
    }

    private boolean timeMatches(String time) {
        boolean any = false;
        for (ToggleChip c : timeChips) any |= c.isSelected();
        if (!any) return true;
        int h;
        try { h = Integer.parseInt(time.substring(0, time.indexOf(':'))); }
        catch (Exception e) { return true; }
        int bucket = (h >= 23 || h < 5) ? 3 : h < 10 ? 0 : h < 17 ? 1 : 2;
        return timeChips.get(bucket).isSelected();
    }

    /** Back to the first screen: clears the form and hides the filters and results. */
    private void resetSearch() {
        for (JPopupMenu p : popups) p.setVisible(false);
        fromField.setText("");
        toField.setText("");
        searchError.setText(" ");
        setDate(LocalDate.now());
        allBuses.clear();
        busModel.setRowCount(0);
        resultsShown = false;
        bodyCards.show(body, "hero");
    }

    /** Refreshes the list only if the results are already on screen. */
    private void refreshIfShown() {
        if (resultsShown) doSearch(false);
    }

    // ------------------------------------------------------------ small custom widgets

    /** One cell of the search bar. All cells share the same height and the same light-gray look. */
    private static class Segment extends JPanel {
        private Color base = SEG_BASE;
        private Color hover = SEG_HOVER;
        private boolean over;

        Segment(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        void setColors(Color base, Color hover) {
            this.base = base;
            this.hover = hover;
            repaint();
        }

        void onClick(Runnable action) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { over = false; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    if (contains(e.getPoint())) action.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(over ? hover : base);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    /** Clips its children to a rounded shape, so the whole bar has round outer corners. */
    private static class SegmentBar extends JPanel {
        SegmentBar(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 26, 26));
            super.paintChildren(g2);
            g2.dispose();
        }
    }

    /** The red "Search >" cell. */
    private static class SearchSegment extends Segment {
        private final String text;

        SearchSegment(String text) {
            super(new BorderLayout());
            this.text = text;
            setColors(Theme.RED, Theme.RED_DARK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(Theme.bold(20));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(text);
            int x = (getWidth() - (textW + 24)) / 2;
            int cy = getHeight() / 2;
            g2.drawString(text, x, cy + (fm.getAscent() - fm.getDescent()) / 2);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int ax = x + textW + 14;
            g2.drawLine(ax, cy - 6, ax + 6, cy);
            g2.drawLine(ax + 6, cy, ax, cy + 6);
            g2.dispose();
        }
    }

    /** Round button with two arrows (swap From and To). */
    private static class SwapButton extends JButton {
        SwapButton() {
            setPreferredSize(new Dimension(36, 36));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(getModel().isRollover() ? Theme.RED_SOFT : Color.WHITE);
            g2.fillOval(1, 1, w - 3, h - 3);
            g2.setColor(new Color(0xCFCFCF));
            g2.drawOval(1, 1, w - 3, h - 3);
            g2.setColor(Theme.TEXT);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = w / 2, cy = h / 2;
            g2.drawLine(cx - 8, cy - 4, cx + 8, cy - 4);           // top arrow  ->
            g2.drawLine(cx + 8, cy - 4, cx + 4, cy - 8);
            g2.drawLine(cx + 8, cy - 4, cx + 4, cy);
            g2.drawLine(cx + 8, cy + 5, cx - 8, cy + 5);           // bottom arrow <-
            g2.drawLine(cx - 8, cy + 5, cx - 4, cy + 1);
            g2.drawLine(cx - 8, cy + 5, cx - 4, cy + 9);
            g2.dispose();
        }
    }

    /** Rounded on/off button used for Bus Type and Departure Time. */
    private static class ToggleChip extends JToggleButton {
        ToggleChip(String text) {
            super(text);
            setFont(Theme.plain(13));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            setForeground(Theme.TEXT);
            addItemListener(e -> setForeground(isSelected() ? Theme.RED_DARK : Theme.TEXT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isSelected();
            g2.setColor(on ? Theme.RED_SOFT : Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.setColor(on ? Theme.RED : Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Slider with two handles (minimum and maximum price). */
    private static class RangeSlider extends JComponent {
        private int min = 0, max = 0, low = 0, high = 0;
        private int dragging = 0;                    // 1 = low handle, 2 = high handle
        private final Runnable onChange;

        RangeSlider(Runnable onChange) {
            this.onChange = onChange;
            setPreferredSize(new Dimension(200, 30));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    dragging = Math.abs(e.getX() - xFor(low)) <= Math.abs(e.getX() - xFor(high)) ? 1 : 2;
                    move(e.getX());
                }
                @Override public void mouseDragged(MouseEvent e) { if (dragging != 0) move(e.getX()); }
                @Override public void mouseReleased(MouseEvent e) { dragging = 0; }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void setRange(int min, int max) {
            this.min = min;
            this.max = max;
            this.low = min;
            this.high = max;
            setEnabled(max > min);
            repaint();
        }

        int getLow()  { return low; }
        int getHigh() { return high; }

        private int xFor(int value) {
            int pad = 10, span = Math.max(1, max - min);
            return pad + (int) ((getWidth() - 2.0 * pad) * (value - min) / span);
        }

        private void move(int x) {
            int pad = 10, span = Math.max(1, max - min);
            int v = min + (int) Math.round((x - pad) * (double) span / Math.max(1, getWidth() - 2 * pad));
            v = Math.max(min, Math.min(max, v));
            if (dragging == 1) low = Math.min(v, high);
            else high = Math.max(v, low);
            repaint();
            onChange.run();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = getHeight() / 2;
            int xl = xFor(low), xh = xFor(high);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0xE0E0E0));
            g2.drawLine(xFor(min), y, xFor(max), y);
            g2.setColor(isEnabled() ? Theme.RED : new Color(0xBBBBBB));
            g2.drawLine(xl, y, xh, y);
            for (int x : new int[]{xl, xh}) {
                g2.setColor(Color.WHITE);
                g2.fillOval(x - 9, y - 9, 18, 18);
                g2.setColor(isEnabled() ? Theme.RED : new Color(0xBBBBBB));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x - 9, y - 9, 18, 18);
            }
            g2.dispose();
        }
    }

    // ========================================================= MY TICKETS PAGE

    private JComponent buildBookingsPage() {
        // page with a colored gradient on top of the photo
        JPanel page = new JPanel(new BorderLayout(0, 18)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 60, 190),
                        0, getHeight(), new Color(216, 78, 85, 110)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(28, 70, 24, 70));

        JLabel heading = new JLabel("My Tickets");
        heading.setFont(Theme.bold(34));
        heading.setForeground(Color.WHITE);
        JPanel titleBox = new JPanel(new GridLayout(1, 1));
        titleBox.setOpaque(false);
        titleBox.add(heading);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        chips.setOpaque(false);
        chips.add(totalChip);
        chips.add(bookedChip);
        chips.add(cancelledChip);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titleBox, BorderLayout.WEST);
        header.add(chips, BorderLayout.EAST);

        styleTable(bookingTable);
        bookingTable.getColumn("Total").setCellRenderer(new RowRenderer(true));
        bookingTable.getColumn("Status").setCellRenderer(new StatusRenderer());
        setWidths(bookingTable, 110, 190, 250, 120, 90, 80, 120, 140);

        Theme.RoundButton cancelBtn = Theme.outline("Cancel Ticket");
        cancelBtn.addActionListener(e -> cancelTicket());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bottom.setOpaque(false);
        Theme.RoundButton viewBtn = Theme.primary("View Ticket");
        viewBtn.addActionListener(e -> showTicket());
        bottom.add(viewBtn);
        bottom.add(Box.createHorizontalStrut(12));
        bottom.add(cancelBtn);

        Theme.RoundButton deleteBtn = Theme.outline("Delete Ticket");
        deleteBtn.addActionListener(e -> deleteTicket());
        bottom.add(Box.createHorizontalStrut(12));
        bottom.add(deleteBtn);

        CardPanel card = new CardPanel(new BorderLayout(0, 10));
        card.add(tableScroll(bookingTable, "No tickets yet. Book your first trip!"), BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);

        page.add(header, BorderLayout.NORTH);
        page.add(card, BorderLayout.CENTER);
        return page;
    }

    // ================================================================== TABLES

    private static final java.util.Set<String> CENTERED = java.util.Set.of(
            "Date", "Time", "Departure", "Price", "Seats Left", "Seats", "Ticket No.", "Total", "Status");
    private static final Color HEADER_BG = new Color(0x2B, 0x2D, 0x42);
    private static final Color ROW_TINT = new Color(0xFF, 0xF7, 0xF7);

    private JScrollPane tableScroll(JTable table, String emptyText) {
        JScrollPane sp = new JScrollPane();
        sp.setViewport(new TableViewport(table, emptyText));
        sp.setViewportView(table);
        sp.setOpaque(false);
        sp.setBorder(BorderFactory.createLineBorder(new Color(0xE5E5E5)));
        return sp;
    }

    private void setWidths(JTable t, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void styleTable(JTable t) {
        t.setOpaque(false);
        t.setRowHeight(40);
        t.setFont(Theme.plain(14));
        t.setShowGrid(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(0xF1, 0xE3, 0xE3));
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        t.setSelectionBackground(Theme.RED_SOFT);
        t.setSelectionForeground(Theme.TEXT);
        t.setDefaultRenderer(Object.class, new RowRenderer(false));

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setBackground(HEADER_BG);
        h.setPreferredSize(new Dimension(10, 44));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, false, false, row, col);
                String name = String.valueOf(value);
                setText(name.toUpperCase());
                setFont(Theme.bold(12));
                setForeground(Color.WHITE);
                setBackground(HEADER_BG);
                setOpaque(true);
                setHorizontalAlignment(CENTERED.contains(name) ? CENTER : LEFT);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 3, 0, Theme.RED),
                        BorderFactory.createEmptyBorder(0, 12, 0, 12)));
                return this;
            }
        });
    }

    /** Viewport that paints a soft gradient (and an empty-state message) behind the rows. */
    private static class TableViewport extends JViewport {
        private final JTable table;
        private final String emptyText;

        TableViewport(JTable table, String emptyText) {
            this.table = table;
            this.emptyText = emptyText;
            setOpaque(false);
            setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, Color.WHITE, 0, getHeight(), new Color(0xFF, 0xEC, 0xEC)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (table.getRowCount() == 0) {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(Theme.bold(18));
                g2.setColor(new Color(0xA8, 0xA8, 0xB4));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(emptyText, (getWidth() - fm.stringWidth(emptyText)) / 2, getHeight() / 2);
            }
            g2.dispose();
        }
    }

    private static class RowRenderer extends DefaultTableCellRenderer {
        private final boolean money;

        RowRenderer(boolean money) { this.money = money; }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                                                       boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setForeground(Theme.TEXT);
            setBackground(sel ? Theme.RED_SOFT : (row % 2 == 0 ? Color.WHITE : ROW_TINT));
            setHorizontalAlignment(CENTERED.contains(t.getColumnName(col)) ? CENTER : LEFT);
            if (money && v instanceof Number) {
                setText(Theme.money(((Number) v).doubleValue()));
            }
            return this;
        }
    }

    /** Draws the ticket status as a colored pill (green = Booked, red = Cancelled). */
    private static class StatusRenderer extends DefaultTableCellRenderer {
        private Color rowBg = Color.WHITE, pillBg = Color.WHITE, pillFg = Color.BLACK;
        private String label = "";

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                                                       boolean sel, boolean focus, int row, int col) {
            String s = String.valueOf(v);
            label = s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
            boolean booked = "BOOKED".equalsIgnoreCase(s);
            pillBg = booked ? new Color(0xDD, 0xF5, 0xE3) : new Color(0xFD, 0xE4, 0xE4);
            pillFg = booked ? new Color(0x1E, 0x7B, 0x3A) : new Color(0xB3, 0x26, 0x1E);
            rowBg = sel ? Theme.RED_SOFT : (row % 2 == 0 ? Color.WHITE : ROW_TINT);
            setOpaque(false);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(rowBg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setFont(Theme.bold(12));
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(label) + 26, h = 24;
            int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
            g2.setColor(pillBg);
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(pillFg);
            g2.drawString(label, x + 13, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Small translucent statistic box shown at the top of the My Tickets page. */
    private static class StatChip extends JPanel {
        private final JLabel value = new JLabel("0", JLabel.CENTER);

        StatChip(String caption) {
            super(new GridLayout(2, 1));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 22));
            value.setFont(Theme.bold(24));
            value.setForeground(Color.WHITE);
            JLabel cap = new JLabel(caption, JLabel.CENTER);
            cap.setFont(Theme.plain(12));
            cap.setForeground(new Color(255, 255, 255, 200));
            add(value);
            add(cap);
        }

        void setValue(int v) { value.setText(String.valueOf(v)); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 255, 255, 45));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.setColor(new Color(255, 255, 255, 90));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class TabButton extends JButton {
        private boolean active;

        TabButton(String text) {
            super(text);
            setFont(Theme.bold(15));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setActive(false);
        }

        void setActive(boolean a) {
            active = a;
            setForeground(a ? Theme.RED : Theme.TEXT);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (active) {
                g.setColor(Theme.RED);
                g.fillRect(8, getHeight() - 3, getWidth() - 16, 3);
            }
        }
    }

    // ============================================================== DATABASE

    private void bookTicket() {
        int row = busTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a bus first.");
            return;
        }
        row = busTable.convertRowIndexToModel(row);

        int busId = ((Number) busModel.getValueAt(row, 0)).intValue();
        double price = ((Number) busModel.getValueAt(row, 6)).doubleValue();
        int seats = ((Number) seatSpinner.getValue()).intValue();
        double total = price * seats;

        int ok = JOptionPane.showConfirmDialog(this,
                "Book " + seats + " seat(s)?\nTotal price: " + Theme.money(total),
                "Confirm Booking", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String updateSql = "UPDATE buses SET available_seats = available_seats - ? "
                        + "WHERE id = ? AND available_seats >= ?";
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    update.setInt(1, seats);
                    update.setInt(2, busId);
                    update.setInt(3, seats);
                    if (update.executeUpdate() == 0) {
                        conn.rollback();
                        JOptionPane.showMessageDialog(this, "Not enough seats available.");
                        return;
                    }
                }

                String insertSql = "INSERT INTO bookings (user_id, bus_id, seats, total_price) "
                        + "VALUES (?, ?, ?, ?)";
                try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                    insert.setInt(1, userId);
                    insert.setInt(2, busId);
                    insert.setInt(3, seats);
                    insert.setDouble(4, total);
                    insert.executeUpdate();
                }

                conn.commit();
                JOptionPane.showMessageDialog(this, "Ticket booked successfully!");
                refreshIfShown();

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to book ticket: " + ex.getMessage());
        }
    }

    private void loadMyBookings() {
        bookingModel.setRowCount(0);

        String sql = "SELECT b.id, bs.bus_name, bs.source, bs.destination, bs.travel_date, "
                + "bs.departure_time, b.seats, b.total_price, b.status "
                + "FROM bookings b JOIN buses bs ON b.bus_id = bs.id "
                + "WHERE b.user_id = ? ORDER BY b.id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bookingModel.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("bus_name"),
                            rs.getString("source") + " - " + rs.getString("destination"),
                            rs.getDate("travel_date"),
                            rs.getString("departure_time"),
                            rs.getInt("seats"),
                            rs.getDouble("total_price"),
                            rs.getString("status")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load tickets: " + ex.getMessage());
        }

        int booked = 0, cancelled = 0;
        for (int i = 0; i < bookingModel.getRowCount(); i++) {
            String st = String.valueOf(bookingModel.getValueAt(i, 7));
            if ("BOOKED".equalsIgnoreCase(st)) booked++;
            else if ("CANCELLED".equalsIgnoreCase(st)) cancelled++;
        }
        totalChip.setValue(bookingModel.getRowCount());
        bookedChip.setValue(booked);
        cancelledChip.setValue(cancelled);
    }

    // ============================================================ TICKET PRINTOUT

    /** Shows the selected BOOKED ticket as a small printable ticket box, with Print and Close buttons. */
    private void showTicket() {
        int row = bookingTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a ticket to view.");
            return;
        }
        row = bookingTable.convertRowIndexToModel(row);

        String status = String.valueOf(bookingModel.getValueAt(row, 7));
        if (!"BOOKED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "This ticket has been cancelled, so it cannot be printed.");
            return;
        }

        JPanel ticket = buildTicketPanel(
                String.valueOf(bookingModel.getValueAt(row, 0)),
                String.valueOf(bookingModel.getValueAt(row, 1)),
                String.valueOf(bookingModel.getValueAt(row, 2)),
                String.valueOf(bookingModel.getValueAt(row, 3)),
                String.valueOf(bookingModel.getValueAt(row, 4)),
                String.valueOf(bookingModel.getValueAt(row, 5)),
                Theme.money(((Number) bookingModel.getValueAt(row, 6)).doubleValue()),
                status);

        JDialog dlg = new JDialog(this, "Your Ticket", true);
        JPanel wrap = new JPanel(new BorderLayout(0, 14));
        wrap.setBackground(new Color(0xF3, 0xF4, 0xF4));
        wrap.setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));
        wrap.add(ticket, BorderLayout.CENTER);

        Theme.RoundButton printBtn = Theme.primary("Print");
        printBtn.addActionListener(e -> printTicket(ticket));
        Theme.RoundButton closeBtn = Theme.outline("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(printBtn);
        buttons.add(closeBtn);
        wrap.add(buttons, BorderLayout.SOUTH);

        dlg.setContentPane(wrap);
        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private JPanel buildTicketPanel(String ticketNo, String bus, String route, String date,
                                    String time, String seats, String total, String status) {
        JPanel ticket = new JPanel(new BorderLayout());
        ticket.setBackground(Color.WHITE);
        ticket.setBorder(BorderFactory.createLineBorder(Theme.RED, 2));

        // red header
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.RED);
        head.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));
        JLabel brand = new JLabel("BusTiket");
        brand.setFont(Theme.bold(22));
        brand.setForeground(Color.WHITE);
        JLabel kind = new JLabel("E-TICKET");
        kind.setFont(Theme.bold(13));
        kind.setForeground(new Color(255, 255, 255, 220));
        head.add(brand, BorderLayout.WEST);
        head.add(kind, BorderLayout.EAST);

        // all ticket details
        JPanel info = new JPanel(new GridLayout(0, 2, 24, 14));
        info.setBackground(Color.WHITE);
        info.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        addTicketLine(info, "Ticket No.", ticketNo);
        addTicketLine(info, "Passenger", displayName);
        addTicketLine(info, "Bus", bus);
        addTicketLine(info, "Route", route);
        addTicketLine(info, "Date", date);
        addTicketLine(info, "Time", time);
        addTicketLine(info, "Seats", seats);
        addTicketLine(info, "Total", total);
        addTicketLine(info, "Status", status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase());

        JLabel foot = new JLabel("Please show this ticket when boarding. Have a safe trip!", JLabel.CENTER);
        foot.setFont(Theme.plain(12));
        foot.setForeground(Theme.MUTED);
        foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE5E5E5)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        ticket.add(head, BorderLayout.NORTH);
        ticket.add(info, BorderLayout.CENTER);
        ticket.add(foot, BorderLayout.SOUTH);
        ticket.setPreferredSize(new Dimension(480, ticket.getPreferredSize().height));
        return ticket;
    }

    private void addTicketLine(JPanel panel, String caption, String value) {
        JPanel cell = new JPanel(new GridLayout(2, 1, 0, 2));
        cell.setOpaque(false);
        JLabel c = new JLabel(caption);
        c.setFont(Theme.plain(12));
        c.setForeground(Theme.MUTED);
        JLabel v = new JLabel(value);
        v.setFont(Theme.bold(15));
        v.setForeground(Theme.TEXT);
        cell.add(c);
        cell.add(v);
        panel.add(cell);
    }

    private void printTicket(JPanel ticket) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Bus Ticket");
        job.setPrintable((g, pf, pageIndex) -> {
            if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) g;
            g2.translate(pf.getImageableX(), pf.getImageableY());
            double scale = Math.min(1.3, pf.getImageableWidth() / ticket.getWidth());
            g2.scale(scale, scale);
            ticket.printAll(g2);
            return Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Failed to print ticket: " + ex.getMessage());
            }
        }
    }

    /** Permanently removes a CANCELLED ticket from the list (its seats were already returned when it was cancelled). */
    private void deleteTicket() {
        int row = bookingTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a ticket to delete.");
            return;
        }
        row = bookingTable.convertRowIndexToModel(row);

        int bookingId = ((Number) bookingModel.getValueAt(row, 0)).intValue();
        String status = String.valueOf(bookingModel.getValueAt(row, 7));
        if (!"CANCELLED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this,
                    "Only cancelled tickets can be deleted.\nCancel the ticket first.");
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this,
                "Delete this cancelled ticket permanently?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        String sql = "DELETE FROM bookings WHERE id = ? AND user_id = ? AND status = 'CANCELLED'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.setInt(2, userId);
            if (ps.executeUpdate() == 0) {
                JOptionPane.showMessageDialog(this, "Ticket not found or not cancelled.");
                return;
            }
            JOptionPane.showMessageDialog(this, "Ticket deleted.");
            loadMyBookings();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete ticket: " + ex.getMessage());
        }
    }

    private void cancelTicket() {
        int row = bookingTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a ticket to cancel.");
            return;
        }
        row = bookingTable.convertRowIndexToModel(row);

        int bookingId = ((Number) bookingModel.getValueAt(row, 0)).intValue();
        String status = String.valueOf(bookingModel.getValueAt(row, 7));
        if (!"BOOKED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "This ticket is already cancelled or inactive.");
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel this ticket?",
                "Confirm Cancellation", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int busId;
                int seats;

                String selectSql = "SELECT bus_id, seats FROM bookings "
                        + "WHERE id = ? AND user_id = ? AND status = 'BOOKED'";
                try (PreparedStatement select = conn.prepareStatement(selectSql)) {
                    select.setInt(1, bookingId);
                    select.setInt(2, userId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            JOptionPane.showMessageDialog(this, "Ticket not found or already cancelled.");
                            return;
                        }
                        busId = rs.getInt("bus_id");
                        seats = rs.getInt("seats");
                    }
                }

                String cancelSql = "UPDATE bookings SET status = 'CANCELLED' "
                        + "WHERE id = ? AND user_id = ? AND status = 'BOOKED'";
                try (PreparedStatement cancel = conn.prepareStatement(cancelSql)) {
                    cancel.setInt(1, bookingId);
                    cancel.setInt(2, userId);
                    if (cancel.executeUpdate() == 0) {
                        conn.rollback();
                        JOptionPane.showMessageDialog(this, "Failed to cancel the ticket.");
                        return;
                    }
                }

                String returnSql = "UPDATE buses SET available_seats = available_seats + ? WHERE id = ?";
                try (PreparedStatement giveBack = conn.prepareStatement(returnSql)) {
                    giveBack.setInt(1, seats);
                    giveBack.setInt(2, busId);
                    giveBack.executeUpdate();
                }

                conn.commit();
                JOptionPane.showMessageDialog(this, "Ticket cancelled successfully.");
                loadMyBookings();
                refreshIfShown();

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to cancel ticket: " + ex.getMessage());
        }
    }
}