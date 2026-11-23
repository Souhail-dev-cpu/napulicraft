package dev.napuli;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

final class NapoliProperties {
    private NapoliProperties() {}

    static final AttachmentType<Map<String, UUID>> OWNERS = AttachmentRegistry.<Map<String, UUID>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC)).buildAndRegister(Napuli.id("property_owners"));

    static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.dimension() != NapoliMap.LEVEL)
                return InteractionResult.PASS;
            for (var house : NapoliMergellina.PROPERTIES) {
                if (hit.getBlockPos().equals(house.sign())) {
                    if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) open(serverPlayer, house);
                    return InteractionResult.SUCCESS;
                }
                if ((house.contains(hit.getBlockPos()) || house.contains(hit.getBlockPos().relative(hit.getDirection())))
                        && player instanceof ServerPlayer serverPlayer && !owns(serverPlayer, house)) {
                    say(serverPlayer, "private");
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            if (!(player instanceof ServerPlayer p) || level.dimension() != NapoliMap.LEVEL) return true;
            return NapoliMergellina.PROPERTIES.stream().noneMatch(house ->
                    house.sign().equals(pos) || house.contains(pos) && !owns(p, house));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 != 0) return;
            ServerLevel city = server.getLevel(NapoliMap.LEVEL);
            if (city == null) return;
            for (ServerPlayer player : city.players()) {
                if (player.isSpectator()) continue;
                for (var house : NapoliMergellina.PROPERTIES) if (house.contains(player.blockPosition()) && !owns(player, house)) {
                    visit(player, house.entrance().south());
                    say(player, "private");
                    break;
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(Commands.literal("casa")
                        .executes(c -> list(c.getSource().getPlayerOrException()))
                        .then(Commands.literal("compra").then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> buy(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("vai").then(Commands.argument("id", StringArgumentType.word())
                                .executes(c -> go(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id")))))));
    }

    private static int list(ServerPlayer player) {
        if (player.level().dimension() != NapoliMap.LEVEL) { say(player, "city"); return 0; }
        var actions = NapoliMergellina.PROPERTIES.stream().map(house -> {
            boolean owned = owns(player, house);
            var label = owned ? Component.translatable("napuli.property.enter", house.name())
                    : Component.translatable("napuli.property.locate", house.name(), house.sign().getX(), house.sign().getZ());
            return owned ? button(label, "/casa vai " + house.id()) : new ActionButton(new CommonButtonData(label, 250), Optional.empty());
        }).toList();
        show(player, Component.translatable("napuli.property.title"), Component.translatable("napuli.property.list"), actions);
        return 1;
    }

    private static void open(ServerPlayer player, NapoliMergellina.House house) {
        if (player.isSpectator()) return;
        UUID owner = owner(player.level(), house);
        if (owner != null && !owner.equals(player.getUUID())) { say(player, "sold"); return; }
        boolean owned = owner != null;
        var body = owned ? Component.translatable("napuli.property.mine")
                : Component.translatable("napuli.property.price", house.price(), NapoliStreet.cash(player));
        var action = button(Component.translatable(owned ? "napuli.property.go" : "napuli.property.buy"),
                "/casa " + (owned ? "vai " : "compra ") + house.id());
        show(player, Component.literal(house.name()), body, List.of(action));
    }

    private static int buy(ServerPlayer player, String id) {
        if (player.level().dimension() != NapoliMap.LEVEL || !player.isAlive() || player.isSpectator()) return 0;
        var house = find(id);
        if (house == null || house.sign().distToCenterSqr(player.position()) > 36) { say(player, "sign"); return 0; }
        if (owner(player.level(), house) != null) { say(player, "sold"); return 0; }
        if (NapoliStreet.cash(player) < house.price()) { say(player, "poor"); return 0; }
        NapoliStreet.credit(player, -house.price());
        var owners = new HashMap<>(player.level().getAttachedOrElse(OWNERS, Map.of()));
        owners.put(id, player.getUUID());
        player.level().setAttached(OWNERS, Map.copyOf(owners));
        player.sendSystemMessage(Component.translatable("napuli.property.bought", house.name()));
        return 1;
    }

    private static int go(ServerPlayer player, String id) {
        var house = find(id);
        if (house == null || player.level().dimension() != NapoliMap.LEVEL || !owns(player, house)
                || player.isSpectator() || Carabinieri.isJailed(player) || Carabinieri.isWanted(player)) return 0;
        visit(player, house.entrance().south());
        return 1;
    }

    private static void visit(ServerPlayer player, BlockPos pos) {
        player.stopRiding();
        player.teleportTo(player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 180, 0, true);
    }

    private static NapoliMergellina.House find(String id) {
        return NapoliMergellina.PROPERTIES.stream().filter(house -> house.id().equals(id)).findFirst().orElse(null);
    }

    static boolean owns(ServerPlayer player, NapoliMergellina.House house) {
        return player.getUUID().equals(owner(player.level(), house));
    }

    static boolean claimForResident(ServerLevel city, NapoliMergellina.House house, UUID resident) {
        UUID existing = owner(city, house);
        if (existing != null) return existing.equals(resident);
        var updated = new HashMap<>(city.getAttachedOrElse(OWNERS, Map.of()));
        updated.put(house.id(), resident);
        city.setAttached(OWNERS, Map.copyOf(updated));
        return true;
    }

    static UUID owner(ServerLevel city, NapoliMergellina.House house) {
        return city.getAttachedOrElse(OWNERS, Map.of()).get(house.id());
    }

    private static ActionButton button(Component label, String command) {
        return new ActionButton(new CommonButtonData(label, 250),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(command))));
    }

    private static void show(ServerPlayer player, Component title, Component body, List<ActionButton> buttons) {
        player.openDialog(Holder.direct(new MultiActionDialog(new CommonDialogData(title, Optional.empty(), true, false,
                DialogAction.CLOSE, List.of(new PlainMessage(body, 265)), List.of()), buttons, Optional.empty(), 1)));
    }

    private static void say(ServerPlayer player, String key) {
        player.sendOverlayMessage(Component.translatable("napuli.property." + key));
    }
}
