package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

/** A fictional tax visit for each player, every three Minecraft days, including nights skipped by sleeping. */
public final class GiorgiaMeloni {
    static final long INTERVAL = 3L * 24000;
    static final long APPROACH_TIMEOUT = 20L * 120;
    private static final int SPAWN_DISTANCE = 50;
    private static final double TALK_RANGE_SQUARED = 6 * 6;
    static final AttachmentType<Long> NEXT_VISIT = AttachmentRegistry.<Long>builder()
            .persistent(Codec.LONG).copyOnDeath().buildAndRegister(Napuli.id("next_tax_visit"));
    static final AttachmentType<Long> APPROACH_DEADLINE = AttachmentRegistry.<Long>builder()
            .persistent(Codec.LONG).copyOnDeath().buildAndRegister(Napuli.id("tax_approach_deadline"));
    // Live references/tokens are deliberately not saved. An outstanding bill survives via NEXT_VISIT.
    static final AttachmentType<NapoliResident> COLLECTOR = AttachmentRegistry.create(Napuli.id("tax_collector"));
    static final AttachmentType<Session> SESSION = AttachmentRegistry.create(Napuli.id("tax_dialog"));
    enum Payment { ITEMS, CASH, REFUSE }
    record Session(NapoliResident npc, String token, long expires, int cashTax) {}

    private GiorgiaMeloni() {}

