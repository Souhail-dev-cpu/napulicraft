package dev.napuli.client;

import dev.napuli.Carabinieri;
import dev.napuli.Motorino;
import dev.napuli.Napuli;
import dev.napuli.NapoliMap;
import dev.napuli.NapoliResident;
import dev.napuli.NapoliScenes;
import dev.napuli.Radio;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Dev-only (-Dnapuli.radiotest=true): the parade song follows the scooter, helmets on the boys, the carabiniere
 * stopping a parade without helmets (music off, boys in the cells), the GTA radio on the player's scooter, Vesuvius.
 */
final class RadioTest {
	private RadioTest() {}
	private static int tick = -1, titleTicks;
	private static boolean started;
	private static Motorino parade, mine;
	private static final List<NapoliResident> riders = new ArrayList<>();
	private static String firstSoundPos;

	static void register() {
		if (Boolean.getBoolean("napuli.radiotest")) ClientTickEvents.END_CLIENT_TICK.register(RadioTest::onTick);
	}

	private static void onTick(Minecraft mc) {
		if (!started && mc.gui.screen() instanceof TitleScreen) {
			if (++titleTicks == 110) {
				started = true;
				var s = new LevelSettings("napoli-radio", GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
						true, WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows().createFreshLevel("napoli-radio-" + System.currentTimeMillis(), s, SelectWorldScreen.TEST_OPTIONS,
						WorldPresets::createTestWorldDimensions, new TitleScreen());
			}
			return;
		}
		if (mc.player == null || mc.getSingleplayerServer() == null) return;
		mc.options.pauseOnLostFocus = false;
		if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
		MinecraftServer server = mc.getSingleplayerServer();
		if (tick < 0) {
			if (++titleTicks == 200) server.execute(() -> NapoliMap.visit(PoliceTest.player(server)));
			if (mc.player.level().dimension() == NapoliMap.LEVEL && titleTicks > 280) tick = 0;
			return;
		}
		if (tick > 20 && tick < 160 || tick > 290) mc.player.getAbilities().flying = mc.player.getVehicle() == null;
		switch (++tick) {
			case 10 -> server.execute(() -> {
				PoliceTest.run(server, "time set 6000", "weather clear", "gamerule advance_time false");
				// no patrol near Via Toledo for the first part: we place our own carabiniere
				var city = PoliceTest.city(server);
				for (var cc : city.getEntitiesOfClass(NapoliResident.class, PoliceTest.player(server).getBoundingBox().inflate(80),
						r -> r.role() == NapoliResident.Role.CARABINIERE)) cc.discard();
			});
			case 20 -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.player.getAbilities().flying = true; }
			case 24 -> {
				// the files really decode: bytes and loudness from the chorus of each song
				for (var song : dev.napuli.client.music.UserMusic.library()) {
					try (var st = dev.napuli.client.music.UserAudio.open(song.path(), 60, 2)) {
						long bytes = 0; int peak = 0;
						for (int i = 0; i < 40; i++) {
							var b = st.read(8192);
							if (!b.hasRemaining()) break;
							bytes += b.remaining();
							while (b.remaining() >= 2) peak = Math.max(peak, Math.abs(b.getShort()));
						}
						PoliceTest.log("decode {} -> {} bytes in 2 s (mono {} Hz), peak {}", song.file(), bytes, (int) st.getFormat().getSampleRate(), peak);
					} catch (Exception e) { PoliceTest.log("decode {} FAILED {}", song.file(), e.toString()); }
				}
				PoliceTest.log("stations: {}", RadioClient.stations().stream().map(st -> st.name() + "(" + st.songs().size() + ")").toList());
			}
			case 26 -> PoliceTest.run(server, "execute as @a run napuli canzone lite");
			case 29 -> PoliceTest.log("situation lite: {}", dev.napuli.client.music.UserMusic.sceneDebug());
			case 30 -> { PoliceTest.tp(server, 0, 65, -5, -20, 12); if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
			case 45 -> server.execute(() -> {
				var city = PoliceTest.city(server);
				// any parade already running somewhere else goes away; ours comes up Via Toledo towards the camera
				for (var m : city.getEntitiesOfClass(Motorino.class, PoliceTest.player(server).getBoundingBox().inflate(120))) { m.ejectPassengers(); m.discard(); }
				parade = Napuli.MOTORINO.create(city, EntitySpawnReason.EVENT);
				parade.snapTo(3.5, 64, 4.5, 180, 0);
				parade.startParade();
				city.addFreshEntity(parade);
				riders.clear();
				for (var role : new NapoliResident.Role[] {NapoliResident.Role.GENNARO, NapoliResident.Role.CIRO, NapoliResident.Role.ENZO}) {
					NapoliResident r = NapoliResident.spawn(city, role, BlockPos.containing(3.5, 64, 4.5), 180);
					r.setNoAi(true);
					r.startRiding(parade, true, false);
					r.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Napuli.CASCO)); // the helmet shot
					riders.add(r);
				}
				PoliceTest.log("parade bike station {} riders {}", parade.station(), parade.getPassengers().size());
			});
			case 62 -> PoliceTest.shot(mc, "r01_corteo_casco");
			case 72 -> { firstSoundPos = RadioClient.debug(); PoliceTest.log("radio t72: {}", firstSoundPos); }
			case 92 -> PoliceTest.log("radio t92 (the sound must have moved with the bike): {}", RadioClient.debug());
			case 95 -> server.execute(() -> {
				// take the helmets off and put a carabiniere in their way
				for (var r : riders) r.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
				if (parade == null) return;
				BlockPos at = BlockPos.containing(parade.getX() - 3, 64, parade.getZ() - 7);
				NapoliResident cc = NapoliResident.spawn(PoliceTest.city(server), NapoliResident.Role.CARABINIERE, at, 180);
				PoliceTest.log("carabiniere placed {} at {}", cc != null, at);
				PoliceTest.tpNow(server, parade.getX() + 8, 65.6, parade.getZ() - 4, 90, 8);
			});
			case 115 -> {
				PoliceTest.shot(mc, "r02_alt_carabiniere");
				server.execute(() -> PoliceTest.log("after the stop: bike alive {} station {} (OFF={})", parade != null && parade.isAlive(),
						parade == null ? "-" : parade.station(), Radio.OFF));
			}
			case 118 -> PoliceTest.log("radio after the stop: {}", RadioClient.debug());
			case 280 -> server.execute(() -> {
				StringBuilder sb = new StringBuilder();
				for (var r : riders) sb.append(r.role()).append(r.isAlive() ? " at " + r.blockPosition() : " gone")
						.append(Carabinieri.npcJailed(r.getUUID()) ? " JAILED; " : " free; ");
				PoliceTest.log("bike seized: {} | riders: {}", parade == null || !parade.isAlive(), sb);
			});
			case 285 -> PoliceTest.log("radio after the seizure: {}", RadioClient.debug());
			case 290 -> server.execute(() -> {
				var r = riders.isEmpty() ? null : riders.getFirst();
				if (r != null) PoliceTest.tpNow(server, r.getX() + 4, r.getY() + 1, r.getZ(), 90, 10);
			});
			case 320 -> PoliceTest.shot(mc, "r03_in_cella");
			// ------------------------------------------------ the player's scooter and the GTA radio
			case 326 -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
			case 330 -> server.execute(() -> {
				var p = PoliceTest.player(server);
				var city = PoliceTest.city(server);
				PoliceTest.tpNow(server, -30, 64, 54, -90, 0);
				mine = Napuli.MOTORINO.create(city, EntitySpawnReason.EVENT);
				mine.snapTo(-29.5, 64, 54.5, -90, 0);
				city.addFreshEntity(mine);
				p.getAbilities().flying = false;
				p.startRiding(mine, true, true);
				mine.setStation(0);
				PoliceTest.log("riding {} station {}", p.getVehicle() == mine, mine.station());
			});
			case 370 -> RadioClient.tune(2);
			case 390 -> {
				PoliceTest.shot(mc, "r04_banner_stazione");
				server.execute(() -> PoliceTest.log("station after tuning to 2: {}", mine.station()));
			}
			case 395 -> PoliceTest.log("radio while riding: {} | stations {}", RadioClient.debug(), RadioClient.stations().size());
			case 400 -> mc.gui.setScreen(new RadioWheelScreen());
			case 415 -> PoliceTest.shot(mc, "r05_ruota_radio");
			case 418 -> mc.gui.setScreen(new dev.napuli.client.music.SongsScreen(null));
			case 424 -> PoliceTest.shot(mc, "r05b_schermata_canzoni");
			case 428 -> mc.gui.setScreen(null);
			case 722 -> server.execute(() -> {
				// no helmet, a carabiniere right there: that's the CASCO crime for the player
				var p = PoliceTest.player(server);
				PoliceTest.tpNow(server, -30, 64, 54, -90, 0);
				p.startRiding(mine, true, true);
				NapoliResident.spawn(PoliceTest.city(server), NapoliResident.Role.CARABINIERE, p.blockPosition().offset(0, 0, -4), 0);
			});
			case 762 -> server.execute(() -> PoliceTest.log("player without helmet seen on the scooter -> wanted: {}", Carabinieri.isWanted(PoliceTest.player(server))));
			case 475 -> server.execute(() -> PoliceTest.player(server).stopRiding());
			case 485 -> server.execute(() -> PoliceTest.log("radio after getting off: station {}", mine.station()));
			case 490 -> PoliceTest.log("radio after getting off (client): {}", RadioClient.debug());
			// ------------------------------------------------ Vesuvius
			case 500 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); PoliceTest.run(server, "clear @a"); PoliceTest.tp(server, -34, 74, 48, -50, 4); }
			case 540 -> PoliceTest.shot(mc, "r06_vesuvio_da_partenope");
			case 545 -> PoliceTest.tp(server, 2, 78, 108, -50, 0);
			case 585 -> PoliceTest.shot(mc, "r07_vesuvio_dal_castello");
			case 590 -> PoliceTest.tp(server, 0, 150, 60, -45, 25);
			case 630 -> PoliceTest.shot(mc, "r08_vesuvio_aereo");
			case 635 -> PoliceTest.tp(server, 76, 122, 160, -45, 40);
			case 675 -> PoliceTest.shot(mc, "r09_cratere");
			case 680 -> PoliceTest.tp(server, 20, 80, 98, -50, 2);
			case 720 -> PoliceTest.shot(mc, "r10_pendici");
			case 775 -> { PoliceTest.log("done"); mc.stop(); }
			default -> {}
		}
	}
}
