package dev.napuli;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

final class NapoliLandmarkAccess {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> FORT_RAMP = Set.of(Blocks.TUFF_BRICKS, Blocks.STONE_BRICK_STAIRS,
            Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF_SLAB, Blocks.STONE_BRICK_WALL);
    private static final Set<Block> PALACE_RAMP = Set.of(Blocks.SMOOTH_STONE, Blocks.SANDSTONE, Blocks.POLISHED_ANDESITE);

    private NapoliLandmarkAccess() {}

    static void upgrade(ServerLevel city) {
        repairFortRamp(city);
        connectPalaceEntrance(city);
    }

    private static void repairFortRamp(ServerLevel city) {
        var fort = NapoliAtlas.find("santelmo");
        var ground = new BlockPos(fort.x(), NapoliExpansion.height(fort.x(), fort.z()), fort.z());
        if (!city.getBlockState(ground.offset(0, 1, 10)).is(Blocks.TUFF_BRICKS)) return;
        var step = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int run = 0; run <= 14; run++) {
            int rise = 1 + run / 2;
            var finish = run % 2 == 0 ? step : Blocks.POLISHED_TUFF.defaultBlockState();
            for (int x = -2; x <= 2; x++) {
                var tread = ground.offset(x, rise, 20 - run);
                put(city, tread, finish, FORT_RAMP);
                for (int head = 1; head <= 3; head++)
                    put(city, tread.above(head), Blocks.AIR.defaultBlockState(), FORT_RAMP);
            }
        }
    }

    private static void connectPalaceEntrance(ServerLevel city) {
        var palace = NapoliAtlas.find("donnanna");
        var arrival = palace.arrival();
        var landing = new BlockPos(palace.x(), NapoliExpansion.height(palace.x(), palace.z()), palace.z() + 26);
        NapoliAtlasLandmarks.generate(city, landing.west(1), 2, arrival.getZ() - landing.getZ());
        var step = Blocks.POLISHED_TUFF_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        int approachY = approach(city, palace).getY();
        int rise = landing.getY() + 1 - approachY;
        int terraceEdge = landing.getZ() + 1;
        for (int z = arrival.getZ() - 1; z > terraceEdge; z--) {
            if (surface(city, new BlockPos(landing.getX(), landing.getY() + 1, z)).getY() >= landing.getY() + 1) {
                terraceEdge = z;
                break;
            }
        }
        for (int row = 0; row < rise; row++) {
            int z = Math.min(arrival.getZ() - 1, terraceEdge + rise - 1) - row, y = approachY + row;
            for (int dx = -1; dx <= 1; dx++) {
                var tread = new BlockPos(landing.getX() + dx, y, z);
                for (int foundation = approachY; foundation < y; foundation++)
                    put(city, tread.atY(foundation), Blocks.TUFF_BRICKS.defaultBlockState(), PALACE_RAMP);
                put(city, tread, step, PALACE_RAMP);
            }
        }
    }

    static BlockPos approach(ServerLevel city, NapoliAtlas.Place site) {
        return surface(city, site.arrival());
    }

    private static BlockPos surface(ServerLevel city, BlockPos arrival) {
        for (int offset = 1; offset >= -16; offset--) {
            var feet = arrival.offset(0, offset, 0);
            var floor = feet.below();
            if (city.isEmptyBlock(feet) && city.isEmptyBlock(feet.above())
                    && city.getBlockState(floor).isFaceSturdy(city, floor, Direction.UP)) return feet;
        }
        return arrival;
    }

    private static void put(ServerLevel city, BlockPos pos, BlockState state, Set<Block> legacy) {
        if (CityProperty.playerPlaced(city, pos) || city.getBlockEntity(pos) != null) return;
        var previous = city.getBlockState(pos);
        if ((previous.isAir() || legacy.contains(previous.getBlock())) && previous != state)
            city.setBlock(pos, state, FLAGS);
    }
}
