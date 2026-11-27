import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Deterministic material atlas for code-authored meshes. Never modifies existing textures. */
public final class GenVehicleInteriors {
    public static void main(String[] args)throws Exception{
        String[] names={"car_blue","car_red","car_cream","car_green","police","taxi","bus","tram","metro"};
        int[] paint={0x245b86,0x963c37,0xc6bda0,0x376657,0x203b5c,0xe1b84b,0xb74531,0xceab50,0xbdc7cf};
        var dir=Path.of("src/main/resources/assets/napuli/textures/entity/vehicles");Files.createDirectories(dir);
        for(int n=0;n<names.length;n++){
            var image=new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
            int[] palette={paint[n],0x82b9cc,0x22272c,0x34465a,0x555b62,0x555a59,0xffeab3,0x2c71da};
            for(int y=0;y<512;y++)for(int x=0;x<512;x++){
                int tile=x/256+(y/128)*2,u=x%256,v=y%128,base=palette[tile];
                if(tile==0&&v>=96)base=u<128?0xc53630:0xe8a83c;
                int grain=Math.floorMod(x*13+y*37+x*y*3,7)-3;
                if(tile==0)grain+=v%32<2?8:0;
                if(tile==2)grain+=u%6==0?-15:0;
                if(tile==3)grain+=(u%12==0||v%12==0)?18:0;
                if(tile==4)grain+=v%16<2?-12:0;
                if(tile==5)grain+=(u%8<2&&v%8<2)?20:0;
                if(tile==6)grain+=(u%4==0||v%4==0)?-15:0;
                int red=Math.clamp((base>>16&255)+grain,0,255),green=Math.clamp((base>>8&255)+grain,0,255),blue=Math.clamp((base&255)+grain,0,255);
                image.setRGB(x,y,(tile==1?38:255)<<24|red<<16|green<<8|blue);
            }
            ImageIO.write(image,"PNG",dir.resolve(names[n]+".png").toFile());
        }
    }
}
