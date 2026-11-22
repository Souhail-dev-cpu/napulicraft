import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/**
 * Motorino entity texture (painted from vanilla block textures onto the model's UV boxes)
 * and hand-placed 16x16 item icons (pizza, cafe, ciabatta, motorino) with vanilla-like palettes.
 */
public class GenNewTextures {
	static ZipFile jar;
	static Path out = Paths.get("src/main/resources/assets/napuli/textures");

	public static void main(String[] a) throws Exception {
		jar = new ZipFile(a[0]);
		BufferedImage t = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		BufferedImage blue = tex("block/light_blue_concrete"), iron = tex("block/iron_block"), black = tex("block/black_concrete_powder"),
				grey = tex("block/gray_concrete"), seat = tex("block/black_wool"), white = tex("block/white_concrete");
		// box(u, v, w, h, d, texture, brightness)
		box(t, 0, 0, 3, 8, 8, black, 0.7);          // wheels
		wheelSide(t, 0, 8); wheelSide(t, 11, 8);
		box(t, 0, 18, 10, 2, 16, grey, 0.8);        // floorboard
		stripes(t, 16, 18, 10, 16);
		box(t, 54, 18, 12, 9, 18, blue, 0.89);      // long rear body
		box(t, 0, 40, 8, 2, 11, seat, 0.9);
		for (int x = 11; x < 19; x++) t.setRGB(x, 43, 0xff656465);
		box(t, 76, 0, 12, 14, 3, blue, 1.0);       // shield
		box(t, 24, 0, 4, 6, 3, iron, 0.85);
		box(t, 40, 0, 2, 5, 3, iron, 0.95);
		box(t, 40, 12, 16, 2, 2, iron, 0.83);
		box(t, 108, 0, 5, 4, 2, white, 1.0);
		lamp(t, 111, 2);
		box(t, 108, 8, 5, 2, 1, tex("block/red_concrete"), 1.0);
		box(t, 72, 54, 8, 3, 2, iron, 0.9);
		box(t, 72, 62, 2, 3, 13, iron, 0.75);
		save(t, "entity/motorino.png");
		if (a.length > 1 && a[1].equals("--motorino")) return;

		icon("pizza", new String[] {
			"................",
			".....cccccc.....",
			"...cCCCCCCCCc...",
			"..cCRRRRRRRRCc..",
			".cCRRMMRRRBRRCc.",
			".cCRMMmRRRbRRCc.",
			"cCRRRmRRMMRRRRCc",
			"cCRBRRRRMmRRRRCc",
			"cCRbRRRRRRRRMRCc",
			"cCRRRMMRRRBRmRCc",
			"cCRRRMmRRRbRRRCc",
			".cCRRRRRRRRRRCc.",
			".cCRRRBRRMMRRCc.",
			"..cCRRbRRmRRCc..",
			"...cCCCCCCCCc...",
			".....cccccc....."},
			Map.of('c', 0x8a5426, 'C', 0xc98a44, 'R', 0xb52a22, 'M', 0xf4efe2, 'm', 0xd9cfb6, 'B', 0x3f8a2c, 'b', 0x2a5e1e));
		icon("cafe", new String[] {
			"................",
			"................",
			"................",
			"....OOOOOOO.....",
			"...OKkkkkkKO....",
			"...OWkkkkkWO....",
			"...OWWWWWWWOOO..",
			"...OWWWWWWWO.O..",
			"...OWWWWWWWO.O..",
			"...OsWWWWWsOOO..",
			"....OsWWWsO.....",
			".OOOOOOOOOOOOO..",
			".OPPPPPPPPPPPO..",
			"..OOOOOOOOOOO...",
			"................",
			"................"},
			Map.of('O', 0x4f4f4f, 'K', 0xb07a45, 'k', 0x4a2c17, 'W', 0xf0f0f0, 's', 0xc9c9c9, 'P', 0xdcdcdc));
		icon("ciabatta", new String[] {
			"................",
			"..........OOO...",
			".........OSSSO..",
			"........OSBBBSO.",
			".......OSBbbBSO.",
			"......OSSBBBSSO.",
			".....OSSSSSSSO..",
			"....OSSSSSSSO...",
			"...OSSSSSSSO....",
			"..OSsSSSSSO.....",
			".OSsSSSSSO......",
			".OSssSSSO.......",
			".OSsSSSO........",
			"..OSSSO.........",
			"...OOO..........",
			"................"},
			Map.of('O', 0x4a3020, 'S', 0xb07a4a, 's', 0x8a5a34, 'B', 0x3d63c4, 'b', 0x2f4fa0));
		icon("motorino", new String[] {
			"................",
			"................",
			"..HH............",
			"...H............",
			"...LS...........",
			"...LS..DDDDD....",
			"...LS.DDDDDDD...",
			"...LSAAAAAAAAA..",
			"...LSAAAAAAAAAA.",
			"...LSAaaaAAAAAA.",
			"..SLLGGGGGAAAAa.",
			".TTT.GGGGG.TTT..",
			"TTxTT.....TTxTT.",
			"TxxxT.....TxxxT.",
			"TTxTT.....TTxTT.",
			".TTT.......TTT.."},
			Map.of('H', 0x9a9a9a, 'L', 0x6fb3e6, 'S', 0x4a8cc4, 'D', 0x2b2b2b, 'A', 0x7cc0ee, 'a', 0x4a8cc4, 'G', 0x6b6b6b, 'T', 0x1e1e1e, 'x', 0xa0a0a0));
		System.out.println("new textures done");
	}

