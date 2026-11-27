package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * City upgrade v7: Vesuvius as you see it from Naples (it was a green four-sided pyramid).
 *  - the Gran Cono: a concave, truncated cone with a real crater bowl at the top (and a little fumarole smoke)
 *  - Monte Somma: the old caldera rim wrapping the cone on the north side - from the city it's the second, lower
 *    summit on the LEFT - with the Valle del Gigante between the two
 *  - green lower slopes with umbrella pines and vineyards, scrub higher up, bare grey-brown ash on the cone,
 *    dark lava flows down the flank facing the city, little white towns along the foot by the sea
 */
final class NapoliVesuvio {
	static final int CX = 86, CZ = 170;            // further out in the gulf than the old one, clear of the castle
	private static final int RIM = 11, BASE = 62, SOMMA_R = 38; // wide and low like the real one (~1:5)
	private static final double SOMMA_DIR = Math.atan2(-0.81, 0.58); // left of the line of sight from the city
	private final ServerLevel world;

	private NapoliVesuvio(ServerLevel world) { this.world = world; }

	static void build(ServerLevel world) {
		Napuli.LOGGER.info("Napoli v7: rebuilding Vesuvius (Gran Cono, crater, Monte Somma)");
		NapoliVesuvio v = new NapoliVesuvio(world);
		v.clearOld();
		v.mountain();
		v.crater();
		Napuli.LOGGER.info("Napoli v7: Vesuvius done");
	}

	private void clearOld() {
		// the old pyramid: x 20..94, z 119..152, from the water up
		fill(18, 64, 117, 96, 101, 154, Blocks.AIR);
	}

	// ---------------------------------------------------------------- the shape
	/** angular weight of the Somma rim: 1 in the middle of the arc, fading to 0 at its ends (about +-75 degrees) */
	private static double sommaArc(double angle) {
		double d = Math.abs(Math.atan2(Math.sin(angle - SOMMA_DIR), Math.cos(angle - SOMMA_DIR)));
		double span = Math.toRadians(78);
		if (d >= span) return 0;
		return Math.pow(Math.cos(d / span * Math.PI / 2), 0.7);
	}

	static double height(int x, int z) {
		double dx = x - CX, dz = z - CZ, r = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
		double rr = Math.hypot(x - CX, z - CZ);
		double n = (smooth(x, z, 9) * 2.2 + smooth(x, z, 4) * 0.8) * Math.min(1, rr / 24.0); // gentle lumps, a clean rim
		// Gran Cono: concave flanks up to the rim at y ~118
		double cone;
		if (r <= RIM) cone = 112;                                                     // truncated top
		else if (r <= BASE) cone = 64 + 48 * Math.pow((BASE - r) / (double) (BASE - RIM), 1.3);
		else cone = 64 - (r - BASE) * 0.6;
		// gullies running down the cone
		if (r > RIM + 2 && r < BASE) cone -= Math.max(0, Math.sin(a * 9) * 1.2) * (r / BASE);
		// Monte Somma: steep inner wall over the Valle del Gigante, long outer slope; the "Nasone" a bit off centre
		double w = sommaArc(a), somma = 0;
		if (w > 0) {
			double peak = 64 + 37 * w + 4 * Math.exp(-Math.pow((a - SOMMA_DIR - 0.35) / 0.3, 2)); // ~90% of the cone, like 1131 m vs 1281 m
			if (r <= SOMMA_R) somma = Math.max(77, peak - (SOMMA_R - r) * 4.0);  // caldera wall down to the valley floor
			else somma = peak - Math.pow((r - SOMMA_R) / 32.0, 1.25) * (peak - 60);
			if (r < SOMMA_R - 8) somma = 0;                                        // inside: the cone takes over
		}
		return Math.max(cone, somma) + n;
	}

	private void mountain() {
		int reach = BASE + 28;
		// pass 1: the ground; pass 2: trees, vines and towns on top (so no column overwrites a tree)
		for (int x = CX - reach; x <= CX + reach; x++) for (int z = CZ - reach; z <= CZ + reach; z++) {
			int h = (int) Math.floor(height(x, z));
			double r = Math.hypot(x - CX, z - CZ), a = Math.atan2(z - CZ, x - CX);
			boolean sea = z <= 161 && Math.abs(x) <= 112;
			if (h < 63 && !sea && r < BASE + 26) h = 63;   // the plain at the foot, on land
			if (h < 60) continue;
			for (int y = 56; y < h - 3; y++) put(x, y, z, Blocks.STONE);
			for (int y = Math.max(56, h - 3); y < h; y++) put(x, y, z, under(h, r));
			put(x, h, z, surface(x, h, z, r, a));
			// above the mountain the old content (water of the gulf, the old pyramid) gives way to air
			if (sea && h < 63) for (int y = h + 1; y <= 63; y++) put(x, y, z, Blocks.WATER);
		}
		for (int x = CX - reach; x <= CX + reach; x++) for (int z = CZ - reach; z <= CZ + reach; z++) {
			int h = (int) Math.floor(height(x, z));
			double r = Math.hypot(x - CX, z - CZ);
			boolean sea = z <= 161 && Math.abs(x) <= 112;
			if (h < 63 && !sea && r < BASE + 26) h = 63;
			if (h >= 63) decorate(x, h, z, r, Math.atan2(z - CZ, x - CX));
		}
	}

