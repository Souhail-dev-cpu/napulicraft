package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class CityHeists {
    private record Target(String id, BlockPos position, int payout, int seconds, boolean bank) {}
    private record Attempt(UUID player, Target target, long ends) {}
    private static final List<Target> TARGETS = List.of(
            new Target("bank_west", new BlockPos(381, 64, -12), 90, 12, true),
            new Target("bank_east", new BlockPos(397, 64, -12), 120, 16, true),
            new Target("bar", NapoliMap.CAFE, 14, 5, false),
            new Target("pizzeria", NapoliInteriors.PIZZERIA, 19, 6, false),
            new Target("market", new BlockPos(72, 64, -125), 24, 7, false));
    private static final AttachmentType<Map<String, Long>> COOLDOWNS = AttachmentRegistry.<Map<String, Long>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).buildAndRegister(Napuli.id("heist_cooldowns"));
    private static final Map<ServerLevel, Map<String, Attempt>> RUNNING = new java.util.WeakHashMap<>();

    private CityHeists() {}

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, kind) -> dispatcher.register(
                Commands.literal("colpo").executes(c -> start(c.getSource().getPlayerOrException()))));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != NapoliMap.LEVEL || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown())
                return InteractionResult.PASS;
            var target = TARGETS.stream().anyMatch(t -> t.bank() && t.position().equals(hit.getBlockPos()));
            if (!target) return InteractionResult.PASS;
            if (player instanceof ServerPlayer server) start(server);
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            ServerLevel city = server.getLevel(NapoliMap.LEVEL);
            if (city != null) tick(city);
        });
    }

    private static boolean near(ServerPlayer player, Target target) {
        return target.position().distToCenterSqr(player.position()) <= 25;
    }

    private static int start(ServerPlayer player) {
        if (player.level().dimension() != NapoliMap.LEVEL || !player.isAlive() || player.isSpectator()
                || Carabinieri.isJailed(player)) return 0;
        ServerLevel city = player.level();
        Target target = TARGETS.stream().filter(t -> near(player, t))
                .min(java.util.Comparator.comparingDouble(t -> t.position().distToCenterSqr(player.position())))
                .orElse(null);
        if (target == null) {
            notice(player, "where");
            return 0;
        }
        long now = city.getGameTime();
        var active = RUNNING.computeIfAbsent(city, ignored -> new HashMap<>());
        if (active.containsKey(target.id()) || active.values().stream().anyMatch(a -> a.player().equals(player.getUUID()))
                || city.getAttachedOrElse(COOLDOWNS, Map.of()).getOrDefault(target.id(), 0L) > now) {
            notice(player, "locked");
            return 0;
        }
        var cooldowns = new HashMap<>(city.getAttachedOrElse(COOLDOWNS, Map.of()));
        cooldowns.put(target.id(), now + 20L * (target.bank() ? 300 : 120));
        city.setAttached(COOLDOWNS, Map.copyOf(cooldowns));
        active.put(target.id(), new Attempt(player.getUUID(), target, now + target.seconds() * 20L));
        if (!target.bank()) Carabinieri.crime(player, Carabinieri.Crime.THEFT);
        Music.play(player, Music.Situation.FURTO);
        CityEffects.sound(city,null, target.position(), Napuli.URLO, SoundSource.NEUTRAL, .6f, target.bank() ? .85f : 1.12f);
        notice(player, "started", target.seconds());
        return 1;
    }

    private static void tick(ServerLevel city) {
        var attempts = RUNNING.get(city);
        if (attempts == null) return;
        var iterator = attempts.values().iterator();
        while (iterator.hasNext()) {
            Attempt a = iterator.next();
            ServerPlayer p = city.getServer().getPlayerList().getPlayer(a.player());
            if (p == null || p.level() != city || !p.isAlive() || p.isSpectator() || Carabinieri.isJailed(p) || !near(p, a.target())) {
                iterator.remove();
                if (p != null) notice(p, "aborted");
            } else if (city.getGameTime() >= a.ends()) {
                iterator.remove();
                NapoliStreet.credit(p, a.target().payout(),"heist");
                if (a.target().bank()) Carabinieri.report(p, Carabinieri.Crime.ROBBERY);
                notice(p, "finished", a.target().payout());
            } else {
                int seconds = (int) ((a.ends() - city.getGameTime() + 19) / 20);
                p.sendOverlayMessage(Component.translatable("napuli.heist.countdown", seconds));
            }
        }
    }

    static void build(ServerLevel city) {
        for (Target target : TARGETS) {
            if (!target.bank()) continue;
            int x = target.position().getX(), z = target.position().getZ();
            fill(city, x - 5, 63, z - 2, x + 5, 69, z + 5, Blocks.STONE_BRICKS);
            fill(city, x - 4, 64, z - 1, x + 4, 68, z + 4, Blocks.AIR);
            fill(city, x - 1, 64, z + 5, x + 1, 66, z + 5, Blocks.AIR);
            for (int side : new int[]{-1, 1}) {
                fill(city, x + side * 3, 65, z + 5, x + side * 3, 66, z + 5, Blocks.GLASS);
                fill(city, x + side * 2, 64, z + 6, x + side * 2, 68, z + 6, Blocks.QUARTZ_PILLAR);
            }
            fill(city, x - 5, 69, z + 6, x + 5, 69, z + 6, Blocks.SMOOTH_QUARTZ);
            fill(city, x - 1, 63, z + 6, x + 1, 63, z + 8, Blocks.POLISHED_ANDESITE);
            fill(city, x - 2, 68, z + 1, x + 2, 68, z + 1, Blocks.SEA_LANTERN);
            city.setBlock(target.position(), Blocks.ENDER_CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
            NapoliEmploymentMap.sign(city, target.position().south(2).east(2), "BANCA", "Maiusc + clic", "per iniziare");
        }
    }

    private static void fill(ServerLevel city, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2))
            city.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void notice(ServerPlayer player, String key, Object... args) {
        NapoliNotice.show(player, Component.translatable("napuli.heist." + key, args));
    }
}
