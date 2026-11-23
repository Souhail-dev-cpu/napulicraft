package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;

/** The census remains authoritative when entities or their chunks are absent. */
final class CitySimulation {
    // Register persistent needs before any world attachments are decoded.
    static void register() {}

    record Needs(int hunger,int sleep,int hygiene,int fun,int social,int bladder) {
        static final Codec<Needs> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.INT.fieldOf("hunger").forGetter(Needs::hunger),Codec.INT.fieldOf("sleep").forGetter(Needs::sleep),
                Codec.INT.fieldOf("hygiene").forGetter(Needs::hygiene),Codec.INT.fieldOf("fun").forGetter(Needs::fun),
                Codec.INT.fieldOf("social").forGetter(Needs::social),Codec.INT.fieldOf("bladder").forGetter(Needs::bladder)).apply(i,Needs::new));
        int mood() { return (hunger+sleep+hygiene+fun+social)/5; }
        Needs change(int a,int b,int c,int d,int e,int f) { return new Needs(clamp(hunger+a),clamp(sleep+b),clamp(hygiene+c),clamp(fun+d),clamp(social+e),clamp(bladder+f)); }
    }
    record Life(Needs needs,int health,int reputation,int experience,int rank,int absences,int food,long last,
                String personality,Map<String,Integer> relations,List<String> memories,int water,int energy,long lastBirth) {
        static final Codec<Life> CODEC=RecordCodecBuilder.create(i->i.group(
                Needs.CODEC.fieldOf("needs").forGetter(Life::needs),Codec.INT.fieldOf("health").forGetter(Life::health),
                Codec.INT.fieldOf("reputation").forGetter(Life::reputation),Codec.INT.fieldOf("experience").forGetter(Life::experience),
                Codec.INT.fieldOf("rank").forGetter(Life::rank),Codec.INT.fieldOf("absences").forGetter(Life::absences),
                Codec.INT.fieldOf("food").forGetter(Life::food),Codec.LONG.fieldOf("last").forGetter(Life::last),
                Codec.STRING.fieldOf("personality").forGetter(Life::personality),Codec.unboundedMap(Codec.STRING,Codec.INT).fieldOf("relations").forGetter(Life::relations),
                Codec.STRING.listOf().fieldOf("memories").forGetter(Life::memories),Codec.INT.fieldOf("water").forGetter(Life::water),
                Codec.INT.fieldOf("energy").forGetter(Life::energy),Codec.LONG.fieldOf("lastBirth").forGetter(Life::lastBirth)).apply(i,Life::new));
        Life withNeeds(Needs n,int meals,long now,int newWater,int newEnergy) { return new Life(n,health,reputation,experience,rank,absences,meals,now,personality,relations,memories,newWater,newEnergy,lastBirth); }
        Life career(int hp,int rep,int xp,int grade,int missing,List<String> news) { return new Life(needs,hp,rep,xp,grade,missing,food,last,personality,relations,news,water,energy,lastBirth); }
    }
    static final AttachmentType<Map<String,Life>> LIFE=AttachmentRegistry.<Map<String,Life>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING,Life.CODEC)).buildAndRegister(Napuli.id("city_life"));
    static Life fresh(UUID id) { return new Life(new Needs(85,85,85,75,75,85),100,50,0,0,0,8,0,
            new String[]{"socievole","tranquillo","ambizioso","creativo","sportivo"}[Math.floorMod(id.hashCode(),5)],Map.of(),List.of(),0,0,-100); }
    static Life life(ServerLevel city,UUID id) { return city.getAttachedOrElse(LIFE,Map.of()).getOrDefault(id.toString(),fresh(id)); }
    static void put(ServerLevel city,UUID id,Life state) {
        var copy=new HashMap<>(city.getAttachedOrElse(LIFE,Map.of()));copy.put(id.toString(),state);city.setAttached(LIFE,Map.copyOf(copy));
    }
    private static int clamp(int n) { return Math.clamp(n,0,100); }
    static List<String> memory(Life l,String message) {
        var list=new ArrayList<>(l.memories());list.add(message);while(list.size()>6)list.removeFirst();return List.copyOf(list);
    }
    static void birth(ServerLevel city,UUID id,long day) {
        var l=life(city,id);put(city,id,new Life(l.needs(),l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),l.food()-2,l.last(),l.personality(),l.relations(),memory(l,"Nascita in famiglia, giorno "+day),l.water(),l.energy(),day));
    }
    static void inherit(ServerLevel city,Map<UUID,NapoliCivicLife.Citizen> people,long day) {
        for(var deceased:List.copyOf(people.values())) {
            var l=life(city,deceased.id());
            if(l.health()>0&&deceased.age(day)<CityLifeConfig.settings.maximumAge)continue;
            var heirs=people.values().stream().filter(p->!p.id().equals(deceased.id())&&
                    (p.id().toString().equals(deceased.partner())||p.parents().equals(deceased.id().toString())||life(city,p.id()).relations().containsKey("parent:"+deceased.id()))).toList();
            var spouse=heirs.stream().filter(p->p.id().toString().equals(deceased.partner())).findFirst();
            if(spouse.isPresent())heirs=List.of(spouse.get());
            var owners=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));
            var transferTo=heirs.isEmpty()?null:heirs.getFirst().id();
            for(var entry:List.copyOf(owners.entrySet()))if(entry.getValue().equals(deceased.id())) {
                HouseGuests.clear(city,entry.getKey());
                if(transferTo==null)owners.remove(entry.getKey());else owners.put(entry.getKey(),transferTo);
            }
            city.setAttached(NapoliProperties.OWNERS,Map.copyOf(owners));
            CityHousingFinance.inherit(city,deceased.id(),transferTo);
            for(int i=0;i<heirs.size();i++) {
                var heir=people.get(heirs.get(i).id());
                int share=deceased.balance()/heirs.size()+(i==0?deceased.balance()%heirs.size():0);
                people.put(heir.id(),heir.withHousehold(heir.partner().equals(deceased.id().toString())?"":heir.partner(),
                        heir.property().isEmpty()?deceased.property():heir.property(),Math.min(99999,heir.balance()+share)));
                var memory=life(city,heir.id());put(city,heir.id(),memory.career(memory.health(),memory.reputation(),memory.experience(),memory.rank(),memory.absences(),memory(memory,"Eredità di "+deceased.name())));
            }
            put(city,deceased.id(),l.career(0,l.reputation(),l.experience(),l.rank(),l.absences(),memory(l,"Decesso, giorno "+day)));
            people.remove(deceased.id());
            var entity=city.getEntity(deceased.id());if(entity!=null)entity.discard();
        }
    }
    static void parentage(ServerLevel city,UUID child,UUID mother,UUID father) {
        var l=life(city,child);var family=new HashMap<>(l.relations());family.put("parent:"+mother,100);family.put("parent:"+father,100);
        put(city,child,new Life(l.needs(),l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),l.food(),l.last(),l.personality(),Map.copyOf(family),l.memories(),l.water(),l.energy(),l.lastBirth()));
    }
    static Set<String> ancestors(ServerLevel city,NapoliCivicLife.Citizen c,Map<UUID,NapoliCivicLife.Citizen> people,int depth) {
        var ids=new HashSet<String>();if(depth==0)return ids;
        if(!c.parents().isEmpty())ids.add(c.parents());
        for(var key:life(city,c.id()).relations().keySet())if(key.startsWith("parent:"))ids.add(key.substring(7));
        for(var id:List.copyOf(ids)){var parent=people.get(UUID.fromString(id));if(parent!=null)ids.addAll(ancestors(city,parent,people,depth-1));}
        return ids;
    }
    static void relationships(ServerLevel city,Map<UUID,NapoliCivicLife.Citizen> people,long day) {
        var states=new HashMap<>(city.getAttachedOrElse(LIFE,Map.of()));
        for(var c:people.values()) {
            var l=states.getOrDefault(c.id().toString(),fresh(c.id()));var relationships=new HashMap<>(l.relations());
            for(var other:people.values())if(!c.id().equals(other.id())&&
                    (!c.property().isEmpty()&&c.property().equals(other.property())||c.job()>=0&&c.job()==other.job())) {
                int previous=relationships.getOrDefault(other.id().toString(),50);
                boolean conflict=l.needs().mood()<25&&Math.floorMod(day+c.id().hashCode(),4)==0;
                relationships.put(other.id().toString(),clamp(previous+(conflict?-3:1)));
            }
            states.put(c.id().toString(),new Life(l.needs(),l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),l.food(),l.last(),l.personality(),Map.copyOf(relationships),l.memories(),l.water(),l.energy(),l.lastBirth()));
        }
        city.setAttached(LIFE,Map.copyOf(states));
    }
    static void encounter(ServerLevel city,UUID a,UUID b,long day){
        for(var pair:List.of(List.of(a,b),List.of(b,a))){
            var l=life(city,pair.getFirst());var relations=new HashMap<>(l.relations());String other=pair.getLast().toString();
            if(relations.getOrDefault("met:"+other,-1)>=(int)day)continue;
            relations.put("met:"+other,(int)day);relations.put(other,clamp(relations.getOrDefault(other,50)+2));
            put(city,pair.getFirst(),new Life(l.needs(),l.health(),l.reputation(),l.experience(),l.rank(),l.absences(),l.food(),l.last(),l.personality(),Map.copyOf(relations),l.memories(),l.water(),l.energy(),l.lastBirth()));
        }
    }
    static void update(ServerLevel city) {
        var census=NapoliCivicLife.census(city);if(census==null)return;
        long now=city.getGameTime(), time=NapoliBroadcast.time(city);
        var states=new HashMap<>(city.getAttachedOrElse(LIFE,Map.of()));
        for(var person:census.people()) {
            Life l=states.getOrDefault(person.id().toString(),fresh(person.id()));
            if(now-l.last()<CityLifeConfig.settings.needsIntervalTicks)continue;
            // Bounded elapsed steps; every resident is updated, without requesting any chunks.
            int elapsed=(int)Math.clamp((now-l.last())/CityLifeConfig.settings.needsIntervalTicks,1,12);
            Needs n=l.needs().change(-3*elapsed,-2*elapsed,-2*elapsed,-2*elapsed,-elapsed,-3*elapsed);
            var action=activity(city,person,l,time);
            CityRoutines.attendance(city,person,action==NapoliHouseholds.Activity.WORK);
            int food=l.food(),water=l.water(),energy=l.energy();
            if(food==0&&!person.parents().isEmpty()) {
                var parent=NapoliCivicLife.profile(city,UUID.fromString(person.parents()));
                if(parent!=null) {
                    var stock=states.getOrDefault(parent.id().toString(),fresh(parent.id()));
                    if(stock.food()>1) {food=1;states.put(parent.id().toString(),stock.withNeeds(stock.needs(),stock.food()-1,stock.last(),stock.water(),stock.energy()));}
                }
            }
            switch(action) {
                case EAT,COOK -> {
                    // Visible meals are completed by the kitchen state machine, never by a timer alone.
                    var entity=city.getEntity(person.id());
                    if(!(entity instanceof NapoliResident)||CityLivingHomes.find(person.property())==null)
                        if(food>0) { n=n.change(35,0,0,3,5,0);food--; }
                }
                case SLEEP -> n=n.change(0,35,0,0,0,0);
                case WASH -> { n=n.change(0,0,45,0,0,45);water++;energy++; }
                case MATCH,PLAY,REST -> n=n.change(0,0,0,20,15,0);
                case CARE -> l=l.career(Math.min(100,l.health()+8),l.reputation(),l.experience(),l.rank(),l.absences(),l.memories());
                default -> {}
            }
            if(!person.partner().isEmpty()) n=n.change(0,0,0,0,8,0);
            states.put(person.id().toString(),l.withNeeds(n,food,now,water,energy));
        }
        city.setAttached(LIFE,Map.copyOf(states));
    }
    static NapoliHouseholds.Activity activity(ServerLevel city,NapoliCivicLife.Citizen c,Life l,long time) {
        int age=c.age(NapoliBroadcast.day(city));
        boolean child=age<NapoliCivicLife.ADULT_AGE;
        if(l.health()<45)return NapoliHouseholds.Activity.CARE;
        if(CityEvents.celebrating(city)&&time<19000&&l.personality().equals("sportivo"))return NapoliHouseholds.Activity.CELEBRATE;
        if(CityEvents.danger(city))return NapoliHouseholds.Activity.REST;
        if((time>=16500||time<900)&&!CityProfessions.night(c.profession()))return NapoliHouseholds.Activity.SLEEP;
        if(child&&age>=3&&time>=2500&&time<8000&&Math.floorMod(NapoliBroadcast.day(city),7)<5)return NapoliHouseholds.Activity.SCHOOL;
        if(!child&&c.profession()!=null&&age<CityLifeConfig.settings.retirementAge
                &&CityProfessions.open(c.profession(),time,NapoliBroadcast.day(city)))return NapoliHouseholds.Activity.WORK;
        if(l.food()==0&&l.needs().hunger()<60&&!child)return NapoliHouseholds.Activity.SHOP;
        if(l.needs().hunger()<35||time>=1000&&time<1900||time>=10500&&time<11600)return NapoliHouseholds.Activity.EAT;
        if(l.needs().hygiene()<40||l.needs().bladder()<30||time>=14500&&time<15600)return NapoliHouseholds.Activity.WASH;
        if(child&&age<3)return NapoliHouseholds.Activity.REST;
        if(child)return time<10000?NapoliHouseholds.Activity.STUDY:NapoliHouseholds.Activity.PLAY;
        if(time>=9500&&time<10500&&l.food()<5)return NapoliHouseholds.Activity.SHOP;
        if(time>=10000&&time<12000&&c.woman())return NapoliHouseholds.Activity.COOK;
        if(time>=12000&&time<16000)return NapoliHouseholds.Activity.MATCH;
        return city.isRaining()?NapoliHouseholds.Activity.REST:NapoliHouseholds.Activity.SOCIAL;
    }
    static NapoliCivicLife.Citizen daily(ServerLevel city,NapoliCivicLife.Citizen c,long day,int wage) {
        Life l=life(city,c.id());
        boolean scheduled=c.profession()!=null&&CityProfessions.open(c.profession(),CityProfessions.start(c.profession())+1,day-1);
        boolean working=scheduled&&wage>0&&l.health()>=45&&l.needs().sleep()>10&&!CityPublicOrder.jailed(city,c.id());
        int performance=CityRoutines.performance(city,c.id(),day-1);
        int xp=l.experience()+(working&&performance>=3?1:0),absence=working?0:l.absences()+(scheduled?1:0);
        int rank=l.rank();
        var memories=l.memories();
        if(working&&performance>=3&&l.health()>=70&&l.needs().sleep()>=40&&xp>=(rank+1)*14&&l.reputation()>=45) {
            int max=c.profession()==NapoliCivicLife.Job.CARABINIERE?7:3;
            boolean vacancy=NapoliCivicLife.census(city).people().stream().filter(p->p.job()==c.job()&&life(city,p.id()).rank()>l.rank()).count()<2;
            if(rank<max&&vacancy) {rank++;memories=memory(l,"Promozione al giorno "+day);}
        }
        int hp=clamp(l.health()+(l.needs().hunger()<15||l.needs().hygiene()<10?-8:2));
        int job=c.job();
        if(absence>=4&&job>=0) {job=-1;rank=0;memories=memory(l,"Licenziamento per assenze ripetute");}
        int money=Math.clamp(c.balance()+(working?wage+l.rank()*4:0)-3,0,99999);
        int food=l.food();
        if(food<6&&money>=2) {int bought=CityEconomy.buyMeals(city,"mercato",6-food,money);money-=bought*2;food+=bought;}
        int utilities=(l.water()+l.energy())/8;
        if(utilities>0&&money>=utilities) money-=utilities;
        Life next=new Life(l.needs(),hp,clamp(l.reputation()+(working?1:-1)),xp,rank,absence,food,l.last(),l.personality(),l.relations(),memories,l.water()%8,l.energy()%8,l.lastBirth());
        put(city,c.id(),next);
        return c.withMoney(money,job);
    }
    static void tickEntity(NapoliResident npc,ServerLevel city) {
        var c=NapoliCivicLife.profile(city,npc.getUUID());
        if(c==null) { if(life(city,npc.getUUID()).health()==0)npc.discard();return; }
        double distance=city.players().stream().mapToDouble(p->p.distanceToSqr(npc)).min().orElse(Double.MAX_VALUE);
        int interval=distance<32*32?40:distance<80*80?160:600;
        if((npc.tickCount+npc.getId())%interval!=0)return;
        int age=c.age(NapoliBroadcast.day(city));npc.setCivicAge(age);
        if(npc.role().civic())npc.setCivicAppearance(c.profession()==NapoliCivicLife.Job.INFLUENCER?10:c.profession()==null?11:c.profession().category.ordinal());
        var l=life(city,c.id());var action=activity(city,c,l,NapoliBroadcast.time(city));npc.setActivity(action);
        if(distance>=128*128) {
            var census=NapoliCivicLife.census(city);
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream()
                    .map(p->p.id().equals(c.id())?new NapoliCivicLife.Citizen(p.id(),p.name(),p.woman(),p.birth(),p.balance(),p.job(),p.partner(),p.property(),p.parents(),false,p.childhoodDays()):p).toList()));
            npc.discard();return;
        }
        if(distance>=80*80) {npc.getNavigation().stop();return;}
        var house=CityLivingHomes.find(c.property());
        var visit = visitingHouse(city,c);
        if(visit!=null) {house=visit;action=NapoliBroadcast.time(city)<11600?NapoliHouseholds.Activity.EAT:NapoliHouseholds.Activity.MATCH;npc.setActivity(action);}
        BlockPos home=NapoliCivicLife.home(c);
        BlockPos target=home;
        if(action!=NapoliHouseholds.Activity.WORK&&action!=NapoliHouseholds.Activity.CARE&&action!=NapoliHouseholds.Activity.SLEEP
                &&action!=NapoliHouseholds.Activity.SCHOOL&&action!=NapoliHouseholds.Activity.SHOP&&action!=NapoliHouseholds.Activity.SOCIAL&&action!=NapoliHouseholds.Activity.CELEBRATE&&house!=null){
            if(CityArrivals.waitOutside(npc,city,house))return;
            if(DomesticMeals.act(city,npc,house,l.food()))return;
        }
        if(CityNeighborhood.tick(npc,city,c,action))return;
        if(action==NapoliHouseholds.Activity.WORK&&c.profession()!=null) target=c.profession().site;
        else if(action==NapoliHouseholds.Activity.CARE)target=CityProfessions.Category.HEALTH.site().offset(8,0,7);
        else if(action==NapoliHouseholds.Activity.SCHOOL)target=CityProfessions.Category.PUBLIC.site().offset(8,0,7);
        else if(action==NapoliHouseholds.Activity.SHOP)target=CityProfessions.Category.TRADE.site().offset(8,0,7);
        else if(action==NapoliHouseholds.Activity.CELEBRATE)target=house==null?home.south(5):house.entrance().south(2);
        else if(house!=null) {
            if(!HousingCapacity.canEnter(city,house.inside(),npc.getUUID())) { CityArrivals.waitOutside(npc,city,house);return; }
            var furniture=switch(action) {case COOK,EAT->CityLivingHomes.Furniture.STOVE;case WASH->CityLivingHomes.Furniture.SHOWER;
                case SLEEP->CityLivingHomes.Furniture.BED;case STUDY->CityLivingHomes.Furniture.DESK;default->CityLivingHomes.Furniture.SOFA;};
            var point=CityLivingHomes.use(house,furniture);
            if(action==NapoliHouseholds.Activity.SLEEP&&age<NapoliCivicLife.ADULT_AGE)point=CityLivingHomes.points(house).stream().filter(p->p.room()==CityLivingHomes.Room.CHILD_BEDROOM).findFirst().orElse(point);
            target=point.stand();
            if(action==NapoliHouseholds.Activity.SLEEP) {
                BlockPos bed=point.block().east(c.woman()?1:0);
                if(!npc.isSleeping()&&city.hasChunkAt(bed)&&city.getBlockState(bed).getBlock() instanceof BedBlock&&npc.blockPosition().distSqr(bed)<9)npc.startSleeping(bed);
            }
            if(action==NapoliHouseholds.Activity.MATCH) {
                var tv=CityLivingHomes.use(house,CityLivingHomes.Furniture.TV).block();
                if(NapoliBroadcast.channel(city,tv)==NapoliBroadcast.Channel.OFF)npc.setActivity(NapoliHouseholds.Activity.REST);
                NapoliHouseholds.paintTelevision(city,tv);
                npc.getLookControl().setLookAt(tv.getX()+.5,tv.getY()+.5,tv.getZ()+.5);
            }
        }
        if(action!=NapoliHouseholds.Activity.SLEEP&&npc.isSleeping())npc.stopSleeping();
        var escort=CityNeighborhood.schoolEscort(npc,city,c);
        if(escort!=null)target=escort;
        else if((action==NapoliHouseholds.Activity.WORK||action==NapoliHouseholds.Activity.SCHOOL)&&CityRoutines.commute(npc,city,target))return;
        if(action==NapoliHouseholds.Activity.SHOP&&target.distToCenterSqr(npc.position())<36)CityEconomy.shopResident(city,c);
        if(action==NapoliHouseholds.Activity.SOCIAL&&c.age(NapoliBroadcast.day(city))>=60)target=CityNeighborhood.cafe(npc.blockPosition());
        if((action==NapoliHouseholds.Activity.WORK||action==NapoliHouseholds.Activity.SCHOOL)&&target.distToCenterSqr(npc.position())>64)npc.setActivity(NapoliHouseholds.Activity.COMMUTE);
        target=CityRoutines.sheltered(city,npc,CityRoutines.localGoal(city,npc,target));
        if(!city.hasChunkAt(target))return;
        if(target.distToCenterSqr(npc.position())>2&&!npc.isSleeping()) {
            var path=npc.getNavigation().createPath(target,0);
            if(path!=null&&path.canReach())npc.getNavigation().moveTo(path,city.isRaining()?.72:.85);
            else npc.getNavigation().stop(); // Never teleport a visible inhabitant through a wall.
        } else if(action==NapoliHouseholds.Activity.COOK||action==NapoliHouseholds.Activity.WASH)npc.swingForAttack(InteractionHand.MAIN_HAND);
        var tool=action==NapoliHouseholds.Activity.WORK&&c.profession()!=null?c.profession().category.tool
                :action==NapoliHouseholds.Activity.EAT?Items.BREAD:action==NapoliHouseholds.Activity.STUDY?Items.BOOK:Items.AIR;
        if(npc.getMainHandItem().getItem()!=tool)npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(tool));
    }
    static void describe(ServerPlayer p,NapoliCivicLife.Citizen c) {
        if (p.level().getEntity(c.id()) instanceof NapoliResident npc) NeighbourMemory.greet(p, npc);
        var l=life(p.level(),c.id());var n=l.needs();
        p.sendSystemMessage(Component.literal("Fame "+n.hunger()+" · Riposo "+n.sleep()+" · Igiene "+n.hygiene()+" · Divertimento "+n.fun()+" · Socialità "+n.social()+" · Umore "+n.mood()+" · Salute "+l.health()));
        p.sendSystemMessage(Component.literal("Personalità: "+l.personality()+" · Reputazione: "+l.reputation()+" · Scorte: "+l.food()+" pasti"));
        if(c.profession()!=null)p.sendSystemMessage(Component.literal(CityProfessions.rank(c.profession(),l.rank())+" · esperienza "+l.experience()+" giorni"));
        if(!l.memories().isEmpty())p.sendSystemMessage(Component.literal(l.memories().getLast()));
        var entity=p.level().getEntity(c.id());var observed=entity instanceof NapoliResident npc?npc.activity():activity(p.level(),c,l,NapoliBroadcast.time(p.level()));
        p.sendSystemMessage(Component.translatable("napuli.family."+observed.name().toLowerCase(java.util.Locale.ROOT)));
        CitySocial.open(p,c);
    }
    static BlockPos location(ServerLevel city,NapoliCivicLife.Citizen c) {
        var visit=visitingHouse(city,c);if(visit!=null)return visit.inside();
        var action=activity(city,c,life(city,c.id()),NapoliBroadcast.time(city));
        if(action==NapoliHouseholds.Activity.WORK&&c.profession()!=null)return c.profession().site;
        if(action==NapoliHouseholds.Activity.CARE)return CityProfessions.Category.HEALTH.site().offset(8,0,7);
        if(action==NapoliHouseholds.Activity.SCHOOL)return CityProfessions.Category.PUBLIC.site().offset(8,0,7);
        if(action==NapoliHouseholds.Activity.SHOP)return CityProfessions.Category.TRADE.site().offset(8,0,7);
        return NapoliCivicLife.home(c);
    }
    private static NapoliMergellina.House visitingHouse(ServerLevel city,NapoliCivicLife.Citizen c) {
        long time=NapoliBroadcast.time(city);
        if(time<10500||time>=16000||life(city,c.id()).health()<45||CityPublicOrder.jailed(city,c.id())
                ||c.profession()!=null&&CityProfessions.open(c.profession(),time,NapoliBroadcast.day(city)))return null;
        return CityLivingHomes.find(HouseGuests.visitingHome(city,c.id()));
    }
    private CitySimulation() {}
}
