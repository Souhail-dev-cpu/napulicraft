package dev.napuli;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Finishes the historic promenade and the approaches to the square on existing maps. */
final class NapoliWaterfrontFinish {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> PAVING = Set.of(Blocks.SAND, Blocks.GRASS_BLOCK, Blocks.SMOOTH_STONE,
            Blocks.POLISHED_ANDESITE, Blocks.ANDESITE, Blocks.CALCITE, Blocks.TUFF, Blocks.STONE_BRICKS,
            Blocks.POLISHED_BASALT, Blocks.POLISHED_DIORITE, Blocks.COBBLED_DEEPSLATE);
    private final ServerLevel level;

    private NapoliWaterfrontFinish(ServerLevel level) {
        this.level = level;
    }

    static void upgrade(ServerLevel level) {
        var build = new NapoliWaterfrontFinish(level);
        build.square();
        build.palaceFacade();
        build.promenade();
        build.fountain();
        build.hotel(-111, -101, CityBlocks.vanilla("white_terracotta"), Blocks.DARK_OAK_PLANKS, "HOTEL EXCELSIOR", 4);
        build.hotel(-98, -89, Blocks.SMOOTH_SANDSTONE, Blocks.SPRUCE_PLANKS, "GRAND HOTEL VESUVIO", 3);
        build.borgo();
    }

    private void square() {
        // Basoli in long courses, with a light fan only on the axis of the colonnade.
        for (int x = -42; x <= 30; x++) {
            for (int z = 8; z <= 44; z++) {
                double radius = Math.hypot(x - NapoliPlebiscito.CX, z - NapoliPlebiscito.CZ);
                boolean fan = x >= -39 && x <= -11 && (Math.abs(radius - 11) < 0.49
                        || Math.abs(radius - 17) < 0.49);
                paving(x, z, fan ? Blocks.POLISHED_DIORITE
                        : Math.floorMod(z, 4) == 0 ? Blocks.ANDESITE : Blocks.POLISHED_ANDESITE);
            }
        }
        // A continuous stone plinth and cornice give the open portico visible depth.
        for (int side : new int[] {-1, 1}) {
            for (double degrees = 40; degrees <= 77; degrees += 0.5) {
                double angle = Math.toRadians(degrees) * side;
                int x = NapoliPlebiscito.CX - (int) Math.round((NapoliPlebiscito.R_IN - 1) * Math.cos(angle));
                int z = NapoliPlebiscito.CZ + (int) Math.round((NapoliPlebiscito.R_IN - 1) * Math.sin(angle));
                if (!level.getBlockState(new BlockPos(x, 73, z)).isAir()) {
                    put(x, 73, z, Blocks.CHISELED_QUARTZ_BLOCK);
                    if (level.getBlockState(new BlockPos(x, 74, z)).isAir())
                        put(x, 74, z, Blocks.SMOOTH_QUARTZ_SLAB);
                }
            }
        }
        for (int z : new int[] {18, 34}) {
            for (int x = -20; x <= -16; x++) {
                for (int side : new int[] {-1, 1}) {
                    var pedestal = new BlockPos(x, 68, z + side);
                    if (level.getBlockState(pedestal).is(Blocks.POLISHED_DIORITE))
                        put(x, 68, z + side, Blocks.CHISELED_STONE_BRICKS);
                }
            }
        }
    }

    private void palaceFacade() {
        // Frame the existing arches without filling the entrances or replacing the statues.
        for (int z = 7; z <= 42; z += 5) {
            if (!level.getBlockState(new BlockPos(33, 65, z)).is(Blocks.STONE_BRICKS)) continue;
            for (int y = 64; y <= 68; y++) {
                if (!level.getBlockState(new BlockPos(32, y, z)).isAir()) continue;
                put(32, y, z, y == 64 || y == 68 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE);
            }
        }
        for (int z = 7; z <= 45; z++) {
            if (level.getBlockState(new BlockPos(33, 70, z)).is(Blocks.STONE_BRICKS)
                    && level.getBlockState(new BlockPos(32, 70, z)).isAir())
                put(32, 70, z, Blocks.POLISHED_ANDESITE_SLAB);
        }
    }

