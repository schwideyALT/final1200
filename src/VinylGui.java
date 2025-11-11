import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class VinylGui extends JFrame {

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            setSystemDefaults();
            new VinylGui().setVisible(true);
        });
    }
    // Colors
    private static final Color BG = new Color(24, 24, 24);
    private static final Color PANEL = new Color(30, 30, 30);
    private static final Color ALT_ROW = new Color(40, 40, 40);
    private static final Color FG = new Color(230, 230, 230);
    private static final Color SUBFG = new Color(180, 180, 180);
    private static final Color HEADER_BG = new Color(34, 34, 34);
    private static final Color RED = new Color(210, 54, 54);
    private static final Color RED_HOVER = new Color(230, 76, 76);

    private JTable table;
    private SongTableModel model;
    private TableRowSorter<SongTableModel> sorter;
    private JTextField searchField;
    private ColumnManager columnManager;

    // Track hovered row for hover-only Actions button
    private int hoveredRow = -1;

    public static void main(String[] args) {
        launch();
    }

    public VinylGui() {
        super("Music Library");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1180, 740);
        setLocationRelativeTo(null);
        setBackground(BG);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        model = new SongTableModel();
        // Seed a few rows for preview
        model.addSong(new Song(1, "Lost In Tokyo", "Kavinsky", "Nightcall", "Synthwave", 92, 242, true, 4, null));
        model.addSong(new Song(2, "Rose Rouge", "St Germain", "Tourist", "House", 124, 446, false, 5, null));
        model.addSong(new Song(3, "Brazil", "Declan McKenna", "What Do You Think About The Car?", "Indie", 120, 250, false, 3, null));

        table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground((row % 2 == 0) ? PANEL : ALT_ROW);
                } else {
                    c.setBackground(new Color(55, 55, 55));
                }
                c.setForeground(FG);
                return c;
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                return getPreferredSize().width < getParent().getWidth();
            }
        };

        table.setRowHeight(56);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setBackground(PANEL);
        table.setForeground(FG);
        table.setSelectionBackground(new Color(64, 64, 64));
        table.setSelectionForeground(FG);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JTableHeader header = table.getTableHeader();
        header.setBackground(HEADER_BG);
        header.setForeground(SUBFG);
        header.setReorderingAllowed(false);
        header.setOpaque(false);
        header.setDefaultRenderer(new HeaderRenderer());

        // Sorter for all columns
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        // Column sizing
        setColumnWidths();

        // Rounded renderers for text cells
        RoundedCellRenderer leftCell = new RoundedCellRenderer(SwingConstants.LEFT);
        RoundedCellRenderer centerCell = new RoundedCellRenderer(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerCell); // ID
        table.getColumnModel().getColumn(2).setCellRenderer(leftCell);   // Title
        table.getColumnModel().getColumn(3).setCellRenderer(leftCell);   // Artist
        table.getColumnModel().getColumn(4).setCellRenderer(leftCell);   // Album
        table.getColumnModel().getColumn(5).setCellRenderer(leftCell);   // Genre
        table.getColumnModel().getColumn(6).setCellRenderer(centerCell); // BPM center
        table.getColumnModel().getColumn(7).setCellRenderer(centerCell); // Length center

        // Other renderers and editors
        table.getColumnModel().getColumn(1).setCellRenderer(new CoverRenderer());
        table.getColumnModel().getColumn(8).setCellRenderer(new ExplicitRenderer());
        table.getColumnModel().getColumn(9).setCellRenderer(new RatingRenderer());
        table.getColumnModel().getColumn(9).setCellEditor(new RatingEditor());
        table.getColumnModel().getColumn(10).setCellRenderer(new ActionsRenderer());
        table.getColumnModel().getColumn(10).setCellEditor(new ActionsEditor());

        // Hover support for Actions column
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                hoveredRow = viewRow;
                table.repaint();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredRow = -1;
                table.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.rowAtPoint(e.getPoint());
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        Song s = model.getSong(modelRow);
                        openPropertiesDialog(s, false);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        // Hide scrollbars but keep wheel scrolling
        styleScrollBars(scroll);

        JPanel top = buildTopBar();
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);

        JMenuBar bar = buildMenuBar();
        bar.add(buildViewMenu());
        setJMenuBar(bar);
        getContentPane().add(root, BorderLayout.CENTER);

        // Column manager for View menu toggles
        columnManager = new ColumnManager(table);
    }

    private static void setSystemDefaults() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        UIManager.put("Panel.background", new Color(30, 30, 30));
        UIManager.put("OptionPane.background", new Color(30, 30, 30));
        UIManager.put("OptionPane.messageForeground", new Color(230, 230, 230));
        UIManager.put("TextField.background", new Color(40, 40, 40));
        UIManager.put("TextField.foreground", new Color(230, 230, 230));
        UIManager.put("TextField.caretForeground", new Color(230, 230, 230));
        UIManager.put("TextField.border", BorderFactory.createLineBorder(new Color(60, 60, 60)));
        UIManager.put("ComboBox.background", new Color(40, 40, 40));
        UIManager.put("ComboBox.foreground", new Color(230, 230, 230));
        UIManager.put("Spinner.background", new Color(40, 40, 40));
        UIManager.put("Spinner.foreground", new Color(230, 230, 230));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.setBackground(BG);
        bar.setBorder(new EmptyBorder(6, 8, 6, 8));
        JMenu file = new JMenu("File");
        file.setForeground(FG);
        JMenuItem importXml = new JMenuItem("Import XML...");
        JMenuItem exportXml = new JMenuItem("Export XML...");
        JMenuItem exit = new JMenuItem("Exit");

        importXml.addActionListener(e -> doImportXml());
        exportXml.addActionListener(e -> doExportXml());
        exit.addActionListener(e -> dispose());

        file.add(importXml);
        file.add(exportXml);
        file.addSeparator();
        file.add(exit);
        bar.add(file);
        return bar;
    }

    private JMenu buildViewMenu() {
        JMenu view = new JMenu("View");
        view.setForeground(FG);
        String[] names = {"ID","Cover","Title","Artist","Album","Genre","BPM","Length","Explicit","Rating","Actions"};
        for (int i = 0; i < names.length; i++) {
            final int modelIndex = i;
            JCheckBoxMenuItem item = new JCheckBoxMenuItem("Show " + names[i], true);
            item.addActionListener(e -> {
                boolean show = item.isSelected();
                columnManager.setVisible(modelIndex, show);
            });
            view.add(item);
        }
        return view;
    }

    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);

        JButton addBtn = redButton("Add");
        addBtn.addActionListener(e -> openPropertiesDialog(new Song(), true));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setBackground(BG);
        left.add(addBtn);

        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(320, 34));
        searchField.putClientProperty("JTextField.placeholderText", "Search");
        searchField.setBorder(new EmptyBorder(6, 10, 6, 10));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setBackground(BG);
        right.add(searchField);

        top.setBorder(new EmptyBorder(0, 0, 10, 0));
        top.add(left, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        return top;
    }

    // ----- Buttons
    private JButton redButton(String text) { return new RoundedButton(text, RED, RED_HOVER, 18); }
    private JButton grayButton(String text) { return new RoundedButton(text, new Color(70,70,70), new Color(88,88,88), 18); }

    static class RoundedButton extends JButton {
        private final int radius;
        private final Color base;
        private final Color hover;
        private boolean over;
        RoundedButton(String text, Color base, Color hover, int radius) {
            super(text);
            this.radius = radius;
            this.base = base;
            this.hover = hover;
            setFocusPainted(false);
            setBorder(new EmptyBorder(8, 16, 8, 16));
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(over ? hover : base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius*2, radius*2);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private void applyFilter() {
        String q = searchField.getText();
        if (q == null || q.trim().isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            String s = q.trim().toLowerCase();
            sorter.setRowFilter(new RowFilter<SongTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends SongTableModel, ? extends Integer> entry) {
                    Song song = model.getSong(entry.getIdentifier());
                    return song.matches(s);
                }
            });
        }
    }

    private void setColumnWidths() {
        TableColumnModel cols = table.getColumnModel();
        setPrefWidth(cols.getColumn(0), 60);   // ID
        setPrefWidth(cols.getColumn(1), 64);   // Cover
        setPrefWidth(cols.getColumn(2), 260);  // Title
        setPrefWidth(cols.getColumn(3), 180);  // Artist
        setPrefWidth(cols.getColumn(4), 200);  // Album
        setPrefWidth(cols.getColumn(5), 140);  // Genre
        setPrefWidth(cols.getColumn(6), 90);   // BPM
        setPrefWidth(cols.getColumn(7), 100);  // Length
        setPrefWidth(cols.getColumn(8), 84);   // Explicit
        setPrefWidth(cols.getColumn(9), 140);  // Rating
        setPrefWidth(cols.getColumn(10), 100); // Actions
    }

    private void setPrefWidth(TableColumn col, int w) {
        col.setPreferredWidth(w);
        col.setMinWidth(40);
    }

    private void doImportXml() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import songs.xml");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                List<Song> songs = XmlStore.load(f);
                model.setSongs(songs);
            } catch (Exception ex) {
                showError("Failed to import XML: " + ex.getMessage());
            }
        }
    }

    private void doExportXml() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export songs.xml");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                XmlStore.save(f, model.getAll());
            } catch (Exception ex) {
                showError("Failed to export XML: " + ex.getMessage());
            }
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void openPropertiesDialog(Song song, boolean isNew) {
        PropertiesDialog dlg = new PropertiesDialog(this, song, isNew);
        dlg.setVisible(true);
        if (dlg.saved) {
            if (isNew) {
                if (song.id <= 0) song.id = model.nextId();
                model.addSong(song);
            } else {
                int idx = model.indexOf(song);
                if (idx >= 0) model.fireTableRowsUpdated(idx, idx);
            }
        }
    }

    // ---------- Data model ----------
    static class Song {
        int id;
        String title = "";
        String artist = "";
        String album = "";
        String genre = "";
        int bpm = 0;
        int lengthSeconds = 0; // total seconds
        boolean explicit = false;
        int rating = 0; // 0..5
        String coverPath; // may be null
        transient ImageIcon coverIcon; // cached scaled icon

        Song() {}
        Song(int id, String title, String artist, String album, String genre, int bpm, int lengthSeconds, boolean explicit, int rating, String coverPath) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.genre = genre;
            this.bpm = bpm;
            this.lengthSeconds = lengthSeconds;
            this.explicit = explicit;
            this.rating = rating;
            this.coverPath = coverPath;
        }

        boolean matches(String q) {
            String all = (title + " " + artist + " " + album + " " + genre + " " + id + " " + bpm + " " + formatDuration(lengthSeconds)).toLowerCase();
            return all.contains(q);
        }

        static String formatDuration(int secs) {
            int m = secs / 60;
            int s = secs % 60;
            return String.format("%d:%02d", m, s);
        }
        static int parseDuration(String mmss) {
            String t = mmss.trim();
            if (t.isEmpty()) return 0;
            String[] parts = t.split(":");
            if (parts.length == 1) {
                return Integer.parseInt(parts[0]);
            } else {
                int m = Integer.parseInt(parts[0]);
                int s = Integer.parseInt(parts[1]);
                if (s < 0 || s > 59) throw new IllegalArgumentException("Seconds must be 00..59");
                return m * 60 + s;
            }
        }

        ImageIcon getScaledCover(int size) {
            try {
                if (coverPath == null || coverPath.isEmpty()) return placeholderIcon(size, size);
                if (coverIcon != null && coverIcon.getIconWidth() == size && coverIcon.getIconHeight() == size) {
                    return coverIcon;
                }
                BufferedImage img = ImageIO.read(new File(coverPath));
                if (img == null) return placeholderIcon(size, size);
                Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
                coverIcon = new ImageIcon(scaled);
                return coverIcon;
            } catch (Exception e) {
                return placeholderIcon(size, size);
            }
        }

        private static ImageIcon placeholderIcon(int w, int h) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(60, 60, 60));
            g.fillRoundRect(0, 0, w, h, 8, 8);
            g.setColor(new Color(90, 90, 90));
            int pad = w / 5;
            g.fillRoundRect(pad, pad, w - pad * 2, h - pad * 2, 6, 6);
            g.dispose();
            return new ImageIcon(img);
        }
    }

    static class SongTableModel extends AbstractTableModel {
        private final String[] cols = {"ID", "Cover", "Title", "Artist", "Album", "Genre", "BPM", "Length", "Explicit", "Rating", "Actions"};
        private final Class<?>[] types = {Integer.class, ImageIcon.class, String.class, String.class, String.class, String.class, Integer.class, String.class, Boolean.class, Integer.class, Object.class};
        private final java.util.List<Song> rows = new ArrayList<>();

        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        public Class<?> getColumnClass(int c) { return types[c]; }
        public boolean isCellEditable(int r, int c) { return c == 9 || c == 10; }

        public Object getValueAt(int r, int c) {
            Song s = rows.get(r);
            switch (c) {
                case 0: return s.id;
                case 1: return s.getScaledCover(44);
                case 2: return s.title;
                case 3: return s.artist;
                case 4: return s.album;
                case 5: return s.genre;
                case 6: return s.bpm;
                case 7: return Song.formatDuration(s.lengthSeconds);
                case 8: return s.explicit;
                case 9: return s.rating;
                case 10: return ""; // Actions column
            }
            return null;
        }

        public void setValueAt(Object val, int r, int c) {
            Song s = rows.get(r);
            if (c == 9 && val instanceof Integer) s.rating = (Integer) val;
        }

        void addSong(Song s) {
            rows.add(s);
            int i = rows.size() - 1;
            fireTableRowsInserted(i, i);
        }

        void removeAt(int modelRow) {
            if (modelRow >= 0 && modelRow < rows.size()) {
                rows.remove(modelRow);
                fireTableRowsDeleted(modelRow, modelRow);
            }
        }

        int indexOf(Song s) { return rows.indexOf(s); }
        Song getSong(int modelRow) { return rows.get(modelRow); }
        java.util.List<Song> getAll() { return new ArrayList<>(rows); }
        void setSongs(java.util.List<Song> list) {
            rows.clear();
            rows.addAll(list);
            fireTableDataChanged();
        }
        int nextId() {
            int max = 0;
            for (Song s : rows) max = Math.max(max, s.id);
            return max + 1;
        }
    }

    // ---------- Renderers and editors ----------
    static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() { setHorizontalAlignment(LEFT); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
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

    static class RoundedCellRenderer extends DefaultTableCellRenderer {
        private final int align;
        RoundedCellRenderer(int align) { this.align = align; }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setHorizontalAlignment(align);
            setText(value == null ? "" : value.toString());
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setForeground(FG);
            setOpaque(false);
            JPanel p = new JPanel(new BorderLayout()) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color base = (row % 2 == 0) ? PANEL : ALT_ROW;
                    if (table.getSelectionModel().isSelectedIndex(row)) base = new Color(55,55,55);
                    g2.setColor(base);
                    g2.fillRoundRect(6, 6, getWidth()-12, getHeight()-12, 16, 16);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            JLabel l = new JLabel(getText(), getIcon(), align);
            l.setForeground(FG);
            l.setBorder(new EmptyBorder(8, 12, 8, 12));
            p.add(l, BorderLayout.CENTER);
            return p;
        }
    }

    class CoverRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JPanel p = new JPanel(new GridBagLayout()) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color base = (row % 2 == 0) ? PANEL : ALT_ROW;
                    if (table.getSelectionModel().isSelectedIndex(row)) base = new Color(55,55,55);
                    g2.setColor(base);
                    g2.fillRoundRect(6, 6, getWidth()-12, getHeight()-12, 16, 16);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            JLabel l = new JLabel();
            l.setOpaque(false);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.setBorder(new EmptyBorder(6, 0, 6, 0));
            if (value instanceof ImageIcon) l.setIcon((ImageIcon) value);
            p.add(l);
            return p;
        }
    }

    static class ExplicitRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            boolean exp = value instanceof Boolean && (Boolean) value;
            JPanel p = roundedCellBackground(table, row);
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
        static JPanel roundedCellBackground(JTable table, int row) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color base = (row % 2 == 0) ? new Color(30,30,30) : new Color(40,40,40);
                    if (table.getSelectionModel().isSelectedIndex(row)) base = new Color(55,55,55);
                    g2.setColor(base);
                    g2.fillRoundRect(6, 6, getWidth()-12, getHeight()-12, 16, 16);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            return p;
        }
    }

    static class RatingRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int rating = value instanceof Integer ? (Integer) value : 0;
            JPanel p = ExplicitRenderer.roundedCellBackground(table, row);
            p.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 0));
            for (int i = 1; i <= 5; i++) {
                JLabel star = new JLabel(i <= rating ? "★" : "☆");
                star.setForeground(new Color(255, 204, 64));
                star.setFont(star.getFont().deriveFont(Font.PLAIN, 18f));
                p.add(star);
            }
            return p;
        }
    }

    static class RatingEditor extends AbstractCellEditor implements TableCellEditor {
        private int rating;
        private JPanel panel;

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            rating = value instanceof Integer ? (Integer) value : 0;
            panel = ExplicitRenderer.roundedCellBackground(table, row);
            panel.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 0));
            for (int i = 1; i <= 5; i++) {
                final int idx = i;
                JLabel star = new JLabel(i <= rating ? "★" : "☆");
                star.setForeground(new Color(255, 204, 64));
                star.setFont(star.getFont().deriveFont(Font.PLAIN, 18f));
                star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                star.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) { rating = idx; stopCellEditing(); }
                });
                panel.add(star);
            }
            return panel;
        }

        public Object getCellEditorValue() { return rating; }
    }

    class ActionsRenderer implements TableCellRenderer {
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(40,40,40));
                g2.fillRoundRect(6, 6, getWidth()-12, getHeight()-12, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        private final JButton trash = makeTrashButton();
        ActionsRenderer() { holder.setOpaque(false); holder.add(trash); }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            boolean show = (row == hoveredRow);
            trash.setVisible(show);
            return holder;
        }
    }

    class ActionsEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(55,55,55));
                g2.fillRoundRect(6, 6, getWidth()-12, getHeight()-12, 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        private final JButton trash = makeTrashButton();
        private int editingRow = -1;
        ActionsEditor() {
            holder.setOpaque(false);
            holder.add(trash);
            trash.addActionListener(e -> {
                if (editingRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(editingRow);
                    model.removeAt(modelRow);
                    cancelCellEditing();
                }
            });
        }
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            editingRow = row;
            return holder;
        }
        public Object getCellEditorValue() { return null; }
    }

    private JButton makeTrashButton() {
        JButton b = new JButton(trashIcon(18, 18));
        b.setToolTipText("Delete song");
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(70, 70, 70));
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(6, 12, 6, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setContentAreaFilled(false);
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(88, 88, 88)); }
            public void mouseExited(MouseEvent e) { b.setBackground(new Color(70, 70, 70)); }
        });
        return b;
    }

    private static Icon trashIcon(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(240, 240, 240));
        int binW = w - 6;
        int x = 3;
        int y = 5;
        g.fillRoundRect(x, y, binW, 4, 3, 3);           // lid
        g.fillRoundRect(x + 1, y + 4, binW - 2, h - y - 6, 3, 3); // body
        g.fillRoundRect(x + binW / 2 - 3, 2, 6, 3, 2, 2);         // handle
        g.dispose();
        return new ImageIcon(img);
    }

    private void styleScrollBars(JScrollPane sp) {
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.getVerticalScrollBar().setUI(new MinimalScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new MinimalScrollBarUI());
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getHorizontalScrollBar().setOpaque(false);
        sp.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, new JPanel(){ { setOpaque(false);} });
    }

    static class MinimalScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {}
        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {}
        @Override
        protected Dimension getMinimumThumbSize() { return new Dimension(0, 0); }
        @Override
        protected Dimension getMaximumThumbSize() { return new Dimension(0, 0); }
        @Override
        protected JButton createDecreaseButton(int orientation) { return zero(); }
        @Override
        protected JButton createIncreaseButton(int orientation) { return zero(); }
        private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0,0)); b.setOpaque(false); b.setContentAreaFilled(false); b.setBorder(null); return b; }
        @Override
        public Dimension getPreferredSize(JComponent c) { return new Dimension(0,0); }
    }

    // ---------- Properties dialog ----------
    class PropertiesDialog extends JDialog {
        boolean saved = false;
        private final Song song;

        private JTextField idField;
        private JTextField titleField;
        private JTextField artistField;
        private JTextField albumField;
        private JTextField genreField;
        private IntField bpmField;
        private JTextField lengthField; // mm:ss
        private JCheckBox explicitBox;
        private StarBar ratingBar;

        private JLabel coverPreview;
        private String selectedCoverPath;

        PropertiesDialog(Frame owner, Song song, boolean isNew) {
            super(owner, isNew ? "Add Song" : "Song Properties", true);
            this.song = song;
            this.selectedCoverPath = song.coverPath;

            setSize(720, 540);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("General", buildGeneralTab());
            tabs.addTab("Cover", buildCoverTab());

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            actions.setBackground(BG);
            JButton cancel = grayButton("Cancel");
            JButton save = redButton("Save");
            cancel.addActionListener(e -> dispose());
            save.addActionListener(e -> { if (applyChanges()) { saved = true; dispose(); } });
            actions.add(cancel);
            actions.add(save);

            add(tabs, BorderLayout.CENTER);
            add(actions, BorderLayout.SOUTH);
        }

        private JPanel buildGeneralTab() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(PANEL);
            p.setBorder(new EmptyBorder(16,16,16,16));

            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(8, 8, 8, 8);
            gc.anchor = GridBagConstraints.WEST;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1.0;

            idField = textField(String.valueOf(song.id));
            titleField = textField(song.title);
            artistField = textField(song.artist);
            albumField = textField(song.album);
            genreField = textField(song.genre);
            bpmField = new IntField(String.valueOf(song.bpm));
            lengthField = textField(Song.formatDuration(song.lengthSeconds));
            explicitBox = new JCheckBox("Explicit");
            explicitBox.setSelected(song.explicit);
            explicitBox.setForeground(FG);
            explicitBox.setBackground(PANEL);
            ratingBar = new StarBar(song.rating);

            int r = 0;
            addRow(p, gc, r++, "ID", idField);
            addRow(p, gc, r++, "Title", titleField);
            addRow(p, gc, r++, "Artist", artistField);
            addRow(p, gc, r++, "Album", albumField);
            addRow(p, gc, r++, "Genre", genreField);
            addRow(p, gc, r++, "BPM", bpmField);
            addRow(p, gc, r++, "Length (mm:ss)", lengthField);
            addRow(p, gc, r++, "Flags", explicitBox);
            addRow(p, gc, r++, "Rating", ratingBar);

            return p;
        }

        private JPanel buildCoverTab() {
            JPanel p = new JPanel(new BorderLayout());
            p.setBackground(PANEL);
            p.setBorder(new EmptyBorder(16,16,16,16));

            coverPreview = new JLabel();
            coverPreview.setHorizontalAlignment(SwingConstants.CENTER);
            coverPreview.setOpaque(true);
            coverPreview.setBackground(new Color(36, 36, 36));
            coverPreview.setPreferredSize(new Dimension(300, 300));
            updateCoverPreview();

            JButton choose = redButton("Choose Image");
            choose.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Choose cover image");
                fc.setFileFilter(new FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "gif"));
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File f = fc.getSelectedFile();
                    selectedCoverPath = f.getAbsolutePath();
                    updateCoverPreview();
                }
            });

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            bottom.setBackground(PANEL);
            bottom.add(choose);

            p.add(coverPreview, BorderLayout.CENTER);
            p.add(bottom, BorderLayout.SOUTH);
            return p;
        }

        private void updateCoverPreview() {
            ImageIcon icon;
            if (selectedCoverPath == null || selectedCoverPath.isEmpty()) {
                icon = Song.placeholderIcon(280, 280);
            } else {
                try {
                    BufferedImage img = ImageIO.read(new File(selectedCoverPath));
                    Image scaled = img.getScaledInstance(280, 280, Image.SCALE_SMOOTH);
                    icon = new ImageIcon(scaled);
                } catch (Exception ex) {
                    icon = Song.placeholderIcon(280, 280);
                }
            }
            coverPreview.setIcon(icon);
        }

        private boolean applyChanges() {
            try {
                int id = 0;
                String idTxt = idField.getText().trim();
                if (!idTxt.isEmpty()) id = Integer.parseInt(idTxt);
                song.id = id;
                song.title = titleField.getText().trim();
                song.artist = artistField.getText().trim();
                song.album = albumField.getText().trim();
                song.genre = genreField.getText().trim();
                song.bpm = Integer.parseInt(bpmField.getText().trim());
                song.lengthSeconds = Song.parseDuration(lengthField.getText());
                song.explicit = explicitBox.isSelected();
                song.rating = ratingBar.getValue();
                song.coverPath = selectedCoverPath;
                song.coverIcon = null;
                return true;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage());
                return false;
            }
        }

        private JTextField textField(String text) {
            JTextField tf = new JTextField(text);
            tf.setBackground(new Color(40,40,40));
            tf.setForeground(FG);
            tf.setCaretColor(FG);
            tf.setBorder(new EmptyBorder(8,10,8,10));
            return tf;
        }

        private void addRow(JPanel p, GridBagConstraints gc, int row, String label, Component field) {
            gc.gridx = 0; gc.gridy = row; gc.weightx = 0; gc.gridwidth = 1;
            JLabel l = new JLabel(label);
            l.setForeground(SUBFG);
            p.add(l, gc);
            gc.gridx = 1; gc.weightx = 1; gc.gridwidth = 2;
            p.add(field, gc);
        }
    }

    // Int-only text field
    static class IntField extends JTextField {
        IntField(String text) {
            super(text);
            setBackground(new Color(40,40,40));
            setForeground(FG);
            setCaretColor(FG);
            setBorder(new EmptyBorder(8,10,8,10));
            ((AbstractDocument) getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                    if (string == null) return;
                    if (string.chars().allMatch(Character::isDigit)) super.insertString(fb, offset, string, attr);
                }
                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                    if (text == null) return;
                    if (text.chars().allMatch(Character::isDigit)) super.replace(fb, offset, length, text, attrs);
                }
            });
        }
    }

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
                star.setFont(star.getFont().deriveFont(Font.PLAIN, 22f));
                star.setForeground(new Color(255,204,64));
                star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                star.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { setValue(idx); } });
                stars.add(star);
                add(star);
            }
            refresh();
        }
        void setValue(int v) { value = Math.max(0, Math.min(5, v)); refresh(); }
        int getValue() { return value; }
        private void refresh() {
            for (int i = 0; i < stars.size(); i++) stars.get(i).setText(i < value ? "★" : "☆");
        }
    }

    // Column show/hide manager
    static class ColumnManager {
        private final JTable table;
        private final TableColumnModel tcm;
        private final Map<Integer, TableColumn> columnsByModelIndex = new HashMap<>();
        private final boolean[] visible;
        ColumnManager(JTable table) {
            this.table = table;
            this.tcm = table.getColumnModel();
            int count = table.getModel().getColumnCount();
            visible = new boolean[count];
            for (int v = 0; v < tcm.getColumnCount(); v++) {
                TableColumn tc = tcm.getColumn(v);
                int m = tc.getModelIndex();
                columnsByModelIndex.put(m, tc);
                visible[m] = true;
            }
        }
        void setVisible(int modelIndex, boolean show) {
            if (modelIndex < 0 || modelIndex >= visible.length) return;
            if (show == visible[modelIndex]) return;
            if (!show) {
                // remove if present
                for (int v = 0; v < tcm.getColumnCount(); v++) {
                    if (tcm.getColumn(v).getModelIndex() == modelIndex) {
                        TableColumn tc = tcm.getColumn(v);
                        tcm.removeColumn(tc);
                        break;
                    }
                }
                visible[modelIndex] = false;
            } else {
                // add back at the correct position based on model index order
                TableColumn tc = columnsByModelIndex.get(modelIndex);
                if (tc == null) return;
                tcm.addColumn(tc);
                int currentIndex = tcm.getColumnCount() - 1; // added at end
                int insertAt = 0;
                for (int v = 0; v < tcm.getColumnCount(); v++) {
                    if (v == currentIndex) continue;
                    int m = tcm.getColumn(v).getModelIndex();
                    if (m < modelIndex) insertAt++;
                }
                tcm.moveColumn(currentIndex, insertAt);
                visible[modelIndex] = true;
            }
            table.getTableHeader().revalidate();
            table.getTableHeader().repaint();
        }
    }

    // ---------- XML store ----------
    static class XmlStore {
        static void save(File file, List<Song> songs) throws Exception {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element root = doc.createElement("songs");
            doc.appendChild(root);
            for (Song s : songs) {
                Element e = doc.createElement("song");
                e.setAttribute("id", String.valueOf(s.id));
                append(doc, e, "title", s.title);
                append(doc, e, "artist", s.artist);
                append(doc, e, "album", s.album);
                append(doc, e, "genre", s.genre);
                append(doc, e, "bpm", String.valueOf(s.bpm));
                append(doc, e, "length", String.valueOf(s.lengthSeconds));
                append(doc, e, "explicit", String.valueOf(s.explicit));
                append(doc, e, "rating", String.valueOf(s.rating));
                append(doc, e, "cover", s.coverPath == null ? "" : s.coverPath);
                root.appendChild(e);
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            t.transform(new DOMSource(doc), new StreamResult(file));
        }

        static List<Song> load(File file) throws Exception {
            List<Song> out = new ArrayList<>();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            NodeList songNodes = doc.getElementsByTagName("song");
            for (int i = 0; i < songNodes.getLength(); i++) {
                Element e = (Element) songNodes.item(i);
                Song s = new Song();
                s.id = parseIntSafe(e.getAttribute("id"));
                s.title = textOf(e, "title");
                s.artist = textOf(e, "artist");
                s.album = textOf(e, "album");
                s.genre = textOf(e, "genre");
                s.bpm = parseIntSafe(textOf(e, "bpm"));
                s.lengthSeconds = parseIntSafe(textOf(e, "length"));
                s.explicit = Boolean.parseBoolean(textOf(e, "explicit"));
                s.rating = parseIntSafe(textOf(e, "rating"));
                s.coverPath = textOf(e, "cover");
                out.add(s);
            }
            return out;
        }

        private static void append(Document doc, Element parent, String name, String value) {
            Element n = doc.createElement(name);
            n.appendChild(doc.createTextNode(value == null ? "" : value));
            parent.appendChild(n);
        }
        private static int parseIntSafe(String s) {
            try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
        }
        private static String textOf(Element e, String tag) {
            NodeList nl = e.getElementsByTagName(tag);
            if (nl.getLength() == 0) return "";
            return nl.item(0).getTextContent();
        }
    }
}
