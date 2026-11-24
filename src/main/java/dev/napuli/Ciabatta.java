package dev.napuli;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** 'A ciabatta 'e mammà: thrown by Mammà (or by you). Hurts a bit, pushes a lot, drops back as an item. */
public class Ciabatta extends ThrowableItemProjectile {
	public Ciabatta(EntityType<? extends Ciabatta> type, Level level) { super(type, level); }

	public Ciabatta(Level level, LivingEntity owner, ItemStack stack) { super(Napuli.CIABATTA, owner, level, stack); }

	public Ciabatta(Level level, double x, double y, double z, ItemStack stack) { super(Napuli.CIABATTA, x, y, z, level, stack); }

	@Override
	protected Item getDefaultItem() { return Napuli.CIABATTA_ITEM; }

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity e = hit.getEntity();
		if (this.level() instanceof ServerLevel level) {
			e.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), 3f);
			if (e instanceof LivingEntity le) le.knockback(1.2, this.getX() - e.getX(), this.getZ() - e.getZ(), this.damageSources().thrown(this, this.getOwner()), 3f);
			CityEffects.sound(level,null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 1.4f);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			this.spawnAtLocation(level, new ItemStack(Napuli.CIABATTA_ITEM)); // you can pick it up and throw it back
			this.discard();
		}
	}
}
