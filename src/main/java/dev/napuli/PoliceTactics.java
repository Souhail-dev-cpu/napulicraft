package dev.napuli;

import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Chase one suspect, replan quickly, and mine only a nearby player-made obstruction after getting stuck. */
final class PoliceTactics {
    static final int RANGE = 48;
    private static final AttachmentType<Chase> CHASE = AttachmentRegistry.create(Napuli.id("police_chase"));
    private static final class Chase {
        UUID suspect;
        Vec3 checkpoint, goal;
        int stuck;
        long nextRepath;
        BlockPos obstacle;
        BlockState originalBlock;
        float progress;
        ItemStack previousHand;
    }
    private PoliceTactics() {}

    static boolean assignedTo(NapoliResident officer, ServerPlayer suspect) {
        ServerPlayer closest = null;
        double best = RANGE * RANGE;
        for (ServerPlayer player : suspect.level().players()) {
            if (!Carabinieri.isWanted(player) || Carabinieri.isJailed(player) || !player.isAlive() || player.isSpectator()) continue;
            double distance = officer.distanceToSqr(player);
            if (distance < best || distance == best && closest != null && player.getId() < closest.getId()) { best = distance; closest = player; }
        }
        return closest == suspect;
    }

    static void pursue(NapoliResident officer, ServerPlayer suspect, long now) {
        if (officer.role() != NapoliResident.Role.CARABINIERE || officer.isNoAi() || officer.isPassenger()
                || !suspect.isAlive() || officer.level() != suspect.level()) return;
        Chase chase = officer.getAttachedOrCreate(CHASE, Chase::new);
        if (!suspect.getUUID().equals(chase.suspect)) {
            cancelMining(officer, chase);
            chase.suspect = suspect.getUUID(); chase.checkpoint = officer.position(); chase.stuck = 0; chase.nextRepath = now;
        }
        officer.getLookControl().setLookAt(suspect, 30, 30);
        if (now % 10 == 0) {
            boolean moved = chase.checkpoint == null || officer.position().distanceToSqr(chase.checkpoint) > .09;
            chase.stuck = moved ? 0 : chase.stuck + 10;
            chase.checkpoint = officer.position();
        }
        if (chase.obstacle != null) {
            if (chase.goal != null && suspect.position().distanceToSqr(chase.goal) > 4) cancelMining(officer, chase);
            else if (now % 10 == 0) {
                var route = officer.getNavigation().createPath(suspect, 0);
                if (route != null && route.canReach()) { cancelMining(officer, chase); officer.getNavigation().moveTo(route, 1.3); }
            }
            if (chase.obstacle != null) { mine(officer, chase, now); return; }
        }
        if (now >= chase.nextRepath) { officer.getNavigation().moveTo(suspect, 1.3); chase.nextRepath = now + 6; }
        var route = officer.getNavigation().getPath();
        boolean blockedLadder = officer.onClimbable() && (route == null || !route.canReach()) && overhead(officer,suspect) != null;
        if (!blockedLadder && (chase.stuck < 20 || route != null && route.canReach() && chase.stuck < 40)) return;
        var obstruction = obstruction(officer, suspect);
        if (obstruction == null) return;
        chase.obstacle = obstruction;
        chase.originalBlock = officer.level().getBlockState(obstruction);
        chase.previousHand = officer.getMainHandItem().copy();
        chase.goal = suspect.position(); chase.progress = 0;
        officer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        officer.setDropChance(EquipmentSlot.MAINHAND, 0);
        officer.getNavigation().stop();
        suspect.sendOverlayMessage(Component.translatable("napuli.cc.breach"));
        mine(officer, chase, now);
    }

    static boolean breaching(NapoliResident officer) { var chase = officer.getAttached(CHASE); return chase != null && chase.obstacle != null; }
    static boolean active(NapoliResident officer) { return officer.hasAttached(CHASE); }
    static boolean canGrab(NapoliResident officer, ServerPlayer suspect) {
        if (officer.distanceToSqr(suspect) >= 1.9 * 1.9 || !officer.hasLineOfSight(suspect)) return false;
        var hit = officer.level().clip(new ClipContext(officer.position().add(0, .6, 0), suspect.position().add(0, .6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, officer));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static BlockPos obstruction(NapoliResident officer, ServerPlayer target) {
        var overhead = overhead(officer,target);
        if (overhead != null) return overhead;
        var level = (ServerLevel) officer.level();
        for (double height : new double[] {officer.getEyeHeight(), .4}) {
            Vec3 from = officer.position().add(0, height, 0);
            Vec3 to = target.position().add(0, Math.min(height, 1.3), 0);
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, officer));
            if (hit.getType() == HitResult.Type.BLOCK && canBreach(officer, hit.getBlockPos())) return hit.getBlockPos().immutable();
        }
        return null;
    }

