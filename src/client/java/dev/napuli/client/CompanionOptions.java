package dev.napuli.client;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import dev.napuli.Napuli;
import net.minecraft.client.Minecraft;

final class CompanionOptions {
    static CompanionOptions current = new CompanionOptions();
    boolean city = true, kitchen = true, tracker = true, compact, notices = true;
    boolean lightPreset, reducedParticles, reducedFlashes, reducedMotion, quietAmbience, textAlerts=true;
    Boolean savedBob, savedLightning;
    Double savedTilt, savedFov, savedScreen;
    int size = 1;
    private static Path path() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config/napulicraft-interface.json"); }
    static void load() {
        if(!Files.isRegularFile(path())) return;
        try(var reader=Files.newBufferedReader(path())) {
            var value=new GsonBuilder().create().fromJson(reader,CompanionOptions.class);
            if(value!=null){value.size=Math.clamp(value.size,0,2);current=value;}
        } catch(Exception e) { Napuli.LOGGER.warn("Cannot read interface preferences; using defaults",e); }
    }
    void save() {
        try {Files.createDirectories(path().getParent());try(var writer=Files.newBufferedWriter(path())){new GsonBuilder().setPrettyPrinting().create().toJson(this,writer);}}
        catch(Exception e){Napuli.LOGGER.warn("Cannot save interface preferences",e);}
        CompanionEffectsClient.send();
    }
    float scale() {return size==0?.85f:size==2?1.15f:1f;}
    private CompanionOptions() {}
}
