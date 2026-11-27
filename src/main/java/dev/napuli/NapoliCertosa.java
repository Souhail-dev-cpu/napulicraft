package dev.napuli;

import static dev.napuli.NapoliMonumentInteriors.material;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class NapoliCertosa {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final String EXHIBIT_ID = "presepe_certosa";
    private static final Set<Block> GARDEN_SURFACE = Set.of(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE,
            Blocks.SMOOTH_STONE, Blocks.SMOOTH_QUARTZ, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK,
            Blocks.TERRACOTTA, Blocks.BRICKS, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE);
    private static final Set<Block> LEGACY_ARCADE = Set.of(
            Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.SMOOTH_SANDSTONE);
    static final Block PRESEPE = Registry.register(BuiltInRegistries.BLOCK, Napuli.id(EXHIBIT_ID), new NativityExhibit(
            BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, Napuli.id(EXHIBIT_ID)))
                    .sound(SoundType.WOOD).strength(1.1F).noOcclusion()));

    private NapoliCertosa() {}

    static void register() {
        var key = ResourceKey.create(Registries.ITEM, Napuli.id(EXHIBIT_ID));
        Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(PRESEPE, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    static BlockPos floor() {
        var place = NapoliAtlas.find("san_martino");
        return new BlockPos(place.x(), NapoliExpansion.height(place.x(), place.z()), place.z());
    }

    static BlockPos exhibit() { return floor().offset(-18, 2, -1); }
    static BlockPos guidePost() { return floor().offset(-1, 1, 18); }
    static BlockPos restorerPost() { return floor().offset(-21, 1, 1); }

    static void upgrade(ServerLevel city) {
        var centre = floor();
        var shell = new HashSet<ChunkPos>();
        for (int cx = (centre.getX() - 25) >> 4; cx <= (centre.getX() + 24) >> 4; cx++) {
            for (int cz = (centre.getZ() - 24) >> 4; cz <= (centre.getZ() + 21) >> 4; cz++) {
                var chunk = city.getChunk(cx, cz);
                NapoliExpansion.generate(city, chunk);
                // The courtyard is open. Check its engineered floor, and the covered wing separately.
                int x = Math.clamp(cx * 16 + 8, centre.getX() - 24, centre.getX() + 23);
                int z = Math.clamp(cz * 16 + 8, centre.getZ() - 23, centre.getZ() + 20);
                var pos = new BlockPos(x, centre.getY(), z);
                var state = city.getBlockState(pos);
                if (state.is(Blocks.POLISHED_ANDESITE) || state.is(Blocks.MOSS_BLOCK)
                        || city.getBlockState(pos.above(15)).is(Blocks.SMOOTH_SANDSTONE)) shell.add(chunk.getPos());
            }
        }
        for (int dy = 1; dy <= 5; dy++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            if (dx * dx + dz * dz > 16) continue;
            var pos = centre.offset(dx, dy, dz);
            var old = city.getBlockState(pos);
            boolean legacyFountainPillar = dx == 0 && dz == 0 && dy <= 4
                    && (old.is(Blocks.CHISELED_STONE_BRICKS) || old.is(Blocks.CHISELED_QUARTZ_BLOCK));
            boolean legacyFountainRim = dy == 1 && dx * dx + dz * dz >= 9 && old.is(Blocks.SMOOTH_QUARTZ);
            if (shell.contains(ChunkPos.containing(pos)) && !CityProperty.playerPlaced(city, pos)
                    && (old.is(Blocks.WATER) || legacyFountainPillar || legacyFountainRim))
                city.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        for (int dz : new int[]{-17, 16}) for (int y = 1; y <= 8; y++) {
            var pos = centre.offset(0, y, dz);
            if (shell.contains(ChunkPos.containing(pos)) && !CityProperty.playerPlaced(city, pos)
                    && city.getBlockState(pos).is(Blocks.QUARTZ_PILLAR)) city.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        NapoliMonumentInteriors.place(city, "certosa_chiostro", centre.offset(-16, 0, -15),
                pos -> shell.contains(ChunkPos.containing(pos)), (pos, old) -> pos.getY() == centre.getY()
                        && (old.is(Blocks.POLISHED_ANDESITE) || old.is(Blocks.MOSS_BLOCK) || old.is(Blocks.WATER)
                            || old.is(Blocks.SMOOTH_QUARTZ)),
                List.of(Blocks.POLISHED_DIORITE, Blocks.SMOOTH_STONE, Blocks.AZALEA_LEAVES, Blocks.CHISELED_QUARTZ_BLOCK,
                        Blocks.QUARTZ_PILLAR, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.CHISELED_TUFF_BRICKS,
                        Blocks.SMOOTH_QUARTZ_SLAB, Blocks.WATER_CAULDRON));
        NapoliMonumentInteriors.place(city, "certosa_sale_presepiali", centre.offset(-24, 0, -10),
                pos -> shell.contains(ChunkPos.containing(pos)), (pos, old) -> pos.getY() == centre.getY()
                        && old.is(Blocks.POLISHED_ANDESITE),
                List.of(Blocks.TERRACOTTA, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_QUARTZ_BLOCK,
                        Blocks.LANTERN, Blocks.BRICKS, Blocks.DECORATED_POT, Blocks.TUFF, Blocks.TUFF_BRICKS,
                        Blocks.DARK_OAK_PLANKS, material("ochre_froglight")));
        var exhibit = exhibit();
        if (city.getBlockState(exhibit.below()).is(Blocks.DARK_OAK_PLANKS) && city.isEmptyBlock(exhibit)
                && !CityProperty.playerPlaced(city, exhibit)) city.setBlock(exhibit, PRESEPE.defaultBlockState(), FLAGS);
        if (shell.contains(ChunkPos.containing(guidePost()))) placeStaff(city, NapoliResident.Role.CERTOSA_GUIDE, guidePost());
        if (shell.contains(ChunkPos.containing(restorerPost()))) placeStaff(city, NapoliResident.Role.CERTOSA_RESTORER, restorerPost());
    }

    static void refitGarden(ServerLevel city) {
        var centre = floor();
        var garden = new HashSet<Long>();
        for (BlockPos tile : BlockPos.betweenClosed(centre.offset(-15, 0, -14), centre.offset(15, 0, 13))) {
            if (GARDEN_SURFACE.contains(city.getBlockState(tile).getBlock()) && !CityProperty.playerPlaced(city, tile))
                garden.add(tile.asLong());
        }
        for (int x : new int[]{-11, -6, 6, 11}) for (int z : new int[]{-10, -5, 5, 10}) {
            var shrub = centre.offset(x, 1, z);
            if (garden.contains(shrub.below().asLong()) && !CityProperty.playerPlaced(city, shrub)
                    && city.getBlockState(shrub).is(Blocks.AZALEA_LEAVES))
                city.setBlock(shrub, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        // Only install a complete tree where its footprint can accommodate the crown.
        var reserved = new HashSet<Long>();
        for (int x : new int[]{-10, 10}) {
            var tree = centre.offset(x, 0, -7);
            for (BlockPos pos : BlockPos.betweenClosed(tree.offset(-2, 1, -2), tree.offset(2, 5, 2))) {
                if (CityProperty.playerPlaced(city, pos) || !city.isEmptyBlock(pos)
                        || !garden.contains(pos.atY(centre.getY()).asLong())) {
                    for (BlockPos column : BlockPos.betweenClosed(tree.offset(-2, 0, -2), tree.offset(2, 0, 2)))
                        reserved.add(column.asLong());
                    break;
                }
            }
        }
        NapoliMonumentInteriors.place(city, "certosa_giardino_chiostro", centre.offset(-15, 0, -14), pos -> {
            long tile = pos.atY(centre.getY()).asLong();
            return garden.contains(tile) && (pos.getY() == centre.getY() || !reserved.contains(tile));
        }, (pos, old) -> pos.getY() == centre.getY() && GARDEN_SURFACE.contains(old.getBlock()),
                List.of(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.POLISHED_DIORITE, Blocks.TERRACOTTA,
                        Blocks.BRICKS, Blocks.GRASS_BLOCK, Blocks.STRIPPED_OAK_LOG, Blocks.AZALEA_LEAVES,
                        Blocks.OAK_LEAVES, Blocks.OXEYE_DAISY));
    }

    static void refitArcades(ServerLevel city) {
        var centre = floor();
        // Keep the new upper facade above the existing columns and entrances.
        NapoliMonumentInteriors.place(city, "certosa_portico", centre.offset(-16, 0, -15),
                (pos, old) -> LEGACY_ARCADE.contains(old.getBlock()),
                List.of(Blocks.AIR, Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_STAIRS,
                        Blocks.SMOOTH_QUARTZ_SLAB, Blocks.CHISELED_QUARTZ_BLOCK,
                        Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, material("light_gray_stained_glass")));
    }

    private static void placeStaff(ServerLevel city, NapoliResident.Role role, BlockPos pos) {
        if (!city.isEmptyBlock(pos) || !city.isEmptyBlock(pos.above()) || !city.getBlockState(pos.below()).isSolidRender()) return;
        if (city.getEntitiesOfClass(NapoliResident.class, new AABB(pos).inflate(8), resident -> resident.role() == role).isEmpty())
            NapoliResident.spawn(city, role, pos, 0);
    }

    static void talk(ServerPlayer visitor, NapoliResident guide) {
        guide.getLookControl().setLookAt(visitor, 30, 30);
        NpcSpeech.say(visitor, guide, Component.translatable(guide.role() == NapoliResident.Role.CERTOSA_GUIDE
                ? "napuli.certosa.guide" : "napuli.certosa.restorer"));
    }

    private static final class NativityExhibit extends Block {
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 13, 15);

        NativityExhibit(Properties properties) { super(properties); }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer visitor)
                NapoliNotice.show(visitor, Component.translatable("napuli.certosa.presepe"));
            return InteractionResult.SUCCESS;
        }
    }
}
