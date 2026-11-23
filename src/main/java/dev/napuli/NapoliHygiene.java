package dev.napuli;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

final class NapoliHygiene {
    static final BlockPos PUBLIC_SINK = new BlockPos(-116, 64, 36);
    private static final AttachmentType<Integer> CLEAN = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT).copyOnDeath().buildAndRegister(Napuli.id("hygiene"));
    private NapoliHygiene() {}

    static int value(ServerPlayer p) { return p.getAttachedOrElse(CLEAN, 100); }

    static BlockPos shower(NapoliMergellina.House house) {
        return new BlockPos(house.x() + 8, 64, house.z() + 9);
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 200 != 0) return;
            var city = server.getLevel(NapoliMap.LEVEL);
            if (city == null) return;
            for (ServerPlayer p : city.players()) {
                if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
                int before = value(p);
                int current = Math.max(0, before - (p.isSprinting() || p.isSwimming() ? 4 : 2));
                p.setAttached(CLEAN, current);
                if (before > 25 && current <= 25)
                    p.sendSystemMessage(Component.translatable("napuli.hygiene.dirty"));
                if (current <= 15) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 230, 0, false, false));
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != NapoliMap.LEVEL || hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (pos.equals(PUBLIC_SINK)) {
                if (player instanceof ServerPlayer p) wash(p, pos, Math.max(value(p), Math.min(70, value(p) + 30)));
                return InteractionResult.SUCCESS;
            }
            for (var house : NapoliMergellina.PROPERTIES) if (pos.equals(shower(house)) || (pos.equals(shower(house).above()) && NapoliShower.isColumn(level, pos))) {
                if (player instanceof ServerPlayer p) {
                    if (NapoliProperties.hasAccess(p, house)) wash(p, pos, 100);
                    else p.sendOverlayMessage(Component.translatable("napuli.property.private"));
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    static void wash(ServerPlayer player, BlockPos pos, int result) {
        player.setAttached(CLEAN, result);
        player.level().playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, .45f, .9f);
        player.level().sendParticles(ParticleTypes.SPLASH, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 14, .2, .2, .2, .025);
        player.sendOverlayMessage(Component.translatable("napuli.hygiene.washed", result));
    }
}
