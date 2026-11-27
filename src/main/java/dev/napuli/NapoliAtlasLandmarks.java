package dev.napuli;

import static dev.napuli.NapoliMonumentInteriors.material;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Individual plans for the Vomero museums, Donn'Anna and the Teatro Mediterraneo. */
final class NapoliAtlasLandmarks {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> FLOOR = Set.of(Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE,
            Blocks.CHISELED_STONE_BRICKS, Blocks.GRASS_BLOCK);
    private static final List<Block> STONE = List.of(Blocks.AIR, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
            Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE,
            Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF_STAIRS, Blocks.CALCITE, Blocks.BRICK_SLAB, Blocks.SMOOTH_QUARTZ,
            Blocks.QUARTZ_PILLAR, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS,
            Blocks.FLOWER_POT, Blocks.DECORATED_POT, Blocks.IRON_BARS, Blocks.TERRACOTTA, Blocks.CRIMSON_STAIRS,
            material("red_wool"), material("yellow_terracotta"), material("red_terracotta"),
            material("light_gray_stained_glass"), material("black_concrete"), material("ochre_froglight"));

    private NapoliAtlasLandmarks() {}

    static BlockPos floridiana() { return origin("floridiana", -10, -25); }
    static BlockPos santelmo() { return origin("santelmo", -16, -11).above(8); }
    static BlockPos donnanna() { return origin("donnanna", -26, -22); }
    static BlockPos mostra() { return origin("mostra", -38, -35); }

    static void upgrade(ServerLevel city) {
        floridiana(city);
        santelmo(city);
        donnanna(city);
        teatroMediterraneo(city);
    }

    private static void floridiana(ServerLevel city) {
        var start = floridiana();
        generate(city, start, 20, 17);
        if (!FLOOR.contains(city.getBlockState(start.offset(10, 0, 7)).getBlock())) return;
        for (var pos : BlockPos.betweenClosed(start.above(), start.offset(20, 8, 17))) {
            remove(city, pos, Blocks.OAK_LOG);
            remove(city, pos, Blocks.OAK_LEAVES);
        }
        NapoliMonumentInteriors.place(city, "floridiana_duca_di_martina", start,
                (pos, old) -> pos.getY() == start.getY() && FLOOR.contains(old.getBlock()), STONE);
    }

    private static void santelmo(ServerLevel city) {
        var start = santelmo();
        generate(city, start, 35, 20);
        if (!city.getBlockState(start.offset(15, 0, 8)).is(Blocks.TUFF_BRICKS)) return;
        NapoliMonumentInteriors.place(city, "santelmo_museo_novecento", start,
                pos -> pos.getY() != start.getY() || city.getBlockState(pos).is(Blocks.TUFF_BRICKS),
                (pos, old) -> pos.getY() == start.getY() && old.is(Blocks.TUFF_BRICKS), STONE);
    }

    private static void donnanna(ServerLevel city) {
        var start = donnanna();
        var plaster = material("yellow_terracotta");
        var shell = NapoliCapodimonteSanita.shell(city, start, 51, 41, 24, Set.of(plaster));
        retireFront(city, start, shell, 51, 41, 2, Blocks.SMOOTH_QUARTZ, 8, 23, 25);
        NapoliMonumentInteriors.place(city, "donnanna_loggia", start, pos -> inShell(shell, start, pos, 41), (pos, old) -> {
            int z = pos.getZ() - start.getZ();
            if (z == 41) return old.is(plaster) || old.is(Blocks.GLASS_PANE) || old.is(Blocks.QUARTZ_PILLAR);
            return pos.getY() == start.getY() && FLOOR.contains(old.getBlock());
        }, STONE);
    }

    private static void teatroMediterraneo(ServerLevel city) {
        var start = mostra();
        var shell = NapoliCapodimonteSanita.shell(city, start, 75, 27, 17, Set.of(Blocks.SMOOTH_SANDSTONE));
        retireFront(city, start, shell, 75, 27, 1, Blocks.SMOOTH_QUARTZ, 8, 16, 18);
        NapoliMonumentInteriors.place(city, "mostra_teatro_mediterraneo", start, pos -> inShell(shell, start, pos, 27), (pos, old) ->
                pos.getY() == start.getY() && FLOOR.contains(old.getBlock())
                || pos.getZ() == start.getZ() + 27 && (old.is(Blocks.SMOOTH_SANDSTONE) || old.is(Blocks.GLASS_PANE)), STONE);
    }

    private static boolean inShell(Set<ChunkPos> shell, BlockPos start, BlockPos pos, int front) {
        return shell.contains(ChunkPos.containing(pos.north(Math.max(0, pos.getZ() - start.getZ() - front))));
    }

    private static void retireFront(ServerLevel city, BlockPos start, Set<ChunkPos> shell, int right, int front, int setback,
            Block pilaster, int... courses) {
        for (int x = -1; x <= right + 1; x++) {
            if (!shell.contains(ChunkPos.containing(start.offset(Math.clamp(x, 0, right), 0, front)))) continue;
            for (int y : courses) remove(city, start.offset(x, y, front + setback), Blocks.SMOOTH_STONE_SLAB);
            if (x < 2 || x >= right || (x - 2) % 6 != 0 || Math.abs(x - (right + 1) / 2) < 4) continue;
            remove(city, start.offset(x, 2, front + setback), Blocks.POLISHED_ANDESITE);
            for (int y = 1; y < courses[courses.length - 1]; y++)
                remove(city, start.offset(x, y, front + setback), pilaster);
        }
    }

    static void generate(ServerLevel city, BlockPos start, int width, int depth) {
        for (int cx = start.getX() >> 4; cx <= (start.getX() + width) >> 4; cx++)
            for (int cz = start.getZ() >> 4; cz <= (start.getZ() + depth) >> 4; cz++)
                NapoliExpansion.generate(city, city.getChunk(cx, cz));
    }

    private static void remove(ServerLevel city, BlockPos pos, Block previous) {
        if (city.getBlockState(pos).is(previous) && !CityProperty.playerPlaced(city, pos))
            city.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private static BlockPos origin(String id, int dx, int dz) {
        var site = NapoliAtlas.find(id);
        return new BlockPos(site.x() + dx, NapoliExpansion.height(site.x(), site.z()), site.z() + dz);
    }
}
