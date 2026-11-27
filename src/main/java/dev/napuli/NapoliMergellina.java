package dev.napuli;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

final class NapoliMergellina {
    record House(String id, String name, int x, int z, int price, Block wall, int width, int depth, int floors, int style) {
        House(String id, String name, int x, int z, int price, Block wall) { this(id, name, x, z, price, wall, 11, 12, 2, -1); }
        int civic() { return 2 * (-x - 123) + 1; }
        String address() { return name + " · " + civic(); }
        int ground(){return id.startsWith("atlas_")?DistrictLife.floor(this):64;}
        BlockPos entrance() { return new BlockPos(x + width / 2, ground(), z + depth); }
        BlockPos approach(int distance) { var at=entrance().south(distance);return id.startsWith("atlas_")?new BlockPos(at.getX(),CityStreets.walkingHeight(at.getX(),at.getZ())+1,at.getZ()):at; }
        BlockPos sign() { return entrance().east(2); }
        BlockPos inside() { return new BlockPos(x + 5, ground(), z + 8); }
        boolean contains(BlockPos p) {
            return p.getX() >= x && p.getX() < x + width && p.getZ() >= z && p.getZ() < z + depth
                    && p.getY() >= ground()-3 && p.getY() <= Math.max(ground()+14, ground() + floors * 5);
        }
    }

    static final List<House> PROPERTIES = properties();

    private static List<House> properties() {
        var houses = new java.util.ArrayList<>(List.of(
            new House("leone", "Casa del Leone", -157, 28, 55, Blocks.SMOOTH_SANDSTONE),
            new House("chiara", "Casa delle Terrazze", -182, 28, 85, Blocks.CUT_SANDSTONE),
            new House("golfo", "Casa sul Golfo", -214, 28, 120, Blocks.BRICKS),
            new House("limoni", "Casa dei Limoni", -232, 10, 70, Blocks.SMOOTH_SANDSTONE),
            new House("approdo", "Casa dell’Approdo", -272, 28, 95, Blocks.CUT_SANDSTONE),
            new House("giardini", "Casa dei Giardini", -294, 9, 110, Blocks.BRICKS),
            new House("ponente", "Casa di Ponente", -315, 28, 130, Blocks.SMOOTH_SANDSTONE)));
        String[] names = {"Vico Azzurro", "Casa del Glicine", "Villa Partenope", "Corte dei Musicisti", "Loft del Porto", "Attico Vesuvio", "Casa del Corno"};
        Block[] walls = {Blocks.BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA, Blocks.CALCITE};
        for (int i = 0; i < 28; i++) houses.add(new House("viva_" + (i + 1), names[i % 7] + " " + (i / 7 + 1),
                -534 + i % 7 * 27, -196 + i / 7 * 35, 145 + i * 13, walls[i % walls.length],
                15 + i % 4 * 2, 16 + i / 4 % 3 * 2, 2 + i % 3 / 2, i));
        return List.copyOf(houses);
    }

    private static final List<House> RESIDENCES = List.of(
            new House("corte", "Corte di Mergellina", -153, 4, 0, Blocks.CUT_SANDSTONE),
            new House("marina", "Casa dei Marinai", -197, 7, 0, Blocks.SMOOTH_SANDSTONE),
            new House("terrazza", "Casa del Vico", -222, -8, 0, Blocks.BRICKS),
            new House("corallo", "Casa del Corallo", -134, 27, 0, Blocks.BRICKS),
            new House("sole", "Casa del Sole", -173, 12, 0, Blocks.SMOOTH_SANDSTONE),
            new House("pergola", "Casa della Pergola", -201, 28, 0, Blocks.CUT_SANDSTONE),
            new House("pini", "Casa dei Pini", -237, 27, 0, Blocks.SMOOTH_SANDSTONE));

    private NapoliMergellina() {}

