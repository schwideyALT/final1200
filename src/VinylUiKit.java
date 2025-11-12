import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.InputStream;
import java.net.URL;

import static javax.swing.SwingConstants.LEFT;

// Shared UI utilities for the Vinyl app.
// Colors, table with column bands, renderers, rating editor, buttons, header renderer, scrollbars.
public final class VinylUiKit {
    private VinylUiKit() {}

    // ---------- Colors ----------
    public static final Color BG = new Color(24, 24, 24);
    public static final Color PANEL = new Color(30, 30, 30);
    public static final Color FG = new Color(230, 230, 230);
    public static final Color SUBFG = new Color(180, 180, 180);
    public static final Color HEADER_BG = new Color(34, 34, 34);
    public static final Color RED = new Color(210, 54, 54);
    public static final Color RED_HOVER = new Color(230, 76, 76);

    // ---------- Defaults ----------
    public static void setSystemDefaults() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", FG);
        UIManager.put("TextField.background", new Color(40, 40, 40));
        UIManager.put("TextField.foreground", FG);
        UIManager.put("TextField.caretForeground", FG);
        UIManager.put("TextField.border", BorderFactory.createLineBorder(new Color(60, 60, 60)));
        UIManager.put("ComboBox.background", new Color(40, 40, 40));
        UIManager.put("ComboBox.foreground", FG);
        UIManager.put("Spinner.background", new Color(40, 40, 40));
        UIManager.put("Spinner.foreground", FG);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
    }

    // ---------- Column-banded JTable ----------
    public static final class ColumnBandTable extends JTable {
        public ColumnBandTable(TableModel model) {
            super(model);
            setOpaque(false);
            setDefaultRenderer(Object.class, new TextCellRenderer(SwingConstants.LEFT));
            setShowGrid(false);
            setRowHeight(40);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int bandInsetX = 6;
            int bandRadius = 16;
            TableColumnModel tcm = getColumnModel();
            int rowCount = getRowCount();

            for (int row = 0; row < rowCount; row++) {
                int y = getCellRect(row, 0, true).y;
                int h = getRowHeight(row);
                int totalWidth = 0;
                for (int col = 0; col < tcm.getColumnCount(); col++) {
                    totalWidth += tcm.getColumn(col).getWidth();
                }
                g2.setColor(row % 2 == 0 ? new Color(40, 40, 40) : new Color(35, 35, 35));
                g2.fillRoundRect(bandInsetX, y + 1, totalWidth - bandInsetX, h - 2, bandRadius, bandRadius);
            }
            g2.dispose();

            super.paintComponent(g);
        }
    }

    // ---------- Header renderer ----------
    public static final class HeaderRenderer extends DefaultTableCellRenderer {
        public HeaderRenderer() { setHorizontalAlignment(LEFT); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setOpaque(false);
            JPanel p = new JPanel(new BorderLayout()) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(HEADER_BG);
                    g2.fillRoundRect(6, 6, getWidth()-12, getHeight()-6, 12, 12);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            JLabel l = new JLabel(value == null ? "" : value.toString());
            l.setBorder(new EmptyBorder(10, 12, 10, 12));
            l.setForeground(SUBFG);
            p.add(l, BorderLayout.CENTER);
            return p;
        }
    }

    // ---------- Transparent text cell ----------
    public static final class TextCellRenderer extends DefaultTableCellRenderer {
        private final int align;
        public TextCellRenderer(int align) { this.align = align; setOpaque(false); }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(align);
            setText(value == null ? "" : value.toString());
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setForeground(FG);
            setOpaque(false);
            return this;
        }
    }

    // ---------- Cover, Explicit, Rating ----------
    public static final class CoverRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JPanel p = new JPanel(new GridBagLayout()); p.setOpaque(false);
            JLabel l = new JLabel(); l.setOpaque(false); l.setHorizontalAlignment(SwingConstants.CENTER); l.setBorder(new EmptyBorder(6, 0, 6, 0));
            if (value instanceof ImageIcon) l.setIcon((ImageIcon) value);
            p.add(l); return p;
        }
    }
    public static final class ExplicitRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            boolean exp = value instanceof Boolean && (Boolean) value;
            JPanel p = new JPanel(new GridBagLayout()); 
            p.setOpaque(false);
            if (exp) { 
                JLabel pill = new JLabel("E"); 
                pill.setForeground(Color.WHITE); 
                pill.setOpaque(true); 
                pill.setBackground(new Color(180, 30, 30)); 
                pill.setBorder(new EmptyBorder(3, 10, 3, 10)); 
                p.add(pill); 
            }
            return p;
        }
    }
    public static final class RatingRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int rating = value instanceof Integer ? (Integer) value : 0;
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 15)); p.setOpaque(false);
            for (int i = 1; i <= 5; i++) { JLabel star = new JLabel(i <= rating ? "★" : "☆"); star.setForeground(new Color(255, 204, 64)); star.setFont(star.getFont().deriveFont(Font.PLAIN, 18f)); p.add(star); }
            return p;
        }
    }
    public static final class RatingEditor extends AbstractCellEditor implements TableCellEditor {
        private int rating; private JPanel panel;
        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            rating = value instanceof Integer ? (Integer) value : 0;
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0)); panel.setOpaque(false);
            for (int i = 1; i <= 5; i++) { final int idx = i; JLabel star = new JLabel(i <= rating ? "★" : "☆"); star.setForeground(new Color(255, 204, 64)); star.setFont(star.getFont().deriveFont(Font.PLAIN, 18f)); star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); star.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { rating = idx; stopCellEditing(); } }); panel.add(star); }
            return panel;
        }
        @Override public Object getCellEditorValue() { return rating; }
    }

    // ---------- Buttons ----------
    public static JButton redButton(String text) { return new RoundedButton(text, RED, RED_HOVER, 18); }
    public static JButton grayButton(String text) { return new RoundedButton(text, new Color(70,70,70), new Color(88,88,88), 18); }
    public static final class RoundedButton extends JButton {
        private final int radius; private final Color base; private final Color hover; private boolean over;
        public RoundedButton(String text, Color base, Color hover, int radius) { super(text); this.radius = radius; this.base = base; this.hover = hover; setFocusPainted(false); setBorder(new EmptyBorder(8, 16, 8, 16)); setContentAreaFilled(false); setOpaque(false); setForeground(Color.WHITE); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { over = true; repaint(); } public void mouseExited(MouseEvent e) { over = false; repaint(); } }); }
        @Override protected void paintComponent(Graphics g) { Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setColor(over ? hover : base); g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius*2, radius*2); g2.dispose(); super.paintComponent(g); }
    }

    // ---------- Minimal scrollbars ----------
    public static final class MinimalScrollBarUI extends BasicScrollBarUI {
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {}
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {}
        @Override protected Dimension getMinimumThumbSize() { return new Dimension(0, 0); }
        @Override protected Dimension getMaximumThumbSize() { return new Dimension(0, 0); }
        @Override protected JButton createDecreaseButton(int orientation) { return zero(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zero(); }
        private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0,0)); b.setOpaque(false); b.setContentAreaFilled(false); b.setBorder(null); return b; }
        @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(0,0); }
    }

    // ---------- Icon loading ----------
    public static ImageIcon loadIcon(String path, int width, int height) {
        try {
            URL resource = VinylUiKit.class.getResource(path);
            if (resource != null) {
                ImageIcon icon = new ImageIcon(resource);
                Image scaled = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
        } catch (Exception e) {
            System.err.println("Failed to load icon: " + path);
        }
        return null;
    }
}
