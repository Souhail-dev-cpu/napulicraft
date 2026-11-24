package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Small, bounded alternatives when a scheduled action cannot actually happen. */
final class CityRoutines {
    record Work(long day,int periods) {
        static final Codec<Work> CODEC=RecordCodecBuilder.create(i->i.group(Codec.LONG.fieldOf("day").forGetter(Work::day),Codec.INT.fieldOf("periods").forGetter(Work::periods)).apply(i,Work::new));
    }
    static final AttachmentType<Map<String,Work>> WORK=AttachmentRegistry.<Map<String,Work>>builder().persistent(Codec.unboundedMap(Codec.STRING,Work.CODEC)).buildAndRegister(Napuli.id("work_attendance"));
    private record Trip(BlockPos goal,long waiting,long walking){}
    private static final Map<ServerLevel,Map<UUID,Trip>> TRIPS=new WeakHashMap<>();
    static void attendance(ServerLevel city,NapoliCivicLife.Citizen c,boolean working){
        if(!working)return;var entity=city.getEntity(c.id());
        if(entity!=null&&(c.profession()==null||CityOccupations.workplace(c).distToCenterSqr(entity.position())>144&&!CityOccupations.kind(c).equals("courier")&&!CityOccupations.kind(c).equals("cleaner")))return;
        if(entity==null&&c.profession()!=null&&!CityJourneys.at(city,c,CitySimulation.destination(city,c),8))return;
        var all=new HashMap<>(city.getAttachedOrElse(WORK,Map.of()));long day=NapoliBroadcast.day(city);var previous=all.get(c.id().toString());
        all.put(c.id().toString(),new Work(day,Math.min(32,previous==null||previous.day()!=day?1:previous.periods()+1)));city.setAttached(WORK,Map.copyOf(all));
    }
    static int performance(ServerLevel city,UUID id,long day){var work=city.getAttachedOrElse(WORK,Map.of()).get(id.toString());return work!=null&&work.day()==day?work.periods():0;}
    static boolean commute(NapoliResident npc,ServerLevel city,BlockPos goal){
        var all=TRIPS.computeIfAbsent(city,k->new HashMap<>());var trip=all.get(npc.getUUID());long now=city.getGameTime();
        if(trip==null||!trip.goal().equals(goal)){trip=new Trip(goal,now,0);all.put(npc.getUUID(),trip);}
        if(trip.walking()>now)return false;
        if(npc.getVehicle() instanceof CityVehicle bus){
            if(goal.distToCenterSqr(bus.position())<20*20||now-trip.waiting()>1200){npc.stopRiding();all.put(npc.getUUID(),new Trip(goal,now,now+2400));return false;}
            npc.setActivity(NapoliHouseholds.Activity.COMMUTE);return true;
        }
        if(goal.distToCenterSqr(npc.position())<48*48)return false;
        int patience=CitySimulation.life(city,npc.getUUID()).personality().equals("tranquillo")?1200:600;
        if(now-trip.waiting()>=patience){all.put(npc.getUUID(),new Trip(goal,now,now+2400));CityMovement.release(city,npc.getUUID());return false;}
        // Only wait on the Partenope transport corridor, never invent a stop in another district.
        if(npc.getX()<-365||npc.getX()>-320||npc.getZ()<-200||npc.getZ()>210)return false;
        var stop=CityTransit.stop(npc.getZ(),goal.getZ()>npc.getZ());
        long eta=CityTransit.eta(city,stop);if(now-trip.waiting()>120&&(eta<0||eta>patience)){all.put(npc.getUUID(),new Trip(goal,now,now+2400));CityMovement.release(city,npc.getUUID());return false;}
        boolean front=CityMovement.acquire(city,stop,npc.getUUID(),60);
        npc.setActivity(NapoliHouseholds.Activity.WAIT);CityDomestic.move(npc,front?stop:CityMovement.waitingSpot(city,stop,npc.getUUID()));
        for(var bus:city.getEntitiesOfClass(CityVehicle.class,npc.getBoundingBox().inflate(6)))if(front&&bus.kind()>0&&bus.atStop()&&Math.signum(bus.getLookAngle().z)==Math.signum(goal.getZ()-npc.getZ())&&npc.startRiding(bus)){npc.getNavigation().stop();CityMovement.release(city,npc.getUUID());break;}
        return true;
    }
    static BlockPos sheltered(ServerLevel city,NapoliResident npc,BlockPos goal){
        if(!city.isRaining()||goal.distToCenterSqr(npc.position())<100||!city.canSeeSky(npc.blockPosition()))return goal;
        // Choose an actual dry, walkable waypoint toward the destination, without loading chunks.
        BlockPos best=null;double score=Double.MAX_VALUE;
        for(var p:BlockPos.betweenClosed(npc.blockPosition().offset(-6,0,-6),npc.blockPosition().offset(6,0,6)))
            if(city.hasChunkAt(p)&&!city.canSeeSky(p)&&NapoliResidentHomes.walkable(city,p)&&p.distSqr(goal)<npc.blockPosition().distSqr(goal)
                    &&NapoliMergellina.PROPERTIES.stream().noneMatch(h->h.contains(p)&&!h.contains(goal))){
                double distance=p.distSqr(goal);if(distance<score){best=p.immutable();score=distance;}
            }
        return best==null?goal:best;
    }
    static BlockPos localGoal(ServerLevel city,NapoliResident npc,BlockPos goal){
        if(!NapoliAtlas.ORIGINAL.contains(npc.getBlockX(),npc.getBlockZ())||!NapoliAtlas.ORIGINAL.contains(goal.getX(),goal.getZ()))return DistrictLife.localGoal(city,npc,goal);
        for(var home:CityLivingHomes.all())if(home.contains(npc.blockPosition())&&!home.contains(goal))return home.approach(1);
        for(var service:CityProfessions.Category.values())if(inside(service.site(),npc.blockPosition())&&!inside(service.site(),goal))return service.site().offset(8,0,13);
        if(city.hasChunkAt(goal)&&goal.distToCenterSqr(npc.position())<40*40){var direct=npc.getNavigation().createPath(goal,0);if(direct!=null&&direct.canReach())return goal;}
        final BlockPos requested=goal;
        var destination=CityLivingHomes.all().stream().filter(h->h.style()>=0&&h.contains(requested)).findFirst().orElse(null);
        var service=Arrays.stream(CityProfessions.Category.values()).filter(c->requested.distSqr(c.site().offset(8,0,7))<4).findFirst().orElse(null);
        int arrivalZ=destination!=null?destination.z()+24:service!=null?service.site().getZ()+15:goal.getZ();
        boolean housingRow=npc.getZ()>=68&&npc.getZ()<=208||npc.getZ()>=-204&&npc.getZ()<=-56;
        if(npc.getX()<-349&&npc.getX()>=-540&&housingRow){
            int frontage=npc.getZ()>0?94+35*(int)Math.round((npc.getZ()-94)/35.0):-172+35*(int)Math.round((npc.getZ()+172)/35.0);
            // Doorways face south. Reach the frontage before turning east, rather than re-entering the house.
            if(Math.abs(npc.getZ()-(frontage+.5))>1.5)goal=new BlockPos(npc.getBlockX(),64,frontage);
            else if(destination!=null&&Math.abs(frontage-(destination.z()+24))<=2)goal=destination.approach(1);
            else goal=new BlockPos(-347,64,frontage);
        }else if(npc.getX()>=-349&&npc.getX()<=-330&&Math.abs(npc.getZ()-(arrivalZ+.5))>1.5)goal=new BlockPos(-347,64,arrivalZ);
        else if(destination!=null&&Math.abs(npc.getZ()-(arrivalZ+.5))>1.5)goal=new BlockPos(-347,64,arrivalZ);
        else if(service!=null&&Math.abs(npc.getX()-goal.getX())>28)goal=new BlockPos(goal.getX(),64,arrivalZ);
        if(service!=null){var site=service.site();boolean inside=npc.getX()>site.getX()&&npc.getX()<site.getX()+18&&npc.getZ()>site.getZ()&&npc.getZ()<site.getZ()+12;
            if(!inside&&npc.getX()<site.getX()+22&&npc.getX()>site.getX()-4){
                if(npc.getZ()<=site.getZ()&&npc.getX()<site.getX()+19)goal=new BlockPos(site.getX()+20,64,Math.min(npc.getBlockZ(),site.getZ()-2));
                else if(npc.getZ()<site.getZ()+13&&npc.getX()>=site.getX()+18)goal=site.offset(20,0,13);
                else goal=site.offset(8,0,13);
            }}
        if(city.hasChunkAt(goal)&&goal.distToCenterSqr(npc.position())<40*40){var direct=npc.getNavigation().createPath(goal,0);if(direct!=null&&direct.canReach())return goal;}
        // Walk through nearby loaded chunks. A distant workplace must not freeze an observable commuter.
        double direction=Math.atan2(goal.getZ()-npc.getZ(),goal.getX()-npc.getX());
        BlockPos best=npc.blockPosition();double remaining=best.distSqr(goal);
        for(int turn:new int[]{0,1,-1,2,-2}){
            double angle=direction+turn*Math.PI/4;
            var candidate=npc.blockPosition().offset((int)Math.round(Math.cos(angle)*12),0,(int)Math.round(Math.sin(angle)*12));
            for(int y:new int[]{0,1,-1}){
                var pos=candidate.offset(0,y,0);if(!city.hasChunkAt(pos)||!NapoliResidentHomes.walkable(city,pos)||pos.distSqr(goal)>=remaining)continue;
                if(CityLivingHomes.all().stream().anyMatch(h->h.contains(pos)&&!h.contains(requested)))continue;
                if(Arrays.stream(CityProfessions.Category.values()).anyMatch(category->inside(category.site(),pos)&&!inside(category.site(),requested)))continue;
                var path=npc.getNavigation().createPath(pos,0);
                if(path!=null&&path.canReach()){best=pos;remaining=pos.distSqr(goal);}
            }
        }return best;
    }
    static boolean inside(BlockPos site,BlockPos position){return position.getX()>site.getX()&&position.getX()<site.getX()+18&&position.getZ()>site.getZ()&&position.getZ()<site.getZ()+12&&position.getY()>=64&&position.getY()<69;}
    static void register(){}
    private CityRoutines(){}
}
