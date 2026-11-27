package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.LevelChunk;

/** Recognizable Neapolitan facades and usable interiors, upgraded one loaded chunk at a time. */
final class CityArchitecture {
    static final AttachmentType<Boolean> FITTED=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).buildAndRegister(Napuli.id("home_architecture_v1"));
    private final ServerLevel city;
    private final LevelChunk chunk;
    private final Set<BlockPos> passages=new HashSet<>();
    private CityArchitecture(ServerLevel city,LevelChunk chunk){this.city=city;this.chunk=chunk;}
    static int storey(NapoliMergellina.House h){return h.style()<0?4:5;}
    static int roof(NapoliMergellina.House h){return h.ground()+h.floors()*storey(h);}
    static void register(){}
    static void fit(ServerLevel city,LevelChunk chunk){
        if(chunk.getAttachedOrElse(FITTED,false)||CityProperty.playerBuiltInChunk(city,chunk.getPos()))return;
        if(NapoliAtlas.ORIGINAL.contains(chunk.getPos().getMiddleBlockX(),chunk.getPos().getMiddleBlockZ())&&!NapoliInteriors.upgradedWorkplaces(city))return;
        if(!NapoliAtlas.ORIGINAL.contains(chunk.getPos().getMiddleBlockX(),chunk.getPos().getMiddleBlockZ())&&NapoliExpansion.revision(chunk)<1)return;
        var builder=new CityArchitecture(city,chunk);
        for(var home:CityLivingHomes.inChunk(chunk.getPos()))builder.house(home);
        for(var home:CityProperty.classicHomes())builder.classic(home);
        chunk.setAttached(FITTED,true);
    }
    static void finishNew(ServerLevel city,NapoliMergellina.House home){
        for(int x=(home.x()-1)>>4;x<=(home.x()+home.width())>>4;x++)for(int z=(home.z()-1)>>4;z<=(home.z()+home.depth()+1)>>4;z++){
            var chunk=city.getChunkSource().getChunkNow(x,z);if(chunk!=null&&!CityProperty.playerBuiltInChunk(city,chunk.getPos()))new CityArchitecture(city,chunk).house(home);
        }
    }
    private boolean ours(BlockPos p){return ChunkPos.containing(p).equals(chunk.getPos())&&!CityProperty.playerPlaced(city,p)&&chunk.getBlockEntity(p)==null;}
    private boolean facade(BlockState state){return state.is(Blocks.SMOOTH_SANDSTONE)||state.is(Blocks.CUT_SANDSTONE)||state.is(Blocks.TERRACOTTA)||state.is(Blocks.BRICKS)||state.is(Blocks.CALCITE)
            ||state.is(CityBlocks.vanilla("yellow_terracotta"))||state.is(CityBlocks.vanilla("pink_terracotta"))||state.is(CityBlocks.vanilla("white_terracotta"))||state.is(CityBlocks.vanilla("orange_terracotta"))
            ||state.is(CityMaterials.OCHRE)||state.is(CityMaterials.CORAL);}
    private boolean exists(NapoliMergellina.House h){
        int x=Math.clamp(h.x()+h.width()/2,chunk.getPos().getMinBlockX(),chunk.getPos().getMaxBlockX());
        int z=Math.clamp(h.z()+h.depth()/2,chunk.getPos().getMinBlockZ(),chunk.getPos().getMaxBlockZ());
        var base=chunk.getBlockState(new BlockPos(x,h.ground()-1,z));var ceiling=chunk.getBlockState(new BlockPos(x,h.ground()+storey(h)-1,z));
        return (base.is(Blocks.SPRUCE_PLANKS)||base.is(Blocks.STONE_BRICKS)||facade(base))&&(ceiling.is(Blocks.SPRUCE_PLANKS)||ceiling.is(Blocks.TUFF_BRICKS)||facade(ceiling)||ceiling.is(CityMaterials.MAJOLICA));
    }
    private void house(NapoliMergellina.House h){
        if(!exists(h))return;
        passages.clear();for(var point:CityLivingHomes.points(h)){passages.add(point.stand());passages.add(point.stand().above());}
        int g=h.ground(),top=roof(h),step=storey(h);Block paint=CityMaterials.facade(h.id().hashCode());
        for(int x=Math.max(h.x(),chunk.getPos().getMinBlockX());x<=Math.min(h.x()+h.width()-1,chunk.getPos().getMaxBlockX());x++)
            for(int z=Math.max(h.z(),chunk.getPos().getMinBlockZ());z<=Math.min(h.z()+h.depth()-1,chunk.getPos().getMaxBlockZ());z++){
                boolean edge=x==h.x()||x==h.x()+h.width()-1||z==h.z()||z==h.z()+h.depth()-1;
                if(edge)for(int y=g;y<top;y++){
                    var p=new BlockPos(x,y,z);if(!ours(p))continue;var old=chunk.getBlockState(p);
                    if(facade(old)||(y-g)%step==step-1&&old.is(Blocks.SPRUCE_PLANKS)){
                        Block material=y==g?Blocks.TUFF_BRICKS:(y-g)%step==step-1?Blocks.SMOOTH_SANDSTONE:paint;
                        city.setBlock(p,material.defaultBlockState(),Block.UPDATE_CLIENTS);
                    }
                }
                var tile=new BlockPos(x,top,z);var state=chunk.getBlockState(tile);
                if(ours(tile)&&(state.is(Blocks.BRICK_SLAB)||state.is(Blocks.SMOOTH_STONE_SLAB)||state.is(CityMaterials.COPPI_SLAB))){
                    city.setBlock(tile,edge?CityMaterials.COPPI.defaultBlockState():CityMaterials.COPPI_SLAB.defaultBlockState(),Block.UPDATE_CLIENTS);
                    if(edge)empty(tile.above(),Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
        balcony(h);windows(h);
        var front=h.entrance().above(2);if(city.hasChunkAt(front.north())&&city.getBlockState(front.north()).isFaceSturdy(city,front.north(),Direction.SOUTH))
            empty(front,Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,Direction.SOUTH));
        // Ground-floor ceramic kitchen flooring uses existing solid authored floors.
        for(int x=2;x<=5;x++)for(int z=2;z<=7;z++)floor(new BlockPos(h.x()+x,g-1,h.z()+z));
        if(h.id().startsWith("atlas_"))for(int level=0;level<h.floors();level++)rooms(h,g+level*5,level>0);
        if(h.style()>=0){
            var ladder=h.id().startsWith("atlas_")?new BlockPos(h.x()+1,top-1,h.z()+h.depth()-3):new BlockPos(h.x()+1,top-1,h.z()+1);
            var hatch=ladder.above();if(ours(hatch)&&chunk.getBlockState(ladder).is(Blocks.LADDER)&&chunk.getBlockState(hatch).is(CityMaterials.COPPI_SLAB))
                city.setBlock(hatch,Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING,Direction.SOUTH).setValue(TrapDoorBlock.HALF,Half.BOTTOM),Block.UPDATE_CLIENTS);
        }
    }
    private void windows(NapoliMergellina.House h){
        int top=roof(h);for(int level=0;level<h.floors();level++)for(int dx=2;dx<h.width()-2;dx++)for(int dz:new int[]{0,h.depth()-1}){
            var window=new BlockPos(h.x()+dx,h.ground()+level*storey(h)+1,h.z()+dz);
            if(!ours(window))continue;var state=chunk.getBlockState(window);if(!state.is(Blocks.GLASS)&&!state.is(Blocks.GLASS_PANE))continue;
            for(int side:new int[]{-1,1})for(int y=window.getY();y<=Math.min(top-1,window.getY()+1);y++){
                var shutter=new BlockPos(window.getX()+side,y,window.getZ());if(ours(shutter)&&facade(chunk.getBlockState(shutter)))city.setBlock(shutter,Blocks.DARK_OAK_PLANKS.defaultBlockState(),Block.UPDATE_CLIENTS);
            }
        }
    }
    private void balcony(NapoliMergellina.House h){
        int y=h.ground()+storey(h);var exit=new BlockPos(h.x()+h.width()/2,y,h.z()+h.depth()-1);
        if(h.style()>=0&&!h.id().startsWith("atlas_"))door(exit,Direction.SOUTH);
        for(int x=h.x()+1;x<h.x()+h.width()-1;x++)for(int z=h.z()+h.depth();z<=h.z()+h.depth()+1;z++){
            var pos=new BlockPos(x,y-1,z);if(ours(pos)&&chunk.getBlockState(pos).is(Blocks.SMOOTH_SANDSTONE_SLAB))
                city.setBlock(pos,Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP),Block.UPDATE_CLIENTS);
        }
    }
    private void rooms(NapoliMergellina.House h,int y,boolean upstairs){
        // Two-block doorways connect the bathroom, bedrooms and the common hall.
        if(!upstairs)for(int z=1;z<=10;z++)for(int dy=0;dy<=3;dy++){
            partition(new BlockPos(h.x()+6,y+dy,h.z()+z));partition(new BlockPos(h.x()+10,y+dy,h.z()+z));
            if(z>=2)partition(new BlockPos(h.x()+15,y+dy,h.z()+z));
        }
        if(!upstairs){door(new BlockPos(h.x()+6,y,h.z()+4),Direction.EAST);door(new BlockPos(h.x()+6,y,h.z()+7),Direction.EAST);door(new BlockPos(h.x()+15,y,h.z()+9),Direction.EAST);}
        door(new BlockPos(h.x()+10,y,h.z()+9),Direction.EAST);
        for(int x=7;x<=9;x++)for(int z=2;z<=9;z++)floor(new BlockPos(h.x()+x,y-1,h.z()+z));
        if(upstairs){
            // Preserve the furnished apartment layout and its existing beds, carpets and staircase.
            door(new BlockPos(h.x()+17,y,h.z()+9),Direction.EAST);
            door(new BlockPos(h.x()+11,y,h.z()+14),Direction.SOUTH);
            supported(new BlockPos(h.x()+8,y,h.z()+5),NapoliFurniture.WC);
            supported(new BlockPos(h.x()+13,y,h.z()+18),NapoliFurniture.COFFEE_TABLE);
            supported(new BlockPos(h.x()+12,y,h.z()+19),NapoliFurniture.DIVANO);
            supported(new BlockPos(h.x()+18,y,h.z()+3),Blocks.BARREL);
            supported(new BlockPos(h.x()+18,y+1,h.z()+3),NapoliFurniture.LAMP);
        }
    }
    private void partition(BlockPos pos){if(passages.contains(pos))return;if(ours(pos)){var old=chunk.getBlockState(pos);if(old.isAir()||old.is(Blocks.CALCITE))city.setBlock(pos,Blocks.CALCITE.defaultBlockState(),Block.UPDATE_CLIENTS);}}
    private void door(BlockPos lower,Direction facing){
        var upper=lower.above();if(!ours(lower)||!ours(upper))return;
        var a=chunk.getBlockState(lower);var b=chunk.getBlockState(upper);
        if(!(a.isAir()||a.is(Blocks.CALCITE)||facade(a)||a.is(Blocks.GLASS)||a.is(Blocks.GLASS_PANE))||!(b.isAir()||b.is(Blocks.CALCITE)||facade(b)||b.is(Blocks.GLASS)||b.is(Blocks.GLASS_PANE)))return;
        var state=Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING,facing);
        city.setBlock(lower,state.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),Block.UPDATE_CLIENTS);
        city.setBlock(upper,state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS);
    }
    private void floor(BlockPos pos){if(ours(pos)&&chunk.getBlockState(pos).is(Blocks.SPRUCE_PLANKS))city.setBlock(pos,CityMaterials.MAJOLICA.defaultBlockState(),Block.UPDATE_CLIENTS);}
    private void supported(BlockPos pos,Block block){if(ours(pos)&&city.getBlockState(pos.below()).isFaceSturdy(city,pos.below(),Direction.UP))empty(pos,block.defaultBlockState());}
    private void empty(BlockPos pos,BlockState state){if(ours(pos)&&chunk.getBlockState(pos).isAir()&&!passages.contains(pos))city.setBlock(pos,state,Block.UPDATE_CLIENTS);}
    private void classic(CityProperty.Home h){
        var bounds=chunk.getPos();int minX=Math.max(h.min().getX(),bounds.getMinBlockX()),maxX=Math.min(h.max().getX(),bounds.getMaxBlockX()),minZ=Math.max(h.min().getZ(),bounds.getMinBlockZ()),maxZ=Math.min(h.max().getZ(),bounds.getMaxBlockZ());
        if(minX>maxX||minZ>maxZ)return;var paint=CityMaterials.facade(h.min().hashCode());
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(x==h.min().getX()||x==h.max().getX()||z==h.min().getZ()||z==h.max().getZ())
            for(int y=Math.max(64,h.min().getY()+1);y<=h.max().getY();y++){var pos=new BlockPos(x,y,z);if(ours(pos)&&facade(chunk.getBlockState(pos)))city.setBlock(pos,paint.defaultBlockState(),Block.UPDATE_CLIENTS);}
    }
    private CityArchitecture(){throw new AssertionError();}
}