    private void promenade() {
        // Keep the old slope towards Megaride and the open lanes at the bridge and at the piers.
        for (int x = -247; x <= 175; x++) {
            if (Math.abs(x) <= 5) continue;
            int edge = x < -108 ? 57 : Math.abs(x) <= 73
                    ? Math.max(56, 55 + (int) (9 * Math.pow(Math.abs(x) / 73.0, 1.6)))
                    : x < -73 ? 56 : 60;
            for (int z = 51; z <= edge; z++) {
                Block finish = z == edge ? Blocks.POLISHED_DIORITE
                        : z == 52 ? Blocks.ANDESITE : Blocks.SMOOTH_STONE;
                paving(x, z, finish);
            }
            if ((x >= -226 && x <= -221) || (x >= -191 && x <= -186)) continue;
            var rim = new BlockPos(x, 63, edge);
            if (!level.getBlockState(rim).is(Blocks.POLISHED_DIORITE)) continue;
            BlockState rail = level.getBlockState(rim.above());
            if (rail.isAir() || rail.is(Blocks.STONE_BRICK_WALL)) {
                for (int y = 61; y < 63; y++) {
                    var wall = new BlockPos(x, y, edge);
                    var previous = level.getBlockState(wall);
                    if (previous.isAir() || previous.is(Blocks.WATER) || previous.is(Blocks.SAND)
                            || previous.is(Blocks.COBBLESTONE) || previous.is(Blocks.STONE_BRICKS))
                        put(x, y, edge, Blocks.STONE_BRICKS);
                }
                // Handrail runs across the outer paving, keeping the inner lane free of obstacles.
                put(x, 64, edge, Math.floorMod(x, 6) == 0 ? Blocks.POLISHED_ANDESITE
                        : Blocks.POLISHED_ANDESITE_SLAB);
            }
        }
        // North end of the causeway remains level with the square.
        for (int x = -3; x <= 3; x++)
            for (int z = 46; z <= 67; z++) paving(x, z, Blocks.POLISHED_ANDESITE);
    }

    private void fountain() {
        int cx = -61, front = 52;
        if (!vacant(cx - 6, cx + 6, front - 5, front, 75)) return;
        fill(cx - 6, 63, front - 5, cx + 6, 63, front, Blocks.POLISHED_ANDESITE);
        fill(cx - 6, 64, front - 4, cx + 6, 64, front, Blocks.SMOOTH_QUARTZ);
        for (int x : new int[] {cx - 6, cx - 2, cx + 2, cx + 6}) {
            fill(x, 65, front - 4, x, 69, front - 4, Blocks.QUARTZ_PILLAR);
            put(x, 70, front - 4, Blocks.CHISELED_QUARTZ_BLOCK);
        }
        for (int arch : new int[] {cx - 4, cx, cx + 4}) {
            for (int dx = -1; dx <= 1; dx++)
                fill(arch + dx, 71 - Math.abs(dx), front - 4, arch + dx, 72, front - 4, Blocks.SMOOTH_QUARTZ);
            put(arch, 73, front - 4, Blocks.CHISELED_QUARTZ_BLOCK);
            fill(arch - 1, 65, front - 2, arch + 1, 65, front, Blocks.SMOOTH_QUARTZ_SLAB);
            put(arch, 65, front - 1, Blocks.WATER);
            put(arch, 65, front - 2, Blocks.SMOOTH_QUARTZ);
        }
        put(cx, 74, front - 4, Blocks.CHISELED_QUARTZ_BLOCK);
    }

