package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

final class NapoliFurniture {
    static final Block WORKTOP = register("piano_da_lavoro", properties -> new CraftingTableBlock(properties.sound(SoundType.WOOD)));
    static final Block SINK = register("lavabo_ceramica", Sink::new);
    static final Block LAMP = register("lampada_da_tavolo", Lamp::new);

    static final Block COFFEE_TABLE = decor("tavolino", 1, 0, 1, 15, 9, 15, HomeFixtureBlock.Action.WOOD);
    static final Block KITCHEN_SET = decor("utensili_cucina", 1, 0, 1, 15, 8, 15, HomeFixtureBlock.Action.TOOLS);
    static final Block BATH_SET = storage("mobile_bagno");
    static final Block LAUNDRY_BASKET = decor("cesto_biancheria", 2, 0, 2, 14, 11, 14, HomeFixtureBlock.Action.FABRIC);
    static final Block COMPUTER = decor("computer_scrivania", 1, 0, 1, 15, 13, 15, HomeFixtureBlock.Action.COMPUTER);
    static final Block ENTRY_TRAY = decor("svuotatasche", 2, 0, 3, 14, 2, 13, HomeFixtureBlock.Action.TOOLS);

    static final Block POLTRONA = fixture("poltrona", HomeFixtureBlock.Action.SEAT, 15);
    static final Block DIVANO = fixture("divano", HomeFixtureBlock.Action.SEAT, 15);
    static final Block SEDIA_IMBOTTITA = fixture("sedia_imbottita", HomeFixtureBlock.Action.SEAT, 16);
    static final Block SEDIA_SCRIVANIA = fixture("sedia_scrivania", HomeFixtureBlock.Action.SEAT, 16);
    static final Block LAVASTOVIGLIE = fixture("lavastoviglie", HomeFixtureBlock.Action.APPLIANCE, 16);
    static final Block LAVATRICE = fixture("lavatrice", HomeFixtureBlock.Action.APPLIANCE, 16);
    static final Block ASCIUGATRICE = fixture("asciugatrice", HomeFixtureBlock.Action.APPLIANCE, 16);
    static final Block MICROONDE = fixture("microonde", HomeFixtureBlock.Action.HEAT, 9);
    static final Block TOSTAPANE = fixture("tostapane", HomeFixtureBlock.Action.HEAT, 9);
    static final Block BOLLITORE = fixture("bollitore", HomeFixtureBlock.Action.HEAT, 10);
    static final Block FRIGORIFERO = storage("frigorifero");
    static final Block CAPPA = fixture("cappa", HomeFixtureBlock.Action.FABRIC, 16);
    static final Block BIDET = fixture("bidet", HomeFixtureBlock.Action.WASH, 10);
    static final Block WC = fixture("wc", HomeFixtureBlock.Action.TOILET, 16);
    static final Block VASCA = fixture("vasca", HomeFixtureBlock.Action.WASH, 8);
    static final Block SPECCHIO = fixture("specchio", HomeFixtureBlock.Action.FABRIC, 15);
    static final Block TENDA = fixture("tenda", HomeFixtureBlock.Action.FABRIC, 15);
    static final Block TERMOSIFONE = fixture("termosifone", HomeFixtureBlock.Action.HEAT, 14);
    static final Block ASSE_STIRO = fixture("asse_stiro", HomeFixtureBlock.Action.HEAT, 13);
    static final Block KIT_BAGNO = fixture("kit_bagno", HomeFixtureBlock.Action.TOOLS, 6);
    static final Block KIT_PULIZIA = fixture("kit_pulizia", HomeFixtureBlock.Action.TOOLS, 16);
    static final Block KIT_TAVOLA = fixture("kit_tavola", HomeFixtureBlock.Action.TOOLS, 6);
    static final Block KIT_SCRIVANIA = fixture("kit_scrivania", HomeFixtureBlock.Action.TOOLS, 6);
    static final Block RACCOLTA = fixture("raccolta", HomeFixtureBlock.Action.WOOD, 11);
    static final Block APPENDIABITI = fixture("appendiabiti", HomeFixtureBlock.Action.WOOD, 14);
    static final Block PLAID = fixture("plaid", HomeFixtureBlock.Action.FABRIC, 3);
    static final Block OMBRELLONE = fixture("ombrellone", HomeFixtureBlock.Action.FABRIC, 16);
    static final Block BARBECUE = fixture("barbecue", HomeFixtureBlock.Action.HEAT, 13);

    static final Block HAIR_DRYER = decor("phon", 2, 0, 2, 14, 10, 14, HomeFixtureBlock.Action.DRYER);
    static final Block SCALE = decor("bilancia", 1, 0, 1, 15, 2, 15, HomeFixtureBlock.Action.SCALE);
    static final Block VACUUM = decor("aspirapolvere", 1, 0, 1, 15, 15, 15, HomeFixtureBlock.Action.VACUUM);
    static final Block POWER_STRIP = decor("presa_multipla", 1, 0, 5, 15, 3, 11, HomeFixtureBlock.Action.POWER_STRIP);

