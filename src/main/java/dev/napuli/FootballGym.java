package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

public final class FootballGym {
    private static final int SESSION_TICKS = 1200;
    private static final int XP_PER_POINT = 100;
    private static final BlockPos BENCH = new BlockPos(351, 64, 8);
    private static final BlockPos FIRING_LINE = new BlockPos(355, 64, 37);
    private static final List<BlockPos> TARGETS = List.of(new BlockPos(353, 65, 40),
            new BlockPos(357, 65, 40), new BlockPos(355, 66, 40));

    enum Drill {
        SPRINT("sprint", "pace", List.of(lane(354, 23), lane(354, 28), lane(354, 35)), 16),
        ENDURANCE("resistenza", "stamina", List.of(lane(354, 26), lane(354, 35), lane(354, 26),
                lane(354, 19), lane(354, 26), lane(354, 35)), 90),
        AGILITY("slalom", "defense", List.of(lane(353, 22), lane(356, 26), lane(353, 30), lane(356, 34)), 30),
        STRENGTH("forza", "strength", java.util.Collections.nCopies(4, BENCH), 48),
        ACCURACY("mira", "shooting", List.of(TARGETS.get(0), TARGETS.get(2), TARGETS.get(1), TARGETS.get(2)), 18);

        final String id, rating;
        final List<BlockPos> checkpoints;
        final int minimumTicks;
        Drill(String id, String rating, List<BlockPos> checkpoints, int minimumTicks) {
            this.id = id;
            this.rating = rating;
            this.checkpoints = checkpoints;
            this.minimumTicks = minimumTicks;
        }
        boolean movement() { return this != STRENGTH && this != ACCURACY; }
        BlockPos start() {
            if (this == STRENGTH) return BENCH.south();
            if (this == ACCURACY) return FIRING_LINE;
            return FootballGround.START_RUN;
        }
        Component label() { return Component.translatable("napuli.gym." + id); }
    }

    private record Session(Drill drill, int step, long started, long last, Vec3 previous) {}
    private static final Attachments ATTACHMENTS = new Attachments();
    private static final class Attachments {
        final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Session> session =
                AttachmentRegistry.create(Napuli.id("gym_session"));
        final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Map<String, Integer>> gains =
                AttachmentRegistry.<Map<String, Integer>>builder()
                        .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().buildAndRegister(Napuli.id("gym_gains"));
        final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Map<String, Integer>> experience =
                AttachmentRegistry.<Map<String, Integer>>builder()
                        .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().buildAndRegister(Napuli.id("gym_stat_xp"));
    }

    private FootballGym() {}

    private static BlockPos lane(int x, int z) { return new BlockPos(x, 64, z); }

    static int bonus(ServerPlayer p, String rating) {
        return p.getAttachedOrElse(ATTACHMENTS.gains, Map.of()).getOrDefault(rating, 0)
                + totalXp(p, rating) / XP_PER_POINT;
    }

    public static int progressXp(ServerPlayer p, String rating) { return totalXp(p, rating) % XP_PER_POINT; }

    private static int totalXp(ServerPlayer p, String rating) {
        return p.getAttachedOrElse(ATTACHMENTS.experience, Map.of()).getOrDefault(rating, 0);
    }

    static int earnXp(ServerPlayer p, String rating) {
        int before = FootballPractice.rating(p, rating);
        if (before == 99) return 0;
        int earned = Math.min(FootballPractice.primaryRating(p, rating) ? 40 : 25,
                before == 98 ? XP_PER_POINT - progressXp(p, rating) : XP_PER_POINT);
        var xp = new HashMap<>(p.getAttachedOrElse(ATTACHMENTS.experience, Map.of()));
        xp.merge(rating, earned, Integer::sum);
        p.setAttached(ATTACHMENTS.experience, Map.copyOf(xp));
        return earned;
    }

