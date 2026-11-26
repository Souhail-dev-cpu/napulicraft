package dev.napuli.client;

import static dev.napuli.CityText.t;
import dev.napuli.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Portrait shell with physical bezels and two OS visual languages; every communication action is server checked. */
final class PhoneScreen extends Screen {
    private PhonePacket state;
    private String app="home", query="", draft="";
    private final Map<UUID,String> drafts=new HashMap<>();
    private UUID peer;
    private int page, ticks, x, y;
    private float scale;
    private EditBox input;
    private boolean filterDirty;
    private boolean micWasMuted=true;
    private static final int W=248,H=512;
    private static final Map<String,Integer> COLORS=Map.of("calls",0xff37bc74,"messages",0xff3985f3,"gps",0xff428bc6,"contacts",0xffef984d,"settings",0xff8f93a0,"about",0xff9763e8,"camera",0xff54637a,"photos",0xffdf788e);
    PhoneScreen(PhonePacket state){super(Component.literal(PhoneModel.values()[state.model()].name));this.state=state;}
    PhonePacket snapshot(){return state;}
    String app(){return app;}
    void update(PhonePacket next){
        boolean rebuild=state.model()!=next.model()||state.airplane()!=next.airplane()||!state.goal().equals(next.goal())
                ||!state.contacts().equals(next.contacts())||state.messages().size()!=next.messages().size()
                ||(state.call()==null)!=(next.call()==null)||state.call()!=null&&next.call()!=null&&state.call().active()!=next.call().active();
        state=next;if(rebuild)refreshWidgets();
    }
    private PhoneModel model(){return PhoneModel.values()[state.model()];}
    private int homeInk(){return model()==PhoneModel.VIVO_X500_PRO_MAX?0xff2b241e:0xffffffff;}
    private int ink(){return PhoneClient.dark?0xfff3f5fa:0xff182337;}
    private int muted(){return PhoneClient.dark?0xffa1b1c9:0xff65738c;}
    private int surface(){return PhoneClient.dark?0xff222d43:0xfff8faff;}
    private int px(float v){return x+Math.round(v*scale);}
    private int py(float v){return y+Math.round(v*scale);}
    @Override protected void init(){scale=Math.min((width-16f)/W,(height-12f)/H);x=Math.round((width-W*scale)/2);y=Math.round((height-H*scale)/2);build();}
    private void saveInput(){if(input!=null){if(app.equals("thread")){draft=input.getValue();if(peer!=null)drafts.put(peer,draft);}else query=input.getValue();}}
    private void refreshWidgets(){boolean focused=input!=null&&input.isFocused();int cursor=input==null?0:input.getCursorPosition();saveInput();rebuildWidgets();if(focused&&input!=null){input.setCursorPosition(Math.min(cursor,input.getValue().length()));input.setFocused(true);setFocused(input);}}
    private void go(String next){saveInput();app=next;if(app.equals("thread"))draft=drafts.getOrDefault(peer,"");query="";page=0;rebuildWidgets();}
    private void request(String operation,String target,String value){PhoneClient.request(operation,target,value);}
    @Override public void tick(){super.tick();if(filterDirty){filterDirty=false;refreshWidgets();}if(micWasMuted!=PhoneAudio.muted()){micWasMuted=PhoneAudio.muted();if(app.equals("calls"))refreshWidgets();}if(++ticks%40==0)request("refresh","","");}
    @Override public void removed(){super.removed();request("close","","");}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt){}
    private void build(){
        input=null;
        tap(97,487,54,9,t("phone.home"),()->go("home"),0,false);
        if(!app.equals("home"))tap(17,43,25,24,Component.literal("‹"),()->go(app.equals("thread")?"messages":"home"),0,false);
        switch(app){
            case "home" -> {
                String[] icons={"gps","contacts","settings","about","camera","photos","calls","messages"};
                for(int i=0;i<icons.length;i++){String id=icons[i];int bx=i<6?27+i%3*68:53+(i-6)*94,by=i<6?263+i/3*74:425;
                    icon(bx,by,id,()->go(id));}
                if(state.call()!=null)tap(22,202,204,42,t(state.call().active()?"phone.active_call":"phone.incoming",state.call().name()),()->go("calls"),0xff317765,true);
            }
            case "contacts", "messages", "calls" -> {
                if(app.equals("calls")&&state.call()!=null){
                    var call=state.call();
                    if(call.incoming()&&!call.active())tap(25,274,198,36,t("phone.answer"),()->request("answer",call.id().toString(),""),0xff2aa66a,true);
                    if(call.active())tap(25,274,198,36,t(PhoneAudio.muted()?"phone.mic_enable":"phone.mic_disable"),()->{PhoneAudio.toggle();rebuildWidgets();},0xff4e6f9e,true);
                    tap(25,322,198,36,t("phone.end"),()->request("end",call.id().toString(),""),0xffd95666,true);
                    tap(25,371,198,28,t("phone.call_chat"),()->{peer=call.peer();go("thread");},0,false);
                }else{
                    edit(24,79,200,27,false);var contacts=filteredContacts();int size=5,start=Math.min(page*size,contacts.size());
                    for(int i=start;i<Math.min(start+size,contacts.size());i++){var c=contacts.get(i);final String selectedApp=app;
                        contact(23,117+(i-start)*59,c,()->{peer=c.id();if(selectedApp.equals("calls"))request("call",peer.toString(),"");else go("thread");});}
                    paging(contacts.size(),size,425);
                }
            }
            case "thread" -> {
                edit(24,390,200,29,true);
                tap(24,429,127,31,t("phone.send"),()->{if(peer!=null&&!input.getValue().isBlank()){request("message",peer.toString(),input.getValue());input.setValue("");draft="";}},0xff347af0,true);
                tap(158,429,66,31,t("phone.call"),()->{request("call",peer.toString(),"");go("calls");},0xff2ba46c,true);
                var history=thread();paging(history.size(),5,350);
            }
            case "gps" -> {
                edit(24,79,200,27,false);var places=filteredPlaces();int size=3,start=Math.min(page*size,places.size());
                for(int i=start;i<Math.min(start+size,places.size());i++){var p=places.get(i);tap(24,285+(i-start)*40,200,34,p.name(),()->request("navigate",p.id(),""),surface(),false);}
                paging(places.size(),size,411);
                tap(24,443,200,22,t(state.goal().isEmpty()?"phone.to_napoli":"phone.stop_gps"),()->{if(state.goal().isEmpty()){onClose();minecraft.getConnection().sendCommand("napoli");}else request("stop_gps","","");},0,false);
            }
            case "settings" -> {
                tap(24,103,200,47,t(state.airplane()?"phone.airplane_on":"phone.airplane_off"),()->request("airplane","",""),surface(),false);
                tap(24,166,200,47,t(PhoneClient.dark?"phone.dark_on":"phone.dark_off"),()->{PhoneClient.dark=!PhoneClient.dark;CompanionOptions.current.phoneDark=PhoneClient.dark;CompanionOptions.current.save();rebuildWidgets();},surface(),false);
                tap(24,229,200,47,t("phone.recharge"),()->request("charge","",""),surface(),false);
                tap(24,292,200,47,t("phone.about"),()->go("about"),surface(),false);
            }
            case "about" -> paging(12,6,434);
            case "camera" -> tap(96,416,56,42,t("phone.capture"),this::photo,0xffeeeeee,false);
            case "photos" -> tap(24,427,200,32,t("phone.photos_folder"),()->PhonePhoto.openFolder(minecraft),surface(),false);
            default -> {}
        }
    }
    private void edit(int bx,int by,int bw,int bh,boolean message){
        input=new EditBox(font,px(bx),py(by),Math.round(bw*scale),Math.max(12,Math.round(bh*scale)),t(message?"phone.write":"phone.search"));
        input.setMaxLength(message?256:64);input.setValue(message?draft:query);input.setHint(t(message?"phone.write":"phone.search"));
        input.setResponder(value->{if(!message){query=value;page=0;filterDirty=true;}});addRenderableWidget(input);
    }
    private void icon(int bx,int by,String id,Runnable action){
        tap(bx-2,by-2,56,63,t("phone."+id),action,0,false, (g)->{
            g.blit(Napuli.id("textures/phone/icons/"+id+".png"),bx,by,bx+48,by+48,0,1,0,1);
            center(g,t("phone."+id).getString(),bx+24,by+53,PhoneClient.dark||app.equals("home")?0xffffffff:ink(),62);
        });
    }
    private void contact(int bx,int by,PhonePacket.Contact c,Runnable action){tap(bx,by,202,50,Component.literal(c.name()),action,surface(),false,g->{
        rounded(g,bx+7,by+8,32,32,16,c.online()?0xffd2e9ff:0xffdfe2e9);center(g,c.name().substring(0,1).toUpperCase(Locale.ROOT),bx+23,by+20,0xff2a527a,27);
        text(g,c.name(),bx+48,by+11,ink(),143);text(g,c.number()+(c.online()?"  •":""),bx+48,by+29,muted(),143);
    });}
    private Button tap(int bx,int by,int bw,int bh,Component label,Runnable action,int color,boolean white){return tap(bx,by,bw,bh,label,action,color,white,null);}
    private Button tap(int bx,int by,int bw,int bh,Component label,Runnable action,int color,boolean white,java.util.function.Consumer<GuiGraphicsExtractor> contents){
        var button=new Button(px(bx),py(by),Math.max(1,Math.round(bw*scale)),Math.max(1,Math.round(bh*scale)),label,b->action.run(),s->s.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt){
                g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale);
                if(color!=0)rounded(g,bx,by,bw,bh,Math.min(12,bh/2),color);
                if(isHoveredOrFocused())rounded(g,bx,by,bw,bh,Math.min(12,bh/2),0x254b88c9);
                if(contents!=null)contents.accept(g);else if(bh>10)center(g,label.getString(),bx+bw/2,by+(bh-8)/2,white?0xffffffff:ink(),bw-12);
                g.pose().popMatrix();
            }
        };button.setTooltip(Tooltip.create(label));return addRenderableWidget(button);
    }
    private List<PhonePacket.Contact> filteredContacts(){return state.contacts().stream().filter(c->CompanionBoard.normalize(c.name()+" "+c.number()).contains(CompanionBoard.normalize(query))).toList();}
    private List<PhonePacket.Place> filteredPlaces(){return state.places().stream().filter(p->CompanionBoard.normalize(p.name().getString()).contains(CompanionBoard.normalize(query))).toList();}
    private List<PhonePacket.Text> thread(){return state.messages().stream().filter(m->m.peer().equals(peer)).toList();}
    private void paging(int count,int size,int by){int pages=Math.max(1,(count+size-1)/size);page=Math.clamp(page,0,pages-1);
        if(page>0)tap(24,by,53,25,Component.literal("‹"),()->{saveInput();page--;rebuildWidgets();},surface(),false);
        if(page+1<pages)tap(171,by,53,25,Component.literal("›"),()->{saveInput();page++;rebuildWidgets();},surface(),false);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(app.equals("home")||app.equals("settings")||app.equals("camera"))return super.mouseScrolled(mx,my,horizontal,vertical);saveInput();page=Math.max(0,page+(vertical<0?1:-1));rebuildWidgets();return true;}
    @Override public boolean keyPressed(KeyEvent event){if(event.key()==257&&app.equals("thread")&&input!=null){request("message",peer.toString(),input.getValue());input.setValue("");draft="";return true;}return super.keyPressed(event);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        if(!app.equals("camera"))g.fill(0,0,width,height,0x960b1020);
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale);
        g.blit(Napuli.id("textures/phone/"+model().id+(app.equals("camera")?"_viewfinder":"_front")+".png"),0,0,W,H,0,1,0,1);
        if(!app.equals("home")&&!app.equals("camera"))rounded(g,13,33,222,445,23,PhoneClient.dark?0xff141d30:0xffedf1f8);
        int statusColor=app.equals("home")?homeInk():ink();
        text(g,clock(),29,18,statusColor,48);text(g,state.airplane()?"✈":"5G",170,18,statusColor,25);
        rounded(g,200,17,22,9,3,statusColor);rounded(g,201,18,Math.max(1,state.charge()*20/100),7,2,state.charge()<20?0xffd9545f:0xff53cd93);
        if(app.equals("home"))home(g);else{
            text(g,t("phone."+app).getString(),50,51,ink(),168);
            switch(app){case "calls"->calls(g);case "contacts","messages"->{if(filteredContacts().isEmpty())paragraph(g,t("phone.no_contacts"),27,134,190,6,muted());}
                case "thread"->messages(g);case "gps"->map(g);case "about"->about(g);
                case "camera"->{g.fill(26,99,222,100,0x90ffffff);g.fill(26,365,222,366,0x90ffffff);g.fill(89,100,90,366,0x60ffffff);g.fill(158,100,159,366,0x60ffffff);center(g,"1×",124,382,0xffffffff,100);}
                case "photos"->{paragraph(g,t("phone.photos_help"),25,105,198,8,ink());}
                default->{}
            }
        }
        if(!state.status().getString().isBlank()&&!app.equals("home"))text(g,state.status().getString(),25,470,muted(),198);
        rounded(g,96,490,56,3,2,app.equals("home")?0xeeffffff:ink());
        g.pose().popMatrix();super.extractRenderState(g,mx,my,dt);
    }
    private String clock(){if(minecraft.level==null)return "08:30";long time=Math.floorMod(minecraft.level.getOverworldClockTime()+6000,24000);return String.format(Locale.ROOT,"%02d:%02d",time/1000,time%1000*60/1000);}
    private void home(GuiGraphicsExtractor g){
        String clockTexture=model()==PhoneModel.VIVO_X500_PRO_MAX?"clock_dark":"clock";
        String time=clock();for(int i=0;i<time.length();i++){int glyph=time.charAt(i)==':'?10:time.charAt(i)-'0';int bx=30+i*38;g.blit(Napuli.id("textures/phone/"+clockTexture+".png"),bx,78,bx+38,140,glyph/11f,(glyph+1)/11f,0,1);}
        center(g,t("phone.home_date").getString(),124,151,homeInk(),200);
        center(g,model().os,124,164,homeInk(),200);
        if(model().colorOS())rounded(g,24,179,200,63,18,0x28ffffff);else{rounded(g,24,179,96,63,19,0x30ffffff);rounded(g,128,179,96,63,19,0x30ffffff);}
        text(g,t("phone.weather").getString(),35,191,homeInk(),model().colorOS()?175:75);
        text(g,"NAPOLI  ·  "+(minecraft.level!=null&&minecraft.level.isRaining()?"☂":"☀"),35,210,homeInk(),model().colorOS()?174:76);
        if(!model().colorOS()){text(g,state.airplane()?"OFFLINE":"5G",139,191,homeInk(),75);text(g,state.number(),139,211,homeInk(),75);text(g,state.charge()+"%",139,226,homeInk(),75);}
        else text(g,state.number(),123,225,homeInk(),100);
    }
    private void calls(GuiGraphicsExtractor g){var c=state.call();if(c==null)return;
        rounded(g,84,103,80,80,40,0xffd5e7ff);center(g,c.name().substring(0,1).toUpperCase(Locale.ROOT),124,139,0xff31577a,70);
        center(g,c.name(),124,200,ink(),198);center(g,c.active()?String.format(Locale.ROOT,"%02d:%02d",c.seconds()/60,c.seconds()%60):t(c.incoming()?"phone.ringing_in":"phone.ringing_out").getString(),124,225,muted(),198);
        paragraph(g,t(PhoneAudio.error.isEmpty()?"phone.mic_hint":PhoneAudio.error),28,414,192,4,muted());
    }
    private void messages(GuiGraphicsExtractor g){
        String name=state.contacts().stream().filter(c->c.id().equals(peer)).map(PhonePacket.Contact::name).findFirst().orElse("Chat");text(g,name,26,83,muted(),198);
        var all=thread();int end=Math.max(0,all.size()-page*5),start=Math.max(0,end-5),by=109;
        for(var m:all.subList(start,end)){
            int bx=m.outgoing()?45:24;rounded(g,bx,by,179,44,11,m.outgoing()?0xffd6e8ff:surface());paragraph(g,Component.literal(m.body()),bx+9,by+8,161,3,m.outgoing()?0xff193254:ink());by+=47;
        }
        if(all.isEmpty())paragraph(g,t("phone.no_messages"),26,130,196,5,muted());
    }
    private void map(GuiGraphicsExtractor g){
        rounded(g,24,115,200,143,15,PhoneClient.dark?0xff263e48:0xffdfece5);
        if(minecraft.player!=null)for(int row=0;row<128;row+=4)for(int col=0;col<192;col+=4){
            int rgb=NapoliRadar.terrainColor(minecraft.player.getBlockX()+Math.round((col-96)*1.4f),minecraft.player.getBlockZ()+Math.round((row-64)*1.4f));
            int r=(rgb>>16&255),green=(rgb>>8&255),b=rgb&255;if(!PhoneClient.dark){r=(r+240)/2;green=(green+248)/2;b=(b+240)/2;}
            g.fill(28+col,122+row,32+col,126+row,0xff000000|r<<16|green<<8|b);
        }
        text(g,"NAPOLI",37,124,0xff65857a,180);text(g,"N ↑",193,124,0xff52666b,27);
        if(minecraft.player!=null&&minecraft.level.dimension()==NapoliMap.LEVEL){
            var pos=minecraft.player.blockPosition();
            for(var p:state.places()){int dx=124+Math.round((p.position().getX()-pos.getX())/1.4f),dz=186+Math.round((p.position().getZ()-pos.getZ())/1.4f);if(dx>30&&dx<217&&dz>144&&dz<232)rounded(g,dx,dz,4,4,2,0xffaf7c6c);}
            if(state.waypoint()!=null){int dx=Math.clamp(124+Math.round((state.waypoint().getX()-pos.getX())/1.4f),31,215),dz=Math.clamp(186+Math.round((state.waypoint().getZ()-pos.getZ())/1.4f),145,232);line(g,124,186,dx,dz,0xff427ef1);rounded(g,dx-3,dz-3,7,7,3,0xffed9b3a);}
            rounded(g,119,181,11,11,5,0xffffffff);rounded(g,121,183,7,7,3,0xff347eee);
        }
        text(g,minecraft.player==null?"":minecraft.player.blockPosition().toShortString(),28,246,0xff43616d,192);
        text(g,state.goal().isEmpty()?t("phone.pick_destination").getString():state.instruction().getString(),25,267,muted(),198);
    }
    private void about(GuiGraphicsExtractor g){
        var m=model();String[] rows={m.name,m.os,"CPU · "+m.cpu,"GPU · "+m.gpu,"RAM · "+m.ram,m.storage,m.display,m.battery+" mAh",t("phone.charging").getString()+" · "+m.charging,m.cameras,"5G · Wi-Fi 7 · Bluetooth 6.0","GPS · NFC · USB-C"};
        for(int i=page*6;i<Math.min(page*6+6,rows.length);i++){int by=94+(i-page*6)*54;rounded(g,24,by,200,47,13,surface());paragraph(g,Component.literal(rows[i]),34,by+12,180,3,ink());}
        center(g,(page+1)+" / 2",124,444,muted(),80);
    }
    private void photo(){
        var mc=minecraft;mc.gui.setScreen(null);
        // Queue after the next rendered world frame, then return to the handset.
        PhonePhoto.capture(this);
    }
    private void text(GuiGraphicsExtractor g,String value,int tx,int ty,int color,int max){g.text(font,NapoliScreen.fit(font,value,max),tx,ty,color,false);}
    private void center(GuiGraphicsExtractor g,String value,int cx,int cy,int color,int max){String fit=NapoliScreen.fit(font,value,max);g.text(font,fit,cx-font.width(fit)/2,cy,color,false);}
    private void paragraph(GuiGraphicsExtractor g,Component value,int tx,int ty,int max,int rows,int color){var lines=font.split(value,max);for(int i=0;i<Math.min(rows,lines.size());i++)g.text(font,lines.get(i),tx,ty+i*10,color,false);}
    static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int color){for(int row=0;row<h;row++){int edge=0;if(row<r)edge=(int)Math.ceil(r-Math.sqrt(r*r-Math.pow(r-row-.5,2)));else if(row>=h-r)edge=(int)Math.ceil(r-Math.sqrt(r*r-Math.pow(row-(h-r)+.5,2)));g.fill(x+edge,y+row,x+w-edge,y+row+1,color);}}
    private static void line(GuiGraphicsExtractor g,int x,int y,int dx,int dy,int color){int n=Math.max(Math.abs(dx-x),Math.abs(dy-y));for(int i=0;i<=n;i++){int px=x+(dx-x)*i/Math.max(1,n),py=y+(dy-y)*i/Math.max(1,n);g.fill(px,py,px+3,py+3,color);}}
}
