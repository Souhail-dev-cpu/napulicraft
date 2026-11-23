package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** Work reserves inputs before animation and settles each output once, including across chunk reloads. */
final class CityOccupations {
    record Task(String kind,int stage,int progress,int cargo,String recipient,long last) {
        static final Codec<Task> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("kind").forGetter(Task::kind),Codec.INT.fieldOf("stage").forGetter(Task::stage),Codec.INT.fieldOf("progress").forGetter(Task::progress),Codec.INT.fieldOf("cargo").forGetter(Task::cargo),Codec.STRING.fieldOf("recipient").forGetter(Task::recipient),Codec.LONG.fieldOf("last").forGetter(Task::last)).apply(i,Task::new));
    }
    record Litter(BlockPos position,int units,long day){static final Codec<Litter> CODEC=RecordCodecBuilder.create(i->i.group(BlockPos.CODEC.fieldOf("position").forGetter(Litter::position),Codec.INT.fieldOf("units").forGetter(Litter::units),Codec.LONG.fieldOf("day").forGetter(Litter::day)).apply(i,Litter::new));}
    record Parcel(UUID recipient,BlockPos position,int food,long delivered,boolean collected){static final Codec<Parcel> CODEC=RecordCodecBuilder.create(i->i.group(net.minecraft.core.UUIDUtil.CODEC.fieldOf("recipient").forGetter(Parcel::recipient),BlockPos.CODEC.fieldOf("position").forGetter(Parcel::position),Codec.INT.fieldOf("food").forGetter(Parcel::food),Codec.LONG.fieldOf("delivered").forGetter(Parcel::delivered),Codec.BOOL.fieldOf("collected").forGetter(Parcel::collected)).apply(i,Parcel::new));}
    static final AttachmentType<Map<String,Task>> TASKS=AttachmentRegistry.<Map<String,Task>>builder().persistent(Codec.unboundedMap(Codec.STRING,Task.CODEC)).buildAndRegister(Napuli.id("occupation_tasks"));
    static final AttachmentType<Map<String,Integer>> OUTPUTS=AttachmentRegistry.<Map<String,Integer>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.INT)).buildAndRegister(Napuli.id("occupation_outputs"));
    static final AttachmentType<Map<String,Long>> RESTOCKED=AttachmentRegistry.<Map<String,Long>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.LONG)).buildAndRegister(Napuli.id("service_restock_days"));
    static int outputs(ServerLevel city,UUID id){return city.getAttachedOrElse(OUTPUTS,Map.of()).getOrDefault(id.toString(),0);}
    private static void output(ServerLevel city,UUID id){var all=new HashMap<>(city.getAttachedOrElse(OUTPUTS,Map.of()));all.put(id.toString(),Math.min(1000000,all.getOrDefault(id.toString(),0)+1));city.setAttached(OUTPUTS,Map.copyOf(all));}
    static final AttachmentType<Map<String,Integer>> SUPPLIES=AttachmentRegistry.<Map<String,Integer>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.INT)).buildAndRegister(Napuli.id("service_supplies"));
    static final AttachmentType<Map<String,Litter>> LITTER=AttachmentRegistry.<Map<String,Litter>>builder().persistent(Codec.unboundedMap(Codec.STRING,Litter.CODEC)).buildAndRegister(Napuli.id("street_litter"));
    static final AttachmentType<Map<String,Parcel>> PARCELS=AttachmentRegistry.<Map<String,Parcel>>builder().persistent(Codec.unboundedMap(Codec.STRING,Parcel.CODEC)).buildAndRegister(Napuli.id("doorstep_parcels"));
    static Task task(ServerLevel city,UUID id){return city.getAttachedOrElse(TASKS,Map.of()).get(id.toString());}
    static void put(ServerLevel city,UUID id,Task task){var all=new HashMap<>(city.getAttachedOrElse(TASKS,Map.of()));all.put(id.toString(),task);city.setAttached(TASKS,Map.copyOf(all));}
    static int supply(ServerLevel city,String key){return city.getAttachedOrElse(SUPPLIES,Map.of()).getOrDefault(key,key.equals("medicine")||key.equals("parts")?24:key.equals("coffee")?48:0);}
    static void supply(ServerLevel city,String key,int value){var all=new HashMap<>(city.getAttachedOrElse(SUPPLIES,Map.of()));all.put(key,Math.clamp(value,0,key.equals("returns")?4096:256));city.setAttached(SUPPLIES,Map.copyOf(all));}
    static String kind(NapoliCivicLife.Citizen c){if(c.profession()==null)return "";return switch(c.profession()){
        case BAKER,PASTRY_CHEF,PIZZA_MAKER,COOK,PASTA_MAKER -> "baker";case BARISTA,WAITER -> "barista";case CLEANER -> "cleaner";case COURIER,POSTMAN -> "courier";case DOCTOR,NURSE,NIGHT_DOCTOR,NIGHT_NURSE -> "doctor";case MECHANIC,PLUMBER,CARETAKER -> "repair";default -> "";};}
    static BlockPos workplace(NapoliCivicLife.Citizen c){if(DistrictLife.resident(c))return DistrictLife.work(c);return kind(c).isEmpty()?c.profession().site:c.profession().category.site().offset(8,0,7);}
    static BlockPos station(String kind){return switch(kind){case "baker"->CityProfessions.Category.FOOD.site().offset(3,0,4);case "barista"->CityProfessions.Category.FOOD.site().offset(10,0,4);case "cleaner"->CityProfessions.Category.SERVICES.site().offset(3,0,4);case "courier"->CityEconomy.DEPOT.south();default->CityProfessions.Category.HEALTH.site().offset(8,0,7);};}
    static void fixtures(ServerLevel city){
        for(var category:List.of(CityProfessions.Category.FOOD,CityProfessions.Category.SERVICES,CityProfessions.Category.HEALTH)){
            var pos=category.site().offset(3,0,3);if(!CityProperty.playerPlaced(city,pos)&&city.getBlockState(pos).is(Blocks.CRAFTING_TABLE))city.setBlock(pos,(category==CityProfessions.Category.FOOD?Blocks.SMOKER:Blocks.BARREL).defaultBlockState(),2);
        }
    }
    static String description(ServerLevel city,UUID id){var t=task(city,id);if(t==null)return "";return switch(t.kind()){case "baker"->t.cargo()>0?"Sto lavorando un impasto già prelevato dalle scorte.":"Aspetto farina e un forno libero.";case "barista"->"Servo i clienti al bancone e riordino le tazze.";case "cleaner"->t.cargo()>0?"Porto i rifiuti raccolti al contenitore.":"Sto pulendo le strade del quartiere.";case "courier"->t.cargo()>0?"Ho una consegna da portare a un vicino.":"Ritiro i pacchi al deposito.";case "doctor"->"Visito i pazienti presenti in ambulatorio.";default->"";};}
    static String bakery(ServerLevel city,UUID id){var c=NapoliCivicLife.profile(city,id);return DistrictLife.resident(c)?DistrictShops.key(c):"forno";}
    static void returnFlour(ServerLevel city,UUID id,int amount){var shop=bakery(city,id);var s=CityEconomy.stock(city,shop);CityEconomy.put(city,shop,new CityEconomy.Stock(s.grain(),s.flour()+amount,s.bread(),s.processed(),s.delivered()));}
    static boolean reserveDough(ServerLevel city,UUID id){
        var old=task(city,id);if(old!=null&&old.cargo()>0)return true;var shop=bakery(city,id);var s=CityEconomy.stock(city,shop);if(shop.equals("forno")&&CityPersonalGoals.ovenBroken(city)||s.flour()+s.grain()<1)return false;
        CityEconomy.put(city,shop,new CityEconomy.Stock(s.grain()-(s.flour()>0?0:1),s.flour()-(s.flour()>0?1:0),s.bread(),s.processed(),s.delivered()));put(city,id,new Task("baker",1,0,1,"",city.getGameTime()));return true;
    }
    static boolean finishBread(ServerLevel city,UUID id){var t=task(city,id);if(t==null||!t.kind().equals("baker")||t.cargo()!=1||t.stage()!=2)return false;var shop=bakery(city,id);var s=CityEconomy.stock(city,shop);if(s.bread()>=192||shop.equals("forno")&&CityPersonalGoals.ovenBroken(city))return false;CityEconomy.put(city,shop,new CityEconomy.Stock(s.grain(),s.flour(),s.bread()+1,s.processed(),s.delivered()));put(city,id,new Task("baker",0,0,0,"",city.getGameTime()));output(city,id);return true;}
    static void litter(ServerLevel city,BlockPos position,int amount){var all=new HashMap<>(city.getAttachedOrElse(LITTER,Map.of()));String key=position.asLong()+"";var previous=all.get(key);all.put(key,new Litter(position,Math.clamp((previous==null?0:previous.units())+amount,0,6),NapoliBroadcast.day(city)));all.values().removeIf(l->l.units()<=0);while(all.size()>96)all.remove(all.keySet().iterator().next());city.setAttached(LITTER,Map.copyOf(all));}
    static boolean collectParcel(ServerLevel city,String id){var all=new HashMap<>(city.getAttachedOrElse(PARCELS,Map.of()));var p=all.get(id);if(p==null||p.collected())return false;var c=NapoliCivicLife.profile(city,p.recipient());if(c==null||!CityJourneys.at(city,c,p.position(),12))return false;
        var life=CitySimulation.life(city,c.id());CitySimulation.put(city,c.id(),life.withNeeds(life.needs(),Math.min(24,life.food()+p.food()),life.last(),life.water(),life.energy()));all.put(id,new Parcel(p.recipient(),p.position(),p.food(),p.delivered(),true));city.setAttached(PARCELS,Map.copyOf(all));return true;}
    static BlockPos target(ServerLevel city,NapoliCivicLife.Citizen c){var t=task(city,c.id());String kind=kind(c);
        if(DistrictLife.resident(c)&&!kind.equals("courier")&&!kind.equals("cleaner")&&!kind.equals("repair"))return DistrictLife.work(c);
        if(kind.equals("repair")){if(t!=null&&t.kind().equals(kind)&&t.cargo()>0&&!t.recipient().isEmpty()){var issue=city.getAttachedOrElse(CityTraces.ISSUES,Map.of()).get(t.recipient());if(issue!=null){var h=CityLivingHomes.find(issue.home());if(h!=null)return h.entrance().south();}}return CityTraces.repairTarget(city);}
        if(t!=null&&t.kind().equals(kind)&&t.cargo()>0&&kind.equals("courier")&&!t.recipient().isEmpty()){var recipient=NapoliCivicLife.profile(city,UUID.fromString(t.recipient()));var home=recipient==null?null:CityLivingHomes.find(recipient.property());return home==null?station(kind):home.entrance().south();}
        if(kind.equals("cleaner")&&(t==null||t.cargo()==0))return city.getAttachedOrElse(LITTER,Map.of()).values().stream().filter(l->l.units()>0).min(Comparator.comparingDouble(l->l.position().distSqr(workplace(c)))).map(Litter::position).orElse(station(kind));
        return DistrictLife.resident(c)?workplace(c):station(kind);
    }
    static boolean tick(NapoliResident npc,ServerLevel city,NapoliCivicLife.Citizen c){
        String kind=kind(c);if(kind.isEmpty())return false;var t=normalize(city,c,kind);long now=city.getGameTime();
        var goal=target(city,c);npc.setActivity(NapoliHouseholds.Activity.WORK);CityMovement.stand(npc);
        if(goal.distToCenterSqr(npc.position())>3){npc.setActivity(NapoliHouseholds.Activity.COMMUTE);CityDomestic.move(npc,CityRoutines.localGoal(city,npc,goal));return true;}
        if(!CityMovement.acquire(city,goal,npc.getUUID(),240)){npc.setActivity(NapoliHouseholds.Activity.WAIT);CityDomestic.move(npc,CityMovement.waitingSpot(city,goal,npc.getUUID()));return true;}
        npc.getNavigation().stop();npc.animate(switch(kind){case "baker"->t.stage()<2?CityGestures.Gesture.CHOP:CityGestures.Gesture.STIR;case "barista"->CityGestures.Gesture.WASH;case "cleaner"->CityGestures.Gesture.SWEEP;case "courier"->CityGestures.Gesture.PACK;default->CityGestures.Gesture.EXAMINE;},50);
        npc.setDropChance(EquipmentSlot.MAINHAND,0);npc.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(kind.equals("cleaner")?Items.BRUSH:kind.equals("courier")?Items.CHEST:kind.equals("doctor")?Items.GLASS_BOTTLE:Items.BOWL));
        if(now-t.last()<200&&t.cargo()==0){CityMovement.release(city,npc.getUUID());return true;}
        if(kind.equals("baker")){
            if(t.stage()==0){if(!reserveDough(city,c.id())){npc.setActivity(NapoliHouseholds.Activity.WAIT);CityMovement.release(city,npc.getUUID());}return true;}
            if(t.progress()<120){put(city,c.id(),new Task(kind,t.stage(),t.progress()+40,t.cargo(),t.recipient(),t.last()));return true;}
            if(t.stage()==1)put(city,c.id(),new Task(kind,2,0,t.cargo(),"",t.last()));else finishBread(city,c.id());
        }else if(kind.equals("cleaner")){
            if(t.cargo()>0){supply(city,"recycled",supply(city,"recycled")+t.cargo());put(city,c.id(),new Task(kind,0,0,0,"",now));}
            else{var dirt=city.getAttachedOrElse(LITTER,Map.of()).get(goal.asLong()+"");if(dirt!=null&&dirt.units()>0){litter(city,goal,-1);put(city,c.id(),new Task(kind,1,0,1,"",now));}}
        }else if(kind.equals("courier")){
            if(t.cargo()>0)deliver(city,c.id(),t);
            else{var census=NapoliCivicLife.census(city);var recipient=census.people().stream().filter(p->!p.id().equals(c.id())&&!p.property().isEmpty()&&(!DistrictLife.resident(c)||NapoliCivicLife.home(p).distSqr(DistrictLife.work(c))<400*400)&&CitySimulation.life(city,p.id()).food()<4&&city.getAttachedOrElse(PARCELS,Map.of()).values().stream().noneMatch(pcl->pcl.recipient().equals(p.id())&&!pcl.collected())).findFirst().orElse(null);
                if(recipient!=null&&CityEconomy.reserveBread(city,bakery(city,c.id()),1))put(city,c.id(),new Task(kind,1,0,1,recipient.id().toString(),now));}
        }else if(kind.equals("barista"))serveCoffee(city,npc,c,now);
        else if(kind.equals("repair"))repair(city,c,t,now);else treat(city,npc,c,now);
        CityMovement.release(city,npc.getUUID());return true;
    }
    static boolean deliver(ServerLevel city,UUID courier,Task t){
        if(!t.kind().equals("courier")||t.cargo()<1)return false;String key=courier+"/"+t.last();var all=new HashMap<>(city.getAttachedOrElse(PARCELS,Map.of()));if(!all.containsKey(key))all.put(key,new Parcel(UUID.fromString(t.recipient()),target(city,NapoliCivicLife.profile(city,courier)),t.cargo(),city.getGameTime(),false));
        while(all.size()>128){var old=all.entrySet().stream().filter(e->e.getValue().collected()).min(Comparator.comparingLong(e->e.getValue().delivered()));if(old.isEmpty())break;all.remove(old.get().getKey());}city.setAttached(PARCELS,Map.copyOf(all));put(city,courier,new Task("courier",0,0,0,"",city.getGameTime()));return true;
    }
    private static void serveCoffee(ServerLevel city,NapoliResident npc,NapoliCivicLife.Citizen worker,long now){
        if(supply(city,"coffee")<1)return;var customer=city.getEntitiesOfClass(NapoliResident.class,npc.getBoundingBox().inflate(10),n->n!=npc&&n.activity()==NapoliHouseholds.Activity.SOCIAL).stream().map(n->NapoliCivicLife.profile(city,n.getUUID())).filter(Objects::nonNull).filter(c->c.balance()>0&&CitySimulation.life(city,c.id()).needs().fun()<80).findFirst().orElse(null);if(customer==null)return;
        supply(city,"coffee",supply(city,"coffee")-1);money(city,customer.id(),-1);money(city,worker.id(),1);var l=CitySimulation.life(city,customer.id());CitySimulation.put(city,customer.id(),l.withNeeds(l.needs().change(0,0,0,15,10,0),l.food(),l.last(),l.water(),l.energy()));
        if(city.getEntity(customer.id()) instanceof NapoliResident guest){guest.setDropChance(EquipmentSlot.MAINHAND,0);guest.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Napuli.CAFE));guest.animate(CityGestures.Gesture.EAT,80);}put(city,worker.id(),new Task("barista",0,0,0,"",now));
    }
    static boolean treat(ServerLevel city,NapoliResident npc,NapoliCivicLife.Citizen worker,long now){
        var patient=CityServices.patient(city,worker.id());if(patient==null)return false;boolean completed=CityServices.examine(city,worker,patient);if(completed)put(city,worker.id(),new Task("doctor",0,0,0,"",now));return completed;
    }
    static void money(ServerLevel city,UUID id,int amount){var census=NapoliCivicLife.census(city);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(c->c.id().equals(id)?c.withMoney(Math.clamp(c.balance()+amount,0,99999),c.job()):c).toList()));}
    static void repair(ServerLevel city,NapoliCivicLife.Citizen c,Task t,long now){var issue=CityTraces.issueNear(city,target(city,c));if(issue==null)return;
        if(t.cargo()==0){if(supply(city,"parts")==0)return;supply(city,"parts",supply(city,"parts")-1);put(city,c.id(),new Task("repair",1,0,1,issue.position().asLong()+"",now));}
        else if(t.progress()<160)put(city,c.id(),new Task("repair",1,t.progress()+40,1,t.recipient(),t.last()));
        else{CityTraces.repair(city,BlockPos.of(Long.parseLong(t.recipient())));put(city,c.id(),new Task("repair",0,0,0,"",now));}
    }
    static void virtualTick(ServerLevel city,NapoliCivicLife.Citizen c){
        if(c.profession()==null||CityJourneys.get(city,c.id())==null||!CityHabits.working(c,NapoliBroadcast.time(city),NapoliBroadcast.day(city))&&!delivering(city,c)||CitySimulation.life(city,c.id()).health()<45||CityPublicOrder.jailed(city,c.id()))return;
        String kind=kind(c);if(!Set.of("cleaner","courier","repair","baker").contains(kind))return;if(kind.equals("baker")&&task(city,c.id())==null)return;var t=normalize(city,c,kind);long now=city.getGameTime();if(now-t.last()<200||!CityJourneys.at(city,c,target(city,c),4))return;
        if(kind.equals("baker")){if(t.cargo()>0){if(t.progress()<120)put(city,c.id(),new Task(kind,t.stage(),t.progress()+40,t.cargo(),t.recipient(),t.last()));else if(t.stage()==1)put(city,c.id(),new Task(kind,2,0,1,"",t.last()));else finishBread(city,c.id());}return;}
        if(kind.equals("repair")){repair(city,c,t,now);return;}
        if(kind.equals("cleaner")){if(t.cargo()>0){supply(city,"recycled",supply(city,"recycled")+t.cargo());put(city,c.id(),new Task(kind,0,0,0,"",now));}else{var goal=target(city,c);var dirt=city.getAttachedOrElse(LITTER,Map.of()).get(goal.asLong()+"");if(dirt!=null&&dirt.units()>0){litter(city,goal,-1);put(city,c.id(),new Task(kind,1,0,1,"",now));}}}
        else if(t.cargo()>0)deliver(city,c.id(),t);
        else{var recipient=NapoliCivicLife.census(city).people().stream().filter(p->!p.id().equals(c.id())&&!p.property().isEmpty()&&CitySimulation.life(city,p.id()).food()<4&&city.getAttachedOrElse(PARCELS,Map.of()).values().stream().noneMatch(pcl->pcl.recipient().equals(p.id())&&!pcl.collected())).findFirst().orElse(null);if(recipient!=null&&CityEconomy.reserveBread(city,bakery(city,c.id()),1))put(city,c.id(),new Task(kind,1,0,1,recipient.id().toString(),now));}
    }
    static Task normalize(ServerLevel city,NapoliCivicLife.Citizen c,String kind){var old=task(city,c.id());if(old!=null&&old.kind().equals(kind))return old;if(old!=null&&old.cargo()>0){switch(old.kind()){case "baker"->returnFlour(city,c.id(),old.cargo());case "courier"->supply(city,"returns",supply(city,"returns")+old.cargo());case "repair"->supply(city,"parts",supply(city,"parts")+old.cargo());case "cleaner"->supply(city,"recycled",supply(city,"recycled")+old.cargo());default->{}}}var fresh=new Task(kind,0,0,0,"",-1000);put(city,c.id(),fresh);return fresh;}
    static boolean delivering(ServerLevel city,NapoliCivicLife.Citizen c){var t=task(city,c.id());var l=CitySimulation.life(city,c.id());return kind(c).equals("courier")&&t!=null&&t.kind().equals("courier")&&t.cargo()>0&&l.health()>=45&&l.needs().sleep()>15&&l.needs().hunger()>15;}
    static void restock(ServerLevel city){long day=NapoliBroadcast.day(city);if(NapoliBroadcast.time(city)<1500)return;var delivered=new HashMap<>(city.getAttachedOrElse(RESTOCKED,Map.of()));
        for(String key:List.of("medicine","coffee","parts")){if(supply(city,key)>=12||delivered.getOrDefault(key,-1L)>=day)continue;String business=key.equals("medicine")?"doctor":key.equals("coffee")?"barista":"repair";var payer=NapoliCivicLife.census(city).people().stream().filter(c->kind(c).equals(business)&&c.balance()>=8).findFirst().orElse(null);if(payer!=null){money(city,payer.id(),-8);supply(city,key,supply(city,key)+16);delivered.put(key,day);}}
        city.setAttached(RESTOCKED,Map.copyOf(delivered));
    }
    static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%200!=0)return;var city=server.getLevel(NapoliMap.LEVEL);if(city==null||NapoliCivicLife.census(city)==null)return;restock(city);for(var id:List.copyOf(city.getAttachedOrElse(PARCELS,Map.of()).keySet()))collectParcel(city,id);int returns=supply(city,"returns");if(returns>0){var stock=CityEconomy.stock(city,"forno");int received=Math.min(returns,Math.max(0,192-stock.bread()));if(received>0){CityEconomy.put(city,"forno",new CityEconomy.Stock(stock.grain(),stock.flour(),stock.bread()+received,stock.processed(),stock.delivered()));supply(city,"returns",returns-received);}}});}
    private CityOccupations(){}
}
