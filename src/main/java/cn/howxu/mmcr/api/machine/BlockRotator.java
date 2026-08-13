package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 多方块模板的 yaw 旋转:
 * <ul>
 *     <li>{@code rotateYCCW(pos)} = (z, y, -x)</li>
 *     <li>{@code rotateYCCWSouthUntil(pos, target)} 从 SOUTH 起步一直 YCCW 直到 target。</li>
 * </ul>
 * BuildCommand 与 StructureMatcher 共用,这样两者对同一 ctrlFacing 计算 world offset 一致。
 */
public final class BlockRotator {

    private BlockRotator() {}

    public static BlockPos rotateYCCW(BlockPos pos) {
        return new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
    }

    /**
     * 起点 SOUTH,循环 rotateYCCW 直到 facing 等于 target。
     */
    public static BlockPos rotateYCCWSouthUntil(BlockPos pos, Direction target) {
        Direction current = Direction.SOUTH;
        BlockPos r = pos;
        while (current != target) {
            current = current.getCounterClockWise();
            r = rotateYCCW(r);
        }
        return r;
    }

    public static BlockPos rotateSouthTo(BlockPos pos, Direction target) {
        return rotateSouthTo(pos, target, Direction.SOUTH);
    }

    public static BlockPos rotateSouthTo(BlockPos pos, Direction target, Direction rollFacing) {
        Direction normalizedRoll = normalizedRoll(target, rollFacing);
        if (!target.getAxis().isVertical()) {
            return rotateYCCWSouthUntil(pos, target);
        }

        Direction front = target;
        Direction up = normalizedRoll;
        Direction xAxis = cross(up, front);
        return project(pos, xAxis, up, front);
    }

    public static BlockPos normalizeFromFace(BlockPos offset, Direction sourceFace) {
        return normalizeFromFace(offset, sourceFace, Direction.SOUTH);
    }

    public static BlockPos normalizeFromFace(BlockPos offset, Direction sourceFace, Direction rollFacing) {
        Direction normalizedRoll = normalizedRoll(sourceFace, rollFacing);
        if (!sourceFace.getAxis().isVertical()) {
            return normalizeHorizontal(offset, sourceFace);
        }

        Direction front = sourceFace;
        Direction up = normalizedRoll;
        Direction xAxis = cross(up, front);
        return unproject(offset, xAxis, up, front);
    }

    public static Direction normalizedRoll(Direction facing, Direction rollFacing) {
        if (!facing.getAxis().isVertical()) return Direction.SOUTH;
        if (rollFacing == null || !rollFacing.getAxis().isHorizontal()) return Direction.SOUTH;
        return rollFacing;
    }

    private static BlockPos normalizeHorizontal(BlockPos offset, Direction sourceFace) {
        Direction current = sourceFace;
        BlockPos normalized = offset;
        while (current != Direction.SOUTH) {
            current = current.getCounterClockWise();
            normalized = rotateYCCW(normalized);
        }
        return normalized;
    }

    private static BlockPos project(BlockPos pos, Direction xAxis, Direction yAxis, Direction zAxis) {
        return new BlockPos(
                xAxis.getStepX() * pos.getX() + yAxis.getStepX() * pos.getY() + zAxis.getStepX() * pos.getZ(),
                xAxis.getStepY() * pos.getX() + yAxis.getStepY() * pos.getY() + zAxis.getStepY() * pos.getZ(),
                xAxis.getStepZ() * pos.getX() + yAxis.getStepZ() * pos.getY() + zAxis.getStepZ() * pos.getZ());
    }

    private static BlockPos unproject(BlockPos pos, Direction xAxis, Direction yAxis, Direction zAxis) {
        return new BlockPos(dot(pos, xAxis), dot(pos, yAxis), dot(pos, zAxis));
    }

    private static int dot(BlockPos pos, Direction axis) {
        return pos.getX() * axis.getStepX() + pos.getY() * axis.getStepY() + pos.getZ() * axis.getStepZ();
    }

    private static Direction cross(Direction a, Direction b) {
        int x = a.getStepY() * b.getStepZ() - a.getStepZ() * b.getStepY();
        int y = a.getStepZ() * b.getStepX() - a.getStepX() * b.getStepZ();
        int z = a.getStepX() * b.getStepY() - a.getStepY() * b.getStepX();
        for (Direction direction : Direction.values()) {
            if (direction.getStepX() == x && direction.getStepY() == y && direction.getStepZ() == z) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Directions must be perpendicular: " + a + ", " + b);
    }
}
