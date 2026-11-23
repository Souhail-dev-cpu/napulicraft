package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Wider approaches and new blocks around the existing district anchors. */
final class NapoliCityScale {
    private final ServerLevel level;

    private NapoliCityScale(ServerLevel level) { this.level = level; }

    static void build(ServerLevel level) {
        var city = new NapoliCityScale(level);
        city.centro();
        city.quartieri();
        city.plebiscito();
        city.waterfront();
        city.municipio();
        Napuli.LOGGER.info("Napoli v18: expanded Centro, Quartieri, Plebiscito, waterfront and Municipio");
    }

    private void centro() {
        paving(-33, -251, 35, -208, false);
        paving(-6, -251, 6, -205, true);
        palazzo(-29, -247, 16, 12, 4, "yellow_terracotta");
        palazzo(-12, -247, 15, 12, 3, "white_terracotta");
        palazzo(13, -247, 19, 12, 4, "pink_terracotta");
        palazzo(16, -229, 18, 11, 3, "orange_terracotta");
        lamp(-19, -227);
        lamp(20, -213);
        sign(-8, -212, "PIAZZA DANTE", "Verso il Convitto");
        sign(9, -231, "VIA TOLEDO", "Piazza Dante");
    }

    private void quartieri() {
        paving(-128, -70, -68, -22, false);
        paving(-126, -53, -64, -46, true);
        paving(-108, -65, -103, -24, true);
        palazzo(-124, -69, 14, 12, 3, "red_terracotta");
        palazzo(-101, -69, 15, 12, 4, "yellow_terracotta");
        palazzo(-124, -42, 14, 13, 4, "pink_terracotta");
        palazzo(-101, -42, 15, 13, 3, "orange_terracotta");
        lamp(-116, -46);
        lamp(-92, -46);
        sign(-78, -45, "QUARTIERI", "Vico dei balconi");
    }

    private void plebiscito() {
        // Frame the existing monument axes with two broader pedestrian approaches.
        paving(-32, -1, 24, 13, false);
        paving(-33, 40, 27, 48, false);
        for (int x = -32; x <= 26; x++) {
            for (int z : new int[]{8, 10, 41, 43})
                if (Math.floorMod(x, 8) < 6) replacePaving(x, z, Blocks.POLISHED_DIORITE);
        }
        for (int x : new int[]{-31, -7, 11, 26}) lamp(x, 44);
        sign(-29, 7, "PIAZZA DEL", "PLEBISCITO");
    }

    private void waterfront() {
        // Keep the view towards the bay open and follow the existing shoreline.
        for (int x = -107; x <= 175; x++) {
            int edge = x <= 73 ? 56 : 60;
            for (int z = 51; z <= edge; z++) {
                Block surface = z == edge ? Blocks.CALCITE
                        : Math.floorMod(x, 9) <= 1 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE;
                BlockPos pos = new BlockPos(x, 63, z);
                if (x >= 74 && z >= 57 && level.getBlockState(pos).is(Blocks.WATER)
                        && level.getBlockState(pos.above()).isAir()) {
                    put(x, 62, z, Blocks.STONE_BRICKS);
                    put(x, 63, z, surface);
                } else {
                    replacePaving(x, z, surface);
                }
            }
            if (Math.floorMod(x, 24) == 8) lamp(x, edge - 1);
        }
        sign(72, 54, "LUNGOMARE", "Mergellina / Porto");
    }

    private void municipio() {
        paving(277, -9, 348, 62, false);
        paving(275, -4, 348, 4, true);
        paving(306, 3, 315, 63, true);
        palazzo(294, 11, 12, 16, 3, "white_terracotta");
        palazzo(318, 11, 20, 16, 4, "light_gray_terracotta");
        palazzo(319, 34, 25, 16, 3, "yellow_terracotta");
        lamp(310, 22);
        lamp(310, 48);
        sign(310, 7, "MUNICIPIO", "Verso Beverello");
    }

