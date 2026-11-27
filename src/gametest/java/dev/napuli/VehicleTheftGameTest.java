package dev.napuli;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public final class VehicleTheftGameTest {
    @GameTest(structure="napuli:police_arena") public void parkedCarsAndScootersBecomePlayerDrivenWithoutChangingTheirIdentity(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,2,players->{for(int i=0;i<2;i++){var at=h.absolutePos(new BlockPos(5,1,5));var p=players.get(i);p.setPos(at.getX()+2.5,at.getY(),at.getZ()+.5);var c=person(city,UUID.randomUUID());var vehicle=CityMobility.create(city,c,i==0?"car":"scooter",at);h.assertTrue(vehicle!=null,"The NPC's actual parked vehicle exists");var id=vehicle.getUUID();
                try{h.assertTrue(VehicleTheft.take(p,vehicle)&&vehicle.getControllingPassenger()==p,"The player takes control of the "+(i==0?"car":"scooter"));h.assertTrue(vehicle.getUUID().equals(id)&&VehicleTheft.missing(city,c.id()),"The same entity is retained and the owner records the loss");h.assertTrue(!Carabinieri.isWanted(p),"An unwitnessed theft cannot randomly summon the police");h.assertTrue(CityMobility.create(city,c,i==0?"car":"scooter",at)==null,"The original owner cannot create a second copy");p.stopRiding();h.assertTrue(vehicle.shouldBeSaved(),"A parked stolen vehicle is saved normally; occupied ones follow the player's vanilla RootVehicle save");}
                finally{p.stopRiding();vehicle.discard();}
            }}));h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void takingAnOccupiedVehicleReleasesTheNpcAndCancelsItsOldCommute(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,ps->{var at=h.absolutePos(new BlockPos(5,1,5));var p=ps.getFirst();p.setPos(at.getX()+2.5,at.getY(),at.getZ()+.5);var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,at,0);npc.setNoAi(true);var c=person(city,npc.getUUID());var car=CityMobility.create(city,c,"car",at);h.assertTrue(car!=null&&npc.startRiding(car),"The actual owner is in the driver's seat");city.setAttached(CityMobility.RIDES,Map.of(c.id().toString(),new CityMobility.Ride("car",new CityJourneys.Journey(List.of(at,at.south(80)),0,400,at.south(80),0),1,at.south(80))));
            try{h.assertTrue(VehicleTheft.take(p,car)&&p.getVehicle()==car&&!npc.isPassenger()&&npc.isAlive()&&npc.getHealth()==20,"The player takes the seat while the owner gets out alive nearby");h.assertTrue(!CityMobility.inTransit(city,c.id())&&VehicleTheft.missing(city,c.id())&&npc.distanceToSqr(car)<10*10,"The old AI journey is released without teleporting the owner across town");h.assertTrue(car.getAttached(VehicleTheft.STOLEN).robbery()&&car.getAttached(VehicleTheft.STOLEN).witnessed(),"The ejected owner knows about the robbery");}
            finally{p.stopRiding();npc.discard();car.discard();}
        }));h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void witnessesNeedToBeAwakeFacingTheTheftAndAbleToSeeBothActors(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,ps->{var at=h.absolutePos(new BlockPos(5,1,4));var p=ps.getFirst();p.setPos(at.getX()+2.5,at.getY(),at.getZ()+.5);var car=CityMobility.create(city,person(city,UUID.randomUUID()),"car",at);var witness=NapoliResident.spawn(city,NapoliResident.Role.PESCATORE,h.absolutePos(new BlockPos(5,1,12)),180);witness.setNoAi(true);witness.setYHeadRot(180);
            try{h.assertTrue(VehicleTheft.sees(witness,p,car),"A nearby observer facing the theft can report it");witness.setYHeadRot(0);h.assertTrue(!VehicleTheft.sees(witness,p,car),"An observer looking away cannot see through the back of their head");witness.setYHeadRot(180);for(int x=2;x<=11;x++)for(int y=1;y<=4;y++)h.setBlock(x,y,8,Blocks.STONE);h.assertTrue(!VehicleTheft.sees(witness,p,car),"A solid wall prevents a remote magical report");witness.setHealth(0);h.assertTrue(!VehicleTheft.sees(witness,p,car),"A dead NPC cannot make a call");}
            finally{witness.discard();car.discard();}
        }));h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void fastVehiclesRejectTheftWithoutEvictingTheOwnerOrChangingOwnership(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,ps->{var at=h.absolutePos(new BlockPos(5,1,5));var p=ps.getFirst();p.setPos(at.getX()+2.5,at.getY(),at.getZ()+.5);var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,at,0);npc.setNoAi(true);var c=person(city,npc.getUUID());var car=CityMobility.create(city,c,"car",at);h.assertTrue(car!=null&&npc.startRiding(car),"The moving fixture has its owner onboard");car.setDeltaMovement(new Vec3(0,0,.5));
            try{h.assertTrue(!VehicleTheft.take(p,car)&&npc.getVehicle()==car&&!p.isPassenger()&&!VehicleTheft.taken(car)&&!VehicleTheft.missing(city,c.id()),"An unsafe attempt cannot partially commit a theft");car.setDeltaMovement(Vec3.ZERO);p.setPos(at.getX()+20,at.getY(),at.getZ());h.assertTrue(!VehicleTheft.take(p,car)&&npc.getVehicle()==car,"A forged distant interaction cannot evict the driver");}
            finally{npc.stopRiding();npc.discard();car.discard();}
        }));h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void stolenVehicleDataAndManualControlSurviveEntitySaving(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,ps->{var at=h.absolutePos(new BlockPos(5,1,5));var p=ps.getFirst();p.setPos(at.getX()+2.5,at.getY(),at.getZ()+.5);var c=person(city,UUID.randomUUID());var car=CityMobility.create(city,c,"car",at);
            try{h.assertTrue(VehicleTheft.take(p,car),"The car is taken before saving");var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,city.registryAccess());car.saveWithoutId(out);var restored=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);h.assertTrue(restored!=null,"The saved car can be reconstructed");restored.load(TagValueInput.create(ProblemReporter.DISCARDING,city.registryAccess(),out.buildResult()));h.assertTrue(restored.stolenVehicle()&&restored.shouldBeSaved()&&restored.getAttached(VehicleTheft.STOLEN).owner().equals(c.id()),"The manual-control flag and original owner survive actual entity serialization");var record=city.getServer().overworld().getAttached(VehicleTheft.MISSING).get(c.id().toString());var ops=RegistryOps.create(JsonOps.INSTANCE,city.registryAccess());h.assertTrue(VehicleTheft.Missing.CODEC.parse(ops,VehicleTheft.Missing.CODEC.encodeStart(ops,record).getOrThrow()).getOrThrow().equals(record),"The authoritative missing-vehicle record survives a restart");h.assertTrue(!CityMobility.drive(restored,city),"Reloading cannot restart the original NPC autopilot");}
            finally{p.stopRiding();car.discard();}
        }));h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void stolenScootersCannotBecomeLegalGarageTokens(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,ps->{var at=h.absolutePos(new BlockPos(5,1,5));var p=ps.getFirst();p.setPos(at.getX()+2.5,at.getY(),at.getZ()+.5);var bike=(Motorino)CityMobility.create(city,person(city,UUID.randomUUID()),"scooter",at);
            try{h.assertTrue(VehicleTheft.take(p,bike)&&ScooterGarage.observe(bike,p)&&ScooterGarage.get(city,bike.getUUID())==null,"Driving a stolen scooter does not register a legal owner");p.stopRiding();h.assertTrue(!ScooterGarage.pickup(p,bike)&&ScooterGarage.get(city,bike.getUUID())==null,"Picking up cannot mint a legal receipt for the stolen vehicle");h.assertTrue(p.getInventory().getNonEquipmentItems().stream().noneMatch(i->i.is(Napuli.MOTORINO_ITEM)),"The theft creates no extra scooter item");}
            finally{p.stopRiding();bike.discard();}
        }));h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void takingAPoliceVehicleDetachesItsPursuitController(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,ps->{var at=h.absolutePos(new BlockPos(5,1,5));var p=ps.getFirst();p.setPos(at.getX()+2.5,at.getY(),at.getZ()+.5);var officer=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,at,0);var car=PoliceVehicles.create(city,officer,"car");h.assertTrue(car!=null&&officer.startRiding(car),"The officer starts in its actual patrol car");PoliceVehicles.course(car,p,at.south(50),city.getGameTime(),List.of(at,at.south(50)));
            try{h.assertTrue(VehicleTheft.take(p,car)&&!officer.isPassenger()&&p.getVehicle()==car,"The stopped patrol car can be taken from its driver");PoliceVehicles.finish(p);h.assertTrue(p.getVehicle()==car&&car.isAlive()&&!PoliceVehicles.drive(car,city),"Ending the old pursuit cannot erase or remotely drive the stolen car");}
            finally{p.stopRiding();officer.discard();car.discard();}
        }));h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void destroyedVehiclesReleaseTheirOwnerWithoutReappearingInTheMissingRegistry(GameTestHelper h){
        isolated(h,city->HousingCapacityGameTest.withPlayers(h,1,ps->{var at=h.absolutePos(new BlockPos(5,1,5));var p=ps.getFirst();p.setPos(at.getX()+2.5,at.getY(),at.getZ()+.5);var c=person(city,UUID.randomUUID());var car=CityMobility.create(city,c,"car",at);
            try{h.assertTrue(VehicleTheft.take(p,car),"The stolen car belongs to an actual NPC identity");p.stopRiding();car.hurtServer(city,city.damageSources().generic(),1000);h.assertTrue(!VehicleTheft.missing(city,c.id()),"Destroying the stolen car releases the original owner's missing-vehicle record");VehicleTheft.tick(car,city);car.discard();h.assertTrue(!VehicleTheft.missing(city,c.id()),"Death and subsequent unload cannot register the destroyed vehicle again");}
            finally{p.stopRiding();car.discard();}
        }));h.succeed();
    }
    @GameTest public void oldComplaintSavesKeepTheirOriginalOffenceAndNewReportsKeepTheftSeverity(GameTestHelper h){
        var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());var old=new CityResidentSafety.Complaint(UUID.randomUUID(),BlockPos.ZERO,60,600,false);var json=CityResidentSafety.Complaint.CODEC.encodeStart(ops,old).getOrThrow();json.getAsJsonObject().remove("crime");h.assertTrue(CityResidentSafety.Complaint.CODEC.parse(ops,json).getOrThrow().crime().equals("ASSAULT"),"Existing assault reports remain readable");var theft=new CityResidentSafety.Complaint(old.attacker(),old.seen(),60,600,false,"THEFT");h.assertTrue(CityResidentSafety.Complaint.CODEC.parse(ops,CityResidentSafety.Complaint.CODEC.encodeStart(ops,theft).getOrThrow()).getOrThrow().crime().equals("THEFT"),"Theft reports do not turn into assault charges after reloading");h.succeed();
    }
    private static final List<AttachmentType<?>> TYPES=List.of(NapoliCivicLife.CENSUS,CityMobility.OWNED,CityMobility.RIDES,CityResidentSafety.CALLS,VehicleTheft.MISSING);
    private static void isolated(GameTestHelper h,java.util.function.Consumer<ServerLevel> body){var city=h.getLevel();var saved=new HashMap<AttachmentType<?>,Object>();for(var t:TYPES)saved.put(t,city.removeAttached(t));floor(h);try{body.accept(city);}finally{for(var t:TYPES)restore(city,t,saved.get(t));}}
    private static NapoliCivicLife.Citizen person(ServerLevel city,UUID id){return MobilityGameTest.person(city,id,30);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void restore(ServerLevel city,AttachmentType type,Object value){if(value==null)city.removeAttached(type);else city.setAttached(type,value);}
    private static void floor(GameTestHelper h){for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,0,z,Blocks.STONE);for(int y=1;y<=5;y++)h.setBlock(x,y,z,Blocks.AIR);}}
}
