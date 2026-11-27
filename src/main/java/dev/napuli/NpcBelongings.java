package dev.napuli;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;

/** A finite individual bag, rather than a new reward on every inventory opening. */
final class NpcBelongings {
    static String theme(NapoliResident npc){var city=(ServerLevel)npc.level();var c=NapoliCivicLife.profile(city,npc.getUUID());return c==null?"role:"+npc.role().id():c.age(NapoliBroadcast.day(city))<18?"child":"job:"+(c.profession()==null?"resident":c.profession().name());}
    static List<ItemStack> create(NapoliResident npc){
        var pool=new ArrayList<>(pool(npc));var random=new Random(npc.getUUID().getMostSignificantBits()^Long.rotateLeft(npc.getUUID().getLeastSignificantBits(),17));Collections.shuffle(pool,random);
        var items=new ArrayList<ItemStack>();for(int i=0;i<4;i++)items.add(new ItemStack(pool.get(i),1+random.nextInt(2)));
        var personal=new ArrayList<>(List.of(Items.APPLE,Items.BREAD,Items.BAKED_POTATO,Items.LEATHER,Items.STRING,Items.PAPER));Collections.shuffle(personal,random);for(int i=0;i<2;i++)items.add(new ItemStack(personal.get(i)));
        while(items.size()<9)items.add(ItemStack.EMPTY);Collections.rotate(items,random.nextInt(9));return List.copyOf(items);
    }
    private static List<Item> pool(NapoliResident npc){
        var c=NapoliCivicLife.profile((ServerLevel)npc.level(),npc.getUUID());
        if(c!=null&&c.age(NapoliBroadcast.day((ServerLevel)npc.level()))<18||npc.role()==NapoliResident.Role.SCUGNIZZO||npc.role()==NapoliResident.Role.FAMILY_CHILD||npc.role()==NapoliResident.Role.FAMILY_CHILD_TWO)return List.of(Items.BOOK,Items.PAPER,Items.APPLE,Items.FEATHER,Items.STRING,Items.COOKIE);
        if(c!=null&&c.profession()!=null){
            var specific=switch(c.profession()){
                case CARPENTER->List.of(Items.OAK_PLANKS,Items.STICK,Items.OAK_LOG,Items.WOODEN_AXE,Items.SPRUCE_PLANKS,Items.STRING);
                case FISHER,FISHMONGER->List.of(Items.COD,Items.SALMON,Items.KELP,Items.STRING,Items.FISHING_ROD,Items.BOWL);
                case GARDENER,FLORIST->List.of(Items.WHEAT_SEEDS,Items.BONE_MEAL,Items.DANDELION,Items.POPPY,Items.OAK_SAPLING,Items.MOSS_BLOCK);
                case BAKER,PASTRY_CHEF->List.of(Items.WHEAT,Items.BREAD,Items.SUGAR,Items.COOKIE,Items.EGG,Items.BOWL);
                case COURIER,POSTMAN,POSTAL_CLERK,DELIVERY_RIDER->List.of(Items.PAPER,Items.STRING,Items.LEATHER,Items.FEATHER,Items.INK_SAC,Items.BOOK);
                case CLEANER,LAUNDERER->List.of(Items.BRUSH,Items.GLASS_BOTTLE,Items.WOOL.white(),Items.STRING,Items.BOWL,Items.STICK);
                case MUSICIAN,RADIO_HOST->List.of(Items.PAPER,Items.FEATHER,Items.INK_SAC,Items.STRING,Items.STICK,Items.BOOK);
                default->null;
            };if(specific!=null)return specific;
            return switch(c.profession().category){
                case FOOD->List.of(Items.CARROT,Items.POTATO,Items.BOWL,Items.WHEAT,Items.EGG,Items.SUGAR);
                case TRADE->List.of(Items.PAPER,Items.LEATHER,Items.STRING,Items.APPLE,Items.BOOK,Items.COPPER_INGOT);
                case SERVICES->List.of(Items.SHEARS,Items.STRING,Items.LEATHER,Items.WOOL.white(),Items.PAPER,Items.GLASS_BOTTLE);
                case PUBLIC->List.of(Items.BOOK,Items.PAPER,Items.FEATHER,Items.INK_SAC,Items.MAP,Items.STRING);
                case TRANSPORT->List.of(Items.COMPASS,Items.PAPER,Items.IRON_NUGGET,Items.COAL,Items.LEATHER,Items.STRING);
                case MEDIA->List.of(Items.PAPER,Items.FEATHER,Items.INK_SAC,Items.BOOK,Items.STRING,Items.LEATHER);
                case BUILDING->List.of(Items.BRICK,Items.COBBLESTONE,Items.STICK,Items.IRON_NUGGET,Items.STRING,Items.OAK_PLANKS);
                case OFFICE->List.of(Items.PAPER,Items.BOOK,Items.FEATHER,Items.INK_SAC,Items.LEATHER,Items.MAP);
                case HEALTH->List.of(Items.PAPER,Items.GLASS_BOTTLE,Items.WOOL.white(),Items.BOWL,Items.STRING,Items.SUGAR);
                case SECURITY->List.of(Items.PAPER,Items.COMPASS,Items.BOOK,Items.INK_SAC,Items.LEATHER,Items.IRON_NUGGET);
            };
        }
        return switch(npc.role()){
            case PIZZAIOLO,MICHELE,VENDOR->List.of(Items.WHEAT,Items.BREAD,Items.BOWL,Items.EGG,Items.CARROT,Items.SUGAR);
            case PESCATORE->List.of(Items.COD,Items.SALMON,Items.KELP,Items.STRING,Items.FISHING_ROD,Items.BOWL);
            case TAILOR,BUCATO->List.of(Items.STRING,Items.SHEARS,Items.WOOL.white(),Items.LEATHER,Items.DYE.blue(),Items.PAPER);
            case BUSKER,MUSICISTA->List.of(Items.PAPER,Items.FEATHER,Items.INK_SAC,Items.STRING,Items.STICK,Items.BOOK);
            case MOTOR_DEALER->List.of(Items.IRON_NUGGET,Items.COPPER_INGOT,Items.PAPER,Items.LEATHER,Items.COAL,Items.STRING);
            case ARMORER->List.of(Items.IRON_NUGGET,Items.LEATHER,Items.FLINT,Items.IRON_HELMET,Items.IRON_BOOTS,Items.STRING);
            case CARABINIERE->List.of(Items.PAPER,Items.COMPASS,Items.BOOK,Items.INK_SAC,Items.LEATHER,Items.IRON_NUGGET);
            case PRESEPAIO,GUARDIANO_PRESEPE,CERTOSA_RESTORER->List.of(Items.CLAY_BALL,Items.BRICK,Items.STICK,Items.BRUSH,Items.DYE.white(),Items.PAPER);
            case CARTOMANTE->List.of(Items.PAPER,Items.AMETHYST_SHARD,Items.FEATHER,Items.INK_SAC,Items.BOOK,Items.STRING);
            case EMPLOYMENT_CLERK,FOOTBALL_CLERK,CERTOSA_GUIDE->List.of(Items.PAPER,Items.BOOK,Items.FEATHER,Items.INK_SAC,Items.MAP,Items.LEATHER);
            case PEPPE_FETISH->List.of(Napuli.CALZINI_SPORCHI,Items.LEATHER,Items.STRING,Items.WOOL.white(),Items.PAPER,Items.SHEARS);
            case NONNA,FAMILY_MOTHER->List.of(Items.BREAD,Items.COOKIE,Items.WHEAT,Items.STRING,Items.WOOL.white(),Items.APPLE);
            default->List.of(Items.PAPER,Items.APPLE,Items.BREAD,Items.LEATHER,Items.STRING,Items.BOOK);
        };
    }
    private NpcBelongings(){}
}
