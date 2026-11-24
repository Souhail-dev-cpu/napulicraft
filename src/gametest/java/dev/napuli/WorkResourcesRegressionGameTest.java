package dev.napuli;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class WorkResourcesRegressionGameTest {
    @GameTest public void growthWaitsForAPlayerInsideTheFutureCrown(GameTestHelper h){
        HousingCapacityGameTest.withPlayers(h,1,players->{
            var city=h.getLevel();var base=h.absolutePos(new BlockPos(0,170,0));clear(h,base);var player=players.getFirst();
            player.snapTo(base.getX()+1.5,base.getY()+3,base.getZ()+.5,0,0);
            h.assertTrue(!WorkResources.grow(city,base)&&city.getBlockState(base).isAir()&&city.getBlockState(base.east().above(3)).isAir(),"Night growth cannot create leaves inside a player's body or leave a partial trunk");
            player.snapTo(base.getX()+5.5,base.getY()+3,base.getZ()+.5,0,0);
            h.assertTrue(WorkResources.grow(city,base),"The pending tree can grow normally after the player leaves its crown");
        });h.succeed();
    }
    @GameTest public void treeGrowthKeepsTheAdjacentPublicBenchAndUnevenGround(GameTestHelper h){
        var city=h.getLevel();var base=h.absolutePos(new BlockPos(0,150,0));clear(h,base);
        var bench=base.east().north();var slope=base.west().above();
        city.setBlock(bench,Blocks.CRAFTING_TABLE.defaultBlockState(),18);
        city.setBlock(slope,Blocks.GRASS_BLOCK.defaultBlockState(),18);
        h.assertTrue(WorkResources.grow(city,base),"The authored work tree can grow beside its public crafting table and sloping ground");
        h.assertTrue(city.getBlockState(bench).is(Blocks.CRAFTING_TABLE)&&city.getBlockState(slope).is(Blocks.GRASS_BLOCK),"Growth does not change the neighboring bench or terrain");
        h.assertTrue(city.getBlockState(base).is(Blocks.OAK_LOG)&&city.getBlockState(base.above(3)).is(Blocks.OAK_LOG)&&city.getBlockState(base.east().above(4)).is(Blocks.OAK_LEAVES),"The complete harvestable trunk and crown are generated");
        h.succeed();
    }
    @GameTest public void occupiedCrownContainersAndPlayerBuildingRejectGrowthAtomically(GameTestHelper h){
        var city=h.getLevel();var base=h.absolutePos(new BlockPos(0,160,0));clear(h,base);
        var crown=base.east().above(3);var neighbor=base.east();
        try {
            city.setBlock(crown,Blocks.EMERALD_BLOCK.defaultBlockState(),18);
            h.assertTrue(!WorkResources.grow(city,base)&&city.getBlockState(base).isAir()&&city.getBlockState(crown).is(Blocks.EMERALD_BLOCK),"An occupied crown cannot be overwritten or leave a partial trunk");
            city.setBlock(crown,Blocks.AIR.defaultBlockState(),18);city.setBlock(neighbor,Blocks.CHEST.defaultBlockState(),18);
            h.assertTrue(!WorkResources.grow(city,base)&&city.getBlockState(base).isAir()&&city.getBlockEntity(neighbor)!=null,"A neighboring container stays protected even outside the generated trunk and leaves");
            city.setBlock(neighbor,Blocks.CRAFTING_TABLE.defaultBlockState(),18);CityProperty.placed(city,neighbor);
            h.assertTrue(!WorkResources.grow(city,base)&&city.getBlockState(base).isAir()&&city.getBlockState(neighbor).is(Blocks.CRAFTING_TABLE),"A player's own bench still prevents growth around their construction");
        } finally {CityProperty.forget(city,neighbor);}
        h.succeed();
    }
    private static void clear(GameTestHelper h,BlockPos base){
        var city=h.getLevel();for(var at:BlockPos.betweenClosed(base.offset(-2,0,-2),base.offset(2,5,2)))city.setBlock(at,Blocks.AIR.defaultBlockState(),18);
    }
}
