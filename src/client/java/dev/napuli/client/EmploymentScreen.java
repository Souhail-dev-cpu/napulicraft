package dev.napuli.client;

import static dev.napuli.CityText.t;
import dev.napuli.EmploymentOffers;
import dev.napuli.Hud;
import dev.napuli.NapoliEmployment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

final class EmploymentScreen extends NapoliScreen {
    private final EmploymentOffers board;
    private int page, rows, columns, rowHeight;

    EmploymentScreen(EmploymentOffers board) {
        super(Component.translatable("napuli.work.title"));
        this.board = board;
    }
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EmploymentOffers.TYPE, (packet, ctx) -> ctx.client().gui.setScreen(new EmploymentScreen(packet)));
    }
    @Override protected Component subtitle() {
        return t("screen.work_help", Hud.clock(NapoliEmployment.OPEN), Hud.clock(NapoliEmployment.CLOSE));
    }
    private int pageSize() { return rows * columns; }
    private int pages() { return Math.max(1, (board.offers().size() + pageSize() - 1) / pageSize()); }
    @Override protected void init() {
        layout(560, 330);
        columns = panelWidth >= 440 ? 2 : 1;
        rowHeight = height < 280 ? 44 : 52;
        rows = Math.max(1, (panelHeight - 84) / rowHeight);
        page = Math.clamp(page, 0, pages() - 1);
        int cellWidth = (panelWidth - 20 - 6 * (columns - 1)) / columns;
        for (int i = page * pageSize(); i < Math.min(board.offers().size(), (page + 1) * pageSize()); i++) {
            var offer = board.offers().get(i);
            int cell = i - page * pageSize();
            boolean current = board.current().equals(offer.id());
            var label = Component.translatable("napuli.work.offer", Component.translatable(offer.name()), offer.wage());
            var detail = t(current ? "screen.work_current" : "screen.work_choose").copy().append(" · ")
                    .append(Component.translatable("napuli.work.task_" + offer.id()));
            var choice = card(left + 10 + cell % columns * (cellWidth + 6), top + 44 + cell / columns * rowHeight,
                    cellWidth, rowHeight - 4, label, detail, !current, () -> {
                        if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lavoro scegli " + offer.id());
                        onClose();
                    });
            choice.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label.copy().append("\n")
                    .append(t("screen.work_pay", offer.wage())).append("\n").append(detail)));
        }
        var prev = button(left + 10, bottom - 26, 24, Component.literal("←"), () -> changePage(-1));
        prev.active = page > 0; prev.setTooltip(net.minecraft.client.gui.components.Tooltip.create(t("screen.previous")));
        var next = button(left + 38, bottom - 26, 24, Component.literal("→"), () -> changePage(1));
        next.active = page + 1 < pages(); next.setTooltip(net.minecraft.client.gui.components.Tooltip.create(t("screen.next")));
        button(left + panelWidth - 80, bottom - 26, 70, t("close"), this::onClose);
    }
    private void changePage(int direction) {
        int selected = Math.clamp(page + direction, 0, pages() - 1);
        if (selected != page) { page = selected; rebuildWidgets(); }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && x >= left && x < left + panelWidth && y >= top + 42 && y < bottom - 34) { changePage(vertical < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 266 || event.key() == 267) { changePage(event.key() == 266 ? -1 : 1); return true; }
        return super.keyPressed(event);
    }
    @Override protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.text(font, t("screen.pagination", page + 1, pages()), left + 70, bottom - 20, MUTED, false);
        if (board.offers().isEmpty()) paragraph(g, t("screen.work_empty"), left + 14, top + 50, panelWidth - 28, 3, MUTED);
    }
}
