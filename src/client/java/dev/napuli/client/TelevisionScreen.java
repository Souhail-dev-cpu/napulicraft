package dev.napuli.client;

import dev.napuli.TelevisionProgram;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** An original animated broadcast, driven by the server's fictional match calendar. */
final class TelevisionScreen extends Screen {
    private final TelevisionProgram program;
    private int elapsed;
    TelevisionScreen(TelevisionProgram program){super(Component.literal("TeleNapoli"));this.program=program;}
    static void register(){ClientPlayNetworking.registerGlobalReceiver(TelevisionProgram.TYPE,(p,c)->c.client().gui.setScreen(new TelevisionScreen(p)));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){elapsed++;}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int w=Math.min(420,width-24),h=Math.min(244,height-24),x=(width-w)/2,y=(height-h)/2;
        g.fill(0,0,width,height,0xe008101d);g.fill(x-4,y-4,x+w+4,y+h+4,0xff12151b);
        g.fill(x,y,x+w,y+25,0xff1388b4);g.text(font,"TELENAPOLI  ·  "+program.channel(),x+9,y+8,0xffffffff,false);
        boolean sport=program.channel().equals("SPORT")&&!program.opponent().isEmpty();
        if(sport){
            int top=y+48,bottom=y+h-30,fieldH=bottom-top;
            for(int stripe=0;stripe<10;stripe++)g.fill(x+8+stripe*(w-16)/10,top,x+8+(stripe+1)*(w-16)/10,bottom,stripe%2==0?0xff247c40:0xff2c934d);
            g.fill(x+w/2,top,x+w/2+1,bottom,0xffe4eed9);g.fill(x+8,top,x+w-8,top+1,0xffe4eed9);g.fill(x+8,bottom-1,x+w-8,bottom,0xffe4eed9);
            int minute=Math.min(90,program.minute()+elapsed*90/Math.max(1,program.duration()));
            g.centeredText(font,"NAPOLI "+program.napoli()*minute/90+" – "+program.away()*minute/90+" "+program.opponent().toUpperCase(java.util.Locale.ROOT)+"   "+minute+"'",width/2,y+32,0xffffffff);
            double t=(program.clock()+elapsed+delta)/20.;
            for(int team=0;team<2;team++)for(int player=0;player<11;player++){
                int px=x+18+(int)((player%4+1)*(w-40)/5.+Math.sin(t*.6+player+team)*7);
                int py=top+10+(int)((player/4.+team*.5)*(fieldH-20)/4+Math.cos(t+player)*5);
                g.fill(px,py,px+4,py+6,team==0?0xff61c6f2:0xfff3f3f3);g.fill(px,py,px+4,py+1,0xffe3b98f);
            }
            int bx=x+w/2+(int)(Math.sin(t*.7)*(w-80)/3),by=top+fieldH/2+(int)(Math.sin(t*1.3)*(fieldH-20)/3);
            g.fill(bx,by,bx+3,by+3,0xffffffff);
        }else{
            String text=switch(program.channel()){
                case "NEWS"->"Notizie dai quartieri: lavoro, famiglie ed eventi in città.";
                case "WEATHER"->program.opponent();default->"Nessuna partita in diretta. Torna per il prossimo incontro.";
            };
            int lineY=y+55;for(var line:font.split(Component.literal(text),w-30)){g.text(font,line,x+15,lineY,0xffd8e5e9,false);lineY+=12;}
        }
        g.centeredText(font,"Trasmissione fittizia  ·  Esc per tornare al divano",width/2,y+h-15,0xffc5d0db);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){}
}