    private void palazzo(int x, int z, int width, int depth, int floors, String colour) {
        int endX = x + width - 1, endZ = z + depth - 1, roof = 64 + floors * 5;
        for (BlockPos p : BlockPos.betweenClosed(x, 64, z, endX, roof, endZ + 1))
            if (!level.getBlockState(p).isAir()) return;

        Block facade = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(colour));
        rectangle(x, 63, z, endX, endZ, Blocks.SPRUCE_PLANKS);
        for (int y = 64; y <= roof; y++) {
            int floor = (y - 64) % 5;
            Block band = y == roof ? Blocks.BRICKS : floor == 4 ? Blocks.CALCITE : facade;
            for (int bx = x; bx <= endX; bx++) {
                int bay = (bx - x) % 4;
                boolean entrance = Math.abs(bx - (x + width / 2)) <= 1 && y <= 66;
                boolean window = y < roof && (floor == 1 || floor == 2) && (bay == 1 || bay == 2);
                if (!entrance) put(bx, y, endZ, window ? Blocks.GLASS : band);
                put(bx, y, z, window ? Blocks.GLASS : band);
                if (floor == 0 && y > 64 && y < roof && bay > 0 && bx < endX)
                    put(bx, y, endZ + 1, Blocks.BRICK_SLAB);
                if (floor == 1 && y > 65 && bay > 0 && bx < endX)
                    put(bx, y, endZ + 1, Blocks.IRON_BARS);
            }
            for (int bz = z; bz <= endZ; bz++) {
                put(x, y, bz, band);
                put(endX, y, bz, band);
            }
            if (floor == 4 || y == roof) rectangle(x + 1, y, z + 1, endX - 1, endZ - 1,
                    y == roof ? Blocks.BRICKS : Blocks.SPRUCE_PLANKS);
        }
        for (int y = 64; y < roof; y++) {
            BlockPos landing = new BlockPos(x + 1, y, z + 2);
            level.setBlock(landing, Blocks.LADDER.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                    net.minecraft.core.Direction.EAST), Block.UPDATE_CLIENTS);
        }
        for (int y = 69; y < roof; y += 5) {
            put(endX - 2, y, z + 2, Blocks.BOOKSHELF);
            put(endX - 3, y, z + 2, Blocks.BARREL);
            put(x + 2, y + 3, endZ - 2, Blocks.LANTERN);
        }
        put(x + 2, 67, endZ - 2, Blocks.LANTERN);
        put(endX - 2, 64, endZ - 2, Blocks.BARREL);
    }

    private void paving(int minX, int minZ, int maxX, int maxZ, boolean road) {
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            replacePaving(x, z, road ? Blocks.POLISHED_DEEPSLATE
                    : Math.floorMod(x + 3 * z, 13) == 0 ? Blocks.ANDESITE : Blocks.POLISHED_ANDESITE);
    }

    private void replacePaving(int x, int z, Block material) {
        BlockPos pos = new BlockPos(x, 63, z);
        if (!level.getBlockState(pos.above()).isAir()) return;
        Block old = level.getBlockState(pos).getBlock();
        if (old == Blocks.SAND || old == Blocks.STONE_BRICKS || old == Blocks.SMOOTH_STONE
                || old == Blocks.POLISHED_ANDESITE || old == Blocks.ANDESITE || old == Blocks.TUFF
                || old == Blocks.POLISHED_DEEPSLATE) put(x, 63, z, material);
    }

    private void lamp(int x, int z) {
        BlockPos pos = new BlockPos(x, 64, z);
        if (!level.getBlockState(pos.below()).isSolidRender()) return;
        for (int y = 0; y <= 3; y++) if (!level.getBlockState(pos.above(y)).isAir()) return;
        for (int y = 64; y < 67; y++) put(x, y, z, Blocks.DEEPSLATE_BRICK_WALL);
        put(x, 67, z, Blocks.LANTERN);
    }

    private void sign(int x, int z, String first, String second) {
        BlockPos pos = new BlockPos(x, 64, z);
        if (level.getBlockState(pos).isAir()) NapoliEmploymentMap.sign(level, pos, first, second);
    }

    private void rectangle(int x, int y, int z, int endX, int endZ, Block block) {
        for (int bx = x; bx <= endX; bx++) for (int bz = z; bz <= endZ; bz++) put(bx, y, bz, block);
    }

    private void put(int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
