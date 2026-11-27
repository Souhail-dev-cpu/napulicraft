package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;

/** Real interaction packets and held W input verify both stolen vehicles and the ensuing police response. */
public final class VehicleTheftClientTest {
    private static int titles,ticks,phase,age,frames;
    private static volatile int driving;
    private static volatile UUID target;
    private static volatile boolean done;
    private static volatile String picture;
    private static CityVehicle car;
    private static Motorino bike;
    private static NapoliResident owner,officer;
    private static Vec3 start,officerStart;
    private static final UUID CAR_OWNER=new UUID(17,3),BIKE_OWNER=new UUID(17,1);
    public static void register(){
        if(!Boolean.getBoolean("napuli.vehiclethefttest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(done){mc.options.keyUp.setDown(false);mc.stop();return;}
            if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){var settings=new LevelSettings("Furti dei veicoli",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("vehicle-theft-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
            mc.options.pauseOnLostFocus=false;mc.options.keyUp.setDown(driving!=0);mc.options.keyShift.setDown(false);
            if(target!=null&&mc.player.getVehicle()==null&&++frames%10==0){var id=target;mc.level.getEntitiesOfClass(LivingEntity.class,mc.player.getBoundingBox().inflate(5),e->e.getUUID().equals(id)).stream().findFirst().ifPresent(e->mc.gameMode.interact(mc.player,e,new EntityHitResult(e),InteractionHand.MAIN_HAND));}
            if(picture!=null){var name=picture;picture=null;Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,message->{});}
        });ServerTickEvents.END_SERVER_TICK.register(VehicleTheftClientTest::tick);
    }
    private static void tick(net.minecraft.server.MinecraftServer server){
        var city=server.getLevel(NapoliMap.LEVEL);if(city==null||done)return;var p=server.getPlayerList().getPlayers().stream().filter(a->a.level()==city).findFirst().orElse(null);if(p==null)return;
        try{
            if(++ticks>2400||++age>600)throw new IllegalStateException("Vehicle theft deadline phase="+phase+" player="+p.position()+" vehicle="+p.getVehicle());
            if(ticks%100==0)Napuli.LOGGER.info("[VEHICLETHEFT] phase={} age={} player={} vehicle={} wanted={}",phase,age,p.position(),p.getVehicle(),Carabinieri.wantedStars(p));
            switch(phase){
                case 0->{
                    if(age<30)return;DistrictLife.seed(city);CityLifeConfig.settings.policePatrolLimit=0;CityLifeConfig.settings.nightThieves=0;var actors=new ArrayList<NapoliResident>();for(var e:city.getAllEntities())if(e instanceof NapoliResident n)actors.add(n);actors.forEach(NapoliResident::discard);clock(server,5000);
                    city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(person(city,CAR_OWNER),person(city,BIKE_OWNER))));p.setGameMode(GameType.SURVIVAL);p.setPermanentlyInvulnerable(true);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));p.teleportTo(city,-332.5,64,100.5,Set.of(),0,0,true);next(99);
                }
                case 99->{
                    if(age<35||!city.areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(-21,6)))return;car=(CityVehicle)CityMobility.create(city,person(city,CAR_OWNER),"car",new BlockPos(-335,64,100));check(car!=null,"The NPC car is physically parked beside the player");target=car.getUUID();next(1);
                }
                case 1->{
                    if(p.getVehicle()!=car)return;target=null;check(car.stolenVehicle()&&car.getControllingPassenger()==p&&car.getAttached(VehicleTheft.STOLEN)!=null,"A real right-click makes the player the car's driver with saved theft data");check(!Carabinieri.isWanted(p),"The unwitnessed theft produces no random report");start=car.position();driving=1;next(2);
                }
                case 2->{
                    if(age==40)picture="auto-rubata-guida";if(age<80)return;driving=0;check(car.position().distanceToSqr(start)>25,"Holding actual W input physically drives the stolen car");check(!Carabinieri.isWanted(p),"Unwitnessed driving stays unreported");p.stopRiding();check(VehicleTheft.missing(city,CAR_OWNER)&&car.shouldBeSaved(),"The parked stolen car remains saved and its owner cannot regenerate it");p.teleportTo(city,-332.5,64,70.5,Set.of(),0,0,true);next(30);
                }
                case 30->{
                    if(age<35||!city.areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(-21,4)))return;owner=NapoliResident.create(city,NapoliResident.Role.CIVIC_MAN,new BlockPos(-335,64,70),0);owner.setUUID(BIKE_OWNER);owner.setNoAi(true);city.addFreshEntity(owner);bike=(Motorino)CityMobility.create(city,person(city,BIKE_OWNER),"scooter",owner.blockPosition());check(bike!=null&&owner.startRiding(bike),"The real owner is in the scooter's driver's seat");target=bike.getUUID();next(3);
                }
                case 3->{
                    if(p.getVehicle()!=bike)return;target=null;check(!owner.isPassenger()&&owner.isAlive()&&owner.distanceToSqr(bike)<10*10,"Taking the scooter makes its owner dismount safely nearby");check(bike.getAttached(VehicleTheft.STOLEN).witnessed()&&city.getAttachedOrElse(CityResidentSafety.CALLS,Map.of()).containsKey(BIKE_OWNER.toString()),"The ejected owner starts an actual 112 report");start=bike.position();driving=2;officer=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,new BlockPos(-338,64,50),0);officerStart=officer.position();PoliceDispatch.invalidate(city);next(4);
                }
                case 4->{
                    if(age==70)picture="motorino-rubato-inseguimento";if(age<110)return;driving=0;check(bike.position().distanceToSqr(start)>25&&p.getVehicle()==bike,"Actual player input drives the stolen scooter");check(Carabinieri.isWanted(p)&&Carabinieri.wantedStars(p)==3,"The owner's vehicle robbery report creates the correct wanted offence");check(Carabinieri.chasing(city,officer)&&officer.position().distanceToSqr(officerStart)>1,"An actual carabiniere follows the reported driver");check(ScooterGarage.get(city,bike.getUUID())==null&&VehicleTheft.missing(city,BIKE_OWNER),"The scooter is still the original NPC's missing vehicle, without a legal garage token");next(5);
                }
                case 5->{if(age<30)return;Napuli.LOGGER.info("[VEHICLETHEFT] ALL PASSED: real right-click theft, actual player-driven car and scooter, unwitnessed theft without random police, safe driver ejection, owner phone report, robbery pursuit, saved identity and no duplicated vehicle or garage token");done=true;}
            }
        }catch(Throwable failure){driving=0;Napuli.LOGGER.error("[VEHICLETHEFT] FAILED",failure);done=true;}
    }
    private static NapoliCivicLife.Citizen person(ServerLevel city,UUID id){return new NapoliCivicLife.Citizen(id,"Proprietario prova",false,NapoliBroadcast.day(city)-30L*CityLifeConfig.settings.daysPerYear,100,-1,"","public_0","",true);}
    private static void clock(net.minecraft.server.MinecraftServer s,int time){s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"time set "+time);}
    private static void next(int value){phase=value;age=0;}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private VehicleTheftClientTest(){}
}