    private void hotel(int x1, int x2, Block plaster, Block floor, String name, int floors) {
        int back = 30, front = 43, center = (x1 + x2) / 2, roof = 63 + 5 * floors;
        if (!vacant(x1 - 1, x2 + 1, back - 1, front + 2, roof + 3)) {
            Napuli.LOGGER.info("Napoli: skipped {} because its plot is occupied", name);
            return;
        }
        for (int y = 63; y <= roof; y++) {
            int tier = (y - 63) % 5;
            if (tier == 0) fill(x1, y, back, x2, y, front, y == roof ? Blocks.POLISHED_ANDESITE : floor);
            for (int x = x1; x <= x2; x++) {
                int bay = Math.floorMod(x - center, 4);
                boolean entrance = y >= 64 && y <= 66 && Math.abs(x - center) <= 1;
                boolean window = tier == 2 || tier == 3;
                Block wall = tier == 0 ? Blocks.SMOOTH_QUARTZ : y < 68 ? Blocks.POLISHED_ANDESITE : plaster;
                put(x, y, front, entrance ? Blocks.AIR : window && bay == 0 ? Blocks.GLASS : wall);
                put(x, y, back, window && bay == 0 ? Blocks.GLASS : wall);
                if (y >= 69 && window && (bay == 1 || bay == 3))
                    put(x, y, front, Blocks.SMOOTH_QUARTZ);
                if (y > 64 && tier == 1 && (bay == 0 || floors == 4 && bay != 2)) {
                    put(x, y - 1, front + 1, Blocks.SMOOTH_QUARTZ);
                    put(x, y, front + 1, Blocks.POLISHED_BLACKSTONE_WALL);
                }
            }
            for (int z = back + 1; z < front; z++) {
                Block wall = tier == 0 ? Blocks.SMOOTH_QUARTZ : plaster;
                if ((tier == 2 || tier == 3) && (z - back) % 4 == 2) wall = Blocks.GLASS;
                put(x1, y, z, wall);
                put(x2, y, z, wall);
            }
        }
        fill(x1 - 1, roof + 1, back, x2 + 1, roof + 1, back, Blocks.SMOOTH_QUARTZ);
        fill(x1 - 1, roof + 1, front, x2 + 1, roof + 1, front, Blocks.SMOOTH_QUARTZ);
        fill(x1 - 1, roof, back, x1 - 1, roof, front, Blocks.SMOOTH_QUARTZ);
        fill(x2 + 1, roof, back, x2 + 1, roof, front, Blocks.SMOOTH_QUARTZ);
        fill(center - 2, 67, front, center + 2, 67, front + 2, Blocks.SMOOTH_QUARTZ);
        for (int x : new int[] {center - 2, center + 2}) {
            fill(x, 64, front + 2, x, 66, front + 2, Blocks.QUARTZ_PILLAR);
            put(x, 67, front + 2, Blocks.CHISELED_QUARTZ_BLOCK);
        }
        for (int x : new int[] {x1, x2}) {
            for (int y = 64; y <= roof; y++) put(x, y, front, y % 5 == 3
                    ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.SMOOTH_QUARTZ);
        }
        fill(center - 2, 63, front + 1, center + 2, 63, 51, Blocks.POLISHED_ANDESITE);
        NapoliEmploymentMap.sign(level, new BlockPos(center + 3, 64, front + 1), name, "Via Partenope");
        fill(center + 2, 64, front - 4, x2 - 2, 64, front - 4, Blocks.DARK_OAK_PLANKS);
        put(center + 2, 65, front - 4, Blocks.LANTERN);
        put(center - 2, 64, front - 3, Blocks.DARK_OAK_STAIRS);
        put(center - 2, 64, front - 6, Blocks.DARK_OAK_STAIRS);
        for (int story = 0; story < floors; story++) {
            int y = 63 + story * 5;
            put(center, y + 1, back + 3, Blocks.LANTERN);
            if (story + 1 < floors) {
                // One flight per floor with a landing connected to the corridor along the back wall.
                boolean north = story % 2 == 0;
                int first = north ? front - 3 : back + 4;
                int step = north ? -1 : 1;
                for (int i = 0; i < 5; i++) {
                    int z = first + step * i;
                    put(x1 + 2, y + 1 + i, z, Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, north ? Direction.NORTH : Direction.SOUTH));
                    if (i >= 1 && i < 4) put(x1 + 2, y + 5, z, Blocks.AIR);
                }
            }
            if (story == 0) continue;
            for (int x = x1 + 5; x < x2 - 3; x += 5) {
                fill(x, y + 1, back + 6, x, y + 4, front - 1, plaster);
                put(x + 1, y + 1, front - 2, Blocks.DARK_OAK_STAIRS);
                put(x + 2, y + 1, front - 2, Blocks.DARK_OAK_PLANKS);
                put(x + 2, y + 2, front - 2, Blocks.LANTERN);
                BlockState bed = CityBlocks.vanilla("blue_bed").defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
                put(x + 3, y + 1, front - 3, bed);
                put(x + 3, y + 1, front - 2, bed.setValue(BlockStateProperties.BED_PART,
                        net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            }
        }
    }

    private void borgo() {
        for (int building = 0; building < 4; building++) {
            int x = 19 + 7 * building;
            for (int floor = 1; floor < (building % 2 == 0 ? 3 : 4); floor++) {
                int y = 65 + floor * 4;
                for (int dx : new int[] {2, 4}) {
                    var rear = new BlockPos(x + dx, y, 91);
                    if (!Set.of(CityBlocks.vanilla("white_terracotta"), CityBlocks.vanilla("pink_terracotta"), CityBlocks.vanilla("orange_terracotta"),
                                    CityBlocks.vanilla("yellow_terracotta")).contains(level.getBlockState(rear).getBlock())) continue;
                    put(x + dx, y, 91, Blocks.GLASS);
                    put(x + dx, y + 1, 91, Blocks.GLASS);
                    put(x + dx, y - 1, 91, Blocks.SMOOTH_QUARTZ);
                }
            }
        }
    }

    private boolean vacant(int x1, int x2, int z1, int z2, int top) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, 64, z1, x2, top, z2)) {
            if (!level.getBlockState(pos).isAir() || CityProperty.playerPlaced(level, pos)) return false;
        }
        return true;
    }

    private void paving(int x, int z, Block finish) {
        var pos = new BlockPos(x, 63, z);
        if (PAVING.contains(level.getBlockState(pos).getBlock()) && level.getBlockState(pos.above()).isAir())
            put(x, 63, z, finish);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2))
            put(pos.getX(), pos.getY(), pos.getZ(), block);
    }

    private void put(int x, int y, int z, Block block) {
        put(x, y, z, block.defaultBlockState());
    }

    private void put(int x, int y, int z, BlockState state) {
        var pos = new BlockPos(x, y, z);
        if (!CityProperty.playerPlaced(level, pos) && level.getBlockEntity(pos) == null)
            level.setBlock(pos, state, FLAGS);
    }
}
