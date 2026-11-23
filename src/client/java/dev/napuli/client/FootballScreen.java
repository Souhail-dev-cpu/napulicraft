package dev.napuli.client;

import dev.napuli.FootballBroadcast;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class FootballScreen extends Screen {
    private static final int PANEL = 0xff101d2a, TEXT = 0xffe6edf2;
    private static final int CHALK = 0xb8eff6d9, HOME = 0xff71d5fa, AWAY = 0xfff2efea;
    private FootballBroadcast.Frame broadcast;
    private double clock;
    private long lastRender;

    FootballScreen(FootballBroadcast.Frame frame) {
        super(Component.literal("Telenapoli Sport"));
        broadcast = frame;
        clock = frame.tick();
        lastRender = System.nanoTime();
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FootballBroadcast.Frame.TYPE, (frame, context) -> {
            var gui = context.client().gui;
            if (frame.open()) gui.setScreen(new FootballScreen(frame));
            else if (gui.screen() instanceof FootballScreen screen) {
                if (frame.mode() == FootballBroadcast.Frame.CLOSED) gui.setScreen(null);
                else screen.broadcast = frame;
            }
        });
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float partialTick) {}

    @Override public void removed() {
        if (ClientPlayNetworking.canSend(FootballBroadcast.Leave.TYPE))
            ClientPlayNetworking.send(new FootballBroadcast.Leave());
        super.removed();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        long now = System.nanoTime();
        if (broadcast.mode() != FootballBroadcast.Frame.FINAL)
            clock += Mth.clamp((now - lastRender) / 50_000_000.0, 0, 4);
        lastRender = now;
        int fieldHeight = Math.min(Math.max(1, height - 82), (width - 44) * 68 / 105);
        int fieldWidth = fieldHeight * 105 / 68;
        var pitch = new Pitch((width - fieldWidth) / 2, (height - fieldHeight) / 2 + 12, fieldWidth, fieldHeight);
        int panelLeft = pitch.x() - 16, panelRight = pitch.x() + pitch.w() + 16;
        int top = pitch.y() - 49;
        g.fill(0, 0, width, height, 0xbe07111a);
        g.fill(panelLeft, top, panelRight, pitch.y() + pitch.h() + 24, PANEL);
        scoreboard(g, panelLeft, panelRight, top);
        drawPitch(g, pitch);

        FootballPlay.Frame action = action();
        int ballX = pitch.px(action.ball().x()), ballY = pitch.py(action.ball().y());
        int selected = action.focus(), nearest = 65;
        for (int i = 0; i < action.players().length; i++) {
            var at = action.players()[i];
            int x = pitch.px(at.x()), y = pitch.py(at.y());
            int dist = (mx - x) * (mx - x) + (my - y) * (my - y);
            if (dist < nearest) {
                nearest = dist;
                selected = i;
            }
            drawPlayer(g, x, y, i, i == action.focus(), fieldHeight >= 125);
        }
        g.fill(ballX - 2, ballY - 2, ballX + 3, ballY + 3, PANEL);
        g.fill(ballX - 1, ballY - 1, ballX + 2, ballY + 2, 0xffffffff);

        var player = FootballPlay.player(selected);
        var at = action.players()[selected];
        String label = player.number() + " " + player.name();
        int labelWidth = font.width(label);
        int lx = Mth.clamp(pitch.px(at.x()) - labelWidth / 2, pitch.x() + 5, pitch.x() + pitch.w() - labelWidth - 5);
        int ly = Math.max(pitch.y() + 4, pitch.py(at.y()) - 18);
        g.fill(lx - 3, ly - 2, lx + labelWidth + 4, ly + 10, 0xee101d2a);
        g.text(font, label, lx, ly, FootballPlay.home(selected) ? HOME : AWAY, false);
        String hint = font.plainSubstrByWidth(Component.translatable("napuli.tv.match_hint").getString(),
                panelRight - panelLeft - 16);
        g.centeredText(font, hint, width / 2, pitch.y() + pitch.h() + 9, 0xffa9bac6);
    }

    FootballPlay.Frame action() { return FootballPlay.at(clock); }

    FootballBroadcast.Frame broadcast() { return broadcast; }

    private void scoreboard(GuiGraphicsExtractor g, int left, int right, int top) {
        g.fill(left, top, right, top + 2, 0xff66cbf0);
        g.text(font, "TELENAPOLI SPORT", left + 9, top + 9, TEXT, false);
        String mode = Component.translatable(switch (broadcast.mode()) {
            case FootballBroadcast.Frame.REPLAY -> "napuli.tv.replay";
            case FootballBroadcast.Frame.FINAL -> "napuli.tv.full_time";
            default -> "napuli.tv.on_air";
        }).getString();
        int badgeX = right - font.width(mode) - 12;
        g.fill(badgeX - 5, top + 5, right - 6, top + 20,
                broadcast.mode() == FootballBroadcast.Frame.LIVE ? 0xffa23d49 : 0xff294759);
        g.text(font, mode, badgeX, top + 9, TEXT, false);

        int mid = width / 2;
        g.fill(mid - 30, top + 24, mid + 30, top + 43, 0xff223848);
        g.centeredText(font, broadcast.napoli() + " – " + broadcast.juventus(), mid, top + 29, TEXT);
        String home = width < 300 ? "NAP" : "NAPOLI";
        String away = width < 300 ? "JUV" : "JUVENTUS";
        g.text(font, home, mid - 39 - font.width(home), top + 29, HOME, false);
        g.text(font, away, mid + 39, top + 29, AWAY, false);
        String minute = broadcast.minute() + "'";
        g.text(font, minute, right - font.width(minute) - 9, top + 29, 0xffa9bac6, false);
    }

    private record Pitch(int x, int y, int w, int h) {
        int px(float n) { return x + Math.round(n * w); }
        int py(float n) { return y + Math.round(n * h); }
    }

    private static void drawPitch(GuiGraphicsExtractor g, Pitch p) {
        for (int stripe = 0; stripe < 12; stripe++)
            g.fill(p.x() + stripe * p.w() / 12, p.y(), p.x() + (stripe + 1) * p.w() / 12,
                    p.y() + p.h(), stripe % 2 == 0 ? 0xff226342 : 0xff2a754a);
        box(g, p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), CHALK);
        g.fill(p.px(.5f), p.y(), p.px(.5f) + 1, p.y() + p.h(), CHALK);
        point(g, p.px(.5f), p.py(.5f), CHALK);
        arc(g, p, .5f, .5f, 0, 360);
        for (int side : new int[]{-1, 1}) {
            float edge = side < 0 ? 0 : 1;
            float inside = -side;
            int line = p.px(edge);
            area(g, p, line, p.px(edge + inside * .157f), .204f);
            area(g, p, line, p.px(edge + inside * .052f), .365f);
            float spot = edge + inside * .105f;
            point(g, p.px(spot), p.py(.5f), CHALK);
            arc(g, p, spot, .5f, side < 0 ? -52 : 128, side < 0 ? 52 : 232);
            int back = p.px(edge + side * .024f), y1 = p.py(.447f), y2 = p.py(.553f);
            int left = Math.min(line, back), right = Math.max(line, back);
            g.fill(left, y1, right, y2, 0xff36584e);
            for (int y = y1 + 2; y < y2; y += 2) g.fill(left, y, right, y + 1, 0xff74958a);
            box(g, left, y1, right, y2, 0xfff4f7ef);
        }
    }

    private static void arc(GuiGraphicsExtractor g, Pitch p, float x, float y, int start, int end) {
        for (int step = start; step <= end; step += 3) {
            double angle = Math.toRadians(step);
            int px = p.px(x + (float) Math.cos(angle) * 9.15f / 105);
            int py = p.py(y + (float) Math.sin(angle) * 9.15f / 68);
            g.fill(px, py, px + 1, py + 1, CHALK);
        }
    }

    private static void area(GuiGraphicsExtractor g, Pitch p, int edge, int inner, float top) {
        box(g, Math.min(edge, inner), p.py(top), Math.max(edge, inner), p.py(1 - top), CHALK);
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int right, int bottom, int color) {
        g.fill(x, y, right + 1, y + 1, color);
        g.fill(x, bottom, right + 1, bottom + 1, color);
        g.fill(x, y + 1, x + 1, bottom, color);
        g.fill(right, y + 1, right + 1, bottom, color);
    }

    private static void point(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x - 1, y - 1, x + 2, y + 2, color);
    }

    private void drawPlayer(GuiGraphicsExtractor g, int x, int y, int i, boolean active, boolean numbers) {
        var p = FootballPlay.player(i);
        boolean home = FootballPlay.home(i);
        int half = numbers ? 6 : 3;
        g.fill(x - half - 1, y - half, x + half + 2, y + half + 2, active ? 0xffffcc63 : PANEL);
        int shirt = p.number() == 1 ? (home ? 0xffe2ac5e : 0xffc793e4) : home ? HOME : AWAY;
        g.fill(x - half, y - half + 1, x + half + 1, y + half + 1, shirt);
        if (numbers) {
            String number = Integer.toString(p.number());
            g.text(font, number, x - font.width(number) / 2, y - 4, PANEL, false);
        }
    }
}
