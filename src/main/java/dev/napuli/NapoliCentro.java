package dev.napuli;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * City upgrade v8: the CENTRO STORICO, north of Via Toledo (z -68 .. -152), plus the cleanup of the old Galleria.
 *  - Piazza del Gesù: the Gesù Nuovo with its grey piperno façade cut in diamond points, the baroque Guglia
 *    dell'Immacolata in the middle of the square, the unofficial parking guy with a couple of scooters
 *  - Spaccanapoli: one long, straight, narrow street of tall palazzi (ochre, pink, red), shops and "bassi" on the
 *    ground floor, green shutters, little balconies, laundry hung across the street
 *  - San Gregorio Armeno: the street of the nativity workshops, stalls full of figurines, candles and cornicelli
 *  - Largo Maradona: the mural of the "10", the shrine with candles, scarves and flowers
 *  - Totonno's pizzeria with the wood oven, Nonna Assunta outside her basso
 */
final class NapoliCentro {
	static final int SPACCA_Z = -125;                 // centre line of Spaccanapoli (5 wide: z -127..-123)
	static final int SGA_X = 30;                      // San Gregorio Armeno (3 wide: x 29..31), north of Spaccanapoli
	static final BlockPos GUGLIA = new BlockPos(-40, 64, -83);

	/** where the people of the centro live (NapoliStreet keeps one of each role near each spot) */
	static final List<Object[]> SPOTS = List.of(
			new Object[] {NapoliResident.Role.NONNA, new BlockPos(15, 64, -123), 180f},
			new Object[] {NapoliResident.Role.PARCHEGGIATORE, new BlockPos(-24, 64, -76), 90f},
			new Object[] {NapoliResident.Role.TIFOSO, new BlockPos(-60, 64, -128), -90f},
			new Object[] {NapoliResident.Role.TIFOSO, new BlockPos(48, 64, -125), 90f},
			new Object[] {NapoliResident.Role.SCUGNIZZO, new BlockPos(-30, 64, -86), 0f},
			new Object[] {NapoliResident.Role.SCUGNIZZO, new BlockPos(60, 64, -125), 90f},
			new Object[] {NapoliResident.Role.PRESEPAIO, new BlockPos(SGA_X, 64, -136), 180f},
			new Object[] {NapoliResident.Role.PRESEPAIO, new BlockPos(SGA_X, 64, -146), 0f},
			new Object[] {NapoliResident.Role.PIZZAIOLO, new BlockPos(-12, 64, -131), 0f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(-46, 64, -76), 0f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(-20, 64, -125), 90f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(0, 64, -95), 180f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(16, 64, -125), -90f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(40, 64, -125), 90f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(SGA_X, 64, -141), 180f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(0, 64, -140), 0f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(20, 64, -78), 180f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(56, 64, -94), 0f},
			new Object[] {NapoliResident.Role.TIFOSO, new BlockPos(40, 64, -76), 180f},
			// more Neapolitans in the old city too
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(0, 64, -10), 180f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(-10, 64, 22), 90f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(-50, 64, 50), -90f},
			new Object[] {NapoliResident.Role.PASSERBY, new BlockPos(50, 64, 51), 90f},
			new Object[] {NapoliResident.Role.TIFOSO, new BlockPos(-20, 64, 12), 0f},
			new Object[] {NapoliResident.Role.NONNA, new BlockPos(-40, 64, -40), 0f},
			new Object[] {NapoliResident.Role.SCUGNIZZO, new BlockPos(10, 64, -40), 0f},
			new Object[] {NapoliResident.Role.PIZZAIOLO, new BlockPos(30, 64, 79), 180f},
			new Object[] {NapoliResident.Role.PARCHEGGIATORE, new BlockPos(14, 64, 46), 180f});

	private static final Block[] COLOURS = {block("yellow_terracotta"), block("pink_terracotta"), block("orange_terracotta"),
			block("white_terracotta"), Blocks.TERRACOTTA, block("red_terracotta"), Blocks.SMOOTH_SANDSTONE};
	private final ServerLevel world;
	private final List<BlockPos> toConnect = new ArrayList<>();

	private NapoliCentro(ServerLevel world) { this.world = world; }

	static void build(ServerLevel world) {
		Napuli.LOGGER.info("Napoli v8: cleaning the old Galleria, building the centro storico");
		NapoliCentro c = new NapoliCentro(world);
		int removed = c.galleryLeftovers();
		Napuli.LOGGER.info("Napoli v8: removed {} blocks left from the old Galleria", removed);
		c.ground();
		c.piazzaDelGesu();
		c.spaccanapoli();
		c.sanGregorioArmeno();
		c.largoMaradona();
		c.piazzaBellini();
		c.connect();
		Napuli.LOGGER.info("Napoli v8: centro storico done");
	}

	// ================================================================ the old Galleria's leftovers
	/**
	 * The v3 demolition missed the bays and roof ribs of the old Galleria at x=24 and x=38 and the dome ring at
	 * z=-17 and z=3 (sandstone, quartz, brown and light blue glass hanging in the air next to the Palazzo Reale).
	 * Only the old Galleria's own materials go, nothing that was built later (Palazzo Reale, market stalls).
	 */
	private int galleryLeftovers() {
		java.util.Set<Block> gallery = java.util.Set.of(Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_QUARTZ, Blocks.CHISELED_QUARTZ_BLOCK,
				block("brown_stained_glass"), block("light_blue_stained_glass"), Blocks.IRON_BLOCK, Blocks.GLASS, Blocks.CALCITE,
				Blocks.QUARTZ_SLAB, Blocks.LANTERN);
		int n = 0;
		for (int x = 12; x <= 51; x++) for (int z = -24; z <= 8; z++) {
			if (x >= 30 && x <= 48 && z >= 5) continue;            // the Palazzo Reale
			for (int y = 64; y <= 96; y++) {
				BlockPos p = new BlockPos(x, y, z);
				Block b = world.getBlockState(p).getBlock();
				if (!gallery.contains(b)) continue;
				// the bar's east wall (x=24, z -32..-20) was overwritten by the gallery bays: give it back
				boolean barWall = x == 24 && z >= -32 && z <= -20 && y <= 70;
				world.setBlock(p, barWall ? block("yellow_terracotta").defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				n++;
			}
		}
		return n;
	}

	// ================================================================ ground and streets
	private void ground() {
		fill(-76, 56, -153, 76, 62, -67, Blocks.STONE);
		fill(-76, 63, -153, 76, 63, -67, Blocks.STONE_BRICKS);
		fill(-76, 64, -153, 76, 100, -67, Blocks.AIR);
		// Via Toledo goes on north, across Spaccanapoli
		basoli(-6, -153, 6, -67);
		fill(-7, 63, -153, -7, 63, -67, Blocks.SMOOTH_STONE);
		fill(7, 63, -153, 7, 63, -67, Blocks.SMOOTH_STONE);
		for (int z = -72; z >= -150; z -= 12) { lamp(-8, z); lamp(8, z); }
		sign(-8, 64, -69, "CENTRO STORICO", "Piazza d' 'o Gesù ←", "Spaccanapoli ↑", "S. Gregorio Armeno");
	}

	/** the grey lava-stone paving of Naples ("basoli") */
	private void basoli(int x1, int z1, int x2, int z2) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
			put(x, 63, z, Math.floorMod(x * 3 + z * 7, 5) == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE);
	}

	// ================================================================ Piazza del Gesù
	private void piazzaDelGesu() {
		basoli(-62, -100, -8, -70);
		// the Gesù Nuovo: façade on the north side of the square, facing south
		int fx1 = -56, fx2 = -24, fz = -101, top = 86;
		fill(fx1, 64, fz - 22, fx2, top - 6, fz, Blocks.POLISHED_ANDESITE);       // the church body
		fill(fx1 + 2, 64, fz - 20, fx2 - 2, top - 7, fz - 2, Blocks.AIR);
		fill(fx1 + 2, 63, fz - 20, fx2 - 2, 63, fz - 2, Blocks.POLISHED_DIORITE);
		for (int x = fx1; x <= fx2; x++) for (int y = 64; y <= top; y++) {
			// diamond-point rustication: every stone of the piperno façade has a point (a button sticking out)
			put(x, y, fz, (x + y) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES);
			if ((x + y) % 2 == 0 && y < top - 1)
				set(x, y, fz + 1, Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
						.setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		}
		fill(fx1, top + 1, fz, fx2, top + 1, fz, Blocks.SMOOTH_STONE_SLAB);
		// the marble portal: columns, a pediment, the door
		int cx = (fx1 + fx2) / 2;
		fill(cx - 3, 64, fz + 1, cx + 3, 73, fz + 1, Blocks.AIR);
		for (int dx : new int[] {-3, 3}) fill(cx + dx, 64, fz + 1, cx + dx, 71, fz + 1, Blocks.QUARTZ_PILLAR);
		fill(cx - 4, 72, fz + 1, cx + 4, 72, fz + 1, Blocks.SMOOTH_QUARTZ);
		for (int i = 0; i <= 4; i++) {
			put(cx - 4 + i, 73 + i / 2, fz + 1, Blocks.QUARTZ_BLOCK);
			put(cx + 4 - i, 73 + i / 2, fz + 1, Blocks.QUARTZ_BLOCK);
		}
		put(cx, 75, fz + 1, Blocks.CHISELED_QUARTZ_BLOCK);
		fill(cx - 2, 64, fz, cx + 2, 70, fz, Blocks.AIR);                          // the door opening
		fill(cx - 1, 71, fz, cx + 1, 71, fz, Blocks.AIR);
		sign(cx, 64, fz + 3, "GESU' NUOVO", "piperno a", "punta 'e diamante");
		// high windows and a dome peeping over the façade
		for (int x : new int[] {fx1 + 5, fx2 - 5}) fill(x, 76, fz, x + 1, 80, fz, block("yellow_stained_glass"));
		dome(cx, fz - 12, top - 6, 7);
		// inside: nave lit with candles and lanterns, benches
		for (int z = fz - 4; z >= fz - 17; z -= 3) {
			for (int x = cx - 8; x <= cx - 3; x++) set(x, 64, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
			for (int x = cx + 3; x <= cx + 8; x++) set(x, 64, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		}
		fill(cx - 3, 64, fz - 20, cx + 3, 65, fz - 19, Blocks.SMOOTH_QUARTZ);
		put(cx, 66, fz - 20, Blocks.GOLD_BLOCK);
		for (int x = fx1 + 4; x <= fx2 - 4; x += 6) for (int z = fz - 4; z >= fz - 18; z -= 7) hang(x, top - 8, z);

		guglia(GUGLIA.getX(), GUGLIA.getZ());

		// the parking guy's "car park": two scooters lined up on the east side of the square
		for (int i = 0; i < 2; i++) {
			Motorino m = Napuli.MOTORINO.create(world, EntitySpawnReason.EVENT);
			if (m != null) {
				m.snapTo(-20.5, 64, -78.5 - i * 3, 90, 0);
				world.addFreshEntity(m);
			}
		}
		for (int z = -82; z <= -74; z += 4) put(-18, 64, z, Blocks.POLISHED_BLACKSTONE_WALL);
		sign(-22, 64, -72, "PARCHEGGIO", "custodito", "2 euro", "(nun è legale)");
		// benches around the square
		for (int x = -58; x <= -30; x += 7) {
			set(x, 64, -72, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
			set(x + 1, 64, -72, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		}
		for (int x = -60; x <= -12; x += 16) { lamp(x, -71); lamp(x, -98); }
	}

	private void dome(int cx, int cz, int base, int r) {
		for (int y = 0; y <= r; y++) {
			double ring = r * Math.sqrt(1 - y * y / (double) (r * r));
			for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
				double d = Math.hypot(dx, dz);
				if (d <= ring && d >= ring - 1.5) put(cx + dx, base + y, cz + dz, dx == 0 || dz == 0 ? Blocks.CALCITE : Blocks.POLISHED_ANDESITE);
			}
		}
		put(cx, base + r + 1, cz, Blocks.CALCITE);
		put(cx, base + r + 2, cz, Blocks.END_ROD);
	}

	/** the Guglia dell'Immacolata: a tall baroque spire of white marble, gilded, the Madonna on top */
	private void guglia(int x, int z) {
		fill(x - 4, 64, z - 4, x + 4, 64, z + 4, Blocks.POLISHED_DIORITE);
		fill(x - 3, 65, z - 3, x + 3, 66, z + 3, Blocks.SMOOTH_QUARTZ);
		int y = 67;
		int[] widths = {2, 2, 2, 1, 1, 1, 1, 0, 0, 0};
		int[] heights = {3, 2, 2, 3, 2, 2, 2, 3, 3, 2};
		for (int t = 0; t < widths.length; t++) {
			int w = widths[t];
			for (int h = 0; h < heights[t]; h++, y++) {
				fill(x - w, y, z - w, x + w, y, z + w, h == heights[t] - 1 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.QUARTZ_BLOCK);
				if (h == 0 && w > 0) for (int[] c : new int[][] {{-w - 1, -w - 1}, {w + 1, -w - 1}, {-w - 1, w + 1}, {w + 1, w + 1}})
					put(x + c[0], y, z + c[1], Blocks.QUARTZ_PILLAR);               // the statues at the corners
			}
			if (t % 3 == 2) for (Direction d : Direction.Plane.HORIZONTAL)
				put(x + d.getStepX() * (w + 1), y - 1, z + d.getStepZ() * (w + 1), Blocks.GOLD_BLOCK);    // gilded reliefs
		}
		// the Immacolata on the top
		put(x, y, z, Blocks.CALCITE);
		put(x, y + 1, z, Blocks.CALCITE);
		put(x, y + 2, z, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);                  // the golden halo
		for (Direction d : Direction.Plane.HORIZONTAL) put(x + d.getStepX() * 5, 64, z + d.getStepZ() * 5, Blocks.LANTERN);
		sign(x + 6, 64, z, "GUGLIA", "DELL'IMMACOLATA", "1747-1750");
	}

	// ================================================================ Spaccanapoli
	private void spaccanapoli() {
		int z0 = SPACCA_Z;
		basoli(-66, z0 - 2, 72, z0 + 2);
		// the alley from the square to the beginning of Spaccanapoli
		basoli(-62, -101, -58, z0);
		int i = 0;
		// south side (fronts face north, onto the street at z0+1)
		for (int x = -18; x <= 66; x += 8) {
			if (x + 7 >= -7 && x <= 7) continue;                                         // Via Toledo
			palazzo(x, x + 7, z0 + 3, z0 + 13, false, 4 + (i % 3 == 0 ? 1 : 0), i++);
		}
		// north side (fronts face south), interrupted by Via Toledo and San Gregorio Armeno
		for (int x = -56; x <= 66; x += 8) {
			if (x + 7 >= -7 && x <= 7) continue;
			if (x + 7 >= SGA_X - 9 && x <= SGA_X + 9) continue;                          // the San Gregorio block
			palazzo(x, x + 7, z0 - 3, z0 - 13, true, 4 + (i % 4 == 1 ? 1 : 0), i++);
		}
		// laundry across the street, the soul of Spaccanapoli
		for (int x = -50; x <= 64; x += 9) {
			if (x >= -8 && x <= 8) continue;
			int y = 72 + Math.floorMod(x, 3) * 3;
			for (int z = z0 - 2; z <= z0 + 2; z++) put(x, y, z, Blocks.TRIPWIRE);
			put(x, y - 1, z0 - 1, laundry(x));
			put(x, y - 1, z0 + 1, laundry(x + 1));
		}
		// Totonno's pizzeria (in the first north palazzo west of Toledo)
		pizzeria(-12, z0 - 3);
		// Nonna Assunta's basso: plastic chair, a little table, the Madonna in the window
		set(14, 64, z0 + 2, Blocks.BIRCH_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		put(12, 64, z0 + 2, Blocks.BIRCH_FENCE);
		put(12, 65, z0 + 2, block("white_carpet"));
		toConnect.add(new BlockPos(12, 64, z0 + 2));
		sign(-62, 64, z0 + 2, "SPACCANAPOLI", "←  →", "'a strada ca", "spacca Napule");
	}

	static void repairResidentialDoors(ServerLevel city) {
		int i=0;
		for(int x=-18;x<=66;x+=8){
			if(x+7>=-7&&x<=7)continue;
			if(i++%3==2)NapoliExpansion.ensureDoor(city,new BlockPos(x+3,64,SPACCA_Z+3),Direction.NORTH);
		}
		for(int x=-56;x<=66;x+=8){
			if((x+7>=-7&&x<=7)||(x+7>=SGA_X-9&&x<=SGA_X+9))continue;
			if(i++%3==2)NapoliExpansion.ensureDoor(city,new BlockPos(x+3,64,SPACCA_Z-3),Direction.SOUTH);
		}
		i=40;
		for(int x=10;x<=62;x+=8)
			if(i++%3==2)NapoliExpansion.ensureDoor(city,new BlockPos(x+3,64,-101),Direction.SOUTH);
	}

	private Block laundry(int x) {
		Block[] c = {block("white_wool"), block("light_blue_wool"), block("pink_wool"), block("yellow_wool"), block("red_wool")};
		return c[Math.floorMod(x * 7, c.length)];
	}

	/**
	 * A tall narrow palazzo: coloured plaster, piperno base, ground floor shop or "basso", green shutters,
	 * small iron balconies, a cornice and a flat roof. {@code north}: the palazzo is north of the street (front faces south).
	 */
	private void palazzo(int x1, int x2, int front, int back, boolean north, int floors, int index) {
		Block wall = COLOURS[Math.floorMod(index * 5 + x1, COLOURS.length)];
		int dir = north ? 1 : -1;                    // from the front towards the street
		int zMin = Math.min(front, back), zMax = Math.max(front, back);
		int top = 63 + floors * 4 + 1;
        CityProperty.recordHome(world, new BlockPos(x1, 63, zMin), new BlockPos(x2, top, zMax));
		fill(x1, 64, zMin, x2, top, zMax, wall);
		fill(x1 + 1, 64, zMin + 1, x2 - 1, top - 1, zMax - 1, Blocks.AIR);
		for (int f = 1; f < floors; f++) fill(x1 + 1, 63 + f * 4 + 1, zMin + 1, x2 - 1, 63 + f * 4 + 1, zMax - 1, Blocks.SPRUCE_PLANKS);
		fill(x1, 64, front, x2, 65, front, Blocks.POLISHED_ANDESITE);               // piperno base
		fill(x1, top, front, x2, top, front, Blocks.SMOOTH_STONE);                   // cornice
		fill(x1, top + 1, zMin, x2, top + 1, zMax, Blocks.SMOOTH_STONE_SLAB);
		// ground floor: a shop opening or a basso door
		boolean shop = index % 3 != 2;
		int mid = (x1 + x2) / 2;
		if (shop) {
			fill(mid - 1, 64, front, mid + 1, 66, front, Blocks.AIR);
			hang(mid, 66, front - dir);
			put(mid - 2, 64, front - dir, Blocks.BARREL);
			put(mid + 2, 64, front - dir, Blocks.BARREL);
			// an awning over the shop
			for (int x = mid - 2; x <= mid + 2; x++) set(x, 67, front + dir, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
					.setValue(BlockStateProperties.HALF, Half.TOP).setValue(BlockStateProperties.HORIZONTAL_FACING, north ? Direction.NORTH : Direction.SOUTH));
		} else {
			fill(mid, 64, front, mid, 65, front, Blocks.AIR);
			NapoliExpansion.ensureDoor(world,new BlockPos(mid,64,front),north?Direction.SOUTH:Direction.NORTH);
			put(mid + 1, 65, front + dir, Blocks.POTTED_RED_TULIP);
		}
		hang(mid, 65 + 3, front - dir);
		// upper floors: two windows each, green shutters, a balcony on the first floor
		BlockState shut = block("warped_trapdoor").defaultBlockState().setValue(BlockStateProperties.OPEN, true)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, north ? Direction.SOUTH : Direction.NORTH);
		for (int f = 1; f < floors; f++) {
			int y = 63 + f * 4 + 2;
			for (int wx : new int[] {x1 + 2, x2 - 2}) {
				put(wx, y, front, Blocks.GLASS_PANE);
				put(wx, y + 1, front, Blocks.GLASS_PANE);
				toConnect.add(new BlockPos(wx, y, front));
				toConnect.add(new BlockPos(wx, y + 1, front));
				for (int s : new int[] {-1, 1}) {
					int sx = wx + s;
					if (sx <= x1 || sx >= x2 || sx == mid) continue;
					set(sx, y, front + dir, shut);
					set(sx, y + 1, front + dir, shut);
				}
				if (f == 1 || (f == 3 && index % 2 == 0)) {
					put(wx, y - 1, front + dir, Blocks.SMOOTH_STONE_SLAB);   // a shallow balcony: the street is narrow
					put(wx, y, front + dir, Blocks.IRON_BARS);
					toConnect.add(new BlockPos(wx, y, front + dir));
				}
			}
			hang(mid, y + 2, (front + back) / 2);
		}
		// an edicola votiva on some corners: a little altar with a candle
		if (index % 4 == 3) {
			put(x1, 68, front + dir, Blocks.SMOOTH_QUARTZ_SLAB);
			set(x1, 69, front + dir, block("red_candle").defaultBlockState().setValue(BlockStateProperties.CANDLES, 2).setValue(BlockStateProperties.LIT, true));
		}
	}

	private void pizzeria(int x, int front) {
		// replaces the ground floor of the palazzo there
		fill(x - 3, 64, front - 9, x + 3, 67, front - 1, Blocks.AIR);
		fill(x - 3, 64, front, x + 3, 66, front, Blocks.AIR);
		fill(x - 3, 63, front - 9, x + 3, 63, front - 1, block("white_terracotta"));
		// the wood oven: a dome of bricks with fire inside
		fill(x - 2, 64, front - 9, x + 2, 66, front - 7, Blocks.BRICKS);
		put(x, 64, front - 7, Blocks.CAMPFIRE);
		put(x, 65, front - 7, Blocks.AIR);
		fill(x - 1, 67, front - 9, x + 1, 67, front - 8, Blocks.BRICKS);
		// counter and tables
		fill(x - 3, 64, front - 5, x + 3, 64, front - 5, Blocks.SMOOTH_QUARTZ);
		put(x - 2, 65, front - 5, Blocks.CAKE);
		for (int tx : new int[] {x - 2, x + 2}) {
			put(tx, 64, front - 2, Blocks.OAK_FENCE);
			put(tx, 65, front - 2, block("red_carpet"));
			toConnect.add(new BlockPos(tx, 64, front - 2));
		}
		hang(x, 67, front - 3);
		sign(x, 67, front + 1, "PIZZERIA", "DA TOTONNO", "dal 1924", "forno a legna");
	}

	// ================================================================ San Gregorio Armeno
	private void sanGregorioArmeno() {
		int x0 = SGA_X;
		basoli(x0 - 1, -152, x0 + 1, SPACCA_Z - 3);
		// two rows of narrow workshops facing the street, packed with nativity figurines
		int k = 0;
		for (int z = SPACCA_Z - 4; z >= -150; z -= 6) {
			workshop(x0 - 9, x0 - 2, z - 5, z, true, k++);
			workshop(x0 + 2, x0 + 9, z - 5, z, false, k++);
		}
		sign(x0 + 2, 64, SPACCA_Z - 3, "VIA SAN", "GREGORIO", "ARMENO", "'e presepi");
	}

	/** a presepi workshop: open on the street, shelves full of figurines, candles, cornicelli, little houses */
	private void workshop(int x1, int x2, int z1, int z2, boolean westSide, int k) {
		Block wall = COLOURS[Math.floorMod(k * 3, COLOURS.length)];
		int front = westSide ? x2 : x1, dir = westSide ? 1 : -1;
		fill(x1, 64, z1, x2, 76, z2, wall);
		fill(x1 + 1, 64, z1 + 1, x2 - 1, 75, z2 - 1, Blocks.AIR);
		fill(x1 + 1, 68, z1 + 1, x2 - 1, 68, z2 - 1, Blocks.SPRUCE_PLANKS);
		fill(x1, 77, z1, x2, 77, z2, Blocks.SMOOTH_STONE_SLAB);
		fill(front, 64, z1 + 1, front, 67, z2 - 1, Blocks.AIR);                     // the whole front is open
		// display steps outside and inside, like the real shops spilling onto the street
		for (int z = z1 + 1; z <= z2 - 1; z++) {
			put(front + dir, 64, z, Blocks.SPRUCE_SLAB);
			put(front + dir, 65, z, figurine(z + k));
			int back = westSide ? x1 + 1 : x2 - 1;
			put(back, 64, z, Blocks.SPRUCE_PLANKS);
			put(back, 65, z, Blocks.SPRUCE_SLAB);
			put(back, 66, z, figurine(z * 3 + k));
			put(back, 67, z, Math.floorMod(z, 2) == 0 ? block("red_candle") : Blocks.FLOWER_POT);
		}
		// a little presepe in the middle: a manger, the grotto, the star
		int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
		put(mx, 64, mz, Blocks.HAY_BLOCK);
		put(mx, 65, mz, Blocks.MOSSY_COBBLESTONE_WALL);
		put(mx, 67, mz, Blocks.END_ROD);
		hang(mx, 67, mz + 1);
		// cornicelli hanging at the entrance (red candles under the lintel)
		for (int z = z1 + 1; z <= z2 - 1; z += 2)
			set(front, 67, z, block("red_candle").defaultBlockState().setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, k % 2 == 0));
		if (k % 3 == 0) sign(front + dir, 68, z2 - 1, "PRESEPI", "Bottega n. " + (k + 1));
	}

	/** what passes for a figurine: little heads, pots and lanterns of all colours */
	private Block figurine(int i) {
		Block[] f = {Blocks.PLAYER_HEAD, Blocks.DECORATED_POT, block("white_candle"), Blocks.POTTED_FERN, Blocks.FLOWER_POT,
				block("yellow_candle"), Blocks.PLAYER_HEAD, Blocks.LANTERN, Blocks.POTTED_RED_TULIP, Blocks.PLAYER_HEAD};
		return f[Math.floorMod(i, f.length)];
	}

	// ================================================================ Largo Maradona
	private void largoMaradona() {
		int z0 = SPACCA_Z;
		basoli(-76, z0 - 12, -64, z0 + 8);
		// the wall with the mural, on the west side of the little square, facing east
		int wx = -76;
		fill(wx, 64, z0 - 12, wx, 87, z0 + 9, block("white_terracotta"));
		String[] art = {
				"....................",
				".....KKKKKK.........",
				"....KKKKKKKK........",
				"...KKKKKKKKKK.......",
				"...KKSSSSSSKK..B.BBB",
				"...KSSSSSSSSK.BB.B.B",
				"...KSDDSSDDSK..B.B.B",
				"...KSSSSSSSSK..B.B.B",
				"...KSSSSDSSSK..B.B.B",
				"....SSDDDDSS...B.B.B",
				".....SSSSSS...BBBBBB",
				"......SSSS..........",
				"....BBBWWBBB........",
				"..BBBBBBWWBBBBB.....",
				".BBBBBBBBBBBBBBB....",
				".BBBBBBBBBBBBBBB....",
				".BBBBBBBBBBBBBBB....",
				"...................."};
		for (int row = 0; row < art.length; row++) for (int col = 0; col < art[row].length(); col++) {
			char c = art[row].charAt(col);
			Block b = switch (c) {
				case 'K' -> block("black_concrete");
				case 'S' -> block("orange_terracotta");
				case 'D' -> block("brown_concrete");
				case 'B' -> block("light_blue_concrete");
				case 'W' -> block("white_concrete");
				default -> block("white_concrete");
			};
			put(wx, 85 - row, z0 + 8 - col, b);                    // facing east: columns run from south to north
		}
		// the frame in Napoli azure
		for (int z = z0 + 9; z >= z0 - 12; z--) { put(wx, 86, z, block("blue_concrete")); put(wx, 67, z, block("blue_concrete")); }
		for (int y = 67; y <= 86; y++) { put(wx, y, z0 + 9, block("blue_concrete")); put(wx, y, z0 - 12, block("blue_concrete")); }
		// the shrine at its foot: candles, flowers, scarves, the "10"
		fill(wx + 1, 64, z0 - 6, wx + 1, 64, z0 + 4, Blocks.SMOOTH_QUARTZ_SLAB);
		for (int z = z0 - 6; z <= z0 + 4; z++) {
			if (Math.floorMod(z, 2) == 0) set(wx + 1, 65, z, block("white_candle").defaultBlockState().setValue(BlockStateProperties.CANDLES, 4).setValue(BlockStateProperties.LIT, true));
			else put(wx + 1, 65, z, Math.floorMod(z, 4) == 1 ? Blocks.POTTED_BLUE_ORCHID : Blocks.POTTED_WHITE_TULIP);
		}
		for (int z = z0 - 8; z <= z0 + 6; z += 7)
			set(wx + 1, 70, z, block("light_blue_wall_banner").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		sign(wx + 3, 64, z0 - 1, "LARGO", "MARADONA", "D10S", "'o pibe 'e Napule");
		lamp(-66, z0 - 10);
		lamp(-66, z0 + 6);
	}

	// ================================================================ Piazza Bellini
	/** east of Via Toledo: cafes with umbrellas around the sunken remains of the Greek walls, trees, benches */
	private void piazzaBellini() {
		basoli(9, -100, 70, -70);
		// the Greek walls: a sunken pit with blocks of tuff, behind a railing
		int x1 = 28, x2 = 48, z1 = -91, z2 = -80;
		fill(x1, 61, z1, x2, 63, z2, Blocks.AIR);
		fill(x1, 60, z1, x2, 60, z2, Blocks.COARSE_DIRT);
		for (int x = x1 + 2; x <= x2 - 2; x++) {
			int h = 61 + Math.floorMod(x * 7, 3);
			fill(x, 61, z1 + 4, x, h, z1 + 5, Math.floorMod(x, 3) == 0 ? Blocks.TUFF : Blocks.TUFF_BRICKS);
		}
		for (int x = x1 - 1; x <= x2 + 1; x++) for (int z : new int[] {z1 - 1, z2 + 1}) { put(x, 64, z, Blocks.IRON_BARS); toConnect.add(new BlockPos(x, 64, z)); }
		for (int z = z1 - 1; z <= z2 + 1; z++) for (int x : new int[] {x1 - 1, x2 + 1}) { put(x, 64, z, Blocks.IRON_BARS); toConnect.add(new BlockPos(x, 64, z)); }
		sign(x2 + 2, 64, z2 + 2, "PIAZZA BELLINI", "'e mura greche", "Neapolis, IV sec. a.C.");
		// cafe tables with umbrellas on the north and south edges
		for (int x = 14; x <= 64; x += 8) { cafeTable(x, -74, x % 16 == 14); cafeTable(x, -96, x % 16 == 6); }
		// trees in planters
		for (int x : new int[] {20, 56}) for (int z : new int[] {-84, -88}) tree(x, z);
		for (int x = 12; x <= 68; x += 14) lamp(x, -85);
		// a row of palazzi along the north side of the square (fronts face south, onto the square)
		int i = 40;
		for (int x = 10; x <= 62; x += 8) palazzo(x, x + 7, -101, -111, true, 3 + (i % 2), i++);
	}

	private void cafeTable(int x, int z, boolean red) {
		put(x, 64, z, Blocks.SPRUCE_FENCE);
		put(x, 65, z, Blocks.SPRUCE_FENCE);
		put(x, 66, z, Blocks.SPRUCE_FENCE);
		Block a = block(red ? "red_wool" : "green_wool"), w = block("white_wool");
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(x + dx, 67, z + dz, (dx + dz) % 2 == 0 ? a : w);
		for (int dx : new int[] {-2, 2}) {
			put(x + dx, 64, z, Blocks.BIRCH_FENCE);
			put(x + dx, 65, z, block("white_carpet"));
			toConnect.add(new BlockPos(x + dx, 64, z));
		}
		toConnect.add(new BlockPos(x, 64, z));
	}

	private void tree(int x, int z) {
		fill(x - 1, 64, z - 1, x + 1, 64, z + 1, Blocks.STONE_BRICK_SLAB);
		put(x, 64, z, Blocks.GRASS_BLOCK);
		fill(x, 65, z, x, 68, z, Blocks.OAK_LOG);
		BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++)
			if (Math.abs(dx) + Math.abs(dz) + dy <= 3 && !(dx == 0 && dz == 0 && dy == 0)) set(x + dx, 68 + dy, z + dz, leaf);
	}

	// ================================================================ helpers
	private void lamp(int x, int z) {
		fill(x, 64, z, x, 67, z, Blocks.DEEPSLATE_BRICK_WALL);
		put(x, 68, z, Blocks.LANTERN);
		toConnect.add(new BlockPos(x, 64, z));
	}

	private void hang(int x, int y, int z) {
		BlockPos above = new BlockPos(x, y + 1, z);
		if (world.getBlockState(above).isAir()) return;   // a hanging lantern needs something to hang from
		set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
	}

	private void sign(int x, int y, int z, String... lines) {
		BlockPos pos = new BlockPos(x, y, z);
		world.setBlock(pos, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8), Block.UPDATE_CLIENTS);
		if (world.getBlockEntity(pos) instanceof SignBlockEntity s) {
			var t = SignText.EMPTY.asMutable();
			for (int i = 0; i < lines.length && i < 4; i++) t.setLine(i, Component.literal(lines[i]));
			s.setText(t.asImmutable(), SignTextSlot.FRONT);
			s.setText(t.asImmutable(), SignTextSlot.BACK);
			s.setChanged();
		}
	}

	private void connect() {
		for (BlockPos p : toConnect) world.setBlock(p, Block.updateFromNeighbourShapes(world.getBlockState(p), world, p), Block.UPDATE_CLIENTS);
	}

	private void set(int x, int y, int z, BlockState st) { world.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); }
	private void put(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }
	private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
		BlockState st = b.defaultBlockState();
		for (BlockPos p : BlockPos.betweenClosed(Math.min(x1, x2), y1, Math.min(z1, z2), Math.max(x1, x2), y2, Math.max(z1, z2)))
			world.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
	private static Block block(String n) { return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(n)); }
}
