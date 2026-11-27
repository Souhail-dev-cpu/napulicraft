package dev.napuli;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import static dev.napuli.CityText.t;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.block.*;

/** Player careers are separate per trade. Receipts, tools, rank and upgrades survive death and restart. */
final class WorkCareer {
    record Career(int xp,int rank,int tier,int shifts,int earned,int serial,boolean issued){
        static final Codec<Career> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("xp").forGetter(Career::xp),Codec.INT.fieldOf("rank").forGetter(Career::rank),Codec.INT.fieldOf("tier").forGetter(Career::tier),Codec.INT.fieldOf("shifts").forGetter(Career::shifts),Codec.INT.fieldOf("earned").forGetter(Career::earned),Codec.INT.fieldOf("serial").forGetter(Career::serial),Codec.BOOL.fieldOf("issued").forGetter(Career::issued)).apply(i,Career::new));
        static final Career NEW=new Career(0,0,0,0,0,0,false);
    }
    static final AttachmentType<Map<String,Career>> CAREERS=AttachmentRegistry.<Map<String,Career>>builder().persistent(Codec.unboundedMap(Codec.STRING,Career.CODEC)).copyOnDeath().buildAndRegister(Napuli.id("player_work_careers"));
    static final AttachmentType<Map<String,BlockPos>> STATIONS=AttachmentRegistry.<Map<String,BlockPos>>builder().persistent(Codec.unboundedMap(Codec.STRING,BlockPos.CODEC)).buildAndRegister(Napuli.id("career_workstations"));
    static Career get(ServerPlayer p,NapoliEmployment.Contract c){return p.getAttachedOrElse(CAREERS,Map.of()).getOrDefault(c.id(),Career.NEW);}
    static void put(ServerPlayer p,NapoliEmployment.Contract c,Career next){var all=new HashMap<>(p.getAttachedOrElse(CAREERS,Map.of()));all.put(c.id(),next);p.setAttached(CAREERS,Map.copyOf(all));}
    static String rank(int level){return new String[]{"Apprendista","Qualificato","Specialista","Maestro"}[Math.clamp(level,0,3)];}
    static int threshold(int next){return new int[]{0,60,180,400}[Math.clamp(next,0,3)];}
    static int hourly(ServerPlayer p,NapoliEmployment.Contract c){return c.job.wage+get(p,c).rank()*4;}
    static int toolCost(Career career){return career.tier()==0?30:90;}
    static Item material(Career career){return career.tier()==0?Items.DIAMOND:Items.NETHERITE_INGOT;}
    static Item tool(NapoliEmployment.Contract c,int tier){return switch(c){
        case CARPENTER->tier==0?Items.IRON_AXE:tier==1?Items.DIAMOND_AXE:Items.NETHERITE_AXE;
        case BAKER,MERCHANT->tier==0?Items.IRON_HOE:tier==1?Items.DIAMOND_HOE:Items.NETHERITE_HOE;
        case GARDENER->tier==0?Items.IRON_SHOVEL:tier==1?Items.DIAMOND_SHOVEL:Items.NETHERITE_SHOVEL;
        case PORTER,COURIER->tier==0?Items.IRON_BOOTS:tier==1?Items.DIAMOND_BOOTS:Items.NETHERITE_BOOTS;
        case FISHER->Items.FISHING_ROD;case CLEANER->Items.BRUSH;
    };}
    private static ItemStack equipment(ServerPlayer p,NapoliEmployment.Contract c,Career career){
        var stack=new ItemStack(tool(c,career.tier()));var tag=new CompoundTag();tag.putString("career_owner",p.getUUID().toString());tag.putString("career_job",c.id());tag.putInt("career_serial",career.serial());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,t("career.tool_name",c.nameLabel(),t("work.rank."+(career.tier()+1))));
        stack.set(DataComponents.LORE,new ItemLore(List.of(t("career.assigned",p.getName()),t("work.station_help"))));
        if((c==NapoliEmployment.Contract.FISHER||c==NapoliEmployment.Contract.CLEANER)&&career.tier()>0)
            stack.set(DataComponents.MAX_DAMAGE,stack.getMaxDamage()*(career.tier()+1));
        return stack;
    }
    static boolean owned(ServerPlayer p,NapoliEmployment.Contract c,ItemStack stack){var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();return data.getStringOr("career_owner","").equals(p.getUUID().toString())&&data.getStringOr("career_job","").equals(c.id())&&data.getIntOr("career_serial",-1)==get(p,c).serial();}
    static int toolSlot(ServerPlayer p,NapoliEmployment.Contract c){for(int i=0;i<p.getInventory().getContainerSize();i++)if(owned(p,c,p.getInventory().getItem(i)))return i;return -1;}
    static boolean begin(ServerPlayer p,NapoliEmployment.Contract c){var career=get(p,c);if(career.issued())return true;
        var stack=equipment(p,c,career);if(!p.getInventory().add(stack)){tell(p,t("career.free_slot"));return false;}
        put(p,c,new Career(career.xp(),career.rank(),career.tier(),career.shifts(),career.earned(),career.serial(),true));
        tell(p,t("career.issued"));return true;
    }
    static void completed(ServerPlayer p,NapoliEmployment.Contract c,int hours,int pay){var a=get(p,c);put(p,c,new Career(Math.min(1000000,a.xp()+20+hours*5),a.rank(),a.tier(),a.shifts()+1,Math.min(100000000,a.earned()+pay),a.serial(),a.issued()));}
    static BlockPos station(ServerLevel city,NapoliEmployment.Contract c){return city.getAttachedOrElse(STATIONS,Map.of()).getOrDefault(c.id(),c.clock.east(5));}
    static void fixtures(ServerLevel city){var all=new HashMap<>(city.getAttachedOrElse(STATIONS,Map.of()));
        for(var c:NapoliEmployment.Contract.values()){
            if(all.containsKey(c.id())||!city.hasChunkAt(c.clock)||city.players().stream().noneMatch(p->c.clock.distToCenterSqr(p.position())<48*48))continue;
            var candidates=new ArrayList<BlockPos>(List.of(c.clock.east(5),c.clock.west(5),c.clock.south(5),c.clock.north(5),c.clock.offset(5,0,3),c.clock.offset(-5,0,3)));
            for(int radius=2;radius<=10;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(Math.max(Math.abs(dx),Math.abs(dz))==radius)for(int dy:new int[]{0,1,-1})candidates.add(c.clock.offset(dx,dy,dz));
            for(var candidate:candidates){
                if(!city.hasChunkAt(candidate)||CityProperty.playerPlaced(city,candidate)||!city.isEmptyBlock(candidate)||!city.isEmptyBlock(candidate.above())||city.getBlockState(candidate.below()).getCollisionShape(city,candidate.below()).isEmpty())continue;
                if(!city.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(candidate).inflate(.2,1,.2)).isEmpty())continue;
                if(CityLivingHomes.all().stream().anyMatch(h->h.contains(candidate)&&NapoliProperties.owner(city,h)!=null))continue;
                city.setBlock(candidate,Blocks.SMITHING_TABLE.defaultBlockState(),2);all.put(c.id(),candidate);
                if(c==NapoliEmployment.Contract.GARDENER)for(int i=0;i<6;i++){var plant=new BlockPos(-225+i*2,64,56);var ground=plant.below();var s=city.getBlockState(ground);if(!CityProperty.playerPlaced(city,plant)&&!CityProperty.playerPlaced(city,ground)&&city.isEmptyBlock(plant)&&(s.is(Blocks.STONE_BRICKS)||s.is(Blocks.SMOOTH_STONE)||s.is(Blocks.GRASS_BLOCK)))city.setBlock(ground,Blocks.GRASS_BLOCK.defaultBlockState(),2);}
                if(city.isEmptyBlock(candidate.above())&&!CityProperty.playerPlaced(city,candidate.above()))NapoliEmploymentMap.sign(city,candidate.above(),"CARRIERA",c.nameLabel().getString(),"Attrezzi e ranghi","Tasto destro");break;
            }
        }
        city.setAttached(STATIONS,Map.copyOf(all));
    }
    static NapoliEmployment.Contract selected(ServerPlayer p){var state=p.getAttached(NapoliEmployment.WORK);return state==null||FootballEmployment.hasContract(p)?null:NapoliEmployment.find(state.contract());}
    static boolean near(ServerPlayer p,NapoliEmployment.Contract c){return p.level().dimension()==NapoliMap.LEVEL&&p.isAlive()&&!p.isSpectator()&&!Carabinieri.isJailed(p)&&p.level().getAttachedOrElse(STATIONS,Map.of()).containsKey(c.id())&&p.level().getBlockState(station(p.level(),c)).is(Blocks.SMITHING_TABLE)&&station(p.level(),c).distToCenterSqr(p.position())<=36;}
    static int promote(ServerPlayer p){var c=selected(p);return c==null||!near(p,c)?0:promoteAt(p,c);}
    static int promoteAt(ServerPlayer p,NapoliEmployment.Contract c){var a=get(p,c);if(a.rank()>=3)return 0;int next=a.rank()+1,cost=next*20;
        if(a.xp()<threshold(next)||a.shifts()<next*2||NapoliStreet.cash(p)<cost){tell(p,t("career.requirements",t("work.rank."+next),threshold(next),next*2,cost));return 0;}
        NapoliStreet.credit(p,-cost,"career",c.id());WorkEconomy.revenue(p.level(),cost);put(p,c,new Career(a.xp(),next,a.tier(),a.shifts(),a.earned(),a.serial(),a.issued()));tell(p,t("career.promoted",t("work.rank."+next),hourly(p,c)));return 1;
    }
    static int upgrade(ServerPlayer p){var c=selected(p);return c==null||!near(p,c)?0:upgradeAt(p,c);}
    static int upgradeAt(ServerPlayer p,NapoliEmployment.Contract c){var a=get(p,c);int slot=toolSlot(p,c);if(a.tier()>=2||slot<0)return 0;
        int cost=toolCost(a);var material=material(a);
        if(a.rank()<a.tier()+1||NapoliStreet.cash(p)<cost||WorkResources.count(p,material)<1){tell(p,t("work.upgrade_cost",cost,material.getDefaultInstance().getHoverName(),t("work.rank."+(a.tier()+1))));return 0;}
        var previous=p.getInventory().getItem(slot);var nextCareer=new Career(a.xp(),a.rank(),a.tier()+1,a.shifts(),a.earned(),a.serial(),true);var upgraded=equipment(p,c,nextCareer);
        if(previous.has(DataComponents.ENCHANTMENTS))upgraded.set(DataComponents.ENCHANTMENTS,previous.get(DataComponents.ENCHANTMENTS));
        if(previous.isDamageableItem()&&upgraded.isDamageableItem())upgraded.setDamageValue((int)((double)previous.getDamageValue()/previous.getMaxDamage()*upgraded.getMaxDamage()));
        if(!CityEconomy.take(p,material,1))return 0;NapoliStreet.credit(p,-cost,"career",c.id());WorkEconomy.revenue(p.level(),cost);p.getInventory().setItem(slot,upgraded);put(p,c,nextCareer);tell(p,t("career.upgraded",nextCareer.tier()+1));return 1;
    }
    static int recover(ServerPlayer p){var c=selected(p);if(c==null||!near(p,c))return 0;var a=get(p,c);if(!a.issued())return begin(p,c)?1:0;if(toolSlot(p,c)>=0){tell(p,t("career.have_tool"));return 0;}
        int cost=6+a.tier()*8;if(NapoliStreet.cash(p)<cost)return 0;var next=new Career(a.xp(),a.rank(),a.tier(),a.shifts(),a.earned(),a.serial()+1,true);var tool=equipment(p,c,next);
        if(!p.getInventory().add(tool))return 0;NapoliStreet.credit(p,-cost,"career",c.id());WorkEconomy.revenue(p.level(),cost);put(p,c,next);tell(p,t("career.replaced",cost));return 1;
    }
    static int timber(ServerPlayer p){var c=selected(p);if(c!=NapoliEmployment.Contract.CARPENTER||!near(p,c)||WorkResources.count(p,Items.OAK_LOG)<1||CityOccupations.supply(p.level(),"timber")>=256||WorkEconomy.fund(p.level())<2)return 0;
        if(!NapoliStreet.canCredit(p,2)){tell(p,t("wallet.full",NapoliStreet.MAX_CASH));return 0;}
        if(!CityEconomy.take(p,Items.OAK_LOG,1))return 0;WorkEconomy.wage(p,2);CityOccupations.supply(p.level(),"timber",CityOccupations.supply(p.level(),"timber")+1);tell(p,t("career.timber"));return 1;}
    static int repair(ServerPlayer p){var c=selected(p);if(c==null||!near(p,c))return 0;int slot=toolSlot(p,c);if(slot<0)return 0;var stack=p.getInventory().getItem(slot);if(!stack.isDamaged())return 0;int cost=3+get(p,c).tier()*3;
        if(NapoliStreet.cash(p)<cost)return 0;NapoliStreet.credit(p,-cost,"career",c.id());WorkEconomy.revenue(p.level(),cost);stack.setDamageValue(0);tell(p,t("career.repaired",cost));return 1;
    }
    static int menu(ServerPlayer p){return CompanionWork.menu(p);}
    private static void tell(ServerPlayer p,Component text){p.sendSystemMessage(text);}
    private static void tell(ServerPlayer p,String text){p.sendSystemMessage(Component.literal(text));}
    static void register(){
        CommandRegistrationCallback.EVENT.register((d,a,e)->{var root=Commands.literal("lavoro");
            root.then(Commands.literal("carriera").executes(c->menu(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("rango").executes(c->promote(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("potenzia").executes(c->upgrade(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("attrezzi").executes(c->recover(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("ripara").executes(c->repair(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("rifornisci").executes(c->timber(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("risorse").executes(c->WorkResources.pickup(c.getSource().getPlayerOrException())));
            root.then(Commands.literal("chiudi").executes(c->1));d.register(root);
        });
        UseBlockCallback.EVENT.register((p,l,hand,hit)->{if(!(p instanceof ServerPlayer player)||hand!=InteractionHand.MAIN_HAND||l.dimension()!=NapoliMap.LEVEL)return InteractionResult.PASS;
            for(var entry:player.level().getAttachedOrElse(STATIONS,Map.of()).entrySet())if(hit.getBlockPos().equals(entry.getValue())||hit.getBlockPos().equals(entry.getValue().above())){
                var selected=selected(player);if(selected!=null&&selected.id().equals(entry.getKey()))menu(player);else tell(player,"Questo è il banco carriera di un altro mestiere.");return InteractionResult.SUCCESS;
            }return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%100==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)fixtures(city);}});
    }
    private WorkCareer(){}
}
