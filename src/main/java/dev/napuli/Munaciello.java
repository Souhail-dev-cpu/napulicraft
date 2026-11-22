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
 * Follow his trail, use a Cornicello to bargain, or recover the item from where he vanishes.
 * Kill too many and his big brother comes for you.
 */
public class Munaciello extends Monster {
	private Player target;
	private int fleeTicks = -1; // >=0 once he stole something
	private int life;
	private int stalk;
	private java.util.UUID owner;
	private static final int FLEE_LIMIT = 20 * 28;

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

	private static boolean protectedByCharm(Player player) {
		return player.getMainHandItem().is(Napuli.CORNICELLO) || player.getOffhandItem().is(Napuli.CORNICELLO);
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("napuli_flee_ticks", fleeTicks);
		if (owner != null) out.store("napuli_loot_owner", net.minecraft.core.UUIDUtil.CODEC, owner);
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
		super.readAdditionalSaveData(in);
		fleeTicks = in.getIntOr("napuli_flee_ticks", getMainHandItem().isEmpty() ? -1 : 0);
		owner = in.read("napuli_loot_owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected net.minecraft.world.InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!hasLoot() || !protectedByCharm(player) || (owner != null && !owner.equals(player.getUUID())))
			return super.mobInteract(player, hand);
		if (level() instanceof ServerLevel level) {
			ItemStack recovered = getMainHandItem();
			Component recoveredName = recovered.getHoverName();
			setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.addItem(recovered);
			if (!recovered.isEmpty()) spawnAtLocation(level, recovered);
			player.sendSystemMessage(Component.translatable("napuli.horror.bargain", recoveredName));
			vanish(level, false);
		}
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) return;
		life++;
		if (fleeTicks >= 0) {
			fleeTicks++;
			if (target == null && owner != null) {
				var player = level.getServer().getPlayerList().getPlayer(owner);
				if (player != null && player.level() == level) target = player;
			}
			if (fleeTicks % 7 == 0)
				level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.2, getZ(), 2, 0.13, 0.16, 0.13, 0.015);
			if (this.getNavigation().isDone() && target != null && target.level() == level) {
				Vec3 away = DefaultRandomPos.getPosAway(this, 20, 7, target.position());
				if (away != null) this.getNavigation().moveTo(away.x, away.y, away.z, 1.15);
			}
			if (fleeTicks >= FLEE_LIMIT) vanish(level, true);
			return;
		}
		if (target == null || !target.isAlive() || target.isSpectator() || target.level() != level || target.distanceToSqr(this) > 48 * 48) {
			target = level.getNearestPlayer(this, 40);
			stalk = 0;
			if (target == null && life > 1200) { vanish(level, false); return; }
		}
		if (target == null) return;
		if (protectedByCharm(target)) {
			getNavigation().stop();
			stalk = 0;
			return;
		}
		if (distanceToSqr(target) < 12 * 12 && stalk == 0) {
			target.sendOverlayMessage(Component.translatable("napuli.horror.stalk"));
			level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.6f, 0.8f);
		}
		if (distanceToSqr(target) < 12 * 12) stalk++;
		else stalk = 0;
		if (tickCount % 10 == 0) getNavigation().moveTo(target, stalk < 24 ? 0.82 : 1.0);
		if (stalk >= 24 && distanceToSqr(target) < 4 && hasLineOfSight(target)) steal(level, target);
	}

	private void steal(ServerLevel level, Player p) {
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) if (!p.getInventory().getItem(i).isEmpty()) slots.add(i);
		if (slots.isEmpty()) return;
		int slot = slots.get(this.random.nextInt(slots.size()));
		ItemStack stolen = p.getInventory().removeItem(slot, 1);
		if (stolen.isEmpty()) return;
		owner = p.getUUID();
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
		if (withLoot && !getMainHandItem().isEmpty()) {
			var fallen = spawnAtLocation(level, getMainHandItem().copy());
			if (fallen != null) fallen.setGlowingTag(true);
			if (target != null) target.sendSystemMessage(Component.translatable("napuli.horror.dropped", getMainHandItem().getHoverName()));
		}
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
