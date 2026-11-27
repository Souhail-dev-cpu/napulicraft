package dev.napuli;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/** A real sleeper owns a real bed; family members never occupy the same mattress. */
final class CityBeds {
    static BlockPos foot(ServerLevel city,BlockPos position){
        var state=city.getBlockState(position);
        return state.getBlock() instanceof BedBlock&&state.getValue(BedBlock.PART)==BedPart.HEAD?position.relative(state.getValue(BedBlock.FACING).getOpposite()):position;
    }
    static BlockPos choose(NapoliResident npc,ServerLevel city,NapoliMergellina.House house,BlockPos preferred){
        var candidates=new LinkedHashSet<BlockPos>();candidates.add(preferred);
        var beds=CityLivingHomes.points(house).stream().filter(p->p.furniture()==CityLivingHomes.Furniture.BED).toList();
        for(var point:beds)if(point.block().equals(preferred)||point.block().east().equals(preferred)){candidates.add(point.block());candidates.add(point.block().east());}
        for(var point:beds){candidates.add(point.block());candidates.add(point.block().east());}
        var occupied=new HashSet<BlockPos>();
        var bounds=new AABB(house.x(),house.ground()-3,house.z(),house.x()+house.width(),house.ground()+house.floors()*5+15,house.z()+house.depth());
        for(var sleeper:city.getEntitiesOfClass(LivingEntity.class,bounds,e->e.isAlive()&&e.isSleeping()&&e!=npc))
            sleeper.getSleepingPos().ifPresent(position->occupied.add(foot(city,position)));
        for(var player:city.players())if(player.isSleeping())player.getSleepingPos().filter(house::contains).ifPresent(position->occupied.add(foot(city,position)));
        if(npc.isSleeping()&&npc.getSleepingPos().isPresent()){
            var current=foot(city,npc.getSleepingPos().get());if(house.contains(current)&&!occupied.contains(current))return current;
        }
        for(var bed:candidates)if(city.hasChunkAt(bed)&&city.getBlockState(bed).getBlock() instanceof BedBlock&&!occupied.contains(foot(city,bed)))return foot(city,bed);
        return null;
    }
    private CityBeds(){}
}
