package dev.napuli;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import io.netty.buffer.ByteBuf;

public record FootballMenu(boolean canReturn) implements CustomPacketPayload {
    public static final Type<FootballMenu> TYPE = new Type<>(Napuli.id("football_menu"));
    static final StreamCodec<ByteBuf, FootballMenu> CODEC =
            ByteBufCodecs.BOOL.map(FootballMenu::new, FootballMenu::canReturn);

    @Override public Type<FootballMenu> type() { return TYPE; }
}
