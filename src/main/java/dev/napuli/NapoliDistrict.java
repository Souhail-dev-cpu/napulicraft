package dev.napuli;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * City upgrade v3: the map grows east. A COMPLETE Galleria Umberto I (four glass-vaulted arms, octagonal dome,
 * zodiac mosaic, 16 shops: the vendors get their own shop), the Comando Carabinieri with a "gazzella" and a real
 * prison (walls, towers, 10 cells, yard), more homes. The old cut-off gallery becomes Galleria Principe's square
 * with market stalls, the old small caserma becomes the market.
 */
public final class NapoliDistrict {
	// ---- new landmarks (used by the rest of the mod)
	static final int GX = 118, GZ = -40, ARM = 26;               // Galleria centre and arm half-length
	static final BlockPos GALLERIA = new BlockPos(GX, 64, GZ);
	public static final BlockPos PUTECA = new BlockPos(GX + 22, 64, GZ - 9);   // Rafele's shop (east arm, north side)
	static final BlockPos SARTORIA = new BlockPos(GX - 22, 64, GZ - 9); // Pascà's shop (west arm, north side)
	static final BlockPos BUSKER_SPOT = new BlockPos(GX + 3, 64, GZ + 3);
	static final BlockPos LUCIA_SPOT = new BlockPos(GX - 3, 64, GZ + 2);
	static final BlockPos COMANDO_DOOR = new BlockPos(105, 64, 8);
	static final BlockPos PRISON_GATE = new BlockPos(136, 64, 7);
	static final List<BlockPos> CELLS = new ArrayList<>();
	static {
		for (int i = 0; i < 5; i++) {
			int z = 17 + i * 5;
			CELLS.add(new BlockPos(131, 64, z));   // west row (inside x 129..133), bars at x=134
			CELLS.add(new BlockPos(141, 64, z));   // east row (inside x 139..143), bars at x=138
		}
	}

	private final ServerLevel world;
	private final RandomSource random;
	private final List<BlockPos> toConnect = new ArrayList<>();

	private NapoliDistrict(ServerLevel world) { this.world = world; this.random = world.getRandom(); }

	static void build(ServerLevel world) {
		Napuli.LOGGER.info("Napoli v3: building the east district (Galleria, Comando, carcere)");
		NapoliDistrict d = new NapoliDistrict(world);
		d.oldGalleryToSquare();
		d.oldCasermaToMarket();
		d.ground();
		d.galleria();
		d.comando();
		d.carcere();
		d.homes();
		d.connectAll();
		d.moveResidents();
	}

	// ================================================================ the old places
	/** the old gallery was cut by the bar and the houses: take it down, a square with stalls takes its place */
	private void oldGalleryToSquare() {
		fill(13, 64, -16, 50, 96, 2, Blocks.AIR);            // east-west arm + dome
		fill(25, 64, -23, 37, 96, 7, Blocks.AIR);            // north-south arm (the bar's wall at x=24 stays)
		fill(13, 63, -16, 50, 63, 2, Blocks.STONE_BRICKS);
		fill(25, 63, -23, 37, 63, 7, Blocks.STONE_BRICKS);
		fill(13, 63, -4, 50, 63, 4, Blocks.POLISHED_DEEPSLATE); // the main road again
		for (int x = 16; x <= 46; x += 10) stall(x, -12, x % 20 == 6 ? "red_wool" : "green_wool");
		for (int x = 16; x <= 46; x += 15) lamp(x, 6);
		sign(31, 64, -7, "PIAZZETTA", "d' 'o mercato", "Galleria → est");
	}

	private void oldCasermaToMarket() {
		fill(52, 64, -30, 72, 72, -8, Blocks.AIR);
		fill(52, 63, -30, 72, 63, -8, Blocks.STONE_BRICKS);
		for (int x = 55; x <= 67; x += 6) { stall(x, -24, "yellow_wool"); stall(x, -15, "blue_wool"); }
		sign(62, 64, -9, "MERCATO", "Frutta, pesce", "e tutto 'o riesto");
	}

