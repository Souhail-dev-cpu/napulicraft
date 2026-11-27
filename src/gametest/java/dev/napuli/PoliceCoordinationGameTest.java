package dev.napuli;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class PoliceCoordinationGameTest {
    @GameTest public void patrolPostsCoverLandmarksAndDistrictsWithStableDistinctPairs(GameTestHelper h){
        var ids=new HashSet<UUID>();
        for(var zone:PolicePatrols.zones())for(int slot=0;slot<2;slot++)
            h.assertTrue(ids.add(PolicePatrols.identity(zone,slot)),"Patrol slots cannot overlap across districts");
        for(var place:NapoliAtlas.PLACES)h.assertTrue(PolicePatrols.zones().stream().anyMatch(z->PolicePatrols.horizontal(z.center(),place.arrival())<=72*72),"Every landmark has a nearby patrol post: "+place.id());
        for(var district:DistrictLife.districts())h.assertTrue(PolicePatrols.zones().stream().anyMatch(z->PolicePatrols.horizontal(z.center(),district.business().approach(3))<=72*72),"Residential districts also have street coverage: "+district.id());
        h.assertTrue(PolicePatrols.nearby(List.of()).isEmpty(),"Empty areas do not activate patrols");h.succeed();
    }
    @GameTest public void radioDispatchSharesOfficersAcrossSuspectsAndKeepsSurplusOnPatrol(GameTestHelper h){
        var a=new PoliceDispatch.Alert(UUID.randomUUID(),new BlockPos(10,64,0),1);var b=new PoliceDispatch.Alert(UUID.randomUUID(),new BlockPos(-10,64,0),3);
        var officers=new ArrayList<PoliceDispatch.Officer>();for(int i=0;i<12;i++)officers.add(new PoliceDispatch.Officer(new UUID(0,i+1),new BlockPos(i-6,64,0)));
        var orders=PoliceDispatch.assign(officers,List.of(a,b));
        h.assertTrue(orders.values().stream().filter(o->o.suspect().equals(a.id())).count()==3,"A minor offence receives three nearby officers");
        h.assertTrue(orders.values().stream().filter(o->o.suspect().equals(b.id())).count()==5,"A serious offence receives up to five officers");
        h.assertTrue(orders.size()==8,"Surplus officers keep patrolling instead of all piling onto one player");
        var scarce=PoliceDispatch.assign(officers.subList(0,2),List.of(a,b));
        h.assertTrue(scarce.values().stream().map(PoliceDispatch.Assignment::suspect).distinct().count()==2,"With only two officers both suspects still receive a response");
        var far=new PoliceDispatch.Officer(UUID.randomUUID(),new BlockPos(500,64,0));
        h.assertTrue(PoliceDispatch.assign(List.of(far),List.of(a)).isEmpty(),"A report does not draw distant officers across the entire city");h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void repeatedMaintenanceKeepsPairsAndThePopulationLimit(GameTestHelper h){
        floor(h,0,15);HousingCapacityGameTest.withPlayers(h,1,players->{var p=players.getFirst();var city=h.getLevel();var at=h.absolutePos(new BlockPos(4,1,4));p.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
            var zones=List.of(new PolicePatrols.Zone("pair-a",at),new PolicePatrols.Zone("pair-b",at.east(6)),new PolicePatrols.Zone("pair-c",at.south(6)));
            int previous=CityLifeConfig.settings.policePatrolLimit;int initial=city.getEntities(Napuli.RESIDENT,PolicePatrols::managed).size();CityLifeConfig.settings.policePatrolLimit=initial+4;
            try{
                PolicePatrols.maintain(city,zones);var first=owned(city,zones);
                h.assertTrue(first.size()==4,"Street patrols fill two pairs while respecting the configured limit");
                h.assertTrue(first.stream().noneMatch(NapoliResident::shouldBeSaved),"Patrol actors cannot leave duplicate residents in saved chunks");
                PolicePatrols.maintain(city,zones);h.assertTrue(owned(city,zones).size()==4,"Repeated updates do not duplicate occupied slots");
                h.assertTrue(city.getEntities(Napuli.RESIDENT,PolicePatrols::managed).size()<=initial+4,"The active patrol budget is bounded");
            }finally{owned(city,zones).forEach(NapoliResident::discard);CityLifeConfig.settings.policePatrolLimit=previous;}
        });h.succeed();
    }
    @GameTest public void anUnloadedPostNeverForcesItsChunkToLoad(GameTestHelper h){
        var city=h.getLevel();var remote=new BlockPos(29000000,100,29000000);h.assertTrue(!city.hasChunkAt(remote),"The remote chunk starts unloaded");
        PolicePatrols.maintain(city,List.of(new PolicePatrols.Zone("unloaded",remote)));
        h.assertTrue(!city.hasChunkAt(remote),"Maintaining patrols does not generate or load remote neighbourhoods");h.succeed();
    }
    @GameTest(structure="napuli:police_arena",maxTicks=140) public void managedOfficersPhysicallyWalkTheirStreetPatrol(GameTestHelper h){
        floor(h,-8,24);var p=player(h);var at=h.absolutePos(new BlockPos(8,1,8));p.snapTo(at.getX()+.5,at.getY(),at.getZ()+10.5,0,0);
        var zone=new PolicePatrols.Zone("walking",at);PolicePatrols.maintain(h.getLevel(),List.of(zone));var patrol=owned(h.getLevel(),List.of(zone));
        h.assertTrue(patrol.size()==2,"A visited street receives its pair of officers");var npc=patrol.getFirst();var start=npc.position();
        h.runBeforeTestEnd(()->patrol.forEach(NapoliResident::discard));
        h.runAtTickTime(100,()->{h.assertTrue(npc.position().distanceToSqr(start)>4,"Patrols follow actual paths instead of standing at spawn");h.assertTrue(npc.position().distanceToSqr(Vec3.atBottomCenterOf(at))<48*48,"Routine patrols stay in their assigned neighbourhood");h.succeed();});
    }
    @GameTest(structure="napuli:police_arena") public void losingSightSearchesTheObservedSceneInsteadOfTheHiddenPlayer(GameTestHelper h){
        floor(h,0,15);HousingCapacityGameTest.withPlayers(h,1,players->{var p=players.getFirst();var city=h.getLevel();var npc=officer(h,3,3);var known=h.absolutePos(new BlockPos(12,1,3));
            try{
                p.snapTo(known.getX()+.5,known.getY(),known.getZ()+9.5,0,0);
                PoliceTactics.pursue(npc,p,city.getGameTime(),known,Vec3.ZERO,city.getGameTime()-60,0);
                h.assertTrue(known.equals(npc.getNavigation().getTargetPos()),"An unseen player cannot reveal their new position to the officer");
                npc.snapTo(known.getX()-1.5,known.getY(),known.getZ()+.5,0,0);npc.setOnGround(true);
                var searching=PoliceTactics.approach(npc,known,Vec3.ZERO,80,0);
                h.assertTrue(!searching.equals(known)&&searching.distSqr(known)<=6*6,"At the scene the officer checks nearby exits using the last sighting");
            }finally{PoliceTactics.stop(npc);npc.discard();}
        });h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void supportOfficersCoverDifferentSidesOfARunner(GameTestHelper h){
        floor(h,0,15);var npc=officer(h,2,7);var goal=h.absolutePos(new BlockPos(10,1,7));
        try{var left=PoliceTactics.approach(npc,goal,new Vec3(0,0,.2),0,1);var right=PoliceTactics.approach(npc,goal,new Vec3(0,0,.2),0,2);
            h.assertTrue(!left.equals(right)&&!left.equals(goal)&&!right.equals(goal),"Supporting officers approach opposite sides rather than the same tile");
            h.assertTrue(PoliceTactics.approach(npc,goal,Vec3.ZERO,0,0).equals(goal),"The lead officer keeps closing directly for the arrest");
        }finally{npc.discard();}h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void citizenPursuitsPickTheNearestSuspectAndContinueBetweenUpdates(GameTestHelper h){
        floor(h,0,15);var city=h.getLevel();var officer=officer(h,2,5);var near=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,h.absolutePos(new BlockPos(8,1,5)),0);var far=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,h.absolutePos(new BlockPos(13,1,5)),0);var previous=city.getAttached(CityPublicOrder.INCIDENTS);
        try{
            long now=city.getGameTime();var cases=new LinkedHashMap<String,CityPublicOrder.Incident>();cases.put(far.getUUID().toString(),new CityPublicOrder.Incident("THEFT",now+1200,0,Carabinieri.CELLS.getFirst()));cases.put(near.getUUID().toString(),new CityPublicOrder.Incident("THEFT",now+1200,0,Carabinieri.CELLS.getFirst()));city.setAttached(CityPublicOrder.INCIDENTS,Map.copyOf(cases));
            officer.tickCount=Math.floorMod(-officer.getId(),20);
            h.assertTrue(CityPublicOrder.tickEntity(officer,city),"An officer responds to a visible reported citizen");
            h.assertTrue(near.blockPosition().equals(officer.getNavigation().getTargetPos()),"The closer incident takes priority over iteration order");
            officer.tickCount++;h.assertTrue(CityPublicOrder.tickEntity(officer,city),"The pursuit owns movement between decision ticks");
            city.removeAttached(CityPublicOrder.INCIDENTS);h.assertTrue(!CityPublicOrder.tickEntity(officer,city),"Resolved incidents release the officer immediately");
        }finally{officer.discard();near.discard();far.discard();if(previous==null)city.removeAttached(CityPublicOrder.INCIDENTS);else city.setAttached(CityPublicOrder.INCIDENTS,previous);}h.succeed();
    }
    private static List<NapoliResident> owned(ServerLevel city,List<PolicePatrols.Zone> zones){var ids=new HashSet<UUID>();for(var zone:zones)for(int slot=0;slot<2;slot++)ids.add(PolicePatrols.identity(zone,slot));return new ArrayList<>(city.getEntities(Napuli.RESIDENT,n->ids.contains(n.getUUID())));}
    private static NapoliResident officer(GameTestHelper h,int x,int z){var n=NapoliResident.spawn(h.getLevel(),NapoliResident.Role.CARABINIERE,h.absolutePos(new BlockPos(x,1,z)),0);n.setOnGround(true);return n;}
    private static void floor(GameTestHelper h,int min,int max){for(int x=min;x<=max;x++)for(int z=min;z<=max;z++){h.setBlock(x,0,z,Blocks.STONE);for(int y=1;y<5;y++)h.setBlock(x,y,z,Blocks.AIR);}}
    private static ServerPlayer player(GameTestHelper h){var city=h.getLevel();var server=city.getServer();var profile=new GameProfile(UUID.randomUUID(),"PatrolTest");var p=new ServerPlayer(server,city,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);server.getPlayerList().placeNewPlayer(connection,p,CommonListenerCookie.createInitial(profile,false));h.runBeforeTestEnd(()->{server.getPlayerList().remove(p);channel.finishAndReleaseAll();});return p;}
}
