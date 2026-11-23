package dev.napuli;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

public final class NapoliScenes {
    private static final AttachmentType<Visits> VISITS = AttachmentRegistry.create(Napuli.id("city_visits"));
    private static final Map<ServerLevel, Long> NEXT_MOTORINO = new WeakHashMap<>();
    private static final int RIDE_RANGE = 32;
    private static final NapoliResident.Role[] RIDERS = {
            NapoliResident.Role.GENNARO, NapoliResident.Role.CIRO, NapoliResident.Role.ENZO
    };

    private static final class Visits {
        long crypt;
        long cryptWarning;
        long galleria;
        long water;
        long cafe;
        long greetings;
    }

    private NapoliScenes() {}

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        ServerLevel level = server.getLevel(NapoliMap.LEVEL);
        if (level == null) return;
        long now = level.getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (player.level() != level) NapoliStreet.resetWeightOutside(player);
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            NapoliStreet.tick(level, player, now);
            BlockPos pos = player.blockPosition();
            Visits visits = player.getAttachedOrCreate(VISITS, Visits::new);
            if (now >= NEXT_MOTORINO.getOrDefault(level, 0L) && pos.getY() >= NapoliMap.STREET
                    && Math.abs(pos.getX()) < 17 && pos.getZ() >= -51 && pos.getZ() < 39) {
                motorino(player);
            }
            boolean inCrypt = pos.getY() < 63 && pos.distSqr(NapoliMap.CRYPT) < 100;
            if (!inCrypt) visits.cryptWarning = 0;
            else if (now >= visits.crypt) {
                if (visits.cryptWarning == 0) {
                    visits.cryptWarning = now + 20;
                    player.sendOverlayMessage(Component.translatable("napuli.horror.crypt_warning"));
                    var point = NapoliMap.CRYPT;
                    level.playSound(null, point, SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.AMBIENT, 0.65f, 0.6f);
                    level.sendParticles(ParticleTypes.SOUL, point.getX() + 0.5, point.getY() + 0.4, point.getZ() + 0.5,
                            12, 0.2, 0.2, 0.2, 0.025);
                } else if (now >= visits.cryptWarning) {
                    visits.cryptWarning = 0;
                    visits.crypt = now + 20 * 55;
                    folklore(player, NapoliResident.Role.ANIMA);
                }
            }
            if (now >= visits.water && pos.distSqr(NapoliMap.WATERFRONT) < 150 && !level.isBrightOutside()) {
                visits.water = now + 20 * 90;
                folklore(player, NapoliResident.Role.PARTENOPE);
            }
            if (pos.distSqr(NapoliMap.GALLERY) < 81 && now >= visits.galleria && !level.isBrightOutside()) {
                visits.galleria = now + 20 * 100;
                folklore(player, NapoliResident.Role.MBRIANA);
            }
            if (pos.distSqr(NapoliMap.CAFE) < 20 && now >= visits.cafe && level.isBrightOutside()) {
                visits.cafe = now + 20 * 110;
                player.sendSystemMessage(Component.translatable("napuli.meme.cafe"));
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.AMBIENT, 0.55f, 1.2f);
            }
            if (now >= visits.greetings) {
                for (NapoliResident npc : level.getEntitiesOfClass(NapoliResident.class, player.getBoundingBox().inflate(4))) {
                    if (!npc.role().social()) continue;
                    var bond = NapoliDialogue.bond(player, npc.role());
                    if (bond.interest() < 35) continue;
                    player.sendSystemMessage(Component.translatable("napuli.dialog." + npc.role().id() + ".approach", player.getName()));
                    visits.greetings = now + 20 * 95;
                    break;
                }
            }
        }
    }

    public static int motorino(ServerPlayer observer) {
        ServerLevel level = observer.level();
        if (level.dimension() != NapoliMap.LEVEL || observer.isSpectator()) return 0;
        long now = level.getGameTime();
        if (now < NEXT_MOTORINO.getOrDefault(level, 0L)) return 0;
        BlockPos start = new BlockPos(3, 64, Math.clamp(observer.getBlockZ() + 13, -34, 40));
        float yaw = 180;
        if (observer.getBlockZ() > 22) {
            start = new BlockPos(3, 64, observer.getBlockZ() - 13);
            yaw = 0;
        }
        level.getChunkAt(start);
        Motorino bike = Napuli.MOTORINO.create(level, EntitySpawnReason.EVENT);
        if (bike == null) return 0;
        bike.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, yaw, 0);
        bike.startParade();
        // sometimes they're good boys with helmets on; mostly not (and then the carabinieri may stop them)
        boolean helmets = level.getRandom().nextFloat() < 0.3f;
        for (NapoliResident.Role role : RIDERS) {
            NapoliResident rider = NapoliResident.create(level, role, start, yaw);
            rider.setNoAi(true);
            if (helmets) rider.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Napuli.CASCO));
            if (!rider.startRiding(bike, true, false)) {
                Napuli.LOGGER.warn("The motorino refused rider {}", role);
                return 0;
            }
        }
        if (!level.tryAddFreshEntityWithPassengers(bike)) {
            Napuli.LOGGER.warn("Could not spawn the motorino group at {}", start);
            return 0;
        }
        NEXT_MOTORINO.put(level, now + 20 * 75L);

        List<ServerPlayer> listeners = level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(bike) <= RIDE_RANGE * RIDE_RANGE);
        // the song plays from the scooter itself (station PARADE), so it moves with the boys and stops when they vanish
        for (ServerPlayer player : listeners) {
            if (Music.current(player) != null) Music.stop(player);
            player.sendSystemMessage(Component.translatable("napuli.meme.motorino"));
        }
        level.playSound(null, start, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.NEUTRAL, 0.95f, 0.75f);
        Napuli.LOGGER.info("Napoli motorino started: {} riders, {} listeners", bike.getPassengers().size(), listeners.size());
        return 1;
    }

    static void folklore(ServerPlayer player, NapoliResident.Role role) {
        ServerLevel level = player.level();
        boolean charm = player.getMainHandItem().is(Napuli.CORNICELLO) || player.getOffhandItem().is(Napuli.CORNICELLO);
        BlockPos point;
        switch (role) {
            case ANIMA -> point = NapoliMap.CRYPT;
            case PARTENOPE -> point = new BlockPos(4, 64, 63);
            case MBRIANA -> point = NapoliDistrict.GALLERIA.offset(4, 0, -4);
            default -> throw new IllegalArgumentException(role.toString());
        }
        level.getChunkAt(point);
        float yaw = switch (role) {
            case ANIMA -> 0;
            case MBRIANA -> 90;
            default -> 180;
        };
        var spirit = NapoliResident.spawn(level, role, point, yaw);
        player.sendSystemMessage(Component.translatable("napuli.folklore." + role.id()));
        level.sendParticles(ParticleTypes.SOUL, spirit.getX(), spirit.getY() + 1, spirit.getZ(), 18, 0.45, 0.7, 0.45, 0.035);
        switch (role) {
            case MBRIANA -> {
                level.playSound(null, point, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1, 0.75f);
                player.removeEffect(MobEffects.DARKNESS);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            }
            case PARTENOPE -> {
                level.playSound(null, point, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.AMBIENT, 1.1f, 0.55f);
                Music.play(player, Music.Situation.NOTTE);
                if (!charm) player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0));
            }
            case ANIMA -> {
                level.playSound(null, point, SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 1.2f, 0.75f);
                if (!charm) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 0));
                }
            }
            default -> throw new IllegalArgumentException(role.toString());
        }
        if (charm && role != NapoliResident.Role.MBRIANA)
            NapoliNotice.show(player, Component.translatable("napuli.folklore.charm"));
    }

    static void answerSpirit(ServerPlayer player, NapoliResident spirit) {
        var level = player.level();
        switch (spirit.role()) {
            case ANIMA -> {
                var hand = player.getMainHandItem().is(net.minecraft.world.item.Items.CANDLE)
                        ? player.getMainHandItem() : player.getOffhandItem();
                if (!hand.is(net.minecraft.world.item.Items.CANDLE)) {
                    player.sendOverlayMessage(Component.translatable("napuli.horror.anima_offer"));
                    return;
                }
                if (!player.isCreative()) hand.shrink(1);
                player.removeEffect(MobEffects.SLOWNESS);
                player.removeEffect(MobEffects.DARKNESS);
            }
            case PARTENOPE -> {
                player.removeEffect(MobEffects.SLOWNESS);
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 25, 0));
            }
            case MBRIANA -> {
                player.removeEffect(MobEffects.DARKNESS);
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 30, 0));
            }
            default -> { return; }
        }
        level.sendParticles(ParticleTypes.SOUL, spirit.getX(), spirit.getY() + 1, spirit.getZ(),
                24, 0.32, 0.5, 0.32, 0.03);
        level.playSound(null, spirit.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.8f, 1.1f);
        NapoliNotice.show(player, Component.translatable("napuli.horror.answer_" + spirit.role().id()));
        spirit.discard();
    }

    static void removeRiders(Motorino bike) {
        for (Entity rider : List.copyOf(bike.getPassengers())) {
            if (rider instanceof NapoliResident) rider.discard();
        }
    }
}
