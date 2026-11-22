package dev.napuli;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Runs inside the opt-in integration world, after 26.3's data-driven item components have been loaded. */
final class TaxPaymentChecks {
    private TaxPaymentChecks() {}

    static void run() {
        for (var item : List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE,
                Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.BOW, Items.CROSSBOW, Items.TRIDENT,
                Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS,
                Items.DIAMOND_BOOTS, Items.ELYTRA, Items.SHIELD, Items.FISHING_ROD, Items.SHEARS,
                Items.FLINT_AND_STEEL, Items.BUCKET, Items.BUNDLE, Items.SHULKER_BOX, Items.CARVED_PUMPKIN,
                Items.COMPASS, Items.RECOVERY_COMPASS, Items.CLOCK, Items.LEAD, Items.MAP, Items.FILLED_MAP,
                Items.ARROW, Items.OAK_BOAT, Items.MINECART, Items.ARMOR_STAND,
                Napuli.KNIFE, Napuli.KNUCKLES, Napuli.BATON, Napuli.PISTOL, Napuli.CIABATTA_ITEM, Napuli.CASCO, Napuli.MOTORINO_ITEM)) {
            ItemStack stack = new ItemStack(item);
            check(TaxPayment.collect(List.of(stack)) == 0 && stack.getCount() == 1, "Equipment taxed: " + item);
        }
        ItemStack customWeapon = new ItemStack(Items.STICK, 64);
        customWeapon.set(DataComponents.WEAPON, new ItemStack(Items.DIAMOND_SWORD).get(DataComponents.WEAPON));
        check(TaxPayment.collect(List.of(customWeapon)) == 0 && customWeapon.getCount() == 64, "Custom weapon taxed");

        ItemStack diamonds = new ItemStack(Items.DIAMOND, 20);
        ItemStack dirt = new ItemStack(Items.DIRT, 64);
        check(TaxPayment.amount(List.of(diamonds, dirt)) == 15, "Wrong payment preview");
        check(TaxPayment.collect(List.of(diamonds, dirt)) == 15, "Payment cap exceeded");
        check(dirt.getCount() == 49 && diamonds.getCount() == 20, "Did not prefer blocks");

        ItemStack stone = new ItemStack(Items.STONE, 5);
        dirt = new ItemStack(Items.DIRT, 4);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 10);
        check(TaxPayment.collect(List.of(stone, dirt, iron)) == 15, "Wrong total across stacks");
        check(stone.isEmpty() && dirt.isEmpty() && iron.getCount() == 4, "Cap applied per stack");

        ItemStack ore = new ItemStack(Items.RAW_IRON, 2);
        ItemStack bread = new ItemStack(Items.BREAD, 3);
        check(TaxPayment.collect(List.of(ore, ItemStack.EMPTY, bread)) == 5, "Small payment failed");
        check(ore.isEmpty() && bread.isEmpty(), "Small payment not collected");
        check(TaxPayment.collect(List.of(ItemStack.EMPTY)) == 0, "Empty inventory charged");
        Napuli.LOGGER.info("[TAXTEST] Payment checks passed: equipment, custom weapons, containers, cap, blocks, ores, food, empty inventory");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
