package dev.napuli.client.music;

import java.io.File;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * "Canzoni" (key J): pick which of YOUR songs plays in each situation and from which second.
 * Songs are files in napulicraft/canzoni; "Casuale" = a random one every time.
 */
public final class SongsScreen extends Screen {
	private final Screen parent;
	private int page;
	private String status = "";

	public SongsScreen(Screen parent) {
		super(Component.literal("Canzoni"));
		this.parent = parent;
		UserMusic.rescan();
	}

	private int rowsPerPage() { return Math.max(3, (height - 86) / 21); }
	private int pages() { return (UserMusic.SITUATIONS.size() + rowsPerPage() - 1) / rowsPerPage(); }

	@Override
	protected void init() {
		List<UserMusic.Song> lib = UserMusic.library();
		int rows = rowsPerPage();
		int labelW = Math.min(170, width / 4);
		int x0 = 10 + labelW;
		int titleW = Math.max(90, width - x0 - 20 - 20 - 26 - 44 - 26 - 30 - 20);
		int y = 34;
		List<String> sits = UserMusic.SITUATIONS;
		for (int i = page * rows; i < Math.min(sits.size(), (page + 1) * rows); i++, y += 21) {
			String sit = sits.get(i);
			UserMusic.Choice c = UserMusic.choice(sit);
			int x = x0;
			addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(sit, -1)).bounds(x, y, 20, 20).build());
			x += 20;
			String shown = c.file().isEmpty() ? "🎲 Casuale" : UserMusic.find(c.file()) == null ? "⚠ " + c.file() + " (manca)" : UserMusic.find(c.file()).title();
			addRenderableWidget(Button.builder(Component.literal(fit(shown, titleW - 8)), b -> preview(sit)).bounds(x, y, titleW, 20)
					.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Clicca pe' sentì 12 secondi"))).build());
			x += titleW;
			addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(sit, 1)).bounds(x, y, 20, 20).build());
			x += 26;
			addRenderableWidget(Button.builder(Component.literal("-5s"), b -> shift(sit, -5)).bounds(x, y, 26, 20).build());
			x += 26 + 44;
			addRenderableWidget(Button.builder(Component.literal("+5s"), b -> shift(sit, 5)).bounds(x, y, 26, 20).build());
		}
		int by = height - 28;
		int bw = Math.min(110, (width - 50) / 5);
		int bx = width / 2 - (bw * 5 + 16) / 2;
		addRenderableWidget(Button.builder(Component.literal("📂 Apri cartella"), b -> openFolder()).bounds(bx, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("🔄 Ricarica"), b -> { UserMusic.rescan(); rebuildWidgets(); status = UserMusic.library().size() + " canzoni"; })
				.bounds(bx + (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("⬇ Scarica link"), b -> minecraft.gui.setScreen(new DownloadScreen(this)))
				.bounds(bx + 2 * (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Pagina " + (page + 1) + "/" + pages()), b -> { page = (page + 1) % pages(); rebuildWidgets(); })
				.bounds(bx + 3 * (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Fatto"), b -> onClose()).bounds(bx + 4 * (bw + 4), by, bw, 20).build());
		if (lib.isEmpty()) status = "Nisciuna canzone: miette mp3/ogg/wav dint'a cartella e premi Ricarica";
	}

	private String fit(String s, int w) {
		if (font.width(s) <= w) return s;
		while (s.length() > 1 && font.width(s + "…") > w) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	private void cycle(String sit, int dir) {
		List<UserMusic.Song> lib = UserMusic.library();
		UserMusic.Choice c = UserMusic.choice(sit);
		int idx = -1; // -1 = casuale
		for (int i = 0; i < lib.size(); i++) if (lib.get(i).file().equals(c.file())) idx = i;
		int n = lib.size() + 1;
		int next = Math.floorMod(idx + 1 + dir, n) - 1;
		UserMusic.choose(sit, new UserMusic.Choice(next < 0 ? "" : lib.get(next).file(), c.start()));
		RadioStations.reset();
		rebuildWidgets();
	}

	private void shift(String sit, int d) {
		UserMusic.Choice c = UserMusic.choice(sit);
		UserMusic.choose(sit, new UserMusic.Choice(c.file(), Math.max(0, c.start() + d)));
		rebuildWidgets();
	}

	private void preview(String sit) {
		UserMusic.Song s = UserMusic.songFor(sit);
		if (s == null) { status = "Nisciuna canzone 'a sentì"; return; }
		int start = UserMusic.startFor(sit, s);
		UserMusic.preview(s, start);
		status = "♪ " + s.title() + " da " + time(start);
	}

	static String time(int sec) { return String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60); }

	private void openFolder() {
		File dir = UserMusic.folder();
		try {
			String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
			ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("explorer", dir.getAbsolutePath())
					: os.contains("mac") ? new ProcessBuilder("open", dir.getAbsolutePath()) : new ProcessBuilder("xdg-open", dir.getAbsolutePath());
			pb.start();
		} catch (Exception e) {
			status = "Cartella: " + dir.getAbsolutePath();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		super.extractRenderState(g, mx, my, a);
		g.centeredText(font, "🎵 Canzoni d'o Napulicraft — " + UserMusic.library().size() + " canzoni toje", width / 2, 8, 0xFFFFD54F);
		g.centeredText(font, "Cartella: napulicraft/canzoni  ·  sotto-cartelle = stazioni d'a radio", width / 2, 20, 0xFF9E9E9E);
		int rows = rowsPerPage();
		int labelW = Math.min(170, width / 4);
		int x0 = 10 + labelW;
		int titleW = Math.max(90, width - x0 - 20 - 20 - 26 - 44 - 26 - 30 - 20);
		int y = 34;
		List<String> sits = UserMusic.SITUATIONS;
		for (int i = page * rows; i < Math.min(sits.size(), (page + 1) * rows); i++, y += 21) {
			String sit = sits.get(i);
			g.text(font, fit(UserMusic.label(sit), labelW - 8), 10, y + 6, 0xFFFFFFFF, true);
			UserMusic.Choice c = UserMusic.choice(sit);
			int tx = x0 + 20 + titleW + 26 + 26; // right after the -5s button
			g.centeredText(font, "da " + time(c.start()), tx + 22, y + 6, 0xFFBBBBBB);
		}
		if (!status.isEmpty()) g.centeredText(font, status, width / 2, height - 42, 0xFF80DEEA);
	}

	@Override
	public void onClose() {
		UserMusic.stopScene();
		minecraft.gui.setScreen(parent);
	}
}
