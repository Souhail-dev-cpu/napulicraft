package dev.napuli;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public record FootballAction(int actor, int move, boolean left) implements CustomPacketPayload {
    public enum Move {
        PASS(8), DRIVE(11), CURL(12), CHIP(11), ROULETTE(13), FEINT(10), SIDE_STEP(9);

        public final int ticks;
        Move(int ticks) { this.ticks = ticks; }
    }

    public static final Type<FootballAction> TYPE = new Type<>(Napuli.id("football_action"));
    static final StreamCodec<ByteBuf, FootballAction> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FootballAction::actor, ByteBufCodecs.VAR_INT, FootballAction::move,
            ByteBufCodecs.BOOL, FootballAction::left, FootballAction::new);

    static void play(LivingEntity actor, Move move, boolean left) {
        if (actor == null) return;
        var packet = new FootballAction(actor.getId(), move.ordinal(), left);
        for (ServerPlayer viewer : PlayerLookup.tracking(actor)) {
            if (viewer != actor && ServerPlayNetworking.canSend(viewer, TYPE)) ServerPlayNetworking.send(viewer, packet);
        }
        if (actor instanceof ServerPlayer player && ServerPlayNetworking.canSend(player, TYPE))
            ServerPlayNetworking.send(player, packet);
    }

    @Override public Type<FootballAction> type() { return TYPE; }
}
