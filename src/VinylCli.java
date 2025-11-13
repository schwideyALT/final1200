// VinylCli.java
import java.io.File;
import java.util.List;
import java.util.Scanner;

public class VinylCli {
    private static final File XML = new File("src/xml/songs.xml");

    public static void run() {
        Vinyl.SongTableModel model = new Vinyl.SongTableModel();

        // Load on start
        if (XML.exists()) {
            try {
                model.setSongs(Vinyl.XmlStore.load(XML));
            } catch (Exception e) {
                System.err.println("Failed to load songs.xml: " + e.getMessage());
            }
        }

        // Auto-save on change
        model.addChangeListener(() -> {
            try {
                File parent = XML.getParentFile();
                if (parent != null) parent.mkdirs();
                Vinyl.XmlStore.save(XML, model.getAll());
            } catch (Exception e) {
                System.err.println("Failed to save songs.xml: " + e.getMessage());
            }
        });

        System.out.println("Vinyl CLI. Type 'help' for commands. Using songs.xml in current directory.");
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line == null) break;
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 0 || parts[0].isEmpty()) continue;
            String cmd = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1] : "";

            try {
                switch (cmd) {
                    case "help":
                        printHelp();
                        break;
                    case "list":
                        list(model);
                        break;
                    case "add":
                        add(model, arg);
                        break;
                    case "delete":
                    case "del":
                    case "rm":
                        delete(model, arg);
                        break;
                    case "rate":
                        rate(model, arg);
                        break;
                    case "explicit":
                    case "e":
                        explicit(model, arg);
                        break;
                    case "save":
                        File parent = XML.getParentFile();
                        if (parent != null) parent.mkdirs();
                        Vinyl.XmlStore.save(XML, model.getAll());
                        System.out.println("Saved.");
                        break;
                    case "gui":
                    case "5":
                        System.out.println("Launching GUI...");
                        VinylGui.launch();
                        return;
                    case "quit":
                    case "exit":
                        return;
                    default:
                        System.out.println("Unknown command. Type 'help'.");
                }
            } catch (Exception ex) {
                System.err.println("Error: " + ex.getMessage());
            }
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  list                          - list songs");
        System.out.println("  add title=... artist=...      - add a song (optional: album=..., genre=..., bpm=120, len=3:45, explicit=true, rating=3)");
        System.out.println("  delete <id>                   - delete by id");
        System.out.println("  rate <id> <0..5>              - set rating");
        System.out.println("  explicit <id> <true|false>    - set explicit flag");
        System.out.println("  save                          - force save");
        System.out.println("  gui                           - open GUI and exit CLI");
        System.out.println("  exit                          - quit");
    }

    private static void list(Vinyl.SongTableModel model) {
        List<Vinyl.Song> rows = model.getAll();
        if (rows.isEmpty()) {
            System.out.println("(no songs)");
            return;
        }
        for (Vinyl.Song s : rows) {
            System.out.printf("#%d  %s — %s  [album=%s, genre=%s, bpm=%d, len=%s, explicit=%s, rating=%d]%n",
                    s.id, s.title, s.artist, s.album, s.genre, s.bpm,
                    Vinyl.Song.formatDuration(s.lengthSeconds), s.explicit, s.rating);
        }
    }

    private static void add(Vinyl.SongTableModel model, String arg) {
        if (arg.isEmpty()) {
            System.out.println("Usage: add title=... artist=... [album=...] [genre=...] [bpm=120] [len=3:45] [explicit=true] [rating=3]");
            System.out.println("Examples:");
            System.out.println("  add title=\"My Song\" artist=\"Some Artist\"");
            System.out.println("  add title=Track artist=Band bpm=120 len=3:45 explicit=true rating=4");
            return;
        }

        // Parse pairs like: key = value, allowing quotes and spaces:
        // - key = value
        // - key= "value with spaces"
        // - key = 'value with spaces'
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("([A-Za-z]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s]+))");
        java.util.regex.Matcher m = p.matcher(arg);
        java.util.Map<String, String> kv = new java.util.HashMap<>();
        while (m.find()) {
            String key = m.group(1).toLowerCase();
            String value = m.group(3) != null ? m.group(3)
                           : m.group(4) != null ? m.group(4)
                           : m.group(5);
            kv.put(key, value);
        }

        Vinyl.Song s = new Vinyl.Song();
        s.title = kv.getOrDefault("title", "");
        s.artist = kv.getOrDefault("artist", "");
        s.album = kv.getOrDefault("album", "");
        s.genre = kv.getOrDefault("genre", "");
        s.bpm = parseInt(kv.getOrDefault("bpm", ""), 0);
        if (kv.containsKey("len")) {
            s.lengthSeconds = Vinyl.Song.parseDuration(kv.get("len"));
        }
        if (kv.containsKey("explicit")) {
            s.explicit = Boolean.parseBoolean(kv.get("explicit"));
        }
        if (kv.containsKey("rating")) {
            s.rating = Math.max(0, Math.min(5, parseInt(kv.get("rating"), 0)));
        }
        if (kv.containsKey("cover")) {
            s.coverPath = kv.get("cover");
        }

        if (s.title.trim().isEmpty() || s.artist.trim().isEmpty()) {
            System.out.println("title and artist are required. Example: add title=\"My Title\" artist=\"Some Artist\"");
            return;
        }

        s.id = model.nextId();
        model.addSong(s);
        System.out.println("Added id=" + s.id);
    }

    private static void delete(Vinyl.SongTableModel model, String arg) {
        int id = parseInt(arg, -1);
        if (id <= 0) { System.out.println("Usage: delete <id>"); return; }
        int idx = findById(model, id);
        if (idx < 0) { System.out.println("No such id: " + id); return; }
        model.removeAt(idx);
        System.out.println("Deleted id=" + id);
    }

    private static void rate(Vinyl.SongTableModel model, String arg) {
        String[] p = arg.split("\\s+");
        if (p.length != 2) { System.out.println("Usage: rate <id> <0..5>"); return; }
        int id = parseInt(p[0], -1);
        int rating = Math.max(0, Math.min(5, parseInt(p[1], 0)));
        int idx = findById(model, id);
        if (idx < 0) { System.out.println("No such id: " + id); return; }
        model.setValueAt(rating, idx, 9);
        System.out.println("Rated id=" + id + " = " + rating);
    }

    private static void explicit(Vinyl.SongTableModel model, String arg) {
        String[] p = arg.split("\\s+");
        if (p.length != 2) { System.out.println("Usage: explicit <id> <true|false>"); return; }
        int id = parseInt(p[0], -1);
        boolean flag = Boolean.parseBoolean(p[1]);
        int idx = findById(model, id);
        if (idx < 0) { System.out.println("No such id: " + id); return; }
        Vinyl.Song s = model.getSong(idx);
        s.explicit = flag;
        // fire refresh + notify save
        model.fireTableRowsUpdated(idx, idx);
        // since we updated the object, also notify modelChanged
        try {
            java.lang.reflect.Method m = model.getClass().getDeclaredMethod("notifyChanged");
            m.setAccessible(true);
            m.invoke(model);
        } catch (Exception ignored) {}
        System.out.println("Explicit id=" + id + " = " + flag);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static int findById(Vinyl.SongTableModel model, int id) {
        List<Vinyl.Song> rows = model.getAll();
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id == id) return i;
        return -1;
    }
}