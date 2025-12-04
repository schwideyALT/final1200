import javax.imageio.ImageIO;
import javax.swing.AbstractCellEditor;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.AbstractBorder;
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
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.List;

// Swing frame using Vinyl (domain) and VinylUiKit (UI widgets).
public class VinylGui extends JFrame {

    // ---------- Launch ----------
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            VinylUiKit.setSystemDefaults();

            // 1) Show a small splash window right away
            JWindow splash = createSplashWindow();
            splash.setVisible(true);

            // 2) Build the real UI in the background so the splash stays responsive
            SwingWorker<VinylGui, Void> worker = new SwingWorker<>() {
                @Override
                protected VinylGui doInBackground() {
                    // Heavy construction work happens here
                    return new VinylGui();
                }

                @Override
                protected void done() {
                    try {
                        VinylGui gui = get();
                        // 3) Hide splash and show the main window
                        splash.dispose();
                        gui.setVisible(true);
                    } catch (Exception ex) {
                        splash.dispose();
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(
                                null,
                                "Failed to start Music Library:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            };
            worker.execute();
        });
    }

    private static JWindow createSplashWindow() {
        JWindow w = new JWindow();
        w.setBackground(new Color(0, 0, 0, 0)); // transparent window background

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(new EmptyBorder(24, 32, 24, 32));
        content.setBackground(VinylUiKit.BG);

        JLabel title = new JLabel("Music Library");
        title.setForeground(VinylUiKit.FG);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JLabel subtitle = new JLabel("Loading songs…");
        subtitle.setForeground(VinylUiKit.SUBFG);
        subtitle.setBorder(new EmptyBorder(8, 0, 0, 0));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(title);
        textPanel.add(subtitle);

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBorder(new EmptyBorder(12, 0, 0, 0));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(textPanel);
        center.add(bar);

        content.add(center, BorderLayout.CENTER);
        w.setContentPane(content);
        w.pack();
        w.setLocationRelativeTo(null);
        return w;
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



    private final JTable table;
    private final Vinyl.SongTableModel model;
    private final TableRowSorter<Vinyl.SongTableModel> sorter;
    private JTextField searchField;
    private final ColumnManager columnManager;
    private boolean autoHideZero = false; // auto-hide rows with count==0

    // Toast notification components
    private JPanel toastPanel;
    private JLabel toastLabel;
    private Timer toastTimer;
    private int toastTargetY;
    private int toastState = 0; // 0=hidden, 1=sliding down, 2=visible, 3=sliding up

    // Track hovered row for hover-only Actions button
    //private int hoveredRow = -1;
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
                List<Vinyl.Song> songs = Vinyl.XmlStore.load(xmlFile);
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
        // Hide header text for Cover (index 1) and Explicit (index 8) and actions
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(1).setHeaderValue("");
        cm.getColumn(8).setHeaderValue("");
        cm.getColumn(12).setHeaderValue("");
        // Do not hide headers for Rating, Price, Count so users can see titles
        // Adjusted: Actions header will remain default too
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
        // Price (10) and Count (11) use default renderers
        table.getColumnModel().getColumn(10).setCellRenderer(new PriceRenderer());
        table.getColumnModel().getColumn(11).setCellRenderer(new CountRenderer());
        table.getColumnModel().getColumn(12).setCellRenderer(new ActionsRenderer());
        table.getColumnModel().getColumn(12).setCellEditor(new ActionsEditor());
        // Track hover for actions cell AND for whole-row band shading (on hover, not click)
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewCol = table.columnAtPoint(e.getPoint());
                int viewRow = table.rowAtPoint(e.getPoint());
                int actionsViewCol = table.convertColumnIndexToView(12);

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

        // Initialize toast overlay
        initToast();
    }

    public class TranslucentPopup extends JPopupMenu {

        {
            // need to disable that to work
            setLightWeightPopupEnabled(false);
        }

        @Override
        public void setVisible(boolean visible) {
            if (visible == isVisible())
                return;
            super.setVisible(visible);
            if (visible) {
                // attempt to set tranparency
                try {
                    Window w = SwingUtilities.getWindowAncestor(this);
                    w.setOpacity(0.90F);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    // Create the toast panel once and add it to the frame's layered pane
    private void initToast() {
        if (toastPanel != null) return;

        toastPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                setOpaque(false);

                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int arc = 18;

                // Frosted glass fill
                Color fill = new Color(255, 255, 255, 20); // increase alpha for stronger white
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.dispose();
                super.paintComponent(g);
            }
        };

        toastPanel.setOpaque(false);
        toastPanel.setBorder(new EmptyBorder(8, 16, 8, 16));

        toastLabel = new JLabel();
// Dark text works better on light glass
        toastLabel.setForeground(FG);
        toastLabel.setFont(toastLabel.getFont().deriveFont(Font.PLAIN, 13f));
        toastPanel.add(toastLabel, BorderLayout.CENTER);

        JLayeredPane lp = getLayeredPane();
        int width = getWidth();
        int height = 40;
        int x = (width - 320) / 2;
        toastTargetY = 16;
        toastPanel.setBounds(x, -height, 320, height);
        lp.add(toastPanel, JLayeredPane.POPUP_LAYER);
        toastPanel.setVisible(false);
    }

    // Show a sliding toast at the top of the main window
    private void showToast(String message) {
        // Do NOT log here any more. Logging is done explicitly at each action.
        initToast();
        toastLabel.setText(message);

        JLayeredPane lp = getLayeredPane();
        int width = getWidth();
        int height = toastPanel.getHeight();
        int x = (width - toastPanel.getWidth()) / 2;
        toastPanel.setBounds(x, toastPanel.getY(), toastPanel.getWidth(), height);

        if (toastTimer != null && toastTimer.isRunning()) {
            toastTimer.stop();
        }

        toastPanel.setVisible(true);
        toastState = 1; // start sliding down

        toastTimer = new Timer(16, new ActionListener() {
            long visibleStart = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                int y = toastPanel.getY();

                if (toastState == 1) { // sliding down
                    int newY = y + 10;
                    if (newY >= toastTargetY) {
                        newY = toastTargetY;
                        toastState = 2;
                        visibleStart = System.currentTimeMillis();
                    }
                    toastPanel.setLocation(toastPanel.getX(), newY);
                } else if (toastState == 2) { // visible, wait
                    if (System.currentTimeMillis() - visibleStart > 1600) { // ~1.6s
                        toastState = 3;
                    }
                } else if (toastState == 3) { // sliding up
                    int newY = y - 10;
                    if (newY <= -height) {
                        newY = -height;
                        toastPanel.setVisible(false);
                        toastTimer.stop();
                        toastState = 0;
                    }
                    toastPanel.setLocation(toastPanel.getX(), newY);
                }
                lp.repaint();
            }
        });
        toastTimer.start();
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
        JCheckBoxMenuItem inStockOnly = new JCheckBoxMenuItem("Only show items in stock", false);
        switchToCli.addActionListener(e -> {
            // Close GUI and start CLI in a background thread
            dispose();
            new Thread(VinylCli::run, "Vinyl-CLI").start();
        });
        inStockOnly.addActionListener(e -> setAutoHideZeroCount(inStockOnly.isSelected()));
        // keep the checkbox state in sync if needed elsewhere
        inStockOnly.setSelected(autoHideZero);

        mode.add(switchToCli);
        mode.addSeparator();
        mode.add(inStockOnly);
        bar.add(mode);

        // Logs menu to review and export activity log
        JMenu logs = createTopMenu("Logs");
        JMenuItem viewLogs = new JMenuItem("View Logs");
        JMenuItem exportLogs = new JMenuItem("Export Logs...");
        viewLogs.addActionListener(e -> showLogDialog());
        exportLogs.addActionListener(e -> exportLogsToFile());
        logs.add(viewLogs);
        logs.add(exportLogs);
        bar.add(logs);

        return bar;
    }
    
    private static final class LogHeaderRenderer extends DefaultTableCellRenderer {
        LogHeaderRenderer() {
            setHorizontalAlignment(LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setOpaque(true);
            setBackground(BG);       // blend with dialog background
            setForeground(FG);       // use foreground text color
            setBorder(new EmptyBorder(6, 10, 6, 10));
            return this;
        }
    }


    // Table model that exposes the shared activity log as rows
    private final class LogTableModel extends AbstractTableModel {
        private final String[] columns = {"Time", "Song", "Total Price", "Message"};

        @Override
        public int getRowCount() {
            return LogEntry.activityLog.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LogEntry entry = LogEntry.activityLog.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getTimestampText();
                case 1 -> entry.getSongTitle();
                case 2 -> entry.getTotalPrice() == 0.0
                        ? ""
                        : String.format("%.2f", entry.getTotalPrice());
                case 3 -> entry.getMessage();
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    // Show a dialog containing the activity log in a simple table
    private void showLogDialog() {
        JDialog dlg = new JDialog(this, "Activity Log", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout());
        dlg.getContentPane().setBackground(BG);

        LogTableModel logModel = new LogTableModel();
        JTable logTable = new JTable(logModel);
        logTable.setFillsViewportHeight(true);
        logTable.setBackground(BG);
        logTable.setForeground(FG);
        logTable.setGridColor(new Color(60, 60, 60));
        logTable.setRowHeight(24);

        JTableHeader header = logTable.getTableHeader();
        header.setBackground(BG);
        header.setForeground(FG);
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new LogHeaderRenderer());

        // Use the same rounded scroll pane + minimal scrollbars as the main view
        JScrollPane scrollPane = new VinylUiKit.RoundedScrollPane(logTable, 12);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(BG);
        if (scrollPane.getColumnHeader() != null) {
            scrollPane.getColumnHeader().setOpaque(false);
        }

        scrollPane.getVerticalScrollBar().setUI(new VinylUiKit.MinimalScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new VinylUiKit.MinimalScrollBarUI());
        scrollPane.getVerticalScrollBar().setOpaque(false);
        scrollPane.getHorizontalScrollBar().setOpaque(false);

        dlg.add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(BG);
        JButton closeBtn = VinylUiKit.grayButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        bottom.add(closeBtn);

        dlg.add(bottom, BorderLayout.SOUTH);
        dlg.setSize(900, 400);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // Export the activity log to a user-chosen file (CSV-style text)
    private void exportLogsToFile() {
        if (LogEntry.activityLog.isEmpty()) {
            showToast("No logs to export");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Logs");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File f = chooser.getSelectedFile();
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)))) {
            out.println("Time,Song,TotalPrice,Message");
            for (LogEntry entry : LogEntry.activityLog) {
                String time = entry.getTimestampText();
                String song = escapeCsvForExport(entry.getSongTitle());
                String total = entry.getTotalPrice() == 0.0
                        ? ""
                        : String.format("%.2f", entry.getTotalPrice());
                String msg = escapeCsvForExport(entry.getMessage());
                out.printf("%s,%s,%s,%s%n", time, song, total, msg);
            }
            showToast("Logs exported to " + f.getName());
        } catch (IOException ex) {
            showError("Failed to export logs: " + ex.getMessage());
        }
    }

    // Simple CSV helper for export (separate from LogEntry to keep GUI independent)
    private String escapeCsvForExport(String s) {
        if (s == null) return "\"\"";
        String result = s.replace("\"", "\"\"");
        return "\"" + result + "\"";
    }

    public void setAutoHideZeroCount(boolean on) {
        this.autoHideZero = on;
        applyFilter();
    }


    private JMenu buildViewMenu() {
        JMenu view = createTopMenu("View");
        //VinylUiKit.stylePopup(view.getPopupMenu());
        String[] names = {"ID", "Cover", "Title", "Artist", "Album", "Genre", "BPM", "Length", "Explicit", "Rating", "Price", "Count", "Actions"};
        for (int i = 0; i < names.length; i++) {
            final int modelIndex = i;
            JCheckBoxMenuItem item = new JCheckBoxMenuItem("Show " + names[i], true);
            item.addActionListener(e -> columnManager.setVisible(modelIndex, item.isSelected()));
            view.add(item);
        }
        // Style the View menu popup
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
                // Style this menu’s popup (File/Mode/View)
                //VinylUiKit.stylePopup(getPopupMenu());
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
                    g2.setColor(Color.darkGray); // darker hover shade
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                }
                g2.dispose();
                super.paintComponent(g);
            }

        };
    }

    // Menu bar used inside the Song Properties dialog, visually identical to the main menu bar

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
        List<RowFilter<Vinyl.SongTableModel, Integer>> filters = new ArrayList<>();

        if (q != null && !q.trim().isEmpty()) {
            String s = q.trim().toLowerCase();
            filters.add(new RowFilter<Vinyl.SongTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends Vinyl.SongTableModel, ? extends Integer> entry) {
                    Vinyl.Song song = model.getSong(entry.getIdentifier());
                    return song.matches(s);
                }
            });
        }

        if (autoHideZero) {
            filters.add(new RowFilter<Vinyl.SongTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends Vinyl.SongTableModel, ? extends Integer> entry) {
                    Vinyl.Song song = model.getSong(entry.getIdentifier());
                    return song.count > 0;
                }
            });
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else if (filters.size() == 1) {
            sorter.setRowFilter(filters.get(0));
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
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
        setPrefWidth(cols.getColumn(10), 90);  // Price
        setPrefWidth(cols.getColumn(11), 80);  // Count
        setPrefWidth(cols.getColumn(12), 25);  // Actions
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
                List<Vinyl.Song> songs = Vinyl.XmlStore.load(f);
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
            int actionsViewCol = table.convertColumnIndexToView(12);
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
                    //popup.setLightWeightPopupEnabled(true);
                    popup.show(menuBtn, 0, menuBtn.getHeight());
                }
            });
            return holder;
        }

        public Object getCellEditorValue() {
            return null;
        }
    }

    // Transparent, right-aligned price renderer with two decimals
    static final class PriceRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setOpaque(false);
            setForeground(FG);
            setHorizontalAlignment(SwingConstants.RIGHT);
            if (value instanceof Number) {
                setText(String.format("%.2f", ((Number) value).doubleValue()));
            } else {
                setText("");
            }
            setBorder(new EmptyBorder(8, 12, 8, 12));
            return this;
        }
    }

    // Transparent, centered count renderer
    static final class CountRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setOpaque(false);
            setForeground(FG);
            setHorizontalAlignment(SwingConstants.CENTER);
            setText(value == null ? "" : value.toString());
            setBorder(new EmptyBorder(8, 12, 8, 12));
            return this;
        }
    }


    private JPopupMenu buildActionsPopup(Vinyl.Song s, int modelRow) {
        JPopupMenu popup = new TranslucentPopup();
        //VinylUiKit.stylePopup(popup);
        //popup.setLightWeightPopupEnabled(true);

        JMenuItem props = new JMenuItem("Properties...");
        props.addActionListener(e -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            openPropertiesDialog(s, false);
        });

        JMenuItem addInv = new JMenuItem("Add Inventory");
        addInv.addActionListener(e -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            Integer qty = promptAddQuantity();
            if (qty == null || qty <= 0) return;
            s.count += qty;
            model.songUpdated(modelRow);

            String songDisplay = "ID " + s.id + ": " + s.title + " - " + s.artist;
            String msg = "Added " + qty + " to inventory";

            LogEntry.logEvent(msg, songDisplay);
            showToast(msg);
        });

        JMenuItem sell = new JMenuItem("Sell");
        sell.addActionListener(e -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            if (s.count <= 0) {
                showNoInventoryDialog();
                return;
            }
            while (true) {
                Integer qty = promptSellQuantityUnbounded(s.count);
                if (qty == null || qty <= 0) return;

                if (qty > s.count) {
                    int choice = showOverSellDialog(qty, s.count);
                    if (choice == 0) {
                        qty = s.count;
                    } else if (choice == 1) {
                        continue;
                    } else {
                        return;
                    }
                }

                s.count -= qty;
                model.songUpdated(modelRow);

                // Build display fields for the log row
                String songDisplay = "ID " + s.id + ": " + s.title + " - " + s.artist;
                double totalPrice = s.price * qty;
                String msg = "Sold " + qty + " item" + (qty == 1 ? "" : "s");

                // Single rich log entry for this sell action
                LogEntry.logEvent(msg, songDisplay, totalPrice);

                // Toast only shows the message
                showToast(msg);
                return;
            }
        });

        JMenuItem delete = new JMenuItem("Delete");

        delete.addActionListener(e -> {
            if (table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
            model.removeAt(modelRow);
            showToast("Song deleted");

        });

        // add after creation
        popup.add(props);
        popup.addSeparator();
        popup.add(addInv);
        popup.add(sell);
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



    private Integer promptSellQuantityUnbounded(int displayMax) {
        final JDialog dlg = new JDialog(this, "Sell Item", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout(15, 15));
        dlg.getContentPane().setBackground(BG);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(true);
        center.setBackground(BG);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(12, 12, 12, 12);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0;

        JLabel lbl = new JLabel("Quantity to sell (max " + displayMax + "):");
        lbl.setForeground(FG);
        center.add(lbl, gc);

        gc.gridy = 1;
        IntField qtyField = new IntField("1");
        qtyField.setForeground(FG);
        qtyField.setCaretColor(FG);
        qtyField.setColumns(10);
        center.add(qtyField, gc);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        actions.setBackground(BG);
        JButton cancel = VinylUiKit.grayButton("Cancel");
        JButton ok = VinylUiKit.redButton("Sell");

        final Integer[] result = new Integer[1];

        cancel.addActionListener(e -> {
            result[0] = null;
            dlg.dispose();
        });
        ok.addActionListener(e -> {
            String t = qtyField.getText().trim();
            int val = 0;
            try { val = t.isEmpty() ? 0 : Integer.parseInt(t); } catch (Exception ignored) {}
            if (val < 1) val = 1;
            // Note: no clamping here
            result[0] = val;
            dlg.dispose();
        });

        actions.add(cancel);
        actions.add(ok);

        dlg.add(center, BorderLayout.CENTER);
        dlg.add(actions, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return result[0];
    }

    // Pill-buttons dialog for oversell: returns 0=Sell max, 1=Re-enter, 2=Cancel
    private int showOverSellDialog(int requested, int available) {
        final JDialog dlg = new JDialog(this, "Sell", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout(15, 15));
        dlg.getContentPane().setBackground(BG);

        // Message
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(true);
        center.setBackground(BG);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(12, 16, 12, 16);
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;

        JLabel msg = new JLabel(String.format("Requested %d exceeds available (%d).", requested, available));
        msg.setForeground(FG);
        center.add(msg, gc);

        // Pill buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        actions.setBackground(BG);
        JButton sellMax = VinylUiKit.redButton("Sell " + available);
        JButton reenter = VinylUiKit.grayButton("Re-enter");
        JButton cancel = VinylUiKit.grayButton("Cancel");

        final int[] result = {-1};
        sellMax.addActionListener(e -> { result[0] = 0; dlg.dispose(); });
        reenter.addActionListener(e -> { result[0] = 1; dlg.dispose(); });
        cancel.addActionListener(e -> { result[0] = 2; dlg.dispose(); });

        actions.add(cancel);
        actions.add(reenter);
        actions.add(sellMax);

        dlg.add(center, BorderLayout.CENTER);
        dlg.add(actions, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return result[0];
    }

    // Pill-button dialog for "no inventory to sell"
    private void showNoInventoryDialog() {
        final JDialog dlg = new JDialog(this, "Sell", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout(15, 15));
        dlg.getContentPane().setBackground(BG);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(true);
        center.setBackground(BG);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(12, 16, 12, 16);
        gc.gridx = 0; gc.gridy = 0; gc.anchor = GridBagConstraints.WEST;

        JLabel msg = new JLabel("No inventory to sell.");
        msg.setForeground(FG);
        center.add(msg, gc);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        actions.setBackground(BG);
        JButton ok = VinylUiKit.redButton("OK");
        ok.addActionListener(e -> dlg.dispose());
        actions.add(ok);

        dlg.add(center, BorderLayout.CENTER);
        dlg.add(actions, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }



    private Integer promptAddQuantity() {
        final JDialog dlg = new JDialog(this, "Add Inventory", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout(15, 15));
        dlg.getContentPane().setBackground(BG);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(true);
        center.setBackground(BG);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(12, 12, 12, 12);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0;

        JLabel lbl = new JLabel("Quantity to add:");
        lbl.setForeground(FG);
        center.add(lbl, gc);

        gc.gridy = 1;
        IntField qtyField = new IntField("1");
        qtyField.setForeground(FG);
        qtyField.setCaretColor(FG);
        qtyField.setColumns(10);
        center.add(qtyField, gc);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        actions.setBackground(BG);
        JButton cancel = VinylUiKit.grayButton("Cancel");
        JButton ok = VinylUiKit.redButton("Add");

        final Integer[] result = new Integer[1];

        cancel.addActionListener(e -> { result[0] = null; dlg.dispose(); });
        ok.addActionListener(e -> {
            String t = qtyField.getText().trim();
            int val = 0;
            try { val = t.isEmpty() ? 0 : Integer.parseInt(t); } catch (Exception ignored) {}
            if (val < 1) val = 1;
            result[0] = val;
            dlg.dispose();
        });

        actions.add(cancel);
        actions.add(ok);

        dlg.add(center, BorderLayout.CENTER);
        dlg.add(actions, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return result[0];
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
        // New fields
        private JTextField priceField;
        private IntField countField;

        PropertiesDialog(Frame owner, Vinyl.Song song, boolean isNew) {
            super(owner, isNew ? "Add Song" : "Song Properties", true);
            this.song = song; this.selectedCoverPath = song.coverPath;

            // Make the window borderless and transparent
            setUndecorated(true);
            setOpacity(0.95f);
            setBackground(new Color(0, 0, 0, 0)); // fully transparent window

            // Card panel that stays opaque on top of the transparent window
            JPanel card = new JPanel(new BorderLayout());
            card.setOpaque(false);
            card.setBorder(new EmptyBorder(10, 10, 10, 10)); // outer margin

            JPanel cardInner = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int arc = 18;
                    g2.setColor(BG); // solid dialog background
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            cardInner.setOpaque(false);
            cardInner.setBorder(new EmptyBorder(16, 16, 16, 16));
            card.add(cardInner, BorderLayout.CENTER);

            setContentPane(cardInner); // everything else goes into the rounded card

            setSize(720, 540);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());
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
            tabs.addTab("Inventory", buildInventoryTab());

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            actions.setBackground(BG);
            JButton cancel = VinylUiKit.grayButton("Cancel");
            JButton save = VinylUiKit.redButton("Save");
            cancel.addActionListener(e -> dispose());
            save.addActionListener(e -> {
                if (applyChanges()) {
                    saved = true;
                    // Notify parent that changes have been saved
                    if (owner instanceof VinylGui gui) {
                        gui.showToast("Song saved");
                    }
                    dispose();
                }
            });
            actions.add(cancel);
            actions.add(save);

            cardInner.add(tabs, BorderLayout.CENTER);
            cardInner.add(actions, BorderLayout.SOUTH);
        }


        private JPanel buildGeneralTab() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(BG);
            // Remove panel border to match "no border" requirement
            p.setBorder(BorderFactory.createEmptyBorder());
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4, 8, 8, 8);
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
            // moved priceField and countField to Inventory tab

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

        private JPanel buildInventoryTab() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(BG);
            p.setBorder(BorderFactory.createEmptyBorder());
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(8, 8, 8, 8);
            gc.anchor = GridBagConstraints.WEST;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1.0;

            // Initialize fields moved from General to Inventory
            priceField = textField(song.price == 0.0 ? "" : String.valueOf(song.price));
            countField = new IntField(String.valueOf(song.count));

            int r = 0;
            addRow(p, gc, r++, "Price", priceField);
            addRow(p, gc, r++, "Count", countField);
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
            // Larger preview area to show big artwork
            coverPreview.setPreferredSize(new Dimension(300, 300));

            updateCoverPreview();

            JButton artworkButton = makeArtworkButton();

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            bottom.setBackground(PANEL);
            bottom.add(artworkButton);
            p.add(coverPreview, BorderLayout.CENTER);
            p.add(bottom, BorderLayout.SOUTH);
            return p;
        }

        private JButton makeArtworkButton() {
            // Use HTML so "Add" and "Artwork" are red, "+" is white
            String label =
                    "<html>" + "<span style='color: #FFFFFF;'>+  </span>" +
                            "<span style='color: rgb(210,54,54);'> Add </span>" +
                            "<span style='color: rgb(210,54,54);'> Artwork</span>" +
                            "</html>";

            JButton b = new JButton(label) {
                {
                    setRolloverEnabled(true);
                }

                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (getModel().isRollover()) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int pad = 2;
                        // Same soft rounded hover background as the kebab button
                        g2.setColor(new Color(200, 200, 200, 40));
                        g2.fillRoundRect(pad, pad,
                                getWidth() - pad * 2 - 1,
                                getHeight() - pad * 2 - 1,
                                10, 10);
                        g2.dispose();
                    }
                }
            };
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setBorder(new EmptyBorder(6, 12, 6, 12));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            b.addActionListener(e -> {
                // If no artwork yet, go straight to chooser
                if (selectedCoverPath == null || selectedCoverPath.isEmpty()) {
                    chooseArtwork();
                    return;
                }

                // If artwork exists, show a small menu for change/remove
                // Use rounded popup directly instead of stylePopup
                JPopupMenu menu = new TranslucentPopup();
                //menu.setLightWeightPopupEnabled(true);


                JMenuItem change = new JMenuItem("Change Artwork...");
                JMenuItem remove = new JMenuItem("Remove Artwork");

                change.addActionListener(ev -> chooseArtwork());
                remove.addActionListener(ev -> {
                    selectedCoverPath = null;
                    updateCoverPreview();
                });

                menu.add(change);
                menu.add(remove);
                menu.show(b, 0, b.getHeight());
            });

            return b;
        }


        private void chooseArtwork() {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Choose cover image");
            fc.setFileFilter(new FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "gif"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                selectedCoverPath = f.getAbsolutePath();
                updateCoverPreview();
            }
        }



        private void updateCoverPreview() {
            final int PREVIEW_SIZE = 300;
            ImageIcon icon = null;

            // 1) Try to load from selectedCoverPath (URL or file), in high resolution
            if (selectedCoverPath != null && !selectedCoverPath.isEmpty()) {
                try {
                    if (selectedCoverPath.startsWith("http://") || selectedCoverPath.startsWith("https://")) {
                        // Apple Music-style URLs: swap trailing "/WxHbb.jpg" for "/600x600bb.jpg"
                        String urlString = selectedCoverPath;
                        int lastSlash = urlString.lastIndexOf('/');
                        if (lastSlash != -1 && urlString.substring(lastSlash).matches("/\\d+x\\d+bb\\.jpg")) {
                            urlString = urlString.substring(0, lastSlash) + "/300x300bb.jpg";
                        }

                        URL url = new URL(urlString);
                        BufferedImage img = ImageIO.read(url);
                        if (img != null) {
                            Image scaled = img.getScaledInstance(PREVIEW_SIZE, PREVIEW_SIZE, Image.SCALE_SMOOTH);
                            icon = new ImageIcon(scaled);
                        }
                    } else {
                        // Local file path
                        File f = new File(selectedCoverPath);
                        BufferedImage img = ImageIO.read(f);
                        if (img != null) {
                            Image scaled = img.getScaledInstance(PREVIEW_SIZE, PREVIEW_SIZE, Image.SCALE_SMOOTH);
                            icon = new ImageIcon(scaled);
                        }
                    }
                } catch (Exception ex) {
                    // ignore and fall through to other options
                }
            }

            if (icon == null && song.coverIcon != null) {
                Image img = song.coverIcon.getImage();
                Image scaled = img.getScaledInstance(PREVIEW_SIZE, PREVIEW_SIZE, Image.SCALE_SMOOTH);
                icon = new ImageIcon(scaled);
            }

            if (icon == null) {
                icon = Vinyl.Song.placeholderIcon(PREVIEW_SIZE, PREVIEW_SIZE);
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
                // Persist price and count (stored as double and int)
                String priceTxt = priceField.getText().trim();
                song.price = priceTxt.isEmpty() ? 0.0 : Double.parseDouble(priceTxt);
                String countTxt = countField.getText().trim();
                song.count = countTxt.isEmpty() ? 0 : Integer.parseInt(countTxt);
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
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

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
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setFont(font);
            g2.setColor(FG);
            int y = textRect.y + metrics.getAscent();
            g2.drawString(title, textRect.x, y);
            g2.dispose();
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
        private final List<JLabel> stars = new ArrayList<>();

        StarBar(int initial) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            setValue(initial);
            for (int i = 1; i <= 5; i++) {
                final int idx = i;
                JLabel star = new JLabel("☆");
                // Old:
                star.setFont(FontManager.starFont(18f));

                // New: explicitly use SF Pro Display for this component
                //star.setFont(FontManager.sfProPlain(22f));
                star.setForeground(RED);
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