    static List<House> allHouses() {
        return java.util.stream.Stream.concat(PROPERTIES.stream(), RESIDENCES.stream()).toList();
    }

    static List<BlockPos> homes() { return RESIDENCES.stream().map(House::inside).toList(); }

    static void build(ServerLevel level) {
        // Westward extension from Via Partenope into a new waterfront district.
        fill(level, -247, 63, 43, -109, 63, 50, Blocks.POLISHED_DEEPSLATE);
        fill(level, -247, 63, 51, -110, 63, 57, Blocks.STONE_BRICKS);
        fill(level, -238, 63, 49, -228, 63, 50, Blocks.CALCITE);
        fill(level, -243, 63, -12, -242, 63, 45, Blocks.COBBLED_DEEPSLATE);
        fill(level, -237, 63, 20, -143, 63, 23, Blocks.COBBLED_DEEPSLATE);
        for (int x = -243; x <= -114; x += 10) {
            put(level, x, 64, 55, Blocks.ANDESITE_WALL);
            put(level, x, 65, 55, Blocks.ANDESITE_WALL);
            put(level, x, 66, 55, Blocks.LANTERN);
        }
        // Calm basin beside the promenade, with two navigable fingers and quay bollards.
        fill(level, -244, 60, 58, -126, 63, 76, Blocks.WATER);
        fill(level, -244, 60, 77, -126, 63, 182, Blocks.WATER);
        for (int x : new int[]{-225, -190}) {
            fill(level, x, 63, 56, x + 3, 63, 70, Blocks.SPRUCE_PLANKS);
            for (int z : new int[]{61, 66, 70}) {
                fill(level, x, 61, z, x, 64, z, Blocks.STRIPPED_SPRUCE_LOG);
                fill(level, x + 3, 61, z, x + 3, 64, z, Blocks.STRIPPED_SPRUCE_LOG);
            }
        }
        for (int x = -245; x <= -145; x += 20) {
            fill(level, x, 63, 12, x + 2, 63, 43, Blocks.COBBLED_DEEPSLATE);
            put(level, x + 1, 64, 41, Blocks.POTTED_FERN);
        }

        for (int x : new int[]{-163, -190, -221}) {
            fill(level, x, 63, 51, x + 3, 63, 53, Blocks.MOSS_BLOCK);
            fill(level, x + 1, 64, 52, x + 1, 66, 52, Blocks.OAK_LOG);
            fill(level, x, 67, 51, x + 2, 68, 53, Blocks.OAK_LEAVES);
            put(level, x + 3, 64, 52, Blocks.SPRUCE_STAIRS);
        }
        for (House house : PROPERTIES) buildHouse(level, house, true);
        for (House house : RESIDENCES) buildHouse(level, house, false);
        sign(level, new BlockPos(-123, 64, 51), "LUNGOMARE", "Mergellina ←", "Case e approdi");
        sign(level, new BlockPos(-180, 64, 52), "MERGELLINA", "Passeggiata", "← Porticciolo");
        for (int x : new int[]{-170, -201, -235}) {
            var pos = new BlockPos(x, 64, 47);
            level.getChunkAt(pos);
            NapoliResident.spawn(level, NapoliResident.Role.PASSERBY, pos, 0);
        }
        Napuli.LOGGER.info("Napoli v13: Mergellina and residential properties");
    }

