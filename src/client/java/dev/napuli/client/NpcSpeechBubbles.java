package dev.napuli.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.napuli.NapoliResident;
import dev.napuli.NpcSpeech;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Small bounded client queues; immutable render snapshots follow the interpolated speaker. */
public final class NpcSpeechBubbles {
    private static final int WIDTH = 164, MAX_SPEAKERS = 64, MAX_QUEUE = 6;
    private static final Map<UUID, ArrayDeque<Line>> LINES = new LinkedHashMap<>();
    private static Object world;
    private static long clock;
    private record Line(Component message, List<FormattedCharSequence> lines, int width, long starts, long ends) {}
    record Bubble(List<FormattedCharSequence> lines, int width, float opacity) {}

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NpcSpeech.TYPE, (packet, context) -> {
            var client = context.client();var receivedIn = client.level;
            client.execute(() -> { if (receivedIn != null && client.level == receivedIn) receive(packet); });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            syncWorld(client);
            if (client.level == null || client.isPaused()) return;
            clock++;
            LINES.values().forEach(queue -> { while (!queue.isEmpty() && queue.peekFirst().ends() <= clock) queue.removeFirst(); });
            LINES.values().removeIf(Deque::isEmpty);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    private static void syncWorld(Minecraft client) {
        if (world != client.level) { clear();world = client.level; }
    }
    private static void clear() { LINES.clear();clock = 0;world = null; }
    private static void receive(NpcSpeech packet) {
        var client = Minecraft.getInstance();syncWorld(client);
        String text = packet.message().getString();
        if (text.isBlank()) return;
        if (text.length() > 512) text = text.substring(0, 509) + "…";
        var message = Component.literal(text);
        var queue = LINES.computeIfAbsent(packet.speaker(), id -> new ArrayDeque<>());
        if (queue.stream().anyMatch(line -> line.message().equals(message))) return;
        var wrapped = client.font.split(message, WIDTH);
        int pages = Math.min(MAX_QUEUE - 1, (wrapped.size() + 5) / 6);
        while (queue.size() + pages > MAX_QUEUE) queue.removeLast();
        for (int page = 0; page < pages; page++) {
            var lines = List.copyOf(wrapped.subList(page * 6, Math.min(wrapped.size(), (page + 1) * 6)));
            int width = lines.stream().mapToInt(client.font::width).max().orElse(0);
            long start = queue.isEmpty() ? clock : queue.peekLast().ends();
            int duration = Math.clamp(65 + text.length() * 2 / pages, 100, 240);
            queue.addLast(new Line(message, lines, width, start, start + duration));
        }
        while (LINES.size() > MAX_SPEAKERS) LINES.remove(LINES.keySet().iterator().next());
    }

    /** Also used by the opt-in rendered integration test. */
    public static boolean hasBubble(UUID speaker) {
        var queue = LINES.get(speaker);return queue != null && !queue.isEmpty() && queue.peekFirst().ends() > clock;
    }

    static Bubble extract(NapoliResident npc, float partialTick) {
        var client = Minecraft.getInstance();var queue = LINES.get(npc.getUUID());
        if (queue == null || queue.isEmpty() || !npc.isAlive() || client.player == null
                || npc.isInvisibleTo(client.player) || client.player.distanceToSqr(npc) > (CompanionOptions.current.lightPreset?12*12:NpcSpeech.RANGE*NpcSpeech.RANGE)) return null;
        var line = queue.peekFirst();double now = clock + partialTick;
        float opacity = (float)Math.clamp(Math.min((now - line.starts()) / 5, (line.ends() - now) / 16), 0, 1);
        return opacity < .04f ? null : new Bubble(line.lines(), line.width(), opacity);
    }

    static void submit(Bubble bubble, Font font, EntityRenderState state, PoseStack pose,
            SubmitNodeCollector collector, CameraRenderState camera) {
        if (bubble == null || state.isInvisible) return;
        pose.pushPose();
        pose.translate(0, state.boundingBoxHeight + .65, 0);
        pose.rotate(camera.orientation);
        pose.scale(.025f, -.025f, .025f);
        float left = -bubble.width() / 2f - 8, top = -bubble.lines().size() * 11 - 20;
        int alpha = Math.round(255 * bubble.opacity());
        int border = alpha << 24 | 0x293640, fill = alpha << 24 | 0xfff8e9;
        rounded(collector, pose, left, top, bubble.width() + 16, bubble.lines().size() * 11 + 12, border);
        for (int i = 0; i < 6; i++) box(collector, pose, -6+i, -8+i, 6-i, -7+i, border);
        pose.translate(0, 0, .01);
        rounded(collector, pose, left+1, top+1, bubble.width()+14, bubble.lines().size()*11+10, fill);
        for (int i = 0; i < 4; i++) box(collector, pose, -4+i, -8+i, 4-i, -7+i, fill);
        pose.translate(0, 0, .01);
        for (int i = 0; i < bubble.lines().size(); i++) {
            var line = bubble.lines().get(i);
            collector.submitText(pose, -font.width(line)/2f, top+6+i*11, line, false,
                    Font.DisplayMode.NORMAL, 0xf000f0, alpha << 24 | 0x192833, 0, 0);
        }
        pose.popPose();
    }
    private static void rounded(SubmitNodeCollector collector, PoseStack pose, float x, float y, float width, float height, int color) {
        box(collector,pose,x+2,y,x+width-2,y+height,color);
        box(collector,pose,x,y+2,x+width,y+height-2,color);
        box(collector,pose,x+1,y+1,x+width-1,y+height-1,color);
    }
    private static void box(SubmitNodeCollector collector, PoseStack pose, float x1, float y1, float x2, float y2, int color) {
        collector.submitTextBackground(pose,x1,y1,x2,y2,color,Font.DisplayMode.NORMAL,0xf000f0);
    }
    private NpcSpeechBubbles() {}
}
