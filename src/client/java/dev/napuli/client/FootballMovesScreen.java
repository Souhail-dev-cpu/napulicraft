package dev.napuli.client;

import static dev.napuli.CityText.t;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class FootballMovesScreen extends NapoliScreen {
    private enum Move {
        DRIVE("tira"), CURL("effetto"), CHIP("pallonetto"),
        ROULETTE("roulette"), FEINT("finta"), SIDE_STEP("tocco");
        final String command;
        Move(String command) { this.command = command; }
        Component label() { return Component.translatable("napuli.football.move." + command); }
        Component description() { return Component.translatable("napuli.football.move." + command + ".desc"); }
    }
    FootballMovesScreen() { super(Component.translatable("napuli.football.moves")); }
    @Override protected Component subtitle() { return t("screen.moves_help"); }
    @Override protected void init() {
        layout(460, 224);
        int cellWidth = (panelWidth - 26) / 2;
        var moves = Move.values();
        for (int i = 0; i < 3; i++) {
            addMove(moves[i], left + 10, top + 60 + 40 * i, cellWidth);
            addMove(moves[i + 3], left + 16 + cellWidth, top + 60 + 40 * i, cellWidth);
        }
        button(left + 10, bottom - 26, panelWidth - 20, t("close"), this::onClose);
    }
    private void addMove(Move move, int x, int y, int w) {
        card(x, y, w, 36, move.label(), move.description(), true, () -> {
            if (FootballControlsClient.canShoot()) FootballControlsClient.sendAction(minecraft, move.command);
            onClose();
        });
    }
    @Override protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.text(font, Component.translatable("napuli.football.moves.shots"), left + 12, top + 44, GOLD, false);
        g.text(font, Component.translatable("napuli.football.moves.skills"), left + 18 + (panelWidth - 26) / 2, top + 44, GOLD, false);
    }
}
