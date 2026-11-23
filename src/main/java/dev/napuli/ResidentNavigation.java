package dev.napuli;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

/** Ground paths with real vertical ladder edges; residents still need an actual climbable block. */
final class ResidentNavigation extends GroundPathNavigation {
    ResidentNavigation(Mob mob, Level level) { super(mob, level); setMaxVisitedNodesMultiplier(1.5f); }

    @Override protected PathFinder createPathFinder(int maxNodes) {
        nodeEvaluator = new LadderEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        nodeEvaluator.setCanOpenDoors(true);
        nodeEvaluator.setCanFloat(true);
        return new PathFinder(nodeEvaluator, maxNodes);
    }

    @Override protected boolean canUpdatePath() {
        return mob.onClimbable() || level.getBlockState(mob.blockPosition().below()).is(BlockTags.CLIMBABLE) || super.canUpdatePath();
    }

    @Override public void tick() {
        super.tick();
        if (path == null || path.isDone() || !mob.onClimbable()) return;
        // A wooden hatch at a ladder exit is a passage, rather than a reason to abandon the route.
        for (int dy = 0; dy <= 2; dy++) {
            var pos = mob.blockPosition().above(dy);
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.WOODEN_TRAPDOORS) && !state.getValue(BlockStateProperties.OPEN))
                level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 3);
        }
    }

    @Override public boolean canCutCorner(PathType type) {
        if (mob.onClimbable() || level.getBlockState(mob.blockPosition().below()).is(BlockTags.CLIMBABLE)) return false;
        return super.canCutCorner(type);
    }

    private record ClimbMotion(Vec3 motion, boolean centerOnLadder) {}
    private ClimbMotion climbingMotion() {
        if (path == null || path.isDone()) return null;
        var feet = mob.blockPosition();
        var ladder = level.getBlockState(feet).is(BlockTags.CLIMBABLE) ? feet
                : level.getBlockState(feet.below()).is(BlockTags.CLIMBABLE) ? feet.below() : null;
        if (ladder == null) return null;
        var next = path.getNextEntityPos(mob);
        double vertical = next.y - mob.getY();
        boolean leavingColumn = Math.abs(next.x-ladder.getX()-.5) > .3 || Math.abs(next.z-ladder.getZ()-.5) > .3;
        if (leavingColumn && vertical >= -.5) {
            var previous = mob.getDeltaMovement();
            return new ClimbMotion(new Vec3(previous.x, vertical > -.08 ? Math.max(previous.y,.22) : previous.y, previous.z),false);
        }
        if (Math.abs(vertical) <= .15) return null;
        // Ordinary walking momentum must not carry an airborne resident off the ladder column.
        return new ClimbMotion(new Vec3(net.minecraft.util.Mth.clamp((ladder.getX()+.5-mob.getX())*.4,-.12,.12),
                vertical > 0 ? .22 : -.15, net.minecraft.util.Mth.clamp((ladder.getZ()+.5-mob.getZ())*.4,-.12,.12)),true);
    }

    static final class LadderMoveControl extends MoveControl {
        LadderMoveControl(Mob mob) { super(mob); }
        @Override public void tick() {
            if (mob.getNavigation() instanceof ResidentNavigation navigation) {
                var command = navigation.climbingMotion();
                if (command != null) {
                    if (command.centerOnLadder()) { mob.setSpeed(0); mob.setZza(0); mob.setXxa(0); }
                    else super.tick();
                    mob.setDeltaMovement(command.motion()); return;
                }
            }
            super.tick();
        }
    }

    private static final class LadderEvaluator extends WalkNodeEvaluator {
        private boolean ladder(BlockPos pos) { return currentContext.getBlockState(pos).is(BlockTags.CLIMBABLE); }
        private boolean clear(BlockPos pos) {
            var state = currentContext.getBlockState(pos);
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || state.is(BlockTags.FIRE)) return false;
            return state.is(BlockTags.CLIMBABLE) || state.is(BlockTags.WOODEN_TRAPDOORS)
                    || state.getCollisionShape(currentContext.level(), pos).isEmpty();
        }
        private boolean room(BlockPos pos) { return clear(pos) && clear(pos.above()); }
        private Node climbingNode(BlockPos pos) {
            var node = getNode(pos);
            node.type = PathType.WALKABLE;
            node.costMalus = Math.max(0, node.costMalus);
            return node;
        }
        @Override public Node getStart() {
            var pos = mob.blockPosition();
            return ladder(pos) && room(pos) ? climbingNode(pos) : super.getStart();
        }
        @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
            var pos = new BlockPos(x, y, z);
            if ((ladder(pos) || ladder(pos.below())) && room(pos)) return PathType.WALKABLE;
            return super.getPathTypeOfMob(context, x, y, z, mob);
        }
        @Override public int getNeighbors(Node[] neighbors, Node node) {
            int count = super.getNeighbors(neighbors, node);
            var pos = node.asBlockPos();
            if ((ladder(pos) || ladder(pos.above())) && room(pos.above())) count = add(neighbors, count, pos.above());
            if ((ladder(pos) || ladder(pos.below())) && room(pos.below())) count = add(neighbors, count, pos.below());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                var side = pos.relative(direction);
                if ((ladder(side) || ladder(side.below())) && room(side)) count = add(neighbors, count, side);
            }
            return count;
        }
        private int add(Node[] neighbors, int count, BlockPos pos) {
            if (count >= neighbors.length) return count;
            Node candidate = climbingNode(pos);
            if (candidate.closed) return count;
            for (int i = 0; i < count; i++) if (neighbors[i] == candidate) return count;
            neighbors[count] = candidate;
            return count + 1;
        }
    }
}
