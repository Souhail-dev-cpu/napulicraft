package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.*;

/** FIFO admission for shared spaces, with expiring tickets and physical, non-lootable seats. */
final class CityMovement {
    static final String SEAT="napuli_city_seat";
    private record Ticket(UUID actor,long seen){}
    private static final class Gate { final LinkedHashMap<UUID,Ticket> line=new LinkedHashMap<>();long until;UUID holder; }
    private static final Map<ServerLevel,Map<BlockPos,Gate>> GATES=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<UUID,BlockPos>> PASSAGES=new WeakHashMap<>();
    private static boolean present(ServerLevel city,UUID id){var entity=city.getEntity(id);if(entity!=null)return entity.isAlive();var p=city.getServer().getPlayerList().getPlayer(id);return p!=null&&p.level()==city&&p.isAlive()&&!p.isSpectator();}
    static boolean acquire(ServerLevel city,BlockPos resource,UUID actor,int lease){
        var gates=GATES.computeIfAbsent(city,k->new HashMap<>());var gate=gates.computeIfAbsent(resource.immutable(),k->new Gate());long now=city.getGameTime();
        gate.line.values().removeIf(t->now-t.seen()>240||!present(city,t.actor()));
        if(gate.holder!=null&&(gate.until<now||!gate.line.containsKey(gate.holder))){gate.line.remove(gate.holder);gate.holder=null;}
        if(gate.line.size()>=16&&!gate.line.containsKey(actor))return false;
        gate.line.put(actor,new Ticket(actor,now));
        if(gate.holder==null)gate.holder=gate.line.keySet().iterator().next();
        if(!actor.equals(gate.holder))return false;gate.until=now+lease;return true;
    }
    static void release(ServerLevel city,UUID actor){
        var passages=PASSAGES.get(city);if(passages!=null)passages.remove(actor);
        var gates=GATES.get(city);if(gates==null)return;
        gates.values().forEach(g->{g.line.remove(actor);if(actor.equals(g.holder))g.holder=null;});gates.values().removeIf(g->g.line.isEmpty());
    }
    static void release(ServerLevel city,BlockPos resource,UUID actor){
        var gates=GATES.get(city);if(gates==null)return;var gate=gates.get(resource);if(gate==null)return;
        gate.line.remove(actor);if(actor.equals(gate.holder))gate.holder=null;if(gate.line.isEmpty())gates.remove(resource);
    }
    static BlockPos waitingSpot(ServerLevel city,BlockPos resource,UUID actor){
        var gate=GATES.getOrDefault(city,Map.of()).get(resource);int index=gate==null?1:Math.max(1,new ArrayList<>(gate.line.keySet()).indexOf(actor));
        for(int side:new int[]{0,1,-1,2,-2}){var spot=resource.offset(side,0,1+index);if(NapoliResidentHomes.walkable(city,spot))return spot;}
        return resource.south(2);
    }
    static boolean beforeMove(NapoliResident npc,BlockPos goal){
        var city=(ServerLevel)npc.level();if(npc.isPassenger())return true;
        // A pedestrian already on the road has priority. Others wait on the pavement.
        boolean pavement=npc.getX()<-343||npc.getX()>-335;
        if(pavement&&npc.getX()>-349&&npc.getX()<-329&&Math.abs(goal.getX()-npc.getX())>2){
            var crossing=new AABB(-344,npc.getY(),npc.getZ()-5,-334,npc.getY()+3,npc.getZ()+5);
            if(!city.getEntitiesOfClass(CityVehicle.class,crossing,v->v.isAlive()&&approaching(v.position(),new Vec3(v.getX()-v.xo,0,v.getZ()-v.zo),npc.position())).isEmpty()){npc.setActivity(NapoliHouseholds.Activity.WAIT);npc.getNavigation().stop();return false;}
        }
        BlockPos nextDoor=null;double closest=Double.MAX_VALUE;var direction=Vec3.atBottomCenterOf(goal).subtract(npc.position()).multiply(1,0,1).normalize();
        for(var p:BlockPos.betweenClosed(npc.blockPosition().offset(-1,0,-1),npc.blockPosition().offset(1,1,1))){
            var state=city.getBlockState(p);if(!(state.getBlock() instanceof DoorBlock door)||state.getValue(DoorBlock.HALF)!=DoubleBlockHalf.LOWER)continue;
            var relative=Vec3.atBottomCenterOf(p).subtract(npc.position()).multiply(1,0,1);double along=relative.dot(direction);
            if(along<-.5&&relative.lengthSqr()>.8*.8)continue;
            double sideways=relative.subtract(direction.scale(along)).lengthSqr();if(sideways>1)continue;
            double distance=relative.lengthSqr();if(distance<closest){nextDoor=p.immutable();closest=distance;}
        }
        var passages=PASSAGES.computeIfAbsent(city,k->new HashMap<>());var previous=passages.get(npc.getUUID());
        if(previous!=null&&!previous.equals(nextDoor)){release(city,previous,npc.getUUID());passages.remove(npc.getUUID());}
        if(nextDoor!=null){
            passages.put(npc.getUUID(),nextDoor);
            if(!acquire(city,nextDoor,npc.getUUID(),60)){npc.setActivity(NapoliHouseholds.Activity.WAIT);npc.getNavigation().stop();return false;}
            var state=city.getBlockState(nextDoor);((DoorBlock)state.getBlock()).setOpen(npc,city,state,nextDoor,true);
        }
        return true;
    }
    static boolean approaching(Vec3 from,Vec3 motion,Vec3 crossing){return motion.horizontalDistanceSqr()>.0001&&crossing.subtract(from).multiply(1,0,1).dot(motion)>=-.05;}
    static boolean isSeat(Entity entity){return entity!=null&&entity.entityTags().contains(SEAT);}
    static boolean sit(NapoliResident npc,BlockPos chair){
        if(isSeat(npc.getVehicle()))return true;
        if (!sitAt(npc, chair)) return false;
        npc.getNavigation().stop();
        return true;
    }

