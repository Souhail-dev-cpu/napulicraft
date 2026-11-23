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
 * 'O Munaciello Gigante watches until held in the player's gaze, then winds up and gives chase.
 * Breaking sight or reaching sustained block light ends the chase. Gone at sunrise.
 */
public class MunacielloGigante extends Monster {
	private Player prey;
	private boolean chasing;
	private int idleTicks;
	private int gazeTicks;
	private int windup;
	private int chaseTicks;
	private int hiddenTicks;
	private int brightTicks;

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

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putBoolean("napuli_chase", chasing);
		out.putInt("napuli_chase_ticks", chaseTicks);
		out.putInt("napuli_windup", windup);
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
		super.readAdditionalSaveData(in);
		chasing = in.getBooleanOr("napuli_chase", false);
		chaseTicks = in.getIntOr("napuli_chase_ticks", 0);
		windup = in.getIntOr("napuli_windup", 0);
	}

	public void setPrey(Player p) { this.prey = p; }
	public boolean isChasing() { return chasing; }

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) return;
		if (prey == null || !prey.isAlive() || prey.isSpectator() || prey.level() != level) {
			prey = level.getNearestPlayer(this, 128);
			if (prey == null) { if (tickCount > 300) leave(level); return; }
		}
		// sunrise: he's gone
		if (level.isBrightOutside() && this.tickCount > 200) { leave(level); return; }

		if (!chasing) {
			getNavigation().stop();
			getLookControl().setLookAt(prey, 30f, 30f);
			if (windup > 0) {
				if (--windup == 0) startChase(level);
				return;
			}
			idleTicks++;
			gazeTicks = isLookedAtBy(prey) ? gazeTicks + 1 : 0;
			if (gazeTicks >= 12 || idleTicks > 20 * 40 || distanceToSqr(prey) < 9 * 9) {
				windup = 18;
				prey.sendOverlayMessage(Component.translatable("napuli.horror.giant_windup"));
				level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 0.9f);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 3, getZ(), 30, 0.6, 1.8, 0.6, 0.02);
			}
			return;
		}
		chaseTicks++;
		setTarget(prey);
		boolean visible = hasLineOfSight(prey);
		hiddenTicks = visible ? 0 : hiddenTicks + 1;
		boolean lit = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, prey.blockPosition()) >= 12;
		brightTicks = lit ? brightTicks + 1 : 0;
		if (visible && !lit && chaseTicks % 55 == 0 && distanceToSqr(prey) < 28 * 28)
			prey.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 34, 0, false, false));
		if (chaseTicks % 13 == 0)
			level.sendParticles(ParticleTypes.ASH, getX(), getY() + 0.3, getZ(), 4, 0.65, 0.2, 0.65, 0.01);
		if (chaseTicks >= 20 * 32 || hiddenTicks >= 100 || brightTicks >= 60 || distanceToSqr(prey) > 110 * 110) {
			prey.sendOverlayMessage(Component.translatable("napuli.horror.giant_lost"));
			leave(level);
		}
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
		Songs.urlo(level, prey.position());
		prey.sendSystemMessage(Component.translatable("napuli.gigante.chase"));
		if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, prey.blockPosition()) < 12)
			prey.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, false, false));
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
