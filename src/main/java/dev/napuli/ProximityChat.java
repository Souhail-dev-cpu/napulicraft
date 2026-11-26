package dev.napuli;

import static dev.napuli.CityText.t;
import java.time.Instant;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/** Server-side recipient routing. Signed message bodies and Minecraft's content filters stay intact. */
public final class ProximityChat {
    public static final int CHAT_RANGE=15,WHISPER_RANGE=5,EAR_RANGE=2,SHOUT_RANGE=70;

    private static boolean nearby(ServerPlayer sender,ServerPlayer recipient,int range){
        return sender.level()==recipient.level()&&sender.distanceToSqr(recipient)<=(double)range*range;
    }

    /** Player broadcasts are local; messages without a player sender retain their normal routing. */
    public static boolean shouldReceive(ServerPlayer sender,ServerPlayer recipient){
        return sender==null||nearby(sender,recipient,CHAT_RANGE);
    }

    private static boolean validate(ServerPlayer sender,ServerPlayer recipient,int range){
        if(sender==recipient){sender.sendSystemMessage(t("chat.self"));return false;}
        var players=sender.level().getServer().getPlayerList();
        if(players.getPlayer(sender.getUUID())!=sender)return false;
        if(players.getPlayer(recipient.getUUID())!=recipient||!nearby(sender,recipient,range)){
            sender.sendSystemMessage(t("chat.too_far",recipient.getDisplayName(),range));return false;
        }
        return true;
    }

    static void register(){
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            dispatcher.register(Commands.literal("urlo").requires(source->source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("messaggio",MessageArgument.message()).executes(context->{
                    var sender=context.getSource().getPlayerOrException();
                    MessageArgument.resolveChatMessage(context,"messaggio",message->{
                        var server=sender.level().getServer();
                        if(server.getPlayerList().getPlayer(sender.getUUID())!=sender)return;
                        if(message.signedContent().isBlank()){sender.sendSystemMessage(t("chat.shout_empty"));return;}
                        var name=t("chat.urlo_name",sender.getDisplayName()).copy().withStyle(ChatFormatting.GOLD);
                        var type=ChatType.bind(ChatType.CHAT,sender.level().registryAccess(),name);
                        var outgoing=OutgoingChatMessage.create(message);
                        boolean anyFiltered=false;
                        // Select the 70-block audience directly instead of using the normal 15-block broadcast.
                        for(var recipient:sender.level().players()){
                            if(!nearby(sender,recipient,SHOUT_RANGE))continue;
                            boolean filtered=recipient!=sender&&context.getSource().shouldFilterMessageTo(recipient);
                            recipient.sendChatMessage(outgoing,filtered,type);
                            anyFiltered|=filtered&&message.isFullyFiltered();
                        }
                        server.logChatMessage(message.decoratedContent(),type,message.hasSignature()&&!message.hasExpiredServer(Instant.now())?null:"Not Secure");
                        if(anyFiltered)sender.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
                    });
                    return 1;
                })));
            for(String command:new String[]{"sussurro","bisbiglio"}){
                int range=command.equals("sussurro")?WHISPER_RANGE:EAR_RANGE;
                dispatcher.register(Commands.literal(command).requires(source->source.getEntity() instanceof ServerPlayer)
                    .then(Commands.argument("giocatore",EntityArgument.player())
                        .suggests((context,builder)->{
                            var sender=context.getSource().getPlayerOrException();
                            return SharedSuggestionProvider.suggest(sender.level().players().stream()
                                .filter(peer->peer!=sender&&nearby(sender,peer,range))
                                .map(peer->peer.getGameProfile().name()),builder);
                        })
                        .then(Commands.argument("messaggio",MessageArgument.message()).executes(context->{
                            var sender=context.getSource().getPlayerOrException();var recipient=EntityArgument.getPlayer(context,"giocatore");
                            if(!validate(sender,recipient,range))return 0;
                            MessageArgument.resolveChatMessage(context,"messaggio",message->{
                                // Decoration/filtering can be asynchronous: recheck position and connection at delivery.
                                if(!validate(sender,recipient,range))return;
                                if(message.signedContent().isBlank()){sender.sendSystemMessage(t("chat.empty"));return;}
                                var outgoing=OutgoingChatMessage.create(message);
                                var from=t("chat."+command+"_name",sender.getDisplayName());
                                var to=t("chat."+command+"_name",recipient.getDisplayName());
                                var incomingType=ChatType.bind(ChatType.MSG_COMMAND_INCOMING,sender.level().registryAccess(),from);
                                var outgoingType=ChatType.bind(ChatType.MSG_COMMAND_OUTGOING,sender.level().registryAccess(),sender.getDisplayName()).withTargetName(to);
                                sender.sendChatMessage(outgoing,false,outgoingType);
                                boolean filtered=context.getSource().shouldFilterMessageTo(recipient);
                                recipient.sendChatMessage(outgoing,filtered,incomingType);
                                if(filtered&&message.isFullyFiltered())sender.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
                            });
                            return 1;
                        }))));
            }
        });
    }
    private ProximityChat(){}
}
