package dev.napuli;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Low-frequency route decisions, server movement and bounded passenger capacity. */
public final class CityVehicle extends PathfinderMob {
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(CityVehicle.class,EntityDataSerializers.INT);
    private int stop,waypoint;
    public CityVehicle(EntityType<? extends CityVehicle> type,Level level){super(type,level);setNoAi(true);setPersistenceRequired();}
    public int kind(){return entityData.get(KIND);}
    public static AttributeSupplier.Builder attributes(){return createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,.16);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(KIND,0);}
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out){super.addAdditionalSaveData(out);out.putInt("city_kind",kind());out.putInt("city_waypoint",waypoint);out.putInt("city_stop",stop);}
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in){super.readAdditionalSaveData(in);entityData.set(KIND,Math.clamp(in.getIntOr("city_kind",0),0,2));waypoint=in.getIntOr("city_waypoint",0);stop=in.getIntOr("city_stop",0);}
    private List<Vec3> route(){
        if(kind()==2)return List.of(new Vec3(-345,64,-185),new Vec3(-345,64,190));
        return List.of(new Vec3(-341,64,-184),new Vec3(-341,64,199),new Vec3(-337,64,199),new Vec3(-337,64,-184));
    }
    @Override public void tick(){
        super.tick();if(level().isClientSide()||!(level() instanceof ServerLevel city))return;
        if(city.players().stream().noneMatch(p->p.distanceToSqr(this)<112*112)){setDeltaMovement(Vec3.ZERO);return;}
        if(stop>0){stop--;setDeltaMovement(Vec3.ZERO);return;}
        if(kind()>0&&CityEvents.active(city)&&CityEvents.event(NapoliBroadcast.day(city))==CityEvents.Event.STRIKE)return;
        var target=route().get(Math.floorMod(waypoint,route().size()));
        var difference=target.subtract(position()).multiply(1,0,1);
        if(difference.length()<.35){waypoint++;stop=kind()==0?5:80;setDeltaMovement(Vec3.ZERO);return;}
        var motion=difference.normalize().scale(kind()==0?.14:.11);
        var ahead=getBoundingBox().move(motion.scale(14)).inflate(.1,0,.1);
        if(!city.hasChunkAt(BlockPos.containing(position().add(motion.scale(20))))||!city.getEntities(this,ahead,e->!e.isPassengerOfSameVehicle(this)&&e.isAlive()&&(e instanceof Player||e instanceof CityVehicle||e instanceof NapoliResident)).isEmpty()){
            setDeltaMovement(Vec3.ZERO);return;
        }
        setYRot((float)Math.toDegrees(Math.atan2(-motion.x,motion.z)));setYBodyRot(getYRot());setYHeadRot(getYRot());
        setDeltaMovement(motion);move(MoverType.SELF,motion);setDeltaMovement(Vec3.ZERO);
    }
    @Override protected boolean canAddPassenger(Entity passenger){return getPassengers().size()<(kind()==0?2:8);}
    @Override protected InteractionResult mobInteract(Player p,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND||p.isShiftKeyDown())return InteractionResult.PASS;
        if(!level().isClientSide()&&canAddPassenger(p)&&p.startRiding(this)){
            stop=60;if(p instanceof net.minecraft.server.level.ServerPlayer sp)sp.sendSystemMessage(Component.literal("Trasporto urbano automatico. Shift per scendere."));
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected void positionRider(Entity p,Entity.MoveFunction move){
        int seat=Math.max(0,getPassengers().indexOf(p));double yaw=Math.toRadians(getYRot()),along=.55-seat/2*.45,side=seat%2==0?-.35:.35;
        move.accept(p,getX()-Math.sin(yaw)*along+Math.cos(yaw)*side,getY()+.35,getZ()+Math.cos(yaw)*along+Math.sin(yaw)*side);
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%200!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null||city.players().isEmpty())return;
            for(int kind=0;kind<3;kind++){
                var id=java.util.UUID.nameUUIDFromBytes(("napuli:city_vehicle:"+kind).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var start=new BlockPos(kind==2?-345:kind==1?-337:-341,64,kind==0?-170:kind==1?-50:130);
                if(!city.hasChunkAt(start)||city.players().stream().noneMatch(p->start.distToCenterSqr(p.position())<96*96)||city.getEntity(id)!=null)continue;
                // Chunk ownership prevents respawning an already persisted but unloaded route vehicle.
                var marker=city.getAttachedOrElse(SPAWNED,java.util.Set.of());if(marker.contains(kind))continue;
                var v=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);if(v==null)continue;
                v.setUUID(id);v.entityData.set(KIND,kind);v.waypoint=kind==1?3:1;v.snapTo(start.getX(),64,start.getZ(),0,0);
                v.setCustomName(Component.literal(kind==0?"Auto di quartiere":kind==1?"Bus · Linea Partenope":"Tram · Rione Partenope"));city.addFreshEntity(v);
                var updated=new java.util.HashSet<>(marker);updated.add(kind);city.setAttached(SPAWNED,java.util.Set.copyOf(updated));
            }
        });
    }
    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<java.util.Set<Integer>> SPAWNED=
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<java.util.Set<Integer>>builder()
                    .persistent(com.mojang.serialization.Codec.INT.listOf().xmap(java.util.Set::copyOf,List::copyOf)).buildAndRegister(Napuli.id("city_vehicles_spawned"));
}
