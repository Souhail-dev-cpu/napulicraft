package dev.napuli;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Piazza Municipio, Castel Nuovo and the port extend the city beyond the old eastern boundary. */
final class NapoliMunicipio {
    static final BlockPos GARAGE = new BlockPos(193, 64, -13);
    static final BlockPos ARMORY = new BlockPos(193, 64, 15);
    static final BlockPos MINE = new BlockPos(272, 64, -29);
    static final BlockPos PIER = new BlockPos(239, 64, 65);
    static final BlockPos CASTLE = new BlockPos(216, 64, 34);
    record Vein(BlockPos pos, Block ore) {}
    private static final List<Vein> VEINS = veins();
    private final ServerLevel level;

    private NapoliMunicipio(ServerLevel level) { this.level = level; }

    static void build(ServerLevel level) {
        NapoliMunicipio city = new NapoliMunicipio(level);
        city.streets();
        city.shop(181, -21, true);
        city.shop(181, 8, false);
        city.castle();
        city.port();
        city.mine();
        Napuli.LOGGER.info("Napoli v9: Municipio, Castel Nuovo, Beverello and mine built");
    }

    private void streets() {
        fill(177, 63, -55, 292, 63, 74, Blocks.STONE_BRICKS);
        fill(177, 63, -4, 292, 63, 4, Blocks.POLISHED_DEEPSLATE);
        fill(202, 63, 6, 220, 63, 58, Blocks.SMOOTH_STONE);
        fill(203, 63, -32, 290, 63, -26, Blocks.POLISHED_DEEPSLATE);
        for (int x = 204; x <= 257; x += 17) {
            NapoliMap.buildHome(level, x, -50, x);
            NapoliMap.buildHome(level, x, -22, x + 1);
        }
        for (int z = 10; z <= 52; z += 8) {
            fill(203, 63, z, 219, 63, z, Blocks.CALCITE);
            fill(205, 64, z + 1, 209, 64, z + 3, Blocks.SMOOTH_SANDSTONE);
            fill(206, 65, z + 2, 208, 65, z + 2, Blocks.AZALEA_LEAVES);
            lamp(219, z + 1);
        }
        sign(179, 64, 2, "PIAZZA MUNICIPIO", "Molo Beverello", "Officina / Armeria");
        sign(212, 64, -26, "GALLERIA MINERARIA", "verso est");
    }

    private void shop(int x, int z, boolean garage) {
        Block plaster = garage ? Blocks.SMOOTH_SANDSTONE : Blocks.TERRACOTTA;
        fill(x, 64, z, x + 21, 77, z + 15, plaster);
        fill(x + 1, 64, z + 1, x + 20, 76, z + 14, Blocks.AIR);
        int wall = garage ? z + 15 : z;
        fill(x + 8, 64, wall, x + 15, 68, wall, Blocks.AIR);
        for (int y : new int[]{69, 76}) fill(x - 1, y, z - 1, x + 22, y, z + 16, Blocks.CALCITE);
        fill(x + 1, 70, z + 1, x + 20, 70, z + 14, Blocks.SPRUCE_PLANKS);
        for (int i = 2; i <= 19; i += 4) {
            fill(x + i, 72, wall, x + i + 1, 74, wall, Blocks.GLASS);
            if (i < 6 || i > 14) fill(x + i, 65, wall, x + i + 1, 67, wall, Blocks.GLASS);
        }
        fill(x - 1, 78, z - 1, x + 22, 78, z + 16, Blocks.BRICKS);
        int counter = garage ? z + 3 : z + 12;
        fill(x + 8, 64, counter, x + 16, 64, counter, Blocks.DARK_OAK_SLAB);
        for (int i : new int[]{4, 17}) put(x + i, 65, counter, Blocks.LANTERN);
        for (int i : new int[]{3, 18})
            fill(x + i, 64, z + 6, x + i + 1, 64, z + 10, garage ? Blocks.IRON_BLOCK : Blocks.SPRUCE_PLANKS);
        sign(x + 7, 64, wall + (garage ? 1 : -1), garage ? "OFFICINA" : "ARMERIA", garage ? "Caschi / motorini" : "Equipaggiamento");
    }

