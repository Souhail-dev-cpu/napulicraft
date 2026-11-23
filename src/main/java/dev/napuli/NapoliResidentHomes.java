package dev.napuli;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class NapoliResidentHomes {
    private NapoliResidentHomes() {}

    private static final List<BlockPos> ANCHORS = anchors();

    private static List<BlockPos> anchors() {
        var homes = new ArrayList<>(NapoliMergellina.homes());
        for (int[] house : NapoliInteriors.homes()) homes.add(new BlockPos(house[0] + 5, 64, house[1] + 7));
        for (int x : new int[]{21, 28, 35, 42}) homes.add(new BlockPos(x + 1, 64, 87));
        return List.copyOf(homes);
    }

    static BlockPos destination(ServerLevel city, BlockPos post, int offset) {
        return ANCHORS.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(post)))
                .map(home -> {
                    city.getChunkAt(home);
                    BlockPos preferred = home.offset(offset % 5 - 2, 0, offset / 5 % 3 - 1);
                    if (walkable(city, preferred)) return preferred;
                    for (BlockPos pos : BlockPos.betweenClosed(home.offset(-2, 0, -2), home.offset(2, 0, 2)))
                        if (walkable(city, pos)) return pos.immutable();
                    return null;
                }).orElse(null);
    }

    static boolean walkable(ServerLevel city, BlockPos pos) {
        if (!city.hasChunkAt(pos)) return false;
        return city.getFluidState(pos).isEmpty()
                && city.getBlockState(pos).getCollisionShape(city, pos).isEmpty()
                && city.getBlockState(pos.above()).getCollisionShape(city, pos.above()).isEmpty()
                && !city.getBlockState(pos.below()).getCollisionShape(city, pos.below()).isEmpty();
    }
}
