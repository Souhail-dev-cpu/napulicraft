package dev.napuli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** One packaged catalogue drives items, vanilla recipes and the kitchen station menus. */
public final class NapoliKitchen {
    public enum Kind { TABLE, OVEN, FRY, COFFEE, PUZZLE }
    public record Food(String id, boolean sweet, boolean fried, String effect) {}
    public record Recipe(String id, Item output, int count, List<Item> ingredients, Kind station, boolean fuel) {}
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    public static final Map<String, KitchenStationBlock> BLOCKS = new LinkedHashMap<>();
    public static final List<Recipe> RECIPES = new ArrayList<>();
    private static final Map<Item, Food> FOODS = new LinkedHashMap<>();
    private NapoliKitchen() {}

    static void register() {
        try (var source = new InputStreamReader(NapoliKitchen.class.getResourceAsStream("/assets/napuli/kitchen.json"), StandardCharsets.UTF_8)) {
            JsonObject catalogue = JsonParser.parseReader(source).getAsJsonObject();
            for (var value : catalogue.getAsJsonArray("ingredients")) {
                JsonObject spec = value.getAsJsonObject();
                String id = spec.get("id").getAsString();
                var properties = properties(id);
                if (text(spec, "container") != null) properties.craftRemainder(item("minecraft:" + text(spec, "container")));
                properties.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable(id.endsWith("_crudo") || id.equals("pizza_cruda")
                        ? "napuli.kitchen.raw" : "napuli.kitchen.hint"))));
                add(id, new Item(properties));
            }
            for (var value : catalogue.getAsJsonArray("foods")) {
                JsonObject spec = value.getAsJsonObject();
                String id = spec.get("id").getAsString(), container = text(spec, "container");
                var consumable = "glass_bottle".equals(container) ? Consumables.defaultDrink() : Consumables.defaultFood();
                var properties = properties(id).stacksTo(container == null ? 64 : 16)
                        .food(new FoodProperties.Builder().nutrition(spec.get("nutrition").getAsInt())
                                .saturationModifier(spec.get("saturation").getAsFloat()).alwaysEdible().build(),
                                consumable.consumeSeconds(spec.get("seconds").getAsFloat()).build())
                        .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("napuli.food." + id))));
                if (container != null) properties.usingConvertsTo(item("minecraft:" + container));
                if (id.equals("pizza_portafoglio") || id.equals("chiacchiere"))
                    properties.component(DataComponents.USE_EFFECTS, new net.minecraft.world.item.component.UseEffects(true, true, 1f));
                if (spec.get("rare").getAsBoolean()) properties.rarity(Rarity.RARE);
                Item food = add(id, new KitchenFoodItem(properties));
                FOODS.put(food, new Food(id, spec.get("sweet").getAsBoolean(), spec.get("fried").getAsBoolean(), spec.get("effect").getAsString()));
            }
            add("borsa_fritto", new FryBagItem(properties("borsa_fritto").stacksTo(1).component(DataComponents.LORE,
                    new ItemLore(List.of(Component.translatable("napuli.kitchen.bag", FryBagItem.INITIAL))))));
            add("moneta", new Item(properties("moneta")) {
                @Override public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level level,
                        net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        if (NapoliStreet.cash(serverPlayer) >= 99999) return net.minecraft.world.InteractionResult.FAIL;
                        player.getItemInHand(hand).shrink(1);
                        NapoliStreet.credit(serverPlayer, 1);
                        serverPlayer.sendOverlayMessage(Component.translatable("napuli.kitchen.coin"));
                    }
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
            });
            for (var value : catalogue.getAsJsonArray("blocks")) {
                JsonObject spec = value.getAsJsonObject();
                String id = spec.get("id").getAsString();
                ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Napuli.id(id));
                KitchenStationBlock block = new KitchenStationBlock(Kind.valueOf(spec.get("kind").getAsString()),
                        BlockBehaviour.Properties.of().setId(key).strength(2.5f).sound(SoundType.WOOD));
                Registry.register(BuiltInRegistries.BLOCK, key, block);
                BLOCKS.put(id, block);
                add(id, new BlockItem(block, properties(id).useBlockDescriptionPrefix()));
            }
            for (var value : catalogue.getAsJsonArray("recipes")) {
                JsonObject spec = value.getAsJsonObject();
                List<Item> ingredients = new ArrayList<>();
                for (var ingredient : spec.getAsJsonArray("ingredients")) ingredients.add(item(ingredient.getAsString()));
                RECIPES.add(new Recipe(spec.get("id").getAsString(), item(spec.get("output").getAsString()), spec.get("count").getAsInt(),
                        List.copyOf(ingredients), Kind.valueOf(spec.get("station").getAsString()), spec.get("fuel").getAsBoolean()));
            }
        } catch (Exception failure) { throw new IllegalStateException("Invalid Napulicraft kitchen catalogue", failure); }
        KitchenProgress.register();
        MunacielloFriendship.register();
        ServerTickEvents.END_SERVER_TICK.register(KitchenEffects::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> KitchenStationBlock.commands(dispatcher));
    }

    private static String text(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null; }
    private static Item.Properties properties(String id) { return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Napuli.id(id))); }
    private static Item add(String id, Item item) { Registry.register(BuiltInRegistries.ITEM, Napuli.id(id), item); ITEMS.put(id, item); return item; }
    public static Item item(String id) {
        Item value = BuiltInRegistries.ITEM.getValue(Identifier.parse(id.contains(":") ? id : "napuli:" + id));
        if (value == null || value == Items.AIR) throw new IllegalArgumentException("Unknown kitchen ingredient: " + id);
        return value;
    }
    public static Food food(ItemStack stack) { return FOODS.get(stack.getItem()); }
    public static boolean sweet(ItemStack stack) { Food food = food(stack); return food != null && food.sweet() || stack.is(Items.COOKIE) || stack.is(Items.PUMPKIN_PIE); }
    public static boolean pizza(ItemStack stack) { return stack.is(Napuli.PIZZA) || stack.is(ITEMS.get("pizza_portafoglio")); }
    public static void give(net.minecraft.server.level.ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.spawnAtLocation(player.level(), stack);
    }
}
