package dev.napuli;

import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.LevelChunk;

public final class EntranceGameTest {
    @GameTest public void newDistrictHomesAndShopsHaveClosedDoorFrames(GameTestHelper h){
        var city=h.getLevel();for(var house:java.util.stream.Stream.concat(DistrictLife.homes().stream(),DistrictLife.districts().stream().map(DistrictLife.District::business)).toList()){
            generate(city,house);var door=house.entrance().north();assertClosedFrame(h,door);
            h.assertTrue(city.getBlockState(door).getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&city.getBlockState(door.above()).getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER,"Door has both halves: "+house.id());
        }h.succeed();
    }
    @GameTest public void oldWideOpeningIsRepairedOnceWithoutChangingDoorState(GameTestHelper h){
        oldOpening(h,fixture->{var city=h.getLevel();var door=fixture.door();var before=city.getBlockState(door).setValue(DoorBlock.OPEN,true).setValue(DoorBlock.HINGE,DoorHingeSide.RIGHT);
            city.setBlock(door,before,18);city.setBlock(door.above(),before.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),18);
            h.assertTrue(NapoliExpansion.repairEntrances(city,fixture.chunk())==7,"Only the seven empty masonry blocks surrounding the existing door are restored");assertFrame(h,door,fixture.wall());
            h.assertTrue(city.getBlockState(door).equals(before)&&city.getBlockState(door.above()).equals(before.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER)),"An open door keeps its type, hinge and state");
            city.setBlock(door.west(),Blocks.AIR.defaultBlockState(),18);
            h.assertTrue(NapoliExpansion.repairEntrances(city,fixture.chunk())==0&&city.isEmptyBlock(door.west()),"Completed migration does not undo subsequent player demolition");
        });h.succeed();
    }
    @GameTest public void migrationPreservesCustomizedEntrances(GameTestHelper h){
        oldOpening(h,fixture->{var city=h.getLevel();var custom=fixture.door().above(2);city.setBlock(custom,Blocks.GLASS.defaultBlockState(),18);CityProperty.placed(city,custom);
            try{h.assertTrue(NapoliExpansion.repairEntrances(city,fixture.chunk())==0&&city.getBlockState(custom).is(Blocks.GLASS)&&city.isEmptyBlock(fixture.door().west()),"A customized entrance is preserved as a whole");}
            finally{CityProperty.forget(city,custom);}
        });h.succeed();
    }
    @GameTest public void migrationPreservesPlayerReplacementDoors(GameTestHelper h){
        oldOpening(h,fixture->{var city=h.getLevel();var door=fixture.door();var birch=Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.FACING,net.minecraft.core.Direction.SOUTH);city.setBlock(door,birch,18);city.setBlock(door.above(),birch.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),18);
            h.assertTrue(NapoliExpansion.repairEntrances(city,fixture.chunk())==0&&city.getBlockState(door).is(Blocks.BIRCH_DOOR)&&city.isEmptyBlock(door.above(2)),"A replacement door and its surrounding renovation are not overwritten");
        });h.succeed();
    }
    @GameTest public void olderResidentialHomesAlsoReceiveTheirMissingLintel(GameTestHelper h){
        var city=h.getLevel();var home=CityLivingHomes.PUBLIC_HOMES.getFirst();CityLivingHomes.buildHouse(city,home);var door=home.entrance().north();var chunk=city.getChunkAt(door);var marker=((AttachmentTarget)chunk).removeAttached(NapoliExpansion.ENTRANCES_FIXED);var lintel=city.getBlockState(door.above(2));
        try{city.setBlock(door.above(2),Blocks.AIR.defaultBlockState(),18);NapoliExpansion.repairEntrances(city,chunk);h.assertTrue(city.getBlockState(door.above(2)).is(lintel.getBlock()),"The old doorway closes using its current authored facade material");}
        finally{city.setBlock(door.above(2),lintel,18);restore(chunk,marker);}h.succeed();
    }
    @GameTest(maxTicks=100) public void repairedDoorStillOpensAndAllowsNpcNavigation(GameTestHelper h){
        var city=h.getLevel();var home=DistrictLife.homes().getLast();generate(city,home);var source=home.entrance().north();
        // Copy the real generated doorway into the ticking GameTest corridor; remote city chunks do not tick here.
        for(var p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(1,1,0)),h.absolutePos(new BlockPos(3,1,6))))city.setBlock(p,Blocks.STONE.defaultBlockState(),18);
        for(var p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(1,2,0)),h.absolutePos(new BlockPos(3,4,6))))city.setBlock(p,Blocks.AIR.defaultBlockState(),18);
        for(int x:new int[]{1,3})for(int y=2;y<=4;y++)for(int z=0;z<=6;z++)city.setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.BRICKS.defaultBlockState(),18);
        var door=h.absolutePos(new BlockPos(2,2,3));
        for(int dx=-1;dx<=1;dx++)for(int dy=0;dy<=2;dy++)city.setBlock(door.offset(dx,dy,0),city.getBlockState(source.offset(dx,dy,0)),18);
        var npc=NapoliResident.spawn(city,NapoliResident.Role.CIVIC_MAN,h.absolutePos(new BlockPos(2,2,5)),0);
        var state=city.getBlockState(door);((DoorBlock)state.getBlock()).setOpen(npc,city,state,door,true);
        h.runAfterDelay(3,()->{try{var path=npc.getNavigation().createPath(h.absolutePos(new BlockPos(2,2,1)),0);h.assertTrue(city.getBlockState(door).getValue(DoorBlock.OPEN)&&path!=null&&path.canReach(),"An NPC can plan through the real two-block doorway with its solid lintel");}
            finally{npc.discard();}h.succeed();});
    }
    private record Fixture(BlockPos door,Block wall,LevelChunk chunk){}
    private static void oldOpening(GameTestHelper h,Consumer<Fixture> body){
        opening(h,true,body);
    }
    @GameTest public void finishedPortalRepairsItsOwnStoneInsteadOfCopyingThePlinth(GameTestHelper h){
        opening(h,false,fixture->{h.assertTrue(NapoliExpansion.repairEntrances(h.getLevel(),fixture.chunk())==7,"The finished portal also restores seven missing blocks");assertFrame(h,fixture.door(),Blocks.POLISHED_DEEPSLATE);});h.succeed();
    }
    private static void opening(GameTestHelper h,boolean legacy,Consumer<Fixture> body){
        var city=h.getLevel();var home=DistrictLife.homes().getFirst();generate(city,home);var door=home.entrance().north();var chunk=city.getChunkAt(door);var wall=city.getBlockState(door.above(3)).getBlock();var marker=((AttachmentTarget)chunk).removeAttached(NapoliExpansion.ENTRANCES_FIXED);var states=new LinkedHashMap<BlockPos,BlockState>();
        for(var p:List.of(door.west(2),door.east(2))){states.put(p,city.getBlockState(p));city.setBlock(p,(legacy?wall:Blocks.POLISHED_ANDESITE).defaultBlockState(),18);}
        for(int dx=-1;dx<=1;dx++)for(int dy=0;dy<=2;dy++){var p=door.offset(dx,dy,0);states.put(p,city.getBlockState(p));if(dx!=0||dy==2)city.setBlock(p,Blocks.AIR.defaultBlockState(),18);}
        try{body.accept(new Fixture(door,wall,chunk));}finally{states.forEach((p,s)->city.setBlock(p,s,18));restore(chunk,marker);}
    }
    private static void restore(LevelChunk chunk,Boolean marker){if(marker==null)((AttachmentTarget)chunk).removeAttached(NapoliExpansion.ENTRANCES_FIXED);else ((AttachmentTarget)chunk).setAttached(NapoliExpansion.ENTRANCES_FIXED,marker);}
    private static void generate(ServerLevel city,NapoliMergellina.House home){for(int x=home.x()>>4;x<=(home.x()+21)>>4;x++)for(int z=home.z()>>4;z<=(home.z()+23)>>4;z++)NapoliExpansion.generate(city,city.getChunk(x,z));}
    private static void assertFrame(GameTestHelper h,BlockPos door,Block wall){for(int dx=-1;dx<=1;dx++)for(int dy=0;dy<=2;dy++)if(dx!=0||dy==2)h.assertTrue(h.getLevel().getBlockState(door.offset(dx,dy,0)).is(wall),"Doorframe at "+door.offset(dx,dy,0)+" expected="+wall+" actual="+h.getLevel().getBlockState(door.offset(dx,dy,0)));}
    private static void assertClosedFrame(GameTestHelper h,BlockPos door){
        for(int dx=-1;dx<=1;dx++)for(int dy=0;dy<=2;dy++)if(dx!=0||dy==2){var p=door.offset(dx,dy,0);var state=h.getLevel().getBlockState(p);
            h.assertTrue(state.isFaceSturdy(h.getLevel(),p,net.minecraft.core.Direction.NORTH)&&state.isFaceSturdy(h.getLevel(),p,net.minecraft.core.Direction.SOUTH),"The generated masonry closes the frame on both faces: "+p+" / "+state);}
    }
}
