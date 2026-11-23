package dev.napuli;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Dialogue is attached to its real speaker; ordinary game notices use NapoliNotice. */
public record NpcSpeech(UUID speaker, Component message) implements CustomPacketPayload {
    public static final int RANGE = 32;
    public static final Type<NpcSpeech> TYPE = new Type<>(Napuli.id("npc_speech"));
    static final StreamCodec<RegistryFriendlyByteBuf, NpcSpeech> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, NpcSpeech::speaker,
            ComponentSerialization.STREAM_CODEC, NpcSpeech::message, NpcSpeech::new);

    static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }

    public static boolean say(Player viewer, NapoliResident speaker, Component message) {
        if (!(viewer instanceof ServerPlayer player) || !speaker.isAlive() || player.level() != speaker.level()
                || player.distanceToSqr(speaker) > RANGE * RANGE) return false;
        if (ServerPlayNetworking.canSend(player, TYPE))
            ServerPlayNetworking.send(player, new NpcSpeech(speaker.getUUID(), message));
        else player.sendOverlayMessage(Component.empty().append(speaker.getDisplayName()).append(": ").append(message));
        return true;
    }

    static boolean say(ServerPlayer viewer, UUID speaker, Component message) {
        return viewer.level().getEntity(speaker) instanceof NapoliResident npc && say(viewer, npc, message);
    }

    @Override public Type<NpcSpeech> type() { return TYPE; }
}