    static void completeUpperFloors(ServerLevel level) {
        var houses = new java.util.ArrayList<>(PROPERTIES);
        houses.addAll(RESIDENCES);
        for (House h : houses) {
            if (h.style() >= 0) continue;
            int x = h.x(), z = h.z();
            boolean empty = true;
            for (BlockPos pos : BlockPos.betweenClosed(x + 1, 67, z + 1, x + 9, 70, z + 10)) {
                if (level.getBlockState(pos).isAir()) continue;
                empty = false;
                break;
            }
            if (!empty) continue;
            fill(level, x + 1, 67, z + 1, x + 9, 67, z + 10, Blocks.SPRUCE_PLANKS);
            for (int y = 64; y <= 68; y++) level.setBlock(new BlockPos(x + 1, y, z + 1),
                    Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
                    Block.UPDATE_CLIENTS);
            put(level, x + 8, 68, z + 3, Blocks.BOOKSHELF);
            put(level, x + 8, 69, z + 3, Blocks.BOOKSHELF);
            put(level, x + 5, 70, z + 5, Blocks.LANTERN);
            put(level, x + 7, 68, z + 8, Blocks.BARREL);
            for (int bx : new int[]{3, 4, 6, 7}) {
                BlockPos bed = new BlockPos(x + bx, 68, z + 5);
                Block bedBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                        net.minecraft.resources.Identifier.withDefaultNamespace("blue_bed"));
                level.setBlock(bed, bedBlock.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH)
                        .setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
                level.setBlock(bed.north(), bedBlock.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH)
                        .setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
            }
        }
    }

    static void buildHouse(ServerLevel level, House h, boolean forSale) {
        if (h.style() >= 0) { CityLivingHomes.buildHouse(level, h); return; }
        int x = h.x(), z = h.z();
        fill(level, x, 63, z, x + 10, 71, z + 11, h.wall());
        fill(level, x + 1, 64, z + 1, x + 9, 70, z + 10, Blocks.AIR);
        fill(level, x, 63, z, x + 10, 63, z + 11, Blocks.SPRUCE_PLANKS);
        fill(level, x - 1, 72, z - 1, x + 11, 72, z + 12, Blocks.BRICK_SLAB);
        fill(level, x, 67, z + 11, x + 10, 67, z + 11, Blocks.TUFF_BRICKS);
        fill(level, x + 1, 67, z + 12, x + 9, 67, z + 13, Blocks.SMOOTH_SANDSTONE_SLAB);
        for (int dx : new int[]{2, 5, 8}) {
            put(level, x + dx, 69, z + 11, Blocks.GLASS_PANE);
            put(level, x + dx - 1, 69, z + 11, Blocks.DARK_OAK_PLANKS);
            put(level, x + dx + 1, 69, z + 11, Blocks.DARK_OAK_PLANKS);
        }
        for (int dx : new int[]{2, 8}) {
            for (int y = 65; y <= 66; y++) put(level, x + dx, y, z + 11, Blocks.GLASS_PANE);
            put(level, x + dx, 64, z + 12, Blocks.FLOWER_POT);
        }
        fill(level, x + 5, 64, z + 11, x + 5, 65, z + 11, Blocks.AIR);
        NapoliExpansion.ensureDoor(level,h.entrance().north(),Direction.SOUTH);
        put(level, x + 5, 66, z + 8, Blocks.LANTERN);
        put(level, x + 2, 64, z + 3, Blocks.BARREL);
        put(level, x + 8, 64, z + 3, Blocks.CRAFTING_TABLE);
        BlockPos bed = new BlockPos(x + 2, 64, z + 5);
        Block bedBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace("blue_bed"));
        level.setBlock(bed, bedBlock.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS);
        level.setBlock(bed.north(), bedBlock.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
        if (forSale) sign(level, h.sign(), h.name(), "€ " + h.price(), "Tasto destro");
    }

    private static void sign(ServerLevel level, BlockPos pos, String... lines) {
        put(level, pos.getX(), pos.getY(), pos.getZ(), Blocks.OAK_SIGN);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            var text = net.minecraft.world.level.block.entity.SignText.EMPTY.asMutable();
            for (int i = 0; i < lines.length; i++) text.setLine(i, Component.literal(lines[i]));
            sign.setText(text.asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
            sign.setText(text.asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.BACK);
            sign.setChanged();
        }
    }

    private static void fill(ServerLevel level, int x, int y, int z, int x2, int y2, int z2, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(x, y, z, x2, y2, z2))
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void put(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
