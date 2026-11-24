package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.napuli.CityText.t;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** Shared scheduled public transport for players and census citizens. Empty distant vehicles are virtual. */
final class CityNetwork {
    enum Mode { BUS, TRAM, METRO }
    record Stop(String id,String name,BlockPos street){BlockPos boarding(Line line){return line.mode()==Mode.METRO?new BlockPos(street.getX()+5,line.level()+1,street.getZ()-2):new BlockPos(street.getX()+5,CityStreets.walkingHeight(street.getX()+5,street.getZ())+1,street.getZ());}BlockPos sign(){return new BlockPos(street.getX()+6,CityStreets.walkingHeight(street.getX()+6,street.getZ()+5)+2,street.getZ()+5); }BlockPos entrance(){return new BlockPos(street.getX()+5,CityStreets.walkingHeight(street.getX()+5,street.getZ()+5)+1,street.getZ()+5);}}
    record Line(String id,String name,Mode mode,List<String> stops,double speed,int capacity,int level,boolean night){}
    record Leg(String line,String from,String to){static final Codec<Leg> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("line").forGetter(Leg::line),Codec.STRING.fieldOf("from").forGetter(Leg::from),Codec.STRING.fieldOf("to").forGetter(Leg::to)).apply(i,Leg::new));}
    record Ticket(List<Leg> legs,String state,String vehicle,long created,long due,long delay,boolean paid){static final Codec<Ticket> CODEC=RecordCodecBuilder.create(i->i.group(Leg.CODEC.listOf().fieldOf("legs").forGetter(Ticket::legs),Codec.STRING.fieldOf("state").forGetter(Ticket::state),Codec.STRING.fieldOf("vehicle").forGetter(Ticket::vehicle),Codec.LONG.fieldOf("created").forGetter(Ticket::created),Codec.LONG.fieldOf("due").forGetter(Ticket::due),Codec.LONG.fieldOf("delay").forGetter(Ticket::delay),Codec.BOOL.fieldOf("paid").forGetter(Ticket::paid)).apply(i,Ticket::new));}
    record Frame(Vec3 from,Vec3 to,int start,int end,String stop){}
    record Position(Vec3 point,String stop,int phase){}
    record Plan(List<Frame> frames,int cycle,int fleet){}
    static final AttachmentType<Ticket> TICKET=AttachmentRegistry.<Ticket>builder().persistent(Ticket.CODEC).buildAndRegister(Napuli.id("public_transport_ticket"));
    static final AttachmentType<Long> PASS=AttachmentRegistry.<Long>builder().persistent(Codec.LONG).copyOnDeath().buildAndRegister(Napuli.id("urban_transfer_valid_until"));
    static final AttachmentType<String> WAYPOINT=AttachmentRegistry.<String>builder().persistent(Codec.STRING).buildAndRegister(Napuli.id("transport_stop_waypoint"));
    static final AttachmentType<Map<String,Ticket>> NPC=AttachmentRegistry.<Map<String,Ticket>>builder().persistent(Codec.unboundedMap(Codec.STRING,Ticket.CODEC)).buildAndRegister(Napuli.id("citizen_transport_tickets"));
    static final AttachmentType<Map<String,Long>> DELAYS=AttachmentRegistry.<Map<String,Long>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.LONG)).buildAndRegister(Napuli.id("public_transport_delays"));
    static final AttachmentType<Long> EPOCH=AttachmentRegistry.<Long>builder().persistent(Codec.LONG).buildAndRegister(Napuli.id("public_transport_epoch"));
    private static final Map<ServerLevel,Map<String,Long>> LIVE_DELAYS=new WeakHashMap<>();
    private record CachedPosition(long clock,Position position){}
    private static final Map<ServerLevel,Map<String,CachedPosition>> POSITIONS=new WeakHashMap<>();
    private static final Set<UUID> ABOARD=new HashSet<>();
    private static final Map<UUID,Long> WALK_UNTIL=new HashMap<>();
    private static final class Data {static final Map<String,Stop> STOPS=makeStops();static final List<Line> LINES=makeLines();static final Map<String,Plan> PLANS=makePlans();}
    static Collection<Stop> stops(){return Data.STOPS.values();}static List<Line> lines(){return Data.LINES;}static Stop stop(String id){return Data.STOPS.get(id);}static Line line(String id){return lines().stream().filter(l->l.id().equals(id)).findFirst().orElse(null);}static Plan plan(Line line){return Data.PLANS.get(line.id());}
    private static Stop create(String id,String name,BlockPos around){return new Stop(id,name,CityRoutes.nearest(around).position());}
    private static Map<String,Stop> makeStops(){var all=new LinkedHashMap<String,Stop>();for(var p:NapoliAtlas.PLACES)if(!p.id().equals("nisida"))all.put(p.id(),create(p.id(),p.name(),new BlockPos(p.x(),64,p.z())));
        all.put("sannazaro",create("sannazaro","Mergellina / Piazza Sannazaro",NapoliAtlas.find("sannazaro").arrival()));
        all.put("augusto",create("augusto","Augusto",new BlockPos(-1280,64,-50)));all.put("lala",create("lala","Lala",new BlockPos(-1110,64,40)));all.put("arco_mirelli",create("arco_mirelli","Arco Mirelli - Repubblica",new BlockPos(-870,64,100)));all.put("san_pasquale",create("san_pasquale","San Pasquale",new BlockPos(-730,64,100)));all.put("chiaia_metro",create("chiaia_metro","Chiaia - Monte di Dio",new BlockPos(-550,64,20)));all.put("duomo_metro",create("duomo_metro","Duomo - Piazza Nicola Amore",new BlockPos(500,64,-40)));
        return Collections.unmodifiableMap(all);
    }
    private static Line route(String id,String name,Mode mode,int level,String... stops){return new Line(id,name,mode,List.of(stops),mode==Mode.BUS?.42:mode==Mode.TRAM?.52:1.0,mode==Mode.BUS?8:mode==Mode.TRAM?12:16,level,id.equals("N7"));}
    private static List<Line> makeLines(){return List.of(
        route("M1","Metro M1 · fermate principali",Mode.METRO,18,"scampia","medaglie","vanvitelli","mann","dante","municipio","duomo_metro","garibaldi","direzionale"),
        route("M6","Metro M6 · Mostra–Municipio",Mode.METRO,10,"mostra","augusto","lala","sannazaro","arco_mirelli","san_pasquale","chiaia_metro","municipio"),
        route("B1","Bus B1 · Pianura, Soccavo e stadio",Mode.BUS,0,"dante","medaglie","soccavo","pianura","maradona","mostra"),
        route("B2","Bus B2 · Posillipo e Bagnoli",Mode.BUS,0,"mostra","sannazaro","donnanna","marechiaro","virgiliano","pausilypon","scienza","pontile"),
        route("B3","Bus B3 · Sanità e Capodimonte",Mode.BUS,0,"dante","sanita","fontanelle","san_gennaro","capodimonte","secondigliano","scampia"),
        route("B4","Bus B4 · Foria e Centrale",Mode.BUS,0,"dante","mann","cavour","orto","carlo_iii","albergo","duomo","capuana","nazionale","garibaldi"),
        route("B5","Bus B5 · quartieri orientali",Mode.BUS,0,"garibaldi","direzionale","ponticelli","barra","san_giovanni","pietrarsa"),
        route("B6","Bus B6 · Vomero e Chiaia",Mode.BUS,0,"dante","artisti","vanvitelli","santelmo","san_martino","floridiana","amedeo","martiri","vittoria","villa_comunale","sannazaro"),
        route("N7","Bus N7 · collegamento notturno",Mode.BUS,0,"secondigliano","garibaldi","municipio","mercato","carmine"),
        route("T1","Tram T1 · porto e Napoli est",Mode.TRAM,0,"municipio","mercato","carmine","san_giovanni","pietrarsa")
    );}
    static List<BlockPos> walkRoute(BlockPos from,BlockPos to){return CityRoutes.walk(from,to);}
    private static Map<String,Plan> makePlans(){var all=new HashMap<String,Plan>();for(var l:lines()){
        var frames=new ArrayList<Frame>();int clock=0;Vec3 cursor=null;var visits=new ArrayList<String>(l.stops());for(int i=l.stops().size()-2;i>=0;i--)visits.add(l.stops().get(i));
        for(int i=0;i<visits.size()-1;i++){var a=stop(visits.get(i));var b=stop(visits.get(i+1));List<BlockPos> points=l.mode()==Mode.METRO?List.of(new BlockPos(a.street().getX(),l.level()+1,a.street().getZ()),new BlockPos(b.street().getX(),l.level()+1,a.street().getZ()),new BlockPos(b.street().getX(),l.level()+1,b.street().getZ())):CityRoutes.between(a.street(),b.street());if(points.isEmpty())throw new IllegalStateException("Disconnected route "+l.id()+": "+a.id()+" -> "+b.id());
            var lane=new ArrayList<Vec3>();for(int j=0;j<points.size();j++){var p=points.get(j);var before=points.get(Math.max(0,j-1));var after=points.get(Math.min(points.size()-1,j+1));double dx=after.getX()-before.getX(),dz=after.getZ()-before.getZ(),length=Math.max(1,Math.hypot(dx,dz));lane.add(new Vec3(p.getX()+.5-dz/length*1.35,p.getY(),p.getZ()+.5+dx/length*1.35));}
            if(cursor==null)cursor=lane.getFirst();if(cursor.distanceTo(lane.getFirst())>.01){int ticks=Math.max(1,(int)Math.ceil(cursor.distanceTo(lane.getFirst())/l.speed()));frames.add(new Frame(cursor,lane.getFirst(),clock,clock+ticks,""));clock+=ticks;cursor=lane.getFirst();}
            frames.add(new Frame(cursor,cursor,clock,clock+80,a.id()));clock+=80;
            for(var next:lane){if(next.distanceTo(cursor)<.01)continue;int ticks=Math.max(1,(int)Math.ceil(cursor.distanceTo(next)/l.speed()));frames.add(new Frame(cursor,next,clock,clock+ticks,""));clock+=ticks;cursor=next;}
        }
        // The terminal dwell and the short lane change close a continuous round trip.
        frames.add(new Frame(cursor,cursor,clock,clock+80,l.stops().getFirst()));clock+=80;Vec3 first=frames.getFirst().from();int last=Math.max(1,(int)Math.ceil(cursor.distanceTo(first)/l.speed()));frames.add(new Frame(cursor,first,clock,clock+last,""));clock+=last;
        all.put(l.id(),new Plan(List.copyOf(frames),clock,Math.clamp((clock+399)/400,2,32)));
    }return Map.copyOf(all);}
    static Frame frameAt(Plan plan,int phase){int low=0,high=plan.frames().size()-1;while(low<=high){int mid=(low+high)>>>1;var f=plan.frames().get(mid);if(phase<f.start())high=mid-1;else if(phase>=f.end())low=mid+1;else return f;}throw new IllegalStateException("Incomplete transport clock");}
    static Position sample(Line l,int slot,long time){var p=plan(l);int phase=(int)Math.floorMod(time+(long)slot*p.cycle()/p.fleet(),p.cycle());var f=frameAt(p,phase);double t=(double)(phase-f.start())/Math.max(1,f.end()-f.start());var v=f.from().lerp(f.to(),t);if(l.mode()!=Mode.METRO){int x=(int)Math.floor(v.x),z=(int)Math.floor(v.z);double fx=v.x-x,fz=v.z-z;double h=(1-fx)*(1-fz)*CityStreets.walkingHeight(x,z)+fx*(1-fz)*CityStreets.walkingHeight(x+1,z)+(1-fx)*fz*CityStreets.walkingHeight(x,z+1)+fx*fz*CityStreets.walkingHeight(x+1,z+1);v=new Vec3(v.x,h+1,v.z);}return new Position(v,f.stop(),phase);}
    static String vehicleKey(Line l,int slot){return l.id()+"/"+slot;}static UUID vehicleId(String key){return UUID.nameUUIDFromBytes(("napuli:network:"+key).getBytes(StandardCharsets.UTF_8));}
    static long epoch(ServerLevel city){return city.getAttachedOrSet(EPOCH,city.getGameTime());}static Map<String,Long> delays(ServerLevel city){return LIVE_DELAYS.computeIfAbsent(city,k->new HashMap<>(city.getAttachedOrElse(DELAYS,Map.of())));}static long delay(ServerLevel city,String key){return delays(city).getOrDefault(key,0L);}static void delay(ServerLevel city,String key,long amount){delays(city).merge(key,amount,Long::sum);}
    static Position position(ServerLevel city,Line l,int slot){String key=vehicleKey(l,slot);long clock=city.getGameTime()-epoch(city)-delay(city,key);var cache=POSITIONS.computeIfAbsent(city,k->new HashMap<>());var old=cache.get(key);if(old!=null&&old.clock()==clock)return old.position();var result=sample(l,slot,clock);cache.put(key,new CachedPosition(clock,result));return result;}
    static boolean open(ServerLevel city,Line l){long time=NapoliBroadcast.time(city);return !CityWorkIncidents.stopped(city,l.id())&&(l.night()||time>=500&&time<19000)&&!(CityEvents.active(city)&&CityEvents.event(NapoliBroadcast.day(city))==CityEvents.Event.STRIKE);}
    static long until(Line l,int phase,String stop){int best=Integer.MAX_VALUE;for(var f:plan(l).frames())if(f.stop().equals(stop)){if(phase>=f.start()&&phase<f.end())return 0;best=Math.min(best,Math.floorMod(f.start()-phase,plan(l).cycle()));}return best==Integer.MAX_VALUE?-1:best;}
    static long eta(ServerLevel city,Line l,String origin){if(!open(city,l))return -1;long best=Long.MAX_VALUE;for(int slot=0;slot<plan(l).fleet();slot++)best=Math.min(best,until(l,position(city,l,slot).phase(),origin));return best==Long.MAX_VALUE?-1:best;}
    static List<Leg> itinerary(String from,String to){return itinerary(from,to,l->true);}
    static List<Leg> itinerary(ServerLevel city,String from,String to){return itinerary(from,to,l->open(city,l));}
    private static List<Leg> itinerary(String from,String to,java.util.function.Predicate<Line> available){if(from.equals(to))return List.of();record Edge(String to,String line,double cost){}var edges=new HashMap<String,List<Edge>>();for(var l:lines())if(available.test(l))for(int i=1;i<l.stops().size();i++){String a=l.stops().get(i-1),b=l.stops().get(i);double cost=Math.sqrt(stop(a).street().distSqr(stop(b).street()))/l.speed()+160;edges.computeIfAbsent(a,k->new ArrayList<>()).add(new Edge(b,l.id(),cost));edges.computeIfAbsent(b,k->new ArrayList<>()).add(new Edge(a,l.id(),cost));}
        record Q(String at,double cost){}var queue=new PriorityQueue<Q>(Comparator.comparingDouble(Q::cost));var costs=new HashMap<String,Double>();var previous=new HashMap<String,Leg>();costs.put(from,0.0);queue.add(new Q(from,0));while(!queue.isEmpty()){var q=queue.remove();if(q.at().equals(to))break;if(q.cost()>costs.getOrDefault(q.at(),Double.MAX_VALUE))continue;for(var e:edges.getOrDefault(q.at(),List.of())){double cost=q.cost()+e.cost();if(cost<costs.getOrDefault(e.to(),Double.MAX_VALUE)){costs.put(e.to(),cost);previous.put(e.to(),new Leg(e.line(),q.at(),e.to()));queue.add(new Q(e.to(),cost));}}}
        if(!previous.containsKey(to))return List.of();var route=new ArrayList<Leg>();for(String at=to;!at.equals(from);){var leg=previous.get(at);if(leg==null)return List.of();route.add(leg);at=leg.from();}Collections.reverse(route);var merged=new ArrayList<Leg>();for(var leg:route){if(!merged.isEmpty()&&merged.getLast().line().equals(leg.line())){var old=merged.removeLast();merged.add(new Leg(old.line(),old.from(),leg.to()));}else merged.add(leg);}return List.copyOf(merged);
    }
    static Stop nearest(BlockPos p){var active=new HashSet<String>();for(var l:lines())active.addAll(l.stops());return stops().stream().filter(s->active.contains(s.id())).min(Comparator.comparingDouble(s->(double)(s.street().getX()-p.getX())*(s.street().getX()-p.getX())+(double)(s.street().getZ()-p.getZ())*(s.street().getZ()-p.getZ()))).orElseThrow();}
    static void npcTicket(ServerLevel city,UUID id,Ticket ticket){var all=new HashMap<>(city.getAttachedOrElse(NPC,Map.of()));if(ticket==null)all.remove(id.toString());else all.put(id.toString(),ticket);city.setAttached(NPC,Map.copyOf(all));}
    static boolean inTransit(ServerLevel city,UUID id){var t=city.getAttachedOrElse(NPC,Map.of()).get(id.toString());return t!=null&&(t.state().equals("riding")||t.state().equals("exit"));}
    static boolean commute(NapoliResident npc,ServerLevel city,BlockPos goal){var ticket=city.getAttachedOrElse(NPC,Map.of()).get(npc.getUUID().toString());long now=city.getGameTime();if(ticket!=null){if(ticket.state().equals("riding")||ticket.state().equals("exit")){npc.setActivity(NapoliHouseholds.Activity.COMMUTE);return true;}if(now-ticket.created()>1200){npcTicket(city,npc.getUUID(),null);WALK_UNTIL.put(npc.getUUID(),now+2400);return false;}var leg=ticket.legs().getFirst();var l=line(leg.line());var origin=stop(leg.from());var target=l.mode()==Mode.METRO?origin.entrance():origin.boarding(l);npc.setActivity(NapoliHouseholds.Activity.WAIT);CityDomestic.move(npc,DistrictLife.localGoal(city,npc,target));return true;}
        if(goal.distToCenterSqr(npc.position())<160*160||WALK_UNTIL.getOrDefault(npc.getUUID(),0L)>now)return false;var from=nearest(npc.blockPosition());var to=nearest(goal);if(from.street().distToCenterSqr(npc.position())>180*180||to.street().distSqr(goal)>180*180)return false;var route=itinerary(city,from.id(),to.id());if(route.isEmpty()||!open(city,line(route.getFirst().line())))return false;npcTicket(city,npc.getUUID(),new Ticket(route,"waiting","",now,0,0,false));return true;
    }
    static CityVehicle vehicle(ServerLevel city,String key){return city.getEntity(vehicleId(key)) instanceof CityVehicle v?v:null;}
    static void ensureStation(ServerLevel city,Stop s){for(int x=(s.street().getX()-8)>>4;x<=(s.street().getX()+8)>>4;x++)for(int z=(s.street().getZ()-8)>>4;z<=(s.street().getZ()+8)>>4;z++){var chunk=city.getChunk(x,z);NapoliExpansion.generate(city,chunk);DistrictLife.fit(city,chunk);CityRailway.fit(city,chunk);}}
    private static CityVehicle spawn(ServerLevel city,Line l,int slot,boolean passenger){String key=vehicleKey(l,slot);var existing=vehicle(city,key);if(existing!=null)return existing;if(CityJourneys.known(city,vehicleId(key)))return null;var pos=position(city,l,slot).point();if(passenger)city.getChunkAt(BlockPos.containing(pos));if(!city.hasChunkAt(BlockPos.containing(pos)))return null;var v=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);if(v==null)return null;v.configureNetwork(key,l.mode()==Mode.METRO?3:l.mode()==Mode.TRAM?2:1);v.setUUID(vehicleId(key));v.setPos(pos);v.setCustomName(Component.literal(l.name()));v.setCustomNameVisible(true);return city.addFreshEntity(v)?v:null;}
    static void drive(CityVehicle v,ServerLevel city){var pieces=v.networkKey().split("/");var l=line(pieces[0]);if(l==null){v.discard();return;}int slot=Integer.parseInt(pieces[1]);long elapsed=city.getGameTime()-v.networkTick;if(elapsed>1)delay(city,v.networkKey(),elapsed-1);v.networkTick=city.getGameTime();if(CityWorkIncidents.stopped(city,l.id())){delay(city,v.networkKey(),1);return;}var sample=position(city,l,slot);var target=sample.point();if(v.getPassengers().isEmpty()&&city.players().stream().noneMatch(p->p.distanceToSqr(v)<112*112)){v.discard();return;}
        var at=BlockPos.containing(target);if(!v.getPassengers().isEmpty()){city.getChunkAt(at);var c=city.getChunkAt(at);NapoliExpansion.generate(city,c);DistrictLife.fit(city,c);CityRailway.fit(city,c);}
        if(!city.hasChunkAt(at)){delay(city,v.networkKey(),1);return;}for(int x=(at.getX()-3)>>4;x<=(at.getX()+3)>>4;x++)for(int z=(at.getZ()-3)>>4;z<=(at.getZ()+3)>>4;z++){var chunk=city.getChunkSource().getChunkNow(x,z);if(chunk==null&&!v.getPassengers().isEmpty())chunk=city.getChunk(x,z);if(chunk!=null)CityRailway.fit(city,chunk);}if(!open(city,l)&&v.getPassengers().isEmpty()){v.discard();return;}var motion=target.subtract(v.position());if(motion.length()>3){alignClock(city,v,l,sample.phase());return;}
        var ahead=v.getBoundingBox().move(motion.scale(5)).inflate(.05);boolean blocked=city.getEntities(v,ahead,e->e.isAlive()&&!e.isPassengerOfSameVehicle(v)&&((e instanceof CityVehicle other&&other.getUUID().compareTo(v.getUUID())<0)||e instanceof net.minecraft.world.entity.player.Player||e instanceof NapoliResident)).stream().anyMatch(e->!e.isPassenger());
        if(blocked&&motion.lengthSqr()>.001){delay(city,v.networkKey(),1);return;}var before=v.position();v.setOnGround(true);v.move(MoverType.SELF,motion);if(v.position().subtract(target).horizontalDistanceSqr()>.35*.35)delay(city,v.networkKey(),1);var moved=v.position().subtract(before);if(moved.horizontalDistanceSqr()>.0001){float yaw=(float)Math.toDegrees(Math.atan2(-moved.x,moved.z));v.setYRot(yaw);v.setYBodyRot(yaw);v.setYHeadRot(yaw);}v.setDeltaMovement(Vec3.ZERO);
    }
    private static void alignClock(ServerLevel city,CityVehicle vehicle,Line line,int currentPhase){
        double best=Double.MAX_VALUE;int phase=currentPhase;
        for(var frame:plan(line).frames()){var delta=frame.to().subtract(frame.from());double fraction=Math.clamp(vehicle.position().subtract(frame.from()).dot(delta)/Math.max(.0001,delta.lengthSqr()),0,1);var point=frame.from().lerp(frame.to(),fraction);int candidate=frame.start()+(int)(fraction*Math.max(1,frame.end()-frame.start()-1));int behind=Math.floorMod(currentPhase-candidate,plan(line).cycle());double distance=point.subtract(vehicle.position()).horizontalDistanceSqr()+behind*.000001;if(distance<best){best=distance;phase=candidate;}}
        delay(city,vehicle.networkKey(),Math.max(1,Math.floorMod(currentPhase-phase,plan(line).cycle())));
    }
    private static long arrivalDue(ServerLevel city,Line l,String key,String destination){int slot=Integer.parseInt(key.split("/")[1]);var p=position(city,l,slot);long wait=until(l,p.phase(),destination);return city.getGameTime()+Math.max(1,wait);}
    private static Ticket advance(Ticket t,long now){return t.legs().size()==1?null:new Ticket(List.copyOf(t.legs().subList(1,t.legs().size())),"transfer","",now,now+40,0,t.paid());}
    static Ticket process(ServerLevel city,LivingEntity actor,Ticket t,boolean player){if(t.legs().isEmpty())return null;var leg=t.legs().getFirst();var l=line(leg.line());var origin=stop(leg.from());var destination=stop(leg.to());long now=city.getGameTime();if(l==null||origin==null||destination==null)return null;
        if(t.state().equals("exit")){if(now<t.due())return t;ensureStation(city,destination);var exit=destination.entrance();actor.teleportTo(exit.getX()+.5,exit.getY(),exit.getZ()+.5);return null;}
        if(t.state().equals("riding")){
            boolean voluntary=player&&ABOARD.contains(actor.getUUID())&&(!(actor.getVehicle() instanceof CityVehicle v)||!v.networkKey().equals(t.vehicle()));if(voluntary){ABOARD.remove(actor.getUUID());return null;}
            if(now>=t.due()+Math.max(0,delay(city,t.vehicle())-t.delay())){actor.stopRiding();ensureStation(city,destination);var p=destination.boarding(l);actor.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);ABOARD.remove(actor.getUUID());return !player&&t.legs().size()==1&&l.mode()==Mode.METRO?new Ticket(t.legs(),"exit","",now,now+40,0,true):advance(t,now);}
            if(!(actor.getVehicle() instanceof CityVehicle)){var v=spawn(city,l,Integer.parseInt(t.vehicle().split("/")[1]),true);if(v!=null){actor.teleportTo(v.getX(),v.getY()+1,v.getZ());if(actor.startRiding(v,true,false))ABOARD.add(actor.getUUID());}}return t;
        }
        var board=origin.boarding(l);double horizontal=Math.hypot(actor.getX()-board.getX(),actor.getZ()-board.getZ());if(horizontal<10&&Math.abs(actor.getY()-board.getY())>5){
            if(!t.state().equals("lift"))return new Ticket(t.legs(),"lift","",t.created(),now+40,0,t.paid());if(now<t.due())return t;ensureStation(city,origin);actor.teleportTo(board.getX()+.5,board.getY(),board.getZ()+.5);t=new Ticket(t.legs(),"waiting","",t.created(),0,0,t.paid());
        }else if(t.state().equals("transfer")){if(now<t.due())return t;ensureStation(city,origin);actor.teleportTo(board.getX()+.5,board.getY(),board.getZ()+.5);t=new Ticket(t.legs(),"waiting","",t.created(),0,0,t.paid());}
        if(!open(city,l)||actor.blockPosition().distSqr(board)>12*12)return t;
        for(int slot=0;slot<plan(l).fleet();slot++){var where=position(city,l,slot);if(!where.stop().equals(origin.id())||!towards(l,where.phase(),origin.id(),destination.id()))continue;String key=vehicleKey(l,slot);var v=spawn(city,l,slot,true);if(v==null||!v.canAddPassenger(actor))continue;
            int fare=t.paid()?0:player&&((ServerPlayer)actor).getAttachedOrElse(PASS,0L)>now?0:2;var citizen=player?null:NapoliCivicLife.profile(city,actor.getUUID());if((player?NapoliStreet.cash((ServerPlayer)actor):citizen==null?0:citizen.balance())<fare){if(player)((ServerPlayer)actor).sendOverlayMessage(t("travel.fare_needed"));else WALK_UNTIL.put(actor.getUUID(),now+2400);return null;}
            if(actor.startRiding(v,true,false)){if(player){var p=(ServerPlayer)actor;if(fare>0){NapoliStreet.credit(p,-fare,"transport",l.id());WorkEconomy.revenue(city,fare);p.setAttached(PASS,now+6000);}CityOnboarding.event(p,CityOnboarding.TRANSIT);ABOARD.add(p.getUUID());}else if(fare>0)CityOccupations.money(city,actor.getUUID(),-fare);return new Ticket(t.legs(),"riding",key,t.created(),arrivalDue(city,l,key,destination.id()),delay(city,key),true);}
        }return t;
    }
    private static void tick(ServerLevel city){long now=city.getGameTime();epoch(city);
        if(now%20==0)for(var l:lines())if(open(city,l))for(int slot=0;slot<plan(l).fleet();slot++){var pos=position(city,l,slot).point();if(city.players().stream().anyMatch(p->p.distanceToSqr(pos)<96*96))spawn(city,l,slot,false);}
        for(var p:city.players()){var t=p.getAttached(TICKET);if(t!=null){if(!p.isAlive()||p.isSpectator()||Carabinieri.isJailed(p)){p.removeAttached(TICKET);continue;}var changed=process(city,p,t,true);if(changed==null)p.removeAttached(TICKET);else if(!changed.equals(t))p.setAttached(TICKET,changed);}
            if(now%40==0){var destination=stop(p.getAttachedOrElse(WAYPOINT,""));if(destination!=null){int d=(int)Math.sqrt(p.blockPosition().distSqr(destination.entrance()));if(d<8)p.removeAttached(WAYPOINT);else if(p.getAttachedOrElse(CityCompanion.TRACK,"auto").equals("auto"))p.sendOverlayMessage(Component.literal(destination.name()+" · "+d+" m · "+NapoliAtlasGuide.direction(destination.street().getX()-p.getX(),destination.street().getZ()-p.getZ())));}}
        }
        for(var entry:List.copyOf(city.getAttachedOrElse(NPC,Map.of()).entrySet())){var uuid=UUID.fromString(entry.getKey());var t=entry.getValue();var c=NapoliCivicLife.profile(city,uuid);if(c==null){npcTicket(city,uuid,null);continue;}var entity=city.getEntity(uuid);
            if(entity instanceof NapoliResident npc){var changed=process(city,npc,t,false);if(!Objects.equals(changed,t))npcTicket(city,uuid,changed);}else if(t.state().equals("riding")&&now>=t.due()+Math.max(0,delay(city,t.vehicle())-t.delay())){var leg=t.legs().getFirst();var pos=stop(leg.to()).entrance();CityJourneys.put(city,uuid,CityJourneys.plan(pos,DistrictLife.resident(c)?DistrictLife.destination(city,c,CitySimulation.activity(city,c,CitySimulation.life(city,uuid),NapoliBroadcast.time(city))):NapoliCivicLife.home(c),now,80));npcTicket(city,uuid,advance(t,now));}else if(entity==null&&t.state().equals("exit")){if(now>=t.due()){var pos=stop(t.legs().getFirst().to()).entrance();CityJourneys.put(city,uuid,CityJourneys.plan(pos,CitySimulation.destination(city,c),now,80));npcTicket(city,uuid,null);}}else if(!t.state().equals("riding")&&entity==null)npcTicket(city,uuid,null);
        }
        if(city.isRaining()&&now%5==0)for(var l:lines())if(l.mode()!=Mode.METRO)for(int slot=0;slot<plan(l).fleet();slot++)delay(city,vehicleKey(l,slot),1);
        if(now%200==0)city.setAttached(DELAYS,Map.copyOf(delays(city)));
    }
    static Component lineLabel(Line l){return t("travel.line",l.id(),stop(l.stops().getFirst()).name(),stop(l.stops().getLast()).name());}
    static int menu(ServerPlayer p){var buttons=new ArrayList<ActionButton>();var close=nearest(p.blockPosition());buttons.add(CitySocial.button(t("atlas.near_stop",close.name()),"/trasporti fermata "+close.id()));for(var l:lines())buttons.add(CitySocial.button(lineLabel(l),"/trasporti linea "+l.id()));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(t("menu.travel"),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(t("travel.network_info"),300)),List.of()),buttons,Optional.empty(),1)));return 1;}
    static int lineMenu(ServerPlayer p,String id){var l=line(id);if(l==null)return 0;var buttons=new ArrayList<ActionButton>();for(var s:l.stops())buttons.add(CitySocial.button(Component.literal(stop(s).name()),"/trasporti fermata "+s));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(lineLabel(l),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(t(l.mode()==Mode.METRO?"travel.metro_info":"travel.surface_info"),290)),List.of()),buttons,Optional.empty(),1)));return 1;}
    static int stopMenu(ServerPlayer p,String id){return stopMenu(p,id,0);}
    static int stopMenu(ServerPlayer p,String id,int page){var s=stop(id);if(s==null)return 0;var buttons=new ArrayList<ActionButton>();var body=new ArrayList<net.minecraft.server.dialog.body.DialogBody>();
        buttons.add(CitySocial.button(t("travel.live"),"/compagno corse "+id));
        for(var l:lines())if(l.stops().contains(id)){long eta=eta(p.level(),l,id);body.add(new PlainMessage(t("travel.native_eta",lineLabel(l),eta<0?CompanionTravel.unavailableText(p.level(),l):t("travel.eta",(eta+19)/20)),280));}
        buttons.add(CitySocial.button(t("travel.reach"),"/trasporti cerca "+id));
        if(lines().stream().anyMatch(l->l.mode()==Mode.METRO&&l.stops().contains(id)))buttons.add(CitySocial.button(t("travel.lift"),"/trasporti ascensore "+id));
        var destinations=lines().stream().flatMap(l->l.stops().stream()).distinct().filter(t->!t.equals(id)).toList();int first=Math.clamp(page,0,(destinations.size()-1)/8)*8;
        for(var target:destinations.subList(first,Math.min(first+8,destinations.size())))buttons.add(CitySocial.button(t("travel.go",stop(target).name()),"/trasporti viaggia "+target));
        if(first>0)buttons.add(CitySocial.button(t("previous"),"/trasporti pagina "+id+" "+(page-1)));if(first+8<destinations.size())buttons.add(CitySocial.button(t("next"),"/trasporti pagina "+id+" "+(page+1)));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(s.name()),Optional.empty(),true,false,DialogAction.CLOSE,body,List.of()),buttons,Optional.empty(),1)));return 1;}
    static int lift(ServerPlayer p,String id){
        var s=stop(id);if(s==null||p.level().dimension()!=NapoliMap.LEVEL||!p.isAlive()||p.isSpectator()||Carabinieri.isJailed(p)||p.isPassenger()||Math.hypot(p.getX()-s.street().getX(),p.getZ()-s.street().getZ())>12)return 0;
        var metro=lines().stream().filter(l->l.mode()==Mode.METRO&&l.stops().contains(id)).toList();if(metro.isEmpty())return 0;ensureStation(p.level(),s);var target=p.getY()<s.street().getY()-5?s.entrance():s.boarding(metro.getFirst());p.teleportTo(target.getX()+.5,target.getY(),target.getZ()+.5);return 1;
    }
    // Opposite-direction vehicles must not take a passenger the long way around.
    static boolean towards(Line l,int phase,String origin,String destination){long arrival=until(l,phase,destination);long nextOrigin=Long.MAX_VALUE;for(var f:plan(l).frames())if(f.stop().equals(origin)&&!(phase>=f.start()&&phase<f.end())){long d=Math.floorMod(f.start()-phase,plan(l).cycle());if(d>0)nextOrigin=Math.min(nextOrigin,d);}return arrival>=0&&arrival<nextOrigin;}
    static int book(ServerPlayer p,String destination){return book(p,destination,"");}
    static int book(ServerPlayer p,String destination,String preferred){
        if(p.level().dimension()!=NapoliMap.LEVEL||p.isSpectator()||!p.isAlive()||Carabinieri.isJailed(p))return CityCompanion.fail(p,t("travel.not_allowed"));
        var origin=nearest(p.blockPosition());
        if(Math.hypot(origin.street().getX()-p.getX(),origin.street().getZ()-p.getZ())>12)return CityCompanion.fail(p,t("travel.at_stop"));
        if(stop(destination)==null)return CityCompanion.fail(p,t("travel.unknown"));
        if(origin.id().equals(destination))return CityCompanion.fail(p,t("atlas.same_stop"));
        if(p.getAttached(TICKET)!=null)return CityCompanion.fail(p,t("travel.already"));
        var chosen=line(preferred);var route=preferred.isEmpty()?itinerary(p.level(),origin.id(),destination):chosen!=null&&open(p.level(),chosen)&&chosen.stops().contains(origin.id())&&chosen.stops().contains(destination)?List.of(new Leg(preferred,origin.id(),destination)):List.<Leg>of();
        if(route.isEmpty())return CityCompanion.fail(p,t("atlas.no_route"));
        p.setAttached(TICKET,new Ticket(route,"waiting","",p.level().getGameTime(),0,0,false));
        p.sendSystemMessage(t("travel.booked",origin.name()));return 1;
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(s->{var city=s.getLevel(NapoliMap.LEVEL);if(city!=null&&NapoliCivicLife.census(city)!=null)tick(city);});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{var city=s.getLevel(NapoliMap.LEVEL);if(city!=null){city.setAttached(DELAYS,Map.copyOf(delays(city)));for(var p:city.players())if(p.getAttached(TICKET)!=null)p.stopRiding();}ABOARD.clear();});ServerLifecycleEvents.SERVER_STOPPED.register(s->{LIVE_DELAYS.clear();WALK_UNTIL.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,s)->ABOARD.remove(handler.player.getUUID()));
        UseBlockCallback.EVENT.register((p,level,hand,hit)->{if(!(p instanceof ServerPlayer player)||hand!=InteractionHand.MAIN_HAND||level.dimension()!=NapoliMap.LEVEL)return InteractionResult.PASS;for(var s:stops())if((hit.getBlockPos().equals(s.entrance().above())||hit.getBlockPos().equals(s.sign())||lines().stream().filter(l->l.mode()==Mode.METRO&&l.stops().contains(s.id())).anyMatch(l->hit.getBlockPos().equals(new BlockPos(s.street().getX()+5,l.level()+1,s.street().getZ()-4))))){stopMenu(player,s.id());return InteractionResult.SUCCESS;}return InteractionResult.PASS;});
        CommandRegistrationCallback.EVENT.register((d,r,e)->{var root=Commands.literal("trasporti").executes(c->menu(c.getSource().getPlayerOrException()));for(var action:List.of("linea","fermata","cerca","viaggia","ascensore"))root.then(Commands.literal(action).then(Commands.argument("id",StringArgumentType.word()).executes(c->{var p=c.getSource().getPlayerOrException();String id=StringArgumentType.getString(c,"id");return switch(action){case "linea"->lineMenu(p,id);case "fermata"->stopMenu(p,id);case "viaggia"->book(p,id);case "ascensore"->lift(p,id);default->{if(stop(id)==null)yield 0;p.removeAttached(NapoliAtlasGuide.TARGET);p.removeAttached(CityCompanion.GOAL);p.setAttached(CityCompanion.TRACK,"meta");p.setAttached(WAYPOINT,id);yield 1;}};})));root.then(Commands.literal("corsa").then(Commands.argument("linea",StringArgumentType.word()).then(Commands.argument("destinazione",StringArgumentType.word()).executes(c->book(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"destinazione"),StringArgumentType.getString(c,"linea"))))));root.then(Commands.literal("pagina").then(Commands.argument("fermata",StringArgumentType.word()).then(Commands.argument("numero",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(c->stopMenu(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"fermata"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"numero"))))));root.then(Commands.literal("annulla").executes(c->{var p=c.getSource().getPlayerOrException();p.removeAttached(TICKET);p.removeAttached(WAYPOINT);ABOARD.remove(p.getUUID());p.stopRiding();return 1;}));root.then(Commands.literal("partenope").executes(c->CityTransit.legacyMenu(c.getSource().getPlayerOrException())));d.register(root);});
    }
    private CityNetwork(){}
}
