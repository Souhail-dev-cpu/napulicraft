package dev.napuli;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;

/** Bounded, physical access checks. Generated streets and doorways are checked in their real chunks. */
final class CityAccessibility {
    record Target(String id, BlockPos outside, BlockPos inside) {}
    record Result(String id, String state, BlockPos position, int visited) {}
    private record Search(boolean reached, int visited) {}
    private static final Map<ServerLevel, ArrayDeque<Target>> QUEUES = new WeakHashMap<>();
    private static final Map<ServerLevel, List<Result>> RESULTS = new WeakHashMap<>();

    static List<Target> targets(boolean allBuildings) {
        var all = new LinkedHashMap<String, Target>();
        for (var home : java.util.stream.Stream.concat(CityLivingHomes.all().stream(),NapoliMergellina.allHouses().stream()).toList())
            all.put(home.id(), new Target(home.id(), home.approach(1), home.inside()));
        for (var district : DistrictLife.districts()) {
            var h = district.business();
            all.put(h.id(), new Target(h.id(), h.approach(1), h.inside()));
        }
        if (allBuildings) for (int gx = -52; gx <= 51; gx++) for (int gz = -21; gz <= 13; gz++) {
            int x = gx * 40 + 9, z = gz * 40 + 9;
            if (!NapoliExpansion.houseAllowed(x, z)) continue;
            int y = NapoliExpansion.legacyHeight(x + 11, z + 24) + 1;
            // Coastal palazzi open onto the upper street; the seafront can be several storeys below.
            var approach = NapoliExpansion.water(x + 11, z + 32)
                    ? CityRoutes.nearest(new BlockPos(x + 11, y, z + 24)).position()
                    : new BlockPos(x + 11, CityStreets.walkingHeight(x + 11, z + 32) + 1, z + 32);
            all.put("palazzo_" + gx + "_" + gz, new Target("palazzo_" + gx + "_" + gz,
                    approach,
                    new BlockPos(x + 11, y, z + 21)));
        }
        if(allBuildings)for(int x=-2038;x<2040;x+=40)for(int z=-798;z<540;z+=40){
            if(!NapoliExpansion.writable(x,z)||NapoliExpansion.water(x,z)||!CityStreets.adjusted(x,z)||!CityStreets.adjusted(x+2,z))continue;
            String id="incrocio_"+x+"_"+z;all.put(id,new Target(id,new BlockPos(x,CityStreets.walkingHeight(x,z)+1,z),new BlockPos(x+2,CityStreets.walkingHeight(x+2,z)+1,z)));
        }
        for (var place : NapoliAtlas.PLACES) {
            var at = place.arrival();
            all.put("luogo_" + place.id(), new Target("luogo_" + place.id(), at, at));
        }
        for (var stop : CityNetwork.stops()) for (var line : CityNetwork.lines()) {
            if (line.mode() != CityNetwork.Mode.METRO || !line.stops().contains(stop.id())) continue;
            all.put("metro_" + line.id() + "_" + stop.id(), new Target("metro_" + line.id() + "_" + stop.id(),
                    stop.entrance(), stop.boarding(line)));
        }
        return List.copyOf(all.values());
    }

