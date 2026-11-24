package dev.napuli;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;

/** Opt-in measurements; no frame or tick allocations in normal gameplay. */
public final class CityPerformance {
    private static final ArrayList<Double> TICKS = new ArrayList<>();
    private static long began; private static int samples;
    private static ThreadPoolExecutor writer;
    public static boolean enabled() { return Boolean.getBoolean("napuli.performance") || Boolean.getBoolean("napuli.realmp"); }
    public static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) return 0; var sorted = new ArrayList<>(values); Collections.sort(sorted);
        return sorted.get(Math.clamp((int)Math.ceil(sorted.size() * fraction) - 1, 0, sorted.size() - 1));
    }
    static void start() { if (enabled()) ServerTickEvents.START_SERVER_TICK.register(s -> began = System.nanoTime()); }
    static void register() {
        if (!enabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(s->writer=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var thread=new Thread(r,"napuli-performance-report");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.DiscardPolicy()));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            TICKS.add((System.nanoTime() - began) / 1_000_000.0); samples++;
            if (TICKS.size() > 6000) TICKS.removeFirst();
            if (samples % 200 == 0) {
                var city = s.getLevel(NapoliMap.LEVEL); var census = city == null ? null : NapoliCivicLife.census(city);
                var runtime = Runtime.getRuntime();
                var sorted=new ArrayList<>(TICKS);Collections.sort(sorted);
                String row = String.format(Locale.ROOT, "%d,%d,%d,%.3f,%.3f,%.3f,%d,%d%n", samples,
                        s.getPlayerList().getPlayerCount(), census == null ? 0 : census.people().size(),
                        sorted.get(Math.clamp((int)Math.ceil(sorted.size()*.5)-1,0,sorted.size()-1)), sorted.get(Math.clamp((int)Math.ceil(sorted.size()*.95)-1,0,sorted.size()-1)), sorted.get(Math.clamp((int)Math.ceil(sorted.size()*.99)-1,0,sorted.size()-1)),
                        (runtime.totalMemory() - runtime.freeMemory()) / 1048576, runtime.maxMemory() / 1048576);
                if(writer!=null)writer.execute(()->{try { var folder = Path.of("napulicraft", "reports"); Files.createDirectories(folder);
                    var file = folder.resolve("server-performance.csv");
                    if (!Files.exists(file)) Files.writeString(file, "tick,giocatori,residenti,tick_p50_ms,tick_p95_ms,tick_p99_ms,heap_usato_mb,heap_max_mb\n");
                    Files.writeString(file, row, StandardOpenOption.APPEND); }
                catch (Exception error) { Napuli.LOGGER.warn("Cannot save performance measurements", error); }});
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {if(writer!=null){writer.shutdown();try{writer.awaitTermination(1,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}writer=null;} TICKS.clear(); samples = 0; });
    }
    private CityPerformance() {}
}
