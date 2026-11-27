package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Real night pedestrians, timed pickpocket approach, fleeing, police arrest and player restitution. */
public final class NightlifeClientTest {
    private static int titles,ticks,phase,age;
    private static volatile boolean done;
    private static volatile String picture;
    private static NapoliResident thief,victim;
    private static final Map<UUID,net.minecraft.world.phys.Vec3> STARTS=new HashMap<>();
    public static void register(){
        if(!Boolean.getBoolean("napuli.nightlifetest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done){mc.stop();return;}
            if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){var settings=new LevelSettings("Napoli di notte",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("nightlife-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
            mc.options.pauseOnLostFocus=false;if(picture!=null){String name=picture;picture=null;Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,message->{});}
        });ServerTickEvents.END_SERVER_TICK.register(NightlifeClientTest::tick);
    }
    private static void tick(net.minecraft.server.MinecraftServer server){
        var city=server.getLevel(NapoliMap.LEVEL);if(city==null||done)return;var p=server.getPlayerList().getPlayers().stream().filter(player->player.level()==city).findFirst().orElse(null);if(p==null)return;
        try{
            if(++ticks>3000||++age>800)throw new IllegalStateException("Nightlife deadline at phase "+phase+" thief="+(thief==null?"none":thief.position())+" loot="+city.getAttachedOrElse(CityThieves.LOOT,Map.of()));
            if(ticks%100==0)Napuli.LOGGER.info("[NIGHTLIFE] phase={} age={} thief={} victim={} loot={}",phase,age,thief==null?"none":thief.blockPosition(),victim==null?"none":victim.blockPosition(),city.getAttachedOrElse(CityThieves.LOOT,Map.of()).size());
            switch(phase){
                case 0->{
                    if(age<30)return;CityLifeConfig.settings.policePatrolLimit=0;CityLifeConfig.settings.nightThieves=0;
                    DistrictLife.seed(city);var initial=NapoliCivicLife.census(city);var selected=initial.people().stream().filter(c->CityNightlife.outgoing(c,NapoliBroadcast.day(city))&&CityNightlife.closing(c,0)==22500&&!CityNightlife.nightWorker(c)).limit(12).toList();check(selected.size()==12,"There are enough real late-night residents in the expanded census");
                    clear(city);var center=new BlockPos(-339,64,94);p.setGameMode(GameType.CREATIVE);p.teleportTo(city,-332.5,65,96.5,Set.of(),90,6,true);
                    for(int x=(center.getX()-32)>>4;x<=(center.getX()+32)>>4;x++)for(int z=(center.getZ()-32)>>4;z<=(center.getZ()+32)>>4;z++)city.getChunk(x,z);
                    clock(server,19000);var points=new ArrayList<BlockPos>();for(var at:BlockPos.betweenClosed(center.offset(-15,0,-15),center.offset(15,0,15)))if(CityLivingHomes.at(at)==null&&NapoliResidentHomes.walkable(city,at)&&points.stream().noneMatch(point->point.distSqr(at)<4))points.add(at.immutable());points.sort(Comparator.comparingDouble(at->at.distSqr(center)));
                    var people=new ArrayList<NapoliCivicLife.Citizen>();int i=0;for(var c:selected){var npc=NapoliResident.create(city,c.woman()?NapoliResident.Role.CIVIC_WOMAN:NapoliResident.Role.CIVIC_MAN,points.get(i++),0);npc.setUUID(c.id());city.addFreshEntity(npc);npc.setCustomName(net.minecraft.network.chat.Component.literal(c.name()));people.add(new NapoliCivicLife.Citizen(c.id(),c.name(),c.woman(),c.birth(),100,-1,c.partner(),c.property(),c.parents(),true,c.childhoodDays()));CitySimulation.put(city,c.id(),CitySimulation.fresh(c.id()));STARTS.put(c.id(),npc.position());}
                    city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.copyOf(people)));next(1);
                }
                case 1->{
                    if(age<340)return;long moved=STARTS.entrySet().stream().filter(e->city.getEntity(e.getKey()) instanceof NapoliResident npc&&npc.position().distanceToSqr(e.getValue())>1).count();check(moved>=3,"Late-night residents keep walking after their conversations; moved="+moved);picture="notte-quartiere";next(2);
                }
                case 2->{
                    if(age<30)return;clear(city);CityLifeConfig.settings.nightThieves=1;city.removeAttached(CityPublicOrder.INCIDENTS);city.removeAttached(CityThieves.LOOT);city.removeAttached(CityThieves.COOLDOWNS);
                    thief=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,new BlockPos(-339,64,89),0);thief.setCustomName(net.minecraft.network.chat.Component.literal("Borseggiatore prova"));victim=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_WOMAN,new BlockPos(-339,64,95),0);victim.setNoAi(true);victim.setYRot(0);victim.setYHeadRot(0);
                    city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(profile(city,thief,"Borseggiatore prova"),profile(city,victim,"Passante prova"))));city.setAttached(CityThieves.MEMBERS,Set.of(thief.getUUID().toString()));next(3);
                }
                case 3->{
                    if(city.getAttachedOrElse(CityThieves.LOOT,Map.of()).isEmpty())return;
                    check(age>=80,"The approach includes an actual four-second contact, never an instant remote debit");check(NapoliCivicLife.profile(city,victim.getUUID()).balance()==92,"The NPC victim loses exactly the reserved eight euros");check(!CityPublicOrder.jailed(city,thief.getUUID()),"No officer teleports into the scene");STARTS.put(thief.getUUID(),thief.position());picture="notte-borseggio";next(4);
                }
                case 4->{
                    if(age<50)return;check(thief.position().distanceToSqr(STARTS.get(thief.getUUID()))>1,"The thief physically flees with the money");
                    var at=CityJourneys.spawn(city,thief.blockPosition().east(2));check(at!=null,"A nearby officer can stand on a walkable street");NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,at,90);next(5);
                }
                case 5->{
                    if(!CityPublicOrder.jailed(city,thief.getUUID()))return;
                    check(NapoliCivicLife.profile(city,victim.getUUID()).balance()==100&&city.getAttachedOrElse(CityThieves.LOOT,Map.of()).isEmpty(),"The actual arrest returns the stolen money once");
                    p.setGameMode(GameType.SURVIVAL);p.teleportTo(city,-338.5,64,156.5,Set.of(),0,0,true);NapoliStreet.credit(p,100-NapoliStreet.cash(p));
                    thief=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,new BlockPos(-339,64,151),0);var census=NapoliCivicLife.census(city);var people=new ArrayList<>(census.people());people.add(profile(city,thief,"Borseggiatore del giocatore"));city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(people)));city.setAttached(CityThieves.MEMBERS,Set.of(thief.getUUID().toString()));next(6);
                }
                case 6->{
                    if(city.getAttachedOrElse(CityThieves.LOOT,Map.of()).values().stream().noneMatch(l->l.victim().equals(p.getUUID())))return;
                    check(age>=80&&NapoliStreet.cash(p)==92&&!Carabinieri.isWanted(p),"The survival player's timed theft is bounded and does not make the victim wanted");
                    var at=CityJourneys.spawn(city,thief.blockPosition().east(1));check(at!=null,"The player can catch up with the suspect");p.teleportTo(city,at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),90,0,true);
                    check(CityThieves.interact(thief,p)&&NapoliStreet.cash(p)==100,"Catching the thief with right-click recovers the player's actual money");CityThieves.recover(city,thief.getUUID());check(NapoliStreet.cash(p)==100,"Repeating recovery cannot mint another refund");picture="notte-recupero";next(7);
                }
                case 7->{if(age<40)return;Napuli.LOGGER.info("[NIGHTLIFE] ALL PASSED: midnight pedestrians, timed NPC and player theft, physical escape, police arrest, exact restitution and no duplicate refunds");done=true;}
            }
        }catch(Throwable failure){Napuli.LOGGER.error("[NIGHTLIFE] FAILED",failure);done=true;}
    }
    private static void clear(ServerLevel city){var actors=new ArrayList<NapoliResident>();for(var entity:city.getAllEntities())if(entity instanceof NapoliResident npc&&(NapoliCivicLife.profile(city,npc.getUUID())!=null||npc.role()==NapoliResident.Role.CARABINIERE))actors.add(npc);actors.forEach(NapoliResident::discard);}
    private static NapoliCivicLife.Citizen profile(ServerLevel city,NapoliResident npc,String name){return new NapoliCivicLife.Citizen(npc.getUUID(),name,false,NapoliBroadcast.day(city)-30L*CityLifeConfig.settings.daysPerYear,100,-1,"","public_0","",true);}
    private static void clock(net.minecraft.server.MinecraftServer server,int value){server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set "+value);}
    private static void next(int value){phase=value;age=0;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private NightlifeClientTest(){}
}
