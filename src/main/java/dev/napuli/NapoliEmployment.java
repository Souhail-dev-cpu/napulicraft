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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.AABB;
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

public final class NapoliEmployment {
    private static final BlockPos OLD_DESK = new BlockPos(-119, 65, 33);
    static final BlockPos CLERK_POST = new BlockPos(-118, 64, 31);
    private static final String CLERK = "napuli_employment_clerk";
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

    static final AttachmentType<Work> WORK = AttachmentRegistry.<Work>builder().persistent(Work.CODEC)
            .copyOnDeath().buildAndRegister(Napuli.id("player_employment"));

    private static final int HOUR = 1000;
    public static final int OPEN = 2500, CLOSE = 10500;
    private static final int PATCHES = 4, SWEEPS = 3;
    private static final String SUPERVISOR = "napuli_supervisor_";
    private static final String ASSISTANT = "napuli_workmate_";

    record Timecard(long day, int ticks, int sweeps, long action, long lastSweep) {
        static final Codec<Timecard> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Timecard::day),
                Codec.INT.fieldOf("ticks").forGetter(Timecard::ticks),
                Codec.INT.fieldOf("sweeps").forGetter(Timecard::sweeps),
                Codec.LONG.fieldOf("action").forGetter(Timecard::action),
                Codec.LONG.fieldOf("last_sweep").forGetter(Timecard::lastSweep)).apply(i, Timecard::new));
    }

    static final AttachmentType<Timecard> HOURS = AttachmentRegistry.<Timecard>builder()
            .persistent(Timecard.CODEC).copyOnDeath().buildAndRegister(Napuli.id("work_timecard"));

    private static final AttachmentType<Boolean> TRACK_OFFICE = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL).copyOnDeath().buildAndRegister(Napuli.id("track_employment_office"));
    private NapoliEmployment() {}

    static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(EmploymentOffers.TYPE, EmploymentOffers.CODEC);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != NapoliMap.LEVEL || hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();

            if (player.getMainHandItem().is(Items.BRUSH) && patchIndex(pos) >= 0) {
                if (player instanceof ServerPlayer p) sweep(p, pos);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            var city = server.getLevel(NapoliMap.LEVEL);
            if (city == null) return;
            for (ServerPlayer p : city.players()) updateHours(p, city);
            if (server.getTickCount() % 20 == 0 && NapoliInteriors.upgradedWorkplaces(city)) maintainStaff(city);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, kind) -> dispatcher.register(
                Commands.literal("lavoro").executes(c -> status(c.getSource().getPlayerOrException()))
                    .then(Commands.literal("segui").executes(c -> trackOffice(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("paga").executes(c -> collectPay(c.getSource().getPlayerOrException(), true)))
                    .then(Commands.literal("scegli").then(Commands.argument("contratto", StringArgumentType.word())
                        .suggests((c, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                Arrays.stream(Contract.values()).map(Contract::id), builder))
                        .executes(c -> choose(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "contratto")))))));
    }

    private static void menu(ServerPlayer p) {
        if (FootballEmployment.hasContract(p)) {
            CityLeague.menu(p);
            return;
        }
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
        if (!eligible(p) || staffTarget(p.level(), CLERK_POST, CLERK).distToCenterSqr(p.position()) > 36 || find(id) == null) return 0;
        if (FootballEmployment.hasContract(p)) {
            NapoliNotice.show(p, FootballEmployment.summary(p));
            return 0;
        }
        long day = NapoliBroadcast.day(p.level());
        Work previous = p.getAttached(WORK);
        if (previous != null && previous.contract().equals(id)) return status(p);
        if (previous != null && (previous.stage() == 2 || previous.day() == day && previous.stage() != 0)) {
            say(p, "finish_first");
            return 0;
        }
        p.setAttached(WORK, new Work(id, day, 0, previous == null ? Long.MIN_VALUE : previous.paid()));
        p.removeAttached(TRACK_OFFICE);
        CityOnboarding.event(p,CityOnboarding.JOB);
        say(p, "hired");
        return status(p);
    }

    static int work(ServerPlayer p, Contract contract, BlockPos sign) {
        if (!eligible(p)) return 0;
        Work state = p.getAttached(WORK);
        if (FootballEmployment.hasContract(p) && (state == null || state.stage() != 2 || !sign.equals(contract.clock))) {
            NapoliNotice.show(p, FootballEmployment.summary(p));
            return 0;
        }
        if (state == null || !state.contract().equals(contract.id())) { say(p, "desk"); return 0; }
        long day = NapoliBroadcast.day(p.level());
        long hour = NapoliBroadcast.time(p.level());
        if (state.paid() >= day) { say(p, "already_paid"); return 0; }

        int stage = state.day() == day || state.stage() == 2 ? state.stage() : 0;
        if (stage != 2 && (hour < OPEN || hour >= CLOSE)) { say(p, "closed"); return 0; }
        if (sign.equals(contract.clock) && stage == 0) {
            if (!WorkCareer.begin(p,contract)) return 0;
            p.setAttached(HOURS, new Timecard(day, 0, 0, -HOUR, -HOUR));
            p.setAttached(WORK, new Work(state.contract(), day, 1, state.paid()));
            say(p, "started");
            if (contract == Contract.CLEANER) say(p, "sweep_help");
            return status(p);
        }
        if (sign.equals(contract.task) && state.day() == day && hour >= OPEN && hour < CLOSE && stage == 1
                && contract != Contract.CLEANER) {
            if ((contract==Contract.PORTER||contract==Contract.COURIER)&&!WorkResources.consumeParcel(p,contract)) { p.sendSystemMessage(Component.literal("Ritira prima il pacco al deposito seguendo la freccia."));return 0; }
            if (contract.supplies != null) {
                boolean found = false;
                for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(contract.supplies)) {
                    p.getInventory().removeItem(i, 1);
                    found = true;
                    break;
                }
                if (!found) {
                    NapoliNotice.show(p, Component.translatable("napuli.work.need", contract.supplies.getDefaultInstance().getHoverName()));
                    return 0;
                }
            }
            if(stage==2)return status(p);
            WorkEconomy.delivery(p.level(),contract,1);
            CityEffects.particles(p.level(),net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    sign.getX() + .5, sign.getY() + 1, sign.getZ() + .5, 8, .35, .2, .35, .02);
            p.setAttached(WORK, new Work(state.contract(), day, 2, state.paid()));
            Timecard card = timecard(p, day);
            p.setAttached(HOURS, new Timecard(day, card.ticks(), card.sweeps(), p.level().getGameTime(), card.lastSweep()));
            say(p, "done_task");
            return status(p);
        }
        if (sign.equals(contract.clock) && stage == 2) {
            return collectPay(p, false);
        }
        return status(p);
    }

    private static int collectPay(ServerPlayer p, boolean proportional) {
        if (!eligible(p)) return 0;
        Work work = p.getAttached(WORK);
        if (work == null || work.stage() != 2) {
            if (FootballEmployment.hasContract(p) && CityLeague.claim(p) > 0) return 1;
            return status(p);
        }
        Contract contract = find(work.contract());
        if (contract == null) return 0;
        long day = NapoliBroadcast.day(p.level());
        boolean beforeClose = work.day() == day && NapoliBroadcast.time(p.level()) < CLOSE;
        int ticks = p.getAttached(HOURS) == null ? HOUR : timecard(p, work.day()).ticks();
        int hours = ticks / HOUR;
        if (hours == 0 && beforeClose && !proportional) {
            NapoliNotice.show(p, Component.translatable("napuli.work.hour_progress", ticks * 60 / HOUR));
            return 0;
        }
        int pay = shiftPay(ticks, WorkCareer.hourly(p,contract), proportional || !beforeClose);
        if (pay == 0 && beforeClose) return 0;
        if (pay > 0 && !NapoliStreet.canCredit(p, pay)) {
            NapoliNotice.show(p, CityText.t("wallet.full", NapoliStreet.MAX_CASH));
            return 0;
        }
        if (pay > 0 && !WorkEconomy.wage(p,pay)) {
            NapoliNotice.show(p, Component.literal("Il fondo lavori è temporaneamente insufficiente. Il compenso resta da riscuotere."));
            return 0;
        }
        p.setAttached(WORK, new Work(work.contract(), day, 0, work.day()));
        p.removeAttached(HOURS);
        if (pay > 0) WorkCareer.completed(p,contract,hours,pay);
        if ((proportional || !beforeClose) && ticks % HOUR != 0)
            NapoliNotice.show(p, Component.translatable("napuli.work.partial_pay", ticks * 60 / HOUR, pay));
        else NapoliNotice.show(p, Component.translatable("napuli.work.hour_pay", hours, pay));
        return 1;
    }

    static int status(ServerPlayer p) {
        if (eligible(p) && FootballEmployment.hasContract(p)) {
            NapoliNotice.show(p, FootballEmployment.summary(p));
            return 1;
        }
        Work state = p.getAttached(WORK);
        if (!eligible(p) || state == null) { say(p, "desk"); return 1; }
        Contract contract = find(state.contract());
        if (contract == null) return 0;
        var objective = objective(p);
        if (objective == null) { say(p, "already_paid"); return 1; }
        if (state.day() == NapoliBroadcast.day(p.level()) && state.stage() > 0) {
            int ticks = timecard(p, state.day()).ticks();
            NapoliNotice.show(p, Component.translatable("napuli.work.hours_status", ticks / HOUR, ticks % HOUR * 60 / HOUR,
                    ticks / HOUR * WorkCareer.hourly(p,contract)));
        }
        var target = objective.target();
        NapoliNotice.show(p, Component.translatable("napuli.work.status", contract.nameLabel(),
                Component.translatable(objective.key()), target.getX(), target.getY(), target.getZ()));
        if (state.day() == NapoliBroadcast.day(p.level()) && state.stage() == 1 && contract.supplies != null)
            NapoliNotice.show(p, Component.translatable("napuli.work.need", contract.supplies.getDefaultInstance().getHoverName()));
        return 1;
    }

    private static int trackOffice(ServerPlayer p) {
        if (!eligible(p)) return 0;
        if (FootballEmployment.hasContract(p)) return CityLeague.menu(p);
        boolean active = office(p) == null;
        p.setAttached(TRACK_OFFICE, active);
        say(p, active ? "visit" : "untrack");
        return 1;
    }

    static void footballSigned(ServerPlayer player) { player.removeAttached(TRACK_OFFICE); }

    static boolean trackingOffice(ServerPlayer player) { return eligible(player) && !FootballEmployment.hasContract(player) && player.getAttachedOrElse(TRACK_OFFICE, false); }

    static NapoliFactions.Objective office(ServerPlayer player) {
        if (!eligible(player) || FootballEmployment.hasContract(player) || !player.getAttachedOrElse(TRACK_OFFICE, player.getAttached(WORK) == null)) return null;
        return new NapoliFactions.Objective("napuli.work.visit", staffTarget(player.level(), CLERK_POST, CLERK));
    }

    static NapoliFactions.Objective objective(ServerPlayer player) {
        if (!eligible(player)) return null;
        if (FootballEmployment.hasContract(player)) return FootballEmployment.objective(player);
        Work state = player.getAttached(WORK);
        long day = NapoliBroadcast.day(player.level());
        if (state == null || state.paid() >= day) return null;
        Contract contract = find(state.contract());
        if (contract == null) return null;
        int stage = state.day() == day || state.stage() == 2 ? state.stage() : 0;
        var resource = stage==1 ? WorkResources.target(player,contract) : null;
        if(resource!=null)return new NapoliFactions.Objective("napuli.work.resource_"+contract.id(),resource);
        return new NapoliFactions.Objective("napuli.work." + (stage == 0 ? "clock_in" : stage == 1 ? "task_" + contract.id() : "clock_out"),
                stage == 1 && contract == Contract.CLEANER
                        ? patch(Math.min(PATCHES - 1, timecard(player, day).sweeps() / SWEEPS)).above()
                        : stage == 1 ? staffTarget(player.level(), contract.task, ASSISTANT + contract.id())
                        : staffTarget(player.level(), contract.clock, SUPERVISOR + contract.id()));
    }

    static void buildWorkplaces(ServerLevel city) {
        clearBoard(city, OLD_DESK, "SPORTELLO");
        if (city.getBlockState(OLD_DESK.below()).is(Blocks.LECTERN))
            city.setBlock(OLD_DESK.below(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (Contract contract : Contract.values()) {
            clearBoard(city, contract.clock, "TIMBRATURA");
            clearBoard(city, contract.task, "MANSIONE");
        }
        maintainStaff(city);
    }

    private static void maintainStaff(ServerLevel city) {
        checkPost(city, CLERK_POST, CLERK, NapoliResident.Role.EMPLOYMENT_CLERK, "napuli.work.clerk");
        for (Contract contract : Contract.values()) {
            checkPost(city, contract.clock, SUPERVISOR + contract.id(), NapoliResident.Role.SHIFT_LEAD, "napuli.work.supervisor");
            if (contract != Contract.CLEANER)
                checkPost(city, contract.task, ASSISTANT + contract.id(), NapoliResident.Role.SHIFT_WORKER, "napuli.work.workmate");
        }
    }

    private static void checkPost(ServerLevel city, BlockPos anchor, String tag, NapoliResident.Role role, String name) {
        if (!city.hasChunkAt(anchor) || city.players().stream().noneMatch(p -> anchor.distToCenterSqr(p.position()) < 40 * 40)) return;
        var actors = staff(city, anchor, tag);
        if (actors.size() == 1 && actors.getFirst().role() == role) return;
        putWorker(city, anchor, tag, role, name, actors);
    }

    private static void clearBoard(ServerLevel city, BlockPos pos, String title) {
        if (city.getBlockEntity(pos) instanceof SignBlockEntity sign
                && sign.getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString().equals(title))
            city.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static List<NapoliResident> staff(ServerLevel city, BlockPos anchor, String tag) {
        return city.getEntitiesOfClass(NapoliResident.class, new AABB(anchor).inflate(7), n -> n.entityTags().contains(tag));
    }

    private static BlockPos staffTarget(ServerLevel city, BlockPos anchor, String tag) {
        var actors = staff(city, anchor, tag);
        return actors.isEmpty() ? anchor : actors.getFirst().blockPosition();
    }

    private static void putWorker(ServerLevel city, BlockPos anchor, String tag, NapoliResident.Role role,
                                  String name, List<NapoliResident> existing) {
        // Entities from old saves can load after their blocks. Keep the established worker when both appear.
        NapoliResident worker = existing.stream().filter(npc -> npc.role() != role).findFirst()
                .orElse(existing.isEmpty() ? null : existing.getFirst());
        for (NapoliResident duplicate : existing) if (duplicate != worker) duplicate.discard();
        BlockPos at = workerPost(city, anchor, worker, role == NapoliResident.Role.EMPLOYMENT_CLERK);
        if (at == null) {
            Napuli.LOGGER.warn("No clear workstation for {} near {}", tag, anchor);
            return;
        }
        if (worker == null) worker = NapoliResident.spawn(city, role, at, 0);
        else worker.relocate(at, 0);
        worker.setEmploymentAppearance(role, Math.floorMod(tag.hashCode(), role.variants()));
        worker.setNoAi(true);
        worker.setPersistenceRequired();
        worker.setCustomName(Component.translatable(name));
        worker.setCustomNameVisible(true);
        worker.addTag(tag);
    }

    private static BlockPos workerPost(ServerLevel city, BlockPos anchor, NapoliResident worker, boolean clerk) {
        if (clerk && clearPost(city, anchor, worker)) return anchor;
        for (BlockPos offset : List.of(anchor.north(3), anchor.east(3), anchor.south(3), anchor.west(3))) {
            if (availablePost(city, offset, worker)) return offset;
        }
        for (int radius = 3; radius <= 5; radius++) {
            for (BlockPos candidate : BlockPos.betweenClosed(anchor.offset(-radius, 0, -radius), anchor.offset(radius, 0, radius))) {
                if (candidate.distSqr(anchor) < 9 || candidate.distSqr(anchor) > 25) continue;
                if (availablePost(city, candidate, worker)) return candidate.immutable();
            }
        }
        return clerk || !availablePost(city, anchor, worker) ? null : anchor;
    }

    private static boolean availablePost(ServerLevel city, BlockPos at, NapoliResident worker) {
        return Arrays.stream(NapoliCivicLife.Job.values()).noneMatch(job -> job.site.distSqr(at) < 5)
                && clearPost(city, at, worker);
    }

    private static boolean clearPost(ServerLevel city, BlockPos pos, NapoliResident worker) {
        return NapoliResidentHomes.walkable(city, pos)
                && city.getEntitiesOfClass(NapoliResident.class, new AABB(pos).inflate(1.2, .5, 1.2),
                        npc -> npc != worker).isEmpty();
    }

    static boolean interact(NapoliResident npc, net.minecraft.world.entity.player.Player player) {
        if (npc.role() == NapoliResident.Role.EMPLOYMENT_CLERK) {
            if (player instanceof ServerPlayer p && eligible(p) && p.distanceToSqr(npc) <= 36) menu(p);
            return true;
        }
        if (npc.role() != NapoliResident.Role.SHIFT_LEAD && npc.role() != NapoliResident.Role.SHIFT_WORKER) return false;
        for (Contract contract : Contract.values()) {
            boolean supervisor = npc.entityTags().contains(SUPERVISOR + contract.id());
            if (!supervisor && !npc.entityTags().contains(ASSISTANT + contract.id())) continue;
            if (player instanceof ServerPlayer p && p.distanceToSqr(npc) <= 36)
                work(p, contract, supervisor ? contract.clock : contract.task);
            return true;
        }
        return true;
    }

    private static BlockPos patch(int index) {
        return Contract.CLEANER.task.offset(index % 2 * 2, -1, index / 2 * 2);
    }

    private static int patchIndex(BlockPos pos) {
        for (int i = 0; i < PATCHES; i++) if (patch(i).equals(pos)) return i;
        return -1;
    }

    static Timecard timecard(ServerPlayer p, long day) {
        Timecard card = p.getAttached(HOURS);
        return card != null && card.day() == day ? card : new Timecard(day, 0, 0, -HOUR, -HOUR);
    }

    private static void sweep(ServerPlayer p, BlockPos pos) {
        if (FootballEmployment.hasContract(p)) return;
        if (!eligible(p) || pos.distToCenterSqr(p.position()) > 25) return;
        long day = NapoliBroadcast.day(p.level()), hour = NapoliBroadcast.time(p.level());
        Work work = p.getAttached(WORK);
        if (work == null || !work.contract().equals(Contract.CLEANER.id()) || work.day() != day || work.stage() == 0
                || hour < OPEN || hour >= CLOSE) return;
        var card = timecard(p, day);
        long now = p.level().getGameTime();
        if (now - card.lastSweep() < 7) return;
        int made = card.sweeps() % (PATCHES * SWEEPS);
        if (patchIndex(pos) != made / SWEEPS) { say(p, "sweep_target"); return; }
        int advance=1+Math.min(1,WorkCareer.get(p,Contract.CLEANER).tier());
        int next = card.sweeps() + Math.min(advance,SWEEPS-card.sweeps()%SWEEPS);
        p.setAttached(HOURS, new Timecard(day, card.ticks(), next, now, now));
        p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        CityEffects.particles(p.level(),ParticleTypes.POOF, pos.getX() + .5, pos.getY() + 1.08, pos.getZ() + .5, 4, .25, .04, .25, .005);
        CityEffects.sound(p.level(),null, pos, net.minecraft.sounds.SoundEvents.BRUSH_GENERIC, net.minecraft.sounds.SoundSource.PLAYERS, .5f, 1);
        if (next % SWEEPS == 0)
            NapoliNotice.show(p, Component.translatable("napuli.work.swept", made / SWEEPS + 1, PATCHES));
        if (next == PATCHES * SWEEPS) {
            p.setAttached(WORK, new Work(work.contract(), day, 2, work.paid()));
            say(p, "done_task");
        }
    }

    private static void updateHours(ServerPlayer p, ServerLevel city) {
        if (FootballEmployment.hasContract(p)) return;
        Work work = p.getAttached(WORK);
        long day = NapoliBroadcast.day(city), hour = NapoliBroadcast.time(city);
        if (!eligible(p) || work == null || work.stage() == 0 || work.day() != day || hour < OPEN || hour >= CLOSE) return;
        Contract contract = find(work.contract());
        if (contract == null) return;
        var resource=WorkResources.target(p,contract);
        boolean atTask = contract.task.distToCenterSqr(p.position()) <= 16 * 16;
        boolean atClock = work.stage() == 2 && contract.clock.distToCenterSqr(p.position()) <= 16 * 16;
        if (!atTask && !atClock && (resource == null || resource.distToCenterSqr(p.position()) > 16 * 16)) return;
        Timecard card = timecard(p, day);
        long now = city.getGameTime();
        if (contract == Contract.CLEANER && work.stage() == 1) {
            BlockPos dirt = patch(card.sweeps() / SWEEPS);
            CityEffects.particles(city,p, ParticleTypes.DUST_PLUME, false, false,
                    dirt.getX() + .5, dirt.getY() + 1.02, dirt.getZ() + .5, 3, .21, .01, .21, .004);
        }
        if (card.ticks() >= CLOSE - OPEN || work.stage() == 1
                && (card.action() < 0 || now - card.action() > 2L * HOUR)) return;
        int ticks = card.ticks() + 10;
        p.setAttached(HOURS, new Timecard(day, ticks, card.sweeps(), card.action(), card.lastSweep()));
        if (ticks % HOUR == 0)
            NapoliNotice.show(p, Component.translatable("napuli.work.hour_logged", ticks / HOUR, ticks / HOUR * contract.job.wage));
    }

    static int shiftPay(int ticks, int wage, boolean includePartialHour) {
        if (ticks <= 0) return 0;
        return includePartialHour ? Math.max(1, ticks * wage / HOUR) : ticks / HOUR * wage;
    }

    static Contract find(String id) {
        return Arrays.stream(Contract.values()).filter(contract -> contract.id().equals(id)).findFirst().orElse(null);
    }

    static void recordActivity(ServerPlayer p) {
        var work=p.getAttached(WORK);if(work==null||work.stage()!=1||work.day()!=NapoliBroadcast.day(p.level()))return;
        var card=timecard(p,work.day());p.setAttached(HOURS,new Timecard(card.day(),card.ticks(),card.sweeps(),p.level().getGameTime(),card.lastSweep()));
    }

    private static void say(ServerPlayer p, String message) {
        NapoliNotice.show(p, Component.translatable("napuli.work." + message));
    }
}
