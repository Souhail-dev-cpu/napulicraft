package dev.napuli.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import dev.napuli.ProximityChat;
import java.util.function.Predicate;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Filter recipients before sending any chat payload, preserving the vanilla signed-chat pipeline. */
@Mixin(PlayerList.class)
abstract class ProximityChatMixin {
    @WrapWithCondition(
        method="broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V")
    )
    private boolean napuli$nearbyChat(ServerPlayer recipient,OutgoingChatMessage outgoing,boolean filtered,ChatType.Bound type,
                                    PlayerChatMessage original,Predicate<ServerPlayer> filter,ServerPlayer sender,ChatType.Bound originalType){
        return ProximityChat.shouldReceive(sender,recipient);
    }
}
