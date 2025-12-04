import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public final class LogEntry {

    static final java.util.List<LogEntry> activityLog = new ArrayList<>();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Single, persistent log file for the whole application
    private static final File LOG_FILE = new File("logs.csv");

    private final LocalDateTime timestamp;
    private final String message;

    // New fields
    private final String songTitle;
    private double totalPrice;

    static {
        // Load existing log file into memory when the class is first used
        loadFromDisk();
    }

    LogEntry(LocalDateTime timestamp, String message) {
        this(timestamp, message, "", 0.0);
    }

    LogEntry(LocalDateTime timestamp, String message, String songTitle, double totalPrice) {
        this.timestamp = timestamp;
        this.message = message;
        this.songTitle = songTitle == null ? "" : songTitle;
        this.totalPrice = totalPrice;
    }

    LogEntry(LocalDateTime timestamp, String message, String songTitle) {
        this.timestamp = timestamp;
        this.message = message;
        this.songTitle = songTitle == null ? "" : songTitle;
    }

    public static void logEvent(String message) {
        logEvent(message, "", 0.0);
    }

    // Overload that records song title & total price
    public static void logEvent(String message, String songTitle, double totalPrice) {
        LogEntry entry = new LogEntry(LocalDateTime.now(), message, songTitle, totalPrice);
        activityLog.add(entry);
        appendToDisk(entry);
    }

    public static void logEvent(String message, String songTitle) {
        LogEntry entry = new LogEntry(LocalDateTime.now(), message, songTitle);
        activityLog.add(entry);
        appendToDisk(entry);
    }

    String getTimestampText() {
        return FORMATTER.format(timestamp);
    }

    String getMessage() {
        return message;
    }

    String getSongTitle() {
        return songTitle;
    }

    double getTotalPrice() {
        return totalPrice;
    }

    // ---------- Persistence helpers ----------

    private static void loadFromDisk() {
        if (!LOG_FILE.exists()) {
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(LOG_FILE))) {
            String header = br.readLine(); // header line, may be null or legacy
            String line;
            while ((line = br.readLine()) != null) {
                LogEntry entry = parseCsvLine(line);
                if (entry != null) {
                    activityLog.add(entry);
                }
            }
        } catch (IOException e) {
            // Failing to load logs is non-fatal; just print for debugging
            e.printStackTrace();
        }
    }

    private static void appendToDisk(LogEntry entry) {
        boolean newFile = !LOG_FILE.exists();
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)))) {
            if (newFile) {
                // New header with 4 columns
                out.println("Time,Song,TotalPrice,Message");
            }
            out.println(toCsvLine(entry));
        } catch (IOException e) {
            // Logging failures should not crash the app; print for debugging
            e.printStackTrace();
        }
    }

    private static String toCsvLine(LogEntry entry) {
        String time = entry.getTimestampText();
        String song = escapeCsv(entry.getSongTitle());
        String total = entry.getTotalPrice() == 0.0 ? "" : String.format("%.2f", entry.getTotalPrice());
        String msg = escapeCsv(entry.getMessage());
        // Keep comma-separated with quotes where needed
        return String.format("%s,%s,%s,%s", time, song, total, msg);
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        String result = s.replace("\"", "\"\"");
        return "\"" + result + "\"";
    }

    private static LogEntry parseCsvLine(String line) {
        // Very simple CSV parsing that supports:
        // - Old format: Time,Event
        // - New format: Time,Song,TotalPrice,Message
        // We don’t need a full CSV parser, just enough for our own format.

        // Split on commas, but honor quotes (basic implementation)
        java.util.List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                sb.append(c);
            } else if (c == ',' && !inQuotes) {
                parts.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString());

        if (parts.size() < 2) {
            return null;
        }

        try {
            LocalDateTime ts = LocalDateTime.parse(parts.get(0), FORMATTER);

            if (parts.size() == 2) {
                // Legacy: Time,Event
                String msg = unquote(parts.get(1));
                return new LogEntry(ts, msg);
            } else {
                // New: Time,Song,TotalPrice,Message
                String song = unquote(parts.get(1));
                String totalStr = unquote(parts.get(2));
                double total = totalStr.isEmpty() ? 0.0 : Double.parseDouble(totalStr);
                String msg = unquote(parts.get(3));
                return new LogEntry(ts, msg, song, total);
            }
        } catch (Exception ex) {
            // If parsing fails for some line, skip that line
            ex.printStackTrace();
            return null;
        }
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\"\"", "\"");
    }
}
