import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Weapons: a 3D held model (box elements, like vanilla's 3D items) + a shaded 2D icon for the inventory.
 * The item definition shows the icon in GUI / on the ground / in frames, and the 3D model in hand.
 * Run from D:\NapuliMod: java tools/GenWeapons.java
 */
public class GenWeapons {
	static Path res = Paths.get("src/main/resources/assets/napuli");

	public static void main(String[] a) throws Exception {
		// ---------- 3D texture atlases: 4x4 swatches of material colours (uv in 4-px squares)
		atlas("pistola_3d", 0x3a4048, 0x23272d, 0x1a1a1a, 0x8f9aa3);   // slide, dark metal, grip, light metal
		atlas("manganello_3d", 0x1c1d21, 0x2c2e34, 0x111214, 0x6a6e78); // rod, rod light, rubber grip, metal cap
		atlas("coltello_3d", 0xc9d1d8, 0x8f9aa3, 0x6b4428, 0x3a2414);   // blade, blade edge, handle, handle dark
		atlas("tirapugni_3d", 0xb8a26a, 0x8a7440, 0x4a4030, 0xe8d79a);  // brass, brass shade, grip, shine

		// ---------- 3D models (16x16x16 item space, laid out in the X/Y plane like the icon, depth in Z)
		model("pistola_3d", "pistola", List.of(
			box(3, 10, 7, 14, 12.5, 9, 0),          // slide
			box(4, 9, 7.2, 12.5, 10, 8.8, 1),       // frame
			box(14, 10.4, 7.4, 15.2, 12, 8.6, 1),   // muzzle
			rot(box(3.2, 4, 7.1, 6.2, 9.5, 8.9, 2), 4.5, 9, 8, -22.5), // grip, angled back
			box(6.2, 7, 7.6, 9.6, 7.6, 8.4, 1),     // trigger guard bottom
			box(9.0, 7.6, 7.6, 9.6, 9, 8.4, 1),     // trigger guard front
			box(7.2, 7.6, 7.8, 7.8, 9, 8.2, 3),     // trigger
			box(13, 12.5, 7.7, 13.7, 13.1, 8.3, 3), // front sight
			box(3.4, 12.5, 7.5, 4.6, 13.1, 8.5, 3), // rear sight
			box(5, 11.6, 6.9, 9, 12.2, 7.0, 3)),    // slide serrations highlight
			"pistol");
		model("manganello_3d", "manganello", List.of(
			rot(box(7.2, 4, 7.2, 8.8, 15.5, 8.8, 0), 8, 8, 8, -45),   // rod
			rot(box(7.0, 0.8, 7.0, 9.0, 4.6, 9.0, 2), 8, 8, 8, -45),  // rubber grip
			rot(box(6.9, 4.6, 6.9, 9.1, 5.2, 9.1, 3), 8, 8, 8, -45),  // collar
			rot(box(7.4, 15.5, 7.4, 8.6, 16, 8.6, 3), 8, 8, 8, -45),  // end cap
			rot(box(9.0, 0.2, 7.9, 10.0, 1.6, 8.1, 3), 8, 8, 8, -45)), // wrist strap ring
			"sword");
		model("coltello_3d", "coltello", List.of(
			rot(box(7.3, 7.6, 7.8, 8.9, 14.2, 8.2, 0), 8, 8, 8, -45), // blade
			rot(box(8.9, 7.6, 7.85, 9.3, 13.6, 8.15, 1), 8, 8, 8, -45), // sharpened edge
			rot(box(7.6, 14.2, 7.85, 8.7, 15.4, 8.15, 0), 8, 8, 8, -45), // tip
			rot(box(6.6, 6.9, 7.4, 9.8, 7.6, 8.6, 3), 8, 8, 8, -45),    // guard
			rot(box(7.4, 1.6, 7.4, 8.8, 6.9, 8.6, 2), 8, 8, 8, -45)),   // handle
			"sword");
		model("tirapugni_3d", "tirapugni", List.of(
			box(2.5, 9, 7.4, 13.5, 10.2, 8.6, 0),   // top bar over the fingers
			box(2.5, 5.6, 7.4, 13.5, 6.6, 8.6, 1),  // bottom bar
			box(2.5, 6.6, 7.4, 3.3, 9, 8.6, 0), box(5.1, 6.6, 7.4, 5.8, 9, 8.6, 0),
			box(7.7, 6.6, 7.4, 8.4, 9, 8.6, 0), box(10.2, 6.6, 7.4, 10.9, 9, 8.6, 0),
			box(12.7, 6.6, 7.4, 13.5, 9, 8.6, 0),   // the finger holes
			box(4, 3, 7.6, 12, 5.6, 8.4, 2),        // palm grip
			box(3, 10.2, 7.6, 13, 10.6, 8.4, 3)),   // shine along the top
			"fist");

		// ---------- 2D icons, shaded by hand (o outline, d dark, m mid, h highlight, g grip, w wood, s steel, b brass)
		icon("pistola", new String[] {
			"................",
			"................",
			"................",
			"...oooooooooooo.",
			"..ohhhhhhhhhhhmo",
			"..ommmmmmmmmmmmo",
			"..odddmmmmmmdddo",
			"..oooodddddoooo.",
			"...oggo.od.o....",
			"...oggo..oo.....",
			"..oggo..........",
			"..oggo..........",
			".oggo...........",
			".oggo...........",
			".oooo...........",
			"................"},
			Map.of('o', 0x16191d, 'h', 0x9aa6b0, 'm', 0x4a525c, 'd', 0x2a2f36, 'g', 0x232323));
		icon("manganello", diagonal(new char[] {'g', 'g', 'g', 'g', 'c', 'm', 'm', 'm', 'm', 'm', 'm', 'm', 'm', 'e'}),
			Map.of('o', 0x0b0b0d, 'g', 0x1a1a1a, 'G', 0x3a3a3a, 'c', 0x7a7e88, 'C', 0xa9adb5, 'm', 0x26282d, 'M', 0x4a4d55, 'e', 0x7a7e88, 'E', 0xa9adb5));
		icon("coltello", diagonal(new char[] {'w', 'w', 'w', 'w', 'c', 's', 's', 's', 's', 's', 's', 's', 's', 'e'}),
			Map.of('o', 0x2a1a10, 'w', 0x6b4428, 'W', 0x8f5f38, 'c', 0x8f9aa3, 'C', 0xc9d1d8, 's', 0xaeb7bf, 'S', 0xeef2f5, 'e', 0xaeb7bf, 'E', 0xeef2f5));
		icon("tirapugni", new String[] {
			"................",
			"................",
			"................",
			"................",
			"..oooooooooooo..",
			".ohhhhhhhhhhhho.",
			".obb.bb.bb.bbbo.",
			".ob.ob.ob.ob.bo.",
			".obb.bb.bb.bbbo.",
			".ommmmmmmmmmmmo.",
			"..oooggggggooo..",
			"....oggggggo....",
			".....oooooo.....",
			"................",
			"................",
			"................"},
			Map.of('o', 0x3a2e14, 'h', 0xf0dfa0, 'b', 0xc2ab6c, 'm', 0x8a7440, 'g', 0x4a4030));
		System.out.println("weapons done");
	}

	/** a diagonal weapon (bottom-left handle to top-right tip), 2 px thick with a highlight row and an outline */
	static String[] diagonal(char[] along) {
		char[][] g = new char[16][16];
		for (char[] r : g) Arrays.fill(r, '.');
		for (int i = 0; i < along.length; i++) {
			int x = 1 + i, y = 14 - i;
			char c = along[i];
			put(g, x, y, c); put(g, x + 1, y, Character.toUpperCase(c)); // body + highlight
			put(g, x, y + 1, 'o'); put(g, x + 2, y, 'o'); put(g, x - 1, y, 'o'); put(g, x + 1, y - 1, 'o');
		}
		// re-draw the body over the outline pass
		for (int i = 0; i < along.length; i++) { int x = 1 + i, y = 14 - i; put(g, x, y, along[i]); put(g, x + 1, y, Character.toUpperCase(along[i])); }
		String[] out = new String[16];
		for (int y = 0; y < 16; y++) out[y] = new String(g[y]);
		return out;
	}
	static void put(char[][] g, int x, int y, char c) { if (x >= 0 && x < 16 && y >= 0 && y < 16) g[y][x] = c; }

	record Box(double[] from, double[] to, int swatch, double[] origin, double angle) {}
	static Box box(double x1, double y1, double z1, double x2, double y2, double z2, int swatch) {
		return new Box(new double[] {x1, y1, z1}, new double[] {x2, y2, z2}, swatch, null, 0);
	}
	static Box rot(Box b, double ox, double oy, double oz, double angle) { return new Box(b.from, b.to, b.swatch, new double[] {ox, oy, oz}, angle); }

	static void model(String name, String icon, List<Box> boxes, String hold) throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n  \"textures\": { \"t\": \"napuli:item/").append(name).append("\", \"particle\": \"napuli:item/").append(icon).append("\" },\n");
		sb.append("  \"elements\": [\n");
		for (int i = 0; i < boxes.size(); i++) {
			Box b = boxes.get(i);
			int u = (b.swatch % 4) * 4;
			String uv = "[" + u + ", 0, " + (u + 4) + ", 4]";
			sb.append("    { \"from\": ").append(arr(b.from)).append(", \"to\": ").append(arr(b.to));
			if (b.origin != null) sb.append(", \"rotation\": { \"origin\": ").append(arr(b.origin)).append(", \"axis\": \"z\", \"angle\": ").append(b.angle).append(" }");
			sb.append(",\n      \"faces\": { ");
			String[] faces = {"north", "south", "east", "west", "up", "down"};
			for (int f = 0; f < faces.length; f++)
				sb.append("\"").append(faces[f]).append("\": { \"uv\": ").append(uv).append(", \"texture\": \"#t\" }").append(f < faces.length - 1 ? ", " : "");
			sb.append(" } }").append(i < boxes.size() - 1 ? ",\n" : "\n");
		}
		sb.append("  ],\n  \"display\": ").append(display(hold)).append("\n}\n");
		write("models/item/" + name + ".json", sb.toString());
		// item definition: 2D icon in the inventory, on the ground and in item frames; 3D in hand
		write("items/" + icon + ".json", "{\n  \"model\": {\n    \"type\": \"minecraft:select\",\n    \"property\": \"minecraft:display_context\",\n"
				+ "    \"cases\": [ { \"when\": [\"gui\", \"ground\", \"fixed\"], \"model\": { \"type\": \"minecraft:model\", \"model\": \"napuli:item/" + icon + "\" } } ],\n"
				+ "    \"fallback\": { \"type\": \"minecraft:model\", \"model\": \"napuli:item/" + name + "\" }\n  }\n}\n");
		write("models/item/" + icon + ".json", "{ \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"napuli:item/" + icon + "\" } }\n");
	}

	/** how it sits in the hand: pistol held level and pointing forward, the rest like a sword / a fist */
	static String display(String hold) {
		return switch (hold) {
			// barrel along +x: rotate like a sword blade but 45 degrees more, so it points forward
			case "pistol" -> "{\n    \"thirdperson_righthand\": { \"rotation\": [0, -90, 100], \"translation\": [0, 2.5, 1], \"scale\": [0.85, 0.85, 0.85] },\n"
					+ "    \"thirdperson_lefthand\": { \"rotation\": [0, 90, -100], \"translation\": [0, 2.5, 1], \"scale\": [0.85, 0.85, 0.85] },\n"
					+ "    \"firstperson_righthand\": { \"rotation\": [0, 90, -5], \"translation\": [1.5, 2.5, -1], \"scale\": [0.55, 0.55, 0.55] },\n"
					+ "    \"firstperson_lefthand\": { \"rotation\": [0, -90, 5], \"translation\": [1.5, 2.5, -1], \"scale\": [0.55, 0.55, 0.55] }\n  }";
			case "fist" -> "{\n    \"thirdperson_righthand\": { \"rotation\": [0, 90, -10], \"translation\": [0, 1.5, 1.5], \"scale\": [0.7, 0.7, 0.7] },\n"
					+ "    \"thirdperson_lefthand\": { \"rotation\": [0, -90, 10], \"translation\": [0, 1.5, 1.5], \"scale\": [0.7, 0.7, 0.7] },\n"
					+ "    \"firstperson_righthand\": { \"rotation\": [0, 90, -10], \"translation\": [1, 3, 1], \"scale\": [0.7, 0.7, 0.7] },\n"
					+ "    \"firstperson_lefthand\": { \"rotation\": [0, -90, 10], \"translation\": [1, 3, 1], \"scale\": [0.7, 0.7, 0.7] }\n  }";
			default -> "{\n    \"thirdperson_righthand\": { \"rotation\": [0, -90, 55], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85] },\n"
					+ "    \"thirdperson_lefthand\": { \"rotation\": [0, 90, -55], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85] },\n"
					+ "    \"firstperson_righthand\": { \"rotation\": [0, -90, 25], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.68, 0.68, 0.68] },\n"
					+ "    \"firstperson_lefthand\": { \"rotation\": [0, 90, -25], \"translation\": [1.13, 3.2, 1.13], \"scale\": [0.68, 0.68, 0.68] }\n  }";
		};
	}

	static String arr(double[] v) { return "[" + fmt(v[0]) + ", " + fmt(v[1]) + ", " + fmt(v[2]) + "]"; }
	static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((int) d) : String.valueOf(d); }

	static void atlas(String name, int... colors) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int s = 0; s < 4; s++)
			for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
				int c = colors[s];
				double f = y == 0 ? 1.15 : y == 3 ? 0.85 : ((x + y) % 3 == 0 ? 0.95 : 1.0); // a little light from above
				img.setRGB(s * 4 + x, y, 0xff000000 | scale(c, f));
			}
		ImageIO.write(img, "png", res.resolve("textures/item/" + name + ".png").toFile());
	}

	static void icon(String name, String[] rows, Map<Character, Integer> pal) throws Exception {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			String r = (rows[y] + "................").substring(0, 16);
			for (int x = 0; x < 16; x++) {
				char c = r.charAt(x);
				if (c == '.') continue;
				Integer col = pal.get(c);
				if (col == null) throw new IllegalStateException(name + ": unknown '" + c + "'");
				img.setRGB(x, y, 0xff000000 | col);
			}
		}
		ImageIO.write(img, "png", res.resolve("textures/item/" + name + ".png").toFile());
	}

	static int scale(int c, double f) {
		int r = Math.min(255, (int) (((c >> 16) & 255) * f)), g = Math.min(255, (int) (((c >> 8) & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
		return (r << 16) | (g << 8) | b;
	}
	static void write(String rel, String s) throws Exception { Path p = res.resolve(rel); Files.createDirectories(p.getParent()); Files.writeString(p, s, StandardCharsets.UTF_8); }
}
