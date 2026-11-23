package dev.napuli;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record NapoliNotice(Component message) implements CustomPacketPayload {
    public static final Type<NapoliNotice> TYPE = new Type<>(Napuli.id("notice"));
    private static final StreamCodec<RegistryFriendlyByteBuf, NapoliNotice> CODEC =
            ComponentSerialization.STREAM_CODEC.map(NapoliNotice::new, NapoliNotice::message);

    static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }

    static void show(Player player, Component message) {
        if (player instanceof ServerPlayer server && ServerPlayNetworking.canSend(server, TYPE))
            ServerPlayNetworking.send(server, new NapoliNotice(message));
        else player.sendOverlayMessage(message);
    }

    @Override public Type<NapoliNotice> type() { return TYPE; }
}
