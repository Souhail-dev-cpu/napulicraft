package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class FootballGround {
    static final int MID_X = 399, MID_Z = 33, MIN_X = 363, MAX_X = 435, MIN_Z = 10, MAX_Z = 56;
    static final BlockPos STAND = new BlockPos(MID_X, 70, 3);
    static final BlockPos START_RUN = new BlockPos(354, 64, 19), FINISH_RUN = new BlockPos(354, 64, 35);

    private FootballGround() {}

    static void build(ServerLevel level) {
        clearOldFixtures(level);
        Block green = colour("green_concrete"), light = colour("green_concrete_powder");
        for (int x = 358; x <= MAX_X + 5; x++) for (int z = 7; z <= MAX_Z + 5; z++) {
            int goalDepth = Math.min(x - MIN_X, MAX_X - x);
            int fromCenter = Math.abs(z - MID_Z);
            boolean inside = goalDepth >= 0 && z >= MIN_Z && z <= MAX_Z;
            boolean line = inside && (goalDepth == 0 || z == MIN_Z || z == MAX_Z || x == MID_X
                    || boxLine(goalDepth, fromCenter, 11, 13) || boxLine(goalDepth, fromCenter, 4, 7)
                    || fromCenter == 0 && goalDepth == 8
                    || Math.abs(Math.hypot(x - MID_X, z - MID_Z) - 6) < .5);
            boolean runoff = x >= MIN_X - 3 && x <= MAX_X + 3 && z >= MIN_Z - 2 && z <= MAX_Z + 2;
            set(level, new BlockPos(x, 63, z), line ? Blocks.CALCITE
                    : runoff ? ((x / 6) % 2 == 0 ? green : light) : Blocks.POLISHED_ANDESITE);
        }
        for (int x : new int[]{MIN_X, MAX_X}) {
            int back = x + (x == MIN_X ? -3 : 3);
            for (int z : new int[]{MID_Z - 3, MID_Z + 3}) {
                for (int y = 64; y <= 67; y++) set(level, new BlockPos(x, y, z), Blocks.QUARTZ_PILLAR);
                for (int xx = Math.min(x, back); xx <= Math.max(x, back); xx++)
                    if (xx != x) for (int y = 64; y <= 67; y++) set(level, new BlockPos(xx, y, z), colour("white_stained_glass"));
            }
            for (int z = MID_Z - 3; z <= MID_Z + 3; z++) {
                set(level, new BlockPos(x, 67, z), Blocks.SMOOTH_QUARTZ);
                for (int y = 64; y <= 67; y++) set(level, new BlockPos(back, y, z), colour("white_stained_glass"));
            }
        }
        for (int x = 375; x <= 423; x++) for (int row = 0; row < 6; row++) {
            for (int y = 64; y <= 64 + row; y++)
                set(level, new BlockPos(x, y, 8 - row), Blocks.POLISHED_ANDESITE);
            if ((x - 375) % 12 > 1)
                set(level, new BlockPos(x, 64 + row, 8 - row), colour(row % 2 == 0 ? "light_blue_concrete" : "blue_concrete"));
        }
        for (int x : new int[]{369, 429}) for (int z : new int[]{7, 59}) {
            for (int y = 64; y <= 75; y++) set(level, new BlockPos(x, y, z), Blocks.POLISHED_DEEPSLATE_WALL);
            for (int dx = -1; dx <= 1; dx++) set(level, new BlockPos(x + dx, 76, z), Blocks.SEA_LANTERN);
        }
        for (int z = 19; z <= 35; z++) {
            set(level, new BlockPos(354, 63, z), z == 19 || z == 35 ? Blocks.CALCITE : colour("red_terracotta"));
        }
    }

    private static boolean boxLine(int depth, int width, int front, int side) {
        return (depth == front && width <= side) || (width == side && depth <= front);
    }

    private static void clearOldFixtures(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(361, 64, 20, 407, 67, 26)) {
            if (p.getX() != 361 && p.getX() != 363 && p.getX() != 405 && p.getX() != 407) continue;
            var state = level.getBlockState(p);
            if (state.is(Blocks.QUARTZ_PILLAR) || state.is(Blocks.SMOOTH_QUARTZ) || state.is(Blocks.GLASS))
                set(level, p, Blocks.AIR);
        }
        for (int x = 372; x <= 396; x++) for (int row = 0; row < 3; row++) {
            BlockPos p = new BlockPos(x, 64, 9 - row);
            for (int y = 0; y <= row; y++) {
                var state = level.getBlockState(p.above(y));
                if (state.is(Blocks.SMOOTH_STONE) || state.is(Blocks.POLISHED_ANDESITE)) set(level, p.above(y), Blocks.AIR);
            }
        }
        for (int x : new int[]{368, 400}) for (int y = 64; y <= 69; y++) {
            BlockPos p = new BlockPos(x, y, 8);
            var state = level.getBlockState(p);
            if (state.is(Blocks.ANDESITE_WALL) || state.is(Blocks.LANTERN)) set(level, p, Blocks.AIR);
        }
    }

    private static Block colour(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
    }

    private static void set(ServerLevel level, BlockPos at, Block block) {
        level.setBlock(at, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
