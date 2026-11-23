package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Architectural volumes that fit around the original streets and their gameplay entrances. */
final class NapoliHistoricArchitecture {
    private final ServerLevel city;

    private NapoliHistoricArchitecture(ServerLevel city) { this.city = city; }

    static void upgrade(ServerLevel city) {
        var architecture = new NapoliHistoricArchitecture(city);
        for (var home : CityProperty.authoredHomes(city)) {
            BlockPos min = home.min(), max = home.max();
            if (!NapoliAtlas.ORIGINAL.contains(min.getX(), min.getZ())
                    || !NapoliAtlas.ORIGINAL.contains(max.getX(), max.getZ())) continue;
            int roof = max.getY();
            if (city.getBlockState(new BlockPos(min.getX(), roof + 1, min.getZ())).is(Blocks.SMOOTH_STONE_SLAB))
                roof++;
            architecture.homeRoof(min.getX(), roof, min.getZ(), max.getX() - min.getX() + 1, max.getZ() - min.getZ() + 1);
        }
        for (var home : NapoliMergellina.PROPERTIES) {
            architecture.homeRoof(home.x(), 72, home.z(), home.width(), home.depth());
        }
        architecture.royalPalace();
        architecture.galleryPorticos();
        architecture.castleTerraces();
    }

    private void homeRoof(int x, int roof, int z, int width, int depth) {
        var corner = city.getBlockState(new BlockPos(x, roof, z));
        if (width < 7 || depth < 7 || (!corner.is(Blocks.BRICKS) && !corner.is(Blocks.BRICK_SLAB)
                && !corner.is(Blocks.SMOOTH_STONE_SLAB))) return;
        int right = x + width - 1, front = z + depth - 1;
        for (int px = x; px <= right; px++) {
            put(px, roof + 1, front, Blocks.BRICK_WALL);
            put(px, roof + 1, z, Blocks.BRICK_WALL);
        }
        for (int pz = z + 1; pz < front; pz++) {
            put(x, roof + 1, pz, Blocks.BRICK_WALL);
            put(right, roof + 1, pz, Blocks.BRICK_WALL);
        }
        // Recess the service volume behind the parapet so the roof reads as a terrace from the street.
        for (int px = x + 2; px <= x + 4; px++) for (int pz = z + 2; pz <= z + 4; pz++) {
            put(px, roof + 1, pz, Blocks.SMOOTH_SANDSTONE);
            put(px, roof + 2, pz, Blocks.CUT_SANDSTONE);
            put(px, roof + 3, pz, Blocks.BRICK_SLAB);
        }
        put(right - 2, roof + 1, front - 2, Blocks.POTTED_FERN);
    }

    private void royalPalace() {
        // The portal keeps its original passage. The stone band and balcony project over it.
        for (int z = 24; z <= 32; z++) {
            for (int x = 30; x <= 32; x++) put(x, 71, z, Blocks.SMOOTH_STONE_SLAB);
            put(30, 72, z, z % 2 == 0 ? Blocks.STONE_BRICK_WALL : Blocks.CHISELED_STONE_BRICKS);
        }
        for (int z : new int[]{24, 32}) for (int x = 31; x <= 32; x++) put(x, 72, z, Blocks.STONE_BRICK_WALL);
        for (int z = 5; z <= 47; z++) put(31, 83, z, Blocks.SMOOTH_STONE_SLAB);
        for (int pavilion : new int[]{7, 39}) {
            for (int z = pavilion; z <= pavilion + 6; z++) {
                for (int x = 34; x <= 43; x++) {
                    put(x, 84, z, Blocks.BRICKS);
                    int slope = Math.min(z - pavilion, pavilion + 6 - z);
                    put(x, 85 + slope, z, Blocks.BRICK_SLAB);
                }
            }
        }
    }

    private void galleryPorticos() {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos centre = NapoliDistrict.GALLERIA.relative(direction, NapoliDistrict.ARM + 2);
            Direction transverse = direction.getClockWise();
            for (int n : new int[]{-9, -6, 6, 9}) {
                BlockPos post = centre.relative(transverse, n);
                for (int dy = 0; dy <= 15; dy++)
                    put(post.getX(), 64 + dy, post.getZ(), dy == 0 || dy == 15 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.QUARTZ_PILLAR);
            }
            for (int n = -10; n <= 10; n++) {
                BlockPos at = centre.relative(transverse, n);
                put(at.getX(), 80, at.getZ(), Blocks.SMOOTH_QUARTZ);
                put(at.getX(), 85 - Math.abs(n) / 2, at.getZ(), Blocks.QUARTZ_SLAB);
            }
        }
    }

    private void castleTerraces() {
        for (int x = -19; x <= -9; x++) for (int z = 94; z <= 105; z++) {
            if (x != -19 && x != -9 && z != 94 && z != 105) continue;
            put(x, 90, z, Blocks.POLISHED_TUFF_SLAB);
            if (Math.floorMod(x + z, 3) == 0) put(x, 91, z, Blocks.TUFF_BRICK_WALL);
        }
    }

    private void put(int x, int y, int z, Block block) {
        BlockPos pos = new BlockPos(x, y, z);
        if (city.isEmptyBlock(pos) && !CityProperty.playerPlaced(city, pos))
            city.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
