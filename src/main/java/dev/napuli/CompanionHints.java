package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.*;
import static dev.napuli.CityText.t;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** One suggestion per first encounter, with a persistent opt-out and a replayable help menu. */
final class CompanionHints {
    static final AttachmentType<List<String>> SEEN=AttachmentRegistry.<List<String>>builder().persistent(Codec.STRING.listOf()).copyOnDeath().buildAndRegister(Napuli.id("companion_hints_seen"));
    static final AttachmentType<Boolean> ENABLED=AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath().buildAndRegister(Napuli.id("companion_hints_enabled"));
    static void encounter(ServerPlayer p,String id,Component message) {
        if(!p.getAttachedOrElse(ENABLED,true))return;var seen=p.getAttachedOrElse(SEEN,List.of());if(seen.contains(id))return;
        var next=new ArrayList<>(seen);next.add(id);p.setAttached(SEEN,List.copyOf(next));NapoliNotice.show(p,t("hints.message",message));
    }
    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getTickCount()%100!=0)return;var city=s.getLevel(NapoliMap.LEVEL);if(city==null)return;
            for(var p:city.players()) {
                if(p.containerMenu instanceof KitchenMenus.Grid || p.containerMenu instanceof KitchenMenus.Heat)
                    encounter(p,"cucina",t("hints.cooking"));
                else if(DistrictLife.districts().stream().anyMatch(d->d.business().entrance().distToCenterSqr(p.position())<12*12))
                    encounter(p,"bottega",t("hints.shop"));
                else if(CityNetwork.stops().stream().anyMatch(stop->stop.entrance().distToCenterSqr(p.position())<10*10))
                    encounter(p,"trasporti",t("hints.travel"));
            }
        });
    }
    private CompanionHints() {}
}
