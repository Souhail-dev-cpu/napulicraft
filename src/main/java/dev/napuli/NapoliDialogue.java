package dev.napuli;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

final class NapoliDialogue {
    private static final int MAX_SCORE = 100;
    private static final int TALK_RANGE_SQUARED = 36;

    record Bond(int points, int interest, int passion, long day, int choices, boolean dating) {
        static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("points").forGetter(Bond::points),
                Codec.INT.fieldOf("interest").forGetter(Bond::interest),
                Codec.INT.fieldOf("passion").forGetter(Bond::passion),
                Codec.LONG.fieldOf("day").forGetter(Bond::day),
                Codec.INT.fieldOf("choices").forGetter(Bond::choices),
                Codec.BOOL.fieldOf("dating").forGetter(Bond::dating)).apply(i, Bond::new));
        static final Bond NEW = new Bond(0, 10, 0, -1, 0, false);
    }

    private enum Choice { LISTEN, JOKE, COMPLIMENT, COFFEE, WALK, ACCEPT, DECLINE, SPACE }
    private record Session(NapoliResident npc, String token, long expires, List<Choice> options) {}

    private static final AttachmentType<Map<String, Bond>> BONDS =
            AttachmentRegistry.<Map<String, Bond>>builder().persistent(Codec.unboundedMap(Codec.STRING, Bond.CODEC))
                    .copyOnDeath().buildAndRegister(Napuli.id("legami"));
    private static final AttachmentType<Session> SESSION = AttachmentRegistry.create(Napuli.id("dialogue"));

    private NapoliDialogue() {}

    static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("napuliparla")
                .then(Commands.argument("turno", StringArgumentType.word())
                        .then(Commands.argument("scelta", IntegerArgumentType.integer(0, Choice.values().length - 1))
                                .executes(c -> answer(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "turno"),
                                        Choice.values()[IntegerArgumentType.getInteger(c, "scelta")])))));
    }

    static Bond bond(ServerPlayer player, NapoliResident.Role role) {
        Bond bond = player.getAttachedOrElse(BONDS, Map.of()).getOrDefault(role.id(), Bond.NEW);
        long day = player.level().getOverworldClockTime() / 24000;
        return bond.day() == day ? bond : new Bond(bond.points(), bond.interest(), bond.passion(), day, 0, bond.dating());
    }

    static void open(ServerPlayer player, NapoliResident npc) {
        if (!canTalk(player, npc)) return;
        Bond bond = bond(player, npc.role());
        String key = bond.dating() ? "together" : bond.interest() >= 55 ? "flirt" : "hello";
        show(player, npc, bond, Component.translatable("napuli.dialog." + npc.role().id() + "." + key));
    }

    private static boolean canTalk(ServerPlayer player, NapoliResident npc) {
        return npc.isAlive() && npc.role().social() && !player.isSpectator()
                && player.level() == npc.level() && player.distanceToSqr(npc) <= TALK_RANGE_SQUARED;
    }

    private static boolean offered(Bond bond) {
        return !bond.dating() && bond.interest() >= 65 && bond.passion() >= 25 && (bond.choices() & (1 << Choice.DECLINE.ordinal())) == 0;
    }

    private static void show(ServerPlayer player, NapoliResident npc, Bond bond, Component line) {
        List<Choice> options = offered(bond) ? List.of(Choice.ACCEPT, Choice.DECLINE, Choice.SPACE)
                : List.of(Choice.LISTEN, Choice.JOKE, Choice.COMPLIMENT, Choice.COFFEE, Choice.WALK, Choice.SPACE);
        String token = UUID.randomUUID().toString();
        player.setAttached(SESSION, new Session(npc, token, player.level().getGameTime() + 20 * 90, options));
        var body = new java.util.ArrayList<net.minecraft.server.dialog.body.DialogBody>();
        body.add(new PlainMessage(line, 315));
        if (offered(bond)) body.add(new PlainMessage(Component.translatable("napuli.dialog." + npc.role().id() + ".invite"), 315));
        body.add(new PlainMessage(Component.translatable("napuli.dialog.stats", bond.points(), bond.interest(), bond.passion()), 315));
        var data = new CommonDialogData(npc.role().displayName(), Optional.empty(), true, false,
                DialogAction.CLOSE, body, List.of());
        var actions = options.stream().map(choice -> new ActionButton(
                new CommonButtonData(Component.translatable("napuli.dialog.choice." + choice.name().toLowerCase(java.util.Locale.ROOT)), 300),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/napuliparla " + token + " " + choice.ordinal()))))).toList();
        player.openDialog(Holder.direct(new MultiActionDialog(data, actions, Optional.empty(), 1)));
    }

    private static int answer(ServerPlayer player, String token, Choice choice) {
        Session session = player.getAttached(SESSION);
        if (session == null || !session.token().equals(token) || !session.options().contains(choice)) return 0;
        player.removeAttached(SESSION);
        NapoliResident npc = session.npc();
        if (!canTalk(player, npc) || player.level().getGameTime() > session.expires()) return 0;

        Bond old = bond(player, npc.role());
        long day = player.level().getOverworldClockTime() / 24000;
        int used = old.day() == day ? old.choices() : 0;
        int bit = 1 << choice.ordinal();
        if (choice == Choice.SPACE || choice == Choice.DECLINE) {
            if (choice == Choice.DECLINE) save(player, npc, new Bond(old.points(), old.interest(),
                    Math.max(0, old.passion() - 5), day, used | bit, old.dating()));
            player.sendSystemMessage(Component.translatable("napuli.dialog.reply." + (choice == Choice.SPACE ? "space" : "decline"), npc.role().displayName()));
            return 1;
        }
        if ((used & bit) != 0 && choice != Choice.ACCEPT) {
            show(player, npc, old, Component.translatable("napuli.dialog.reply.repeat"));
            return 1;
        }

        // every girl has her own character: what she likes more (listen / joke / coffee)
        String who = npc.role().id();
        int[] likes = switch (npc.role()) {
            case TERESA -> new int[] {12, 18, 16};   // the barista: laughs a lot, loves coffee talk
            case LUCIA -> new int[] {16, 10, 11};    // the artist: wants to be listened to
            case CARMELA -> new int[] {10, 14, 18};  // the pizzaiola: food and family first
            case GIUSY -> new int[] {8, 20, 10};     // the neomelodic singer: jokes and drama
            case SOFIA -> new int[] {20, 8, 12};     // the student: deep talks
            default -> new int[] {12, 12, 12};
        };
        int interest;
        int passion;
        String response;
        switch (choice) {
            case LISTEN -> { interest = likes[0]; passion = 2; response = "listen_" + who; }
            case JOKE -> { interest = likes[1]; passion = likes[1] >= 18 ? 7 : 3; response = "joke_" + who; }
            case COMPLIMENT -> {
                boolean welcome = old.interest() >= 30;
                interest = welcome ? 10 : -4;
                passion = welcome ? 14 : 0;
                response = welcome ? "compliment" : "slow";
            }
            case COFFEE -> { interest = likes[2]; passion = 6; response = "coffee"; }
            case WALK -> {
                boolean welcome = old.interest() >= 40;
                interest = welcome ? 14 : 0;
                passion = welcome ? 11 : 0;
                response = welcome ? "walk" : "slow";
            }
            case ACCEPT -> {
                if (!offered(old)) return 0;
                interest = 10; passion = 12; response = "accept";
            }
            default -> throw new IllegalStateException(choice.toString());
        }
        Bond updated = new Bond(old.points() + Math.max(0, interest + passion),
                Math.clamp(old.interest() + interest, 0, MAX_SCORE),
                Math.clamp(old.passion() + passion, 0, MAX_SCORE),
                day, used | bit, old.dating() || choice == Choice.ACCEPT);
        save(player, npc, updated);
        if (choice == Choice.ACCEPT) Music.play(player, Music.Situation.AMORE);
        show(player, npc, updated, Component.translatable("napuli.dialog.reply." + response));
        return 1;
    }

    private static void save(ServerPlayer player, NapoliResident npc, Bond updated) {
        var bonds = new HashMap<>(player.getAttachedOrElse(BONDS, Map.of()));
        bonds.put(npc.role().id(), updated);
        player.setAttached(BONDS, Map.copyOf(bonds));
    }
}
