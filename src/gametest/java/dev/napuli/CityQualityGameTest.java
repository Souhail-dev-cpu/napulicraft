package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;

public final class CityQualityGameTest {
    @GameTest(structure="napuli:police_arena") public void aWalkerRequestsOnlyTheNearestDoorOnItsRoute(GameTestHelper h){
        var city=h.getLevel();var at=h.absolutePos(new BlockPos(8,1,8));var first=at.north();var second=first.east();
        for(var p:List.of(at,first,second))city.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);
        for(var p:List.of(first,second)){var state=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH);city.setBlock(p,state.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);city.setBlock(p.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);}
        var a=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,at,0);var b=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,at.east(4),0);a.setNoAi(true);b.setNoAi(true);
        try{h.assertTrue(CityMovement.beforeMove(a,at.north(6)),"The route through the nearest door is available");
            h.assertTrue(city.getBlockState(first).getValue(DoorBlock.OPEN)&&!city.getBlockState(second).getValue(DoorBlock.OPEN),"Walking through one doorway does not open its neighbour");
            h.assertTrue(CityMovement.acquire(city,second,b.getUUID(),60),"An adjacent doorway remains available to another resident");
        }finally{CityMovement.release(city,a.getUUID());CityMovement.release(city,b.getUUID());a.discard();b.discard();}h.succeed();
    }
    @GameTest public void carpetedRoomsRemainAvailableToResidentsAndAccessChecks(GameTestHelper h){
        var city=h.getLevel();var from=h.absolutePos(new BlockPos(1,2,1));var to=from.east();
        for(var p:List.of(from,to)){city.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);city.setBlock(p,CityBlocks.vanilla("blue_carpet").defaultBlockState(),2);city.setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);}
        h.assertTrue(NapoliResidentHomes.walkable(city,to)&&CityAccessibility.connected(city,from,to),"A thin carpet is a usable floor, not an obstruction");
        city.setBlock(to.above(),Blocks.STONE.defaultBlockState(),2);h.assertTrue(!NapoliResidentHomes.walkable(city,to)&&!CityAccessibility.walkable(city,to),"The same room still needs head clearance");h.succeed();
    }
    @GameTest public void authoredVanillaMaterialsResolveToRealBlocks(GameTestHelper h)throws Exception{
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/city-block-ids.txt"))){
            for(var id:new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().filter(s->!s.isBlank()).toList())
                h.assertTrue(CityBlocks.vanilla(id)!=Blocks.AIR,"Authored material must exist: "+id);
        }
        boolean rejected=false;try{CityBlocks.vanilla("napuli_missing_material");}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected,"A typo must fail explicitly instead of silently creating air");h.succeed();
    }
    @GameTest public void addressLookupIncludesEveryHomeBoundaryAndRejectsOutsidePositions(GameTestHelper h){
        for(var home:CityLivingHomes.all()){
            h.assertTrue(CityLivingHomes.find(home.id())==home,"Address lookup preserves the canonical home");
            for(var p:List.of(new BlockPos(home.x(),home.ground(),home.z()),new BlockPos(home.x()+home.width()-1,home.ground()+5,home.z()+home.depth()-1),home.inside(),home.entrance(),home.inside().above(50))){
                var expected=CityLivingHomes.all().stream().filter(a->a.contains(p)).findFirst().orElse(null);
                h.assertTrue(Objects.equals(CityLivingHomes.at(p),expected),"Spatial lookup agrees at boundary "+home.id()+" / "+p);
            }
        }h.succeed();
    }
    @GameTest public void residentsCannotAttendWorkBeforeTheirFirstJourney(GameTestHelper h){
        var city=h.getLevel();var c=new NapoliCivicLife.Citizen(UUID.randomUUID(),"Ciro",false,-200,100,0,"","public_0","",false);
        var home=NapoliCivicLife.home(c);
        h.assertTrue(CityJourneys.at(city,c,home,3),"A resident without a journey starts at home");
        h.assertTrue(!CityJourneys.at(city,c,home.east(100),3),"An unobserved resident cannot already be at a distant workplace");h.succeed();
    }
    @GameTest public void journeyBatchReadsItsChangesAndCommitsNestedUpdatesTogether(GameTestHelper h){
        var city=h.getLevel();var previous=city.removeAttached(CityJourneys.JOURNEYS);var a=UUID.randomUUID();var b=UUID.randomUUID();
        var journey=CityJourneys.plan(new BlockPos(-530,64,80),new BlockPos(-500,64,94),city.getGameTime(),80);
        try{
            CityJourneys.batch(city,()->{CityJourneys.put(city,a,journey);h.assertTrue(CityJourneys.get(city,a).equals(journey),"The next simulated decision sees the pending movement");
                h.assertTrue(city.getAttachedOrElse(CityJourneys.JOURNEYS,Map.of()).isEmpty(),"The persistent census is copied once per batch");
                CityJourneys.batch(city,()->CityJourneys.put(city,b,journey));});
            h.assertTrue(CityJourneys.get(city,a).equals(journey)&&CityJourneys.get(city,b).equals(journey),"Both nested updates survive the commit");
        }finally{if(previous==null)city.removeAttached(CityJourneys.JOURNEYS);else city.setAttached(CityJourneys.JOURNEYS,previous);}h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void siblingsChooseSeparateRealBedsBeforeUsingTheParentsRoom(GameTestHelper h){
        var city=h.getLevel();var p=h.absolutePos(new BlockPos(1,1,1));var home=new NapoliMergellina.House("quality_beds","Letti",p.getX(),p.getZ(),0,Blocks.BRICKS,17,18,2,0);
        var child=CityLivingHomes.points(home).stream().filter(a->a.room()==CityLivingHomes.Room.CHILD_BEDROOM&&a.furniture()==CityLivingHomes.Furniture.BED).findFirst().orElseThrow().block();
        for(var point:CityLivingHomes.points(home))if(point.furniture()==CityLivingHomes.Furniture.BED)for(var foot:List.of(point.block(),point.block().east())){
            city.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),2);city.setBlock(foot.north().below(),Blocks.STONE.defaultBlockState(),2);
            var state=Blocks.BED.blue().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
            city.setBlock(foot,state.setValue(BedBlock.PART,BedPart.FOOT),2);city.setBlock(foot.north(),state.setValue(BedBlock.PART,BedPart.HEAD),2);
        }
        var a=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,child,0);var b=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,child.east(),0);a.setNoAi(true);b.setNoAi(true);
        try{a.startSleeping(child);h.assertTrue(a.isSleeping(),"The first sibling occupies a real bed");
            h.assertTrue(child.east().equals(CityBeds.choose(b,city,home,child)),"The other child uses the second bed in the same room");
            b.startSleeping(child.east());h.assertTrue(!Objects.equals(CityBeds.choose(a,city,home,child),CityBeds.choose(b,city,home,child)),"Existing sleepers retain distinct beds");
        }finally{a.stopSleeping();b.stopSleeping();a.discard();b.discard();}h.succeed();
    }
    @GameTest public void releasingADoorDoesNotReleaseTheSameResidentsKitchenStation(GameTestHelper h){
        HousingCapacityGameTest.withPlayers(h,2,players->{var city=h.getLevel();var a=players.get(0).getUUID();var b=players.get(1).getUUID();var door=h.absolutePos(new BlockPos(1,1,1));var fridge=door.east();
            try{h.assertTrue(CityMovement.acquire(city,door,a,60)&&CityMovement.acquire(city,fridge,a,60),"A resident may use a door while holding a kitchen station");
                h.assertTrue(!CityMovement.acquire(city,door,b,60),"The second actor waits its turn");CityMovement.release(city,door,a);
                h.assertTrue(CityMovement.acquire(city,door,b,60)&&!CityMovement.acquire(city,fridge,b,60),"Only the released doorway becomes available");
            }finally{CityMovement.release(city,a);CityMovement.release(city,b);}});h.succeed();
    }
    @GameTest public void parkedAndDepartingVehiclesDoNotBlockPedestrians(GameTestHelper h){
        var from=new Vec3(0,64,0);var crossing=new Vec3(5,64,0);
        h.assertTrue(!CityMovement.approaching(from,Vec3.ZERO,crossing),"A parked taxi does not stop pedestrians indefinitely");
        h.assertTrue(!CityMovement.approaching(from,new Vec3(-.2,0,0),crossing),"A departing vehicle does not block a crossing behind it");
        h.assertTrue(CityMovement.approaching(from,new Vec3(.2,0,0),crossing),"A car approaching the crossing still has priority");h.succeed();
    }
    @GameTest public void improvedHomesKeepFurnitureClearAndBalconiesAccessible(GameTestHelper h){
        var city=h.getLevel();for(var home:CityLivingHomes.PUBLIC_HOMES.subList(0,4)){
            CityLivingHomes.buildHouse(city,home);
            for(var point:CityLivingHomes.points(home))h.assertTrue(NapoliResidentHomes.walkable(city,point.stand()),"Usable furniture after renovation: "+home.id()+" "+point.furniture()+" "+point.stand());
            var balcony=new BlockPos(home.x()+home.width()/2,home.ground()+5,home.z()+home.depth()-1);
            h.assertTrue(city.getBlockState(balcony).getBlock() instanceof DoorBlock,"An upper-floor door leads to the balcony");
            h.assertTrue(CityAccessibility.connected(city,balcony.north(),balcony.south()),"Balcony floor and doorway connect physically");
            h.assertTrue(city.getBlockState(new BlockPos(home.x()+1,CityArchitecture.roof(home),home.z()+1)).getBlock() instanceof TrapDoorBlock,"The roof ladder has a usable hatch");
            h.assertTrue(city.getBlockState(new BlockPos(home.x()+3,home.ground()-1,home.z()+3)).is(CityMaterials.MAJOLICA),"Kitchen ceramic flooring exists");
        }h.succeed();
    }
    @GameTest public void renovationPreservesStoredItemsAndPlayerChanges(GameTestHelper h){
        var city=h.getLevel();var home=CityLivingHomes.PUBLIC_HOMES.get(5);CityLivingHomes.buildHouse(city,home);var fridge=CityLivingHomes.use(home,CityLivingHomes.Furniture.FRIDGE).block();
        var storage=(net.minecraft.world.Container)city.getBlockEntity(fridge);storage.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
        var custom=new BlockPos(home.x(),home.ground()+2,home.z()+4);city.setBlock(custom,Blocks.EMERALD_BLOCK.defaultBlockState(),2);CityProperty.placed(city,custom);
        try{CityArchitecture.finishNew(city,home);h.assertTrue(storage.getItem(0).getCount()==3,"Renovation retains the exact container and contents");
            h.assertTrue(city.getBlockState(custom).is(Blocks.EMERALD_BLOCK),"A player wall replacement remains intact");
        }finally{CityProperty.forget(city,custom);storage.clearContent();}h.succeed();
    }
}