	/** a market stall: striped awning, counter of barrels, fruit on top */
	private void stall(int x, int z, String awning) {
		for (int dx = 0; dx <= 3; dx++) {
			put(x + dx, 64, z, Blocks.BARREL);
			put(x + dx, 67, z - 1, block(dx % 2 == 0 ? awning : "white_wool"));
			put(x + dx, 67, z, block(dx % 2 == 0 ? awning : "white_wool"));
		}
		for (int y = 64; y <= 66; y++) { put(x, y, z - 1, Blocks.OAK_FENCE); put(x + 3, y, z - 1, Blocks.OAK_FENCE); }
		put(x + 1, 65, z, Blocks.MELON); put(x + 2, 65, z, Blocks.PUMPKIN); put(x + 3, 65, z, Blocks.HAY_BLOCK);
	}

	// ================================================================ new ground
	private void ground() {
		fill(74, 63, -72, 176, 63, 56, Blocks.STONE_BRICKS);
		fill(74, 63, -4, 176, 63, 4, Blocks.POLISHED_DEEPSLATE);   // Via Toledo goes on
		for (int x = 74; x <= 176; x += 6) put(x, 63, 0, Blocks.CALCITE);
		for (int x = 78; x <= 174; x += 12) { lamp(x, -6); lamp(x, 6); }
		fill(GX - 13, 63, GZ + ARM + 1, GX + 13, 63, -5, Blocks.POLISHED_DIORITE); // the little square in front
		sign(80, 64, -6, "VIA TOLEDO", "Galleria Umberto I ↑", "Carabinieri ↓");
	}

	// ================================================================ Galleria Umberto I
	private interface Map2 { BlockPos at(int a, int d, int y); }

	private void galleria() {
		Map2 ew = (a, d, y) -> new BlockPos(GX + a, y, GZ + d);
		Map2 ns = (a, d, y) -> new BlockPos(GX + d, y, GZ + a);
		arm(ew, true);
		arm(ns, false);
		crossing();
		for (int s : new int[] {-1, 1}) { entrance(ew, s * ARM); entrance(ns, s * ARM); }
		signAt(new BlockPos(GX, 77, GZ + ARM + 1), Direction.SOUTH, "GALLERIA", "UMBERTO I", "1890");
		shops(ew, ns);
	}

	/** one arm: shops at ground level, upper facades, the glass barrel vault on iron ribs */
	private void arm(Map2 m, boolean eastWest) {
		for (int a = -ARM; a <= ARM; a++) {
			if (Math.abs(a) <= 11) continue; // the crossing is built separately
			for (int d = -11; d <= 11; d++) {
				int ad = Math.abs(d);
				set(m.at(a, d, 63), (Math.abs(a) % 4 == 0 && ad <= 5) || ad == 5 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState()
						: (a + d) % 2 == 0 ? Blocks.POLISHED_DIORITE.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState());
				if (ad <= 4) {
					fillCol(m.at(a, d, 64), 78, Blocks.AIR);
				} else if (ad == 5) {
					shopFront(m, a, d);
					for (int y = 69; y <= 78; y++) set(m.at(a, d, y), upperFacade(a, y).defaultBlockState());
				} else {
					fillCol(m.at(a, d, 64), 78, ad == 11 ? Blocks.SMOOTH_SANDSTONE : Blocks.SMOOTH_SANDSTONE);
					set(m.at(a, d, 79), Blocks.STONE_BRICK_SLAB.defaultBlockState());
					if (ad == 11 && (Math.abs(a) % 4 == 2)) for (int y = 66; y <= 76; y += 5) set(m.at(a, d, y), block("light_blue_stained_glass").defaultBlockState());
				}
				// the vault
				if (ad <= 5) {
					int top = 79 + (int) Math.round(5 * Math.sqrt(Math.max(0, 1 - (d / 6.0) * (d / 6.0))));
					boolean rib = Math.abs(a) % 4 == 0 || d == 0;
					int from = ad == 5 ? 79 : top;
					for (int y = from; y <= top; y++) set(m.at(a, d, y), (rib ? Blocks.IRON_BLOCK : Blocks.GLASS).defaultBlockState());
				}
			}
			if (Math.abs(a) % 8 == 4) { hang(m.at(a, 0, 77)); }
		}
	}

