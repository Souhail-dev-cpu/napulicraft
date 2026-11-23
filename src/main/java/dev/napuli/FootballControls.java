package dev.napuli;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FootballControls(int situation) implements CustomPacketPayload {
    public static final int FREE = 0, YOURS = 1, TEAM = 2, OPPONENT = 3, TEAM_PASS = 4;
    public static final Type<FootballControls> TYPE = new Type<>(Napuli.id("football_controls"));
    static final StreamCodec<ByteBuf, FootballControls> CODEC =
            ByteBufCodecs.VAR_INT.map(FootballControls::new, FootballControls::situation);

    @Override public Type<FootballControls> type() { return TYPE; }
}
