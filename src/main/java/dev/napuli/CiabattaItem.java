package dev.napuli;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Throw Mammà's slipper yourself (like a snowball, but it hurts). */
public class CiabattaItem extends Item {
	public CiabattaItem(Properties props) { super(props); }

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_THROW, SoundSource.PLAYERS, 0.8f, 1.1f);
		if (level instanceof ServerLevel server)
			Projectile.spawnProjectileFromRotation(Ciabatta::new, server, stack, player, 0f, 1.4f, 1f);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
