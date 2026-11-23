package dev.napuli;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.*;
import net.minecraft.world.Container;

/** Opt-in dedicated-server soak. Uses four connected server players and real world/player saves. */
public final class CitySoakTest implements ModInitializer {
    private static final int DAYS=7, WRITE_TICKS=DAYS*24000;
    private final List<ServerPlayer> players=new ArrayList<>();
    private final List<EmbeddedChannel> channels=new ArrayList<>();
    private final NapoliMergellina.House house=CityLivingHomes.find("viva_1");
    private final List<UUID> residents=java.util.stream.IntStream.range(0,4).mapToObj(i->id("resident"+i)).toList();
    private static final Path CHECKPOINT=Path.of("soak-checkpoint.json");
    private MinecraftServer server;private ServerLevel city;private long started;private int tick,unloads,reloads;private boolean read,route,shoppingDebug,done,unloaded;
    private UUID repair,worker;private JsonObject checkpoint;private boolean employed;
    private UUID baker,doctor,patient,teacher,student,shopper;private int shoppingUnits;private boolean shoppingDelivered;
    private final Set<CityGestures.Gesture> observedGestures=EnumSet.noneOf(CityGestures.Gesture.class);
    private static UUID id(String name){return UUID.nameUUIDFromBytes(("napuli-soak/"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onInitialize(){
        if(!Boolean.getBoolean("napuli.citysoak"))return;
        read=System.getProperty("napuli.soak.phase","write").equals("read");
        route=System.getProperty("napuli.soak.phase","write").equals("route");
        shoppingDebug=System.getProperty("napuli.soak.phase","write").equals("shopping");
        ServerLifecycleEvents.SERVER_STARTED.register(s->{try{start(s);}catch(Throwable e){fail(e);}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done||server==null)return;try{step();}catch(Throwable e){fail(e);}});
    }
    private void start(MinecraftServer s)throws Exception{
        server=s;city=Objects.requireNonNull(s.getLevel(NapoliMap.LEVEL),"Napoli dimension must load in a real dedicated server");
        if(!read&&!route&&!shoppingDebug){check(!Files.exists(CHECKPOINT),"Use a fresh -PsoakRun for write phase");NapoliMap.ensureBuilt(city);}
        for(int i=0;i<4;i++)connect(i);
        started=city.getGameTime();
        if(read){checkpoint=JsonParser.parseString(Files.readString(CHECKPOINT)).getAsJsonObject();verifySaved();}
        else if(route)worker=NapoliCivicLife.census(city).people().stream().filter(c->c.name().equals("In cerca di lavoro")).findFirst().orElseThrow().id();else if(shoppingDebug)shopper=NapoliCivicLife.census(city).people().stream().filter(c->c.name().equals("Spesa di prova")).findFirst().orElseThrow().id();else setup();
        s.tickRateManager().requestGameToSprint(read?12000:WRITE_TICKS+100);
        Napuli.LOGGER.info("[SOAK] {} started: four connected players, {} ticks",read?"READ":"WRITE",read?12000:WRITE_TICKS);
    }
    private void connect(int i){
        String name=List.of("SoakAlice","SoakBruno","SoakCiro","SoakDora").get(i);var profile=new GameProfile(id(name),name);
        var p=new ServerPlayer(server,city,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);
        if(read||route||shoppingDebug)server.getPlayerList().loadPlayerData(new net.minecraft.server.players.NameAndId(profile)).ifPresent(nbt->{var input=net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,server.registryAccess(),nbt);p.load(input);input.read("Pos",net.minecraft.world.phys.Vec3.CODEC).ifPresent(p::setPos);});
        server.getPlayerList().placeNewPlayer(connection,p,CommonListenerCookie.createInitial(profile,false));players.add(p);channels.add(channel);p.setPermanentlyInvulnerable(true);
        p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        p.connection.teleport(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());
        if(route)move(p,new BlockPos(-417,64,-65));
    }
    private void setup(){
        var owners=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));owners.put(house.id(),players.getFirst().getUUID());city.setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));
        for(int i=0;i<4;i++){var npc=new NapoliResident(Napuli.RESIDENT,city);npc.setUUID(residents.get(i));npc.setPos(house.x()+4.5+i%2*2,64,house.z()+7.5+i/2*2);npc.setNoAi(true);npc.setPermanentlyInvulnerable(true);npc.addTag("napuli_soak_fixture");city.addFreshEntity(npc);}
        move(players.get(0),house.inside());for(int i=1;i<4;i++)move(players.get(i),house.entrance().south(3+i));
        var fridge=(Container)city.getBlockEntity(CityLivingHomes.use(house,CityLivingHomes.Furniture.FRIDGE).block());fridge.setItem(20,new ItemStack(Items.WHEAT,13));
        var host=players.getFirst();host.getInventory().add(new ItemStack(Items.BREAD,8));players.get(1).getInventory().add(new ItemStack(Items.BREAD,2));
        check(CityGatherings.create(host,house.id(),1)==1,"Host schedules dinner");
        for(int i=1;i<=2;i++){check(CityGatherings.invite(host,house.id(),players.get(i).getUUID().toString())==1,"Invitation sent");check(CityGatherings.respond(players.get(i),house.id(),true)==1,"RSVP accepted");}
        check(CityGatherings.food(host,house.id(),8)==1&&CityGatherings.food(players.get(1),house.id(),2)==1,"Shared food removes ten actual items");
        CityLeague.season(city);check(CityLeague.choose(host,"partenope")==1&&CityLeague.choose(players.get(1),"mergellina")==1,"Two opposing neighborhood teams");
    }
    private void step()throws Exception{
        tick=(int)(city.getGameTime()-started);network();
        if(route&&tick==200){var npc=(NapoliResident)city.getEntity(worker);check(npc!=null,"Route actor loaded");move(players.getFirst(),npc.blockPosition().east(2));var census=NapoliCivicLife.census(city);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(c->c.id().equals(worker)?c.withMoney(c.balance(),-1):c).toList()));CityPersonalGoals.assign(city,NapoliCivicLife.profile(city,worker),"work");check(CityPersonalGoals.act(players.getFirst(),worker,"accetta")==1,"Route diagnostic accepted");}
        if(route){routeStep();return;}
        if(shoppingDebug){shoppingStep();return;}
        if(tick%200==0){audit();for(var p:players){p.setHealth(20);p.getFoodData().setFoodLevel(20);}}
        if(tick%6000==0)Napuli.LOGGER.info("[SOAK] {} tick={} day={} population={} unloads={} reloads={}",read?"READ":"WRITE",tick,NapoliBroadcast.day(city),NapoliCivicLife.census(city).people().size(),unloads,reloads);
        if(read){readStep();return;}
        vividStep();
        if(tick==100){check(HousingCapacity.reservedOccupants(city,house,List.of())==5,"Five ordinary occupants remain admitted");move(players.get(3),house.inside());}
        if(tick==300)check(!house.contains(players.get(3).blockPosition()),"A stranger is ejected from the private full house");
        if(tick==1250){for(int i=1;i<=2;i++)move(players.get(i),house.inside().offset(0,0,i));HouseGuests.grant(city,house.id(),players.getFirst().getUUID(),players.get(2));}
        if(tick==1350){check(house.contains(players.get(1).blockPosition())&&house.contains(players.get(2).blockPosition()),"Two invited guests remain with five ordinary occupants");
            for(int i=0;i<3;i++)check(CityGatherings.eat(players.get(i),house.id())==1&&CityGatherings.eat(players.get(i),house.id())==0,"One portion per attendee");
            check(CityGatherings.party(city,house.id()).portions()==7,"Ten portions minus three meals");}
        if(tick==3800){check(CityGatherings.party(city,house.id()).closed(),"Dinner ends on time");check(!HouseGuests.invited(city,house.id(),players.get(1).getUUID())&&HouseGuests.invited(city,house.id(),players.get(2).getUUID()),"Temporary access expires and manually upgraded access survives");
            check(CityGatherings.refund(players.getFirst(),house.id())==1&&CityGatherings.refund(players.getFirst(),house.id())==0,"Leftovers refunded once");park();}
        if(tick>=4000&&tick<11000)cycleChunks(9000);
        if(tick==11200){check(unloads>0&&reloads>0,"A real house chunk unload/reload completed");startMatch("1/0");}
        if(tick==14500){var f=CityLeague.fixture(city,"1/0");check(f.state().equals("played")&&f.players().size()==2,"A multiplayer league match completes");for(int i=0;i<2;i++){var p=players.get(i);check(f.players().get(p.getUUID().toString()).ticks()>=600,"Connected participation counted");int money=NapoliStreet.cash(p);check(CityLeague.claim(p)==0&&NapoliStreet.cash(p)==money,"Automatic prizes cannot be claimed again");}park();}
        if(tick==74000){var npc=person("Riparatore",NapoliCivicLife.Job.BAKER.ordinal(),true);repair=npc.getUUID();CityPersonalGoals.assign(city,NapoliCivicLife.profile(city,repair),"oven");}
        if(tick==74200)repairOven();
        if(tick==98000)beginWork();
        if(tick==98200){var npc=(NapoliResident)city.getEntity(worker);check(npc!=null,"Employment beneficiary activated");move(players.getFirst(),npc.blockPosition().east(2));check(CityPersonalGoals.act(players.getFirst(),worker,"accetta")==1,"Employment escort accepted");npc.setNoAi(false);}
        if(worker!=null&&!employed&&tick>98200&&tick<110000&&tick%20==0){var npc=(NapoliResident)city.getEntity(worker);check(npc!=null,"Escorted resident stays loaded");move(players.getFirst(),npc.blockPosition().east(2));
            if(npc.blockPosition().distSqr(CityPersonalGoals.workplace())<=36){check(CityPersonalGoals.act(players.getFirst(),worker,"completa")==1,"Escorted resident accepts a real job at municipality");check(CityPersonalGoals.act(players.getFirst(),worker,"completa")==0,"Employment settles once");employed=true;park();}}
        if(tick==110000)check(employed,"Resident reaches municipality by walking");
        if(tick==162000){check(CityLeague.season(city).fixtures().stream().allMatch(f->f.state().equals("played")),"Full six-match calendar settles over seven days");check(!CityLeague.champion(city).isEmpty(),"Standings produce a champion");check(CityLeague.next(players.getFirst())==1,"Next season opens");startMatch("2/0");}
        if(tick==163200)park();
        if(tick>=WRITE_TICKS){check(unloads>0&&reloads>0&&employed,"Long-running checks complete");saveCheckpoint();finish("WRITE");}
    }
    private void cycleChunks(int returnAt){
        var pos=CityLivingHomes.use(house,CityLivingHomes.Furniture.FRIDGE).block();
        if(tick<returnAt&&city.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null&&residents.stream().allMatch(uuid->city.getEntity(uuid)==null)){if(!unloaded){unloaded=true;unloads++;}}
        if(tick==returnAt){check(unloaded,"House actually unloads when all four players leave");move(players.getFirst(),house.entrance().south(3));}
        if(tick>returnAt&&unloaded&&residents.stream().allMatch(uuid->city.getEntity(uuid)!=null)){unloaded=false;reloads++;checkFridge();}
    }
    private void routeStep(){
        if(tick%20!=0)return;var npc=(NapoliResident)city.getEntity(worker);
        if(npc!=null){move(players.getFirst(),npc.blockPosition().east(2));
            if(tick%200==0){var target=CityRoutines.localGoal(city,npc,CityPersonalGoals.workplace());Napuli.LOGGER.info("[SOAK] ROUTE tick={} position={} goal={} activity={}",tick,npc.blockPosition(),target,npc.activity());}
            if(tick>200&&npc.blockPosition().distSqr(CityPersonalGoals.workplace())<=36){check(CityPersonalGoals.act(players.getFirst(),worker,"completa")==1,"Escorted resident completes the route");finish("ROUTE");}
        }if(tick>=10000)throw new AssertionError("Route still blocked");
    }
    private void shoppingStep(){if(tick%20!=0)return;var npc=(NapoliResident)city.getEntity(shopper);if(npc!=null){move(players.get(2),npc.blockPosition().east(2));var home=CityLivingHomes.find("viva_7");var requested=CityLivingHomes.use(home,CityLivingHomes.Furniture.FRIDGE).stand();if(tick%200==0){var target=CityRoutines.localGoal(city,npc,requested);var path=npc.getNavigation().createPath(target,0);Napuli.LOGGER.info("[SOAK] SHOPPING tick={} position={} target={} reach={} activity={} cargo={}",tick,npc.blockPosition(),target,path!=null&&path.canReach(),npc.activity(),CityTraces.cargo(city,shopper));}if(CityTraces.cargo(city,shopper)==null)finish("SHOPPING");}if(tick>=10000)throw new AssertionError("Shopping route still blocked");}
    private void checkFridge(){var fridge=(Container)city.getBlockEntity(CityLivingHomes.use(house,CityLivingHomes.Furniture.FRIDGE).block());check(fridge!=null&&fridge.getItem(20).is(Items.WHEAT)&&fridge.getItem(20).getCount()==13,"Actual fridge inventory survives chunk reload");for(var uuid:residents)check(city.getEntity(uuid)!=null,"Same persistent fixture UUID returns");}
    private void repairOven(){
        var npc=city.getEntity(repair);check(npc!=null,"Repairer becomes visible after chunk activation");move(players.getFirst(),npc.blockPosition().east(2));
        var p=players.getFirst();check(CitySocial.nearby(p,repair)!=null,"Repair beneficiary is alive and nearby: jailed="+Carabinieri.isJailed(p)+", distance="+p.distanceToSqr(npc));
        p.getInventory().add(new ItemStack(Items.IRON_INGOT,4));int cash=NapoliStreet.cash(p);check(CityPersonalGoals.act(p,repair,"accetta")==1,"Repair promise accepted");check(CityPersonalGoals.act(p,repair,"completa")==1,"Four actual ingots repair the oven");check(NapoliStreet.cash(p)==cash+16&&CityPersonalGoals.act(p,repair,"completa")==0,"Repair pays once");park();
    }
    private void beginWork(){fixture("Impiegato del municipio",NapoliCivicLife.Job.SOCIAL_WORKER.ordinal(),"viva_9",CityServices.place("municipality"),true,30);var npc=person("In cerca di lavoro",-1,true);worker=npc.getUUID();CityPersonalGoals.assign(city,NapoliCivicLife.profile(city,worker),"work");move(players.getFirst(),npc.blockPosition().east(2));}
    private NapoliResident person(String name,int job,boolean noAi){
        var position=new BlockPos(-417,64,-65);city.getChunkAt(position);move(players.getFirst(),position.east(2));
        var npc=new NapoliResident(Napuli.RESIDENT,city);npc.setEmploymentAppearance(NapoliResident.Role.CIVIC_MAN,2);npc.setPos(position.getX()+.5,64,position.getZ()+.5);npc.setNoAi(noAi);npc.setPermanentlyInvulnerable(true);check(city.addFreshEntity(npc),"Goal beneficiary is inserted into the loaded world");
        long day=NapoliBroadcast.day(city);String home=name.equals("Riparatore")?"viva_28":"viva_27";var c=new NapoliCivicLife.Citizen(npc.getUUID(),name,false,day-30L*CityLifeConfig.settings.daysPerYear,100,job,"",home,"",true);var census=NapoliCivicLife.census(city);var all=new ArrayList<>(census.people());all.add(c);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(all)));own(home,c.id());return npc;
    }
    private void own(String home,UUID owner){var all=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));all.put(home,owner);city.setAttached(NapoliProperties.OWNERS,Map.copyOf(all));}
    private NapoliResident fixture(String name,int job,String home,BlockPos position,boolean noAi,int age){
        city.getChunkAt(position);var npc=new NapoliResident(Napuli.RESIDENT,city);npc.setEmploymentAppearance(NapoliResident.Role.CIVIC_MAN,2);npc.setPos(position.getX()+.5,position.getY(),position.getZ()+.5);npc.setNoAi(noAi);npc.setPermanentlyInvulnerable(true);check(city.addFreshEntity(npc),"Vivid-city actor inserted");
        long day=NapoliBroadcast.day(city);var c=new NapoliCivicLife.Citizen(npc.getUUID(),name,false,day-(long)age*CityLifeConfig.settings.daysPerYear,100,job,"",home,"",true);var census=NapoliCivicLife.census(city);var people=new ArrayList<>(census.people());people.add(c);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(people)));own(home,c.id());
        var l=CitySimulation.fresh(c.id());CitySimulation.put(city,c.id(),l.withNeeds(new CitySimulation.Needs(100,100,100,100,100,100),8,city.getGameTime(),0,0));return npc;
    }
    private void vividStep(){
        if(tick==6200){move(players.getFirst(),CityOccupations.station("baker").east(2));move(players.get(1),CityOccupations.station("baker").east(3));var stock=CityEconomy.stock(city,"forno");if(stock.bread()>=64)CityEconomy.reserveBread(city,"forno",64);baker=fixture("Fornaio di prova",NapoliCivicLife.Job.BAKER.ordinal(),"viva_2",CityOccupations.station("baker"),false,30).getUUID();}
        if(baker!=null&&tick>6400&&tick<10400&&city.getEntity(baker) instanceof NapoliResident npc)observedGestures.add(npc.gesture());
        if(baker!=null&&tick>=6400&&tick<=10400&&tick%400==0&&city.getEntity(baker) instanceof NapoliResident npc)Napuli.LOGGER.info("[SOAK] baker tick={} entityTicks={} noAi={} activity={} profession={} clock={} task={} needs={}",tick,npc.tickCount,npc.isNoAi(),npc.activity(),NapoliCivicLife.profile(city,baker).profession(),NapoliBroadcast.time(city),CityOccupations.task(city,baker),CitySimulation.life(city,baker).needs());
        if(tick==10400){var npc=city.getEntity(baker);check(CityOccupations.outputs(city,baker)>0,"The physical baker completes a real production cycle: task="+CityOccupations.task(city,baker)+", position="+(npc==null?"unloaded":npc.blockPosition())+", stock="+CityEconomy.stock(city,"forno"));check(observedGestures.contains(CityGestures.Gesture.CHOP)&&observedGestures.contains(CityGestures.Gesture.STIR),"Different production gestures are observed during the actual cycle");}
        if(tick==27000){move(players.get(1),CityServices.place("school").south(3));teacher=fixture("Insegnante di prova",NapoliCivicLife.Job.TEACHER.ordinal(),"viva_3",CityServices.place("school"),false,30).getUUID();student=fixture("Alunno di prova",-1,"viva_4",CityServices.place("school").south(2),false,9).getUUID();}
        if(tick==29000){var visit=CityServices.visit(city,student);check(visit!=null&&visit.kind().equals("school")&&visit.state().equals("completed")&&CitySimulation.life(city,student).experience()>0,"A real pupil attends a staffed lesson and earns progress once");}
        if(tick==30000){move(players.getFirst(),CityServices.place("doctor").south(3));doctor=fixture("Medico di prova",NapoliCivicLife.Job.DOCTOR.ordinal(),"viva_5",CityServices.place("doctor"),false,30).getUUID();patient=fixture("Paziente di prova",-1,"viva_6",CityServices.place("doctor").south(2),false,30).getUUID();var l=CitySimulation.life(city,patient);CitySimulation.put(city,patient,l.career(30,l.reputation(),l.experience(),l.rank(),l.absences(),l.memories()));CityServices.book(city,NapoliCivicLife.profile(city,patient),"doctor");}
        if(tick==32000){var visit=CityServices.visit(city,patient);check(visit!=null&&visit.state().equals("completed")&&CitySimulation.life(city,patient).health()>=55,"A present doctor examines the waiting patient using real medicine");park();}
        if(tick==36000){var residence=CityLivingHomes.find("viva_8");var resident=fixture("Bucato di prova",-1,"viva_8",residence.inside(),false,30);CityJourneys.withdraw(city,resident);var h=CityTraces.home(city,"viva_8");CityTraces.put(city,"viva_8",new CityTraces.Home(h.dishes(),3,true,city.getGameTime()+1800,city.getGameTime(),h.generated(),h.cleaned()));check(city.players().stream().noneMatch(p->residence.inside().distToCenterSqr(p.position())<96*96),"Laundry is outside every player's simulation area");server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather rain 600");}
        if(tick==46000){check(!CityTraces.home(city,"viva_8").outside(),"The available resident retrieves washing while every player is elsewhere: rain="+city.isRaining()+", home="+CityTraces.home(city,"viva_8"));server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear 600");}
        if(tick==50000){move(players.get(2),CityProfessions.Category.TRADE.site().offset(8,0,7));shopper=fixture("Spesa di prova",-1,"viva_7",CityProfessions.Category.TRADE.site().offset(8,0,7),true,30).getUUID();var l=CitySimulation.life(city,shopper);CitySimulation.put(city,shopper,l.withNeeds(new CitySimulation.Needs(30,100,100,100,100,100),0,city.getGameTime(),0,0));}
        if(tick==50200){var s=CityEconomy.stock(city,"mercato");CityEconomy.put(city,"mercato",new CityEconomy.Stock(s.grain(),s.flour(),Math.max(8,s.bread()),s.processed(),s.delivered()));var c=NapoliCivicLife.profile(city,shopper);int cash=c.balance();CityEconomy.shopResident(city,c);var cargo=CityTraces.cargo(city,shopper);check(cargo!=null&&cargo.meals()>0&&NapoliCivicLife.profile(city,shopper).balance()==cash-cargo.meals()*2,"Shopping reserves finite paid groceries instead of creating an inventory prop");shoppingUnits=cargo.meals();((NapoliResident)city.getEntity(shopper)).setNoAi(false);}
        if(shopper!=null&&!shoppingDelivered&&tick>50200&&tick<62000&&tick%20==0){var npc=(NapoliResident)city.getEntity(shopper);check(npc!=null,"The shopper remains visible during the whole walk home");move(players.get(2),npc.blockPosition().east(2));if(CityTraces.cargo(city,shopper)==null){var h=CityLivingHomes.find("viva_7");var fridge=(Container)city.getBlockEntity(CityLivingHomes.use(h,CityLivingHomes.Furniture.FRIDGE).block());int bread=0;for(int i=0;i<fridge.getContainerSize();i++)if(fridge.getItem(i).is(Items.BREAD))bread+=fridge.getItem(i).getCount();check(bread>=shoppingUnits-1&&!CityTraces.unload(city,NapoliCivicLife.profile(city,shopper)),"Groceries arrive in the actual fridge and cannot be unloaded twice");shoppingDelivered=true;move(players.get(2),FootballGround.START_RUN.east());}}
        if(tick==62000)check(shoppingDelivered,"The shopper carries groceries all the way home without teleporting");
    }
    private void startMatch(String fixture){check(CityLeague.start(players.getFirst(),fixture)==1&&CityLeague.start(players.get(1),fixture)==1,"Two connected players join the same scheduled match");move(players.get(2),FootballGround.STAND);move(players.get(3),FootballGround.STAND);}
    private void park(){for(var p:players)move(p,FootballGround.START_RUN.east());}
    private void move(ServerPlayer p,BlockPos pos){p.teleportTo(city,pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),0,0,true);city.getChunkSource().move(p);}
    private void network(){for(int i=0;i<channels.size();i++){var channel=channels.get(i);players.get(i).connection.tick();players.get(i).level().getChunkSource().move(players.get(i));channel.runPendingTasks();Object packet;int budget=512,teleports=0;while(budget-->0&&(packet=channel.readOutbound())!=null){try{
            if(packet instanceof ClientboundKeepAlivePacket keep)players.get(i).connection.handleKeepAlive(new ServerboundKeepAlivePacket(keep.getId()));
            if(packet instanceof ClientboundChunkBatchFinishedPacket)players.get(i).connection.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(64));
            if(packet instanceof ClientboundPlayerPositionPacket tp){check(++teleports<=8,"Teleport acknowledgements converge");var p=players.get(i);p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(tp.id(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot()));}
        }finally{ReferenceCountUtil.release(packet);}}}}
    private void audit(){
        check(server.getPlayerList().getPlayerCount()==4,"All four multiplayer connections stay online");var people=NapoliCivicLife.census(city).people();check(people.stream().map(NapoliCivicLife.Citizen::id).distinct().count()==people.size(),"No duplicated census UUIDs");check(people.size()>=120&&people.size()<180,"Population remains bounded");
        for(var p:players)check(p.isAlive(),"Connected participant stays alive: "+p.getName().getString()+" at "+p.blockPosition());
        for(String shop:CityEconomy.SHOPS){var s=CityEconomy.stock(city,shop);check(s.grain()>=0&&s.flour()>=0&&s.bread()>=0&&s.grain()<=512&&s.bread()<=192,"Finite stock remains consistent: "+shop);}
        int props=0;for(var e:city.getAllEntities())if(e.entityTags().contains(CityTraces.PROP))props++;check(props<=320,"Decorative traces stay bounded in loaded areas");for(var cargo:city.getAttachedOrElse(CityTraces.CARGO,Map.of()).values())check(cargo.meals()>0&&cargo.meals()<=24,"Carried groceries remain finite");
    }
    private void saveCheckpoint()throws Exception{
        var f=CityLeague.fixture(city,"2/0");check(f.state().equals("playing")&&f.elapsed()>=1000&&f.elapsed()<3000,"A real match is suspended before restart");
        var data=new JsonObject();data.add("season",CityLeague.Season.CODEC.encodeStart(JsonOps.INSTANCE,CityLeague.season(city)).getOrThrow());
        data.add("parties",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityGatherings.Party.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttached(CityGatherings.PARTIES)).getOrThrow());
        data.add("stories",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityReputation.Story.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttached(CityReputation.STORIES)).getOrThrow());
        data.add("bonds",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CitySocial.Bond.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttached(CitySocial.BONDS)).getOrThrow());
        data.add("journeys",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityJourneys.Journey.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttachedOrElse(CityJourneys.JOURNEYS,Map.of())).getOrThrow());
        data.add("visits",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityServices.Visit.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttachedOrElse(CityServices.VISITS,Map.of())).getOrThrow());
        data.add("homes",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityTraces.Home.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttachedOrElse(CityTraces.HOMES,Map.of())).getOrThrow());
        data.add("events",com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityRelationships.Event.CODEC.listOf()).encodeStart(JsonOps.INSTANCE,city.getAttachedOrElse(CityRelationships.EVENTS,Map.of())).getOrThrow());
        data.addProperty("repair",repair.toString());data.addProperty("worker",worker.toString());var wallets=new JsonArray();for(var p:players)wallets.add(NapoliStreet.cash(p));data.add("wallets",wallets);
        Files.writeString(CHECKPOINT,new GsonBuilder().setPrettyPrinting().create().toJson(data));server.saveEverything(true,true,true);
    }
    private void verifySaved(){
        var actual=CityLeague.Season.CODEC.encodeStart(JsonOps.INSTANCE,CityLeague.season(city)).getOrThrow();check(actual.equals(checkpoint.get("season")),"League scores, clock, participation and standings survive full server restart");
        var parties=com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityGatherings.Party.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttached(CityGatherings.PARTIES)).getOrThrow();check(parties.equals(checkpoint.get("parties")),"Dinner consumption and settlement survive full restart");
        var stories=com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityReputation.Story.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttached(CityReputation.STORIES)).getOrThrow();check(stories.equals(checkpoint.get("stories")),"Reputation sources and intermediaries survive full restart");
        var bonds=com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CitySocial.Bond.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttached(CitySocial.BONDS)).getOrThrow();check(bonds.equals(checkpoint.get("bonds")),"Shared NPC memories survive full restart");
        check(com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityJourneys.Journey.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttachedOrElse(CityJourneys.JOURNEYS,Map.of())).getOrThrow().equals(checkpoint.get("journeys")),"Journey positions and ETAs survive full restart");
        check(com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityServices.Visit.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttachedOrElse(CityServices.VISITS,Map.of())).getOrThrow().equals(checkpoint.get("visits")),"School and medical service progress survives restart");
        check(com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityTraces.Home.CODEC).encodeStart(JsonOps.INSTANCE,city.getAttachedOrElse(CityTraces.HOMES,Map.of())).getOrThrow().equals(checkpoint.get("homes")),"Household chores and traces survive restart");
        check(com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING,CityRelationships.Event.CODEC.listOf()).encodeStart(JsonOps.INSTANCE,city.getAttachedOrElse(CityRelationships.EVENTS,Map.of())).getOrThrow().equals(checkpoint.get("events")),"Personal life events survive restart");
        for(int i=0;i<4;i++)check(NapoliStreet.cash(players.get(i))==checkpoint.getAsJsonArray("wallets").get(i).getAsInt(),"Actual player.dat wallet reloaded");
        repair=UUID.fromString(checkpoint.get("repair").getAsString());worker=UUID.fromString(checkpoint.get("worker").getAsString());
        check(CityPersonalGoals.goal(city,repair).complete()&&CityPersonalGoals.goal(city,worker).complete(),"Completed goals survive restart");check(NapoliCivicLife.profile(city,worker).profession()!=null,"Employment survives restart");
        check(!HouseGuests.invited(city,house.id(),players.get(1).getUUID())&&HouseGuests.invited(city,house.id(),players.get(2).getUUID()),"Temporary and permanent invitations remain distinct after restart");
        for(var p:players){int cash=NapoliStreet.cash(p);check(CityLeague.claim(p)==0&&NapoliStreet.cash(p)==cash,"Prize receipts prevent replay after reconnect");}
    }
    private void readStep()throws Exception{
        if(tick==100)startMatch("2/0");
        if(tick==2300){check(CityLeague.fixture(city,"2/0").state().equals("played"),"Interrupted match resumes and completes after restart");for(var p:players){int money=NapoliStreet.cash(p);check(CityLeague.claim(p)==0&&NapoliStreet.cash(p)==money,"Resumed match settles once");}park();}
        if(tick>=2500&&tick<10000)cycleChunks(8000);
        if(tick>=10500){check(unloads>0&&reloads>0,"Chunk persistence checked again after restart");finish("READ");}
    }
    private void finish(String phase){done=true;Napuli.LOGGER.info("[SOAK] {} PASSED: ticks={}, players=4, unloads={}, reloads={}",phase,tick,unloads,reloads);server.halt(false);}
    private void fail(Throwable e){done=true;Napuli.LOGGER.error("[SOAK] FAILED at tick="+tick,e);if(server!=null)server.halt(false);}
}
