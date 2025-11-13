import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
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
    private static final ImageIcon KEBAB_ICON = tintIcon(VinylUiKit.loadIcon("/icons/KebobIcon.png", 25, 25), RED);



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

        // Load existing songs.xml at startup (if present)
        File xmlFile = new File("src/xml/songs.xml");
        if (xmlFile.exists()) {
            try {
                java.util.List<Vinyl.Song> songs = Vinyl.XmlStore.load(xmlFile);
                model.setSongs(songs);
            } catch (Exception ex) {
                // Non-fatal: keep the seeded rows; show a brief error
                System.err.println("Failed to load songs.xml: " + ex.getMessage());
            }
        }

        // Auto-save on any model change
        model.addChangeListener(() -> {
            try {
                Vinyl.XmlStore.save(xmlFile, model.getAll());
            } catch (Exception ex) {
                System.err.println("Failed to save songs.xml: " + ex.getMessage());
            }
        });

        // Column-banded table so each column reads as a single connected block
        table = new VinylUiKit.ColumnBandTable(model);
        table.setRowHeight(56);

        table.setIntercellSpacing(new Dimension(0, 0));
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setBackground(BG);
        table.setForeground(BG);
        table.setSelectionBackground(new Color(0, 0, 0, 0)); // no darkening on click
        table.setSelectionForeground(FG);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JTableHeader header = table.getTableHeader();
        header.setBackground(BG);
        header.setForeground(SUBFG);
        header.setReorderingAllowed(false);
        header.setOpaque(true);
        header.setDefaultRenderer(new VinylUiKit.HeaderRenderer());

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        setColumnWidths();
        // Hide header text for Cover (index 1) and Explicit (index 8)
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(1).setHeaderValue("");
        cm.getColumn(8).setHeaderValue("");
        cm.getColumn(9).setHeaderValue("");
        cm.getColumn(10).setHeaderValue("");
        header.revalidate();
        header.repaint();

        // Transparent text renderers so backgrounds align vertically per column
        VinylUiKit.TextCellRenderer leftCell = new VinylUiKit.TextCellRenderer(SwingConstants.LEFT);
        VinylUiKit.TextCellRenderer centerCell = new VinylUiKit.TextCellRenderer(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerCell); // ID
        table.getColumnModel().getColumn(2).setCellRenderer(leftCell);   // Title
        table.getColumnModel().getColumn(3).setCellRenderer(leftCell);   // Artist
        table.getColumnModel().getColumn(4).setCellRenderer(leftCell);   // Album
        table.getColumnModel().getColumn(5).setCellRenderer(leftCell);   // Genre
        table.getColumnModel().getColumn(6).setCellRenderer(leftCell); // BPM
        table.getColumnModel().getColumn(7).setCellRenderer(leftCell); // Length

        // Other renderers and editors
        table.getColumnModel().getColumn(1).setCellRenderer(new VinylUiKit.CoverRenderer());
        table.getColumnModel().getColumn(8).setCellRenderer(new VinylUiKit.ExplicitRenderer()); // Explicit centered
        table.getColumnModel().getColumn(9).setCellRenderer(new VinylUiKit.RatingRenderer());
        table.getColumnModel().getColumn(9).setCellEditor(new VinylUiKit.RatingEditor());
        table.getColumnModel().getColumn(10).setCellRenderer(new ActionsRenderer());
        table.getColumnModel().getColumn(10).setCellEditor(new ActionsEditor());

        // Track hover for actions cell AND for whole-row band shading (on hover, not click)
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewCol = table.columnAtPoint(e.getPoint());
                int viewRow = table.rowAtPoint(e.getPoint());
                int actionsViewCol = table.convertColumnIndexToView(10);

                // update actions hover: only when cursor is over the 28x28 kebob box
                int prevActionsRow = hoveredActionsRow;
                if (viewCol == actionsViewCol && viewRow >= 0) {
                    Rectangle cellRect = table.getCellRect(viewRow, actionsViewCol, false);
                    int size = 28; // kebob label/button size
                    int kebabX = cellRect.x + (cellRect.width - size) / 2;
                    int kebabY = cellRect.y + (table.getRowHeight(viewRow) - size) / 2;
                    Rectangle kebabRect = new Rectangle(kebabX, kebabY, size, size);
                    hoveredActionsRow = kebabRect.contains(e.getPoint()) ? viewRow : -1;
                } else {
                    hoveredActionsRow = -1;
                }

                // update band hover (ColumnBandTable reads "hoverRow")
                int prevBandRow = bandHoverRow;
                bandHoverRow = viewRow;
                table.putClientProperty("hoverRow", bandHoverRow);

                if (prevActionsRow != hoveredActionsRow || prevBandRow != bandHoverRow) {
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
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


        JScrollPane scroll = new VinylUiKit.RoundedScrollPane(table, 16);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        // Ensure the column header viewport is transparent
        if (scroll.getColumnHeader() != null) {
            scroll.getColumnHeader().setOpaque(false);
        }
        // Minimal scrollbars
        scroll.getVerticalScrollBar().setUI(new VinylUiKit.MinimalScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new VinylUiKit.MinimalScrollBarUI());
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getHorizontalScrollBar().setOpaque(false);
        // Make both top corners transparent


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
        // Custom menu bar with rounded background (no border)
        JMenuBar bar = new JMenuBar() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 14;
                int pad = 2; // slight top padding so arcs look nicer
                g2.setColor(HEADER_BG);
                g2.fillRoundRect(0, pad, getWidth(), getHeight() - pad, arc, arc);
                g2.dispose();
            }
        };
        bar.setOpaque(false); // allow rounded corners to show through
        bar.setBorder(new EmptyBorder(6, 8, 6, 8));
        bar.setForeground(SUBFG);

        JMenu file = createTopMenu("File");
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
        // Note: View menu is added by the caller via buildViewMenu(), so we don't add it here to avoid duplicates.

        // Mode menu to switch to CLI
        JMenu mode = createTopMenu("Mode");
        JMenuItem switchToCli = new JMenuItem("Switch to CLI");
        switchToCli.addActionListener(e -> {
            // Close GUI and start CLI in a background thread
            dispose();
            new Thread(VinylCli::run, "Vinyl-CLI").start();
        });
        mode.add(switchToCli);
        bar.add(mode);

        return bar;
    }

    private JMenu buildViewMenu() {
        JMenu view = createTopMenu("View");
        String[] names = {"ID", "Cover", "Title", "Artist", "Album", "Genre", "BPM", "Length", "Explicit", "Rating", "Actions"};
        for (int i = 0; i < names.length; i++) {
            final int modelIndex = i;
            JCheckBoxMenuItem item = new JCheckBoxMenuItem("Show " + names[i], true);
            item.addActionListener(e -> columnManager.setVisible(modelIndex, item.isSelected()));
            view.add(item);
        }
        return view;
    }

    // Rounded hover for top-level menus ("File", "View")
    private JMenu createTopMenu(String title) {
        return new JMenu(title) {
            private boolean hover = false;

            {
                setOpaque(false);
                setForeground(SUBFG);
                setRolloverEnabled(true);
                setBorder(new EmptyBorder(4, 10, 4, 10));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean over = hover || getModel().isRollover() || getModel().isArmed();
                if (over) {
                    g2.setColor(new Color(100, 100, 100)); // darker hover shade
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    // Menu bar used inside the Song Properties dialog, visually identical to the main menu bar
    private JMenuBar buildDialogMenuBar() {
        JMenuBar bar = new JMenuBar() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 14;
                int pad = 2;
                g2.setColor(HEADER_BG);
                g2.fillRoundRect(0, pad, getWidth(), getHeight() - pad, arc, arc);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(6, 8, 6, 8));
        bar.setForeground(SUBFG);


        JMenuItem save = new JMenuItem("Save");
        JMenuItem cancel = new JMenuItem("Cancel");
        save.addActionListener(e -> {
            // Try to trigger the dialog's Save button by dispatching to the default button if present
            JRootPane rp = getRootPane();
            if (rp != null && rp.getDefaultButton() != null) rp.getDefaultButton().doClick();
        });
        cancel.addActionListener(e -> dispose());

        return bar;
    }

    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG);

        JButton addBtn = VinylUiKit.redButton("Add");
        addBtn.addActionListener(e -> openPropertiesDialog(new Vinyl.Song(), true));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        left.setBackground(BG);
        left.add(addBtn);

        searchField = new VinylUiKit.RoundedTextField();
        searchField.setPreferredSize(new Dimension(320, 34));
        searchField.putClientProperty("JTextField.placeholderText", "Search");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
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
                @Override
                public boolean include(Entry<? extends Vinyl.SongTableModel, ? extends Integer> entry) {
                    Vinyl.Song song = model.getSong(entry.getIdentifier());
                    return song.matches(s);
                }
            });
        }
    }

    private void setColumnWidths() {
        TableColumnModel cols = table.getColumnModel();
        setPrefWidth(cols.getColumn(0), 50);   // ID
        setPrefWidth(cols.getColumn(1), 75);   // Cover
        setPrefWidth(cols.getColumn(2), 260);  // Title
        setPrefWidth(cols.getColumn(3), 180);  // Artist
        setPrefWidth(cols.getColumn(4), 200);  // Album
        setPrefWidth(cols.getColumn(5), 140);  // Genre
        setPrefWidth(cols.getColumn(6), 90);   // BPM
        setPrefWidth(cols.getColumn(7), 70);  // Length
        setPrefWidth(cols.getColumn(8), 30);   // Explicit
        setPrefWidth(cols.getColumn(9), 125);  // Rating
        setPrefWidth(cols.getColumn(10), 25); // Actions
    }

    private void setPrefWidth(TableColumn col, int w) {
        col.setPreferredWidth(w);
        col.setMinWidth(40);
    }

    private static ImageIcon tintIcon(ImageIcon src, Color color) {
        if (src == null || src.getIconWidth() <= 0 || src.getIconHeight() <= 0) return src;
        int w = src.getIconWidth();
        int h = src.getIconHeight();
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        src.paintIcon(null, g, 0, 0);
        g.dispose();

        BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = tinted.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillRect(0, 0, w, h);
        g2.setComposite(AlphaComposite.DstIn);
        g2.drawImage(mask, 0, 0, null);
        g2.dispose();
        return new ImageIcon(tinted);
    }

    private void doImportXml() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import songs.xml");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                java.util.List<Vinyl.Song> songs = Vinyl.XmlStore.load(f);
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
                Vinyl.XmlStore.save(f, model.getAll());
            } catch (Exception ex) {
                showError("Failed to export XML: " + ex.getMessage());
            }
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void openPropertiesDialog(Vinyl.Song song, boolean isNew) {
        PropertiesDialog dlg = new PropertiesDialog(this, song, isNew);
        dlg.setVisible(true);
        if (dlg.saved) {
            if (isNew) {
                if (song.id <= 0) song.id = model.nextId();
                model.addSong(song);
            } else {
                int idx = model.indexOf(song);
                if (idx >= 0) model.songUpdated(idx);
            }
        }
    }

    // ---------- Actions column ----------
    private int hoveredActionsRow = -1;
    private int bandHoverRow = -1;

    // Small label that can draw a rounded outline when "hover" is true
    private final class KebabLabel extends JLabel {
        private boolean hover;
        KebabLabel() {
            super();
            setText(null);
            setIcon(KEBAB_ICON); // use provided image for perfect centering
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setPreferredSize(new Dimension(28, 28)); // consistent size
            setMinimumSize(new Dimension(28, 28));
            setMaximumSize(new Dimension(28, 28));
            setOpaque(false);
        }
        void setHover(boolean h) {
            if (this.hover != h) { this.hover = h; repaint(); }
        }
        @Override
        protected void paintComponent(Graphics g) {
            if (hover) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int pad = 2;
                int w = getWidth() - pad * 2 - 1;
                int h = getHeight() - pad * 2 - 1;
                // Fill only (no border) for hover background
                g2.setColor(new Color(200, 200, 200, 40));
                g2.fillRoundRect(pad, pad, w, h, 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
    class ActionsRenderer implements TableCellRenderer {
        private final JPanel holder = new JPanel(new GridBagLayout());
        private final KebabLabel dots = new KebabLabel();

        ActionsRenderer() {
            holder.setOpaque(false);
            holder.add(dots, new GridBagConstraints()); // perfectly centered
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int actionsViewCol = table.convertColumnIndexToView(10);
            int viewRow = row;
            dots.setHover(viewRow == hoveredActionsRow && column == actionsViewCol);
            return holder;
        }
    }



    class ActionsEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel holder = new JPanel(new GridBagLayout());
        private final JButton menuBtn = makeKebabButton();
        private int editingRow = -1;

        ActionsEditor() {
            holder.setOpaque(false);
            holder.add(menuBtn, new GridBagConstraints());
            menuBtn.addActionListener(e -> {
                if (editingRow < 0) return;
                int modelRow = table.convertRowIndexToModel(editingRow);
                Vinyl.Song s = model.getSong(modelRow);
                JPopupMenu popup = buildActionsPopup(s, modelRow);
                popup.show(menuBtn, 0, menuBtn.getHeight());
            });
        }

        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            editingRow = row;
            SwingUtilities.invokeLater(() -> {
                if (editingRow == row) {
                    int modelRow = table.convertRowIndexToModel(editingRow);
                    Vinyl.Song s = model.getSong(modelRow);
                    JPopupMenu popup = buildActionsPopup(s, modelRow);
                    popup.show(menuBtn, 0, menuBtn.getHeight());
                }
            });
            return holder;
        }

        public Object getCellEditorValue() {
            return null;
        }
    }


    private JPopupMenu buildActionsPopup(Vinyl.Song s, int modelRow) {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem props = new JMenuItem("Properties...");
        props.addActionListener(e -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            openPropertiesDialog(s, false);
        });

        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            model.removeAt(modelRow);
        });

        popup.add(props);
        popup.addSeparator();
        popup.add(delete);
        return popup;
    }


    private JButton makeKebabButton() {
        JButton b = new JButton() {
            { setRolloverEnabled(true); }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getModel().isRollover()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int pad = 2;
                    // Fill only (no border) for hover background
                    g2.setColor(new Color(200, 200, 200, 40));
                    g2.fillRoundRect(pad, pad, getWidth() - pad * 2 - 1, getHeight() - pad * 2 - 1, 10, 10);
                    g2.dispose();
                }
            }
        };
        b.setToolTipText("More actions");
        b.setText(null);
        b.setIcon(KEBAB_ICON); // use provided image
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setBorder(null); // no border
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setVerticalTextPosition(SwingConstants.CENTER);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Keep size stable so it stays perfectly centered vertically in the cell
        b.setPreferredSize(new Dimension(28, 28));
        b.setMinimumSize(new Dimension(28, 28));
        b.setMaximumSize(new Dimension(28, 28));
        return b;
    }




    // ---------- Column show/hide manager ----------
    static class ColumnManager {
        private final JTable table;
        private final TableColumnModel tcm;
        private final Map<Integer, TableColumn> byModel = new HashMap<>();
        private final boolean[] visible;

        ColumnManager(JTable table) {
            this.table = table;
            this.tcm = table.getColumnModel();
            int count = table.getModel().getColumnCount();
            visible = new boolean[count];
            for (int v = 0; v < tcm.getColumnCount(); v++) {
                TableColumn tc = tcm.getColumn(v);
                int m = tc.getModelIndex();
                byModel.put(m, tc);
                visible[m] = true;
            }
        }

        void setVisible(int modelIndex, boolean show) {
            if (modelIndex < 0 || modelIndex >= visible.length) return;
            if (show == visible[modelIndex]) return;
            if (!show) {
                for (int v = 0; v < tcm.getColumnCount(); v++) {
                    if (tcm.getColumn(v).getModelIndex() == modelIndex) {
                        TableColumn tc = tcm.getColumn(v);
                        tcm.removeColumn(tc);
                        break;
                    }
                }
                visible[modelIndex] = false;
            } else {
                TableColumn tc = byModel.get(modelIndex);
                if (tc == null) return;
                tcm.addColumn(tc);
                int currentIndex = tcm.getColumnCount() - 1;
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

    // ---------- Properties dialog ----------
    class PropertiesDialog extends JDialog {
        boolean saved = false;
        private final Vinyl.Song song;
        private JTextField idField, titleField, artistField, albumField, genreField, lengthField; // length mm:ss
        private IntField bpmField;
        private JCheckBox explicitBox;
        private StarBar ratingBar;
        private JLabel coverPreview;
        private String selectedCoverPath;

        PropertiesDialog(Frame owner, Vinyl.Song song, boolean isNew) {
            super(owner, isNew ? "Add Song" : "Song Properties", true);
            this.song = song; this.selectedCoverPath = song.coverPath;
            setSize(720, 540); setLocationRelativeTo(owner); setLayout(new BorderLayout());
            // Remove the top bar above tabs
            // setJMenuBar(buildDialogMenuBar());

            JTabbedPane tabs = new JTabbedPane();
            tabs.setUI(new RoundedTabsUI());
            tabs.setBorder(BorderFactory.createEmptyBorder());
            tabs.setBackground(BG);
            tabs.setForeground(FG);
            tabs.setOpaque(false);
            tabs.putClientProperty("TabbedPane.contentOpaque", Boolean.FALSE);

            tabs.addTab("General", buildGeneralTab());
            tabs.addTab("Cover", buildCoverTab());

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            actions.setBackground(BG);
            JButton cancel = VinylUiKit.grayButton("Cancel");
            JButton save = VinylUiKit.redButton("Save");
            JButton deleteCover = VinylUiKit.grayButton("Delete Cover");
            cancel.addActionListener(e -> dispose());
            save.addActionListener(e -> {
                if (applyChanges()) {
                    saved = true;
                    dispose();
                }
            });
            deleteCover.addActionListener(e -> {
                selectedCoverPath = null; // clear selected cover
                updateCoverPreview();     // refresh preview immediately
            });
            actions.add(cancel);
            actions.add(deleteCover);
            actions.add(save);
            add(tabs, BorderLayout.CENTER);
            add(actions, BorderLayout.SOUTH);
        }

        private JPanel buildGeneralTab() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(BG);
            // Remove panel border to match "no border" requirement
            p.setBorder(BorderFactory.createEmptyBorder());
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
            lengthField = textField(Vinyl.Song.formatDuration(song.lengthSeconds));
            explicitBox = new JCheckBox("Explicit");
            explicitBox.setSelected(song.explicit);
            explicitBox.setForeground(FG);
            explicitBox.setBackground(BG);
            explicitBox.setOpaque(false);
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
            p.setBackground(BG);
            // Remove panel border to match "no border" requirement
            p.setBorder(BorderFactory.createEmptyBorder());
            coverPreview = new JLabel();
            coverPreview.setHorizontalAlignment(SwingConstants.CENTER);
            coverPreview.setOpaque(true);
            coverPreview.setBackground(new Color(36, 36, 36));
            coverPreview.setPreferredSize(new Dimension(300, 300));
            updateCoverPreview();
            JButton choose = VinylUiKit.redButton("Choose Image");
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
            if (selectedCoverPath == null || selectedCoverPath.isEmpty()) icon = Vinyl.Song.placeholderIcon(280, 280);
            else {
                try {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new File(selectedCoverPath));
                    Image scaled = img.getScaledInstance(280, 280, Image.SCALE_SMOOTH);
                    icon = new ImageIcon(scaled);
                } catch (Exception ex) {
                    icon = Vinyl.Song.placeholderIcon(280, 280);
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
                song.lengthSeconds = Vinyl.Song.parseDuration(lengthField.getText());
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
            JTextField tf = new VinylUiKit.RoundedTextField(text);
            tf.setForeground(FG);
            tf.setCaretColor(FG);
            return tf;
        }

        private void addRow(JPanel p, GridBagConstraints gc, int row, String label, Component field) {
            gc.gridx = 0;
            gc.gridy = row;
            gc.weightx = 0;
            gc.gridwidth = 1;
            JLabel l = new JLabel(label);
            l.setForeground(SUBFG);
            p.add(l, gc);
            gc.gridx = 1;
            gc.weightx = 1;
            gc.gridwidth = 2;
            p.add(field, gc);
        }
    }

    // Int-only text field
    static class IntField extends VinylUiKit.RoundedTextField {
        IntField(String text) {
            super(text);
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

    // Rounded tabs UI for dialog tabs (no content border, BG/FG colors, rounded tab pills)
    private static final class RoundedTabsUI extends BasicTabbedPaneUI {
        private static final int ARC = 14;

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(6, 8, 6, 8);
            tabInsets = new Insets(8, 14, 8, 14);
            contentBorderInsets = new Insets(0, 0, 0, 0);
            tabPane.setOpaque(false);
            tabPane.setBackground(BG);
            tabPane.setForeground(FG);
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            // no content border
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
            // no explicit border; shape is handled in background
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = isSelected ? new Color(46, 46, 46) : new Color(38, 38, 38);
            g2.setColor(fill);
            g2.fillRoundRect(x, y + 2, w, h - 4, ARC, ARC);
            g2.dispose();
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
            // no focus ring
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            g.setFont(font);
            g.setColor(FG);
            int y = textRect.y + metrics.getAscent();
            g.drawString(title, textRect.x, y);
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) + 4;
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 12;
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
                star.setForeground(new Color(255, 204, 64));
                star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                star.addMouseListener(new MouseAdapter() {
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
            for (int i = 0; i < stars.size(); i++) stars.get(i).setText(i < value ? "★" : "☆");
        }
    }
}