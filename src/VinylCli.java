// VinylCli.java

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;


public class VinylCli {
    private static boolean SHOW_ONLY_IN_STOCK = false; // when true, list shows only items with count > 0
    // method to run the CLI
    private static final File XML = new File("src/xml/songs.xml");
    public static void run() {
        java.util.Scanner sc = new java.util.Scanner(System.in);
        System.out.println("Vinyl CLI");
        System.out.println("Type 'help' for commands.");
        // Load on start
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

        // prompt user for commands
        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            if ("exit".equals(cmd) || "quit".equals(cmd)) {
                System.out.println("Goodbye!");
                break;
            }

            switch (cmd) {
                case "help":
                    System.out.println("Commands:");
                    System.out.println("  list                          - list songs");
                    System.out.println("  add title=... artist=...      - add a song (optional: album=..., genre=..., bpm=120, len=3:45, explicit=true, rating=3, price=double, count =...)");
                    System.out.println("  edit <id> flag=...            - edit a song (optional: album=..., genre=..., bpm=120, len=3:45, explicit=true, rating=3, price=double, count =...)");
                    System.out.println("  delete <id>                   - delete by id");
                    System.out.println("  rate <id> <0..5>              - set rating");
                    System.out.println("  explicit <id> <true|false>    - set explicit flag");
                    System.out.println("  instock <on|off>              - when on, list only shows items with count > 0");
                    System.out.println("  save                          - force save");
                    System.out.println("  gui                           - open GUI and exit CLI");
                    System.out.println("  exit                          - quit");
                    System.out.println("  sell <id> <count>             - Decrease inventory for a song by id");
                    System.out.println("  addinv <id> <count>           - increase inventory for a song by id");
                    System.out.println("  logs                          - view activity log");
                    System.out.println("  exportlogs [file]             - export activity log as CSV");
                    break;

                case "list":
                    list(model);
                    break;
                case "add":
                    add(model, line.substring(3).trim());
                    break;
                case "edit":
                    edit(model, line.substring(4).trim());
                    break;
                case "delete":
                case "del":
                case "rm":
                    delete(model, line.substring(6).trim());
                    break;
                case "rate":
                    rate(model, line.substring(4).trim());
                    break;
                case "explicit":
                case "e":
                    explicit(model, line.substring(8).trim());
                    break;
                case "instock":
                    toggleInStockMode(line.substring(7).trim());
                    break;
                case "save":
                    File parent = XML.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try {
                        Vinyl.XmlStore.save(XML, model.getAll());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println("Saved.");
                    break;
                case "gui":
                case "5":
                    System.out.println("Launching GUI...");
                    VinylGui.launch();
                    return;
                case "addinv": {
                    if (parts.length < 3) {
                        System.out.println("Usage: addinv <id> <count>");
                        break;
                    }
                    try {
                        int id = Integer.parseInt(parts[1]);
                        int count = Integer.parseInt(parts[2]);
                        addInventory(model, id, count);
                        
                    } catch (NumberFormatException nfe) {
                        System.out.println("Invalid numbers. Usage: addinv <id> <count>");
                    } catch (Exception e) {
                        System.out.println("Add inventory failed: " + e.getMessage());
                    }

                    break;
                }

                case "sell": {
                    if (parts.length < 3) {
                        System.out.println("Usage: sell <id> <count>");
                        break;
                    }
                    try {
                        int id = Integer.parseInt(parts[1]);
                        int count = Integer.parseInt(parts[2]);
                        sell(model, id, count);
                    } catch (NumberFormatException nfe) {
                        System.out.println("Invalid numbers. Usage: sell <id> <count>");
                    } catch (Exception e) {
                        System.out.println("Sell failed: " + e.getMessage());
                    }
                    break;
                }

                case "logs":
                    viewLogs();                    // NEW
                    break;

                case "exportlogs":
                    // allow optional path after command
                    String arg = line.length() > "exportlogs".length()
                            ? line.substring("exportlogs".length()).trim()
                            : "";
                    exportLogsCli(arg);             // NEW
                    break;

                default:
                    System.out.println("Unknown command. Type 'help' for a list of commands.");
            }
        }
    }

