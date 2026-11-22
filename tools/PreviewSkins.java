import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
public class PreviewSkins {
	public static void main(String[] a) throws Exception {
		String[] skins = {"entity/munaciello", "entity/munaciello_gigante"};
		int S = 10;
		BufferedImage out = new BufferedImage(2 * 20 * S + 16 * S + 40, 34 * S, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics(); g.setColor(new Color(0x6f8f6f)); g.fillRect(0, 0, out.getWidth(), out.getHeight());
		int[][] F = {{8,8,8,8,4,0},{20,20,8,12,4,8},{44,20,4,12,0,8},{36,52,4,12,12,8},{4,20,4,12,4,20},{20,52,4,12,8,20},{40,8,8,8,4,0}};
		for (int i = 0; i < skins.length; i++) {
			BufferedImage s = ImageIO.read(new File("src/main/resources/assets/napuli/textures/" + skins[i] + ".png"));
			int ox = 10 + i * 20 * S;
			for (int[] f : F) g.drawImage(s, ox + f[4]*S, 10 + f[5]*S, ox + (f[4]+f[2])*S, 10 + (f[5]+f[3])*S, f[0], f[1], f[0]+f[2], f[1]+f[3], null);
		}
		BufferedImage c = ImageIO.read(new File("src/main/resources/assets/napuli/textures/item/cornicello.png"));
		g.drawImage(c, 2 * 20 * S + 20, 60, 16 * S, 16 * S, null);
		ImageIO.write(out, "png", new File(a[0]));
	}
}
