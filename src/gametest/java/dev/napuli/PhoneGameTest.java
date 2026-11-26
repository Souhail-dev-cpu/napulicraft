package dev.napuli;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;

public final class PhoneGameTest {
    @GameTest public void fiveHandsetsHaveSurvivalRecipesAndIndependentHardware(GameTestHelper h){
        Set<Integer> batteries=new HashSet<>();
        for(var model:PhoneModel.values()){
            var item=Phones.ITEMS.get(model);h.assertTrue(item!=null&&item.getDefaultInstance().getMaxStackSize()==1,"Every requested phone is a real non-stackable item");
            h.assertTrue(Phones.charge(new ItemStack(item))==model.battery&&batteries.add(model.battery),"Each handset starts with its documented capacity");
            var holder=h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Napuli.id(model.id))).orElseThrow();
            h.assertTrue(holder.value() instanceof CraftingRecipe,"Phones are craftable in survival");
        }h.succeed();
    }
    @GameTest public void messagesArePrivateLiteralAndBoundedAcrossSerialization(GameTestHelper h){with(h,3,ps->{
        var a=ps.get(0);var b=ps.get(1);var outsider=ps.get(2);
        h.assertTrue(Phones.message(a,b.getUUID()," /op Someone\n§c Ciao "),"A normal private message is accepted");
        h.assertTrue(Phones.mail(a).size()==1&&Phones.mail(b).size()==1&&Phones.mail(outsider).isEmpty(),"Only the two participants receive mail");
        h.assertTrue(Phones.mail(b).getFirst().body().equals("/op Someonec Ciao")&&!Phones.mail(b).getFirst().outgoing(),"Chat text remains literal and control codes are stripped");
        var world=h.getLevel().getServer().overworld();
        for(int i=0;i<80;i++)Phones.message(a,b.getUUID(),"Message "+i);
        h.assertTrue(Phones.mail(a).size()==64&&Phones.mail(b).size()==64,"Mail history cannot grow beyond its bound");
        var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());
        var saved=Phones.Mail.CODEC.listOf().encodeStart(ops,Phones.mail(b)).getOrThrow();
        h.assertTrue(Phones.Mail.CODEC.listOf().parse(ops,saved).getOrThrow().equals(Phones.mail(b)),"Conversations retain sender, text, direction and time in a save");
        h.assertTrue(Phones.resolve(a,Phones.account(b).number()).equals(b.getUUID()),"Phone numbers resolve to the authoritative account");
    });h.succeed();}
    @GameTest public void staleTokensAndMissingOrEmptyPhonesCannotSend(GameTestHelper h){with(h,2,ps->{
        var a=ps.get(0);var b=ps.get(1);Phones.open(a,PhoneModel.ONEPLUS_16);var token=a.getAttached(Phones.SESSION).token();
        Phones.action(a,new PhonePacket.Action(UUID.randomUUID(),"message",b.getUUID().toString(),"Forged"));
        h.assertTrue(Phones.mail(b).isEmpty(),"An unknown menu token cannot send private messages");
        Phones.open(a,PhoneModel.ONEPLUS_16);Phones.action(a,new PhonePacket.Action(token,"message",b.getUUID().toString(),"Replay"));
        h.assertTrue(Phones.mail(b).isEmpty(),"Reopening invalidates the previous token");
        Phones.charge(Phones.find(a,null),0);h.assertTrue(!Phones.message(a,b.getUUID(),"Empty")&&!Phones.dial(a,b.getUUID()),"An empty phone cannot communicate");
        a.getInventory().clearContent();h.assertTrue(!Phones.message(a,b.getUUID(),"Missing"),"Owning no handset disables messaging");
    });h.succeed();}
    @GameTest public void callsRequireConsentAndAudioOnlyReachesTheOtherParticipant(GameTestHelper h){with(h,3,ps->{
        var a=ps.get(0);var b=ps.get(1);var outsider=ps.get(2);
        h.assertTrue(Phones.dial(a,b.getUUID())&&!Phones.dial(outsider,b.getUUID()),"A ringing phone cannot accept a second caller");
        var call=Phones.snapshot(a,false,Component.empty()).call();var frame=new PhonePacket.Voice(call.id(),new byte[PhonePacket.Voice.FRAME_BYTES]);
        h.assertTrue(Phones.voiceTarget(a,frame)==null,"No microphone audio is relayed before acceptance");
        h.assertTrue(!Phones.answer(a,call.id().toString())&&!Phones.answer(b,UUID.randomUUID().toString()),"Only the intended recipient can accept the exact call");
        h.assertTrue(Phones.answer(b,call.id().toString()),"The recipient can answer");
        h.assertTrue(Phones.voiceTarget(a,frame)==b&&Phones.voiceTarget(b,frame)==a&&Phones.voiceTarget(outsider,frame)==null,"Private audio is relayed only between the accepted participants");
        h.assertTrue(Phones.voiceTarget(a,new PhonePacket.Voice(UUID.randomUUID(),frame.pcm()))==null,"A forged call identifier cannot route audio");
        for(int i=0;i<40;i++)Phones.voiceTarget(a,frame);
        h.assertTrue(Phones.voiceTarget(a,frame)==null,"Voice packet flooding is bounded");
        Phones.end(b,Component.empty());h.assertTrue(Phones.voiceTarget(a,frame)==null&&Phones.snapshot(a,false,Component.empty()).call()==null,"Hanging up invalidates both audio routes");
    });h.succeed();}
    @GameTest public void airplaneModeAndLosingAHandsetEndBothSidesOfACall(GameTestHelper h){with(h,2,ps->{
        var a=ps.get(0);var b=ps.get(1);Phones.dial(a,b.getUUID());var call=Phones.snapshot(a,false,Component.empty()).call();Phones.answer(b,call.id().toString());
        b.getInventory().clearContent();Phones.tick(h.getLevel().getServer());
        h.assertTrue(Phones.snapshot(a,false,Component.empty()).call()==null,"Losing the handset ends the remote participant's call");
        b.getInventory().setItem(0,new ItemStack(Phones.ITEMS.get(PhoneModel.IQOO_16)));b.setAttached(Phones.AIRPLANE,true);
        h.assertTrue(!Phones.dial(a,b.getUUID())&&!Phones.message(b,a.getUUID(),"Airplane"),"Airplane mode blocks calls and outgoing messages");
    });h.succeed();}
    @GameTest public void chargerConsumesExactlyOneRedstoneAndPreservesPhoneData(GameTestHelper h){with(h,1,ps->{
        var p=ps.getFirst();p.setGameMode(GameType.SURVIVAL);var phone=Phones.find(p,null);Phones.charge(phone,1000);
        h.assertTrue(!Phones.recharge(p)&&Phones.charge(phone)==1000,"Charging requires the USB-C accessory");
        p.getInventory().setItem(1,new ItemStack(Phones.CHARGER));p.getInventory().setItem(2,new ItemStack(Items.REDSTONE,2));
        h.assertTrue(Phones.recharge(p)&&Phones.charge(phone)==3000&&p.getInventory().countItem(Items.REDSTONE)==1,"One redstone recharges 2000 mAh exactly once");
        var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());var saved=ItemStack.CODEC.encodeStart(ops,phone).getOrThrow();var restored=ItemStack.CODEC.parse(ops,saved).getOrThrow();
        h.assertTrue(Phones.charge(restored)==3000,"Battery state follows the physical item through serialization");
        Phones.charge(phone,PhoneModel.ONEPLUS_16.battery);h.assertTrue(!Phones.recharge(p)&&p.getInventory().countItem(Items.REDSTONE)==1,"A full battery does not consume redstone");
    });h.succeed();}
    @GameTest public void phonePacketRoundTripPreservesPrivateMessagesAndCallIdentifiers(GameTestHelper h){with(h,2,ps->{
        var a=ps.get(0);var b=ps.get(1);Phones.open(a,null);Phones.message(a,b.getUUID(),"Una foto da Napoli");Phones.dial(a,b.getUUID());
        var original=Phones.snapshot(a,true,Component.empty());var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{PhonePacket.CODEC.encode(buf,original);var decoded=PhonePacket.CODEC.decode(buf);h.assertTrue(original.equals(decoded),"The complete real phone state survives the network codec");}
        finally{buf.release();}
        var voice=new PhonePacket.Voice(original.call().id(),new byte[PhonePacket.Voice.FRAME_BYTES]);buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{PhonePacket.Voice.CODEC.encode(buf,voice);var decoded=PhonePacket.Voice.CODEC.decode(buf);h.assertTrue(decoded.call().equals(voice.call())&&Arrays.equals(decoded.pcm(),voice.pcm()),"Voice frames retain their exact call and audio bytes");}finally{buf.release();}
    });h.succeed();}
    @GameTest public void sharingAHandsetNeverSharesTheOriginalOwnersMessages(GameTestHelper h){with(h,3,ps->{
        var a=ps.get(0);var b=ps.get(1);var receiver=ps.get(2);Phones.message(a,b.getUUID(),"Privato");var handset=a.getInventory().removeItem(0,1);receiver.getInventory().setItem(0,handset);
        h.assertTrue(Phones.snapshot(receiver,true,Component.empty()).messages().isEmpty()&&Phones.mail(a).size()==1,"Mail belongs to the SIM account rather than the stolen handset");
        var offline=UUID.randomUUID();var world=h.getLevel().getServer().overworld();var book=new HashMap<>(world.getAttached(Phones.BOOK));book.put(offline.toString(),new Phones.Subscriber("OfflineContact","081999999"));world.setAttached(Phones.BOOK,Map.copyOf(book));
        h.assertTrue(Phones.message(receiver,offline,"Ti scrivo offline")&&world.getAttached(Phones.MAIL).get(offline.toString()).size()==1,"Known offline accounts receive saved messages without a fabricated online connection");
    });h.succeed();}
    private static void with(GameTestHelper h,int count,java.util.function.Consumer<List<ServerPlayer>> body){
        var world=h.getLevel().getServer().overworld();var book=world.getAttached(Phones.BOOK);var mail=world.getAttached(Phones.MAIL);
        try{HousingCapacityGameTest.withPlayers(h,count,ps->{try{for(var p:ps){p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.getInventory().setItem(0,new ItemStack(Phones.ITEMS.get(PhoneModel.ONEPLUS_16)));Phones.account(p);}body.accept(ps);}finally{for(var p:ps)Phones.end(p,Component.empty());}});}
        finally{if(book==null)world.removeAttached(Phones.BOOK);else world.setAttached(Phones.BOOK,book);if(mail==null)world.removeAttached(Phones.MAIL);else world.setAttached(Phones.MAIL,mail);}
    }
}
