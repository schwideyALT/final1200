import java.util.*;

public class Main {
    public static class Vinyl {
        int id;
        String name, artist, album, genre, length; // length as mm:ss
        Integer bpm, rating; // nullable
        int quantity;

        Vinyl(int id, String name, String artist, String album, String genre,
              Integer bpm, Integer rating, String length, int quantity) {
            this.id = id; this.name = name; this.artist = artist; this.album = album;
            this.genre = genre; this.bpm = bpm; this.rating = rating; this.length = length;
            this.quantity = quantity;
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

    public static final Scanner in = new Scanner(System.in);
    public static final List<Vinyl> inv = new ArrayList<>();
    private static int nextId = 1;
    public static int nextId() { return nextId++; }

    public static void main(String[] args) {
        System.out.print("Use GUI? y/n: ");
        String ui = in.nextLine().trim().toLowerCase(Locale.ROOT);
        if (ui.startsWith("y")) {
            try {
                VinylGui.launch();
                return;
            } catch (Throwable t) {
                System.out.println("GUI failed, using CLI.");
            }
        }
        while (true) {
            System.out.println();
            System.out.println("Vinyl Inventory");
            System.out.println("1) Add  2) View  3) Edit  4) Exit");
            System.out.print("Choose: ");
            String c = in.nextLine().trim();
            if (c.equals("1")) add();
            else if (c.equals("2")) view();
            else if (c.equals("3")) edit();
            else if (c.equals("4")) return;
            else System.out.println("Invalid choice.");
        }
    }

    static void add() {
        System.out.println("Add vinyl");
        String name = ask("Track name");
        String artist = ask("Artist");
        String album = ask("Album");
        String genre = ask("Genre");
        Integer bpm = parseIntOpt(askOpt("BPM (blank ok)"));
        Integer rating = clamp15(parseIntOpt(askOpt("Rating 1..5 (blank ok)")));
        String length = ask("Length mm:ss");
        int qty = parseIntOr(ask("Quantity"), 0);
        Vinyl v = new Vinyl(nextId(), name, artist, album, genre, bpm, rating, length, qty);
        inv.add(v);
        System.out.println("Added: " + v);
    }

    static void view() {
        if (inv.isEmpty()) { System.out.println("No items."); return; }
        System.out.printf("%-4s %-24s %-16s %-16s %-10s %-4s %-5s %-6s %-4s%n",
                "ID","Name","Artist","Album","Genre","BPM","Rate","Len","Qty");
        System.out.println("-".repeat(100));
        for (Vinyl v : inv) {
            System.out.printf("%-4d %-24s %-16s %-16s %-10s %-4s %-5s %-6s %-4d%n",
                    v.id, cut(v.name,24), cut(v.artist,16), cut(v.album,16), cut(v.genre,10),
                    v.bpm==null? "": v.bpm.toString(), v.rating==null? "": v.rating.toString(), v.length, v.quantity);
        }
    }

    static void edit() {
        if (inv.isEmpty()) { System.out.println("No items."); return; }
        int id = parseIntOr(ask("Enter id to edit"), -1);
        Vinyl v = find(id);
        if (v == null) { System.out.println("Not found."); return; }
        System.out.println("Editing: " + v);
        String s;
        s = askEdit("Track name", v.name); if (!s.isEmpty()) v.name = s;
        s = askEdit("Artist", v.artist); if (!s.isEmpty()) v.artist = s;
        s = askEdit("Album", v.album); if (!s.isEmpty()) v.album = s;
        s = askEdit("Genre", v.genre); if (!s.isEmpty()) v.genre = s;
        s = askEdit("BPM", v.bpm==null? "" : v.bpm.toString()); if (!s.isEmpty()) v.bpm = parseIntOpt(s);
        s = askEdit("Rating 1..5", v.rating==null? "" : v.rating.toString()); if (!s.isEmpty()) v.rating = clamp15(parseIntOpt(s));
        s = askEdit("Length mm:ss", v.length); if (!s.isEmpty()) v.length = s;
        s = askEdit("Quantity", Integer.toString(v.quantity)); if (!s.isEmpty()) v.quantity = parseIntOr(s, v.quantity);
        System.out.println("Updated: " + v);
    }

    // helpers
    static String ask(String p) {
        while (true) { System.out.print(p + ": "); String s = in.nextLine().trim(); if (!s.isEmpty()) return s; System.out.println("Required."); }
    }
    static String askOpt(String p) { System.out.print(p + ": "); return in.nextLine().trim(); }
    static String askEdit(String p, String cur) { System.out.print(p + " [" + cur + "] (enter keep): "); return in.nextLine().trim(); }
    static Integer parseIntOpt(String s) { try { return s.isEmpty()? null : Integer.parseInt(s); } catch (Exception e) { return null; } }
    static int parseIntOr(String s, int d) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; } }
    static Integer clamp15(Integer v) { if (v == null) return null; return Math.max(1, Math.min(5, v)); }
    static Vinyl find(int id) { for (Vinyl v : inv) if (v.id == id) return v; return null; }
    static String cut(String s, int n) { return s.length()<=n? s : s.substring(0,n-1) + "…"; }
}
