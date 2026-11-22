package dev.napuli;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The server only says WHICH moment it is ("lite", "morte", "urlo"...). Each player's own client decides which song
 * plays for it, from the songs THEY put in napulicraft/canzoni (no songs ship with the mod).
 */
public final class Songs {
	private Songs() {}

	/** play the song chosen for a situation, for N seconds (0 = the whole song) */
	public record Play(String situation, int seconds) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(Napuli.id("song_play"));
		public static final StreamCodec<ByteBuf, Play> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Play::situation, ByteBufCodecs.VAR_INT, Play::seconds, Play::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record Stop() implements CustomPacketPayload {
		public static final Type<Stop> TYPE = new Type<>(Napuli.id("song_stop"));
		public static final StreamCodec<ByteBuf, Stop> CODEC = StreamCodec.unit(new Stop());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Stop.TYPE, Stop.CODEC);
	}

	static void play(ServerPlayer p, String situation, int seconds) {
		if (ServerPlayNetworking.canSend(p, Play.TYPE)) ServerPlayNetworking.send(p, new Play(situation, seconds));
	}

	static void stop(ServerPlayer p) {
		if (ServerPlayNetworking.canSend(p, Stop.TYPE)) ServerPlayNetworking.send(p, new Stop());
	}

	/** the giant's scream: 5 seconds of the clip each player picked, for everyone near */
	static void urlo(ServerLevel level, Vec3 at) {
		for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(at) < 64 * 64)) play(p, "urlo", 5);
	}
}
