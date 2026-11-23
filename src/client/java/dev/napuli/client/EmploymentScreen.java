package dev.napuli.client;

import dev.napuli.EmploymentOffers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class EmploymentScreen extends Screen {
    private static final int INK = 0xff122927, PAPER = 0xfff6ead5, GOLD = 0xffdfab65;
    private final EmploymentOffers board;
    private int left, top, panelWidth, rowHeight, columns, bottom;

    EmploymentScreen(EmploymentOffers board) {
        super(Component.translatable("napuli.work.title"));
        this.board = board;
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EmploymentOffers.TYPE, (packet, ctx) ->
                ctx.client().gui.setScreen(new EmploymentScreen(packet)));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        panelWidth = Math.min(width - 12, 520);
        columns = panelWidth >= 280 || height < 200 ? 2 : 1;
        int rows = (board.offers().size() + columns - 1) / columns;
        rowHeight = Math.max(13, Math.min(44, (height - 68) / rows));
        left = (width - panelWidth) / 2;
        top = Math.max(0, (height - (58 + rows * rowHeight)) / 2);
        bottom = top + 55 + rows * rowHeight;
        int columnWidth = (panelWidth - 16) / columns;
        for (int i = 0; i < board.offers().size(); i++) {
            var offer = board.offers().get(i);
            int x = left + 8 + i % columns * columnWidth;
            int y = top + 39 + i / columns * rowHeight;
            Component label = Component.translatable("napuli.work.offer", Component.translatable(offer.name()), offer.wage());
            Button card = new Button(x, y, columnWidth - 4, rowHeight - 2, label, button -> {
                if (minecraft.getConnection() != null)
                    minecraft.getConnection().sendCommand("lavoro scegli " + offer.id());
                onClose();
            }, supplier -> supplier.get()) {
                @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                    int x = getX(), y = getY();
                    boolean current = board.current().equals(offer.id());
                    g.fill(x, y, x + getWidth(), y + getHeight(), isHoveredOrFocused() ? 0xffffdfac : 0xffeadcc4);
                    if (current) g.fill(x, y, x + 3, y + getHeight(), 0xff2d8265);
                    String pay = "€ " + offer.wage();
                    int payWidth = font.width(pay);
                    g.text(font, pay, x + getWidth() - payWidth - 5, y + 2, 0xff25664e, false);
                    var job = Component.translatable(offer.name()).getString();
                    g.text(font, font.plainSubstrByWidth(job, getWidth() - payWidth - 15), x + 7, y + 2, INK, false);
                    if (rowHeight >= 28) {
                        String place = offer.workplace().getX() + " / " + offer.workplace().getZ();
                        g.text(font, place, x + 7, y + 13, 0xff52645d, false);
                    }
                    if (rowHeight >= 41) {
                        var task = font.split(Component.translatable("napuli.work.task_" + offer.id()), getWidth() - 14);
                        for (int line = 0; line < Math.min(2, task.size()); line++)
                            g.text(font, task.get(line), x + 7, y + 23 + line * 9, 0xff364b45, false);
                    }
                }
            };
            if (rowHeight < 41) card.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("napuli.work.task_" + offer.id())));
            addRenderableWidget(card);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xa0102524);
        g.fill(left, top, left + panelWidth, bottom, PAPER);
        g.fill(left, top, left + panelWidth, top + 34, INK);
        g.fill(left, top + 34, left + panelWidth, top + 36, GOLD);
        g.centeredText(font, title, width / 2, top + 7, 0xfffff2dc);
        g.centeredText(font, "Mergellina", width / 2, top + 20, GOLD);
        String hours = "08:30 – 16:30  ·  Esc";
        g.text(font, hours, (width - font.width(hours)) / 2, bottom - 12, INK, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}
}
