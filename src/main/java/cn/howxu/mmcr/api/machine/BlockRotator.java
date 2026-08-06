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
        return switch (target) {
            case NORTH, SOUTH, EAST, WEST -> rotateYCCWSouthUntil(pos, target);
            case UP -> new BlockPos(pos.getX(), pos.getZ(), -pos.getY());
            case DOWN -> new BlockPos(pos.getX(), -pos.getZ(), pos.getY());
        };
    }

    public static BlockPos normalizeFromFace(BlockPos offset, Direction sourceFace) {
        return switch (sourceFace) {
            case NORTH, SOUTH, EAST, WEST -> normalizeHorizontal(offset, sourceFace);
            case UP -> new BlockPos(offset.getX(), -offset.getZ(), offset.getY());
            case DOWN -> new BlockPos(offset.getX(), offset.getZ(), -offset.getY());
        };
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
}
