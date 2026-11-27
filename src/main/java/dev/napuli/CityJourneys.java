package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A citizen has one authoritative position, whether represented by an entity or by a timed journey. */
final class CityJourneys {
    static boolean known(ServerLevel city,UUID id){return ((dev.napuli.mixin.CityEntityManagerAccessor)city).napuli$entityManager().isLoaded(id);}
    record Journey(List<BlockPos> route,long departed,long arrives,BlockPos destination,long observed) {
        static final Codec<Journey> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.listOf().fieldOf("route").forGetter(Journey::route),Codec.LONG.fieldOf("departed").forGetter(Journey::departed),Codec.LONG.fieldOf("arrives").forGetter(Journey::arrives),BlockPos.CODEC.fieldOf("destination").forGetter(Journey::destination),Codec.LONG.fieldOf("observed").forGetter(Journey::observed)).apply(i,Journey::new));
    }
    static final AttachmentType<Map<String,Journey>> JOURNEYS=AttachmentRegistry.<Map<String,Journey>>builder().persistent(Codec.unboundedMap(Codec.STRING,Journey.CODEC)).buildAndRegister(Napuli.id("citizen_journeys"));
    private static final AttachmentType<Map<String,Journey>> PENDING=AttachmentRegistry.create(Napuli.id("citizen_journey_batch"));
    static Journey get(ServerLevel city,UUID id){var pending=city.getAttached(PENDING);return (pending==null?city.getAttachedOrElse(JOURNEYS,Map.of()):pending).get(id.toString());}
    static void put(ServerLevel city,UUID id,Journey journey){var pending=city.getAttached(PENDING);if(pending!=null){pending.put(id.toString(),journey);return;}var all=new HashMap<>(city.getAttachedOrElse(JOURNEYS,Map.of()));all.put(id.toString(),journey);city.setAttached(JOURNEYS,Map.copyOf(all));}
    static void batch(ServerLevel city,Runnable work){
        if(city.hasAttached(PENDING)){work.run();return;}
        var original=city.getAttachedOrElse(JOURNEYS,Map.of());var updates=new HashMap<>(original);city.setAttached(PENDING,updates);
        try{work.run();}finally{city.removeAttached(PENDING);if(!updates.equals(original))city.setAttached(JOURNEYS,Map.copyOf(updates));}
    }
    static double length(List<BlockPos> route){double total=0;for(int i=1;i<route.size();i++)total+=Math.sqrt(route.get(i-1).distSqr(route.get(i)));return total;}
    static BlockPos sample(Journey j,long now){
        if(j.route().isEmpty())return j.destination();if(now>=j.arrives())return j.destination();
        double distance=length(j.route())*Math.clamp((double)(now-j.departed())/Math.max(1,j.arrives()-j.departed()),0,1);
        for(int i=1;i<j.route().size();i++){var a=j.route().get(i-1);var b=j.route().get(i);double segment=Math.sqrt(a.distSqr(b));if(distance<=segment){double f=segment==0?1:distance/segment;return BlockPos.containing(a.getX()+(b.getX()-a.getX())*f,a.getY()+(b.getY()-a.getY())*f,a.getZ()+(b.getZ()-a.getZ())*f);}distance-=segment;}
        return j.destination();
    }
    static List<BlockPos> route(BlockPos from,BlockPos to){
        if(!NapoliAtlas.ORIGINAL.contains(from.getX(),from.getZ())||!NapoliAtlas.ORIGINAL.contains(to.getX(),to.getZ()))return CityNetwork.walkRoute(from,to);
        var points=new ArrayList<BlockPos>();points.add(from.immutable());
        var origin=CityLivingHomes.at(from);
        var destination=CityLivingHomes.at(to);
        if(origin!=null&&origin!=destination)points.add(origin.entrance().south());
        for(var service:CityProfessions.Category.values())if(CityRoutines.inside(service.site(),from)&&!CityRoutines.inside(service.site(),to))points.add(service.site().offset(8,0,13));
        if(from.distSqr(to)>32*32&&(from.getX()<-330||to.getX()<-330)){
            BlockPos departure=points.getLast();int row=departure.getZ()>=68?94+35*(int)Math.round((departure.getZ()-94)/35.0):departure.getZ()<=-56?-172+35*(int)Math.round((departure.getZ()+172)/35.0):departure.getZ();
            points.add(new BlockPos(departure.getX(),64,row));points.add(new BlockPos(-347,64,row));
            int arrival=destination!=null?destination.z()+24:to.getZ();
            var service=Arrays.stream(CityProfessions.Category.values()).filter(c->CityRoutines.inside(c.site(),to)).findFirst().orElse(null);
            if(service!=null)arrival=service.site().getZ()+15;
            points.add(new BlockPos(-347,64,arrival));points.add(new BlockPos(to.getX(),64,arrival));
            if(service!=null)points.add(service.site().offset(8,0,13));
        }
        if(destination!=null&&destination!=origin)points.add(destination.entrance().south());
        points.add(to.immutable());return List.copyOf(points);
    }
    static Journey plan(BlockPos from,BlockPos to,long now,int vitality){
        var route=route(from,to);long duration=(long)Math.ceil(length(route)/(.10+.03*Math.clamp(vitality,0,100)/100.0));
        return new Journey(route,now,now+duration,to.immutable(),now);
    }
    static BlockPos position(ServerLevel city,NapoliCivicLife.Citizen c,BlockPos destination){
        var previous=get(city,c.id());long now=city.getGameTime();
        if(city.getEntity(c.id()) instanceof NapoliResident npc){if(!npc.isNoAi())observe(city,npc,c,destination);return npc.blockPosition();}
        if(previous==null){previous=plan(NapoliCivicLife.home(c),destination,now,CitySimulation.life(city,c.id()).needs().sleep());put(city,c.id(),previous);}
        else if(!previous.destination().equals(destination)){previous=plan(sample(previous,now),destination,now,CitySimulation.life(city,c.id()).needs().sleep());put(city,c.id(),previous);}
        return sample(previous,now);
    }
    static boolean at(ServerLevel city,NapoliCivicLife.Citizen c,BlockPos target,double radius){
        var entity=city.getEntity(c.id());if(entity!=null)return entity.blockPosition().distSqr(target)<=radius*radius;
        var j=get(city,c.id());return (j==null?NapoliCivicLife.home(c):sample(j,city.getGameTime())).distSqr(target)<=radius*radius;
    }
    static void observe(ServerLevel city,NapoliResident npc,NapoliCivicLife.Citizen c,BlockPos destination){
        if(npc.isNoAi())return;var previous=get(city,c.id());var position=npc.blockPosition();
        if(position.equals(destination)&&previous!=null&&previous.destination().equals(destination)&&previous.route().stream().allMatch(position::equals))return;
        put(city,c.id(),plan(position,destination,city.getGameTime(),CitySimulation.life(city,c.id()).needs().sleep()));
    }
    static void unmaterialized(ServerLevel city,UUID id){var census=NapoliCivicLife.census(city);if(census==null)return;city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(c->c.id().equals(id)?new NapoliCivicLife.Citizen(c.id(),c.name(),c.woman(),c.birth(),c.balance(),c.job(),c.partner(),c.property(),c.parents(),false,c.childhoodDays()):c).toList()));}
    static void withdraw(ServerLevel city,NapoliResident npc){var c=NapoliCivicLife.profile(city,npc.getUUID());if(c==null||npc.isNoAi())return;CityMovement.stand(npc);if(npc.isSleeping())npc.stopSleeping();observe(city,npc,c,CitySimulation.destination(city,c));CityMovement.release(city,c.id());unmaterialized(city,c.id());npc.discard();}
    static boolean managed(NapoliResident npc){return !npc.isNoAi()&&npc.level() instanceof ServerLevel city&&NapoliCivicLife.profile(city,npc.getUUID())!=null;}
    static BlockPos spawn(ServerLevel city,BlockPos projected){return spawn(city,projected,Set.of());}
    static BlockPos spawn(ServerLevel city,BlockPos projected,Set<BlockPos> reserved){
        if(freeArrival(city,projected,reserved))return projected;
        // Only repair a collision within the same small neighborhood; never request distant chunks.
        for(int radius=1;radius<=4;radius++)for(var p:BlockPos.betweenClosed(projected.offset(-radius,-1,-radius),projected.offset(radius,1,radius)))if(p.distSqr(projected)<=radius*radius&&freeArrival(city,p,reserved))return p.immutable();
        return null;
    }
    private static boolean freeArrival(ServerLevel city,BlockPos at,Set<BlockPos> reserved){if(reserved.contains(at)||!city.hasChunkAt(at)||!NapoliResidentHomes.walkable(city,at))return false;
        var box=new net.minecraft.world.phys.AABB(at.getX()+.2,at.getY(),at.getZ()+.2,at.getX()+.8,at.getY()+1.9,at.getZ()+.8);
        return city.players().stream().noneMatch(p->p.getBoundingBox().intersects(box))&&city.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,box,e->e.isAlive()).isEmpty();}
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%20!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;var census=NapoliCivicLife.census(city);if(census==null)return;
            batch(city,()->{for(var c:census.people()){var e=city.getEntity(c.id());if(e instanceof NapoliResident npc&&!npc.isNoAi()){
                observe(city,npc,c,CitySimulation.destination(city,c));if(city.players().stream().noneMatch(p->p.distanceToSqr(npc)<96*96))withdraw(city,npc);
            }else if(e==null&&!c.spawned())position(city,c,CitySimulation.destination(city,c));}});
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,city)->{if(entity instanceof NapoliResident npc&&managed(npc)){var c=NapoliCivicLife.profile(city,npc.getUUID());observe(city,npc,c,CitySimulation.destination(city,c));unmaterialized(city,c.id());}});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;var census=NapoliCivicLife.census(city);if(census==null)return;for(var c:census.people())if(city.getEntity(c.id()) instanceof NapoliResident npc&&managed(npc)){CityMovement.stand(npc);if(npc.isPassenger())npc.stopRiding();if(npc.isSleeping())npc.stopSleeping();observe(city,npc,c,CitySimulation.destination(city,c));unmaterialized(city,c.id());}});
    }
    private CityJourneys(){}
}
