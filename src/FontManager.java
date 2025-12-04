import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

public final class FontManager {

    public static final String SF_PRO_DISPLAY_FAMILY = "SF Pro Display";

    // Family name to use for the star font (Wingdine)
    private static final String STAR_FONT_FAMILY = null;

    private FontManager() {
        // utility class
    }

    public static void init() {
        // Load and register all SF Pro Display fonts we have
        registerFont("/fonts/SFPRODISPLAYREGULAR.OTF");
        registerFont("/fonts/SFPRODISPLAYMEDIUM.OTF");
        registerFont("/fonts/SFPRODISPLAYBOLD.OTF");
        registerFont("/fonts/SFPRODISPLAYTHINITALIC.OTF");
        registerFont("/fonts/SFPRODISPLAYBLACKITALIC.OTF");
        registerFont("/fonts/SFPRODISPLAYHEAVYITALIC.OTF");
        registerFont("/fonts/SFPRODISPLAYLIGHTITALIC.OTF");
        registerFont("/fonts/SFPRODISPLAYSEMIBOLDITALIC.OTF");
        registerFont("/fonts/SFPRODISPLAYULTRALIGHTITALIC.OTF");

        // Load Wingdine font for stars
        initStarFont();
    }

    private static void registerFont(String resourcePath) {
        try (InputStream is = FontManager.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("Font not found on classpath: " + resourcePath);
                return;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(font);
        } catch (Exception e) {
            //System.err.println("Failed to load font: " + resourcePath);
            //e.printStackTrace();
        }
    }

    // Load wingdine.ttf and remember its family name for later use.
    private static void initStarFont() {
    }

    public static Font sfPro(int style, float size) {
        return new Font(SF_PRO_DISPLAY_FAMILY, style, (int) size);
    }

    public static Font sfProPlain(float size) {
        return sfPro(Font.PLAIN, size);
    }

    public static Font starFont(float size) {
        if (STAR_FONT_FAMILY != null) {
            return new Font(STAR_FONT_FAMILY, Font.PLAIN, (int) size);
        }
        // Fallback so stars still render instead of rectangles
        return new Font("Dialog", Font.PLAIN, (int) size);
    }
}
