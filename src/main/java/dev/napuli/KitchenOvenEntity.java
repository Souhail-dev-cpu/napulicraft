package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent station inventory backed by its own recipe family and fuel. */
final class KitchenOvenEntity extends AbstractFurnaceBlockEntity {
    private final NapoliKitchen.Kind kind;

    KitchenOvenEntity(BlockPos pos, BlockState state) {
        super(NapoliKitchen.OVEN_ENTITY, pos, state, NapoliKitchen.cookingType(((KitchenStationBlock) state.getBlock()).kind));
        kind = ((KitchenStationBlock) state.getBlock()).kind;
    }

    @Override protected int getBurnDuration(net.minecraft.server.level.ServerLevel level, net.minecraft.world.item.ItemStack stack) {
        return KitchenMenus.fuel(kind, stack) ? 400 : 0;
    }

    @Override public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) {
        return slot == 1 ? KitchenMenus.fuel(kind, stack) : super.canPlaceItem(slot, stack);
    }

    @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        if (!input.getBooleanOr("napuli_fuel_rules", false)) {
            dataAccess.set(0, 0);
            dataAccess.set(1, 0);
        }
    }

    @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("napuli_fuel_rules", true);
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new KitchenMenus.Heat(id, inventory, this, dataAccess, kind);
    }
}
