package dev.napuli;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

public final class KitchenGameTest {
    private static int sequence;

    @GameTest public void civicLifePaysOnceAndCreatesGrowingFamilies(GameTestHelper helper) {
        var level = helper.getLevel();
        var initialCensus = level.removeAttached(NapoliCivicLife.CENSUS);
        var initialOwners = level.removeAttached(NapoliProperties.OWNERS);
        var initialLife = level.removeAttached(CitySimulation.LIFE);
        var initialAccounts = level.removeAttached(CityHousingFinance.ACCOUNTS);
        var initialStocks = level.removeAttached(CityEconomy.STOCKS);
        try {
            verifyCivicLife(helper, level);
        } finally {
            if (initialCensus == null) level.removeAttached(NapoliCivicLife.CENSUS);
            else level.setAttached(NapoliCivicLife.CENSUS, initialCensus);
            if (initialOwners == null) level.removeAttached(NapoliProperties.OWNERS);
            else level.setAttached(NapoliProperties.OWNERS, initialOwners);
            if (initialLife == null) level.removeAttached(CitySimulation.LIFE);
            else level.setAttached(CitySimulation.LIFE, initialLife);
            if (initialAccounts == null) level.removeAttached(CityHousingFinance.ACCOUNTS);
            else level.setAttached(CityHousingFinance.ACCOUNTS, initialAccounts);
            if(initialStocks==null)level.removeAttached(CityEconomy.STOCKS);else level.setAttached(CityEconomy.STOCKS,initialStocks);
        }
    }