	private Block upperFacade(int a, int y) {
		if (Math.abs(a) % 4 == 0) return y == 74 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.QUARTZ_PILLAR;
		if (y == 69 || y == 74 || y == 78) return Blocks.SMOOTH_QUARTZ;
		if ((y >= 70 && y <= 72) || (y >= 75 && y <= 77)) return block("brown_stained_glass");
		return Blocks.SMOOTH_SANDSTONE;
	}

	/** ground floor of the arm wall facing the corridor: plinth, shop windows, door in the middle of each shop */
	private void shopFront(Map2 m, int a, int d) {
		int rel = Math.floorMod(Math.abs(a) - 12, 7); // 0 = wall between shops, 3 = door
		for (int y = 64; y <= 68; y++) {
			BlockPos p = m.at(a, d, y);
			if (rel == 0) { set(p, (y == 68 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.QUARTZ_PILLAR).defaultBlockState()); continue; }
			if (y == 68) { set(p, Blocks.SMOOTH_QUARTZ.defaultBlockState()); continue; }
			if (rel == 3 && y <= 66) { set(p, Blocks.AIR.defaultBlockState()); continue; }
			if (y == 64 || y == 67) { set(p, Blocks.DARK_OAK_PLANKS.defaultBlockState()); continue; }
			set(p, Blocks.GLASS_PANE.defaultBlockState());
			toConnect.add(p);
		}
	}