    static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != NapoliMap.LEVEL || hand != InteractionHand.MAIN_HAND || player.isSpectator())
                return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;
            var session = server.getAttached(ATTACHMENTS.session);
            if (session == null || session.drill.movement()) return InteractionResult.PASS;
            var at = hit.getBlockPos();
            if (!session.drill.checkpoints.contains(at)) return InteractionResult.PASS;
            if (at.equals(session.drill.checkpoints.get(session.step))
                    && (session.drill != Drill.ACCURACY || near(server, FIRING_LINE, 2.1)))
                advance(server, session, level.getGameTime());
            return InteractionResult.SUCCESS;
        });
    }

    static void menu(ServerPlayer player) {
        var buttons = java.util.Arrays.stream(Drill.values()).map(drill -> {
            int rating = FootballPractice.rating(player, drill.rating);
            var title = rating == 99
                    ? Component.translatable("napuli.gym.at_cap", drill.label())
                    : Component.translatable("napuli.gym.with_xp", drill.label(), progressXp(player, drill.rating));
            return new ActionButton(new CommonButtonData(title,
                    Optional.of(Component.translatable("napuli.gym." + drill.id + ".tip")), 142),
                    Optional.of(new StaticAction(new ClickEvent.RunCommand("/calcio esercizio " + drill.id))));
        }).toList();
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(
                Component.translatable("napuli.gym.title"), Optional.empty(), true, false, DialogAction.CLOSE,
                List.of(new PlainMessage(Component.translatable("napuli.gym.intro"), 310)), List.of()),
                buttons, Optional.empty(), 2)));
    }

    static int start(ServerPlayer player, Drill drill) {
        if (player.level().dimension() != NapoliMap.LEVEL || player.isSpectator() || player.isPassenger()
                || Carabinieri.isJailed(player) || FootballPractice.isPlaying(player)
                || !near(player, FootballGround.START_RUN, 28)) return 0;
        BlockPos start = drill.start();
        player.teleportTo(player.level(), start.getX() + .5, start.getY(), start.getZ() + .5,
                java.util.Set.of(), drill == Drill.STRENGTH ? 180 : 0, 0, true);
        long now = player.level().getGameTime();
        player.setAttached(ATTACHMENTS.session, new Session(drill, 0, now, now, player.position()));
        NapoliNotice.show(player, Component.translatable("napuli.gym." + drill.id + ".tip"));
        instruction(player, drill, 0);
        return 1;
    }

    static void tick(ServerPlayer player) {
        var session = player.getAttached(ATTACHMENTS.session);
        if (session == null) return;
        long now = player.level().getGameTime();
        if (player.level().dimension() != NapoliMap.LEVEL || FootballPractice.isPlaying(player)
                || !player.isAlive() || player.isSpectator() || player.isPassenger()
                || now - session.started > SESSION_TICKS || !near(player, FootballGround.START_RUN, 30)
                || session.previous.distanceToSqr(player.position()) > 16) {
            player.removeAttached(ATTACHMENTS.session);
            player.sendOverlayMessage(Component.translatable("napuli.gym.cancelled"));
            return;
        }
        var target = session.drill.checkpoints.get(session.step);
        if (session.drill.movement() && near(player, target, 1)
                && (session.drill != Drill.SPRINT || player.isSprinting())) {
            advance(player, session, now);
            return;
        }
        if (now % 10 == 0) {
            CityEffects.particles(player.level(),player, ParticleTypes.HAPPY_VILLAGER, false, false,
                    target.getX() + .5, target.getY() + .45, target.getZ() + .5, 5, .16, .16, .16, .01);
            instruction(player, session.drill, session.step);
        }
        if (!session.previous.equals(player.position()))
            player.setAttached(ATTACHMENTS.session, new Session(session.drill, session.step, session.started, session.last, player.position()));
    }

    static void clear(ServerPlayer player) { player.removeAttached(ATTACHMENTS.session); }

    private static void advance(ServerPlayer p, Session s, long now) {
        int delay = s.drill == Drill.STRENGTH ? 12 : s.drill == Drill.ACCURACY ? 6 : 3;
        int next = s.step + 1;
        if (now - s.last < delay || next == s.drill.checkpoints.size() && now - s.started < s.drill.minimumTicks) return;
        CityEffects.sound(p.level(),null, p.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, .45f, 1.4f);
        if (next < s.drill.checkpoints.size()) {
            p.setAttached(ATTACHMENTS.session, new Session(s.drill, next, s.started, now, p.position()));
            instruction(p, s.drill, next);
            return;
        }
        clear(p);
        int before = FootballPractice.rating(p, s.drill.rating);
        int earned = p.isCreative() ? 0 : earnXp(p, s.drill.rating);
        int after = FootballPractice.rating(p, s.drill.rating);
        if (earned == 0) {
            p.sendOverlayMessage(Component.translatable("napuli.gym.complete_practice"));
        } else if (after > before) {
            p.sendOverlayMessage(Component.translatable("napuli.gym.level_up", s.drill.label(), after));
        } else {
            p.sendOverlayMessage(Component.translatable("napuli.gym.xp_earned", earned,
                    progressXp(p, s.drill.rating), XP_PER_POINT));
        }
    }

    private static void instruction(ServerPlayer player, Drill drill, int step) {
        player.sendOverlayMessage(Component.translatable("napuli.gym.progress", drill.label(), step, drill.checkpoints.size()));
    }

    private static boolean near(ServerPlayer player, BlockPos pos, double distance) {
        return pos.distToCenterSqr(player.position()) <= distance * distance;
    }
}
