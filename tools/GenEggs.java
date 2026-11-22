import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Spawn egg icons for the creative tab: egg shape, base colour, spots, dark outline, a highlight. */
public class GenEggs {
	public static void main(String[] a) throws Exception {
		String out = a.length > 0 ? a[0] : "../src/main/resources/assets/napuli/textures/item";
		egg(out, "munaciello_spawn_egg", 0x6B4A2E, 0xC62828);   // brown habit, red cap
		egg(out, "munaciello_gigante_spawn_egg", 0x1A1A1A, 0xD50000);
		egg(out, "napuletano_spawn_egg", 0x29A9E1, 0xFFFFFF);    // Napoli azure and white
	}

	static void egg(String dir, String name, int base, int spot) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		boolean[][] in = new boolean[16][16];
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
			double dx = (x + 0.5 - 8) / 5.2;
			double dy = (y + 0.5 - 9) / 6.4;
			if (dy < 0) dx *= 1 + (-dy) * 0.35;          // narrower at the top, like an egg
			in[x][y] = dx * dx + dy * dy <= 1;
		}
		int[][] spots = {{6, 5}, {10, 7}, {5, 10}, {9, 12}, {11, 3}};
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
			if (!in[x][y]) continue;
			int c = base;
			for (int[] s : spots) if (Math.abs(x - s[0]) + Math.abs(y - s[1]) <= 1) c = spot;
			if (x >= 6 && x <= 7 && y >= 4 && y <= 6 && c == base) c = blend(base, 0xFFFFFF, 0.35); // highlight
			if (x >= 10 && y >= 10 && c == base) c = blend(base, 0x000000, 0.25);                   // shade
			img.setRGB(x, y, 0xFF000000 | c);
		}
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
			if (in[x][y]) continue;
			boolean edge = false;
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = x + d[0], ny = y + d[1];
				if (nx >= 0 && nx < 16 && ny >= 0 && ny < 16 && in[nx][ny]) edge = true;
			}
			if (edge) img.setRGB(x, y, 0xFF000000 | blend(base, 0x000000, 0.65));
		}
		ImageIO.write(img, "png", new File(dir, name + ".png"));
	}

	static int blend(int a, int b, double t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}
}
