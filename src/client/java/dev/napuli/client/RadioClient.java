package dev.napuli.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.napuli.Motorino;
import dev.napuli.Napuli;
import dev.napuli.Radio;
import dev.napuli.client.music.RadioStations;
import dev.napuli.client.music.UserMusic;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Client side of the motorino radio (GTA V style):
 *  - every scooter with a station on plays that station FROM the scooter (sound bound to the entity: it moves with it,
 *    fades with distance, and stops when the scooter is gone) - also the parade scooter of the boys
 *  - the driver holds R for the radio wheel (release on a station to tune in), a short tap jumps to the next station
 *  - when the station or the song changes, its name shows at the top of the screen like in GTA
 */
public final class RadioClient {
	private RadioClient() {}


	static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.napuli.radio",
			InputConstants.KEY_R, KeyMapping.Category.register(Napuli.id("napulicraft"))));
	static final KeyMapping SONGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.napuli.canzoni",
			InputConstants.KEY_J, KeyMapping.Category.register(Napuli.id("canzoni"))));
	private static final float VOLUME = 2.5f;      // 2.5 x 16 blocks of hearing range
	private static final int RANGE = 56;

	private static final Map<Integer, Tuned> PLAYING = new HashMap<>();
	private static int heldTicks;
	private static boolean wasDown;
	private static int pendingSkip = -1, pendingBike, skipTicks;

	// the GTA-style banner
	private static String bannerName, bannerSong;
	private static int bannerColor, bannerTicks;

	private static final class Tuned {
		int station, song = -1, age;
		UserMusic.FileSound sound;
		boolean finished;
	}

	static void register() {
		UserMusic.register();
		ClientTickEvents.END_CLIENT_TICK.register(RadioClient::tick);
		HudElementRegistry.addLast(Napuli.id("radio_banner"), RadioClient::banner);
	}

	// ---------------------------------------------------------------- stations: the player's own songs
	static List<UserMusic.Station> stations() { return RadioStations.all(); }

	static void reload() { UserMusic.rescan(); }

	public static void suspendForScene() {
		var mc = Minecraft.getInstance();
		for (Tuned t : PLAYING.values()) stopTrack(mc, t);
		mc.getSoundManager().stop(null, SoundSource.MUSIC);
	}

	private static void stopTrack(Minecraft mc, Tuned t) {
		if (t.sound == null) return;
		t.sound.halt();
		mc.getSoundManager().stop(t.sound);
		t.sound = null;
	}

	// ---------------------------------------------------------------- every tick
	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			for (Tuned t : PLAYING.values()) stopTrack(mc, t);
			PLAYING.clear();
			return;
		}
		var sounds = mc.getSoundManager();
		Map<Integer, Motorino> near = new HashMap<>();
		for (Motorino bike : mc.level.getEntitiesOfClass(Motorino.class, mc.player.getBoundingBox().inflate(RANGE)))
			near.put(bike.getId(), bike);

		boolean scenePlaying = UserMusic.scenePlaying();
		Motorino parade = scenePlaying ? null : near.values().stream()
				.filter(bike -> bike.inParade() && bike.station() == Radio.PARADE && bike.distanceToSqr(mc.player) < 40 * 40)
				.filter(bike -> { Tuned t = PLAYING.get(bike.getId()); return t == null || t.station != Radio.PARADE || !t.finished; })
				.min(java.util.Comparator.comparingDouble(bike -> bike.distanceToSqr(mc.player))).orElse(null);
		if (scenePlaying || parade != null) sounds.stop(null, SoundSource.MUSIC);
		for (var entry : PLAYING.entrySet()) {
			Tuned track = entry.getValue();
            boolean paradeStopped = track.station == Radio.PARADE && (parade == null || entry.getKey() != parade.getId());
            if (paradeStopped && track.sound != null) track.finished = true;
            if (scenePlaying || paradeStopped || (parade != null && entry.getKey() != parade.getId()) || !near.containsKey(entry.getKey()))
				stopTrack(mc, entry.getValue());
		}
		for (Motorino bike : near.values()) {
			int st = bike.station();
			boolean audible = !scenePlaying && (parade == null ? st != Radio.PARADE : bike == parade);
			Tuned t = PLAYING.get(bike.getId());
			if (st == Radio.OFF) {
				if (t != null) { stopTrack(mc, t); PLAYING.remove(bike.getId()); }
				continue;
			}
			if (t == null || t.station != st) {
				if (t != null) stopTrack(mc, t);
				t = new Tuned();
				t.station = st;
				PLAYING.put(bike.getId(), t);
			}
			if (!audible || t.finished) continue;
			if (t.sound == null) {
				next(mc, bike, t, false);
				continue;
			}
			t.age++;
			if (!t.finished && t.age > 40 && t.sound != null && !sounds.isActive(t.sound)) {
				if (st == Radio.PARADE) { t.finished = true; continue; }  // the boys' song plays once
				next(mc, bike, t, true);                                   // the radio goes on: next song
			}
		}
		for (Iterator<Map.Entry<Integer, Tuned>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			if (!near.containsKey(e.getKey())) it.remove();
		}

		if (skipTicks > 0) {
			if (mc.player.getVehicle() instanceof Motorino bike && bike.getId() == pendingBike && !skip(pendingSkip)) skipTicks--;
			else skipTicks = 0;
		}

		// the radio wheel: hold R on the scooter (as the driver); a quick tap tunes to the next station
		boolean driving = mc.player.getVehicle() instanceof Motorino bike && bike.getFirstPassenger() == mc.player && !bike.inParade();
		boolean down = KEY.isDown();
		if (driving && mc.gui.screen() == null) {
			if (down) {
				if (++heldTicks == 5) mc.gui.setScreen(new RadioWheelScreen());
			} else if (wasDown && heldTicks > 0 && heldTicks < 5) {
				Motorino b = (Motorino) mc.player.getVehicle();
				int n = stations().size();
				int st = b.station();
				tune(st == Radio.OFF ? 0 : st + 1 >= n ? Radio.OFF : st + 1);
			}
		}
		if (!down) heldTicks = 0;
		wasDown = down;
		if (bannerTicks > 0) bannerTicks--;
		while (SONGS_KEY.consumeClick()) if (mc.gui.screen() == null) mc.gui.setScreen(new dev.napuli.client.music.SongsScreen(null));
	}

	private static void next(Minecraft mc, Motorino bike, Tuned t, boolean advance) {
		UserMusic.Song song;
		String stationName;
		int color, start = 0;
		if (t.station == Radio.PARADE) {
			// the boys' scooter plays the song picked for the fights ("lite"), from its chorus
			song = UserMusic.songFor("lite");
			stationName = "Corteo d'o motorino";
			color = 0xFFFF5252;
			if (song != null) start = UserMusic.startFor("lite", song);
		} else {
			List<UserMusic.Station> list = stations();
			if (list.isEmpty()) {
				t.finished = true;
				if (bike.getFirstPassenger() == mc.player) {
					bannerName = "Nisciuna canzone";
					bannerSong = "Miette 'e mp3 dint'a napulicraft/canzoni (tasto J)";
					bannerColor = 0xFFAAAAAA;
					bannerTicks = 120;
				}
				return;
			}
			UserMusic.Station s = list.get(Math.floorMod(t.station, list.size()));
			// like a live radio you tune in somewhere in the playlist, then it goes on in order
			if (t.song < 0) t.song = mc.level.getRandom().nextInt(s.songs().size());
			else t.song = Math.floorMod(t.song + (advance ? 1 : 0), s.songs().size());
			song = s.songs().get(t.song);
			stationName = s.name();
			color = s.color();
		}
		if (song == null) { t.finished = true; return; }
		t.sound = new UserMusic.FileSound(song, start, 0, bike, VOLUME);
		t.age = 0;
		t.finished = false;
		mc.getSoundManager().play(t.sound);
		if (bike.getFirstPassenger() == mc.player) {
			bannerName = stationName;
			bannerSong = song.title();
			bannerColor = color;
			bannerTicks = 110;
		}
	}

	/** for the play-test: what every nearby scooter is playing and where the sound is */
	static String debug() {
		Minecraft mc = Minecraft.getInstance();
		StringBuilder sb = new StringBuilder();
		for (var e : PLAYING.entrySet()) {
			Tuned t = e.getValue();
			Entity bike = mc.level.getEntity(e.getKey());
			sb.append(String.format(java.util.Locale.ROOT, "[bike %d station %d song %s active %s at %.1f,%.1f bike %s] ", e.getKey(), t.station,
					t.sound == null ? "-" : t.sound.song.title(), t.sound != null && mc.getSoundManager().isActive(t.sound),
					t.sound == null ? 0 : t.sound.getX(), t.sound == null ? 0 : t.sound.getZ(),
					bike == null ? "gone" : String.format(java.util.Locale.ROOT, "%.1f,%.1f", bike.getX(), bike.getZ())));
		}
		return sb.length() == 0 ? "nothing playing" : sb.toString();
	}

	static void requestSkip(int station) {
		var player = Minecraft.getInstance().player;
		if (player == null || !(player.getVehicle() instanceof Motorino bike) || bike.getFirstPassenger() != player
				|| bike.inParade() || station < 0 || station >= stations().size() || stations().get(station).songs().size() < 2) return;
		if (!skip(station)) {
			pendingSkip = station;
			pendingBike = bike.getId();
			skipTicks = 20;
		}
	}

	static int currentTrack() {
		var player = Minecraft.getInstance().player;
		if (player == null || !(player.getVehicle() instanceof Motorino bike)) return -1;
		var tuned = PLAYING.get(bike.getId());
		return tuned == null ? -1 : tuned.song;
	}

	private static boolean skip(int station) {
		Minecraft mc = Minecraft.getInstance();
		if (station < 0 || station >= stations().size() || mc.player == null
				|| !(mc.player.getVehicle() instanceof Motorino bike) || bike.getFirstPassenger() != mc.player
				|| bike.inParade() || bike.station() != station || stations().get(station).songs().size() < 2) return false;
		Tuned t = PLAYING.get(bike.getId());
		if (t == null || t.station != station || t.song < 0) return false;
		if (t.sound == null) {
			t.song = (t.song + 1) % stations().get(station).songs().size();
		} else {
			stopTrack(mc, t);
			next(mc, bike, t, true);
		}
		return true;
	}

	static void tune(int station) {
		skipTicks = 0;
		ClientPlayNetworking.send(new Radio.Tune(station));
		if (station == Radio.OFF) { bannerName = "Radio spenta"; bannerSong = ""; bannerColor = 0xFFAAAAAA; bannerTicks = 50; }
	}

	static int currentStation() {
		var p = Minecraft.getInstance().player;
		return p != null && p.getVehicle() instanceof Motorino bike ? bike.station() : Radio.OFF;
	}

	// ---------------------------------------------------------------- the banner, top centre, like GTA
	private static void banner(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (bannerTicks <= 0 || bannerName == null) return;
		Minecraft mc = Minecraft.getInstance();
		int alpha = Math.min(255, bannerTicks * 12);
		if (alpha < 8) return;
		int w = g.guiWidth();
		var font = mc.font;
		g.pose().pushMatrix();
		g.pose().translate(w / 2f, 12);
		g.pose().scale(1.7f, 1.7f);
		String name = bannerName;
		g.text(font, name, -font.width(name) / 2, 0, (alpha << 24) | (bannerColor & 0xFFFFFF), true);
		g.pose().popMatrix();
		if (bannerSong != null && !bannerSong.isEmpty())
			g.text(font, bannerSong, w / 2 - font.width(bannerSong) / 2, 31, (alpha << 24) | 0xFFFFFF, true);
	}
}
