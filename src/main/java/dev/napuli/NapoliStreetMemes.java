package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class NapoliStreetMemes {
    enum Scene {
        JUSTIN("justin", "Perché no Napoli?", new BlockPos(17, 64, 51), 10,
                List.of("Uagliù, m'hanno ditto ca arriva Justin Bieber. Tenite pronti 'e telefoni!",
                        "Aggio visto na macchina nera! Aspè… era nu taxi.",
                        "A Roma sì, a Milano pure… e pecché no Napule?",
                        "Uagliù, niente Justin. Ce facimmo na foto 'e gruppo e ce ne jammo?")),
        PRESEPE("presepe", "Te piace 'o presepe?", new BlockPos(-28, 64, -8), 3,
                List.of("Viene a vedé: aggio miso pure 'o pizzaiuolo dint'ô presepe.",
                        "E 'a Madonna addò sta?", "Aspè, mo tene 'a pala. Sta facenno 'a pizza cu ll'ate.")),
        PULCINELLA("pulcinella", "Il naso di Pulcinella", new BlockPos(25, 64, 32), 3,
                List.of("M'hanno ditto ca si tuocche 'o naso 'e Pulicenella porta fortuna.",
                        "E tu addò sì juto? Chillo era 'o naso d' 'o parcheggiatore!",
                        "Mo capisco pecché m'ha chiesto ddoje euro."));

        final String id;
        final String title;
        final BlockPos point;
        final int actors;
        final List<String> lines;

        Scene(String id, String title, BlockPos point, int actors, List<String> lines) {
            this.id = id;
            this.title = title;
            this.point = point;
            this.actors = actors;
            this.lines = lines;
        }
    }

    private record Performance(Scene scene, List<NapoliResident> cast, long started, int spoken, Set<UUID> heard) {}
    private static final String ACTOR = "napuli_street_meme";
    private record JustinPlayback(ServerLevel city, long until) {}
    private static final AttachmentType<JustinPlayback> JUSTIN_PLAYBACK =
            AttachmentRegistry.create(Napuli.id("street_meme_justin_playback"));
    private static final int JUSTIN_AUDIO_TICKS = 446;
    private static final int BEAT = 100;
    private static final int INVITATION_RANGE = 28;
    private static final AttachmentType<Performance> LIVE = AttachmentRegistry.create(Napuli.id("street_meme_live"));
    private static final AttachmentType<Long> NEXT = AttachmentRegistry.create(Napuli.id("street_meme_cooldown"));
    private static final AttachmentType<Long> PERFORMED = AttachmentRegistry.<Long>builder().persistent(Codec.LONG)
            .buildAndRegister(Napuli.id("street_meme_day"));

    private NapoliStreetMemes() {}

    // Register the daily marker before the city dimension loads a saved scene history.
    static void register() {}

    static boolean actor(NapoliResident resident) { return resident.entityTags().contains(ACTOR); }

    static Scene today(long seed, long day) {
        var scenes = Scene.values();
        return scenes[Math.floorMod(day + Long.hashCode(seed), scenes.length)];
    }

    static void tick(ServerLevel city) {
        long now = city.getGameTime();
        var live = city.getAttached(LIVE);
        if (live != null) {
            var viewers = audience(city, live.scene());
            long age = now - live.started();
            if (age >= (live.scene().lines.size() + 1) * BEAT || viewers.isEmpty()) {
                live.cast().forEach(NapoliResident::discard);
                city.removeAttached(LIVE);
            } else {
                if (live.scene() == Scene.JUSTIN)
                    for (var viewer : viewers) playJustin(city, live, viewer, false);
                int line = (int) (age / BEAT);
                if (line > live.spoken() && line < live.scene().lines.size()) {
                    speak(city, live, line);
                    city.setAttached(LIVE, new Performance(live.scene(), live.cast(), live.started(), line, live.heard()));
                }
            }
            return;
        }
        long day = NapoliBroadcast.day(city);
        long time = NapoliBroadcast.time(city);
        if (time < 7500 || time >= 14000 || city.getAttachedOrElse(PERFORMED, Long.MIN_VALUE) >= day) return;
        var scene = today(city.getSeed(), day);
        if (!audience(city, scene).isEmpty() && start(city, scene))
            city.setAttached(PERFORMED, day);
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        var command = Commands.literal("meme").executes(c -> {
            var player = c.getSource().getPlayerOrException();
            NapoliNotice.show(player, Component.literal("/citta meme justin sul lungomare · presepe nei vicoli · pulcinella al Plebiscito"));
            return 1;
        });
        for (var scene : Scene.values())
            command.then(Commands.literal(scene.id).executes(c -> request(c.getSource().getPlayerOrException(), scene)));
        return command;
    }

    private static int request(ServerPlayer player, Scene scene) {
        var city = player.level();
        if (city.dimension() != NapoliMap.LEVEL || player.isSpectator() || !player.isAlive()
                || scene.point.distToCenterSqr(player.position()) > INVITATION_RANGE * INVITATION_RANGE) {
            NapoliNotice.show(player, Component.literal(scene.title + " · X " + scene.point.getX()
                    + ", Z " + scene.point.getZ() + " · avvicinati per vedere la scena."));
            return 0;
        }
        var existing = city.getAttached(LIVE);
        if (scene == Scene.JUSTIN) {
            if (existing == null) {
                start(city, scene, true);
                existing = city.getAttached(LIVE);
            }
            if (existing != null && existing.scene() == scene) {
                if (!playJustin(city, existing, player, true) && existing.heard().contains(player.getUUID()))
                    NapoliNotice.show(player, Component.literal("L'audio di Justin è già partito per questa scena."));
                return 1;
            }
        } else if (start(city, scene)) return 1;
        NapoliNotice.show(player, Component.literal("Aspetta un attimo: i ragazzi si stanno ancora organizzando."));
        return 0;
    }

    private static List<ServerPlayer> audience(ServerLevel city, Scene scene) {
        return city.getPlayers(p -> p.isAlive() && !p.isSpectator()
                && scene.point.distToCenterSqr(p.position()) <= INVITATION_RANGE * INVITATION_RANGE);
    }

    static boolean start(ServerLevel city, Scene scene) { return start(city, scene, false); }

    private static boolean start(ServerLevel city, Scene scene, boolean requested) {
        long now = city.getGameTime();
        if (city.hasAttached(LIVE) || !requested && now < city.getAttachedOrElse(NEXT, Long.MIN_VALUE)) return false;
        var cast = new ArrayList<NapoliResident>();
        for (int row = 0; row < 3 && cast.size() < scene.actors; row++) {
            for (int col = 0; col < 5 && cast.size() < scene.actors; col++) {
                var spot = scene.point.offset((col - 2) * 2 + (row % 2), 0, row * 2 - 2);
                if (!NapoliResidentHomes.walkable(city, spot)
                        || !city.getEntitiesOfClass(NapoliResident.class,
                                new net.minecraft.world.phys.AABB(spot).inflate(.35)).isEmpty()) continue;
                int slot = cast.size();
                var role = slot % 2 == 0 ? NapoliResident.Role.CIVIC_WOMAN : NapoliResident.Role.PASSERBY;
                var npc = NapoliResident.create(city, role, spot, 180);
                npc.setEmploymentAppearance(role, slot % role.variants());
                npc.setCustomName(Component.literal(scene == Scene.JUSTIN ? "Fan di Justin" : "Vicino"));
                npc.setCustomNameVisible(false);
                npc.addTag(ACTOR);
                npc.setNoAi(true);
                npc.setPermanentlyInvulnerable(true);
                if (scene == Scene.JUSTIN) {
                    npc.setCivicAge(15 + slot % 4);
                    var prop = slot % 3 == 0 ? Items.SPYGLASS : Items.PAPER;
                    npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(prop));
                    npc.setDropChance(EquipmentSlot.MAINHAND, 0f);
                }
                if (city.addFreshEntity(npc)) cast.add(npc);
            }
        }
        if (cast.size() < Math.min(scene.actors, 6)) {
            cast.forEach(NapoliResident::discard);
            return false;
        }
        var live = new Performance(scene, List.copyOf(cast), now, 0, new HashSet<>());
        city.setAttached(LIVE, live);
        city.setAttached(NEXT, now + 1200);
        for (var viewer : audience(city, scene)) NapoliNotice.show(viewer, Component.literal(scene.title));

        speak(city, live, 0);
        return true;
    }

    private static boolean playJustin(ServerLevel city, Performance scene, ServerPlayer player, boolean requested) {
        if (scene.heard().contains(player.getUUID()) || !requested && (CityEffects.flags(player) & CityEffects.QUIET) != 0)
            return false;
        var playback = player.getAttached(JUSTIN_PLAYBACK);
        long now = city.getGameTime();
        if (playback != null && playback.city() == city && now < playback.until()) return false;

        var origin = scene.scene().point;
        player.connection.send(new ClientboundSoundPacket(Holder.direct(Napuli.JUSTIN_CROWD), SoundSource.VOICE,
                origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5, 1.3f, 1f, city.getRandom().nextLong()));
        scene.heard().add(player.getUUID());
        player.setAttached(JUSTIN_PLAYBACK, new JustinPlayback(city, now + JUSTIN_AUDIO_TICKS));
        return true;
    }

    private static void speak(ServerLevel city, Performance live, int line) {
        var cast = live.cast();
        var speaker = cast.get(line % cast.size());
        var gesture = line == live.scene().lines.size() - 1 ? CityGestures.Gesture.HAPPY : CityGestures.Gesture.WAVE;
        for (int i = 0; i < cast.size(); i++) {
            var npc = cast.get(i);
            var focus = i == line % cast.size() ? live.scene().point.north(5) : speaker.blockPosition();
            float yaw = (float) Math.toDegrees(Math.atan2(npc.getX() - focus.getX() - .5, focus.getZ() + .5 - npc.getZ()));
            npc.setYRot(yaw);
            npc.setYBodyRot(yaw);
            npc.setYHeadRot(yaw);
            if (npc == speaker || i % 3 == line % 3) npc.animate(gesture, 55);
        }
        for (var viewer : audience(city, live.scene()))
            NpcSpeech.say(viewer, speaker, Component.literal(live.scene().lines.get(line)));
        if (live.scene() != Scene.JUSTIN)
            CityEffects.sound(city, null, speaker.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, .4f, 1.18f);
    }

    static void reply(ServerPlayer player, NapoliResident actor) {
        var live = player.level().getAttached(LIVE);
        if (live == null || !live.cast().contains(actor)) return;
        int line = Math.clamp((int) ((player.level().getGameTime() - live.started()) / BEAT),
                0, live.scene().lines.size() - 1);
        NpcSpeech.reply(player, actor, Component.literal(live.scene().lines.get(line)));
    }
}
