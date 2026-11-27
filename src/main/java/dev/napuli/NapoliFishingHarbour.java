package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

final class NapoliFishingHarbour {
    private NapoliFishingHarbour() {}

    static void build(ServerLevel level) {
        refurbishBorgo(level);
        // Open water outside the old harbour, with a wooden pier and serviced fishing boats.
        for (int z = 56; z <= 75; z++) {
            for (int x = -25; x <= -23; x++) put(level, x, 63, z, Blocks.SPRUCE_PLANKS);
            if (z % 3 == 2) for (int x : new int[]{-25, -23}) {
                for (int y = 60; y <= 63; y++) put(level, x, y, z, Blocks.STRIPPED_SPRUCE_LOG);
                put(level, x, 64, z, Blocks.SPRUCE_FENCE);
            }
        }
        put(level, -25, 65, 65, Blocks.LANTERN);
        put(level, -25, 65, 74, Blocks.LANTERN);
        for (int x : new int[]{-28, -20}) for (int z : new int[]{69, 74}) {
            BlockPos berth = new BlockPos(x, 63, z);
            level.getChunkAt(berth);
            for (BlockPos pos : BlockPos.betweenClosed(berth.offset(-1, -3, -1), berth.offset(1, 0, 1)))
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            String type = z == 69 ? "spruce_chest_boat" : "oak_boat";
            var entityType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(type));
            if (entityType == null) continue;
            var boat = entityType.create(level, EntitySpawnReason.STRUCTURE);
            if (boat == null) continue;
            boat.snapTo(x + .5, 63.8, z + .5, x < -24 ? 0 : 180, 0);
            if (level.addFreshEntity(boat)) Napuli.LOGGER.info("Launched fishing boat at {}", berth);
        }
        // Low fishing store with tuff corners, plaster infill, tiled roof and a working counter.
        for (int x = -28; x <= -20; x++) for (int z = 49; z <= 54; z++) {
            put(level, x, 63, z, Blocks.TUFF_BRICKS);
            if (x == -28 || x == -20 || z == 49) {
                for (int y = 64; y <= 67; y++) put(level, x, y, z, y == 64 ? Blocks.TUFF : Blocks.SMOOTH_SANDSTONE);
            }
            for (int y = 68; y <= 69; y++) {
                if (y == 69 && (z == 49 || z == 54)) continue;
                if (z >= 51 && z <= 52 && y == 69) put(level, x, y, z, Blocks.BRICKS);
                else if (y == 68) level.setBlock(new BlockPos(x, y, z), Blocks.BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, z <= 51 ? Direction.SOUTH : Direction.NORTH), Block.UPDATE_CLIENTS);
            }
        }
        for (int x : new int[]{-27, -21}) {
            for (int y = 64; y <= 68; y++) put(level, x, y, 54, Blocks.STRIPPED_SPRUCE_LOG);
            put(level, x, 67, 55, Blocks.DARK_OAK_FENCE);
            put(level, x, 68, 55, Blocks.LANTERN);
        }
        for (int x : new int[]{-27, -26, -22, -21}) put(level, x, 64, 53, Blocks.BARREL);
        for (int x = -26; x <= -22; x++) put(level, x, 67, 54, Blocks.SPRUCE_PLANKS);
        put(level, -26, 64, 51, NapoliKitchen.BLOCKS.get("banca_frittura"));
        put(level, -22, 64, 51, NapoliKitchen.BLOCKS.get("tavolo_pizzaiolo"));
        // Simple plaster, stone ledges and dark shutters around an open service window.
        for (int x = -27; x <= -21; x++) put(level, x, 65, 49, x % 2 == 0 ? Blocks.GLASS_PANE : Blocks.SMOOTH_SANDSTONE);
    }

    static void refitStore(ServerLevel level) {
        var origin = new BlockPos(-28,63,49);
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        var template = level.getStructureTemplateManager().get(Napuli.id("pescheria_lungomare"));
        if (template.isEmpty()) return;
        for (var pos : BlockPos.betweenClosed(origin, origin.offset(8,7,6)))
            if (CityProperty.playerPlaced(level,pos)) return;

        var materials = java.util.List.of(Blocks.TUFF_BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.STRIPPED_SPRUCE_LOG,
                Blocks.BRICKS, Blocks.BRICK_SLAB, Blocks.GLASS, Blocks.BARREL, Blocks.LANTERN,
                BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("cyan_wool")),
                BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("white_wool")),
                NapoliKitchen.BLOCKS.get("banca_frittura"), NapoliKitchen.BLOCKS.get("tavolo_pizzaiolo"));
        for (var material : materials)
            for (var part : template.get().filterBlocks(origin, settings, material)) {
                if (level.getBlockEntity(part.pos()) == null)
                    level.setBlock(part.pos(),part.state(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            }
    }


    static void refitBorgoStalls(ServerLevel level) {
        var template = level.getStructureTemplateManager().get(Napuli.id("banco_borgo"));
        if (template.isEmpty()) return;
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        var blue = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("blue_wool"));
        var white = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("white_wool"));
        String[] colors = {"blue_wool", "green_wool", "red_wool"};
        for (int i = 0; i < colors.length; i++) {
            var origin = new BlockPos(21 + i * 7,64,75);
            boolean edited = false;
            for (var pos : BlockPos.betweenClosed(origin,origin.offset(6,4,2)))
                if (CityProperty.playerPlaced(level,pos)) { edited = true; break; }
            if (edited) continue;
            var accent = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(colors[i]));
            for (var material : java.util.List.of(blue,white,Blocks.LANTERN,Blocks.STRIPPED_SPRUCE_LOG))
                for (var part : template.get().filterBlocks(origin,settings,material))
                    if (level.isEmptyBlock(part.pos()))
                        level.setBlock(part.pos(),material==blue?accent.defaultBlockState():part.state(),
                                Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }


    private static void refurbishBorgo(ServerLevel level) {
        Block cloth = CityBlocks.vanilla("white_wool");
        String[] colors = {"blue_wool", "green_wool", "red_wool", "yellow_wool"};
        for (int i = 0; i < colors.length; i++) {
            int start = 19 + i * 7;
            Block stripe = CityBlocks.vanilla(colors[i]);
            for (int dx = 1; dx <= 5; dx++) for (int z = 80; z <= 81; z++) {
                BlockPos pos = new BlockPos(start + dx, 67, z);
                if (level.getBlockState(pos).isAir()) put(level, start + dx, 67, z, dx % 2 == 0 ? cloth : stripe);
            }
            for (int dx : new int[]{0, 6}) {
                BlockPos pos = new BlockPos(start + dx, 67, 81);
                if (level.getBlockState(pos).isAir()) put(level, start + dx, 67, 81, Blocks.LANTERN);
            }
        }
        put(level, 35, 64, 89, NapoliKitchen.BLOCKS.get("forno_legna_portatile"));
        put(level, 36, 64, 89, NapoliKitchen.BLOCKS.get("tavolo_pizzaiolo"));
        put(level, 42, 64, 89, NapoliKitchen.BLOCKS.get("banca_frittura"));
    }

    private static void put(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
