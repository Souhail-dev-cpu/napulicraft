package dev.napuli;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** The levy is at most fifteen individual materials, never equipment or its contents. */
final class TaxPayment {
    static final int MAX_ITEMS = 15;

    private TaxPayment() {}

    static boolean taxable(ItemStack stack) {
        if (stack.isEmpty() || stack.isDamageableItem() || stack.getItem() instanceof CiabattaItem) return false;
        // Several utility items stack (empty buckets, compasses, leads...) despite being equipment.
        if (stack.getItem() instanceof BucketItem || stack.getItem() instanceof BoatItem
                || stack.getItem() instanceof MinecartItem || stack.getItem() instanceof ArmorStandItem
                || stack.is(Items.COMPASS) || stack.is(Items.RECOVERY_COMPASS) || stack.is(Items.CLOCK)
                || stack.is(Items.MAP) || stack.is(Items.FILLED_MAP) || stack.is(Items.LEAD)
                || stack.is(Items.NAME_TAG) || stack.is(net.minecraft.tags.ItemTags.ARROWS)) return false;
        if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.EQUIPPABLE) || stack.has(DataComponents.GLIDER)
                || stack.has(DataComponents.BLOCKS_ATTACKS) || stack.has(DataComponents.KINETIC_WEAPON)
                || stack.has(DataComponents.PIERCING_WEAPON)
                || !stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty()) return false;
        // A container may hide someone's weapons/tools. Do not levy the container or inspect its contents.
        if (stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.CONTAINER_LOOT)
                || stack.has(DataComponents.BUNDLE_CONTENTS)) return false;
        return stack.getItem() instanceof BlockItem || stack.getMaxStackSize() > 1;
    }

    static int amount(List<ItemStack> inventory) {
        int total = 0;
        for (ItemStack stack : inventory)
            if (taxable(stack)) total += Math.min(MAX_ITEMS - total, stack.getCount());
        return total;
    }

    static int collect(List<ItemStack> inventory) {
        int taken = 0;
        // Prefer building blocks, then ores and other consumable materials. The cap applies to the whole levy.
        for (boolean blocks : new boolean[] {true, false}) {
            for (ItemStack stack : inventory) {
                if (taken == MAX_ITEMS) return taken;
                if (!taxable(stack) || (stack.getItem() instanceof BlockItem) != blocks) continue;
                int count = Math.min(MAX_ITEMS - taken, stack.getCount());
                stack.shrink(count);
                taken += count;
            }
        }
        return taken;
    }
}
