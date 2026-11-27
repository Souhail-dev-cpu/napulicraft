package dev.napuli;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * City upgrade v4: Piazza del Plebiscito done properly.
 *  - the DOUBLE Doric colonnade in a semicircle that embraces the square (it was a single thin row)
 *  - San Francesco di Paola: temple front with 6 columns and a triangular pediment, the big Pantheon-like dome
 *    on a drum, two small side domes, a lit interior
 *  - the two equestrian statues in front, paving in grey basalt with light radial lines, cast-iron lamps
 *  - Palazzo Reale: red plaster with grey piperno frames, ground-floor arcade with the kings' statues in niches,
 *    piano nobile windows with balconies, cornice, balustrade, the clock, lit interior floors
 *  - all over town: green shutters on the windows, palms and benches on the seafront
 */
final class NapoliPlebiscito {
	// semicircle of the colonnade: centre, inner/outer row radius
	static final int CX = -30, CZ = 26, R_IN = 15, R_OUT = 18; // fits between Via Toledo (z<=4) and the seafront (z>=45)
	private final ServerLevel world;
	private final List<BlockPos> toConnect = new ArrayList<>();

	private NapoliPlebiscito(ServerLevel world) { this.world = world; }

	static void build(ServerLevel world) {
		Napuli.LOGGER.info("Napoli v4: rebuilding Piazza del Plebiscito, San Francesco di Paola, Palazzo Reale");
		NapoliPlebiscito p = new NapoliPlebiscito(world);
		p.clearAndPave();
		p.colonnade();
		p.basilica();
		p.equestrian(-18, 18);
		p.equestrian(-18, 34);
		p.palazzoReale();
		p.lamps();
		p.shutters();
		p.seafront();
		p.connect();
	}

	static void refine(ServerLevel world) {
		var piazza = new NapoliPlebiscito(world);
		for (int side : new int[] {-1, 1}) {
			for (int deg = 71; deg <= 79; deg++) {
				double angle = Math.toRadians(deg) * side;
				for (int radius = R_IN - 1; radius <= R_OUT + 1; radius++) {
					int x = (int) Math.round(CX - radius * Math.cos(angle));
					int z = (int) Math.round(CZ + radius * Math.sin(angle));
					if (z < 7 || z > 44) continue;
					piazza.put(x, 73, z, Blocks.SMOOTH_QUARTZ);
					piazza.put(x, 74, z, Blocks.QUARTZ_SLAB);
				}
			}
			for (int radius : new int[] {R_IN, R_OUT}) {
				double angle = Math.toRadians(77) * side;
				int x = (int) Math.round(CX - radius * Math.cos(angle));
				int z = (int) Math.round(CZ + radius * Math.sin(angle));
				piazza.fill(x, 64, z, x, 71, z, Blocks.QUARTZ_PILLAR);
				piazza.put(x, 72, z, Blocks.CHISELED_QUARTZ_BLOCK);
			}
		}
		for (int z : new int[] {18, 34}) {
			for (int x = -21; x <= -15; x++) for (int zz = z - 3; zz <= z + 3; zz++) {
				if (x == -21 || x == -15 || zz == z - 3 || zz == z + 3)
					piazza.put(x, 63, zz, Blocks.POLISHED_BLACKSTONE);
			}
		}
		for (int x = -37; x <= 9; x++)
			if (world.getBlockState(new BlockPos(x, 64, CZ)).isAir())
				piazza.put(x, 63, CZ, Blocks.POLISHED_ANDESITE);
	}

	/** v5: the old gallery had its west arch one block outside the area demolished in v3: it was left floating */
	static void cleanup(ServerLevel world) {
		NapoliPlebiscito p = new NapoliPlebiscito(world);
		p.fill(12, 64, -16, 12, 96, 2, Blocks.AIR);
		p.fill(13, 64, -16, 13, 96, 2, Blocks.AIR);
		Napuli.LOGGER.info("Napoli v5: removed the floating arch of the old gallery");
	}

