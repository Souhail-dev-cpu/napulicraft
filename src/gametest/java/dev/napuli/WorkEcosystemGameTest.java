package dev.napuli;

import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

public final class WorkEcosystemGameTest {
    @GameTest public void crowdedArrivalsReserveSeparatePlacesAndWaitWhenAllAreOccupied(GameTestHelper h){var city=h.getLevel();var center=h.absolutePos(new BlockPos(4,35,4));var reserved=new HashSet<BlockPos>();
        for(var p:BlockPos.betweenClosed(center.offset(-5,-1,-5),center.offset(5,-1,5)))city.setBlock(p,Blocks.STONE.defaultBlockState(),18);
        for(var p:BlockPos.betweenClosed(center.offset(-5,0,-5),center.offset(5,2,5)))city.setBlock(p,Blocks.AIR.defaultBlockState(),18);
        for(int i=0;i<32;i++){var at=CityJourneys.spawn(city,center,reserved);h.assertTrue(at!=null&&reserved.add(at),"Thirty-two simultaneous arrivals each reserve a different physical location");}
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=1;y++)reserved.add(center.offset(x,y,z));
        h.assertTrue(CityJourneys.spawn(city,center,reserved)==null,"A fully occupied arrival area defers materialization rather than stacking bodies");h.succeed();}
    @GameTest public void schoolArrivalsUseDistinctPlacesInsideTheClassroom(GameTestHelper h){var desks=new HashSet<BlockPos>();var origin=CityServices.place("school");
        for(int i=0;i<96;i++){var child=new NapoliCivicLife.Citizen(new UUID(0,i),"Alunno",false,-35,5,-1,"","public_0","",false);var at=CityServices.waiting(child,"school");desks.add(at);
            h.assertTrue(Math.abs(at.getX()-origin.getX())<=5&&Math.abs(at.getZ()-origin.getZ())<=4,"School waiting places stay inside the classroom");}
        h.assertTrue(desks.size()>=20,"The school spreads pupils across the classroom instead of materializing a crowd at one point");h.succeed();}
    @GameTest public void aSaleSplitsTheActualPaymentWithoutCreatingMoney(GameTestHelper h){var city=h.getLevel();var shop=DistrictLife.districts().getFirst();var ledger=DistrictShops.ledger(city,shop.id());var fund=city.getAttached(WorkEconomy.FUND);
        try{city.setAttached(WorkEconomy.FUND,100);DistrictShops.put(city,shop.id(),new DistrictShops.Ledger(ledger.goods(),20,ledger.delivered()));WorkEconomy.sale(city,shop,16);
            h.assertTrue(WorkEconomy.fund(city)==104&&DistrictShops.ledger(city,shop.id()).till()==32,"The sixteen-euro payment becomes twelve euros in the till and four in the fund");}
        finally{DistrictShops.put(city,shop.id(),ledger);if(fund==null)city.removeAttached(WorkEconomy.FUND);else city.setAttached(WorkEconomy.FUND,fund);}h.succeed();}
    @GameTest public void aConcurrentPublicBreakdownDoesNotStealAnExistingRepairOrItsMaterials(GameTestHelper h){var city=h.getLevel();var home=CityLivingHomes.PUBLIC_HOMES.getFirst();var pos=home.entrance().above(3);var id=UUID.randomUUID();
        var worker=new NapoliCivicLife.Citizen(id,"Artigiano",false,-200,100,NapoliCivicLife.Job.MECHANIC.ordinal(),"",home.id(),"",true);
        var homes=city.getAttached(CityTraces.ISSUES);var incidents=city.getAttached(CityWorkIncidents.ISSUES);var tasks=city.getAttached(CityOccupations.TASKS);var supplies=city.getAttached(CityOccupations.SUPPLIES);
        try{city.removeAttached(CityWorkIncidents.ISSUES);city.setAttached(CityTraces.ISSUES,Map.of(pos.asLong()+"",new CityTraces.Issue(pos,home.id(),0,false)));
            var task=new CityOccupations.Task("repair",1,40,2,pos.asLong()+"",0);CityOccupations.put(city,id,task);CityWorkIncidents.report(city,"M6",CityOccupations.workplace(worker).east(20));
            h.assertTrue(CityOccupations.target(city,worker).equals(home.approach(1)),"Reserved home materials keep their original destination when another service breaks");
            CityTraces.repair(city,pos);int parts=CityOccupations.supply(city,"parts"),boards=CityOccupations.supply(city,"planks");CityOccupations.repair(city,worker,task,400);
            h.assertTrue(CityOccupations.supply(city,"parts")==parts+1&&CityOccupations.supply(city,"planks")==boards+1&&CityOccupations.task(city,id).cargo()==0,"If somebody else finishes first, both reserved inputs return once");
            CityOccupations.repair(city,worker,CityOccupations.task(city,id),401);h.assertTrue(CityOccupations.supply(city,"planks")==boards+1,"Continuing work cannot refund the same board twice");}
        finally{if(homes==null)city.removeAttached(CityTraces.ISSUES);else city.setAttached(CityTraces.ISSUES,homes);if(incidents==null)city.removeAttached(CityWorkIncidents.ISSUES);else city.setAttached(CityWorkIncidents.ISSUES,incidents);if(tasks==null)city.removeAttached(CityOccupations.TASKS);else city.setAttached(CityOccupations.TASKS,tasks);if(supplies==null)city.removeAttached(CityOccupations.SUPPLIES);else city.setAttached(CityOccupations.SUPPLIES,supplies);}h.succeed();}
    @GameTest public void starterToolsAreAssignedOnceAndBelongToTheirWorker(GameTestHelper h){
        HousingCapacityGameTest.withPlayers(h,2,players->{var a=players.getFirst();var b=players.getLast();var c=NapoliEmployment.Contract.CARPENTER;
            h.assertTrue(WorkCareer.begin(a,c),"The carpenter receives an iron axe");int count=WorkResources.count(a,Items.IRON_AXE);
            h.assertTrue(WorkCareer.begin(a,c)&&WorkResources.count(a,Items.IRON_AXE)==count,"Clocking in again never duplicates the initial kit");
            var tool=a.getInventory().getItem(WorkCareer.toolSlot(a,c));h.assertTrue(WorkCareer.owned(a,c,tool)&&!WorkCareer.owned(b,c,tool),"Only its assigned worker can upgrade the tool");
            var old=WorkCareer.get(a,c);WorkCareer.put(a,c,new WorkCareer.Career(old.xp(),old.rank(),old.tier(),old.shifts(),old.earned(),old.serial()+1,true));
            h.assertTrue(!WorkCareer.owned(a,c,tool),"A recovered tool invalidates the previous assignment");
        });h.succeed();
    }
    @GameTest public void careerExperienceAndToolsRoundTripWithoutLosingProgress(GameTestHelper h){
        var state=new WorkCareer.Career(240,2,1,8,340,3,true);var json=WorkCareer.Career.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,state).getOrThrow();
        h.assertTrue(WorkCareer.Career.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow().equals(state),"Restart retains rank, earned pay, completed shifts and equipment serial");h.succeed();
    }
    @GameTest public void workFundCannotOverdrawItsBalance(GameTestHelper h){HousingCapacityGameTest.withPlayers(h,1,players->{var p=players.getFirst();var city=p.level();var old=city.getAttached(WorkEconomy.FUND);try{
        city.setAttached(WorkEconomy.FUND,10);int before=NapoliStreet.cash(p);h.assertTrue(WorkEconomy.wage(p,8)&&NapoliStreet.cash(p)==before+8,"Pay transfers money from the municipal fund");
        h.assertTrue(!WorkEconomy.wage(p,8)&&WorkEconomy.fund(city)==2&&NapoliStreet.cash(p)==before+8,"Insufficient funds preserve both balances");
        WorkEconomy.revenue(city,6);h.assertTrue(WorkEconomy.fund(city)==8,"Workshop revenue replenishes pay funding");
        }finally{if(old==null)city.removeAttached(WorkEconomy.FUND);else city.setAttached(WorkEconomy.FUND,old);}});h.succeed();}
    @GameTest public void promotionsAndUpgradesRequirePresenceRankMoneyAndMaterials(GameTestHelper h){inCity(h,players->{var p=players.getFirst();var c=NapoliEmployment.Contract.CARPENTER;select(p,c);var station=c.clock.east(5);p.level().setAttached(WorkCareer.STATIONS,Map.of(c.id(),station));move(p,station.south());
        WorkCareer.begin(p,c);WorkCareer.put(p,c,new WorkCareer.Career(200,0,0,6,100,0,true));NapoliStreet.credit(p,200-NapoliStreet.cash(p));p.getInventory().add(new ItemStack(Items.DIAMOND));
        int money=NapoliStreet.cash(p);h.assertTrue(WorkCareer.upgradeAt(p,c)==0&&NapoliStreet.cash(p)==money&&WorkResources.count(p,Items.DIAMOND)==1,"An apprentice cannot spend materials on a higher-tier tool");
        h.assertTrue(WorkCareer.promoteAt(p,c)==1&&WorkCareer.get(p,c).rank()==1,"Completed shifts and XP unlock a paid promotion");
        h.assertTrue(WorkCareer.upgradeAt(p,c)==1&&WorkCareer.get(p,c).tier()==1&&WorkResources.count(p,Items.DIAMOND)==0,"The workshop replaces the assigned iron axe with a diamond axe");
        h.assertTrue(WorkResources.count(p,Items.IRON_AXE)==0&&WorkResources.count(p,Items.DIAMOND_AXE)==1,"An upgrade does not duplicate the old equipment");
        move(p,station.south(20));int balance=NapoliStreet.cash(p);h.assertTrue(WorkCareer.promote(p)==0&&NapoliStreet.cash(p)==balance,"Remote promotion commands cannot bypass the workshop");
    });h.succeed();}
    @GameTest public void carpenterGuidanceFollowsLogsThenWorkbenchThenDelivery(GameTestHelper h){inCity(h,players->{var p=players.getFirst();var c=NapoliEmployment.Contract.CARPENTER;select(p,c);
        h.assertTrue(WorkResources.target(p,c)!=null,"Without boards the job points toward actual timber");p.getInventory().add(new ItemStack(Items.OAK_LOG));
        h.assertTrue(WorkResources.target(p,c).equals(WorkResources.bench().south()),"A harvested log changes the target to processing");move(p,WorkResources.bench().south());
        h.assertTrue(WorkResources.processPickup(p)==1&&WorkResources.count(p,Items.OAK_LOG)==0&&WorkResources.count(p,Items.OAK_PLANKS)==4,"One real log becomes four boards");
        h.assertTrue(WorkResources.target(p,c)==null,"With boards available the ordinary delivery objective resumes");
    });h.succeed();}
    @GameTest public void parcelCollectionIsOwnedAndCannotBeRepeatedForFree(GameTestHelper h){inCity(h,players->{var p=players.getFirst();var c=NapoliEmployment.Contract.COURIER;select(p,c);move(p,WorkResources.DEPOT.south());
        h.assertTrue(WorkResources.processPickup(p)==1&&WorkResources.hasParcel(p,c),"A working courier can collect today's package at the depot");
        h.assertTrue(WorkResources.processPickup(p)==0&&WorkResources.count(p,Items.PAPER)==1,"Repeated clicks do not duplicate packages");
        h.assertTrue(WorkResources.consumeParcel(p,c)&&!WorkResources.consumeParcel(p,c),"The delivery consumes exactly one valid package");
    });h.succeed();}
    @GameTest public void renewedTreesNeverOverwriteAPlayerRenovation(GameTestHelper h){var city=h.getLevel();var base=WorkResources.trees().getFirst();
        for(var p:BlockPos.betweenClosed(base.offset(-2,0,-2),base.offset(2,5,2)))city.setBlock(p,Blocks.AIR.defaultBlockState(),18);
        var custom=base.above();city.setBlock(custom,Blocks.EMERALD_BLOCK.defaultBlockState(),18);CityProperty.placed(city,custom);
        try{h.assertTrue(!WorkResources.grow(city,base)&&city.getBlockState(custom).is(Blocks.EMERALD_BLOCK),"Night growth skips the entire occupied tree rather than erasing construction");}
        finally{CityProperty.forget(city,custom);city.setBlock(custom,Blocks.AIR.defaultBlockState(),18);}h.succeed();}
    @GameTest public void deliveredBoardsAndFoodEnterTheCitySupply(GameTestHelper h){var city=h.getLevel();int boards=CityOccupations.supply(city,"planks");WorkEconomy.delivery(city,NapoliEmployment.Contract.CARPENTER,4);
        h.assertTrue(CityOccupations.supply(city,"planks")==boards+4,"Delivered boards are available to city trades");var before=CityEconomy.stock(city,"forno");WorkEconomy.delivery(city,NapoliEmployment.Contract.BAKER,1);
        h.assertTrue(CityEconomy.stock(city,"forno").bread()==Math.min(192,before.bread()+1),"A baker's delivery replenishes actual food stock");h.succeed();}
    @GameTest public void workTreesRegrowAtNightOnlyOnce(GameTestHelper h){var city=h.getLevel();var base=WorkResources.trees().getLast();var previous=city.getAttached(WorkResources.CUT);
        for(var p:BlockPos.betweenClosed(base.offset(-2,0,-2),base.offset(2,5,2)))city.setBlock(p,Blocks.AIR.defaultBlockState(),18);
        int saplings=CityOccupations.supply(city,"saplings");city.setAttached(WorkResources.CUT,Map.of(base.asLong()+"",5L));
        try{WorkResources.regrow(city,5,12000);h.assertTrue(city.getBlockState(base).isAir(),"Trees remain cut during the working day");WorkResources.regrow(city,5,13000);
            h.assertTrue(city.getBlockState(base).is(Blocks.OAK_LOG)&&city.getBlockState(base.above(3)).is(Blocks.OAK_LOG),"The authored tree returns at night");
            WorkResources.regrow(city,5,14000);h.assertTrue(CityOccupations.supply(city,"saplings")==saplings+2,"A night restock is recorded once");}
        finally{if(previous==null)city.removeAttached(WorkResources.CUT);else city.setAttached(WorkResources.CUT,previous);CityOccupations.supply(city,"saplings",saplings);}h.succeed();}
    @GameTest public void metroShaftsOpenAtBothPlatformsAndKeepPlayerChanges(GameTestHelper h){var city=h.getLevel();var stop=CityNetwork.stop("municipio");CityNetwork.ensureStation(city,stop);
        for(String id:List.of("M1","M6")){var line=CityNetwork.line(id);var exit=new BlockPos(stop.street().getX()+5,line.level()+1,stop.street().getZ()+4);
            h.assertTrue(city.isEmptyBlock(exit)&&city.isEmptyBlock(exit.above()),"The ladder has a two-block doorway onto "+id);
            h.assertTrue(CityAccessibility.connected(city,stop.entrance(),stop.boarding(line)),"The street is physically linked to platform "+id);
        }
        var custom=new BlockPos(stop.street().getX()+5,11,stop.street().getZ()+4);var chunk=city.getChunkAt(custom);city.setBlock(custom,Blocks.GLASS.defaultBlockState(),18);CityProperty.placed(city,custom);chunk.removeAttached(CityRailway.SHAFT_EXITS);
        try{CityRailway.repairExits(city,chunk);h.assertTrue(city.getBlockState(custom).is(Blocks.GLASS),"An existing player's glass is not removed by the exit migration");}
        finally{CityProperty.forget(city,custom);city.setBlock(custom,Blocks.AIR.defaultBlockState(),18);}h.succeed();}
    @GameTest public void waterfrontMetroHasAUsableStreetEntranceAndSign(GameTestHelper h){var city=h.getLevel();var stop=CityNetwork.stop("sannazaro");CityNetwork.ensureStation(city,stop);
        h.assertTrue(CityAccessibility.walkable(city,stop.entrance()),"The coast promenade cannot seal the ladder entrance from above");
        h.assertTrue(CityAccessibility.connected(city,stop.entrance(),stop.boarding(CityNetwork.line("M6"))),"The raised promenade connects physically to the underground platform");
        h.assertTrue(city.getBlockEntity(stop.sign()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity,"The updated stop has its actual clickable sign at the advertised location");h.succeed();}
    @GameTest public void pierApproachUsesTheDeckInsteadOfTheGroundUnderneath(GameTestHelper h){var city=h.getLevel();var place=NapoliAtlas.find("pontile");
        for(int x=(place.x()-8)>>4;x<=(place.x()+8)>>4;x++)for(int z=(place.z()-place.radius()-4)>>4;z<=(place.z()+place.radius())>>4;z++)NapoliExpansion.generate(city,city.getChunk(x,z));
        var from=new BlockPos(place.x()+2,64,place.z()-place.radius()-3);var to=new BlockPos(place.x()+2,CityStreets.walkingHeight(place.x()+2,42)+1,42);
        h.assertTrue(to.getY()==66&&CityAccessibility.connected(city,from,to),"The physical pier staircase reaches the upper deck without routing beneath it");h.succeed();}
    @GameTest public void maintenanceStopsAndRestoresTheActualTransportLine(GameTestHelper h){var city=h.getLevel();var stop=CityNetwork.stop("augusto");String line="M6";var old=city.getAttached(CityWorkIncidents.ISSUES);
        try{city.removeAttached(CityWorkIncidents.ISSUES);h.assertTrue(CityWorkIncidents.report(city,line,stop.entrance())&&CityWorkIncidents.stopped(city,line),"A saved maintenance issue disables its line");
            var issue=CityWorkIncidents.near(city,stop.entrance());h.assertTrue(CityWorkIncidents.resolve(city,issue.id())&&!CityWorkIncidents.stopped(city,line),"Repairing the cause restores service");h.assertTrue(!CityWorkIncidents.resolve(city,issue.id()),"Completed maintenance cannot pay or complete twice");}
        finally{if(old==null)city.removeAttached(CityWorkIncidents.ISSUES);else city.setAttached(CityWorkIncidents.ISSUES,old);}h.succeed();}
    @GameTest public void personalPreferencesAreStableAndVaryBetweenNeighbors(GameTestHelper h){var a=UUID.fromString("81c434fd-f789-4bf7-b390-65df19a35670");var b=UUID.fromString("b7141347-e493-4b36-9d32-9f56fedec452");
        h.assertTrue(CityCharacter.tastes(a).equals(CityCharacter.tastes(UUID.fromString(a.toString()))),"Reloading identity keeps tastes, greeting and preferred company");
        h.assertTrue(!CityCharacter.tastes(a).equals(CityCharacter.tastes(b)),"Neighbors have distinguishable preferences");h.succeed();}
    @GameTest public void physicalAccessRejectsBlockedPassages(GameTestHelper h){var city=h.getLevel();var from=h.absolutePos(new BlockPos(2,30,1));var to=h.absolutePos(new BlockPos(2,30,7));
        for(var p:BlockPos.betweenClosed(from.offset(-1,-1,0),to.offset(1,-1,0)))city.setBlock(p,Blocks.STONE.defaultBlockState(),18);
        for(var p:BlockPos.betweenClosed(from,to))city.setBlock(p,Blocks.AIR.defaultBlockState(),18);
        h.assertTrue(CityAccessibility.connected(city,from,to),"An unobstructed physical corridor is connected");
        for(int x=-10;x<=10;x++)for(int y=-2;y<=3;y++)city.setBlock(from.offset(x,y,3),Blocks.BEDROCK.defaultBlockState(),18);
        h.assertTrue(!CityAccessibility.connected(city,from,to),"A solid barrier is never considered an NPC passage");h.succeed();}
    @GameTest public void tutorialProgressUsesNormalActionsAndRemainsIdempotent(GameTestHelper h){inCity(h,players->{var p=players.getFirst();p.setAttached(CityOnboarding.ACTIVE,true);CityOnboarding.record(p,CityOnboarding.JOB);CityOnboarding.record(p,CityOnboarding.JOB);
        h.assertTrue(p.getAttachedOrElse(CityOnboarding.STEPS,0)==CityOnboarding.JOB,"Repeating a completed activity cannot advance other steps");
        CityOnboarding.record(p,CityOnboarding.TALK);CityOnboarding.record(p,CityOnboarding.TRANSIT);CityOnboarding.record(p,CityOnboarding.MEAL);
        h.assertTrue(p.getAttachedOrElse(CityOnboarding.STEPS,0)==15&&!p.getAttachedOrElse(CityOnboarding.ACTIVE,true),"Completing the four real activities ends the introduction");
    });h.succeed();}
    private static void select(ServerPlayer p,NapoliEmployment.Contract c){p.setAttached(NapoliEmployment.WORK,new NapoliEmployment.Work(c.id(),NapoliBroadcast.day(p.level()),1,-1));}
    private static void move(ServerPlayer p,BlockPos at){p.teleportTo(p.level(),at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),0,0,true);}
    private static void inCity(GameTestHelper h,Consumer<List<ServerPlayer>> body){HousingCapacityGameTest.withPlayers(h,2,players->{for(var p:players)p.getInventory().clearContent();body.accept(players);});}
}
