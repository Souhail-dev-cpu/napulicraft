package dev.napuli;

import java.util.*;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** A disposable real client verifies density, walking, native text submission and conversation limits. */
public final class NpcLifeClientTest {
    private static int titles,ticks,phase,phaseTicks,uiTicks;
    private static volatile boolean done;
    private static volatile int ui;
    private static NapoliResident speaker;
    private static final Map<UUID,net.minecraft.world.phys.Vec3> STARTS=new HashMap<>();
    public static void register(){
        if(!Boolean.getBoolean("napuli.npclifetest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done){mc.stop();return;}
            try{
                if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){
                    var settings=new LevelSettings("NPC e conversazioni",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("npc-life-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());
                }return;}
                mc.options.pauseOnLostFocus=false;
                if(ui==0){uiTicks=0;return;}if(++uiTicks<25)return;
                if(ui==1&&mc.gui.screen()!=null){
                    var edit=descendants(mc.gui.screen()).filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElse(null);if(edit==null)return;
                    edit.setValue("Che lavoro fai?");shot(mc,"npc-dialogo");
                    var button=descendants(mc.gui.screen()).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).filter(b->b.getMessage().getString().equals("Invia il messaggio")).findFirst().orElseThrow();
                    ui=0;button.onPress(new KeyEvent(257,0,0));
                }else if(ui==2&&mc.gui.screen()!=null){
                    var button=descendants(mc.gui.screen()).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).filter(b->b.getMessage().getString().equals("Raccontami di più")).findFirst().orElse(null);if(button==null)return;
                    shot(mc,"npc-risposta");ui=0;button.onPress(new KeyEvent(257,0,0));
                }else if(ui==3&&mc.gui.screen()==null){
                    check(dev.napuli.client.NpcSpeechBubbles.hasBubble(speaker.getUUID(),net.minecraft.network.chat.Component.literal("A presto, "+mc.player.getName().getString()+"! Ci vediamo in giro.")),"The latest direct reply replaces older speech immediately");
                    shot(mc,"npc-popolazione");ui=0;
                }
            }catch(Throwable failure){fail(failure);}
        });
        ServerTickEvents.END_SERVER_TICK.register(NpcLifeClientTest::serverTick);
    }
    private static void serverTick(net.minecraft.server.MinecraftServer server){
        var city=server.getLevel(NapoliMap.LEVEL);if(city==null||done)return;var p=server.getPlayerList().getPlayers().stream().filter(player->player.level()==city).findFirst().orElse(null);if(p==null)return;
        try{
            if(++ticks>1800||++phaseTicks>500)throw new IllegalStateException("NPC test timeout at phase "+phase+" ui="+ui);
            if(ticks%100==0)Napuli.LOGGER.info("[NPCLIFE] phase={} residents={} active={}",phase,NapoliCivicLife.census(city).people().size(),actors(city).size());
            switch(phase){
                case 0->{
                    if(phaseTicks<30)return;
                    DistrictLife.seed(city);check(NapoliCivicLife.census(city).people().size()>=576,"The default city has at least 576 real census residents; actual="+NapoliCivicLife.census(city).people().size());
                    for(var npc:actors(city))CityJourneys.withdraw(city,npc);
                    var center=new BlockPos(-339,64,94);p.setGameMode(GameType.CREATIVE);p.teleportTo(city,-338.5,64,95.5,Set.of(),90,5,true);
                    for(int x=(center.getX()-32)>>4;x<=(center.getX()+32)>>4;x++)for(int z=(center.getZ()-32)>>4;z<=(center.getZ()+32)>>4;z++)city.getChunk(x,z);
                    CityLifeConfig.settings.visibleResidentsPerPlayer=24;CityLifeConfig.settings.visibleResidentsMaximum=24;
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 7000");
                    var points=new ArrayList<BlockPos>();for(var at:BlockPos.betweenClosed(center.offset(-23,0,-23),center.offset(23,0,23)))if(CityLivingHomes.at(at)==null&&NapoliResidentHomes.walkable(city,at)&&points.stream().noneMatch(point->point.distSqr(at)<4))points.add(at.immutable());
                    points.sort(Comparator.comparingDouble(point->point.distSqr(center)));check(points.size()>=36,"The actual street provides separate walkable arrival points");
                    var census=NapoliCivicLife.census(city);var people=new ArrayList<>(census.people());int staged=0;
                    for(int i=0;i<people.size()&&staged<36;i++){var c=people.get(i);if(c.age(NapoliBroadcast.day(city))<18)continue;
                        var changed=new NapoliCivicLife.Citizen(c.id(),c.name(),c.woman(),c.birth(),c.balance(),-1,c.partner(),c.property(),c.parents(),false,c.childhoodDays());people.set(i,changed);
                        CitySimulation.put(city,c.id(),CitySimulation.fresh(c.id()).withNeeds(new CitySimulation.Needs(95,95,95,75,75,95),8,city.getGameTime(),0,0));
                        var point=points.get(staged++);CityJourneys.put(city,c.id(),CityJourneys.plan(point,point,city.getGameTime(),95));
                    }
                    city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(people)));
                    for(int i=0;i<5;i++)NapoliCivicLife.materializeNearby(city);
                    var actors=actors(city);check(actors.size()==24,"The physical population reaches and respects the configured visible limit");
                    for(var npc:actors)STARTS.put(npc.getUUID(),npc.position());next(1);
                }
                case 1->{
                    // Newly met neighbors can stop for a twelve-second chat before resuming their route.
                    if(phaseTicks<340)return;var actors=actors(city);
                    long moved=actors.stream().filter(npc->STARTS.containsKey(npc.getUUID())&&npc.position().distanceToSqr(STARTS.get(npc.getUUID()))>1).count();
                    if(moved<4)for(var npc:actors.stream().limit(4).toList()){var c=NapoliCivicLife.profile(city,npc.getUUID());Napuli.LOGGER.info("[NPCLIFE] movement diagnostic npc={} ticks={} at={} action={} wanted={} nav={} player={} distance={}",c.name(),npc.tickCount,npc.position(),npc.activity(),CitySimulation.activity(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city)),npc.getNavigation().getPath(),p.position(),p.distanceToSqr(npc));}
                    check(moved>=4,"Several actual residents walk independently through the street; moved="+moved);
                    check(actors.size()<=24,"Repeated updates do not exceed the visible population ceiling");
                    speaker=actors.stream().filter(npc->{var c=NapoliCivicLife.profile(city,npc.getUUID());return CityConversation.canPause(city,npc,c,CitySimulation.activity(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city)));}).findFirst().orElseThrow();
                    // Camera placement is test setup; the resident's position remains controlled by its own AI.
                    var approach=CityJourneys.spawn(city,speaker.blockPosition().east(2));check(approach!=null,"There is a real reachable place for the visitor");
                    p.teleportTo(city,approach.getX()+.5,approach.getY(),approach.getZ()+.5,Set.of(),90,5,true);
                    check(CityConversation.talk(p,speaker.getUUID(),"Come stai?")==1,"A nearby resident accepts the initial question");ui=1;next(2);
                }
                case 2->{
                    var memory=CityConversation.memory(city,speaker.getUUID(),p.getUUID());if(memory==null||CityConversation.previous(memory)!=CityConversation.Topic.WORK)return;
                    check(memory.turns()==2,"Typing and pressing the native send button reaches the server exactly once");ui=2;next(3);
                }
                case 3->{
                    var memory=CityConversation.memory(city,speaker.getUUID(),p.getUUID());if(memory.turns()<3)return;
                    check(CityConversation.previous(memory)==CityConversation.Topic.WORK,"The follow-up button retains the previous topic");
                    var original=p.position();p.teleportTo(city,original.x+20,original.y,original.z,Set.of(),90,5,true);
                    check(CityConversation.talk(p,speaker.getUUID(),"Parlami della tua famiglia")==0&&CityConversation.memory(city,speaker.getUUID(),p.getUUID()).turns()==3,"A distant player cannot advance the conversation");
                    p.teleportTo(city,original.x,original.y,original.z,Set.of(),90,5,true);
                    check(CityConversation.talk(p,speaker.getUUID(),"arrivederci")==1,"The goodbye action ends the exchange");ui=3;next(4);
                }
                case 4->{if(ui!=0||phaseTicks<40)return;Napuli.LOGGER.info("[NPCLIFE] ALL PASSED: 576 residents, 24 bounded physical NPCs, autonomous walking, native typed input, contextual follow-up and distance checks");done=true;}
            }
        }catch(Throwable failure){fail(failure);}
    }
    private static List<NapoliResident> actors(ServerLevel city){var list=new ArrayList<NapoliResident>();for(var entity:city.getAllEntities())if(entity instanceof NapoliResident npc&&NapoliCivicLife.profile(city,npc.getUUID())!=null)list.add(npc);return list;}
    private static Stream<GuiEventListener> descendants(GuiEventListener node){return node instanceof ContainerEventHandler container?Stream.concat(Stream.of(node),container.children().stream().flatMap(NpcLifeClientTest::descendants)):Stream.of(node);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,message->{});}
    private static void next(int value){phase=value;phaseTicks=0;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void fail(Throwable failure){Napuli.LOGGER.error("[NPCLIFE] FAILED",failure);done=true;}
    private NpcLifeClientTest(){}
}
