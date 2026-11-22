import java.awt.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
public class IconSheet { public static void main(String[] a) throws Exception {
 String[] n = {"pistola","manganello","coltello","tirapugni","cornicello"}; int S=10;
 BufferedImage o = new BufferedImage(n.length*(16*S+20)+20 + 64*5+20, 64*5+20, BufferedImage.TYPE_INT_ARGB); Graphics2D g=o.createGraphics(); g.setColor(new Color(0x8b8b8b)); g.fillRect(0,0,o.getWidth(),o.getHeight());
 for (int i=0;i<n.length;i++) g.drawImage(ImageIO.read(new File("src/main/resources/assets/napuli/textures/item/"+n[i]+".png")), 20+i*(16*S+20), 20, 16*S, 16*S, null);
 g.drawImage(ImageIO.read(new File("src/main/resources/assets/napuli/textures/entity/motorino.png")), 20+n.length*(16*S+20), 10, 64*5, 64*5, null);
 ImageIO.write(o,"png",new File(a[0])); } }
