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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class NapoliStreetMemes {
    enum Scene {
        JUSTIN("justin", "Perché no Napoli?", new BlockPos(17, 64, 51), 10,
                List.of("Uagliù, m'hanno ditto ca arriva Justin Bieber. Tenite pronti 'e telefoni!",
                        "Aggio visto na macchina nera! Aspè… era nu taxi.",
                        "A Roma sì, a Milano pure… e pecché no Napule?",
                        "Uagliù, niente Justin. Ce facimmo na foto 'e gruppo e ce ne jammo?"), Napuli.JUSTIN_CROWD, 428),
        PRESEPE("presepe", "Te piace 'o presepe?", new BlockPos(-28, 64, -8), 3,
                List.of("Viene a vedé: aggio miso pure 'o pizzaiuolo dint'ô presepe.",
                        "E 'a Madonna addò sta?", "Aspè, mo tene 'a pala. Sta facenno 'a pizza cu ll'ate.")),
        PULCINELLA("pulcinella", "Il naso di Pulcinella", new BlockPos(25, 64, 32), 3,
                List.of("M'hanno ditto ca si tuocche 'o naso 'e Pulicenella porta fortuna.",
                        "E tu addò sì juto? Chillo era 'o naso d' 'o parcheggiatore!",
                        "Mo capisco pecché m'ha chiesto ddoje euro.")),
        MERCATO("mercato", "Tre euro", new BlockPos(-15, 64, 53), 3,
                List.of("Quanto vene 'o cuoppo?", "Siente, 'o mercatante t'o dice n'ata vota.",
                        "Aggio capito, dammene uno!"), Napuli.VOCALE_MERCATO, 80),
        TEATRO("teatro", "Lo spettacolo in TV", NapoliTheatreQuarter.FORECOURT, 3,
                List.of("Te si' scurdato 'e piglià 'o biglietto?", "Aspè, m'hanno mannato nu vocale.",
                        "Allora ce 'o vedimmo 'a casa."), Napuli.VOCALE_TEATRO, 112),
        CANTANTE("cantante", "Stamattina sul lungomare", new BlockPos(-96, 64, 50), 3,
                List.of("Uagliù, stateve a sentì chillo ca canta.", "Fa 'o ritornello!",
                        "Mo ce rummane 'n capa pe' tutt' 'a jurnata."), Napuli.VOCALE_CANTANTE, 147),
        ORDINANZA("ordinanza", "Riaprono le scuole", new BlockPos(-7, 64, 12), 3,
                List.of("Doppo 'a pioggia, 'a scola dimane arape?", "Sentimmo che dice l'avviso.",
                        "Va buò, allora pripara 'o zaino."), Napuli.VOCALE_ORDINANZA, 130),
        RICETTA("ricetta", "Il panino dimenticato", new BlockPos(-63, 64, -13), 3,
                List.of("Carmè, è pronto 'o pranzo?", "Siente che dice dint'ô vocale.",
                        "Va buò, ce facimmo nu panino doppo."), Napuli.VOCALE_RICETTA, 134),
        BALLO("ballo", "Due passi alla Cassa Armonica", NapoliPosillipo.center("villa_comunale").offset(-12, 0, 36), 3,
                List.of("Chi s' 'a sente 'e ballà?", "Mo te faccio sentì che m'ha risposto.",
                        "Duje passe e ce ne jammo."), Napuli.VOCALE_BALLO, 82),
        GIUBBETTO("giubbetto", "Cercasi giubbetto", new BlockPos(74, 64, -127), 3,
                List.of("Avite visto nu giubbetto?", "Aspè, te faccio sentì 'o messaggio.",
                        "Si 'o truvammo, t'avvisammo."), Napuli.VOCALE_GIUBBETTO, 112),
        SALUTO("saluto", "Buongiorno dal molo", new BlockPos(13, 64, 53), 3,
                List.of("So' turnate 'e varche!", "Ecco 'o saluto d''a matina.",
                        "Mo vire che pesce ce sta."), Napuli.VOCALE_SALUTO, 130),
        INDICAZIONI("indicazioni", "Turista alla calata", NapoliPosillipo.center("marechiaro").offset(0, 0, 13), 3,
                List.of("Scusate, pe' arrivà all'acqua addò s'adda scennere?", "Siente, ce stanno cercanno pure ll'ate.",
                        "Seguita 'a calata, se vede 'o mare."), Napuli.VOCALE_INDICAZIONI, 62),
        MODELLO("modello", "Nu modello 'e nu metro e nuvanta",
                NapoliDistrictLandmarks.site("mercato").south(17), 2,
                List.of("Buonasera!", "Guè, buonasera!", "Aspè ca chiammo a n'amica mia.",
                        "Era nu modello, alto nu metro e nuvanta!"), Napuli.VOCALE_MODELLO, 332);

        final String id;
        final String title;
        final BlockPos point;
        final int actors;
        final List<String> lines;

        final SoundEvent recording;
        final int recordingTicks;

        Scene(String id, String title, BlockPos point, int actors, List<String> lines) {
            this(id, title, point, actors, lines, null, 0);
        }

        Scene(String id, String title, BlockPos point, int actors, List<String> lines,
                SoundEvent recording, int recordingTicks) {
            this.id = id;
            this.title = title;
            this.point = point;
            this.actors = actors;
            this.lines = lines;
            this.recording = recording;
            this.recordingTicks = recordingTicks;
        }
    }

    private record Performance(Scene scene, List<NapoliResident> cast, long started, int spoken, Set<UUID> heard) {}
    private static final String ACTOR = "napuli_street_meme";
    private record RecordingPlayback(ServerLevel city, long until) {}
    private static final AttachmentType<RecordingPlayback> RECORDING_PLAYBACK =
            AttachmentRegistry.create(Napuli.id("street_meme_recording_playback"));
    private static final int BEAT = 100;
    private static final int INVITATION_RANGE = 28;
    private static final int CALL_RANGE = 14;
    private static final int APPROACH = 0, GREETING = 1, LEAVING = 2, CALLING = 3;
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

    static void tick(ServerLevel city, boolean streetTick) {
        long now = city.getGameTime();
        var live = city.getAttached(LIVE);
        if (live != null && live.scene() == Scene.MODELLO) {
            tickModel(city, live, audience(city, live.scene()), now);
            return;
        }
        if (!streetTick) return;
        if (live != null) {
            var viewers = audience(city, live.scene());
            long age = now - live.started();
            if (age >= (live.scene().lines.size() + 1) * BEAT || viewers.isEmpty()) {
                live.cast().forEach(NapoliResident::discard);
                city.removeAttached(LIVE);
            } else {
                var scene = live.scene();
                long cue = scene == Scene.JUSTIN ? 0 : BEAT;
                if (scene.recording != null && age >= cue && (scene == Scene.JUSTIN || age < cue + BEAT))
                    for (var viewer : viewers) playRecording(city, live, viewer, false);
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
        if (now % 20 != 0 || now < city.getAttachedOrElse(NEXT, Long.MIN_VALUE)) return;
        var scenes = Scene.values();
        int first = today(city.getSeed(), day).ordinal();
        for (int i = 0; i < scenes.length; i++) {
            var scene = scenes[(first + i) % scenes.length];
            if (!audience(city, scene).isEmpty() && start(city, scene)) {
                city.setAttached(PERFORMED, day);
                return;
            }
        }
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        var command = Commands.literal("meme").executes(c -> {
            var player = c.getSource().getPlayerOrException();
            var entries = java.util.Arrays.stream(Scene.values()).map(scene -> scene.id)
                    .collect(java.util.stream.Collectors.joining(" · "));
            NapoliNotice.show(player, Component.literal("/citta meme · " + entries));
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
                if (!playRecording(city, existing, player, true) && existing.heard().contains(player.getUUID()))
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
        if (scene == Scene.MODELLO) return startModel(city, now);
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

    private static boolean startModel(ServerLevel city, long now) {
        var point = Scene.MODELLO.point;
        for (int x = -6; x <= 6; x++)
            if (!NapoliResidentHomes.walkable(city, point.east(x))) return false;
        var woman = NapoliResident.create(city, NapoliResident.Role.SIGNORA_TELEFONATA, point.west(5), -90);
        var model = NapoliResident.create(city, NapoliResident.Role.MODELLO_190, point.east(5), 90);
        woman.setCustomName(Component.literal("La signora"));
        model.setCustomName(Component.literal("Il modello"));
        var scale = model.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(1.90 / model.getBbHeight());
        var cast = List.of(woman, model);
        for (var npc : cast) {
            npc.addTag(ACTOR);
            npc.setNoAi(true);
            npc.setPermanentlyInvulnerable(true);
            if (!city.addFreshEntity(npc)) {
                cast.forEach(NapoliResident::discard);
                return false;
            }
        }
        city.setAttached(LIVE, new Performance(Scene.MODELLO, cast, now, APPROACH, new HashSet<>()));
        city.setAttached(NEXT, now + 1200);
        return true;
    }

    private static void tickModel(ServerLevel city, Performance live, List<ServerPlayer> viewers, long now) {
        var woman = live.cast().getFirst();
        var model = live.cast().getLast();
        long elapsed = now - live.started();
        int stage = live.spoken();
        if (viewers.isEmpty() || !woman.isAlive() || !model.isAlive() || elapsed >=
                (stage == CALLING ? live.scene().recordingTicks + 35L : 160)) {
            live.cast().forEach(NapoliResident::discard);
            city.removeAttached(LIVE);
            return;
        }
        if (stage == APPROACH) {
            walkAlongRow(woman, live.scene().point.getX() - .5);
            walkAlongRow(model, live.scene().point.getX() + 1.5);
        } else if (stage == LEAVING) {
            walkAlongRow(model, live.scene().point.getX() + 6.5);
        }
        if (stage == APPROACH && woman.distanceToSqr(model) <= 6.25) {
            face(woman, model);
            face(model, woman);
            woman.animate(CityGestures.Gesture.WAVE, 28);
            model.animate(CityGestures.Gesture.WAVE, 28);
            for (var viewer : viewers) NpcSpeech.say(viewer, model, Component.literal(live.scene().lines.get(0)));
            advance(city, live, now, GREETING);
        } else if (stage == GREETING && elapsed == 24) {
            for (var viewer : viewers) NpcSpeech.say(viewer, woman, Component.literal(live.scene().lines.get(1)));
        } else if (stage == GREETING && elapsed >= 50) {
            woman.setGesture(CityGestures.Gesture.NONE);
            model.setGesture(CityGestures.Gesture.NONE);
            advance(city, live, now, LEAVING);
        } else if (stage == LEAVING && woman.distanceToSqr(model) >= 16) {
            var phone = Phones.ITEMS.get(PhoneModel.ONEPLUS_15);
            if (phone != null) woman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(phone));
            woman.setDropChance(EquipmentSlot.MAINHAND, 0);
            woman.animate(CityGestures.Gesture.PHONE_CALL, live.scene().recordingTicks + 30);
            for (var viewer : viewers) if (viewer.distanceToSqr(woman) <= CALL_RANGE * CALL_RANGE)
                NpcSpeech.say(viewer, woman, Component.literal(live.scene().lines.get(2)));
            advance(city, live, now, CALLING);
        } else if (stage == CALLING && elapsed >= 10 && elapsed <= 30) {
            for (var viewer : viewers) if (viewer.distanceToSqr(woman) <= CALL_RANGE * CALL_RANGE)
                playRecording(city, live, viewer, false);
        }
    }

    private static void advance(ServerLevel city, Performance live, long now, int stage) {
        city.setAttached(LIVE, new Performance(live.scene(), live.cast(), now, stage, live.heard()));
    }

    private static void walkAlongRow(NapoliResident npc, double targetX) {
        double dx = Math.clamp(targetX - npc.getX(), -.10, .10);
        if (Math.abs(dx) < .001) return;
        float yaw = dx > 0 ? -90 : 90;
        npc.setYRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setYHeadRot(yaw);
        npc.move(MoverType.SELF, new Vec3(dx, 0, 0));
    }

    private static void face(NapoliResident npc, NapoliResident target) {
        float yaw = (float) Math.toDegrees(Math.atan2(npc.getX() - target.getX(), target.getZ() - npc.getZ()));
        npc.setYRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setYHeadRot(yaw);
    }

    private static boolean playRecording(ServerLevel city, Performance scene, ServerPlayer player, boolean requested) {
        if (scene.heard().contains(player.getUUID()) || !requested && (CityEffects.flags(player) & CityEffects.QUIET) != 0)
            return false;
        var playback = player.getAttached(RECORDING_PLAYBACK);
        long now = city.getGameTime();
        if (playback != null && playback.city() == city && now < playback.until()) return false;

        var origin = scene.scene() == Scene.JUSTIN ? scene.scene().point
                : scene.cast().get(scene.scene() == Scene.MODELLO ? 0 : 1).blockPosition();
        player.connection.send(new ClientboundSoundPacket(Holder.direct(scene.scene().recording), SoundSource.VOICE,
                origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5, 1.3f, 1f, city.getRandom().nextLong()));
        scene.heard().add(player.getUUID());
        player.setAttached(RECORDING_PLAYBACK, new RecordingPlayback(city, now + scene.scene().recordingTicks));
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
        if (live.scene().recording == null)
            CityEffects.sound(city, null, speaker.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, .4f, 1.18f);
    }

    static void reply(ServerPlayer player, NapoliResident actor) {
        var live = player.level().getAttached(LIVE);
        if (live == null || !live.cast().contains(actor)) return;
        int line = live.scene() == Scene.MODELLO ? live.spoken() : Math.clamp(
                (int) ((player.level().getGameTime() - live.started()) / BEAT), 0, live.scene().lines.size() - 1);
        NpcSpeech.reply(player, actor, Component.literal(live.scene().lines.get(line)));
    }
}
