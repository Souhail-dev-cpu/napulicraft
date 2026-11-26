import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Reproducible 16px pixel art extending the repository's inventory texture generators. */
public final class GenKitchenTextures {
    static BufferedImage img;
    static final int INK=0x503326, GOLD=0xe6a54b, LIGHT=0xffd786, BROWN=0xa55b2a, WHITE=0xf4e9ce;
    public static void main(String[] args) throws Exception {
        var rows=Files.readAllLines(Path.of("tools/kitchen-art.tsv"));
        Map<String,String> shapes=new HashMap<>();
        for(String row:rows){var p=row.split("\t");shapes.put(p[0],p[1]);}
        Path output=Path.of("src/main/resources/assets/napuli/textures");
        Files.createDirectories(output.resolve("block")); Files.createDirectories(output.resolve("item"));
        for(String row:rows){
            var p=row.split("\t");String id=p[0],shape=p[1];
            if (args.length > 0 && !java.util.Arrays.asList(args).contains(id)) continue;
            if(shape.equals("block")){
                for(String side:new String[]{"top","side"}){
                    img=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);station(id,side.equals("top"));
                    ImageIO.write(img,"PNG",output.resolve("block/"+id+"_"+side+".png").toFile());
                }
                continue;
            }
            img=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            boolean raw=id.endsWith("_crudo")||id.equals("pizza_cruda");
            if(raw)shape=shapes.getOrDefault(id.replace("_crudo",""),"pie");
            food(id,shape,Integer.parseInt(p[2]));
            if(raw)for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                int c=img.getRGB(x,y);if((c>>>24)==0)continue;
                int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
                img.setRGB(x,y,0xff000000|((r+240)/2)<<16|((g+218)/2)<<8|(b+166)/2);
            }
            ImageIO.write(img,"PNG",output.resolve("item/"+id+".png").toFile());
        }
        System.out.println("Generated "+rows.size()+" kitchen pixel-art assets.");
    }
    static void food(String id,String shape,int seed){
        switch (id.replace("_crudo", "")) {
            case "sartu_riso" -> {
                oval(2, 10, 12, 4, 0x533b4d);
                poly(new int[]{3, 5, 11, 13}, new int[]{11, 5, 5, 11}, 0x754426);
                oval(4, 3, 8, 5, 0xefac4b);
                for (int x = 4; x <= 11; x += 2) px(x, 8 + x % 3, 0xffdf89);
                px(6, 5, 0xb54832); px(9, 5, 0x688440); return;
            }
            case "capitone", "capitone_fritto" -> {
                boolean fried = id.startsWith("capitone_fritto");
                oval(2, 3, 12, 10, 0x433832);
                oval(3, 4, 10, 8, fried ? 0xc4853c : 0x516d72);
                oval(5, 6, 5, 4, 0);
                rect(9, 8, 11, 10, 0);
                line(9, 9, 13, 11, fried ? 0xdb9b45 : 0x53666b);
                px(5, 4, WHITE); px(11, 6, fried ? LIGHT : 0x98b2aa); return;
            }
            case "baccala_napoletana" -> {
                oval(1, 6, 14, 8, 0x653b31);
                oval(2, 6, 12, 6, 0xb34c38);
                poly(new int[]{4, 10, 12, 8, 3}, new int[]{5, 4, 7, 10, 8}, WHITE);
                line(4, 7, 10, 6, 0xbda778);
                px(3, 9, 0x536237); px(11, 9, 0x536237); return;
            }
            case "casatiello" -> {
                oval(1, 5, 14, 10, INK); oval(2, 5, 12, 9, BROWN);
                oval(3, 5, 10, 6, GOLD); oval(6, 7, 4, 3, INK);
                oval(3, 6, 3, 4, WHITE); oval(10, 6, 3, 4, WHITE);
                px(4, 7, 0xf4bb56); px(11, 7, 0xf4bb56); return;
            }
            case "zeppole" -> {
                oval(2, 5, 12, 9, INK); oval(3, 5, 10, 8, GOLD);
                oval(5, 4, 6, 6, WHITE); oval(7, 3, 3, 3, 0xad343d);
                line(4, 10, 10, 10, LIGHT); return;
            }
            case "lasagne_carnevale" -> {
                poly(new int[]{2, 10, 14, 6}, new int[]{6, 3, 7, 11}, 0xd78544);
                poly(new int[]{2, 6, 14, 14, 6, 2}, new int[]{6, 9, 6, 11, 14, 10}, INK);
                line(3, 8, 6, 10, WHITE); line(6, 10, 13, 7, WHITE);
                line(3, 10, 6, 12, GOLD); line(6, 12, 13, 9, 0xb94a39); return;
            }
            case "gatto_patate" -> {
                poly(new int[]{3, 8, 13, 13, 5, 3}, new int[]{5, 3, 6, 11, 14, 10}, INK);
                poly(new int[]{4, 8, 12, 5}, new int[]{5, 4, 6, 9}, GOLD);
                poly(new int[]{5, 12, 12, 5}, new int[]{9, 7, 10, 13}, WHITE);
                px(7, 10, 0xd98971); px(10, 9, 0xd98971); return;
            }
            case "pizza_cruda" -> {
                oval(1, 4, 14, 10, INK); oval(2, 4, 12, 9, LIGHT);
                oval(3, 5, 10, 7, 0xcd6656);
                oval(4, 6, 3, 2, WHITE); oval(8, 8, 3, 2, WHITE); px(8, 6, 0x687e55); return;
            }
            case "insalata_rinforzo" -> {
                oval(1, 8, 14, 7, 0x42586a); oval(2, 6, 12, 7, 0xa0bdc3);
                oval(3, 5, 10, 6, 0x66824f);
                for (int x : new int[]{4, 7, 10}) oval(x, 5 + x % 2, 3, 3, WHITE);
                px(6, 9, 0xb6443d); px(9, 7, 0xb6443d); return;
            }
            case "olio", "rum" -> {
                boolean oil = id.equals("olio");
                rect(6, 2, 9, 6, INK); rect(7, 1, 8, 3, 0x9b734c);
                rect(oil ? 5 : 4, 5, oil ? 10 : 11, 14, INK);
                rect(oil ? 6 : 5, 6, oil ? 9 : 10, 13, oil ? 0x949641 : 0x8d4c39);
                rect(6, 8, 9, 11, oil ? 0xd6d69a : 0xd5a178); return;
            }
            case "borsa_fritto" -> {
                poly(new int[]{3, 13, 11, 5}, new int[]{4, 4, 14, 14}, INK);
                poly(new int[]{4, 12, 10, 6}, new int[]{5, 5, 13, 13}, 0xccaa76);
                line(5, 7, 11, 7, 0xf3daa3); line(7, 9, 10, 9, 0xa15941); return;
            }
            case "pasta" -> {
                for (int x = 3; x < 12; x += 3) {
                    line(x, 4, x + 2, 12, INK); line(x + 1, 4, x + 3, 11, GOLD);
                }
                return;
            }
            case "riso" -> {
                oval(2, 7, 12, 7, 0x705245);
                for (int x = 4; x < 12; x += 2) {
                    line(x, 6 + x % 3, x + 1, 7 + x % 3, WHITE);
                    line(x, 10, x + 1, 11, 0xffdf9c);
                }
                return;
            }
            default -> {}
        }

        int accent=id.contains("friarielli")||id.contains("rinforzo")?0x5e9a42:id.contains("mustaccioli")?0x61372d:0xb74730;
        switch(shape){
            case "bag" -> {rect(3,5,13,14,INK);rect(4,6,12,13,0xbd8750);rect(5,7,11,12,0xd8b578);for(int x:new int[]{4,7,10}){rect(x,2,x+2,8,INK);rect(x,3,x+1,8,LIGHT);}rect(5,11,11,12,0xb35b31);}
            case "fold" -> {poly(new int[]{3,13,11,3},new int[]{3,5,13,3},INK);poly(new int[]{4,12,10,4},new int[]{4,5,12,4},GOLD);line(5,6,11,7,accent);line(6,8,10,9,WHITE);}
            case "shell" -> {oval(2,4,12,10,INK);oval(3,4,10,9,GOLD);for(int x=4;x<=11;x+=2)line(x,5,x-2,11,LIGHT);}
            case "bun" -> {oval(2,5,12,9,INK);oval(3,5,10,8,GOLD);line(5,6,9,6,LIGHT);rect(6,9,10,10,WHITE);}
            case "baba" -> {oval(3,2,10,8,INK);oval(4,2,8,7,GOLD);rect(5,8,11,13,INK);rect(6,8,10,12,BROWN);line(5,4,9,4,LIGHT);}
            case "pie","tray" -> {rect(2,5,14,13,INK);rect(3,5,13,12,GOLD);rect(4,6,12,10,accent);for(int x=4;x<13;x+=3)line(x,6,x-1,10,LIGHT);line(3,11,12,11,WHITE);}
            case "ring" -> {oval(2,3,12,11,INK);oval(3,3,10,10,GOLD);oval(6,6,4,4,INK);oval(7,6,2,3,0);if(id.equals("zeppole")){oval(5,4,6,4,WHITE);px(8,4,0xb93936);}else{px(5,5,LIGHT);px(11,8,LIGHT);}}
            case "diamond" -> {poly(new int[]{8,14,8,2},new int[]{2,8,14,8},INK);poly(new int[]{8,13,8,3},new int[]{3,8,13,8},0x694036);line(6,6,10,6,0x9a6451);}
            case "ribbons" -> {for(int y:new int[]{3,7,11}){rect(2,y,13,y+3,INK);rect(3,y,12,y+2,LIGHT);px(6,y+1,WHITE);}}
            case "balls" -> {oval(1,9,14,5,0x6b4230);for(int y=4;y<11;y+=3)for(int x=3+(y%2);x<13;x+=3){oval(x,y,4,4,INK);oval(x,y,3,3,GOLD);px(x+1,y+1,LIGHT);}px(7,5,0xc04a4a);px(9,9,0x58a65c);}
            case "bowl","plate" -> {oval(1,7,14,7,INK);oval(2,6,12,6,WHITE);oval(3,6,10,4,id.contains("minestra")?0x739348:0xc6a24e);for(int x=4;x<13;x+=3){px(x,8,accent);px(x-1,7,WHITE);}rect(3,11,13,13,0x8e5637);}
            case "fish" -> {poly(new int[]{2,12,13,15,13,12,2,1},new int[]{7,5,2,3,8,11,10,8},INK);oval(2,6,10,4,GOLD);px(3,7,WHITE);px(4,7,INK);line(6,7,9,7,LIGHT);}
            case "bottle" -> {rect(6,1,10,5,INK);rect(7,2,9,5,WHITE);rect(4,5,12,14,INK);rect(5,6,11,13,id.contains("cocomero")?0xd55c61:0xd9d05f);rect(6,7,7,12,WHITE);rect(7,0,9,2,0x8d633d);}
            case "liqueur", "wine", "sparkling" -> drink(id, shape);
            case "coin" -> {oval(2,2,12,12,0x99621d);oval(3,2,10,11,0xf6cc53);line(6,5,9,5,WHITE);line(6,7,9,7,0xa46d23);line(6,9,9,9,WHITE);}
            default -> ingredient(id,seed);
        }
    }
    static void drink(String id, String shape) {
        int liquid = switch (id) {
            case "amaro_napoletano" -> 0x82462b;
            case "crema_limone" -> 0xf3dda0;
            case "liquore_finocchietto" -> 0x739b50;
            case "liquore_liquirizia" -> 0x40312e;
            case "liquore_mandorla" -> 0xc79a66;
            case "falanghina" -> 0xd9cf79;
            case "aglianico" -> 0x852e46;
            case "prosecco" -> 0xb2c974;
            case "spumante" -> 0xce9875;
            case "grappa" -> 0xbdd9da;
            default -> 0xd3b65c;
        };
        boolean wine = shape.equals("wine"), sparkling = shape.equals("sparkling");
        int left = wine ? 5 : 4, right = wine ? 10 : 11;
        int glass = sparkling ? 0x365b47 : wine ? 0x3d634d : 0x667d78;
        rect(6, 1, 9, 6, INK); rect(7, 2, 8, 6, glass);
        rect(left, 6, right, 14, INK); rect(left + 1, 6, right - 1, 13, glass);
        rect(left + 1, 8, right - 1, 13, liquid);
        line(left + 1, 7, left + 1, 12, 0xc6e2d1);
        rect(6, 9, 9, 11, sparkling ? 0xf2cc68 : WHITE);
        px(7, 10, liquid); px(8, 10, liquid);
        rect(6, 1, 9, 2, sparkling ? GOLD : id.equals("aglianico") ? 0xa74a55 : 0x9c7853);
        if (sparkling) { rect(6, 3, 9, 4, GOLD); px(12, 3, LIGHT); px(13, 5, WHITE); }
        else if (!wine) px(8, 4, WHITE);
    }
    static void ingredient(String id,int seed){
        if(id.equals("pizza_cruda")){food(id,"pie",seed);return;}
        if(id.equals("olio")||id.equals("rum")){food(id,"bottle",seed);return;}
        if(id.equals("pomodoro")){oval(2,5,12,9,INK);oval(3,5,10,8,0xd64e36);line(5,4,10,4,0x507a38);line(7,2,8,5,0x507a38);px(4,7,0xf88c5e);return;}
        if(id.equals("limone")){oval(2,4,12,9,INK);oval(3,4,10,8,0xe2c64d);line(4,6,9,6,0xffec93);px(12,4,0x658b42);return;}
        if(id.equals("capitone")||id.equals("baccala")){food(id,"fish",seed);return;}
        if(id.equals("salsiccia")){oval(2,5,12,7,INK);oval(3,5,10,6,0xa95846);line(4,6,10,6,0xd89571);return;}
        if(id.equals("friarielli")){for(int x:new int[]{3,6,9}){line(8,14,x+1,5,0x51713a);oval(x,3,5,6,0x447d40);px(x+1,4,0x8fba5c);}return;}
        if(id.equals("vongole")){oval(2,5,12,9,INK);oval(3,5,10,8,0xb8aca0);for(int x=5;x<12;x+=2)line(8,12,x,6,0x78665e);return;}
        if(id.equals("chicchi_caffe")){for(int x:new int[]{3,8}){oval(x,4,5,8,INK);oval(x,4,4,7,0x8c573b);line(x+2,5,x+1,10,0xd39e65);}return;}
        if(id.equals("farina")||id.equals("sale")){food(id,"bag",seed);rect(5,3,11,8,WHITE);rect(5,8,11,10,id.equals("sale")?0x9ab8c7:0xf3dcaa);return;}
        oval(2,5,12,9,INK);oval(3,5,10,8,id.equals("mozzarella")||id.equals("ricotta")?WHITE:0xe9cc8a);
        for(int x=4;x<12;x+=3)px(x,7,((seed+x)%2==0)?0xffe6b4:0xb89158);
    }
    static void station(String id,boolean top){
        int base=id.contains("forno")?0xa75d46:id.contains("frittura")?0x8a939b:id.contains("enigma")?0x73808b:0x845a3b;
        rect(0,0,16,16,base);
        for(int y=1;y<16;y+=4)line(0,y,15,y,0x564b41);
        if(top && id.contains("forno")) {
            rect(1,1,15,15,0x9a4e3d);
            for (int y=2;y<16;y+=4) { line(0,y,15,y,0x653b35); for (int x=2+y%3;x<15;x+=5) line(x,y,x,y+3,0x653b35); }
            rect(9,3,14,8,0x65332e); rect(10,4,13,7,0x392b2b); return;
        }
        if(top && id.contains("pizzaiolo")) {
            rect(1,1,15,15,0xece1ce); line(1,13,14,13,0xc5aeaa); line(3,3,13,5,0xd4c8bb);
            oval(3,6,7,6,0xc2a97c); oval(4,6,6,5,0xe9cc9e);
            line(6,4,12,10,0x694b38); line(7,4,13,10,0xb58259); px(11,3,WHITE); return;
        }
        if(top){rect(1,1,15,15,id.contains("pizzaiolo")?0xeadbc4:base);if(id.contains("frittura")){rect(3,3,13,13,INK);rect(4,4,12,12,0xc3a34b);for(int x:new int[]{5,8,10})px(x,7,LIGHT);}else if(id.contains("caffe")){oval(4,3,8,9,INK);oval(5,3,6,7,0xb6b2a1);oval(6,4,4,4,0x574032);}else if(id.contains("enigma")){line(3,3,12,3,LIGHT);line(12,3,12,7,LIGHT);line(8,7,12,7,LIGHT);line(8,7,8,10,LIGHT);px(8,13,LIGHT);}else{oval(3,3,10,10,INK);oval(4,4,8,8,GOLD);px(6,6,0xb74730);px(9,8,WHITE);}}else{rect(1,2,15,5,0xc2a37a);rect(3,7,13,14,INK);if(id.contains("forno")||id.contains("frittura")){rect(4,11,12,13,0xd76d2e);px(6,10,GOLD);px(9,9,LIGHT);}else{line(3,7,12,7,WHITE);px(11,10,GOLD);}}
    }
    static void px(int x,int y,int c){if(x>=0&&x<16&&y>=0&&y<16)img.setRGB(x,y,c==0?0:0xff000000|c);}
    static void rect(int x1,int y1,int x2,int y2,int c){for(int y=y1;y<y2;y++)for(int x=x1;x<x2;x++)px(x,y,c);}
    static void oval(int x,int y,int w,int h,int c){for(int dy=0;dy<h;dy++)for(int dx=0;dx<w;dx++)if(Math.pow((dx+.5-w/2.)/(w/2.),2)+Math.pow((dy+.5-h/2.)/(h/2.),2)<=1.05)px(x+dx,y+dy,c);}
    static void line(int x,int y,int x2,int y2,int c){int steps=Math.max(Math.abs(x2-x),Math.abs(y2-y));for(int s=0;s<=steps;s++)px(x+(x2-x)*s/Math.max(1,steps),y+(y2-y)*s/Math.max(1,steps),c);}
    static void poly(int[] xs,int[] ys,int c){var polygon=new java.awt.Polygon(xs,ys,xs.length);for(int y=0;y<16;y++)for(int x=0;x<16;x++)if(polygon.contains(x+.5,y+.5))px(x,y,c);}
}
