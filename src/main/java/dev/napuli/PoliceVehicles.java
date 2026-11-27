package dev.napuli;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;

/** Bounded street pursuit. Vehicles only receive radio sightings, never a hidden player's location. */
final class PoliceVehicles {
    static final String TAG="napuli_police_vehicle";
    private static final AttachmentType<UUID> DRIVER=AttachmentRegistry.<UUID>builder().persistent(UUIDUtil.CODEC).buildAndRegister(Napuli.id("police_vehicle_driver"));
    private static final AttachmentType<Boolean> LEGACY=AttachmentRegistry.<Boolean>builder().persistent(com.mojang.serialization.Codec.BOOL).buildAndRegister(Napuli.id("police_vehicle_persistent_driver"));
    private static final AttachmentType<Drive> DRIVE=AttachmentRegistry.create(Napuli.id("police_vehicle_drive"));
    private static final AttachmentType<Long> REST=AttachmentRegistry.create(Napuli.id("police_vehicle_rest"));
    private static final Map<ServerLevel,Set<Entity>> RETIRING=new WeakHashMap<>();
    private static final class Drive {
        UUID suspect;BlockPos goal;long observed,repath,blocked;List<BlockPos> route;int next=1;
        Drive(UUID suspect,BlockPos goal,long observed,List<BlockPos> route){this.suspect=suspect;this.goal=goal;this.observed=observed;this.route=route;}
    }
    static boolean managed(Entity vehicle){return vehicle!=null&&(vehicle.entityTags().contains(TAG)||vehicle instanceof Motorino m&&m.policeVehicle()||vehicle instanceof CityVehicle c&&c.policeVehicle());}
    static boolean riding(Entity officer){return managed(officer.getVehicle());}
    static UUID identity(UUID officer){return UUID.nameUUIDFromBytes(("napuli:police_vehicle:"+officer).getBytes(StandardCharsets.UTF_8));}
    static boolean mayBoard(Entity vehicle,Entity passenger){return passenger instanceof NapoliResident npc&&npc.role()==NapoliResident.Role.CARABINIERE&&(vehicle.level().isClientSide()||passenger.getUUID().equals(vehicle.getAttached(DRIVER)));}
    private static NapoliResident driver(Entity vehicle){var id=vehicle.getAttached(DRIVER);var actor=id==null?null:((ServerLevel)vehicle.level()).getEntity(id);if(actor instanceof NapoliResident npc)return npc;return vehicle.getPassengers().stream().filter(p->p instanceof NapoliResident).map(p->(NapoliResident)p).findFirst().orElse(null);}
    static boolean saved(Entity vehicle){return vehicle.getAttachedOrElse(LEGACY,false);}
    static String mode(int role){return (role&1)==0?"scooter":"car";}
    static double speed(Entity vehicle){return vehicle instanceof Motorino?.43:.40;}
    static boolean road(ServerLevel city,BlockPos at){
        if(!city.hasChunkAt(at)||!NapoliResidentHomes.walkable(city,at)||CityLivingHomes.at(at)!=null||CityProperty.home(city,at)||Math.abs(at.getY()-CityStreets.walkingHeight(at.getX(),at.getZ())-1)>1)return false;
        if(CityMobility.western(at))return Math.abs(at.getX()+339)<=5&&at.getZ()>=-184&&at.getZ()<=199;
        return !NapoliAtlas.ORIGINAL.contains(at.getX(),at.getZ())&&CityStreets.paved(at.getX(),at.getZ());
    }
    static List<BlockPos> path(BlockPos from,BlockPos goal){
        List<BlockPos> streets;
        if(CityMobility.western(from)&&CityMobility.western(goal))streets=List.of(new BlockPos(-339,64,Math.clamp(from.getZ(),-184,199)),new BlockPos(-339,64,Math.clamp(goal.getZ(),-184,199)));
        else{if(NapoliAtlas.ORIGINAL.contains(from.getX(),from.getZ())||NapoliAtlas.ORIGINAL.contains(goal.getX(),goal.getZ()))return List.of();streets=CityRoutes.between(from,goal);if(streets.size()<2||streets.getFirst().distSqr(from)>26*26||streets.getLast().distSqr(goal)>80*80)return List.of();}
        var points=new ArrayList<>(CityMobility.route(streets,from));if(points.size()>3){var a=Vec3.atBottomCenterOf(points.get(1));var edge=Vec3.atBottomCenterOf(points.get(2)).subtract(a);double t=Vec3.atBottomCenterOf(from).subtract(a).dot(edge)/Math.max(.01,edge.lengthSqr());if(t>0&&t<1)points.remove(1);}return List.copyOf(points);
    }
    private static int count(ServerLevel city){int n=0;for(var e:city.getAllEntities())if(managed(e))n++;return n;}
    static boolean pursue(NapoliResident officer,ServerPlayer suspect,BlockPos known,long observed,int role){
        var city=(ServerLevel)officer.level();long now=city.getGameTime();
        if(riding(officer)){var vehicle=officer.getVehicle();var d=vehicle.getAttached(DRIVE);if(d==null||!d.suspect.equals(suspect.getUUID())){requestStop(officer);return true;}d.goal=known.immutable();d.observed=observed;return true;}
        if(officer.isPassenger()||role>1||now<officer.getAttachedOrElse(REST,0L)||known.distToCenterSqr(officer.position())<24*24||!road(city,officer.blockPosition())||count(city)>=CityLifeConfig.settings.policeVehicleLimit)return false;
        var route=path(officer.blockPosition(),known);if(route.size()<2||CityJourneys.known(city,identity(officer.getUUID())))return false;
        var vehicle=create(city,officer,mode(role));if(vehicle==null)return false;
        if(!officer.startRiding(vehicle,false,false)){vehicle.discard();return false;}
        course(vehicle,suspect,known,observed,route);officer.getNavigation().stop();PoliceDispatch.invalidate(city);return true;
    }
    static void course(Entity vehicle,ServerPlayer suspect,BlockPos known,long observed,List<BlockPos> route){var d=new Drive(suspect.getUUID(),known.immutable(),observed,route);d.repath=vehicle.level().getGameTime()+20;vehicle.setAttached(DRIVE,d);}
    static Entity create(ServerLevel city,NapoliResident officer,String mode){
        if(CityJourneys.known(city,identity(officer.getUUID()))||!city.areEntitiesActuallyLoadedAndTicking(net.minecraft.world.level.ChunkPos.containing(officer.blockPosition())))return null;
        var vehicle=mode.equals("scooter")?Napuli.MOTORINO.create(city,EntitySpawnReason.EVENT):Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);if(vehicle==null)return null;
        vehicle.setUUID(identity(officer.getUUID()));vehicle.addTag(TAG);vehicle.setAttached(DRIVER,officer.getUUID());vehicle.setPos(officer.position());
        vehicle.setAttached(LEGACY,!PolicePatrols.managed(officer)&&NapoliCivicLife.profile(city,officer.getUUID())==null);
        if(vehicle instanceof Motorino bike)bike.configurePolice();else if(vehicle instanceof CityVehicle car)car.configurePolice();
        vehicle.setCustomName(Component.literal(mode.equals("scooter")?"Carabinieri · Moto":"Carabinieri · Auto"));vehicle.setCustomNameVisible(true);
        if(city.getBlockCollisions(vehicle,vehicle.getBoundingBox()).iterator().hasNext()||!city.getEntities(vehicle,vehicle.getBoundingBox(),e->e.isAlive()&&e!=officer&&!e.isSpectator()).isEmpty())return null;
        return city.addFreshEntity(vehicle)?vehicle:null;
    }
    static boolean drive(Entity vehicle,ServerLevel city){
        if(!managed(vehicle))return false;var npc=driver(vehicle);var d=vehicle.getAttached(DRIVE);long now=city.getGameTime();
        var suspect=d==null?null:city.getServer().getPlayerList().getPlayer(d.suspect);
        if(npc==null||!npc.isAlive()||npc.getVehicle()!=vehicle||d==null||suspect==null||suspect.level()!=city||!suspect.isAlive()||!Carabinieri.isWanted(suspect)||Carabinieri.isJailed(suspect)||city.players().stream().noneMatch(p->p.distanceToSqr(vehicle)<128*128)){release(vehicle,city);return true;}
        if(d.goal.distToCenterSqr(vehicle.position())<6*6){release(vehicle,city);return true;}
        if(now>=d.repath){var route=path(vehicle.blockPosition(),d.goal);if(route.size()<2){release(vehicle,city);return true;}d.route=route;d.next=1;d.repath=now+20;}
        if(d.next>=d.route.size()){release(vehicle,city);return true;}
        var goal=Vec3.atBottomCenterOf(d.route.get(d.next));var delta=goal.subtract(vehicle.position());if(delta.horizontalDistanceSqr()<.4*.4){d.next++;return true;}
        var motion=delta.normalize().scale(Math.min(delta.length(),speed(vehicle)*(city.isRaining()?.8:1)));var next=BlockPos.containing(vehicle.position().add(motion.scale(4)));
        if(!city.hasChunkAt(next)||CityLivingHomes.at(next)!=null||CityProperty.home(city,next)){release(vehicle,city);return true;}
        var ahead=vehicle.getBoundingBox().expandTowards(motion.scale(3)).inflate(.1,.1,.1);
        boolean blocked=!city.getEntities(vehicle,ahead,e->e.isAlive()&&!e.isPassenger()&&!e.isPassengerOfSameVehicle(vehicle)&&!e.isSpectator()&&e instanceof LivingEntity).isEmpty();var before=vehicle.position();
        if(!blocked){float yaw=(float)Math.toDegrees(Math.atan2(-motion.x,motion.z));vehicle.setYRot(yaw);if(vehicle instanceof LivingEntity living){living.setYBodyRot(yaw);living.setYHeadRot(yaw);}vehicle.move(MoverType.SELF,motion);blocked=vehicle.position().distanceToSqr(before)<.0001;}
        vehicle.setDeltaMovement(Vec3.ZERO);if(blocked){if(d.blocked==0)d.blocked=now;if(now-d.blocked>=40)release(vehicle,city);}else d.blocked=0;
        return true;
    }
    private static BlockPos exit(ServerLevel city,Entity vehicle,NapoliResident npc){
        for(int[] d:new int[][]{{-2,0},{2,0},{0,-2},{0,2},{-3,0},{3,0},{0,-3},{0,3},{0,0}}){var at=vehicle.blockPosition().offset(d[0],0,d[1]);if(!city.hasChunkAt(at)||!NapoliResidentHomes.walkable(city,at))continue;var box=npc.getBoundingBox().move(Vec3.atBottomCenterOf(at).subtract(npc.position()));if(!city.getEntities(npc,box,e->e!=vehicle&&e.isAlive()&&!e.isSpectator()).isEmpty())continue;return at;}
        return null;
    }
    private static void release(Entity vehicle,ServerLevel city){
        var npc=driver(vehicle);if(npc!=null&&npc.getVehicle()==vehicle){var at=exit(city,vehicle,npc);if(at==null)return;npc.stopRiding();npc.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,npc.getYRot(),0);npc.setAttached(REST,city.getGameTime()+160);npc.getNavigation().stop();if(npc.isAlive()&&!npc.isRemoved())npc.resumePatrol();}
        vehicle.discard();PoliceDispatch.invalidate(city);
    }
    static void requestStop(NapoliResident officer){if(riding(officer))RETIRING.computeIfAbsent((ServerLevel)officer.level(),k->new HashSet<>()).add(officer.getVehicle());}
    static void finish(ServerPlayer suspect){var city=suspect.level();var all=new ArrayList<Entity>();for(var e:city.getAllEntities())if(managed(e)&&e.getAttached(DRIVE)!=null&&e.getAttached(DRIVE).suspect.equals(suspect.getUUID()))all.add(e);for(var e:all)release(e,city);}
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{for(var city:server.getAllLevels()){var pending=RETIRING.remove(city);if(pending!=null)for(var e:pending)release(e,city);}});
        // Entity-section callbacks must not remove passengers while Minecraft is iterating that section.
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,city)->{if(managed(entity)&&(entity.getRemovalReason()==null||!entity.getRemovalReason().shouldDestroy()))RETIRING.computeIfAbsent(city,k->new HashSet<>()).add(entity);});
        ServerEntityEvents.ENTITY_LOAD.register((entity,city)->{if(managed(entity)&&entity.getAttached(DRIVER)==null)RETIRING.computeIfAbsent(city,k->new HashSet<>()).add(entity);});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var city:server.getAllLevels()){var all=new ArrayList<Entity>();for(var e:city.getAllEntities())if(managed(e))all.add(e);for(var e:all)release(e,city);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->RETIRING.clear());
    }
    private PoliceVehicles(){}
}
