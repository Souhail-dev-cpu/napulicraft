package dev.napuli.client;

import dev.napuli.CityEffects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;

/** Backups also survive a client restart while reduced-motion mode is enabled. */
final class CompanionEffectsClient {
    static void send(){var p=CompanionOptions.current;if(ClientPlayNetworking.canSend(CityEffects.Preferences.TYPE))ClientPlayNetworking.send(new CityEffects.Preferences((p.lightPreset?CityEffects.LIGHT:0)|(p.reducedParticles?CityEffects.PARTICLES:0)|(p.reducedFlashes?CityEffects.FLASHES:0)|(p.quietAmbience?CityEffects.QUIET:0)|(p.textAlerts?CityEffects.TEXT:0)));}
    static void apply(Minecraft mc){var p=CompanionOptions.current;var o=mc.options;boolean changed=false;
        if(p.reducedMotion){if(p.savedBob==null){p.savedBob=o.bobView().get();p.savedTilt=o.damageTiltStrength().get();p.savedFov=o.fovEffectScale().get();changed=true;}o.bobView().set(false);o.damageTiltStrength().set(0.0);o.fovEffectScale().set(0.0);}
        else if(p.savedBob!=null){o.bobView().set(p.savedBob);o.damageTiltStrength().set(p.savedTilt==null?1:p.savedTilt);o.fovEffectScale().set(p.savedFov==null?1:p.savedFov);p.savedBob=null;p.savedTilt=null;p.savedFov=null;changed=true;}
        if(p.reducedFlashes){if(p.savedLightning==null){p.savedLightning=o.hideLightningFlash().get();p.savedScreen=o.screenEffectScale().get();changed=true;}o.hideLightningFlash().set(true);o.screenEffectScale().set(0.0);}
        else if(p.savedLightning!=null){o.hideLightningFlash().set(p.savedLightning);o.screenEffectScale().set(p.savedScreen==null?1:p.savedScreen);p.savedLightning=null;p.savedScreen=null;changed=true;}
        if(changed)p.save();
    }
    static void register(){ClientPlayConnectionEvents.JOIN.register((h,s,c)->send());ClientTickEvents.END_CLIENT_TICK.register(CompanionEffectsClient::apply);}
    private CompanionEffectsClient(){}
}
