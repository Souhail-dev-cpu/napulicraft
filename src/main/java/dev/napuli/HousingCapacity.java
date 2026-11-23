package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** One combined capacity for residents and players, regardless of age or game mode. */
final class HousingCapacity {
    static final int MAX_OCCUPANTS = 5;
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();
    private static final List<NapoliMergellina.House> AUTHORED = authored();

    private record Home(CityProperty.Home bounds, BlockPos entrance) {}
    private static final class State {
        List<CityProperty.Home> layout;
        List<Home> homes;
        final Map<Home, List<UUID>> admitted = new HashMap<>();
        final Map<UUID, Vec3> outside = new HashMap<>();
        final Map<UUID, Long> notified = new HashMap<>();
    }

    private static List<NapoliMergellina.House> authored() {
        var houses = new LinkedHashMap<String, NapoliMergellina.House>();
        for (var house : CityLivingHomes.all()) houses.put(house.id(), house);
        for (var house : NapoliMergellina.allHouses()) houses.put(house.id(), house);
        houses.put(NapoliWestQuarter.WORKER_HOME.id(), NapoliWestQuarter.WORKER_HOME);
        return List.copyOf(houses.values());
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var city = server.getLevel(NapoliMap.LEVEL);
            if (city != null) enforce(city);
        });
    }

    private static boolean occupant(Entity entity) {
        return !entity.isRemoved() && entity.isAlive()
                && (entity instanceof NapoliResident || entity instanceof ServerPlayer);
    }

    private static List<Home> homes(ServerLevel city) {
        var state = STATES.computeIfAbsent(city, ignored -> new State());
        var layout = CityProperty.homes(city);
        if (layout.equals(state.layout)) return state.homes;
        var homes = new ArrayList<Home>();
        for (var h : AUTHORED) homes.add(new Home(new CityProperty.Home(
                new BlockPos(h.x(), 61, h.z()),
                new BlockPos(h.x() + h.width() - 1, Math.max(78, 64 + h.floors() * 5), h.z() + h.depth() - 1)),
                h.entrance().south()));
        // The four inhabited Borgo Marinari buildings predate the property registry.
        for (int i = 0; i < 4; i++) {
            int x = 19 + i * 7;
            int floors = i % 2 == 0 ? 3 : 4;
            homes.add(new Home(new CityProperty.Home(new BlockPos(x, 63, 82),
                    new BlockPos(x + 6, 64 + floors * 4, 91)), new BlockPos(x + 3, 64, 80)));
        }
        // Include older homes and houses registered with the city's property system.
        for (var bounds : layout) {
            if (homes.stream().anyMatch(h -> h.bounds().min().getX() == bounds.min().getX()
                    && h.bounds().min().getZ() == bounds.min().getZ())) continue;
            homes.add(new Home(bounds, new BlockPos((bounds.min().getX() + bounds.max().getX()) / 2,
                    bounds.min().getY() + 1, bounds.max().getZ() + 2)));
        }
        state.layout = List.copyOf(layout);
        state.homes = List.copyOf(homes);
        return state.homes;
    }

    private static Home at(List<Home> homes, BlockPos position) {
        for (var home : homes) if (home.bounds().contains(position)) return home;
        return null;
    }

    static boolean canEnter(ServerLevel city, BlockPos destination, UUID entrant) {
        var homes = homes(city);
        var home = at(homes, destination);
        if (home == null) return true;
        int count = 0;
        for (var entity : city.getAllEntities()) {
            if (occupant(entity) && !entity.getUUID().equals(entrant)
                    && home.equals(at(homes, entity.blockPosition())) && ++count >= MAX_OCCUPANTS) return false;
        }
        return true;
    }

    static int reservedOccupants(ServerLevel city, NapoliMergellina.House house,
                                 Collection<NapoliCivicLife.Citizen> residents) {
        var ids = new HashSet<UUID>();
        for (var citizen : residents) if (citizen.property().equals(house.id())) ids.add(citizen.id());
        for (var entity : city.getAllEntities())
            if (occupant(entity) && house.contains(entity.blockPosition())) ids.add(entity.getUUID());
        return ids.size();
    }

    static void enforce(ServerLevel city) {
        var state = STATES.computeIfAbsent(city, ignored -> new State());
        var homes = homes(city);
        var groups = new HashMap<Home, List<Entity>>();
        var present = new HashSet<UUID>();
        for (var entity : city.getAllEntities()) {
            if (!occupant(entity)) continue;
            present.add(entity.getUUID());
            var home = at(homes, entity.blockPosition());
            if (home == null) state.outside.put(entity.getUUID(), entity.position());
            else groups.computeIfAbsent(home, ignored -> new ArrayList<>()).add(entity);
        }
        for (var entry : groups.entrySet()) {
            var home = entry.getKey();
            var inside = entry.getValue();
            var previous = state.admitted.getOrDefault(home, List.of());
            // Existing occupants keep their places; simultaneous arrivals use a stable order.
            inside.sort(Comparator.<Entity>comparingInt(e -> {
                int index = previous.indexOf(e.getUUID());
                return index < 0 ? MAX_OCCUPANTS : index;
            }).thenComparingInt(Entity::getId));
            int allowed = Math.min(MAX_OCCUPANTS, inside.size());
            state.admitted.put(home, inside.subList(0, allowed).stream().map(Entity::getUUID).toList());
            for (int i = allowed; i < inside.size(); i++) {
                var entity = inside.get(i);
                Vec3 exit = exit(city, homes, home, state.outside.get(entity.getUUID()));
                entity.stopRiding();
                if (entity instanceof NapoliResident npc) {
                    if (npc.isSleeping()) npc.stopSleeping();
                    npc.getNavigation().stop();
                }
                entity.setDeltaMovement(Vec3.ZERO);
                if (entity instanceof ServerPlayer player) {
                    player.teleportTo(city, exit.x, exit.y, exit.z, Set.of(), player.getYRot(), player.getXRot(), true);
                    if (city.getGameTime() - state.notified.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) >= 100) {
                        player.sendOverlayMessage(Component.translatable("napuli.property.full", MAX_OCCUPANTS));
                        state.notified.put(player.getUUID(), city.getGameTime());
                    }
                } else entity.snapTo(exit.x, exit.y, exit.z, entity.getYRot(), entity.getXRot());
            }
        }
        state.admitted.keySet().retainAll(groups.keySet());
        state.outside.keySet().retainAll(present);
        state.notified.keySet().retainAll(present);
    }

    private static Vec3 exit(ServerLevel city, List<Home> homes, Home home, Vec3 previous) {
        if (previous != null && at(homes, BlockPos.containing(previous)) == null
                && NapoliResidentHomes.walkable(city, BlockPos.containing(previous))) return previous;
        var entrance = home.entrance();
        for (int radius = 0; radius <= 8; radius++) {
            for (var pos : BlockPos.betweenClosed(entrance.offset(-radius, 0, -radius), entrance.offset(radius, 0, radius))) {
                if (at(homes, pos) == null && NapoliResidentHomes.walkable(city, pos)) return Vec3.atBottomCenterOf(pos);
            }
        }
        // A completely barricaded exit must not trap an extra occupant inside the house.
        var gate = new BlockPos(0, 64, -37);
        city.getChunkAt(gate);
        while (!NapoliResidentHomes.walkable(city, gate) && gate.getY() < city.getMaxY() - 2) gate = gate.above();
        return Vec3.atBottomCenterOf(gate);
    }

    private HousingCapacity() {}
}