    static boolean sitAt(LivingEntity rider, BlockPos chair) {
        if (!(rider.level() instanceof ServerLevel city) || rider.isPassenger()) return false;
        var occupied = city.getEntitiesOfClass(ArmorStand.class, new AABB(chair).inflate(.1),
                seat -> isSeat(seat) && !seat.getPassengers().isEmpty());
        if (!occupied.isEmpty()) return false;
        var marker = new ArmorStand(city, chair.getX()+.5, chair.getY()+.35, chair.getZ()+.5);
        marker.setInvisible(true);
        marker.setNoGravity(true);

        ((dev.napuli.mixin.SeatMarkerAccessor) marker).napuli$setMarker(true);
        marker.addTag(SEAT);
        if (!city.addFreshEntity(marker) || !rider.startRiding(marker,true,false)) {
            marker.discard();
            release(city,rider.getUUID());
            return false;
        }
        return true;
    }

    static void stand(NapoliResident npc){var vehicle=npc.getVehicle();if(isSeat(vehicle)){npc.stopRiding();vehicle.discard();release((ServerLevel)npc.level(),npc.getUUID());}}
    static void register(){
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(level.dimension()!=NapoliMap.LEVEL||hand!=InteractionHand.MAIN_HAND||!(player instanceof ServerPlayer p))return InteractionResult.PASS;
            var pos=hit.getBlockPos();var state=level.getBlockState(pos);if(!(state.getBlock() instanceof DoorBlock))return InteractionResult.PASS;
            if(state.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER)pos=pos.below();
            if(!acquire(p.level(),pos,p.getUUID(),40)){p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Lascia passare chi è arrivato prima."));return InteractionResult.FAIL;}return InteractionResult.PASS;
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % 10 != 0) return;
            for (var entity : level.getAllEntities()) {
                if (!isSeat(entity)) continue;
                if (entity.getPassengers().isEmpty() || level.isEmptyBlock(entity.blockPosition())) {
                    for (var passenger : entity.getPassengers()) release(level, passenger.getUUID());
                    entity.discard();
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%200!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;
            var gates=GATES.get(city);if(gates!=null){long now=city.getGameTime();gates.values().forEach(g->g.line.values().removeIf(t->now-t.seen()>240||!present(city,t.actor())));gates.values().removeIf(g->g.line.isEmpty());}
            var passages=PASSAGES.get(city);if(passages!=null)passages.entrySet().removeIf(e->!present(city,e.getKey()));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{GATES.clear();PASSAGES.clear();});
    }
    private CityMovement(){}
}
