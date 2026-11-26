package dev.napuli;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;

/** Embedded peer exercises real server conversations while the graphical client drives its own packets. */
public final class PhoneClientFixture {
    public static volatile boolean ready;
    public static UUID peerId;
    public static final String PEER="PhonePeer";
    private static ServerPlayer peer;
    private static EmbeddedChannel channel;
    public static void prepare(ServerPlayer p){
        if(!Boolean.getBoolean("napuli.phonetest"))throw new IllegalStateException("Phone fixture disabled");
        p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();int slot=0;
        for(var model:PhoneModel.values())p.getInventory().setItem(slot++,new ItemStack(Phones.ITEMS.get(model)));
        p.getInventory().setItem(5,new ItemStack(Phones.CHARGER));Phones.account(p);
        var server=p.level().getServer();var profile=new GameProfile(UUID.randomUUID(),PEER);peerId=profile.id();peer=new ServerPlayer(server,p.level(),profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);server.getPlayerList().placeNewPlayer(connection,peer,CommonListenerCookie.createInitial(profile,false));
        peer.setGameMode(GameType.CREATIVE);peer.getInventory().clearContent();peer.getInventory().setItem(0,new ItemStack(Phones.ITEMS.get(PhoneModel.IQOO_16)));Phones.account(peer);ready=true;
    }
    public static void model(ServerPlayer p,int index){Phones.open(p,PhoneModel.values()[index]);}
    public static void reply(ServerPlayer p){check(Phones.mail(p).size()==1&&Phones.mail(peer).size()==1,"The actual client message reaches both saved mailboxes");check(Phones.message(peer,p.getUUID(),"Ricevuto, ci vediamo a Piazza Dante."),"The other player can reply");}
    public static void answer(ServerPlayer p){var call=Phones.snapshot(peer,false,Component.empty()).call();check(call!=null&&call.incoming()&&!call.active(),"Only the intended peer receives the ringing call");check(Phones.answer(peer,call.id().toString()),"The other player accepts the real client call");
        check(Phones.voiceTarget(p,new PhonePacket.Voice(call.id(),new byte[PhonePacket.Voice.FRAME_BYTES]))==peer,"Accepted microphone frames route to the peer");}
    public static void callBack(ServerPlayer p){check(Phones.snapshot(peer,false,Component.empty()).call()==null,"Hangup cleared the other endpoint");check(Phones.dial(peer,p.getUUID()),"A second player can ring the real client");}
    public static void pin(ServerPlayer p,String destination){check(CityCompanion.goal(p)!=null&&CityCompanion.destinations(p).stream().filter(d->d.id().equals(destination)).anyMatch(d->d.position().equals(CityCompanion.goal(p).position())),"The GPS selects the authoritative destination and walking tracker");}
    public static void emptyBattery(ServerPlayer p){p.setGameMode(GameType.SURVIVAL);Phones.charge(Phones.find(p,PhoneModel.ONEPLUS_15),1000);p.getInventory().setItem(6,new ItemStack(Items.REDSTONE,2));Phones.open(p,PhoneModel.ONEPLUS_15);}
    public static void charged(ServerPlayer p){check(Phones.charge(Phones.find(p,PhoneModel.ONEPLUS_15))==3000&&p.getInventory().countItem(Items.REDSTONE)==1,"The charger adds 2000 mAh and consumes exactly one redstone through real client input");}
    public static void airplane(ServerPlayer p){check(p.getAttachedOrElse(Phones.AIRPLANE,false)&&!Phones.dial(peer,p.getUUID()),"Airplane mode rejects a call to the real client");}
    public static void cleanup(ServerPlayer p){Phones.end(p,Component.empty());if(peer!=null){Phones.end(peer,Component.empty());p.level().getServer().getPlayerList().remove(peer);}if(channel!=null)channel.finishAndReleaseAll();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private PhoneClientFixture(){}
}
