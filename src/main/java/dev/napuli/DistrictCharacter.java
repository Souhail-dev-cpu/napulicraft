package dev.napuli;

import java.util.*;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.LevelChunk;

/** Regional palettes, shop fronts and soundscapes, applied once without modifying player renovations. */
final class DistrictCharacter {
    record Style(Block trim, Block awning, Block pot, SoundEvent sound, String business) {}
    static final AttachmentType<Boolean> FITTED=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).buildAndRegister(Napuli.id("district_character_v1"));
    static Style style(DistrictLife.District district) {
        String name=district.name().toLowerCase(Locale.ITALIAN);
        if(name.contains("posillipo")||name.contains("chiaia")||name.contains("bagnoli"))return new Style(Blocks.CUT_SANDSTONE,block("light_blue_wool"),Blocks.POTTED_FERN,SoundEvents.WATER_AMBIENT,"Alimentari del mare");
        if(name.contains("vomero")||name.contains("arenella")||name.contains("capodimonte"))return new Style(Blocks.SMOOTH_QUARTZ,block("green_wool"),block("potted_azalea_bush"),SoundEvents.NOTE_BLOCK_FLUTE.value(),"Bottega della collina");
        if(name.contains("direzionale")||name.contains("fuorigrotta")||name.contains("ponticelli"))return new Style(Blocks.POLISHED_ANDESITE,block("orange_wool"),Blocks.POTTED_CACTUS,SoundEvents.NOTE_BLOCK_BASEDRUM.value(),"Emporio del quartiere");
        return new Style(Blocks.TUFF_BRICKS,block("red_wool"),Blocks.POTTED_RED_TULIP,SoundEvents.NOTE_BLOCK_GUITAR.value(),"Bottega del vicolo");
    }
    static void fit(ServerLevel city,LevelChunk chunk) {
        if(chunk.getAttachedOrElse(FITTED,false)||NapoliExpansion.revision(chunk)<1)return;
        if(CityProperty.playerBuiltInChunk(city,chunk.getPos())){chunk.setAttached(FITTED,true);return;}
        for(var d:DistrictLife.districts()){
            var s=style(d);var h=d.business();int y=h.ground(),front=h.z()+23;
            for(int dx=2;dx<=19;dx++){
                var band=new BlockPos(h.x()+dx,y+4,front+1);
                place(city,chunk,band,s.awning());
            }
            for(int dx:new int[]{2,19}){
                place(city,chunk,new BlockPos(h.x()+dx,y,front+1),s.trim());
                place(city,chunk,new BlockPos(h.x()+dx,y+1,front+1),s.pot());
                for(int f=1;f<h.floors();f++)place(city,chunk,new BlockPos(h.x()+dx,y+f*5,front+1),s.pot());
            }
            for(var home:d.homes())for(int dx:new int[]{2,19}){
                place(city,chunk,new BlockPos(home.x()+dx,home.ground()+4,home.z()+24),s.awning());
                place(city,chunk,new BlockPos(home.x()+dx,home.ground()+6,home.z()+24),s.pot());
            }
        }
        chunk.setAttached(FITTED,true);
    }
    private static void place(ServerLevel city,LevelChunk chunk,BlockPos at,Block block){
        if(!net.minecraft.world.level.ChunkPos.containing(at).equals(chunk.getPos())||CityProperty.playerPlaced(city,at)||!chunk.getBlockState(at).isAir())return;
        city.setBlock(at,block.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
    static void register(){ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%1200!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;
        for(var d:DistrictLife.districts()){var at=d.business().entrance();if(city.players().stream().noneMatch(p->at.distToCenterSqr(p.position())<32*32))continue;
            city.playSound(null,at,style(d).sound(),SoundSource.AMBIENT,.08f,.85f+Math.floorMod(d.id().hashCode(),4)*.1f);
        }
    });}
    private static Block block(String id){return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));}
    private DistrictCharacter(){}
}
