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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** Drives the real resident AI and the actual public fleet in an isolated graphical world. */
public final class MobilityClientTest {
    private static int titles,ticks,phase,age,mode;
    private static volatile boolean done;
    private static volatile String picture;
    private static NapoliResident actor;
    private static Entity vehicle;
    private static Vec3 start;
    private static BlockPos goal;
    private static CityNetwork.Leg leg;
    private static final String[] PUBLIC={"B5","T1","M6"};
    public static void register(){
        if(!Boolean.getBoolean("napuli.mobilitytest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done){mc.stop();return;}
            if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){var settings=new LevelSettings("Residenti in viaggio",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("mobility-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
            mc.options.pauseOnLostFocus=false;if(picture!=null){var name=picture;picture=null;Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,message->{});}
        });
        ServerTickEvents.END_SERVER_TICK.register(MobilityClientTest::tick);
    }
    private static void tick(net.minecraft.server.MinecraftServer server){
        var city=server.getLevel(NapoliMap.LEVEL);if(city==null||done)return;var player=server.getPlayerList().getPlayers().stream().filter(p->p.level()==city).findFirst().orElse(null);if(player==null)return;
        try{
            if(++ticks>7000||++age>2000)throw new IllegalStateException("Mobility deadline phase="+phase+" mode="+mode+" actor="+(actor==null?"none":actor.position())+" ride="+(actor==null?"none":CityMobility.ride(city,actor.getUUID()))+" tickets="+city.getAttachedOrElse(CityNetwork.NPC,Map.of()));
            if(ticks%100==0)Napuli.LOGGER.info("[MOBILITY] phase={} mode={} age={} actor={} vehicle={} tickets={}",phase,mode,age,actor==null?"none":actor.blockPosition(),actor==null?"none":actor.getVehicle(),city.getAttachedOrElse(CityNetwork.NPC,Map.of()));
            if(actor!=null&&actor.getVehicle()!=null&&ticks%10==0)watch(player,city,actor.getVehicle());
            switch(phase){
                case 0->{
                    if(age<30)return;CityLifeConfig.settings.policePatrolLimit=0;CityLifeConfig.settings.nightThieves=0;CityLifeConfig.settings.citizenVehicleLimit=24;
                    DistrictLife.seed(city);var residents=new ArrayList<NapoliResident>();for(var e:city.getAllEntities())if(e instanceof NapoliResident n&&(NapoliCivicLife.profile(city,n.getUUID())!=null||n.role()==NapoliResident.Role.CARABINIERE))residents.add(n);residents.forEach(NapoliResident::discard);
                    city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of()));
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 5000");player.setGameMode(GameType.CREATIVE);preparePrivate(city,player);next(1);
                }
                case 1->{
                    if(actor.getVehicle()==null)return;vehicle=actor.getVehicle();check(mode==0?vehicle instanceof Motorino:vehicle instanceof CityVehicle,"The resident chooses the expected private vehicle");check(CityMobility.managed(vehicle),"The actual vehicle belongs to the resident");check(NapoliCivicLife.profile(city,actor.getUUID()).balance()==(mode==0?99:98),"Fuel is charged once at boarding");start=vehicle.position();next(2);
                }
                case 2->{
                    if(age<80)return;check(vehicle.position().distanceToSqr(start)>16,"The private vehicle physically drives along the avenue");picture=mode==0?"npc-motorino":"npc-auto";next(3);
                }
                case 3->{
                    if(CityMobility.inTransit(city,actor.getUUID()))return;check(!actor.isPassenger(),"The driver dismounts at arrival");var parked=city.getAttachedOrElse(CityMobility.OWNED,Map.of()).get(actor.getUUID().toString());check(parked!=null&&Math.abs(parked.parked().getZ()-goal.getZ())<4,"The owned vehicle is parked near the destination");check(vehicle.isAlive(),"The observed parked vehicle stays in the city");actor.setNoAi(true);Napuli.LOGGER.info("[MOBILITY] Private {} passed: {} -> {}",mode,start,parked.parked());
                    if(++mode<2){preparePrivate(city,player);next(1);}else{mode=0;preparePublic(city,player);next(4);}
                }
                case 4->{
                    if(!(actor.getVehicle() instanceof CityVehicle v))return;check(v.networkKey().startsWith(PUBLIC[mode]+"/"),"The NPC boards the planned public line");check(NapoliCivicLife.profile(city,actor.getUUID()).balance()==98,"The public fare is exactly two euros");vehicle=v;start=v.position();next(5);
                }
                case 5->{
                    if(age<150)return;check(vehicle.position().distanceToSqr(start)>16,"The public vehicle actually travels with its passenger");picture="npc-"+PUBLIC[mode].toLowerCase(Locale.ROOT);next(6);
                }
                case 6->{
                    if(city.getAttachedOrElse(CityNetwork.NPC,Map.of()).containsKey(actor.getUUID().toString()))return;check(!actor.isPassenger(),"The public passenger dismounts");check(actor.blockPosition().distSqr(CityNetwork.stop(leg.to()).entrance())<144,"The passenger exits at the planned destination");check(NapoliCivicLife.profile(city,actor.getUUID()).balance()==98,"No repeated fare during the trip");Napuli.LOGGER.info("[MOBILITY] Public {} passed: {} -> {}",PUBLIC[mode],leg.from(),leg.to());actor.discard();
                    if(++mode<PUBLIC.length){preparePublic(city,player);next(4);}else next(7);
                }
                case 7->{if(age<30)return;Napuli.LOGGER.info("[MOBILITY] ALL PASSED: real scooter and car commuting, physical driving and parking, bus/tram/metro boarding, movement, fares and destination exits");done=true;}
            }
        }catch(Throwable failure){Napuli.LOGGER.error("[MOBILITY] FAILED",failure);done=true;}
    }
    private static void preparePrivate(ServerLevel city,ServerPlayer player){
        var job=mode==0?NapoliCivicLife.Job.COOK:Arrays.stream(NapoliCivicLife.Job.values()).filter(j->j.category==CityProfessions.Category.OFFICE).findFirst().orElseThrow();
        var at=new BlockPos(-335,64,102);for(int x=-24;x<=-20;x++)for(int z=-4;z<=8;z++)city.getChunk(x,z);
        actor=NapoliResident.create(city,NapoliResident.Role.CIVIC_MAN,at,0);actor.setUUID(new UUID(0,mode==0?1:3));var c=profile(city,actor,job.ordinal());addProfile(city,c);city.addFreshEntity(actor);actor.setCustomName(net.minecraft.network.chat.Component.literal(c.name()));goal=CitySimulation.destination(city,c);check(goal.distSqr(at)>64*64,"The real work destination requires commuting");watch(player,city,actor);Napuli.LOGGER.info("[MOBILITY] Preparing {} work commute to {}",CityMobility.preference(c,NapoliBroadcast.day(city)),goal);
    }
    private static void preparePublic(ServerLevel city,ServerPlayer player){
        var line=CityNetwork.line(PUBLIC[mode]);long best=Long.MAX_VALUE;CityNetwork.Frame chosen=null;leg=null;
        for(var from:line.stops())for(var to:line.stops()){
            if(from.equals(to))continue;var origin=CityNetwork.stop(from);var end=CityNetwork.stop(to);if(origin.boarding(line).distSqr(end.entrance())<100*100||!CityNetwork.nearest(origin.boarding(line)).id().equals(from)||!CityNetwork.nearest(end.entrance()).id().equals(to))continue;
            var route=CityNetwork.itinerary(city,from,to);if(route.size()!=1||!route.getFirst().line().equals(line.id()))continue;
            for(var frame:CityNetwork.plan(line).frames())if(frame.stop().equals(from)&&CityNetwork.towards(line,frame.start()+1,from,to)){long duration=CityNetwork.until(line,frame.start()+1,to);if(duration>150&&duration<best){best=duration;leg=route.getFirst();chosen=frame;}}
        }
        check(leg!=null,"A real direct itinerary exists for "+line.id());CityNetwork.ensureStation(city,CityNetwork.stop(leg.from()));CityNetwork.ensureStation(city,CityNetwork.stop(leg.to()));
        // Reset only this isolated world's schedule before starting each new journey.
        var empty=new ArrayList<CityVehicle>();for(var entity:city.getAllEntities())if(entity instanceof CityVehicle v&&!v.networkKey().isEmpty()&&v.getPassengers().isEmpty())empty.add(v);empty.forEach(CityVehicle::discard);
        city.setAttached(CityNetwork.EPOCH,city.getGameTime()-chosen.start()-1-CityNetwork.delay(city,line.id()+"/0"));
        var at=CityNetwork.stop(leg.from()).boarding(line);actor=NapoliResident.create(city,NapoliResident.Role.CIVIC_MAN,at,0);actor.setUUID(new UUID(0,5+mode*10));actor.setNoAi(true);var c=profile(city,actor,-1);addProfile(city,c);city.addFreshEntity(actor);actor.setCustomName(net.minecraft.network.chat.Component.literal(c.name()));goal=CityNetwork.stop(leg.to()).entrance();watch(player,city,actor);
        check(CityMobility.commute(actor,city,c,goal),"The mobility planner selects public transport");var ticket=city.getAttachedOrElse(CityNetwork.NPC,Map.of()).get(c.id().toString());check(ticket!=null&&ticket.legs().getFirst().equals(leg),"The planner produces the intended public route");Napuli.LOGGER.info("[MOBILITY] Preparing {} {} -> {}, duration {} ticks",line.id(),leg.from(),leg.to(),best);
    }
    private static NapoliCivicLife.Citizen profile(ServerLevel city,NapoliResident npc,int job){return new NapoliCivicLife.Citizen(npc.getUUID(),"Residente in viaggio "+npc.getUUID().getLeastSignificantBits(),false,NapoliBroadcast.day(city)-30L*CityLifeConfig.settings.daysPerYear,100,job,"","public_0","",true);}
    private static void addProfile(ServerLevel city,NapoliCivicLife.Citizen c){var all=new ArrayList<>(NapoliCivicLife.census(city).people());all.add(c);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.copyOf(all)));var life=CitySimulation.fresh(c.id());CitySimulation.put(city,c.id(),life.withNeeds(new CitySimulation.Needs(95,95,95,95,95,95),8,city.getGameTime(),0,0));}
    private static void watch(ServerPlayer p,ServerLevel city,Entity focus){p.teleportTo(city,focus.getX()+9,focus.getY()+2,focus.getZ()+2,Set.of(),102,8,true);}
    private static void next(int p){phase=p;age=0;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private MobilityClientTest(){}
}
