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

/** Real client, paired door updates, then a saved old doorway repaired on chunk reload. */
public final class EntranceClientTest {
    private static int titles,tick,settled;private static boolean starting;
    private static volatile boolean ready,failed,done,returned,repaired;
    private static NapoliMergellina.House home;private static BlockPos door,kept;private static Block wall;
    public static void register(){if(!Boolean.getBoolean("napuli.entrancetest"))return;ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(failed||done){mc.stop();return;}
        if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==80){var settings=new LevelSettings("Porte e muri test",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("napoli-entrances-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());}return;}
        if(mc.getSingleplayerServer()==null||mc.player.level().dimension()!=NapoliMap.LEVEL)return;
        if(!starting){starting=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();server(mc,p->{
            var city=p.level();home=DistrictLife.homes().stream().filter(h->Math.floorMod(Math.floorDiv(h.x()-9,40)+Math.floorDiv(h.z()-9,40),5)==3).findFirst().orElseThrow();
            for(int x=(home.x()-16)>>4;x<=(home.x()+37)>>4;x++)for(int z=(home.z()-16)>>4;z<=(home.z()+45)>>4;z++)NapoliExpansion.generate(city,city.getChunk(x,z));
            door=home.entrance().north();wall=city.getBlockState(door.west(2)).getBlock();kept=door.north(3).east(3);city.setBlock(kept,Blocks.EMERALD_BLOCK.defaultBlockState(),18);CityProperty.placed(city,kept);
            p.setGameMode(GameType.SPECTATOR);view(p);city.getServer().getCommands().performPrefixedCommand(city.getServer().createCommandSourceStack(),"time set 6000");ready=true;
        });}if(!ready)return;
        try{tick++;
            if(tick==80){checkFrame(mc);shot(mc,"entrance-new-closed");}
            if(tick==100)server(mc,p->{var state=p.level().getBlockState(door);((DoorBlock)state.getBlock()).setOpen(p,p.level(),state,door,true);});
            if(tick==130){check(mc.player.level().getBlockState(door).getValue(DoorBlock.OPEN)&&mc.player.level().getBlockState(door.above()).getValue(DoorBlock.OPEN),"Both halves open on the real client");shot(mc,"entrance-open");}
            if(tick==160)server(mc,p->{var city=p.level();var state=city.getBlockState(door);((DoorBlock)state.getBlock()).setOpen(p,city,state,door,false);
                for(int dx=-1;dx<=1;dx++)for(int dy=0;dy<=2;dy++)if(dx!=0||dy==2)city.setBlock(door.offset(dx,dy,0),Blocks.AIR.defaultBlockState(),18);
                ((AttachmentTarget)city.getChunkAt(door)).removeAttached(NapoliExpansion.ENTRANCES_FIXED);city.getServer().saveEverything(true,true,true);
                p.teleportTo(city,home.x()<0?1900.5:-1900.5,180,-750.5,Set.of(),180,0,true);city.getChunkSource().move(p);
            });
            if(tick>=200&&tick%20==0&&!repaired)server(mc,p->{var city=p.level();var chunk=city.getChunkSource().getChunkNow(door.getX()>>4,door.getZ()>>4);
                if(!returned&&chunk==null){Napuli.LOGGER.info("[ENTRANCETEST] old doorway really unloaded");returned=true;view(p);}
                else if(returned&&chunk!=null&&((AttachmentTarget)chunk).getAttachedOrElse(NapoliExpansion.ENTRANCES_FIXED,false)){
                    for(int dx=-1;dx<=1;dx++)for(int dy=0;dy<=2;dy++)if(dx!=0||dy==2)check(chunk.getBlockState(door.offset(dx,dy,0)).is(wall),"Reload automatically repairs the old doorway");
                    check(city.getBlockState(kept).is(Blocks.EMERALD_BLOCK),"Interior player construction survives the saved upgrade");repaired=true;
                }
            });
            if(repaired&&settled==0)settled=tick;
            if(settled>0&&tick>settled+60){checkFrame(mc);shot(mc,"entrance-saved-repaired");Napuli.LOGGER.info("[ENTRANCETEST] ALL PASSED: closed facade, working door, actual chunk unload/reload, old-save repair and preserved player block");server(mc,EntranceClientTest::checkCrimeAndPay);settled=-1;}
            if(tick>1200)throw new AssertionError("Saved doorway did not unload and repair in time");
        }catch(Throwable e){failed=true;Napuli.LOGGER.error("[ENTRANCETEST] FAILED",e);}
    });}
    private static void checkCrimeAndPay(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        var city=p.level();
        var attacker=city.damageSources().playerAttack(p);
        var victim=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,p.blockPosition().east(),0);
        try {
            check(victim.hurtServer(city,attacker,1)&&Carabinieri.wantedStars(p)==3,"Actual damage to a civilian is reported as assault");
            check(victim.hurtServer(city,attacker,100)&&Carabinieri.wantedStars(p)==4,"Killing a civilian escalates the report");
        } finally {
            victim.discard();
            Carabinieri.endPursuit(p);
        }
        long day=NapoliBroadcast.day(city);
        p.setAttached(NapoliEmployment.WORK,new NapoliEmployment.Work(NapoliEmployment.Contract.CLEANER.id(),day,2,Long.MIN_VALUE));
        p.setAttached(NapoliEmployment.HOURS,new NapoliEmployment.Timecard(day,300,0,0,0));
        int before=NapoliStreet.cash(p);
        var commands=city.getServer().getCommands();
        commands.performPrefixedCommand(p.createCommandSourceStack(),"lavoro paga");
        check(NapoliStreet.cash(p)==before+2,"Direct payroll pays the 18 worked minutes");
        commands.performPrefixedCommand(p.createCommandSourceStack(),"lavoro paga");
        check(NapoliStreet.cash(p)==before+2,"Direct payroll cannot be collected twice");
        Napuli.LOGGER.info("[ENTRANCETEST] Assault, homicide and proportional payroll verified");
        done=true;
    }

    private static void view(ServerPlayer p){p.teleportTo(p.level(),door.getX()+.5,door.getY()+.1,door.getZ()+5.5,Set.of(),180,5,true);p.level().getChunkSource().move(p);}
    private static void checkFrame(Minecraft mc){for(int dx=-1;dx<=1;dx++)for(int dy=0;dy<=2;dy++)if(dx!=0||dy==2)check(mc.player.level().getBlockState(door.offset(dx,dy,0)).is(wall),"Client receives solid masonry around its door");}
    private static void server(Minecraft mc,Consumer<ServerPlayer> action){var s=mc.getSingleplayerServer();s.execute(()->{try{action.accept(s.getPlayerList().getPlayers().getFirst());}catch(Throwable e){failed=true;Napuli.LOGGER.error("[ENTRANCETEST] FAILED on server",e);}});}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->Napuli.LOGGER.info("[ENTRANCETEST] screenshot {}",name));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private EntranceClientTest(){}
}
