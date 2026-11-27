package dev.napuli;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;

final class FootballFacilities {
    static final String RECEPTION_TAG = "napuli_football_reception", TRAINER_TAG = "napuli_football_trainer";
    private static final BlockPos CLERK = new BlockPos(365, 64, 0), COACH = new BlockPos(354, 64, 14);

    private FootballFacilities() {}

    static BlockPos reception() { return CLERK; }

    static void build(ServerLevel city) {
        for (BlockPos pos : List.of(new BlockPos(365, 64, 3), new BlockPos(358, 64, 15),
                new BlockPos(355, 64, 19), new BlockPos(356, 64, 17))) {
            if (city.getBlockEntity(pos) instanceof SignBlockEntity) put(city, pos, Blocks.AIR);
        }

        // Glass-fronted ticket office, accessible from the concourse.
        pavilion(city, 361, -5, 370, 3, 69, 364, 366);
        fill(city, 363, 63, 3, 367, 63, 6, Blocks.POLISHED_ANDESITE);
        fill(city, 362, 64, -1, 364, 64, -1, Blocks.SMOOTH_QUARTZ);
        fill(city, 362, 64, -4, 363, 64, -4, Blocks.BARREL);
        fill(city, 368, 64, -3, 368, 64, 0, Blocks.OAK_SLAB);
        put(city, new BlockPos(367, 64, 0), Blocks.POTTED_FERN);

        // High-ceiling gym with an open central aisle and a separate changing alcove.
        pavilion(city, 349, 5, 357, 16, 72, 353, 355);
        fill(city, 350, 63, 6, 352, 63, 11, block("gray_concrete"));
        fill(city, 355, 63, 6, 356, 63, 11, block("gray_concrete"));
        for (int x : new int[]{351, 355}) for (int z : new int[]{8, 11}) {
            put(city, new BlockPos(x, 64, z), Blocks.SMOOTH_STONE_SLAB);
            put(city, new BlockPos(x, 64, z - 1), block("blue_carpet"));
        }
        fill(city, 349, 63, 17, 352, 63, 24, Blocks.SMOOTH_STONE);
        fill(city, 349, 64, 17, 349, 68, 24, Blocks.BRICKS);
        fill(city, 349, 64, 24, 352, 68, 24, Blocks.BRICKS);
        fill(city, 349, 68, 17, 352, 68, 24, Blocks.SMOOTH_QUARTZ);
        fill(city, 350, 64, 22, 350, 65, 23, Blocks.BARREL);
        fill(city, 352, 64, 19, 352, 64, 22, Blocks.OAK_SLAB);
        put(city, new BlockPos(351, 67, 21), Blocks.SEA_LANTERN);
        for (int z = 19; z <= 35; z++) {
            fill(city, 353, 63, z, 355, 63, z, z == 19 || z == 35 ? Blocks.CALCITE : block("red_terracotta"));
        }
        staff(city, CLERK, RECEPTION_TAG, NapoliResident.Role.FOOTBALL_CLERK, "napuli.football.clerk");
        staff(city, COACH, TRAINER_TAG, NapoliResident.Role.FOOTBALL_COACH, "napuli.football.trainer");
    }

    static void upgradeGym(ServerLevel city) {
        var blue = block("blue_concrete");
        var gold = block("yellow_concrete");
        for (int z = 19; z <= 35; z++) {
            drillBlock(city, new BlockPos(353, 63, z), blue);
            drillBlock(city, new BlockPos(355, 63, z), blue);
        }
        for (int z : new int[]{19, 23, 26, 28, 35}) drillBlock(city, new BlockPos(354, 63, z), Blocks.CALCITE);
        for (var pos : List.of(new BlockPos(353, 63, 22), new BlockPos(356, 63, 26),
                new BlockPos(353, 63, 30), new BlockPos(356, 63, 34)))
            drillBlock(city, pos, gold);
        drillBlock(city, new BlockPos(355, 63, 37), Blocks.CALCITE);
        for (int x : new int[]{353, 355, 357}) {
            drillBlock(city, new BlockPos(x, 64, 40), Blocks.POLISHED_ANDESITE);
            drillBlock(city, new BlockPos(x, 65, 40), x == 355 ? Blocks.POLISHED_ANDESITE : Blocks.TARGET);
        }
        drillBlock(city, new BlockPos(355, 66, 40), Blocks.TARGET);
    }

