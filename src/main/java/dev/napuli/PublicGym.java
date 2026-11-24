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
        PRESS("panca_pesi", new BlockPos(351, 64, 11), 5, 15, 2, false),
        STEP("stepper", new BlockPos(350, 64, 7), 7, 10, 3, false);

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
    }

    private record Workout(Machine machine, BlockPos pos, long started, long last, int reps, boolean previousInput, Entity seat) {}
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

    private static boolean near(ServerPlayer p, BlockPos pos) {
        return pos.distToCenterSqr(p.position()) <= 9;
    }

    private static InteractionResult use(ServerPlayer p, BlockPos pos, Machine machine) {
        if (p.isSpectator() || FootballPractice.isPlaying(p) || Carabinieri.isJailed(p)) return InteractionResult.PASS;
        var workout = p.getAttached(ACTIVE);
        if (workout != null) {
            if (workout.pos.equals(pos) && machine == Machine.PRESS && near(p, pos)) rep(p, workout, p.level().getGameTime());
            return InteractionResult.SUCCESS;
        }
        if (p.isPassenger() || !near(p, pos)) return InteractionResult.PASS;
        for (var other : p.level().players()) {
            var busy = other.getAttached(ACTIVE);
            if (busy != null && busy.pos.equals(pos)) {
                p.sendOverlayMessage(Component.translatable("napuli.public_gym.busy"));
                return InteractionResult.SUCCESS;
            }
        }
        if (machine.seated && !CityMovement.sitAt(p, pos)) return InteractionResult.SUCCESS;
        FootballGym.clear(p);
        long now = p.level().getGameTime();
        p.setAttached(ACTIVE, new Workout(machine, pos.immutable(), now, now, 0,
                p.getLastClientInput().jump(), machine.seated ? p.getVehicle() : null));
        NapoliNotice.show(p, machine.help());
        show(p, machine, 0);
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
        var input = p.getLastClientInput();
        boolean jump = input.jump();
        boolean counting = switch (w.machine) {
            case BIKE -> input.forward();
            case ROWER, STEP -> jump && !w.previousInput;
            case PRESS -> false;
        };
        if (w.previousInput != jump) {
            w = new Workout(w.machine, w.pos, w.started, w.last, w.reps, jump, w.seat);
            p.setAttached(ACTIVE, w);
        }
        if (counting) rep(p, w, now);
        else if (now % 20 == 0) show(p, w.machine, w.reps);
    }

    private static void rep(ServerPlayer p, Workout w, long now) {
        if (now - w.last < w.machine.ticks) return;
        CityEffects.sound(p.level(),null, w.pos, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, .26f, 1.55f);
        CityEffects.particles(p.level(),p, ParticleTypes.CLOUD, false, false,
                w.pos.getX()+.5, w.pos.getY()+.4, w.pos.getZ()+.5, 3, .17, .1, .17, .02);
        int reps = w.reps + 1;
        if (reps < w.machine.reps) {
            p.setAttached(ACTIVE, new Workout(w.machine, w.pos, w.started, now, reps, w.previousInput, w.seat));
            show(p, w.machine, reps);
            return;
        }
        end(p, w);
        long day = NapoliBroadcast.day(p.level());
        var paid = p.getAttachedOrElse(PAID, Map.of());
        if (p.isCreative() || paid.getOrDefault(w.machine.id, Long.MIN_VALUE) >= day) {
            p.sendOverlayMessage(Component.translatable("napuli.public_gym.practice"));
            return;
        }

        var days = new HashMap<>(paid);
        days.put(w.machine.id, day);
        p.setAttached(PAID, Map.copyOf(days));
        p.setAttached(SESSIONS, sessions(p) + 1);
        int spent = Math.min(w.machine.fullness, NapoliStreet.fullness(p));
        NapoliStreet.addFullness(p, -spent);
        p.causeFoodExhaustion(.4f);
        p.sendOverlayMessage(Component.translatable("napuli.public_gym.done", w.machine.nameLabel(), spent));
    }

    private static void end(ServerPlayer p, Workout w) {
        if (w == null) return;
        p.removeAttached(ACTIVE);
        if (w.seat != null && p.getVehicle() == w.seat) p.stopRiding();
        if (w.seat != null) w.seat.discard();
    }

    private static void show(ServerPlayer p, Machine machine, int reps) {
        p.sendOverlayMessage(Component.translatable("napuli.public_gym.progress", machine.nameLabel(), reps, machine.reps));
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
            return box(1, 0, 1, 15, machine == Machine.STEP ? 5 : 12, 15);
        }
    }
}
