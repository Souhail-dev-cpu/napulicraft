package dev.napuli;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class NapoliCivicQuarter {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> PLANTING = Set.of(Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.SHORT_GRASS,
            Blocks.FLOWER_POT, Blocks.OAK_STAIRS);
    static final BlockPos BEVERELLO = new BlockPos(235,64,67);
    static final BlockPos MARITTIMA = new BlockPos(306,64,73);
    static final BlockPos GREGORIO = new BlockPos(47,64,-146);
    static final BlockPos LORENZO = new BlockPos(68,64,-166);
    static final BlockPos CONVITTO = new BlockPos(-38,64,-181);
    static final BlockPos PORT_ALBA = new BlockPos(27,64,-161);
    static final BlockPos LOWER_LIFT = new BlockPos(100,64,68);
    static final BlockPos UPPER_LIFT = LOWER_LIFT.above(20);
    private final ServerLevel city;

    private NapoliCivicQuarter(ServerLevel city) {
        this.city = city;
    }

    static void upgrade(ServerLevel city) {
        var build = new NapoliCivicQuarter(city);
        build.market();
        build.harbour();
        build.oldTown();
        build.dante();
        build.echia();
    }

    static BlockPos liftButton(BlockPos landing) {
        return landing.offset(2,1,1);
    }

    static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND
                    || level.dimension() != NapoliMap.LEVEL || player.isSpectator()) return InteractionResult.PASS;
            var button = hit.getBlockPos();
            if (button.equals(liftButton(LOWER_LIFT))) return ride(serverPlayer, LOWER_LIFT, UPPER_LIFT);
            if (button.equals(liftButton(UPPER_LIFT))) return ride(serverPlayer, UPPER_LIFT, LOWER_LIFT);
            return InteractionResult.PASS;
        });
    }

    private static InteractionResult ride(ServerPlayer player, BlockPos from, BlockPos to) {
        var city = player.level();
        if (!city.getBlockState(liftButton(from)).is(Blocks.POLISHED_BLACKSTONE_BUTTON)
                || !city.getBlockState(liftButton(to)).is(Blocks.POLISHED_BLACKSTONE_BUTTON))
            return InteractionResult.PASS;
        var landing = to.south();
        if (player.distanceToSqr(from.getX()+.5,from.getY(),from.getZ()+.5) > 25
                || !CityAccessibility.walkable(city, landing)) return InteractionResult.FAIL;
        city.playSound(null, from, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, .6f, 1);
        player.teleportTo(city, landing.getX()+.5, landing.getY(), landing.getZ()+.5, Set.of(), 0, 0, true);
        city.playSound(null, landing, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, .6f, 1);
        player.sendOverlayMessage(Component.literal(to.equals(UPPER_LIFT) ? "Monte Echia · Belvedere" : "Monte Echia · Santa Lucia"));
        return InteractionResult.SUCCESS;
    }

    private void market() {
        var piazza = NapoliDistrictLandmarks.site("mercato");
        var carmine = NapoliDistrictLandmarks.site("carmine");
        NapoliAtlasLandmarks.generate(city, piazza.offset(-32,0,-31), 164, 130);
        for (int x = -27; x <= 27; x++) for (int z = -24; z <= 24; z++) {
            var p = piazza.offset(x,0,z);
            clearPlanting(p,0,7);
            put(p.below(), Math.abs(x) >= 23 || Math.abs(z) == 22 || Math.abs(x) < 3
                    ? Blocks.SMOOTH_SANDSTONE : (x+z)%7 == 0 ? Blocks.ANDESITE : Blocks.POLISHED_ANDESITE);
        }
        for (int side : new int[] {-1,1}) {
            var fontana = piazza.offset(side * 18,0,-17);
            stoneWell(fontana);
            for (int z : new int[] {-9,1,11}) marketStall(piazza.offset(side*18,0,z), z != 1);
        }
        sign(piazza.offset(-5,0,23), "PIAZZA MERCATO", "Carmine e S. Eligio");

        int length = carmine.getX() - piazza.getX();
        for (int i = 18; i <= length; i++) {
            int x = piazza.getX()+i, z = piazza.getZ() + 6*i/10;
            for (int dz = -2; dz <= 2; dz++) stoneGround(x,z+dz, Blocks.POLISHED_ANDESITE);
        }
        var arch = piazza.offset(33,0,19);
        for (int dx = -6; dx <= 6; dx++) {
            int opening = 5 - Math.abs(dx)/2;
            int base = Math.abs(dx) > 4 ? 0 : opening;
            box(arch,dx,base,-1, dx,13,1, Blocks.CUT_SANDSTONE);
            if (Math.abs(dx) <= 4) box(arch,dx,0,-1, dx,opening-1,1, Blocks.AIR);
            put(arch.offset(dx,13,2), Blocks.SMOOTH_QUARTZ_SLAB);
        }
        NapoliMonumentInteriors.place(city, "sant_eligio_orologio", arch.offset(-3,7,1),
                (pos,state) -> state.is(Blocks.CUT_SANDSTONE),
                List.of(Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_BLACKSTONE_BUTTON));
        var eligio = arch.offset(-12,0,-6);
        hall(eligio, 5,8,10, Blocks.CUT_SANDSTONE, Blocks.TUFF_BRICKS);
        roof(eligio,5,8,10);
        portal(eligio.south(8),Direction.SOUTH,Blocks.TUFF_BRICKS);
        put(eligio.offset(0,6,8), CityBlocks.vanilla("blue_stained_glass"));
        pews(eligio, 3, -3, 4);
        altar(eligio.north(6), Blocks.CUT_SANDSTONE);
        sign(arch.offset(8,0,1), "SANT'ELIGIO", "Arco e chiesa");
        for (int i : new int[] {49,64,80}) {
            int x = piazza.getX()+i, z = piazza.getZ()+6*i/10+9;
            var shop = new BlockPos(x,NapoliExpansion.height(x,z)+1,z);
            marketStall(shop,i != 64);
        }
        carmine(carmine);
    }

    private void carmine(BlockPos p) {
        // Remove the cupola and cylindrical tower of the generic Atlas church.
        for (var at : BlockPos.betweenClosed(p.offset(-19,0,-20),p.offset(26,40,19))) {
            Block b = city.getBlockState(at).getBlock();
            if (b == Blocks.CALCITE || b == Blocks.QUARTZ_PILLAR || b == Blocks.STONE_BRICKS
                    || b == Blocks.SPRUCE_STAIRS || b == Blocks.POLISHED_ANDESITE || b == Blocks.STONE_BRICK_WALL
                    || b == Blocks.SMOOTH_QUARTZ || b == Blocks.GLASS_PANE
                    || b == Blocks.SMOOTH_STONE_SLAB || b == Blocks.SMOOTH_QUARTZ_SLAB || b == Blocks.POLISHED_ANDESITE_SLAB
                    || b == CityBlocks.vanilla("oxidized_copper")) put(at,Blocks.AIR);
        }
        hall(p,15,18,19, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE);
        roof(p,15,18,19,4);
        var plaster = CityBlocks.vanilla("orange_terracotta");
        box(p,-14,1,18, 14,18,18, plaster);
        for (int x : new int[] {-12,-6,6,12}) {
            column(p.offset(x,0,19),18,Blocks.POLISHED_DIORITE);
            put(p.offset(x,0,19),Blocks.POLISHED_ANDESITE);
            put(p.offset(x,10,19),Blocks.SMOOTH_QUARTZ);
            put(p.offset(x,17,19),Blocks.SMOOTH_QUARTZ);
            for (int z : new int[] {-1,1}) {
                int niche = x + z;
                box(p,niche,4,18, niche,6,18, Blocks.POLISHED_ANDESITE);
                put(p.offset(niche,3,19),Blocks.SMOOTH_QUARTZ_SLAB);
            }
            for (int z : new int[] {-12,-6,0,6,12}) {
                column(p.offset(x,0,z),9,Blocks.QUARTZ_PILLAR);
                box(p,x,9,z, x<0 ? x+3 : x-3,9,z, Blocks.SMOOTH_QUARTZ);
                hang(p.offset(x<0 ? x+2 : x-2,8,z));
            }
        }
        box(p,-15,11,19, 15,12,19, Blocks.SMOOTH_QUARTZ);
        box(p,-8,18,18, 8,18,19, Blocks.SMOOTH_QUARTZ);
        glazed(p.offset(0,14,18),CityBlocks.vanilla("yellow_stained_glass"));
        for (int x : new int[] {-9,9}) glazed(p.offset(x,15,18),Blocks.TINTED_GLASS);
        portal(p.south(18),Direction.SOUTH,Blocks.SMOOTH_SANDSTONE);
        box(p,-2,-1,-17, 2,-1,18, Blocks.POLISHED_DIORITE);
        pews(p, 4, -10, 12);
        for (int z : new int[] {-11,-5,1,7}) for (int s : new int[] {-1,1}) {
            altar(p.offset(13*s,0,z), Blocks.POLISHED_GRANITE);
        }
        altar(p.north(14), Blocks.SMOOTH_QUARTZ);
        put(p.offset(0,2,-16), Blocks.GOLD_BLOCK);
        for (int side : new int[] {-1,1}) box(p,2*side,1,-15, 2*side,3,-15, Blocks.GILDED_BLACKSTONE);

        var campanile = p.offset(21,0,12);
        for (int y = 0; y <= 38; y++) {
            int r = y < 12 ? 4 : y < 24 ? 3 : y < 32 ? 2 : 1;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (Math.abs(x)+Math.abs(z) > 2*r-1 && r > 1) continue;
                boolean aperture = y >= 25 && y <= 28 && (x == 0 || z == 0);
                if (!aperture) put(campanile.offset(x,y,z), y % 6 == 0 ? Blocks.SMOOTH_QUARTZ
                        : y >= 32 ? CityBlocks.vanilla("oxidized_cut_copper")
                        : y % 6 == 1 || Math.abs(x) == r || Math.abs(z) == r ? Blocks.POLISHED_DEEPSLATE : Blocks.TERRACOTTA);
            }
        }
        put(campanile.offset(0,26,0), Blocks.BELL);
        column(campanile.above(39),3,Blocks.QUARTZ_PILLAR);
        box(campanile,-1,40,0, 1,40,0, Blocks.QUARTZ_PILLAR);
        for (int x=-8; x<=8; x++) for (int z=19; z<=32; z++) {
            var at = p.offset(x,0,z);
            clearPlanting(at,0,4);
            put(at.below(), Math.abs(x)<=2 ? Blocks.CALCITE : Blocks.POLISHED_ANDESITE);
        }
        sign(p.offset(5,0,23), "BASILICA", "DEL CARMINE");
    }

    private void harbour() {
        box(BEVERELLO,-16,0,-7, 17,6,2, Blocks.AIR);
        deck(BEVERELLO, -18,-10,22,12);
        var terminal = BEVERELLO;
        hall(terminal,17,6,7,Blocks.GLASS,Blocks.SMOOTH_QUARTZ);
        for (int x = -17; x <= 17; x++) {
            for (int z = -9; z <= 9; z++)
                put(terminal.offset(x,7+Math.max(0,3-Math.abs(z)/2),z),Blocks.SMOOTH_QUARTZ);
            if (Math.floorMod(x,5) == 0) for (int z : new int[] {-7,7}) {
                column(terminal.offset(x,0,z),8,Blocks.POLISHED_ANDESITE);
                put(terminal.offset(x,5,z>0 ? 4 : -4), Blocks.SEA_LANTERN);
            }
        }
        for (int x : new int[] {-11,0,11}) {
            open(terminal.offset(x,0,-6),Direction.NORTH,2,4);
            open(terminal.offset(x,0,6),Direction.SOUTH,2,4);
        }
        open(terminal.east(17),Direction.EAST,1,3);
        // Retain the old destination marker's position but give the waiting room a clear centre aisle.
        for (int x : new int[] {-13,-7,7,13}) for (int z : new int[] {-2,2})
            put(terminal.offset(x,0,z),stair(Blocks.SMOOTH_QUARTZ_STAIRS,Direction.NORTH));
        box(terminal,-15,0,-1, -10,0,0, Blocks.SMOOTH_QUARTZ);
        put(terminal.offset(-13,1,0), Blocks.LECTERN);
        sign(terminal.offset(-8,0,-8),"MOLO BEVERELLO","Biglietti e imbarchi");

        deck(MARITTIMA,-16,-13,16,23);
        deck(new BlockPos(277,64,68),-25,-4,16,5);
        hall(MARITTIMA,14,10,11,Blocks.CALCITE,Blocks.SMOOTH_QUARTZ);
        for (int x : new int[] {-11,11}) {
            box(MARITTIMA,x-3,11,-10, x+3,18,-3, Blocks.SMOOTH_QUARTZ);
            for (int dx : new int[] {-1,1})
                box(MARITTIMA,x+dx,14,-10, x+dx,17,-10, Blocks.TINTED_GLASS);
        }
        for (int x = -13; x <= 13; x += 3) {
            for (int z : new int[] {-10,10})
                box(MARITTIMA,x,4,z, x+1,8,z, Blocks.GLASS);
            put(MARITTIMA.offset(x,6,-7), Blocks.SEA_LANTERN);
        }
        open(MARITTIMA.north(10), Direction.NORTH,3,5);
        open(MARITTIMA.south(10), Direction.SOUTH,2,4);
        for (int x : new int[] {-10,10}) {
            box(MARITTIMA,x-2,0,-1, x+2,0,0, Blocks.SMOOTH_QUARTZ);
            put(MARITTIMA.offset(x,1,0), Blocks.LECTERN);
            for (int z : new int[] {4,7}) put(MARITTIMA.offset(x,0,z),stair(Blocks.SPRUCE_STAIRS,Direction.NORTH));
        }
        sign(MARITTIMA.offset(5,0,-12),"STAZIONE","MARITTIMA");
        for (int x : new int[] {-13,13}) for (int z = 13; z <= 22; z+=3)
            put(MARITTIMA.offset(x,0,z),Blocks.POLISHED_BLACKSTONE_WALL);
        deck(BEVERELLO, -4,8,4,27);
        for (int x : new int[] {-4,4}) for (int z = 14; z<=26; z+=3)
            put(BEVERELLO.offset(x,0,z),Blocks.POLISHED_BLACKSTONE_WALL);
        sign(BEVERELLO.offset(3,0,22),"IMBARCHI","Capri e Ischia");

        // A clear, separated walking strip bends around Castel Nuovo from Municipio.
        street(212,5,218,60);
        street(214,55,239,58);
        street(236,55,238,60);
        street(246,64,307,66);
        street(303,62,309,64);
    }

    private void oldTown() {
        // Preserve the two rows of presepe workshops and enter the monastery from their north end.
        street(28,-155,49,-153);
        street(32,-158,85,-156);
        var church = GREGORIO;
        hall(church,7,6,13,Blocks.SMOOTH_SANDSTONE,Blocks.CALCITE);
        roof(church,7,6,13);
        portal(church.north(6),Direction.NORTH,Blocks.SMOOTH_QUARTZ);
        for (int side : new int[] {-1,1}) for (int z : new int[] {-3,0,3}) {
            column(church.offset(side*5,0,z),8,Blocks.QUARTZ_PILLAR);
            box(church,side*5,8,z, side,8,z, Blocks.SMOOTH_QUARTZ);
            hang(church.offset(side*3,7,z));
        }
        pews(church,2,-3,2);
        altar(church.south(4),Blocks.POLISHED_GRANITE);
        var cloister = GREGORIO.east(19);
        cloister(cloister,9,7,true);
        street(59,-155,68,-152);
        sign(church.offset(-3,0,-8),"SAN GREGORIO","Chiesa e chiostro");
        sign(cloister.offset(3,0,-9),"CHIOSTRO","Fontana al pozzo");

        var lorenzo = LORENZO;
        hall(lorenzo,12,8,16,Blocks.SMOOTH_SANDSTONE,Blocks.CALCITE);
        roof(lorenzo,12,8,16);
        portal(lorenzo.south(8),Direction.SOUTH,Blocks.SMOOTH_QUARTZ);
        for (int x : new int[] {-10,-6,6,10}) column(lorenzo.offset(x,0,8),11,Blocks.QUARTZ_PILLAR);
        glazed(lorenzo.offset(0,12,8),CityBlocks.vanilla("light_blue_stained_glass"));
        for (int x = -5; x <= 5; x++) {
            box(lorenzo,x,0,-7, x,8,-7, Blocks.CUT_SANDSTONE);
            put(lorenzo.offset(x,0,-6),Blocks.POLISHED_DIORITE);
        }
        pews(lorenzo,4,-2,5);
        altar(lorenzo.north(5),Blocks.SMOOTH_QUARTZ);
        sign(lorenzo.offset(-5,0,10),"SAN LORENZO","MAGGIORE");

        var court = LORENZO.east(24);
        cloister(court,10,8,false);
        street(82,-158,95,-155);
        var ruins = court.offset(0,-9,0);
        box(ruins,-10,-1,-9, 10,5,9, Blocks.TUFF);
        box(ruins,-9,0,-8, 9,4,8, Blocks.AIR);
        box(ruins,-2,-1,-8, 2,-1,8, Blocks.POLISHED_ANDESITE);
        for (int side : new int[] {-1,1}) for (int z : new int[] {-6,-2,2,6}) {
            box(ruins,side*4,0,z, side*8,2,z, Blocks.TUFF_BRICKS);
            put(ruins.offset(side*5,0,z+1),Blocks.DECORATED_POT);
            put(ruins.offset(side*3,0,z),Blocks.LANTERN);
        }
        stoneWell(ruins.north(6));
        for (int i = 0; i < 9; i++) {
            var step = court.offset(-6,-i-1,13-i);
            for (int x = 0; x < 2; x++) {
                put(step.east(x).below(),Blocks.TUFF);
                put(step.east(x),stair(Blocks.TUFF_BRICK_STAIRS,Direction.SOUTH));
                for (int y=1; y<=3; y++) put(step.east(x).above(y),Blocks.AIR);
            }
        }
        sign(court.offset(-3,0,14),"SCAVI ROMANI","Macellum");
    }

    private void dante() {
        // Open the west end of the existing central arch into a reception and an internal reading room.
        box(CONVITTO,-8,-1,-6, 7,-1,6, Blocks.POLISHED_DIORITE);
        box(CONVITTO,-8,0,-5, 8,5,5, Blocks.AIR);
        box(CONVITTO,-8,6,-6, 6,6,6, Blocks.CALCITE);
        for (int x : new int[] {-6,0,5}) {
            column(CONVITTO.offset(x,0,5),6,Blocks.QUARTZ_PILLAR);
            column(CONVITTO.offset(x,0,-5),6,Blocks.QUARTZ_PILLAR);
            hang(CONVITTO.offset(x,5,0));
        }
        hall(CONVITTO.west(15),7,8,7,Blocks.TERRACOTTA,Blocks.SMOOTH_SANDSTONE);
        open(CONVITTO.west(8),Direction.EAST,2,4);
        for (int z = -6; z <= 6; z += 3)
            box(CONVITTO,-21,0,z, -20,2,z, Blocks.BOOKSHELF);
        for (int z : new int[]{-4,4}) {
            box(CONVITTO,-17,0,z, -12,0,z, Blocks.DARK_OAK_PLANKS);
            put(CONVITTO.offset(-14,1,z),Blocks.LECTERN);
        }
        sign(CONVITTO.offset(5,0,3),"CONVITTO","Atrio e biblioteca");
        for (int offset = -25; offset <= 25; offset += 5) {
            if (Math.abs(offset) <= 5) continue;
            var plinth = new BlockPos(-34+offset*offset/53,80,CONVITTO.getZ()+offset);
            if (city.getBlockState(plinth).is(Blocks.SMOOTH_QUARTZ_SLAB)
                    && city.getBlockState(plinth.above()).is(Blocks.QUARTZ_PILLAR))
                put(plinth,Blocks.SMOOTH_QUARTZ);
        }
        street(8,-167,28,-164);
        street(25,-165,29,-152);
        for (int side : new int[] {-1,1}) {
            var bookshop = PORT_ALBA.offset(side*7,0,6);
            box(bookshop,-2,-1,-5, 2,-1,4, Blocks.DARK_OAK_PLANKS);
            box(bookshop,2*side,0,-4, 2*side,2,3, Blocks.BOOKSHELF);
            put(bookshop.offset(0,0,-2),Blocks.LECTERN);
            put(bookshop.offset(0,0,2),stair(Blocks.SPRUCE_STAIRS,Direction.NORTH));
            sign(bookshop.offset(-side,0,4),"LIBRERIA",side<0 ? "Libri antichi" : "Stampe e racconti");
        }
    }

    private void echia() {
        var rock = LOWER_LIFT.offset(4,0,7);
        for (int x = -21; x <= 21; x++) for (int z = -10; z <= 16; z++) {
            double rim = Math.hypot(x/21.0,(z-2)/15.0);
            if (rim > 1) continue;
            int height = rim < .74 ? 19 : Math.max(3,19-(int)((rim-.74)*54));
            for (int y = -4; y <= height; y++) {
                Block rockBlock = ((x*13 + z*23 + y*7) & 7) == 0 ? Blocks.TUFF : Blocks.SANDSTONE;
                put(rock.offset(x,y,z), y == height && height == 19 ? Blocks.SMOOTH_SANDSTONE : rockBlock);
            }
        }
        street(98,52,102,63);
        deck(LOWER_LIFT,-3,-7,3,5);
        box(LOWER_LIFT,-2,0,-7, 2,3,6, Blocks.AIR);
        box(LOWER_LIFT,-3,4,-5, 3,4,5, Blocks.TUFF_BRICKS);
        for (int y=5; y<20; y++) {
            box(LOWER_LIFT,-1,y,-2, 1,y,0, Blocks.AIR);
            box(LOWER_LIFT,-1,y,1, 1,y,1, Blocks.GLASS);
        }
        for (var landing : List.of(LOWER_LIFT,UPPER_LIFT)) {
            box(landing,-3,-1,-2, 3,-1,3, Blocks.SMOOTH_STONE);
            box(landing,-3,0,-1, 3,3,3, Blocks.AIR);
            box(landing,-3,4,-1, 3,4,3, Blocks.TUFF_BRICKS);
            box(landing,2,0,0, 2,3,0, Blocks.POLISHED_ANDESITE);
            box(landing,-2,0,0, -2,3,0, Blocks.GLASS);
            put(liftButton(landing), Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE,net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH));
            // The console is a wall-mounted button south of the kiosk's right pillar.
            put(liftButton(landing).north(), Blocks.POLISHED_ANDESITE);
        }
        box(UPPER_LIFT,-7,-1,3, 15,-1,13, Blocks.SMOOTH_SANDSTONE);
        for (int x=-7; x<=15; x++)
            put(UPPER_LIFT.offset(x,0,13), railing(x > -7,x < 15,x == -7 || x == 15,false));
        for (int z=4; z<13; z++) for (int x : new int[]{-7,15})
            put(UPPER_LIFT.offset(x,0,z),railing(false,false,z > 4,true));
        for (int x : new int[]{-4,4,12}) {
            box(UPPER_LIFT,x-1,-1,5, x+1,-1,6, Blocks.GRASS_BLOCK);
            put(UPPER_LIFT.offset(x,0,5),Blocks.AZALEA);
        }
        for (int x : new int[]{-4,4,12}) {
            put(UPPER_LIFT.offset(x,0,11),stair(Blocks.SMOOTH_SANDSTONE_STAIRS,Direction.NORTH));
            column(UPPER_LIFT.offset(x,0,13),2,Blocks.TUFF_BRICKS);
            put(UPPER_LIFT.offset(x,2,13),Blocks.LANTERN);
        }
        sign(LOWER_LIFT.offset(3,0,-4),"SANTA LUCIA","Ascensore Monte Echia");
        sign(UPPER_LIFT.offset(-2,0,7),"PIZZOFALCONE","Belvedere");
    }

    private void cloister(BlockPos p, int width, int depth, boolean garden) {
        box(p,-width,-1,-depth, width,-1,depth, Blocks.POLISHED_ANDESITE);
        for (int x=-width; x<=width; x++) for (int z=-depth; z<=depth; z++) {
            if (Math.abs(x)>width-3 || Math.abs(z)>depth-3) {
                put(p.offset(x,4,z),Blocks.SMOOTH_QUARTZ);
                if ((Math.abs(x)==width || Math.abs(z)==depth) && Math.floorMod(x+z,4)==0)
                    column(p.offset(x,0,z),4,Blocks.QUARTZ_PILLAR);
            } else if (garden && Math.abs(x)>2 && Math.abs(z)>1) {
                put(p.offset(x,-1,z),Blocks.GRASS_BLOCK);
                if ((x+z)%3==0) put(p.offset(x,0,z),Blocks.AZURE_BLUET);
            }
        }
        stoneWell(p);
    }

    private void stoneWell(BlockPos p) {
        box(p,-2,-1,-2, 2,-1,2, Blocks.CUT_SANDSTONE);
        box(p,-2,0,-2, 2,0,2, Blocks.SMOOTH_SANDSTONE);
        box(p,-1,0,-1, 1,0,1, Blocks.WATER);
        column(p.above(),2,Blocks.CHISELED_SANDSTONE);
    }

    private void marketStall(BlockPos p, boolean red) {
        clearPlanting(p,4,7);
        box(p,-4,-1,-3, 4,-1,3, Blocks.SMOOTH_STONE);
        for (int x : new int[] {-4,4}) for (int z : new int[] {-2,2})
            column(p.offset(x,0,z),3,Blocks.SPRUCE_FENCE);
        var canvas = CityBlocks.vanilla(red ? "red_wool" : "green_wool");
        for (int x=-4;x<=4;x++)
            box(p,x,3,-2, x,3,2, x%2==0 ? canvas : CityBlocks.vanilla("white_wool"));
        box(p,-3,0,-1, 3,0,0, Blocks.SPRUCE_PLANKS);
        for (int x : new int[] {-2,0,2}) put(p.offset(x,1,-1),Blocks.FLOWER_POT);
    }

    private void hall(BlockPos p,int w,int d,int height,Block wall,Block trim) {
        box(p,-w,-1,-d, w,-1,d, Blocks.POLISHED_ANDESITE);
        box(p,-w,0,-d, w,height,d, wall);
        box(p,-w+1,0,-d+1, w-1,height-1,d-1, Blocks.AIR);
        box(p,-w,height,-d, w,height,d, trim);
        if (wall==Blocks.GLASS) return;
        for (int z=-d+3;z<d-1;z+=4) for (int x : new int[]{-w,w}) {
            box(p,x,3,z, x,5,z, Blocks.GLASS);
            put(p.offset(x,2,z),trim);
        }
    }

    private void roof(BlockPos p,int w,int d,int base) {
        roof(p,w,d,base,2);
    }

    private void roof(BlockPos p,int w,int d,int base,int runPerBlock) {
        for (int x=-w-1; x<=w+1; x++) {
            int rise=Math.max(0,(w-Math.abs(x))/runPerBlock);
            box(p,x,base+rise,-d-1, x,base+rise,d+1, Blocks.BRICKS);
            if (rise>0) for (int z : new int[]{-d,d})
                box(p,x,base+1,z, x,base+rise,z, Blocks.CUT_SANDSTONE);
        }
    }

    private void open(BlockPos p, Direction facing, int width, int height) {
        for (int x=-width; x<=width; x++) for (int y=0; y<height;y++)
            put(p.relative(facing.getClockWise(),x).above(y),Blocks.AIR);
    }

    private void portal(BlockPos p,Direction facing,Block trim) {
        var side=facing.getClockWise();
        for (int x : new int[]{-2,2}) column(p.relative(side,x).relative(facing),4,trim);
        for (int x=-2;x<=2;x++) put(p.relative(side,x).relative(facing).above(4),trim);
        open(p,facing,1,3);
        var door=Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING,facing);
        put(p,door);
        put(p.above(),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
    }

    private void glazed(BlockPos p,Block glass) {
        for (int x=-2;x<=2;x++) for (int y=-2;y<=2;y++)
            if (x*x+y*y<=5) put(p.offset(x,y,0),glass);
    }

    private void pews(BlockPos p,int x,int first,int last) {
        for (int z=first;z<=last;z+=3) for (int side : new int[]{-1,1})
            for (int seat=x;seat<=x+2;seat++)
                put(p.offset(side*seat,0,z),stair(Blocks.DARK_OAK_STAIRS,Direction.NORTH));
    }

    private void altar(BlockPos p,Block stone) {
        box(p,-1,0,0, 1,0,1, stone);
        put(p.offset(0,1,1),Blocks.LANTERN);
    }

    private void deck(BlockPos p,int x,int z,int xx,int zz) {
        box(p,x,-1,z, xx,-1,zz, Blocks.SMOOTH_STONE);
        for (int dx=x+2;dx<=xx;dx+=8) for (int dz=z+2;dz<=zz;dz+=8)
            box(p,dx,-4,dz, dx,-2,dz, Blocks.STONE_BRICKS);
    }

    private void street(int x,int z,int xx,int zz) {
        for (int gx=x;gx<=xx;gx++) for (int gz=z;gz<=zz;gz++) {
            var p = new BlockPos(gx,64,gz);
            if (city.getBlockState(p).is(Blocks.SHORT_GRASS)) put(p,Blocks.AIR);
            if (city.isEmptyBlock(p) && city.isEmptyBlock(p.above())) put(p.below(),Blocks.POLISHED_ANDESITE);
        }
    }

    private void stoneGround(int x,int z,Block block) {
        var p = new BlockPos(x,NapoliExpansion.height(x,z)+1,z);
        if (city.isEmptyBlock(p)) put(p.below(),block);
    }

    private void clearPlanting(BlockPos p,int r,int h) {
        for (var at : BlockPos.betweenClosed(p.offset(-r,0,-r),p.offset(r,h,r)))
            if (PLANTING.contains(city.getBlockState(at).getBlock())) put(at,Blocks.AIR);
    }

    private void sign(BlockPos p,String... lines) {
        if (city.isEmptyBlock(p) && city.getBlockState(p.below()).isSolidRender() && !CityProperty.playerPlaced(city,p))
            NapoliEmploymentMap.sign(city,p,lines);
    }

    private void hang(BlockPos p) {
        put(p,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));
    }

    private void column(BlockPos p,int height,Block block) {
        for (int y=0;y<height;y++) put(p.above(y),block);
    }

    private static BlockState stair(Block block,Direction dir) {
        return block.defaultBlockState().setValue(StairBlock.FACING,dir);
    }

    private static BlockState railing(boolean west,boolean east,boolean north,boolean south) {
        return Blocks.IRON_BARS.defaultBlockState().setValue(BlockStateProperties.WEST,west)
                .setValue(BlockStateProperties.EAST,east).setValue(BlockStateProperties.NORTH,north)
                .setValue(BlockStateProperties.SOUTH,south);
    }

    private void box(BlockPos p,int x,int y,int z,int xx,int yy,int zz,Block block) {
        for (var at : BlockPos.betweenClosed(p.offset(x,y,z),p.offset(xx,yy,zz))) put(at,block);
    }

    private void put(BlockPos p,Block b) { put(p,b.defaultBlockState()); }

    private void put(BlockPos p,BlockState state) {
        if (!CityProperty.playerPlaced(city,p) && city.getBlockEntity(p)==null && city.getBlockState(p)!=state)
            city.setBlock(p,state,FLAGS);
    }
}
