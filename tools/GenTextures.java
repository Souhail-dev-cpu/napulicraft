import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Builds the Munaciello skins and the cornicello from vanilla textures (recolour + a few hand-placed pixels). */
public class GenTextures {
	static ZipFile jar;
	static Path out = Paths.get("src/main/resources/assets/napuli/textures");

	public static void main(String[] a) throws Exception {
		jar = new ZipFile(a[0]);
		BufferedImage zombie = tex("entity/zombie/zombie");

		// --- 'O Munaciello: brown monk robe, hood, face in shadow, small warm eyes, rope belt
		BufferedImage m = recolor(zombie, tex("block/brown_wool"), 1.0);
		hood(m, rgb(0x5a3a22));
		face(m, rgb(0x1a110b));
		px(m, 10, 12, rgb(0xf2d16b)); px(m, 13, 12, rgb(0xf2d16b));         // eyes
		px(m, 10, 13, rgb(0x7a5a24)); px(m, 13, 13, rgb(0x7a5a24));         // eye glow under
		belt(m, rgb(0xc8a46a), rgb(0x9c7c45));
		hands(m, rgb(0xd9a27a), rgb(0xb5815c));
		save(m, "entity/munaciello.png");

		// --- 'O Munaciello Gigante: black robe, black face, red eyes, white grin, grey dead hands
		BufferedImage g = recolor(zombie, tex("block/black_wool"), 1.35);
		hood(g, rgb(0x15151a));
		face(g, rgb(0x050506));
		for (int x : new int[] {9, 10, 13, 14}) px(g, x, 11, rgb(0xff2a2a));
		for (int x = 10; x <= 13; x++) px(g, x, 14, rgb(0xe8e4dc));
		px(g, 9, 13, rgb(0xe8e4dc)); px(g, 14, 13, rgb(0xe8e4dc));
		belt(g, rgb(0x3a3a40), rgb(0x2a2a30));
		hands(g, rgb(0x9a9a92), rgb(0x76766e));
		save(g, "entity/munaciello_gigante.png");
		// glowing layer: only eyes and grin light up in the dark
		BufferedImage e = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int x : new int[] {9, 10, 13, 14}) px(e, x, 11, rgb(0xff2a2a));
		for (int x = 10; x <= 13; x++) px(e, x, 14, rgb(0xd8d4cc));
		px(e, 9, 13, rgb(0xd8d4cc)); px(e, 14, 13, rgb(0xd8d4cc));
		save(e, "entity/munaciello_gigante_eyes.png");

		// --- Cornicello: the vanilla goat horn, in lucky red
		save(recolor(tex("item/goat_horn"), tex("block/red_concrete"), 1.15), "item/cornicello.png");
		System.out.println("textures done");
	}

	/** Keep the source's light/dark pattern, take colours from the palette texture (with darker/lighter steps). */
	static BufferedImage recolor(BufferedImage src, BufferedImage pal, double bright) {
		int w = src.getWidth(), h = src.getHeight();
		double sum = 0; int n = 0;
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { int c = src.getRGB(x, y); if ((c >>> 24) > 0) { sum += luma(c); n++; } }
		double mean = sum / Math.max(1, n);
		BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
			int c = src.getRGB(x, y);
			if ((c >>> 24) == 0) continue;
			int p = pal.getRGB(x % 16, y % 16);
			double s = Math.max(0.45, Math.min(1.5, Math.pow(luma(c) / mean, 1.2))) * bright;
			o.setRGB(x, y, (c & 0xFF000000) | scale(p, s));
		}
		return o;
	}

	/** Hood: whole head and the hat layer in robe colour, leaving a face opening on the hat front. */
	static void hood(BufferedImage img, int robe) {
		for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++) {
			boolean base = x < 32, hat = x >= 32;
			int fx = x - (hat ? 40 : 8), fy = y - 8;
			boolean front = fx >= 0 && fx < 8 && fy >= 0 && fy < 8;
			if (base && front) continue; // face handled separately
			if (hat && front && fx >= 1 && fx <= 6 && fy >= 2) { img.setRGB(x, y, 0); continue; } // opening
			if (y < 8 && (x < 8 || (x >= 24 && x < 40) || x >= 56)) continue; // unused UV corners
			int shade = ((x * 7 + y * 13) % 5 == 0) ? scale(robe, 0.85) : robe;
			img.setRGB(x, y, 0xFF000000 | (shade & 0xFFFFFF));
		}
	}

	static void face(BufferedImage img, int dark) {
		for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) img.setRGB(x, y, 0xFF000000 | (dark & 0xFFFFFF));
	}

	static void belt(BufferedImage img, int rope, int knot) {
		for (int x = 16; x < 40; x++) px(img, x, 26, rope);
		px(img, 22, 27, knot); px(img, 22, 28, knot); px(img, 23, 29, knot);
	}

	static void hands(BufferedImage img, int skin, int shade) {
		for (int y = 28; y < 32; y++) {
			for (int x = 40; x < 56; x++) px(img, x, y, (x % 4 == 0) ? shade : skin);  // right arm sides
			for (int x = 32; x < 48; x++) px(img, x, y + 32, (x % 4 == 0) ? shade : skin); // left arm (64x64 layout)
		}
	}

	static void px(BufferedImage img, int x, int y, int c) { img.setRGB(x, y, 0xFF000000 | (c & 0xFFFFFF)); }
	static int rgb(int c) { return c; }
	static double luma(int c) { return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0; }
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
