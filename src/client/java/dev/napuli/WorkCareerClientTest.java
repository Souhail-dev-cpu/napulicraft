package dev.napuli;

import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Acknowledged native interactions remain reliable while the server is generating distant chunks. */
public final class WorkCareerClientTest {
    private static int titles,ticks,age,last=-1;
    private static boolean starting,attacking;
    private static volatile boolean ready,failed,done,checking;
    private static volatile int phase;
    private static BlockPos tree,workshop;
    private static int wallet;
    public static void register(){if(!Boolean.getBoolean("napuli.worktest"))return;ClientTickEvents.END_CLIENT_TICK.register(mc->{
        if(failed||done){mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.stop();return;}
        if(mc.player==null){if(mc.gui.screen() instanceof TitleScreen&&++titles==60){
            var settings=new LevelSettings("Lavori e risorse test",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("napoli-work-"+System.currentTimeMillis(),settings,SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());
        }return;}
        if(mc.getSingleplayerServer()==null||mc.player.level().dimension()!=NapoliMap.LEVEL)return;
        if(!starting){starting=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);server(mc,p->{
            p.getInventory().clearContent();
            p.level().getServer().getCommands().performPrefixedCommand(p.level().getServer().createCommandSourceStack(),"time set 3500");
            yard(p);WorkResources.regrow(p.level(),NapoliBroadcast.day(p.level()),13000);
            int crops=0;for(var at:BlockPos.betweenClosed(WorkResources.farm().offset(-3,0,-3),WorkResources.farm().offset(3,0,3)))if(p.level().getBlockState(at).is(Blocks.WHEAT))crops++;
            check(crops>=32,"The work field provides actual wheat");
            tree=WorkResources.trees().stream().filter(at->p.level().getBlockState(at).is(Blocks.OAK_LOG)).findFirst().orElseThrow();
            move(p,NapoliEmployment.CLERK_POST,0,0);check(NapoliEmployment.choose(p,"carpenter")==1,"The clerk assigns a real carpenter contract");
            var c=NapoliEmployment.Contract.CARPENTER;move(p,c.clock,0,0);check(NapoliEmployment.work(p,c,c.clock)==1,"Clock-in supplies equipment");
            check(WorkResources.count(p,Items.IRON_AXE)==1,"The starter kit contains one iron axe");
            p.setGameMode(GameType.SURVIVAL);move(p,tree.south(2),180,20);wallet=NapoliStreet.cash(p);ready=true;
        });}if(!ready)return;
        try{
            ticks++;if(last!=phase){last=phase;age=0;attacking=false;Napuli.LOGGER.info("[WORKTEST] phase {}",phase);}age++;
            if(ticks>6000)throw new AssertionError("The acknowledged work cycle timed out at phase "+phase);
            if(phase==0){
                if(mc.level.getBlockState(tree).is(Blocks.OAK_LOG)&&Math.abs(mc.player.getX()-tree.getX()-.5)<3&&Math.abs(mc.player.getZ()-tree.getZ()-.5)<4&&age>30){shot(mc,"lavoro-raccolta");phase=1;}
            }else if(phase==1||phase==2){
                var at=phase==1?tree:tree.above();
                if(!attacking||age%20==0){mc.gameMode.startDestroyBlock(at,Direction.SOUTH);attacking=true;}
                mc.gameMode.continueDestroyBlock(at,Direction.SOUTH);
                if(age%10==0)server(mc,p->{if(p.level().getBlockState(at).isAir())phase++;});
            }else if(phase==3){
                mc.player.setYRot(180);
                double distance=Math.hypot(mc.player.getX()-tree.getX()-.5,mc.player.getZ()-tree.getZ()-.5);
                mc.options.keyUp.setDown(distance>1.1);mc.options.keyJump.setDown(distance>1.1);
                if(age%10==0)server(mc,p->{if(WorkResources.count(p,Items.OAK_LOG)>0){Napuli.LOGGER.info("[WORKTEST] Actual timber picked up");move(p,WorkResources.bench().south(),0,0);phase=4;}});
            }else if(phase==4){
                mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);
                if(age%30==0)mc.player.connection.sendCommand("lavoro risorse");
                if(age%10==0)server(mc,p->{if(WorkResources.count(p,Items.OAK_PLANKS)>=4){
                    check(WorkResources.target(p,NapoliEmployment.Contract.CARPENTER)==null,"The pointer advances to delivery after processing real timber");
                    var c=NapoliEmployment.Contract.CARPENTER;move(p,c.task,0,0);
                    check(NapoliEmployment.work(p,c,c.task)==1&&p.getAttached(NapoliEmployment.WORK).stage()==2,"The actual boards enter the city supply");phase=5;
                }});
            }else if(phase==5){
                if(age==30)shot(mc,"lavoro-consegna");
                if(age%20==0)server(mc,p->{var card=p.getAttached(NapoliEmployment.HOURS);if(card!=null&&card.ticks()>=1000){
                    var c=NapoliEmployment.Contract.CARPENTER;move(p,c.clock,0,0);NapoliEmployment.work(p,c,c.clock);
                    check(NapoliStreet.cash(p)>wallet,"A real timed hour pays the worker");int now=NapoliStreet.cash(p);
                    NapoliEmployment.work(p,c,c.clock);check(NapoliStreet.cash(p)==now,"Repeated clock-out never duplicates pay");
                    for(var job:NapoliEmployment.Contract.values()){
                        for(int x=(job.clock.getX()-12)>>4;x<=(job.clock.getX()+12)>>4;x++)for(int z=(job.clock.getZ()-12)>>4;z<=(job.clock.getZ()+12)>>4;z++)p.level().getChunk(x,z);
                        move(p,job.clock,0,0);WorkCareer.fixtures(p.level());
                        check(p.level().getAttachedOrElse(WorkCareer.STATIONS,Map.of()).containsKey(job.id()),"Every workplace has a physical career point: "+job.id());
                    }
                    WorkCareer.put(p,c,new WorkCareer.Career(100,0,0,4,100,0,true));NapoliStreet.credit(p,100-NapoliStreet.cash(p));p.getInventory().add(new ItemStack(Items.DIAMOND));
                    workshop=WorkCareer.station(p.level(),c);move(p,workshop.south(),180,5);phase=6;
                }});
            }else if(phase==6){
                if(age%30==0)mc.player.connection.sendCommand("lavoro carriera");
                if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>){shot(mc,"lavoro-carriera");mc.gui.setScreen(null);mc.player.connection.sendCommand("lavoro rango");phase=7;}
            }else if(phase==7){
                if(age%10==0)server(mc,p->{if(WorkCareer.get(p,NapoliEmployment.Contract.CARPENTER).rank()==1)phase=8;});
            }else if(phase==8){
                if(age%30==0)mc.player.connection.sendCommand("lavoro potenzia");
                if(age%10==0)server(mc,p->{var c=NapoliEmployment.Contract.CARPENTER;if(WorkCareer.get(p,c).tier()!=1)return;
                    check(WorkResources.count(p,Items.DIAMOND_AXE)==1,"The native command upgrades the actual assigned axe");
                    yard(p);WorkResources.regrow(p.level(),NapoliBroadcast.day(p.level()),12000);
                    check(!p.level().getBlockState(tree).is(Blocks.OAK_LOG),"A cut tree remains cut during daytime");
                    WorkResources.regrow(p.level(),NapoliBroadcast.day(p.level()),13000);
                    check(p.level().getBlockState(tree).is(Blocks.OAK_LOG),"The original work tree regrows at night");
                    move(p,tree.south(4),180,0);phase=9;
                });
            }else if(phase==9&&age>=40){
                shot(mc,"lavoro-ricrescita");
                Napuli.LOGGER.info("[WORKTEST] ALL PASSED: native harvest, pickup, processing, timed pay, eight physical workshops, rank, upgraded tool and night growth");done=true;
            }
        }catch(Throwable e){failed=true;Napuli.LOGGER.error("[WORKTEST] FAILED",e);}
    });}
    private static void yard(ServerPlayer p){for(int x=-37;x<=-34;x++)for(int z=-2;z<=5;z++){var chunk=p.level().getChunk(x,z);NapoliExpansion.generate(p.level(),chunk);CityRailway.fit(p.level(),chunk);WorkResources.fit(p.level(),chunk);}}
    private static void move(ServerPlayer p,BlockPos at,float yaw,float pitch){p.teleportTo(p.level(),at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),yaw,pitch,true);}
    private static void server(Minecraft mc,Consumer<ServerPlayer> action){if(checking)return;checking=true;var server=mc.getSingleplayerServer();server.execute(()->{try{action.accept(server.getPlayerList().getPlayers().getFirst());}catch(Throwable e){failed=true;Napuli.LOGGER.error("[WORKTEST] FAILED on server",e);}finally{checking=false;}});}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.gameRenderer.mainRenderTarget(),1,m->{});}
}
