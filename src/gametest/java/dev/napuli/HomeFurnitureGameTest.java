package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import static dev.napuli.NapoliHouseholds.Activity;

public final class HomeFurnitureGameTest {
    private record Fixture(ServerLevel city,NapoliMergellina.House home,NapoliResident npc,NapoliCivicLife.Citizen citizen) {}
    private static Fixture fixture(GameTestHelper h){
        var city=h.getLevel();var origin=h.absolutePos(new BlockPos(0,1,0));
        var home=new NapoliMergellina.House("home-use-test","Casa prova",origin.getX(),origin.getZ(),0,Blocks.BRICKS,17,18,2,0);
        for(int y:new int[]{63,68})for(var p:BlockPos.betweenClosed(home.x(),y,home.z(),home.x()+16,y,home.z()+17))city.setBlock(p,Blocks.SPRUCE_PLANKS.defaultBlockState(),2);
        var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,home.inside(),0);npc.setNoAi(true);
        var c=new NapoliCivicLife.Citizen(npc.getUUID(),"Ciro",false,-250,50,-1,"",home.id(),"",true);
        CitySimulation.put(city,c.id(),CitySimulation.fresh(c.id()));
        h.runBeforeTestEnd(()->{CityHomeUse.cancel(npc,city);if(npc.isSleeping())npc.stopSleeping();npc.discard();var all=new HashMap<>(city.getAttachedOrElse(CitySimulation.LIFE,Map.of()));all.remove(c.id().toString());city.setAttached(CitySimulation.LIFE,Map.copyOf(all));});
        return new Fixture(city,home,npc,c);
    }
    private static void at(NapoliResident npc,BlockPos p){npc.snapTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);}
    private static BlockPos place(Fixture f,CityLivingHomes.Furniture kind,Block block){var p=CityLivingHomes.use(f.home(),kind).block();f.city().setBlock(p,block.defaultBlockState(),2);return p;}
    private static void needs(Fixture f,int hygiene,int bladder){var l=CitySimulation.life(f.city(),f.citizen().id());CitySimulation.put(f.city(),f.citizen().id(),l.withNeeds(new CitySimulation.Needs(80,80,hygiene,30,50,bladder),l.food(),l.last(),l.water(),l.energy()));}
    @GameTest(structure="napuli:police_arena",maxTicks=250) public void toiletRequiresArrivalAndHandwashingBeforeCompletion(GameTestHelper h){
        var f=fixture(h);needs(f,70,5);place(f,CityLivingHomes.Furniture.TOILET,NapoliFurniture.WC);place(f,CityLivingHomes.Furniture.SINK,NapoliFurniture.SINK);
        at(f.npc(),f.home().inside());CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.WASH);
        for(int tick:new int[]{40,80,120})h.runAtTickTime(tick,()->{at(f.npc(),CityLivingHomes.use(f.home(),CityLivingHomes.Furniture.TOILET).stand());CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.WASH);});
        h.runAtTickTime(125,()->h.assertTrue(CitySimulation.life(f.city(),f.citizen().id()).needs().bladder()==5,"Walking and part of the toilet action cannot complete a wash"));
        h.runAtTickTime(160,()->{CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.WASH);CityMovement.stand(f.npc());at(f.npc(),CityLivingHomes.use(f.home(),CityLivingHomes.Furniture.SINK).stand());CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.WASH);});
        h.runAtTickTime(175,()->h.assertTrue(CitySimulation.life(f.city(),f.citizen().id()).needs().bladder()==5,"Handwashing also requires actual time at the sink"));
        h.runAtTickTime(200,()->{CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.WASH);var life=CitySimulation.life(f.city(),f.citizen().id());h.assertTrue(life.needs().bladder()==90&&life.needs().hygiene()==85&&life.water()==1,"The completed toilet and handwash restore the correct needs and consume water once: "+life.needs()+" / water="+life.water()+" / position="+f.npc().blockPosition());});
        h.runAtTickTime(240,()->{CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.WASH);h.assertTrue(CitySimulation.life(f.city(),f.citizen().id()).water()==1,"A completed routine cannot repeat its effect on the next decision");h.succeed();});
    }
    @GameTest(structure="napuli:police_arena") public void anOccupiedBathroomWaitsOutsideAndReleasesOnActivityChange(GameTestHelper h){
        var f=fixture(h);needs(f,80,5);place(f,CityLivingHomes.Furniture.TOILET,NapoliFurniture.WC);
        var other=NapoliResident.spawn(f.city(),NapoliResident.Role.CIVIC_MAN,f.home().inside(),0);other.setNoAi(true);
        var c=new NapoliCivicLife.Citizen(other.getUUID(),"Anna",true,-250,50,-1,"",f.home().id(),"",true);
        try{at(f.npc(),CityLivingHomes.use(f.home(),CityLivingHomes.Furniture.TOILET).stand());CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.WASH);
            CityHomeUse.tick(other,f.city(),c,f.home(),Activity.WASH);h.assertTrue(other.activity()==Activity.WAIT,"Another resident waits instead of sharing the occupied bathroom");
            var resource=CityLivingHomes.use(f.home(),CityLivingHomes.Furniture.TOILET).block();CityHomeUse.prepare(f.npc(),f.city(),Activity.WORK);
            h.assertTrue(CityMovement.acquire(f.city(),resource,other.getUUID(),240),"Changing activity immediately releases bathroom admission");
        }finally{CityHomeUse.cancel(other,f.city());CityMovement.release(f.city(),other.getUUID());other.discard();}h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void aBusySofaUsesAnotherSeatAndReadingRequiresTheDesk(GameTestHelper h){
        var f=fixture(h);place(f,CityLivingHomes.Furniture.SOFA,NapoliFurniture.DIVANO);
        var alternative=new BlockPos(f.home().x()+4,64,f.home().z()+8);f.city().setBlock(alternative,NapoliFurniture.POLTRONA.defaultBlockState(),2);
        var occupant=NapoliResident.spawn(f.city(),NapoliResident.Role.CIVIC_MAN,alternative,0);occupant.setNoAi(true);
        try{var sofa=CityLivingHomes.use(f.home(),CityLivingHomes.Furniture.SOFA).block();CityMovement.sit(occupant,sofa);
            at(f.npc(),alternative.east());CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.REST);
            h.assertTrue(CityMovement.isSeat(f.npc().getVehicle())&&f.npc().getVehicle().blockPosition().equals(alternative),"The free armchair is used while the sofa is occupied");
            CityHomeUse.prepare(f.npc(),f.city(),Activity.STUDY);var desk=place(f,CityLivingHomes.Furniture.DESK,NapoliFurniture.WORKTOP);
            at(f.npc(),CityLivingHomes.use(f.home(),CityLivingHomes.Furniture.DESK).stand());CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.STUDY);
            h.assertTrue(f.npc().gesture()==CityGestures.Gesture.WRITE&&f.npc().getMainHandItem().is(net.minecraft.world.item.Items.BOOK),"Studying at the actual desk holds a book and uses the writing gesture");
            f.city().setBlock(desk,Blocks.AIR.defaultBlockState(),2);CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.STUDY);
            h.assertTrue(f.npc().activity()==Activity.WAIT,"A demolished desk cannot still be used");
        }finally{CityMovement.stand(occupant);occupant.discard();}h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void furnitureCannotBeUsedThroughAWallOrFromTheFloorBelow(GameTestHelper h){
        var f=fixture(h);place(f,CityLivingHomes.Furniture.TOILET,NapoliFurniture.WC);var station=CityHomeStations.fixture(f.home(),CityLivingHomes.Furniture.TOILET);
        at(f.npc(),station.stand());h.assertTrue(CityHomeStations.reached(f.npc(),station),"The clear approach can use the fixture");
        at(f.npc(),station.stand().below(5));h.assertTrue(!CityHomeStations.reached(f.npc(),station),"The same x/z below a room cannot use its furniture");
        at(f.npc(),station.stand().west());f.city().setBlock(station.stand(),Blocks.STONE.defaultBlockState(),2);f.city().setBlock(station.stand().above(),Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(!CityHomeStations.reached(f.npc(),station),"A solid wall blocks interaction even at short range");h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void incompleteBedsAreSkippedAndResidentsWakeForTheirNextActivity(GameTestHelper h){
        var f=fixture(h);var bed=CityHomeStations.preferredBed(f.home(),f.citizen(),NapoliBroadcast.day(f.city()));
        var state=Blocks.BED.blue().defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);f.city().setBlock(bed,state.setValue(BedBlock.PART,BedPart.FOOT),2);
        h.assertTrue(CityBeds.choose(f.npc(),f.city(),f.home(),bed)==null,"A missing head means the bed is not usable");
        f.city().setBlock(bed.north(),state.setValue(BedBlock.PART,BedPart.HEAD),2);at(f.npc(),bed.south());CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.SLEEP);
        h.assertTrue(f.npc().isSleeping(),"The resident sleeps after reaching the complete bed");CityHomeUse.prepare(f.npc(),f.city(),Activity.WORK);
        h.assertTrue(!f.npc().isSleeping(),"Leaving the sleep routine wakes the resident before commuting");h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void theAssociatedDeskChairIsAUsableApproach(GameTestHelper h){
        var f=fixture(h);var desk=place(f,CityLivingHomes.Furniture.DESK,NapoliFurniture.WORKTOP);var chair=desk.south();
        f.city().setBlock(chair,NapoliFurniture.SEDIA_SCRIVANIA.defaultBlockState(),2);
        var station=new CityHomeStations.Station(CityLivingHomes.Furniture.DESK,desk,desk.south(2),chair);at(f.npc(),station.stand());
        h.assertTrue(CityHomeStations.valid(f.city(),station)&&CityHomeStations.reached(f.npc(),station),"A resident may sit down even when the chair back intersects the view of its associated desk");
        f.city().setBlock(desk,Blocks.AIR.defaultBlockState(),2);h.assertTrue(!CityHomeStations.valid(f.city(),station),"The chair alone does not replace a missing desk");h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void steppingOntoTheSofaEdgeStillAllowsSitting(GameTestHelper h){
        var f=fixture(h);var sofa=place(f,CityLivingHomes.Furniture.SOFA,NapoliFurniture.DIVANO);var point=CityLivingHomes.use(f.home(),CityLivingHomes.Furniture.SOFA);
        var station=new CityHomeStations.Station(CityLivingHomes.Furniture.SOFA,sofa,point.stand(),sofa);
        f.npc().snapTo(point.stand().getX()+.5,point.stand().getY()+15.0/16,point.stand().getZ()+.8,0,0);
        h.assertTrue(CityHomeStations.reached(f.npc(),station),"The step height of the actual sofa must not deadlock its arrival check");
        at(f.npc(),point.stand().below(4));h.assertTrue(!CityHomeStations.reached(f.npc(),station),"Furniture on another storey remains out of reach");h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void anUpstairsResidentDoesNotReserveTheBathroomBeforeComingDown(GameTestHelper h){
        var f=fixture(h);needs(f,80,5);var toilet=place(f,CityLivingHomes.Furniture.TOILET,NapoliFurniture.WC);
        at(f.npc(),CityLivingHomes.use(f.home(),CityLivingHomes.Furniture.BED).stand());CityHomeUse.tick(f.npc(),f.city(),f.citizen(),f.home(),Activity.WASH);
        var other=NapoliResident.spawn(f.city(),NapoliResident.Role.CIVIC_WOMAN,f.home().inside(),0);other.setNoAi(true);
        try{h.assertTrue(CityMovement.acquire(f.city(),toilet,other.getUUID(),240),"The resident already downstairs can use the bathroom while another walks down");}
        finally{CityMovement.release(f.city(),other.getUUID());other.discard();}h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void visibleResidentsDoNotRecoverHygieneOrSleepByMerelyWaiting(GameTestHelper h){
        var f=fixture(h);var old=f.city().getAttached(NapoliCivicLife.CENSUS);int interval=CityLifeConfig.settings.needsIntervalTicks;
        try{f.city().setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(f.city()),List.of(f.citizen())));
            var life=CitySimulation.fresh(f.citizen().id());long due=f.city().getGameTime()-interval;
            CitySimulation.put(f.city(),f.citizen().id(),life.withNeeds(new CitySimulation.Needs(80,80,5,50,50,5),8,due,0,0));CitySimulation.update(f.city());
            var washed=CitySimulation.life(f.city(),f.citizen().id());h.assertTrue(washed.needs().hygiene()<5&&washed.water()==0,"A visible resident cannot wash without using the bathroom");
            CitySimulation.put(f.city(),f.citizen().id(),life.withNeeds(new CitySimulation.Needs(80,5,80,50,50,80),8,due,0,0));CitySimulation.update(f.city());
            h.assertTrue(CitySimulation.life(f.city(),f.citizen().id()).needs().sleep()<5,"An awake resident cannot recover sleep merely because the schedule says sleep");
        }finally{if(old==null)f.city().removeAttached(NapoliCivicLife.CENSUS);else f.city().setAttached(NapoliCivicLife.CENSUS,old);}h.succeed();
    }
}
