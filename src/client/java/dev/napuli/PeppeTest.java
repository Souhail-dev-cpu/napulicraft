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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** Opt-in real-client test of the merchant, actual checkout and timed item use in both hands. */
public final class PeppeTest {
    private static int titleTicks, elapsed, clientStep = -1, buyTick = -1, lastCapture, captureTicks;
    private static volatile int phase, capture, buyClicks;
    private static volatile boolean captured, finished;
    private static boolean started;
    private static NapoliResident merchant;
    private static long useStart = -1, cheeseEatenAt, cheeseCrashAt;
    private static float initialHealth;

    private PeppeTest() {}

    public static void register() {
        if (!Boolean.getBoolean("napuli.peppetest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(PeppeTest::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(PeppeTest::serverTick);
    }

    private static void clientTick(Minecraft mc) {
        if (!started && mc.gui.screen() instanceof TitleScreen) {
            if (++titleTicks == 1) {
                mc.getLanguageManager().setSelected("it_it");
                mc.reloadResourcePacks();
            }
            if (titleTicks == 80) {
                started = true;
                var settings = new LevelSettings("napuli-peppe-test", GameType.SURVIVAL,
                        new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("napuli-peppe-test-" + System.currentTimeMillis(), settings,
                        SelectWorldScreen.TEST_OPTIONS, WorldPresets::createTestWorldDimensions, new TitleScreen());
            }
            return;
        }
        if (mc.player == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
        if (capture > 0 && !captured) {
            if (lastCapture != capture) { lastCapture = capture; captureTicks = 0; }
            if (++captureTicks >= 30) {
                Screenshot.grab(mc.gameDirectory, "peppe-" + capture + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                        message -> Napuli.LOGGER.info("[PEPPETEST] {}", message.getString()));
                captured = true;
            }
        }
        mc.options.keyUse.setDown(phase == 3 || phase == 5 || phase == 8 || phase == 12);
        if ((phase == 2 || phase == 10 || phase == 11) && clientStep != phase && captured) {
            var screen = mc.gui.screen();
            var button = screen == null ? java.util.Optional.<AbstractButton>empty()
                    : descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                            .filter(b -> b.getMessage().getString().contains(phase == 11 ? "Formaggio odor piede" : "Calzini sporchi")).findFirst();
            if (button.isPresent()) {
                button.get().onPress(new KeyEvent(257, 0, 0));
                clientStep = phase;
                buyClicks++;
            }
        }
        if ((phase == 3 || phase == 5 || phase == 8 || phase == 12) && clientStep != phase) {
            mc.gameMode.useItem(mc.player, phase == 8 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            clientStep = phase;
        }
        if (finished) { mc.options.keyUse.setDown(false); mc.stop(); }
    }

    private static Stream<GuiEventListener> descendants(GuiEventListener widget) {
        return widget instanceof ContainerEventHandler container
                ? Stream.concat(Stream.of(widget), container.children().stream().flatMap(PeppeTest::descendants))
                : Stream.of(widget);
    }

    private static void next(int value) { elapsed = 0; phase = value; }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int socks(ServerPlayer player) {
        int count = player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(Napuli.CALZINI_SPORCHI))
                .mapToInt(ItemStack::getCount).sum();
        return count + (player.getOffhandItem().is(Napuli.CALZINI_SPORCHI) ? player.getOffhandItem().getCount() : 0);
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
                    server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "napuli peppe");
                    check(p.blockPosition().distSqr(PeppeFetish.STALL) < 25, "Shortcut did not reach merchant");
                    var people = p.level().getEntitiesOfClass(NapoliResident.class, p.getBoundingBox().inflate(12),
                            npc -> npc.role() == NapoliResident.Role.PEPPE_FETISH);
                    check(people.size() == 1, "Merchant missing or duplicated");
                    merchant = people.getFirst();
                    check(p.level().noCollision(merchant), "Merchant spawned inside blocks");
                    merchant.setNoAi(true);
                    p.getInventory().clearContent();
                    p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Napuli.CORNICELLO));
                    p.setHealth(10);
                    p.getFoodData().setFoodLevel(10);
                    initialHealth = p.getHealth();
                    NapoliStreet.credit(p, 20 - NapoliStreet.cash(p));
                    capture = 1; captured = false;
                    next(1);
                }
                case 1 -> {
                    if (!captured || elapsed < 40) return;
                    merchant.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    capture = 2; captured = false;
                    next(2);
                }
                case 2 -> {
                    if (socks(p) == 0) { check(elapsed < 160, "Could not buy socks through shop dialog"); return; }
                    check(socks(p) == 1 && NapoliStreet.cash(p) == 14, "Incorrect purchase quantity or price");
                    check(p.getMainHandItem().is(Napuli.CALZINI_SPORCHI), "Purchased socks not selected");
                    check(!TaxPayment.taxable(p.getMainHandItem()), "Reusable socks treated as taxable materials");
                    Napuli.LOGGER.info("[PEPPETEST] Merchant, collision-free spawn, actual shop button and 6-euro purchase passed");
                    p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ(), Set.of(), 90, -25, true);
                    next(3);
                }
                case 3 -> {
                    if (!p.isUsingItem()) { check(elapsed < 100, "Sniff animation did not start"); return; }
                    if (useStart < 0) useStart = p.level().getGameTime();
                    if (p.level().getGameTime() - useStart < 8) return;
                    p.stopUsingItem();
                    next(4);
                }
                case 4 -> {
                    if (elapsed < 15) return;
                    check(!p.hasEffect(MobEffects.NAUSEA) && !p.hasEffect(MobEffects.REGENERATION), "Interrupted sniff applied effects");
                    check(socks(p) == 1, "Interrupted sniff consumed socks");
                    next(5);
                }
                case 5 -> {
                    if (!p.hasEffect(MobEffects.NAUSEA) || !p.hasEffect(MobEffects.REGENERATION)) {
                        check(elapsed < 160, "Completed sniff did not apply both effects"); return;
                    }
                    check(p.getEffect(MobEffects.NAUSEA).getDuration() >= CalziniSporchiItem.NAUSEA_TICKS - 3, "Wrong nausea duration");
                    check(p.getEffect(MobEffects.REGENERATION).getDuration() >= CalziniSporchiItem.REGEN_TICKS - 3, "Wrong regeneration duration");
                    check(p.getEffect(MobEffects.REGENERATION).getAmplifier() == 0, "Wrong regeneration amplifier");
                    check(socks(p) == 1 && p.getCooldowns().isOnCooldown(p.getMainHandItem()), "Reuse or cooldown failed");
                    check(Napuli.CALZINI_SPORCHI.use(p.level(), p, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS,
                            "Cooldown did not block another use");
                    Napuli.LOGGER.info("[PEPPETEST] Real timed sniff, interruption, nausea, regeneration, reusable item and cooldown passed");
                    next(6);
                }
                case 6 -> {
                    if (elapsed < 110) return;
                    check(p.getHealth() > initialHealth, "Regeneration did not heal hearts");
                    check(p.getFoodData().getFoodLevel() == 10 && socks(p) == 1, "Sniff changed hunger or consumed socks");
                    ItemStack pair = p.getMainHandItem(), charm = p.getOffhandItem();
                    p.setItemInHand(InteractionHand.MAIN_HAND, charm);
                    p.setItemInHand(InteractionHand.OFF_HAND, pair);
                    p.inventoryMenu.broadcastChanges();
                    next(7);
                }
                case 7 -> { if (elapsed >= 30) next(8); }
                case 8 -> {
                    if (p.getEffect(MobEffects.NAUSEA) == null
                            || p.getEffect(MobEffects.NAUSEA).getDuration() < CalziniSporchiItem.NAUSEA_TICKS - 3) {
                        check(elapsed < 160, "Offhand sniff did not refresh effects"); return;
                    }
                    check(p.getOffhandItem().is(Napuli.CALZINI_SPORCHI) && socks(p) == 1, "Offhand use consumed socks");
                    capture = 3; captured = false;
                    next(9);
                }
                case 9 -> {
                    if (!captured || elapsed < 30) return;
                    NapoliStreet.credit(p, -NapoliStreet.cash(p));
                    p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ(), Set.of(), 180, 0, true);
                    merchant.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    next(10);
                }
                case 10 -> {
                    if (buyClicks < 2) { check(elapsed < 160, "Second shop button not pressed"); return; }
                    if (buyTick < 0) { buyTick = server.getTickCount(); return; }
                    if (server.getTickCount() - buyTick < 20) return;
                    check(NapoliStreet.cash(p) == 0 && socks(p) == 1, "Insufficient funds gave free socks or negative money");
                    Napuli.LOGGER.info("[PEPPETEST] Socks: purchase, insufficient funds, sniff, nausea, healing, cooldown and offhand reuse passed");
                    ItemStack pair = p.getOffhandItem(), charm = p.getMainHandItem();
                    p.getInventory().setItem(1, pair);
                    p.setItemInHand(InteractionHand.OFF_HAND, charm);
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    p.getFoodData().setFoodLevel(20);
                    NapoliStreet.credit(p, 30);
                    check(!p.hasAttached(FormaggioPiedeItem.AFTERMATH), "Cheese crash scheduled before eating");
                    merchant.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    capture = 4; captured = false;
                    next(11);
                }
                case 11 -> {
                    if (!p.getMainHandItem().is(Napuli.FORMAGGIO_PIEDE)) {
                        check(elapsed < 160, "Could not buy cheese through shop dialog"); return;
                    }
                    check(p.getMainHandItem().getCount() == 1 && NapoliStreet.cash(p) == 20 && socks(p) == 1,
                            "Cheese purchase had wrong cost or altered socks");
                    check(p.getFoodData().getFoodLevel() == 20, "Full-hunger cheese test setup failed");
                    p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ(), Set.of(), 90, -25, true);
                    next(12);
                }
                case 12 -> {
                    if (!p.hasEffect(MobEffects.SPEED) || !p.hasEffect(MobEffects.STRENGTH)) {
                        check(elapsed < 160, "Cheese could not be eaten at full hunger");
                        check(!p.hasAttached(FormaggioPiedeItem.AFTERMATH), "Cheese effects started before consumption finished");
                        return;
                    }
                    cheeseEatenAt = server.overworld().getGameTime();
                    check(p.getInventory().getNonEquipmentItems().stream().noneMatch(s -> s.is(Napuli.FORMAGGIO_PIEDE)),
                            "Cheese portion was not consumed");
                    check(p.getEffect(MobEffects.SPEED).getDuration() >= 197 && p.getEffect(MobEffects.SPEED).getAmplifier() == 1,
                            "Wrong speed duration or level");
                    check(p.getEffect(MobEffects.STRENGTH).getDuration() >= 57 && p.getEffect(MobEffects.STRENGTH).getAmplifier() == 4,
                            "Wrong strength duration or level");
                    check(!p.hasEffect(MobEffects.SLOWNESS) && !p.hasEffect(MobEffects.WEAKNESS), "Cheese penalties started immediately");
                    var pending = p.getAttached(FormaggioPiedeItem.AFTERMATH);
                    var saved = FormaggioPiedeItem.Aftermath.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, pending).getOrThrow();
                    check(FormaggioPiedeItem.Aftermath.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow().equals(pending),
                            "Cheese countdown did not survive codec round trip");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time add 24000");
                    Napuli.LOGGER.info("[PEPPETEST] Cheese shop purchase, full-hunger consumption, Speed II 10 s, Strength V 3 s and saved countdown passed");
                    next(13);
                }
                case 13 -> {
                    long sinceEating = server.overworld().getGameTime() - cheeseEatenAt;
                    if (sinceEating >= 65 && sinceEating < 190)
                        check(!p.hasEffect(MobEffects.STRENGTH) && p.hasEffect(MobEffects.SPEED), "Strength did not end before speed");
                    if (!p.hasEffect(MobEffects.SLOWNESS) || !p.hasEffect(MobEffects.WEAKNESS)) {
                        check(sinceEating < 205, "Cheese crash did not start after ten seconds");
                        if (sinceEating < 200) check(!p.hasEffect(MobEffects.SLOWNESS) && !p.hasEffect(MobEffects.WEAKNESS),
                                "Cheese crash started before all bonuses ended");
                        return;
                    }
                    check(sinceEating >= 200, "Cheese penalties applied too early");
                    check(!p.hasEffect(MobEffects.SPEED) && !p.hasEffect(MobEffects.STRENGTH), "Cheese bonuses overlapped the crash");
                    check(p.getEffect(MobEffects.SLOWNESS).getDuration() >= 397 && p.getEffect(MobEffects.SLOWNESS).getAmplifier() == 0,
                            "Wrong slowness duration or level");
                    check(p.getEffect(MobEffects.WEAKNESS).getDuration() >= 117 && p.getEffect(MobEffects.WEAKNESS).getAmplifier() == 0,
                            "Wrong weakness duration or level");
                    cheeseCrashAt = server.overworld().getGameTime();
                    capture = 5; captured = false;
                    Napuli.LOGGER.info("[PEPPETEST] Cheese crash starts after both bonuses: Slowness I 20 s and Weakness I 6 s");
                    next(14);
                }
                case 14 -> {
                    if (server.overworld().getGameTime() - cheeseCrashAt < 125) return;
                    check(!p.hasEffect(MobEffects.WEAKNESS) && p.hasEffect(MobEffects.SLOWNESS), "Weakness did not expire after six seconds");
                    next(15);
                }
                case 15 -> {
                    if (server.overworld().getGameTime() - cheeseCrashAt < 405) return;
                    check(!p.hasEffect(MobEffects.WEAKNESS) && !p.hasEffect(MobEffects.SLOWNESS), "Cheese penalties did not expire");
                    check(!p.hasAttached(FormaggioPiedeItem.AFTERMATH), "Cheese left a repeating crash scheduled");
                    check(NapoliStreet.cash(p) == 20 && socks(p) == 1, "Cheese aftermath charged money or removed socks");
                    Napuli.LOGGER.info("[PEPPETEST] ALL PASSED: socks and cheese shop, consumption, timed bonuses and delayed penalties");
                    finished = true;
                }
                default -> throw new AssertionError("Unexpected phase");
            }
        } catch (Throwable failure) {
            Napuli.LOGGER.error("[PEPPETEST] FAILED at phase {}", phase, failure);
            finished = true;
        }
    }
}
