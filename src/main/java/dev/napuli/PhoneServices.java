package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import static dev.napuli.CityText.t;

/** Local phone services: requests survive a restart; their temporary actors never duplicate in saves. */
public final class PhoneServices {
    public static final String PIZZA_NUMBER = "0815550101", TAXI_NUMBER = "0815550202";
    static final int PIZZA_PRICE = 8, PIZZA_DELAY = 334; // 20 clock minutes at 1,000 ticks/hour.
    static final String COURIER = "napuli_pizza_delivery", RESPONDER = "napuli_phone_police";
    private static final String OWNER = "napuli_service_owner:";
    record Pizza(UUID id, long due) {
        static final Codec<Pizza> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Pizza::id), Codec.LONG.fieldOf("due").forGetter(Pizza::due)).apply(i, Pizza::new));
    }
    record Taxi(UUID id, BlockPos pickup, BlockPos goal, String label, int fare, long expires, boolean paid) {
        static final Codec<Taxi> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Taxi::id), BlockPos.CODEC.fieldOf("pickup").forGetter(Taxi::pickup),
                BlockPos.CODEC.fieldOf("goal").forGetter(Taxi::goal), Codec.STRING.fieldOf("label").forGetter(Taxi::label),
                Codec.INT.fieldOf("fare").forGetter(Taxi::fare), Codec.LONG.fieldOf("expires").forGetter(Taxi::expires),
                Codec.BOOL.fieldOf("paid").forGetter(Taxi::paid)).apply(i, Taxi::new));
    }
    static final AttachmentType<Pizza> PIZZA = AttachmentRegistry.<Pizza>builder().persistent(Pizza.CODEC).copyOnDeath().buildAndRegister(Napuli.id("phone_pizza_order"));
    static final AttachmentType<Taxi> TAXI = AttachmentRegistry.<Taxi>builder().persistent(Taxi.CODEC).copyOnDeath().buildAndRegister(Napuli.id("phone_taxi_booking"));
    private static final AttachmentType<Long> POLICE_UNTIL = AttachmentRegistry.<Long>builder().persistent(Codec.LONG).copyOnDeath().buildAndRegister(Napuli.id("phone_police_cooldown"));
    private static final AttachmentType<Boolean> ASKED = AttachmentRegistry.create(Napuli.id("pizza_payment_requested"));
    private static final AttachmentType<Long> NEXT_SPAWN = AttachmentRegistry.create(Napuli.id("phone_service_spawn_retry"));
    private static final AttachmentType<Long> OFFICER_UNTIL = AttachmentRegistry.create(Napuli.id("phone_officer_until"));

    static boolean dial(ServerPlayer p, String input) {
        String raw = input.replaceAll("[\\s().-]", "");
        String number = PhoneNumbers.normalize(input);
        if (raw.equals("112") || raw.equals("+39112") || raw.equals("0039112")) { police(p); return true; }
        if (number.equals(PhoneNumbers.normalize(PIZZA_NUMBER))) { orderPizza(p); return true; }
        if (number.equals(PhoneNumbers.normalize(TAXI_NUMBER))) { Phones.feedback(p,t("phone.service.taxi_choose")); return true; }
        return false;
    }
    private static boolean eligible(ServerPlayer p) {
        if (!Phones.available(p)) { Phones.feedback(p,t("phone.unavailable")); return false; }
        if (!CityCompanion.inCity(p) || Carabinieri.isJailed(p)) { Phones.feedback(p,t("phone.service.city_only")); return false; }
        return true;
    }
    static boolean orderPizza(ServerPlayer p) {
        if (!eligible(p)) return false;
        var old = p.getAttached(PIZZA);
        if (old != null) { Phones.feedback(p,t("phone.service.pizza_pending",Math.max(0,(old.due()-p.level().getOverworldClockTime())*60/1000))); return false; }
        p.setAttached(PIZZA,new Pizza(UUID.randomUUID(),p.level().getOverworldClockTime()+PIZZA_DELAY));
        Phones.feedback(p,t("phone.service.pizza_ordered",PIZZA_PRICE)); return true;
    }
    static boolean police(ServerPlayer p) {
        if (!eligible(p)) return false;
        long now=p.level().getGameTime();
        if (now<p.getAttachedOrElse(POLICE_UNTIL,0L)) { Phones.feedback(p,t("phone.service.police_pending")); return false; }
        BlockPos at=spawnNear(p,8,14);
        if(at==null){Phones.feedback(p,t("phone.service.no_access"));return false;}
        var officer=NapoliResident.spawn(p.level(),NapoliResident.Role.CARABINIERE,at,0);
        officer.addTag(RESPONDER);officer.addTag(OWNER+p.getUUID());officer.setAttached(OFFICER_UNTIL,now+1200);
        p.setAttached(POLICE_UNTIL,now+1200);Phones.feedback(p,t("phone.service.police_sent"));return true;
    }
    static int taxiFare(BlockPos from,BlockPos to){return Math.clamp(5+(int)Math.ceil(Math.sqrt(from.distSqr(to))/80),6,100);}
    static boolean bookTaxi(ServerPlayer p,String destination){
        if(!eligible(p))return false;
        if(p.hasAttached(TAXI)){Phones.feedback(p,t("phone.service.taxi_pending"));return false;}
        var place=CityCompanion.destinations(p).stream().filter(d->d.id().equals(destination)&&!d.id().startsWith("banchina/")&&d.position().getY()>=60).findFirst().orElse(null);
        if(place==null){Phones.feedback(p,t("phone.service.taxi_choose"));return false;}
        if(place.position().distToCenterSqr(p.position())<12*12){Phones.feedback(p,t("phone.service.taxi_near"));return false;}
        BlockPos pickup=streetStop(p.level(),p.blockPosition());
        if(pickup==null){Phones.feedback(p,t("phone.service.no_access"));return false;}
        int fare=taxiFare(pickup,place.position());
        if(NapoliStreet.cash(p)<fare){Phones.feedback(p,t("phone.service.need_money",fare));return false;}
        p.setAttached(TAXI,new Taxi(UUID.randomUUID(),pickup,place.position(),place.name().getString(),fare,p.level().getGameTime()+2400,false));
        Phones.feedback(p,t("phone.service.taxi_booked",place.name(),fare,pickup.toShortString()));return true;
    }
    static void cancel(ServerPlayer p,String service){
        if(service.equals("pizza")){p.removeAttached(PIZZA);Phones.feedback(p,t("phone.service.cancelled"));}
        if(service.equals("taxi")){
            var ride=p.getAttached(TAXI);if(ride==null)return;
            if(p.getVehicle() instanceof CityVehicle v&&ride.id().equals(v.taxiId()))p.stopRiding();
            p.removeAttached(TAXI);Phones.feedback(p,t("phone.service.cancelled"));
        }
    }
    private static UUID owner(NapoliResident npc){
        for(String tag:npc.entityTags())if(tag.startsWith(OWNER))try{return UUID.fromString(tag.substring(OWNER.length()));}catch(IllegalArgumentException ignored){}
        return null;
    }
    static boolean temporary(NapoliResident npc){return npc.entityTags().contains(COURIER)||npc.entityTags().contains(RESPONDER);}
    static boolean tickActor(NapoliResident npc,ServerLevel city){
        if(!temporary(npc))return false;
        UUID owner=owner(npc);var p=owner==null?null:city.getServer().getPlayerList().getPlayer(owner);
        if(p==null||p.level()!=city||!p.isAlive()){npc.discard();return true;}
        if(npc.entityTags().contains(RESPONDER)){
            if(city.getGameTime()>=npc.getAttachedOrElse(OFFICER_UNTIL,0L)){npc.discard();return true;}
            if(Carabinieri.chasing(city,npc))return false;
            if(CityPublicOrder.tickEntity(npc,city))return true;
            if(npc.distanceToSqr(p)>16)CityDomestic.move(npc,CityRoutines.localGoal(city,npc,p.blockPosition()));
            else {npc.getNavigation().stop();npc.getLookControl().setLookAt(p,30,30);}
            return true;
        }
        var order=p.getAttached(PIZZA);
        if(order==null||!order.id().equals(npc.getUUID())){npc.discard();return true;}
        if(npc.distanceToSqr(p)>9)CityDomestic.move(npc,CityRoutines.localGoal(city,npc,p.blockPosition()));
        else{
            npc.getNavigation().stop();npc.getLookControl().setLookAt(p,30,30);
            if(city.getOverworldClockTime()>=order.due()&&!npc.getAttachedOrElse(ASKED,false)){
                npc.setAttached(ASKED,true);NpcSpeech.say(p,npc,t("phone.service.pizza_pay",PIZZA_PRICE));Phones.feedback(p,t("phone.service.pizza_arrived",PIZZA_PRICE));
            }
        }
        return true;
    }
    static boolean interact(NapoliResident npc,Player player){
        if(!npc.entityTags().contains(COURIER))return false;
        if(player instanceof ServerPlayer p)payPizza(p,npc);
        return true;
    }
    static boolean payPizza(ServerPlayer p,NapoliResident npc){
        var order=p.getAttached(PIZZA);
        if(order==null||!p.getUUID().equals(owner(npc))||!order.id().equals(npc.getUUID())||p.level()!=npc.level()
                ||!p.isAlive()||p.isSpectator()||p.distanceToSqr(npc)>16||p.level().getOverworldClockTime()<order.due()||!npc.hasLineOfSight(p))return false;
        if(NapoliStreet.cash(p)<PIZZA_PRICE){Phones.feedback(p,t("phone.service.need_money",PIZZA_PRICE));return false;}
        var inventory=new ArrayList<ItemStack>();for(int i=0;i<36;i++)inventory.add(p.getInventory().getItem(i).copy());
        if(!PlayerInventoryPlan.add(inventory,new ItemStack(Napuli.PIZZA))){Phones.feedback(p,t("phone.service.inventory_full"));return false;}
        p.removeAttached(PIZZA);NapoliStreet.credit(p,-PIZZA_PRICE,"shop","pizza_delivery");WorkEconomy.revenue(p.level(),PIZZA_PRICE);
        for(int i=0;i<36;i++)p.getInventory().setItem(i,inventory.get(i));p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();
        npc.discard();Phones.feedback(p,t("phone.service.pizza_delivered",PIZZA_PRICE));return true;
    }
    private static BlockPos spawnNear(ServerPlayer p,int min,int max){
        for(int attempt=0;attempt<4;attempt++){
            var at=Spawner.groundNear(p.level(),p,min,max);if(at==null)continue;
            var route=PlayerNavigator.search(p.level(),at,p.blockPosition(),1024);
            if(route.reached())return at;
        }return null;
    }
    static BlockPos streetStop(ServerLevel city,BlockPos center){
        BlockPos best=null;double distance=Double.MAX_VALUE;
        for(var at:BlockPos.betweenClosed(center.offset(-10,-2,-10),center.offset(10,2,10))){
            if(at.distSqr(center)<9||!city.hasChunkAt(at)||!city.canSeeSky(at)||!NapoliResidentHomes.walkable(city,at)||CityProperty.home(city,at)
                    ||CityLivingHomes.all().stream().anyMatch(h->h.contains(at)))continue;
            if(!city.noCollision(new AABB(at.getX()-.3,at.getY(),at.getZ()-.3,at.getX()+1.3,at.getY()+1.8,at.getZ()+1.3)))continue;
            double score=at.distSqr(center);if(score<distance){best=at.immutable();distance=score;}
        }return best;
    }
    static boolean boardTaxi(ServerPlayer p,CityVehicle vehicle){
        var ride=p.getAttached(TAXI);
        if(ride==null||!ride.id().equals(vehicle.taxiId())||p.level()!=vehicle.level()||!p.isAlive()||p.isSpectator()
                ||Carabinieri.isJailed(p)||p.distanceToSqr(vehicle)>36||p.isPassenger())return false;
        if(!ride.paid()&&NapoliStreet.cash(p)<ride.fare()){Phones.feedback(p,t("phone.service.need_money",ride.fare()));return false;}
        if(!p.startRiding(vehicle))return false;
        if(!ride.paid()){NapoliStreet.credit(p,-ride.fare(),"transport","taxi");WorkEconomy.revenue(p.level(),ride.fare());}
        p.setAttached(TAXI,new Taxi(ride.id(),ride.pickup(),ride.goal(),ride.label(),ride.fare(),Long.MAX_VALUE,true));
        Phones.feedback(p,t("phone.service.taxi_boarded",ride.label()));return true;
    }
    static boolean tickTaxi(CityVehicle vehicle,ServerLevel city){
        if(vehicle.taxiId()==null)return false;
        var p=city.getServer().getPlayerList().getPlayer(vehicle.taxiOwner());var ride=p==null?null:p.getAttached(TAXI);
        if(ride==null||!ride.id().equals(vehicle.taxiId())||p.level()!=city||!p.isAlive()){
            vehicle.ejectPassengers();vehicle.discard();return true;
        }
        if(p.getVehicle()!=vehicle){vehicle.getNavigation().stop();return true;}
        if(ride.goal().distToCenterSqr(vehicle.position())<10*10){
            p.stopRiding();p.removeAttached(TAXI);Phones.feedback(p,t("phone.service.taxi_arrived",ride.label()));vehicle.discard();return true;
        }
        if(city.getGameTime()%20==0){
            var points=PlayerNavigator.macro(vehicle.blockPosition(),ride.goal());int nearest=0;
            for(int i=1;i<points.size();i++)if(points.get(i).distToCenterSqr(vehicle.position())<points.get(nearest).distToCenterSqr(vehicle.position()))nearest=i;
            var target=points.get(Math.min(points.size()-1,nearest+(points.get(nearest).distToCenterSqr(vehicle.position())<64?1:0)));
            vehicle.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,1.1);
        }
        return true;
    }
    static void tick(ServerPlayer p){
        if(!CityCompanion.inCity(p)||!p.isAlive()||p.isSpectator()||Carabinieri.isJailed(p))return;
        var city=p.level();long now=city.getGameTime();if(now<p.getAttachedOrElse(NEXT_SPAWN,0L))return;
        var order=p.getAttached(PIZZA);
        if(order!=null&&city.getOverworldClockTime()>=order.due()-100&&city.getEntity(order.id())==null){
            var at=spawnNear(p,4,7);p.setAttached(NEXT_SPAWN,now+40);
            if(at!=null){var npc=NapoliResident.create(city,NapoliResident.Role.SHIFT_WORKER,at,0);npc.setUUID(order.id());
                npc.addTag(COURIER);npc.addTag(OWNER+p.getUUID());npc.setCustomName(t("phone.service.courier"));npc.setCustomNameVisible(true);
                npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Napuli.PIZZA));npc.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND,0);city.addFreshEntity(npc);}
        }
        var ride=p.getAttached(TAXI);if(ride==null)return;
        if(now>=ride.expires()){cancel(p,"taxi");return;}
        if(city.getEntity(ride.id())!=null)return;
        BlockPos at=ride.paid()?streetStop(city,p.blockPosition()):ride.pickup();
        if(at==null||!city.hasChunkAt(at)||at.distToCenterSqr(p.position())>96*96)return;
        var vehicle=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);if(vehicle==null)return;
        vehicle.setUUID(ride.id());vehicle.configureTaxi(ride.id(),p.getUUID());vehicle.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        if(!city.noCollision(vehicle))return;
        vehicle.setCustomName(t("phone.service.taxi_name",p.getName()));vehicle.setCustomNameVisible(true);city.addFreshEntity(vehicle);
        Phones.feedback(p,t("phone.service.taxi_waiting",ride.fare()));
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTickCount()%10==0)for(var p:server.getPlayerList().getPlayers())tick(p);});
        ServerEntityEvents.ENTITY_LOAD.register((entity,city)->{
            if(entity instanceof NapoliResident npc){
                var speed=npc.getAttribute(Attributes.MOVEMENT_SPEED);
                if(speed!=null&&(Math.abs(speed.getBaseValue()-.23)<.0001||Math.abs(speed.getBaseValue()-.27)<.0001))
                    speed.setBaseValue(npc.role()==NapoliResident.Role.CARABINIERE?.32:.29);
            }
        });
    }
    private PhoneServices(){}
}
