import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Reproducible antialiased phone materials, optical lenses, wallpapers, UI icons and thin clock glyphs. */
public final class GenPhoneTextures {
    static final Path OUT=Path.of("src/main/resources/assets/napuli/textures/phone");
    static String[] ids={"oneplus_16","vivo_x500_pro_max","iqoo_15","iqoo_16","oneplus_15"};
    static int[] colors={0xcdd0d5,0x41464c,0xe4e5e4,0x575666,0x181a1e};
    static Graphics2D g;
    static BufferedImage image(int w,int h){var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);return image;}
    static void save(BufferedImage image,String file)throws Exception{g.dispose();var path=(file.matches(".*_(back|edge|camera|face|flash)$")||file.equals("caricatore_usb_c")?OUT.resolve("../item/phones"):OUT).resolve(file+".png");Files.createDirectories(path.getParent());ImageIO.write(image,"PNG",path.toFile());}
    static void color(int rgb){g.setColor(new Color(rgb));}
    static void lens(int x,int y,int size){g.setPaint(new RadialGradientPaint(x+size*.35f,y+size*.3f,size*.7f,new float[]{0,.38f,.65f,.8f,1},new Color[]{new Color(0x91a4b3),new Color(0x272e38),new Color(0x87909c),new Color(0x161a20),new Color(0x6a707a)}));g.fillOval(x,y,size,size);color(0x070e18);g.fillOval(x+size/8,y+size/8,size*3/4,size*3/4);g.setPaint(new RadialGradientPaint(x+size*.46f,y+size*.42f,size*.38f,new float[]{0,.45f,1},new Color[]{new Color(0x17344b),new Color(0x070f21),new Color(0x344650)}));g.fillOval(x+size/4,y+size/4,size/2,size/2);g.setColor(new Color(150,194,218,150));g.fillOval(x+size/3,y+size/3,size/5,size/9);g.setColor(new Color(164,113,182,90));g.fillOval(x+size/2,y+size/2,size/8,size/5);}
    public static void main(String[] args)throws Exception{
        for(int i=0;i<ids.length;i++){
            String id=ids[i];int base=colors[i];
            var back=image(256,512);g.setPaint(new GradientPaint(0,0,new Color(base).brighter(),256,460,new Color(base).darker()));g.fillRect(0,0,256,512);
            g.setColor(new Color(255,255,255,22));for(int n=0;n<250;n++){int y=n*3;g.drawLine(0,y,256,y-40);}
            if(id.startsWith("iqoo")){color(0x244b85);g.fillRect(43,0,9,512);color(0x6f98cf);g.fillRect(53,0,9,512);color(0xb54b50);g.fillRect(63,0,9,512);}
            color(id.startsWith("vivo")?0xf8e8dc:0x42484b);g.setFont(new Font("SansSerif",Font.BOLD,id.startsWith("vivo")?29:22));g.drawString(id.startsWith("vivo")?"vivo":id.startsWith("iqoo")?"iQOO":"1+",id.startsWith("vivo")?28:110,id.startsWith("vivo")?455:296);
            if(id.equals("oneplus_16")){g.setColor(new Color(235,255,245,140));g.setFont(new Font("SansSerif",Font.PLAIN,15));g.drawString("16 AREA",105,420);}
            if(id.equals("oneplus_16"))onePlus16Back();
            if(id.equals("oneplus_15"))onePlus15Back();
            if(id.equals("iqoo_15"))iqoo15Back();
            if(id.equals("iqoo_16"))iqoo16Back();
            if(id.equals("vivo_x500_pro_max"))vivoX500Back();
            save(back,id+"_back");
            var edge=image(32,128);g.setPaint(new LinearGradientPaint(0,0,32,0,new float[]{0,.17f,.4f,.8f,1},new Color[]{new Color(base).darker(),new Color(id.equals("oneplus_15")?0x55585d:id.equals("iqoo_16")?0x9594a4:0xf1f1ec),new Color(base),new Color(base).darker(),new Color(id.equals("oneplus_15")?0x36393e:id.equals("iqoo_16")?0x747383:0xe5e7e2)}));g.fillRect(0,0,32,128);save(edge,id+"_edge");
            var camera=image(256,256);g.setPaint(new GradientPaint(0,0,new Color(base).darker(),256,256,new Color(0x292c31)));g.fillRoundRect(0,0,256,256,45,45);color(0x15191f);g.fillRoundRect(6,6,244,244,42,42);
            if(id.startsWith("vivo")){lens(9,9,238);lens(48,48,65);lens(148,56,61);lens(94,147,64);g.setColor(new Color(35,101,180));g.fillRect(105,112,47,18);g.setFont(new Font("SansSerif",Font.BOLD,11));color(0xf7f9fc);g.drawString("ZEISS",110,125);}
            else{lens(22,22,90);lens(140,24,90);lens(22,142,90);color(0xede8d1);g.fillOval(168,170,29,29);g.setColor(new Color(255,255,255,70));g.drawRoundRect(2,2,251,251,43,43);}
            if(id.equals("oneplus_16"))onePlus16Camera();
            if(id.equals("oneplus_15"))onePlus15Camera();
            if(id.equals("iqoo_15"))iqoo15Camera();
            if(id.equals("iqoo_16"))iqoo16Camera();
            if(id.equals("vivo_x500_pro_max"))vivoX500Camera();
            save(camera,id+"_camera");
            if(id.equals("oneplus_15")||id.equals("iqoo_15")){
                boolean black=id.equals("oneplus_15");
                var rear=image(496,1024);g.setColor(new Color(0,0,0,60));g.fillRoundRect(4,3,488,1018,66,66);
                g.setPaint(new GradientPaint(0,0,new Color(black?0x55585d:0xe6e7e6),496,1024,new Color(black?0x15171b:0x8c9194)));g.fillRoundRect(8,3,480,1014,66,66);
                g.setClip(new RoundRectangle2D.Float(19,12,458,991,54,54));g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(back,19,12,458,991,null);g.setClip(null);
                g.setColor(new Color(0,0,0,black?85:50));g.fillRoundRect(50,39,305,293,55,55);g.drawImage(camera,48,36,299,282,null);
                color(black?0x34373c:0x9ca1a5);g.fillRoundRect(4,248,5,92,3,3);g.fillRoundRect(487,213,5,112,3,3);save(rear,id+"_rear");
            }
            if(id.equals("oneplus_16")){
                var rear=image(496,1024);g.setColor(new Color(0,0,0,60));g.fillRoundRect(4,3,488,1018,66,66);
                g.setPaint(new GradientPaint(0,0,new Color(0xf0f1f3),496,1024,new Color(0x7d848e)));g.fillRoundRect(8,3,480,1014,66,66);
                g.setClip(new RoundRectangle2D.Float(19,12,458,991,54,54));g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(back,19,12,458,991,null);g.setClip(null);
                g.setColor(new Color(52,58,68,45));g.fillRoundRect(50,39,305,293,55,55);g.drawImage(camera,48,36,299,282,null);
                color(0xa4aab4);g.fillRoundRect(4,248,5,92,3,3);g.fillRoundRect(487,213,5,112,3,3);save(rear,"oneplus_16_rear");
            }
            if(id.equals("iqoo_16")){
                var rear=image(496,1024);g.setColor(new Color(0,0,0,65));g.fillRoundRect(4,3,488,1018,66,66);
                g.setPaint(new GradientPaint(0,0,new Color(0x9998a7),496,1024,new Color(0x272634)));g.fillRoundRect(8,3,480,1014,66,66);
                g.setClip(new RoundRectangle2D.Float(19,12,458,991,54,54));g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(back,19,12,458,991,null);g.setClip(null);
                g.setColor(new Color(18,18,29,90));g.fillRoundRect(49,42,310,309,59,59);g.drawImage(camera,47,37,303,295,null);
                color(0x565565);g.fillRoundRect(4,248,5,92,3,3);g.fillRoundRect(487,213,5,112,3,3);save(rear,"iqoo_16_rear");
            }
            if(id.equals("vivo_x500_pro_max")){
                var flash=image(64,64);g.setPaint(new RadialGradientPaint(29,25,31,new float[]{0,.72f,1},new Color[]{new Color(0xfff8dc),new Color(0xe2dfd1),new Color(0x93979b)}));g.fillOval(2,2,60,60);g.setColor(new Color(255,255,251,180));g.setStroke(new BasicStroke(1));for(int n=10;n<55;n+=8){g.drawLine(n,8,n,55);g.drawLine(8,n,55,n);}save(flash,id+"_flash");
                var rear=image(496,1024);g.setColor(new Color(0,0,0,60));g.fillRoundRect(4,3,488,1018,66,66);
                g.setPaint(new GradientPaint(0,0,new Color(0x9ea3a7),496,1024,new Color(0x42474b)));g.fillRoundRect(8,3,480,1014,66,66);
                g.setClip(new RoundRectangle2D.Float(19,12,458,991,54,54));g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(back,19,12,458,991,null);g.setClip(null);
                g.setColor(new Color(7,10,13,65));g.fillOval(72,38,365,370);g.drawImage(camera,73,35,360,367,null);g.drawImage(flash,60,55,38,38,null);
                color(0x676d72);g.fillRoundRect(4,248,5,92,3,3);g.fillRoundRect(487,213,5,112,3,3);save(rear,id+"_rear");
            }
            var front=image(496,1024);g.setColor(new Color(0,0,0,80));g.fillRoundRect(4,2,488,1020,70,70);g.setPaint(new GradientPaint(0,0,new Color(id.equals("oneplus_15")?0x51545c:id.equals("iqoo_16")?0x9695a2:0xd5dbdc),496,930,new Color(id.equals("oneplus_15")?0x24262c:id.equals("iqoo_16")?0x373645:0x50565d)));g.fillRoundRect(8,3,480,1014,66,66);color(0x070a11);g.fillRoundRect(13,7,470,1006,60,60);
            Shape glass=new RoundRectangle2D.Float(24,17,448,983,49,49);g.setClip(glass);
            boolean co=id.startsWith("oneplus");g.setPaint(new GradientPaint(0,0,new Color(co?0x759aa5:0x8bb7d8),500,1050,new Color(co?0x24344d:0x1f4a83)));g.fillRect(0,0,496,1024);
            for(int n=0;n<14;n++){g.setColor(new Color(co?123:214,co?168:163,co?189:141,18));g.fill(new Ellipse2D.Float(-370+n*11,220+n*7,1040-n*9,1240-n*24));}
            g.setPaint(new GradientPaint(0,500,new Color(co?0x617f84:0x9e8a81),440,950,new Color(co?0xb2ac98:0xd0b08d)));g.fill(new Ellipse2D.Float(-260,600,960,830));
            g.setColor(new Color(255,255,255,21));g.setStroke(new BasicStroke(4));g.draw(new Arc2D.Float(-220,100,800,1120,55,210,Arc2D.OPEN));
            var wallpaper=ImageIO.read(OUT.resolve(id+"_wallpaper.png").toFile());g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(wallpaper,24,17,448,983,null);
            g.setClip(null);
            color(0x02050a);g.fillOval(239,36,19,19);g.setColor(new Color(80,113,145,80));g.fillOval(245,40,5,5);color(0x404951);g.fillRoundRect(486,255,5,90,3,3);g.fillRoundRect(5,212,5,110,3,3);save(front,id+"_front");
            // Keep the product lettering off the interactive home screen, which draws its own widgets.
            BufferedImage productFront=front;
            if(id.equals("oneplus_15")){
                productFront=image(496,1024);g.drawImage(front,0,0,null);color(0xffffff);g.setFont(new Font("SansSerif",Font.PLAIN,72));g.drawString("Never",126,347);g.drawString("Settle",126,414);save(productFront,id+"_product_front");
            }
            var face=image(256,512);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(productFront,0,0,256,512,null);save(face,id+"_face");
            var viewfinder=image(496,1024);g.drawImage(front,0,0,null);g.setComposite(AlphaComposite.Clear);g.fillRect(52,200,392,532);save(viewfinder,id+"_viewfinder");
        }
        var clock=image(704,128);color(0xffffff);g.setFont(new Font("SansSerif",Font.PLAIN,98));for(int i=0;i<11;i++){String glyph=i==10?":":Integer.toString(i);g.drawString(glyph,i*64+(64-g.getFontMetrics().stringWidth(glyph))/2,105);}save(clock,"clock");
        var darkClock=image(704,128);color(0x2b241e);g.setFont(new Font("SansSerif",Font.PLAIN,98));for(int i=0;i<11;i++){String glyph=i==10?":":Integer.toString(i);g.drawString(glyph,i*64+(64-g.getFontMetrics().stringWidth(glyph))/2,105);}save(darkClock,"clock_dark");
        String[] icons={"calls","messages","gps","contacts","settings","about","camera","photos"};int[] tones={0x31b56b,0x347de8,0xe5eee4,0xef964e,0x8c919c,0x8762ce,0x3b4658,0xe76d87};
        for(int i=0;i<icons.length;i++){String id=icons[i];var icon=image(128,128);g.setPaint(new GradientPaint(0,0,new Color(tones[i]).brighter(),110,128,new Color(tones[i])));g.fillRoundRect(2,2,124,124,31,31);g.setStroke(new BasicStroke(8,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));color(0xffffff);
            switch(id){case "calls"->{g.rotate(-.7,64,64);g.drawArc(35,23,61,79,90,180);g.fillRoundRect(28,22,22,31,8,8);g.fillRoundRect(28,78,22,31,8,8);}
                case "messages"->{g.fillRoundRect(24,28,80,61,18,18);g.fillPolygon(new int[]{37,37,58},new int[]{77,106,82},3);color(0x7db5ef);g.setStroke(new BasicStroke(5));g.drawLine(40,48,86,48);g.drawLine(40,65,72,65);}
                case "gps"->{color(0xf0d777);g.fillPolygon(new int[]{0,32,128,96},new int[]{22,0,106,128},4);color(0x91c788);g.fillRect(0,74,128,14);color(0x70a9dd);g.fillPolygon(new int[]{74,128,128,102},new int[]{0,0,128,128},4);color(0xffffff);g.fillPolygon(new int[]{63,96,34},new int[]{22,103,88},3);color(0x377bec);g.fillPolygon(new int[]{63,85,47},new int[]{37,89,80},3);}
                case "contacts"->{g.fillOval(45,25,39,39);g.fillRoundRect(28,73,73,34,22,22);}
                case "settings"->{for(int n=0;n<8;n++){g.rotate(Math.PI/4,64,64);g.fillRoundRect(53,17,22,36,4,4);}g.fillOval(30,30,68,68);color(0x8f95a2);g.fillOval(48,48,32,32);}
                case "about"->{g.drawOval(26,26,76,76);g.fillOval(59,43,10,10);g.drawLine(64,65,64,88);}
                case "camera"->{g.fillRoundRect(18,34,92,66,16,16);g.fillRoundRect(38,24,49,30,9,9);lens(40,44,48);}
                case "photos"->{for(int n=0;n<8;n++){g.rotate(Math.PI/4,64,64);g.setColor(new Color(Color.HSBtoRGB(n/8f,.55f,.98f)));g.fillOval(50,13,29,54);}}
            }save(icon,"icons/"+id);
        }
        var charger=image(64,64);color(0x66717c);g.fillRoundRect(15,20,34,31,7,7);color(0xe8ecea);g.fillRoundRect(17,20,29,27,7,7);color(0x555c67);g.fillRect(23,8,5,13);g.fillRect(36,8,5,13);g.setStroke(new BasicStroke(4));g.draw(new Arc2D.Float(41,31,18,26,-90,190,Arc2D.OPEN));save(charger,"caricatore_usb_c");
        System.out.println("Generated five detailed phone shells and eight OS icons.");
        comparison("oneplus_16","OnePlus 16");comparison("iqoo_16","iQOO 16");comparison("vivo_x500_pro_max","vivo X500 Pro Max");comparison("oneplus_15","OnePlus 15");comparison("iqoo_15","iQOO 15");
    }
    static void comparison(String id,String name)throws Exception{
        var comparison=image(1060,1120);color(0xf4f4f5);g.fillRect(0,0,1060,1120);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(ImageIO.read(OUT.resolve(id+"_rear.png").toFile()),28,44,496,1024,null);g.drawImage(ImageIO.read(OUT.resolve(id+(id.equals("oneplus_15")?"_product_front.png":"_front.png")).toFile()),536,44,496,1024,null);
        color(0x3f4650);g.setFont(new Font("SansSerif",Font.PLAIN,17));String label=name+" · retro";g.drawString(label,276-g.getFontMetrics().stringWidth(label)/2,1095);g.drawString("Display",752,1095);
        g.dispose();Path preview=Path.of(".gradle/phone-client/screenshots/"+id.replace('_','-')+"-riferimento.png");Files.createDirectories(preview.getParent());ImageIO.write(comparison,"PNG",preview.toFile());
    }
    static void onePlus15Back(){
        g.setPaint(new LinearGradientPaint(15,0,235,512,new float[]{0,.4f,.8f,1},new Color[]{new Color(0x34363a),new Color(0x24262a),new Color(0x17191d),new Color(0x101215)}));g.fillRect(0,0,256,512);
        for(int offset=1;offset>=0;offset--){g.setColor(offset==1?new Color(120,126,134,25):new Color(4,6,9,150));g.setStroke(new BasicStroke(2.3f));int x=108+offset,y=244+offset;g.drawLine(x,y,x+25,y);g.drawLine(x,y,x,y+31);g.drawLine(x,y+31,x+31,y+31);g.drawLine(x+31,y+31,x+31,y+10);g.setFont(new Font("SansSerif",Font.PLAIN,24));g.drawString("1",x+8,y+24);g.drawLine(x+36,y-8,x+36,y+5);g.drawLine(x+30,y-2,x+42,y-2);}
    }
    static void onePlus15Camera(){
        g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,256,256);g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new LinearGradientPaint(0,0,248,250,new float[]{0,.48f,1},new Color[]{new Color(0x45474b),new Color(0x292b30),new Color(0x16181c)}));g.fillRoundRect(2,2,252,252,58,58);
        g.setColor(new Color(139,145,153,65));g.setStroke(new BasicStroke(2));g.drawRoundRect(4,4,247,247,55,55);
        color(0x41444b);g.fillRoundRect(18,19,111,222,55,55);color(0x020408);g.fillRoundRect(24,24,99,210,49,49);
        vivoOptic(31,31,85,false);vivoOptic(42,163,64,true);color(0x121820);g.fillRoundRect(69,132,10,3,2,2);
        vivoOptic(147,31,84,false);vivoOptic(148,152,83,false);color(0xd4d4cd);g.fillOval(171,175,37,37);
        g.setColor(new Color(255,255,246,180));g.fillOval(178,181,13,13);g.setColor(new Color(114,116,107,140));g.setStroke(new BasicStroke(1));for(int x=177;x<204;x+=5)g.drawLine(x,179,x,207);
    }
    static void iqoo15Back()throws Exception{
        var marble=ImageIO.read(OUT.resolve("iqoo_15_marble.png").toFile());g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(marble,0,0,256,512,null);
        g.setColor(new Color(255,255,255,42));g.setStroke(new BasicStroke(1));g.drawLine(3,72,3,490);
        color(0x22242a);g.setFont(new Font("SansSerif",Font.PLAIN,23));String logo="iQOO";g.drawString(logo,(256-g.getFontMetrics().stringWidth(logo))/2,424);
    }
    static void iqoo15Camera(){
        g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,256,256);g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new LinearGradientPaint(0,0,250,250,new float[]{0,.5f,1},new Color[]{new Color(0xf0f0ed),new Color(0x83898d),new Color(0xd3d7d7)}));g.fillRoundRect(1,1,254,254,66,66);
        color(0x43474c);g.fillRoundRect(5,5,246,246,62,62);g.setPaint(new GradientPaint(0,0,new Color(0x24272c),230,250,new Color(0x080a0e)));g.fillRoundRect(11,11,234,234,57,57);
        g.setColor(new Color(184,190,198,25));g.setStroke(new BasicStroke(1));g.drawRoundRect(15,15,226,226,54,54);
        vivoOptic(29,31,77,false);vivoOptic(147,34,72,false);vivoOptic(30,148,77,true);vivoOptic(146,148,75,false);
        color(0xe1dfd6);g.fillOval(170,172,27,27);g.setColor(new Color(255,255,247,160));g.fillOval(175,176,8,8);
        g.setFont(new Font("SansSerif",Font.PLAIN,12));color(0x666b71);g.drawString("100x",110,127);g.setColor(new Color(139,147,154,55));g.drawLine(112,134,143,134);
    }
    static void vivoX500Back(){
        g.setPaint(new LinearGradientPaint(15,0,238,512,new float[]{0,.35f,.75f,1},new Color[]{new Color(0x74797f),new Color(0x4b5056),new Color(0x34393f),new Color(0x292d31)}));g.fillRect(0,0,256,512);
        g.setColor(new Color(221,226,230,20));g.setStroke(new BasicStroke(.8f));g.drawLine(3,72,3,490);
        var transform=g.getTransform();g.translate(39,440);g.rotate(-Math.PI/2);g.setFont(new Font("SansSerif",Font.PLAIN,28));g.setColor(new Color(13,18,23,160));g.drawString("vivo",0,0);g.setColor(new Color(156,163,171,28));g.drawString("vivo",0,-1);g.setTransform(transform);
    }
    static void vivoOptic(int x,int y,int size,boolean bright){
        g.setPaint(new RadialGradientPaint(x+size*.42f,y+size*.36f,size*.67f,new float[]{0,.7f,1},new Color[]{new Color(0x1d262c),new Color(0x080c10),new Color(0x373f45)}));g.fillOval(x,y,size,size);
        color(0x020509);g.fillOval(x+3,y+3,size-6,size-6);
        int lens=size/2;g.setPaint(new RadialGradientPaint(x+size*.48f,y+size*.46f,size*.3f,new float[]{0,.55f,1},new Color[]{new Color(bright?0x1c405b:0x18242e),new Color(0x03080f),new Color(bright?0x324852:0x1c252c)}));g.fillOval(x+(size-lens)/2,y+(size-lens)/2,lens,lens);
        g.setColor(new Color(145,184,207,bright?120:45));g.fillOval(x+size/3,y+size/3,size/6,size/11);
    }
    static void vivoX500Camera(){
        g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,256,256);g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new LinearGradientPaint(12,0,239,256,new float[]{0,.35f,.7f,1},new Color[]{new Color(0x8d949a),new Color(0x3b4247),new Color(0x22272c),new Color(0x707980)}));g.fillOval(1,1,254,254);
        color(0x101519);g.fillOval(7,7,242,242);g.setPaint(new GradientPaint(0,0,new Color(0x181d21),256,256,new Color(0x020506)));g.fillOval(11,11,234,234);
        g.setColor(new Color(204,218,229,24));g.setStroke(new BasicStroke(1.5f));g.drawArc(14,14,228,228,23,124);
        vivoOptic(44,46,64,false);vivoOptic(146,46,66,true);vivoOptic(53,146,62,false);vivoOptic(143,146,60,true);
        color(0x18499c);g.fillRect(115,116,26,20);g.setFont(new Font("SansSerif",Font.BOLD,7));color(0xf5f9ff);g.drawString("ZEISS",117,129);
        color(0x9c302c);g.setFont(new Font("SansSerif",Font.BOLD,10));g.drawString("T*",122,148);
    }
    static void iqoo16Back(){
        g.setPaint(new LinearGradientPaint(0,0,225,512,new float[]{0,.35f,.76f,1},new Color[]{new Color(0x8b8a9b),new Color(0x626172),new Color(0x393748),new Color(0x282536)}));g.fillRect(0,0,256,512);
        g.setColor(new Color(232,230,246,12));g.setStroke(new BasicStroke(1));g.drawLine(3,80,3,488);g.setColor(new Color(8,8,18,18));g.drawLine(252,80,252,488);
        g.setFont(new Font("SansSerif",Font.PLAIN,24));String name="iQOO";int x=(256-g.getFontMetrics().stringWidth(name))/2;
        g.setColor(new Color(13,12,27,50));g.drawString(name,x+1,430);g.setColor(new Color(160,156,184,82));g.drawString(name,x,429);
    }
    static void iqoo16Camera(){
        // Reference: three lenses in a triangle, two on the left and one at centre-right.
        g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,256,256);g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new LinearGradientPaint(0,0,256,256,new float[]{0,.4f,1},new Color[]{new Color(0x92919f),new Color(0x626271),new Color(0x30303f)}));g.fillRoundRect(1,1,254,254,62,62);
        g.setColor(new Color(180,179,201,90));g.setStroke(new BasicStroke(3));g.drawRoundRect(5,5,245,245,58,58);
        g.setPaint(new GradientPaint(10,10,new Color(0x4e4e5c),236,243,new Color(0x30303f)));g.fillRoundRect(15,15,226,226,46,46);
        g.setClip(new RoundRectangle2D.Float(18,18,220,220,43,43));g.setStroke(new BasicStroke(.65f));g.setColor(new Color(162,161,183,24));for(int n=-250;n<500;n+=5)g.drawLine(n,15,n+190,241);g.setClip(null);
        g.setColor(new Color(143,141,158,95));g.fillPolygon(new int[]{63,211,63},new int[]{28,126,227},3);
        g.setColor(new Color(39,39,53,120));g.fillPolygon(new int[]{63,211,63},new int[]{61,126,193},3);
        lens(27,26,86);lens(146,86,84);lens(28,151,85);
        color(0xa34a54);g.fillRect(164,28,4,7);g.setColor(new Color(184,180,199,130));g.setStroke(new BasicStroke(1));g.drawLine(171,31,197,31);g.drawLine(171,36,206,36);
    }
    static void onePlus16Camera(){
        // User's close-up: pale silver island, continuous black capsule, black-ringed optic and flash.
        g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,256,256);g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new LinearGradientPaint(0,0,250,256,new float[]{0,.36f,.76f,1},new Color[]{new Color(0xf0f0f4),new Color(0xd9d9e1),new Color(0xc6c8d0),new Color(0xb3b7c1)}));
        g.fillRoundRect(1,1,254,254,65,65);g.setColor(new Color(255,255,255,155));g.setStroke(new BasicStroke(2));g.drawRoundRect(3,3,249,249,62,62);
        g.setPaint(new GradientPaint(15,18,new Color(0xa2a6b1),125,237,new Color(0x7b808c)));g.fillRoundRect(16,17,116,224,59,59);
        color(0x020308);g.fillRoundRect(22,22,104,213,53,53);
        // Both left optics sit inside the same uninterrupted glass, without separate silver rings.
        onePlus16Glass(74,67,46,false);onePlus16Glass(74,194,44,true);
        color(0x131720);g.fillRoundRect(67,123,14,4,3,3);
        g.setPaint(new GradientPaint(145,19,new Color(0x7e828d),234,113,new Color(0xb3b7c1)));g.fillOval(143,20,92,92);
        color(0x030409);g.fillOval(147,24,84,84);onePlus16Glass(189,66,43,false);
        g.setPaint(new GradientPaint(145,146,new Color(0x7a7f8a),234,237,new Color(0xa3a8b3)));g.fillOval(144,144,91,91);
        color(0x020409);g.fillOval(148,148,83,83);
        g.setPaint(new RadialGradientPaint(186,186,21,new float[]{0,.65f,1},new Color[]{new Color(0xfffee9),new Color(0xf4efcf),new Color(0xcac4a7)}));g.fillOval(172,172,35,35);
        g.setClip(new Ellipse2D.Float(173,173,33,33));g.setColor(new Color(138,134,112,70));g.setStroke(new BasicStroke(.7f));
        for(int n=176;n<207;n+=4){g.drawLine(n,173,n,207);g.drawLine(173,n,207,n);}g.setClip(null);
        g.setColor(new Color(255,255,246,160));g.fillOval(178,177,10,8);
    }
    static void onePlus16Glass(int cx,int cy,int size,boolean blue){
        int x=cx-size/2,y=cy-size/2;
        g.setPaint(new RadialGradientPaint(cx-size*.13f,cy-size*.12f,size*.57f,new float[]{0,.5f,.78f,1},new Color[]{new Color(blue?0x182c43:0x151b2a),new Color(0x060a12),new Color(0x202737),new Color(0x06090f)}));g.fillOval(x,y,size,size);
        g.setColor(new Color(70,86,111,60));g.setStroke(new BasicStroke(1));g.drawArc(x+3,y+3,size-6,size-6,30,155);
        color(0x03060b);g.fillOval(cx-size/5,cy-size/5,size*2/5,size*2/5);
        g.setColor(new Color(155,175,206,110));g.fillOval(cx-size/4,cy-size/4,size/4,size/7);
        g.setColor(new Color(94,116,164,65));g.fillOval(cx+size/10,cy+size/10,size/7,size/5);
    }
    static void onePlus16Back(){
        g.setPaint(new LinearGradientPaint(30,0,220,512,new float[]{0,.35f,.74f,1},new Color[]{new Color(0xe7e8ec),new Color(0xd6d8de),new Color(0xbcc0c8),new Color(0x9fa4ae)}));g.fillRect(0,0,256,512);
        g.setStroke(new BasicStroke(.8f));
        for(int ring=0;ring<10;ring++){var path=new Path2D.Float();for(int n=0;n<=100;n++){double a=n*Math.PI*2/100,r=69+ring*4.2+2.4*Math.sin(a*5+ring*.3);float x=(float)(129+Math.cos(a)*r*.89),y=(float)(259+Math.sin(a)*r);if(n==0)path.moveTo(x,y);else path.lineTo(x,y);}g.setColor(new Color(250,251,255,ring%2==0?22:12));g.draw(path);}
        for(int offset=1;offset>=0;offset--){g.setColor(offset==1?new Color(100,107,120,32):new Color(250,251,255,94));g.setStroke(new BasicStroke(2));int x=108+offset,y=244+offset;g.drawLine(x,y,x+25,y);g.drawLine(x,y,x,y+31);g.drawLine(x,y+31,x+31,y+31);g.drawLine(x+31,y+31,x+31,y+10);g.setFont(new Font("SansSerif",Font.PLAIN,24));g.drawString("1",x+8,y+24);g.drawLine(x+36,y-8,x+36,y+5);g.drawLine(x+30,y-2,x+42,y-2);}
        var font=new Font("SansSerif",Font.PLAIN,45);Shape numeral=font.createGlyphVector(g.getFontRenderContext(),"16").getOutline(35,430);
        for(int glow=10;glow>0;glow--){g.setColor(new Color(255,245,179,7));g.setStroke(new BasicStroke(glow,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(numeral);}
        g.setColor(new Color(255,250,204));g.fill(numeral);g.setFont(new Font("SansSerif",Font.BOLD,8));g.drawString("AREA",53,439);
        g.setStroke(new BasicStroke(1.4f));g.drawLine(185,399,185,410);g.drawLine(180,404,190,404);
        for(int row=0;row<3;row++){g.drawLine(198,400+row*5,222-row*4,400+row*5);g.fillRect(192,399+row*5,2,2);}
    }
}
