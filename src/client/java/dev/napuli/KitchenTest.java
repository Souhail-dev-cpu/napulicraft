package dev.napuli;

import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in real-client checkout, eating while sprinting, delayed effects, puzzle and taming. */
public final class KitchenTest {
    private static volatile int phase, clientStep, monkId;
    private static volatile boolean finished;
    private static int titleTicks, elapsed, phaseTicks, captureTicks, feedTicks;
    private static Vec3 start;
    private static Munaciello monk;
    private static final BlockPos OVEN = new BlockPos(0, 129, 0), PUZZLE = new BlockPos(2, 129, 0);
    public static void register() {
        if (!Boolean.getBoolean("napuli.kitchentest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(KitchenTest::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(KitchenTest::serverTick);
    }
    private static void clientTick(Minecraft mc) {
        if (finished) { mc.options.keyUse.setDown(false); mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); mc.stop(); return; }
        if (mc.player == null) {
            if (mc.gui.screen() instanceof TitleScreen) {
                if (++titleTicks == 1) { mc.getLanguageManager().setSelected("it_it"); mc.reloadResourcePacks(); }
                if (titleTicks == 80) {
                    var settings = new LevelSettings("napuli-kitchen-test", GameType.SURVIVAL,
                            new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("napuli-kitchen-test-" + System.currentTimeMillis(), settings,
                            SelectWorldScreen.TEST_OPTIONS, WorldPresets::createTestWorldDimensions, new TitleScreen());
                }
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
        mc.options.keyUse.setDown(phase == 3 || phase == 4 || phase == 6);
        mc.options.keyUp.setDown(phase == 3);
        mc.options.keySprint.setDown(phase == 3);
        if ((phase == 1 || phase == 7) && clientStep != phase && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            BlockPos pos = phase == 1 ? OVEN : PUZZLE;
            if (!(mc.level.getBlockState(pos).getBlock() instanceof KitchenStationBlock)) return;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            clientStep = phase;
        }
        if (phase == 2 && mc.gui.screen() != null && ++captureTicks >= 30 && clientStep != 2) {
            if (captureTicks == 30) Screenshot.grab(mc.gameDirectory, "kitchen-station.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            if (press(mc, "Prepara")) clientStep = 2;
            else press(mc, "Portafoglio");
        }
        if ((phase == 3 || phase == 4 || phase == 6) && clientStep != phase) {
            String id = phase == 3 ? "pizza_portafoglio" : phase == 4 ? "baba_rum" : "sartu_riso";
            if (mc.player.getMainHandItem().is(NapoliKitchen.item(id))) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); clientStep = phase;
            }
        }
        if (phase >= 8 && phase <= 10 && clientStep != phase) {
            String id = switch (phase) { case 8 -> "farina"; case 9 -> "impasto"; default -> "pizza"; };
            if (press(mc, new ItemStack(NapoliKitchen.item(id)).getHoverName().getString())) clientStep = phase;
        }
        if (phase == 11 && ++feedTicks % 15 == 0 && mc.player.getMainHandItem().is(Items.COOKIE)) {
            var entity = mc.level.getEntity(monkId);
            if (entity != null) mc.gameMode.interact(mc.player, entity, new EntityHitResult(entity, entity.position().add(0, .5, 0)), InteractionHand.MAIN_HAND);
        }
        if (phase == 12 && clientStep != 12 && ++captureTicks >= 30) {
            Screenshot.grab(mc.gameDirectory, "kitchen-foods-and-friend.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            clientStep = 12;
        }
    }
    private static boolean press(Minecraft mc, String text) {
        if (mc.gui.screen() == null) return false;
        var button = descendants(mc.gui.screen()).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                .filter(b -> b.getMessage().getString().contains(text)).findFirst();
        button.ifPresent(b -> b.onPress(new KeyEvent(257, 0, 0)));
        return button.isPresent();
    }
    private static Stream<GuiEventListener> descendants(GuiEventListener widget) {
        return widget instanceof ContainerEventHandler container ? Stream.concat(Stream.of(widget),
                container.children().stream().flatMap(KitchenTest::descendants)) : Stream.of(widget);
    }
    private static void serverTick(MinecraftServer server) {
        if (finished || server.getPlayerList().getPlayers().isEmpty()) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        try {
            if (++elapsed > 2000) throw new IllegalStateException("Timed out in phase " + phase + ", client " + clientStep);
            phaseTicks++;
            switch (phase) {
                case 0 -> {
                    if (player.level().dimension() != NapoliMap.LEVEL) return;
                    var level = server.overworld();
                    for (int x = -8; x <= 8; x++) for (int z = -8; z <= 24; z++) {
                        level.setBlock(new BlockPos(x, 128, z), Blocks.STONE.defaultBlockState(), 3);
                        for (int y = 129; y < 135; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                    player.teleportTo(level, -.5, 129, -1.5, Set.of(), 0, 0, true);
                    level.setBlock(OVEN, NapoliKitchen.BLOCKS.get("forno_legna_portatile").defaultBlockState(), 3);
                    level.setBlock(PUZZLE, NapoliKitchen.BLOCKS.get("enigma_sartu").defaultBlockState(), 3);
                    level.setBlock(new BlockPos(4, 129, 0), NapoliKitchen.BLOCKS.get("banca_frittura").defaultBlockState(), 3);
                    level.setBlock(new BlockPos(6, 129, 0), NapoliKitchen.BLOCKS.get("banca_caffe").defaultBlockState(), 3);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(9, new ItemStack(NapoliKitchen.item("pizza_portafoglio_crudo")));
                    player.getInventory().setItem(10, new ItemStack(Items.CHARCOAL, 2));
                    player.getFoodData().setFoodLevel(10);
                    next(1);
                }
                case 1 -> { if (clientStep == 1 && phaseTicks > 10) next(2); }
                case 2 -> {
                    if (player.getInventory().countItem(NapoliKitchen.item("pizza_portafoglio")) != 1) return;
                    check(player.getInventory().countItem(Items.CHARCOAL) == 1, "Native station consumes one fuel");
                    start = player.position(); next(3);
                }
                case 3 -> {
                    if (clientStep != 3 || phaseTicks < 25) return;
                    check(player.getInventory().countItem(NapoliKitchen.item("pizza_portafoglio")) == 0, "Quick pizza actually consumed");
                    check(player.position().distanceTo(start) > 1.5, "Player eats while running");
                    player.teleportTo(server.overworld(), -.5, 129, -1.5, Set.of(), 0, 0, true);
                    player.getInventory().setItem(0, new ItemStack(NapoliKitchen.item("baba_rum"))); next(4);
                }
                case 4 -> {
                    if (player.getAttachedOrElse(KitchenEffects.BABA, 0) == 0) return;
                    check(player.hasEffect(MobEffects.SPEED) && player.hasEffect(MobEffects.JUMP_BOOST), "Baba gives actual positive effects"); next(5);
                }
                case 5 -> {
                    if (phaseTicks < 320) return;
                    check(!player.hasEffect(MobEffects.SPEED) && player.hasEffect(MobEffects.NAUSEA), "Actual baba rush ends before staggering");
                    player.getInventory().setItem(0, new ItemStack(NapoliKitchen.item("sartu_riso"))); next(6);
                }
                case 6 -> { if (player.getAttachedOrElse(KitchenProgress.PUZZLE_READY, false)) {
                    player.teleportTo(server.overworld(), 2.5, 129, -2, Set.of(), 0, 0, true); next(7);
                } }
                case 7 -> { if (clientStep == 7 && phaseTicks > 10) next(8); }
                case 8, 9 -> { if (clientStep == phase && phaseTicks > 10) next(phase + 1); }
                case 10 -> {
                    if (!player.getAttachedOrElse(KitchenProgress.PUZZLE_CLAIMED, false)) return;
                    check(player.getInventory().countItem(Items.EMERALD) == 3, "Native puzzle gives one reward");
                    player.getInventory().setItem(8, new ItemStack(Items.EMERALD, 3));
                    player.getInventory().setItem(0, new ItemStack(Items.COOKIE, 3));
                    player.teleportTo(server.overworld(), 2.5, 129, -3, Set.of(), 0, 0, true);
                    monk = Napuli.MUNACIELLO.create(player.level(), EntitySpawnReason.EVENT);
                    monk.snapTo(2.5, 129, -1.5, 180, 0); monk.setNoAi(true); player.level().addFreshEntity(monk); monkId = monk.getId(); next(11);
                }
                case 11 -> {
                    if (!monk.isTamed()) return;
                    check(player.getUUID().equals(monk.owner()) && player.getInventory().countItem(Items.COOKIE) == 0, "Three native interactions tame the monk");
                    int slot = 1; for (String id : java.util.List.of("sfogliatella_riccia", "sfogliatella_frolla", "pastiera", "zeppole", "struffoli", "baba_rum", "borsa_fritto"))
                        player.getInventory().setItem(slot++, new ItemStack(NapoliKitchen.item(id)));
                    next(12);
                }
                case 12 -> {
                    if (phaseTicks < 30 || clientStep != 12) return;
                    check(FryBagItem.data(player.getInventory().getItem(7)).getLong("last_tick").isPresent(), "Carried bag regenerates in an unselected inventory slot");
                    Napuli.LOGGER.info("[KITCHENTEST] ALL PASSED: native station checkout, sprint eating, delayed baba effects, sartu puzzle and taming");
                    finished = true;
                }
            }
        } catch (Throwable failure) { Napuli.LOGGER.error("[KITCHENTEST] FAILED in phase " + phase, failure); finished = true; }
    }
    private static void next(int value) { phaseTicks = 0; if (value == 12) captureTicks = 0; phase = value; Napuli.LOGGER.info("[KITCHENTEST] phase {}", value); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
