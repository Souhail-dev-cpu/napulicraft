package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class NeighbourMemory {
    static final AttachmentType<Map<String, Integer>> RELATIONS = AttachmentRegistry
            .<Map<String, Integer>>builder().persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
            .buildAndRegister(Napuli.id("neighbour_memory"));

    private NeighbourMemory() {}

    private static String key(ServerPlayer p, NapoliResident npc) { return npc.getUUID().toString(); }

    static int trust(ServerPlayer p, NapoliResident npc) {
        int value=CitySocial.bond(p.level(),npc.getUUID(),p.getUUID()).trust()-50;return value>=0?value*2/5:value/8;
    }

    static void remember(ServerPlayer p, NapoliResident npc, int change) {
        var city=p.level();var id=npc.getUUID();
        if(change<0){CitySocial.harm(city,id,p.getUUID());return;}
        CitySocial.meet(city,id,p.getUUID(),NapoliBroadcast.day(city));
        if(change<=2)CitySocial.purchased(city,id,p.getUUID());else CitySocial.helped(city,id,p.getUUID(),NapoliBroadcast.day(city));
    }

    static int price(ServerPlayer p, NapoliResident npc, int base) {
        return CityEconomy.price(p,npc.getUUID(),base);
    }

    static void greet(ServerPlayer p, NapoliResident npc) {
        var b=CitySocial.meet(p.level(),npc.getUUID(),p.getUUID(),NapoliBroadcast.day(p.level()));
        if(b.meetings()>1||b.grievances()>0)p.sendSystemMessage(Component.literal(npc.getName().getString()+": "+CitySocial.greeting(b,p.getName().getString())));
        String heard=CityReputation.tell(p,npc.getUUID());if(!heard.isEmpty())p.sendSystemMessage(Component.literal(npc.getName().getString()+": "+heard));
    }
}
