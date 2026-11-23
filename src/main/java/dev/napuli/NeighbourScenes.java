package dev.napuli;

import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

final class NeighbourScenes {
    private record Conversation(long next, NapoliResident first, NapoliResident second, int topic) {}
    private static final AttachmentType<Conversation> SCENE = AttachmentRegistry.create(Napuli.id("neighbour_scene"));

    private NeighbourScenes() {}

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            var city = server.getLevel(NapoliMap.LEVEL);
            if (city != null && !city.players().isEmpty()) tick(city);
        });
    }

    private static void tick(ServerLevel city) {
        long now = city.getGameTime();
        Conversation scene = city.getAttached(SCENE);
        if (scene != null && now < scene.next()) return;
        var nearby = city.getPlayers(p -> NapoliMap.CAFE.distToCenterSqr(p.position()) < 18 * 18 && !p.isSpectator());
        if (scene != null && scene.second() != null) {
            if (!nearby.isEmpty() && scene.first().isAlive() && scene.second().isAlive()
                    && scene.second().distanceToSqr(scene.first()) <= 64)
                speak(scene.second(), scene.first(), nearby, scene.topic(), true);
            city.setAttached(SCENE, new Conversation(now + 900, null, null, 0));
            return;
        }
        if (nearby.isEmpty() || NapoliBroadcast.time(city) < 1400 || NapoliBroadcast.time(city) > 12400) return;
        var residents = city.getEntitiesOfClass(NapoliResident.class, new AABB(NapoliMap.CAFE).inflate(11),
                npc -> !npc.isSleeping() && npc.entityTags().isEmpty() && !npc.role().spirit() && !npc.role().fighter());
        if (residents.size() < 2) return;
        NapoliResident first = residents.getFirst();
        var second = residents.stream().skip(1).filter(npc -> npc.distanceToSqr(first) <= 64).findFirst().orElse(null);
        if (second == null) return;
        int topic = city.isRaining() ? 0 : NapoliBroadcast.time(city) < 5000 ? 1 : 2;
        speak(first, second, nearby, topic, false);
        city.setAttached(SCENE, new Conversation(now + 70, first, second, topic));
    }

    private static void speak(NapoliResident speaker, NapoliResident partner, List<ServerPlayer> audience,
                              int topic, boolean reply) {
        speaker.getLookControl().setLookAt(partner, 30, 25);
        Component line = Component.translatable("napuli.neighbour.scene." + topic + (reply ? ".reply" : ".start"),
                speaker.getDisplayName());
        for (ServerPlayer player : audience) player.sendSystemMessage(line);
    }
}
