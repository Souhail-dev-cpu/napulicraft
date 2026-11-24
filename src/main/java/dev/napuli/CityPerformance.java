package dev.napuli;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;

/** Opt-in measurements; no frame or tick allocations in normal gameplay. */
public final class CityPerformance {
    private static final ArrayList<Double> TICKS = new ArrayList<>();
    private static long began; private static int samples;
    public static boolean enabled() { return Boolean.getBoolean("napuli.performance") || Boolean.getBoolean("napuli.realmp"); }
    public static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) return 0; var sorted = new ArrayList<>(values); Collections.sort(sorted);
        return sorted.get(Math.clamp((int)Math.ceil(sorted.size() * fraction) - 1, 0, sorted.size() - 1));
    }
    static void start() { if (enabled()) ServerTickEvents.START_SERVER_TICK.register(s -> began = System.nanoTime()); }
    static void register() {
        if (!enabled()) return;
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            TICKS.add((System.nanoTime() - began) / 1_000_000.0); samples++;
            if (TICKS.size() > 6000) TICKS.removeFirst();
            if (samples % 200 == 0) {
                var city = s.getLevel(NapoliMap.LEVEL); var census = city == null ? null : NapoliCivicLife.census(city);
                var runtime = Runtime.getRuntime();
                String row = String.format(Locale.ROOT, "%d,%d,%d,%.3f,%.3f,%.3f,%d,%d%n", samples,
                        s.getPlayerList().getPlayerCount(), census == null ? 0 : census.people().size(),
                        percentile(TICKS, .5), percentile(TICKS, .95), percentile(TICKS, .99),
                        (runtime.totalMemory() - runtime.freeMemory()) / 1048576, runtime.maxMemory() / 1048576);
                try { var folder = Path.of("napulicraft", "reports"); Files.createDirectories(folder);
                    var file = folder.resolve("server-performance.csv");
                    if (!Files.exists(file)) Files.writeString(file, "tick,giocatori,residenti,tick_p50_ms,tick_p95_ms,tick_p99_ms,heap_usato_mb,heap_max_mb\n");
                    Files.writeString(file, row, StandardOpenOption.APPEND); }
                catch (Exception error) { Napuli.LOGGER.warn("Cannot save performance measurements", error); }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { TICKS.clear(); samples = 0; });
    }
    private CityPerformance() {}
}
