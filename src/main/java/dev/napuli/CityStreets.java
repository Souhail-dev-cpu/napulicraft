package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.LevelChunk;

/** Grade streets between fixed building elevations, then cut/fill their loaded columns safely. */
final class CityStreets {
    static final int REVISION=1;
    static final AttachmentType<Integer> GRADED=AttachmentRegistry.<Integer>builder().persistent(Codec.INT).buildAndRegister(Napuli.id("street_grade_revision"));
    private record Plateau(int x,int z,int radius,int height){}
    private record Front(int x,int z,int height){}
    private static final class Plan {
        static final List<Plateau> PLATEAUS=NapoliAtlas.PLACES.stream().filter(p->!p.original()&&p.kind()!=NapoliAtlas.Kind.PIER)
                .map(p->new Plateau(p.x(),p.z(),p.radius(),NapoliExpansion.terrain(p.x(),p.z()))).toList();
        static final Map<Long,Front> FRONTS=fronts();
    }
    private static final Map<Long,Integer> HEIGHTS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ServerLevel,Map<UUID,Integer>> ARRIVALS=new WeakHashMap<>();
    static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity,city)->{if(city.dimension()==NapoliMap.LEVEL&&traveler(entity))ARRIVALS.computeIfAbsent(city,k->new HashMap<>()).put(entity.getUUID(),city.getServer().getTickCount());});
        ServerTickEvents.END_SERVER_TICK.register(server->{var city=server.getLevel(NapoliMap.LEVEL);var pending=ARRIVALS.get(city);if(city==null||pending==null)return;
            for(var entry:List.copyOf(pending.entrySet())){var entity=city.getEntity(entry.getKey());if(entity==null||server.getTickCount()-entry.getValue()>200){pending.remove(entry.getKey());continue;}var chunk=city.getChunkSource().getChunkNow(entity.getBlockX()>>4,entity.getBlockZ()>>4);if(chunk!=null&&((AttachmentTarget)chunk).getAttachedOrElse(GRADED,0)>=REVISION){settle(city,chunk,entity);pending.remove(entry.getKey());}}
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->ARRIVALS.clear());
    }
    private static boolean traveler(Entity entity){return entity instanceof ServerPlayer||entity instanceof NapoliResident||entity instanceof ItemEntity;}
    private static long key(int x,int z){return ChunkPos.pack(x,z);}
    private static Map<Long,Front> fronts(){var all=new HashMap<Long,Front>();
        for(int gx=-52;gx<=51;gx++)for(int gz=-21;gz<=13;gz++){int x=gx*40+9,z=gz*40+9;if(NapoliExpansion.houseAllowed(x,z))all.put(key(gx,gz),new Front(x+11,z+24,NapoliExpansion.legacyHeight(x+11,z+24)));}
        return Map.copyOf(all);
    }
    private static boolean building(int x,int z){int dx=Math.floorMod(x,40),dz=Math.floorMod(z,40);return dx>=9&&dx<=30&&dz>=9&&dz<=32&&Plan.FRONTS.containsKey(key(Math.floorDiv(x,40),Math.floorDiv(z,40)));}
    static boolean landmark(int x,int z){for(var p:Plan.PLATEAUS)if(Math.abs(x-p.x())<=p.radius()&&Math.abs(z-p.z())<=p.radius())return true;return false;}
    static boolean apron(int x,int z){int gx=Math.floorDiv(x,40),gz=Math.floorDiv(z,40);for(int row=gz-1;row<=gz;row++){var f=Plan.FRONTS.get(key(gx,row));if(f!=null&&Math.abs(x-f.x())<=1&&z>=f.z()&&z<=f.z()+9)return true;}return false;}
    static boolean grid(int x,int z){return Math.floorMod(x,40)<4||Math.floorMod(z,40)<4;}
    static boolean paved(int x,int z){return grid(x,z)||NapoliExpansion.nearRoad(x,z,5)||Math.abs(z-NapoliExpansion.coast(x))<5||apron(x,z);}
    static boolean adjusted(int x,int z){
        if(!NapoliExpansion.writable(x,z)||NapoliExpansion.water(x,z)||landmark(x,z)||building(x,z))return false;
        int dx=Math.floorMod(x-2,40),dz=Math.floorMod(z-2,40);dx=Math.min(dx,40-dx);dz=Math.min(dz,40-dz);
        return dx<=6||dz<=6||NapoliExpansion.nearRoad(x,z,8)||Math.abs(z-NapoliExpansion.coast(x))<5||NapoliExpansion.nearPlace(x,z,7)||apron(x,z);
    }
    static int height(int x,int z){
        if(NapoliAtlas.ORIGINAL.contains(x,z))return 63;
        if(NapoliExpansion.water(x,z))return 65;
        long key=key(x,z);var cached=HEIGHTS.get(key);if(cached!=null)return cached;
        int coreDx=Math.max(0,Math.max(NapoliAtlas.ORIGINAL.minX()-x,x-NapoliAtlas.ORIGINAL.maxX()+1));
        int coreDz=Math.max(0,Math.max(NapoliAtlas.ORIGINAL.minZ()-z,z-NapoliAtlas.ORIGINAL.maxZ()+1));
        int low=63,high=63+coreDx+coreDz,nearest=10000;
        for(var p:Plan.PLATEAUS){int d=Math.max(0,Math.abs(x-p.x())-p.radius())+Math.max(0,Math.abs(z-p.z())-p.radius());nearest=Math.min(nearest,d);low=Math.max(low,p.height()-d);high=Math.min(high,p.height()+d);}
        int gx=Math.floorDiv(x,40),gz=Math.floorDiv(z,40);
        for(int bx=gx-2;bx<=gx+2;bx++)for(int bz=gz-2;bz<=gz+2;bz++){var f=Plan.FRONTS.get(key(bx,bz));if(f!=null){int d=Math.abs(x-f.x())+Math.abs(z-f.z());low=Math.max(low,f.height()-d);high=Math.min(high,f.height()+d);}}
        // A one-block bank also joins the sea-level promenade without a two-block lip.
        if(nearest>=8)for(var d:Direction.Plane.HORIZONTAL)if(NapoliExpansion.water(x+d.getStepX(),z+d.getStepZ()))low=Math.max(low,64);
        int result=low<=high?Math.clamp(NapoliExpansion.terrain(x,z),low,high):(low+high)/2;
        if(HEIGHTS.size()>=65536)HEIGHTS.clear();HEIGHTS.put(key,result);return result;
    }
    static BlockState paving(int x,int z){
        BlockState flat=(NapoliExpansion.nearRoad(x,z,5)?Blocks.POLISHED_DEEPSLATE:Blocks.SMOOTH_STONE).defaultBlockState();
        int h=NapoliExpansion.height(x,z),best=h;Direction down=null;
        boolean ns=Math.floorMod(x,40)<4,ew=Math.floorMod(z,40)<4;
        var directions=ns&&!ew?List.of(Direction.NORTH,Direction.SOUTH):ew&&!ns?List.of(Direction.EAST,Direction.WEST):List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST);
        for(var direction:directions){int nx=x+direction.getStepX(),nz=z+direction.getStepZ();if(!paved(nx,nz))continue;int candidate=NapoliExpansion.height(nx,nz);if(candidate<best){best=candidate;down=direction;}}
        return down==null?flat:Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,down.getOpposite());
    }
    private static boolean replaceable(BlockState s){return s.isAir()||s.is(Blocks.TUFF)||s.is(Blocks.STONE)||s.is(Blocks.SANDSTONE)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT)||s.is(Blocks.SAND)||s.is(Blocks.SMOOTH_STONE)||s.is(Blocks.POLISHED_ANDESITE)||s.is(Blocks.POLISHED_DEEPSLATE)||s.is(Blocks.STONE_BRICK_STAIRS)||s.is(Blocks.RAIL);}
    private record SignMove(BlockPos position,BlockState state,SignText front,SignText back){}
    private record PropMove(BlockPos position,BlockState state){}
    static int fit(ServerLevel city,LevelChunk chunk){
        if(((AttachmentTarget)chunk).getAttachedOrElse(GRADED,0)>=REVISION||NapoliExpansion.revision(chunk)<1)return 0;
        var signs=new ArrayList<SignMove>();var props=new ArrayList<PropMove>();moveAuthoredSigns(city,chunk,signs,props);
        int changed=0;var cp=chunk.getPos();
        for(int x=cp.getMinBlockX();x<=cp.getMaxBlockX();x++)for(int z=cp.getMinBlockZ();z<=cp.getMaxBlockZ();z++)if(adjusted(x,z)){
            int old=NapoliExpansion.legacyHeight(x,z),next=height(x,z),bottom=Math.min(old,next),top=Math.max(old,next)+3;boolean safe=true;
            for(int y=bottom;y<=top;y++){var p=new BlockPos(x,y,z);if(CityProperty.playerPlaced(city,p)||chunk.getBlockEntity(p)!=null||!replaceable(chunk.getBlockState(p))){safe=false;break;}}
            if(!safe)continue;
            if(old!=next){for(int y=bottom;y<next;y++)changed+=put(city,chunk,new BlockPos(x,y,z),Blocks.TUFF.defaultBlockState());for(int y=next+1;y<=top;y++)changed+=put(city,chunk,new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
            var surface=paved(x,z)?paving(x,z):Blocks.GRASS_BLOCK.defaultBlockState();
            if(!chunk.getBlockState(new BlockPos(x,next,z)).isAir())changed+=put(city,chunk,new BlockPos(x,next,z),surface);
            else if(next>=old)changed+=put(city,chunk,new BlockPos(x,next,z),surface);
        }
        for(var move:props)if(replaceable(chunk.getBlockState(move.position()))&&!CityProperty.playerPlaced(city,move.position()))put(city,chunk,move.position(),move.state());
        for(var move:signs){put(city,chunk,move.position(),move.state());if(chunk.getBlockEntity(move.position()) instanceof SignBlockEntity sign){sign.setText(move.front(),SignTextSlot.FRONT);sign.setText(move.back(),SignTextSlot.BACK);sign.setChanged();}}
        ((AttachmentTarget)chunk).setAttached(GRADED,REVISION);
        // Connected players can arrive before a remote chunk's entity sections become visible.
        // Use the level's player list so terrain repairs also protect them during that transition.
        for(var player:city.players())settle(city,chunk,player);
        for(var entity:city.getEntitiesOfClass(Entity.class,new AABB(cp.getMinBlockX(),60,cp.getMinBlockZ(),cp.getMaxBlockX()+1,130,cp.getMaxBlockZ()+1),e->traveler(e)&&!(e instanceof ServerPlayer)))settle(city,chunk,entity);
        return changed;
    }
    private static void settle(ServerLevel city,LevelChunk chunk,Entity entity){
        if(!entity.isAlive()||entity.isPassenger()||entity.isSpectator()||entity instanceof ServerPlayer p&&p.getAbilities().flying)return;
        int x=entity.getBlockX(),z=entity.getBlockZ();if(!ChunkPos.containing(entity.blockPosition()).equals(chunk.getPos())||!adjusted(x,z))return;
        int old=NapoliExpansion.legacyHeight(x,z),next=height(x,z);double feet=entity.getY();
        if(old==next||feet>=next+.4&&feet<=next+1.25||Math.abs(feet-(old+1))>1.5)return;
        var floor=new BlockPos(x,next,z);
        if(chunk.getBlockState(floor).getCollisionShape(city,floor).isEmpty()||!chunk.getBlockState(floor.above()).getCollisionShape(city,floor.above()).isEmpty()||!chunk.getBlockState(floor.above(2)).getCollisionShape(city,floor.above(2)).isEmpty())return;
        if(entity instanceof ServerPlayer p)p.teleportTo(city,p.getX(),next+1,p.getZ(),Set.of(),p.getYRot(),p.getXRot(),true);else entity.teleportTo(entity.getX(),next+1,entity.getZ());
        entity.resetFallDistance();entity.setDeltaMovement(entity.getDeltaMovement().multiply(1,0,1));
    }
    private static void moveAuthoredSigns(ServerLevel city,LevelChunk chunk,List<SignMove> signs,List<PropMove> props){
        for(var p:NapoliAtlas.PLACES)if(!p.original()&&p.kind()!=NapoliAtlas.Kind.PIER){
            var road=NapoliAtlas.ROADS.stream().filter(r->r.from().equals(p.id())||r.to().equals(p.id())).findFirst();if(road.isEmpty())continue;
            int x=p.x()+7,z=p.z()+p.radius()+4;var lines=new ArrayList<String>();String row="";
            for(var word:road.get().name().split(" ")){if(!row.isEmpty()&&row.length()+word.length()+1>15){lines.add(row);row="";}row+=row.isEmpty()?word:" "+word;}if(!row.isEmpty())lines.add(row);if(lines.size()<4)lines.add("/napoli luoghi");
            moveSign(city,chunk,new BlockPos(x,NapoliExpansion.legacyHeight(p.x(),p.z())+1,z),new BlockPos(x,NapoliExpansion.height(x,z)+1,z),lines,signs);
        }
        if(((AttachmentTarget)chunk).getAttachedOrElse(CityRailway.FITTED,0)!=3)return;
        for(var stop:CityNetwork.stops()){
            var served=CityNetwork.lines().stream().filter(l->l.stops().contains(stop.id())).toList();if(served.isEmpty())continue;
            int x=stop.street().getX(),z=stop.street().getZ(),old=NapoliExpansion.legacyHeight(x,z)+1;
            var names=served.stream().map(CityNetwork.Line::id).reduce((a,b)->a+" "+b).orElse("");
            moveSign(city,chunk,new BlockPos(x+6,old+1,z+5),stop.sign(),List.of("FERMATA",stop.name(),names,"Tasto destro"),signs);
            moveProp(city,chunk,new BlockPos(x+6,old,z+5),stop.sign().below(),Blocks.OAK_FENCE,props);
            int lamp=NapoliExpansion.height(x+6,z+6)+1;
            moveProp(city,chunk,new BlockPos(x+6,old+1,z+6),new BlockPos(x+6,lamp+1,z+6),Blocks.LANTERN,props);
            moveProp(city,chunk,new BlockPos(x+6,old,z+6),new BlockPos(x+6,lamp,z+6),Blocks.SMOOTH_STONE,props);
        }
    }
    private static void moveSign(ServerLevel city,LevelChunk chunk,BlockPos old,BlockPos next,List<String> expected,List<SignMove> moves){
        if(old.equals(next)||!ChunkPos.containing(old).equals(chunk.getPos())||CityProperty.playerPlaced(city,old)||CityProperty.playerPlaced(city,next))return;
        boolean ownPost=!expected.isEmpty()&&expected.getFirst().equals("FERMATA")&&next.equals(old.below())&&chunk.getBlockState(next).is(Blocks.OAK_FENCE);
        if(!replaceable(chunk.getBlockState(next))&&!ownPost)return;
        if(!(chunk.getBlockEntity(old) instanceof SignBlockEntity sign)||sign.isWaxed())return;
        var front=sign.getText(SignTextSlot.FRONT);for(int i=0;i<4;i++)if(!front.getMessages(false).get(i).getString().equals(i<expected.size()?expected.get(i):""))return;
        moves.add(new SignMove(next,chunk.getBlockState(old),front,sign.getText(SignTextSlot.BACK)));put(city,chunk,old,Blocks.AIR.defaultBlockState());
    }
    private static void moveProp(ServerLevel city,LevelChunk chunk,BlockPos old,BlockPos next,Block type,List<PropMove> moves){
        if(old.equals(next)||!ChunkPos.containing(old).equals(chunk.getPos())||CityProperty.playerPlaced(city,old)||CityProperty.playerPlaced(city,next)||!chunk.getBlockState(old).is(type)||!replaceable(chunk.getBlockState(next)))return;
        moves.add(new PropMove(next,chunk.getBlockState(old)));put(city,chunk,old,Blocks.AIR.defaultBlockState());
    }
    private static int put(ServerLevel city,LevelChunk chunk,BlockPos pos,BlockState state){if(chunk.getBlockState(pos).equals(state))return 0;city.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);return 1;}
    private CityStreets(){}
}
