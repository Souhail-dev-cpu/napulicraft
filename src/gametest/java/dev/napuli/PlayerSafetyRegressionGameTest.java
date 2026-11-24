package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerSafetyRegressionGameTest {
    @GameTest public void losingStoragePermissionClosesTheOpenContainerImmediately(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,2,players->{
            try(var fixture=new HomeFixture(h,players)) {
                var guest=players.get(1);fixture.invite(guest);
                HomePermissions.put(fixture.city,fixture.home.id(),guest.getUUID(),new HomePermissions.Grant(players.getFirst().getUUID(),"roommate",true,true,true));
                fixture.open(guest);
                HomePermissions.put(fixture.city,fixture.home.id(),guest.getUUID(),new HomePermissions.Grant(players.getFirst().getUUID(),"roommate",true,true,false));
                h.assertTrue(!HomePermissions.allows(guest,fixture.home,HomePermissions.Capability.STORAGE),"The new grant denies storage");
                h.assertTrue(guest.containerMenu==guest.inventoryMenu,"The old container id becomes invalid before another click can run");
                h.assertTrue(fixture.barrel.getItem(0).getCount()==3&&guest.inventoryMenu.getCarried().isEmpty(),"Revocation leaves all protected diamonds in their container");
            }
        });h.succeed();
    }

    @GameTest public void revokingALegacyInvitationClosesItsOpenContainer(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,2,players->{
            try(var fixture=new HomeFixture(h,players)) {
                var guest=players.get(1);fixture.invite(guest);
                fixture.city.setAttached(HomePermissions.GRANTS,Map.of());
                h.assertTrue(HomePermissions.allows(guest,fixture.home,HomePermissions.Capability.STORAGE),"A legacy invitation has its original rights");
                fixture.open(guest);
                h.assertTrue(HouseGuests.revoke(fixture.city,fixture.home.id(),players.getFirst().getUUID(),guest.getUUID()),"The owner revokes the actual invitation");
                h.assertTrue(guest.containerMenu==guest.inventoryMenu&&!HouseGuests.invited(fixture.city,fixture.home.id(),guest.getUUID()),"Legacy grants cannot keep a revoked storage session open");
            }
        });h.succeed();
    }

    @GameTest public void clearingHouseAccessClosesAffectedSessionsAndLeavesOtherPlayersAlone(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,3,players->{
            try(var fixture=new HomeFixture(h,players)) {
                var owner=players.getFirst();var guest=players.get(1);var stranger=players.get(2);fixture.invite(guest);
                HomePermissions.put(fixture.city,fixture.home.id(),guest.getUUID(),new HomePermissions.Grant(owner.getUUID(),"roommate",true,true,true));
                fixture.open(owner);fixture.open(guest);
                stranger.openMenu(new SimpleMenuProvider((id,inventory,player)->ChestMenu.threeRows(id,inventory,new SimpleContainer(27)),Component.literal("Public storage")));
                var publicMenu=stranger.containerMenu;
                HouseGuests.clear(fixture.city,fixture.home.id());
                h.assertTrue(owner.containerMenu==owner.inventoryMenu&&guest.containerMenu==guest.inventoryMenu,"Ownership changes close the old owner's and guests' sessions before rights are cleared");
                h.assertTrue(stranger.containerMenu==publicMenu&&publicMenu!=stranger.inventoryMenu,"An unrelated player's inventory session stays open");
            }
        });h.succeed();
    }

    @GameTest public void addingPermissionKeepsAnAlreadyAuthorizedContainerOpen(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,2,players->{
            try(var fixture=new HomeFixture(h,players)) {
                var guest=players.get(1);fixture.invite(guest);
                var owner=players.getFirst().getUUID();
                HomePermissions.put(fixture.city,fixture.home.id(),guest.getUUID(),new HomePermissions.Grant(owner,"roommate",true,false,true));
                fixture.open(guest);var menu=guest.containerMenu;
                HomePermissions.put(fixture.city,fixture.home.id(),guest.getUUID(),new HomePermissions.Grant(owner,"roommate",true,true,true));
                h.assertTrue(guest.containerMenu==menu,"Adding appliance rights does not interrupt an authorized storage session");
            }
        });h.succeed();
    }

    @GameTest public void houseSalesRejectWalletOverflowWithoutChangingOwnershipOrAccounts(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,1,players->{
            var p=players.getFirst();var city=p.level();var home=NapoliMergellina.PROPERTIES.getFirst();
            var owners=city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of());var accounts=city.getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of());
            var grants=city.getAttachedOrElse(HomePermissions.GRANTS,Map.of());var invites=city.getAttachedOrElse(HouseGuests.INVITATIONS,Map.of());
            try {
                int price=home.price()*80/100;var at=home.sign();p.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
                for(int room:new int[]{0,price-1,price}) {
                    city.setAttached(NapoliProperties.OWNERS,Map.of(home.id(),p.getUUID()));CityHousingFinance.ownOutright(city,home.id(),p.getUUID());
                    var account=city.getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of()).get(home.id());
                    cash(p,NapoliStreet.MAX_CASH-room);var history=p.getAttachedOrElse(PlayerLedger.HISTORY,List.of());
                    int result=CityHousingFinance.sell(p,home.id());
                    if(room<price) {
                        h.assertTrue(result==0&&p.getUUID().equals(NapoliProperties.owner(city,home)),"An overflowing sale keeps the house owned by the seller");
                        h.assertTrue(account.equals(city.getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of()).get(home.id()))&&p.getAttachedOrElse(PlayerLedger.HISTORY,List.of()).equals(history),"Rejected sales preserve the account and wallet history");
                    } else h.assertTrue(result==1&&NapoliStreet.cash(p)==NapoliStreet.MAX_CASH&&NapoliProperties.owner(city,home)==null,"An exact-fit sale transfers ownership and credits every euro");
                }
            } finally {city.setAttached(NapoliProperties.OWNERS,owners);city.setAttached(CityHousingFinance.ACCOUNTS,accounts);city.setAttached(HomePermissions.GRANTS,grants);city.setAttached(HouseGuests.INVITATIONS,invites);}
        });h.succeed();
    }

    @GameTest public void wagesPreserveTheFundUntilTheWholePaymentFits(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,1,players->{
            var p=players.getFirst();var city=p.level();var saved=city.getAttached(WorkEconomy.FUND);
            try {
                city.setAttached(WorkEconomy.FUND,100);
                for(int room:new int[]{0,12}) {
                    cash(p,NapoliStreet.MAX_CASH-room);var history=p.getAttachedOrElse(PlayerLedger.HISTORY,List.of());
                    h.assertTrue(!WorkEconomy.wage(p,13)&&WorkEconomy.fund(city)==100&&NapoliStreet.cash(p)==NapoliStreet.MAX_CASH-room,"A rejected wage changes neither the fund nor the wallet");
                    h.assertTrue(p.getAttachedOrElse(PlayerLedger.HISTORY,List.of()).equals(history),"Rejected wages do not record fictitious income");
                }
                cash(p,NapoliStreet.MAX_CASH-13);
                h.assertTrue(WorkEconomy.wage(p,13)&&NapoliStreet.cash(p)==NapoliStreet.MAX_CASH&&WorkEconomy.fund(city)==87,"Retrying with enough capacity pays the entire original wage");
                cash(p,100);city.setAttached(WorkEconomy.FUND,12);
                h.assertTrue(!WorkEconomy.wage(p,13)&&NapoliStreet.cash(p)==100&&WorkEconomy.fund(city)==12,"Insufficient municipal funds still reject payment without side effects");
            } finally {if(saved==null)city.removeAttached(WorkEconomy.FUND);else city.setAttached(WorkEconomy.FUND,saved);}
        });h.succeed();
    }

    private static void cash(ServerPlayer player,int value){NapoliStreet.credit(player,value-NapoliStreet.cash(player));}
    private static final class HomeFixture implements AutoCloseable {
        final ServerLevel city;final List<ServerPlayer> players;final NapoliMergellina.House home=NapoliMergellina.PROPERTIES.getFirst();
        final Map<String,UUID> owners;final Map<String,HomePermissions.Grant> grants;final Map<String,List<HouseGuests.Invitation>> invitations;
        final net.minecraft.core.BlockPos position;final BlockState original;final BarrelBlockEntity barrel;
        HomeFixture(GameTestHelper h,List<ServerPlayer> players) {
            this.players=players;city=h.getLevel();owners=city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of());grants=city.getAttachedOrElse(HomePermissions.GRANTS,Map.of());invitations=city.getAttachedOrElse(HouseGuests.INVITATIONS,Map.of());
            position=home.inside().above(12);city.getChunkAt(position);original=city.getBlockState(position);
            h.assertTrue(city.getBlockEntity(position)==null,"The temporary container cannot overwrite an existing inventory");
            city.setBlock(position,Blocks.BARREL.defaultBlockState(),18);barrel=(BarrelBlockEntity)city.getBlockEntity(position);barrel.setItem(0,new ItemStack(Items.DIAMOND,3));
            var all=new HashMap<>(owners);all.put(home.id(),players.getFirst().getUUID());city.setAttached(NapoliProperties.OWNERS,Map.copyOf(all));
        }
        void invite(ServerPlayer guest){if(!HouseGuests.grant(city,home.id(),players.getFirst().getUUID(),guest))throw new AssertionError("The fixture owner must be able to invite a guest");}
        void open(ServerPlayer player){player.snapTo(position.getX()+.5,position.getY(),position.getZ()+1.5,0,0);if(player.openMenu(barrel).isEmpty())throw new AssertionError("The real barrel menu must open");}
        @Override public void close(){for(var p:players)if(p.containerMenu!=p.inventoryMenu)p.closeContainer();city.setBlock(position,original,18);city.setAttached(NapoliProperties.OWNERS,owners);city.setAttached(HomePermissions.GRANTS,grants);city.setAttached(HouseGuests.INVITATIONS,invitations);}
    }
}
