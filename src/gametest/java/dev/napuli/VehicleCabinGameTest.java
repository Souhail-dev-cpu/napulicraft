package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.storage.*;

public final class VehicleCabinGameTest {
    @GameTest public void vehicleBodiesAndSeatsFitHumanPassengersAndDifferByMode(GameTestHelper h){
        int[] capacities={4,8,12,16};float previous=0;
        for(int kind=0;kind<4;kind++){
            var layout=VehicleCabin.layout(kind);h.assertTrue(layout.seats().size()==capacities[kind],"Each model has its own actual seats");h.assertTrue(layout.length()>previous,"Bus, tram and metro have different full-sized bodies");previous=layout.length();
            for(var seat:layout.seats()){h.assertTrue(Math.abs(seat.x)+.3<layout.width()/2&&Math.abs(seat.z)+.4<layout.length()/2,"Passenger shoulders and legs remain inside the body");h.assertTrue(seat.y+1.62<layout.height()-.12,"Seated eyes stay below the roof");}
            h.assertTrue(new HashSet<>(layout.seats()).size()==capacities[kind],"No two passengers share a chair");
        }h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void fourCarSeatsKeepTheDriverAndOwnerWhilePassingControlOnExit(GameTestHelper h){
        HousingCapacityGameTest.withPlayers(h,5,players->{var city=h.getLevel();var car=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);var at=h.absolutePos(new BlockPos(6,2,6));car.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);city.addFreshEntity(car);car.setStolenVehicle(true);var owner=UUID.randomUUID();car.setAttached(VehicleTheft.STOLEN,new VehicleTheft.Stolen(owner,players.getFirst().getUUID(),city.getGameTime(),true,false));
            try{
                for(int i=0;i<4;i++){var p=players.get(i);p.setPos(car.getX()+2,car.getY(),car.getZ());h.assertTrue(i==0?p.startRiding(car):VehicleTheft.take(p,car),"Four players can enter distinct car seats");}
                players.get(4).setPos(car.getX()+2,car.getY(),car.getZ());h.assertTrue(!VehicleTheft.take(players.get(4),car),"A full car rejects a fifth passenger");h.assertTrue(car.getControllingPassenger()==players.getFirst(),"Boarding passengers cannot steal the occupied driving controls");
                h.assertTrue(players.subList(0,4).stream().map(car::passengerLocation).distinct().count()==4,"Four different rendered seats are used");players.getFirst().stopRiding();VehicleTheft.tick(car,city);
                h.assertTrue(car.getControllingPassenger()==players.get(1)&&car.passengerLocation(players.get(1)).equals(VehicleCabin.layout(0).seat(0)),"The next driver moves into the actual driving seat");h.assertTrue(car.getAttached(VehicleTheft.STOLEN).owner().equals(owner),"A change of driver cannot lose the original vehicle owner");
            }finally{players.forEach(p->p.stopRiding());car.discard();}
        });h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void standingPassengersUseTheAisleWithoutSharingSeatsOrLeavingTheVehicle(GameTestHelper h){
        HousingCapacityGameTest.withPlayers(h,2,ps->{var city=h.getLevel();var car=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);car.configureNetwork("",1);car.addTag(VehicleTheft.TAG);var at=h.absolutePos(new BlockPos(6,2,6));car.snapTo(at.getX(),at.getY(),at.getZ(),90,0);city.addFreshEntity(car);
            try{ps.forEach(p->{p.startRiding(car,false,false);p.setLastClientInput(new Input(false,false,false,false,true,false,false));});car.tick();h.assertTrue(ps.stream().allMatch(car::standing),"Space stands up each passenger");
                ps.forEach(p->p.setLastClientInput(Input.EMPTY));car.tick();var front=ps.stream().max(Comparator.comparingDouble(p->car.passengerLocation(p).z)).orElseThrow();var rear=ps.stream().filter(p->p!=front).findFirst().orElseThrow();
                rear.setLastClientInput(new Input(true,false,false,false,false,false,false));for(int t=0;t<20;t++)car.tick();h.assertTrue(car.passengerLocation(front).z-car.passengerLocation(rear).z>=.65-.001,"A passenger cannot walk through someone standing ahead");rear.setLastClientInput(Input.EMPTY);
                double before=car.passengerLocation(front).z;front.setLastClientInput(new Input(true,false,false,false,false,false,false));for(int t=0;t<50;t++)car.tick();
                h.assertTrue(car.passengerLocation(front).z>before&&car.passengerLocation(front).z<=VehicleCabin.layout(1).aisleEnd(),"W moves along a free bounded aisle, never through the cab wall");h.assertTrue(ps.stream().allMatch(p->p.getVehicle()==car),"Moving through the cabin retains the vehicle attachment");
                ps.getFirst().setLastClientInput(new Input(false,false,false,false,true,false,false));car.tick();h.assertTrue(!car.standing(ps.getFirst())&&car.standing(ps.get(1)),"Seat toggles remain independent per passenger");
            }finally{ps.forEach(p->{p.setLastClientInput(Input.EMPTY);p.stopRiding();});car.discard();}
        });h.succeed();
    }
    @GameTest public void metroJunctionClearanceFitsRotatingCoachesAndPreservesPlayerBlocks(GameTestHelper h){
        var city=h.getLevel();var line=CityNetwork.line("M6");
        var corner=CityNetwork.plan(line).frames().stream().filter(f->f.from().distanceTo(f.to())>30&&CityNetwork.stops().stream().allMatch(s->Math.hypot(f.to().x-s.street().getX(),f.to().z-s.street().getZ())>20)).findFirst().orElseThrow().to();
        var at=BlockPos.containing(corner);for(int x=(at.getX()-10)>>4;x<=(at.getX()+10)>>4;x++)for(int z=(at.getZ()-10)>>4;z<=(at.getZ()+10)>>4;z++)CityRailway.fit(city,city.getChunk(x,z));
        var car=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);car.configureNetwork("",3);car.setPos(corner);
        for(float yaw:new float[]{0,45,90,135}){car.setYRot(yaw);h.assertTrue(!city.getBlockCollisions(car,car.getBoundingBox()).iterator().hasNext(),"The full coach fits the actual excavated junction at "+yaw+" degrees");}
        var protectedAt=at.above();var shaft=at.above(5);var chunk=city.getChunkAt(protectedAt);var old=city.getBlockState(protectedAt);var oldShaft=city.getBlockState(shaft);boolean marked=CityProperty.playerPlaced(city,protectedAt);
        try{city.setBlock(protectedAt,net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),2);CityProperty.placed(city,protectedAt);city.setBlock(shaft,net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState(),2);chunk.removeAttached(CityRailway.COACH_CLEARANCE);CityRailway.repairCoachClearance(city,chunk);h.assertTrue(city.getBlockState(protectedAt).is(net.minecraft.world.level.block.Blocks.STONE_BRICKS),"Fitting larger rolling stock cannot erase player construction");h.assertTrue(city.getBlockState(shaft).is(net.minecraft.world.level.block.Blocks.LADDER),"Clearance repairs must preserve existing station shaft exits through the ceiling");}
        finally{city.setBlock(protectedAt,old,2);city.setBlock(shaft,oldShaft,2);if(!marked)CityProperty.forget(city,protectedAt);}
        h.succeed();
    }
    @GameTest public void metroDimensionsAndKindSurviveActualEntitySerialization(GameTestHelper h){
        var city=h.getLevel();var car=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);car.configureNetwork("",3);
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,city.registryAccess());car.saveWithoutId(out);var restored=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);restored.load(TagValueInput.create(ProblemReporter.DISCARDING,city.registryAccess(),out.buildResult()));
        h.assertTrue(restored.kind()==3&&restored.getBbHeight()>3.3&&restored.getBbWidth()>2.7,"Reloading a metro cannot turn it into a smaller tram");
        restored.setYRot(90);var bounds=restored.getBoundingBox();h.assertTrue(bounds.getXsize()>11.9&&bounds.getZsize()<2.8,"The physical body rotates with the twelve metre carriage");h.succeed();
    }
}
