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
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * City upgrade v6: Castel dell'Ovo and Borgo Marinari done like the real ones.
 *  - Megaride: an irregular tuff islet with reefs, joined to Via Partenope by a stone causeway on arches
 *  - the castle: one massive, low, polygonal fortress in yellow tuff with battered (sloping) walls rising from the
 *    sea, flat parapets with embrasures, few small windows, the big square Torre Maestra and the Torre Normandia,
 *    an arched gate at the end of the bridge, a courtyard, a ramp up to the cannon terrace facing the gulf
 *  - Borgo Marinari: the fishermen's village at the foot of the castle: colourful 3-4 storey houses with green
 *    shutters and balconies, trattorie with tables and umbrellas on the quay, a little harbour with gozzi,
 *    bollards, a breakwater with a red-and-white lighthouse
 */
final class NapoliCastello {
	// outer line of the castle walls (x, z), clockwise from the gate front
	private static final int[][] OUTLINE = {{-12, 84}, {12, 84}, {16, 88}, {18, 98}, {22, 104}, {20, 114}, {10, 117},
			{-8, 117}, {-18, 112}, {-22, 102}, {-20, 92}, {-16, 86}};
	private final ServerLevel world;
	private final List<BlockPos> toConnect = new ArrayList<>();

	private NapoliCastello(ServerLevel world) { this.world = world; }

	static void build(ServerLevel world) {
		Napuli.LOGGER.info("Napoli v6: rebuilding Castel dell'Ovo and Borgo Marinari");
		NapoliCastello c = new NapoliCastello(world);
		c.clear();
		c.islet();
		c.causeway();
		c.castle();
		c.borgo();
		c.harbour();
		c.connect();
	}

	// ================================================================ ground
	/** wipe the old rock, castle boxes, quay and boats (the sea strip south of Via Partenope, never Vesuvius at z>=119) */
	private void clear() {
		fill(-30, 60, 61, 50, 63, 118, Blocks.WATER);
		fill(-30, 64, 61, 50, 110, 118, Blocks.AIR);
		fill(-30, 56, 61, 50, 59, 118, Blocks.WATER);
		// the old causeway head on the land side
		fill(-5, 64, 55, 5, 66, 60, Blocks.AIR);
	}

