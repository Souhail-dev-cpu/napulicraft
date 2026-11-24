package dev.napuli;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Instructions for opt-in graphical multiplayer QA; never sent by ordinary game servers. */
public record CityVerification(String instruction) implements CustomPacketPayload {
    public static final Type<CityVerification> TYPE = new Type<>(Napuli.id("city_verification"));
    public static final StreamCodec<FriendlyByteBuf, CityVerification> CODEC = StreamCodec.of(
            (b, p) -> b.writeUtf(p.instruction), b -> new CityVerification(b.readUtf()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    static void register() { PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC); }
}