    static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("napulitassa")
                .then(Commands.argument("turno", StringArgumentType.word())
                        .then(Commands.literal("paga").executes(c -> answer(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "turno"), Payment.ITEMS)))
                        .then(Commands.literal("soldi").executes(c -> answer(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "turno"), Payment.CASH)))
                        .then(Commands.literal("rifiuta").executes(c -> answer(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "turno"), Payment.REFUSE)))));
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        long now = server.overworld().getOverworldClockTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NapoliResident npc = player.getAttached(COLLECTOR);
            if (!eligible(player)) {
                dismiss(player);
                continue;
            }
            long due = player.getAttachedOrCreate(NEXT_VISIT, () -> now + INTERVAL);
            boolean reached = npc != null && npc.isAlive() && npc.level() == player.level()
                    && npc.distanceToSqr(player) <= 3 * 3 && npc.hasLineOfSight(player);
            if (reached) player.removeAttached(APPROACH_DEADLINE);
            Long deadline = player.getAttached(APPROACH_DEADLINE);
            if (deadline != null && server.overworld().getGameTime() >= deadline) {
                if (Carabinieri.jailForTaxEvasion(player)) {
                    NapoliNotice.show(player, Component.translatable("napuli.tax.timeout"));
                    finishVisit(player);
                }
                continue;
            }
            if (now < due && deadline == null && npc == null) continue;
            if (npc == null || !npc.isAlive() || npc.level() != player.level() || npc.distanceToSqr(player) > 128 * 128) {
                dismiss(player);
                if (server.getTickCount() % 100 == 0) spawnVisit(player);
                continue;
            }
            npc.getLookControl().setLookAt(player, 30, 30);
            if (!reached) npc.getNavigation().moveTo(player, 1.15);
            else {
                npc.getNavigation().stop();
                if (!player.hasAttached(SESSION)) open(player, npc);
            }
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !Carabinieri.isJailed(player);
    }

    /** Prefer a reachable route, but still allow a safe spawn when the player is barricaded in. */
    static boolean spawnVisit(ServerPlayer player) {
        if (!eligible(player)) return false;
        NapoliResident existing = player.getAttached(COLLECTOR);
        if (existing != null && existing.isAlive() && existing.level() == player.level()) return true;
        beginApproach(player);
        ServerLevel level = player.level();
        NapoliResident fallback = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos pos = Spawner.groundNear(level, player, SPAWN_DISTANCE, SPAWN_DISTANCE);
            if (pos == null) continue;
            NapoliResident npc = NapoliResident.create(level, NapoliResident.Role.GIORGIA_MELONI, pos, 0);
            // Ground navigation cannot plan until the newly created mob has a grounded state.
            npc.setOnGround(true);
            npc.setHomeTo(player.blockPosition(), 96);
            npc.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(80);
            npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32);
            if (npc.getNavigation() instanceof GroundPathNavigation navigation) {
                navigation.setCanOpenDoors(true);
                navigation.setCanFloat(true);
            }
            var path = npc.getNavigation().createPath(player, 0);
            if (!level.noCollision(npc)) continue;
            if (path == null || !path.canReach()) {
                if (fallback == null) fallback = npc;
                continue;
            }
            if (!startVisit(player, npc)) continue;
            npc.getNavigation().moveTo(path, 1.15);
            return true;
        }
        return fallback != null && startVisit(player, fallback);
    }

    private static void beginApproach(ServerPlayer player) {
        if (player.hasAttached(APPROACH_DEADLINE)) return;
        player.setAttached(APPROACH_DEADLINE, player.level().getServer().overworld().getGameTime() + APPROACH_TIMEOUT);
        NapoliNotice.show(player, Component.translatable("napuli.tax.approach_warning"));
    }

    private static boolean startVisit(ServerPlayer player, NapoliResident npc) {
        if (!player.level().addFreshEntity(npc)) return false;
        player.setAttached(COLLECTOR, npc);
        player.removeAttached(SESSION);
        NapoliNotice.show(player, Component.translatable("napuli.tax.arriving"));
        return true;
    }

    /** Old collectors loaded from a save cannot create a second bill or charge a different player. */
    static void residentTick(NapoliResident npc) {
        for (ServerPlayer player : ((ServerLevel) npc.level()).players())
            if (player.getAttached(COLLECTOR) == npc && eligible(player)) return;
        npc.discard();
    }

    static void open(ServerPlayer player, NapoliResident npc) {
        if (player.getAttached(COLLECTOR) != npc || !eligible(player) || npc.level() != player.level()
                || !npc.isAlive() || player.distanceToSqr(npc) > TALK_RANGE_SQUARED || !npc.hasLineOfSight(player)) return;
        player.removeAttached(APPROACH_DEADLINE);
        int count = TaxPayment.amount(player.getInventory().getNonEquipmentItems());
        int balance = NapoliStreet.cash(player);
        int cashTax = TaxPayment.cashAmount(balance);
        String token = UUID.randomUUID().toString();
        player.setAttached(SESSION, new Session(npc, token, player.level().getGameTime() + 20 * 90, cashTax));
        var data = new CommonDialogData(Component.translatable("napuli.tax.title"), Optional.empty(), true, false,
                DialogAction.CLOSE, List.of(
                        new PlainMessage(Component.translatable("napuli.tax.request", balance, cashTax), 330),
                        new PlainMessage(Component.translatable("napuli.tax.protected"), 330),
                        new PlainMessage(Component.translatable("napuli.tax.warning", Carabinieri.JAIL_SECONDS), 330)), List.of());
        var actions = new ArrayList<ActionButton>();
        if (count > 0 || balance == 0) actions.add(new ActionButton(new CommonButtonData(Component.translatable(
                count == 0 ? "napuli.tax.exempt.button" : "napuli.tax.pay", count), 330),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/napulitassa " + token + " paga")))));
        if (balance > 0) actions.add(new ActionButton(new CommonButtonData(Component.translatable("napuli.tax.cash", cashTax), 330),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/napulitassa " + token + " soldi")))));
        actions.add(new ActionButton(new CommonButtonData(Component.translatable("napuli.tax.refuse"), 330),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/napulitassa " + token + " rifiuta")))));
        NapoliNotice.show(player, Component.translatable("napuli.tax.reopen"));
        player.openDialog(Holder.direct(new MultiActionDialog(data, actions, Optional.empty(), 1)));
    }

    static int answer(ServerPlayer player, String token, Payment payment) {
        Session session = player.getAttached(SESSION);
        if (session == null || !session.token().equals(token) || player.getAttached(COLLECTOR) != session.npc()) return 0;
        NapoliResident npc = session.npc();
        if (!eligible(player) || !npc.isAlive() || npc.level() != player.level()
                || player.distanceToSqr(npc) > TALK_RANGE_SQUARED) return 0;
        if (player.level().getGameTime() > session.expires()) {
            open(player, npc);
            return 0;
        }
        if (payment == Payment.ITEMS) {
            if (TaxPayment.amount(player.getInventory().getNonEquipmentItems()) == 0 && NapoliStreet.cash(player) > 0) {
                NapoliNotice.show(player, Component.translatable("napuli.tax.no_materials"));
                open(player, npc);
                return 0;
            }
            int taken = TaxPayment.collect(player.getInventory().getNonEquipmentItems());
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            NapoliNotice.show(player, Component.translatable(taken == 0 ? "napuli.tax.exempt" : "napuli.tax.paid", taken));
        } else if (payment == Payment.CASH) {
            int tax = TaxPayment.cashAmount(NapoliStreet.cash(player));
            // Requote a changed bill instead of withdrawing an amount the player has not reviewed.
            if (tax != session.cashTax()) {
                NapoliNotice.show(player, Component.translatable("napuli.tax.balance_changed"));
                open(player, npc);
                return 0;
            }
            if (tax == 0 && TaxPayment.amount(player.getInventory().getNonEquipmentItems()) > 0) {
                NapoliNotice.show(player, Component.translatable("napuli.tax.no_cash"));
                open(player, npc);
                return 0;
            }
            NapoliStreet.credit(player, -tax);
            WorkEconomy.revenue(player.level(),tax);
            NapoliNotice.show(player, Component.translatable(tax == 0 ? "napuli.tax.exempt" : "napuli.tax.cash_paid",
                    tax, NapoliStreet.cash(player)));
        } else if (!Carabinieri.jailForTaxEvasion(player)) return 0;
        finishVisit(player);
        return 1;
    }

    private static void finishVisit(ServerPlayer player) {
        player.setAttached(NEXT_VISIT, player.level().getServer().overworld().getOverworldClockTime() + INTERVAL);
        player.removeAttached(APPROACH_DEADLINE);
        dismiss(player);
    }

    private static void dismiss(ServerPlayer player) {
        NapoliResident npc = player.getAttached(COLLECTOR);
        if (npc != null) npc.discard();
        player.removeAttached(COLLECTOR);
        player.removeAttached(SESSION);
    }

    /** Operator-only shortcut, available under /napuli meloni. */
    public static int summon(ServerPlayer player) {
        if (!eligible(player)) {
            NapoliNotice.show(player, Component.translatable("napuli.tax.survival_only"));
            return 0;
        }
        player.setAttached(NEXT_VISIT, player.level().getServer().overworld().getOverworldClockTime());
        boolean spawned = spawnVisit(player);
        if (!spawned) NapoliNotice.show(player, Component.translatable("napuli.tax.no_space"));
        return spawned ? 1 : 0;
    }
}
