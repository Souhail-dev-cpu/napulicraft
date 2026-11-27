package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.server.level.ServerLevel;

/** Preferences are stable per person; practice and family agreements survive world saves. */
final class CityHabits {
    static final AttachmentType<Map<String,Integer>> PRACTICE=AttachmentRegistry.<Map<String,Integer>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING,Codec.intRange(0,100))).buildAndRegister(Napuli.id("cooking_practice"));
    static void register() {}
    static int offset(UUID id){return Math.floorMod(id.hashCode(),361)-180;}
    static int cooking(ServerLevel city,UUID id){return Math.min(100,20+Math.floorMod(id.hashCode()*31,41)+city.getAttachedOrElse(PRACTICE,Map.of()).getOrDefault(id.toString(),0));}
    static void practice(ServerLevel city,UUID id){var all=new HashMap<>(city.getAttachedOrElse(PRACTICE,Map.of()));all.put(id.toString(),Math.min(40,all.getOrDefault(id.toString(),0)+1));city.setAttached(PRACTICE,Map.copyOf(all));}
    static boolean working(NapoliCivicLife.Citizen c,long time,long day){
        if(CityNightlife.vendor(c))return time>=12000&&time<22500;
        long personal=time-offset(c.id());if(DistrictLife.resident(c)&&c.profession()==NapoliCivicLife.Job.BAKER){long local=Math.floorMod(personal,24000),weekday=Math.floorMod(day+Math.floorDiv(personal,24000),7);return local>=1800&&local<(weekday==6?6500:11000);}return c.profession()!=null&&CityProfessions.open(c.profession(),Math.floorMod(personal,24000),day+Math.floorDiv(personal,24000));
    }
    static UUID cook(ServerLevel city,NapoliCivicLife.Citizen person,long time){
        var agreement=CityRelationships.agreement(city,person);if(agreement!=null){var assigned=NapoliCivicLife.profile(city,UUID.fromString(agreement.cook()));if(assigned!=null&&!working(assigned,time,NapoliBroadcast.day(city))&&CitySimulation.life(city,assigned.id()).health()>=45)return assigned.id();}
        var census=NapoliCivicLife.census(city);if(census==null||person.property().isEmpty())return person.id();long day=NapoliBroadcast.day(city);
        return NapoliCivicLife.household(city,person.property()).stream().filter(c->c.age(day)>=18&&CitySimulation.life(city,c.id()).health()>=45&&!working(c,time,day)&&!CityPublicOrder.jailed(city,c.id()))
                .max(Comparator.comparingInt((NapoliCivicLife.Citizen c)->cooking(city,c.id())+(int)Math.floorMod(day+c.id().hashCode(),3)*5).thenComparing(c->c.id().toString())).map(NapoliCivicLife.Citizen::id).orElse(person.id());
    }
    static NapoliHouseholds.Activity choose(ServerLevel city,NapoliCivicLife.Citizen c,CitySimulation.Life life,long time){
        var n=life.needs();long day=NapoliBroadcast.day(city);int age=c.age(day);boolean child=age<18;
        if(life.health()<45)return NapoliHouseholds.Activity.CARE;
        if(CityServices.needsVisit(city,c)&&n.hunger()>=18)return NapoliHouseholds.Activity.CARE;
        if(n.hunger()<18)return CityTraces.foodAvailable(city,c)?NapoliHouseholds.Activity.EAT:NapoliHouseholds.Activity.SHOP;
        if(n.bladder()<15||n.hygiene()<12)return NapoliHouseholds.Activity.WASH;
        if(n.sleep()<15)return NapoliHouseholds.Activity.SLEEP;
        long clock=Math.floorMod(time-offset(c.id()),24000);
        if(CityNightlife.nightWorker(c)&&!working(c,time,day)&&(clock>=22500||clock<8500))return NapoliHouseholds.Activity.SLEEP;
        if(CityThieves.onProwl(city,c,life,time))return NapoliHouseholds.Activity.SOCIAL;
        if(CityNightlife.outing(city,c,life,time))return NapoliHouseholds.Activity.SOCIAL;
        if((clock>=16500||clock<900)&&!CityNightlife.nightWorker(c))return NapoliHouseholds.Activity.SLEEP;
        if(child&&age>=3&&clock>=2500&&clock<8000&&Math.floorMod(day,7)<5)return NapoliHouseholds.Activity.SCHOOL;
        if(!child&&CityRelationships.escorting(city,c,time))return NapoliHouseholds.Activity.SCHOOL;
        if(!child&&age<CityLifeConfig.settings.retirementAge&&working(c,time,day))return NapoliHouseholds.Activity.WORK;
        if(CityEvents.danger(city))return NapoliHouseholds.Activity.REST;
        if(n.sleep()<35)return NapoliHouseholds.Activity.REST;
        if(!CityRelationships.recent(city,c.id(),"grief").isEmpty())return NapoliHouseholds.Activity.REST;
        if(CityEvents.celebrating(city)&&clock<19000&&life.personality().equals("sportivo"))return NapoliHouseholds.Activity.CELEBRATE;
        if(life.food()==0&&n.hunger()<60&&!child)return NapoliHouseholds.Activity.SHOP;
        if(n.hunger()<35||clock>=1000&&clock<1900||clock>=10500&&clock<11600)return NapoliHouseholds.Activity.EAT;
        if(n.hygiene()<40||n.bladder()<30||clock>=14500&&clock<15600)return NapoliHouseholds.Activity.WASH;
        if(child&&age<3)return NapoliHouseholds.Activity.REST;
        if(child)return clock<10000?NapoliHouseholds.Activity.STUDY:NapoliHouseholds.Activity.PLAY;
        var agreement=CityRelationships.agreement(city,c);
        if(clock>=9500&&clock<10500&&life.food()<5&&(agreement==null||agreement.shopper().equals(c.id().toString())))return NapoliHouseholds.Activity.SHOP;
        if(clock>=10000&&clock<12000&&cook(city,c,time).equals(c.id()))return NapoliHouseholds.Activity.COOK;
        if(clock>=11600&&clock<14500&&agreement!=null&&agreement.washer().equals(c.id().toString())&&(CityTraces.home(city,c.property()).dishes()>0||CityTraces.home(city,c.property()).laundry()>0))return NapoliHouseholds.Activity.CLEAN;
        if(clock>=12000&&clock<16000)return life.personality().equals("sportivo")||life.personality().equals("tranquillo")?NapoliHouseholds.Activity.MATCH:NapoliHouseholds.Activity.SOCIAL;
        return city.isRaining()?NapoliHouseholds.Activity.REST:NapoliHouseholds.Activity.SOCIAL;
    }
    static String reason(ServerLevel city,NapoliCivicLife.Citizen c){
        var l=CitySimulation.life(city,c.id());var n=l.needs();
        if(l.health()<45)return "Non sto bene: cerco una visita medica.";
        if(n.hunger()<18)return "Ho troppa fame per aspettare l'orario del pranzo.";
        if(n.sleep()<35)return "Sono stanco: oggi mi riposo e rinuncio a uscire.";
        if(n.bladder()<30||n.hygiene()<40)return "Prima devo passare dal bagno.";
        var activity=choose(city,c,l,NapoliBroadcast.time(city));
        if(activity==NapoliHouseholds.Activity.SOCIAL&&CityNightlife.night(NapoliBroadcast.time(city))){String evening=CityNightlife.description(city,c);if(!evening.isEmpty())return evening;}
        return switch(activity){case WORK->"Ho il mio turno di lavoro.";case SCHOOL->"Mi aspettano a scuola.";case COOK->"Oggi tocca a me cucinare: ci siamo divisi le faccende.";case SHOP->"Devo comprare le provviste per casa.";case SLEEP->"È il mio momento di dormire.";case MATCH->"Mi rilasso guardando la televisione.";default->city.isRaining()?"Aspetto che passi la pioggia.":"Ho un po' di tempo per incontrare qualcuno.";};
    }
    private CityHabits(){}
}
