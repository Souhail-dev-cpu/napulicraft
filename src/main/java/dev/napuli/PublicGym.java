package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PublicGym {
    enum Machine {
        BIKE("cyclette", new BlockPos(355, 64, 8), 8, 10, 4, true),
        ROWER("vogatore", new BlockPos(355, 64, 11), 6, 14, 4, true),
        PRESS("panca_pesi", new BlockPos(351, 64, 11), 5, 16, 2, true),
        STEP("stepper", new BlockPos(350, 64, 7), 7, 10, 3, false),
        CHEST("chest_press", new BlockPos(351, 64, 8), 5, 16, 2, true),
        SHOULDER("shoulder_press", new BlockPos(354, 64, 7), 5, 16, 2, true);

        final String id;
        final BlockPos station;
        final int reps, ticks, fullness;
        final boolean seated;
        Block block;

        Machine(String id, BlockPos station, int reps, int ticks, int fullness, boolean seated) {
            this.id = id;
            this.station = station;
            this.reps = reps;
            this.ticks = ticks;
            this.fullness = fullness;
            this.seated = seated;
        }
        Component nameLabel() { return Component.translatable("block.napuli." + id); }
        Component help() { return Component.translatable("napuli.public_gym." + id); }
        boolean weighted() { return this == PRESS || this == CHEST || this == SHOULDER; }
        String rating() {
            return switch (this) {
                case BIKE, ROWER -> "stamina";
                case STEP -> "pace";
                case PRESS, CHEST, SHOULDER -> "strength";
            };
        }
        double seatHeight() {
            return switch (this) {
                case BIKE -> .79;
                case ROWER -> .40;
                case CHEST, SHOULDER -> .55;
                default -> .43;
            };
        }
        double seatDepth() { return this == BIKE ? .61 : this == ROWER ? .67 : .5; }
    }

    private record Workout(Machine machine, BlockPos pos, long started, long last, int reps, boolean previousInput,
                           Entity seat, int weight, boolean coached) {
        int interval() { return machine.ticks + Math.max(0, (weight - 10) / 5); }
    }
    private static final int START_WEIGHT = 10, TOP_WEIGHT = 100, WEIGHT_STEP = 5;
    private static final int DAYS_PER_STEP = 24, MIN_DAYS_BETWEEN_SESSIONS = 2;
    private static final AttachmentType<Map<String, Integer>> MAX_LOAD = loadAttachment("gym_public_max_load");
    private static final AttachmentType<Map<String, Integer>> SELECTED_LOAD = loadAttachment("gym_public_selected_load");
    private static final AttachmentType<Map<String, Integer>> LOAD_DAYS = loadAttachment("gym_public_load_days");
    private static final AttachmentType<Map<String, Long>> LAST_LOAD_DAY = AttachmentRegistry.<Map<String, Long>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath()
            .buildAndRegister(Napuli.id("gym_public_last_load_day"));

    private static AttachmentType<Map<String, Integer>> loadAttachment(String id) {
        return AttachmentRegistry.<Map<String, Integer>>builder()
                .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().buildAndRegister(Napuli.id(id));
    }
    private static final AttachmentType<Workout> ACTIVE = AttachmentRegistry.create(Napuli.id("gym_public_active"));
    private static final AttachmentType<Map<String, Long>> PAID = AttachmentRegistry.<Map<String, Long>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath().buildAndRegister(Napuli.id("gym_public_days"));
    private static final AttachmentType<Integer> SESSIONS = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT).copyOnDeath().buildAndRegister(Napuli.id("gym_public_sessions"));

    private PublicGym() {}

    static void register() {
        for (var machine : Machine.values()) {
            machine.block = NapoliFurniture.register(machine.id, properties -> new MachineBlock(properties, machine));
        }
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) ->
                dispatcher.register(Commands.literal("palestra").executes(command -> {
                    var player = command.getSource().getPlayerOrException();
                    NapoliNotice.show(player, Component.translatable("napuli.public_gym.profile", sessions(player)));
                    return 1;
                }).then(Commands.literal("vai").executes(command -> arrive(command.getSource().getPlayerOrException())))));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(PublicGym::tick));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                end(handler.getPlayer(), handler.getPlayer().getAttached(ACTIVE)));
    }

    private static int arrive(ServerPlayer p) {
        if (p.level().dimension() != NapoliMap.LEVEL || p.isSpectator() || p.isPassenger()
                || FootballPractice.isPlaying(p) || Carabinieri.isJailed(p)) return 0;
        p.teleportTo(p.level(), 354.5, 64, 13.5, java.util.Set.of(), 180, 0, true);
        NapoliNotice.show(p, Component.translatable("napuli.public_gym.profile", sessions(p)));
        return 1;
    }

    public static int sessions(ServerPlayer p) { return p.getAttachedOrElse(SESSIONS, 0); }

    public static int unlockedLoad(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockState(pos).getBlock() instanceof MachineBlock station && station.machine.weighted())
            return load(p, MAX_LOAD, station.machine);
        return 0;
    }

    private static boolean near(ServerPlayer p, BlockPos pos) {
        return pos.distToCenterSqr(p.position()) <= 9;
    }

    private static InteractionResult use(ServerPlayer p, BlockPos pos, Machine machine) {
        return use(p, pos, machine, false);
    }

    static int startCoachedStrength(ServerPlayer p) {
        var station = Machine.CHEST.station;
        if (!p.level().getBlockState(station).is(Machine.CHEST.block) || p.getAttached(ACTIVE) != null) return 0;
        use(p, station, Machine.CHEST, true);
        return p.getAttached(ACTIVE) != null ? 1 : 0;
    }

    private static InteractionResult use(ServerPlayer p, BlockPos pos, Machine machine, boolean coached) {
        if (p.isSpectator() || FootballPractice.isPlaying(p) || Carabinieri.isJailed(p)) return InteractionResult.PASS;
        var workout = p.getAttached(ACTIVE);
        if (workout != null) {
            if (workout.pos.equals(pos)) show(p, workout);
            return InteractionResult.SUCCESS;
        }
        if (p.isPassenger() || !near(p, pos)) return InteractionResult.PASS;
        if (machine.weighted() && p.isShiftKeyDown() && !coached) {
            selectNextLoad(p, machine);
            return InteractionResult.SUCCESS;
        }
        for (var other : p.level().players()) {
            var busy = other.getAttached(ACTIVE);
            if (busy != null && busy.pos.equals(pos)) {
                p.sendOverlayMessage(Component.translatable("napuli.public_gym.busy"));
                return InteractionResult.SUCCESS;
            }
        }
        if (machine.seated) {
            if (machine != Machine.PRESS)
                p.teleportTo(p.level(),p.getX(),p.getY(),p.getZ(),java.util.Set.of(),180,0,true);
            if (!CityMovement.sitAt(p, pos, machine.seatHeight(), machine.seatDepth())) return InteractionResult.SUCCESS;
        }
        FootballGym.clear(p);
        long now = p.level().getGameTime();
        var next = new Workout(machine, pos.immutable(), now, now, 0, p.getLastClientInput().jump(),
                machine.seated ? p.getVehicle() : null, machine.weighted() ? load(p, SELECTED_LOAD, machine) : 0, coached);
        p.setAttached(ACTIVE, next);
        if (machine == Machine.PRESS) FootballAction.play(p, FootballAction.Move.BENCH_IDLE, false);
        NapoliNotice.show(p, machine.help());
        show(p, next);
        return InteractionResult.SUCCESS;
    }

    private static void tick(ServerPlayer p) {
        var w = p.getAttached(ACTIVE);
        if (w == null) return;
        long now = p.level().getGameTime();
        if (!p.isAlive() || p.isSpectator() || !near(p, w.pos) || now - w.started > 600
                || p.level().dimension() != NapoliMap.LEVEL || !p.level().getBlockState(w.pos).is(w.machine.block)
                || w.machine.seated && p.getVehicle() != w.seat || FootballPractice.isPlaying(p)) {
            end(p, w);
            return;
        }
        if (w.reps >= w.machine.reps) {
            if (now - w.last >= w.interval()) complete(p, w);
            return;
        }
        var input = p.getLastClientInput();
        boolean jump = input.jump();
        boolean counting = switch (w.machine) {
            case BIKE -> input.forward();
            default -> jump && !w.previousInput;
        };
        if (w.previousInput != jump) {
            w = new Workout(w.machine, w.pos, w.started, w.last, w.reps, jump, w.seat, w.weight, w.coached);
            p.setAttached(ACTIVE, w);
        }
        if (w.machine == Machine.PRESS && now - w.last >= 16 && now % 8 == 0)
            FootballAction.play(p, FootballAction.Move.BENCH_IDLE, false);
        if (counting) rep(p, w, now);
        else if (now % 20 == 0) show(p, w);
    }

    private static void rep(ServerPlayer p, Workout w, long now) {
        if (now - w.last < w.interval()) return;
        if (w.machine == Machine.PRESS) FootballAction.play(p, FootballAction.Move.BENCH_PRESS, false);
        if (w.machine == Machine.CHEST) FootballAction.play(p, FootballAction.Move.CHEST_PRESS, false);
        if (w.machine == Machine.SHOULDER) FootballAction.play(p, FootballAction.Move.SHOULDER_PRESS, false);
        CityEffects.sound(p.level(),null, w.pos, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, .26f, 1.55f);
        CityEffects.particles(p.level(),p, ParticleTypes.CLOUD, false, false,
                w.pos.getX()+.5, w.pos.getY()+.4, w.pos.getZ()+.5, 3, .17, .1, .17, .02);
        int reps = w.reps + 1;
        if (reps < w.machine.reps || w.machine.weighted()) {
            var next = new Workout(w.machine, w.pos, w.started, now, reps, w.previousInput, w.seat, w.weight, w.coached);
            p.setAttached(ACTIVE, next);
            show(p, next);
            return;
        }
        complete(p, w);
    }

    private static void complete(ServerPlayer p, Workout w) {
        end(p, w);
        if (w.machine.weighted() && !p.isCreative()) trainLoad(p, w);
        var xp = FootballGym.completeMachine(p, w.machine.rating());
        if (w.coached) {
            p.sendOverlayMessage(xp);
            return;
        }
        long day = NapoliBroadcast.day(p.level());
        var paid = p.getAttachedOrElse(PAID, Map.of());
        if (p.isCreative() || paid.getOrDefault(w.machine.id, Long.MIN_VALUE) >= day) {
            p.sendOverlayMessage(xp);
            return;
        }

        var days = new HashMap<>(paid);
        days.put(w.machine.id, day);
        p.setAttached(PAID, Map.copyOf(days));
        p.setAttached(SESSIONS, sessions(p) + 1);
        int spent = Math.min(w.machine.fullness, NapoliStreet.fullness(p));
        NapoliStreet.addFullness(p, -spent);
        p.causeFoodExhaustion(.4f);
        p.sendOverlayMessage(xp);
    }

    private static void end(ServerPlayer p, Workout w) {
        if (w == null) return;
        p.removeAttached(ACTIVE);
        if (w.machine.weighted()) FootballAction.play(p, FootballAction.Move.BENCH_STOP, false);
        if (w.seat != null && p.getVehicle() == w.seat) p.stopRiding();
        if (w.seat != null) w.seat.discard();
    }

    private static void show(ServerPlayer p, Workout w) {
        var label = w.machine.nameLabel();
        if (w.machine.weighted()) label = Component.translatable("napuli.public_gym.weight_label", label, w.weight);
        p.sendOverlayMessage(Component.translatable("napuli.public_gym.progress", label, w.reps, w.machine.reps));
    }

    private static int load(ServerPlayer p, AttachmentType<Map<String, Integer>> type, Machine machine) {
        return p.getAttachedOrElse(type, Map.of()).getOrDefault(machine.id, START_WEIGHT);
    }

    private static void store(ServerPlayer p, AttachmentType<Map<String, Integer>> type, Machine machine, int value) {
        var entries = new HashMap<>(p.getAttachedOrElse(type, Map.of()));
        entries.put(machine.id, value);
        p.setAttached(type, Map.copyOf(entries));
    }

    private static void selectNextLoad(ServerPlayer p, Machine machine) {
        int max = load(p, MAX_LOAD, machine);
        int selected = load(p, SELECTED_LOAD, machine);
        int next = selected < max ? selected + WEIGHT_STEP : START_WEIGHT;
        store(p, SELECTED_LOAD, machine, next);
        NapoliNotice.show(p, Component.translatable("napuli.public_gym.weight_selected", next, max));
    }

    private static void trainLoad(ServerPlayer p, Workout w) {
        int max = load(p, MAX_LOAD, w.machine);
        if (w.weight != max || max >= TOP_WEIGHT) return;
        long day = NapoliBroadcast.day(p.level());
        var lastDays = new HashMap<>(p.getAttachedOrElse(LAST_LOAD_DAY, Map.of()));
        Long lastDay = lastDays.get(w.machine.id);
        if (lastDay != null && day < lastDay + MIN_DAYS_BETWEEN_SESSIONS) {
            NapoliNotice.show(p, Component.translatable("napuli.public_gym.weight_rest",
                    lastDay + MIN_DAYS_BETWEEN_SESSIONS - day));
            return;
        }
        lastDays.put(w.machine.id, day);
        p.setAttached(LAST_LOAD_DAY, Map.copyOf(lastDays));
        int days = p.getAttachedOrElse(LOAD_DAYS, Map.of()).getOrDefault(w.machine.id, 0) + 1;
        if (days < DAYS_PER_STEP) {
            store(p, LOAD_DAYS, w.machine, days);
            NapoliNotice.show(p, Component.translatable("napuli.public_gym.weight_training", days, DAYS_PER_STEP, max + WEIGHT_STEP));
            return;
        }
        store(p, LOAD_DAYS, w.machine, 0);
        store(p, MAX_LOAD, w.machine, max + WEIGHT_STEP);
        NapoliNotice.show(p, Component.translatable("napuli.public_gym.weight_unlocked", max + WEIGHT_STEP));
    }

    private static final class MachineBlock extends Block {
        private final Machine machine;
        MachineBlock(Properties properties, Machine machine) { super(properties); this.machine = machine; }

        @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player p, BlockHitResult hit) {
            if (p.isSpectator() || level.dimension() != NapoliMap.LEVEL) return InteractionResult.PASS;
            return p instanceof ServerPlayer server ? use(server, pos, machine) : InteractionResult.SUCCESS;
        }

        @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
                net.minecraft.world.level.BlockGetter level, BlockPos pos,
                net.minecraft.world.phys.shapes.CollisionContext context) {
            return box(0, 0, 0, 16, switch (machine) {
                case STEP -> 8;
                case PRESS -> 16;
                case ROWER -> 14;
                case BIKE -> 22;
                case CHEST, SHOULDER -> 25;
            }, 16);
        }

        @Override protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,
                net.minecraft.world.level.BlockGetter level, BlockPos pos,
                net.minecraft.world.phys.shapes.CollisionContext context) {
            return box(3, 0, 2, 13, machine == Machine.STEP ? 4 : 2, 14);
        }
    }
}
