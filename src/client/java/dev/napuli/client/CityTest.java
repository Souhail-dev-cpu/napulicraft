package dev.napuli.client;

import dev.napuli.Motorino;
import dev.napuli.Music;
import dev.napuli.NapoliMap;
import dev.napuli.NapoliResident;
import dev.napuli.Napuli;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

final class CityTest {
    private static final boolean EXPORT = "1".equals(System.getenv("NAPULI_EXPORT_CITY"));
    private static int tick;
    private static int titleTicks;
    private static boolean started;
    private static volatile boolean failed;
    private static int waitingTicks;
    private static double initialZ;

    static void register() {
        if (Boolean.getBoolean("napuli.citytest")) ClientTickEvents.END_CLIENT_TICK.register(CityTest::tick);
    }

    private static void tick(Minecraft mc) {
        if (failed && mc.player == null) { mc.stop(); return; }
        if (!started && mc.gui.screen() instanceof TitleScreen) {
            if (++titleTicks == 1) {
                mc.getLanguageManager().setSelected("nap_it");
                mc.reloadResourcePacks();
            } else if (titleTicks == 120) {
                started = true;
                var settings = new LevelSettings(EXPORT ? "Napoli - Centro storico" : "Napoli — prova", GameType.SURVIVAL,
                    new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), !EXPORT, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel((EXPORT ? "napoli-map-" : "napoli-test-") + System.currentTimeMillis(),
                        settings, SelectWorldScreen.TEST_OPTIONS,
                        EXPORT ? WorldPresets::createNormalWorldDimensions : WorldPresets::createTestWorldDimensions, new TitleScreen());
            }
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
        if (tick == 60 && (mc.player.level().dimension() != NapoliMap.LEVEL || mc.gui.screen() != null)) {
            if (++waitingTicks >= 2400) {
                assertTrue(false, "city dimension loaded in time");
                mc.stop();
            }
            return;
        }
        switch (++tick) {
            case 25 -> {
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.options.showSubtitles().set(true);
                if (EXPORT) {
                    server(mc, player -> {
                        player.setGameMode(GameType.SPECTATOR);
                        AutoTest.run(player.level().getServer(), "time set noon");
                    });
                    break;
                }
                mc.player.connection.sendCommand("gamerule spawn_monsters false");
                mc.player.connection.sendCommand("gamerule spawn_mobs false");
                mc.player.connection.sendCommand("time set noon");
                mc.player.connection.sendCommand("napoli");
            }
            case 40 -> { if (EXPORT) mc.player.connection.sendCommand("napoli"); }
            case 62 -> server(mc, player -> {
                if (EXPORT) {
                    var spawn = new net.minecraft.core.BlockPos(0, 64, -54);
                    player.level().setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData.of(
                            NapoliMap.LEVEL, spawn, 0f, 0f));
                    view(player, 0.5, 64, -53.5, 0, 0);
                    player.setGameMode(GameType.SURVIVAL);
                    Napuli.LOGGER.info("[CITYTEST] export save ready");
                    mc.execute(mc::stop);
                    return;
                }
                var bikes = player.level().getEntitiesOfClass(Motorino.class, player.getBoundingBox().inflate(28));
                assertTrue(!bikes.isEmpty() && bikes.getFirst().getPassengers().size() == 3, "three riders spawned");
                if (!bikes.isEmpty()) {
                    Motorino bike = bikes.getFirst();
                    initialZ = bike.getZ();
                    assertTrue(bike.getPassengers().stream().allMatch(rider ->
                            Math.abs(net.minecraft.util.Mth.wrapDegrees(rider.getYHeadRot() - bike.getYRot())) < 5),
                            "riders face forward");
                    assertTrue(bike.getPassengers().getFirst().distanceTo(bike.getPassengers().getLast()) > 0.85f,
                            "three riders fit on the two seats");
                    view(player, bike.getX() - 5, 64, bike.getZ() - 1.7, -90, 0);
                }
                assertTrue(Music.current(player) == Music.Situation.LITE, "scene music started");
            });
            case 68 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
            case 72 -> {
                var music = mc.getSoundManager().getSoundEvent(Music.Situation.LITE.sound.location());
                assertTrue(music != null && music.getWeight() > 0, "music resource is loaded");
                AutoTest.shot(mc, "napoli_due_posti");
            }
            case 76 -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
            case 85 -> server(mc, player -> {
                var bikes = player.level().getEntitiesOfClass(Motorino.class, player.getBoundingBox().inflate(28));
                assertTrue(!bikes.isEmpty() && Math.abs(bikes.getFirst().getZ() - initialZ) > 2, "motorino moved");
                player.setGameMode(GameType.SPECTATOR);
                view(player, -17, 111, -47, -25, 25);
                var city = player.level();
                assertTrue(city.getBlockState(new net.minecraft.core.BlockPos(31, 92, -7)).is(net.minecraft.world.level.block.Blocks.IRON_BLOCK),
                        "gallery iron dome");
                assertTrue(city.getBlockState(new net.minecraft.core.BlockPos(20, 78, 98)).is(net.minecraft.world.level.block.Blocks.CUT_SANDSTONE),
                        "upper castle rampart");
            });
            case 105 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
            case 113 -> AutoTest.shot(mc, "napoli_v2_01_panorama");
            case 115 -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
            case 120 -> server(mc, player -> { player.setGameMode(GameType.SURVIVAL); view(player, 19.1, 64, -22.9, 122, 9); });
            case 134 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
            case 139 -> AutoTest.shot(mc, "napoli_teresa_rivista");
            case 142 -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
            case 144 -> server(mc, player -> talk(player, NapoliResident.Role.TERESA));
            case 161 -> AutoTest.shot(mc, "napoli_v2_03_dialogo");
            case 165 -> press(mc, "listen");
            case 179 -> press(mc, "joke");
            case 193 -> press(mc, "compliment");
            case 207 -> press(mc, "coffee");
            case 235 -> {
                AutoTest.shot(mc, "napoli_v2_04_invito");
                press(mc, "accept");
            }
            case 251 -> {
                AutoTest.shot(mc, "napoli_v2_05_appuntamento");
                press(mc, "space");
                server(mc, player -> view(player, -52.5, 58, -27, 180, 0));
            }
            case 280 -> server(mc, player -> {
                assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), "crypt triggers darkness");
                assertTrue(hasGhost(player, NapoliResident.Role.ANIMA), "wandering soul spawned");
            });
            case 290 -> AutoTest.shot(mc, "napoli_v2_06_anema_pezzentella");
            case 300 -> {
                mc.player.connection.sendCommand("time set midnight");
                server(mc, player -> view(player, 2.5, 64, 53, 0, 0));
            }
            case 327 -> server(mc, player -> assertTrue(hasGhost(player, NapoliResident.Role.PARTENOPE), "Partenope spawned"));
            case 330 -> AutoTest.shot(mc, "napoli_v2_07_partenope");
            case 336 -> server(mc, player -> view(player, 33.5, 64, -8, -90, 0));
            case 365 -> server(mc, player -> {
                assertTrue(hasGhost(player, NapoliResident.Role.MBRIANA), "Bella 'Mbriana spawned");
                assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "guardian heals");
            });
            case 370 -> AutoTest.shot(mc, "napoli_v2_08_mbriana");
            case 372 -> {
                mc.player.connection.sendCommand("time set noon");
                server(mc, player -> view(player, 36.5, 64, -6.5, 108, 9));
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            }
            case 382 -> AutoTest.shot(mc, "napoli_lucia_rivista");
            case 384 -> {
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                server(mc, player -> talk(player, NapoliResident.Role.LUCIA));
            }
            case 398 -> {
                AutoTest.shot(mc, "napoli_v2_09_lucia");
                press(mc, "listen");
            }
            case 408 -> press(mc, "space");
            case 414 -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                server(mc, player -> {
                    player.setGameMode(GameType.SPECTATOR);
                    view(player, 21, 78, 26, 90, 4);
                });
            }
            case 426 -> AutoTest.shot(mc, "napoli_v2_plebiscito");
            case 430 -> server(mc, player -> player.setGameMode(GameType.SURVIVAL));
            case 435 -> server(mc, player -> view(player, -6.5, 64, -27.5, 90, 0));
            case 465 -> server(mc, player -> talk(player, NapoliResident.Role.VENDOR));
            case 478 -> {
                AutoTest.shot(mc, "napoli_bottega");
                var screen = mc.gui.screen();
                var pizza = screen == null ? java.util.Optional.<AbstractButton>empty()
                    : descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                        .filter(b -> b.getMessage().getString().contains(new net.minecraft.world.item.ItemStack(Napuli.PIZZA).getHoverName().getString())).findFirst();
                assertTrue(pizza.isPresent(), "market pizza button");
                pizza.ifPresent(b -> b.onPress(new KeyEvent(257, 0, 0)));
            }
            case 497 -> server(mc, player -> {
                assertTrue(player.getInventory().contains(new net.minecraft.world.item.ItemStack(Napuli.PIZZA)), "pizza purchased");
                var pizza = new net.minecraft.world.item.ItemStack(Napuli.PIZZA);
                var consumable = pizza.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
                for (int n = 0; n < 4; n++) consumable.onConsume(player.level(), player, pizza.copy());
                assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) > 1, "overeating changes size");
            });
            case 515 -> server(mc, player -> talk(player, NapoliResident.Role.VENDOR));
            case 529 -> streetButton(mc, "delivery");
            case 543 -> {
                mc.player.connection.sendCommand("time set midnight");
                server(mc, player -> view(player, 12, 64, 50, -35, 0));
            }
            case 573 -> server(mc, player -> {
                var clash = player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        player.getBoundingBox().inflate(20));
                assertTrue(clash.stream().anyMatch(mob -> mob instanceof NapoliResident npc && npc.role().fighter()), "night clash spawns at the waterfront");
                talk(player, NapoliResident.Role.PORTER);
            });
            case 588 -> streetButton(mc, "turn_in");
            case 607 -> server(mc, player -> assertTrue(dev.napuli.NapoliStreet.cash(player) >= 15, "dock delivery pays twelve coins"));
            case 613 -> server(mc, player -> view(player, 27.5, 64, 73.5, 0, 0));
            case 639 -> server(mc, player -> talk(player, NapoliResident.Role.SALVATORE));
            case 654 -> {
                assertTrue(mc.gui.screen() instanceof DialogScreen<?>, "Borgo Marinari leader has a mission dialog");
                AutoTest.shot(mc, "napoli_maree");
                if (mc.gui.screen() != null) mc.gui.screen().onClose();
            }
            case 660 -> server(mc, player -> view(player, -31, 64, -2.5, 90, 0));
            case 686 -> server(mc, player -> talk(player, NapoliResident.Role.ADA));
            case 703 -> {
                AutoTest.shot(mc, "napoli_lanterne");
                streetButton(mc, "help_supply");
            }
            case 715 -> server(mc, player -> view(player, -46, 64, -5.5, 90, 0));
            case 734 -> server(mc, player -> talk(player, NapoliResident.Role.MICHELE));
            case 751 -> streetButton(mc, "contact");
            case 764 -> server(mc, player -> view(player, -31, 64, -2.5, 90, 0));
            case 785 -> server(mc, player -> talk(player, NapoliResident.Role.ADA));
            case 801 -> streetButton(mc, "report");
            case 819 -> server(mc, player -> assertTrue(dev.napuli.NapoliStreet.cash(player) >= 31, "faction mission pays sixteen coins"));
            case 827 -> mc.player.connection.sendCommand("portafoglio");
            case 836 -> server(mc, player -> talk(player, NapoliResident.Role.ADA));
            case 853 -> {
                var screen = mc.gui.screen();
                var unlocked = screen == null ? java.util.List.<String>of() : descendants(screen)
                    .filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .map(b -> b.getMessage().getString()).toList();
                assertTrue(unlocked.contains(Component.translatable("napuli.street.levy").getString())
                    && !unlocked.contains(Component.translatable("napuli.street.cache").getString()), "reputation unlocks the correct job");
                AutoTest.shot(mc, "napoli_rango_missioni");
                if (screen != null) screen.onClose();
            }
            case 866 -> server(mc, player -> view(player, 27.5, 64, 73.5, 0, 0));
            case 898 -> server(mc, player -> view(player, 4.5, 64, -18.5, 0, 0));
            case 914 -> server(mc, player -> {
                var pistol = new net.minecraft.world.item.ItemStack(Napuli.PISTOL);
                player.setItemInHand(InteractionHand.MAIN_HAND, pistol);
                Napuli.PISTOL.use(player.level(), player, InteractionHand.MAIN_HAND);
                assertTrue(pistol.getDamageValue() == 1, "arcade weapon fires and consumes a charge");
            });
            case 934 -> mc.player.connection.sendCommand("napoli esci");
            case 959 -> {
                assertTrue(mc.player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD, "return command");
                Napuli.LOGGER.info("[CITYTEST] complete, passed={}", !failed);
                mc.stop();
            }
            default -> {}
        }
    }

    private static void server(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(AutoTest.player(server)));
    }

    private static boolean hasGhost(ServerPlayer p, NapoliResident.Role role) {
        return p.level().getEntitiesOfClass(NapoliResident.class, p.getBoundingBox().inflate(20)).stream()
                .anyMatch(npc -> npc.role() == role);
    }

    private static void talk(ServerPlayer p, NapoliResident.Role role) {
        var people = p.level().getEntitiesOfClass(NapoliResident.class, p.getBoundingBox().inflate(6));
        var npc = people.stream().filter(person -> person.role() == role).findFirst();
        assertTrue(npc.isPresent(), role + " is in place");
        npc.ifPresent(person -> person.interact(p, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO));
    }

    private static void streetButton(Minecraft mc, String choice) {
        String label = Component.translatable("napuli.street." + choice).getString();
        var screen = mc.gui.screen();
        var button = screen == null ? java.util.Optional.<AbstractButton>empty()
                : descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .filter(b -> b.getMessage().getString().equals(label)).findFirst();
        assertTrue(button.isPresent(), "street button " + choice);
        button.ifPresent(b -> b.onPress(new KeyEvent(257, 0, 0)));
    }

    private static void press(Minecraft mc, String choice) {
        var screen = mc.gui.screen();
        assertTrue(screen instanceof DialogScreen<?>, "dialog is visible for " + choice);
        if (!(screen instanceof DialogScreen<?>)) return;
        String expected = Component.translatable("napuli.dialog.choice." + choice).getString();
        var button = descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                .filter(b -> b.getMessage().getString().equals(expected)).findFirst();
        assertTrue(button.isPresent(), "button " + choice);
        button.ifPresent(b -> b.onPress(new KeyEvent(257, 0, 0)));
    }

    private static java.util.stream.Stream<net.minecraft.client.gui.components.events.GuiEventListener> descendants(
            net.minecraft.client.gui.components.events.GuiEventListener widget) {
        if (widget instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(widget), container.children().stream().flatMap(CityTest::descendants));
        return java.util.stream.Stream.of(widget);
    }

    private static void view(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.level(), x, y, z, Set.of(), yaw, pitch, true);
    }

    private static void assertTrue(boolean condition, String description) {
        Napuli.LOGGER.info("[CITYTEST] {}: {}", description, condition);
        if (!condition) failed = true;
    }
}