	private void islet() {
		for (int x = -30; x <= 30; x++) for (int z = 78; z <= 118; z++) {
			double e = Math.pow(x / 27.0, 2) + Math.pow((z - 100) / 19.5, 2);
			double n = noise(x, z) * 0.18;
			if (e > 1 + n) continue;
			int top = 62 + (int) Math.round((1 - e) * 3 + noise(x * 3, z) * 1.5);
			for (int y = 56; y <= top; y++) put(x, y, z, rock(x, y, z));
		}
		// reefs (scogli) around the rock
		int[][] reefs = {{-27, 90}, {-29, 106}, {-20, 117}, {24, 113}, {28, 99}, {-12, 80}, {6, 119}};
		for (int[] r : reefs)
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
				int h = 63 - Math.abs(dx) - Math.abs(dz) + (int) Math.round(noise(r[0] + dx, r[1] + dz) * 1.4);
				for (int y = 57; y <= h; y++) put(r[0] + dx, y, r[1] + dz, rock(dx, y, dz));
			}
		// the Borgo stands on a low shelf on the east side of the rock
		fill(5, 56, 72, 48, 63, 95, Blocks.TUFF);
		fill(5, 63, 72, 46, 63, 81, Blocks.STONE_BRICKS);
		for (int x = 5; x <= 46; x++) put(x, 63, 72, Blocks.POLISHED_ANDESITE);  // quay edge
		fill(18, 63, 82, 48, 63, 95, Blocks.ANDESITE);
	}

	private void causeway() {
		for (int z = 56; z <= 84; z++) {
			fill(-5, 56, z, 5, 62, z, Blocks.TUFF_BRICKS);
			fill(-4, 63, z, 4, 63, z, Math.floorMod(z, 4) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.POLISHED_BASALT);
			// arches the sea flows through
			int k = Math.floorMod(z - 58, 7);
			if (z >= 58 && z <= 81 && k >= 1 && k <= 5) {
				fill(-5, 56, z, 5, 61, z, Blocks.WATER);
				if (k >= 2 && k <= 4) put(-5, 62, z, Blocks.WATER);
				if (k >= 2 && k <= 4) put(5, 62, z, Blocks.WATER);
				if (k >= 2 && k <= 4) fill(-4, 62, z, 4, 62, z, Blocks.WATER);
			}
			if (z >= 58) {
				put(-5, 63, z, Blocks.TUFF_BRICKS);
				put(-5, 64, z, Blocks.TUFF_BRICK_WALL);
				toConnect.add(new BlockPos(-5, 64, z));
				boolean quayGap = z >= 73 && z <= 80;
				put(5, 63, z, quayGap ? Blocks.STONE_BRICKS : Blocks.TUFF_BRICKS);
				if (!quayGap) { put(5, 64, z, Blocks.TUFF_BRICK_WALL); toConnect.add(new BlockPos(5, 64, z)); }
			}
			if (z >= 60 && z <= 80 && Math.floorMod(z - 60, 10) == 0) {
				lamp(-5, z);
				if (z != 80) lamp(5, z);
			}
		}
		// the ramp up into the gate
		fill(-3, 64, 83, 3, 64, 83, Blocks.POLISHED_TUFF);
		BlockState step = Blocks.POLISHED_TUFF_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
		for (int x = -3; x <= 3; x++) set(x, 64, 82, step);
		sign(-3, 64, 58, "CASTEL", "DELL'OVO", "Megaride");
	}

	// ================================================================ the castle
	private void castle() {
		int floor = 64;
		for (int x = -30; x <= 30; x++) for (int z = 78; z <= 120; z++) {
			boolean in = inside(x, z);
			double d = edgeDistance(x, z);
			int wallTop = z < 90 ? 76 : z >= 105 ? 74 : 79; // the south bastion is low: the cannon terrace looks over it
			if (!in) {
				// battered scarp: the walls slope out into the sea
				if (d <= 2.2) {
					int top = wallTop - (d < 1.2 ? 8 : 12);
					for (int y = 57; y <= top; y++) put(x, y, z, tuff(x, y, z));
				}
				continue;
			}
			if (d < 2.6) {
				for (int y = 57; y <= wallTop; y++) put(x, y, z, tuff(x, y, z));
				if (d < 1.0) {
					// string course under the parapet, then the parapet with embrasures
					put(x, wallTop - 1, z, Blocks.CUT_SANDSTONE);
					boolean embrasure = Math.floorMod(x * 7 + z * 3, 6) == 0;
					if (!embrasure) put(x, wallTop + 1, z, tuff(x, wallTop + 1, z));
					// small windows through the thick wall, few of them
					if (Math.floorMod(x * 5 + z * 11, 17) == 0 && z > 86) {
						put(x, wallTop - 6, z, block("black_stained_glass"));
						put(x, wallTop - 7, z, block("black_stained_glass"));
					}
				}
			} else {
				for (int y = 57; y < floor; y++) put(x, y, z, tuff(x, y, z));
				fill(x, floor + 1, z, x, 100, z, Blocks.AIR); // the bare rock must not poke into the courtyard
				put(x, floor, z, Math.floorMod(x + z, 5) == 0 ? Blocks.TUFF_BRICKS : Blocks.POLISHED_TUFF);
			}
		}
		gate(floor);
		hall(6, 95, 17, 104, floor, 77);
		terrace(floor);
		tower(-20, 93, -8, 106, 89, true);    // Torre Maestra: massive, only a little higher than the walls
		tower(9, 85, 16, 93, 84, false);      // Torre Normandia
		courtyardLights(floor);
	}

	private void gate(int floor) {
		// the passage through the front wall
		fill(-2, floor + 1, 82, 2, floor + 3, 88, Blocks.AIR);
		fill(-1, floor + 4, 82, 1, floor + 4, 88, Blocks.AIR);
		fill(-2, floor, 83, 2, floor, 88, Blocks.POLISHED_TUFF);
		// piperno frame and arch around the opening
		for (int y = floor + 1; y <= floor + 3; y++) { put(-3, y, 84, Blocks.POLISHED_DEEPSLATE); put(3, y, 84, Blocks.POLISHED_DEEPSLATE); }
		BlockState archL = Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
		BlockState archR = Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP);
		set(-2, floor + 4, 84, archL);
		set(2, floor + 4, 84, archR);
		fill(-1, floor + 5, 84, 1, floor + 5, 84, Blocks.POLISHED_DEEPSLATE);
		// the coat of arms above the gate
		put(0, floor + 7, 84, Blocks.CHISELED_STONE_BRICKS);
		put(-1, floor + 7, 84, Blocks.STONE_BRICK_WALL);
		put(1, floor + 7, 84, Blocks.STONE_BRICK_WALL);
		put(0, floor + 8, 84, Blocks.STONE_BRICK_WALL);
		hang(0, floor + 4, 86);
	}

	/** the halls on the east side of the courtyard (Sala delle Prigioni / Sala Italia) */
	private void hall(int x1, int z1, int x2, int z2, int floor, int top) {
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
			if (!inside(x, z)) continue;
			boolean wall = x == x1 || z == z1 || z == z2 || edgeDistance(x, z) < 2.6;
			for (int y = floor + 1; y <= top; y++) put(x, y, z, wall ? tuff(x, y, z) : Blocks.AIR);
			if (!wall) {
				put(x, top, z, Blocks.SMOOTH_SANDSTONE);
				put(x, floor, z, Blocks.POLISHED_TUFF);
			}
		}
		// door and windows on the courtyard side
		fill(x1, floor + 1, 98, x1, floor + 3, 100, Blocks.AIR);
		for (int z = z1 + 2; z <= z2 - 2; z += 3) {
			if (z >= 98 && z <= 100) continue;
			put(x1, floor + 5, z, block("black_stained_glass"));
			put(x1, floor + 6, z, block("black_stained_glass"));
		}
		// vaulted ceiling feel: an inner row of tuff pillars, lanterns hanging between them
		for (int z = z1 + 2; z <= z2 - 2; z += 3) {
			fill(x1 + 5, floor + 1, z, x1 + 5, top - 1, z, Blocks.TUFF_BRICKS);
			hang(x1 + 3, top - 1, z);
		}
		// a little museum of the egg legend
		put(x1 + 7, floor + 1, (z1 + z2) / 2, Blocks.SMOOTH_QUARTZ);
		put(x1 + 7, floor + 2, (z1 + z2) / 2, Blocks.TURTLE_EGG);
		signAt(new BlockPos(x1 + 6, floor + 1, (z1 + z2) / 2), Direction.WEST, "L'UOVO", "'e Virgilio:", "si se rompe", "cade Napule!");
		// flat roof with a parapet
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
			if (inside(x, z) && (x == x1 || z == z1 || z == z2)) put(x, top + 1, z, tuff(x, top + 1, z));
	}

	/** the raised southern block: ramp from the courtyard, cannon terrace on the gulf */
	private void terrace(int floor) {
		int level = 74;
		for (int x = -30; x <= 30; x++) for (int z = 105; z <= 120; z++) {
			if (!inside(x, z) || edgeDistance(x, z) < 2.6) continue;
			for (int y = floor + 1; y < level; y++) put(x, y, z, tuff(x, y, z));
			put(x, level, z, Math.floorMod(x, 4) == 0 ? Blocks.TUFF_BRICKS : Blocks.POLISHED_TUFF);
		}
		// the ramp: straight, wide, along the courtyard
		for (int i = 0; i <= 9; i++) {
			int z = 95 + i, y = floor + 1 + i;
			fill(-6, floor + 1, z, -3, y - 1, z, Blocks.TUFF_BRICKS);
			BlockState st = Blocks.POLISHED_TUFF_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
			for (int x = -6; x <= -3; x++) set(x, y, z, st);
			put(-7, y + 1, z, Blocks.TUFF_BRICK_WALL);
			put(-2, y + 1, z, Blocks.TUFF_BRICK_WALL);
			toConnect.add(new BlockPos(-7, y + 1, z));
			toConnect.add(new BlockPos(-2, y + 1, z));
		}
		fill(-6, floor + 1, 105, -3, level - 1, 105, Blocks.TUFF_BRICKS);
		fill(-6, level, 105, -3, level, 105, Blocks.POLISHED_TUFF);
		// the terrace retaining wall towards the courtyard
		for (int x = -30; x <= 30; x++) {
			if (!inside(x, 105) || edgeDistance(x, 105) < 2.6 || (x >= -6 && x <= -3)) continue;
			put(x, level + 1, 105, Blocks.TUFF_BRICK_WALL);
			toConnect.add(new BlockPos(x, level + 1, 105));
		}
		// cannons in the embrasures facing the sea
		for (int x : new int[] {-12, -4, 4, 12}) cannon(x, level + 1, 113);
		for (int x = -14; x <= 16; x += 10) hangPost(x, level, 109);
		sign(1, level + 1, 107, "TERRAZZA", "d'e cannune", "'o Vesuvio", "sta llà ->");
	}

	private void cannon(int x, int y, int z) {
		put(x - 1, y, z, Blocks.SPRUCE_SLAB);
		put(x + 1, y, z, Blocks.SPRUCE_SLAB);
		put(x, y, z, Blocks.DARK_OAK_PLANKS);
		BlockState barrel = Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
		set(x, y + 1, z, barrel);
		set(x, y + 1, z + 1, barrel);
		set(x, y + 1, z + 2, barrel);
		put(x, y + 1, z - 1, Blocks.POLISHED_BLACKSTONE_BUTTON);
	}

	/** a massive square tower with a battered base, corbelled parapet and a flag */
	private void tower(int x1, int z1, int x2, int z2, int top, boolean flag) {
		for (int x = x1 - 2; x <= x2 + 2; x++) for (int z = z1 - 2; z <= z2 + 2; z++) {
			int out = Math.max(Math.max(x1 - x, x - x2), Math.max(z1 - z, z - z2));
			if (out > 0) {
				// sloping skirt
				int skirt = 70 - out * 4;
				for (int y = 57; y <= skirt; y++) put(x, y, z, tuff(x, y, z));
				continue;
			}
			boolean wall = x == x1 || x == x2 || z == z1 || z == z2 || x == x1 + 1 || x == x2 - 1 || z == z1 + 1 || z == z2 - 1;
			for (int y = 57; y <= top; y++) put(x, y, z, wall ? tuff(x, y, z) : (y <= 64 ? tuff(x, y, z) : Blocks.AIR));
			if (!wall) { put(x, 64, z, Blocks.POLISHED_TUFF); put(x, top, z, Blocks.POLISHED_TUFF); }
		}
		// corbels and the parapet stepping out at the top
		for (int x = x1 - 1; x <= x2 + 1; x++) for (int z = z1 - 1; z <= z2 + 1; z++) {
			boolean ring = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
			if (!ring) continue;
			Direction out = x == x1 - 1 ? Direction.EAST : x == x2 + 1 ? Direction.WEST : z == z1 - 1 ? Direction.SOUTH : Direction.NORTH;
			set(x, top - 1, z, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, out).setValue(StairBlock.HALF, Half.TOP));
			put(x, top, z, Blocks.CUT_SANDSTONE);
			if (Math.floorMod(x + z, 3) != 0) put(x, top + 1, z, tuff(x, top + 1, z));
		}
		// string course and a few slit windows
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
			boolean face = x == x1 || x == x2 || z == z1 || z == z2;
			if (!face) continue;
			put(x, 76, z, Blocks.CUT_SANDSTONE);
			if (Math.floorMod(x + z, 4) == 1) {
				for (int y : new int[] {top - 6, top - 5}) put(x, y, z, block("black_stained_glass"));
			}
		}
		// a door from the courtyard and a ladder up inside
		int doorX = x2 == -8 ? x2 : x1, doorZ = (z1 + z2) / 2;
		if (x2 == -8) {
			fill(x2 - 1, 65, doorZ, x2, 67, doorZ, Blocks.AIR);
		} else {
			fill(doorX, 65, z2 - 1, doorX + 1, 67, z2, Blocks.AIR);
		}
		int lx = x1 + 2, lz = z1 + 2;
		for (int y = 65; y < top; y++) {
			set(lx, y, lz, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		}
		put(lx, top, lz, Blocks.AIR);
		for (int y = 68; y < top; y += 6) put((x1 + x2) / 2, y, (z1 + z2) / 2 + 1, Blocks.LIGHT);
		if (flag) flag((x1 + x2) / 2, top + 1, (z1 + z2) / 2);
	}

	private void flag(int x, int y, int z) {
		fill(x, y, z, x, y + 7, z, Blocks.SPRUCE_FENCE);
		// flies west from the pole: seen from the land (looking south) the pole is on the left -> green, white, red
		Block[] tri = {block("green_wool"), block("white_wool"), block("red_wool")};
		for (int i = 0; i < 3; i++) fill(x - 1 - i, y + 5, z, x - 1 - i, y + 7, z, tri[i]);
	}

	private void courtyardLights(int floor) {
		for (int[] p : new int[][] {{-8, 90}, {4, 90}, {-8, 100}, {2, 103}, {4, 97}}) hangPost(p[0], floor, p[1]);
		// a well in the middle of the courtyard
		int wx = -1, wz = 94;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(wx + dx, floor + 1, wz + dz, Blocks.TUFF_BRICKS);
		put(wx, floor + 1, wz, Blocks.WATER);
		for (int[] c : new int[][] {{-1, -1}, {1, 1}}) fill(wx + c[0], floor + 2, wz + c[1], wx + c[0], floor + 3, wz + c[1], Blocks.SPRUCE_FENCE);
		fill(wx - 1, floor + 4, wz - 1, wx + 1, floor + 4, wz + 1, Blocks.SPRUCE_SLAB);
	}

	// ================================================================ Borgo Marinari
	private static final String[][] SHOPS = {
			{"CIRCOLO", "NAUTICO", "Soci sulamente"},
			{"TRATTORIA", "'O SCOGLIO", "Spaghetti", "a vongole"},
			{"PIZZERIA", "MEGARIDE", "Forno a legna"},
			{"FRIGGITORIA", "'E MARINARI", "Cuoppo 'e", "pesce frijuto"}};

	private void borgo() {
		Block[] colours = {block("white_terracotta"), block("pink_terracotta"), block("orange_terracotta"), block("yellow_terracotta")};
		int[] floors = {3, 4, 3, 4};
		for (int i = 0; i < 4; i++) house(19 + i * 7, 82, colours[i], floors[i], SHOPS[i], i);
		sign(10, 64, 77, "BORGO", "MARINARI");
		// tables and umbrellas of the trattorie on the quay, in front of the houses
		for (int x = 23; x <= 45; x += 7) {
			if (x == 23) continue; // the circolo has no tables
			umbrella(x - 2, 79, (x / 7) % 2 == 0);
		}
		// a few planters and chairs
		for (int x = 21; x <= 46; x += 5) put(x, 64, 77, Blocks.POTTED_RED_TULIP);
	}

	private void house(int x0, int z0, Block colour, int floors, String[] shop, int index) {
		int w = 6, depth = 9, top = 63 + floors * 4;
		for (int x = x0; x <= x0 + w; x++) for (int z = z0; z <= z0 + depth; z++) {
			boolean wall = x == x0 || x == x0 + w || z == z0 || z == z0 + depth;
			for (int y = 64; y <= top; y++) put(x, y, z, wall ? colour : Blocks.AIR);
			for (int f = 1; f < floors; f++) put(x, 63 + f * 4, z, wall ? Blocks.SMOOTH_STONE : Blocks.SPRUCE_PLANKS);
			put(x, top + 1, z, wall ? Blocks.SMOOTH_STONE_SLAB : Blocks.SMOOTH_STONE);
		}
		// piperno base course and a cornice
		for (int x = x0; x <= x0 + w; x++) { put(x, 64, z0, Blocks.POLISHED_DEEPSLATE); put(x, top, z0, Blocks.SMOOTH_STONE); }
		// ground floor: two arches open on the quay
		for (int x : new int[] {x0 + 1, x0 + 4}) fill(x, 64, z0, x + 1, 66, z0, Blocks.AIR);
		hang(x0 + 3, 66, z0 + 3);
		// inside the shop: counter and tables
		fill(x0 + 1, 64, z0 + depth - 2, x0 + w - 1, 64, z0 + depth - 2, Blocks.SPRUCE_PLANKS);
		put(x0 + 2, 64, z0 + 3, Blocks.SPRUCE_FENCE);
		put(x0 + 2, 65, z0 + 3, block("white_carpet"));
		set(x0 + 1, 64, z0 + 3, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
		if (index == 2) put(x0 + w - 1, 64, z0 + depth - 1, Blocks.BLAST_FURNACE); // the wood oven
		signAt(new BlockPos(x0 + 3, 67, z0 - 1), Direction.NORTH, shop);
		// upper floors: windows with green shutters, balconies on the piano nobile
		BlockState shut = block("warped_trapdoor").defaultBlockState().setValue(BlockStateProperties.OPEN, true)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		for (int f = 1; f < floors; f++) {
			int y = 64 + f * 4 + 1;
			for (int x : new int[] {x0 + 2, x0 + 4}) {
				put(x, y, z0, Blocks.GLASS_PANE);
				put(x, y + 1, z0, Blocks.GLASS_PANE);
				toConnect.add(new BlockPos(x, y, z0));
				toConnect.add(new BlockPos(x, y + 1, z0));
				for (int side : new int[] {-1, 1}) {
					int sx = x + side;
					if (sx == x0 + 3 || sx == x0 || sx == x0 + w) continue;
					set(sx, y, z0 - 1, shut);
					set(sx, y + 1, z0 - 1, shut);
				}
			}
			if (f == 1 || (f == 2 && index % 2 == 1)) {
				// balcony: a slab in front of the windows and an iron railing
				fill(x0 + 1, y - 1, z0 - 1, x0 + 5, y - 1, z0 - 1, Blocks.SMOOTH_STONE_SLAB);
				for (int x = x0 + 1; x <= x0 + 5; x++) {
					put(x, y - 1, z0 - 2, Blocks.SMOOTH_STONE_SLAB);
					put(x, y, z0 - 2, Blocks.IRON_BARS);
					toConnect.add(new BlockPos(x, y, z0 - 2));
				}
				put(x0 + 3, y, z0 - 1, Blocks.POTTED_RED_TULIP);
			}
			hang(x0 + 3, f + 1 < floors ? 63 + (f + 1) * 4 - 1 : top, z0 + 4);
		}
		// laundry hung out on a line between two windows of the top floor (it's Naples)
		Block[] cloth = {block("white_wool"), block("light_blue_wool"), block("pink_wool"), block("yellow_wool")};
		if (index % 2 == 0) for (int x = x0 + 1; x <= x0 + 5; x++) {
			put(x, top - 1, z0 - 2, Blocks.TRIPWIRE);
			if (x % 2 == 0) put(x, top - 2, z0 - 2, cloth[Math.floorMod(x + index, 4)]);
		}
		// a ladder to the roof terrace
		for (int y = 64; y <= top; y++) set(x0 + w - 1, y, z0 + depth - 1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		put(x0 + w - 1, top + 1, z0 + depth - 1, Blocks.AIR);
	}

	private void umbrella(int x, int z, boolean red) {
		put(x, 64, z, Blocks.SPRUCE_FENCE);
		put(x, 65, z, Blocks.SPRUCE_FENCE);
		put(x, 66, z, Blocks.SPRUCE_FENCE);
		Block a = red ? block("red_wool") : block("blue_wool"), b = block("white_wool");
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(x + dx, 67, z + dz, (dx + dz) % 2 == 0 ? a : b);
		put(x, 68, z, a);
		// two tables with white tablecloths and chairs around the umbrella
		for (int dx : new int[] {-2, 2}) {
			put(x + dx, 64, z, Blocks.OAK_FENCE);
			put(x + dx, 65, z, block("white_carpet"));
			set(x + dx, 64, z - 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
			set(x + dx, 64, z + 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
			toConnect.add(new BlockPos(x + dx, 64, z));
		}
		toConnect.add(new BlockPos(x, 64, z));
	}

	// ================================================================ the harbour
	private void harbour() {
		// bollards along the quay edge
		for (int x = 8; x <= 44; x += 6) put(x, 64, 72, Blocks.ANDESITE_WALL);
		// the breakwater on the east side, with the lighthouse at its head (leaves a mouth to the open sea at the north)
		fill(44, 56, 63, 47, 63, 72, Blocks.TUFF);
		fill(44, 63, 63, 47, 63, 72, Blocks.STONE_BRICKS);
		for (int z = 64; z <= 72; z += 2) { put(47, 64, z, Blocks.STONE_BRICK_WALL); toConnect.add(new BlockPos(47, 64, z)); }
		// tetrapods / rocks on the outside of the breakwater
		for (int z = 62; z <= 74; z++) for (int x = 48; x <= 50; x++)
			if (noise(x, z) > -0.1 + (x - 48) * 0.35) fill(x, 57, z, x, 62 - (x - 48) + (int) Math.round(noise(z, x)), z, rock(x, 60, z));
		lighthouse(45, 64);
		// gozzi: the colourful wooden boats of the fishermen
		String[] hulls = {"white_concrete", "blue_concrete", "red_concrete", "yellow_concrete", "light_blue_concrete", "white_concrete"};
		int[][] boats = {{12, 69, 0}, {20, 69, 1}, {30, 70, 2}, {38, 69, 3}, {16, 64, 4}, {31, 64, 5}};
		for (int[] b : boats) gozzo(b[0], b[1], block(hulls[b[2]]), b[2]);
		// a small pontoon for the boats
		fill(24, 63, 66, 26, 63, 71, Blocks.SPRUCE_PLANKS);
		for (int z = 66; z <= 71; z += 5) { put(24, 64, z, Blocks.SPRUCE_FENCE); put(26, 64, z, Blocks.SPRUCE_FENCE); }
		toConnect.add(new BlockPos(24, 64, 66));
	}

	private void gozzo(int cx, int cz, Block hull, int index) {
		for (int dx = -3; dx <= 3; dx++) for (int dz = -1; dz <= 1; dz++) {
			if (Math.abs(dx) == 3 && dz != 0) continue;
			int x = cx + dx, z = cz + dz;
			put(x, 62, z, Blocks.DARK_OAK_PLANKS);
			put(x, 63, z, Blocks.SPRUCE_PLANKS);
			boolean rim = Math.abs(dz) == 1 || Math.abs(dx) == 3;
			put(x, 64, z, rim ? hull : Blocks.AIR);
		}
		// the bow post, the seat, and the oars
		put(cx + 3, 65, cz, Blocks.SPRUCE_FENCE);
		put(cx, 64, cz, Blocks.SPRUCE_SLAB);
		if (index % 2 == 0) put(cx - 2, 64, cz, Blocks.BARREL);
		else put(cx - 2, 64, cz, Blocks.COBWEB); // the net
	}

	private void lighthouse(int x, int z) {
		for (int y = 64; y <= 72; y++) {
			Block b = ((y - 64) / 2) % 2 == 0 ? block("red_concrete") : block("white_concrete");
			fill(x - 1, y, z - 1, x + 1, y, z + 1, b);
		}
		fill(x - 1, 73, z - 1, x + 1, 73, z + 1, Blocks.GLASS);
		put(x, 73, z, Blocks.SEA_LANTERN);
		fill(x - 1, 74, z - 1, x + 1, 74, z + 1, block("red_concrete"));
		put(x, 75, z, Blocks.END_ROD);
	}

	// ================================================================ helpers
	private boolean inside(int x, int z) {
		boolean in = false;
		double px = x + 0.5, pz = z + 0.5;
		for (int i = 0, j = OUTLINE.length - 1; i < OUTLINE.length; j = i++) {
			double xi = OUTLINE[i][0], zi = OUTLINE[i][1], xj = OUTLINE[j][0], zj = OUTLINE[j][1];
			if ((zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi) + xi) in = !in;
		}
		return in;
	}

	private double edgeDistance(int x, int z) {
		double px = x + 0.5, pz = z + 0.5, best = Double.MAX_VALUE;
		for (int i = 0, j = OUTLINE.length - 1; i < OUTLINE.length; j = i++) {
			double ax = OUTLINE[j][0], az = OUTLINE[j][1], bx = OUTLINE[i][0], bz = OUTLINE[i][1];
			double dx = bx - ax, dz = bz - az;
			double t = Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz)));
			best = Math.min(best, Math.hypot(px - (ax + t * dx), pz - (az + t * dz)));
		}
		return best;
	}

	/** deterministic value noise in [-1, 1] */
	private static double noise(int x, int z) {
		long h = x * 374761393L + z * 668265263L;
		h = (h ^ (h >>> 13)) * 1274126177L;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 32767.5 - 1;
	}

	/** Neapolitan yellow tuff: ochre and sand tones, weathered */
	private Block tuff(int x, int y, int z) {
		// broad weathering patches, not salt-and-pepper speckles (red terracotta dots read as a leopard/camo pattern)
		double coarse = (noise(Math.floorDiv(x, 4) * 7 + Math.floorDiv(y, 3), Math.floorDiv(z, 4) * 5 - Math.floorDiv(y, 3) * 3) + 1) / 2;
		double fine = (noise(x * 31 + y, z * 17 - y * 7) + 1) / 2;
		if (y <= 64 && fine < 0.35) return Blocks.TUFF; // grey-green stain at the waterline
		double n = coarse * 0.7 + fine * 0.3;
		if (n < 0.40) return Blocks.SMOOTH_SANDSTONE;
		if (n < 0.75) return Blocks.SANDSTONE;
		return Blocks.CUT_SANDSTONE;
	}

	/** the bare rock of the islet, darker and greener at the waterline */
	private Block rock(int x, int y, int z) {
		double n = (noise(x * 13 + y * 5, z * 19 + y) + 1) / 2;
		if (y <= 63 && n < 0.25) return Blocks.MOSSY_COBBLESTONE;
		if (n < 0.45) return Blocks.TUFF;
		if (n < 0.65) return Blocks.ANDESITE;
		if (n < 0.85) return Blocks.COBBLESTONE;
		return Blocks.STONE;
	}

	private void lamp(int x, int z) {
		fill(x, 65, z, x, 67, z, Blocks.DEEPSLATE_BRICK_WALL);
		put(x, 68, z, Blocks.LANTERN);
		toConnect.add(new BlockPos(x, 65, z));
	}

	private void hangPost(int x, int floor, int z) {
		fill(x, floor + 1, z, x, floor + 3, z, Blocks.DEEPSLATE_BRICK_WALL);
		put(x, floor + 4, z, Blocks.LANTERN);
	}

	private void hang(int x, int y, int z) { set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true)); }

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
