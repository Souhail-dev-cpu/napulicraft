package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;

/** A nearby renewable work yard. Only authored trunks regrow; player construction is never erased. */
final class WorkResources {
    static final AttachmentType<Boolean> FITTED=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).buildAndRegister(Napuli.id("work_yard_v1"));
    static final AttachmentType<Map<String,Long>> CUT=AttachmentRegistry.<Map<String,Long>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.LONG)).buildAndRegister(Napuli.id("work_trees_cut"));
    static final AttachmentType<Map<String,Long>> PARCELS=AttachmentRegistry.<Map<String,Long>>builder().persistent(Codec.unboundedMap(Codec.STRING,Codec.LONG)).buildAndRegister(Napuli.id("employment_parcels_issued"));
    static final BlockPos DEPOT=CityEconomy.DEPOT;
    static boolean reserved(int x,int z){return x>=-567&&x<=-545&&z>=-12&&z<=68;}
    static List<BlockPos> trees(){var all=new ArrayList<BlockPos>();for(int x:new int[]{-565,-549})for(int z:new int[]{15,27,55})all.add(new BlockPos(x,NapoliExpansion.height(x,z)+1,z));return List.copyOf(all);}
    static BlockPos farm(){return new BlockPos(-550,NapoliExpansion.height(-550,-7)+1,-7);}
    static BlockPos bench(){return new BlockPos(-564,NapoliExpansion.height(-564,54)+1,54);}
    static int count(ServerPlayer p,Item item){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))n+=p.getInventory().getItem(i).getCount();return n;}
    static BlockPos target(ServerPlayer p,NapoliEmployment.Contract c){
        if(c==NapoliEmployment.Contract.CARPENTER&&count(p,Items.OAK_PLANKS)<c.quantity()){
            if(count(p,Items.OAK_LOG)>0)return bench().south();
            return trees().stream().filter(t->!p.level().hasChunkAt(t)||p.level().getBlockState(t).is(Blocks.OAK_LOG)).min(Comparator.comparingDouble(t->t.distToCenterSqr(p.position()))).orElse(bench()).south();
        }
        if((c==NapoliEmployment.Contract.BAKER&&count(p,Items.BREAD)<c.quantity())||(c==NapoliEmployment.Contract.MERCHANT&&count(p,Items.WHEAT)<c.quantity())){
            if(c==NapoliEmployment.Contract.BAKER&&count(p,Items.WHEAT)>=3)return bench().south();
            return farm().south(4);
        }
        if(c==NapoliEmployment.Contract.FISHER&&count(p,Items.COD)<c.quantity())return c.task.east();
        if(c==NapoliEmployment.Contract.GARDENER&&count(p,Items.OAK_SAPLING)<c.quantity())return bench().south();
        if((c==NapoliEmployment.Contract.PORTER||c==NapoliEmployment.Contract.COURIER)&&!hasParcel(p,c))return DEPOT.south();
        return null;
    }
    static boolean hasParcel(ServerPlayer p,NapoliEmployment.Contract c){for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(stack.is(Items.PAPER)&&tag.getStringOr("work_owner","").equals(p.getUUID().toString())&&tag.getStringOr("work_contract","").equals(c.id())&&tag.getLongOr("work_day",-1)==NapoliBroadcast.day(p.level())&&tag.getIntOr("work_sequence",0)==NapoliEmployment.progress(p))return true;}return false;}
    static boolean consumeParcel(ServerPlayer p,NapoliEmployment.Contract c){for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(stack.is(Items.PAPER)&&tag.getStringOr("work_owner","").equals(p.getUUID().toString())&&tag.getStringOr("work_contract","").equals(c.id())&&tag.getLongOr("work_day",-1)==NapoliBroadcast.day(p.level())&&tag.getIntOr("work_sequence",0)==NapoliEmployment.progress(p)){stack.shrink(1);return true;}}return false;}
    static int pickup(ServerPlayer p){if(p.level().dimension()!=NapoliMap.LEVEL)return 0;return processPickup(p);}
    static int processPickup(ServerPlayer p){var work=p.getAttached(NapoliEmployment.WORK);if(work==null||work.stage()<1||work.day()!=NapoliBroadcast.day(p.level()))return 0;var c=NapoliEmployment.find(work.contract());if(c==null||p.isSpectator()||Carabinieri.isJailed(p))return 0;
        if(c==NapoliEmployment.Contract.PORTER||c==NapoliEmployment.Contract.COURIER){
            if(DEPOT.distToCenterSqr(p.position())>36||hasParcel(p,c))return 0;String key=p.getUUID()+"/"+c.id()+"/"+NapoliEmployment.progress(p);var receipts=new HashMap<>(p.level().getAttachedOrElse(PARCELS,Map.of()));long day=work.day();
            boolean replacement=receipts.getOrDefault(key,-1L)>=day;if(replacement&&NapoliStreet.cash(p)<3){p.sendSystemMessage(Component.literal("Per sostituire il pacco perso servono 3 €."));return 0;}
            var stack=new ItemStack(Items.PAPER);var tag=new CompoundTag();tag.putString("work_owner",p.getUUID().toString());tag.putString("work_contract",c.id());tag.putLong("work_day",day);tag.putInt("work_sequence",NapoliEmployment.progress(p));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));stack.set(DataComponents.CUSTOM_NAME,Component.literal("Pacco di lavoro · "+p.getName().getString()));
            if(!p.getInventory().add(stack))return 0;if(replacement){NapoliStreet.credit(p,-3,"parcel");WorkEconomy.revenue(p.level(),3);}receipts.put(key,day);p.level().setAttached(PARCELS,Map.copyOf(receipts));NapoliEmployment.recordActivity(p);return 1;
        }
        if(bench().distToCenterSqr(p.position())>36)return 0;
        Item input,output;int quantity,produced;
        if(c==NapoliEmployment.Contract.CARPENTER){input=Items.OAK_LOG;output=Items.OAK_PLANKS;quantity=1;produced=4;}
        else if(c==NapoliEmployment.Contract.BAKER){input=Items.WHEAT;output=Items.BREAD;quantity=3;produced=1;}
        else if(c==NapoliEmployment.Contract.GARDENER){if(count(p,Items.OAK_SAPLING)>=c.quantity()||CityOccupations.supply(p.level(),"saplings")<1)return 0;var stack=new ItemStack(Items.OAK_SAPLING);if(!p.getInventory().add(stack))return 0;CityOccupations.supply(p.level(),"saplings",CityOccupations.supply(p.level(),"saplings")-1);NapoliEmployment.recordActivity(p);return 1;}
        else return 0;
        if(!transform(p,input,quantity,new ItemStack(output,produced)))return 0;
        NapoliEmployment.recordActivity(p);return 1;
    }
    static void fit(ServerLevel city,LevelChunk chunk){
        if(chunk.getAttachedOrElse(FITTED,false)||NapoliExpansion.revision(chunk)<1)return;
        for(var base:trees())if(ChunkPos.containing(base).equals(chunk.getPos())&&!grow(city,base)){var pending=new HashMap<>(city.getAttachedOrElse(CUT,Map.of()));pending.putIfAbsent(base.asLong()+"",-1L);city.setAttached(CUT,Map.copyOf(pending));}
        var farm=farm();for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            var p=farm.offset(dx,0,dz);if(!ChunkPos.containing(p).equals(chunk.getPos())||CityProperty.playerPlaced(city,p)||CityProperty.playerPlaced(city,p.below())||chunk.getBlockEntity(p)!=null)continue;
            int oldGround=NapoliExpansion.height(p.getX(),p.getZ());boolean safe=true;
            for(int y=Math.min(p.getY()-1,oldGround);y<=Math.max(p.getY()+2,oldGround+2);y++){var at=new BlockPos(p.getX(),y,p.getZ());var s=chunk.getBlockState(at);if(CityProperty.playerPlaced(city,at)||chunk.getBlockEntity(at)!=null||!(s.isAir()||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT)||s.is(Blocks.TUFF)||s.is(Blocks.SANDSTONE)||s.is(Blocks.STONE)||s.is(Blocks.FARMLAND)||s.is(Blocks.WHEAT)||s.is(Blocks.WATER))){safe=false;break;}}
            if(!safe)continue;
            for(int y=63;y<p.getY()-1;y++)city.setBlock(new BlockPos(p.getX(),y,p.getZ()),Blocks.DIRT.defaultBlockState(),2);
            for(int y=p.getY();y<=Math.max(p.getY()+2,oldGround+2);y++)city.setBlock(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState(),2);
            if(dx==0&&dz==0)city.setBlock(p.below(),Blocks.WATER.defaultBlockState(),2);
            else{city.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE,7),2);city.setBlock(p,Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7),2);}
        }
        var table=bench();if(ChunkPos.containing(table).equals(chunk.getPos())&&!CityProperty.playerPlaced(city,table)&&chunk.getBlockState(table).isAir())city.setBlock(table,Blocks.CRAFTING_TABLE.defaultBlockState(),2);
        chunk.setAttached(FITTED,true);
    }
    static boolean grow(ServerLevel city,BlockPos base){
        var players=city.players().stream().map(p->p.getBoundingBox()).toList();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=5;y++){
            var p=base.offset(x,y,z);if(!city.hasChunkAt(p))return false;var state=city.getBlockState(p);
            if(CityProperty.playerPlaced(city,p)||city.getBlockEntity(p)!=null)return false;
            boolean written=x==0&&z==0&&y<4||y>=3&&Math.abs(x)+Math.abs(z)<=3&&(x!=0||z!=0||y>=4);
            if(written&&!players.isEmpty()&&players.stream().anyMatch(box->box.intersects(new net.minecraft.world.phys.AABB(p))))return false;
            // The public bench and sloping ground below the crown are not replaced by this tree.
            if(written&&!state.isAir()&&!state.is(Blocks.OAK_LOG)&&!state.is(Blocks.OAK_LEAVES))return false;
        }
        for(int y=0;y<4;y++)city.setBlock(base.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=3;y<=5;y++)if(Math.abs(x)+Math.abs(z)<=3&&(x!=0||z!=0||y>=4))city.setBlock(base.offset(x,y,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
        return true;
    }
    static void regrow(ServerLevel city){regrow(city,NapoliBroadcast.day(city),NapoliBroadcast.time(city));}
    static void regrow(ServerLevel city,long day,long time){var all=new HashMap<>(city.getAttachedOrElse(CUT,Map.of()));
        for(var base:trees()){String key=base.asLong()+"";var due=all.get(key);if(due==null||day<due||day==due&&time<13000||!city.hasChunkAt(base))continue;
            if(grow(city,base)){all.remove(key);if(due>=0)CityOccupations.supply(city,"saplings",CityOccupations.supply(city,"saplings")+2);}}
        for(var entry:List.copyOf(all.entrySet()))if(entry.getKey().startsWith("crop:")){
            long due=entry.getValue();if(day<due||day==due&&time<13000)continue;var at=BlockPos.of(Long.parseLong(entry.getKey().substring(5)));if(!city.hasChunkAt(at)||CityProperty.playerPlaced(city,at)||CityProperty.playerPlaced(city,at.below())||!city.getBlockState(at.below()).is(Blocks.FARMLAND)||(!city.isEmptyBlock(at)&&!city.getBlockState(at).is(Blocks.WHEAT))||CityOccupations.supply(city,"seeds")<1)continue;
            CityOccupations.supply(city,"seeds",CityOccupations.supply(city,"seeds")-1);city.setBlock(at,Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7),2);all.remove(entry.getKey());
        }
        city.setAttached(CUT,Map.copyOf(all));
    }
    static void register(){
        PlayerBlockBreakEvents.AFTER.register((level,player,pos,state,entity)->{if(!(level instanceof ServerLevel city)||level.dimension()!=NapoliMap.LEVEL)return;
            if(reserved(pos.getX(),pos.getZ())&&player instanceof ServerPlayer worker)NapoliEmployment.recordActivity(worker);
            if(state.is(Blocks.WHEAT)&&Math.abs(pos.getX()-farm().getX())<=3&&Math.abs(pos.getZ()-farm().getZ())<=3){var crops=new HashMap<>(city.getAttachedOrElse(CUT,Map.of()));crops.put("crop:"+pos.asLong(),NapoliBroadcast.day(city)+(NapoliBroadcast.time(city)>=13000?1:0));city.setAttached(CUT,Map.copyOf(crops));}
            if(!state.is(Blocks.OAK_LOG)||CityProperty.playerPlaced(city,pos))return;
            for(var base:trees())if(pos.getX()==base.getX()&&pos.getZ()==base.getZ()&&pos.getY()>=base.getY()&&pos.getY()<base.getY()+4){var all=new HashMap<>(city.getAttachedOrElse(CUT,Map.of()));all.put(base.asLong()+"",NapoliBroadcast.day(city)+(NapoliBroadcast.time(city)>=13000?1:0));city.setAttached(CUT,Map.copyOf(all));}
        });
        UseBlockCallback.EVENT.register((p,l,hand,hit)->{if(l.dimension()!=NapoliMap.LEVEL||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            if(hit.getBlockPos().equals(bench())||hit.getBlockPos().distSqr(DEPOT)<4){if(p instanceof ServerPlayer player&&pickup(player)==1)return InteractionResult.SUCCESS;}return InteractionResult.PASS;});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%100!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city!=null)regrow(city);});
    }
    private static boolean transform(ServerPlayer p,Item input,int quantity,ItemStack result){
        var inventory=new ArrayList<ItemStack>();
        for(int i=0;i<p.getInventory().getContainerSize();i++)inventory.add(p.getInventory().getItem(i).copy());
        int remaining=quantity;
        for(var stack:inventory)if(stack.is(input)){
            int used=Math.min(remaining,stack.getCount());stack.shrink(used);remaining-=used;
            if(remaining==0)break;
        }
        // Inputs can also come from the offhand, but outputs use normal storage slots.
        if(remaining>0||!PlayerInventoryPlan.add(inventory.subList(0,36),result))return false;
        for(int i=0;i<inventory.size();i++)p.getInventory().setItem(i,inventory.get(i));
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();return true;
    }
    private WorkResources(){}
}
