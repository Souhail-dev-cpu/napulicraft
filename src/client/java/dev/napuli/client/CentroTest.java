package dev.napuli.client;

import dev.napuli.NapoliFactions;
import dev.napuli.NapoliMap;
import dev.napuli.NapoliResident;
import dev.napuli.NapoliStreet;
import dev.napuli.Napuli;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Dev-only (-Dnapuli.centrotest=true): old Galleria cleanup, HUD + mission tracker, creative tab, new people, centro storico. */
final class CentroTest {
	private CentroTest() {}
	private static int tick = -1, titleTicks;
	private static boolean started;
	private static final List<NapoliResident> lineup = new ArrayList<>();

	static void register() {
		if (Boolean.getBoolean("napuli.centrotest")) ClientTickEvents.END_CLIENT_TICK.register(CentroTest::onTick);
	}

	private static void onTick(Minecraft mc) {
		if (!started && mc.gui.screen() instanceof TitleScreen) {
			if (++titleTicks == 110) {
				started = true;
				var s = new LevelSettings("napoli-centro", GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
						true, WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows().createFreshLevel("napoli-centro-" + System.currentTimeMillis(), s, SelectWorldScreen.TEST_OPTIONS,
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
		if (tick > 20 && mc.gui.screen() == null) mc.player.getAbilities().flying = true;
		switch (++tick) {
			case 10 -> server.execute(() -> PoliceTest.run(server, "time set 6000", "weather clear", "gamerule advance_time false"));
			case 20 -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.player.getAbilities().flying = true; }
			// ---- 1. the spot of the user's screenshot: no more floating sandstone and glass
			case 25 -> server.execute(() -> {
				var city = PoliceTest.city(server);
				int left = 0;
				java.util.Set<Block> gallery = java.util.Set.of(net.minecraft.world.level.block.Blocks.SMOOTH_SANDSTONE,
						net.minecraft.world.level.block.Blocks.CHISELED_QUARTZ_BLOCK, block("brown_stained_glass"), block("light_blue_stained_glass"),
						net.minecraft.world.level.block.Blocks.IRON_BLOCK, net.minecraft.world.level.block.Blocks.GLASS);
				for (int x = 12; x <= 51; x++) for (int z = -24; z <= 8; z++) for (int y = 64; y <= 96; y++) {
					if (x >= 30 && x <= 48 && z >= 5) continue;
					if (gallery.contains(city.getBlockState(new BlockPos(x, y, z)).getBlock())) left++;
				}
				PoliceTest.log("old Galleria blocks still standing: {}", left);
			});
			case 30 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); PoliceTest.tp(server, 44, 67, 1, 50, 2); }
			case 65 -> PoliceTest.shot(mc, "v01_dove_cera_larco");
			// ---- 2. HUD: in the Lanterne clan, on a mission, wanted
			case 70 -> server.execute(() -> {
				var p = PoliceTest.player(server);
				NapoliFactions.debugSet(p, 27, -6, 0, 1);       // Lanterne 27 (Soldato), supply run to Michele
				NapoliStreet.credit(p, 117);
				PoliceTest.tpNow(server, -30, 64, -10, 120, 0);
			});
			case 75 -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
			case 105 -> PoliceTest.shot(mc, "v02_hud_missione");
			case 106 -> PoliceTest.log("hud state: {}", HudClient.state());
			case 110 -> server.execute(() -> NapoliFactions.debugSet(PoliceTest.player(server), 2, 0, -1, 0));
			case 135 -> PoliceTest.shot(mc, "v03_hud_senza_clan");
			// ---- 3. the creative tab
			case 140 -> mc.gui.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true));
			case 145 -> {
				var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Napuli.id("napulicraft")));
				if (mc.gui.screen() instanceof CreativeModeInventoryScreen cs) {
					var f = (FabricCreativeModeInventoryScreen) cs;
					f.switchToPage(f.getPage(tab));
					PoliceTest.log("creative tab selected: {}", f.setSelectedTab(tab));
				}
			}
			case 160 -> PoliceTest.shot(mc, "v04_tab_creativa");
			case 165 -> mc.gui.setScreen(null);
			// ---- 4. the new people, lined up
			case 170 -> server.execute(() -> {
				var city = PoliceTest.city(server);
				NapoliResident.Role[] roles = {NapoliResident.Role.NONNA, NapoliResident.Role.PARCHEGGIATORE, NapoliResident.Role.TIFOSO,
						NapoliResident.Role.SCUGNIZZO, NapoliResident.Role.PRESEPAIO, NapoliResident.Role.PIZZAIOLO};
				for (int i = 0; i < roles.length; i++) {
					var npc = NapoliResident.spawn(city, roles[i], new BlockPos(-15 + i * 2, 64, -88), 180);
					npc.setNoAi(true);
					lineup.add(npc);
				}
				PoliceTest.tpNow(server, -10, 65.2, -82, 180, 8);
			});
			case 175 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
			case 205 -> PoliceTest.shot(mc, "v05_nuovi_napoletani");
			case 210 -> server.execute(() -> { lineup.forEach(n -> n.discard()); lineup.clear(); });
			// ---- 5. the centro storico
			case 215 -> PoliceTest.tp(server, -40, 67, -71, 180, -8);    // Piazza del Gesù from the south
			case 255 -> PoliceTest.shot(mc, "v06_piazza_gesu");
			case 260 -> PoliceTest.tp(server, -36, 66, -95, 180, -10);  // close to the diamond-point façade
			case 295 -> PoliceTest.shot(mc, "v07_facciata_gesu");
			case 300 -> PoliceTest.tp(server, -52, 66, -125, -90, 4);   // down Spaccanapoli, eastwards
			case 335 -> PoliceTest.shot(mc, "v08_spaccanapoli");
			case 340 -> PoliceTest.tp(server, 30, 66, -129, 180, 4);    // up San Gregorio Armeno
			case 375 -> PoliceTest.shot(mc, "v09_san_gregorio");
			case 380 -> PoliceTest.tp(server, -52, 75, -126, 90, -2);    // Largo Maradona, facing the mural
			case 415 -> PoliceTest.shot(mc, "v10_largo_maradona");
			case 420 -> PoliceTest.tp(server, 38, 70, -70, 180, 18);   // into Totonno's pizzeria
			case 455 -> PoliceTest.shot(mc, "v11_piazza_bellini");
			case 460 -> PoliceTest.tp(server, 0, 130, -40, 180, 40);    // the whole district from above
			case 500 -> PoliceTest.shot(mc, "v12_aerea_centro");
			case 505 -> server.execute(() -> {
				var city = PoliceTest.city(server);
				var p = PoliceTest.player(server);
				PoliceTest.tpNow(server, 0, 64, -125, 0, 0);
			});
			case 560 -> server.execute(() -> {
				var city = PoliceTest.city(server);
				var people = city.getEntitiesOfClass(NapoliResident.class, PoliceTest.player(server).getBoundingBox().inflate(70),
						n -> n.role().people() || n.role() == NapoliResident.Role.PASSERBY);
				PoliceTest.log("people around the centro: {} ({})", people.size(),
						people.stream().map(n -> n.role().id()).sorted().toList());
			});
			case 570 -> { PoliceTest.log("done"); mc.stop(); }
			default -> {}
		}
	}

	private static Block block(String n) { return BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(n)); }
}
