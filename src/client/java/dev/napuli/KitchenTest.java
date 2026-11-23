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
    private static final BlockPos OVEN = new BlockPos(0, 129, 0), PUZZLE = new BlockPos(2, 129, 0), TABLE = new BlockPos(-2, 129, 0);
    private static final BlockPos FRYER = new BlockPos(4, 129, 0), COFFEE = new BlockPos(6, 129, 0);
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
            BlockPos pos = switch (phase) { case 1 -> OVEN; case 7 -> PUZZLE; case 16 -> FRYER; case 18 -> COFFEE; default -> TABLE; };
            if (!(mc.level.getBlockState(pos).getBlock() instanceof KitchenStationBlock)) return;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            clientStep = phase;
        }
        if (phase == 2 && mc.player.containerMenu instanceof net.minecraft.world.inventory.FurnaceMenu furnace) {
            if (clientStep != 2) {
                click(mc, 3, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
                click(mc, 4, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
                clientStep = 2;
            } else if (furnace.getSlot(2).hasItem()) {
                Screenshot.grab(mc.gameDirectory, "kitchen-station.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
                click(mc, 2, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
                click(mc, 1, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
                mc.player.closeContainer();
            }
        }
        if (phase == 17 && mc.player.containerMenu instanceof KitchenMenus.Heat fryer && !fryer.woodOven()) {
            if (clientStep != 17) {
                click(mc, 3, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
                click(mc, 4, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
                clientStep = 17;
            } else if (fryer.getSlot(2).hasItem()) {
                Screenshot.grab(mc.gameDirectory, "kitchen-fryer.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
                click(mc, 2, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
                mc.player.closeContainer();
            }
        }
        if (phase == 19 && mc.player.containerMenu instanceof KitchenMenus.Grid coffee && coffee.coffee() && clientStep != 19) {
            Screenshot.grab(mc.gameDirectory, "kitchen-coffee.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            clientStep = 19;
        }
        if (phase == 14 && mc.player.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu grid) {
            if (clientStep != 14) {
                for (int i = 0; i < 4; i++) {
                    click(mc, 10 + i, net.minecraft.world.inventory.ContainerInput.PICKUP);
                    click(mc, 1 + i, net.minecraft.world.inventory.ContainerInput.PICKUP);
                }
                clientStep = 14;
            } else if (grid.getSlot(0).hasItem()) {
                Screenshot.grab(mc.gameDirectory, "kitchen-grid.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
                click(mc, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
                mc.player.closeContainer();
            }
        }
        if (phase == 15 && mc.level.dimension() == NapoliMap.LEVEL && ++captureTicks > 35 && clientStep != 15) {
            Screenshot.grab(mc.gameDirectory, "fishing-harbour.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            clientStep = 15;
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
    private static void click(Minecraft mc, int slot, net.minecraft.world.inventory.ContainerInput action) {
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 0, action, mc.player);
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
                    level.setBlock(TABLE, NapoliKitchen.BLOCKS.get("tavolo_pizzaiolo").defaultBlockState(), 3);
                    level.setBlock(OVEN, NapoliKitchen.BLOCKS.get("forno_legna_portatile").defaultBlockState(), 3);
                    level.setBlock(PUZZLE, NapoliKitchen.BLOCKS.get("enigma_sartu").defaultBlockState(), 3);
                    level.setBlock(new BlockPos(4, 129, 0), NapoliKitchen.BLOCKS.get("banca_frittura").defaultBlockState(), 3);
                    level.setBlock(new BlockPos(6, 129, 0), NapoliKitchen.BLOCKS.get("banca_caffe").defaultBlockState(), 3);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(9, new ItemStack(NapoliKitchen.item("pizza_portafoglio_crudo")));
                    player.getInventory().setItem(10, new ItemStack(Items.OAK_LOG, 2));
                    player.getFoodData().setFoodLevel(10);
                    next(1);
                }
                case 1 -> { if (clientStep == 1 && phaseTicks > 10) next(2); }
                case 2 -> {
                    if (player.getInventory().countItem(NapoliKitchen.item("pizza_portafoglio")) != 1) return;
                    check(player.getInventory().countItem(Items.OAK_LOG) == 1, "Native station consumes one fuel");
                    for (int i = 0; i < 36; i++) {
                        if (!player.getInventory().getItem(i).is(NapoliKitchen.item("pizza_portafoglio"))) continue;
                        var pizza = player.getInventory().removeItem(i, 1);
                        player.setItemInHand(InteractionHand.MAIN_HAND, pizza);
                        break;
                    }
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
                    player.teleportTo(server.overworld(), -1.5, 129, -1.5, Set.of(), 0, 0, true);
                    player.getInventory().setItem(9, new ItemStack(NapoliKitchen.item("impasto")));
                    player.getInventory().setItem(10, new ItemStack(NapoliKitchen.item("pomodoro")));
                    player.getInventory().setItem(11, new ItemStack(NapoliKitchen.item("mozzarella")));
                    player.getInventory().setItem(12, new ItemStack(Items.PAPER));
                    next(13);
                }
                case 13 -> { if (phaseTicks > 15) { openStation(player, TABLE); next(14); } }
                case 14 -> {
                    if (player.getInventory().countItem(NapoliKitchen.item("pizza_portafoglio_crudo")) != 1) return;
                    var city = server.getLevel(NapoliMap.LEVEL);
                    player.teleportTo(city, -23.7, 64, 66, Set.of(), 8, 13, true);
                    next(15);
                }
                case 15 -> {
                    if (clientStep != 15) return;
                    var city = player.level();
                    var boats = city.getEntitiesOfClass(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class,
                            new net.minecraft.world.phys.AABB(-34, 59, 62, -14, 68, 84));
                    check(boats.size() >= 2, "Fishing harbour contains usable boats");
                    check(player.startRiding(boats.getFirst(), true, true), "Player boards a fishing boat");
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                    Items.FISHING_ROD.use(city, player, InteractionHand.MAIN_HAND);
                    check(player.fishing != null, "Player casts from the boat");
                    player.stopRiding();
                    ItemStack pistol = new ItemStack(Napuli.PISTOL);
                    player.setItemInHand(InteractionHand.MAIN_HAND, pistol);
                    Napuli.PISTOL.use(city, player, InteractionHand.MAIN_HAND);
                    Napuli.PISTOL.use(city, player, InteractionHand.MAIN_HAND);
                    check(pistol.getDamageValue() == 1, "Arcade weapon fires once during its cooldown");
                    player.teleportTo(server.overworld(), 4.5, 129, -1.5, Set.of(), 0, 0, true);
                    player.getInventory().setItem(9, new ItemStack(NapoliKitchen.item("chiacchiere_crudo")));
                    player.getInventory().setItem(10, new ItemStack(NapoliKitchen.item("olio")));
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    next(16);
                }
                case 16 -> { if (phaseTicks > 15) { openStation(player, FRYER); next(17); } }
                case 17 -> {
                    if (player.getInventory().countItem(NapoliKitchen.item("chiacchiere")) != 1) return;
                    player.teleportTo(server.overworld(), 6.5, 129, -1.5, Set.of(), 0, 0, true);
                    next(18);
                }
                case 18 -> { if (phaseTicks > 15) { openStation(player, COFFEE); next(19); } }
                case 19 -> {
                    if (clientStep != 19 || phaseTicks < 10) return;
                    Napuli.LOGGER.info("[KITCHENTEST] ALL PASSED: furnace slots, crafting grid, fishing boats, arcade effects and kitchen progression");
                    finished = true;
                }
            }
        } catch (Throwable failure) { Napuli.LOGGER.error("[KITCHENTEST] FAILED in phase " + phase, failure); finished = true; }
    }
    private static void openStation(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        var state = player.level().getBlockState(pos);
        ((KitchenStationBlock) state.getBlock()).useWithoutItem(state, player.level(), pos, player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    private static void next(int value) { phaseTicks = 0; if (value == 12 || value == 15) captureTicks = 0; phase = value; Napuli.LOGGER.info("[KITCHENTEST] phase {}", value); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