    static boolean walkable(ServerLevel city, BlockPos at) {
        if (!city.hasChunkAt(at)) return false;
        var feet = city.getBlockState(at); var head = city.getBlockState(at.above());
        boolean ladder = feet.is(Blocks.LADDER) || feet.is(Blocks.VINE);
        return (ladder || !city.getBlockState(at.below()).getCollisionShape(city, at.below()).isEmpty())
                && clear(city, at, feet) && clear(city, at.above(), head);
    }
    private static boolean clear(ServerLevel city, BlockPos at, net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.is(Blocks.LADDER) || state.is(Blocks.VINE)
                || state.getCollisionShape(city, at).isEmpty();
    }
    static boolean connected(ServerLevel city, BlockPos from, BlockPos to) { return search(city, from, to).reached(); }
    private static Search search(ServerLevel city, BlockPos from, BlockPos to) {
        if (!walkable(city, from) || !walkable(city, to)) return new Search(false, 0);
        var queue = new PriorityQueue<BlockPos>(Comparator.comparingInt((BlockPos p)->Math.abs(p.getX()-to.getX())+Math.abs(p.getZ()-to.getZ())+2*Math.abs(p.getY()-to.getY()))); var seen = new HashSet<BlockPos>();
        queue.add(from); seen.add(from);
        int minX = Math.min(from.getX(), to.getX()) - 10, maxX = Math.max(from.getX(), to.getX()) + 10;
        int minZ = Math.min(from.getZ(), to.getZ()) - 10, maxZ = Math.max(from.getZ(), to.getZ()) + 10;
        int low = Math.min(from.getY(), to.getY()) - 2, high = Math.max(from.getY(), to.getY()) + 3;
        while (!queue.isEmpty() && seen.size() < 8192) {
            var current = queue.remove();
            if (current.equals(to)) return new Search(true, seen.size());
            for (var direction : Direction.Plane.HORIZONTAL) for (int dy : new int[]{0, 1, -1}) {
                var next = current.relative(direction).offset(0, dy, 0);
                if (next.getX() < minX || next.getX() > maxX || next.getZ() < minZ || next.getZ() > maxZ
                        || next.getY() < low || next.getY() > high || seen.contains(next)) continue;
                if (dy > 0 && !clear(city, current.above(2), city.getBlockState(current.above(2)))) continue;
                if (dy < 0 && !clear(city, next.above(2), city.getBlockState(next.above(2)))) continue;
                if (walkable(city, next)) { seen.add(next); queue.add(next); }
            }
            if (city.getBlockState(current).is(Blocks.LADDER) || city.getBlockState(current).is(Blocks.VINE))
                for (var next : List.of(current.above(), current.below()))
                    if (next.getY() >= low && next.getY() <= high && !seen.contains(next) && walkable(city, next)) {
                        seen.add(next); queue.add(next);
                    }
        }
        return new Search(false, seen.size());
    }
    static Result inspect(ServerLevel city, Target target) {
        int minX = Math.min(target.outside().getX(), target.inside().getX()) - 10;
        int maxX = Math.max(target.outside().getX(), target.inside().getX()) + 10;
        int minZ = Math.min(target.outside().getZ(), target.inside().getZ()) - 10;
        int maxZ = Math.max(target.outside().getZ(), target.inside().getZ()) + 10;
        boolean custom = false;
        for (int x = minX >> 4; x <= maxX >> 4; x++) for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
            var chunk = city.getChunk(x, z);
            custom |= CityProperty.playerBuiltInChunk(city, chunk.getPos());
            NapoliExpansion.generate(city, chunk); NapoliExpansion.repairEntrances(city, chunk);
            CityStreets.fit(city, chunk); NapoliExpansion.upgradeArchitecture(city, chunk);
            NapoliExpansion.upgradeInteriors(city, chunk); DistrictLife.fit(city, chunk); CityRailway.fit(city, chunk);
        }
        var result = search(city, target.outside(), target.inside());
        if(!result.reached())Napuli.LOGGER.warn("[CITYACCESS] {}: exterior={} ground={} feet={} head={}; interior={} ground={} feet={} head={}",target.id(),target.outside(),city.getBlockState(target.outside().below()),city.getBlockState(target.outside()),city.getBlockState(target.outside().above()),target.inside(),city.getBlockState(target.inside().below()),city.getBlockState(target.inside()),city.getBlockState(target.inside().above()));
        return new Result(target.id(), result.reached() ? "OK" : custom ? "PERSONALIZZATO" : "BLOCCATO",
                target.inside(), result.visited());
    }
    static void start(ServerLevel city, boolean all) {
        var selected=System.getProperty("napuli.audit.targets","");var ids=selected.isBlank()?Set.<String>of():new HashSet<>(Arrays.asList(selected.split(",")));
        QUEUES.put(city, new ArrayDeque<>(targets(all).stream().filter(t->ids.isEmpty()||ids.contains(t.id())).toList())); RESULTS.put(city, new ArrayList<>());
    }
    static int remaining(ServerLevel city){var q=QUEUES.get(city);return q==null?0:q.size();}
    static boolean running(ServerLevel city) { return QUEUES.containsKey(city); }
    static List<Result> results(ServerLevel city) { return List.copyOf(RESULTS.getOrDefault(city, List.of())); }
    private static void tick(ServerLevel city) {
        var queue = QUEUES.get(city); if (queue == null) return;
        var target = queue.poll(); if (target != null) RESULTS.get(city).add(inspect(city, target));
        if (queue.isEmpty()) {
            QUEUES.remove(city);
            var report = new StringBuilder("id,stato,x,y,z,nodi\n");
            for (var r : RESULTS.get(city)) report.append(r.id()).append(',').append(r.state()).append(',')
                    .append(r.position().getX()).append(',').append(r.position().getY()).append(',')
                    .append(r.position().getZ()).append(',').append(r.visited()).append('\n');
            try { var folder = Path.of("napulicraft", "reports"); Files.createDirectories(folder);
                Files.writeString(folder.resolve("accessibilita.csv"), report); }
            catch (Exception e) { Napuli.LOGGER.error("Cannot save accessibility report", e); }
            Napuli.LOGGER.info("[CITYACCESS] Complete: {} checks, {} blocked", RESULTS.get(city).size(),
                    RESULTS.get(city).stream().filter(r -> r.state().equals("BLOCCATO")).count());
        }
    }
    static void register() {
        CommandRegistrationCallback.EVENT.register((d, a, e) -> d.register(Commands.literal("citta")
                .then(Commands.literal("verifica").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> { var city = c.getSource().getServer().getLevel(NapoliMap.LEVEL);
                            if (city == null) return 0; start(city, true);
                            c.getSource().sendSuccess(() -> Component.literal("Verifica avviata; rapporto in napulicraft/reports/accessibilita.csv"), false); return 1; }))));
        ServerTickEvents.END_SERVER_TICK.register(s -> { var city = s.getLevel(NapoliMap.LEVEL);
            if (city != null && (Boolean.getBoolean("napuli.accessaudit")||s.getTickCount() % 4 == 0)) tick(city); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { QUEUES.clear(); RESULTS.clear(); });
    }
    private CityAccessibility() {}
}
