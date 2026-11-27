package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** A killed resident keeps their identity and estate; complaints use the observed assault location. */
final class CityResidentSafety {
    record Return(long day,BlockPos at,CompoundTag actor,boolean citizen,boolean patrol){
        static final Codec<Return> CODEC=RecordCodecBuilder.create(i->i.group(Codec.LONG.fieldOf("day").forGetter(Return::day),BlockPos.CODEC.fieldOf("at").forGetter(Return::at),CompoundTag.CODEC.fieldOf("actor").forGetter(Return::actor),Codec.BOOL.fieldOf("citizen").forGetter(Return::citizen),Codec.BOOL.fieldOf("patrol").forGetter(Return::patrol)).apply(i,Return::new));
    }
    record Complaint(UUID attacker,BlockPos seen,long callAt,long until,boolean reported){
        static final Codec<Complaint> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("attacker").forGetter(Complaint::attacker),BlockPos.CODEC.fieldOf("seen").forGetter(Complaint::seen),Codec.LONG.fieldOf("callAt").forGetter(Complaint::callAt),Codec.LONG.fieldOf("until").forGetter(Complaint::until),Codec.BOOL.fieldOf("reported").forGetter(Complaint::reported)).apply(i,Complaint::new));
    }
    static final AttachmentType<Map<String,Return>> RETURNS=AttachmentRegistry.<Map<String,Return>>builder().persistent(Codec.unboundedMap(Codec.STRING,Return.CODEC)).buildAndRegister(Napuli.id("resident_returns"));
    static final AttachmentType<Map<String,Complaint>> CALLS=AttachmentRegistry.<Map<String,Complaint>>builder().persistent(Codec.unboundedMap(Codec.STRING,Complaint.CODEC)).buildAndRegister(Napuli.id("resident_complaints"));
    static boolean human(NapoliResident npc){return !npc.role().spirit()&&!npc.entityTags().contains("napuli_practice_actor");}
    static boolean absent(ServerLevel city,UUID id){return city.getAttachedOrElse(RETURNS,Map.of()).containsKey(id.toString());}
    static void attacked(NapoliResident npc,ServerPlayer attacker){
        var city=(ServerLevel)npc.level();if(!human(npc)||!npc.isAlive()||city.dimension()!=NapoliMap.LEVEL)return;
        long now=city.getGameTime();var all=new HashMap<>(city.getAttachedOrElse(CALLS,Map.of()));var old=all.get(npc.getUUID().toString());
        if(old!=null&&old.until()>now&&old.attacker().equals(attacker.getUUID())&&(!old.reported()||Carabinieri.isWanted(attacker)))return;
        all.put(npc.getUUID().toString(),new Complaint(attacker.getUUID(),attacker.blockPosition().immutable(),now+(npc.role()==NapoliResident.Role.CARABINIERE?0:60),now+600,false));city.setAttached(CALLS,Map.copyOf(all));
        CityNeighborhood.leave(city,npc.getUUID());CityMovement.stand(npc);if(npc.isSleeping())npc.stopSleeping();
        for(var viewer:city.players())if(viewer.distanceToSqr(npc)<24*24)NpcSpeech.reply(viewer,npc,CityText.t("safety.calling"));
    }
    static boolean react(NapoliResident npc,ServerLevel city){
        var call=city.getAttachedOrElse(CALLS,Map.of()).get(npc.getUUID().toString());if(call==null||city.getGameTime()>=call.until()||npc.role()==NapoliResident.Role.CARABINIERE||npc.role().fighter())return false;
        if(npc.isNoAi()||npc.isPassenger())return false;
        var attacker=city.getServer().getPlayerList().getPlayer(call.attacker());if(attacker==null||attacker.level()!=city||attacker.distanceToSqr(npc)>24*24)return false;
        npc.setTarget(null);npc.setActivity(NapoliHouseholds.Activity.COMMUTE);
        if((npc.tickCount+npc.getId())%10!=0)return true;
        Vec3 away=npc.position().subtract(attacker.position()).multiply(1,0,1).normalize();if(away.lengthSqr()<.01)away=new Vec3(1,0,0);
        for(int turn:new int[]{0,1,-1,2,-2}){double angle=Math.atan2(away.z,away.x)+turn*Math.PI/4;var target=npc.blockPosition().offset((int)Math.round(Math.cos(angle)*8),0,(int)Math.round(Math.sin(angle)*8));
            for(int dy:new int[]{0,1,-1}){var at=target.offset(0,dy,0);if(!city.hasChunkAt(at)||!NapoliResidentHomes.walkable(city,at))continue;var path=npc.getNavigation().createPath(at,0);if(path!=null&&path.canReach()&&CityMovement.beforeMove(npc,at)){npc.getNavigation().moveTo(path,1.35);return true;}}
        }return true;
    }
    static void killed(NapoliResident npc,ServerLevel city){
        if(!human(npc)||PhoneServices.temporary(npc))return;String key=npc.getUUID().toString();if(absent(city,npc.getUUID()))return;
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,city.registryAccess());npc.saveWithoutId(out);
        var all=new HashMap<>(city.getAttachedOrElse(RETURNS,Map.of()));all.put(key,new Return(Pickpocket.day(npc)+1,npc.returnPost(),out.buildResult(),NapoliCivicLife.profile(city,npc.getUUID())!=null,PolicePatrols.managed(npc)));city.setAttached(RETURNS,Map.copyOf(all));
        CityMovement.release(city,npc.getUUID());CityNeighborhood.leave(city,npc.getUUID());CityNetwork.npcTicket(city,npc.getUUID(),null);CityMobility.cancel(city,npc.getUUID());CityJourneys.unmaterialized(city,npc.getUUID());
        var calls=new HashMap<>(city.getAttachedOrElse(CALLS,Map.of()));calls.remove(key);city.setAttached(CALLS,Map.copyOf(calls));
    }
    static void tick(ServerLevel city){
        long now=city.getGameTime(),day=Math.floorDiv(city.getOverworldClockTime(),24000);
        var calls=new HashMap<>(city.getAttachedOrElse(CALLS,Map.of()));boolean changed=false;
        for(var e:List.copyOf(calls.entrySet())){
            var c=e.getValue();if(c.until()<=now||absent(city,UUID.fromString(e.getKey()))){calls.remove(e.getKey());changed=true;continue;}
            if(c.reported()||now<c.callAt())continue;var player=city.getServer().getPlayerList().getPlayer(c.attacker());
            if(player==null||player.level()!=city||player.isSpectator()||Carabinieri.isJailed(player))continue;
            Carabinieri.denounce(player,Carabinieri.Crime.ASSAULT,c.seen());calls.put(e.getKey(),new Complaint(c.attacker(),c.seen(),c.callAt(),c.until(),true));changed=true;
            if(city.getEntity(UUID.fromString(e.getKey())) instanceof NapoliResident npc)for(var viewer:city.players())if(viewer.distanceToSqr(npc)<24*24)NpcSpeech.reply(viewer,npc,CityText.t("safety.reported"));
        }if(changed)city.setAttached(CALLS,Map.copyOf(calls));
        var pending=new HashMap<>(city.getAttachedOrElse(RETURNS,Map.of()));changed=false;int restored=0;
        for(var e:List.copyOf(pending.entrySet())){
            var r=e.getValue();UUID id=UUID.fromString(e.getKey());if(day<r.day()||city.getEntity(id)!=null||CityJourneys.known(city,id))continue;
            if(r.patrol()){pending.remove(e.getKey());changed=true;continue;} // The bounded patrol manager recreates the same identity.
            if(r.citizen()){
                var citizen=NapoliCivicLife.profile(city,id);if(citizen!=null){var l=CitySimulation.life(city,id);CitySimulation.put(city,id,new CitySimulation.Life(l.needs(),100,l.reputation(),l.experience(),l.rank(),l.absences(),l.food(),now,l.personality(),l.relations(),CitySimulation.memory(l,"Ritorno dopo le cure"),l.water(),l.energy(),l.lastBirth()));CityJourneys.put(city,id,CityJourneys.plan(NapoliCivicLife.home(citizen),NapoliCivicLife.home(citizen),now,80));CityJourneys.unmaterialized(city,id);}
                pending.remove(e.getKey());changed=true;continue;
            }
            if(restored>=4||!city.hasChunkAt(r.at())||city.players().stream().noneMatch(p->p.isAlive()&&!p.isSpectator()&&r.at().distToCenterSqr(p.position())<96*96))continue;
            var at=CityJourneys.spawn(city,r.at());if(at==null)continue;
            var npc=NapoliResident.create(city,NapoliResident.Role.GENNARO,at,0);npc.load(TagValueInput.create(ProblemReporter.DISCARDING,city.registryAccess(),r.actor()));npc.setUUID(id);npc.setHealth(npc.getMaxHealth());npc.deathTime=0;npc.hurtTime=0;npc.setInvulnerableTime(0);npc.setDeltaMovement(Vec3.ZERO);npc.setTarget(null);npc.clearFire();npc.stopSleeping();npc.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,npc.getYRot(),0);
            if(city.addFreshEntity(npc)){pending.remove(e.getKey());changed=true;restored++;}
        }if(changed)city.setAttached(RETURNS,Map.copyOf(pending));
    }
    static void register(){
        ServerLivingEntityEvents.AFTER_DEATH.register((victim,source)->{if(victim instanceof NapoliResident npc&&npc.level() instanceof ServerLevel city)killed(npc,city);});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%20==0)for(var city:s.getAllLevels())tick(city);});
    }
    private CityResidentSafety(){}
}
