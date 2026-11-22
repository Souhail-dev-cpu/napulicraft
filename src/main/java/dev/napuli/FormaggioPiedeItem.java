package dev.napuli;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A consumed cheese portion gives a short rush, followed by a delayed crash. */
public final class FormaggioPiedeItem extends Item {
    static final int SPEED_TICKS = 20 * 10, STRENGTH_TICKS = 20 * 3;
    static final int SLOWNESS_TICKS = 20 * 20, WEAKNESS_TICKS = 20 * 6;
    record Aftermath(int remaining, long lastTick) {
        static final Codec<Aftermath> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, SPEED_TICKS).fieldOf("remaining").forGetter(Aftermath::remaining),
                Codec.LONG.fieldOf("last_tick").forGetter(Aftermath::lastTick)).apply(i, Aftermath::new));
    }
    // Persist with the player, but follow vanilla potion behavior and clear on death.
    static final AttachmentType<Aftermath> AFTERMATH = AttachmentRegistry.<Aftermath>builder()
            .persistent(Aftermath.CODEC).buildAndRegister(Napuli.id("cheese_aftermath"));

    public FormaggioPiedeItem(Properties properties) { super(properties); }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity eater) {
        ItemStack result = super.finishUsingItem(stack, level, eater);
        if (level instanceof ServerLevel && eater instanceof ServerPlayer player) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_TICKS, 1));
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, STRENGTH_TICKS, 4));
            player.setAttached(AFTERMATH, new Aftermath(SPEED_TICKS, player.level().getServer().overworld().getGameTime()));
            player.sendOverlayMessage(Component.translatable("napuli.cheese.rush"));
        }
        return result;
    }

    static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Aftermath pending = player.getAttached(AFTERMATH);
            if (pending == null) continue;
            if (!player.isAlive()) { player.removeAttached(AFTERMATH); continue; }
            // Skip the eating tick and paused ticks. Logout pauses this countdown just like potion durations.
            if (pending.lastTick() == now) continue;
            if (pending.remaining() > 1) {
                player.setAttached(AFTERMATH, new Aftermath(pending.remaining() - 1, now));
                continue;
            }
            player.removeAttached(AFTERMATH);
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0));
            player.sendOverlayMessage(Component.translatable("napuli.cheese.crash"));
        }
    }
}