	/** Paint all six faces of a model box's UV area with a block texture. */
	static void box(BufferedImage img, int u, int v, int w, int h, int d, BufferedImage src, double bright) {
		for (int y = v; y < v + d + h; y++)
			for (int x = u; x < u + 2 * d + 2 * w; x++) {
				boolean topRow = y < v + d;
				if (topRow && (x < u + d || x >= u + d + 2 * w)) continue; // unused corners
				int c = src.getRGB(x % 16, y % 16);
				img.setRGB(x, y, 0xFF000000 | scale(c, bright * (topRow ? 1.08 : 1.0)));
			}
	}

	static void wheelSide(BufferedImage img, int u, int v) {
		for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
			double radius = Math.hypot(x - 3.5, y - 3.5);
			int color = radius < 1.5 ? 0xbfc3c7 : radius < 2.8 ? 0x60676e : radius < 3.8 ? 0x29292c : 0x18181b;
			img.setRGB(u + x, v + y, 0xff000000 | color);
		}
	}

	static void lamp(BufferedImage img, int x, int y) { // front face of the headlight
		int[] c = {0xfff6c8, 0xffffff, 0xfff6c8, 0xfff0b0, 0xfffbe0, 0xfff0b0, 0xd8c890, 0xe8d8a0, 0xd8c890};
		for (int i = 0; i < 9; i++) img.setRGB(x + i % 3, y + i / 3, 0xFF000000 | c[i]);
	}

	static void stripes(BufferedImage img, int x0, int y0, int w, int d) { // rubber strips on the floorboard top
		for (int y = y0; y < y0 + d; y += 2) for (int x = x0; x < x0 + w; x++) img.setRGB(x, y, 0xFF2e2e2e);
	}

	static void icon(String name, String[] rows, Map<Character, Integer> pal) throws IOException {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		if (rows.length != 16) throw new IllegalStateException(name + " rows=" + rows.length);
		for (int y = 0; y < 16; y++) {
			if (rows[y].length() != 16) throw new IllegalStateException(name + " row " + y + " len=" + rows[y].length());
			for (int x = 0; x < 16; x++) {
				char ch = rows[y].charAt(x);
				if (ch == '.') continue;
				Integer c = pal.get(ch);
				if (c == null) throw new IllegalStateException(name + " unknown " + ch);
				img.setRGB(x, y, 0xFF000000 | c);
			}
		}
		save(img, "item/" + name + ".png");
	}

	static int scale(int c, double f) {
		int r = (int) Math.min(255, ((c >> 16) & 255) * f), g = (int) Math.min(255, ((c >> 8) & 255) * f), b = (int) Math.min(255, (c & 255) * f);
		return (r << 16) | (g << 8) | b;
	}
	static BufferedImage tex(String p) throws Exception {
		try (InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/" + p + ".png"))) { return ImageIO.read(in); }
	}
	static void save(BufferedImage img, String rel) throws IOException {
		Path p = out.resolve(rel); Files.createDirectories(p.getParent()); ImageIO.write(img, "png", p.toFile());
	}
}
