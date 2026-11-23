package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class FootballGround {
    static final int MID_X = 384, MID_Z = 23, MIN_X = 363, MAX_X = 405, MIN_Z = 10, MAX_Z = 36;
    static final BlockPos STAND = new BlockPos(384, 67, 7), GYM = new BlockPos(358, 64, 15);
    static final BlockPos START_RUN = new BlockPos(354, 64, 19), FINISH_RUN = new BlockPos(354, 64, 35);

    private FootballGround() {}

    static void build(ServerLevel level) {
        Block green = colour("green_concrete"), light = colour("green_concrete_powder");
        for (int x = 348; x <= 410; x++) for (int z = -4; z <= 41; z++) {
            BlockPos floor = new BlockPos(x, 63, z);
            if (!level.getBlockState(floor).is(Blocks.SAND)) continue;
            boolean turf = x >= 359 && x <= 409 && z >= 9 && z <= 38;
            boolean line = x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z
                    && (x == MIN_X || x == MAX_X || z == MIN_Z || z == MAX_Z || x == MID_X
                    || (Math.abs(z - MID_Z) <= 5 && (x == MIN_X + 4 || x == MAX_X - 4))
                    || (Math.abs(z - MID_Z) == 5 && (x < MIN_X + 4 || x > MAX_X - 4))
                    || Math.abs(Math.hypot(x - MID_X, z - MID_Z) - 4) < .5);
            Block material = line ? Blocks.CALCITE : turf ? ((x / 4) % 2 == 0 ? green : light)
                    : z <= 4 ? Blocks.POLISHED_DEEPSLATE : Blocks.POLISHED_ANDESITE;
            set(level, floor, material);
        }
        for (int x : new int[]{MIN_X, MAX_X}) {
            int behind = x + (x == MIN_X ? -2 : 2);
            for (int z : new int[]{MID_Z - 3, MID_Z + 3}) for (int y = 64; y <= 67; y++)
                set(level, new BlockPos(x, y, z), Blocks.QUARTZ_PILLAR);
            for (int z = MID_Z - 3; z <= MID_Z + 3; z++) {
                set(level, new BlockPos(x, 67, z), Blocks.SMOOTH_QUARTZ);
                for (int y = 64; y <= 66; y++) set(level, new BlockPos(behind, y, z), Blocks.GLASS);
                set(level, new BlockPos(behind, 67, z), Blocks.SMOOTH_QUARTZ);
            }
        }
        for (int x = 372; x <= 396; x++) for (int row = 0; row < 3; row++) {
            for (int y = 64; y <= 64 + row; y++) {
                set(level, new BlockPos(x, y, 9 - row), x % 2 == 0 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE);
            }
        }
        for (int x : new int[]{368, 400}) {
            for (int y = 64; y <= 68; y++) set(level, new BlockPos(x, y, 8), Blocks.ANDESITE_WALL);
            set(level, new BlockPos(x, 69, 8), Blocks.LANTERN);
        }
        for (int z = 19; z <= 35; z++) {
            set(level, new BlockPos(354, 63, z), z == 19 || z == 35 ? Blocks.CALCITE : colour("red_terracotta"));
        }
        NapoliEmploymentMap.sign(level, new BlockPos(365, 64, 3), "FUORIGROTTA", "Campo allenamento", "/calcio guarda");
        NapoliEmploymentMap.sign(level, new BlockPos(355, 64, 19), "PREPARAZIONE", "/calcio palestra", "Corri fino in fondo");
        buildGym(level);
        NapoliEmploymentMap.sign(level, GYM, "PALESTRA", "/calcio palestra", "Corsia esterna");
    }

    private static void buildGym(ServerLevel level) {
        for (int x = 349; x <= 357; x++) for (int z = 9; z <= 16; z++) {
            set(level, new BlockPos(x, 63, z), Blocks.SPRUCE_PLANKS);
            set(level, new BlockPos(x, 68, z), Blocks.DEEPSLATE_BRICKS);
            boolean northWindow = z == 9 && x >= 351 && x <= 355;
            boolean wall = x == 349 || x == 357 || z == 9 || z == 16;
            if (!wall || (z == 16 && x >= 353 && x <= 355)) continue;
            for (int y = 64; y <= 67; y++) set(level, new BlockPos(x, y, z),
                    northWindow && (y == 65 || y == 66) ? Blocks.GLASS : Blocks.SMOOTH_SANDSTONE);
        }
        for (int x : new int[]{351, 355}) {
            set(level, new BlockPos(x, 64, 11), colour("blue_carpet"));
            set(level, new BlockPos(x, 64, 12), colour("blue_carpet"));
            set(level, new BlockPos(x, 64, 13), colour("blue_carpet"));
            set(level, new BlockPos(x, 64, 14), Blocks.SPRUCE_SLAB);
        }
        set(level, new BlockPos(350, 64, 10), Blocks.BARREL);
        set(level, new BlockPos(356, 64, 10), Blocks.BARREL);
        set(level, new BlockPos(353, 64, 10), Blocks.SEA_LANTERN);
        NapoliEmploymentMap.sign(level, new BlockPos(356, 64, 17), "SPOGLIATOIO", "Corsia a sinistra");
    }

    private static Block colour(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
    }

    private static void set(ServerLevel level, BlockPos at, Block block) {
        level.setBlock(at, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
