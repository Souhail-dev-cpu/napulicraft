package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.LevelChunk;

public final class StreetGameTest {
    @GameTest public void streetGridHasNoMultiBlockCliffs(GameTestHelper h){
        for(int x=-2038;x<2040;x+=40)for(int z=-814;z<542;z++)checkEdge(h,x,z,x,z+1);
        for(int z=-798;z<540;z+=40)for(int x=-2046;x<2046;x++)checkEdge(h,x,z,x+1,z);
        h.succeed();
    }
    private static void checkEdge(GameTestHelper h,int x,int z,int nx,int nz){
        if(!CityStreets.adjusted(x,z)||!CityStreets.adjusted(nx,nz)||NapoliExpansion.water(x,z)||NapoliExpansion.water(nx,nz))return;
        int a=NapoliExpansion.height(x,z),b=NapoliExpansion.height(nx,nz);
        h.assertTrue(Math.abs(a-b)<=1,"Street joins adjacent levels: "+x+","+z+" ("+a+") -> "+nx+","+nz+" ("+b+")");
    }
    @GameTest public void gradingKeepsMonumentsAndHomeAddressesAtTheirOriginalElevation(GameTestHelper h){
        for(var place:NapoliAtlas.PLACES)h.assertTrue(NapoliExpansion.height(place.x(),place.z())==NapoliExpansion.legacyHeight(place.x(),place.z()),"Monument foundations stay fixed: "+place.id());
        for(int gx=-52;gx<=51;gx++)for(int gz=-21;gz<=13;gz++){int x=gx*40+9,z=gz*40+9;if(!NapoliExpansion.houseAllowed(x,z))continue;
            h.assertTrue(NapoliExpansion.height(x+11,z+24)==NapoliExpansion.legacyHeight(x+11,z+24),"Every generated home's entrance retains its address elevation at "+x+","+z);
        }h.succeed();
    }
    @GameTest public void oldTerraceRoadsAreRegradedWithSolidFoundations(GameTestHelper h){
        var city=h.getLevel();var cliff=cliff();var chunks=legacy(city,cliff);int changed=0;for(var chunk:chunks)changed+=CityStreets.fit(city,chunk);
        h.assertTrue(changed>0,"An actual old plateau discontinuity is reshaped");
        for(int offset=-4;offset<=4;offset++){int x=cliff.getX(),z=cliff.getZ()+offset;if(!CityStreets.adjusted(x,z))continue;int y=NapoliExpansion.height(x,z);var floor=new BlockPos(x,y,z);
            h.assertTrue(!city.getBlockState(floor).getCollisionShape(city,floor).isEmpty()&&!city.getBlockState(floor.below()).isAir(),"Each ramp step has support: "+floor);
            h.assertTrue(city.getBlockState(floor.above()).getCollisionShape(city,floor.above()).isEmpty()&&city.getBlockState(floor.above(2)).getCollisionShape(city,floor.above(2)).isEmpty(),"Old tuff cliffs and floating paving no longer block the road: "+floor);
        }
        for(var chunk:chunks)h.assertTrue(CityStreets.fit(city,chunk)==0,"Saved street grading is applied once");h.succeed();
    }
    @GameTest public void streetMigrationPreservesPlayerConstruction(GameTestHelper h){
        var city=h.getLevel();var cliff=cliff();var chunks=legacy(city,cliff);var custom=cliff.atY(Math.max(NapoliExpansion.height(cliff.getX(),cliff.getZ()),NapoliExpansion.legacyHeight(cliff.getX(),cliff.getZ()))+1);
        city.setBlock(custom,Blocks.EMERALD_BLOCK.defaultBlockState(),18);CityProperty.placed(city,custom);
        try{for(var chunk:chunks)CityStreets.fit(city,chunk);h.assertTrue(city.getBlockState(custom).is(Blocks.EMERALD_BLOCK),"A player's construction is never excavated for a street");}
        finally{CityProperty.forget(city,custom);city.setBlock(custom,Blocks.AIR.defaultBlockState(),18);}h.succeed();
    }
    @GameTest public void oldBusStopSignAndPostFollowTheNewStreetLevel(GameTestHelper h){
        var city=h.getLevel();var stop=CityNetwork.stops().stream().filter(s->CityNetwork.lines().stream().anyMatch(l->l.stops().contains(s.id())))
                .filter(s->NapoliExpansion.legacyHeight(s.street().getX(),s.street().getZ())+2!=s.sign().getY()).filter(s->CityStreets.adjusted(s.sign().getX(),s.sign().getZ())).findFirst().orElseThrow();
        var lines=CityNetwork.lines().stream().filter(l->l.stops().contains(stop.id())).map(CityNetwork.Line::id).reduce((a,b)->a+" "+b).orElse("");
        int old=NapoliExpansion.legacyHeight(stop.street().getX(),stop.street().getZ())+1;var oldSign=new BlockPos(stop.sign().getX(),old+1,stop.sign().getZ());var chunk=city.getChunkAt(oldSign);NapoliExpansion.generate(city,chunk);
        city.setBlock(stop.sign(),Blocks.AIR.defaultBlockState(),18);city.setBlock(oldSign.below(),Blocks.OAK_FENCE.defaultBlockState(),18);city.setBlock(oldSign,Blocks.OAK_SIGN.defaultBlockState(),18);
        var text=net.minecraft.world.level.block.entity.SignText.EMPTY.asMutable();int i=0;for(String row:List.of("FERMATA",stop.name(),lines,"Tasto destro"))text.setLine(i++,net.minecraft.network.chat.Component.literal(row));
        var saved=text.asImmutable().withGlowingText(true);((net.minecraft.world.level.block.entity.SignBlockEntity)city.getBlockEntity(oldSign)).setText(saved,net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
        ((AttachmentTarget)chunk).removeAttached(CityStreets.GRADED);((AttachmentTarget)chunk).setAttached(CityRailway.FITTED,3);CityStreets.fit(city,chunk);
        h.assertTrue(city.getBlockEntity(stop.sign()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign&&sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).equals(saved),"An authored stop moves to the ramp without losing its text or glow");
        h.assertTrue(city.getBlockState(stop.sign().below()).is(Blocks.OAK_FENCE),"The stop's support is moved with its sign");h.succeed();
    }
    @GameTest public void standingPlayersAreMovedOntoTheRepairedRoad(GameTestHelper h){
        var city=h.getLevel();var cliff=cliff();var chunks=legacy(city,cliff);var at=NapoliExpansion.height(cliff.getX(),cliff.getZ())==NapoliExpansion.legacyHeight(cliff.getX(),cliff.getZ())?cliff.south():cliff;
        HousingCapacityGameTest.withPlayers(h,1,players->{var player=players.getFirst();player.setPos(at.getX()+.5,NapoliExpansion.legacyHeight(at.getX(),at.getZ())+1,at.getZ()+.5);float health=player.getHealth();
            h.assertTrue(city.players().contains(player),"The connected player is registered even before remote entity sections become active");
            for(var chunk:chunks)CityStreets.fit(city,chunk);
            h.assertTrue(Math.abs(player.getY()-NapoliExpansion.height(at.getX(),at.getZ())-1)<.05&&player.getHealth()==health,"A standing player is neither buried by fill nor dropped from the old terrace during the upgrade: actual="+player.getY()+", expected="+(NapoliExpansion.height(at.getX(),at.getZ())+1)+", health="+player.getHealth());
        });h.succeed();
    }
    static BlockPos cliff(){
        for(var p:NapoliAtlas.PLACES)if(!p.original())for(int x=p.x()-p.radius()-12;x<=p.x()+p.radius()+12;x++)if(Math.floorMod(x,40)==2)for(int z=p.z()-p.radius()-10;z<=p.z()+p.radius()+10;z++){
            if(!CityStreets.adjusted(x,z)||!CityStreets.adjusted(x,z+1))continue;
            if(Math.abs(NapoliExpansion.legacyHeight(x,z)-NapoliExpansion.legacyHeight(x,z+1))>=3)return new BlockPos(x,0,z);
        }throw new AssertionError("Expected an old road cliff to reproduce");
    }
    private static List<LevelChunk> legacy(ServerLevel city,BlockPos cliff){
        var chunks=new LinkedHashMap<Long,LevelChunk>();
        for(int x=cliff.getX()-1;x<=cliff.getX()+1;x++)for(int z=cliff.getZ()-5;z<=cliff.getZ()+5;z++)if(CityStreets.adjusted(x,z)){
            var chunk=city.getChunk(x>>4,z>>4);NapoliExpansion.generate(city,chunk);chunks.put(chunk.getPos().pack(),chunk);
            int old=NapoliExpansion.legacyHeight(x,z),top=Math.max(old,NapoliExpansion.height(x,z))+3;
            for(int y=63;y<=top;y++)city.setBlock(new BlockPos(x,y,z),(y<old?Blocks.TUFF:y==old?Blocks.POLISHED_ANDESITE:Blocks.AIR).defaultBlockState(),18);
        }
        for(var chunk:chunks.values()){((AttachmentTarget)chunk).setAttached(NapoliExpansion.GENERATED,1);((AttachmentTarget)chunk).removeAttached(CityStreets.GRADED);}return List.copyOf(chunks.values());
    }
}
