package dev.napuli.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Code-drawn city symbols and surfaces: crisp at every GUI scale, without bitmap dependencies. */
final class CompanionTheme {
    static final int BACK=0xff101c2b, NAV=0xff0c1622, CARD=0xff1b2b3e, HOVER=0xff243d50;
    static final int EDGE=0xff30465a, INK=0xfff7f1e6, SOFT=0xffadc0d0, DIM=0xff788d9f;
    static final int SEA=0xff83dfd2, SAND=0xffefc48c, CORAL=0xffffac99, LILAC=0xffc1b5f2;
    enum Icon { CITY, MAP, WORK, HOME, TRANSIT, SHOP, BOOK, JOURNAL, HELP, TARGET, WALLET, GROUP, TRADE, SCOOTER, CHART, STAR, SETTINGS, SEARCH }
    static Icon icon(String command){
        if(command.contains("preferiti")||command.contains("preferito"))return Icon.STAR;
        if(command.contains("atlante")||command.contains("luogo ")||command.contains("segui "))return Icon.MAP;
        if(command.contains("carriera")||command.contains("lavoro"))return Icon.WORK;
        if(command.contains("cooperativ"))return Icon.GROUP;
        if(command.contains("scambi"))return Icon.TRADE;
        if(command.contains("motorini")||command.contains("parcheggi")||command.contains("recupero"))return Icon.SCOOTER;
        if(command.contains("case")||command.contains("casa ")||command.contains("permessi"))return Icon.HOME;
        if(command.contains("trasporti")||command.contains("corse "))return Icon.TRANSIT;
        if(command.startsWith("negozio"))return Icon.SHOP;
        if(command.contains("ricett"))return Icon.BOOK;
        if(command.contains("diario"))return Icon.JOURNAL;
        if(command.contains("aiuto"))return Icon.HELP;
        if(command.contains("obiettivi"))return Icon.TARGET;
        if(command.equals("portafoglio"))return Icon.WALLET;
        if(command.contains("economia")||command.contains("spese"))return Icon.CHART;
        if(command.contains("amici"))return Icon.GROUP;
        return Icon.CITY;
    }
    static int tint(Icon icon){return switch(icon){case WORK,HOME,WALLET,CHART->SAND;case GROUP,TRADE->LILAC;case SHOP,BOOK,HELP->CORAL;default->SEA;};}
    static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int color){
        if(w<=0||h<=0)return;r=Math.max(0,Math.min(r,Math.min(w,h)/2));
        g.fill(x+r,y,x+w-r,y+h,color);g.fill(x,y+r,x+w,y+h-r,color);
        for(int row=0;row<r;row++){int inset=r-(int)Math.floor(Math.sqrt(r*r-(r-row-.5)*(r-row-.5)));g.fill(x+inset,y+row,x+w-inset,y+row+1,color);g.fill(x+inset,y+h-row-1,x+w-inset,y+h-row,color);}
    }
    static void surface(GuiGraphicsExtractor g,int x,int y,int w,int h,int fill,int edge){rounded(g,x,y,w,h,5,edge);rounded(g,x+1,y+1,w-2,h-2,4,fill);}
    static void large(GuiGraphicsExtractor g,Font font,String text,int x,int y,int width,float scale,int color){
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale);
        g.text(font,NapoliScreen.fit(font,text,(int)(width/scale)),0,0,color,false);g.pose().popMatrix();
    }
    private static void line(GuiGraphicsExtractor g,int x,int y,int a,int b,int color){
        int dx=Math.abs(a-x),sx=x<a?1:-1,dy=-Math.abs(b-y),sy=y<b?1:-1,error=dx+dy;
        while(true){g.fill(x,y,x+1,y+1,color);if(x==a&&y==b)break;int twice=2*error;if(twice>=dy){error+=dy;x+=sx;}if(twice<=dx){error+=dx;y+=sy;}}
    }
    private static void box(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+1,color);g.fill(x,y+h-1,x+w,y+h,color);g.fill(x,y,x+1,y+h,color);g.fill(x+w-1,y,x+w,y+h,color);}
    static void icon(GuiGraphicsExtractor g,Icon icon,int x,int y,int c){
        switch(icon){
            case CITY->{line(g,x,y+14,x+6,y+5,c);line(g,x+6,y+5,x+10,y+9,c);line(g,x+10,y+9,x+14,y+3,c);line(g,x+14,y+3,x+19,y+14,c);g.fill(x,y+17,x+20,y+18,c);g.fill(x+3,y+20,x+17,y+21,c);}
            case MAP->{box(g,x+1,y+4,6,15,c);box(g,x+7,y+1,6,15,c);box(g,x+13,y+4,6,15,c);g.fill(x+9,y+7,x+11,y+9,c);}
            case WORK->{box(g,x+1,y+7,19,13,c);box(g,x+7,y+3,7,5,c);g.fill(x+1,y+11,x+20,y+12,c);g.fill(x+9,y+10,x+12,y+14,c);}
            case HOME->{line(g,x,y+10,x+10,y+1,c);line(g,x+10,y+1,x+20,y+10,c);box(g,x+3,y+9,15,12,c);box(g,x+8,y+13,5,8,c);}
            case TRANSIT->{surface(g,x+3,y+1,15,18,NAV,c);box(g,x+5,y+4,11,7,c);g.fill(x+6,y+14,x+8,y+16,c);g.fill(x+13,y+14,x+15,y+16,c);line(g,x+6,y+19,x+3,y+22,c);line(g,x+15,y+19,x+18,y+22,c);}
            case SHOP->{box(g,x+3,y+9,15,12,c);box(g,x+1,y+3,19,7,c);g.fill(x+6,y+3,x+7,y+10,c);g.fill(x+13,y+3,x+14,y+10,c);box(g,x+8,y+14,5,7,c);}
            case BOOK->{box(g,x+1,y+3,9,17,c);box(g,x+10,y+3,9,17,c);g.fill(x+4,y+7,x+7,y+8,c);g.fill(x+13,y+7,x+16,y+8,c);g.fill(x+4,y+11,x+7,y+12,c);}
            case JOURNAL->{box(g,x+4,y+2,14,19,c);g.fill(x+7,y+6,x+14,y+7,c);g.fill(x+7,y+10,x+14,y+11,c);g.fill(x+7,y+14,x+12,y+15,c);g.fill(x+1,y+6,x+6,y+7,c);g.fill(x+1,y+15,x+6,y+16,c);}
            case HELP->{box(g,x+3,y+3,15,17,c);g.fill(x+8,y+6,x+14,y+7,c);g.fill(x+13,y+7,x+14,y+11,c);g.fill(x+10,y+10,x+14,y+11,c);g.fill(x+10,y+11,x+11,y+14,c);g.fill(x+10,y+16,x+11,y+17,c);}
            case TARGET,SEARCH->{box(g,x+2,y+2,13,13,c);if(icon==Icon.TARGET){box(g,x+6,y+6,5,5,c);line(g,x+10,y+10,x+20,y,c);}else{line(g,x+14,y+14,x+20,y+20,c);line(g,x+13,y+14,x+19,y+20,c);}}
            case WALLET->{box(g,x+1,y+5,19,15,c);box(g,x+12,y+10,9,6,c);g.fill(x+15,y+12,x+17,y+14,c);line(g,x+1,y+5,x+14,y+1,c);}
            case GROUP->{box(g,x+8,y+2,6,6,c);box(g,x+1,y+6,5,5,c);box(g,x+16,y+6,5,5,c);box(g,x+6,y+11,10,9,c);line(g,x+1,y+20,x+1,y+14,c);line(g,x+20,y+20,x+20,y+14,c);}
            case TRADE->{line(g,x+1,y+6,x+19,y+6,c);line(g,x+15,y+2,x+19,y+6,c);line(g,x+15,y+10,x+19,y+6,c);line(g,x+2,y+16,x+20,y+16,c);line(g,x+2,y+16,x+6,y+12,c);line(g,x+2,y+16,x+6,y+20,c);}
            case SCOOTER->{box(g,x+1,y+15,5,5,c);box(g,x+15,y+15,5,5,c);line(g,x+4,y+14,x+9,y+10,c);line(g,x+9,y+10,x+17,y+16,c);line(g,x+16,y+4,x+17,y+16,c);g.fill(x+11,y+3,x+17,y+4,c);g.fill(x+5,y+9,x+12,y+10,c);}
            case CHART->{box(g,x+2,y+12,4,8,c);box(g,x+9,y+7,4,13,c);box(g,x+16,y+2,4,18,c);}
            case STAR->{int[] xs={10,13,21,15,17,10,3,5,0,7,10},ys={0,7,8,13,21,17,21,13,8,7,0};for(int i=1;i<xs.length;i++)line(g,x+xs[i-1],y+ys[i-1],x+xs[i],y+ys[i],c);}
            case SETTINGS->{for(int n=0;n<3;n++){int yy=y+5+n*6;g.fill(x+1,yy,x+20,yy+1,c);box(g,x+4+(n%2)*8,yy-2,4,5,c);}}
        }
    }
    private CompanionTheme(){}
}
