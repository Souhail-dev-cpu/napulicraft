package dev.napuli.client.music;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Download a song from a DIRECT link to an audio file (.mp3 / .ogg / .wav) into napulicraft/canzoni.
 * Streaming sites (YouTube, Spotify...) are not supported: use music you have the right to use, e.g. free music from
 * Free Music Archive, Jamendo, Internet Archive, Pixabay Music, or your own files.
 */
public final class DownloadScreen extends Screen {
	private static final long MAX_BYTES = 60L * 1024 * 1024;
	private static final List<String> STREAMING = List.of("youtube.", "youtu.be", "spotify.", "soundcloud.", "deezer.", "music.apple.",
			"tidal.", "amazon.", "tiktok.");
	private final Screen parent;
	private EditBox url;
	private Button go;
	private volatile String status = "";
	private volatile int statusColor = 0xFFBBBBBB;
	private volatile boolean busy;

	DownloadScreen(Screen parent) {
		super(Component.literal("Scarica canzone"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = Math.min(380, width - 40);
		url = new EditBox(font, width / 2 - w / 2, height / 2 - 20, w, 20, Component.literal("Link"));
		url.setMaxLength(2000);
		url.setHint(Component.literal("https://.../canzone.mp3"));
		addRenderableWidget(url);
		setInitialFocus(url);
		go = addRenderableWidget(Button.builder(Component.literal("⬇ Scarica"), b -> start()).bounds(width / 2 - 104, height / 2 + 10, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Indietro"), b -> onClose()).bounds(width / 2 + 4, height / 2 + 10, 100, 20).build());
	}

	private void say(String s, int color) { status = s; statusColor = color; }

	private void start() {
		if (busy) return;
		String link = url.getValue().trim();
		URI uri;
		try {
			uri = URI.create(link);
			if (uri.getScheme() == null || !(uri.getScheme().equals("https") || uri.getScheme().equals("http")) || uri.getHost() == null) throw new IllegalArgumentException();
		} catch (Exception e) {
			say("Link nun valido: ce vo' https://...", 0xFFFF8A80);
			return;
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		for (String s : STREAMING) if (host.contains(s)) {
			say("Chisto è nu sito 'e streaming: serve nu link DIRETTO a nu file .mp3/.ogg/.wav", 0xFFFF8A80);
			return;
		}
		busy = true;
		say("Sto scaricanno...", 0xFFFFD54F);
		Thread t = new Thread(() -> download(uri), "Napulicraft song download");
		t.setDaemon(true);
		t.start();
	}

	private void download(URI uri) {
		File tmp = null;
		try {
			HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
			HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).header("User-Agent", "Napulicraft-Minecraft-mod").GET().build();
			HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
			if (res.statusCode() / 100 != 2) throw new Exception("il server ha risposto " + res.statusCode());
			String type = res.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
			String name = fileName(res.uri(), res.headers().firstValue("Content-Disposition").orElse(""));
			String ext = extension(name, type);
			if (ext == null) throw new Exception("nun è nu file audio (.mp3/.ogg/.wav)");
			if (!name.toLowerCase(Locale.ROOT).endsWith(ext)) name = name + ext;
			long len = res.headers().firstValueAsLong("Content-Length").orElse(-1);
			if (len > MAX_BYTES) throw new Exception("file troppo gruosso (max 60 MB)");
			File dir = UserMusic.folder();
			tmp = File.createTempFile("napuli-dl", ext, dir);
			long total = 0;
			try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp.toPath())) {
				byte[] buf = new byte[1 << 16];
				int n;
				while ((n = in.read(buf)) > 0) {
					total += n;
					if (total > MAX_BYTES) throw new Exception("file troppo gruosso (max 60 MB)");
					out.write(buf, 0, n);
				}
			}
			File target = new File(dir, name);
			for (int i = 2; target.exists(); i++) target = new File(dir, name.replaceAll("(\\.[a-z0-9]+)$", " (" + i + ")$1"));
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
			tmp = null;
			// make sure it really plays
			try (var check = UserAudio.open(target, 0, 1)) {
				if (!check.read(4096).hasRemaining()) throw new Exception("empty");
			} catch (Exception bad) {
				Files.deleteIfExists(target.toPath());
				throw new Exception("'o file nun se sente: formato nun supportato");
			}
			UserMusic.rescan();
			say("✔ Scaricata: " + target.getName() + " (" + total / 1024 + " KB)", 0xFFA5D6A7);
		} catch (Exception e) {
			say("Nun se pò scarricà: " + e.getMessage(), 0xFFFF8A80);
		} finally {
			if (tmp != null) tmp.delete();
			busy = false;
		}
	}

	static String fileName(URI uri, String disposition) {
		String name = null;
		var m = java.util.regex.Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(disposition);
		if (m.find()) name = java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8);
		if (name == null || name.isBlank()) {
			String path = uri.getPath() == null ? "" : uri.getPath();
			name = java.net.URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), java.nio.charset.StandardCharsets.UTF_8);
		}
		name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
		if (name.isEmpty() || name.startsWith(".")) name = "canzone";
		return name.length() > 120 ? name.substring(0, 120) : name;
	}

	static String extension(String name, String type) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".wav")) return n.substring(n.length() - 4);
		if (type.contains("audio/mpeg") || type.contains("audio/mp3")) return ".mp3";
		if (type.contains("ogg") || type.contains("vorbis")) return ".ogg";
		if (type.contains("wav")) return ".wav";
		return null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		super.extractRenderState(g, mx, my, a);
		go.active = !busy;
		g.centeredText(font, "⬇ Scarica na canzone da nu link diretto", width / 2, height / 2 - 62, 0xFFFFD54F);
		g.centeredText(font, "Link a nu file .mp3 / .ogg / .wav (no YouTube/Spotify).", width / 2, height / 2 - 48, 0xFF9E9E9E);
		g.centeredText(font, "Usa musica ca puo' usà: Free Music Archive, Jamendo, Internet Archive, Pixabay...", width / 2, height / 2 - 37, 0xFF9E9E9E);
		g.centeredText(font, status, width / 2, height / 2 + 40, statusColor);
	}

	@Override
	public void onClose() {
		UserMusic.rescan();
		minecraft.gui.setScreen(parent instanceof SongsScreen ? new SongsScreen(null) : parent);
	}
}
