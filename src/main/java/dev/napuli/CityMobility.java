package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;

/** Resident-owned vehicles follow real streets. Unobserved rides continue without loading chunks. */
final class CityMobility {
    static final String TAG="napuli_citizen_vehicle";
    record Owned(String kind,BlockPos parked){
        static final Codec<Owned> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Owned::kind),BlockPos.CODEC.fieldOf("parked").forGetter(Owned::parked)).apply(i,Owned::new));
    }
    record Ride(String kind,CityJourneys.Journey road,int next,BlockPos goal){
        static final Codec<Ride> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Ride::kind),CityJourneys.Journey.CODEC.fieldOf("road").forGetter(Ride::road),Codec.INT.fieldOf("next").forGetter(Ride::next),BlockPos.CODEC.fieldOf("goal").forGetter(Ride::goal)).apply(i,Ride::new));
    }
    static final AttachmentType<Map<String,Owned>> OWNED=AttachmentRegistry.<Map<String,Owned>>builder().persistent(Codec.unboundedMap(Codec.STRING,Owned.CODEC)).buildAndRegister(Napuli.id("citizen_vehicles"));
    static final AttachmentType<Map<String,Ride>> RIDES=AttachmentRegistry.<Map<String,Ride>>builder().persistent(Codec.unboundedMap(Codec.STRING,Ride.CODEC)).buildAndRegister(Napuli.id("citizen_drives"));
    private static final AttachmentType<UUID> DRIVER=AttachmentRegistry.create(Napuli.id("citizen_vehicle_driver"));
    private static final AttachmentType<Long> BLOCKED=AttachmentRegistry.create(Napuli.id("citizen_vehicle_blocked"));
    private static final AttachmentType<Long> WALK=AttachmentRegistry.create(Napuli.id("citizen_walk_until"));
    private static final AttachmentType<Boolean> LEAVING=AttachmentRegistry.create(Napuli.id("citizen_vehicle_leaving"));
    // Unload callbacks run inside Minecraft's entity-section iterator. Defer removals until it ends.
    private static final Map<ServerLevel,Set<Entity>> UNLOADING=new WeakHashMap<>();
    static boolean managed(Entity vehicle){return vehicle!=null&&(vehicle.entityTags().contains(TAG)||vehicle instanceof Motorino m&&m.residentOwned()||vehicle instanceof CityVehicle c&&c.residentOwned());}
    static boolean mayBoard(Entity vehicle,Entity actor){return vehicle.level().isClientSide()?actor instanceof NapoliResident:actor.getUUID().equals(vehicle.getAttached(DRIVER));}
    static UUID identity(UUID owner){return UUID.nameUUIDFromBytes(("napuli:personal_vehicle:"+owner).getBytes(StandardCharsets.UTF_8));}
    static UUID owner(Entity vehicle){return vehicle.getAttached(DRIVER);}
    static String preference(NapoliCivicLife.Citizen c,long day){
        if(c.age(day)<18||c.profession()!=null&&c.profession().category==CityProfessions.Category.SECURITY)return "public";
        int n=Math.floorMod(c.id().hashCode(),10);return n<3?"scooter":n<5?"car":"public";
    }
    static double speed(String kind){return kind.equals("car")?.27:.25;}
    static Ride ride(ServerLevel city,UUID id){return city.getAttachedOrElse(RIDES,Map.of()).get(id.toString());}
    static boolean inTransit(ServerLevel city,UUID id){return ride(city,id)!=null;}
    static void cancel(ServerLevel city,UUID id){var r=ride(city,id);var vehicle=city.getEntity(identity(id));if(r!=null&&vehicle!=null)park(city,id,new Owned(r.kind(),vehicle.blockPosition()));putRide(city,id,null);}
    static BlockPos position(ServerLevel city,UUID id){var r=ride(city,id);return r==null?null:CityJourneys.sample(r.road(),city.getGameTime());}
    private static void putRide(ServerLevel city,UUID id,Ride value){var all=new HashMap<>(city.getAttachedOrElse(RIDES,Map.of()));if(value==null)all.remove(id.toString());else all.put(id.toString(),value);city.setAttached(RIDES,Map.copyOf(all));}
    private static void park(ServerLevel city,UUID id,Owned value){var all=new HashMap<>(city.getAttachedOrElse(OWNED,Map.of()));all.put(id.toString(),value);city.setAttached(OWNED,Map.copyOf(all));}
    static boolean western(BlockPos p){return p.getX()>=-550&&p.getX()<=-325&&p.getZ()>=-200&&p.getZ()<=208&&Math.abs(p.getY()-64)<=12;}
    static List<BlockPos> streets(BlockPos from,BlockPos to){
        if(western(from)&&western(to)){
            int start=Math.clamp(from.getZ(),-180,190),end=Math.clamp(to.getZ(),-180,190);
            return Math.abs(start-end)<48?List.of():List.of(new BlockPos(-339,64,start),new BlockPos(-339,64,end));
        }
        if(NapoliAtlas.ORIGINAL.contains(from.getX(),from.getZ())||NapoliAtlas.ORIGINAL.contains(to.getX(),to.getZ()))return List.of();
        var route=CityRoutes.between(from,to);
        return route.size()<2||route.getFirst().distSqr(from)>96*96||route.getLast().distSqr(to)>96*96?List.of():route;
    }
    private static BlockPos curb(BlockPos road,BlockPos other){
        int dx=Integer.compare(other.getX(),road.getX()),dz=Integer.compare(other.getZ(),road.getZ());
        int x=road.getX()-dz*4,z=road.getZ()+dx*4;
        return new BlockPos(x,CityStreets.walkingHeight(x,z)+1,z);
    }
    static List<BlockPos> route(List<BlockPos> streets,BlockPos parked){
        if(streets.size()<2)return List.of();var points=new ArrayList<BlockPos>();points.add(parked);
        for(int i=0;i<streets.size();i++){
            var at=streets.get(i);var a=streets.get(Math.max(0,i-1));var b=streets.get(Math.min(streets.size()-1,i+1));
            double dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ(),length=Math.max(1,Math.hypot(dx,dz));
            int x=at.getX()-(int)Math.round(dz/length*1.35),z=at.getZ()+(int)Math.round(dx/length*1.35);
            points.add(new BlockPos(x,CityStreets.walkingHeight(x,z)+1,z));
        }
        var end=streets.getLast();var before=streets.get(streets.size()-2);points.add(curb(end,end.offset(end.getX()-before.getX(),0,end.getZ()-before.getZ())));
        return List.copyOf(points);
    }
    private static CityJourneys.Journey journey(List<BlockPos> points,long now,String kind){return new CityJourneys.Journey(points,now,now+Math.max(1,(long)Math.ceil(CityJourneys.length(points)/speed(kind))),points.getLast(),now);}
    private static int count(ServerLevel city){int n=0;for(var entity:city.getAllEntities())if(managed(entity)&&!VehicleTheft.taken(entity))n++;return n;}
    private static boolean street(ServerLevel city,BlockPos p){return city.hasChunkAt(p)&&NapoliResidentHomes.walkable(city,p)&&CityLivingHomes.at(p)==null&&!CityProperty.home(city,p);}
    private static BlockPos exit(ServerLevel city,BlockPos at){
        for(int[] d:new int[][]{{-2,0},{2,0},{0,-2},{0,2},{-3,0},{3,0},{0,-3},{0,3}}){var p=at.offset(d[0],0,d[1]);if(!street(city,p))continue;var free=CityJourneys.spawn(city,p);if(free!=null&&street(city,free))return free;}
        return null;
    }
    static boolean commute(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,BlockPos goal){
        if(VehicleTheft.missing(city,c.id()))return CityNetwork.commute(npc,city,goal);
        var current=ride(city,c.id());
        if(current!=null){if(!managed(npc.getVehicle())){putRide(city,c.id(),null);npc.setAttached(WALK,city.getGameTime()+200);return false;}npc.setActivity(NapoliHouseholds.Activity.COMMUTE);if(current.goal().distSqr(goal)>32*32)finish(city,npc.getVehicle(),current);return true;}
        if(city.getAttachedOrElse(CityNetwork.NPC,Map.of()).containsKey(c.id().toString()))return CityNetwork.commute(npc,city,goal);
        if(npc.isPassenger()||npc.isSleeping()||CityPublicOrder.jailed(city,c.id()))return false;
        if(goal.distToCenterSqr(npc.position())<64*64||city.getGameTime()<npc.getAttachedOrElse(WALK,0L))return false;
        String kind=preference(c,NapoliBroadcast.day(city));
        if(CityLifeConfig.settings.citizenVehicleLimit==0||kind.equals("public")||kind.equals("scooter")&&city.isRaining())return CityNetwork.commute(npc,city,goal);
        int cost=kind.equals("car")?2:1;if(c.balance()<cost)return CityNetwork.commute(npc,city,goal);
        var owned=city.getAttachedOrElse(OWNED,Map.of()).get(c.id().toString());
        BlockPos start=owned==null?npc.blockPosition():owned.parked();var roads=streets(start,goal);
        if(roads.isEmpty()||start.distToCenterSqr(npc.position())>96*96)return CityNetwork.commute(npc,city,goal);
        if(owned==null){owned=new Owned(kind,curb(roads.getFirst(),roads.get(1)));if(owned.parked().distToCenterSqr(npc.position())>96*96)return CityNetwork.commute(npc,city,goal);park(city,c.id(),owned);}
        var path=route(roads,owned.parked());if(path.isEmpty()||!street(city,owned.parked()))return CityNetwork.commute(npc,city,goal);
        var approach=exit(city,owned.parked());if(approach==null)return CityNetwork.commute(npc,city,goal);
        if(owned.parked().distToCenterSqr(npc.position())>9){CityMovement.stand(npc);npc.setActivity(NapoliHouseholds.Activity.COMMUTE);CityDomestic.move(npc,CityRoutines.localGoal(city,npc,approach));return true;}
        var vehicle=city.getEntity(identity(c.id()));if(vehicle==null){if(count(city)>=CityLifeConfig.settings.citizenVehicleLimit)return CityNetwork.commute(npc,city,goal);vehicle=create(city,c,owned.kind(),owned.parked());}
        if(vehicle==null||!vehicle.getPassengers().isEmpty()||!npc.hasLineOfSight(vehicle)||!npc.startRiding(vehicle,false,false))return false;
        putRide(city,c.id(),new Ride(owned.kind(),journey(path,city.getGameTime(),owned.kind()),1,goal.immutable()));
        CityOccupations.money(city,c.id(),-cost);WorkEconomy.revenue(city,cost);CityMovement.release(city,c.id());npc.setActivity(NapoliHouseholds.Activity.COMMUTE);return true;
    }
    static Entity create(ServerLevel city,NapoliCivicLife.Citizen c,String kind,BlockPos at){
        if(VehicleTheft.missing(city,c.id())||CityJourneys.known(city,identity(c.id()))||!city.hasChunkAt(at))return null;
        var entity=kind.equals("scooter")?Napuli.MOTORINO.create(city,EntitySpawnReason.EVENT):Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);if(entity==null)return null;
        entity.setUUID(identity(c.id()));entity.addTag(TAG);entity.setAttached(DRIVER,c.id());entity.setPos(Vec3.atBottomCenterOf(at));if(entity instanceof Motorino bike)bike.configureResident();else if(entity instanceof CityVehicle car){car.configureResident();car.setNoAi(true);}
        entity.setCustomName(Component.literal((kind.equals("scooter")?"Motorino di ":"Auto di ")+c.name()));
        if(city.getBlockCollisions(entity,entity.getBoundingBox()).iterator().hasNext()||!city.getEntities(entity,entity.getBoundingBox(),e->e.isAlive()&&!e.isSpectator()&&!e.getUUID().equals(c.id())).isEmpty())return null;
        return city.addFreshEntity(entity)?entity:null;
    }
    static boolean drive(Entity vehicle,ServerLevel city){
        if(VehicleTheft.taken(vehicle))return false;
        if(!managed(vehicle))return false;UUID id=vehicle.getAttached(DRIVER);if(id==null){vehicle.discard();return true;}var r=ride(city,id);
        if(city.players().stream().noneMatch(p->p.distanceToSqr(vehicle)<112*112)){virtualize(vehicle,city);return true;}
        if(r==null){vehicle.setDeltaMovement(Vec3.ZERO);return true;}
        var owner=city.getEntity(id);if(!(owner instanceof NapoliResident npc)||npc.getVehicle()!=vehicle){park(city,id,new Owned(r.kind(),vehicle.blockPosition()));putRide(city,id,null);return true;}
        if(r.next()>=r.road().route().size()){finish(city,vehicle,r);return true;}
        var point=r.road().route().get(r.next());Vec3 target=Vec3.atBottomCenterOf(point),delta=target.subtract(vehicle.position());
        if(delta.horizontalDistanceSqr()<.35*.35){putRide(city,id,new Ride(r.kind(),r.road(),r.next()+1,r.goal()));vehicle.removeAttached(BLOCKED);return true;}
        double pace=speed(r.kind())*(city.isRaining()?.75:1);var motion=delta.normalize().scale(Math.min(pace,delta.length()));
        var next=vehicle.position().add(motion.scale(4));if(!city.hasChunkAt(BlockPos.containing(next))){virtualize(vehicle,city);return true;}
        var ahead=vehicle.getBoundingBox().expandTowards(motion.scale(4)).inflate(.12,.1,.12);
        boolean blocked=!city.getEntities(vehicle,ahead,e->e.isAlive()&&!e.isPassengerOfSameVehicle(vehicle)&&!e.isPassenger()&&!e.isSpectator()&&(e instanceof LivingEntity)).isEmpty();
        var before=vehicle.position();
        if(!blocked){vehicle.setYRot((float)Math.toDegrees(Math.atan2(-motion.x,motion.z)));if(vehicle instanceof LivingEntity living){living.setYBodyRot(vehicle.getYRot());living.setYHeadRot(vehicle.getYRot());}vehicle.move(MoverType.SELF,motion);blocked=vehicle.position().distanceToSqr(before)<.0001;}
        vehicle.setDeltaMovement(Vec3.ZERO);
        if(blocked){long since=vehicle.getAttachedOrSet(BLOCKED,city.getGameTime());if(city.getGameTime()-since>200)finish(city,vehicle,r);}else vehicle.removeAttached(BLOCKED);
        return true;
    }
    private static boolean finish(ServerLevel city,Entity vehicle,Ride r){
        UUID id=vehicle.getAttached(DRIVER);var owner=city.getEntity(id);BlockPos at=vehicle.blockPosition();
        if(owner instanceof NapoliResident npc&&npc.getVehicle()==vehicle){var outside=exit(city,at);if(outside==null)return false;npc.stopRiding();npc.snapTo(outside.getX()+.5,outside.getY(),outside.getZ()+.5,npc.getYRot(),0);npc.setAttached(WALK,city.getGameTime()+200);}
        park(city,id,new Owned(r.kind(),at));putRide(city,id,null);vehicle.setDeltaMovement(Vec3.ZERO);return true;
    }
    static boolean virtualize(NapoliResident npc,ServerLevel city){if(!managed(npc.getVehicle()))return false;virtualize(npc.getVehicle(),city);return true;}
    private static void virtualize(Entity vehicle,ServerLevel city){
        if(VehicleTheft.taken(vehicle))return;
        if(vehicle.getAttachedOrElse(LEAVING,false))return;vehicle.setAttached(LEAVING,true);UUID id=vehicle.getAttached(DRIVER);if(id==null){vehicle.discard();return;}var r=ride(city,id);
        if(r!=null){var points=new ArrayList<BlockPos>();points.add(vehicle.blockPosition());points.addAll(r.road().route().subList(Math.min(r.next(),r.road().route().size()-1),r.road().route().size()));putRide(city,id,new Ride(r.kind(),journey(List.copyOf(points),city.getGameTime(),r.kind()),1,r.goal()));}
        for(var passenger:List.copyOf(vehicle.getPassengers()))if(passenger instanceof NapoliResident npc){npc.stopRiding();CityJourneys.unmaterialized(city,npc.getUUID());npc.discard();}
        vehicle.discard();
    }
    private static void tick(ServerLevel city){
        long now=city.getGameTime();int restored=0;
        for(var entry:List.copyOf(city.getAttachedOrElse(RIDES,Map.of()).entrySet())){
            UUID id=UUID.fromString(entry.getKey());var r=entry.getValue();var c=NapoliCivicLife.profile(city,id);
            if(c==null){putRide(city,id,null);continue;}if(city.getEntity(identity(id))!=null)continue;
            if(now>=r.road().arrives()){
                park(city,id,new Owned(r.kind(),r.road().destination()));putRide(city,id,null);CityJourneys.put(city,id,CityJourneys.plan(r.road().destination(),r.goal(),now,80));continue;
            }
            var at=CityJourneys.sample(r.road(),now);
            if(restored>=2||count(city)>=CityLifeConfig.settings.citizenVehicleLimit||!city.hasChunkAt(at)||city.getEntity(id)!=null||CityJourneys.known(city,id)||!mayRestore(city,at))continue;
            var vehicle=create(city,c,r.kind(),at);if(vehicle==null)continue;
            var npc=NapoliResident.create(city,c.woman()?NapoliResident.Role.CIVIC_WOMAN:NapoliResident.Role.CIVIC_MAN,at,0);npc.setUUID(id);npc.setCustomName(Component.literal(c.name()));npc.setCivicAge(c.age(NapoliBroadcast.day(city)));
            if(!city.addFreshEntity(npc)||!npc.startRiding(vehicle,true,false)){npc.discard();vehicle.discard();continue;}
            double traveled=CityJourneys.length(r.road().route())*Math.clamp((double)(now-r.road().departed())/Math.max(1,r.road().arrives()-r.road().departed()),0,1);int next=1;
            while(next<r.road().route().size()-1&&traveled>Math.sqrt(r.road().route().get(next-1).distSqr(r.road().route().get(next)))){traveled-=Math.sqrt(r.road().route().get(next-1).distSqr(r.road().route().get(next)));next++;}
            putRide(city,id,new Ride(r.kind(),r.road(),next,r.goal()));markMaterialized(city,id);restored++;
        }
    }
    static boolean mayRestore(ServerLevel city,BlockPos at){
        var viewers=city.players().stream().filter(p->p.isAlive()&&!p.isSpectator()).toList();if(viewers.stream().noneMatch(p->at.distToCenterSqr(p.position())<64*64))return false;
        int total=0;var nearby=new HashMap<UUID,Integer>();for(var entity:city.getAllEntities())if(entity instanceof NapoliResident npc&&NapoliCivicLife.profile(city,npc.getUUID())!=null){total++;for(var p:viewers)if(p.distanceToSqr(npc)<96*96)nearby.merge(p.getUUID(),1,Integer::sum);}
        return total<CityLifeConfig.settings.visibleResidentsMaximum&&viewers.stream().noneMatch(p->at.distToCenterSqr(p.position())<96*96&&nearby.getOrDefault(p.getUUID(),0)>=CityLifeConfig.settings.visibleResidentsPerPlayer);
    }
    static void markMaterialized(ServerLevel city,UUID id){var census=NapoliCivicLife.census(city);if(census!=null)city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(c->c.id().equals(id)?c.materialized():c).toList()));}
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(s->{var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;var pending=UNLOADING.remove(city);if(pending!=null)for(var entity:pending)virtualize(entity,city);if(s.getTickCount()%20==0)tick(city);});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,city)->{if(managed(entity)&&!VehicleTheft.taken(entity)&&!entity.getAttachedOrElse(LEAVING,false)&&(entity.getRemovalReason()==null||!entity.getRemovalReason().shouldDestroy()))UNLOADING.computeIfAbsent(city,k->new HashSet<>()).add(entity);});
        ServerEntityEvents.ENTITY_LOAD.register((entity,city)->{if(managed(entity)&&!VehicleTheft.taken(entity)&&entity.getAttached(DRIVER)==null)UNLOADING.computeIfAbsent(city,k->new HashSet<>()).add(entity);});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;var vehicles=new ArrayList<Entity>();for(var e:city.getAllEntities())if(managed(e))vehicles.add(e);for(var e:vehicles)virtualize(e,city);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->UNLOADING.clear());
    }
    private CityMobility(){}
}
