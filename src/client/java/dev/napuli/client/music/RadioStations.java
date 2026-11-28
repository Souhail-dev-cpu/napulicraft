package dev.napuli.client.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.napuli.Radio;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The radio stations of the scooter, built from the player's own songs:
 *  1. "Radio Napulicraft": every song in napulicraft/canzoni
 *  2. the stations listed in config/napulicraft-canzoni.json ("stazioni": name, dj, colour, songs)
 *  3. one station per sub-folder of napulicraft/canzoni (the folder name is the station name)
 */
public final class RadioStations {
	private RadioStations() {}
	public static final String TALK_NAME = "Radio Vicoli";
	public static final int TALK_COLOR = 0xFFE1694F;

	private static final int[] COLORS = {0x29B6F6, 0xF06292, 0xEF5350, 0xFFB300, 0x66BB6A, 0xAB47BC, 0x26A69A, 0xFF7043};
	private static List<UserMusic.Station> stations;

	static void reset() { stations = null; }

	public static List<UserMusic.Station> all() {
		if (stations == null) stations = build();
		return stations;
	}

	public static List<UserMusic.Station> music() {
		var all = all();
		return all.subList(0, all.size() - 1);
	}

	public static int channelAt(int index) {
		return index == all().size() - 1 ? Radio.TALK : index;
	}

	public static int indexOf(int channel) {
		if (channel == Radio.TALK) return all().size() - 1;
		return channel >= 0 && channel < music().size() ? channel : Radio.OFF;
	}

	private static List<UserMusic.Station> build() {
		List<UserMusic.Station> out = new ArrayList<>();
		List<UserMusic.Song> lib = UserMusic.library();
		if (!lib.isEmpty()) out.add(new UserMusic.Station("Radio Napulicraft", "Tutte 'e canzone toje", 0xFF000000 | COLORS[0], lib));
		for (JsonObject o : UserMusic.configStations()) {
			List<UserMusic.Song> songs = new ArrayList<>();
			if (o.has("canzoni")) for (JsonElement e : o.getAsJsonArray("canzoni")) {
				UserMusic.Song s = UserMusic.find(e.getAsString());
				if (s != null) songs.add(s);
			}
			if (songs.isEmpty() || !o.has("nome")) continue;
			int color = COLORS[out.size() % COLORS.length];
			if (o.has("colore")) try { color = Integer.parseInt(o.get("colore").getAsString().replace("#", ""), 16); } catch (NumberFormatException ignored) {}
			out.add(new UserMusic.Station(o.get("nome").getAsString(), o.has("dj") ? o.get("dj").getAsString() : "", 0xFF000000 | color, songs));
		}
		Map<String, List<UserMusic.Song>> folders = new LinkedHashMap<>();
		for (UserMusic.Song s : lib) {
			int slash = s.file().indexOf('/');
			if (slash > 0) folders.computeIfAbsent(s.file().substring(0, slash), k -> new ArrayList<>()).add(s);
		}
		for (var e : folders.entrySet())
			out.add(new UserMusic.Station(e.getKey(), "La stazione toja", 0xFF000000 | COLORS[out.size() % COLORS.length], e.getValue()));
		if (out.size() > Radio.MAX_STATIONS) out.subList(Radio.MAX_STATIONS, out.size()).clear();
		out.add(new UserMusic.Station(TALK_NAME, "", TALK_COLOR, List.of()));
		return List.copyOf(out);
	}
}