	// ================================================================ the square
	private void clearAndPave() {
		fill(-92, 64, 5, 31, 112, 44, Blocks.AIR);
		for (int x = -92; x <= 31; x++) for (int z = 5; z <= 44; z++) {
			double ang = Math.atan2(z - CZ, x - CX);
			double r = Math.hypot(x - CX, z - CZ);
			boolean ray = Math.abs(Math.sin(ang * 8)) < 0.07 && r > 4 && r < R_IN - 2 && x > CX - R_IN;
			boolean ring = Math.abs(r - 9) < 0.6 && x > CX - 9;
			Block b = ray || ring ? Blocks.CALCITE : ((x * 7 + z * 13) % 5 == 0 ? Blocks.ANDESITE : (x + z) % 3 == 0 ? Blocks.TUFF : Blocks.POLISHED_ANDESITE);
			put(x, 63, z, b);
		}
		sign(0, 64, 6, "PIAZZA DEL", "PLEBISCITO", "21 ott. 1860");
	}

	// ================================================================ the double colonnade
	private void colonnade() {
		for (int side : new int[] {-1, 1}) {
			// floor, entablature and roof of the portico: fine steps along the arc
			for (double deg = 40; deg <= 71; deg += 0.4) {
				double t = Math.toRadians(deg) * side;
				for (double r = R_IN - 1; r <= R_OUT + 1; r += 0.5) {
					int x = (int) Math.round(CX - r * Math.cos(t)), z = (int) Math.round(CZ + r * Math.sin(t));
					put(x, 63, z, Blocks.POLISHED_DIORITE);
					put(x, 73, z, Blocks.SMOOTH_QUARTZ);
					put(x, 74, z, Blocks.SMOOTH_QUARTZ);
					put(x, 75, z, Blocks.QUARTZ_SLAB);
				}
				// triglyph band on the outer face
				int xo = (int) Math.round(CX - (R_IN - 1.5) * Math.cos(t)), zo = (int) Math.round(CZ + (R_IN - 1.5) * Math.sin(t));
				if (((int) deg) % 3 == 0) put(xo, 73, zo, Blocks.CHISELED_QUARTZ_BLOCK);
			}
			// two rows of Doric columns
			for (int deg = 42; deg <= 71; deg += 5) {
				double t = Math.toRadians(deg) * side;
				for (int r : new int[] {R_IN, R_OUT}) {
					int x = (int) Math.round(CX - r * Math.cos(t)), z = (int) Math.round(CZ + r * Math.sin(t));
					put(x, 64, z, Blocks.SMOOTH_QUARTZ);
					for (int y = 65; y <= 71; y++) put(x, y, z, Blocks.QUARTZ_PILLAR);
					put(x, 72, z, Blocks.CHISELED_QUARTZ_BLOCK);
				}
				if (deg % 10 == 4) {
					double rm = (R_IN + R_OUT) / 2.0;
					hang((int) Math.round(CX - rm * Math.cos(t)), 72, (int) Math.round(CZ + rm * Math.sin(t)));
				}
			}
		}
	}

