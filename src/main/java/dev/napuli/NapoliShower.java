package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

final class NapoliShower {
    private static final Block TRAY = fixture("shower_tray");
    private static final Block COLUMN = fixture("shower_column");

    private NapoliShower() {}

    static void register() {}

    private static Block fixture(String name) {
        var key = ResourceKey.create(Registries.BLOCK, Napuli.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of()
                .setId(key).noCollision().strength(1.5f).sound(SoundType.STONE)));
    }

    static boolean isColumn(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBlockState(pos).is(COLUMN);
    }

    static void upgrade(ServerLevel level) {
        for (var house : NapoliMergellina.PROPERTIES) {
            BlockPos pos = NapoliHygiene.shower(house);
            if (!level.getBlockState(pos).is(Blocks.CAULDRON)
                    || !level.getBlockState(pos.above(2)).is(Blocks.END_ROD)
                    || !level.getBlockState(pos.above()).isAir()) continue;
            place(level, pos);
            replace(level, pos.above(2), Blocks.END_ROD, Blocks.AIR);
            replace(level, pos.south(), Blocks.QUARTZ_STAIRS, Blocks.AIR);
            replace(level, pos.east(), Blocks.GLASS_PANE, Blocks.AIR);
        }
    }

    static void place(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, TRAY.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(pos.above(), COLUMN.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void replace(ServerLevel level, BlockPos pos, Block old, Block replacement) {
        if (level.getBlockState(pos).is(old)) level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
