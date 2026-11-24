package dev.napuli;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

final class BombaCartaItem extends Item {
    BombaCartaItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.dimension() != NapoliMap.LEVEL) return InteractionResult.PASS;
        var stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        if (level instanceof ServerLevel city && player instanceof ServerPlayer server) {
            if (Carabinieri.isJailed(server)) return InteractionResult.FAIL;
            var point = player.position().add(player.getLookAngle().scale(1.5)).add(0, .65, 0);
            CityEffects.particles(city,ParticleTypes.SMOKE, point.x, point.y, point.z, 18, .38, .23, .38, .07);
            CityEffects.sound(city,null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1f, .84f);
            Carabinieri.crime(server, Carabinieri.Crime.VANDALISM);
            player.getCooldowns().addCooldown(stack, 60);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
