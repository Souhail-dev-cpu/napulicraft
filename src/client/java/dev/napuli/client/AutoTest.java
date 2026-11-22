package dev.napuli.client;

import dev.napuli.Munaciello;
import dev.napuli.MunacielloGigante;
import dev.napuli.Napuli;
import dev.napuli.Spawner;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Dev-only play-test (-Dnapuli.autotest=true): theft, catching him, the giant's stare -> chase, language. */
public final class AutoTest {
	private AutoTest() {}
	private static int tick = -1;
	private static boolean made;
	private static int y;
	private static Munaciello thief;
	private static MunacielloGigante giant;

	static void log(String f, Object... a) { Napuli.LOGGER.info("[NAPTEST] " + f, a); }

	public static void register() {
		if (!Boolean.getBoolean("napuli.autotest")) return;
		ClientTickEvents.END_CLIENT_TICK.register(AutoTest::onTick);
	}

	private static void onTick(Minecraft mc) {
		if (!made && mc.gui.screen() instanceof TitleScreen) {
			made = true;
			log("language list has Napulitano: {}", mc.getLanguageManager().getLanguages().containsKey("nap_it"));
			mc.getLanguageManager().setSelected("nap_it");
			mc.reloadResourcePacks();
			return;
		}
		if (made && tick < 0 && mc.player == null && mc.gui.screen() instanceof TitleScreen && ++titleTicks > 100 && !worldStarted) {
			worldStarted = true;
			LevelSettings s = new LevelSettings("napulitest", GameType.SURVIVAL,
					new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, WorldDataConfiguration.DEFAULT);
			mc.createWorldOpenFlows().createFreshLevel("napulitest-" + System.currentTimeMillis(), s, SelectWorldScreen.TEST_OPTIONS, WorldPresets::createTestWorldDimensions, new TitleScreen());
			return;
		}
		if (mc.player == null || mc.getSingleplayerServer() == null) return;
		mc.options.pauseOnLostFocus = false;
		if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) mc.gui.setScreen(null);
		tick++;
		MinecraftServer server = mc.getSingleplayerServer();
		switch (tick) {
			case 5 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); shot(mc, "00_pause_menu_check"); }
			case 15 -> server.execute(() -> {
				ServerLevel lvl = server.overworld();
				y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
				run(server, "time set noon", "weather clear", "gamerule advance_time false", "gamerule spawn_mobs false",
						"gamerule spawn_monsters false", "execute in minecraft:overworld run tp @p 0 " + y + " 0 0 0", "effect give @p resistance infinite 4 true");
				ServerPlayer p = player(server);
				p.getInventory().clearContent();
				p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
				log("menu string 'menu.singleplayer' in game language: '{}'", net.minecraft.network.chat.Component.translatable("menu.singleplayer").getString());
				thief = Napuli.MUNACIELLO.create(lvl, EntitySpawnReason.COMMAND);
				thief.snapTo(0.5, y, 1.5, 180, 0);
				lvl.addFreshEntity(thief);
			});
			case 50 -> {
				for (var ev : new net.minecraft.sounds.SoundEvent[] {dev.napuli.Music.Situation.RADIO.sound, dev.napuli.Music.Situation.LITE.sound, Napuli.URLO, Napuli.RISATA}) {
					var w = mc.getSoundManager().getSoundEvent(ev.location());
					log("sound {} -> weight {}", ev.location(), w == null ? "MISSING" : w.getWeight());
				}
				server.execute(() -> Spawner.playSong(server));
			}
			case 70 -> shot(mc, "01_munaciello_coming");
			case 182 -> server.execute(() -> {
				ServerPlayer p = player(server);
				log("after approach: munaciello has loot={} holding='{}', player still has pickaxe={}",
						thief.hasLoot(), thief.getMainHandItem().getHoverName().getString(), p.getInventory().contains(new ItemStack(Items.DIAMOND_PICKAXE)));
			});
			case 185 -> shot(mc, "02_munaciello_running_with_pick");
			case 120, 140, 160, 180 -> server.execute(() -> log("t={} thief distance from player = {}", tick, (int) thief.distanceTo(player(server))));
			case 190 -> server.execute(() -> {
				ServerPlayer p = player(server);
				thief.hurtServer(server.overworld(), server.overworld().damageSources().playerAttack(p), 100f);
			});
			case 220 -> server.execute(() -> {
				var items = server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						new net.minecraft.world.phys.AABB(-150, -64, -150, 150, 320, 150));
				for (var it : items) log("drop after catching him: {} x{}", it.getItem().getHoverName().getString(), it.getItem().getCount());
				log("raggia now = {}", player(server).getAttachedOrElse(Napuli.RAGGIA, 0));
				run(server, "kill @e[type=item]", "time set midnight", "tp @p 0 " + y + " 0 0 0");
			});
			case 240 -> server.execute(() -> {
				ServerLevel lvl = server.overworld();
				giant = Napuli.GIGANTE.create(lvl, EntitySpawnReason.COMMAND);
				giant.snapTo(0.5, y, 30.5, 180, 0); // straight in front of the player, 30 blocks
				giant.setPrey(player(server));
				lvl.addFreshEntity(giant);
				run(server, "tp @p 0 " + y + " 0 90 0"); // look away first
			});
			case 290 -> server.execute(() -> log("looking away: giant chasing={} dist={}", giant.isChasing(), (int) giant.distanceTo(player(server))));
			case 300 -> shot(mc, "03_dark_looking_away");
			case 310 -> server.execute(() -> run(server, "tp @p 0 " + y + " 0 0 -6")); // now look at him
			case 330 -> server.execute(() -> log("looked at him: giant chasing={} darkness on player={}", giant.isChasing(),
					player(server).hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)));
			case 332 -> shot(mc, "04_giant_stare");
			case 365 -> server.execute(() -> log("pursuit: giant chasing={} darkness on player={}", giant.isChasing(),
					player(server).hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)));
			case 380 -> shot(mc, "05_giant_chasing");
			case 420 -> server.execute(() -> log("after 4 s chase: dist={} health player={}", (int) giant.distanceTo(player(server)), player(server).getHealth()));
			case 430 -> { run(server, "time set noon"); }
			case 440 -> shot(mc, "06_giant_daylight");
			case 470 -> server.execute(() -> log("sunrise: giant still there={}", giant.isAlive() && !giant.isRemoved()));
			case 480 -> server.execute(() -> {
				ServerLevel lvl = server.overworld();
				MunacielloGigante g2 = Napuli.GIGANTE.create(lvl, EntitySpawnReason.COMMAND);
				g2.snapTo(5, y, 5, 0, 0);
				lvl.addFreshEntity(g2);
				g2.hurtServer(lvl, lvl.damageSources().playerAttack(player(server)), 5000f);
			});
			case 500 -> server.execute(() -> {
				var items = server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						new net.minecraft.world.phys.AABB(-150, -64, -150, 150, 320, 150));
				for (var it : items) log("giant drop: {} x{}", it.getItem().getHoverName().getString(), it.getItem().getCount());
				log("done");
			});
			case 510 -> server.execute(() -> {
				run(server, "time set noon", "tp @p 0 " + y + " 0 0 0", "fill -4 " + (y - 1) + " -4 4 " + (y - 1) + " 14 minecraft:smooth_stone",
						"fill -4 " + y + " -4 4 " + (y + 4) + " 14 minecraft:air", "summon napuli:motorino 0.5 " + y + " 3.5");
			});
			case 525 -> {
				mc.options.showSubtitles().set(true);
				if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				shot(mc, "07_motorino");
			}
			case 545 -> server.execute(() -> {
				ServerPlayer p = player(server);
				var bikes = p.level().getEntitiesOfClass(dev.napuli.Motorino.class, p.getBoundingBox().inflate(10));
				if (bikes.isEmpty()) throw new IllegalStateException("Motorino not spawned");
				dev.napuli.Music.stop(p);
				bikes.getFirst().interact(p, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
				log("motorino mounted={} radio={}", p.getVehicle() == bikes.getFirst(), dev.napuli.Music.current(p));
			});
			case 565 -> {
				var radio = mc.getSoundManager().getSoundEvent(dev.napuli.Music.Situation.RADIO.sound.location());
				log("radio resource count={}", radio == null ? 0 : radio.getWeight());
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			}
			case 575 -> shot(mc, "08_motorino_radio");
			case 595 -> server.execute(() -> {
				player(server).stopRiding();
				dev.napuli.Music.stop(player(server));
				dev.napuli.Napuli.LOGGER.info("[NAPTEST] Mammà calls");
				dev.napuli.Situazioni.chiamaMamma(player(server), 3);
			});
			case 615 -> {
				mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
				shot(mc, "09_mamma");
			}
			case 680 -> {
				shot(mc, "10_mamma_risposta");
				server.execute(() -> log("music and Mammà test done"));
			}
			case 705 -> mc.stop();
			default -> {}
		}
	}

	private static boolean worldStarted;
	private static int titleTicks;

	static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	static void run(MinecraftServer server, String... cmds) {
		for (String c : cmds) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
	}

	static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> log("screenshot {}", name));
	}

	@SuppressWarnings("unused") private static Spawner unused;
}
