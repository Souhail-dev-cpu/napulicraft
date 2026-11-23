package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;

public final class CityContinuityGameTest {
    @GameTest public void pedestriansCanPlanARouteAcrossTramRails(GameTestHelper h){
        var city=h.getLevel();var start=h.absolutePos(new BlockPos(1,2,1));var end=start.east(5);
        for(var p:BlockPos.betweenClosed(start.offset(-1,-1,-1),end.offset(1,-1,1)))city.setBlock(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        for(var p:BlockPos.betweenClosed(start.offset(-1,0,-1),end.offset(1,2,1)))city.setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        for(int x=-1;x<=6;x++)for(int y=0;y<2;y++)for(int z:new int[]{-1,1})city.setBlock(start.offset(x,y,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
        city.setBlock(start.east(2),net.minecraft.world.level.block.Blocks.RAIL.defaultBlockState(),2);
        var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,start,0);
        h.runAfterDelay(3,()->{try{var path=npc.getNavigation().createPath(end,0);h.assertTrue(path!=null&&path.canReach(),"Rails can be crossed on foot instead of permanently blocking commuters");}finally{npc.discard();}h.succeed();});
    }
    @GameTest public void historicalBakeryMigratesOnceAndSharesFiniteStock(GameTestHelper h){
        var city=h.getLevel();var old=city.removeAttached(BakeryStock.STOCK);var stocks=city.removeAttached(CityEconomy.STOCKS);
        try{
            CityEconomy.put(city,"forno",new CityEconomy.Stock(0,0,0,0,0));city.setAttached(BakeryStock.STOCK,new BakeryStock.Stock(2,3));
            var migrated=CityEconomy.stock(city,"forno");
            h.assertTrue(migrated.flour()==4&&migrated.bread()==3,"Legacy flour preserves its two-loaf yield");
            h.assertTrue(CityEconomy.stock(city,"forno").equals(migrated)&&city.getAttached(BakeryStock.STOCK)==null,"Importing legacy inventory is idempotent");
            h.assertTrue(BakeryStock.reserveBread(city,2)&&CityEconomy.buyMeals(city,"forno",2,100)==1,"Old and new customers draw from the same shelf");
            h.assertTrue(!BakeryStock.reserveBread(city,1)&&CityEconomy.stock(city,"forno").bread()==0,"Neither interface can oversell the last loaf");
            BakeryStock.deliver(city);h.assertTrue(CityEconomy.stock(city,"forno").flour()==8,"An old delivery job replenishes the unified supply chain");
        }finally{restore(city,BakeryStock.STOCK,old);restore(city,CityEconomy.STOCKS,stocks);}h.succeed();
    }
    @GameTest public void oldAndNewMemorySharePricesWithoutStackedDiscounts(GameTestHelper h){
        var city=h.getLevel();var before=city.removeAttached(CitySocial.BONDS);var npc=NapoliResident.spawn(city,NapoliResident.Role.MERCATANTE,h.absolutePos(new BlockPos(1,2,1)),0);
        try{
            HousingCapacityGameTest.withPlayers(h,1,players->{var p=players.getFirst();p.setAttached(NeighbourMemory.RELATIONS,Map.of(npc.getUUID().toString(),8));
                var bond=CitySocial.bond(city,npc.getUUID(),p.getUUID());h.assertTrue(bond.trust()==70&&bond.meetings()==3,"An established relationship survives the old memory format");
                h.assertTrue(p.getAttached(NeighbourMemory.RELATIONS).isEmpty(),"The imported relation cannot be applied a second time");
                int price=CityEconomy.price(p,npc.getUUID(),10);h.assertTrue(NeighbourMemory.price(p,npc,10)==price,"Every merchant uses one event and loyalty price calculation");
                int purchases=bond.purchases();NeighbourMemory.remember(p,npc,2);h.assertTrue(CitySocial.bond(city,npc.getUUID(),p.getUUID()).purchases()==purchases+1,"A legacy sale records one purchase");
            });
        }finally{npc.discard();restore(city,CitySocial.BONDS,before);}h.succeed();
    }
    @GameTest public void queuesRespectArrivalOrderAndReleaseDepartedActors(GameTestHelper h){
        var city=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));var actors=new ArrayList<NapoliResident>();
        try{
            for(int i=0;i<3;i++)actors.add(NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,pos.offset(i,0,0),0));
            var a=actors.get(0).getUUID();var b=actors.get(1).getUUID();var c=actors.get(2).getUUID();
            h.assertTrue(CityMovement.acquire(city,pos,a,100)&&!CityMovement.acquire(city,pos,b,100)&&!CityMovement.acquire(city,pos,c,100),"One person uses the counter and two people queue");
            CityMovement.release(city,a);h.assertTrue(!CityMovement.acquire(city,pos,c,100)&&CityMovement.acquire(city,pos,b,100),"The last arrival cannot jump the queue");
            actors.get(1).discard();h.assertTrue(CityMovement.acquire(city,pos,c,100),"A disconnected or removed actor does not block the line");
        }finally{for(var npc:actors){CityMovement.release(city,npc.getUUID());npc.discard();}}h.succeed();
    }
    @GameTest public void seatsHaveOneOccupantAndAreRemovedWhenTheyStand(GameTestHelper h){
        var city=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));var a=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,pos,0);var b=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_WOMAN,pos.east(),0);
        try{
            h.assertTrue(CityMovement.sit(a,pos)&&CityMovement.isSeat(a.getVehicle()),"Sitting attaches the resident to a real invisible seat");
            h.assertTrue(!CityMovement.sit(b,pos),"Two residents cannot occupy the same chair");
            var marker=a.getVehicle();CityMovement.stand(a);h.assertTrue(marker.isRemoved()&&!a.isPassenger()&&CityMovement.sit(b,pos),"Standing removes the marker and frees the chair");
        }finally{CityMovement.stand(a);CityMovement.stand(b);a.discard();b.discard();}h.succeed();
    }
    @GameTest public void personalRepairsChangeProductionAndChargeOnlyOnce(GameTestHelper h){
        var city=h.getLevel();var goals=city.removeAttached(CityPersonalGoals.GOALS);var census=city.removeAttached(NapoliCivicLife.CENSUS);var lives=city.removeAttached(CitySimulation.LIFE);
        try{
            long day=NapoliBroadcast.day(city);var c=new NapoliCivicLife.Citizen(UUID.randomUUID(),"Assunta",true,day-30L*CityLifeConfig.settings.daysPerYear,100,NapoliCivicLife.Job.BAKER.ordinal(),"","public_0","",false);
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(day,List.of(c)));
            CityPersonalGoals.put(city,c.id(),new CityPersonalGoals.Goal("oven",c.property(),day-3,"",false,0));
            h.assertTrue(CityPersonalGoals.ovenBroken(city),"An unresolved repair request blocks the production oven");
            CityPersonalGoals.tick(city);h.assertTrue(!CityPersonalGoals.ovenBroken(city)&&NapoliCivicLife.profile(city,c.id()).balance()==76,"The resident can pay a repairer after three days and production can resume");
            CityPersonalGoals.tick(city);h.assertTrue(NapoliCivicLife.profile(city,c.id()).balance()==76,"A completed objective cannot charge the resident twice");
            var g=CityPersonalGoals.goal(city,c.id());var json=CityPersonalGoals.Goal.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,g).getOrThrow();h.assertTrue(CityPersonalGoals.Goal.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow().equals(g),"The objective outcome survives reloads");
        }finally{restore(city,CityPersonalGoals.GOALS,goals);restore(city,NapoliCivicLife.CENSUS,census);restore(city,CitySimulation.LIFE,lives);}h.succeed();
    }
    @GameTest public void dinnerPortionsAndTemporaryInvitationsSurviveReloadAndEndOnce(GameTestHelper h){
        var city=h.getLevel();var parties=city.removeAttached(CityGatherings.PARTIES);var owners=city.removeAttached(NapoliProperties.OWNERS);var invitations=city.removeAttached(HouseGuests.INVITATIONS);
        try{
            HousingCapacityGameTest.withPlayers(h,4,players->{var host=players.get(0);var temporary=players.get(1);var permanent=players.get(2);var upgraded=players.get(3);var home=NapoliMergellina.PROPERTIES.get(15);long now=city.getGameTime();
                city.setAttached(NapoliProperties.OWNERS,Map.of(home.id(),host.getUUID()));HouseGuests.grant(city,home.id(),host.getUUID(),permanent);
                var guests=new HashMap<String,CityGatherings.Guest>();for(var p:players)guests.put(p.getUUID().toString(),new CityGatherings.Guest(p.getName().getString(),"accepted",false,false));
                var party=new CityGatherings.Party(home.id(),host.getUUID(),now,now+2400,4,false,Map.copyOf(guests));CityGatherings.put(city,party);CityGatherings.tick(city);
                h.assertTrue(HouseGuests.invited(city,home.id(),temporary.getUUID()),"A confirmed guest receives access during the event");
                h.assertTrue(CityGatherings.eat(city,temporary.getUUID())&&!CityGatherings.eat(city,temporary.getUUID()),"One guest consumes exactly one shared portion");
                var saved=CityGatherings.party(city,home.id());var json=CityGatherings.Party.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,saved).getOrThrow();CityGatherings.put(city,CityGatherings.Party.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow());
                h.assertTrue(!CityGatherings.eat(city,temporary.getUUID())&&CityGatherings.party(city,home.id()).portions()==3,"Reloading cannot reset consumed servings");
                HouseGuests.grant(city,home.id(),host.getUUID(),upgraded);CityGatherings.close(city,CityGatherings.party(city,home.id()));
                h.assertTrue(!HouseGuests.invited(city,home.id(),temporary.getUUID())&&HouseGuests.invited(city,home.id(),permanent.getUUID())&&HouseGuests.invited(city,home.id(),upgraded.getUUID()),"Ending a dinner revokes only its own permissions, including after a manual invitation upgrade");
                var ended=CityGatherings.party(city,home.id());CityGatherings.close(city,ended);h.assertTrue(CityGatherings.party(city,home.id()).equals(ended),"Closing the event twice cannot repeat settlement");
            });
        }finally{restore(city,CityGatherings.PARTIES,parties);restore(city,NapoliProperties.OWNERS,owners);restore(city,HouseGuests.INVITATIONS,invitations);}h.succeed();
    }
    @GameTest public void reputationSpreadsThroughNamedTellersInsteadOfTheWholeCity(GameTestHelper h){
        var city=h.getLevel();var stories=city.removeAttached(CityReputation.STORIES);var census=city.removeAttached(NapoliCivicLife.CENSUS);var bonds=city.removeAttached(CitySocial.BONDS);
        var pos=h.absolutePos(new BlockPos(1,2,1));var a=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,pos,0);var b=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_WOMAN,pos.east(2),0);var stranger=UUID.randomUUID();var subject=UUID.randomUUID();
        try{
            long day=NapoliBroadcast.day(city);var people=new ArrayList<NapoliCivicLife.Citizen>();for(var id:List.of(a.getUUID(),b.getUUID(),stranger))people.add(new NapoliCivicLife.Citizen(id,"Vicino",false,day-30L*CityLifeConfig.settings.daysPerYear,8,-1,"","public_0","",true));city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(day,people));
            a.setActivity(NapoliHouseholds.Activity.SOCIAL);b.setActivity(NapoliHouseholds.Activity.SOCIAL);
            String id=CityReputation.record(city,subject,a.getUUID(),"harm","qualcuno ha aggredito un vicino.",-15);
            h.assertTrue(CityReputation.influence(city,b.getUUID(),subject)==0,"A distant or uninformed neighbor starts without knowledge of the incident");
            CityReputation.spread(city,city.getGameTime()+1200);var story=city.getAttached(CityReputation.STORIES).get(id);var heard=story.knowers().get(b.getUUID().toString());
            h.assertTrue(heard!=null&&heard.confidence()==75&&heard.via().equals(a.getUUID().toString()),"A nearby conversation records its immediate teller and weaker confidence");
            h.assertTrue(!story.knowers().containsKey(stranger.toString())&&CityReputation.influence(city,stranger,subject)==0,"Census membership alone cannot reveal private events");
            h.assertTrue(CitySocial.price(city,b.getUUID(),subject,10)==11,"A credible negative report has a bounded price consequence");
            var json=CityReputation.Story.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,story).getOrThrow();h.assertTrue(CityReputation.Story.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow().equals(story),"Source, intermediaries and confidence survive reloads");
        }finally{a.discard();b.discard();restore(city,CityReputation.STORIES,stories);restore(city,NapoliCivicLife.CENSUS,census);restore(city,CitySocial.BONDS,bonds);}h.succeed();
    }
    @GameTest public void leagueCalendarStandingsAndSuspendedParticipationArePersistent(GameTestHelper h){
        var city=h.getLevel();var old=city.removeAttached(CityLeague.SEASON);
        try{
            var s=CityLeague.schedule(3,20);city.setAttached(CityLeague.SEASON,s);
            h.assertTrue(s.fixtures().size()==6&&s.fixtures().stream().map(f->Set.of(f.home(),f.away())).distinct().count()==6,"Every pair of neighborhoods meets exactly once");
            var f=CityLeague.begin(city,s.fixtures().getFirst());String player=UUID.randomUUID().toString();
            CityLeague.progress(city,f.id(),2,1,1200,Map.of(player,new CityLeague.Participant(0,800,1)));
            CityLeague.progress(city,f.id(),2,1,1000,Map.of(player,new CityLeague.Participant(0,600,0)));
            var resumed=CityLeague.fixture(city,f.id());h.assertTrue(resumed.elapsed()==1200&&resumed.players().get(player).ticks()==800&&resumed.players().get(player).goals()==1,"An older checkpoint cannot erase earned participation");
            CityLeague.complete(city,f.id());h.assertTrue(CityLeague.standings(CityLeague.season(city).fixtures()).stream().allMatch(r->r.played()==0),"A suspended match does not enter the standings");
            CityLeague.progress(city,f.id(),2,1,3000,Map.of());CityLeague.complete(city,f.id());CityLeague.complete(city,f.id());
            var row=CityLeague.standings(CityLeague.season(city).fixtures()).stream().filter(r->r.team().equals(f.home())).findFirst().orElseThrow();h.assertTrue(row.points()==3&&row.played()==1&&row.goals()==2,"One win counts once");
            var saved=CityLeague.season(city);var json=CityLeague.Season.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,saved).getOrThrow();h.assertTrue(CityLeague.Season.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow().equals(saved),"Calendar, standings and participant progress survive serialization");
        }finally{restore(city,CityLeague.SEASON,old);}h.succeed();
    }
    private static <T> void restore(net.minecraft.server.level.ServerLevel city,net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> type,T value){if(value==null)city.removeAttached(type);else city.setAttached(type,value);}
}
