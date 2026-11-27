package dev.napuli;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class NapoliVedute {
    private static final int DANTE_Z = -181;
    private static final Set<Block> MOSAIC_BASE = Set.of(Blocks.POLISHED_DIORITE, Blocks.SMOOTH_QUARTZ,
            Blocks.CHISELED_QUARTZ_BLOCK, Blocks.POLISHED_BLACKSTONE);
    private final ServerLevel level;

    private NapoliVedute(ServerLevel level) { this.level = level; }

    static void build(ServerLevel level) {
        var city = new NapoliVedute(level);
        city.dante();
        city.portAlba();
        city.galleryMosaic();
        city.quartieri();
        city.palaceApproach();
        city.bay();
        city.promenade();
        Napuli.LOGGER.info("Napoli v10: Piazza Dante, Port’Alba, Galleria and waterfront refinements");
    }

    private void dante() {
        // The Convitto forms the western hemicycle, facing the monument and Via Toledo.
        for (int x = -44; x <= 38; x++) for (int z = -210; z <= -154; z++) {
            Block pavement = Math.floorMod(x * 17 + z * 13, 9) == 0 ? Blocks.ANDESITE : Blocks.POLISHED_ANDESITE;
            replace(new BlockPos(x, 63, z), Blocks.SAND, pavement);
        }
        for (int x = -5; x <= 5; x++) for (int z = -210; z <= -154; z++)
            replace(new BlockPos(x, 63, z), Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DEEPSLATE);

        for (int offset = -25; offset <= 25; offset++) {
            int z = DANTE_Z + offset;
            int front = -34 + offset * offset / 53;
            if (Math.abs(offset) <= 5) continue;
            fill(-43, 64, z, front, 64, z, Blocks.SMOOTH_SANDSTONE);
            fill(front - 7, 65, z, front, 79, z, Blocks.TERRACOTTA);
            fill(front - 6, 65, z, front - 1, 78, z, Blocks.AIR);
            fill(front, 65, z, front, 68, z, Blocks.SMOOTH_SANDSTONE);
            for (int band : new int[]{69, 75, 79}) put(front + 1, band, z, Blocks.CALCITE);
            if (Math.floorMod(offset, 6) <= 1) {
                for (int floor : new int[]{66, 71, 76}) {
                    fill(front, floor, z, front, floor + 1, z, Blocks.GLASS);
                    put(front, floor + 2, z, Blocks.CHISELED_SANDSTONE);
                }
            }
            if (Math.floorMod(offset, 5) == 0) {
                put(front, 80, z, Blocks.SMOOTH_QUARTZ_SLAB);
                put(front, 81, z, Blocks.QUARTZ_PILLAR);
                put(front, 82, z, Blocks.CHISELED_QUARTZ_BLOCK);
            }
        }

        // Central arch, layered cornice and a clock on the upper pavilion.
        fill(-43, 64, DANTE_Z - 6, -31, 84, DANTE_Z + 6, Blocks.SMOOTH_SANDSTONE);
        fill(-42, 65, DANTE_Z - 5, -32, 78, DANTE_Z + 5, Blocks.AIR);
        for (int d = -4; d <= 4; d++) {
            int top = 72 + (4 - Math.abs(d)) / 2;
            fill(-32, 65, DANTE_Z + d, -30, top, DANTE_Z + d, Blocks.AIR);
            put(-30, top + 1, DANTE_Z + d, Blocks.CHISELED_SANDSTONE);
        }
        for (int dz : new int[]{-6, 6}) fill(-30, 64, DANTE_Z + dz, -30, 81, DANTE_Z + dz, Blocks.QUARTZ_PILLAR);
        fill(-32, 78, DANTE_Z - 6, -30, 78, DANTE_Z + 6, Blocks.CALCITE);
        fill(-32, 84, DANTE_Z - 7, -30, 84, DANTE_Z + 7, Blocks.CALCITE);
        for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++)
            if (y * y + z * z <= 5) put(-30, 81 + y, DANTE_Z + z,
                    z == 0 && y >= 0 || y == 0 && z == 1 ? Blocks.POLISHED_BLACKSTONE : Blocks.QUARTZ_BLOCK);

        fill(-16, 64, DANTE_Z - 2, -12, 64, DANTE_Z + 2, Blocks.SMOOTH_QUARTZ);
        fill(-15, 65, DANTE_Z - 1, -13, 67, DANTE_Z + 1, Blocks.POLISHED_DIORITE);
        fill(-14, 68, DANTE_Z, -14, 71, DANTE_Z, Blocks.QUARTZ_PILLAR);
        put(-14, 72, DANTE_Z, Blocks.CHISELED_QUARTZ_BLOCK);
        put(-14, 71, DANTE_Z + 1, Blocks.SMOOTH_QUARTZ_SLAB);
        put(-13, 71, DANTE_Z + 1, Blocks.SMOOTH_QUARTZ_SLAB);
        for (int z : new int[]{-202, -160}) {
            lamp(-22, z);
            lamp(9, z);
        }
        sign(7, 64, -157, "PIAZZA DANTE", "Convitto Nazionale", "Port’Alba →");
    }

    private void portAlba() {
        // A gate and a narrow street of booksellers connect to the older blocks.
        for (int x = 16; x <= 42; x++) for (int z = -171; z <= -154; z++)
            replace(new BlockPos(x, 63, z), Blocks.POLISHED_ANDESITE,
                    Math.floorMod(x + z, 4) == 0 ? Blocks.TUFF : Blocks.POLISHED_BASALT);
        fill(19, 64, -162, 35, 78, -160, Blocks.TUFF_BRICKS);
        for (int dx = -4; dx <= 4; dx++) {
            int h = 72 + (4 - Math.abs(dx)) / 2;
            fill(27 + dx, 64, -162, 27 + dx, h, -160, Blocks.AIR);
            put(27 + dx, h + 1, -159, Blocks.SMOOTH_SANDSTONE);
        }
        fill(18, 78, -163, 36, 78, -159, Blocks.CALCITE);
        fill(25, 79, -162, 29, 81, -160, Blocks.SMOOTH_SANDSTONE);
        put(27, 80, -159, Blocks.CHISELED_QUARTZ_BLOCK);
        for (int x : new int[]{20, 33}) {
            fill(x, 64, -158, x + 2, 64, -155, Blocks.SPRUCE_PLANKS);
            fill(x, 65, -158, x, 67, -155, Blocks.BOOKSHELF);
            fill(x - 1, 68, -159, x + 3, 68, -154, Blocks.DARK_OAK_SLAB);
            put(x + 2, 65, -156, Blocks.LANTERN);
        }
        sign(33, 64, -153, "PORT’ALBA", "Librerie", "Piazza Dante");
    }

    private void galleryMosaic() {
        // Twelve marble medallions surround the existing compass rose.
        Block[] centers = {Blocks.CHISELED_QUARTZ_BLOCK, Blocks.POLISHED_GRANITE, Blocks.POLISHED_DIORITE};
        for (int panel = 0; panel < 12; panel++) {
            double angle = 2 * Math.PI * panel / 12;
            int x = NapoliDistrict.GX + (int) Math.round(9 * Math.cos(angle));
            int z = NapoliDistrict.GZ + (int) Math.round(9 * Math.sin(angle));
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = new BlockPos(x + dx, 63, z + dz);
                if (MOSAIC_BASE.contains(level.getBlockState(pos).getBlock()))
                    put(pos.getX(), pos.getY(), pos.getZ(), dx == 0 && dz == 0
                            ? centers[panel % centers.length] : Math.abs(dx) + Math.abs(dz) == 1
                            ? Blocks.TERRACOTTA : Blocks.POLISHED_BLACKSTONE);
            }
        }
    }

    private void quartieri() {
        // Narrow awnings, small stalls and signposts in the westbound lanes.
        for (int z : new int[]{-48, -30}) {
            for (int x = -68; x <= -23; x++) {
                BlockPos floor = new BlockPos(x, 63, z);
                if (level.getBlockState(floor.above()).isAir())
                    replace(floor, Blocks.STONE_BRICKS,
                            Math.floorMod(x * 7 + z, 5) == 0 ? Blocks.ANDESITE : Blocks.POLISHED_BASALT);
            }
            for (int x : new int[]{-65, -51, -37}) {
                Block accent = x == -51 ? coloured("blue_wool") : coloured("red_wool");
                for (int dx = 0; dx < 4; dx++) for (int dz = 0; dz < 2; dz++)
                    addIfAir(x + dx, 67, z + dz - 1, dx % 2 == 0 ? accent : coloured("white_wool"));
                addIfAir(x, 64, z - 1, Blocks.SPRUCE_SLAB);
                if (level.getBlockState(new BlockPos(x, 64, z - 1)).is(Blocks.SPRUCE_SLAB))
                    addIfAir(x, 65, z - 1, Blocks.POTTED_FERN);
            }
        }
        sign(-22, 64, -48, "QUARTIERI", "Vico lungo", "Via Toledo →");
    }

    private void palaceApproach() {
        // Pale edging along the arcade in front of the long piperno facade.
        for (int z = 9; z <= 43; z++) for (int x : new int[]{28, 30}) {
            BlockPos floor = new BlockPos(x, 63, z);
            if (level.getBlockState(floor.above()).isAir()) {
                Block current = level.getBlockState(floor).getBlock();
                if (current == Blocks.POLISHED_ANDESITE || current == Blocks.ANDESITE || current == Blocks.TUFF)
                    put(x, 63, z, x == 30 ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_STONE);
            }
        }
    }

    private static Block coloured(String id) {
        return CityBlocks.vanilla(id);
    }

    private void addIfAir(int x, int y, int z, Block value) {
        if (level.getBlockState(new BlockPos(x, y, z)).isAir()) put(x, y, z, value);
    }

    private void bay() {
        // Continue the water through strips of untouched flat terrain between the districts.
        var p = new BlockPos.MutableBlockPos();
        for (int x = -125; x <= 335; x++) {
            int shore = x < 73 ? 65 : x < 177 ? 67 : 78;
            int farEdge = 248 - (int) (35 * Math.pow((x - 85) / 250.0, 2));
            for (int z = shore; z <= farEdge; z++) {
                p.set(x, 63, z);
                if (!level.getBlockState(p).is(Blocks.SAND) || !level.getBlockState(p.above()).isAir()
                        || !level.getBlockState(p.below()).is(Blocks.SANDSTONE)) continue;
                put(x, 63, z, Blocks.WATER);
                put(x, 62, z, Blocks.WATER);
                if (level.getBlockState(p.set(x, 61, z)).is(Blocks.SANDSTONE)) put(x, 61, z, Blocks.WATER);
            }
        }
        // Irregular riprap hugs the promenade rather than forming a second straight wall.
        for (int x = -64; x <= -11; x++) {
            int edge = 55 + (int)(9 * Math.pow(Math.abs(x) / 73.0, 1.6));
            for (int z = edge + 2; z <= edge + 6; z++) {
                BlockPos p0 = new BlockPos(x, 63, z);
                if (!level.getBlockState(p0).is(Blocks.WATER) || Math.floorMod(x * 13 + z * 19, 7) >= 5) continue;
                put(x, 62, z, Blocks.ANDESITE);
                put(x, 63, z, Math.floorMod(x + z, 3) == 0 ? Blocks.TUFF : Blocks.COBBLESTONE);
            }
        }
    }

    private void promenade() {
        // Pale curb and striped paving where the older shore meets the Municipio quays.
        for (int x = 74; x <= 179; x++) for (int z = 52; z <= 56; z++) {
            BlockPos pos = new BlockPos(x, 63, z);
            if (level.getBlockState(pos.above()).isAir())
                replace(pos, Blocks.STONE_BRICKS, z == 56 ? Blocks.CALCITE
                        : x % 5 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);
        }
        for (int x : new int[]{82, 103, 125, 148, 168}) {
            BlockPos shore = new BlockPos(x, 64, 55);
            if (level.getBlockState(shore).isAir() && level.getBlockState(shore.above()).isAir()
                    && level.getBlockState(shore.below()).is(Blocks.SMOOTH_STONE)) {
                put(x, 64, 55, Blocks.DEEPSLATE_BRICK_WALL);
                put(x, 65, 55, Blocks.LANTERN);
            }
        }
    }

    private void lamp(int x, int z) {
        fill(x, 64, z, x, 67, z, Blocks.DEEPSLATE_BRICK_WALL);
        put(x, 68, z, Blocks.LANTERN);
    }

    private void sign(int x, int y, int z, String... lines) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.getBlockState(pos).isAir()) return;
        level.setBlock(pos, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            var text = SignText.EMPTY.asMutable();
            for (int i = 0; i < lines.length; i++) text.setLine(i, Component.literal(lines[i]));
            sign.setText(text.asImmutable(), SignTextSlot.FRONT);
            sign.setText(text.asImmutable(), SignTextSlot.BACK);
            sign.setChanged();
        }
    }

    private void replace(BlockPos pos, Block old, Block next) {
        if (level.getBlockState(pos).is(old))
            level.setBlock(pos, next.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private void put(int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        var state = block.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1))
            level.setBlock(p, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
