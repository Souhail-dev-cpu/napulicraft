package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Native rendering verification in a disposable save, including real generated hill houses. */
public final class CityQualityClientTest {
    private static int titles,step;
    private static volatile boolean finished,ready;
    private static NapoliMergellina.House atlas;
    public static void register(){
        if(!Boolean.getBoolean("napuli.cityqualitytest"))return;
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(finished){mc.stop();return;}
            if(mc.player==null){
                if(mc.gui.screen() instanceof TitleScreen&&++titles==80){
                    var settings=new LevelSettings("Verifica qualità città",GameType.SPECTATOR,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("city-quality-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());
                }return;
            }
            mc.options.pauseOnLostFocus=false;
            if(mc.getSingleplayerServer()==null||mc.player.level().dimension()!=NapoliMap.LEVEL)return;
            step++;
            try{
                if(step==40)server(mc,p->{
                    var city=p.level();check(NapoliInteriors.upgradedWorkplaces(city),"City interiors have finished generating");
                    for(var home:NapoliMergellina.PROPERTIES){
                        for(var point:CityLivingHomes.points(home))city.getChunkAt(point.stand());
                        CityArchitecture.finishNew(city,home);
                        for(var point:CityLivingHomes.points(home))check(NapoliResidentHomes.walkable(city,point.stand()),"Furniture access "+home.id()+" / "+point.furniture()+" / "+city.getBlockState(point.stand())+" / "+city.getBlockState(point.stand().above()));
                    }
                    p.setGameMode(GameType.SPECTATOR);p.teleportTo(city,-513.5,77,-162,Set.of(),180,15,true);ready=true;
                });
                if(step==70&&!mc.gui.hud.isHidden())mc.gui.hud.toggle();
                if(step==180&&ready)shot(mc,"quality-facciate");
                if(step==200)server(mc,p->{var home=NapoliMergellina.PROPERTIES.get(7);p.teleportTo(p.level(),home.x()+5.5,65.1,home.z()+6.5,Set.of(),145,16,true);});
                if(step==280)shot(mc,"quality-cucina");
                if(step==300)server(mc,p->{
                    var city=p.level();atlas=DistrictLife.homes().stream().filter(a->a.ground()>68&&a.floors()>=2).findFirst().orElseThrow();
                    var chunks=new ArrayList<net.minecraft.world.level.chunk.LevelChunk>();
                    for(int x=(atlas.x()-1)>>4;x<=(atlas.x()+atlas.width())>>4;x++)for(int z=(atlas.z()-1)>>4;z<=(atlas.z()+atlas.depth()+1)>>4;z++){
                        var chunk=city.getChunk(x,z);NapoliExpansion.generate(city,chunk);chunks.add(chunk);
                    }
                    for(var chunk:chunks){DistrictLife.fit(city,chunk);NapoliFurniture.fit(city,chunk);}
                    CityArchitecture.finishNew(city,atlas);
                    for(var point:CityLivingHomes.points(atlas))check(NapoliResidentHomes.walkable(city,point.stand()),"Hill house furniture access "+point.furniture()+" "+point.stand());
                    var upstairs=new net.minecraft.core.BlockPos(atlas.x()+13,atlas.ground()+5,atlas.z()+5);
                    check(city.getBlockState(upstairs).getBlock() instanceof BedBlock,"Furnished upper-floor bedroom");
                    check(CityAccessibility.connected(city,new net.minecraft.core.BlockPos(atlas.x()+12,atlas.ground()+5,atlas.z()+12),upstairs.south()),"The new bedroom connects to the upstairs hall");
                    p.teleportTo(city,atlas.x()+32,atlas.ground()+15,atlas.z()+38,Set.of(),140,15,true);
                    Napuli.LOGGER.info("[CITYQUALITY] Hill home {} y={}",atlas.id(),atlas.ground());
                });
                if(step==440)shot(mc,"quality-palazzo");
                if(step==460)server(mc,p->p.teleportTo(p.level(),atlas.x()+13.5,atlas.ground()+6.2,atlas.z()+11.5,Set.of(),180,12,true));
                if(step==560)shot(mc,"quality-stanze");
                if(step==580)server(mc,p->{p.level().getServer().getCommands().performPrefixedCommand(p.level().getServer().createCommandSourceStack(),"time set 13800");p.teleportTo(p.level(),atlas.x()+32,atlas.ground()+15,atlas.z()+38,Set.of(),140,15,true);});
                if(step==700)shot(mc,"quality-sera");
                if(step==730){Napuli.LOGGER.info("[CITYQUALITY] ALL PASSED: original furniture, hill homes, upper rooms, material rendering and five native screenshots");finished=true;}
            }catch(Throwable failure){Napuli.LOGGER.error("[CITYQUALITY] FAILED at step "+step,failure);finished=true;}
        });
    }
    private static void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> work){mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());if(p==null)throw new IllegalStateException("Missing test player");work.accept(p);}catch(Throwable failure){Napuli.LOGGER.error("[CITYQUALITY] FAILED server step "+step,failure);finished=true;}});}
    private static void check(boolean value,String description){if(!value)throw new IllegalStateException(description);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[CITYQUALITY] screenshot {}",name));}
}
