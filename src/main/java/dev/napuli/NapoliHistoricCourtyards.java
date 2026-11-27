package dev.napuli;

import static dev.napuli.NapoliMonumentInteriors.material;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Open the palace courts and detail two Atlas landmarks on their existing sites. */
final class NapoliHistoricCourtyards {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Block PALACE_ROOF = material("pink_terracotta");
    private static final Set<Block> OLD_TOWER = Set.of(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_WALL);

    private NapoliHistoricCourtyards() {}

    static BlockPos campanile() { return NapoliCapodimonteSanita.sanita().offset(30, 0, -1); }

    static BlockPos capuana() {
        var site = NapoliAtlas.find("capuana");
        return new BlockPos(site.x() - 8, NapoliExpansion.height(site.x(), site.z()), site.z() - 4);
    }

    static void upgrade(ServerLevel city) {
        var palace = NapoliCapodimonteSanita.capodimonte();
        for (int x : new int[]{7, 34, 61}) openCourt(city, palace.offset(x, 0, 2));
        bellTower(city);
        cityGate(city);
    }

    private static void openCourt(ServerLevel city, BlockPos origin) {
        for (var pos : BlockPos.betweenClosed(origin, origin.offset(17, 24, 9)))
            if (CityProperty.playerPlaced(city, pos)) return;
        for (var pos : BlockPos.betweenClosed(origin.offset(1, 24, 1), origin.offset(16, 24, 8)))
            if (!city.getBlockState(pos).is(PALACE_ROOF)) return;
        for (var pos : BlockPos.betweenClosed(origin.offset(1, 0, 1), origin.offset(16, 0, 8))) {
            clear(city, pos.above(9), Blocks.POLISHED_DIORITE);
            clear(city, pos.above(10), Blocks.IRON_BARS);
            clear(city, pos.above(24), PALACE_ROOF);
        }
        NapoliMonumentInteriors.place(city, "capodimonte_cortile", origin, (pos, old) -> {
            int y = pos.getY() - origin.getY();
            return y == 0 && (old.is(Blocks.POLISHED_ANDESITE) || old.is(Blocks.CHISELED_STONE_BRICKS))
                    || y == 9 && old.is(Blocks.POLISHED_DIORITE)
                    || y == 10 && old.is(Blocks.IRON_BARS);
        }, List.of(Blocks.ANDESITE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB,
                material("red_terracotta"), material("light_gray_stained_glass"), Blocks.SMOOTH_STONE_SLAB));
    }

    private static void bellTower(ServerLevel city) {
        var origin = campanile();
        if (!city.getBlockState(origin.offset(6, 10, 6)).is(Blocks.STONE_BRICKS)) return;
        for (int x = 2; x <= 10; x += 2) for (int y = 31; y <= 34; y++)
            clear(city, origin.offset(x, y, 2), Blocks.STONE_BRICK_WALL);
        NapoliMonumentInteriors.place(city, "sanita_campanile", origin, (pos, old) -> OLD_TOWER.contains(old.getBlock()),
                List.of(Blocks.AIR, Blocks.POLISHED_DIORITE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
                        Blocks.LADDER, Blocks.BELL, material("yellow_terracotta"), material("white_terracotta"),
                        material("blue_terracotta"), material("waxed_weathered_copper"), material("waxed_oxidized_copper"),
                        material("lightning_rod")));
    }

    private static void cityGate(ServerLevel city) {
        var origin = capuana();
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + 16) >> 4; cx++)
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + 8) >> 4; cz++)
                NapoliExpansion.generate(city, city.getChunk(cx, cz));
        if (!city.getBlockState(origin.offset(8, 16, 4)).is(Blocks.SMOOTH_SANDSTONE)) return;
        NapoliMonumentInteriors.place(city, "porta_capuana_arco", origin, (pos, old) ->
                old.is(Blocks.SMOOTH_SANDSTONE) || old.is(Blocks.STONE_BRICKS),
                List.of(Blocks.SMOOTH_QUARTZ, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.POLISHED_TUFF));
    }

    private static void clear(ServerLevel city, BlockPos pos, Block legacy) {
        if (city.getBlockState(pos).is(legacy) && !CityProperty.playerPlaced(city, pos))
            city.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }
}
