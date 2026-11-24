package dev.napuli;

import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Rendered smoke test of a populated district, shop dialogue and real metro passenger journey. */
public final class DistrictClientTest {
    private static int titles,tick;private static boolean starting;private static volatile boolean ready,failed,done,boarded,arrived;private static String shop;private static int palaceAt;
    public static void register(){if(!Boolean.getBoolean("napuli.districttest"))return;ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(failed||done){mc.stop();return;}if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==80){var settings=new LevelSettings("Quartieri e trasporti test",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("napoli-district-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
        if(mc.getSingleplayerServer()==null||mc.player.level().dimension()!=NapoliMap.LEVEL)return;
        if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen)mc.gui.setScreen(null);
        if(!starting){starting=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();server(mc,p->{var city=p.level();city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set 2300");DistrictLife.seed(city);var d=DistrictLife.districts().getFirst();shop=d.id();var h=d.business();for(int x=(h.x()-20)>>4;x<=(h.x()+40)>>4;x++)for(int z=(h.z()-20)>>4;z<=(h.z()+45)>>4;z++){var c=city.getChunk(x,z);NapoliExpansion.generate(city,c);DistrictLife.fit(city,c);CityRailway.fit(city,c);}p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.teleportTo(city,h.x()+11.5,h.ground()+5,h.z()+38.5,Set.of(),180,18,true);ready=true;});}if(!ready)return;
        try{tick++;
            if(tick==15)server(mc,p->{
                var home=DistrictLife.byId(shop).homes().getFirst();
                for(int x=home.x()>>4;x<=(home.x()+21)>>4;x++)for(int z=home.z()>>4;z<=(home.z()+23)>>4;z++){
                    var chunk=p.level().getChunk(x,z);
                    NapoliExpansion.generate(p.level(),chunk);
                    NapoliExpansion.upgradeInteriors(p.level(),chunk);
                    DistrictLife.fit(p.level(),chunk);
                    NapoliFurniture.fit(p.level(),chunk);
                    NapoliHomeDecor.fit(p.level(),chunk);
                }
                var sofa=CityLivingHomes.use(home,CityLivingHomes.Furniture.SOFA).block();
                var sofaHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(sofa), net.minecraft.core.Direction.NORTH, sofa, false);
                var sofaState=p.level().getBlockState(sofa);
                check(sofaState.is(NapoliFurniture.DIVANO),"Generated sofa gets the interactive model");
                sofaState.useWithoutItem(p.level(), p, sofaHit);
                check(CityMovement.isSeat(p.getVehicle()),"Clicking the sofa seats the player");
                var seat=p.getVehicle();
                p.stopRiding();
                seat.discard();
                var fridge=CityLivingHomes.use(home,CityLivingHomes.Furniture.FRIDGE).block();
                var fridgeEntity=p.level().getBlockEntity(fridge);
                check(fridgeEntity instanceof net.minecraft.world.Container,"Fridge has a persistent inventory");
                var fridgeHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(fridge), net.minecraft.core.Direction.SOUTH, fridge, false);
                var fridgeState=p.level().getBlockState(fridge);
                check(fridgeState.is(NapoliFurniture.FRIGORIFERO),"Generated fridge has the new model");
                fridgeState.useWithoutItem(p.level(),p,fridgeHit);
                check(p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu,"Fridge opens native slots");
                p.closeContainer();
                var microwave=new net.minecraft.core.BlockPos(home.x()+1,home.ground()+1,home.z()+5);
                var prev=p.level().getBlockState(microwave);
                p.level().setBlockAndUpdate(microwave,NapoliFurniture.MICROONDE.defaultBlockState());
                var microwaveHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(microwave), net.minecraft.core.Direction.NORTH, microwave, false);
                var potato=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTATO);
                p.level().getBlockState(microwave).useItemOn(potato,p.level(),p,net.minecraft.world.InteractionHand.MAIN_HAND,microwaveHit);
                check(p.level().getBlockState(microwave).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),"Microwave starts an animated cycle");
                check(p.getInventory().countItem(net.minecraft.world.item.Items.BAKED_POTATO)==1,"Microwave cooks a potato");
                p.level().setBlockAndUpdate(microwave,prev);
                checkAccessories(p, new net.minecraft.core.BlockPos(home.x()+8, home.ground(), home.z()+17));
                var coffeeTable=new net.minecraft.core.BlockPos(home.x()+3,home.ground(),home.z()+8);
                check(p.level().getBlockState(coffeeTable).is(NapoliFurniture.COFFEE_TABLE),"Living room has a low table");
                var stoveApproach=CityLivingHomes.use(home,CityLivingHomes.Furniture.STOVE).stand();
                check(p.level().isEmptyBlock(stoveApproach),"Kitchen work aisle stays clear");
                var sink=CityLivingHomes.use(home,CityLivingHomes.Furniture.SINK).block();
                var desk=CityLivingHomes.use(home,CityLivingHomes.Furniture.DESK).block();
                var lamp=desk.above();
                check(p.level().getBlockState(sink).is(NapoliFurniture.SINK),"Ceramic sink replaces its legacy fixture");
                check(p.level().getBlockState(desk).is(NapoliFurniture.WORKTOP),"Worktop replaces its legacy fixture");
                var state=p.level().getBlockState(lamp);
                check(state.is(NapoliFurniture.LAMP),"Lamp rests on the worktop");
                var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(lamp),net.minecraft.core.Direction.UP,lamp,false);
                state.useWithoutItem(p.level(),p,hit);
                check(p.level().getBlockState(lamp).getLightEmission()==14,"Lamp turns on");
                NapoliHygiene.wash(p,sink,20);
                var sinkHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(sink),net.minecraft.core.Direction.UP,sink,false);
                p.level().getBlockState(sink).useWithoutItem(p.level(),p,sinkHit);
                check(NapoliHygiene.value(p)==55,"Ceramic sink restores hygiene");
                var stair=new net.minecraft.core.BlockPos(home.x()+16,home.ground(),home.z()+16);
                check(p.level().getBlockState(stair).is(net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS),"The district house has an interior staircase");
                p.teleportTo(p.level(),sofa.getX()+.5,sofa.getY(),sofa.getZ()-1.5,Set.of(),0,0,true);
                sofaState.useWithoutItem(p.level(), p, sofaHit);
                check(CityMovement.isSeat(p.getVehicle()),"Clicking the sofa seats the player");
            });
            if(tick==50){
                check(mc.player.getVehicle() instanceof net.minecraft.world.entity.decoration.ArmorStand marker && marker.isInvisible(),"The seat mount is synced to the real client");
                shot(mc,"interni-divano");
                server(mc,p->{
                    var seat=p.getVehicle();
                    p.stopRiding();
                    if(CityMovement.isSeat(seat)) seat.discard();
                    var home=DistrictLife.byId(shop).homes().getFirst();
                    p.teleportTo(p.level(),home.x()+7.5,home.ground()+.1,home.z()+13.5,Set.of(),180,5,true);
                });
            }
            if(tick==65)shot(mc,"interni-basso");
            if(tick==68)server(mc,p->{
                var home=DistrictLife.byId(shop).homes().getFirst();
                p.getAbilities().flying=false;
                p.onUpdateAbilities();
                p.teleportTo(p.level(),home.x()+16.5,home.ground()+.1,home.z()+15.2,Set.of(),0,0,true);
            });
            if(tick==73)mc.options.keyUp.setDown(true);
            if(tick==105)mc.options.keyUp.setDown(false);
            if(tick==108)server(mc,p->{
                var home=DistrictLife.byId(shop).homes().getFirst();
                check(p.getY()>=home.ground()+4.95,"The client can walk all the way up the staircase");
                var shower=CityLivingHomes.use(home,CityLivingHomes.Furniture.SHOWER).block();
                check(NapoliShower.isColumn(p.level(),shower.above()),"The bathroom has the usable shower model");
                p.teleportTo(p.level(),home.x()+15.5,home.ground()+5.1,home.z()+11.5,Set.of(),180,12,true);
            });
            if(tick==112)shot(mc,"interni-piano-superiore");
            if(tick==118)server(mc,p->{
                var business=DistrictLife.byId(shop).business();
                p.teleportTo(p.level(),business.x()+11.5,business.ground()+5,business.z()+38.5,Set.of(),180,18,true);
            });
            if(tick==160)shot(mc,"district-bottega");if(tick==180)server(mc,p->DistrictShops.menu(p,shop));if(tick==220){check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,"Shop dialogue opens on the real client");shot(mc,"district-shop-menu");mc.gui.setScreen(null);server(mc,p->CityNetwork.menu(p));}if(tick==260){check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,"Transport dialogue opens on the real client");shot(mc,"district-transport-menu");mc.gui.setScreen(null);server(mc,p->{var line=CityNetwork.line("M6");var stop=CityNetwork.stop("augusto");CityNetwork.ensureStation(p.level(),stop);var pos=stop.boarding(line);p.teleportTo(p.level(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),90,0,true);NapoliStreet.credit(p,100);check(CityNetwork.book(p,"lala","M6")==1,"Metro trip can be booked");});}
            if(tick>260&&tick%10==0)server(mc,p->{var t=p.getAttached(CityNetwork.TICKET);if(t!=null&&t.state().equals("riding")){boarded=true;check(p.getVehicle() instanceof CityVehicle,"Real client is a vehicle passenger");}if(boarded&&t==null&&!arrived){arrived=true;check(p.getBlockY()==11,"Metro arrives on an underground platform");}});
            if(tick==400)shot(mc,"district-metro-ride");if(arrived&&tick>430&&palaceAt==0){
                shot(mc,"district-metro-arrival");
                palaceAt=tick;
                server(mc,p->{
                    p.teleportTo(p.level(),38.5,71.1,34.5,Set.of(),0,2,true);
                    var stair=new net.minecraft.core.BlockPos(42,64,28);
                    check(p.level().getBlockState(stair).is(net.minecraft.world.level.block.Blocks.QUARTZ_STAIRS),"The palace has a real staircase to the piano nobile");
                });
            }
            if(palaceAt>0&&tick>=palaceAt+45){shot(mc,"interni-palazzo-reale");Napuli.LOGGER.info("[DISTRICTTEST] ALL PASSED: rendered district, native shops/transit and real metro ride");done=true;}if(tick>2000)throw new AssertionError("Metro trip did not complete");
        }catch(Throwable e){failed=true;Napuli.LOGGER.error("[DISTRICTTEST] FAILED",e);}
    });}
    private static void server(Minecraft mc,Consumer<ServerPlayer> action){var s=mc.getSingleplayerServer();s.execute(()->{try{action.accept(s.getPlayerList().getPlayers().getFirst());}catch(Throwable e){failed=true;Napuli.LOGGER.error("[DISTRICTTEST] FAILED on server",e);}});}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[DISTRICTTEST] screenshot {}",name));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private DistrictClientTest(){}
    private static void checkAccessories(ServerPlayer p, net.minecraft.core.BlockPos strip) {
        var city = p.level();
        var dryer = strip.east();
        var vacuum = strip.south();
        var scale = strip.west();
        var fixtures = java.util.Map.of(strip, NapoliFurniture.POWER_STRIP, dryer, NapoliFurniture.HAIR_DRYER,
                vacuum, NapoliFurniture.VACUUM, scale, NapoliFurniture.SCALE);
        var original = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
        fixtures.forEach((pos, block) -> {
            original.put(pos, city.getBlockState(pos));
            city.setBlockAndUpdate(pos, block.defaultBlockState());
        });
        var lit = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
        try {
            check(!HomeFixtureBlock.powered(city, dryer), "An off power strip supplies no power");
            useFixture(p, strip);
            check(HomeFixtureBlock.powered(city, dryer), "A switched-on strip powers the nearby hair dryer");
            NapoliHygiene.soakHair(p);
            useFixture(p, dryer);
            check(city.getBlockState(dryer).getValue(lit) && !NapoliHygiene.dryHair(p), "The hair dryer dries hair");
            var litterPos = vacuum.south();
            var key = Long.toString(litterPos.asLong());
            int previous = city.getAttachedOrElse(CityOccupations.LITTER, java.util.Map.of())
                    .getOrDefault(key, new CityOccupations.Litter(litterPos, 0, 0)).units();
            CityOccupations.litter(city, litterPos, 1);
            useFixture(p, vacuum);
            check(city.getBlockState(vacuum).getValue(lit) && city.getAttachedOrElse(CityOccupations.LITTER, java.util.Map.of())
                    .getOrDefault(key, new CityOccupations.Litter(litterPos, 0, 0)).units() == previous, "The vacuum removes litter");
            useFixture(p, scale);
            check(city.getBlockState(scale).getValue(lit), "The scale turns on to show a reading");
        } finally {
            original.forEach(city::setBlockAndUpdate);
        }
    }

    private static void useFixture(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        player.level().getBlockState(pos).useWithoutItem(player.level(), player, hit);
    }

}