	// ================================================================ San Francesco di Paola
	private void basilica() {
		int front = CX - R_IN;          // x = -52: the column line of the temple front
		int zc = CZ, z1 = zc - 9, z2 = zc + 9;
		// steps and floor of the portico
		for (int z = z1; z <= z2; z++) {
			set(front + 2, 64, z, Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
			put(front + 1, 64, z, Blocks.SMOOTH_QUARTZ);
		}
		fill(front - 6, 64, z1, front, 64, z2, Blocks.SMOOTH_QUARTZ);
		// six Ionic columns (+ two pilasters against the cella)
		for (int z = z1 + 1; z <= z2 - 1; z += 3) {
			for (int y = 65; y <= 77; y++) put(front, y, z, Blocks.QUARTZ_PILLAR);
			put(front, 78, z, Blocks.CHISELED_QUARTZ_BLOCK);
			for (int y = 65; y <= 77; y++) put(front - 5, y, z, Blocks.QUARTZ_PILLAR);
		}
		// architrave and the triangular pediment, statues on top
		fill(front - 6, 79, z1, front, 80, z2, Blocks.SMOOTH_QUARTZ);
		for (int z = z1; z <= z2; z++) put(front + 1, 80, z, Blocks.QUARTZ_SLAB);
		for (int z = z1; z <= z2; z++) {
			int top = 81 + (int) ((10.5 - Math.abs(z - zc)) * 0.7);
			fill(front - 6, 81, z, front, top, z, Blocks.SMOOTH_QUARTZ);
			put(front + 1, top, z, Blocks.QUARTZ_STAIRS);
			if (Math.abs(z - zc) == 0) put(front, top + 1, z, Blocks.CALCITE);
		}
		for (int z : new int[] {z1, z2, zc}) { put(front, 82 + (z == zc ? 7 : 0), z, Blocks.CALCITE); put(front, 83 + (z == zc ? 7 : 0), z, Blocks.CHISELED_QUARTZ_BLOCK); }
		// the cella wall with the great door
		fill(front - 7, 65, z1, front - 7, 80, z2, Blocks.SMOOTH_SANDSTONE);
		fill(front - 7, 65, zc - 2, front - 7, 71, zc + 2, Blocks.AIR);
		// the rotunda: drum with pilasters and windows, then the dome, the lantern, the cross
		int rx = -67, rz = zc, R = 14;
		for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
			double r = Math.hypot(dx, dz);
			if (r > R + 0.5) continue;
			int x = rx + dx, z = rz + dz;
			if (r <= R - 1.5) {
				// inside: marble floor in rings, a ring of light in the floor
				put(x, 63, z, Math.abs(r - 9) < 0.5 ? Blocks.SEA_LANTERN : ((int) r) % 3 == 0 ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_QUARTZ);
				for (int y = 64; y <= 84; y++) put(x, y, z, Blocks.AIR);
			} else {
				double ang = Math.toDegrees(Math.atan2(dz, dx));
				boolean pilaster = Math.abs(((ang % 22.5) + 22.5) % 22.5) < 3;
				boolean window = Math.abs(((ang % 45) + 45) % 45 - 22.5) < 4;
				for (int y = 64; y <= 84; y++) {
					Block b = pilaster ? Blocks.QUARTZ_PILLAR : (window && y >= 76 && y <= 81) ? Blocks.GLASS : Blocks.SMOOTH_SANDSTONE;
					put(x, y, z, b);
				}
				put(x, 85, z, Blocks.SMOOTH_QUARTZ);
			}
			// dome: lead-grey shell with light ribs
			int top = 86 + (int) Math.round(14 * Math.sqrt(Math.max(0, 1 - (r / (R + 0.5)) * (r / (R + 0.5)))));
			double ang = Math.toDegrees(Math.atan2(dz, dx));
			boolean rib = Math.abs(((ang % 30) + 30) % 30) < 2.5;
			int from = r >= R - 3 ? 86 : top - 1;
			for (int y = from; y <= top; y++) put(x, y, z, r < 2.2 ? Blocks.GLASS : rib ? ((y % 4 == 0 && r < R - 3) ? Blocks.SEA_LANTERN : Blocks.CALCITE) : Blocks.POLISHED_ANDESITE);
			if (r >= R - 2.5 && r < R - 1.5) { put(x, 80, z, Blocks.SMOOTH_QUARTZ_SLAB); if ((dx + dz) % 4 == 0) put(x, 81, z, Blocks.LANTERN); }
		}
		for (int y = 102; y <= 105; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
			if (Math.abs(dx) == 2 || Math.abs(dz) == 2) put(rx + dx, y, rz + dz, (dx == 0 || dz == 0) && y < 105 ? Blocks.GLASS : Blocks.CALCITE);
		fill(rx - 2, 106, rz - 2, rx + 2, 106, rz + 2, Blocks.POLISHED_ANDESITE);
		put(rx, 107, rz, Blocks.CALCITE);
		put(rx, 108, rz, block("lightning_rod"));
		// inside: the altar against the back, candles, pews
		fill(rx - 12, 64, rz - 2, rx - 11, 65, rz + 2, Blocks.SMOOTH_QUARTZ);
		put(rx - 12, 66, rz, Blocks.GOLD_BLOCK);
		for (int dz = -2; dz <= 2; dz += 2) candle(rx - 11, 66, rz + dz);
		for (int dx = -6; dx <= 6; dx += 3) for (int dz : new int[] {-6, -5, -4, 4, 5, 6})
			set(rx + dx, 64, rz + dz, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		// the two small side domes
		for (int sz : new int[] {zc - 14, zc + 14}) smallDome(-57, sz);
		// the great door goes through the cella AND the drum of the rotunda
		fill(front - 9, 65, zc - 2, front - 7, 71, zc + 2, Blocks.AIR);
	}

	private void smallDome(int cx, int cz) {
		int R = 5;
		for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
			double r = Math.hypot(dx, dz);
			if (r > R + 0.5) continue;
			for (int y = 64; y <= 77; y++) put(cx + dx, y, cz + dz, r <= R - 1.5 ? (y == 64 ? Blocks.SEA_LANTERN : Blocks.AIR) : Blocks.SMOOTH_SANDSTONE);
			int top = 78 + (int) Math.round(5 * Math.sqrt(Math.max(0, 1 - (r / (R + 0.5)) * (r / (R + 0.5)))));
			for (int y = r >= R - 1 ? 78 : top; y <= top; y++) put(cx + dx, y, cz + dz, Blocks.POLISHED_ANDESITE);
		}
		put(cx, 84, cz, Blocks.CALCITE);
		put(cx, 85, cz, block("lightning_rod"));
	}

	/** Canova's horsemen: a marble pedestal and a dark bronze horse and rider, facing the palace */
	private void equestrian(int x, int z) {
		fill(x - 3, 64, z - 2, x + 3, 64, z + 2, Blocks.SMOOTH_QUARTZ);
		fill(x - 2, 65, z - 1, x + 2, 68, z + 1, Blocks.POLISHED_DIORITE);
		fill(x - 3, 69, z - 2, x + 3, 69, z + 2, Blocks.SMOOTH_QUARTZ_SLAB);
		int y = 70;
		Block bronze = Blocks.POLISHED_BLACKSTONE;
		for (int dz = 0; dz <= 1; dz++) {
			put(x - 2, y, z - 1 + dz * 2, Blocks.POLISHED_BLACKSTONE_WALL); put(x + 1, y, z - 1 + dz * 2, Blocks.POLISHED_BLACKSTONE_WALL); // legs
			put(x - 2, y + 1, z - 1 + dz * 2, Blocks.POLISHED_BLACKSTONE_WALL); put(x + 1, y + 1, z - 1 + dz * 2, Blocks.POLISHED_BLACKSTONE_WALL);
		}
		fill(x - 2, y + 2, z - 1, x + 1, y + 3, z + 1, bronze);       // body
		fill(x + 2, y + 3, z, x + 2, y + 4, z, bronze);                 // neck
		set(x + 3, y + 4, z, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)); // head
		put(x - 3, y + 2, z, Blocks.POLISHED_BLACKSTONE_WALL);          // tail
		fill(x - 1, y + 4, z, x - 1, y + 5, z, bronze);                 // rider
		put(x - 1, y + 6, z, Blocks.POLISHED_BLACKSTONE_SLAB);
		put(x, y + 5, z, Blocks.POLISHED_BLACKSTONE_WALL);              // arm, pointing ahead
		for (int dz : new int[] {-1, 1}) put(x - 1, y + 4, z + dz, Blocks.POLISHED_BLACKSTONE_SLAB); // the cloak
	}

