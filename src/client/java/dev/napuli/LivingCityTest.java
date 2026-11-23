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
    public static void register(){
        if(!Boolean.getBoolean("napuli.livingcitytest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
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
                    Napuli.LOGGER.info("[LIVINGCITYTEST] ALL PASSED");
                });
                if(step==550){shot(mc,"living-city-traffic");mc.stop();}
            }catch(Throwable error){Napuli.LOGGER.error("[LIVINGCITYTEST] FAILED",error);failed=true;}
        });
    }
    private static void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> action){
        var server=mc.getSingleplayerServer();var id=mc.player.getUUID();server.execute(()->{try{action.accept(server.getPlayerList().getPlayer(id));}catch(Throwable e){Napuli.LOGGER.error("[LIVINGCITYTEST] FAILED",e);failed=true;}});
    }
    private static void guestPermissions(ServerPlayer player){
        var city=player.level();var house=NapoliMergellina.PROPERTIES.get(7);
        var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,player.blockPosition().east(2),0);npc.setNoAi(true);guest=npc.getUUID();
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
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[LIVINGCITYTEST] screenshot {}",name));}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
