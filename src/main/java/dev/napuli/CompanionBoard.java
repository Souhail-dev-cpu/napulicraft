package dev.napuli;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Read-only menu snapshot. Actions still go through the normal server command checks. */
public record CompanionBoard(String page, Component title, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 512;
    public record Entry(Component title, Component detail, String command, String extra, String extraCommand) {
        public Entry(Component title, Component detail, String command) { this(title, detail, command, "", ""); }
        public Entry(String title, String detail, String command) {
            this(Component.literal(title), Component.literal(detail), command, "", "");
        }
    }
    public static final Type<CompanionBoard> TYPE = new Type<>(Napuli.id("companion_board"));
    static final StreamCodec<RegistryFriendlyByteBuf, CompanionBoard> CODEC = StreamCodec.of((buf, board) -> {
        if(board.entries().size()>MAX_ENTRIES)throw new IllegalArgumentException("Invalid companion board size");
        buf.writeUtf(board.page(), 64);
        ComponentSerialization.STREAM_CODEC.encode(buf, board.title());
        buf.writeVarInt(board.entries().size());
        for (var entry : board.entries()) {
            ComponentSerialization.STREAM_CODEC.encode(buf, entry.title());
            ComponentSerialization.STREAM_CODEC.encode(buf, entry.detail());
            buf.writeUtf(entry.command(), 256);
            buf.writeUtf(entry.extra(), 64);
            buf.writeUtf(entry.extraCommand(), 256);
        }
    }, buf -> {
        String page = buf.readUtf(64);
        var title = ComponentSerialization.STREAM_CODEC.decode(buf);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid companion board size");
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(
                ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                buf.readUtf(256), buf.readUtf(64), buf.readUtf(256)));
        return new CompanionBoard(page, title, List.copyOf(entries));
    });
    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }
    public static boolean matches(Entry entry, String query) {
        String haystack = normalize(entry.title().getString() + " " + entry.detail().getString());
        for (String word : normalize(query).split("\\s+")) if (!haystack.contains(word)) return false;
        return true;
    }
    @Override public Type<CompanionBoard> type() { return TYPE; }
}
