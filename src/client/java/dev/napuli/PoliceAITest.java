package dev.napuli;

import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real player packets verify vandalism, construction provenance, arrest and a roof pursuit. */
public final class PoliceAITest {
    private static volatile int phase, clientStep;
    private static volatile boolean finished;
    private static int titleTicks, elapsed, phaseTicks, frames;
    private static NapoliResident officer;
    private static boolean usedPickaxe, climbed;
    private static final BlockPos FURNITURE = new BlockPos(-402,65,-398), BARRIER = new BlockPos(-406,65,-400);
    private static final Vec3 INSIDE = new Vec3(-405.5,65,-396.5);

    public static void register() {
        if (!Boolean.getBoolean("napuli.policeaitest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(PoliceAITest::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(PoliceAITest::serverTick);
    }
    private static void clientTick(Minecraft mc) {
        if (finished) { if (mc.gameMode != null) mc.gameMode.stopDestroyBlock(); mc.stop(); return; }
        if (mc.player == null) {
            if (mc.gui.screen() instanceof TitleScreen) {
                if (++titleTicks == 1) { mc.getLanguageManager().setSelected("it_it"); mc.reloadResourcePacks(); }
                if (titleTicks == 80) {
                    var settings = new LevelSettings("napuli-police-ai-test",GameType.SURVIVAL,
                            new LevelSettings.DifficultySettings(Difficulty.NORMAL,false,false),true,WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel("napuli-police-ai-test-"+System.currentTimeMillis(),settings,
                            SelectWorldScreen.TEST_OPTIONS,WorldPresets::createTestWorldDimensions,new TitleScreen());
                }
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
        if (mc.level.dimension() != NapoliMap.LEVEL) return;
        if (phase == 1 && mc.player.getMainHandItem().is(Items.DIRT)
                && mc.player.position().distanceToSqr(new Vec3(-402.5,65,-400.5))<.5
                && mc.level.getBlockState(FURNITURE).isAir() && !mc.level.getBlockState(FURNITURE.below()).isAir()
                && (clientStep!=1||++frames%15==0)) {
            place(mc,FURNITURE.below()); clientStep = 1;
        }
        if ((phase == 2 || phase == 3) && mc.player.getMainHandItem().is(Items.IRON_PICKAXE) && !mc.level.getBlockState(FURNITURE).isAir()) {
            if (clientStep != phase) { mc.gameMode.startDestroyBlock(FURNITURE,Direction.NORTH); clientStep = phase; }
            else mc.gameMode.continueDestroyBlock(FURNITURE,Direction.NORTH);
        }
        if (phase == 4 && clientStep != 4 && ++frames >= 15) {
            Screenshot.grab(mc.gameDirectory,"police-vandalism.png",mc.gameRenderer.mainRenderTarget(),1,message -> {}); clientStep = 4;
        }
        if ((phase == 5 || phase == 6) && clientStep != phase && mc.player.getMainHandItem().is(Items.STONE)
                && mc.player.position().distanceToSqr(INSIDE) < .5) {
            BlockPos support = phase == 5 ? BARRIER.below() : BARRIER;
            if (!mc.level.getBlockState(support).isAir()) { place(mc,support); clientStep = phase; }
        }
        if (phase == 9 && clientStep != 9 && ++frames >= 30) {
            Screenshot.grab(mc.gameDirectory,"police-climbed-to-roof.png",mc.gameRenderer.mainRenderTarget(),1,message -> {}); clientStep = 9;
        }
    }
    private static void place(Minecraft mc, BlockPos support) {
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(support.getX()+.5,support.getY()+1,support.getZ()+.5),Direction.UP,support,false));
    }
    private static void clearArena(ServerLevel city) {
        for (int x=-416;x<=-388;x++) for (int z=-414;z<=-386;z++) {
            city.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),3);
            for (int y=65;y<=79;y++) city.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        // Preserve the disposable arena while newly visited district chunks finish their repairs.
        for(int x=-416;x<=-388;x+=16)for(int z=-414;z<=-386;z+=16)CityProperty.placed(city,new BlockPos(x,64,z));
    }
    private static void serverTick(MinecraftServer server) {
        if (finished || server.getPlayerList().getPlayers().isEmpty()) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        try {
            if (++elapsed > 1800) throw new IllegalStateException("Timed out at phase "+phase+", client "+clientStep);
            phaseTicks++;
            if (officer != null) {
                usedPickaxe |= officer.getMainHandItem().is(Items.IRON_PICKAXE);
                climbed |= phase == 8 && officer.onClimbable() && officer.getY() > 66;
            }
            switch (phase) {
                case 0 -> {
                    if (player.level().dimension() != NapoliMap.LEVEL) return;
                    var city = player.level();
                    PolicePatrols.maintain(city);
                    var patrols=city.getEntities(Napuli.RESIDENT,PolicePatrols::managed);
                    if(patrols.size()<2)return;
                    check(patrols.stream().map(NapoliResident::getUUID).distinct().count()==patrols.size(),"District patrols have unique slots");
                    check(patrols.stream().noneMatch(NapoliResident::shouldBeSaved),"Temporary district patrols cannot duplicate across saves");
                    Napuli.LOGGER.info("[POLICEAITEST] Distributed street patrols verified: {} officers",patrols.size());
                    // Keep the following native breach and arrest fixture focused on its own officer.
                    CityLifeConfig.settings.policePatrolLimit=0;patrols.forEach(NapoliResident::discard);PoliceDispatch.invalidate(city);
                    clearArena(city);
                    var owners=new java.util.HashMap<>(city.getAttachedOrElse(NapoliProperties.OWNERS,java.util.Map.of()));
                    for(var home:DistrictLife.homes())if(home.contains(FURNITURE)||home.contains(BARRIER)||home.contains(BlockPos.containing(INSIDE)))owners.put(home.id(),player.getUUID());
                    city.setAttached(NapoliProperties.OWNERS,java.util.Map.copyOf(owners));
                    CityProperty.recordHome(city,new BlockPos(-416,64,-414),new BlockPos(-388,79,-386));
                    player.teleportTo(city,-402.5,65,-400.5,Set.of(),0,0,true);
                    player.getInventory().clearContent(); player.getInventory().setItem(0,new ItemStack(Items.DIRT));
                    player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Napuli.CORNICELLO));
                    NapoliStreet.credit(player,100-NapoliStreet.cash(player));
                    officer = NapoliResident.spawn(city,NapoliResident.Role.CARABINIERE,new BlockPos(-411,65,-401),0);
                    next(1);
                }
                case 1 -> {
                    if (!player.level().getBlockState(FURNITURE).is(Blocks.DIRT)) return;
                    check(CityProperty.playerPlaced(player.level(),FURNITURE),"Real placement packet records player construction");
                    player.getInventory().setItem(0,new ItemStack(Items.IRON_PICKAXE)); next(2);
                }
                case 2 -> {
                    if (!player.level().getBlockState(FURNITURE).isAir()) return;
                    check(!Carabinieri.isWanted(player),"Removing one's construction is legal");
                    check(!CityProperty.playerPlaced(player.level(),FURNITURE),"Successful break clears provenance");
                    player.level().setBlock(FURNITURE,Blocks.GLASS.defaultBlockState(),3); next(3);
                }
                case 3 -> {
                    if (!player.level().getBlockState(FURNITURE).isAir()) return;
                    check(Carabinieri.isWanted(player),"Breaking home furniture triggers vandalism through real player packets"); next(4);
                }
                case 4 -> {
                    if (clientStep != 4) return;
                    var city = player.level();
                    for (int z=-410;z<=-389;z++) {
                        for (int y=65;y<=69;y++) { city.setBlock(new BlockPos(-407,y,z),Blocks.STONE.defaultBlockState(),3); city.setBlock(new BlockPos(-405,y,z),Blocks.STONE.defaultBlockState(),3); }
                        city.setBlock(new BlockPos(-406,67,z),Blocks.STONE.defaultBlockState(),3);
                    }
                    for (int y=65;y<=67;y++) for (int z:new int[]{-410,-389}) city.setBlock(new BlockPos(-406,y,z),Blocks.STONE.defaultBlockState(),3);
                    player.teleportTo(city,INSIDE.x,INSIDE.y,INSIDE.z,Set.of(),180,0,true);
                    PoliceTactics.stop(officer); officer.snapTo(-405.5,65,-407.5,0,0); officer.setDeltaMovement(Vec3.ZERO);
                    player.getInventory().setItem(0,new ItemStack(Items.STONE,2)); next(5);
                }
                case 5 -> {
                    if (!player.level().getBlockState(BARRIER).is(Blocks.STONE)) return;
                    check(CityProperty.playerPlaced(player.level(),BARRIER),"Bottom barricade tracked"); next(6);
                }
                case 6 -> {
                    if (!player.level().getBlockState(BARRIER.above()).is(Blocks.STONE)) return;
                    check(CityProperty.playerPlaced(player.level(),BARRIER.above()),"Top barricade tracked"); next(7);
                }
                case 7 -> {
                    if (!player.level().getBlockState(BARRIER).isAir() || !player.level().getBlockState(BARRIER.above()).isAir()) {
                        check(Carabinieri.isWanted(player),"A sealed barricade prevents arrest through the wall"); return;
                    }
                    if (Carabinieri.isWanted(player)) return;
                    check(usedPickaxe,"Actual pursuit used the iron pickaxe");
                    check(NapoliStreet.cash(player)==85,"Vandalism receives the standard 15 euro fine");
                    check(officer.getMainHandItem().is(Napuli.BATON),"Mining equipment restored after arrest");
                    var city=player.level(); clearArena(city);
                    for(int y=65;y<=73;y++) {
                        city.setBlock(new BlockPos(-407,y,-402),Blocks.STONE.defaultBlockState(),3);
                        if(y<=72) city.setBlock(new BlockPos(-406,y,-402),Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST),3);
                    }
                    for(int x=-406;x<=-400;x++) for(int z=-404;z<=-400;z++) if(x!=-406||z!=-402)
                        city.setBlock(new BlockPos(x,71,z),Blocks.STONE.defaultBlockState(),3);
                    officer.snapTo(-411.5,65,-402.5,0,0); officer.setDeltaMovement(Vec3.ZERO);
                    player.teleportTo(city,-401.5,72,-401.5,Set.of(),90,45,true);
                    Carabinieri.crime(player,Carabinieri.Crime.ASSAULT);
                    check(Carabinieri.isWanted(player),"The reported incident identifies the roof before the pursuit starts");next(8);
                }
                case 8 -> {
                    if(officer.getY()<71.7) return;
                    check(climbed,"Officer used the actual ladder to reach the roof");
                    officer.setNoAi(true); next(9);
                }
                case 9 -> {
                    if(clientStep!=9) return;
                    Napuli.LOGGER.info("[POLICEAITEST] ALL PASSED: construction tracking, vandalism, iron-pickaxe breach, lawful arrest and roof ladder pursuit");
                    finished=true;
                }
            }
        } catch(Throwable failure) { Napuli.LOGGER.error("[POLICEAITEST] FAILED at phase "+phase,failure); finished=true; }
    }
    private static void next(int value) { phaseTicks=0; frames=0; phase=value; Napuli.LOGGER.info("[POLICEAITEST] phase {}",value); }
    private static void check(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
