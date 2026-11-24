package dev.napuli.client;

import dev.napuli.NapoliNotice;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class NapoliNoticeToast implements Toast {
    private static final int WIDTH = 208;
    private final Component message;
    private final List<FormattedCharSequence> lines;
    private Visibility visibility = Visibility.SHOW;

    private NapoliNoticeToast(Font font, Component message) {
        this.message = message;
        lines = font.split(message, WIDTH - 20);
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NapoliNotice.TYPE, (packet, ctx) -> {
            var client = ctx.client();
            var manager = client.gui.toastManager();
            if (manager.getToast(NapoliNoticeToast.class, packet.message()) == null)
                manager.addToast(new NapoliNoticeToast(client.font, packet.message()));
        });
    }

    @Override public Object getToken() { return message; }
    @Override public int width() { return WIDTH; }
    @Override public int height() { return 26 + lines.size() * 10; }
    @Override public Visibility getWantedVisibility() { return visibility; }

    @Override public void update(ToastManager manager, long elapsed) {
        long duration = Math.max(3800, 2250 + lines.size() * 1150L);
        if (elapsed >= duration * manager.getNotificationDisplayTimeMultiplier()) visibility = Visibility.HIDE;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, Font font, long elapsed) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof FootballMovesScreen) return;
        g.fill(0, 1, WIDTH - 3, height() - 2, 0xf414222d);
        g.fill(0, 1, 2, height() - 2, 0xff77d1ef);
        g.text(font, "Napulicraft", 10, 7, 0xff9cdef4, false);
        for (int i = 0; i < lines.size(); i++)
            g.text(font, lines.get(i), 10, 20 + i * 10, 0xfff5f2e9, false);
    }
}