	// ================================================================ Palazzo Reale
	private void palazzoReale() {
		int fx = 33, x2 = 47, z1 = 6, z2 = 46; // stops before the seafront
		fill(fx - 3, 64, z1 - 1, x2 + 1, 92, z2 + 1, Blocks.AIR);
		fill(fx, 64, z1, x2, 83, z2, block("red_terracotta"));
		fill(fx + 2, 64, z1 + 2, x2 - 2, 82, z2 - 2, Blocks.AIR);
		for (int y : new int[] {70, 76}) fill(fx + 2, y, z1 + 2, x2 - 2, y, z2 - 2, Blocks.SMOOTH_STONE); // floors inside
		for (int x = fx + 4; x <= x2 - 3; x += 5) for (int z = z1 + 4; z <= z2 - 3; z += 6)
			for (int y : new int[] {64, 71, 77}) put(x, y, z, Blocks.LANTERN);       // light every room: no monsters
		// ground floor in grey piperno: arcade of 9 bays, kings in the niches, the great portal in the middle
		fill(fx, 64, z1, fx + 1, 69, z2, Blocks.POLISHED_ANDESITE);
		for (int y : new int[] {66, 68}) fill(fx, y, z1, fx, y, z2, Blocks.ANDESITE);
		fill(fx, 70, z1, fx, 70, z2, Blocks.STONE_BRICKS);                          // belt course
		int bay = 5;
		for (int k = 0; k < 8; k++) {
			int z0 = z1 + 1 + k * bay;                                               // bay: z0..z0+4, opening z0+1..z0+3
			for (int y = 64; y <= 69; y++) put(fx, y, z0, Blocks.STONE_BRICKS);      // pilaster
			boolean portal = k == 4, kingNiche = k != 0 && k != 7 && !portal;
			if (portal) {
				fill(fx, 64, z0 + 1, fx + 1, 68, z0 + 3, Blocks.AIR);
				put(fx, 69, z0 + 2, Blocks.CHISELED_STONE_BRICKS);
			} else if (kingNiche) {
				fill(fx, 64, z0 + 1, fx, 68, z0 + 3, Blocks.AIR);                    // the niche, one block deep
				king(fx, z0 + 2);
			} else {
				fill(fx, 64, z0 + 1, fx + 1, 67, z0 + 3, Blocks.AIR);                // open arch into the courtyard
			}
			arch(fx, 68, z0 + 1, z0 + 3);
			// piano nobile window with balcony, second floor window
			window(fx, 72, z0 + 2, 4, true);
			window(fx, 78, z0 + 2, 3, false);
		}
		// cornice, balustrade, the clock in the middle
		fill(fx - 1, 82, z1 - 1, fx - 1, 82, z2 + 1, Blocks.SMOOTH_STONE_SLAB);
		fill(fx, 83, z1, fx, 83, z2, Blocks.POLISHED_ANDESITE);
		for (int z = z1; z <= z2; z++) put(fx, 84, z, (z - z1) % 5 == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.STONE_BRICK_WALL);
		fill(fx, 84, 26, x2 - 8, 89, 32, block("red_terracotta"));
		fill(fx - 1, 85, 28, fx - 1, 87, 30, block("white_concrete"));
		put(fx - 1, 86, 29, block("black_concrete")); put(fx - 1, 87, 29, block("black_concrete")); put(fx - 1, 86, 30, block("black_concrete"));
		for (int z = 26; z <= 32; z++) put(fx - 1, 89 + (3 - Math.abs(z - 29)) / 2, z, Blocks.POLISHED_ANDESITE);
		fill(fx + 3, 90, 29, fx + 3, 94, 29, Blocks.IRON_BARS);
		Block[] flag = {block("green_wool"), block("white_wool"), block("red_wool")};
		for (int i = 0; i < 3; i++) put(fx + 3, 93, 30 + i, flag[i]);
		signAt(new BlockPos(fx - 1, 64, z1 + 1 + 4 * bay + 4), Direction.WEST, "PALAZZO", "REALE");
	}

