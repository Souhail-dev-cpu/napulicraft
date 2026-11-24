package dev.napuli.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.napuli.FootballPractice;
import dev.napuli.Hud;
import dev.napuli.NapoliAtlas;
import dev.napuli.NapoliMap;
import dev.napuli.Napuli;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Client-only radar. Terrain is sampled from chunks the server has already sent. */
final class NapoliRadar {
    private static final int EDGE = 0xff101a20, RIM = 0xff849792, UNKNOWN = 0xff27363a;
    private static final int LANDMARK = 0xff9bddd0, OBJECTIVE = 0xffffce65;
    private static final int TILE = 3;
    private static final KeyMapping MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.napuli.radar", InputConstants.KEY_Y, KeyMapping.Category.register(Napuli.id("radar"))));
    private static final Long2IntOpenHashMap TERRAIN = new Long2IntOpenHashMap();
    private static ClientLevel level;
    private static int mode;
    private static int ticks;
    private static String district = "";
    private static int districtX = Integer.MIN_VALUE, districtZ = Integer.MIN_VALUE;

    private NapoliRadar() {}

    static void register() {
        TERRAIN.defaultReturnValue(0);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != level) {
                level = client.level;
                TERRAIN.clear();
                districtX = Integer.MIN_VALUE;
                districtZ = Integer.MIN_VALUE;
                ticks = 0;
            }
            if (++ticks % 20 == 0) TERRAIN.clear();
            while (MODE.consumeClick()) if (inNapoli(client) && client.gui.screen() == null) mode = (mode + 1) % 3;
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Napuli.id("radar"), (graphics, delta) ->
                draw(graphics, delta.getGameTimeDeltaPartialTick(false)));
    }

    static boolean inNapoli(Minecraft client) {
        return client.player != null && client.level != null && client.level.dimension() == NapoliMap.LEVEL;
    }

    private static void draw(GuiGraphicsExtractor g, float partialTick) {
        var mc = Minecraft.getInstance();
        if (!inNapoli(mc) || level != mc.level || mc.gui.screen() != null || mode == 2) return;
        int radius = Math.clamp(Math.min(g.guiHeight() / 6 - 6, (g.guiWidth() / 2 - 108) / 2), 20, 39);
        int x = radius + 9, y = g.guiHeight() - radius - 12;
        double scale = mode == 0 ? 1.65 : 3.3;
        double worldX = mc.player.getX(), worldZ = mc.player.getZ();
        var frame = Frame.fromYaw(mc.player.getViewYRot(partialTick));
        disc(g, x, y, radius + 3, EDGE);
        disc(g, x, y, radius + 1, RIM);
        disc(g, x, y, radius, UNKNOWN);
        terrain(g, frame, x, y, radius, scale, worldX, worldZ);

        for (var place : NapoliAtlas.PLACES) {
            double dx = place.x() - worldX, dz = place.z() - worldZ;
            if (Math.hypot(dx, dz) > (radius - 8) * scale) continue;
            var point = frame.project(dx / scale, dz / scale);
            diamond(g, x + (int)Math.round(point.x), y + (int)Math.round(point.y), 2, LANDMARK);
        }
        var hud = HudClient.state();
        if (hud != null && hud.tx() != Hud.NO_TARGET) {
            var point = frame.project((hud.tx() - worldX) / scale, (hud.tz() - worldZ) / scale);
            double distance = Math.hypot(point.x, point.y);
            double clamp = distance > radius - 7 ? (radius - 7) / distance : 1.0;
            diamond(g, x + (int)Math.round(point.x * clamp), y + (int)Math.round(point.y * clamp), 3, OBJECTIVE);
        }

        var north = frame.project(0, -radius);
        int nx = x + (int)Math.round(north.x * .84), ny = y + (int)Math.round(north.y * .84);
        g.centeredText(mc.font, Component.literal("N"), nx, ny - 4, 0xfff3f0d9);
        for (int row = 0; row <= 7; row++) {
            int half = row / 2;
            g.fill(x - half - 1, y + row - 5, x + half + 2, y + row - 4, EDGE);
            if (row > 0 && row < 7) g.fill(x - half, y + row - 5, x + half + 1, y + row - 4, 0xfff7f4e7);
        }
        int cellX = Math.floorDiv(mc.player.getBlockX(), 8);
        int cellZ = Math.floorDiv(mc.player.getBlockZ(), 8);
        if (cellX != districtX || cellZ != districtZ) {
            districtX = cellX;
            districtZ = cellZ;
            district = districtAt(mc.player.getBlockX(), mc.player.getBlockZ());
        }
        var subtitle = mc.player.getBlockY() < 59
                ? Component.translatable("napuli.radar.underground", district) : Component.literal(district);
        String label = mc.font.plainSubstrByWidth(subtitle.getString(), Math.max(90, radius * 2 + 22));
        int labelWidth = mc.font.width(label);
        int labelX = Math.max(5, x - labelWidth / 2);
        int labelY = y - radius - 16;
        g.fill(labelX - 3, labelY - 2, labelX + labelWidth + 3, labelY + 10, 0xe9101a20);
        g.text(mc.font, label, labelX, labelY, 0xffe6eee6, false);
    }

    static String districtAt(int x, int z) {
        return FootballPractice.atSportsGround(x, z) ? "Fuorigrotta" : NapoliAtlas.nearest(x, z).district();
    }

    private static void terrain(GuiGraphicsExtractor g, Frame frame, int x, int y, int radius, double scale,
            double worldX, double worldZ) {
        for (int row = -radius; row < radius; row += TILE) {
            int nextRow = Math.min(row + TILE, radius);
            int half = (int)Math.sqrt(radius * radius - Math.max(row * row, nextRow * nextRow));
            for (int col = -half; col < half; col += TILE) {
                int nextCol = Math.min(half, col + TILE);
                var point = frame.unproject((col + nextCol) * .5 * scale, (row + nextRow) * .5 * scale);
                int bx = (int)Math.floor(worldX + point.x), bz = (int)Math.floor(worldZ + point.y);
                int color = terrainColor(bx, bz);
                g.fill(x + col, y + row, x + nextCol, y + nextRow, color);
            }
        }
    }

    private static int terrainColor(int x, int z) {
        long key = ChunkPos.pack(x, z);
        int color = TERRAIN.get(key);
        if (color != 0) return color;
        var pos = new BlockPos(x, level.getMinY(), z);
        if (!level.hasChunkAt(pos)) return UNKNOWN;
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        pos = pos.atY(surface - 1);
        var state = level.getBlockState(pos);
        MapColor map = state.getMapColor(level, pos);
        if (map == MapColor.WATER || state.is(Blocks.WATER)) color = 0xff335b67;
        else if (map == MapColor.GRASS || map == MapColor.PLANT) color = 0xff455e49;
        else if (state.is(Blocks.POLISHED_ANDESITE) || state.is(Blocks.SMOOTH_STONE)
                || state.is(Blocks.POLISHED_DEEPSLATE) || state.is(Blocks.STONE_BRICKS)) color = 0xffadb5ad;
        else if (map == MapColor.NONE) color = UNKNOWN;
        else {
            int rgb = map.col;
            double shade = surface > 70 ? .61 : .47;
            int r = 20 + (int)((rgb >> 16 & 255) * shade);
            int g = 25 + (int)((rgb >> 8 & 255) * shade);
            int b = 28 + (int)((rgb & 255) * shade);
            color = 0xff000000 | r << 16 | g << 8 | b;
        }
        TERRAIN.put(key, color);
        return color;
    }

    private static void disc(GuiGraphicsExtractor g, int x, int y, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = (int)Math.sqrt(radius * radius - row * row);
            g.fill(x - half, y + row, x + half + 1, y + row + 1, color);
        }
    }

    private static void diamond(GuiGraphicsExtractor g, int x, int y, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int width = radius - Math.abs(row);
            g.fill(x - width - 1, y + row, x + width + 2, y + row + 1, EDGE);
            g.fill(x - width, y + row, x + width + 1, y + row + 1, color);
        }
    }

    record Point(double x, double y) {}
    record Frame(double cos, double sin) {
        static Frame fromYaw(float yaw) {
            double radians = Math.toRadians(yaw);
            return new Frame(Math.cos(radians), Math.sin(radians));
        }
        Point project(double dx, double dz) { return new Point(-cos * dx - sin * dz, sin * dx - cos * dz); }
        Point unproject(double x, double y) { return new Point(-cos * x + sin * y, -sin * x - cos * y); }
    }
}
