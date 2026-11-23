package dev.napuli;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends each player what the right-side panel and the mission tracker show: money, reputation with both clans, the
 * clan you belong to, the current objective and where it is, carabinieri (stars, seconds) and jail time.
 */
public final class Hud {
	private Hud() {}

	public static final int NO_TARGET = Integer.MIN_VALUE;

	public record State(boolean inCity, int cash, int lanterns, int tides, int ally, String objective, int tx, int tz,
			int stars, int wantedLeft, int jail, int cooldown, int pizzas, int fullness, int heat, int hygiene) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(Napuli.id("hud_v2"));
		public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of((buf, s) -> {
			buf.writeBoolean(s.inCity);
			buf.writeVarInt(s.cash); buf.writeVarInt(s.lanterns); buf.writeVarInt(s.tides); buf.writeVarInt(s.ally);
			buf.writeUtf(s.objective); buf.writeInt(s.tx); buf.writeInt(s.tz);
			buf.writeVarInt(s.stars); buf.writeVarInt(s.wantedLeft); buf.writeVarInt(s.jail); buf.writeVarInt(s.cooldown);
			buf.writeVarInt(s.pizzas); buf.writeVarInt(s.fullness); buf.writeVarInt(s.heat); buf.writeVarInt(s.hygiene);
		}, buf -> new State(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readUtf(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Hud::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(p, State.TYPE)) continue;
			var obj = NapoliFactions.objective(p);
			if (NapoliEmployment.trackingOffice(p)) obj = NapoliEmployment.office(p);
			else if (obj.key().equals("none") || obj.key().equals("cooldown")) {
				var local = NapoliLocalJobs.objective(p);
				if (local != null) obj = local;
				else {
					var work = NapoliEmployment.objective(p);
					if (work != null) obj = work;
					else {
						var office = NapoliEmployment.office(p);
						if (office != null) obj = office;
					}
				}
			}
			NapoliFactions.Side ally = NapoliFactions.ally(p);
			ServerPlayNetworking.send(p, new State(p.level().dimension() == NapoliMap.LEVEL, NapoliStreet.cash(p),
					NapoliFactions.reputation(p, NapoliFactions.Side.LANTERNE), NapoliFactions.reputation(p, NapoliFactions.Side.MAREE),
					ally == null ? -1 : ally.ordinal(), obj.key(),
					obj.target() == null ? NO_TARGET : obj.target().getX(), obj.target() == null ? NO_TARGET : obj.target().getZ(),
					Carabinieri.wantedStars(p), Carabinieri.wantedSecondsLeft(p), Carabinieri.jailSecondsLeft(p), NapoliFactions.cooldownSeconds(p),
					KitchenProgress.pizzas(p), NapoliStreet.fullness(p), KitchenEffects.heat(p), NapoliHygiene.value(p)));
		}
	}
}