	private static Block under(int h, double r) {
		if (h > 96) return Blocks.TUFF;
		return h > 70 ? Blocks.DIRT : Blocks.TUFF;
	}

	/** what you see on top: shore rock, green slopes, scrub, ash, lava flows */
	private Block surface(int x, int h, int z, double r, double a) {
		// broad patches (lesson: per-block mixes of contrasting blocks read as leopard spots)
		double n = ((smooth(x * 3, z * 3, 7) + 1) / 2) * 0.75 + ((noise(x * 7, z * 5) + 1) / 2) * 0.25;
		boolean lava = r > RIM + 1 && r < BASE - 6 && flowFacingCity(a) && h > 72;
		boolean land = z > 161 || Math.abs(x) > 112;
		if (h <= 64 && land) return n < 0.15 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
		if (h <= 64) return n < 0.5 ? Blocks.TUFF : Blocks.GRAVEL;
		if (lava) return n < 0.55 ? Blocks.BLACKSTONE : n < 0.85 ? Blocks.BASALT : Blocks.TUFF;
		boolean onSomma = sommaArc(a) > 0.15 && r > SOMMA_R - 6;
		if (onSomma && r < SOMMA_R + 1 && h > 84) return n < 0.5 ? Blocks.TUFF : Blocks.STONE;          // bare caldera wall
		int scrub = onSomma ? 92 : 82, ash = onSomma ? 200 : 90;
		if (h < scrub) return n < 0.12 ? Blocks.COARSE_DIRT : n < 0.2 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
		if (h < ash) return n < 0.35 ? Blocks.COARSE_DIRT : n < 0.6 ? Blocks.GRASS_BLOCK : n < 0.8 ? Blocks.PODZOL : Blocks.TUFF;
		// the bare cone: grey-brown ash and lapilli
		return n < 0.35 ? Blocks.GRAVEL : n < 0.62 ? Blocks.TUFF : n < 0.8 ? Blocks.ANDESITE : Blocks.COARSE_DIRT;
	}

	/** two dark tongues of old lava on the flank that faces the city (south-west of the cone) */
	private static boolean flowFacingCity(double a) {
		double toCity = Math.atan2(100 - CZ, 0 - CX);
		for (double off : new double[] {-0.42, 0.28}) {
			double d = Math.abs(Math.atan2(Math.sin(a - toCity - off), Math.cos(a - toCity - off)));
			if (d < 0.045 + 0.02 * Math.sin(a * 37)) return true;
		}
		return false;
	}

	private void decorate(int x, int h, int z, double r, double a) {
		if (h < 63 || h > 92) return;
		long cell = hash(x, z);
		boolean onSomma = sommaArc(a) > 0.15 && r > SOMMA_R - 2;
		boolean lava = r > RIM + 1 && r < BASE - 6 && flowFacingCity(a);
		if (lava) return;
		// little white towns along the foot, on the side that faces the city (Torre del Greco, Ercolano...)
		double toCity = Math.atan2(100 - CZ, 0 - CX);
		double facing = Math.abs(Math.atan2(Math.sin(a - toCity), Math.cos(a - toCity)));
		if (h <= 70 && facing < 1.3 && Math.floorMod(x + (int) (cell >> 9 & 1), 6) == 0 && Math.floorMod(z + (int) (cell >> 10 & 1), 6) == 0 && (cell & 3) == 0) {
			house(x, h, z, cell);
			return;
		}
		// umbrella pines and woods (thicker on the Somma, which is green and wooded), vines lower down
		int treeEvery = onSomma ? 40 : 80;   // scattered: from the city the mountain reads green-brown, not a jungle
		if (h < (onSomma ? 90 : 80) && Math.floorMod(cell, treeEvery) == 0 && free(x, h, z)) {
			if ((cell >> 4 & 1) == 0) pine(x, h, z); else bush(x, h, z, onSomma);
			return;
		}
		if (h < 78 && !onSomma && Math.floorMod(x, 3) == 0 && (cell & 7) < 5) {
			put(x, h + 1, z, Blocks.OAK_FENCE);       // rows of vines (Lacryma Christi)
			if ((cell & 1) == 0) put(x, h + 2, z, Blocks.OAK_LEAVES);
		}
	}

