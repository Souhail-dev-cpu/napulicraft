package dev.napuli;

import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public final class HouseGuestsGameTest {
    @GameTest(maxTicks = 200) public void invitedFriendsWaitWhenFiveResidentsAreInside(GameTestHelper helper) {
        loadedHouse(helper, house -> HousingCapacityGameTest.withPlayers(helper, 3, players -> state(helper, () -> {
            var city = helper.getLevel(); var owner = players.get(0); var friend = players.get(1); var stranger = players.get(2);
            city.setAttached(NapoliProperties.OWNERS, Map.of(house.id(), owner.getUUID()));
            var npcs = new ArrayList<NapoliResident>();
            try {
                for (int i = 0; i < 4; i++) {
                    var npc = NapoliResident.spawn(city, NapoliResident.Role.CIVIC_MAN, house.inside(), 0);
                    npc.setNoAi(true); npcs.add(npc);
                }
                owner.snapTo(house.inside().getX()+.5,64,house.inside().getZ()+.5,0,0);
                for (var p : List.of(friend,stranger)) p.snapTo(house.entrance().getX()+.5,64,house.entrance().getZ()+1.5,0,0);
                HousingCapacity.enforce(city);
                helper.assertTrue(!HousingCapacity.canEnter(city,house.inside(),friend.getUUID()),"A full house initially rejects an uninvited friend");
                helper.assertTrue(!HouseGuests.grant(city,house.id(),stranger.getUUID(),friend),"A stranger cannot authorize an invitation");
                helper.assertTrue(HouseGuests.grant(city,house.id(),owner.getUUID(),friend),"The owner can invite a friend into a full house");
                helper.assertTrue(!HousingCapacity.canEnter(city,house.inside(),friend.getUUID()),"An invited friend must also wait for room");
                helper.assertTrue(NapoliProperties.hasAccess(friend,house)&&!NapoliProperties.owns(friend,house),"An invitation grants access without ownership");
                helper.assertTrue(!HouseGuests.grant(city,house.id(),friend.getUUID(),stranger),"Guests cannot invite other people");
                friend.snapTo(house.inside().getX()+.5,64,house.inside().getZ()+.5,0,0);
                stranger.snapTo(house.inside().getX()+.5,64,house.inside().getZ()+.5,0,0);
                HousingCapacity.enforce(city);
                helper.assertTrue(!house.contains(friend.blockPosition())&&house.contains(owner.blockPosition())&&npcs.stream().allMatch(n->house.contains(n.blockPosition())),"The five existing occupants keep their places");
                helper.assertTrue(!house.contains(stranger.blockPosition()),"The exception does not admit uninvited strangers");
                var visitor=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_WOMAN,house.inside(),0);visitor.setNoAi(true);npcs.add(visitor);
                helper.assertTrue(HouseGuests.grant(city,house.id(),owner.getUUID(),visitor),"NPCs can also be invited as guests");
                HousingCapacity.enforce(city);
                helper.assertTrue(!house.contains(visitor.blockPosition())&&!house.contains(friend.blockPosition()),"Invited NPCs and friends wait when there are five occupants");
                var family=new ArrayList<NapoliCivicLife.Citizen>();
                for(int i=0;i<4;i++) family.add(new NapoliCivicLife.Citizen(npcs.get(i).getUUID(),"Resident "+i,false,0,0,-1,"",house.id(),"",true));
                helper.assertTrue(HousingCapacity.reservedOccupants(city,house,family)==5,"Authorized visits do not consume family capacity or duplicate residents");
                helper.assertTrue(HouseGuests.revoke(city,house.id(),owner.getUUID(),friend.getUUID()),"The host can end one guest's stay");
                HousingCapacity.enforce(city);
                helper.assertTrue(!house.contains(friend.blockPosition())&&!NapoliProperties.hasAccess(friend,house),"Revocation restores access and capacity restrictions");
                HouseGuests.clear(city,house.id());HousingCapacity.enforce(city);
                helper.assertTrue(!house.contains(visitor.blockPosition()),"Ending hospitality restores the normal five-person limit");
            } finally {for(var npc:npcs)npc.discard();}
        })));
    }

    @GameTest public void tenantsCanInviteAndSavedInvitationsAreRevocable(GameTestHelper helper) {
        HousingCapacityGameTest.withPlayers(helper,3,players->state(helper,()->{
            var city=helper.getLevel();var house=NapoliMergellina.PROPERTIES.get(8);var tenant=players.get(0);var guest=players.get(1);var stranger=players.get(2);
            city.setAttached(NapoliProperties.OWNERS,Map.of(house.id(),CityHousingFinance.LANDLORD));
            city.setAttached(CityHousingFinance.ACCOUNTS,Map.of(house.id(),new CityHousingFinance.Account(tenant.getUUID(),0,10,-1,0,0,0,true)));
            helper.assertTrue(HouseGuests.grant(city,house.id(),tenant.getUUID(),guest),"The current tenant may host a friend");
            var invitation=city.getAttached(HouseGuests.INVITATIONS).get(house.id()).getFirst();
            var encoded=HouseGuests.Invitation.CODEC.encodeStart(JsonOps.INSTANCE,invitation).getOrThrow();
            var decoded=HouseGuests.Invitation.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
            city.setAttached(HouseGuests.INVITATIONS,Map.of(house.id(),List.of(decoded)));
            helper.assertTrue(HouseGuests.invited(city,house.id(),guest.getUUID()),"A reloaded invitation retains access permission");
            helper.assertTrue(!HouseGuests.revoke(city,house.id(),stranger.getUUID(),guest.getUUID()),"A non-host cannot revoke someone else's invitation");
            CityHousingFinance.inherit(city,tenant.getUUID(),null);
            helper.assertTrue(!HouseGuests.invited(city,house.id(),guest.getUUID()),"Ending the tenancy also ends its guest permissions");
        }));helper.succeed();
    }

    @GameTest public void invitationsAreScopedToOneHomeAndDoNotIncreaseResidentCapacity(GameTestHelper helper) {
        HousingCapacityGameTest.withPlayers(helper,3,players->state(helper,()->{
            var city=helper.getLevel();var house=NapoliMergellina.PROPERTIES.get(9);var other=NapoliMergellina.PROPERTIES.get(10);
            var owner=players.get(0);var guest=players.get(1);var buyer=players.get(2);
            city.setAttached(NapoliProperties.OWNERS,Map.of(house.id(),owner.getUUID(),other.id(),buyer.getUUID()));
            HouseGuests.grant(city,house.id(),owner.getUUID(),guest);
            helper.assertTrue(!HouseGuests.invited(city,other.id(),guest.getUUID()),"An invitation cannot be reused for another home");
            city.setAttached(NapoliProperties.OWNERS,Map.of(house.id(),buyer.getUUID(),other.id(),buyer.getUUID()));
            CityHousingFinance.ownOutright(city,house.id(),buyer.getUUID());
            helper.assertTrue(!HouseGuests.invited(city,house.id(),guest.getUUID()),"A change of ownership cancels the old owner's invitations");
            HouseGuests.grant(city,house.id(),buyer.getUUID(),guest);
            var resident=new NapoliCivicLife.Citizen(guest.getUUID(),"Permanent resident",false,0,0,-1,"",house.id(),"",true);
            city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(0,List.of(resident)));
            helper.assertTrue(!HouseGuests.capacityExempt(city,house.id(),guest.getUUID()),"Inviting a permanent resident cannot bypass the five-resident rule");
        }));helper.succeed();
    }

    private static void loadedHouse(GameTestHelper helper,Consumer<NapoliMergellina.House> body) {
        var city=helper.getLevel();var house=NapoliMergellina.PROPERTIES.get(7);
        var chunk=new ChunkPos(Math.floorDiv(house.inside().getX(),16),Math.floorDiv(house.inside().getZ(),16));
        boolean forced=city.setChunkForced(chunk.x(),chunk.z(),true);city.getChunkAt(house.inside());
        for(var pos:BlockPos.betweenClosed(house.x()-2,63,house.z()-2,house.x()+house.width()+2,63,house.z()+house.depth()+3))city.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        helper.runBeforeTestEnd(()->{if(forced)city.setChunkForced(chunk.x(),chunk.z(),false);});
        helper.startSequence()
                .thenWaitUntil(()->helper.assertTrue(city.areEntitiesActuallyLoadedAndTicking(chunk),"The house chunk must have visible, ticking entities"))
                .thenExecute(()->body.accept(house)).thenSucceed();
    }

    private static void state(GameTestHelper helper,Runnable body) {
        var city=helper.getLevel();var owners=city.removeAttached(NapoliProperties.OWNERS);var accounts=city.removeAttached(CityHousingFinance.ACCOUNTS);
        var invitations=city.removeAttached(HouseGuests.INVITATIONS);var census=city.removeAttached(NapoliCivicLife.CENSUS);
        try {body.run();} finally {
            if(owners==null)city.removeAttached(NapoliProperties.OWNERS);else city.setAttached(NapoliProperties.OWNERS,owners);
            if(accounts==null)city.removeAttached(CityHousingFinance.ACCOUNTS);else city.setAttached(CityHousingFinance.ACCOUNTS,accounts);
            if(invitations==null)city.removeAttached(HouseGuests.INVITATIONS);else city.setAttached(HouseGuests.INVITATIONS,invitations);
            if(census==null)city.removeAttached(NapoliCivicLife.CENSUS);else city.setAttached(NapoliCivicLife.CENSUS,census);
        }
    }
}
