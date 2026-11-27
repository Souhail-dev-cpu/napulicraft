package dev.napuli;

import static dev.napuli.NapoliMonumentInteriors.material;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Teatro San Carlo and the existing Galleria Umberto I, in the authored city. */
final class NapoliTheatreQuarter {
    static final BlockPos THEATRE = new BlockPos(84, 63, -117);
    static final BlockPos FORECOURT = THEATRE.offset(15, 1, 34);
    private static final Set<Block> EMPTY_PLOT_GROUND = Set.of(Blocks.SAND, Blocks.GRASS_BLOCK, Blocks.DIRT,
            Blocks.STONE, Blocks.SANDSTONE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE);
    private static final Set<Block> GALLERY_FLOOR = Set.of(Blocks.POLISHED_DIORITE, Blocks.SMOOTH_QUARTZ,
            Blocks.CHISELED_QUARTZ_BLOCK, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_GRANITE, Blocks.GOLD_BLOCK,
            Blocks.TERRACOTTA, material("cut_copper"));
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private NapoliTheatreQuarter() {}

    static void upgrade(ServerLevel city) {
        NapoliMonumentInteriors.place(city, "teatro_san_carlo", THEATRE,
                (pos, present) -> pos.getY() == THEATRE.getY() && EMPTY_PLOT_GROUND.contains(present.getBlock()),
                List.of(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE,
                        Blocks.DARK_OAK_PLANKS, Blocks.SMOOTH_SANDSTONE, Blocks.POLISHED_DEEPSLATE,
                        Blocks.DEEPSLATE_TILES, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ,
                        Blocks.QUARTZ_PILLAR, Blocks.DARK_OAK_STAIRS, Blocks.GLASS, Blocks.SEA_LANTERN,
                        Blocks.GOLD_BLOCK, material("red_wool"), material("crimson_stairs"), material("crimson_fence"),
                        material("ochre_froglight"), material("red_carpet"), material("light_blue_terracotta")));
        pave(city, 94, -86, 104, -82);
        pave(city, 88, -85, 93, -82);
        pave(city, 88, -81, 91, -69);
        refitGallery(city);
    }

    private static void refitGallery(ServerLevel city) {
        var centre = NapoliDistrict.GALLERIA;
        NapoliMonumentInteriors.place(city, "mosaico_umberto", centre.offset(-10, -1, -10),
                (pos, present) -> GALLERY_FLOOR.contains(present.getBlock()),
                List.of(Blocks.POLISHED_DIORITE, Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_GRANITE,
                        Blocks.CHISELED_QUARTZ_BLOCK, Blocks.POLISHED_BLACKSTONE, Blocks.GOLD_BLOCK,
                        material("oxidized_cut_copper")));
        for (int x = -NapoliDistrict.ARM; x <= NapoliDistrict.ARM; x++) {
            for (int z = -NapoliDistrict.ARM; z <= NapoliDistrict.ARM; z++) {
                boolean arm = Math.min(Math.abs(x), Math.abs(z)) <= 5;
                if (!arm && x * x + z * z > 156) continue;
                for (int y = 79; y <= 94; y++) {
                    var pos = centre.offset(x, y - centre.getY(), z);
                    if (city.getBlockState(pos).is(Blocks.IRON_BLOCK)) place(city, pos, Blocks.DEEPSLATE_TILES);
                }
            }
        }
        for (Direction arm : Direction.Plane.HORIZONTAL) {
            for (int offset : new int[]{12, 20})
                suspendLantern(city, centre.relative(arm, offset).atY(77));
            for (int side : new int[]{-4, 4}) {
                var start = centre.relative(arm).relative(arm.getClockWise(), side);
                for (int offset = 12; offset <= 23; offset++) {
                    for (int height : new int[]{5, 10}) {
                        var pos = start.relative(arm, offset - 1).above(height);
                        if (city.isEmptyBlock(pos) && city.getBlockState(pos.relative(arm.getClockWise(), Integer.signum(side))).isSolidRender())
                            place(city, pos, Blocks.SMOOTH_QUARTZ_SLAB);
                    }
                }
            }
        }
        for (int x : new int[]{-6, 6}) for (int z : new int[]{-6, 6})
            suspendLantern(city, centre.offset(x, 14, z));
    }

    private static void pave(ServerLevel city, int x1, int z1, int x2, int z2) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, 63, z1, x2, 63, z2)) {
            if ((city.isEmptyBlock(pos) || EMPTY_PLOT_GROUND.contains(city.getBlockState(pos).getBlock()))
                    && city.isEmptyBlock(pos.above())) place(city, pos, Blocks.POLISHED_DIORITE);
        }
    }

    private static void suspendLantern(ServerLevel city, BlockPos lamp) {
        var chain = material("iron_chain");
        var current = city.getBlockState(lamp);
        if (!current.isAir() && !current.is(Blocks.LANTERN) || CityProperty.playerPlaced(city, lamp)) return;
        BlockPos anchor = lamp.above();
        while (anchor.getY() <= 94) {
            var state = city.getBlockState(anchor);
            if (!state.isAir() && !state.is(chain)) break;
            if (CityProperty.playerPlaced(city, anchor)) return;
            anchor = anchor.above();
        }
        if (anchor.getY() > 94 || !city.getBlockState(anchor).isFaceSturdy(city, anchor, Direction.DOWN)) return;
        for (BlockPos link = anchor.below(); link.getY() > lamp.getY(); link = link.below())
            place(city, link, chain);
        city.setBlock(lamp, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), FLAGS);
    }

    private static void place(ServerLevel city, BlockPos pos, Block block) {
        if (!CityProperty.playerPlaced(city, pos) && city.getBlockEntity(pos) == null)
            city.setBlock(pos, block.defaultBlockState(), FLAGS);
    }
}
