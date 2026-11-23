package dev.napuli.client;

import dev.napuli.FootballMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class FootballMenuScreen extends Screen {
    private static final int PANEL = 0xff102932, PAPER = 0xffe8f2ed, TURF = 0xff33794f;
    private final FootballMenu menu;
    private int left, top, panelWidth, panelHeight;

    private FootballMenuScreen(FootballMenu menu) {
        super(Component.translatable("napuli.football.menu"));
        this.menu = menu;
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FootballMenu.TYPE, (packet, context) ->
                context.client().gui.setScreen(new FootballMenuScreen(packet)));
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {}

    @Override protected void init() {
        panelWidth = Math.min(width - 16, 354);
        panelHeight = menu.canReturn() ? 161 : 131;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int cell = (panelWidth - 28) / 2;
        option("watch_option", "guarda", left + 10, top + 62, cell);
        option("play_option", "gioca", left + 18 + cell, top + 62, cell);
        option("training_option", "palestra", left + 10, top + 91, cell);
        option("profile_option", "", left + 18 + cell, top + 91, cell);
        if (menu.canReturn()) option("return_option", "torna", left + 10, top + 120, panelWidth - 20);
    }

    private void option(String key, String command, int x, int y, int width) {
        Component label = Component.translatable("napuli.football." + key);
        Button button = new Button(x, y, width, 25, label, ignored -> {
            if (minecraft.getConnection() != null)
                minecraft.getConnection().sendCommand(command.isEmpty() ? "calcio" : "calcio " + command);
            onClose();
        }, supplier -> supplier.get()) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                int x = getX(), y = getY();
                g.fill(x, y, x + getWidth(), y + getHeight(), isHoveredOrFocused() ? 0xffcff3c0 : PAPER);
                g.fill(x, y, x + 3, y + getHeight(), TURF);
                String text = font.plainSubstrByWidth(label.getString(), getWidth() - 12);
                g.text(font, text, x + 8, y + 9, PANEL, false);
            }
        };
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        addRenderableWidget(button);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xc006161c);
        g.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        int greenWidth = panelWidth - 20;
        for (int i = 0; i < 8; i++)
            g.fill(left + 10 + i * greenWidth / 8, top + 10, left + 10 + (i + 1) * greenWidth / 8,
                    top + 53, i % 2 == 0 ? 0xff214f38 : 0xff285d3e);
        g.fill(width / 2, top + 10, width / 2 + 1, top + 53, 0x38eff6e8);
        g.centeredText(font, title, width / 2, top + 25, 0xfff5ffec);
        super.extractRenderState(g, mx, my, delta);
    }
}
