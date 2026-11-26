package dev.napuli.client;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Shared, keyboard-accessible presentation for the city's non-inventory screens. */
public abstract class NapoliScreen extends Screen {
    public static final int PANEL = 0xff122630, SURFACE = 0xff1d3540, HOVER = 0xff2b4b58;
    public static final int TEXT = 0xfff5f1e7, MUTED = 0xffb7cdd3, ACCENT = 0xff79d6df;
    public static final int GOLD = 0xfff0c487, SUCCESS = 0xffa1e0b5, ERROR = 0xffffb3a6;
    protected int left, top, panelWidth, panelHeight, bottom;
    private final Set<Button> selectedButtons = new HashSet<>();

    protected NapoliScreen(Component title) { super(title); }

    protected final void layout(int maxWidth, int wantedHeight) {
        selectedButtons.clear();
        panelWidth = Math.max(1, Math.min(maxWidth, width - 16));
        panelHeight = Math.max(1, Math.min(wantedHeight, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        bottom = top + panelHeight;
    }

    protected Component subtitle() { return Component.empty(); }
    protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override public final void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xb309141c);
        g.fill(left + 3, top + 3, left + panelWidth + 3, bottom + 3, 0x60000000);
        g.fill(left, top, left + panelWidth, bottom, PANEL);
        g.fill(left, top, left + panelWidth, top + 2, ACCENT);
        g.text(font, fit(font, title.getString(), panelWidth - 24), left + 12, top + 10, TEXT, false);
        g.text(font, fit(font, subtitle().getString(), panelWidth - 24), left + 12, top + 25, MUTED, false);
        g.fill(left + 10, bottom - 34, left + panelWidth - 10, bottom - 33, 0xff36515e);
        extractPanel(g, mx, my, delta);
        super.extractRenderState(g, mx, my, delta);
    }

    @Override public final void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public boolean isPauseScreen() { return false; }

    protected final Button button(int x, int y, int w, Component label, Runnable action) {
        return button(x, y, w, 20, label, action);
    }

    protected final Button button(int x, int y, int w, int h, Component label, Runnable action) {
        var button = new Button(x, y, w, h, label, b -> action.run(), s -> s.get()) {
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                boolean focused = isHoveredOrFocused();
                boolean selected = selectedButtons.contains(this);
                int x = getX(), y = getY();
                g.fill(x, y, x + getWidth(), y + getHeight(), selected || active && focused ? HOVER : SURFACE);
                if (active && focused) border(g, x, y, getWidth(), getHeight(), ACCENT);
                if (selected) g.fill(x, y + getHeight() - 2, x + getWidth(), y + getHeight(), ACCENT);
                String text = fit(font, getMessage().getString(), getWidth() - 10);
                g.text(font, text, x + (getWidth() - font.width(text)) / 2, y + (getHeight() - 8) / 2,
                        selected ? ACCENT : active ? TEXT : 0xff8199a3, false);
            }
        };
        button.setTooltip(Tooltip.create(label));
        return addRenderableWidget(button);
    }

    protected final void markSelected(Button button) { selectedButtons.add(button); button.active = false; }

    protected final Button card(int x, int y, int w, int h, Component label, Component detail,
                                boolean actionable, Runnable action) {
        var card = new Button(x, y, w, h, label, b -> { if (actionable) action.run(); }, s -> s.get()) {
            @Override protected MutableComponent createNarrationMessage() {
                return label.copy().append(". ").append(detail);
            }
            @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
                int x = getX(), y = getY();
                g.fill(x, y, x + getWidth(), y + getHeight(), isHoveredOrFocused() ? HOVER : SURFACE);
                if (isFocused()) border(g, x, y, getWidth(), getHeight(), ACCENT);
                if (actionable) g.fill(x, y + 4, x + 2, y + getHeight() - 4, ACCENT);
                int textWidth = getWidth() - (actionable ? 28 : 16);
                g.text(font, fit(font, label.getString(), textWidth), x + 8, y + 5, TEXT, false);
                var lines = font.split(detail, Math.max(1, textWidth));
                int count = Math.max(0, (getHeight() - 16) / 10);
                for (int line = 0; line < Math.min(count, lines.size()); line++)
                    g.text(font, lines.get(line), x + 8, y + 17 + line * 10, MUTED, false);
                if (actionable) g.text(font, "›", x + getWidth() - 12, y + (getHeight() - 8) / 2, ACCENT, false);
            }
        };
        card.setTooltip(Tooltip.create(label.copy().append("\n").append(detail)));
        return addRenderableWidget(card);
    }

    protected final void paragraph(GuiGraphicsExtractor g, Component text, int x, int y, int w, int maxLines, int color) {
        var lines = font.split(text, Math.max(1, w));
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) g.text(font, lines.get(i), x, y + i * 11, color, false);
    }

    public static String fit(Font font, String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    protected static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
