package dev.napuli;

import java.util.ArrayList;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Only a chest actually opened by this player is eligible for a prank. */
final class MunacielloFriendship {
    private static final AttachmentType<GlobalPos> LAST_CHEST = AttachmentRegistry.<GlobalPos>builder()
            .persistent(GlobalPos.CODEC).copyOnDeath().buildAndRegister(Napuli.id("munaciello_chest"));
    private static final AttachmentType<Object> LAST_MENU = AttachmentRegistry.create(Napuli.id("munaciello_seen_menu"));
    private MunacielloFriendship() {}
    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getAttached(LAST_MENU) == player.containerMenu) continue;
                player.setAttached(LAST_MENU, player.containerMenu);
                if (!(player.containerMenu instanceof ChestMenu menu)) continue;
                for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-6, -5, -6), player.blockPosition().offset(6, 5, 6))) {
                    if (!(player.level().getBlockEntity(pos) instanceof ChestBlockEntity chest)) continue;
                    var container = menu.getContainer();
                    if (container == chest || container instanceof CompoundContainer compound && compound.contains(chest)) {
                        remember(player, pos); break;
                    }
                }
            }
        });
    }
    static void remember(ServerPlayer player, BlockPos pos) { player.setAttached(LAST_CHEST, GlobalPos.of(player.level().dimension(), pos.immutable())); }
    static ChestBlockEntity chest(Munaciello monk, ServerPlayer player) {
        GlobalPos home = player.getAttached(LAST_CHEST);
        if (home == null || home.dimension() != monk.level().dimension() || !monk.level().hasChunkAt(home.pos())
                || home.pos().distToCenterSqr(monk.position()) > 64 * 64) return null;
        return monk.level().getBlockEntity(home.pos()) instanceof ChestBlockEntity chest ? chest : null;
    }
    static boolean hide(Munaciello monk, ServerPlayer player) {
        var chest = chest(monk, player);
        if (chest == null || monk.getMainHandItem().isEmpty()) return false;
        var inventory = new ArrayList<ItemStack>();
        for (int i = 0; i < chest.getContainerSize(); i++) inventory.add(chest.getItem(i).copy());
        if (!KitchenStationBlock.insert(inventory, monk.getMainHandItem().copy())
                || !KitchenStationBlock.insert(inventory, new ItemStack(NapoliKitchen.item("moneta"), 1 + monk.getRandom().nextInt(3)))) return false;
        for (int i = 0; i < inventory.size(); i++) chest.setItem(i, inventory.get(i));
        chest.setChanged();
        monk.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var pos = chest.getBlockPos();
        player.sendSystemMessage(Component.translatable("napuli.munaciello.hidden", pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }
}
