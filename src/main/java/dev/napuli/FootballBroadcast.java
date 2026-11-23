package dev.napuli;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FootballBroadcast {
    private FootballBroadcast() {}

    public record Frame(boolean open, int mode, int minute, int napoli, int juventus, long tick)
            implements CustomPacketPayload {
        public static final int LIVE = 0, REPLAY = 1, FINAL = 2, CLOSED = 3;
        public static final Type<Frame> TYPE = new Type<>(Napuli.id("football_frame"));
        static final StreamCodec<FriendlyByteBuf, Frame> CODEC = StreamCodec.of((buf, frame) -> {
            buf.writeBoolean(frame.open());
            buf.writeByte(frame.mode());
            buf.writeByte(frame.minute());
            buf.writeByte(frame.napoli());
            buf.writeByte(frame.juventus());
            buf.writeVarLong(frame.tick());
        }, buf -> new Frame(buf.readBoolean(), buf.readUnsignedByte(), buf.readUnsignedByte(),
                buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readVarLong()));
        @Override public Type<Frame> type() { return TYPE; }
    }

    public record Leave() implements CustomPacketPayload {
        public static final Type<Leave> TYPE = new Type<>(Napuli.id("football_leave"));
        static final StreamCodec<FriendlyByteBuf, Leave> CODEC = StreamCodec.unit(new Leave());
        @Override public Type<Leave> type() { return TYPE; }
    }
}