    static final Item TOAST = accessory("pane_tostato", true);
    static final Item HOT_WATER = accessory("acqua_calda", false);
    static final Item CLEAN_DISH = accessory("piatto_pulito", false);
    static final Item DIRTY_DISH = accessory("piatto_sporco", false);
    static final Item CLEAN_CLOTH = accessory("strofinaccio_pulito", false);
    static final Item DIRTY_CLOTH = accessory("strofinaccio_sporco", false);
    static final Item WET_CLOTH = accessory("strofinaccio_bagnato", false);

    private static Item accessory(String name, boolean food) {
        var key = ResourceKey.create(Registries.ITEM, Napuli.id(name));
        var properties = new Item.Properties().setId(key);
        if (food) properties.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationModifier(.6f).build());
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
    }

    private static Block fixture(String name, HomeFixtureBlock.Action action, int height) {
        return register(name, properties -> new HomeFixtureBlock(properties, Block.box(0, 0, 0, 16, height, 16), action));
    }

    private static Block decor(String name, int x0, int y0, int z0, int x1, int y1, int z1, HomeFixtureBlock.Action action) {
        return register(name, properties -> new HomeFixtureBlock(properties,
                Block.box(x0, y0, z0, x1, y1, z1), action));
    }

    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> FITTED =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<Boolean>builder()
                    .persistent(com.mojang.serialization.Codec.BOOL).buildAndRegister(Napuli.id("functional_furniture"));

    static void fit(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
        if (chunk.getAttachedOrElse(FITTED, false) || CityProperty.playerBuiltInChunk(level, chunk.getPos())) return;
        for (var house : CityLivingHomes.all()) {
            if (((house.x() + 7) >> 4 > chunk.getPos().x() || (house.x() + 12) >> 4 < chunk.getPos().x())
                    || ((house.z() + 2) >> 4 > chunk.getPos().z() || (house.z() + 13) >> 4 < chunk.getPos().z())) continue;
            if (house.id().startsWith("atlas_") && NapoliExpansion.revision(chunk) < 1) return;
            var sink = CityLivingHomes.use(house, CityLivingHomes.Furniture.SINK).block();
            if (net.minecraft.world.level.ChunkPos.containing(sink).equals(chunk.getPos())
                    && level.getBlockState(sink).is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
                level.setBlock(sink, SINK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            var desk = CityLivingHomes.use(house, CityLivingHomes.Furniture.DESK).block();
            if (!net.minecraft.world.level.ChunkPos.containing(desk).equals(chunk.getPos())) continue;
            var state = level.getBlockState(desk);
            if (state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
                level.setBlock(desk, WORKTOP.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if ((state.is(WORKTOP) || state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE))
                    && level.isEmptyBlock(desk.above())) {
                level.setBlock(desk.above(), LAMP.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        chunk.setAttached(FITTED, true);
    }

    private NapoliFurniture() {}

    static void register() {
        var barrel = (net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType)
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("barrel"));
        barrel.addValidBlock(FRIGORIFERO);
        barrel.addValidBlock(BATH_SET);
    }

    private static Block storage(String name) {
        return register(name, properties -> new net.minecraft.world.level.block.BarrelBlock(properties) {
            @Override
            protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
                if (player.isSpectator()) return InteractionResult.PASS;
                if (player instanceof ServerPlayer user && !HomeFixtureBlock.access(user, pos)) return InteractionResult.SUCCESS;
                return super.useWithoutItem(state, level, pos, player, hit);
            }
        });
    }

    private static Block register(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        var id = Napuli.id(name);
        var blockKey = ResourceKey.create(Registries.BLOCK, id);
        var itemKey = ResourceKey.create(Registries.ITEM, id);
        var block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                factory.apply(BlockBehaviour.Properties.of().setId(blockKey).strength(1.5f).noOcclusion()));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static final class Sink extends Block {
        Sink(Properties properties) {
            super(properties.sound(SoundType.STONE));
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player.isSpectator()) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) {
                if (level.dimension() == NapoliMap.LEVEL) {
                    for (var house : CityLivingHomes.all()) {
                        if (!house.contains(pos) || house.price() <= 0 || NapoliProperties.hasAccess(serverPlayer, house)) continue;
                        serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("napuli.property.private"));
                        return InteractionResult.SUCCESS;
                    }
                }
                NapoliHygiene.wash(serverPlayer, pos, Math.min(100, NapoliHygiene.value(serverPlayer) + 35));
            }
            return InteractionResult.SUCCESS;
        }
    }

    private static final class Lamp extends Block {
        Lamp(Properties properties) {
            super(properties.sound(SoundType.GLASS).lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 14 : 0));
            registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.LIT);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player.isSpectator()) return InteractionResult.PASS;
            if (player instanceof ServerPlayer) {
                level.setBlock(pos, state.cycle(BlockStateProperties.LIT), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, .35f, 1);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
                net.minecraft.world.level.BlockGetter level, BlockPos pos,
                net.minecraft.world.phys.shapes.CollisionContext context) {
            return box(3, 0, 3, 13, 14, 13);
        }
    }
}
