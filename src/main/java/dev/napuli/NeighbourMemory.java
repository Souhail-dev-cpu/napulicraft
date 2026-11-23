package dev.napuli;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class NeighbourMemory {
    private static final AttachmentType<Map<String, Integer>> RELATIONS = AttachmentRegistry
            .<Map<String, Integer>>builder().persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
            .buildAndRegister(Napuli.id("neighbour_memory"));

    private NeighbourMemory() {}

    private static String key(ServerPlayer p, NapoliResident npc) { return npc.getUUID().toString(); }

    static int trust(ServerPlayer p, NapoliResident npc) {
        return p.getAttachedOrElse(RELATIONS, Map.of()).getOrDefault(key(p, npc), 0);
    }

    static void remember(ServerPlayer p, NapoliResident npc, int change) {
        Map<String, Integer> copy = new HashMap<>(p.getAttachedOrElse(RELATIONS, Map.of()));
        copy.put(key(p, npc), Math.clamp(trust(p, npc) + change, -20, 20));
        p.setAttached(RELATIONS, Map.copyOf(copy));
    }

    static int price(ServerPlayer p, NapoliResident npc, int base) {
        int trust = trust(p, npc);
        return Math.max(1, base + (trust >= 6 ? -1 : trust <= -3 ? 2 : 0));
    }

    static void greet(ServerPlayer p, NapoliResident npc) {
        int trust = trust(p, npc);
        if (trust == 0) return;
        String key = trust < 0 ? "distrust" : trust >= 6 ? "regular" : "returning";
        p.sendSystemMessage(Component.translatable("napuli.neighbour." + key, npc.getDisplayName()));
    }
}
