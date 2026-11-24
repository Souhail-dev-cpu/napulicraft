package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Server-driven furniture actions with a synced state and vanilla use animation. */
final class HomeFixtureBlock extends Block {
    enum Action {
        SEAT(SoundEvents.WOOL_STEP, 0),
        WOOD(SoundEvents.BARREL_OPEN, 0),
        TOOLS(SoundEvents.IRON_TRAPDOOR_OPEN, 0),
        COMPUTER(SoundEvents.LEVER_CLICK, -1),
        WASH(SoundEvents.BUCKET_EMPTY, 45),
        TOILET(SoundEvents.BUCKET_EMPTY, 30),
        APPLIANCE(SoundEvents.PISTON_EXTEND, 85),
        HEAT(SoundEvents.FURNACE_FIRE_CRACKLE, 65),
        FABRIC(SoundEvents.WOOL_STEP, -1),
        DRYER(SoundEvents.FIRE_EXTINGUISH, 35),
        SCALE(SoundEvents.LEVER_CLICK, 25),
        VACUUM(SoundEvents.PISTON_EXTEND, 50),
        POWER_STRIP(SoundEvents.LEVER_CLICK, -1);

        final SoundEvent sound;
        final int duration;
        Action(SoundEvent sound, int duration) {
            this.sound = sound;
            this.duration = duration;
        }
    }

    private final VoxelShape shape;
    private final Action action;

