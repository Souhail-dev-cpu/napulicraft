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
                }
                var stair=new net.minecraft.core.BlockPos(home.x()+16,home.ground(),home.z()+16);
                check(p.level().getBlockState(stair).is(net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS),"The district house has an interior staircase");
                p.teleportTo(p.level(),home.x()+7.5,home.ground()+.1,home.z()+13.5,Set.of(),180,5,true);
            });
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
}
