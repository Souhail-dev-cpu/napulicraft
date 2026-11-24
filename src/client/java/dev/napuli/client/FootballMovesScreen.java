package dev.napuli.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class FootballMovesScreen extends Screen {
    private enum Move {
        DRIVE("tira"), CURL("effetto"), CHIP("pallonetto"),
        ROULETTE("roulette"), FEINT("finta"), SIDE_STEP("tocco");

        final String command;
        Move(String command) { this.command = command; }
        Component label() { return Component.translatable("napuli.football.move." + command); }
        Component description() { return Component.translatable("napuli.football.move." + command + ".desc"); }
    }

    private int left, top, panelWidth;

    FootballMovesScreen() { super(Component.translatable("napuli.football.moves")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {}

    @Override protected void init() {
        panelWidth = Math.min(width - 16, 284);
        left = (width - panelWidth) / 2;
        top = (height - 116) / 2;
        int cell = (panelWidth - 24) / 2;
        var moves = Move.values();
        for (int i = 0; i < 3; i++) {
            addMove(moves[i], left + 10, top + 39 + 22 * i, cell);
            addMove(moves[i + 3], left + 14 + cell, top + 39 + 22 * i, cell);
        }
    }

    private void addMove(Move move, int x, int y, int cell) {
        var button = new Button(x, y, cell, 19, move.label(), ignored -> {
            if (FootballControlsClient.canShoot()) FootballControlsClient.sendAction(minecraft, move.command);
            onClose();
        }, supplier -> supplier.get()) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                int x = getX(), y = getY();
                g.fill(x, y, x + getWidth(), y + getHeight(), isHoveredOrFocused() ? 0xff285847 : 0xff1a3038);
                g.fill(x, y, x + 2, y + getHeight(), 0xff68cb9a);
                g.text(font, font.plainSubstrByWidth(getMessage().getString(), getWidth() - 12),
                        x + 7, y + 5, 0xffeff7ed, false);
            }
        };
        button.setTooltip(Tooltip.create(move.description()));
        addRenderableWidget(button);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x3000141b);
        g.fill(left, top, left + panelWidth, top + 116, 0xff12242d);
        g.fill(left, top, left + panelWidth, top + 2, 0xff68cb9a);
        g.centeredText(font, title, width / 2, top + 9, 0xffeff7ed);
        g.text(font, Component.translatable("napuli.football.moves.shots"), left + 11, top + 25, 0xff9bc5b7, false);
        g.text(font, Component.translatable("napuli.football.moves.skills"), left + 16 + (panelWidth - 24) / 2, top + 25, 0xff9bc5b7, false);
        super.extractRenderState(g, mx, my, delta);
    }
}
