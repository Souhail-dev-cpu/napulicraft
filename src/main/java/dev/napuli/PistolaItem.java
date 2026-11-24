package dev.napuli;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Short-range arcade weapon for the city's encounters. */
final class PistolaItem extends Item {
    PistolaItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.dimension() != NapoliMap.LEVEL) return InteractionResult.PASS;
        var stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        if (level instanceof ServerLevel world && player instanceof ServerPlayer shooter) {
            Vec3 start = shooter.getEyePosition();
            Vec3 aim = start.add(shooter.getLookAngle().scale(14));
            Vec3 end = world.clip(new ClipContext(start, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter)).getLocation();
            var hit = ProjectileUtil.getEntityHitResult(shooter, start, end,
                    shooter.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                    entity -> entity instanceof LivingEntity && entity.isPickable(), start.distanceToSqr(end));
            if (hit != null && (hit.getEntity() instanceof Monster
                    || hit.getEntity() instanceof NapoliResident npc && npc.role().fighter())) {
                if (hit.getEntity().hurtServer(world, world.damageSources().playerAttack(shooter), 6f)) {
                    CityEffects.particles(world,ParticleTypes.CRIT, hit.getLocation().x, hit.getLocation().y,
                            hit.getLocation().z, 8, 0.15, 0.15, 0.15, 0.05);
                }
            }
            CityEffects.sound(world,null, shooter.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.6f, 1.6f);
            Vec3 tip = start.add(shooter.getLookAngle().scale(0.7));
            CityEffects.particles(world,ParticleTypes.FLAME, tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
            Vec3 impact = hit == null ? end : hit.getLocation();
            for (int i = 1; i <= 5; i++) {
                Vec3 dust = tip.lerp(impact, i / 6.0);
                CityEffects.particles(world,ParticleTypes.SMOKE, dust.x, dust.y, dust.z, 1, 0, 0, 0, 0);
            }
            Carabinieri.crime(shooter, Carabinieri.Crime.SHOOTING);
            shooter.getCooldowns().addCooldown(stack, 24);
            stack.hurtAndBreak(1, shooter, hand);
        }
        return InteractionResult.SUCCESS;
    }
}
