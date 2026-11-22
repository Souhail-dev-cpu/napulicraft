import java.awt.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
public class PreviewResidents { public static void main(String[] a) throws Exception {
 File dir = new File("src/main/resources/assets/napuli/textures/entity");
 String[] n = {"gennaro","ciro","enzo","teresa","lucia","vendor","tailor","busker","porter","ada","michele","nico","salvatore","rosa","antonio","carabiniere_0","carabiniere_1","carmela","giusy","sofia","passerby_0","passerby_1","passerby_2","passerby_3","passerby_4","passerby_5","mbriana","partenope","anima","giorgia_meloni"};
 int S=6, cw=16*S+14, ch=32*S+34, cols=10;
 BufferedImage o=new BufferedImage(cols*cw, ((n.length+cols-1)/cols)*ch, BufferedImage.TYPE_INT_ARGB); Graphics2D g=o.createGraphics(); g.setColor(new Color(0x7c9a7c)); g.fillRect(0,0,o.getWidth(),o.getHeight()); g.setFont(new Font("SansSerif",Font.BOLD,11));
 int[][] F={{8,8,8,8,4,0},{20,20,8,12,4,8},{44,20,4,12,0,8},{44,20,4,12,12,8},{4,20,4,12,4,20},{4,20,4,12,8,20}};
 for(int k=0;k<n.length;k++){ BufferedImage s=ImageIO.read(new File(dir,n[k]+".png")); int ox=(k%cols)*cw+7, oy=(k/cols)*ch+18;
  for(int[] f:F) g.drawImage(s,ox+f[4]*S,oy+f[5]*S,ox+(f[4]+f[2])*S,oy+(f[5]+f[3])*S,f[0],f[1],f[0]+f[2],f[1]+f[3],null);
  g.setColor(Color.BLACK); g.drawString(n[k],ox,oy-5);}
 ImageIO.write(o,"png",new File(a[0])); } }
