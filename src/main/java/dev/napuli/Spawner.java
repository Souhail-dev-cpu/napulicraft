package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/** Our own spawn logic: Munaciello at night or in the dark, the giant when you made him angry, random songs. */
public final class Spawner {
	private Spawner() {}

	private static int nextSong = 20 * 60 * 3;

	public static void tick(MinecraftServer server) {
		// a random neomelodic song for everyone every 8-16 minutes
		if (--nextSong <= 0) {
			nextSong = 20 * 60 * (8 + server.overworld().getRandom().nextInt(9));
			for (ServerPlayer player : server.getPlayerList().getPlayers())
				if (player.level().dimension() == NapoliMap.LEVEL) Music.play(player, Music.Situation.RADIO);
		}
		if (server.getTickCount() % 600 != 0) return; // every 30 s
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || p.isCreative() || p.level().dimension() != NapoliMap.LEVEL) continue;
			ServerLevel level = p.level();
			RandomSource r = level.getRandom();
			boolean dark = !level.isBrightOutside() || level.getMaxLocalRawBrightness(p.blockPosition()) < 6;
			int raggia = p.getAttachedOrElse(Napuli.RAGGIA, 0);
			if (raggia >= 3 && !level.isBrightOutside() && level.getEntities(Napuli.GIGANTE, e -> true).isEmpty()) {
				if (spawnGigante(level, p)) p.setAttached(Napuli.RAGGIA, 0);
				continue;
			}
			if (dark && r.nextFloat() < 0.08f && level.getEntities(Napuli.MUNACIELLO, e -> e.distanceToSqr(p) < 64 * 64).isEmpty())
				spawnMunaciello(level, p);
		}
	}

	public static void playSong(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers())
			Music.play(p, Music.Situation.RADIO);
	}

	public static void addRaggia(ServerLevel level, Player p) {
		int v = p.getAttachedOrElse(Napuli.RAGGIA, 0) + 1;
		p.setAttached(Napuli.RAGGIA, v);
		NapoliNotice.show(p, Component.translatable(v >= 3 ? "napuli.raggia.max" : "napuli.raggia." + v));
	}

	public static boolean spawnMunaciello(ServerLevel level, Player p) {
		BlockPos pos = groundNear(level, p, 10, 18);
		if (pos == null) return false;
		Munaciello m = Napuli.MUNACIELLO.create(level, EntitySpawnReason.EVENT);
		if (m == null) return false;
		m.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		return level.addFreshEntity(m);
	}

	public static boolean spawnGigante(ServerLevel level, Player p) {
		BlockPos pos = groundNear(level, p, 28, 44, 7);
		if (pos == null) return false;
		MunacielloGigante g = Napuli.GIGANTE.create(level, EntitySpawnReason.EVENT);
		if (g == null) return false;
		g.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		g.setPrey(p);
		if (!level.noCollision(g) || !level.addFreshEntity(g)) return false;
		// the warning: a song starts, from nowhere
		if (p instanceof ServerPlayer sp) Music.play(sp, Music.Situation.GIGANTE);
		return true;
	}

	/** A spot on the surface/floor between min and max blocks from the player (tries a few angles). */
	static BlockPos groundNear(ServerLevel level, Player p, int min, int max) {
		return groundNear(level, p, min, max, 2);
	}

	private static BlockPos groundNear(ServerLevel level, Player p, int min, int max, int clearance) {
		RandomSource r = level.getRandom();
		for (int tries = 0; tries < 24; tries++) {
			double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			// underground: search near the player's height; outside: surface
			BlockPos start = new BlockPos(x, (int) p.getY() + 6, z);
			for (int dy = 0; dy < 14; dy++) {
				BlockPos feet = start.below(dy);
				if (clearColumn(level, feet, clearance)
						&& level.getBlockState(feet.below()).isSolidRender()) return feet;
			}
			if (level.canSeeSky(p.blockPosition())) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
				if (clearColumn(level, top, clearance) && level.getBlockState(top.below()).isSolidRender()) return top;
			}
		}
		return null;
	}

	private static boolean clearColumn(ServerLevel level, BlockPos feet, int clearance) {
		for (int i = 0; i < clearance; i++) if (!level.getBlockState(feet.above(i)).isAir()) return false;
		return true;
	}
}
