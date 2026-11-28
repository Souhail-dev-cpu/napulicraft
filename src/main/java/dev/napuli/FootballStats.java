package dev.napuli;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FootballStats(String home, String away, int homeGoals, int awayGoals, Line profile, List<Line> lineup)
        implements CustomPacketPayload {
    public record Ratings(int pace, int shooting, int defense, int strength, int stamina) {
        public static final int UNRATED = -1;
        public int overall() {
            return shooting == UNRATED ? UNRATED : (pace + shooting + defense + strength + stamina + 2) / 5;
        }
        public int[] values() { return new int[]{pace, shooting, defense, strength, stamina}; }
    }

    public record Line(String name, String role, String club, int side, boolean npc, int matches,
                       Ratings ratings, int goals, int shots, int passes, int recoveries) {
        private static final StreamCodec<ByteBuf, Line> CODEC = StreamCodec.of(
                (buf, row) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.name);
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.role);
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.club);
                    buf.writeByte(row.side);
                    buf.writeBoolean(row.npc);
                    writeInts(buf, row.matches);
                    for (int rating : row.ratings.values()) buf.writeByte(rating);
                    writeInts(buf, row.goals, row.shots, row.passes, row.recoveries);
                },
                buf -> new Line(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf), buf.readByte(), buf.readBoolean(), readInt(buf),
                        new Ratings(buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte()),
                        readInt(buf), readInt(buf), readInt(buf), readInt(buf)));
    }

    public static final Type<FootballStats> TYPE = new Type<>(Napuli.id("football_stats"));
    private static final StreamCodec<ByteBuf, List<Line>> LINEUP = Line.CODEC.apply(ByteBufCodecs.list(10));
    static final StreamCodec<ByteBuf, FootballStats> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FootballStats::home,
            ByteBufCodecs.STRING_UTF8, FootballStats::away,
            ByteBufCodecs.VAR_INT, FootballStats::homeGoals,
            ByteBufCodecs.VAR_INT, FootballStats::awayGoals,
            Line.CODEC, FootballStats::profile, LINEUP, FootballStats::lineup, FootballStats::new);

    private static void writeInts(ByteBuf buf, int... values) {
        for (int value : values) ByteBufCodecs.VAR_INT.encode(buf, value);
    }
    private static int readInt(ByteBuf buf) { return ByteBufCodecs.VAR_INT.decode(buf); }
    public boolean live() { return !lineup.isEmpty(); }
    @Override public Type<FootballStats> type() { return TYPE; }
}
