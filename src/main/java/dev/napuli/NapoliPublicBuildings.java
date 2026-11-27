package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class NapoliPublicBuildings {
    private final ServerLevel level;

    private NapoliPublicBuildings(ServerLevel level) { this.level = level; }

    static void upgrade(ServerLevel level) {
        var buildings = new NapoliPublicBuildings(level);
        buildings.station();
        buildings.prison();
        Napuli.LOGGER.info("Napoli v19: rebuilt Carabinieri station and prison");
    }

    private void station() {
        int front = 10;
        Block plaster = block("yellow_terracotta");
        fill(92, 64, front, 118, 74, front, Blocks.SMOOTH_SANDSTONE);
        fill(93, 70, front, 117, 73, front, plaster);
        fill(91, 75, front - 1, 119, 75, front + 1, Blocks.SMOOTH_SANDSTONE);
        fill(92, 76, front, 118, 76, front, Blocks.SANDSTONE_SLAB);
        for (int centre : new int[]{97, 105, 113}) {
            arch(centre, front, 64, 2, 3, Blocks.POLISHED_ANDESITE, Blocks.AIR);
            fill(centre - 1, 70, front, centre + 1, 72, front, Blocks.CUT_SANDSTONE);
            fill(centre, 70, front, centre, 72, front, Blocks.GLASS);
            put(centre, 69, front, Blocks.POLISHED_ANDESITE);
            for (int x : new int[]{centre - 3, centre + 3}) {
                fill(x, 64, front - 1, x, 67, front - 1, Blocks.POLISHED_ANDESITE);
                put(x, 68, front - 1, Blocks.CHISELED_STONE_BRICKS);
            }
            put(centre, 68, front - 1, Blocks.LANTERN);
        }
        for (int x = 104; x <= 106; x++) {
            replace(x, 69, 9, block("green_wall_banner"), Blocks.AIR);
            replace(x, 69, 9, block("white_wall_banner"), Blocks.AIR);
            replace(x, 69, 9, block("red_wall_banner"), Blocks.AIR);
            replace(x, 64, 14, Blocks.SPRUCE_PLANKS, Blocks.AIR);
        }
        replace(105, 64, 15, Blocks.SPRUCE_STAIRS, Blocks.AIR);
        replace(105, 67, 9, Blocks.SPRUCE_WALL_SIGN, Blocks.AIR);
        wallSign(105, 69, 9, "CASERMA", "PASTRENGO", "CARABINIERI");
        flag(105, 75, 9);
        fill(94, 63, 8, 116, 63, 13, Blocks.POLISHED_ANDESITE);

        // Public desk, waiting benches and a straight passage into the cloister.
        fill(96, 64, 14, 100, 64, 14, Blocks.DARK_OAK_PLANKS);
        fill(96, 65, 14, 100, 65, 14, Blocks.DARK_OAK_SLAB);
        wallSign(98, 65, 13, "SPORTELLO", "DENUNCE");
        for (int x = 110; x <= 115; x++) facing(x, 64, 13, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        for (int z = 16; z <= 31; z++) fill(104, 63, z, 106, 63, z, Blocks.POLISHED_DIORITE);
        arch(105, 30, 64, 2, 3, Blocks.POLISHED_ANDESITE, Blocks.AIR);
        for (int x : new int[]{94, 116}) for (int z : new int[]{33, 38}) {
            fill(x, 64, z, x, 68, z, Blocks.POLISHED_ANDESITE);
            put(x, 64, z + 1, Blocks.DECORATED_POT);
            put(x, 65, z + 1, Blocks.FLOWERING_AZALEA);
        }
        for (int x : new int[]{94, 116}) fill(x, 69, 31, x, 69, 39, Blocks.SMOOTH_SANDSTONE);
        for (int z = 31; z <= 39; z++) {
            put(93, 63, z, Blocks.POLISHED_ANDESITE);
            put(117, 63, z, Blocks.POLISHED_ANDESITE);
        }
    }

    private void prison() {
        int front = 9;
        Block green = block("green_terracotta");
        fill(123, 71, front, 151, 71, front, Blocks.STONE_BRICKS);
        fill(124, 64, front, 150, 69, front, Blocks.ANDESITE);
        fill(124, 70, front, 150, 78, front, Blocks.SMOOTH_SANDSTONE);
        fill(123, 79, front - 1, 151, 79, front + 4, Blocks.POLISHED_ANDESITE);
        for (int x : new int[]{124, 150})
            fill(x, 70, front + 1, x, 78, front + 3, Blocks.SMOOTH_SANDSTONE);
        fill(124, 70, front + 4, 150, 78, front + 4, Blocks.SMOOTH_SANDSTONE);
        for (int centre : new int[]{128, 136, 144}) {
            arch(centre, front, 64, 2, 3, Blocks.POLISHED_ANDESITE, centre == 136 ? Blocks.AIR : green);
            if (centre != 136) {
                fill(centre - 1, 66, front, centre + 1, 67, front, Blocks.IRON_BARS);
                for (int x = centre - 1; x <= centre + 1; x++) for (int y = 66; y <= 67; y++) {
                    BlockPos pos = new BlockPos(x, y, front);
                    level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_CLIENTS);
                }
            }
            fill(centre - 2, 73, front, centre + 2, 77, front, Blocks.CUT_SANDSTONE);
            fill(centre - 1, 74, front, centre + 1, 76, front, block("light_gray_stained_glass"));
            fill(centre - 1, 73, front - 1, centre + 1, 73, front - 1, Blocks.POLISHED_ANDESITE_SLAB);
            put(centre, 68, front - 1, Blocks.LANTERN);
        }
        replace(133, 68, 8, Blocks.SPRUCE_WALL_SIGN, Blocks.AIR);
        wallSign(136, 72, 8, "CASA", "CIRCONDARIALE", "POGGIOREALE");
        flag(136, 78, 8);
        fill(124, 63, 7, 150, 63, 8, Blocks.POLISHED_ANDESITE);
        fill(134, 63, 9, 138, 63, 15, Blocks.POLISHED_ANDESITE);
        arch(136, 15, 64, 2, 3, Blocks.POLISHED_ANDESITE, Blocks.AIR);
        wallSign(136, 69, 14, "PADIGLIONE", "NAPOLI");

        // The existing cells retain their entrances and teleport destinations.
        fill(135, 63, 16, 137, 63, 44, Blocks.SMOOTH_STONE);
        for (int i = 0; i < NapoliDistrict.CELLS.size(); i++) {
            BlockPos cell = NapoliDistrict.CELLS.get(i);
            int frame = cell.getX() < 136 ? 134 : 138;
            int side = frame < 136 ? 1 : -1;
            int z = cell.getZ();
            for (int dz : new int[]{-2, 2}) fill(frame, 64, z + dz, frame, 68, z + dz, Blocks.POLISHED_ANDESITE);
            put(frame, 68, z, Blocks.CHISELED_STONE_BRICKS);
            put(cell.getX(), 67, z - 1, Blocks.LANTERN);
            put(cell.getX() - side, 64, z + 1, Blocks.SMOOTH_STONE_SLAB);
            Direction direction = side == 1 ? Direction.EAST : Direction.WEST;
            sign(frame + side, 67, z, direction, "CELLA " + (i + 1));
        }
        arch(136, 44, 64, 2, 2, Blocks.POLISHED_ANDESITE, Blocks.AIR);
        wallSign(136, 68, 43, "CORTILE", "ORA D’ARIA");
        // Mark out a compact exercise court beside the existing benches.
        fill(125, 63, 46, 149, 63, 50, Blocks.POLISHED_ANDESITE);
        for (int z : new int[]{46, 50}) fill(127, 63, z, 147, 63, z, Blocks.CALCITE);
        for (int x : new int[]{127, 147}) fill(x, 63, 47, x, 63, 49, Blocks.CALCITE);
        put(137, 63, 48, Blocks.CALCITE);
        put(137, 67, 48, Blocks.IRON_BARS);
        put(137, 67, 49, Blocks.SMOOTH_QUARTZ);
        for (int x : new int[]{125, 149}) {
            put(x, 64, 51, Blocks.ANDESITE_WALL);
            put(x, 65, 51, Blocks.LANTERN);
        }
    }

    private void arch(int x, int z, int y, int halfWidth, int straight, Block trim, Block infill) {
        for (int dx = -halfWidth - 1; dx <= halfWidth + 1; dx++) {
            int distance = Math.abs(dx);
            int height = straight + (distance < halfWidth ? 1 : 0);
            if (distance > halfWidth) {
                fill(x + dx, y, z, x + dx, y + straight, z, trim);
            } else {
                fill(x + dx, y, z, x + dx, y + height - 1, z, infill);
                put(x + dx, y + height, z, trim);
            }
        }
    }

    private void flag(int x, int y, int z) {
        for (int i = 0; i < 3; i++) {
            Block banner = block(new String[]{"red_wall_banner", "white_wall_banner", "green_wall_banner"}[i]);
            facing(x + i - 1, y, z, banner, Direction.NORTH);
        }
    }

    private void wallSign(int x, int y, int z, String... lines) {
        sign(x, y, z, Direction.NORTH, lines);
    }

    private void sign(int x, int y, int z, Direction facing, String... lines) {
        facing(x, y, z, Blocks.SPRUCE_WALL_SIGN, facing);
        if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof SignBlockEntity sign) {
            var text = SignText.EMPTY.asMutable();
            for (int i = 0; i < lines.length; i++) text.setLine(i, Component.literal(lines[i]));
            sign.setText(text.asImmutable(), SignTextSlot.FRONT);
            sign.setChanged();
        }
    }

    private void replace(int x, int y, int z, Block old, Block replacement) {
        if (level.getBlockState(new BlockPos(x, y, z)).is(old)) put(x, y, z, replacement);
    }

    private void facing(int x, int y, int z, Block block, Direction facing) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), Block.UPDATE_CLIENTS);
    }

    private void fill(int x, int y, int z, int maxX, int maxY, int maxZ, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(x, y, z, maxX, maxY, maxZ))
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private void put(int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static Block block(String id) {
        return CityBlocks.vanilla(id);
    }
}
