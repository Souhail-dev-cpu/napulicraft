package dev.napuli;

import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** A disposable real world exercises generated interiors, transactions, rendering and broadcast packets. */
public final class LivingCityTest {
    private static int titles,step;
    private static volatile boolean ready,failed;
    private static int originalCash;
    private static java.util.UUID guest;
    private static java.util.UUID diner;
    private static volatile int dinnerStage=-2;
    private static int photographed=-2;
    private static long dinnerStarted;
    private static volatile boolean mealFinished,testsDone;
    public static void register(){
        if(!Boolean.getBoolean("napuli.livingcitytest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(testsDone){mc.stop();return;}
            if(failed){mc.stop();return;}
            if(mc.player==null){
                if(mc.gui.screen() instanceof TitleScreen&&++titles==80){
                    var settings=new LevelSettings("Napoli viva test",GameType.SURVIVAL,new LevelSettings.DifficultySettings(Difficulty.NORMAL,false,false),true,WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("napoli-living-test-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());
                }return;
            }
            if(mc.getSingleplayerServer()==null||mc.player.level().dimension()!=NapoliMap.LEVEL)return;
            mc.options.pauseOnLostFocus=false;step++;
            try{
                if(step==40)server(mc,p->{
                    var city=p.level();check(NapoliCivicLife.census(city).people().size()>=120,"120 persistent residents");
                    var obstructed=new java.util.ArrayList<String>();
                    for(var h:NapoliMergellina.PROPERTIES){
                        check(city.getBlockState(CityLivingHomes.use(h,CityLivingHomes.Furniture.STOVE).block()).is(net.minecraft.world.level.block.Blocks.SMOKER),"Functional stove "+h.id());
                        check(NapoliShower.isColumn(city,CityLivingHomes.use(h,CityLivingHomes.Furniture.SHOWER).block().above()),"Usable shower "+h.id());
                        for(var point:CityLivingHomes.points(h)){
                            city.getChunkAt(point.stand());
                            if(!NapoliResidentHomes.walkable(city,point.stand()))obstructed.add(h.id()+" "+point.furniture()+" "+city.getBlockState(point.stand())+" / "+city.getBlockState(point.stand().above()));
                        }
                    }
                    check(obstructed.isEmpty(),"Obstructed furniture: "+obstructed);
                    var h=NapoliMergellina.PROPERTIES.get(7);p.stopRiding();
                    p.snapTo(h.sign().getX()+.5,64,h.sign().getZ()+.5,180,0);NapoliStreet.credit(p,1000-NapoliStreet.cash(p));
                    check(CityHousingFinance.acquire(p,h.id(),false)==1,"Mortgage purchase");
                    originalCash=NapoliStreet.cash(p);check(CityHousingFinance.acquire(p,h.id(),false)==0&&NapoliStreet.cash(p)==originalCash,"No duplicate purchase charge");
                    CityHousingFinance.bill(city,NapoliBroadcast.day(city)+7);int after=NapoliStreet.cash(p);
                    check(after<originalCash,"Installment auto-payment");CityHousingFinance.bill(city,NapoliBroadcast.day(city)+7);check(NapoliStreet.cash(p)==after,"No duplicate installment");
                    p.setGameMode(GameType.SPECTATOR);p.teleportTo(city,-526.5,78,-168,Set.of(),180,18,true);ready=true;
                });
                if(step==60&&!mc.gui.hud.isHidden())mc.gui.hud.toggle();
                if(step==170&&ready)shot(mc,"living-city-houses");
                if(step==190)server(mc,p->{var h=NapoliMergellina.PROPERTIES.get(7);p.teleportTo(p.level(),h.x()+4.5,65,h.z()+6.5,Set.of(),180,12,true);});
                if(step==230)shot(mc,"living-city-kitchen");
                if(step==250)server(mc,p->{p.level().getServer().getCommands().performPrefixedCommand(p.level().getServer().createCommandSourceStack(),"time set 13800");NapoliBroadcast.showProgram(p,NapoliBroadcast.Channel.SPORT);});
                if(step==290){check(mc.gui.screen()!=null&&mc.gui.screen().getClass().getSimpleName().equals("TelevisionScreen"),"TV packet opens animated broadcast");shot(mc,"living-city-tv");}
                if(step==310){mc.gui.setScreen(null);server(mc,p->p.teleportTo(p.level(),-337,66,-50,Set.of(),0,10,true));}
                if(step==350)server(mc,p->{p.setGameMode(GameType.SURVIVAL);guestPermissions(p);});
                if(step==390){check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,"The host can open the native guest menu");shot(mc,"living-city-guests");}
                if(step==410){mc.gui.setScreen(null);server(mc,p->{HouseGuests.clear(p.level(),NapoliMergellina.PROPERTIES.get(7).id());var npc=p.level().getEntity(guest);if(npc!=null)npc.discard();});}
                if(step==510)server(mc,p->{
                    p.setGameMode(GameType.SURVIVAL);
                    check(!p.level().getEntitiesOfClass(CityVehicle.class,p.getBoundingBox().inflate(100)).isEmpty(),"Urban traffic materializes near the player");
                    var bottle=new net.minecraft.world.item.ItemStack(CityResidentShop.WATER,2);bottle.finishUsingItem(p.level(),p);
                    check(p.hasEffect(net.minecraft.world.effect.MobEffects.SPEED)&&p.getEffect(net.minecraft.world.effect.MobEffects.SPEED).getDuration()==600,"Influencer bottle grants exactly 30 seconds of speed");
                    check(p.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER)&&bottle.getCount()==1,"The bottle is consumed and causes hunger");
                });
                if(step==550)shot(mc,"living-city-traffic");
                if(step==570)server(mc,LivingCityTest::startDomesticScene);
                if(step>570&&step%40==0)server(mc,p->{
                    var d=CityDomestic.dinner(p.level(),diner);dinnerStage=d.stage();
                    var npc=p.level().getEntity(diner);
                    Napuli.LOGGER.info("[LIVINGCITYTEST] dinner stage={} progress={} position={}",d.stage(),d.progress(),npc==null?"missing":npc.blockPosition());
                    if(d.stage()==-1&&d.served()>=dinnerStarted){check(CitySimulation.life(p.level(),diner).needs().hunger()>10,"The observed meal restores hunger");mealFinished=true;}
                });
                if(step>610&&dinnerStage!=photographed&&dinnerStage>=0){photographed=dinnerStage;shot(mc,"living-city-meal-"+dinnerStage);}
                if(step>1400&&mealFinished)server(mc,p->{var npc=p.level().getEntity(diner);if(npc!=null)npc.discard();Napuli.LOGGER.info("[LIVINGCITYTEST] ALL PASSED");testsDone=true;});
                if(step==2100)throw new IllegalStateException("The resident did not complete a natural meal: stage="+dinnerStage);
            }catch(Throwable error){Napuli.LOGGER.error("[LIVINGCITYTEST] FAILED",error);failed=true;}
        });
    }
    private static void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> action){
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{try{action.accept(server.getPlayerList().getPlayer(id));}catch(Throwable e){Napuli.LOGGER.error("[LIVINGCITYTEST] FAILED",e);failed=true;}});
    }
    private static void guestPermissions(ServerPlayer player){
        var city=player.level();var house=NapoliMergellina.PROPERTIES.get(7);
        var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,player.blockPosition().east(2),0);npc.setNoAi(true);npc.setCustomName(net.minecraft.network.chat.Component.literal("Marco Esposito"));guest=npc.getUUID();
        city.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),"casa invita "+house.id()+" "+guest);
        check(HouseGuests.invited(city,house.id(),guest),"The registered invitation command authorizes a nearby NPC");
        var owners=city.getAttachedOrElse(NapoliProperties.OWNERS,java.util.Map.of());
        var accounts=city.getAttachedOrElse(CityHousingFinance.ACCOUNTS,java.util.Map.of());
        var invitations=city.getAttachedOrElse(HouseGuests.INVITATIONS,java.util.Map.of());
        var target=house.inside().above(2);var oldTarget=city.getBlockState(target);var oldSupport=city.getBlockState(target.below());
        var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,2);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(target.below()),net.minecraft.core.Direction.UP,target.below(),false);
        try{
            var changedOwners=new java.util.HashMap<>(owners);changedOwners.put(house.id(),guest);city.setAttached(NapoliProperties.OWNERS,java.util.Map.copyOf(changedOwners));
            var changedAccounts=new java.util.HashMap<>(accounts);changedAccounts.remove(house.id());city.setAttached(CityHousingFinance.ACCOUNTS,java.util.Map.copyOf(changedAccounts));
            HouseGuests.clear(city,house.id());check(HouseGuests.grant(city,house.id(),guest,player),"The temporary host can invite the player");
            check(NapoliProperties.hasAccess(player,house)&&!CityProperty.mayBuild(city,player,target),"A guest has access without construction rights");
            check(CityProperty.storageAllowed(player,CityLivingHomes.use(house,CityLivingHomes.Furniture.FRIDGE).block()),"Invited guests can use the fridge without a theft accusation");
            city.setBlock(target,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
            city.setBlock(target.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
            var item=(net.minecraft.world.item.BlockItem)stack.getItem();
            item.place(new net.minecraft.world.item.context.BlockPlaceContext(city,player,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit));
            check(city.isEmptyBlock(target)&&stack.getCount()==2,"Guest block placement is rejected without consuming the item");
            city.setAttached(NapoliProperties.OWNERS,owners);city.setAttached(CityHousingFinance.ACCOUNTS,accounts);city.setAttached(HouseGuests.INVITATIONS,invitations);
            item.place(new net.minecraft.world.item.context.BlockPlaceContext(city,player,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit));
            check(city.getBlockState(target).is(net.minecraft.world.level.block.Blocks.COBBLESTONE)&&stack.getCount()==1,"The owner can still place blocks normally");
        }finally{
            city.setAttached(NapoliProperties.OWNERS,owners);city.setAttached(CityHousingFinance.ACCOUNTS,accounts);city.setAttached(HouseGuests.INVITATIONS,invitations);
            city.setBlock(target,oldTarget,2);city.setBlock(target.below(),oldSupport,2);
        }
        check(HouseGuests.menu(player,house.id())==1,"The mortgage owner can manage invitations");
    }
    private static void startDomesticScene(ServerPlayer p){
        var city=p.level();var h=NapoliMergellina.PROPERTIES.get(7);
        // Delivery settlement uses real inventory and cannot be replayed for a second payment.
        var stocks=city.getAttachedOrElse(CityEconomy.STOCKS,java.util.Map.of());
        try{
            CityEconomy.put(city,"forno",new CityEconomy.Stock(0,0,0,0,0));
            p.teleportTo(city,CityEconomy.DEPOT.getX()+.5,64,CityEconomy.DEPOT.getZ()+.5,Set.of(),0,0,true);
            check(CityEconomy.delivery(p,"forno",false)==1,"A player can accept a supply job at the depot");
            p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT,8));
            var destination=CityEconomy.site("forno");p.teleportTo(city,destination.getX()+.5,64,destination.getZ()+.5,Set.of(),0,0,true);
            int cash=NapoliStreet.cash(p);check(CityEconomy.delivery(p,"forno",true)==1&&NapoliStreet.cash(p)==cash+12,"A completed delivery pays once and supplies the shop");
            check(CityEconomy.stock(city,"forno").grain()==8&&CityEconomy.delivery(p,"forno",true)==0&&NapoliStreet.cash(p)==cash+12,"Replaying delivery completion cannot create money or duplicate supplies");
        }finally{city.setAttached(CityEconomy.STOCKS,stocks);}
        p.teleportTo(city,h.entrance().getX()+.5,64,h.entrance().getZ()+1.5,Set.of(),180,0,true);
        check(CityArrivals.key(p,h.id())==1,"An owner can collect a physical house key");
        net.minecraft.world.item.ItemStack key=net.minecraft.world.item.ItemStack.EMPTY;
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(CityArrivals.keyHome(p.getInventory().getItem(i)).equals(h.id()))key=p.getInventory().getItem(i).copy();
        check(!key.isEmpty(),"The key identifies the home");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,key);
        var door=h.entrance().north();boolean wasOpen=city.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,city,net.minecraft.world.InteractionHand.MAIN_HAND);
        check(city.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN)!=wasOpen,"An authorized key opens the actual door");
        var owners=city.getAttachedOrElse(NapoliProperties.OWNERS,java.util.Map.of());var accounts=city.getAttachedOrElse(CityHousingFinance.ACCOUNTS,java.util.Map.of());
        try{
            city.setAttached(NapoliProperties.OWNERS,java.util.Map.of(h.id(),java.util.UUID.randomUUID()));city.setAttached(CityHousingFinance.ACCOUNTS,java.util.Map.of());
            boolean open=city.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
            check(net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,city,net.minecraft.world.InteractionHand.MAIN_HAND)==net.minecraft.world.InteractionResult.FAIL&&city.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN)==open,"Keeping or copying a key does not preserve revoked permission");
            check(CityArrivals.ring(p,h.id())==1,"An unauthorized visitor can ring from the doorstep");
        }finally{city.setAttached(NapoliProperties.OWNERS,owners);city.setAttached(CityHousingFinance.ACCOUNTS,accounts);}
        city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set 10550");
        var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,h.inside(),0);diner=npc.getUUID();npc.setCustomName(net.minecraft.network.chat.Component.literal("Ciro Esposito"));
        long day=NapoliBroadcast.day(city);var c=new NapoliCivicLife.Citizen(diner,"Ciro Esposito",false,day-30L*CityLifeConfig.settings.daysPerYear,100,-1,"",h.id(),"",true);
        var census=NapoliCivicLife.census(city);var people=new java.util.ArrayList<>(census.people());people.add(c);city.setAttached(NapoliCivicLife.CENSUS,new NapoliCivicLife.Census(census.day(),java.util.List.copyOf(people)));
        var l=CitySimulation.fresh(diner);CitySimulation.put(city,diner,l.withNeeds(new CitySimulation.Needs(10,80,80,80,80,80),3,city.getGameTime(),0,0));
        dinnerStarted=city.getGameTime();p.setGameMode(GameType.SPECTATOR);p.teleportTo(city,h.x()+5.5,64,h.z()+10.5,Set.of(),180,8,true);
    }
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[LIVINGCITYTEST] screenshot {}",name));}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
