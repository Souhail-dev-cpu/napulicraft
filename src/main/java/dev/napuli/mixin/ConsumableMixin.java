package dev.napuli.mixin;

import dev.napuli.NapoliStreet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Consumable.class)
abstract class ConsumableMixin {
    @Inject(method = "onConsume", at = @At("HEAD"))
    private void napuli$eat(Level level, LivingEntity eater, ItemStack food, CallbackInfoReturnable<ItemStack> result) {
        if (eater instanceof ServerPlayer player) NapoliStreet.ate(player, food);
    }
}
