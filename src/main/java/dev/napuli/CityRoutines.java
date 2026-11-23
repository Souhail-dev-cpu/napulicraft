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
        if(entity!=null&&(c.profession()==null||c.profession().site.distToCenterSqr(entity.position())>64))return;
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
        if(now-trip.waiting()>=240){all.put(npc.getUUID(),new Trip(goal,now,now+2400));return false;}
        // Only wait on the Partenope transport corridor, never invent a stop in another district.
        if(npc.getX()<-365||npc.getX()>-320||npc.getZ()<-200||npc.getZ()>210)return false;
        var stop=new BlockPos(-334,64,Math.clamp((int)Math.round(npc.getZ()/60)*60,-180,180));
        npc.setActivity(NapoliHouseholds.Activity.WAIT);CityDomestic.move(npc,stop);
        for(var bus:city.getEntitiesOfClass(CityVehicle.class,npc.getBoundingBox().inflate(6)))if(bus.kind()>0&&npc.startRiding(bus)){npc.getNavigation().stop();break;}
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
        for(var home:CityLivingHomes.all())if(home.contains(npc.blockPosition())&&!home.contains(goal))return home.entrance().south();
        if(city.hasChunkAt(goal)&&goal.distToCenterSqr(npc.position())<40*40)return goal;
        final BlockPos requested=goal;
        var destination=CityLivingHomes.all().stream().filter(h->h.style()>=0&&h.contains(requested)).findFirst().orElse(null);
        var service=Arrays.stream(CityProfessions.Category.values()).filter(c->requested.distSqr(c.site().offset(8,0,7))<4).findFirst().orElse(null);
        int arrivalZ=destination!=null?destination.z()+24:service!=null?service.site().getZ()+15:goal.getZ();
        boolean housingRow=npc.getZ()>=68&&npc.getZ()<=208||npc.getZ()>=-204&&npc.getZ()<=-56;
        if(npc.getX()<-344&&npc.getX()>=-540&&housingRow){
            int frontage=npc.getZ()>0?94+35*(int)Math.round((npc.getZ()-94)/35):-172+35*(int)Math.round((npc.getZ()+172)/35);
            // Doorways face south. Reach the frontage before turning east, rather than re-entering the house.
            if(Math.abs(npc.getZ()-frontage)>1)goal=new BlockPos(npc.getBlockX(),64,frontage);
            else if(destination!=null&&Math.abs(frontage-(destination.z()+24))<=2)goal=destination.entrance().south();
            else goal=new BlockPos(-337,64,frontage);
        }else if(npc.getX()>=-344&&npc.getX()<=-330&&Math.abs(npc.getZ()-arrivalZ)>1)goal=new BlockPos(-337,64,arrivalZ);
        else if(destination!=null&&Math.abs(npc.getZ()-arrivalZ)>1)goal=new BlockPos(-337,64,arrivalZ);
        else if(service!=null&&Math.abs(npc.getX()-goal.getX())>28)goal=new BlockPos(goal.getX(),64,arrivalZ);
        // Walk through nearby loaded chunks. A distant workplace must not freeze an observable commuter.
        double direction=Math.atan2(goal.getZ()-npc.getZ(),goal.getX()-npc.getX());
        BlockPos best=npc.blockPosition();double remaining=best.distSqr(goal);
        for(int turn:new int[]{0,1,-1,2,-2}){
            double angle=direction+turn*Math.PI/4;
            var candidate=npc.blockPosition().offset((int)Math.round(Math.cos(angle)*12),0,(int)Math.round(Math.sin(angle)*12));
            for(int y:new int[]{0,1,-1}){
                var pos=candidate.offset(0,y,0);if(!city.hasChunkAt(pos)||!NapoliResidentHomes.walkable(city,pos)||pos.distSqr(goal)>=remaining)continue;
                if(CityLivingHomes.all().stream().anyMatch(h->h.contains(pos)&&!h.contains(requested)))continue;
                var path=npc.getNavigation().createPath(pos,0);
                if(path!=null&&path.canReach()){best=pos;remaining=pos.distSqr(goal);}
            }
        }return best;
    }
    static void register(){}
    private CityRoutines(){}
}
