package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;

/** Residents keep their homes and identities while following varied, need-aware evening routines. */
final class CityNightlife {
    static boolean night(long time){long clock=Math.floorMod(time,24000);return clock>=12500&&clock<22500;}
    static boolean vendor(NapoliCivicLife.Citizen c){var d=DistrictLife.district(c);return d!=null&&c.profession()==NapoliCivicLife.Job.MERCHANT&&c.id().equals(DistrictLife.identity(d,8));}
    static boolean nightWorker(NapoliCivicLife.Citizen c){return CityProfessions.night(c.profession())||vendor(c);}
    static int closing(NapoliCivicLife.Citizen c,long day){if(Math.floorMod(c.id().hashCode()/7,5)==0)return 22500;return 18500+Math.floorMod(c.id().hashCode()/13,5)*500+(Math.floorMod(day,7)>=5?1000:0);}
    static boolean outgoing(NapoliCivicLife.Citizen c,long day){return c.age(day)>=18&&c.age(day)<75&&!nightWorker(c)&&Math.floorMod(c.id().hashCode(),100)<CityLifeConfig.settings.nightActivityPercent;}
    static boolean outing(ServerLevel city,NapoliCivicLife.Citizen c,CitySimulation.Life life,long time){
        var n=life.needs();long day=NapoliBroadcast.day(city);
        return night(time)&&Math.floorMod(time,24000)<closing(c,day)&&outgoing(c,day)&&!CityHabits.working(c,time,day)&&!CityEvents.danger(city)
                &&!CityPublicOrder.jailed(city,c.id())&&life.health()>=60&&n.sleep()>=40&&n.hunger()>=40&&n.hygiene()>=30&&n.bladder()>=35
                &&CityRelationships.recent(city,c.id(),"grief").isEmpty();
    }
    static BlockPos destination(ServerLevel city,NapoliCivicLife.Citizen c){
        var d=DistrictLife.district(c);var home=NapoliCivicLife.home(c);BlockPos center=d==null?CityNeighborhood.cafe(home):d.business().approach(5);
        if(Math.floorMod(c.id().hashCode()/17,3)==1){int[][] route={{-10,0},{0,8},{10,0},{0,-6}};int at=Math.floorMod(city.getGameTime()/500+c.id().hashCode(),route.length);var offset=route[at];var candidate=center.offset(offset[0],0,offset[1]);if(city.hasChunkAt(candidate)&&NapoliResidentHomes.walkable(city,candidate)&&!CityProperty.home(city,candidate))return candidate;}
        return CityCharacter.meetingPoint(city,c,center);
    }
    static boolean tick(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c,NapoliHouseholds.Activity action){
        if(action!=NapoliHouseholds.Activity.SOCIAL||!outing(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city))||Math.floorMod(c.id().hashCode()/19,12)!=0)return false;
        var goal=destination(city,c);if(goal.distToCenterSqr(npc.position())>9)return false;npc.getNavigation().stop();npc.animate(CityGestures.Gesture.HAPPY,60);
        if(Math.floorMod(city.getGameTime()+Math.floorMod(c.id().hashCode(),160),160)<40){int note=Math.floorMod(city.getGameTime()/160+c.id().hashCode(),5);CityEffects.sound(city,null,npc.blockPosition(),SoundEvents.NOTE_BLOCK_GUITAR.value(),SoundSource.AMBIENT,.22f,new float[]{1f,1.12f,1.26f,1.5f,1.26f}[note]);}return true;
    }
    static String description(ServerLevel city,NapoliCivicLife.Citizen c){
        if(vendor(c))return "Questa sera tengo aperta la bottega fino alle 04:30.";
        if(!outing(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city)))return "";
        return Math.floorMod(c.id().hashCode()/17,3)==1?"Faccio una passeggiata nel quartiere, poi torno a casa.":"Stasera mi fermo vicino alla bottega per un caffè e due chiacchiere.";
    }
    static void register(){
        CityThieves.register();
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%200!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;CityThieves.seed(city);if(!night(NapoliBroadcast.time(city)))return;
            for(var p:city.players())if(!p.isSpectator()){
                var npc=city.getEntitiesOfClass(NapoliResident.class,p.getBoundingBox().inflate(16),n->n.activity()==NapoliHouseholds.Activity.SOCIAL&&!n.isSleeping()).stream().filter(n->{var c=NapoliCivicLife.profile(city,n.getUUID());return c!=null&&outing(city,c,CitySimulation.life(city,c.id()),NapoliBroadcast.time(city));}).findFirst().orElse(null);
                if(npc!=null&&Math.floorMod(server.getTickCount()+npc.getId(),1200)<200)CityNeighborhood.say(city,npc,city.isRaining()?"Ripariamoci qui, poi torniamo a casa.":"La sera è ancora giovane. Ci fermiamo a bere qualcosa?",16);
            }
        });
    }
    private CityNightlife(){}
}
