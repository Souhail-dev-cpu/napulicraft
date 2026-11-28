package dev.napuli;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** The landmark buildings on the Riviera di Chiaia and at the two ends of Via Posillipo. */
final class NapoliPosillipo {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> VEGETATION = Set.of(Blocks.OAK_LOG, Blocks.OAK_LEAVES,
            Blocks.GRASS_BLOCK, Blocks.DIRT);
    private static final Set<Block> OLD_MARECHIARO = Set.of(Blocks.CALCITE, Blocks.TERRACOTTA,
            Blocks.GLASS, Blocks.GLASS_PANE, Blocks.LANTERN, Blocks.QUARTZ_PILLAR,
            Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_ANDESITE, Blocks.SPRUCE_STAIRS, Blocks.OAK_FENCE,
            Blocks.OAK_PRESSURE_PLATE, CityBlocks.vanilla("iron_chain"));
    private final ServerLevel city;

    private NapoliPosillipo(ServerLevel city) {
        this.city = city;
    }

    static BlockPos center(String id) {
        var place = NapoliAtlas.find(id);
        return new BlockPos(place.x(), NapoliExpansion.legacyHeight(place.x(), place.z()) + 1, place.z());
    }

    static void upgrade(ServerLevel city) {
        var coast = new NapoliPosillipo(city);
        coast.villaComunale();
        coast.sannazaro();
        coast.donnanna();
        coast.marechiaro();
    }

