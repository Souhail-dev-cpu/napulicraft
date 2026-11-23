package dev.napuli.client.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.napuli.Music;
import dev.napuli.Napuli;
import dev.napuli.Songs;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;

/**
 * Everyone's own music. No songs ship with the mod: people drop .mp3 / .ogg / .wav files into
 * {@code <minecraft>/napulicraft/canzoni/} (sub-folders become radio stations) and pick, in the "Canzoni" screen
 * (key J), which song plays for each situation and from which second. Saved in config/napulicraft-canzoni.json.
 */
public final class UserMusic {
	private UserMusic() {}

	public static final String NS = "napuli_user";
	/** the situations a song can be picked for: the server's moments plus the giant's scream */
	public static final List<String> SITUATIONS = new ArrayList<>();
	static {
		for (Music.Situation s : Music.Situation.values()) SITUATIONS.add(s.id());
		SITUATIONS.add("urlo");
	}

	public record Song(String file, String title, File path) {}
	public record Choice(String file, int start) {}
	public record Station(String name, String dj, int color, List<Song> songs) {}

	private static List<Song> library;
	private static final Map<String, Choice> CHOICES = new LinkedHashMap<>();
	private static final List<JsonObject> CONFIG_STATIONS = new ArrayList<>();
	private static boolean configLoaded;

	// ---------------------------------------------------------------- files
	public static File folder() {
		File f = new File(Minecraft.getInstance().gameDirectory, "napulicraft/canzoni");
		if (!f.isDirectory()) f.mkdirs();
		return f;
	}

	private static File configFile() { return new File(Minecraft.getInstance().gameDirectory, "config/napulicraft-canzoni.json"); }

	/** every playable file, root first, then the station folders */
	public static List<Song> library() {
		if (library == null) {
			List<Song> out = new ArrayList<>();
			File root = folder();
			File[] files = root.listFiles();
			if (files != null) {
				Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
				for (File f : files) if (f.isFile() && UserAudio.supported(f.getName())) out.add(new Song(f.getName(), title(f.getName()), f));
				for (File dir : files) {
					if (!dir.isDirectory()) continue;
					File[] inner = dir.listFiles();
					if (inner == null) continue;
					Arrays.sort(inner, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
					for (File f : inner) if (f.isFile() && UserAudio.supported(f.getName()))
						out.add(new Song(dir.getName() + "/" + f.getName(), title(f.getName()), f));
				}
			}
			library = out;
		}
		return library;
	}

	public static void rescan() { library = null; RadioStations.reset(); }

	/** "Gianni X - Song (Video Ufficiale).mp3" -> "Gianni X - Song" */
	public static String title(String fileName) {
		String t = fileName.replaceAll("\\.[A-Za-z0-9]+$", "");
		t = t.replaceAll("(?i)\\s*[\\(\\[][^\\)\\]]*(video|official|ufficiale|lyrics|audio|testo|hd)[^\\)\\]]*[\\)\\]]", "");
		t = t.replaceAll("(?i)\\s*-\\s*[a-z]*music[a-z]* official", "").replace('_', ' ').trim();
		return t.isEmpty() ? fileName : t;
	}

	public static Song find(String file) {
		for (Song s : library()) if (s.file().equals(file)) return s;
		return null;
	}

	// ---------------------------------------------------------------- config
	private static void loadConfig() {
		if (configLoaded) return;
		configLoaded = true;
		CHOICES.clear();
		CONFIG_STATIONS.clear();
		File f = configFile();
		if (!f.isFile()) return;
		try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			if (root.has("situazioni")) for (var e : root.getAsJsonObject("situazioni").entrySet()) {
				JsonObject o = e.getValue().getAsJsonObject();
				CHOICES.put(e.getKey(), new Choice(o.has("canzone") ? o.get("canzone").getAsString() : "", o.has("da") ? o.get("da").getAsInt() : 0));
			}
			if (root.has("stazioni")) for (JsonElement e : root.getAsJsonArray("stazioni")) CONFIG_STATIONS.add(e.getAsJsonObject());
		} catch (Exception e) {
			Napuli.LOGGER.warn("Can't read {}", f, e);
		}
	}

