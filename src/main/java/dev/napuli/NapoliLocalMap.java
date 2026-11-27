package dev.napuli;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class NapoliLocalMap {
    static final BlockPos LAUNDRY = new BlockPos(-34, 64, -124);
    static final BlockPos ROOF = new BlockPos(-34, 90, -120);
    static final BlockPos ROOF_MARK = ROOF.offset(8, 0, 0);
    static final BlockPos TUNNEL_ENTRY = new BlockPos(-19, 64, -125);
    static final BlockPos TUNNEL_START = new BlockPos(-19, 42, -125);
    static final BlockPos CISTERN = new BlockPos(-1, 42, -109);
    private static final BlockPos MARKET = new BlockPos(72, 64, -125);

    private NapoliLocalMap() {}

    private record Spot(NapoliResident.Role role, BlockPos pos, float yaw) {}

    static void build(ServerLevel level) {
        // An excavated gallery, a vaulted chamber and a raised path to the old cistern.
        room(level, -22, -128, -5, -122, 41, 46);
        room(level, -9, -128, 5, -104, 41, 48);
        room(level, -15, -116, -5, -107, 41, 47);
        fill(level, -5, 41, -115, 4, 41, -106, Blocks.PRISMARINE_BRICKS);
        fill(level, -4, 40, -114, -3, 40, -106, Blocks.PRISMARINE_BRICKS);
        fill(level, -4, 41, -114, -3, 41, -106, Blocks.WATER);
        fill(level, 1, 40, -114, 3, 40, -106, Blocks.PRISMARINE_BRICKS);
        fill(level, 1, 41, -114, 3, 41, -106, Blocks.WATER);
        fill(level, -2, 41, -116, 0, 41, -105, Blocks.SMOOTH_SANDSTONE);
        for (int z : new int[]{-126, -119, -113, -107}) {
            fill(level, 4, 42, z, 4, 46, z, Blocks.CUT_SANDSTONE);
            place(level, new BlockPos(3, 42, z), Blocks.SOUL_LANTERN);
        }
        place(level, new BlockPos(-15, 42, -127), Blocks.LANTERN);
        place(level, new BlockPos(-8, 42, -125), Blocks.LANTERN);
        place(level, CISTERN.below(), Blocks.CHISELED_SANDSTONE);
        place(level, CISTERN.offset(-1, 0, 2), Blocks.LANTERN);

        place(level, new BlockPos(-13, 42, -109), Blocks.AMETHYST_BLOCK);
        place(level, new BlockPos(-13, 43, -109), Blocks.AMETHYST_CLUSTER);
        place(level, new BlockPos(-12, 42, -109), Blocks.BUDDING_AMETHYST);
        sign(level, TUNNEL_ENTRY.offset(1, 0, 1), "NAPOLI", "SOTTERRANEA", "Ingresso");
        sign(level, CISTERN.offset(0, 0, 2), "CISTERNA", "dell’acquedotto");

        // Terrace and laundry lines above Spaccanapoli.
        fill(level, -37, 89, -122, -24, 89, -117, Blocks.SMOOTH_SANDSTONE);
        fill(level, -37, 90, -122, -24, 90, -122, Blocks.BRICK_WALL);
        fill(level, -37, 90, -117, -24, 90, -117, Blocks.BRICK_WALL);
        fill(level, -37, 90, -121, -37, 90, -118, Blocks.BRICK_WALL);
        fill(level, -24, 90, -121, -24, 90, -118, Blocks.BRICK_WALL);
        for (int x : new int[]{-32, -27}) {
            fill(level, x, 91, -122, x, 93, -122, Blocks.DARK_OAK_FENCE);
            fill(level, x, 91, -117, x, 93, -117, Blocks.DARK_OAK_FENCE);
            fill(level, x, 93, -121, x, 93, -118, Blocks.SPRUCE_FENCE);
            place(level, new BlockPos(x, 92, -120), wool("white"));
            place(level, new BlockPos(x, 92, -119), wool("light_blue"));
        }
        place(level, ROOF_MARK.below(), Blocks.CHISELED_SANDSTONE);

        // Porta Nolana market stall.
        fill(level, 69, 63, -128, 75, 63, -123, Blocks.COBBLED_DEEPSLATE);
        for (int x : new int[]{69, 75}) for (int z : new int[]{-128, -123})
            fill(level, x, 64, z, x, 67, z, Blocks.SPRUCE_FENCE);
        for (int x = 69; x <= 75; x++)
            fill(level, x, 68, -128, x, 68, -123, x % 2 == 0 ? wool("white") : wool("red"));
        fill(level, 70, 64, -127, 74, 64, -127, Blocks.BARREL);
        place(level, MARKET.offset(0, 0, 2), Blocks.LANTERN);
        sign(level, MARKET.offset(2, 0, 2), "PORTA NOLANA", "Mercato");

        for (Spot spot : List.of(
                new Spot(NapoliResident.Role.BUCATO, LAUNDRY, 0),
                new Spot(NapoliResident.Role.BUCATO, ROOF.offset(-1, 0, 0), -90),
                new Spot(NapoliResident.Role.TURISTA, new BlockPos(118, 64, -19), 180),
                new Spot(NapoliResident.Role.PESCATORE, new BlockPos(9, 64, 52), 180),
                new Spot(NapoliResident.Role.CUSTODE_TUNNEL, TUNNEL_ENTRY, 0),
                new Spot(NapoliResident.Role.CUSTODE_TUNNEL, TUNNEL_START.offset(1, 0, -1), 0),
                new Spot(NapoliResident.Role.CARTOMANTE, new BlockPos(-37, 64, -125), 0),
                new Spot(NapoliResident.Role.MERCATANTE, MARKET, 0),
                new Spot(NapoliResident.Role.MUSICISTA, new BlockPos(123, 64, -10), 0),
                new Spot(NapoliResident.Role.GUARDIANO_PRESEPE, new BlockPos(30, 64, -142), 0))) {
            level.getChunkAt(spot.pos());
            NapoliResident.spawn(level, spot.role(), spot.pos(), spot.yaw());
        }
        Napuli.LOGGER.info("Napoli v11: resident quests, rooftops and underground cistern");
    }

    private static void sign(ServerLevel level, BlockPos pos, String... lines) {
        place(level, pos, Blocks.OAK_SIGN);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            var text = net.minecraft.world.level.block.entity.SignText.EMPTY.asMutable();
            for (int i = 0; i < lines.length; i++) text.setLine(i, net.minecraft.network.chat.Component.literal(lines[i]));
            sign.setText(text.asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
            sign.setText(text.asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.BACK);
            sign.setChanged();
        }
    }

    private static Block wool(String color) {
        return CityBlocks.vanilla(color + "_wool");
    }

    private static void room(ServerLevel level, int x1, int z1, int x2, int z2, int y1, int y2) {
        fill(level, x1, y1, z1, x2, y2, z2, Blocks.SANDSTONE);
        fill(level, x1 + 1, y1 + 1, z1 + 1, x2 - 1, y2 - 1, z2 - 1, Blocks.AIR);
        // Open connecting walls where the galleries meet.
        fill(level, -10, 42, -126, -7, 44, -124, Blocks.AIR);
        fill(level, -10, 42, -113, -7, 44, -110, Blocks.AIR);
    }

    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) place(level, pos, block);
    }

    private static void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
