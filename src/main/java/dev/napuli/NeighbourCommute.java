package dev.napuli;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

final class NeighbourCommute {
    private record Wait(long day, long arrived, boolean done) {}
    private static final AttachmentType<Wait> WAIT = AttachmentRegistry.create(Napuli.id("commute_wait"));

    private NeighbourCommute() {}

    static BlockPos target(ServerLevel city, NapoliResident npc, BlockPos home, BlockPos destination) {
        if (home.getX() > -350 || destination.getX() < -350 || Math.abs(home.getZ() - destination.getZ()) < 30)
            return destination;
        long day = NapoliBroadcast.day(city), now = city.getGameTime();
        var wait = npc.getAttached(WAIT);
        if (wait != null && wait.day() == day && wait.done()) return destination;
        BlockPos stop = new BlockPos(-340, 64, Math.clamp(home.getZ(), -182, 195));
        if (npc.getVehicle() instanceof CityVehicle) {
            npc.setActivity(NapoliHouseholds.Activity.COMMUTE);
            if (Math.abs(npc.getZ() - destination.getZ()) < 6 || wait == null || now - wait.arrived() > 360) {
                npc.stopRiding();
                npc.setAttached(WAIT, new Wait(day, now, true));
            }
            return destination;
        }
        if (stop.distToCenterSqr(npc.position()) > 4 && (wait == null || wait.day() != day)) return stop;
        if (wait == null || wait.day() != day) {
            wait = new Wait(day, now, false);
            npc.setAttached(WAIT, wait);
        }
        if (now - wait.arrived() >= 160) {
            npc.setAttached(WAIT, new Wait(day, now, true));
            return destination;
        }
        double direction = Math.signum(destination.getZ() - npc.getZ());
        for (var bus : city.getEntitiesOfClass(CityVehicle.class, new AABB(stop).inflate(4),
                v -> v.kind() == 1 && Math.signum(v.getLookAngle().z) == direction)) {
            if (npc.startRiding(bus, false, false)) {
                npc.setActivity(NapoliHouseholds.Activity.COMMUTE);
                return destination;
            }
        }
        npc.setActivity(NapoliHouseholds.Activity.WAIT_TRANSIT);
        return stop;
    }
}
