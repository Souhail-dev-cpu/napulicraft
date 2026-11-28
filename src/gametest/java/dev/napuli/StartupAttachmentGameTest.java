package dev.napuli;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;
import net.minecraft.gametest.framework.GameTestHelper;

/** Capture registration before any world or test fixture can initialize a late class. */
public final class StartupAttachmentGameTest implements ModInitializer {
    private static final List<String> MARKERS = List.of("home_decor_v3", "football_district_link_v1",
            "home_architecture_v1", "napoli_flat_ground_v1", "atlas_house_sections_v1", "street_meme_day");
    private static final Map<String,Boolean> REGISTERED_AT_STARTUP=new HashMap<>();

    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTING.register(server->{
            REGISTERED_AT_STARTUP.clear();
            for(var id:MARKERS){
                var type=AttachmentRegistryImpl.get(Napuli.id(id));
                REGISTERED_AT_STARTUP.put(id,type!=null&&type.isPersistent());
            }
        });
    }

    @GameTest public void savedChunkMarkersExistBeforeAnyWorldIsLoaded(GameTestHelper h){
        for(var id:MARKERS)
            h.assertTrue(Boolean.TRUE.equals(REGISTERED_AT_STARTUP.get(id)),"The persisted chunk marker is registered at mod initialization, before deserialization: "+id);
        h.succeed();
    }
}
