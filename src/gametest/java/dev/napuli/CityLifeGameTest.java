package dev.napuli;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.mojang.serialization.JsonOps;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;

public final class CityLifeGameTest {
    @GameTest public void neighbourhoodEventsRotateAndPayOncePerVisitor(GameTestHelper helper) {
        var venues = NapoliEventCatalogue.VENUES;
        var titles = new HashSet<String>();
        for (var venue : venues) {
            helper.assertTrue(NapoliAtlas.find(venue.place()) != null && venue.scenes().size() == 10,
                    "Each of the ten venues has ten events at an atlas destination");
            venue.scenes().forEach(scene -> {
                helper.assertTrue(titles.add(scene.title()), "Event titles are unique");
                helper.assertTrue(scene.reward() != null && scene.reward() != net.minecraft.world.item.Items.AIR
                                && scene.donation() != net.minecraft.world.item.Items.AIR, "Event items are registered");
            });
            for (long seed : new long[] {42, 621}) {
                for (int cycle = 0; cycle < 6; cycle++) {
                    var seen = new HashSet<NapoliNeighbourhoodEvents.Scene>();
                    for (long day = cycle * 10L; day < (cycle + 1) * 10L; day++) {
                        var scene = venue.scene(seed, day);
                        helper.assertTrue(seen.add(scene) && !scene.equals(venue.scene(seed, day - 1)),
                                "Every cycle visits each scene once with no consecutive repeat");
                        helper.assertTrue(scene.equals(venue.scene(seed, day)), "Co-op players see the same shared schedule");
                    }
                }
            }
            helper.assertTrue(!venue.open(venue.opens() - 1) && venue.open(venue.opens()) && !venue.open(venue.opens() + 3500),
                    "Events respect both ends of the time window");
        }
        helper.assertTrue(titles.size() == 100, "Exactly 100 unique neighbourhood events");
        var venue = venues.getFirst();
        var quiz = venue.scenes().stream().filter(scene -> scene.quiz() != null).findFirst().orElseThrow();
        var trade = venue.scenes().stream().filter(scene -> scene.donation() != null).findFirst().orElseThrow();
        var gift = venue.scenes().stream().filter(scene -> scene.donation() == null && scene.quiz() == null)
                .findFirst().orElseThrow();
        var level = helper.getLevel();
        var players = level.getServer().getPlayerList();
        var profile = new GameProfile(UUID.randomUUID(), "NapoliEventTest");
        var first = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        players.placeNewPlayer(connection, first, CommonListenerCookie.createInitial(profile, false));
        first.setGameMode(GameType.SURVIVAL);
        var second = helper.makeMockServerPlayerInLevel();
        try {
            helper.assertTrue(NapoliNeighbourhoodEvents.takePart(first, venue, quiz, 50, 3 - quiz.quiz().answer()) == 0,
                    "Wrong answers give no reward");
            helper.assertTrue(NapoliNeighbourhoodEvents.takePart(first, venue, quiz, 50, quiz.quiz().answer()) == 1,
                    "Correct answers award the souvenir");
            helper.assertTrue(NapoliNeighbourhoodEvents.takePart(first, venue, quiz, 50, quiz.quiz().answer()) == 0,
                    "A second claim on the same day is refused");
            helper.assertTrue(NapoliNeighbourhoodEvents.takePart(second, venue, gift, 50, 0) == 1,
                    "The first visitor does not consume the event for others");
            first.getInventory().clearContent();
            helper.assertTrue(NapoliNeighbourhoodEvents.takePart(first, venue, trade, 51, 0) == 0,
                    "Trades require the requested item in hand");
            first.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(trade.donation(), 2));
            helper.assertTrue(NapoliNeighbourhoodEvents.takePart(first, venue, trade, 51, 0) == 1
                    && first.getOffhandItem().getCount() == 1, "A trade consumes one item and pays once on the next day");
        } finally {
            players.remove(first);
            players.remove(second);
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    @GameTest public void footballRatingsScaleToNinetyNine(GameTestHelper helper) {
        helper.assertTrue(FootballRatings.trained(38, 100, 10, false) == 99, "Any attribute can reach 99");
        helper.assertTrue(FootballRatings.trained(54, 3, 0, true) > FootballRatings.trained(54, 3, 0, false),
                "Role-specific training improves priority attributes faster");
        helper.assertTrue(FootballRatings.paceBonus(99) > FootballRatings.paceBonus(90),
                "At equal effort, the faster player moves faster");
        helper.assertTrue(FootballRatings.aimAssist(99) == 1 && FootballRatings.shotBoost(99) > FootballRatings.shotBoost(70),
                "Elite shooting gives full in-range aim assistance and a harder shot");
        helper.assertTrue(FootballRatings.interceptionRange(99) > FootballRatings.interceptionRange(50)
                        && FootballRatings.tackleDistance(99, 60) > FootballRatings.tackleDistance(60, 60),
                "Defense improves control of the duel");
        helper.assertTrue(FootballRatings.staminaDrain(80, 99) < FootballRatings.staminaDrain(80, 50),
                "Endurance reduces sprint drain");
        helper.succeed();
    }

    @GameTest public void footballLineupFillsBothTeamsAndReusesVacancies(GameTestHelper helper) {
        var occupied = new HashSet<Integer>();
        for (int i = 0; i < 5; i++) {
            int seat = FootballLineup.available(4, 0, occupied::contains);
            helper.assertTrue(seat >= 0 && seat < 5 && occupied.add(seat), "Each Napoli player gets a unique seat");
        }
        helper.assertTrue(FootballLineup.available(4, 0, occupied::contains) == -1, "A full side rejects another player");
        for (int i = 0; i < 5; i++) {
            int seat = FootballLineup.available(4, -1, occupied::contains);
            helper.assertTrue(seat >= 5 && occupied.add(seat), "Auto join fills the visiting side");
        }
        helper.assertTrue(FootballLineup.available(4, -1, occupied::contains) == -1, "Ten human seats are the limit");
        occupied.remove(7);
        helper.assertTrue(FootballLineup.available(1, 5, occupied::contains) == 7, "A departing player frees only their seat");
        helper.succeed();
    }


    @GameTest public void cryptMigrationClosesTheOldStairsAndOpensTheAqueductPassage(GameTestHelper helper) {
        var world = helper.getLevel();
        var altar = new BlockPos(-53, 58, -36);
        var stairs = new BlockPos(-52, 63, -16);
        var facade = stairs.above().west(2);
        world.setBlockAndUpdate(altar, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
        world.removeBlock(stairs, false);
        world.setBlockAndUpdate(facade, Blocks.TERRACOTTA.defaultBlockState());

        NapoliMap.relocateCrypt(world);

        helper.assertTrue(world.getBlockState(stairs).is(Blocks.STONE_BRICKS),
                "The old entrance is paved over");
        helper.assertTrue(world.getBlockState(facade).is(Blocks.TERRACOTTA),
                "The neighboring facade survives the migration");
        helper.assertTrue(NapoliMap.inClosedCrypt(world, altar.above()), "Players in the old crypt can be relocated");
        for (int x = 5; x <= NapoliMap.CRYPT.getX(); x++) {
            var step = new BlockPos(x, 42, -119);
            helper.assertTrue(world.getBlockState(step).isAir() && world.getBlockState(step.above()).isAir()
                    && world.getBlockState(step.below()).is(Blocks.TUFF_BRICKS),
                    "A walkable passage connects the aqueduct and the crypt");
        }
        helper.succeed();
    }

    @GameTest public void censusPopulationAndNeedsSurviveSerialization(GameTestHelper helper) {
        var level=helper.getLevel();var previous=level.removeAttached(NapoliCivicLife.CENSUS);
        var previousLife=level.removeAttached(CitySimulation.LIFE);
        try {
            NapoliCivicLife.populate(level);
            var census=NapoliCivicLife.census(level);
            helper.assertTrue(census.people().size()==CityLifeConfig.settings.population,"Configured population is created");
            helper.assertTrue(census.people().stream().map(NapoliCivicLife.Citizen::id).distinct().count()==census.people().size(),"Every identity is unique");
            NapoliCivicLife.populate(level);
            helper.assertTrue(NapoliCivicLife.census(level).equals(census),"Reapplying migration cannot duplicate residents");
            helper.assertTrue(census.people().stream().anyMatch(p->p.profession()==NapoliCivicLife.Job.INFLUENCER&&p.woman()&&p.age(census.day())>=18),"Adult female influencer exists");
            helper.assertTrue(census.people().stream().anyMatch(p->p.profession()==NapoliCivicLife.Job.AIR_SELLER),"Souvenir merchant exists");
            helper.assertTrue(census.people().stream().anyMatch(p->p.age(census.day())>=70)&&census.people().stream().anyMatch(p->p.age(census.day())<18),"The initial neighborhood includes elders as well as children");
            var state=CitySimulation.fresh(UUID.randomUUID());
            var encoded=CitySimulation.Life.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow();
            helper.assertTrue(CitySimulation.Life.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow().equals(state),"Needs, inventory, reputation and memories round-trip");
            var encodedCensus=NapoliCivicLife.Census.CODEC.encodeStart(JsonOps.INSTANCE,census).getOrThrow();
            helper.assertTrue(NapoliCivicLife.Census.CODEC.parse(JsonOps.INSTANCE,encodedCensus).getOrThrow().equals(census),"Families, homes and jobs round-trip");
        } finally {
            if(previous==null)level.removeAttached(NapoliCivicLife.CENSUS);else level.setAttached(NapoliCivicLife.CENSUS,previous);
            if(previousLife==null)level.removeAttached(CitySimulation.LIFE);else level.setAttached(CitySimulation.LIFE,previousLife);
        }
        helper.succeed();
    }
    @GameTest public void housingCatalogueHasCompleteDistinctNonOverlappingLots(GameTestHelper helper) {
        var houses=NapoliMergellina.PROPERTIES;
        helper.assertTrue(houses.size()>=35,"At least 35 purchasable houses");
        helper.assertTrue(NapoliCivicLife.Job.values().length>=70,"At least 70 professions");
        helper.assertTrue(houses.stream().map(NapoliMergellina.House::id).distinct().count()==houses.size(),"Stable unique house IDs");
        for(var h:houses) {
            var points=CityLivingHomes.points(h);
            helper.assertTrue(points.stream().map(CityLivingHomes.UsePoint::room).distinct().count()>=7,"Kitchen, bathroom, living room, bedrooms, study and laundry: "+h.id());
            for(var p:points) helper.assertTrue(h.contains(p.block())&&h.contains(p.stand()),"Furniture and use points stay inside "+h.id());
            for(var other:houses)if(h!=other)helper.assertTrue(h.x()+h.width()<=other.x()||other.x()+other.width()<=h.x()
                    ||h.z()+h.depth()<=other.z()||other.z()+other.depth()<=h.z(),"Property boundaries cannot overlap");
        }
        helper.succeed();
    }
    @GameTest public void infantSchoolNightShiftAndNeedsChooseDifferentRoutines(GameTestHelper helper) {
        var level=helper.getLevel();long day=NapoliBroadcast.day(level);var id=new UUID(0, 1);
        var child=new NapoliCivicLife.Citizen(id,"Bambina",true,day-8L*CityLifeConfig.settings.daysPerYear,0,-1,"","public_0","",false);
        // Fixed personalities keep match celebrations out of the routine assertions.
        helper.assertTrue(CitySimulation.activity(level,child,CitySimulation.fresh(id),17000)==NapoliHouseholds.Activity.SLEEP,"Children sleep at night");
        var baby=new NapoliCivicLife.Citizen(new UUID(0, 2),"Neonato",false,day,0,-1,"","public_0","",false);
        helper.assertTrue(CitySimulation.activity(level,baby,CitySimulation.fresh(baby.id()),6000)==NapoliHouseholds.Activity.REST,"Infants do not attend school");
        var doctor=new NapoliCivicLife.Citizen(new UUID(0, 3),"Medico di guardia",false,day-30L*CityLifeConfig.settings.daysPerYear,100,NapoliCivicLife.Job.NIGHT_DOCTOR.ordinal(),"","public_1","",false);
        helper.assertTrue(CitySimulation.activity(level,doctor,CitySimulation.fresh(doctor.id()),17000)==NapoliHouseholds.Activity.WORK,"Night workers stay on duty");
        var needs=new CitySimulation.Needs(5,80,80,80,80,80);
        var l=CitySimulation.fresh(id).withNeeds(needs,4,0,0,0);
        helper.assertTrue(CitySimulation.activity(level,child,l,12000)==NapoliHouseholds.Activity.EAT,"A hungry child eats instead of playing");
        helper.assertTrue(needs.change(-999,999,-999,999,999,-999).hunger()==0,"Need values are bounded");
        helper.succeed();
    }
    @GameTest public void inheritanceTransfersHouseAndMoneyExactlyOnce(GameTestHelper helper) {
        var city=helper.getLevel();var saved=city.removeAttached(NapoliProperties.OWNERS);var life=city.removeAttached(CitySimulation.LIFE);
        try {
            long day=NapoliBroadcast.day(city);var parent=UUID.randomUUID();var child=UUID.randomUUID();var house=NapoliMergellina.PROPERTIES.getFirst();
            var people=new LinkedHashMap<UUID,NapoliCivicLife.Citizen>();
            people.put(parent,new NapoliCivicLife.Citizen(parent,"Genitore",true,day-1000,103,-1,"",house.id(),"",false));
            people.put(child,new NapoliCivicLife.Citizen(child,"Erede",false,day-4,5,-1,"",house.id(),parent.toString(),false));
            NapoliProperties.claimForResident(city,house,parent);
            CitySimulation.inherit(city,people,day);
            helper.assertTrue(people.size()==1&&people.get(child).balance()==108,"Estate conserves the exact wallet balance");
            helper.assertTrue(NapoliProperties.owner(city,house).equals(child),"Property transfers to the child");
            CitySimulation.inherit(city,people,day);
            helper.assertTrue(people.get(child).balance()==108,"Inheritance cannot pay twice");
        } finally {
            if(saved==null)city.removeAttached(NapoliProperties.OWNERS);else city.setAttached(NapoliProperties.OWNERS,saved);
            if(life==null)city.removeAttached(CitySimulation.LIFE);else city.setAttached(CitySimulation.LIFE,life);
        }
        helper.succeed();
    }
    @GameTest public void mortgageBillingAndCalendarArePersistentAndBounded(GameTestHelper helper) {
        var city=helper.getLevel();var previous=city.removeAttached(CityHousingFinance.ACCOUNTS);
        try {
            var account=new CityHousingFinance.Account(UUID.randomUUID(),132,11,12,0,0,0,false);
            city.setAttached(CityHousingFinance.ACCOUNTS,Map.of("test",account));
            CityHousingFinance.bill(city,7);
            var billed=city.getAttached(CityHousingFinance.ACCOUNTS).get("test");
            helper.assertTrue(billed.arrears()==13&&billed.missed()==1,"One installment plus utilities is due");
            CityHousingFinance.bill(city,7);
            helper.assertTrue(city.getAttached(CityHousingFinance.ACCOUNTS).get("test").equals(billed),"Repeated day cannot bill twice");
            var json=CityHousingFinance.Account.CODEC.encodeStart(JsonOps.INSTANCE,billed).getOrThrow();
            helper.assertTrue(CityHousingFinance.Account.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow().equals(billed),"Mortgage survives reload");
            city.setAttached(CityHousingFinance.ACCOUNTS,Map.of("rounded",new CityHousingFinance.Account(UUID.randomUUID(),128,11,12,0,0,0,false)));
            CityHousingFinance.bill(city,84);
            helper.assertTrue(city.getAttached(CityHousingFinance.ACCOUNTS).get("rounded").arrears()==152,"Final installment never overcharges principal: 128 plus 24 utilities");
            helper.assertTrue(NapoliBroadcast.match(0)!=null&&NapoliBroadcast.match(1)==null,"Default fixtures follow the game calendar");
            helper.assertTrue(CityEvents.price(city,3)>0,"Dynamic prices remain positive");
        }finally{if(previous==null)city.removeAttached(CityHousingFinance.ACCOUNTS);else city.setAttached(CityHousingFinance.ACCOUNTS,previous);}
        helper.succeed();
    }
    @GameTest public void paternalInheritanceAndDebtSurviveTheFirstParentsDeath(GameTestHelper helper){
        var city=helper.getLevel();var owners=city.removeAttached(NapoliProperties.OWNERS);var lives=city.removeAttached(CitySimulation.LIFE);var accounts=city.removeAttached(CityHousingFinance.ACCOUNTS);
        try{
            long day=NapoliBroadcast.day(city);UUID mother=UUID.randomUUID(),father=UUID.randomUUID(),child=UUID.randomUUID();var house=NapoliMergellina.PROPERTIES.getFirst();
            var people=new LinkedHashMap<UUID,NapoliCivicLife.Citizen>();
            people.put(mother,new NapoliCivicLife.Citizen(mother,"Madre",true,day-30L*CityLifeConfig.settings.daysPerYear,10,-1,father.toString(),house.id(),"",false));
            people.put(father,new NapoliCivicLife.Citizen(father,"Padre",false,day-30L*CityLifeConfig.settings.daysPerYear,20,-1,mother.toString(),house.id(),"",false));
            people.put(child,new NapoliCivicLife.Citizen(child,"Figlio",false,day-5L*CityLifeConfig.settings.daysPerYear,5,-1,"",house.id(),mother.toString(),false));
            CitySimulation.parentage(city,child,mother,father);NapoliProperties.claimForResident(city,house,mother);
            CityHousingFinance.mortgageForResident(city,house,mother,day,10);
            var life=CitySimulation.life(city,mother);CitySimulation.put(city,mother,life.career(0,50,0,0,0,List.of()));
            CitySimulation.inherit(city,people,day);
            helper.assertTrue(NapoliProperties.owner(city,house).equals(father)&&people.get(father).balance()==30,"Spouse receives the first estate");
            helper.assertTrue(city.getAttached(CityHousingFinance.ACCOUNTS).get(house.id()).tenant().equals(father),"Mortgage transfers with ownership");
            CitySimulation.put(city,father,CitySimulation.fresh(father).career(0,50,0,0,0,List.of()));CitySimulation.inherit(city,people,day);
            helper.assertTrue(NapoliProperties.owner(city,house).equals(child)&&people.get(child).balance()==35,"The surviving child inherits from either parent");
            helper.assertTrue(city.getAttached(CityHousingFinance.ACCOUNTS).get(house.id()).tenant().equals(child),"The mortgage cannot disappear during inheritance");
        }finally{
            if(owners==null)city.removeAttached(NapoliProperties.OWNERS);else city.setAttached(NapoliProperties.OWNERS,owners);
            if(lives==null)city.removeAttached(CitySimulation.LIFE);else city.setAttached(CitySimulation.LIFE,lives);
            if(accounts==null)city.removeAttached(CityHousingFinance.ACCOUNTS);else city.setAttached(CityHousingFinance.ACCOUNTS,accounts);
        }helper.succeed();
    }
    @GameTest public void reportedNpcIsArrestedAndSentenceSerializes(GameTestHelper helper){
        var city=helper.getLevel();var previous=city.removeAttached(CityPublicOrder.INCIDENTS);var lives=city.removeAttached(CitySimulation.LIFE);
        var suspect=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,helper.absolutePos(new BlockPos(2,2,2)),0);
        var officer=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,helper.absolutePos(new BlockPos(3,2,2)),0);
        try{
            CityPublicOrder.report(city,suspect.getUUID(),CityPublicOrder.Offence.THEFT);
            for(int tick=0;tick<20;tick++){officer.tickCount++;CityPublicOrder.tickEntity(officer,city);}
            helper.assertTrue(CityPublicOrder.jailed(city,suspect.getUUID()),"A nearby officer arrests the reported suspect");
            var sentence=city.getAttached(CityPublicOrder.INCIDENTS).get(suspect.getUUID().toString());
            var json=CityPublicOrder.Incident.CODEC.encodeStart(JsonOps.INSTANCE,sentence).getOrThrow();
            helper.assertTrue(CityPublicOrder.Incident.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow().equals(sentence),"Cell and release deadline survive reload");
        }finally{
            suspect.discard();officer.discard();
            if(previous==null)city.removeAttached(CityPublicOrder.INCIDENTS);else city.setAttached(CityPublicOrder.INCIDENTS,previous);
            if(lives==null)city.removeAttached(CitySimulation.LIFE);else city.setAttached(CitySimulation.LIFE,lives);
        }helper.succeed();
    }
}
