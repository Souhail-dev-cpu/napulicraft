package dev.napuli;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;

/** Street-facing stonework and room finishes for the city's residential plans. */
final class NapoliResidentialFinish {
    private static final Set<Block> FLOORS = Set.of(Blocks.SPRUCE_PLANKS, Blocks.OAK_PLANKS,
            Blocks.STRIPPED_OAK_WOOD, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS);
    private static final Set<Block> PLASTER = Set.of(Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE,
            Blocks.CUT_SANDSTONE, Blocks.CALCITE, Blocks.BRICKS, NapoliMonumentInteriors.material("yellow_terracotta"),
            NapoliMonumentInteriors.material("pink_terracotta"), NapoliMonumentInteriors.material("white_terracotta"));
    private static final List<Block> TILES = List.of(NapoliMonumentInteriors.material("blue_glazed_terracotta"),
            NapoliMonumentInteriors.material("light_blue_glazed_terracotta"),
            NapoliMonumentInteriors.material("yellow_glazed_terracotta"));
    private static final Set<Block> GLAZING = Set.of(Blocks.GLASS, Blocks.GLASS_PANE);

    private NapoliResidentialFinish() {}

    static void historic(ServerLevel city) {
        var work = new Finish(city, pos -> true);
        for (var home : NapoliMergellina.allHouses()) registered(work, home);
        for (var home : CityLivingHomes.PUBLIC_HOMES) registered(work, home);
        for (int[] lot : NapoliInteriors.homes()) {
            int x = lot[0], z = lot[1], variant = lot[2];
            if (x == -70 && z == -26) continue; // Carmela's pizzeria has its own interior.
            work.elevation(x, 64, z + 11, 11, 3 + Math.floorMod(variant, 4) / 2, 4, false);
            work.floor(x + 3, 63, z + 3, 5, 6, variant);
        }
    }

    private static void registered(Finish work, NapoliMergellina.House home) {
        var door = home.entrance().north();
        if (!(work.city.getBlockState(door).getBlock() instanceof DoorBlock)) return;
        int x = home.x(), z = home.z(), y = home.ground();
        work.elevation(x, y, door.getZ(), home.width(), home.floors(), home.style() < 0 ? 4 : 5, true);
        work.floor(x + 2, y - 1, z + 2, 4, 4, x + z);
        work.floor(x + home.width() / 2 - 1, y - 1, door.getZ() - 2, 3, 2, x + z);
    }

    static void atlas(ServerLevel city, Predicate<BlockPos> local, int x, int y, int z, int floors) {
        var work = new Finish(city, local);
        work.elevation(x, y, z + 23, 22, floors, 5, true);
        work.elevation(x, y, z, 22, floors, 5, false);
        work.floor(x + 2, y - 1, z + 2, 7, 5, x + z);
        work.floor(x + 9, y - 1, z + 15, 5, 8, x + z);
    }

    private record Finish(ServerLevel city, Predicate<BlockPos> includes) {
        void elevation(int x, int y, int z, int width, int floors, int stride, boolean portal) {
            for (int offset = 0; offset < width; offset++) {
                var base = new BlockPos(x + offset, y, z);
                if (!includes.test(base)) continue;
                int centre = width / 2;
                boolean jamb = portal && Math.abs(offset - centre) == 1;
                replace(base, PLASTER, jamb ? Blocks.POLISHED_DEEPSLATE : Blocks.POLISHED_ANDESITE);
                if (jamb) replace(base, Set.of(Blocks.POLISHED_ANDESITE), Blocks.POLISHED_DEEPSLATE);
                if (jamb) replace(base.above(), PLASTER, Blocks.POLISHED_DEEPSLATE);
                if (portal && Math.abs(offset - centre) <= 1) replace(base.above(2), PLASTER, Blocks.POLISHED_DEEPSLATE);
                boolean corner = offset == 0 || offset == width - 1;
                for (int storey = 0; storey < floors; storey++) {
                    var lower = base.above(storey * stride + 1);
                    if (corner) {
                        // The legacy facade sometimes places a window on the shared corner.
                        for (int row = 0; row < 2; row++) {
                            replace(lower.above(row), GLAZING, Blocks.CUT_SANDSTONE);
                            replace(lower.above(row), PLASTER, Blocks.CUT_SANDSTONE);
                        }
                    } else if (GLAZING.contains(city.getBlockState(lower).getBlock())
                            && GLAZING.contains(city.getBlockState(lower.above()).getBlock())) {
                        replace(lower.below(), PLASTER, Blocks.CUT_SANDSTONE);
                        replace(lower.above(2), PLASTER, Blocks.SMOOTH_SANDSTONE);
                    }
                }
            }
        }

        void floor(int x, int y, int z, int width, int depth, int variant) {
            var tile = TILES.get(Math.floorMod(variant, TILES.size()));
            for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == width - 1 || dz == depth - 1;
                replace(new BlockPos(x + dx, y, z + dz), FLOORS,
                        edge ? Blocks.TERRACOTTA : (dx + dz) % 2 == 0 ? tile : Blocks.SMOOTH_SANDSTONE);
            }
        }

        private void replace(BlockPos pos, Set<Block> previous, Block finish) {
            if (includes.test(pos) && previous.contains(city.getBlockState(pos).getBlock())
                    && !CityProperty.playerPlaced(city, pos) && city.getBlockEntity(pos) == null)
                city.setBlock(pos, finish.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
}
