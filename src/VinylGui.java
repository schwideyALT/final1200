import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

// Swing frame using Vinyl (domain) and VinylUiKit (UI widgets).
public class VinylGui extends JFrame {

    // ---------- Launch ----------
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            VinylUiKit.setSystemDefaults();
            new VinylGui().setVisible(true);
        });
    }

    // ---------- Colors (via static import-like referencing) ----------
    private static final Color BG = VinylUiKit.BG;
    private static final Color PANEL = VinylUiKit.PANEL;
    private static final Color FG = VinylUiKit.FG;
    private static final Color SUBFG = VinylUiKit.SUBFG;
    private static final Color HEADER_BG = VinylUiKit.HEADER_BG;
    private static final Color RED = VinylUiKit.RED;
    private static final Color RED_HOVER = VinylUiKit.RED_HOVER;

    private JTable table;
    private Vinyl.SongTableModel model;
    private TableRowSorter<Vinyl.SongTableModel> sorter;
    private JTextField searchField;
    private ColumnManager columnManager;

    // Track hovered row for hover-only Actions button
    private int hoveredRow = -1;

    public VinylGui() {
        super("Music Library");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1180, 740);
        setLocationRelativeTo(null);
        setBackground(BG);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        model = new Vinyl.SongTableModel();
        // Seed preview rows
        model.addSong(new Vinyl.Song(1, "Lost In Tokyo", "Kavinsky", "Nightcall", "Synthwave", 92, 242, true, 4, null));
        model.addSong(new Vinyl.Song(2, "Rose Rouge", "St Germain", "Tourist", "House", 124, 446, false, 5, null));
        model.addSong(new Vinyl.Song(3, "Brazil", "Declan McKenna", "What Do You Think About The Car?", "Indie", 120, 250, false, 3, null));

        // Column-banded table so each column reads as a single connected block
        table = new VinylUiKit.ColumnBandTable(model);
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
        header.setDefaultRenderer(new VinylUiKit.HeaderRenderer());

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        setColumnWidths();

        // Transparent text renderers so backgrounds align vertically per column
        VinylUiKit.TextCellRenderer leftCell = new VinylUiKit.TextCellRenderer(SwingConstants.LEFT);
        VinylUiKit.TextCellRenderer centerCell = new VinylUiKit.TextCellRenderer(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerCell); // ID
        table.getColumnModel().getColumn(2).setCellRenderer(leftCell);   // Title
        table.getColumnModel().getColumn(3).setCellRenderer(leftCell);   // Artist
        table.getColumnModel().getColumn(4).setCellRenderer(leftCell);   // Album
        table.getColumnModel().getColumn(5).setCellRenderer(leftCell);   // Genre
        table.getColumnModel().getColumn(6).setCellRenderer(centerCell); // BPM
        table.getColumnModel().getColumn(7).setCellRenderer(centerCell); // Length

        // Other renderers and editors
        table.getColumnModel().getColumn(1).setCellRenderer(new VinylUiKit.CoverRenderer());
        table.getColumnModel().getColumn(8).setCellRenderer(new VinylUiKit.ExplicitRenderer());
        table.getColumnModel().getColumn(9).setCellRenderer(new VinylUiKit.RatingRenderer());
        table.getColumnModel().getColumn(9).setCellEditor(new VinylUiKit.RatingEditor());
        table.getColumnModel().getColumn(10).setCellRenderer(new ActionsRenderer());
        table.getColumnModel().getColumn(10).setCellEditor(new ActionsEditor());

        // Hover support for Actions column
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hoveredRow = table.rowAtPoint(e.getPoint()); table.repaint(); }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { hoveredRow = -1; table.repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.rowAtPoint(e.getPoint());
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        Vinyl.Song s = model.getSong(modelRow);
                        openPropertiesDialog(s, false);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        // Minimal scrollbars
        scroll.getVerticalScrollBar().setUI(new VinylUiKit.MinimalScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new VinylUiKit.MinimalScrollBarUI());
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getHorizontalScrollBar().setOpaque(false);
        scroll.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, new JPanel(){ { setOpaque(false);} });

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

        columnManager = new ColumnManager(table);
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
        file.add(importXml); file.add(exportXml); file.addSeparator(); file.add(exit);
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
            item.addActionListener(e -> columnManager.setVisible(modelIndex, item.isSelected()));
            view.add(item);
        }
        return view;
    }

    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);

        JButton addBtn = VinylUiKit.redButton("Add");
        addBtn.addActionListener(e -> openPropertiesDialog(new Vinyl.Song(), true));

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

    private void applyFilter() {
        String q = searchField.getText();
        if (q == null || q.trim().isEmpty()) sorter.setRowFilter(null);
        else {
            String s = q.trim().toLowerCase();
            sorter.setRowFilter(new RowFilter<Vinyl.SongTableModel, Integer>() {
                @Override public boolean include(Entry<? extends Vinyl.SongTableModel, ? extends Integer> entry) {
                    Vinyl.Song song = model.getSong(entry.getIdentifier());
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
    private void setPrefWidth(TableColumn col, int w) { col.setPreferredWidth(w); col.setMinWidth(40); }

    private void doImportXml() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import songs.xml");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                java.util.List<Vinyl.Song> songs = Vinyl.XmlStore.load(f);
                model.setSongs(songs);
            } catch (Exception ex) { showError("Failed to import XML: " + ex.getMessage()); }
        }
    }
    private void doExportXml() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export songs.xml");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try { Vinyl.XmlStore.save(f, model.getAll()); }
            catch (Exception ex) { showError("Failed to export XML: " + ex.getMessage()); }
        }
    }
    private void showError(String msg) { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); }

    private void openPropertiesDialog(Vinyl.Song song, boolean isNew) {
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

    // ---------- Actions column ----------
    class ActionsRenderer implements TableCellRenderer {
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        private final JButton trash = makeTrashButton();
        ActionsRenderer() { holder.setOpaque(false); holder.add(trash); }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            boolean show = (row == hoveredRow);
            trash.setVisible(show);
            return holder;
        }
    }
    class ActionsEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
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
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) { editingRow = row; return holder; }
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

    private static ImageIcon loadIcon(String path, int w, int h) {
        try {
            if (path == null || path.trim().isEmpty()) {
                return Vinyl.Song.placeholderIcon(w, h);
            }
            BufferedImage img = javax.imageio.ImageIO.read(new File(path));
            if (img == null) {
                return Vinyl.Song.placeholderIcon(w, h);
            }
            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (IOException ioe) {
            return Vinyl.Song.placeholderIcon(w, h);
        }
    }

    private static Icon trashIcon(int w, int h) {
        ImageIcon icon = VinylUiKit.loadIcon("/icons/trash.png", w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int rgb = img.getRGB(x, y);
                if ((rgb & 0xFF000000) != 0) {
                    img.setRGB(x, y, 0xFFFFFFFF);
                }
            }
        }
        g.dispose();
        return new ImageIcon(img);
    }

    // Safe image loader with fallback and scaling




    // ---------- Column show/hide manager ----------
    static class ColumnManager {
        private final JTable table; private final TableColumnModel tcm; private final Map<Integer, TableColumn> byModel = new HashMap<>(); private final boolean[] visible;
        ColumnManager(JTable table) {
            this.table = table; this.tcm = table.getColumnModel();
            int count = table.getModel().getColumnCount();
            visible = new boolean[count];
            for (int v = 0; v < tcm.getColumnCount(); v++) {
                TableColumn tc = tcm.getColumn(v); int m = tc.getModelIndex(); byModel.put(m, tc); visible[m] = true;
            }
        }
        void setVisible(int modelIndex, boolean show) {
            if (modelIndex < 0 || modelIndex >= visible.length) return;
            if (show == visible[modelIndex]) return;
            if (!show) {
                for (int v = 0; v < tcm.getColumnCount(); v++) {
                    if (tcm.getColumn(v).getModelIndex() == modelIndex) { TableColumn tc = tcm.getColumn(v); tcm.removeColumn(tc); break; }
                }
                visible[modelIndex] = false;
            } else {
                TableColumn tc = byModel.get(modelIndex); if (tc == null) return; tcm.addColumn(tc);
                int currentIndex = tcm.getColumnCount() - 1; int insertAt = 0;
                for (int v = 0; v < tcm.getColumnCount(); v++) { if (v == currentIndex) continue; int m = tcm.getColumn(v).getModelIndex(); if (m < modelIndex) insertAt++; }
                tcm.moveColumn(currentIndex, insertAt); visible[modelIndex] = true;
            }
            table.getTableHeader().revalidate(); table.getTableHeader().repaint();
        }
    }

    // ---------- Properties dialog ----------
    class PropertiesDialog extends JDialog {
        boolean saved = false; private final Vinyl.Song song;
        private JTextField idField, titleField, artistField, albumField, genreField, lengthField; // length mm:ss
        private IntField bpmField; private JCheckBox explicitBox; private StarBar ratingBar;
        private JLabel coverPreview; private String selectedCoverPath;

        PropertiesDialog(Frame owner, Vinyl.Song song, boolean isNew) {
            super(owner, isNew ? "Add Song" : "Song Properties", true);
            this.song = song; this.selectedCoverPath = song.coverPath;
            setSize(720, 540); setLocationRelativeTo(owner); setLayout(new BorderLayout());
            JTabbedPane tabs = new JTabbedPane(); tabs.addTab("General", buildGeneralTab()); tabs.addTab("Cover", buildCoverTab());
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setBackground(BG);
            JButton cancel = VinylUiKit.grayButton("Cancel"); JButton save = VinylUiKit.redButton("Save");
            cancel.addActionListener(e -> dispose());
            save.addActionListener(e -> { if (applyChanges()) { saved = true; dispose(); } });
            actions.add(cancel); actions.add(save);
            add(tabs, BorderLayout.CENTER); add(actions, BorderLayout.SOUTH);
        }
        private JPanel buildGeneralTab() {
            JPanel p = new JPanel(new GridBagLayout()); p.setBackground(PANEL); p.setBorder(new EmptyBorder(16,16,16,16));
            GridBagConstraints gc = new GridBagConstraints(); gc.insets = new Insets(8,8,8,8); gc.anchor = GridBagConstraints.WEST; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;
            idField = textField(String.valueOf(song.id)); titleField = textField(song.title); artistField = textField(song.artist); albumField = textField(song.album); genreField = textField(song.genre);
            bpmField = new IntField(String.valueOf(song.bpm)); lengthField = textField(Vinyl.Song.formatDuration(song.lengthSeconds));
            explicitBox = new JCheckBox("Explicit"); explicitBox.setSelected(song.explicit); explicitBox.setForeground(FG); explicitBox.setBackground(PANEL);
            ratingBar = new StarBar(song.rating);
            int r = 0; addRow(p, gc, r++, "ID", idField); addRow(p, gc, r++, "Title", titleField); addRow(p, gc, r++, "Artist", artistField); addRow(p, gc, r++, "Album", albumField);
            addRow(p, gc, r++, "Genre", genreField); addRow(p, gc, r++, "BPM", bpmField); addRow(p, gc, r++, "Length (mm:ss)", lengthField); addRow(p, gc, r++, "Flags", explicitBox); addRow(p, gc, r++, "Rating", ratingBar);
            return p;
        }
        private JPanel buildCoverTab() {
            JPanel p = new JPanel(new BorderLayout()); p.setBackground(PANEL); p.setBorder(new EmptyBorder(16,16,16,16));
            coverPreview = new JLabel(); coverPreview.setHorizontalAlignment(SwingConstants.CENTER); coverPreview.setOpaque(true); coverPreview.setBackground(new Color(36,36,36)); coverPreview.setPreferredSize(new Dimension(300,300)); updateCoverPreview();
            JButton choose = VinylUiKit.redButton("Choose Image");
            choose.addActionListener(e -> { JFileChooser fc = new JFileChooser(); fc.setDialogTitle("Choose cover image"); fc.setFileFilter(new FileNameExtensionFilter("Images", "jpg","jpeg","png","gif")); if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { File f = fc.getSelectedFile(); selectedCoverPath = f.getAbsolutePath(); updateCoverPreview(); }});
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bottom.setBackground(PANEL); bottom.add(choose);
            p.add(coverPreview, BorderLayout.CENTER); p.add(bottom, BorderLayout.SOUTH); return p;
        }
        private void updateCoverPreview() {
            ImageIcon icon;
            if (selectedCoverPath == null || selectedCoverPath.isEmpty()) icon = Vinyl.Song.placeholderIcon(280, 280);
            else {
                try { java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new File(selectedCoverPath)); Image scaled = img.getScaledInstance(280, 280, Image.SCALE_SMOOTH); icon = new ImageIcon(scaled); }
                catch (Exception ex) { icon = Vinyl.Song.placeholderIcon(280, 280); }
            }
            coverPreview.setIcon(icon);
        }
        private boolean applyChanges() {
            try {
                int id = 0; String idTxt = idField.getText().trim(); if (!idTxt.isEmpty()) id = Integer.parseInt(idTxt);
                song.id = id; song.title = titleField.getText().trim(); song.artist = artistField.getText().trim(); song.album = albumField.getText().trim(); song.genre = genreField.getText().trim();
                song.bpm = Integer.parseInt(bpmField.getText().trim()); song.lengthSeconds = Vinyl.Song.parseDuration(lengthField.getText()); song.explicit = explicitBox.isSelected(); song.rating = ratingBar.getValue();
                song.coverPath = selectedCoverPath; song.coverIcon = null; return true;
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage()); return false; }
        }
        private JTextField textField(String text) { JTextField tf = new JTextField(text); tf.setBackground(new Color(40,40,40)); tf.setForeground(FG); tf.setCaretColor(FG); tf.setBorder(new EmptyBorder(8,10,8,10)); return tf; }
        private void addRow(JPanel p, GridBagConstraints gc, int row, String label, Component field) {
            gc.gridx = 0; gc.gridy = row; gc.weightx = 0; gc.gridwidth = 1; JLabel l = new JLabel(label); l.setForeground(SUBFG); p.add(l, gc);
            gc.gridx = 1; gc.weightx = 1; gc.gridwidth = 2; p.add(field, gc);
        }
    }

    // Int-only text field
    static class IntField extends JTextField {
        IntField(String text) {
            super(text);
            setBackground(new Color(40,40,40)); setForeground(FG); setCaretColor(FG); setBorder(new EmptyBorder(8,10,8,10));
            ((AbstractDocument) getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException { if (string == null) return; if (string.chars().allMatch(Character::isDigit)) super.insertString(fb, offset, string, attr); }
                @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException { if (text == null) return; if (text.chars().allMatch(Character::isDigit)) super.replace(fb, offset, length, text, attrs); }
            });
        }
    }

    // Clickable star bar for dialog
    static class StarBar extends JPanel {
        private int value; private final java.util.List<JLabel> stars = new ArrayList<>();
        StarBar(int initial) { super(new FlowLayout(FlowLayout.LEFT, 4, 0)); setOpaque(false); setValue(initial); for (int i = 1; i <= 5; i++) { final int idx = i; JLabel star = new JLabel("☆"); star.setFont(star.getFont().deriveFont(Font.PLAIN, 22f)); star.setForeground(new Color(255,204,64)); star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); star.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { setValue(idx); } }); stars.add(star); add(star);} refresh(); }
        void setValue(int v) { value = Math.max(0, Math.min(5, v)); refresh(); }
        int getValue() { return value; }
        private void refresh() { for (int i = 0; i < stars.size(); i++) stars.get(i).setText(i < value ? "★" : "☆"); }
    }
}