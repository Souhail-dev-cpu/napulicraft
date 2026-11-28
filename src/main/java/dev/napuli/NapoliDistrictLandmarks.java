package dev.napuli;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class NapoliDistrictLandmarks {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Set<Block> CLEARABLE = Set.of(Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.OAK_STAIRS,
            Blocks.GRASS_BLOCK, Blocks.SHORT_GRASS, Blocks.STONE_BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.WATER);
    private final ServerLevel city;

    private NapoliDistrictLandmarks(ServerLevel city) {
        this.city = city;
    }

    static BlockPos site(String id) {
        var place = NapoliAtlas.find(id);
        return new BlockPos(place.x(), NapoliExpansion.height(place.x(), place.z()) + 1, place.z());
    }

    static void upgrade(ServerLevel city) {
        var build = new NapoliDistrictLandmarks(city);
        build.fontanelle(build.load("fontanelle", 33));
        build.catacombs(build.load("san_gennaro", 28));
        build.mostra(build.load("mostra", 49));
        build.botanicalGarden(build.load("orto", 42));
        build.businessDistrict(build.load("direzionale", 63));
        build.church(build.load("pianura", 28), false);
        build.church(build.load("ponticelli", 32), true);
    }

    private BlockPos load(String id, int radius) {
        var p = site(id);
        NapoliAtlasLandmarks.generate(city, p.offset(-radius, 0, -radius), radius * 2, radius * 2);
        return p;
    }

    private void retireHypogeum(BlockPos p) {
        // Fill the old rectangular excavation before cutting the three new routes.
        box(p, -19,-11,-9, 19,-1,9, Blocks.TUFF);
        for (int z = 10; z <= 20; z++)
            box(p, -2,-11,z, 2,-1,z, Blocks.TUFF);
    }

    private void fontanelle(BlockPos p) {
        retireHypogeum(p);
        for (int x : new int[] {-13, 0, 13}) {
            var nave = p.offset(x, -9, 0);
            box(nave, -5,-1,-24, 5,8,11, Blocks.TUFF);
            for (int dx = -4; dx <= 4; dx++) {
                int h = 7 - Math.abs(dx);
                box(nave, dx,0,-23, dx,h,10, Blocks.AIR);
            }
            for (int z = -21; z <= 5; z += 4) {
                for (int side : new int[] {-1, 1}) {
                    put(nave.offset(side * 4, 0, z), Blocks.TUFF_BRICKS);
                    put(nave.offset(side * 4, 1, z), Blocks.SKELETON_SKULL);
                    put(nave.offset(side * 4, 2, z + 1), Blocks.TUFF_BRICKS);
                    put(nave.offset(side * 4, 3, z + 1), Blocks.LANTERN);
                }
                box(nave, -1,-1,z, 1,-1,z+3, Blocks.POLISHED_TUFF);
            }
        }
        for (int z : new int[] {-17, -5, 8}) {
            var passage = p.offset(0, -9, z);
            box(passage, -13,-1,-1, 13,4,1, Blocks.TUFF);
            box(passage, -13,0,-1, 13,3,1, Blocks.AIR);
        }
        descent(p, 0, 27, 9);
        NapoliMonumentInteriors.place(city, "fontanelle_cappella", p.offset(-3,-10,-23),
                (pos, old) -> old.is(Blocks.TUFF) || old.is(Blocks.POLISHED_TUFF),
                List.of(Blocks.POLISHED_ANDESITE, Blocks.CUT_SANDSTONE, Blocks.LANTERN, Blocks.FLOWER_POT));
        sign(p.offset(3,0,27), "FONTANELLE", "Ossario nel tufo");
    }

    private void catacombs(BlockPos p) {
        retireHypogeum(p);
        var upper = p.offset(-7,-6,0);
        chamber(upper, 8, -23, 8, 5);
        box(upper, -3,-1,-21, 3,-1,-12, Blocks.POLISHED_DIORITE);
        box(upper, -3,0,-21, 3,0,-19, Blocks.SMOOTH_QUARTZ);
        for (int x : new int[] {-6,6}) for (int z = -18; z <= 4; z += 5) {
            box(upper, x,0,z, x,4,z, Blocks.CUT_SANDSTONE);
            put(upper.offset(x, 1, z + 1), Blocks.LANTERN);
            box(upper, x > 0 ? 7 : -8,2,z+2, x > 0 ? 8 : -7,3,z+3, Blocks.AIR);
        }
        for (int x = -3; x <= 3; x++)
            for (int y = 2; y <= 4; y++)
                put(upper.offset(x, y, -23), (x + y) % 2 == 0 ? Blocks.TERRACOTTA : CityBlocks.vanilla("blue_terracotta"));
        descent(p, -7, 20, 6);
        box(upper, -1,-1,8, 1,-1,9, Blocks.POLISHED_TUFF);
        box(upper, -1,0,8, 1,3,9, Blocks.AIR);

        var lower = p.offset(14,-12,0);
        chamber(lower, 7, -19, 2, 5);
        for (int x : new int[] {-4,4}) for (int z = -16; z <= -4; z += 4) {
            box(lower, x,0,z, x,4,z, Blocks.TUFF_BRICKS);
            put(lower.offset(x, 0, z + 1), stair(Blocks.SMOOTH_SANDSTONE_STAIRS, Direction.NORTH));
        }
        box(lower, -2,0,-18, 2,0,-16, Blocks.CUT_SANDSTONE);
        put(lower.offset(0,1,-17), Blocks.LANTERN);
        descent(p, 14, 26, 12);
        box(lower, -1,0,2, 1,3,3, Blocks.AIR);
        sign(p.offset(-4,0,20), "SAN GENNARO", "Vestibolo alto");
        sign(p.offset(17,0,26), "SANT'AGRIPPINO", "Basilica ipogea");
    }

    private void chamber(BlockPos p, int half, int north, int south, int height) {
        box(p, -half,-1,north, half,height,south, Blocks.TUFF);
        box(p, -half+1,0,north+1, half-1,height-1,south-1, Blocks.AIR);
        box(p, -2,-1,north+1, 2,-1,south-1, Blocks.POLISHED_TUFF);
    }

    // South landing at street level, north landing at the corresponding gallery floor.
    private void descent(BlockPos p, int x, int south, int drop) {
        for (int i = 0; i <= drop * 2; i++) {
            int z = south - i, y = -(i + 1) / 2;
            for (int dx = -2; dx <= 2; dx++) {
                var foot = p.offset(x + dx, y, z);
                if (Math.abs(dx) == 2) {
                    put(foot, Blocks.TUFF_BRICKS);
                    continue;
                }
                put(foot.below(2), Blocks.TUFF);
                if (i % 2 != 0) {
                    put(foot.below(), Blocks.POLISHED_TUFF);
                    put(foot, stair(Blocks.TUFF_BRICK_STAIRS, Direction.SOUTH));
                } else {
                    put(foot.below(), Blocks.POLISHED_TUFF);
                    put(foot, Blocks.AIR);
                }
                for (int dy = 1; dy <= 3; dy++) put(foot.above(dy), Blocks.AIR);
            }
        }
    }

    private void mostra(BlockPos p) {
        drainSurface(p, 48);
        box(p, -8,0,4, 8,1,32, Blocks.AIR); // old display pool
        box(p, -9,-1,3, 9,-1,33, Blocks.SMOOTH_STONE);
        for (int x : new int[] {-30,30}) {
            box(p, x,0,4, x,25,4, Blocks.AIR);
            for (int z = 12; z <= 32; z += 10) clearPlanting(p.offset(x,0,z), 3, 7);
        }
        for (int x = -43; x <= 47; x++) for (int z = 40; z <= 43; z++)
            put(p.offset(x,-1,z), Blocks.POLISHED_ANDESITE);
        var arena = p.offset(-27,0,18);
        for (int z = -14; z <= 15; z++) for (int x = -17; x <= 17; x++) {
            double radius = Math.hypot(x, z * 1.12);
            if (z > 0 && radius >= 6 && radius <= 17 && Math.abs(x) > 1) {
                int tier = (int) ((radius - 6) / 2);
                box(arena, x,-1,z, x,tier,z, Blocks.SMOOTH_SANDSTONE);
                put(arena.offset(x,tier+1,z), Blocks.SMOOTH_SANDSTONE_SLAB);
            } else if (Math.abs(x) <= 16 && z >= -10) {
                put(arena.offset(x,-1,z), Blocks.POLISHED_ANDESITE);
            }
        }
        box(arena, -12,-1,-11, 12,0,-6, Blocks.CUT_SANDSTONE);
        box(arena, -12,1,-12, 12,8,-12, Blocks.CALCITE);
        for (int x = -11; x <= 11; x += 4)
            box(arena, x,1,-11, x,7,-11, Blocks.QUARTZ_PILLAR);
        sign(p.offset(-26,0,37), "ARENA FLEGREA");

        var fountain = p.offset(15,0,22);
        for (int x = -12; x <= 12; x++) for (int z = -11; z <= 7; z++) {
            double radius = Math.hypot(x, z);
            if (radius > 12.4) continue;
            put(fountain.offset(x,-1,z), Blocks.PRISMARINE_BRICKS);
            boolean rim = radius > 11.1 || z == -11 || z == 7;
            put(fountain.offset(x,0,z), rim ? Blocks.SMOOTH_QUARTZ : Blocks.WATER);
            if (radius > 11.1 && z <= 0) put(fountain.offset(x,1,z), Blocks.SMOOTH_QUARTZ_SLAB);
        }
        for (int x = -5; x <= 5; x += 5) {
            put(fountain.offset(x,0,-1), Blocks.SEA_LANTERN);
            put(fountain.offset(x,1,-1), Blocks.END_ROD);
        }
        sign(fountain.offset(0,0,10), "FONTANA", "DELL'ESEDRA");

        var tower = p.offset(37,0,12);
        hall(tower, 7, 5, 26, Blocks.SMOOTH_SANDSTONE, Blocks.QUARTZ_BLOCK);
        for (int y = 3; y <= 22; y += 3)
            box(tower, -6,y,5, 6,y+1,5, CityBlocks.vanilla("gray_stained_glass"));
        for (int x : new int[] {-7,7}) box(tower, x,0,6, x,25,6, Blocks.QUARTZ_PILLAR);
        box(tower, -7,25,6, 7,26,6, Blocks.SMOOTH_QUARTZ);
        sign(tower.offset(3,0,8), "TORRE", "DELLE NAZIONI");

        for (int x : new int[] {-7,36}) {
            var exhibit = p.offset(x,0,31);
            boolean modern = x > 0;
            hall(exhibit, modern ? 9 : 5, 7, modern ? 9 : 8,
                    modern ? Blocks.CALCITE : Blocks.SMOOTH_SANDSTONE, Blocks.POLISHED_ANDESITE);
            if (modern) {
                for (int z = -6; z <= 6; z += 4)
                    box(exhibit, -9,3,z, -9,6,z+1, Blocks.GLASS);
                box(exhibit, -8,9,-2, 8,9,2, Blocks.GLASS);
                box(exhibit, -8,8,9, 8,8,9, Blocks.SMOOTH_QUARTZ_SLAB);
                for (int side : new int[] {-7,7}) box(exhibit, side,0,9, side,7,9, Blocks.QUARTZ_PILLAR);
            } else {
                for (int z = -7; z <= 7; z++)
                    for (int dx = -5; dx <= 5; dx++)
                        put(exhibit.offset(dx,9+(5-Math.abs(dx))/2,z), Blocks.BRICK_SLAB);
            }
            for (int z = 8; z <= 12; z++)
                box(exhibit,-2,-1,z, 2,-1,z, Blocks.POLISHED_ANDESITE);
            put(exhibit.offset(-3,0,7).north(1), Blocks.LECTERN);
            for (int side : new int[] {-1,1}) for (int z : new int[] {-4,0,4}) {
                put(exhibit.offset(side*3,0,z), Blocks.CHISELED_QUARTZ_BLOCK);
                put(exhibit.offset(side*3,1,z), Blocks.FLOWER_POT);
            }
            sign(exhibit.offset(3,0,9), "PADIGLIONE", x < 0 ? "Esposizioni" : "Mostra storica");
        }
    }

    private void botanicalGarden(BlockPos p) {
        drainSurface(p, 41);
        box(p,-6,0,-6, 6,4,6, Blocks.AIR);
        box(p,-6,-1,-6, 6,-1,6, Blocks.SMOOTH_SANDSTONE);
        for (int x = -26; x <= 26; x++) for (int z = -24; z <= 36; z++)
            if (Math.abs(x) <= 2 || Math.abs(z - 13) <= 1 || Math.abs(z + 15) <= 1) {
                var path = p.offset(x,0,z);
                clearPlanting(path, 0, 6);
                put(path.below(), Blocks.SMOOTH_SANDSTONE);
            }
        greenhouse(p.offset(0,0,-25), 11, 11, true);
        greenhouse(p.offset(19,0,-6), 6, 10, false);
        greenhouse(p.offset(-19,0,-6), 6, 10, false);
        for (int x : new int[] {-20,-10,10,20}) for (int z : new int[] {18,28}) {
            var bed = p.offset(x,0,z);
            clearPlanting(bed, 3, 7);
            box(bed, -3,-1,-3, 3,-1,3, Blocks.GRASS_BLOCK);
            for (int i = -2; i <= 2; i += 2) {
                put(bed.offset(i,0,-2), Blocks.FERN);
                put(bed.offset(i,0,0), Blocks.AZURE_BLUET);
                put(bed.offset(i,0,2), Blocks.OXEYE_DAISY);
            }
        }
        sign(p.offset(4,0,35), "ORTO BOTANICO", "Serre e collezioni");
    }

    private void greenhouse(BlockPos p, int half, int depth, boolean merola) {
        for (int x = -half; x <= half; x++) for (int z = -depth; z <= depth; z++) {
            var base = p.offset(x,0,z);
            clearPlanting(base, 0, 8);
            boolean side = Math.abs(x) == half, end = Math.abs(z) == depth;
            put(base.below(), Math.abs(x) <= 1 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_STONE);
            if (side || end) for (int y = 0; y <= 4; y++)
                put(base.above(y), y == 0 || Math.floorMod(x+z,5) == 0 ? Blocks.CUT_SANDSTONE : Blocks.GLASS);
            int roof = 5 + (half - Math.abs(x)) / 3;
            put(base.above(roof), (z + depth) % 5 == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.GLASS);
            if (end) for (int y = 5; y < roof; y++) put(base.above(y), Blocks.GLASS);
            if (!side && !end && Math.abs(x) > 2 && Math.abs(z) < depth - 1) {
                put(base, Blocks.MOSS_BLOCK);
                put(base.above(), (x+z) % 4 == 0 ? Blocks.FERN : Blocks.AZALEA);
            }
        }
        var door = p.south(depth);
        box(door, -1,0,0, 1,2,0, Blocks.AIR);
        door(door);
        if (merola) {
            box(door, -2,-1,1, 2,-1,6, Blocks.CUT_SANDSTONE);
            sign(door.offset(3,0,2), "SERRA MEROLA", "Piante tropicali");
        } else sign(door.offset(3,0,2), "SERRE CALIFANO", "Collezioni botaniche");
    }

    private void businessDistrict(BlockPos p) {
        var palettes = List.of(CityBlocks.vanilla("light_blue_stained_glass"), CityBlocks.vanilla("gray_stained_glass"), CityBlocks.vanilla("white_stained_glass"));
        for (int row : new int[] {-30,22}) {
            int i = 0;
            for (int dx : new int[] {-39,0,36}) {
                var tower = p.offset(dx,0,row);
                int roof = 28 + Math.floorMod(dx * 31 + row,24);
                Block glass = palettes.get(i++);
                for (int y = 4; y < roof - 1; y++) for (int x = -8; x <= 7; x++) {
                    int spacing = glass == CityBlocks.vanilla("gray_stained_glass") ? 3 : 5;
                    if ((x + 8) % spacing != 0 && y % 6 != 0)
                        for (int z : new int[] {-10,9}) put(tower.offset(x,y,z), glass);
                }
                box(tower, -4,0,9, 4,3,9, glass);
                box(tower, -1,0,9, 1,2,9, Blocks.AIR);
                box(tower, -6,-1,10, 6,-1,15, Blocks.SMOOTH_STONE);
                box(tower, -8,-1,-8, 7,-1,8, Blocks.POLISHED_DIORITE);
                for (int x : new int[] {-6,6}) {
                    box(tower, x,0,10, x,3,10, Blocks.POLISHED_ANDESITE);
                    for (int z : new int[] {-5,1,5}) put(tower.offset(x,4,z), Blocks.SEA_LANTERN);
                    put(tower.offset(x,0,6), stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.NORTH));
                }
                box(tower, -6,4,10, 6,4,13, Blocks.SMOOTH_QUARTZ_SLAB);
                box(tower, -3,0,-4, 1,0,-4, Blocks.SMOOTH_QUARTZ);
                put(tower.offset(-3,1,-4), Blocks.LECTERN);
            }
        }
        for (int x = -45; x <= 43; x++) for (int z : new int[] {-14,-13,35,36,37,38})
            put(p.offset(x,-1,z), Blocks.SMOOTH_STONE);
        for (int z = -14; z <= 56; z++) for (int x : new int[] {13,14,15})
            put(p.offset(x,-1,z), Blocks.SMOOTH_STONE);
        sign(p.offset(16,0,41), "CENTRO DIREZ.", "Atrii e uffici");
        // Join the existing metro stairs at their actual surface elevation.
        var exit = CityNetwork.stop("direzionale").entrance().north(2);
        var corner = new BlockPos(exit.getX(), exit.getY(), p.getZ()+41);
        var promenade = p.offset(14,0,41);
        NapoliAtlasLandmarks.generate(city,
                new BlockPos(Math.min(exit.getX(), promenade.getX()), p.getY(), Math.min(exit.getZ(), promenade.getZ())),
                Math.abs(exit.getX()-promenade.getX()), Math.abs(exit.getZ()-promenade.getZ()));
        pavement(exit, corner);
        pavement(corner, promenade);
    }

    private void pavement(BlockPos from, BlockPos to) {
        int length = Math.max(Math.abs(to.getX()-from.getX()), Math.abs(to.getZ()-from.getZ()));
        for (int i = 1; i <= length; i++) {
            double t = (double) i / length;
            int x = (int) Math.round(from.getX() + (to.getX()-from.getX()) * t);
            int z = (int) Math.round(from.getZ() + (to.getZ()-from.getZ()) * t);
            int y = NapoliExpansion.height(x,z);
            var floor = new BlockPos(x,y,z);
            if (city.isEmptyBlock(floor.above()) && city.isEmptyBlock(floor.above(2))
                    && !city.getBlockState(floor).getCollisionShape(city,floor).isEmpty())
                put(floor, Blocks.SMOOTH_STONE);
        }
    }

    private void church(BlockPos p, boolean ponticelli) {
        // Retire the identical model originally placed behind both piazzas.
        int r = ponticelli ? 31 : 27;
        box(p,-14,0,-r+2, 14,16,-r+21, Blocks.AIR);
        box(p,14,0,-r+4, 20,25,-r+12, Blocks.AIR);
        box(p,-2,17,-r+20, 2,23,-r+20, Blocks.AIR);
        var nave = p.offset(0,0,-r+12);
        var plaster = ponticelli ? Blocks.CALCITE : Blocks.SMOOTH_SANDSTONE;
        var trim = ponticelli ? Blocks.SMOOTH_QUARTZ : Blocks.CUT_SANDSTONE;
        int half = ponticelli ? 11 : 8;
        hall(nave, half, 10, 10, plaster, trim);
        for (int x = -half; x <= half; x++) {
            int pitch = (half - Math.abs(x)) / 2;
            if (pitch > 0) {
                box(nave, x,11,-10, x,10+pitch,10, Blocks.BRICKS);
                put(nave.offset(x,11+pitch,10), trim);
                put(nave.offset(x,10+pitch,11), trim);
            }
        }
        put(nave.offset(0,8,10), CityBlocks.vanilla("blue_stained_glass"));
        for (int x : new int[] {-half+2,half-2}) {
            box(nave, x,1,11, x,8,11, trim);
            if (ponticelli) box(nave, x,5,9, x,8,9, Blocks.QUARTZ_PILLAR);
        }
        box(nave, -2,-1,11, 2,-1,24, trim);
        box(nave, -2,0,-8, 2,0,-6, trim);
        put(nave.offset(0,1,-7), Blocks.LANTERN);
        for (int z = -3; z <= 6; z += 3) for (int x : new int[] {-4,-3,3,4})
            put(nave.offset(x,0,z), stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
        var bell = nave.offset(ponticelli ? 15 : -11, 0, ponticelli ? 3 : -5);
        int top = ponticelli ? 24 : 16;
        hall(bell, 2, 2, top, plaster, trim);
        for (int y : new int[] {top-6,top-1}) box(bell,-3,y,-3, 3,y,3, trim);
        box(bell,-1,top-5,2, 1,top-2,2, Blocks.AIR);
        put(bell.above(top-5), Blocks.BELL);
        box(bell,-1,top+1,-1, 1,top+2,1, ponticelli ? Blocks.CUT_SANDSTONE : Blocks.BRICKS);
        sign(nave.offset(4,0,14), ponticelli ? "S. MARIA" : "SAN GIORGIO", ponticelli ? "DELLA NEVE" : "MARTIRE");
    }

    private void hall(BlockPos p, int half, int depth, int top, Block wall, Block trim) {
        box(p, -half,-1,-depth, half,-1,depth, Blocks.SMOOTH_STONE);
        box(p, -half,top,-depth, half,top,depth, trim);
        for (int x = -half; x <= half; x++) for (int z = -depth; z <= depth; z++) {
            if (Math.abs(x) == half || Math.abs(z) == depth)
                for (int y = 0; y < top; y++) put(p.offset(x,y,z), y == 0 || y == top-1 ? trim : wall);
            else for (int y = 0; y <= 2; y++) {
                var at = p.offset(x,y,z);
                if (CLEARABLE.contains(city.getBlockState(at).getBlock())) put(at, Blocks.AIR);
            }
        }
        for (int x = -half + 2; x < half; x += 4) if (x != 0) {
            box(p,x,3,depth, x,4,depth, Blocks.GLASS);
            box(p,x,3,-depth, x,4,-depth, Blocks.GLASS);
        }
        door(p.south(depth));
    }

    private void clearPlanting(BlockPos pos, int r, int height) {
        for (var at : BlockPos.betweenClosed(pos.offset(-r,0,-r), pos.offset(r,height,r)))
            if (CLEARABLE.contains(city.getBlockState(at).getBlock())) put(at, Blocks.AIR);
    }

    private void drainSurface(BlockPos p, int radius) {
        for (var pos : BlockPos.betweenClosed(p.offset(-radius,-3,-radius), p.offset(radius,5,radius))) {
            if (!city.getBlockState(pos).is(Blocks.WATER)) continue;
            put(pos, pos.getY() < p.getY() ? Blocks.SMOOTH_STONE : Blocks.AIR);
        }
    }

    private void sign(BlockPos pos, String... lines) {
        if (city.isEmptyBlock(pos) && !CityProperty.playerPlaced(city,pos)
                && city.getBlockState(pos.below()).isSolidRender()) NapoliEmploymentMap.sign(city, pos, lines);
    }

    private void door(BlockPos pos) {
        var state = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        put(pos, state);
        put(pos.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static BlockState stair(Block block, Direction facing) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private void box(BlockPos p, int x, int y, int z, int xx, int yy, int zz, Block block) {
        for (var at : BlockPos.betweenClosed(p.offset(x,y,z), p.offset(xx,yy,zz))) put(at, block);
    }

    private void put(BlockPos pos, Block block) {
        put(pos, block.defaultBlockState());
    }

    private void put(BlockPos pos, BlockState state) {
        if (!CityProperty.playerPlaced(city,pos) && city.getBlockEntity(pos) == null
                && city.getBlockState(pos) != state) city.setBlock(pos,state,FLAGS);
    }
}
