package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Surface lanes, tram rails and walkable metro tunnels, authored only in the supplied loaded chunk. */
final class CityRailway {
    static final AttachmentType<Integer> FITTED=AttachmentRegistry.<Integer>builder().persistent(Codec.INT).buildAndRegister(Napuli.id("transport_chunk_revision"));
    // Register the saved marker before any existing chunks are deserialized.
    static void register() {}
    record Track(CityNetwork.Line line,Vec3 from,Vec3 to){}
    private static final class Index {static final Map<Long,List<Track>> TRACKS=index();}
    private static Map<Long,List<Track>> index(){var result=new HashMap<Long,List<Track>>();var seen=new HashSet<String>();for(var l:CityNetwork.lines())for(var f:CityNetwork.plan(l).frames()){
        if(f.from().distanceTo(f.to())<.01)continue;String key=l.id()+"/"+f.from()+"/"+f.to();if(!seen.add(key))continue;int radius=l.mode()==CityNetwork.Mode.METRO?6:4;
        for(int x=((int)Math.floor(Math.min(f.from().x,f.to().x))-radius)>>4;x<=((int)Math.ceil(Math.max(f.from().x,f.to().x))+radius)>>4;x++)for(int z=((int)Math.floor(Math.min(f.from().z,f.to().z))-radius)>>4;z<=((int)Math.ceil(Math.max(f.from().z,f.to().z))+radius)>>4;z++)result.computeIfAbsent(ChunkPos.pack(x,z),k->new ArrayList<>()).add(new Track(l,f.from(),f.to()));
    }var all=new HashMap<Long,List<Track>>();result.forEach((k,v)->all.put(k,List.copyOf(v)));return Map.copyOf(all);}
    private static double distance(int x,int z,Vec3 a,Vec3 b){double dx=b.x-a.x,dz=b.z-a.z;double t=Math.clamp(((x+.5-a.x)*dx+(z+.5-a.z)*dz)/Math.max(.001,dx*dx+dz*dz),0,1);return Math.hypot(x+.5-a.x-t*dx,z+.5-a.z-t*dz);}
    private static boolean natural(BlockState s){return s.isAir()||s.is(Blocks.STONE)||s.is(Blocks.SANDSTONE)||s.is(Blocks.TUFF)||s.is(Blocks.DIRT)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.SAND);}
    static void fit(ServerLevel city,LevelChunk chunk){NapoliExpansion.generate(city,chunk);if(((AttachmentTarget)chunk).getAttachedOrElse(FITTED,0)>=4){CityStreets.fit(city,chunk);return;}CityStreets.fit(city,chunk);var w=new Writer(city,chunk);for(var t:Index.TRACKS.getOrDefault(chunk.getPos().pack(),List.of()))for(int x=w.minX;x<=w.maxX;x++)for(int z=w.minZ;z<=w.maxZ;z++){
        double d=distance(x,z,t.from(),t.to());if(t.line().mode()==CityNetwork.Mode.METRO){if(d>5.1)continue;int floor=t.line().level();w.put(x,floor,z,Blocks.STONE_BRICKS,false);for(int y=floor+1;y<=floor+5;y++)w.put(x,y,z,d<4.1||insideTunnel(chunk,t.line().level(),x,z)?Blocks.AIR:Blocks.STONE_BRICKS,false);w.put(x,floor+6,z,Math.floorMod(x+z,20)==0?Blocks.GLOWSTONE:Blocks.STONE_BRICKS,false);if(d<.55)w.rail(x,floor+1,z,Math.abs(t.to().x-t.from().x)>Math.abs(t.to().z-t.from().z));}
        else if(d<3.6&&NapoliExpansion.writable(x,z)&&!NapoliExpansion.water(x,z)){int floor=NapoliExpansion.height(x,z);var paving=CityStreets.paving(x,z);if(!(paving.getBlock() instanceof StairBlock))paving=Blocks.POLISHED_DEEPSLATE.defaultBlockState();w.state(x,floor,z,paving,true);for(int y=floor+1;y<=floor+3;y++)if(natural(chunk.getBlockState(new BlockPos(x,y,z))))w.put(x,y,z,Blocks.AIR,true);if(t.line().mode()==CityNetwork.Mode.TRAM&&d<.5)w.rail(x,floor+1,z,Math.abs(t.to().x-t.from().x)>Math.abs(t.to().z-t.from().z));}
    }
        for(var s:CityNetwork.stops())if(s.street().getX()+8>=w.minX&&s.street().getX()-8<=w.maxX&&s.street().getZ()+8>=w.minZ&&s.street().getZ()-8<=w.maxZ){var served=CityNetwork.lines().stream().filter(l->l.stops().contains(s.id())).toList();if(served.isEmpty())continue;int x=s.street().getX(),z=s.street().getZ(),ground=s.entrance().getY();
            for(var l:served)if(l.mode()==CityNetwork.Mode.METRO){for(int px=x-7;px<=x+7;px++)for(int pz=z-7;pz<=z+7;pz++){w.put(px,l.level(),pz,Blocks.POLISHED_ANDESITE,false);for(int y=l.level()+1;y<=l.level()+5;y++)w.put(px,y,pz,(Math.abs(px-x)==7||Math.abs(pz-z)==7)&&!passage(chunk,l,px,pz)?Blocks.STONE_BRICKS:Blocks.AIR,false);w.put(px,l.level()+6,pz,Math.floorMod(px+pz,9)==0?Blocks.SEA_LANTERN:Blocks.STONE_BRICKS,false);}w.sign(x+5,l.level()+1,z-4,l.id(),s.name(),"/trasporti", "Banchina");}
            int bottom=served.stream().filter(l->l.mode()==CityNetwork.Mode.METRO).mapToInt(CityNetwork.Line::level).min().orElse(ground);if(bottom<ground){for(int y=bottom+1;y<=ground+1;y++)for(int px=x+4;px<=x+6;px++)for(int pz=z+4;pz<=z+6;pz++){boolean opening=px==x+5&&pz==z+4&&y>=ground;w.put(px,y,pz,opening||px==x+5&&pz==z+5?Blocks.AIR:Blocks.GLASS,false);}for(int y=bottom+1;y<=ground+1;y++)w.state(x+5,y,z+5,Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH),false);}
            w.sign(x+6,NapoliExpansion.height(x+6,z+5)+2,z+5,"FERMATA",s.name(),served.stream().map(CityNetwork.Line::id).reduce((a,b)->a+" "+b).orElse(""),"Tasto destro");w.put(x+6,NapoliExpansion.height(x+6,z+5)+1,z+5,Blocks.OAK_FENCE,true);w.put(x+6,NapoliExpansion.height(x+6,z+6)+1,z+6,Blocks.SMOOTH_STONE,true);w.put(x+6,NapoliExpansion.height(x+6,z+6)+2,z+6,Blocks.LANTERN,true);
        }
        ((AttachmentTarget)chunk).setAttached(FITTED,4);
    }
    // Tunnel sections overlap at curves and junctions: another section's outside wall
    // must never fill a section that has already been excavated for the opposite track.
    private static boolean insideTunnel(LevelChunk chunk,int level,int x,int z){for(var track:Index.TRACKS.getOrDefault(chunk.getPos().pack(),List.of()))if(track.line().mode()==CityNetwork.Mode.METRO&&track.line().level()==level&&distance(x,z,track.from(),track.to())<4.1)return true;return false;}
    private static boolean passage(LevelChunk chunk,CityNetwork.Line line,int x,int z){for(var track:Index.TRACKS.getOrDefault(chunk.getPos().pack(),List.of()))if(track.line().id().equals(line.id())&&distance(x,z,track.from(),track.to())<4.1)return true;return false;}
    private static final class Writer {
        final ServerLevel city;final LevelChunk chunk;final int minX,maxX,minZ,maxZ;
        Writer(ServerLevel c,LevelChunk k){city=c;chunk=k;minX=k.getPos().getMinBlockX();maxX=k.getPos().getMaxBlockX();minZ=k.getPos().getMinBlockZ();maxZ=k.getPos().getMaxBlockZ();}
        void put(int x,int y,int z,Block b,boolean surface){state(x,y,z,b.defaultBlockState(),surface);}
        void state(int x,int y,int z,BlockState state,boolean surface){if(x<minX||x>maxX||z<minZ||z>maxZ||!NapoliAtlas.EXPANDED.contains(x,z))return;var p=new BlockPos(x,y,z);if(CityProperty.playerPlaced(city,p)||chunk.getBlockEntity(p)!=null)return;var old=chunk.getBlockState(p);if(state.isAir()&&old.is(Blocks.RAIL))return;if(surface&&!natural(old)&&!old.is(Blocks.POLISHED_DEEPSLATE)&&!old.is(Blocks.POLISHED_ANDESITE)&&!old.is(Blocks.SMOOTH_STONE)&&!(old.getBlock() instanceof StairBlock)&&!old.is(Blocks.RAIL))return;if(!surface&&!natural(old)&&!old.is(Blocks.STONE_BRICKS)&&!old.is(Blocks.POLISHED_ANDESITE)&&!old.is(Blocks.GLASS)&&!old.is(Blocks.RAIL)&&!old.is(Blocks.SEA_LANTERN)&&!old.is(Blocks.GLOWSTONE)&&!old.is(Blocks.LADDER))return;city.setBlock(p,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
        void rail(int x,int y,int z,boolean east){state(x,y,z,Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,east?RailShape.EAST_WEST:RailShape.NORTH_SOUTH),false);}
        void sign(int x,int y,int z,String... lines){if(x<minX||x>maxX||z<minZ||z>maxZ||CityProperty.playerPlaced(city,new BlockPos(x,y,z)))return;var p=new BlockPos(x,y,z);if(!chunk.getBlockState(p).isAir())return;city.setBlock(p,Blocks.OAK_SIGN.defaultBlockState(),2);if(chunk.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign){var text=net.minecraft.world.level.block.entity.SignText.EMPTY.asMutable();for(int i=0;i<Math.min(4,lines.length);i++)text.setLine(i,net.minecraft.network.chat.Component.literal(lines[i]));sign.setText(text.asImmutable(),net.minecraft.world.level.block.entity.SignTextSlot.FRONT);sign.setText(text.asImmutable(),net.minecraft.world.level.block.entity.SignTextSlot.BACK);sign.setChanged();}}
    }
    private CityRailway(){}
}
