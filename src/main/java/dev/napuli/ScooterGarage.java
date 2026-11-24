package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import static dev.napuli.CityText.t;

/** A saved generation invalidates old entities and copied recovery tokens without loading chunks. */
final class ScooterGarage {
    static final int RECOVERY_COST=10;
    record Bike(UUID id,UUID owner,String dimension,BlockPos position,int generation,boolean stored,boolean occupied,int station){
        static final Codec<Bike> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("id").forGetter(Bike::id),UUIDUtil.CODEC.fieldOf("owner").forGetter(Bike::owner),Codec.STRING.fieldOf("dimension").forGetter(Bike::dimension),BlockPos.CODEC.fieldOf("position").forGetter(Bike::position),Codec.INT.fieldOf("generation").forGetter(Bike::generation),Codec.BOOL.fieldOf("stored").forGetter(Bike::stored),Codec.BOOL.fieldOf("occupied").forGetter(Bike::occupied),Codec.INT.fieldOf("station").forGetter(Bike::station)).apply(i,Bike::new));
    }
    static final AttachmentType<Map<String,Bike>> BIKES=AttachmentRegistry.<Map<String,Bike>>builder().persistent(Codec.unboundedMap(Codec.STRING,Bike.CODEC)).buildAndRegister(Napuli.id("owned_scooters"));
    static final AttachmentType<UUID> OWNER=AttachmentRegistry.<UUID>builder().persistent(UUIDUtil.CODEC).buildAndRegister(Napuli.id("scooter_owner"));
    static final AttachmentType<Integer> GENERATION=AttachmentRegistry.<Integer>builder().persistent(Codec.INT).buildAndRegister(Napuli.id("scooter_generation"));
    static final AttachmentType<UUID> LAST=AttachmentRegistry.<UUID>builder().persistent(UUIDUtil.CODEC).copyOnDeath().buildAndRegister(Napuli.id("last_scooter"));
    static ServerLevel registry(ServerLevel level){return level.getServer().overworld();}
    static Bike get(ServerLevel level,UUID id){return registry(level).getAttachedOrElse(BIKES,Map.of()).get(id.toString());}
    static void put(ServerLevel level,Bike bike){var all=new HashMap<>(registry(level).getAttachedOrElse(BIKES,Map.of()));all.put(bike.id().toString(),bike);registry(level).setAttached(BIKES,Map.copyOf(all));}
    static String dimension(ServerLevel level){return level.dimension().identifier().toString();}
    static boolean observe(Motorino m,ServerPlayer driver){
        if(m.inParade()||!(m.level() instanceof ServerLevel level))return true;
        var owner=m.getAttached(OWNER);if(owner==null){if(driver==null)return true;owner=driver.getUUID();m.setAttached(OWNER,owner);}
        var old=get(level,m.getUUID());int generation=m.getAttachedOrElse(GENERATION,0);
        if(old!=null&&(old.stored()||old.generation()!=generation||!old.owner().equals(owner))){m.discard();return false;}
        var snapshot=new Bike(m.getUUID(),owner,dimension(level),m.blockPosition().immutable(),generation,false,m.isVehicle(),m.savedStation());if(!snapshot.equals(old))put(level,snapshot);
        if(driver!=null&&owner.equals(driver.getUUID()))driver.setAttached(LAST,m.getUUID());return true;
    }
    static ItemStack token(Bike b){var item=new ItemStack(Napuli.MOTORINO_ITEM);var tag=new CompoundTag();tag.putString("napuli_bike",b.id().toString());tag.putInt("napuli_generation",b.generation());item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return item;}
    static UUID tokenId(ItemStack item){return CompanionMeetups.uuid(item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr("napuli_bike",""));}
    static boolean marked(ItemStack item){return item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().contains("napuli_bike");}
    static boolean matches(Bike b,ServerPlayer p,ItemStack item){return b!=null&&b.stored()&&b.owner().equals(p.getUUID())&&b.generation()==item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getIntOr("napuli_generation",-1);}
    static boolean prepare(ServerPlayer p,ItemStack item,Motorino m){
        if(marked(item)){var id=tokenId(item);var b=id==null?null:get(p.level(),id);if(!matches(b,p,item)){p.sendOverlayMessage(t("scooter.invalid"));return false;}m.setUUID(b.id());m.setAttached(GENERATION,b.generation());m.setStation(b.station());m.setStation(Radio.OFF);}
        m.setAttached(OWNER,p.getUUID());return true;
    }
    static void placed(ServerPlayer p,Motorino m){put(p.level(),new Bike(m.getUUID(),p.getUUID(),dimension(p.level()),m.blockPosition().immutable(),m.getAttachedOrElse(GENERATION,0),false,false,m.savedStation()));p.setAttached(LAST,m.getUUID());}
    static Bike stored(Bike b){return new Bike(b.id(),b.owner(),b.dimension(),b.position(),b.generation()+1,true,false,b.station());}
    static boolean pickup(ServerPlayer p,Motorino m){
        if(!observe(m,p)||!p.getUUID().equals(m.getAttached(OWNER))){p.sendOverlayMessage(t("scooter.owner_only"));return false;}
        if(m.isVehicle())return false;var next=stored(get(p.level(),m.getUUID()));var inventory=PlayerInventoryPlan.copy(p);if(!PlayerInventoryPlan.add(inventory,token(next))){p.sendOverlayMessage(t("scooter.full"));return false;}
        put(p.level(),next);PlayerInventoryPlan.apply(p,inventory);m.discard();return true;
    }
    static Motorino loaded(ServerLevel level,UUID id){for(var world:level.getServer().getAllLevels()){var entity=world.getEntity(id);if(entity instanceof Motorino m)return m;}return null;}
    static int menu(ServerPlayer p){
        var entries=new ArrayList<CompanionBoard.Entry>();var last=p.getAttached(LAST);
        var owned=registry(p.level()).getAttachedOrElse(BIKES,Map.of()).values().stream().filter(b->b.owner().equals(p.getUUID())).sorted(Comparator.comparing(b->!b.id().equals(last))).toList();
        for(var bike:owned){var m=loaded(p.level(),bike.id());if(m!=null)observe(m,null);var b=get(p.level(),bike.id());entries.add(new CompanionBoard.Entry(t(b.id().equals(last)?"scooter.last":"scooter.bike"),t("scooter.status",t(b.stored()?"scooter.stored":b.occupied()?"scooter.occupied":"scooter.parked"),b.position().toShortString(),b.dimension()),b.stored()?"":"compagno motorino segui/"+b.id()));if(!b.stored())entries.add(new CompanionBoard.Entry(t("scooter.recovery",RECOVERY_COST),t("scooter.recovery_help"),"compagno motorino preventivo/"+b.id()+"/"+b.generation()));}
        entries.add(new CompanionBoard.Entry(t("scooter.depot"),t("scooter.depot_help",RECOVERY_COST),"compagno motorino deposito"));
        entries.add(new CompanionBoard.Entry(t("scooter.parking"),t("scooter.parking_help"),"compagno motorino parcheggi"));
        return CityCompanion.show(p,"motorini",t("scooter.title"),entries);
    }
    static final List<BlockPos> PARKING=List.of(NapoliMunicipio.GARAGE.east(5),new BlockPos(-159,64,72),new BlockPos(414,64,34));
    static int action(ServerPlayer p,String value){
        var parts=value.split("/");if(parts[0].equals("deposito")){CityCompanion.pin(p,new CityCompanion.Goal("garage",NapoliMunicipio.GARAGE,t("scooter.depot")));return menu(p);}
        if(parts[0].equals("parcheggi")){var rows=new ArrayList<CompanionBoard.Entry>();for(int i=0;i<PARKING.size();i++)rows.add(new CompanionBoard.Entry(t("scooter.parking_number",i+1),net.minecraft.network.chat.Component.literal(PARKING.get(i).toShortString()),"compagno motorino parcheggio/"+i));return CityCompanion.show(p,"parcheggi",t("scooter.parking"),rows);}
        if(parts[0].equals("parcheggio")){try{var at=PARKING.get(Integer.parseInt(parts[1]));CityCompanion.pin(p,new CityCompanion.Goal("parking",at,t("scooter.parking")));return menu(p);}catch(RuntimeException ignored){return 0;}}
        if(parts.length<2)return 0;var id=CompanionMeetups.uuid(parts[1]);var b=id==null?null:get(p.level(),id);if(b==null||!b.owner().equals(p.getUUID()))return CityCompanion.fail(p,t("scooter.owner_only"));
        if(parts[0].equals("segui")){if(!dimension(p.level()).equals(b.dimension())||b.stored())return CityCompanion.fail(p,t("scooter.other_world"));CityCompanion.pin(p,new CityCompanion.Goal("motorino",b.position(),t("scooter.last")));return menu(p);}
        if(parts[0].equals("preventivo"))return CityCompanion.show(p,"recupero",t("scooter.recovery",RECOVERY_COST),List.of(new CompanionBoard.Entry(t("scooter.confirm",RECOVERY_COST),t("scooter.recovery_help"),"compagno motorino recupera/"+b.id()+"/"+b.generation()),new CompanionBoard.Entry(t("scooter.depot"),t("scooter.depot_help",RECOVERY_COST),"compagno motorino deposito")));
        if(parts[0].equals("recupera")){if(!CityCompanion.inCity(p)||!PlayerTrade.available(p)||p.blockPosition().distSqr(NapoliMunicipio.GARAGE)>64)return CityCompanion.fail(p,t("scooter.at_depot"));if(parts.length<3||!parts[2].equals(Integer.toString(b.generation()))||b.stored())return CityCompanion.fail(p,t("scooter.invalid"));var m=loaded(p.level(),id);if(m!=null){observe(m,null);b=get(p.level(),id);}if(b.occupied()||m!=null&&m.isVehicle())return CityCompanion.fail(p,t("scooter.busy"));if(NapoliStreet.cash(p)<RECOVERY_COST)return CityCompanion.fail(p,t("scooter.poor"));var next=stored(b);var inventory=PlayerInventoryPlan.copy(p);if(!PlayerInventoryPlan.add(inventory,token(next)))return CityCompanion.fail(p,t("scooter.full"));put(p.level(),next);if(m!=null)m.discard();PlayerInventoryPlan.apply(p,inventory);NapoliStreet.credit(p,-RECOVERY_COST,"scooter");WorkEconomy.revenue(p.level(),RECOVERY_COST);return menu(p);}return 0;
    }
    static boolean tradable(ServerPlayer p,Collection<ItemStack> items){var seen=new HashSet<UUID>();for(var item:items)if(item.is(Napuli.MOTORINO_ITEM)&&marked(item)){var id=tokenId(item);if(id==null||item.getCount()!=1||!seen.add(id)||!matches(get(p.level(),id),p,item))return false;}return true;}
    static void transfer(ServerPlayer from,ServerPlayer to,Collection<ItemStack> items){for(var item:items)if(item.is(Napuli.MOTORINO_ITEM)&&marked(item)){var b=get(from.level(),tokenId(item));put(from.level(),new Bike(b.id(),to.getUUID(),b.dimension(),b.position(),b.generation(),true,false,b.station()));to.setAttached(LAST,b.id());}}
    static void register(){}
    private ScooterGarage(){}
}
