package dev.napuli;

import java.util.*;
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
    private static final EntityDataAccessor<Boolean> CITIZEN=SynchedEntityData.defineId(CityVehicle.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> POLICE=SynchedEntityData.defineId(CityVehicle.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STOLEN=SynchedEntityData.defineId(CityVehicle.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> CABIN=SynchedEntityData.defineId(CityVehicle.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DOORS=SynchedEntityData.defineId(CityVehicle.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TAXI=SynchedEntityData.defineId(CityVehicle.class,EntityDataSerializers.BOOLEAN);
    private record Place(int seat,boolean standing,double z,boolean jump){}
    private final Map<UUID,Place> places=new HashMap<>();
    private int doorTicks;
    private float doorProgress,previousDoorProgress;
    public boolean taxi(){return entityData.get(TAXI);}
    public float doorOpening(float delta){return net.minecraft.util.Mth.lerp(delta,previousDoorProgress,doorProgress);}
    public boolean standing(Entity passenger){var place=places.get(passenger.getUUID());return place!=null&&place.standing();}
    public Vec3 passengerLocation(Entity passenger){
        var layout=VehicleCabin.layout(kind());var place=places.get(passenger.getUUID());
        int index=Math.max(0,getPassengers().indexOf(passenger));
        if(place!=null&&place.standing())return new Vec3(0,layout.floor(),place.z());
        // The taxi passenger sits behind the cab driver's cockpit.
        return layout.seat(taxi()?2:place==null?index:place.seat());
    }
    @Override protected EntityDimensions getDefaultDimensions(Pose pose){var layout=VehicleCabin.layout(entityData==null?0:kind());return EntityDimensions.scalable(layout.width(),layout.height());}
    @Override protected net.minecraft.world.phys.AABB makeBoundingBox(Vec3 at){
        var l=VehicleCabin.layout(entityData==null?0:kind());double a=Math.toRadians(getYRot()),s=Math.abs(Math.sin(a)),c=Math.abs(Math.cos(a));
        double x=(c*l.width()+s*l.length())/2,z=(s*l.width()+c*l.length())/2;
        return new net.minecraft.world.phys.AABB(at.x-x,at.y,at.z-z,at.x+x,at.y+l.height(),at.z+z);
    }
    @Override public void setYRot(float yaw){super.setYRot(yaw);if(entityData!=null)setBoundingBox(makeBoundingBox(position()));}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);if(KIND.equals(key))refreshDimensions();
        if(CABIN.equals(key)&&level().isClientSide()){
            places.clear();for(String row:entityData.get(CABIN).split(";")){if(row.isEmpty())continue;String[] fields=row.split(",");
                try{if(fields.length==4)places.put(UUID.fromString(fields[0]),new Place(Integer.parseInt(fields[1]),Boolean.parseBoolean(fields[2]),Integer.parseInt(fields[3])/100.0,false));}catch(IllegalArgumentException ignored){}
            }
        }
    }
    private void syncCabin(){var value=new StringBuilder();for(var p:getPassengers()){var at=places.get(p.getUUID());if(at!=null)value.append(p.getUUID()).append(',').append(at.seat()).append(',').append(at.standing()).append(',').append(Math.round(at.z()*100)).append(';');}entityData.set(CABIN,value.toString());}
    @Override protected void addPassenger(Entity p){super.addPassenger(p);if(level().isClientSide())return;int seat=0;while(seat<VehicleCabin.layout(kind()).seats().size()){int candidate=seat;if(places.values().stream().noneMatch(v->v.seat()==candidate))break;seat++;}places.put(p.getUUID(),new Place(seat,false,VehicleCabin.layout(kind()).seat(seat).z,false));doorTicks=35;syncCabin();if(kind()>0&&p instanceof net.minecraft.server.level.ServerPlayer player)player.sendOverlayMessage(Component.literal("Spazio: alzati/siediti · W/S: corridoio · Shift: scendi"));}
    @Override protected void removePassenger(Entity p){super.removePassenger(p);if(!level().isClientSide()){places.remove(p.getUUID());doorTicks=35;if(kind()==0&&!getPassengers().isEmpty()){var driver=getFirstPassenger();var old=places.get(driver.getUUID());if(old!=null&&old.seat()!=0){var occupied=places.entrySet().stream().filter(e->e.getValue().seat()==0).findFirst().orElse(null);if(occupied!=null)places.put(occupied.getKey(),new Place(old.seat(),false,0,false));places.put(driver.getUUID(),new Place(0,false,0,false));}}syncCabin();}}
    private void tickCabin(){
        previousDoorProgress=doorProgress;doorProgress=net.minecraft.util.Mth.clamp(doorProgress+(entityData.get(DOORS)?.12f:-.12f),0,1);
        if(level().isClientSide())return;
        if(doorTicks>0)doorTicks--;boolean stopped=position().subtract(getPosition(0)).horizontalDistanceSqr()<.0025;
        boolean station=atStop();if(!networkKey.isEmpty()){var parts=networkKey.split("/");var line=CityNetwork.line(parts[0]);station=line!=null&&!CityNetwork.position((ServerLevel)level(),line,Integer.parseInt(parts[1])).stop().isEmpty();}
        entityData.set(DOORS,stopped&&(doorTicks>0||kind()>0&&station));
        if(kind()==0)return;boolean changed=false;var layout=VehicleCabin.layout(kind());
        for(var entity:getPassengers())if(entity instanceof net.minecraft.server.level.ServerPlayer player){
            var old=places.get(player.getUUID());if(old==null)continue;var input=player.getLastClientInput();boolean stand=old.standing();double z=old.z();int seat=old.seat();
            if(input.jump()&&!old.jump()){
                if(stand){stand=false;double distance=Double.MAX_VALUE;for(int i=0;i<layout.seats().size();i++){int candidate=i;boolean used=places.entrySet().stream().anyMatch(e->!e.getKey().equals(player.getUUID())&&e.getValue().seat()==candidate);double d=Math.abs(layout.seat(i).z-z);if(!used&&d<distance){distance=d;seat=i;}}}
                else for(double offset:new double[]{0,.75,-.75,1.5,-1.5,2.25,-2.25,3,-3,3.75,-3.75}){double candidate=Math.clamp(layout.seat(seat).z+offset,-layout.aisleEnd(),layout.aisleEnd());if(places.entrySet().stream().noneMatch(e->!e.getKey().equals(player.getUUID())&&e.getValue().standing()&&Math.abs(e.getValue().z()-candidate)<.65)){stand=true;z=candidate;break;}}
            }
            if(stand){double step=(input.forward()?1:0)-(input.backward()?1:0);double proposed=Math.clamp(z+step*.1,-layout.aisleEnd(),layout.aisleEnd());
                if(places.entrySet().stream().noneMatch(e->!e.getKey().equals(player.getUUID())&&e.getValue().standing()&&Math.abs(e.getValue().z()-proposed)<.65))z=proposed;
            }
            var value=new Place(seat,stand,z,input.jump());places.put(player.getUUID(),value);changed|=stand!=old.standing()||seat!=old.seat()||Math.abs(z-old.z())>.001;
        }if(changed)syncCabin();
    }
    public boolean stolenVehicle(){return entityData.get(STOLEN);}
    void setStolenVehicle(boolean value){entityData.set(STOLEN,value);}
    public boolean policeVehicle(){return entityData.get(POLICE);}
    void configurePolice(){entityData.set(POLICE,true);setNoAi(true);getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.2);}
    boolean residentOwned(){return entityData.get(CITIZEN);}
    void configureResident(){entityData.set(CITIZEN,true);getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.2);}
    private int stop,waypoint;
    private String lastStop="";
    private long lastStopAt;
    private String networkKey="";
    private java.util.UUID taxiId,taxiOwner;
    java.util.UUID taxiId(){return taxiId;}
    java.util.UUID taxiOwner(){return taxiOwner;}
    void configureTaxi(java.util.UUID id,java.util.UUID owner){
        taxiId=id;taxiOwner=owner;entityData.set(TAXI,true);setNoAi(false);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.42);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
        getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.2);
    }
    long networkTick;
    String networkKey(){return networkKey;}
    void configureNetwork(String key,int kind){networkKey=key;networkTick=level().getGameTime();entityData.set(KIND,kind);setNoGravity(true);getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(1.2);}
    @Override public boolean shouldBeSaved(){return (VehicleTheft.taken(this)||!PoliceVehicles.managed(this)||PoliceVehicles.saved(this))&&(VehicleTheft.taken(this)||!CityMobility.managed(this))&&taxiId==null&&networkKey.isEmpty()&&super.shouldBeSaved();}
    @Override public boolean saveAsPassenger(net.minecraft.world.level.storage.ValueOutput out){return (VehicleTheft.taken(this)||!PoliceVehicles.managed(this)||PoliceVehicles.saved(this))&&(VehicleTheft.taken(this)||!CityMobility.managed(this))&&taxiId==null&&networkKey.isEmpty()&&super.saveAsPassenger(out);}
    int remainingStop(){return stop;}
    boolean atStop(){return stop>0;}
    double distanceToStop(BlockPos target){
        var points=route();Vec3 at=position();double distance=0;int index=Math.floorMod(waypoint,points.size());
        for(int leg=0;leg<=points.size();leg++){var end=points.get(index);boolean vertical=Math.abs(end.x-at.x)<1;double lane=target.getX()<-339?-341:-337;
            if(vertical&&Math.abs(at.x-lane)<2&&target.getZ()>=Math.min(at.z,end.z)&&target.getZ()<=Math.max(at.z,end.z))return distance+Math.abs(target.getZ()-at.z);
            distance+=at.distanceTo(end);at=end;index=(index+1)%points.size();
        }return distance;
    }
    public CityVehicle(EntityType<? extends CityVehicle> type,Level level){super(type,level);setNoAi(true);setPersistenceRequired();}
    public int kind(){return entityData.get(KIND);}
    @Override public boolean isPushable(){return !residentOwned()&&!policeVehicle()&&super.isPushable();}
    public static AttributeSupplier.Builder attributes(){return createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,.16).add(Attributes.FOLLOW_RANGE,48);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(KIND,0);b.define(CITIZEN,false);b.define(POLICE,false);b.define(STOLEN,false);b.define(CABIN,"");b.define(DOORS,false);b.define(TAXI,false);}
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out){super.addAdditionalSaveData(out);out.putBoolean("npc_vehicle_stolen",stolenVehicle());out.putBoolean("npc_vehicle_police",policeVehicle());out.putInt("city_kind",kind());out.putInt("city_waypoint",waypoint);out.putInt("city_stop",stop);out.putString("city_last_stop",lastStop);out.putLong("city_last_stop_at",lastStopAt);}
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in){super.readAdditionalSaveData(in);entityData.set(STOLEN,in.getBooleanOr("npc_vehicle_stolen",false));entityData.set(POLICE,in.getBooleanOr("npc_vehicle_police",false));entityData.set(KIND,Math.clamp(in.getIntOr("city_kind",0),0,3));waypoint=in.getIntOr("city_waypoint",0);stop=in.getIntOr("city_stop",0);lastStop=in.getStringOr("city_last_stop","");lastStopAt=in.getLongOr("city_last_stop_at",0);}
    private List<Vec3> route(){
        if(kind()==2)return List.of(new Vec3(-345,64,-185),new Vec3(-345,64,190));
        return List.of(new Vec3(-341,64,-184),new Vec3(-341,64,199),new Vec3(-337,64,199),new Vec3(-337,64,-184));
    }
    @Override public void tick(){
        super.tick();tickCabin();if(level().isClientSide()||!(level() instanceof ServerLevel city))return;
        if(VehicleTheft.tick(this,city))return;
        if(PoliceVehicles.drive(this,city))return;
        if(CityMobility.drive(this,city))return;
        if(PhoneServices.tickTaxi(this,city))return;
        if(!networkKey.isEmpty()){CityNetwork.drive(this,city);return;}
        if(city.players().stream().noneMatch(p->p.distanceToSqr(this)<112*112)){setDeltaMovement(Vec3.ZERO);return;}
        if(stop>0){stop--;setDeltaMovement(Vec3.ZERO);return;}
        if(kind()>0)for(int z:CityTransit.STOPS)if(Math.abs(getZ()-z)<.14){String key=getBlockX()+"/"+z;if(!key.equals(lastStop)||city.getGameTime()-lastStopAt>1200){lastStop=key;lastStopAt=city.getGameTime();stop=80;setDeltaMovement(Vec3.ZERO);return;}}
        if(kind()>0&&CityEvents.active(city)&&CityEvents.event(NapoliBroadcast.day(city))==CityEvents.Event.STRIKE)return;
        var target=route().get(Math.floorMod(waypoint,route().size()));
        var difference=target.subtract(position()).multiply(1,0,1);
        if(difference.length()<.35){waypoint++;stop=kind()==0?5:80;setDeltaMovement(Vec3.ZERO);return;}
        var motion=difference.normalize().scale((kind()==0?.14:.11)*(city.isRaining()?.8:1));
        var ahead=getBoundingBox().move(motion.scale(14)).inflate(.1,0,.1);
        if(!city.hasChunkAt(BlockPos.containing(position().add(motion.scale(20))))||!city.getEntities(this,ahead,e->!e.isPassengerOfSameVehicle(this)&&e.isAlive()&&(e instanceof Player||e instanceof CityVehicle||e instanceof NapoliResident)).isEmpty()){
            setDeltaMovement(Vec3.ZERO);return;
        }
        setYRot((float)Math.toDegrees(Math.atan2(-motion.x,motion.z)));setYBodyRot(getYRot());setYHeadRot(getYRot());
        setDeltaMovement(motion);move(MoverType.SELF,motion);setDeltaMovement(Vec3.ZERO);
    }
    @Override protected boolean canAddPassenger(Entity passenger){if(VehicleTheft.taken(this))return passenger instanceof Player&&getPassengers().size()<VehicleCabin.layout(kind()).seats().size();if(PoliceVehicles.managed(this))return getPassengers().isEmpty()&&PoliceVehicles.mayBoard(this,passenger);if(CityMobility.managed(this))return getPassengers().isEmpty()&&CityMobility.mayBoard(this,passenger);if(taxiId!=null)return passenger.getUUID().equals(taxiOwner)&&getPassengers().isEmpty();return getPassengers().size()<VehicleCabin.layout(kind()).seats().size();}
    @Override protected InteractionResult mobInteract(Player p,InteractionHand hand){
        if(VehicleTheft.eligible(this))return VehicleTheft.interact(p,this,hand);
        if(hand!=InteractionHand.MAIN_HAND||p.isShiftKeyDown())return InteractionResult.PASS;
        if(CityMobility.managed(this)||PoliceVehicles.managed(this))return InteractionResult.PASS;
        if(taxiId!=null){if(p instanceof net.minecraft.server.level.ServerPlayer sp)PhoneServices.boardTaxi(sp,this);return InteractionResult.SUCCESS;}
        if(!networkKey.isEmpty()){if(p instanceof net.minecraft.server.level.ServerPlayer sp)CityNetwork.lineMenu(sp,networkKey.split("/")[0]);return InteractionResult.SUCCESS;}
        if(!level().isClientSide()&&canAddPassenger(p)&&p.startRiding(this)){
            stop=60;if(p instanceof net.minecraft.server.level.ServerPlayer sp)NapoliNotice.show(sp, Component.literal("Trasporto urbano automatico. Shift per scendere."));
        }
        return InteractionResult.SUCCESS;
    }
    @Override public LivingEntity getControllingPassenger(){return stolenVehicle()&&getFirstPassenger() instanceof Player p?p:null;}
    @Override protected void tickRidden(Player driver,Vec3 input){super.tickRidden(driver,input);setRot(driver.getYRot(),driver.getXRot()*.25f);yRotO=yBodyRot=yHeadRot=getYRot();}
    @Override protected Vec3 getRiddenInput(Player driver,Vec3 input){return new Vec3(0,0,driver.zza<0?driver.zza*.3:driver.zza);}
    @Override protected float getRiddenSpeed(Player driver){return .42f;}
    @Override protected void positionRider(Entity p,Entity.MoveFunction move){
        var at=VehicleCabin.world(position(),getYRot(),passengerLocation(p));move.accept(p,at.x,at.y,at.z);if(p instanceof NapoliResident npc){npc.setYBodyRot(getYRot());npc.setYHeadRot(getYRot());}
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity p){
        var cabin=VehicleCabin.layout(kind());
        for(int side:new int[]{1,-1})for(double along:new double[]{passengerLocation(p).z(),0,-cabin.length()/2.0-.8}){
            var at=VehicleCabin.world(position(),getYRot(),new Vec3(side*(cabin.width()/2+.8),0,along));var base=BlockPos.containing(at);
            for(int dy:new int[]{0,1,-1,-2}){var pos=base.offset(0,dy,0);if(!level().hasChunkAt(pos)||!level().getBlockState(pos.below()).isSolid())continue;var feet=Vec3.atBottomCenterOf(pos);if(level().noCollision(p,p.getDimensions(Pose.STANDING).makeBoundingBox(feet)))return feet;}
        }return super.getDismountLocationForPassenger(p);
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%200!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null||city.players().isEmpty())return;
            for(int slot=0;slot<5;slot++){
                int kind=slot<3?slot:0;
                var id=java.util.UUID.nameUUIDFromBytes(("napuli:city_vehicle:"+slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(slot>=3&&!CityAtmosphere.rush(NapoliBroadcast.time(city))){
                    var parked=city.getEntity(id);if(parked!=null&&parked.getPassengers().isEmpty()){
                        parked.discard();var updated=new java.util.HashSet<>(city.getAttachedOrElse(SPAWNED,java.util.Set.of()));updated.remove(slot);city.setAttached(SPAWNED,java.util.Set.copyOf(updated));
                    }continue;
                }
                var start=new BlockPos(kind==2?-345:kind==1?-337:-341,64,slot>=3?slot==3?-80:40:kind==0?-170:kind==1?-50:130);
                if(!city.hasChunkAt(start)||city.players().stream().noneMatch(p->start.distToCenterSqr(p.position())<96*96)||city.getEntity(id)!=null)continue;
                // Chunk ownership prevents respawning an already persisted but unloaded route vehicle.
                var marker=city.getAttachedOrElse(SPAWNED,java.util.Set.of());if(marker.contains(slot))continue;
                var v=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);if(v==null)continue;
                v.setUUID(id);v.entityData.set(KIND,kind);v.waypoint=kind==1?3:1;v.snapTo(start.getX(),64,start.getZ(),0,0);
                v.setCustomName(Component.literal(kind==0?"Auto di quartiere":kind==1?"Bus · Linea Partenope":"Tram · Rione Partenope"));city.addFreshEntity(v);
                var updated=new java.util.HashSet<>(marker);updated.add(slot);city.setAttached(SPAWNED,java.util.Set.copyOf(updated));
            }
        });
    }
    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<java.util.Set<Integer>> SPAWNED=
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<java.util.Set<Integer>>builder()
                    .persistent(com.mojang.serialization.Codec.INT.listOf().xmap(java.util.Set::copyOf,List::copyOf)).buildAndRegister(Napuli.id("city_vehicles_spawned"));
}
