package dev.napuli.client;

import dev.napuli.CompanionBoard;
import static dev.napuli.CityText.t;
import dev.napuli.Napuli;
import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keyboard-accessible searchable city menu; every entry remains a server-validated command. */
final class CompanionScreen extends Screen {
    static final KeyMapping OPEN=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.napuli.companion",InputConstants.KEY_O,KeyMapping.Category.register(Napuli.id("companion"))));
    private static final Map<String,String> FILTERS=new HashMap<>();
    private final CompanionBoard board;
    private final boolean options;
    private EditBox search;
    private String query="";
    private int page, left, top, panelWidth, rows, rowHeight, bottom, refreshTicks;
    private List<CompanionBoard.Entry> visible=List.of();
    private final List<net.minecraft.client.gui.components.AbstractWidget> rowWidgets=new ArrayList<>();
    CompanionScreen(CompanionBoard board) {super(board.title());this.board=board;this.options=false;query=FILTERS.getOrDefault(board.page(),"");}
    private CompanionScreen() {super(Component.translatable("napuli.companion.options"));board=null;options=true;}
    static void register() {
        CompanionOptions.load();
        CompanionEffectsClient.register();
        ClientPlayNetworking.registerGlobalReceiver(CompanionBoard.TYPE,(packet,ctx)->{
            var old=ctx.client().gui.screen();
            var screen=new CompanionScreen(packet);
            if(old instanceof CompanionScreen previous && previous.board!=null && previous.board.page().equals(packet.page())) {
                screen.query=previous.query;screen.page=previous.page;
            }
            ctx.client().gui.setScreen(screen);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc->{while(OPEN.consumeClick()){
            if(mc.player!=null && mc.gui.screen()==null && mc.getConnection()!=null)mc.getConnection().sendCommand("compagno");
        }if(mc.level==null)FILTERS.clear();});
    }
    @Override public boolean isPauseScreen(){return false;}
    private Button button(int x,int y,int w,String text,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
    private void command(String command){
        if(command.isEmpty())return;
        if(board!=null)FILTERS.put(board.page(),query);
        if(!command.startsWith("compagno ") || command.startsWith("compagno segui ") || command.startsWith("compagno fissa ") || command.startsWith("compagno obiettivo ") || command.startsWith("compagno invita ") || command.startsWith("compagno accetta "))onClose();
        if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand(command);
    }
    @Override protected void init(){
        panelWidth=Math.min(520,width-12);left=(width-panelWidth)/2;top=8;bottom=height-8;
        int gap=4,navWidth=(panelWidth-24)/4;
        String[] labels={t("screen.menu").getString(),t("screen.atlas").getString(),t("screen.favorites").getString(),t("screen.options").getString()};String[] commands={"compagno","compagno atlante","compagno preferiti",""};
        for(int i=0;i<4;i++){final int index=i;button(left+6+i*(navWidth+gap),top+24,navWidth,labels[i],()->{if(index==3)minecraft.gui.setScreen(new CompanionScreen());else command(commands[index]);});}
        if(options){settings();return;}
        search=new EditBox(font,left+8,top+50,panelWidth-16,20,Component.translatable("napuli.companion.search"));
        search.setMaxLength(96);search.setValue(query);search.setHint(Component.translatable("napuli.companion.search"));
        search.setResponder(value->{query=value;page=0;refreshRows();});addRenderableWidget(search);
        rowHeight=height<240?30:42;rows=Math.max(1,(bottom-top-112)/rowHeight);
        button(left+8,bottom-26,48,"←",()->{if(page>0){page--;refreshRows();}});
        button(left+60,bottom-26,48,"→",()->{if((page+1)*rows<visible.size()){page++;refreshRows();}});
        button(left+panelWidth-88,bottom-26,80,t("close").getString(),this::onClose);
        refreshRows();
    }
    private void settings(){
        var o=CompanionOptions.current;
        int x=left+8,w=panelWidth-16,y=top+54;
        String[] labels={t("settings.city",on(o.city)).getString(),t("settings.kitchen",on(o.kitchen)).getString(),t("settings.tracker",on(o.tracker)).getString(),t("settings.compact",on(o.compact)).getString(),t("settings.size",t(o.size==0?"settings.small":o.size==1?"settings.normal":"settings.large")).getString(),t("settings.notices",on(o.notices)).getString(),t("settings.light",on(o.lightPreset)).getString(),t("settings.particles",on(o.reducedParticles)).getString(),t("settings.flashes",on(o.reducedFlashes)).getString(),t("settings.motion",on(o.reducedMotion)).getString(),t("settings.quiet",on(o.quietAmbience)).getString(),t("settings.text",on(o.textAlerts)).getString()};
        rows=Math.max(1,(bottom-y-52)/24);int pages=(labels.length+rows-1)/rows;page=Math.clamp(page,0,pages-1);
        for(int i=page*rows;i<Math.min(labels.length,(page+1)*rows);i++){final int option=i;var choice=button(x,y+(i-page*rows)*24,w,labels[i],()->{
            switch(option){case 0->o.city=!o.city;case 1->o.kitchen=!o.kitchen;case 2->o.tracker=!o.tracker;case 3->o.compact=!o.compact;case 4->o.size=(o.size+1)%3;case 5->o.notices=!o.notices;case 6->o.lightPreset=!o.lightPreset;case 7->o.reducedParticles=!o.reducedParticles;case 8->o.reducedFlashes=!o.reducedFlashes;case 9->o.reducedMotion=!o.reducedMotion;case 10->o.quietAmbience=!o.quietAmbience;case 11->o.textAlerts=!o.textAlerts;default->{}}
            o.save();CompanionEffectsClient.apply(minecraft);rebuildWidgets();
        });if(i>=6)choice.setTooltip(Tooltip.create(t("settings.effects_help")));}
        button(x,bottom-50,48,"←",()->{page=Math.max(0,page-1);rebuildWidgets();});
        button(x+52,bottom-50,48,"→",()->{page=Math.min(pages-1,page+1);rebuildWidgets();});
        button(x,bottom-26,w,t("done").getString(),()->command("compagno"));
    }
    private static String on(boolean value){return t(value?"yes":"no").getString();}
    private void refreshRows(){
        for(var widget:rowWidgets)removeWidget(widget);rowWidgets.clear();
        visible=board.entries().stream().filter(e->CompanionBoard.matches(e,query)).toList();
        page=Math.clamp(page,0,Math.max(0,(visible.size()-1)/Math.max(1,rows)));
        for(int i=page*rows;i<Math.min(visible.size(),(page+1)*rows);i++){
            var entry=visible.get(i);int y=top+78+(i-page*rows)*rowHeight;
            int extraWidth=entry.extra().isEmpty()?0:Math.min(64,Math.max(26,font.width(extraLabel(entry.extra()))+12));
            var card=new Button(left+8,y,panelWidth-16-extraWidth,rowHeight-3,entry.title(),b->command(entry.command()),s->s.get()){
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                    int x=getX(),w=getWidth();g.fill(x,getY(),x+w,getY()+getHeight(),isHoveredOrFocused()?0xff28424b:0xff1b3039);
                    g.text(font,font.plainSubstrByWidth(entry.title().getString(),w-12),x+6,getY()+4,0xfff5eddc,false);
                    var detail=font.split(entry.detail(),w-12);
                    int count=rowHeight>=40?2:1;for(int line=0;line<Math.min(count,detail.size());line++)g.text(font,detail.get(line),x+6,getY()+15+line*10,0xffb5d3d8,false);
                }
            };
            card.setTooltip(Tooltip.create(entry.title().copy().append("\n").append(entry.detail())));
            addRenderableWidget(card);rowWidgets.add(card);
            if(extraWidth>0){var extra=button(left+panelWidth-8-extraWidth,y,extraWidth,extraLabel(entry.extra()),()->command(entry.extraCommand()));extra.setTooltip(Tooltip.create(t(board.page().equals("ricette")?"recipe.pin":board.page().equals("amici")?"group.decline":board.page().equals("diario")?"journal.action":"atlas.favorite_hint")));rowWidgets.add(extra);}
        }
    }
    private static String extraLabel(String value){return value.startsWith("napuli.ui.")?Component.translatable(value).getString():value;}
    @Override public void tick(){
        super.tick();
        if(!options && board.page().startsWith("corse/") && ++refreshTicks>=100){refreshTicks=0;if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("compagno corse "+board.page().substring(6));}
    }
    @Override public void onClose(){if(board!=null)FILTERS.put(board.page(),query);super.onClose();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        g.fill(0,0,width,height,0xb009131b);g.fill(left,top,left+panelWidth,bottom,0xff10242e);
        g.fill(left,top,left+panelWidth,top+2,0xff62c8dc);
        g.text(font,font.plainSubstrByWidth(title.getString(),panelWidth-16),left+8,top+9,0xffffdeb1,false);
        if(!options){String label=visible.isEmpty()?t("screen.empty").getString():t("screen.page",page+1,Math.max(1,(visible.size()+rows-1)/rows),visible.size()).getString();
            g.text(font,font.plainSubstrByWidth(label,Math.max(20,panelWidth-220)),left+116,bottom-20,0xffb5d3d8,false);}
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){}
}
