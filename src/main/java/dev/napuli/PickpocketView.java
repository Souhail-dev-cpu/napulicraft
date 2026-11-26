package dev.napuli;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Inventory snapshots are display-only; taking an item requires a checked server request. */
public record PickpocketView(UUID token, Component owner, int stolen, List<Entry> entries,
                             Component status, boolean open, boolean closed, boolean canTalk) implements CustomPacketPayload {
    public static final int MAX_SLOTS = 64;
    public record Entry(Component label, ItemStack stack, int available) {}
    public static final Type<PickpocketView> TYPE = new Type<>(Napuli.id("pickpocket_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PickpocketView> CODEC = StreamCodec.of((buf, view) -> {
        if (view.entries.size() > MAX_SLOTS) throw new IllegalArgumentException("Too many inventory slots");
        buf.writeUUID(view.token);
        ComponentSerialization.STREAM_CODEC.encode(buf, view.owner);
        buf.writeVarInt(view.stolen); buf.writeBoolean(view.open); buf.writeBoolean(view.closed); buf.writeBoolean(view.canTalk);
        ComponentSerialization.STREAM_CODEC.encode(buf, view.status);
        buf.writeVarInt(view.entries.size());
        for (var entry : view.entries) {
            ComponentSerialization.STREAM_CODEC.encode(buf, entry.label);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.stack);
            buf.writeVarInt(entry.available);
        }
    }, buf -> {
        UUID token = buf.readUUID();
        var owner = ComponentSerialization.STREAM_CODEC.decode(buf);
        int stolen = buf.readVarInt(); boolean open = buf.readBoolean(), closed = buf.readBoolean(), canTalk = buf.readBoolean();
        var status = ComponentSerialization.STREAM_CODEC.decode(buf);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_SLOTS || stolen < 0 || stolen > 5) throw new IllegalArgumentException("Invalid inventory snapshot");
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) {
            var label = ComponentSerialization.STREAM_CODEC.decode(buf);
            var stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int available = buf.readVarInt();
            if (available < 0 || available > 5) throw new IllegalArgumentException("Invalid theft allowance");
            entries.add(new Entry(label, stack, available));
        }
        return new PickpocketView(token, owner, stolen, List.copyOf(entries), status, open, closed, canTalk);
    });
    @Override public Type<PickpocketView> type() { return TYPE; }

    public record Take(UUID token, int slot) implements CustomPacketPayload {
        public static final Type<Take> TYPE = new Type<>(Napuli.id("pickpocket_take"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Take> CODEC = StreamCodec.of((buf, request) -> {
            buf.writeUUID(request.token); buf.writeVarInt(request.slot);
        }, buf -> new Take(buf.readUUID(), buf.readVarInt()));
        @Override public Type<Take> type() { return TYPE; }
    }
}
