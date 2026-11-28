package dev.napuli;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Landscapes the exposed top layer of Napoli's flat generator after the city builders have run. */
final class NapoliGround {
    static final AttachmentType<Boolean> FINISHED = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL).buildAndRegister(Napuli.id("napoli_flat_ground_v1"));
    private static final int SURFACE = NapoliMap.STREET - 1;

    private NapoliGround() {}

    // Register the saved marker before Minecraft reads any chunk attachments.
    static void register() {}

    static void finish(ServerLevel city, LevelChunk chunk) {
        if (chunk.getAttachedOrElse(FINISHED, false)) return;
        var bounds = chunk.getPos();
        if (!CityProperty.playerBuiltInChunk(city, bounds)) {
            var pos = new BlockPos.MutableBlockPos();
            for (int x = bounds.getMinBlockX(); x <= bounds.getMaxBlockX(); x++) {
                for (int z = bounds.getMinBlockZ(); z <= bounds.getMaxBlockZ(); z++) {
                    pos.set(x, SURFACE, z);
                    if (chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) != SURFACE
                            || !chunk.getBlockState(pos).is(Blocks.SAND)
                            || !chunk.getBlockState(pos.below()).is(Blocks.SANDSTONE)
                            || !chunk.getBlockState(pos.below(5)).is(Blocks.SANDSTONE)
                            || !chunk.getBlockState(pos.below(6)).is(Blocks.STONE)
                            || CityProperty.playerPlaced(city, pos)) continue;
                    city.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        chunk.setAttached(FINISHED, true);
    }
}
