package dev.napuli;

import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Opt-in integration test: runTaxtest. Uses a fresh disposable world and exits after verification. */
public final class TaxVisitTest {
    private static int titleTicks, phase, elapsed;
    private static boolean started;
    private static volatile int capture;
    private static volatile boolean captured, finished;
    private static NapoliResident collector;
    private static String paidToken;
    private static long due;

    private TaxVisitTest() {}

    public static void register() {
        if (!Boolean.getBoolean("napuli.taxtest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(TaxVisitTest::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(TaxVisitTest::serverTick);
    }

    private static void clientTick(Minecraft mc) {
        if (!started && mc.gui.screen() instanceof TitleScreen) {
            if (++titleTicks == 1) {
                mc.getLanguageManager().setSelected("it_it");
                mc.reloadResourcePacks();
            }
            if (titleTicks == 80) {
                started = true;
                var settings = new LevelSettings("napuli-tax-test", GameType.SURVIVAL,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("napuli-tax-test-" + System.currentTimeMillis(), settings,
                        SelectWorldScreen.TEST_OPTIONS, WorldPresets::createTestWorldDimensions, new TitleScreen());
            }
            return;
        }
        if (mc.player == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
        if (capture > 0 && !captured) {
            Screenshot.grab(mc.gameDirectory, "tax-" + capture + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    message -> Napuli.LOGGER.info("[TAXTEST] screenshot {}", message.getString()));
            captured = true;
        }
        if (finished) mc.stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void next(int value) { phase = value; elapsed = 0; }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static void serverTick(MinecraftServer server) {
        if (finished || server.getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
        if (phase == 0 && p.level().dimension() != NapoliMap.LEVEL) return;
        elapsed++;
        try {
            switch (phase) {
                case 0 -> {
                    if (elapsed < 60) return;
                    TaxPaymentChecks.run();
                    run(server, "gamerule advance_time false");
                    run(server, "time set day");
                    var arena = server.overworld();
                    for (BlockPos pos : BlockPos.betweenClosed(-60, 99, -60, 60, 99, 60))
                        arena.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
                    p.teleportTo(arena, 0.5, 100, 0.5, Set.of(), 0, 0, true);
                    p.getInventory().clearContent();
                    p.getInventory().setItem(0, new ItemStack(Items.DIRT, 64));
                    p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 20));
                    p.getInventory().setItem(2, new ItemStack(Napuli.KNIFE));
                    p.getInventory().setItem(3, new ItemStack(Napuli.CIABATTA_ITEM, 16));
                    p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                    p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND_PICKAXE));
                    due = server.overworld().getOverworldClockTime() + GiorgiaMeloni.INTERVAL;
                    p.setAttached(GiorgiaMeloni.NEXT_VISIT, due);
                    run(server, "time add 71999");
                    next(1);
                }
                case 1 -> {
                    if (elapsed < 40) return;
                    check(p.getAttached(GiorgiaMeloni.COLLECTOR) == null, "Tax arrived before three days");
                    check(server.overworld().getOverworldClockTime() == due - 1, "Unexpected day clock");
                    run(server, "time add 1");
                    next(2);
                }
                case 2 -> {
                    collector = p.getAttached(GiorgiaMeloni.COLLECTOR);
                    if (collector == null) { check(elapsed < 300, "No collector after three days"); return; }
                    double distance = Math.sqrt(Math.pow(collector.getX() - p.getX(), 2) + Math.pow(collector.getZ() - p.getZ(), 2));
                    check(distance >= 49 && distance <= 51, "Spawn distance was " + distance);
                    check(collector.role() == NapoliResident.Role.GIORGIA_MELONI, "Wrong NPC");
                    Napuli.LOGGER.info("[TAXTEST] Three-day schedule and 50-block spawn passed (distance={})", distance);
                    next(3);
                }
                case 3 -> {
                    if (!p.hasAttached(GiorgiaMeloni.SESSION)) { check(elapsed < 900, "Collector never reached player"); return; }
                    check(p.distanceToSqr(collector) <= 36, "Dialog opened too far away");
                    float yaw = (float) Math.toDegrees(Math.atan2(-(collector.getX() - p.getX()), collector.getZ() - p.getZ()));
                    p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ(), Set.of(), yaw, 0, true);
                    capture = 1;
                    captured = false;
                    next(4);
                }
                case 4 -> {
                    if (!captured || elapsed < 40) return;
                    var session = p.getAttached(GiorgiaMeloni.SESSION);
                    paidToken = session.token();
                    check(GiorgiaMeloni.answer(p, "00000000-0000-0000-0000-000000000000", true) == 0, "Forged token accepted");
                    check(GiorgiaMeloni.answer(p, paidToken, true) == 1, "Payment rejected");
                    check(p.getInventory().getItem(0).getCount() == 49, "Tax was not exactly fifteen blocks");
                    check(p.getInventory().getItem(1).getCount() == 20, "Diamonds taken before blocks");
                    check(p.getInventory().getItem(2).is(Napuli.KNIFE), "Knife taxed");
                    check(p.getInventory().getItem(3).getCount() == 16, "Ciabatta weapon taxed");
                    check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "Armor taxed");
                    check(p.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.DIAMOND_PICKAXE), "Tool taxed");
                    check(p.getAttached(GiorgiaMeloni.NEXT_VISIT) == server.overworld().getOverworldClockTime() + 72000, "Next visit not scheduled");
                    check(GiorgiaMeloni.answer(p, paidToken, true) == 0, "Replayed payment accepted");
                    check(p.getInventory().getItem(0).getCount() == 49, "Duplicate tax collected");
                    Napuli.LOGGER.info("[TAXTEST] Approach, dialog, payment cap, protected equipment and token replay passed");
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
                    check(GiorgiaMeloni.summon(p) == 1, "Cannot summon second visit");
                    p.getAttached(GiorgiaMeloni.COLLECTOR).relocate(p.blockPosition().offset(0, 0, 2), 180);
                    next(5);
                }
                case 5 -> {
                    var session = p.getAttached(GiorgiaMeloni.SESSION);
                    if (session == null) { check(elapsed < 100, "Refusal dialog not opened"); return; }
                    check(GiorgiaMeloni.answer(p, session.token(), false) == 1, "Refusal rejected");
                    check(Carabinieri.isJailed(p), "Refusal did not cause prison");
                    check(p.gameMode() == GameType.ADVENTURE && p.level().dimension() == NapoliMap.LEVEL, "Wrong prison state");
                    check(p.getInventory().getItem(2).is(Napuli.KNIFE), "Tax imprisonment confiscated weapon");
                    check(p.getInventory().getItem(0).getCount() == 49, "Refusal charged materials");
                    p.teleportTo(server.overworld(), 0.5, 100, 0.5, Set.of(), 0, 0, true);
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
                    next(6);
                }
                case 6 -> {
                    if (elapsed < 40) return;
                    check(p.level().dimension() == NapoliMap.LEVEL && Carabinieri.isJailed(p), "Dimension escape bypassed prison");
                    capture = 2;
                    captured = false;
                    next(7);
                }
                case 7 -> {
                    if (!captured || elapsed < 40) return;
                    run(server, "tick rate 200");
                    next(8);
                }
                case 8 -> {
                    if (Carabinieri.isJailed(p)) { check(elapsed < 1900, "Prison sentence did not expire"); return; }
                    check(p.gameMode() == GameType.SURVIVAL, "Original game mode not restored");
                    check(p.getInventory().getItem(2).is(Napuli.KNIFE), "Equipment lost on release");
                    check(p.blockPosition().distSqr(NapoliDistrict.PRISON_GATE) < 9, "Not released at prison gate");
                    run(server, "tick rate 20");
                    Napuli.LOGGER.info("[TAXTEST] ALL PASSED: refusal, prison, escape prevention and automatic release");
                    finished = true;
                }
                default -> throw new AssertionError("Unexpected phase");
            }
        } catch (Throwable failure) {
            Napuli.LOGGER.error("[TAXTEST] FAILED at phase {}", phase, failure);
            finished = true;
        }
    }
}
