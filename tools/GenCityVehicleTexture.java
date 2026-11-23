import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
/** Reproducible palette for the code-defined urban traffic model. */
public final class GenCityVehicleTexture {
    public static void main(String[] args)throws Exception{
        var image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)image.setRGB(x,y,y<48?0xff258bb8:y<96?(y%16<9?0xff254554:0xffd5d6c8):x<32?0xff202429:0xffffeab0);
        ImageIO.write(image,"PNG",Path.of("src/main/resources/assets/napuli/textures/entity/city_vehicle.png").toFile());
    }
}
