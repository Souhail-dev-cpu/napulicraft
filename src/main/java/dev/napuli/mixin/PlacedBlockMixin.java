package dev.napuli.mixin;

import dev.napuli.CityProperty;
import dev.napuli.NapoliMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class PlacedBlockMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void napuli$recordConstruction(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && context.getPlayer() instanceof ServerPlayer
                && context.getLevel() instanceof ServerLevel level && level.dimension() == NapoliMap.LEVEL)
            CityProperty.placed(level, context.getClickedPos());
    }
}
