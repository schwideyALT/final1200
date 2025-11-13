import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
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

// Domain model and storage helpers shared by the GUI.
public final class Vinyl {
    private Vinyl() {}

    // ---------- Entity ----------
    public static class Song {
        public int id;
        public String title = "";
        public String artist = "";
        public String album = "";
        public String genre = "";
        public int bpm = 0;
        public int lengthSeconds = 0; // total seconds
        public boolean explicit = false;
        public int rating = 0; // 0..5
        public String coverPath; // file path or null
        public transient ImageIcon coverIcon; // cached scaled icon

        public Song() {}
        public Song(int id, String title, String artist, String album, String genre,
                    int bpm, int lengthSeconds, boolean explicit, int rating, String coverPath) {
            this.id = id; this.title = title; this.artist = artist; this.album = album;
            this.genre = genre; this.bpm = bpm; this.lengthSeconds = lengthSeconds;
            this.explicit = explicit; this.rating = rating; this.coverPath = coverPath;
        }

        public boolean matches(String q) {
            String all = (title + " " + artist + " " + album + " " + genre + " " + id + " " + bpm + " " + formatDuration(lengthSeconds)).toLowerCase();
            return all.contains(q);
        }

        public static String formatDuration(int secs) {
            int m = secs / 60; int s = secs % 60; return String.format("%d:%02d", m, s);
        }
        public static int parseDuration(String mmss) {
            String t = mmss.trim(); if (t.isEmpty()) return 0; String[] parts = t.split(":");
            if (parts.length == 1) return Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[0]); int s = Integer.parseInt(parts[1]);
            if (s < 0 || s > 59) throw new IllegalArgumentException("Seconds must be 00..59");
            return m * 60 + s;
        }

        public ImageIcon getScaledCover(int size) {
            try {
                if (coverPath == null || coverPath.isEmpty()) return placeholderIcon(size, size);
                if (coverIcon != null && coverIcon.getIconWidth() == size && coverIcon.getIconHeight() == size) return coverIcon;
                BufferedImage img = ImageIO.read(new File(coverPath));
                if (img == null) return placeholderIcon(size, size);
                Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
                coverIcon = new ImageIcon(scaled);
                return coverIcon;
            } catch (Exception e) {
                return placeholderIcon(size, size);
            }
        }

        public static ImageIcon placeholderIcon(int w, int h) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(60, 60, 60));
            g.fillRoundRect(0, 0, w, h, 16, 16);
            g.setColor(new Color(90, 90, 90));
            int pad = Math.max(2, w / 5);
            g.fillRoundRect(pad, pad, Math.max(4, w - pad * 2), Math.max(4, h - pad * 2), 6, 6);
            g.dispose();
            return new ImageIcon(img);
        }
    }

    // ---------- Table model ----------
    public static class SongTableModel extends AbstractTableModel {
        private final String[] cols = {"ID","Cover","Title","Artist","Album","Genre","BPM","Length","Explicit","Rating","Actions"};
        private final Class<?>[] types = {Integer.class, ImageIcon.class, String.class, String.class, String.class, String.class, Integer.class, String.class, Boolean.class, Integer.class, Object.class};
        private final List<Song> rows = new ArrayList<>();

        // Simple change listeners so GUI can auto-save
        public interface ChangeListener { void modelChanged(); }
        private final List<ChangeListener> listeners = new ArrayList<>();
        public void addChangeListener(ChangeListener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
        public void removeChangeListener(ChangeListener l) { listeners.remove(l); }
        private void notifyChanged() { for (ChangeListener l : new ArrayList<>(listeners)) l.modelChanged(); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return types[c]; }
        @Override public boolean isCellEditable(int r, int c) { return c == 9 || c == 10; }

        @Override public Object getValueAt(int r, int c) {
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
                case 10: return "";
            }
            return null;
        }
        @Override public void setValueAt(Object val, int r, int c) {
            if (c == 9 && val instanceof Integer) {
                rows.get(r).rating = (Integer) val;
                notifyChanged();
            }
        }

        public void addSong(Song s) {
            rows.add(s);
            int i = rows.size() - 1;
            fireTableRowsInserted(i, i);
            notifyChanged();
        }
        public void removeAt(int modelRow) {
            if (modelRow >= 0 && modelRow < rows.size()) {
                rows.remove(modelRow);
                fireTableRowsDeleted(modelRow, modelRow);
                notifyChanged();
            }
        }
        public int indexOf(Song s) { return rows.indexOf(s); }
        public Song getSong(int modelRow) { return rows.get(modelRow); }
        public List<Song> getAll() { return new ArrayList<>(rows); }
        public void setSongs(List<Song> list) {
            rows.clear();
            rows.addAll(list);
            fireTableDataChanged();
            notifyChanged();
        }
        public int nextId() { int max = 0; for (Song s : rows) max = Math.max(max, s.id); return max + 1; }

        // Call this after mutating a song object in-place (e.g., from a dialog)
        public void songUpdated(int row) {
            if (row >= 0 && row < rows.size()) {
                fireTableRowsUpdated(row, row);
                notifyChanged();
            }
        }
    }

    // ---------- XML store ----------
    public static final class XmlStore {
        public static void save(File file, List<Song> songs) throws Exception {
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

        public static List<Song> load(File file) throws Exception {
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
        private static int parseIntSafe(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }
        private static String textOf(Element e, String tag) { NodeList nl = e.getElementsByTagName(tag); if (nl.getLength() == 0) return ""; return nl.item(0).getTextContent(); }
    }
}