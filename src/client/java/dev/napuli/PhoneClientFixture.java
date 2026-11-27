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
    public static String peerNumber;
    public static final String PEER="PhonePeer";
    private static ServerPlayer peer;
    private static EmbeddedChannel channel;
    public static volatile int pizzaActor,taxiActor;
    public static volatile boolean servicesReady,pizzaReady,pizzaPaid,policeCalled,taxiBooked,taxiPaid;
    private static int serviceWallet;
    public static void prepare(ServerPlayer p){
        if(!Boolean.getBoolean("napuli.phonetest"))throw new IllegalStateException("Phone fixture disabled");
        p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();int slot=0;
        for(var model:PhoneModel.values())p.getInventory().setItem(slot++,new ItemStack(Phones.ITEMS.get(model)));
        p.getInventory().setItem(5,new ItemStack(Phones.CHARGER));Phones.account(p);
        var server=p.level().getServer();var profile=new GameProfile(UUID.randomUUID(),PEER);peerId=profile.id();peer=new ServerPlayer(server,p.level(),profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);server.getPlayerList().placeNewPlayer(connection,peer,CommonListenerCookie.createInitial(profile,false));
        peer.setGameMode(GameType.CREATIVE);peer.getInventory().clearContent();peer.getInventory().setItem(0,new ItemStack(Phones.ITEMS.get(PhoneModel.IQOO_16)));peerNumber=Phones.account(peer).number();ready=true;
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
    public static void prepareServices(ServerPlayer p){
        p.teleportTo(p.level(),-347.5,64,20.5,Set.of(),0,0,true);NapoliStreet.credit(p,100-NapoliStreet.cash(p));serviceWallet=NapoliStreet.cash(p);servicesReady=true;
    }
    public static void awaitPizza(ServerPlayer p){
        var order=p.getAttached(PhoneServices.PIZZA);check(order!=null,"The phone button creates a pizza order");
        check(NapoliStreet.cash(p)==serviceWallet,"Ordering a pizza does not charge before delivery");
        if(p.level().getOverworldClockTime()<order.due())return;
        var entity=p.level().getEntity(order.id());
        if(entity instanceof NapoliResident npc&&npc.distanceToSqr(p)<=9&&npc.hasLineOfSight(p)){pizzaActor=npc.getId();pizzaReady=true;}
    }
    public static void pizzaPaid(ServerPlayer p){
        check(!p.hasAttached(PhoneServices.PIZZA)&&WorkResources.count(p,Napuli.PIZZA)==1,"A native right-click receives the actual pizza");
        check(NapoliStreet.cash(p)==serviceWallet-PhoneServices.PIZZA_PRICE,"The courier charges exactly the displayed price");pizzaPaid=true;
    }
    public static void policeCalled(ServerPlayer p){
        check(!Carabinieri.isWanted(p),"Calling 112 does not report the caller as a criminal");
        check(Carabinieri.carabinieri(p.level(),p.blockPosition(),32).stream().anyMatch(n->n.entityTags().contains(PhoneServices.RESPONDER)),"The phone call dispatches an actual patrol");policeCalled=true;
    }
    public static void taxiBooked(ServerPlayer p){
        var ride=p.getAttached(PhoneServices.TAXI);check(ride!=null&&!ride.paid(),"The destination button books an unpaid taxi");
        check(NapoliStreet.cash(p)==serviceWallet-PhoneServices.PIZZA_PRICE,"Taxi booking does not charge before boarding");
        var vehicle=p.level().getEntity(ride.id());if(vehicle!=null){taxiActor=vehicle.getId();taxiBooked=true;}
    }
    public static void taxiPaid(ServerPlayer p){
        var ride=p.getAttached(PhoneServices.TAXI);check(ride!=null&&ride.paid()&&p.getVehicle() instanceof CityVehicle,"A native right-click boards the booked taxi");
        check(NapoliStreet.cash(p)==serviceWallet-PhoneServices.PIZZA_PRICE-ride.fare(),"Boarding charges the quoted taxi fare once");PhoneServices.cancel(p,"taxi");privateBedCrime(p);taxiPaid=true;
    }
    private static void privateBedCrime(ServerPlayer p){
        var city=p.level();var home=NapoliMergellina.PROPERTIES.stream().min(Comparator.comparingDouble(h->h.entrance().distToCenterSqr(p.position()))).orElseThrow();var bed=CityLivingHomes.use(home,CityLivingHomes.Furniture.BED).block();city.getChunkAt(bed);
        var original=city.getBlockState(bed);var owner=NapoliProperties.owner(city,home);check(!p.getUUID().equals(owner),"The fixture bed belongs to someone else");
        city.setBlock(bed,net.minecraft.world.level.block.Blocks.BED.blue().defaultBlockState(),2);p.teleportTo(city,bed.getX()+.5,bed.getY(),bed.getZ()+1.5,Set.of(),0,0,true);
        var officer=NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,bed.south(3),0);officer.setNoAi(true);
        try{
            check(city.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock,"The fixture contains a real bed: "+city.getBlockState(bed));
            check(!p.isCreative()&&!p.isSpectator()&&!Carabinieri.isJailed(p),"The unauthorized sleeper is subject to ordinary law");
            check(!CityProperty.sleepAllowed(p,bed),"The sleeper has no permission to use this bed");
            check(officer.hasLineOfSight(p),"The witness can see the unauthorized sleeper");
            check(Carabinieri.carabinieri(city,p.blockPosition(),32).contains(officer),"The witness is indexed in an already loaded city chunk");
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(bed),net.minecraft.core.Direction.UP,bed,false);
            var result=net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p,city,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            check(result==net.minecraft.world.InteractionResult.FAIL&&Carabinieri.isWanted(p),"Using a private bed is reported before the ordinary property handler blocks it: result="+result+", wanted="+Carabinieri.isWanted(p));
        }finally{officer.discard();city.setBlock(bed,original,2);}
    }
    public static void cleanup(ServerPlayer p){Phones.end(p,Component.empty());if(peer!=null){Phones.end(peer,Component.empty());p.level().getServer().getPlayerList().remove(peer);}if(channel!=null)channel.finishAndReleaseAll();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private PhoneClientFixture(){}
}
