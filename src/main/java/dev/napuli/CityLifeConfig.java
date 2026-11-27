package dev.napuli;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Server-owned settings; no client can change population, prices or the calendar. */
final class CityLifeConfig {
    static Settings settings = new Settings();
    static final class Fixture {
        String opponent = "Juventus";
        int everyDays = 7, dayOffset = 0, kickoff = 13000, fullTime = 15000;
        int napoliGoals = -1, opponentGoals = -1;
    }
    static final class Settings {
        int populationVersion = 0;
        int nightVersion = 0;
        int mobilityVersion = 0;
        int population = 120;
        int startingCash = 16;
        int maximumPopulation = 1024;
        int districtPopulation = 456;
        int visibleResidentsPerPlayer = 96;
        int visibleResidentsMaximum = 256;
        int policePatrolLimit = 32;
        int nightActivityPercent = 40;
        int nightThieves = 8;
        int citizenVehicleLimit = 24;
        int policeVehicleLimit = 12;
        int needsIntervalTicks = 600;
        int daysPerYear = 7;
        int retirementAge = 70;
        int maximumAge = 95;
        List<Fixture> fixtures = List.of(new Fixture());
    }
    /** Upgrade only the complete old preset, preserving administrators' custom population limits. */
    static boolean upgradePopulation(Settings value) {
        if (value.populationVersion >= 1) return false;
        if (value.population == 120 && value.districtPopulation == 76 && value.maximumPopulation == 384) {
            value.districtPopulation = 456;
            value.maximumPopulation = 1024;
        }
        value.populationVersion = 1;
        return true;
    }
    static void load() {
        var file = FabricLoader.getInstance().getConfigDir().resolve("napulicraft-city.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(file)) {
                Settings parsed = gson.fromJson(Files.readString(file), Settings.class);
                if (parsed != null) settings = parsed;
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, gson.toJson(settings));
            }
        } catch (Exception error) {
            Napuli.LOGGER.warn("Cannot read city settings; using defaults", error);
        }
        boolean upgraded = upgradePopulation(settings);
        boolean nightUpgrade=settings.nightVersion<1;settings.nightVersion=1;
        boolean mobilityUpgrade=settings.mobilityVersion<2;settings.mobilityVersion=2;
        settings.maximumPopulation = Math.clamp(settings.maximumPopulation, 24, 2048);
        settings.startingCash = Math.clamp(settings.startingCash, 8, 1000);
        settings.districtPopulation = Math.clamp(settings.districtPopulation,0,456);
        settings.visibleResidentsPerPlayer = Math.clamp(settings.visibleResidentsPerPlayer,16,160);
        settings.visibleResidentsMaximum = Math.clamp(settings.visibleResidentsMaximum,settings.visibleResidentsPerPlayer,512);
        settings.policePatrolLimit = Math.clamp(settings.policePatrolLimit,0,64);
        settings.nightActivityPercent = Math.clamp(settings.nightActivityPercent,0,80);
        settings.nightThieves = Math.clamp(settings.nightThieves,0,24);
        settings.citizenVehicleLimit = Math.clamp(settings.citizenVehicleLimit,0,64);
        settings.policeVehicleLimit = Math.clamp(settings.policeVehicleLimit,0,32);
        settings.population = Math.clamp(settings.population, 8, settings.maximumPopulation);
        settings.needsIntervalTicks = Math.clamp(settings.needsIntervalTicks, 400, 1200);
        settings.daysPerYear = Math.clamp(settings.daysPerYear, 1, 365);
        settings.retirementAge = Math.clamp(settings.retirementAge, 50, 90);
        settings.maximumAge = Math.clamp(settings.maximumAge, settings.retirementAge + 5, 120);
        if (settings.fixtures == null) settings.fixtures = List.of(new Fixture());
        settings.fixtures = settings.fixtures.stream().filter(f -> f != null && f.opponent != null
                && !f.opponent.isBlank() && f.everyDays > 0 && f.kickoff >= 0
                && f.fullTime > f.kickoff && f.fullTime < 21000).toList();
        for(var fixture:settings.fixtures){fixture.opponent=fixture.opponent.substring(0,Math.min(64,fixture.opponent.length()));fixture.napoliGoals=Math.clamp(fixture.napoliGoals,-1,20);fixture.opponentGoals=Math.clamp(fixture.opponentGoals,-1,20);}
        if (upgraded||nightUpgrade||mobilityUpgrade) try { Files.writeString(file, gson.toJson(settings)); }
        catch (Exception error) { Napuli.LOGGER.warn("Cannot save upgraded population settings", error); }
    }
    private CityLifeConfig() {}
}
