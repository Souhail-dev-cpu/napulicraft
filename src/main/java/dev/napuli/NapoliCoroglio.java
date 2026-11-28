package dev.napuli;

import java.util.List;
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

/** Public and historic buildings of the Coroglio waterfront on their existing Atlas sites. */
final class NapoliCoroglio {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> GARDEN = Set.of(Blocks.OAK_LOG, Blocks.OAK_LEAVES,
            Blocks.GRASS_BLOCK, Blocks.DIRT);
    private static final Set<Block> RUINS = Set.of(Blocks.SANDSTONE, Blocks.SANDSTONE_WALL,
            Blocks.TUFF, Blocks.OAK_LEAVES, Blocks.GRASS_BLOCK);
    private static final Set<Block> SHORE = Set.of(Blocks.WATER, Blocks.SAND, Blocks.SANDSTONE,
            Blocks.GRASS_BLOCK, Blocks.DIRT);
    private static final Set<Block> PATH = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND,
            Blocks.SANDSTONE, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DEEPSLATE,
            Blocks.STONE_BRICKS, Blocks.TUFF, Blocks.STONE_BRICK_STAIRS);
    private final ServerLevel city;

    private NapoliCoroglio(ServerLevel city) {
        this.city = city;
    }

    static void upgrade(ServerLevel city) {
        var coast = new NapoliCoroglio(city);
        coast.virgiliano();
        coast.pausilypon();
        coast.coroglio();
        coast.pontileNord();
        coast.nisida();
    }

    static BlockPos grotta() {
        return NapoliPosillipo.center("pausilypon").south(37);
    }

    static BlockPos odeion() {
        return NapoliPosillipo.center("pausilypon").offset(14, 0, 1);
    }

    static BlockPos fablab() {
        return NapoliPosillipo.center("scienza").offset(-35, 0, -43);
    }

    static BlockPos fablabDoor() {
        return fablab().offset(11, 0, 16);
    }

    static BlockPos pierHut() {
        var pier = NapoliAtlas.find("pontile");
        return new BlockPos(pier.x() - 17, 66, pier.z() - pier.radius() + 10);
    }

    static BlockPos nisidaPavilion() {
        return NapoliPosillipo.center("nisida").offset(11, 0, -18);
    }

    static BlockPos chiuppino() {
        int z = 292;
        return new BlockPos(bridgeX(NapoliAtlas.find("scienza"), NapoliAtlas.find("nisida"), z) - 22, 66, z);
    }

    private void virgiliano() {
        var center = NapoliPosillipo.center("virgiliano");
        var gate = center.south(49);
        if (prepare(gate.offset(-9, 0, -2), 18, 10, 5, GARDEN)) {
            for (int x : new int[] {-7, -4, 4, 7}) {
                fill(gate.offset(x, 0, -1), gate.offset(x, 5, 1), Blocks.CUT_SANDSTONE);
                for (int z : new int[] {-2, 2}) {
                    put(gate.offset(x, 0, z), Blocks.POLISHED_ANDESITE);
                    fill(gate.offset(x, 1, z), gate.offset(x, 4, z), Blocks.QUARTZ_PILLAR);
                    put(gate.offset(x, 5, z), Blocks.CALCITE);
                }
            }
            for (int x = -8; x <= 8; x++) {
                int rise = Math.abs(x) <= 4 ? 2 - Math.abs(x) / 2 : 0;
                fill(gate.offset(x, 6, -1), gate.offset(x, 7 + rise, 1), Blocks.SMOOTH_SANDSTONE);
                fill(gate.offset(x, 8 + rise, -1), gate.offset(x, 8 + rise, 1), Blocks.SMOOTH_SANDSTONE_SLAB);
            }
            for (int x = -3; x <= 3; x++) for (int z = -7; z <= 5; z++) pave(gate.offset(x, -1, z));
            sign(gate.offset(5, 0, 3), "PARCO", "VIRGILIANO");
        }
        var room = center.offset(-19, 0, 34);
        if (prepare(room, 10, 7, 9, GARDEN)) {
            shell(room, 10, 9, 5, Blocks.SMOOTH_SANDSTONE, Blocks.CALCITE, Blocks.BRICKS);
            put(room.offset(2, 0, 2), Blocks.BOOKSHELF);
            put(room.offset(8, 0, 2), Blocks.LECTERN);
            table(room.offset(5, 0, 4));
            sign(room.offset(7, 0, 10), "PARCO", "Punto visita");
        }
    }

    private void pausilypon() {
        var center = NapoliPosillipo.center("pausilypon");
        var entrance = grotta();
        if (prepare(entrance.offset(-8, 0, -19), 16, 10, 20, RUINS)) {
            for (int z = -19; z <= 0; z++) {
                for (int x = -8; x <= 8; x++) {
                    int dx = Math.abs(x);
                    int roof = 9 - dx / 2;
                    int clearance = dx <= 2 ? 7 : dx == 3 ? 6 : dx == 4 ? 4 : 0;
                    // Stepped cross-sections read as a continuous arch in the excavated tuff.
                    fill(entrance.offset(x, clearance, z), entrance.offset(x, roof, z),
                            z == 0 || z == -19 || z % 4 == 0 ? Blocks.TUFF_BRICKS : Blocks.TUFF);
                    put(entrance.offset(x, roof + 1, z), dx >= 5 ? Blocks.MOSS_BLOCK : Blocks.TUFF);
                    if ((z == 0 || z == -19) && dx <= 5) {
                        int course = dx == 5 ? 0 : clearance;
                        put(entrance.offset(x, course, z), Blocks.CUT_SANDSTONE);
                        if (dx == 5)
                            fill(entrance.offset(x, 1, z), entrance.offset(x, 4, z), Blocks.CUT_SANDSTONE);
                    }
                    if (dx <= 4) pave(entrance.offset(x, -1, z));
                    if (dx == 3 && z % 5 == -2)
                        put(entrance.offset(x, clearance - 1, z), Blocks.LANTERN.defaultBlockState()
                                .setValue(BlockStateProperties.HANGING, true));
                }
            }
            for (int x = -3; x <= 3; x++) for (int z = 1; z <= 8; z++)
                pave(entrance.offset(x, -1, z));
            for (int side : new int[] {-1, 1}) for (int x = 9; x <= 13; x++) {
                for (int z = -18; z <= -2; z++) {
                    var shoulder = entrance.offset(x * side, 0, z);
                    if (!PATH.contains(city.getBlockState(shoulder.below()).getBlock())) continue;
                    int top = Math.max(0, 5 - (x - 9) - Math.abs(z + 10) / 3);
                    for (int y = 0; y < top; y++) add(shoulder.above(y),
                            y % 3 == 0 ? Blocks.TUFF : Blocks.SANDSTONE);
                    if (top > 0 && !city.isEmptyBlock(shoulder.above(top - 1)))
                        add(shoulder.above(top), Blocks.MOSS_BLOCK);
                }
            }
            sign(entrance.offset(5, 0, 2), "GROTTA", "DI SEIANO");
        }

        for (int x = -11; x <= 11; x++) for (int z = -8; z <= 8; z++) {
            var floor = center.offset(x, -1, z);
            if (PATH.contains(city.getBlockState(floor).getBlock()) && city.isEmptyBlock(floor.above()))
                put(floor, Math.abs(x) == 11 || z == -8 || z == 8
                        ? Blocks.POLISHED_TUFF : Blocks.SMOOTH_SANDSTONE);
        }
        for (int x = -9; x <= 9; x++) for (int z = 5; z <= 7; z++)
            add(center.offset(x, 0, z), Blocks.CUT_SANDSTONE_SLAB);

        var hall = odeion();
        if (prepare(hall, 19, 13, 16, RUINS)) {
            shell(hall, 19, 16, 7, Blocks.TUFF_BRICKS, Blocks.CUT_SANDSTONE, Blocks.BRICKS);
            for (int z = 0; z <= 16; z++) {
                int crown = 7 + Math.min(z, 16 - z) / 2;
                for (int x = 0; x <= 19; x++)
                    fill(hall.offset(x, 7, z), hall.offset(x, crown, z), Blocks.BRICKS);
            }
            fill(hall.offset(3, 0, 2), hall.offset(16, 0, 4), Blocks.POLISHED_DIORITE);
            for (int z : new int[] {7, 10, 13}) for (int x = 2; x <= 17; x++)
                if (Math.abs(x - 9) > 1) put(hall.offset(x, 0, z), stair(Blocks.SMOOTH_SANDSTONE_STAIRS, Direction.NORTH));
            for (int x : new int[] {5, 14})
                put(hall.offset(x, 6, 5), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
            sign(hall.offset(12, 0, 17), "ODEION", "Sala delle audizioni");
        }

        var villa = center.offset(-36, 0, 2);
        if (prepare(villa, 22, 9, 16, RUINS)) {
            for (int x = 0; x <= 22; x++) for (int z = 0; z <= 16; z++) {
                int margin = Math.min(Math.min(x, 22 - x), Math.min(z, 16 - z));
                put(villa.offset(x, -1, z), margin % 4 == 0 ? Blocks.POLISHED_TUFF
                        : margin % 2 == 0 ? Blocks.TERRACOTTA : Blocks.SMOOTH_SANDSTONE);
                if (x == 0 || x == 22 || z == 0)
                    fill(villa.offset(x, 0, z), villa.offset(x, x % 5 == 1 ? 2 : 1, z), Blocks.TUFF_BRICKS);
            }
            for (int x : new int[] {3, 7, 15, 19}) {
                fill(villa.offset(x, 0, 12), villa.offset(x, 3, 12), Blocks.QUARTZ_PILLAR);
                put(villa.offset(x, 4, 12), Blocks.CHISELED_QUARTZ_BLOCK);
            }
            fill(villa.offset(3, 5, 12), villa.offset(19, 5, 12), Blocks.CUT_SANDSTONE);
            for (int x : new int[] {4, 18})
                put(villa.offset(x, 0, 4), Blocks.DECORATED_POT);
            sign(villa.offset(12, 0, 16), "VILLA ROMANA", "Sala e portico");
        }
    }

    private void coroglio() {
        var lab = fablab();
        if (prepare(lab, 22, 11, 16, GARDEN)) {
            shell(lab, 22, 16, 6, Blocks.BRICKS, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE);
            for (int bay = 0; bay < 4; bay++) {
                int start = bay * 6;
                for (int x = start; x <= Math.min(start + 5, 22); x++) {
                    int rise = (x - start) / 2;
                    for (int z = 0; z <= 16; z++)
                        fill(lab.offset(x, 6, z), lab.offset(x, 6 + rise, z), Blocks.SMOOTH_STONE);
                    if (rise == 2)
                        fill(lab.offset(x, 7, 2), lab.offset(x, 7, 14), Blocks.GLASS);
                }
            }
            for (int x : new int[] {3, 18}) {
                fill(lab.offset(x, 0, 3), lab.offset(x, 0, 11), Blocks.SPRUCE_PLANKS);
                put(lab.offset(x, 1, 4), Blocks.CRAFTING_TABLE);
                put(lab.offset(x, 1, 7), Blocks.STONECUTTER);
                put(lab.offset(x, 1, 10), Blocks.SMITHING_TABLE);
                put(lab.offset(x, 0, 13), Blocks.BARREL);
            }
            for (int z : new int[] {4, 11})
                put(lab.offset(11, 5, z), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
            sign(lab.offset(14, 0, 17), "FABLAB", "Via Coroglio");
        }
        var auditorium = NapoliPosillipo.center("scienza").offset(12, 0, -42);
        if (prepare(auditorium, 22, 12, 16, GARDEN)) {
            shell(auditorium, 22, 16, 8, Blocks.BRICKS, Blocks.CALCITE, Blocks.POLISHED_ANDESITE);
            fill(auditorium.offset(3, 0, 2), auditorium.offset(19, 0, 4), Blocks.DARK_OAK_PLANKS);
            fill(auditorium.offset(6, 2, 1), auditorium.offset(16, 5, 1), CityBlocks.vanilla("white_concrete"));
            put(auditorium.offset(16, 1, 3), Blocks.LECTERN);
            for (int z : new int[] {7, 10, 13}) for (int x = 3; x <= 19; x++)
                if (Math.abs(x - 11) > 1) put(auditorium.offset(x, 0, z), stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
            sign(auditorium.offset(14, 0, 17), "CENTRO", "CONGRESSI");
        }
        for (int z = 17; z <= 20; z++) for (int x = 0; x <= 68; x++)
            pave(lab.offset(x, -1, z));
    }

    private void pontileNord() {
        var pier = NapoliAtlas.find("pontile");
        var hut = pierHut();
        NapoliAtlasLandmarks.generate(city, new BlockPos(pier.x() - 5, 65, pier.z() - pier.radius()),
                10, pier.radius() * 2);
        if (prepare(hut, 10, 7, 9, SHORE)) {
            foundation(hut, 10, 9, 63);
            shell(hut, 10, 9, 5, Blocks.BRICKS, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE);
            for (int x = 2; x <= 8; x++) put(hut.offset(x, 4, 9), Blocks.GLASS);
            fill(hut.offset(2, 0, 3), hut.offset(4, 0, 3), Blocks.DARK_OAK_PLANKS);
            put(hut.offset(2, 1, 3), Blocks.LECTERN);
            put(hut.offset(8, 0, 2), Blocks.BARREL);
            sign(hut.offset(3, 0, 10), "PONTILE NORD", "Punto visita");
            for (int x = 5; x <= 13; x++) for (int z = 10; z <= 11; z++) {
                var deck = hut.offset(x, -1, z);
                put(deck, Blocks.STONE_BRICKS);
                replace(deck.above(), Blocks.IRON_BARS, Blocks.AIR);
                replace(deck.above(), Blocks.STONE_BRICK_WALL, Blocks.AIR);
            }
        }
        var base = new BlockPos(pier.x(), 65, pier.z());
        for (int z = -pier.radius() + 2; z <= pier.radius() - 2; z += 8) for (int x : new int[] {-5, 5}) {
            var pierDeck = base.offset(x, 0, z);
            if (!city.getBlockState(pierDeck).is(Blocks.STONE_BRICKS)) continue;
            for (int y = 61; y < 65; y++) {
                var pos = pierDeck.atY(y);
                if (city.isEmptyBlock(pos) || city.getBlockState(pos).is(Blocks.WATER))
                    put(pos, Blocks.STONE_BRICKS);
            }
        }
    }

    private void nisida() {
        var island = NapoliPosillipo.center("nisida");
        NapoliAtlasLandmarks.generate(city, island.offset(-36, 0, -27), 72, 59);
        var pavilion = nisidaPavilion();
        if (prepare(pavilion, 14, 8, 12, GARDEN)) {
            NapoliMonumentInteriors.place(city, "nisida_padiglione", pavilion.below(),
                    (pos, present) -> pos.getY() == pavilion.getY() - 1 && PATH.contains(present.getBlock()),
                    List.of(Blocks.AIR, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE, Blocks.SMOOTH_STONE,
                            Blocks.TUFF_BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE,
                            Blocks.GLASS, Blocks.BRICKS, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS,
                            Blocks.BOOKSHELF, Blocks.DARK_OAK_PLANKS, Blocks.FLOWER_POT, Blocks.DECORATED_POT,
                            Blocks.LANTERN, Blocks.LECTERN, Blocks.BARREL));
            sign(pavilion.offset(10, 0, 13), "NISIDA", "Sala del belvedere");
            for (int z : new int[] {-2, -1, 13, 14}) for (int x = 5; x <= 9; x++)
                pave(pavilion.offset(x, -1, z));
        }
        var tower = island.offset(-25, 0, 8);
        if (prepare(tower, 9, 13, 9, GARDEN)) {
            shell(tower, 9, 9, 10, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.STONE_BRICKS);
            for (int y = 0; y < 11; y++)
                put(tower.offset(1, y, 1), Blocks.LADDER.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            for (int i = 0; i <= 9; i++) for (int side : new int[] {0, 9}) {
                put(tower.offset(i, 11, side), Blocks.STONE_BRICK_WALL);
                put(tower.offset(side, 11, i), Blocks.STONE_BRICK_WALL);
            }
            put(tower.offset(7, 0, 3), Blocks.LECTERN);
            put(tower.offset(7, 0, 5), Blocks.BOOKSHELF);
            sign(tower.offset(7, 0, 10), "TORRE COSTIERA", "Nisida storica");
        }
        islandGreen(island);
        var mainland = NapoliAtlas.find("scienza");
        var islandPlace = NapoliAtlas.find("nisida");
        int last = 322, first = 268;
        int left = bridgeX(mainland, islandPlace, last) - 5;
        int right = bridgeX(mainland, islandPlace, first) + 5;
        NapoliAtlasLandmarks.generate(city, new BlockPos(left, 65, first), right - left, last - first);
        for (int z = first; z <= last; z++) {
            int x = bridgeX(mainland, islandPlace, z);
            for (int side : new int[] {-1, 1}) {
                var deck = new BlockPos(x + side * 5, 65, z);
                if (!NapoliExpansion.water(deck.getX(), z)) continue;
                if (z % 7 == 0) fill(deck.atY(61), deck, Blocks.STONE_BRICKS);
                else add(deck, Blocks.STONE_BRICKS);
                if (city.getBlockState(deck).is(Blocks.STONE_BRICKS)
                        || city.getBlockState(deck).is(Blocks.POLISHED_DEEPSLATE))
                    add(deck.above(), Blocks.STONE_BRICK_WALL);
            }
        }
        var lazzaretto = chiuppino();
        if (prepare(lazzaretto, 11, 8, 11, SHORE)) {
            foundation(lazzaretto, 11, 11, 60);
            shell(lazzaretto, 11, 11, 6, Blocks.SMOOTH_SANDSTONE, Blocks.TUFF_BRICKS, Blocks.BRICKS);
            for (int x : new int[] {2, 9}) {
                fill(lazzaretto.offset(x, 0, 2), lazzaretto.offset(x, 0, 7), Blocks.SPRUCE_SLAB);
                put(lazzaretto.offset(x, 1, 2), Blocks.DECORATED_POT);
            }
            put(lazzaretto.offset(6, 0, 4), Blocks.LECTERN);
            sign(lazzaretto.offset(8, 0, 12), "CHIUPPINO", "Lazzaretto storico");
            for (int x = 5; x <= 17; x++) for (int dz = 12; dz <= 13; dz++) {
                var path = lazzaretto.offset(x, -1, dz);
                if (!city.getBlockState(path).is(Blocks.POLISHED_DEEPSLATE))
                    fill(path.atY(63), path, Blocks.STONE_BRICKS);
                replace(path.above(), Blocks.STONE_BRICK_WALL, Blocks.AIR);
            }
        }
    }

    private void islandGreen(BlockPos island) {
        // Keep the central approach free and break up the flat area around the historic buildings.
        for (var lawn : List.of(island.offset(-31, 0, -12), island.offset(-20, 0, -23),
                island.offset(12, 0, 8), island.offset(23, 0, 16), island.offset(-27, 0, 25))) {
            for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) {
                var pos = lawn.offset(x, -1, z);
                if (!city.isEmptyBlock(pos.above()) || Math.abs(x) == 4 && Math.abs(z) == 3) continue;
                replace(pos, Blocks.SMOOTH_STONE, Math.abs(x) != 4 && Math.abs(z) != 3
                        ? Blocks.GRASS_BLOCK : Blocks.POLISHED_TUFF);
            }
            if (!city.getBlockState(lawn.below()).is(Blocks.GRASS_BLOCK)) continue;
            var canopy = lawn.above(4);
            boolean space = true;
            for (var p : BlockPos.betweenClosed(lawn.offset(-2, 0, -2), canopy.offset(2, 1, 2))) {
                if (!editable(p) || !city.isEmptyBlock(p)) { space = false; break; }
            }
            if (!space) continue;
            fill(lawn, lawn.above(3), Blocks.OAK_LOG);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                if (Math.abs(x) + Math.abs(z) <= 3)
                    put(canopy.offset(x, 0, z), Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(BlockStateProperties.PERSISTENT, true));
            if (city.getBlockState(lawn.east(3).below()).is(Blocks.GRASS_BLOCK))
                add(lawn.east(3), Blocks.AZALEA);
        }
    }

    private static int bridgeX(NapoliAtlas.Place a, NapoliAtlas.Place b, int z) {
        return (int) Math.round(a.x() + (b.x() - a.x()) * ((double) (z - a.z()) / (b.z() - a.z())));
    }

    private void shell(BlockPos origin, int width, int depth, int height, Block wall, Block trim, Block roof) {
        for (var pos : BlockPos.betweenClosed(origin.below(), origin.offset(width, height, depth))) {
            int x = pos.getX() - origin.getX(), y = pos.getY() - origin.getY(), z = pos.getZ() - origin.getZ();
            boolean end = z == 0 || z == depth, edge = x == 0 || x == width || end;
            if (y == -1) put(pos, edge ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);
            else if (y == height) put(pos, roof);
            else if (edge) {
                int axis = end ? x : z;
                boolean window = axis > 1 && axis % 5 >= 2 && axis % 5 <= 3 && y >= 2 && y <= 4;
                put(pos, y == 0 || y == height - 1 ? trim : window ? Blocks.GLASS : wall);
            }
        }
        var door = origin.offset(width / 2, 0, depth);
        put(door.above(2), trim);
        var state = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        put(door, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        put(origin.offset(width / 2, height - 1, depth / 2),
                Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    private void table(BlockPos pos) {
        put(pos, Blocks.OAK_FENCE);
        put(pos.above(), Blocks.OAK_PRESSURE_PLATE);
        put(pos.west(), Blocks.SPRUCE_SLAB);
        put(pos.east(), Blocks.SPRUCE_SLAB);
    }

    private void foundation(BlockPos origin, int width, int depth, int low) {
        for (var pos : BlockPos.betweenClosed(origin.atY(low), origin.offset(width, -1, depth))) {
            var old = city.getBlockState(pos);
            if (old.isAir() || old.is(Blocks.WATER) || old.is(Blocks.SAND)) put(pos, Blocks.TUFF_BRICKS);
        }
    }

    private boolean prepare(BlockPos origin, int w, int h, int d, Set<Block> allowed) {
        var end = origin.offset(w, h, d);
        NapoliAtlasLandmarks.generate(city, origin, w, d);
        for (var pos : BlockPos.betweenClosed(origin.below(), end)) {
            var old = city.getBlockState(pos);
            if (!editable(pos) || pos.getY() >= origin.getY() && !old.isAir() && !allowed.contains(old.getBlock())) {
                Napuli.LOGGER.info("Coroglio: plot at {} keeps {} at {}", origin, old, pos);
                return false;
            }
        }
        for (var pos : BlockPos.betweenClosed(origin, end)) if (!city.isEmptyBlock(pos)) put(pos, Blocks.AIR);
        return true;
    }

    private static BlockState stair(Block block, Direction facing) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    private void pave(BlockPos pos) {
        if (PATH.contains(city.getBlockState(pos).getBlock()) && city.isEmptyBlock(pos.above()))
            put(pos, Blocks.POLISHED_ANDESITE);
    }

    private void sign(BlockPos pos, String first, String second) {
        if (city.isEmptyBlock(pos) && editable(pos) && city.getBlockState(pos.below()).isSolidRender())
            NapoliEmploymentMap.sign(city, pos, first, second);
    }

    private void fill(BlockPos from, BlockPos to, Block block) {
        for (var pos : BlockPos.betweenClosed(from, to)) put(pos, block);
    }

    private void add(BlockPos pos, Block block) {
        if (city.isEmptyBlock(pos)) put(pos, block);
    }

    private void replace(BlockPos pos, Block expected, Block block) {
        if (city.getBlockState(pos).is(expected)) put(pos, block);
    }

    private boolean editable(BlockPos pos) {
        return !CityProperty.playerPlaced(city, pos) && city.getBlockEntity(pos) == null;
    }

    private void put(BlockPos pos, Block block) {
        put(pos, block.defaultBlockState());
    }

    private void put(BlockPos pos, BlockState state) {
        if (editable(pos)) city.setBlock(pos, state, FLAGS);
    }
}
