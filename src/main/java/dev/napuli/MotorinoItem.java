package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Right-click the ground to park your motorino. */
public class MotorinoItem extends Item {
	public MotorinoItem(Properties props) { super(props); }

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (ctx.getLevel() instanceof ServerLevel level) {
			BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
			Motorino m = Napuli.MOTORINO.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
			if (m == null) return InteractionResult.FAIL;
			float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0;
			m.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
			m.setYBodyRot(yaw);
			m.setYHeadRot(yaw);
			if (!level.noCollision(m) || !level.addFreshEntity(m)) return InteractionResult.FAIL;
			if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
