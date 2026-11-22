package dev.napuli;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 'O Munaciello: the little monk spirit of Naples' alleys. Sneaks up, steals one item and runs away laughing.
 * Catch him and he drops it back with some gold. Leave him and after 40 s he vanishes with your item.
 * Kill too many and his big brother comes for you.
 */
public class Munaciello extends Monster {
	private Player target;
	private int fleeTicks = -1; // >=0 once he stole something
	private int life;

	public Munaciello(EntityType<? extends Munaciello> type, Level level) {
		super(type, level);
		this.setDropChance(EquipmentSlot.MAINHAND, 2.0f); // always give the stolen item back when killed
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 10)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.FOLLOW_RANGE, 40)
				.add(Attributes.ATTACK_DAMAGE, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 16f));
	}

	public boolean hasLoot() { return fleeTicks >= 0; }

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) return;
		life++;
		if (fleeTicks >= 0) {
			fleeTicks++;
			if (this.getNavigation().isDone() && target != null) {
				Vec3 away = DefaultRandomPos.getPosAway(this, 20, 7, target.position());
				if (away != null) this.getNavigation().moveTo(away.x, away.y, away.z, 1.15);
			}
			if (fleeTicks > 800) vanish(level, true);
			return;
		}
		if (target == null || !target.isAlive() || target.isSpectator() || target.distanceToSqr(this) > 48 * 48) {
			target = level.getNearestPlayer(this, 40);
			if (target == null && life > 1200) { vanish(level, false); return; }
		}
		if (target == null) return;
		if (target.getOffhandItem().is(Napuli.CORNICELLO) || target.getMainHandItem().is(Napuli.CORNICELLO)) {
			// the cornicello protects you: he keeps his distance and just stares
			if (this.distanceToSqr(target) < 36) this.getNavigation().stop();
			return;
		}
		if (this.tickCount % 10 == 0) this.getNavigation().moveTo(target, 1.0);
		if (this.distanceToSqr(target) < 4.0) steal(level, target);
	}

	private void steal(ServerLevel level, Player p) {
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) if (!p.getInventory().getItem(i).isEmpty()) slots.add(i);
		if (slots.isEmpty()) return;
		int slot = slots.get(this.random.nextInt(slots.size()));
		ItemStack stolen = p.getInventory().removeItemNoUpdate(slot);
		this.setItemInHand(InteractionHand.MAIN_HAND, stolen);
		this.fleeTicks = 0;
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), Napuli.RISATA, SoundSource.HOSTILE, 1.2f, 1.4f);
		p.sendSystemMessage(Component.translatable("napuli.munaciello.steal", stolen.getHoverName()));
		if (p instanceof net.minecraft.server.level.ServerPlayer sp) Music.play(sp, Music.Situation.FURTO);
	}

	private void vanish(ServerLevel level, boolean withLoot) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.7, this.getZ(), 25, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), Napuli.RISATA, SoundSource.HOSTILE, 1.0f, 0.9f);
		if (withLoot && target != null) target.sendSystemMessage(Component.translatable("napuli.munaciello.gone", this.getMainHandItem().getHoverName()));
		this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		this.discard();
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (killedByPlayer) {
			this.spawnAtLocation(level, new ItemStack(Items.GOLD_NUGGET, 2 + this.random.nextInt(5)));
			if (source.getEntity() instanceof Player p) Spawner.addRaggia(level, p);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) { return !hasLoot() && distSqr > 128 * 128; }
}