    private static void verifyCivicLife(GameTestHelper helper, net.minecraft.server.level.ServerLevel level) {
        NapoliCivicLife.seed(level);
        var seeded=NapoliCivicLife.census(level);
        level.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(seeded.day(),seeded.people().stream().map(p->p.withHousehold("","public_27",p.balance())).toList()));
        long start = NapoliCivicLife.census(level).day();
        var offered = NapoliMergellina.PROPERTIES.getFirst();
        UUID previousOwner = UUID.randomUUID();
        helper.assertTrue(NapoliProperties.claimForResident(level, offered, previousOwner), "Reserve an already sold house");
        NapoliCivicLife.advance(level, start + 1);
        var first = NapoliCivicLife.census(level);
        helper.assertTrue(first.people().getFirst().balance() == 17, "Baker receives daily wages, net of household expenses");
        helper.assertTrue(first.people().getFirst().partner().isEmpty(), "Strangers do not form a couple on their first day");
        NapoliCivicLife.advance(level, start + 1);
        helper.assertTrue(NapoliCivicLife.census(level).equals(first), "A repeated day cannot pay twice");
        NapoliCivicLife.advance(level, start + 24);
        var settled = NapoliCivicLife.census(level);
        var child = settled.people().stream().filter(c -> !c.parents().isEmpty()).findFirst().orElseThrow();
        helper.assertTrue(child.age(settled.day()) < NapoliCivicLife.ADULT_AGE && child.profession() == null, "A child is born after purchase and has no job");
        var house = NapoliMergellina.PROPERTIES.stream().filter(h -> h.id().equals(child.property())).findFirst().orElseThrow();
        helper.assertTrue(NapoliProperties.owner(level, house).toString().equals(child.parents()), "The child lives in the house bought by the parent");
        helper.assertTrue(NapoliProperties.owner(level, offered).equals(previousOwner), "NPCs cannot buy a previously owned house");
        var encoded = NapoliCivicLife.Census.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, settled).getOrThrow();
        helper.assertTrue(NapoliCivicLife.Census.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow().equals(settled),
                "Census preserves family, home and money across serialization");
        long grownDay=child.birth()+(long)NapoliCivicLife.ADULT_AGE*CityLifeConfig.settings.daysPerYear+1;
        while(NapoliCivicLife.census(level).day()<grownDay)NapoliCivicLife.advance(level,grownDay);
        var grown = NapoliCivicLife.profile(level, child.id());
        helper.assertTrue(grown.profession() != null && grown.balance() > 0, "The same child grows up and earns a wage");
        helper.succeed();
    }

    @GameTest public void footballCalendarOnlySchedulesWeeklyFixtures(GameTestHelper helper) {
        var opening = NapoliBroadcast.match(0);
        helper.assertTrue(opening != null && opening.win(), "Opening fixture can trigger celebration");
        helper.assertTrue(NapoliBroadcast.match(1) == null && NapoliBroadcast.match(6) == null, "No match on intervening days");
        helper.assertTrue(NapoliBroadcast.match(7).equals(NapoliBroadcast.match(7)), "A result is stable after reload");
        helper.assertTrue(NapoliBroadcast.match(21) != null && !NapoliBroadcast.match(21).win(), "A loss does not trigger celebration");
        helper.succeed();
    }

    @GameTest public void allRecipesMatchAndAreObtainable(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        var reachable = new java.util.HashSet<net.minecraft.world.item.Item>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("napuli")) reachable.add(item);
        for (var recipe : NapoliKitchen.RECIPES) {
            var holder = manager.byKey(ResourceKey.create(Registries.RECIPE, Napuli.id(recipe.id()))).orElseThrow();
            if (recipe.fuel()) {
                var input = new SingleRecipeInput(new ItemStack(recipe.ingredients().getFirst()));
                var smelting = manager.getRecipeFor(NapoliKitchen.cookingType(recipe.station()), input, helper.getLevel()).orElseThrow();
                helper.assertTrue(smelting.value().assemble(input).is(recipe.output()), "Furnace output " + recipe.id());
            } else {
                List<ItemStack> slots = new ArrayList<>();
                recipe.ingredients().forEach(item -> slots.add(new ItemStack(item)));
                while (slots.size() < 9) slots.add(ItemStack.EMPTY);
                var input = CraftingInput.of(3, 3, slots);
                var crafting = (CraftingRecipe) holder.value();
                helper.assertTrue(crafting.matches(input, helper.getLevel()), "Vanilla crafting ingredients " + recipe.id());
                var output = crafting.assemble(input);
                helper.assertTrue(output.is(recipe.output()) && output.getCount() == recipe.count(), "Vanilla crafting output " + recipe.id());
            }
        }
        for (int i = 0; i < NapoliKitchen.RECIPES.size(); i++) for (var recipe : NapoliKitchen.RECIPES)
            if (reachable.containsAll(recipe.ingredients())) reachable.add(recipe.output());
        for (var entry : NapoliKitchen.ITEMS.entrySet()) if (!entry.getKey().equals("moneta"))
            helper.assertTrue(reachable.contains(entry.getValue()), "Survival crafting path " + entry.getKey());
        helper.succeed();
    }

    @GameTest(maxTicks = 260) public void actualFurnaceCooksPizzaAndFritters(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, NapoliKitchen.BLOCKS.get("forno_legna_portatile"));
        helper.setBlock(2, 1, 1, NapoliKitchen.BLOCKS.get("banca_frittura"));
        var pizza = (KitchenOvenEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
        var fritters = (KitchenOvenEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 1, 1)));
        pizza.setItem(0, new ItemStack(NapoliKitchen.item("pizza_cruda"))); pizza.setItem(1, new ItemStack(Items.OAK_LOG));
        fritters.setItem(0, new ItemStack(NapoliKitchen.item("chiacchiere_crudo"))); fritters.setItem(1, new ItemStack(NapoliKitchen.item("olio")));
        helper.assertTrue(!pizza.canPlaceItem(1, new ItemStack(Items.COAL)), "Wood oven rejects coal");
        helper.assertTrue(!fritters.canPlaceItem(1, new ItemStack(Items.OAK_LOG)), "Fryer rejects logs");
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new SingleRecipeInput(new ItemStack(NapoliKitchen.item("pizza_cruda"))), helper.getLevel()).isEmpty(),
                "Vanilla furnace cannot bake pizza");
        helper.runAtTickTime(230, () -> {
            helper.assertTrue(pizza.getItem(2).is(Napuli.PIZZA) && pizza.getItem(0).isEmpty(), "Pizza cooked in real furnace");
            helper.assertTrue(fritters.getItem(2).is(NapoliKitchen.item("chiacchiere")) && fritters.getItem(0).isEmpty(), "Fried food cooked in real furnace");
            helper.succeed();
        });
    }

    @GameTest public void stationsAreAtomicAndReturnContainers(GameTestHelper helper) {
        withPlayers(helper, 1, players -> {
            var player = players.getFirst();
            for (var recipe : NapoliKitchen.RECIPES) {
                player.getInventory().clearContent();
                for (var ingredient : recipe.ingredients()) player.getInventory().add(new ItemStack(ingredient));
                if (recipe.fuel()) player.getInventory().add(new ItemStack(recipe.station() == NapoliKitchen.Kind.OVEN
                        ? Items.OAK_LOG : NapoliKitchen.item("olio")));
                helper.assertTrue(KitchenStationBlock.prepare(player, recipe), "Station recipe " + recipe.id());
                helper.assertTrue(player.getInventory().countItem(recipe.output()) == recipe.count(), "Exact output " + recipe.id());
                int buckets = (int) recipe.ingredients().stream().filter(i -> i == Items.WATER_BUCKET || i == Items.MILK_BUCKET).count();
                helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == buckets, "Buckets returned " + recipe.id());
            }
            var recipe = NapoliKitchen.RECIPES.stream().filter(r -> r.id().equals("pizza_cottura")).findFirst().orElseThrow();
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(NapoliKitchen.item("pizza_cruda"), 2));
            helper.assertTrue(!KitchenStationBlock.prepare(player, recipe), "Missing fuel rejected");
            helper.assertTrue(player.getInventory().getItem(0).getCount() == 2, "Missing fuel consumes nothing");
            for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            player.getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 2));
            helper.assertTrue(!KitchenStationBlock.prepare(player, recipe), "Full inventory rejected");
            helper.assertTrue(player.getInventory().getItem(0).getCount() == 2 && player.getInventory().getItem(1).getCount() == 2, "Full inventory consumes nothing");
        }); helper.succeed();
    }

    @GameTest public void foodsHealCleanseAndApplyBuffs(GameTestHelper helper) {
        withPlayers(helper, 1, players -> {
            var player = players.getFirst();
            player.setHealth(4); eat(player, "pastiera");
            helper.assertTrue(player.getHealth() >= 12 && player.hasEffect(MobEffects.REGENERATION), "Pastiera heals and regenerates");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WEAKNESS, 200));
            eat(player, "baccala_napoletana");
            helper.assertTrue(!player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WEAKNESS), "Baccala removes harmful effects");
            eat(player, "sfogliatella_frolla"); helper.assertTrue(player.getEffect(MobEffects.LUCK).getAmplifier() == 2, "Rare luck is stronger");
            eat(player, "spaghetti_vongole"); helper.assertTrue(player.hasEffect(MobEffects.DOLPHINS_GRACE), "Underwater speed");
            eat(player, "salsiccia_friarielli"); helper.assertTrue(player.hasEffect(MobEffects.STRENGTH), "Robust food increases damage");
            eat(player, "roccoco"); helper.assertTrue(player.hasEffect(MobEffects.RESISTANCE), "Travel resistance");
            player.setAttached(KitchenEffects.HEAT, 90); eat(player, "acqua_cocomero");
            helper.assertTrue(KitchenEffects.heat(player) == 30 && player.hasEffect(MobEffects.FIRE_RESISTANCE), "Watermelon cools heat");
            for (int i = 0; i < 3; i++) eat(player, "limoncello");
            helper.assertTrue(player.hasEffect(MobEffects.NAUSEA), "Too much limoncello causes drunkenness");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MINING_FATIGUE, 200));
            new ItemStack(Napuli.CAFE).finishUsingItem(player.level(), player);
            helper.assertTrue(!player.hasEffect(MobEffects.MINING_FATIGUE) && player.hasEffect(MobEffects.HASTE), "Coffee removes fatigue and speeds mining");
            eat(player, "sartu_riso"); helper.assertTrue(player.getAttachedOrElse(KitchenProgress.PUZZLE_READY, false), "Sartu unlocks actual puzzle");
            for (var food : NapoliKitchen.ITEMS.values()) if (food instanceof KitchenFoodItem) {
                var serving = new ItemStack(food, 2); var result = serving.finishUsingItem(player.level(), player);
                helper.assertTrue(result.getCount() == 1, "Food consumes one portion " + food);
            }
        }); helper.succeed();
    }

    @GameTest public void struffoliHealOnlyNearbyTeammates(GameTestHelper helper) {
        withPlayers(helper, 3, players -> {
            var scoreboard = helper.getLevel().getServer().getScoreboard();
            var team = scoreboard.addPlayerTeam("kitchen" + sequence);
            try {
                scoreboard.addPlayerToTeam(players.get(0).getScoreboardName(), team);
                scoreboard.addPlayerToTeam(players.get(1).getScoreboardName(), team);
                for (var player : players) { player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1))); player.setHealth(4); }
                eat(players.getFirst(), "struffoli");
                helper.assertTrue(players.get(0).getHealth() == 8 && players.get(1).getHealth() == 8, "Team receives group healing");
                helper.assertTrue(players.get(2).getHealth() == 4 && !players.get(2).hasEffect(MobEffects.REGENERATION), "Bystander excluded");
            } finally { scoreboard.removePlayerTeam(team); }
        }); helper.succeed();
    }

    @GameTest public void friedDietAndPizzaReputationArePerPlayer(GameTestHelper helper) {
        withPlayers(helper, 2, players -> {
            var cook = players.getFirst(); var other = players.get(1);
            eat(cook, "fritto_misto"); int first = NapoliStreet.fullness(cook);
            eat(cook, "fritto_misto"); int second = NapoliStreet.fullness(cook) - first;
            helper.assertTrue(second > first && NapoliStreet.fullness(other) == 0, "Fried streak fills the meter faster for its owner");
            new ItemStack(Items.BREAD).finishUsingItem(cook.level(), cook);
            helper.assertTrue(cook.getAttachedOrElse(KitchenEffects.FRIED_STREAK, 0) == 0, "Varied food resets fried streak");
            new ItemStack(Napuli.PIZZA, 5).onCraftedBy(cook, 5);
            helper.assertTrue(KitchenProgress.pizzas(cook) == 5 && KitchenProgress.helpers(cook) == 1 && KitchenProgress.pizzas(other) == 0, "Crafting hook and player isolation");
            new ItemStack(Napuli.PIZZA, 10).onCraftedBy(cook, 10);
            helper.assertTrue(KitchenProgress.helpers(cook) == 2, "Second helper tier");
            new ItemStack(NapoliKitchen.item("pizza_portafoglio"), 15).onCraftedBy(cook, 15);
            helper.assertTrue(KitchenProgress.helpers(cook) == 3, "Third helper tier");
            KitchenProgress.questStarted(cook);
            var helpers = cook.level().getEntitiesOfClass(NapoliResident.class, cook.getBoundingBox().inflate(10), n -> n.role() == NapoliResident.Role.PIZZAIOLO);
            helper.assertTrue(helpers.size() == 3, "Three NPC helpers spawned for quest");
            KitchenProgress.questStarted(cook);
            helper.assertTrue(cook.level().getEntitiesOfClass(NapoliResident.class, cook.getBoundingBox().inflate(10), n -> n.role() == NapoliResident.Role.PIZZAIOLO).size() == 3, "Quest cancel cannot duplicate helpers");
            helpers.forEach(NapoliResident::discard);
        }); helper.succeed();
    }

    @GameTest public void monkHidesLootAndCanBeTamed(GameTestHelper helper) {
        withPlayers(helper, 2, players -> {
            var player = players.getFirst();
            helper.setBlock(1, 1, 1, Blocks.CHEST);
            var chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
            MunacielloFriendship.remember(player, chest.getBlockPos());
            var monk = helper.spawn(Napuli.MUNACIELLO, 2, 1, 1);
            monk.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(MunacielloFriendship.hide(monk, player), "Stolen item deposited in remembered chest");
            helper.assertTrue(chest.countItem(Items.DIAMOND) == 2 && chest.countItem(NapoliKitchen.item("moneta")) >= 1 && monk.getMainHandItem().isEmpty(), "Items preserved and coins left");
            for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
            monk.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
            helper.assertTrue(!MunacielloFriendship.hide(monk, player) && monk.getMainHandItem().is(Items.EMERALD), "Full chest cannot destroy loot");
            monk.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKIE, 3));
            for (int i = 0; i < 3; i++) monk.mobInteract(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(monk.isTamed() && player.getUUID().equals(monk.owner()) && player.getMainHandItem().isEmpty(), "Three treats tame the monk");
            players.get(1).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKIE));
            monk.mobInteract(players.get(1), InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getUUID().equals(monk.owner()) && players.get(1).getMainHandItem().getCount() == 1, "Other players cannot retame a pet");
            var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            monk.save(saved);
            var restored = new Munaciello(Napuli.MUNACIELLO, helper.getLevel());
            restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
            helper.assertTrue(restored.isTamed() && player.getUUID().equals(restored.owner()), "Tamed state and owner survive save/load");
            monk.discard();
        }); helper.succeed();
    }

    @GameTest public void fryBagRefillsWithoutDuplicatingItsContents(GameTestHelper helper) {
        withPlayers(helper, 1, players -> {
            var player = players.getFirst();
            var bag = new ItemStack(NapoliKitchen.item("borsa_fritto"));
            player.setItemInHand(InteractionHand.MAIN_HAND, bag);
            var item = (FryBagItem) bag.getItem();
            helper.assertTrue(FryBagItem.servings(bag) == 3, "New bag starts with three portions");
            item.use(player.level(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(FryBagItem.servings(bag) == 2 && bag.getCount() == 1, "Bag dispenses and remains reusable");
            helper.assertTrue(player.getInventory().countItem(NapoliKitchen.item("fritto_misto")) == 1, "Dispenses exactly one food");
            var data = FryBagItem.data(bag);
            data.putInt("remaining", 20); data.putLong("last_tick", player.level().getGameTime() - 20);
            bag.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
            item.inventoryTick(bag, player.level(), player, null);
            helper.assertTrue(FryBagItem.servings(bag) == 3, "Elapsed refill adds one portion");
            item.inventoryTick(bag, player.level(), player, null);
            helper.assertTrue(FryBagItem.servings(bag) == 3, "Same tick cannot refill twice");
            data = FryBagItem.data(bag); data.putInt("servings", 8); data.putInt("remaining", 1);
            data.putLong("last_tick", player.level().getGameTime() - 20);
            bag.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
            item.inventoryTick(bag, player.level(), player, null);
            helper.assertTrue(FryBagItem.servings(bag) == 8, "Bag never exceeds eight portions");
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, helper.getLevel().registryAccess());
            var encoded = ItemStack.CODEC.encodeStart(ops, bag).getOrThrow();
            var restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            helper.assertTrue(FryBagItem.servings(restored) == 8 && FryBagItem.data(restored).getIntOr("remaining", 0) > 0, "Bag portions and refill progress survive save/load");
        }); helper.succeed();
    }

    @GameTest public void stolenItemOwnerStaysSeparateFromPetOwner(GameTestHelper helper) {
        withPlayers(helper, 2, players -> {
            var victim = players.getFirst(); var bystander = players.get(1);
            var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            var original = new Munaciello(Napuli.MUNACIELLO, helper.getLevel());
            original.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            original.save(saved);
            var legacy = saved.buildResult();
            legacy.remove("napuli_flee"); legacy.putInt("napuli_flee_ticks", 12);
            legacy.put("napuli_loot_owner", net.minecraft.core.UUIDUtil.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, victim.getUUID()).getOrThrow());
            var monk = new Munaciello(Napuli.MUNACIELLO, helper.getLevel());
            monk.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), legacy));
            helper.assertTrue(monk.hasLoot() && !monk.isTamed() && monk.owner() == null, "Legacy theft saves never become tamed pets");
            bystander.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Napuli.CORNICELLO));
            monk.mobInteract(bystander, InteractionHand.MAIN_HAND);
            helper.assertTrue(!monk.isRemoved() && monk.getMainHandItem().is(Items.DIAMOND), "Bystanders cannot claim another player's stolen item");
            victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Napuli.CORNICELLO));
            monk.mobInteract(victim, InteractionHand.MAIN_HAND);
            helper.assertTrue(monk.isRemoved() && victim.getInventory().countItem(Items.DIAMOND) == 1,
                    "Cornicello returns the item to its original owner");

            var bribed = new Munaciello(Napuli.MUNACIELLO, helper.getLevel());
            bribed.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), legacy));
            bystander.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKIE, 3));
            for (int i = 0; i < 3; i++) bribed.mobInteract(bystander, InteractionHand.MAIN_HAND);
            helper.assertTrue(bribed.isTamed() && bystander.getUUID().equals(bribed.owner()), "Treats establish the pet owner independently");
            helper.assertTrue(victim.getInventory().countItem(Items.DIAMOND) == 2 && bystander.getInventory().countItem(Items.DIAMOND) == 0,
                    "Taming preserves the original theft victim's property");
            helper.assertTrue(!bribed.hasLoot() && bribed.getMainHandItem().isEmpty(), "Returning stolen loot cannot leave duplicate loot on a pet");
        }); helper.succeed();
    }

    static void eat(ServerPlayer player, String id) { new ItemStack(NapoliKitchen.item(id)).finishUsingItem(player.level(), player); }
    private static void withPlayers(GameTestHelper helper, int count, java.util.function.Consumer<List<ServerPlayer>> body) {
        List<ServerPlayer> players = new ArrayList<>(); List<EmbeddedChannel> channels = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                var level = helper.getLevel(); var server = level.getServer();
                var profile = new GameProfile(UUID.randomUUID(), "KitchenTest" + sequence++);
                var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
                var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection));
                server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1))); players.add(player);
            }
            body.accept(players);
        } finally {
            for (var player : players) helper.getLevel().getServer().getPlayerList().remove(player);
            for (var channel : channels) channel.finishAndReleaseAll();
        }
    }
}