    // list method that outputs list of Songs
    private static void list(Vinyl.SongTableModel model) {
        List<Vinyl.Song> rows = model.getAll();
        if (rows.isEmpty()) {
            System.out.println("(no songs)");
            return;
        }
        for (Vinyl.Song s : rows) {
            if (SHOW_ONLY_IN_STOCK && s.count <= 0) continue; // hide zero stock when mode is on
            System.out.printf("#%d  %s — %s  [album=%s, genre=%s, bpm=%d, len=%s, explicit=%s, rating=%d, price=%.2f, count=%d]%n",
                    s.id, s.title, s.artist, s.album, s.genre, s.bpm,
                    Vinyl.Song.formatDuration(s.lengthSeconds), s.explicit, s.rating, s.price, s.count);
        }
    }
    // add a song to the table
    private static void add(Vinyl.SongTableModel model, String arg) {
        if (arg.isEmpty()) {
            System.out.println("Usage: add title=... artist=... [album=...] [genre=...] [bpm=120] [len=3:45] [explicit=true] [rating=3]");
            System.out.println("Examples:");
            System.out.println("  add title=\"My Song\" artist=\"Some Artist\"");
            System.out.println("  add title=Track artist=Band bpm=120 len=3:45 explicit=true rating=4 price=0.99 count=123");
            return;
        }
        // parse arguments
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("([A-Za-z]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s]+))");
        java.util.regex.Matcher m = p.matcher(arg);
        java.util.Map<String, String> kv = new java.util.HashMap<>();
        // regex that captures text, and orders them accordingly
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
        if (kv.containsKey("price")) {
            s.price = parseDouble(kv.get("price"), 0.0);
        }
        if (kv.containsKey("count")) {
            s.count = parseInt(kv.get("count"), 0);
        }
        // require title and artist fields
        if (s.title.trim().isEmpty() || s.artist.trim().isEmpty()) {
            System.out.println("title and artist are required. Example: add title=\"My Title\" artist=\"Some Artist\"");
            return;
        }

        s.id = model.nextId();
        model.addSong(s);
        System.out.println("Added id=" + s.id);
    }
    // edit a song in the table
    private static void edit(Vinyl.SongTableModel model, String arg) {
        if (arg == null || arg.trim().isEmpty()) {
            System.out.println("Usage: edit <id> key=val ...   (fields: title, artist, album, genre, bpm, len, explicit, rating, cover, price, count)");
            return;
        }
        String[] parts = arg.trim().split("\\s+", 2);
        int id = parseInt(parts[0], -1);
        if (id <= 0) {
            System.out.println("edit: invalid id");
            return;
        }
        String kvs = parts.length > 1 ? parts[1] : "";
        if (kvs.isEmpty()) {
            System.out.println("edit: no fields provided");
            return;
        }
        int idx = findById(model, id);
        if (idx < 0) {
            System.out.println("No such id: " + id);
            return;
        }
        Vinyl.Song s = model.getSong(idx);

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("([A-Za-z]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s]+))");
        java.util.regex.Matcher m = p.matcher(kvs);
        java.util.Map<String, String> kv = new java.util.HashMap<>();
        // regex that captures text, and orders them accordingly
        while (m.find()) {
            String key = m.group(1).toLowerCase();
            String value = m.group(3) != null ? m.group(3)
                    : m.group(4) != null ? m.group(4)
                    : m.group(5);
            kv.put(key, value);
        }

        if (kv.containsKey("title"))   s.title = kv.get("title");
        if (kv.containsKey("artist"))  s.artist = kv.get("artist");
        if (kv.containsKey("album"))   s.album = kv.get("album");
        if (kv.containsKey("genre"))   s.genre = kv.get("genre");
        if (kv.containsKey("bpm"))     s.bpm = parseInt(kv.get("bpm"), s.bpm);
        if (kv.containsKey("len"))     s.lengthSeconds = Vinyl.Song.parseDuration(kv.get("len"));
        if (kv.containsKey("explicit")) s.explicit = Boolean.parseBoolean(kv.get("explicit"));
        if (kv.containsKey("rating"))  s.rating = Math.max(0, Math.min(5, parseInt(kv.get("rating"), s.rating)));
        if (kv.containsKey("cover"))   s.coverPath = kv.get("cover");
        if (kv.containsKey("price"))   s.price = parseDouble(kv.get("price"), s.price);
        if (kv.containsKey("count"))   s.count = parseInt(kv.get("count"), s.count);

        // fire refresh + notify save
        model.songUpdated(idx);
        System.out.println("Edited id=" + id);
    }

    private static void sell(Vinyl.SongTableModel model, int id, int count) {
        if (count <= 0) {
            System.out.println("Count must be a positive integer.");
            return;
        }
        int idx = findById(model, id);
        if (idx < 0) {
            System.out.println("No song found with id: " + id);
            return;
        }
        Vinyl.Song s = model.getSong(idx);
        int current = s.count;
        if (current < count) {
            System.out.println("Not enough inventory. Available: " + current + ", requested: " + count);
            return;
        }
        s.count = current - count;
        model.songUpdated(idx);

        String msg = "Sold " + count + " of \"" + s.title + "\" (id=" + id + "). Remaining: " + s.count + ".";
        String songDisplay = "ID " + s.id + ": " + s.title + " - " + s.artist;
        double totalPrice = s.price * count;

        // Record CLI sell action in the shared log
        LogEntry.logEvent(msg, songDisplay, totalPrice);

        System.out.println(msg);
    }

