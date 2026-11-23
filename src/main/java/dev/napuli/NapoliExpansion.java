package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Deterministic, chunk-local city construction. Never requests or writes a neighboring chunk. */
public final class NapoliExpansion {
    static final int REVISION=1;
    private static final int BLOCK_SIZE=40;
    static final AttachmentType<Integer> GENERATED=AttachmentRegistry.<Integer>builder().persistent(Codec.INT).buildAndRegister(Napuli.id("atlas_chunk_revision"));
    static final AttachmentType<Boolean> ENTRANCES_FIXED=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).buildAndRegister(Napuli.id("city_entrances_fixed"));
    private static final Map<ServerLevel,Map<Long,LevelChunk>> PENDING=new WeakHashMap<>();
    private static final AttachmentType<Set<String>> WALKERS=AttachmentRegistry.<Set<String>>builder().persistent(Codec.STRING.listOf().xmap(Set::copyOf,List::copyOf)).buildAndRegister(Napuli.id("atlas_walkers"));
    private static final int[][] COAST={{-2048,35},{-1920,65},{-1800,215},{-1700,455},{-1550,475},{-1400,450},{-1200,300},{-970,205},{-650,185},{-544,256},{480,135},{900,180},{1400,285},{1750,390},{2048,475}};
    static boolean writable(int x,int z){return NapoliAtlas.EXPANDED.contains(x,z)&&!NapoliAtlas.ORIGINAL.contains(x,z);}
    static double coast(int x){for(int i=1;i<COAST.length;i++)if(x<=COAST[i][0]){var a=COAST[i-1];var b=COAST[i];return a[1]+(b[1]-a[1])*(double)(x-a[0])/(b[0]-a[0]);}return COAST[COAST.length-1][1];}
    static boolean water(int x,int z){return z>coast(x)&&Math.pow((x+1870)/56.0,2)+Math.pow((z-365)/48.0,2)>1;}
    static int terrain(int x,int z){
        double edge=Math.max(Math.max(NapoliAtlas.ORIGINAL.minX()-x,x-NapoliAtlas.ORIGINAL.maxX()+1),Math.max(NapoliAtlas.ORIGINAL.minZ()-z,z-NapoliAtlas.ORIGINAL.maxZ()+1));
        double hill=hill(x,z,-800,-310,42,420)+hill(x,z,-50,-640,27,240)+hill(x,z,-1480,300,19,260);
        return 63+(int)Math.round(hill*Math.clamp(edge/64,0,1));
    }
    private static double hill(int x,int z,int cx,int cz,int height,int spread){return height*Math.exp(-((double)(x-cx)*(x-cx)+(double)(z-cz)*(z-cz))/(spread*spread));}
    public static int height(int x,int z){
        return CityStreets.adjusted(x,z)?CityStreets.height(x,z):legacyHeight(x,z);
    }
    static int legacyHeight(int x,int z){
        if(NapoliAtlas.ORIGINAL.contains(x,z))return 63;
        for(var p:NapoliAtlas.PLACES)if(!p.original()&&p.near(x,z,7)&&p.kind()!=NapoliAtlas.Kind.PIER)return terrain(p.x(),p.z());
        return water(x,z)?63:terrain(x,z);
    }
    static int revision(LevelChunk chunk){return ((AttachmentTarget)chunk).getAttachedOrElse(GENERATED,0);}
    private static boolean intersects(LevelChunk c){var p=c.getPos();return writable(p.getMinBlockX(),p.getMinBlockZ())||writable(p.getMaxBlockX(),p.getMaxBlockZ());}
    static boolean generate(ServerLevel city,LevelChunk chunk){
        if(!intersects(chunk)||revision(chunk)>=REVISION)return false;
        // Old, unexplored saves may already contain player buildings outside the former town.
        if(CityProperty.playerBuiltInChunk(city,chunk.getPos())||custom(chunk)){
            ((AttachmentTarget)chunk).setAttached(GENERATED,REVISION);
            ((AttachmentTarget)chunk).setAttached(ENTRANCES_FIXED,true);((AttachmentTarget)chunk).setAttached(CityStreets.GRADED,CityStreets.REVISION);return false;
        }
        var w=new Writer(city,chunk);
        for(int x=w.minX;x<=w.maxX;x++)for(int z=w.minZ;z<=w.maxZ;z++){
            if(!writable(x,z))continue;int h=height(x,z);boolean sea=water(x,z);
            boolean promenade=Math.abs(z-coast(x))<5;
            boolean road=nearRoad(x,z,5);
            boolean lane=CityStreets.grid(x,z)||CityStreets.apron(x,z);
            if(sea){w.fill(x,60,z,x,60,z,Blocks.SAND);w.fill(x,61,z,x,63,z,Blocks.WATER);}
            else{w.fill(x,63,z,x,h-1,z,Blocks.TUFF);w.put(x,h,z,promenade?Blocks.SMOOTH_STONE:road||lane?Blocks.POLISHED_ANDESITE:Blocks.GRASS_BLOCK);}
            if(road||promenade||lane&&!sea){
                int floor=sea?65:h;BlockState paving=sea?Blocks.POLISHED_DEEPSLATE.defaultBlockState():CityStreets.paving(x,z);
                w.put(x,floor,z,paving);if(sea&&Math.floorMod(z,12)==0)w.fill(x,60,z,x,64,z,Blocks.STONE_BRICKS);
            }
        }
        // Small blocks with walk-through entrances, upper floors, ladders and usable furniture.
        for(int gx=Math.floorDiv(w.minX,BLOCK_SIZE)-1;gx<=Math.floorDiv(w.maxX,BLOCK_SIZE);gx++)for(int gz=Math.floorDiv(w.minZ,BLOCK_SIZE)-1;gz<=Math.floorDiv(w.maxZ,BLOCK_SIZE);gz++){
            int x=gx*BLOCK_SIZE+9,z=gz*BLOCK_SIZE+9;
            if(houseAllowed(x,z)){int base=legacyHeight(x+11,z+24);int floors=3+Math.floorMod(gx*31+gz,3);w.house(x,base+1,z,22,24,floors,facade(gx+gz));
                if(w.includes(x,z))CityProperty.recordHome(city,new BlockPos(x,base+1,z),new BlockPos(x+21,base+floors*5+1,z+23));
            }
        }
        for(var p:NapoliAtlas.PLACES)if(!p.original()&&p.near((w.minX+w.maxX)/2,(w.minZ+w.maxZ)/2,12))w.landmark(p);
        // Pocket gardens between blocks, rather than unbuilt sand at the map's edges.
        for(int x=w.minX;x<=w.maxX;x++)for(int z=w.minZ;z<=w.maxZ;z++)if(writable(x,z)&&!water(x,z)&&Math.floorMod(x,BLOCK_SIZE)==36&&Math.floorMod(z,BLOCK_SIZE)==36&&!nearPlace(x,z,15)&&!nearRoad(x,z,12))w.tree(x,height(x,z)+1,z);
        ((AttachmentTarget)chunk).setAttached(GENERATED,REVISION);
        ((AttachmentTarget)chunk).setAttached(ENTRANCES_FIXED,true);
        CityStreets.fit(city,chunk);
        return true;
    }
    private static boolean custom(LevelChunk c){for(int x=c.getPos().getMinBlockX();x<=c.getPos().getMaxBlockX();x++)for(int z=c.getPos().getMinBlockZ();z<=c.getPos().getMaxBlockZ();z++)if(writable(x,z)&&c.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)>63)return true;return !c.getBlockEntities().isEmpty();}
    static boolean nearPlace(int x,int z,int radius){return NapoliAtlas.PLACES.stream().anyMatch(p->p.near(x,z,radius));}
    static boolean nearRoad(int x,int z,int radius){for(var r:NapoliAtlas.ROADS){var a=NapoliAtlas.find(r.from());var b=NapoliAtlas.find(r.to());if(x<Math.min(a.x(),b.x())-radius||x>Math.max(a.x(),b.x())+radius||z<Math.min(a.z(),b.z())-radius||z>Math.max(a.z(),b.z())+radius)continue;if(NapoliAtlas.distance(r,x,z)<radius)return true;}return false;}
    private static boolean clearLanding(int x,int z){for(var p:NapoliAtlas.PLACES)if(p.x()>=x-3&&p.x()<=x+25&&p.approachZ()>=z-3&&p.approachZ()<=z+29)return false;return true;}
    static boolean houseAllowed(int x,int z){return writable(x-4,z-4)&&writable(x+27,z+29)&&!water(x,z)&&!water(x+22,z+24)&&!nearPlace(x+11,z+12,15)&&!nearRoad(x+11,z+12,22)&&clearLanding(x,z)&&Math.abs(z-coast(x))>30&&Math.abs(legacyHeight(x,z)-legacyHeight(x+22,z+24))<12;}
    private static Block facade(int value){return switch(Math.floorMod(value,5)){case 0->Blocks.TERRACOTTA;case 1->block("yellow_terracotta");case 2->block("pink_terracotta");case 3->Blocks.BRICKS;default->Blocks.SMOOTH_SANDSTONE;};}
    private static Block block(String id){return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));}

    /** Repairs only the old doorway template in the supplied loaded chunk, once per save. */
    static int repairEntrances(ServerLevel city,LevelChunk chunk){
        if(((AttachmentTarget)chunk).getAttachedOrElse(ENTRANCES_FIXED,false))return 0;
        int changed=0;var cp=chunk.getPos();
        if(revision(chunk)>=REVISION){
            for(int gx=Math.floorDiv(cp.getMinBlockX(),BLOCK_SIZE)-1;gx<=Math.floorDiv(cp.getMaxBlockX(),BLOCK_SIZE);gx++)
                for(int gz=Math.floorDiv(cp.getMinBlockZ(),BLOCK_SIZE)-1;gz<=Math.floorDiv(cp.getMaxBlockZ(),BLOCK_SIZE);gz++){
                    int x=gx*BLOCK_SIZE+9,z=gz*BLOCK_SIZE+9;
                    if(houseAllowed(x,z))changed+=repairEntrance(city,chunk,new BlockPos(x+11,legacyHeight(x+11,z+24)+1,z+23),facade(gx+gz),true);
                }
        }
        for(var h:CityLivingHomes.all())if(!h.id().startsWith("atlas_")&&h.style()>=0)
            changed+=repairEntrance(city,chunk,h.entrance().north(),h.wall(),false);
        ((AttachmentTarget)chunk).setAttached(ENTRANCES_FIXED,true);
        return changed;
    }

    private static int repairEntrance(ServerLevel city,LevelChunk chunk,BlockPos door,Block wall,boolean wide){
        if(!ChunkPos.containing(door).equals(chunk.getPos()))return 0;
        var lower=chunk.getBlockState(door);var upper=chunk.getBlockState(door.above());
        if(!lower.is(Blocks.OAK_DOOR)||lower.getValue(DoorBlock.HALF)!=DoubleBlockHalf.LOWER
                ||lower.getValue(DoorBlock.FACING)!=Direction.SOUTH||!upper.is(Blocks.OAK_DOOR)
                ||upper.getValue(DoorBlock.HALF)!=DoubleBlockHalf.UPPER
                ||upper.getValue(DoorBlock.FACING)!=lower.getValue(DoorBlock.FACING)
                ||!chunk.getBlockState(door.above(3)).is(wall))return 0;
        if(wide&&(!chunk.getBlockState(door.west(2)).is(wall)||!chunk.getBlockState(door.east(2)).is(wall)))return 0;
        int radius=wide?1:0;
        for(int dx=-radius;dx<=radius;dx++)for(int dy=0;dy<=2;dy++){
            var p=door.offset(dx,dy,0);
            if(!ChunkPos.containing(p).equals(chunk.getPos())||CityProperty.playerPlaced(city,p)||chunk.getBlockEntity(p)!=null)return 0;
            if((dx!=0||dy==2)&&!chunk.getBlockState(p).isAir()&&!chunk.getBlockState(p).is(wall))return 0;
        }
        int changed=0;
        for(int dx=-radius;dx<=radius;dx++)for(int dy=0;dy<=2;dy++)if(dx!=0||dy==2){
            var p=door.offset(dx,dy,0);
            if(chunk.getBlockState(p).isAir()){city.setBlock(p,wall.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);changed++;}
        }
        return changed;
    }
    static void register(){
        // CHUNK_LOAD runs before the full-chunk future is completed. Editing through Level there
        // would wait on that same future; defer until the next server tick and cap the work per tick.
        ServerChunkEvents.CHUNK_LOAD.register((city,chunk,newlyGenerated)->{if(city.dimension()==NapoliMap.LEVEL&&NapoliAtlas.EXPANDED.contains(chunk.getPos().getMinBlockX(),chunk.getPos().getMinBlockZ()))PENDING.computeIfAbsent(city,k->new HashMap<>()).put(chunk.getPos().pack(),chunk);});
        ServerTickEvents.START_SERVER_TICK.register(server->{var city=server.getLevel(NapoliMap.LEVEL);if(city==null)return;var pending=PENDING.get(city);if(pending==null||pending.isEmpty())return;
            var batch=pending.values().stream().sorted(Comparator.comparingDouble(c->city.players().stream().mapToDouble(p->p.blockPosition().distSqr(c.getPos().getMiddleBlockPosition(64))).min().orElse(0))).limit(4).toList();
            for(var chunk:batch){pending.remove(chunk.getPos().pack());if(city.getChunkSource().getChunkNow(chunk.getPos().x(),chunk.getPos().z())==chunk){generate(city,chunk);repairEntrances(city,chunk);DistrictLife.fit(city,chunk);CityRailway.fit(city,chunk);}}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->PENDING.clear());
    }

    private static final class Writer {
        final ServerLevel city;final LevelChunk chunk;final int minX,maxX,minZ,maxZ;
        Writer(ServerLevel city,LevelChunk chunk){this.city=city;this.chunk=chunk;var p=chunk.getPos();minX=p.getMinBlockX();maxX=p.getMaxBlockX();minZ=p.getMinBlockZ();maxZ=p.getMaxBlockZ();}
        boolean includes(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
        void put(int x,int y,int z,Block b){put(x,y,z,b.defaultBlockState());}
        void put(int x,int y,int z,BlockState s){if(!includes(x,z)||!writable(x,z))return;var p=new BlockPos(x,y,z);if(!CityProperty.playerPlaced(city,p))city.setBlock(p,s,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
        void fill(int x,int y,int z,int xx,int yy,int zz,Block b){if(yy<y)return;for(int px=Math.max(x,minX);px<=Math.min(xx,maxX);px++)for(int pz=Math.max(z,minZ);pz<=Math.min(zz,maxZ);pz++)for(int py=y;py<=yy;py++)put(px,py,pz,b);}
        void sign(int x,int y,int z,String... lines){put(x,y,z,Blocks.OAK_SIGN);if(!includes(x,z)||!writable(x,z))return;var pos=new BlockPos(x,y,z);if(chunk.getBlockEntity(pos) instanceof SignBlockEntity sign){var text=SignText.EMPTY.asMutable();for(int i=0;i<Math.min(4,lines.length);i++)text.setLine(i,net.minecraft.network.chat.Component.literal(lines[i]));sign.setText(text.asImmutable(),SignTextSlot.FRONT);sign.setText(text.asImmutable(),SignTextSlot.BACK);sign.setChanged();}}
        void nameSign(int x,int y,int z,String name){var lines=new ArrayList<String>();String line="";for(var word:name.split(" ")){if(!line.isEmpty()&&line.length()+word.length()+1>15){lines.add(line);line="";}line+=line.isEmpty()?word:" "+word;}if(!line.isEmpty())lines.add(line);if(lines.size()<4)lines.add("/napoli luoghi");sign(x,y,z,lines.toArray(String[]::new));}
        void tree(int x,int y,int z){fill(x,y,z,x,y+4,z,Blocks.OAK_LOG);for(int xx=x-3;xx<=x+3;xx++)for(int zz=z-3;zz<=z+3;zz++)if(Math.abs(xx-x)+Math.abs(zz-z)<=4)put(xx,y+5,zz,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));}
        void lamp(int x,int y,int z){fill(x,y,z,x,y+2,z,Blocks.STONE_BRICK_WALL);put(x,y+3,z,Blocks.LANTERN);}
        void house(int x,int y,int z,int width,int depth,int floors,Block wall){
            fill(x,63,z,x+width-1,y-1,z+depth-1,Blocks.STONE_BRICKS);
            fill(x,y,z,x+width-1,y+floors*5,z+depth-1,wall);
            fill(x+1,y,z+1,x+width-2,y+floors*5-1,z+depth-2,Blocks.AIR);
            for(int f=0;f<floors;f++){
                int floor=y+f*5;fill(x+1,floor-1,z+1,x+width-2,floor-1,z+depth-2,Blocks.SPRUCE_PLANKS);
                for(int wx=x+3;wx<x+width-2;wx+=4)for(int wy=floor+1;wy<=floor+2;wy++){put(wx,wy,z,Blocks.GLASS_PANE);put(wx,wy,z+depth-1,Blocks.GLASS_PANE);}
                for(int wz=z+4;wz<z+depth-2;wz+=5)for(int wy=floor+1;wy<=floor+2;wy++){put(x,wy,wz,Blocks.GLASS_PANE);put(x+width-1,wy,wz,Blocks.GLASS_PANE);}
                put(x+2,floor,z+2,Blocks.CRAFTING_TABLE);put(x+3,floor,z+2,Blocks.SMOKER);put(x+4,floor,z+2,Blocks.BARREL);
                put(x+width-3,floor,z+depth-3,Blocks.OAK_STAIRS);put(x+width-4,floor,z+depth-3,Blocks.OAK_STAIRS);put(x+width-3,floor+2,z+2,Blocks.LANTERN);
                var bed=block("white_bed").defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH);put(x+3,floor,z+6,bed.setValue(BedBlock.PART,BedPart.FOOT));put(x+3,floor,z+5,bed.setValue(BedBlock.PART,BedPart.HEAD));
            }
            for(int py=y;py<y+(floors-1)*5;py++){put(x+1,py,z+depth-3,Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.EAST));put(x+1,py,z+depth-2,Blocks.AIR);}
            int door=x+width/2;fill(door-1,y,z+depth-1,door+1,y+2,z+depth-1,wall);
            var state=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);put(door,y,z+depth-1,state.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));put(door,y+1,z+depth-1,state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            fill(x,y+floors*5,z,x+width-1,y+floors*5,z+depth-1,Blocks.BRICK_SLAB);
            for(int step=1;step<=3;step++)fill(door-1,y-step,z+depth-1+step,door+1,y-step,z+depth-1+step,Blocks.STONE_BRICK_STAIRS);
        }
        void landmark(NapoliAtlas.Place p){
            int x=p.x(),z=p.z(),r=p.radius(),y=height(x,z)+1;
            if(p.kind()!=NapoliAtlas.Kind.PIER){fill(x-r,63,z-r,x+r,y-1,z+r,Blocks.SANDSTONE);fill(x-r,y-1,z-r,x+r,y-1,z+r,Blocks.SMOOTH_STONE);}
            switch(p.kind()){
                case PARK->{for(int xx=x-r+4;xx<=x+r-4;xx+=12)for(int zz=z-r+4;zz<=z+r-4;zz+=12){if(Math.abs(xx-x)>4&&Math.abs(zz-z)>4){fill(xx-3,y-1,zz-3,xx+3,y-1,zz+3,Blocks.GRASS_BLOCK);tree(xx,y,zz);}}fountain(x,y,z,5);}
                case SQUARE->{if(p.id().equals("martiri")){fill(x-2,y,z-2,x+2,y+2,z+2,Blocks.QUARTZ_BLOCK);fill(x,y+3,z,x,y+16,z,Blocks.QUARTZ_PILLAR);for(int dx:new int[]{-4,4})for(int dz:new int[]{-4,4}){put(x+dx,y,z+dz,Blocks.SMOOTH_SANDSTONE_STAIRS);put(x+dx,y+1,z+dz,Blocks.CUT_SANDSTONE);}}
                    else if(p.id().equals("vanvitelli")){fill(x-4,y-1,z-4,x+4,y-1,z+4,Blocks.GRASS_BLOCK);tree(x,y,z);}
                    else if(p.id().equals("secondigliano")){market(x-9,y,z-4);market(x+9,y,z-4);}
                    else if(p.id().equals("sannazaro")){fountain(x,y,z,7);fill(x,y+4,z,x,y+7,z,Blocks.CALCITE);put(x,y+8,z,Blocks.CHISELED_QUARTZ_BLOCK);}
                    else if(p.id().equals("pianura")||p.id().equals("ponticelli")){hall(x-14,y,z-r+2,28,20,16,Blocks.CALCITE);tower(x+17,y,z-r+8,3,24);fill(x,y+17,z-r+20,x,y+23,z-r+20,Blocks.QUARTZ_PILLAR);fill(x-2,y+21,z-r+20,x+2,y+21,z-r+20,Blocks.QUARTZ_PILLAR);}
                    else{for(int dx:new int[]{-10,10})for(int dz:new int[]{-8,8}){fill(x+dx-4,y-1,z+dz-4,x+dx+4,y-1,z+dz+4,Blocks.GRASS_BLOCK);tree(x+dx,y,z+dz);put(x+dx+5,y,z+dz,Blocks.OAK_STAIRS);}if(p.id().equals("barra")){fill(x-2,y,z-2,x+2,y+2,z+2,Blocks.QUARTZ_BLOCK);fill(x,y+3,z,x,y+8,z,Blocks.STONE_BRICKS);}}
                }
                case CASTLE->{if(p.id().equals("capuana")){for(int dx:new int[]{-11,11})tower(x+dx,y,z,5,21);fill(x-7,y+13,z-3,x+7,y+19,z+3,Blocks.SMOOTH_SANDSTONE);fill(x-7,y,z-3,x-6,y+12,z+3,Blocks.STONE_BRICKS);fill(x+6,y,z-3,x+7,y+12,z+3,Blocks.STONE_BRICKS);}else star(x,y,z,r-4);}
                case CHURCH->{hall(x-16,y,z-19,32,35,22,Blocks.CALCITE);
                    if(p.id().equals("duomo")){for(int dx=-16;dx<=16;dx++)fill(x+dx,y+22,z+15,x+dx,y+22+(16-Math.abs(dx))/2,z+15,Blocks.QUARTZ_BLOCK);for(int dx=-4;dx<=4;dx++)for(int dy=-4;dy<=4;dy++)if(dx*dx+dy*dy<17)put(x+dx,y+15+dy,z+15,block("blue_stained_glass"));}
                    else{tower(x+20,y,z-14,4,p.id().equals("carmine")?39:29);dome(x,y+22,z-2,11,block("oxidized_copper"));fill(x-3,y+23,z+16,x+3,y+23,z+16,Blocks.QUARTZ_PILLAR);fill(x,y+22,z+16,x,y+30,z+16,Blocks.QUARTZ_PILLAR);}
                    for(int zz=z-8;zz<=z+8;zz+=4)for(int dx:new int[]{-9,9})fill(x+dx-3,y,zz,x+dx+3,y,zz,Blocks.SPRUCE_STAIRS);
                }
                case CLOISTER->{hall(x-25,y,z-24,50,46,14,Blocks.SMOOTH_SANDSTONE);fill(x-16,y,z-15,x+16,y+14,z+14,Blocks.AIR);fill(x-15,y-1,z-14,x+15,y-1,z+13,Blocks.MOSS_BLOCK);for(int off=-18;off<=18;off+=6){fill(x+off,y,z-17,x+off,y+7,z-17,Blocks.QUARTZ_PILLAR);fill(x+off,y,z+16,x+off,y+7,z+16,Blocks.QUARTZ_PILLAR);}fountain(x,y,z,4);}
                case PALACE->{int width=p.id().equals("albergo")?96:p.id().equals("capodimonte")?84:52;hall(x-width/2,y,z-22,width,42,23,facade(p.id().hashCode()));for(int col=x-width/2+4;col<x+width/2;col+=6)fill(col,y,z+19,col,y+15,z+19,Blocks.QUARTZ_PILLAR);if(p.id().equals("capodimonte"))for(int off=-40;off<=40;off+=16)tree(x+off,y,z-42);}
                case MUSEUM->{hall(x-r+6,y,z-23,(r-6)*2,43,21,p.id().equals("mann")?block("red_terracotta"):Blocks.BRICKS);for(int off=-18;off<=18;off+=6){put(x+off,y,z,Blocks.CHISELED_QUARTZ_BLOCK);put(x+off,y+1,z,Blocks.QUARTZ_PILLAR);put(x+off,y+2,z,Blocks.CHISELED_QUARTZ_BLOCK);}if(p.id().equals("pietrarsa"))for(int off=-25;off<=25;off+=10){fill(x+off,y,z-8,x+off,y,z+10,Blocks.RAIL);fill(x+off-1,y+1,z-7,x+off+1,y+3,z+2,block("black_concrete"));put(x+off,y+4,z-6,Blocks.CAMPFIRE);}}
                case CATACOMBS->{int floor=y-10;hall(x-19,floor,z-9,38,18,9,Blocks.TUFF_BRICKS);for(int dx=-12;dx<=12;dx+=8){fill(x+dx-2,floor,z-8,x+dx+2,floor+6,z+8,Blocks.AIR);fill(x+dx-3,floor,z-7,x+dx-3,floor+6,z+7,Blocks.TUFF);for(int zz=z-5;zz<=z+5;zz+=3){put(x+dx-2,floor,zz,Blocks.SKELETON_SKULL);put(x+dx+2,floor,zz,Blocks.SKELETON_SKULL);}}
                    for(int step=0;step<=10;step++){fill(x-2,y-step,z+20-step,x+2,y+2,z+20-step,Blocks.AIR);for(int dx=-2;dx<=2;dx++)put(x+dx,y-step-1,z+20-step,Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));}fill(x-2,floor,z+7,x+2,floor+4,z+10,Blocks.AIR);sign(x+4,y,z+21,"INGRESSO","Sale sotterranee","↓ Scale");
                }
                case STATION->{hall(x-r+4,y,z-r+4,(r-4)*2,25,13,Blocks.SMOOTH_QUARTZ);for(int dx=-30;dx<=30;dx+=10){fill(x+dx,y-1,z-10,x+dx,y-1,z+30,Blocks.STONE_BRICKS);fill(x+dx,y,z-10,x+dx,y,z+30,Blocks.RAIL);fill(x+dx+2,y+9,z-10,x+dx+5,y+9,z+30,Blocks.SMOOTH_QUARTZ_SLAB);}fill(x-7,y+1,z-5,x-4,y+4,z+19,Blocks.IRON_BLOCK);fill(x+4,y+1,z-3,x+7,y+4,z+25,block("blue_concrete"));}
                case TOWERS->{for(int dx:new int[]{-39,0,36})for(int dz:new int[]{-30,22}){int height=28+Math.floorMod(dx*31+dz,24);hall(x+dx-10,y,z+dz-10,20,20,height,block("light_gray_concrete"));for(int yy=y+2;yy<y+height-1;yy+=3)fill(x+dx-9,yy,z+dz+9,x+dx+8,yy+1,z+dz+9,block("light_blue_stained_glass"));}fountain(x,y,z,5);}
                case STADIUM->stadium(x,y,z,r);
                case EXHIBITION->{hall(x-38,y,z-35,76,28,16,Blocks.SMOOTH_SANDSTONE);fill(x-8,y,z+4,x+8,y+1,z+32,Blocks.WATER);for(int dx:new int[]{-30,30}){fill(x+dx,y,z+4,x+dx,y+25,z+4,Blocks.QUARTZ_PILLAR);for(int dz=12;dz<=32;dz+=10)tree(x+dx,y,z+dz);}}
                case SCIENCE->{hall(x-36,y,z-22,72,40,14,Blocks.BRICKS);dome(x+18,y+14,z,17,block("light_gray_concrete"));for(int off=-24;off<=0;off+=8){put(x+off,y,z,Blocks.REDSTONE_BLOCK);put(x+off,y+1,z,Blocks.REDSTONE_LAMP);put(x+off,y+1,z+1,Blocks.LEVER);}sign(x-16,y+2,z+16,"CORPOREA","Scienza e corpo","Planetario →");}
                case PIER->{fill(x-5,65,z-r,x+5,65,z+r,Blocks.STONE_BRICKS);for(int step=0;step<2;step++)for(int dx=-2;dx<=2;dx++)put(x+dx,64+step,z-r-2+step,Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));for(int dz=-r;dz<=r;dz+=12){fill(x-5,60,z+dz,x-5,64,z+dz,Blocks.STONE_BRICKS);fill(x+5,60,z+dz,x+5,64,z+dz,Blocks.STONE_BRICKS);lamp(x-5,66,z+dz);lamp(x+5,66,z+dz);}sign(x,66,z-r+3,"PONTILE NORD","BAGNOLI","Sul Golfo di Pozzuoli");}
                case RUINS->{for(int xx=-r+4;xx<=r-4;xx++)for(int zz=-r+4;zz<=r-4;zz++){double d=Math.hypot(xx,zz);if(zz<0&&d>12&&d<r-5){int tier=(int)(d-12)/4;fill(x+xx,y,z+zz,x+xx,y+tier,z+zz,Blocks.SANDSTONE);}}for(int dx:new int[]{-20,0,20})fill(x+dx,y,z+15,x+dx,y+9,z+15,Blocks.SANDSTONE_WALL);}
                case SEASIDE->{if(!p.id().equals("nisida")){hall(x-13,y,z-15,26,24,12,Blocks.CALCITE);dome(x,y+12,z-4,7,Blocks.TERRACOTTA);}fill(x-10,y-1,z+18,x+10,y-1,z+r,Blocks.STONE_BRICKS);for(int dx=-10;dx<=10;dx+=5)put(x+dx,y,z+r,Blocks.STONE_BRICK_WALL);if(p.id().equals("nisida"))sign(x,y,z+15,"NISIDA","Belvedere esterno","Accessi reali regolati");}
            }
            int sy=p.kind()==NapoliAtlas.Kind.PIER?66:y;
            nameSign(x,sy,z+r-1,p.name());
            var streets=NapoliAtlas.ROADS.stream().filter(road->road.from().equals(p.id())||road.to().equals(p.id())).limit(2).map(NapoliAtlas.Road::name).toList();
            if(!streets.isEmpty()){int sx=x+(p.kind()==NapoliAtlas.Kind.PIER?3:7),sz=p.kind()==NapoliAtlas.Kind.PIER?z-r+5:z+r+4;nameSign(sx,p.kind()==NapoliAtlas.Kind.PIER?sy:height(sx,sz)+1,sz,streets.getFirst());}
            if(p.kind()!=NapoliAtlas.Kind.PIER)for(int dx:new int[]{-r+2,r-2}){lamp(x+dx,y,z+r-3);put(x+dx+1,y,z+r-3,Blocks.OAK_STAIRS);}
        }
        void hall(int x,int y,int z,int width,int depth,int height,Block wall){fill(x,y,z,x+width-1,y+height,z+depth-1,wall);fill(x+1,y,z+1,x+width-2,y+height-1,z+depth-2,Blocks.AIR);fill(x,y-1,z,x+width-1,y-1,z+depth-1,Blocks.POLISHED_ANDESITE);for(int xx=x+3;xx<x+width-2;xx+=5)for(int yy=y+3;yy<y+height-1;yy+=5){fill(xx,yy,z,xx+1,yy+2,z,Blocks.GLASS_PANE);fill(xx,yy,z+depth-1,xx+1,yy+2,z+depth-1,Blocks.GLASS_PANE);}fill(x+width/2-2,y,z+depth-1,x+width/2+2,y+4,z+depth-1,Blocks.AIR);for(int xx=x+5;xx<x+width;xx+=12){for(int py=y+7;py<y+height;py++)put(xx,py,z+depth/2,block("iron_chain"));put(xx,y+6,z+depth/2,Blocks.LANTERN);}}
        void fountain(int x,int y,int z,int radius){for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(dx*dx+dz*dz<=radius*radius){boolean rim=dx*dx+dz*dz>=(radius-1)*(radius-1);put(x+dx,y,z+dz,rim?Blocks.SMOOTH_QUARTZ:Blocks.WATER);}fill(x,y,z,x,y+3,z,Blocks.CHISELED_QUARTZ_BLOCK);put(x,y+4,z,Blocks.WATER);}
        void tower(int x,int y,int z,int radius,int height){for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(dx*dx+dz*dz<=radius*radius)fill(x+dx,y,z+dz,x+dx,y+height,z+dz,Blocks.STONE_BRICKS);for(int off=-radius;off<=radius;off+=2){put(x+off,y+height+1,z-radius,Blocks.STONE_BRICK_WALL);put(x+off,y+height+1,z+radius,Blocks.STONE_BRICK_WALL);}}
        void dome(int x,int y,int z,int r,Block b){for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){double d=dx*dx+dz*dz;if(d<=r*r){int h=(int)Math.sqrt(r*r-d);put(x+dx,y+h,z+dz,b);}}}
        void star(int x,int y,int z,int r){for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){double angle=Math.atan2(dz,dx),limit=r*(.78+.20*Math.cos(6*angle)),d=Math.hypot(dx,dz);if(d<=limit){fill(x+dx,y,z+dz,x+dx,y+7,z+dz,Blocks.TUFF_BRICKS);if(d>limit-2)fill(x+dx,y+8,z+dz,x+dx,y+11,z+dz,Blocks.STONE_BRICK_WALL);}}fill(x-3,y,z+r/2,x+3,y+5,z+r,Blocks.AIR);for(int zz=0;zz<=r/2;zz++)fill(x-2,y+Math.min(7,zz/2),z+r/2-zz,x+2,y+Math.min(7,zz/2),z+r/2-zz,Blocks.STONE_BRICK_STAIRS);}
        void stadium(int x,int y,int z,int r){for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){double ring=Math.sqrt(dx*dx/1.15+dz*dz*1.15);if(ring>r-23&&ring<r-4){int row=(int)(ring-(r-23))/2;fill(x+dx,y,z+dz,x+dx,y+row,z+dz,Blocks.POLISHED_ANDESITE);put(x+dx,y+row+1,z+dz,Math.floorMod(dx+dz,14)<2?Blocks.SMOOTH_STONE:block("blue_concrete"));}else if(Math.abs(dx)<=42&&Math.abs(dz)<=28){boolean line=Math.abs(dx)==42||Math.abs(dz)==28||dx==0||Math.abs(Math.hypot(dx,dz)-7)<.5||(Math.abs(dx)==30&&Math.abs(dz)<=15)||(Math.abs(dx)>=30&&Math.abs(dz)==15);put(x+dx,y-1,z+dz,line?Blocks.CALCITE:block("green_concrete"));}}fill(x-5,y,z+r-26,x+5,y+8,z+r,Blocks.AIR);for(int dx:new int[]{-42,42}){for(int dz:new int[]{-4,4})fill(x+dx,y,z+dz,x+dx,y+4,z+dz,Blocks.QUARTZ_PILLAR);fill(x+dx,y+4,z-4,x+dx,y+4,z+4,Blocks.QUARTZ_PILLAR);}sign(x,y+2,z+r-5,"DIEGO ARMANDO","MARADONA","Fuorigrotta","Pista e tribune");}
        void market(int x,int y,int z){for(int dx:new int[]{-4,4})fill(x+dx,y,z,x+dx,y+4,z+6,Blocks.SPRUCE_FENCE);for(int dx=-4;dx<=4;dx++)fill(x+dx,y+5,z,x+dx,y+5,z+6,dx%2==0?block("white_wool"):block("red_wool"));fill(x-3,y,z+3,x+3,y,z+3,Blocks.BARREL);}
    }
    private NapoliExpansion(){}
}
