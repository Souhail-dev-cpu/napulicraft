package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Small persistent NPC incidents; existing player crime and police tactics remain authoritative for players. */
final class CityPublicOrder {
    enum Offence { THEFT, ROBBERY, VANDALISM, FRAUD, SMUGGLING, BRAWL }
    record Incident(String offence,long wantedUntil,long release,BlockPos cell) {
        static final Codec<Incident> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.STRING.fieldOf("offence").forGetter(Incident::offence),Codec.LONG.fieldOf("wantedUntil").forGetter(Incident::wantedUntil),
                Codec.LONG.fieldOf("release").forGetter(Incident::release),BlockPos.CODEC.fieldOf("cell").forGetter(Incident::cell)).apply(i,Incident::new));
    }
    static final AttachmentType<Map<String,Incident>> INCIDENTS=AttachmentRegistry.<Map<String,Incident>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING,Incident.CODEC)).buildAndRegister(Napuli.id("citizen_incidents"));
    static boolean jailed(ServerLevel city,UUID id){var state=city.getAttachedOrElse(INCIDENTS,Map.of()).get(id.toString());return state!=null&&state.release()>city.getGameTime();}
    static void report(ServerLevel city,UUID id,Offence offence){
        var cases=new HashMap<>(city.getAttachedOrElse(INCIDENTS,Map.of()));
        if(cases.containsKey(id.toString()))return;
        cases.put(id.toString(),new Incident(offence.name(),city.getGameTime()+1200,0,Carabinieri.CELLS.get(Math.floorMod(id.hashCode(),Carabinieri.CELLS.size()))));
        city.setAttached(INCIDENTS,Map.copyOf(cases));
        var l=CitySimulation.life(city,id);CitySimulation.put(city,id,l.career(l.health(),Math.max(0,l.reputation()-20),l.experience(),l.rank(),l.absences(),CitySimulation.memory(l,"Segnalazione: "+offence.name())));
    }
    static boolean tickEntity(NapoliResident npc,ServerLevel city){
        var cases=city.getAttachedOrElse(INCIDENTS,Map.of());var incident=cases.get(npc.getUUID().toString());long now=city.getGameTime();
        if(incident!=null&&incident.release()>now){
            npc.getNavigation().stop();if(npc.blockPosition().distSqr(incident.cell())>9&&city.hasChunkAt(incident.cell()))npc.snapTo(incident.cell().getX()+.5,incident.cell().getY(),incident.cell().getZ()+.5,0,0);return true;
        }
        if(npc.role()!=NapoliResident.Role.CARABINIERE||(npc.tickCount+npc.getId())%20!=0)return false;
        for(var entry:cases.entrySet()){
            var state=entry.getValue();if(state.release()>0||state.wantedUntil()<=now)continue;
            var entity=city.getEntity(UUID.fromString(entry.getKey()));
            if(!(entity instanceof NapoliResident suspect)||suspect.distanceToSqr(npc)>40*40||!npc.hasLineOfSight(suspect))continue;
            npc.getNavigation().moveTo(suspect,1.05);
            if(suspect.distanceToSqr(npc)<5){
                var updated=new HashMap<>(cases);updated.put(entry.getKey(),new Incident(state.offence(),state.wantedUntil(),now+1800,state.cell()));city.setAttached(INCIDENTS,Map.copyOf(updated));
                suspect.stopRiding();city.getChunkAt(state.cell());suspect.getNavigation().stop();suspect.snapTo(state.cell().getX()+.5,state.cell().getY(),state.cell().getZ()+.5,0,0);
                for(var p:city.players())if(p.distanceToSqr(npc)<40*40)p.sendSystemMessage(Component.literal("Carabinieri: fermato "+suspect.getDisplayName().getString()+" per "+state.offence().toLowerCase(Locale.ROOT)+"."));
            }
            return true;
        }
        return false;
    }
    static void register(){
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            if(!(entity instanceof NapoliResident npc)||!(npc.level() instanceof ServerLevel city)||NapoliCivicLife.profile(city,npc.getUUID())==null)return;
            var l=CitySimulation.life(city,npc.getUUID());CitySimulation.put(city,npc.getUUID(),l.career(0,l.reputation(),l.experience(),l.rank(),l.absences(),l.memories()));
            var census=NapoliCivicLife.census(city);var people=new LinkedHashMap<UUID,NapoliCivicLife.Citizen>();for(var p:census.people())people.put(p.id(),p);
            CitySimulation.inherit(city,people,NapoliBroadcast.day(city));city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),List.copyOf(people.values())));
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%100!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;
            var cases=new HashMap<>(city.getAttachedOrElse(INCIDENTS,Map.of()));long now=city.getGameTime();
            cases.entrySet().removeIf(e->{
                var state=e.getValue();boolean expired=state.release()>0?state.release()<=now:state.wantedUntil()<=now;
                if(expired&&state.release()>0&&city.getEntity(UUID.fromString(e.getKey())) instanceof NapoliResident npc){
                    npc.getNavigation().stop();npc.snapTo(Carabinieri.RELEASE.getX()+.5,Carabinieri.RELEASE.getY(),Carabinieri.RELEASE.getZ()+.5,0,0);
                }return expired;
            });city.setAttached(INCIDENTS,Map.copyOf(cases));
            if(server.getTickCount()%1200!=0)return;
            var census=NapoliCivicLife.census(city);if(census==null)return;
            for(var actor:census.people()){
                if(actor.age(NapoliBroadcast.day(city))<18||actor.balance()>5||CitySimulation.life(city,actor.id()).needs().hunger()>30||Math.floorMod(actor.id().hashCode()+NapoliBroadcast.day(city),16)!=0)continue;
                if(!(city.getEntity(actor.id()) instanceof NapoliResident npc))continue;
                var victims=city.getEntitiesOfClass(NapoliResident.class,npc.getBoundingBox().inflate(8),v->!v.getUUID().equals(actor.id())&&NapoliCivicLife.profile(city,v.getUUID())!=null);
                if(victims.isEmpty())continue;
                var victim=NapoliCivicLife.profile(city,victims.getFirst().getUUID());int stolen=Math.min(3,victim.balance());
                if(stolen==0)continue;
                city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(c->c.id().equals(actor.id())?c.withMoney(c.balance()+stolen,c.job()):c.id().equals(victim.id())?c.withMoney(c.balance()-stolen,c.job()):c).toList()));
                var offences=new Offence[]{Offence.THEFT,Offence.ROBBERY,Offence.FRAUD,Offence.SMUGGLING};
                report(city,actor.id(),offences[Math.floorMod(actor.id().hashCode(),offences.length)]);break;
            }
        });
    }
    private CityPublicOrder(){}
}
