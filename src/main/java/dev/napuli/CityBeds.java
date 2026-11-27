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
        if(house.id().startsWith("atlas_")&&preferred.getY()>house.ground()){
            if(preferred.getZ()==house.z()+5){candidates.add(new BlockPos(house.x()+13,preferred.getY(),house.z()+5));candidates.add(new BlockPos(house.x()+15,preferred.getY(),house.z()+5));}
            else for(int floor=1;floor<house.floors();floor++)candidates.add(new BlockPos(house.x()+18,house.ground()+floor*5,house.z()+8));
        }
        var beds=CityLivingHomes.points(house).stream().filter(p->p.furniture()==CityLivingHomes.Furniture.BED).toList();
        for(var point:beds)if(point.block().equals(preferred)||point.block().east().equals(preferred)){candidates.add(point.block());candidates.add(point.block().east());}
        for(var point:beds){candidates.add(point.block());candidates.add(point.block().east());}
        if(house.id().startsWith("atlas_"))for(int floor=1;floor<house.floors();floor++){
            candidates.add(new BlockPos(house.x()+13,house.ground()+floor*5,house.z()+5));
            candidates.add(new BlockPos(house.x()+15,house.ground()+floor*5,house.z()+5));
            candidates.add(new BlockPos(house.x()+18,house.ground()+floor*5,house.z()+8));
        }
        var occupied=new HashSet<BlockPos>();
        var bounds=new AABB(house.x(),house.ground()-3,house.z(),house.x()+house.width(),house.ground()+house.floors()*5+15,house.z()+house.depth());
        for(var sleeper:city.getEntitiesOfClass(LivingEntity.class,bounds,e->e.isAlive()&&e.isSleeping()&&e!=npc))
            sleeper.getSleepingPos().ifPresent(position->occupied.add(foot(city,position)));
        for(var player:city.players())if(player.isSleeping())player.getSleepingPos().filter(house::contains).ifPresent(position->occupied.add(foot(city,position)));
        if(npc.isSleeping()&&npc.getSleepingPos().isPresent()){
            var current=foot(city,npc.getSleepingPos().get());if(house.contains(current)&&!occupied.contains(current))return current;
        }
        for(var bed:candidates)if(complete(city,bed)&&!occupied.contains(foot(city,bed)))return foot(city,bed);
        return null;
    }
    private static boolean complete(ServerLevel city,BlockPos at){
        if(!city.hasChunkAt(at)||!(city.getBlockState(at).getBlock() instanceof BedBlock))return false;
        var foot=foot(city,at);var state=city.getBlockState(foot);
        if(!(state.getBlock() instanceof BedBlock)||state.getValue(BedBlock.PART)!=BedPart.FOOT)return false;
        var head=foot.relative(state.getValue(BedBlock.FACING));
        return city.hasChunkAt(head)&&city.getBlockState(head).is(state.getBlock())
                &&city.getBlockState(head).getValue(BedBlock.PART)==BedPart.HEAD
                &&city.getBlockState(head).getValue(BedBlock.FACING)==state.getValue(BedBlock.FACING);
    }
    private CityBeds(){}
}
