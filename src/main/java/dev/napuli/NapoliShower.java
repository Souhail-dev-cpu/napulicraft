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

    private static final java.util.Map<ServerLevel, java.util.Map<BlockPos, Long>> RUNNING = new java.util.WeakHashMap<>();

    static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(NapoliShower::tick);
    }

    static boolean matches(net.minecraft.world.level.Level level, BlockPos tray, BlockPos hit) {
        return (hit.equals(tray) || hit.equals(tray.above())) && level.getBlockState(tray).is(TRAY)
                && isColumn(level, tray.above());
    }

    static void toggle(ServerLevel level, BlockPos tray) {
        var running = RUNNING.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        if (running.remove(tray) == null) running.put(tray.immutable(), level.getGameTime() + 180);
        CityEffects.sound(level, null, tray, net.minecraft.sounds.SoundEvents.LEVER_CLICK,
                net.minecraft.sounds.SoundSource.BLOCKS, .35f, 1);
    }

    private static void tick(ServerLevel level) {
        var running = RUNNING.get(level);
        if (running == null || running.isEmpty()) return;
        long now = level.getGameTime();
        if (now % 4 != 0) return;
        var iterator = running.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var tray = entry.getKey();
            if (entry.getValue() <= now || !level.hasChunkAt(tray) || !matches(level, tray, tray)) {
                iterator.remove();
                continue;
            }
            double x = tray.getX() + .53, y = tray.getY() + 1.93, z = tray.getZ() + .53;
            CityEffects.particles(level, net.minecraft.core.particles.ParticleTypes.FALLING_WATER,
                    x, y, z, 12, .15, .035, .16, .005);
            if (now % 20 != 0) continue;
            CityEffects.sound(level, null, tray, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH,
                    net.minecraft.sounds.SoundSource.BLOCKS, .25f, 1.4f);
            var booth = new net.minecraft.world.phys.AABB(tray).expandTowards(0, 1.3, 0);
            for (var player : level.players()) {
                if (!booth.contains(player.position()) || player.isSpectator()) continue;
                var house = CityLivingHomes.all().stream()
                        .filter(home -> CityLivingHomes.use(home, CityLivingHomes.Furniture.SHOWER).block().equals(tray))
                        .findFirst().orElse(null);
                if (house != null && house.price() > 0 && !HomePermissions.allows(player, house, HomePermissions.Capability.APPLIANCES)) continue;
                NapoliHygiene.soakHair(player);
                int hygiene = NapoliHygiene.value(player);
                if (hygiene < 100) NapoliHygiene.wash(player, tray, Math.min(100, hygiene + 25));
            }
        }
    }

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
