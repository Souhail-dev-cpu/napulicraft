package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;

/** Finite wheat -> flour -> bread. NPC households and players buy from the same stocks. */
final class CityEconomy {
    record Stock(int grain,int flour,int bread,long processed,long delivered) {
        static final Codec<Stock> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.INT.fieldOf("grain").forGetter(Stock::grain),Codec.INT.fieldOf("flour").forGetter(Stock::flour),
                Codec.INT.fieldOf("bread").forGetter(Stock::bread),Codec.LONG.fieldOf("processed").forGetter(Stock::processed),
                Codec.LONG.fieldOf("delivered").forGetter(Stock::delivered)).apply(i,Stock::new));
        static Stock initial(){return new Stock(96,8,96,-1,-1);}
    }
    record Delivery(String shop,long accepted,long deadline) {
        static final Codec<Delivery> CODEC=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("shop").forGetter(Delivery::shop),
                Codec.LONG.fieldOf("accepted").forGetter(Delivery::accepted),Codec.LONG.fieldOf("deadline").forGetter(Delivery::deadline)).apply(i,Delivery::new));
    }
    static final AttachmentType<Map<String,Stock>> STOCKS=AttachmentRegistry.<Map<String,Stock>>builder().persistent(Codec.unboundedMap(Codec.STRING,Stock.CODEC)).buildAndRegister(Napuli.id("business_stocks"));
    static final AttachmentType<Delivery> DELIVERY=AttachmentRegistry.<Delivery>builder().persistent(Delivery.CODEC).buildAndRegister(Napuli.id("supply_delivery"));
    static final BlockPos DEPOT=new BlockPos(-328,64,45);
    static final List<String> SHOPS=List.of("forno","mercato");
    static boolean merchant(NapoliCivicLife.Citizen c){return c.profession()!=null&&(c.profession().category==CityProfessions.Category.FOOD||c.profession().category==CityProfessions.Category.TRADE);}
    static String business(NapoliCivicLife.Citizen c){if(DistrictLife.resident(c))return DistrictShops.key(c);return c.profession()!=null&&c.profession().category==CityProfessions.Category.FOOD?"forno":"mercato";}
    static BlockPos site(String shop){return (shop.equals("forno")?CityProfessions.Category.FOOD:CityProfessions.Category.TRADE).site().offset(8,0,7);}
    static Stock stock(ServerLevel city,String shop){BakeryStock.migrate(city);return city.getAttachedOrElse(STOCKS,Map.of()).getOrDefault(shop,shop.equals("mercato")?new Stock(0,0,48,-1,-1):Stock.initial());}
    static void put(ServerLevel city,String shop,Stock stock){var all=new HashMap<>(city.getAttachedOrElse(STOCKS,Map.of()));all.put(shop,stock);city.setAttached(STOCKS,Map.copyOf(all));}
    static void supply(ServerLevel city,String shop,int count){var s=stock(city,shop);put(city,shop,new Stock(Math.min(512,s.grain()+count),s.flour(),s.bread(),s.processed(),s.delivered()));}
    static void supplyFlour(ServerLevel city,int count){var s=stock(city,"forno");put(city,"forno",new Stock(s.grain(),s.flour()+Math.max(0,count),s.bread(),s.processed(),s.delivered()));}
    static boolean reserveBread(ServerLevel city,String shop,int count){
        if(count<=0)return false;var s=stock(city,shop);if(s.bread()<count)return false;
        put(city,shop,new Stock(s.grain(),s.flour(),s.bread()-count,s.processed(),s.delivered()));return true;
    }
    static int price(ServerPlayer player,UUID seller,int base){return CitySocial.price(player.level(),seller,player.getUUID(),CityEvents.price(player.level(),base));}
    static Stock produce(Stock s,long period,boolean working){
        if(period<=s.processed())return s;
        if(!working)return new Stock(s.grain(),s.flour(),s.bread(),period,s.delivered());
        int milled=Math.min(16,Math.min(s.grain(),Math.max(0,64-s.flour())));
        int baked=Math.min(16,Math.min(s.flour()+milled,Math.max(0,192-s.bread())));
        return new Stock(s.grain()-milled,s.flour()+milled-baked,s.bread()+baked,period,s.delivered());
    }
    /** Returns the actual units bought; insufficient money or stock never creates food. */
    static int buyMeals(ServerLevel city,String shop,int requested,int money){
        var s=stock(city,shop);int count=Math.max(0,Math.min(requested,Math.min(s.bread(),money/2)));
        if(count>0)put(city,shop,new Stock(s.grain(),s.flour(),s.bread()-count,s.processed(),s.delivered()));return count;
    }
    static void shopResident(ServerLevel city,NapoliCivicLife.Citizen c){
        if(DistrictLife.resident(c)){DistrictShops.shopResident(city,c);return;}
        if(CityTraces.cargo(city,c.id())!=null)return;
        var l=CitySimulation.life(city,c.id());int bought=buyMeals(city,"mercato",Math.max(0,6-l.food()),c.balance());if(bought==0)return;
        CitySimulation.put(city,c.id(),l.withNeeds(l.needs(),l.food()+bought,l.last(),l.water(),l.energy()));
        var census=NapoliCivicLife.census(city);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),census.people().stream().map(p->p.id().equals(c.id())?p.withMoney(p.balance()-bought*2,p.job()):p).toList()));
        CityTraces.shopping(city,c,bought);
    }
    static boolean take(ServerPlayer p,Item item,int count){
        int found=0;for(int slot=0;slot<p.getInventory().getContainerSize();slot++)if(p.getInventory().getItem(slot).is(item))found+=p.getInventory().getItem(slot).getCount();
        if(found<count)return false;
        for(int slot=0;slot<p.getInventory().getContainerSize()&&count>0;slot++){
            var stack=p.getInventory().getItem(slot);if(!stack.is(item))continue;int n=Math.min(count,stack.getCount());stack.shrink(n);count-=n;
        }p.getInventory().setChanged();return true;
    }
    static int buy(ServerPlayer p,UUID id){
        var local=NapoliCivicLife.profile(p.level(),id);if(DistrictLife.resident(local))return DistrictShops.buy(p,DistrictLife.district(local).id(),"pane");
        var c=CitySocial.nearby(p,id);if(c==null||!merchant(c)||!CityProfessions.open(c.profession(),NapoliBroadcast.time(p.level()),NapoliBroadcast.day(p.level())))return 0;
        if(CitySocial.bond(p.level(),id,p.getUUID()).trust()<15){p.sendSystemMessage(Component.literal("Prima dobbiamo risolvere la nostra questione."));return 0;}
        var shop=business(c);var s=stock(p.level(),shop);int cost=CitySocial.price(p.level(),id,p.getUUID(),CityEvents.price(p.level(),2));
        if(s.bread()==0){p.sendSystemMessage(Component.literal("Pane esaurito. Una consegna di grano permetterà al forno di ripartire."));return 0;}
        if(NapoliStreet.cash(p)<cost||!p.getInventory().add(new ItemStack(Items.BREAD)))return 0;
        NapoliStreet.credit(p,-cost);put(p.level(),shop,new Stock(s.grain(),s.flour(),s.bread()-1,s.processed(),s.delivered()));
        CitySocial.purchased(p.level(),id,p.getUUID());return 1;
    }
    static void offer(ServerPlayer p,UUID id){
        var local=NapoliCivicLife.profile(p.level(),id);if(DistrictLife.resident(local)){DistrictShops.menu(p,DistrictLife.district(local).id());return;}
        var c=CitySocial.nearby(p,id);if(c==null||!merchant(c))return;var shop=business(c);var s=stock(p.level(),shop);
        int price=CitySocial.price(p.level(),id,p.getUUID(),CityEvents.price(p.level(),2));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal(c.name()+" · Bottega"),Optional.empty(),true,false,DialogAction.CLOSE,
                List.of(new PlainMessage(Component.literal("Disponibili: "+s.bread()+" pani · grano: "+s.grain()+" · farina: "+s.flour()+
                        "\nIl forno produce solo se riceve materie prime. Le consegne in ritardo ritardano anche la produzione."),270)),List.of()),
                List.of(CitySocial.button("Pane · "+price+" €","/quartiere compra "+id),CitySocial.button("Lavoro: rifornisci "+shop+" · 12 €","/quartiere incarico "+shop)),Optional.empty(),1)));
    }
    static int delivery(ServerPlayer p,String shop,boolean complete){
        if(p.level().dimension()!=NapoliMap.LEVEL||!p.isAlive()||p.isSpectator()||Carabinieri.isJailed(p)||!SHOPS.contains(shop))return 0;
        long now=p.level().getGameTime();var active=p.getAttached(DELIVERY);
        if(!complete){
            if(active!=null){p.sendSystemMessage(Component.literal("Hai già una consegna: "+active.shop()+". Porta 8 grano alla bottega."));return 0;}
            if(p.blockPosition().distSqr(DEPOT)>36){p.sendSystemMessage(Component.literal("Ritira l'incarico al deposito: "+DEPOT.getX()+", "+DEPOT.getZ()+". Servono 8 grano del tuo inventario."));return 0;}
            if(stock(p.level(),shop).grain()>24){p.sendSystemMessage(Component.literal("La bottega ha ancora provviste sufficienti. Ripassa quando avrà bisogno."));return 0;}
            p.setAttached(DELIVERY,new Delivery(shop,now,now+6000));p.sendSystemMessage(Component.literal("Incarico accettato: porta 8 grano a "+shop+" ("+site(shop).getX()+", "+site(shop).getZ()+"). Compenso 12 €, oppure 6 € dopo 5 minuti."));return 1;
        }
        if(active==null||!active.shop().equals(shop)||p.blockPosition().distSqr(site(shop))>64||!take(p,Items.WHEAT,8))return 0;
        supply(p.level(),shop,8);p.removeAttached(DELIVERY);int reward=now<=active.deadline()?12:6;NapoliStreet.credit(p,reward);
        p.sendSystemMessage(Component.literal("Consegna completata: +"+reward+" €. Il grano è nelle scorte della bottega."));return 1;
    }
    static void tick(ServerLevel city){
        if(NapoliCivicLife.census(city)==null)return;
        long time=NapoliBroadcast.time(city),day=NapoliBroadcast.day(city),period=city.getGameTime()/600;
        for(var shop:SHOPS){
            var s=stock(city,shop);
            if(shop.equals("mercato")){
                var bakery=stock(city,"forno");int grain=Math.max(0,Math.min(32,Math.min(s.grain(),512-bakery.grain()))),bread=Math.max(0,Math.min(16,Math.min(bakery.bread(),96-s.bread())));
                put(city,"forno",new Stock(bakery.grain()+grain,bakery.flour(),bakery.bread()-bread,bakery.processed(),bakery.delivered()));
                put(city,shop,new Stock(s.grain()-grain,s.flour(),s.bread()+bread,period,s.delivered()));continue;
            }
            // One finite daily shipment. A transport strike postpones it until the following morning.
            if(time>=1000&&s.delivered()<day&&!(CityEvents.event(day)==CityEvents.Event.STRIKE&&time<11500))s=new Stock(Math.min(512,s.grain()+Math.max(24,NapoliCivicLife.census(city).people().size())),s.flour(),s.bread(),s.processed(),day);
            boolean working=!CityPersonalGoals.ovenBroken(city)&&time>=1000&&time<11000&&NapoliCivicLife.census(city).people().stream().anyMatch(c->merchant(c)&&business(c).equals(shop)&&CityHabits.working(c,time,day)&&CitySimulation.life(city,c.id()).health()>=45&&!CityPublicOrder.jailed(city,c.id())&&city.getEntity(c.id())==null&&CityJourneys.at(city,c,CityOccupations.workplace(c),10));
            put(city,shop,produce(s,period,working));
        }
    }
    static void register(){
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%200==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)tick(city);}});
        CommandRegistrationCallback.EVENT.register((d,a,e)->{
            var root=Commands.literal("quartiere");
            for(var action:List.of("bottega","compra"))root.then(Commands.literal(action).then(Commands.argument("persona",StringArgumentType.word()).executes(c->{try{var p=c.getSource().getPlayerOrException();var id=UUID.fromString(StringArgumentType.getString(c,"persona"));if(action.equals("bottega")){offer(p,id);return 1;}return buy(p,id);}catch(IllegalArgumentException invalid){return 0;}})));
            for(var action:List.of("incarico","consegna"))root.then(Commands.literal(action).then(Commands.argument("bottega",StringArgumentType.word()).suggests((c,b)->{SHOPS.forEach(b::suggest);return b.buildFuture();}).executes(c->delivery(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"bottega"),action.equals("consegna")))));
            d.register(root);
        });
    }
    private CityEconomy(){}
}
