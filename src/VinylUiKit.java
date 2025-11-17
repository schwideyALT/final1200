import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI;
import java.awt.geom.RoundRectangle2D;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import java.util.ArrayList;
import javax.swing.Popup;
import javax.swing.PopupFactory;

import static javax.swing.SwingConstants.LEFT;

// Shared UI utilities for the Vinyl app.
// Colors, table with column bands, renderers, rating editor, buttons, header renderer, scrollbars.
public final class VinylUiKit {
    // Clickable star bar for dialog
    static class StarBar extends JPanel {
        private int value;
        private final java.util.List<JLabel> stars = new ArrayList<>();

        StarBar(int initial) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            setValue(initial);
            for (int i = 1; i <= 5; i++) {
                final int idx = i;
                JLabel star = new JLabel("☆");
                // Use Wingdine-based star font for dialog rating as well
                star.setFont(FontManager.starFont(22f));
                // Match table stars: all red
                star.setForeground(VinylUiKit.RED);
                star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                star.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        setValue(idx);
                    }
                });
                stars.add(star);
                add(star);
            }
            refresh();
        }

        void setValue(int v) {
            value = Math.max(0, Math.min(5, v));
            refresh();
        }

        int getValue() {
            return value;
        }

        private void refresh() {
            for (int i = 0; i < stars.size(); i++) {
                stars.get(i).setText(i < value ? "★" : "☆");
            }
        }
    }
    private VinylUiKit() {}

    // ---------- Colors ----------
    public static final Color BG = new Color(24, 24, 24);
    public static final Color PANEL = new Color(30, 30, 30);
    public static final Color FG = new Color(230, 230, 230);
    public static final Color SUBFG = new Color(180, 180, 180);
    public static final Color HEADER_BG = new Color(34, 34, 34);
    public static final Color RED = new Color(210, 54, 54);
    public static final Color RED_HOVER = new Color(230, 76, 76);

    // Default font to use for non-SF text elements
    public static final Font DEFAULT_STAR_FONT = new JLabel().getFont();

    /**
     * Try to create a Wingdings font at the given size.
     * If Wingdings is not available on this system, fall back to DEFAULT_STAR_FONT.
     */
    public static Font wingdings(float size) {
        Font f = new Font("Wingdings", Font.PLAIN, (int) size);
        if (!"Wingdings".equalsIgnoreCase(f.getFamily())) {
            // System substituted a different family; fall back
            return DEFAULT_STAR_FONT.deriveFont(Font.PLAIN, size);
        }
        return f;
    }

    // ---------- Defaults ----------
    public static void setSystemDefaults() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        // Prefer lightweight popups globally so transparent, rounded corners
        // blend with our dark background instead of a heavyweight window.
        JPopupMenu.setDefaultLightWeightPopupEnabled(true);



        // Core backgrounds
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", FG);

        // Text fields ...
        // ...

        // Menus and menu items
        Color menuBg = new Color(40, 40, 40);
        Color menuSelBg = new Color(70, 70, 70);

        UIManager.put("Menu.background", menuBg);
        UIManager.put("Menu.foreground", FG);
        UIManager.put("Menu.opaque", Boolean.FALSE);

        UIManager.put("MenuItem.background", menuBg);
        UIManager.put("MenuItem.foreground", FG);
        UIManager.put("MenuItem.opaque", Boolean.FALSE);

        UIManager.put("Menu.selectionBackground", menuSelBg);
        UIManager.put("MenuItem.selectionBackground", menuSelBg);
        UIManager.put("Menu.selectionForeground", FG);
        UIManager.put("MenuItem.selectionForeground", FG);

        // Checkmark menu items (JCheckBoxMenuItem)
        UIManager.put("CheckBoxMenuItem.background", menuBg);
        UIManager.put("CheckBoxMenuItem.foreground", Color.WHITE);
        //UIManager.put("CheckBoxMenuItem.selectionBackground", menuSelBg);
        UIManager.put("CheckBoxMenuItem.selectionForeground", Color.WHITE);
        UIManager.put("CheckBoxMenuItem.opaque", Boolean.FALSE);
        UIManager.put("CheckBoxMenuItem.borderPainted", Boolean.FALSE);
        // Use a custom red check icon that only paints when selected
        UIManager.put("CheckBoxMenuItem.checkIcon", new RedCheckIcon(RED));

        // Popup menu defaults
        UIManager.put("PopupMenu.background", menuBg);
        UIManager.put("PopupMenu.foreground", FG);
        UIManager.put("PopupMenu.border", BorderFactory.createEmptyBorder()); // no LAF border
        UIManager.put("Popup.dropShadowPainted", Boolean.FALSE);

        JPopupMenu.setDefaultLightWeightPopupEnabled(true);
    }

    // ---------- Rounded popup menu ----------
    // Use this class when you create popups for a fully rounded, opaque dark background.
    public static final class RoundedPopupMenu extends JPopupMenu {
        private final int radius = 12;
        private final Color fill = new Color(34, 34, 34);
        // make stroke fully transparent so no visible outline
        private final Color stroke = new Color(0, 0, 0, 0);

        public RoundedPopupMenu() {
            setOpaque(false);
            setBorder(new EmptyBorder(6, 8, 6, 8));
            setBorderPainted(false);
            setBackground(new Color(0, 0, 0, 0));
            setForeground(FG);
            setLightWeightPopupEnabled(true);
        }


        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            int w = getWidth();
            int h = getHeight();

            // 1) Clear the whole rectangular area
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0, 0, w, h);
            g2.setComposite(AlphaComposite.SrcOver);

            // 2) Normal painting again
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);

            // Rounded shape
            RoundRectangle2D shape =
                    new RoundRectangle2D.Float(0, 0, w - 1, h - 1, radius * 2, radius * 2);

            // 3) Fill rounded dark background
            g2.setColor(fill);
            g2.fill(shape);

            // 4) Optional stroke (transparent now)
            if (stroke.getAlpha() > 0) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(shape);
            }

            // 5) Clip all menu content to the rounded shape
            Shape oldClip = g2.getClip();
            g2.setClip(shape);
            super.paint(g2);   // let UI paint items inside the clip
            g2.setClip(oldClip);

            g2.dispose();
        }

        @Override
        protected void paintComponent(Graphics g) {
            // nothing; all background is handled in paint()
        }
    }


    // Rounded border that also paints a filled background, used when you need to
    // restyle an existing JPopupMenu through stylePopup.
    // Rounded border that also paints a filled background, used when you need to