    static void upgradePublicGym(ServerLevel city) {
        for (var machine : PublicGym.Machine.values()) drillBlock(city, machine.station, machine.block);
    }

    static void upgradeStrengthMachines(ServerLevel city) {
        var chest = PublicGym.Machine.CHEST;
        var legacy = chest.station;
        var west = legacy.west();
        var east = legacy.east();
        boolean oldBench = (city.getBlockState(legacy).is(Blocks.SMOOTH_STONE_SLAB)
                || city.getBlockState(legacy).is(chest.block))
                && city.getBlockState(west).is(Blocks.ANVIL) && city.getBlockState(east).is(Blocks.ANVIL)
                && !CityProperty.playerPlaced(city, legacy);
        drillBlock(city, legacy, chest.block);
        if (oldBench) {
            if (!CityProperty.playerPlaced(city, west)) put(city, west, Blocks.AIR);
            if (!CityProperty.playerPlaced(city, east)) put(city, east, Blocks.AIR);
        }
        var shoulder = PublicGym.Machine.SHOULDER;
        drillBlock(city, shoulder.station, shoulder.block);
    }

    static void refinePublicGym(ServerLevel city) {
        // Mark each exercise bay at floor level without changing the central aisle.
        for (var machine : PublicGym.Machine.values()) {
            var pos = machine.station.below();
            if (CityProperty.playerPlaced(city, pos)) continue;
            if (city.getBlockState(pos).is(block("gray_concrete")))
                put(city, pos, block("cyan_terracotta"));
        }

        // Closed storage and a rest bench along the changing room walls.
        for (int z = 18; z <= 20; z++) {
            addGymFixture(city, new BlockPos(350, 64, z), Blocks.BARREL);
            addGymFixture(city, new BlockPos(350, 65, z), Blocks.BARREL);
        }
        addGymFixture(city, new BlockPos(352, 64, 18), Blocks.SMOOTH_QUARTZ_SLAB);
    }

    private static void addGymFixture(ServerLevel city, BlockPos pos, Block block) {
        if (city.getBlockState(pos).isAir() && !CityProperty.playerPlaced(city, pos))
            put(city, pos, block);
    }

    private static void drillBlock(ServerLevel city, BlockPos pos, Block block) {
        var old = city.getBlockState(pos);
        if (CityProperty.playerPlaced(city, pos) || city.getBlockEntity(pos) != null) return;
        if (old.isAir() || old.is(Blocks.SAND) || old.is(Blocks.CALCITE) || old.is(Blocks.SMOOTH_STONE_SLAB)
                || old.is(Blocks.POLISHED_ANDESITE) || old.is(block("red_terracotta"))) put(city, pos, block);
    }

