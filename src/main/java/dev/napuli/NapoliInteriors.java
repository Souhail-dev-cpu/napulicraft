package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
 * City upgrade (version 2): furnished homes (floors, ladders, kitchen, living room, bedrooms, altarino with candles),
 * Naples street life outside (edicole votive, Napoli flags, plastic chairs, the panaro, a "10" mural, a pizzeria),
 * the Carabinieri caserma with two cells, and the new girls. Runs once per world, also on cities built before.
 */
final class NapoliInteriors {
	static final int VERSION_NOW = 19;
	private static final AttachmentType<Integer> VERSION = AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT).buildAndRegister(Napuli.id("napoli_version"));
	static final BlockPos PIZZERIA = new BlockPos(-64, 64, -20);
	static final BlockPos GIUSY_SPOT = new BlockPos(-4, 64, 51);
	static final BlockPos SOFIA_SPOT = new BlockPos(-24, 64, -6);

	private final ServerLevel world;
	private final RandomSource random;

	private NapoliInteriors(ServerLevel world) { this.world = world; this.random = world.getRandom(); }

	static void upgrade(ServerLevel world) {
		int v = world.getAttachedOrElse(VERSION, 1);
		if (v >= VERSION_NOW) return;
		if (v < 2) {
			Napuli.LOGGER.info("Napoli v2: furnishing homes, street life");
			new NapoliInteriors(world).run();
		}
		if (v < 3) NapoliDistrict.build(world);
		if (v < 4) NapoliPlebiscito.build(world);
		if (v < 5) NapoliPlebiscito.cleanup(world);
		if (v < 6) NapoliCastello.build(world);
		if (v < 7) NapoliVesuvio.build(world);
		if (v < 8) NapoliCentro.build(world);
		if (v < 9) NapoliMunicipio.build(world);
		if (v < 10) NapoliVedute.build(world);
		if (v < 11) NapoliLocalMap.build(world);
		if (v < 12) NapoliFishingHarbour.build(world);
		if (v < 13) NapoliMergellina.build(world);
		if (v < 14) {
			NapoliMergellina.completeUpperFloors(world);
			NapoliHouseholds.build(world);
			new NapoliInteriors(world).completeEmptyHomes();
			NapoliPlebiscito.refine(world);
		}
		if (v < 15) NapoliCivicLife.seed(world);
		if (v < 16) NapoliEmploymentMap.build(world);
		if (v < 17) NapoliWestQuarter.build(world);
		if (v < 18) {
			NapoliCityScale.build(world);
			NapoliShower.upgrade(world);
		}
		if (v < 19) NapoliPublicBuildings.upgrade(world);
		world.setAttached(VERSION, VERSION_NOW);
	}

	private void completeEmptyHomes() {
		for (int[] home : homes()) {
			int x = home[0], z = home[1], index = home[2];
			int height = 12 + Math.floorMod(index, 4) * 3;
			if (!world.getBlockState(new BlockPos(x, 64 + height, z)).is(Blocks.BRICKS)) continue;
			boolean empty = true;
			for (BlockPos pos : BlockPos.betweenClosed(x + 1, 64, z + 1, x + 9, 63 + height, z + 10)) {
				if (world.getBlockState(pos).isAir() || world.getBlockState(pos).is(Blocks.LANTERN)) continue;
				empty = false;
				break;
			}
			if (empty) furnish(x, z, index, height, x == -70 && z == -26);
		}
	}

	/** furnish one of the new homes of the east district */
	static void furnishHome(ServerLevel world, int x, int z, int index, int height) {
		NapoliInteriors in = new NapoliInteriors(world);
		in.furnish(x, z, index, height, false);
		in.outside(x, z, index, height, false);
	}

	/** Same home positions and heights NapoliMap.build() uses. */
	static List<int[]> homes() {
		java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
		for (int row = 0; row < 3; row++)
			for (int house = 0; house < 4; house++) out.add(new int[] {-70 + house * 14, -62 + row * 18, row * 4 + house});
		for (int z = -62; z <= -43; z += 18) for (int x = 14; x <= 57; x += 14) out.add(new int[] {x, z, x + z});
		for (int x = 49; x <= 65; x += 14) for (int z = 10; z <= 29; z += 18) out.add(new int[] {x, z, x + z});
		return out;
	}

	private void run() {
		for (int[] h : homes()) {
			int height = 12 + Math.floorMod(h[2], 4) * 3;
			boolean pizzeria = h[0] == -70 && h[1] == -26;
			furnish(h[0], h[1], h[2], height, pizzeria);
			outside(h[0], h[1], h[2], height, pizzeria);
		}
		mural(-56, -62);
		caserma();
		NapoliResident.spawn(world, NapoliResident.Role.CARMELA, PIZZERIA, 0);
		NapoliResident.spawn(world, NapoliResident.Role.GIUSY, GIUSY_SPOT, 180);
		NapoliResident.spawn(world, NapoliResident.Role.SOFIA, SOFIA_SPOT, 90);
		for (BlockPos p : new BlockPos[] {Carabinieri.POST, Carabinieri.POST.east(2), Carabinieri.PIAZZA, Carabinieri.PIAZZA.east(2)})
			NapoliResident.spawn(world, NapoliResident.Role.CARABINIERE, p, 180);
	}

	// ---------------------------------------------------------------- inside
	private void furnish(int x, int z, int index, int height, boolean pizzeria) {
		int top = 62 + height;
		int storeys = 0;
		Block floor = index % 2 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.STRIPPED_OAK_WOOD;
		for (int f = 67; f + 3 <= top; f += 4) {
			fill(x + 1, f, z + 1, x + 9, f, z + 10, floor);
			put(x + 1, f, z + 1, Blocks.AIR); // ladder hole
			storeys++;
		}
		for (int y = 64; y <= 63 + 4 * storeys; y++)
			set(x + 1, y, z + 1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		if (pizzeria) pizzeriaRoom(x, z); else living(x, z, 64, index);
		for (int s = 1; s <= storeys; s++) {
			int y = 64 + 4 * s;
			if (s == 1) bedroom(x, z, y, index, block("red_bed"));
			else if (s == 2) bedroom(x, z, y, index + 1, index % 2 == 0 ? block("light_blue_bed") : block("yellow_bed"));
			else attic(x, z, y);
		}
	}

	/** ground floor: kitchen, table with tablecloth, sofa in front of the TV, the altarino with candles */
	private void living(int x, int z, int y, int index) {
		fill(x + 3, y - 1, z + 3, x + 7, y - 1, z + 8, index % 3 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);
		// kitchen along the east wall
		facing(x + 9, y, z + 2, Blocks.SMOKER, Direction.WEST);
		facing(x + 9, y, z + 3, Blocks.FURNACE, Direction.WEST);
		set(x + 9, y, z + 4, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		loot(x + 9, y, z + 5, Blocks.BARREL, Direction.WEST);
		put(x + 9, y + 1, z + 3, Blocks.POTTED_FERN); // basil on the stove
		// table with the tablecloth and four chairs
		put(x + 5, y, z + 5, Blocks.OAK_FENCE); put(x + 5, y + 1, z + 5, block("white_carpet"));
		put(x + 5, y, z + 6, Blocks.OAK_FENCE); put(x + 5, y + 1, z + 6, block("white_carpet"));
		facing(x + 4, y, z + 5, Blocks.OAK_STAIRS, Direction.WEST);
		facing(x + 6, y, z + 6, Blocks.OAK_STAIRS, Direction.EAST);
		facing(x + 5, y, z + 4, Blocks.OAK_STAIRS, Direction.NORTH);
		facing(x + 5, y, z + 7, Blocks.OAK_STAIRS, Direction.SOUTH);
		put(x + 5, y + 2, z + 5, Blocks.AIR);
		// sofa and TV
		facing(x + 2, y, z + 8, Blocks.DARK_OAK_STAIRS, Direction.WEST);
		facing(x + 2, y, z + 9, Blocks.DARK_OAK_STAIRS, Direction.WEST);
		put(x + 7, y, z + 9, Blocks.DARK_OAK_PLANKS);
		put(x + 7, y + 1, z + 9, block("black_concrete"));
		// the altarino: candles, flowers, a little shrine
		put(x + 3, y, z + 10, Blocks.QUARTZ_BLOCK);
		candle(x + 3, y + 1, z + 10, block("red_candle"), 3);
		put(x + 4, y, z + 10, Blocks.POTTED_RED_TULIP);
		put(x + 2, y, z + 10, Blocks.POTTED_WHITE_TULIP);
		put(x + 5, y + 2, z + 8, Blocks.LANTERN);
	}

	private void bedroom(int x, int z, int y, int variant, Block bed) {
		fill(x + 3, y - 1, z + 3, x + 8, y - 1, z + 8, Blocks.AIR); // keep the floor (carpet goes on top)
		fill(x + 3, y - 1, z + 3, x + 8, y - 1, z + 8, Blocks.SPRUCE_PLANKS);
		fill(x + 3, y, z + 4, x + 7, y, z + 8, variant % 2 == 0 ? block("light_blue_carpet") : block("white_carpet"));
		bed(x + 8, y, z + 4, Direction.NORTH, bed);
		bed(x + 7, y, z + 4, Direction.NORTH, bed);
		loot(x + 9, y, z + 7, Blocks.CHEST, Direction.WEST); // the wardrobe drawer
		loot(x + 9, y, z + 8, Blocks.BARREL, Direction.WEST);
		put(x + 9, y + 1, z + 8, Blocks.BARREL);
		put(x + 5, y, z + 10, Blocks.SPRUCE_PLANKS);
		candle(x + 5, y + 1, z + 10, block("white_candle"), 2);
		put(x + 3, y, z + 10, Blocks.POTTED_RED_TULIP);
		put(x + 6, y + 2, z + 6, Blocks.LANTERN);
	}

	private void attic(int x, int z, int y) {
		loot(x + 8, y, z + 2, Blocks.BARREL, Direction.UP);
		put(x + 9, y, z + 2, Blocks.BARREL);
		put(x + 9, y + 1, z + 2, Blocks.BARREL);
		put(x + 7, y, z + 9, Blocks.HAY_BLOCK);
		put(x + 3, y + 2, z + 9, Blocks.COBWEB);
		put(x + 5, y, z + 5, Blocks.LANTERN);
	}

	/** Pizzeria da Carmela: wood-fired oven (brick dome around a fire), counter, tables outside */
	private void pizzeriaRoom(int x, int z) {
		int y = 64;
		fill(x + 6, y, z + 1, x + 9, y + 2, z + 4, Blocks.BRICKS);
		fill(x + 7, y + 1, z + 2, x + 8, y + 1, z + 3, Blocks.AIR);
		put(x + 7, y, z + 2, Blocks.CAMPFIRE); put(x + 8, y, z + 3, Blocks.CAMPFIRE);
		put(x + 7, y + 1, z + 4, Blocks.AIR); // oven mouth
		put(x + 8, y + 3, z + 3, Blocks.BRICK_WALL);
		fill(x + 3, y, z + 6, x + 8, y, z + 6, Blocks.SMOOTH_QUARTZ);   // counter
		put(x + 4, y + 1, z + 6, Blocks.CAKE);
		loot(x + 8, y, z + 7, Blocks.BARREL, Direction.UP);
		put(x + 2, y, z + 2, Blocks.HAY_BLOCK); // flour sacks
		put(x + 2, y, z + 3, block("white_wool"));
		for (int dz : new int[] {8, 10}) {
			put(x + 3, y, z + dz, Blocks.OAK_FENCE); put(x + 3, y + 1, z + dz, block("red_carpet"));
			facing(x + 4, y, z + dz, Blocks.OAK_STAIRS, Direction.EAST);
		}
		put(x + 5, y + 2, z + 8, Blocks.LANTERN);
	}

	// ---------------------------------------------------------------- outside
	private void outside(int x, int z, int index, int height, boolean pizzeria) {
		int front = z + 12;
		// plastic chairs by the door, the classic "basso" life
		facing(x + 2, 64, front, Blocks.QUARTZ_STAIRS, Direction.NORTH);
		facing(x + 8, 64, front, Blocks.QUARTZ_STAIRS, Direction.NORTH);
		// Napoli flags between the windows
		for (int y = 69; y < 63 + height - 1; y += 4)
			set(x + 5, y, front, (index + y) % 2 == 0
					? block("light_blue_wall_banner").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
					: block("white_wall_banner").defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		// edicola votiva: a niche in the wall with candles and flowers
		if (Math.floorMod(index, 3) == 0) {
			put(x + 2, 66, z + 11, Blocks.AIR);
			candle(x + 2, 66, z + 11, block("white_candle"), 3);
			put(x + 2, 67, z + 11, Blocks.CHISELED_QUARTZ_BLOCK);
			put(x + 3, 66, z + 11, Blocks.AIR);
			put(x + 3, 66, z + 11, Blocks.POTTED_RED_TULIP);
		}
		// 'o panaro: the basket lowered from the balcony on a rope
		if (Math.floorMod(index, 4) == 1) {
			int topBalcony = 67;
			for (int y = 68; y < 63 + height; y += 4) topBalcony = y - 1;
			for (int y = topBalcony - 1; y >= 66; y--) put(x + 10, y, z + 13, Blocks.IRON_CHAIN);
			put(x + 10, 65, z + 13, Blocks.DECORATED_POT);
		}
		if (pizzeria) {
			sign(x + 5, 67, front, "PIZZERIA", "da Carmela", "Margherita", "e Marinara");
			for (int dx : new int[] {1, 9}) {
				put(x + dx, 64, front + 1, Blocks.OAK_FENCE);
				put(x + dx, 65, front + 1, block("red_carpet"));
			}
		}
	}

	/** a big "10" and FORZA NAPULE on a side wall (no faces, just the number) */
	private void mural(int x, int z) {
		int wx = x, y0 = 66;
		fill(wx, y0, z + 2, wx, y0 + 6, z + 9, block("white_concrete"));
		String[] ten = {
			"#..###",
			"#..#.#",
			"#..#.#",
			"#..#.#",
			"#..###"};
		for (int r = 0; r < ten.length; r++)
			for (int c = 0; c < ten[r].length(); c++)
				if (ten[r].charAt(c) == '#') put(wx, y0 + 5 - r, z + 3 + c, block("light_blue_concrete"));
		fill(wx, y0, z + 2, wx, y0, z + 9, block("blue_concrete"));
		set(wx - 1, 64, z + 5, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		text(new BlockPos(wx - 1, 64, z + 5), "FORZA", "NAPULE", "sempe!");
	}

	// ---------------------------------------------------------------- caserma dei Carabinieri
	private void caserma() {
		int x1 = 54, x2 = 70, z1 = -28, z2 = -10;
		fill(x1, 63, z1, x2, 63, z2, Blocks.POLISHED_ANDESITE);
		fill(x1, 64, z1, x2, 70, z2, Blocks.SMOOTH_SANDSTONE);
		fill(x1 + 1, 64, z1 + 1, x2 - 1, 69, z2 - 1, Blocks.AIR);
		fill(x1 - 1, 71, z1 - 1, x2 + 1, 71, z2 + 1, Blocks.POLISHED_ANDESITE);
		fill(x1, 69, z2, x2, 69, z2, block("blue_concrete")); // the dark blue band
		fill(x1, 69, z1, x1, 69, z2, block("blue_concrete"));
		fill(x2, 69, z1, x2, 69, z2, block("blue_concrete"));
		fill(61, 64, z2, 63, 66, z2, Blocks.AIR); // door
		for (int x : new int[] {57, 67}) fill(x, 66, z2, x, 67, z2, Blocks.GLASS_PANE);
		// Italian flag over the door
		Block[] flag = {block("green_wall_banner"), block("white_wall_banner"), block("red_wall_banner")};
		for (int i = 0; i < 3; i++)
			set(61 + i, 68, z2 + 1, flag[i].defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		set(62, 67, z2 + 1, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		text(new BlockPos(62, 67, z2 + 1), "CARABINIERI", "Stazione", "Napule Centro");
		// office: desk, chair, filing cabinets, lamp
		fill(58, 64, -15, 66, 64, -15, Blocks.SPRUCE_PLANKS);
		facing(62, 64, -16, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
		put(60, 65, -15, Blocks.LANTERN);
		for (int z = -19; z <= -17; z++) { put(55, 64, z, Blocks.BARREL); put(55, 65, z, Blocks.BARREL); }
		put(69, 64, -12, Blocks.POTTED_FERN);
		// the two cells at the back: bars in front, a bed and a bucket each
		fill(x1 + 1, 64, -23, x2 - 1, 67, -23, Blocks.IRON_BARS);
		fill(62, 64, -27, 62, 67, -24, Blocks.SMOOTH_SANDSTONE);
		fill(x1 + 1, 68, -27, x2 - 1, 68, -23, Blocks.SMOOTH_SANDSTONE);
		bed(55, 64, -26, Direction.NORTH, block("white_bed"));
		bed(69, 64, -26, Direction.NORTH, block("white_bed"));
		put(60, 64, -27, Blocks.CAULDRON);
		put(64, 64, -27, Blocks.CAULDRON);
		put(58, 67, -27, Blocks.LANTERN); put(66, 67, -27, Blocks.LANTERN);
		connectBars(x1 + 1, -23, x2 - 1);
		for (int x : new int[] {x1 - 1, x2 + 1}) { put(x, 64, z2 + 2, Blocks.DEEPSLATE_BRICK_WALL); put(x, 65, z2 + 2, Blocks.LANTERN); }
	}

	private void connectBars(int xa, int z, int xb) {
		for (int x = xa; x <= xb; x++)
			for (int y = 64; y <= 67; y++) {
				BlockPos p = new BlockPos(x, y, z);
				world.setBlock(p, Block.updateFromNeighbourShapes(world.getBlockState(p), world, p), Block.UPDATE_CLIENTS);
			}
	}

	// ---------------------------------------------------------------- helpers
	private static final Item[][] LOOT = {
		{Items.BREAD}, {Items.GOLD_NUGGET}, {Items.EMERALD}, {Items.STRING}, {Items.CANDLE}, {Items.APPLE}, {Items.COOKIE}};

	/** a container with a few household things inside (taking them in front of a carabiniere = theft) */
	private void loot(int x, int y, int z, Block block, Direction dir) {
		BlockState st = block.defaultBlockState();
		if (st.hasProperty(BlockStateProperties.FACING)) st = st.setValue(BlockStateProperties.FACING, dir);
		else if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && dir.getAxis().isHorizontal())
			st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
		BlockPos pos = new BlockPos(x, y, z);
		world.setBlock(pos, st, Block.UPDATE_CLIENTS);
		if (!(world.getBlockEntity(pos) instanceof Container c)) return;
		int n = 2 + random.nextInt(3);
		for (int i = 0; i < n; i++) {
			ItemStack s;
			float r = random.nextFloat();
			if (r < 0.12f) s = new ItemStack(Napuli.PIZZA);
			else if (r < 0.22f) s = new ItemStack(Napuli.CAFE);
			else if (r < 0.26f) s = new ItemStack(Napuli.CORNICELLO);
			else if (r < 0.30f) s = new ItemStack(Napuli.CIABATTA_ITEM);
			else {
				Item it = LOOT[random.nextInt(LOOT.length)][0];
				s = new ItemStack(it, it == Items.EMERALD ? 1 : 1 + random.nextInt(4));
			}
			c.setItem(random.nextInt(c.getContainerSize()), s);
		}
	}

	private void bed(int x, int y, int z, Direction toHead, Block bed) {
		BlockState foot = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toHead).setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
		set(x, y, z, foot);
		BlockPos head = new BlockPos(x, y, z).relative(toHead);
		world.setBlock(head, foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
	}

	private void candle(int x, int y, int z, Block candle, int count) {
		set(x, y, z, candle.defaultBlockState().setValue(BlockStateProperties.CANDLES, count).setValue(BlockStateProperties.LIT, true));
	}

	private void facing(int x, int y, int z, Block b, Direction d) {
		BlockState st = b.defaultBlockState();
		if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
		set(x, y, z, st);
	}

	private void sign(int x, int y, int z, String... lines) {
		set(x, y, z, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		text(new BlockPos(x, y, z), lines);
	}

	private void text(BlockPos pos, String... lines) {
		if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			var t = SignText.EMPTY.asMutable();
			for (int i = 0; i < lines.length && i < 4; i++) t.setLine(i, Component.literal(lines[i]));
			sign.setText(t.asImmutable(), SignTextSlot.FRONT);
			sign.setChanged();
		}
	}

	private void set(int x, int y, int z, BlockState st) {
		world.setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private void put(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }

	private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
		BlockState st = b.defaultBlockState();
		for (BlockPos p : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) world.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static Block block(String n) { return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(n)); }
}
