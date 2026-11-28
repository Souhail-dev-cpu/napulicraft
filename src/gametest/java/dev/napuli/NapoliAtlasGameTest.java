package dev.napuli;

import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public final class NapoliAtlasGameTest {
    @GameTest public void generatedEntranceRepairsAMissingDoorHalf(GameTestHelper h) {
        var city=h.getLevel();
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,3,2));
        city.setBlockAndUpdate(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        city.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        city.setBlockAndUpdate(pos.above(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        NapoliExpansion.ensureDoor(city,pos,net.minecraft.core.Direction.SOUTH);
        var lower=city.getBlockState(pos);
        var upper=city.getBlockState(pos.above());
        var half=net.minecraft.world.level.block.DoorBlock.HALF;
        var upperHalf=net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
        h.assertTrue(lower.is(net.minecraft.world.level.block.Blocks.OAK_DOOR)&&upper.getValue(half)==upperHalf,
                "A generated entrance gains both door halves");
        city.setBlock(pos.above(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2|16);
        NapoliExpansion.ensureDoor(city,pos,net.minecraft.core.Direction.SOUTH);
        h.assertTrue(city.getBlockState(pos.above()).is(net.minecraft.world.level.block.Blocks.OAK_DOOR),
                "Repeating the repair restores a missing half");
        h.succeed();
    }

    @GameTest public void oldHouseFrontageGetsConnectedWindowsAndAWalkableThreshold(GameTestHelper helper) {
        var level = helper.getLevel();
        var house = DistrictLife.districts().getLast().homes().getLast();
        int x = house.x(), y = house.ground(), z = house.z();
        var pane = new BlockPos(x + 3, y + 1, z + 23);
        var jamb = new BlockPos(x + 10, y, z + 23);
        var threshold = new BlockPos(x + 11, y, z + 24);
        var door = threshold.north();
        var rail = new BlockPos(x + 3, y + 6, z + 25);
        var lamp = new BlockPos(x + 19, y + 2, z + 2);
        var roofEdge = new BlockPos(x + 3, y + house.floors() * 5 + 1, z + 23);
        for (var pos : List.of(pane, jamb, threshold, door, rail, lamp, roofEdge)) {
            var chunk = level.getChunkAt(pos);
            NapoliExpansion.generate(level, chunk);
        }
        level.setBlock(roofEdge.below(), Blocks.BRICK_SLAB.defaultBlockState(), 2);
        level.setBlock(roofEdge, Blocks.BRICK_WALL.defaultBlockState(), 2);
        level.setBlock(pane, Blocks.GLASS_PANE.defaultBlockState(), 2);
        level.setBlock(jamb, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(threshold, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        for (var pos : List.of(pane, jamb, threshold, door, rail, lamp, roofEdge)) level.getChunkAt(pos).removeAttached(NapoliExpansion.FRONTAGES);
        for (var pos : List.of(pane, jamb, threshold, door, rail, lamp, roofEdge)) NapoliExpansion.repairFrontages(level, level.getChunkAt(pos));
        var wall = level.getBlockState(roofEdge);
        helper.assertTrue(level.getBlockState(roofEdge.below()).is(Blocks.BRICKS)
                        && level.getBlockState(roofEdge.below()).isFaceSturdy(level, roofEdge.below(), net.minecraft.core.Direction.UP),
                "A continuous solid course fills the gap under the roof parapet");
        helper.assertTrue(wall.is(Blocks.BRICK_WALL)
                        && wall.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST_WALL)
                            == net.minecraft.world.level.block.state.properties.WallSide.LOW
                        && wall.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST_WALL)
                            == net.minecraft.world.level.block.state.properties.WallSide.LOW,
                "Existing isolated roof posts become a continuous parapet");
        var guard = level.getBlockState(rail);
        helper.assertTrue(guard.is(Blocks.IRON_BARS) && guard.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST)
                        && guard.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST),
                "The balcony railing is a connected run");
        helper.assertTrue(!level.isEmptyBlock(rail.west().below(2)), "A masonry bracket supports the balcony");
        var lantern = level.getBlockState(lamp);
        helper.assertTrue(lantern.is(Blocks.LANTERN)
                        && lantern.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING)
                        && !level.isEmptyBlock(lamp.above()), "Interior lanterns connect to the ceiling");
        var glass = level.getBlockState(pane);
        helper.assertTrue(glass.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST)
                        && glass.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST),
                "The pane reaches both sides of the masonry");
        helper.assertTrue(level.getBlockState(jamb).is(Blocks.POLISHED_DEEPSLATE)
                        && level.getBlockState(door).is(Blocks.OAK_DOOR), "The existing door gains a jamb");
        helper.assertTrue(level.getBlockState(threshold.below()).is(Blocks.POLISHED_ANDESITE)
                        && level.isEmptyBlock(threshold) && level.isEmptyBlock(threshold.above()),
                "The paved landing is level and clear beside the door");
        helper.succeed();
    }

    @GameTest public void atlasHomesHaveFinishedUpperRoomsWithAStairLanding(GameTestHelper h) {
        var fixture = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var floor = new NapoliInteriorLayout((x, y, z, state) -> fixture.put(new BlockPos(x, y, z), state));
        floor.apartmentBlock(0, 64, 0, 3);
        for (int step = 0; step < 5; step++) {
            h.assertTrue(fixture.get(new BlockPos(16, 64 + step, 16 + step)).is(Blocks.STONE_BRICK_STAIRS),
                    "Continuous steps join the ground and first floor");
            h.assertTrue(fixture.get(new BlockPos(16, 65 + step, 16 + step)).isAir(), "Stair headroom stays clear");
            h.assertTrue(fixture.get(new BlockPos(19, 69 + step, 20 - step)).is(Blocks.STONE_BRICK_STAIRS),
                    "The next flight links to the top floor");
        }
        for (int y : new int[]{69, 74}) {
            h.assertTrue(fixture.get(new BlockPos(13, y, 5)).getBlock() instanceof net.minecraft.world.level.block.BedBlock,
                    "The upper rooms have complete beds");
            h.assertTrue(fixture.get(new BlockPos(18, y, 2)).is(Blocks.BOOKSHELF), "The upper-floor study is furnished");
        }
        h.succeed();
    }

    @GameTest public void anExploredMonumentGetsFacadeDepthWithoutReplacingTheApproach(GameTestHelper h) {
        var level = city(h);
        var museum = NapoliAtlas.find("mann");
        int y = NapoliExpansion.height(museum.x(), museum.z()) + 1;
        var pediment = new BlockPos(museum.x(), y + 27, museum.z() + 20);
        var chunk = level.getChunkAt(pediment);
        chunk.removeAttached(NapoliExpansion.ARCHITECTURE);
        ((AttachmentTarget) chunk).setAttached(NapoliExpansion.GENERATED, NapoliExpansion.REVISION);
        h.assertTrue(NapoliExpansion.upgradeArchitecture(level, chunk), "An established atlas chunk receives the architectural pass");
        h.assertTrue(level.getBlockState(pediment).is(Blocks.SMOOTH_QUARTZ_SLAB), "The MANN facade has a larger central pediment");
        h.assertTrue(!NapoliExpansion.upgradeArchitecture(level, chunk), "The architectural pass runs once");
        var entry = museum.arrival();
        NapoliExpansion.generate(level, level.getChunkAt(entry));
        h.assertTrue(level.isEmptyBlock(entry) && !level.isEmptyBlock(entry.below()), "The atlas arrival is still walkable");
        h.succeed();
    }

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
        var missing=NapoliAtlas.PLACES.stream().map(NapoliAtlas.Place::id).filter(id->!reached.contains(id)).toList();
        h.assertTrue(missing.isEmpty(),"Every destination is reachable through the street network; disconnected: "+missing);h.succeed();
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
    @GameTest public void oldFootballGroundJoinsAtlasOnAWalkableSurface(GameTestHelper h) {
        var level = city(h);
        int border = NapoliAtlas.ORIGINAL.maxX();
        var legacy = new BlockPos(border - 1, 63, 65);
        var expansion = legacy.east();
        var playerPlaced = legacy.south(3);
        var markers=new HashMap<net.minecraft.world.level.chunk.LevelChunk,Boolean>();
        for(var pos:List.of(legacy,expansion)){
            var chunk=level.getChunkAt(pos);markers.putIfAbsent(chunk,chunk.getAttached(NapoliFootballLink.COMPLETE));
            chunk.removeAttached(NapoliFootballLink.COMPLETE);
        }
        for (var pos : List.of(legacy, expansion, playerPlaced)) {
            level.getChunkAt(pos);
            level.setBlock(pos, (pos.equals(expansion) ? Blocks.GRASS_BLOCK : Blocks.SAND).defaultBlockState(), 2);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        }
        CityProperty.placed(level, playerPlaced);
        try {
            NapoliFootballLink.fit(level, level.getChunkAt(legacy));
            NapoliFootballLink.fit(level, level.getChunkAt(expansion));
            for (var pos : List.of(legacy, expansion))
                h.assertTrue(level.getBlockState(pos).is(Blocks.POLISHED_ANDESITE) && level.isEmptyBlock(pos.above()),
                        "The path crosses both sides of the development boundary at the same level");
            h.assertTrue(level.getBlockState(playerPlaced).is(Blocks.SAND), "Player-placed sand is preserved");
        } finally {
            CityProperty.forget(level, playerPlaced);
            markers.forEach((chunk,marker)->{if(marker==null)chunk.removeAttached(NapoliFootballLink.COMPLETE);else chunk.setAttached(NapoliFootballLink.COMPLETE,marker);});
        }
        h.succeed();
    }

    @GameTest public void legacySandBecomesGrassWithoutOverwritingTheCoastOrPavement(GameTestHelper helper) {
        var city = city(helper);
        var ground = new BlockPos(-1101, 63, -1197);
        var playerSand = ground.east(16);
        var sea = ground.south();
        var pavement = ground.south().east();
        var building = ground.east(2);
        var chunk = city.getChunkAt(ground);
        for (var pos : List.of(ground, playerSand, sea, pavement, building)) {
            city.setBlock(pos.below(6), Blocks.STONE.defaultBlockState(), 2);
            for (int depth = 1; depth <= 5; depth++) city.setBlock(pos.below(depth), Blocks.SANDSTONE.defaultBlockState(), 2);
            city.setBlock(pos, Blocks.SAND.defaultBlockState(), 2);
            city.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        }
        city.setBlock(sea.above(), Blocks.WATER.defaultBlockState(), 2);
        city.setBlock(pavement, Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
        city.setBlock(building.above(), Blocks.BRICKS.defaultBlockState(), 2);
        CityProperty.placed(city, playerSand);
        try {
            NapoliGround.finish(city, chunk);
            NapoliGround.finish(city, city.getChunkAt(playerSand));
            helper.assertTrue(city.getBlockState(ground).is(Blocks.GRASS_BLOCK), "Exposed generator sand is landscaped");
            helper.assertTrue(city.getBlockState(sea).is(Blocks.SAND) && city.getBlockState(sea.above()).is(Blocks.WATER),
                    "The seabed and water are preserved");
            helper.assertTrue(city.getBlockState(pavement).is(Blocks.POLISHED_ANDESITE), "Existing pavement is preserved");
            helper.assertTrue(city.getBlockState(building).is(Blocks.SAND), "The substrate under a building is preserved");
            helper.assertTrue(city.getBlockState(playerSand).is(Blocks.SAND), "Player-placed sand is preserved");
            helper.assertTrue(chunk.getAttachedOrElse(NapoliGround.FINISHED, false), "The migration is recorded on the chunk");
        } finally {
            CityProperty.forget(city, playerSand);
        }
        helper.succeed();
    }

    @GameTest public void atlasHouseRestoresACutCornerAndPreservesExistingStorage(GameTestHelper helper) {
        var city = helper.getLevel();
        var home = DistrictLife.homes().stream()
                .filter(h -> Math.floorMod(h.x(), 16) <= 3 && Math.floorMod(h.z(), 16) <= 3)
                .findFirst().orElseThrow();
        var origin = new BlockPos(home.x(), home.ground(), home.z());
        var chunk = city.getChunkAt(origin);
        NapoliExpansion.generate(city, chunk);
        int roof = home.floors() * 5;
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(7, roof, 7)))
            city.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);

        var barrel = origin.offset(5, 0, 6);
        city.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 2);
        var storage = (net.minecraft.world.level.block.entity.BarrelBlockEntity) city.getBlockEntity(barrel);
        storage.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET));
        var wall = origin.offset(0, 2, 2);
        var floor = origin.offset(5, 4, 6);
        var ceiling = origin.offset(5, roof, 6);
        var tracked = origin.offset(6, 0, 7);
        city.setBlock(tracked, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        CityProperty.placed(city, tracked);
        chunk.removeAttached(NapoliExpansion.HOUSE_SECTIONS_FIXED);
        try {
            helper.assertTrue(NapoliExpansion.repairHouseSections(city, chunk) == 0 && city.isEmptyBlock(floor),
                    "The patch leaves chunks with tracked building work alone");
        } finally {
            CityProperty.forget(city, tracked);
        }
        helper.assertTrue(NapoliExpansion.repairHouseSections(city, chunk) > 0, "The old missing building section is reconstructed");
        helper.assertTrue(!city.isEmptyBlock(wall) && !city.isEmptyBlock(floor) && !city.isEmptyBlock(ceiling),
                "Facade, upper floor and roof close again");
        helper.assertTrue(city.getBlockEntity(barrel) == storage && !storage.getItem(0).isEmpty(),
                "Existing storage and its contents survive");
        helper.assertTrue(city.getBlockState(tracked).is(Blocks.DIAMOND_BLOCK), "Solid blocks are not replaced by the missing-block plan");
        helper.assertTrue(NapoliExpansion.repairHouseSections(city, chunk) == 0, "The same chunk can be revisited without another rebuild");
        helper.succeed();
    }

    private static ServerLevel city(GameTestHelper h){return h.getLevel();}
}
