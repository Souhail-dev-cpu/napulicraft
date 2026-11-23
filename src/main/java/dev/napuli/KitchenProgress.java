package dev.napuli;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class KitchenProgress {
    private static final AttachmentType<Integer> PIZZAS = AttachmentRegistry.<Integer>builder().persistent(Codec.intRange(0, 1000000)).copyOnDeath().buildAndRegister(Napuli.id("pizzaiolo_reputation"));
    static final AttachmentType<Boolean> PUZZLE_READY = savedFlag("sartu_puzzle_ready"), PUZZLE_CLAIMED = savedFlag("sartu_puzzle_claimed");
    private static final AttachmentType<Long> HELP_COOLDOWN = AttachmentRegistry.<Long>builder().persistent(Codec.LONG).copyOnDeath().buildAndRegister(Napuli.id("chef_help_cooldown"));
    private static final AttachmentType<java.util.UUID> HELPER_OWNER = AttachmentRegistry.<java.util.UUID>builder().persistent(UUIDUtil.CODEC).buildAndRegister(Napuli.id("chef_helper_owner"));
    private static final AttachmentType<Long> HELPER_UNTIL = AttachmentRegistry.<Long>builder().persistent(Codec.LONG).buildAndRegister(Napuli.id("chef_helper_until"));
    private KitchenProgress() {}
    private static AttachmentType<Boolean> savedFlag(String id) { return AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath().buildAndRegister(Napuli.id(id)); }
    public static int pizzas(ServerPlayer player) { return player.getAttachedOrElse(PIZZAS, 0); }
    public static int helpers(ServerPlayer player) { int pizzas = pizzas(player); return pizzas >= 30 ? 3 : pizzas >= 15 ? 2 : pizzas >= 5 ? 1 : 0; }
    public static void crafted(ServerPlayer player, ItemStack stack, int amount) {
        if (amount <= 0 || !NapoliKitchen.pizza(stack)) return;
        int before = helpers(player);
        player.setAttached(PIZZAS, Math.min(1000000, pizzas(player) + Math.min(amount, 64)));
        if (helpers(player) > before) NapoliNotice.show(player, Component.translatable("napuli.kitchen.chef", pizzas(player), helpers(player)));
    }
    static void questStarted(ServerPlayer player) {
        int count = helpers(player);
        long now = player.level().getGameTime();
        if (count == 0 || now < player.getAttachedOrElse(HELP_COOLDOWN, 0L)) return;
        player.setAttached(HELP_COOLDOWN, now + 3600);
        for (int i = 0; i < count; i++) {
            var npc = NapoliResident.spawn(player.level(), NapoliResident.Role.PIZZAIOLO, player.blockPosition().offset(i + 1, 0, 1), player.getYRot());
            npc.setAttached(HELPER_OWNER, player.getUUID()); npc.setAttached(HELPER_UNTIL, now + 2400);
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Napuli.BATON));
            npc.setDropChance(EquipmentSlot.MAINHAND, 0);
        }
        NapoliKitchen.give(player, new ItemStack(Napuli.PIZZA, count));
        NapoliNotice.show(player, Component.translatable("napuli.kitchen.help", count));
    }
    static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, dealt, blocked) -> {
            if (!(entity instanceof ServerPlayer player) || dealt <= 0 || !(source.getEntity() instanceof LivingEntity attacker) || attacker.isAlliedTo(player)) return;
            if (attacker instanceof NapoliResident npc && player.getUUID().equals(npc.getAttached(HELPER_OWNER))) return;
            for (NapoliResident npc : player.level().getEntitiesOfClass(NapoliResident.class, player.getBoundingBox().inflate(24),
                    n -> player.getUUID().equals(n.getAttached(HELPER_OWNER)))) npc.setTarget(attacker);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (var level : server.getAllLevels()) {
                var helpers = new java.util.ArrayList<NapoliResident>();
                for (var entity : level.getAllEntities()) if (entity instanceof NapoliResident npc && npc.hasAttached(HELPER_OWNER)) helpers.add(npc);
                for (var npc : helpers) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(npc.getAttached(HELPER_OWNER));
                    if (level.getGameTime() >= npc.getAttachedOrElse(HELPER_UNTIL, 0L) || owner == null || owner.level() != level) { npc.discard(); continue; }
                    if (npc.getTarget() != null && (!npc.getTarget().isAlive() || npc.getTarget().distanceToSqr(owner) > 32 * 32)) npc.setTarget(null);
                    if (npc.getTarget() == null && npc.distanceToSqr(owner) > 36) npc.getNavigation().moveTo(owner, 1.15);
                }
            }
        });
    }
}
