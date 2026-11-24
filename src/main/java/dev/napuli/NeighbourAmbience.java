package dev.napuli;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class NeighbourAmbience {
    private static final AttachmentType<Long> LAST_SOUND = AttachmentRegistry.create(Napuli.id("neighbour_ambience"));

    private NeighbourAmbience() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 80 != 0) return;
            ServerLevel city = server.getLevel(NapoliMap.LEVEL);
            if (city == null || city.players().isEmpty()) return;
            long time = NapoliBroadcast.time(city);
            lightWindows(city, time);
            long slot = city.getOverworldClockTime() / 600;
            if (slot == city.getAttachedOrElse(LAST_SOUND, Long.MIN_VALUE)) return;
            var cafeListeners = city.getPlayers(p -> NapoliMap.CAFE.distToCenterSqr(p.position()) <= 20 * 20);
            if (cafeListeners.isEmpty()) return;
            city.setAttached(LAST_SOUND, slot);
            SoundEvent sound = time >= 1000 && time < 2700 ? SoundEvents.IRON_DOOR_OPEN
                    : time >= 5400 && time < 7000 || time >= 9900 && time < 11600 ? SoundEvents.WOOD_PLACE
                    : time >= 13000 && time < 15000 && NapoliBroadcast.live(city) ? SoundEvents.NOTE_BLOCK_BIT.value()
                    : null;
            if (sound != null) CityEffects.sound(city,null, NapoliMap.CAFE, sound, SoundSource.AMBIENT, .22f, .95f);
        });
    }

    private static void lightWindows(ServerLevel city, long time) {
        for (var house : CityLivingHomes.PUBLIC_HOMES) {
            BlockPos light = new BlockPos(house.x() + 3, 71, house.z() + house.depth() - 2);
            if (!city.hasChunkAt(light) || city.getPlayers(p -> light.distToCenterSqr(p.position()) <= 40 * 40).isEmpty()) continue;
            int delay = Math.floorMod(house.id().hashCode(), 1300);
            boolean lit = time >= 12300 + delay && time < 17800 + delay || time < 900 + delay;
            var current = city.getBlockState(light);
            var state = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 11);
            if (lit && current.isAir()) city.setBlock(light, state, Block.UPDATE_CLIENTS);
            if (!lit && current.equals(state)) city.setBlock(light, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
