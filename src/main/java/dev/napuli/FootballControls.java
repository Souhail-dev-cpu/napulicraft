package dev.napuli;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FootballControls(int situation, int stamina, boolean away) implements CustomPacketPayload {
    public static final int FREE = 0, YOURS = 1, TEAM = 2, OPPONENT = 3, TEAM_PASS = 4;
    public static final Type<FootballControls> TYPE = new Type<>(Napuli.id("football_controls_v3"));
    static final StreamCodec<ByteBuf, FootballControls> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, FootballControls::situation,
                    ByteBufCodecs.VAR_INT, FootballControls::stamina,
                    ByteBufCodecs.BOOL, FootballControls::away, FootballControls::new);

    @Override public Type<FootballControls> type() { return TYPE; }
}
