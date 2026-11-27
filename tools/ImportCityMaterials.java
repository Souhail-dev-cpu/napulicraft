import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Import the four generated atlas tiles into Minecraft's 64px, mipmap-safe texture format. */
public final class ImportCityMaterials {
    public static void main(String[] args) throws Exception {
        var image=ImageIO.read(Path.of(args[0]).toFile());
        if(image==null||image.getWidth()!=image.getHeight()||image.getWidth()%2!=0)throw new IllegalArgumentException("Expected an even, square four-tile atlas");
        String[] names={"intonaco_ocra","intonaco_corallo","coppi_napoletani","maiolica_napoletana"};
        var folder=Path.of("src/main/resources/assets/napuli/textures/block");Files.createDirectories(folder);int side=image.getWidth()/2;
        for(int i=0;i<4;i++){
            var tile=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);var graphics=tile.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int x=i%2*side,y=i/2*side;graphics.drawImage(image,0,0,64,64,x,y,x+side,y+side,null);graphics.dispose();
            ImageIO.write(tile,"PNG",folder.resolve(names[i]+".png").toFile());
        }
    }
}
