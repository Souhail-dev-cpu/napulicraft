package dev.napuli;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 'O Munaciello Gigante: the horror one. Stands far away and stares (like the Man From The Fog).
 * The moment you look at him: the scream plays LOUD, darkness falls and he runs at you.
 * Gone at sunrise. Drops the Cornicello.
 */
public class MunacielloGigante extends Monster {
	private Player prey;
	private boolean chasing;
	private int stareTicks;

	public MunacielloGigante(EntityType<? extends MunacielloGigante> type, Level level) {
		super(type, level);
		this.xpReward = 60;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 250)
				.add(Attributes.MOVEMENT_SPEED, 0.36)
				.add(Attributes.ATTACK_DAMAGE, 14)
				.add(Attributes.ARMOR, 8)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 96)
				.add(Attributes.SCALE, 3.5)
				.add(Attributes.STEP_HEIGHT, 2.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
	}

	public void setPrey(Player p) { this.prey = p; }
	public boolean isChasing() { return chasing; }

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) return;
		if (prey == null || !prey.isAlive() || prey.isSpectator()) {
			prey = level.getNearestPlayer(this, 128);
			if (prey == null) return;
		}
		// sunrise: he's gone
		if (level.isBrightOutside() && this.tickCount > 200) { leave(level); return; }

		if (!chasing) {
			this.getNavigation().stop();
			this.getLookControl().setLookAt(prey, 30f, 30f);
			stareTicks++;
			if (isLookedAtBy(prey) || stareTicks > 20 * 45 || this.distanceToSqr(prey) < 14 * 14) startChase(level);
			return;
		}
		this.setTarget(prey);
		if (this.distanceToSqr(prey) < 40 * 40)
			prey.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
		if (this.distanceToSqr(prey) > 110 * 110) leave(level);
	}

	/** Same test the Enderman uses: the player's view points right at him, and nothing is in the way. */
	private boolean isLookedAtBy(Player p) {
		Vec3 view = p.getViewVector(1f).normalize();
		Vec3 to = new Vec3(this.getX() - p.getX(), this.getEyeY() - 3 - p.getEyeY(), this.getZ() - p.getZ());
		double dist = to.length();
		return view.dot(to.normalize()) > 1.0 - 0.06 / Math.max(1, dist / 20) && p.hasLineOfSight(this);
	}

	private void startChase(ServerLevel level) {
		chasing = true;
		// several copies at once: Minecraft caps a single sound's loudness, stacking makes the scream really LOUD
		for (int i = 0; i < 3; i++)
			Songs.urlo(level, prey.position());
		prey.sendSystemMessage(Component.translatable("napuli.gigante.chase"));
		prey.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
	}

	private void leave(ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 3, this.getZ(), 80, 1, 3, 1, 0.02);
		this.discard();
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(Napuli.CORNICELLO));
		this.spawnAtLocation(level, new ItemStack(Items.GOLDEN_APPLE, 2));
		this.spawnAtLocation(level, new ItemStack(Items.GOLD_INGOT, 8 + this.random.nextInt(8)));
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) { return false; }
}
