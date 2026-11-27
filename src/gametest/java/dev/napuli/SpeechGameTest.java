package dev.napuli;

import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class SpeechGameTest {
    @GameTest public void directRepliesPreserveTheirPriorityAcrossTheNetwork(GameTestHelper h){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{var packet=new NpcSpeech(UUID.randomUUID(),Component.literal("A presto!"),true);NpcSpeech.CODEC.encode(buffer,packet);
            h.assertTrue(NpcSpeech.CODEC.decode(buffer).equals(packet),"The client receives both the reply and the instruction to replace stale lines");
            h.assertTrue(!new NpcSpeech(packet.speaker(),packet.message()).immediate(),"Ambient speech retains its ordinary ordered queue");
        }finally{buffer.release();}h.succeed();
    }
    @GameTest public void speechPacketPreservesSpeakerAndClientTranslation(GameTestHelper h){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{var packet=new NpcSpeech(UUID.randomUUID(),Component.translatable("napuli.street.meme_ritardo"));NpcSpeech.CODEC.encode(buffer,packet);var decoded=NpcSpeech.CODEC.decode(buffer);
            h.assertTrue(decoded.equals(packet),"The packet retains the exact NPC identity and translatable message instead of flattening it on the server");
        }finally{buffer.release();}h.succeed();
    }
    @GameTest public void speechIsRestrictedToNearbyLivingSpeakers(GameTestHelper h){
        var city=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));var npc=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,pos,0);npc.setNoAi(true);
        try{HousingCapacityGameTest.withPlayers(h,2,players->{var near=players.get(0);var far=players.get(1);near.setPos(pos.getX()+1,pos.getY(),pos.getZ());far.setPos(pos.getX()+NpcSpeech.RANGE+2,pos.getY(),pos.getZ());
            h.assertTrue(NpcSpeech.say(near,npc,Component.literal("Ciao!")),"A nearby player can receive the reply, including a vanilla fallback");
            h.assertTrue(!NpcSpeech.say(far,npc,Component.literal("Ciao!")),"A distant multiplayer participant cannot receive this conversation");
            npc.discard();h.assertTrue(!NpcSpeech.say(near,npc,Component.literal("Ciao!")),"A removed NPC cannot produce a new speech bubble");
        });}finally{npc.discard();}h.succeed();
    }
}
