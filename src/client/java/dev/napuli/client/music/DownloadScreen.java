package dev.napuli.client.music;

import dev.napuli.SongDownloadFiles;
import static dev.napuli.CityText.t;
import dev.napuli.client.NapoliScreen;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Download a song from a DIRECT link to an audio file (.mp3 / .ogg / .wav) into napulicraft/canzoni.
 * Streaming sites (YouTube, Spotify...) are not supported: use music you have the right to use, e.g. free music from
 * Free Music Archive, Jamendo, Internet Archive, Pixabay Music, or your own files.
 */
public final class DownloadScreen extends NapoliScreen {
	private static final long MAX_BYTES = 60L * 1024 * 1024;
	private static final List<String> STREAMING = List.of("youtube.", "youtu.be", "spotify.", "soundcloud.", "deezer.", "music.apple.",
			"tidal.", "amazon.", "tiktok.");
	private final Screen parent;
	private EditBox url;
	private Button go;
	private volatile Component status = Component.empty();
	private volatile int statusColor = 0xFFBBBBBB;
	private volatile boolean busy;

	DownloadScreen(Screen parent) {
		super(t("music.download_title"));
		this.parent = parent;
	}
	@Override public boolean isPauseScreen() { return true; }

	@Override
	protected void init() {
		String entered = url == null ? "" : url.getValue();
		layout(460, 236);
		int w = panelWidth - 24;
		url = new EditBox(font, left + 12, top + 96, w, 20, t("music.link"));
		url.setMaxLength(2000);
		url.setValue(entered);
		url.setHint(Component.literal("https://.../canzone.mp3"));
		addRenderableWidget(url);
		setInitialFocus(url);
		go = button(left + 12, bottom - 26, (w - 6) / 2, t("music.download"), this::start);
		button(left + 18 + (w - 6) / 2, bottom - 26, (w - 6) / 2, t("screen.back"), this::onClose);
		url.setResponder(value -> updateControls());
		updateControls();
	}

	@Override protected Component subtitle() { return t("music.download_subtitle"); }
	private void say(Component message, int color) { status = message; statusColor = color; }
	private void updateControls() { go.active = !busy && !url.getValue().isBlank(); url.setEditable(!busy); }
	@Override public void tick() { super.tick(); updateControls(); }

	private void start() {
		if (busy) return;
		String link = url.getValue().trim();
		URI uri;
		try {
			uri = URI.create(link);
			if (uri.getScheme() == null || !(uri.getScheme().equals("https") || uri.getScheme().equals("http")) || uri.getHost() == null) throw new IllegalArgumentException();
		} catch (Exception e) {
			say(t("music.invalid_link"), ERROR);
			return;
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		for (String s : STREAMING) if (host.contains(s)) {
			say(t("music.streaming_link"), ERROR);
			return;
		}
		busy = true;
		updateControls();
		say(t("music.downloading"), GOLD);
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
			if (res.statusCode() / 100 != 2) throw new Exception(t("music.server_status", res.statusCode()).getString());
			String type = res.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
			String name = fileName(res.uri(), res.headers().firstValue("Content-Disposition").orElse(""));
			String ext = extension(name, type);
			if (ext == null) throw new Exception(t("music.audio_type").getString());
			if (!name.toLowerCase(Locale.ROOT).endsWith(ext)) name = name + ext;
			long len = res.headers().firstValueAsLong("Content-Length").orElse(-1);
			if (len > MAX_BYTES) throw new Exception(t("music.too_large").getString());
			File dir = UserMusic.folder();
			tmp = File.createTempFile("napuli-dl", ext, dir);
			long total = 0;
			try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp.toPath())) {
				byte[] buf = new byte[1 << 16];
				int n;
				while ((n = in.read(buf)) > 0) {
					total += n;
					if (total > MAX_BYTES) throw new Exception(t("music.too_large").getString());
					out.write(buf, 0, n);
				}
			}
			File target = SongDownloadFiles.available(dir, name);
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
			tmp = null;
			// make sure it really plays
			try (var check = UserAudio.open(target, 0, 1)) {
				if (!check.read(4096).hasRemaining()) throw new Exception("empty");
			} catch (Exception bad) {
				Files.deleteIfExists(target.toPath());
				throw new Exception(t("music.unsupported").getString());
			}
			UserMusic.rescan();
			say(t("music.downloaded", target.getName(), total / 1024), SUCCESS);
		} catch (Exception e) {
			say(t("music.download_failed", e.getMessage()), ERROR);
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
	protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float a) {
		paragraph(g, t("music.download_help"), left + 12, top + 46, panelWidth - 24, 3, MUTED);
		g.text(font, t("music.link"), left + 12, top + 82, TEXT, false);
		paragraph(g, status, left + 12, top + 128, panelWidth - 24, Math.max(1, (panelHeight - 170) / 11), statusColor);
	}
	@Override public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 && url.isFocused() && go.active) { start(); return true; }
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		UserMusic.rescan();
		minecraft.gui.setScreen(parent);
	}
}