    HomeFixtureBlock(Properties properties, VoxelShape shape, Action action) {
        super(properties);
        this.shape = shape;
        this.action = action;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return shape;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer user)) return InteractionResult.SUCCESS;
        var city = user.level();
        if (!access(user, pos)) return InteractionResult.SUCCESS;
        boolean active = state.getValue(BlockStateProperties.LIT);
        if (action.duration > 0 && active) return InteractionResult.SUCCESS;
        if (action == Action.COMPUTER && active && !user.isShiftKeyDown()) {
            if (city.dimension() == NapoliMap.LEVEL) NapoliEmployment.status(user);
            return InteractionResult.SUCCESS;
        }
        if (action == Action.SEAT && !CityMovement.sitAt(user, pos)) return InteractionResult.SUCCESS;
        if (!runAccessory(user, pos)) return InteractionResult.SUCCESS;

        if (action.duration != 0) city.setBlock(pos, state.setValue(BlockStateProperties.LIT, !active), UPDATE_ALL);
        if (action.duration > 0) city.scheduleTick(pos, this, action.duration);
        city.playSound(null, pos, action.sound, SoundSource.BLOCKS, .65f, 1);
        user.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        if (action == Action.WASH) {
            NapoliHygiene.wash(user, pos, Math.min(100, NapoliHygiene.value(user) + 45));
            if (this == NapoliFurniture.VASCA) NapoliHygiene.soakHair(user);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        var result = processedItem(stack);
        if (result == null) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!(player instanceof ServerPlayer user)) return InteractionResult.SUCCESS;
        if (!access(user, pos) || state.getValue(BlockStateProperties.LIT)) return InteractionResult.SUCCESS;
        var cooked = new net.minecraft.world.item.ItemStack(result);
        stack.consume(1, user);
        if (!user.getInventory().add(cooked)) net.minecraft.world.Containers.dropItemStack(level, user.getX(), user.getY(), user.getZ(), cooked);
        return useWithoutItem(state, level, pos, player, hit);
    }

    private net.minecraft.world.item.Item processedItem(net.minecraft.world.item.ItemStack stack) {
        var furniture = this;
        if (furniture == NapoliFurniture.TOSTAPANE && stack.is(net.minecraft.world.item.Items.BREAD)) return NapoliFurniture.TOAST;
        if (furniture == NapoliFurniture.BOLLITORE && stack.is(net.minecraft.world.item.Items.POTION)) {
            var potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (potion != null && potion.is(net.minecraft.world.item.alchemy.Potions.WATER)) return NapoliFurniture.HOT_WATER;
        }
        if (furniture == NapoliFurniture.KIT_TAVOLA && stack.is(NapoliFurniture.CLEAN_DISH)) return NapoliFurniture.DIRTY_DISH;
        if (furniture == NapoliFurniture.KIT_PULIZIA && stack.is(NapoliFurniture.CLEAN_CLOTH)) return NapoliFurniture.DIRTY_CLOTH;
        if (furniture == NapoliFurniture.LAVASTOVIGLIE && stack.is(NapoliFurniture.DIRTY_DISH)) return NapoliFurniture.CLEAN_DISH;
        if (furniture == NapoliFurniture.LAVATRICE && stack.is(NapoliFurniture.DIRTY_CLOTH)) return NapoliFurniture.WET_CLOTH;
        if ((furniture == NapoliFurniture.ASCIUGATRICE || furniture == NapoliFurniture.ASSE_STIRO)
                && stack.is(NapoliFurniture.WET_CLOTH)) return NapoliFurniture.CLEAN_CLOTH;
        if (furniture == NapoliFurniture.MICROONDE && stack.is(net.minecraft.world.item.Items.POTATO))
            return net.minecraft.world.item.Items.BAKED_POTATO;
        if (furniture == NapoliFurniture.BARBECUE) {
            if (stack.is(net.minecraft.world.item.Items.BEEF)) return net.minecraft.world.item.Items.COOKED_BEEF;
            if (stack.is(net.minecraft.world.item.Items.CHICKEN)) return net.minecraft.world.item.Items.COOKED_CHICKEN;
            if (stack.is(net.minecraft.world.item.Items.PORKCHOP)) return net.minecraft.world.item.Items.COOKED_PORKCHOP;
            if (stack.is(net.minecraft.world.item.Items.COD)) return net.minecraft.world.item.Items.COOKED_COD;
        }
        if ((furniture == NapoliFurniture.ASCIUGATRICE || furniture == NapoliFurniture.ASSE_STIRO)
                && stack.is(net.minecraft.world.item.Items.WET_SPONGE)) return net.minecraft.world.item.Items.SPONGE;
        return null;
    }

    private boolean runAccessory(ServerPlayer user, BlockPos pos) {
        var level = user.level();
        if (action == Action.DRYER || action == Action.VACUUM) {
            if (!powered(level, pos)) {
                user.sendOverlayMessage(Component.translatable("napuli.home.no_power"));
                return false;
            }
        }
        switch (action) {
            case DRYER -> {
                if (!NapoliHygiene.dryHair(user)) {
                    user.sendOverlayMessage(Component.translatable("napuli.home.hair_already_dry"));
                    return false;
                }
                level.sendParticles(ParticleTypes.CLOUD, user.getX(), user.getEyeY(), user.getZ(), 8, .18, .14, .18, .025);
                user.sendOverlayMessage(Component.translatable("napuli.home.hair_dried"));
            }
            case VACUUM -> {
                var litter = level.getAttachedOrElse(CityOccupations.LITTER, java.util.Map.of()).values().stream()
                        .filter(dirt -> dirt.position().distSqr(pos) <= 16 && level.hasChunkAt(dirt.position()))
                        .filter(dirt -> level.dimension() != NapoliMap.LEVEL || accessWithoutNotice(user, dirt.position()))
                        .min(java.util.Comparator.comparingDouble(dirt -> dirt.position().distSqr(pos))).orElse(null);
                if (litter == null) {
                    user.sendOverlayMessage(Component.translatable("napuli.home.no_litter"));
                    return false;
                }
                CityOccupations.litter(level, litter.position(), -1);
                var dirt = litter.position();
                level.sendParticles(ParticleTypes.CLOUD, dirt.getX()+.5, dirt.getY()+.25, dirt.getZ()+.5, 9, .25, .08, .25, .02);
                user.sendOverlayMessage(Component.translatable("napuli.home.vacuumed"));
            }
            case SCALE -> {
                int size = (int) Math.round(user.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) * 100);
                user.sendOverlayMessage(Component.translatable("napuli.home.scale_reading", NapoliStreet.fullness(user), size));
            }
            default -> {}
        }
        return true;
    }

    static boolean powered(ServerLevel level, BlockPos pos) {
        if (level.hasNeighborSignal(pos)) return true;
        for (var candidate : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
            if (candidate.distSqr(pos) > 16 || !level.hasChunkAt(candidate)) continue;
            var state = level.getBlockState(candidate);
            if (state.is(NapoliFurniture.POWER_STRIP) && state.getValue(BlockStateProperties.LIT)) return true;
        }
        return false;
    }

    private static boolean accessWithoutNotice(ServerPlayer player, BlockPos pos) {
        for (var house : CityLivingHomes.all()) {
            if (house.contains(pos) && house.price() > 0 && !NapoliProperties.hasAccess(player, house)) return false;
        }
        return true;
    }

    static boolean access(ServerPlayer player, BlockPos pos) {
        if (player.level().dimension() != NapoliMap.LEVEL) return true;
        for (var house : CityLivingHomes.all()) {
            if (!house.contains(pos) || house.price() <= 0 || NapoliProperties.hasAccess(player, house)) continue;
            player.sendOverlayMessage(Component.translatable("napuli.property.private"));
            return false;
        }
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BlockStateProperties.LIT)) return;
        level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, .35f, 1.2f);
        if (action == Action.HEAT || action == Action.APPLIANCE || action == Action.DRYER || action == Action.VACUUM)
            level.sendParticles(ParticleTypes.CLOUD, pos.getX()+.5, pos.getY()+.95, pos.getZ()+.5, 5, .14, .12, .14, .01);
    }
}
