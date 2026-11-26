package dev.napuli.client;

import static dev.napuli.CityText.t;
import dev.napuli.TelevisionProgram;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** An original broadcast driven by the city's fictional calendar. */
final class TelevisionScreen extends NapoliScreen {
    private final TelevisionProgram program;
    private int elapsed;
    TelevisionScreen(TelevisionProgram program) { super(Component.literal("TeleNapoli")); this.program = program; }
    static void register() { ClientPlayNetworking.registerGlobalReceiver(TelevisionProgram.TYPE, (p, c) -> c.client().gui.setScreen(new TelevisionScreen(p))); }
    @Override public void tick() { super.tick(); elapsed++; }
    @Override protected Component subtitle() { return t("screen.tv_channel." + program.channel()); }
    @Override protected void init() {
        layout(460, 292);
        button(left + 10, bottom - 26, panelWidth - 20, t("screen.tv_back"), this::onClose);
    }
    @Override protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = left, y = top, w = panelWidth;
        boolean sport = program.channel().equals("SPORT") && !program.opponent().isEmpty();
        if (sport) {
            int fieldTop = y + 65, fieldBottom = bottom - 44, fieldHeight = fieldBottom - fieldTop;
            for (int stripe = 0; stripe < 10; stripe++) g.fill(x + 10 + stripe * (w - 20) / 10, fieldTop,
                    x + 10 + (stripe + 1) * (w - 20) / 10, fieldBottom, stripe % 2 == 0 ? 0xff247c40 : 0xff2c934d);
            g.fill(x + w / 2, fieldTop, x + w / 2 + 1, fieldBottom, 0xffe4eed9);
            g.fill(x + 10, fieldTop, x + w - 10, fieldTop + 1, 0xffe4eed9);
            g.fill(x + 10, fieldBottom - 1, x + w - 10, fieldBottom, 0xffe4eed9);
            int minute = Math.min(90, program.minute() + elapsed * 90 / Math.max(1, program.duration()));
            String score = "NAPOLI " + program.napoli() * minute / 90 + " – " + program.away() * minute / 90 + " "
                    + program.opponent().toUpperCase(java.util.Locale.ROOT) + "   " + minute + "'";
            g.text(font, fit(font, score, w - 24), x + 12, y + 47, TEXT, false);
            double time = (program.clock() + elapsed + delta) / 20.;
            for (int team = 0; team < 2; team++) for (int player = 0; player < 11; player++) {
                int px = x + 18 + (int)((player % 4 + 1) * (w - 40) / 5. + Math.sin(time * .6 + player + team) * 7);
                int py = fieldTop + 10 + (int)((player / 4. + team * .5) * (fieldHeight - 20) / 4 + Math.cos(time + player) * 5);
                g.fill(px, py, px + 4, py + 6, team == 0 ? 0xff61c6f2 : 0xfff3f3f3);
                g.fill(px, py, px + 4, py + 1, 0xffe3b98f);
            }
            int bx = x + w / 2 + (int)(Math.sin(time * .7) * (w - 80) / 3);
            int by = fieldTop + fieldHeight / 2 + (int)(Math.sin(time * 1.3) * (fieldHeight - 20) / 3);
            g.fill(bx, by, bx + 3, by + 3, 0xffffffff);
        } else {
            Component text = switch (program.channel()) {
                case "NEWS" -> t("screen.tv_news");
                case "WEATHER" -> Component.literal(program.opponent());
                default -> t("screen.tv_no_match");
            };
            paragraph(g, text, x + 14, y + 50, w - 28, Math.max(1, (panelHeight - 100) / 11), TEXT);
        }
    }
}
