package dev.napuli.client;

import dev.napuli.FootballStats;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class FootballStatsScreen extends NapoliScreen {
    private static final int PITCH = 0xff10242a, TRACK = 0xff32454b, SHEET = 0xff172f35;
    private static final String[] ATTRIBUTES = {"pace", "shooting", "defense", "strength", "stamina"};

    private final FootballStats stats;
    private int tab;
    private int selected;
    private int detailX;
    private final FootballStats.Line personal;

    private FootballStatsScreen(FootballStats stats, FootballStatsScreen previous) {
        super(label("title"));
        this.stats = stats;
        personal = stats.lineup().stream().filter(row -> !row.npc() && row.name().equals(stats.profile().name()))
                .findFirst().orElse(stats.profile());
        if (stats.live() && previous != null) {
            tab = previous.tab;
            selected = previous.selected;
        }
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FootballStats.TYPE, (packet, context) -> {
            var previous = context.client().gui.screen();
            context.client().gui.setScreen(new FootballStatsScreen(packet,
                    previous instanceof FootballStatsScreen screen ? screen : null));
        });
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("napuli.football.stats." + key, args);
    }

    private String sideName(int side) {
        String name = side == 0 ? stats.home() : stats.away();
        return name.isBlank() ? label(side == 0 ? "home" : "away").getString() : name;
    }

    private Component tabLabel(int index) {
        if (index == 0) return label("me");
        if (personal.side() < 0) return label(index == 1 ? "home" : "away");
        return label(index - 1 == personal.side() ? "teammates" : "opponents");
    }

    private List<FootballStats.Line> roster() {
        return stats.lineup().stream().filter(row -> row.side() == tab - 1).toList();
    }

    @Override protected Component subtitle() {
        return stats.live() ? Component.literal(sideName(0) + "  " + stats.homeGoals() + " – " + stats.awayGoals()
                + "  " + sideName(1)) : label("career");
    }

    @Override protected void init() {
        layout(560, 268);
        int gap = 4;
        int tabWidth = (panelWidth - 24 - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int target = i;
            var entry = button(left + 12 + i * (tabWidth + gap), top + 38, tabWidth, 20,
                    tabLabel(i), () -> {
                        tab = target;
                        selected = 0;
                        rebuildWidgets();
                    });
            if (i == tab) markSelected(entry);
            else if (!stats.live()) entry.active = false;
        }

        detailX = left + 12;
        if (tab != 0) {
            var players = roster();
            selected = Math.clamp(selected, 0, Math.max(0, players.size() - 1));
            int rosterWidth = Math.min(158, (panelWidth - 32) / 3);
            int rowHeight = Math.clamp((panelHeight - 105) / 5, 12, 23);
            for (int i = 0; i < players.size(); i++) {
                int number = i;
                var row = players.get(i);
                var entry = button(left + 12, top + 64 + i * rowHeight, rosterWidth, rowHeight - 2,
                        Component.literal(row.name()), () -> {
                            selected = number;
                            rebuildWidgets();
                        });
                if (i == selected) markSelected(entry);
            }
            detailX += rosterWidth + 8;
        }

        int footerWidth = (panelWidth - 28) / 2;
        button(left + 12, bottom - 26, footerWidth, 18, label("refresh"), () -> {
            if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("calcio statistiche");
        });
        button(left + 16 + footerWidth, bottom - 26, footerWidth, 18, Component.translatable("gui.done"), this::onClose);
    }

    @Override protected void extractPanel(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var players = tab == 0 ? List.<FootballStats.Line>of() : roster();

        FootballStats.Line row = players.isEmpty() ? personal : players.get(selected);
        int x = detailX, y = top + 64, w = left + panelWidth - 12 - x;
        int end = bottom - 39;
        g.fill(x, y, x + w, end, SHEET);
        g.fill(x, y, x + w, y + 2, tab == 2 ? GOLD : ACCENT);

        int badge = 42;
        g.fill(x + w - badge - 5, y + 4, x + w - 5, y + 31, PITCH);
        int badgeCenter = x + w - badge / 2 - 5;
        g.text(font, "OVR", badgeCenter - font.width("OVR") / 2, y + 7, MUTED, false);
        String overall = value(row.ratings().overall());
        g.pose().pushMatrix();
        g.pose().translate(badgeCenter, y + 16);
        g.pose().scale(1.5f);
        g.text(font, overall, -font.width(overall) / 2, 0, GOLD, false);
        g.pose().popMatrix();

        g.text(font, fit(font, row.name(), w - badge - 20), x + 7, y + 7, TEXT, false);
        String club = row.club().isBlank() && row.side() >= 0 ? sideName(row.side()) : row.club();
        String details = Component.translatable(row.role()).getString();
        if (!club.isBlank()) details += " · " + club;
        g.text(font, fit(font, details, w - badge - 20), x + 7, y + 19, MUTED, false);

        int start = y + 33, metrics = end - 28;
        int rowHeight = Math.max(7, Math.min(18, (metrics - start) / ATTRIBUTES.length));
        var values = row.ratings().values();
        for (int index = 0; index < ATTRIBUTES.length; index++) {
            int at = start + index * rowHeight, rating = values[index];
            String name = label(ATTRIBUTES[index]).getString();
            String number = value(rating);
            int barStart = x + Math.max(68, w / 3), barEnd = x + w - 34;
            g.text(font, fit(font, name, barStart - x - 12), x + 7, at, TEXT, false);
            g.text(font, number, x + w - 9 - font.width(number), at, ratingColor(rating), false);
            if (barEnd > barStart) {
                g.fill(barStart, at + 2, barEnd, at + 6, TRACK);
                if (rating >= 0) g.fill(barStart, at + 2, barStart + (barEnd - barStart) * rating / 99, at + 6,
                        ratingColor(rating));
            }
        }

        String[] keys = {row.matches() < 0 ? "shots" : "matches", "goals", "passes", "recoveries"};
        int[] numbers = {row.matches() < 0 ? row.shots() : row.matches(), row.goals(), row.passes(), row.recoveries()};
        int cell = (w - 8) / keys.length;
        for (int i = 0; i < keys.length; i++) {
            int at = x + 4 + cell * i;
            g.fill(at, metrics + 1, at + cell - 2, end - 4, PITCH);
            g.text(font, fit(font, label(keys[i]).getString(), cell - 7), at + 3, metrics + 4, MUTED, false);
            g.text(font, value(numbers[i]), at + 3, metrics + 15, TEXT, false);
        }
    }

    private static String value(int rating) { return rating < 0 ? "—" : Integer.toString(rating); }

    private static int ratingColor(int rating) {
        if (rating < 0) return MUTED;
        if (rating >= 75) return ACCENT;
        return rating >= 50 ? GOLD : 0xffe49885;
    }
}
