package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Room layouts shared by freshly generated buildings and existing, unmodified blocks. */
final class NapoliInteriorLayout {
    @FunctionalInterface
    interface Fixture {
        void place(int x, int y, int z, BlockState state);
    }

    private final Fixture fixture;

    NapoliInteriorLayout(Fixture fixture) { this.fixture = fixture; }

    static void repairPalaceLights(ServerLevel city) {
        var chain = block("iron_chain").defaultBlockState();
        for (int z = 10; z <= 40; z += 5) for (var pos : java.util.List.of(
                new BlockPos(36, 68, z), new BlockPos(36, 69, z), new BlockPos(41, 82, z))) {
            if (city.isEmptyBlock(pos) && !CityProperty.playerPlaced(city, pos))
                city.setBlock(pos, chain, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    static void upgradeHistoric(ServerLevel city) {
        var repairs = new NapoliInteriorLayout((x, y, z, state) -> {
            BlockPos pos = new BlockPos(x, y, z);
            if (!CityProperty.playerPlaced(city, pos) && city.getBlockEntity(pos) == null)
                city.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        });
        repairs.royalPalace();
        var furnished = new java.util.HashSet<BlockPos>();
        for (var house : CityLivingHomes.all()) {
            if (house.id().startsWith("atlas_")) continue;
            repairs.localHome(house);
            furnished.add(new BlockPos(house.x(), 63, house.z()));
        }
        furnishExistingUpperRooms(city, furnished);
    }

    private static void furnishExistingUpperRooms(ServerLevel city, java.util.Set<BlockPos> furnished) {
        var additions = new NapoliInteriorLayout((x, y, z, state) -> {
            var pos = new BlockPos(x, y, z);
            if (city.isEmptyBlock(pos) && !CityProperty.playerPlaced(city, pos)) city.setBlock(pos, state, Block.UPDATE_CLIENTS);
        });
        for (var home : CityProperty.authoredHomes(city)) {
            var min = home.min();
            var max = home.max();
            if (!NapoliAtlas.ORIGINAL.contains(min.getX(), min.getZ()) || furnished.contains(min)
                    || max.getX() - min.getX() < 6 || max.getZ() - min.getZ() < 7) continue;
            int x = max.getX() - 2, z = max.getZ() - 2;
            for (int y = 64; y <= max.getY() - 3; y++) {
                var floor = city.getBlockState(new BlockPos(x, y - 1, z));
                if (!floor.is(Blocks.SPRUCE_PLANKS) && !floor.is(Blocks.STRIPPED_OAK_WOOD) && !floor.is(Blocks.OAK_PLANKS)) continue;
                var foot = new BlockPos(x, y, z - 2);
                var head = foot.north();
                if (!city.isEmptyBlock(foot) || !city.isEmptyBlock(head)
                        || !city.getBlockState(foot.below()).isSolidRender() || !city.getBlockState(head.below()).isSolidRender()) continue;
                if (y > 64) additions.bed(x, y, z - 2);
                additions.put(x, y, z, Blocks.BOOKSHELF);
                additions.put(x - 1, y + 2, z, Blocks.SEA_LANTERN);
            }
        }
    }

    void apartmentBlock(int x, int y, int z, int floors) {
        for (int f = 0; f < floors; f++) {
            int floor = y + f * 5;
            // Hall along the frontage; an open doorway for each room off the hall.
            for (int xx = 2; xx <= 14; xx++) {
                if (xx == 4 || xx == 5 || xx == 11 || xx == 12) continue;
                column(x + xx, floor, z + 14, 3, Blocks.CALCITE);
            }
            for (int zz = 2; zz <= 13; zz++) {
                if (zz == 8 || zz == 9) continue;
                column(x + 10, floor, z + zz, 3, Blocks.SMOOTH_SANDSTONE);
            }
            living(x + 4, floor, z + 10);
            put(x + 8, floor, z + 11, Blocks.BOOKSHELF);
            put(x + 7, floor, z + 12, Blocks.CRAFTING_TABLE);
            put(x + 2, floor, z + 4, Blocks.CAULDRON);
            put(x + 3, floor, z + 4, Blocks.SMOOTH_QUARTZ_STAIRS);
            put(x + 5, floor, z + 2, Blocks.CRAFTING_TABLE);
            if (f > 0) {
                for (int zz = 2; zz <= 12; zz++) {
                    if (zz != 9 && zz != 10) column(x + 17, floor, z + zz, 3, Blocks.SMOOTH_SANDSTONE);
                }
                bed(x + 13, floor, z + 5);
                bed(x + 15, floor, z + 5);
                bed(x + 18, floor, z + 8);
                put(x + 13, floor, z + 2, Blocks.BARREL);
                put(x + 18, floor, z + 2, Blocks.BOOKSHELF);
                put(x + 19, floor, z + 4, Blocks.CRAFTING_TABLE);
                put(x + 19, floor, z + 5, Blocks.OAK_STAIRS);
                put(x + 14, floor, z + 4, Blocks.BOOKSHELF);
                put(x + 14, floor + 1, z + 4, Blocks.FLOWER_POT);
                for (int dx = 12; dx <= 16; dx++) {
                    for (int dz = 6; dz <= 8; dz++) put(x + dx, floor, z + dz, block("blue_carpet"));
                }
            }
            for (int xx : new int[]{5, 12, 18}) {
                put(x + xx, floor + 3, z + 16, Blocks.SEA_LANTERN);
                put(x + xx, floor + 3, z + 7, Blocks.SEA_LANTERN);
            }
            put(x + 13, floor, z + 17, Blocks.OAK_STAIRS);
            put(x + 14, floor, z + 17, Blocks.POTTED_FERN);
            if (f + 1 < floors) {
                boolean north = f % 2 != 0;
                staircase(x + (north ? 19 : 16), floor, z + (north ? 20 : 16), 5,
                        north ? Direction.NORTH : Direction.SOUTH, Blocks.STONE_BRICK_STAIRS);
            }
        }
    }

    void atlasLandmark(NapoliAtlas.Place place, int floor) {
        int x = place.x(), z = place.z(), radius = place.radius();
        switch (place.kind()) {
            case MUSEUM -> galleries(x, floor, z, radius - 6, 19, place.id().equals("pietrarsa") ? 0 : 8);
            case PALACE -> {
                int half = place.id().equals("albergo") ? 48 : place.id().equals("capodimonte") ? 42 : 26;
                galleries(x, floor, z, half, 19, 9);
            }
            case CHURCH -> {
                for (int zz = z - 10; zz <= z + 10; zz++) put(x, floor - 1, zz, Blocks.POLISHED_DIORITE);
                for (int dx : new int[]{-7, 7}) for (int zz = z - 8; zz <= z + 8; zz += 8)
                    column(x + dx, floor, zz, 8, Blocks.QUARTZ_PILLAR);
                for (int dx = -3; dx <= 3; dx++) put(x + dx, floor, z - 13, Blocks.SMOOTH_QUARTZ);
                put(x, floor + 1, z - 13, Blocks.LANTERN);
            }
            case CLOISTER -> {
                for (int dx : new int[]{-20, 20}) for (int zz : new int[]{-9, 0, 9}) {
                    put(x + dx, floor, z + zz, Blocks.OAK_STAIRS);
                    put(x + dx, floor + 4, z + zz, Blocks.SEA_LANTERN);
                }
            }
            case STATION -> {
                for (int side : new int[]{-1, 1}) {
                    int bx = x + side * Math.min(radius - 10, 13);
                    for (int zz = z - radius + 10; zz < z - radius + 21; zz += 4) {
                        put(bx, floor, zz, Blocks.POLISHED_ANDESITE_STAIRS);
                        put(bx + side * 2, floor, zz, Blocks.OAK_STAIRS);
                        put(bx, floor + 5, zz, Blocks.SEA_LANTERN);
                    }
                }
            }
            case TOWERS -> {
                for (int dx : new int[]{-39, 0, 36}) for (int dz : new int[]{-30, 22}) {
                    int height = 28 + Math.floorMod(dx * 31 + dz, 24);
                    for (int level = 0; level <= height - 7; level += 6) {
                        int bx = x + dx, bz = z + dz, y = floor + level;
                        for (int xx = bx - 9; xx <= bx + 8; xx++) for (int zz = bz - 9; zz <= bz + 8; zz++)
                            put(xx, y - 1, zz, Blocks.SMOOTH_STONE);
                        for (int zz : new int[]{-5, 2, 5}) {
                            put(bx - 5, y, bz + zz, Blocks.BOOKSHELF);
                            put(bx + 4, y, bz + zz, Blocks.CRAFTING_TABLE);
                            put(bx + 3, y, bz + zz, Blocks.OAK_STAIRS);
                        }
                        put(bx, y + 4, bz, Blocks.SEA_LANTERN);
                        if (level + 12 <= height - 1) {
                            boolean north = level % 12 != 0;
                            staircase(bx + (north ? 6 : 4), y, bz + (north ? -2 : -7), 6,
                                    north ? Direction.NORTH : Direction.SOUTH, Blocks.POLISHED_ANDESITE_STAIRS);
                        }
                    }
                }
            }
            case EXHIBITION -> galleries(x, floor, z - 12, 38, 4, 0);
            case SCIENCE -> {
                for (int zz = -14; zz <= 10; zz += 6)
                    for (int dx : new int[]{-28, -8, 12, 27}) {
                        put(x + dx, floor, z + zz, Blocks.POLISHED_ANDESITE);
                        put(x + dx, floor + 1, z + zz, Blocks.GLASS);
                    }
            }
            case SEASIDE -> {
                if (!place.id().equals("nisida")) living(x - 4, floor, z - 5);
            }
            default -> {}
        }
    }

    private void galleries(int x, int floor, int z, int width, int front, int upper) {
        int back = front - (upper == 0 && front == 4 ? 20 : 36);
        for (int side : new int[]{-1, 1}) {
            for (int zz = z + back + 6; zz <= z + front - 4; zz += 5) {
                int bx = x + side * (width / 2);
                put(bx, floor, zz, Blocks.CHISELED_QUARTZ_BLOCK);
                put(bx, floor + 1, zz, Blocks.DECORATED_POT);
                put(bx, floor + 5, zz, Blocks.SEA_LANTERN);
            }
        }
        for (int zz = z + back; zz < z + front; zz++) put(x, floor - 1, zz, Blocks.CHISELED_STONE_BRICKS);
        if (upper <= 0) return;
        for (int px = x - width + 2; px < x + width - 2; px++) {
            if (Math.abs(px - x) <= 4) continue;
            for (int zz = z + back; zz < z + front; zz++) put(px, floor + upper - 1, zz, Blocks.POLISHED_DIORITE);
        }
        for (int zOffset = back; zOffset < front; zOffset++) {
            put(x - 5, floor + upper, z + zOffset, Blocks.IRON_BARS);
            put(x + 5, floor + upper, z + zOffset, Blocks.IRON_BARS);
        }
        for (int side : new int[]{-1, 1}) {
            staircase(x + side * 8, floor, z + front - 4, upper, Direction.NORTH, Blocks.QUARTZ_STAIRS);
            for (int zz = z + back + 3; zz < z + front - 6; zz += 6) {
                put(x + side * (width - 5), floor + upper, zz, Blocks.SMOOTH_QUARTZ);
                put(x + side * (width - 5), floor + upper + 1, zz, Blocks.FLOWER_POT);
            }
        }
    }

    private void localHome(NapoliMergellina.House house) {
        int x = house.x(), z = house.z();
        var stove = CityLivingHomes.use(house, CityLivingHomes.Furniture.STOVE).block();
        put(x + 4, stove.getY(), z + 10, Blocks.SPRUCE_STAIRS);
        put(x + 3, stove.getY(), z + 10, Blocks.OAK_FENCE);
        put(x + 3, stove.getY() + 1, z + 10, Blocks.OAK_PRESSURE_PLATE);
        put(x + 9, stove.getY() + 2, z + 7, Blocks.SEA_LANTERN);
        int bedFloor = CityLivingHomes.use(house, CityLivingHomes.Furniture.BED).block().getY();
        put(x + 2, bedFloor, z + 8, Blocks.BOOKSHELF);
        put(x + 4, bedFloor, z + 8, Blocks.POTTED_FERN);
        put(x + 8, bedFloor + 2, z + 7, Blocks.SEA_LANTERN);
    }

    private void royalPalace() {
        for (int z = 8; z <= 44; z++) {
            put(38, 63, z, Blocks.POLISHED_DIORITE);
            for (int x = 37; x <= 39; x++) put(x, 71, z, block("red_carpet"));
            put(35, 71, z, Blocks.CHISELED_QUARTZ_BLOCK);
            put(45, 71, z, Blocks.CHISELED_QUARTZ_BLOCK);
            if (z % 6 == 0) {
                column(35, 72, z, 4, Blocks.QUARTZ_PILLAR);
                column(44, 72, z, 4, Blocks.QUARTZ_PILLAR);
            }
            if (z % 5 == 0) {
                put(36, 67, z, Blocks.SEA_LANTERN);
                put(36, 68, z, block("iron_chain"));
                put(36, 69, z, block("iron_chain"));
                put(40, 74, z, Blocks.SEA_LANTERN);
                put(40, 75, z, block("iron_chain"));
                put(41, 81, z, Blocks.SEA_LANTERN);
                put(41, 82, z, block("iron_chain"));
            }
        }
        staircase(42, 64, 28, 7, Direction.SOUTH, Blocks.QUARTZ_STAIRS);
        staircase(44, 71, 38, 6, Direction.NORTH, Blocks.QUARTZ_STAIRS);
        for (int y : new int[]{64, 71, 77}) for (int z : new int[]{12, 18, 41}) {
            put(36, y, z, Blocks.DARK_OAK_STAIRS);
            put(37, y, z, Blocks.POLISHED_DIORITE);
            put(37, y + 1, z, Blocks.FLOWER_POT);
            put(40, y, z, Blocks.BOOKSHELF);
        }
        for (int x = 36; x <= 40; x++) {
            put(x, 71, 43, Blocks.CHISELED_QUARTZ_BLOCK);
            put(x, 71, 44, Blocks.CHISELED_QUARTZ_BLOCK);
            put(x, 72, 42, Blocks.SMOOTH_QUARTZ_SLAB);
            for (int y = 72; y <= 75; y++) put(x, y, 44, x == 36 || x == 40 ? Blocks.QUARTZ_PILLAR : block("red_wool"));
        }
        put(37, 72, 43, Blocks.POLISHED_DIORITE);
        put(39, 72, 43, Blocks.POLISHED_DIORITE);
        put(38, 72, 43, Blocks.DARK_OAK_STAIRS);
        put(38, 74, 43, Blocks.GOLD_BLOCK);
    }

    private void staircase(int x, int y, int z, int rise, Direction facing, Block block) {
        var stair = block.defaultBlockState().setValue(StairBlock.FACING, facing);
        for (int step = 0; step < rise; step++) {
            int at = z + step * facing.getStepZ();
            for (int side = 0; side < 2; side++) {
                fixture.place(x + side, y + step, at, stair);
                for (int head = 1; head <= 3; head++) put(x + side, y + step + head, at, Blocks.AIR);
            }
        }
    }

    private void living(int x, int y, int z) {
        put(x - 1, y, z + 1, Blocks.SPRUCE_STAIRS);
        put(x, y, z + 1, Blocks.SPRUCE_STAIRS);
        put(x + 1, y, z + 1, Blocks.SPRUCE_STAIRS);
        put(x, y, z - 1, Blocks.OAK_FENCE);
        put(x, y + 1, z - 1, Blocks.OAK_PRESSURE_PLATE);
    }

    private void bed(int x, int y, int z) {
        var bed = block("blue_bed").defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        fixture.place(x, y, z, bed.setValue(BedBlock.PART, BedPart.FOOT));
        fixture.place(x, y, z - 1, bed.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private void column(int x, int y, int z, int height, Block block) {
        for (int dy = 0; dy < height; dy++) put(x, y + dy, z, block);
    }

    private static Block block(String id) {
        return CityBlocks.vanilla(id);
    }

    private void put(int x, int y, int z, Block block) {
        fixture.place(x, y, z, block.defaultBlockState());
    }
}
