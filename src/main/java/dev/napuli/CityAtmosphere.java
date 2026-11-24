package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Only authored lamps in loaded houses are changed. Player lamps and builds are left alone. */
final class CityAtmosphere {
    private static final Map<ServerLevel,Long> PERIODS=new WeakHashMap<>();
    static BlockPos lamp(NapoliMergellina.House h){return new BlockPos(h.x()+h.width()-2,h.ground()+3,h.z()+h.depth()-2);}
    static boolean lit(NapoliMergellina.House h,long time){
        int offset=Math.floorMod(h.id().hashCode(),1800);return time>=12000+offset&&time<17500+offset;
    }
    static boolean rush(long time){return time>=1800&&time<3200||time>=10500&&time<12000;}
    static void fixtures(ServerLevel city){for(var h:CityLivingHomes.all())if(!h.id().startsWith("atlas_")&&city.isEmptyBlock(lamp(h)))city.setBlock(lamp(h),Blocks.REDSTONE_LAMP.defaultBlockState(),2);}
    static void tick(ServerLevel city){
        long time=NapoliBroadcast.time(city),period=time/600;
        boolean sound=!Objects.equals(PERIODS.put(city,period),period);
        for(var h:CityLivingHomes.all()){
            var pos=lamp(h);if(!city.hasChunkAt(pos))continue;
            var state=city.getBlockState(pos);boolean on=lit(h,time)&&!CityTraces.brokenAt(city,pos);
            if(state.is(Blocks.REDSTONE_LAMP)&&state.getValue(BlockStateProperties.LIT)!=on)city.setBlock(pos,state.setValue(BlockStateProperties.LIT,on),2);
            if(!sound||city.players().stream().noneMatch(p->h.entrance().distToCenterSqr(p.position())<18*18))continue;
            SoundEvent ambient=null;
            if(time>=500&&time<1500)ambient=SoundEvents.BARREL_OPEN;
            else if(time>=10500&&time<11600)ambient=SoundEvents.ITEM_FRAME_ADD_ITEM;
            else if(time>=12000&&time<16000&&NapoliBroadcast.channel(city,CityLivingHomes.use(h,CityLivingHomes.Furniture.TV).block())!=NapoliBroadcast.Channel.OFF)ambient=SoundEvents.NOTE_BLOCK_HARP.value();
            if(ambient!=null)CityEffects.sound(city,null,h.entrance(),ambient,SoundSource.AMBIENT,.18f,.8f+Math.floorMod(h.id().hashCode(),4)*.1f);
        }
    }
    static void register(){ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%100==0){var city=s.getLevel(NapoliMap.LEVEL);if(city!=null&&!city.players().isEmpty())tick(city);}});}
    private CityAtmosphere(){}
}