// restyle an existing JPopupMenu through stylePopup.
    private static final class RoundedPopupBorder implements Border {
        private final int radius;
        private final Color fill;
        private final Color stroke;
        private final Insets insets;

        RoundedPopupBorder(int radius, Color fill, Color stroke) {
            this.radius = radius;
            this.fill = fill;
            // transparent stroke if you want no visible outline
            this.stroke = stroke; // or new Color(0, 0, 0, 0) for no border line
            this.insets = new Insets(6, 8, 6, 8);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(fill);
            g2.fillRoundRect(x, y, width - 1, height - 1, radius * 2, radius * 2);

            if (stroke.getAlpha() > 0) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(x, y, width - 1, height - 1, radius * 2, radius * 2);
            }

            g2.dispose();

            if (c instanceof JComponent jc) {
                jc.setOpaque(false);
                jc.setBackground(new Color(0, 0, 0, 0));
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return insets;
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }


    // Rounded menu item for dropdowns and popup menus, opaque dark background and white text
    public static class RoundedMenuItem extends JMenuItem {
        private final int radius = 10;

        public RoundedMenuItem(String text) {
            super(text);
            init();
        }

        public RoundedMenuItem(Action action) {
            super(action);
            init();
        }

        private void init() {
            setOpaque(false);
            setForeground(FG);
            setBackground(new Color(40, 40, 40));
            setBorder(new EmptyBorder(6, 12, 6, 12));
            setUI(new BasicMenuItemUI() {
                @Override
                public void paint(Graphics g, JComponent c) {
                    JMenuItem item = (JMenuItem) c;
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int w = c.getWidth();
                    int h = c.getHeight();

                    ButtonModel model = item.getModel();
                    Color bg = new Color(40, 40, 40);
                    if (model.isArmed() || model.isSelected()) {
                        bg = new Color(70, 70, 70);
                    }

                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, w - 1, h - 1, radius * 2, radius * 2);

                    g2.setColor(item.getForeground());
                    g2.setFont(item.getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    String text = item.getText();
                    Icon icon = item.getIcon();

                    int textX = 12;
                    int iconY = (h - (icon == null ? 0 : icon.getIconHeight())) / 2;
                    int textY = (h - fm.getHeight()) / 2 + fm.getAscent();

                    if (icon != null) {
                        int iconX = 8;
                        icon.paintIcon(c, g2, iconX, iconY);
                        textX = iconX + icon.getIconWidth() + 6;
                    }

                    if (text != null && !text.isEmpty()) {
                        g2.drawString(text, textX, textY);
                    }

                    g2.dispose();
                }
            });
        }
    }

    private static final class RedCheckIcon implements Icon {
        private final int size = 10;
        private final Color color;

        RedCheckIcon(Color color) {
            this.color = color;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            // Only paint the check when the item is selected
            if (c instanceof JCheckBoxMenuItem item) {
                ButtonModel model = item.getModel();
                if (!model.isSelected()) {
                    return; // no checkmark when not selected
                }
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(color);

            int x1 = x + 2;
            int y1 = y + size / 2;
            int x2 = x + size / 2;
            int y2 = y + size - 2;
            int x3 = x + size - 2;
            int y3 = y + 2;

            g2.drawLine(x1, y1, x2, y2);
            g2.drawLine(x2, y2, x3, y3);

            g2.dispose();
        }
    }


    public static ImageIcon loadIcon(String s, int w, int h) {
        try {
            URL url = VinylUiKit.class.getResource(s);
            if (url != null) {
                ImageIcon icon = new ImageIcon(url);
                return new ImageIcon(icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---------- Rounded text field ----------
    public static class RoundedTextField extends JTextField {
        private final int radius;
        private final Color placeholderColor = new Color(140, 140, 140);

        public RoundedTextField() {
            this("", 12);
        }

        public RoundedTextField(String text) {
            this(text, 12);
        }

        public RoundedTextField(int columns) {
            this("", 12);
            setColumns(columns);
        }

        public RoundedTextField(String text, int radius) {
            super(text);
            this.radius = radius;
            setOpaque(false);
            setBackground(new Color(40, 40, 40));
            setForeground(FG);
            setCaretColor(FG);
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setFont(getFont().deriveFont(Font.PLAIN, getFont().getSize2D()));

            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });

            getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);

            g2.setColor(hasFocus() ? new Color(88, 88, 88) : new Color(60, 60, 60));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius * 2, radius * 2);

            g2.dispose();
            super.paintComponent(g);

            String hint = (String) getClientProperty("JTextField.placeholderText");
            if (hint != null && hint.length() > 0 && getText().isEmpty() && !isFocusOwner()) {
                Graphics2D gh = (Graphics2D) g.create();
                gh.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                gh.setColor(placeholderColor);
                Insets in = getInsets();
                FontMetrics fm = gh.getFontMetrics(getFont());
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                gh.drawString(hint, in.left, y);
                gh.dispose();
            }
        }
    }

    // Apply rounded dark styling to any JPopupMenu that you already created
    public static void stylePopup(JPopupMenu popup) {
        if (popup == null) return;
        JPopupMenu.setDefaultLightWeightPopupEnabled(true);
        popup.setLightWeightPopupEnabled(true);

        // We no longer rely on true transparency; use an opaque popup
        popup.setOpaque(true);
        popup.setBackground(BG); // match app background
        popup.setBorderPainted(false);
        popup.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        popup.setForeground(FG);

        // Custom UI delegate that paints our rounded dark background
        popup.setUI(new BasicPopupMenuUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = c.getWidth();
                int h = c.getHeight();
                int radius = 12;

                // 1) Clear the area with the app background color instead of full transparency
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(BG); // or PANEL, depending on what blends best
                g2.fillRect(0, 0, w, h);

                // Define rounded clip
                RoundRectangle2D clipShape =
                        new RoundRectangle2D.Float(0, 0, w - 1, h - 1, radius * 2, radius * 2);

                // 2) Fill rounded dark background
                g2.setColor(new Color(34, 34, 34));
                g2.fill(clipShape);

                // Optional stroke (kept transparent)
                Color stroke = new Color(0, 0, 0, 0);
                if (stroke.getAlpha() > 0) {
                    g2.setColor(stroke);
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(clipShape);
                }

                // 3) Clip all menu content to the rounded shape
                Shape oldClip = g2.getClip();
                g2.setClip(clipShape);
                super.paint(g2, c);
                g2.setClip(oldClip);

                g2.dispose();
            }
        });

        popup.putClientProperty("JPopupMenu.firePopupMenuCanceledOnExit", Boolean.TRUE);
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

            Object hr = getClientProperty("hoverRow");
            int hoverRow = (hr instanceof Integer) ? (Integer) hr : -1;

            for (int row = 0; row < rowCount; row++) {
                int y = getCellRect(row, 0, true).y;
                int h = getRowHeight(row);
                int totalWidth = 0;
                for (int col = 0; col < tcm.getColumnCount(); col++) {
                    totalWidth += tcm.getColumn(col).getWidth();
                }
                g2.setColor(row % 2 == 0 ? new Color(30,30,30) : BG);
                g2.fillRoundRect(bandInsetX, y + 1, totalWidth - bandInsetX, h - 2, bandRadius, bandRadius);

                if (row == hoverRow) {
                    g2.setColor(new Color(45, 45, 45, 225));
                    g2.fillRoundRect(bandInsetX, y + 1, totalWidth - bandInsetX, h - 2, bandRadius, bandRadius);
                }
            }
            g2.dispose();

            super.paintComponent(g);
        }
    }

    // Dummy text renderer placeholder to keep this file compiling in isolation

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
                    // Rounded, gray background for the header row
                    g2.setColor(BG);
                    int padX = 6;
                    int padTop = 6;
                    int arc = 12;
                    g2.fillRoundRect(padX, padTop, getWidth() - padX * 2, getHeight() - padTop, arc, arc);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            JLabel l = new JLabel(value == null ? "" : value.toString());
            l.setBorder(new EmptyBorder(10, 12, 10, 12));
            l.setForeground(SUBFG);
            p.add(l, BorderLayout.CENTER);

            // Do not show any arrow for the "Cover" (model index 1) or "Actions" (model index 12) columns
            int modelHeaderCol = table.convertColumnIndexToModel(column);
            if (modelHeaderCol == 1 || modelHeaderCol == 12) {
                return p;
            }

            // Determine which column is sorted and its direction
            Icon arrow;
            RowSorter<? extends TableModel> rs = table.getRowSorter();
            boolean isSortedCol = false;
            boolean ascending = true;
            if (rs != null && !rs.getSortKeys().isEmpty()) {
                RowSorter.SortKey key = rs.getSortKeys().get(0);
                int modelCol = table.convertColumnIndexToModel(column);
                isSortedCol = key.getColumn() == modelCol;
                ascending = key.getSortOrder() == SortOrder.ASCENDING;
            }

            if (isSortedCol) {
                // Red arrow for the sorted column (up/down by sort order)
                arrow = new SortArrowIcon(ascending, RED);
            } else {
                // Default gray triangle for all other columns (neutral: down)
                arrow = new SortArrowIcon(false, new Color(120, 120, 120));
            }

            JLabel arrowLabel = new JLabel(arrow);
            arrowLabel.setOpaque(false);
            arrowLabel.setBorder(new EmptyBorder(0, 0, 0, 2)); // right padding
            p.add(arrowLabel, BorderLayout.EAST);

            return p;
        }
    }
    // Small triangle icon for header sort indication
    private static final class SortArrowIcon implements Icon {
        private final boolean up;
        private final Color color;
        private final int w = 10;
        private final int h = 8;
        SortArrowIcon(boolean up, Color color) {
            this.up = up;
            this.color = color;
        }
        @Override public int getIconWidth() { return w; }
        @Override public int getIconHeight() { return h; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            Polygon tri = new Polygon();
            if (up) {
                tri.addPoint(x + w / 2, y);           // top
                tri.addPoint(x, y + h);               // bottom-left
                tri.addPoint(x + w, y + h);           // bottom-right
            } else {
                tri.addPoint(x, y);                   // top-left
                tri.addPoint(x + w, y);               // top-right
                tri.addPoint(x + w / 2, y + h);       // bottom
            }
            g2.fillPolygon(tri);
            g2.dispose();
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

    // render the RED E for the Explicit
    public static final class ExplicitRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            boolean exp = value instanceof Boolean && (Boolean) value;
            JPanel p = new JPanel(new GridBagLayout());
            p.setOpaque(false);
            if (exp) {
                JLabel pill = new JLabel("E");
                pill.setOpaque(false);
                pill.setFont(pill.getFont().deriveFont(Font.PLAIN, 16f));
                pill.setForeground(new Color(180, 30, 30));

                p.add(pill);
            }
            return p;
        }
    }

    // ---------- Explicit, Rating ----------
    public static final class RatingRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int rating = value instanceof Integer ? (Integer) value : 0;
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 15));
            p.setOpaque(false);
            for (int i = 1; i <= 5; i++) {
                JLabel star = new JLabel(i <= rating ? "★" : "☆");
                // Use Wingdine-based star font
                star.setFont(FontManager.starFont(18f));
                // All stars red
                star.setForeground(RED);
                p.add(star);
            }
            return p;
        }
    }

    // ---------- Explicit, Rating ----------
    public static final class RatingEditor extends AbstractCellEditor implements TableCellEditor {
        private int rating;
        private JPanel panel;

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            rating = value instanceof Integer ? (Integer) value : 0;
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 15));
            panel.setOpaque(false);
            for (int i = 1; i <= 5; i++) {
                final int idx = i;
                JLabel star = new JLabel(i <= rating ? "★" : "☆");
                // Use Wingdine-based star font
                star.setFont(FontManager.starFont(18f));
                // All stars red
                star.setForeground(RED);
                star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                star.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        rating = idx;
                        stopCellEditing();
                    }
                });
                panel.add(star);
            }
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return rating;
        }
    }

    // ---------- Buttons ----------
    public static JButton redButton(String text) { return new RoundedButton(text, RED, RED_HOVER, 18); }
    public static JButton grayButton(String text) { return new RoundedButton(text, new Color(70,70,70), new Color(88,88,88), 18); }
    public static final class RoundedButton extends JButton {
        private final int radius; private final Color base; private final Color hover; private boolean over;
        public RoundedButton(String text, Color base, Color hover, int radius) { super(text); this.radius = radius; this.base = base; this.hover = hover; setFocusPainted(false); setBorder(new EmptyBorder(8, 16, 8, 16)); setContentAreaFilled(false); setOpaque(false); setForeground(Color.WHITE); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { over = true; repaint(); } public void mouseExited(MouseEvent e) { over = false; repaint(); } }); }
        @Override protected void paintComponent(Graphics g) { Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setColor(over ? hover : base); g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius*2, radius*2); g2.dispose(); super.paintComponent(g); }
    }

    // ---------- Rounded scroll pane for tables ----------
    public static class RoundedScrollPane extends JScrollPane {
        private final int radius;
        private final Color bg;
        private final Color borderColor;

        public RoundedScrollPane(Component view, int radius) {
            this(view, radius, PANEL, new Color(60, 60, 60));
        }

        public RoundedScrollPane(Component view, int radius, Color bg, Color borderColor) {
            super(view);
            this.radius = radius;
            this.bg = bg;
            this.borderColor = borderColor;
            setBorder(BorderFactory.createEmptyBorder());
            setOpaque(false);
            getViewport().setOpaque(false);
            // Make column header transparent so rounded top corners are visible
            if (getColumnHeader() != null) {
                getColumnHeader().setOpaque(false);
            }
            setBackground(new Color(0, 0, 0, 0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            // Make the scroll pane fully transparent: no background, no border.
            // Children (viewport/table) will paint themselves.
            // Intentionally do nothing here.
        }
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

}