    private static void addInventory(Vinyl.SongTableModel model, int id, int count) {
        if (count <= 0) {
            System.out.println("Count must be a positive integer.");
            return;
        }
        int idx = findById(model, id);
        if (idx < 0) {
            System.out.println("No song found with id: " + id);
            return;
        }
        Vinyl.Song s = model.getSong(idx);
        s.count += count;
        model.songUpdated(idx);

        String msg = "Added " + count + " to \"" + s.title + "\" (id=" + id + "). New total: " + s.count + ".";
        String songDisplay = "ID " + s.id + ": " + s.title + " - " + s.artist;
        double totalPrice = s.price * count;

        // Record CLI inventory addition in the shared log
        LogEntry.logEvent(msg, songDisplay);

        System.out.println(msg);
    }

    //delete a song from the table
    private static void delete(Vinyl.SongTableModel model, String arg) {
        int id = parseInt(arg, -1);
        if (id <= 0) { System.out.println("Usage: delete <id>"); return; }
        int idx = findById(model, id);
        if (idx < 0) { System.out.println("No such id: " + id); return; }
        model.removeAt(idx);
        System.out.println("Deleted id=" + id);
    }

    //rate a song in the table
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

    //mark a song as explicit in the table
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
    // method to hide items that have coun
    private static void toggleInStockMode(String arg) {
        // convert null into an empty string and trim white spaces
        String val = arg == null ? "" : arg.trim().toLowerCase();
        if ("on".equals(val)) {
            SHOW_ONLY_IN_STOCK = true;
        } else if ("off".equals(val)) {
            SHOW_ONLY_IN_STOCK = false;
        } else {
            System.out.println("Usage: instock <on|off>");
            System.out.println("Current: " + (SHOW_ONLY_IN_STOCK ? "on" : "off"));
            return;
        }
        System.out.println("In-stock mode is now " + (SHOW_ONLY_IN_STOCK ? "ON (showing count > 0 only)" : "OFF (showing all)"));
    }

    //parse an integer from a string
    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
    // parse a double from a string
    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }


    //find a song by id in the table
    private static int findById(Vinyl.SongTableModel model, int id) {
        List<Vinyl.Song> rows = model.getAll();
        for (int i = 0; i < rows.size(); i++)
            if (rows.get(i).id == id) return i;
        return -1;
    }

    // Export the activity log from the CLI.
    // Usage: exportlogs [filePath]
    private static void exportLogsCli(String arg) {
        if (LogEntry.activityLog.isEmpty()) {
            System.out.println("No logs to export.");
            return;
        }

        File target;
        if (arg == null || arg.isBlank()) {
            target = new File("logs_export_cli.csv");
        } else {
            target = new File(arg.trim());
        }

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(target)))) {
            out.println("Time,Song,TotalPrice,Message");
            for (LogEntry entry : LogEntry.activityLog) {
                String time = entry.getTimestampText();
                String song = escapeCsv(entry.getSongTitle());
                String total = entry.getTotalPrice() == 0.0
                        ? ""
                        : String.format("%.2f", entry.getTotalPrice());
                String msg = escapeCsv(entry.getMessage());
                out.printf("%s,%s,%s,%s%n", time, song, total, msg);
            }
            System.out.println("Logs exported to: " + target.getAbsolutePath());
        } catch (IOException ex) {
            System.out.println("Failed to export logs: " + ex.getMessage());
        }
    }

    // Minimal CSV escaping helper for CLI export
    private static String escapeCsv(String s) {
        if (s == null) return "\"\"";
        String r = s.replace("\"", "\"\"");
        return "\"" + r + "\"";
    }

    // Print the activity log in a simple text table
    private static void viewLogs() {
        if (LogEntry.activityLog.isEmpty()) {
            System.out.println("No log entries.");
            return;
        }

        System.out.printf("%-19s | %-40s | %-11s | %s%n",
                "Time", "Song", "Total Price", "Message");
        System.out.println("------------------------------------------------------------------------------------------------");

        for (LogEntry entry : LogEntry.activityLog) {
            String time = entry.getTimestampText();
            String song = entry.getSongTitle();
            String total = entry.getTotalPrice() == 0.0
                    ? ""
                    : String.format("%.2f", entry.getTotalPrice());
            String msg = entry.getMessage();

            // Truncate very long song names so columns stay readable
            String songShort = song.length() > 40 ? song.substring(0, 37) + "..." : song;

            System.out.printf("%-19s | %-40s | %-11s | %s%n",
                    time, songShort, total, msg);
        }
    }
}