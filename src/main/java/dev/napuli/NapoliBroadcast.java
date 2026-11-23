package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;

final class NapoliBroadcast {
    enum Channel {
        OFF, SPORT, NEWS, WEATHER;
        Channel next() { return values()[(ordinal() + 1) % values().length]; }
    }

    static final int KICKOFF = 13000;
    static final int FINAL = 15000;
    private static final int FESTA_END = 16300;
    private static final long DAY_LENGTH = 24000;
    private static final BlockPos PIAZZA = new BlockPos(-11, 64, 26);

    private static final AttachmentType<Map<Long, Integer>> CHANNELS = AttachmentRegistry.<Map<Long, Integer>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING.xmap(Long::parseLong, Object::toString), Codec.INT))
            .buildAndRegister(Napuli.id("television_channels"));
    private static final AttachmentType<Long> ANNOUNCED = AttachmentRegistry.<Long>builder()
            .persistent(Codec.LONG).buildAndRegister(Napuli.id("football_last_day"));

    private static final AttachmentType<Long> LAST_PULSE = AttachmentRegistry.create(Napuli.id("football_last_pulse"));

    private static final AttachmentType<BlockPos> WATCHING = AttachmentRegistry.create(Napuli.id("watching_football"));

    private NapoliBroadcast() {}

    record Match(long day, int napoli, int juventus) {
        boolean win() { return napoli > juventus; }
    }

    static Match match(long day) {
        var fixture = fixture(day);
        if (fixture == null) return null;
        long week = Math.floorDiv(day, fixture.everyDays);
        return new Match(day, fixture.napoliGoals >= 0 ? fixture.napoliGoals : 1 + Math.floorMod(week, 3),
                fixture.opponentGoals >= 0 ? fixture.opponentGoals : Math.floorMod(week * 2, 4));
    }

    static CityLifeConfig.Fixture fixture(long day) { return CityLifeConfig.settings.fixtures.stream()
            .filter(f -> Math.floorMod(day-f.dayOffset,f.everyDays)==0).findFirst().orElse(null); }
    static int finalTime(long day) { var f=fixture(day);return f==null?FINAL:f.fullTime; }

    static long day(ServerLevel level) { return Math.floorDiv(level.getOverworldClockTime(), DAY_LENGTH); }
    static long time(ServerLevel level) { return Math.floorMod(level.getOverworldClockTime(), DAY_LENGTH); }

    static boolean live(ServerLevel level) {
        long time = time(level);
        var fixture=fixture(day(level));
        return fixture != null && time >= fixture.kickoff && time < fixture.fullTime;
    }

    static Channel channel(ServerLevel level, BlockPos television) {
        int value = level.getAttachedOrElse(CHANNELS, Map.of()).getOrDefault(television.asLong(), Channel.SPORT.ordinal());
        return Channel.values()[Math.clamp(value, 0, Channel.values().length - 1)];
    }

    static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(TelevisionProgram.TYPE,TelevisionProgram.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FootballBroadcast.Frame.TYPE, FootballBroadcast.Frame.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FootballBroadcast.Leave.TYPE, FootballBroadcast.Leave.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FootballBroadcast.Leave.TYPE,
                (message, context) -> context.player().removeAttached(WATCHING));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != NapoliMap.LEVEL || hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
            BlockPos television = NapoliHouseholds.findTelevision(hit.getBlockPos());
            if (television == null) return InteractionResult.PASS;
            if (player instanceof ServerPlayer viewer) {
                var city = viewer.level();
                Channel current = channel(city, television);
                Channel chosen = player.isShiftKeyDown() ? current.next()
                        : current == Channel.SPORT || current == Channel.OFF ? Channel.SPORT : Channel.OFF;
                var channels = new HashMap<>(city.getAttachedOrElse(CHANNELS, Map.of()));
                channels.put(television.asLong(), chosen.ordinal());
                city.setAttached(CHANNELS, Map.copyOf(channels));
                NapoliHouseholds.paintTelevision(city, television);
                if (!player.isShiftKeyDown() && chosen == Channel.SPORT && openFootball(viewer, television))
                    return InteractionResult.SUCCESS;
                showProgram(viewer, chosen);
            }
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            var city = server.getLevel(NapoliMap.LEVEL);
            if (city != null && !city.players().isEmpty()) {
                tick(city);
                for (ServerPlayer viewer : city.players()) updateFootball(viewer);
            }
        });
    }

    private static boolean openFootball(ServerPlayer viewer, BlockPos television) {
        if (!ServerPlayNetworking.canSend(viewer, FootballBroadcast.Frame.TYPE)) return false;
        FootballBroadcast.Frame frame = football(viewer.level(), true);
        if (frame == null) return false;
        viewer.setAttached(WATCHING, television);
        ServerPlayNetworking.send(viewer, frame);
        return true;
    }

    private static void updateFootball(ServerPlayer viewer) {
        BlockPos television = viewer.getAttached(WATCHING);
        if (television == null) return;
        ServerLevel level = viewer.level();
        var frame = television.distToCenterSqr(viewer.position()) <= 64 && channel(level, television) == Channel.SPORT
                ? football(level, false) : null;
        if (frame == null || !ServerPlayNetworking.canSend(viewer, FootballBroadcast.Frame.TYPE)) {
            viewer.removeAttached(WATCHING);
            if (ServerPlayNetworking.canSend(viewer, FootballBroadcast.Frame.TYPE))
                ServerPlayNetworking.send(viewer, new FootballBroadcast.Frame(false, FootballBroadcast.Frame.CLOSED, 0, 0, 0, 0));
        } else {
            ServerPlayNetworking.send(viewer, frame);
        }
    }

    private static FootballBroadcast.Frame football(ServerLevel level, boolean open) {
        long time = time(level), day = day(level);
        Match match = match(day);
        int mode, minute;
        if (time >= 7000 && time < 11600) {
            match = match(day - 1 - Math.floorMod(day - 1, 7));
            mode = FootballBroadcast.Frame.REPLAY;
            minute = (int) ((time - 7000) * 90 / 4600);
        } else if (match != null && time >= fixture(day).kickoff) {
            var scheduled = fixture(day);
            mode = time < scheduled.fullTime ? FootballBroadcast.Frame.LIVE : FootballBroadcast.Frame.FINAL;
            minute = Math.min(90, (int) ((time - scheduled.kickoff) * 90 / (scheduled.fullTime - scheduled.kickoff)));
        } else {
            return null;
        }
        if (match == null || !fixture(match.day()).opponent.equalsIgnoreCase("Juventus")) return null;
        return new FootballBroadcast.Frame(open, mode, minute, goalsAt(match.napoli(), minute, 13),
                goalsAt(match.juventus(), minute, 21), level.getGameTime());
    }

    private static int goalsAt(int total, int minute, int first) {
        return Math.min(total, Math.max(0, (minute - first + 24) / 24));
    }

    static void showProgram(ServerPlayer player, Channel channel) {
        ServerLevel level = player.level();
        long day = day(level);
        long time = time(level);
        Match match = match(day);
        Component content = switch (channel) {
            case OFF -> Component.translatable("napuli.tv.off");
            case NEWS -> Component.translatable("napuli.tv.news");
            case WEATHER -> Component.translatable(level.isRaining() ? "napuli.tv.rain" : "napuli.tv.clear");
            case SPORT -> match == null || time < KICKOFF
                    ? Component.translatable("napuli.tv.schedule", day + Math.floorMod(-day, 7))
                    : time < FINAL ? Component.translatable("napuli.tv.live") : result(match);
        };
        player.sendSystemMessage(content);
        if(channel!=Channel.OFF && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,TelevisionProgram.TYPE)) {
            var f=fixture(day);boolean football=channel==Channel.SPORT&&f!=null&&time>=f.kickoff;
            int minute=football?(int)Math.clamp((time-f.kickoff)*90/(f.fullTime-f.kickoff),0,90):0;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new TelevisionProgram(channel.name(),
                    football?f.opponent:channel==Channel.WEATHER?(level.isRaining()?"Pioggia: porta l'ombrello.":"Sereno sul Golfo."):"",
                    football?match.napoli():0,football?match.juventus():0,minute,level.getGameTime(),football?f.fullTime-f.kickoff:2000));
        }
        if (channel != Channel.OFF) player.sendOverlayMessage(Component.translatable("napuli.tv.controls"));
    }

    private static Component result(Match match) {
        var fixture=fixture(match.day());
        return Component.literal("Napoli " + match.napoli() + " – " + match.juventus() + " " + (fixture==null?"Juventus":fixture.opponent));
    }

    private static void tick(ServerLevel level) {
        for(var home:CityLivingHomes.all()) {
            var tv=CityLivingHomes.use(home,CityLivingHomes.Furniture.TV).block();
            if(level.hasChunkAt(tv)&&level.getBlockState(tv).getBlock().getDescriptionId().contains("concrete"))NapoliHouseholds.paintTelevision(level,tv);
        }
        long day = day(level);
        long time = time(level);
        Match match = match(day);
        if (match == null || time < finalTime(day) || time >= 19000) return;
        if (day > level.getAttachedOrElse(ANNOUNCED, Long.MIN_VALUE)) {
            level.setAttached(ANNOUNCED, day);
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(result(match));
                player.sendSystemMessage(Component.translatable(match.win() ? "napuli.tv.forza" : "napuli.tv.quiet"));
            }
        }
        long pulse = level.getOverworldClockTime() / 100;
        if (!match.win() || time % 100 >= 20 || pulse == level.getAttachedOrElse(LAST_PULSE, Long.MIN_VALUE)) return;
        level.setAttached(LAST_PULSE, pulse);
        if(time%400<20)for(var player:level.players()) {
            var fans=level.getEntitiesOfClass(NapoliResident.class,player.getBoundingBox().inflate(32));
            if(!fans.isEmpty()) {
                level.playSound(null,fans.getFirst().blockPosition(),SoundEvents.VILLAGER_CELEBRATE,SoundSource.NEUTRAL,.7f,1.1f);
                player.sendSystemMessage(Component.literal(fans.getFirst().getDisplayName().getString()+": FORZA NAPOLI!"));
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(PIAZZA) < 56 * 56)
                level.sendParticles(ParticleTypes.FIREWORK, PIAZZA.getX() + .5, PIAZZA.getY() + 8,
                        PIAZZA.getZ() + .5, 12, 1.2, .7, 1.2, .08);
        }
        var heard = new java.util.HashSet<Integer>();
        for (ServerPlayer player : level.players()) {
            AABB near = player.getBoundingBox().inflate(40);
            for (Motorino bike : level.getEntitiesOfClass(Motorino.class, near)) {
                if (bike.isAlive() && heard.add(bike.getId()))
                    level.playSound(null, bike.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, .75f, .65f);
            }
        }
    }
}
