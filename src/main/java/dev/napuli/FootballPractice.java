package dev.napuli;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FootballPractice {
    private static final int PLAYER_SLOT = 4, MATCH_TICKS = 20 * 150;
    private static final String ACTOR = "napuli_practice_actor", BALL = "napuli_practice_ball";
    private static final AABB ARENA = new AABB(360, 62, 5, 412, 74, 42);
    private static final String[] NAMES = {"Russo", "Greco", "Marino", "Esposito", "Vitale",
            "Riva", "Serra", "Costa", "Leone", "Barbieri"};
    private static final double[] DEPTH = {2, 10, 16, 16, 19};
    private static final int[] WIDTH = {0, 0, -7, 7, 0};
    private static final WeakHashMap<ServerLevel, Game> GAMES = new WeakHashMap<>();
    private static final AttachmentType<Career> CAREER = AttachmentRegistry.<Career>builder()
            .persistent(Career.CODEC).copyOnDeath().buildAndRegister(Napuli.id("football_career"));
    private static final AttachmentType<Long> RUN_STARTED = AttachmentRegistry.create(Napuli.id("football_training_run"));

    private FootballPractice() {}

    public record MatchStats(int passes, int shots, int interceptions, double playerKickDistance) {}

    public static MatchStats statistics(ServerLevel level) {
        Game game = GAMES.get(level);
        return game == null ? new MatchStats(0, 0, 0, 0)
                : new MatchStats(game.passes, game.shots, game.interceptions, game.playerKickDistance);
    }

    public static int fitness(ServerPlayer player) { return career(player).fitness(); }

    private record Career(int matches, int goals, int fitness, long trainingDay) {
        static final Career BEGINNER = new Career(0, 0, 0, Long.MIN_VALUE);
        static final Codec<Career> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("matches").forGetter(Career::matches),
                Codec.INT.fieldOf("goals").forGetter(Career::goals),
                Codec.INT.fieldOf("fitness").forGetter(Career::fitness),
                Codec.LONG.fieldOf("training_day").forGetter(Career::trainingDay)).apply(i, Career::new));
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, kind) -> dispatcher.register(
                Commands.literal("calcio").executes(c -> profile(c.getSource().getPlayerOrException()))
                        .then(Commands.literal("guarda").executes(c -> enter(c.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("gioca").executes(c -> enter(c.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("esci").executes(c -> exit(c.getSource().getPlayerOrException())))
                        .then(kickCommand("passa", false)).then(kickCommand("tira", true))
                        .then(Commands.literal("palestra").executes(c -> gym(c.getSource().getPlayerOrException())))));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GAMES.entrySet().removeIf(entry -> {
            if (entry.getKey().getServer() != server) return false;
            entry.getValue().removeActors();
            return true;
        }));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var city = server.getLevel(NapoliMap.LEVEL);
            Game game = city == null ? null : GAMES.get(city);
            if (game != null && !game.tick(city)) GAMES.remove(city);
            if (city != null) for (ServerPlayer player : city.players()) training(player, city);
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer server) || level.dimension() != NapoliMap.LEVEL
                    || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
            Game game = GAMES.get(server.level());
            if (game == null || !game.controls(server)) return InteractionResult.PASS;
            game.playerKick(server, player.isShiftKeyDown());
            return InteractionResult.SUCCESS;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> kickCommand(String name, boolean shoot) {
        return Commands.literal(name).executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            Game game = GAMES.get(player.level());
            return game != null && game.playerKick(player, shoot) ? 1 : 0;
        });
    }

    private static int enter(ServerPlayer player, boolean play) {
        if (Carabinieri.isJailed(player) || player.isSpectator()) return 0;
        if (player.level().dimension() != NapoliMap.LEVEL && NapoliMap.visit(player) == 0) return 0;
        ServerLevel city = player.level();
        Game game = GAMES.computeIfAbsent(city, Game::new);
        if (play && game.player != null && !game.controls(player)) {
            say(player, "busy");
            return 0;
        }
        if (play) {
            if (!game.controls(player)) game.substitute(player);
            say(player, "controls");
        } else {
            if (game.controls(player)) game.restoreNpc(city);
            teleport(player, FootballGround.STAND, 0);
            say(player, "watch");
        }
        return 1;
    }

    private static int exit(ServerPlayer player) {
        Game game = GAMES.get(player.level());
        if (game == null || !game.controls(player)) return 0;
        game.restoreNpc(player.level());
        teleport(player, FootballGround.STAND, 0);
        return 1;
    }

    private static int gym(ServerPlayer player) {
        if (Carabinieri.isJailed(player) || player.isSpectator()) return 0;
        if (player.level().dimension() != NapoliMap.LEVEL && NapoliMap.visit(player) == 0) return 0;
        Game game = GAMES.get(player.level());
        if (game != null && game.controls(player)) game.restoreNpc(player.level());
        teleport(player, FootballGround.START_RUN, 0);
        player.removeAttached(RUN_STARTED);
        say(player, "train");
        return 1;
    }

    private static void training(ServerPlayer player, ServerLevel city) {
        long now = city.getGameTime();
        Long start = player.getAttached(RUN_STARTED);
        if (player.blockPosition().distSqr(FootballGround.START_RUN) <= 1 && start == null) {
            player.setAttached(RUN_STARTED, now);
        } else if (start != null && (now - start > 160 || Math.abs(player.getX() - 354.5) > 2 || player.getY() > 65.5)) {
            player.removeAttached(RUN_STARTED);
        } else if (start != null && player.blockPosition().distSqr(FootballGround.FINISH_RUN) <= 1) {
            player.removeAttached(RUN_STARTED);
            if (now - start < 30 || player.isCreative() || player.isSpectator()) return;
            Career career = career(player);
            long day = NapoliBroadcast.day(city);
            if (day <= career.trainingDay() || career.fitness() >= 10) {
                say(player, "rest");
                return;
            }
            player.setAttached(CAREER, new Career(career.matches(), career.goals(), career.fitness() + 1, day));
            say(player, "trained", career.fitness() + 1);
        }
    }

    private static Career career(ServerPlayer p) { return p.getAttachedOrElse(CAREER, Career.BEGINNER); }

    private static int profile(ServerPlayer p) {
        Career c = career(p);
        say(p, "profile", c.matches(), c.goals(), c.fitness());
        return 1;
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        NapoliNotice.show(p, Component.translatable("napuli.football." + key, args));
    }

    private static void teleport(ServerPlayer player, BlockPos pos, float yaw) {
        player.teleportTo(player.level(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, Set.of(), yaw, 0, true);
    }

    private static Vec3 spot(int slot) {
        int role = slot % 5, direction = slot < 5 ? 1 : -1;
        int goal = slot < 5 ? FootballGround.MIN_X : FootballGround.MAX_X;
        return new Vec3(goal + direction * DEPTH[role] + .5, 64, FootballGround.MID_Z + direction * WIDTH[role] + .5);
    }

    private static final class Game {
        final NapoliResident[] team = new NapoliResident[10];
        final ItemEntity marker;
        java.util.UUID player;
        Vec3 ball = spot(PLAYER_SLOT).add(.55, 0, 0), velocity = Vec3.ZERO;
        int ticks, unattended, home, away, owner = -1, kicker = -1, pickupDelay, held, playerGoals;
        int passes, shots, interceptions;
        double playerKickDistance;

        Game(ServerLevel city) {
            for (int x = 352; x <= 416; x += 16) for (int z = 0; z <= 48; z += 16)
                city.getChunkAt(new BlockPos(x, 64, z));
            for (var npc : city.getEntitiesOfClass(NapoliResident.class, ARENA, n -> n.entityTags().contains(ACTOR))) npc.discard();
            for (var item : city.getEntitiesOfClass(ItemEntity.class, ARENA, n -> n.entityTags().contains(BALL))) item.discard();
            for (int i = 0; i < team.length; i++) spawn(city, i);
            marker = new ItemEntity(city, ball.x, 64.15, ball.z, new ItemStack(Items.SNOWBALL), 0, 0, 0);
            marker.addTag(BALL);
            marker.setNoGravity(true);
            marker.setPermanentlyInvulnerable(true);
            marker.setNeverPickUp();
            city.addFreshEntity(marker);
            setOwner(4);
        }

        private void spawn(ServerLevel city, int slot) {
            var npc = NapoliResident.spawn(city, NapoliResident.Role.PASSERBY, BlockPos.containing(spot(slot)), slot < 5 ? -90 : 90);
            npc.addTag(ACTOR);
            npc.setNoAi(true);
            npc.setPermanentlyInvulnerable(true);
            npc.setCustomName(Component.literal(NAMES[slot]));
            npc.setCustomNameVisible(true);
            var shirt = new ItemStack(Items.LEATHER_CHESTPLATE);
            shirt.set(DataComponents.DYED_COLOR, new DyedItemColor(slot % 5 == 0 ? 0xe1a538 : slot < 5 ? 0x319bdb : 0xf4eeee));
            npc.setItemSlot(EquipmentSlot.CHEST, shirt);
            team[slot] = npc;
        }

        boolean controls(ServerPlayer viewer) { return viewer.getUUID().equals(player); }

        void substitute(ServerPlayer p) {
            player = p.getUUID();
            Vec3 position = team[PLAYER_SLOT] == null ? spot(PLAYER_SLOT) : team[PLAYER_SLOT].position();
            if (team[PLAYER_SLOT] != null) team[PLAYER_SLOT].discard();
            team[PLAYER_SLOT] = null;
            teleport(p, BlockPos.containing(position), -90);
            if (owner == PLAYER_SLOT) held = 0;
        }

        void restoreNpc(ServerLevel city) {
            player = null;
            playerGoals = 0;
            spawn(city, PLAYER_SLOT);
        }

        private ServerPlayer participant(ServerLevel city) {
            return player == null ? null : city.getServer().getPlayerList().getPlayer(player);
        }

        private Vec3 position(int index, ServerPlayer participant) {
            if (index == PLAYER_SLOT && participant != null) return participant.position();
            return team[index] == null ? spot(index) : team[index].position();
        }

        boolean playerKick(ServerPlayer p, boolean shoot) {
            if (!controls(p) || owner != PLAYER_SLOT || !inside(p.position())) return false;
            Vec3 facing = flat(p.getLookAngle()).normalize();
            if (facing.lengthSqr() < .01) return false;
            if (shoot) {
                shots++;
                kick(facing, .88 + fitness(p) * .028);
            } else {
                Vec3 target = null;
                double best = .30;
                for (int i = 0; i < PLAYER_SLOT; i++) {
                    Vec3 to = position(i, p).subtract(ball);
                    double alignment = flat(to).normalize().dot(facing);
                    if (alignment <= best || to.lengthSqr() > 24 * 24) continue;
                    best = alignment;
                    target = position(i, p);
                }
                if (target == null) { passes++; kick(facing, .64); }
                else pass(target);
            }
            return true;
        }

        boolean tick(ServerLevel city) {
            ServerPlayer participant = participant(city);
            if (player != null && (participant == null || participant.level() != city || !participant.isAlive()
                    || !ARENA.contains(participant.position()) || Carabinieri.isJailed(participant))) {
                restoreNpc(city);
                participant = null;
            }
            boolean audience = false;
            for (ServerPlayer p : city.players()) if (ARENA.contains(p.position())) { audience = true; break; }
            unattended = audience ? 0 : unattended + 1;
            if (unattended > 100 || ticks++ >= MATCH_TICKS) {
                if (audience) finish(city, participant);
                removeActors();
                return false;
            }
            if (!audience) return true;
            driveTeams(participant);
            playBall(participant);
            if (pickupDelay > 0) pickupDelay--;
            marker.setPos(ball.x, 64.15, ball.z);
            marker.setDeltaMovement(Vec3.ZERO);
            if (ticks % 20 == 0) hud(city, participant);
            return true;
        }

        private void removeActors() {
            for (var npc : team) if (npc != null) npc.discard();
            marker.discard();
        }

        private void driveTeams(ServerPlayer participant) {
            int first = nearest(0, participant), second = nearest(5, participant);
            for (int slot = 0; slot < team.length; slot++) {
                var npc = team[slot];
                if (npc == null) continue;
                Vec3 base = spot(slot), pos = npc.position(), target;
                int sign = slot < 5 ? 1 : -1;
                boolean goalkeeper = slot % 5 == 0;
                if (slot == owner) target = new Vec3(pos.x + 5 * sign, 64, FootballGround.MID_Z + .5);
                else if (goalkeeper) {
                    double reach = (ball.x - base.x) * sign;
                    target = reach >= 0 && reach < 5 && owner < 0 ? ball
                            : new Vec3(base.x, 64, Math.clamp(ball.z, base.z - 2.4, base.z + 2.4));
                } else if ((slot == first || slot == second) && (owner < 0 || (slot < 5) != (owner < 5))) target = ball;
                else target = base.add((ball.x - FootballGround.MID_X) * .34, 0, (ball.z - FootballGround.MID_Z) * .30);
                target = new Vec3(Math.clamp(target.x, FootballGround.MIN_X + 1, FootballGround.MAX_X),
                        64, Math.clamp(target.z, FootballGround.MIN_Z + 1, FootballGround.MAX_Z));
                Vec3 delta = target.subtract(pos);
                double distance = delta.horizontalDistance();
                if (distance > .08) {
                    Vec3 move = flat(delta).scale(Math.min(slot == owner ? .16 : .23, distance) / distance);
                    npc.snapTo(pos.x + move.x, 64, pos.z + move.z,
                            (float) Math.toDegrees(Math.atan2(-move.x, move.z)), 0);
                }
            }
        }

        private int nearest(int first, ServerPlayer participant) {
            int closest = first + 1;
            for (int i = first + 2; i < first + 5; i++)
                if (flat(position(i, participant).subtract(ball)).lengthSqr()
                        < flat(position(closest, participant).subtract(ball)).lengthSqr()) closest = i;
            return closest;
        }

        private void playBall(ServerPlayer participant) {
            if (owner >= 0) {
                Vec3 pos = position(owner, participant);
                int direction = owner < 5 ? 1 : -1;
                ball = new Vec3(pos.x + direction * .55, 64, pos.z);
                held++;
                for (int i = owner < 5 ? 5 : 0; i < (owner < 5 ? 10 : 5); i++) {
                    if (held > 18 && ball.distanceToSqr(position(i, participant)) < .62) {
                        interceptions++;
                        setOwner(i);
                        return;
                    }
                }
                if (owner != PLAYER_SLOT || participant == null) {
                    if (held >= 8) npcKick(participant);
                } else if (!inside(pos)) {
                    setOwner(-1);
                }
                return;
            }
            Vec3 previous = ball;
            ball = ball.add(velocity);
            if (kicker == PLAYER_SLOT && participant != null) playerKickDistance += velocity.horizontalDistance();
            velocity = velocity.scale(.963);
            if (!inside(ball)) {
                int edge = ball.x <= FootballGround.MIN_X + .5 ? FootballGround.MIN_X : FootballGround.MAX_X;
                double line = edge + .5;
                boolean crossed = (previous.x - line) * (ball.x - line) <= 0 && ball.x != previous.x;
                double z = crossed ? previous.z + (ball.z - previous.z) * (line - previous.x) / (ball.x - previous.x) : ball.z;
                if (crossed && Math.abs(z - FootballGround.MID_Z - .5) < 2.65) {
                    if (edge == FootballGround.MAX_X) { home++; if (kicker == PLAYER_SLOT && player != null) playerGoals++; }
                    else away++;
                    ball = mid();
                    setOwner(edge == FootballGround.MAX_X ? 9 : 4);
                } else {
                    ball = new Vec3(Math.clamp(ball.x, FootballGround.MIN_X + 1, FootballGround.MAX_X),
                            64, Math.clamp(ball.z, FootballGround.MIN_Z + 1, FootballGround.MAX_Z));
                    velocity = Vec3.ZERO;
                    kicker = -1;
                }
                return;
            }
            if (pickupDelay >= 5) return;
            int best = -1;
            double closest = .90;
            for (int i = 0; i < team.length; i++) {
                if (pickupDelay > 0 && i == kicker) continue;
                double distance = segmentDistance(position(i, participant), previous, ball);
                if (distance < closest) { closest = distance; best = i; }
            }
            if (best >= 0) {
                if (kicker >= 0 && (best < 5) != (kicker < 5)) interceptions++;
                setOwner(best);
            }
        }

        private void npcKick(ServerPlayer participant) {
            int sign = owner < 5 ? 1 : -1, goal = owner < 5 ? FootballGround.MAX_X + 1 : FootballGround.MIN_X;
            double distance = (goal - ball.x) * sign;
            if (distance < 10 && owner % 5 != 0) {
                Vec3 target = new Vec3(goal, 64, FootballGround.MID_Z + (ticks % 2 == 0 ? -1.6 : 2.4));
                shots++;
                kick(target.subtract(ball), .98);
                return;
            }
            Vec3 target = null;
            double best = -1;
            for (int i = owner < 5 ? 0 : 5; i < (owner < 5 ? 5 : 10); i++) {
                if (i == owner) continue;
                Vec3 receiver = position(i, participant), delta = receiver.subtract(ball);
                double length = delta.horizontalDistance();
                if (length < 2 || length > 23) continue;
                double score = delta.x * sign - length * .22;
                for (int foe = owner < 5 ? 5 : 0; foe < (owner < 5 ? 10 : 5); foe++)
                    if (segmentDistance(position(foe, participant), ball, receiver) < 2.25) score -= 4;
                if (score > best) { target = receiver; best = score; }
            }
            if (target != null) pass(target);
            else if (held >= 45) kick(new Vec3(sign, 0, (FootballGround.MID_Z + .5 - ball.z) * .16), .65);
        }

        private void pass(Vec3 target) {
            passes++;
            Vec3 direction = target.subtract(ball);
            kick(direction, Math.clamp(direction.horizontalDistance() * .045 + .28, .48, .87));
        }

        private void kick(Vec3 direction, double speed) {
            kicker = owner;
            owner = -1;
            held = 0;
            pickupDelay = 6;
            velocity = flat(direction).normalize().scale(speed);
        }

        private void setOwner(int next) {
            owner = next;
            held = 0;
            kicker = -1;
            velocity = Vec3.ZERO;
        }

        private void hud(ServerLevel city, ServerPlayer participant) {
            int seconds = (MATCH_TICKS - ticks + 19) / 20;
            String clock = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
            Component score = Component.translatable("napuli.football.score", home, away, clock);
            for (ServerPlayer p : city.players()) {
                if (!ARENA.contains(p.position())) continue;
                p.sendOverlayMessage(p == participant && owner == PLAYER_SLOT ? score.copy().append("  ·  ")
                        .append(Component.translatable("napuli.football.possession")) : score);
            }
        }

        private void finish(ServerLevel city, ServerPlayer participant) {
            if (participant != null) {
                Career c = career(participant);
                participant.setAttached(CAREER, new Career(c.matches() + 1, c.goals() + playerGoals, c.fitness(), c.trainingDay()));
                teleport(participant, FootballGround.STAND, 0);
            }
            for (ServerPlayer p : city.players()) if (ARENA.contains(p.position())) say(p, "final", home, away);
        }
    }

    private static Vec3 mid() { return new Vec3(FootballGround.MID_X + .5, 64, FootballGround.MID_Z + .5); }
    private static Vec3 flat(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    private static boolean inside(Vec3 pos) {
        return pos.x >= FootballGround.MIN_X + .5 && pos.x <= FootballGround.MAX_X + .5
                && pos.z >= FootballGround.MIN_Z + .5 && pos.z <= FootballGround.MAX_Z + .5;
    }

    private static double segmentDistance(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = flat(end.subtract(start)), offset = flat(point.subtract(start));
        double length = segment.lengthSqr();
        double progress = length == 0 ? 0 : Math.clamp(offset.dot(segment) / length, 0, 1);
        return offset.subtract(segment.scale(progress)).lengthSqr();
    }
}
