package dev.napuli.client;

import static dev.napuli.CityText.t;
import dev.napuli.*;
import dev.napuli.client.music.SongsScreen;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.*;
import javax.sound.sampled.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;

/** Responsive layouts and navigation exercised by runQolTest in its disposable client. */
final class InterfaceClientChecks {
    private static final int[][] SIZES = {{320, 240}, {367, 240}, {550, 360}, {960, 540}};

    static void run(Minecraft mc) throws Exception {
        Screen original = mc.gui.screen();
        var entries = new ArrayList<CompanionBoard.Entry>();
        for (int i = 0; i < 40; i++) entries.add(new CompanionBoard.Entry("Caffè " + i,
                "Una descrizione lunga per verificare la leggibilità delle schede e dei dettagli.", ""));
        var offers = new ArrayList<EmploymentOffers.Offer>();
        for (String id : List.of("baker", "porter", "merchant", "cleaner", "gardener", "fisher", "courier", "carpenter"))
            offers.add(new EmploymentOffers.Offer(id, "napuli.civic.job." + id, 9, BlockPos.ZERO));
        try {
            var atlas = new CompanionScreen(new CompanionBoard("atlante", t("atlas.title"), entries));
            mc.gui.setScreen(atlas);
            edit(atlas).setValue("");
            layouts(atlas);
            atlas.init(320, 240);
            check(!previous(atlas).active, "Previous page is disabled on the first page");
            atlas.keyPressed(new KeyEvent(267, 0, 0));
            check(previous(atlas).active, "Page Down advances the atlas");
            atlas.mouseScrolled(100, 130, 0, 1);
            check(!previous(atlas).active, "The mouse wheel returns to the first page");
            edit(atlas).setValue("caffe 39");
            check(buttons(atlas).stream().anyMatch(b -> b.getMessage().getString().equals("Caffè 39")), "Search ignores accents");
            atlas.init(367, 240);
            check(edit(atlas).getValue().equals("caffe 39"), "Resizing preserves the search");
            edit(atlas).setValue("nessun-risultato-qa");
            press(atlas, t("screen.clear_search").getString());
            check(edit(atlas).getValue().isEmpty(), "The empty search offers a working reset action");
            press(atlas, t("screen.options").getString());
            layouts(mc.gui.screen());

            for (Screen screen : List.of(
                    new CompanionScreen(new CompanionBoard("menu", t("menu.title"), entries)),
                    new CompanionScreen(new CompanionBoard("preferiti", t("atlas.favorites"), List.of())),
                    new EmploymentScreen(new EmploymentOffers(offers, "cleaner")),
                    new EmploymentScreen(new EmploymentOffers(List.of(), "")),
                    new FootballMenuScreen(new FootballMenu(false)), new FootballMenuScreen(new FootballMenu(true)),
                    new FootballMovesScreen(), new TelevisionScreen(new TelevisionProgram("SPORT", "Juventus", 2, 1, 30, 0, 1200)),
                    new TelevisionScreen(new TelevisionProgram("NEWS", "", 0, 0, 0, 0, 1200)), new SongsScreen(null))) {
                mc.gui.setScreen(screen); layouts(screen);
            }
            music(mc, original);
            Napuli.LOGGER.info("[QOLTEST] Responsive interfaces passed: four sizes, empty boards, accented search, reset, wheel, keyboard, music import and parent restoration");
        } finally { mc.gui.setScreen(original); }
    }

    static void showPreview(Minecraft mc, int index) {
        var offers = new ArrayList<EmploymentOffers.Offer>();
        for (String id : List.of("baker", "porter", "merchant", "cleaner", "gardener", "fisher", "courier", "carpenter"))
            offers.add(new EmploymentOffers.Offer(id, "napuli.civic.job." + id, 9, BlockPos.ZERO));
        Screen screen = switch (index) {
            case 0 -> new EmploymentScreen(new EmploymentOffers(offers, "cleaner"));
            case 1, 2 -> new SongsScreen(null);
            case 3 -> new FootballMenuScreen(new FootballMenu(true));
            default -> new TelevisionScreen(new TelevisionProgram("SPORT", "Juventus", 2, 1, 30, 0, 1200));
        };
        mc.gui.setScreen(screen);
        if (index == 2) press(screen, t("music.import").getString());
    }

    private static void music(Minecraft mc, Screen parent) throws Exception {
        var folder = dev.napuli.client.music.UserMusic.folder().toPath();
        var song = Files.createTempFile(folder, "interface-qa-", ".wav");
        try {
            var format = new AudioFormat(8000, 16, 1, true, false);
            try (var audio = new AudioInputStream(new ByteArrayInputStream(new byte[16000]), format, 8000)) {
                AudioSystem.write(audio, AudioFileFormat.Type.WAVE, song.toFile());
            }
            var songs = new SongsScreen(parent);
            mc.gui.setScreen(songs); layouts(songs);
            songs.init(320, 240);
            songs.keyPressed(new KeyEvent(267, 0, 0));
            press(songs, t("music.import").getString());
            var download = mc.gui.screen();
            edit(download).setValue("https://example.invalid/brano.wav");
            layouts(download);
            check(edit(download).getValue().equals("https://example.invalid/brano.wav"), "Resizing preserves the import link");
            edit(download).setValue("link non valido");
            press(download, t("music.download").getString());
            check(mc.gui.screen() == download, "An invalid link keeps the import form open");
            download.onClose();
            check(mc.gui.screen() == songs, "Import returns to the same music screen");
            songs.onClose();
            check(mc.gui.screen() == parent, "Music preserves its original parent");
        } finally { Files.deleteIfExists(song); dev.napuli.client.music.UserMusic.rescan(); }
    }

    static void layouts(Screen screen) {
        for (int[] size : SIZES) { screen.init(size[0], size[1]); bounds(screen); }
    }
    static void bounds(Screen screen) {
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w -> w.visible).toList();
        for (var widget : widgets) {
            check(widget.getWidth() > 0 && widget.getHeight() > 0 && widget.getX() >= 0 && widget.getY() >= 0
                    && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                    "Widget outside " + screen.width + "×" + screen.height + " in " + screen.getClass().getSimpleName() + ": " + widget.getMessage().getString());
        }
        for (int i = 0; i < widgets.size(); i++) for (int j = i + 1; j < widgets.size(); j++) {
            var a = widgets.get(i); var b = widgets.get(j);
            check(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                    "Overlapping controls: " + a.getMessage().getString() + " / " + b.getMessage().getString());
        }
    }
    private static List<AbstractButton> buttons(Screen screen) {
        return screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).toList();
    }
    private static EditBox edit(Screen screen) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static AbstractButton previous(Screen screen) {
        return buttons(screen).stream().filter(b -> b.getMessage().getString().equals("←")).findFirst().orElseThrow();
    }
    private static void press(Screen screen, String label) {
        var button = buttons(screen).stream().filter(b -> b.active && b.getMessage().getString().equals(label)).findFirst().orElseThrow();
        button.onPress(new KeyEvent(257, 0, 0));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private InterfaceClientChecks() {}
}
