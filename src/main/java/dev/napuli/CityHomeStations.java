package dev.napuli;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Authored furniture positions, checked against the actual loaded room before use. */
final class CityHomeStations {
    record Station(CityLivingHomes.Furniture kind, BlockPos block, BlockPos stand, BlockPos chair) {}

    static Station fixture(NapoliMergellina.House home, CityLivingHomes.Furniture kind) {
        var point=CityLivingHomes.use(home,kind);
        return new Station(kind,point.block(),point.stand(),null);
    }
    static boolean seat(Block block) {
        return block==NapoliFurniture.DIVANO||block==NapoliFurniture.POLTRONA
                ||block==NapoliFurniture.SEDIA_IMBOTTITA||block==NapoliFurniture.SEDIA_SCRIVANIA
                ||block==Blocks.SPRUCE_STAIRS||block==Blocks.OAK_STAIRS;
    }
    static boolean valid(ServerLevel city, Station station) {
        if(!city.hasChunkAt(station.block())||!city.hasChunkAt(station.stand())
                ||!NapoliResidentHomes.walkable(city,station.stand()))return false;
        var block=city.getBlockState(station.block()).getBlock();
        return switch(station.kind()) {
            case SOFA -> seat(block);
            case DESK -> block==NapoliFurniture.WORKTOP||block instanceof CraftingTableBlock;
            case TOILET -> block==NapoliFurniture.WC||block==Blocks.CAULDRON||block==Blocks.QUARTZ_STAIRS;
            case SINK -> block==NapoliFurniture.SINK||block==Blocks.WATER_CAULDRON;
            case SHOWER -> NapoliShower.isColumn(city,station.block().above());
            default -> false;
        };
    }
    static boolean occupied(ServerLevel city, BlockPos chair, LivingEntity actor) {
        return !city.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,new AABB(chair).inflate(.1),
                marker->CityMovement.isSeat(marker)&&marker.getPassengers().stream().anyMatch(other->other!=actor&&other.isAlive())).isEmpty();
    }
    static boolean reached(NapoliResident npc, Station station) {
        if(CityMovement.isSeat(npc.getVehicle())&&station.chair()!=null
                &&npc.getVehicle().blockPosition().equals(station.chair()))return true;
        var offset=npc.position().subtract(Vec3.atBottomCenterOf(station.stand()));
        // Walking can step onto the 15/16-block edge of a sofa before the sit action runs.
        if(offset.y<-.65||offset.y>(station.chair()!=null?1.4:.65)||offset.horizontalDistanceSqr()>2)return false;
        var end=Vec3.atCenterOf(station.block());
        var hit=npc.level().clip(new ClipContext(npc.getEyePosition(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(station.block())
                ||station.kind()==CityLivingHomes.Furniture.DESK&&station.chair()!=null&&hit.getBlockPos().equals(station.chair());
    }
    static List<Station> seats(ServerLevel city, NapoliMergellina.House home, boolean television) {
        var points=new ArrayList<Station>();var sofa=fixture(home,CityLivingHomes.Furniture.SOFA);
        points.add(new Station(sofa.kind(),sofa.block(),sofa.stand(),sofa.block()));
        addSeat(points,home,4,0,8,5,8);
        addSeat(points,home,4,0,10,5,10);
        if(!television&&home.id().startsWith("atlas_"))for(int floor=1;floor<home.floors();floor++) {
            addSeat(points,home,4,floor*5,11,4,12);
            addSeat(points,home,12,floor*5,19,11,19);
        }
        return points.stream().filter(p->valid(city,p)).toList();
    }
    private static void addSeat(List<Station> points,NapoliMergellina.House h,int x,int dy,int z,int sx,int sz) {
        var chair=new BlockPos(h.x()+x,h.ground()+dy,h.z()+z);
        points.add(new Station(CityLivingHomes.Furniture.SOFA,chair,new BlockPos(h.x()+sx,h.ground()+dy,h.z()+sz),chair));
    }
    static List<Station> desks(ServerLevel city, NapoliMergellina.House home) {
        var points=new ArrayList<Station>();points.add(fixture(home,CityLivingHomes.Furniture.DESK));
        if(home.id().startsWith("atlas_"))for(int floor=1;floor<home.floors();floor++) {
            var block=new BlockPos(home.x()+19,home.ground()+floor*5,home.z()+4);
            points.add(new Station(CityLivingHomes.Furniture.DESK,block,block.south(2),block.south()));
        }
        return points.stream().filter(p->valid(city,p)).map(p->{
            if(p.chair()!=null&&seat(city.getBlockState(p.chair()).getBlock()))return p;
            for(var at:List.of(p.block().north(),p.block().south(),p.block().east(),p.block().west(),p.block().east().south()))
                if(city.hasChunkAt(at)&&seat(city.getBlockState(at).getBlock())&&at.distSqr(p.stand())<=6)
                    return new Station(p.kind(),p.block(),p.stand(),at);
            return new Station(p.kind(),p.block(),p.stand(),null);
        }).toList();
    }
    static BlockPos preferredBed(NapoliMergellina.House home, NapoliCivicLife.Citizen citizen, long day) {
        boolean child=citizen.age(day)<NapoliCivicLife.ADULT_AGE;
        if(home.id().startsWith("atlas_")&&home.floors()>1) {
            int floor=1+Math.floorMod(citizen.id().hashCode(),home.floors()-1);
            return new BlockPos(home.x()+(child?18:citizen.woman()?15:13),home.ground()+floor*5,home.z()+(child?8:5));
        }
        var point=CityLivingHomes.points(home).stream().filter(p->p.furniture()==CityLivingHomes.Furniture.BED
                &&p.room()==(child?CityLivingHomes.Room.CHILD_BEDROOM:CityLivingHomes.Room.DOUBLE_BEDROOM)).findFirst().orElseThrow();
        return point.block().east(citizen.woman()?1:0);
    }
    static BlockPos waypoint(NapoliResident npc,NapoliMergellina.House home,BlockPos goal){
        if(Math.abs(goal.getY()-npc.getY())<1.5)return goal;
        boolean atlas=home.id().startsWith("atlas_");
        int x=home.x()+1,z=home.z()+(atlas?home.depth()-3:1),stride=home.style()<0?4:5;
        int floor=Math.clamp((int)Math.round((npc.getY()-home.ground())/stride),0,home.floors()-1);
        var landing=new BlockPos(x,home.ground()+floor*stride,z);
        if(!npc.level().hasChunkAt(landing))return goal;
        if(!npc.level().getBlockState(landing).is(net.minecraft.tags.BlockTags.CLIMBABLE)
                &&!npc.level().getBlockState(landing.below()).is(net.minecraft.tags.BlockTags.CLIMBABLE))return goal;
        double dx=npc.getX()-x-.5,dz=npc.getZ()-z-.5;
        // Solve the room, vertical column and destination room as short physical paths.
        return dx*dx+dz*dz>4?landing:new BlockPos(x,goal.getY(),z);
    }
    private CityHomeStations() {}
}
