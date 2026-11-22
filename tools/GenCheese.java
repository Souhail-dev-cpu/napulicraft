import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Pixel-art cheese wedge with a green aroma, matching the mod's inventory icons. */
public class GenCheese {
    public static void main(String[] args) throws Exception {
        String[] rows = {
                "................", ".........oo.....", ".......oohho....", ".....oohhhhho...",
                "...oohhhhhhhho..", "..ohhhhhhhhhho..", ".ohhhhshhhhhhho.", ".oppppppppppppo.",
                ".oppsppppsppppo.", ".oppppppppppppo.", "..opppppppppppo.", "...oppppsppppoo.",
                "....ooooooooo...", "................", "................", "................"};
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            char mark = rows[y].charAt(x);
            if (mark != '.') image.setRGB(x, y, 0xff000000 | switch (mark) {
                case 'h' -> 0xffe4a3;
                case 'p' -> 0xe5bd55;
                case 's' -> 0x93803d;
                default -> 0x79632d;
            });
        }
        image.setRGB(3, 9, 0xff8eaa57); image.setRGB(4, 10, 0xffa8b363);
        image.setRGB(12, 1, 0xff95b56b); image.setRGB(13, 2, 0xffb2c47b);
        image.setRGB(5, 0, 0xffb2c47b); image.setRGB(4, 1, 0xff95b56b);
        ImageIO.write(image, "PNG", Path.of("src/main/resources/assets/napuli/textures/item/formaggio_piede.png").toFile());
    }
}