    private void villaComunale() {
        var site = center("villa_comunale");
        var aquarium = site.offset(-36, 0, -16);
        if (prepare(aquarium, 26, 8, 24, VEGETATION)) {
            shell(aquarium, 26, 24, 7, Blocks.POLISHED_DIORITE, Blocks.CALCITE, Blocks.POLISHED_ANDESITE);
            sign(aquarium.offset(16, 0, 24), "AQUARIUM", "Anton Dohrn");
            for (int x : new int[] {3, 8, 17, 22}) {
                tank(aquarium.offset(x - 1, 0, 4));
                tank(aquarium.offset(x - 1, 0, 12));
            }
            fill(aquarium.offset(11, 1, 23), aquarium.offset(15, 1, 23), Blocks.AIR);
            for (int x : new int[] {10, 16}) fill(aquarium.offset(x, 0, 24), aquarium.offset(x, 4, 24),
                    Blocks.QUARTZ_PILLAR);
            fill(aquarium.offset(10, 5, 23), aquarium.offset(16, 5, 24), Blocks.SMOOTH_QUARTZ);
            fill(aquarium.offset(3, 0, 20), aquarium.offset(8, 0, 20), Blocks.DARK_OAK_PLANKS);
            put(aquarium.offset(6, 1, 20), Blocks.LANTERN);
            fill(aquarium.offset(5, 0, 2), aquarium.offset(9, 0, 2), Blocks.BOOKSHELF);
            fill(aquarium.offset(19, 0, 2), aquarium.offset(23, 0, 2), Blocks.BOOKSHELF);
            sign(aquarium.offset(13, 0, 18), "SALE DEL GOLFO", "Scogliere e fondali");
        }

        var casina = site.offset(14, 0, -25);
        if (prepare(casina, 18, 9, 15, VEGETATION)) {
            shell(casina, 18, 13, 6, Blocks.SMOOTH_SANDSTONE, Blocks.CALCITE, Blocks.BRICKS);
            for (int dx : new int[] {2, 6, 12, 16}) fill(casina.offset(dx, 0, 15), casina.offset(dx, 4, 15),
                    Blocks.QUARTZ_PILLAR);
            fill(casina.offset(1, -1, 14), casina.offset(17, -1, 15), Blocks.SMOOTH_QUARTZ);
            fill(casina.offset(1, 5, 13), casina.offset(17, 5, 15), Blocks.SMOOTH_QUARTZ);
            fill(casina.offset(1, 0, 2), casina.offset(1, 0, 10), Blocks.SMOOTH_STONE);
            fill(casina.offset(17, 0, 2), casina.offset(17, 0, 10), Blocks.SMOOTH_STONE);
            for (int z : new int[] {3, 6, 9}) {
                put(casina.offset(1, 1, z), Blocks.DECORATED_POT);
                put(casina.offset(17, 1, z), Blocks.DECORATED_POT);
            }
            sign(casina.offset(13, 0, 16), "CASINA", "POMPEIANA");
        }

        var circolo = site.offset(15, 0, 10);
        if (prepare(circolo, 21, 8, 17, VEGETATION)) {
            shell(circolo, 21, 17, 5, Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_DIORITE, Blocks.SMOOTH_STONE);
            for (int x = 2; x <= 19; x++) if (Math.abs(x - 10) >= 3)
                fill(circolo.offset(x, 1, 17), circolo.offset(x, 3, 17), Blocks.GLASS);
            fill(circolo.offset(2, 0, 2), circolo.offset(19, 1, 2), Blocks.BOOKSHELF);
            for (int z : new int[] {6, 10, 14}) for (int dx : new int[] {4, 16}) {
                put(circolo.offset(dx, 0, z), Blocks.DARK_OAK_PLANKS);
                put(circolo.offset(dx, 0, z - 1), Blocks.SPRUCE_STAIRS);
                put(circolo.offset(dx, 0, z + 1), Blocks.SPRUCE_STAIRS.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            }
            sign(circolo.offset(14, 0, 18), "CIRCOLO", "DELLA STAMPA");
        }

        var stage = site.offset(-4, 0, 24);
        if (prepare(stage.offset(-9, 0, -9), 18, 12, 19, VEGETATION)) {
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                int distance = x * x + z * z;
                if (distance > 64) continue;
                put(stage.offset(x, 0, z), Blocks.POLISHED_ANDESITE);
                if (distance <= 52) put(stage.offset(x, 1, z), Blocks.SPRUCE_PLANKS);
                int rise = distance < 16 ? 10 : distance < 45 ? 9 : 8;
                fill(stage.offset(x, 8, z), stage.offset(x, rise, z), CityBlocks.vanilla("waxed_oxidized_copper"));
            }
            for (int[] post : new int[][] {{-7, 0}, {7, 0}, {0, -7}, {0, 7}, {-5, -5},
                    {-5, 5}, {5, -5}, {5, 5}}) {
                fill(stage.offset(post[0], 1, post[1]), stage.offset(post[0], 8, post[1]),
                        Blocks.POLISHED_BLACKSTONE_WALL);
            }
            for (int dx : new int[] {-1, 0, 1}) {
                stair(stage.offset(dx, 0, 9), Direction.NORTH);
                stair(stage.offset(dx, 1, 8), Direction.NORTH);
            }
            for (int z = 0; z <= 6; z++)
                fill(stage.offset(-2, 1, z), stage.offset(2, 1, z), Blocks.SPRUCE_PLANKS);
            put(stage.offset(0, 2, -3), Blocks.NOTE_BLOCK);
            sign(stage.offset(3, 0, 10), "CASSA ARMONICA", "Villa Comunale");
        }
    }

    private void sannazaro() {
        var site = center("sannazaro");
        var church = site.offset(-11, 0, -25);
        if (prepare(church, 20, 17, 16, VEGETATION)) {
            chapel(church, 20, 16, 10, CityBlocks.vanilla("white_terracotta"));
            // Two terraces lead to the small church above the port.
            fill(church.offset(1, -1, 17), church.offset(19, -1, 20), Blocks.POLISHED_ANDESITE);
            for (int x : new int[] {1, 19}) fill(church.offset(x, 0, 17), church.offset(x, 0, 20),
                    Blocks.STONE_BRICK_WALL);
            sign(church.offset(14, 0, 19), "S. MARIA", "DEL PARTO");
            put(church.offset(16, 0, 3), Blocks.CHISELED_QUARTZ_BLOCK);
            put(church.offset(16, 1, 3), Blocks.DECORATED_POT);
        }
        for (int dx : new int[] {-24, 15}) {
            var house = site.offset(dx, 0, -5);
            if (prepare(house, 9, 12, 11, VEGETATION))
                waterfrontHouse(house, dx < 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.TERRACOTTA, false);
        }
    }

