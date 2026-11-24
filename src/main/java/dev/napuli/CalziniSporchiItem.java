package dev.napuli;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Reusable novelty socks: hold them to your nose for a nauseating burst of regeneration. */
public final class CalziniSporchiItem extends Item {
    static final int SNIFF_TICKS = 24;
    static final int NAUSEA_TICKS = 20 * 15;
    static final int REGEN_TICKS = 20 * 10;
    static final int COOLDOWN_TICKS = 20 * 5;

    public CalziniSporchiItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return SNIFF_TICKS; }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.DRINK; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel world && entity instanceof ServerPlayer player
                && !player.getCooldowns().isOnCooldown(stack)) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 0));
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            CityEffects.sound(world,null, player.blockPosition(), SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 0.7f, 1.7f);
            CityEffects.particles(world,ParticleTypes.SNEEZE, player.getX(), player.getEyeY() - 0.2, player.getZ(),
                    7, 0.18, 0.12, 0.18, 0.01);
            player.sendOverlayMessage(Component.translatable("napuli.socks.sniff"));
        }
        return stack;
    }
}
