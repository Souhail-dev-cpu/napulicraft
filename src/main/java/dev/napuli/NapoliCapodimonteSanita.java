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

/** Refits built atlas shells without changing their coordinates or the surrounding streets. */
final class NapoliCapodimonteSanita {
    private static final Block OLD_PALACE = material("pink_terracotta");
    private static final Block OLD_DOME = material("oxidized_copper");
    private static final Set<Block> PALACE_FACADE = Set.of(OLD_PALACE, Blocks.GLASS_PANE, Blocks.GLASS, Blocks.QUARTZ_PILLAR);

    private NapoliCapodimonteSanita() {}

    static BlockPos capodimonte() { return corner("capodimonte", 42, 22); }
    static BlockPos sanita() { return corner("sanita", 16, 19); }

    private static BlockPos corner(String name, int west, int north) {
        var site = NapoliAtlas.find(name);
        return new BlockPos(site.x() - west, NapoliExpansion.height(site.x(), site.z()), site.z() - north);
    }

    static void upgrade(ServerLevel city) {
        var palace = capodimonte();
        var palaceShell = shell(city, palace, 83, 41, 24, Set.of(OLD_PALACE));
        for (int x = -1; x <= 84; x++) {
            if (!palaceShell.contains(ChunkPos.containing(palace.offset(Math.clamp(x, 0, 83), 0, 41)))) continue;
            for (int y : new int[]{8, 23, 25}) clear(city, palace.offset(x, y, 43), Blocks.SMOOTH_STONE_SLAB);
            if (x >= 2 && x < 83 && (x - 2) % 5 == 0 && Math.abs(x - 42) >= 4)
                clear(city, palace.offset(x, 3, 42), Blocks.POLISHED_ANDESITE_SLAB);
        }
        // Retire the generic pilasters and pendant chains where they cross the new work.
        for (int x = 2; x < 83; x += 6) {
            if (Math.abs(x - 42) < 4) continue;
            var foot = palace.offset(x, 2, 43);
            if (!palaceShell.contains(ChunkPos.containing(foot))) continue;
            clear(city, foot, Blocks.POLISHED_ANDESITE);
            for (int y = 1; y <= 23; y++) clear(city, palace.offset(x, y, 43), Blocks.SMOOTH_QUARTZ);
        }
        for (int x : new int[]{17, 29}) {
            if (!palaceShell.contains(ChunkPos.containing(palace.offset(x, 0, 21)))) continue;
            for (int y = 10; y <= 23; y++) clear(city, palace.offset(x, y, 21), material("iron_chain"));
        }
        NapoliMonumentInteriors.place(city, "capodimonte_reggia", palace,
                pos -> palaceShell.contains(ChunkPos.containing(pos)), (pos, old) -> {
                    int z = pos.getZ() - palace.getZ(), y = pos.getY() - palace.getY();
                    if (z == 41) return PALACE_FACADE.contains(old.getBlock());
                    if (z == 42) return old.is(Blocks.POLISHED_ANDESITE_SLAB);
                    return y == 9 && old.is(Blocks.POLISHED_DIORITE);
                }, List.of(material("red_terracotta"), Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB,
                        material("light_gray_stained_glass"), Blocks.SMOOTH_STONE, Blocks.CHISELED_STONE_BRICKS,
                        Blocks.POLISHED_GRANITE, Blocks.POLISHED_DIORITE, material("white_terracotta"),
                        Blocks.QUARTZ_PILLAR, Blocks.DARK_OAK_PLANKS, Blocks.FLOWER_POT,
                        Blocks.CHISELED_QUARTZ_BLOCK, Blocks.DECORATED_POT, Blocks.SMOOTH_QUARTZ,
                        material("ochre_froglight")));

        var church = sanita();
        var churchShell = shell(city, church, 31, 34, 23, Set.of(Blocks.CALCITE, OLD_DOME));
        NapoliMonumentInteriors.place(city, "sanita_basilica", church,
                pos -> churchShell.contains(ChunkPos.containing(pos)), (pos, old) -> {
                    int y = pos.getY() - church.getY();
                    if (y > 23) return old.is(OLD_DOME);
                    return y == 0 && (old.is(Blocks.POLISHED_ANDESITE) || old.is(Blocks.POLISHED_DIORITE));
                }, List.of(material("yellow_terracotta"), material("green_terracotta"),
                        Blocks.POLISHED_DIORITE, Blocks.QUARTZ_PILLAR, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ,
                        Blocks.CALCITE, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.CUT_SANDSTONE, Blocks.POLISHED_GRANITE,
                        Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.GOLD_BLOCK, Blocks.DARK_OAK_PLANKS, Blocks.LANTERN));
    }

    private static void clear(ServerLevel city, BlockPos pos, Block original) {
        if (!CityProperty.playerPlaced(city, pos) && city.getBlockState(pos).is(original))
            city.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static Set<ChunkPos> shell(ServerLevel city, BlockPos origin, int width, int depth, int roof, Set<Block> previous) {
        var chunks = new HashSet<ChunkPos>();
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + width) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + depth) >> 4; cz++) {
                var chunk = city.getChunk(cx, cz);
                NapoliExpansion.generate(city, chunk);
                var sample = new BlockPos(Math.clamp(cx * 16 + 8, origin.getX(), origin.getX() + width),
                        origin.getY() + roof, Math.clamp(cz * 16 + 8, origin.getZ(), origin.getZ() + depth));
                if (previous.contains(city.getBlockState(sample).getBlock())) chunks.add(chunk.getPos());
            }
        }
        return chunks;
    }
}
