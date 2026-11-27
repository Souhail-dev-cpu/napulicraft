package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class NapoliWestQuarter {
    static final NapoliMergellina.House WORKER_HOME = new NapoliMergellina.House(
            "posto", "Casa dei Lavoratori", -278, 9, 0, Blocks.SMOOTH_SANDSTONE);
    private NapoliWestQuarter() {}

    static void build(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(-317, 63, 43, -246, 63, 58)) {
            Block material = p.getZ() <= 49 ? Blocks.POLISHED_DEEPSLATE
                    : p.getZ() == 58 ? Blocks.CALCITE : Blocks.STONE_BRICKS;
            put(level, p, material);
        }
        for (BlockPos p : BlockPos.betweenClosed(-315, 60, 59, -244, 63, 116)) put(level, p, Blocks.WATER);
        for (int z = 57; z <= 77; z++) for (int x = -305; x <= -301; x++)
            put(level, new BlockPos(x, 63, z), Blocks.SPRUCE_PLANKS);
        for (int x = -314; x < -249; x += 12) {
            put(level, new BlockPos(x, 64, 55), Blocks.ANDESITE_WALL);
            put(level, new BlockPos(x, 65, 55), Blocks.LANTERN);
        }

        // Small garden opposite the pier with a market shelter and three residential entrances.
        for (int x = -306; x <= -274; x++) for (int z = 24; z <= 42; z++)
            put(level, new BlockPos(x, 63, z), x % 7 <= -2 && z < 39 ? Blocks.MOSS_BLOCK : Blocks.SMOOTH_SANDSTONE);
        for (int x : new int[]{-303, -294, -283, -276}) {
            for (int y = 64; y <= 67; y++) put(level, new BlockPos(x, y, 37), Blocks.OAK_LOG);
            var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            for (BlockPos p : BlockPos.betweenClosed(x - 1, 67, 36, x + 1, 69, 38))
                if (level.getBlockState(p).isAir()) level.setBlock(p, leaves, Block.UPDATE_CLIENTS);
            put(level, new BlockPos(x, 64, 41), Blocks.DARK_OAK_STAIRS);
        }
        for (int x = -270; x <= -258; x++) for (int z = 45; z <= 49; z++) {
            var cloth = CityBlocks.vanilla(x % 2 == 0 ? "white_wool" : "blue_wool");
            put(level, new BlockPos(x, 68, z), cloth);
        }
        for (int x : new int[]{-270, -258}) {
            for (int y = 64; y < 68; y++) put(level, new BlockPos(x, y, 45), Blocks.OAK_FENCE);
            put(level, new BlockPos(x, 64, 49), Blocks.BARREL);
        }
        NapoliEmploymentMap.sign(level, new BlockPos(-258, 64, 53), "MERGELLINA", "Nuovo approdo", "Mercato e case");
        NapoliEmploymentMap.sign(level, new BlockPos(-310, 64, 52), "GIARDINI", "Passeggiata", "Case in vendita");
        for (var house : NapoliMergellina.PROPERTIES) {
            BlockPos floor = new BlockPos(house.x(), 63, house.z());
            if (house.x() < -247 && !level.getBlockState(floor).is(Blocks.SPRUCE_PLANKS))
                NapoliMergellina.buildHouse(level, house, true);
            BlockPos shower = NapoliHygiene.shower(house);
            if (level.getBlockState(shower).isAir() && level.getBlockState(shower.above()).isAir()) {
                NapoliShower.place(level, shower);
                if (level.getBlockState(shower.north()).isAir()) put(level, shower.north(), Blocks.BARREL);
                NapoliEmploymentMap.sign(level, shower.west(2), "DOCCIA", "Tasto destro");
            }
        }
        level.setBlock(NapoliHygiene.PUBLIC_SINK, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(BlockStateProperties.LEVEL_CAULDRON, 3), Block.UPDATE_CLIENTS);
        NapoliEmploymentMap.sign(level, NapoliHygiene.PUBLIC_SINK.above(), "LAVABO", "Uso pubblico");
        var lodging = WORKER_HOME;
        NapoliMergellina.buildHouse(level, lodging, false);
        NapoliEmploymentMap.sign(level, lodging.sign(), "CASA DEI", "LAVORATORI");
        for (int x : new int[]{-268, -292, -309}) {
            BlockPos post = new BlockPos(x, 64, 48);
            if (level.getBlockState(post).isAir()) NapoliResident.spawn(level, NapoliResident.Role.PASSERBY, post, 0);
        }
        NapoliMergellina.completeUpperFloors(level);
        NapoliEmploymentMap.markAddressesAndJobs(level);
        NapoliCivicLife.expand(level);
    }

    private static void put(ServerLevel level, BlockPos p, Block block) {
        level.setBlock(p, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
