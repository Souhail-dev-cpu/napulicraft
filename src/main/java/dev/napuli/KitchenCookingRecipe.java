package dev.napuli;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

final class KitchenCookingRecipe extends AbstractCookingRecipe {
    private final NapoliKitchen.Kind station;

    KitchenCookingRecipe(NapoliKitchen.Kind station, CommonInfo info, CookingBookInfo book, Ingredient ingredient,
                         ItemStackTemplate output, float experience, int time) {
        super(info, book, ingredient, output, experience, time);
        this.station = station;
    }

    @Override public RecipeType<KitchenCookingRecipe> getType() { return NapoliKitchen.cookingType(station); }

    @Override public RecipeSerializer<KitchenCookingRecipe> getSerializer() {
        return NapoliKitchen.cookingSerializer(station);
    }

    @Override public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
        return net.minecraft.world.item.crafting.RecipeBookCategories.FURNACE_FOOD;
    }

    @Override public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        var standard = (net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay) super.display().getFirst();
        var fuel = new net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(
                station == NapoliKitchen.Kind.OVEN ? net.minecraft.world.item.Items.OAK_LOG : NapoliKitchen.item("olio"));
        return java.util.List.of(new net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay(
                standard.ingredient(), fuel, standard.result(), standard.craftingStation(),
                standard.duration(), standard.experience()));
    }

    @Override protected Item furnaceIcon() {
        return NapoliKitchen.item(station == NapoliKitchen.Kind.OVEN ? "forno_legna_portatile" : "banca_frittura");
    }
}
