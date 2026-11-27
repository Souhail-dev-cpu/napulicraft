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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.*;
import static dev.napuli.client.CompanionTheme.*;

/** Responsive city hub; every action still uses the server's existing validation. */
public final class CompanionScreen extends NapoliScreen {
    static final KeyMapping OPEN=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.napuli.companion",InputConstants.KEY_O,KeyMapping.Category.register(Napuli.id("companion"))));
    private static final Map<String,String> FILTERS=new HashMap<>();
    private enum Category { FEATURED, CITY, DAILY, SOCIAL, MONEY, ALL }
    private final CompanionBoard board;
    private final CompanionScreen returnTo;
    private final boolean options;
    private final Set<Button> selected=new HashSet<>();
    private final Map<Category,Button> categories=new EnumMap<>(Category.class);
    private final List<AbstractWidget> rowWidgets=new ArrayList<>();
    private EditBox search;
    private Button previous,next,clear;
    private String query="";
    private Category category=Category.FEATURED;
    private int page,rows,columns,rowHeight,contentTop,refreshTicks,optionCount,mainLeft,mainWidth,searchTop;
    private boolean wide;
    private List<CompanionBoard.Entry> visible=List.of();

    CompanionScreen(CompanionBoard board){super(board.title());this.board=board;options=false;returnTo=null;query=FILTERS.getOrDefault(board.page(),"");}
    private CompanionScreen(CompanionScreen parent){super(Component.translatable("napuli.companion.options"));board=null;options=true;returnTo=parent;}
    private boolean home(){return !options&&board.page().equals("menu");}
    static void register(){
        CompanionOptions.load();CompanionEffectsClient.register();
        ClientPlayNetworking.registerGlobalReceiver(CompanionBoard.TYPE,(packet,ctx)->{
            var old=ctx.client().gui.screen();var screen=new CompanionScreen(packet);
            if(old instanceof CompanionScreen previous&&previous.board!=null&&previous.board.page().equals(packet.page())){screen.query=previous.query;screen.page=previous.page;screen.category=previous.category;}
            ctx.client().gui.setScreen(screen);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc->{while(OPEN.consumeClick())openMenu(mc);if(mc.level==null)FILTERS.clear();});
    }
    public static boolean handleOpenKey(Minecraft mc,InputConstants.Key key){return OPEN.matches(key)&&openMenu(mc);}
    private static boolean openMenu(Minecraft mc){
        if(mc.player==null||mc.gui.screen()!=null||mc.gui.overlay()!=null||mc.options.keyDebugModifier.isDown()||mc.getConnection()==null)return false;
        mc.getConnection().sendCommand("compagno");return true;
    }
    private void command(String command){
        if(command.isEmpty())return;if(board!=null)FILTERS.put(board.page(),query);
        if((!command.startsWith("compagno ")&&!command.equals("compagno"))||command.startsWith("compagno segui ")||command.startsWith("compagno fissa ")||command.startsWith("compagno obiettivo ")||command.startsWith("compagno invita ")||command.startsWith("compagno accetta "))super.onClose();
        if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand(command);
    }
    @Override protected Component subtitle(){return t(options?"screen.options_help":home()?"hub.welcome":"screen.browse_help");}
    @Override protected void init(){
        rowWidgets.clear();selected.clear();categories.clear();search=null;clear=null;
        layout(780,height-16);wide=panelWidth>=500&&height>=280;
        mainLeft=left+(wide?142:12);mainWidth=panelWidth-(wide?156:24);
        String[] league=leagueRoute();mainNavigation();
        if(league.length==3){leagueNavigation(league);contentTop=top+(wide?109:112);}
        else if(options)contentTop=top+(wide?77:76);
        else{
            searchTop=top+(wide?53:58);createSearch();
            if(home())categoryNavigation();
            contentTop=top+(home()?(wide?128:114):(wide?103:101));
        }
        rowHeight=options?(height<280?35:46):home()?(height<280?24:panelHeight>=370?68:56):(height<280?28:44);
        columns=home()&&mainWidth>=320?2:1;
        rows=Math.max(1,(bottom-36-contentTop)/rowHeight);
        previous=hubButton(mainLeft,bottom-28,22,20,Component.literal("←"),()->changePage(-1));previous.setTooltip(Tooltip.create(t("screen.previous")));
        next=hubButton(mainLeft+28,bottom-28,22,20,Component.literal("→"),()->changePage(1));next.setTooltip(Tooltip.create(t("screen.next")));
        if(options||!home())hubButton(left+panelWidth-150,bottom-28,66,20,t("screen.back"),this::back);
        hubButton(left+panelWidth-78,bottom-28,66,20,t("close"),this::onClose);
        if(options)settings();else refreshRows();
    }
    private void createSearch(){
        search=new EditBox(font,mainLeft+29,searchTop+7,Math.max(1,mainWidth-62),16,Component.translatable("napuli.companion.search"));
        search.setBordered(false);search.setTextColor(INK);search.setMaxLength(96);search.setValue(query);
        search.setHint(t(home()?"hub.search_services":"hub.search_places"));
        search.setResponder(value->{query=value;page=0;refreshRows();});addRenderableWidget(search);
        clear=hubButton(mainLeft+mainWidth-24,searchTop+3,20,20,Component.literal("×"),()->{search.setValue("");setFocused(search);});clear.setTooltip(Tooltip.create(t("screen.clear_search")));
    }
    private void mainNavigation(){
        String[] keys={"menu","atlas","favorites","options"},commands={"compagno","compagno atlante","compagno preferiti",""};
        Icon[] icons={Icon.CITY,Icon.MAP,Icon.STAR,Icon.SETTINGS};
        int width=wide?114:(mainWidth-12)/4;
        for(int i=0;i<4;i++){
            final int at=i;int x=wide?left+12:mainLeft+i*(width+4),y=wide?top+70+i*36:top+30;
            var nav=control(x,y,width,wide?30:20,t("screen."+keys[i]),wide?icons[i]:null,()->{if(at==3)minecraft.gui.setScreen(new CompanionScreen(this));else command(commands[at]);});
            boolean active=options?i==3:i==0&&home()||i==1&&board.page().equals("atlante")||i==2&&board.page().equals("preferiti");
            if(active){selected.add(nav);nav.active=false;}
        }
    }
    private void categoryNavigation(){
        int width=(mainWidth-15)/6;
        for(var value:Category.values()){
            var button=hubButton(mainLeft+value.ordinal()*(width+3),top+86,width,20,t("hub.category."+value.name().toLowerCase(Locale.ROOT)),()->{
                category=value;page=0;if(search!=null&&!query.isEmpty())search.setValue("");refreshRows();
            });
            button.setTooltip(Tooltip.create(t("hub.section."+value.name().toLowerCase(Locale.ROOT))));categories.put(value,button);
        }
    }
    private static boolean belongs(CompanionBoard.Entry entry,Category category){
        String c=entry.command();
        if(category==Category.ALL)return true;
        if(category==Category.FEATURED)return c.isEmpty()||List.of("compagno atlante","compagno carriera","compagno case","compagno trasporti","negozio","compagno diario").contains(c);
        return category==switch(CompanionTheme.icon(c)){
            case MAP,STAR,TRANSIT,SCOOTER,SHOP->Category.CITY;
            case WORK,HOME,BOOK,JOURNAL,HELP,TARGET,CITY,SETTINGS,SEARCH->Category.DAILY;
            case GROUP,TRADE->Category.SOCIAL;
            case WALLET,CHART->Category.MONEY;
        };
    }
    private String[] leagueRoute(){return !options&&board.page().startsWith("campionato/")?board.page().split("/"):new String[0];}
    private void leagueNavigation(String[] route){
        String[] sections={"Calendario","Classifica","Contratti"};int w=(mainWidth-8)/3;
        for(int i=0;i<sections.length;i++){String name=sections[i],id=name.toLowerCase(Locale.ROOT);var tab=hubButton(mainLeft+i*(w+4),top+(wide?53:58),w,20,Component.literal(name),()->command("calcio campionato "+route[1]+" "+id));if(route[2].equals(id)){selected.add(tab);tab.active=false;}}
        w=(mainWidth-12)/4;
        for(int i=0;i<4;i++){String division=String.valueOf((char)('D'-i));var tab=hubButton(mainLeft+i*(w+4),top+(wide?80:84),w,20,Component.literal("Serie "+division),()->command("calcio campionato "+division+" "+route[2]));if(route[1].equals(division)){selected.add(tab);tab.active=false;}}
    }
    private void back(){
        if(options&&returnTo!=null){minecraft.gui.setScreen(returnTo);return;}
        String id=board.page();String target=id.startsWith("preventivo/")?"compagno casa "+id.substring(11):id.startsWith("casa/")?"compagno case":id.startsWith("luogo/")?"compagno atlante":id.startsWith("ricetta/")?"compagno ricette":id.startsWith("corse/")?"compagno trasporti":id.startsWith("permessi/")?"compagno casa "+id.substring(9):id.startsWith("scambio")?"compagno scambi":id.startsWith("cooperativa/")?"compagno cooperative":id.equals("parcheggi")||id.equals("recupero")?"compagno motorini":"compagno";command(target);
    }
    private int pageSize(){return rows*columns;}
    private int pageCount(){return Math.max(1,((options?optionCount:visible.size())+pageSize()-1)/pageSize());}
    private void updatePaging(){previous.active=page>0;next.active=page+1<pageCount();}
    private void changePage(int direction){int at=Math.clamp(page+direction,0,pageCount()-1);if(at==page)return;page=at;if(options)rebuildWidgets();else refreshRows();}
    private void settings(){
        var o=CompanionOptions.current;
        Component[] labels={t("settings.city",on(o.city)),t("settings.kitchen",on(o.kitchen)),t("settings.tracker",on(o.tracker)),t("settings.compact",on(o.compact)),t("settings.size",t(o.size==0?"settings.small":o.size==1?"settings.normal":"settings.large")),t("settings.notices",on(o.notices)),t("settings.light",on(o.lightPreset)),t("settings.particles",on(o.reducedParticles)),t("settings.flashes",on(o.reducedFlashes)),t("settings.motion",on(o.reducedMotion)),t("settings.quiet",on(o.quietAmbience)),t("settings.text",on(o.textAlerts))};
        boolean[] values={o.city,o.kitchen,o.tracker,o.compact,true,o.notices,o.lightPreset,o.reducedParticles,o.reducedFlashes,o.reducedMotion,o.quietAmbience,o.textAlerts};
        optionCount=labels.length;page=Math.clamp(page,0,pageCount()-1);
        for(int i=page*rows;i<Math.min(labels.length,(page+1)*rows);i++){
            final int at=i;var label=labels[i];var help=t("settings.help."+i);String value=i==4?t(o.size==0?"settings.small":o.size==1?"settings.normal":"settings.large").getString():on(values[i]).getString();boolean enabled=values[i];
            var choice=new Button(mainLeft,contentTop+(i-page*rows)*rowHeight,mainWidth,rowHeight-6,label,b->{
                switch(at){case 0->o.city=!o.city;case 1->o.kitchen=!o.kitchen;case 2->o.tracker=!o.tracker;case 3->o.compact=!o.compact;case 4->o.size=(o.size+1)%3;case 5->o.notices=!o.notices;case 6->o.lightPreset=!o.lightPreset;case 7->o.reducedParticles=!o.reducedParticles;case 8->o.reducedFlashes=!o.reducedFlashes;case 9->o.reducedMotion=!o.reducedMotion;case 10->o.quietAmbience=!o.quietAmbience;case 11->o.textAlerts=!o.textAlerts;default->{}}
                o.save();CompanionEffectsClient.apply(minecraft);rebuildWidgets();
            },s->s.get()){
                @Override protected MutableComponent createNarrationMessage(){return label.copy().append(". ").append(help);}
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                    surface(g,getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused()?CompanionTheme.HOVER:CARD,isFocused()?SEA:EDGE);
                    int badge=Math.max(32,font.width(value)+16),by=getY()+(getHeight()-16)/2;
                    rounded(g,getRight()-badge-10,by,badge,16,5,enabled?0xff244b47:NAV);
                    g.text(font,value,getRight()-badge-10+(badge-font.width(value))/2,by+4,enabled?SEA:SOFT,false);
                    String name=label.getString();int colon=name.lastIndexOf(':');if(colon>0)name=name.substring(0,colon);
                    g.text(font,fit(font,name,getWidth()-badge-34),getX()+12,getY()+(getHeight()<36?(getHeight()-8)/2:7),INK,false);
                    if(getHeight()>=36)g.text(font,fit(font,help.getString(),getWidth()-badge-34),getX()+12,getY()+23,SOFT,false);
                }
            };choice.setTooltip(Tooltip.create(help));addRenderableWidget(choice);
        }updatePaging();
    }
    private static Component on(boolean value){return t(value?"yes":"no");}
    private void refreshRows(){
        for(var widget:rowWidgets)removeWidget(widget);rowWidgets.clear();
        boolean league=leagueRoute().length==3;
        visible=board.entries().stream().filter(e->league?!e.command().startsWith("calcio campionato ")||e.title().getString().equals("Scegli il tuo club"):CompanionBoard.matches(e,query)&&(!home()||!query.isBlank()||belongs(e,category))).toList();
        for(var e:categories.entrySet()){selected.remove(e.getValue());e.getValue().active=true;if(query.isBlank()&&e.getKey()==category){selected.add(e.getValue());e.getValue().active=false;}}
        page=Math.clamp(page,0,pageCount()-1);int cellWidth=(mainWidth-(columns-1)*8)/columns;
        for(int i=page*pageSize();i<Math.min(visible.size(),(page+1)*pageSize());i++){
            var entry=visible.get(i);int cell=i-page*pageSize(),x=mainLeft+cell%columns*(cellWidth+8),y=contentTop+cell/columns*rowHeight;
            int extraWidth=entry.extra().isEmpty()?0:Math.clamp(font.width(extraLabel(entry.extra()).getString())+16,30,76),gap=extraWidth>0?6:0;
            rowWidgets.add(serviceCard(x,y,cellWidth-extraWidth-gap,rowHeight-6,entry));
            if(extraWidth>0){var extra=hubButton(x+cellWidth-extraWidth,y,extraWidth,rowHeight-6,extraLabel(entry.extra()),()->command(entry.extraCommand()));extra.setTooltip(Tooltip.create(t(board.page().equals("ricette")?"recipe.pin":board.page().equals("amici")?"group.decline":board.page().equals("diario")?"journal.action":"atlas.favorite_hint")));rowWidgets.add(extra);}
        }
        if(visible.isEmpty()){
            boolean filtered=!query.isBlank();int y=Math.min(contentTop+(height<280?25:48),bottom-60);
            rowWidgets.add(hubButton(mainLeft+8,y,mainWidth-16,20,t(filtered?"screen.clear_search":"screen.open_atlas"),()->{if(filtered)search.setValue("");else command("compagno atlante");}));
        }
        if(clear!=null)clear.active=!query.isEmpty();updatePaging();
    }
    private Button serviceCard(int x,int y,int w,int h,CompanionBoard.Entry entry){
        boolean actionable=!entry.command().isEmpty();Icon glyph=CompanionTheme.icon(entry.command());int tint=tint(glyph);
        var card=new Button(x,y,w,h,entry.title(),b->{if(actionable)command(entry.command());},s->s.get()){
            @Override protected MutableComponent createNarrationMessage(){return entry.title().copy().append(". ").append(entry.detail());}
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                int xx=getX(),yy=getY(),ww=getWidth(),hh=getHeight();boolean hover=isHoveredOrFocused();
                surface(g,xx,yy,ww,hh,hover?CompanionTheme.HOVER:CARD,hover?SEA:EDGE);
                if(hh<34){
                    if(hh<24){g.pose().pushMatrix();g.pose().translate(xx+8,yy+(hh-16)/2);g.pose().scale(.7f);icon(g,glyph,0,0,tint);g.pose().popMatrix();}
                    else icon(g,glyph,xx+7,yy+(hh-22)/2,tint);
                    g.text(font,fit(font,entry.title().getString(),ww-53),xx+35,yy+(hh-8)/2,INK,false);
                }
                else if(!home()){
                    rounded(g,xx+8,yy+6,27,26,5,0xff10202e);icon(g,glyph,xx+11,yy+8,tint);
                    g.text(font,fit(font,entry.title().getString(),ww-60),xx+43,yy+6,INK,false);
                    g.text(font,fit(font,entry.detail().getString(),ww-60),xx+43,yy+23,SOFT,false);
                }
                else{
                    rounded(g,xx+8,yy+7,28,26,5,0xff10202e);icon(g,glyph,xx+12,yy+9,tint);
                    g.text(font,fit(font,entry.title().getString(),ww-60),xx+43,yy+15,INK,false);
                    var lines=font.split(entry.detail(),Math.max(1,ww-22));int count=Math.max(0,(hh-36)/10);
                    for(int i=0;i<Math.min(count,lines.size());i++)g.text(font,lines.get(i),xx+11,yy+34+i*10,SOFT,false);
                }
                if(actionable)g.text(font,"›",xx+ww-12,yy+(hh<34?(hh-8)/2:15),hover?SEA:DIM,false);
            }
        };card.setTooltip(Tooltip.create(entry.title().copy().append("\n").append(entry.detail())));card.setTooltipDelay(java.time.Duration.ofMillis(450));return addRenderableWidget(card);
    }
    private Button hubButton(int x,int y,int w,int h,Component label,Runnable action){return control(x,y,w,h,label,null,action);}
    private Button control(int x,int y,int w,int h,Component label,Icon icon,Runnable action){
        var button=new Button(x,y,w,h,label,b->action.run(),s->s.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
                int xx=getX(),yy=getY();boolean chosen=selected.contains(this),hover=active&&isHoveredOrFocused();
                surface(g,xx,yy,getWidth(),getHeight(),chosen?0xff234642:hover?CompanionTheme.HOVER:NAV,chosen?0xff49776f:hover?SEA:EDGE);
                int color=chosen?SEA:active?INK:DIM;int pad=icon==null?10:39;
                String text=fit(font,getMessage().getString(),getWidth()-pad-(icon==null?0:7));
                if(icon!=null)CompanionTheme.icon(g,icon,xx+9,yy+(getHeight()-22)/2,color);
                g.text(font,text,icon==null?xx+(getWidth()-font.width(text))/2:xx+36,yy+(getHeight()-8)/2,color,false);
            }
        };button.setTooltip(Tooltip.create(label));button.setTooltipDelay(java.time.Duration.ofMillis(450));return addRenderableWidget(button);
    }
    private static Component extraLabel(String value){return value.startsWith("napuli.ui.")?Component.translatable(value):Component.literal(value);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(x>=mainLeft&&x<mainLeft+mainWidth&&y>=contentTop&&y<bottom-34&&vertical!=0){changePage(vertical<0?1:-1);return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(KeyEvent event){
        if(search!=null&&event.key()==InputConstants.KEY_F&&event.hasControlDown()){setFocused(search);search.setCursorPosition(query.length());search.setHighlightPos(0);return true;}
        if(search!=null&&search.isFocused()&&(event.key()==InputConstants.KEY_RETURN||event.key()==InputConstants.KEY_NUMPADENTER)&&visible.size()==1&&!visible.getFirst().command().isEmpty()){command(visible.getFirst().command());return true;}
        if(event.key()==InputConstants.KEY_PAGEUP||event.key()==InputConstants.KEY_PAGEDOWN){changePage(event.key()==InputConstants.KEY_PAGEUP?-1:1);return true;}
        if(event.isEscape()&&search!=null&&!query.isEmpty()&&search.isFocused()){search.setValue("");return true;}
        if((search==null||!search.isFocused())&&OPEN.matches(InputConstants.getKey(event))){onClose();return true;}
        return super.keyPressed(event);
    }
    @Override public void tick(){super.tick();if(!options&&board.page().startsWith("corse/")&&++refreshTicks>=100){refreshTicks=0;if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("compagno corse "+board.page().substring(6));}}
    @Override public void onClose(){if(board!=null)FILTERS.put(board.page(),query);super.onClose();}
    @Override protected void extractFrame(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xe30a111b);rounded(g,left+3,top+5,panelWidth,bottom-top,9,0x70000000);
        rounded(g,left,top,panelWidth,panelHeight,8,EDGE);rounded(g,left+1,top+1,panelWidth-2,panelHeight-2,7,BACK);
        if(wide){
            rounded(g,left+1,top+1,132,panelHeight-2,7,NAV);g.fill(left+132,top+12,left+133,bottom-12,EDGE);
            icon(g,Icon.CITY,left+18,top+17,SEA);g.text(font,"NAPULI",left+47,top+20,INK,false);g.text(font,"CRAFT",left+47,top+32,SAND,false);
            g.text(font,t("hub.your_city"),left+16,top+56,DIM,false);
            if(panelHeight>=310){
                int by=bottom-101;surface(g,left+12,by,112,58,0xff172739,EDGE);
                String name=minecraft.player==null?t("hub.welcome_short").getString():minecraft.player.getName().getString();
                g.text(font,fit(font,name,92),left+22,by+10,INK,false);var hud=HudClient.state();
                g.text(font,hud==null?"Napoli":"€ "+hud.cash(),left+22,by+27,SAND,false);
                if(hud!=null)g.text(font,dev.napuli.Hud.clock(hud.cityTime()),left+22,by+42,SOFT,false);
            }
            g.text(font,fit(font,t("hub.keyboard").getString(),110),left+14,bottom-30,DIM,false);
            g.text(font,fit(font,t("hub.keyboard_close").getString(),110),left+14,bottom-17,DIM,false);
        }
        float scale=wide?1.35f:1.1f;CompanionTheme.large(g,font,title.getString(),mainLeft,top+12,mainWidth,scale,INK);
        if(wide)g.text(font,fit(font,subtitle().getString(),mainWidth),mainLeft,top+33,SOFT,false);
        if(search!=null){surface(g,mainLeft,searchTop,mainWidth,26,NAV,search.isFocused()?SEA:EDGE);icon(g,Icon.SEARCH,mainLeft+8,searchTop+4,DIM);}
        g.fill(mainLeft,bottom-36,left+panelWidth-12,bottom-35,EDGE);
    }
    @Override protected void extractPanel(GuiGraphicsExtractor g,int mx,int my,float delta){
        boolean hasBack=options||!home();g.text(font,fit(font,t("screen.pagination",page+1,pageCount()).getString(),mainWidth-(hasBack?216:144)),mainLeft+59,bottom-22,SOFT,false);
        if(leagueRoute().length==0){
            if(options)g.text(font,fit(font,t("hub.settings_section").getString(),mainWidth),mainLeft,contentTop-14,SOFT,false);
            else if(wide||!home()){
                Component section=home()?t(query.isBlank()?"hub.section."+category.name().toLowerCase(Locale.ROOT):"hub.search_results"):t("hub.results",visible.size());
                g.text(font,fit(font,section.getString(),mainWidth-60),mainLeft,contentTop-14,SOFT,false);
                if(home()){String count=String.valueOf(visible.size());g.text(font,count,mainLeft+mainWidth-font.width(count),contentTop-14,DIM,false);}
            }
        }
        if(!options&&visible.isEmpty())paragraph(g,t(query.isBlank()?"screen.no_entries":"screen.empty"),mainLeft+8,contentTop+7,mainWidth-16,2,SOFT);
    }
}
