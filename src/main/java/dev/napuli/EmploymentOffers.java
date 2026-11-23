package dev.napuli;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EmploymentOffers(List<Offer> offers, String current) implements CustomPacketPayload {
    public record Offer(String id, String name, int wage, BlockPos workplace) {}

    public static final Type<EmploymentOffers> TYPE = new Type<>(Napuli.id("employment_offers"));
    static final StreamCodec<FriendlyByteBuf, EmploymentOffers> CODEC = StreamCodec.of((buf, packet) -> {
        buf.writeUtf(packet.current());
        buf.writeVarInt(packet.offers().size());
        for (Offer offer : packet.offers()) {
            buf.writeUtf(offer.id());
            buf.writeUtf(offer.name());
            buf.writeVarInt(offer.wage());
            buf.writeBlockPos(offer.workplace());
        }
    }, buf -> {
        String current = buf.readUtf();
        int count = buf.readVarInt();
        if (count < 0 || count > 64) throw new IllegalArgumentException("Invalid employment offer count");
        var offers = new java.util.ArrayList<Offer>(count);
        for (int i = 0; i < count; i++)
            offers.add(new Offer(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBlockPos()));
        return new EmploymentOffers(List.copyOf(offers), current);
    });

    @Override public Type<EmploymentOffers> type() { return TYPE; }
}
