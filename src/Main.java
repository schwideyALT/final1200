import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;

public class Main {

    public static void main(String[] args) {
        // Initialize SF Pro Display fonts
        FontManager.init();

        // Set default UI font to SF Pro Display Regular 14
        Font base = FontManager.sfProPlain(14f);
        setDefaultUIFont(new FontUIResource(base));

        boolean modeChosen = false;
        boolean cli = false;

        for (String a : args) {
            if ("--cli".equalsIgnoreCase(a) || "-cli".equalsIgnoreCase(a)) { cli = true; modeChosen = true; }
            if ("--gui".equalsIgnoreCase(a) || "-gui".equalsIgnoreCase(a)) { cli = false; modeChosen = true; }
        }

        if (!modeChosen) {
            //prompt user to select interface
            try {
                System.out.println("Choose mode:");
                System.out.println("  1) GUI");
                System.out.println("  2) CLI");
                System.out.print("> ");
                java.util.Scanner sc = new java.util.Scanner(System.in);
                String input = sc.nextLine();
                cli = "2".equals(input) || "cli".equalsIgnoreCase(input);
                modeChosen = true;
            } catch (Exception ignored) {}

            // If no console (or input failed), show a small dialog
            if (!modeChosen) {
                try {
                    String[] options = {"GUI", "CLI"};
                    int choice = javax.swing.JOptionPane.showOptionDialog(
                            null,
                            "Select a mode to start:",
                            "Start Mode",
                            javax.swing.JOptionPane.DEFAULT_OPTION,
                            javax.swing.JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[0]
                    );
                    cli = (choice == 1);
                    modeChosen = true;
                } catch (Exception ignored) {}
            }
        }

        if (cli) {
            VinylCli.run();
        } else {
            VinylGui.launch();
        }
    }

    private static void setDefaultUIFont(FontUIResource f) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, f);
            }
        }
    }
}