	/** a semicircular arch head over an opening (upside-down stairs at the sides) */
	private void arch(int x, int y, int za, int zb) {
		set(x, y, za, Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
		set(x, y, zb, Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.HALF, Half.TOP));
		put(x, y, (za + zb) / 2, Blocks.AIR);
		put(x, y + 1, (za + zb) / 2, Blocks.CHISELED_STONE_BRICKS);
	}

	/** one of the eight kings of Naples, in white stone, on a pedestal */
	private void king(int x, int z) {
		put(x, 64, z, Blocks.SMOOTH_QUARTZ);
		put(x, 65, z, Blocks.CALCITE);
		put(x, 66, z, Blocks.CALCITE);
		put(x, 67, z, Blocks.CHISELED_QUARTZ_BLOCK);
		put(x, 68, z, block("yellow_candle")); // the crown
		
	}

	/** window with a grey frame; the piano nobile ones get a pediment and an iron balcony */
	private void window(int x, int y, int z, int h, boolean noble) {
		for (int dy = 0; dy < h; dy++) { put(x, y + dy, z, block("black_stained_glass")); put(x, y + dy, z - 1, Blocks.POLISHED_ANDESITE); put(x, y + dy, z + 1, Blocks.POLISHED_ANDESITE); }
		fill(x, y - 1, z - 1, x, y - 1, z + 1, Blocks.POLISHED_ANDESITE);
		if (noble) {
			put(x - 1, y + h, z - 1, Blocks.POLISHED_ANDESITE_SLAB);
			put(x - 1, y + h, z + 1, Blocks.POLISHED_ANDESITE_SLAB);
			set(x - 1, y + h, z, Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
			fill(x - 1, y - 1, z - 1, x - 1, y - 1, z + 1, Blocks.SMOOTH_STONE_SLAB);
			for (int dz = -1; dz <= 1; dz++) { put(x - 2, y, z + dz, Blocks.IRON_BARS); toConnect.add(new BlockPos(x - 2, y, z + dz)); }
			put(x - 2, y - 1, z - 1, Blocks.SMOOTH_STONE_SLAB); put(x - 2, y - 1, z, Blocks.SMOOTH_STONE_SLAB); put(x - 2, y - 1, z + 1, Blocks.SMOOTH_STONE_SLAB);
		} else {
			put(x - 1, y + h, z, Blocks.POLISHED_ANDESITE_SLAB);
		}
	}

	// ================================================================ lamps, shutters, seafront
	private void lamps() {
		// the real square is a big empty space: lamps only along its edges
		for (int z = 10; z <= 40; z += 10) lamp(28, z);
		for (int x = -8; x <= 22; x += 10) { lamp(x, 6); lamp(x, 43); }
	}

	/** cast-iron lamp: dark post, crossbar, two lanterns */
	private void lamp(int x, int z) {
		for (int y = 64; y <= 68; y++) put(x, y, z, Blocks.POLISHED_BLACKSTONE_WALL);
		put(x, 69, z, Blocks.POLISHED_BLACKSTONE);
		put(x, 70, z, Blocks.LANTERN);
		
	}

	/** green shutters ("persiane") next to the front windows of every home */
	private void shutters() {
		List<int[]> homes = new ArrayList<>(NapoliInteriors.homes());
		for (int[] h : new int[][] {{150, -30, 3}, {163, -30, 6}, {150, -60, 9}, {163, -60, 2}, {156, 12, 5}, {156, 30, 7}}) homes.add(h);
		BlockState shut = block("warped_trapdoor").defaultBlockState().setValue(BlockStateProperties.OPEN, true)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		for (int[] h : homes) {
			int x = h[0], z = h[1], height = 12 + Math.floorMod(h[2], 4) * 3;
			for (int y = 68; y < 63 + height; y += 4)
				for (int dx : new int[] {2, 5, 8}) {
					BlockPos left = new BlockPos(x + dx - 1, y, z + 12);
					if (world.getBlockState(left).isAir()) world.setBlock(left, shut, Block.UPDATE_CLIENTS);
				}
		}
	}

	/** palms and benches along Via Partenope, between the lamps */
	private void seafront() {
		for (int x = -68; x <= 68; x += 10) {
			int edge = 55 + (int) (9 * Math.pow(Math.abs(x) / 73.0, 1.6));
			int z = edge - 6;
			if (Math.abs(x) < 8) continue;
			if (Math.floorMod(x, 20) == 12) palm(x, z);
			else set(x, 64, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		}
	}

	private void palm(int x, int z) {
		for (int y = 64; y <= 70; y++) put(x + (y >= 69 ? 1 : 0), y, z, Blocks.JUNGLE_LOG);
		BlockState leaf = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		int tx = x + 1, ty = 71;
		set(tx, ty, z, leaf);
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
			for (int i = 1; i <= 3; i++) set(tx + d.getStepX() * i, ty - (i == 3 ? 1 : 0), z + d.getStepZ() * i, leaf);
		for (int[] c : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) set(tx + c[0], ty, z + c[1], leaf);
	}

	// ================================================================ helpers
	private void hang(int x, int y, int z) { set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true)); }
	private void candle(int x, int y, int z) { set(x, y, z, block("white_candle").defaultBlockState().setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, true)); }

	private void sign(int x, int y, int z, String... lines) {
		set(x, y, z, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8));
		text(new BlockPos(x, y, z), lines);
	}

	private void signAt(BlockPos p, Direction face, String... lines) {
		world.setBlock(p, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face), Block.UPDATE_CLIENTS);
		text(p, lines);
	}

	private void text(BlockPos pos, String... lines) {
		if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			var t = SignText.EMPTY.asMutable();
			for (int i = 0; i < lines.length && i < 4; i++) t.setLine(i, Component.literal(lines[i]));
			sign.setText(t.asImmutable(), SignTextSlot.FRONT);
			sign.setText(t.asImmutable(), SignTextSlot.BACK);
			sign.setChanged();
		}
	}

	private void connect() {
		for (BlockPos p : toConnect) world.setBlock(p, Block.updateFromNeighbourShapes(world.getBlockState(p), world, p), Block.UPDATE_CLIENTS);
	}

	private void set(int x, int y, int z, BlockState st) { world.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); }
	private void put(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }
	private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
		BlockState st = b.defaultBlockState();
		for (BlockPos p : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) world.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
	private static Block block(String n) { return CityBlocks.vanilla(n); }
}
