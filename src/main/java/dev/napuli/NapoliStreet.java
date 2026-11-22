package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class NapoliStreet {
    private static final AttachmentType<Integer> CASH = saved("cash", Codec.INT);
    private static final AttachmentType<Integer> FULLNESS = saved("fullness", Codec.INT);
    private static final AttachmentType<Long> DELIVERY = saved("delivery", Codec.LONG);
    private static final AttachmentType<Long> GAME = saved("cups", Codec.LONG);
    private static final AttachmentType<Session> DIALOG = AttachmentRegistry.create(Napuli.id("street_dialog"));
    private static final AttachmentType<Long> NEXT_THIEF = AttachmentRegistry.create(Napuli.id("street_next_thief"));
    private static final AttachmentType<Long> TROUBLE = AttachmentRegistry.create(Napuli.id("street_trouble"));
    private static final AttachmentType<Long> NEXT_CLASH = AttachmentRegistry.create(Napuli.id("street_clash"));
    private static final net.minecraft.resources.Identifier SIZE = Napuli.id("diet_size");
    private static final net.minecraft.resources.Identifier SPEED = Napuli.id("diet_speed");
    private static final AttachmentType<Long> ALLY_COOLDOWN = AttachmentRegistry.create(Napuli.id("ally_cooldown"));
    private static final AttachmentType<UUID> ALLY_OWNER = AttachmentRegistry.create(Napuli.id("ally_owner"));
    private static final AttachmentType<Long> ALLY_LIFE = saved("ally_life", Codec.LONG);
    private static final AttachmentType<Long> CREW = saved("crew_expires", Codec.LONG);
    private static final BlockPos DOCK = new BlockPos(13, 64, 52);

    private record Spot(NapoliResident.Role role, BlockPos pos, float yaw) {}
    private record Session(NapoliResident npc, String token, long expires, List<Action> allowed) {}
    private record Offer(ItemStack stack, int cost) {}
    private enum Action { PIZZA, BREAD, COFFEE, SWORD, COAT, CHARM, DELIVERY, TURN_IN, LEFT, MIDDLE, RIGHT, HELP_SUPPLY, HELP_CLASH, CONTACT, REPORT, ABANDON, LEVY, CACHE, COLLECT, TAKE_LEDGER, KNUCKLES, KNIFE, PISTOL, BATON }

    private static final List<Spot> SPOTS = List.of(
        new Spot(NapoliResident.Role.VENDOR, NapoliDistrict.PUTECA, 0),        // his own shop in the Galleria
        new Spot(NapoliResident.Role.TAILOR, NapoliDistrict.SARTORIA, 0),
        new Spot(NapoliResident.Role.BUSKER, NapoliDistrict.BUSKER_SPOT, 180),
        new Spot(NapoliResident.Role.PORTER, DOCK, 180),
        new Spot(NapoliResident.Role.PASSERBY, new BlockPos(-21, 64, -37), 0),
        new Spot(NapoliResident.Role.PASSERBY, new BlockPos(-27, 64, 30), 0),
        new Spot(NapoliResident.Role.PASSERBY, new BlockPos(20, 64, 51), 180),
        new Spot(NapoliResident.Role.PASSERBY, new BlockPos(118, 64, -60), 0),
        new Spot(NapoliResident.Role.PASSERBY, new BlockPos(31, 64, -7), 90),
        new Spot(NapoliResident.Role.ADA, new BlockPos(-33, 64, -3), 0),
        new Spot(NapoliResident.Role.MICHELE, new BlockPos(-48, 64, -6), 0),
        new Spot(NapoliResident.Role.SALVATORE, new BlockPos(26, 64, 75), 180),
        new Spot(NapoliResident.Role.ROSA, new BlockPos(32, 64, 75), 180));

    private NapoliStreet() {}

    private static <T> AttachmentType<T> saved(String id, Codec<T> codec) {
        return AttachmentRegistry.<T>builder().persistent(codec).copyOnDeath().buildAndRegister(Napuli.id(id));
    }

    static void register() {
        NapoliFactions.register();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, dealt, blocked) -> {
            if (dealt <= 0 || !(victim instanceof ServerPlayer player) || player.level().dimension() != NapoliMap.LEVEL
                    || !(source.getEntity() instanceof NapoliResident rival) || !rival.role().fighter()) return;
            support(player, rival);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
            if (entity.getAttached(CREW) == null || !(entity.level() instanceof ServerLevel level)
                    || level.dimension() != NapoliMap.LEVEL) return;
            var attacker = damage.getEntity();
            ServerPlayer player = attacker instanceof ServerPlayer direct ? direct : null;
            if (player == null && attacker instanceof NapoliResident npc) {
                UUID owner = npc.getAttached(ALLY_OWNER);
                if (owner != null) player = level.getServer().getPlayerList().getPlayer(owner);
                if (player != null && (player.level() != level || player.distanceToSqr(entity) > 32 * 32)) player = null;
            }
            if (player != null) {
                if (entity instanceof NapoliResident npc) NapoliFactions.rivalDefeated(player, NapoliFactions.Side.of(npc.role()));
                else {
                    credit(player, 4);
                    message(player, "reward", 4);
                }
            }
        });
    }

    static void commands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("portafoglio").executes(c -> {
            var p = c.getSource().getPlayerOrException();
            message(p, "stats", cash(p), p.getAttachedOrElse(FULLNESS, 0));
            p.sendSystemMessage(NapoliFactions.status(p));
            return 1;
        }));
        dispatcher.register(Commands.literal("napulistrada").then(Commands.argument("turno", StringArgumentType.word())
                .then(Commands.argument("scelta", StringArgumentType.word()).executes(c -> {
                    try {
                        return choose(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "turno"),
                                Action.valueOf(StringArgumentType.getString(c, "scelta")));
                    } catch (IllegalArgumentException invalid) { return 0; }
                }))));
    }

    static void tick(ServerLevel level, ServerPlayer player, long now) {
        int stored = player.getAttachedOrElse(FULLNESS, 0);
        if (stored > 0 && level.getServer().getTickCount() % (20 * 60) == 0) player.setAttached(FULLNESS, stored - 1);
        applyWeight(player);
        NapoliFactions.recognize(player);
        for (var ally : level.getEntitiesOfClass(NapoliResident.class, player.getBoundingBox().inflate(35),
                npc -> npc.getAttachedOrElse(ALLY_LIFE, Long.MAX_VALUE) <= now)) ally.discard();
        for (Spot spot : SPOTS) {
            if (spot.pos().distSqr(player.blockPosition()) > 35 * 35 || !level.hasChunkAt(spot.pos())) continue;
            var around = new AABB(spot.pos()).inflate(10, 3, 10);
            if (level.getEntitiesOfClass(NapoliResident.class, around, npc -> npc.role() == spot.role()).isEmpty())
                NapoliResident.spawn(level, spot.role(), spot.pos(), spot.yaw());
        }
        if (!player.isCreative() && now >= player.getAttachedOrElse(TROUBLE, now + 1)) {
            player.removeAttached(TROUBLE);
            boolean giant = player.getAttachedOrElse(Napuli.RAGGIA, 0) >= 3;
            if (giant && !level.isBrightOutside() && Spawner.spawnGigante(level, player)) player.setAttached(Napuli.RAGGIA, 0);
            else if (!giant && level.getEntities(Napuli.MUNACIELLO, m -> m.distanceToSqr(player) < 28 * 28).isEmpty()
                    && Spawner.spawnMunaciello(level, player)) message(player, "theft");
        }
        if (DOCK.distSqr(player.blockPosition()) < 30 * 30) {
            var rivals = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(DOCK).inflate(45),
                    entity -> entity.getAttached(CREW) != null);
            for (var rival : rivals) if (rival.getAttachedOrElse(CREW, 0L) <= now) rival.discard();
            if (!player.isCreative() && !level.isBrightOutside() && DOCK.distSqr(player.blockPosition()) < 22 * 22
                    && now >= player.getAttachedOrElse(NEXT_CLASH, 0L) && rivals.isEmpty()) encounter(level, player, now);
        }
    }

    private static void encounter(ServerLevel level, ServerPlayer player, long now) {
        player.setAttached(NEXT_CLASH, now + 20L * 70);
        message(player, "clash");
        Music.play(player, Music.Situation.LITE);
        var nico = NapoliResident.create(level, NapoliResident.Role.NICO, new BlockPos(14, 64, 47), 0);
        var antonio = NapoliResident.create(level, NapoliResident.Role.ANTONIO, new BlockPos(16, 64, 51), 180);
        nico.setAttached(CREW, now + 20 * 65);
        antonio.setAttached(CREW, now + 20 * 65);
        level.addFreshEntity(nico);
        level.addFreshEntity(antonio);
        nico.setTarget(antonio);
        antonio.setTarget(nico);
        var side = NapoliFactions.ally(player);
        if (side != null) support(player, side == NapoliFactions.Side.LANTERNE ? antonio : nico);
    }

    static boolean confront(ServerPlayer player, NapoliFactions.Side side) {
        var level = player.level();
        var at = Spawner.groundNear(level, player, 5, 9);
        if (at == null) return false;
        var role = side == NapoliFactions.Side.LANTERNE ? NapoliResident.Role.NICO : NapoliResident.Role.ANTONIO;
        var challenger = NapoliResident.spawn(level, role, at, 0);
        challenger.setAttached(ALLY_LIFE, level.getGameTime() + 20 * 35);
        challenger.setTarget(player);
        return true;
    }

    private static void support(ServerPlayer player, NapoliResident rival) {
        var side = NapoliFactions.ally(player);
        if (side == null || side == NapoliFactions.Side.of(rival.role()) || !rival.isAlive()) return;
        long now = player.level().getGameTime();
        if (now < player.getAttachedOrElse(ALLY_COOLDOWN, 0L)) return;
        var friendlyRole = side == NapoliFactions.Side.LANTERNE ? NapoliResident.Role.NICO : NapoliResident.Role.ANTONIO;
        var nearby = player.level().getEntitiesOfClass(NapoliResident.class, player.getBoundingBox().inflate(18),
            npc -> npc.isAlive() && npc.role() == friendlyRole);
        var friendly = nearby.isEmpty() ? NapoliResident.spawn(player.level(), friendlyRole,
                player.blockPosition(), player.getYRot()) : nearby.getFirst();
        friendly.setAttached(ALLY_LIFE, now + 20 * 45);
        friendly.setAttached(ALLY_OWNER, player.getUUID());
        friendly.setTarget(rival);
        rival.setTarget(friendly);
        player.setAttached(ALLY_COOLDOWN, now + 20 * 60);
        message(player, "support", friendly.getDisplayName());
    }

    public static void ate(ServerPlayer player, ItemStack food) {
        var nutrition = food.get(DataComponents.FOOD);
        if (nutrition == null || nutrition.nutrition() == 0 || player.level().dimension() != NapoliMap.LEVEL || player.isCreative()) return;
        int before = player.getAttachedOrElse(FULLNESS, 0);
        int amount = player.getFoodData().getFoodLevel() >= 16 ? nutrition.nutrition() : 0;
        if (amount == 0) return;
        int after = Math.min(60, before + amount);
        player.setAttached(FULLNESS, after);
        applyWeight(player);
        if (before < 16 && after >= 16) message(player, "full");
    }

    private static void applyWeight(ServerPlayer player) {
        int load = player.level().dimension() == NapoliMap.LEVEL ? player.getAttachedOrElse(FULLNESS, 0) : 0;
        double excess = Math.max(0, load - 15) / 45.0;
        modifier(player.getAttribute(Attributes.SCALE), SIZE, excess * 0.16);
        modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, -excess * 0.18);
    }

    static void resetWeightOutside(ServerPlayer player) {
        if (player.getAttribute(Attributes.SCALE).hasModifier(SIZE)) applyWeight(player);
    }

    private static void modifier(net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
                                 net.minecraft.resources.Identifier id, double amount) {
        if (amount == 0) attribute.removeModifier(id);
        else if (attribute.getModifier(id) == null || attribute.getModifier(id).amount() != amount)
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    static void open(ServerPlayer player, NapoliResident npc) {
        if (player.level().dimension() != NapoliMap.LEVEL || !npc.role().street()) return;
        List<Action> options = switch (npc.role()) {
            case VENDOR -> NapoliFactions.onErrand(player, false)
                    ? List.of(Action.PIZZA, Action.BREAD, Action.COFFEE, Action.DELIVERY, Action.COLLECT)
                    : List.of(Action.PIZZA, Action.BREAD, Action.COFFEE, Action.DELIVERY);
            case TAILOR -> {
                var gear = new java.util.ArrayList<>(List.of(Action.KNUCKLES, Action.KNIFE, Action.PISTOL, Action.BATON, Action.COAT, Action.CHARM));
                if (NapoliFactions.onErrand(player, true)) gear.add(Action.TAKE_LEDGER);
                yield gear;
            }
            case PORTER -> List.of(Action.TURN_IN);
            case BUSKER -> List.of(Action.LEFT, Action.MIDDLE, Action.RIGHT);
            case ADA, SALVATORE -> {
                var jobs = new java.util.ArrayList<>(List.of(Action.HELP_SUPPLY, Action.HELP_CLASH, Action.REPORT, Action.ABANDON));
                int rank = NapoliFactions.reputation(player, NapoliFactions.Side.of(npc.role()));
                if (rank >= NapoliFactions.LEVY_REPUTATION) jobs.add(Action.LEVY);
                if (rank >= NapoliFactions.CACHE_REPUTATION) jobs.add(Action.CACHE);
                yield jobs;
            }
            case MICHELE, ROSA -> List.of(Action.CONTACT);
            default -> List.of();
        };
        if (options.isEmpty()) { message(player, levelGreeting(player)); return; }
        String token = UUID.randomUUID().toString();
        player.setAttached(DIALOG, new Session(npc, token, player.level().getGameTime() + 1200, options));
        var actions = options.stream().map(action -> {
            Offer offer = offer(action);
            Component label = offer == null ? Component.translatable(key(action))
                    : Component.translatable("napuli.street.price", offer.stack().getHoverName(), offer.cost());
            return new ActionButton(new CommonButtonData(label, 220),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/napulistrada " + token + " " + action.name()))));
        }).toList();
        var body = new java.util.ArrayList<>(List.<net.minecraft.server.dialog.body.DialogBody>of(
            new PlainMessage(Component.translatable("napuli.street." + npc.role().id()), 270),
            new PlainMessage(Component.translatable("napuli.street.cash", cash(player)), 270),
            new PlainMessage(NapoliFactions.status(player), 270)));
        if (npc.role() == NapoliResident.Role.ADA || npc.role() == NapoliResident.Role.SALVATORE)
            body.add(new PlainMessage(Component.translatable("napuli.factions.thresholds"), 270));
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(npc.getDisplayName(), Optional.empty(),
                true, false, DialogAction.CLOSE, body, List.of()), actions, Optional.empty(), 1)));
    }

    private static String levelGreeting(ServerPlayer player) {
        return switch (player.getRandom().nextInt(3)) {
            case 0 -> "hello";
            case 1 -> "meme_ritardo";
            default -> "meme_nuttata";
        };
    }

    public static int cash(ServerPlayer player) { return player.getAttachedOrElse(CASH, 8); }

    public static void credit(ServerPlayer player, int amount) {
        player.setAttached(CASH, Math.clamp(cash(player) + amount, 0, 99999));
    }

    private static String key(Action action) {
        return "napuli.street." + action.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Offer offer(Action action) {
        return switch (action) {
            case PIZZA -> new Offer(new ItemStack(Napuli.PIZZA), 5);
            case BREAD -> new Offer(new ItemStack(Items.BREAD, 2), 3);
            case COFFEE -> new Offer(new ItemStack(Napuli.CAFE), 3);
            case SWORD -> new Offer(new ItemStack(Items.IRON_SWORD), 14);
            case KNUCKLES -> new Offer(new ItemStack(Napuli.KNUCKLES), 11);
            case KNIFE -> new Offer(new ItemStack(Napuli.KNIFE), 15);
            case PISTOL -> new Offer(new ItemStack(Napuli.PISTOL), 24);
            case BATON -> new Offer(new ItemStack(Napuli.BATON), 16);
            case COAT -> new Offer(new ItemStack(Items.LEATHER_CHESTPLATE), 11);
            case CHARM -> new Offer(new ItemStack(Napuli.CORNICELLO), 18);
            default -> null;
        };
    }

    private static int choose(ServerPlayer player, String token, Action action) {
        Session session = player.getAttached(DIALOG);
        if (session == null || !session.token().equals(token) || !session.allowed().contains(action)) return 0;
        player.removeAttached(DIALOG);
        long now = player.level().getGameTime();
        if (player.isSpectator() || player.level() != session.npc().level() || !session.npc().isAlive()
                || player.distanceToSqr(session.npc()) > 36 || now > session.expires()) return 0;
        Offer offer = offer(action);
        if (offer != null) {
            if (cash(player) < offer.cost()) { message(player, "poor"); return 0; }
            credit(player, -offer.cost());
            player.addItem(offer.stack());
            if (!offer.stack().isEmpty()) player.spawnAtLocation(player.level(), offer.stack());
            message(player, "cash", cash(player));
            scheduleThief(player, now);
            return 1;
        }
        var side = NapoliFactions.Side.of(session.npc().role());
        switch (action) {
            case HELP_SUPPLY -> { return NapoliFactions.accept(player, side, false); }
            case HELP_CLASH -> { return NapoliFactions.accept(player, side, true); }
            case CONTACT -> { return NapoliFactions.handoff(player, side); }
            case REPORT -> { return NapoliFactions.finish(player, side); }
            case ABANDON -> { return NapoliFactions.cancel(player, side); }
            case LEVY -> { return NapoliFactions.advanced(player, side, false); }
            case CACHE -> { return NapoliFactions.advanced(player, side, true); }
            case COLLECT -> {
                int done = NapoliFactions.errand(player, false);
                if (done == 1) Carabinieri.crime(player, Carabinieri.Crime.PIZZO);
                return done;
            }
            case TAKE_LEDGER -> { return NapoliFactions.errand(player, true); }
            case DELIVERY -> {
                if (player.getAttachedOrElse(DELIVERY, 0L) > now) { message(player, "route"); return 0; }
                player.setAttached(DELIVERY, now + 20 * 240);
                message(player, "route");
            }
            case TURN_IN -> {
                if (player.getAttachedOrElse(DELIVERY, 0L) <= now) {
                    player.removeAttached(DELIVERY);
                    message(player, "no_delivery");
                    return 0;
                }
                player.removeAttached(DELIVERY);
                credit(player, 12);
                message(player, "reward", 12);
                scheduleThief(player, now);
            }
            default -> {
                if (cash(player) < 2) { message(player, "poor"); return 0; }
                if (now < player.getAttachedOrElse(GAME, 0L)) { message(player, "wait"); return 0; }
                player.setAttached(GAME, now + 20 * 30);
                boolean win = player.getRandom().nextInt(5) == 0;
                credit(player, win ? 4 : -2);
                message(player, win ? "cups_win" : "cups_lose");
            }
        }
        return 1;
    }

    private static void scheduleThief(ServerPlayer player, long now) {
        if (player.getAttached(TROUBLE) == null && now >= player.getAttachedOrElse(NEXT_THIEF, 0L)) {
            player.setAttached(TROUBLE, now + 20 * 16);
            player.setAttached(NEXT_THIEF, now + 20 * 80);
        }
    }

    private static void message(ServerPlayer player, String key, Object... arguments) {
        player.sendSystemMessage(Component.translatable("napuli.street." + key, arguments));
    }
}
