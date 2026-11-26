package dev.napuli;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;

public final class PickpocketGameTest {
    @GameTest public void onlyFiveIndividualItemsCanBeTakenAcrossAllVisitorsAndReopenings(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h, 3, ps -> {
            place(h, ps); var a = ps.get(0); var b = ps.get(1); var victim = ps.get(2);
            for (int i = 0; i < 36; i++) victim.getInventory().setItem(i, new ItemStack(Items.DIAMOND, 64));
            victim.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.EMERALD, 4));
            victim.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            int before = count(victim);
            h.assertTrue(Pickpocket.inventory(victim).size() == 41, "The whole bag, off hand and armour are visible without duplicating the selected hand");
            h.assertTrue(Pickpocket.open(a, victim), "A nearby player can inspect another player");
            var token = a.getAttached(Pickpocket.OPEN).token();
            h.assertTrue(Pickpocket.open(b, victim), "Two visitors may inspect the same victim concurrently");
            var slots = Pickpocket.inventory(victim); var initial = Pickpocket.daily(victim, slots);
            int locked = -1;
            for (int i = 0; i < slots.size(); i++) if (!slots.get(i).get().isEmpty() && Pickpocket.available(initial, i, slots.get(i).get()) == 0) { locked = i; break; }
            h.assertTrue(locked >= 0 && !Pickpocket.take(a, token, locked), "Unselected items cannot be taken even with a forged slot request");
            for (int i = 0; i < 5; i++) h.assertTrue(takeNext(i % 2 == 0 ? a : b, victim), "Interleaved visitors each transfer one unit from the same shared selection");
            h.assertTrue(count(a) == 3 && count(b) == 2 && count(victim) == before - 5 && !takeNext(a, victim), "The two inventories receive five units in total, rather than five stacks each");
            h.assertTrue(Pickpocket.open(b, victim) && !takeNext(b, victim) && count(b) == 2, "A second thief shares the victim's exhausted allowance");
            h.assertTrue(Pickpocket.open(a, victim) && !takeNext(a, victim), "Closing and reopening cannot reroll or reset the daily five");
            h.assertTrue(!Pickpocket.take(a, token, 0), "A previous screen token cannot be replayed after reopening");
        }); h.succeed();
    }

    @GameTest public void vanillaVillagerInventoryIsInspectedAndDepletedWithoutCreatingItems(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h, 1, ps -> {
            place(h, ps); var thief = ps.getFirst();
            var villager = net.minecraft.world.entity.EntityTypes.VILLAGER.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (villager == null) throw new AssertionError("The villager fixture must be created");
            villager.snapTo(thief.getX() + 2, thief.getY(), thief.getZ(), 0, 0); villager.setNoAi(true); h.getLevel().addFreshEntity(villager);
            try {
                villager.getInventory().setItem(0, new ItemStack(Items.BREAD, 40));
                h.assertTrue(UseEntityCallback.EVENT.invoker().interact(thief, thief.level(), InteractionHand.MAIN_HAND, villager, null) == InteractionResult.PASS
                        && thief.getAttached(Pickpocket.OPEN) == null, "Right-click leaves vanilla NPC trading available");
                thief.setShiftKeyDown(true);
                h.assertTrue(UseEntityCallback.EVENT.invoker().interact(thief, thief.level(), InteractionHand.MAIN_HAND, villager, null).consumesAction()
                        && thief.getAttached(Pickpocket.OPEN) != null, "Shift-right-click opens a vanilla NPC inventory");
                thief.setShiftKeyDown(false);
                for (int i = 0; i < 5; i++) h.assertTrue(takeNext(thief, villager), "The villager's actual carried items can be taken");
                h.assertTrue(villager.getInventory().getItem(0).getCount() == 35 && count(thief) == 5 && !takeNext(thief, villager),
                        "A vanilla villager loses exactly five units from its own inventory");
            } finally { villager.discard(); }
        }); h.succeed();
    }

    @GameTest public void fullInventoryPreservesBothTheItemAndItsAllowanceUntilThereIsRoom(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h, 2, ps -> {
            place(h, ps); var thief = ps.get(0); var victim = ps.get(1);
            for (int i = 0; i < 36; i++) thief.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 64));
            Pickpocket.open(thief, victim); var token = thief.getAttached(Pickpocket.OPEN).token();
            h.assertTrue(!Pickpocket.take(thief, token, 0) && victim.getInventory().getItem(0).getCount() == 64,
                    "A full destination cannot remove or drop the source item");
            h.assertTrue(Pickpocket.daily(victim, Pickpocket.inventory(victim)).stolen() == 0, "Failed delivery does not consume a theft allowance");
            thief.getInventory().setItem(0, ItemStack.EMPTY);
            h.assertTrue(Pickpocket.take(thief, token, 0) && thief.getInventory().getItem(0).getCount() == 1
                    && victim.getInventory().getItem(0).getCount() == 63, "Freeing a slot permits the same request to transfer one real item");
        }); h.succeed();
    }

    @GameTest public void changedStacksComponentsAndForgedTokensCannotBypassTheRandomSelection(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h, 2, ps -> {
            place(h, ps); var thief = ps.get(0); var victim = ps.get(1);
            victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 20));
            Pickpocket.open(thief, victim); var token = thief.getAttached(Pickpocket.OPEN).token();
            h.assertTrue(!Pickpocket.take(thief, UUID.randomUUID(), 0) && !Pickpocket.take(thief, token, 99), "Tokens and slot bounds are checked on the server");
            victim.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));
            h.assertTrue(!Pickpocket.take(thief, token, 0), "A different item moved into a selected slot remains protected");
            var renamed = new ItemStack(Items.DIAMOND, 20); renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Diverso"));
            victim.getInventory().setItem(0, renamed);
            h.assertTrue(!Pickpocket.take(thief, token, 0), "Components are checked as well as item type");
            Pickpocket.open(thief, victim);
            h.assertTrue(!takeNext(thief, victim) && count(thief) == 0, "Reopening a changed inventory keeps the original selection");
        }); h.succeed();
    }

    @GameTest public void distanceDeathSelfInspectionAndSecondaryClickAreHandledOnTheServer(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h, 2, ps -> {
            place(h, ps); var thief = ps.get(0); var victim = ps.get(1);
            victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 10));
            h.assertTrue(!Pickpocket.open(thief, thief), "A player cannot rob their own inventory");
            thief.setShiftKeyDown(false);
            h.assertTrue(UseEntityCallback.EVENT.invoker().interact(thief, thief.level(), InteractionHand.MAIN_HAND, victim, null) == InteractionResult.PASS
                    && thief.getAttached(Pickpocket.OPEN) == null, "Right-click on players does not open their inventory");
            thief.setShiftKeyDown(true);
            h.assertTrue(UseEntityCallback.EVENT.invoker().interact(thief, thief.level(), InteractionHand.MAIN_HAND, victim, null).consumesAction()
                    && thief.getAttached(Pickpocket.OPEN) != null, "Shift-right-click opens the player inventory");
            thief.setShiftKeyDown(false);
            var token = thief.getAttached(Pickpocket.OPEN).token(); var at = victim.position();
            victim.snapTo(at.x + 20, at.y, at.z, 0, 0);
            h.assertTrue(!Pickpocket.take(thief, token, 0), "Moving out of reach invalidates an open view");
            victim.snapTo(at.x, at.y, at.z, 0, 0); victim.setHealth(0);
            h.assertTrue(!Pickpocket.take(thief, token, 0) && count(thief) == 0, "Death cannot leave a usable remote inventory session");
        }); h.succeed();
    }

    @GameTest public void aNewGameDayRestoresFiveButAnOldScreenCannotUseTheNewAllowance(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h, 2, ps -> {
            place(h, ps); var thief = ps.get(0); var victim = ps.get(1); var world = h.getLevel();
            long clock = world.getOverworldClockTime();
            try {
                victim.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 64));
                Pickpocket.open(thief, victim); var token = thief.getAttached(Pickpocket.OPEN).token();
                for (int i = 0; i < 5; i++) h.assertTrue(takeNext(thief, victim), "The first day's five units can be taken");
                world.getServer().getCommands().performPrefixedCommand(world.getServer().createCommandSourceStack(), "time add 24000");
                h.assertTrue(!Pickpocket.take(thief, token, 0), "Yesterday's view cannot spend today's allowance");
                h.assertTrue(Pickpocket.open(thief, victim), "A new view can be opened on the new day");
                for (int i = 0; i < 5; i++) h.assertTrue(takeNext(thief, victim), "The new day permits five more individual units");
                h.assertTrue(count(thief) == 10 && victim.getInventory().getItem(0).getCount() == 54 && !takeNext(thief, victim), "Each day has its own strict limit");
            } finally { world.getServer().getCommands().performPrefixedCommand(world.getServer().createCommandSourceStack(), "time set " + clock); }
        }); h.succeed();
    }

    @GameTest public void savedNpcPocketsAndAllowancesSurviveSerializationWithoutRefilling(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h, 1, ps -> {
            place(h, ps); var thief = ps.getFirst();
            var npc = NapoliResident.spawn(h.getLevel(), NapoliResident.Role.TERESA, thief.blockPosition().east(2), 0); npc.setNoAi(true);
            try {
                int before = count(npc);
                h.assertTrue(UseEntityCallback.EVENT.invoker().interact(thief, thief.level(), InteractionHand.MAIN_HAND, npc, null) == InteractionResult.PASS
                        && thief.getAttached(Pickpocket.OPEN) == null, "Right-click leaves resident dialogue available without opening theft");
                thief.setShiftKeyDown(true);
                h.assertTrue(UseEntityCallback.EVENT.invoker().interact(thief, thief.level(), InteractionHand.MAIN_HAND, npc, null).consumesAction()
                        && thief.getAttached(Pickpocket.OPEN) != null, "Shift-right-click opens resident pockets");
                thief.setShiftKeyDown(false);
                for (int i = 0; i < 5; i++) h.assertTrue(takeNext(thief, npc), "NPC items are transferred from their actual saved pockets");
                var ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
                var daily = Pickpocket.daily(npc, Pickpocket.inventory(npc));
                var saved = Pickpocket.Daily.CODEC.encodeStart(ops, daily).getOrThrow();
                Pickpocket.saveDaily(npc, Pickpocket.Daily.CODEC.parse(ops, saved).getOrThrow());
                var storage = h.getLevel().getServer().overworld(); var pockets = storage.getAttachedOrElse(Pickpocket.POCKETS, Map.of());
                var pocket = pockets.get(npc.getUUID().toString());
                var encoded = Pickpocket.Pocket.CODEC.encodeStart(ops, pocket).getOrThrow();
                var restored = new HashMap<>(pockets); restored.put(npc.getUUID().toString(), Pickpocket.Pocket.CODEC.parse(ops, encoded).getOrThrow());
                storage.setAttached(Pickpocket.POCKETS, Map.copyOf(restored));
                thief.removeAttached(Pickpocket.OPEN); Pickpocket.open(thief, npc);
                h.assertTrue(count(npc) == before - 5 && count(thief) == 5 && !takeNext(thief, npc), "Saving and reopening restores depleted pockets and the consumed allowance");
            } finally { npc.discard(); }
        }); h.succeed();
    }

    @GameTest public void randomSelectionAndNetworkSnapshotKeepTheirLimitsAndItemComponents(GameTestHelper h) {
        var items = new ArrayList<ItemStack>(); for (int i = 0; i < 41; i++) items.add(new ItemStack(Items.DIAMOND, 64));
        var selected = new HashSet<Integer>();
        for (int seed = 0; seed < 40; seed++) {
            var daily = Pickpocket.select(7, items, RandomSource.create(seed));
            h.assertTrue(daily.choices().stream().mapToInt(Pickpocket.Choice::remaining).sum() == 5, "Random selection always permits five individual units");
            daily.choices().forEach(c -> selected.add(c.slot()));
        }
        h.assertTrue(selected.size() > 20, "The random draw considers the whole carried inventory");
        var item = new ItemStack(Items.DIAMOND, 64); item.set(DataComponents.CUSTOM_NAME, Component.literal("Oggetto personale"));
        var view = new PickpocketView(UUID.randomUUID(), Component.literal("Bersaglio"), 2,
                List.of(new PickpocketView.Entry(Component.literal("Tasca"), item, 3)), Component.empty(), true, false, false);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            PickpocketView.CODEC.encode(buf, view); var decoded = PickpocketView.CODEC.decode(buf);
            h.assertTrue(decoded.token().equals(view.token()) && decoded.stolen() == 2 && decoded.entries().getFirst().available() == 3
                    && decoded.entries().getFirst().stack().getCount() == 64 && ItemStack.isSameItemSameComponents(item, decoded.entries().getFirst().stack()),
                    "The client receives the original stack and components alongside its independent theft quota");
        } finally { buf.release(); }
        h.succeed();
    }

    private static void place(GameTestHelper h, List<ServerPlayer> players) {
        var at = h.absolutePos(new BlockPos(1, 40, 1));
        for (int i = 0; i < players.size(); i++) {
            var p = players.get(i); p.getInventory().clearContent();
            for (var slot : EquipmentSlot.values()) if (p.canUseSlot(slot)) p.setItemSlot(slot, ItemStack.EMPTY);
            p.snapTo(at.getX() + .5 + i, at.getY(), at.getZ() + .5, 0, 0);
        }
    }
    private static int count(net.minecraft.world.entity.LivingEntity target) {
        return Pickpocket.inventory(target).stream().mapToInt(s -> s.get().getCount()).sum();
    }
    private static boolean takeNext(ServerPlayer thief, net.minecraft.world.entity.LivingEntity victim) {
        var session = thief.getAttached(Pickpocket.OPEN); var slots = Pickpocket.inventory(victim); var daily = Pickpocket.daily(victim, slots);
        for (int i = 0; i < slots.size(); i++) if (Pickpocket.available(daily, i, slots.get(i).get()) > 0) return Pickpocket.take(thief, session.token(), i);
        return false;
    }
}
