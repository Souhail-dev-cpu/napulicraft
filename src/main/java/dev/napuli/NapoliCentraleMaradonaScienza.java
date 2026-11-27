package dev.napuli;

import static dev.napuli.NapoliMonumentInteriors.material;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class NapoliCentraleMaradonaScienza {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> PAVING = Set.of(Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE,
            Blocks.POLISHED_DEEPSLATE);
    private static final Set<Block> STADIUM_STRUCTURE = Set.of(Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE,
            material("blue_concrete"));

    private NapoliCentraleMaradonaScienza() {}

    static BlockPos centrale() { return origin("garibaldi", -45, -45); }
    static BlockPos maradona() { return origin("maradona", -76, -76); }
    static BlockPos scienza() { return origin("scienza", -36, -22); }

    static void upgrade(ServerLevel city) {
        station(city);
        stadium(city);
        museum(city);
    }

    private static void station(ServerLevel city) {
        var start = centrale();
        var shell = NapoliCapodimonteSanita.shell(city, start, 89, 24, 14, Set.of(Blocks.SMOOTH_QUARTZ));
        removeFront(city, start, shell, 89, 24, Blocks.SMOOTH_QUARTZ, 8, 13, 15);
        NapoliMonumentInteriors.place(city, "napoli_centrale_atrio", start,
                pos -> inside(shell, start, pos, 24), (pos, old) -> {
                    int y = pos.getY() - start.getY(), z = pos.getZ() - start.getZ();
                    return y == 0 && PAVING.contains(old.getBlock())
                            || z == 24 && (old.is(Blocks.SMOOTH_QUARTZ) || old.is(Blocks.GLASS_PANE));
                }, List.of(Blocks.AIR, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE, Blocks.SMOOTH_QUARTZ,
                        Blocks.QUARTZ_PILLAR, material("light_gray_stained_glass"), material("ochre_froglight"),
                        Blocks.DARK_OAK_PLANKS, Blocks.QUARTZ_STAIRS));
    }

    private static void stadium(ServerLevel city) {
        var start = maradona();
        NapoliAtlasLandmarks.generate(city, start, 152, 152);
        if (!city.getBlockState(start.offset(76, 0, 76)).is(Blocks.CALCITE)) return;
        for (int x : new int[]{12, 140}) for (int z : new int[]{46, 106}) {
            for (int y = 18; y <= 25; y++) clear(city, start.offset(x, y, z), Blocks.IRON_BARS);
            for (int dx = -2; dx <= 2; dx++) {
                clear(city, start.offset(x + dx, 26, z), Blocks.SEA_LANTERN);
                clear(city, start.offset(x + dx, 25, z), Blocks.POLISHED_ANDESITE);
            }
        }
        NapoliMonumentInteriors.place(city, "maradona_copertura_pista", start,
                (pos, old) -> pos.getY() == start.getY() ? PAVING.contains(old.getBlock()) : STADIUM_STRUCTURE.contains(old.getBlock()),
                List.of(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_STONE,
                        material("light_gray_stained_glass"), material("light_gray_concrete"), Blocks.SEA_LANTERN,
                        material("light_blue_concrete"), material("white_concrete")));
    }

    private static void museum(ServerLevel city) {
        var start = scienza();
        var shell = NapoliCapodimonteSanita.shell(city, start, 71, 39, 15,
                Set.of(Blocks.BRICKS, material("light_gray_concrete")));
        removeFront(city, start, shell, 71, 39, Blocks.CUT_SANDSTONE, 8, 14, 16);
        sealDome(city, start.offset(54, 15, 22), shell);
        if (shell.contains(ChunkPos.containing(start.offset(53, 7, 20)))) {
            clear(city, start.offset(53, 7, 20), Blocks.LANTERN);
            for (int y = 8; y <= 14; y++) clear(city, start.offset(53, y, 20), material("iron_chain"));
        }
        NapoliMonumentInteriors.place(city, "corporea_planetario", start,
                pos -> inside(shell, start, pos, 39), (pos, old) -> {
                    int y = pos.getY() - start.getY(), z = pos.getZ() - start.getZ();
                    return y == 0 && PAVING.contains(old.getBlock())
                            || z == 39 && (old.is(Blocks.BRICKS) || old.is(Blocks.GLASS_PANE));
                }, List.of(Blocks.AIR, Blocks.BRICKS, material("light_gray_stained_glass"), Blocks.POLISHED_ANDESITE,
                        Blocks.SMOOTH_STONE, Blocks.POLISHED_DEEPSLATE, material("black_concrete"), Blocks.END_ROD,
                        Blocks.DARK_OAK_STAIRS, Blocks.SEA_LANTERN, material("light_blue_terracotta"),
                        Blocks.POLISHED_DIORITE, Blocks.SMOOTH_QUARTZ, material("red_terracotta"),
                        material("purple_terracotta"), material("ochre_froglight")));
    }

    private static void sealDome(ServerLevel city, BlockPos center, Set<ChunkPos> shell) {
        var finish = material("light_gray_concrete").defaultBlockState();
        for (int x = -17; x <= 17; x++) for (int z = -17; z <= 17; z++) {
            int crown = domeHeight(x, z);
            if (crown <= 1 || !shell.contains(ChunkPos.containing(center.offset(x, 0, z)))) continue;
            int joint = Math.min(Math.min(domeHeight(x - 1, z), domeHeight(x + 1, z)),
                    Math.min(domeHeight(x, z - 1), domeHeight(x, z + 1)));
            for (int y = Math.max(1, joint); y < crown; y++) {
                var pos = center.offset(x, y, z);
                if (city.isEmptyBlock(pos) && !CityProperty.playerPlaced(city, pos)) city.setBlock(pos, finish, FLAGS);
            }
        }
    }

    private static int domeHeight(int x, int z) {
        return (int) Math.sqrt(Math.max(0, 17 * 17 - x * x - z * z));
    }

    private static void removeFront(ServerLevel city, BlockPos start, Set<ChunkPos> shell, int width, int front,
            Block pilaster, int... bands) {
        for (int x = -1; x <= width + 1; x++) {
            if (!shell.contains(ChunkPos.containing(start.offset(Math.clamp(x, 0, width), 0, front)))) continue;
            for (int y : bands) clear(city, start.offset(x, y, front + 1), Blocks.SMOOTH_STONE_SLAB);
            if (x < 2 || x >= width || (x - 2) % 6 != 0 || Math.abs(x - (width + 1) / 2) < 4) continue;
            clear(city, start.offset(x, 2, front + 1), Blocks.POLISHED_ANDESITE);
            for (int y = 1; y <= bands[bands.length - 1]; y++) clear(city, start.offset(x, y, front + 1), pilaster);
        }
    }

    private static boolean inside(Set<ChunkPos> shell, BlockPos start, BlockPos pos, int front) {
        int z = pos.getZ() - start.getZ();
        return shell.contains(ChunkPos.containing(pos.north(Math.max(0, z - front))));
    }

    private static void clear(ServerLevel city, BlockPos pos, Block previous) {
        if (city.getBlockState(pos).is(previous) && !CityProperty.playerPlaced(city, pos))
            city.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private static BlockPos origin(String id, int dx, int dz) {
        var place = NapoliAtlas.find(id);
        return new BlockPos(place.x() + dx, NapoliExpansion.height(place.x(), place.z()), place.z() + dz);
    }
}
