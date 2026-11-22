package dev.napuli;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/** An original adult fictional merchant with his own stall on Spaccanapoli. */
final class PeppeFetish {
    static final BlockPos STALL = new BlockPos(-4, 64, NapoliCentro.SPACCA_Z);
    static final int SOCKS_PRICE = 6, CHEESE_PRICE = 10;

    private PeppeFetish() {}

    static int visit(ServerPlayer player) {
        if (player.level().dimension() != NapoliMap.LEVEL && NapoliMap.visit(player) == 0) return 0;
        var city = player.level();
        NapoliMap.ensureBuilt(city);
        city.getChunkAt(STALL);
        if (city.getEntitiesOfClass(NapoliResident.class, new AABB(STALL).inflate(10, 3, 10),
                npc -> npc.isAlive() && npc.role() == NapoliResident.Role.PEPPE_FETISH).isEmpty())
            NapoliResident.spawn(city, NapoliResident.Role.PEPPE_FETISH, STALL, 0);
        return player.teleportTo(city, STALL.getX() + 0.5, STALL.getY(), STALL.getZ() + 3.5,
                Set.of(), 180, 0, true) ? 1 : 0;
    }
}
