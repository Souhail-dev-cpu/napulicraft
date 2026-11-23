package dev.napuli;

import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Modular houses have actual rooms and stable use points, shared by players and the simulation. */
final class CityLivingHomes {
    static final List<NapoliMergellina.House> PUBLIC_HOMES = java.util.stream.IntStream.range(0,28)
            .mapToObj(i->new NapoliMergellina.House("public_"+i,"Corte dei residenti "+(i+1),-534+i%7*27,70+i/7*35,0,Blocks.SMOOTH_SANDSTONE,17,18,2,100+i)).toList();
    private static final class Catalogue {static final List<NapoliMergellina.House> ALL=java.util.stream.Stream.concat(java.util.stream.Stream.concat(NapoliMergellina.PROPERTIES.stream(),PUBLIC_HOMES.stream()),DistrictLife.homes().stream()).toList();}
    static List<NapoliMergellina.House> all() { return Catalogue.ALL; }
    enum Room { KITCHEN, BATHROOM, LIVING, DOUBLE_BEDROOM, CHILD_BEDROOM, STUDY, LAUNDRY, BALCONY }
    enum Furniture { STOVE, FRIDGE, SINK, SHOWER, TOILET, SOFA, TV, BED, WARDROBE, DESK, WASHER, TABLE }
    record UsePoint(Room room, Furniture furniture, BlockPos block, BlockPos stand) {}
    static List<UsePoint> points(NapoliMergellina.House h) {
        boolean expanded=h.id().startsWith("atlas_");int x = h.x(), z = h.z(), upstairs = expanded?64:h.style() >= 0 ? 69 : 68;
        var points=List.of(point(Room.KITCHEN, Furniture.STOVE, x+3,64,z+2, x+3,64,z+3),
                point(Room.KITCHEN,Furniture.FRIDGE,x+2,64,z+2,x+3,64,z+3),
                point(Room.BATHROOM,Furniture.SINK,x+7,64,z+2,x+7,64,z+3),
                point(Room.BATHROOM,Furniture.SHOWER,x+8,64,z+9,x+8,64,z+9),
                point(Room.BATHROOM,Furniture.TOILET,x+8,64,z+5,x+7,64,z+5),
                point(Room.LIVING,Furniture.SOFA,x+2,64,z+7,x+2,64,z+6),
                point(Room.LIVING,Furniture.TV,x+2,65,z+9,x+2,64,z+6),
                point(Room.DOUBLE_BEDROOM,Furniture.BED,x+3,upstairs,z+4,x+3,upstairs,z+5),
                point(Room.DOUBLE_BEDROOM,Furniture.WARDROBE,x+2,upstairs,z+2,x+2,upstairs,z+3),
                point(Room.CHILD_BEDROOM,Furniture.BED,x+7,upstairs,z+4,x+7,upstairs,z+5),
                point(Room.STUDY,Furniture.DESK,x+7,upstairs,z+8,x+7,upstairs,z+7),
                point(Room.LAUNDRY,Furniture.WASHER,x+7,64,z+8,x+7,64,z+7));
        if(!expanded)return points;return points.stream().map(p->{int dx=p.furniture()==Furniture.BED?9:p.furniture()==Furniture.WARDROBE?10:p.furniture()==Furniture.DESK?5:0,dz=p.furniture()==Furniture.BED?3:p.furniture()==Furniture.DESK?5:0;return new UsePoint(p.room(),p.furniture(),p.block().offset(dx,h.ground()-64,dz),p.stand().offset(dx,h.ground()-64,dz+(p.furniture()==Furniture.SHOWER?1:0)));}).toList();
    }
    private static UsePoint point(Room r,Furniture f,int x,int y,int z,int sx,int sy,int sz) {
        return new UsePoint(r,f,new BlockPos(x,y,z),new BlockPos(sx,sy,sz));
    }
    static UsePoint use(NapoliMergellina.House h, Furniture f) {
        return points(h).stream().filter(p -> p.furniture()==f).findFirst().orElseThrow();
    }
    static NapoliMergellina.House find(String id) {
        return all().stream().filter(h -> h.id().equals(id)).findFirst().orElse(null);
    }
    static void build(ServerLevel city) {
        // New lots do not overlap any existing home, landmark or player property.
        fill(city,-540,63,-204,-343,63,-56,Blocks.STONE_BRICKS);
        for (int z=-200; z<=-56; z+=35) fill(city,-540,63,z,-343,63,z+4,Blocks.POLISHED_DEEPSLATE);
        fill(city,-343,63,-200,-335,63,51,Blocks.POLISHED_DEEPSLATE);
        fill(city,-343,63,47,-315,63,51,Blocks.POLISHED_DEEPSLATE);
        fill(city,-343,63,51,-335,63,207,Blocks.POLISHED_DEEPSLATE);
        fill(city,-347,63,-196,-344,63,206,Blocks.STONE_BRICKS);
        for(int z=-196;z<=206;z++)put(city,new BlockPos(-345,64,z),Blocks.RAIL);
        for(var h:PUBLIC_HOMES) {
            fill(city,h.x()-2,63,h.z()-2,h.x()+24,63,h.z()+23,Blocks.STONE_BRICKS);
            buildHouse(city,h);
        }
        for (var h : NapoliMergellina.PROPERTIES) {
            if (h.style()>=0) buildHouse(city,h);
            else furnish(city,h);
        }
        NapoliEmploymentMap.sign(city,new BlockPos(-337,64,44),"RIONE PARTENOPE","35 case / famiglie","Segui la strada");
    }
    static void buildHouse(ServerLevel city, NapoliMergellina.House h) {
        int x=h.x(), z=h.z(), w=h.width(), d=h.depth(), roof=64+h.floors()*5;
        fill(city,x,63,z,x+w-1,63,z+d-1,Blocks.SPRUCE_PLANKS);
        for (int y=64;y<=roof;y++) for (int bx=x;bx<x+w;bx++) for (int bz=z;bz<z+d;bz++) {
            boolean wall=bx==x||bx==x+w-1||bz==z||bz==z+d-1;
            Block b=y==roof? (h.style()%2==0?Blocks.BRICK_SLAB:Blocks.SMOOTH_STONE_SLAB)
                    : (y-64)%5==4?Blocks.SPRUCE_PLANKS:wall?h.wall():Blocks.AIR;
            if (wall && y<roof && (y-64)%5>=1 && (y-64)%5<=2 && (bx-x+bz-z+h.style())%4==1) b=Blocks.GLASS;
            put(city,new BlockPos(bx,y,bz),b);
        }
        put(city,new BlockPos(x+w/2,66,z+d-1),h.wall());
        var door=Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING,Direction.SOUTH);
        city.setBlock(new BlockPos(x+w/2,64,z+d-1),door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER),2);
        city.setBlock(new BlockPos(x+w/2,65,z+d-1),door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
        for (int y=64;y<roof;y++) city.setBlock(new BlockPos(x+1,y,z+1),Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH),Block.UPDATE_CLIENTS);
        // Partition bathroom, the two bedrooms and a study without blocking the hall.
        fill(city,x+6,64,z+1,x+6,67,z+5,Blocks.CALCITE);
        put(city,new BlockPos(x+6,64,z+4),Blocks.AIR); put(city,new BlockPos(x+6,65,z+4),Blocks.AIR);
        fill(city,x+6,64,z+6,x+6,67,z+11,Blocks.CALCITE);
        put(city,new BlockPos(x+6,64,z+7),Blocks.AIR);put(city,new BlockPos(x+6,65,z+7),Blocks.AIR);
        fill(city,x+5,69,z+2,x+5,72,z+6,Blocks.SPRUCE_PLANKS);
        for(int bx=2;bx<w-1;bx+=4) put(city,new BlockPos(x+bx,67,z+d-3),Blocks.LANTERN);
        // Each of the 28 lots varies its footprint, height, partition, garden, roof and balcony.
        int partition=10+h.style()%Math.max(1,w-11);
        fill(city,x+partition,64,z+2,x+partition,66,z+5+h.style()%3,Blocks.BOOKSHELF);
        if(h.style()%7==2) fill(city,x+w-4,64,z+d-4,x+w-2,64,z+d-2,Blocks.MOSS_BLOCK);
        if(h.style()%7==3) fill(city,x+10,64,z+10,x+w-2,64,z+d-2,Blocks.AIR);
        if(h.style()%7==4) fill(city,x+10,64,z+8,x+w-2,64,z+10,Blocks.SMOOTH_STONE);
        for(int bit=0;bit<5;bit++)if((h.style()&(1<<bit))!=0)put(city,new BlockPos(x+w-2,69,z+2+bit*2),Blocks.BOOKSHELF);
        fill(city,x+2,68,z+d,x+w-3,68,z+d+1,Blocks.SMOOTH_SANDSTONE_SLAB);
        for(int bx=x+2;bx<x+w-2;bx++) put(city,new BlockPos(bx,69,z+d+1),Blocks.SPRUCE_FENCE);
        put(city,new BlockPos(x+2,70,z+d),block("light_blue_banner"));
        furnish(city,h);
        CityProperty.recordHome(city,new BlockPos(x,63,z),new BlockPos(x+w-1,roof,z+d+1));
        NapoliEmploymentMap.sign(city,h.sign(),h.name(),"€ "+h.price(),"Acquisto / affitto");
    }
    private static void furnish(ServerLevel city,NapoliMergellina.House h) {
        if(h.style()<0) {
            for(int dx:new int[]{3,4,6,7})for(int dz:new int[]{4,5}) {
                var p=new BlockPos(h.x()+dx,68,h.z()+dz);
                if(city.getBlockState(p).getBlock() instanceof BedBlock)put(city,p,Blocks.AIR);
            }
            int design=NapoliMergellina.PROPERTIES.indexOf(h);
            for(int bit=0;bit<3;bit++)put(city,new BlockPos(h.x()+9,68,h.z()+2+bit*4),(design&(1<<bit))!=0?Blocks.BOOKSHELF:Blocks.AIR);
        }
        for(var p:points(h)) {
            Block block=switch(p.furniture()) {
                case STOVE -> Blocks.SMOKER;
                case FRIDGE,WARDROBE -> Blocks.BARREL;
                case SINK -> Blocks.WATER_CAULDRON;
                case TOILET -> Blocks.QUARTZ_STAIRS;
                case SOFA -> Blocks.SPRUCE_STAIRS;
                case TV -> block("black_concrete");
                case DESK -> Blocks.CRAFTING_TABLE;
                case TABLE -> Blocks.OAK_FENCE;
                case WASHER -> Blocks.DISPENSER;
                case SHOWER,BED -> Blocks.AIR;
            };
            if(p.furniture()==Furniture.SHOWER) NapoliShower.place(city,p.block());
            else if(p.furniture()==Furniture.BED) bed(city,p.block());
            else put(city,p.block(),block);
        }
        BlockPos stove=use(h,Furniture.STOVE).block();
        put(city,stove.east(),Blocks.FURNACE);
        put(city,stove.west().above(),Blocks.IRON_BLOCK);
        put(city,stove.east(2),NapoliKitchen.BLOCKS.get("forno_legna_portatile"));
        put(city,new BlockPos(h.x()+4,64,h.z()+5),Blocks.CRAFTING_TABLE);
        put(city,new BlockPos(h.x()+4,64,h.z()+7),Blocks.OAK_FENCE);
        put(city,new BlockPos(h.x()+4,65,h.z()+7),block("blue_carpet"));
        bed(city,use(h,Furniture.BED).block().east());
        bed(city,new BlockPos(h.x()+8,h.style()>=0?69:68,h.z()+4));
        for(int dx=-1;dx<=1;dx++) put(city,use(h,Furniture.TV).block().east(dx),block("black_concrete"));
    }
    private static void bed(ServerLevel city,BlockPos foot) {
        var state=block("blue_bed").defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);
        city.setBlock(foot,state.setValue(BedBlock.PART,BedPart.FOOT),Block.UPDATE_CLIENTS);
        city.setBlock(foot.north(),state.setValue(BedBlock.PART,BedPart.HEAD),Block.UPDATE_CLIENTS);
    }
    static void register() {
        UseBlockCallback.EVENT.register((player,level,hand,hit)-> {
            if(level.dimension()!=NapoliMap.LEVEL||hand!=InteractionHand.MAIN_HAND||player.isSpectator()) return InteractionResult.PASS;
            for(var h:NapoliMergellina.PROPERTIES) for(var p:points(h)) {
                if(!hit.getBlockPos().equals(p.block())) continue;
                if(p.furniture()!=Furniture.TOILET&&p.furniture()!=Furniture.SINK&&p.furniture()!=Furniture.WASHER&&p.furniture()!=Furniture.SOFA) continue;
                if(player instanceof ServerPlayer sp && NapoliProperties.hasAccess(sp,h)) {
                    boolean sofa=p.furniture()==Furniture.SOFA;
                    if(!sofa) NapoliHygiene.wash(sp,p.block(),Math.min(100,NapoliHygiene.value(sp)+35));
                    else sp.sendOverlayMessage(Component.literal("Ti rilassi sul divano. Shift + clic sulla TV cambia canale."));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
    private static void put(ServerLevel city,BlockPos p,Block b) {
        if(city.getBlockEntity(p) instanceof net.minecraft.world.Container storage && !storage.isEmpty() && !city.getBlockState(p).is(b))return;
        city.setBlock(p,b.defaultBlockState(),Block.UPDATE_CLIENTS);
    }
    private static Block block(String id) { return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id)); }
    private static void fill(ServerLevel city,int x,int y,int z,int ex,int ey,int ez,Block b) {
        for(BlockPos p:BlockPos.betweenClosed(x,y,z,ex,ey,ez)) put(city,p,b);
    }
    private CityLivingHomes() {}
}
