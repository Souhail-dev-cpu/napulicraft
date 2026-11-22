package dev.napuli;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in client/server test with one real client and two simulated remote players. */
public final class MotorinoTest {
    private static volatile int phase, bikeId, clientStep;
    private static volatile boolean finished;
    private static int titleTicks, serverTicks, phaseTicks, clientTicks;
    private static Motorino bike;
    private static Vec3 start;
    private static float heading;
    private static final List<ServerPlayer> bots = new ArrayList<>();
    private static final List<EmbeddedChannel> channels = new ArrayList<>();

    public static void register() {
        if (!Boolean.getBoolean("napuli.motorinotest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(MotorinoTest::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(MotorinoTest::serverTick);
    }

    private static void clientTick(Minecraft mc) {
        if (finished) { mc.options.keyUp.setDown(false); mc.stop(); return; }
        if (mc.player == null) {
            if (mc.gui.screen() instanceof TitleScreen && ++titleTicks == 80) {
                var settings = new LevelSettings("napuli-motorino-test", GameType.SURVIVAL,
                        new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("napuli-motorino-test-" + System.currentTimeMillis(), settings,
                        SelectWorldScreen.TEST_OPTIONS, WorldPresets::createTestWorldDimensions, new TitleScreen());
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
        var entity = mc.level.getEntity(bikeId);
        if (!(entity instanceof Motorino replica)) return;
        if (++clientTicks % 200 == 0) Napuli.LOGGER.info("[MOTORINOTEST] client phase={} riders={} localVehicle={} parade={}", phase,
                replica.getPassengers().stream().map(e -> e.getName().getString()).toList(), mc.player.getVehicle() == replica, replica.inParade());
        mc.options.keyUp.setDown(phase == 3 || phase == 5 || phase == 7);
        if (phase == 1 && clientStep != 1 && mc.player.distanceToSqr(replica) < 9) {
            mc.gameMode.interact(mc.player, replica, new EntityHitResult(replica, replica.position().add(0, 0.5, 0)), InteractionHand.MAIN_HAND);
            clientStep = 1;
        }
        if (phase == 2 && replica.getPassengers().size() == 3 && replica.getControllingPassenger() == mc.player) {
            check(replica.getPassengers().get(1).getUUID().equals(bots.get(0).getUUID()), "Client middle rider");
            check(replica.getPassengers().get(2).getUUID().equals(bots.get(1).getUUID()), "Client rear rider");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            clientStep = 2;
        }
        if (phase == 4 && clientStep != 4) {
            Screenshot.grab(mc.gameDirectory, "motorino-three-players.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            ClientPlayNetworking.send(new Radio.Tune(5));
            clientStep = 4;
        }
        if (phase == 5 && replica.getFirstPassenger() != mc.player && mc.player.getVehicle() == replica && clientStep != 5) {
            mc.player.setYRot(90);
            ClientPlayNetworking.send(new Radio.Tune(9));
            clientStep = 5;
        }
        if (phase == 6 && replica.getFirstPassenger() == mc.player && clientStep != 6) {
            mc.player.setYRot(0);
            ClientPlayNetworking.send(new Radio.Tune(3));
            clientStep = 6;
        }
    }

    private static void serverTick(MinecraftServer server) {
        if (finished || server.getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        try {
            if (++serverTicks > 1600) throw new IllegalStateException("Client test timed out in phase " + phase);
            phaseTicks++;
            if (phaseTicks % 200 == 0) Napuli.LOGGER.info("[MOTORINOTEST] phase={} clientStep={} riders={}", phase, clientStep,
                    bike == null ? 0 : bike.getPassengers().size());
            switch (phase) {
                case 0 -> {
                    if (player.level().dimension() != NapoliMap.LEVEL) return;
                    var level = server.overworld();
                    for (int x = -8; x <= 8; x++) for (int z = -8; z <= 24; z++) {
                        level.setBlock(new BlockPos(x, 128, z), Blocks.STONE.defaultBlockState(), 3);
                        for (int y = 129; y <= 134; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                    player.teleportTo(level, -0.5, 129, 0.5, Set.of(), 0, 0, true);
                    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Napuli.CASCO));
                    bike = Napuli.MOTORINO.create(level, EntitySpawnReason.EVENT);
                    bike.snapTo(0.5, 129, 0.5, 0, 0);
                    level.addFreshEntity(bike);
                    bikeId = bike.getId();
                    for (int i = 0; i < 2; i++) {
                        var profile = new GameProfile(UUID.randomUUID(), "MotorinoTest" + i);
                        var bot = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
                        var connection = new Connection(PacketFlow.SERVERBOUND);
                        channels.add(new EmbeddedChannel(connection));
                        server.getPlayerList().placeNewPlayer(connection, bot, CommonListenerCookie.createInitial(profile, false));
                        bot.teleportTo(level, 2.5 + i, 129, 0.5, Set.of(), 0, 0, true);
                        bot.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Napuli.CASCO));
                        bots.add(bot);
                    }
                    next(1);
                }
                case 1 -> {
                    if (player.getVehicle() != bike || phaseTicks < 20) return;
                    // Let the first-join welcome teleport finish before returning the simulated players to the track.
                    for (ServerPlayer bot : bots) {
                        bot.teleportTo(server.overworld(), 2.5, 129, 0.5, Set.of(), 0, 0, true);
                        check(bot.startRiding(bike), "Remote player boards");
                    }
                    next(2);
                }
                case 2 -> {
                    if (clientStep != 2) return;
                    start = bike.position();
                    next(3);
                }
                case 3 -> {
                    if (phaseTicks < 45) return;
                    check(bike.position().distanceTo(start) > 1.5, "Real driver moves three riders");
                    check(bike.getPassengers().size() == 3, "Three players remain mounted while driving");
                    next(4);
                }
                case 4 -> {
                    if (clientStep != 4 || phaseTicks < 20) return;
                    check(bike.station() == 5, "Driver radio packet reaches server");
                    player.stopRiding();
                    check(player.startRiding(bike, true, true), "Former driver joins at the back");
                    check(bike.getControllingPassenger() == bots.getFirst(), "Remote player takes control");
                    heading = bike.getYRot();
                    next(5);
                }
                case 5 -> {
                    if (clientStep != 5 || phaseTicks < 40) return;
                    check(bike.station() == 5, "Passenger's real radio packet is rejected");
                    check(Math.abs(bike.getYRot() - heading) < 0.01, "Passenger cannot steer");
                    for (ServerPlayer bot : bots) bot.stopRiding();
                    check(bike.getControllingPassenger() == player, "Local passenger promoted to driver");
                    next(6);
                }
                case 6 -> {
                    if (clientStep != 6 || phaseTicks < 20) return;
                    check(bike.station() == 3, "Promoted driver can tune through network");
                    start = bike.position();
                    next(7);
                }
                case 7 -> {
                    if (phaseTicks < 40) return;
                    check(bike.position().distanceTo(start) > 1.5, "Promoted driver can move through network");
                    player.stopRiding();
                    bike.discard();
                    for (ServerPlayer bot : bots) server.getPlayerList().remove(bot);
                    for (EmbeddedChannel channel : channels) channel.finishAndReleaseAll();
                    Napuli.LOGGER.info("[MOTORINOTEST] ALL PASSED: three players, synchronized seats, driving, passenger rejection and promotion");
                    finished = true;
                }
            }
        } catch (Throwable failure) {
            Napuli.LOGGER.error("[MOTORINOTEST] FAILED in phase " + phase, failure);
            finished = true;
        }
    }

    private static void next(int value) {
        phaseTicks = 0;
        phase = value;
        Napuli.LOGGER.info("[MOTORINOTEST] phase {}", value);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
