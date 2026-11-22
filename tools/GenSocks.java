import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Reproducible 16px novelty item matching the mod's existing pixel-art inventory assets. */
public class GenSocks {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        sock(image, 2, 2, 0xd8d2ad, 0x8b865e);
        sock(image, 9, 5, 0xbdb88e, 0x77734f);
        pixel(image, 4, 7, 0x9d9255); pixel(image, 5, 9, 0xa89a63);
        pixel(image, 10, 10, 0x8f8550); pixel(image, 12, 12, 0x9b8c51);
        pixel(image, 6, 0, 0x9bb765); pixel(image, 7, 1, 0xb0c87c);
        pixel(image, 13, 2, 0x9bb765); pixel(image, 12, 3, 0xb0c87c);
        ImageIO.write(image, "PNG", Path.of("src/main/resources/assets/napuli/textures/item/calzini_sporchi.png").toFile());
    }

    static void sock(BufferedImage image, int x, int y, int fabric, int shade) {
        String[] rows = {"oooo", "ohho", "oppo", "opso", "oppo", "opppo", "oppppo", "oopppo", ".oooo."};
        for (int dy = 0; dy < rows.length; dy++) for (int dx = 0; dx < rows[dy].length(); dx++) {
            char mark = rows[dy].charAt(dx);
            if (mark == '.' || mark == ' ') continue;
            pixel(image, x + dx, y + dy, mark == 'o' ? 0x4c4b38 : mark == 'h' ? fabric + 0x101008 : mark == 'p' ? fabric : shade);
        }
    }

    static void pixel(BufferedImage image, int x, int y, int color) { image.setRGB(x, y, 0xff000000 | color); }
}
