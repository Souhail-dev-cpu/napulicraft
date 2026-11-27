package dev.napuli;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

public final class PhoneServicesGameTest {
    @GameTest public void pizzaClockAndBookingsSurviveSaving(GameTestHelper h){
        h.assertTrue(PhoneServices.PIZZA_DELAY==334,"Twenty game minutes take 334 clock ticks, rather than twenty real minutes");
        var pizza=new PhoneServices.Pizza(UUID.randomUUID(),24334);
        var json=PhoneServices.Pizza.CODEC.encodeStart(JsonOps.INSTANCE,pizza).getOrThrow();
        h.assertTrue(PhoneServices.Pizza.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow().equals(pizza),"Pizza identity and midnight delivery deadline survive restart");
        var taxi=new PhoneServices.Taxi(UUID.randomUUID(),BlockPos.ZERO,new BlockPos(160,64,0),"Porto",8,Long.MAX_VALUE,true);
        var saved=PhoneServices.Taxi.CODEC.encodeStart(JsonOps.INSTANCE,taxi).getOrThrow();
        h.assertTrue(PhoneServices.Taxi.CODEC.parse(JsonOps.INSTANCE,saved).getOrThrow().equals(taxi),"A resumed taxi retains its prepaid fare and destination");h.succeed();
    }
    @GameTest public void pizzaPaymentRequiresTheOwnerDueTimeMoneyAndInventorySpace(GameTestHelper h){
        HousingCapacityGameTest.withPlayers(h,2,ps->{var p=ps.getFirst();var other=ps.getLast();var city=h.getLevel();
            var at=h.absolutePos(new BlockPos(1,100,1));p.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);other.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
            var npc=NapoliResident.spawn(city,NapoliResident.Role.SHIFT_WORKER,at.east(),0);npc.addTag(PhoneServices.COURIER);npc.addTag("napuli_service_owner:"+p.getUUID());
            try{
                p.getInventory().clearContent();NapoliStreet.credit(p,50-NapoliStreet.cash(p));int money=NapoliStreet.cash(p);long now=city.getOverworldClockTime();
                var order=new PhoneServices.Pizza(npc.getUUID(),now+1);p.setAttached(PhoneServices.PIZZA,order);
                h.assertTrue(!PhoneServices.payPizza(p,npc)&&NapoliStreet.cash(p)==money,"Early collection never charges the customer");
                p.setAttached(PhoneServices.PIZZA,new PhoneServices.Pizza(npc.getUUID(),now));other.setAttached(PhoneServices.PIZZA,new PhoneServices.Pizza(npc.getUUID(),now));
                h.assertTrue(!PhoneServices.payPizza(other,npc),"Another player cannot collect or pay for this order");
                for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
                h.assertTrue(!PhoneServices.payPizza(p,npc)&&NapoliStreet.cash(p)==money&&p.hasAttached(PhoneServices.PIZZA),"A full inventory preserves both order and wallet");
                p.getInventory().setItem(0,ItemStack.EMPTY);NapoliStreet.credit(p,-NapoliStreet.cash(p));
                h.assertTrue(!PhoneServices.payPizza(p,npc)&&p.hasAttached(PhoneServices.PIZZA),"Insufficient money leaves the pizza with its courier");
                NapoliStreet.credit(p,money);
                h.assertTrue(PhoneServices.payPizza(p,npc)&&WorkResources.count(p,Napuli.PIZZA)==1&&NapoliStreet.cash(p)==money-PhoneServices.PIZZA_PRICE,"Payment and delivery exchange exactly one pizza for the quoted fare");
                h.assertTrue(!PhoneServices.payPizza(p,npc)&&WorkResources.count(p,Napuli.PIZZA)==1,"Repeating the interaction cannot duplicate a pizza or payment");
                h.assertTrue(!npc.shouldBeSaved(),"A courier is recreated from the saved order instead of being duplicated in chunks");
            }finally{npc.discard();}
        });h.succeed();
    }
    @GameTest public void sleepingRespectsOwnershipInvitationsAndRevokedPermissions(GameTestHelper h){
        var city=h.getLevel();var home=NapoliMergellina.PROPERTIES.getFirst();var host=UUID.randomUUID();var guest=UUID.randomUUID();var bed=home.inside();
        var owners=city.getAttached(NapoliProperties.OWNERS);var invitations=city.getAttached(HouseGuests.INVITATIONS);var grants=city.getAttached(HomePermissions.GRANTS);
        try{
            var all=new HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of()));all.put(home.id(),host);city.setAttached(NapoliProperties.OWNERS,Map.copyOf(all));
            h.assertTrue(CityProperty.sleepAllowed(city,host,bed,false)&&!CityProperty.sleepAllowed(city,guest,bed,false),"An owner may sleep at home, a stranger may not");
            var invites=new HashMap<>(city.getAttachedOrElse(HouseGuests.INVITATIONS,Map.of()));invites.put(home.id(),List.of(new HouseGuests.Invitation(host,guest,"Guest")));city.setAttached(HouseGuests.INVITATIONS,Map.copyOf(invites));
            HomePermissions.put(city,home.id(),guest,new HomePermissions.Grant(host,"roommate",true,true,true));
            h.assertTrue(CityProperty.sleepAllowed(city,guest,bed,false),"An invited roommate with household permissions may sleep");
            HomePermissions.put(city,home.id(),guest,new HomePermissions.Grant(host,"guest",true,false,false));
            h.assertTrue(!CityProperty.sleepAllowed(city,guest,bed,false),"Door access alone does not grant permission to use the beds");
            h.assertTrue(!CityProperty.sleepAllowed(city,guest,new BlockPos(-65,64,-55),false),"Legacy houses are private too");
            h.assertTrue(CityProperty.sleepAllowed(city,guest,new BlockPos(2800,80,2800),false),"A bed outside a private city home remains legal");
        }finally{restore(city,NapoliProperties.OWNERS,owners);restore(city,HouseGuests.INVITATIONS,invitations);restore(city,HomePermissions.GRANTS,grants);}h.succeed();
    }
    @GameTest(structure="napuli:police_arena",maxTicks=180) public void aTaxiActuallyDrivesAndNeverChargesTwice(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,0,z,Blocks.STONE);for(int y=1;y<=5;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var p=player(h);var city=h.getLevel();var start=h.absolutePos(new BlockPos(3,1,3));var goal=h.absolutePos(new BlockPos(13,1,13));
        p.snapTo(start.getX()+.5,start.getY(),start.getZ()+.5,0,0);NapoliStreet.credit(p,100-NapoliStreet.cash(p));
        var vehicle=Napuli.CITY_VEHICLE.create(city,EntitySpawnReason.EVENT);var id=UUID.randomUUID();vehicle.setUUID(id);vehicle.configureTaxi(id,p.getUUID());vehicle.snapTo(p.getX(),p.getY(),p.getZ(),0,0);city.addFreshEntity(vehicle);
        p.setAttached(PhoneServices.TAXI,new PhoneServices.Taxi(id,start,goal,"Arrivo",7,city.getGameTime()+2400,false));
        h.assertTrue(PhoneServices.boardTaxi(p,vehicle)&&NapoliStreet.cash(p)==93,"Boarding charges the quoted fare once");
        p.stopRiding();h.assertTrue(PhoneServices.boardTaxi(p,vehicle)&&NapoliStreet.cash(p)==93,"Getting back in continues the prepaid journey");
        h.runBeforeTestEnd(()->vehicle.discard());
        h.runAtTickTime(140,()->{
            h.assertTrue(!p.hasAttached(PhoneServices.TAXI)&&!p.isPassenger(),"The taxi drives to its destination and lets the passenger out");
            h.assertTrue(start.distToCenterSqr(p.position())>9&&goal.distToCenterSqr(p.position())<12*12,"Arrival follows physical vehicle movement");
            h.assertTrue(NapoliStreet.cash(p)==93,"Arrival never charges a second fare");h.succeed();
        });
    }
    @GameTest public void batchesAndExtraMinutesIncreaseWorkAndPay(GameTestHelper h){
        for(var c:NapoliEmployment.Contract.values())h.assertTrue(c.quantity()>=3,"Each job requires a batch, repeated deliveries or sustained cleaning");
        h.assertTrue(NapoliEmployment.Contract.CARPENTER.quantity()==16,"A carpenter supplies four logs' worth of boards");
        h.assertTrue(NapoliEmployment.shiftPay(1500,10,false)==15&&NapoliEmployment.shiftPay(3000,10,false)==30,"Extra minutes and extra hours both increase the wage");
        HousingCapacityGameTest.withPlayers(h,1,ps->{var p=ps.getFirst();var c=NapoliEmployment.Contract.CARPENTER;p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(Items.OAK_PLANKS,4));
            h.assertTrue(WorkResources.target(p,c)!=null,"A single processed log no longer completes the carpenter's task");
            p.getInventory().add(new ItemStack(Items.OAK_PLANKS,12));h.assertTrue(WorkResources.target(p,c)==null,"Guidance advances to delivery only after the entire batch is ready");
        });h.succeed();
    }
    @GameTest(structure="napuli:police_arena") public void endingAPursuitRestoresPatrollingOutsideTheOldHome(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,0,z,Blocks.STONE);for(int y=1;y<4;y++)h.setBlock(x,y,z,Blocks.AIR);}
        HousingCapacityGameTest.withPlayers(h,1,ps->{var p=ps.getFirst();var at=h.absolutePos(new BlockPos(5,1,5));var npc=NapoliResident.spawn(h.getLevel(),NapoliResident.Role.CARABINIERE,at,0);
            try{npc.setOnGround(true);npc.setHomeTo(at.east(80),10);p.snapTo(at.getX()+5,at.getY(),at.getZ(),0,0);PoliceTactics.pursue(npc,p,h.getLevel().getGameTime());PoliceTactics.stop(npc);
                h.assertTrue(!PoliceTactics.active(npc)&&npc.isWithinHome(npc.position()),"An escaped suspect releases the chase and restores a reachable patrol area");
                h.assertTrue(!npc.getNavigation().isDone(),"The officer immediately starts a patrol path");
            }finally{npc.discard();}
        });h.succeed();
    }
    private static <T> void restore(ServerLevel city,net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> key,T value){if(value==null)city.removeAttached(key);else city.setAttached(key,value);}
    private static ServerPlayer player(GameTestHelper h){
        var city=h.getLevel();var server=city.getServer();var profile=new GameProfile(UUID.randomUUID(),"TaxiTest");var p=new ServerPlayer(server,city,profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);server.getPlayerList().placeNewPlayer(connection,p,CommonListenerCookie.createInitial(profile,false));
        h.runBeforeTestEnd(()->{server.getPlayerList().remove(p);channel.finishAndReleaseAll();});return p;
    }
}
