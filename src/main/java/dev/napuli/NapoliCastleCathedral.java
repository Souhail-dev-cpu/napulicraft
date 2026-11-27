package dev.napuli;

import static dev.napuli.NapoliMonumentInteriors.material;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class NapoliCastleCathedral {
    static final BlockPos BARONI = new BlockPos(235, 63, 21);
    static final BlockPos PALATINA = new BlockPos(233, 63, 40);
    private static final Set<Block> CASTLE_PAVING = Set.of(Blocks.STONE_BRICKS, Blocks.SMOOTH_STONE, Blocks.CALCITE);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private NapoliCastleCathedral() {}

    static void upgrade(ServerLevel city) {
        castle(city);
        cathedral(city);
    }

    static BlockPos cathedralFloor() {
        var place = NapoliAtlas.find("duomo");
        return new BlockPos(place.x() - 15, NapoliExpansion.height(place.x(), place.z()), place.z() - 18);
    }

    private static void castle(ServerLevel city) {
        NapoliMonumentInteriors.place(city, "arco_castel_nuovo", new BlockPos(220, 63, 29),
                (pos, present) -> present.is(Blocks.QUARTZ_PILLAR) && pos.getX() == 221,
                List.of(Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.SMOOTH_QUARTZ));
        NapoliMonumentInteriors.place(city, "sala_dei_baroni", BARONI, (pos, present) -> false,
                List.of(Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF_BRICKS, Blocks.POLISHED_ANDESITE,
                        Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_SANDSTONE,
                        Blocks.GLASS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, material("ochre_froglight")));
        NapoliMonumentInteriors.place(city, "cappella_palatina", PALATINA,
                (pos, present) -> pos.getY() == PALATINA.getY() && CASTLE_PAVING.contains(present.getBlock()),
                List.of(Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_SANDSTONE, Blocks.TUFF_BRICKS, Blocks.SMOOTH_QUARTZ,
                        Blocks.CHISELED_QUARTZ_BLOCK, Blocks.POLISHED_GRANITE, Blocks.QUARTZ_PILLAR,
                        Blocks.DARK_OAK_STAIRS, material("blue_stained_glass"), material("yellow_stained_glass"),
                        material("ochre_froglight")));
        for (BlockPos pos : BlockPos.betweenClosed(226, 63, 33, 243, 63, 38)) {
            if (CASTLE_PAVING.contains(city.getBlockState(pos).getBlock()) && !CityProperty.playerPlaced(city, pos))
                city.setBlock(pos, Blocks.POLISHED_ANDESITE.defaultBlockState(), FLAGS);
        }
    }

    private static void cathedral(ServerLevel city) {
        var floor = cathedralFloor();
        var shellChunks = new HashSet<ChunkPos>();
        for (int cx = (floor.getX() - 1) >> 4; cx <= (floor.getX() + 30) >> 4; cx++) {
            for (int cz = (floor.getZ() - 1) >> 4; cz <= (floor.getZ() + 34) >> 4; cz++) {
                var chunk = city.getChunk(cx, cz);
                NapoliExpansion.generate(city, chunk);
                var sample = new BlockPos(Math.clamp(cx * 16 + 8, floor.getX(), floor.getX() + 29),
                        floor.getY() + 23, Math.clamp(cz * 16 + 8, floor.getZ(), floor.getZ() + 32));
                if (city.getBlockState(sample).is(Blocks.CALCITE)) shellChunks.add(chunk.getPos());
            }
        }
        for (int x : new int[]{9, 21}) for (int z : new int[]{10, 14, 18, 22, 26}) {
            var pos = floor.offset(x, 1, z);
            if (shellChunks.contains(ChunkPos.containing(pos)) && city.getBlockState(pos).is(Blocks.SPRUCE_STAIRS)
                    && !CityProperty.playerPlaced(city, pos)) city.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        NapoliMonumentInteriors.place(city, "duomo_napoli_interni", floor,
                pos -> shellChunks.contains(ChunkPos.containing(pos)), (pos, present) -> cathedralFinish(floor, pos, present),
                List.of(Blocks.POLISHED_DIORITE, Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_ANDESITE, Blocks.CALCITE,
                        Blocks.POLISHED_GRANITE, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.DARK_OAK_STAIRS,
                        Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.GOLD_BLOCK, Blocks.DARK_OAK_PLANKS, Blocks.LANTERN,
                        Blocks.WATER_CAULDRON, material("blue_terracotta"), material("ochre_froglight"), material("red_carpet"),
                        material("iron_chain"), material("blue_stained_glass"), material("yellow_stained_glass")));
    }

    private static boolean cathedralFinish(BlockPos floor, BlockPos pos, BlockState present) {
        int y = pos.getY() - floor.getY();
        if (y == 0) return present.is(Blocks.POLISHED_ANDESITE) || present.is(Blocks.POLISHED_DIORITE)
                || present.is(Blocks.SMOOTH_STONE);
        if (y == 1 && present.is(Blocks.SPRUCE_STAIRS)) return true;
        return y == 22 && present.is(material("iron_chain"));
    }
}
