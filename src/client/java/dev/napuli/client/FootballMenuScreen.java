package dev.napuli.client;

import static dev.napuli.CityText.t;
import dev.napuli.FootballMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class FootballMenuScreen extends NapoliScreen {
    private final FootballMenu menu;
    FootballMenuScreen(FootballMenu menu) {
        super(Component.translatable("napuli.football.menu")); this.menu = menu;
    }
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FootballMenu.TYPE, (packet, context) -> context.client().gui.setScreen(new FootballMenuScreen(packet)));
    }
    @Override protected Component subtitle() { return t("screen.football_help"); }
    @Override protected void init() {
        layout(520, menu.canReturn() ? 226 : 198);
        int third = (panelWidth - 28) / 3, half = (panelWidth - 24) / 2;
        option("watch_option", "guarda", left + 10, top + 44, third);
        option("play_option", "gioca napoli", left + 14 + third, top + 44, third);
        option("away_option", "gioca ospiti", left + 18 + 2 * third, top + 44, third);
        option("training_option", "palestra", left + 10, top + 72, half);
        option("profile_option", "", left + 14 + half, top + 72, half);
        option("role.attack", "ruolo attaccante", left + 10, top + 114, third);
        option("role.midfield", "ruolo centrocampista", left + 14 + third, top + 114, third);
        option("role.defense", "ruolo difensore", left + 18 + 2 * third, top + 114, third);
        option("league_option", "campionato", left + 10, top + 142, panelWidth - 20);
        if (menu.canReturn()) option("return_option", "torna", left + 10, top + 168, panelWidth - 20);
        button(left + 10, bottom - 26, panelWidth - 20, t("close"), this::onClose);
    }
    private void option(String key, String command, int x, int y, int w) {
        button(x, y, w, 22, Component.translatable("napuli.football." + key), () -> {
            if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(command.isEmpty() ? "calcio" : "calcio " + command);
            onClose();
        });
    }
    @Override protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.text(font, t("screen.football_role"), left + 12, top + 101, GOLD, false);
    }
}
