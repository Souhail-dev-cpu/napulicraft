package dev.napuli;

import static dev.napuli.CityText.t;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

/** Five individual items per victim and day, shared by every visitor and saved with the world. */
final class Pickpocket {
    static final int LIMIT = 5;
    private static final List<EquipmentSlot> GEAR = List.of(EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    record Choice(int slot, ItemStack kind, int remaining) {
        static final Codec<Choice> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(Choice::slot), ItemStack.CODEC.fieldOf("kind").forGetter(Choice::kind),
                Codec.INT.fieldOf("remaining").forGetter(Choice::remaining)).apply(i, Choice::new));
    }
    record Daily(long day, int stolen, List<Choice> choices) {
        static final Codec<Daily> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Daily::day), Codec.INT.fieldOf("stolen").forGetter(Daily::stolen),
                Choice.CODEC.listOf().fieldOf("choices").forGetter(Daily::choices)).apply(i, Daily::new));
    }
    record Pocket(List<ItemStack> items) {
        static final Codec<Pocket> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(Pocket::new, Pocket::items);
    }
    record Session(LivingEntity target, UUID token, long day, boolean reported) {}
    record Slot(Component label, Supplier<ItemStack> read, Consumer<ItemStack> write) {
        ItemStack get() { return read.get(); }
        void set(ItemStack stack) { write.accept(stack); }
    }
    static final AttachmentType<Map<String, Daily>> DAYS = AttachmentRegistry.<Map<String, Daily>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING, Daily.CODEC)).buildAndRegister(Napuli.id("pickpocket_days"));
    static final AttachmentType<Map<String, Pocket>> POCKETS = AttachmentRegistry.<Map<String, Pocket>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING, Pocket.CODEC)).buildAndRegister(Napuli.id("citizen_pockets"));
    static final AttachmentType<Session> OPEN = AttachmentRegistry.create(Napuli.id("pickpocket_open"));

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(PickpocketView.TYPE, PickpocketView.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PickpocketView.Take.TYPE, PickpocketView.Take.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PickpocketView.Take.TYPE, (request, ctx) -> request(ctx.player(), request));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !supported(entity)
                    || player.isSpectator() || !player.isAlive()) return InteractionResult.PASS;
            if (player instanceof ServerPlayer server) open(server, (LivingEntity)entity);
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) {
                var session = player.getAttached(OPEN);
                if (session != null && (!valid(player, session.target) || session.day != day(player))) close(player, session);
            }
        });
    }
    static boolean supported(Entity target) {
        return target instanceof LivingEntity && (target instanceof Player || target instanceof NapoliResident
                || target instanceof net.minecraft.world.entity.npc.Npc);
    }
    private static ServerLevel storage(LivingEntity target) { return ((ServerLevel)target.level()).getServer().overworld(); }
    static long day(LivingEntity target) { return Math.floorDiv(target.level().getOverworldClockTime(), 24000); }
    static boolean valid(ServerPlayer player, LivingEntity target) {
        return supported(target) && player != target && player.isAlive() && !player.isSpectator()
                && !Carabinieri.isJailed(player) && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && player.level() == target.level() && player.distanceToSqr(target) <= 36 && player.hasLineOfSight(target)
                && (target instanceof ServerPlayer p ? p.level().getServer().getPlayerList().getPlayer(p.getUUID()) == p : true)
                && player.containerMenu == player.inventoryMenu;
    }
    static boolean open(ServerPlayer player, LivingEntity target) {
        if (!valid(player, target)) return false;
        var session = new Session(target, UUID.randomUUID(), day(player), false);
        player.setAttached(OPEN, session);
        send(player, session, true, Component.empty());
        return true;
    }
    static List<Slot> inventory(LivingEntity target) {
        var slots = new ArrayList<Slot>();
        if (target instanceof Player p) {
            for (int i = 0; i < p.getInventory().getNonEquipmentItems().size(); i++) {
                final int index = i;
                slots.add(new Slot(t(i < 9 ? "pick.hotbar" : "pick.bag", i < 9 ? i + 1 : i - 8),
                        () -> p.getInventory().getItem(index), item -> p.getInventory().setItem(index, item)));
            }
        } else if (target instanceof NapoliResident npc) {
            pocket(npc);
            for (int i = 0; i < 9; i++) {
                final int index = i;
                slots.add(new Slot(t("pick.pocket", i + 1), () -> pocket(npc).items.get(index), item -> {
                    var items = new ArrayList<>(pocket(npc).items);
                    items.set(index, item); savePocket(npc, new Pocket(List.copyOf(items)));
                }));
            }
        } else if (target instanceof InventoryCarrier carrier) {
            var container = carrier.getInventory();
            for (int i = 0; i < container.getContainerSize(); i++) {
                final int index = i;
                slots.add(new Slot(t("pick.pocket", i + 1), () -> container.getItem(index), item -> container.setItem(index, item)));
            }
        }
        if (!(target instanceof Player)) slots.add(gear(target, EquipmentSlot.MAINHAND));
        for (var equipment : GEAR) slots.add(gear(target, equipment));
        return List.copyOf(slots);
    }
    private static Slot gear(LivingEntity target, EquipmentSlot slot) {
        return new Slot(t("pick.gear." + slot.name().toLowerCase(Locale.ROOT)),
                () -> target.getItemBySlot(slot), item -> target.setItemSlot(slot, item));
    }
    private static Pocket pocket(NapoliResident npc) {
        var existing = storage(npc).getAttachedOrElse(POCKETS, Map.of()).get(npc.getUUID().toString());
        if (existing != null) return existing;
        // Citizens previously had no bag. Supplies are created once, never on daily reset or reopening.
        var items = new ArrayList<ItemStack>(List.of(new ItemStack(Items.BREAD, 2), new ItemStack(Items.WHEAT, 2),
                new ItemStack(Items.STICK, 2), new ItemStack(Items.PAPER), new ItemStack(Items.TORCH, 2),
                new ItemStack(Items.COAL), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        Collections.rotate(items, Math.floorMod(npc.getUUID().hashCode(), items.size()));
        var pocket = new Pocket(List.copyOf(items)); savePocket(npc, pocket); return pocket;
    }
    private static void savePocket(NapoliResident npc, Pocket pocket) {
        var world = storage(npc); var pockets = new HashMap<>(world.getAttachedOrElse(POCKETS, Map.of()));
        pockets.put(npc.getUUID().toString(), pocket); world.setAttached(POCKETS, Map.copyOf(pockets));
    }
    static Daily daily(LivingEntity target, List<Slot> slots) {
        var existing = storage(target).getAttachedOrElse(DAYS, Map.of()).get(target.getUUID().toString());
        long day = day(target);
        if (existing != null && existing.day == day) return existing;
        var daily = select(day, slots.stream().map(Slot::get).toList(), target.getRandom());
        saveDaily(target, daily); return daily;
    }
    static Daily select(long day, List<ItemStack> items, RandomSource random) {
        var active = new ArrayList<Integer>(); var amounts = new LinkedHashMap<Integer, Integer>();
        for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) active.add(i);
        for (int count = 0; count < LIMIT && !active.isEmpty(); count++) {
            int at = random.nextInt(active.size()), slot = active.get(at);
            int amount = amounts.merge(slot, 1, Integer::sum);
            if (amount >= items.get(slot).getCount()) active.remove(at);
        }
        return new Daily(day, 0, amounts.entrySet().stream()
                .map(e -> new Choice(e.getKey(), items.get(e.getKey()).copyWithCount(1), e.getValue())).toList());
    }
    static void saveDaily(LivingEntity target, Daily daily) {
        var world = storage(target); var days = new HashMap<>(world.getAttachedOrElse(DAYS, Map.of()));
        days.put(target.getUUID().toString(), daily); world.setAttached(DAYS, Map.copyOf(days));
    }
    static int available(Daily daily, int index, ItemStack present) {
        if (daily.stolen >= LIMIT || present.isEmpty()) return 0;
        return daily.choices.stream().filter(c -> c.slot == index && ItemStack.isSameItemSameComponents(c.kind, present))
                .mapToInt(c -> Math.min(c.remaining, present.getCount())).findFirst().orElse(0);
    }
    static boolean take(ServerPlayer player, UUID token, int index) {
        var session = player.getAttached(OPEN);
        if (session == null || !session.token.equals(token) || session.day != day(player) || !valid(player, session.target)) return false;
        var slots = inventory(session.target);
        if (index < 0 || index >= slots.size()) return false;
        var slot = slots.get(index); var present = slot.get(); var daily = daily(session.target, slots);
        if (available(daily, index, present) == 0) return false;
        var loot = present.copyWithCount(1);
        var destination = PlayerInventoryPlan.copy(player);
        if (!PlayerInventoryPlan.add(destination, loot)) return false;
        var choices = new ArrayList<Choice>();
        for (var choice : daily.choices) {
            int remaining = choice.remaining - (choice.slot == index ? 1 : 0);
            if (remaining > 0) choices.add(new Choice(choice.slot, choice.kind, remaining));
        }
        var rest = present.copy(); rest.shrink(1);
        slot.set(rest);
        saveDaily(session.target, new Daily(daily.day, daily.stolen + 1, List.copyOf(choices)));
        PlayerInventoryPlan.apply(player, destination);
        if (session.target instanceof ServerPlayer victim) {
            victim.getInventory().setChanged(); victim.inventoryMenu.broadcastChanges(); victim.containerMenu.broadcastChanges();
            victim.sendOverlayMessage(t("pick.victim", loot.getHoverName()));
        }
        if (!session.reported) {
            player.setAttached(OPEN, new Session(session.target, session.token, session.day, true));
            Carabinieri.crime(player, Carabinieri.Crime.THEFT);
            if (session.target instanceof NapoliResident && NapoliCivicLife.profile(player.level(), session.target.getUUID()) != null)
                CitySocial.harm(player.level(), session.target.getUUID(), player.getUUID());
        }
        return true;
    }
    private static void send(ServerPlayer player, Session session, boolean open, Component status) {
        var slots = inventory(session.target); var daily = daily(session.target, slots);
        var entries = new ArrayList<PickpocketView.Entry>();
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i); var stack = slot.get();
            entries.add(new PickpocketView.Entry(slot.label, stack.copy(), available(daily, i, stack)));
        }
        if (ServerPlayNetworking.canSend(player, PickpocketView.TYPE))
            ServerPlayNetworking.send(player, new PickpocketView(session.token, session.target.getName(), daily.stolen, List.copyOf(entries), status, open, false, !(session.target instanceof Player)));
    }
    private static void request(ServerPlayer player, PickpocketView.Take request) {
        var session = player.getAttached(OPEN);
        if (session == null || !session.token.equals(request.token())) return;
        if (request.slot() == -2) { player.removeAttached(OPEN); return; }
        if (!valid(player, session.target) || session.day != day(player)) { close(player, session); return; }
        if (request.slot() == -3 && !(session.target instanceof Player)) {
            close(player, session);
            if (session.target instanceof NapoliResident npc) npc.mobInteract(player, InteractionHand.MAIN_HAND);
            else session.target.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            return;
        }
        if (request.slot() == -1) { send(player, session, false, Component.empty()); return; }
        if (request.slot() < 0 || request.slot() >= PickpocketView.MAX_SLOTS) return;
        boolean success = take(player, request.token(), request.slot());
        send(player, session, false, t(success ? "pick.taken" : "pick.cannot_take"));
    }
    private static void close(ServerPlayer player, Session session) {
        player.removeAttached(OPEN);
        if (ServerPlayNetworking.canSend(player, PickpocketView.TYPE)) ServerPlayNetworking.send(player,
                new PickpocketView(session.token, session.target.getName(), 0, List.of(), t("pick.closed"), false, true, false));
    }
    private Pickpocket() {}
}
