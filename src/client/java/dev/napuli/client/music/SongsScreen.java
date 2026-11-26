package dev.napuli.client.music;

import static dev.napuli.CityText.t;
import dev.napuli.client.NapoliScreen;
import java.io.File;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Personal music, with controls that fit both a small window and a large GUI scale. */
public final class SongsScreen extends NapoliScreen {
    private final Screen parent;
    private int page, rows;
    private Component status = Component.empty();

    public SongsScreen(Screen parent) {
        super(t("music.title")); this.parent = parent; UserMusic.rescan();
    }
    @Override public boolean isPauseScreen() { return true; }
    private int pages() { return Math.max(1, (UserMusic.SITUATIONS.size() + rows - 1) / rows); }
    @Override protected Component subtitle() { return t("music.subtitle", UserMusic.library().size()); }
    @Override protected void init() {
        layout(600, height - 16);
        rows = Math.max(1, (panelHeight - 134) / 52);
        page = Math.clamp(page, 0, pages() - 1);
        int toolWidth = (panelWidth - 28) / 3;
        button(left + 10, top + 44, toolWidth, t("music.folder"), this::openFolder);
        button(left + 14 + toolWidth, top + 44, toolWidth, t("music.reload"), () -> {
            UserMusic.rescan(); status = t("music.loaded", UserMusic.library().size()); rebuildWidgets();
        });
        button(left + 18 + 2 * toolWidth, top + 44, toolWidth, t("music.import"), () -> minecraft.gui.setScreen(new DownloadScreen(this)));
        if (!UserMusic.library().isEmpty()) {
            for (int i = page * rows; i < Math.min(UserMusic.SITUATIONS.size(), (page + 1) * rows); i++) {
                String sit = UserMusic.SITUATIONS.get(i);
                int y = top + 78 + (i - page * rows) * 52;
                UserMusic.Choice choice = UserMusic.choice(sit);
                var minus = button(left + panelWidth - 122, y, 28, 18, Component.literal("−5s"), () -> shift(sit, -5));
                minus.active = choice.start() > 0; minus.setTooltip(Tooltip.create(t("music.earlier")));
                button(left + panelWidth - 38, y, 28, 18, Component.literal("+5s"), () -> shift(sit, 5)).setTooltip(Tooltip.create(t("music.later")));
                button(left + 10, y + 22, 24, Component.literal("←"), () -> cycle(sit, -1)).setTooltip(Tooltip.create(t("music.previous")));
                var found = UserMusic.find(choice.file());
                Component shown = choice.file().isEmpty() ? t("music.random") : found == null ? t("music.missing", choice.file()) : Component.literal(found.title());
                button(left + 38, y + 22, panelWidth - 76, t("music.preview", shown), () -> preview(sit))
                        .setTooltip(Tooltip.create(t("music.preview_help", shown)));
                button(left + panelWidth - 34, y + 22, 24, Component.literal("→"), () -> cycle(sit, 1)).setTooltip(Tooltip.create(t("music.next")));
            }
        }
        var previous = button(left + 10, bottom - 26, 24, Component.literal("←"), () -> changePage(-1));
        previous.active = page > 0 && !UserMusic.library().isEmpty(); previous.setTooltip(Tooltip.create(t("screen.previous")));
        var next = button(left + 38, bottom - 26, 24, Component.literal("→"), () -> changePage(1));
        next.active = page + 1 < pages() && !UserMusic.library().isEmpty(); next.setTooltip(Tooltip.create(t("screen.next")));
        button(left + panelWidth - 154, bottom - 26, 70, t("music.stop"), () -> { UserMusic.stopScene(); status = t("music.stopped"); });
        button(left + panelWidth - 80, bottom - 26, 70, t("done"), this::onClose);
    }
    private void changePage(int direction) {
        int selected = Math.clamp(page + direction, 0, pages() - 1);
        if (selected != page) { page = selected; rebuildWidgets(); }
    }
    private void cycle(String sit, int direction) {
        List<UserMusic.Song> library = UserMusic.library();
        var choice = UserMusic.choice(sit);
        int index = -1;
        for (int i = 0; i < library.size(); i++) if (library.get(i).file().equals(choice.file())) index = i;
        int next = Math.floorMod(index + 1 + direction, library.size() + 1) - 1;
        UserMusic.choose(sit, new UserMusic.Choice(next < 0 ? "" : library.get(next).file(), choice.start()));
        RadioStations.reset(); status = t("music.saved"); rebuildWidgets();
    }
    private void shift(String sit, int seconds) {
        var choice = UserMusic.choice(sit);
        UserMusic.choose(sit, new UserMusic.Choice(choice.file(), Math.max(0, choice.start() + seconds)));
        status = t("music.saved"); rebuildWidgets();
    }
    private void preview(String sit) {
        var song = UserMusic.songFor(sit);
        if (song == null) { status = t("music.empty_title"); return; }
        int start = UserMusic.startFor(sit, song);
        UserMusic.preview(song, start); status = t("music.playing", song.title(), time(start));
    }
    static String time(int seconds) { return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private void openFolder() {
        File directory = UserMusic.folder();
        try {
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            ProcessBuilder process = os.contains("win") ? new ProcessBuilder("explorer", directory.getAbsolutePath())
                    : os.contains("mac") ? new ProcessBuilder("open", directory.getAbsolutePath()) : new ProcessBuilder("xdg-open", directory.getAbsolutePath());
            process.start(); status = t("music.folder_opened");
        } catch (Exception e) { status = Component.literal(directory.getAbsolutePath()); }
    }
    @Override protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean empty = UserMusic.library().isEmpty();
        if (empty) {
            g.text(font, t("music.empty_title"), left + 14, top + 84, GOLD, false);
            paragraph(g, t("music.empty_help"), left + 14, top + 102, panelWidth - 28, Math.max(1, (panelHeight - 168) / 11), MUTED);
        } else {
            for (int i = page * rows; i < Math.min(UserMusic.SITUATIONS.size(), (page + 1) * rows); i++) {
                String sit = UserMusic.SITUATIONS.get(i);
                int y = top + 78 + (i - page * rows) * 52;
                g.text(font, fit(font, t("music.situation." + sit).getString(), panelWidth - 142), left + 12, y + 5, GOLD, false);
                g.text(font, time(UserMusic.choice(sit).start()), left + panelWidth - 87, y + 5, MUTED, false);
            }
        }
        paragraph(g, status.getString().isEmpty() ? t("music.autosave") : status, left + 12, bottom - 58, panelWidth - 24, 2, ACCENT);
        g.text(font, t("screen.pagination", empty ? 1 : page + 1, empty ? 1 : pages()), left + 70, bottom - 20, MUTED, false);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!UserMusic.library().isEmpty() && vertical != 0 && x >= left && x < left + panelWidth && y >= top + 74 && y < bottom - 66) { changePage(vertical < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!UserMusic.library().isEmpty() && (event.key() == 266 || event.key() == 267)) { changePage(event.key() == 266 ? -1 : 1); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { UserMusic.stopScene(); minecraft.gui.setScreen(parent); }
}
