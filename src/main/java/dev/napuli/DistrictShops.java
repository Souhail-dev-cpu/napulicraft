package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** Each neighborhood has its own finite inventory, production, till, staff and once-a-day paid delivery. */
final class DistrictShops {
    record Ledger(Map<String,Integer> goods,int till,long delivered){static final Codec<Ledger> CODEC=RecordCodecBuilder.create(i->i.group(Codec.unboundedMap(Codec.STRING,Codec.INT).fieldOf("goods").forGetter(Ledger::goods),Codec.INT.fieldOf("till").forGetter(Ledger::till),Codec.LONG.fieldOf("delivered").forGetter(Ledger::delivered)).apply(i,Ledger::new));}
    record Product(String id,String name,Item item,int price,int initial){}
    static List<Product> products(){return List.of(new Product("pane","Pane",Items.BREAD,2,24),new Product("caffe","Caffè",Napuli.CAFE,2,24),new Product("mele","Mela",Items.APPLE,1,32),new Product("pesce","Pesce cotto",Items.COOKED_COD,4,16),new Product("torce","Torce",Items.TORCH,2,16),new Product("attrezzi","Piccone di ferro",Items.IRON_PICKAXE,18,4));}
    static final AttachmentType<Map<String,Ledger>> LEDGERS=AttachmentRegistry.<Map<String,Ledger>>builder().persistent(Codec.unboundedMap(Codec.STRING,Ledger.CODEC)).buildAndRegister(Napuli.id("district_shop_ledgers"));
    static String key(DistrictLife.District d){return "district_"+d.id();}
    static String key(NapoliCivicLife.Citizen c){var d=DistrictLife.district(c);return d==null?"forno":key(d);}
    static Ledger ledger(ServerLevel city,String id){return city.getAttachedOrElse(LEDGERS,Map.of()).getOrDefault(id,new Ledger(Map.of("caffe",24,"mele",32,"pesce",16,"torce",16,"attrezzi",4),96,-1));}
    static void put(ServerLevel city,String id,Ledger l){var all=new HashMap<>(city.getAttachedOrElse(LEDGERS,Map.of()));all.put(id,l);city.setAttached(LEDGERS,Map.copyOf(all));}
    static NapoliCivicLife.Citizen staff(ServerLevel city,DistrictLife.District d){var census=NapoliCivicLife.census(city);if(census==null)return null;return census.people().stream().filter(c->DistrictLife.resident(c)&&DistrictLife.district(c).id().equals(d.id())&&c.profession()==NapoliCivicLife.Job.BAKER&&CityHabits.working(c,NapoliBroadcast.time(city),NapoliBroadcast.day(city))&&CitySimulation.life(city,c.id()).health()>=45&&CityJourneys.at(city,c,DistrictLife.work(c),12)).findFirst().orElse(null);}
    static int quantity(ServerLevel city,DistrictLife.District d,String product){return product.equals("pane")?CityEconomy.stock(city,key(d)).bread():ledger(city,d.id()).goods().getOrDefault(product,0);}
    static boolean reserve(ServerLevel city,DistrictLife.District d,String product,int units){if(units<=0||quantity(city,d,product)<units)return false;if(product.equals("pane"))return CityEconomy.reserveBread(city,key(d),units);var l=ledger(city,d.id());var goods=new HashMap<>(l.goods());goods.put(product,goods.getOrDefault(product,0)-units);put(city,d.id(),new Ledger(Map.copyOf(goods),l.till(),l.delivered()));return true;}
    static void credit(ServerLevel city,DistrictLife.District d,int amount){var l=ledger(city,d.id());put(city,d.id(),new Ledger(l.goods(),Math.clamp(l.till()+amount,0,100000),l.delivered()));}
    static int buy(ServerPlayer p,String district,String item){var d=DistrictLife.byId(district);if(d==null||p.level().dimension()!=NapoliMap.LEVEL||!p.isAlive()||p.isSpectator()||Carabinieri.isJailed(p)||p.blockPosition().distSqr(d.business().entrance())>14*14||staff(p.level(),d)==null)return 0;
        var product=products().stream().filter(x->x.id().equals(item)).findFirst().orElse(null);if(product==null)return 0;var seller=staff(p.level(),d);var entity=p.level().getEntity(seller.id());if(entity==null||entity.distanceToSqr(p)>10*10||!p.hasLineOfSight(entity)||CitySocial.bond(p.level(),seller.id(),p.getUUID()).trust()<15)return 0;int price=CityEconomy.price(p,seller.id(),product.price());if(NapoliStreet.cash(p)<price||quantity(p.level(),d,item)<1)return 0;
        // Inventory admission precedes debit; the server serializes competing purchases.
        if(!p.getInventory().add(new ItemStack(product.item())))return 0;if(!reserve(p.level(),d,item,1))throw new IllegalStateException("Inventory changed during serialized shop purchase");NapoliStreet.credit(p,-price);credit(p.level(),d,price);CitySocial.purchased(p.level(),seller.id(),p.getUUID());p.sendSystemMessage(Component.literal(product.name()+" acquistato: "+price+" € · rimasti "+quantity(p.level(),d,item)));return 1;
    }
    static void shopResident(ServerLevel city,NapoliCivicLife.Citizen c){var d=DistrictLife.district(c);if(d==null||CityTraces.cargo(city,c.id())!=null||staff(city,d)==null)return;var l=CitySimulation.life(city,c.id());int units=Math.min(6-l.food(),Math.min(quantity(city,d,"pane"),c.balance()/2));if(units<=0||!reserve(city,d,"pane",units))return;CityOccupations.money(city,c.id(),-units*2);credit(city,d,units*2);CitySimulation.put(city,c.id(),l.withNeeds(l.needs(),l.food()+units,l.last(),l.water(),l.energy()));CityTraces.shopping(city,c,units);}
    static int menu(ServerPlayer p,String id){var d=DistrictLife.byId(id);if(d==null)return 0;var worker=staff(p.level(),d);var buttons=new ArrayList<ActionButton>();buttons.add(CitySocial.button("Fermata vicina alla bottega","/trasporti cerca "+CityNetwork.nearest(d.business().entrance()).id()));for(var item:products())buttons.add(CitySocial.button(item.name()+" · "+item.price()+" € · "+quantity(p.level(),d,item.id())+" disponibili","/negozio compra "+id+" "+item.id()));
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.literal("Bottega · "+d.name()),Optional.empty(),true,false,DialogAction.CLOSE,List.of(new PlainMessage(Component.literal((worker==null?"Chiuso: il negoziante è assente o fuori turno.":"Al banco: "+worker.name())+"\nIndirizzo: "+d.business().entrance().toShortString()+"\nPane, generi alimentari e attrezzi hanno scorte locali. Il prezzo finale tiene conto dei rapporti con il negoziante. Raggiungi l'ingresso per acquistare."),300)),List.of()),buttons,Optional.empty(),1)));return 1;}
    static void fit(ServerLevel city,net.minecraft.world.level.chunk.LevelChunk chunk){for(var d:DistrictLife.districts()){var h=d.business();var points=List.of(new BlockPos(h.x()+8,h.ground(),h.z()+17),new BlockPos(h.x()+11,h.ground(),h.z()+18),h.entrance().east(2));for(var pos:points)if(net.minecraft.world.level.ChunkPos.containing(pos).equals(chunk.getPos())&&!CityProperty.playerPlaced(city,pos)){if(pos.equals(points.get(2)))NapoliEmploymentMap.sign(city,pos,"BOTTEGA",d.name(),"Alimenti e attrezzi","Tasto destro");else city.setBlock(pos,(pos.equals(points.getFirst())?Blocks.SMOKER:Blocks.BARREL).defaultBlockState(),2);}}
    }
    static void tick(ServerLevel city){var census=NapoliCivicLife.census(city);if(census==null)return;long day=NapoliBroadcast.day(city),period=city.getGameTime()/600;for(var d:DistrictLife.districts()){
        var l=ledger(city,d.id());var s=CityEconomy.stock(city,key(d));if(l.delivered()<day&&NapoliBroadcast.time(city)>=1000&&l.till()>=24&&!(CityEvents.active(city)&&CityEvents.event(day)==CityEvents.Event.STRIKE)){
            var goods=new HashMap<>(l.goods());for(var p:products())if(!p.id().equals("pane"))goods.put(p.id(),Math.min(p.initial()*2,goods.getOrDefault(p.id(),0)+Math.max(1,p.initial()/2)));l=new Ledger(Map.copyOf(goods),l.till()-24,day);put(city,d.id(),l);s=new CityEconomy.Stock(Math.min(192,s.grain()+24),s.flour(),s.bread(),s.processed(),day);}
        var worker=staff(city,d);boolean virtual=worker!=null&&city.getEntity(worker.id())==null;CityEconomy.put(city,key(d),CityEconomy.produce(s,period,virtual));
    }}
    static void register(){ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%200==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)tick(city);}});
        UseBlockCallback.EVENT.register((p,level,hand,hit)->{if(!(p instanceof ServerPlayer player)||hand!=InteractionHand.MAIN_HAND||level.dimension()!=NapoliMap.LEVEL)return InteractionResult.PASS;for(var d:DistrictLife.districts())if(hit.getBlockPos().equals(d.business().entrance().east(2))){menu(player,d.id());return InteractionResult.SUCCESS;}return InteractionResult.PASS;});
        CommandRegistrationCallback.EVENT.register((d,r,e)->d.register(Commands.literal("negozio").executes(c->{var p=c.getSource().getPlayerOrException();var near=DistrictLife.districts().stream().min(Comparator.comparingDouble(q->q.business().entrance().distSqr(p.blockPosition()))).orElseThrow();return menu(p,near.id());}).then(Commands.literal("apri").then(Commands.argument("quartiere",StringArgumentType.word()).executes(c->menu(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"quartiere"))))).then(Commands.literal("compra").then(Commands.argument("quartiere",StringArgumentType.word()).then(Commands.argument("articolo",StringArgumentType.word()).executes(c->buy(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"quartiere"),StringArgumentType.getString(c,"articolo"))))))));
    }
    private DistrictShops(){}
}