	private void crossing() {
		for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
			int ax = Math.abs(dx), az = Math.abs(dz);
			if (ax > 11 && az > 11) continue;
			int oct = Math.max(ax, az) + Math.min(ax, az) / 2;
			BlockPos floor = new BlockPos(GX + dx, 63, GZ + dz);
			boolean open = ax <= 4 || az <= 4 || oct <= 11;
			if (ax <= 11 && az <= 11) {
				if (open) {
					fillCol(floor.above(), 78, Blocks.AIR);
					int r2 = dx * dx + dz * dz;
					// the zodiac mosaic: rings and rays in copper, blackstone and quartz
					Block f = r2 <= 2 ? Blocks.GOLD_BLOCK
							: (r2 >= 20 && r2 <= 26) ? Blocks.POLISHED_BLACKSTONE
							: (r2 >= 42 && r2 <= 50) ? block("cut_copper")
							: (dx == 0 || dz == 0 || ax == az) && r2 < 42 ? Blocks.CHISELED_QUARTZ_BLOCK
							: (dx + dz) % 2 == 0 ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_QUARTZ;
					set(floor, f.defaultBlockState());
				} else {
					fillCol(floor.above(), 78, Blocks.SMOOTH_SANDSTONE);
					set(floor, Blocks.SMOOTH_STONE.defaultBlockState());
					if (oct == 12 || oct == 13) for (int y = 64; y <= 76; y += 4) set(floor.atY(y), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
				}
			}
			// the dome
			double r = Math.sqrt(dx * dx + dz * dz);
			if (r <= 12.5) {
				int top = 79 + (int) Math.round(15 * Math.sqrt(Math.max(0, 1 - (r / 12.5) * (r / 12.5))));
				boolean rib = dx == 0 || dz == 0 || ax == az;
				int from = r >= 10 ? 79 : top;
				for (int y = from; y <= top; y++) set(floor.atY(y), (rib ? Blocks.IRON_BLOCK : block("light_blue_stained_glass")).defaultBlockState());
			}
		}
		set(new BlockPos(GX, 95, GZ), Blocks.IRON_BLOCK.defaultBlockState());
		set(new BlockPos(GX, 96, GZ), block("lightning_rod").defaultBlockState());
		for (int[] c : new int[][] {{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) hang(new BlockPos(GX + c[0], 78, GZ + c[1]));
	}

	/** monumental entrance at the end of an arm: columns, a big arch, a pediment */
	private void entrance(Map2 m, int a) {
		for (int d = -11; d <= 11; d++) {
			int ad = Math.abs(d);
			for (int y = 64; y <= 85; y++) {
				int archTop = ad <= 4 ? 71 + (int) Math.round(Math.sqrt(25 - ad * ad)) : 0;
				BlockPos p = m.at(a, d, y);
				if (ad <= 4 && y <= archTop) { set(p, Blocks.AIR.defaultBlockState()); continue; }
				int pedimentTop = 85 - ad / 2;
				if (y > pedimentTop) continue;
				Block b = (ad == 6 || ad == 9) && y <= 79 ? Blocks.QUARTZ_PILLAR
						: y == 80 ? Blocks.CHISELED_QUARTZ_BLOCK
						: ad <= 4 && y == archTop + 1 ? Blocks.CHISELED_QUARTZ_BLOCK
						: Blocks.SMOOTH_QUARTZ;
				set(p, b.defaultBlockState());
			}
		}
	}

	// ---------------------------------------------------------------- the 16 shops
	private enum Shop { PUTECA, SARTORIA, PASTICCERIA, LIBRERIA, CAFFE, CURNICIELLE, GIOIELLERIA, BOUTIQUE }

	private void shops(Map2 ew, Map2 ns) {
		// Rafele's grocery and Pascà's tailor shop have fixed places (their NPCs live there); the others rotate
		Shop[] others = {Shop.PASTICCERIA, Shop.CAFFE, Shop.LIBRERIA, Shop.CURNICIELLE, Shop.GIOIELLERIA, Shop.BOUTIQUE};
		int n = 0;
		for (Map2 m : new Map2[] {ew, ns})
			for (int half : new int[] {1, -1})
				for (int k = 0; k < 2; k++)
					for (int side : new int[] {-1, 1}) {
						int a0 = half * (13 + 7 * k);          // first block of the shop along the arm
						Shop type;
						if (m == ew && k == 1 && side == -1 && half == 1) type = Shop.PUTECA;
						else if (m == ew && k == 1 && side == -1 && half == -1) type = Shop.SARTORIA;
						else type = others[n++ % others.length];
						shop(m, a0, half, side, type);
					}
	}

	/** inside one shop: floor, counter, shelves on the back wall, lamp, the goods of its trade, sign over the door */
	private void shop(Map2 m, int a0, int half, int side, Shop type) {
		int[] along = new int[6];
		for (int i = 0; i < 6; i++) along[i] = a0 + half * i;
		for (int a : along) for (int ad = 6; ad <= 10; ad++) {
			fillCol(m.at(a, side * ad, 64), 67, Blocks.AIR);
			set(m.at(a, side * ad, 63), (type == Shop.CAFFE || type == Shop.PASTICCERIA ? Blocks.SMOOTH_QUARTZ : Blocks.SPRUCE_PLANKS).defaultBlockState());
		}
		int mid = a0 + half * 2;
		hang(m.at(mid, side * 8, 67));
		// counter across the shop, with a gap to walk behind it
		for (int i = 1; i <= 4; i++) set(m.at(a0 + half * i, side * 8, 64), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
		set(m.at(a0 + half * 4, side * 8, 64), Blocks.AIR.defaultBlockState());
		switch (type) {
			case PUTECA -> {
				for (int i = 0; i < 6; i++) { loot(m.at(along[i], side * 10, 64)); set(m.at(along[i], side * 10, 65), (i % 3 == 0 ? Blocks.MELON : i % 3 == 1 ? Blocks.PUMPKIN : Blocks.HAY_BLOCK).defaultBlockState()); }
				set(m.at(a0 + half, side * 8, 65), Blocks.CAKE.defaultBlockState());
				shopSign(m, a0, half, side, "PUTECA", "'E RAFELE", "alimentari");
			}
			case SARTORIA -> {
				String[] wool = {"white_wool", "light_blue_wool", "red_wool", "black_wool", "gray_wool", "blue_wool"};
				for (int i = 0; i < 6; i++) { set(m.at(along[i], side * 10, 64), block(wool[i]).defaultBlockState()); set(m.at(along[i], side * 10, 65), block(wool[(i + 2) % 6]).defaultBlockState()); }
				set(m.at(a0 + half * 2, side * 7, 64), Blocks.LOOM.defaultBlockState());
				set(m.at(a0 + half * 1, side * 7, 64), Blocks.CRAFTING_TABLE.defaultBlockState());
				shopSign(m, a0, half, side, "SARTORIA", "PASCA'", "su misura");
			}
			case PASTICCERIA -> {
				for (int i = 1; i <= 3; i++) set(m.at(a0 + half * i, side * 8, 65), Blocks.CAKE.defaultBlockState());
				for (int i = 0; i < 6; i++) loot(m.at(along[i], side * 10, 64));
				shopSign(m, a0, half, side, "PASTICCERIA", "Sfogliatelle", "e babà");
			}
			case LIBRERIA -> {
				for (int i = 0; i < 6; i++) { set(m.at(along[i], side * 10, 64), Blocks.BOOKSHELF.defaultBlockState()); set(m.at(along[i], side * 10, 65), Blocks.BOOKSHELF.defaultBlockState()); set(m.at(along[i], side * 10, 66), Blocks.BOOKSHELF.defaultBlockState()); }
				set(m.at(a0 + half * 2, side * 7, 64), Blocks.LECTERN.defaultBlockState());
				shopSign(m, a0, half, side, "LIBRERIA", "Libri 'e", "Napule");
			}
			case CAFFE -> {
				set(m.at(a0 + half * 2, side * 8, 65), Blocks.BREWING_STAND.defaultBlockState());
				for (int i = 0; i < 6; i += 2) loot(m.at(along[i], side * 10, 64));
				for (int i = 1; i < 6; i += 2) { set(m.at(along[i], side * 10, 64), Blocks.OAK_FENCE.defaultBlockState()); set(m.at(along[i], side * 10, 65), block("white_carpet").defaultBlockState()); }
				shopSign(m, a0, half, side, "CAFFE'", "GAMBRINIELLO", "dal 1860");
			}
			case CURNICIELLE -> {
				for (int i = 0; i < 6; i++) { set(m.at(along[i], side * 10, 64), Blocks.QUARTZ_BLOCK.defaultBlockState()); candle(m.at(along[i], side * 10, 65), "red_candle"); }
				shopSign(m, a0, half, side, "CURNICIELLE", "Portafortuna", "e ricordini");
			}
			case GIOIELLERIA -> {
				for (int i = 1; i <= 3; i++) set(m.at(a0 + half * i, side * 8, 64), Blocks.GLASS.defaultBlockState());
				for (int i = 0; i < 6; i++) loot(m.at(along[i], side * 10, 64));
				shopSign(m, a0, half, side, "GIOIELLERIA", "Coralli e oro", "");
			}
			default -> {
				for (int i = 0; i < 6; i++) loot(m.at(along[i], side * 10, 64));
				shopSign(m, a0, half, side, "BOUTIQUE", "Moda", "napuletana");
			}
		}
	}

	private void shopSign(Map2 m, int a0, int half, int side, String... lines) {
		BlockPos p = m.at(a0 + half * 2, side * 4, 67); // right over the door
		// facing the corridor centre
		BlockPos wall = m.at(a0 + half * 2, side * 5, 67);
		Direction face = Direction.getApproximateNearest(p.getX() - wall.getX(), 0, p.getZ() - wall.getZ());
		set(p, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face));
		text(p, lines);
	}

	// ================================================================ Comando Carabinieri
	private void comando() {
		int x1 = 92, x2 = 118, z1 = 10, z2 = 30;
		fill(x1, 64, z1, x2, 72, z2, Blocks.SMOOTH_SANDSTONE);
		fill(x1 + 1, 64, z1 + 1, x2 - 1, 67, z2 - 1, Blocks.AIR);           // ground floor
		fill(x1 + 1, 69, z1 + 1, x2 - 1, 71, z2 - 1, Blocks.AIR);           // first floor
		fill(x1 - 1, 73, z1 - 1, x2 + 1, 73, z2 + 1, Blocks.POLISHED_ANDESITE);
		fill(x1, 68, z1, x2, 68, z1, block("blue_concrete"));
		fill(x1, 72, z1, x2, 72, z1, block("blue_concrete"));
		for (int x = x1 + 3; x <= x2 - 3; x += 4) { fill(x, 65, z1, x, 66, z1, Blocks.GLASS_PANE); fill(x, 70, z1, x, 71, z1, Blocks.GLASS_PANE); toConnect.add(new BlockPos(x, 65, z1)); toConnect.add(new BlockPos(x, 66, z1)); toConnect.add(new BlockPos(x, 70, z1)); toConnect.add(new BlockPos(x, 71, z1)); }
		fill(104, 64, z1, 106, 66, z1, Blocks.AIR);                        // door
		Block[] flag = {block("green_wall_banner"), block("white_wall_banner"), block("red_wall_banner")};
		// seen from the road (looking south) green must be on the left = east
		for (int i = 0; i < 3; i++) set(new BlockPos(106 - i, 69, z1 - 1), flag[i].defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		signAt(new BlockPos(105, 67, z1 - 1), Direction.NORTH, "COMANDO", "CARABINIERI", "Napoli");
		// inside: reception, offices, armoury, stairs
		fill(100, 64, 14, 110, 64, 14, Blocks.SPRUCE_PLANKS);
		facing(105, 64, 15, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
		for (int x = 95; x <= 115; x += 5) { put(x, 64, 22, Blocks.SPRUCE_PLANKS); facing(x, 64, 23, Blocks.SPRUCE_STAIRS, Direction.SOUTH); put(x, 65, 22, Blocks.LANTERN); }
		for (int z = 25; z <= 29; z++) { put(93, 64, z, Blocks.BARREL); put(93, 65, z, Blocks.BARREL); }
		for (int y = 64; y <= 68; y++) set(new BlockPos(117, y, 12), Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));

		hang(new BlockPos(105, 67, 18)); hang(new BlockPos(98, 67, 25)); hang(new BlockPos(112, 67, 25));
		// courtyard behind with the gazzella
		fill(x1, 63, z2 + 1, x2, 63, z2 + 10, Blocks.SMOOTH_STONE);
		gazzella(100, z2 + 4);
		lamp(x1, z2 + 9); lamp(x2, z2 + 9);
	}

	/** the Carabinieri car: dark blue body, white roof, red stripe, blue light on top */
	private void gazzella(int x, int z) {
		fill(x, 64, z, x + 5, 64, z + 2, block("blue_concrete"));
		fill(x + 1, 65, z, x + 4, 65, z + 2, block("light_gray_stained_glass"));
		fill(x + 2, 65, z, x + 3, 65, z + 2, block("blue_concrete"));
		fill(x + 1, 66, z, x + 4, 66, z + 2, block("white_concrete"));
		fill(x, 64, z, x + 5, 64, z, block("red_concrete"));
		fill(x, 64, z + 2, x + 5, 64, z + 2, block("red_concrete"));
		put(x + 2, 67, z + 1, block("blue_stained_glass"));
		put(x + 3, 67, z + 1, Blocks.SEA_LANTERN);
		for (int[] w : new int[][] {{0, -1}, {5, -1}, {0, 3}, {5, 3}}) put(x + w[0], 64, z + w[1], block("black_concrete"));
	}

	// ================================================================ the prison
    static void installCivicLamps(ServerLevel city) {
        var template=city.getStructureTemplateManager().get(Napuli.id("lampione_civico"));
        if(template.isEmpty())return;
        var settings=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        for(var origin:java.util.List.of(new BlockPos(130,64,5),new BlockPos(140,64,5))) {
            var support=origin.offset(1,-1,0);
            if(!city.getBlockState(support).isFaceSturdy(city,support,Direction.UP))continue;
            var blocks=new java.util.ArrayList<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo>();
            for(var material:java.util.List.of(Blocks.POLISHED_DEEPSLATE,Blocks.DEEPSLATE_BRICK_WALL,Blocks.POLISHED_DEEPSLATE_SLAB,Blocks.LANTERN))
                blocks.addAll(template.get().filterBlocks(origin,settings,material));
            if(blocks.stream().anyMatch(p->CityProperty.playerPlaced(city,p.pos())||!city.isEmptyBlock(p.pos())))continue;
            for(var part:blocks)city.setBlock(part.pos(),part.state(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }


	private void carcere() {
		int x1 = 122, x2 = 152, z1 = 9, z2 = 54;
		// perimeter wall with a walkway and four towers
		for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
			if (x != x1 && x != x2 && z != z1 && z != z2) continue;
			fillCol(new BlockPos(x, 64, z), 70, Blocks.STONE_BRICKS);
			put(x, 71, z, (x + z) % 2 == 0 ? Blocks.STONE_BRICK_WALL : Blocks.AIR);
		}
		for (int[] c : new int[][] {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) tower(c[0], c[1]);
		fill(134, 64, z1, 138, 67, z1, Blocks.AIR);                      // gate
		fill(134, 64, z1, 138, 67, z1, Blocks.IRON_BARS);
		fill(135, 64, z1, 137, 66, z1, Blocks.AIR);
		for (int x = 134; x <= 138; x++) for (int y = 64; y <= 67; y++) toConnect.add(new BlockPos(x, y, z1));
		signAt(new BlockPos(133, 68, z1 - 1), Direction.NORTH, "CASA", "CIRCONDARIALE", "'O CARCERE");
		fill(x1 + 1, 63, z1 + 1, x2 - 1, 63, z2 - 1, Blocks.GRAVEL);
		// the cell block
		int bx1 = 127, bx2 = 145, bz1 = 15, bz2 = 44;
		fill(bx1, 63, bz1, bx2, 63, bz2, Blocks.POLISHED_ANDESITE);
		fill(bx1, 64, bz1, bx2, 69, bz2, Blocks.STONE_BRICKS);
		fill(bx1 + 1, 64, bz1 + 1, bx2 - 1, 68, bz2 - 1, Blocks.AIR);
		fill(bx1 - 1, 70, bz1 - 1, bx2 + 1, 70, bz2 + 1, Blocks.POLISHED_ANDESITE);
		fill(135, 64, bz1, 137, 66, bz1, Blocks.AIR);                     // door from the gate side
		// cells: two rows of five along a central corridor (x 135..137)
		for (int i = 0; i < 5; i++) {
			int z = 17 + i * 5;
			cell(128, 133, z, 134, Direction.EAST);   // west row, bars at x=134
			cell(139, 144, z, 138, Direction.WEST);   // east row, bars at x=138
		}
		for (int z = 17; z <= 41; z += 6) hang(new BlockPos(136, 68, z));
		put(136, 64, 43, Blocks.SPRUCE_PLANKS);                          // the guard's desk at the end
		facing(136, 64, 42, Blocks.SPRUCE_STAIRS, Direction.NORTH);
		// the yard for the "ora d'aria"
		fill(x1 + 2, 63, 47, x2 - 2, 63, z2 - 2, Blocks.SMOOTH_STONE);
		for (int x = x1 + 4; x <= x2 - 4; x += 6) { facing(x, 64, 51, Blocks.STONE_STAIRS, Direction.SOUTH); }
		put(137, 64, 49, Blocks.OAK_FENCE); put(137, 65, 49, Blocks.OAK_FENCE); put(137, 66, 49, block("orange_concrete")); // the basketball pole
	}

	private void cell(int xa, int xb, int z, int barsX, Direction toCorridor) {
		boolean west = barsX == 134;
		fill(xa, 64, z - 2, xb, 68, z + 2, Blocks.STONE_BRICKS);
		// inside: 5 wide, from the outer wall to the bars
		fill(west ? xa + 1 : barsX + 1, 64, z - 1, west ? barsX - 1 : xb - 1, 67, z + 1, Blocks.AIR);
		for (int dz = -1; dz <= 1; dz++) for (int y = 64; y <= 67; y++) { BlockPos p = new BlockPos(barsX, y, z + dz); set(p, Blocks.IRON_BARS.defaultBlockState()); toConnect.add(p); }
		int bedX = west ? xa + 1 : xb - 1;
		bed(bedX, 64, z + 1, Direction.NORTH, block("white_bed"));
		put(bedX, 64, z - 1, Blocks.CAULDRON);
		BlockPos window = new BlockPos(west ? xa : xb, 66, z);
		set(window, Blocks.IRON_BARS.defaultBlockState());
		toConnect.add(window);
	}

	private void tower(int x, int z) {
		fill(x - 1, 64, z - 1, x + 1, 74, z + 1, Blocks.STONE_BRICKS);
		fill(x - 1, 75, z - 1, x + 1, 75, z + 1, Blocks.STONE_BRICK_SLAB);
		put(x, 74, z, Blocks.LANTERN);
		put(x, 73, z, Blocks.AIR);
	}

	// ================================================================ more homes
	private void homes() {
		int[][] spots = {{150, -30, 3}, {163, -30, 6}, {150, -60, 9}, {163, -60, 2}, {156, 12, 5}, {156, 30, 7}};
		for (int[] h : spots) {
			NapoliMap.buildHome(world, h[0], h[1], h[2]);
			NapoliInteriors.furnishHome(world, h[0], h[1], h[2], 12 + Math.floorMod(h[2], 4) * 3);
		}
	}

	// ================================================================ people move with the map
	private void moveResidents() {
		for (NapoliResident npc : world.getEntities(Napuli.RESIDENT, r -> true)) {
			switch (npc.role()) {
				case LUCIA -> npc.relocate(LUCIA_SPOT, 180);
				case VENDOR, TAILOR, BUSKER, CARABINIERE -> npc.discard(); // they come back in their new places
				default -> {}
			}
		}
		NapoliResident.spawn(world, NapoliResident.Role.VENDOR, PUTECA, 0);
		NapoliResident.spawn(world, NapoliResident.Role.TAILOR, SARTORIA, 0);
		NapoliResident.spawn(world, NapoliResident.Role.BUSKER, BUSKER_SPOT, 180);
		for (int i = 0; i < 2; i++) NapoliResident.spawn(world, NapoliResident.Role.CARABINIERE, COMANDO_DOOR.offset(i * 2 - 1, 0, -2), 0);
	}

	// ================================================================ helpers
	private void hang(BlockPos p) {
		set(p, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
	}

	private void lamp(int x, int z) {
		for (int y = 64; y <= 67; y++) put(x, y, z, Blocks.DEEPSLATE_BRICK_WALL);
		put(x, 68, z, Blocks.LANTERN);
	}

	private void candle(BlockPos p, String name) {
		set(p, block(name).defaultBlockState().setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, true));
	}

	private void bed(int x, int y, int z, Direction toHead, Block bed) {
		BlockState foot = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toHead).setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
		set(new BlockPos(x, y, z), foot);
		world.setBlock(new BlockPos(x, y, z).relative(toHead), foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
	}

	private void facing(int x, int y, int z, Block b, Direction d) {
		BlockState st = b.defaultBlockState();
		if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
		set(new BlockPos(x, y, z), st);
	}

	private static final Item[] LOOT = {Items.BREAD, Items.GOLD_NUGGET, Items.EMERALD, Items.COOKIE, Items.APPLE, Items.CANDLE};

	private void loot(BlockPos p) {
		set(p, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		if (!(world.getBlockEntity(p) instanceof Container c)) return;
		for (int i = 0; i < 2 + random.nextInt(3); i++) {
			float r = random.nextFloat();
			ItemStack s = r < 0.15f ? new ItemStack(Napuli.PIZZA) : r < 0.25f ? new ItemStack(Napuli.CAFE) : r < 0.28f ? new ItemStack(Napuli.CORNICELLO)
					: new ItemStack(LOOT[random.nextInt(LOOT.length)], 1 + random.nextInt(3));
			c.setItem(random.nextInt(c.getContainerSize()), s);
		}
	}

	private void sign(int x, int y, int z, String... lines) { signAt(new BlockPos(x, y, z), Direction.SOUTH, lines); }

	private void signAt(BlockPos p, Direction face, String... lines) {
		set(p, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face));
		text(p, lines);
	}

	private void text(BlockPos pos, String... lines) {
		if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			var t = SignText.EMPTY.asMutable();
			for (int i = 0; i < lines.length && i < 4; i++) t.setLine(i, Component.literal(lines[i]));
			sign.setText(t.asImmutable(), SignTextSlot.FRONT);
			sign.setChanged();
		}
	}

	private void connectAll() {
		for (BlockPos p : toConnect) world.setBlock(p, Block.updateFromNeighbourShapes(world.getBlockState(p), world, p), Block.UPDATE_CLIENTS);
	}

	private void fillCol(BlockPos bottom, int topY, Block b) {
		for (int y = bottom.getY(); y <= topY; y++) set(bottom.atY(y), b.defaultBlockState());
	}

	private void set(BlockPos p, BlockState st) { world.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); }

	private void put(int x, int y, int z, Block b) { set(new BlockPos(x, y, z), b.defaultBlockState()); }

	private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
		BlockState st = b.defaultBlockState();
		for (BlockPos p : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) world.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static Block block(String n) { return CityBlocks.vanilla(n); }
}
