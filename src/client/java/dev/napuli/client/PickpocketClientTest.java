package dev.napuli.client;

import static dev.napuli.CityText.t;
import dev.napuli.*;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;

/** Actual entity interactions, server packets and clickable item icons in a disposable client. */
final class PickpocketClientTest {
    private static int titles, total, phase = -2, age, last = -3, expected;
    private static boolean preparing;
    private static volatile boolean done, failed;

    static void register() {
        if (!Boolean.getBoolean("napuli.pickpockettest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (done || failed) { mc.options.keyShift.setDown(false); mc.stop(); return; }
            try {
                if (++total > 6000) throw new AssertionError("Pickpocket client timed out at phase " + phase);
                if (mc.player == null) {
                    if (mc.gui.screen() instanceof TitleScreen && ++titles == 40) {
                        var settings = new LevelSettings("Furto di cinque oggetti", GameType.CREATIVE,
                                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                        mc.createWorldOpenFlows().createFreshLevel("napoli-pockets-" + System.currentTimeMillis(), settings,
                                SelectWorldScreen.TEST_OPTIONS, WorldPresets::createTestWorldDimensions, new TitleScreen());
                    }
                    return;
                }
                mc.options.pauseOnLostFocus = false;
                if (!preparing) {
                    if (mc.player.level().dimension() != NapoliMap.LEVEL) return;
                    preparing = true; mc.options.renderDistance().set(4); mc.options.simulationDistance().set(5);
                    server(mc, PickpocketClientFixture::prepare);
                }
                if (!PickpocketClientFixture.ready || mc.player.level().dimension() != Level.OVERWORLD) return;
                if (last != phase) { last = phase; age = 0; Napuli.LOGGER.info("[PICKPOCKETTEST] phase {}", phase); }
                age++;
                mc.options.keyShift.setDown(phase == 0 || phase == 3 || phase == 12);
                if (phase == -2 && age > 30 && mc.gui.screen() == null && interact(mc, PickpocketClientFixture.npcId)) phase = -1;
                else if (phase == -1 && age > 10 && mc.gui.screen() instanceof DialogScreen<?>) {
                    mc.gui.setScreen(null); phase = 0;
                } else if (phase == 0 && age > 30 && mc.gui.screen() == null && interact(mc, PickpocketClientFixture.npcId)) phase = 1;
                else if (phase == 1 && age > 10 && mc.gui.screen() instanceof PickpocketScreen screen) {
                    check(screen.snapshot().entries().size() == 15 && screen.snapshot().entries().stream().mapToInt(PickpocketView.Entry::available).sum() == 5,
                            "Shift-right-click shows all NPC pockets and equipment with five random permitted units");
                    int w = screen.width, h = screen.height; InterfaceClientChecks.layouts(screen); screen.init(w, h);
                    shot(mc, "furto-inventario-npc"); expected = 1; steal(screen); phase = 2;
                } else if (phase == 2 && age > 4 && mc.gui.screen() instanceof PickpocketScreen screen && screen.snapshot().stolen() == expected) {
                    if (expected < 5) { expected++; steal(screen); age = 0; }
                    else {
                        check(screen.snapshot().entries().stream().noneMatch(e -> e.available() > 0), "All remaining NPC items are view-only after five units");
                        shot(mc, "furto-limite-giornaliero"); server(mc, PickpocketClientFixture::npcLimit); screen.onClose(); phase = 3;
                    }
                } else if (phase == 3 && age > 10 && interact(mc, PickpocketClientFixture.npcId)) phase = 4;
                else if (phase == 4 && age > 10 && mc.gui.screen() instanceof PickpocketScreen screen) {
                    check(screen.snapshot().stolen() == 5, "Reopening does not reset the daily allowance");
                    press(screen, t("pick.talk").getString()); phase = 5;
                } else if (phase == 5 && age > 10 && mc.gui.screen() instanceof DialogScreen<?>) {
                    mc.gui.setScreen(null); server(mc, PickpocketClientFixture::prepareVictim); phase = 6;
                } else if (phase == 6 && age > 30 && interact(mc, PickpocketClientFixture.victimId)) phase = 11;
                else if (phase == 11 && age > 20) {
                    check(mc.gui.screen() == null, "A normal right-click on a player leaves their inventory closed"); phase = 12;
                } else if (phase == 12 && age > 10 && interact(mc, PickpocketClientFixture.victimId)) phase = 7;
                else if (phase == 7 && age > 10 && mc.gui.screen() instanceof PickpocketScreen screen) {
                    check(screen.snapshot().entries().size() == 41 && !screen.snapshot().canTalk(), "Shift-right-click shows a player's whole inventory, including off hand and armour once each");
                    int w = screen.width, h = screen.height;
                    InterfaceClientChecks.layouts(screen); screen.init(320, 240);
                    screen.keyPressed(new KeyEvent(267, 0, 0)); InterfaceClientChecks.bounds(screen);
                    screen.keyPressed(new KeyEvent(266, 0, 0)); screen.init(w, h);
                    shot(mc, "furto-inventario-giocatore"); expected = 1; steal(screen); phase = 8;
                } else if (phase == 8 && age > 4 && mc.gui.screen() instanceof PickpocketScreen screen && screen.snapshot().stolen() == expected) {
                    if (expected < 5) { expected++; steal(screen); age = 0; }
                    else { server(mc, PickpocketClientFixture::playerLimit); phase = 9; }
                } else if (phase == 9 && age > 30 && mc.gui.screen() == null) {
                    server(mc, p -> {
                        PickpocketClientFixture.cleanup(p);
                        Napuli.LOGGER.info("[PICKPOCKETTEST] ALL PASSED: NPC right-click dialogue, Shift-right-click theft on NPCs and players, player plain-click leaves inventory closed, five-unit limit, no reroll, saved inventory, talk button, distance closure and four GUI sizes");
                        done = true;
                    }); phase = 10;
                }
            } catch (Throwable error) { failed = true; Napuli.LOGGER.error("[PICKPOCKETTEST] FAILED", error); }
        });
    }
    private static boolean interact(Minecraft mc, UUID id) {
        var entity = mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(8), e -> e.getUUID().equals(id)).stream().findFirst();
        entity.ifPresent(target -> mc.gameMode.interact(mc.player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND));
        return entity.isPresent();
    }
    private static void steal(PickpocketScreen screen) {
        var entry = screen.snapshot().entries().stream().filter(e -> e.available() > 0).findFirst().orElseThrow();
        press(screen, entry.label().getString() + " · " + entry.stack().getHoverName().getString());
    }
    private static void press(net.minecraft.client.gui.screens.Screen screen, String label) {
        var button = screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                .filter(b -> b.active && b.getMessage().getString().equals(label)).findFirst().orElseThrow();
        button.onPress(new KeyEvent(257, 0, 0));
    }
    private static void server(Minecraft mc, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> { try { action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())); }
            catch (Throwable error) { failed = true; Napuli.LOGGER.error("[PICKPOCKETTEST] FAILED on server", error); } });
    }
    private static void shot(Minecraft mc, String name) { InterfaceClientChecks.bounds(mc.gui.screen()); Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> {}); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private PickpocketClientTest() {}
}
