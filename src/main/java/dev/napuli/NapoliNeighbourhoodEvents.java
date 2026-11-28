package dev.napuli;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

final class NapoliNeighbourhoodEvents {
    record Scene(String title, String description, Item reward, Item donation, Quiz quiz) {}
    record Quiz(String first, String second, int answer) {}
    record Venue(String place, int opens, SoundEvent sound, SimpleParticleType particle, List<Scene> scenes) {
        BlockPos point() { return NapoliAtlas.find(place).arrival(); }

        boolean open(long time) { return time >= opens && time < opens + DURATION; }

        Scene scene(long seed, long day) {
            int size = scenes.size();
            long cycle = Math.floorDiv(day, size);
            long venueSeed = seed ^ place.hashCode();
            int[] order = order(venueSeed, cycle, size);
            int previousLast = order(venueSeed, cycle - 1, size)[size - 1];
            if (order[0] == previousLast) {
                order[0] = order[1];
                order[1] = previousLast;
            }
            return scenes.get(order[Math.floorMod(day, size)]);
        }
    }

    private static final int DURATION = 3500;
    private static final int NEAR = 24;
    private static final int PARTICIPATE = 14;
    private static final AttachmentType<Map<String, Long>> VISITED = AttachmentRegistry
            .<Map<String, Long>>builder().persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG))
            .copyOnDeath().buildAndRegister(Napuli.id("neighbourhood_event_visits"));
    private static final AttachmentType<Map<String, Long>> CLAIMED = AttachmentRegistry
            .<Map<String, Long>>builder().persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG))
            .copyOnDeath().buildAndRegister(Napuli.id("neighbourhood_event_rewards"));

    private NapoliNeighbourhoodEvents() {}

    private static int[] order(long seed, long cycle, int size) {
        var rng = new Random(seed ^ Long.rotateLeft(cycle * 0x9E3779B97F4A7C15L, 21));
        int[] order = new int[size];
        for (int i = 0; i < size; i++) {
            int j = rng.nextInt(i + 1);
            order[i] = order[j];
            order[j] = i;
        }
        return order;
    }

    static void tick(ServerLevel city) {
        long time = NapoliBroadcast.time(city);
        long day = NapoliBroadcast.day(city);
        for (Venue venue : NapoliEventCatalogue.VENUES) {
            if (!venue.open(time)) continue;
            BlockPos pos = venue.point();
            var visitors = city.getPlayers(p -> eligible(p) && p.blockPosition().distSqr(pos) <= NEAR * NEAR);
            if (visitors.isEmpty()) continue;
            var scene = venue.scene(city.getSeed(), day);
            for (var player : visitors) {
                if (recordDay(player, VISITED, venue, day))
                    NapoliNotice.show(player, Component.literal(scene.title() + " · /citta partecipa"));
            }
            CityEffects.particles(city, venue.particle(), pos.getX() + .5, pos.getY() + 1,
                    pos.getZ() + .5, 10, .65, .45, .65, .03);
            CityEffects.sound(city, null, pos, venue.sound(), SoundSource.AMBIENT, .55f, 1f);
        }
    }

    static LiteralArgumentBuilder<CommandSourceStack> commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(NapoliStreetMemes.command()).then(Commands.literal("eventi").executes(c -> agenda(c.getSource().getPlayerOrException())))
                .then(Commands.literal("partecipa")
                        .executes(c -> participate(c.getSource().getPlayerOrException(), 0))
                        .then(Commands.argument("risposta", IntegerArgumentType.integer(1, 2))
                                .executes(c -> participate(c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "risposta")))));
    }

    private static int agenda(ServerPlayer player) {
        if (!eligible(player)) return tell(player, "Il programma degli eventi è disponibile a Napoli.");
        var city = player.level();
        long day = NapoliBroadcast.day(city);
        var buttons = NapoliEventCatalogue.VENUES.stream()
                .sorted(java.util.Comparator.comparingInt(Venue::opens))
                .map(venue -> CitySocial.button(Component.literal(clock(venue.opens()) + "–"
                                + clock(venue.opens() + DURATION) + "  " + venue.scene(city.getSeed(), day).title()),
                        "/napoli meta " + venue.place()))
                .toList();
        var title = Component.literal("Eventi di Napoli · giorno " + day);
        var help = new PlainMessage(Component.literal("Scegli un evento per seguirne la destinazione. Sul posto usa /citta partecipa."), 290);
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(title, Optional.empty(),
                true, false, DialogAction.CLOSE, List.of(help), List.of()), buttons, Optional.empty(), 1)));
        return buttons.size();
    }

    private static int participate(ServerPlayer player, int answer) {
        if (!eligible(player)) return tell(player, "Puoi partecipare agli eventi quando sei a Napoli.");
        var city = player.level();
        long time = NapoliBroadcast.time(city);
        Venue venue = NapoliEventCatalogue.VENUES.stream()
                .filter(v -> v.open(time) && player.blockPosition().distSqr(v.point()) <= PARTICIPATE * PARTICIPATE)
                .findFirst().orElse(null);
        if (venue == null) return tell(player, "Avvicinati al luogo dell'evento durante il suo orario. /citta eventi");
        return takePart(player, venue, venue.scene(city.getSeed(), NapoliBroadcast.day(city)),
                NapoliBroadcast.day(city), answer);
    }

    static int takePart(ServerPlayer player, Venue venue, Scene scene, long day, int answer) {
        if (usedToday(player, CLAIMED, venue, day)) return tell(player, "Hai già partecipato a questo evento oggi.");
        if (scene.quiz() != null) {
            Quiz quiz = scene.quiz();
            if (answer == 0) {
                var prompt = new PlainMessage(Component.literal(scene.description()), 260);
                var title = Component.literal(scene.title());
                var buttons = List.of(CitySocial.button(quiz.first(), "/citta partecipa 1"),
                        CitySocial.button(quiz.second(), "/citta partecipa 2"));
                player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(title, Optional.empty(),
                        true, false, DialogAction.CLOSE, List.of(prompt), List.of()), buttons, Optional.empty(), 1)));
                return 1;
            }
            if (answer != quiz.answer()) return tell(player, "Riprova. " + scene.description());
        }

        if (scene.donation() != null) {
            ItemStack donation = player.getMainHandItem().is(scene.donation()) ? player.getMainHandItem() : player.getOffhandItem();
            if (!donation.is(scene.donation())) {
                var itemName = new ItemStack(scene.donation()).getHoverName();
                NapoliNotice.show(player, Component.literal("Tieni in mano ").append(itemName).append("."));
                return 0;
            }
            if (!player.isCreative()) donation.shrink(1);
        }

        recordDay(player, CLAIMED, venue, day);
        var reward = new ItemStack(scene.reward());
        var name = reward.getHoverName();
        if (!player.getInventory().add(reward)) player.spawnAtLocation(player.level(), reward);
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        NapoliNotice.show(player, Component.literal(scene.description() + " Hai ricevuto ").append(name));
        CityEffects.sound(player.level(), null, player.blockPosition(), venue.sound(), SoundSource.AMBIENT, .7f, 1.15f);
        return 1;
    }

    private static boolean eligible(ServerPlayer player) {
        return player.level().dimension() == NapoliMap.LEVEL && player.isAlive() && !player.isSpectator();
    }

    private static boolean usedToday(ServerPlayer player, AttachmentType<Map<String, Long>> type, Venue venue, long day) {
        return player.getAttachedOrElse(type, Map.of()).getOrDefault(venue.place(), Long.MIN_VALUE) >= day;
    }

    private static boolean recordDay(ServerPlayer player, AttachmentType<Map<String, Long>> type, Venue venue, long day) {
        if (usedToday(player, type, venue, day)) return false;
        var records = new HashMap<>(player.getAttachedOrElse(type, Map.of()));
        records.put(venue.place(), day);
        player.setAttached(type, Map.copyOf(records));
        return true;
    }

    private static int tell(ServerPlayer player, String message) {
        NapoliNotice.show(player, Component.literal(message));
        return 0;
    }

    private static String clock(int ticks) {
        int minutes = (ticks * 60 / 1000 + 360) % 1440;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
