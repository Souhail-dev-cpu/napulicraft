package dev.napuli;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Additive furnishing for the catalogue of occupied and purchasable homes. */
final class NapoliHomeDecor {
    private static final AttachmentType<Boolean> FITTED = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL).buildAndRegister(Napuli.id("home_decor_v3"));

    /** Initialize the saved marker before Minecraft reads any chunk attachments. */
    static void register() {}

    private final ServerLevel level;
    private final LevelChunk chunk;
    private java.util.Set<BlockPos> approaches;

    private NapoliHomeDecor(ServerLevel level, LevelChunk chunk) {
        this.level = level;
        this.chunk = chunk;
    }

    static void fit(ServerLevel level, LevelChunk chunk) {
        if (chunk.getAttachedOrElse(FITTED, false) || CityProperty.playerBuiltInChunk(level, chunk.getPos())) return;
        var decor = new NapoliHomeDecor(level, chunk);
        var bounds = chunk.getPos();
        for (var house : CityLivingHomes.all()) {
            if (house.x() > bounds.getMaxBlockX() || house.x() + house.width() <= bounds.getMinBlockX()
                    || house.z() > bounds.getMaxBlockZ() || house.z() + house.depth() + 1 <= bounds.getMinBlockZ()) continue;
            if (house.id().startsWith("atlas_") && NapoliExpansion.revision(chunk) < 1) return;
            decor.furnish(house);
        }
        chunk.setAttached(FITTED, true);
    }

    private void furnish(NapoliMergellina.House house) {
        approaches = new java.util.HashSet<>();
        for (var point : CityLivingHomes.points(house)) {
            approaches.add(point.stand());
            approaches.add(point.stand().above());
        }
        int x = house.x(), y = house.ground(), z = house.z();
        var bed = CityLivingHomes.use(house, CityLivingHomes.Furniture.BED).block();
        var desk = CityLivingHomes.use(house, CityLivingHomes.Furniture.DESK).block();
        var sink = CityLivingHomes.use(house, CityLivingHomes.Furniture.SINK).block();
        int entranceZ = z + house.depth() - 2;
        boolean roomy = house.id().startsWith("atlas_");

        // Keep the work aisle at z+3 and the house's scripted stove, fridge and table.
        floor(new BlockPos(x + 4, y, z + 2), Blocks.FURNACE);
        wall(new BlockPos(x + 3, y + 2, z + 1), NapoliFurniture.CAPPA);
        floor(new BlockPos(x + 5, y, z + 2), NapoliFurniture.WORKTOP);
        floor(new BlockPos(x + 5, y + 1, z + 2), NapoliFurniture.KITCHEN_SET);
        floor(new BlockPos(x + 5, y, z + 4), NapoliFurniture.SINK);
        floor(new BlockPos(x + 2, y, z + 4), NapoliKitchen.BLOCKS.get("banca_caffe"));

        // The low table leaves the sofa's approach free. The TV keeps its existing use point.
        floor(new BlockPos(x + 3, y, z + 8), NapoliFurniture.COFFEE_TABLE);
        floor(new BlockPos(x + 3, y, z + 7), block("cyan_carpet"));
        floor(new BlockPos(x + 4, y, z + 8), NapoliFurniture.POLTRONA.defaultBlockState());
        floor(new BlockPos(x + 1, y, z + 8), block("cyan_carpet"));
        floor(new BlockPos(x + 3, y, z + 9), Blocks.BARREL);
        floor(new BlockPos(x + 1, y, z + 10), Blocks.BOOKSHELF);
        floor(new BlockPos(x + 2, y, z + 10), Blocks.POTTED_FERN);

        floor(sink.east(2), NapoliFurniture.BATH_SET);
        floor(sink.east(2).above(), NapoliFurniture.HAIR_DRYER);
        floor(sink.east().south(2), NapoliFurniture.SCALE);
        floor(sink.east(2).north(), NapoliFurniture.POWER_STRIP);
        
        floor(sink.east().south(), block("light_blue_carpet"));
        floor(new BlockPos(x + 9, y, z + 8), Blocks.BARREL);
        floor(new BlockPos(x + 9, y, z + 9), NapoliFurniture.LAUNDRY_BASKET);

        floor(bed.west().north(), Blocks.BARREL);
        floor(bed.west().north().above(), NapoliFurniture.LAMP);
        floor(bed.south(2), block("blue_carpet"));
        floor(desk.east().north(), Blocks.BOOKSHELF);
        floor(desk.east().south(), NapoliFurniture.SEDIA_IMBOTTITA.defaultBlockState());
        floor(desk.east(2), Blocks.BARREL);
        floor(desk.east(2).above(), NapoliFurniture.COMPUTER);

        floor(new BlockPos(x + house.width() / 2, y, entranceZ), block("brown_carpet"));
        floor(new BlockPos(x + house.width() - 2, y, entranceZ), Blocks.BARREL);
        floor(new BlockPos(x + house.width() - 2, y + 1, entranceZ), NapoliFurniture.ENTRY_TRAY);
        floor(new BlockPos(x + house.width() - 2, y, entranceZ - 2), Blocks.POTTED_FERN);

        var fridge = CityLivingHomes.use(house, CityLivingHomes.Furniture.FRIDGE).block();
        substitute(fridge, NapoliFurniture.FRIGORIFERO, Blocks.BARREL);
        var sofa = CityLivingHomes.use(house, CityLivingHomes.Furniture.SOFA).block();
        substitute(sofa, NapoliFurniture.DIVANO, Blocks.SPRUCE_STAIRS, Blocks.OAK_STAIRS);
        var toilet = CityLivingHomes.use(house, CityLivingHomes.Furniture.TOILET).block();
        substitute(toilet, NapoliFurniture.WC, Blocks.QUARTZ_STAIRS, Blocks.CAULDRON);
        var washer = CityLivingHomes.use(house, CityLivingHomes.Furniture.WASHER).block();
        substitute(washer, NapoliFurniture.LAVATRICE, Blocks.IRON_BLOCK, Blocks.DISPENSER);
        floor(washer.east(3), NapoliFurniture.ASCIUGATRICE);
        floor(washer.east(4), NapoliFurniture.ASSE_STIRO);
        floor(washer.south(2), NapoliFurniture.KIT_PULIZIA);
        floor(washer.east(2).south(2), NapoliFurniture.VACUUM);
        floor(washer.east(2).above(), NapoliFurniture.POWER_STRIP);
        floor(sink.east(2).south(2), NapoliFurniture.BIDET);
        floor(sink.south(2), NapoliFurniture.VASCA);
        floor(new BlockPos(x + 1, y, z + 2), NapoliFurniture.RACCOLTA);
        floor(new BlockPos(x + 1, y, z + 3), NapoliFurniture.LAVASTOVIGLIE);
        floor(new BlockPos(x + 1, y, z + 5), Blocks.BARREL);
        floor(new BlockPos(x + 1, y + 1, z + 5), NapoliFurniture.MICROONDE);
        floor(new BlockPos(x + 1, y, z + 6), Blocks.BARREL);
        floor(new BlockPos(x + 1, y + 1, z + 6), NapoliFurniture.TOSTAPANE);
        floor(new BlockPos(x + house.width() - 2, y, entranceZ - 1), NapoliFurniture.APPENDIABITI);
        floor(bed.west().south(), NapoliFurniture.PLAID);
        floor(desk.south().east(2), NapoliFurniture.SEDIA_SCRIVANIA);
        floor(new BlockPos(x + 4, y, z + 2).above(), NapoliFurniture.BOLLITORE);
        floor(new BlockPos(x + 6, y, z + 2), Blocks.BARREL);
        floor(new BlockPos(x + 6, y + 1, z + 2), NapoliFurniture.KIT_TAVOLA);
        floor(new BlockPos(x + 4, y + 1, entranceZ).west(3), NapoliFurniture.KIT_BAGNO);
        floor(new BlockPos(x + house.width() - 2, y, entranceZ - 3), Blocks.CHEST);
        wall(new BlockPos(x + house.width() - 2, y + 1, entranceZ), NapoliFurniture.SPECCHIO);
        wall(sink.above().north(), NapoliFurniture.TERMOSIFONE);
        wall(bed.north(2).above(), NapoliFurniture.TENDA);
        if (roomy) {
            floor(new BlockPos(x + 13, y, z + 11), NapoliFurniture.COFFEE_TABLE);
            floor(new BlockPos(x + 12, y, z + 11), NapoliFurniture.SEDIA_IMBOTTITA.defaultBlockState());
            floor(new BlockPos(x + 14, y, z + 11), NapoliFurniture.SEDIA_IMBOTTITA.defaultBlockState());
            // Use the frontage ends, clear of the stairwell and balcony approach.
            floor(new BlockPos(x + 3, y + 5, z + house.depth()), Blocks.POTTED_FERN);
            floor(new BlockPos(x + 6, y + 5, z + house.depth()), NapoliFurniture.COFFEE_TABLE);
            floor(new BlockPos(x + 8, y + 5, z + house.depth()), NapoliFurniture.SEDIA_IMBOTTITA);
            floor(new BlockPos(x + 9, y + 5, z + house.depth()), NapoliFurniture.OMBRELLONE);
            floor(new BlockPos(x + 4, y + 5, z + house.depth()), NapoliFurniture.BARBECUE);
        }
    }

    private void wall(BlockPos pos, Block block) {
        if (!ChunkPos.containing(pos).equals(chunk.getPos())
                || !ChunkPos.containing(pos.south()).equals(chunk.getPos())
                || !level.isEmptyBlock(pos) || CityProperty.playerPlaced(level, pos)
                || approaches.contains(pos)) return;
        if (level.getBlockState(pos.south()).isFaceSturdy(level, pos.south(), Direction.NORTH))
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private void substitute(BlockPos pos, Block replacement, Block... legacy) {
        if (!ChunkPos.containing(pos).equals(chunk.getPos()) || CityProperty.playerPlaced(level, pos)) return;
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container inventory && !inventory.isEmpty()) return;
        var state = level.getBlockState(pos);
        for (var block : legacy) {
            if (!state.is(block)) continue;
            level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
    }

    private void floor(BlockPos pos, Block block) {
        floor(pos, block.defaultBlockState());
    }

    private void floor(BlockPos pos, BlockState state) {
        if (!ChunkPos.containing(pos).equals(chunk.getPos()) || !level.isEmptyBlock(pos)
                || CityProperty.playerPlaced(level, pos)) return;
        if (approaches.contains(pos) && !(state.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock)) return;
        if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
    }
}
