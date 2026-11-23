package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class NapoliEmploymentMap {
    private NapoliEmploymentMap() {}

    static void build(ServerLevel level) {
        // Reconnect the west promenade to the older seafront across the strip of bare sand.
        for (int x = -111; x <= -65; x++) for (int z = 49; z <= 56; z++) {
            BlockPos ground = new BlockPos(x, 63, z);
            if (level.getBlockState(ground).is(Blocks.SAND) && level.getBlockState(ground.above()).isAir())
                put(level, ground, z == 49 || z == 56 ? Blocks.CALCITE : Blocks.STONE_BRICKS);
        }
        sign(level, new BlockPos(-102, 64, 54), "LUNGOMARE", "Mergellina ←", "Ufficio lavoro");

        for (BlockPos p : BlockPos.betweenClosed(-122, 63, 28, -113, 69, 39)) {
            boolean wall = p.getX() == -122 || p.getX() == -113 || p.getZ() == 28 || p.getZ() == 39;
            if (p.getY() == 63) put(level, p, Blocks.TUFF_BRICKS);
            else if (p.getY() == 69) put(level, p, Blocks.BRICK_SLAB);
            else if (wall) {
                if (p.getZ() == 39 && p.getX() >= -118 && p.getX() <= -116 && p.getY() <= 66)
                    put(level, p, Blocks.AIR);
                else if (p.getY() >= 65 && p.getY() <= 66 && p.getZ() == 39 && (p.getX() == -120 || p.getX() == -114))
                    put(level, p, Blocks.GLASS);
                else put(level, p, p.getY() == 67 ? Blocks.TUFF_BRICKS : Blocks.CUT_SANDSTONE);
            } else put(level, p, Blocks.AIR);
        }
        for (int x = -122; x <= -113; x++) {
            put(level, new BlockPos(x, 68, 40), Blocks.DARK_PRISMARINE_SLAB);
            for (int z = 40; z <= 49; z++) put(level, new BlockPos(x, 63, z), Blocks.POLISHED_ANDESITE);
        }
        for (int x = -120; x <= -116; x++) put(level, new BlockPos(x, 64, 33), Blocks.SPRUCE_PLANKS);

        wallSign(level, new BlockPos(-121, 66, 40), "CENTRO PER", "L'IMPIEGO", "Mergellina 2");
        put(level, new BlockPos(-120, 67, 35), Blocks.LANTERN);
        put(level, new BlockPos(-115, 67, 35), Blocks.LANTERN);
        markAddressesAndJobs(level);
    }

    static void markAddressesAndJobs(ServerLevel level) {
        for (NapoliMergellina.House h : NapoliMergellina.allHouses()) {
            BlockPos facade = new BlockPos(h.x() + 3, 65, h.z() + 12);
            if (level.getBlockState(facade).isAir()) wallSign(level, facade, "MERGELLINA", "CIVICO " + h.civic());
        }
        int number = 101;
        for (int[] home : NapoliInteriors.homes()) {
            BlockPos face = new BlockPos(home[0] + 3, 65, home[1] + 12);
            if (level.getBlockState(face).isAir() && level.getBlockState(face.north()).isSolidRender()
                    && level.getBlockState(face.east(2).north()).isAir())
                wallSign(level, face, "CIVICO", Integer.toString(number));
            number++;
        }
    }

    static void sign(ServerLevel level, BlockPos pos, String... lines) {
        put(level, pos, Blocks.OAK_SIGN);
        text(level, pos, lines);
    }

    private static void wallSign(ServerLevel level, BlockPos pos, String... lines) {
        level.setBlock(pos, Blocks.BIRCH_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                Direction.SOUTH), Block.UPDATE_CLIENTS);
        text(level, pos, lines);
    }

    private static void text(ServerLevel level, BlockPos pos, String[] lines) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        var text = SignText.EMPTY.asMutable();
        for (int i = 0; i < lines.length; i++) text.setLine(i, Component.literal(lines[i]));
        sign.setText(text.asImmutable(), SignTextSlot.FRONT);
        sign.setText(text.asImmutable(), SignTextSlot.BACK);
        sign.setChanged();
    }

    private static void put(ServerLevel level, BlockPos p, Block block) {
        level.setBlock(p, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
