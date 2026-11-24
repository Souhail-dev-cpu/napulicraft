package dev.napuli;

import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Real walking across an old cliff, then saved legacy terrain regraded on chunk reload. */
public final class StreetClientTest {
    private static int titles,tick,settled,direction,startZ,endZ;private static boolean starting,walked;
    private static volatile boolean ready,failed,done,returned,repaired;
    private static BlockPos cliff,kept;
    public static void register(){if(!Boolean.getBoolean("napuli.streettest"))return;ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(failed||done){mc.options.keyUp.setDown(false);mc.stop();return;}
        if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==80){var settings=new LevelSettings("Strade e dislivelli test",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("napoli-streets-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
        if(mc.getSingleplayerServer()==null||mc.player.level().dimension()!=NapoliMap.LEVEL)return;
        if(!starting){starting=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.autoJump().set(false);mc.options.keyJump.setDown(false);mc.options.keyUp.setDown(false);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();server(mc,p->{
            cliff=findCliff();direction=NapoliExpansion.legacyHeight(cliff.getX(),cliff.getZ())<NapoliExpansion.legacyHeight(cliff.getX(),cliff.getZ()+1)?1:-1;startZ=direction==1?cliff.getZ()-4:cliff.getZ()+5;endZ=direction==1?cliff.getZ()+5:cliff.getZ()-4;
            var city=p.level();for(int x=(cliff.getX()-20)>>4;x<=(cliff.getX()+20)>>4;x++)for(int z=(cliff.getZ()-24)>>4;z<=(cliff.getZ()+24)>>4;z++)NapoliExpansion.generate(city,city.getChunk(x,z));
            kept=new BlockPos(cliff.getX()+6,NapoliExpansion.height(cliff.getX()+6,cliff.getZ())+2,cliff.getZ());city.setBlock(kept,Blocks.EMERALD_BLOCK.defaultBlockState(),18);CityProperty.placed(city,kept);
            p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();p.setPermanentlyInvulnerable(true);view(p);city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set 6000");ready=true;
        });}if(!ready)return;
        try{tick++;
            if(tick==65)shot(mc,"street-new-ramp");
            if(tick==80)mc.options.keyUp.setDown(true);
            if(tick>=80&&tick<260&&!walked&&direction*(mc.player.getZ()-(startZ+.5))>=8.8){walked=true;mc.options.keyUp.setDown(false);Napuli.LOGGER.info("[STREETTEST] walked across old cliff without jumping at tick {}",tick);}
            if(tick==260){mc.options.keyUp.setDown(false);check(walked,"Player can walk across the repaired street without jumping");shot(mc,"street-walked");server(mc,p->{
                var city=p.level();p.setGameMode(GameType.SPECTATOR);var chunks=new LinkedHashMap<Long,net.minecraft.world.level.chunk.LevelChunk>();
                for(int x=cliff.getX()-1;x<=cliff.getX()+1;x++)for(int z=cliff.getZ()-5;z<=cliff.getZ()+5;z++)if(CityStreets.adjusted(x,z)){
                    var chunk=city.getChunk(x>>4,z>>4);chunks.put(chunk.getPos().pack(),chunk);int old=NapoliExpansion.legacyHeight(x,z),top=Math.max(old,NapoliExpansion.height(x,z))+3;
                    for(int y=63;y<=top;y++)city.setBlock(new BlockPos(x,y,z),(y<old?Blocks.TUFF:y==old?Blocks.POLISHED_ANDESITE:Blocks.AIR).defaultBlockState(),18);
                }
                for(var chunk:chunks.values())((AttachmentTarget)chunk).removeAttached(CityStreets.GRADED);city.getServer().saveEverything(true,true,true);
                p.teleportTo(city,cliff.getX()<0?1900.5:-1900.5,180,-750.5,Set.of(),180,0,true);city.getChunkSource().move(p);
            });}
            if(tick>=300&&tick%20==0&&!repaired)server(mc,p->{var city=p.level();var chunk=city.getChunkSource().getChunkNow(cliff.getX()>>4,cliff.getZ()>>4);
                if(!returned&&chunk==null){returned=true;Napuli.LOGGER.info("[STREETTEST] legacy street really unloaded");view(p);}
                else if(returned&&chunk!=null&&((AttachmentTarget)chunk).getAttachedOrElse(CityStreets.GRADED,0)>=CityStreets.REVISION){
                    for(int z=cliff.getZ()-4;z<=cliff.getZ()+4;z++){var at=new BlockPos(cliff.getX(),NapoliExpansion.height(cliff.getX(),z),z);check(!city.getBlockState(at).getCollisionShape(city,at).isEmpty()&&city.getBlockState(at.above()).getCollisionShape(city,at.above()).isEmpty(),"Saved street has a supported, clear surface");}
                    check(city.getBlockState(kept).is(Blocks.EMERALD_BLOCK),"Player construction survives the street upgrade");repaired=true;
                }
            });
            if(repaired&&settled==0)settled=tick;
            if(settled>0&&tick>settled+60){shot(mc,"street-saved-repaired");Napuli.LOGGER.info("[STREETTEST] ALL PASSED: walking without jumping, supported ramps, actual unload/reload and preserved player block");done=true;}
            if(tick>1400)throw new AssertionError("Street did not unload and upgrade in time");
        }catch(Throwable e){failed=true;Napuli.LOGGER.error("[STREETTEST] FAILED",e);}
    });}
    private static BlockPos findCliff(){for(var p:NapoliAtlas.PLACES)if(!p.original())for(int x=p.x()-p.radius()-12;x<=p.x()+p.radius()+12;x++)if(Math.floorMod(x,40)==2)for(int z=p.z()-p.radius()-10;z<=p.z()+p.radius()+10;z++){
        if(Math.abs(NapoliExpansion.legacyHeight(x,z)-NapoliExpansion.legacyHeight(x,z+1))<3)continue;boolean clear=true;for(int dz=-5;dz<=6;dz++)if(!CityStreets.adjusted(x,z+dz))clear=false;if(clear)return new BlockPos(x,0,z);
    }throw new AssertionError("Expected an accessible old road cliff");}
    private static void view(ServerPlayer p){p.teleportTo(p.level(),cliff.getX()+.5,NapoliExpansion.height(cliff.getX(),startZ)+1,startZ+.5,Set.of(),direction==1?0:180,5,true);p.level().getChunkSource().move(p);}
    private static void server(Minecraft mc,Consumer<ServerPlayer> action){var s=mc.getSingleplayerServer();s.execute(()->{try{action.accept(s.getPlayerList().getPlayers().getFirst());}catch(Throwable e){failed=true;Napuli.LOGGER.error("[STREETTEST] FAILED on server",e);}});}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[STREETTEST] screenshot {}",name));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private StreetClientTest(){}
}
