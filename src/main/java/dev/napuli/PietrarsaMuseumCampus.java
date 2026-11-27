package dev.napuli;

import static dev.napuli.NapoliMonumentInteriors.material;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class PietrarsaMuseumCampus {
    record Wing(String letter, String title, int x, int z, int width, int depth) {
        BlockPos origin() { return NapoliAlbergoPietrarsa.pietrarsa().offset(x, 0, z); }
        BlockPos entrance() { return origin().offset(width / 2, 1, depth - 1); }
    }

    static final List<Wing> WINGS = List.of(
            new Wing("b", "Locomotive elettriche", 2, -23, 20, 17),
            new Wing("c", "Carrozze e littorine", 22, -23, 19, 17),
            new Wing("d", "Diesel da manovra", 6, 44, 23, 14),
            new Wing("e", "Cinema", 52, 47, 17, 14),
            new Wing("f", "Macchinari d’officina", 69, 47, 17, 14),
            new Wing("g", "Modellismo", 50, -23, 27, 17));
    private static final Set<Block> PLAZA = Set.of(Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE,
            Blocks.SANDSTONE, Blocks.GRASS_BLOCK, Blocks.DIRT);
    private static final List<Block> BUILDING = List.of(Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE,
            Blocks.BRICKS, Blocks.BRICK_SLAB, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
            Blocks.SANDSTONE_WALL, material("light_gray_stained_glass"), Blocks.DARK_OAK_PLANKS,
            Blocks.POLISHED_BLACKSTONE, material("ochre_froglight"), Blocks.RAIL, Blocks.CHISELED_POLISHED_BLACKSTONE,
            Blocks.POLISHED_DEEPSLATE, material("green_terracotta"), material("brown_terracotta"),
            material("blue_terracotta"), material("orange_terracotta"), material("black_concrete"),
            Blocks.CRIMSON_STAIRS, material("red_wool"), Blocks.ANVIL, material("yellow_terracotta"),
            material("iron_chain"), Blocks.BLAST_FURNACE, Blocks.GRASS_BLOCK, material("red_terracotta"),
            Blocks.DARK_OAK_STAIRS, Blocks.LECTERN);

    private PietrarsaMuseumCampus() {}

    static void upgrade(ServerLevel city) {
        var start = NapoliAlbergoPietrarsa.pietrarsa();
        NapoliAtlasLandmarks.generate(city, start.offset(-3, 0, -23), 88, 85);
        for (var wing : WINGS) {
            var origin = wing.origin();
            if (!PLAZA.contains(city.getBlockState(origin.offset(wing.width / 2, 0, wing.depth / 2)).getBlock())) continue;
            NapoliMonumentInteriors.place(city, "pietrarsa_padiglione_" + wing.letter, origin,
                    (pos, old) -> pos.getY() == origin.getY() && PLAZA.contains(old.getBlock()), BUILDING);
            var sign = wing.entrance().south(2).east(2);
            if (city.isEmptyBlock(sign) && !CityProperty.playerPlaced(city, sign))
                NapoliEmploymentMap.sign(city, sign, "PIETRARSA · " + wing.letter.toUpperCase(java.util.Locale.ROOT), wing.title, "");
        }
        path(city, start.offset(2, 0, -6), start.offset(80, 0, -4));
        path(city, start.offset(17, 0, 58), start.offset(44, 0, 58));
        path(city, start.offset(42, 0, 61), start.offset(85, 0, 62));
        path(city, start.offset(42, 0, 43), start.offset(44, 0, 62));
        path(city, start.offset(-3, 0, -5), start.offset(-2, 0, 46));
        path(city, start.offset(-2, 0, 43), start.offset(44, 0, 43));
    }

    private static void path(ServerLevel city, BlockPos a, BlockPos b) {
        for (var pos : BlockPos.betweenClosed(a, b))
            if (PLAZA.contains(city.getBlockState(pos).getBlock()) && !CityProperty.playerPlaced(city, pos))
                city.setBlock(pos, Blocks.POLISHED_GRANITE.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
