package dev.napuli;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public final class PoliceNavigationGameTest {
    private static int sequence;
    private static void arena(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            helper.setBlock(x, 0, z, Blocks.STONE);
            for (int y = 1; y <= 12; y++) helper.setBlock(x, y, z, x == 0 || x == 15 || z == 0 || z == 15 ? Blocks.BEDROCK : Blocks.AIR);
        }
    }
    private static NapoliResident officer(GameTestHelper helper, Vec3 at) {
        return NapoliResident.spawn(helper.getLevel(), NapoliResident.Role.CARABINIERE,
                helper.absolutePos(BlockPos.containing(at)), 0);
    }
    private record Suspect(ServerPlayer player, EmbeddedChannel channel) {
        void close() { player.level().getServer().getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }
    private static Suspect suspect(GameTestHelper helper, Vec3 at) {
        var level = helper.getLevel(); var server = level.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "PoliceTest" + sequence++);
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        player.setGameMode(GameType.SURVIVAL); player.setPos(helper.absoluteVec(at)); player.setNoGravity(true);
        return new Suspect(player, channel);
    }

    @GameTest(structure="napuli:police_arena", maxTicks=500)
    public void preferWalkingAroundABarricade(GameTestHelper helper) {
        arena(helper);
        for (int z = 1; z <= 10; z++) for (int y = 1; y <= 3; y++) {
            helper.setBlock(7, y, z, Blocks.STONE);
            CityProperty.placed(helper.getLevel(), helper.absolutePos(new BlockPos(7, y, z)));
        }
        var npc = officer(helper, new Vec3(3.5,1,5.5));
        var suspect = suspect(helper, new Vec3(12.5,1,5.5));
        helper.runAtTickTime(499, suspect::close);
        helper.onEachTick(() -> {
            PoliceTactics.pursue(npc, suspect.player(), helper.getLevel().getGameTime());
            if (npc.distanceToSqr(suspect.player()) >= 2) return;
            for (int z = 1; z <= 10; z++) helper.assertTrue(helper.getBlockState(new BlockPos(7,1,z)).is(Blocks.STONE), "Valid detour leaves the barricade intact");
            PoliceTactics.stop(npc); suspect.close(); helper.succeed();
        });
    }

    @GameTest(structure="napuli:police_arena", maxTicks=300)
    public void openWoodenDoorsInsteadOfGettingStuck(GameTestHelper helper) {
        arena(helper);
        for (int z = 2; z <= 13; z++) for (int y = 1; y <= 4; y++) { helper.setBlock(2,y,z,Blocks.STONE); helper.setBlock(4,y,z,Blocks.STONE); }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        helper.setBlock(new BlockPos(3,1,7), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(3,2,7), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        var npc = officer(helper, new Vec3(3.5,1,4.5)); var goal = helper.absoluteVec(new Vec3(3.5,1,11.5));
        helper.runAfterDelay(3, () -> helper.assertTrue(npc.getNavigation().moveTo(goal.x,goal.y,goal.z,1.3), "Door route can be planned"));
        boolean[] opened = {false};
        helper.runAtTickTime(298, () -> Napuli.LOGGER.info("[POLICETEST] door position={} goal={} path={} next={}",npc.position(),goal,npc.getNavigation().getPath(),npc.getNavigation().isDone()?"done":npc.getNavigation().getPath().getNextEntityPos(npc)));
        helper.onEachTick(() -> {
            if (helper.getBlockState(new BlockPos(3,1,7)).getValue(BlockStateProperties.OPEN)) opened[0] = true;
            if (npc.position().distanceToSqr(goal) >= 2.25) return;
            helper.assertTrue(opened[0], "Officer physically opened the door"); helper.succeed();
        });
    }

    @GameTest(structure="napuli:police_arena", maxTicks=600)
    public void climbALadderToAnUpperFloorAndReturn(GameTestHelper helper) {
        ladderArena(helper);
        var npc = officer(helper,new Vec3(2.5,1,5.5)); var upper = helper.absoluteVec(new Vec3(10.5,8,5.5));
        var lower = helper.absoluteVec(new Vec3(2.5,1,5.5));
        helper.runAfterDelay(3, () -> helper.assertTrue(npc.getNavigation().moveTo(upper.x,upper.y,upper.z,1.3),"Vertical ladder route exists"));
        boolean[] reachedUpper = {false}, descendingOnLadder = {false};
        helper.runAtTickTime(598, () -> Napuli.LOGGER.info("[POLICETEST] ladder position={} upper={} climbed={} path={} next={} onLadder={}",npc.position(),upper,reachedUpper[0],npc.getNavigation().getPath(),npc.getNavigation().isDone()?"done":npc.getNavigation().getPath().getNextEntityPos(npc),npc.onClimbable()));
        helper.onEachTick(() -> {
            if (!reachedUpper[0]) {
                if (npc.position().distanceToSqr(upper) >= 2.25 || npc.getY() < upper.y - .5) return;
                reachedUpper[0] = true;
                helper.assertTrue(npc.getNavigation().moveTo(lower.x,lower.y,lower.z,1.3),"Return route exists");
                return;
            }
            if (npc.onClimbable() && npc.getY() < upper.y - 1 && npc.getY() > lower.y + 1) descendingOnLadder[0] = true;
            if (npc.position().distanceToSqr(lower) >= 2.25 || npc.getY() > lower.y + .5) return;
            helper.assertTrue(descendingOnLadder[0],"Resident descends using the ladder"); helper.succeed();
        });
    }

    private static void ladderArena(GameTestHelper helper) {
        arena(helper);
        for (int y=1;y<=9;y++) {
            helper.setBlock(5,y,5,Blocks.STONE);
            if(y<=8) helper.setBlock(new BlockPos(6,y,5),Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST));
        }
        for(int x=6;x<=12;x++) for(int z=4;z<=7;z++) if(x!=6||z!=5) helper.setBlock(x,7,z,Blocks.STONE);
    }

    @GameTest(structure="napuli:police_arena", maxTicks=600)
    public void breachABlockedLadderExitWithoutSlidingDown(GameTestHelper helper) {
        ladderArena(helper);
        helper.setBlock(6,7,5,Blocks.OAK_PLANKS);
        CityProperty.placed(helper.getLevel(),helper.absolutePos(new BlockPos(6,7,5)));
        var npc=officer(helper,new Vec3(2.5,1,5.5)); var suspect=suspect(helper,new Vec3(10.5,8,5.5));
        helper.runAtTickTime(599,suspect::close);
        boolean[] minedFromLadder={false};
        helper.onEachTick(() -> {
            PoliceTactics.pursue(npc,suspect.player(),helper.getLevel().getGameTime());
            minedFromLadder[0]|=npc.onClimbable()&&npc.getMainHandItem().is(Items.IRON_PICKAXE);
            if(npc.getY()<helper.absoluteVec(new Vec3(0,8,0)).y-.5 || npc.distanceToSqr(suspect.player())>=3) return;
            helper.assertTrue(minedFromLadder[0]&&helper.getBlockState(new BlockPos(6,7,5)).isAir(),"Officer mines the blocked exit while holding the ladder");
            PoliceTactics.stop(npc); suspect.close(); helper.succeed();
        });
    }

    @GameTest(structure="napuli:police_arena")
    public void cannotArrestThroughALowWall(GameTestHelper helper) {
        arena(helper); var npc=officer(helper,new Vec3(3.5,1,4.5)); var suspect=suspect(helper,new Vec3(3.5,1,6.3));
        try {
            helper.setBlock(3,1,5,Blocks.STONE);
            helper.assertTrue(!PoliceTactics.canGrab(npc,suspect.player()),"Seeing a head over a wall is not enough for an arrest");
            helper.setBlock(3,1,5,Blocks.AIR);
            helper.assertTrue(PoliceTactics.canGrab(npc,suspect.player()),"Clear reach allows an arrest");
        } finally { suspect.close(); }
        helper.succeed();
    }

    @GameTest(structure="napuli:police_arena", maxTicks=400)
    public void mineASealedPassageWithAnIronPickaxe(GameTestHelper helper) {
        arena(helper);
        for (int z = 2; z <= 13; z++) for (int y = 1; y <= 5; y++) { helper.setBlock(2,y,z,Blocks.STONE); helper.setBlock(4,y,z,Blocks.STONE); }
        for (int z = 2; z <= 13; z++) helper.setBlock(3,3,z,Blocks.STONE);
        for (int y = 1; y <= 3; y++) { helper.setBlock(3,y,2,Blocks.STONE); helper.setBlock(3,y,13,Blocks.STONE); }
        for (int y = 1; y <= 2; y++) {
            helper.setBlock(3,y,7,Blocks.STONE); CityProperty.placed(helper.getLevel(),helper.absolutePos(new BlockPos(3,y,7)));
        }
        var npc = officer(helper,new Vec3(3.5,1,4.5)); var suspect = suspect(helper,new Vec3(3.5,1,10.5));
        helper.runAtTickTime(399,suspect::close);
        boolean[] usedPickaxe = {false};
        helper.onEachTick(() -> {
            PoliceTactics.pursue(npc,suspect.player(),helper.getLevel().getGameTime());
            if (npc.getMainHandItem().is(Items.IRON_PICKAXE)) usedPickaxe[0] = true;
            if (npc.distanceToSqr(suspect.player()) >= 2) return;
            helper.assertTrue(usedPickaxe[0],"Officer actually equipped an iron pickaxe");
            helper.assertTrue(helper.getBlockState(new BlockPos(3,1,7)).isAir() && helper.getBlockState(new BlockPos(3,2,7)).isAir(),"Barricade is removed");
            int drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(10)).stream()
                    .filter(item -> item.getItem().is(Items.COBBLESTONE)).mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(drops >= 2,"Iron pickaxe produces the correct stone drops");
            PoliceTactics.stop(npc); helper.assertTrue(npc.getMainHandItem().is(Napuli.BATON),"Normal equipment restored");
            suspect.close(); helper.succeed();
        });
    }

    @GameTest(structure="napuli:police_arena")
    public void protectNaturalBlocksAndRespectIronToolLimits(GameTestHelper helper) {
        arena(helper); var npc = officer(helper,new Vec3(3.5,1,4.5));
        var pos = helper.absolutePos(new BlockPos(3,1,5));
        helper.setBlock(3,1,5,Blocks.STONE);
        helper.assertTrue(!PoliceTactics.canBreach(npc,pos),"City and natural blocks are not barricades");
        CityProperty.placed(helper.getLevel(),pos);
        helper.assertTrue(PoliceTactics.canBreach(npc,pos),"Player stone is breakable");
        helper.setBlock(3,1,5,Blocks.OBSIDIAN); helper.assertTrue(!PoliceTactics.canBreach(npc,pos),"Iron cannot harvest obsidian");
        helper.setBlock(3,1,5,Blocks.BEDROCK); helper.assertTrue(!PoliceTactics.canBreach(npc,pos),"Unbreakable blocks stay intact");
        helper.setBlock(3,1,5,Blocks.CHEST); helper.assertTrue(!PoliceTactics.canBreach(npc,pos),"Container inventories are preserved");
        helper.setBlock(3,1,5,Blocks.STONE);
        var rules = helper.getLevel().getGameRules(); boolean previous = rules.get(GameRules.MOB_GRIEFING);
        try { rules.set(GameRules.MOB_GRIEFING,false,helper.getLevel().getServer()); helper.assertTrue(!PoliceTactics.canBreach(npc,pos),"mobGriefing disables mining"); }
        finally { rules.set(GameRules.MOB_GRIEFING,previous,helper.getLevel().getServer()); }
        helper.succeed();
    }

    @GameTest public void vandalismDistinguishesHomesFromPlayerConstruction(GameTestHelper helper) {
        var level = helper.getLevel();
        var home = new BlockPos(-65,64,-55);
        helper.assertTrue(CityProperty.isVandalism(level,home,Blocks.OAK_PLANKS.defaultBlockState()),"Breaking a resident's furniture is vandalism");
        helper.assertTrue(CityProperty.home(level,new BlockPos(213,65,-45)),"Municipio houses are indexed in existing worlds");
        helper.assertTrue(CityProperty.home(level,new BlockPos(-48,65,-134)),"Centro houses are indexed in existing worlds");
        helper.assertTrue(!CityProperty.isVandalism(level,new BlockPos(272,64,-29),Blocks.IRON_ORE.defaultBlockState()),"Quest mine stays legal");
        CityProperty.placed(level,home);
        helper.assertTrue(!CityProperty.isVandalism(level,home,Blocks.OAK_PLANKS.defaultBlockState()),"Player construction is exempt");
        CityProperty.forget(level,home);
        helper.assertTrue(CityProperty.isVandalism(level,home,Blocks.OAK_PLANKS.defaultBlockState()),"Removing construction clears its provenance");
        helper.succeed();
    }
}
