package dev.napuli;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;

public final class MobilityGameTest {
    @GameTest public void drivingChoicesAreStableAndChildrenDoNotDrive(GameTestHelper h){
        var city=h.getLevel();var modes=new HashSet<String>();for(int i=0;i<100;i++){var c=person(city,new UUID(0,i),30);modes.add(CityMobility.preference(c,NapoliBroadcast.day(city)));h.assertTrue(CityMobility.preference(c,0).equals(CityMobility.preference(c,20)),"Personal transport preference survives days and reloads");}
        h.assertTrue(modes.equals(Set.of("car","scooter","public")),"The city uses all three transport choices");h.assertTrue(CityMobility.preference(person(city,UUID.randomUUID(),12),0).equals("public"),"Children use public transport instead of driving");h.succeed();
    }
    @GameTest public void vehicleAndJourneyIdentitySurviveSaving(GameTestHelper h){
        var at=new BlockPos(-343,64,90);var owned=new CityMobility.Owned("scooter",at);var road=new CityJourneys.Journey(List.of(at,at.north(80)),100,420,at.north(80),100);var ride=new CityMobility.Ride("scooter",road,1,at.north(90));
        h.assertTrue(CityMobility.Owned.CODEC.parse(JsonOps.INSTANCE,CityMobility.Owned.CODEC.encodeStart(JsonOps.INSTANCE,owned).getOrThrow()).getOrThrow().equals(owned),"The actual parking spot survives saving");
        h.assertTrue(CityMobility.Ride.CODEC.parse(JsonOps.INSTANCE,CityMobility.Ride.CODEC.encodeStart(JsonOps.INSTANCE,ride).getOrThrow()).getOrThrow().equals(ride),"Route, mode, progress and final destination survive saving");
        UUID id=UUID.randomUUID();h.assertTrue(CityMobility.identity(id).equals(CityMobility.identity(id))&&!CityMobility.identity(id).equals(id),"A resident has one stable vehicle identity, distinct from the resident");h.succeed();
    }
    @GameTest public void privateRoutesUseTheAvenueAndDoNotCutThroughTheOldCenter(GameTestHelper h){
        var road=CityMobility.streets(new BlockPos(-372,64,130),new BlockPos(-417,64,-30));h.assertTrue(road.size()==2&&road.stream().allMatch(p->p.getX()==-339),"The original neighborhood uses its actual broad avenue");
        h.assertTrue(CityMobility.streets(new BlockPos(-340,64,130),new BlockPos(100,64,-30)).isEmpty(),"A private route does not drive straight through old-city houses");h.succeed();
    }
    @GameTest public void mountingChargesOnceAndTheDriverCannotCloneAVehicle(GameTestHelper h){
        isolated(h,city->{var at=new BlockPos(-335,64,102);floor(city,at,10);var npc=actor(city,new UUID(0,3),at);var c=person(city,npc.getUUID(),30);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(c)));
            try{h.assertTrue(CityMobility.commute(npc,city,c,new BlockPos(-335,64,-80))&&npc.getVehicle() instanceof CityVehicle,"A car owner walks to the curb and mounts its own car");
                h.assertTrue(NapoliCivicLife.profile(city,c.id()).balance()==98,"Fuel is charged only after mounting succeeds");var vehicle=npc.getVehicle();CityMobility.commute(npc,city,NapoliCivicLife.profile(city,c.id()),new BlockPos(-335,64,-80));
                h.assertTrue(npc.getVehicle()==vehicle&&NapoliCivicLife.profile(city,c.id()).balance()==98,"Repeated AI updates neither create another vehicle nor charge again");
                npc.stopRiding();h.assertTrue(!CityMobility.commute(npc,city,NapoliCivicLife.profile(city,c.id()),new BlockPos(-335,64,-80))&&!CityMobility.inTransit(city,c.id()),"An interrupted ride releases the resident instead of freezing its routine");vehicle.discard();
            }finally{var v=npc.getVehicle();npc.stopRiding();npc.discard();if(v!=null)v.discard();}
        });h.succeed();
    }
    @GameTest public void carsYieldToPedestriansThenParkAndDismount(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,players->{var at=h.absolutePos(new BlockPos(5,3,5));floor(city,at,18);players.getFirst().setPos(at.getX(),at.getY(),at.getZ()-10);var npc=actor(city,UUID.randomUUID(),at);var c=person(city,npc.getUUID(),30);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(c)));var vehicle=CityMobility.create(city,c,"car",at);h.assertTrue(vehicle!=null&&npc.startRiding(vehicle),"Fixture creates a real resident-driven car");
            var end=at.east(12);city.setAttached(CityMobility.RIDES,Map.of(c.id().toString(),new CityMobility.Ride("car",new CityJourneys.Journey(List.of(at,end),0,100,end,0),1,end.east(5))));var pedestrian=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,at.east(2),0);pedestrian.setNoAi(true);
            try{var before=vehicle.position();CityMobility.drive(vehicle,city);h.assertTrue(vehicle.position().distanceToSqr(before)<.0001,"A pedestrian ahead stops the car");pedestrian.discard();
                for(int i=0;i<65;i++)CityMobility.drive(vehicle,city);
                h.assertTrue(vehicle.position().distanceToSqr(before)>25&&npc.getVehicle()==null&&!CityMobility.inTransit(city,c.id()),"The real car moves, parks, and releases its passenger");h.assertTrue(city.getAttached(CityMobility.OWNED).get(c.id().toString()).parked().distSqr(end)<4,"The registry records where the vehicle was actually parked");
            }finally{pedestrian.discard();npc.stopRiding();npc.discard();vehicle.discard();}
        }));h.succeed();
    }
    @GameTest public void distantDrivesBecomeVirtualWithoutLosingTheirDestination(GameTestHelper h){
        isolated(h,city->{var at=h.absolutePos(new BlockPos(5,3,5));floor(city,at,10);var npc=actor(city,UUID.randomUUID(),at);var c=person(city,npc.getUUID(),30);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(0,List.of(c)));var vehicle=CityMobility.create(city,c,"scooter",at);h.assertTrue(vehicle!=null&&npc.startRiding(vehicle),"Fixture mounts a real scooter");var goal=at.east(160);city.setAttached(CityMobility.RIDES,Map.of(c.id().toString(),new CityMobility.Ride("scooter",new CityJourneys.Journey(List.of(at,goal),city.getGameTime(),city.getGameTime()+640,goal,0),1,goal.south(4))));
            try{CityMobility.drive(vehicle,city);h.assertTrue(vehicle.isRemoved()&&npc.isRemoved()&&CityMobility.inTransit(city,c.id()),"Unobserved vehicles and passengers leave the physical simulation together");h.assertTrue(CityMobility.ride(city,c.id()).goal().equals(goal.south(4))&&CityMobility.position(city,c.id()).distSqr(at)<4,"The virtual drive starts at its real departure point and retains the goal");}
            finally{npc.stopRiding();npc.discard();vehicle.discard();}
        });h.succeed();
    }
    @GameTest public void residentScootersNeverEnterThePlayersGarage(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,players->{var at=h.absolutePos(new BlockPos(5,3,5));floor(city,at,10);var npc=actor(city,UUID.randomUUID(),at);var c=person(city,npc.getUUID(),30);var bike=(Motorino)CityMobility.create(city,c,"scooter",at);
            try{h.assertTrue(bike!=null&&!bike.shouldBeSaved(),"A resident scooter is saved through its authoritative registry");h.assertTrue(!players.getFirst().startRiding(bike)&&ScooterGarage.get(city,bike.getUUID())==null,"A player cannot claim the resident's scooter or register a recovery token for it");}
            finally{npc.discard();if(bike!=null)bike.discard();}
        }));h.succeed();
    }
    @GameTest public void npcPassengersBoardBusTramAndMetroAndPayOnlyOnce(GameTestHelper h){
        for(String id:List.of("B5","T1","M6"))isolated(h,city->{var line=CityNetwork.line(id);String from=line.stops().getFirst(),to=line.stops().get(1);int phase=CityNetwork.plan(line).frames().stream().filter(f->f.stop().equals(from)&&CityNetwork.towards(line,f.start()+1,from,to)).findFirst().orElseThrow().start()+1;city.setAttached(CityNetwork.EPOCH,city.getGameTime()-phase);long clock=city.getOverworldClockTime();setClock(city,6000);
            var at=CityNetwork.stop(from).boarding(line);floor(city,at,12);var npc=actor(city,UUID.randomUUID(),at);var c=person(city,npc.getUUID(),30);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(0,List.of(c)));var ticket=new CityNetwork.Ticket(List.of(new CityNetwork.Leg(id,from,to)),"waiting","",city.getGameTime(),0,0,false);
            try{var boarded=CityNetwork.process(city,npc,ticket,false);h.assertTrue(boarded!=null&&boarded.state().equals("riding")&&npc.getVehicle() instanceof CityVehicle,"The resident physically boards "+id);h.assertTrue(NapoliCivicLife.profile(city,c.id()).balance()==98,"The actual urban fare is paid on boarding: "+id);CityNetwork.process(city,npc,boarded,false);h.assertTrue(NapoliCivicLife.profile(city,c.id()).balance()==98,"Remaining onboard does not charge another fare: "+id);}
            finally{var v=npc.getVehicle();npc.stopRiding();npc.discard();if(v!=null)v.discard();setClock(city,clock);}
        });h.succeed();
    }
    @GameTest public void virtualPassengersReserveCapacityWithoutChargingWaitingResidents(GameTestHelper h){
        isolated(h,city->{var line=CityNetwork.line("B5");String from=line.stops().getFirst(),to=line.stops().get(1);int phase=CityNetwork.plan(line).frames().stream().filter(f->f.stop().equals(from)&&CityNetwork.towards(line,f.start()+1,from,to)).findFirst().orElseThrow().start()+1;city.setAttached(CityNetwork.EPOCH,city.getGameTime()-phase-CityNetwork.delay(city,"B5/0"));long clock=city.getOverworldClockTime();setClock(city,6000);
            var at=CityNetwork.stop(from).boarding(line);floor(city,at,12);var npc=actor(city,UUID.randomUUID(),at);var c=person(city,npc.getUUID(),30);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(0,List.of(c)));var legs=List.of(new CityNetwork.Leg(line.id(),from,to));var all=new HashMap<String,CityNetwork.Ticket>();for(int i=0;i<line.capacity();i++)all.put(UUID.randomUUID().toString(),new CityNetwork.Ticket(legs,"riding","B5/0",0,city.getGameTime()+500,0,true));city.setAttached(CityNetwork.NPC,Map.copyOf(all));
            try{var waiting=CityNetwork.process(city,npc,new CityNetwork.Ticket(legs,"waiting","",city.getGameTime(),0,0,false),false);h.assertTrue(waiting!=null&&waiting.state().equals("waiting")&&!npc.isPassenger(),"Virtual passengers still occupy the bus seats");h.assertTrue(NapoliCivicLife.profile(city,c.id()).balance()==100,"Waiting for a full bus costs nothing");}
            finally{npc.discard();setClock(city,clock);}
        });h.succeed();
    }
    @GameTest public void shortTripsAndUnaffordableTransitRemainOnFoot(GameTestHelper h){
        isolated(h,city->{var at=h.absolutePos(new BlockPos(3,3,3));floor(city,at,8);var npc=actor(city,new UUID(0,5),at);var c=person(city,npc.getUUID(),30).withMoney(1,-1);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(0,List.of(c)));
            try{h.assertTrue(!CityMobility.commute(npc,city,c,at.east(10))&&!CityNetwork.commute(npc,city,at.east(300)),"Short trips and trips without the fare do not create tickets or debts");h.assertTrue(npc.getVehicle()==null&&city.getAttachedOrElse(CityNetwork.NPC,Map.of()).isEmpty(),"The resident remains on foot");}finally{npc.discard();}
        });h.succeed();
    }
    @GameTest public void virtualTransitUsesTheVehiclePositionAndCannotWorkAtHome(GameTestHelper h){
        isolated(h,city->{var c=person(city,UUID.randomUUID(),30);var home=NapoliCivicLife.home(c);long now=city.getGameTime();city.setAttached(CityJourneys.JOURNEYS,Map.of(c.id().toString(),new CityJourneys.Journey(List.of(home,home),now,now+1,home,now)));var line=CityNetwork.line("M6");var leg=new CityNetwork.Leg(line.id(),line.stops().getFirst(),line.stops().get(1));city.setAttached(CityNetwork.NPC,Map.of(c.id().toString(),new CityNetwork.Ticket(List.of(leg),"riding","M6/0",now,now+1000,0,true)));
            var actual=BlockPos.containing(CityNetwork.position(city,line,0).point());h.assertTrue(CityJourneys.position(city,c,home).equals(actual),"An unmaterialized passenger is located on the actual train");h.assertTrue(!CityJourneys.at(city,c,home,12),"A passenger cannot use furniture, shop or work through an old walking journey");
            city.removeAttached(CityNetwork.NPC);city.setAttached(CityMobility.RIDES,Map.of(c.id().toString(),new CityMobility.Ride("car",new CityJourneys.Journey(List.of(home,home.east(100)),now,now+400,home.east(100),now),1,home.east(104))));h.assertTrue(!CityJourneys.at(city,c,home,12),"A virtual driver also waits until dismounting to use the destination");
        });h.succeed();
    }
    private static final List<AttachmentType<?>> TYPES=List.of(NapoliCivicLife.CENSUS,CityJourneys.JOURNEYS,CityMobility.OWNED,CityMobility.RIDES,CityNetwork.NPC,CityNetwork.NPC_PASSES,CityNetwork.EPOCH,WorkEconomy.FUND);
    private record FixtureBlock(net.minecraft.world.level.block.state.BlockState state,net.minecraft.nbt.CompoundTag data){}
    private static final Map<BlockPos,FixtureBlock> FLOOR=new LinkedHashMap<>();
    private static void isolated(GameTestHelper h,java.util.function.Consumer<ServerLevel> body){var city=h.getLevel();var saved=new HashMap<AttachmentType<?>,Object>();for(var t:TYPES)saved.put(t,city.removeAttached(t));try{body.accept(city);}finally{
        for(var e:FLOOR.entrySet())city.setBlock(e.getKey(),e.getValue().state(),18);
        for(var e:FLOOR.entrySet())if(e.getValue().data()!=null&&city.getBlockEntity(e.getKey()) instanceof net.minecraft.world.level.block.entity.BlockEntity be)be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,city.registryAccess(),e.getValue().data()));FLOOR.clear();
        for(var t:TYPES)restore(city,t,saved.get(t));}}
    private static NapoliResident actor(ServerLevel city,UUID id,BlockPos at){var npc=NapoliResident.create(city,NapoliResident.Role.CIVIC_MAN,at,0);npc.setUUID(id);npc.setNoAi(true);city.addFreshEntity(npc);return npc;}
    static NapoliCivicLife.Citizen person(ServerLevel city,UUID id,int age){return new NapoliCivicLife.Citizen(id,"Residente in viaggio",false,NapoliBroadcast.day(city)-(long)age*CityLifeConfig.settings.daysPerYear,100,-1,"","public_0","",true);}
    private static void floor(ServerLevel city,BlockPos at,int radius){for(var p:BlockPos.betweenClosed(at.offset(-radius,-1,-radius),at.offset(radius,3,radius))){var be=city.getBlockEntity(p);FLOOR.putIfAbsent(p.immutable(),new FixtureBlock(city.getBlockState(p),be==null?null:be.saveWithFullMetadata(city.registryAccess())));city.setBlock(p,p.getY()==at.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);}}
    private static void setClock(ServerLevel city,long time){city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set "+time);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void restore(ServerLevel city,AttachmentType type,Object value){if(value==null)city.removeAttached(type);else city.setAttached(type,value);}
}