	private boolean free(int x, int h, int z) {
		return world.getBlockState(new BlockPos(x, h + 1, z)).isAir();
	}

	private void pine(int x, int h, int z) {
		int top = h + 5 + (int) (hash(z, x) & 1);
		for (int y = h + 1; y <= top; y++) put(x, y, z, Blocks.SPRUCE_LOG);
		BlockState leaf = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			if (Math.abs(dx) + Math.abs(dz) > 3) continue;
			set(x + dx, top + 1, z + dz, leaf);
			if (Math.abs(dx) + Math.abs(dz) <= 1) set(x + dx, top + 2, z + dz, leaf);
		}
	}

	private void bush(int x, int h, int z, boolean chestnut) {
		BlockState leaf = (chestnut ? Blocks.OAK_LEAVES : Blocks.JUNGLE_LEAVES).defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		put(x, h + 1, z, Blocks.OAK_LOG);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 2; dy <= 3; dy++)
			if (dy == 2 || Math.abs(dx) + Math.abs(dz) <= 1) set(x + dx, h + dy, z + dz, leaf);
	}

	private void house(int x, int h, int z, long cell) {
		Block[] walls = {Blocks.SMOOTH_QUARTZ, block("white_terracotta"), Blocks.SMOOTH_SANDSTONE, block("pink_terracotta")};
		Block wall = walls[(int) (cell >> 3 & 3)];
		int top = h + 2 + (int) (cell >> 6 & 1);
		for (int dx = 0; dx <= 2; dx++) for (int dz = 0; dz <= 2; dz++) {
			for (int y = h; y <= top; y++) put(x + dx, y, z + dz, wall);
			put(x + dx, top + 1, z + dz, Blocks.SMOOTH_STONE_SLAB);
		}
		put(x + 1, h + 2, z, block("black_stained_glass"));
		put(x, h + 2, z + 1, block("black_stained_glass"));
	}

	// ---------------------------------------------------------------- the crater
	private void crater() {
		int floor = 102;
		for (int dx = -RIM; dx <= RIM; dx++) for (int dz = -RIM; dz <= RIM; dz++) {
			double r = Math.hypot(dx, dz);
			if (r > RIM - 0.5) continue;
			int x = CX + dx, z = CZ + dz;
			// a bowl: steep walls, flat bottom of ash
			int bottom = (int) Math.round(floor + Math.pow(r / RIM, 2.5) * (111 - floor)); // never above the rim (112)
			for (int y = bottom + 1; y <= 125; y++) put(x, y, z, Blocks.AIR);
			double n = (noise(x * 5, z * 3) + 1) / 2;
			put(x, bottom, z, r < 3 ? Blocks.GRAVEL : n < 0.4 ? Blocks.TUFF : n < 0.7 ? Blocks.BLACKSTONE : Blocks.BASALT);
			for (int y = bottom - 3; y < bottom; y++) put(x, y, z, Blocks.TUFF);
		}
		// fumaroles: a thin plume over the crater, like on the old postcards
		put(CX, floor, CZ, Blocks.HAY_BLOCK);
		set(CX, floor + 1, CZ, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true));
		set(CX + 3, floor + 2, CZ - 1, Blocks.CAMPFIRE.defaultBlockState());
		put(CX + 3, floor + 1, CZ - 1, Blocks.TUFF);
	}

	// ---------------------------------------------------------------- helpers
	private static long hash(int x, int z) {
		long h = x * 734287L + z * 912931L;
		h = (h ^ (h >>> 15)) * 2246822519L;
		return (h ^ (h >>> 13)) & 0x7FFFFFFF;
	}

	/** smooth value noise in [-1, 1] with the given cell size */
	private static double smooth(int x, int z, int cell) {
		int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
		double fx = (x - gx * cell) / (double) cell, fz = (z - gz * cell) / (double) cell;
		fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
		double a = noise(gx, gz), b = noise(gx + 1, gz), c = noise(gx, gz + 1), d = noise(gx + 1, gz + 1);
		return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
	}

	private static double noise(int x, int z) {
		long h = x * 374761393L + z * 668265263L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 32767.5 - 1;
	}

	private void set(int x, int y, int z, BlockState st) { world.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); }
	private void put(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }
	private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
		BlockState st = b.defaultBlockState();
		for (BlockPos p : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) world.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
	private static Block block(String n) { return CityBlocks.vanilla(n); }
}
