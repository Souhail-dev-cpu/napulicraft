package dev.napuli;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class FryBagItem extends Item {
    public static final int INITIAL = 3, CAPACITY = 8, REFILL_TICKS = 1200;
    FryBagItem(Properties properties) { super(properties); }
    static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    public static int servings(ItemStack stack) { return Math.clamp(data(stack).getIntOr("servings", INITIAL), 0, CAPACITY); }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * servings(stack) / CAPACITY); }
    @Override public int getBarColor(ItemStack stack) { return 0xf4b942; }
    @Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof ServerPlayer)) return;
        long now = level.getGameTime();
        CompoundTag data = data(stack);
        if (data.getLong("last_tick").isEmpty()) {
            data.putLong("last_tick", now); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return;
        }
        long last = data.getLongOr("last_tick", now);
        if (last == now || now > last && now - last < 20) return;
        int remaining = Math.clamp(data.getIntOr("remaining", REFILL_TICKS), 1, REFILL_TICKS);
        int count = servings(stack);
        // Only loaded inventory ticks count. Moving between dimensions or logging out grants no instant refill.
        if (count < CAPACITY && (remaining -= 20) <= 0) { count++; remaining = REFILL_TICKS; }
        data.putInt("servings", count); data.putInt("remaining", remaining); data.putLong("last_tick", now);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            int count = servings(stack);
            if (count <= 0) { serverPlayer.sendOverlayMessage(Component.translatable("napuli.kitchen.bag_empty")); return InteractionResult.FAIL; }
            ItemStack serving = new ItemStack(NapoliKitchen.item("fritto_misto"));
            if (!player.getInventory().add(serving)) { serverPlayer.sendOverlayMessage(Component.translatable("napuli.kitchen.full")); return InteractionResult.FAIL; }
            CompoundTag data = data(stack); data.putInt("servings", count - 1);
            data.putLong("last_tick", level.getGameTime());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            player.getCooldowns().addCooldown(stack, 20);
            serverPlayer.sendOverlayMessage(Component.translatable("napuli.kitchen.bag", count - 1));
        }
        return InteractionResult.SUCCESS;
    }
}
