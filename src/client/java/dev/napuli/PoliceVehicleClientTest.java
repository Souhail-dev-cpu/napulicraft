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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** Real mounted pursuit, foot arrest, exposed escape failure and continuous physical concealment. */
public final class PoliceVehicleClientTest {
    private static int titles,ticks,phase,age;
    private static volatile boolean done;
    private static volatile String picture;
    private static NapoliResident bikeOfficer,carOfficer,searcher;
    private static Entity bike,car;
    private static Vec3 bikeStart,carStart;
    private static BlockPos scene;
    public static void register(){
        if(!Boolean.getBoolean("napuli.policevehicletest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done){mc.stop();return;}
            if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){var settings=new LevelSettings("Pattuglie e nascondigli",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("police-vehicle-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
            mc.options.pauseOnLostFocus=false;if(picture!=null){var name=picture;picture=null;Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,message->{});}
        });ServerTickEvents.END_SERVER_TICK.register(PoliceVehicleClientTest::tick);
    }
    private static void tick(net.minecraft.server.MinecraftServer server){
        var city=server.getLevel(NapoliMap.LEVEL);if(city==null||done)return;var p=server.getPlayerList().getPlayers().stream().filter(a->a.level()==city).findFirst().orElse(null);if(p==null)return;
        try{
            if(++ticks>3000||++age>1400)throw new IllegalStateException("Police vehicle deadline phase="+phase+" bike="+(bikeOfficer==null?"none":bikeOfficer.position())+" car="+(carOfficer==null?"none":carOfficer.position()));
            if(ticks%100==0)Napuli.LOGGER.info("[POLICEVEHICLE] phase={} age={} stars={} hiddenLeft={} bike={} car={}",phase,age,Carabinieri.wantedStars(p),Carabinieri.wantedSecondsLeft(p),bikeOfficer==null?"none":bikeOfficer.getVehicle(),carOfficer==null?"none":carOfficer.getVehicle());
            switch(phase){
                case 0->{
                    if(age<30)return;DistrictLife.seed(city);CityLifeConfig.settings.policePatrolLimit=0;CityLifeConfig.settings.nightThieves=0;CityLifeConfig.settings.policeVehicleLimit=2;clear(city);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of()));clock(server,5000);
                    p.setGameMode(GameType.SURVIVAL);p.setPermanentlyInvulnerable(true);p.teleportTo(city,-338.5,64,140.5,Set.of(),180,0,true);NapoliStreet.credit(p,100-NapoliStreet.cash(p));for(int z=3;z<=9;z++)for(int x=-22;x<=-21;x++)city.getChunk(x,z);
                    next(99);
                }
                case 99->{
                    if(age<30||!city.areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(-22,5))||!city.areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(-22,6)))return;
                    bikeOfficer=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,new BlockPos(-338,64,95),0);carOfficer=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,new BlockPos(-341,64,85),0);Carabinieri.denounce(p,Carabinieri.Crime.THEFT,p.blockPosition());next(1);
                }
                case 1->{
                    if(!(bikeOfficer.getVehicle() instanceof Motorino)||!(carOfficer.getVehicle() instanceof CityVehicle))return;bike=bikeOfficer.getVehicle();car=carOfficer.getVehicle();bikeStart=bike.position();carStart=car.position();check(PoliceDispatch.eligible(bikeOfficer)&&PoliceDispatch.eligible(carOfficer),"Both mounted responders remain assigned to the suspect");next(2);
                }
                case 2->{
                    if(age<100)return;check(bike.position().distanceToSqr(bikeStart)>100&&car.position().distanceToSqr(carStart)>100,"The actual police bike and car physically advance along the street: bike "+bikeStart+" -> "+bike.position()+" car "+carStart+" -> "+car.position());picture="carabinieri-inseguimento";next(3);
                }
                case 3->{
                    if(Carabinieri.isWanted(p))return;check(NapoliStreet.cash(p)==85,"The responders dismount, catch the player and settle the minor-offence fine once");check(!bikeOfficer.isPassenger()&&!carOfficer.isPassenger()&&bike.isRemoved()&&car.isRemoved(),"The arrest retires both emergency vehicles and releases their drivers");clear(city);p.teleportTo(city,-338.5,64,140.5,Set.of(),180,0,true);Carabinieri.denounce(p,Carabinieri.Crime.THEFT,p.blockPosition());scene=Carabinieri.lastKnown(p);next(4);
                }
                case 4->{
                    if(age<440)return;check(Carabinieri.isWanted(p)&&Carabinieri.wantedSecondsLeft(p)==20,"Even twenty-two seconds unseen on an open street do not clear the search");
                    // A small closed room in this disposable world supplies actual opaque cover.
                    for(var at:BlockPos.betweenClosed(-327,63,138,-323,68,142)){boolean shell=at.getY()==63||at.getY()==68||at.getX()==-327||at.getX()==-323||at.getZ()==138||at.getZ()==142;city.setBlock(at,shell?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState(),18);}
                    searcher=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,scene.north(4),0);p.teleportTo(city,-325.5,64,140.5,Set.of(),90,0,true);PoliceDispatch.invalidate(city);next(5);
                }
                case 5->{
                    if(age<120)return;check(Carabinieri.isWanted(p)&&Carabinieri.wantedSecondsLeft(p)<20,"An actual room advances the concealed countdown");check(Carabinieri.lastKnown(p).equals(scene),"The searching officer never receives the hidden player's new position");picture="carabinieri-nascondiglio";p.teleportTo(city,scene.getX()+.5,scene.getY(),scene.getZ()+.5,Set.of(),0,0,true);next(6);
                }
                case 6->{
                    if(age<10)return;check(Carabinieri.isWanted(p)&&Carabinieri.wantedSecondsLeft(p)==20,"Reappearing in front of an officer resets the whole countdown");p.teleportTo(city,-325.5,64,140.5,Set.of(),90,0,true);next(7);
                }
                case 7->{
                    if(age<420)return;check(!Carabinieri.isWanted(p)&&!Carabinieri.chasing(city,searcher)&&!PoliceTactics.active(searcher),"Staying hidden for twenty continuous seconds clears the search and resumes the patrol");picture="carabinieri-seminati";next(8);
                }
                case 8->{if(age<30)return;Napuli.LOGGER.info("[POLICEVEHICLE] ALL PASSED: actual bike and car pursuit, dismount and physical arrest, exposed-road escape rejection, genuine room concealment, no hidden tracking, sighting reset and resumed patrol");done=true;}
            }
        }catch(Throwable failure){Napuli.LOGGER.error("[POLICEVEHICLE] FAILED",failure);done=true;}
    }
    private static void clear(ServerLevel city){var all=new ArrayList<Entity>();for(var e:city.getAllEntities())if(e instanceof NapoliResident||PoliceVehicles.managed(e))all.add(e);all.forEach(Entity::discard);PoliceDispatch.invalidate(city);}
    private static void clock(net.minecraft.server.MinecraftServer s,int time){s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"time set "+time);}
    private static void next(int value){phase=value;age=0;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private PoliceVehicleClientTest(){}
}
