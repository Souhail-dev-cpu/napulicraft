package dev.napuli;

import java.util.Set;
import dev.napuli.GiorgiaMeloni.Payment;
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
    private static long due, approachDeadline;
    private static int approachCash, approachBlocks;

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

    private static void nextVisit(ServerPlayer player) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
        check(GiorgiaMeloni.summon(player) == 1, "Cannot summon next visit");
        player.getAttached(GiorgiaMeloni.COLLECTOR).relocate(player.blockPosition().offset(0, 0, 2), 180);
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
                    NapoliStreet.credit(p, 100 - NapoliStreet.cash(p));
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
                    check(!p.hasAttached(GiorgiaMeloni.APPROACH_DEADLINE), "Approach timer remained after arrival");
                    check(net.minecraft.network.chat.Component.translatable("napuli.tax.cash", 30).getString()
                            .equals("Pagamento partita iva (30 €)"), "Wrong cash payment label");
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
                    check(GiorgiaMeloni.answer(p, "00000000-0000-0000-0000-000000000000", Payment.ITEMS) == 0, "Forged token accepted");
                    check(GiorgiaMeloni.answer(p, paidToken, Payment.ITEMS) == 1, "Payment rejected");
                    check(p.getInventory().getItem(0).getCount() == 49, "Tax was not exactly fifteen blocks");
                    check(p.getInventory().getItem(1).getCount() == 20, "Diamonds taken before blocks");
                    check(p.getInventory().getItem(2).is(Napuli.KNIFE), "Knife taxed");
                    check(p.getInventory().getItem(3).getCount() == 16, "Ciabatta weapon taxed");
                    check(p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "Armor taxed");
                    check(p.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.DIAMOND_PICKAXE), "Tool taxed");
                    check(p.getAttached(GiorgiaMeloni.NEXT_VISIT) == server.overworld().getOverworldClockTime() + 72000, "Next visit not scheduled");
                    check(GiorgiaMeloni.answer(p, paidToken, Payment.ITEMS) == 0, "Replayed payment accepted");
                    check(p.getInventory().getItem(0).getCount() == 49, "Duplicate tax collected");
                    check(NapoliStreet.cash(p) == 100, "Item payment also charged money");
                    Napuli.LOGGER.info("[TAXTEST] Approach, dialog, payment cap, protected equipment and token replay passed");
                    nextVisit(p);
                    next(5);
                }
                case 5 -> {
                    var session = p.getAttached(GiorgiaMeloni.SESSION);
                    if (session == null) { check(elapsed < 100, "Cash dialog not opened"); return; }
                    check(session.cashTax() == 30, "Wrong cash quote for 100 euros");
                    NapoliStreet.credit(p, 1);
                    check(GiorgiaMeloni.answer(p, session.token(), Payment.CASH) == 0, "Changed balance charged without confirmation");
                    check(NapoliStreet.cash(p) == 101, "Requote withdrew money");
                    session = p.getAttached(GiorgiaMeloni.SESSION);
                    check(session.cashTax() == 31, "Updated quote did not round up");
                    String cashToken = session.token();
                    server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "napulitassa " + cashToken + " soldi");
                    check(NapoliStreet.cash(p) == 70, "Cash payment did not subtract 31 euros from 101");
                    check(p.getInventory().getItem(0).getCount() == 49 && p.getInventory().getItem(1).getCount() == 20,
                            "Cash payment also took inventory items");
                    check(GiorgiaMeloni.answer(p, cashToken, Payment.CASH) == 0 && NapoliStreet.cash(p) == 70,
                            "Replayed cash payment accepted");
                    check(p.getAttached(GiorgiaMeloni.NEXT_VISIT) == server.overworld().getOverworldClockTime() + 72000,
                            "Cash payment did not schedule next visit");
                    Napuli.LOGGER.info("[TAXTEST] Cash command passed: 100 -> quote 30, changed balance 101 -> quote 31 -> remaining 70, no items taken");
                    NapoliStreet.credit(p, -NapoliStreet.cash(p));
                    nextVisit(p);
                    next(6);
                }
                case 6 -> {
                    var session = p.getAttached(GiorgiaMeloni.SESSION);
                    if (session == null) { check(elapsed < 100, "Zero-balance dialog not opened"); return; }
                    check(GiorgiaMeloni.answer(p, session.token(), Payment.CASH) == 0, "Zero money bypassed item tax");
                    check(p.getInventory().getItem(0).getCount() == 49 && NapoliStreet.cash(p) == 0, "Zero-balance attempt charged payment");
                    p.getInventory().setItem(0, ItemStack.EMPTY);
                    p.getInventory().setItem(1, ItemStack.EMPTY);
                    NapoliStreet.credit(p, 7);
                    GiorgiaMeloni.open(p, p.getAttached(GiorgiaMeloni.COLLECTOR));
                    session = p.getAttached(GiorgiaMeloni.SESSION);
                    check(GiorgiaMeloni.answer(p, session.token(), Payment.ITEMS) == 0 && NapoliStreet.cash(p) == 7,
                            "Missing materials bypassed cash tax");
                    NapoliStreet.credit(p, -7);
                    GiorgiaMeloni.open(p, p.getAttached(GiorgiaMeloni.COLLECTOR));
                    session = p.getAttached(GiorgiaMeloni.SESSION);
                    check(GiorgiaMeloni.answer(p, session.token(), Payment.ITEMS) == 1 && !Carabinieri.isJailed(p),
                            "No materials and no money did not grant exemption");
                    Napuli.LOGGER.info("[TAXTEST] Zero balance, unavailable payment methods and genuine exemption passed");
                    p.getInventory().setItem(0, new ItemStack(Items.DIRT, 49));
                    p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 20));
                    NapoliStreet.credit(p, 100);
                    nextVisit(p);
                    next(7);
                }
                case 7 -> {
                    var session = p.getAttached(GiorgiaMeloni.SESSION);
                    if (session == null) { check(elapsed < 100, "Refusal dialog not opened"); return; }
                    check(GiorgiaMeloni.answer(p, session.token(), Payment.REFUSE) == 1, "Refusal rejected");
                    check(Carabinieri.isJailed(p), "Refusal did not cause prison");
                    check(p.gameMode() == GameType.ADVENTURE && p.level().dimension() == NapoliMap.LEVEL, "Wrong prison state");
                    check(p.getInventory().getItem(2).is(Napuli.KNIFE), "Tax imprisonment confiscated weapon");
                    check(p.getInventory().getItem(0).getCount() == 49, "Refusal charged materials");
                    check(NapoliStreet.cash(p) == 100, "Refusal charged money");
                    p.teleportTo(server.overworld(), 0.5, 100, 0.5, Set.of(), 0, 0, true);
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(null));
                    next(8);
                }
                case 8 -> {
                    if (elapsed < 40) return;
                    check(p.level().dimension() == NapoliMap.LEVEL && Carabinieri.isJailed(p), "Dimension escape bypassed prison");
                    capture = 2;
                    captured = false;
                    next(9);
                }
                case 9 -> {
                    if (!captured || elapsed < 40) return;
                    run(server, "tick rate 200");
                    next(10);
                }
                case 10 -> {
                    if (Carabinieri.isJailed(p)) { check(elapsed < 1900, "Prison sentence did not expire"); return; }
                    check(p.gameMode() == GameType.SURVIVAL, "Original game mode not restored");
                    check(p.getInventory().getItem(2).is(Napuli.KNIFE), "Equipment lost on release");
                    check(p.blockPosition().distSqr(NapoliDistrict.PRISON_GATE) < 9, "Not released at prison gate");
                    run(server, "tick rate 20");
                    Napuli.LOGGER.info("[TAXTEST] Refusal, prison, escape prevention and automatic release passed");
                    p.teleportTo(server.overworld(), 0.5, 100, 0.5, Set.of(), 0, 0, true);
                    approachCash = NapoliStreet.cash(p);
                    approachBlocks = p.getInventory().getItem(0).getCount();
                    // An enclosed player must not prevent a safe collector spawn or evade the deadline.
                    for (BlockPos pos : BlockPos.betweenClosed(-2, 100, -2, 2, 104, 2))
                        if (Math.abs(pos.getX()) == 2 || Math.abs(pos.getZ()) == 2)
                            server.overworld().setBlock(pos, Blocks.GLASS.defaultBlockState(), 2);
                    check(GiorgiaMeloni.summon(p) == 1, "Barricade prevented collector spawn");
                    collector = p.getAttached(GiorgiaMeloni.COLLECTOR);
                    var path = collector.getNavigation().createPath(p, 0);
                    check(path == null || !path.canReach(), "Barricade did not block the approach");
                    collector.setNoAi(true);
                    collector.getNavigation().stop();
                    approachDeadline = p.getAttached(GiorgiaMeloni.APPROACH_DEADLINE);
                    check(approachDeadline - server.overworld().getGameTime() == 2400, "Approach limit is not two minutes");
                    run(server, "time add 24000");
                    check(p.getAttached(GiorgiaMeloni.APPROACH_DEADLINE) == approachDeadline, "Day-clock jump changed deadline");
                    run(server, "tick rate 200");
                    next(11);
                }
                case 11 -> {
                    check(!Carabinieri.isJailed(p), "Approach timeout arrested too early");
                    if (elapsed < 1000) return;
                    collector.discard();
                    next(12);
                }
                case 12 -> {
                    NapoliResident replacement = p.getAttached(GiorgiaMeloni.COLLECTOR);
                    if (replacement == null || replacement == collector || !replacement.isAlive()) {
                        check(elapsed < 200, "Lost collector was not replaced");
                        return;
                    }
                    collector = replacement;
                    collector.setNoAi(true);
                    collector.getNavigation().stop();
                    check(p.getAttached(GiorgiaMeloni.APPROACH_DEADLINE) == approachDeadline, "Collector respawn reset the deadline");
                    Napuli.LOGGER.info("[TAXTEST] Blocked approach: safe spawn, two-minute deadline, day-clock independence and respawn continuity passed");
                    next(13);
                }
                case 13 -> {
                    long gameTime = server.overworld().getGameTime();
                    if (!Carabinieri.isJailed(p)) {
                        check(gameTime <= approachDeadline + 20, "No arrest after two minutes");
                        return;
                    }
                    check(gameTime >= approachDeadline, "Arrest before the two-minute deadline");
                    check(collector.isRemoved(), "Collector did not disappear on timeout");
                    check(!p.hasAttached(GiorgiaMeloni.COLLECTOR) && !p.hasAttached(GiorgiaMeloni.SESSION)
                            && !p.hasAttached(GiorgiaMeloni.APPROACH_DEADLINE), "Timeout did not clear visit state");
                    check(p.level().dimension() == NapoliMap.LEVEL && p.gameMode() == GameType.ADVENTURE,
                            "Timeout did not send player to prison");
                    check(p.getInventory().getItem(0).getCount() == approachBlocks && NapoliStreet.cash(p) == approachCash,
                            "Timeout changed inventory/cash: " + approachBlocks + "/" + approachCash + " -> "
                                    + p.getInventory().getItem(0).getCount() + "/" + NapoliStreet.cash(p));
                    check(p.getAttached(GiorgiaMeloni.NEXT_VISIT) == server.overworld().getOverworldClockTime() + 72000,
                            "Timeout did not schedule next visit");
                    check(Carabinieri.release(p) && p.gameMode() == GameType.SURVIVAL, "Cannot release timed-out player");
                    run(server, "tick rate 20");
                    Napuli.LOGGER.info("[TAXTEST] ALL PASSED: payments, partita iva label, refusal, approach timeout, prison and release");
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
