package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class NapoliLocalJobs {
    private NapoliLocalJobs() {}

    enum Job {
        PIZZA(NapoliResident.Role.PIZZAIOLO, 10, 2),
        BUCATO(NapoliResident.Role.BUCATO, 9, 0),
        TOURIST(NapoliResident.Role.TURISTA, 5, 0),
        FISH(NapoliResident.Role.PESCATORE, 12, 3),
        TUNNEL(NapoliResident.Role.CUSTODE_TUNNEL, 12, 0),
        CARDS(NapoliResident.Role.CARTOMANTE, 11, 1),
        MARKET(NapoliResident.Role.MERCATANTE, 0, 0),
        MUSIC(NapoliResident.Role.MUSICISTA, 9, 0),
        NATIVITY(NapoliResident.Role.GUARDIANO_PRESEPE, 10, 3);

        final NapoliResident.Role role;
        final int reward;
        final int amount;

        Job(NapoliResident.Role role, int reward, int amount) {
            this.role = role;
            this.reward = reward;
            this.amount = amount;
        }

        int bit() { return 1 << ordinal(); }
        String key() { return "napuli.local." + role.id(); }

        Item ingredient() {
            return switch (this) {
                case PIZZA -> Napuli.PIZZA;
                case FISH -> Items.COD;
                case CARDS -> Items.AMETHYST_SHARD;
                case NATIVITY -> Items.CANDLE;
                default -> throw new IllegalStateException(name());
            };
        }
    }

    private record Demo(NapoliResident npc, long start) {}
    private record Session(NapoliResident npc, Job job, String token, List<String> actions, long expires) {}
    private record Offer(Item item, int count, int price) {
        ItemStack stack() { return new ItemStack(item, count); }
    }

    private static final AttachmentType<Integer> ACCEPTED = saved("local_jobs_accepted");
    private static final AttachmentType<Integer> COMPLETED = saved("local_jobs_completed");
    private static final AttachmentType<Integer> VISITED = saved("local_jobs_visited");
    private static final AttachmentType<Session> SESSION = AttachmentRegistry.create(Napuli.id("local_jobs_menu"));
    private static final AttachmentType<Integer> BEAT = AttachmentRegistry.create(Napuli.id("local_jobs_beat"));
    private static final AttachmentType<Demo> DEMO = AttachmentRegistry.create(Napuli.id("local_jobs_demo"));
    private static final List<Float> PITCHES = List.of(0.75f, 1.0f, 1.33f);
    private static final List<String> NOTES = List.of("low", "middle", "high");
    private static final List<Integer> MELODY = List.of(0, 2, 1, 0);

    private static AttachmentType<Integer> saved(String id) {
        return AttachmentRegistry.<Integer>builder().persistent(Codec.INT).copyOnDeath().buildAndRegister(Napuli.id(id));
    }

    static Job job(NapoliResident.Role role) {
        return switch (role) {
            case PIZZAIOLO -> Job.PIZZA;
            case BUCATO -> Job.BUCATO;
            case TURISTA -> Job.TOURIST;
            case PESCATORE -> Job.FISH;
            case CUSTODE_TUNNEL -> Job.TUNNEL;
            case CARTOMANTE -> Job.CARDS;
            case MERCATANTE -> Job.MARKET;
            case MUSICISTA -> Job.MUSIC;
            case GUARDIANO_PRESEPE -> Job.NATIVITY;
            default -> null;
        };
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            dispatcher.register(Commands.literal("napolivicolo").then(Commands.argument("token", StringArgumentType.word())
                .then(Commands.argument("action", StringArgumentType.word()).executes(context ->
                    choose(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "token"),
                        StringArgumentType.getString(context, "action")))))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) playDemo(player);
            if (server.getTickCount() % 10 != 0) return;
            var city = server.getLevel(NapoliMap.LEVEL);
            if (city != null) for (ServerPlayer player : city.players()) {
                visit(player, Job.BUCATO, NapoliLocalMap.ROOF_MARK);
                visit(player, Job.TUNNEL, NapoliLocalMap.CISTERN);
            }
        });
    }

    private static void visit(ServerPlayer player, Job job, BlockPos destination) {
        if (player.isSpectator() || !player.isAlive() || !has(player, ACCEPTED, job)
                || has(player, VISITED, job) || player.blockPosition().distSqr(destination) > 9) return;
        mark(player, VISITED, job);
        say(player, "return");
    }

    static NapoliFactions.Objective objective(ServerPlayer player) {
        for (Job job : Job.values()) {
            if (!has(player, ACCEPTED, job) || has(player, COMPLETED, job)) continue;
            boolean visited = has(player, VISITED, job);
            BlockPos target = switch (job) {
                case BUCATO -> visited ? NapoliLocalMap.ROOF : NapoliLocalMap.ROOF_MARK;
                case TUNNEL -> visited ? NapoliLocalMap.TUNNEL_START : NapoliLocalMap.CISTERN;
                default -> null;
            };
            return new NapoliFactions.Objective(visited ? "napuli.local.return" : job.key() + ".task", target);
        }
        return null;
    }

    static void open(ServerPlayer player, NapoliResident npc, Job job) {
        var options = new ArrayList<String>();
        options.add("learn");
        if (job.reward > 0 && !has(player, COMPLETED, job)) {
            options.add(has(player, ACCEPTED, job) ? "report" : "accept");
        }
        switch (job) {
            case PIZZA -> options.add("pizza");
            case BUCATO, TUNNEL -> options.add("access");
            case TOURIST -> {
                if (has(player, ACCEPTED, job) && !has(player, COMPLETED, job))
                    options.addAll(List.of("gallery", "vesuvius", "crypt"));
            }
            case MARKET -> options.addAll(List.of("bread", "beetroot", "milk", "candle", "fish", "firewood"));
            case MUSIC -> {
                options.add("listen");
                if (!has(player, COMPLETED, job) && has(player, ACCEPTED, job)) options.addAll(NOTES);
            }
            default -> {}
        }

        String token = UUID.randomUUID().toString();
        player.setAttached(SESSION, new Session(npc, job, token, List.copyOf(options), player.level().getGameTime() + 1200));
        var buttons = options.stream().map(action -> {
            Offer offer = offer(action);
            Component label = offer == null ? Component.translatable("napuli.local.button." + action)
                    : Component.translatable("napuli.local.offer", offer.count(), offer.stack().getHoverName(), offer.price());
            return new ActionButton(new CommonButtonData(label, 220), Optional.of(
                    new StaticAction(new ClickEvent.RunCommand("/napolivicolo " + token + " " + action))));
        }).toList();
        var body = List.<net.minecraft.server.dialog.body.DialogBody>of(
                new PlainMessage(Component.translatable(job.key() + ".intro"), 265),
                new PlainMessage(Component.translatable("napuli.street.cash", NapoliStreet.cash(player)), 265));
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(npc.getDisplayName(), Optional.empty(),
                true, false, DialogAction.CLOSE, body, List.of()), buttons, Optional.empty(), 1)));
    }

    private static int choose(ServerPlayer player, String token, String action) {
        Session s = player.getAttached(SESSION);
        if (s == null || !s.token().equals(token) || !s.actions().contains(action)
                || !s.npc().isAlive() || s.npc().level() != player.level() || s.npc().distanceToSqr(player) > 36
                || player.level().getGameTime() > s.expires() || player.isSpectator()) return 0;
        player.removeAttached(SESSION);
        Job job = s.job();
        switch (action) {
            case "learn" -> {
                player.sendSystemMessage(Component.translatable(job.key() + ".lesson"));
                if (job == Job.PIZZA) {
                    for (String id : List.of("pizza", "pizza_marinara")) {
                        var recipe = net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE, Napuli.id(id));
                        player.level().getServer().getRecipeManager().byKey(recipe)
                                .ifPresent(holder -> player.awardRecipes(List.of(holder)));
                    }
                }
            }
            case "accept" -> {
                mark(player, ACCEPTED, job);
                if (job == Job.FISH) give(player, new ItemStack(Items.FISHING_ROD));
                NapoliNotice.show(player, Component.translatable(job.key() + ".task"));
            }
            case "report" -> report(player, job);
            case "access" -> {
                if (player.level().dimension() != NapoliMap.LEVEL) return 0;
                if (job == Job.BUCATO) {
                    if (!has(player, ACCEPTED, job)) { say(player, "accept_first"); return 0; }
                    travel(player, s.npc().getY() > 80 ? NapoliLocalMap.LAUNDRY : NapoliLocalMap.ROOF);
                } else travel(player, s.npc().getY() < 50 ? NapoliLocalMap.TUNNEL_ENTRY : NapoliLocalMap.TUNNEL_START);
            }
            case "gallery" -> {
                if (has(player, ACCEPTED, job) && !has(player, COMPLETED, job)) {
                    ItemStack souvenir = new ItemStack(Items.COMPASS);
                    souvenir.set(DataComponents.CUSTOM_NAME, Component.translatable("napuli.local.souvenir"));
                    give(player, souvenir);
                    complete(player, job);
                } else player.sendSystemMessage(Component.translatable(job.key() + ".lesson"));
            }
            case "vesuvius", "crypt" -> say(player, "wrong_direction");
            case "listen" -> {
                say(player, "melody");
                player.setAttached(DEMO, new Demo(s.npc(), player.level().getGameTime()));
            }
            case "low", "middle", "high" -> {
                int index = player.getAttachedOrElse(BEAT, 0);
                int note = NOTES.indexOf(action);
                player.level().playSound(null, s.npc().blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(),
                        SoundSource.RECORDS, 0.8f, PITCHES.get(note));
                if (note != MELODY.get(index)) {
                    player.removeAttached(BEAT);
                    say(player, "beat_retry");
                } else if (index == MELODY.size() - 1) {
                    player.removeAttached(BEAT);
                    complete(player, job);
                    Music.play(player, Music.Situation.AMORE);
                    return 1;
                } else {
                    player.setAttached(BEAT, index + 1);
                    NapoliNotice.show(player, Component.translatable("napuli.local.beat_progress", index + 1, MELODY.size()));
                }
                open(player, s.npc(), job);
            }
            default -> {
                Offer offer = offer(action);
                if (offer == null) return 0;
                if (NapoliStreet.cash(player) < offer.price()) { say(player, "poor"); return 0; }
                NapoliStreet.credit(player, -offer.price());
                give(player, offer.stack());
                say(player, "bought");
            }
        }
        return 1;
    }

    private static void playDemo(ServerPlayer player) {
        Demo demo = player.getAttached(DEMO);
        if (demo == null) return;
        long elapsed = player.level().getGameTime() - demo.start();
        if (elapsed < 0 || elapsed >= MELODY.size() * 8L || !demo.npc().isAlive()
                || demo.npc().level() != player.level() || demo.npc().distanceToSqr(player) > 100) {
            player.removeAttached(DEMO);
        } else if (elapsed % 8 == 0) {
            int note = MELODY.get((int) (elapsed / 8));
            player.level().playSound(null, demo.npc().blockPosition(), SoundEvents.NOTE_BLOCK_HARP.value(),
                    SoundSource.RECORDS, 0.8f, PITCHES.get(note));
        }
    }

    private static void report(ServerPlayer player, Job job) {
        if (job.amount > 0) {
            int count = 0;
            for (int slot = 0; slot < 36; slot++) {
                var stack = player.getInventory().getItem(slot);
                if (stack.is(job.ingredient())) count += stack.getCount();
            }
            if (count >= job.amount) {
                int remaining = job.amount;
                for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                    var stack = player.getInventory().getItem(slot);
                    if (stack.is(job.ingredient()))
                        remaining -= player.getInventory().removeItem(slot, remaining).getCount();
                }
                if (job == Job.CARDS) give(player, new ItemStack(Napuli.CORNICELLO));
                if (job == Job.NATIVITY) give(player, new ItemStack(Items.LANTERN));
                complete(player, job);
                return;
            }
        } else if ((job == Job.BUCATO || job == Job.TUNNEL) && has(player, VISITED, job)) {
            complete(player, job);
            return;
        }
        NapoliNotice.show(player, Component.translatable(job.key() + ".task"));
    }

    private static Offer offer(String action) {
        return switch (action) {
            case "pizza" -> new Offer(Napuli.PIZZA, 1, 5);
            case "bread" -> new Offer(Items.BREAD, 2, 3);
            case "firewood" -> new Offer(Items.OAK_LOG, 4, 3);
            case "beetroot" -> new Offer(Items.BEETROOT, 4, 4);
            case "milk" -> new Offer(Items.MILK_BUCKET, 1, 5);
            case "candle" -> new Offer(Items.CANDLE, 3, 5);
            case "fish" -> new Offer(Items.COOKED_COD, 2, 5);
            default -> null;
        };
    }

    private static void travel(ServerPlayer player, BlockPos pos) {
        player.stopRiding();
        player.teleportTo(player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 0, 0, true);
    }

    private static boolean has(ServerPlayer player, AttachmentType<Integer> state, Job job) {
        return (player.getAttachedOrElse(state, 0) & job.bit()) != 0;
    }

    private static void mark(ServerPlayer player, AttachmentType<Integer> state, Job job) {
        player.setAttached(state, player.getAttachedOrElse(state, 0) | job.bit());
    }

    private static void complete(ServerPlayer player, Job job) {
        if (has(player, COMPLETED, job)) return;
        mark(player, COMPLETED, job);
        NapoliStreet.credit(player, job.reward);
        NapoliNotice.show(player, Component.translatable("napuli.local.done", job.reward));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.spawnAtLocation(player.level(), stack);
    }

    private static void say(ServerPlayer player, String suffix) {
        NapoliNotice.show(player, Component.translatable("napuli.local." + suffix));
    }
}
