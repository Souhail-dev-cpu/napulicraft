package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;

public final class DistrictGameTest {
    @GameTest public void eachNeighborhoodHasDistinctHomesAndAWorkplace(GameTestHelper h){
        var ids=new HashSet<String>();var sites=new HashSet<BlockPos>();
        for(var d:DistrictLife.districts()){
            h.assertTrue(!DistrictLife.square(d).id().equals("nisida"),"Daily social routines stay on the accessible mainland");
            h.assertTrue(ids.add(d.id())&&d.homes().size()==6,"Every district has a stable identity and six distinct residential lots");
            for(var home:java.util.stream.Stream.concat(d.homes().stream(),java.util.stream.Stream.of(d.business())).toList()){
                h.assertTrue(sites.add(new BlockPos(home.x(),0,home.z()))&&NapoliExpansion.houseAllowed(home.x(),home.z()),"No overlapping or unbuildable assigned lot: "+home.id());
                h.assertTrue(home.entrance().getY()==home.ground(),"Addresses follow the local hillside elevation");
            }
        }h.succeed();
    }
    @GameTest public void populationIsBoundedAndSeedingDoesNotResurrectResidents(GameTestHelper h){
        var city=h.getLevel();var census=city.removeAttached(NapoliCivicLife.CENSUS);var settled=city.removeAttached(DistrictLife.SETTLED);var owners=city.removeAttached(NapoliProperties.OWNERS);var lives=city.removeAttached(CitySimulation.LIFE);var journeys=city.removeAttached(CityJourneys.JOURNEYS);int max=CityLifeConfig.settings.maximumPopulation,target=CityLifeConfig.settings.districtPopulation;
        try{
            CityLifeConfig.settings.maximumPopulation=24;CityLifeConfig.settings.districtPopulation=88;city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of()));
            h.assertTrue(DistrictLife.seed(city)==24&&DistrictLife.seed(city)==0,"Seeding respects the administrator's population ceiling and is idempotent");
            var before=NapoliCivicLife.census(city);var dead=before.people().getFirst();city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(before.day(),before.people().subList(1,before.people().size())));DistrictLife.seed(city);
            h.assertTrue(NapoliCivicLife.profile(city,dead.id())==null,"A deceased resident is not resurrected by the district initializer");
            for(var c:NapoliCivicLife.census(city).people())h.assertTrue(CityLivingHomes.find(c.property())!=null&&DistrictLife.work(c)!=null,"Seeded adults have an actual home and workplace");
        }finally{CityLifeConfig.settings.maximumPopulation=max;CityLifeConfig.settings.districtPopulation=target;restore(city,NapoliCivicLife.CENSUS,census);restore(city,DistrictLife.SETTLED,settled);restore(city,NapoliProperties.OWNERS,owners);restore(city,CitySimulation.LIFE,lives);restore(city,CityJourneys.JOURNEYS,journeys);}h.succeed();
    }
    @GameTest public void newHomesHaveUsableFurnitureAtTheirActualElevation(GameTestHelper h){
        var city=h.getLevel();for(var d:DistrictLife.districts())for(var home:d.homes()){
            for(int x=home.x()>>4;x<=(home.x()+21)>>4;x++)for(int z=home.z()>>4;z<=(home.z()+23)>>4;z++){var chunk=city.getChunk(x,z);NapoliExpansion.generate(city,chunk);DistrictLife.fit(city,chunk);}
            var bed=CityLivingHomes.use(home,CityLivingHomes.Furniture.BED).block();h.assertTrue(city.getBlockState(bed).getBlock() instanceof BedBlock&&city.getBlockState(bed.east()).getBlock() instanceof BedBlock,"Both adult partners have a bed: "+home.id());
            var fridge=CityLivingHomes.use(home,CityLivingHomes.Furniture.FRIDGE).block();h.assertTrue(city.getBlockEntity(fridge) instanceof net.minecraft.world.Container,"The household refrigerator is actual storage");
        }h.succeed();
    }
    @GameTest public void shopsDoNotOversellOrShareInventories(GameTestHelper h){
        var city=h.getLevel();var stocks=city.removeAttached(CityEconomy.STOCKS);var ledgers=city.removeAttached(DistrictShops.LEDGERS);
        try{var a=DistrictLife.districts().get(0);var b=DistrictLife.districts().get(1);CityEconomy.put(city,DistrictShops.key(a),new CityEconomy.Stock(0,0,1,0,0));
            int other=DistrictShops.quantity(city,b,"pane");h.assertTrue(DistrictShops.reserve(city,a,"pane",1)&&!DistrictShops.reserve(city,a,"pane",1),"Competing purchases cannot sell the same final loaf");
            h.assertTrue(DistrictShops.quantity(city,b,"pane")==other,"Each district owns separate bread supplies");
            h.assertTrue(!DistrictShops.reserve(city,a,"mele",0)&&!DistrictShops.reserve(city,a,"mele",100),"Invalid quantities cannot create goods");
            int money=DistrictShops.ledger(city,a.id()).till();DistrictShops.credit(city,a,2);h.assertTrue(DistrictShops.ledger(city,a.id()).till()==money+2,"Sales enter the local till");
        }finally{restore(city,CityEconomy.STOCKS,stocks);restore(city,DistrictShops.LEDGERS,ledgers);}h.succeed();
    }
    @GameTest public void newDrinksMigrateOldShopLedgersWithoutRefillingSoldStock(GameTestHelper h){
        var city=h.getLevel();var saved=city.removeAttached(DistrictShops.LEDGERS);
        try{
            var a=DistrictLife.districts().getFirst();var b=DistrictLife.districts().get(1);
            DistrictShops.put(city,a.id(),new DistrictShops.Ledger(Map.of("mele",0,"caffe",7),53,12));
            var migrated=DistrictShops.ledger(city,a.id());
            h.assertTrue(migrated.till()==53&&migrated.delivered()==12&&migrated.goods().get("mele")==0&&migrated.goods().get("caffe")==7,"Migration preserves cash, delivery date and existing quantities");
            for(var product:DistrictShops.drinks()){
                h.assertTrue(DistrictShops.quantity(city,a,product.id())==product.initial(),"New drink stock exists in old saves: "+product.id());
                h.assertTrue(DistrictShops.reserve(city,a,product.id(),product.initial()),"All initial bottles can be reserved");
                h.assertTrue(DistrictShops.quantity(city,a,product.id())==0&&!DistrictShops.reserve(city,a,product.id(),1),"Reading a sold-out ledger never replenishes bottles");
                h.assertTrue(DistrictShops.quantity(city,b,product.id())==product.initial(),"Districts retain independent drink stocks");
            }
        }finally{restore(city,DistrictShops.LEDGERS,saved);}h.succeed();
    }
    @GameTest public void allTransportStopsAreConnectedByTraversableRoutes(GameTestHelper h){
        for(var line:CityNetwork.lines()){
            var plan=CityNetwork.plan(line);h.assertTrue(plan.fleet()>=2&&plan.fleet()<=32,"Vehicle counts stay bounded");
            for(int i=0;i<plan.frames().size();i++){var frame=plan.frames().get(i);var next=plan.frames().get((i+1)%plan.frames().size());h.assertTrue(frame.to().distanceTo(next.from())<.001,"Scheduled movement is continuous: "+line.id());h.assertTrue(frame.end()>frame.start(),"Every frame advances time");}
            for(String id:line.stops())h.assertTrue(CityNetwork.stop(id)!=null,"Every route has actual named stops");
        }
        for(var district:DistrictLife.districts()){var stop=CityNetwork.nearest(district.landmark().arrival());h.assertTrue(!CityNetwork.itinerary("dante",stop.id()).isEmpty()||stop.id().equals("dante"),"Every inhabited district joins the shared transit network: "+district.name());}h.succeed();
    }
    @GameTest public void boardingChoosesTheCorrectDirection(GameTestHelper h){
        var line=CityNetwork.line("M6");int forward=0,reverse=0;for(var frame:CityNetwork.plan(line).frames())if(frame.stop().equals("lala")){if(CityNetwork.towards(line,frame.start()+1,"lala","sannazaro"))forward++;else reverse++;}
        h.assertTrue(forward==1&&reverse==1,"A passenger going east boards the eastbound train only");h.succeed();
    }
    @GameTest public void persistedTicketsRetainTransfersAndPayment(GameTestHelper h){
        var legs=CityNetwork.itinerary("pianura","barra");h.assertTrue(legs.size()>=2,"Distant districts support an actual transfer itinerary");
        var ticket=new CityNetwork.Ticket(legs,"riding","B1/2",10,400,12,true);var json=CityNetwork.Ticket.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,ticket).getOrThrow();h.assertTrue(CityNetwork.Ticket.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow().equals(ticket),"Destination, paid fare and delay baseline survive saving");h.succeed();
    }
    @GameTest public void metroPassengerReturnsToTheStreetAfterArrival(GameTestHelper h){
        var city=h.getLevel();var line=CityNetwork.line("M6");var stop=CityNetwork.stop("lala");var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,h.absolutePos(new BlockPos(1,2,1)),0);
        try{var t=new CityNetwork.Ticket(List.of(new CityNetwork.Leg("M6","augusto","lala")),"riding","M6/0",0,city.getGameTime()-1,CityNetwork.delay(city,"M6/0"),true);var exit=CityNetwork.process(city,npc,t,false);h.assertTrue(exit!=null&&exit.state().equals("exit")&&npc.getBlockY()==line.level()+1,"NPC alights on the underground platform");
            var due=new CityNetwork.Ticket(exit.legs(),exit.state(),"",0,city.getGameTime()-1,0,true);h.assertTrue(CityNetwork.process(city,npc,due,false)==null&&npc.blockPosition().distSqr(stop.entrance())<3,"NPC exits through the station before resuming the walk");
        }finally{npc.discard();}h.succeed();
    }
    @GameTest public void metroTrackClearanceIncludesBothDirectionsAndStationExits(GameTestHelper h){
        var city=h.getLevel();var line=CityNetwork.line("M6");var fitted=new HashSet<Long>();
        for(int tick=0;tick<650;tick+=3){var at=CityNetwork.sample(line,0,tick).point();for(int x=((int)Math.floor(at.x)-3)>>4;x<=((int)Math.floor(at.x)+3)>>4;x++)for(int z=((int)Math.floor(at.z)-3)>>4;z<=((int)Math.floor(at.z)+3)>>4;z++)if(fitted.add(net.minecraft.world.level.ChunkPos.pack(x,z))){var c=city.getChunk(x,z);NapoliExpansion.generate(city,c);CityRailway.fit(city,c);}
            for(int x=(int)Math.floor(at.x-1.1);x<=(int)Math.floor(at.x+1.1);x++)for(int z=(int)Math.floor(at.z-1.1);z<=(int)Math.floor(at.z+1.1);z++)for(int y=11;y<=12;y++){var p=new BlockPos(x,y,z);h.assertTrue(city.getBlockState(p).getCollisionShape(city,p).isEmpty(),"Train clearance, phase="+tick+", position="+at+", obstacle="+p+" "+city.getBlockState(p));}
        }h.succeed();
    }
    @GameTest public void invitedGuestsHaveAdditionalPlacesInNewDistrictHomes(GameTestHelper h){
        var city=h.getLevel();var owners=city.removeAttached(NapoliProperties.OWNERS);var invitations=city.removeAttached(HouseGuests.INVITATIONS);var home=DistrictLife.homes().getFirst();var host=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,h.absolutePos(new BlockPos(1,2,1)),0);
        try{city.setAttached(NapoliProperties.OWNERS,Map.of(home.id(),host.getUUID()));HousingCapacityGameTest.withPlayers(h,1,players->{var guest=players.getFirst();h.assertTrue(HouseGuests.grant(city,home.id(),host.getUUID(),guest)&&HouseGuests.capacityExempt(city,home.id(),guest.getUUID()),"An invited friend has a guest place beyond the five ordinary places in a new home");});}
        finally{host.discard();restore(city,NapoliProperties.OWNERS,owners);restore(city,HouseGuests.INVITATIONS,invitations);}h.succeed();
    }
    @GameTest public void publicVehiclesEnforceTheirActualSeatCapacity(GameTestHelper h){
        var city=h.getLevel();for(String id:List.of("B5","T1","M6")){var line=CityNetwork.line(id);var vehicle=Napuli.CITY_VEHICLE.create(city,net.minecraft.world.entity.EntitySpawnReason.EVENT);vehicle.configureNetwork(id+"/0",line.mode()==CityNetwork.Mode.METRO?3:line.mode()==CityNetwork.Mode.TRAM?2:1);vehicle.setPos(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(2,2,2))));city.addFreshEntity(vehicle);var actors=new ArrayList<NapoliResident>();
            try{for(int i=0;i<=line.capacity();i++){var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,h.absolutePos(new BlockPos(1,2,1)),0);actors.add(npc);h.assertTrue(npc.startRiding(vehicle,false,false)==(i<line.capacity()),"Public vehicle admits only its available seats: "+id);}h.assertTrue(vehicle.getPassengers().size()==line.capacity(),"The rejected passenger does not displace seated passengers");}
            finally{for(var actor:actors){actor.stopRiding();actor.discard();}vehicle.discard();}
        }h.succeed();
    }
    private static <T> void restore(net.minecraft.server.level.ServerLevel city,AttachmentType<T> type,T value){if(value==null)city.removeAttached(type);else city.setAttached(type,value);}
}
