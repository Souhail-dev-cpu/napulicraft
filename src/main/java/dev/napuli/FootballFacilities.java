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
        drillBlock(city, new BlockPos(351, 64, 8), Blocks.SMOOTH_STONE_SLAB);
        drillBlock(city, new BlockPos(350, 64, 8), Blocks.ANVIL);
        drillBlock(city, new BlockPos(352, 64, 8), Blocks.ANVIL);
    }

    static void upgradePublicGym(ServerLevel city) {
        for (var machine : PublicGym.Machine.values()) drillBlock(city, machine.station, machine.block);
    }

    private static void drillBlock(ServerLevel city, BlockPos pos, Block block) {
        var old = city.getBlockState(pos);
        if (CityProperty.playerPlaced(city, pos) || city.getBlockEntity(pos) != null) return;
        if (old.isAir() || old.is(Blocks.SAND) || old.is(Blocks.CALCITE) || old.is(Blocks.SMOOTH_STONE_SLAB)
                || old.is(Blocks.POLISHED_ANDESITE) || old.is(block("red_terracotta"))) put(city, pos, block);
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
