package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
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
    private static final int SPAWN_DISTANCE = 50;
    private static final double TALK_RANGE_SQUARED = 6 * 6;
    static final AttachmentType<Long> NEXT_VISIT = AttachmentRegistry.<Long>builder()
            .persistent(Codec.LONG).copyOnDeath().buildAndRegister(Napuli.id("next_tax_visit"));
    // Live references/tokens are deliberately not saved. An outstanding bill survives via NEXT_VISIT.
    static final AttachmentType<NapoliResident> COLLECTOR = AttachmentRegistry.create(Napuli.id("tax_collector"));
    static final AttachmentType<Session> SESSION = AttachmentRegistry.create(Napuli.id("tax_dialog"));
    record Session(NapoliResident npc, String token, long expires) {}

    private GiorgiaMeloni() {}

    static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("napulitassa")
                .then(Commands.argument("turno", StringArgumentType.word())
                        .then(Commands.literal("paga").executes(c -> answer(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "turno"), true)))
                        .then(Commands.literal("rifiuta").executes(c -> answer(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "turno"), false)))));
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
            if (now < due) continue;
            if (npc == null || !npc.isAlive() || npc.level() != player.level() || npc.distanceToSqr(player) > 128 * 128) {
                dismiss(player);
                if (server.getTickCount() % 100 == 0) spawnVisit(player);
                continue;
            }
            npc.getLookControl().setLookAt(player, 30, 30);
            if (npc.distanceToSqr(player) > 3 * 3) npc.getNavigation().moveTo(player, 1.15);
            else {
                npc.getNavigation().stop();
                if (!player.hasAttached(SESSION)) open(player, npc);
            }
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !Carabinieri.isJailed(player);
    }

    /** Returns false on impassable terrain; the due bill is retried instead of spawning inside a wall. */
    static boolean spawnVisit(ServerPlayer player) {
        if (!eligible(player)) return false;
        NapoliResident existing = player.getAttached(COLLECTOR);
        if (existing != null && existing.isAlive() && existing.level() == player.level()) return true;
        ServerLevel level = player.level();
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
            if (!level.noCollision(npc) || path == null || !path.canReach()) {
                Napuli.LOGGER.debug("Tax spawn rejected at {}: path={}, reachable={}", pos, path, path != null && path.canReach());
                continue;
            }
            if (!level.addFreshEntity(npc)) continue;
            player.setAttached(COLLECTOR, npc);
            player.removeAttached(SESSION);
            npc.getNavigation().moveTo(path, 1.15);
            player.sendSystemMessage(Component.translatable("napuli.tax.arriving"));
            return true;
        }
        return false;
    }

    /** Old collectors loaded from a save cannot create a second bill or charge a different player. */
    static void residentTick(NapoliResident npc) {
        for (ServerPlayer player : ((ServerLevel) npc.level()).players())
            if (player.getAttached(COLLECTOR) == npc && eligible(player)) return;
        npc.discard();
    }

    static void open(ServerPlayer player, NapoliResident npc) {
        if (player.getAttached(COLLECTOR) != npc || !eligible(player) || npc.level() != player.level()
                || !npc.isAlive() || player.distanceToSqr(npc) > TALK_RANGE_SQUARED) return;
        int count = TaxPayment.amount(player.getInventory().getNonEquipmentItems());
        String token = UUID.randomUUID().toString();
        player.setAttached(SESSION, new Session(npc, token, player.level().getGameTime() + 20 * 90));
        var data = new CommonDialogData(Component.translatable("napuli.tax.title"), Optional.empty(), true, false,
                DialogAction.CLOSE, List.of(
                        new PlainMessage(Component.translatable("napuli.tax.request", count), 330),
                        new PlainMessage(Component.translatable("napuli.tax.protected"), 330),
                        new PlainMessage(Component.translatable("napuli.tax.warning", Carabinieri.JAIL_SECONDS), 330)), List.of());
        var pay = new ActionButton(new CommonButtonData(Component.translatable(
                count == 0 ? "napuli.tax.exempt.button" : "napuli.tax.pay", count), 330),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/napulitassa " + token + " paga"))));
        var refuse = new ActionButton(new CommonButtonData(Component.translatable("napuli.tax.refuse"), 330),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/napulitassa " + token + " rifiuta"))));
        player.sendSystemMessage(Component.translatable("napuli.tax.reopen"));
        player.openDialog(Holder.direct(new MultiActionDialog(data, List.of(pay, refuse), Optional.empty(), 1)));
    }

    static int answer(ServerPlayer player, String token, boolean pay) {
        Session session = player.getAttached(SESSION);
        if (session == null || !session.token().equals(token) || player.getAttached(COLLECTOR) != session.npc()) return 0;
        NapoliResident npc = session.npc();
        if (!eligible(player) || !npc.isAlive() || npc.level() != player.level()
                || player.distanceToSqr(npc) > TALK_RANGE_SQUARED) return 0;
        if (player.level().getGameTime() > session.expires()) {
            open(player, npc);
            return 0;
        }
        if (pay) {
            int taken = TaxPayment.collect(player.getInventory().getNonEquipmentItems());
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            player.sendSystemMessage(Component.translatable(taken == 0 ? "napuli.tax.exempt" : "napuli.tax.paid", taken));
        } else if (!Carabinieri.jailForTaxEvasion(player)) return 0;
        player.setAttached(NEXT_VISIT, player.level().getServer().overworld().getOverworldClockTime() + INTERVAL);
        dismiss(player);
        return 1;
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
            player.sendSystemMessage(Component.translatable("napuli.tax.survival_only"));
            return 0;
        }
        player.setAttached(NEXT_VISIT, player.level().getServer().overworld().getOverworldClockTime());
        boolean spawned = spawnVisit(player);
        if (!spawned) player.sendSystemMessage(Component.translatable("napuli.tax.no_space"));
        return spawned ? 1 : 0;
    }
}
