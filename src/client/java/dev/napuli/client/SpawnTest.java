package dev.napuli.client;

import dev.napuli.NapoliMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Dev-only (-Dnapuli.spawntest=true): a brand new world must put you in Napoli; dying without a bed brings you back there. */
final class SpawnTest {
	private SpawnTest() {}
	private static int tick = -1, titleTicks;
	private static boolean started;

	static void register() {
		if (Boolean.getBoolean("napuli.spawntest")) ClientTickEvents.END_CLIENT_TICK.register(SpawnTest::onTick);
	}

	private static void onTick(Minecraft mc) {
		if (!started && mc.gui.screen() instanceof TitleScreen) {
			if (++titleTicks == 110) {
				started = true;
				var s = new LevelSettings("napoli-spawn", GameType.SURVIVAL, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
						true, WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows().createFreshLevel("napoli-spawn-" + System.currentTimeMillis(), s, SelectWorldScreen.TEST_OPTIONS,
						WorldPresets::createNormalWorldDimensions, new TitleScreen());
			}
			return;
		}
		if (mc.player == null || mc.getSingleplayerServer() == null) return;
		mc.options.pauseOnLostFocus = false;
		if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
		if (mc.gui.screen() instanceof DeathScreen) { mc.player.respawn(); mc.gui.setScreen(null); }
		var server = mc.getSingleplayerServer();
		tick++;
		switch (tick) {
			case 200 -> server.execute(() -> PoliceTest.log("S1 after joining a new world: dimension={} pos={}",
					PoliceTest.player(server).level().dimension().identifier(), PoliceTest.player(server).blockPosition()));
			case 210 -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
			case 230 -> PoliceTest.shot(mc, "s01_first_spawn");
			case 240 -> server.execute(() -> PoliceTest.run(server, "kill @p"));
			case 400 -> server.execute(() -> PoliceTest.log("S2 after dying (no bed): dimension={} pos={}",
					PoliceTest.player(server).level().dimension().identifier(), PoliceTest.player(server).blockPosition()));
			case 410 -> { PoliceTest.log("done"); mc.stop(); }
			default -> {}
		}
	}

	@SuppressWarnings("unused") private static NapoliMap unused;
}
