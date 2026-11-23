package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public final class NapoliAtlasGameTest {
    @GameTest public void cityAreaIsExactlyTenTimesThePreviousEnvelope(GameTestHelper h){
        h.assertTrue(NapoliAtlas.EXPANDED.area()==NapoliAtlas.ORIGINAL.area()*10,"The explorable city envelope expands from 557,056 to 5,570,560 square blocks");
        h.assertTrue(NapoliAtlas.EXPANDED.contains(NapoliAtlas.ORIGINAL.minX(),NapoliAtlas.ORIGINAL.minZ())&&NapoliAtlas.EXPANDED.contains(NapoliAtlas.ORIGINAL.maxX()-1,NapoliAtlas.ORIGINAL.maxZ()-1),"The entire previous city remains inside the expanded map");h.succeed();
    }
    @GameTest public void realPlacesHaveUniqueIdentifiersAndReservedFootprints(GameTestHelper h){
        var ids=new HashSet<String>();for(var p:NapoliAtlas.PLACES){h.assertTrue(ids.add(p.id()),"Every destination has a unique stable identifier");if(p.original())continue;
            h.assertTrue(NapoliAtlas.EXPANDED.contains(p.x()-p.radius(),p.z()-p.radius())&&NapoliAtlas.EXPANDED.contains(p.x()+p.radius(),p.z()+p.radius()),"Landmark fits inside the city: "+p.id());
            h.assertTrue(!NapoliAtlas.ORIGINAL.contains(p.x(),p.z()),"New monuments do not replace the old city: "+p.id());
        }h.succeed();
    }
    @GameTest public void allDestinationsAreConnectedToTheExistingTown(GameTestHelper h){
        var reached=new HashSet<String>();reached.add("plebiscito");boolean changed;do{changed=false;for(var r:NapoliAtlas.ROADS){h.assertTrue(NapoliAtlas.find(r.from())!=null&&NapoliAtlas.find(r.to())!=null,"Named connections have real endpoints");if(reached.contains(r.from()))changed|=reached.add(r.to());if(reached.contains(r.to()))changed|=reached.add(r.from());}}while(changed);
        h.assertTrue(reached.size()==NapoliAtlas.PLACES.size(),"Every destination is reachable through the street network");h.succeed();
    }
    @GameTest public void hillsCoastAndNeighborhoodsFollowNaplesOrientation(GameTestHelper h){
        h.assertTrue(NapoliAtlas.find("maradona").x()<NapoliAtlas.find("mergellina").x()&&NapoliAtlas.find("direzionale").x()>NapoliAtlas.find("garibaldi").x(),"Fuorigrotta is west, Centro Direzionale east of Centrale");
        h.assertTrue(NapoliAtlas.find("capodimonte").z()<NapoliAtlas.find("sanita").z()&&NapoliAtlas.find("ponticelli").z()<NapoliAtlas.find("barra").z(),"Capodimonte is north of Sanità and Ponticelli north of Barra");
        h.assertTrue(NapoliExpansion.height(-790,-300)>NapoliExpansion.height(680,-195)+20,"Vomero is a hill, not a flat copy of the waterfront");
        h.assertTrue(NapoliExpansion.water(-1980,400)&&!NapoliExpansion.water(-1870,365),"The sea surrounds the island of Nisida");h.succeed();
    }
    @GameTest public void everyNewLandmarkBuildsOnItsReservedGround(GameTestHelper h){
        var city=city(h);for(var p:NapoliAtlas.PLACES)if(!p.original()){
            var chunk=city.getChunkAt(new BlockPos(p.x(),64,p.z()));NapoliExpansion.generate(city,chunk);h.assertTrue(NapoliExpansion.revision(chunk)==NapoliExpansion.REVISION,"Chunk built on exploration: "+p.id());
            int y=p.kind()==NapoliAtlas.Kind.PIER?65:NapoliExpansion.height(p.x(),p.z());
            var floor=new BlockPos(p.x(),y,p.z());h.assertTrue(!city.getBlockState(floor).getCollisionShape(city,floor).isEmpty(),"Landmark has solid accessible ground: "+p.id());
            h.assertTrue(!NapoliExpansion.generate(city,chunk),"Reloading or checking a district cannot regenerate it: "+p.id());
            var arrival=p.arrival();var landing=city.getChunkAt(arrival);NapoliExpansion.generate(city,landing);h.assertTrue(!city.getBlockState(arrival.below()).getCollisionShape(city,arrival.below()).isEmpty()&&city.getBlockState(arrival).getCollisionShape(city,arrival).isEmpty(),"Walking destination has a clear dry landing: "+p.id()+" at "+arrival+", floor="+city.getBlockState(arrival.below())+", body="+city.getBlockState(arrival)+", revision="+NapoliExpansion.revision(landing));
        }h.succeed();
    }
    @GameTest public void regenerationKeepsPlayerConstructionAndStorage(GameTestHelper h){
        var city=city(h);var pos=new BlockPos(1980,64,-600);var chunk=city.getChunkAt(pos);var old=city.getBlockState(pos);
        try{city.setBlock(pos,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);CityProperty.placed(city,pos);((AttachmentTarget)chunk).removeAttached(NapoliExpansion.GENERATED);
            h.assertTrue(!NapoliExpansion.generate(city,chunk)&&city.getBlockState(pos).is(Blocks.DIAMOND_BLOCK),"An existing player's chunk is preserved instead of terraformed");
        }finally{CityProperty.forget(city,pos);city.setBlock(pos,old,2);}h.succeed();
    }
    @GameTest public void newHomesKeepTheCombinedFiveOccupantLimit(GameTestHelper h){
        var city=city(h);var pos=h.absolutePos(new BlockPos(0,40,0));city.getChunkAt(pos);CityProperty.recordHome(city,pos,pos.offset(9,6,9));var actors=new ArrayList<NapoliResident>();
        try{for(int i=0;i<5;i++){var npc=NapoliResident.spawn(city,NapoliResident.Role.PASSERBY,pos.offset(2+i,0,2),0);npc.setNoAi(true);actors.add(npc);}h.assertTrue(!HousingCapacity.canEnter(city,pos.offset(5,0,5),UUID.randomUUID()),"New district houses count all five residents before admitting another ordinary occupant");}
        finally{for(var npc:actors)npc.discard();}h.succeed();
    }
    private static ServerLevel city(GameTestHelper h){return h.getLevel();}
}
