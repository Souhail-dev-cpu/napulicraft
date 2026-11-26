package dev.napuli.client;

import dev.napuli.CompanionBoard;
import static dev.napuli.CityText.t;
import dev.napuli.Napuli;
import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Searchable city hub. Commands and transactions are still checked by the server. */
final class CompanionScreen extends NapoliScreen {
    static final KeyMapping OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.napuli.companion", InputConstants.KEY_O, KeyMapping.Category.register(Napuli.id("companion"))));
    private static final Map<String, String> FILTERS = new HashMap<>();
    private final CompanionBoard board;
    private final CompanionScreen returnTo;
    private final boolean options;
    private EditBox search;
    private Button previous, next, clear;
    private String query = "";
    private int page, rows, columns, rowHeight, contentTop, refreshTicks, optionCount;
    private List<CompanionBoard.Entry> visible = List.of();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();

    CompanionScreen(CompanionBoard board) {
        super(board.title()); this.board = board; options = false; returnTo = null;
        query = FILTERS.getOrDefault(board.page(), "");
    }
    private CompanionScreen(CompanionScreen returnTo) {
        super(Component.translatable("napuli.companion.options"));
        board = null; options = true; this.returnTo = returnTo;
    }
    static void register() {
        CompanionOptions.load(); CompanionEffectsClient.register();
        ClientPlayNetworking.registerGlobalReceiver(CompanionBoard.TYPE, (packet, ctx) -> {
            var old = ctx.client().gui.screen();
            var screen = new CompanionScreen(packet);
            if (old instanceof CompanionScreen previous && previous.board != null && previous.board.page().equals(packet.page())) {
                screen.query = previous.query; screen.page = previous.page;
            }
            ctx.client().gui.setScreen(screen);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.consumeClick()) if (mc.player != null && mc.gui.screen() == null && mc.getConnection() != null) mc.getConnection().sendCommand("compagno");
            if (mc.level == null) FILTERS.clear();
        });
    }
    private void command(String command) {
        if (command.isEmpty()) return;
        if (board != null) FILTERS.put(board.page(), query);
        if ((!command.startsWith("compagno ") && !command.equals("compagno")) || command.startsWith("compagno segui ") || command.startsWith("compagno fissa ") || command.startsWith("compagno obiettivo ") || command.startsWith("compagno invita ") || command.startsWith("compagno accetta ")) super.onClose();
        if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(command);
    }
    @Override protected Component subtitle() { return t(options ? "screen.options_help" : board.page().equals("menu") ? "screen.menu_help" : "screen.browse_help"); }

    @Override protected void init() {
        rowWidgets.clear();
        layout(560, height - 16);
        int navWidth = (panelWidth - 32) / 4;
        String[] keys = {"menu", "atlas", "favorites", "options"};
        String[] commands = {"compagno", "compagno atlante", "compagno preferiti", ""};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            var nav = button(left + 10 + i * (navWidth + 4), top + 42, navWidth, t("screen." + keys[i]), () -> {
                if (index == 3) minecraft.gui.setScreen(new CompanionScreen(this)); else command(commands[index]);
            });
            boolean selected = options ? i == 3 : i == 0 && board.page().equals("menu") || i == 1 && board.page().equals("atlante") || i == 2 && board.page().equals("preferiti");
            if (selected) markSelected(nav);
        }
        contentTop = top + (options ? 70 : 96);
        rowHeight = options ? 26 : height < 280 ? 30 : 44;
        columns = !options && board.page().equals("menu") && panelWidth >= 440 ? 2 : 1;
        rows = Math.max(1, (bottom - 36 - contentTop) / rowHeight);
        if (!options) {
            search = new EditBox(font, left + 10, top + 69, panelWidth - 48, 20, Component.translatable("napuli.companion.search"));
            search.setMaxLength(96); search.setValue(query); search.setHint(Component.translatable("napuli.companion.search"));
            search.setResponder(value -> { query = value; page = 0; refreshRows(); });
            addRenderableWidget(search);
            clear = button(left + panelWidth - 34, top + 69, 24, Component.literal("×"), () -> { search.setValue(""); setFocused(search); });
            clear.setTooltip(Tooltip.create(t("screen.clear_search")));
        }
        previous = button(left + 10, bottom - 26, 24, Component.literal("←"), () -> changePage(-1));
        previous.setTooltip(Tooltip.create(t("screen.previous")));
        next = button(left + 38, bottom - 26, 24, Component.literal("→"), () -> changePage(1));
        next.setTooltip(Tooltip.create(t("screen.next")));
        boolean hasBack = options || !board.page().equals("menu");
        if (hasBack) button(left + panelWidth - 146, bottom - 26, 66, t("screen.back"), this::back);
        button(left + panelWidth - 76, bottom - 26, 66, t("close"), this::onClose);
        if (options) settings(); else refreshRows();
    }

    private void back() {
        if (options && returnTo != null) { minecraft.gui.setScreen(returnTo); return; }
        String id = board.page();
        String target = id.startsWith("preventivo/") ? "compagno casa " + id.substring(11)
                : id.startsWith("casa/") ? "compagno case" : id.startsWith("luogo/") ? "compagno atlante"
                : id.startsWith("ricetta/") ? "compagno ricette" : id.startsWith("corse/") ? "compagno trasporti"
                : id.startsWith("permessi/") ? "compagno casa " + id.substring(9)
                : id.startsWith("scambio") ? "compagno scambi" : id.startsWith("cooperativa/") ? "compagno cooperative"
                : id.equals("parcheggi") || id.equals("recupero") ? "compagno motorini" : "compagno";
        command(target);
    }
    private int pageSize() { return rows * columns; }
    private int pageCount() { return Math.max(1, ((options ? optionCount : visible.size()) + pageSize() - 1) / pageSize()); }
    private void updatePaging() { previous.active = page > 0; next.active = page + 1 < pageCount(); }
    private void changePage(int direction) {
        int selected = Math.clamp(page + direction, 0, pageCount() - 1);
        if (selected == page) return;
        page = selected;
        if (options) rebuildWidgets(); else refreshRows();
    }
    private void settings() {
        var o = CompanionOptions.current;
        Component[] labels = {t("settings.city", on(o.city)), t("settings.kitchen", on(o.kitchen)), t("settings.tracker", on(o.tracker)), t("settings.compact", on(o.compact)), t("settings.size", t(o.size == 0 ? "settings.small" : o.size == 1 ? "settings.normal" : "settings.large")), t("settings.notices", on(o.notices)), t("settings.light", on(o.lightPreset)), t("settings.particles", on(o.reducedParticles)), t("settings.flashes", on(o.reducedFlashes)), t("settings.motion", on(o.reducedMotion)), t("settings.quiet", on(o.quietAmbience)), t("settings.text", on(o.textAlerts))};
        optionCount = labels.length; page = Math.clamp(page, 0, pageCount() - 1);
        for (int i = page * rows; i < Math.min(labels.length, (page + 1) * rows); i++) {
            final int option = i;
            var choice = button(left + 10, contentTop + (i - page * rows) * rowHeight, panelWidth - 20, 22, labels[i], () -> {
                switch (option) {
                    case 0 -> o.city = !o.city; case 1 -> o.kitchen = !o.kitchen; case 2 -> o.tracker = !o.tracker;
                    case 3 -> o.compact = !o.compact; case 4 -> o.size = (o.size + 1) % 3; case 5 -> o.notices = !o.notices;
                    case 6 -> o.lightPreset = !o.lightPreset; case 7 -> o.reducedParticles = !o.reducedParticles;
                    case 8 -> o.reducedFlashes = !o.reducedFlashes; case 9 -> o.reducedMotion = !o.reducedMotion;
                    case 10 -> o.quietAmbience = !o.quietAmbience; case 11 -> o.textAlerts = !o.textAlerts; default -> {}
                }
                o.save(); CompanionEffectsClient.apply(minecraft); rebuildWidgets();
            });
            choice.setTooltip(Tooltip.create(t("settings.help." + option)));
        }
        updatePaging();
    }
    private static Component on(boolean value) { return t(value ? "yes" : "no"); }
    private void refreshRows() {
        for (var widget : rowWidgets) removeWidget(widget);
        rowWidgets.clear();
        visible = board.entries().stream().filter(e -> CompanionBoard.matches(e, query)).toList();
        page = Math.clamp(page, 0, pageCount() - 1);
        int cellWidth = (panelWidth - 20 - (columns - 1) * 6) / columns;
        for (int i = page * pageSize(); i < Math.min(visible.size(), (page + 1) * pageSize()); i++) {
            var entry = visible.get(i);
            int cell = i - page * pageSize(), x = left + 10 + cell % columns * (cellWidth + 6), y = contentTop + cell / columns * rowHeight;
            int extraWidth = entry.extra().isEmpty() ? 0 : Math.clamp(font.width(extraLabel(entry.extra()).getString()) + 12, 26, 72);
            int gap = extraWidth > 0 ? 4 : 0;
            rowWidgets.add(card(x, y, cellWidth - extraWidth - gap, rowHeight - 4, entry.title(), entry.detail(), !entry.command().isEmpty(), () -> command(entry.command())));
            if (extraWidth > 0) {
                var extra = button(x + cellWidth - extraWidth, y, extraWidth, rowHeight - 4, extraLabel(entry.extra()), () -> command(entry.extraCommand()));
                extra.setTooltip(Tooltip.create(t(board.page().equals("ricette") ? "recipe.pin" : board.page().equals("amici") ? "group.decline" : board.page().equals("diario") ? "journal.action" : "atlas.favorite_hint")));
                rowWidgets.add(extra);
            }
        }
        if (visible.isEmpty()) {
            boolean filtered = !query.isBlank();
            rowWidgets.add(button(left + 20, contentTop + 42, panelWidth - 40, t(filtered ? "screen.clear_search" : "screen.open_atlas"), () -> {
                if (filtered) search.setValue(""); else command("compagno atlante");
            }));
        }
        if (clear != null) clear.active = !query.isEmpty();
        updatePaging();
    }
    private static Component extraLabel(String value) { return value.startsWith("napuli.ui.") ? Component.translatable(value) : Component.literal(value); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x >= left && x < left + panelWidth && y >= contentTop && y < bottom - 34 && vertical != 0) { changePage(vertical < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 266 || event.key() == 267) { changePage(event.key() == 266 ? -1 : 1); return true; }
        if (event.key() == 256 && search != null && !query.isEmpty() && search.isFocused()) { search.setValue(""); return true; }
        return super.keyPressed(event);
    }
    @Override public void tick() {
        super.tick();
        if (!options && board.page().startsWith("corse/") && ++refreshTicks >= 100) {
            refreshTicks = 0;
            if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("compagno corse " + board.page().substring(6));
        }
    }
    @Override public void onClose() { if (board != null) FILTERS.put(board.page(), query); super.onClose(); }
    @Override protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean hasBack = options || !board.page().equals("menu");
        var label = t("screen.pagination", page + 1, pageCount());
        g.text(font, fit(font, label.getString(), panelWidth - (hasBack ? 230 : 160)), left + 70, bottom - 20, MUTED, false);
        if (!options && visible.isEmpty()) paragraph(g, t(query.isBlank() ? "screen.no_entries" : "screen.empty"), left + 20, contentTop + 8, panelWidth - 40, 2, MUTED);
    }
}
