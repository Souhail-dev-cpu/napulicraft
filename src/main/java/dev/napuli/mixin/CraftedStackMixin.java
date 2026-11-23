package dev.napuli.mixin;

import dev.napuli.KitchenProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
abstract class CraftedStackMixin {
    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void napuli$craftedPizza(Player player, int amount, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) KitchenProgress.crafted(serverPlayer, (ItemStack) (Object) this, amount);
    }
}