    private void castle() {
        // Round piperno towers flank the white entrance, with a fifth tower on the harbor side.
        fill(223, 64, 18, 263, 80, 53, Blocks.POLISHED_BLACKSTONE_BRICKS);
        fill(226, 64, 21, 260, 80, 50, Blocks.AIR);
        fill(223, 64, 32, 225, 71, 39, Blocks.AIR);
        fill(222, 64, 29, 222, 78, 42, Blocks.SMOOTH_QUARTZ);
        for (int z = 32; z <= 39; z++) {
            int arch = 71 + Math.min(z - 32, 39 - z);
            fill(222, 64, z, 222, arch, z, Blocks.AIR);
        }
        for (int z : new int[]{29, 42}) fill(221, 64, z, 221, 77, z, Blocks.QUARTZ_PILLAR);
        for (int x = 224; x <= 263; x += 3)
            for (int z : new int[]{18, 53}) put(x, 81, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        for (int z = 19; z <= 52; z += 3)
            for (int x : new int[]{223, 263}) put(x, 81, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        tower(223, 18);
        tower(223, 53);
        tower(263, 18);
        tower(263, 53);
        tower(263, 35);
        sign(CASTLE.getX(), 64, CASTLE.getZ(), "CASTEL NUOVO", "Maschio Angioino");
        lamp(229, 27);
        lamp(255, 43);
    }

    private void tower(int cx, int cz) {
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            int radius = dx * dx + dz * dz;
            if (radius > 64) continue;
            fill(cx + dx, 64, cz + dz, cx + dx, 67, cz + dz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            if (radius >= 38) fill(cx + dx, 68, cz + dz, cx + dx, 84, cz + dz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            put(cx + dx, 85, cz + dz, Blocks.POLISHED_BLACKSTONE);
            if (radius >= 46 && (dx + dz) % 3 == 0)
                put(cx + dx, 86, cz + dz, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        }
    }

    private void port() {
        fill(177, 61, 75, 293, 63, 111, Blocks.WATER);
        fill(185, 63, 60, 288, 63, 74, Blocks.SMOOTH_STONE);
        fill(229, 63, 74, 248, 63, 96, Blocks.STONE_BRICKS);
        fill(219, 64, 61, 251, 68, 63, Blocks.GLASS);
        fill(218, 69, 60, 252, 69, 69, Blocks.SMOOTH_QUARTZ);
        for (int x = 219; x <= 251; x += 4) fill(x, 64, 68, x, 68, 68, Blocks.QUARTZ_PILLAR);
        for (int x = 257; x <= 282; x += 12) {
            fill(x - 3, 64, 80, x + 3, 65, 99, Blocks.DARK_OAK_PLANKS);
            fill(x - 2, 66, 83, x + 2, 68, 96, Blocks.SMOOTH_QUARTZ);
            fill(x - 1, 67, 85, x + 1, 67, 94, Blocks.GLASS);
            fill(x - 2, 69, 87, x + 2, 69, 94, Blocks.QUARTZ_SLAB);
            put(x, 70, 89, Blocks.REDSTONE_BLOCK);
        }
        sign(PIER.getX(), 64, PIER.getZ(), "MOLO BEVERELLO", "Terminal traghetti");
    }

    private void mine() {
        fill(264, 63, -33, 279, 63, -25, Blocks.ANDESITE);
        for (int dx : new int[]{-2, 2}) fill(272 + dx, 64, -30, 272 + dx, 68, -30, Blocks.TUFF_BRICKS);
        fill(270, 68, -30, 274, 68, -30, Blocks.TUFF_BRICKS);
        sign(269, 64, -29, "GALLERIA MINERARIA", "Ingresso libero", "Ferro / rame");
        for (int step = 0; step <= 15; step++) {
            int y = 63 - step, z = -30 - step;
            fill(271, y + 1, z, 273, y + 4, z, Blocks.AIR);
            for (int x = 271; x <= 273; x++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
            }
            if (step % 5 == 0) put(273, y + 2, z, Blocks.SEA_LANTERN);
        }
        fill(245, 48, -65, 290, 48, -46, Blocks.POLISHED_TUFF);
        fill(246, 49, -64, 289, 53, -46, Blocks.AIR);
        for (int x = 249; x <= 287; x += 7) {
            for (int z : new int[]{-63, -48}) fill(x, 49, z, x, 53, z, Blocks.SPRUCE_LOG);
            fill(x, 53, -63, x, 53, -48, Blocks.SPRUCE_PLANKS);
            put(x, 52, -55, Blocks.SEA_LANTERN);
        }
        for (Vein vein : VEINS) put(vein.pos().getX(), vein.pos().getY(), vein.pos().getZ(), vein.ore());
    }

    private static List<Vein> veins() {
        var out = new ArrayList<Vein>();
        for (int x = 246; x < 290; x += 2) {
            out.add(new Vein(new BlockPos(x, 50, -65), Blocks.IRON_ORE));
            out.add(new Vein(new BlockPos(x, 51, -65), x % 4 == 0 ? Blocks.COAL_ORE : Blocks.IRON_ORE));
            out.add(new Vein(new BlockPos(x, 50, -46), Blocks.COPPER_ORE));
            out.add(new Vein(new BlockPos(x, 51, -46), x % 4 == 0 ? Blocks.COAL_ORE : Blocks.COPPER_ORE));
        }
        return List.copyOf(out);
    }

    static boolean vein(BlockPos pos, Block block) {
        return VEINS.stream().anyMatch(v -> v.pos().equals(pos) && v.ore() == block);
    }

    static void refill(ServerLevel level) {
        for (Vein v : VEINS) if (level.hasChunkAt(v.pos()) && level.getBlockState(v.pos()).isAir())
            level.setBlock(v.pos(), v.ore().defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private void lamp(int x, int z) {
        fill(x, 64, z, x, 67, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
        put(x, 68, z, Blocks.LANTERN);
    }

    private void sign(int x, int y, int z, String... lines) {
        BlockPos pos = new BlockPos(x, y, z);
        put(x, y, z, Blocks.SPRUCE_SIGN);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity block) {
            var text = SignText.EMPTY.asMutable();
            for (int i = 0; i < lines.length; i++) text.setLine(i, Component.literal(lines[i]));
            block.setText(text.asImmutable(), SignTextSlot.FRONT);
            block.setText(text.asImmutable(), SignTextSlot.BACK);
            block.setChanged();
        }
    }

    private void put(int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        var state = block.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2))
            level.setBlock(p, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
