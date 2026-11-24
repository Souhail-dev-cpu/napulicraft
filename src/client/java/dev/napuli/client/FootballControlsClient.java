package dev.napuli.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.napuli.FootballControls;
import dev.napuli.Napuli;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class FootballControlsClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Napuli.id("football"));
    private static final KeyMapping PASS = bind("pass", InputConstants.KEY_G);
    private static final KeyMapping SHOOT = bind("shoot", InputConstants.KEY_V);
    private static final KeyMapping CURL = bind("curl", InputConstants.KEY_K);
    private static final KeyMapping CHIP = bind("chip", InputConstants.KEY_M);
    private static final KeyMapping REQUEST = bind("request", InputConstants.KEY_B);
    private static final KeyMapping INTERCEPT = bind("intercept", InputConstants.KEY_N);
    private static final KeyMapping MOVES = bind("moves", InputConstants.KEY_U);
    private static int situation, stamina, remaining;
    private static boolean away;

    private FootballControlsClient() {}

    private static KeyMapping bind(String action, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.napuli.football." + action, key, CATEGORY));
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FootballControls.TYPE, (packet, ctx) -> {
            situation = packet.situation();
            stamina = packet.stamina();
            away = packet.away();
            remaining = 12;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null) remaining = 0;
            else if (remaining > 0) remaining--;
            handle(mc, PASS, "passa", situation == FootballControls.YOURS);
            handle(mc, SHOOT, "tira", canShoot());
            handle(mc, CURL, "effetto", canShoot());
            handle(mc, CHIP, "pallonetto", canShoot());
            while (MOVES.consumeClick()) openMoves(mc);
            if (mc.gui.screen() instanceof FootballMovesScreen screen && !canShoot()) screen.onClose();
            handle(mc, REQUEST, "chiedi", situation == FootballControls.TEAM);
            handle(mc, INTERCEPT, "intercetta", situation == FootballControls.OPPONENT || situation == FootballControls.FREE);
        });
        HudElementRegistry.addLast(Napuli.id("football_controls"), (graphics, delta) -> {
            if (!visible() || Minecraft.getInstance().gui.screen() instanceof FootballMovesScreen) return;
            var font = Minecraft.getInstance().font;
            var title = Component.translatable(away ? "napuli.football.side_away" : "napuli.football.side_home");
            var labels = switch (situation) {
                case FootballControls.YOURS -> new Component[]{label(PASS, "pass"), label(SHOOT, "shoot"), label(MOVES, "moves")};
                case FootballControls.TEAM -> new Component[]{label(REQUEST, "request")};
                case FootballControls.TEAM_PASS -> new Component[0];
                default -> new Component[]{label(INTERCEPT, "intercept")};
            };
            int width = font.width(title);
            for (var label : labels) width = Math.max(width, font.width(label));
            int x = 6, y = 56;
            var staminaText = Component.translatable("napuli.football.stamina", stamina);
            width = Math.max(width, font.width(staminaText));
            graphics.fill(x - 4, y - 4, x + width + 5, y + 33 + 11 * labels.length, 0xee15222e);
            graphics.fill(x - 4, y - 4, x + width + 5, y - 2, 0xff58c4ed);
            graphics.text(font, title, x, y, 0xffb5dfed, true);
            graphics.text(font, staminaText, x, y + 12, 0xffaaddb6, false);
            graphics.fill(x, y + 23, x + width, y + 27, 0xff3e4a51);
            graphics.fill(x, y + 23, x + width * stamina / 100, y + 27, stamina > 25 ? 0xff62c680 : 0xffff8861);
            for (int i = 0; i < labels.length; i++) {
                graphics.text(font, labels[i], x, y + 31 + 11 * i, 0xffe5ffc9, true);
            }
        });
    }

    static boolean visible() { return remaining > 0; }

    static boolean canShoot() { return visible() && situation == FootballControls.YOURS; }

    private static Component label(KeyMapping mapping, String action) {
        return Component.translatable("napuli.football.hud." + action, mapping.getTranslatedKeyMessage());
    }

    private static void handle(Minecraft mc, KeyMapping mapping, String command, boolean enabled) {
        while (mapping.consumeClick()) {
            if (enabled && mc.gui.screen() == null) sendAction(mc, command);
        }
    }

    static void openMoves(Minecraft mc) {
        if (canShoot() && mc.gui.screen() == null) mc.gui.setScreen(new FootballMovesScreen());
    }

    static void sendAction(Minecraft mc, String command) {
        if (visible() && mc.player != null) mc.player.connection.sendCommand("calcio " + command);
    }
}
