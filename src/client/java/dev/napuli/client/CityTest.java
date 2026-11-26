package dev.napuli.client;

import net.minecraft.world.level.block.Blocks;

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
    private static int waitingStaffTicks;
    private static double initialZ;
    private static int localCash;
    private static NapoliResident resident;

    private static boolean ridersInChat, ridersSpoke, footballNoticeInChat;

    static void register() {
        if (Boolean.getBoolean("napuli.footballtest")) {
            net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                if (overlay || !(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)) return;
                if (text.getKey().equals("napuli.meme.motorino")) ridersInChat = true;
                if (text.getKey().startsWith("napuli.football.")) footballNoticeInChat = true;
            });
        }
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
        if (Boolean.getBoolean("napuli.footballtest")) for (var npc : mc.level.getEntitiesOfClass(NapoliResident.class,mc.player.getBoundingBox().inflate(32)))
            if ((npc.role()==NapoliResident.Role.ENZO||npc.role()==NapoliResident.Role.GENNARO)&&NpcSpeechBubbles.hasBubble(npc.getUUID())) ridersSpoke=true;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
        if (tick == 60 && (mc.player.level().dimension() != NapoliMap.LEVEL || mc.gui.screen() != null)) {
            if (++waitingTicks >= 2400) {
                assertTrue(false, "city dimension loaded in time");
                mc.stop();
            }
            return;
        }
        ++tick;
        if (Boolean.getBoolean("napuli.footballtest") && tick > 60) {
            football(mc, tick);
            return;
        }
        if (Boolean.getBoolean("napuli.employmenttest") && tick > 60) {
            employment(mc, tick);
            return;
        }
        if (Boolean.getBoolean("napuli.civictest") && tick > 60) {
            civicLife(mc, tick);
            return;
        }
        if (Boolean.getBoolean("napuli.familiestest") && tick > 60) {
            families(mc, tick);
            return;
        }
        if (Boolean.getBoolean("napuli.housingtest") && tick > 60) {
            housing(mc, tick);
            return;
        }
        if (Boolean.getBoolean("napuli.localjobstest") && tick > 60) {
            localJobs(mc, tick);
            return;
        }
        switch (tick) {
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
            case 294 -> server(mc, player -> {
                assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), "crypt triggers darkness");
                assertTrue(hasGhost(player, NapoliResident.Role.ANIMA), "wandering soul spawned");
            });
            case 297 -> AutoTest.shot(mc, "napoli_v2_06_anema_pezzentella");
            case 298 -> server(mc, player -> {
                var ghost = player.level().getEntitiesOfClass(NapoliResident.class, player.getBoundingBox().inflate(20))
                        .stream().filter(npc -> npc.role() == NapoliResident.Role.ANIMA).findFirst();
                ghost.ifPresent(npc -> {
                    var previous = player.getMainHandItem();
                    player.setItemInHand(InteractionHand.MAIN_HAND,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CANDLE));
                    npc.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
                    assertTrue(npc.isRemoved() && !player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS),
                            "candle at the crypt puts the soul to rest");
                    player.setItemInHand(InteractionHand.MAIN_HAND, previous);
                });
            });
            case 300 -> {
                mc.player.connection.sendCommand("time set midnight");
                server(mc, player -> view(player, 2.5, 64, 53, 0, 0));
            }
            case 327 -> server(mc, player -> assertTrue(hasGhost(player, NapoliResident.Role.PARTENOPE), "Partenope spawned"));
            case 330 -> AutoTest.shot(mc, "napoli_v2_07_partenope");
            case 331 -> server(mc, player -> {
                interactWithGhost(player, NapoliResident.Role.PARTENOPE);
                assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING), "Partenope grants water breathing");
            });
            case 336 -> server(mc, player -> view(player, 116.5, 64, -43.5, -90, 0));
            case 365 -> server(mc, player -> {
                assertTrue(hasGhost(player, NapoliResident.Role.MBRIANA), "Bella 'Mbriana spawned");
                assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "guardian heals");
            });
            case 370 -> AutoTest.shot(mc, "napoli_v2_08_mbriana");
            case 371 -> server(mc, player -> {
                interactWithGhost(player, NapoliResident.Role.MBRIANA);
                assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "Bella 'Mbriana grants night vision");
            });
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


    private static java.util.Map<String, net.minecraft.world.phys.Vec3> firstRunners;
    private static net.minecraft.core.BlockPos footballEntry;
    private static boolean footballPoseSeen, footballPickerUsed;
    private static int footballPickerOpened;
    private static int footballBankBalance, waitingBankTicks;
    private static long footballBankStarted;
    private static volatile boolean bankCheckPending, bankCheckFinished;
    private static int waitingTrainingTicks;
    private static volatile boolean trainingCheckPending, trainingCheckFinished;

    private static void football(Minecraft mc, int step) {
        if (step >= 66 && step < 127 && !footballPickerUsed) {
            if (mc.gui.screen() == null && FootballControlsClient.canShoot()) {
                FootballControlsClient.openMoves(mc);
                footballPickerOpened = step;
            } else if (mc.gui.screen() instanceof FootballMovesScreen screen && step > footballPickerOpened + 2) {
                AutoTest.shot(mc, "fuorigrotta-tiri-e-mosse");
                var buttons = descendants(screen).filter(AbstractButton.class::isInstance)
                        .map(AbstractButton.class::cast).toList();
                assertTrue(buttons.stream().filter(button -> button.getMessage().getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().startsWith("napuli.football.move.")).count() == 6,
                        "The selector shows three shots and three skills");
                assertTrue(buttons.stream().anyMatch(button -> button.getMessage().equals(dev.napuli.CityText.t("close"))),
                        "The selector also offers an explicit close button");
                var chip = Component.translatable("napuli.football.move.pallonetto").getString();
                footballPickerUsed = buttons.stream().filter(button -> button.getMessage().getString().equals(chip))
                        .findFirst().map(button -> {
                            button.onPress(new KeyEvent(257, 0, 0));
                            return true;
                        }).orElse(false);
                assertTrue(mc.gui.screen() == null, "Choosing the chip closes the selector");
            }
        }
        if (footballPickerUsed && step < 135 && FootballActionClient.active(mc.player.getId())) footballPoseSeen = true;
        switch (step) {
            case 65 -> server(mc, p -> {
                var commands = p.level().getServer().getCommands();
                footballEntry = p.blockPosition();
                commands.performPrefixedCommand(p.createCommandSourceStack(), "calcio gioca");
                assertTrue(p.blockPosition().getX() > 365 && p.blockPosition().getZ() == 33, "player enters the pitch");
                p.setYRot(-40);
                p.setXRot(0);
            });
            case 135 -> server(mc, p -> {
                var field = p.getBoundingBox().inflate(75);
                var actors = p.level().getEntitiesOfClass(NapoliResident.class, field, n -> n.entityTags().contains("napuli_practice_actor"));
                assertTrue(actors.size() == 9, "player replaces one of ten footballers");
                assertTrue(footballPickerUsed, "A compact selector opens while in possession and sends the selected shot");
                assertTrue(footballPoseSeen, "The shot pose reached the real client");
                var stats = dev.napuli.FootballPractice.statistics(p.level());
                assertTrue(stats.shots() >= 1 && stats.playerKickDistance() > 1 && stats.playerKickHeight() > .6,
                        "chip shot lifts the ball off the pitch");
                var movement = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                assertTrue(movement.getValue() > movement.getBaseValue(), "pace increases actual movement speed on the pitch");
            });
            case 145 -> {
                assertTrue(FootballControlsClient.visible(), "control card is visible on the pitch");
                AutoTest.shot(mc, "fuorigrotta-in-campo");
                var east = NapoliRadar.Frame.fromYaw(-90);
                var forward = east.project(20, 0);
                var restored = east.unproject(forward.x(), forward.y());
                assertTrue(NapoliRadar.inNapoli(mc) && forward.y() < -19.9 && Math.abs(restored.x()-20) < .01
                        && Math.abs(restored.y()) < .01, "Radar rotates terrain and markers together");
                assertTrue(NapoliRadar.districtAt(mc.player.getBlockX(), mc.player.getBlockZ()).equals("Fuorigrotta"),
                        "The older sports ground keeps its district label");
                AutoTest.shot(mc, "fuorigrotta-minimappa");
            }
            case 152 -> mc.player.connection.sendCommand("calcio guarda");
            case 173 -> server(mc, p -> {
                assertTrue(p.blockPosition().getY() == 70 && p.blockPosition().getZ() == 3, "spectator reaches the stand");
                var actors = p.level().getEntitiesOfClass(NapoliResident.class, p.getBoundingBox().inflate(75),
                        n -> n.entityTags().contains("napuli_practice_actor"));
                assertTrue(actors.size() == 10, "NPC takes the place of the outgoing player");
                firstRunners = actors.stream().collect(java.util.stream.Collectors.toMap(
                        n -> n.getName().getString(), NapoliResident::position));
            });
            case 178 -> {
                assertTrue(!FootballControlsClient.visible(), "control card clears in the stands");
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                server(mc, p -> view(p, 399.5, 70, 3.5, 0, 19));
            }
            case 196 -> AutoTest.shot(mc, "fuorigrotta-partita");
            case 405 -> server(mc, p -> {
                var actors = p.level().getEntitiesOfClass(NapoliResident.class, p.getBoundingBox().inflate(75),
                        n -> n.entityTags().contains("napuli_practice_actor"));
                long movers = actors.stream().filter(n -> n.position().distanceToSqr(
                        firstRunners.get(n.getName().getString())) > 1).count();
                assertTrue(movers >= 2, "multiple NPCs reposition during the match");
                var stats = dev.napuli.FootballPractice.statistics(p.level());
                assertTrue(stats.passes() > 0 && stats.interceptions() > 0, "NPCs pass and intercept the ball (" + stats + ")");
                expectBlock(p, 435, 63, 33, Blocks.CALCITE);
                expectBlock(p, 405, 65, 20, Blocks.AIR);
            });
            case 411 -> AutoTest.shot(mc, "fuorigrotta-partita-azione");
            case 415 -> server(mc, p -> view(p, 354.5, 64, 17.5, 180, 0));
            case 423 -> AutoTest.shot(mc, "fuorigrotta-palestra");
            case 425 -> clickStaff(mc, NapoliResident.Role.FOOTBALL_COACH);
            case 432 -> {
                assertTrue(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,
                        "The coach opens the training selection");
                AutoTest.shot(mc, "fuorigrotta-scelta-allenamento");
                mc.gui.setScreen(null);
                mc.player.connection.sendCommand("calcio esercizio sprint");
            }
            case 440 -> {
                // Do not run until the exercise's teleport has reached the real client.
                if (Math.abs(mc.player.getX() - 354.5) > .6 || Math.abs(mc.player.getZ() - 19.5) > .6
                        || Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot())) > 1) {
                    tick--;
                    if (++waitingTrainingTicks > 1200) {
                        assertTrue(false, "sprint exercise timed out waiting for its starting position");
                        mc.stop();
                    }
                    break;
                }
                waitingTrainingTicks = 0;
                mc.options.keyUp.setDown(true);
                mc.options.keySprint.setDown(true);
            }
            case 502 -> {
                boolean running = mc.player.getZ() < 36;
                mc.options.keyUp.setDown(running);
                mc.options.keySprint.setDown(running);
                if (running || !trainingCheckFinished) {
                    tick--;
                    if (++waitingTrainingTicks > 1200) {
                        mc.options.keyUp.setDown(false);
                        mc.options.keySprint.setDown(false);
                        assertTrue(false, "sprint exercise timed out waiting for earned XP at " + mc.player.position());
                        mc.stop();
                        break;
                    }
                    if (!trainingCheckFinished && !trainingCheckPending) {
                        trainingCheckPending = true;
                        server(mc, p -> {
                            try { trainingCheckFinished = dev.napuli.FootballGym.progressXp(p, "pace") == 25; }
                            finally { trainingCheckPending = false; }
                        });
                    }
                }
            }
            case 508 -> server(mc, p -> {
                assertTrue(p.blockPosition().getZ() >= 34, "player can run through the training lane");
                assertTrue(dev.napuli.FootballPractice.pace(p) == 65 && dev.napuli.FootballGym.progressXp(p, "pace") == 25, "Sprint earns XP without giving a full rating point");
            });
            case 512 -> {
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.gui.toastManager().clear();
                mc.player.connection.sendCommand("calcio");
            }
            case 523 -> {
                assertTrue(ridersSpoke&&!ridersInChat, "scooter dialogue appears above its riders instead of chat");
                assertTrue(!footballNoticeInChat, "football notices stay out of chat");
                var profile = Component.translatable("napuli.football.profile", Component.translatable("napuli.football.position.attaccante"), 55, 0, 0);
                assertTrue(mc.gui.toastManager().getToast(NapoliNoticeToast.class, profile) != null,
                        "football profile appears as a screen notification");
                AutoTest.shot(mc, "fuorigrotta-avviso-schermo");
            }
            case 530 -> mc.player.connection.sendCommand("calcio torna");
            case 555 -> server(mc, p -> {
                assertTrue(p.blockPosition().equals(footballEntry), "football return reaches the original city location");
                for (var oldSign : java.util.List.of(
                        new net.minecraft.core.BlockPos(365, 64, 3), new net.minecraft.core.BlockPos(358, 64, 15),
                        new net.minecraft.core.BlockPos(355, 64, 19), new net.minecraft.core.BlockPos(356, 64, 17))) {
                    assertTrue(!(p.level().getBlockEntity(oldSign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity),
                            "old sports interaction sign is absent at " + oldSign.toShortString());
                }
                view(p, 365.5, 64, 4.5, 180, 0);
            });
            case 572 -> {
                mc.gui.toastManager().clear();
                if(mc.player.level().getEntitiesOfClass(NapoliResident.class,mc.player.getBoundingBox().inflate(5),n->n.role()==NapoliResident.Role.FOOTBALL_CLERK).isEmpty()&&waitingStaffTicks++<200){tick--;return;}
                clickStaff(mc, NapoliResident.Role.FOOTBALL_CLERK);
            }
            case 590 -> {
                var screen = mc.gui.screen();
                var watch = Component.translatable("napuli.football.watch_option").getString();
                assertTrue(screen instanceof FootballMenuScreen && descendants(screen).filter(AbstractButton.class::isInstance)
                        .map(AbstractButton.class::cast).anyMatch(button -> button.getMessage().getString().equals(watch)),
                        "talking to the match attendant opens the football menu");
                AutoTest.shot(mc, "fuorigrotta-menu");
                var league=descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                        .filter(b->b.getMessage().getString().equals(Component.translatable("napuli.football.league_option").getString())).findFirst();
                assertTrue(league.isPresent(),"The sports menu offers the neighborhood league");
                league.ifPresent(b->b.onPress(new KeyEvent(257,0,0)));
            }
            case 595 -> {
                assertTrue(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,"The league button opens the server calendar and standings");
                AutoTest.shot(mc,"fuorigrotta-campionato");
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
                mc.player.connection.sendCommand("calcio gioca ospiti");
            }
            case 625 -> server(mc, p -> {
                assertTrue(p.blockPosition().getX() > 399 && p.blockPosition().getZ() == 33,
                        "a visitor joins the opposite end of the same pitch");
                var actors = p.level().getEntitiesOfClass(NapoliResident.class, p.getBoundingBox().inflate(90),
                        n -> n.entityTags().contains("napuli_practice_actor"));
                assertTrue(actors.size() == 9, "entering as a visitor replaces an NPC");
            });
            case 640 -> mc.player.connection.sendCommand("calcio guarda");
            case 670 -> {
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
                server(mc, p -> {
                    var city = p.level();
                    view(p, 381.5, 64, -9.5, 180, 0);
                    expectBlock(p, 381, 64, -12, Blocks.ENDER_CHEST);
                    footballBankBalance = dev.napuli.NapoliStreet.cash(p);
                    footballBankStarted = city.getGameTime();
                    p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "colpo");
                    assertTrue(dev.napuli.NapoliStreet.cash(p) == footballBankBalance,
                            "starting the bank heist does not pay before the countdown");
                    AutoTest.run(city.getServer(), "tick sprint 260");
                });
            }
            case 735 -> {
                if (bankCheckFinished) break;
                // Client ticks keep running during a server sprint. Wait for the server's
                // clock, and acknowledge the checks before later steps move the player away.
                tick--;
                if (++waitingBankTicks > 1200) {
                    assertTrue(false, "bank heist verification timed out waiting for the server");
                    mc.stop();
                    break;
                }
                if (!bankCheckPending) {
                    bankCheckPending = true;
                    server(mc, p -> {
                        try {
                            long elapsed = p.level().getGameTime() - footballBankStarted;
                            if (elapsed < 260) return;
                            int expected = footballBankBalance + 90;
                            int paid = dev.napuli.NapoliStreet.cash(p);
                            assertTrue(paid == expected && dev.napuli.Carabinieri.isWanted(p),
                                    "bank heist pays once and triggers the Carabinieri (before="
                                            + footballBankBalance + ", after=" + paid + ", elapsed=" + elapsed + " ticks)");
                            p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "colpo");
                            assertTrue(dev.napuli.NapoliStreet.cash(p) == expected,
                                    "bank cooldown blocks immediate repeat payout");
                            bankCheckFinished = true;
                        } finally { bankCheckPending = false; }
                    });
                }
            }
            case 742 -> server(mc, p -> {
                view(p, 354.5, 64, 9.5, -90, 0);
                var bike = new net.minecraft.core.BlockPos(355, 64, 8);
                var blockRegistry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
                for (var machine : java.util.Map.of(bike, "cyclette",
                        new net.minecraft.core.BlockPos(355,64,11), "vogatore",
                        new net.minecraft.core.BlockPos(351,64,11), "panca_pesi",
                        new net.minecraft.core.BlockPos(350,64,7), "stepper").entrySet()) {
                    assertTrue(p.level().getBlockState(machine.getKey()).is(blockRegistry.getValue(Napuli.id(machine.getValue()))),
                            "Public gym installs " + machine.getValue());
                }
                var hit = new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(bike), net.minecraft.core.Direction.WEST, bike, false);
                p.level().getBlockState(bike).useWithoutItem(p.level(), p, hit);
                assertTrue(p.isPassenger(), "A player outside a match mounts the exercise bike");
            });
            case 750 -> mc.options.keyUp.setDown(true);
            case 785 -> AutoTest.shot(mc, "fuorigrotta-cyclette");
            case 860 -> mc.options.keyUp.setDown(false);
            case 865 -> server(mc, p -> {
                assertTrue(dev.napuli.PublicGym.sessions(p) == 1 && !p.isPassenger(),
                        "Pedalling with the real client completes and records the public workout");
                assertTrue(dev.napuli.FootballPractice.pace(p) == 65 && dev.napuli.FootballGym.progressXp(p, "pace") == 25,
                        "Public exercise leaves football ratings alone");
            });
            case 880 -> { Napuli.LOGGER.info("[FOOTBALLTEST] completed, failed={}", failed); mc.stop(); }
            default -> {}
        }
    }

    private static void employment(Minecraft mc, int step) {
        switch (step) {
            case 65 -> server(mc, p -> {
                view(p, -118.5, 64, 35.5, 180, 0);
                AutoTest.run(p.level().getServer(), "gamerule advance_time false", "time set noon");
            });

            case 95 -> assertTrue(HudClient.state() != null && HudClient.state().tx() == -118
                    && HudClient.state().objective().equals("napuli.work.visit")
                    && dev.napuli.Hud.clock(HudClient.state().cityTime()).equals("12:00"),
                    "HUD shows the employment office without a command and synchronizes the noon clock");
            case 100 -> {
                assertTrue(!(mc.player.level().getBlockEntity(new net.minecraft.core.BlockPos(-119,65,33))
                        instanceof net.minecraft.world.level.block.entity.SignBlockEntity), "office has no interaction sign");
                clickStaff(mc, NapoliResident.Role.EMPLOYMENT_CLERK);
            }
            case 119 -> {
                String job = Component.translatable("napuli.work.offer", Component.translatable("napuli.civic.job.cleaner"), 9).getString();
                var screen = mc.gui.screen();
                var button = screen == null ? java.util.Optional.<AbstractButton>empty()
                        : descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                            .filter(b -> b.getMessage().getString().equals(job)).findFirst();
                assertTrue(screen instanceof EmploymentScreen && button.isPresent(), "custom employment screen offers cleaner contract");
                AutoTest.shot(mc, "centro-impiego-offerte");
                button.ifPresent(b -> b.onPress(new KeyEvent(257, 0, 0)));
            }
            case 133 -> server(mc, p -> view(p, -165.5, 64, 48.5, 0, 0));
            case 150 -> server(mc, CityTest::viewCleaner);
            case 163 -> {
                clickStaff(mc, NapoliResident.Role.SHIFT_LEAD);
                AutoTest.shot(mc, "mergellina-capoturno");
            }
            case 173 -> server(mc, p -> {
                assertTrue(dev.napuli.NapoliStreet.cash(p) == 8, "clocking in does not grant payment");
                var brush = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BRUSH);
                assertTrue(p.getInventory().contains(brush), "supervisor hands the player a brush");
                p.setItemInHand(InteractionHand.MAIN_HAND, brush);
                view(p, -171.5, 64, 53.5, 0, 42);
            });
            case 185, 194, 203 -> employmentClick(mc, -172, 63, 53);
            case 212, 221, 230 -> employmentClick(mc, -170, 63, 53);
            case 239, 248, 257 -> employmentClick(mc, -172, 63, 55);
            case 266, 275, 284 -> employmentClick(mc, -170, 63, 55);
            case 295 -> server(mc, p -> {
                assertTrue(dev.napuli.NapoliStreet.cash(p) == 8, "no payment before one working hour");
                AutoTest.run(p.level().getServer(), "tick sprint 1050");
            });
            case 360 -> server(mc, CityTest::viewCleaner);
            case 375 -> clickStaff(mc, NapoliResident.Role.SHIFT_LEAD);
            case 395 -> server(mc, p -> assertTrue(dev.napuli.NapoliStreet.cash(p) == 17, "one working hour pays nine euros"));
            case 402 -> clickStaff(mc, NapoliResident.Role.SHIFT_LEAD);
            case 415 -> server(mc, p -> {
                assertTrue(dev.napuli.NapoliStreet.cash(p) == 17, "cannot collect twice on the same day");
                var civic = new net.minecraft.core.BlockPos(-154, 65, 40);
                assertTrue(p.level().getBlockEntity(civic) instanceof net.minecraft.world.level.block.entity.SignBlockEntity,
                        "Mergellina house has a numbered plaque");
                view(p, -151.5, 64, 44.5, 180, -5);
            });
            case 445 -> AutoTest.shot(mc, "mergellina-civico");
            case 450 -> server(mc, p -> view(p, -107.5, 65, 52.5, -90, 11));
            case 485 -> AutoTest.shot(mc, "mergellina-raccordo");
            case 620 -> server(mc, p -> view(p, -273, 65, 47, 90, 5));
            case 652 -> AutoTest.shot(mc, "mergellina-approdo");
            case 660 -> server(mc, p -> assertTrue(p.level().getEntitiesOfClass(NapoliResident.class,
                    p.getBoundingBox().inflate(55)).stream().anyMatch(npc -> npc.getName().getString().equals("Lucia De Luca")),
                    "new workers appear in the western quarter"));
            case 675 -> server(mc, p -> {
                expectBlock(p, 0, 63, -249, Blocks.POLISHED_DEEPSLATE);
                expectBlock(p, -108, 63, -46, Blocks.POLISHED_DEEPSLATE);
                expectBlock(p, -29, 63, 10, Blocks.POLISHED_DIORITE);
                expectBlock(p, 89, 63, 59, Blocks.SMOOTH_STONE);
                expectBlock(p, 340, 63, 0, Blocks.POLISHED_DEEPSLATE);
                view(p, 307.5, 64, 31.5, -80, -16);
            });
            case 710 -> AutoTest.shot(mc, "napoli-municipio-isolati");
            case 720 -> server(mc, p -> view(p, 6.5, 64, -219, -155, -12));
            case 755 -> AutoTest.shot(mc, "napoli-centro-isolati");
            case 765 -> { Napuli.LOGGER.info("[EMPLOYMENTTEST] completed, failed={}", failed); mc.stop(); }
            default -> {}
        }
    }

    private static void viewCleaner(ServerPlayer p) {
        var area = new net.minecraft.world.phys.AABB(-173,63,46,-159,67,60);
        var staff = p.level().getEntitiesOfClass(NapoliResident.class, area,
                npc -> npc.entityTags().contains("napuli_supervisor_cleaner"));
        assertTrue(staff.size() == 1, "cleaning shift has exactly one supervisor");
        if (staff.isEmpty()) return;
        var lead = staff.getFirst();
        assertTrue(lead.blockPosition().distSqr(new net.minecraft.core.BlockPos(-166,64,53)) >= 9,
                "cleaning supervisor uses a separate location from the civic cleaner");
        assertTrue(p.level().getEntitiesOfClass(NapoliResident.class, lead.getBoundingBox().inflate(.2),
                npc -> npc != lead).isEmpty(), "supervisor does not overlap another resident");
        view(p, lead.getX(), 64, lead.getZ() + 2, 180, 0);
    }

    private static void clickStaff(Minecraft mc, NapoliResident.Role role) {
        var staff = mc.player.level().getEntitiesOfClass(NapoliResident.class, mc.player.getBoundingBox().inflate(5),
                npc -> npc.role() == role).stream().min(java.util.Comparator.comparingDouble(npc -> npc.distanceToSqr(mc.player)));
        assertTrue(staff.isPresent(), "workplace NPC is available to talk to: " + role);
        staff.ifPresent(npc -> {
            mc.gameMode.interact(mc.player, npc, new net.minecraft.world.phys.EntityHitResult(npc), InteractionHand.MAIN_HAND);
        });
    }

    private static void expectBlock(net.minecraft.server.level.ServerPlayer p, int x, int y, int z,
                                    net.minecraft.world.level.block.Block expected) {
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        assertTrue(p.level().getBlockState(pos).is(expected), "expanded district pavement at " + pos.toShortString());
    }

    private static void employmentClick(Minecraft mc, int x, int y, int z) {
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.SOUTH, pos, false));
    }

    private static NapoliResident citizenChild;
    private static void civicLife(Minecraft mc, int step) {
        switch (step) {
            case 65 -> server(mc, p -> {
                view(p, -151.5, 64, 43.5, 180, 0);
                AutoTest.run(p.level().getServer(), "gamerule advance_time false", "time set noon", "time add 192000");
            });
            case 130 -> server(mc, p -> {
                var people = p.level().getEntitiesOfClass(NapoliResident.class, p.getBoundingBox().inflate(60));
                citizenChild = people.stream().filter(npc -> (npc.role() == NapoliResident.Role.CIVIC_MAN
                        || npc.role() == NapoliResident.Role.CIVIC_WOMAN)
                        && npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) < .9)
                        .findFirst().orElse(null);
                assertTrue(citizenChild != null, "children appear near the purchased family homes");
                AutoTest.run(p.level().getServer(), "time set midnight");
            });
            case 200 -> server(mc, p -> {
                if (citizenChild != null) {
                    view(p, citizenChild.getX(), citizenChild.getY() + 1, citizenChild.getZ() + 4, 180, 12);
                    assertTrue(citizenChild.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) < 1,
                            "child has a smaller model");
                }
            });
            case 220 -> AutoTest.shot(mc, "mergellina-cittadini");
            case 223 -> server(mc, p -> AutoTest.run(p.level().getServer(), "time add 480000"));
            case 303 -> server(mc, p -> {
                assertTrue(citizenChild != null && citizenChild.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.SCALE) == 1, "same resident becomes adult");
                AutoTest.run(p.level().getServer(), "time set noon");
            });
            case 370 -> server(mc, p -> {
                assertTrue(citizenChild != null && !citizenChild.getMainHandItem().isEmpty(), "adult child joins a daytime shift");
                if (citizenChild != null) view(p, citizenChild.getX(), 64, citizenChild.getZ() + 4, 180, 0);
            });
            case 392 -> AutoTest.shot(mc, "mergellina-lavoro");
            case 409 -> { Napuli.LOGGER.info("[CIVICTEST] completed, failed={}", failed); mc.stop(); }
            default -> {}
        }
    }

    private static FootballPlay.Frame firstFootballFrame;

    private static void families(Minecraft mc, int step) {
        switch (step) {
            case 65 -> server(mc, p -> {
                view(p, -168.5, 64, 21.5, 180, 0);
                AutoTest.run(p.level().getServer(), "gamerule advance_time false", "time set 8000");
            });
            case 110 -> server(mc, p -> {
                assertTrue(familyActivity(p, NapoliResident.Role.FAMILY_MOTHER, dev.napuli.NapoliHouseholds.Activity.COOK), "mother cooks");
                assertTrue(familyActivity(p, NapoliResident.Role.FAMILY_FATHER, dev.napuli.NapoliHouseholds.Activity.MATCH), "father watches Napoli");
                assertTrue(familyActivity(p, NapoliResident.Role.FAMILY_CHILD, dev.napuli.NapoliHouseholds.Activity.PLAY)
                        && familyActivity(p, NapoliResident.Role.FAMILY_CHILD_TWO, dev.napuli.NapoliHouseholds.Activity.PLAY), "children play");
            });
            case 120 -> {
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                AutoTest.shot(mc, "famiglia-mergellina-pomeriggio");
            }
            case 124 -> clickFamilyTv(mc);
            case 133 -> {
                assertTrue(mc.gui.screen() instanceof FootballScreen, "clicking TV opens the sports replay");
                if (mc.gui.screen() instanceof FootballScreen screen) {
                    assertTrue(screen.broadcast().mode() == dev.napuli.FootballBroadcast.Frame.REPLAY, "afternoon match is a replay");
                    firstFootballFrame = screen.action();
                    assertTrue(firstFootballFrame.players().length == 22, "both football teams have eleven players");
                }
                AutoTest.shot(mc, "telenapoli-replica");
            }
            case 148 -> {
                if (mc.gui.screen() instanceof FootballScreen screen) {
                    var next = screen.action();
                    assertTrue(firstFootballFrame != null && !next.ball().equals(firstFootballFrame.ball())
                            && !java.util.Arrays.equals(next.players(), firstFootballFrame.players()), "players and ball move between rendered frames");
                    AutoTest.shot(mc, "telenapoli-palla-in-movimento");
                    screen.onClose();
                } else assertTrue(false, "broadcast remains open");
                mc.options.keyShift.setDown(true);
            }
            case 156 -> clickFamilyTv(mc);
            case 158 -> mc.options.keyShift.setDown(false);
            case 164 -> clickFamilyTv(mc);
            case 173 -> {
                assertTrue(mc.gui.screen() instanceof TelevisionScreen, "click opens the selected news channel");
                AutoTest.shot(mc, "telenapoli-notizie");
                if (mc.gui.screen() instanceof TelevisionScreen screen) screen.onClose();
                mc.options.keyShift.setDown(true);
            }
            case 181, 186 -> clickFamilyTv(mc);
            case 189 -> mc.options.keyShift.setDown(false);
            case 194 -> server(mc, p -> {
                assertTrue(p.level().getBlockState(new net.minecraft.core.BlockPos(-171, 65, 20))
                        .is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("black_concrete"))), "TV switches off");
                assertTrue(familyActivity(p, NapoliResident.Role.FAMILY_FATHER, dev.napuli.NapoliHouseholds.Activity.REST),
                        "father stops watching when the TV is off");
            });
            case 197 -> clickFamilyTv(mc);
            case 205 -> {
                if (mc.gui.screen() instanceof FootballScreen screen) screen.onClose();
                server(mc, p -> AutoTest.run(p.level().getServer(), "time set 1150"));
            }
            case 234 -> server(mc, p -> assertTrue(familyActivity(p, NapoliResident.Role.FAMILY_MOTHER,
                    dev.napuli.NapoliHouseholds.Activity.WASH), "mother visits the washbasin in the morning"));
            case 239 -> server(mc, p -> AutoTest.run(p.level().getServer(), "time set 10800"));
            case 267 -> server(mc, p -> assertTrue(familyActivity(p, NapoliResident.Role.FAMILY_FATHER,
                    dev.napuli.NapoliHouseholds.Activity.EAT), "family changes activity for dinner"));
            case 271 -> server(mc, p -> AutoTest.run(p.level().getServer(), "time set 13823"));
            case 278 -> clickFamilyTv(mc);
            case 290 -> {
                assertTrue(mc.gui.screen() instanceof FootballScreen screen
                        && screen.broadcast().mode() == dev.napuli.FootballBroadcast.Frame.LIVE
                        && screen.broadcast().minute() == 37, "evening TV shows the live minute");
                AutoTest.shot(mc, "telenapoli-diretta");
                server(mc, p -> {
                    assertTrue(familyActivity(p, NapoliResident.Role.FAMILY_FATHER, dev.napuli.NapoliHouseholds.Activity.MATCH),
                            "father watches the scheduled evening fixture");
                    AutoTest.run(p.level().getServer(), "time set 15000");
                });
            }
            case 317 -> {
                assertTrue(mc.gui.screen() instanceof FootballScreen screen
                        && screen.broadcast().mode() == dev.napuli.FootballBroadcast.Frame.FINAL
                        && screen.broadcast().minute() == 90 && screen.broadcast().napoli() == 1
                        && screen.broadcast().juventus() == 0, "final television score matches the scheduled result");
                if (mc.gui.screen() instanceof FootballScreen screen) screen.onClose();
                server(mc, p -> AutoTest.run(p.level().getServer(), "time set midnight"));
            }
            case 367 -> server(mc, p -> {
                var relatives = p.level().getEntitiesOfClass(NapoliResident.class, p.getBoundingBox().inflate(12));
                assertTrue(relatives.stream().filter(npc -> npc.role() == NapoliResident.Role.FAMILY_MOTHER
                        || npc.role() == NapoliResident.Role.FAMILY_FATHER).filter(NapoliResident::isSleeping).count() == 2,
                        "parents sleep in the upstairs beds");
                AutoTest.run(p.level().getServer(), "time set noon");
                view(p, 4.5, 65, 26.5, 90, -6);
            });
            case 407 -> AutoTest.shot(mc, "plebiscito-colonnato");
            case 427 -> { Napuli.LOGGER.info("[FAMILYTEST] completed, failed={}", failed); mc.stop(); }
            default -> {}
        }
    }

    private static void clickFamilyTv(Minecraft mc) {
        var pos = new net.minecraft.core.BlockPos(-171, 65, 20);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.EAST, pos, false));
    }

    private static boolean familyActivity(ServerPlayer player, NapoliResident.Role role, dev.napuli.NapoliHouseholds.Activity activity) {
        return player.level().getEntitiesOfClass(NapoliResident.class, player.getBoundingBox().inflate(12)).stream()
                .anyMatch(npc -> npc.role() == role && npc.activity() == activity);
    }

    private static void housing(Minecraft mc, int step) {
        switch (step) {
            case 65 -> server(mc, p -> view(p, -151.5, 64, 37.5, 0, 0));
            case 84 -> server(mc, p -> assertTrue(p.getZ() >= 40, "unowned property expels visitors"));
            case 87 -> server(mc, p -> view(p, -149.5, 64, 41.5, 180, 0));
            case 98 -> mc.player.connection.sendCommand("casa compra leone");
            case 116 -> server(mc, p -> {
                assertTrue(dev.napuli.NapoliStreet.cash(p) == 8, "cannot purchase without funds");
                dev.napuli.NapoliStreet.credit(p, 60);
            });
            case 125 -> {
                var pos = new net.minecraft.core.BlockPos(-150, 64, 40);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                                net.minecraft.core.Direction.SOUTH, pos, false));
            }
            case 145 -> {
                var screen = mc.gui.screen();
                String label = Component.translatable("napuli.property.buy").getString();
                var button = screen == null ? java.util.Optional.<AbstractButton>empty()
                        : descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                            .filter(b -> b.getMessage().getString().equals(label)).findFirst();
                assertTrue(button.isPresent(), "property sign displays purchase confirmation");
                button.ifPresent(b -> b.onPress(new KeyEvent(257, 0, 0)));
            }
            case 162 -> server(mc, p -> {
                assertTrue(dev.napuli.NapoliStreet.cash(p) == 13, "home purchase debits exactly 55 euros");
                view(p, -151.5, 64, 37.5, 180, 0);
            });
            case 183 -> {
                AutoTest.shot(mc, "mergellina-home");
                server(mc, p -> {
                    assertTrue(p.getZ() < 39, "owner stays inside the house");
                    view(p, -168, 74, 47, 90, 15);
                });
            }
            case 200 -> AutoTest.shot(mc, "mergellina-lungomare");
            case 203 -> server(mc, p -> {
                resident = p.level().getEntitiesOfClass(NapoliResident.class,
                        new net.minecraft.world.phys.AABB(-179, 62, 40, -161, 68, 55)).stream()
                        .filter(npc -> npc.role() == NapoliResident.Role.PASSERBY)
                        .findFirst().orElse(null);
                assertTrue(resident != null, "Mergellina has street residents");
                AutoTest.run(p.level().getServer(), "gamerule advance_time false", "time set midnight");
            });
            case 438 -> server(mc, p -> {
                assertTrue(resident != null && resident.getZ() < 24, "resident returns indoors at night");
                AutoTest.run(p.level().getServer(), "time set noon");
            });
            case 659 -> server(mc, p -> assertTrue(resident != null && resident.getZ() > 41,
                    "resident returns to their usual daytime post"));
            case 675 -> { Napuli.LOGGER.info("[HOUSINGTEST] completed, failed={}", failed); mc.stop(); }
            default -> {}
        }
    }

    private static void localJobs(Minecraft mc, int step) {
        switch (step) {
            case 65 -> server(mc, p -> view(p, -18.5, 64, -122.5, 180, 0));
            case 80 -> server(mc, p -> talk(p, NapoliResident.Role.CUSTODE_TUNNEL));
            case 90 -> localButton(mc, "accept");
            case 103 -> server(mc, p -> talk(p, NapoliResident.Role.CUSTODE_TUNNEL));
            case 115 -> localButton(mc, "access");
            case 128 -> server(mc, p -> assertTrue(p.getBlockY() == 42, "keeper opens the underground"));
            case 132 -> AutoTest.shot(mc, "local_01_tunnel");
            case 135 -> server(mc, p -> view(p, -0.5, 42, -108.5, 180, 0));
            case 150 -> AutoTest.shot(mc, "local_02_cistern");
            case 155 -> server(mc, p -> view(p, -18.5, 42, -124.5, 180, 0));
            case 167 -> server(mc, p -> talk(p, NapoliResident.Role.CUSTODE_TUNNEL));
            case 180 -> localButton(mc, "report");
            case 194 -> server(mc, p -> {
                assertTrue(dev.napuli.NapoliStreet.cash(p) == 20, "cistern mission pays twelve euros");
                talk(p, NapoliResident.Role.CUSTODE_TUNNEL);
            });
            case 207 -> localButton(mc, "access");
            case 222 -> server(mc, p -> {
                assertTrue(p.getBlockY() == 64, "keeper returns player to street");
                view(p, -33.5, 64, -121.5, 180, 0);
            });
            case 235 -> server(mc, p -> talk(p, NapoliResident.Role.BUCATO));
            case 245 -> localButton(mc, "accept");
            case 257 -> server(mc, p -> talk(p, NapoliResident.Role.BUCATO));
            case 267 -> localButton(mc, "access");
            case 285 -> server(mc, p -> assertTrue(p.getBlockY() == 90, "laundry lady opens the rooftop"));
            case 290 -> AutoTest.shot(mc, "local_03_rooftop");
            case 295 -> server(mc, p -> view(p, -25.5, 90, -119.5, 90, 0));
            case 309 -> server(mc, p -> view(p, -33.5, 90, -119.5, 90, 0));
            case 321 -> server(mc, p -> talk(p, NapoliResident.Role.BUCATO));
            case 333 -> localButton(mc, "report");
            case 345 -> server(mc, p -> {
                assertTrue(dev.napuli.NapoliStreet.cash(p) == 29, "rooftop mission pays nine euros");
                localCash = dev.napuli.NapoliStreet.cash(p);
                view(p, 123.5, 64, -7.5, 180, 0);
            });
            case 360 -> server(mc, p -> talk(p, NapoliResident.Role.MUSICISTA));
            case 371 -> localButton(mc, "accept");
            case 383 -> server(mc, p -> talk(p, NapoliResident.Role.MUSICISTA));
            case 395 -> localButton(mc, "low");
            case 407 -> localButton(mc, "high");
            case 419 -> localButton(mc, "middle");
            case 431 -> localButton(mc, "low");
            case 448 -> server(mc, p -> {
                assertTrue(dev.napuli.NapoliStreet.cash(p) == localCash + 9, "four correct notes pay nine euros");
                localCash = dev.napuli.NapoliStreet.cash(p);
                view(p, 72.5, 64, -122.5, 180, 0);
            });
            case 465 -> AutoTest.shot(mc, "local_04_market");
            case 470 -> server(mc, p -> talk(p, NapoliResident.Role.MERCATANTE));
            case 485 -> {
                var screen = mc.gui.screen();
                var button = screen == null ? java.util.Optional.<AbstractButton>empty()
                        : descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                            .filter(b -> b.getMessage().getString().equals(Component.translatable("napuli.local.offer", 2,
                                    net.minecraft.world.item.Items.BREAD.getDefaultInstance().getHoverName(), 3).getString())).findFirst();
                assertTrue(button.isPresent(), "market bread offer displayed");
                button.ifPresent(b -> b.onPress(new KeyEvent(257, 0, 0)));
            }
            case 505 -> server(mc, p -> assertTrue(dev.napuli.NapoliStreet.cash(p) == localCash - 3
                    && p.getInventory().contains(net.minecraft.world.item.Items.BREAD.getDefaultInstance()), "market trades bread for three euros"));
            case 520 -> server(mc, p -> view(p, -11.5, 64, -128.5, 180, 0));
            case 535 -> server(mc, p -> talk(p, NapoliResident.Role.PIZZAIOLO));
            case 548 -> localButton(mc, "learn");
            case 560 -> server(mc, p -> {
                localCash = dev.napuli.NapoliStreet.cash(p);
                view(p, 118.5, 64, -21.5, 0, 0);
            });
            case 575 -> server(mc, p -> talk(p, NapoliResident.Role.TURISTA));
            case 587 -> localButton(mc, "accept");
            case 599 -> server(mc, p -> talk(p, NapoliResident.Role.TURISTA));
            case 610 -> localButton(mc, "gallery");
            case 625 -> server(mc, p -> {
                var souvenir = net.minecraft.world.item.Items.COMPASS.getDefaultInstance();
                souvenir.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable("napuli.local.souvenir"));
                assertTrue(dev.napuli.NapoliStreet.cash(p) == localCash + 5
                        && p.getInventory().contains(souvenir), "tourist gives a souvenir");
                view(p, 9.5, 64, 50.5, 0, 0);
            });
            case 638 -> server(mc, p -> talk(p, NapoliResident.Role.PESCATORE));
            case 650 -> localButton(mc, "accept");
            case 665 -> server(mc, p -> {
                assertTrue(p.getInventory().contains(net.minecraft.world.item.Items.FISHING_ROD.getDefaultInstance()), "fisherman lends a rod");
                view(p, -36.5, 64, -122.5, 180, 0);
            });
            case 680 -> server(mc, p -> talk(p, NapoliResident.Role.CARTOMANTE));
            case 692 -> localButton(mc, "learn");
            case 705 -> server(mc, p -> view(p, 30.5, 64, -139.5, 180, 0));
            case 720 -> server(mc, p -> talk(p, NapoliResident.Role.GUARDIANO_PRESEPE));
            case 732 -> localButton(mc, "accept");
            case 745 -> {
                Napuli.LOGGER.info("[CITYTEST] local jobs completed, failed={}", failed);
                mc.stop();
            }
            default -> {}
        }
    }

    private static void localButton(Minecraft mc, String action) {
        String label = Component.translatable("napuli.local.button." + action).getString();
        var screen = mc.gui.screen();
        var button = screen == null ? java.util.Optional.<AbstractButton>empty()
                : descendants(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .filter(b -> b.getMessage().getString().equals(label)).findFirst();
        assertTrue(button.isPresent(), "local jobs button " + action);
        button.ifPresent(b -> b.onPress(new KeyEvent(257, 0, 0)));
    }

    private static void interactWithGhost(ServerPlayer player, NapoliResident.Role role) {
        player.level().getEntitiesOfClass(NapoliResident.class, player.getBoundingBox().inflate(20)).stream()
                .filter(npc -> npc.role() == role).findFirst()
                .ifPresent(npc -> npc.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO));
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
