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

/** Opt-in rendered tour; screenshots and native atlas dialogue from a disposable real city. */
public final class AtlasTest {
    private static final String[] TOUR={"santelmo","san_martino","capodimonte","garibaldi","maradona","pausilypon","pietrarsa","pontile"};
    private static int titles,step;private static boolean starting;private static volatile boolean ready,failed,done;
    private static net.minecraft.core.BlockPos savedBlock;
    public static void register(){if(!Boolean.getBoolean("napuli.atlastest"))return;ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(failed||done){mc.stop();return;}if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==80){var settings=new LevelSettings("Atlante Napoli test",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("napoli-atlas-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
        if(mc.getSingleplayerServer()==null||mc.player.level().dimension()!=NapoliMap.LEVEL)return;
        if(!starting){starting=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(10);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();server(mc,p->{p.setGameMode(GameType.SPECTATOR);var s=p.level().getServer();s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"time set 6000");s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"weather clear");ready=true;});}if(!ready)return;
        try{int phase=step/180;int local=step%180;step++;
            if(phase<TOUR.length){var place=NapoliAtlas.find(TOUR[phase]);if(local==0)server(mc,p->{var city=p.level();var chunk=city.getChunkAt(new net.minecraft.core.BlockPos(place.x(),64,place.z()));NapoliExpansion.generate(city,chunk);p.teleportTo(city,place.x()+.5,NapoliExpansion.height(place.x(),place.z())+75,place.z()-75.5,Set.of(),0,43,true);check(NapoliExpansion.revision(chunk)==NapoliExpansion.REVISION,"Exploring builds "+place.name());});
                if(phase==0&&local==80)server(mc,p->{savedBlock=new net.minecraft.core.BlockPos(place.x(),NapoliExpansion.height(place.x(),place.z())+8,place.z());p.level().setBlock(savedBlock,net.minecraft.world.level.block.Blocks.EMERALD_BLOCK.defaultBlockState(),2);CityProperty.placed(p.level(),savedBlock);p.level().getServer().saveEverything(true,true,true);});
                if(local==150){check(mc.player.level().hasChunkAt(new net.minecraft.core.BlockPos(place.x(),64,place.z())),"Client receives the landmark chunk: "+place.id());shot(mc,"atlas-"+place.id());}
            }else if(phase==TOUR.length){if(local==0)server(mc,p->{check(p.level().getChunkSource().getChunkNow(savedBlock.getX()>>4,savedBlock.getZ()>>4)==null,"The first district really unloads during the distant tour");NapoliAtlasGuide.menu(p,0);});if(local==40){check(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>,"Native atlas opens on the real client");shot(mc,"atlas-menu");mc.gui.setScreen(null);server(mc,p->{check(NapoliAtlasGuide.target(p,"vanvitelli")==1,"A destination can be selected");var dest=NapoliAtlas.find("vanvitelli").arrival();p.teleportTo(p.level(),dest.getX()+.5,dest.getY()+3,dest.getZ()+12.5,Set.of(),180,5,true);});}if(local==110)server(mc,p->{var chunk=p.level().getChunkAt(savedBlock);check(NapoliExpansion.revision(chunk)==NapoliExpansion.REVISION&&p.level().getBlockState(savedBlock).is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK),"Saved district reloads without replacing player changes");Napuli.LOGGER.info("[ATLASTEST] real chunk unload/reload and player edit preserved");});if(local==150)shot(mc,"atlas-vanvitelli-street");}
            else{Napuli.LOGGER.info("[ATLASTEST] ALL PASSED: {} rendered districts, native atlas and saved walking directions",TOUR.length);done=true;}
        }catch(Throwable e){failed=true;Napuli.LOGGER.error("[ATLASTEST] FAILED",e);}
    });}
    private static void server(Minecraft mc,Consumer<ServerPlayer> action){var s=mc.getSingleplayerServer();s.execute(()->{try{action.accept(s.getPlayerList().getPlayers().getFirst());}catch(Throwable e){failed=true;Napuli.LOGGER.error("[ATLASTEST] FAILED on server",e);}});}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[ATLASTEST] screenshot {}",name));}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private AtlasTest(){}
}
