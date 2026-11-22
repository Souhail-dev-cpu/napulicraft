package dev.napuli;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The "music director": every situation has its song (folders/mapping in the songs pack).
 * One song at a time per player; a more important situation interrupts a less important one,
 * the same situation doesn't repeat for a few minutes.
 */
public final class Music {
	private Music() {}

	public enum Situation {
		// priority, cooldown seconds, how long the excerpt lasts (seconds)
		MORTE(100, 120, 50), GIGANTE(90, 60, 50), LITE(80, 180, 50), OSPEDALE(70, 300, 50), FURTO(60, 120, 50),
		VITTORIA(55, 60, 50), DIAMANTI(50, 240, 50), NETHER(45, 300, 50), AMORE(40, 240, 50),
		RISVEGLIO(35, 600, 50), NOTTE(30, 600, 50), RADIO(10, 0, 80);

		final int priority, cooldown, length;
		public SoundEvent sound;
		Situation(int priority, int cooldown, int length) { this.priority = priority; this.cooldown = cooldown; this.length = length; }
		public String id() { return name().toLowerCase(); }
	}

	private record Playing(Situation what, long until) {}
	private static final Map<UUID, Playing> NOW = new HashMap<>();
	private static final Map<String, Long> LAST = new HashMap<>();

	/** @return true if it started */
	public static boolean play(ServerPlayer p, Situation s) {
		long now = p.level().getGameTime();
		Playing cur = NOW.get(p.getUUID());
		boolean busy = cur != null && now < cur.until();
		if (busy && cur.what().priority >= s.priority && s != Situation.RADIO) return false;
		// on the scooter with the radio on, the radio is the soundtrack: only the big moments cut in
		if (p.getVehicle() instanceof Motorino bike && bike.radioOn() && s.priority < 90) return false;
		if (busy && s == Situation.RADIO && cur.what() != Situation.RADIO) return false;
		Long last = LAST.get(p.getUUID() + s.id());
		if (last != null && now - last < s.cooldown * 20L) return false;
		playScene(p, s);
		return true;
	}

	/** Authored scenes begin their own cue even when the ambient radio is already playing. */
	static void playScene(ServerPlayer p, Situation s) {
		long now = p.level().getGameTime();
		stop(p);
		// the client picks the actual song (each player's own music, see napulicraft/canzoni)
		Songs.play(p, s.id(), s.length);
		NOW.put(p.getUUID(), new Playing(s, now + s.length * 20L));
		LAST.put(p.getUUID() + s.id(), now);
		Napuli.LOGGER.info("[music] {} -> {}", p.getName().getString(), s.id());
	}

	public static void stop(ServerPlayer p) {
		Playing cur = NOW.remove(p.getUUID());
		if (cur != null) Songs.stop(p);
	}

	public static Situation current(ServerPlayer p) {
		Playing cur = NOW.get(p.getUUID());
		return cur != null && p.level().getGameTime() < cur.until() ? cur.what() : null;
	}
}
