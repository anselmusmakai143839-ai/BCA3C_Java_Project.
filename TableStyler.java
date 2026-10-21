import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Locale;

public final class TableStyler {

    private TableStyler() {}

    // Warna
    private static final Color HEADER_TOP     = new Color(0x4F46E5);
    private static final Color HEADER_BOTTOM  = new Color(0x4338CA);
    private static final Color ROW_EVEN       = new Color(255, 255, 255, 228);
    private static final Color ROW_ODD        = new Color(243, 245, 255, 228);
    private static final Color ROW_SELECTED   = new Color(199, 210, 254, 245);
    private static final Color LINE           = new Color(226, 232, 240);
    private static final Color TEXT_NORMAL    = new Color(0x1E293B);
    private static final Color TEXT_CANCELLED = new Color(0x94A3B8);

    // Kolom: 0 ID, 1 Bus, 2 Rute, 3 Tanggal, 4 Jam, 5 Kursi, 6 Total, 7 Status
    private static final int STATUS_COL = 7;
    private static final int[] WIDTHS = {90, 170, 230, 110, 80, 80, 120, 150};

    private static int alignFor(int modelCol) {
        return (modelCol == 0 || modelCol == 3 || modelCol == 4 || modelCol == 5)
                ? SwingConstants.CENTER : SwingConstants.LEFT;
    }

    private static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    private static Color rowColor(boolean selected, int row) {
        if (selected) return ROW_SELECTED;
        return row % 2 == 0 ? ROW_EVEN : ROW_ODD;
    }

    private static boolean isCancelled(JTable table, int row) {
        Object s = table.getModel().getValueAt(
                table.convertRowIndexToModel(row), STATUS_COL);
        return s != null && "CANCELLED".equalsIgnoreCase(s.toString());
    }

    // ===== Dipanggil dari DashboardFrame =====

    public static void styleBookingTable(JTable table) {
        table.setOpaque(false);
        table.setRowHeight(46);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Header
        JTableHeader header = table.getTableHeader();
        header.setDefaultRenderer(new HeaderRenderer());
        header.setPreferredSize(new Dimension(0, 46));
        header.setReorderingAllowed(false);
        header.setBackground(HEADER_BOTTOM);
        header.setOpaque(true);

        // Renderer tiap kolom
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableCellRenderer r;
            if (i == STATUS_COL)      r = new StatusRenderer();
            else if (i == 6)          r = new TextRenderer(i, true);
            else                      r = new TextRenderer(i, false);
            table.getColumnModel().getColumn(i).setCellRenderer(r);
            if (i < WIDTHS.length) {
                table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
            }
        }
    }

    public static void styleScrollPane(JScrollPane sp) {
        Component view = sp.getViewport().getView();

        JViewport vp = new JViewport() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(255, 255, 255, 175));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        vp.setOpaque(false);
        vp.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        sp.setViewport(vp);
        sp.setViewportView(view);
        sp.setOpaque(false);
        sp.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 210), 1));
    }

    // ===== Header bergradasi =====

    private static class HeaderRenderer extends JLabel implements TableCellRenderer {
        HeaderRenderer() {
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(font(Font.BOLD, 13));
            setBorder(new EmptyBorder(0, 14, 0, 14));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setHorizontalAlignment(alignFor(table.convertColumnIndexToModel(column)));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, HEADER_TOP, 0, getHeight(), HEADER_BOTTOM));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ===== Sel teks biasa (zebra + format harga) =====

    private static class TextRenderer extends JLabel implements TableCellRenderer {
        private final int modelCol;
        private final boolean money;
        private Color bg = ROW_EVEN;

        TextRenderer(int modelCol, boolean money) {
            this.modelCol = modelCol;
            this.money = money;
            setOpaque(false);
            setFont(font(Font.PLAIN, 13));
            setBorder(new EmptyBorder(0, 14, 0, 14));
            setHorizontalAlignment(alignFor(modelCol));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            bg = rowColor(isSelected, row);
            setForeground(isCancelled(table, row) ? TEXT_CANCELLED : TEXT_NORMAL);
            if (money && value instanceof Number) {
                setText(String.format(Locale.US, "Rs %,.0f", ((Number) value).doubleValue()));
            } else {
                setText(value == null ? "" : value.toString());
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(bg);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(LINE);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            super.paintComponent(g);
        }
    }

    // ===== Badge status (hijau = BOOKED, merah = CANCELLED) =====

    private static class StatusRenderer extends JPanel implements TableCellRenderer {
        private String text = "";
        private boolean booked;
        private Color bg = ROW_EVEN;

        StatusRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            String s = value == null ? "" : value.toString();
            booked = "BOOKED".equalsIgnoreCase(s);
            if (booked) text = "Booked";
            else if ("CANCELLED".equalsIgnoreCase(s)) text = "Cancelled";
            else text = s;
            bg = rowColor(isSelected, row);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // latar baris
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(LINE);
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);

            // ukuran pil
            Font f = font(Font.BOLD, 12);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int dot = 8, gap = 7, padX = 12, h = 26;
            int w = padX + dot + gap + fm.stringWidth(text) + padX;
            int x = 14;
            int y = (getHeight() - h) / 2;

            Color pill = booked ? new Color(220, 252, 231) : new Color(254, 226, 226);
            Color dotC = booked ? new Color(22, 163, 74)   : new Color(220, 38, 38);
            Color txtC = booked ? new Color(21, 128, 61)   : new Color(185, 28, 28);

            g2.setColor(pill);
            g2.fillRoundRect(x, y, w, h, h, h);

            g2.setColor(dotC);
            g2.fillOval(x + padX, y + (h - dot) / 2, dot, dot);

            g2.setColor(txtC);
            g2.drawString(text, x + padX + dot + gap,
                    y + (h - fm.getHeight()) / 2 + fm.getAscent());

            g2.dispose();
        }
    }
}