	public static void saveConfig() {
		JsonObject root = new JsonObject();
		JsonObject sit = new JsonObject();
		for (var e : CHOICES.entrySet()) {
			JsonObject o = new JsonObject();
			o.addProperty("canzone", e.getValue().file());
			o.addProperty("da", e.getValue().start());
			sit.add(e.getKey(), o);
		}
		root.add("situazioni", sit);
		JsonArray st = new JsonArray();
		CONFIG_STATIONS.forEach(st::add);
		root.add("stazioni", st);
		File f = configFile();
		f.getParentFile().mkdirs();
		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
			gson.toJson(root, w);
		} catch (IOException e) {
			Napuli.LOGGER.warn("Can't save {}", f, e);
		}
	}

	/** the song picked for a situation (an empty file name = a random song every time) */
	public static Choice choice(String situation) {
		loadConfig();
		return CHOICES.getOrDefault(situation, new Choice("", defaultStart(situation)));
	}

	public static void choose(String situation, Choice c) {
		loadConfig();
		CHOICES.put(situation, c);
		saveConfig();
	}

	static int defaultStart(String situation) { return situation.equals("radio") ? 0 : 45; } // ~ where choruses start

	static List<JsonObject> configStations() { loadConfig(); return CONFIG_STATIONS; }

	// ---------------------------------------------------------------- playback
	private static final Map<String, Track> TOKENS = new ConcurrentHashMap<>();
	private static final AtomicInteger NEXT = new AtomicInteger();
	private record Track(File file, double start, double max) {}
	private static SoundInstance scene;

	/** called by the sound library mixin: the file behind "napuli_user:sounds/tN.ogg" */
	public static net.minecraft.client.sounds.AudioStream open(Identifier location) throws IOException {
		String p = location.getPath();
		String token = p.substring(p.lastIndexOf('/') + 1).replace(".ogg", "");
		Track t = TOKENS.remove(token);
		if (t == null) throw new IOException("Unknown song token " + location);
		return UserAudio.open(t.file(), t.start(), t.max());
	}

	static Identifier register(File file, double start, double max) {
		String token = "t" + NEXT.incrementAndGet();
		TOKENS.put(token, new Track(file, start, max));
		return Identifier.fromNamespaceAndPath(NS, token);
	}

	/** a song played straight from a file; follows an entity if given, else plays "in your ears" */
	public static class FileSound extends AbstractSoundInstance implements TickableSoundInstance {
		private final Entity follow;
		private final int lengthTicks;
		private final float baseVolume;
		private int age;
		private boolean stopped;
		public final Song song;

		public FileSound(Song song, double start, double maxSeconds, Entity follow, float volume) {
			super(register(song.path(), start, maxSeconds), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
			this.song = song;
			this.follow = follow;
			this.baseVolume = volume;
			this.volume = volume;
			this.lengthTicks = maxSeconds > 0 ? (int) (maxSeconds * 20) : 0;
			if (follow == null) {
				this.relative = true;
				this.attenuation = SoundInstance.Attenuation.NONE;
				x = y = z = 0;
			} else {
				this.attenuation = SoundInstance.Attenuation.LINEAR;
				move();
			}
		}

		private void move() { x = follow.getX(); y = follow.getY() + 0.8; z = follow.getZ(); }
		public void halt() { stopped = true; }
		@Override public boolean isStopped() { return stopped; }

		@Override
		public void tick() {
			age++;
			if (follow != null) { if (follow.isRemoved()) stopped = true; else move(); }
			// situations end with a 2-second fade (the stream itself is cut a little later)
			if (lengthTicks > 0 && age > lengthTicks - 40) volume = baseVolume * Math.max(0, (lengthTicks - age) / 40f);
			if (lengthTicks > 0 && age > lengthTicks + 10) stopped = true;
		}

		@Override
		public WeighedSoundEvents getOrResolve(SoundManager manager) {
			this.sound = new Sound(identifier, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
			this.soundEvent = new WeighedSoundEvents(identifier, null);
			return this.soundEvent;
		}
	}

	/** pick the song for a situation: the chosen one, else a random one from the library */
	public static Song songFor(String situation) {
		Choice c = choice(situation);
		Song s = c.file().isEmpty() ? null : find(c.file());
		if (s == null) {
			List<Song> all = library();
			if (all.isEmpty()) return null;
			s = all.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(all.size()));
		}
		return s;
	}

	public static int startFor(String situation, Song s) {
		if (situation.equals("urlo") && choice("urlo").file().isEmpty()) situation = "lite";
		Choice c = choice(situation);
		return s.file().equals(c.file()) || c.file().isEmpty() ? c.start() : defaultStart(situation);
	}

	public static void playSituation(String situation, int seconds) {
		boolean urlo = situation.equals("urlo");
		Song s = songFor(urlo && choice("urlo").file().isEmpty() ? "lite" : situation);
		if (s != null) {
			startScene(new FileSound(s, startFor(situation, s), seconds, null, urlo ? 1.0f : 0.9f));
		} else if (urlo) {
			startScene(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.WARDEN_ROAR, 0.85f));
		}
	}

	public static String sceneDebug() {
		return scene instanceof FileSound s ? "[" + s.song.title() + " active " + scenePlaying() + "]" : "no scene song";
	}

	public static void stopScene() {
		if (scene == null) return;
		if (scene instanceof FileSound file) file.halt();
		Minecraft.getInstance().getSoundManager().stop(scene);
		scene = null;
	}

	public static boolean scenePlaying() {
		return scene != null && (!(scene instanceof FileSound file) || !file.isStopped())
				&& Minecraft.getInstance().getSoundManager().isActive(scene);
	}

	private static void startScene(SoundInstance next) {
		stopScene();
		dev.napuli.client.RadioClient.suspendForScene();
		scene = next;
		Minecraft.getInstance().getSoundManager().play(scene);
	}

	/** preview in the songs screen */
	public static FileSound preview(Song s, int start) {
		FileSound snd = new FileSound(s, start, 12, null, 1.0f);
		startScene(snd);
		return snd;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Songs.Play.TYPE, (p, ctx) -> playSituation(p.situation(), p.seconds()));
		ClientPlayNetworking.registerGlobalReceiver(Songs.Stop.TYPE, (p, ctx) -> stopScene());
	}

	/** nice name of a situation for the screen */
	public static String label(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "morte" -> "Quando muori";
			case "gigante" -> "Arriva 'o Munaciello gigante";
			case "lite" -> "Litigate (e 'o corteo d'o motorino)";
			case "ospedale" -> "Poca vita";
			case "furto" -> "'O Munaciello t'arrobba";
			case "vittoria" -> "Vittoria";
			case "diamanti" -> "Diamanti";
			case "nether" -> "Nether";
			case "amore" -> "Ammore";
			case "risveglio" -> "Buongiorno";
			case "notte" -> "Notte";
			case "radio" -> "Radio pe' strada";
			case "urlo" -> "Urlo d'o gigante (5 s)";
			default -> id;
		};
	}
}
