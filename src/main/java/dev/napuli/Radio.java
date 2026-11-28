package dev.napuli;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The motorino radio, GTA style: the driver picks a station on the radio wheel (key R), the station is synced on the
 * scooter itself, and every client near the scooter plays that station from the scooter (the sound follows it and
 * dies with it). The songs and station names live in the songs resource pack (assets/napuli/radio/stations.json).
 */
public final class Radio {
	private Radio() {}

	public static final int OFF = -1, PARADE = 99, MAX_STATIONS = 16;
	public static final int TALK = MAX_STATIONS;

	/** client -> server: the driver tuned to a station (OFF to switch the radio off) */
	public record Tune(int station) implements CustomPacketPayload {
		public static final Type<Tune> TYPE = new Type<>(Napuli.id("radio_tune"));
		public static final StreamCodec<ByteBuf, Tune> CODEC = ByteBufCodecs.VAR_INT.map(Tune::new, Tune::station);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	static void register() {
		Songs.register();
		PayloadTypeRegistry.serverboundPlay().register(Tune.TYPE, Tune.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Tune.TYPE, (payload, ctx) -> {
			tune(ctx.player(), payload.station());
		});
	}

	/** All clients, including passengers, must pass this server-side driver check. */
	static boolean tune(net.minecraft.server.level.ServerPlayer player, int station) {
		if (station != OFF && (station < 0 || station > TALK)) return false;
		if (!(player.getVehicle() instanceof Motorino bike) || bike.getControllingPassenger() != player
				|| bike.inParade()) return false;
		if (station == TALK && bike.station() == TALK) {
			bike.nextTalkCall();
			return true;
		}
		bike.setStation(station);
		return true;
	}
}
