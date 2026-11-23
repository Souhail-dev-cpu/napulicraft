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
 * Follow his trail to your chest, use a Cornicello to bargain, or recover the item when he vanishes.
 * Kill too many and his big brother comes for you.
 */
public class Munaciello extends Monster {
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> TAMED =
			net.minecraft.network.syncher.SynchedEntityData.defineId(Munaciello.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
	private Player target;
	private int fleeTicks = -1; // >=0 once he stole something
	private int life;
	// A pet's owner and the victim whose item it carries must remain separate identities.
	private java.util.UUID owner, feeder, lootOwner;
	private int treats;
	private int stalk;
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
	public boolean isTamed() { return entityData.get(TAMED); }
	public java.util.UUID owner() { return owner; }

	@Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder); builder.define(TAMED, false);
	}
	@Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.storeNullable("napuli_owner", net.minecraft.core.UUIDUtil.CODEC, owner);
		output.storeNullable("napuli_feeder", net.minecraft.core.UUIDUtil.CODEC, feeder);
		output.storeNullable("napuli_loot_owner", net.minecraft.core.UUIDUtil.CODEC, lootOwner);
		output.putInt("napuli_treats", treats); output.putInt("napuli_flee", fleeTicks);
	}
	@Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		owner = input.read("napuli_owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		feeder = input.read("napuli_feeder", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		lootOwner = input.read("napuli_loot_owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		treats = Math.clamp(input.getIntOr("napuli_treats", 0), 0, 3);
		fleeTicks = input.getIntOr("napuli_flee", input.getIntOr("napuli_flee_ticks", getMainHandItem().isEmpty() ? -1 : 0));
		entityData.set(TAMED, owner != null);
		if (owner != null) setPersistenceRequired();
	}

	@Override protected net.minecraft.world.InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isTamed() && hasLoot() && protectedByCharm(player) && (lootOwner == null || lootOwner.equals(player.getUUID()))) {
			if (level() instanceof ServerLevel level) {
				ItemStack recovered = getMainHandItem();
				Component recoveredName = recovered.getHoverName();
				setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				player.addItem(recovered);
				if (!recovered.isEmpty()) {
					var fallen = spawnAtLocation(level, recovered);
					if (fallen != null) fallen.setGlowingTag(true);
				}
				NapoliNotice.show(player, Component.translatable("napuli.horror.bargain", recoveredName));
				vanish(level, false);
			}
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		ItemStack sweet = player.getItemInHand(hand);
		if (!NapoliKitchen.sweet(sweet) || owner != null && !owner.equals(player.getUUID())) return net.minecraft.world.InteractionResult.PASS;
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			if (!player.getAbilities().instabuild) sweet.shrink(1);
			if (isTamed()) { heal(4); return net.minecraft.world.InteractionResult.SUCCESS; }
			if (!player.getUUID().equals(feeder)) { feeder = player.getUUID(); treats = 0; }
			treats++;
			serverPlayer.level().sendParticles(ParticleTypes.HEART, getX(), getY() + 1.2, getZ(), 5, .3, .2, .3, .02);
			NapoliNotice.show(serverPlayer, Component.translatable("napuli.munaciello.sweet", treats));
			if (treats >= 3) {
				owner = player.getUUID(); entityData.set(TAMED, true); setPersistenceRequired();
				if (hasLoot()) returnLoot(serverPlayer.level());
				fleeTicks = -1; target = null; getNavigation().stop();
				NapoliNotice.show(serverPlayer, Component.translatable("napuli.munaciello.tamed"));
			}
		}
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	private static boolean protectedByCharm(Player player) {
		return player.getMainHandItem().is(Napuli.CORNICELLO) || player.getOffhandItem().is(Napuli.CORNICELLO);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level) || isNoAi()) return;
		life++;
		if (isTamed()) {
			Player friend = level.getPlayerByUUID(owner);
			if (friend != null && friend.isAlive() && tickCount % 20 == 0 && distanceToSqr(friend) > 36) getNavigation().moveTo(friend, 1.1);
			return;
		}
		if (fleeTicks >= 0) {
			fleeTicks++;
			var original = lootOwner == null ? null : level.getServer().getPlayerList().getPlayer(lootOwner);
			if ((target == null || !target.isAlive() || target.level() != level) && original != null && original.level() == level) target = original;
			if (fleeTicks % 7 == 0)
				level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.2, getZ(), 2, 0.13, 0.16, 0.13, 0.015);
			var chest = original == null ? null : MunacielloFriendship.chest(this, original);
			if (chest != null) {
				var pos = chest.getBlockPos();
				if (pos.distToCenterSqr(position()) < 9 && MunacielloFriendship.hide(this, original)) { vanish(level, false); return; }
				if (tickCount % 20 == 0) getNavigation().moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 1.15);
			}
			if (chest == null && this.getNavigation().isDone() && target != null && target.level() == level) {
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
		if (NapoliKitchen.sweet(target.getMainHandItem()) || NapoliKitchen.sweet(target.getOffhandItem())) {
			getNavigation().stop(); getLookControl().setLookAt(target, 30, 30); stalk = 0; return;
		}
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
		lootOwner = p.getUUID();
		this.setItemInHand(InteractionHand.MAIN_HAND, stolen);
		this.fleeTicks = 0;
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), Napuli.RISATA, SoundSource.HOSTILE, 1.2f, 1.4f);
		NapoliNotice.show(p, Component.translatable("napuli.munaciello.steal", stolen.getHoverName()));
		if (p instanceof net.minecraft.server.level.ServerPlayer sp) Music.play(sp, Music.Situation.FURTO);
	}

	private void vanish(ServerLevel level, boolean withLoot) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.7, this.getZ(), 25, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), Napuli.RISATA, SoundSource.HOSTILE, 1.0f, 0.9f);
		if (withLoot) returnLoot(level);
		this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		this.discard();
	}

	private void returnLoot(ServerLevel level) {
		if (getMainHandItem().isEmpty()) return;
		var original = lootOwner == null ? null : level.getServer().getPlayerList().getPlayer(lootOwner);
		if (original != null && MunacielloFriendship.hide(this, original)) return;
		ItemStack loot = getMainHandItem().copy();
		if (original != null) {
			NapoliKitchen.give(original, loot);
			NapoliNotice.show(original, Component.translatable("napuli.munaciello.returned"));
		} else {
			var fallen = spawnAtLocation(level, loot);
			if (fallen != null) fallen.setGlowingTag(true);
		}
		setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (killedByPlayer) {
			this.spawnAtLocation(level, new ItemStack(Items.GOLD_NUGGET, 2 + this.random.nextInt(5)));
			if (!isTamed() && source.getEntity() instanceof Player p) Spawner.addRaggia(level, p);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) { return !isTamed() && !hasLoot() && distSqr > 128 * 128; }
	@Override public void checkDespawn() {
		if (isTamed()) return;
		if (hasLoot() && level() instanceof ServerLevel level && level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			returnLoot(level); discard(); return;
		}
		super.checkDespawn();
	}
}
