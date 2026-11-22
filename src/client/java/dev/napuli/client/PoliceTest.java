package dev.napuli.client;

import dev.napuli.Carabinieri;
import dev.napuli.NapoliMap;
import dev.napuli.NapoliResident;
import dev.napuli.NapoliResident.Role;
import dev.napuli.NapoliStreet;
import dev.napuli.Napuli;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Dev-only test (-Dnapuli.policetest=true): looks of every resident, furnished homes / pizzeria / caserma,
 * the weapons in hand, and the Carabinieri (fine, jail, release, escape). Screenshots + [POLTEST] log lines.
 */
final class PoliceTest {
	private PoliceTest() {}
	private static int tick = -1, titleTicks;
	private static boolean started;
	private static final List<NapoliResident> shown = new ArrayList<>();
	private static int cashBefore;
	private static double ccStartDist;
	private static net.minecraft.world.phys.Vec3 vendorStart;

	static void register() {
		if (Boolean.getBoolean("napuli.policetest")) ClientTickEvents.END_CLIENT_TICK.register(PoliceTest::onTick);
	}

	static void log(String f, Object... a) { Napuli.LOGGER.info("[POLTEST] " + f, a); }

	private static void onTick(Minecraft mc) {
		if (!started && mc.gui.screen() instanceof TitleScreen) {
			if (++titleTicks == 1) { mc.getLanguageManager().setSelected("nap_it"); mc.reloadResourcePacks(); }
			if (titleTicks == 110) {
				started = true;
				var s = new LevelSettings("napoli-polizia", GameType.SURVIVAL, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
						true, WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows().createFreshLevel("napoli-polizia-" + System.currentTimeMillis(), s, SelectWorldScreen.TEST_OPTIONS,
						WorldPresets::createTestWorldDimensions, new TitleScreen());
			}
			return;
		}
		if (mc.player == null || mc.getSingleplayerServer() == null) return;
		mc.options.pauseOnLostFocus = false;
		if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
		MinecraftServer server = mc.getSingleplayerServer();
		if (tick < 0) {
			if (++titleTicks == 200) server.execute(() -> NapoliMap.visit(player(server)));
			if (mc.player.level().dimension() == NapoliMap.LEVEL && titleTicks > 260) tick = 0;
			return;
		}
		tick++;
		switch (tick) {
			case 20 -> server.execute(() -> {
				run(server, "time set noon", "weather clear", "gamerule advance_time false", "gamerule spawn_mobs false",
						"gamerule spawn_monsters false", "effect give @p resistance infinite 4 true", "effect give @p night_vision infinite 0 true");
				log("carabinieri in city: {}", city(server).getEntities(Napuli.RESIDENT, r -> r.role() == Role.CARABINIERE).size());
			});
			case 30 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); }
			// ---- looks: three lineups of 10
			case 40 -> lineup(server, 0);
			case 70 -> shot(mc, "01_lineup_a");
			case 80 -> lineup(server, 1);
			case 110 -> shot(mc, "02_lineup_b");
			case 120 -> lineup(server, 2);
			case 150 -> shot(mc, "03_lineup_c");
			case 160 -> girls(server);
			case 190 -> shot(mc, "04_girls_close");
			case 200 -> server.execute(() -> clear());
			// ---- the city
			case 210 -> tp(server, -65, 64, -53, 180, 10);   // home (-70,-62): ground floor, looking north
			case 235 -> shot(mc, "05_home_ground");
			case 245 -> tp(server, 118, 64, -6, 180, -12);   // Galleria from the square in front
			case 285 -> shot(mc, "06_galleria_entrance");
			case 295 -> tp(server, 118, 64, -24, 180, -38);  // inside, looking up at the dome
			case 320 -> shot(mc, "07_galleria_dome");
			case 330 -> tp(server, 140, 64, -43, 180, 5);    // east arm: Rafele's shop on the north side
			case 355 -> shot(mc, "08_puteca");
			case 365 -> tp(server, 96, 64, -43, 180, 5);     // west arm: Pascà's tailor shop
			case 390 -> shot(mc, "09_sartoria");
			case 400 -> tp(server, 105, 64, 1, 0, -10);      // Comando Carabinieri from Via Toledo
			case 425 -> shot(mc, "10_comando");
			case 435 -> tp(server, 137, 64, 0, 0, -12);      // the prison walls and gate
			case 460 -> shot(mc, "11_carcere");
			case 470 -> tp(server, 136, 64, 41, 180, 0);     // cell block corridor
			case 495 -> shot(mc, "12_cellblock");
			case 505 -> tp(server, 61, 64, -4, 180, 5);      // old caserma -> market
			case 530 -> shot(mc, "13_mercato");
			case 540 -> tp(server, 31, 64, 3, 180, 0);       // old gallery -> piazzetta
			case 565 -> shot(mc, "14_piazzetta");
			case 575 -> tp(server, 118, 64, -30, 180, -5);   // the Galleria arm with shops and people
			case 600 -> shot(mc, "15_galleria_life");
			// ---- Carabinieri A: small theft, has money -> fine
			case 605 -> { mc.gui.screen(); }
			case 610 -> server.execute(() -> {
				ServerPlayer p = player(server);
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				NapoliStreet.credit(p, 60);
				cashBefore = NapoliStreet.cash(p);
				tpNow(server, 105, 64, 4, 0, 0);
				Carabinieri.report(p, Carabinieri.Crime.THEFT);
				ccStartDist = nearestCc(server, p);
				log("A: wanted={} cash={} nearest carabiniere={}", Carabinieri.isWanted(p), cashBefore, (int) ccStartDist);
			});
			case 640, 670, 700 -> server.execute(() -> log("A: t={} nearest carabiniere={} wanted={}", tick, (int) nearestCc(server, player(server)), Carabinieri.isWanted(player(server))));
			case 650 -> shot(mc, "15_carabinieri_coming");
			case 760 -> server.execute(() -> {
				ServerPlayer p = player(server);
				log("A result: wanted={} jailed={} cash {} -> {}", Carabinieri.isWanted(p), Carabinieri.isJailed(p), cashBefore, NapoliStreet.cash(p));
			});
			// ---- B: shooting -> jail, weapons taken
			case 780 -> server.execute(() -> {
				ServerPlayer p = player(server);
				p.getInventory().add(new ItemStack(Napuli.PISTOL));
				p.getInventory().add(new ItemStack(Napuli.KNIFE));
				tpNow(server, 105, 64, 4, 0, 0);
				Carabinieri.report(p, Carabinieri.Crime.SHOOTING);
			});
			case 960 -> server.execute(() -> {
				ServerPlayer p = player(server);
				log("B result: jailed={} gamemode={} pos={} has pistol={} has knife={}", Carabinieri.isJailed(p), p.gameMode(), p.blockPosition(),
						p.getInventory().contains(new ItemStack(Napuli.PISTOL)), p.getInventory().contains(new ItemStack(Napuli.KNIFE)));
			});
			case 970 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
			case 990 -> shot(mc, "16_in_cella");
			case 1000 -> server.execute(() -> {
				ServerPlayer p = player(server);
				tpNow(server, 62, 64, -10, 0, 0); // try to walk out: must be put back
			});
			case 1010 -> server.execute(() -> log("B escape attempt: pos={} (cell is near 57/67,-26)", player(server).blockPosition()));
			case 1020 -> server.execute(() -> {
				ServerPlayer p = player(server);
				boolean ok = Carabinieri.release(p);
				log("B release: ok={} jailed={} gamemode={} pos={}", ok, Carabinieri.isJailed(p), p.gameMode(), p.blockPosition());
			});
			// ---- C: run away and hide
			case 1040 -> server.execute(() -> { Carabinieri.report(player(server), Carabinieri.Crime.THEFT); tpNow(server, -60, 64, 40, 0, 0); });
			case 1500 -> server.execute(() -> log("C after 23 s far away: wanted={}", Carabinieri.isWanted(player(server))));
			// ---- the scooter boys: their song must stop when they vanish
			case 1520 -> server.execute(() -> {
				ServerPlayer p = player(server);
				tpNow(server, 0, 64, -10, 180, 0);
				dev.napuli.Music.stop(p);
				int ok = dev.napuli.NapoliScenes.motorino(p);
				log("D motorino started={} music now={}", ok, dev.napuli.Music.current(p));
			});
			case 1740 -> server.execute(() -> log("D after the parade: motorini left={} music now={}",
					city(server).getEntities(Napuli.MOTORINO, e -> true).size(), dev.napuli.Music.current(player(server))));
			// ---- shopkeepers walk around their shop
			case 1750 -> server.execute(() -> {
				tpNow(server, 140, 64, -43, 180, 5);
				var v = city(server).getEntities(Napuli.RESIDENT, r -> r.role() == Role.VENDOR);
				vendorStart = v.isEmpty() ? null : v.getFirst().position();
				log("E vendor at {}", vendorStart);
			});
			case 1990 -> server.execute(() -> {
				var v = city(server).getEntities(Napuli.RESIDENT, r -> r.role() == Role.VENDOR);
				if (vendorStart != null && !v.isEmpty()) log("E vendor moved {} blocks in 12 s, now {} from his shop", String.format("%.1f", v.getFirst().position().distanceTo(vendorStart)),
						String.format("%.1f", Math.sqrt(v.getFirst().blockPosition().distSqr(dev.napuli.NapoliDistrict.PUTECA))));
			});
			// ---- weapons in hand: pistol first person (barrel forward), baton held by a carabiniere
			case 2000 -> server.execute(() -> { tpNow(server, 106, 64, 3, 0, 0); player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Napuli.PISTOL)); });
			case 2010 -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
			case 2030 -> shot(mc, "16_pistol_firstperson");
			case 2040 -> server.execute(() -> {
				ServerLevel c = city(server);
				NapoliResident cc = NapoliResident.spawn(c, Role.CARABINIERE, new BlockPos(106, 64, 6), 180);
				cc.setNoAi(true);
				setVariant(cc, 1);
				shown.add(cc);
				player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Napuli.BATON));
			});
			case 2060 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			case 2080 -> shot(mc, "17_baton_in_hand");
			case 2090 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); log("done"); mc.stop(); }
			default -> {}
		}
	}

	// ---------------------------------------------------------------- helpers
	record LookEntry(Role role, int variant) {}

	static List<LookEntry> allLooks() {
		List<LookEntry> out = new ArrayList<>();
		for (Role r : Role.values()) for (int v = 0; v < r.variants(); v++) out.add(new LookEntry(r, v));
		return out;
	}

	private static void lineup(MinecraftServer server, int group) {
		server.execute(() -> {
			clear();
			ServerLevel city = city(server);
			List<LookEntry> all = allLooks();
			int from = group * 10, to = Math.min(all.size(), from + 10);
			for (int i = from; i < to; i++) {
				LookEntry e = all.get(i);
				int x = -9 + (i - from) * 2;
				NapoliResident npc = NapoliResident.spawn(city, e.role(), new BlockPos(x, 64, -50), 180);
				npc.setNoAi(true);
				setVariant(npc, e.variant());
				shown.add(npc);
			}
			tpNow(server, 0, 64, -63, 0, 0);
			log("lineup {}: {}", group, all.subList(from, to));
		});
	}

	private static void girls(MinecraftServer server) {
		server.execute(() -> {
			clear();
			ServerLevel city = city(server);
			Role[] girls = {Role.TERESA, Role.LUCIA, Role.CARMELA, Role.GIUSY, Role.SOFIA};
			for (int i = 0; i < girls.length; i++) {
				NapoliResident npc = NapoliResident.spawn(city, girls[i], new BlockPos(-4 + i * 2, 64, -54), 180);
				npc.setNoAi(true);
				setVariant(npc, 0);
				shown.add(npc);
			}
			NapoliResident cc = NapoliResident.spawn(city, Role.CARABINIERE, new BlockPos(7, 64, -57), 180);
			cc.setNoAi(true);
			setVariant(cc, 0);
			shown.add(cc);
			tpNow(server, 1, 64, -60, 0, 8);
		});
	}

	private static void setVariant(NapoliResident npc, int v) {
		// the variant is synced data; set it through a save/load round trip so we don't need a public setter
		var out = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
		npc.saveWithoutId(out);
		var tag = out.buildResult();
		tag.putInt("napuli_variant", v);
		npc.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, npc.registryAccess(), tag));
	}

	private static void clear() { for (NapoliResident n : shown) n.discard(); shown.clear(); }

	private static double nearestCc(MinecraftServer server, ServerPlayer p) {
		double best = 999;
		for (NapoliResident r : city(server).getEntities(Napuli.RESIDENT, r -> r.role() == Role.CARABINIERE))
			best = Math.min(best, r.distanceTo(p));
		return best;
	}

	static ServerLevel city(MinecraftServer s) { return s.getLevel(NapoliMap.LEVEL); }
	static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	static void tp(MinecraftServer server, double x, double y, double z, float yaw, float pitch) { server.execute(() -> tpNow(server, x, y, z, yaw, pitch)); }
	static void tpNow(MinecraftServer server, double x, double y, double z, float yaw, float pitch) {
		player(server).teleportTo(city(server), x + 0.5, y, z + 0.5, Set.of(), yaw, pitch, true);
	}

	static void run(MinecraftServer server, String... cmds) {
		for (String c : cmds) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
	}

	static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, m -> log("screenshot {}", name));
	}
}
