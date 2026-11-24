package dev.napuli;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Two independent graphical Minecraft processes, including reconnect and measured FPS. */
public final class RealMultiplayerClient {
    private static String instruction = ""; private static int tick, waiting, offline, joined;
    private static final Set<String> RECEIVED = new HashSet<>();
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CityVerification.TYPE, (packet, ctx) -> {
            if (!Boolean.getBoolean("napuli.realmp")) return;
            if (RECEIVED.add(packet.instruction())) { instruction = packet.instruction(); waiting = 0; }
        });
        if (!CityPerformance.enabled()) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tick++; mc.options.pauseOnLostFocus = false;
            if (tick % 100 == 0 && mc.player != null) {
                try {
                    var folder = mc.gameDirectory.toPath().resolve("napulicraft/reports"); Files.createDirectories(folder);
                    var file = folder.resolve("client-performance.csv");
                    if (!Files.exists(file)) Files.writeString(file,"tick,fps,x,y,z,dimensione\n");
                    Files.writeString(file,String.format(Locale.ROOT,"%d,%d,%.2f,%.2f,%.2f,%s%n",tick,mc.getFps(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.level().dimension().identifier()),StandardOpenOption.APPEND);
                } catch (Exception e) { Napuli.LOGGER.warn("Cannot save FPS measurements", e); }
            }
            if (!Boolean.getBoolean("napuli.realmp")) return;
            if(mc.options.renderDistance().get()!=4)mc.options.renderDistance().set(4);
            if(mc.options.simulationDistance().get()!=5)mc.options.simulationDistance().set(5);
            if (mc.player == null) {
                if (++offline % 100 == 0 && !(mc.gui.screen() instanceof ConnectScreen)) {
                    String address = System.getProperty("napuli.realmp.address", "127.0.0.1:25576");
                    ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
                            new ServerData("Napoli QA", address, ServerData.Type.OTHER), false, null);
                }
                if (offline > 2400) throw new IllegalStateException("[REALMPCLIENT] Server connection timed out");
                return;
            }
            if (offline > 0) { joined++; offline = 0; Napuli.LOGGER.info("[REALMPCLIENT] Connected, session {}",joined); }
            if(!Double.isFinite(mc.player.getX())||!Double.isFinite(mc.player.getY())||!Double.isFinite(mc.player.getZ())||!Float.isFinite(mc.player.getYRot())||!Float.isFinite(mc.player.getXRot()))
                throw new AssertionError("[REALMPCLIENT] Invalid local movement: "+mc.player.position()+" yaw="+mc.player.getYRot()+" pitch="+mc.player.getXRot()+" velocity="+mc.player.getDeltaMovement());
            if (instruction.isEmpty() || ++waiting != 20) return;
            String[] parts = instruction.split("\\|", 2);
            if (parts[0].equals("command")) mc.player.connection.sendCommand(parts[1]);
            else if (parts[0].equals("observe")) {
                var expected=java.util.UUID.fromString(parts[1]);boolean visible=false;
                for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(expected))visible=true;
                if(!visible)throw new AssertionError("[REALMPCLIENT] Shared citizen is missing from the rendered client world");
                mc.player.connection.sendCommand("napuliqa observed");
            } else if (parts[0].equals("shot")) {
                Screenshot.grab(mc.gameDirectory,parts[1]+".png",mc.gameRenderer.mainRenderTarget(),1,m -> {});
                mc.gui.setScreen(null);
            } else if (parts[0].equals("done")) {
                Napuli.LOGGER.info("[REALMPCLIENT] ALL PASSED: graphical sessions={}, fps={}",joined,mc.getFps());
                mc.stop();
            }
            instruction = "";
        });
    }
    private RealMultiplayerClient() {}
}
