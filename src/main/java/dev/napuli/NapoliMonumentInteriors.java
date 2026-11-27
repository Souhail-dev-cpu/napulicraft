package dev.napuli;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Interior modules for the two handcrafted Plebiscito monuments. */
final class NapoliMonumentInteriors {
    private NapoliMonumentInteriors() {}

    static void refit(ServerLevel city) {
        place(city, "sala_del_trono", new BlockPos(35, 70, 18), (pos, present) -> false,
                List.of(Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.GOLD_BLOCK,
                        material("red_wool"), Blocks.SEA_LANTERN, material("granite_wall"), Blocks.CANDLE,
                        material("yellow_carpet")));
        place(city, "altare_san_francesco", new BlockPos(-79, 63, NapoliPlebiscito.CZ - 12),
                NapoliMonumentInteriors::basilicaFinish,
                List.of(Blocks.CHISELED_QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_PILLAR,
                        Blocks.POLISHED_DIORITE, Blocks.POLISHED_GRANITE, Blocks.LAPIS_BLOCK, Blocks.CANDLE));
    }

    static void refitRoyalPalace(ServerLevel city) {
        place(city, "palazzo_reale_finiture", new BlockPos(32, 70, 6),
                (pos, present) -> pos.getY() == 70 && pos.getX() >= 35 && present.is(Blocks.SMOOTH_STONE),
                List.of(Blocks.POLISHED_ANDESITE_SLAB, Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_GRANITE,
                        Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE, Blocks.CHISELED_QUARTZ_BLOCK,
                        material("red_wool"), Blocks.GOLD_BLOCK, Blocks.SMOOTH_QUARTZ));
    }

    private static boolean basilicaFinish(BlockPos pos, BlockState present) {
        if (pos.getY() == 63) {
            return present.is(Blocks.SMOOTH_QUARTZ) || present.is(Blocks.POLISHED_DIORITE)
                    || present.is(Blocks.SEA_LANTERN);
        }
        return pos.getX() == -78 && pos.getY() == 65 && present.is(Blocks.SMOOTH_QUARTZ);
    }

    static void place(ServerLevel city, String name, BlockPos origin,
            BiPredicate<BlockPos, BlockState> replacesLegacyFinish, List<Block> materials) {
        place(city, name, origin, pos -> true, replacesLegacyFinish, materials);
    }

    static void place(ServerLevel city, String name, BlockPos origin, Predicate<BlockPos> inside,
            BiPredicate<BlockPos, BlockState> replacesLegacyFinish, List<Block> materials) {
        var template = city.getStructureTemplateManager().get(Napuli.id(name))
                .orElseThrow(() -> new IllegalStateException("Missing Napoli structure template: " + name));
        var settings = new StructurePlaceSettings();
        for (Block material : materials) {
            for (var part : template.filterBlocks(origin, settings, material)) {
                var pos = part.pos();
                if (!inside.test(pos)) continue;
                var present = city.getBlockState(pos);
                if (CityProperty.playerPlaced(city, pos) || city.getBlockEntity(pos) != null) continue;
                if (present.isAir() || replacesLegacyFinish.test(pos, present)) {
                    city.setBlock(pos, part.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    static Block material(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
    }
}
