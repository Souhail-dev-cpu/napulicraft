package dev.napuli;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public final class CityBehaviorGameTest {
    @GameTest(maxTicks=200) public void dinnerConsumesIngredientsOnceAndRestoresHungerOnlyAtTable(GameTestHelper helper){
        var city=helper.getLevel();var h=NapoliMergellina.PROPERTIES.get(12);
        var chunk=new ChunkPos(Math.floorDiv(h.inside().getX(),16),Math.floorDiv(h.inside().getZ(),16));
        boolean forced=city.setChunkForced(chunk.x(),chunk.z(),true);city.getChunkAt(h.inside());
        helper.runBeforeTestEnd(()->{if(forced)city.setChunkForced(chunk.x(),chunk.z(),false);});
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(city.areEntitiesActuallyLoadedAndTicking(chunk),"The kitchen must be loaded"))
                .thenExecute(()->{
                    var lives=city.removeAttached(CitySimulation.LIFE);var dinners=city.removeAttached(CityDomestic.DINNERS);
                    var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,h.inside(),0);npc.setNoAi(true);
                    var c=citizen(city,npc.getUUID(),h.id());
                    try{
                        for(var pos:BlockPos.betweenClosed(h.x()-1,63,h.z()-1,h.x()+h.width(),63,h.z()+h.depth()))city.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
                        var fridge=CityDomestic.point(h,0).block();city.setBlock(fridge,Blocks.BARREL.defaultBlockState(),2);
                        var storage=(Container)city.getBlockEntity(fridge);storage.setItem(0,new ItemStack(Items.WHEAT,2));
                        var l=CitySimulation.fresh(c.id());CitySimulation.put(city,c.id(),l.withNeeds(new CitySimulation.Needs(10,80,80,80,80,80),3,0,0,0));
                        npc.snapTo(h.entrance().getX()+.5,64,h.entrance().getZ()+.5,0,0);
                        CityDomestic.tick(npc,city,c,h,NapoliHouseholds.Activity.EAT);
                        helper.assertTrue(storage.getItem(0).getCount()==2&&CityDomestic.dinner(city,c.id()).stage()==0,"Walking toward the fridge does not consume ingredients or complete a stage");
                        for(int stage=0;stage<6;stage++){
                            var point=CityDomestic.point(h,stage).stand();npc.snapTo(point.getX()+.5,64,point.getZ()+.5,0,0);
                            for(int turn=0;turn<3;turn++)CityDomestic.tick(npc,city,c,h,NapoliHouseholds.Activity.EAT);
                            if(stage<4)helper.assertTrue(CitySimulation.life(city,c.id()).needs().hunger()==10,"Preparation and cooking do not count as eating");
                            if(stage==2){var dinner=CityDomestic.dinner(city,c.id());var json=CityDomestic.Dinner.CODEC.encodeStart(JsonOps.INSTANCE,dinner).getOrThrow();CityDomestic.put(city,c.id(),CityDomestic.Dinner.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());}
                        }
                        var after=CitySimulation.life(city,c.id());
                        helper.assertTrue(storage.getItem(0).getCount()==1&&after.food()==3,"One physical ingredient was consumed, without charging the personal pantry too");
                        helper.assertTrue(after.needs().hunger()==45&&after.water()==1&&CityDomestic.dinner(city,c.id()).stage()==-1,"Eating restores hunger; washing completes the meal and consumes water");
                        CityDomestic.tick(npc,city,c,h,NapoliHouseholds.Activity.EAT);
                        helper.assertTrue(storage.getItem(0).getCount()==1,"The same meal cannot be consumed twice during its cooldown");
                        storage.clearContent();CitySimulation.put(city,c.id(),after.withNeeds(after.needs(),1,0,0,0));
                        helper.assertTrue(CityDomestic.ingredient(city,c,h)&&!CityDomestic.ingredient(city,c,h),"An empty fridge can use one reserved ration, but cannot invent a second one");
                    }finally{
                        CityDomestic.release(city,c.id());npc.discard();restore(city,CitySimulation.LIFE,lives);restore(city,CityDomestic.DINNERS,dinners);
                        if(forced)city.setChunkForced(chunk.x(),chunk.z(),false);
                    }
                }).thenSucceed();
    }
    @GameTest public void sharedStationsReleaseAfterTheOccupantLeaves(GameTestHelper helper){
        var city=helper.getLevel();var pos=helper.absolutePos(new BlockPos(1,2,1));
        var a=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,pos,0);var b=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_WOMAN,pos.east(),0);
        try{
            helper.assertTrue(CityDomestic.claim(city,pos,a.getUUID())&&!CityDomestic.claim(city,pos,b.getUUID()),"Two residents cannot occupy a kitchen station simultaneously");
            a.discard();helper.assertTrue(CityDomestic.claim(city,pos,b.getUUID()),"An unloaded or removed resident cannot leave the kitchen locked");
        }finally{CityDomestic.release(city,a.getUUID());CityDomestic.release(city,b.getUUID());a.discard();b.discard();}helper.succeed();
    }
    @GameTest public void productionConservesSuppliesAndEmptyShopCannotCreateMeals(GameTestHelper helper){
        var city=helper.getLevel();var stocks=city.removeAttached(CityEconomy.STOCKS);
        try{
            var start=new CityEconomy.Stock(2,1,0,0,0);var made=CityEconomy.produce(start,1,true);
            helper.assertTrue(made.grain()==0&&made.flour()==0&&made.bread()==3,"Two grain and one flour produce exactly three breads");
            helper.assertTrue(CityEconomy.produce(made,1,true).equals(made),"The same production period cannot run twice");
            helper.assertTrue(CityEconomy.produce(made,2,true).bread()==3,"A later period without raw materials cannot create bread");
            helper.assertTrue(CityEconomy.produce(start,1,false).bread()==0,"A closed or unstaffed shop cannot produce");
            CityEconomy.put(city,"mercato",made);
            helper.assertTrue(CityEconomy.buyMeals(city,"mercato",6,3)==1&&CityEconomy.stock(city,"mercato").bread()==2,"A household pays two euros per actual available meal");
            helper.assertTrue(CityEconomy.buyMeals(city,"mercato",6,100)==2&&CityEconomy.buyMeals(city,"mercato",6,100)==0,"Households share finite shop stock");
            var json=CityEconomy.Stock.CODEC.encodeStart(JsonOps.INSTANCE,made).getOrThrow();helper.assertTrue(CityEconomy.Stock.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow().equals(made),"Raw materials and processed-period marker survive reload");
        }finally{restore(city,CityEconomy.STOCKS,stocks);}helper.succeed();
    }
    @GameTest public void acquaintancesNeedRepeatedDaysAndMisconductChangesTrustAndPrices(GameTestHelper helper){
        var city=helper.getLevel();var previous=city.removeAttached(CitySocial.BONDS);UUID npc=UUID.randomUUID(),p=UUID.randomUUID();
        try{
            CitySocial.meet(city,npc,p,0);for(int i=0;i<10;i++)CitySocial.meet(city,npc,p,0);
            helper.assertTrue(CitySocial.bond(city,npc,p).meetings()==1,"Clicking repeatedly does not build a friendship");
            CitySocial.meet(city,npc,p,1);CitySocial.meet(city,npc,p,2);for(int i=0;i<3;i++)CitySocial.purchased(city,npc,p);
            var b=CitySocial.bond(city,npc,p);helper.assertTrue(CitySocial.greeting(b,"Ciro").contains("cliente abituale"),"A shopkeeper remembers repeat customers");
            CitySocial.put(city,npc,p,new CitySocial.Bond(70,b.meetings(),b.purchases(),1,0,b.met(),2,-1));
            helper.assertTrue(CitySocial.price(city,npc,p,10)==9,"Established trust grants a visible discount");
            CitySocial.harm(city,npc,p);CitySocial.harm(city,npc,p);
            helper.assertTrue(CitySocial.price(city,npc,p,10)==13&&CitySocial.greeting(CitySocial.bond(city,npc,p),"Ciro").contains("Ricordo"),"Aggression changes the remembered dialogue and prices");
            var bond=CitySocial.bond(city,npc,p);var json=CitySocial.Bond.CODEC.encodeStart(JsonOps.INSTANCE,bond).getOrThrow();helper.assertTrue(CitySocial.Bond.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow().equals(bond),"Trust and grievances survive reloads");
        }finally{restore(city,CitySocial.BONDS,previous);}helper.succeed();
    }
    @GameTest public void calendarScaleChangePreservesAgesAndFamilyIdentities(GameTestHelper helper){
        var city=helper.getLevel();var previous=city.removeAttached(NapoliCivicLife.CENSUS);var ratio=city.removeAttached(CityCalendar.RATIO);int original=CityLifeConfig.settings.daysPerYear;
        try{
            var c=citizen(city,UUID.randomUUID(),"public_0");city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(NapoliBroadcast.day(city),List.of(c)));city.setAttached(CityCalendar.RATIO,original);
            CityLifeConfig.settings.daysPerYear=original==7?3:7;CityCalendar.sync(city);
            var changed=NapoliCivicLife.census(city).people().getFirst();helper.assertTrue(changed.age(NapoliBroadcast.day(city))==30&&changed.id().equals(c.id())&&changed.balance()==c.balance(),"A slower calendar cannot turn adults into children or change their savings");
            var same=NapoliCivicLife.census(city);CityCalendar.sync(city);helper.assertTrue(NapoliCivicLife.census(city).equals(same),"Calendar conversion runs once");
            helper.assertTrue(new CityLifeConfig.Settings().daysPerYear==7,"New installations have a longer childhood by default");
        }finally{CityLifeConfig.settings.daysPerYear=original;restore(city,NapoliCivicLife.CENSUS,previous);restore(city,CityCalendar.RATIO,ratio);}helper.succeed();
    }
    @GameTest public void promotionRequiresRecordedPerformanceAndHealthyRest(GameTestHelper helper){
        var city=helper.getLevel();var lives=city.removeAttached(CitySimulation.LIFE);var attendance=city.removeAttached(CityRoutines.WORK);var census=city.removeAttached(NapoliCivicLife.CENSUS);
        try{
            var c=citizen(city,UUID.randomUUID(),"public_0");city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(0,List.of(c)));
            var l=CitySimulation.fresh(c.id()).career(100,60,13,0,0,List.of());CitySimulation.put(city,c.id(),l);
            CitySimulation.daily(city,c,1,12);helper.assertTrue(CitySimulation.life(city,c.id()).rank()==0,"Time alone does not award a promotion");
            CitySimulation.put(city,c.id(),l);city.setAttached(CityRoutines.WORK,Map.of(c.id().toString(),new CityRoutines.Work(0,4)));
            CitySimulation.daily(city,c,1,12);helper.assertTrue(CitySimulation.life(city,c.id()).rank()==1,"A rested, trusted worker with recorded attendance earns promotion");
        }finally{restore(city,CitySimulation.LIFE,lives);restore(city,CityRoutines.WORK,attendance);restore(city,NapoliCivicLife.CENSUS,census);}helper.succeed();
    }
    @GameTest public void windowsSwitchGraduallyAndRushHoursHaveBoundaries(GameTestHelper helper){
        var homes=CityLivingHomes.all();long lit=homes.stream().filter(h->CityAtmosphere.lit(h,18000)).count();
        helper.assertTrue(lit>0&&lit<homes.size(),"Some households remain awake after other windows go dark");
        helper.assertTrue(homes.stream().noneMatch(h->CityAtmosphere.lit(h,6000)),"Home lamps are off in daylight");
        helper.assertTrue(CityAtmosphere.rush(2500)&&CityAtmosphere.rush(11000)&&!CityAtmosphere.rush(17000),"Extra traffic is tied to two rush-hour periods");helper.succeed();
    }
    @GameTest public void neighborsChooseACafeInTheirOwnQuarter(GameTestHelper helper){
        var west=CityLivingHomes.PUBLIC_HOMES.getFirst().entrance();
        helper.assertTrue(CityNeighborhood.cafe(west).distSqr(west)<NapoliMap.CAFE.distSqr(west),"Residents of Partenope can meet locally instead of crossing the entire city");
        helper.assertTrue(CityNeighborhood.cafe(NapoliMap.CAFE).equals(NapoliMap.CAFE),"The original cafe remains the closest meeting point in the center");helper.succeed();
    }
    @GameTest public void materialRemovalIsAtomicAndInvalidDeliveryCommandsDoNotPay(GameTestHelper helper){
        HousingCapacityGameTest.withPlayers(helper,1,players->{var p=players.getFirst();p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.WHEAT,3));int cash=NapoliStreet.cash(p);
            helper.assertTrue(!CityEconomy.take(p,Items.WHEAT,4)&&p.getInventory().getItem(0).getCount()==3,"A missing ingredient leaves all inventory stacks unchanged");
            helper.assertTrue(CityEconomy.take(p,Items.WHEAT,2)&&p.getInventory().getItem(0).getCount()==1,"A valid material transfer removes exactly the requested quantity");
            helper.assertTrue(CityEconomy.delivery(p,"forno",true)==0&&NapoliStreet.cash(p)==cash,"An out-of-city delivery cannot pay a reward");
        });helper.succeed();
    }
    private static NapoliCivicLife.Citizen citizen(net.minecraft.server.level.ServerLevel city,UUID id,String home){return new NapoliCivicLife.Citizen(id,"Ciro Esposito",false,NapoliBroadcast.day(city)-30L*CityLifeConfig.settings.daysPerYear,100,NapoliCivicLife.Job.BAKER.ordinal(),"",home,"",true);}
    private static <T> void restore(net.minecraft.server.level.ServerLevel city,net.fabricmc.fabric.api.attachment.v1.AttachmentType<T> type,T value){if(value==null)city.removeAttached(type);else city.setAttached(type,value);}
}