    static void installCanopies(ServerLevel city) {
        var template=city.getStructureTemplateManager().get(Napuli.id("pensilina_fuorigrotta"));
        if(template.isEmpty()) return;
        var settings=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        for(var origin:List.of(new BlockPos(363,64,4),new BlockPos(352,64,17))) {
            boolean changed=false;
            for(var pos:BlockPos.betweenClosed(origin,origin.offset(4,4,2)))
                if(CityProperty.playerPlaced(city,pos)){changed=true;break;}
            if(changed)continue;
            for(var material:List.of(Blocks.POLISHED_DEEPSLATE,Blocks.SMOOTH_QUARTZ_SLAB,block("blue_concrete"),Blocks.LANTERN))
                for(var part:template.get().filterBlocks(origin,settings,material))
                    if(city.isEmptyBlock(part.pos()))
                        city.setBlock(part.pos(),part.state(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }


    static void installGardenShelters(ServerLevel city) {
        var template=city.getStructureTemplateManager().get(Napuli.id("gazebo_fuorigrotta"));
        if(template.isEmpty())return;
        var settings=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        for(var origin:List.of(new BlockPos(385,63,63),new BlockPos(410,63,63))) {
            var parts=new java.util.ArrayList<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo>();
            for(var material:List.of(Blocks.STRIPPED_SPRUCE_LOG,Blocks.STONE_BRICKS,Blocks.SPRUCE_PLANKS,Blocks.SPRUCE_SLAB,Blocks.LANTERN))
                parts.addAll(template.get().filterBlocks(origin,settings,material));
            if(parts.stream().anyMatch(part->CityProperty.playerPlaced(city,part.pos())
                    ||city.getBlockEntity(part.pos())!=null
                    ||part.pos().getY()>origin.getY()&&!city.isEmptyBlock(part.pos())))continue;
            for(var part:parts)city.setBlock(part.pos(),part.state(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }


    private static void pavilion(ServerLevel city, int x1, int z1, int x2, int z2, int roof,
                                 int door1, int door2) {
        fill(city, x1, 63, z1, x2, 63, z2, Blocks.SMOOTH_STONE);
        fill(city, x1, 64, z1, x2, roof - 1, z2, Blocks.AIR);
        Block blue = block("blue_concrete");
        for (BlockPos pos : BlockPos.betweenClosed(x1, 64, z1, x2, roof - 1, z2)) {
            if (pos.getX() != x1 && pos.getX() != x2 && pos.getZ() != z1 && pos.getZ() != z2) continue;
            boolean corner = (pos.getX() == x1 || pos.getX() == x2) && (pos.getZ() == z1 || pos.getZ() == z2);
            boolean door = pos.getZ() == z2 && pos.getX() >= door1 && pos.getX() <= door2 && pos.getY() <= 66;
            if (door) continue;
            put(city, pos, pos.getY() == roof - 1 ? blue : corner || pos.getY() == 64
                    ? Blocks.BRICKS : Blocks.GLASS);
        }
        fill(city, x1 - 1, roof, z1 - 1, x2 + 1, roof, z2 + 1, Blocks.SMOOTH_QUARTZ);
        fill(city, x1 + 1, roof + 1, z1 + 1, x2 - 1, roof + 1, z2 - 1, Blocks.POLISHED_DEEPSLATE);
        for (int z = z1 + 2; z < z2; z += 4)
            fill(city, x1 + 2, roof - 1, z, x2 - 2, roof - 1, z, Blocks.SEA_LANTERN);
    }

    static void updateStaffAppearance(NapoliResident npc) {
        if (npc.entityTags().contains(RECEPTION_TAG)) npc.setEmploymentAppearance(NapoliResident.Role.FOOTBALL_CLERK, 0);
        else if (npc.entityTags().contains(TRAINER_TAG)) npc.setEmploymentAppearance(NapoliResident.Role.FOOTBALL_COACH, 0);
    }

    private static void staff(ServerLevel city, BlockPos post, String tag, NapoliResident.Role role, String name) {
        city.getChunkAt(post);
        if (!city.getEntitiesOfClass(NapoliResident.class, new AABB(post).inflate(3),
                npc -> npc.entityTags().contains(tag)).isEmpty()) return;
        var npc = NapoliResident.spawn(city, role, post, 0);
        npc.addTag(tag);
        npc.setNoAi(true);
        npc.setCustomName(Component.translatable(name));
        npc.setCustomNameVisible(true);
    }

    private static Block block(String name) {
        return CityBlocks.vanilla(name);
    }

    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (BlockPos at : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) put(level, at, block);
    }

    private static void put(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