    private static BlockPos overhead(NapoliResident officer, ServerPlayer target) {
        if (!officer.onClimbable() || target.getY() <= officer.getY()+1) return null;
        for (int dy=1;dy<=4;dy++) {
            var pos=officer.blockPosition().above(dy);
            if (!canBreach(officer,pos)) continue;
            var hit=officer.level().clip(new ClipContext(officer.getEyePosition(),Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,officer));
            if (hit.getType()==HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return pos;
        }
        return null;
    }

    static boolean canBreach(NapoliResident officer, BlockPos pos) {
        if (!(officer.level() instanceof ServerLevel level) || !level.getGameRules().get(GameRules.MOB_GRIEFING)
                || !level.hasChunkAt(pos) || !CityProperty.playerPlaced(level, pos)
                || pos.getY() < officer.blockPosition().getY() || pos.distToCenterSqr(officer.getEyePosition()) > 3 * 3) return false;
        BlockState block = level.getBlockState(pos);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        return !block.isAir() && !block.hasBlockEntity() && block.getFluidState().isEmpty() && !block.is(BlockTags.CLIMBABLE)
                && !block.is(BlockTags.WOODEN_DOORS) && block.getDestroySpeed(level, pos) >= 0
                && (!block.requiresCorrectToolForDrops() || pickaxe.isCorrectToolForDrops(block));
    }

    private static void mine(NapoliResident officer, Chase chase, long now) {
        ServerLevel level = (ServerLevel) officer.level();
        if (!canBreach(officer, chase.obstacle) || level.getBlockState(chase.obstacle) != chase.originalBlock) { cancelMining(officer, chase); return; }
        officer.getNavigation().stop();
        if (officer.onClimbable()) officer.setDeltaMovement(Vec3.ZERO);
        officer.getLookControl().setLookAt(chase.obstacle.getX() + .5, chase.obstacle.getY() + .5, chase.obstacle.getZ() + .5);
        float hardness = chase.originalBlock.getDestroySpeed(level, chase.obstacle);
        chase.progress += hardness == 0 ? 1 : officer.getMainHandItem().getDestroySpeed(chase.originalBlock) / hardness / 30f;
        level.destroyBlockProgress(officer.getId(), chase.obstacle, Math.min(9, (int) (chase.progress * 10)));
        if (now % 6 == 0) {
            officer.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
            level.playSound(null, chase.obstacle, chase.originalBlock.getSoundType().getHitSound(), SoundSource.BLOCKS, .6f, 1);
        }
        if (chase.progress >= 1) {
            BlockPos pos = chase.obstacle;
            if (level.destroyBlock(pos, false, officer)) {
                Block.dropResources(chase.originalBlock, level, pos, null, officer, officer.getMainHandItem());
                CityProperty.forget(level, pos);
            }
            cancelMining(officer, chase); chase.stuck = 0;
            officer.getNavigation().recomputePath();
        }
    }

    private static void cancelMining(NapoliResident officer, Chase chase) {
        if (chase.obstacle != null && officer.level() instanceof ServerLevel level) level.destroyBlockProgress(officer.getId(), chase.obstacle, -1);
        if (chase.previousHand != null) officer.setItemSlot(EquipmentSlot.MAINHAND, chase.previousHand);
        chase.previousHand = null; chase.obstacle = null; chase.originalBlock = null; chase.progress = 0;
    }
    static void stop(NapoliResident officer) {
        var chase = officer.getAttached(CHASE);
        if (chase != null) { cancelMining(officer, chase); officer.removeAttached(CHASE); officer.getNavigation().stop(); }
        // An in-progress chase is transient. A server restart must not leave the mining tool equipped forever.
        if (officer.getMainHandItem().is(Items.IRON_PICKAXE)) officer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Napuli.BATON));
    }
}
