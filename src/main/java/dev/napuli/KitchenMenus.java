package dev.napuli;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class KitchenMenus {
    public static MenuType<Heat> OVEN, FRYER;
    public static MenuType<Grid> PIZZA_TABLE, COFFEE;

    private KitchenMenus() {}

    static void register() {
        OVEN = Registry.register(BuiltInRegistries.MENU, Napuli.id("wood_oven"),
                new MenuType<>((id, inventory) -> new Heat(id, inventory, NapoliKitchen.Kind.OVEN), FeatureFlags.DEFAULT_FLAGS));
        FRYER = Registry.register(BuiltInRegistries.MENU, Napuli.id("fryer"),
                new MenuType<>((id, inventory) -> new Heat(id, inventory, NapoliKitchen.Kind.FRY), FeatureFlags.DEFAULT_FLAGS));
        PIZZA_TABLE = Registry.register(BuiltInRegistries.MENU, Napuli.id("pizza_table"),
                new MenuType<>((id, inventory) -> new Grid(id, inventory, ContainerLevelAccess.NULL, NapoliKitchen.Kind.TABLE), FeatureFlags.DEFAULT_FLAGS));
        COFFEE = Registry.register(BuiltInRegistries.MENU, Napuli.id("coffee_counter"),
                new MenuType<>((id, inventory) -> new Grid(id, inventory, ContainerLevelAccess.NULL, NapoliKitchen.Kind.COFFEE), FeatureFlags.DEFAULT_FLAGS));
    }

    static boolean fuel(NapoliKitchen.Kind kind, ItemStack stack) {
        return kind == NapoliKitchen.Kind.OVEN ? stack.is(ItemTags.LOGS) : stack.is(NapoliKitchen.item("olio"));
    }

    public static final class Heat extends FurnaceMenu {
        private final NapoliKitchen.Kind kind;

        Heat(int id, Inventory inventory, NapoliKitchen.Kind kind) {
            super(id, inventory);
            this.kind = kind;
        }

        Heat(int id, Inventory inventory, Container container, ContainerData data, NapoliKitchen.Kind kind) {
            super(id, inventory, container, data);
            this.kind = kind;
        }

        public boolean woodOven() { return kind == NapoliKitchen.Kind.OVEN; }
        @Override public MenuType<?> getType() { return woodOven() ? OVEN : FRYER; }
        @Override protected boolean isFuel(ItemStack stack) { return fuel(kind, stack); }
        @Override public PostPlaceAction handlePlacement(boolean max, boolean creative,
                net.minecraft.world.item.crafting.RecipeHolder<?> recipe, net.minecraft.server.level.ServerLevel level, Inventory inventory) {
            if (recipe.value().getType() != NapoliKitchen.cookingType(kind)) return PostPlaceAction.NOTHING;
            return super.handlePlacement(max, creative, recipe, level, inventory);
        }

        @Override protected boolean canSmelt(ItemStack stack) {
            return NapoliKitchen.RECIPES.stream().anyMatch(r -> r.station() == kind && r.fuel()
                    && r.ingredients().size() == 1 && stack.is(r.ingredients().getFirst()));
        }
    }

    public static final class Grid extends CraftingMenu {
        private final ContainerLevelAccess access;
        private final NapoliKitchen.Kind kind;

        Grid(int id, Inventory inventory, ContainerLevelAccess access, NapoliKitchen.Kind kind) {
            super(id, inventory, access);
            this.access = access;
            this.kind = kind;
        }

        public boolean coffee() { return kind == NapoliKitchen.Kind.COFFEE; }

        private boolean serves(net.minecraft.world.item.crafting.RecipeHolder<?> recipe) {
            return NapoliKitchen.RECIPES.stream().anyMatch(r -> r.station() == kind
                    && Napuli.id(r.id()).equals(recipe.id().identifier()));
        }

        @Override public PostPlaceAction handlePlacement(boolean max, boolean creative,
                net.minecraft.world.item.crafting.RecipeHolder<?> recipe, net.minecraft.server.level.ServerLevel level, Inventory inventory) {
            if (coffee() && !serves(recipe)) return PostPlaceAction.NOTHING;
            return super.handlePlacement(max, creative, recipe, level, inventory);
        }

        @Override public void slotsChanged(Container changed) {
            if (coffee() && owner() instanceof net.minecraft.server.level.ServerPlayer player) {
                var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3,
                        getInputGridSlots().stream().map(Slot::getItem).toList());
                var recipe = player.level().getServer().getRecipeManager()
                        .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, player.level());
                if (recipe.isPresent() && !serves(recipe.get())) {
                    getResultSlot().set(ItemStack.EMPTY);
                    broadcastChanges();
                    return;
                }
            }
            super.slotsChanged(changed);
        }
        @Override public MenuType<?> getType() { return coffee() ? COFFEE : PIZZA_TABLE; }
        @Override public boolean stillValid(Player player) {
            return stillValid(access, player, NapoliKitchen.BLOCKS.get(coffee() ? "banca_caffe" : "tavolo_pizzaiolo"));
        }
    }
}
