package dev.napuli.client;

import dev.napuli.*;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Opt-in real packet/render test in a disposable world, including queues and dimension cleanup. */
final class SpeechBubbleTest {
    private static int titles,tick;private static boolean starting;private static volatile boolean ready,failed,done;
    private static volatile UUID first,second;private static NapoliResident left,right;
    private static final Component REPLY=Component.translatable("napuli.street.meme_nuttata");
    static void register(){if(!Boolean.getBoolean("napuli.speechtest"))return;ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(failed||done){mc.stop();return;}
        if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==90){mc.getLanguageManager().setSelected("it_it");mc.reloadResourcePacks();var settings=new LevelSettings("Nuvolette NPC test",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("napoli-speech-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
        if(mc.getSingleplayerServer()==null)return;
        if(!starting){if(mc.player.level().dimension()!=NapoliMap.LEVEL)return;starting=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.gui.toastManager().clear();if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();server(mc,p->{
            var city=p.level();for(var pos:BlockPos.betweenClosed(new BlockPos(90,139,-90),new BlockPos(115,139,-65)))city.setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),2);
            p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(city,100.5,140,-74.5,Set.of(),180,-10,true);
            left=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,new BlockPos(98,140,-81),0);left.setNoAi(true);left.setCustomName(Component.literal("Luca"));left.setCustomNameVisible(true);
            right=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,new BlockPos(103,140,-81),0);right.setNoAi(true);right.setCustomName(Component.literal("Maria"));right.setCustomNameVisible(true);first=left.getUUID();second=right.getUUID();
            city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set 6000");ready=true;
        });}if(!ready)return;
        try{tick++;
            if(tick==20)server(mc,p->{left.interact(p,InteractionHand.MAIN_HAND,left.position());NpcSpeech.say(p,right,REPLY);});
            if(tick==50){check(NpcSpeechBubbles.hasBubble(first)&&NpcSpeechBubbles.hasBubble(second),"Two actual server packets attach to distinct speakers");check(mc.gui.toastManager().getToast(NapoliNoticeToast.class,REPLY)==null,"Spoken lines do not create a toast");shot(mc,"speech-two-npcs");}
            if(tick==70)server(mc,p->left.setPos(96.5,140,-80.5));
            if(tick==100){var npc=find(mc,first);check(Math.abs(npc.getX()-96.5)<.2&&NpcSpeechBubbles.extract(npc,0)!=null,"The speech follows its moving NPC");shot(mc,"speech-following-npc");}
            if(tick==110)server(mc,p->NpcSpeech.say(p,left,Component.literal("Oggi apro la bottega, preparo il pane e porto le provviste a casa. Dopo il lavoro ci vediamo in piazza per un caffè e due chiacchiere con i vicini. Domani accompagno i bambini a scuola, poi torno al forno.")));
            if(tick==125)server(mc,p->p.teleportTo(p.level(),140.5,140,-74.5,Set.of(),180,-10,true));
            if(tick==140){check(NpcSpeechBubbles.hasBubble(first),"The timed speech still exists while out of hearing range");check(NpcSpeechBubbles.extract(find(mc,first),0)==null,"A distant speech is not rendered");server(mc,p->p.teleportTo(p.level(),100.5,140,-74.5,Set.of(),180,-10,true));}
            if(tick==230){var bubble=NpcSpeechBubbles.extract(find(mc,first),0);check(bubble!=null&&bubble.lines().size()>=3&&bubble.lines().size()<=6,"Long speech wraps into bounded pages");shot(mc,"speech-long-line");}
            if(tick==740){check(!NpcSpeechBubbles.hasBubble(first)&&!NpcSpeechBubbles.hasBubble(second),"Bubbles expire automatically");server(mc,p->NpcSpeech.say(p,left,Component.literal("Ci vediamo domani!")));}
            if(tick==760){check(NpcSpeechBubbles.hasBubble(first),"A new reply can be shown after expiry");server(mc,p->p.teleportTo(p.level().getServer().overworld(),100.5,140,-74.5,Set.of(),180,-10,true));}
            if(tick==790){check(mc.player.level().dimension()!=NapoliMap.LEVEL&&!NpcSpeechBubbles.hasBubble(first),"Changing dimension clears speech from the previous world");Napuli.LOGGER.info("[SPEECHTEST] ALL PASSED: real packets, distinct speakers, moving bubbles, wrapping, range, expiry and dimension cleanup");done=true;}
        }catch(Throwable e){failed=true;Napuli.LOGGER.error("[SPEECHTEST] FAILED",e);}
    });}
    private static NapoliResident find(Minecraft mc,UUID id){return mc.level.getEntitiesOfClass(NapoliResident.class,mc.player.getBoundingBox().inflate(160)).stream().filter(n->n.getUUID().equals(id)).findFirst().orElseThrow();}
    private static void server(Minecraft mc,Consumer<ServerPlayer> action){var s=mc.getSingleplayerServer();s.execute(()->{try{action.accept(s.getPlayerList().getPlayers().getFirst());}catch(Throwable e){failed=true;Napuli.LOGGER.error("[SPEECHTEST] FAILED on server",e);}});}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[SPEECHTEST] screenshot {}",name));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private SpeechBubbleTest(){}
}
