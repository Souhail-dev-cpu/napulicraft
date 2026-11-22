import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** The casco: a white open-face "jet" scooter helmet with a Napoli-azure stripe and a smoked visor brim. */
public class GenCasco {
	static final int SHELL = 0xFFF2F2EE, SHADE = 0xFFD6D6D0, AZURE = 0xFF1F8FE0, AZURE_D = 0xFF1670B8,
			VISOR = 0xFF3A4450, PAD = 0xFF2A2A2A, STRAP = 0xFF1C1C1C;

	public static void main(String[] a) throws Exception {
		String root = a.length > 0 ? a[0] : "../src/main/resources/assets/napuli/textures";
		BufferedImage arm = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		// head cube 8x8x8 at (0,0): top (8,0) bottom (16,0); right (0,8) front (8,8) left (16,8) back (24,8)
		for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
			boolean stripe = x == 3 || x == 4;
			arm.setRGB(8 + x, y, stripe ? AZURE : (x == 0 || x == 7 ? SHADE : SHELL));     // top: stripe front to back
		}
		for (int x = 0; x < 8; x++) {
			// front: shell band, the visor brim, then the open face (transparent)
			arm.setRGB(8 + x, 8, (x == 3 || x == 4) ? AZURE : SHELL);
			arm.setRGB(8 + x, 9, SHELL);
			arm.setRGB(8 + x, 10, VISOR);
			arm.setRGB(8 + x, 11, x == 0 || x == 7 ? PAD : 0);
		}
		for (int y = 12; y < 16; y++) { arm.setRGB(8, y, y < 14 ? PAD : 0); arm.setRGB(15, y, y < 14 ? PAD : 0); }
		// sides: full shell down to the ear, the strap under the chin, cut-out at the front-lower corner
		for (int side : new int[] {0, 16}) for (int x = 0; x < 8; x++) for (int y = 8; y < 16; y++) {
			boolean frontEdge = side == 0 ? x == 7 : x == 0; // right face: front is on the right edge; left face: on the left edge
			boolean cut = frontEdge && y >= 12 || (side == 0 ? x >= 6 : x <= 1) && y >= 14;
			int c = y == 8 ? SHELL : y == 15 ? SHADE : SHELL;
			if (y >= 9 && y <= 10 && (side == 0 ? x == 2 : x == 5)) c = AZURE_D; // little side badge
			if (y == 15 && (side == 0 ? x == 5 : x == 2)) c = STRAP;
			arm.setRGB(side + x, y, cut ? 0 : c);
		}
		for (int x = 0; x < 8; x++) for (int y = 8; y < 16; y++) {
			boolean stripe = x == 3 || x == 4;
			arm.setRGB(24 + x, y, y == 15 ? SHADE : stripe ? AZURE : SHELL);              // back
		}
		for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) arm.setRGB(16 + x, y, (x == 0 || x == 7 || y == 0 || y == 7) ? PAD : 0); // bottom: open ring
		File eq = new File(root, "entity/equipment/humanoid/casco.png");
		eq.getParentFile().mkdirs();
		ImageIO.write(arm, "png", eq);

		// item icon 16x16: three-quarter view of the helmet
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		int cx = 8, cy = 9;
		for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
			double dx = (x + 0.5 - cx) / 6.5, dy = (y + 0.5 - cy) / 6.2;
			if (dx * dx + dy * dy > 1 || y > 12) continue;
			int c = SHELL;
			if (dy * dy + dx * dx > 0.75) c = SHADE;
			if (x >= 7 && x <= 8 && y <= 10) c = AZURE;
			if (y == 9 && x >= 2 && x <= 12) c = VISOR;
			if (y >= 10 && x >= 3 && x <= 11) c = 0;           // the open face
			if (y >= 10 && (x == 2 || x == 12)) c = PAD;
			if (y == 12 && (x == 2 || x == 3 || x == 11 || x == 12)) c = SHADE;
			if (c != 0) icon.setRGB(x, y, c);
		}
		for (int y = 1; y < 13; y++) for (int x = 0; x < 16; x++) {
			// dark outline
			if ((icon.getRGB(x, y) >>> 24) != 0) continue;
			boolean edge = false;
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = x + d[0], ny = y + d[1];
				if (nx >= 0 && nx < 16 && ny >= 0 && ny < 16 && (icon.getRGB(nx, ny) >>> 24) != 0 && !(ny >= 10 && nx >= 3 && nx <= 11)) edge = true;
			}
			if (edge && !(y >= 10 && x >= 3 && x <= 11)) icon.setRGB(x, y, 0xFF33363B);
		}
		for (int x = 5; x <= 10; x++) icon.setRGB(x, 14, STRAP); // the strap hanging
		icon.setRGB(4, 13, STRAP); icon.setRGB(11, 13, STRAP);
		ImageIO.write(icon, "png", new File(root, "item/casco.png"));
		System.out.println("casco textures written");
	}
}
