package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** Actual client camera and input packets, not simulated walking commands. Separate disposable world. */
public final class VehicleInteriorClientTest {
    private static int titles,age,phase,kind;
    private static volatile boolean done,walking,jump,viewBack;
    private static volatile String picture;
    private static CityVehicle vehicle;
    private static double initial;
    public static void register(){
        if(!Boolean.getBoolean("napuli.vehicleinteriortest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done){mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.stop();return;}
            if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){var settings=new LevelSettings("Interni veicoli",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("vehicle-interiors-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
            mc.options.pauseOnLostFocus=false;mc.options.keyUp.setDown(walking);mc.options.keyJump.setDown(jump);mc.options.keyShift.setDown(false);
            if(mc.player.getVehicle()!=null&&viewBack){mc.player.setYRot(180);mc.player.setXRot(12);}
            if(picture!=null){var name=picture;picture=null;Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,message->{});}
        });ServerTickEvents.END_SERVER_TICK.register(VehicleInteriorClientTest::tick);
    }
    private static void tick(net.minecraft.server.MinecraftServer server){
        var city=server.getLevel(NapoliMap.LEVEL);if(city==null||done)return;var p=server.getPlayerList().getPlayers().stream().filter(a->a.level()==city).findFirst().orElse(null);if(p==null)return;
        try{
            if(++age>400)throw new IllegalStateException("Deadline phase="+phase+" kind="+kind);
            switch(phase){
                case 0->{if(age<35)return;CityLifeConfig.settings.policePatrolLimit=0;CityLifeConfig.settings.nightThieves=0;var remove=new ArrayList<NapoliResident>();for(var e:city.getAllEntities())if(e instanceof NapoliResident n)remove.add(n);remove.forEach(NapoliResident::discard);p.setPermanentlyInvulnerable(true);server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 5000");p.teleportTo(city,-330,64,100,Set.of(),45,8,true);next(1);}
                case 1->{if(age<40)return;vehicle=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);check(vehicle!=null,"Vehicle created");vehicle.configureNetwork("",kind);vehicle.addTag(VehicleTheft.TAG);vehicle.snapTo(-335,64,106,0,0);city.addFreshEntity(vehicle);next(2);}
                case 2->{if(age==30)picture="mezzo-"+kind+"-esterno";if(age<50)return;p.teleportTo(city,-333,64,106,Set.of(),0,13,true);check(p.startRiding(vehicle,false,false),"The passenger enters the actual vehicle");viewBack=kind>0;next(3);}
                case 3->{if(age==35)picture="mezzo-"+kind+"-interno";if(age<50)return;var local=vehicle.passengerLocation(p);check(Math.abs(p.getY()-vehicle.getY()-local.y)<.1,"Feet match the rendered seat");check(p.getEyeY()<vehicle.getY()+VehicleCabin.layout(kind).height()-.12,"The first-person camera fits below the roof");if(kind==0){p.stopRiding();check(!p.isPassenger()&&city.noCollision(p),"Dismount exits outside the body without entering a wall");vehicle.discard();kind++;next(0);}else{jump=true;next(4);}}
                case 4->{if(age<5)return;jump=false;if(!vehicle.standing(p))return;initial=vehicle.passengerLocation(p).z;walking=true;next(5);}
                case 5->{vehicle.move(MoverType.SELF,new Vec3(0,0,.08));if(age<25)return;walking=false;check(vehicle.standing(p)&&vehicle.passengerLocation(p).z>initial+.3,"Actual W input walks down the moving cabin aisle");check(p.getVehicle()==vehicle,"Walking never detaches a passenger from the carriage");picture="mezzo-"+kind+"-corridoio";jump=true;next(6);}
                case 6->{if(age==5)jump=false;if(age<15)return;check(!vehicle.standing(p),"Another space press sits back down");p.stopRiding();check(city.noCollision(p),"Safe exit for each coach size");vehicle.discard();if(++kind<4)next(0);else{Napuli.LOGGER.info("[VEHICLEINTERIOR] ALL PASSED: four full-sized interiors, seat positions, unobstructed first-person camera, real space and W input, secure aisle walking and safe exits");done=true;}}
            }
        }catch(Throwable failure){walking=jump=false;Napuli.LOGGER.error("[VEHICLEINTERIOR] FAILED",failure);done=true;}
    }
    private static void next(int value){phase=value;age=0;}
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private VehicleInteriorClientTest(){}
}
