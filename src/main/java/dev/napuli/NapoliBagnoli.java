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

/** Walkable industrial archaeology and the public waterfront of the Atlas district. */
final class NapoliBagnoli {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> TERRAIN = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND,
            Blocks.SANDSTONE, Blocks.TUFF, Blocks.STONE, Blocks.WATER);
    private static final Set<Block> VEGETATION = Set.of(Blocks.OAK_LOG, Blocks.OAK_LEAVES);
    private static final Set<Block> PAVING = Set.of(Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE,
            Blocks.POLISHED_DEEPSLATE, Blocks.STONE_BRICKS, Blocks.POLISHED_TUFF);
    private final ServerLevel city;

    private NapoliBagnoli(ServerLevel city) {
        this.city = city;
    }

    static BlockPos steelworks() { return new BlockPos(-1908, 66, 30); }
    static BlockPos blastFurnace() { return new BlockPos(-1877, 66, 29); }
    static BlockPos workshop() { return new BlockPos(-1959, 66, 28); }
    static BlockPos powerhouse() { return new BlockPos(-1956, 66, 7); }
    static BlockPos telex() { return new BlockPos(-1906, 66, 13); }
    static BlockPos pumps() { return new BlockPos(-1959, 66, 51); }
    static BlockPos coolingTower() { return new BlockPos(-1947, 66, 57); }
    static BlockPos lido() { return new BlockPos(-1978, 66, 38); }

    static void upgrade(ServerLevel city) {
        var work = new NapoliBagnoli(city);
        NapoliAtlasLandmarks.generate(city, new BlockPos(-1980, 65, 6), 141, 110);
        work.steelHall();
        work.furnaces();
        work.mechanicalShop();
        work.thermalStation();
        work.telexOffice();
        work.pumpingStation();
        work.cokeWorks();
        work.publicWaterfront();
        work.connectCampus();
    }

    private void steelHall() {
        var hall = steelworks();
        if (!prepare(hall, 28, 20, 24)) return;
        envelope(hall, 28, 24, 13, Blocks.BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_STONE);
        // The main shed has a clerestory, repeated brick bays and a steel crane above the casting floor.
        for (int z = 0; z <= 24; z++) {
            for (int x = 0; x <= 28; x++) {
                int rise = (14 - Math.abs(x - 14)) / 4;
                if (z == 0 || z == 24) {
                    for (int y = 14; y <= 13 + rise; y++) put(hall.offset(x, y, z), Blocks.BRICKS);
                }
                fill(hall.offset(x, 14, z), hall.offset(x, 14 + rise, z), Blocks.POLISHED_DEEPSLATE);
            }
            for (int x : new int[] {2, 26}) put(hall.offset(x, 9, z), Blocks.IRON_BLOCK);
        }
        for (int z : new int[] {4, 10, 16, 22}) {
            for (int x : new int[] {2, 26})
                fill(hall.offset(x, 0, z), hall.offset(x, 12, z), Blocks.POLISHED_DEEPSLATE);
            fill(hall.offset(2, 12, z), hall.offset(26, 12, z), Blocks.POLISHED_DEEPSLATE);
            hang(hall.offset(7, 11, z));
            hang(hall.offset(21, 11, z));
        }
        gantry(hall.offset(2, 0, 12), 24, 9);
        var ladle = hall.offset(9, 1, 4);
        fill(ladle.offset(-3, -1, -3), ladle.offset(3, -1, 3), Blocks.DEEPSLATE_BRICKS);
        disk(ladle, 2, Blocks.POLISHED_DEEPSLATE);
        for (int y = 1; y <= 3; y++) ring(ladle.above(y), 2, Blocks.IRON_BLOCK);
        ring(ladle.above(4), 2, Blocks.POLISHED_DEEPSLATE);
        put(ladle.offset(3, 2, 0), CityBlocks.vanilla("waxed_cut_copper"));
        put(ladle.offset(-3, 2, 0), CityBlocks.vanilla("waxed_cut_copper"));
        fill(ladle.offset(3, 0, 0), ladle.offset(3, 1, 0), Blocks.IRON_BLOCK);
        fill(ladle.offset(-3, 0, 0), ladle.offset(-3, 1, 0), Blocks.IRON_BLOCK);
        fill(hall.offset(8, 0, 8), hall.offset(11, 0, 16), Blocks.DEEPSLATE_BRICKS);
        for (int z : new int[] {9, 12, 15}) put(hall.offset(9, 1, z), Blocks.CAULDRON);
        fill(hall.offset(17, 0, 8), hall.offset(20, 0, 15), Blocks.STONE_BRICKS);
        for (int z : new int[] {9, 12, 14}) put(hall.offset(19, 1, z), Blocks.ANVIL);
        put(hall.offset(6, 0, 19), Blocks.LECTERN);
        put(hall.offset(22, 0, 19), Blocks.BOOKSHELF);
        for (int x = 12; x <= 16; x++)
            fill(hall.offset(x, 0, 24), hall.offset(x, 4, 24), Blocks.AIR);
        fill(hall.offset(12, 5, 24), hall.offset(16, 5, 24), Blocks.IRON_BLOCK);
        sign(hall.offset(18, 0, 25), "ACCIAIERIA", "Sala di colata");
    }

    private void furnaces() {
        var yard = blastFurnace();
        if (!prepare(yard, 31, 32, 17)) return;
        var furnace = yard.offset(8, 0, 7);
        for (int y = 0; y <= 24; y++) {
            int radius = y < 5 ? 3 : y < 12 ? 5 : y < 18 ? 4 : 3;
            ring(furnace.above(y), radius, y % 5 == 0 ? Blocks.IRON_BLOCK : Blocks.POLISHED_DEEPSLATE);
        }
        disk(furnace.above(24), 3, Blocks.IRON_BLOCK);
        for (int dx : new int[] {-6, 6}) for (int dz : new int[] {-6, 6}) {
            fill(furnace.offset(dx, 0, dz), furnace.offset(dx, 27, dz), Blocks.DEEPSLATE_BRICKS);
            for (int y : new int[] {7, 16, 26})
                fill(furnace.offset(dx, y, -6), furnace.offset(dx, y, 6), Blocks.IRON_BLOCK);
        }
        for (int y : new int[] {7, 16, 26}) for (int z : new int[] {-6, 6})
            fill(furnace.offset(-6, y, z), furnace.offset(6, y, z), Blocks.IRON_BLOCK);
        // Three separate hot-blast stoves with round crowns and high ducts back to the furnace.
        for (int x : new int[] {18, 23, 28}) {
            var cowper = yard.offset(x, 0, 8);
            for (int y = 0; y <= 16; y++) ring(cowper.above(y), 2, y % 4 == 0 ? Blocks.IRON_BLOCK : Blocks.BRICKS);
            disk(cowper.above(17), 2, Blocks.BRICKS);
            disk(cowper.above(18), 1, Blocks.BRICKS);
            fill(cowper.above(19), cowper.above(20), Blocks.IRON_BLOCK);
        }
        fill(yard.offset(8, 20, 8), yard.offset(28, 20, 8), Blocks.IRON_BLOCK);
        fill(yard.offset(7, 21, 8), yard.offset(28, 21, 8), Blocks.POLISHED_DEEPSLATE);
        for (int x = -1; x <= 1; x++) fill(furnace.offset(x, 0, 3), furnace.offset(x, 2, 3), Blocks.AIR);
        put(furnace.offset(0, 0, -1), Blocks.BLAST_FURNACE);
        put(furnace.offset(2, 0, 0), Blocks.LECTERN);
        put(furnace.offset(-2, 0, 0), Blocks.CAULDRON);
        fill(furnace.offset(-5, 5, 0), furnace.offset(5, 5, 0), Blocks.IRON_BLOCK);
        put(furnace.offset(0, 4, 0), Blocks.SEA_LANTERN);
        gantry(yard.offset(17, 0, 15), 13, 7);
        sign(yard.offset(10, 0, 15), "ALTOFORNO N. 4", "Cowpers e Moxey");
    }

    private void mechanicalShop() {
        var shop = workshop();
        if (!prepare(shop, 20, 13, 18)) return;
        envelope(shop, 20, 18, 8, Blocks.BRICKS, Blocks.CUT_SANDSTONE, Blocks.STONE_BRICKS);
        for (int x = 0; x <= 20; x++) {
            int pitch = (10 - Math.abs(x - 10)) / 3;
            fill(shop.offset(x, 8, 0), shop.offset(x, 8 + pitch, 18), Blocks.BRICKS);
            for (int z : new int[] {0, 18}) for (int y = 8; y < 8 + pitch; y++)
                put(shop.offset(x, y, z), Blocks.CUT_SANDSTONE);
        }
        for (int z : new int[] {4, 9, 14}) {
            put(shop.offset(5, 0, z), Blocks.CRAFTING_TABLE);
            put(shop.offset(6, 0, z), Blocks.STONECUTTER);
            put(shop.offset(14, 0, z), Blocks.SMITHING_TABLE);
            put(shop.offset(15, 0, z), Blocks.ANVIL);
            fill(shop.offset(2, 6, z), shop.offset(18, 6, z), Blocks.POLISHED_DEEPSLATE);
            hang(shop.offset(5, 5, z));
            hang(shop.offset(15, 5, z));
        }
        door(shop.offset(10, 0, 18), Direction.SOUTH);
        sign(shop.offset(13, 0, 19), "OFFICINA", "MECCANICA");
    }

    private void thermalStation() {
        var hall = powerhouse();
        if (!prepare(hall, 24, 28, 16)) return;
        envelope(hall, 24, 16, 10, Blocks.BRICKS, Blocks.POLISHED_ANDESITE, CityBlocks.vanilla("light_gray_concrete"));
        for (int x : new int[] {5, 17}) {
            var boiler = hall.offset(x, 0, 5);
            for (int y = 0; y <= 4; y++) ring(boiler.above(y), 2, Blocks.POLISHED_DEEPSLATE);
            disk(boiler.above(5), 2, Blocks.IRON_BLOCK);
            fill(boiler.offset(0, 6, 0), boiler.offset(0, 9, 0), Blocks.IRON_BLOCK);
            put(boiler.offset(0, 0, 3), Blocks.BLAST_FURNACE);
            put(boiler.offset(2, 0, 3), Blocks.REDSTONE_LAMP);
        }
        stack(hall.offset(4, 11, 4), 16);
        stack(hall.offset(20, 11, 4), 14);
        for (int x = 1; x <= 23; x++)
            put(hall.offset(x, 8, 12), x % 5 == 0 ? Blocks.SEA_LANTERN : Blocks.IRON_BLOCK);
        put(hall.offset(18, 0, 12), Blocks.LECTERN);
        door(hall.offset(12, 0, 16), Direction.SOUTH);
        sign(hall.offset(16, 0, 17), "CENTRALE", "TERMICA");
    }

    private void telexOffice() {
        var office = telex();
        if (!prepare(office, 13, 7, 12)) return;
        NapoliMonumentInteriors.place(city, "bagnoli_telex", office.below(),
                (pos, old) -> pos.getY() == office.getY() - 1 && (PAVING.contains(old.getBlock()) || TERRAIN.contains(old.getBlock())),
                List.of(Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE,
                        Blocks.BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_STONE_SLAB,
                        Blocks.GLASS, Blocks.SPRUCE_DOOR, Blocks.OBSERVER, Blocks.REDSTONE_LAMP,
                        Blocks.LANTERN, Blocks.LECTERN, Blocks.BOOKSHELF));
        sign(office.offset(10, 0, 13), "CENTRALINA", "TELEX");
    }

    private void pumpingStation() {
        var hall = pumps();
        if (!prepare(hall, 11, 9, 10)) return;
        envelope(hall, 11, 10, 6, Blocks.POLISHED_ANDESITE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_STONE);
        fill(hall.offset(1, 0, 2), hall.offset(4, 2, 7), Blocks.GLASS);
        fill(hall.offset(2, 0, 3), hall.offset(3, 0, 6), Blocks.WATER);
        fill(hall.offset(1, -1, 2), hall.offset(4, -1, 7), Blocks.PRISMARINE_BRICKS);
        for (int z : new int[] {3, 6}) {
            put(hall.offset(8, 0, z), Blocks.CAULDRON);
            fill(hall.offset(8, 1, z), hall.offset(8, 4, z), Blocks.IRON_BLOCK);
            fill(hall.offset(3, 4, z), hall.offset(8, 4, z), Blocks.IRON_BLOCK);
            hang(hall.offset(6, 5, z));
        }
        door(hall.offset(6, 0, 10), Direction.SOUTH);
        sign(hall.offset(8, 0, 11), "SALA POMPE", "Canali e valvole");
    }

    private void cokeWorks() {
        var lot = coolingTower();
        if (!prepare(lot, 18, 25, 14)) return;
        var tower = lot.offset(5, 0, 6);
        for (int y = 0; y <= 21; y++) {
            int radius = y < 4 ? 4 : y < 12 ? 3 : y < 17 ? 2 : 3;
            ring(tower.above(y), radius, y % 7 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
        }
        for (int x = -1; x <= 1; x++) fill(tower.offset(x, 0, 4), tower.offset(x, 3, 4), Blocks.AIR);
        put(tower.offset(0, 0, 0), Blocks.LECTERN);
        for (int x = 10; x <= 17; x += 2) {
            fill(lot.offset(x, 0, 3), lot.offset(x, 3, 8), Blocks.BRICKS);
            put(lot.offset(x, 1, 9), Blocks.IRON_BLOCK);
        }
        fill(lot.offset(13, 3, 3), lot.offset(17, 3, 7), Blocks.POLISHED_ANDESITE);
        stack(lot.offset(15, 4, 5), 19);
        sign(lot.offset(7, 0, 13), "TORRE COKE", "Torre di spegnimento");
    }

    private void publicWaterfront() {
        var lounge = lido();
        if (prepare(lounge, 16, 8, 12)) {
            envelope(lounge, 16, 12, 5, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.SPRUCE_PLANKS);
            for (int x = 2; x <= 14; x++) if (Math.abs(x - 8) > 1)
                fill(lounge.offset(x, 1, 12), lounge.offset(x, 3, 12), Blocks.GLASS);
            for (int x : new int[] {2, 6, 10, 14}) {
                for (int z : new int[] {5, 8}) {
                    put(lounge.offset(x, 0, z), Blocks.SPRUCE_STAIRS);
                    put(lounge.offset(x + 1, 0, z), Blocks.SPRUCE_SLAB);
                }
            }
            for (int x = 0; x <= 16; x++) for (int z = 13; z <= 17; z++)
                deck(lounge.offset(x, -1, z), Blocks.SPRUCE_PLANKS);
            for (int x : new int[] {2, 6, 10, 14})
                put(lounge.offset(x, 0, 16), Blocks.SMOOTH_QUARTZ_STAIRS);
            door(lounge.offset(8, 0, 12), Direction.SOUTH);
            sign(lounge.offset(12, 0, 13), "LIDO COMUNALE", "Terrazza a mare");
        }
        var pier = NapoliAtlas.find("pontile");
        var end = new BlockPos(pier.x(), 65, pier.z() + pier.radius() - 2);
        // An open lookout shelters benches while the middle of the pier stays walkable.
        for (int x : new int[] {-4, 4}) for (int z : new int[] {-4, 1}) {
            var column = end.offset(x, 1, z);
            if (!city.getBlockState(column.below()).is(Blocks.STONE_BRICKS)) continue;
            for (int y = 0; y < 4; y++) add(column.above(y), Blocks.POLISHED_ANDESITE);
        }
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 1; z++)
            add(end.offset(x, 5, z), Blocks.SMOOTH_STONE_SLAB);
        for (int x : new int[] {-3, 3}) add(end.offset(x, 1, -1), Blocks.SPRUCE_STAIRS);
    }

    private void connectCampus() {
        // Raised access shares the level of the existing pier deck at y=65.
        for (var entry : List.of(steelworks().offset(14, 0, 24), workshop().offset(10, 0, 18),
                powerhouse().offset(12, 0, 16), telex().offset(7, 0, 12), pumps().offset(6, 0, 10)))
            for (int z = 1; z <= 3; z++) for (int x = -2; x <= 2; x++) pave(entry.offset(x, -1, z));
        for (int x = -1920; x <= -1886; x++) pave(new BlockPos(x, 65, 58));
        for (int x = -1968; x <= -1860; x++) for (int z = 26; z <= 28; z++)
            pave(new BlockPos(x, 65, z));
        for (int x = -1966; x <= -1892; x++) for (int z = 49; z <= 51; z++)
            pave(new BlockPos(x, 65, z));
        for (int z = 51; z <= 63; z++) pave(new BlockPos(-1960, 65, z));
        for (int x = -1960; x <= -1952; x++) pave(new BlockPos(x, 65, 63));
        for (int x = -1933; x <= -1901; x++) for (int z = 72; z <= 74; z++)
            pave(new BlockPos(x, 65, z));
        for (int z = 27; z <= 72; z++) {
            for (int x : new int[] {-1924, -1923, -1918, -1917}) pave(new BlockPos(x, 65, z));
        }
        for (int x : new int[] {-1954, -1940, -1904, -1880}) {
            var path = new BlockPos(x, 65, 27);
            if (city.getBlockState(path).is(Blocks.POLISHED_ANDESITE)) {
                add(path.above(), Blocks.POLISHED_BLACKSTONE_WALL);
                add(path.above(2), Blocks.LANTERN);
            }
        }
        sign(new BlockPos(-1913, 66, 28), "EX ITALSIDER", "Piazza archeologie");
    }

    private void envelope(BlockPos p, int width, int depth, int h, Block wall, Block trim, Block roof) {
        for (var pos : BlockPos.betweenClosed(p.below(), p.offset(width, h, depth))) {
            int x = pos.getX() - p.getX(), y = pos.getY() - p.getY(), z = pos.getZ() - p.getZ();
            boolean end = z == 0 || z == depth, edge = x == 0 || x == width || end;
            if (y == -1) put(pos, edge ? trim : Blocks.SMOOTH_STONE);
            else if (y == h) put(pos, roof);
            else if (edge) {
                int bay = end ? x : z;
                boolean window = bay > 1 && (bay % 6 == 2 || bay % 6 == 3)
                        && (y >= 2 && y <= Math.min(5, h - 2) || y >= 8 && y <= h - 2);
                put(pos, y == 0 || y == h - 1 ? trim : window ? Blocks.GLASS : wall);
            }
        }
    }

    private void gantry(BlockPos p, int span, int lift) {
        for (int x : new int[] {0, span}) fill(p.offset(x, 0, 0), p.offset(x, lift, 0), Blocks.IRON_BLOCK);
        fill(p.above(lift), p.offset(span, lift, 0), Blocks.IRON_BLOCK);
        fill(p.above(lift + 1), p.offset(span, lift + 1, 0), CityBlocks.vanilla("yellow_concrete"));
        var hook = p.offset(span / 2, lift - 1, 0);
        fill(hook.below(3), hook, CityBlocks.vanilla("iron_chain"));
        put(hook.below(4), Blocks.IRON_BARS);
    }

    private void stack(BlockPos base, int height) {
        for (int y = 0; y <= height; y++) ring(base.above(y), y < height / 3 ? 2 : 1,
                y > height - 3 ? Blocks.POLISHED_ANDESITE : Blocks.BRICKS);
    }

    private void disk(BlockPos p, int r, Block block) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
            if (x * x + z * z <= r * r + 1) put(p.offset(x, 0, z), block);
    }

    private void ring(BlockPos p, int r, Block block) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            int distance = x * x + z * z;
            if (distance <= r * r + 1 && distance >= Math.max(1, (r - 1) * (r - 1)))
                put(p.offset(x, 0, z), block);
        }
    }

    private boolean prepare(BlockPos origin, int w, int h, int d) {
        var end = origin.offset(w, h, d);
        for (var pos : BlockPos.betweenClosed(origin.below(), end)) {
            var old = city.getBlockState(pos);
            if (!editable(pos) || pos.getY() >= origin.getY() && !old.isAir()
                    && !TERRAIN.contains(old.getBlock()) && !VEGETATION.contains(old.getBlock())) {
                Napuli.LOGGER.info("Bagnoli: preserved occupied plot {} at {} ({})", origin, pos, old);
                return false;
            }
        }
        for (var pos : BlockPos.betweenClosed(origin, end))
            if (!city.isEmptyBlock(pos)) put(pos, Blocks.AIR);
        for (int x = 0; x <= w; x++) for (int z = 0; z <= d; z++)
            deck(origin.offset(x, -1, z), Blocks.POLISHED_ANDESITE);
        return true;
    }

    private void deck(BlockPos pos, Block block) {
        if (!editable(pos)) return;
        for (int y = 61; y < pos.getY(); y++) {
            var lower = pos.atY(y);
            var old = city.getBlockState(lower);
            if (old.isAir() || old.is(Blocks.WATER)) put(lower, Blocks.STONE_BRICKS);
        }
        put(pos, block);
    }

    private void pave(BlockPos pos) {
        var old = city.getBlockState(pos);
        if (city.isEmptyBlock(pos.above()) && city.isEmptyBlock(pos.above(2))
                && (old.isAir() || TERRAIN.contains(old.getBlock()) || old.is(Blocks.SMOOTH_STONE)))
            deck(pos, Blocks.POLISHED_ANDESITE);
    }

    private void hang(BlockPos pos) {
        put(pos, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    private void door(BlockPos p, Direction facing) {
        var state = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        put(p, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(p.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private void sign(BlockPos pos, String first, String second) {
        if (editable(pos) && city.isEmptyBlock(pos) && city.getBlockState(pos.below()).isSolidRender())
            NapoliEmploymentMap.sign(city, pos, first, second);
    }

    private void fill(BlockPos from, BlockPos to, Block block) {
        for (var pos : BlockPos.betweenClosed(from, to)) put(pos, block);
    }

    private void add(BlockPos pos, Block block) {
        if (city.isEmptyBlock(pos)) put(pos, block);
    }

    private boolean editable(BlockPos pos) {
        return !CityProperty.playerPlaced(city, pos) && city.getBlockEntity(pos) == null;
    }

    private void put(BlockPos pos, Block block) { put(pos, block.defaultBlockState()); }

    private void put(BlockPos pos, BlockState state) {
        if (editable(pos)) city.setBlock(pos, state, FLAGS);
    }
}
