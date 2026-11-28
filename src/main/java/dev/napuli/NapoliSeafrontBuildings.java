package dev.napuli;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Architectural finishing for the authored San Ferdinando–Mergellina shoreline. */
final class NapoliSeafrontBuildings {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> APPROACH = Set.of(Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.DIRT,
            Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE, Blocks.CALCITE, Blocks.STONE_BRICKS);
    private final ServerLevel city;

    private NapoliSeafrontBuildings(ServerLevel city) {
        this.city = city;
    }

    static void upgrade(ServerLevel city) {
        var works = new NapoliSeafrontBuildings(city);
        works.palace("PALAZZO DELLA PREFETTURA", 7, 14, Direction.SOUTH, false);
        works.palace("PALAZZO SALERNO", 7, 36, Direction.NORTH, true);
        works.basilica();
        works.royalPalace();
        works.borgo();
        works.fishMarket();
        works.employmentOffice();
        for (var house : NapoliMergellina.allHouses()) {
            if (house.style() == -1) works.residence(house);
        }
    }

    private void palace(String name, int x1, int front, Direction facing, boolean salerno) {
        int x2 = x1 + 17, center = x1 + 8, back = front - facing.getStepZ() * (salerno ? 6 : 7);
        int minZ = Math.min(front, back), maxZ = Math.max(front, back);
        // Only occupy a free plot. Old saves may contain a player's building on the square.
        for (var p : BlockPos.betweenClosed(x1, 64, minZ, x2, 82, maxZ)) {
            if (!city.isEmptyBlock(p) || CityProperty.playerPlaced(city, p)) {
                Napuli.LOGGER.info("{}: occupied plot at {}", name, p);
                return;
            }
        }
        Block plaster = salerno ? Blocks.SMOOTH_SANDSTONE : CityBlocks.vanilla("white_terracotta");
        Block stone = salerno ? Blocks.SMOOTH_QUARTZ : Blocks.CALCITE;
        for (var p : BlockPos.betweenClosed(x1, 63, minZ, x2, 79, maxZ)) {
            int x = p.getX(), y = p.getY(), z = p.getZ();
            if (y == 63 || y == 69 || y == 74 || y == 79) {
                boolean exterior = x == x1 || x == x2 || z == front || z == back;
                put(p, y == 79 ? Blocks.BRICKS : y == 63 ? Blocks.POLISHED_DIORITE
                        : exterior ? stone : Blocks.SPRUCE_PLANKS);
                continue;
            }
            boolean sideWall = x == x1 || x == x2;
            if (!sideWall && z != front && z != back) continue;
            int fromFront = Math.abs(z - front);
            boolean bay = sideWall ? fromFront == 2 || fromFront == 4
                    : Math.floorMod(x - center, 4) == 0 && x > x1 + 1 && x < x2 - 1;
            boolean column = sideWall ? z == front || z == back
                    : Math.floorMod(x - center, 4) == 2;
            boolean opening = z == front && Math.abs(x - center) <= 1 && y <= 66;
            if (opening) continue;
            boolean glass = bay && (y == 66 || y == 67 || y == 71 || y == 72 || y == 76 || y == 77);
            put(p, glass ? Blocks.GLASS : (column || y == 68 || y == 73 || y == 78) ? stone
                    : y <= 68 ? Blocks.POLISHED_ANDESITE : plaster);
        }
        for (int x = x1; x <= x2; x++) {
            var frontPos = new BlockPos(x, 69, front);
            put(frontPos, stone);
            put(frontPos.above(5), stone);
            put(frontPos.above(10), stone);
            if (Math.abs(x - center) <= 5) {
                int height = 3 - Math.abs(x - center) / 2;
                for (int y = 80; y <= 79 + height; y++) put(new BlockPos(x, y, front), stone);
            }
        }
        for (int side : new int[] {-1, 1}) {
            int x = side < 0 ? x1 : x2;
            for (int dist : new int[] {2, 4}) {
                int z = front - facing.getStepZ() * dist;
                for (int y : new int[] {65, 70, 75}) {
                    var sill = new BlockPos(x, y, z);
                    replace(sill, y == 65 ? Blocks.POLISHED_ANDESITE : plaster, stone);
                    add(sill.offset(side, 0, 0), Blocks.SMOOTH_QUARTZ_SLAB);
                }
            }
        }
        var door = new BlockPos(center, 64, front);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dist = 1; dist <= 3; dist++)
                pave(door.offset(dx, -1, 0).relative(facing, dist));
        }
        var sign = door.east(2).relative(facing);
        if (city.isEmptyBlock(sign) && !CityProperty.playerPlaced(city, sign))
            NapoliEmploymentMap.sign(city, sign, "PALAZZO", salerno ? "SALERNO" : "PREFETTURA");
        int interior = front - facing.getStepZ() * 3;
        for (int x : new int[] {x1 + 3, x2 - 3}) {
            for (int y : new int[] {64, 70, 75}) {
                put(new BlockPos(x, y, interior), Blocks.SPRUCE_STAIRS.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
                put(new BlockPos(x, y + 3, interior), Blocks.LANTERN.defaultBlockState()
                        .setValue(BlockStateProperties.HANGING, true));
                put(new BlockPos(x, y, back + facing.getStepZ()), Blocks.BOOKSHELF);
            }
        }
        // A service stair off the foyer leaves the central aisle to the reception clear.
        for (int i = 0; i < 6; i++) {
            var p = new BlockPos(x1 + 2 + i, 64 + i, back + facing.getStepZ());
            put(p, Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            if (i >= 2 && i < 5) put(p.atY(69), Blocks.AIR);
        }
        for (int i = 0; i < 5; i++) {
            var p = new BlockPos(x2 - 2 - i, 70 + i, back + facing.getStepZ());
            put(p, Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
            if (i >= 1 && i < 4) put(p.atY(74), Blocks.AIR);
        }
    }

    private void basilica() {
        // Close the seam underneath the lantern of the central dome.
        for (var p : BlockPos.betweenClosed(-69, 101, 24, -65, 101, 28)) {
            if (Math.abs(p.getX() + 67) == 2 || Math.abs(p.getZ() - 26) == 2) add(p, Blocks.POLISHED_ANDESITE);
        }
        for (int side : new int[] {-1, 1}) {
            for (int degrees = 77; degrees <= 86; degrees++) {
                double angle = Math.toRadians(degrees) * side;
                for (int radius = 14; radius <= 18; radius++) {
                    int x = NapoliPlebiscito.CX - (int) Math.round(radius * Math.cos(angle));
                    int z = NapoliPlebiscito.CZ + (int) Math.round(radius * Math.sin(angle));
                    add(new BlockPos(x, 73, z), Blocks.SMOOTH_QUARTZ);
                    add(new BlockPos(x, 74, z), Blocks.QUARTZ_SLAB);
                }
            }
            for (int radius : new int[] {15, 18}) {
                int z = NapoliPlebiscito.CZ + radius * side;
                for (int y = 64; y <= 72; y++)
                    add(new BlockPos(NapoliPlebiscito.CX - 1, y, z),
                            y == 64 || y == 72 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.QUARTZ_PILLAR);
            }
        }
    }

    private void royalPalace() {
        // Join the existing stepped pavilion roof courses to the roof below them.
        for (int pavilion : new int[] {7, 39}) {
            for (int dz = 0; dz <= 6; dz++) {
                int top = 85 + Math.min(dz, 6 - dz);
                for (int x = 34; x <= 43; x++) {
                    var roofTile = new BlockPos(x, top, pavilion + dz);
                    if (!city.getBlockState(roofTile).is(Blocks.BRICK_SLAB)
                            || !city.getBlockState(roofTile.atY(84)).is(Blocks.BRICKS)) continue;
                    for (int y = 85; y < top; y++) add(roofTile.atY(y), Blocks.BRICKS);
                }
            }
        }
        // The seaward return carries the same piperno bands as the front on the square.
        Block plaster = CityBlocks.vanilla("red_terracotta");
        for (int x = 34; x <= 46; x++) {
            var wall = new BlockPos(x, 70, 46);
            replace(wall, plaster, Blocks.POLISHED_ANDESITE);
            replace(wall.above(12), plaster, Blocks.POLISHED_ANDESITE);
        }
        for (int x = 36; x <= 44; x += 4) {
            for (int y : new int[] {72, 78}) {
                var sill = new BlockPos(x, y - 1, 46);
                replace(sill, plaster, Blocks.POLISHED_ANDESITE);
                replace(sill.above(), plaster, Blocks.GLASS);
                replace(sill.above(2), plaster, Blocks.GLASS);
                replace(sill.above(3), plaster, Blocks.POLISHED_ANDESITE);
            }
        }
    }

    private void borgo() {
        // Finish all four houses while keeping the nautic club and the working kitchen blocks.
        for (int house = 0; house < 4; house++) {
            int x = 19 + house * 7, roof = 63 + (house % 2 == 0 ? 3 : 4) * 4;
            for (int dx = 0; dx <= 6; dx++) {
                var back = new BlockPos(x + dx, 64, 91);
                if (!city.isEmptyBlock(back)) put(back, Blocks.POLISHED_DEEPSLATE);
                if (!city.isEmptyBlock(back.atY(roof))) put(back.atY(roof), Blocks.SMOOTH_STONE);
            }
            for (int y = 69; y < roof; y += 4) {
                for (int dx : new int[] {1, 5}) {
                    var at = new BlockPos(x + dx, y, 91);
                    if (!city.isEmptyBlock(at)) add(at.south(), Blocks.BRICK_SLAB);
                }
                for (int dx : new int[] {2, 4}) {
                    var window = new BlockPos(x + dx, y, 91);
                    if (city.getBlockState(window).is(Blocks.GLASS)) {
                        var sill = window.below();
                        add(sill.south(), Blocks.POLISHED_ANDESITE_SLAB);
                    }
                }
                var central = new BlockPos(x + 3, y - 1, 88);
                if (!city.isEmptyBlock(central.below())) add(central, Blocks.BOOKSHELF);
            }
        }
    }

    private void fishMarket() {
        for (int x : new int[] {-28, -20}) {
            for (int z = 49; z <= 54; z++) {
                var base = new BlockPos(x, 64, z);
                replace(base, Blocks.TUFF, Blocks.TUFF_BRICKS);
                replace(base.above(3), Blocks.SMOOTH_SANDSTONE, Blocks.TUFF_BRICKS);
            }
        }
        for (int x = -26; x <= -22; x++)
            add(new BlockPos(x, 68, 55), Blocks.BRICK_SLAB);
    }

    private void employmentOffice() {
        for (int x = -122; x <= -113; x++) {
            var at = new BlockPos(x, 67, 39);
            if (city.getBlockState(at).is(Blocks.TUFF_BRICKS)) add(at.south(), Blocks.POLISHED_ANDESITE_SLAB);
            pave(at.atY(63).south(2));
        }
    }

    private void residence(NapoliMergellina.House house) {
        var foot = house.entrance().below();
        for (int dist = 0; dist <= 3; dist++) {
            for (int dx = -1; dx <= 1; dx++) pave(foot.south(dist).east(dx));
        }
        var door = house.entrance().north();
        var variant = Math.floorMod(house.x() + house.z(), 3);
        Block trim = switch (variant) {
            case 0 -> Blocks.CUT_SANDSTONE;
            case 1 -> Blocks.POLISHED_TUFF;
            default -> Blocks.SMOOTH_QUARTZ;
        };
        for (int y = 64; y <= 71; y++) {
            for (int x : new int[] {house.x(), house.x() + house.width() - 1}) {
                var corner = new BlockPos(x, y, door.getZ());
                if (y == 64 || y == 67 || y == 71) {
                    replace(corner, house.wall(), trim);
                    if (city.getBlockState(corner).is(Blocks.POLISHED_ANDESITE))
                        replace(corner, Blocks.POLISHED_ANDESITE, trim);
                }
            }
        }
        // Furnish two out-of-the-way spots, each on an existing floor or next to an existing wall.
        var study = new BlockPos(house.x() + house.width() - 3, 68, house.z() + 2);
        if (city.getBlockState(study.below()).isSolidRender() && city.isEmptyBlock(study)) {
            add(study, variant == 1 ? Blocks.BOOKSHELF : Blocks.SPRUCE_STAIRS);
            if (city.getBlockState(study).is(Blocks.BOOKSHELF)) add(study.above(), Blocks.BOOKSHELF);
        }
        var foyer = new BlockPos(house.x() + 2, 64, door.getZ() - 2);
        if (city.getBlockState(foyer.below()).isSolidRender() && city.isEmptyBlock(foyer)) {
            add(foyer, Blocks.SPRUCE_PLANKS);
            if (city.getBlockState(foyer).is(Blocks.SPRUCE_PLANKS)) add(foyer.above(), Blocks.POTTED_FERN);
        }
        for (int dx : new int[] {-1, 1}) {
            var jamb = door.east(dx);
            replace(jamb, house.wall(), Blocks.POLISHED_ANDESITE);
            replace(jamb.above(), house.wall(), Blocks.POLISHED_ANDESITE);
        }
        replace(door.above(2), house.wall(), Blocks.CHISELED_STONE_BRICKS);
        for (int y : new int[] {65, 69}) {
            for (int x : new int[] {house.x() + 2, house.x() + house.width() - 3}) {
                var back = new BlockPos(x, y, house.z());
                if (city.getBlockState(back).is(house.wall()) && city.getBlockState(back.above()).is(house.wall())) {
                    replace(back, house.wall(), Blocks.GLASS);
                    replace(back.above(), house.wall(), Blocks.GLASS);
                    add(back.north().below(), Blocks.POLISHED_ANDESITE_SLAB);
                }
            }
        }
    }

    private void pave(BlockPos pos) {
        if (city.isEmptyBlock(pos.above()) && APPROACH.contains(city.getBlockState(pos).getBlock()))
            put(pos, Blocks.POLISHED_ANDESITE);
    }

    private void add(BlockPos pos, Block block) {
        if (city.isEmptyBlock(pos)) put(pos, block);
    }

    private void replace(BlockPos pos, Block expected, Block block) {
        if (city.getBlockState(pos).is(expected)) put(pos, block);
    }

    private void put(BlockPos pos, Block block) {
        put(pos, block.defaultBlockState());
    }

    private void put(BlockPos pos, BlockState state) {
        if (!CityProperty.playerPlaced(city, pos) && city.getBlockEntity(pos) == null)
            city.setBlock(pos, state, FLAGS);
    }
}
