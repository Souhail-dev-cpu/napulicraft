package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;

/** Household agreements, voluntary meetings and events whose effects last beyond one conversation. */
final class CityRelationships {
    record Agreement(long day,String cook,String washer,String shopper,String carer){static final Codec<Agreement> CODEC=RecordCodecBuilder.create(i->i.group(Codec.LONG.fieldOf("day").forGetter(Agreement::day),Codec.STRING.fieldOf("cook").forGetter(Agreement::cook),Codec.STRING.fieldOf("washer").forGetter(Agreement::washer),Codec.STRING.fieldOf("shopper").forGetter(Agreement::shopper),Codec.STRING.fieldOf("carer").forGetter(Agreement::carer)).apply(i,Agreement::new));}
    record Event(String kind,String detail,long day,long until){static final Codec<Event> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Event::kind),Codec.STRING.fieldOf("detail").forGetter(Event::detail),Codec.LONG.fieldOf("day").forGetter(Event::day),Codec.LONG.fieldOf("until").forGetter(Event::until)).apply(i,Event::new));}
    record Meeting(UUID first,UUID second,BlockPos place,long starts,long ends,boolean settled){static final Codec<Meeting> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("first").forGetter(Meeting::first),UUIDUtil.CODEC.fieldOf("second").forGetter(Meeting::second),BlockPos.CODEC.fieldOf("place").forGetter(Meeting::place),Codec.LONG.fieldOf("starts").forGetter(Meeting::starts),Codec.LONG.fieldOf("ends").forGetter(Meeting::ends),Codec.BOOL.fieldOf("settled").forGetter(Meeting::settled)).apply(i,Meeting::new));}
    static final AttachmentType<Map<String,Agreement>> AGREEMENTS=AttachmentRegistry.<Map<String,Agreement>>builder().persistent(Codec.unboundedMap(Codec.STRING,Agreement.CODEC)).buildAndRegister(Napuli.id("family_agreements"));
    static final AttachmentType<Map<String,List<Event>>> EVENTS=AttachmentRegistry.<Map<String,List<Event>>>builder().persistent(Codec.unboundedMap(Codec.STRING,Event.CODEC.listOf())).buildAndRegister(Napuli.id("personal_life_events"));
    static final AttachmentType<Map<String,Meeting>> MEETINGS=AttachmentRegistry.<Map<String,Meeting>>builder().persistent(Codec.unboundedMap(Codec.STRING,Meeting.CODEC)).buildAndRegister(Napuli.id("neighbor_meetings"));
    static final AttachmentType<Map<String,Long>> STRAIN=AttachmentRegistry.<Map<String,Long>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.LONG)).buildAndRegister(Napuli.id("couple_strain"));
    static final AttachmentType<Map<String,String>> ADDRESSES=AttachmentRegistry.<Map<String,String>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.STRING)).buildAndRegister(Napuli.id("family_last_address"));
    static Agreement agreement(ServerLevel city,NapoliCivicLife.Citizen c){
        if(c.property().isEmpty()||NapoliCivicLife.census(city)==null)return null;long day=NapoliBroadcast.day(city);var saved=city.getAttachedOrElse(AGREEMENTS,Map.of());var previous=saved.get(c.property());if(previous!=null&&previous.day()==day)return previous;var all=new HashMap<>(saved);
        var adults=NapoliCivicLife.household(city,c.property()).stream().filter(p->p.age(day)>=18&&CitySimulation.life(city,p.id()).health()>=45&&!CityPublicOrder.jailed(city,p.id())).sorted(Comparator.comparing(p->p.id().toString())).toList();if(adults.isEmpty())return null;
        var cook=adults.stream().filter(p->!CityHabits.working(p,11000,day)).max(Comparator.comparingInt((NapoliCivicLife.Citizen p)->CityHabits.cooking(city,p.id())).thenComparing(p->p.id().toString())).orElse(adults.getFirst());
        var other=adults.stream().filter(p->!p.id().equals(cook.id())).toList();var washer=other.isEmpty()?cook:other.get(Math.floorMod((int)day,other.size()));
        var shopper=adults.stream().filter(p->!CityHabits.working(p,9700,day)).findFirst().orElse(washer);
        var carer=adults.stream().filter(p->!CityNightlife.nightWorker(p)&&!CityHabits.working(p,2500,day)).findFirst()
                .orElseGet(()->adults.stream().filter(p->!CityNightlife.nightWorker(p)).findFirst().orElse(washer));
        var result=new Agreement(day,cook.id().toString(),washer.id().toString(),shopper.id().toString(),carer.id().toString());all.put(c.property(),result);city.setAttached(AGREEMENTS,Map.copyOf(all));return result;
    }
    static void event(ServerLevel city,UUID person,String kind,String detail,long day,int days){
        var all=new HashMap<>(city.getAttachedOrElse(EVENTS,Map.of()));var events=new ArrayList<>(all.getOrDefault(person.toString(),List.of()));if(events.stream().anyMatch(e->e.kind().equals(kind)&&e.day()==day))return;
        events.add(new Event(kind,detail,day,day+days));while(events.size()>8)events.removeFirst();all.put(person.toString(),List.copyOf(events));city.setAttached(EVENTS,Map.copyOf(all));
        var life=CitySimulation.life(city,person);CitySimulation.put(city,person,life.career(life.health(),life.reputation(),life.experience(),life.rank(),life.absences(),CitySimulation.memory(life,detail)));
    }
    static String recent(ServerLevel city,UUID person,String kind){long day=NapoliBroadcast.day(city);return city.getAttachedOrElse(EVENTS,Map.of()).getOrDefault(person.toString(),List.of()).stream().filter(e->e.kind().equals(kind)&&e.until()>=day).reduce((a,b)->b).map(Event::detail).orElse("");}
    static void loss(ServerLevel city,UUID heir,NapoliCivicLife.Citizen deceased,long day){event(city,heir,"grief","Mi manca "+deceased.name()+". Per qualche giorno preferisco stare con la famiglia.",day,3);var l=CitySimulation.life(city,heir);CitySimulation.put(city,heir,l.withNeeds(l.needs().change(0,0,0,-15,-15,0),l.food(),l.last(),l.water(),l.energy()));}
    static void daily(ServerLevel city,Map<UUID,NapoliCivicLife.Citizen> people,long day){
        var strain=new HashMap<>(city.getAttachedOrElse(STRAIN,Map.of()));var addresses=new HashMap<>(city.getAttachedOrElse(ADDRESSES,Map.of()));
        for(var c:List.copyOf(people.values())){
            if(Math.floorMod(day-c.birth(),CityLifeConfig.settings.daysPerYear)==0)event(city,c.id(),"birthday","Oggi compio "+c.age(day)+" anni. Mi piacerebbe incontrare qualcuno davanti a casa.",day,1);
            String previous=addresses.put(c.id().toString(),c.property());var residence=CityLivingHomes.find(c.property());
            if(previous!=null&&!c.property().isEmpty()&&!previous.equals(c.property()))event(city,c.id(),"home","Mi sono trasferito "+(residence==null?"in una nuova casa.":"a "+residence.name()+"."),day,3);
            if(c.partner().isEmpty()||c.id().toString().compareTo(c.partner())>0)continue;var partner=people.get(UUID.fromString(c.partner()));if(partner==null)continue;
            int a=CitySimulation.life(city,c.id()).relations().getOrDefault(partner.id().toString(),50),b=CitySimulation.life(city,partner.id()).relations().getOrDefault(c.id().toString(),50);String key=c.id()+"/"+partner.id();
            if(a>=25||b>=25){strain.remove(key);continue;}long began=strain.computeIfAbsent(key,k->day);if(day-began<3)continue;
            var leaver=partner;var home=CityLivingHomes.find(c.property());if(home!=null&&partner.id().equals(NapoliProperties.owner(city,home)))leaver=c;var staying=leaver.id().equals(c.id())?partner:c;
            String destination=CityLivingHomes.PUBLIC_HOMES.stream().filter(h->!h.id().equals(staying.property())&&HousingCapacity.reservedOccupants(city,h,people.values())<HousingCapacity.MAX_OCCUPANTS).map(NapoliMergellina.House::id).findFirst().orElse(leaver.property());
            people.put(leaver.id(),leaver.withHousehold("",destination,leaver.balance()));people.put(staying.id(),staying.withHousehold("",staying.property(),staying.balance()));
            event(city,leaver.id(),"separation","Abbiamo deciso di vivere separati dopo diversi giorni difficili.",day,4);event(city,staying.id(),"separation","La nostra relazione è finita: sto cambiando le mie abitudini.",day,4);strain.remove(key);
        }city.setAttached(STRAIN,Map.copyOf(strain));city.setAttached(ADDRESSES,Map.copyOf(addresses));
    }
    static Meeting attending(ServerLevel city,UUID id){long now=city.getGameTime();return city.getAttachedOrElse(MEETINGS,Map.of()).values().stream().filter(m->!m.settled()&&(m.first().equals(id)||m.second().equals(id))&&now>=m.starts()-2000&&now<m.ends()).findFirst().orElse(null);}
    static BlockPos socialDestination(ServerLevel city,NapoliCivicLife.Citizen c){var m=attending(city,c.id());return m==null?null:m.place();}
    static UUID carer(ServerLevel city,NapoliCivicLife.Citizen child){var agreement=agreement(city,child);return agreement!=null?UUID.fromString(agreement.carer()):child.parents().isEmpty()?child.id():UUID.fromString(child.parents());}
    static boolean escorting(ServerLevel city,NapoliCivicLife.Citizen adult,long time){var census=NapoliCivicLife.census(city);long day=NapoliBroadcast.day(city);if(census==null||time<2200||time>4000||Math.floorMod(day,7)>=5)return false;return census.people().stream().anyMatch(child->!child.parents().isEmpty()&&child.age(day)>=3&&child.age(day)<12&&carer(city,child).equals(adult.id())&&!CityJourneys.at(city,child,CityServices.place("school"),6));}
    static void tick(ServerLevel city){
        var census=NapoliCivicLife.census(city);if(census==null)return;long now=city.getGameTime(),day=NapoliBroadcast.day(city),clock=NapoliBroadcast.time(city);var all=new HashMap<>(city.getAttachedOrElse(MEETINGS,Map.of()));all.values().removeIf(m->now-m.ends()>48000);
        for(var entry:List.copyOf(all.entrySet())){
            var m=entry.getValue();if(m.settled())continue;var a=NapoliCivicLife.profile(city,m.first());var b=NapoliCivicLife.profile(city,m.second());boolean met=now>=m.starts()&&a!=null&&b!=null&&CityJourneys.at(city,a,m.place(),5)&&CityJourneys.at(city,b,m.place(),5);
            if(met){CitySimulation.encounter(city,a.id(),b.id(),day);for(var person:List.of(a,b)){var l=CitySimulation.life(city,person.id());CitySimulation.put(city,person.id(),l.withNeeds(l.needs().change(0,0,0,10,15,0),l.food(),l.last(),l.water(),l.energy()));event(city,person.id(),"meeting","Ho passato del tempo con "+(person.id().equals(a.id())?b.name():a.name())+".",day,2);}}
            if(met||now>=m.ends())all.put(entry.getKey(),new Meeting(m.first(),m.second(),m.place(),m.starts(),m.ends(),true));
        }
        if(clock>=8500&&clock<11000&&all.keySet().stream().noneMatch(k->k.startsWith(day+"/"))){
            var candidates=census.people().stream().sorted(Comparator.comparing((NapoliCivicLife.Citizen c)->recent(city,c.id(),"birthday").isEmpty())).toList();
            outer:for(var c:candidates)if(c.age(day)>=12&&CitySimulation.life(city,c.id()).needs().sleep()>=35&&!CityHabits.working(c,12000,day)&&!CityPublicOrder.jailed(city,c.id())){
                var home=CityLivingHomes.find(c.property());if(home==null)continue;
                for(var other:census.people())if(!c.id().equals(other.id())&&other.age(day)>=12&&!CityHabits.working(other,12000,day)&&!CityPublicOrder.jailed(city,other.id())&&NapoliCivicLife.home(other).distSqr(home.entrance())<100*100&&(CitySimulation.life(city,c.id()).relations().getOrDefault(other.id().toString(),0)>=60||!recent(city,c.id(),"birthday").isEmpty()&&c.property().equals(other.property()))){
                    all.put(day+"/"+c.id(),new Meeting(c.id(),other.id(),home.entrance().south(3),now+2000,now+4400,false));break outer;
                }
            }
        }
        city.setAttached(MEETINGS,Map.copyOf(all));
    }
    static boolean visit(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c){var m=attending(city,c.id());if(m==null||CitySimulation.life(city,c.id()).needs().sleep()<20||CitySimulation.life(city,c.id()).health()<45||CityHabits.working(c,NapoliBroadcast.time(city),NapoliBroadcast.day(city)))return false;
        CityMovement.stand(npc);npc.setActivity(NapoliHouseholds.Activity.SOCIAL);CityDomestic.move(npc,CityRoutines.localGoal(city,npc,m.place()));if(m.place().distToCenterSqr(npc.position())<16){var other=city.getEntity(m.first().equals(c.id())?m.second():m.first());if(other!=null)npc.getLookControl().setLookAt(other,30,30);}return true;}
    static void register(){ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%200==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)tick(city);}});}
    private CityRelationships(){}
}
