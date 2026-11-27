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
    private static final AttachmentType<UUID> NPC_TARGET=AttachmentRegistry.create(Napuli.id("police_citizen_target"));
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
        if(npc.role()!=NapoliResident.Role.CARABINIERE)return false;
        UUID target=npc.getAttached(NPC_TARGET);
        var current=target==null?null:cases.get(target.toString());
        var actor=target==null?null:city.getEntity(target);
        boolean active=current!=null&&current.release()==0&&current.wantedUntil()>now&&actor instanceof NapoliResident suspect&&suspect.isAlive()&&suspect.distanceToSqr(npc)<=48*48;
        if(!active&&target!=null){npc.removeAttached(NPC_TARGET);npc.getNavigation().stop();}
        if((npc.tickCount+npc.getId())%20!=0)return active;
        NapoliResident closest=null;double distance=48*48;
        for(var entry:cases.entrySet()){
            var state=entry.getValue();if(state.release()>0||state.wantedUntil()<=now)continue;
            var entity=city.getEntity(UUID.fromString(entry.getKey()));
            if(!(entity instanceof NapoliResident suspect)||!suspect.isAlive()||!npc.hasLineOfSight(suspect))continue;
            double next=suspect.distanceToSqr(npc);if(next<distance){closest=suspect;distance=next;}
        }
        if(closest!=null){
            var suspect=closest;var state=cases.get(suspect.getUUID().toString());npc.setAttached(NPC_TARGET,suspect.getUUID());
            npc.getNavigation().moveTo(suspect,1.3);
            if(PoliceTactics.canGrab(npc,suspect)){
                var updated=new HashMap<>(cases);updated.put(suspect.getUUID().toString(),new Incident(state.offence(),state.wantedUntil(),now+1800,state.cell()));city.setAttached(INCIDENTS,Map.copyOf(updated));
                CityThieves.recover(city,suspect.getUUID());
                suspect.stopRiding();city.getChunkAt(state.cell());suspect.getNavigation().stop();suspect.snapTo(state.cell().getX()+.5,state.cell().getY(),state.cell().getZ()+.5,0,0);
                for(var p:city.players())if(p.distanceToSqr(npc)<40*40)NpcSpeech.say(p,npc,Component.literal("Fermato "+suspect.getDisplayName().getString()+" per "+state.offence().toLowerCase(Locale.ROOT)+"."));
                npc.removeAttached(NPC_TARGET);npc.getNavigation().stop();
            }
            return true;
        }
        return active;
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%100!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;
            var cases=new HashMap<>(city.getAttachedOrElse(INCIDENTS,Map.of()));long now=city.getGameTime();
            cases.entrySet().removeIf(e->{
                var state=e.getValue();boolean expired=state.release()>0?state.release()<=now:state.wantedUntil()<=now;
                if(expired&&state.release()>0&&city.getEntity(UUID.fromString(e.getKey())) instanceof NapoliResident npc){
                    npc.getNavigation().stop();npc.snapTo(Carabinieri.RELEASE.getX()+.5,Carabinieri.RELEASE.getY(),Carabinieri.RELEASE.getZ()+.5,0,0);
                }return expired;
            });city.setAttached(INCIDENTS,Map.copyOf(cases));
        });
    }
    private CityPublicOrder(){}
}
