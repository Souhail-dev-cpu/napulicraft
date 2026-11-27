package dev.napuli;

import static dev.napuli.NapoliMonumentInteriors.material;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Piazza Carlo III entrance and Pietrarsa's steam locomotive hall. */
final class NapoliAlbergoPietrarsa {
    private static final Block OLD_ALBERGO = material("yellow_terracotta");
    private static final Set<Block> OLD_FRONT = Set.of(OLD_ALBERGO, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.QUARTZ_PILLAR);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private NapoliAlbergoPietrarsa() {}

    static BlockPos albergo() { return origin("albergo", 48, 22); }
    static BlockPos pietrarsa() { return origin("pietrarsa", 43, 23); }

    private static BlockPos origin(String name, int west, int north) {
        var site = NapoliAtlas.find(name);
        return new BlockPos(site.x() - west, NapoliExpansion.height(site.x(), site.z()), site.z() - north);
    }

    static void upgrade(ServerLevel city) {
        rebuildAlbergo(city);
        rebuildPietrarsa(city);
    }

    private static void rebuildAlbergo(ServerLevel city) {
        var start = albergo();
        var shell = NapoliCapodimonteSanita.shell(city, start, 95, 41, 24, Set.of(OLD_ALBERGO));
        for (int x = -1; x <= 96; x++) {
            if (!shell.contains(ChunkPos.containing(start.offset(Math.clamp(x, 0, 95), 0, 41)))) continue;
            for (int y : new int[]{8, 23, 25}) remove(city, start.offset(x, y, 43), Blocks.SMOOTH_STONE_SLAB);
            if (Math.abs(x - 48) < 4 || x < 2 || x >= 95) continue;
            if ((x - 2) % 5 == 0) remove(city, start.offset(x, 3, 42), Blocks.POLISHED_ANDESITE_SLAB);
            if ((x - 2) % 6 == 0) {
                remove(city, start.offset(x, 2, 43), Blocks.POLISHED_ANDESITE);
                for (int y = 1; y <= 23; y++) remove(city, start.offset(x, y, 43), Blocks.SMOOTH_QUARTZ);
            }
        }
        NapoliMonumentInteriors.place(city, "albergo_dei_poveri", start, pos ->
                shell.contains(ChunkPos.containing(pos.atY(start.getY()).north(Math.max(0, pos.getZ() - start.getZ() - 41)))),
                (pos, old) -> {
                    int y = pos.getY() - start.getY(), z = pos.getZ() - start.getZ();
                    if (z == 41) return OLD_FRONT.contains(old.getBlock());
                    return y == 0 && (old.is(Blocks.POLISHED_ANDESITE) || old.is(Blocks.CHISELED_STONE_BRICKS));
                }, List.of(Blocks.AIR, Blocks.POLISHED_ANDESITE, material("pink_terracotta"), Blocks.SMOOTH_SANDSTONE,
                        Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE,
                        material("light_gray_stained_glass"), Blocks.CHISELED_QUARTZ_BLOCK, Blocks.POLISHED_GRANITE,
                        Blocks.POLISHED_DIORITE, Blocks.SMOOTH_STONE, Blocks.QUARTZ_PILLAR, material("ochre_froglight")));
    }

    private static void rebuildPietrarsa(ServerLevel city) {
        var start = pietrarsa();
        var shell = NapoliCapodimonteSanita.shell(city, start, 85, 42, 22, Set.of(Blocks.BRICKS));
        for (int x = -1; x <= 86; x++) {
            if (!shell.contains(ChunkPos.containing(start.offset(Math.clamp(x, 0, 85), 0, 42)))) continue;
            for (int y : new int[]{8, 21, 23}) remove(city, start.offset(x, y, 43), Blocks.SMOOTH_STONE_SLAB);
            remove(city, start.offset(x, 22, 43), Blocks.SMOOTH_QUARTZ_SLAB);
            if (Math.abs(x - 43) < 5 || x < 2 || x >= 85) continue;
            if ((x - 2) % 5 == 0) remove(city, start.offset(x, 3, 43), Blocks.POLISHED_ANDESITE_SLAB);
            if ((x - 2) % 6 == 0) {
                remove(city, start.offset(x, 2, 43), Blocks.POLISHED_ANDESITE);
                for (int y = 1; y <= 21; y++) remove(city, start.offset(x, y, 43), Blocks.CUT_SANDSTONE);
            }
        }
        for (int x = 5; x < 86; x += 12) removePendant(city, shell, start.offset(x, 0, 21), 7, 21);
        for (int x : new int[]{22, 64}) for (int z = 12; z <= 37; z += 5)
            removePendant(city, shell, start.offset(x, 0, z), 6, 21);
        for (int x = 25; x <= 61; x += 6) {
            var at = start.offset(x, 1, 23);
            if (!shell.contains(ChunkPos.containing(at))) continue;
            remove(city, at, Blocks.CHISELED_QUARTZ_BLOCK);
            remove(city, at.above(), Blocks.QUARTZ_PILLAR);
            remove(city, at.above(2), Blocks.CHISELED_QUARTZ_BLOCK);
        }
        for (int x = 18; x <= 68; x += 10) {
            var chimney = start.offset(x, 5, 17);
            if (shell.contains(ChunkPos.containing(chimney))) remove(city, chimney, Blocks.CAMPFIRE);
            for (var pos : BlockPos.betweenClosed(start.offset(x - 1, 2, 16), start.offset(x + 1, 4, 25)))
                if (shell.contains(ChunkPos.containing(pos))) remove(city, pos, material("black_concrete"));
        }
        NapoliMonumentInteriors.place(city, "pietrarsa_padiglione_a", start, pos -> shell.contains(ChunkPos.containing(pos)),
                (pos, old) -> {
                    int y = pos.getY() - start.getY(), z = pos.getZ() - start.getZ();
                    if (y >= 22 || z == 0 || z == 42) return old.is(Blocks.BRICKS) || old.is(Blocks.GLASS_PANE);
                    if (y == 0) return old.is(Blocks.POLISHED_ANDESITE) || old.is(Blocks.CHISELED_STONE_BRICKS);
                    return y == 1 && old.is(Blocks.RAIL);
                }, List.of(Blocks.AIR, Blocks.BRICKS, material("red_terracotta"), material("light_gray_stained_glass"),
                        Blocks.CUT_SANDSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.RED_NETHER_BRICKS,
                        material("ochre_froglight"), Blocks.POLISHED_GRANITE, Blocks.SMOOTH_STONE, Blocks.RAIL,
                        Blocks.POLISHED_DEEPSLATE, Blocks.CHISELED_POLISHED_BLACKSTONE,
                        material("yellow_terracotta"), material("green_terracotta")));
    }

    private static void removePendant(ServerLevel city, Set<ChunkPos> shell, BlockPos at, int first, int last) {
        if (!shell.contains(ChunkPos.containing(at))) return;
        remove(city, at.above(first), Blocks.LANTERN);
        for (int y = first + 1; y <= last; y++) remove(city, at.above(y), material("iron_chain"));
    }

    private static void remove(ServerLevel city, BlockPos pos, Block previous) {
        if (city.getBlockState(pos).is(previous) && !CityProperty.playerPlaced(city, pos))
            city.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }
}
