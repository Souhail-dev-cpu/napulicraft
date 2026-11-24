package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.*;
import static dev.napuli.CityText.t;

final class HomePermissions {
    enum Capability {DOORS,APPLIANCES,STORAGE}
    record Grant(UUID host,String role,boolean doors,boolean appliances,boolean storage){
        static final Codec<Grant> CODEC=RecordCodecBuilder.create(i->i.group(UUIDUtil.CODEC.fieldOf("host").forGetter(Grant::host),Codec.STRING.fieldOf("role").forGetter(Grant::role),Codec.BOOL.fieldOf("doors").forGetter(Grant::doors),Codec.BOOL.fieldOf("appliances").forGetter(Grant::appliances),Codec.BOOL.fieldOf("storage").forGetter(Grant::storage)).apply(i,Grant::new));
        boolean allows(Capability c){return switch(c){case DOORS->doors;case APPLIANCES->appliances;case STORAGE->storage;};}
    }
    static final AttachmentType<Map<String,Grant>> GRANTS=AttachmentRegistry.<Map<String,Grant>>builder().persistent(Codec.unboundedMap(Codec.STRING,Grant.CODEC)).buildAndRegister(Napuli.id("home_permissions"));
    static String key(String house,UUID guest){return house+"/"+guest;}
    static void put(ServerLevel city,String house,UUID guest,Grant grant){var all=new HashMap<>(city.getAttachedOrElse(GRANTS,Map.of()));all.put(key(house,guest),grant);city.setAttached(GRANTS,Map.copyOf(all));}
    static Grant grant(ServerLevel city,String house,UUID guest){return city.getAttachedOrElse(GRANTS,Map.of()).get(key(house,guest));}
    static boolean allows(ServerLevel city,NapoliMergellina.House house,UUID guest,Capability capability){
        if(HouseGuests.host(city,house.id(),guest))return true;
        if(!HouseGuests.invited(city,house.id(),guest))return false;
        var grant=grant(city,house.id(),guest);
        // Invitations in existing saves retain their previous rights until explicitly edited.
        return grant==null||HouseGuests.host(city,house.id(),grant.host())&&grant.allows(capability);
    }
    static boolean allows(ServerPlayer p,NapoliMergellina.House house,Capability capability){return allows(p.level(),house,p.getUUID(),capability);}
    static void forget(ServerLevel city,String house,UUID guest){var all=new HashMap<>(city.getAttachedOrElse(GRANTS,Map.of()));if(guest==null)all.keySet().removeIf(k->k.startsWith(house+"/"));else all.remove(key(house,guest));city.setAttached(GRANTS,Map.copyOf(all));}
    static boolean edit(ServerPlayer p,String house,UUID guest,String action){
        if(!CityCompanion.inCity(p)||!PlayerTrade.available(p)||!HouseGuests.host(p.level(),house,p.getUUID())||HouseGuests.host(p.level(),house,guest)||!HouseGuests.invited(p.level(),house,guest))return false;
        var old=grant(p.level(),house,guest);if(old==null)old=new Grant(p.getUUID(),"guest",true,true,true);
        var next=switch(action){case "guest"->new Grant(p.getUUID(),"guest",true,false,false);case "roommate"->new Grant(p.getUUID(),"roommate",true,true,true);case "doors"->new Grant(p.getUUID(),old.role(),!old.doors(),old.appliances(),old.storage());case "appliances"->new Grant(p.getUUID(),old.role(),old.doors(),!old.appliances(),old.storage());case "storage"->new Grant(p.getUUID(),old.role(),old.doors(),old.appliances(),!old.storage());default->null;};
        if(next==null)return false;put(p.level(),house,guest,next);return true;
    }
    static int menu(ServerPlayer p,String house){
        if(!CityCompanion.inCity(p)||!HouseGuests.host(p.level(),house,p.getUUID()))return CityCompanion.fail(p,t("permission.owner_only"));var rows=new ArrayList<CompanionBoard.Entry>();
        rows.add(new CompanionBoard.Entry(t("permission.invite"),t("permission.defaults"),"casa ospiti "+house));
        for(var invitation:p.level().getAttachedOrElse(HouseGuests.INVITATIONS,Map.of()).getOrDefault(house,List.of()))if(HouseGuests.host(p.level(),house,invitation.host())){
            var g=grant(p.level(),house,invitation.guest());if(g==null)g=new Grant(invitation.host(),"guest",true,true,true);String prefix="compagno permessi "+house+"/"+invitation.guest()+"/";
            rows.add(new CompanionBoard.Entry(t("permission.member",invitation.name(),t("permission."+g.role())),t("permission.role_help"),prefix+(g.role().equals("guest")?"roommate":"guest")));
            for(var c:Capability.values())rows.add(new CompanionBoard.Entry(t("permission.switch",t("permission."+c.name().toLowerCase(Locale.ROOT)),t(g.allows(c)?"yes":"no")),t("permission.independent"),prefix+c.name().toLowerCase(Locale.ROOT)));
            rows.add(new CompanionBoard.Entry(t("permission.revoke",invitation.name()),t("permission.revoke_help"),prefix+"revoke"));
        }
        return CityCompanion.show(p,"permessi/"+house,t("permission.title"),rows);
    }
    static int action(ServerPlayer p,String value){var bits=value.split("/");if(bits.length==1)return menu(p,value);if(bits.length!=3)return 0;var id=CompanionMeetups.uuid(bits[1]);if(id==null)return 0;
        if(bits[2].equals("revoke")){if(!CityCompanion.inCity(p)||!PlayerTrade.available(p)||!HouseGuests.revoke(p.level(),bits[0],p.getUUID(),id))return CityCompanion.fail(p,t("permission.owner_only"));}
        else if(!edit(p,bits[0],id,bits[2]))return CityCompanion.fail(p,t("permission.owner_only"));return menu(p,bits[0]);}
    static void register(){UseBlockCallback.EVENT.register((player,level,hand,hit)->{
        if(!(player instanceof ServerPlayer p)||!CityCompanion.inCity(p))return InteractionResult.PASS;
        var block=level.getBlockState(hit.getBlockPos()).getBlock();Capability capability=null;
        if(block instanceof DoorBlock||block instanceof TrapDoorBlock||block instanceof FenceGateBlock)capability=Capability.DOORS;
        else if(block instanceof ChestBlock||block instanceof EnderChestBlock||block instanceof BarrelBlock||block instanceof ShulkerBoxBlock||block instanceof HopperBlock||block instanceof DispenserBlock)capability=Capability.STORAGE;
        else if(block instanceof AbstractFurnaceBlock||block instanceof CraftingTableBlock||block instanceof HomeFixtureBlock||block instanceof KitchenStationBlock||block instanceof BrewingStandBlock||block instanceof AnvilBlock||block instanceof SmithingTableBlock||block instanceof EnchantingTableBlock||block instanceof GrindstoneBlock||block instanceof LoomBlock||block instanceof StonecutterBlock||block instanceof CartographyTableBlock)capability=Capability.APPLIANCES;
        if(capability==null)return InteractionResult.PASS;
        for(var home:CityLivingHomes.all())if(home.price()>0&&home.contains(hit.getBlockPos())&&!allows(p,home,capability)){p.sendOverlayMessage(t("permission.denied",t("permission."+capability.name().toLowerCase(Locale.ROOT))));return InteractionResult.FAIL;}
        return InteractionResult.PASS;
    });}
    private HomePermissions(){}
}
