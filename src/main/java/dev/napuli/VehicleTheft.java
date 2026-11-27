package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Physical theft preserves the original owner and never creates a second copy or a legal garage receipt. */
final class VehicleTheft {
    static final String TAG="napuli_stolen_vehicle";
    record Stolen(UUID owner,UUID driver,long boarded,boolean witnessed,boolean robbery){
        static final Codec<Stolen> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("owner").forGetter(Stolen::owner),UUIDUtil.CODEC.fieldOf("driver").forGetter(Stolen::driver),Codec.LONG.fieldOf("boarded").forGetter(Stolen::boarded),Codec.BOOL.fieldOf("witnessed").forGetter(Stolen::witnessed),Codec.BOOL.fieldOf("robbery").forGetter(Stolen::robbery)).apply(i,Stolen::new));
    }
    record Missing(UUID vehicle,String dimension,BlockPos at){
        static final Codec<Missing> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("vehicle").forGetter(Missing::vehicle),Codec.STRING.fieldOf("dimension").forGetter(Missing::dimension),BlockPos.CODEC.fieldOf("at").forGetter(Missing::at)).apply(i,Missing::new));
    }
    static final AttachmentType<Stolen> STOLEN=AttachmentRegistry.<Stolen>builder().persistent(Stolen.CODEC).buildAndRegister(Napuli.id("stolen_vehicle"));
    static final AttachmentType<Map<String,Missing>> MISSING=AttachmentRegistry.<Map<String,Missing>>builder().persistent(Codec.unboundedMap(Codec.STRING,Missing.CODEC)).buildAndRegister(Napuli.id("missing_citizen_vehicles"));
    static boolean taken(Entity vehicle){return vehicle!=null&&(vehicle.entityTags().contains(TAG)||vehicle instanceof CityVehicle c&&c.stolenVehicle()||vehicle instanceof Motorino m&&m.stolenVehicle());}
    static boolean missing(ServerLevel city,UUID owner){return city.getServer().overworld().getAttachedOrElse(MISSING,Map.of()).containsKey(owner.toString());}
    static boolean eligible(Entity vehicle){return taken(vehicle)||vehicle instanceof Motorino bike&&!bike.inParade()&&(CityMobility.managed(bike)||PoliceVehicles.managed(bike))||vehicle instanceof CityVehicle car&&car.kind()==0&&car.networkKey().isEmpty()&&car.taxiId()==null;}
    static boolean valid(ServerPlayer player,Entity vehicle){return eligible(vehicle)&&vehicle.isAlive()&&!vehicle.isRemoved()&&player.isAlive()&&!player.isSpectator()&&!Carabinieri.isJailed(player)&&player.level()==vehicle.level()&&!player.isPassenger()&&player.distanceToSqr(vehicle)<=9&&Math.abs(player.getY()-vehicle.getY())<2&&player.hasLineOfSight(vehicle);}
    static InteractionResult interact(Player player,Entity vehicle,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND||!eligible(vehicle))return InteractionResult.PASS;
        if(!(player instanceof ServerPlayer server))return InteractionResult.SUCCESS;
        if(player.isSecondaryUseActive()){server.sendOverlayMessage(CityText.t("vehicle.theft.no_pickup"));return InteractionResult.FAIL;}
        return take(server,vehicle)?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
    static boolean take(ServerPlayer player,Entity vehicle){
        if(!valid(player,vehicle))return false;var city=player.level();var occupants=List.copyOf(vehicle.getPassengers());
        if(occupants.stream().anyMatch(p->p instanceof Player)){if(taken(vehicle)&&vehicle instanceof CityVehicle&&player.startRiding(vehicle,false,false))return true;player.sendOverlayMessage(CityText.t("vehicle.theft.occupied"));return false;}
        var movement=vehicle.position().subtract(vehicle.getPosition(0));if(!occupants.isEmpty()&&(vehicle.tickCount>0&&movement.horizontalDistanceSqr()>.23*.23||vehicle.getDeltaMovement().horizontalDistanceSqr()>.23*.23)){player.sendOverlayMessage(CityText.t("vehicle.theft.moving"));return false;}
        var exits=new LinkedHashMap<NapoliResident,BlockPos>();var reserved=new HashSet<BlockPos>();
        for(var actor:occupants){if(!(actor instanceof NapoliResident npc))return false;var at=exit(city,vehicle,reserved);if(at==null){player.sendOverlayMessage(CityText.t("vehicle.theft.no_exit"));return false;}exits.put(npc,at);reserved.add(at);}
        var previous=vehicle.getAttached(STOLEN);UUID owner=previous==null?CityMobility.owner(vehicle):previous.owner();if(owner==null)owner=PoliceVehicles.owner(vehicle);if(owner==null)owner=vehicle.getUUID();
        boolean already=taken(vehicle);boolean noAi=vehicle instanceof Mob mob&&mob.isNoAi();var state=new Stolen(owner,player.getUUID(),city.getGameTime(),false,!occupants.isEmpty());
        vehicle.setAttached(STOLEN,state);mark(vehicle,true);for(var e:exits.entrySet()){e.getKey().stopRiding();var at=e.getValue();e.getKey().snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,e.getKey().getYRot(),0);}
        if(!player.startRiding(vehicle,false,false)){
            mark(vehicle,already);if(previous==null)vehicle.removeAttached(STOLEN);else vehicle.setAttached(STOLEN,previous);if(vehicle instanceof Mob mob)mob.setNoAi(noAi);for(var npc:exits.keySet())npc.startRiding(vehicle,true,false);return false;
        }
        CityMobility.cancel(city,owner);PoliceVehicles.taken(vehicle);if(vehicle instanceof Mob mob){mob.setNoAi(false);mob.getNavigation().stop();mob.setTarget(null);}vehicle.setDeltaMovement(Vec3.ZERO);remember(city,vehicle,state);
        for(var npc:exits.keySet()){npc.getNavigation().stop();CityMovement.release(city,npc.getUUID());}
        report(player,vehicle,exits.keySet());player.sendOverlayMessage(CityText.t(vehicle instanceof Motorino?"vehicle.theft.bike_controls":"vehicle.theft.car_controls"));return true;
    }
    private static void mark(Entity vehicle,boolean stolen){if(stolen)vehicle.addTag(TAG);else vehicle.removeTag(TAG);if(vehicle instanceof Motorino bike)bike.setStolenVehicle(stolen);if(vehicle instanceof CityVehicle car)car.setStolenVehicle(stolen);}
    private static BlockPos exit(ServerLevel city,Entity vehicle,Set<BlockPos> reserved){
        for(int[] d:new int[][]{{-2,0},{2,0},{0,-2},{0,2},{-3,0},{3,0},{0,-3},{0,3}}){var at=vehicle.blockPosition().offset(d[0],0,d[1]);if(!city.hasChunkAt(at)||CityProperty.home(city,at)||CityLivingHomes.at(at)!=null)continue;var free=CityJourneys.spawn(city,at,reserved);if(free!=null)return free;}return null;
    }
    static boolean sees(NapoliResident npc,ServerPlayer player,Entity vehicle){
        if(!npc.isAlive()||npc.isSleeping()||!CityResidentSafety.human(npc)||npc.level()!=player.level()||CityPublicOrder.jailed(player.level(),npc.getUUID())||npc.distanceToSqr(vehicle)>(npc.role()==NapoliResident.Role.CARABINIERE?32*32:20*20)||!npc.hasLineOfSight(player)||!npc.hasLineOfSight(vehicle))return false;
        var toward=player.position().subtract(npc.position()).multiply(1,0,1);return toward.lengthSqr()<1||Vec3.directionFromRotation(0,npc.getYHeadRot()).dot(toward.normalize())>=0;
    }
    private static void report(ServerPlayer player,Entity vehicle,Collection<NapoliResident> forced){
        var city=player.level();var state=vehicle.getAttached(STOLEN);if(state==null||state.witnessed())return;
        var witnesses=new LinkedHashSet<NapoliResident>();for(var npc:forced)if(npc.isAlive()&&!npc.isSleeping())witnesses.add(npc);
        city.getEntitiesOfClass(NapoliResident.class,vehicle.getBoundingBox().inflate(32),npc->sees(npc,player,vehicle)).stream().sorted(Comparator.comparingDouble(npc->npc.distanceToSqr(vehicle))).limit(6).forEach(witnesses::add);
        if(witnesses.isEmpty())return;var crime=state.robbery()?Carabinieri.Crime.ROBBERY:Carabinieri.Crime.THEFT;var seen=player.blockPosition().immutable();
        for(var npc:witnesses){if(npc.role()==NapoliResident.Role.CARABINIERE)Carabinieri.denounce(player,crime,seen);else CityResidentSafety.complain(npc,player,crime,seen,true);if(npc.getUUID().equals(state.owner())&&NapoliCivicLife.profile(city,state.owner())!=null)CitySocial.harm(city,state.owner(),player.getUUID());}
        vehicle.setAttached(STOLEN,new Stolen(state.owner(),state.driver(),state.boarded(),true,state.robbery()));
    }
    private static void remember(ServerLevel city,Entity vehicle,Stolen state){var world=city.getServer().overworld();var all=new HashMap<>(world.getAttachedOrElse(MISSING,Map.of()));var snapshot=new Missing(vehicle.getUUID(),city.dimension().identifier().toString(),vehicle.blockPosition().immutable());if(snapshot.equals(all.get(state.owner().toString())))return;all.put(state.owner().toString(),snapshot);world.setAttached(MISSING,Map.copyOf(all));}
    static boolean tick(Entity vehicle,ServerLevel city){
        if(!taken(vehicle))return false;if(!vehicle.isAlive())return true;var state=vehicle.getAttached(STOLEN);if(state==null)return true;
        if(vehicle.getFirstPassenger() instanceof ServerPlayer player){if(!player.getUUID().equals(state.driver())){state=new Stolen(state.owner(),player.getUUID(),city.getGameTime(),false,false);vehicle.setAttached(STOLEN,state);}if(!state.witnessed()&&city.getGameTime()-state.boarded()<=40&&(vehicle.tickCount%5==0))report(player,vehicle,List.of());}
        else vehicle.setDeltaMovement(new Vec3(0,vehicle.getDeltaMovement().y,0));if(vehicle.tickCount%100==0)remember(city,vehicle,state);return true;
    }
    static void register(){
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(!taken(entity)||!(entity.level() instanceof ServerLevel city))return;var state=entity.getAttached(STOLEN);if(state==null)return;var world=city.getServer().overworld();var all=new HashMap<>(world.getAttachedOrElse(MISSING,Map.of()));var old=all.get(state.owner().toString());if(old!=null&&old.vehicle().equals(entity.getUUID())){all.remove(state.owner().toString());world.setAttached(MISSING,Map.copyOf(all));}});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,city)->{if(taken(entity)&&entity.getAttached(STOLEN)!=null&&(entity.getRemovalReason()==null||!entity.getRemovalReason().shouldDestroy()))remember(city,entity,entity.getAttached(STOLEN));});
    }
    private VehicleTheft(){}
}
