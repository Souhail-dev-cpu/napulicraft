import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public class GenStreetGear {
    public static void main(String[] args) throws Exception {
        paint("tirapugni", 0x687584, 0xc6d3d6, ".....o.o.o.o", "....ohhhhhhho", "...oh.o.o.ohho", "...oh.o.o.ohho", "...oohhhhhhoo", "....ooooooo", ".....opppo", ".....opppo", "......ooo");
        paint("coltello", 0x7b5943, 0xe3eaf0, ".............o", "............oho", "...........ohho", "..........ohho", ".........ohho", "........ohho", ".......ohho", "......ohho", ".....oooho", "....opoo", "...oppo", "..oppo", "..ooo");
        paint("pistola", 0x4e6375, 0x9bb3bf, "....ohhhhhhhoo", "...oppppppppppo", "...ooooooppppo", "....o..opppo", "....oooopp", ".......opp", ".......oppo", ".......oooo");
        paint("manganello", 0x434856, 0x9aafbc, "............oo", "...........oppo", "..........oppo", ".........oppo", "........oppo", ".......oppo", "......oppo", ".....ohho", "....oppop", "...oppo", "..oppo", "..ooo");
    }

    private static void paint(String name, int main, int highlight, String... rows) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int offset = (16 - rows.length) / 2;
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
            char mark = rows[y].charAt(x);
            if (mark != '.') image.setRGB(x, y + offset, 0xff000000 | (mark == 'p' ? main : mark == 'h' ? highlight : 0x20252c));
        }
        ImageIO.write(image, "PNG", Path.of("src/main/resources/assets/napuli/textures/item/" + name + ".png").toFile());
    }
}
