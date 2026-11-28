package dev.napuli.client;

import dev.napuli.Carabinieri;
import dev.napuli.Motorino;
import dev.napuli.Napuli;
import dev.napuli.NapoliMap;
import dev.napuli.NapoliResident;
import dev.napuli.NapoliScenes;
import dev.napuli.Radio;
import dev.napuli.RadioTalk;
import dev.napuli.client.music.RadioStations;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.sounds.SoundInstance;
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
	private static dev.napuli.client.music.UserMusic.FileSound interruptedScene;
	private static final List<SoundInstance> callSounds = new ArrayList<>();
	private static long firstCue = RadioTalk.NO_CALL, beforeSkip = RadioTalk.NO_CALL;
	private static int skipAt, offAt;
	private static boolean showedBanner;

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
		if (Boolean.getBoolean("napuli.talkRadioTest")) {
			testTalk(mc, server);
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
			case 30 -> { dev.napuli.client.music.UserMusic.stopScene(); PoliceTest.tp(server, 0, 65, -5, -20, 12); if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
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
			case 428 -> {
				mc.gui.setScreen(null);
				dev.napuli.client.music.UserMusic.stopScene();
				int station = java.util.stream.IntStream.range(0, RadioClient.stations().size())
						.filter(i -> RadioClient.stations().get(i).songs().size() >= 2).findFirst()
						.orElseThrow(() -> new IllegalStateException("Radio test requires a station with two tracks"));
				RadioClient.tune(station);
			}
			case 445 -> {
				int station = RadioClient.currentStation();
				int before = RadioClient.currentTrack();
				if (station < 0 || before < 0) throw new IllegalStateException("Radio is not playing");
				var wheel = new RadioWheelScreen();
				mc.gui.setScreen(wheel);
				double angle = -Math.PI / 2 + (station + 1) * 2 * Math.PI / (RadioClient.stations().size() + 1);
				var click = new net.minecraft.client.input.MouseButtonEvent(wheel.width / 2.0 + 50 * Math.cos(angle),
						wheel.height / 2.0 + 50 * Math.sin(angle), new net.minecraft.client.input.MouseButtonInfo(0, 0));
				wheel.mouseClicked(click, false);
				if (mc.gui.screen() != wheel || RadioClient.currentTrack() != before) throw new IllegalStateException("First click must keep playing");
				wheel.mouseClicked(click, true);
				int expected = (before + 1) % RadioClient.stations().get(station).songs().size();
				if (RadioClient.currentTrack() != expected || RadioClient.currentStation() != station)
					throw new IllegalStateException("Double click did not advance within the station");
				PoliceTest.log("double click advances the current station: true");
				mc.gui.setScreen(null);
			}
			case 722 -> server.execute(() -> {
				// no helmet, a carabiniere right there: that's the CASCO crime for the player
				var p = PoliceTest.player(server);
				PoliceTest.tpNow(server, -30, 64, 54, -90, 0);
				p.startRiding(mine, true, true);
				NapoliResident.spawn(PoliceTest.city(server), NapoliResident.Role.CARABINIERE, p.blockPosition().offset(0, 0, -4), 0);
			});
			case 762 -> server.execute(() -> PoliceTest.log("player without helmet seen on the scooter -> wanted: {}", Carabinieri.isWanted(PoliceTest.player(server))));
			case 450 -> {
				if (!RadioClient.debug().contains("active true")) throw new IllegalStateException("Radio must be playing before the encounter");
				var song = dev.napuli.client.music.UserMusic.library().getFirst();
				interruptedScene = dev.napuli.client.music.UserMusic.preview(song, 0);
			}
			case 458 -> dev.napuli.client.music.UserMusic.playSituation("urlo", 1);
			case 463 -> {
				if (!interruptedScene.isStopped() || !dev.napuli.client.music.UserMusic.scenePlaying()
						|| RadioClient.debug().contains("active true"))
					throw new IllegalStateException("The Giant's clip must replace the previous scene and suspend the radio");
				PoliceTest.log("giant clip plays alone: true");
			}
			case 525 -> server.execute(() -> PoliceTest.player(server).stopRiding());
			case 535 -> server.execute(() -> PoliceTest.log("radio after getting off: station {}", mine.station()));
			case 513 -> {
				if (dev.napuli.client.music.UserMusic.scenePlaying() || !RadioClient.debug().contains("active true"))
					throw new IllegalStateException("The radio must return after the encounter clip: "
							+ dev.napuli.client.music.UserMusic.sceneDebug() + " / " + RadioClient.debug());
				PoliceTest.log("radio returns after encounter: true");
			}
			// ------------------------------------------------ Vesuvius
			case 536 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); PoliceTest.run(server, "clear @a"); PoliceTest.tp(server, -34, 74, 48, -50, 4); }
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

	private static void testTalk(Minecraft mc, MinecraftServer server) {
		if (++tick == 1) {
			mc.getSoundManager().addListener((sound, weighted, range) -> {
				var id = sound.getIdentifier();
				if ("napuli".equals(id.getNamespace())
						&& (id.getPath().startsWith("radio_") || id.getPath().startsWith("vocale_")))
					callSounds.add(sound);
			});
			server.execute(() -> {
				var p = PoliceTest.player(server);
				var city = PoliceTest.city(server);
				PoliceTest.run(server, "time set noon");
				PoliceTest.tpNow(server, -30, 64, 54, -90, 0);
				mine = Napuli.MOTORINO.create(city, EntitySpawnReason.EVENT);
				verifyProgramme(city);
				mine.snapTo(-29.5, 64, 54.5, -90, 0);
				city.addFreshEntity(mine);
				p.getAbilities().flying = false;
				p.startRiding(mine, true, true);
			});
		}
		if (tick == 100) {
			var wheel = new RadioWheelScreen();
			mc.gui.setScreen(wheel);
			var click = talkSlot(wheel);
			wheel.mouseClicked(click, false);
		}
		if (tick == 122) {
			PoliceTest.shot(mc, "radio-vicoli-ruota");
			mc.gui.setScreen(null);
		}
		if (tick < 125) return;
		require(mc.player.getVehicle() instanceof Motorino, "Player is still mounted");
		var bike = (Motorino) mc.player.getVehicle();
		if (tick == 125) {
			firstCue = bike.talkCue();
			require(bike.station() == Radio.TALK && RadioTalk.index(firstCue) >= 0,
					"Selecting Radio Vicoli tunes the server station and chooses a caller");
		}
		if (!showedBanner && callSounds.size() == 1) {
			showedBanner = true;
			PoliceTest.shot(mc, "radio-vicoli-in-diretta");
		}
		if (callSounds.size() >= 4 && skipAt == 0) {
			var first = RadioTalk.CALLS.get(RadioTalk.index(firstCue));
			var expected = List.of(first.intro(), first.voice(), first.reply());
			require(callSounds.subList(0, 3).stream().map(SoundInstance::getIdentifier).toList().equals(expected),
					"The client hears the announcement, supplied voice, and reply in order");
			for (var sound : callSounds.subList(0,3))
				require(!mc.getSoundManager().isActive(sound), "The previous call has stopped");
			beforeSkip = bike.talkCue();
			require(RadioTalk.index(beforeSkip) != RadioTalk.index(firstCue), "The radio chooses another caller");
			require(callSounds.get(3).getIdentifier().equals(RadioTalk.CALLS.get(RadioTalk.index(beforeSkip)).intro()),
					"The next call plays its own announcement");
			var wheel = new RadioWheelScreen();
			mc.gui.setScreen(wheel);
			var click = talkSlot(wheel);
			wheel.mouseClicked(click, false);
			require(bike.talkCue() == beforeSkip, "A single click keeps the current call");
			wheel.mouseClicked(click, true);
			mc.gui.setScreen(null);
			skipAt = tick;
		}
		if (skipAt > 0 && offAt == 0 && tick - skipAt >= 25) {
			require(bike.station() == Radio.TALK && RadioTalk.index(bike.talkCue()) != RadioTalk.index(beforeSkip),
					"Double-click skips to a new caller without leaving the station");
			require(!mc.getSoundManager().isActive(callSounds.get(3)), "Skipping stops the previous announcement");
			require(callSounds.size() >= 5, "The skipped-to caller is audible");
			RadioClient.tune(Radio.OFF);
			offAt = tick;
		}
		if (offAt > 0 && tick - offAt >= 25) {
			require(bike.station() == Radio.OFF, "Switching off updates the server");
			require(callSounds.stream().noneMatch(mc.getSoundManager()::isActive), "Switching off silences the broadcast");
			Napuli.LOGGER.info("[TALKRADIO] ALL PASSED: wheel, ordered broadcast, new caller, skip and silence");
			mc.stop();
		}
		require(tick < 1600, "Radio Vicoli test timed out");
	}

	private static void verifyProgramme(net.minecraft.server.level.ServerLevel city) {
		var radio = Napuli.MOTORINO.create(city, EntitySpawnReason.EVENT);
		var calls = RadioTalk.CALLS;
		for (String source : List.of("trash5_", "trash8_", "messaggi_")) {
			require(calls.stream().anyMatch(call -> call.voice().getPath().startsWith("radio_" + source)),
					"The programme includes " + source);
		}
		var gennaro = calls.stream().filter(call -> call.caller().equals("Gennaro")).findFirst().orElseThrow();
		var signora = calls.stream().filter(call -> call.caller().equals("La signora al telefono")).findFirst().orElseThrow();
		require(gennaro.district().equals("Palazzo Reale") && gennaro.voice().equals(Napuli.id("radio_trash5_26"))
						&& signora.voice().equals(Napuli.id("radio_trash5_25")) && !gennaro.intro().equals(signora.intro()),
				"Gennaro and the woman have separate introductions and recordings");
		var seen = new java.util.HashSet<Integer>();
		int previous = -1;
		for (int i = 0; i <= calls.size(); i++) {
			radio.setStation(Radio.TALK);
			int current = RadioTalk.index(radio.talkCue());
			require(current != previous, "The end of a shuffle does not repeat the last vocal");
			if (i < calls.size()) require(seen.add(current), "Each clip plays once per rotation");
			previous = current;
			radio.setStation(Radio.OFF);
		}
		Napuli.LOGGER.info("[TALKRADIO] verified {} calls across all three compilations", seen.size());
	}

	private static MouseButtonEvent talkSlot(RadioWheelScreen wheel) {
		int index = RadioStations.indexOf(Radio.TALK);
		double angle = -Math.PI / 2 + (index + 1) * 2 * Math.PI / (RadioClient.stations().size() + 1);
		return new MouseButtonEvent(wheel.width / 2.0 + 50 * Math.cos(angle),
				wheel.height / 2.0 + 50 * Math.sin(angle), new MouseButtonInfo(0, 0));
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new IllegalStateException(message);
	}
}
