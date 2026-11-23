package dev.napuli;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

final class KitchenFoodItem extends Item {
    KitchenFoodItem(Properties properties) { super(properties); }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity eater) {
        NapoliKitchen.Food food = NapoliKitchen.food(stack);
        ItemStack result = super.finishUsingItem(stack, level, eater);
        if (eater instanceof ServerPlayer player && food != null) KitchenEffects.eaten(player, food);
        return result;
    }
}
