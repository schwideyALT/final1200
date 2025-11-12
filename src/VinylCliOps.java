import java.util.*;

// CLI-only utilities. Keeps Main simple and independent of Swing.
public final class VinylCliOps {
    private VinylCliOps() {}

    // ---------- Data holder used by the CLI ----------
    public static class Record {
        public int id;
        public String name;
        public String artist;
        public String album;
        public String genre;
        public Integer bpm;      // nullable
        public Integer rating;   // nullable 1..5
        public String length;    // mm:ss
        public int quantity;

        public Record(int id, String name, String artist, String album, String genre,
                      Integer bpm, Integer rating, String length, int quantity) {
            this.id = id; this.name = name; this.artist = artist; this.album = album; this.genre = genre;
            this.bpm = bpm; this.rating = rating; this.length = length; this.quantity = quantity;
        }

        @Override public String toString() {
            return String.format(Locale.US,
                    "#%d | %s | %s | %s | %s | bpm:%s | rate:%s | len:%s | qty:%d",
                    id, name, artist, album, genre,
                    bpm == null ? "-" : bpm.toString(),
                    rating == null ? "-" : rating.toString(),
                    length, quantity);
        }
    }

    // ---------- Incrementing ID generator ----------
    public static class IdGen {
        private int next = 1;
        public int nextId() { return next++; }
    }

    // ---------- Parsing helpers ----------
    public static Integer parseIntOpt(String s) {
        try { return s == null || s.trim().isEmpty() ? null : Integer.parseInt(s.trim()); }
        catch (Exception e) { return null; }
    }
    public static int parseIntOr(String s, int d) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; }
    }
    public static Integer clampRating15(Integer v) {
        if (v == null) return null;
        return Math.max(1, Math.min(5, v));
    }

    // ---------- CRUD on List<Record> ----------
    public static Record add(List<Record> inv, IdGen ids,
                             String name, String artist, String album, String genre,
                             Integer bpm, Integer rating, String length, int qty) {
        Record r = new Record(ids.nextId(), name, artist, album, genre, bpm, clampRating15(rating), length, qty);
        inv.add(r);
        return r;
    }

    public static Record findById(List<Record> inv, int id) {
        for (Record r : inv) if (r.id == id) return r; return null;
    }

    public static void remove(List<Record> inv, int id) {
        Iterator<Record> it = inv.iterator();
        while (it.hasNext()) if (it.next().id == id) { it.remove(); return; }
    }

    // ---------- Printing ----------
    public static void printTable(List<Record> inv) {
        if (inv.isEmpty()) { System.out.println("No items."); return; }
        System.out.printf("%-4s %-24s %-16s %-16s %-10s %-4s %-5s %-6s %-4s%n",
                "ID","Name","Artist","Album","Genre","BPM","Rate","Len","Qty");
        System.out.println("-".repeat(100));
        for (Record r : inv) {
            System.out.printf("%-4d %-24s %-16s %-16s %-10s %-4s %-5s %-6s %-4d%n",
                    r.id,
                    cut(r.name,24),
                    cut(r.artist,16),
                    cut(r.album,16),
                    cut(r.genre,10),
                    r.bpm==null? "" : r.bpm.toString(),
                    r.rating==null? "" : r.rating.toString(),
                    r.length,
                    r.quantity);
        }
    }

    public static String cut(String s, int n) {
        if (s == null) return "";
        return s.length()<=n? s : s.substring(0, Math.max(0, n-1)) + "…";
    }
}