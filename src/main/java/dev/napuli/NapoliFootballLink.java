package dev.napuli;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/** Joins the older football ground and its surroundings to the first streets of the atlas. */
final class NapoliFootballLink {
    private static final AttachmentType<Boolean> COMPLETE = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL).buildAndRegister(Napuli.id("football_district_link_v1"));

    private static final int WEST = 350;
    private static final int EAST = 523;
    private static final int NORTH = -88;
    private static final int SOUTH = 99;
    private static final int CROSS_STREET = 65;

    private NapoliFootballLink() {}

    static void fit(ServerLevel level, LevelChunk chunk) {
        var bounds = chunk.getPos();
        if (bounds.getMaxBlockX() < WEST || bounds.getMinBlockX() > EAST
                || bounds.getMaxBlockZ() < NORTH || bounds.getMinBlockZ() > SOUTH
                || chunk.getAttachedOrElse(COMPLETE, false)) return;

        for (int x = Math.max(WEST, bounds.getMinBlockX()); x <= Math.min(EAST, bounds.getMaxBlockX()); x++) {
            for (int z = Math.max(NORTH, bounds.getMinBlockZ()); z <= Math.min(SOUTH, bounds.getMaxBlockZ()); z++) {
                if (NapoliExpansion.water(x, z) || footballPitch(x, z)) continue;
                var surface = new BlockPos(x, NapoliExpansion.height(x, z), z);
                var old = chunk.getBlockState(surface);
                boolean path = footpath(x, z);
                if (!old.is(Blocks.SAND) && !(path && old.is(Blocks.GRASS_BLOCK) && NapoliExpansion.writable(x, z))) continue;
                var above = chunk.getBlockState(surface.above());
                if (!above.isAir() || CityProperty.playerPlaced(level, surface) || CityProperty.playerPlaced(level, surface.above())) continue;
                Block material = path ? (onPathEdge(x, z) ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE) : Blocks.GRASS_BLOCK;
                level.setBlock(surface, material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (!path && treeBed(x, z)) level.setBlock(surface, Blocks.MOSS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        chunk.setAttached(COMPLETE, true);
    }

    private static boolean footballPitch(int x, int z) {
        return x >= FootballGround.MIN_X - 6 && x <= FootballGround.MAX_X + 5
                && z >= FootballGround.MIN_Z - 3 && z <= FootballGround.MAX_Z + 5;
    }

    private static boolean footpath(int x, int z) {
        return Math.abs(z - CROSS_STREET) <= 2 || Math.abs(x - 444) <= 2
                || (x <= 444 && Math.abs(z + 12) <= 2)
                || (x >= 480 && Math.abs(x - 481) <= 1);
    }

    private static boolean onPathEdge(int x, int z) {
        if (Math.abs(z - CROSS_STREET) <= 2) return Math.abs(z - CROSS_STREET) == 2;
        return Math.abs(x - 444) == 2 || Math.abs(z + 12) == 2 || x == 482;
    }

    private static boolean treeBed(int x, int z) {
        return Math.floorMod(x + 4, 18) <= 2 && Math.floorMod(z + 6, 18) <= 2;
    }
}
