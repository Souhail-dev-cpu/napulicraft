package dev.napuli.client;

import static dev.napuli.CityText.t;
import dev.napuli.PickpocketView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Shows the whole carried inventory and highlights only today's randomly permitted units. */
final class PickpocketScreen extends NapoliScreen {
    private PickpocketView view;
    private Component status = Component.empty();
    private int page, columns, rows, cellWidth, cellHeight, refreshTicks;

    PickpocketScreen(PickpocketView view) { super(t("pick.title", view.owner())); this.view = view; }
    PickpocketView snapshot() { return view; }
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PickpocketView.TYPE, (packet, ctx) -> {
            var client = ctx.client();
            if (packet.open()) client.gui.setScreen(new PickpocketScreen(packet));
            else if (client.gui.screen() instanceof PickpocketScreen screen && screen.view.token().equals(packet.token())) {
                if (packet.closed()) screen.onClose();
                else {
                    screen.view = packet;
                    if (!packet.status().getString().isEmpty()) screen.status = packet.status();
                    screen.rebuildWidgets();
                }
            }
        });
    }
    @Override protected Component subtitle() { return t("pick.subtitle", view.stolen(), 5); }
    private int pageSize() { return rows * columns; }
    private int pages() { return Math.max(1, (view.entries().size() + pageSize() - 1) / pageSize()); }
    private void request(int slot) {
        if (ClientPlayNetworking.canSend(PickpocketView.Take.TYPE)) ClientPlayNetworking.send(new PickpocketView.Take(view.token(), slot));
    }
    @Override protected void init() {
        layout(560, height - 16);
        columns = panelWidth >= 440 ? 9 : 7;
        cellWidth = (panelWidth - 20) / columns;
        cellHeight = height < 280 ? 32 : 40;
        rows = Math.max(1, (panelHeight - 120) / cellHeight);
        page = Math.clamp(page, 0, pages() - 1);
        for (int i = page * pageSize(); i < Math.min(view.entries().size(), (page + 1) * pageSize()); i++) {
            final int index = i;
            var entry = view.entries().get(i);
            int cell = i - page * pageSize();
            var label = entry.label().copy().append(" · ").append(entry.stack().isEmpty() ? t("pick.empty") : entry.stack().getHoverName());
            var slot = new Button(left + 10 + cell % columns * cellWidth, top + 60 + cell / columns * cellHeight,
                    cellWidth - 3, cellHeight - 3, label, b -> { if (entry.available() > 0) request(index); }, s -> s.get()) {
                @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    int x = getX(), y = getY(), w = getWidth(), h = getHeight();
                    g.fill(x, y, x + w, y + h, isHoveredOrFocused() ? HOVER : SURFACE);
                    if (entry.available() > 0) border(g, x, y, w, h, ACCENT);
                    else if (isFocused()) border(g, x, y, w, h, MUTED);
                    if (!entry.stack().isEmpty()) {
                        g.item(entry.stack(), x + (w - 16) / 2, y + 3);
                        if (entry.stack().getCount() > 1) {
                            String count = Integer.toString(entry.stack().getCount());
                            g.text(font, count, x + w - font.width(count) - 2, y + 13, TEXT, true);
                        }
                        String hint = entry.available() > 0 ? "+" + entry.available() : "—";
                        g.text(font, hint, x + (w - font.width(hint)) / 2, y + h - 10, entry.available() > 0 ? ACCENT : MUTED, false);
                    }
                }
            };
            slot.active = !entry.stack().isEmpty();
            var tooltip = label.copy();
            if (!entry.stack().isEmpty()) {
                var details = getTooltipFromItem(minecraft, entry.stack());
                for (int line = 1; line < details.size(); line++) tooltip.append("\n").append(details.get(line));
            }
            slot.setTooltip(Tooltip.create(tooltip.append("\n").append(t(entry.available() > 0 ? "pick.allowed" : "pick.locked", entry.available()))));
            addRenderableWidget(slot);
        }
        var previous = button(left + 10, bottom - 26, 24, Component.literal("←"), () -> changePage(-1));
        previous.active = page > 0; previous.setTooltip(Tooltip.create(t("screen.previous")));
        var next = button(left + 38, bottom - 26, 24, Component.literal("→"), () -> changePage(1));
        next.active = page + 1 < pages(); next.setTooltip(Tooltip.create(t("screen.next")));
        if (view.canTalk()) button(left + panelWidth - 150, bottom - 26, 66, t("pick.talk"), () -> request(-3));
        button(left + panelWidth - 80, bottom - 26, 70, t("close"), this::onClose);
    }
    private void changePage(int amount) {
        int selected = Math.clamp(page + amount, 0, pages() - 1);
        if (selected != page) { page = selected; rebuildWidgets(); }
    }
    @Override protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.text(font, fit(font, t("pick.legend").getString(), panelWidth - 24), left + 12, top + 44, MUTED, false);
        int available = view.entries().stream().mapToInt(PickpocketView.Entry::available).sum();
        paragraph(g, view.stolen() >= 5 ? t("pick.exhausted") : status.getString().isEmpty() ? t("pick.help", available) : status,
                left + 12, bottom - 59, panelWidth - 24, 2, MUTED);
        g.text(font, t("screen.pagination", page + 1, pages()), left + 70, bottom - 20, MUTED, false);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && x >= left && x < left + panelWidth && y >= top + 60 && y < bottom - 62) { changePage(vertical < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 266 || event.key() == 267) { changePage(event.key() == 266 ? -1 : 1); return true; }
        return super.keyPressed(event);
    }
    @Override public void tick() { if (++refreshTicks >= 20) { refreshTicks = 0; request(-1); } }
    @Override public void removed() { request(-2); super.removed(); }
}
