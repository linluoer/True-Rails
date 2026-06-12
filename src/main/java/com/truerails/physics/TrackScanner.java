package com.truerails.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class TrackScanner {

    public record ScanResult(double distCurve, double distSlope, double distStop) {
        public static final ScanResult EMPTY = new ScanResult(-1, -1, -1);
    }

    @Nullable
    public static BlockPos findRail(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof BaseRailBlock) return pos;
        BlockPos below = pos.below();
        if (level.getBlockState(below).getBlock() instanceof BaseRailBlock) return below;
        BlockPos above = pos.above();
        if (level.getBlockState(above).getBlock() instanceof BaseRailBlock) return above;
        return null;
    }

    public static ScanResult scan(Level level, AbstractMinecart cart, Vec3 motion, int maxDist) {
        BlockPos pos = findRail(level, cart.blockPosition());
        if (pos == null) return ScanResult.EMPTY;
        Direction dir = Direction.getNearest(motion.x, 0.0, motion.z);
        if (dir.getAxis().isVertical()) return ScanResult.EMPTY;

        double dCurve = -1, dSlope = -1, dStop = -1;
        for (int i = 1; i <= maxDist; i++) {
            if (!level.hasChunkAt(pos)) break;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BaseRailBlock rail)) break;
            RailShape shape = state.getValue(rail.getShapeProperty());
            Direction exit = exitDirection(shape, dir);
            if (exit == null) break;

            BlockPos next = pos.relative(exit);
            if (shape.isAscending() && exit == ascendDirection(shape)) next = next.above();
            if (!level.hasChunkAt(next)) break;
            BlockPos nextRail = findRail(level, next);
            if (nextRail == null) break;

            BlockState ns = level.getBlockState(nextRail);
            RailShape nsShape = ns.getValue(((BaseRailBlock) ns.getBlock()).getShapeProperty());
            if (dCurve < 0 && CruiseController.isCurve(nsShape)) dCurve = i;
            if (dSlope < 0 && nsShape.isAscending()) dSlope = i;
            if (dStop < 0 && ns.is(Blocks.POWERED_RAIL) && !ns.getValue(PoweredRailBlock.POWERED)) dStop = i;

            pos = nextRail;
            dir = exit;
            if (dCurve >= 0 && dSlope >= 0 && dStop >= 0) break;
        }
        return new ScanResult(dCurve, dSlope, dStop);
    }

    @Nullable
    static Direction exitDirection(RailShape shape, Direction travel) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH ->
                    (travel == Direction.NORTH || travel == Direction.SOUTH) ? travel : null;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST ->
                    (travel == Direction.EAST || travel == Direction.WEST) ? travel : null;
            case SOUTH_EAST -> travel == Direction.NORTH ? Direction.EAST
                    : travel == Direction.WEST ? Direction.SOUTH : null;
            case SOUTH_WEST -> travel == Direction.NORTH ? Direction.WEST
                    : travel == Direction.EAST ? Direction.SOUTH : null;
            case NORTH_WEST -> travel == Direction.SOUTH ? Direction.WEST
                    : travel == Direction.EAST ? Direction.NORTH : null;
            case NORTH_EAST -> travel == Direction.SOUTH ? Direction.EAST
                    : travel == Direction.WEST ? Direction.NORTH : null;
        };
    }

    static Direction ascendDirection(RailShape shape) {
        return switch (shape) {
            case ASCENDING_EAST -> Direction.EAST;
            case ASCENDING_WEST -> Direction.WEST;
            case ASCENDING_NORTH -> Direction.NORTH;
            case ASCENDING_SOUTH -> Direction.SOUTH;
            default -> Direction.NORTH;
        };
    }

    private TrackScanner() {}
}
