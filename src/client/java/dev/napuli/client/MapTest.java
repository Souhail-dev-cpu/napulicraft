package dev.napuli.client;

import dev.napuli.NapoliMap;
import dev.napuli.Napuli;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Dev-only (-Dnapuli.maptest=true): the rebuilt Plebiscito and town details, plus "no zombies in the city". */
final class MapTest {
	private MapTest() {}
	private static int tick = -1, titleTicks;
	private static boolean started;
	private static net.minecraft.world.entity.Entity zombie;

	static void register() {
		if (Boolean.getBoolean("napuli.maptest")) ClientTickEvents.END_CLIENT_TICK.register(MapTest::onTick);
	}

	private static void onTick(Minecraft mc) {
		if (!started && mc.gui.screen() instanceof TitleScreen) {
			if (++titleTicks == 110) {
				started = true;
				var s = new LevelSettings("napoli-mappa", GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
						true, WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows().createFreshLevel("napoli-mappa-" + System.currentTimeMillis(), s, SelectWorldScreen.TEST_OPTIONS,
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
		if (tick > 20) mc.player.getAbilities().flying = true; // landing on a terrace turns creative flight off
		switch (++tick) {
			case 10 -> server.execute(() -> PoliceTest.run(server, "time set 6000", "weather clear", "gamerule advance_time false"));
			case 20 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.player.getAbilities().flying = true; }
			case 30 -> PoliceTest.tp(server, -34, 74, 48, -34, 6);
			case 65 -> PoliceTest.shot(mc, "c01_castello_da_partenope");
			case 70 -> PoliceTest.tp(server, 0, 125, 40, 0, 42);
			case 105 -> PoliceTest.shot(mc, "c02_castello_aereo");
			case 110 -> PoliceTest.tp(server, 0, 66, 68, 0, -4);
			case 145 -> PoliceTest.shot(mc, "c03_porta_dal_ponte");
			case 150 -> PoliceTest.tp(server, -1, 67, 89, 0, -12);
			case 185 -> PoliceTest.shot(mc, "c04_cortile_rampa");
			case 190 -> PoliceTest.tp(server, 2, 77, 107, -35, 6);
			case 225 -> PoliceTest.shot(mc, "c05_terrazza_cannoni");
			case 230 -> PoliceTest.tp(server, 30, 69, 61, 0, 8);
			case 265 -> PoliceTest.shot(mc, "c06_borgo_dal_porto");
			case 270 -> PoliceTest.tp(server, 20, 65, 75, -90, 2);
			case 305 -> PoliceTest.shot(mc, "c07_borgo_banchina");
			case 310 -> PoliceTest.tp(server, -52, 76, 100, -90, 6);
			case 345 -> PoliceTest.shot(mc, "c08_mura_dal_mare");
			case 350 -> PoliceTest.tp(server, -15, 96, 104, -120, 20);
			case 385 -> PoliceTest.shot(mc, "c09_torre_maestra");
			case 390 -> PoliceTest.tp(server, 30, 66, 76, 0, -28);
			case 425 -> PoliceTest.shot(mc, "c10_balconi");
			case 440 -> server.execute(() -> {
				var city = PoliceTest.city(server);
				zombie = EntityTypes.ZOMBIE.create(city, EntitySpawnReason.NATURAL);
				zombie.snapTo(36, 64, 20, 0, 0);
				city.addFreshEntity(zombie);
			});
			case 445 -> server.execute(() -> {
				var city = PoliceTest.city(server);
				PoliceTest.log("zombie in the city still there: {}; monsters left in the city: {}", zombie.isAlive() && !zombie.isRemoved(),
						city.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.monster.Monster.class),
								m -> !"napuli".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getNamespace())).size());
			});
			case 460 -> { PoliceTest.log("done"); mc.stop(); }
			default -> {}
		}
	}

	@SuppressWarnings("unused") private static Napuli unused;
}
