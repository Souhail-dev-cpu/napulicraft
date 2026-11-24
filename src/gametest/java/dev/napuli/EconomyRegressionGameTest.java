package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;

public final class EconomyRegressionGameTest {
    @GameTest public void simultaneousParentsDeathsPreserveBothInheritancesInEitherOrder(GameTestHelper h) {
        var city=h.getLevel();
        var life=city.getAttachedOrElse(CitySimulation.LIFE,Map.of());
        var owners=city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of());
        var accounts=city.getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of());
        var events=city.getAttachedOrElse(CityRelationships.EVENTS,Map.of());
        try {
            for(boolean reverse:List.of(false,true)) {
                long day=NapoliBroadcast.day(city);
                var father=UUID.randomUUID();var mother=UUID.randomUUID();var child=UUID.randomUUID();
                long birth=day-30L*CityLifeConfig.settings.daysPerYear;
                var a=new NapoliCivicLife.Citizen(father,"Parent A",false,birth,10,-1,mother.toString(),"","",false);
                var b=new NapoliCivicLife.Citizen(mother,"Parent B",true,birth,20,-1,father.toString(),"","",false);
                var people=new LinkedHashMap<UUID,NapoliCivicLife.Citizen>();
                for(var parent:reverse?List.of(b,a):List.of(a,b))people.put(parent.id(),parent);
                people.put(child,new NapoliCivicLife.Citizen(child,"Child",false,day,7,-1,"","",father.toString(),false));
                CitySimulation.parentage(city,child,father,mother);
                for(var id:List.of(father,mother)) {
                    var state=CitySimulation.life(city,id);
                    CitySimulation.put(city,id,state.career(0,state.reputation(),0,0,0,List.of()));
                }
                CitySimulation.inherit(city,people,day);
                h.assertTrue(people.size()==1&&people.get(child).balance()==37,
                        "The surviving child retains seven euros and inherits both estates in either processing order");
                CitySimulation.inherit(city,people,day);
                h.assertTrue(people.get(child).balance()==37,"Settled estates cannot be paid again");
            }
        } finally {
            city.setAttached(CitySimulation.LIFE,life);city.setAttached(NapoliProperties.OWNERS,owners);
            city.setAttached(CityHousingFinance.ACCOUNTS,accounts);city.setAttached(CityRelationships.EVENTS,events);
        }
        h.succeed();
    }

    @GameTest public void mortgageSaleQuoteAndPaymentSettleOverduePrincipalOnlyOnce(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,1,players->{
            var p=players.getFirst();var city=p.level();var house=NapoliMergellina.PROPERTIES.getFirst();
            var owners=city.getAttachedOrElse(NapoliProperties.OWNERS,Map.of());
            var accounts=city.getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of());
            var invitations=city.getAttachedOrElse(HouseGuests.INVITATIONS,Map.of());
            var grants=city.getAttachedOrElse(HomePermissions.GRANTS,Map.of());
            var terms=CompanionHousing.terms(house,false);
            try {
                var at=house.sign();p.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
                for(int missed:new int[]{0,1,2,13})for(boolean payFirst:List.of(false,true)) {
                    int principalDue=Math.min(terms.principal(),terms.installment()*missed);
                    var account=new CityHousingFinance.Account(p.getUUID(),terms.principal(),terms.installment(),12,
                            principalDue+2*missed,missed,7L*missed,false);
                    city.setAttached(NapoliProperties.OWNERS,Map.of(house.id(),p.getUUID()));
                    city.setAttached(CityHousingFinance.ACCOUNTS,Map.of(house.id(),account));
                    NapoliStreet.credit(p,1000-NapoliStreet.cash(p));
                    int expected=1000+house.price()*80/100-terms.principal()-2*missed;
                    if(payFirst&&missed>0)h.assertTrue(CityHousingFinance.pay(p,house)==1,"Overdue installments can be paid before selling");
                    int quoted=-CompanionHousing.charge(p,house,"vendi");
                    int before=NapoliStreet.cash(p);
                    h.assertTrue(before+quoted==expected,"The quote charges the remaining debt and utility arrears exactly once");
                    h.assertTrue(CityHousingFinance.sell(p,house.id())==1&&NapoliStreet.cash(p)==expected,
                            "Actual proceeds equal the quote whether arrears were paid before selling or settled with the sale");
                    h.assertTrue(!city.getAttachedOrElse(CityHousingFinance.ACCOUNTS,Map.of()).containsKey(house.id()),
                            "The sale closes the mortgage account");
                }
            } finally {
                city.setAttached(NapoliProperties.OWNERS,owners);city.setAttached(CityHousingFinance.ACCOUNTS,accounts);
                city.setAttached(HouseGuests.INVITATIONS,invitations);city.setAttached(HomePermissions.GRANTS,grants);
            }
        });
        h.succeed();
    }

    @GameTest public void saleKeepsUtilityChargesAndCapsTheLastPrincipalInstallment(GameTestHelper h) {
        var house=NapoliMergellina.PROPERTIES.getFirst();int value=house.price()*80/100;
        h.assertTrue(CityHousingFinance.saleProceeds(house,null)==value,"An outright sale without an account keeps its normal price");
        var owner=UUID.randomUUID();
        var paid=new CityHousingFinance.Account(owner,0,0,0,6,3,21,false);
        h.assertTrue(CityHousingFinance.saleProceeds(house,paid)==value-6,"A repaid mortgage still owes its utility charges");
        var last=new CityHousingFinance.Account(owner,2,5,1,4,1,7,false);
        h.assertTrue(CityHousingFinance.saleProceeds(house,last)==value-4,"The last principal installment is capped to the two-euro residual debt");
        h.succeed();
    }

    @GameTest public void workConversionsUseStorageFreedByConsumedIngredients(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,1,players->{
            var p=players.getFirst();
            for(var job:List.of(NapoliEmployment.Contract.CARPENTER,NapoliEmployment.Contract.BAKER)) {
                start(p,job);fill(p);
                Item input=job==NapoliEmployment.Contract.CARPENTER?Items.OAK_LOG:Items.WHEAT;
                Item output=job==NapoliEmployment.Contract.CARPENTER?Items.OAK_PLANKS:Items.BREAD;
                int required=job==NapoliEmployment.Contract.CARPENTER?1:3;
                int produced=job==NapoliEmployment.Contract.CARPENTER?4:1;
                p.getInventory().setItem(0,new ItemStack(input,required));
                h.assertTrue(WorkResources.processPickup(p)==1,"The conversion can reuse the input slot in a full inventory");
                h.assertTrue(p.getInventory().countItem(input)==0&&p.getInventory().countItem(output)==produced,
                        "Exactly the required input becomes the expected output");
                h.assertTrue(p.getInventory().countItem(Items.STONE)==35*64,"All unrelated stacks remain intact");
            }
        });h.succeed();
    }

    @GameTest public void rejectedWorkConversionsDoNotConsumeOrPartiallyInsertAnything(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,1,players->{
            var p=players.getFirst();start(p,NapoliEmployment.Contract.CARPENTER);fill(p);
            p.getInventory().setItem(0,new ItemStack(Items.OAK_LOG,2));
            p.getInventory().setItem(1,new ItemStack(Items.OAK_PLANKS,63));
            var before=PlayerInventoryPlan.copy(p);
            h.assertTrue(WorkResources.processPickup(p)==0,"One free place in an existing stack cannot hold four new planks");
            unchanged(h,p,before);
            start(p,NapoliEmployment.Contract.BAKER);fill(p);
            p.getInventory().setItem(0,new ItemStack(Items.WHEAT,2));
            p.getInventory().setItem(1,ItemStack.EMPTY);before=PlayerInventoryPlan.copy(p);
            h.assertTrue(WorkResources.processPickup(p)==0,"Insufficient ingredients reject the complete conversion");
            unchanged(h,p,before);
        });h.succeed();
    }

    @GameTest public void offhandInputsRemainUsableWithoutTreatingTheOffhandAsOutputSpace(GameTestHelper h) {
        HousingCapacityGameTest.withPlayers(h,1,players->{
            var p=players.getFirst();start(p,NapoliEmployment.Contract.CARPENTER);fill(p);
            p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.OAK_LOG));
            h.assertTrue(WorkResources.processPickup(p)==0&&p.getOffhandItem().getCount()==1,
                    "Consuming an offhand input must not create storage space in the main inventory");
            p.getInventory().setItem(0,ItemStack.EMPTY);
            h.assertTrue(WorkResources.processPickup(p)==1&&p.getOffhandItem().isEmpty()
                    &&p.getInventory().countItem(Items.OAK_PLANKS)==4,"Offhand ingredients are consumed when real output storage is available");
        });h.succeed();
    }

    private static void start(ServerPlayer p,NapoliEmployment.Contract job) {
        var at=WorkResources.bench();p.snapTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        p.setAttached(NapoliEmployment.WORK,new NapoliEmployment.Work(job.id(),NapoliBroadcast.day(p.level()),1,-1));
    }
    private static void fill(ServerPlayer p) {
        p.getInventory().clearContent();
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
    }
    private static void unchanged(GameTestHelper h,ServerPlayer p,List<ItemStack> before) {
        for(int i=0;i<before.size();i++) {
            var actual=p.getInventory().getItem(i);var expected=before.get(i);
            h.assertTrue(actual.getCount()==expected.getCount()&&ItemStack.isSameItemSameComponents(actual,expected),
                    "A rejected conversion preserves every original inventory stack");
        }
    }
}
