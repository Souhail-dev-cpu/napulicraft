package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class NapoliEmployment {
    static final BlockPos DESK = new BlockPos(-119, 65, 33);
    enum Contract {
        BAKER(NapoliCivicLife.Job.BAKER, -151, 44, -146, 44, Items.BREAD),
        PORTER(NapoliCivicLife.Job.PORTER, -224, 59, -223, 67, null),
        MERCHANT(NapoliCivicLife.Job.MERCHANT, -186, 48, -181, 48, Items.WHEAT),
        CLEANER(NapoliCivicLife.Job.CLEANER, -166, 53, -172, 53, null),
        GARDENER(NapoliCivicLife.Job.GARDENER, -219, 51, -215, 51, Items.OAK_SAPLING),
        FISHER(NapoliCivicLife.Job.FISHER, -303, 59, -303, 72, Items.COD),
        COURIER(NapoliCivicLife.Job.COURIER, -261, 51, -277, 47, null),
        CARPENTER(NapoliCivicLife.Job.CARPENTER, -313, 53, -310, 57, Items.OAK_PLANKS);

        final NapoliCivicLife.Job job;
        final BlockPos clock, task;
        final Item supplies;

        Contract(NapoliCivicLife.Job job, int x, int z, int tx, int tz, Item supplies) {
            this.job = job;
            clock = new BlockPos(x, 64, z);
            task = new BlockPos(tx, 64, tz);
            this.supplies = supplies;
        }

        String id() { return name().toLowerCase(Locale.ROOT); }
        Component nameLabel() { return Component.translatable(job.key()); }
    }

    record Work(String contract, long day, int stage, long paid) {
        static final Codec<Work> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("contract").forGetter(Work::contract),
                Codec.LONG.fieldOf("day").forGetter(Work::day),
                Codec.INT.fieldOf("stage").forGetter(Work::stage),
                Codec.LONG.fieldOf("paid").forGetter(Work::paid)).apply(i, Work::new));
    }

    private static final AttachmentType<Work> WORK = AttachmentRegistry.<Work>builder().persistent(Work.CODEC)
            .copyOnDeath().buildAndRegister(Napuli.id("player_employment"));

    private static final AttachmentType<Boolean> TRACK_OFFICE = AttachmentRegistry.create(Napuli.id("track_employment_office"));
    private NapoliEmployment() {}

    static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(EmploymentOffers.TYPE, EmploymentOffers.CODEC);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != NapoliMap.LEVEL || hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (pos.equals(DESK)) {
                if (player instanceof ServerPlayer p) menu(p);
                return InteractionResult.SUCCESS;
            }
            for (Contract contract : Contract.values()) if (pos.equals(contract.clock) || pos.equals(contract.task)) {
                if (player instanceof ServerPlayer p) work(p, contract, pos);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, kind) -> dispatcher.register(
                Commands.literal("lavoro").executes(c -> status(c.getSource().getPlayerOrException()))
                    .then(Commands.literal("segui").executes(c -> trackOffice(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("scegli").then(Commands.argument("contratto", StringArgumentType.word())
                        .executes(c -> choose(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "contratto")))))));
    }

    private static void menu(ServerPlayer p) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, EmploymentOffers.TYPE)) {
            Work work = p.getAttached(WORK);
            var offers = Arrays.stream(Contract.values()).map(c ->
                    new EmploymentOffers.Offer(c.id(), c.job.key(), c.job.wage, c.clock)).toList();
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                    new EmploymentOffers(offers, work == null ? "" : work.contract()));
            return;
        }
        var buttons = Arrays.stream(Contract.values()).map(contract -> new ActionButton(new CommonButtonData(
                Component.translatable("napuli.work.offer", contract.nameLabel(), contract.job.wage), 158),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/lavoro scegli " + contract.id()))))).toList();
        p.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(Component.translatable("napuli.work.title"),
                Optional.empty(), true, false, DialogAction.CLOSE, List.of(new PlainMessage(Component.translatable("napuli.work.intro"), 260)),
                List.of()), buttons, Optional.empty(), 2)));
    }

    private static boolean eligible(ServerPlayer p) {
        return p.level().dimension() == NapoliMap.LEVEL && p.isAlive() && !p.isSpectator() && !Carabinieri.isJailed(p);
    }

    static int choose(ServerPlayer p, String id) {
        if (!eligible(p) || DESK.distToCenterSqr(p.position()) > 36 || find(id) == null) return 0;
        long day = NapoliBroadcast.day(p.level());
        Work previous = p.getAttached(WORK);
        if (previous != null && previous.contract().equals(id)) return status(p);
        if (previous != null && previous.day() == day && previous.stage() != 0) {
            say(p, "finish_first");
            return 0;
        }
        p.setAttached(WORK, new Work(id, day, 0, previous == null ? Long.MIN_VALUE : previous.paid()));
        p.removeAttached(TRACK_OFFICE);
        say(p, "hired");
        return status(p);
    }

    static int work(ServerPlayer p, Contract contract, BlockPos sign) {
        if (!eligible(p) || sign.distToCenterSqr(p.position()) > 36) return 0;
        Work state = p.getAttached(WORK);
        if (state == null || !state.contract().equals(contract.id())) { say(p, "desk"); return 0; }
        long day = NapoliBroadcast.day(p.level());
        long hour = NapoliBroadcast.time(p.level());
        if (state.paid() >= day) { say(p, "already_paid"); return 0; }
        if (hour < 2500 || hour >= 10500) { say(p, "closed"); return 0; }
        int stage = state.day() == day ? state.stage() : 0;
        if (sign.equals(contract.clock) && stage == 0) {
            p.setAttached(WORK, new Work(state.contract(), day, 1, state.paid()));
            say(p, "started");
            return status(p);
        }
        if (sign.equals(contract.task) && stage == 1) {
            if (contract.supplies != null) {
                boolean found = false;
                for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(contract.supplies)) {
                    p.getInventory().removeItem(i, 1);
                    found = true;
                    break;
                }
                if (!found) {
                    p.sendSystemMessage(Component.translatable("napuli.work.need", contract.supplies.getDefaultInstance().getHoverName()));
                    return 0;
                }
            }
            p.level().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    sign.getX() + .5, sign.getY() + 1, sign.getZ() + .5, 8, .35, .2, .35, .02);
            p.setAttached(WORK, new Work(state.contract(), day, 2, state.paid()));
            say(p, "done_task");
            return status(p);
        }
        if (sign.equals(contract.clock) && stage == 2) {
            p.setAttached(WORK, new Work(state.contract(), day, 0, day));
            NapoliStreet.credit(p, contract.job.wage);
            p.sendSystemMessage(Component.translatable("napuli.work.paid", contract.job.wage));
            return 1;
        }
        return status(p);
    }

    private static int status(ServerPlayer p) {
        Work state = p.getAttached(WORK);
        if (!eligible(p) || state == null) { say(p, "desk"); return 1; }
        Contract contract = find(state.contract());
        if (contract == null) return 0;
        var objective = objective(p);
        if (objective == null) { say(p, "already_paid"); return 1; }
        var target = objective.target();
        p.sendSystemMessage(Component.translatable("napuli.work.status", contract.nameLabel(),
                Component.translatable(objective.key()), target.getX(), target.getY(), target.getZ()));
        if (state.day() == NapoliBroadcast.day(p.level()) && state.stage() == 1 && contract.supplies != null)
            p.sendSystemMessage(Component.translatable("napuli.work.need", contract.supplies.getDefaultInstance().getHoverName()));
        return 1;
    }

    private static int trackOffice(ServerPlayer p) {
        if (!eligible(p)) return 0;
        boolean active = !p.getAttachedOrElse(TRACK_OFFICE, false);
        p.setAttached(TRACK_OFFICE, active);
        say(p, active ? "visit" : "untrack");
        return 1;
    }

    static boolean trackingOffice(ServerPlayer player) { return eligible(player) && player.getAttachedOrElse(TRACK_OFFICE, false); }

    static NapoliFactions.Objective office(ServerPlayer player) {
        if (!eligible(player)) return null;
        Work work = player.getAttached(WORK);
        if (work != null && work.paid() < NapoliBroadcast.day(player.level()) && !trackingOffice(player)) return null;
        return new NapoliFactions.Objective("napuli.work.visit", DESK);
    }

    static NapoliFactions.Objective objective(ServerPlayer player) {
        if (!eligible(player)) return null;
        Work state = player.getAttached(WORK);
        long day = NapoliBroadcast.day(player.level());
        if (state == null || state.paid() >= day) return null;
        Contract contract = find(state.contract());
        if (contract == null) return null;
        int stage = state.day() == day ? state.stage() : 0;
        return new NapoliFactions.Objective("napuli.work." + (stage == 0 ? "clock_in" : stage == 1 ? "task_" + contract.id() : "clock_out"),
                stage == 1 ? contract.task : contract.clock);
    }

    private static Contract find(String id) {
        return Arrays.stream(Contract.values()).filter(contract -> contract.id().equals(id)).findFirst().orElse(null);
    }

    private static void say(ServerPlayer p, String message) {
        p.sendSystemMessage(Component.translatable("napuli.work." + message));
    }
}