    private void donnanna() {
        var base = NapoliAtlasLandmarks.donnanna();
        // Keep the existing entrance ramp and loggia. The side wings use the same tuff and tall voids.
        NapoliAtlasLandmarks.generate(city, base, 51, 43);
        var oldPlaster = CityBlocks.vanilla("yellow_terracotta");
        for (int x : new int[] {0, 51}) {
            for (int z = 1; z <= 40; z++) {
                for (int y : new int[] {1, 2, 8, 15, 22}) replace(base.offset(x, y, z), oldPlaster,
                        y == 2 || y == 15 ? Blocks.POLISHED_TUFF : Blocks.TUFF_BRICKS);
                if (z < 5 || z > 35 || (z % 7 != 0 && z % 7 != 1)) continue;
                for (int floor : new int[] {4, 11, 18}) {
                    var lower = base.offset(x, floor, z);
                    replace(lower, oldPlaster, Blocks.IRON_BARS);
                    replace(lower.above(), oldPlaster, Blocks.IRON_BARS);
                    replace(lower.above(2), oldPlaster, Blocks.TUFF_BRICKS);
                }
            }
        }
        // The hall where the legend places the theatre, alongside a seaward passage.
        for (int x = 6; x <= 17; x++) for (int z = 9; z <= 21; z++)
            replace(base.offset(x, 0, z), Blocks.POLISHED_ANDESITE, Blocks.POLISHED_TUFF);
        if (city.getBlockState(base.offset(11, 0, 10)).is(Blocks.POLISHED_TUFF)) {
            for (int x = 7; x <= 16; x++) for (int z = 10; z <= 12; z++)
                add(base.offset(x, 1, z), Blocks.DARK_OAK_PLANKS);
            for (int z : new int[] {16, 19}) for (int x = 8; x <= 15; x++) if (x != 11 && x != 12)
                add(base.offset(x, 1, z), Blocks.DARK_OAK_STAIRS.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            sign(base.offset(7, 1, 21), "SALA DEL TEATRO", "Palazzo Donn'Anna");
        }
    }

    private void marechiaro() {
        var site = center("marechiaro");
        var genericHall = site.offset(-13, 0, -15);
        if (prepare(genericHall, 25, 20, 23, OLD_MARECHIARO)) {
            var chapel = site.offset(-13, 0, -14);
            chapel(chapel, 11, 18, 8, Blocks.CALCITE);
            sign(site.offset(-9, 0, 6), "S. MARIA", "DEL FARO");
            put(chapel.offset(1, 0, 17), Blocks.CHISELED_QUARTZ_BLOCK);
            put(chapel.offset(10, 0, 17), Blocks.CHISELED_QUARTZ_BLOCK);
            waterfrontHouse(site.offset(3, 0, -14), Blocks.SMOOTH_SANDSTONE, false);
            waterfrontHouse(site.offset(3, 0, -2), Blocks.TERRACOTTA, true);
            fill(site.offset(-1, -1, -14), site.offset(2, -1, 17), Blocks.POLISHED_TUFF);
        }
        for (int z = 11; z <= 17; z++) for (int dx = -6; dx <= 6; dx++) {
            var paved = site.offset(dx, -1, z);
            var old = city.getBlockState(paved);
            if (old.isAir() || old.is(Blocks.SMOOTH_STONE) || old.is(Blocks.POLISHED_ANDESITE)
                    || old.is(Blocks.GRASS_BLOCK) || old.is(Blocks.STONE_BRICKS)) {
                put(paved, Blocks.POLISHED_TUFF);
                for (int y = 64; y < paved.getY(); y++)
                    add(paved.atY(y), Blocks.TUFF_BRICKS);
            }
        }
        calata(site);
        villaDegliSpiriti(site);
    }

    private void calata(BlockPos site) {
        // Follow the height of the land to join the authored overlook to the actual generated sea.
        int coastZ = (int) NapoliExpansion.coast(site.getX());
        NapoliAtlasLandmarks.generate(city, site.offset(-8, 0, 18), 16, coastZ - site.getZ() - 11);
        int plateau = site.getY() - 1;
        int gradeStart = coastZ - (plateau - 64) * 2;
        for (int z = site.getZ() + 16; z <= coastZ; z++) {
            int y = plateau - Math.max(0, (z - gradeStart + 1) / 2);
            boolean stairs = z >= gradeStart + 1 && (z - gradeStart) % 2 == 1;
            for (int dx = -2; dx <= 2; dx++) {
                var tread = new BlockPos(site.getX() + dx, y, z);
                int surface = Math.max(y, NapoliExpansion.height(tread.getX(), z));
                if (!editable(tread)) continue;
                boolean clear = true;
                for (int top = y + 1; top <= surface + 2; top++) {
                    var p = tread.atY(top);
                    var state = city.getBlockState(p);
                    boolean natural = state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.TUFF)
                            || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_STONE)
                            || state.is(Blocks.POLISHED_ANDESITE) || state.is(Blocks.SAND) || state.is(Blocks.POLISHED_DEEPSLATE)
                            || state.is(Blocks.STONE) || state.is(Blocks.STONE_BRICK_STAIRS)
                            || z == site.getZ() + 30 && state.is(Blocks.STONE_BRICK_WALL);
                    if (!state.isAir() && (!natural || !editable(p))) { clear = false; break; }
                }
                if (!clear) continue;
                for (int top = y + 1; top <= surface + 2; top++)
                    if (!city.isEmptyBlock(tread.atY(top))) put(tread.atY(top), Blocks.AIR);
                for (int support = 64; support < y; support++) {
                    var p = tread.atY(support);
                    if (city.isEmptyBlock(p) || city.getBlockState(p).is(Blocks.WATER)) put(p, Blocks.TUFF_BRICKS);
                }
                if (stairs) stair(tread, Direction.NORTH);
                else put(tread, Blocks.POLISHED_TUFF);
            }
        }
        var quay = new BlockPos(site.getX(), 64, coastZ + 1);
        for (int z = 0; z <= 6; z++) for (int x = -5; x <= 5; x++) {
            var pos = quay.offset(x, 0, z);
            var old = city.getBlockState(pos);
            if (old.isAir() || old.is(Blocks.WATER)) put(pos, Blocks.STONE_BRICKS);
            if (Math.abs(x) <= 2) replace(pos.above(), Blocks.POLISHED_DEEPSLATE, Blocks.AIR);
            if ((z == 6 || Math.abs(x) == 5) && city.getBlockState(pos).is(Blocks.STONE_BRICKS))
                add(pos.above(), Blocks.STONE_BRICK_WALL);
        }
        sign(quay.offset(3, 1, 1), "CALATA", "PONTICELLO");
    }

    private void villaDegliSpiriti(BlockPos site) {
        // The surviving seaward wall and vaulted rooms remain exposed as an archaeological site.
        int shoreline = (int) NapoliExpansion.coast(site.getX());
        var origin = new BlockPos(site.getX() - 22, 65, shoreline + 11);
        NapoliAtlasLandmarks.generate(city, origin, 22, 12);
        for (var p : BlockPos.betweenClosed(origin.below(4), origin.offset(10, 7, 8))) {
            var existing = city.getBlockState(p);
            boolean terrain = existing.is(Blocks.WATER) || existing.is(Blocks.SAND) || existing.is(Blocks.SANDSTONE)
                    || existing.is(Blocks.TUFF) || existing.is(Blocks.GRASS_BLOCK) || existing.is(Blocks.DIRT)
                    || existing.is(Blocks.SMOOTH_STONE) || existing.is(Blocks.STONE_BRICKS) || existing.is(Blocks.TERRACOTTA);
            if (p.getY() < origin.getY() && terrain && editable(p)) continue;
            if (!editable(p) || !existing.isAir() && !existing.is(Blocks.WATER)) {
                Napuli.LOGGER.info("Villa degli Spiriti: conserved {} at {}", existing, p);
                return;
            }
        }
        fill(origin.below(4), origin.offset(10, -1, 8), Blocks.TUFF_BRICKS);
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 8; z++)
            put(origin.offset(x, -1, z), (x + z) % 3 == 0 ? Blocks.POLISHED_TUFF : Blocks.TUFF_BRICKS);
        for (int z : new int[] {0, 8}) {
            for (int x = 0; x <= 10; x++) {
                int bay = x % 4;
                for (int y = 0; y <= 4; y++) {
                    if (bay > 0 && x < 10 && (y < 3 || y == 3 && bay == 2)) continue;
                    put(origin.offset(x, y, z), y == 4 ? Blocks.CHISELED_TUFF_BRICKS : Blocks.TUFF_BRICKS);
                }
            }
        }
        fill(origin.offset(0, 0, 1), origin.offset(0, 4, 7), Blocks.TUFF_BRICKS);
        for (int z = 2; z <= 6; z++)
            fill(origin.offset(1, 4 + Math.min(z - 2, 6 - z), z),
                    origin.offset(5, 4 + Math.min(z - 2, 6 - z), z), Blocks.TUFF_BRICKS);
        for (int x = 11; x <= 20; x++)
            for (int z = 0; z <= 1; z++) bridge(origin.offset(x, -1, z));
        for (int z = -5; z <= 0; z++)
            for (int x = 19; x <= 20; x++) bridge(origin.offset(x, -1, z));
        sign(origin.offset(9, 0, 2), "VILLA", "DEGLI SPIRITI");
    }

    private void bridge(BlockPos p) {
        if (city.isEmptyBlock(p) || city.getBlockState(p).is(Blocks.WATER)) put(p, Blocks.STONE_BRICKS);
        replace(p.above(), Blocks.STONE_BRICK_WALL, Blocks.AIR);
        replace(p.above(), Blocks.POLISHED_DEEPSLATE, Blocks.AIR);
    }

    private void chapel(BlockPos origin, int w, int d, int h, Block wall) {
        shell(origin, w, d, h, wall, Blocks.SMOOTH_QUARTZ, Blocks.BRICKS);
        int center = w / 2;
        for (int x = 0; x <= w; x++) {
            int rise = Math.min(x, w - x) / 2;
            for (int y = h; y <= h + rise; y++)
                fill(origin.offset(x, y, 0), origin.offset(x, y, d), y == h + rise ? Blocks.BRICKS : wall);
        }
        for (int x : new int[] {center - 3, center + 3})
            fill(origin.offset(x, 0, d), origin.offset(x, 6, d), Blocks.QUARTZ_PILLAR);
        put(origin.offset(center, 6, d), Blocks.GLASS);
        int side = w - 2;
        fill(origin.offset(side, h, 3), origin.offset(side + 2, h + 3, 5), wall);
        put(origin.offset(side + 1, h + 2, 5), Blocks.AIR);
        put(origin.offset(side + 1, h + 2, 4), Blocks.BELL);
        fill(origin.offset(side, h + 4, 3), origin.offset(side + 2, h + 4, 5), Blocks.BRICKS);
        fill(origin.offset(center - 2, 0, 2), origin.offset(center + 2, 0, 4), Blocks.SMOOTH_QUARTZ);
        put(origin.offset(center, 1, 3), Blocks.CHISELED_QUARTZ_BLOCK);
        for (int z = 6; z <= d - 3; z += 3) for (int x : new int[] {center - 3, center - 2, center + 2, center + 3})
            put(origin.offset(x, 0, z), Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private void waterfrontHouse(BlockPos origin, Block wall, boolean finestrella) {
        shell(origin, 9, 10, 7, wall, Blocks.SMOOTH_SANDSTONE, Blocks.BRICKS);
        fill(origin.offset(1, 3, 1), origin.offset(8, 3, 9), Blocks.SPRUCE_PLANKS);
        for (int i = 0; i < 4; i++) {
            var on = origin.offset(1 + i, i, 2);
            stair(on, Direction.EAST);
            if (i >= 1 && i < 3) put(on.atY(origin.getY() + 3), Blocks.AIR);
        }
        put(origin.offset(2, 0, 7), Blocks.CRAFTING_TABLE);
        put(origin.offset(3, 0, 7), Blocks.BARREL);
        put(origin.offset(7, 0, 7), Blocks.SPRUCE_STAIRS);
        put(origin.offset(6, 4, 7), CityBlocks.vanilla("white_bed").defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        put(origin.offset(7, 4, 7), CityBlocks.vanilla("white_bed").defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        fill(origin.offset(2, 3, 11), origin.offset(7, 3, 11), Blocks.SMOOTH_SANDSTONE);
        for (int x = 2; x <= 7; x++) put(origin.offset(x, 4, 11), Blocks.STONE_BRICK_WALL);
        if (finestrella) {
            put(origin.offset(6, 4, 10), Blocks.AIR);
            put(origin.offset(6, 5, 10), Blocks.GLASS);
            put(origin.offset(6, 4, 11), Blocks.POTTED_RED_TULIP);
            sign(origin.offset(8, 0, 11), "FINESTRELLA", "di Marechiaro");
        }
    }

    private void tank(BlockPos origin) {
        fill(origin, origin.offset(3, 4, 4), Blocks.GLASS);
        fill(origin, origin.offset(3, 0, 4), Blocks.PRISMARINE_BRICKS);
        fill(origin.offset(1, 1, 1), origin.offset(2, 3, 3), Blocks.WATER);
        put(origin.offset(1, 0, 1), Blocks.SEA_LANTERN);
        put(origin.offset(2, 1, 2), Blocks.SEAGRASS);
    }

    private void shell(BlockPos origin, int width, int depth, int height, Block wall, Block trim, Block roof) {
        for (var p : BlockPos.betweenClosed(origin.below(), origin.offset(width, height, depth))) {
            int dx = p.getX() - origin.getX(), dy = p.getY() - origin.getY(), dz = p.getZ() - origin.getZ();
            boolean edge = dx == 0 || dx == width || dz == 0 || dz == depth;
            if (dy == -1) put(p, Blocks.POLISHED_ANDESITE);
            else if (dy == height) put(p, roof);
            else if (edge) {
                int offset = dz == 0 || dz == depth ? dx : dz;
                boolean window = offset > 1 && offset % 4 == 2 && (dy == 2 || dy == 3 || dy == 5);
                boolean band = dy == 0 || dy == height - 1 || dx == 0 && (dz == 0 || dz == depth)
                        || dx == width && (dz == 0 || dz == depth);
                put(p, window && !band ? Blocks.GLASS : band ? trim : wall);
            }
        }
        var entrance = origin.offset(width / 2, 0, depth);
        door(entrance);
        put(entrance.north(depth / 2).above(height - 1), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true));
    }

    private boolean prepare(BlockPos start, int width, int height, int depth, Set<Block> replaceable) {
        var end = start.offset(width, height, depth);
        NapoliAtlasLandmarks.generate(city, start, width, depth);
        for (var p : BlockPos.betweenClosed(start.below(), end)) {
            if (!editable(p) || p.getY() >= start.getY() && !city.isEmptyBlock(p)
                    && !replaceable.contains(city.getBlockState(p).getBlock())) {
                Napuli.LOGGER.info("Posillipo: conserved occupied volume at {} ({})", p, city.getBlockState(p));
                return false;
            }
        }
        for (var p : BlockPos.betweenClosed(start, end)) if (!city.isEmptyBlock(p)) put(p, Blocks.AIR);
        return true;
    }

    private void door(BlockPos p) {
        var door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        put(p, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(p.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private void stair(BlockPos p, Direction facing) {
        put(p, Blocks.POLISHED_TUFF_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
    }

    private void fill(BlockPos start, BlockPos end, Block block) {
        for (var p : BlockPos.betweenClosed(start, end)) put(p, block);
    }

    private void sign(BlockPos p, String first, String second) {
        if (editable(p) && city.isEmptyBlock(p) && city.getBlockState(p.below()).isSolidRender())
            NapoliEmploymentMap.sign(city, p, first, second);
    }

    private void replace(BlockPos p, Block previous, Block block) {
        if (city.getBlockState(p).is(previous)) put(p, block);
    }

    private void add(BlockPos p, Block block) {
        add(p, block.defaultBlockState());
    }

    private void add(BlockPos p, BlockState state) {
        if (city.isEmptyBlock(p)) put(p, state);
    }

    private boolean editable(BlockPos p) {
        return !CityProperty.playerPlaced(city, p) && city.getBlockEntity(p) == null;
    }

    private void put(BlockPos p, Block block) {
        put(p, block.defaultBlockState());
    }

    private void put(BlockPos p, BlockState state) {
        if (editable(p)) city.setBlock(p, state, FLAGS);
    }
}
