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

/** Refit the Megaride halls and the rooms inside the existing MANN shell. */
final class NapoliOvoMuseum {
    static final BlockPos COLONNE = new BlockPos(-2, 58, 88);
    static final BlockPos ITALIA = new BlockPos(7, 64, 96);
    private static final Set<Block> CASTLE_MASONRY = Set.of(Blocks.TUFF, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF,
            Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private NapoliOvoMuseum() {}

    static void upgrade(ServerLevel city) {
        NapoliMonumentInteriors.place(city, "ovo_sala_delle_colonne", COLONNE,
                (pos, present) -> CASTLE_MASONRY.contains(present.getBlock()),
                List.of(Blocks.CAVE_AIR, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF_STAIRS, Blocks.TUFF_BRICKS,
                        Blocks.CHISELED_TUFF_BRICKS, Blocks.QUARTZ_PILLAR, Blocks.CUT_SANDSTONE, Blocks.LANTERN,
                        Blocks.TUFF_BRICK_WALL, Blocks.SMOOTH_SANDSTONE_STAIRS));
        NapoliMonumentInteriors.place(city, "ovo_sala_italia", ITALIA,
                (pos, present) -> pos.getY() == ITALIA.getY() && CASTLE_MASONRY.contains(present.getBlock()),
                List.of(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_TUFF, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
                        Blocks.CHISELED_TUFF_BRICKS, Blocks.DARK_OAK_STAIRS, Blocks.LANTERN, material("iron_chain")));
        museum(city);
    }

    static BlockPos museumFloor() {
        var p = NapoliAtlas.find("mann");
        return new BlockPos(p.x() - p.radius() + 7, NapoliExpansion.height(p.x(), p.z()), p.z() - 22);
    }

    private static void museum(ServerLevel city) {
        var floor = museumFloor();
        var redWall = material("red_terracotta");
        var shell = new HashSet<ChunkPos>();
        for (int cx = floor.getX() >> 4; cx <= (floor.getX() + 49) >> 4; cx++) {
            for (int cz = floor.getZ() >> 4; cz <= (floor.getZ() + 42) >> 4; cz++) {
                var chunk = city.getChunk(cx, cz);
                NapoliExpansion.generate(city, chunk);
                var sample = new BlockPos(Math.clamp(cx * 16 + 8, floor.getX(), floor.getX() + 49),
                        floor.getY() + 22, Math.clamp(cz * 16 + 8, floor.getZ(), floor.getZ() + 40));
                if (city.getBlockState(sample).is(redWall)) shell.add(chunk.getPos());
            }
        }
        for (int z = 4; z < 24; z++) for (int x : new int[]{20, 30})
            clearLegacy(city, shell, floor.offset(x, 9, z), Blocks.IRON_BARS);
        for (int y = 9; y < 21; y++)
            clearLegacy(city, shell, floor.offset(28, y, 20), material("iron_chain"));
        NapoliMonumentInteriors.place(city, "mann_percorso_interni", floor,
                pos -> shell.contains(ChunkPos.containing(pos)), (pos, present) -> museumFinish(floor, pos, present),
                List.of(Blocks.POLISHED_GRANITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_QUARTZ,
                        Blocks.CHISELED_QUARTZ_BLOCK, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.QUARTZ_PILLAR,
                        Blocks.POLISHED_BLACKSTONE, Blocks.TERRACOTTA, Blocks.DECORATED_POT, Blocks.SMOOTH_QUARTZ_STAIRS,
                        Blocks.DARK_OAK_FENCE, Blocks.LANTERN, material("iron_chain"), material("white_stained_glass"),
                        material("ochre_froglight"), material("waxed_cut_copper"), material("light_blue_terracotta"),
                        material("yellow_terracotta"), material("white_terracotta")));
    }

    private static void clearLegacy(ServerLevel city, Set<ChunkPos> shell, BlockPos pos, Block material) {
        if (shell.contains(ChunkPos.containing(pos)) && city.getBlockState(pos).is(material)
                && !CityProperty.playerPlaced(city, pos)) city.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private static boolean museumFinish(BlockPos floor, BlockPos pos, BlockState present) {
        int y = pos.getY() - floor.getY();
        if (y == 0) return present.is(Blocks.POLISHED_ANDESITE) || present.is(Blocks.CHISELED_STONE_BRICKS);
        if (y == 8) return present.is(Blocks.POLISHED_DIORITE);
        if (y == 9 && pos.getZ() == floor.getZ() + 24) return present.is(Blocks.IRON_BARS);
        return y == 21 && present.is(material("iron_chain"));
    }
}
